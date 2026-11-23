package com.vitorfreitas.getyourhelp.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Profile details, the categories of work the helper is interested in, and sign out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: AppUser,
    busy: Boolean,
    onSaveCategories: (List<TaskCategory>) -> Unit,
    onSignOut: () -> Unit
) {
    var selected by rememberSaveable(user.uid, user.helperCategories) { mutableStateOf(user.helperCategories.toSet()) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                actions = { TextButton(onClick = onSignOut, enabled = !busy) { Text("Sign out") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(user.displayName, style = MaterialTheme.typography.titleLarge)
            Text(user.email, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(18.dp))
            Text("Categories I'm interested in", style = MaterialTheme.typography.titleMedium)
            TaskCategory.entries.forEach { category ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = category in selected,
                        onCheckedChange = { checked -> selected = if (checked) selected + category else selected - category }
                    )
                    Text(category.label)
                }
            }
            Button(
                // Saved in the same order as the list on screen
                onClick = { onSaveCategories(TaskCategory.entries.filter { it in selected }) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save categories") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
