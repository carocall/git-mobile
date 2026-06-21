package com.carocall.gitmobile.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carocall.gitmobile.R
import com.carocall.gitmobile.data.model.GitAccount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitAccountsScreen(
    accounts: List<GitAccount>,
    onSaveAccount: (GitAccount) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onBack: () -> Unit
) {
    var editingAccount by remember { mutableStateOf<GitAccount?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.git_accounts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Account")
                    }
                }
            )
        }
    ) { padding ->
        if (accounts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_git_accounts), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(accounts) { account ->
                    GitAccountCard(
                        account = account,
                        onClick = { editingAccount = account },
                        onDelete = { onDeleteAccount(account.id) }
                    )
                }
            }
        }

        if (showAddDialog || editingAccount != null) {
            val account = editingAccount ?: GitAccount(name = "", username = "", token = "")
            GitAccountEditDialog(
                initialAccount = account,
                onDismiss = {
                    showAddDialog = false
                    editingAccount = null
                },
                onConfirm = {
                    onSaveAccount(it)
                    showAddDialog = false
                    editingAccount = null
                }
            )
        }
    }
}

@Composable
fun GitAccountCard(
    account: GitAccount,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.titleMedium)
                Text(account.username, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
