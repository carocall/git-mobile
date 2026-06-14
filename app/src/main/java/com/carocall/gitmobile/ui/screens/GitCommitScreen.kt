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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
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
import com.carocall.gitmobile.data.git.GitManager
import com.carocall.gitmobile.data.model.CommitInfo
import com.carocall.gitmobile.data.model.RepoStatus
import com.carocall.gitmobile.ui.component.PushDialog
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitCommitScreen(repoRoot: File, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(RepoStatus()) }
    var history by remember { mutableStateOf<List<CommitInfo>>(emptyList()) }
    var selectedStagedFiles by remember { mutableStateOf(setOf<String>()) }
    var selectedUnstagedFiles by remember { mutableStateOf(setOf<String>()) }
    var commitMessage by remember { mutableStateOf("") }

    var sessionToken by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var remoteConfig by remember { mutableStateOf(Triple("", "", "")) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // 查看提交变更的状态
    var selectedCommit by remember { mutableStateOf<CommitInfo?>(null) }
    var commitChanges by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedDiffFile by remember { mutableStateOf<String?>(null) }
    var fileDiffContent by remember { mutableStateOf("") }
    var showChangesDialog by remember { mutableStateOf(false) }
    var showDiffDialog by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            status = GitManager.getStatus(repoRoot)
            history = GitManager.getHistory(repoRoot)
            remoteConfig = GitManager.getRemoteConfig(repoRoot)
            // 自动选中所有暂存的文件
            selectedStagedFiles = status.staged.map { it.path }.toSet()
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
            
            // 如果 url 为空，无论如何都需要配置
            if (url.isBlank()) {
                showConfigDialog = true
            } else if (forceAuth && (user.isBlank() || currentToken.isBlank())) {
                // 如果强制需要认证（如推送、同步）且信息不全，则显示配置
                showConfigDialog = true
            } else {
                // 其他情况（如公开仓库拉取）直接执行
                action(url, user, currentToken)
            }
        }
    }

    fun performPull(user: String, token: String) {
        scope.launch {
            isLoading = true
            GitManager.pull(repoRoot, user, token).onSuccess {
                Toast.makeText(context, "拉取成功", Toast.LENGTH_SHORT).show()
                if (token.isNotBlank()) sessionToken = token
                refresh()
            }.onFailure { e ->
                val msg = e.message ?: ""
                if (msg.contains("not authorized", ignoreCase = true) || msg.contains("auth", ignoreCase = true)) {
                    Toast.makeText(context, "需要认证信息", Toast.LENGTH_SHORT).show()
                    showConfigDialog = true
                } else {
                    Toast.makeText(context, "拉取失败: $msg", Toast.LENGTH_LONG).show()
                }
            }
            isLoading = false
        }
    }

    fun performPush(user: String, token: String) {
        scope.launch {
            isLoading = true
            GitManager.push(repoRoot, user, token).onSuccess {
                Toast.makeText(context, "推送成功", Toast.LENGTH_SHORT).show()
                if (token.isNotBlank()) sessionToken = token
                refresh()
            }.onFailure { e ->
                val msg = e.message ?: ""
                if (msg.contains("not authorized", ignoreCase = true) || msg.contains("auth", ignoreCase = true)) {
                    Toast.makeText(context, "需要认证信息", Toast.LENGTH_SHORT).show()
                    showConfigDialog = true
                } else {
                    Toast.makeText(context, "推送失败: $msg", Toast.LENGTH_LONG).show()
                }
            }
            isLoading = false
        }
    }

    fun performSync(url: String, user: String, token: String) {
        scope.launch {
            isLoading = true
            GitManager.sync(repoRoot, url, user, token).onSuccess {
                Toast.makeText(context, "同步成功", Toast.LENGTH_SHORT).show()
                if (token.isNotBlank()) sessionToken = token
                refresh()
            }.onFailure { e ->
                val msg = e.message ?: ""
                if (msg.contains("not authorized", ignoreCase = true) || msg.contains("auth", ignoreCase = true)) {
                    Toast.makeText(context, "需要认证信息", Toast.LENGTH_SHORT).show()
                    showConfigDialog = true
                } else {
                    Toast.makeText(context, "同步失败: $msg", Toast.LENGTH_LONG).show()
                }
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("源代码管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 远程仓库信息卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable {
                        scope.launch {
                            remoteConfig = GitManager.getRemoteConfig(repoRoot)
                            showConfigDialog = true
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "远程仓库",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (remoteConfig.first.isNotBlank()) {
                                    val name = remoteConfig.first.substringAfterLast("/").substringBefore(".git")
                                    if (name.isBlank()) "Git Remote" else name
                                } else "未配置同步地址",
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
                                        "已连接",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                }
            }

            // 操作栏：拉取、同步、推送
            if (remoteConfig.first.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { withRemoteConfig(forceAuth = false) { _, u, t -> performPull(u, t) } }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Download, null)
                            Text("拉取", fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = { withRemoteConfig(forceAuth = true) { url, u, t -> performSync(url, u, t) } }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Sync, null)
                            Text("一键同步", fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = { withRemoteConfig(forceAuth = true) { _, u, t -> performPush(u, t) } }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Upload, null)
                            Text("推送", fontSize = 12.sp)
                        }
                    }
                }
                HorizontalDivider()
            }

            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("待提交变更", style = MaterialTheme.typography.titleSmall) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("最近提交历史", style = MaterialTheme.typography.titleSmall) }
                )
            }

            if (selectedTabIndex == 0) {
                // 提交信息输入框放在变更列表上方
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    placeholder = { Text("提交变更内容...") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    maxLines = 3,
                    trailingIcon = {
                        if (commitMessage.isNotBlank()) {
                            IconButton(onClick = {
                                scope.launch {
                                    val filesToCommit = if (selectedStagedFiles.isNotEmpty()) selectedStagedFiles.toList() else emptyList()
                                    GitManager.commit(repoRoot, commitMessage, filesToCommit)
                                        .onSuccess { 
                                            commitMessage = ""
                                            selectedStagedFiles = emptySet()
                                            refresh() 
                                        }
                                        .onFailure {
                                            Toast.makeText(context, "提交失败: ${it.message}", Toast.LENGTH_LONG).show()
                                        }
                                }
                            }) {
                                Icon(Icons.Default.Check, "提交", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                )

                if (commitMessage.isNotBlank()) {
                    Text(
                        if (selectedStagedFiles.isNotEmpty()) "提示：将提交选中的暂存变更" else "提示：将提交所有本地变更（自动暂存）",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                LazyColumn(Modifier.fillMaxSize()) {
                    // --- 暂存的变更 ---
                    if (status.staged.isNotEmpty()) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("暂存的更改 (${status.staged.size})", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                IconButton(onClick = { scope.launch { GitManager.unstageAll(repoRoot); refresh() } }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Remove, "全部取消暂存", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        items(status.staged) { change ->
                            Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(change.path, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                val color = when (change.type) {
                                    com.carocall.gitmobile.data.model.GitChange.ChangeType.ADDED -> Color(0xFF4CAF50)
                                    com.carocall.gitmobile.data.model.GitChange.ChangeType.DELETED -> Color(0xFFE91E63)
                                    else -> Color(0xFF2196F3)
                                }
                                Text(change.type.name.take(1), color = color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                IconButton(onClick = { scope.launch { GitManager.unstage(repoRoot, change.path); refresh() } }) {
                                    Icon(Icons.Default.Remove, "取消暂存", tint = Color.Gray)
                                }
                            }
                        }
                    }

                    // --- 未暂存的变更 ---
                    if (status.unstaged.isNotEmpty()) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("更改 (${status.unstaged.size})", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                IconButton(onClick = { scope.launch { GitManager.stageAll(repoRoot); refresh() } }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Add, "全部暂存", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        items(status.unstaged) { change ->
                            Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(change.path, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                val color = when (change.type) {
                                    com.carocall.gitmobile.data.model.GitChange.ChangeType.UNTRACKED -> Color(0xFF4CAF50)
                                    com.carocall.gitmobile.data.model.GitChange.ChangeType.DELETED -> Color(0xFFE91E63)
                                    else -> Color(0xFF2196F3)
                                }
                                Text(change.type.name.take(1), color = color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                IconButton(onClick = { scope.launch { GitManager.stage(repoRoot, change.path); refresh() } }) {
                                    Icon(Icons.Default.Add, "暂存", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(history) { commit ->
                        ListItem(
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(commit.message, maxLines = 1, modifier = Modifier.weight(1f))
                                    // 如果是云端位置，显示 Badge
                                    if (commit.isRemote) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = MaterialTheme.shapes.extraSmall,
                                            modifier = Modifier.padding(start = 4.dp)
                                        ) {
                                            Text("Cloud", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    }
                                }
                            },
                            supportingContent = { Text("${commit.author} • ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(commit.time))}") },
                            trailingContent = { Text(commit.id.take(7), fontSize = 11.sp, color = Color.Gray) },
                            modifier = Modifier.clickable { viewCommitChanges(commit) }
                        )
                    }
                }
            }
        }

        if (showChangesDialog) {
            AlertDialog(
                onDismissRequest = { showChangesDialog = false },
                title = { Text("提交变更 - ${selectedCommit?.message?.take(20)}...") },
                text = {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(commitChanges) { (path, type) ->
                            ListItem(
                                headlineContent = { Text(path, fontSize = 14.sp) },
                                trailingContent = { Text(type.take(1), fontWeight = FontWeight.Bold, color = if(type == "ADD") Color(0xFF4CAF50) else Color(0xFFE91E63)) },
                                modifier = Modifier.clickable { viewFileDiff(selectedCommit!!.id, path) }
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showChangesDialog = false }) { Text("关闭") } }
            )
        }

        if (showDiffDialog) {
            AlertDialog(
                onDismissRequest = { showDiffDialog = false },
                title = { Text("变更详情: ${selectedDiffFile?.substringAfterLast("/")}") },
                text = {
                    Box(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                        Column {
                            fileDiffContent.lines().forEach { line ->
                                val color = when {
                                    line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF4CAF50)
                                    line.startsWith("-") && !line.startsWith("---") -> Color(0xFFE91E63)
                                    line.startsWith("@@") -> Color(0xFF2196F3)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                                Text(
                                    line,
                                    color = color,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showDiffDialog = false }) { Text("关闭") } }
            )
        }

        if (showConfigDialog) {
            PushDialog(
                initialUrl = remoteConfig.first,
                initialUser = remoteConfig.second,
                initialToken = remoteConfig.third.ifBlank { sessionToken },
                onDismiss = { showConfigDialog = false },
                onConfirm = { url, user, token ->
                    showConfigDialog = false
                    scope.launch {
                        GitManager.saveRemoteConfig(repoRoot, url, user, token)
                        refresh()
                        Toast.makeText(context, "远程配置已保存", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        if (isLoading) {
            AlertDialog(onDismissRequest = {}, confirmButton = {}, text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Spacer(Modifier.width(16.dp)); Text("同步中...") } })
        }
    }
}