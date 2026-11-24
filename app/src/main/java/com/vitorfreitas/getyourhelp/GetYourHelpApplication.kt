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

/** Created once when the app starts (registered in AndroidManifest.xml). Owns the [AppContainer]. */
class GetYourHelpApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/**
 * The objects shared by the whole app, created once (simple manual dependency injection).
 * ViewModels get them through their factories: Firebase, the snackbar queue, GPS and Google sign-in.
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
