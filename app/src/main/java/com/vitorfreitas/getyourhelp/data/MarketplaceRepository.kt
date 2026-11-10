package com.vitorfreitas.getyourhelp.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * The only class that talks to Firebase.
 *
 * Firebase Authentication handles accounts
 * Cloud Firestore stores the shared data in the collections users, tasks, applications, paymentRecords, reports and reviews.
 */
class MarketplaceRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    /** Returns the signed-in user with their profile from `users`, or null if nobody is signed in. */
    suspend fun currentUser(): AppUser? {
        val firebaseUser = auth.currentUser ?: return null
        val doc = db.collection("users").document(firebaseUser.uid).get().await()
        val categories = (doc.get("helperCategories") as? List<*>)?.filterIsInstance<String>().orEmpty()
        return AppUser(
            uid = firebaseUser.uid,
            displayName = doc.getString("displayName") ?: firebaseUser.displayName ?: firebaseUser.email.orEmpty(),
            email = firebaseUser.email.orEmpty(),
            helperCategories = categories
        )
    }

    /** Signs in with email and password. Creates the profile if it is missing. */
    suspend fun signIn(email: String, password: String): AppUser {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Authentication did not return a user.")
        val docRef = db.collection("users").document(user.uid)
        val doc = docRef.get().await()
        if (!doc.exists()) {
            val profile = mapOf(
                "uid" to user.uid,
                "displayName" to (user.displayName ?: email.substringBefore("@")),
                "email" to email.trim(),
                "helperCategories" to emptyList<String>(),
                "createdAt" to System.currentTimeMillis()
            )
            docRef.set(profile).await()
        }
        return currentUser() ?: error("Could not load user profile.")
    }

    /** Creates a Firebase Authentication account and its profile document in `users`. */
    suspend fun register(name: String, email: String, password: String): AppUser {
        require(name.isNotBlank()) { "Enter your name." }
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Registration did not return a user.")
        val profile = mapOf(
            "uid" to user.uid,
            "displayName" to name.trim(),
            "email" to email.trim(),
            "helperCategories" to emptyList<String>(),
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("users").document(user.uid).set(profile).await()
        return AppUser(user.uid, name.trim(), email.trim())
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun updateHelperCategories(user: AppUser, categories: List<String>): AppUser {
        val updated = user.copy(helperCategories = categories.distinct())
        db.collection("users").document(user.uid)
            .update("helperCategories", updated.helperCategories).await()
        return updated
    }

    /**
     * Live list of all tasks, newest first. Firestore pushes a new list whenever any task changes, so every device sees new tasks and status changes without refreshing.
     */
    fun observeTasks(): Flow<List<TaskItem>> = callbackFlow {
        val registration = db.collection("tasks")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error)
                else trySend(snapshot?.documents?.mapNotNull { it.toTaskItem() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    suspend fun createTask(
        owner: AppUser,
        title: String,
        description: String,
        category: String,
        taskType: String,
        amount: Double,
        paymentMethods: List<String>,
        location: String
    ) {
        require(title.isNotBlank()) { "Task title is required." }
        require(description.isNotBlank()) { "Task description is required." }
        require(location.isNotBlank()) { "Location is required." }
        if (taskType == "PAID") {
            require(amount > 0.0) { "Enter an amount greater than zero for a paid task." }
            require(paymentMethods.isNotEmpty()) { "Select at least one payment method." }
        }
        val task = TaskItem(
            id = UUID.randomUUID().toString(), ownerId = owner.uid, ownerName = owner.displayName,
            title = title.trim(), description = description.trim(), category = category,
            taskType = taskType, amount = if (taskType == "PAID") amount else 0.0,
            acceptedPaymentMethods = if (taskType == "PAID") paymentMethods.distinct() else emptyList(),
            location = location.trim()
        )
        db.collection("tasks").document(task.id).set(task.toMap()).await()
    }

    /** All applications for one task. The security rules only allow the task owner to run this. */
    suspend fun applicationsForTask(taskId: String): List<TaskApplication> =
        db.collection("applications").whereEqualTo("taskId", taskId).get().await()
            .documents.mapNotNull { it.toApplication() }

    /** Applications sent by this helper. */
    suspend fun myApplications(userId: String): List<TaskApplication> =
        db.collection("applications").whereEqualTo("helperId", userId).get().await()
            .documents.mapNotNull { it.toApplication() }

    /** Creates a PENDING application */
    suspend fun applyToTask(task: TaskItem, helper: AppUser, preferredMethod: String) {
        require(task.ownerId != helper.uid) { "You cannot apply to your own task." }
        require(task.status == "OPEN") { "This task is no longer open." }
        if (task.taskType == "PAID") require(preferredMethod in task.acceptedPaymentMethods) {
            "Choose a payment method accepted by the requester."
        }
        val alreadyApplied = myApplications(helper.uid)
            .any { it.taskId == task.id && it.status != "WITHDRAWN" }
        if (alreadyApplied) error("You have already applied for this task.")
        val application = TaskApplication(
            id = UUID.randomUUID().toString(), taskId = task.id, helperId = helper.uid,
            helperName = helper.displayName, preferredPaymentMethod = preferredMethod
        )
        db.collection("applications").document(application.id).set(application.toMap()).await()
    }

    /**
     * Accepts one helper. Everything is written in a single batch, so it all succeeds or all fails:
     * - the chosen application becomes ACCEPTED and other pending ones DECLINED
     * - the task becomes ASSIGNED, with the helper and the agreed payment method
     * - for paid tasks, a PENDING payment record is created
     */
    suspend fun acceptApplication(task: TaskItem, application: TaskApplication) {
        require(task.ownerId == auth.currentUser?.uid) { "Only the requester can accept an application." }
        require(task.status == "OPEN") { "This task is no longer open." }
        val batch = db.batch()
        val existingApplications = db.collection("applications").whereEqualTo("taskId", task.id).get().await()
        existingApplications.documents.forEach { document ->
            val newStatus = if (document.id == application.id) "ACCEPTED" else "DECLINED"
            if ((document.getString("status") ?: "PENDING") == "PENDING") {
                batch.update(document.reference, "status", newStatus)
            }
        }
        batch.update(db.collection("tasks").document(task.id), mapOf(
            "status" to "ASSIGNED", "assignedHelperId" to application.helperId,
            "assignedHelperName" to application.helperName,
            "agreedPaymentMethod" to application.preferredPaymentMethod
        ))
        if (task.taskType == "PAID") {
            val payment = PaymentRecord(
                id = UUID.randomUUID().toString(), taskId = task.id, taskTitle = task.title,
                payerId = task.ownerId, payeeId = application.helperId,
                otherPartyName = "${task.ownerName} and ${application.helperName}",
                amount = task.amount, method = application.preferredPaymentMethod
            )
            batch.set(db.collection("paymentRecords").document(payment.id), payment.toMap())
        }
        batch.commit().await()
    }

    /** The requester confirms the work is done: ASSIGNED becomes COMPLETED. */
    suspend fun completeTask(task: TaskItem, user: AppUser) {
        require(task.ownerId == user.uid) { "Only the requester can confirm task completion." }
        require(task.status == "ASSIGNED") { "Only an assigned task can be completed." }
        db.collection("tasks").document(task.id).update("status", "COMPLETED").await()
    }

    /** Saves a report for later review. The task is not removed automatically. */
    suspend fun reportTask(task: TaskItem, user: AppUser, reason: String, details: String) {
        require(reason.isNotBlank()) { "Select a report reason." }
        val report = TaskReport(UUID.randomUUID().toString(), task.id, user.uid, reason, details.trim())
        db.collection("reports").document(report.id).set(report.toMap()).await()
    }

    /** After completion, the requester and the assigned helper can each review the other once. */
    suspend fun submitReview(task: TaskItem, reviewer: AppUser, rating: Int, comment: String) {
        require(task.status == "COMPLETED") { "Reviews are available after a task is completed." }
        require(rating in 1..5) { "Choose a rating from 1 to 5." }
        val revieweeId = if (reviewer.uid == task.ownerId) task.assignedHelperId else task.ownerId
        val revieweeName = if (reviewer.uid == task.ownerId) task.assignedHelperName else task.ownerName
        require(revieweeId.isNotBlank() && reviewer.uid in listOf(task.ownerId, task.assignedHelperId)) {
            "Only the requester and accepted helper can review this task."
        }
        val existing = db.collection("reviews").whereEqualTo("reviewerId", reviewer.uid)
            .get().await().documents.firstOrNull { it.getString("taskId") == task.id }
        require(existing == null) { "You have already reviewed this task." }
        val review = ReviewRecord(UUID.randomUUID().toString(), task.id, reviewer.uid, revieweeId,
            revieweeName, rating, comment.trim())
        db.collection("reviews").document(review.id).set(review.toMap()).await()
    }

    /** Payment records where the user is the payer or the payee, newest first. */
    suspend fun paymentRecords(userId: String): List<PaymentRecord> {
        val outgoing = db.collection("paymentRecords").whereEqualTo("payerId", userId).get().await()
        val incoming = db.collection("paymentRecords").whereEqualTo("payeeId", userId).get().await()
        return (outgoing.documents + incoming.documents).distinctBy { it.id }
            .mapNotNull { it.toPayment() }.sortedByDescending { it.updatedAt }
    }

    /** Marks a payment as paid. This only updates the record; no money is moved. */
    suspend fun markPaymentRecorded(paymentId: String, user: AppUser) {
        val ref = db.collection("paymentRecords").document(paymentId)
        val doc = ref.get().await()
        val payer = doc.getString("payerId")
        val payee = doc.getString("payeeId")
        require(user.uid == payer || user.uid == payee) { "You are not part of this payment record." }
        ref.update(mapOf("status" to "MARKED_PAID", "updatedAt" to System.currentTimeMillis())).await()
    }
}

// Converting data classes to Firestore documents (field name -> value).
private fun TaskItem.toMap() = mapOf(
    "id" to id, "ownerId" to ownerId, "ownerName" to ownerName, "title" to title,
    "description" to description, "category" to category, "taskType" to taskType, "amount" to amount,
    "acceptedPaymentMethods" to acceptedPaymentMethods, "location" to location, "status" to status,
    "assignedHelperId" to assignedHelperId, "assignedHelperName" to assignedHelperName,
    "agreedPaymentMethod" to agreedPaymentMethod, "createdAt" to createdAt
)
private fun TaskApplication.toMap() = mapOf(
    "id" to id, "taskId" to taskId, "helperId" to helperId, "helperName" to helperName,
    "preferredPaymentMethod" to preferredPaymentMethod, "status" to status, "createdAt" to createdAt
)
private fun PaymentRecord.toMap() = mapOf(
    "id" to id, "taskId" to taskId, "taskTitle" to taskTitle, "payerId" to payerId,
    "payeeId" to payeeId, "otherPartyName" to otherPartyName, "amount" to amount,
    "method" to method, "status" to status, "updatedAt" to updatedAt
)
private fun TaskReport.toMap() = mapOf(
    "id" to id, "taskId" to taskId, "reporterId" to reporterId, "reason" to reason,
    "details" to details, "status" to status, "createdAt" to createdAt
)
private fun ReviewRecord.toMap() = mapOf(
    "id" to id, "taskId" to taskId, "reviewerId" to reviewerId, "revieweeId" to revieweeId,
    "revieweeName" to revieweeName, "rating" to rating, "comment" to comment, "createdAt" to createdAt
)

// Reading Firestore documents back into data classes. A malformed document returns null and is skipped.
private fun DocumentSnapshot.toTaskItem(): TaskItem? = try {
    TaskItem(id = id, ownerId = getString("ownerId").orEmpty(), ownerName = getString("ownerName").orEmpty(),
        title = getString("title").orEmpty(), description = getString("description").orEmpty(), category = getString("category").orEmpty(),
        taskType = getString("taskType").orEmpty(), amount = getDouble("amount") ?: 0.0,
        acceptedPaymentMethods = (get("acceptedPaymentMethods") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        location = getString("location").orEmpty(), status = getString("status") ?: "OPEN",
        assignedHelperId = getString("assignedHelperId").orEmpty(), assignedHelperName = getString("assignedHelperName").orEmpty(),
        agreedPaymentMethod = getString("agreedPaymentMethod").orEmpty(), createdAt = getLong("createdAt") ?: 0L)
} catch (_: Exception) { null }
private fun DocumentSnapshot.toApplication(): TaskApplication? = try {
    TaskApplication(id, getString("taskId").orEmpty(), getString("helperId").orEmpty(), getString("helperName").orEmpty(),
        getString("preferredPaymentMethod").orEmpty(), getString("status") ?: "PENDING", getLong("createdAt") ?: 0L)
} catch (_: Exception) { null }
private fun DocumentSnapshot.toPayment(): PaymentRecord? = try {
    PaymentRecord(id, getString("taskId").orEmpty(), getString("taskTitle").orEmpty(), getString("payerId").orEmpty(),
        getString("payeeId").orEmpty(), getString("otherPartyName").orEmpty(), getDouble("amount") ?: 0.0,
        getString("method").orEmpty(), getString("status") ?: "PENDING", getLong("updatedAt") ?: 0L)
} catch (_: Exception) { null }
