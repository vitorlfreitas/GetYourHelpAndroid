package com.vitorfreitas.getyourhelp.ui.create

import java.util.Calendar
import java.util.TimeZone

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

// The Material date picker represents a day as midnight UTC of that day, while a task's time is in
// the phone's own time zone. These helpers convert between the two (plain Kotlin).

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

/** The calendar day of [localMillis] (in the phone's time zone), as the date picker represents it. */
fun localDayAsUtcMillis(localMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
    val local = Calendar.getInstance(zone).apply { timeInMillis = localMillis }
    return Calendar.getInstance(UTC).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** Joins a day from the date picker (midnight UTC) with an hour and minute in the phone's time zone. */
fun combineDateAndTime(pickerDayUtcMillis: Long, hour: Int, minute: Int, zone: TimeZone = TimeZone.getDefault()): Long {
    val day = Calendar.getInstance(UTC).apply { timeInMillis = pickerDayUtcMillis }
    return Calendar.getInstance(zone).apply {
        clear()
        set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH), hour, minute)
    }.timeInMillis
}

/** Hour (0-23) and minute of [millis] in the phone's time zone, used to pre-fill the time picker. */
fun hourAndMinuteOf(millis: Long, zone: TimeZone = TimeZone.getDefault()): Pair<Int, Int> {
    val calendar = Calendar.getInstance(zone).apply { timeInMillis = millis }
    return calendar.get(Calendar.HOUR_OF_DAY) to calendar.get(Calendar.MINUTE)
}
