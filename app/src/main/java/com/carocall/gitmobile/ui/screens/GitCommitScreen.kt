package com.carocall.gitmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
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
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }
    var commitMessage by remember { mutableStateOf("") }

    var sessionToken by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var remoteConfig by remember { mutableStateOf(Triple("", "", "")) }
    var menuExpanded by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            status = GitManager.getStatus(repoRoot)
            history = GitManager.getHistory(repoRoot)
            // 只有当 selectedFiles 为空时才默认全选
            if (selectedFiles.isEmpty() && status.allChanges.isNotEmpty()) {
                selectedFiles = status.allChanges.map { it.first }.toSet()
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun withRemoteConfig(action: (String, String, String) -> Unit) {
        scope.launch {
            val config = GitManager.getRemoteConfig(repoRoot)
            remoteConfig = config
            val (url, user, savedToken) = config
            val currentToken = if (sessionToken.isBlank()) savedToken else sessionToken
            if (url.isBlank() || user.isBlank() || currentToken.isBlank()) {
                showConfigDialog = true
            } else {
                action(url, user, currentToken)
            }
        }
    }

    fun performPull(user: String, token: String) {
        scope.launch {
            isLoading = true
            GitManager.pull(repoRoot, user, token).onSuccess {
                Toast.makeText(context, "拉取成功", Toast.LENGTH_SHORT).show()
                sessionToken = token
                refresh()
            }.onFailure {
                Toast.makeText(context, "拉取失败: ${it.message}", Toast.LENGTH_LONG).show()
            }
            isLoading = false
        }
    }

    fun performPush(user: String, token: String) {
        scope.launch {
            isLoading = true
            GitManager.push(repoRoot, user, token).onSuccess {
                Toast.makeText(context, "推送成功", Toast.LENGTH_SHORT).show()
                sessionToken = token
                refresh()
            }.onFailure {
                Toast.makeText(context, "推送失败: ${it.message}", Toast.LENGTH_LONG).show()
            }
            isLoading = false
        }
    }

    fun performSync(url: String, user: String, token: String) {
        scope.launch {
            isLoading = true
            GitManager.sync(repoRoot, url, user, token).onSuccess {
                Toast.makeText(context, "同步成功", Toast.LENGTH_SHORT).show()
                sessionToken = token
                refresh()
            }.onFailure {
                Toast.makeText(context, "失败: ${it.message}", Toast.LENGTH_LONG).show()
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
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "更多") }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("拉取 (Pull)") },
                                leadingIcon = { Icon(Icons.Default.Download, null) },
                                onClick = { menuExpanded = false; withRemoteConfig { _, u, t -> performPull(u, t) } }
                            )
                            DropdownMenuItem(
                                text = { Text("推送 (Push)") },
                                leadingIcon = { Icon(Icons.Default.Upload, null) },
                                onClick = { menuExpanded = false; withRemoteConfig { _, u, t -> performPush(u, t) } }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("远程设置") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = {
                                    scope.launch {
                                        remoteConfig = GitManager.getRemoteConfig(repoRoot)
                                        menuExpanded = false
                                        showConfigDialog = true
                                    }
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = commitMessage,
                onValueChange = { commitMessage = it },
                placeholder = { Text("提交变更内容...") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                maxLines = 3
            )

            Button(
                onClick = {
                    if (commitMessage.isNotBlank()) {
                        scope.launch {
                            GitManager.commit(repoRoot, commitMessage, selectedFiles.toList())
                                .onSuccess { commitMessage = ""; selectedFiles = emptySet(); refresh() }
                        }
                    } else {
                        withRemoteConfig { url, u, t -> performSync(url, u, t) }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.extraSmall,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (commitMessage.isNotBlank()) Icons.Default.Sync else Icons.Default.Sync, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (commitMessage.isNotBlank()) "提交" else "同步更改 ${if(status.allChanges.isNotEmpty()) status.allChanges.size else ""}")
                }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("更改", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        if (status.allChanges.isNotEmpty()) {
                            val allSelected = selectedFiles.size == status.allChanges.size
                            Text(
                                if (allSelected) "取消全选" else "全选",
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
                    Row(Modifier.fillMaxWidth().clickable { selectedFiles = if (selectedFiles.contains(path)) selectedFiles - path else selectedFiles + path }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selectedFiles.contains(path), onCheckedChange = { selectedFiles = if (it) selectedFiles + path else selectedFiles - path })
                        Text(path, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Text(type.take(1), color = if (type == "Untracked") Color(0xFF4CAF50) else Color(0xFF2196F3), fontWeight = FontWeight.Bold)
                    }
                }
                item {
                    Text("最近提交", modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(16.dp, 4.dp), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                }
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
                        trailingContent = { Text(commit.id, fontSize = 11.sp, color = Color.Gray) }
                    )
                }
            }
        }

        if (showConfigDialog) {
            PushDialog(
                initialUrl = remoteConfig.first,
                initialUser = remoteConfig.second,
                initialToken = remoteConfig.third.ifBlank { sessionToken },
                onDismiss = { showConfigDialog = false },
                onConfirm = { url, user, token ->
                    showConfigDialog = false
                    performSync(url, user, token)
                }
            )
        }

        if (isLoading) {
            AlertDialog(onDismissRequest = {}, confirmButton = {}, text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Spacer(Modifier.width(16.dp)); Text("同步中...") } })
        }
    }
}