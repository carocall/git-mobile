package com.carocall.gitmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carocall.gitmobile.data.git.GitManager
import com.carocall.gitmobile.data.model.RepoStatus
import kotlinx.coroutines.launch
import java.io.File

/**
 * Git 提交界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitCommitScreen(repoRoot: File, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(RepoStatus()) }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }
    var commitMessage by remember { mutableStateOf("") }

    fun refreshStatus() {
        scope.launch { status = GitManager.getStatus(repoRoot) }
    }
    LaunchedEffect(Unit) { refreshStatus() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("源代码管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = commitMessage,
                onValueChange = { commitMessage = it },
                label = { Text("提交信息") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (commitMessage.isNotBlank() && selectedFiles.isNotEmpty()) {
                        scope.launch {
                            GitManager.commit(repoRoot, commitMessage, selectedFiles.toList())
                                .onSuccess {
                                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                    refreshStatus(); commitMessage = ""; selectedFiles = emptySet()
                                }.onFailure {
                                Toast.makeText(
                                    context,
                                    "错误: ${it.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("提交 (Commit)") }
            Spacer(Modifier.height(16.dp))
            Text(
                "更改内容",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            LazyColumn {
                items(status.allChanges) { (path, type) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedFiles =
                                    if (selectedFiles.contains(path)) selectedFiles - path else selectedFiles + path
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selectedFiles.contains(path),
                            onCheckedChange = {
                                selectedFiles =
                                    if (it) selectedFiles + path else selectedFiles - path
                            })
                        Column {
                            Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                type,
                                fontSize = 12.sp,
                                color = if (type == "Untracked") Color(0xFF4CAF50) else Color(
                                    0xFF2196F3
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
