package com.carocall.gitmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.*
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carocall.gitmobile.R
import com.carocall.gitmobile.data.model.CommitInfo
import com.carocall.gitmobile.data.model.GitAccount
import com.carocall.gitmobile.data.model.RepoStatus
import com.carocall.gitmobile.ui.component.ErrorDialog
import com.carocall.gitmobile.ui.viewmodels.GitCommitViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "shimmerOffsetX"
    )

    background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.LightGray.copy(alpha = 0.3f),
                Color.LightGray.copy(alpha = 0.5f),
                Color.LightGray.copy(alpha = 0.3f),
            ),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    ).onGloballyPositioned {
        size = it.size
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitCommitScreen(
    repoRoot: File,
    globalGitName: String = "",
    globalGitEmail: String = "",
    gitAccounts: List<GitAccount> = emptyList(),
    onBack: () -> Unit,
    onGoToRemoteConfig: (String) -> Unit = {},
    onGoToBranchManagement: (String) -> Unit = {},
    onViewCommit: (String, String) -> Unit = { _, _ -> },
    onViewDiff: (String, String, String) -> Unit = { _, _, _ -> },
    viewModel: GitCommitViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var sessionToken by remember { mutableStateOf("") }
    var showDiscardConfirmDialog by remember { mutableStateOf<List<String>?>(null) }
    var showIdentityMissingDialog by remember { mutableStateOf(false) }

    LaunchedEffect(repoRoot) {
        viewModel.init(repoRoot)
    }

    fun withRemoteConfig(forceAuth: Boolean = true, action: (String, String, String) -> Unit) {
        val config = uiState.remoteConfig
        val url = config.url
        val accountId = config.accountId
        
        if (url.isNotBlank() && accountId.isNullOrBlank() && forceAuth) {
            Toast.makeText(context, "No account associated with this repository. Please select an account in Remote Settings.", Toast.LENGTH_LONG).show()
            onGoToRemoteConfig(repoRoot.absolutePath)
            return
        }

        val account = if (!accountId.isNullOrBlank()) gitAccounts.find { it.id == accountId } else null
        if (url.isNotBlank() && !accountId.isNullOrBlank() && account == null && forceAuth) {
            Toast.makeText(context, "Account not found. Please re-configure.", Toast.LENGTH_LONG).show()
            onGoToRemoteConfig(repoRoot.absolutePath)
            return
        }

        val user = account?.username ?: ""
        val savedToken = account?.token ?: ""
        val currentToken = sessionToken.ifBlank { savedToken }
        
        if (url.isBlank()) {
            onGoToRemoteConfig(repoRoot.absolutePath)
        } else if (forceAuth && (user.isBlank() || currentToken.isBlank())) {
            Toast.makeText(context, "Authentication failed.", Toast.LENGTH_SHORT).show()
            onGoToRemoteConfig(repoRoot.absolutePath)
        } else {
            action(url, user, currentToken)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.source_control)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            RemoteInfoCard(
                url = uiState.remoteConfig.url,
                branch = uiState.status.branch,
                isInitialLoading = uiState.isInitialLoading,
                onRemoteClick = { onGoToRemoteConfig(repoRoot.absolutePath) },
                onBranchClick = { onGoToBranchManagement(repoRoot.absolutePath) },
                onPull = { withRemoteConfig(false) { _, u, t -> viewModel.performPull(context, u, t) } },
                onSync = { withRemoteConfig(true) { _, u, t -> viewModel.performSync(context, u, t) } },
                onPush = { withRemoteConfig(true) { _, u, t -> viewModel.performPush(context, u, t) } }
            )

            SecondaryTabRow(selectedTabIndex = selectedTabIndex, containerColor = Color.Transparent, divider = {}) {
                Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text(stringResource(R.string.pending_changes), style = MaterialTheme.typography.titleSmall) })
                Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text(stringResource(R.string.recent_history), style = MaterialTheme.typography.titleSmall) })
            }

            if (selectedTabIndex == 0) {
                CommitActionArea(
                    commitMessage = uiState.commitMessage,
                    onMessageChange = { viewModel.updateCommitMessage(it) },
                    authorName = if (uiState.localIdentity.first.isNotBlank()) uiState.localIdentity.first else globalGitName,
                    onAuthorClick = { showIdentityMissingDialog = true },
                    canCommit = uiState.commitMessage.isNotBlank() && uiState.selectedFiles.isNotEmpty(),
                    onCommit = {
                        val name = uiState.localIdentity.first.ifBlank { globalGitName }
                        val email = uiState.localIdentity.second.ifBlank { globalGitEmail }
                        if (name.isBlank() || email.isBlank()) {
                            showIdentityMissingDialog = true
                        } else {
                            viewModel.commit(uiState.commitMessage, uiState.selectedFiles.toList(), name, email)
                        }
                    }
                )

                PendingChangesList(
                    status = uiState.status,
                    selectedFiles = uiState.selectedFiles,
                    isInitialLoading = uiState.isInitialLoading,
                    onToggleFile = { viewModel.toggleFileSelection(it) },
                    onToggleAll = { viewModel.setAllFilesSelected(it) },
                    onDiscard = { showDiscardConfirmDialog = it },
                    onViewDiff = { path -> onViewDiff(repoRoot.absolutePath, "HEAD", path) }
                )
            } else {
                GitHistoryTimeline(
                    history = uiState.history,
                    hasMore = uiState.hasMoreHistory,
                    isLoading = uiState.isHistoryLoading,
                    onLoadMore = { viewModel.loadMoreHistory() },
                    onViewCommit = { commitId -> onViewCommit(repoRoot.absolutePath, commitId) }
                )
            }
        }

        // Dialogs
        if (showIdentityMissingDialog) {
            IdentityDialog(
                initialName = uiState.localIdentity.first.ifBlank { globalGitName },
                initialEmail = uiState.localIdentity.second.ifBlank { globalGitEmail },
                onDismiss = { showIdentityMissingDialog = false },
                onSave = { name, email ->
                    viewModel.saveLocalIdentity(name, email)
                    showIdentityMissingDialog = false
                }
            )
        }

        if (showDiscardConfirmDialog != null) {
            val paths = showDiscardConfirmDialog!!
            AlertDialog(
                onDismissRequest = { showDiscardConfirmDialog = null },
                title = { Text(stringResource(R.string.discard_changes_confirm_title)) },
                text = { Text(stringResource(R.string.discard_changes_confirm_msg, paths.size)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.discardChanges(paths)
                        showDiscardConfirmDialog = null
                    }) { Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { showDiscardConfirmDialog = null }) { Text(stringResource(R.string.cancel)) } }
            )
        }

        if (uiState.isLoading) {
            LoadingDialog(uiState.loadingStatus, uiState.gitProgress)
        }

        uiState.errorMessage?.let { ErrorDialog(error = it, onDismiss = { viewModel.clearErrorMessage() }) }
    }
}

@Composable
fun RemoteInfoCard(
    url: String,
    branch: String,
    isInitialLoading: Boolean,
    onRemoteClick: () -> Unit,
    onBranchClick: () -> Unit,
    onPull: () -> Unit,
    onSync: () -> Unit,
    onPush: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp)) {
            if (isInitialLoading) {
                // 骨架屏：标题
                Box(Modifier.size(60.dp, 12.dp).clip(CircleShape).shimmerEffect())
                Spacer(Modifier.height(8.dp))
                // 骨架屏：仓库名
                Box(Modifier.size(120.dp, 18.dp).clip(CircleShape).shimmerEffect())
                Spacer(Modifier.height(16.dp))
                // 骨架屏：分支名
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).clip(CircleShape).shimmerEffect())
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(80.dp, 14.dp).clip(CircleShape).shimmerEffect())
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onRemoteClick() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.remote_repo), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (url.isNotBlank()) url.substringAfterLast("/").substringBefore(".git").ifBlank { "Git Remote" } else stringResource(R.string.no_remote_config),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (url.isNotBlank()) {
                        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = MaterialTheme.shapes.extraSmall) {
                            Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.connected), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth().clickable { onBranchClick() }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(text = branch.ifBlank { "..." }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                }

                if (url.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = onPull) { Icon(Icons.Default.Download, stringResource(R.string.pull)) }
                        IconButton(onClick = onSync) { Icon(Icons.Default.Sync, stringResource(R.string.one_click_sync)) }
                        IconButton(onClick = onPush) { Icon(Icons.Default.Upload, stringResource(R.string.push)) }
                    }
                }
            }
        }
    }
}

@Composable
fun CommitActionArea(
    commitMessage: String,
    onMessageChange: (String) -> Unit,
    authorName: String,
    onAuthorClick: () -> Unit,
    canCommit: Boolean,
    onCommit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = commitMessage,
                onValueChange = onMessageChange,
                placeholder = { Text(stringResource(R.string.commit_msg_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                shape = MaterialTheme.shapes.medium
            )
            
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).clickable { onAuthorClick() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = authorName.ifBlank { stringResource(R.string.no_identity) },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Button(
                    onClick = onCommit,
                    enabled = canCommit,
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}

@Composable
fun PendingChangesList(
    status: RepoStatus,
    selectedFiles: Set<String>,
    isInitialLoading: Boolean,
    onToggleFile: (String) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onDiscard: (List<String>) -> Unit,
    onViewDiff: (String) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (isInitialLoading) {
            item { Spacer(Modifier.height(8.dp)) }
            items(5) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                    Spacer(Modifier.width(16.dp))
                    Box(Modifier.weight(1f).height(16.dp).clip(CircleShape).shimmerEffect())
                    Spacer(Modifier.width(16.dp))
                    Box(Modifier.size(40.dp, 16.dp).clip(CircleShape).shimmerEffect())
                }
            }
        } else {
            if (status.hasConflicts) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(Modifier.padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Conflicts (${status.conflicts.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                items(status.conflicts.toList()) { path ->
                    ListItem(
                        headlineContent = { Text(path, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) },
                        leadingContent = { Icon(Icons.Default.Dangerous, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier.clickable { onViewDiff(path) }
                    )
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.local_changes, status.allChanges.size),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selectedFiles.isNotEmpty()) {
                        IconButton(onClick = { onDiscard(selectedFiles.toList()) }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                    TextButton(onClick = { onToggleAll(selectedFiles.size != status.allChanges.size) }) {
                        val all = selectedFiles.size == status.allChanges.size && status.allChanges.isNotEmpty()
                        Text(if (all) stringResource(R.string.unselect_all) else stringResource(R.string.select_all))
                    }
                }
            }
            
            items(status.allChanges) { (path, type) ->
                val color = when (type) {
                    "Untracked", "Added" -> Color(0xFF4CAF50)
                    "Removed" -> Color(0xFFE91E63)
                    else -> Color(0xFF2196F3)
                }
                ListItem(
                    headlineContent = { Text(path, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = {
                        Checkbox(
                            checked = selectedFiles.contains(path),
                            onCheckedChange = { onToggleFile(path) }
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = color.copy(alpha = 0.1f),
                                shape = MaterialTheme.shapes.extraSmall,
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, color.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = type.take(1).uppercase(),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = color,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = { onDiscard(listOf(path)) }) {
                                Icon(Icons.AutoMirrored.Filled.Undo, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    },
                    modifier = Modifier.clickable { onViewDiff(path) }
                )
            }
        }
    }
}

@Composable
fun GitHistoryTimeline(
    history: List<CommitInfo>,
    hasMore: Boolean,
    isLoading: Boolean,
    onLoadMore: () -> Unit,
    onViewCommit: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val groupedHistory = remember(history) { history.groupBy { dateFormat.format(Date(it.time)) } }

    LazyColumn(Modifier.fillMaxSize()) {
        groupedHistory.forEach { (date, commits) ->
            item(key = date) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                )
            }

            itemsIndexed(commits, key = { _, c -> c.id }) { index, commit ->
                if (commit.id == history.lastOrNull()?.id && hasMore && !isLoading) {
                    LaunchedEffect(commit.id) { onLoadMore() }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onViewCommit(commit.id) }.padding(horizontal = 16.dp).height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp).fillMaxHeight()) {
                        Box(modifier = Modifier.width(1.dp).weight(1f).background(if (index == 0) Color.Transparent else MaterialTheme.colorScheme.outlineVariant))
                        Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = if (commit.isRemote) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) {}
                        Box(modifier = Modifier.width(1.dp).weight(1f).background(if (index == commits.size - 1) Color.Transparent else MaterialTheme.colorScheme.outlineVariant))
                    }

                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp, bottom = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = commit.id.take(7), style = MaterialTheme.typography.labelSmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), modifier = Modifier.padding(end = 8.dp))
                            Text(text = commit.message, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (commit.isRemote) { Icon(Icons.Default.Cloud, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) }
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = commit.author, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                            Text(text = " • ${timeFormat.format(Date(commit.time))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
        
        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
fun IdentityDialog(
    initialName: String,
    initialEmail: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.commit_author_section)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.git_identity_description), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.author_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.author_email)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, email) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun LoadingDialog(status: String, progress: com.carocall.gitmobile.data.model.GitProgress?) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(status.ifBlank { stringResource(R.string.syncing) })
                }
                progress?.let { p ->
                    if (p.taskName.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text(p.displayString, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        if (!p.indeterminate) {
                            LinearProgressIndicator(progress = { p.progress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    )
}
