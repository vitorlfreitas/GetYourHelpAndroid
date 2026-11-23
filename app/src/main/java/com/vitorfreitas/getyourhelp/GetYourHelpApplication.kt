package com.vitorfreitas.getyourhelp

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorfreitas.getyourhelp.auth.GoogleSignInHelper
import com.vitorfreitas.getyourhelp.data.MarketplaceRepository
import com.vitorfreitas.getyourhelp.location.LocationService
import com.vitorfreitas.getyourhelp.ui.UserMessenger

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * The Android application object, created once when the app starts (registered in AndroidManifest.xml).
 * It owns the [AppContainer] shared by all screens.
 */
class GetYourHelpApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/**
 * Objects shared by the whole app, created only once (simple manual dependency injection).
 * ViewModels receive them through their factories instead of creating their own copies.
 * - repository: Firebase Authentication and Cloud Firestore
 * - messenger: the queue of snackbar messages
 * - location: the GPS sensor and place-name lookups
 * - googleSignIn: the Google account picker for "Continue with Google"
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val repository = MarketplaceRepository()
    val messenger = UserMessenger()
    val location = LocationService(appContext)
    val googleSignIn = GoogleSignInHelper(appContext)
}

/** Gives a ViewModel factory access to the [AppContainer]. */
fun CreationExtras.appContainer(): AppContainer =
    (checkNotNull(this[APPLICATION_KEY]) as GetYourHelpApplication).container
