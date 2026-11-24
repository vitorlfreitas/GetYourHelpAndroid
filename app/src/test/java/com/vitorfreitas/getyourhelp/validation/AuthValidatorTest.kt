package com.vitorfreitas.getyourhelp.validation

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

class AuthValidatorTest {
    @Test
    fun acceptsValidEmails() {
        assertNull(AuthValidator.email("ana@example.com"))
        assertNull(AuthValidator.email("  ana.silva+test@mail.co.uk  "))
    }

    @Test
    fun rejectsInvalidEmails() {
        listOf("", "ana", "ana@", "ana@example", "@example.com", "ana silva@example.com").forEach { email ->
            assertNotNull("'$email' should be rejected", AuthValidator.email(email))
        }
    }

    @Test
    fun newPasswordNeedsSixCharacters() {
        assertNotNull(AuthValidator.newPassword(""))
        assertNotNull(AuthValidator.newPassword("12345"))
        assertNull(AuthValidator.newPassword("123456"))
    }

    @Test
    fun signInPasswordOnlyNeedsToBeFilledIn() {
        assertNotNull(AuthValidator.signInPassword(""))
        assertNull(AuthValidator.signInPassword("x"))
    }

    @Test
    fun confirmationMustMatch() {
        assertNotNull(AuthValidator.confirmPassword("secret1", ""))
        assertNotNull(AuthValidator.confirmPassword("secret1", "secret2"))
        assertNull(AuthValidator.confirmPassword("secret1", "secret1"))
    }

    @Test
    fun nameIsRequiredAndLimited() {
        assertNotNull(AuthValidator.name("  "))
        assertNotNull(AuthValidator.name("x".repeat(AuthValidator.MAX_NAME_LENGTH + 1)))
        assertNull(AuthValidator.name("Ana Silva"))
    }
}
