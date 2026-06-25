package com.carocall.gitmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import com.carocall.gitmobile.R
import com.carocall.gitmobile.data.git.ComposeGitProgressMonitor
import com.carocall.gitmobile.data.git.GitManager
import com.carocall.gitmobile.data.model.GitAccount
import com.carocall.gitmobile.data.model.GitProgress
import com.carocall.gitmobile.ui.component.AccountSelectionDialog
import com.carocall.gitmobile.ui.component.ErrorDialog
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRepoScreen(
    gitAccounts: List<GitAccount>,
    onBack: () -> Unit,
    onRepoCreated: () -> Unit,
    onManageAccounts: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootDir = remember { context.filesDir }
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.create_repo), stringResource(R.string.clone_repo))

    var cloningProgress by remember { mutableStateOf<GitProgress?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_repo)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Box(Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> CreateLocalTab(
                        onConfirm = { name ->
                            val f = File(rootDir, name)
                            if (f.exists()) {
                                Toast.makeText(context, context.getString(R.string.dir_already_exists), Toast.LENGTH_SHORT).show()
                            } else if (f.mkdirs()) {
                                scope.launch {
                                    GitManager.initRepo(f)
                                    onRepoCreated()
                                }
                            }
                        }
                    )
                    1 -> CloneRemoteTab(
                        accounts = gitAccounts,
                        onManageAccounts = onManageAccounts,
                        onConfirm = { url, name, branch, accountId ->
                            val f = File(rootDir, name)
                            if (f.exists()) {
                                Toast.makeText(context, context.getString(R.string.dir_already_exists), Toast.LENGTH_SHORT).show()
                            } else {
                                scope.launch {
                                    val account = gitAccounts.find { it.id == accountId }
                                    val user = account?.username
                                    val token = account?.token

                                    val progressMonitor = ComposeGitProgressMonitor { progress ->
                                        cloningProgress = progress
                                    }

                                    val result = GitManager.clone(
                                        dir = f,
                                        url = url,
                                        username = user,
                                        token = token,
                                        accountId = accountId,
                                        branch = branch.ifBlank { null },
                                        progressMonitor = progressMonitor
                                    )
                                    
                                    cloningProgress = null
                                    if (result.isSuccess) {
                                        Toast.makeText(context, context.getString(R.string.clone_success), Toast.LENGTH_SHORT).show()
                                        onRepoCreated()
                                    } else {
                                        errorMessage = context.getString(R.string.clone_failed, result.exceptionOrNull()?.message)
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        if (cloningProgress != null) {
            val progress = cloningProgress!!
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.cloning_repo)) },
                text = {
                    Column {
                        Text(progress.displayString)
                        Spacer(Modifier.height(8.dp))
                        if (!progress.indeterminate) {
                            LinearProgressIndicator(
                                progress = { progress.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                confirmButton = {}
            )
        }

        errorMessage?.let { ErrorDialog(error = it, onDismiss = { errorMessage = null }) }
    }
}

@Composable
fun CreateLocalTab(onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.create_new_project), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.local_repo_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(
            onClick = { onConfirm(name) },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank()
        ) {
            Text(stringResource(R.string.confirm))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneRemoteTab(
    accounts: List<GitAccount>,
    onManageAccounts: () -> Unit,
    onConfirm: (url: String, name: String, branch: String, accountId: String?) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var showAccountSelector by remember { mutableStateOf(false) }

    val selectedAccount = accounts.find { it.id == selectedAccountId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.clone_remote_repo),
            style = MaterialTheme.typography.titleMedium,
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
            placeholder = { Text("https://github.com/user/repo.git") },
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
                        text = selectedAccount?.name ?: stringResource(R.string.no_identity),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
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
                if (url.isNotBlank() && name.isNotBlank()) {
                    onConfirm(url, name, branch, selectedAccountId)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = url.isNotBlank() && name.isNotBlank()
        ) {
            Text(stringResource(R.string.start_clone))
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
