package com.vitorfreitas.getyourhelp.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vitorfreitas.getyourhelp.AppViewModel

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * The signed-in part of the app: the bottom navigation bar, the snackbar area and the screens.
 * The bar is shown on the main tabs and hidden on detail screens, which have a back arrow instead.
 */
@Composable
fun MainScaffold(vm: AppViewModel, snackbarHostState: SnackbarHostState) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // Before the first screen is set up the destination is null; that is the start tab (Browse).
    val currentTab = if (currentDestination == null) TopLevelDestination.BROWSE
    else TopLevelDestination.entries.firstOrNull { currentDestination.hasRoute(it.route::class) }
    val showBottomBar = currentTab != null

    Scaffold(
        // Each screen has its own top bar, which already leaves room for the status bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            // Without the bottom bar, keep snackbars above the system navigation bar.
            SnackbarHost(snackbarHostState, if (showBottomBar) Modifier else Modifier.navigationBarsPadding())
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { tab ->
                        val selected = tab == currentTab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(tab) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.unselectedIcon, contentDescription = null) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AppNavHost(
            appVm = vm,
            navController = navController,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
        )
    }
}

/**
 * Opens a tab the way bottom navigation should behave:
 * - only one copy of each tab is kept, and Back from any tab returns to Browse
 * - each tab keeps its state (scroll position, typed text) when you leave it and come back
 */
private fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
