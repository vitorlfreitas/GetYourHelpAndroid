package com.vitorfreitas.getyourhelp.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.vitorfreitas.getyourhelp.appContainer
import com.vitorfreitas.getyourhelp.data.AppUser
import com.vitorfreitas.getyourhelp.data.ChatMessage
import com.vitorfreitas.getyourhelp.data.MarketplaceRepository
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.data.errorMessage
import com.vitorfreitas.getyourhelp.data.hasChatFor
import com.vitorfreitas.getyourhelp.ui.UserMessenger
import com.vitorfreitas.getyourhelp.ui.navigation.ChatRoute
import com.vitorfreitas.getyourhelp.validation.ChatValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * - task: the live task (for the other person's name, and to close the chat when the task is completed)
 * - messages / messagesLoaded: the live messages, oldest first
 * - unsentText: a message that failed to send, put back in the text field so it is not lost
 */
data class ChatState(
    val task: TaskItem? = null,
    val loading: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    val messagesLoaded: Boolean = false,
    val unsentText: String? = null
)

/** ViewModel of a task's chat. The task id comes from the navigation route. */
class ChatViewModel(
    private val repository: MarketplaceRepository,
    private val messenger: UserMessenger,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val taskId: String = savedStateHandle.toRoute<ChatRoute>().taskId
    private val userId: String = repository.currentUserId().orEmpty()
    private val _state = MutableStateFlow(ChatState())
    val state = _state.asStateFlow()
    private var messagesJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeTask(taskId)
                .catch { e ->
                    _state.update { it.copy(loading = false) }
                    messenger.showError("Could not load the task. ${errorMessage(e)}")
                }
                .collect { task ->
                    _state.update { it.copy(task = task, loading = false) }
                    // The security rules only let the requester and the accepted helper read the messages
                    if (task != null && task.hasChatFor(userId)) listenToMessages()
                }
        }
    }

    private fun listenToMessages() {
        if (messagesJob != null) return
        messagesJob = viewModelScope.launch {
            repository.observeMessages(taskId)
                .catch { e ->
                    _state.update { it.copy(messagesLoaded = true) }
                    messenger.showError("Could not load the messages. ${errorMessage(e)}")
                }
                .collect { messages -> _state.update { it.copy(messages = messages, messagesLoaded = true) } }
        }
    }

    /**
     * Sends a message. It shows up in the list straight away (as "Sending…") while Firestore
     * delivers it. If it fails, the text goes back into the text field.
     */
    fun send(sender: AppUser, text: String) {
        val task = state.value.task ?: return
        if (ChatValidator.message(text) != null) return
        viewModelScope.launch {
            try {
                repository.sendMessage(task.id, sender, text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(unsentText = text) }
                messenger.showError("Message not sent. ${errorMessage(e)}")
            }
        }
    }

    /** The screen has put the unsent text back in the text field. */
    fun unsentTextRestored() = _state.update { it.copy(unsentText = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ChatViewModel(container.repository, container.messenger, createSavedStateHandle())
            }
        }
    }
}
