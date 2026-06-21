package com.carocall.gitmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.carocall.gitmobile.data.SettingsManager
import com.carocall.gitmobile.ui.MainApp
import com.carocall.gitmobile.ui.screens.RepoSortOrder
import com.carocall.gitmobile.ui.theme.GitMobileTheme
import com.carocall.gitmobile.ui.theme.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsManager = SettingsManager(this)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsManager.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val sortOrder by settingsManager.repoSortOrderFlow.collectAsState(initial = RepoSortOrder.TIME)
            val globalGitName by settingsManager.globalGitNameFlow.collectAsState(initial = "")
            val globalGitEmail by settingsManager.globalGitEmailFlow.collectAsState(initial = "")
            val gitAccounts by settingsManager.gitAccountsFlow.collectAsState(initial = emptyList())
            val scope = rememberCoroutineScope()

            GitMobileTheme(themeMode = themeMode) {
                MainApp(
                    themeMode = themeMode,
                    onThemeChange = { mode ->
                        scope.launch { settingsManager.saveThemeMode(mode) }
                    },
                    sortOrder = sortOrder,
                    onSortOrderChange = { order ->
                        scope.launch { settingsManager.saveRepoSortOrder(order) }
                    },
                    globalGitName = globalGitName,
                    globalGitEmail = globalGitEmail,
                    onGlobalGitIdentityChange = { name, email ->
                        scope.launch { settingsManager.saveGlobalGitIdentity(name, email) }
                    },
                    gitAccounts = gitAccounts,
                    onSaveGitAccount = { account ->
                        scope.launch { settingsManager.saveGitAccount(account) }
                    },
                    onDeleteGitAccount = { accountId ->
                        scope.launch { settingsManager.deleteGitAccount(accountId) }
                    }
                )
            }
        }
    }
}