package com.vitorfreitas.getyourhelp.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

// Firebase errors are technical ("The supplied auth credential is incorrect, malformed or has expired.").
// These functions turn them into short messages a user can act on.

/** Message for a failed sign-in, registration or password reset. */
fun authErrorMessage(error: Throwable): String = when (error) {
    is FirebaseNetworkException -> NO_CONNECTION
    is FirebaseTooManyRequestsException -> TOO_MANY_ATTEMPTS
    is FirebaseAuthException -> authErrorMessageForCode(error.errorCode)
    else -> error.message ?: GENERIC_ERROR
}

/**
 * Message for a Firebase Authentication error code.
 * With email enumeration protection, a wrong password and
 * an unknown email both return ERROR_INVALID_CREDENTIAL, so the message does not say which one it was.
 */
fun authErrorMessageForCode(code: String): String = when (code) {
    "ERROR_INVALID_EMAIL" -> "That email address doesn't look right."
    "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> "Wrong email or password."
    "ERROR_USER_DISABLED" -> "This account has been disabled."
    "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists. Try signing in instead."
    "ERROR_WEAK_PASSWORD" -> "That password is too weak. Use at least 6 characters."
    "ERROR_TOO_MANY_REQUESTS" -> TOO_MANY_ATTEMPTS
    "ERROR_NETWORK_REQUEST_FAILED" -> NO_CONNECTION
    "ERROR_OPERATION_NOT_ALLOWED" -> "Email sign-in is not enabled for this app yet."
    else -> "Something went wrong ($code). Please try again."
}

/** Message for any other failed action, such as saving or loading data. */
fun errorMessage(error: Throwable): String = when {
    error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        "That isn't allowed right now. The task may have changed, so check its current status."
    error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.UNAVAILABLE -> NO_CONNECTION
    error is FirebaseNetworkException -> NO_CONNECTION
    else -> error.message ?: GENERIC_ERROR
}

private const val NO_CONNECTION = "No internet connection. Check your connection and try again."
private const val TOO_MANY_ATTEMPTS = "Too many attempts. Wait a moment and try again."
private const val GENERIC_ERROR = "Something went wrong. Please try again."
