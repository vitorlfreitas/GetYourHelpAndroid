package com.vitorfreitas.getyourhelp.ui.browse

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Open tasks published by other users, with a category filter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    tasks: List<TaskItem>,
    tasksLoaded: Boolean,
    user: AppUser,
    onOpenTask: (TaskItem) -> Unit
) {
    // null means "All categories"
    var category by rememberSaveable { mutableStateOf<TaskCategory?>(null) }
    val visible = tasks.filter {
        it.status == TaskStatus.OPEN && it.ownerId != user.uid && (category == null || it.category == category)
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Available tasks") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text("Browse paid jobs and volunteering opportunities.", style = MaterialTheme.typography.bodySmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)
            ) {
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
                TaskCategory.entries.forEach { value ->
                    FilterChip(selected = category == value, onClick = { category = value }, label = { Text(value.label) })
                }
            }
            when {
                !tasksLoaded -> LoadingIndicator()
                visible.isEmpty() -> EmptyState("No tasks found", "Try another category, or ask someone to publish a task.")
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(visible, key = { it.id }) { task -> TaskCard(task, onClick = { onOpenTask(task) }) }
                }
            }
        }
    }
}
