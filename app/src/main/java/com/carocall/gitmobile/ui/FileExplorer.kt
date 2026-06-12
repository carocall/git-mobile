package com.carocall.gitmobile

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 导航入口
 */
@Composable
fun MainApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "explorer") {
        // 1. 文件浏览器页面
        composable("explorer") {
            FileExplorerScreen(onOpenFile = { file ->
                // 路径编码处理，防止特殊字符干扰路由
                val encodedPath = URLEncoder.encode(file.absolutePath, "UTF-8")
                navController.navigate("editor/$encodedPath")
            })
        }

        // 2. 文件编辑器页面
        composable(
            route = "editor/{filePath}",
            arguments = listOf(navArgument("filePath") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("filePath") ?: ""
            val filePath = URLDecoder.decode(encodedPath, "UTF-8")
            FileEditorScreen(
                file = File(filePath),
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * 文件浏览器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(onOpenFile: (File) -> Unit) {
    val context = LocalContext.current
    val rootDir = remember { context.filesDir }

    // 关键：状态保存在此 Composable 中，Navigation 返回时会自动恢复
    var currentDir by remember { mutableStateOf(rootDir) }
    var files by remember { mutableStateOf(currentDir.listFiles()?.toList() ?: emptyList()) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isFolder by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<File?>(null) }

    fun refresh() {
        files = currentDir.listFiles()?.toList()?.sortedWith(
            compareBy({ !it.isDirectory }, { it.name.lowercase() })
        ) ?: emptyList()
    }

    // 导入文件
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedUri ->
            val contentResolver = context.contentResolver
            val fileName = contentResolver.query(selectedUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: "imported_${System.currentTimeMillis()}"
            try {
                contentResolver.openInputStream(selectedUri)?.use { input ->
                    File(currentDir, fileName).outputStream().use { output -> input.copyTo(output) }
                }
                refresh()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    LaunchedEffect(currentDir) { refresh() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (currentDir == rootDir) "根目录" else currentDir.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (currentDir != rootDir) {
                        IconButton(onClick = { currentDir = currentDir.parentFile ?: rootDir }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    }
                },

            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(onClick = { importLauncher.launch("*/*") }, modifier = Modifier.padding(bottom = 16.dp), containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Default.UploadFile, "导入")
                }
                FloatingActionButton(onClick = { isFolder = true; showCreateDialog = true }, modifier = Modifier.padding(bottom = 16.dp)) {
                    Icon(Icons.Default.CreateNewFolder, "文件夹")
                }
                FloatingActionButton(onClick = { isFolder = false; showCreateDialog = true }) {
                    Icon(Icons.Default.Add, "文件")
                }
            }
        }
    ) { padding ->
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Text("文件夹为空", color = MaterialTheme.colorScheme.outline) }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(files) { file ->
                    var menuExpanded by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text(file.name) },
                        leadingContent = { Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description, null, tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) },
                        trailingContent = {
                            Box {
                                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "更多") }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    DropdownMenuItem(text = { Text("重命名") }, onClick = { menuExpanded = false; selectedFile = file; showRenameDialog = true }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                                    DropdownMenuItem(text = { Text("删除") }, onClick = { menuExpanded = false; selectedFile = file; showDeleteDialog = true }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                                    DropdownMenuItem(text = { Text("复制 (暂不可用)") }, onClick = { menuExpanded = false }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) })
                                    DropdownMenuItem(text = { Text("剪切 (暂不可用)") }, onClick = { menuExpanded = false }, leadingIcon = { Icon(Icons.Default.ContentCut, null) })
                                }
                            }
                        },
                        modifier = Modifier.clickable { if (file.isDirectory) currentDir = file else onOpenFile(file) }
                    )
                }
            }
        }

        // 对话框逻辑
        if (showCreateDialog) {
            InputDialog(title = "创建${if (isFolder) "文件夹" else "文件"}", onDismiss = { showCreateDialog = false }, onConfirm = { name ->
                val f = File(currentDir, name)
                if (isFolder) f.mkdirs() else f.createNewFile()
                refresh()
            })
        }
        if (showRenameDialog && selectedFile != null) {
            InputDialog(title = "重命名", initialValue = selectedFile!!.name, onDismiss = { showRenameDialog = false }, onConfirm = { newName ->
                File(selectedFile!!.parentFile, newName).let { selectedFile!!.renameTo(it) }
                refresh()
            })
        }
        if (showDeleteDialog && selectedFile != null) {
            AlertDialog(onDismissRequest = { showDeleteDialog = false }, title = { Text("确认删除") }, text = { Text("确定删除 '${selectedFile!!.name}'？") },
                confirmButton = { Button(colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), onClick = { selectedFile?.deleteRecursively(); refresh(); showDeleteDialog = false }) { Text("删除") } },
                dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
            )
        }
    }
}

/**
 * 文件编辑器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileEditorScreen(file: File, onBack: () -> Unit) {
    val isBinary = remember(file) { isBinaryFile(file) }
    var text by remember { mutableStateOf(if (!isBinary) (try { file.readText() } catch (e: Exception) { "" }) else "") }
    var originalText by remember { mutableStateOf(text) }
    var showExitDialog by remember { mutableStateOf(false) }

    // 撤销/重做栈 (限10步)
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }

    fun handleTextChange(newText: String) {
        if (newText != text) {
            if (undoStack.size >= 10) undoStack.removeAt(0)
            undoStack.add(text)
            text = newText
            redoStack.clear()
        }
    }

    val isDirty = !isBinary && text != originalText

    BackHandler { if (isDirty) showExitDialog = true else onBack() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(file.name, style = MaterialTheme.typography.titleSmall) },
                navigationIcon = {
                    IconButton(onClick = { if (isDirty) showExitDialog = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (!isBinary) {
                        IconButton(onClick = {
                            redoStack.add(text)
                            text = undoStack.removeAt(undoStack.size - 1)
                        }, enabled = undoStack.isNotEmpty()) { Icon(Icons.Default.Undo, "撤销") }

                        IconButton(onClick = {
                            undoStack.add(text)
                            text = redoStack.removeAt(redoStack.size - 1)
                        }, enabled = redoStack.isNotEmpty()) { Icon(Icons.Default.Redo, "下一步") }

                        IconButton(onClick = {
                            file.writeText(text)
                            originalText = text
                        }, enabled = isDirty) { Icon(Icons.Default.Save, "保存") }
                    }
                }
            )
        }
    ) { padding ->
        if (isBinary) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Text("不支持二进制文件编辑")
                }
            }
        } else {
            TextField(
                value = text,
                onValueChange = { handleTextChange(it) },
                modifier = Modifier.fillMaxSize().padding(padding),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("未保存的更改") },
                text = { Text("您有未保存的更改，确定要退出吗？") },
                confirmButton = { TextButton(onClick = { showExitDialog = false; onBack() }) { Text("退出") } },
                dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("取消") } }
            )
        }
    }
}

/**
 * 辅助函数：判断是否为二进制文件
 */
fun isBinaryFile(file: File): Boolean {
    if (!file.exists() || file.isDirectory) return false
    return try {
        file.inputStream().use { input ->
            val buffer = ByteArray(1024)
            val bytesRead = input.read(buffer)
            if (bytesRead <= 0) return false
            for (i in 0 until bytesRead) {
                if (buffer[i] == 0.toByte()) return true
            }
            false
        }
    } catch (e: Exception) { true }
}

/**
 * 通用输入对话框
 */
@Composable
fun InputDialog(title: String, initialValue: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true) },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) { onConfirm(name); onDismiss() } }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
