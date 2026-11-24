package com.vitorfreitas.getyourhelp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vitorfreitas.getyourhelp.auth.GoogleSignInHelper
import com.vitorfreitas.getyourhelp.data.AppUser
import com.vitorfreitas.getyourhelp.data.Coordinates
import com.vitorfreitas.getyourhelp.data.MarketplaceRepository
import com.vitorfreitas.getyourhelp.data.PaymentRecord
import com.vitorfreitas.getyourhelp.data.TaskApplication
import com.vitorfreitas.getyourhelp.data.TaskCategory
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.data.errorMessage
import com.vitorfreitas.getyourhelp.location.LocationService
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
 * Data shared by the whole app. user == null means signed out. busy / googleBusy disable buttons
 * while an action runs, and authError is shown under the login form.
 */
data class MarketplaceState(
    val user: AppUser? = null,
    val checkingSession: Boolean = false,
    val tasks: List<TaskItem> = emptyList(),
    val tasksLoaded: Boolean = false,
    val myApplications: List<TaskApplication> = emptyList(),
    val payments: List<PaymentRecord> = emptyList(),
    val busy: Boolean = false,
    val googleBusy: Boolean = false,
    val authError: String? = null,
    val locating: Boolean = false,
)

/**
 * The activity's ViewModel: the session, the live task list, the user's applications, wallet,
 * profile and location. Screens with their own logic have their own ViewModel.
 */
class AppViewModel(
    private val repository: MarketplaceRepository,
    private val messenger: UserMessenger,
    private val location: LocationService,
    private val googleSignIn: GoogleSignInHelper,
) : ViewModel() {
    private val _state = MutableStateFlow(MarketplaceState(checkingSession = true))
    val state = _state.asStateFlow()
    val messages: StateFlow<List<UserMessage>> = messenger.messages
    val myLocation: StateFlow<Coordinates?> = location.myLocation
    private var listenersJob: Job? = null

    // Restore the session if Firebase still has a signed-in user
    init {
        viewModelScope.launch {
            messenger.attempt(errorPrefix = "Could not restore your session. ") {
                val user = repository.currentUser()
                _state.update { it.copy(user = user) }
                if (user != null) startListening(user)
            }
            _state.update { it.copy(checkingSession = false) }
        }
    }

    /** The live lists that stay open while someone is signed in. */
    private fun startListening(user: AppUser) {
        listenersJob?.cancel()
        listenersJob = viewModelScope.launch {
            launch {
                repository.observeTasks()
                    .catch { e ->
                        _state.update { it.copy(tasksLoaded = true) }
                        messenger.showError("Could not load tasks. ${errorMessage(e)}")
                    }
                    .collect { tasks -> _state.update { it.copy(tasks = tasks, tasksLoaded = true) } }
            }
            launch {
                repository.observeMyApplications(user.uid)
                    .catch { e -> messenger.showError("Could not load your applications. ${errorMessage(e)}") }
                    .collect { applications -> _state.update { it.copy(myApplications = applications) } }
            }
        }
    }

    // Session

    fun signIn(email: String, password: String) = signInWith("Welcome back") { repository.signIn(email, password) }

    fun register(name: String, email: String, password: String) =
        signInWith("Account created. Welcome") { repository.register(name, email, password) }

    /** With the ID token of the Google account the user picked (see GoogleSignInHelper). */
    fun signInWithGoogle(idToken: String) = signInWith("Welcome", google = true) { repository.signInWithGoogle(idToken) }

    /** Runs a sign-in, then opens the app. A failure is shown under the login form. */
    private fun signInWith(greeting: String, google: Boolean = false, signIn: suspend () -> AppUser) {
        if (state.value.busy || state.value.googleBusy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = !google, googleBusy = google, authError = null) }
            val error = try {
                val user = signIn()
                _state.update { it.copy(user = user) }
                startListening(user)
                messenger.show("$greeting, ${user.displayName.substringBefore(' ')}!")
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage(e, google)
            }
            _state.update { it.copy(busy = false, googleBusy = false, authError = error) }
        }
    }

    fun showAuthError(message: String) = _state.update { it.copy(authError = message) }

    fun clearAuthError() = _state.update { it.copy(authError = null) }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            messenger.attempt("If an account exists for ${email.trim()}, a reset link is on its way. Check your inbox.") {
                repository.sendPasswordReset(email)
            }
        }
    }

    fun signOut() {
        // Stop the live lists first, otherwise they fail with "permission denied"
        listenersJob?.cancel()
        listenersJob = null
        repository.signOut()
        _state.value = MarketplaceState()
        // Forget the Google account, so the next person can pick their own
        viewModelScope.launch { googleSignIn.clearCredentialState() }
        messenger.show("Signed out.")
    }

    // Location

    /** Reads the phone's position for distances. [userInitiated]: explain a failure to the user. */
    fun refreshLocation(userInitiated: Boolean) {
        if (state.value.locating) return
        viewModelScope.launch {
            _state.update { it.copy(locating = true) }
            val found = location.currentLocation()
            _state.update { it.copy(locating = false) }
            if (found == null && userInitiated) {
                messenger.showError("Couldn't find your location. Check that location is turned on, then try again.")
            }
        }
    }

    /** Reads the position quietly if the user already allowed it. */
    fun refreshLocationIfAllowed() {
        if (location.hasPermission() && myLocation.value == null) refreshLocation(userInitiated = false)
    }

    fun locationPermissionDenied() = messenger.showError(
        "Location is off for GetYourHelp, so distances can't be shown. You can allow it in Settings > Apps > GetYourHelp > Permissions."
    )

    fun showMessage(text: String) = messenger.show(text)

    fun dismissMessage(id: Long) = messenger.dismiss(id)

    // Profile and wallet

    fun updateCategories(categories: List<TaskCategory>) = runAction("Interests saved.") {
        val user = state.value.user ?: return@runAction
        _state.update { it.copy(user = repository.updateHelperCategories(user, categories)) }
    }

    /** Loads the payment records when the Wallet opens. */
    fun refreshPayments() {
        val user = state.value.user ?: return
        viewModelScope.launch {
            messenger.attempt(errorPrefix = "Could not load your payments. ") {
                val payments = repository.paymentRecords(user.uid)
                _state.update { it.copy(payments = payments) }
            }
        }
    }

    fun markPayment(payment: PaymentRecord) = runAction("Payment marked as paid. The app does not verify transfers.") {
        repository.markPaymentRecorded(payment.id)
        refreshPayments()
    }

    /** Runs an action with the buttons disabled, then shows [successMessage] or the error. */
    private fun runAction(successMessage: String, action: suspend () -> Unit) {
        if (state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            messenger.attempt(successMessage, action = action)
            _state.update { it.copy(busy = false) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                AppViewModel(container.repository, container.messenger, container.location, container.googleSignIn)
            }
        }
    }
}
