package com.vitorfreitas.getyourhelp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** A round avatar with the person's initials (the app has no profile photos). */
@Composable
fun InitialsAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                initialsOf(name),
                style = if (size >= 56.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/** Up to two initials, e.g. "Vitor Freitas" -> "VF", "ana" -> "A". Returns "?" for a blank name. */
fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { "?" }
