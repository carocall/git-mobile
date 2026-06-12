package com.carocall.gitmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
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

    fun refresh() {
        scope.launch {
            status = GitManager.getStatus(repoRoot)
            history = GitManager.getHistory(repoRoot)
            if (selectedFiles.isEmpty()) {
                selectedFiles = status.allChanges.map { it.first }.toSet()
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

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

    fun handleSyncClick() {
        scope.launch {
            // 获取保存的配置（包含 Token）
            val (url, user, savedToken) = GitManager.getRemoteConfig(repoRoot)
            val currentToken = if (sessionToken.isBlank()) savedToken else sessionToken

            if (url.isBlank() || user.isBlank() || currentToken.isBlank()) {
                showConfigDialog = true
            } else {
                performSync(url, user, currentToken)
            }
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
                                .onSuccess { commitMessage = ""; refresh() }
                        }
                    } else {
                        handleSyncClick()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.extraSmall,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (commitMessage.isNotBlank()) "提交" else "同步更改 ${if(status.allChanges.isNotEmpty()) status.allChanges.size else ""}")
                }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text("更改", modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(16.dp, 4.dp), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
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
            PushDialog(onDismiss = { showConfigDialog = false }, onConfirm = { url, user, token ->
                showConfigDialog = false
                performSync(url, user, token)
            })
        }

        if (isLoading) {
            AlertDialog(onDismissRequest = {}, confirmButton = {}, text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Spacer(Modifier.width(16.dp)); Text("同步中...") } })
        }
    }
}