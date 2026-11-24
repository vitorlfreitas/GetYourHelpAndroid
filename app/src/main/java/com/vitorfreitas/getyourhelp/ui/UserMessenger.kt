package com.vitorfreitas.getyourhelp.ui

import com.vitorfreitas.getyourhelp.data.errorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** A short message for the user. Errors stay on screen longer. */
data class UserMessage(val id: Long, val text: String, val isError: Boolean)

/**
 * A queue of messages any ViewModel can add to. The root of the UI shows them one at a time as
 * snackbars, so a message stays visible even if the screen that sent it has closed.
 */
class UserMessenger {
    private val nextId = AtomicLong()
    private val _messages = MutableStateFlow<List<UserMessage>>(emptyList())
    val messages: StateFlow<List<UserMessage>> = _messages.asStateFlow()

    fun show(text: String) = add(text, isError = false)

    fun showError(text: String) = add(text, isError = true)

    /** Removes a message once it has been shown or dismissed. */
    fun dismiss(id: Long) = _messages.update { list -> list.filterNot { it.id == id } }

    /**
     * Runs [action] and tells the user how it went: [success] if it worked, otherwise a friendly
     * error after [errorPrefix]. Returns true if it worked.
     */
    suspend fun attempt(success: String? = null, errorPrefix: String = "", action: suspend () -> Unit): Boolean =
        try {
            action()
            success?.let(::show)
            true
        } catch (e: CancellationException) {
            throw e // the screen was closed: not an error
        } catch (e: Exception) {
            showError(errorPrefix + errorMessage(e))
            false
        }

    private fun add(text: String, isError: Boolean) =
        _messages.update { it + UserMessage(nextId.getAndIncrement(), text, isError) }
}
