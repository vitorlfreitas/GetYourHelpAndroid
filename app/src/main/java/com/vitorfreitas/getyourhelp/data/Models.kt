package com.vitorfreitas.getyourhelp.data

// The app's data model:
// - enums: the fixed values a task, application or payment can have. Firestore stores each value
//   by its name (e.g. "OPEN", "PET_CARE"), `label` is the text shown in the app.
// - data classes: the documents saved in Cloud Firestore, one collection per class.

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Kinds of work. Used when creating a task and for the categories a helper is interested in. */
enum class TaskCategory(val label: String) {
    PET_CARE("Pet Care"),
    GARDENING("Gardening"),
    SHOPPING("Shopping"),
    HOME_HELP("Home Help"),
    MOVING("Moving"),
    CLEANING("Cleaning"),
    VOLUNTEERING("Volunteering"),
    OTHER("Other")
}

/** A paid job, or unpaid volunteering. */
enum class TaskType(val label: String) {
    PAID("Paid"),
    VOLUNTEER("Volunteering")
}

/**
 * Life cycle of a task: OPEN -> ASSIGNED (a helper was accepted) -> COMPLETED (requester confirmed).
 * The app shows ASSIGNED as "Ongoing", the name used in the project proposal.
 */
enum class TaskStatus(val label: String) {
    OPEN("Open"),
    ASSIGNED("Ongoing"),
    COMPLETED("Completed")
}

/** How a paid task can be paid. Volunteer tasks have no payment method, which the app represents as null. */
enum class PaymentMethod(val label: String) {
    CASH("Cash"),
    BANK_TRANSFER("Bank transfer")
}

/** PENDING until the requester accepts a helper, then ACCEPTED or DECLINED. A helper can also withdraw. */
enum class ApplicationStatus(val label: String) {
    PENDING("Pending"),
    ACCEPTED("Accepted"),
    DECLINED("Declined"),
    WITHDRAWN("Withdrawn")
}

/** The app never moves money, so a payment record only goes from PENDING to MARKED_PAID. */
enum class PaymentStatus(val label: String) {
    PENDING("Pending"),
    MARKED_PAID("Marked as paid")
}

data class AppUser(
    val uid: String,
    val displayName: String,
    val email: String,
    val helperCategories: List<TaskCategory> = emptyList()
)

/**
 * A task published by a requester (collection `tasks`).
 * Volunteer tasks have amount 0 and no accepted payment methods.
 * scheduledAt is the expected date and time (epoch milliseconds), or null when the date is flexible.
 * agreedPaymentMethod is set when a helper is accepted (null until then, and for volunteer tasks).
 */
data class TaskItem(
    val id: String,
    val ownerId: String,
    val ownerName: String,
    val title: String,
    val description: String,
    val category: TaskCategory,
    val taskType: TaskType,
    val amount: Double,
    val acceptedPaymentMethods: List<PaymentMethod>,
    val location: String,
    val scheduledAt: Long? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    val assignedHelperId: String = "",
    val assignedHelperName: String = "",
    val agreedPaymentMethod: PaymentMethod? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A helper's application for a task (collection `applications`).
 * The document id is "<taskId>_<helperId>", so a helper can have only one application per task.
 * preferredPaymentMethod is null for volunteer tasks; message is an optional note to the requester.
 */
data class TaskApplication(
    val id: String,
    val taskId: String,
    val helperId: String,
    val helperName: String,
    val preferredPaymentMethod: PaymentMethod?,
    val message: String = "",
    val status: ApplicationStatus = ApplicationStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)

/** Record of an agreed payment for a paid task (collection `paymentRecords`). */
data class PaymentRecord(
    val id: String,
    val taskId: String,
    val taskTitle: String,
    val payerId: String,
    val payeeId: String,
    val otherPartyName: String,
    val amount: Double,
    val method: PaymentMethod?,
    val status: PaymentStatus = PaymentStatus.PENDING,
    val updatedAt: Long = System.currentTimeMillis()
)

/** A user's report about a task (collection `reports`), stored for later review. */
data class TaskReport(
    val id: String,
    val taskId: String,
    val reporterId: String,
    val reason: String,
    val details: String,
    val status: String = "SUBMITTED",
    val createdAt: Long = System.currentTimeMillis()
)

/** A 1-5 star review between requester and helper after a completed task (collection `reviews`). */
data class ReviewRecord(
    val id: String,
    val taskId: String,
    val reviewerId: String,
    val revieweeId: String,
    val revieweeName: String,
    val rating: Int,
    val comment: String,
    val createdAt: Long = System.currentTimeMillis()
)
