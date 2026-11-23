package com.vitorfreitas.getyourhelp.ui.browse

import com.vitorfreitas.getyourhelp.data.Coordinates
import com.vitorfreitas.getyourhelp.data.TaskCategory
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.data.TaskStatus
import com.vitorfreitas.getyourhelp.location.distanceKmOrNull

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

// How Browse filters and sorts the open tasks. Plain Kotlin, covered by unit tests.

enum class TaskSort(val label: String) {
    NEWEST("Newest first"),
    NEAREST("Nearest first")
}

/** The distance filter options, in kilometres. */
val DISTANCE_OPTIONS_KM = listOf(1, 5, 10, 25, 50)

/** What the user chose on Browse. Null category or distance means "any". */
data class BrowseFilters(
    val category: TaskCategory? = null,
    val maxDistanceKm: Int? = null,
    val sort: TaskSort = TaskSort.NEWEST
) {
    /** True if the user's location is needed to apply these filters. */
    val usesDistance: Boolean get() = maxDistanceKm != null || sort == TaskSort.NEAREST
}

/** A task as shown on Browse, with its distance from the user (null if either position is unknown). */
data class BrowseItem(val task: TaskItem, val distanceKm: Double?)

/**
 * The tasks Browse shows: open tasks from other users that match [filters].
 * - Without [myLocation], distances are unknown, so the distance filter and "Nearest first" cannot
 *   be applied yet and the list is shown newest first (the screen explains this).
 * - With a distance filter, tasks without a position are hidden, because their distance is unknown.
 * - "Nearest first" puts tasks without a position at the end.
 * [tasks] must be newest first, as the repository returns them.
 */
fun browseTasks(
    tasks: List<TaskItem>,
    userId: String,
    filters: BrowseFilters,
    myLocation: Coordinates?
): List<BrowseItem> {
    val items = tasks
        .filter { it.status == TaskStatus.OPEN && it.ownerId != userId }
        .filter { filters.category == null || it.category == filters.category }
        .map { BrowseItem(it, distanceKmOrNull(myLocation, it.coordinates)) }
    if (myLocation == null) return items

    val maxDistance = filters.maxDistanceKm
    val inRange = if (maxDistance == null) items else items.filter { it.distanceKm != null && it.distanceKm <= maxDistance }
    return when (filters.sort) {
        TaskSort.NEWEST -> inRange
        TaskSort.NEAREST -> inRange.sortedWith(compareBy(nullsLast()) { it.distanceKm })
    }
}
