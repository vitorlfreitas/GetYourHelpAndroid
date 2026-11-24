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

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * The live task (for the names, and to close the chat once it is completed) and its messages,
 * oldest first. unsentText is a message that failed to send, put back in the text field.
 */
data class ChatState(
    val task: TaskItem? = null,
    val loading: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    val messagesLoaded: Boolean = false,
    val unsentText: String? = null
)

class ChatViewModel(
    private val repository: MarketplaceRepository,
    private val messenger: UserMessenger,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val taskId = savedStateHandle.toRoute<ChatRoute>().taskId
    private val userId = repository.currentUserId().orEmpty()
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
                    // Only the requester and the accepted helper may read the messages
                    if (task?.hasChatFor(userId) == true) listenToMessages()
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

    /** The message appears at once as "Sending…". If it fails, the text goes back into the text field. */
    fun send(sender: AppUser, text: String) {
        val task = state.value.task ?: return
        if (ChatValidator.message(text) != null) return
        viewModelScope.launch {
            val sent = messenger.attempt(errorPrefix = "Message not sent. ") { repository.sendMessage(task.id, sender, text) }
            if (!sent) _state.update { it.copy(unsentText = text) }
        }
    }

    fun unsentTextRestored() = _state.update { it.copy(unsentText = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer { appContainer().let { ChatViewModel(it.repository, it.messenger, createSavedStateHandle()) } }
        }
    }
}
