package com.carocall.gitmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carocall.gitmobile.R
import com.carocall.gitmobile.data.git.GitManager
import com.carocall.gitmobile.data.model.GitAccount
import com.carocall.gitmobile.data.model.RemoteProfile
import com.carocall.gitmobile.ui.component.ErrorDialog
import com.carocall.gitmobile.ui.component.RemoteProfileSheet
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteConfigScreen(
    repoRoot: File,
    gitAccounts: List<GitAccount>,
    onManageAccounts: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<RemoteProfile>>(emptyList()) }
    var currentRemoteUrl by remember { mutableStateOf("") }
    var currentAccountId by remember { mutableStateOf<String?>(null) }
    var editingProfile by remember { mutableStateOf<RemoteProfile?>(null) }
    var profileToDelete by remember { mutableStateOf<RemoteProfile?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            profiles = GitManager.getRemoteProfiles(repoRoot)
            val config = GitManager.getRemoteConfig(repoRoot)
            currentRemoteUrl = config.url
            currentAccountId = config.accountId
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.remote_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(profiles) { profile ->
                    val account = gitAccounts.find { it.id == profile.accountId }
                    val effectiveUser = account?.username ?: ""
                    val effectiveToken = account?.token ?: ""
                    val accountName = account?.name
                    
                    RemoteProfileCard(
                        profile = profile,
                        accountName = accountName,
                        effectiveUser = effectiveUser,
                        isInUse = profile.url == currentRemoteUrl && profile.accountId == currentAccountId,
                        onUse = {
                            scope.launch {
                                GitManager.saveRemoteConfig(repoRoot, profile)
                                // 切换后自动获取最新的分支信息
                                GitManager.fetch(repoRoot, effectiveUser, effectiveToken)
                                Toast.makeText(context, context.getString(R.string.remote_config_saved), Toast.LENGTH_SHORT).show()
                                refresh()
                            }
                        },
                        onEdit = { editingProfile = profile },
                        onDelete = {
                            profileToDelete = profile
                        },
                        onTest = {
                            scope.launch {
                                isTestingConnection = true
                                val result = GitManager.testConnection(profile.url, effectiveUser, effectiveToken)
                                isTestingConnection = false
                                if (result.isSuccess) {
                                    Toast.makeText(context, context.getString(R.string.connection_success), Toast.LENGTH_SHORT).show()
                                } else {
                                    errorMessage = context.getString(R.string.connection_failed, result.exceptionOrNull()?.message)
                                }
                            }
                        }
                    )
                }
            }
            
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_remote))
            }
        }

        if (showAddDialog) {
            RemoteProfileSheet(
                title = stringResource(R.string.add_remote_title),
                accounts = gitAccounts,
                onManageAccounts = onManageAccounts,
                onDismiss = { showAddDialog = false },
                onConfirm = { profile ->
                    scope.launch {
                        GitManager.saveRemoteProfile(repoRoot, profile)
                        refresh()
                        showAddDialog = false
                    }
                }
            )
        }

        if (editingProfile != null) {
            RemoteProfileSheet(
                title = stringResource(R.string.edit_remote),
                initialProfile = editingProfile,
                accounts = gitAccounts,
                onManageAccounts = onManageAccounts,
                onDismiss = { editingProfile = null },
                onConfirm = { profile ->
                    scope.launch {
                        if (profile.name != editingProfile?.name) {
                            GitManager.deleteRemoteProfile(repoRoot, editingProfile!!.name)
                        }
                        GitManager.saveRemoteProfile(repoRoot, profile)
                        refresh()
                        editingProfile = null
                    }
                }
            )
        }

        if (profileToDelete != null) {
            AlertDialog(
                onDismissRequest = { profileToDelete = null },
                title = { Text(stringResource(R.string.confirm_delete_remote)) },
                text = { Text(stringResource(R.string.confirm_delete_remote_msg, profileToDelete!!.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val name = profileToDelete!!.name
                            scope.launch {
                                GitManager.deleteRemoteProfile(repoRoot, name)
                                refresh()
                                profileToDelete = null
                            }
                        }
                    ) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { profileToDelete = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        errorMessage?.let { ErrorDialog(error = it, onDismiss = { errorMessage = null }) }

        if (isTestingConnection) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text(stringResource(R.string.testing_connection)) },
                text = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            )
        }
    }
}

@Composable
fun RemoteProfileCard(
    profile: RemoteProfile,
    accountName: String?,
    effectiveUser: String,
    isInUse: Boolean,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    Surface(
        onClick = { if (!isInUse) onUse() },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isInUse) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (isInUse) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isInUse) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isInUse) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        Text(
                            text = stringResource(R.string.in_use),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete, 
                        null, 
                        tint = if (isInUse) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) 
                               else MaterialTheme.colorScheme.error, 
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = profile.url,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isInUse) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) 
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AccountBox,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isInUse) MaterialTheme.colorScheme.onPrimaryContainer 
                           else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (!accountName.isNullOrBlank()) "$accountName ($effectiveUser)" 
                           else if (effectiveUser.isNotBlank()) effectiveUser 
                           else stringResource(R.string.no_identity),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isInUse) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) 
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onTest,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isInUse) MaterialTheme.colorScheme.onPrimaryContainer 
                                      else MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(
                        1.dp, 
                        if (isInUse) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f) 
                        else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.test_connection), style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isInUse) MaterialTheme.colorScheme.onPrimaryContainer 
                                      else MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(
                        1.dp, 
                        if (isInUse) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f) 
                        else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.edit_remote), style = MaterialTheme.typography.labelLarge)
                }

                Spacer(Modifier.weight(1f))

                if (!isInUse) {
                    Button(
                        onClick = onUse,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Text(stringResource(R.string.use_remote), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

