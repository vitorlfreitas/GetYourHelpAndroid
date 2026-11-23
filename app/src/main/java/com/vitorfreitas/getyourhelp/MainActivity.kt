package com.vitorfreitas.getyourhelp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.vitorfreitas.getyourhelp.ui.GetYourHelpApp
import com.vitorfreitas.getyourhelp.ui.theme.GetYourHelpTheme

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid


/** The app's single activity. Every screen is a composable under [GetYourHelpApp] (see the `ui` package). */
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels { AppViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as GetYourHelpApplication).container
        setContent {
            GetYourHelpTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GetYourHelpApp(viewModel, container.googleSignIn)
                }
            }
        }
    }
}
