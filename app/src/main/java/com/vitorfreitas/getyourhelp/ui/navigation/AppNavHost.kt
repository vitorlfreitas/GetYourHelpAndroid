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
import com.vitorfreitas.getyourhelp.data.AppUser
import com.vitorfreitas.getyourhelp.location.openInMaps
import com.vitorfreitas.getyourhelp.ui.browse.BrowseScreen
import com.vitorfreitas.getyourhelp.ui.chat.ChatScreen
import com.vitorfreitas.getyourhelp.ui.chat.ChatViewModel
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
 * Connects each route to its screen: the one place that reads the ViewModels and decides where
 * each button goes. The task form, Task Details and Chat get their own ViewModel, which lives as
 * long as that screen is in the back stack.
 */
@Composable
fun AppNavHost(appVm: AppViewModel, user: AppUser, navController: NavHostController, modifier: Modifier = Modifier) {
    val appState by appVm.state.collectAsStateWithLifecycle()
    val myLocation by appVm.myLocation.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Taps during a screen transition are ignored, so a double tap cannot open a screen twice
    fun NavBackStackEntry.open(route: Any) {
        if (lifecycle.currentState == Lifecycle.State.RESUMED) navController.navigate(route)
    }
    fun NavBackStackEntry.back() {
        if (lifecycle.currentState == Lifecycle.State.RESUMED) navController.navigateUp()
    }

    NavHost(
        navController = navController,
        startDestination = BrowseRoute,
        modifier = modifier,
        enterTransition = { fadeIn(tween(250)) },
        exitTransition = { fadeOut(tween(250)) }
    ) {
        composable<BrowseRoute> { entry ->
            BrowseScreen(
                tasks = appState.tasks, tasksLoaded = appState.tasksLoaded, user = user, myLocation = myLocation,
                locating = appState.locating, onRefreshLocation = appVm::refreshLocation,
                onLocationDenied = appVm::locationPermissionDenied,
                onOpenTask = { entry.open(TaskDetailRoute(it.id)) }, onCreateTask = { entry.open(CreateTaskRoute) }
            )
        }
        composable<MyTasksRoute> { entry ->
            MyTasksScreen(
                tasks = appState.tasks, applications = appState.myApplications, user = user, myLocation = myLocation,
                onOpenTask = { entry.open(TaskDetailRoute(it.id)) }, onCreateTask = { entry.open(CreateTaskRoute) }
            )
        }
        composable<WalletRoute> {
            LaunchedEffect(Unit) { appVm.refreshPayments() }
            WalletScreen(payments = appState.payments, userId = user.uid, busy = appState.busy, onMarkPaid = appVm::markPayment)
        }
        composable<ProfileRoute> {
            ProfileScreen(user = user, busy = appState.busy, onSaveCategories = appVm::updateCategories, onSignOut = appVm::signOut)
        }
        composable<CreateTaskRoute> { entry ->
            val formVm: TaskFormViewModel = viewModel(factory = TaskFormViewModel.CreateFactory)
            val state by formVm.state.collectAsStateWithLifecycle()
            // Open the new task. The form leaves the back stack, so Back returns to where the user started.
            LaunchedEffect(state.savedTaskId) {
                state.savedTaskId?.let { navController.navigate(TaskDetailRoute(it)) { popUpTo<CreateTaskRoute> { inclusive = true } } }
            }
            TaskFormScreen(
                TaskFormMode.CREATE, state, onUseCurrentLocation = formVm::useCurrentLocation,
                onLocationDenied = appVm::locationPermissionDenied, onSubmit = { formVm.submit(user, it) }, onBack = { entry.back() }
            )
        }
        composable<EditTaskRoute> { entry ->
            val formVm: TaskFormViewModel = viewModel(factory = TaskFormViewModel.EditFactory)
            val state by formVm.state.collectAsStateWithLifecycle()
            // Back to the (live) task once saved, or straight away if it could not be opened
            LaunchedEffect(state.savedTaskId, state.loadFailed) {
                if (state.savedTaskId != null || state.loadFailed) navController.navigateUp()
            }
            TaskFormScreen(
                TaskFormMode.EDIT, state, onUseCurrentLocation = formVm::useCurrentLocation,
                onLocationDenied = appVm::locationPermissionDenied, onSubmit = { formVm.submit(user, it) }, onBack = { entry.back() }
            )
        }
        composable<TaskDetailRoute> { entry ->
            val taskId = entry.toRoute<TaskDetailRoute>().taskId
            val detailVm: TaskDetailViewModel = viewModel(factory = TaskDetailViewModel.Factory)
            val state by detailVm.state.collectAsStateWithLifecycle()
            // For "1.2 km from you": read the position if location is already allowed
            LaunchedEffect(Unit) { appVm.refreshLocationIfAllowed() }
            TaskDetailScreen(
                state = state,
                user = user,
                myLocation = myLocation,
                actions = TaskDetailActions(
                    onBack = { entry.back() },
                    onApply = { method, message -> detailVm.applyForTask(user, method, message) },
                    onWithdraw = detailVm::withdraw,
                    onAccept = detailVm::accept,
                    onDecline = detailVm::decline,
                    onEdit = { entry.open(EditTaskRoute(taskId)) },
                    onCancelTask = detailVm::cancelTask,
                    onMarkWorkDone = detailVm::markWorkDone,
                    onConfirmCompleted = detailVm::confirmCompleted,
                    onWorkNotFinished = detailVm::markWorkNotFinished,
                    onOpenChat = { entry.open(ChatRoute(taskId)) },
                    onOpenMaps = { task ->
                        if (!openInMaps(context, task.location, task.coordinates)) appVm.showMessage("No app on this phone can open maps.")
                    },
                    onReport = { reason, details -> detailVm.report(user, reason, details) },
                    onReview = { rating, comment -> detailVm.review(user, rating, comment) }
                )
            )
        }
        composable<ChatRoute> { entry ->
            val chatVm: ChatViewModel = viewModel(factory = ChatViewModel.Factory)
            val state by chatVm.state.collectAsStateWithLifecycle()
            ChatScreen(
                state = state, user = user, onSend = { chatVm.send(user, it) },
                onUnsentTextRestored = chatVm::unsentTextRestored, onBack = { entry.back() }
            )
        }
    }
}
