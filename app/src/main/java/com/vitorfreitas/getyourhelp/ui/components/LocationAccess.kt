package com.vitorfreitas.getyourhelp.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vitorfreitas.getyourhelp.location.LOCATION_PERMISSIONS
import com.vitorfreitas.getyourhelp.location.hasLocationPermission

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Location permission for a screen. request() runs onGranted at once if allowed, otherwise asks first. */
class LocationAccess(val granted: Boolean, val request: () -> Unit)

/** Asks for location only when the user does something that needs it, not when the app opens. */
@Composable
fun rememberLocationAccess(onGranted: () -> Unit, onDenied: () -> Unit): LocationAccess {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }
    val currentOnGranted by rememberUpdatedState(onGranted)
    val currentOnDenied by rememberUpdatedState(onDenied)

    // Android shows its own permission dialog. The user may allow precise or only approximate location.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        granted = results.values.any { it }
        if (granted) currentOnGranted() else currentOnDenied()
    }
    // The user may change the permission in Settings while the app is in the background.
    LifecycleResumeEffect(Unit) {
        granted = hasLocationPermission(context)
        onPauseOrDispose { }
    }
    return LocationAccess(
        granted = granted,
        request = {
            if (hasLocationPermission(context)) currentOnGranted() else launcher.launch(LOCATION_PERMISSIONS)
        }
    )
}
