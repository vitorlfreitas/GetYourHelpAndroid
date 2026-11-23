package com.vitorfreitas.getyourhelp.ui.mytasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorfreitas.getyourhelp.data.AppUser
import com.vitorfreitas.getyourhelp.data.TaskApplication
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.ui.components.TaskCard

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Tasks the user published, and tasks they applied for or were assigned to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTasksScreen(
    tasks: List<TaskItem>,
    applications: List<TaskApplication>,
    user: AppUser,
    onOpenTask: (TaskItem) -> Unit
) {
    val ownTasks = tasks.filter { it.ownerId == user.uid }
    val appliedTaskIds = applications.map { it.taskId }.toSet()
    val helperTasks = tasks.filter { it.id in appliedTaskIds || it.assignedHelperId == user.uid }
    Scaffold(topBar = { TopAppBar(title = { Text("My activity") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("Tasks you published or applied for.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Text("Tasks I published", style = MaterialTheme.typography.titleMedium)
            if (ownTasks.isEmpty()) Text("You have not published any tasks yet.", style = MaterialTheme.typography.bodySmall)
            ownTasks.forEach { task ->
                TaskCard(task, onClick = { onOpenTask(task) })
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text("Tasks I applied for", style = MaterialTheme.typography.titleMedium)
            if (helperTasks.isEmpty()) Text("You have not applied for any tasks yet.", style = MaterialTheme.typography.bodySmall)
            helperTasks.forEach { task ->
                TaskCard(task, onClick = { onOpenTask(task) })
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
