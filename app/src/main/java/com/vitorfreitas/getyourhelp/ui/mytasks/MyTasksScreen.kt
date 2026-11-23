package com.vitorfreitas.getyourhelp.ui.mytasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vitorfreitas.getyourhelp.data.AppUser
import com.vitorfreitas.getyourhelp.data.TaskApplication
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.ui.components.ApplicationStatusChip
import com.vitorfreitas.getyourhelp.ui.components.EmptyState
import com.vitorfreitas.getyourhelp.ui.components.TaskCard

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

private enum class MyTasksTab(val label: String) { REQUESTED("Requested"), HELPING("Helping") }

/**
 * The user's tasks in two tabs, as described in the proposal:
 * - Requested: tasks the user posted, as Open / Ongoing / Completed
 * - Helping: tasks the user applied to, as Applied / Ongoing / Completed
 * The lists are live, so a task moves to Ongoing as soon as a helper is accepted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTasksScreen(
    tasks: List<TaskItem>,
    applications: List<TaskApplication>,
    user: AppUser,
    onOpenTask: (TaskItem) -> Unit,
    onCreateTask: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(MyTasksTab.REQUESTED) }
    var requestedStage by rememberSaveable { mutableStateOf(TaskStage.WAITING) }
    var helpingStage by rememberSaveable { mutableStateOf(TaskStage.WAITING) }
    val requested = remember(tasks, user.uid) { requestedTasksByStage(tasks, user.uid) }
    val helping = remember(tasks, applications, user.uid) { helpingTasksByStage(tasks, applications, user.uid) }

    Scaffold(topBar = { TopAppBar(title = { Text("My tasks") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                MyTasksTab.entries.forEach { option ->
                    Tab(
                        selected = tab == option,
                        onClick = { tab = option },
                        text = { Text(option.label) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            when (tab) {
                MyTasksTab.REQUESTED -> {
                    StageChips(
                        selected = requestedStage,
                        labels = mapOf(TaskStage.WAITING to "Open", TaskStage.ONGOING to "Ongoing", TaskStage.COMPLETED to "Completed"),
                        counts = requested.mapValues { it.value.size },
                        onSelect = { requestedStage = it }
                    )
                    val list = requested.getValue(requestedStage)
                    if (list.isEmpty()) {
                        RequestedEmptyState(requestedStage, onCreateTask)
                    } else {
                        CardList(list, key = { it.id }) { task -> TaskCard(task, onClick = { onOpenTask(task) }) }
                    }
                }
                MyTasksTab.HELPING -> {
                    StageChips(
                        selected = helpingStage,
                        labels = mapOf(TaskStage.WAITING to "Applied", TaskStage.ONGOING to "Ongoing", TaskStage.COMPLETED to "Completed"),
                        counts = helping.mapValues { it.value.size },
                        onSelect = { helpingStage = it }
                    )
                    val list = helping.getValue(helpingStage)
                    if (list.isEmpty()) {
                        HelpingEmptyState(helpingStage)
                    } else {
                        CardList(list, key = { it.task.id }) { item ->
                            // In "Applied", show what happened to each application
                            val status = item.displayStatus.takeIf { helpingStage == TaskStage.WAITING }
                            TaskCard(
                                item.task,
                                onClick = { onOpenTask(item.task) },
                                badge = if (status != null) { { ApplicationStatusChip(status) } } else null
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The Open / Ongoing / Completed filter, with the number of tasks in each group. */
@Composable
private fun StageChips(
    selected: TaskStage,
    labels: Map<TaskStage, String>,
    counts: Map<TaskStage, Int>,
    onSelect: (TaskStage) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TaskStage.entries.forEach { stage ->
            FilterChip(
                selected = stage == selected,
                onClick = { onSelect(stage) },
                label = { Text("${labels.getValue(stage)} (${counts[stage] ?: 0})") }
            )
        }
    }
}

/** A scrolling list of cards. [key] identifies each item, so Compose can animate and keep scroll position correctly. */
@Composable
private fun <T> CardList(items: List<T>, key: (T) -> Any, card: @Composable (T) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = key) { item -> card(item) }
    }
}

@Composable
private fun RequestedEmptyState(stage: TaskStage, onCreateTask: () -> Unit) {
    val (icon: ImageVector, title, message) = when (stage) {
        TaskStage.WAITING -> Triple(Icons.Outlined.Inbox, "No open tasks", "Post a task and people nearby can apply to help.")
        TaskStage.ONGOING -> Triple(Icons.Outlined.HourglassEmpty, "Nothing in progress", "When you accept a helper, the task moves here.")
        TaskStage.COMPLETED -> Triple(Icons.Outlined.CheckCircle, "No completed tasks yet", "Tasks you confirm as done appear here.")
    }
    EmptyState(
        title = title,
        message = message,
        icon = icon,
        action = if (stage == TaskStage.WAITING) {
            { Button(onClick = onCreateTask) { Text("Post a task") } }
        } else null
    )
}

@Composable
private fun HelpingEmptyState(stage: TaskStage) {
    val (icon: ImageVector, title, message) = when (stage) {
        TaskStage.WAITING -> Triple(Icons.Outlined.Search, "No applications", "Find a task in Browse and apply to help.")
        TaskStage.ONGOING -> Triple(Icons.Outlined.HourglassEmpty, "No ongoing tasks", "When a requester accepts you, the task appears here.")
        TaskStage.COMPLETED -> Triple(Icons.Outlined.CheckCircle, "No completed tasks yet", "Tasks you've finished helping with appear here.")
    }
    EmptyState(title = title, message = message, icon = icon)
}
