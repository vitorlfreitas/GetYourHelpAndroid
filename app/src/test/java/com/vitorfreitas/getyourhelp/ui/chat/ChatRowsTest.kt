package com.vitorfreitas.getyourhelp.ui.chat

import com.vitorfreitas.getyourhelp.data.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

class ChatRowsTest {
    private val dublin = TimeZone.getTimeZone("Europe/Dublin")
    private val uk = Locale.UK

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(dublin).apply { clear(); set(year, month, day, hour, minute) }.timeInMillis

    private val now = at(2026, Calendar.NOVEMBER, 23, 18)

    private fun message(id: String, sentAt: Long, sender: String = "other") =
        ChatMessage(id = id, senderId = sender, senderName = "Name", text = "Hello $id", sentAt = sentAt)

    private fun rows(vararg messages: ChatMessage) = chatRows(messages.toList(), myId = "me", now = now, zone = dublin, locale = uk)

    @Test
    fun aDateHeaderComesBeforeTheFirstMessageOfEachDay() {
        val rows = rows(
            message("1", at(2026, Calendar.NOVEMBER, 22, 9)),
            message("2", at(2026, Calendar.NOVEMBER, 22, 23, 59)),
            message("3", at(2026, Calendar.NOVEMBER, 23, 8))
        )
        val headers = rows.filterIsInstance<ChatRow.DayHeader>().map { it.label }
        assertEquals(listOf("Yesterday", "Today"), headers)
        assertEquals(5, rows.size)
        assertTrue(rows[0] is ChatRow.DayHeader)
        assertTrue(rows[3] is ChatRow.DayHeader)
    }

    @Test
    fun olderDaysShowTheDate() {
        assertEquals("Fri 20 Nov", dayLabel(at(2026, Calendar.NOVEMBER, 20, 0), now, dublin, uk))
        assertEquals("20 Nov 2025", dayLabel(at(2025, Calendar.NOVEMBER, 20, 0), now, dublin, uk))
    }

    @Test
    fun myMessagesAreMarkedAsMine() {
        val bubbles = rows(message("1", now - 1000, sender = "me"), message("2", now)).filterIsInstance<ChatRow.Bubble>()
        assertTrue(bubbles[0].isMine)
        assertFalse(bubbles[1].isMine)
    }

    @Test
    fun rowKeysAreUnique() {
        val rows = rows(message("1", at(2026, Calendar.NOVEMBER, 21, 9)), message("2", at(2026, Calendar.NOVEMBER, 23, 9)))
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }
}
