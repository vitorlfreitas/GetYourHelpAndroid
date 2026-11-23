package com.vitorfreitas.getyourhelp.ui.taskdetail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.vitorfreitas.getyourhelp.appContainer
import com.vitorfreitas.getyourhelp.data.AppUser
import com.vitorfreitas.getyourhelp.data.MarketplaceRepository
import com.vitorfreitas.getyourhelp.data.PaymentMethod
import com.vitorfreitas.getyourhelp.data.TaskApplication
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.data.errorMessage
import com.vitorfreitas.getyourhelp.ui.UserMessenger
import com.vitorfreitas.getyourhelp.ui.navigation.TaskDetailRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * Everything the task details page shows.
 * - task: the latest version of the task (null while loading, or if it no longer exists)
 * - applications: everyone who applied (only loaded for the task owner)
 * - myApplication: the signed-in helper's own application for this task, if any
 * - hasReported: this user already reported the task (each user can report a task once)
 * - busy: an action is running, so its buttons are disabled
 */
data class TaskDetailState(
    val task: TaskItem? = null,
    val loading: Boolean = true,
    val applications: List<TaskApplication> = emptyList(),
    val myApplication: TaskApplication? = null,
    val hasReported: Boolean = false,
    val busy: Boolean = false
)

/**
 * ViewModel of one task details page. All its data is live: when the other person accepts, declines,
 * applies or marks the work as done on their phone, this page updates by itself. The task id comes
 * from the navigation route.
 */
class TaskDetailViewModel(
    private val repository: MarketplaceRepository,
    private val messenger: UserMessenger,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val taskId: String = savedStateHandle.toRoute<TaskDetailRoute>().taskId
    private val userId: String = repository.currentUserId().orEmpty()
    private val _state = MutableStateFlow(TaskDetailState())
    val state = _state.asStateFlow()
    private var applicationsJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeTask(taskId)
                .catch { e ->
                    _state.update { it.copy(loading = false) }
                    messenger.showError("Could not load the task. ${errorMessage(e)}")
                }
                .collect { task ->
                    _state.update { it.copy(task = task, loading = false) }
                    if (task != null && task.ownerId == userId) listenToApplications()
                }
        }
        if (userId.isNotEmpty()) {
            viewModelScope.launch {
                repository.observeMyApplications(userId)
                    .catch { e -> messenger.showError("Could not load your application. ${errorMessage(e)}") }
                    .collect { applications ->
                        // The list is newest first, so this is the latest application for this task
                        _state.update { it.copy(myApplication = applications.firstOrNull { app -> app.taskId == taskId }) }
                    }
            }
            viewModelScope.launch {
                try {
                    val reported = repository.hasReported(taskId, userId)
                    _state.update { it.copy(hasReported = reported) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Not critical: the report button stays visible, and the server still refuses a second report.
                    Log.w("TaskDetail", "Could not check for an earlier report", e)
                }
            }
        }
    }

    /** Only the owner may read everyone's applications, so this starts once we know the user owns the task. */
    private fun listenToApplications() {
        if (applicationsJob != null) return
        applicationsJob = viewModelScope.launch {
            repository.observeApplicationsForTask(taskId)
                .catch { e -> messenger.showError("Could not load the applications. ${errorMessage(e)}") }
                .collect { applications -> _state.update { it.copy(applications = applications) } }
        }
    }

    // ---------------------------------------------------------------- Helper actions

    fun applyForTask(helper: AppUser, method: PaymentMethod?, message: String) =
        runAction("Application sent. You'll see the answer here and in My Tasks.") {
            repository.applyToTask(requireTask(), helper, method, message)
        }

    fun withdraw() = runAction("Application withdrawn.") {
        repository.withdrawApplication(state.value.myApplication ?: error("You have not applied for this task."))
    }

    /** The accepted helper has finished the work. The requester is then asked to confirm it. */
    fun markWorkDone() {
        val task = state.value.task ?: return
        runAction("Marked as done. ${task.ownerName.substringBefore(' ')} will confirm it.") {
            repository.markWorkDone(requireTask())
        }
    }

    // ---------------------------------------------------------------- Owner actions

    fun accept(application: TaskApplication) = runAction("${application.helperName} is now helping with this task.") {
        repository.acceptApplication(requireTask(), application)
    }

    fun decline(application: TaskApplication) = runAction("Application declined.") {
        repository.declineApplication(requireTask(), application)
    }

    fun cancelTask() = runAction("Task cancelled. It no longer appears in Browse.") {
        repository.cancelTask(requireTask())
    }

    /** The requester confirms the work the helper marked as done. */
    fun confirmCompleted(owner: AppUser) = runAction("Task completed. You can now review each other.") {
        repository.completeTask(requireTask(), owner)
    }

    /** The requester says the work isn't finished, so the task goes back to the helper. */
    fun markWorkNotFinished() = runAction("The helper can now finish the work and mark it as done again.") {
        repository.markWorkNotFinished(requireTask())
    }

    // ---------------------------------------------------------------- Both

    fun report(user: AppUser, reason: String, details: String) = runAction("Report sent. Thank you, we'll review it.") {
        repository.reportTask(requireTask(), user, reason, details)
        _state.update { it.copy(hasReported = true) }
    }

    fun review(user: AppUser, rating: Int, comment: String) = runAction("Review submitted. Thank you!") {
        repository.submitReview(requireTask(), user, rating, comment)
    }

    private fun requireTask(): TaskItem = state.value.task ?: error("This task is no longer available.")

    /** Runs an action, disabling buttons meanwhile, then shows [successMessage] or the error. */
    private fun runAction(successMessage: String, action: suspend () -> Unit) {
        if (state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                action()
                messenger.show(successMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messenger.showError(errorMessage(e))
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                TaskDetailViewModel(container.repository, container.messenger, createSavedStateHandle())
            }
        }
    }
}
