package com.carocall.gitmobile.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.carocall.gitmobile.data.model.GitAccount
import com.carocall.gitmobile.data.model.LocalRepo
import com.carocall.gitmobile.data.model.RecentFile
import com.carocall.gitmobile.ui.screens.*
import com.carocall.gitmobile.ui.theme.ThemeMode
import com.carocall.gitmobile.ui.util.navigateSafe
import com.carocall.gitmobile.ui.util.popBackStackSafe

import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

// --- 导航与主入口 ---

@Composable
fun MainApp(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    sortOrder: RepoSortOrder,
    onSortOrderChange: (RepoSortOrder) -> Unit,
    globalGitName: String,
    globalGitEmail: String,
    onGlobalGitIdentityChange: (String, String) -> Unit,
    gitAccounts: List<GitAccount>,
    recentFiles: List<RecentFile>,
    localRepos: List<LocalRepo>,
    onSaveGitAccount: (GitAccount) -> Unit,
    onDeleteGitAccount: (String) -> Unit,
    onUpdateRepo: (LocalRepo) -> Unit,
    onDeleteRepo: (String) -> Unit
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "home",
        // 1. 进入新页面：从右侧滑入
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(400)
            )
        },
        // 2. 离开去新页面：向左侧滑出（可选：也可以保留原地不动或稍微位移）
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(400)
            )
        },
        // 3. 返回上一页：旧页面从左侧滑入
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(400)
            )
        },
        // 4. 当前页退出：向右侧滑出
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(400)
            )
        }
    ) {
        composable("home") {
            HomeScreen(
                localRepos = localRepos,
                gitAccounts = gitAccounts,
                recentFiles = recentFiles,
                globalGitName = globalGitName,
                globalGitEmail = globalGitEmail,
                onUpdateGlobalIdentity = onGlobalGitIdentityChange,
                onOpenRepo = { repo ->
                    onUpdateRepo(repo.copy(lastOpened = System.currentTimeMillis()))
                    val encodedPath = URLEncoder.encode(repo.path, "UTF-8")
                    navController.navigateSafe("repo_explorer/$encodedPath")
                },
                onOpenFile = { file ->
                    val encodedPath = URLEncoder.encode(file.absolutePath, "UTF-8")
                    navController.navigateSafe("editor/$encodedPath")
                },
                onOpenSettings = {
                    navController.navigateSafe("settings")
                },
                onManageAccounts = {
                    navController.navigateSafe("git_accounts")
                },
                onAddRepo = {
                    navController.navigateSafe("add_repo")
                },
                onViewAllRepos = {
                    navController.navigateSafe("repo_list")
                },
                onUpdateRepo = onUpdateRepo,
                onDeleteRepo = onDeleteRepo
            )
        }
        composable("repo_list") {
            RepoListScreen(
                repos = localRepos,
                onBack = { navController.popBackStackSafe() },
                onOpenRepo = { repo ->
                    onUpdateRepo(repo.copy(lastOpened = System.currentTimeMillis()))
                    val encodedPath = URLEncoder.encode(repo.path, "UTF-8")
                    navController.navigateSafe("repo_explorer/$encodedPath")
                },
                onUpdateRepo = onUpdateRepo,
                onDeleteRepo = onDeleteRepo
            )
        }
        composable("add_repo") {
            AddRepoScreen(
                gitAccounts = gitAccounts,
                onBack = { navController.popBackStackSafe() },
                onRepoCreated = { repoFile ->
                    onUpdateRepo(LocalRepo(path = repoFile.absolutePath, name = repoFile.name))
                    navController.popBackStackSafe()
                },
                onManageAccounts = {
                    navController.navigateSafe("git_accounts")
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                currentTheme = themeMode,
                onThemeChange = onThemeChange,
                currentSortOrder = sortOrder,
                onSortOrderChange = onSortOrderChange,
                globalGitName = globalGitName,
                globalGitEmail = globalGitEmail,
                onGlobalGitIdentityChange = onGlobalGitIdentityChange,
                gitAccounts = gitAccounts,
                navController = navController
            )
        }
        composable("git_accounts") {
            GitAccountsScreen(
                accounts = gitAccounts,
                onSaveAccount = onSaveGitAccount,
                onDeleteAccount = onDeleteGitAccount,
                onBack = { navController.popBackStackSafe() }
            )
        }
        composable("repo_explorer/{repoRootPath}") { backStackEntry ->
            val path = URLDecoder.decode(backStackEntry.arguments?.getString("repoRootPath") ?: "", "UTF-8")
            RepoExplorerScreen(
                repoRoot = File(path),
                onBackToRepos = { navController.popBackStackSafe() },
                onOpenFile = { file ->
                    val encodedPath = URLEncoder.encode(file.absolutePath, "UTF-8")
                    navController.navigateSafe("editor/$encodedPath")
                },
                onGoToGit = { repoPath ->
                    val encodedPath = URLEncoder.encode(repoPath, "UTF-8")
                    navController.navigateSafe("git_commit/$encodedPath")
                },
            )
        }
        composable("editor/{filePath}") { backStackEntry ->
            val path = URLDecoder.decode(backStackEntry.arguments?.getString("filePath") ?: "", "UTF-8")
            FileEditorScreen(file = File(path), onBack = { navController.popBackStackSafe() })
        }
        composable("git_commit/{repoPath}") { backStackEntry ->
            val path = URLDecoder.decode(backStackEntry.arguments?.getString("repoPath") ?: "", "UTF-8")
            GitCommitScreen(
                repoRoot = File(path),
                globalGitName = globalGitName,
                globalGitEmail = globalGitEmail,
                gitAccounts = gitAccounts,
                onBack = { navController.popBackStackSafe() },
                onGoToRemoteConfig = { repoPath ->
                    val encodedPath = URLEncoder.encode(repoPath, "UTF-8")
                    navController.navigateSafe("remote_config/$encodedPath")
                },
                onGoToBranchManagement = { repoPath ->
                    val encodedPath = URLEncoder.encode(repoPath, "UTF-8")
                    navController.navigateSafe("branch_management/$encodedPath")
                },
                onViewCommit = { repoPath, commitId ->
                    val encodedPath = URLEncoder.encode(repoPath, "UTF-8")
                    navController.navigateSafe("commit_detail/$encodedPath/$commitId")
                },
                onViewDiff = { repoPath, commitId, filePath ->
                    val encodedPath = URLEncoder.encode(repoPath, "UTF-8")
                    val encodedFile = URLEncoder.encode(filePath, "UTF-8")
                    navController.navigateSafe("diff/$encodedPath/$commitId/$encodedFile")
                }
            )
        }
        composable("commit_detail/{repoPath}/{commitId}") { backStackEntry ->
            val repoPath = URLDecoder.decode(backStackEntry.arguments?.getString("repoPath") ?: "", "UTF-8")
            val commitId = backStackEntry.arguments?.getString("commitId") ?: ""
            CommitDetailScreen(
                repoRoot = File(repoPath),
                commitId = commitId,
                onBack = { navController.popBackStackSafe() },
                onViewDiff = { filePath ->
                    val encodedPath = URLEncoder.encode(repoPath, "UTF-8")
                    val encodedFile = URLEncoder.encode(filePath, "UTF-8")
                    navController.navigateSafe("diff/$encodedPath/$commitId/$encodedFile")
                }
            )
        }
        composable("diff/{repoPath}/{commitId}/{filePath}") { backStackEntry ->
            val repoPath = URLDecoder.decode(backStackEntry.arguments?.getString("repoPath") ?: "", "UTF-8")
            val commitId = backStackEntry.arguments?.getString("commitId") ?: ""
            val filePath = URLDecoder.decode(backStackEntry.arguments?.getString("filePath") ?: "", "UTF-8")
            DiffScreen(
                repoRoot = File(repoPath),
                commitId = commitId,
                filePath = filePath,
                onBack = { navController.popBackStackSafe() }
            )
        }
        composable("remote_config/{repoPath}") { backStackEntry ->
            val path = URLDecoder.decode(backStackEntry.arguments?.getString("repoPath") ?: "", "UTF-8")
            RemoteConfigScreen(
                repoRoot = File(path),
                gitAccounts = gitAccounts,
                onManageAccounts = {
                    navController.navigateSafe("git_accounts")
                },
                onBack = { navController.popBackStackSafe() }
            )
        }
        composable("branch_management/{repoPath}") { backStackEntry ->
            val path = URLDecoder.decode(backStackEntry.arguments?.getString("repoPath") ?: "", "UTF-8")
            BranchManagementScreen(
                repoRoot = File(path),
                gitAccounts = gitAccounts,
                onBack = { navController.popBackStackSafe() }
            )
        }
    }
}
