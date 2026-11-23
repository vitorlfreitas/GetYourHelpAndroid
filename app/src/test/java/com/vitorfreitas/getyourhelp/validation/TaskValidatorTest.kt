package com.vitorfreitas.getyourhelp.validation

import com.vitorfreitas.getyourhelp.data.PaymentMethod
import com.vitorfreitas.getyourhelp.data.TaskCategory
import com.vitorfreitas.getyourhelp.data.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

class TaskValidatorTest {
    private val now = 1_800_000_000_000L
    private val hour = 60L * 60 * 1000

    private fun validInput() = TaskInput(
        title = "Walk my dog",
        description = "Walk my dog for 30 minutes in the park.",
        category = TaskCategory.PET_CARE,
        location = "Rathmines, Dublin",
        type = TaskType.PAID,
        amount = "15",
        paymentMethods = setOf(PaymentMethod.CASH),
        scheduledAt = null
    )

    private fun validate(input: TaskInput) = TaskValidator.validate(input, now)

    @Test
    fun validPaidTaskHasNoErrors() {
        val result = validate(validInput())
        assertTrue(result.isValid)
        assertEquals(15.0, result.parsedAmount, 0.0)
    }

    @Test
    fun titleIsRequiredAndLimited() {
        assertNotNull(validate(validInput().copy(title = "   ")).title)
        assertNotNull(validate(validInput().copy(title = "Dog")).title)
        assertNotNull(validate(validInput().copy(title = "x".repeat(TaskValidator.TITLE_MAX + 1))).title)
        assertNull(validate(validInput().copy(title = "x".repeat(TaskValidator.TITLE_MAX))).title)
    }

    @Test
    fun titleLengthIgnoresSurroundingSpaces() {
        assertNotNull(validate(validInput().copy(title = "  Dog  ")).title)
    }

    @Test
    fun descriptionNeedsSomeDetail() {
        assertNotNull(validate(validInput().copy(description = "")).description)
        assertNotNull(validate(validInput().copy(description = "Walk my dog")).description)
        assertNull(validate(validInput().copy(description = "Walk my dog today")).description)
    }

    @Test
    fun locationIsRequired() {
        assertNotNull(validate(validInput().copy(location = " ")).location)
    }

    @Test
    fun commaIsAcceptedAsDecimalSeparator() {
        val result = validate(validInput().copy(amount = "12,50"))
        assertNull(result.amount)
        assertEquals(12.5, result.parsedAmount, 0.0)
    }

    @Test
    fun amountMustBeAPlainNumber() {
        listOf("abc", "1.000,50", "12e3", "NaN", "-5").forEach { text ->
            assertNotNull("'$text' should be rejected", validate(validInput().copy(amount = text)).amount)
        }
    }

    @Test
    fun amountMustBePositiveAndNotTooHigh() {
        assertNotNull(validate(validInput().copy(amount = "0")).amount)
        assertNotNull(validate(validInput().copy(amount = "10000.01")).amount)
        assertNull(validate(validInput().copy(amount = "10000")).amount)
    }

    @Test
    fun amountHasAtMostTwoDecimals() {
        assertNotNull(validate(validInput().copy(amount = "12.505")).amount)
        assertNull(validate(validInput().copy(amount = "12.500")).amount)
    }

    @Test
    fun emptyAmountIsRejectedForPaidTasks() {
        assertNotNull(validate(validInput().copy(amount = "")).amount)
    }

    @Test
    fun paidTaskNeedsAPaymentMethod() {
        assertNotNull(validate(validInput().copy(paymentMethods = emptySet())).paymentMethods)
    }

    @Test
    fun volunteerTaskIgnoresAmountAndPaymentMethods() {
        val result = validate(validInput().copy(type = TaskType.VOLUNTEER, amount = "", paymentMethods = emptySet()))
        assertTrue(result.isValid)
        assertEquals(0.0, result.parsedAmount, 0.0)
    }

    @Test
    fun scheduleMustBeInTheFutureAndWithinAYear() {
        assertNotNull(validate(validInput().copy(scheduledAt = now - hour)).scheduledAt)
        assertNotNull(validate(validInput().copy(scheduledAt = now + 400 * 24 * hour)).scheduledAt)
        assertNull(validate(validInput().copy(scheduledAt = now + 2 * hour)).scheduledAt)
    }

    @Test
    fun invalidFieldMakesWholeFormInvalid() {
        assertFalse(validate(validInput().copy(location = "")).isValid)
    }

    @Test
    fun applicationMessageIsOptionalButLimited() {
        assertNull(TaskValidator.applicationMessage(""))
        assertNull(TaskValidator.applicationMessage("x".repeat(TaskValidator.APPLICATION_MESSAGE_MAX)))
        assertNotNull(TaskValidator.applicationMessage("x".repeat(TaskValidator.APPLICATION_MESSAGE_MAX + 1)))
    }
}
