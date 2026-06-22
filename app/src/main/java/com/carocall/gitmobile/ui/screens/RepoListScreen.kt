package com.carocall.gitmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import com.carocall.gitmobile.R
import com.carocall.gitmobile.data.model.GitAccount
import com.carocall.gitmobile.data.model.RecentFile
import com.carocall.gitmobile.ui.component.IdentityDialog
import com.carocall.gitmobile.ui.component.InputSheet
import com.carocall.gitmobile.ui.component.RecentFileCard
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
    recentFiles: List<RecentFile>,
    globalGitName: String,
    globalGitEmail: String,
    onUpdateGlobalIdentity: (String, String) -> Unit,
    onOpenRepo: (File) -> Unit,
    onOpenFile: (File) -> Unit,
    onOpenSettings: () -> Unit,
    onManageAccounts: () -> Unit,
    onAddRepo: () -> Unit
) {
    val context = LocalContext.current
    val rootDir = remember { context.filesDir }
    
    var repos by remember { mutableStateOf(emptyList<File>()) }
    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<File?>(null) }
    var showIdentityDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredRepos = remember(repos, searchQuery) {
        if (searchQuery.isBlank()) repos
        else repos.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

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
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.ExtraBold) }
            )
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
                // 1. 欢迎与统计 (工作台感)
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .clickable { showIdentityDialog = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.workbench_title),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (globalGitName.isNotEmpty()) {
                                    stringResource(R.string.signed_in_as, globalGitName)
                                } else {
                                    stringResource(R.string.guest_user)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        
                        // 用户头像占位符
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (globalGitName.isNotEmpty()) globalGitName.take(1).uppercase() else "G",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                // 2. 搜索框
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        placeholder = { Text("Search repositories...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, null)
                                }
                            }
                        } else null,
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // 3. 快捷操作 (Quick Actions)
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionItem(
                            icon = Icons.Default.Add,
                            label = stringResource(R.string.create_repo),
                            onClick = onAddRepo,
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionItem(
                            icon = Icons.Default.AccountCircle,
                            label = stringResource(R.string.git_accounts_title),
                            onClick = onManageAccounts,
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionItem(
                            icon = Icons.Default.Settings,
                            label = "Settings",
                            onClick = onOpenSettings,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // 3. 最近文件区块
                if (recentFiles.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.recent_files),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(recentFiles) { file ->
                                RecentFileCard(file = file, onClick = { onOpenFile(File(file.path)) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // 2. 仓库列表标题
                item {
                    Text(
                        text = stringResource(R.string.workspace_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                items(filteredRepos) { repo ->
                    ElevatedCard(
                        onClick = { onOpenRepo(repo) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    repo.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    stringResource(R.string.last_modified_label, SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(repo.lastModified()))),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    maxLines = 1
                                )
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

        if (showIdentityDialog) {
            IdentityDialog(
                initialName = globalGitName,
                initialEmail = globalGitEmail,
                onDismiss = { showIdentityDialog = false },
                onConfirm = { name, email ->
                    onUpdateGlobalIdentity(name, email)
                    showIdentityDialog = false
                }
            )
        }
    }
}

@Composable
fun QuickActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
