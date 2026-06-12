package com.carocall.gitmobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen() {
    val context = LocalContext.current
    val rootDir = remember { context.filesDir }
    var currentDir by remember { mutableStateOf(rootDir) }
    var files by remember { mutableStateOf(currentDir.listFiles()?.toList() ?: emptyList()) }

    // 对话框状态管理
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var isFolder by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<File?>(null) }

    // 刷新文件列表
    fun refresh() {
        files = currentDir.listFiles()?.toList()?.sortedWith(
            compareBy({ !it.isDirectory }, { it.name.lowercase() })
        ) ?: emptyList()
    }

    LaunchedEffect(currentDir) { refresh() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (currentDir == rootDir) "根目录" else currentDir.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (currentDir != rootDir) {
                        IconButton(onClick = { currentDir = currentDir.parentFile ?: rootDir }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // 新建文件夹 (改为大按钮)
                FloatingActionButton(
                    onClick = { isFolder = true; showCreateDialog = true },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, "新建文件夹")
                }
                // 新建文件
                FloatingActionButton(onClick = { isFolder = false; showCreateDialog = true }) {
                    Icon(Icons.Default.Add, "新建文件")
                }
            }
        }
    ) { innerPadding ->
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                Text("文件夹为空", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(innerPadding)) {
                items(files) { file ->
                    var menuExpanded by remember { mutableStateOf(false) }

                    ListItem(
                        headlineContent = { Text(file.name) },
                        leadingContent = {
                            Icon(
                                if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                contentDescription = null,
                                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        },
                        trailingContent = {
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                                }
                                // 操作菜单
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("重命名") },
                                        onClick = {
                                            menuExpanded = false
                                            selectedFile = file
                                            showRenameDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除") },
                                        onClick = {
                                            menuExpanded = false
                                            selectedFile = file
                                            showDeleteDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("复制 (暂不可用)") },
                                        onClick = { menuExpanded = false },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("剪切 (暂不可用)") },
                                        onClick = { menuExpanded = false },
                                        leadingIcon = { Icon(Icons.Default.ContentCut, null) }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clickable {
                            if (file.isDirectory) currentDir = file
                        }
                    )
                }
            }
        }

        // --- 对话框部分 ---

        // 1. 新建对话框
        if (showCreateDialog) {
            InputDialog(
                title = "创建${if (isFolder) "文件夹" else "文件"}",
                onDismiss = { showCreateDialog = false },
                onConfirm = { name ->
                    val f = File(currentDir, name)
                    if (isFolder) f.mkdirs() else f.createNewFile()
                    refresh()
                }
            )
        }

        // 2. 重命名对话框
        if (showRenameDialog && selectedFile != null) {
            InputDialog(
                title = "重命名",
                initialValue = selectedFile!!.name,
                onDismiss = { showRenameDialog = false },
                onConfirm = { newName ->
                    val dest = File(selectedFile!!.parentFile, newName)
                    selectedFile!!.renameTo(dest)
                    refresh()
                }
            )
        }

        // 3. 删除确认对话框
        if (showDeleteDialog && selectedFile != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("确认删除") },
                text = { Text("确定要删除 '${selectedFile!!.name}' 吗？此操作不可撤销。") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            selectedFile?.deleteRecursively() // 文件夹会连通内容一起删除
                            refresh()
                            showDeleteDialog = false
                        }
                    ) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
                }
            )
        }
    }
}

/**
 * 通用的输入对话框组件
 */
@Composable
fun InputDialog(
    title: String,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onConfirm(name)
                    onDismiss()
                }
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}