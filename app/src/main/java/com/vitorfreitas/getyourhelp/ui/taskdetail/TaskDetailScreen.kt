package com.vitorfreitas.getyourhelp.ui.taskdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiPeople
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.vitorfreitas.getyourhelp.data.ApplicationStatus
import com.vitorfreitas.getyourhelp.data.AppUser
import com.vitorfreitas.getyourhelp.data.PaymentMethod
import com.vitorfreitas.getyourhelp.data.TaskApplication
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.data.TaskStatus
import com.vitorfreitas.getyourhelp.data.TaskType
import com.vitorfreitas.getyourhelp.ui.components.ApplicationStatusChip
import com.vitorfreitas.getyourhelp.ui.components.CategoryAvatar
import com.vitorfreitas.getyourhelp.ui.components.ConfirmDialog
import com.vitorfreitas.getyourhelp.ui.components.EmptyState
import com.vitorfreitas.getyourhelp.ui.components.InfoRow
import com.vitorfreitas.getyourhelp.ui.components.InitialsAvatar
import com.vitorfreitas.getyourhelp.ui.components.LoadingIndicator
import com.vitorfreitas.getyourhelp.ui.components.RewardTag
import com.vitorfreitas.getyourhelp.ui.components.TaskStatusChip
import com.vitorfreitas.getyourhelp.ui.formatDateTime
import com.vitorfreitas.getyourhelp.ui.formatEuro
import com.vitorfreitas.getyourhelp.ui.formatRelativeTime
import com.vitorfreitas.getyourhelp.ui.theme.AppTheme
import com.vitorfreitas.getyourhelp.validation.TaskValidator

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * Full-page task details. What the page offers depends on who is looking and on the task status:
 * - owner, OPEN: the live list of applications, each with Accept and Decline
 * - owner, ONGOING: "Confirm task completed"
 * - helper, OPEN: apply (payment method + optional message), or withdraw a pending application
 * - owner or accepted helper, COMPLETED: "Leave a review"
 * - anyone except the owner: "Report this task"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    state: TaskDetailState,
    user: AppUser,
    onBack: () -> Unit,
    onApply: (method: PaymentMethod?, message: String) -> Unit,
    onWithdraw: () -> Unit,
    onAccept: (TaskApplication) -> Unit,
    onDecline: (TaskApplication) -> Unit,
    onComplete: () -> Unit,
    onReport: (reason: String, details: String) -> Unit,
    onReview: (rating: Int, comment: String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task details") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        val contentModifier = Modifier.padding(padding).consumeWindowInsets(padding)
        val task = state.task
        when {
            task != null -> TaskDetailContent(
                task, state, user, onApply, onWithdraw, onAccept, onDecline, onComplete, onReport, onReview,
                modifier = contentModifier
            )
            state.loading -> LoadingIndicator(contentModifier)
            else -> EmptyState(
                title = "Task not available",
                message = "This task may have been removed.",
                icon = Icons.Outlined.Info,
                modifier = contentModifier
            )
        }
    }
}

@Composable
private fun TaskDetailContent(
    task: TaskItem,
    state: TaskDetailState,
    user: AppUser,
    onApply: (PaymentMethod?, String) -> Unit,
    onWithdraw: () -> Unit,
    onAccept: (TaskApplication) -> Unit,
    onDecline: (TaskApplication) -> Unit,
    onComplete: () -> Unit,
    onReport: (String, String) -> Unit,
    onReview: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isOwner = task.ownerId == user.uid
    val isHelper = task.assignedHelperId == user.uid
    var showReport by rememberSaveable { mutableStateOf(false) }
    var showReview by rememberSaveable { mutableStateOf(false) }
    var confirmComplete by rememberSaveable { mutableStateOf(false) }
    var applicationToAccept by remember { mutableStateOf<TaskApplication?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .imePadding() // keeps the application message field above the keyboard
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header: category icon, title, status and reward
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(task.category, size = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.headlineSmall)
                Text(task.category.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskStatusChip(task.status)
            RewardTag(task)
        }
        Spacer(Modifier.height(16.dp))

        // Key facts
        Card(colors = CardDefaults.cardColors(containerColor = AppTheme.extendedColors.card), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                InfoRow(Icons.Outlined.Place, "Location", task.location)
                InfoRow(Icons.Outlined.Schedule, "When", task.scheduledAt?.let(::formatDateTime) ?: "Flexible, to be agreed")
                InfoRow(Icons.Outlined.Payments, "Payment", paymentDescription(task))
                InfoRow(Icons.Outlined.Person, "Requester", if (isOwner) "${task.ownerName} (you)" else task.ownerName)
                if (task.status != TaskStatus.OPEN) {
                    InfoRow(Icons.Outlined.EmojiPeople, "Helper", if (isHelper) "${task.assignedHelperName} (you)" else task.assignedHelperName)
                }
                val posted = formatRelativeTime(task.createdAt)
                if (posted.isNotEmpty()) InfoRow(Icons.Outlined.History, "Posted", posted)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("About this task", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(task.description, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))

        if (isOwner) {
            OwnerSection(
                task = task,
                applications = state.applications,
                busy = state.busy,
                onAccept = { applicationToAccept = it },
                onDecline = onDecline,
                onComplete = { confirmComplete = true }
            )
        } else {
            HelperSection(task, state.myApplication, isHelper, state.busy, onApply, onWithdraw)
        }

        if (task.status == TaskStatus.COMPLETED && (isOwner || isHelper)) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { showReview = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.RateReview, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Leave a review")
            }
        }
        if (!isOwner) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { showReport = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Report this task")
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    applicationToAccept?.let { application ->
        ConfirmDialog(
            title = "Accept ${application.helperName}?",
            message = "They become the helper for this task, and the other pending applications are declined.",
            confirmLabel = "Accept",
            onConfirm = { onAccept(application) },
            onDismiss = { applicationToAccept = null }
        )
    }
    if (confirmComplete) {
        ConfirmDialog(
            title = "Is the task done?",
            message = "The task moves to Completed and you can review each other.",
            confirmLabel = "Confirm",
            onConfirm = onComplete,
            onDismiss = { confirmComplete = false }
        )
    }
    if (showReport) {
        ReportTaskDialog(
            onSubmit = { reason, details -> onReport(reason, details); showReport = false },
            onDismiss = { showReport = false }
        )
    }
    if (showReview) {
        ReviewDialog(
            onSubmit = { rating, comment -> onReview(rating, comment); showReview = false },
            onDismiss = { showReview = false }
        )
    }
}

/** "€15.00 · Cash or Bank transfer", the agreed method once a helper is chosen, or the volunteering note. */
private fun paymentDescription(task: TaskItem): String = when {
    task.taskType == TaskType.VOLUNTEER -> "Volunteering, no payment"
    task.agreedPaymentMethod != null -> "${formatEuro(task.amount)} · ${task.agreedPaymentMethod.label} (agreed)"
    else -> "${formatEuro(task.amount)} · ${task.acceptedPaymentMethods.joinToString(" or ") { it.label }}"
}

/** The requester's part of the page. */
@Composable
private fun OwnerSection(
    task: TaskItem,
    applications: List<TaskApplication>,
    busy: Boolean,
    onAccept: (TaskApplication) -> Unit,
    onDecline: (TaskApplication) -> Unit,
    onComplete: () -> Unit
) {
    when (task.status) {
        TaskStatus.OPEN -> {
            Text("Applications (${applications.size})", style = MaterialTheme.typography.titleMedium)
            Text(
                if (applications.isEmpty()) "No one has applied yet. New applications appear here straight away."
                else "Accept one helper. The other pending applications are declined automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            applications.forEach { application ->
                ApplicationCard(application, busy, onAccept, onDecline)
                Spacer(Modifier.height(8.dp))
            }
        }
        TaskStatus.ASSIGNED -> NoticeCard(
            icon = Icons.Outlined.EmojiPeople,
            title = "${task.assignedHelperName} is helping you",
            text = "When the work is done, confirm it here. You can then review each other."
        ) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onComplete, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Confirm task completed") }
        }
        TaskStatus.COMPLETED -> NoticeCard(
            icon = Icons.Outlined.CheckCircle,
            title = "Task completed",
            text = "${task.assignedHelperName} helped you with this task.",
            containerColor = AppTheme.extendedColors.successContainer,
            contentColor = AppTheme.extendedColors.onSuccessContainer
        )
    }
}

/** One application, as the requester sees it. */
@Composable
private fun ApplicationCard(
    application: TaskApplication,
    busy: Boolean,
    onAccept: (TaskApplication) -> Unit,
    onDecline: (TaskApplication) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = AppTheme.extendedColors.card), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(application.helperName)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(application.helperName, style = MaterialTheme.typography.titleSmall)
                    val applied = formatRelativeTime(application.createdAt)
                    if (applied.isNotEmpty()) {
                        Text("Applied $applied", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ApplicationStatusChip(application.status)
            }
            application.preferredPaymentMethod?.let { method ->
                Spacer(Modifier.height(8.dp))
                Text("Prefers to be paid by ${method.label.lowercase()}", style = MaterialTheme.typography.bodyMedium)
            }
            if (application.message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("“${application.message}”", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
            }
            if (application.status == ApplicationStatus.PENDING) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onDecline(application) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Decline") }
                    Button(onClick = { onAccept(application) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Accept") }
                }
            }
        }
    }
}

/** The helper's part of the page: what they can do depends on their application and the task status. */
@Composable
private fun HelperSection(
    task: TaskItem,
    myApplication: TaskApplication?,
    isHelper: Boolean,
    busy: Boolean,
    onApply: (PaymentMethod?, String) -> Unit,
    onWithdraw: () -> Unit
) {
    val extended = AppTheme.extendedColors
    when {
        isHelper && task.status == TaskStatus.ASSIGNED -> NoticeCard(
            icon = Icons.Outlined.CheckCircle,
            title = "You're helping with this task",
            text = buildString {
                append("${task.ownerName} accepted your application.")
                task.agreedPaymentMethod?.let { append(" Agreed payment: ${formatEuro(task.amount)} by ${it.label.lowercase()}.") }
                append(" The requester confirms when the work is done.")
            },
            containerColor = extended.successContainer,
            contentColor = extended.onSuccessContainer
        )
        isHelper && task.status == TaskStatus.COMPLETED -> NoticeCard(
            icon = Icons.Outlined.CheckCircle,
            title = "Task completed",
            text = "${task.ownerName} confirmed the work is done. Thank you for helping!",
            containerColor = extended.successContainer,
            contentColor = extended.onSuccessContainer
        )
        task.status != TaskStatus.OPEN -> NoticeCard(
            icon = Icons.Outlined.Info,
            title = "No longer taking applications",
            text = if (myApplication != null) "The requester chose another helper." else "This task already has a helper."
        )
        myApplication?.status == ApplicationStatus.PENDING -> NoticeCard(
            icon = Icons.Outlined.HourglassEmpty,
            title = "Application sent",
            text = "${task.ownerName} will accept or decline it. You'll see the answer here and in My Tasks."
        ) {
            myApplication.preferredPaymentMethod?.let {
                Spacer(Modifier.height(8.dp))
                Text("Preferred payment: ${it.label}", style = MaterialTheme.typography.bodyMedium)
            }
            if (myApplication.message.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("“${myApplication.message}”", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onWithdraw, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Withdraw application") }
        }
        myApplication?.status == ApplicationStatus.DECLINED -> NoticeCard(
            icon = Icons.Outlined.Info,
            title = "Application declined",
            text = "${task.ownerName} decided not to go ahead with your application. Other tasks are waiting in Browse!"
        )
        else -> ApplyForm(task, busy, applyingAgain = myApplication?.status == ApplicationStatus.WITHDRAWN, onApply)
    }
}

/** Choose how to be paid (paid tasks only), add an optional message, and apply. */
@Composable
private fun ApplyForm(task: TaskItem, busy: Boolean, applyingAgain: Boolean, onApply: (PaymentMethod?, String) -> Unit) {
    val isPaid = task.taskType == TaskType.PAID
    var method by rememberSaveable(task.id) { mutableStateOf(task.acceptedPaymentMethods.firstOrNull()) }
    var message by rememberSaveable(task.id) { mutableStateOf("") }
    val messageError = TaskValidator.applicationMessage(message)
    Card(colors = CardDefaults.cardColors(containerColor = AppTheme.extendedColors.card), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(if (applyingAgain) "Apply again" else "Interested? Apply for this task", style = MaterialTheme.typography.titleMedium)
            if (isPaid) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (task.acceptedPaymentMethods.size > 1) "How would you like to be paid?" else "The requester pays by:",
                    style = MaterialTheme.typography.bodyMedium
                )
                task.acceptedPaymentMethods.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = method == option, onClick = { method = option }, role = Role.RadioButton)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = method == option, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(option.label)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Message to the requester (optional)") },
                placeholder = { Text("e.g. I have a dog myself and I'm free on Saturday.") },
                isError = messageError != null,
                supportingText = { Text(messageError ?: "${message.trim().length}/${TaskValidator.APPLICATION_MESSAGE_MAX}") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onApply(if (isPaid) method else null, message) },
                enabled = !busy && messageError == null && (!isPaid || method != null),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Apply for this task") }
        }
    }
}

/** A coloured card with an icon and a title, used for status messages on this page. */
@Composable
private fun NoticeCard(
    icon: ImageVector,
    title: String,
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}
