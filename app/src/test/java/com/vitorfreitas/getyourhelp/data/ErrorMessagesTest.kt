package com.vitorfreitas.getyourhelp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    @Test
    fun googleSignInNeverTalksAboutAPassword() {
        // With Google there is no password, so "Wrong email or password." would be confusing
        val message = googleAuthErrorMessageForCode("ERROR_INVALID_CREDENTIAL")
        assertNotEquals(authErrorMessageForCode("ERROR_INVALID_CREDENTIAL"), message)
        assertFalse(message.contains("password"))
        assertTrue(googleAuthErrorMessageForCode("ERROR_OPERATION_NOT_ALLOWED").contains("Google"))
    }

    @Test
    fun otherGoogleErrorsUseTheUsualMessages() {
        assertEquals(authErrorMessageForCode("ERROR_USER_DISABLED"), googleAuthErrorMessageForCode("ERROR_USER_DISABLED"))
    }
}
