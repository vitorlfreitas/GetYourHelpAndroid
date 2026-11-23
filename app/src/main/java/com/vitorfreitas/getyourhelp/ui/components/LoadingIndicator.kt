package com.vitorfreitas.getyourhelp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** A spinner centred in the available space, shown while data is loading. */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
