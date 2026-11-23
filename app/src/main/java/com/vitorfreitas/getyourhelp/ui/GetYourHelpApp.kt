package com.vitorfreitas.getyourhelp.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorfreitas.getyourhelp.AppViewModel
import com.vitorfreitas.getyourhelp.ui.auth.LoginScreen
import com.vitorfreitas.getyourhelp.ui.components.LoadingIndicator
import com.vitorfreitas.getyourhelp.ui.navigation.MainScaffold

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * Root of the UI. While the saved session is being checked it shows a spinner, then either the
 * login screen or the signed-in app ([MainScaffold]).
 *
 * Messages from the ViewModel are shown here as snackbars, so they stay visible when the
 * screen changes (for example "Signed in successfully." right after logging in).
 */
@Composable
fun GetYourHelpApp(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val user = state.user

    // While signed out, errors are shown under the login form instead of in a snackbar.
    val errorText = if (user != null) state.error else null
    val snackbarText = errorText ?: state.message
    LaunchedEffect(snackbarText) {
        if (snackbarText != null) {
            snackbarHostState.showSnackbar(
                message = snackbarText,
                withDismissAction = true,
                duration = if (snackbarText == errorText) SnackbarDuration.Long else SnackbarDuration.Short
            )
            vm.clearMessage()
        }
    }

    when {
        state.checkingSession -> LoadingIndicator()
        user == null -> Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            LoginScreen(
                busy = state.busy,
                error = state.error,
                onSignIn = vm::signIn,
                onRegister = vm::register,
                modifier = Modifier.padding(padding)
            )
        }
        else -> MainScaffold(vm = vm, userId = user.uid, snackbarHostState = snackbarHostState)
    }
}
