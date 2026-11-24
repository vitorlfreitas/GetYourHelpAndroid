package com.vitorfreitas.getyourhelp.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.vitorfreitas.getyourhelp.appContainer
import com.vitorfreitas.getyourhelp.data.AppUser
import com.vitorfreitas.getyourhelp.data.Coordinates
import com.vitorfreitas.getyourhelp.data.MarketplaceRepository
import com.vitorfreitas.getyourhelp.data.PaymentMethod
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.data.TaskStatus
import com.vitorfreitas.getyourhelp.data.TaskType
import com.vitorfreitas.getyourhelp.location.LocationService
import com.vitorfreitas.getyourhelp.location.approximate
import com.vitorfreitas.getyourhelp.ui.UserMessenger
import com.vitorfreitas.getyourhelp.ui.navigation.EditTaskRoute
import com.vitorfreitas.getyourhelp.validation.TaskInput
import com.vitorfreitas.getyourhelp.validation.TaskValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** A place found with GPS for the location field. [id] changes every time, so the screen fills it in only once. */
data class LocationFill(val id: Long, val label: String)

/**
 * initial is null while the task to edit is loading. paymentLocked: someone has applied, so the
 * payment can't change. savedTaskId and loadFailed tell the screen to close.
 */
data class TaskFormState(
    val initial: TaskInput? = null,
    val paymentLocked: Boolean = false,
    val busy: Boolean = false,
    val locating: Boolean = false,
    val locationFill: LocationFill? = null,
    val savedTaskId: String? = null,
    val loadFailed: Boolean = false
)

/** A place name with its approximate position. */
private data class Place(val label: String, val coordinates: Coordinates?)

/**
 * Posts a new task ([editTaskId] = null) or edits an open one. The form fields live in the screen.
 * Each task gets an approximate position when possible: from GPS, or by looking up the typed place.
 */
class TaskFormViewModel(
    private val repository: MarketplaceRepository,
    private val location: LocationService,
    private val messenger: UserMessenger,
    editTaskId: String?
) : ViewModel() {
    private val _state = MutableStateFlow(TaskFormState(initial = if (editTaskId == null) TaskInput.Empty else null))
    val state = _state.asStateFlow()
    private var original: TaskItem? = null // the task being edited
    private var knownPlace: Place? = null // a place name with a known position (saved, or found with GPS)
    private var fillCount = 0L

    init {
        if (editTaskId != null) viewModelScope.launch {
            val loaded = messenger.attempt(errorPrefix = "Could not open the task for editing. ") {
                val task = repository.getTask(editTaskId)?.takeIf { it.status == TaskStatus.OPEN }
                    ?: error("Only open tasks can be edited.")
                original = task
                knownPlace = Place(task.location, task.coordinates)
                val paymentLocked = repository.hasPendingApplications(task.id)
                _state.update { it.copy(initial = TaskInput.from(task), paymentLocked = paymentLocked) }
            }
            if (!loaded) _state.update { it.copy(loadFailed = true) }
        }
    }

    /** Fills the location field with the area's name ("Ranelagh, Dublin"). Only an approximate position is kept. */
    fun useCurrentLocation() {
        if (state.value.locating) return
        viewModelScope.launch {
            _state.update { it.copy(locating = true) }
            val here = location.currentLocation()
            val area = here?.let { location.areaName(it) }
            when {
                here == null -> messenger.showError("Couldn't find your location. Check that location is turned on, then try again.")
                area == null -> messenger.showError("Found your location, but not the name of the area. Please type it in.")
                else -> {
                    knownPlace = Place(area, here.approximate())
                    _state.update { it.copy(locationFill = LocationFill(++fillCount, area)) }
                }
            }
            _state.update { it.copy(locating = false) }
        }
    }

    /** Checks the form again (the button is not the only safeguard), then publishes or saves the task. */
    fun submit(owner: AppUser, input: TaskInput) {
        val validation = TaskValidator.validate(input)
        if (!validation.isValid || state.value.busy) return
        val editing = original
        // Saving without changes would only mark the task as "edited"
        if (editing != null && TaskValidator.sameTask(TaskInput.from(editing), input)) {
            messenger.show("No changes to save.")
            _state.update { it.copy(savedTaskId = editing.id) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            messenger.attempt(
                success = if (editing == null) "Task published. Helpers can now apply." else "Changes saved.",
                errorPrefix = if (editing == null) "Could not publish the task. " else "Could not save the changes. "
            ) {
                val task = taskFrom(input, validation.parsedAmount, owner)
                val taskId = if (editing == null) repository.createTask(task) else editing.id.also { repository.updateTask(editing, task) }
                _state.update { it.copy(savedTaskId = taskId) }
            }
            _state.update { it.copy(busy = false) }
        }
    }

    /** The task described by the form. Volunteer tasks have no amount or payment methods. */
    private suspend fun taskFrom(input: TaskInput, amount: Double, owner: AppUser): TaskItem {
        val paid = input.type == TaskType.PAID
        return TaskItem(
            id = "", ownerId = owner.uid, ownerName = owner.displayName, title = input.title.trim(),
            description = input.description.trim(), category = input.category, taskType = input.type,
            amount = if (paid) amount else 0.0,
            acceptedPaymentMethods = if (paid) PaymentMethod.entries.filter { it in input.paymentMethods } else emptyList(),
            location = input.location.trim(), coordinates = coordinatesFor(input.location), scheduledAt = input.scheduledAt
        )
    }

    /** The known position if the place was not changed, otherwise a lookup of the typed text (null if not found). */
    private suspend fun coordinatesFor(place: String): Coordinates? {
        val text = place.trim()
        knownPlace?.takeIf { it.label.equals(text, ignoreCase = true) && it.coordinates != null }?.let { return it.coordinates }
        return location.coordinatesOf(text)?.approximate()
    }

    companion object {
        val CreateFactory = viewModelFactory {
            initializer { appContainer().let { TaskFormViewModel(it.repository, it.location, it.messenger, editTaskId = null) } }
        }
        val EditFactory = viewModelFactory {
            initializer {
                val taskId = createSavedStateHandle().toRoute<EditTaskRoute>().taskId
                appContainer().let { TaskFormViewModel(it.repository, it.location, it.messenger, taskId) }
            }
        }
    }
}
