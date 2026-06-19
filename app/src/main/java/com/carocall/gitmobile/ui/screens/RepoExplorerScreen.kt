package com.carocall.gitmobile.ui.screens

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.res.stringResource
import com.carocall.gitmobile.R
import com.carocall.gitmobile.data.git.GitManager
import com.carocall.gitmobile.data.model.RepoStatus
import com.carocall.gitmobile.ui.component.FileDetailsSheet
import com.carocall.gitmobile.ui.component.InputSheet
import com.carocall.gitmobile.utils.EditorConfig
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
    var showDetailsDialog by remember { mutableStateOf<File?>(null) }

    // Speed Dial 状态
    var isFabExpanded by remember { mutableStateOf(false) }

    fun refresh() {
        files = currentDir.listFiles()?.toList()
            ?.filter { it.name != ".git" }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        scope.launch { gitStatus = GitManager.getStatus(repoRoot) }
    }

    LaunchedEffect(currentDir) { refresh() }

    // 处理系统返回键
    BackHandler {
        if (isFabExpanded) {
            isFabExpanded = false
        } else if (currentDirPath == repoRoot.absolutePath) {
            onBackToRepos()
        } else {
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
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = { onGoToGit(repoRoot.absolutePath) }) {
                        BadgedBox(badge = { if (gitStatus.hasChanges) Badge { Text("!") } }) { Icon(Icons.Default.Source, stringResource(R.string.source_control)) }
                    }
                    // 此处移除了原来的 CloudUpload 按钮
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // 子按钮列表
                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        FloatingActionButton(
                            onClick = { isFabExpanded = false; importLauncher.launch("*/*") },
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) { Icon(Icons.Default.UploadFile, null) }

                        FloatingActionButton(
                            onClick = { isFabExpanded = false; showCreateDialog = true },
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) { Icon(Icons.Default.CreateNewFolder, null) }

                        FloatingActionButton(
                            onClick = { isFabExpanded = false; showCreateDialog = false },
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) { Icon(Icons.Default.Add, null) }
                    }
                }
                // 主切换按钮
                FloatingActionButton(
                    onClick = { isFabExpanded = !isFabExpanded },
                    containerColor = if (isFabExpanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(if (isFabExpanded) Icons.Default.Close else Icons.Default.Add, null)
                }
            }
        }
    ) { padding ->
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Text(stringResource(R.string.empty_folder), color = Color.Gray) }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(files) { file ->
                    val relativePath = file.absolutePath.substringAfter(repoRoot.absolutePath + "/", "")
                    val color = when {
                        gitStatus.untracked.contains(relativePath) -> Color(0xFF4CAF50)
                        gitStatus.modified.contains(relativePath) -> Color(0xFF2196F3)
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    val fileIcon = EditorConfig.getFileIcon(file)
                    val iconTint = EditorConfig.getIconTint(file)

                    ListItem(
                        headlineContent = {
                            Text(
                                text = file.name,
                                color = color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = { Icon(fileIcon, null, tint = iconTint) },
                        trailingContent = {
                            var menuOpen by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, null) }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.file_info)) }, onClick = { menuOpen = false; showDetailsDialog = file })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menuOpen = false; showRenameDialog = file })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = {
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
            val typeName = if (isFolder) stringResource(R.string.new_folder) else stringResource(R.string.new_file)
            InputSheet(title = stringResource(R.string.create_new, typeName), onDismiss = { showCreateDialog = null }, onConfirm = { name ->
                val f = File(currentDir, name)
                if (isFolder) f.mkdirs() else f.createNewFile()
                refresh(); showCreateDialog = null
            })
        }
        showRenameDialog?.let { file ->
            InputSheet(title = stringResource(R.string.rename), initialValue = file.name, onDismiss = { showRenameDialog = null }, onConfirm = { name ->
                file.renameTo(File(file.parentFile, name)); refresh(); showRenameDialog = null
            })
        }
        showDetailsDialog?.let { file ->
            FileDetailsSheet(file = file, onDismiss = { showDetailsDialog = null })
        }

    }
}
