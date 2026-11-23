package com.vitorfreitas.getyourhelp.ui.taskdetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlin.math.roundToInt

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Reasons a user can pick when reporting a task. The chosen text is stored in the report. */
private val reportReasons = listOf(
    "Unsafe or potentially dangerous",
    "Misleading information",
    "Prohibited task",
    "Incorrect payment information",
    "Other"
)

/** Report a task with a reason and optional details. The report is stored for review; the task is not removed. */
@Composable
fun ReportTaskDialog(onSubmit: (reason: String, details: String) -> Unit, onDismiss: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf(reportReasons.first()) }
    var details by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report task") },
        text = {
            Column {
                reportReasons.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == option, onClick = { reason = option })
                        Text(option, style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(details, { details = it }, label = { Text("Additional details (optional)") }, minLines = 2)
            }
        },
        confirmButton = { Button(onClick = { onSubmit(reason, details) }) { Text("Submit report") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Rate the other person from 1 to 5 stars, with an optional comment, after the task is completed. */
@Composable
fun ReviewDialog(onSubmit: (rating: Int, comment: String) -> Unit, onDismiss: () -> Unit) {
    var rating by rememberSaveable { mutableIntStateOf(5) }
    var comment by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review the other person") },
        text = {
            Column {
                Text("Rating: $rating / 5")
                Slider(
                    value = rating.toFloat(),
                    onValueChange = { rating = it.roundToInt().coerceIn(1, 5) },
                    valueRange = 1f..5f,
                    steps = 3
                )
                OutlinedTextField(comment, { comment = it }, label = { Text("Comment (optional)") }, minLines = 3)
            }
        },
        confirmButton = { Button(onClick = { onSubmit(rating, comment) }) { Text("Submit review") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
