package com.carocall.gitmobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carocall.gitmobile.R
import com.carocall.gitmobile.data.model.LocalRepo
import com.carocall.gitmobile.ui.component.InputSheet
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoListScreen(
    repos: List<LocalRepo>,
    onBack: () -> Unit,
    onOpenRepo: (LocalRepo) -> Unit,
    onUpdateRepo: (LocalRepo) -> Unit,
    onDeleteRepo: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf<LocalRepo?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<LocalRepo?>(null) }

    val filteredRepos = remember(repos, searchQuery) {
        repos.filter { 
            it.displayName.contains(searchQuery, ignoreCase = true) || 
            it.path.contains(searchQuery, ignoreCase = true)
        }.sortedByDescending { it.isStarred }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.repo_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text(stringResource(R.string.search_repositories)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                } else null,
                shape = MaterialTheme.shapes.medium,
                singleLine = true
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredRepos) { repo ->
                    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
                    
                    ElevatedCard(
                        onClick = { onOpenRepo(repo) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = repo.displayName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (repo.alias.isNotBlank()) {
                                        Text(
                                            text = repo.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                
                                IconButton(onClick = { onUpdateRepo(repo.copy(isStarred = !repo.isStarred)) }) {
                                    Icon(
                                        imageVector = if (repo.isStarred) Icons.Default.Star else Icons.Default.StarOutline,
                                        contentDescription = null,
                                        tint = if (repo.isStarred) Color(0xFFFFB300) else Color.Gray
                                    )
                                }
                                
                                var menuExpanded by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, null, tint = Color.Gray) }
                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.rename_repo)) },
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
                            
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(12.dp))
                            
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = repo.path,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = dateFormat.format(Date(repo.lastOpened)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showRenameDialog != null) {
            val repo = showRenameDialog!!
            InputSheet(
                title = stringResource(R.string.rename_repo),
                initialValue = repo.displayName,
                onDismiss = { showRenameDialog = null },
                onConfirm = { newName ->
                    onUpdateRepo(repo.copy(alias = newName))
                    showRenameDialog = null
                }
            )
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
                    ) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }
}
