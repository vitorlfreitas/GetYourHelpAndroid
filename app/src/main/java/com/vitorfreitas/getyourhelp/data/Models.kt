package com.vitorfreitas.getyourhelp.data

// The data model. Enums are stored in Firestore by name ("OPEN"), and `label` is the text shown in
// the app. Each data class is a document in a Cloud Firestore collection.

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Kinds of work, for tasks and for the categories a helper is interested in. */
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

enum class TaskType(val label: String) { PAID("Paid"), VOLUNTEER("Volunteering") }

/**
 * OPEN -> ASSIGNED (a helper was accepted, shown as "Ongoing") -> COMPLETED (the helper marked the
 * work as done and the requester confirmed it). An OPEN task can also be CANCELLED.
 */
enum class TaskStatus(val label: String) { OPEN("Open"), ASSIGNED("Ongoing"), COMPLETED("Completed"), CANCELLED("Cancelled") }

/** Volunteer tasks have no payment method (null). */
enum class PaymentMethod(val label: String) { CASH("Cash"), BANK_TRANSFER("Bank transfer") }

enum class ApplicationStatus(val label: String) {
    PENDING("Pending"), ACCEPTED("Accepted"), DECLINED("Declined"), WITHDRAWN("Withdrawn")
}

/** The app never moves money: a payment record only goes from PENDING to MARKED_PAID. */
enum class PaymentStatus(val label: String) { PENDING("Pending"), MARKED_PAID("Marked as paid") }

data class AppUser(val uid: String, val displayName: String, val email: String, val helperCategories: List<TaskCategory> = emptyList())

/** A point on the map. Tasks store it rounded to about 100 m, so they never reveal an exact address. */
data class Coordinates(val latitude: Double, val longitude: Double)

/**
 * A task (collection `tasks`). Volunteer tasks have amount 0 and no payment methods. Nullable
 * fields are unknown or not happened yet: no position, a flexible date, no helper, not done, never edited.
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
    val coordinates: Coordinates? = null,
    val scheduledAt: Long? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    val assignedHelperId: String = "",
    val assignedHelperName: String = "",
    val agreedPaymentMethod: PaymentMethod? = null,
    val workDoneAt: Long? = null,
    val editedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** Once a helper is accepted, the requester and that helper can chat. After completion it is read-only. */
fun TaskItem.hasChatFor(userId: String): Boolean =
    (status == TaskStatus.ASSIGNED || status == TaskStatus.COMPLETED) &&
        assignedHelperId.isNotEmpty() && (userId == ownerId || userId == assignedHelperId)

val TaskItem.chatIsOpen: Boolean get() = status == TaskStatus.ASSIGNED

/** A helper's application (collection `applications`, id "<taskId>_<helperId>": one per helper and task). */
data class TaskApplication(
    val id: String, val taskId: String, val helperId: String, val helperName: String,
    val preferredPaymentMethod: PaymentMethod?, val message: String = "",
    val status: ApplicationStatus = ApplicationStatus.PENDING, val createdAt: Long = System.currentTimeMillis()
)

/** An agreed payment for a paid task (collection `paymentRecords`). */
data class PaymentRecord(
    val id: String, val taskId: String, val taskTitle: String, val payerId: String, val payeeId: String,
    val otherPartyName: String, val amount: Double, val method: PaymentMethod?,
    val status: PaymentStatus = PaymentStatus.PENDING, val updatedAt: Long = System.currentTimeMillis()
)

/**
 * A chat message (subcollection `tasks/{taskId}/messages`). The server sets sentAt, so both phones
 * order messages the same way. pending: not on the server yet.
 */
data class ChatMessage(
    val id: String, val senderId: String, val senderName: String, val text: String, val sentAt: Long, val pending: Boolean = false
)

/** A report about a task (collection `reports`, id "<taskId>_<reporterId>": one per user and task). */
data class TaskReport(
    val id: String, val taskId: String, val reporterId: String, val reason: String, val details: String,
    val status: String = "SUBMITTED", val createdAt: Long = System.currentTimeMillis()
)

/** A 1-5 star review after a completed task (collection `reviews`). */
data class ReviewRecord(
    val id: String, val taskId: String, val reviewerId: String, val revieweeId: String, val revieweeName: String,
    val rating: Int, val comment: String, val createdAt: Long = System.currentTimeMillis()
)
