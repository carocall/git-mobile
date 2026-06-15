package com.carocall.gitmobile.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            
            Button(
                onClick = { if (value.isNotBlank()) onConfirm(value) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(stringResource(R.string.confirm))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneSheet(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

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
            
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text(stringResource(R.string.username)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(stringResource(R.string.token_password)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Button(
                onClick = {
                    if (url.isNotBlank() && name.isNotBlank()) {
                        onConfirm(url, name, branch, user, token)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(stringResource(R.string.start_clone))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteProfileSheet(
    title: String,
    initialProfile: RemoteProfile? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialProfile?.name ?: "") }
    var url by remember { mutableStateOf(initialProfile?.url ?: "") }
    var user by remember { mutableStateOf(initialProfile?.user ?: "") }
    var token by remember { mutableStateOf(initialProfile?.token ?: "") }

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
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text(stringResource(R.string.user_optional)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(stringResource(R.string.token_optional)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Button(
                onClick = {
                    if (name.isNotBlank() && url.isNotBlank()) {
                        onConfirm(name, url, user, token)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text(stringResource(R.string.ok))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitChangesSheet(
    commit: CommitInfo?,
    changes: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onFileClick: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                stringResource(R.string.commit_changes_title, commit?.message?.take(20) ?: ""),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(changes) { (path, type) ->
                    ListItem(
                        headlineContent = { Text(path, fontSize = 14.sp) },
                        trailingContent = { 
                            Text(
                                type.take(1), 
                                fontWeight = FontWeight.Bold, 
                                color = if(type == "ADD") Color(0xFF4CAF50) else Color(0xFFE91E63)
                            ) 
                        },
                        modifier = Modifier.clickable { onFileClick(path) }
                    )
                }
            }
            
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End).padding(end = 16.dp)
            ) {
                Text(stringResource(R.string.close))
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                stringResource(R.string.change_details, fileName),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            ) {
                Column {
                    diffContent.lines().forEach { line ->
                        val color = when {
                            line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF4CAF50)
                            line.startsWith("-") && !line.startsWith("---") -> Color(0xFFE91E63)
                            line.startsWith("@@") -> Color(0xFF2196F3)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            line,
                            color = color,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
            
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End).padding(end = 16.dp)
            ) {
                Text(stringResource(R.string.close))
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
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.file_info),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailItem(stringResource(R.string.rename), file.name)
                DetailItem(stringResource(R.string.path_label, "").substringBefore(":"), file.absolutePath)
                DetailItem(stringResource(R.string.size_label, "").substringBefore(":"), formatFileSize(file.length()))
                DetailItem(stringResource(R.string.last_modified_label, "").substringBefore(":"), dateFormat.format(Date(file.lastModified())))
            }
            
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
    return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
