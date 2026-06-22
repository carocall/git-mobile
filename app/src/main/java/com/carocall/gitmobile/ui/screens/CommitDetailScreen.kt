package com.carocall.gitmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carocall.gitmobile.R
import com.carocall.gitmobile.data.git.GitManager
import com.carocall.gitmobile.data.model.CommitInfo
import com.carocall.gitmobile.ui.component.ErrorDialog
import com.carocall.gitmobile.ui.component.InputSheet
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitDetailScreen(
    repoRoot: File,
    commitId: String,
    onBack: () -> Unit,
    onViewDiff: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var commitInfo by remember { mutableStateOf<CommitInfo?>(null) }
    var changes by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var showTagInput by remember { mutableStateOf(false) }
    var showBranchInput by remember { mutableStateOf(false) }

    LaunchedEffect(commitId) {
        isLoading = true
        commitInfo = GitManager.getCommitInfo(repoRoot, commitId)
        changes = GitManager.getCommitChanges(repoRoot, commitId)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.change_details, commitId.take(7))) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                Column(Modifier.padding(16.dp)) {
                    commitInfo?.let { info ->
                        Text(info.message, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${info.author} • ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(info.time))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Text(info.id, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                scope.launch {
                                    GitManager.checkoutCommit(repoRoot, commitId).onSuccess {
                                        Toast.makeText(context, "Checked out ${commitId.take(7)}", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    }.onFailure { errorMessage = it.message }
                                }
                            },
                            label = { Text("Checkout") },
                            leadingIcon = { Icon(Icons.Default.Sync, null, Modifier.size(18.dp)) }
                        )
                        AssistChip(
                            onClick = {
                                scope.launch {
                                    GitManager.cherryPick(repoRoot, commitId).onSuccess {
                                        Toast.makeText(context, "Cherry picked!", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    }.onFailure { errorMessage = it.message }
                                }
                            },
                            label = { Text("Cherry-pick") },
                            leadingIcon = { Icon(Icons.Default.ContentPaste, null, Modifier.size(18.dp)) }
                        )
                        AssistChip(
                            onClick = {
                                scope.launch {
                                    GitManager.revertCommit(repoRoot, commitId).onSuccess {
                                        Toast.makeText(context, "Reverted!", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    }.onFailure { errorMessage = it.message }
                                }
                            },
                            label = { Text("Revert") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp)) }
                        )
                        AssistChip(
                            onClick = { showTagInput = true },
                            label = { Text("Tag") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null, Modifier.size(18.dp)) }
                        )
                        AssistChip(
                            onClick = { showBranchInput = true },
                            label = { Text("Branch") },
                            leadingIcon = { Icon(Icons.Default.AccountTree, null, Modifier.size(18.dp)) }
                        )
                    }
                }

                HorizontalDivider()
                
                Text(
                    "Files Changed (${changes.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                LazyColumn(modifier = Modifier.weight(1f)) {
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
                            modifier = Modifier.clickable { onViewDiff(path) }
                        )
                    }
                }
            }
        }

        if (showTagInput) {
            InputSheet(
                title = "Add Tag to ${commitId.take(7)}",
                onDismiss = { showTagInput = false },
                onConfirm = { tagName ->
                    scope.launch {
                        GitManager.addTag(repoRoot, tagName, commitId).onSuccess {
                            Toast.makeText(context, "Tag $tagName added", Toast.LENGTH_SHORT).show()
                            showTagInput = false
                        }.onFailure { errorMessage = it.message }
                    }
                }
            )
        }

        if (showBranchInput) {
            InputSheet(
                title = "Create Branch from ${commitId.take(7)}",
                onDismiss = { showBranchInput = false },
                onConfirm = { branchName ->
                    scope.launch {
                        GitManager.createBranch(repoRoot, branchName, startPoint = commitId).onSuccess {
                            Toast.makeText(context, "Branch $branchName created", Toast.LENGTH_SHORT).show()
                            showBranchInput = false
                        }.onFailure { errorMessage = it.message }
                    }
                }
            )
        }

        errorMessage?.let { ErrorDialog(error = it, onDismiss = { errorMessage = null }) }
    }
}
