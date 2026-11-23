package com.vitorfreitas.getyourhelp.ui.create

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorfreitas.getyourhelp.data.PaymentMethod
import com.vitorfreitas.getyourhelp.data.TaskCategory
import com.vitorfreitas.getyourhelp.data.TaskType

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Form to publish a task. The button stays disabled until the required fields are valid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    busy: Boolean,
    onCreate: (
        title: String,
        description: String,
        category: TaskCategory,
        type: TaskType,
        amount: String,
        methods: List<PaymentMethod>,
        location: String
    ) -> Unit
) {
    // rememberSaveable keeps what was typed when the phone is rotated
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(TaskCategory.PET_CARE) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf(TaskType.PAID) }
    var amount by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var cash by rememberSaveable { mutableStateOf(true) }
    var bank by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Publish a task") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Task title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                description, { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Box {
                OutlinedTextField(
                    category.label, {},
                    label = { Text("Category") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true
                )
                // Transparent layer on top of the text field, so tapping anywhere on it opens the menu
                Box(Modifier.matchParentSize().clickable { categoryExpanded = true })
                DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                    TaskCategory.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = { category = option; categoryExpanded = false }
                        )
                    }
                }
            }
            OutlinedTextField(
                location, { location = it },
                label = { Text("Location (town / area)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text("Task type", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == TaskType.PAID, onClick = { type = TaskType.PAID }, label = { Text("Paid task") })
                FilterChip(
                    selected = type == TaskType.VOLUNTEER,
                    onClick = { type = TaskType.VOLUNTEER; amount = ""; cash = false; bank = false },
                    label = { Text("Volunteer") }
                )
            }
            if (type == TaskType.PAID) {
                OutlinedTextField(amount, { amount = it }, label = { Text("Offered amount (€)") }, modifier = Modifier.fillMaxWidth())
                Text("Accepted payment methods", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(cash, { cash = it }); Text(PaymentMethod.CASH.label) }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(bank, { bank = it }); Text(PaymentMethod.BANK_TRANSFER.label) }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val methods = buildList {
                        if (cash) add(PaymentMethod.CASH)
                        if (bank) add(PaymentMethod.BANK_TRANSFER)
                    }
                    onCreate(title, description, category, type, amount, methods, location)
                },
                enabled = !busy && title.isNotBlank() && description.isNotBlank() && location.isNotBlank() &&
                    (type == TaskType.VOLUNTEER || ((amount.toDoubleOrNull() ?: 0.0) > 0 && (cash || bank))),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "Publishing…" else "Publish task") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
