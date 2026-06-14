package com.carocall.gitmobile.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.carocall.gitmobile.R

@Composable
fun InputDialog(title: String, initialValue: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initialValue) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { TextField(value = name, onValueChange = { name = it }, singleLine = true) },
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
            Column {
                TextField(url, {
                    url = it
                    // 自动从 URL 提取仓库名作为默认建议
                    if (name.isBlank() || name == url.substringBeforeLast(".git").substringAfterLast("/")) {
                        val suggestedName = it.substringAfterLast("/").substringBefore(".git")
                        if (suggestedName.isNotBlank()) name = suggestedName
                    }
                }, label = { Text(stringResource(R.string.https_url)) })
                Spacer(Modifier.height(8.dp))
                TextField(name, { name = it }, label = { Text(stringResource(R.string.local_repo_name)) })
                Spacer(Modifier.height(8.dp))
                TextField(branch, { branch = it }, label = { Text(stringResource(R.string.branch_name_optional)) })
                Spacer(Modifier.height(8.dp))
                TextField(user, { user = it }, label = { Text(stringResource(R.string.user_optional)) })
                Spacer(Modifier.height(8.dp))
                TextField(token, { token = it }, label = { Text(stringResource(R.string.token_optional)) })
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
            Column {
                TextField(url, { url = it }, label = { Text(stringResource(R.string.https_url)) })
                Spacer(Modifier.height(8.dp))
                TextField(user, { user = it }, label = { Text(stringResource(R.string.username)) })
                Spacer(Modifier.height(8.dp))
                TextField(token, { token = it }, label = { Text(stringResource(R.string.token_password)) })
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
