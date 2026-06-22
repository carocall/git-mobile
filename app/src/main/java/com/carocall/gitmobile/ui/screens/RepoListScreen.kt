package com.carocall.gitmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import com.carocall.gitmobile.data.model.GitAccount
import com.carocall.gitmobile.ui.component.InputSheet
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class RepoSortOrder { NAME, TIME }

// --- 界面 1: 仓库列表 ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoListScreen(
    sortOrder: RepoSortOrder,
    gitAccounts: List<GitAccount>,
    onOpenRepo: (File) -> Unit,
    onOpenSettings: () -> Unit,
    onManageAccounts: () -> Unit,
    onAddRepo: () -> Unit
) {
    val context = LocalContext.current
    val rootDir = remember { context.filesDir }
    
    var repos by remember { mutableStateOf(emptyList<File>()) }
    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<File?>(null) }
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

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
            FloatingActionButton(
                onClick = onAddRepo,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Repository")
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
    }
}
