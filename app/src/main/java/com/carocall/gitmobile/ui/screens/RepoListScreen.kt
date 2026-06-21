package com.carocall.gitmobile.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.res.stringResource
import com.carocall.gitmobile.R
import com.carocall.gitmobile.data.git.GitManager
import com.carocall.gitmobile.ui.component.CloneSheet
import com.carocall.gitmobile.ui.component.ErrorDialog
import com.carocall.gitmobile.ui.component.InputSheet
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
    var cloningProgress by remember { mutableStateOf<Pair<String, Float>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    var isFabExpanded by remember { mutableStateOf(false) }

    fun refreshRepos() {
        val list = rootDir.listFiles()?.filter { 
            it.isDirectory && File(it, ".git").exists() 
        } ?: emptyList()
        repos = when (sortOrder) {
            RepoSortOrder.TIME -> list.sortedByDescending { it.lastModified() }
            RepoSortOrder.NAME -> list.sortedBy { it.name.lowercase() }
        }
    }

    LaunchedEffect(sortOrder) { refreshRepos() }

    BackHandler(enabled = isFabExpanded) {
        isFabExpanded = false
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.workspace_title), fontWeight = FontWeight.ExtraBold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        FloatingActionButton(
                            onClick = { isFabExpanded = false; showCloneDialog = true },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) { Icon(Icons.Default.CloudDownload, stringResource(R.string.clone_repo)) }

                        FloatingActionButton(
                            onClick = { isFabExpanded = false; showCreateDialog = true },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) { Icon(Icons.Default.Add, stringResource(R.string.create_repo)) }
                    }
                }
                FloatingActionButton(
                    onClick = { isFabExpanded = !isFabExpanded },
                    containerColor = if (isFabExpanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(if (isFabExpanded) Icons.Default.Close else Icons.Default.Add, null)
                }
            }
        }
    ) { padding ->
        if (repos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.first_project_hint), color = Color.Gray)
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
                                        text = { Text(stringResource(R.string.rename_project)) },
                                        onClick = { menuExpanded = false; showRenameDialog = repo },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.remove_project), color = MaterialTheme.colorScheme.error) },
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
            InputSheet(title = stringResource(R.string.create_new_project), onDismiss = { showCreateDialog = false }, onConfirm = { name ->
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
            InputSheet(title = stringResource(R.string.rename_project_title), initialValue = repo.name, onDismiss = { showRenameDialog = null }, onConfirm = { newName ->
                val dest = File(repo.parentFile, newName)
                if (repo.renameTo(dest)) {
                    refreshRepos()
                    showRenameDialog = null
                } else {
                    Toast.makeText(context, context.getString(R.string.rename_failed), Toast.LENGTH_SHORT).show()
                }
            })
        }

        if (showCloneDialog) {
            CloneSheet(onDismiss = { showCloneDialog = false }, onConfirm = { url, name, branch, user, token ->
                val f = File(rootDir, name)
                if (f.exists()) {
                    Toast.makeText(context, context.getString(R.string.dir_already_exists), Toast.LENGTH_SHORT).show()
                } else {
                    showCloneDialog = false
                    scope.launch {
                        val progressMonitor = object : org.eclipse.jgit.lib.ProgressMonitor {
                            // ... progress monitor implementation
                            private var total = 0
                            private var completed = 0
                            private var taskName = ""

                            override fun start(totalTasks: Int) {}
                            override fun beginTask(title: String, totalWork: Int) {
                                taskName = title
                                total = totalWork
                                completed = 0
                                cloningProgress = taskName to 0f
                            }
                            override fun update(completedUnits: Int) {
                                completed += completedUnits
                                if (total > 0) {
                                    cloningProgress = taskName to (completed.toFloat() / total)
                                } else {
                                    cloningProgress = taskName to 0f
                                }
                            }
                            override fun endTask() {}
                            override fun isCancelled(): Boolean = false
                            override fun showDuration(enabled: Boolean) {}
                        }

                        val result = GitManager.clone(
                            f, url,
                            user.ifBlank { null },
                            token.ifBlank { null },
                            branch.ifBlank { null },
                            progressMonitor
                        )
                        
                        cloningProgress = null
                        if (result.isSuccess) {
                            Toast.makeText(context, context.getString(R.string.clone_success), Toast.LENGTH_SHORT).show()
                            refreshRepos()
                        } else {
                            errorMessage = context.getString(R.string.clone_failed, result.exceptionOrNull()?.message)
                        }
                    }
                }
            })
        }

        if (cloningProgress != null) {
            val (task, progress) = cloningProgress!!
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.cloning_repo)) },
                text = {
                    Column {
                        Text(task)
                        Spacer(Modifier.height(8.dp))
                        if (progress > 0) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                confirmButton = {}
            )
        }

        if (showDeleteConfirm != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = null },
                title = { Text(stringResource(R.string.confirm_delete_title)) },
                text = { Text(stringResource(R.string.confirm_delete_msg, showDeleteConfirm?.name ?: "")) },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirm?.deleteRecursively()
                            refreshRepos()
                            showDeleteConfirm = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.confirm_delete_title)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        errorMessage?.let { ErrorDialog(error = it, onDismiss = { errorMessage = null }) }
    }
}
