package com.vitorfreitas.getyourhelp.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Asks the user to confirm an action that is hard to undo, such as accepting a helper. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
