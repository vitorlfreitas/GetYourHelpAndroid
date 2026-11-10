package com.vitorfreitas.getyourhelp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorfreitas.getyourhelp.data.AppUser
import com.vitorfreitas.getyourhelp.data.PaymentRecord
import com.vitorfreitas.getyourhelp.data.TaskApplication
import com.vitorfreitas.getyourhelp.data.TaskItem
import java.text.DateFormat
import java.util.Date

/** The app's single activity */
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GetYourHelpApp(viewModel)
                }
            }
        }
    }
}

/** Tabs in the bottom navigation bar. */
private enum class MainTab(val label: String) { BROWSE("Browse"), CREATE("Create"), MY_TASKS("My Tasks"), WALLET("Wallet"), PROFILE("Profile") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GetYourHelpApp(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(MainTab.BROWSE) }
    var selectedTask by remember { mutableStateOf<TaskItem?>(null) }

    LaunchedEffect(state.user?.uid) {
        if (state.user != null) vm.refreshMyData()
    }

    if (state.user == null) {
        LoginScreen(
            busy = state.busy,
            error = state.error,
            onSignIn = vm::signIn,
            onRegister = vm::register
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("GetYourHelp", fontWeight = FontWeight.Bold) },
                    actions = { TextButton(onClick = { vm.signOut() }) { Text("Sign out") } }
                )
            },
            bottomBar = {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        val icon = when (tab) {
                            MainTab.BROWSE -> Icons.Default.Home
                            MainTab.CREATE -> Icons.Default.Add
                            MainTab.MY_TASKS -> Icons.Default.Work
                            MainTab.WALLET -> Icons.Default.AccountBalanceWallet
                            MainTab.PROFILE -> Icons.Default.Person
                        }
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab; if (tab == MainTab.WALLET || tab == MainTab.MY_TASKS) vm.refreshMyData() },
                            icon = { Icon(icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                state.message?.let { MessageBanner(it, isError = false) { vm.clearMessage() } }
                state.error?.let { MessageBanner(it, isError = true) { vm.clearMessage() } }
                when (selectedTab) {
                    MainTab.BROWSE -> BrowseScreen(state.tasks, state.user!!, onTask = { selectedTask = it })
                    MainTab.CREATE -> CreateTaskScreen(state.busy, onCreate = { title, description, category, type, amount, methods, location ->
                        vm.createTask(title, description, category, type, amount, methods, location)
                    })
                    MainTab.MY_TASKS -> MyTasksScreen(state.tasks, state.myApplications, state.user!!, onTask = { selectedTask = it })
                    MainTab.WALLET -> WalletScreen(state.payments, state.user!!, state.busy, vm::markPayment)
                    MainTab.PROFILE -> ProfileScreen(state.user!!, state.busy, vm::updateCategories)
                }
            }
        }
    }

    selectedTask?.let { selected ->
        val activeTask = state.tasks.firstOrNull { it.id == selected.id } ?: selected
        TaskDetailDialog(
            task = activeTask,
            user = state.user,
            applications = state.selectedTaskApplications,
            busy = state.busy,
            onLoadApplications = { vm.loadApplications(activeTask) },
            onApply = { method -> vm.apply(activeTask, method) },
            onAccept = { app -> vm.accept(activeTask, app) },
            onComplete = { vm.complete(activeTask) },
            onReport = { reason, details -> vm.report(activeTask, reason, details) },
            onReview = { rating, comment -> vm.review(activeTask, rating, comment) },
            onDismiss = { selectedTask = null }
        )
    }
}

@Composable
private fun MessageBanner(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Surface(color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

/** Email and password sign-in, with a switch to create a new account. */
@Composable
private fun LoginScreen(
    busy: Boolean,
    error: String?,
    onSignIn: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit
) {
    var registerMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        Text("GetYourHelp", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Connect people who need a hand with people ready to help.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Text(if (registerMode) "Create account" else "Sign in", style = MaterialTheme.typography.titleLarge)
        if (registerMode) OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { if (registerMode) onRegister(name, email, password) else onSignIn(email, password) },
            enabled = !busy && email.isNotBlank() && password.length >= 6 && (!registerMode || name.isNotBlank()),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Please wait…" else if (registerMode) "Create account" else "Sign in") }
        TextButton(onClick = { registerMode = !registerMode }) {
            Text(if (registerMode) "Already have an account? Sign in" else "New here? Create an account")
        }
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Open tasks published by other users, with a category filter. */
@Composable
private fun BrowseScreen(tasks: List<TaskItem>, user: AppUser, onTask: (TaskItem) -> Unit) {
    var category by remember { mutableStateOf("All") }
    val categories = listOf("All") + tasks.map { it.category }.distinct().sorted()
    val visible = tasks.filter { it.status == "OPEN" && it.ownerId != user.uid && (category == "All" || it.category == category) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Available tasks", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Browse paid jobs and volunteering opportunities.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            categories.take(4).forEach { value ->
                FilterChip(selected = category == value, onClick = { category = value }, label = { Text(value) })
            }
        }
        if (visible.isEmpty()) EmptyState("No tasks found", "Try another category, or ask someone to publish a task.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(visible, key = { it.id }) { task -> TaskCard(task, onClick = { onTask(task) }) }
        }
    }
}

@Composable
private fun TaskCard(task: TaskItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                StatusTag(task.status)
            }
            Spacer(Modifier.height(4.dp))
            Text("${task.category} · ${if (task.taskType == "PAID") "Paid" else "Volunteering"}", style = MaterialTheme.typography.bodySmall)
            Text(task.location, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text(if (task.taskType == "PAID") "€${"%.2f".format(task.amount)}" else "Volunteer opportunity",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Posted by ${task.ownerName}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StatusTag(status: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Text(status.replace('_', ' '), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

/** Form to publish a task. The button stays disabled until the required fields are valid. */
@Composable
private fun CreateTaskScreen(
    busy: Boolean,
    onCreate: (String, String, String, String, String, List<String>, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Pet Care") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf("PAID") }
    var amount by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var cash by remember { mutableStateOf(true) }
    var bank by remember { mutableStateOf(false) }
    val categories = listOf("Pet Care", "Gardening", "Shopping", "Home Help", "Moving", "Cleaning", "Volunteering", "Other")
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Publish a task", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(title, { title = it }, label = { Text("Task title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Box {
            OutlinedTextField(category, {}, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), readOnly = true)
            Box(Modifier.matchParentSize().clickable { categoryExpanded = true })
            DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                categories.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { category = it; categoryExpanded = false }) }
            }
        }
        OutlinedTextField(location, { location = it }, label = { Text("Location (town / area)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text("Task type", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type == "PAID", onClick = { type = "PAID" }, label = { Text("Paid task") })
            FilterChip(selected = type == "VOLUNTEER", onClick = { type = "VOLUNTEER"; amount = ""; cash = false; bank = false }, label = { Text("Volunteer") })
        }
        if (type == "PAID") {
            OutlinedTextField(amount, { amount = it }, label = { Text("Offered amount (€)") }, modifier = Modifier.fillMaxWidth())
            Text("Accepted payment methods", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(cash, { cash = it }); Text("Cash") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(bank, { bank = it }); Text("Bank transfer") }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onCreate(title, description, category, type, amount, buildList { if (cash) add("CASH"); if (bank) add("BANK_TRANSFER") }, location) },
            enabled = !busy && title.isNotBlank() && description.isNotBlank() && location.isNotBlank() &&
                (type == "VOLUNTEER" || ((amount.toDoubleOrNull() ?: 0.0) > 0 && (cash || bank))),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Publishing…" else "Publish task") }
    }
}

/** Tasks the user published, and tasks they applied for or were assigned to. */
@Composable
private fun MyTasksScreen(tasks: List<TaskItem>, applications: List<TaskApplication>, user: AppUser, onTask: (TaskItem) -> Unit) {
    val ownTasks = tasks.filter { it.ownerId == user.uid }
    val appliedTaskIds = applications.map { it.taskId }.toSet()
    val helperTasks = tasks.filter { it.id in appliedTaskIds || it.assignedHelperId == user.uid }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("My activity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Tasks you published or applied for.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Text("Tasks I published", style = MaterialTheme.typography.titleMedium)
        if (ownTasks.isEmpty()) Text("You have not published any tasks yet.", style = MaterialTheme.typography.bodySmall)
        ownTasks.forEach { task -> TaskCard(task) { onTask(task) }; Spacer(Modifier.height(8.dp)) }
        Spacer(Modifier.height(10.dp))
        Text("Tasks I applied for", style = MaterialTheme.typography.titleMedium)
        if (helperTasks.isEmpty()) Text("You have not applied for any tasks yet.", style = MaterialTheme.typography.bodySmall)
        helperTasks.forEach { task -> TaskCard(task) { onTask(task) }; Spacer(Modifier.height(8.dp)) }
    }
}

/** Payment records for the user's paid tasks. Record keeping only; no money moves through the app. */
@Composable
private fun WalletScreen(payments: List<PaymentRecord>, user: AppUser, busy: Boolean, onMarkPaid: (PaymentRecord) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Wallet & payment history", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Record keeping only. Payments happen outside GetYourHelp.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        if (payments.isEmpty()) EmptyState("No payment records", "A record is created when a requester accepts an application for a paid task.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(payments, key = { it.id }) { payment ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(payment.taskTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("€${"%.2f".format(payment.amount)} · ${payment.method.replace('_', ' ')}")
                        Text("Participants: ${payment.otherPartyName}", style = MaterialTheme.typography.bodySmall)
                        Text("Status: ${payment.status.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall)
                        if (payment.status != "MARKED_PAID") {
                            OutlinedButton(onClick = { onMarkPaid(payment) }, enabled = !busy) { Text("Mark as paid (record only)") }
                        }
                    }
                }
            }
        }
    }
}

/** Profile details and the categories of work the helper is interested in. */
@Composable
private fun ProfileScreen(user: AppUser, busy: Boolean, onSaveCategories: (List<String>) -> Unit) {
    val categories = listOf("Pet Care", "Gardening", "Shopping", "Home Help", "Moving", "Cleaning", "Other")
    var selected by remember(user.uid, user.helperCategories) { mutableStateOf(user.helperCategories.toSet()) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(user.displayName, style = MaterialTheme.typography.titleLarge)
        Text(user.email, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(18.dp))
        Text("Categories I'm interested in", style = MaterialTheme.typography.titleMedium)
        categories.forEach { category ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = category in selected, onCheckedChange = { checked -> selected = if (checked) selected + category else selected - category })
                Text(category)
            }
        }
        Button(onClick = { onSaveCategories(selected.toList()) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save categories") }
    }
}

/**
 * Task details. The actions shown depend on who is looking and on the task status:
 * - owner, OPEN: the list of applications, each with "Accept applicant"
 * - anyone else, OPEN: choose a payment method and apply
 * - owner, ASSIGNED: "Confirm task completed"
 * - owner or assigned helper, COMPLETED: "Leave a review"
 * - anyone except the owner: "Report this task"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailDialog(
    task: TaskItem,
    user: AppUser?,
    applications: List<TaskApplication>,
    busy: Boolean,
    onLoadApplications: () -> Unit,
    onApply: (String) -> Unit,
    onAccept: (TaskApplication) -> Unit,
    onComplete: () -> Unit,
    onReport: (String, String) -> Unit,
    onReview: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    if (user == null) return
    var preferredMethod by remember(task.id) { mutableStateOf(task.acceptedPaymentMethods.firstOrNull().orEmpty()) }
    var showReport by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("Unsafe or potentially dangerous") }
    var reportDetails by remember { mutableStateOf("") }
    var reviewRating by remember { mutableIntStateOf(5) }
    var reviewComment by remember { mutableStateOf("") }
    val isOwner = task.ownerId == user.uid

    LaunchedEffect(task.id, isOwner) { if (isOwner) onLoadApplications() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(task.title) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                StatusTag(task.status)
                Spacer(Modifier.height(8.dp))
                Text(task.description)
                Spacer(Modifier.height(8.dp))
                Text("Category: ${task.category}")
                Text("Location: ${task.location}")
                Text("Requester: ${task.ownerName}")
                Text(if (task.taskType == "PAID") "Offered: €${"%.2f".format(task.amount)}" else "Volunteer opportunity")
                if (task.taskType == "PAID") Text("Accepted: ${task.acceptedPaymentMethods.joinToString { it.replace('_', ' ') }}")
                if (task.status == "ASSIGNED") Text("Accepted helper: ${task.assignedHelperName}")
                if (isOwner && task.status == "OPEN") {
                    Spacer(Modifier.height(12.dp))
                    Text("Applications", style = MaterialTheme.typography.titleMedium)
                    if (applications.isEmpty()) Text("No applications yet.", style = MaterialTheme.typography.bodySmall)
                    applications.forEach { application ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(10.dp)) {
                                Text(application.helperName, fontWeight = FontWeight.SemiBold)
                                Text("Preferred payment: ${application.preferredPaymentMethod.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall)
                                Text("Status: ${application.status}", style = MaterialTheme.typography.bodySmall)
                                if (application.status == "PENDING") {
                                    Button(onClick = { onAccept(application) }, enabled = !busy) { Text("Accept applicant") }
                                }
                            }
                        }
                    }
                }
                if (!isOwner && task.status == "OPEN") {
                    Spacer(Modifier.height(12.dp))
                    Text("Choose your preferred payment method", style = MaterialTheme.typography.titleSmall)
                    if (task.taskType == "VOLUNTEER") Text("This is an unpaid volunteering opportunity.")
                    else task.acceptedPaymentMethods.forEach { method ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = preferredMethod == method, onClick = { preferredMethod = method })
                            Text(method.replace('_', ' '))
                        }
                    }
                    Button(onClick = { onApply(if (task.taskType == "VOLUNTEER") "NOT_APPLICABLE" else preferredMethod) }, enabled = !busy && (task.taskType == "VOLUNTEER" || preferredMethod.isNotBlank()), modifier = Modifier.fillMaxWidth()) {
                        Text("Apply for this task")
                    }
                }
                if (isOwner && task.status == "ASSIGNED") {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onComplete, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Confirm task completed") }
                }
                if (task.status == "COMPLETED" && user.uid in listOf(task.ownerId, task.assignedHelperId)) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showReview = true }, modifier = Modifier.fillMaxWidth()) { Text("Leave a review") }
                }
                if (!isOwner) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showReport = true }) { Text("Report this task") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )

    if (showReport) AlertDialog(
        onDismissRequest = { showReport = false },
        title = { Text("Report task") },
        text = {
            Column {
                listOf("Unsafe or potentially dangerous", "Misleading information", "Prohibited task", "Incorrect payment information", "Other").forEach { reason ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reportReason == reason, onClick = { reportReason = reason })
                        Text(reason, style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(reportDetails, { reportDetails = it }, label = { Text("Additional details (optional)") }, minLines = 2)
            }
        },
        confirmButton = { Button(onClick = { onReport(reportReason, reportDetails); showReport = false }) { Text("Submit report") } },
        dismissButton = { TextButton(onClick = { showReport = false }) { Text("Cancel") } }
    )

    if (showReview) AlertDialog(
        onDismissRequest = { showReview = false },
        title = { Text("Review the other person") },
        text = {
            Column {
                Text("Rating: $reviewRating / 5")
                Slider(value = reviewRating.toFloat(), onValueChange = { reviewRating = it.toInt().coerceIn(1, 5) }, valueRange = 1f..5f, steps = 3)
                OutlinedTextField(reviewComment, { reviewComment = it }, label = { Text("Comment (optional)") }, minLines = 3)
            }
        },
        confirmButton = { Button(onClick = { onReview(reviewRating, reviewComment); showReview = false }) { Text("Submit review") } },
        dismissButton = { TextButton(onClick = { showReview = false }) { Text("Cancel") } }
    )
}

@Composable
private fun EmptyState(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodySmall)
    }
}
