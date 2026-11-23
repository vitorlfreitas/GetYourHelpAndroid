package com.vitorfreitas.getyourhelp.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.vitorfreitas.getyourhelp.AppViewModel
import com.vitorfreitas.getyourhelp.location.openInMaps
import com.vitorfreitas.getyourhelp.ui.browse.BrowseScreen
import com.vitorfreitas.getyourhelp.ui.chat.ChatScreen
import com.vitorfreitas.getyourhelp.ui.chat.ChatViewModel
import com.vitorfreitas.getyourhelp.ui.components.LoadingIndicator
import com.vitorfreitas.getyourhelp.ui.create.TaskFormMode
import com.vitorfreitas.getyourhelp.ui.create.TaskFormScreen
import com.vitorfreitas.getyourhelp.ui.create.TaskFormViewModel
import com.vitorfreitas.getyourhelp.ui.mytasks.MyTasksScreen
import com.vitorfreitas.getyourhelp.ui.profile.ProfileScreen
import com.vitorfreitas.getyourhelp.ui.taskdetail.TaskDetailActions
import com.vitorfreitas.getyourhelp.ui.taskdetail.TaskDetailScreen
import com.vitorfreitas.getyourhelp.ui.taskdetail.TaskDetailViewModel
import com.vitorfreitas.getyourhelp.ui.wallet.WalletScreen

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * Connects each route to its screen. Screens only receive data and callbacks; this is the one place
 * that reads the ViewModels and decides where each button navigates.
 * - [appVm] holds the data shared by all screens (session, tasks, applications, wallet, location)
 * - the task form, Task Details and Chat get their own ViewModel, created with `viewModel(factory = ...)`.
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
            val myLocation by appVm.myLocation.collectAsStateWithLifecycle()
            val user = state.user ?: return@composable
            BrowseScreen(
                tasks = state.tasks,
                tasksLoaded = state.tasksLoaded,
                user = user,
                myLocation = myLocation,
                locating = state.locating,
                onRefreshLocation = appVm::refreshLocation,
                onLocationDenied = appVm::locationPermissionDenied,
                onOpenTask = { task -> if (entry.isResumed()) navController.navigate(TaskDetailRoute(task.id)) },
                onCreateTask = { if (entry.isResumed()) navController.navigate(CreateTaskRoute) }
            )
        }
        composable<MyTasksRoute> { entry ->
            val state by appVm.state.collectAsStateWithLifecycle()
            val myLocation by appVm.myLocation.collectAsStateWithLifecycle()
            val user = state.user ?: return@composable
            MyTasksScreen(
                tasks = state.tasks,
                applications = state.myApplications,
                user = user,
                myLocation = myLocation,
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
            val formVm: TaskFormViewModel = viewModel(factory = TaskFormViewModel.CreateFactory)
            val state by formVm.state.collectAsStateWithLifecycle()
            // Once the task is saved, open it. The form is removed from the back stack, so Back
            // from the new task returns to where the user started instead of to an empty form.
            LaunchedEffect(state.savedTaskId) {
                state.savedTaskId?.let { taskId ->
                    navController.navigate(TaskDetailRoute(taskId)) {
                        popUpTo<CreateTaskRoute> { inclusive = true }
                    }
                }
            }
            TaskFormScreen(
                mode = TaskFormMode.CREATE,
                initial = state.initial ?: return@composable,
                paymentLocked = false,
                busy = state.busy,
                locating = state.locating,
                locationFill = state.locationFill,
                onUseCurrentLocation = formVm::useCurrentLocation,
                onLocationDenied = appVm::locationPermissionDenied,
                onSubmit = { input -> formVm.submit(user, input) },
                onBack = { if (entry.isResumed()) navController.navigateUp() }
            )
        }
        composable<EditTaskRoute> { entry ->
            val appState by appVm.state.collectAsStateWithLifecycle()
            val user = appState.user ?: return@composable
            val formVm: TaskFormViewModel = viewModel(factory = TaskFormViewModel.EditFactory)
            val state by formVm.state.collectAsStateWithLifecycle()
            // Back to the task once it is saved (the details page is live, so it shows the changes),
            // or straight away if the task could not be opened for editing.
            LaunchedEffect(state.savedTaskId, state.loadFailed) {
                if (state.savedTaskId != null || state.loadFailed) navController.navigateUp()
            }
            val initial = state.initial
            if (initial == null) {
                LoadingIndicator()
            } else {
                TaskFormScreen(
                    mode = TaskFormMode.EDIT,
                    initial = initial,
                    paymentLocked = state.paymentLocked,
                    busy = state.busy,
                    locating = state.locating,
                    locationFill = state.locationFill,
                    onUseCurrentLocation = formVm::useCurrentLocation,
                    onLocationDenied = appVm::locationPermissionDenied,
                    onSubmit = { input -> formVm.submit(user, input) },
                    onBack = { if (entry.isResumed()) navController.navigateUp() }
                )
            }
        }
        composable<TaskDetailRoute> { entry ->
            val appState by appVm.state.collectAsStateWithLifecycle()
            val myLocation by appVm.myLocation.collectAsStateWithLifecycle()
            val user = appState.user ?: return@composable
            val taskId = entry.toRoute<TaskDetailRoute>().taskId
            val detailVm: TaskDetailViewModel = viewModel(factory = TaskDetailViewModel.Factory)
            val state by detailVm.state.collectAsStateWithLifecycle()
            val context = LocalContext.current
            // For "1.2 km from you": read the position if the user already allowed location
            LaunchedEffect(Unit) { appVm.refreshLocationIfAllowed() }
            TaskDetailScreen(
                state = state,
                user = user,
                myLocation = myLocation,
                actions = TaskDetailActions(
                    onBack = { if (entry.isResumed()) navController.navigateUp() },
                    onApply = { method, message -> detailVm.applyForTask(user, method, message) },
                    onWithdraw = detailVm::withdraw,
                    onAccept = detailVm::accept,
                    onDecline = detailVm::decline,
                    onEdit = { if (entry.isResumed()) navController.navigate(EditTaskRoute(taskId)) },
                    onCancelTask = detailVm::cancelTask,
                    onMarkWorkDone = detailVm::markWorkDone,
                    onConfirmCompleted = { detailVm.confirmCompleted(user) },
                    onWorkNotFinished = detailVm::markWorkNotFinished,
                    onOpenChat = { if (entry.isResumed()) navController.navigate(ChatRoute(taskId)) },
                    onOpenMaps = { task ->
                        if (!openInMaps(context, task.location, task.coordinates)) appVm.showMessage("No app on this phone can open maps.")
                    },
                    onReport = { reason, details -> detailVm.report(user, reason, details) },
                    onReview = { rating, comment -> detailVm.review(user, rating, comment) }
                )
            )
        }
        composable<ChatRoute> { entry ->
            val appState by appVm.state.collectAsStateWithLifecycle()
            val user = appState.user ?: return@composable
            val chatVm: ChatViewModel = viewModel(factory = ChatViewModel.Factory)
            val state by chatVm.state.collectAsStateWithLifecycle()
            ChatScreen(
                state = state,
                user = user,
                onSend = { text -> chatVm.send(user, text) },
                onUnsentTextRestored = chatVm::unsentTextRestored,
                onBack = { if (entry.isResumed()) navController.navigateUp() }
            )
        }
    }
}

/**
 * True when the screen is fully shown. Clicks are ignored during the transition between screens,
 * so a quick double tap cannot open the same screen twice or go back two screens.
 */
private fun NavBackStackEntry.isResumed() = lifecycle.currentState == Lifecycle.State.RESUMED
