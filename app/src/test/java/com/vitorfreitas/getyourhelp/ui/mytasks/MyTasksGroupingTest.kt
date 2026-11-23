package com.vitorfreitas.getyourhelp.ui.mytasks

import com.vitorfreitas.getyourhelp.data.ApplicationStatus
import com.vitorfreitas.getyourhelp.data.TaskApplication
import com.vitorfreitas.getyourhelp.data.TaskCategory
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.data.TaskStatus
import com.vitorfreitas.getyourhelp.data.TaskType
import org.junit.Assert.assertEquals
import org.junit.Test

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

class MyTasksGroupingTest {
    private val me = "me"

    private fun task(id: String, owner: String = "someone", status: TaskStatus = TaskStatus.OPEN, helper: String = "") =
        TaskItem(
            id = id, ownerId = owner, ownerName = "Owner", title = "Task $id", description = "A task",
            category = TaskCategory.OTHER, taskType = TaskType.VOLUNTEER, amount = 0.0,
            acceptedPaymentMethods = emptyList(), location = "Dublin", status = status, assignedHelperId = helper
        )

    private fun application(taskId: String, status: ApplicationStatus = ApplicationStatus.PENDING, createdAt: Long = 0L) =
        TaskApplication(
            id = "${taskId}_$me", taskId = taskId, helperId = me, helperName = "Me",
            preferredPaymentMethod = null, status = status, createdAt = createdAt
        )

    @Test
    fun requestedTasksAreGroupedByStatus() {
        val tasks = listOf(
            task("open", owner = me),
            task("ongoing", owner = me, status = TaskStatus.ASSIGNED, helper = "h"),
            task("done", owner = me, status = TaskStatus.COMPLETED, helper = "h"),
            task("not-mine")
        )
        val groups = requestedTasksByStage(tasks, me)
        assertEquals(listOf("open"), groups.getValue(TaskStage.WAITING).map { it.id })
        assertEquals(listOf("ongoing"), groups.getValue(TaskStage.ONGOING).map { it.id })
        assertEquals(listOf("done"), groups.getValue(TaskStage.COMPLETED).map { it.id })
    }

    @Test
    fun acceptedTasksAreOngoingOrCompletedForTheHelper() {
        val tasks = listOf(
            task("ongoing", status = TaskStatus.ASSIGNED, helper = me),
            task("done", status = TaskStatus.COMPLETED, helper = me)
        )
        val applications = listOf(
            application("ongoing", ApplicationStatus.ACCEPTED),
            application("done", ApplicationStatus.ACCEPTED)
        )
        val groups = helpingTasksByStage(tasks, applications, me)
        assertEquals(emptyList<String>(), groups.getValue(TaskStage.WAITING).map { it.task.id })
        assertEquals(listOf("ongoing"), groups.getValue(TaskStage.ONGOING).map { it.task.id })
        assertEquals(listOf("done"), groups.getValue(TaskStage.COMPLETED).map { it.task.id })
    }

    @Test
    fun appliedListsEveryApplicationThatWasNotAccepted() {
        val tasks = listOf(task("pending"), task("declined"), task("withdrawn"))
        val applications = listOf(
            application("pending"),
            application("declined", ApplicationStatus.DECLINED),
            application("withdrawn", ApplicationStatus.WITHDRAWN)
        )
        val applied = helpingTasksByStage(tasks, applications, me).getValue(TaskStage.WAITING)
        assertEquals(listOf("pending", "declined", "withdrawn"), applied.map { it.task.id })
        assertEquals(
            listOf(ApplicationStatus.PENDING, ApplicationStatus.DECLINED, ApplicationStatus.WITHDRAWN),
            applied.map { it.displayStatus }
        )
    }

    @Test
    fun pendingApplicationForATaskGivenToSomeoneElseShowsAsDeclined() {
        val tasks = listOf(task("taken", status = TaskStatus.ASSIGNED, helper = "another helper"))
        val applied = helpingTasksByStage(tasks, listOf(application("taken")), me).getValue(TaskStage.WAITING)
        assertEquals(ApplicationStatus.DECLINED, applied.single().displayStatus)
    }

    @Test
    fun onlyTheNewestApplicationPerTaskIsShown() {
        // Applications arrive newest first, as the repository returns them
        val applications = listOf(
            application("t", ApplicationStatus.PENDING, createdAt = 2),
            application("t", ApplicationStatus.WITHDRAWN, createdAt = 1)
        )
        val applied = helpingTasksByStage(listOf(task("t")), applications, me).getValue(TaskStage.WAITING)
        assertEquals(ApplicationStatus.PENDING, applied.single().displayStatus)
    }

    @Test
    fun applicationsForDeletedTasksAreSkipped() {
        val applied = helpingTasksByStage(emptyList(), listOf(application("gone")), me).getValue(TaskStage.WAITING)
        assertEquals(0, applied.size)
    }
}
