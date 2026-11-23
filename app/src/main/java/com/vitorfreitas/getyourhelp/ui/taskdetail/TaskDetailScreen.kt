package com.vitorfreitas.getyourhelp.ui.taskdetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorfreitas.getyourhelp.data.ApplicationStatus
import com.vitorfreitas.getyourhelp.data.AppUser
import com.vitorfreitas.getyourhelp.data.PaymentMethod
import com.vitorfreitas.getyourhelp.data.TaskApplication
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.data.TaskStatus
import com.vitorfreitas.getyourhelp.data.TaskType
import com.vitorfreitas.getyourhelp.ui.components.EmptyState
import com.vitorfreitas.getyourhelp.ui.components.LoadingIndicator
import com.vitorfreitas.getyourhelp.ui.components.StatusTag
import com.vitorfreitas.getyourhelp.ui.formatEuro

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * Full-page task details. [task] is null while the task list is still loading, or if the task no longer exists.
 *
 * The actions shown depend on who is looking and on the task status:
 * - owner, OPEN: the list of applications, each with "Accept applicant"
 * - anyone else, OPEN: choose a payment method and apply
 * - owner, ASSIGNED: "Confirm task completed"
 * - owner or assigned helper, COMPLETED: "Leave a review"
 * - anyone except the owner: "Report this task"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    task: TaskItem?,
    tasksLoaded: Boolean,
    user: AppUser,
    applications: List<TaskApplication>,
    busy: Boolean,
    onBack: () -> Unit,
    onLoadApplications: (task: TaskItem) -> Unit,
    onApply: (task: TaskItem, method: PaymentMethod?) -> Unit,
    onAccept: (task: TaskItem, application: TaskApplication) -> Unit,
    onComplete: (task: TaskItem) -> Unit,
    onReport: (task: TaskItem, reason: String, details: String) -> Unit,
    onReview: (task: TaskItem, rating: Int, comment: String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when {
            task != null -> TaskDetailContent(
                task, user, applications, busy,
                onLoadApplications, onApply, onAccept, onComplete, onReport, onReview,
                modifier = contentModifier
            )
            !tasksLoaded -> LoadingIndicator(contentModifier)
            else -> EmptyState("Task not available", "This task may have been removed.", contentModifier)
        }
    }
}

@Composable
private fun TaskDetailContent(
    task: TaskItem,
    user: AppUser,
    applications: List<TaskApplication>,
    busy: Boolean,
    onLoadApplications: (TaskItem) -> Unit,
    onApply: (TaskItem, PaymentMethod?) -> Unit,
    onAccept: (TaskItem, TaskApplication) -> Unit,
    onComplete: (TaskItem) -> Unit,
    onReport: (TaskItem, String, String) -> Unit,
    onReview: (TaskItem, Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isOwner = task.ownerId == user.uid
    val isParticipant = user.uid == task.ownerId || user.uid == task.assignedHelperId
    var showReport by rememberSaveable { mutableStateOf(false) }
    var showReview by rememberSaveable { mutableStateOf(false) }

    // Only the owner can see who applied, so the applications are loaded when the owner opens the task.
    LaunchedEffect(task.id, isOwner) { if (isOwner) onLoadApplications(task) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        StatusTag(task.status)
        Spacer(Modifier.height(8.dp))
        Text(task.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(task.description)
        Spacer(Modifier.height(12.dp))
        Text("Category: ${task.category.label}")
        Text("Location: ${task.location}")
        Text("Requester: ${task.ownerName}")
        Text(if (task.taskType == TaskType.PAID) "Offered: ${formatEuro(task.amount)}" else "Volunteer opportunity")
        if (task.taskType == TaskType.PAID) {
            Text("Accepted payment: ${task.acceptedPaymentMethods.joinToString { it.label }}")
        }
        if (task.status != TaskStatus.OPEN) Text("Accepted helper: ${task.assignedHelperName}")

        if (isOwner && task.status == TaskStatus.OPEN) {
            ApplicationsSection(applications, busy, onAccept = { application -> onAccept(task, application) })
        }
        if (!isOwner && task.status == TaskStatus.OPEN) {
            ApplySection(task, busy, onApply = { method -> onApply(task, method) })
        }
        if (isOwner && task.status == TaskStatus.ASSIGNED) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onComplete(task) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Confirm task completed")
            }
        }
        if (task.status == TaskStatus.COMPLETED && isParticipant) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showReview = true }, modifier = Modifier.fillMaxWidth()) { Text("Leave a review") }
        }
        if (!isOwner) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showReport = true }) { Text("Report this task") }
        }
    }

    if (showReport) {
        ReportTaskDialog(
            onSubmit = { reason, details -> onReport(task, reason, details); showReport = false },
            onDismiss = { showReport = false }
        )
    }
    if (showReview) {
        ReviewDialog(
            onSubmit = { rating, comment -> onReview(task, rating, comment); showReview = false },
            onDismiss = { showReview = false }
        )
    }
}

/** The owner's view of an open task: everyone who applied, with a button to accept one of them. */
@Composable
private fun ApplicationsSection(
    applications: List<TaskApplication>,
    busy: Boolean,
    onAccept: (TaskApplication) -> Unit
) {
    Spacer(Modifier.height(16.dp))
    Text("Applications", style = MaterialTheme.typography.titleMedium)
    if (applications.isEmpty()) Text("No applications yet.", style = MaterialTheme.typography.bodySmall)
    applications.forEach { application ->
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(application.helperName, fontWeight = FontWeight.SemiBold)
                application.preferredPaymentMethod?.let { method ->
                    Text("Preferred payment: ${method.label}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Status: ${application.status.label}", style = MaterialTheme.typography.bodySmall)
                if (application.status == ApplicationStatus.PENDING) {
                    Button(onClick = { onAccept(application) }, enabled = !busy) { Text("Accept applicant") }
                }
            }
        }
    }
}

/** A helper's view of an open task: pick a payment method (paid tasks only) and apply. */
@Composable
private fun ApplySection(task: TaskItem, busy: Boolean, onApply: (PaymentMethod?) -> Unit) {
    var preferredMethod by rememberSaveable(task.id) { mutableStateOf(task.acceptedPaymentMethods.firstOrNull()) }
    val isPaid = task.taskType == TaskType.PAID
    Spacer(Modifier.height(16.dp))
    if (isPaid) {
        Text("Choose your preferred payment method", style = MaterialTheme.typography.titleSmall)
        task.acceptedPaymentMethods.forEach { method ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = preferredMethod == method, onClick = { preferredMethod = method })
                Text(method.label)
            }
        }
    } else {
        Text("This is an unpaid volunteering opportunity.")
    }
    Button(
        onClick = { onApply(if (isPaid) preferredMethod else null) },
        enabled = !busy && (!isPaid || preferredMethod != null),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Apply for this task") }
}
