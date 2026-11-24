package com.vitorfreitas.getyourhelp.ui.browse

import com.vitorfreitas.getyourhelp.data.Coordinates
import com.vitorfreitas.getyourhelp.data.TaskCategory
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.data.TaskStatus
import com.vitorfreitas.getyourhelp.data.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

class BrowseFiltersTest {
    private val me = "me"
    private val home = Coordinates(53.3300, -6.2600)

    /** A point about [km] kilometres north of home (1 degree of latitude is about 111 km). */
    private fun north(km: Double) = Coordinates(home.latitude + km / 111.2, home.longitude)

    private fun task(
        id: String,
        owner: String = "someone",
        status: TaskStatus = TaskStatus.OPEN,
        category: TaskCategory = TaskCategory.OTHER,
        coordinates: Coordinates? = null
    ) = TaskItem(
        id = id, ownerId = owner, ownerName = "Owner", title = "Task $id", description = "A task",
        category = category, taskType = TaskType.VOLUNTEER, amount = 0.0, acceptedPaymentMethods = emptyList(),
        location = "Dublin", coordinates = coordinates, status = status
    )

    @Test
    fun onlyOpenTasksFromOtherUsersAreShown() {
        val tasks = listOf(
            task("open"),
            task("mine", owner = me),
            task("taken", status = TaskStatus.ASSIGNED),
            task("cancelled", status = TaskStatus.CANCELLED)
        )
        assertEquals(listOf("open"), browseTasks(tasks, me, BrowseFilters(), home).map { it.task.id })
    }

    @Test
    fun categoryFilterKeepsMatchingTasks() {
        val tasks = listOf(task("dog", category = TaskCategory.PET_CARE), task("garden", category = TaskCategory.GARDENING))
        val shown = browseTasks(tasks, me, BrowseFilters(category = TaskCategory.PET_CARE), home)
        assertEquals(listOf("dog"), shown.map { it.task.id })
    }

    @Test
    fun nearestFirstPutsTasksWithoutAPositionLast() {
        // Newest first, as the repository returns them
        val tasks = listOf(task("unknown"), task("far", coordinates = north(8.0)), task("near", coordinates = north(1.0)))
        val shown = browseTasks(tasks, me, BrowseFilters(sort = TaskSort.NEAREST), home)
        assertEquals(listOf("near", "far", "unknown"), shown.map { it.task.id })
    }

    @Test
    fun distanceFilterHidesFarTasksAndTasksWithoutAPosition() {
        val tasks = listOf(task("near", coordinates = north(2.0)), task("far", coordinates = north(20.0)), task("unknown"))
        val shown = browseTasks(tasks, me, BrowseFilters(maxDistanceKm = 5), home)
        assertEquals(listOf("near"), shown.map { it.task.id })
        assertEquals(2.0, shown.single().distanceKm!!, 0.05)
    }

    @Test
    fun withoutTheUsersLocationDistanceFiltersCannotApplyYet() {
        val tasks = listOf(task("a", coordinates = north(30.0)), task("b"))
        val shown = browseTasks(tasks, me, BrowseFilters(maxDistanceKm = 1, sort = TaskSort.NEAREST), myLocation = null)
        assertEquals(listOf("a", "b"), shown.map { it.task.id })
        assertNull(shown.first().distanceKm)
    }

    @Test
    fun distanceIsKnownWhenBothPositionsAreKnown() {
        val shown = browseTasks(listOf(task("a", coordinates = north(3.0))), me, BrowseFilters(), home)
        assertNotNull(shown.single().distanceKm)
    }
}
