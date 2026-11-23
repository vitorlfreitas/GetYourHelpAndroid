package com.vitorfreitas.getyourhelp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.vitorfreitas.getyourhelp.R

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Plus Jakarta Sans (SIL Open Font License 1.1), bundled in res/font. */
val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold)
)

private val Default = Typography()

/** The Material 3 text styles, using Plus Jakarta Sans. Titles and labels are a little bolder than the defaults. */
val AppTypography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = PlusJakartaSans),
    displayMedium = Default.displayMedium.copy(fontFamily = PlusJakartaSans),
    displaySmall = Default.displaySmall.copy(fontFamily = PlusJakartaSans),
    headlineLarge = Default.headlineLarge.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold),
    headlineMedium = Default.headlineMedium.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold),
    headlineSmall = Default.headlineSmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold),
    titleLarge = Default.titleLarge.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
    titleSmall = Default.titleSmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
    bodyLarge = Default.bodyLarge.copy(fontFamily = PlusJakartaSans),
    bodyMedium = Default.bodyMedium.copy(fontFamily = PlusJakartaSans),
    bodySmall = Default.bodySmall.copy(fontFamily = PlusJakartaSans),
    labelLarge = Default.labelLarge.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
    labelMedium = Default.labelMedium.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
    labelSmall = Default.labelSmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium)
)
