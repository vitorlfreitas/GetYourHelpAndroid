package com.vitorfreitas.getyourhelp.ui.create

import android.text.format.DateFormat
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.vitorfreitas.getyourhelp.ui.formatDateTime

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * The optional "when" of a task. It looks like a text field; tapping it opens a date picker and
 * then a time picker. The X button clears it, making the date flexible again (null).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleField(
    scheduledAt: Long?,
    error: String?,
    onScheduledAtChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var pickedDay by rememberSaveable { mutableStateOf<Long?>(null) }

    // A read-only text field does not report clicks, so listen for taps on it directly.
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) showDatePicker = true
        }
    }

    OutlinedTextField(
        value = scheduledAt?.let(::formatDateTime) ?: "Flexible",
        onValueChange = {},
        readOnly = true,
        label = { Text("Date and time (optional)") },
        leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
        trailingIcon = if (scheduledAt != null) {
            {
                IconButton(onClick = { onScheduledAtChange(null) }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear date and time")
                }
            }
        } else null,
        isError = error != null,
        supportingText = { Text(error ?: "Tap to pick a date and time, or leave it flexible.") },
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth()
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDayAsUtcMillis(scheduledAt ?: System.currentTimeMillis()),
            selectableDates = TodayOrLater
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickedDay = datePickerState.selectedDateMillis
                        showDatePicker = false
                        showTimePicker = true
                    },
                    enabled = datePickerState.selectedDateMillis != null
                ) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val (initialHour, initialMinute) = scheduledAt?.let(::hourAndMinuteOf) ?: (10 to 0)
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Pick a time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    pickedDay?.let { day -> onScheduledAtChange(combineDateAndTime(day, timePickerState.hour, timePickerState.minute)) }
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } }
        )
    }
}

/** The date picker only offers today and later days. */
@OptIn(ExperimentalMaterial3Api::class)
private object TodayOrLater : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        utcTimeMillis >= localDayAsUtcMillis(System.currentTimeMillis())
}
