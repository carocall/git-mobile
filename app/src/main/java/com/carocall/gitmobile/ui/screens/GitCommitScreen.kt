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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.carocall.gitmobile.R
import com.carocall.gitmobile.data.git.GitManager
import com.carocall.gitmobile.data.model.CommitInfo
import com.carocall.gitmobile.data.model.GitAccount
import com.carocall.gitmobile.data.model.RepoStatus
import com.carocall.gitmobile.ui.component.ErrorDialog
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
    onViewDiff: (String, String, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(RepoStatus()) }
    var history by remember { mutableStateOf<List<CommitInfo>>(emptyList()) }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }
    var commitMessage by remember { mutableStateOf("") }

    var sessionToken by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loadingStatus by remember { mutableStateOf("") }
    var remoteConfig by remember { mutableStateOf(com.carocall.gitmobile.data.model.RemoteProfile("", "", null)) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isInitialLoading by remember { mutableStateOf(true) }
    var localIdentity by remember { mutableStateOf("" to "") }

    var showDiscardConfirmDialog by remember { mutableStateOf<List<String>?>(null) }
    var showIdentityMissingDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            status = GitManager.getStatus(repoRoot)
            history = GitManager.getHistory(repoRoot)
            remoteConfig = GitManager.getRemoteConfig(repoRoot)
            localIdentity = GitManager.getLocalIdentity(repoRoot)
            if (selectedFiles.isEmpty() && status.allChanges.isNotEmpty()) {
                selectedFiles = status.allChanges.map { it.first }.toSet()
            }
            isInitialLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun withRemoteConfig(forceAuth: Boolean = true, action: (String, String, String) -> Unit) {
        scope.launch {
            val config = GitManager.getRemoteConfig(repoRoot)
            remoteConfig = config
            val url = config.url
            val accountId = config.accountId
            
            if (config.url.isNotBlank() && accountId.isNullOrBlank() && forceAuth) {
                Toast.makeText(context, "No account associated with this repository. Please select an account in Remote Settings.", Toast.LENGTH_LONG).show()
                onGoToRemoteConfig(repoRoot.absolutePath)
                return@launch
            }

            val account = if (!accountId.isNullOrBlank()) gitAccounts.find { it.id == accountId } else null
            if (config.url.isNotBlank() && !accountId.isNullOrBlank() && account == null && forceAuth) {
                Toast.makeText(context, "Account not found. Please re-configure.", Toast.LENGTH_LONG).show()
                onGoToRemoteConfig(repoRoot.absolutePath)
                return@launch
            }

            val user = account?.username ?: ""
            val savedToken = account?.token ?: ""
            val currentToken = if (sessionToken.isBlank()) savedToken else sessionToken
            
            if (url.isBlank()) {
                onGoToRemoteConfig(repoRoot.absolutePath)
            } else if (forceAuth && (user.isBlank() || currentToken.isBlank())) {
                Toast.makeText(context, "Authentication failed.", Toast.LENGTH_SHORT).show()
                onGoToRemoteConfig(repoRoot.absolutePath)
            } else {
                action(url, user, currentToken)
            }
        }
    }

    fun performPull(user: String, token: String) {
        scope.launch {
            isLoading = true
            loadingStatus = context.getString(R.string.pull)
            GitManager.pull(repoRoot, user, token).onSuccess {
                Toast.makeText(context, context.getString(R.string.pull_success), Toast.LENGTH_SHORT).show()
                if (token.isNotBlank()) sessionToken = token
                refresh()
            }.onFailure { e -> errorMessage = context.getString(R.string.pull_failed, e.message) }
            isLoading = false
            loadingStatus = ""
        }
    }

    fun performPush(user: String, token: String) {
        scope.launch {
            isLoading = true
            loadingStatus = context.getString(R.string.push)
            GitManager.push(repoRoot, user, token).onSuccess {
                Toast.makeText(context, context.getString(R.string.push_success), Toast.LENGTH_SHORT).show()
                if (token.isNotBlank()) sessionToken = token
                refresh()
            }.onFailure { e -> errorMessage = context.getString(R.string.push_failed, e.message) }
            isLoading = false
            loadingStatus = ""
        }
    }

    fun performSync(user: String, token: String) {
        scope.launch {
            isLoading = true
            loadingStatus = context.getString(R.string.syncing)
            GitManager.sync(repoRoot, user, token, context.getString(R.string.sync_pull_failed), context.getString(R.string.push)).onSuccess {
                Toast.makeText(context, context.getString(R.string.sync_success), Toast.LENGTH_SHORT).show()
                if (token.isNotBlank()) sessionToken = token
                refresh()
            }.onFailure { e -> errorMessage = context.getString(R.string.sync_failed, e.message) }
            isLoading = false
            loadingStatus = ""
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
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onGoToRemoteConfig(repoRoot.absolutePath) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.remote_repo), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (remoteConfig.url.isNotBlank()) remoteConfig.url.substringAfterLast("/").substringBefore(".git").ifBlank { "Git Remote" } else stringResource(R.string.no_remote_config),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (!isInitialLoading && remoteConfig.url.isNotBlank()) {
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
                    Row(modifier = Modifier.fillMaxWidth().clickable { onGoToBranchManagement(repoRoot.absolutePath) }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sync, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(text = status.branch, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    }

                    if (!isInitialLoading && remoteConfig.url.isNotBlank()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            IconButton(onClick = { withRemoteConfig(false) { _, u, t -> performPull(u, t) } }) { Icon(Icons.Default.Download, stringResource(R.string.pull)) }
                            IconButton(onClick = { withRemoteConfig(true) { _, u, t -> performSync(u, t) } }) { Icon(Icons.Default.Sync, stringResource(R.string.one_click_sync)) }
                            IconButton(onClick = { withRemoteConfig(true) { _, u, t -> performPush(u, t) } }) { Icon(Icons.Default.Upload, stringResource(R.string.push)) }
                        }
                    }
                }
            }

            SecondaryTabRow(selectedTabIndex = selectedTabIndex, containerColor = Color.Transparent, divider = {}) {
                Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text(stringResource(R.string.pending_changes), style = MaterialTheme.typography.titleSmall) })
                Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text(stringResource(R.string.recent_history), style = MaterialTheme.typography.titleSmall) })
            }

            if (selectedTabIndex == 0) {
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    placeholder = { Text(stringResource(R.string.commit_msg_placeholder)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    maxLines = 3,
                    trailingIcon = {
                        if (commitMessage.isNotBlank()) {
                            IconButton(onClick = {
                                scope.launch {
                                    val name = localIdentity.first.ifBlank { globalGitName }
                                    val email = localIdentity.second.ifBlank { globalGitEmail }
                                    if (name.isBlank() || email.isBlank()) { showIdentityMissingDialog = true } 
                                    else { GitManager.commit(repoRoot, commitMessage, selectedFiles.toList(), name, email).onSuccess { commitMessage = ""; selectedFiles = emptySet(); refresh() } }
                                }
                            }) { Icon(Icons.Default.Check, stringResource(R.string.confirm), tint = MaterialTheme.colorScheme.primary) }
                        }
                    }
                )

                // 仓库身份信息 (显示当前谁在提交)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { showIdentityMissingDialog = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (localIdentity.first.isNotBlank()) "${localIdentity.first} <${localIdentity.second}>" 
                               else if (globalGitName.isNotBlank()) "${globalGitName} <${globalGitEmail}> (Global)"
                               else stringResource(R.string.no_identity),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LazyColumn(Modifier.fillMaxSize()) {
                    if (status.hasConflicts) {
                        item {
                            Text("Conflicts (${status.conflicts.size})", modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)).padding(16.dp, 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        }
                        items(status.conflicts.toList()) { path ->
                            Row(Modifier.fillMaxWidth().clickable { onViewDiff(repoRoot.absolutePath, "HEAD", path) }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Dangerous, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(path, fontSize = 14.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    item {
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(16.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.local_changes, status.allChanges.size), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            if (selectedFiles.isNotEmpty()) {
                                IconButton(onClick = { showDiscardConfirmDialog = selectedFiles.toList() }, modifier = Modifier.size(24.dp)) { Icon(Icons.AutoMirrored.Filled.Undo, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }
                                Spacer(Modifier.width(16.dp))
                            }
                            if (status.allChanges.isNotEmpty()) {
                                val all = selectedFiles.size == status.allChanges.size
                                Text(if (all) stringResource(R.string.unselect_all) else stringResource(R.string.select_all), modifier = Modifier.clickable { selectedFiles = if (all) emptySet() else status.allChanges.map { it.first }.toSet() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    items(status.allChanges) { (path, type) ->
                        Row(Modifier.fillMaxWidth().clickable { onViewDiff(repoRoot.absolutePath, "HEAD", path) }.padding(16.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selectedFiles.contains(path), onCheckedChange = { selectedFiles = if (it) selectedFiles + path else selectedFiles - path }, modifier = Modifier.size(40.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(path, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val color = when (type) {
                                    "Untracked", "Added" -> Color(0xFF4CAF50)
                                    "Removed" -> Color(0xFFE91E63)
                                    else -> Color(0xFF2196F3)
                                }
                                Text(type, color = color, style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { showDiscardConfirmDialog = listOf(path) }) { Icon(Icons.AutoMirrored.Filled.Undo, null, modifier = Modifier.size(18.dp), tint = Color.Gray) }
                        }
                    }
                }
            } else {
                // Pre-process history data: Group by date
                val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
                val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                val groupedHistory = remember(history) { history.groupBy { dateFormat.format(Date(it.time)) } }

                LazyColumn(Modifier.fillMaxSize()) {
                    groupedHistory.forEach { (date, commits) ->
                        // Date header
                        item(key = date) {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                            )
                        }

                        // Commits for this date
                        itemsIndexed(commits, key = { _, c -> c.id }) { index, commit ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onViewCommit(repoRoot.absolutePath, commit.id) }
                                    .padding(horizontal = 16.dp)
                                    .height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Timeline Visual
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(24.dp).fillMaxHeight()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .weight(1f)
                                            .background(if (index == 0) Color.Transparent else MaterialTheme.colorScheme.outlineVariant)
                                    )
                                    Surface(
                                        modifier = Modifier.size(8.dp),
                                        shape = CircleShape,
                                        color = if (commit.isRemote) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    ) {}
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .weight(1f)
                                            .background(if (index == commits.size - 1) Color.Transparent else MaterialTheme.colorScheme.outlineVariant)
                                    )
                                }

                                // Content
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp, bottom = 16.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = commit.message,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (commit.isRemote) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                shape = CircleShape,
                                                modifier = Modifier.padding(start = 8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Cloud,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(10.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        text = "Cloud",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = commit.author,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, false)
                                        )
                                        Text(
                                            text = " • ${timeFormat.format(Date(commit.time))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            text = commit.id.take(7),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showIdentityMissingDialog) {
            var name by remember { mutableStateOf(localIdentity.first.ifBlank { globalGitName }) }
            var email by remember { mutableStateOf(localIdentity.second.ifBlank { globalGitEmail }) }
            AlertDialog(
                onDismissRequest = { showIdentityMissingDialog = false },
                title = { Text(stringResource(R.string.commit_author_section)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.git_identity_description), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.author_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.author_email)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = { TextButton(onClick = { scope.launch { GitManager.saveLocalIdentity(repoRoot, name, email); refresh(); showIdentityMissingDialog = false } }) { Text(stringResource(R.string.save)) } },
                dismissButton = { TextButton(onClick = { showIdentityMissingDialog = false }) { Text(stringResource(R.string.cancel)) } }
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
                        scope.launch {
                            GitManager.discardChanges(repoRoot, paths).onSuccess { selectedFiles = selectedFiles - paths.toSet(); refresh(); showDiscardConfirmDialog = null }
                                .onFailure { errorMessage = it.message }
                        }
                    }) { Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { showDiscardConfirmDialog = null }) { Text(stringResource(R.string.cancel)) } }
            )
        }

        if (isLoading) {
            AlertDialog(onDismissRequest = {}, confirmButton = {}, text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Spacer(Modifier.width(16.dp)); Text(loadingStatus.ifBlank { stringResource(R.string.syncing) }) } })
        }
        errorMessage?.let { ErrorDialog(error = it, onDismiss = { errorMessage = null }) }
    }
}
