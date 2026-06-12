package com.carocall.gitmobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen() {
    val context = LocalContext.current
    // 初始路径设置为应用的私有文件目录：/data/user/0/com.carocall.gitmobile/files
    val rootDir = remember { context.filesDir }
    var currentDir by remember { mutableStateOf(rootDir) }
    var files by remember { mutableStateOf(currentDir.listFiles()?.toList() ?: emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var isFolder by remember { mutableStateOf(false) }

    // 刷新文件列表的方法
    fun refresh() {
        files = currentDir.listFiles()?.toList()?.sortedWith(
            compareBy({ !it.isDirectory }, { it.name.lowercase() })
        ) ?: emptyList()
    }

    // 初始化加载
    LaunchedEffect(currentDir) { refresh() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = if (currentDir == rootDir) "根目录" else currentDir.name) },
                navigationIcon = {
                    if (currentDir != rootDir) {
                        TextButton(onClick = {
                            currentDir = currentDir.parentFile ?: rootDir
                        }) {
                            Text("返回")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Column {
                // 新建文件夹按钮
                SmallFloatingActionButton(
                    onClick = {
                        isFolder = true
                        showCreateDialog = true
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "新建文件夹")
                }
                // 新建文件按钮
                FloatingActionButton(onClick = {
                    isFolder = false
                    showCreateDialog = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = "新建文件")
                }
            }
        }
    ) { innerPadding ->
        if (files.isEmpty()) {
            Box(Modifier
                .fillMaxSize()
                .padding(innerPadding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("文件夹为空", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(innerPadding)) {
                items(files) { file ->
                    ListItem(
                        headlineContent = { Text(file.name) },
                        leadingContent = {
                            Icon(
                                if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                contentDescription = null,
                                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        },
                        modifier = Modifier.clickable {
                            if (file.isDirectory) {
                                currentDir = file
                            } else {
                                // 这里可以扩展点击文件的逻辑，比如读取内容
                            }
                        }
                    )
                }
            }
        }

        // 新建对话框
        if (showCreateDialog) {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("创建${if (isFolder) "文件夹" else "文件"}") },
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
                            val f = File(currentDir, name)
                            if (isFolder) f.mkdirs() else f.createNewFile()
                            refresh()
                            showCreateDialog = false
                        }
                    }) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}
