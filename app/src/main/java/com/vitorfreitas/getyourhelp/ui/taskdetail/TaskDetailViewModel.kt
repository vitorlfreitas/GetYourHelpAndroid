package com.vitorfreitas.getyourhelp.ui.taskdetail

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
 * What the task details page shows. applications are only loaded for the task owner, and
 * myApplication is the signed-in helper's own application for this task.
 */
data class TaskDetailState(
    val task: TaskItem? = null,
    val loading: Boolean = true,
    val applications: List<TaskApplication> = emptyList(),
    val myApplication: TaskApplication? = null,
    val hasReported: Boolean = false,
    val busy: Boolean = false
)

/** One task's page. All data is live: changes made on the other person's phone appear by themselves. */
class TaskDetailViewModel(
    private val repository: MarketplaceRepository,
    private val messenger: UserMessenger,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val taskId = savedStateHandle.toRoute<TaskDetailRoute>().taskId
    private val userId = repository.currentUserId().orEmpty()
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
                    if (task?.ownerId == userId) listenToApplications()
                }
        }
        viewModelScope.launch {
            repository.observeMyApplications(userId)
                .catch { e -> messenger.showError("Could not load your application. ${errorMessage(e)}") }
                // The list is newest first, so this is the latest application for this task
                .collect { list -> _state.update { it.copy(myApplication = list.firstOrNull { app -> app.taskId == taskId }) } }
        }
        viewModelScope.launch {
            // Not critical: if this fails, the button stays and the server still refuses a second report
            val reported = try {
                repository.hasReported(taskId, userId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            _state.update { it.copy(hasReported = reported) }
        }
    }

    /** Only the owner may read everyone's applications. */
    private fun listenToApplications() {
        if (applicationsJob != null) return
        applicationsJob = viewModelScope.launch {
            repository.observeApplicationsForTask(taskId)
                .catch { e -> messenger.showError("Could not load the applications. ${errorMessage(e)}") }
                .collect { list -> _state.update { it.copy(applications = list) } }
        }
    }

    // Helper actions

    fun applyForTask(helper: AppUser, method: PaymentMethod?, message: String) =
        runAction("Application sent. You'll see the answer here and in My Tasks.") { repository.applyToTask(it, helper, method, message) }

    fun withdraw() = runAction("Application withdrawn.") {
        repository.withdrawApplication(state.value.myApplication ?: return@runAction)
    }

    fun markWorkDone() = runAction("Marked as done. The requester will confirm it.") { repository.markWorkDone(it) }

    // Owner actions

    fun accept(application: TaskApplication) =
        runAction("${application.helperName} is now helping with this task.") { repository.acceptApplication(it, application) }

    fun decline(application: TaskApplication) = runAction("Application declined.") { repository.declineApplication(application) }

    fun cancelTask() = runAction("Task cancelled. It no longer appears in Browse.") { repository.cancelTask(it) }

    fun confirmCompleted() = runAction("Task completed. You can now review each other.") { repository.completeTask(it) }

    fun markWorkNotFinished() =
        runAction("The helper can now finish the work and mark it as done again.") { repository.markWorkNotFinished(it) }

    // Both

    fun report(user: AppUser, reason: String, details: String) = runAction("Report sent. Thank you, we'll review it.") {
        repository.reportTask(it, user, reason, details)
        _state.update { state -> state.copy(hasReported = true) }
    }

    fun review(user: AppUser, rating: Int, comment: String) =
        runAction("Review submitted. Thank you!") { repository.submitReview(it, user, rating, comment) }

    /** Runs an action on the current task with the buttons disabled, then shows [successMessage] or the error. */
    private fun runAction(successMessage: String, action: suspend (TaskItem) -> Unit) {
        val task = state.value.task ?: return
        if (state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            messenger.attempt(successMessage) { action(task) }
            _state.update { it.copy(busy = false) }
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
