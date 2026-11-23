package com.vitorfreitas.getyourhelp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorfreitas.getyourhelp.data.TaskCategory

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** The Material icon shown for each category. */
val TaskCategory.icon: ImageVector
    get() = when (this) {
        TaskCategory.PET_CARE -> Icons.Outlined.Pets
        TaskCategory.GARDENING -> Icons.Outlined.Yard
        TaskCategory.SHOPPING -> Icons.Outlined.ShoppingCart
        TaskCategory.HOME_HELP -> Icons.Outlined.Handyman
        TaskCategory.MOVING -> Icons.Outlined.LocalShipping
        TaskCategory.CLEANING -> Icons.Outlined.CleaningServices
        TaskCategory.VOLUNTEERING -> Icons.Outlined.VolunteerActivism
        TaskCategory.OTHER -> Icons.Outlined.Category
    }

/** The category's icon on a soft teal square. Shown at the start of task cards and on the details page. */
@Composable
fun CategoryAvatar(category: TaskCategory, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}
