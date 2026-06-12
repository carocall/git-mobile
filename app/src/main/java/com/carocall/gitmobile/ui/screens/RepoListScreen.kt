package com.carocall.gitmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carocall.gitmobile.data.git.GitManager
import com.carocall.gitmobile.ui.component.CloneDialog
import com.carocall.gitmobile.ui.component.InputDialog
import kotlinx.coroutines.launch
import java.io.File


// --- 界面 1: 仓库列表 ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoListScreen(onOpenRepo: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootDir = remember { context.filesDir }
    var repos by remember { mutableStateOf(rootDir.listFiles()?.filter { it.isDirectory } ?: emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("我的仓库") }) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = { showCloneDialog = true },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) { Icon(Icons.Default.CloudDownload, "克隆仓库") }
                FloatingActionButton(onClick = { showCreateDialog = true }) { Icon(Icons.Default.Add, "创建仓库") }
            }
        }
    ) { padding ->
        if (repos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Text("暂无仓库，点击右下角创建或克隆", color = Color.Gray) }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(repos) { repo ->
                    ListItem(
                        headlineContent = { Text(repo.name, fontWeight = FontWeight.Bold) },
                        leadingContent = { Icon(Icons.Default.AccountTree, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { onOpenRepo(repo) }
                    )
                }
            }
        }

        if (showCreateDialog) {
            InputDialog(title = "创建新仓库", onDismiss = { showCreateDialog = false }, onConfirm = { name ->
                val f = File(rootDir, name)
                if (f.mkdirs()) {
                    scope.launch {
                        GitManager.initRepo(f)
                        repos = rootDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
                        showCreateDialog = false // 修复：创建成功后关闭对话框
                    }
                }
            })
        }

        if (showCloneDialog) {
            CloneDialog(onDismiss = { showCloneDialog = false }, onConfirm = { url, user, token ->
                val repoName = url.substringAfterLast("/").substringBefore(".git")
                val f = File(rootDir, repoName)
                if (f.exists()) {
                    Toast.makeText(context, "目录已存在", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        val result = GitManager.clone(f, url, user, token)
                        if (result.isSuccess) {
                            Toast.makeText(context, "克隆成功", Toast.LENGTH_SHORT).show()
                            repos = rootDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
                            showCloneDialog = false
                        } else {
                            Toast.makeText(context, "克隆失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })
        }
    }
}
