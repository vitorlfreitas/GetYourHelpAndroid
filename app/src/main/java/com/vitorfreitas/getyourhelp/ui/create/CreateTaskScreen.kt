package com.vitorfreitas.getyourhelp.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorfreitas.getyourhelp.data.PaymentMethod
import com.vitorfreitas.getyourhelp.data.TaskCategory
import com.vitorfreitas.getyourhelp.data.TaskType
import com.vitorfreitas.getyourhelp.ui.components.icon
import com.vitorfreitas.getyourhelp.validation.TaskInput
import com.vitorfreitas.getyourhelp.validation.TaskValidator

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * Form to publish a task. The fields are checked by [TaskValidator]: after the first tap on
 * "Publish", each invalid field shows what is wrong under it, and the message updates as the user types.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateTaskScreen(busy: Boolean, onPublish: (TaskInput) -> Unit, onBack: () -> Unit) {
    // rememberSaveable keeps what was typed when the phone is rotated
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(TaskCategory.PET_CARE) }
    var location by rememberSaveable { mutableStateOf("") }
    var scheduledAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var type by rememberSaveable { mutableStateOf(TaskType.PAID) }
    var amount by rememberSaveable { mutableStateOf("") }
    var cash by rememberSaveable { mutableStateOf(true) }
    var bank by rememberSaveable { mutableStateOf(false) }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    val input = TaskInput(
        title = title,
        description = description,
        category = category,
        location = location,
        type = type,
        amount = amount,
        paymentMethods = buildSet {
            if (cash) add(PaymentMethod.CASH)
            if (bank) add(PaymentMethod.BANK_TRANSFER)
        },
        scheduledAt = scheduledAt
    )
    val validation = TaskValidator.validate(input)
    fun shown(error: String?) = error.takeIf { showErrors }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post a task") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding() // keeps the focused field above the keyboard
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionTitle("What do you need help with?")
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                placeholder = { Text("e.g. Walk my dog on Saturday") },
                isError = shown(validation.title) != null,
                supportingText = { Text(shown(validation.title) ?: "${title.trim().length}/${TaskValidator.TITLE_MAX}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                placeholder = { Text("What needs doing, how long it takes, anything the helper should know") },
                isError = shown(validation.description) != null,
                supportingText = {
                    Text(shown(validation.description) ?: "${description.trim().length}/${TaskValidator.DESCRIPTION_MAX}")
                },
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("Category")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskCategory.entries.forEach { option ->
                    FilterChip(
                        selected = category == option,
                        onClick = { category = option },
                        label = { Text(option.label) },
                        leadingIcon = { Icon(option.icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            SectionTitle("Where and when")
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location") },
                placeholder = { Text("Town or area, e.g. Rathmines, Dublin 6") },
                leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = null) },
                isError = shown(validation.location) != null,
                // Tasks are public, so a town or area is enough; the exact address can be shared later
                supportingText = { Text(shown(validation.location) ?: "A town or area is enough. Don't enter your full address.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
            ScheduleField(
                scheduledAt = scheduledAt,
                error = shown(validation.scheduledAt),
                onScheduledAtChange = { scheduledAt = it }
            )

            SectionTitle("Payment")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TaskType.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = type == option,
                        onClick = { type = option },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = TaskType.entries.size)
                    ) { Text(if (option == TaskType.PAID) "Paid task" else "Volunteer") }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (type == TaskType.PAID) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount you will pay") },
                    prefix = { Text("€") },
                    placeholder = { Text("15.00") },
                    isError = shown(validation.amount) != null,
                    supportingText = { Text(shown(validation.amount) ?: "Paid outside the app, in person or by bank transfer.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("Accepted payment methods", style = MaterialTheme.typography.labelLarge)
                CheckboxRow(PaymentMethod.CASH.label, cash) { cash = it }
                CheckboxRow(PaymentMethod.BANK_TRANSFER.label, bank) { bank = it }
                shown(validation.paymentMethods)?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(
                    "Volunteer tasks are unpaid: helpers give their time for free.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    showErrors = true
                    if (validation.isValid) onPublish(input)
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Publish task")
                }
            }
            if (showErrors && !validation.isValid) {
                Text(
                    "Check the fields marked in red.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

/** A checkbox with its label; the whole row is tappable. */
@Composable
private fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Checkbox)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(label)
    }
}
