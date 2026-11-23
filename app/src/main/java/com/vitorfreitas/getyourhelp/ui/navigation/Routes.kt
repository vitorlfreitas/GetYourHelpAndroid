package com.vitorfreitas.getyourhelp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

// Every screen the app can navigate to (type-safe navigation).
// A route is a class instead of a string, so the compiler checks destinations and their arguments.

@Serializable data object BrowseRoute
@Serializable data object MyTasksRoute
@Serializable data object WalletRoute
@Serializable data object ProfileRoute

/** The "Post a task" form, opened from the button on Browse (or from an empty My Tasks list). */
@Serializable data object CreateTaskRoute

/** Details of one task. Only the id is passed; the screen loads the live task itself. */
@Serializable data class TaskDetailRoute(val taskId: String)

/** The "Post a task" form filled in with an open task, so its owner can change it. */
@Serializable data class EditTaskRoute(val taskId: String)

/** The chat between a task's requester and its accepted helper. */
@Serializable data class ChatRoute(val taskId: String)

/** The tabs of the bottom navigation bar, in display order. The icon is filled when the tab is selected. */
enum class TopLevelDestination(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    BROWSE(BrowseRoute, "Browse", Icons.Filled.Home, Icons.Outlined.Home),
    MY_TASKS(MyTasksRoute, "My Tasks", Icons.Filled.Work, Icons.Outlined.Work),
    WALLET(WalletRoute, "Wallet", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    PROFILE(ProfileRoute, "Profile", Icons.Filled.Person, Icons.Outlined.Person)
}
