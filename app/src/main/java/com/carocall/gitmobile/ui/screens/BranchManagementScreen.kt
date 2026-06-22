package com.carocall.gitmobile.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carocall.gitmobile.R
import com.carocall.gitmobile.data.git.GitManager
import com.carocall.gitmobile.data.model.BranchInfo
import com.carocall.gitmobile.data.model.BranchType
import com.carocall.gitmobile.data.model.GitAccount
import com.carocall.gitmobile.ui.component.ErrorDialog
import com.carocall.gitmobile.ui.component.InputSheet
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchManagementScreen(
    repoRoot: File,
    gitAccounts: List<GitAccount> = emptyList(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var branches by remember { mutableStateOf<List<BranchInfo>>(emptyList()) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var branchToDelete by remember { mutableStateOf<BranchInfo?>(null) }
    var branchToCheckoutRemote by remember { mutableStateOf<BranchInfo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshProgress by remember { mutableStateOf("") }
    
    var pendingCheckoutBranch by remember { mutableStateOf<BranchInfo?>(null) }
    var showDirtyWarning by remember { mutableStateOf(false) }

    fun refresh(fetchRemote: Boolean = false) {
        scope.launch {
            if (fetchRemote) {
                isRefreshing = true
                refreshProgress = "Fetching..."
                val config = GitManager.getRemoteConfig(repoRoot)
                
                // Resolve credentials
                val account = gitAccounts.find { it.id == config.accountId }
                val credentials = if (account != null) {
                    Pair(account.username, account.token)
                } else {
                    Pair("", "")
                }
                val user = credentials.first
                val token = credentials.second

                val monitor = object : org.eclipse.jgit.lib.EmptyProgressMonitor() {
                    override fun beginTask(title: String?, totalWork: Int) {
                        refreshProgress = title ?: "Working..."
                    }
                }
                GitManager.fetch(repoRoot, user, token, progressMonitor = monitor)
                isRefreshing = false
                refreshProgress = ""
            }
            branches = GitManager.getBranches(repoRoot)
        }
    }

    fun executeCheckout(branch: BranchInfo) {
        if (branch.type == BranchType.REMOTE) {
            branchToCheckoutRemote = branch
        } else {
            scope.launch {
                GitManager.checkoutBranch(repoRoot, branch.fullRefName).onSuccess {
                    Toast.makeText(context, context.getString(R.string.branch_checkout_success, branch.displayName), Toast.LENGTH_SHORT).show()
                    refresh()
                }.onFailure {
                    errorMessage = it.message
                }
            }
        }
    }

    fun onCheckoutClick(branch: BranchInfo) {
        if (branch.isCurrent) return
        scope.launch {
            val status = GitManager.getStatus(repoRoot)
            if (status.hasChanges) {
                pendingCheckoutBranch = branch
                showDirtyWarning = true
            } else {
                executeCheckout(branch)
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.branch_management)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRefreshing && refreshProgress.isNotBlank()) {
                            Text(
                                text = refreshProgress,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        IconButton(onClick = { refresh(true) }, enabled = !isRefreshing) {
                            if (isRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTabIndex == 0) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, stringResource(R.string.new_branch))
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {}
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text(stringResource(R.string.local_branches)) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text(stringResource(R.string.remote_branches)) }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text(stringResource(R.string.tags)) }
                )
            }

            val filteredBranches = when (selectedTabIndex) {
                0 -> branches.filter { it.type == BranchType.LOCAL }
                1 -> branches.filter { it.type == BranchType.REMOTE }
                else -> branches.filter { it.type == BranchType.TAG }
            }

            AnimatedContent(
                targetState = filteredBranches,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "BranchListAnimation"
            ) { targetBranches ->
                if (targetBranches.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = when(selectedTabIndex) {
                                    0 -> Icons.Default.AccountTree
                                    1 -> Icons.Default.CloudOff
                                    else -> Icons.Default.Label
                                },
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = when(selectedTabIndex) {
                                    0 -> "No local branches"
                                    1 -> "No remote branches found"
                                    else -> "No tags found"
                                },
                                color = MaterialTheme.colorScheme.outline,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(targetBranches, key = { it.fullRefName }) { branch ->
                            BranchCard(
                                branch = branch,
                                onClick = { onCheckoutClick(branch) },
                                onDelete = {
                                    branchToDelete = branch
                                }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }

        if (showCreateDialog) {
            InputSheet(
                title = stringResource(R.string.create_branch_title),
                onDismiss = { showCreateDialog = false },
                onConfirm = { name ->
                    scope.launch {
                        GitManager.createBranch(repoRoot, name).onSuccess {
                            Toast.makeText(context, context.getString(R.string.branch_create_success, name), Toast.LENGTH_SHORT).show()
                            refresh()
                            showCreateDialog = false
                        }.onFailure {
                            errorMessage = it.message
                        }
                    }
                }
            )
        }

        if (branchToCheckoutRemote != null) {
            val displayName = branchToCheckoutRemote!!.displayName
            val suggestedLocalName = displayName.substringAfter("/")
            InputSheet(
                title = stringResource(R.string.checkout_remote_title),
                initialValue = suggestedLocalName,
                onDismiss = { branchToCheckoutRemote = null },
                onConfirm = { localName ->
                    scope.launch {
                        // 基于远程分支创建并检出本地分支
                        GitManager.createBranch(repoRoot, localName, startPoint = branchToCheckoutRemote!!.fullRefName).onSuccess {
                            Toast.makeText(context, context.getString(R.string.branch_checkout_success, localName), Toast.LENGTH_SHORT).show()
                            refresh()
                            branchToCheckoutRemote = null
                            selectedTabIndex = 0 // 切回本地列表
                        }.onFailure {
                            errorMessage = it.message
                        }
                    }
                }
            )
        }

        if (branchToDelete != null) {
            AlertDialog(
                onDismissRequest = { branchToDelete = null },
                title = { Text(stringResource(R.string.confirm_delete_title)) },
                text = { Text(stringResource(R.string.confirm_delete_branch_msg, branchToDelete!!.displayName)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val name = branchToDelete!!.fullRefName
                            scope.launch {
                                GitManager.deleteBranch(repoRoot, name).onSuccess {
                                    Toast.makeText(context, context.getString(R.string.branch_delete_success, branchToDelete!!.displayName), Toast.LENGTH_SHORT).show()
                                    refresh()
                                    branchToDelete = null
                                }.onFailure {
                                    errorMessage = it.message
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { branchToDelete = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showDirtyWarning && pendingCheckoutBranch != null) {
            AlertDialog(
                onDismissRequest = { showDirtyWarning = false },
                title = { Text(stringResource(R.string.dirty_checkout_title)) },
                text = { Text(stringResource(R.string.dirty_checkout_msg)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDirtyWarning = false
                            executeCheckout(pendingCheckoutBranch!!)
                        }
                    ) {
                        Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDirtyWarning = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        errorMessage?.let { ErrorDialog(error = it, onDismiss = { errorMessage = null }) }
    }
}

@Composable
fun BranchCard(
    branch: BranchInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (branch.isCurrent) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (branch.isCurrent) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) 
                 else null
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (branch.isCurrent) MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when(branch.type) {
                        BranchType.LOCAL -> Icons.Default.AccountTree
                        BranchType.REMOTE -> Icons.Default.Cloud
                        BranchType.TAG -> Icons.Default.Label
                    },
                    contentDescription = null,
                    tint = if (branch.isCurrent) MaterialTheme.colorScheme.onPrimary 
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = branch.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (branch.isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = if (branch.isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (branch.isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.current_label),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    if (branch.isTracked && branch.type == BranchType.REMOTE) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = stringResource(R.string.tracked),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(4.dp))
                
                Surface(
                    color = if (branch.isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = branch.shortHash,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (branch.isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (!branch.isCurrent && branch.type == BranchType.LOCAL) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else if (branch.type == BranchType.REMOTE) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = if (branch.isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                           else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
