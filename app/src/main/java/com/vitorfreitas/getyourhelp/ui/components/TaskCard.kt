package com.vitorfreitas.getyourhelp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.ui.formatDateTime
import com.vitorfreitas.getyourhelp.ui.formatRelativeTime
import com.vitorfreitas.getyourhelp.ui.theme.AppTheme

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * Summary of a task in a list: category icon, title, area, date, how far away it is, what the
 * helper gets and who posted it.
 * - [distanceKm]: distance from the user, shown as "1.2 km away" (null hides it, e.g. location unknown)
 * - [badge]: optional label for the top-right corner, e.g. the status of the user's application
 */
@Composable
fun TaskCard(
    task: TaskItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    distanceKm: Double? = null,
    badge: (@Composable () -> Unit)? = null
) {
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppTheme.extendedColors.card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                CategoryAvatar(task.category)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${task.category.label} · ${task.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    badge()
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = secondaryText, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    task.scheduledAt?.let(::formatDateTime) ?: "Flexible date",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (distanceKm != null) {
                    Spacer(Modifier.width(6.dp))
                    DistancePill(distanceKm)
                }
                Spacer(Modifier.width(6.dp))
                RewardTag(task)
            }
            Spacer(Modifier.height(10.dp))
            val posted = formatRelativeTime(task.createdAt)
            Text(
                if (posted.isEmpty()) "Posted by ${task.ownerName}" else "Posted by ${task.ownerName} · $posted",
                style = MaterialTheme.typography.labelSmall,
                color = secondaryText
            )
        }
    }
}
