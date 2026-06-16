package com.carocall.gitmobile.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carocall.gitmobile.R

@Composable
fun ErrorDialog(
    title: String = stringResource(R.string.error_title),
    error: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.error) },
        text = {
            Column {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Git Error", error)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
            }) {
                Text(stringResource(R.string.copy))
            }
        }
    )
}

@Composable
fun InputDialog(title: String, initialValue: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initialValue) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun PushDialog(
    initialUrl: String = "",
    initialUser: String = "",
    initialToken: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    GitAuthDialog(
        title = stringResource(R.string.config_remote_sync),
        confirmText = stringResource(R.string.save_config),
        initialUrl = initialUrl,
        initialUser = initialUser,
        initialToken = initialToken,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
fun CloneDialog(onDismiss: () -> Unit, onConfirm: (String, String, String, String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.clone_remote_repo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            }
        },
        confirmButton = {
            Button(onClick = {
                if (url.isNotBlank() && name.isNotBlank()) {
                    onConfirm(url, name, branch, user, token)
                }
            }) { Text(stringResource(R.string.start_clone)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun GitAuthDialog(
    title: String,
    confirmText: String,
    initialUrl: String = "",
    initialUser: String = "",
    initialToken: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var user by remember { mutableStateOf(initialUser) }
    var token by remember { mutableStateOf(initialToken) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            }
        },
        confirmButton = {
            Button(onClick = {
                if (url.isNotBlank() && user.isNotBlank() && token.isNotBlank()) {
                    onConfirm(url, user, token)
                }
            }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
