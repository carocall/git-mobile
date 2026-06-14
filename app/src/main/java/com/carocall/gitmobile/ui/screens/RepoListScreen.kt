package com.carocall.gitmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carocall.gitmobile.data.git.GitManager
import com.carocall.gitmobile.ui.component.CloneDialog
import com.carocall.gitmobile.ui.component.InputDialog
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class RepoSortOrder { NAME, TIME }

// --- 界面 1: 仓库列表 ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoListScreen(
    sortOrder: RepoSortOrder,
    onOpenRepo: (File) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootDir = remember { context.filesDir }
    
    var repos by remember { mutableStateOf(emptyList<File>()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<File?>(null) }
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    fun refreshRepos() {
        val list = rootDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        repos = when (sortOrder) {
            RepoSortOrder.TIME -> list.sortedByDescending { it.lastModified() }
            RepoSortOrder.NAME -> list.sortedBy { it.name.lowercase() }
        }
    }

    LaunchedEffect(sortOrder) { refreshRepos() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("我的工作区", fontWeight = FontWeight.ExtraBold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(
                    onClick = { showCloneDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) { Icon(Icons.Default.CloudDownload, "克隆仓库") }
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) { Icon(Icons.Default.Add, "创建仓库") }
            }
        }
    ) { padding ->
        if (repos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(Modifier.height(16.dp))
                    Text("开启你的第一个创作项目", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(repos) { repo ->
                    ElevatedCard(
                        onClick = { onOpenRepo(repo) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(repo.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                                    Text(" ${dateFormat.format(Date(repo.lastModified()))}", fontSize = 11.sp, color = Color.Gray)
                                }
                            }

                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, null, tint = Color.Gray) }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("项目重命名") },
                                        onClick = { menuExpanded = false; showRenameDialog = repo },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("移除项目", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            menuExpanded = false
                                            showDeleteConfirm = repo
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            InputDialog(title = "创建新项目", onDismiss = { showCreateDialog = false }, onConfirm = { name ->
                val f = File(rootDir, name)
                if (f.mkdirs()) {
                    scope.launch {
                        GitManager.initRepo(f)
                        refreshRepos()
                        showCreateDialog = false
                    }
                }
            })
        }

        if (showRenameDialog != null) {
            val repo = showRenameDialog!!
            InputDialog(title = "重命名项目", initialValue = repo.name, onDismiss = { showRenameDialog = null }, onConfirm = { newName ->
                val dest = File(repo.parentFile, newName)
                if (repo.renameTo(dest)) {
                    refreshRepos()
                    showRenameDialog = null
                } else {
                    Toast.makeText(context, "重命名失败", Toast.LENGTH_SHORT).show()
                }
            })
        }

        if (showCloneDialog) {
            CloneDialog(onDismiss = { showCloneDialog = false }, onConfirm = { url, name, user, token ->
                val f = File(rootDir, name)
                if (f.exists()) {
                    Toast.makeText(context, "目录已存在", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        val result = GitManager.clone(f, url, user, token)
                        if (result.isSuccess) {
                            Toast.makeText(context, "克隆成功", Toast.LENGTH_SHORT).show()
                            refreshRepos()
                            showCloneDialog = false
                        } else {
                            Toast.makeText(context, "克隆失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })
        }

        if (showDeleteConfirm != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = null },
                title = { Text("确认删除") },
                text = { Text("确定要删除项目 \"${showDeleteConfirm?.name}\" 吗？此操作不可撤销，且会删除本地所有相关文件。") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirm?.deleteRecursively()
                            refreshRepos()
                            showDeleteConfirm = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("确认删除") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
                }
            )
        }
    }
}
