package com.carocall.gitmobile.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carocall.gitmobile.R
import com.carocall.gitmobile.data.model.CommitInfo
import com.carocall.gitmobile.data.model.GitAccount
import com.carocall.gitmobile.data.model.RemoteProfile
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputSheet(
    title: String,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onConfirm(value) },
                modifier = Modifier.fillMaxWidth(),
                enabled = value.isNotBlank()
            ) {
                Text(stringResource(R.string.confirm))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneSheet(
    accounts: List<GitAccount> = emptyList(),
    onManageAccounts: () -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (url: String, name: String, branch: String, accountId: String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var showAccountSelector by remember { mutableStateOf(false) }

    val selectedAccount = accounts.find { it.id == selectedAccountId }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.clone_remote_repo),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    if (name.isBlank() || name == url.substringBeforeLast(".git").substringAfterLast("/")) {
                        val suggestedName = it.substringAfterLast("/").substringBefore(".git")
                        if (suggestedName.isNotBlank()) name = suggestedName
                    }
                },
                label = { Text(stringResource(R.string.https_url)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.local_repo_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            OutlinedTextField(
                value = branch,
                onValueChange = { branch = it },
                label = { Text(stringResource(R.string.branch_name_optional)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            HorizontalDivider()
            
            OutlinedCard(
                onClick = { showAccountSelector = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccountCircle, null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = selectedAccount?.name ?: stringResource(R.string.select_git_account),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selectedAccount == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        if (selectedAccount != null) {
                            Text(selectedAccount.username, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Icon(Icons.Default.ArrowDropDown, null)
                }
            }
            
            Button(
                onClick = {
                    if (url.isNotBlank() && name.isNotBlank() && selectedAccount != null) {
                        onConfirm(url, name, branch, selectedAccount.id)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                enabled = url.isNotBlank() && name.isNotBlank() && selectedAccount != null
            ) {
                Text(stringResource(R.string.start_clone))
            }
        }
    }

    if (showAccountSelector) {
        AccountSelectionDialog(
            accounts = accounts,
            currentAccountId = selectedAccountId,
            onManageAccounts = onManageAccounts,
            onDismiss = { showAccountSelector = false },
            onSelect = {
                selectedAccountId = it?.id
                showAccountSelector = false
            }
        )
    }
}

@Composable
fun AccountSelectionDialog(
    accounts: List<GitAccount>,
    currentAccountId: String?,
    onManageAccounts: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (GitAccount?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.select_git_account))
                Text(
                    text = stringResource(R.string.configure_now),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { 
                        onDismiss()
                        onManageAccounts()
                    }
                )
            }
        },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = currentAccountId == null, onClick = { onSelect(null) })
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = currentAccountId == null, onClick = null)
                    Text(stringResource(R.string.no_identity), Modifier.padding(start = 16.dp))
                }
                accounts.forEach { account ->
                    val isSelected = account.id == currentAccountId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = isSelected, onClick = { onSelect(account) })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(account.name)
                            Text(account.username, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteProfileSheet(
    title: String,
    initialProfile: RemoteProfile? = null,
    accounts: List<GitAccount> = emptyList(),
    onManageAccounts: () -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (RemoteProfile) -> Unit
) {
    var name by remember { mutableStateOf(initialProfile?.name ?: "") }
    var url by remember { mutableStateOf(initialProfile?.url ?: "") }
    var selectedAccountId by remember { mutableStateOf(initialProfile?.accountId) }
    var showAccountSelector by remember { mutableStateOf(false) }

    val selectedAccount = accounts.find { it.id == selectedAccountId }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.remote_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.https_url)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            HorizontalDivider()
            Text(stringResource(R.string.select_git_account), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            OutlinedCard(
                onClick = { showAccountSelector = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccountCircle, null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = selectedAccount?.name ?: stringResource(R.string.select_git_account),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selectedAccount == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        if (selectedAccount != null) {
                            Text(selectedAccount.username, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Icon(Icons.Default.ArrowDropDown, null)
                }
            }
            
            Button(
                onClick = {
                    if (name.isNotBlank() && url.isNotBlank() && selectedAccountId != null) {
                        onConfirm(RemoteProfile(name, url, selectedAccountId))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && url.isNotBlank() && selectedAccountId != null
            ) {
                Text(stringResource(R.string.ok))
            }
        }
    }

    if (showAccountSelector) {
        AccountSelectionDialog(
            accounts = accounts,
            currentAccountId = selectedAccountId,
            onManageAccounts = onManageAccounts,
            onDismiss = { showAccountSelector = false },
            onSelect = {
                selectedAccountId = it?.id
                showAccountSelector = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitChangesSheet(
    commit: CommitInfo?,
    changes: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onFileClick: (String) -> Unit,
    onAction: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.change_details, commit?.id?.take(7) ?: ""), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    commit?.let {
                        Text("${it.author} • ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it.time))}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            
            Spacer(Modifier.height(8.dp))
            Text(commit?.message ?: "", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(onClick = { onAction("CHECKOUT") }, label = { Text("Checkout") }, leadingIcon = { Icon(Icons.Default.Sync, null, Modifier.size(18.dp)) })
                AssistChip(onClick = { onAction("CHERRY_PICK") }, label = { Text("Cherry-pick") }, leadingIcon = { Icon(Icons.Default.ContentPaste, null, Modifier.size(18.dp)) })
                AssistChip(onClick = { onAction("REVERT") }, label = { Text("Revert") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp)) })
                AssistChip(onClick = { onAction("TAG") }, label = { Text("Tag") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null, Modifier.size(18.dp)) })
                AssistChip(onClick = { onAction("BRANCH") }, label = { Text("Branch") }, leadingIcon = { Icon(Icons.Default.AccountTree, null, Modifier.size(18.dp)) })
            }

            Spacer(Modifier.height(16.dp))
            Text("Files Changed (${changes.size})", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            
            LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 300.dp)) {
                items(changes) { (path, type) ->
                    ListItem(
                        headlineContent = { Text(path, fontSize = 14.sp) },
                        supportingContent = { 
                            val color = when (type) {
                                "ADD" -> Color(0xFF4CAF50)
                                "DELETE" -> Color(0xFFE91E63)
                                else -> Color(0xFF2196F3)
                            }
                            Text(type, color = color, fontSize = 11.sp) 
                        },
                        modifier = Modifier.clickable { onFileClick(path) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffSheet(
    fileName: String,
    diffContent: String,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.fillMaxHeight(0.9f)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fileName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.medium
            ) {
                Box(Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                    Text(
                        text = diffContent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp),
                        softWrap = false
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDetailsSheet(
    file: File,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.file_info), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            DetailItem(stringResource(R.string.path_label, ""), file.absolutePath)
            DetailItem(stringResource(R.string.size_label, ""), formatFileSize(file.length()))
            DetailItem(stringResource(R.string.last_modified_label, ""), SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified())))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.close))
            }
        }
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
