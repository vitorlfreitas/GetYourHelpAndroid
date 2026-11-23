package com.vitorfreitas.getyourhelp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

class ErrorMessagesTest {
    @Test
    fun wrongPasswordAndUnknownEmailGiveTheSameMessage() {
        val message = authErrorMessageForCode("ERROR_INVALID_CREDENTIAL")
        assertEquals(message, authErrorMessageForCode("ERROR_WRONG_PASSWORD"))
        assertEquals(message, authErrorMessageForCode("ERROR_USER_NOT_FOUND"))
    }

    @Test
    fun emailAlreadyInUseSuggestsSigningIn() {
        assertTrue(authErrorMessageForCode("ERROR_EMAIL_ALREADY_IN_USE").contains("signing in"))
    }

    @Test
    fun unknownCodeIsIncludedToHelpDebugging() {
        assertTrue(authErrorMessageForCode("ERROR_SOMETHING_NEW").contains("ERROR_SOMETHING_NEW"))
    }
}
