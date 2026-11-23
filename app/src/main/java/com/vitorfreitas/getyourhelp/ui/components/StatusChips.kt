package com.vitorfreitas.getyourhelp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vitorfreitas.getyourhelp.data.ApplicationStatus
import com.vitorfreitas.getyourhelp.data.PaymentStatus
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.data.TaskStatus
import com.vitorfreitas.getyourhelp.data.TaskType
import com.vitorfreitas.getyourhelp.ui.formatEuro
import com.vitorfreitas.getyourhelp.ui.theme.AppTheme

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** A small rounded label. Statuses, amounts and badges are all built from it so they look consistent. */
@Composable
fun Pill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    border: BorderStroke? = null
) {
    Surface(color = containerColor, contentColor = contentColor, shape = CircleShape, border = border, modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Task status: Open (green), Ongoing (teal), Completed (grey). */
@Composable
fun TaskStatusChip(status: TaskStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val extended = AppTheme.extendedColors
    val (container, content) = when (status) {
        TaskStatus.OPEN -> extended.successContainer to extended.onSuccessContainer
        TaskStatus.ASSIGNED -> colors.primaryContainer to colors.onPrimaryContainer
        TaskStatus.COMPLETED -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
    Pill(status.label, container, content, modifier)
}

/** Application status: Pending (neutral), Accepted (green), Declined (red), Withdrawn (grey). */
@Composable
fun ApplicationStatusChip(status: ApplicationStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val extended = AppTheme.extendedColors
    val (container, content) = when (status) {
        ApplicationStatus.PENDING -> colors.secondaryContainer to colors.onSecondaryContainer
        ApplicationStatus.ACCEPTED -> extended.successContainer to extended.onSuccessContainer
        ApplicationStatus.DECLINED -> colors.errorContainer to colors.onErrorContainer
        ApplicationStatus.WITHDRAWN -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
    Pill(status.label, container, content, modifier)
}

/** Payment record status: Pending (amber) or Marked as paid (green). */
@Composable
fun PaymentStatusChip(status: PaymentStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val extended = AppTheme.extendedColors
    val (container, content) = when (status) {
        PaymentStatus.PENDING -> colors.tertiaryContainer to colors.onTertiaryContainer
        PaymentStatus.MARKED_PAID -> extended.successContainer to extended.onSuccessContainer
    }
    Pill(status.label, container, content, modifier)
}

/**
 * What the helper gets: the amount for a paid task (amber), or "Volunteer" with a heart for an
 * unpaid task (green outline, so it does not look like the green "Open" status).
 */
@Composable
fun RewardTag(task: TaskItem, modifier: Modifier = Modifier) {
    if (task.taskType == TaskType.PAID) {
        Pill(
            formatEuro(task.amount),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = modifier
        )
    } else {
        val green = AppTheme.extendedColors.success
        Pill(
            "Volunteer",
            containerColor = Color.Transparent,
            contentColor = green,
            modifier = modifier,
            icon = Icons.Outlined.FavoriteBorder,
            border = BorderStroke(1.dp, green)
        )
    }
}
