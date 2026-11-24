package com.vitorfreitas.getyourhelp.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorfreitas.getyourhelp.AppViewModel
import com.vitorfreitas.getyourhelp.auth.GoogleSignInHelper
import com.vitorfreitas.getyourhelp.auth.GoogleSignInResult
import com.vitorfreitas.getyourhelp.ui.auth.LoginScreen
import com.vitorfreitas.getyourhelp.ui.components.LoadingIndicator
import com.vitorfreitas.getyourhelp.ui.navigation.MainScaffold
import kotlinx.coroutines.launch

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * Root of the UI: a spinner while the saved session is checked, then the login screen or the app.
 * Queued messages ([UserMessenger]) are shown here one at a time, so they survive screen changes.
 */
@Composable
fun GetYourHelpApp(vm: AppViewModel, googleSignIn: GoogleSignInHelper) {
    val state by vm.state.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val current = messages.firstOrNull()
    LaunchedEffect(current?.id) {
        if (current != null) {
            snackbarHostState.showSnackbar(
                message = current.text,
                withDismissAction = true,
                duration = if (current.isError) SnackbarDuration.Long else SnackbarDuration.Short
            )
            vm.dismissMessage(current.id)
        }
    }

    // The Google account picker needs the activity, which a ViewModel must not keep, so it runs here
    val activityContext = LocalContext.current
    val scope = rememberCoroutineScope()
    var choosingAccount by remember { mutableStateOf(false) }
    fun continueWithGoogle() {
        if (choosingAccount) return
        choosingAccount = true
        vm.clearAuthError()
        scope.launch {
            val result = try {
                googleSignIn.requestIdToken(activityContext)
            } finally {
                choosingAccount = false
            }
            when (result) {
                is GoogleSignInResult.Success -> vm.signInWithGoogle(result.idToken)
                is GoogleSignInResult.Failure -> vm.showAuthError(result.message)
                GoogleSignInResult.Cancelled -> Unit
            }
        }
    }

    val user = state.user
    when {
        state.checkingSession -> LoadingIndicator()
        user == null -> Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            LoginScreen(
                busy = state.busy,
                googleBusy = state.googleBusy || choosingAccount,
                error = state.authError,
                onSignIn = vm::signIn,
                onRegister = vm::register,
                onGoogleSignIn = ::continueWithGoogle,
                onResetPassword = vm::sendPasswordReset,
                onClearError = vm::clearAuthError,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding()
            )
        }
        else -> MainScaffold(vm, user, snackbarHostState)
    }
}
