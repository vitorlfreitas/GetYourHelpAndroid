package com.vitorfreitas.getyourhelp.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

class ModelsTest {
    private fun task(status: TaskStatus, helper: String = "helper") = TaskItem(
        id = "t", ownerId = "owner", ownerName = "Owner", title = "Task", description = "A task",
        category = TaskCategory.OTHER, taskType = TaskType.VOLUNTEER, amount = 0.0,
        acceptedPaymentMethods = emptyList(), location = "Dublin", status = status, assignedHelperId = helper
    )

    @Test
    fun onlyTheRequesterAndTheAcceptedHelperHaveAChat() {
        val ongoing = task(TaskStatus.ASSIGNED)
        assertTrue(ongoing.hasChatFor("owner"))
        assertTrue(ongoing.hasChatFor("helper"))
        assertFalse(ongoing.hasChatFor("someone else"))
    }

    @Test
    fun thereIsNoChatBeforeAHelperIsAccepted() {
        assertFalse(task(TaskStatus.OPEN, helper = "").hasChatFor("owner"))
        assertFalse(task(TaskStatus.CANCELLED, helper = "").hasChatFor("owner"))
    }

    @Test
    fun theChatIsReadOnlyOnceTheTaskIsCompleted() {
        val completed = task(TaskStatus.COMPLETED)
        assertTrue(completed.hasChatFor("helper"))
        assertFalse(completed.chatIsOpen)
        assertTrue(task(TaskStatus.ASSIGNED).chatIsOpen)
    }

    @Test
    fun reportIdsAllowOneReportPerUserPerTask() {
        assertTrue(reportId("task1", "user1") == reportId("task1", "user1"))
        assertFalse(reportId("task1", "user1") == reportId("task1", "user2"))
    }
}
