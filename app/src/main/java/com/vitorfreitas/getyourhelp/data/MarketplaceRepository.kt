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
import kotlin.enums.enumEntries

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid


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
        return AppUser(
            uid = firebaseUser.uid,
            displayName = doc.getString("displayName") ?: firebaseUser.displayName ?: firebaseUser.email.orEmpty(),
            email = firebaseUser.email.orEmpty(),
            helperCategories = doc.getStringList("helperCategories").mapNotNull(::categoryFromStored).distinct()
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

    suspend fun updateHelperCategories(user: AppUser, categories: List<TaskCategory>): AppUser {
        val updated = user.copy(helperCategories = categories.distinct())
        db.collection("users").document(user.uid)
            .update("helperCategories", updated.helperCategories.map { it.name }).await()
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
        category: TaskCategory,
        taskType: TaskType,
        amount: Double,
        paymentMethods: List<PaymentMethod>,
        location: String
    ) {
        require(title.isNotBlank()) { "Task title is required." }
        require(description.isNotBlank()) { "Task description is required." }
        require(location.isNotBlank()) { "Location is required." }
        val isPaid = taskType == TaskType.PAID
        if (isPaid) {
            require(amount > 0.0) { "Enter an amount greater than zero for a paid task." }
            require(paymentMethods.isNotEmpty()) { "Select at least one payment method." }
        }
        val task = TaskItem(
            id = UUID.randomUUID().toString(), ownerId = owner.uid, ownerName = owner.displayName,
            title = title.trim(), description = description.trim(), category = category,
            taskType = taskType, amount = if (isPaid) amount else 0.0,
            acceptedPaymentMethods = if (isPaid) paymentMethods.distinct() else emptyList(),
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

    /** Creates a PENDING application. preferredMethod is ignored (stored as not applicable) for volunteer tasks. */
    suspend fun applyToTask(task: TaskItem, helper: AppUser, preferredMethod: PaymentMethod?) {
        require(task.ownerId != helper.uid) { "You cannot apply to your own task." }
        require(task.status == TaskStatus.OPEN) { "This task is no longer open." }
        val isPaid = task.taskType == TaskType.PAID
        if (isPaid) require(preferredMethod != null && preferredMethod in task.acceptedPaymentMethods) {
            "Choose a payment method accepted by the requester."
        }
        val alreadyApplied = myApplications(helper.uid)
            .any { it.taskId == task.id && it.status != ApplicationStatus.WITHDRAWN }
        if (alreadyApplied) error("You have already applied for this task.")
        val application = TaskApplication(
            id = UUID.randomUUID().toString(), taskId = task.id, helperId = helper.uid,
            helperName = helper.displayName, preferredPaymentMethod = if (isPaid) preferredMethod else null
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
        require(task.status == TaskStatus.OPEN) { "This task is no longer open." }
        val batch = db.batch()
        val existingApplications = db.collection("applications").whereEqualTo("taskId", task.id).get().await()
        existingApplications.documents.forEach { document ->
            val currentStatus = enumFromStored<ApplicationStatus>(document.getString("status")) ?: ApplicationStatus.PENDING
            if (currentStatus == ApplicationStatus.PENDING) {
                val newStatus = if (document.id == application.id) ApplicationStatus.ACCEPTED else ApplicationStatus.DECLINED
                batch.update(document.reference, "status", newStatus.name)
            }
        }
        batch.update(db.collection("tasks").document(task.id), mapOf(
            "status" to TaskStatus.ASSIGNED.name, "assignedHelperId" to application.helperId,
            "assignedHelperName" to application.helperName,
            "agreedPaymentMethod" to application.preferredPaymentMethod.toStored()
        ))
        if (task.taskType == TaskType.PAID) {
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
        require(task.status == TaskStatus.ASSIGNED) { "Only an assigned task can be completed." }
        db.collection("tasks").document(task.id).update("status", TaskStatus.COMPLETED.name).await()
    }

    /** Saves a report for later review. The task is not removed automatically. */
    suspend fun reportTask(task: TaskItem, user: AppUser, reason: String, details: String) {
        require(reason.isNotBlank()) { "Select a report reason." }
        val report = TaskReport(UUID.randomUUID().toString(), task.id, user.uid, reason, details.trim())
        db.collection("reports").document(report.id).set(report.toMap()).await()
    }

    /** After completion, the requester and the assigned helper can each review the other once. */
    suspend fun submitReview(task: TaskItem, reviewer: AppUser, rating: Int, comment: String) {
        require(task.status == TaskStatus.COMPLETED) { "Reviews are available after a task is completed." }
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

    /** Marks a payment as paid. This only updates the record, no money is moved. */
    suspend fun markPaymentRecorded(paymentId: String, user: AppUser) {
        val ref = db.collection("paymentRecords").document(paymentId)
        val doc = ref.get().await()
        val payer = doc.getString("payerId")
        val payee = doc.getString("payeeId")
        require(user.uid == payer || user.uid == payee) { "You are not part of this payment record." }
        ref.update(mapOf("status" to PaymentStatus.MARKED_PAID.name, "updatedAt" to System.currentTimeMillis())).await()
    }
}

// How enums are stored: by name ("OPEN", "CASH"...). A missing payment method (volunteer tasks) is stored as "NOT_APPLICABLE".
private const val NOT_APPLICABLE = "NOT_APPLICABLE"

private fun PaymentMethod?.toStored(): String = this?.name ?: NOT_APPLICABLE

/** The enum value with this stored name, or null if the value is missing or unknown (e.g. "NOT_APPLICABLE"). */
private inline fun <reified T : Enum<T>> enumFromStored(value: String?): T? =
    enumEntries<T>().firstOrNull { it.name == value }

/** Categories used to be stored by their label ("Pet Care") and are now stored by name ("PET_CARE") | both are accepted. */
private fun categoryFromStored(value: String?): TaskCategory? =
    TaskCategory.entries.firstOrNull { it.name == value || it.label == value }

private fun DocumentSnapshot.getStringList(field: String): List<String> =
    (get(field) as? List<*>)?.filterIsInstance<String>().orEmpty()

// Converting data classes to Firestore documents (field name -> value).
private fun TaskItem.toMap() = mapOf(
    "id" to id, "ownerId" to ownerId, "ownerName" to ownerName, "title" to title,
    "description" to description, "category" to category.name, "taskType" to taskType.name, "amount" to amount,
    "acceptedPaymentMethods" to acceptedPaymentMethods.map { it.name }, "location" to location, "status" to status.name,
    "assignedHelperId" to assignedHelperId, "assignedHelperName" to assignedHelperName,
    "agreedPaymentMethod" to agreedPaymentMethod?.name.orEmpty(), "createdAt" to createdAt
)
private fun TaskApplication.toMap() = mapOf(
    "id" to id, "taskId" to taskId, "helperId" to helperId, "helperName" to helperName,
    "preferredPaymentMethod" to preferredPaymentMethod.toStored(), "status" to status.name, "createdAt" to createdAt
)
private fun PaymentRecord.toMap() = mapOf(
    "id" to id, "taskId" to taskId, "taskTitle" to taskTitle, "payerId" to payerId,
    "payeeId" to payeeId, "otherPartyName" to otherPartyName, "amount" to amount,
    "method" to method.toStored(), "status" to status.name, "updatedAt" to updatedAt
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
private fun DocumentSnapshot.toTaskItem(): TaskItem? {
    // A status this version of the app does not know is skipped instead of being shown as open.
    val status = enumFromStored<TaskStatus>(getString("status") ?: TaskStatus.OPEN.name) ?: return null
    return try {
        TaskItem(
            id = id,
            ownerId = getString("ownerId").orEmpty(),
            ownerName = getString("ownerName").orEmpty(),
            title = getString("title").orEmpty(),
            description = getString("description").orEmpty(),
            category = categoryFromStored(getString("category")) ?: TaskCategory.OTHER,
            taskType = enumFromStored<TaskType>(getString("taskType")) ?: TaskType.VOLUNTEER,
            amount = getDouble("amount") ?: 0.0,
            acceptedPaymentMethods = getStringList("acceptedPaymentMethods").mapNotNull { enumFromStored<PaymentMethod>(it) },
            location = getString("location").orEmpty(),
            status = status,
            assignedHelperId = getString("assignedHelperId").orEmpty(),
            assignedHelperName = getString("assignedHelperName").orEmpty(),
            agreedPaymentMethod = enumFromStored<PaymentMethod>(getString("agreedPaymentMethod")),
            createdAt = getLong("createdAt") ?: 0L
        )
    } catch (_: Exception) { null }
}
private fun DocumentSnapshot.toApplication(): TaskApplication? = try {
    TaskApplication(
        id = id,
        taskId = getString("taskId").orEmpty(),
        helperId = getString("helperId").orEmpty(),
        helperName = getString("helperName").orEmpty(),
        preferredPaymentMethod = enumFromStored<PaymentMethod>(getString("preferredPaymentMethod")),
        status = enumFromStored<ApplicationStatus>(getString("status")) ?: ApplicationStatus.PENDING,
        createdAt = getLong("createdAt") ?: 0L
    )
} catch (_: Exception) { null }
private fun DocumentSnapshot.toPayment(): PaymentRecord? = try {
    PaymentRecord(
        id = id,
        taskId = getString("taskId").orEmpty(),
        taskTitle = getString("taskTitle").orEmpty(),
        payerId = getString("payerId").orEmpty(),
        payeeId = getString("payeeId").orEmpty(),
        otherPartyName = getString("otherPartyName").orEmpty(),
        amount = getDouble("amount") ?: 0.0,
        method = enumFromStored<PaymentMethod>(getString("method")),
        status = enumFromStored<PaymentStatus>(getString("status")) ?: PaymentStatus.PENDING,
        updatedAt = getLong("updatedAt") ?: 0L
    )
} catch (_: Exception) { null }
