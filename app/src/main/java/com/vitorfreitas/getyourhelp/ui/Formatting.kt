package com.vitorfreitas.getyourhelp.ui

import android.text.format.DateUtils
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Formats an amount in euro with two decimals, e.g. "€12.50". */
fun formatEuro(amount: Double): String = "€" + String.format(Locale.getDefault(), "%.2f", amount)

/** Date and time in the phone's language and time format, e.g. "Sat 29 Nov, 14:30". */
fun formatDateTime(millis: Long): String {
    val date = Date(millis)
    val day = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(date)
    val time = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(date)
    return "$day, $time"
}

/** How long ago something happened, e.g. "Just now", "5 min. ago" or "2 days ago". Empty if unknown. */
fun formatRelativeTime(millis: Long, now: Long = System.currentTimeMillis()): String = when {
    millis <= 0L -> ""
    now - millis < DateUtils.MINUTE_IN_MILLIS -> "Just now"
    else -> DateUtils.getRelativeTimeSpanString(
        millis, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}
