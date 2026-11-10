package com.vitorfreitas.getyourhelp.data

// Data classes saved in Cloud Firestore, one collection per class.

data class AppUser(
    val uid: String,
    val displayName: String,
    val email: String,
    val helperCategories: List<String> = emptyList()
)

/**
 * A task published by a requester (collection `tasks`).
 *
 * - taskType: "PAID" or "VOLUNTEER". Volunteer tasks have amount 0 and no payment methods.
 * - status: "OPEN" -> "ASSIGNED" (a helper was accepted) -> "COMPLETED" (requester confirmed).
 */
data class TaskItem(
    val id: String,
    val ownerId: String,
    val ownerName: String,
    val title: String,
    val description: String,
    val category: String,
    val taskType: String,
    val amount: Double,
    val acceptedPaymentMethods: List<String>,
    val location: String,
    val status: String = "OPEN",
    val assignedHelperId: String = "",
    val assignedHelperName: String = "",
    val agreedPaymentMethod: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A helper's application for a task (collection `applications`).
 * status: "PENDING", then "ACCEPTED" or "DECLINED" when the requester picks a helper.
 */
data class TaskApplication(
    val id: String,
    val taskId: String,
    val helperId: String,
    val helperName: String,
    val preferredPaymentMethod: String,
    val status: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Record of an agreed payment for a paid task (collection `paymentRecords`).
 * The app never moves money; status only goes from "PENDING" to "MARKED_PAID".
 */
data class PaymentRecord(
    val id: String,
    val taskId: String,
    val taskTitle: String,
    val payerId: String,
    val payeeId: String,
    val otherPartyName: String,
    val amount: Double,
    val method: String,
    val status: String = "PENDING",
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
