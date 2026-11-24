package com.vitorfreitas.getyourhelp.validation

import com.vitorfreitas.getyourhelp.data.PaymentMethod
import com.vitorfreitas.getyourhelp.data.TaskCategory
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.data.TaskType
import java.math.BigDecimal
import java.math.RoundingMode

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** What the user entered in the task form (Post a task, or Edit task), before it is checked. */
data class TaskInput(
    val title: String,
    val description: String,
    val category: TaskCategory,
    val location: String,
    val type: TaskType,
    val amount: String,
    val paymentMethods: Set<PaymentMethod>,
    /** Expected date and time (epoch milliseconds), or null when the date is flexible. */
    val scheduledAt: Long?
) {
    companion object {
        /** The starting values of the form when posting a new task. */
        val Empty = TaskInput(
            title = "", description = "", category = TaskCategory.PET_CARE, location = "",
            type = TaskType.PAID, amount = "", paymentMethods = setOf(PaymentMethod.CASH), scheduledAt = null
        )

        /** The form filled in with an existing task, for editing it. */
        fun from(task: TaskItem) = TaskInput(
            title = task.title,
            description = task.description,
            category = task.category,
            location = task.location,
            type = task.taskType,
            amount = if (task.taskType == TaskType.PAID) TaskValidator.amountText(task.amount) else "",
            // A volunteer task has no methods. Cash is pre-ticked in case the requester switches to paid.
            paymentMethods = task.acceptedPaymentMethods.toSet().ifEmpty { setOf(PaymentMethod.CASH) },
            scheduledAt = task.scheduledAt
        )
    }
}

/** The result of checking a [TaskInput]: one message per invalid field (null = fine). */
data class TaskValidation(
    val title: String? = null,
    val description: String? = null,
    val location: String? = null,
    val amount: String? = null,
    val paymentMethods: String? = null,
    val scheduledAt: String? = null,
    /** The amount as a number. 0 for volunteer tasks, or when the amount is invalid. */
    val parsedAmount: Double = 0.0
) {
    val isValid: Boolean
        get() = listOf(title, description, location, amount, paymentMethods, scheduledAt).all { it == null }
}

/**
 * Form validation for creating a task. Plain Kotlin (no Android classes).
 * The limits below are also shown to the user as character counters.
 */
object TaskValidator {
    const val TITLE_MIN = 5
    const val TITLE_MAX = 80
    const val DESCRIPTION_MIN = 15
    const val DESCRIPTION_MAX = 1000
    const val LOCATION_MAX = 100
    const val AMOUNT_MAX = 10_000
    const val APPLICATION_MESSAGE_MAX = 300

    /** A task can be scheduled up to a year ahead. */
    private const val MAX_DAYS_AHEAD = 365L
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun validate(input: TaskInput, now: Long = System.currentTimeMillis()): TaskValidation {
        val title = input.title.trim()
        val description = input.description.trim()
        val location = input.location.trim()
        val isPaid = input.type == TaskType.PAID
        val amountResult = if (isPaid) checkAmount(input.amount) else AmountResult(value = 0.0)
        return TaskValidation(
            title = when {
                title.isEmpty() -> "Give the task a title."
                title.length < TITLE_MIN -> "Use at least $TITLE_MIN characters."
                title.length > TITLE_MAX -> "Use at most $TITLE_MAX characters."
                else -> null
            },
            description = when {
                description.isEmpty() -> "Describe what needs to be done."
                description.length < DESCRIPTION_MIN -> "Add a bit more detail (at least $DESCRIPTION_MIN characters)."
                description.length > DESCRIPTION_MAX -> "Use at most $DESCRIPTION_MAX characters."
                else -> null
            },
            location = when {
                location.isEmpty() -> "Say where the task is, e.g. a town or area."
                location.length > LOCATION_MAX -> "Use at most $LOCATION_MAX characters."
                else -> null
            },
            amount = amountResult.error,
            paymentMethods = if (isPaid && input.paymentMethods.isEmpty()) "Choose at least one payment method." else null,
            scheduledAt = input.scheduledAt?.let { scheduledAt ->
                when {
                    scheduledAt <= now -> "Pick a time in the future."
                    scheduledAt > now + MAX_DAYS_AHEAD * DAY_MILLIS -> "Pick a date within the next year."
                    else -> null
                }
            },
            parsedAmount = amountResult.value ?: 0.0
        )
    }

    /**
     * True if two forms describe the same task, ignoring differences that are not saved: spaces
     * around the text, how the amount is written ("15" or "15.00"), and payment details of a volunteer task.
     */
    fun sameTask(a: TaskInput, b: TaskInput): Boolean {
        val amountA = parseAmount(a.amount)
        val amountB = parseAmount(b.amount)
        // compareTo ignores the scale, so 15 and 15.00 are equal
        val sameAmount = if (amountA == null || amountB == null) amountA == amountB else amountA.compareTo(amountB) == 0
        val samePayment = a.type != TaskType.PAID || (sameAmount && a.paymentMethods == b.paymentMethods)
        return a.title.trim() == b.title.trim() &&
            a.description.trim() == b.description.trim() &&
            a.category == b.category &&
            a.location.trim() == b.location.trim() &&
            a.scheduledAt == b.scheduledAt &&
            a.type == b.type &&
            samePayment
    }

    /** Checks the optional message a helper sends with an application. */
    fun applicationMessage(message: String): String? =
        if (message.trim().length > APPLICATION_MESSAGE_MAX) "Use at most $APPLICATION_MESSAGE_MAX characters." else null

    /**
     * Reads an amount typed by the user. Accepts "15", "12.5" and "12,50" (comma as decimal separator).
     * Returns null when the text is not a plain number.
     */
    fun parseAmount(text: String): BigDecimal? =
        text.trim().replace(',', '.').takeIf { it.matches(Regex("\\d+(\\.\\d*)?|\\.\\d+")) }?.toBigDecimalOrNull()

    /** An amount the way a user would type it, the opposite of [parseAmount]: 15.0 -> "15", 12.5 -> "12.50". */
    fun amountText(amount: Double): String {
        val cents = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
        return if (cents.stripTrailingZeros().scale() <= 0) cents.setScale(0).toPlainString() else cents.toPlainString()
    }

    private data class AmountResult(val value: Double? = null, val error: String? = null)

    private fun checkAmount(text: String): AmountResult {
        if (text.isBlank()) return AmountResult(error = "Enter the amount you will pay.")
        val amount = parseAmount(text) ?: return AmountResult(error = "Enter a number, like 15 or 12.50.")
        return when {
            amount.signum() <= 0 -> AmountResult(error = "The amount must be more than €0.")
            amount > BigDecimal(AMOUNT_MAX) -> AmountResult(error = "The amount must be €$AMOUNT_MAX or less.")
            amount.stripTrailingZeros().scale() > 2 -> AmountResult(error = "Use at most 2 decimal places.")
            else -> AmountResult(value = amount.toDouble())
        }
    }
}
