package com.vitorfreitas.getyourhelp.ui.mytasks

import com.vitorfreitas.getyourhelp.data.ApplicationStatus
import com.vitorfreitas.getyourhelp.data.TaskApplication
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.data.TaskStatus

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

// How My Tasks sorts tasks into groups (plain Kotlin, no Android classes).

/** WAITING is "Open" for the user's own tasks and "Applied" for tasks they applied to. */
enum class TaskStage { WAITING, ONGOING, COMPLETED, CANCELLED }

/** A task in the Helping tab, with the user's application for it. */
data class HelpingItem(val task: TaskItem, val application: TaskApplication?) {
    /** A pending application for a task that went to someone else is shown as declined. */
    val displayStatus: ApplicationStatus?
        get() = application?.status?.let { status ->
            if (status == ApplicationStatus.PENDING && task.status != TaskStatus.OPEN) ApplicationStatus.DECLINED else status
        }

    val taskCancelled: Boolean get() = task.status == TaskStatus.CANCELLED
}

/** The tasks the user posted, by status, newest first. */
fun requestedTasksByStage(tasks: List<TaskItem>, userId: String): Map<TaskStage, List<TaskItem>> {
    val mine = tasks.filter { it.ownerId == userId }
    return TaskStage.entries.associateWith { stage -> mine.filter { it.stage() == stage } }
}

/**
 * The tasks the user helps with. "Applied" lists every application that was not accepted (pending,
 * declined, withdrawn, given to someone else or cancelled); Ongoing and Completed are the tasks the
 * user was accepted for. [applications] must be newest first, so only the latest one per task is kept.
 */
fun helpingTasksByStage(
    tasks: List<TaskItem>,
    applications: List<TaskApplication>,
    userId: String
): Map<TaskStage, List<HelpingItem>> {
    val tasksById = tasks.associateBy { it.id }
    val latestApplications = applications.distinctBy { it.taskId }
    val assignedToMe = tasks.filter { it.assignedHelperId == userId }
        .map { task -> HelpingItem(task, latestApplications.firstOrNull { it.taskId == task.id }) }
    val waiting = latestApplications
        .mapNotNull { application -> tasksById[application.taskId]?.let { HelpingItem(it, application) } }
        .filter { it.task.assignedHelperId != userId }
    return mapOf(
        TaskStage.WAITING to waiting,
        TaskStage.ONGOING to assignedToMe.filter { it.task.status == TaskStatus.ASSIGNED },
        TaskStage.COMPLETED to assignedToMe.filter { it.task.status == TaskStatus.COMPLETED },
        TaskStage.CANCELLED to emptyList()
    )
}

private fun TaskItem.stage(): TaskStage = when (status) {
    TaskStatus.OPEN -> TaskStage.WAITING
    TaskStatus.ASSIGNED -> TaskStage.ONGOING
    TaskStatus.COMPLETED -> TaskStage.COMPLETED
    TaskStatus.CANCELLED -> TaskStage.CANCELLED
}
