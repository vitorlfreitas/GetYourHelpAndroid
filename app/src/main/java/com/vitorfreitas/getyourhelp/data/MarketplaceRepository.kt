package com.vitorfreitas.getyourhelp.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.enums.enumEntries

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * The only class that talks to Firebase: Authentication for accounts, Cloud Firestore for the data.
 * Functions named observeX return a live Flow, so every device sees changes without refreshing.
 * Who may change what is enforced by firestore.rules on the server.
 */
class MarketplaceRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val users = db.collection("users")
    private val tasks = db.collection("tasks")
    private val applications = db.collection("applications")
    private val payments = db.collection("paymentRecords")
    private val reports = db.collection("reports")
    private val reviews = db.collection("reviews")

    // Session

    fun currentUserId(): String? = auth.currentUser?.uid

    /** The signed-in user with their profile, or null if nobody is signed in. */
    suspend fun currentUser(): AppUser? {
        val user = auth.currentUser ?: return null
        val doc = users.document(user.uid).get().await()
        return AppUser(
            uid = user.uid,
            displayName = doc.getString("displayName") ?: user.displayName ?: user.email.orEmpty(),
            email = user.email.orEmpty(),
            helperCategories = doc.getStringList("helperCategories").mapNotNull(::categoryFromStored).distinct()
        )
    }

    suspend fun signIn(email: String, password: String): AppUser =
        finishSignIn(auth.signInWithEmailAndPassword(email.trim(), password).await().user)

    /** Exchanges the Google ID token for a Firebase session. The first sign-in creates the account. */
    suspend fun signInWithGoogle(idToken: String): AppUser =
        finishSignIn(auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user)

    suspend fun register(name: String, email: String, password: String): AppUser {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Registration did not return a user.")
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
        saveProfile(user.uid, name.trim(), email.trim())
        return AppUser(user.uid, name.trim(), email.trim())
    }

    /** Firebase does not say whether the account exists (privacy), so this succeeds either way. */
    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() = auth.signOut()

    suspend fun updateHelperCategories(user: AppUser, categories: List<TaskCategory>): AppUser {
        users.document(user.uid).update("helperCategories", categories.map { it.name }).await()
        return user.copy(helperCategories = categories)
    }

    /** Creates the profile the first time someone signs in (e.g. with Google), then loads it. */
    private suspend fun finishSignIn(user: FirebaseUser?): AppUser {
        user ?: error("Sign-in did not return a user.")
        if (!users.document(user.uid).get().await().exists()) {
            val email = user.email.orEmpty()
            saveProfile(user.uid, user.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@"), email)
        }
        return currentUser() ?: error("Could not load your profile.")
    }

    private suspend fun saveProfile(uid: String, name: String, email: String) {
        users.document(uid).set(mapOf(
            "uid" to uid, "displayName" to name, "email" to email,
            "helperCategories" to emptyList<String>(), "createdAt" to System.currentTimeMillis()
        )).await()
    }

    // Tasks

    /** All tasks, newest first. */
    fun observeTasks(): Flow<List<TaskItem>> =
        tasks.orderBy("createdAt", Query.Direction.DESCENDING).snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toTaskItem() } }

    /** One task, or null if it does not exist. */
    fun observeTask(taskId: String): Flow<TaskItem?> = tasks.document(taskId).snapshots().map { it.toTaskItem() }

    suspend fun getTask(taskId: String): TaskItem? = tasks.document(taskId).get().await().toTaskItem()

    /** Publishes a new task and returns its id. */
    suspend fun createTask(task: TaskItem): String {
        val ref = tasks.document()
        ref.set(task.toMap()).await()
        return ref.id
    }

    /**
     * Saves the requester's changes to an open task. Once someone has a pending application the
     * payment is fixed, because helpers applied for that offer.
     */
    suspend fun updateTask(original: TaskItem, edited: TaskItem) {
        val paymentChanged = edited.taskType != original.taskType || edited.amount != original.amount ||
            edited.acceptedPaymentMethods.toSet() != original.acceptedPaymentMethods.toSet()
        if (paymentChanged && hasPendingApplications(original.id)) {
            error("People have already applied, so the payment can't be changed. You can still edit the other details.")
        }
        val changes = edited.toMap().filterKeys { it in EDITABLE_FIELDS } + ("editedAt" to System.currentTimeMillis())
        updateIfOpen(original.id, changes, "This task is no longer open, so it can't be edited.")
    }

    /** Takes an open task off Browse. Pending applications are kept, so helpers see what happened. */
    suspend fun cancelTask(task: TaskItem) =
        updateIfOpen(task.id, mapOf("status" to TaskStatus.CANCELLED.name), "This task already has a helper, so it can't be cancelled.")

    /** The helper has finished; the requester then confirms with [completeTask]. */
    suspend fun markWorkDone(task: TaskItem) {
        tasks.document(task.id).update("workDoneAt", System.currentTimeMillis()).await()
    }

    /** The requester says the work isn't finished, so the helper's "done" mark is removed. */
    suspend fun markWorkNotFinished(task: TaskItem) {
        tasks.document(task.id).update("workDoneAt", null).await()
    }

    suspend fun completeTask(task: TaskItem) {
        tasks.document(task.id).update("status", TaskStatus.COMPLETED.name).await()
    }

    /** Changes a task inside a transaction, so nothing is changed if a helper was accepted in the meantime. */
    private suspend fun updateIfOpen(taskId: String, changes: Map<String, Any?>, notOpenMessage: String) {
        val ref = tasks.document(taskId)
        db.runTransaction { transaction ->
            if (transaction.get(ref).getString("status") != TaskStatus.OPEN.name) error(notOpenMessage)
            transaction.update(ref, changes)
            null
        }.await()
    }

    // Chat (subcollection tasks/{taskId}/messages)

    /**
     * The newest messages of a task's chat, oldest first. MetadataChanges.INCLUDE sends another update
     * when a message reaches the server, so "Sending…" turns into the time.
     */
    fun observeMessages(taskId: String): Flow<List<ChatMessage>> =
        messages(taskId).orderBy("sentAt").limitToLast(CHAT_HISTORY_LIMIT).snapshots(MetadataChanges.INCLUDE)
            .map { snapshot -> snapshot.documents.mapNotNull { it.toChatMessage() } }

    /** The message shows up at once from the local cache; the server sets the time. */
    suspend fun sendMessage(taskId: String, sender: AppUser, text: String) {
        messages(taskId).document().set(mapOf(
            "senderId" to sender.uid, "senderName" to sender.displayName,
            "text" to text.trim(), "sentAt" to FieldValue.serverTimestamp()
        )).await()
    }

    private fun messages(taskId: String) = tasks.document(taskId).collection("messages")

    // Applications

    /** Everyone who applied for a task, oldest first. Only the task owner may read them. */
    fun observeApplicationsForTask(taskId: String): Flow<List<TaskApplication>> =
        applications.whereEqualTo("taskId", taskId).snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toApplication() }.sortedBy { it.createdAt } }

    /** The applications this helper has sent, newest first. */
    fun observeMyApplications(userId: String): Flow<List<TaskApplication>> =
        applications.whereEqualTo("helperId", userId).snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toApplication() }.sortedByDescending { it.createdAt } }

    suspend fun hasPendingApplications(taskId: String): Boolean =
        applications.whereEqualTo("taskId", taskId).get().await()
            .documents.any { it.getString("status") == ApplicationStatus.PENDING.name }

    /**
     * The document id is "<taskId>_<helperId>", so a helper can apply only once. After a withdrawal
     * the same document is reused. Volunteer tasks have no payment method.
     */
    suspend fun applyToTask(task: TaskItem, helper: AppUser, preferredMethod: PaymentMethod?, message: String) {
        val application = TaskApplication(
            id = applicationId(task.id, helper.uid), taskId = task.id, helperId = helper.uid, helperName = helper.displayName,
            preferredPaymentMethod = preferredMethod.takeIf { task.taskType == TaskType.PAID }, message = message.trim()
        )
        applications.document(application.id).set(application.toMap()).await()
    }

    suspend fun withdrawApplication(application: TaskApplication) = setStatus(application, ApplicationStatus.WITHDRAWN)

    suspend fun declineApplication(application: TaskApplication) = setStatus(application, ApplicationStatus.DECLINED)

    private suspend fun setStatus(application: TaskApplication, status: ApplicationStatus) {
        applications.document(application.id).update("status", status.name).await()
    }

    /**
     * Accepts one helper in a transaction. Firestore only commits it if the task and applications
     * did not change meanwhile, so two helpers can never be accepted for the same task. In one write:
     * the application is ACCEPTED, the other pending ones DECLINED, the task ASSIGNED, and for a
     * paid task a payment record is created.
     */
    suspend fun acceptApplication(task: TaskItem, application: TaskApplication) {
        val taskRef = tasks.document(task.id)
        val chosenRef = applications.document(application.id)
        // A transaction cannot run queries, so the other applications are found first and re-read inside it
        val otherRefs = applications.whereEqualTo("taskId", task.id).get().await()
            .documents.map { it.reference }.filter { it.id != application.id }
        db.runTransaction { transaction ->
            // Firestore requires every read in a transaction to happen before the first write
            val latestTask = transaction.get(taskRef)
            val chosen = transaction.get(chosenRef)
            val others = otherRefs.map { transaction.get(it) }
            if (latestTask.getString("status") != TaskStatus.OPEN.name) error("This task already has a helper.")
            if (chosen.getString("status") != ApplicationStatus.PENDING.name) error("This application is no longer pending.")

            transaction.update(chosenRef, "status", ApplicationStatus.ACCEPTED.name)
            others.filter { it.getString("status") == ApplicationStatus.PENDING.name }
                .forEach { transaction.update(it.reference, "status", ApplicationStatus.DECLINED.name) }
            transaction.update(taskRef, mapOf(
                "status" to TaskStatus.ASSIGNED.name, "assignedHelperId" to application.helperId,
                "assignedHelperName" to application.helperName,
                "agreedPaymentMethod" to application.preferredPaymentMethod.toStored()
            ))
            if (task.taskType == TaskType.PAID) {
                val payment = PaymentRecord(
                    id = "", taskId = task.id, taskTitle = task.title, payerId = task.ownerId, payeeId = application.helperId,
                    otherPartyName = "${task.ownerName} and ${application.helperName}",
                    amount = task.amount, method = application.preferredPaymentMethod
                )
                transaction.set(payments.document(), payment.toMap())
            }
            null
        }.await()
    }

    // Reports and reviews

    suspend fun hasReported(taskId: String, userId: String): Boolean =
        reports.document(reportId(taskId, userId)).get().await().exists()

    /** The id "<taskId>_<reporterId>" and the transaction make sure each user reports a task only once. */
    suspend fun reportTask(task: TaskItem, user: AppUser, reason: String, details: String) {
        val ref = reports.document(reportId(task.id, user.uid))
        val report = TaskReport(ref.id, task.id, user.uid, reason, details.trim())
        db.runTransaction { transaction ->
            if (transaction.get(ref).exists()) error("You have already reported this task.")
            transaction.set(ref, report.toMap())
            null
        }.await()
    }

    /** After completion, the requester and the helper can each review the other once. */
    suspend fun submitReview(task: TaskItem, reviewer: AppUser, rating: Int, comment: String) {
        val reviewingHelper = reviewer.uid == task.ownerId
        val alreadyReviewed = reviews.whereEqualTo("reviewerId", reviewer.uid).get().await()
            .documents.any { it.getString("taskId") == task.id }
        if (alreadyReviewed) error("You have already reviewed this task.")
        val review = ReviewRecord(
            id = "", taskId = task.id, reviewerId = reviewer.uid,
            revieweeId = if (reviewingHelper) task.assignedHelperId else task.ownerId,
            revieweeName = if (reviewingHelper) task.assignedHelperName else task.ownerName,
            rating = rating, comment = comment.trim()
        )
        reviews.document().set(review.toMap()).await()
    }

    // Payments

    /** Payment records where the user is the payer or the payee, newest first. */
    suspend fun paymentRecords(userId: String): List<PaymentRecord> {
        val outgoing = payments.whereEqualTo("payerId", userId).get().await()
        val incoming = payments.whereEqualTo("payeeId", userId).get().await()
        return (outgoing.documents + incoming.documents).distinctBy { it.id }
            .mapNotNull { it.toPayment() }.sortedByDescending { it.updatedAt }
    }

    /** Only updates the record: the app never moves money. */
    suspend fun markPaymentRecorded(paymentId: String) {
        payments.document(paymentId)
            .update(mapOf("status" to PaymentStatus.MARKED_PAID.name, "updatedAt" to System.currentTimeMillis())).await()
    }

    private companion object {
        const val CHAT_HISTORY_LIMIT = 500L

        /** The task fields the requester can change when editing (firestore.rules allows the same list). */
        val EDITABLE_FIELDS = setOf(
            "title", "description", "category", "taskType", "amount", "acceptedPaymentMethods",
            "location", "coordinates", "scheduledAt"
        )
    }
}

/** Document id of a helper's application for a task. The security rules check this format. */
fun applicationId(taskId: String, helperId: String): String = "${taskId}_$helperId"

/** Document id of a user's report about a task. The security rules check this format. */
fun reportId(taskId: String, reporterId: String): String = "${taskId}_$reporterId"

// Enums are stored by name ("OPEN", "CASH"). Volunteer tasks have no payment method, stored as "NOT_APPLICABLE".
private fun PaymentMethod?.toStored(): String = this?.name ?: "NOT_APPLICABLE"

/** The enum value with this stored name, or null if it is missing or unknown. */
private inline fun <reified T : Enum<T>> enumFromStored(value: String?): T? = enumEntries<T>().firstOrNull { it.name == value }

/** Old tasks stored the category label ("Pet Care"), new ones the name ("PET_CARE"). */
private fun categoryFromStored(value: String?): TaskCategory? =
    TaskCategory.entries.firstOrNull { it.name == value || it.label == value }

private fun DocumentSnapshot.getStringList(field: String): List<String> =
    (get(field) as? List<*>)?.filterIsInstance<String>().orEmpty()

// Data classes -> Firestore documents
private fun TaskItem.toMap() = mapOf(
    "ownerId" to ownerId, "ownerName" to ownerName, "title" to title, "description" to description,
    "category" to category.name, "taskType" to taskType.name, "amount" to amount,
    "acceptedPaymentMethods" to acceptedPaymentMethods.map { it.name }, "location" to location,
    // A GeoPoint, so the security rules can check its type (latlng)
    "coordinates" to coordinates?.let { GeoPoint(it.latitude, it.longitude) },
    "scheduledAt" to scheduledAt, "status" to status.name, "assignedHelperId" to assignedHelperId,
    "assignedHelperName" to assignedHelperName, "agreedPaymentMethod" to agreedPaymentMethod?.name.orEmpty(),
    "workDoneAt" to workDoneAt, "editedAt" to editedAt, "createdAt" to createdAt
)
private fun TaskApplication.toMap() = mapOf(
    "taskId" to taskId, "helperId" to helperId, "helperName" to helperName,
    "preferredPaymentMethod" to preferredPaymentMethod.toStored(), "message" to message,
    "status" to status.name, "createdAt" to createdAt
)
private fun PaymentRecord.toMap() = mapOf(
    "taskId" to taskId, "taskTitle" to taskTitle, "payerId" to payerId, "payeeId" to payeeId,
    "otherPartyName" to otherPartyName, "amount" to amount, "method" to method.toStored(),
    "status" to status.name, "updatedAt" to updatedAt
)
private fun TaskReport.toMap() = mapOf(
    "taskId" to taskId, "reporterId" to reporterId, "reason" to reason,
    "details" to details, "status" to status, "createdAt" to createdAt
)
private fun ReviewRecord.toMap() = mapOf(
    "taskId" to taskId, "reviewerId" to reviewerId, "revieweeId" to revieweeId,
    "revieweeName" to revieweeName, "rating" to rating, "comment" to comment, "createdAt" to createdAt
)

// Firestore documents -> data classes. A missing or malformed document gives null and is skipped.
private fun DocumentSnapshot.toTaskItem(): TaskItem? {
    if (!exists()) return null
    // A status this version of the app does not know is skipped instead of shown as open
    val status = enumFromStored<TaskStatus>(getString("status") ?: TaskStatus.OPEN.name) ?: return null
    return try {
        TaskItem(
            id = id, ownerId = getString("ownerId").orEmpty(), ownerName = getString("ownerName").orEmpty(),
            title = getString("title").orEmpty(), description = getString("description").orEmpty(),
            category = categoryFromStored(getString("category")) ?: TaskCategory.OTHER,
            taskType = enumFromStored<TaskType>(getString("taskType")) ?: TaskType.VOLUNTEER,
            amount = getDouble("amount") ?: 0.0,
            acceptedPaymentMethods = getStringList("acceptedPaymentMethods").mapNotNull { enumFromStored<PaymentMethod>(it) },
            location = getString("location").orEmpty(),
            coordinates = getGeoPoint("coordinates")?.let { Coordinates(it.latitude, it.longitude) },
            scheduledAt = getLong("scheduledAt"), status = status,
            assignedHelperId = getString("assignedHelperId").orEmpty(),
            assignedHelperName = getString("assignedHelperName").orEmpty(),
            agreedPaymentMethod = enumFromStored<PaymentMethod>(getString("agreedPaymentMethod")),
            workDoneAt = getLong("workDoneAt"), editedAt = getLong("editedAt"), createdAt = getLong("createdAt") ?: 0L
        )
    } catch (_: Exception) { null }
}
private fun DocumentSnapshot.toApplication(): TaskApplication? = try {
    TaskApplication(
        id = id, taskId = getString("taskId").orEmpty(), helperId = getString("helperId").orEmpty(),
        helperName = getString("helperName").orEmpty(),
        preferredPaymentMethod = enumFromStored<PaymentMethod>(getString("preferredPaymentMethod")),
        message = getString("message").orEmpty(),
        status = enumFromStored<ApplicationStatus>(getString("status")) ?: ApplicationStatus.PENDING,
        createdAt = getLong("createdAt") ?: 0L
    )
} catch (_: Exception) { null }
private fun DocumentSnapshot.toChatMessage(): ChatMessage? = try {
    ChatMessage(
        id = id, senderId = getString("senderId").orEmpty(), senderName = getString("senderName").orEmpty(),
        text = getString("text").orEmpty(),
        // Until the server has the message its time is unknown, so ESTIMATE uses the local time meanwhile
        sentAt = getTimestamp("sentAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L,
        pending = metadata.hasPendingWrites()
    )
} catch (_: Exception) { null }
private fun DocumentSnapshot.toPayment(): PaymentRecord? = try {
    PaymentRecord(
        id = id, taskId = getString("taskId").orEmpty(), taskTitle = getString("taskTitle").orEmpty(),
        payerId = getString("payerId").orEmpty(), payeeId = getString("payeeId").orEmpty(),
        otherPartyName = getString("otherPartyName").orEmpty(), amount = getDouble("amount") ?: 0.0,
        method = enumFromStored<PaymentMethod>(getString("method")),
        status = enumFromStored<PaymentStatus>(getString("status")) ?: PaymentStatus.PENDING,
        updatedAt = getLong("updatedAt") ?: 0L
    )
} catch (_: Exception) { null }
