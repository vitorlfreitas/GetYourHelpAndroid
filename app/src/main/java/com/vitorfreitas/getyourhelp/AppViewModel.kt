package com.vitorfreitas.getyourhelp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vitorfreitas.getyourhelp.data.AppUser
import com.vitorfreitas.getyourhelp.data.MarketplaceRepository
import com.vitorfreitas.getyourhelp.data.PaymentRecord
import com.vitorfreitas.getyourhelp.data.TaskApplication
import com.vitorfreitas.getyourhelp.data.TaskCategory
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.data.authErrorMessage
import com.vitorfreitas.getyourhelp.data.errorMessage
import com.vitorfreitas.getyourhelp.ui.UserMessage
import com.vitorfreitas.getyourhelp.ui.UserMessenger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * The data shared by the whole signed-in app, in one immutable object.
 * - user: null means signed out, so the login screen is shown
 * - checkingSession: true at start-up while the app checks whether someone is already signed in
 * - tasks / tasksLoaded: live list of all tasks; tasksLoaded is false until the first list arrives
 * - myApplications: live list of the applications this user has sent as a helper
 * - payments: the user's payment records (loaded when the Wallet opens)
 * - busy: an action is running, so its buttons are disabled
 * - authError: why the last sign-in or registration failed, shown under the login form
 */
data class MarketplaceState(
    val user: AppUser? = null,
    val checkingSession: Boolean = false,
    val tasks: List<TaskItem> = emptyList(),
    val tasksLoaded: Boolean = false,
    val myApplications: List<TaskApplication> = emptyList(),
    val payments: List<PaymentRecord> = emptyList(),
    val busy: Boolean = false,
    val authError: String? = null,
)

/**
 * Connects the screens to [MarketplaceRepository] (MVVM) for everything that is shared between screens:
 * the session, the live task list, the user's applications, the wallet and the profile.
 * Screens with their own logic have their own ViewModel (CreateTaskViewModel, TaskDetailViewModel).
 * This ViewModel belongs to the activity and survives screen rotation.
 */
class AppViewModel(
    private val repository: MarketplaceRepository,
    private val messenger: UserMessenger,
) : ViewModel() {
    private val _state = MutableStateFlow(MarketplaceState(checkingSession = true))
    val state = _state.asStateFlow()

    /** Messages waiting to be shown as snackbars (see [UserMessenger]). */
    val messages: StateFlow<List<UserMessage>> = messenger.messages

    private var listenersJob: Job? = null

    // On app start, restore the session if Firebase still has a signed-in user.
    init {
        viewModelScope.launch {
            try {
                val current = repository.currentUser()
                _state.update { it.copy(user = current, checkingSession = false) }
                if (current != null) startListening(current)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(checkingSession = false, authError = "Could not restore your session. Please sign in.") }
            }
        }
    }

    /**
     * Starts (or restarts) the live lists that stay open while someone is signed in:
     * all tasks, and the applications this user has sent.
     */
    private fun startListening(user: AppUser) {
        listenersJob?.cancel()
        listenersJob =
            viewModelScope.launch {
                launch {
                    repository
                        .observeTasks()
                        .catch { e ->
                            _state.update { it.copy(tasksLoaded = true) }
                            messenger.showError("Could not load tasks. ${errorMessage(e)}")
                        }.collect { tasks -> _state.update { it.copy(tasks = tasks, tasksLoaded = true) } }
                }
                launch {
                    repository
                        .observeMyApplications(user.uid)
                        .catch { e -> messenger.showError("Could not load your applications. ${errorMessage(e)}") }
                        .collect { applications -> _state.update { it.copy(myApplications = applications) } }
                }
            }
    }

    // Session

    fun signIn(
        email: String,
        password: String,
    ) = runAuthAction {
        val user = repository.signIn(email, password)
        _state.update { it.copy(user = user) }
        startListening(user)
        messenger.show("Welcome back, ${user.displayName.substringBefore(' ')}!")
    }

    fun register(
        name: String,
        email: String,
        password: String,
    ) = runAuthAction {
        val user = repository.register(name, email, password)
        _state.update { it.copy(user = user) }
        startListening(user)
        messenger.show("Account created. Welcome, ${user.displayName.substringBefore(' ')}!")
    }

    /** Emails a password reset link. The answer is the same whether or not the account exists. */
    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            try {
                repository.sendPasswordReset(email)
                messenger.show("If an account exists for ${email.trim()}, a reset link is on its way. Check your inbox.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messenger.showError(authErrorMessage(e))
            }
        }
    }

    fun signOut() {

        // Stop the live lists before signing out, otherwise they fail with "permission denied".
        listenersJob?.cancel()
        listenersJob = null
        repository.signOut()
        _state.value = MarketplaceState()
        messenger.show("Signed out.")

    }

    fun clearAuthError() = _state.update { it.copy(authError = null) }

    // Profile and wallet

    fun updateCategories(categories: List<TaskCategory>) =
        runAction("Interests saved.") {
            val user = state.value.user ?: error("Please sign in first.")
            val updated = repository.updateHelperCategories(user, categories)
            _state.update { it.copy(user = updated) }
        }

    /** Loads the user's payment records (called when the Wallet opens). */
    fun refreshPayments() {
        val user = state.value.user ?: return
        viewModelScope.launch {
            try {
                val payments = repository.paymentRecords(user.uid)
                _state.update { it.copy(payments = payments) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messenger.showError("Could not load your payments. ${errorMessage(e)}")
            }
        }
    }

    fun markPayment(payment: PaymentRecord) =
        runAction("Payment marked as paid. The app does not verify transfers.") {
            val user = state.value.user ?: error("Please sign in first.")
            repository.markPaymentRecorded(payment.id, user)
            _state.update { it.copy(payments = repository.paymentRecords(user.uid)) }
        }

    fun dismissMessage(id: Long) = messenger.dismiss(id)

    /** Runs an action, disabling buttons meanwhile, then shows [successMessage] or the error. */
    private fun runAction(
        successMessage: String,
        action: suspend () -> Unit,
    ) {
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

    /** Like [runAction], but a failure is shown under the login form instead of in a snackbar. */
    private fun runAuthAction(action: suspend () -> Unit) {
        if (state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, authError = null) }
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(authError = authErrorMessage(e)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        /** Creates this ViewModel with the shared repository and messenger from the AppContainer. */
        val Factory =
            viewModelFactory {
                initializer {
                    val container = appContainer()
                    AppViewModel(container.repository, container.messenger)
                }
            }
    }
}
