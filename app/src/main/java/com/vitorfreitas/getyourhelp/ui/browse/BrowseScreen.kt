package com.vitorfreitas.getyourhelp.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorfreitas.getyourhelp.data.AppUser
import com.vitorfreitas.getyourhelp.data.TaskCategory
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.data.TaskStatus
import com.vitorfreitas.getyourhelp.ui.components.EmptyState
import com.vitorfreitas.getyourhelp.ui.components.LoadingIndicator
import com.vitorfreitas.getyourhelp.ui.components.TaskCard
import com.vitorfreitas.getyourhelp.ui.components.icon

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Open tasks published by other users, with a category filter and a button to post a new task. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    tasks: List<TaskItem>,
    tasksLoaded: Boolean,
    user: AppUser,
    onOpenTask: (TaskItem) -> Unit,
    onCreateTask: () -> Unit
) {
    // null means "All categories"
    var category by rememberSaveable { mutableStateOf<TaskCategory?>(null) }
    val visible = tasks.filter {
        it.status == TaskStatus.OPEN && it.ownerId != user.uid && (category == null || it.category == category)
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Available tasks") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateTask,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Post a task") }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Hi ${user.displayName.substringBefore(' ')}! Find a task to help with, or post your own.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
                }
                items(TaskCategory.entries) { value ->
                    FilterChip(
                        selected = category == value,
                        // Tapping the selected category again goes back to "All"
                        onClick = { category = if (category == value) null else value },
                        label = { Text(value.label) },
                        leadingIcon = { Icon(value.icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
            when {
                !tasksLoaded -> LoadingIndicator()
                visible.isEmpty() -> EmptyState(
                    title = "No tasks here yet",
                    message = if (category == null) "When someone posts a task, it appears here." else "Try another category.",
                    icon = Icons.Outlined.SearchOff
                )
                else -> LazyColumn(
                    // Extra space at the bottom so the last card is not hidden behind the "Post a task" button
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visible, key = { it.id }) { task -> TaskCard(task, onClick = { onOpenTask(task) }) }
                }
            }
        }
    }
}
