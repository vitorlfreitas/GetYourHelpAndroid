package com.vitorfreitas.getyourhelp.validation

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * Checks for the sign-in and registration forms.
 * Each function returns an error message to show under the field, or null when the value is fine.
 * Plain Kotlin (no Android classes).
 */
object AuthValidator {
    /** Firebase Authentication rejects shorter passwords. */
    const val MIN_PASSWORD_LENGTH = 6
    const val MAX_NAME_LENGTH = 50

    private val emailPattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun name(value: String): String? = when {
        value.isBlank() -> "Enter your name."
        value.trim().length > MAX_NAME_LENGTH -> "Use at most $MAX_NAME_LENGTH characters."
        else -> null
    }

    fun email(value: String): String? = when {
        value.isBlank() -> "Enter your email address."
        !emailPattern.matches(value.trim()) -> "Enter a valid email address, like name@example.com."
        else -> null
    }

    /** Sign-in only needs a password; the length rule applies when an account is created. */
    fun signInPassword(value: String): String? =
        if (value.isEmpty()) "Enter your password." else null

    fun newPassword(value: String): String? = when {
        value.isEmpty() -> "Choose a password."
        value.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters."
        else -> null
    }

    fun confirmPassword(password: String, confirmation: String): String? = when {
        confirmation.isEmpty() -> "Type the password again."
        confirmation != password -> "The passwords don't match."
        else -> null
    }
}
