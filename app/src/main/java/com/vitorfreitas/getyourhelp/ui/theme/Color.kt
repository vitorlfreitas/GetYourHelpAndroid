package com.vitorfreitas.getyourhelp.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

// Colour palette, built around the brand teal #245A73 (Material 3 colour roles).
// - primary (teal): main actions, selected items, "Ongoing" status
// - tertiary (amber): money, e.g. the amount offered for a paid task
// - success (green, an extra colour below): volunteering and the "Open" status

val LightColors = lightColorScheme(
    primary = Color(0xFF245A73),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC6E7FF),
    onPrimaryContainer = Color(0xFF001E2D),
    secondary = Color(0xFF4E616C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E5F3),
    onSecondaryContainer = Color(0xFF0A1E28),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBD),
    onTertiaryContainer = Color(0xFF2C1600),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFDCE3E9),
    onSurfaceVariant = Color(0xFF41484D),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC0C7CD),
    inverseSurface = Color(0xFF2D3135),
    inverseOnSurface = Color(0xFFEEF1F4),
    inversePrimary = Color(0xFF8FCDEE),
    surfaceDim = Color(0xFFD7DADD),
    surfaceBright = Color(0xFFF7F9FC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F7),
    surfaceContainer = Color(0xFFEBEEF1),
    surfaceContainerHigh = Color(0xFFE5E8EB),
    surfaceContainerHighest = Color(0xFFE0E3E6)
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF8FCDEE),
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF004D68),
    onPrimaryContainer = Color(0xFFC6E7FF),
    secondary = Color(0xFFB5C9D7),
    onSecondary = Color(0xFF20333D),
    secondaryContainer = Color(0xFF374954),
    onSecondaryContainer = Color(0xFFD1E5F3),
    tertiary = Color(0xFFFFB86E),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDCBD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101417),
    onBackground = Color(0xFFDFE3E7),
    surface = Color(0xFF101417),
    onSurface = Color(0xFFDFE3E7),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC0C7CD),
    outline = Color(0xFF8A9297),
    outlineVariant = Color(0xFF41484D),
    inverseSurface = Color(0xFFDFE3E7),
    inverseOnSurface = Color(0xFF2D3135),
    inversePrimary = Color(0xFF245A73),
    surfaceDim = Color(0xFF101417),
    surfaceBright = Color(0xFF363A3E),
    surfaceContainerLowest = Color(0xFF0B0F12),
    surfaceContainerLow = Color(0xFF181C20),
    surfaceContainer = Color(0xFF1C2024),
    surfaceContainerHigh = Color(0xFF262A2E),
    surfaceContainerHighest = Color(0xFF313539)
)

/**
 * Colours the Material 3 scheme does not have. Read them with `AppTheme.extendedColors`.
 * - success: green for volunteering, "Open" and money received
 * - card: background of cards (white on the light background, a raised grey in dark mode)
 */
@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val card: Color
)

val LightExtendedColors = ExtendedColors(
    success = Color(0xFF2E6B3F),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFB1F1BE),
    onSuccessContainer = Color(0xFF00210C),
    card = Color(0xFFFFFFFF)
)

val DarkExtendedColors = ExtendedColors(
    success = Color(0xFF96D5A3),
    onSuccess = Color(0xFF003919),
    successContainer = Color(0xFF13512A),
    onSuccessContainer = Color(0xFFB1F1BE),
    card = Color(0xFF1C2024)
)
