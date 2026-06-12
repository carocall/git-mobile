package com.carocall.gitmobile

import android.provider.OpenableColumns
import android.widget.Toast
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Git 状态数据类
 */
data class RepoStatus(
    val untracked: Set<String> = emptySet(),
    val modified: Set<String> = emptySet(),
    val added: Set<String> = emptySet(),
    val removed: Set<String> = emptySet()
) {
    val hasChanges: Boolean get() = untracked.isNotEmpty() || modified.isNotEmpty() || added.isNotEmpty() || removed.isNotEmpty()
    val allChanges: List<Pair<String, String>> get() {
        return untracked.map { it to "Untracked" } +
                modified.map { it to "Modified" } +
                added.map { it to "Added" } +
                removed.map { it to "Removed" }
    }
}

/**
 * Git 操作管理类
 */
object GitManager {
    fun isGitRepo(dir: File): Boolean = File(dir, ".git").exists()

    fun findRepoRoot(dir: File): File? {
        var current: File? = dir
        while (current != null) {
            if (isGitRepo(current)) return current
            current = current.parentFile
        }
        return null
    }

    suspend fun initRepo(dir: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            Git.init().setDirectory(dir).call().use {
                Result.success("Git 仓库初始化成功")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStatus(repoRoot: File): RepoStatus = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val status = git.status().call()
                RepoStatus(
                    untracked = status.untracked,
                    modified = status.modified,
                    added = status.added,
                    removed = status.removed
                )
            }
        } catch (e: Exception) {
            RepoStatus()
        }
    }

    suspend fun commit(repoRoot: File, message: String, files: List<String>): Result<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val add = git.add()
                files.forEach { add.addFilepattern(it) }
                add.call()
                git.commit().setMessage(message).call()
                Result.success("提交成功")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun push(repoRoot: File, remoteUrl: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val config = git.repository.config
                config.setString("remote", "origin", "url", remoteUrl)
                config.save()

                val pushCommand = git.push()
                pushCommand.setRemote("origin")
                pushCommand.setCredentialsProvider(UsernamePasswordCredentialsProvider(token, ""))
                pushCommand.call()
                Result.success("推送成功")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val rootDir = remember { context.filesDir }

    // 将当前目录路径状态提升到这里，并使用 rememberSaveable 持久化
    var currentDirPath by rememberSaveable { mutableStateOf(rootDir.absolutePath) }
    val currentDir = remember(currentDirPath) { File(currentDirPath) }

    NavHost(navController = navController, startDestination = "explorer") {
        composable("explorer") {
            FileExplorerScreen(
                currentDir = currentDir,
                onDirChange = { currentDirPath = it.absolutePath },
                onOpenFile = { file ->
                    val encodedPath = URLEncoder.encode(file.absolutePath, "UTF-8")
                    navController.navigate("editor/$encodedPath")
                },
                onGoToGit = { repoPath ->
                    val encodedPath = URLEncoder.encode(repoPath, "UTF-8")
                    navController.navigate("git_commit/$encodedPath")
                }
            )
        }
        composable(
            route = "editor/{filePath}",
            arguments = listOf(navArgument("filePath") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("filePath") ?: ""
            val filePath = URLDecoder.decode(encodedPath, "UTF-8")
            FileEditorScreen(file = File(filePath), onBack = { navController.popBackStack() })
        }
        composable(
            route = "git_commit/{repoPath}",
            arguments = listOf(navArgument("repoPath") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("repoPath") ?: ""
            val repoPath = URLDecoder.decode(encodedPath, "UTF-8")
            GitCommitScreen(repoRoot = File(repoPath), onBack = { navController.popBackStack() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    currentDir: File,
    onDirChange: (File) -> Unit,
    onOpenFile: (File) -> Unit,
    onGoToGit: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootDir = remember { context.filesDir }

    var files by remember { mutableStateOf(currentDir.listFiles()?.toList() ?: emptyList()) }
    val repoRoot = remember(currentDir) { GitManager.findRepoRoot(currentDir) }
    var gitStatus by remember { mutableStateOf(RepoStatus()) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isFolder by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<File?>(null) }

    // 处理系统返回键：如果在子目录，返回上一级；如果在根目录，才退出应用
    BackHandler(enabled = currentDir != rootDir) {
        onDirChange(currentDir.parentFile ?: rootDir)
    }

    fun refresh() {
        files = currentDir.listFiles()?.toList()
            ?.filter { it.name != ".git" }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()

        scope.launch {
            repoRoot?.let { gitStatus = GitManager.getStatus(it) }
        }
    }

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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (currentDir == rootDir) "根目录" else currentDir.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (repoRoot != null) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.AccountTree, "Git", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                navigationIcon = {
                    if (currentDir != rootDir) {
                        IconButton(onClick = { onDirChange(currentDir.parentFile ?: rootDir) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    }
                },
                actions = {
                    if (repoRoot != null) {
                        IconButton(onClick = { onGoToGit(repoRoot.absolutePath) }) {
                            BadgedBox(badge = { if (gitStatus.hasChanges) Badge { Text("!") } }) {
                                Icon(Icons.Default.Source, "Git 提交")
                            }
                        }
                    }
                    var showTopMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showTopMenu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                        DropdownMenu(expanded = showTopMenu, onDismissRequest = { showTopMenu = false }) {
                            if (repoRoot == null) {
                                DropdownMenuItem(
                                    text = { Text("初始化 Git 仓库") },
                                    onClick = {
                                        showTopMenu = false
                                        scope.launch {
                                            GitManager.initRepo(currentDir).onSuccess {
                                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                                refresh()
                                            }.onFailure {
                                                Toast.makeText(context, "错误: ${it.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.SettingsInputAntenna, null) }
                                )
                            }
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
                    val relativePath = repoRoot?.let { file.absolutePath.substringAfter(it.absolutePath + "/", "") } ?: ""
                    val statusColor = when {
                        gitStatus.untracked.contains(relativePath) -> Color(0xFF4CAF50)
                        gitStatus.modified.contains(relativePath) -> Color(0xFF2196F3)
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    var menuExpanded by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = {
                            Text(file.name, color = statusColor, fontWeight = if (statusColor != MaterialTheme.colorScheme.onSurface) FontWeight.Bold else FontWeight.Normal)
                        },
                        leadingContent = { Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description, null, tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) },
                        trailingContent = {
                            Box {
                                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "操作") }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    DropdownMenuItem(text = { Text("重命名") }, onClick = { menuExpanded = false; selectedFile = file; showRenameDialog = true }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                                    DropdownMenuItem(text = { Text("删除") }, onClick = { menuExpanded = false; selectedFile = file; showDeleteDialog = true }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                                }
                            }
                        },
                        modifier = Modifier.clickable { if (file.isDirectory) onDirChange(file) else onOpenFile(file) }
                    )
                }
            }
        }

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
 * Git 提交界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitCommitScreen(repoRoot: File, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(RepoStatus()) }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }
    var commitMessage by remember { mutableStateOf("") }
    var showPushDialog by remember { mutableStateOf(false) }

    fun refreshStatus() {
        scope.launch { status = GitManager.getStatus(repoRoot) }
    }

    LaunchedEffect(Unit) { refreshStatus() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("源代码管理") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { showPushDialog = true }) {
                        Icon(Icons.Default.CloudUpload, "推送到 GitHub")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = commitMessage,
                onValueChange = { commitMessage = it },
                label = { Text("提交信息 (Commit Message)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (commitMessage.isBlank()) {
                        Toast.makeText(context, "请输入提交信息", Toast.LENGTH_SHORT).show()
                    } else if (selectedFiles.isEmpty()) {
                        Toast.makeText(context, "请选择要提交的文件", Toast.LENGTH_SHORT).show()
                    } else {
                        scope.launch {
                            GitManager.commit(repoRoot, commitMessage, selectedFiles.toList()).onSuccess {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                refreshStatus()
                                commitMessage = ""
                                selectedFiles = emptySet()
                            }.onFailure {
                                Toast.makeText(context, "错误: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("提交 (Commit)") }

            Spacer(Modifier.height(16.dp))
            Text("更改内容", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            LazyColumn {
                items(status.allChanges) { (path, type) ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selectedFiles = if (selectedFiles.contains(path)) selectedFiles - path else selectedFiles + path
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = selectedFiles.contains(path), onCheckedChange = {
                            selectedFiles = if (it) selectedFiles + path else selectedFiles - path
                        })
                        Column {
                            Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(type, fontSize = 12.sp, color = when(type) {
                                "Untracked" -> Color(0xFF4CAF50)
                                "Modified" -> Color(0xFF2196F3)
                                else -> Color.Gray
                            })
                        }
                    }
                }
            }
        }

        if (showPushDialog) {
            PushDialog(
                onDismiss = { showPushDialog = false },
                onConfirm = { url, token ->
                    scope.launch {
                        GitManager.push(repoRoot, url, token).onSuccess {
                            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            showPushDialog = false
                        }.onFailure {
                            Toast.makeText(context, "错误: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
    }
}

/**
 * 推送对话框
 */
@Composable
fun PushDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("推送到 GitHub (HTTPS)") },
        text = {
            Column {
                TextField(value = url, onValueChange = { url = it }, label = { Text("仓库地址 (HTTPS URL)") }, placeholder = { Text("https://github.com/user/repo.git") })
                Spacer(Modifier.height(8.dp))
                TextField(value = token, onValueChange = { token = it }, label = { Text("Personal Access Token") }, placeholder = { Text("ghp_xxxx") })
            }
        },
        confirmButton = {
            Button(onClick = { if (url.isNotBlank() && token.isNotBlank()) onConfirm(url, token) }) { Text("推送") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
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
            AlertDialog(onDismissRequest = { showExitDialog = false }, title = { Text("未保存的更改") }, text = { Text("您有未保存的更改，确定要退出吗？") },
                confirmButton = { TextButton(onClick = { showExitDialog = false; onBack() }) { Text("退出") } },
                dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("取消") } }
            )
        }
    }
}

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