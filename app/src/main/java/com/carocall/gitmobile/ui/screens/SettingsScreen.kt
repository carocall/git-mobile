package com.carocall.gitmobile.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavController
import com.carocall.gitmobile.R
import com.carocall.gitmobile.data.model.GitAccount
import com.carocall.gitmobile.ui.theme.ThemeMode
import com.carocall.gitmobile.ui.util.navigateSafe
import com.carocall.gitmobile.ui.util.popBackStackSafe

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    currentSortOrder: RepoSortOrder,
    onSortOrderChange: (RepoSortOrder) -> Unit,
    globalGitName: String,
    globalGitEmail: String,
    onGlobalGitIdentityChange: (String, String) -> Unit,
    gitAccounts: List<GitAccount>,
    navController: NavController,
) {
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showGitIdentityDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(id = R.string.setting_view_title_name)) },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStackSafe()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            SettingGroup(title = stringResource(R.string.settings_general)) {
                SettingItem(
                    title = stringResource(R.string.settings_repo_sort),
                    subtitle = when (currentSortOrder) {
                        RepoSortOrder.TIME -> stringResource(R.string.sort_by_time)
                        RepoSortOrder.NAME -> stringResource(R.string.sort_by_name)
                    },
                    icon = Icons.AutoMirrored.Filled.Sort,
                    onClick = { showSortDialog = true }
                )
                
                SettingItem(
                    title = stringResource(R.string.settings_language),
                    subtitle = when (AppCompatDelegate.getApplicationLocales()[0]?.language) {
                        "zh" -> stringResource(R.string.language_chinese)
                        "en" -> stringResource(R.string.language_english)
                        else -> stringResource(R.string.language_system)
                    },
                    icon = Icons.Default.Language,
                    onClick = { showLanguageDialog = true }
                )

                SettingItem(
                    title = stringResource(R.string.settings_theme),
                    subtitle = when (currentTheme) {
                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
                    },
                    icon = Icons.Default.Palette,
                    onClick = { showThemeDialog = true },
                    showDivider = false
                )
            }

            SettingGroup(title = stringResource(R.string.identity_section)) {
                SettingItem(
                    title = stringResource(R.string.global_git_identity),
                    subtitle = if (globalGitName.isNotBlank()) "$globalGitName <$globalGitEmail>" else stringResource(R.string.git_identity_not_set),
                    icon = Icons.Default.Person,
                    onClick = { showGitIdentityDialog = true }
                )

                SettingItem(
                    title = stringResource(R.string.git_accounts_title),
                    subtitle = stringResource(R.string.git_accounts_subtitle, gitAccounts.size),
                    icon = Icons.Default.Key,
                    onClick = { navController.navigateSafe("git_accounts") },
                    showDivider = false
                )
            }

            SettingGroup(title = stringResource(R.string.settings_about_group)) {
                SettingItem(
                    title = stringResource(R.string.settings_about),
                    icon = Icons.Default.Info,
                    showDivider = false
                )
            }
        }

        if (showLanguageDialog) {
            LanguageSelectionDialog(
                onDismiss = { showLanguageDialog = false }
            ) { tag ->
                val appLocale: LocaleListCompat = if (tag.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(tag)
                }
                AppCompatDelegate.setApplicationLocales(appLocale)
                showLanguageDialog = false
            }
        }

        if (showThemeDialog) {
            ThemeSelectionDialog(
                currentTheme = currentTheme,
                onDismiss = { showThemeDialog = false },
                onThemeSelected = { mode ->
                    onThemeChange(mode)
                    showThemeDialog = false
                }
            )
        }

        if (showSortDialog) {
            SortSelectionDialog(
                currentSortOrder = currentSortOrder,
                onDismiss = { showSortDialog = false },
                onSortOrderSelected = { order ->
                    onSortOrderChange(order)
                    showSortDialog = false
                }
            )
        }

        if (showGitIdentityDialog) {
            GitIdentityDialog(
                initialName = globalGitName,
                initialEmail = globalGitEmail,
                onDismiss = { showGitIdentityDialog = false },
                onConfirm = { name, email ->
                    onGlobalGitIdentityChange(name, email)
                    showGitIdentityDialog = false
                }
            )
        }
    }
}

@Composable
fun GitAccountEditDialog(
    initialAccount: GitAccount,
    onDismiss: () -> Unit,
    onConfirm: (GitAccount) -> Unit
) {
    var name by remember { mutableStateOf(initialAccount.name) }
    var username by remember { mutableStateOf(initialAccount.username) }
    var token by remember { mutableStateOf(initialAccount.token) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialAccount.name.isEmpty()) stringResource(R.string.add_account) else stringResource(R.string.edit_account)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.account_name_label)) },
                    placeholder = { Text("e.g. GitHub - Personal") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.token_or_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(initialAccount.copy(name = name, username = username, token = token)) },
                enabled = name.isNotBlank() && username.isNotBlank() && token.isNotBlank()
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun GitIdentityDialog(
    initialName: String,
    initialEmail: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.global_git_identity)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.git_identity_description), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.author_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.author_email)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, email) }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun SettingGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
            content = { Column(content = content) }
        )
    }
}

@Composable
fun SettingItem(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    showDivider: Boolean = true,
    onClick: () -> Unit = {},
    trailingContent: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            if (trailingContent != null) {
                trailingContent()
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onDismiss: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit
) {
    val themes = listOf(
        ThemeMode.SYSTEM to stringResource(R.string.theme_follow_system),
        ThemeMode.LIGHT to stringResource(R.string.theme_light_mode),
        ThemeMode.DARK to stringResource(R.string.theme_dark_mode)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_theme)) },
        text = {
            Column(Modifier.selectableGroup()) {
                themes.forEach { (mode, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (mode == currentTheme),
                                onClick = { onThemeSelected(mode) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (mode == currentTheme), onClick = null)
                        Text(text = label, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun LanguageSelectionDialog(
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    val currentLanguageTag = AppCompatDelegate.getApplicationLocales()[0]?.language ?: ""

    val languages = listOf(
        "" to stringResource(R.string.language_system),
        "en" to stringResource(R.string.language_english),
        "zh" to stringResource(R.string.language_chinese)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.language_select_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                languages.forEach { (tag, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (tag == currentLanguageTag),
                                onClick = { onLanguageSelected(tag) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (tag == currentLanguageTag),
                            onClick = null
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun SortSelectionDialog(
    currentSortOrder: RepoSortOrder,
    onDismiss: () -> Unit,
    onSortOrderSelected: (RepoSortOrder) -> Unit
) {
    val options = listOf(
        RepoSortOrder.TIME to stringResource(R.string.sort_by_time),
        RepoSortOrder.NAME to stringResource(R.string.sort_by_name)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_repo_sort)) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { (order, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (order == currentSortOrder),
                                onClick = { onSortOrderSelected(order) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (order == currentSortOrder), onClick = null)
                        Text(text = label, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
