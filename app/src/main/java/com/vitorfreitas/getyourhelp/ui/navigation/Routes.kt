package com.vitorfreitas.getyourhelp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

// Every screen the app can navigate to (type-safe navigation).
// A route is a class instead of a string, so the compiler checks destinations and their arguments.

@Serializable data object BrowseRoute
@Serializable data object CreateTaskRoute
@Serializable data object MyTasksRoute
@Serializable data object WalletRoute
@Serializable data object ProfileRoute

/** Details of one task. Only the id is passed; the screen reads the latest task data from the ViewModel. */
@Serializable data class TaskDetailRoute(val taskId: String)

/** The tabs of the bottom navigation bar, in display order. */
enum class TopLevelDestination(val route: Any, val label: String, val icon: ImageVector) {
    BROWSE(BrowseRoute, "Browse", Icons.Default.Home),
    CREATE(CreateTaskRoute, "Create", Icons.Default.Add),
    MY_TASKS(MyTasksRoute, "My Tasks", Icons.Default.Work),
    WALLET(WalletRoute, "Wallet", Icons.Default.AccountBalanceWallet),
    PROFILE(ProfileRoute, "Profile", Icons.Default.Person)
}
