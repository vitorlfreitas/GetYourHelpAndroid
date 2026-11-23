package com.vitorfreitas.getyourhelp.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorfreitas.getyourhelp.validation.AuthValidator

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * Sign in, or create an account. Field errors appear after the first attempt to submit, and then
 * update as the user types. [error] is the reason the last attempt failed (e.g. a wrong password).
 */
@Composable
fun LoginScreen(
    busy: Boolean,
    error: String?,
    onSignIn: (email: String, password: String) -> Unit,
    onRegister: (name: String, email: String, password: String) -> Unit,
    onResetPassword: (email: String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Plain remember on purpose: passwords should not be written into the saved instance state.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    val nameError = if (registerMode) AuthValidator.name(name) else null
    val emailError = AuthValidator.email(email)
    val passwordError = if (registerMode) AuthValidator.newPassword(password) else AuthValidator.signInPassword(password)
    val confirmationError = if (registerMode) AuthValidator.confirmPassword(password, confirmation) else null
    val formValid = listOf(nameError, emailError, passwordError, confirmationError).all { it == null }

    fun submit() {
        showErrors = true
        if (!formValid || busy) return
        if (registerMode) onRegister(name, email, password) else onSignIn(email, password)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight) // fills the screen, so the form is centred when it is short
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(80.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.VolunteerActivism,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("GetYourHelp", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Connect people who need a hand with people ready to help.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Text(
                if (registerMode) "Create your account" else "Sign in",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            if (registerMode) {
                AuthTextField(
                    value = name,
                    onValueChange = { name = it; onClearError() },
                    label = "Name",
                    icon = Icons.Outlined.Person,
                    error = nameError.takeIf { showErrors },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
                )
            }
            AuthTextField(
                value = email,
                onValueChange = { email = it; onClearError() },
                label = "Email",
                icon = Icons.Outlined.Email,
                error = emailError.takeIf { showErrors },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )
            PasswordTextField(
                value = password,
                onValueChange = { password = it; onClearError() },
                label = "Password",
                error = passwordError.takeIf { showErrors },
                visible = passwordVisible,
                onToggleVisible = { passwordVisible = !passwordVisible },
                imeAction = if (registerMode) ImeAction.Next else ImeAction.Done,
                onDone = ::submit
            )
            if (registerMode) {
                PasswordTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it; onClearError() },
                    label = "Confirm password",
                    error = confirmationError.takeIf { showErrors },
                    visible = passwordVisible,
                    onToggleVisible = { passwordVisible = !passwordVisible },
                    imeAction = ImeAction.Done,
                    onDone = ::submit
                )
            } else {
                TextButton(onClick = { showResetDialog = true }, modifier = Modifier.align(Alignment.End)) {
                    Text("Forgot password?")
                }
            }
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                ErrorMessage(error)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = ::submit, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (registerMode) "Create account" else "Sign in")
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { registerMode = !registerMode; showErrors = false; onClearError() }) {
                Text(if (registerMode) "Already have an account? Sign in" else "New here? Create an account")
            }
        }
    }

    if (showResetDialog) {
        ResetPasswordDialog(
            initialEmail = email,
            onSend = { address -> onResetPassword(address); showResetDialog = false },
            onDismiss = { showResetDialog = false }
        )
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    error: String?,
    keyboardOptions: KeyboardOptions
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    imeAction: ImeAction,
    onDone: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password"
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Why the last sign-in or registration failed, in a red box under the form. */
@Composable
private fun ErrorMessage(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Asks for the email address that should receive a password reset link. */
@Composable
private fun ResetPasswordDialog(initialEmail: String, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var showError by remember { mutableStateOf(false) }
    val error = AuthValidator.email(email).takeIf { showError }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
        title = { Text("Reset your password") },
        text = {
            Column {
                Text("We'll email you a link to choose a new password.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    isError = error != null,
                    supportingText = if (error != null) { { Text(error) } } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showError = true
                if (AuthValidator.email(email) == null) onSend(email)
            }) { Text("Send link") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
