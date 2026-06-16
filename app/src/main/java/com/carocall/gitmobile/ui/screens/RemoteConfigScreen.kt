package com.carocall.gitmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carocall.gitmobile.R
import com.carocall.gitmobile.data.git.GitManager
import com.carocall.gitmobile.data.model.RemoteProfile
import com.carocall.gitmobile.ui.component.ErrorDialog
import com.carocall.gitmobile.ui.component.RemoteProfileSheet
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteConfigScreen(repoRoot: File, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<RemoteProfile>>(emptyList()) }
    var editingProfile by remember { mutableStateOf<RemoteProfile?>(null) }
    var profileToDelete by remember { mutableStateOf<RemoteProfile?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            profiles = GitManager.getRemoteProfiles(repoRoot)
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
                    RemoteProfileCard(
                        profile = profile,
                        onUse = {
                            scope.launch {
                                GitManager.saveRemoteConfig(repoRoot, profile.url, profile.user, profile.token)
                                Toast.makeText(context, context.getString(R.string.remote_config_saved), Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        },
                        onEdit = { editingProfile = profile },
                        onDelete = {
                            profileToDelete = profile
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
                onDismiss = { showAddDialog = false },
                onConfirm = { name, url, user, token ->
                    scope.launch {
                        GitManager.saveRemoteProfile(repoRoot, RemoteProfile(name, url, user, token))
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
                onDismiss = { editingProfile = null },
                onConfirm = { name, url, user, token ->
                    scope.launch {
                        if (name != editingProfile?.name) {
                            GitManager.deleteRemoteProfile(repoRoot, editingProfile!!.name)
                        }
                        GitManager.saveRemoteProfile(repoRoot, RemoteProfile(name, url, user, token))
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
    }
}

@Composable
fun RemoteProfileCard(
    profile: RemoteProfile,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.url_label, profile.url),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            
            Text(
                text = stringResource(R.string.identity_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (profile.user.isNotBlank()) {
                Text(
                    text = stringResource(R.string.user_label, profile.user),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.token_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(R.string.no_identity),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.edit_remote))
                }
                
                Button(
                    onClick = onUse,
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    Text(stringResource(R.string.use_remote))
                }
            }
        }
    }
}

