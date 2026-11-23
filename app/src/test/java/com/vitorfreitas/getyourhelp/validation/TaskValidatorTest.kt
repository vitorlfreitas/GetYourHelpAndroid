package com.vitorfreitas.getyourhelp.validation

import com.vitorfreitas.getyourhelp.data.PaymentMethod
import com.vitorfreitas.getyourhelp.data.TaskCategory
import com.vitorfreitas.getyourhelp.data.TaskItem
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

    @Test
    fun amountsAreShownTheWayAUserTypesThem() {
        assertEquals("15", TaskValidator.amountText(15.0))
        assertEquals("12.50", TaskValidator.amountText(12.5))
        assertEquals("1500", TaskValidator.amountText(1500.0))
        // And they read back as the same number
        assertEquals(12.5, TaskValidator.parseAmount(TaskValidator.amountText(12.5))!!.toDouble(), 0.0)
    }

    @Test
    fun editFormStartsWithTheTasksValues() {
        val task = TaskItem(
            id = "t", ownerId = "me", ownerName = "Me", title = "Walk my dog", description = "Walk my dog for 30 minutes.",
            category = TaskCategory.PET_CARE, taskType = TaskType.PAID, amount = 12.5,
            acceptedPaymentMethods = listOf(PaymentMethod.BANK_TRANSFER), location = "Rathmines", scheduledAt = now + hour
        )
        val input = TaskInput.from(task)
        assertEquals("Walk my dog", input.title)
        assertEquals("12.50", input.amount)
        assertEquals(setOf(PaymentMethod.BANK_TRANSFER), input.paymentMethods)
        assertEquals(now + hour, input.scheduledAt)
        assertTrue(validate(input).isValid)
    }

    @Test
    fun formsWithTheSameSavedValuesAreTheSameTask() {
        val input = validInput()
        assertTrue(TaskValidator.sameTask(input, input.copy(title = " Walk my dog ", amount = "15.00")))
        assertFalse(TaskValidator.sameTask(input, input.copy(amount = "16")))
        assertFalse(TaskValidator.sameTask(input, input.copy(paymentMethods = setOf(PaymentMethod.BANK_TRANSFER))))
        // Payment details are not saved for volunteer tasks, so they do not count as a change
        val volunteer = input.copy(type = TaskType.VOLUNTEER, amount = "")
        assertTrue(TaskValidator.sameTask(volunteer, volunteer.copy(amount = "20", paymentMethods = emptySet())))
    }

    @Test
    fun editFormOfAVolunteerTaskHasNoAmount() {
        val task = TaskItem(
            id = "t", ownerId = "me", ownerName = "Me", title = "Help at the food bank", description = "Sort donations for two hours.",
            category = TaskCategory.VOLUNTEERING, taskType = TaskType.VOLUNTEER, amount = 0.0,
            acceptedPaymentMethods = emptyList(), location = "Dublin 8"
        )
        val input = TaskInput.from(task)
        assertEquals("", input.amount)
        assertTrue(validate(input).isValid)
    }
}
