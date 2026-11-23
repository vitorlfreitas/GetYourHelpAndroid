package com.vitorfreitas.getyourhelp.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Email and password sign-in, with a switch to create a new account. */
@Composable
fun LoginScreen(
    busy: Boolean,
    error: String?,
    onSignIn: (email: String, password: String) -> Unit,
    onRegister: (name: String, email: String, password: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Plain remember on purpose: a password should not be written into the saved instance state.
    var password by remember { mutableStateOf("") }
    Column(
        modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text("GetYourHelp", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Connect people who need a hand with people ready to help.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Text(if (registerMode) "Create account" else "Sign in", style = MaterialTheme.typography.titleLarge)
        if (registerMode) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            password, { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { if (registerMode) onRegister(name, email, password) else onSignIn(email, password) },
            enabled = !busy && email.isNotBlank() && password.length >= 6 && (!registerMode || name.isNotBlank()),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Please wait…" else if (registerMode) "Create account" else "Sign in") }
        TextButton(onClick = { registerMode = !registerMode }) {
            Text(if (registerMode) "Already have an account? Sign in" else "New here? Create an account")
        }
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}
