package com.carocall.gitmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.carocall.gitmobile.data.model.LocalRepo
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
fun HomeScreen(
    localRepos: List<LocalRepo>,
    gitAccounts: List<GitAccount>,
    recentFiles: List<RecentFile>,
    globalGitName: String,
    globalGitEmail: String,
    onUpdateGlobalIdentity: (String, String) -> Unit,
    onOpenRepo: (LocalRepo) -> Unit,
    onOpenFile: (File) -> Unit,
    onOpenSettings: () -> Unit,
    onManageAccounts: () -> Unit,
    onAddRepo: () -> Unit,
    onViewAllRepos: () -> Unit,
    onUpdateRepo: (LocalRepo) -> Unit,
    onDeleteRepo: (String) -> Unit
) {
    val context = LocalContext.current
    
    var showRenameDialog by remember { mutableStateOf<LocalRepo?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<LocalRepo?>(null) }
    var showIdentityDialog by remember { mutableStateOf(false) }

    // On Home screen, we only show a few repos (e.g. 4 or 6)
    val displayRepos = remember(localRepos) {
        localRepos.sortedByDescending { it.lastOpened }.take(6)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.ExtraBold) }
            )
        }
    ) { padding ->
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
                                stringResource(R.string.identity_not_configured)
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
                var searchQuery by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_repositories)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
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
                        label = stringResource(R.string.setting_view_title_name),
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
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(recentFiles) { file ->
                            RecentFileCard(file = file, onClick = {
                                val f = File(file.path)
                                if (f.exists()) {
                                    onOpenFile(f)
                                } else {
                                    Toast.makeText(context, R.string.file_not_found, Toast.LENGTH_SHORT).show()
                                }
                            })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // 4. 仓库列表标题
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.workspace_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onViewAllRepos) {
                        Text(stringResource(R.string.view_all_repos))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(16.dp))
                    }
                }
            }

            if (displayRepos.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.first_project_hint), color = Color.Gray)
                        }
                    }
                }
            } else {
                // 两列网格布局
                val rows = displayRepos.chunked(2)
                rows.forEach { rowItems ->
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { repo ->
                                RepoGridItem(
                                    repo = repo,
                                    onClick = { onOpenRepo(repo) },
                                    onMenuClick = { /* Can show menu or just rely on full list for management */ },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        if (showRenameDialog != null) {
            val repo = showRenameDialog!!
            InputSheet(title = stringResource(R.string.rename_project_title), initialValue = repo.displayName, onDismiss = { showRenameDialog = null }, onConfirm = { newName ->
                onUpdateRepo(repo.copy(alias = newName))
                showRenameDialog = null
            })
        }

        if (showDeleteConfirm != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = null },
                title = { Text(stringResource(R.string.confirm_delete_title)) },
                text = { Text(stringResource(R.string.confirm_delete_msg, showDeleteConfirm?.displayName ?: "")) },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteRepo(showDeleteConfirm?.path ?: "")
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

@Composable
fun RepoGridItem(
    repo: LocalRepo,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.height(110.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .padding(12.dp)
                    .align(Alignment.TopStart)
            ) {
                Text(
                    text = repo.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                
                if (repo.alias.isNotBlank()) {
                    Text(
                        text = repo.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Spacer(Modifier.weight(1f))
                
                Text(
                    text = dateFormat.format(Date(repo.lastOpened)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            
            if (repo.isStarred) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier
                        .padding(8.dp)
                        .size(18.dp)
                        .align(Alignment.TopEnd)
                )
            }
        }
    }
}
