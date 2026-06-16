package com.carocall.gitmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.carocall.gitmobile.R
import com.carocall.gitmobile.data.git.GitManager
import com.carocall.gitmobile.data.model.CommitInfo
import com.carocall.gitmobile.data.model.RepoStatus
import com.carocall.gitmobile.ui.component.CommitChangesSheet
import com.carocall.gitmobile.ui.component.DiffSheet
import com.carocall.gitmobile.ui.component.InputSheet
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitCommitScreen(
    repoRoot: File, 
    onBack: () -> Unit,
    onGoToRemoteConfig: (String) -> Unit = {},
    onGoToBranchManagement: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(RepoStatus()) }
    var history by remember { mutableStateOf<List<CommitInfo>>(emptyList()) }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }
    var commitMessage by remember { mutableStateOf("") }

    var sessionToken by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var remoteConfig by remember { mutableStateOf(Triple("", "", "")) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // 查看提交变更的状态
    var selectedCommit by remember { mutableStateOf<CommitInfo?>(null) }
    var commitChanges by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedDiffFile by remember { mutableStateOf<String?>(null) }
    var fileDiffContent by remember { mutableStateOf("") }
    var showChangesDialog by remember { mutableStateOf(false) }
    var showDiffDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf<List<String>?>(null) }
    var showTagInput by remember { mutableStateOf<CommitInfo?>(null) }
    var showBranchInput by remember { mutableStateOf<CommitInfo?>(null) }

    fun refresh() {
        scope.launch {
            status = GitManager.getStatus(repoRoot)
            history = GitManager.getHistory(repoRoot)
            remoteConfig = GitManager.getRemoteConfig(repoRoot)
            // 只有当 selectedFiles 为空时才默认全选
            if (selectedFiles.isEmpty() && status.allChanges.isNotEmpty()) {
                selectedFiles = status.allChanges.map { it.first }.toSet()
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun viewCommitChanges(commit: CommitInfo) {
        scope.launch {
            isLoading = true
            selectedCommit = commit
            commitChanges = GitManager.getCommitChanges(repoRoot, commit.id)
            showChangesDialog = true
            isLoading = false
        }
    }

    fun viewFileDiff(commitId: String, filePath: String) {
        scope.launch {
            isLoading = true
            selectedDiffFile = filePath
            fileDiffContent = GitManager.getFileDiff(repoRoot, commitId, filePath)
            showDiffDialog = true
            isLoading = false
        }
    }

    fun withRemoteConfig(forceAuth: Boolean = true, action: (String, String, String) -> Unit) {
        scope.launch {
            val config = GitManager.getRemoteConfig(repoRoot)
            remoteConfig = config
            val (url, user, savedToken) = config
            val currentToken = if (sessionToken.isBlank()) savedToken else sessionToken
            
            if (url.isBlank()) {
                onGoToRemoteConfig(repoRoot.absolutePath)
            } else if (forceAuth && (user.isBlank() || currentToken.isBlank())) {
                onGoToRemoteConfig(repoRoot.absolutePath)
            } else {
                action(url, user, currentToken)
            }
        }
    }

    fun performPull(user: String, token: String) {
        scope.launch {
            isLoading = true
            GitManager.pull(repoRoot, user, token).onSuccess {
                Toast.makeText(context, context.getString(R.string.pull_success), Toast.LENGTH_SHORT).show()
                if (token.isNotBlank()) sessionToken = token
                refresh()
            }.onFailure { e ->
                val msg = e.message ?: ""
                if (msg.contains("not authorized", ignoreCase = true) || msg.contains("auth", ignoreCase = true)) {
                    Toast.makeText(context, context.getString(R.string.auth_required), Toast.LENGTH_SHORT).show()
                    onGoToRemoteConfig(repoRoot.absolutePath)
                } else {
                    Toast.makeText(context, context.getString(R.string.pull_failed, msg), Toast.LENGTH_LONG).show()
                }
            }
            isLoading = false
        }
    }

    fun performPush(user: String, token: String) {
        scope.launch {
            isLoading = true
            GitManager.push(repoRoot, user, token).onSuccess {
                Toast.makeText(context, context.getString(R.string.push_success), Toast.LENGTH_SHORT).show()
                if (token.isNotBlank()) sessionToken = token
                refresh()
            }.onFailure { e ->
                val msg = e.message ?: ""
                if (msg.contains("not authorized", ignoreCase = true) || msg.contains("auth", ignoreCase = true)) {
                    Toast.makeText(context, context.getString(R.string.auth_required), Toast.LENGTH_SHORT).show()
                    onGoToRemoteConfig(repoRoot.absolutePath)
                } else {
                    Toast.makeText(context, context.getString(R.string.push_failed, msg), Toast.LENGTH_LONG).show()
                }
            }
            isLoading = false
        }
    }

    fun performSync(url: String, user: String, token: String) {
        scope.launch {
            isLoading = true
            GitManager.sync(repoRoot, url, user, token).onSuccess {
                Toast.makeText(context, context.getString(R.string.sync_success), Toast.LENGTH_SHORT).show()
                if (token.isNotBlank()) sessionToken = token
                refresh()
            }.onFailure { e ->
                val msg = e.message ?: ""
                if (msg.contains("not authorized", ignoreCase = true) || msg.contains("auth", ignoreCase = true)) {
                    Toast.makeText(context, context.getString(R.string.auth_required), Toast.LENGTH_SHORT).show()
                    onGoToRemoteConfig(repoRoot.absolutePath)
                } else {
                    Toast.makeText(context, context.getString(R.string.sync_failed, msg), Toast.LENGTH_LONG).show()
                }
            }
            isLoading = false
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
            // 远程仓库信息卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onGoToRemoteConfig(repoRoot.absolutePath) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.remote_repo),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (remoteConfig.first.isNotBlank()) {
                                    val name = remoteConfig.first.substringAfterLast("/").substringBefore(".git")
                                    if (name.isBlank()) "Git Remote" else name
                                } else stringResource(R.string.no_remote_config),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (remoteConfig.first.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        stringResource(R.string.connected),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { 
                            onGoToBranchManagement(repoRoot.absolutePath)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = status.branch,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (remoteConfig.first.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(onClick = { withRemoteConfig(forceAuth = false) { _, u, t -> performPull(u, t) } }) {
                                Icon(Icons.Default.Download, stringResource(R.string.pull))
                            }
                            IconButton(onClick = { withRemoteConfig(forceAuth = true) { url, u, t -> performSync(url, u, t) } }) {
                                Icon(Icons.Default.Sync, stringResource(R.string.one_click_sync))
                            }
                            IconButton(onClick = { withRemoteConfig(forceAuth = true) { _, u, t -> performPush(u, t) } }) {
                                Icon(Icons.Default.Upload, stringResource(R.string.push))
                            }
                        }
                    }
                }
            }

            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text(stringResource(R.string.pending_changes), style = MaterialTheme.typography.titleSmall) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text(stringResource(R.string.recent_history), style = MaterialTheme.typography.titleSmall) }
                )
            }

            if (selectedTabIndex == 0) {
                // 提交信息输入框放在变更列表上方
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
                                    GitManager.commit(repoRoot, commitMessage, selectedFiles.toList())
                                        .onSuccess { commitMessage = ""; selectedFiles = emptySet(); refresh() }
                                }
                            }) {
                                Icon(Icons.Default.Check, stringResource(R.string.confirm), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                )

                if (commitMessage.isNotBlank()) {
                    Text(
                        stringResource(R.string.commit_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.local_changes, status.allChanges.size), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            
                            if (selectedFiles.isNotEmpty()) {
                                IconButton(
                                    onClick = { showDiscardConfirmDialog = selectedFiles.toList() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.discard_changes), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(16.dp))
                            }

                            if (status.allChanges.isNotEmpty()) {
                                val allSelected = selectedFiles.size == status.allChanges.size
                                Text(
                                    if (allSelected) stringResource(R.string.unselect_all) else stringResource(R.string.select_all),
                                    modifier = Modifier.clickable {
                                        selectedFiles = if (allSelected) emptySet() else status.allChanges.map { it.first }.toSet()
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    items(status.allChanges) { (path, type) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { 
                                selectedFiles = if (selectedFiles.contains(path)) selectedFiles - path else selectedFiles + path 
                            }.padding(horizontal = 16.dp, vertical = 4.dp), 
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedFiles.contains(path), 
                                onCheckedChange = { selectedFiles = if (it) selectedFiles + path else selectedFiles - path },
                                modifier = Modifier.size(40.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(path, fontSize = 14.sp)
                                val color = when (type) {
                                    "Untracked" -> Color(0xFF4CAF50) // 绿色
                                    "Added" -> Color(0xFF4CAF50)     // 绿色
                                    "Removed" -> Color(0xFFE91E63)   // 红色
                                    else -> Color(0xFF2196F3)        // 蓝色 (Modified)
                                }
                                Text(type, color = color, style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { showDiscardConfirmDialog = listOf(path) }) {
                                Icon(Icons.AutoMirrored.Filled.Undo, null, modifier = Modifier.size(18.dp), tint = Color.Gray)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(history) { commit ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { viewCommitChanges(commit) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = commit.message,
                                        maxLines = 1,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (commit.isRemote) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = MaterialTheme.shapes.extraSmall,
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Text(
                                                text = "Cloud",
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${commit.author} • ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(commit.time))}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            Text(
                                text = commit.id.take(7),
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }

        if (showChangesDialog) {
            CommitChangesSheet(
                commit = selectedCommit,
                changes = commitChanges,
                onDismiss = { showChangesDialog = false },
                onFileClick = { path -> viewFileDiff(selectedCommit!!.id, path) },
                onAction = { action ->
                    when (action) {
                        "TAG" -> showTagInput = selectedCommit
                        "BRANCH" -> showBranchInput = selectedCommit
                        "CHECKOUT" -> {
                            scope.launch {
                                GitManager.checkoutCommit(repoRoot, selectedCommit!!.id).onSuccess {
                                    Toast.makeText(context, "Checked out ${selectedCommit!!.id.take(7)}", Toast.LENGTH_SHORT).show()
                                    refresh()
                                    showChangesDialog = false
                                }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                            }
                        }
                        "CHERRY_PICK" -> {
                            scope.launch {
                                GitManager.cherryPick(repoRoot, selectedCommit!!.id).onSuccess {
                                    Toast.makeText(context, "Cherry picked!", Toast.LENGTH_SHORT).show()
                                    refresh()
                                    showChangesDialog = false
                                }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                            }
                        }
                        "REVERT" -> {
                            scope.launch {
                                GitManager.revertCommit(repoRoot, selectedCommit!!.id).onSuccess {
                                    Toast.makeText(context, "Reverted!", Toast.LENGTH_SHORT).show()
                                    refresh()
                                    showChangesDialog = false
                                }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                            }
                        }
                        "DROP" -> {
                            // TODO: Implement Drop (Interactive Rebase skip)
                            Toast.makeText(context, "Drop not yet implemented", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        showTagInput?.let { commit ->
            InputSheet(
                title = "Add Tag to ${commit.id.take(7)}",
                onDismiss = { showTagInput = null },
                onConfirm = { tagName ->
                    scope.launch {
                        GitManager.addTag(repoRoot, tagName, commit.id).onSuccess {
                            Toast.makeText(context, "Tag $tagName added", Toast.LENGTH_SHORT).show()
                            showTagInput = null
                            refresh()
                        }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                    }
                }
            )
        }

        showBranchInput?.let { commit ->
            InputSheet(
                title = "Create Branch from ${commit.id.take(7)}",
                onDismiss = { showBranchInput = null },
                onConfirm = { branchName ->
                    scope.launch {
                        GitManager.createBranch(repoRoot, branchName, startPoint = commit.id).onSuccess {
                            Toast.makeText(context, "Branch $branchName created", Toast.LENGTH_SHORT).show()
                            showBranchInput = null
                            refresh()
                        }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                    }
                }
            )
        }

        if (showDiffDialog) {
            DiffSheet(
                fileName = selectedDiffFile?.substringAfterLast("/") ?: "",
                diffContent = fileDiffContent,
                onDismiss = { showDiffDialog = false }
            )
        }

        if (showDiscardConfirmDialog != null) {
            val paths = showDiscardConfirmDialog!!
            AlertDialog(
                onDismissRequest = { showDiscardConfirmDialog = null },
                title = { Text(stringResource(R.string.discard_changes_confirm_title)) },
                text = { Text(stringResource(R.string.discard_changes_confirm_msg, paths.size)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                GitManager.discardChanges(repoRoot, paths).onSuccess {
                                    selectedFiles = selectedFiles - paths.toSet()
                                    refresh()
                                    showDiscardConfirmDialog = null
                                }.onFailure {
                                    Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardConfirmDialog = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (isLoading) {
            AlertDialog(onDismissRequest = {}, confirmButton = {}, text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Spacer(Modifier.width(16.dp)); Text(stringResource(R.string.syncing)) } })
        }
    }
}
