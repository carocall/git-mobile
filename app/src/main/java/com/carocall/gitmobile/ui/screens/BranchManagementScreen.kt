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
import com.carocall.gitmobile.ui.component.ErrorDialog
import com.carocall.gitmobile.ui.component.InputSheet
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchManagementScreen(repoRoot: File, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var branches by remember { mutableStateOf<List<BranchInfo>>(emptyList()) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var branchToDelete by remember { mutableStateOf<BranchInfo?>(null) }
    var branchToCheckoutRemote by remember { mutableStateOf<BranchInfo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            branches = GitManager.getBranches(repoRoot)
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
            }

            val filteredBranches = if (selectedTabIndex == 0) {
                branches.filter { !it.isRemote }
            } else {
                branches.filter { it.isRemote }
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
                                imageVector = if (selectedTabIndex == 0) Icons.Default.AccountTree else Icons.Default.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = if (selectedTabIndex == 0) "No local branches" else "No remote branches found",
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
                        items(targetBranches, key = { it.name }) { branch ->
                            BranchCard(
                                branch = branch,
                                onClick = {
                                    if (branch.isCurrent) return@BranchCard
                                    if (branch.isRemote) {
                                        branchToCheckoutRemote = branch
                                    } else {
                                        scope.launch {
                                            GitManager.checkoutBranch(repoRoot, branch.name).onSuccess {
                                                Toast.makeText(context, context.getString(R.string.branch_checkout_success, branch.name), Toast.LENGTH_SHORT).show()
                                                refresh()
                                            }.onFailure {
                                                errorMessage = it.message
                                            }
                                        }
                                    }
                                },
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
            val remoteName = branchToCheckoutRemote!!.name
            val suggestedLocalName = remoteName.substringAfter("/")
            InputSheet(
                title = stringResource(R.string.checkout_remote_title),
                initialValue = suggestedLocalName,
                onDismiss = { branchToCheckoutRemote = null },
                onConfirm = { localName ->
                    scope.launch {
                        // 基于远程分支创建并检出本地分支
                        GitManager.createBranch(repoRoot, localName, startPoint = "origin/$remoteName").onSuccess {
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
                text = { Text(stringResource(R.string.confirm_delete_branch_msg, branchToDelete!!.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val name = branchToDelete!!.name
                            scope.launch {
                                GitManager.deleteBranch(repoRoot, name).onSuccess {
                                    Toast.makeText(context, context.getString(R.string.branch_delete_success, name), Toast.LENGTH_SHORT).show()
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

        errorMessage?.let { ErrorDialog(error = it, onDismiss = { errorMessage = null }) }
    }
}

@Composable
fun BranchCard(
    branch: BranchInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (branch.isCurrent) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = if (branch.isCurrent) 
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        else 
            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (branch.isCurrent) 2.dp else 0.dp)
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
                    imageVector = if (branch.isRemote) Icons.Default.Cloud else Icons.Default.AccountTree,
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
                        text = branch.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (branch.isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = if (branch.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                }
                
                Spacer(Modifier.height(4.dp))
                
                Surface(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = branch.shortHash,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (!branch.isCurrent && !branch.isRemote) {
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
            } else if (branch.isRemote) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
