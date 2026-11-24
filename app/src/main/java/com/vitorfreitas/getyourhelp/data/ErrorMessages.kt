package com.vitorfreitas.getyourhelp.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * Turns a technical Firebase error into a short message the user can act on.
 * [google] is true for "Continue with Google", where there is no password to blame.
 */
fun errorMessage(error: Throwable, google: Boolean = false): String = when (error) {
    is FirebaseNetworkException -> NO_CONNECTION
    is FirebaseTooManyRequestsException -> TOO_MANY_ATTEMPTS
    is FirebaseAuthException -> authErrorMessageForCode(error.errorCode, google)
    is FirebaseFirestoreException -> when (error.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "That isn't allowed right now. The task may have changed, so check its current status."
        FirebaseFirestoreException.Code.UNAVAILABLE -> NO_CONNECTION
        else -> error.message ?: GENERIC_ERROR
    }
    else -> error.message ?: GENERIC_ERROR
}

/**
 * Message for a Firebase Authentication error code. With email enumeration protection, a wrong
 * password and an unknown email give the same code, so the message does not say which it was.
 */
fun authErrorMessageForCode(code: String, google: Boolean = false): String = when (code) {
    "ERROR_INVALID_CREDENTIAL" -> if (google) "Google sign-in didn't work. Please try again." else "Wrong email or password."
    "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> "Wrong email or password."
    "ERROR_INVALID_EMAIL" -> "That email address doesn't look right."
    "ERROR_USER_DISABLED" -> "This account has been disabled."
    "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists. Try signing in instead."
    "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> "This email already has an account. Sign in with your password instead."
    "ERROR_WEAK_PASSWORD" -> "That password is too weak. Use at least 6 characters."
    "ERROR_TOO_MANY_REQUESTS" -> TOO_MANY_ATTEMPTS
    "ERROR_NETWORK_REQUEST_FAILED" -> NO_CONNECTION
    "ERROR_OPERATION_NOT_ALLOWED" -> "${if (google) "Google" else "Email"} sign-in is not enabled for this app yet."
    else -> "Something went wrong ($code). Please try again."
}

private const val NO_CONNECTION = "No internet connection. Check your connection and try again."
private const val TOO_MANY_ATTEMPTS = "Too many attempts. Wait a moment and try again."
private const val GENERIC_ERROR = "Something went wrong. Please try again."
