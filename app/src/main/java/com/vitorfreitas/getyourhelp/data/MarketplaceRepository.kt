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
import com.vitorfreitas.getyourhelp.validation.ChatValidator
import com.vitorfreitas.getyourhelp.validation.TaskValidator
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
 * Firebase Authentication handles accounts (email and password, or Google).
 * Cloud Firestore stores the shared data in the collections users, tasks (each with a messages
 * subcollection for its chat), applications, paymentRecords, reports and reviews.
 *
 * Functions named observeX return a Flow: Firestore pushes a new value whenever the data changes,
 * so every device sees changes without refreshing.
 */
class MarketplaceRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    /** The id of the signed-in user, or null when nobody is signed in. */
    fun currentUserId(): String? = auth.currentUser?.uid

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
        ensureProfile(user, email.trim())
        return currentUser() ?: error("Could not load user profile.")
    }

    /**
     * Signs in with the Google ID token from GoogleSignInHelper. Firebase creates the account the
     * first time, and the profile takes its name from the Google account.
     */
    suspend fun signInWithGoogle(idToken: String): AppUser {
        val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        val user = result.user ?: error("Google sign-in did not return a user.")
        ensureProfile(user, user.email.orEmpty())
        return currentUser() ?: error("Could not load user profile.")
    }

    /** Creates the user's profile document the first time they sign in. */
    private suspend fun ensureProfile(user: FirebaseUser, email: String) {
        val docRef = db.collection("users").document(user.uid)
        if (docRef.get().await().exists()) return
        val profile = mapOf(
            "uid" to user.uid,
            "displayName" to (user.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")),
            "email" to email,
            "helperCategories" to emptyList<String>(),
            "createdAt" to System.currentTimeMillis()
        )
        docRef.set(profile).await()
    }

    /** Creates a Firebase Authentication account and its profile document in `users`. */
    suspend fun register(name: String, email: String, password: String): AppUser {
        require(name.isNotBlank()) { "Enter your name." }
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Registration did not return a user.")
        // Also store the name on the Firebase account itself, not only in Firestore.
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
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

    /**
     * Asks Firebase to email a password reset link. For privacy, Firebase does not say whether an
     * account exists for the address, so this succeeds either way.
     */
    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
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

    // Tasks

    /** Live list of all tasks, newest first. */
    fun observeTasks(): Flow<List<TaskItem>> = callbackFlow {
        val registration = db.collection("tasks")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error)
                else trySend(snapshot?.documents?.mapNotNull { it.toTaskItem() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    /** Live copy of one task. Emits null if the task does not exist (for example, it was deleted). */
    fun observeTask(taskId: String): Flow<TaskItem?> = callbackFlow {
        val registration = db.collection("tasks").document(taskId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error)
                else trySend(snapshot?.takeIf { it.exists() }?.toTaskItem())
            }
        awaitClose { registration.remove() }
    }

    /** The task as it is now, or null if it does not exist. */
    suspend fun getTask(taskId: String): TaskItem? =
        db.collection("tasks").document(taskId).get().await().takeIf { it.exists() }?.toTaskItem()

    /**
     * Publishes a task and returns its id. The form is validated by TaskValidator before this is called.
     * [coordinates] is the approximate position of [location], or null if it is not known.
     */
    suspend fun createTask(
        owner: AppUser,
        title: String,
        description: String,
        category: TaskCategory,
        taskType: TaskType,
        amount: Double,
        paymentMethods: List<PaymentMethod>,
        location: String,
        coordinates: Coordinates?,
        scheduledAt: Long?
    ): String {
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
            location = location.trim(), coordinates = coordinates, scheduledAt = scheduledAt
        )
        db.collection("tasks").document(task.id).set(task.toMap()).await()
        return task.id
    }

    /**
     * Saves the requester's changes to an open task. Once someone has a pending application, the
     * payment (type, amount and methods) is fixed, because helpers applied for that offer.
     * The transaction re-reads the task, so a task that got a helper in the meantime is not changed.
     */
    suspend fun updateTask(
        task: TaskItem,
        title: String,
        description: String,
        category: TaskCategory,
        taskType: TaskType,
        amount: Double,
        paymentMethods: List<PaymentMethod>,
        location: String,
        coordinates: Coordinates?,
        scheduledAt: Long?
    ) {
        require(task.ownerId == auth.currentUser?.uid) { "Only the requester can edit this task." }
        require(title.isNotBlank() && description.isNotBlank() && location.isNotBlank()) { "Fill in every required field." }
        val isPaid = taskType == TaskType.PAID
        val newAmount = if (isPaid) amount else 0.0
        val newMethods = if (isPaid) paymentMethods.distinct() else emptyList()
        if (isPaid) require(newAmount > 0.0 && newMethods.isNotEmpty()) { "Enter an amount and at least one payment method." }
        val paymentChanged = taskType != task.taskType || newAmount != task.amount ||
            newMethods.toSet() != task.acceptedPaymentMethods.toSet()
        if (paymentChanged && hasPendingApplications(task.id)) {
            error("People have already applied, so the payment can't be changed. You can still edit the other details.")
        }
        val taskRef = db.collection("tasks").document(task.id)
        db.runTransaction { transaction ->
            if (transaction.get(taskRef).getString("status") != TaskStatus.OPEN.name) {
                error("This task is no longer open, so it can't be edited.")
            }
            transaction.update(taskRef, mapOf(
                "title" to title.trim(), "description" to description.trim(), "category" to category.name,
                "taskType" to taskType.name, "amount" to newAmount,
                "acceptedPaymentMethods" to newMethods.map { it.name },
                "location" to location.trim(), "coordinates" to coordinates?.toGeoPoint(),
                "scheduledAt" to scheduledAt, "editedAt" to System.currentTimeMillis()
            ))
            null
        }.await()
    }

    /**
     * The requester cancels an open task: it leaves Browse and nobody can apply any more. Pending
     * applications are kept, so helpers can see what happened to them.
     */
    suspend fun cancelTask(task: TaskItem) {
        require(task.ownerId == auth.currentUser?.uid) { "Only the requester can cancel this task." }
        val taskRef = db.collection("tasks").document(task.id)
        db.runTransaction { transaction ->
            if (transaction.get(taskRef).getString("status") != TaskStatus.OPEN.name) {
                error("This task already has a helper, so it can't be cancelled.")
            }
            transaction.update(taskRef, "status", TaskStatus.CANCELLED.name)
            null
        }.await()
    }

    /** The accepted helper says the work is finished. The requester then confirms it with [completeTask]. */
    suspend fun markWorkDone(task: TaskItem) {
        require(task.assignedHelperId == auth.currentUser?.uid) { "Only the helper can mark the work as done." }
        require(task.status == TaskStatus.ASSIGNED) { "Only an ongoing task can be marked as done." }
        db.collection("tasks").document(task.id).update("workDoneAt", System.currentTimeMillis()).await()
    }

    /** The requester says the work is not finished yet. The helper's "done" mark is removed and the task stays ongoing. */
    suspend fun markWorkNotFinished(task: TaskItem) {
        require(task.ownerId == auth.currentUser?.uid) { "Only the requester can do this." }
        require(task.status == TaskStatus.ASSIGNED && task.workDoneAt != null) { "The helper has not marked the work as done." }
        db.collection("tasks").document(task.id).update("workDoneAt", null).await()
    }

    /** The requester confirms the work the helper marked as done: ASSIGNED becomes COMPLETED. */
    suspend fun completeTask(task: TaskItem, user: AppUser) {
        require(task.ownerId == user.uid) { "Only the requester can confirm task completion." }
        require(task.status == TaskStatus.ASSIGNED) { "Only an ongoing task can be completed." }
        require(task.workDoneAt != null) { "Wait until the helper marks the work as done." }
        db.collection("tasks").document(task.id).update("status", TaskStatus.COMPLETED.name).await()
    }

    // Chat

    /**
     * Live list of a task's chat messages, oldest first (the newest [CHAT_HISTORY_LIMIT]).
     * MetadataChanges.INCLUDE sends an update when a message reaches the server, so "Sending…" changes to its time.
     */
    fun observeMessages(taskId: String): Flow<List<ChatMessage>> = callbackFlow {
        val registration = messages(taskId)
            .orderBy("sentAt", Query.Direction.ASCENDING)
            .limitToLast(CHAT_HISTORY_LIMIT)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) close(error)
                else trySend(snapshot?.documents?.mapNotNull { it.toChatMessage() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    /**
     * Sends a chat message. It appears on screen at once (Firestore's local cache), and the
     * server stamps the time. Only the requester and the helper can write, and only while the task is ongoing.
     */
    suspend fun sendMessage(task: TaskItem, sender: AppUser, text: String) {
        ChatValidator.message(text)?.let { throw IllegalArgumentException(it) }
        require(task.chatIsOpen) { "The chat is closed because the task is completed." }
        require(task.hasChatFor(sender.uid)) { "Only the requester and the helper can use this chat." }
        val message = mapOf(
            "senderId" to sender.uid,
            "senderName" to sender.displayName,
            "text" to text.trim(),
            "sentAt" to FieldValue.serverTimestamp()
        )
        messages(task.id).document().set(message).await()
    }

    private fun messages(taskId: String) = db.collection("tasks").document(taskId).collection("messages")

    // ---------------------------------------------------------------- Applications

    /** Live list of everyone who applied for a task, oldest first. The security rules only allow the task owner to read it. */
    fun observeApplicationsForTask(taskId: String): Flow<List<TaskApplication>> = callbackFlow {
        val registration = db.collection("applications").whereEqualTo("taskId", taskId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error)
                else trySend(snapshot?.documents?.mapNotNull { it.toApplication() }.orEmpty().sortedBy { it.createdAt })
            }
        awaitClose { registration.remove() }
    }

    /** Live list of the applications this helper has sent, newest first. */
    fun observeMyApplications(userId: String): Flow<List<TaskApplication>> = callbackFlow {
        val registration = db.collection("applications").whereEqualTo("helperId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error)
                else trySend(snapshot?.documents?.mapNotNull { it.toApplication() }.orEmpty().sortedByDescending { it.createdAt })
            }
        awaitClose { registration.remove() }
    }

    /** True if someone is waiting for an answer on this task. Only the task owner can ask. */
    suspend fun hasPendingApplications(taskId: String): Boolean =
        db.collection("applications").whereEqualTo("taskId", taskId).get().await()
            .documents.any { it.getString("status") == ApplicationStatus.PENDING.name }

    /**
     * Sends a PENDING application. preferredMethod is ignored (stored as not applicable) for volunteer tasks.
     * The document id is "<taskId>_<helperId>", so applying twice is impossible; after a withdrawal the same
     * document is reused (the security rules allow WITHDRAWN -> PENDING while the task is still open).
     */
    suspend fun applyToTask(task: TaskItem, helper: AppUser, preferredMethod: PaymentMethod?, message: String) {
        require(task.ownerId != helper.uid) { "You cannot apply to your own task." }
        val isPaid = task.taskType == TaskType.PAID
        if (isPaid) require(preferredMethod != null && preferredMethod in task.acceptedPaymentMethods) {
            "Choose a payment method accepted by the requester."
        }
        TaskValidator.applicationMessage(message)?.let { throw IllegalArgumentException(it) }
        // Check the latest status on the server, in case a helper was accepted after the screen was opened.
        val latest = db.collection("tasks").document(task.id).get().await().toTaskItem()
        require(latest?.status == TaskStatus.OPEN) { "This task is no longer open." }
        val application = TaskApplication(
            id = applicationId(task.id, helper.uid), taskId = task.id, helperId = helper.uid,
            helperName = helper.displayName, preferredPaymentMethod = if (isPaid) preferredMethod else null,
            message = message.trim()
        )
        db.collection("applications").document(application.id).set(application.toMap()).await()
    }

    /** The helper takes back a pending application. They can apply again while the task is open. */
    suspend fun withdrawApplication(application: TaskApplication) {
        require(application.helperId == auth.currentUser?.uid) { "You can only withdraw your own application." }
        require(application.status == ApplicationStatus.PENDING) { "Only a pending application can be withdrawn." }
        db.collection("applications").document(application.id)
            .update("status", ApplicationStatus.WITHDRAWN.name).await()
    }

    /** The requester turns down one pending application. The task stays open for others. */
    suspend fun declineApplication(task: TaskItem, application: TaskApplication) {
        require(task.ownerId == auth.currentUser?.uid) { "Only the requester can decline an application." }
        require(application.status == ApplicationStatus.PENDING) { "Only a pending application can be declined." }
        db.collection("applications").document(application.id)
            .update("status", ApplicationStatus.DECLINED.name).await()
    }

    /**
     * Accepts one helper, inside a Firestore transaction. The transaction first reads the latest task and
     * applications, and Firestore only commits it if none of them changed in the meantime, so two
     * helpers can never be accepted for the same task, even if two devices tap "Accept" at once.
     * In one atomic write:
     * - the chosen application becomes ACCEPTED and the other pending ones DECLINED
     * - the task becomes ASSIGNED, with the helper and the agreed payment method
     * - for paid tasks, a PENDING payment record is created
     */
    suspend fun acceptApplication(task: TaskItem, application: TaskApplication) {
        require(task.ownerId == auth.currentUser?.uid) { "Only the requester can accept an application." }
        val taskRef = db.collection("tasks").document(task.id)
        val chosenRef = db.collection("applications").document(application.id)
        // A transaction cannot run queries, so find the other applications first and re-read them inside it.
        val otherRefs = db.collection("applications").whereEqualTo("taskId", task.id).get().await()
            .documents.map { it.reference }.filter { it.id != application.id }
        db.runTransaction { transaction ->
            // Firestore requires every read in a transaction to happen before the first write.
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
                    id = UUID.randomUUID().toString(), taskId = task.id, taskTitle = task.title,
                    payerId = task.ownerId, payeeId = application.helperId,
                    otherPartyName = "${task.ownerName} and ${application.helperName}",
                    amount = task.amount, method = application.preferredPaymentMethod
                )
                transaction.set(db.collection("paymentRecords").document(payment.id), payment.toMap())
            }
            null
        }.await()
    }

    // Reports and reviews

    /** True if this user has already reported the task. */
    suspend fun hasReported(taskId: String, userId: String): Boolean =
        db.collection("reports").document(reportId(taskId, userId)).get().await().exists()

    /**
     * Saves a report for later review. The task is not removed automatically. The document id is
     * "<taskId>_<reporterId>", and the transaction checks it does not exist yet, so each user can
     * report a task only once.
     */
    suspend fun reportTask(task: TaskItem, user: AppUser, reason: String, details: String) {
        require(reason.isNotBlank()) { "Select a report reason." }
        val reportRef = db.collection("reports").document(reportId(task.id, user.uid))
        val report = TaskReport(reportRef.id, task.id, user.uid, reason, details.trim())
        db.runTransaction { transaction ->
            if (transaction.get(reportRef).exists()) error("You have already reported this task.")
            transaction.set(reportRef, report.toMap())
            null
        }.await()
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

    // Payments

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

    private companion object {
        /** A task chat loads at most this many of its latest messages. */
        const val CHAT_HISTORY_LIMIT = 500L
    }
}

/** Document id of a helper's application for a task. The security rules check this format. */
fun applicationId(taskId: String, helperId: String): String = "${taskId}_$helperId"

/** Document id of a user's report about a task, so each user can report a task only once. The security rules check this format. */
fun reportId(taskId: String, reporterId: String): String = "${taskId}_$reporterId"

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

// Positions are stored as Firestore GeoPoints, which the security rules can check (type latlng).
private fun Coordinates.toGeoPoint() = GeoPoint(latitude, longitude)

// Converting data classes to Firestore documents (field name -> value).
private fun TaskItem.toMap() = mapOf(
    "id" to id, "ownerId" to ownerId, "ownerName" to ownerName, "title" to title,
    "description" to description, "category" to category.name, "taskType" to taskType.name, "amount" to amount,
    "acceptedPaymentMethods" to acceptedPaymentMethods.map { it.name }, "location" to location,
    "coordinates" to coordinates?.toGeoPoint(), "scheduledAt" to scheduledAt, "status" to status.name,
    "assignedHelperId" to assignedHelperId, "assignedHelperName" to assignedHelperName,
    "agreedPaymentMethod" to agreedPaymentMethod?.name.orEmpty(), "workDoneAt" to workDoneAt,
    "editedAt" to editedAt, "createdAt" to createdAt
)
private fun TaskApplication.toMap() = mapOf(
    "id" to id, "taskId" to taskId, "helperId" to helperId, "helperName" to helperName,
    "preferredPaymentMethod" to preferredPaymentMethod.toStored(), "message" to message,
    "status" to status.name, "createdAt" to createdAt
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
            coordinates = getGeoPoint("coordinates")?.let { Coordinates(it.latitude, it.longitude) },
            scheduledAt = getLong("scheduledAt"),
            status = status,
            assignedHelperId = getString("assignedHelperId").orEmpty(),
            assignedHelperName = getString("assignedHelperName").orEmpty(),
            agreedPaymentMethod = enumFromStored<PaymentMethod>(getString("agreedPaymentMethod")),
            workDoneAt = getLong("workDoneAt"),
            editedAt = getLong("editedAt"),
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
        message = getString("message").orEmpty(),
        status = enumFromStored<ApplicationStatus>(getString("status")) ?: ApplicationStatus.PENDING,
        createdAt = getLong("createdAt") ?: 0L
    )
} catch (_: Exception) { null }
private fun DocumentSnapshot.toChatMessage(): ChatMessage? = try {
    ChatMessage(
        id = id,
        senderId = getString("senderId").orEmpty(),
        senderName = getString("senderName").orEmpty(),
        text = getString("text").orEmpty(),
        // While a message is still on its way, the server time is unknown: ESTIMATE uses the local time until then.
        sentAt = getTimestamp("sentAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L,
        pending = metadata.hasPendingWrites()
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
