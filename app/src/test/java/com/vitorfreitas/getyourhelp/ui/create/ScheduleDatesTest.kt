package com.vitorfreitas.getyourhelp.ui.create

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

class ScheduleDatesTest {
    private val dublin = TimeZone.getTimeZone("Europe/Dublin")
    private val newYork = TimeZone.getTimeZone("America/New_York")

    /** A day as the date picker gives it: midnight UTC. */
    private fun pickerDay(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(year, month, day) }.timeInMillis

    @Test
    fun combinesThePickedDayWithALocalTime() {
        val result = combineDateAndTime(pickerDay(2026, Calendar.DECEMBER, 24), 14, 30, dublin)
        val local = Calendar.getInstance(dublin).apply { timeInMillis = result }
        assertEquals(2026, local.get(Calendar.YEAR))
        assertEquals(Calendar.DECEMBER, local.get(Calendar.MONTH))
        assertEquals(24, local.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, local.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, local.get(Calendar.MINUTE))
    }

    @Test
    fun lateEveningStaysOnTheSameLocalDay() {
        // 23:30 in New York is already the next day in UTC, but the picker must show the local day
        val lateEvening = Calendar.getInstance(newYork).apply { clear(); set(2026, Calendar.MARCH, 10, 23, 30) }.timeInMillis
        assertEquals(pickerDay(2026, Calendar.MARCH, 10), localDayAsUtcMillis(lateEvening, newYork))
    }

    @Test
    fun hourAndMinuteAreReadInTheLocalTimeZone() {
        val time = combineDateAndTime(pickerDay(2026, Calendar.JULY, 1), 9, 5, dublin)
        assertEquals(9 to 5, hourAndMinuteOf(time, dublin))
    }
}
