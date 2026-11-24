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
import com.vitorfreitas.getyourhelp.data.errorMessage
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
import kotlin.coroutines.cancellation.CancellationException

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** A place name found with GPS, to put in the location field. [id] changes every time, so the screen fills it only once. */
data class LocationFill(val id: Long, val label: String)

/**
 * - initial: the values the form starts with (empty for a new task, the task's values when editing;
 *   null while the task to edit is loading)
 * - paymentLocked: when editing, someone has a pending application, so the payment can't change
 * - busy: the task is being saved
 * - locating: "Use my current location" is reading the GPS
 * - locationFill: a place name found with GPS, for the location field
 * - savedTaskId: set once the task is saved, so the screen can close
 * - loadFailed: the task to edit could not be opened, so the screen closes
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

/** A place name with its approximate position (null if unknown). */
private data class Place(val label: String, val coordinates: Coordinates?)

/**
 * ViewModel of the task form, used both to post a new task ([editTaskId] = null) and to edit an
 * open one. The form fields themselves live in the screen (they survive rotation there).
 *
 * Every task gets an approximate position when possible, for distances and the map:
 * from "Use my current location", or by looking up the typed place name (geocoding).
 */
class TaskFormViewModel(
    private val repository: MarketplaceRepository,
    private val location: LocationService,
    private val messenger: UserMessenger,
    private val editTaskId: String?
) : ViewModel() {
    private val _state = MutableStateFlow(if (editTaskId == null) TaskFormState(initial = TaskInput.Empty) else TaskFormState())
    val state = _state.asStateFlow()

    /** The task being edited (null when posting a new one). */
    private var original: TaskItem? = null

    /** A place name the app already has a position for: the task's saved location, or the one found with GPS. */
    private var knownPlace: Place? = null
    private var fillCount = 0L

    init {
        if (editTaskId != null) loadTaskToEdit(editTaskId)
    }

    private fun loadTaskToEdit(taskId: String) {
        viewModelScope.launch {
            try {
                val task = repository.getTask(taskId) ?: error("This task no longer exists.")
                require(task.ownerId == repository.currentUserId()) { "Only the requester can edit this task." }
                require(task.status == TaskStatus.OPEN) { "Only open tasks can be edited." }
                original = task
                knownPlace = Place(task.location, task.coordinates)
                val paymentLocked = repository.hasPendingApplications(taskId)
                _state.update { it.copy(initial = TaskInput.from(task), paymentLocked = paymentLocked) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messenger.showError("Could not open the task for editing. ${errorMessage(e)}")
                _state.update { it.copy(loadFailed = true) }
            }
        }
    }

    /**
     * Reads the GPS and fills the location field with the name of the area ("Ranelagh, Dublin").
     * Only an approximate position (about 100 m) is kept for the task, never the exact one.
     */
    fun useCurrentLocation() {
        if (state.value.locating) return
        viewModelScope.launch {
            _state.update { it.copy(locating = true) }
            try {
                val here = location.currentLocation()
                if (here == null) {
                    messenger.showError("Couldn't find your location. Check that location is turned on, then try again.")
                    return@launch
                }
                val area = location.areaName(here)
                if (area == null) {
                    messenger.showError("Found your location, but not the name of the area. Please type it in.")
                    return@launch
                }
                knownPlace = Place(area, here.approximate())
                _state.update { it.copy(locationFill = LocationFill(++fillCount, area)) }
            } finally {
                _state.update { it.copy(locating = false) }
            }
        }
    }

    /** Validates the form once more (the button is not the only safeguard), then publishes or saves the task. */
    fun submit(owner: AppUser, input: TaskInput) {
        val validation = TaskValidator.validate(input)
        if (!validation.isValid || state.value.busy) return
        original?.let { task ->
            // Saving without changes would only mark the task as "edited", so just close the form
            if (TaskValidator.sameTask(TaskInput.from(task), input)) {
                messenger.show("No changes to save.")
                _state.update { it.copy(savedTaskId = task.id) }
                return
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val editing = original
            try {
                val paid = input.type == TaskType.PAID
                val base = editing ?: TaskItem(
                    id = "", ownerId = owner.uid, ownerName = owner.displayName, title = "", description = "",
                    category = input.category, taskType = input.type, amount = 0.0,
                    acceptedPaymentMethods = emptyList(), location = ""
                )
                val task = base.copy(
                    title = input.title.trim(), description = input.description.trim(), category = input.category,
                    taskType = input.type, amount = if (paid) validation.parsedAmount else 0.0,
                    acceptedPaymentMethods = if (paid) PaymentMethod.entries.filter { it in input.paymentMethods } else emptyList(),
                    location = input.location.trim(), coordinates = coordinatesFor(input.location),
                    scheduledAt = input.scheduledAt
                )
                val taskId = if (editing == null) {
                    repository.createTask(task).also { messenger.show("Task published. Helpers can now apply.") }
                } else {
                    repository.updateTask(editing, task)
                    messenger.show("Changes saved.")
                    editing.id
                }
                _state.update { it.copy(savedTaskId = taskId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val action = if (editing == null) "publish the task" else "save the changes"
                messenger.showError("Could not $action. ${errorMessage(e)}")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /**
     * The approximate position of the typed location: the known one if the text was not changed,
     * otherwise a geocoder lookup of the text. Null if it cannot be found; the task is still
     * saved, just without a distance.
     */
    private suspend fun coordinatesFor(place: String): Coordinates? {
        val text = place.trim()
        knownPlace?.takeIf { it.label.equals(text, ignoreCase = true) && it.coordinates != null }
            ?.let { return it.coordinates }
        return location.coordinatesOf(text)?.approximate()
    }

    companion object {
        /** For "Post a task". */
        val CreateFactory = viewModelFactory {
            initializer {
                val container = appContainer()
                TaskFormViewModel(container.repository, container.location, container.messenger, editTaskId = null)
            }
        }

        /** For "Edit task": the task id comes from the navigation route. */
        val EditFactory = viewModelFactory {
            initializer {
                val container = appContainer()
                val taskId = createSavedStateHandle().toRoute<EditTaskRoute>().taskId
                TaskFormViewModel(container.repository, container.location, container.messenger, editTaskId = taskId)
            }
        }
    }
}
