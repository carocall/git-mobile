package com.carocall.gitmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carocall.gitmobile.R
import com.carocall.gitmobile.data.git.GitManager
import com.carocall.gitmobile.data.model.BranchInfo
import com.carocall.gitmobile.ui.component.ErrorDialog
import com.carocall.gitmobile.ui.component.InputSheet
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchManagementScreen(repoRoot: File, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var branches by remember { mutableStateOf<List<BranchInfo>>(emptyList()) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var branchToDelete by remember { mutableStateOf<BranchInfo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            branches = GitManager.getBranches(repoRoot)
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.branch_management)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.new_branch))
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text(stringResource(R.string.local_branches)) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text(stringResource(R.string.remote_branches)) }
                )
            }

            val filteredBranches = if (selectedTabIndex == 0) {
                branches.filter { !it.isRemote }
            } else {
                branches.filter { it.isRemote }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(filteredBranches) { branch ->
                    BranchItem(
                        branch = branch,
                        onClick = {
                            if (!branch.isRemote && !branch.isCurrent) {
                                scope.launch {
                                    GitManager.checkoutBranch(repoRoot, branch.name).onSuccess {
                                        Toast.makeText(context, context.getString(R.string.branch_checkout_success, branch.name), Toast.LENGTH_SHORT).show()
                                        refresh()
                                    }.onFailure {
                                        errorMessage = it.message
                                    }
                                }
                            }
                        },
                        onDelete = {
                            branchToDelete = branch
                        }
                    )
                }
            }
        }

        if (showCreateDialog) {
            InputSheet(
                title = stringResource(R.string.create_branch_title),
                onDismiss = { showCreateDialog = false },
                onConfirm = { name ->
                    scope.launch {
                        GitManager.createBranch(repoRoot, name).onSuccess {
                            Toast.makeText(context, context.getString(R.string.branch_create_success, name), Toast.LENGTH_SHORT).show()
                            refresh()
                            showCreateDialog = false
                        }.onFailure {
                            errorMessage = it.message
                        }
                    }
                }
            )
        }

        if (branchToDelete != null) {
            AlertDialog(
                onDismissRequest = { branchToDelete = null },
                title = { Text(stringResource(R.string.confirm_delete_title)) },
                text = { Text(stringResource(R.string.confirm_delete_branch_msg, branchToDelete!!.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val name = branchToDelete!!.name
                            scope.launch {
                                GitManager.deleteBranch(repoRoot, name).onSuccess {
                                    Toast.makeText(context, context.getString(R.string.branch_delete_success, name), Toast.LENGTH_SHORT).show()
                                    refresh()
                                    branchToDelete = null
                                }.onFailure {
                                    errorMessage = it.message
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { branchToDelete = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        errorMessage?.let { ErrorDialog(error = it, onDismiss = { errorMessage = null }) }
    }
}

@Composable
fun BranchItem(
    branch: BranchInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = branch.name,
                fontWeight = if (branch.isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (branch.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Text(branch.shortHash, fontSize = 12.sp, color = Color.Gray)
        },
        leadingContent = {
            if (branch.isCurrent) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            } else {
                Spacer(Modifier.size(24.dp))
            }
        },
        trailingContent = {
            if (!branch.isCurrent && !branch.isRemote) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        modifier = Modifier.clickable { onClick() }
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
}
