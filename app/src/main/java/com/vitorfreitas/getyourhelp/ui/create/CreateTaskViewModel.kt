package com.vitorfreitas.getyourhelp.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vitorfreitas.getyourhelp.appContainer
import com.vitorfreitas.getyourhelp.data.AppUser
import com.vitorfreitas.getyourhelp.data.MarketplaceRepository
import com.vitorfreitas.getyourhelp.data.errorMessage
import com.vitorfreitas.getyourhelp.ui.UserMessenger
import com.vitorfreitas.getyourhelp.validation.TaskInput
import com.vitorfreitas.getyourhelp.validation.TaskValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * - busy: the task is being saved
 * - createdTaskId: set once the task is saved, so the screen can open it
 */
data class CreateTaskState(val busy: Boolean = false, val createdTaskId: String? = null)

/** Publishes a new task. The form fields themselves live in the screen (they survive rotation there). */
class CreateTaskViewModel(
    private val repository: MarketplaceRepository,
    private val messenger: UserMessenger
) : ViewModel() {
    private val _state = MutableStateFlow(CreateTaskState())
    val state = _state.asStateFlow()

    /** Validates the form once more (the button is not the only safeguard) and saves the task. */
    fun publish(owner: AppUser, input: TaskInput) {
        val validation = TaskValidator.validate(input)
        if (!validation.isValid || state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val taskId = repository.createTask(
                    owner = owner,
                    title = input.title,
                    description = input.description,
                    category = input.category,
                    taskType = input.type,
                    amount = validation.parsedAmount,
                    paymentMethods = input.paymentMethods.toList(),
                    location = input.location,
                    scheduledAt = input.scheduledAt
                )
                messenger.show("Task published. Helpers can now apply.")
                _state.update { it.copy(createdTaskId = taskId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messenger.showError("Could not publish the task. ${errorMessage(e)}")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                CreateTaskViewModel(container.repository, container.messenger)
            }
        }
    }
}
