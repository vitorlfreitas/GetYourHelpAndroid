package com.vitorfreitas.getyourhelp.auth

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.vitorfreitas.getyourhelp.BuildConfig
import kotlin.coroutines.cancellation.CancellationException

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** What happened when the user was asked to pick a Google account. */
sealed interface GoogleSignInResult {
    /** The user picked an account. The ID token proves it to Firebase. */
    data class Success(val idToken: String) : GoogleSignInResult

    /** The user closed the account picker. */
    data object Cancelled : GoogleSignInResult

    data class Failure(val message: String) : GoogleSignInResult
}

/**
 * "Continue with Google", using Android's Credential Manager:
 * 1. [requestIdToken] shows the system's Google account picker and returns a Google ID token
 * 2. MarketplaceRepository.signInWithGoogle exchanges the token for a Firebase Authentication session
 *
 * It needs the Google provider enabled in the Firebase console, the app's SHA-1 fingerprint added to
 * the Firebase project, and a google-services.json downloaded after those two steps.
 */
class GoogleSignInHelper(private val appContext: Context) {

    /** The OAuth "web client" id. The google-services plugin generates it from google-services.json. */
    private val webClientId: String? = findWebClientId(appContext)

    /**
     * Shows the account picker. [activityContext] must be the current activity, because Credential
     * Manager draws its picker on top of it.
     */
    suspend fun requestIdToken(activityContext: Context): GoogleSignInResult {
        val clientId = webClientId ?: return GoogleSignInResult.Failure(
            withDetails(
                "Google sign-in isn't available yet.",
                "Enable Google in Firebase Authentication, add the SHA-1, then download google-services.json again"
            )
        )
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        return try {
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type in GOOGLE_CREDENTIAL_TYPES) {
                GoogleSignInResult.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleSignInResult.Failure("Google sign-in returned an unexpected answer. Please try again.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleSignInResult.Failure("There is no Google account on this phone. Add one in Settings, then try again.")
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Google sign-in failed", e)
            GoogleSignInResult.Failure(withDetails("Google sign-in didn't work. Please try again.", e.message))
        } catch (e: GoogleIdTokenParsingException) {
            Log.w(TAG, "Could not read the Google ID token", e)
            GoogleSignInResult.Failure("Google sign-in didn't work. Please try again.")
        }
    }

    /** Forgets the account picked last time, so the picker appears again after signing out. */
    suspend fun clearCredentialState() {
        try {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear the credential state", e)
        }
    }

    private companion object {
        const val TAG = "GoogleSignIn"

        /** The button flow (GetSignInWithGoogleOption) may answer with either of these types. */
        val GOOGLE_CREDENTIAL_TYPES = setOf(
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL
        )

        /** Debug builds add the technical reason, which helps while setting up Firebase. */
        fun withDetails(message: String, details: String?): String =
            if (BuildConfig.DEBUG && !details.isNullOrBlank()) "$message ($details)" else message
    }
}

/**
 * Reads the generated string resource "default_web_client_id" by name. It only exists once Google
 * sign-in is set up in Firebase, so referring to it as R.string.default_web_client_id would stop the
 * app from compiling until then.
 */
@SuppressLint("DiscouragedApi")
private fun findWebClientId(context: Context): String? {
    val resourceId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    return if (resourceId == 0) null else context.getString(resourceId).takeIf { it.isNotBlank() }
}
