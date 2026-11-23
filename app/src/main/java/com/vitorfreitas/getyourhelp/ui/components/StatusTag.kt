package com.vitorfreitas.getyourhelp.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorfreitas.getyourhelp.data.TaskStatus

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Small label showing a task's status (Open, Assigned, Completed). */
@Composable
fun StatusTag(status: TaskStatus) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Text(
            status.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
