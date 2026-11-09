package com.vitorfreitas.getyourhelp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vitorfreitas.getyourhelp.data.AppUser
import com.vitorfreitas.getyourhelp.data.MarketplaceRepository
import com.vitorfreitas.getyourhelp.data.PaymentRecord
import com.vitorfreitas.getyourhelp.data.TaskApplication
import com.vitorfreitas.getyourhelp.data.TaskItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MarketplaceState(
    val user: AppUser? = null,
    val tasks: List<TaskItem> = emptyList(),
    val myApplications: List<TaskApplication> = emptyList(),
    val selectedTaskApplications: List<TaskApplication> = emptyList(),
    val payments: List<PaymentRecord> = emptyList(),
    val loading: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val firebaseMode: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MarketplaceRepository(application)
    private val _state = MutableStateFlow(MarketplaceState(firebaseMode = repository.isFirebaseMode))
    val state = _state.asStateFlow()
    private var taskObserverJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val current = repository.currentUser()
                _state.update { it.copy(user = current, loading = false) }
                if (current != null) startTaskObserver()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Could not restore the session.") }
            }
        }
    }

    private fun startTaskObserver() {
        taskObserverJob?.cancel()
        taskObserverJob = viewModelScope.launch {
            try {
                repository.observeTasks().collect { tasks ->
                    _state.update { it.copy(tasks = tasks, loading = false) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Could not load tasks.") }
            }
        }
    }

    fun signIn(email: String, password: String) = runAction {
        val signedInUser = repository.signIn(email, password)
        _state.update { it.copy(user = signedInUser, message = "Signed in successfully.") }
        startTaskObserver()
        refreshMyData()
    }

    fun register(name: String, email: String, password: String) = runAction {
        val createdUser = repository.register(name, email, password)
        _state.update { it.copy(user = createdUser, message = "Account created.") }
        startTaskObserver()
        refreshMyData()
    }

    fun demoLogin(asRequester: Boolean) = runAction {
        val user = repository.demoLogin(asRequester)
        _state.update { it.copy(user = user, message = "Demo mode: ${user.displayName}.") }
        startTaskObserver()
        refreshMyData()
    }

    fun signOut() = runAction {
        repository.signOut()
        taskObserverJob?.cancel()
        _state.update { MarketplaceState(tasks = it.tasks, firebaseMode = repository.isFirebaseMode, message = "Signed out.") }
    }

    fun createTask(title: String, description: String, category: String, type: String, amount: String, methods: List<String>, location: String) = runAction {
        val user = state.value.user ?: error("Please sign in first.")
        repository.createTask(user, title, description, category, type,
            if (type == "PAID") amount.toDoubleOrNull() ?: 0.0 else 0.0, methods, location)
        _state.update { it.copy(message = "Task published.") }
    }

    fun loadApplications(task: TaskItem) = runAction {
        val applications = repository.applicationsForTask(task.id)
        _state.update { it.copy(selectedTaskApplications = applications) }
    }

    fun apply(task: TaskItem, method: String) = runAction {
        val user = state.value.user ?: error("Please sign in first.")
        repository.applyToTask(task, user, method)
        _state.update { it.copy(message = "Application submitted.") }
        refreshMyData()
    }

    fun accept(task: TaskItem, application: TaskApplication) = runAction {
        repository.acceptApplication(task, application)
        _state.update { it.copy(message = "Applicant accepted.") }
        loadApplications(task)
        refreshMyData()
    }

    fun complete(task: TaskItem) = runAction {
        val user = state.value.user ?: error("Please sign in first.")
        repository.completeTask(task, user)
        _state.update { it.copy(message = "Task marked as completed. Both parties can now review each other.") }
    }

    fun report(task: TaskItem, reason: String, details: String) = runAction {
        val user = state.value.user ?: error("Please sign in first.")
        repository.reportTask(task, user, reason, details)
        _state.update { it.copy(message = "Report submitted for review.") }
    }

    fun review(task: TaskItem, rating: Int, comment: String) = runAction {
        val user = state.value.user ?: error("Please sign in first.")
        repository.submitReview(task, user, rating, comment)
        _state.update { it.copy(message = "Review submitted.") }
    }

    fun markPayment(payment: PaymentRecord) = runAction {
        val user = state.value.user ?: error("Please sign in first.")
        repository.markPaymentRecorded(payment.id, user)
        _state.update { it.copy(message = "Payment status recorded. The app does not verify external transfers.") }
        refreshMyData()
    }

    fun updateCategories(categories: List<String>) = runAction {
        val user = state.value.user ?: error("Please sign in first.")
        val updated = repository.updateHelperCategories(user, categories)
        _state.update { it.copy(user = updated, message = "Helper categories updated.") }
    }

    fun refreshMyData() {
        val user = state.value.user ?: return
        viewModelScope.launch {
            try {
                val apps = repository.myApplications(user.uid)
                val payments = repository.paymentRecords(user.uid)
                _state.update { it.copy(myApplications = apps, payments = payments) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Could not refresh your activity.") }
            }
        }
    }

    fun clearMessage() { _state.update { it.copy(message = null, error = null) } }

    private fun runAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try { action() }
            catch (e: Exception) { _state.update { it.copy(error = e.message ?: "Something went wrong.") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
}
