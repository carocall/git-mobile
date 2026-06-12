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

@Composable
fun InputDialog(title: String, initialValue: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initialValue) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { TextField(value = name, onValueChange = { name = it }, singleLine = true) },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun PushDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    GitAuthDialog(title = "配置远程同步", confirmText = "开始同步", onDismiss = onDismiss, onConfirm = onConfirm)
}

@Composable
fun CloneDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    GitAuthDialog(title = "克隆远程仓库", confirmText = "开始克隆", onDismiss = onDismiss, onConfirm = onConfirm)
}

@Composable
fun GitAuthDialog(title: String, confirmText: String, onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column {
                TextField(url, { url = it }, label = { Text("HTTPS 远程地址") })
                Spacer(Modifier.height(8.dp))
                TextField(user, { user = it }, label = { Text("用户名") })
                Spacer(Modifier.height(8.dp))
                TextField(token, { token = it }, label = { Text("Access Token / 密码") })
            }
        },
        confirmButton = {
            Button(onClick = {
                if (url.isNotBlank() && user.isNotBlank() && token.isNotBlank()) {
                    onConfirm(url, user, token)
                }
            }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}