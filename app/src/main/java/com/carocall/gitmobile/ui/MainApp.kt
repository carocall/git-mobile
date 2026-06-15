package com.carocall.gitmobile.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.carocall.gitmobile.ui.screens.*
import com.carocall.gitmobile.ui.theme.ThemeMode

import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

// --- 导航与主入口 ---

@Composable
fun MainApp(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    sortOrder: RepoSortOrder,
    onSortOrderChange: (RepoSortOrder) -> Unit
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "repo_list",
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
        composable("repo_list") {
            RepoListScreen(
                sortOrder = sortOrder,
                onOpenRepo = { repo ->
                    val encodedPath = URLEncoder.encode(repo.absolutePath, "UTF-8")
                    navController.navigate("repo_explorer/$encodedPath")
                },
                onOpenSettings = {
                    navController.navigate("settings")
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                currentTheme = themeMode,
                onThemeChange = onThemeChange,
                currentSortOrder = sortOrder,
                onSortOrderChange = onSortOrderChange,
                navController = navController
            )
        }
        composable("repo_explorer/{repoRootPath}") { backStackEntry ->
            val path = URLDecoder.decode(backStackEntry.arguments?.getString("repoRootPath") ?: "", "UTF-8")
            RepoExplorerScreen(
                repoRoot = File(path),
                onBackToRepos = { navController.popBackStack() },
                onOpenFile = { file ->
                    val encodedPath = URLEncoder.encode(file.absolutePath, "UTF-8")
                    navController.navigate("editor/$encodedPath")
                },
                onGoToGit = { repoPath ->
                    val encodedPath = URLEncoder.encode(repoPath, "UTF-8")
                    navController.navigate("git_commit/$encodedPath")
                },
            )
        }
        composable("editor/{filePath}") { backStackEntry ->
            val path = URLDecoder.decode(backStackEntry.arguments?.getString("filePath") ?: "", "UTF-8")
            FileEditorScreen(file = File(path), onBack = { navController.popBackStack() })
        }
        composable("git_commit/{repoPath}") { backStackEntry ->
            val path = URLDecoder.decode(backStackEntry.arguments?.getString("repoPath") ?: "", "UTF-8")
            GitCommitScreen(
                repoRoot = File(path),
                onBack = { navController.popBackStack() },
                onGoToRemoteConfig = { repoPath ->
                    val encodedPath = URLEncoder.encode(repoPath, "UTF-8")
                    navController.navigate("remote_config/$encodedPath")
                }
            )
        }
        composable("remote_config/{repoPath}") { backStackEntry ->
            val path = URLDecoder.decode(backStackEntry.arguments?.getString("repoPath") ?: "", "UTF-8")
            RemoteConfigScreen(repoRoot = File(path), onBack = { navController.popBackStack() })
        }
    }
}
