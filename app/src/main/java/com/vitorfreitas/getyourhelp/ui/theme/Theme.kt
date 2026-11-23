package com.vitorfreitas.getyourhelp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Rounder corners than the Material defaults, for a friendlier look. */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** Access to the app's extra colours, next to MaterialTheme.colorScheme. */
object AppTheme {
    val extendedColors: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}

/**
 * The app's theme: brand colours (light or dark, following the phone's setting), the
 * Plus Jakarta Sans font and rounded shapes. Every screen is drawn inside it.
 * Dynamic colour (wallpaper-based) is not used, so the app always keeps its brand colours.
 */
@Composable
fun GetYourHelpTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
