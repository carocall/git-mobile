package com.carocall.gitmobile.ui.screens

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import com.carocall.gitmobile.data.git.GitManager
import com.carocall.gitmobile.data.model.RepoStatus
import com.carocall.gitmobile.ui.component.InputDialog
import com.carocall.gitmobile.ui.component.PushDialog
import kotlinx.coroutines.launch
import java.io.File

// --- 界面 2: 仓库内容浏览器 (扁平导航风格) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoExplorerScreen(repoRoot: File, onBackToRepos: () -> Unit, onOpenFile: (File) -> Unit, onGoToGit: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 当前浏览的内部目录状态 (使用 rememberSaveable 保存路径字符串以支持返回时恢复)
    var currentDirPath by rememberSaveable(repoRoot.absolutePath) { mutableStateOf(repoRoot.absolutePath) }
    val currentDir = remember(currentDirPath) { File(currentDirPath) }
    
    var files by remember { mutableStateOf(emptyList<File>()) }
    var gitStatus by remember { mutableStateOf(RepoStatus()) }

    // 弹窗状态
    var showCreateDialog by remember { mutableStateOf<Boolean?>(null) } // true: Folder, false: File
    var showRenameDialog by remember { mutableStateOf<File?>(null) }

    fun refresh() {
        files = currentDir.listFiles()?.toList()
            ?.filter { it.name != ".git" }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        scope.launch { gitStatus = GitManager.getStatus(repoRoot) }
    }

    LaunchedEffect(currentDir) { refresh() }

    // 处理系统返回键
    BackHandler {
        if (currentDirPath == repoRoot.absolutePath) onBackToRepos() else {
            currentDirPath = File(currentDirPath).parentFile?.absolutePath ?: repoRoot.absolutePath
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
                title = { Text(if (currentDirPath == repoRoot.absolutePath) repoRoot.name else currentDir.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentDirPath == repoRoot.absolutePath) onBackToRepos() else currentDirPath = currentDir.parentFile?.absolutePath ?: repoRoot.absolutePath
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { onGoToGit(repoRoot.absolutePath) }) {
                        BadgedBox(badge = { if (gitStatus.hasChanges) Badge { Text("!") } }) { Icon(Icons.Default.Source, null) }
                    }
                    // 此处移除了原来的 CloudUpload 按钮
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(onClick = { importLauncher.launch("*/*") }, modifier = Modifier.padding(bottom = 8.dp)) { Icon(Icons.Default.UploadFile, null) }
                FloatingActionButton(onClick = { showCreateDialog = true }, modifier = Modifier.padding(bottom = 8.dp)) { Icon(Icons.Default.CreateNewFolder, null) }
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

                    val fileIcon = when {
                        file.isDirectory -> Icons.Default.Folder
                        file.extension.lowercase() == "txt" -> Icons.AutoMirrored.Filled.Article
                        file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> Icons.Default.Image
                        file.extension.lowercase() in listOf("mp4", "mkv", "mov", "webm") -> Icons.Default.Movie
                        file.extension.lowercase() in listOf("mp3", "wav", "flac") -> Icons.Default.Audiotrack
                        file.extension.lowercase() == "pdf" -> Icons.Default.PictureAsPdf
                        file.extension.lowercase() in listOf("java", "kt", "py", "md", "xml", "json", "yaml", "toml", "properties", "gradle", "kts", "c", "cpp", "h", "js", "ts", "sh") -> Icons.Default.Code
                        else -> Icons.Default.Description
                    }

                    val iconTint = when {
                        file.isDirectory -> MaterialTheme.colorScheme.primary
                        fileIcon == Icons.Default.Code -> Color(0xFF673AB7) // 紫色表示代码
                        fileIcon == Icons.Default.Image -> Color(0xFFE91E63) // 粉红表示图片
                        fileIcon == Icons.Default.Movie -> Color(0xFFFF9800) // 橙色表示视频
                        fileIcon == Icons.Default.Audiotrack -> Color(0xFF00BCD4) // 青色表示音频
                        fileIcon == Icons.AutoMirrored.Filled.Article -> Color(0xFF4CAF50) // 绿色表示小说/文本
                        else -> Color.Gray
                    }

                    ListItem(
                        headlineContent = { Text(file.name, color = color) },
                        leadingContent = { Icon(fileIcon, null, tint = iconTint) },
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
                            if (file.isDirectory) currentDirPath = file.absolutePath else onOpenFile(file)
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

    }
}
