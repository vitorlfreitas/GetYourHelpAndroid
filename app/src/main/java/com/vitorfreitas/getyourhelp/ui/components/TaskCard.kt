package com.vitorfreitas.getyourhelp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.data.TaskType
import com.vitorfreitas.getyourhelp.ui.formatEuro

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Summary of a task in a list. Tapping it opens the task details. */
@Composable
fun TaskCard(task: TaskItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                StatusTag(task.status)
            }
            Spacer(Modifier.height(4.dp))
            Text("${task.category.label} · ${task.taskType.label}", style = MaterialTheme.typography.bodySmall)
            Text(task.location, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text(
                if (task.taskType == TaskType.PAID) formatEuro(task.amount) else "Volunteer opportunity",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text("Posted by ${task.ownerName}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
