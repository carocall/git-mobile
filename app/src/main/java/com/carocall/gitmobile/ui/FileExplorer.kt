package com.carocall.gitmobile

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
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

// --- 数据模型 ---

data class RepoStatus(
    val untracked: Set<String> = emptySet(),
    val modified: Set<String> = emptySet(),
    val added: Set<String> = emptySet(),
    val removed: Set<String> = emptySet()
) {
    val hasChanges: Boolean get() = untracked.isNotEmpty() || modified.isNotEmpty() || added.isNotEmpty() || removed.isNotEmpty()
    val allChanges: List<Pair<String, String>> get() =
        untracked.map { it to "Untracked" } + modified.map { it to "Modified" } +
                added.map { it to "Added" } + removed.map { it to "Removed" }
}

// --- Git 管理器 ---

object GitManager {
    fun isGitRepo(dir: File): Boolean = File(dir, ".git").exists()

    suspend fun initRepo(dir: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            Git.init().setDirectory(dir).call().use { Result.success("初始化成功") }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getStatus(repoRoot: File): RepoStatus = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val s = git.status().call()
                RepoStatus(s.untracked, s.modified, s.added, s.removed)
            }
        } catch (e: Exception) { RepoStatus() }
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
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun push(repoRoot: File, remoteUrl: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val config = git.repository.config
                config.setString("remote", "origin", "url", remoteUrl)
                config.save()
                git.push().setRemote("origin").setCredentialsProvider(UsernamePasswordCredentialsProvider(token, "")).call()
                Result.success("推送成功")
            }
        } catch (e: Exception) { Result.failure(e) }
    }
}

// --- 导航与主入口 ---

@Composable
fun MainApp() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "repo_list",
        // 1. 进入新页面：从右侧滑入
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(400)
            )
        },
        // 2. 离开去新页面：向左侧滑出（可选：也可以保留原地不动或稍微位移）
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(400)
            )
        },
        // 3. 返回上一页：旧页面从左侧滑入
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(400)
            )
        },
        // 4. 当前页退出：向右侧滑出
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(400)
            )
        }
    ) {
        composable("repo_list") {
            RepoListScreen(onOpenRepo = { repo ->
                val encodedPath = URLEncoder.encode(repo.absolutePath, "UTF-8")
                navController.navigate("repo_explorer/$encodedPath")
            })
        }
        composable("repo_explorer/{repoRootPath}") { backStackEntry ->
            val path = URLDecoder.decode(backStackEntry.arguments?.getString("repoRootPath") ?: "", "UTF-8")
            RepoExplorerScreen(
                repoRoot = File(path),
                onBackToRepos = { navController.popBackStack() },
                onOpenFile = { file ->
                    val encodedPath = URLEncoder.encode(file.absolutePath, "UTF-8")
                    navController.navigate("editor/$encodedPath")
                },
                onGoToGit = { repoPath ->
                    val encodedPath = URLEncoder.encode(repoPath, "UTF-8")
                    navController.navigate("git_commit/$encodedPath")
                },
            )
        }
        composable("editor/{filePath}") { backStackEntry ->
            val path = URLDecoder.decode(backStackEntry.arguments?.getString("filePath") ?: "", "UTF-8")
            FileEditorScreen(file = File(path), onBack = { navController.popBackStack() })
        }
        composable("git_commit/{repoPath}") { backStackEntry ->
            val path = URLDecoder.decode(backStackEntry.arguments?.getString("repoPath") ?: "", "UTF-8")
            GitCommitScreen(repoRoot = File(path), onBack = { navController.popBackStack() })
        }
    }
}

// --- 界面 1: 仓库列表 ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoListScreen(onOpenRepo: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootDir = remember { context.filesDir }
    var repos by remember { mutableStateOf(rootDir.listFiles()?.filter { it.isDirectory } ?: emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("我的仓库") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) { Icon(Icons.Default.Add, "创建仓库") }
        }
    ) { padding ->
        if (repos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Text("暂无仓库，点击右下角创建", color = Color.Gray) }
        } else {
            LazyColumn(Modifier.padding(padding)) {
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
    }
}

// --- 界面 2: 仓库内容浏览器 (扁平导航风格) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoExplorerScreen(repoRoot: File, onBackToRepos: () -> Unit, onOpenFile: (File) -> Unit, onGoToGit: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 当前浏览的内部目录状态
    var currentDir by remember { mutableStateOf(repoRoot) }
    var files by remember { mutableStateOf(currentDir.listFiles()?.toList() ?: emptyList()) }
    var gitStatus by remember { mutableStateOf(RepoStatus()) }

    // 弹窗状态
    var showCreateDialog by remember { mutableStateOf<Boolean?>(null) } // true: Folder, false: File
    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    var showPushDialog by remember { mutableStateOf(false) }

    fun refresh() {
        files = currentDir.listFiles()?.toList()
            ?.filter { it.name != ".git" }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        scope.launch { gitStatus = GitManager.getStatus(repoRoot) }
    }

    LaunchedEffect(currentDir) { refresh() }

    // 处理系统返回键
    BackHandler {
        if (currentDir == repoRoot) onBackToRepos() else {
            currentDir = currentDir.parentFile ?: repoRoot
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val name = context.contentResolver.query(it, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                c.moveToFirst(); c.getString(i)
            } ?: "imported_${System.currentTimeMillis()}"
            context.contentResolver.openInputStream(it)?.use { input ->
                File(currentDir, name).outputStream().use { output -> input.copyTo(output) }
            }
            refresh()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (currentDir == repoRoot) repoRoot.name else currentDir.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentDir == repoRoot) onBackToRepos() else currentDir = currentDir.parentFile ?: repoRoot
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { onGoToGit(repoRoot.absolutePath) }) {
                        BadgedBox(badge = { if (gitStatus.hasChanges) Badge { Text("!") } }) { Icon(Icons.Default.Source, null) }
                    }
                    IconButton(onClick = { showPushDialog = true }) { Icon(Icons.Default.CloudUpload, null) }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(onClick = { importLauncher.launch("*/*") }, modifier = Modifier.padding(bottom = 8.dp)) { Icon(Icons.Default.UploadFile, null) }
                SmallFloatingActionButton(onClick = { showCreateDialog = true }, modifier = Modifier.padding(bottom = 8.dp)) { Icon(Icons.Default.CreateNewFolder, null) }
                FloatingActionButton(onClick = { showCreateDialog = false }) { Icon(Icons.Default.Add, null) }
            }
        }
    ) { padding ->
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Text("空文件夹", color = Color.Gray) }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(files) { file ->
                    val relativePath = file.absolutePath.substringAfter(repoRoot.absolutePath + "/", "")
                    val color = when {
                        gitStatus.untracked.contains(relativePath) -> Color(0xFF4CAF50)
                        gitStatus.modified.contains(relativePath) -> Color(0xFF2196F3)
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    ListItem(
                        headlineContent = { Text(file.name, color = color) },
                        leadingContent = { Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description, null, tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else Color.Gray) },
                        trailingContent = {
                            var menuOpen by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, null) }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text("重命名") }, onClick = { menuOpen = false; showRenameDialog = file })
                                    DropdownMenuItem(text = { Text("删除") }, onClick = {
                                        menuOpen = false; file.deleteRecursively(); refresh()
                                    })
                                }
                            }
                        },
                        modifier = Modifier.clickable {
                            if (file.isDirectory) currentDir = file else onOpenFile(file)
                        }
                    )
                }
            }
        }

        // 弹窗处理
        showCreateDialog?.let { isFolder ->
            InputDialog(title = "新建${if (isFolder) "文件夹" else "文件"}", onDismiss = { showCreateDialog = null }, onConfirm = { name ->
                val f = File(currentDir, name)
                if (isFolder) f.mkdirs() else f.createNewFile()
                refresh(); showCreateDialog = null
            })
        }
        showRenameDialog?.let { file ->
            InputDialog(title = "重命名", initialValue = file.name, onDismiss = { showRenameDialog = null }, onConfirm = { name ->
                file.renameTo(File(file.parentFile, name)); refresh(); showRenameDialog = null
            })
        }
        if (showPushDialog) {
            PushDialog(onDismiss = { showPushDialog = false }, onConfirm = { url, token ->
                scope.launch { GitManager.push(repoRoot, url, token).onSuccess { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); showPushDialog = false } }
            })
        }
    }
}

// --- 辅助组件 (InputDialog, PushDialog, FileEditor, GitCommit) ---

@Composable
fun InputDialog(title: String, initialValue: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initialValue) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { TextField(value = name, onValueChange = { name = it }, singleLine = true) },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun PushDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }; var token by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("推送") },
        text = { Column { TextField(url, { url = it }, label = { Text("URL") }); TextField(token, { token = it }, label = { Text("Token") }) } },
        confirmButton = { Button(onClick = { onConfirm(url, token) }) { Text("推送") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileEditorScreen(file: File, onBack: () -> Unit) {
    val isBinary = remember(file) { isBinaryFile(file) }
    var text by remember { mutableStateOf(if (!isBinary) file.readText() else "") }
    var original by remember { mutableStateOf(text) }
    val undoStack = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text(file.name, fontSize = 14.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (!isBinary) {
                        IconButton(onClick = { file.writeText(text); original = text }, enabled = text != original) { Icon(Icons.Default.Save, null) }
                    }
                }
            )
        }
    ) { padding ->
        if (isBinary) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("不支持二进制") }
        else TextField(text, { if (it != text) { undoStack.add(text); text = it } }, Modifier.fillMaxSize().padding(padding))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitCommitScreen(repoRoot: File, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(RepoStatus()) }
    var msg by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(Unit) { status = GitManager.getStatus(repoRoot) }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("提交") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {
            TextField(msg, { msg = it }, Modifier.fillMaxWidth(), placeholder = { Text("提交信息") })
            Button(onClick = { scope.launch { GitManager.commit(repoRoot, msg, selected.toList()); onBack() } }, Modifier.fillMaxWidth()) { Text("提交") }
            LazyColumn {
                items(status.allChanges) { (path, type) ->
                    Row(Modifier.clickable { selected = if (selected.contains(path)) selected - path else selected + path }.padding(8.dp)) {
                        Checkbox(selected.contains(path), null)
                        Text(path, Modifier.weight(1f)); Text(type, color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

fun isBinaryFile(file: File): Boolean = try { file.inputStream().use { i -> val b = ByteArray(1024); val r = i.read(b); (0 until r).any { b[it] == 0.toByte() } } } catch (e: Exception) { true }