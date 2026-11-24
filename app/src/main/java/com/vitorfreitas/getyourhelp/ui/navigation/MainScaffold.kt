package com.vitorfreitas.getyourhelp.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vitorfreitas.getyourhelp.AppViewModel
import com.vitorfreitas.getyourhelp.data.AppUser

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * The signed-in app: the bottom navigation bar (only on the main tabs; other screens have a back
 * arrow), the snackbar area and the screens.
 */
@Composable
fun MainScaffold(vm: AppViewModel, user: AppUser, snackbarHostState: SnackbarHostState) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // Before the first screen is set up the destination is null: that is the start tab (Browse)
    val currentTab = if (currentDestination == null) TopLevelDestination.BROWSE
    else TopLevelDestination.entries.firstOrNull { currentDestination.hasRoute(it.route::class) }
    val showBottomBar = currentTab != null
    val onChat = currentDestination?.hasRoute(ChatRoute::class) == true

    Scaffold(
        // Each screen has its own top bar, which already leaves room for the status bar
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            // Keep snackbars above the system bar and the keyboard (and in the chat, above the message field)
            SnackbarHost(
                snackbarHostState,
                when {
                    showBottomBar -> Modifier
                    onChat -> Modifier.navigationBarsPadding().imePadding().padding(bottom = 72.dp)
                    else -> Modifier.navigationBarsPadding().imePadding()
                }
            )
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
        AppNavHost(vm, user, navController, Modifier.padding(innerPadding).consumeWindowInsets(innerPadding))
    }
}

/**
 * Opens a tab the usual bottom-navigation way: one copy of each tab, Back returns to Browse, and
 * each tab keeps its scroll position and typed text.
 */
private fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
