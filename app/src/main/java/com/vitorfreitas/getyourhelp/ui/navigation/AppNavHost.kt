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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vitorfreitas.getyourhelp.AppViewModel
import com.vitorfreitas.getyourhelp.ui.browse.BrowseScreen
import com.vitorfreitas.getyourhelp.ui.create.CreateTaskScreen
import com.vitorfreitas.getyourhelp.ui.create.CreateTaskViewModel
import com.vitorfreitas.getyourhelp.ui.mytasks.MyTasksScreen
import com.vitorfreitas.getyourhelp.ui.profile.ProfileScreen
import com.vitorfreitas.getyourhelp.ui.taskdetail.TaskDetailScreen
import com.vitorfreitas.getyourhelp.ui.taskdetail.TaskDetailViewModel
import com.vitorfreitas.getyourhelp.ui.wallet.WalletScreen

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * Connects each route to its screen. Screens only receive data and callbacks; this is the one place
 * that reads the ViewModels and decides where each button navigates.
 * - [appVm] holds the data shared by all screens (session, tasks, applications, wallet)
 * - Create Task and Task Details get their own ViewModel, created with `viewModel(factory = ...)`.
 *   It lives as long as that screen is in the back stack and is cleared when the screen is closed.
 */
@Composable
fun AppNavHost(appVm: AppViewModel, navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = BrowseRoute,
        modifier = modifier,
        enterTransition = { fadeIn(tween(250)) },
        exitTransition = { fadeOut(tween(250)) }
    ) {
        composable<BrowseRoute> { entry ->
            val state by appVm.state.collectAsStateWithLifecycle()
            val user = state.user ?: return@composable
            BrowseScreen(
                tasks = state.tasks,
                tasksLoaded = state.tasksLoaded,
                user = user,
                onOpenTask = { task -> if (entry.isResumed()) navController.navigate(TaskDetailRoute(task.id)) },
                onCreateTask = { if (entry.isResumed()) navController.navigate(CreateTaskRoute) }
            )
        }
        composable<MyTasksRoute> { entry ->
            val state by appVm.state.collectAsStateWithLifecycle()
            val user = state.user ?: return@composable
            MyTasksScreen(
                tasks = state.tasks,
                applications = state.myApplications,
                user = user,
                onOpenTask = { task -> if (entry.isResumed()) navController.navigate(TaskDetailRoute(task.id)) },
                onCreateTask = { if (entry.isResumed()) navController.navigate(CreateTaskRoute) }
            )
        }
        composable<WalletRoute> {
            val state by appVm.state.collectAsStateWithLifecycle()
            val user = state.user ?: return@composable
            LaunchedEffect(Unit) { appVm.refreshPayments() }
            WalletScreen(payments = state.payments, userId = user.uid, busy = state.busy, onMarkPaid = appVm::markPayment)
        }
        composable<ProfileRoute> {
            val state by appVm.state.collectAsStateWithLifecycle()
            val user = state.user ?: return@composable
            ProfileScreen(
                user = user,
                busy = state.busy,
                onSaveCategories = appVm::updateCategories,
                onSignOut = appVm::signOut
            )
        }
        composable<CreateTaskRoute> { entry ->
            val appState by appVm.state.collectAsStateWithLifecycle()
            val user = appState.user ?: return@composable
            val createVm: CreateTaskViewModel = viewModel(factory = CreateTaskViewModel.Factory)
            val state by createVm.state.collectAsStateWithLifecycle()
            // Once the task is saved, open it. The form is removed from the back stack, so Back
            // from the new task returns to where the user started instead of to an empty form.
            LaunchedEffect(state.createdTaskId) {
                state.createdTaskId?.let { taskId ->
                    navController.navigate(TaskDetailRoute(taskId)) {
                        popUpTo<CreateTaskRoute> { inclusive = true }
                    }
                }
            }
            CreateTaskScreen(
                busy = state.busy,
                onPublish = { input -> createVm.publish(user, input) },
                onBack = { if (entry.isResumed()) navController.navigateUp() }
            )
        }
        composable<TaskDetailRoute> { entry ->
            val appState by appVm.state.collectAsStateWithLifecycle()
            val user = appState.user ?: return@composable
            val detailVm: TaskDetailViewModel = viewModel(factory = TaskDetailViewModel.Factory)
            val state by detailVm.state.collectAsStateWithLifecycle()
            TaskDetailScreen(
                state = state,
                user = user,
                onBack = { if (entry.isResumed()) navController.navigateUp() },
                onApply = { method, message -> detailVm.applyForTask(user, method, message) },
                onWithdraw = detailVm::withdraw,
                onAccept = detailVm::accept,
                onDecline = detailVm::decline,
                onComplete = { detailVm.complete(user) },
                onReport = { reason, details -> detailVm.report(user, reason, details) },
                onReview = { rating, comment -> detailVm.review(user, rating, comment) }
            )
        }
    }
}

/**
 * True when the screen is fully shown. Clicks are ignored during the transition between screens,
 * so a quick double tap cannot open the same screen twice or go back two screens.
 */
private fun NavBackStackEntry.isResumed() = lifecycle.currentState == Lifecycle.State.RESUMED
