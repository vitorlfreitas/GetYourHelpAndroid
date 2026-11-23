package com.vitorfreitas.getyourhelp.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.vitorfreitas.getyourhelp.AppViewModel
import com.vitorfreitas.getyourhelp.ui.browse.BrowseScreen
import com.vitorfreitas.getyourhelp.ui.create.CreateTaskScreen
import com.vitorfreitas.getyourhelp.ui.mytasks.MyTasksScreen
import com.vitorfreitas.getyourhelp.ui.profile.ProfileScreen
import com.vitorfreitas.getyourhelp.ui.taskdetail.TaskDetailScreen
import com.vitorfreitas.getyourhelp.ui.wallet.WalletScreen

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * Connects each route to its screen. Screens only receive data and callbacks; this is the
 * one place that reads the ViewModel state and decides where each button navigates.
 */
@Composable
fun AppNavHost(vm: AppViewModel, navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = BrowseRoute,
        modifier = modifier,
        enterTransition = { fadeIn(tween(250)) },
        exitTransition = { fadeOut(tween(250)) }
    ) {
        composable<BrowseRoute> { entry ->
            val state by vm.state.collectAsStateWithLifecycle()
            val user = state.user ?: return@composable
            BrowseScreen(
                tasks = state.tasks,
                tasksLoaded = state.tasksLoaded,
                user = user,
                onOpenTask = { task -> if (entry.isResumed()) navController.navigate(TaskDetailRoute(task.id)) }
            )
        }
        composable<CreateTaskRoute> {
            val state by vm.state.collectAsStateWithLifecycle()
            CreateTaskScreen(busy = state.busy, onCreate = vm::createTask)
        }
        composable<MyTasksRoute> { entry ->
            val state by vm.state.collectAsStateWithLifecycle()
            val user = state.user ?: return@composable
            LaunchedEffect(Unit) { vm.refreshMyData() }
            MyTasksScreen(
                tasks = state.tasks,
                applications = state.myApplications,
                user = user,
                onOpenTask = { task -> if (entry.isResumed()) navController.navigate(TaskDetailRoute(task.id)) }
            )
        }
        composable<WalletRoute> {
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { vm.refreshMyData() }
            WalletScreen(payments = state.payments, busy = state.busy, onMarkPaid = vm::markPayment)
        }
        composable<ProfileRoute> {
            val state by vm.state.collectAsStateWithLifecycle()
            val user = state.user ?: return@composable
            ProfileScreen(
                user = user,
                busy = state.busy,
                onSaveCategories = vm::updateCategories,
                onSignOut = vm::signOut
            )
        }
        composable<TaskDetailRoute> { entry ->
            val taskId = entry.toRoute<TaskDetailRoute>().taskId
            val state by vm.state.collectAsStateWithLifecycle()
            val user = state.user ?: return@composable
            TaskDetailScreen(
                task = state.tasks.firstOrNull { it.id == taskId },
                tasksLoaded = state.tasksLoaded,
                user = user,
                // The list may still hold another task's applications for a moment, so keep only this task's.
                applications = state.selectedTaskApplications.filter { it.taskId == taskId },
                busy = state.busy,
                onBack = { if (entry.isResumed()) navController.navigateUp() },
                onLoadApplications = vm::loadApplications,
                onApply = vm::applyToTask,
                onAccept = vm::accept,
                onComplete = vm::complete,
                onReport = vm::report,
                onReview = vm::review
            )
        }
    }
}

/**
 * True when the screen is fully shown. Clicks are ignored during the transition between screens,
 * so a quick double tap cannot open the same screen twice or go back two screens.
 */
private fun NavBackStackEntry.isResumed() = lifecycle.currentState == Lifecycle.State.RESUMED
