package com.vitorfreitas.getyourhelp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Message shown in place of a list that has nothing to show. */
@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}
