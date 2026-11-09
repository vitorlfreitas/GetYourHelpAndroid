package com.vitorfreitas.getyourhelp.data

data class AppUser(
    val uid: String,
    val displayName: String,
    val email: String,
    val helperCategories: List<String> = emptyList()
)

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

data class TaskApplication(
    val id: String,
    val taskId: String,
    val helperId: String,
    val helperName: String,
    val preferredPaymentMethod: String,
    val status: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis()
)

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

data class TaskReport(
    val id: String,
    val taskId: String,
    val reporterId: String,
    val reason: String,
    val details: String,
    val status: String = "SUBMITTED",
    val createdAt: Long = System.currentTimeMillis()
)

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
