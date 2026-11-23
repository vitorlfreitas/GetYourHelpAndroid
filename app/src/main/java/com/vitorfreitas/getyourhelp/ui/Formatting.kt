package com.vitorfreitas.getyourhelp.ui

import java.util.Locale

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Formats an amount in euro with two decimals, e.g. "€12.50". */
fun formatEuro(amount: Double): String = "€" + String.format(Locale.getDefault(), "%.2f", amount)
