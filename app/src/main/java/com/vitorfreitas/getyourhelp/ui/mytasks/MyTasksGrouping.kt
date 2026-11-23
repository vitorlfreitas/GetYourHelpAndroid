package com.vitorfreitas.getyourhelp.ui.mytasks

import com.vitorfreitas.getyourhelp.data.ApplicationStatus
import com.vitorfreitas.getyourhelp.data.TaskApplication
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.data.TaskStatus

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

// How the My Tasks screen sorts tasks into groups. Plain Kotlin, covered by unit tests.

/**
 * The three groups in each My Tasks tab. WAITING is shown as "Open" for tasks the user posted,
 * and as "Applied" for tasks the user applied to.
 */
enum class TaskStage { WAITING, ONGOING, COMPLETED }

/** A task in the Helping tab, with the user's application for it (null for old data without one). */
data class HelpingItem(val task: TaskItem, val application: TaskApplication?) {
    /**
     * The application status to show. If the task went to someone else while this application was
     * still pending, it is shown as declined.
     */
    val displayStatus: ApplicationStatus?
        get() = application?.status?.let { status ->
            if (status == ApplicationStatus.PENDING && task.status != TaskStatus.OPEN) ApplicationStatus.DECLINED else status
        }
}

/** Tasks the user posted: OPEN -> Open, ASSIGNED -> Ongoing, COMPLETED -> Completed. Keeps the newest-first order. */
fun requestedTasksByStage(tasks: List<TaskItem>, userId: String): Map<TaskStage, List<TaskItem>> {
    val mine = tasks.filter { it.ownerId == userId }
    return TaskStage.entries.associateWith { stage -> mine.filter { it.stage() == stage } }
}

/**
 * Tasks the user is involved in as a helper:
 * - WAITING ("Applied"): applications that were not accepted (yet): pending, declined, withdrawn,
 *   or the task went to another helper. Newest application first.
 * - ONGOING and COMPLETED: tasks where the user is the accepted helper.
 *
 * [applications] must be newest first (as the repository returns them), so that old duplicate
 * applications for the same task are hidden behind the latest one.
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
        TaskStage.COMPLETED to assignedToMe.filter { it.task.status == TaskStatus.COMPLETED }
    )
}

private fun TaskItem.stage(): TaskStage = when (status) {
    TaskStatus.OPEN -> TaskStage.WAITING
    TaskStatus.ASSIGNED -> TaskStage.ONGOING
    TaskStatus.COMPLETED -> TaskStage.COMPLETED
}
