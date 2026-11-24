package com.vitorfreitas.getyourhelp.validation

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

class ChatValidatorTest {
    @Test
    fun emptyMessagesCannotBeSent() {
        assertNotNull(ChatValidator.message(""))
        assertNotNull(ChatValidator.message("   \n "))
    }

    @Test
    fun messagesAreLimitedTo1000CharactersIgnoringSurroundingSpaces() {
        assertNull(ChatValidator.message("x".repeat(ChatValidator.MESSAGE_MAX)))
        assertNull(ChatValidator.message("  " + "x".repeat(ChatValidator.MESSAGE_MAX) + "  "))
        assertNotNull(ChatValidator.message("x".repeat(ChatValidator.MESSAGE_MAX + 1)))
    }
}
