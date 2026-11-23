package com.vitorfreitas.getyourhelp.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorfreitas.getyourhelp.data.AppUser
import com.vitorfreitas.getyourhelp.data.TaskCategory
import com.vitorfreitas.getyourhelp.ui.components.InitialsAvatar
import com.vitorfreitas.getyourhelp.ui.components.icon
import com.vitorfreitas.getyourhelp.ui.theme.AppTheme

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Profile details, the categories of work the user is interested in as a helper, and sign out. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    user: AppUser,
    busy: Boolean,
    onSaveCategories: (List<TaskCategory>) -> Unit,
    onSignOut: () -> Unit
) {
    var selected by rememberSaveable(user.uid, user.helperCategories) { mutableStateOf(user.helperCategories.toSet()) }
    val changed = selected != user.helperCategories.toSet()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                actions = { TextButton(onClick = onSignOut, enabled = !busy) { Text("Sign out") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = AppTheme.extendedColors.card), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    InitialsAvatar(user.displayName, size = 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(user.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Tasks I'd like to help with", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pick the kinds of tasks you're interested in. We use them to point you to tasks you'll like.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskCategory.entries.forEach { category ->
                    val isSelected = category in selected
                    FilterChip(
                        selected = isSelected,
                        onClick = { selected = if (isSelected) selected - category else selected + category },
                        label = { Text(category.label) },
                        leadingIcon = {
                            Icon(
                                if (isSelected) Icons.Filled.Check else category.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                // Saved in the same order as the chips on screen
                onClick = { onSaveCategories(TaskCategory.entries.filter { it in selected }) },
                enabled = !busy && changed,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (changed) "Save interests" else "Interests saved") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
