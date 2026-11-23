package com.vitorfreitas.getyourhelp

import android.app.Application
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorfreitas.getyourhelp.data.MarketplaceRepository
import com.vitorfreitas.getyourhelp.ui.UserMessenger

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * The Android application object, created once when the app starts (registered in AndroidManifest.xml).
 * It owns the [AppContainer] shared by all screens.
 */
class GetYourHelpApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }
}

/**
 * Objects shared by the whole app, created only once (simple manual dependency injection).
 * ViewModels receive them through their factories instead of creating their own copies.
 */
class AppContainer {
    val repository = MarketplaceRepository()
    val messenger = UserMessenger()
}

/** Gives a ViewModel factory access to the [AppContainer]. */
fun CreationExtras.appContainer(): AppContainer =
    (checkNotNull(this[APPLICATION_KEY]) as GetYourHelpApplication).container
