package com.vitorfreitas.getyourhelp.ui.chat

import com.vitorfreitas.getyourhelp.data.ChatMessage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

// How the chat screen lays out its messages. Plain Kotlin, covered by unit tests.

/** One row of the chat list: a date header, or a message bubble. [key] identifies the row in the list. */
sealed interface ChatRow {
    val key: String

    data class DayHeader(val label: String, override val key: String) : ChatRow

    data class Bubble(val message: ChatMessage, val isMine: Boolean) : ChatRow {
        override val key: String get() = message.id
    }
}

/**
 * The messages in time order, with a header ("Today", "Yesterday", "Mon 23 Nov") before the first
 * message of each day. [myId] decides which bubbles are drawn on the right.
 */
fun chatRows(
    messages: List<ChatMessage>,
    myId: String,
    now: Long = System.currentTimeMillis(),
    zone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault()
): List<ChatRow> {
    val rows = mutableListOf<ChatRow>()
    var currentDay: Long? = null
    for (message in messages) {
        val day = startOfDay(message.sentAt, zone)
        if (day != currentDay) {
            rows += ChatRow.DayHeader(dayLabel(day, now, zone, locale), key = "day-$day")
            currentDay = day
        }
        rows += ChatRow.Bubble(message, isMine = message.senderId == myId)
    }
    return rows
}

/** "Today", "Yesterday", "Mon 23 Nov" for this year, or "23 Nov 2025" for older days. */
fun dayLabel(dayStart: Long, now: Long, zone: TimeZone, locale: Locale): String {
    val today = startOfDay(now, zone)
    val yesterday = Calendar.getInstance(zone).apply { timeInMillis = today; add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
    val sameYear = Calendar.getInstance(zone).apply { timeInMillis = dayStart }.get(Calendar.YEAR) ==
        Calendar.getInstance(zone).apply { timeInMillis = now }.get(Calendar.YEAR)
    return when (dayStart) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> SimpleDateFormat(if (sameYear) "EEE d MMM" else "d MMM yyyy", locale)
            .apply { timeZone = zone }
            .format(Date(dayStart))
    }
}

/** Midnight at the start of the local day that contains [millis]. */
private fun startOfDay(millis: Long, zone: TimeZone): Long =
    Calendar.getInstance(zone).apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
