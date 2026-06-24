package com.carocall.gitmobile.ui.util

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

/**
 * Safe navigation to prevent multiple rapid clicks resulting in multiple screen opens or white screens.
 * Only navigates if the current back stack entry is in the RESUMED state.
 */
fun NavController.navigateSafe(route: String, builder: NavOptionsBuilder.() -> Unit = {}) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        navigate(route, builder)
    }
}

/**
 * Safe back navigation to prevent multiple rapid clicks resulting in popping too many screens.
 */
fun NavController.popBackStackSafe() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}
