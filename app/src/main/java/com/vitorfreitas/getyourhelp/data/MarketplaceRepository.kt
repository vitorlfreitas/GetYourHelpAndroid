package com.vitorfreitas.getyourhelp.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MarketplaceRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("getyourhelp_local_demo", Context.MODE_PRIVATE)
    private val firebaseReady = try {
        FirebaseApp.getApps(appContext).isNotEmpty()
    } catch (_: Exception) {
        false
    }
    private val auth: FirebaseAuth? = if (firebaseReady) FirebaseAuth.getInstance() else null
    private val db: FirebaseFirestore? = if (firebaseReady) FirebaseFirestore.getInstance() else null

    val isFirebaseMode: Boolean get() = firebaseReady

    private val localTasks = MutableStateFlow(readTasks())
    private val localApplications = MutableStateFlow(readApplications())
    private val localPayments = MutableStateFlow(readPayments())
    private val localReports = MutableStateFlow(readReports())
    private val localReviews = MutableStateFlow(readReviews())

    init {
        seedDemoDataIfNeeded()
    }

    suspend fun currentUser(): AppUser? {
        if (firebaseReady) {
            val firebaseUser = auth?.currentUser ?: return null
            val doc = db!!.collection("users").document(firebaseUser.uid).get().await()
            val categories = (doc.get("helperCategories") as? List<*>)?.filterIsInstance<String>().orEmpty()
            return AppUser(
                uid = firebaseUser.uid,
                displayName = doc.getString("displayName") ?: firebaseUser.displayName ?: firebaseUser.email.orEmpty(),
                email = firebaseUser.email.orEmpty(),
                helperCategories = categories
            )
        }
        val uid = prefs.getString("demo_uid", null) ?: return null
        return demoUser(uid)
    }

    suspend fun signIn(email: String, password: String): AppUser {
        check(firebaseReady) { "Firebase is not configured. Add app/google-services.json first, or use a demo account." }
        val result = auth!!.signInWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Authentication did not return a user.")
        val docRef = db!!.collection("users").document(user.uid)
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

    suspend fun register(name: String, email: String, password: String): AppUser {
        check(firebaseReady) { "Firebase is not configured. Add app/google-services.json first." }
        require(name.isNotBlank()) { "Enter your name." }
        val result = auth!!.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Registration did not return a user.")
        val profile = mapOf(
            "uid" to user.uid,
            "displayName" to name.trim(),
            "email" to email.trim(),
            "helperCategories" to emptyList<String>(),
            "createdAt" to System.currentTimeMillis()
        )
        db!!.collection("users").document(user.uid).set(profile).await()
        return AppUser(user.uid, name.trim(), email.trim())
    }

    fun demoLogin(asRequester: Boolean): AppUser {
        check(!firebaseReady) { "Demo accounts are only shown when Firebase is not configured." }
        val user = if (asRequester) demoUser("demo-requester") else demoUser("demo-helper")
        prefs.edit().putString("demo_uid", user.uid).apply()
        return user
    }

    suspend fun signOut() {
        if (firebaseReady) auth?.signOut() else prefs.edit().remove("demo_uid").apply()
    }

    suspend fun updateHelperCategories(user: AppUser, categories: List<String>): AppUser {
        val updated = user.copy(helperCategories = categories.distinct())
        if (firebaseReady) {
            db!!.collection("users").document(user.uid)
                .update("helperCategories", updated.helperCategories).await()
        } else {
            prefs.edit().putString("categories_${user.uid}", JSONArray(updated.helperCategories).toString()).apply()
        }
        return updated
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTasks(): Flow<List<TaskItem>> {
        if (!firebaseReady) return localTasks.asStateFlow()
        return callbackFlow {
            val registration: ListenerRegistration = db!!.collection("tasks")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) close(error)
                    else trySend(snapshot?.documents?.mapNotNull { doc ->
                        doc.toTaskItem()
                    }.orEmpty())
                }
            awaitClose { registration.remove() }
        }
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
        if (firebaseReady) db!!.collection("tasks").document(task.id).set(task.toMap()).await()
        else updateLocalTasks(localTasks.value + task)
    }

    suspend fun applicationsForTask(taskId: String): List<TaskApplication> {
        if (firebaseReady) {
            return db!!.collection("applications").whereEqualTo("taskId", taskId).get().await()
                .documents.mapNotNull { it.toApplication() }
        }
        return localApplications.value.filter { it.taskId == taskId }
    }

    suspend fun myApplications(userId: String): List<TaskApplication> {
        if (firebaseReady) {
            return db!!.collection("applications").whereEqualTo("helperId", userId).get().await()
                .documents.mapNotNull { it.toApplication() }
        }
        return localApplications.value.filter { it.helperId == userId }
    }

    suspend fun applyToTask(task: TaskItem, helper: AppUser, preferredMethod: String) {
        require(task.ownerId != helper.uid) { "You cannot apply to your own task." }
        require(task.status == "OPEN") { "This task is no longer open." }
        if (task.taskType == "PAID") require(preferredMethod in task.acceptedPaymentMethods) {
            "Choose a payment method accepted by the requester."
        }
        val existing = if (firebaseReady) {
            db!!.collection("applications").whereEqualTo("helperId", helper.uid).get().await().documents
                .firstOrNull { it.getString("taskId") == task.id && it.getString("status") != "WITHDRAWN" }
        } else null
        if (existing != null || localApplications.value.any { it.taskId == task.id && it.helperId == helper.uid && it.status != "WITHDRAWN" }) {
            error("You have already applied for this task.")
        }
        val application = TaskApplication(
            id = UUID.randomUUID().toString(), taskId = task.id, helperId = helper.uid,
            helperName = helper.displayName, preferredPaymentMethod = preferredMethod
        )
        if (firebaseReady) db!!.collection("applications").document(application.id).set(application.toMap()).await()
        else updateLocalApplications(localApplications.value + application)
    }

    suspend fun acceptApplication(task: TaskItem, application: TaskApplication) {
        require(task.ownerId == currentUser()?.uid) { "Only the requester can accept an application." }
        require(task.status == "OPEN") { "This task is no longer open." }
        if (firebaseReady) {
            val firestore = db ?: error("Firebase database is not available.")
            val batch = firestore.batch()
            val existingApplications = firestore.collection("applications").whereEqualTo("taskId", task.id).get().await()
            existingApplications.documents.forEach { document ->
                val newStatus = if (document.id == application.id) "ACCEPTED" else "DECLINED"
                if ((document.getString("status") ?: "PENDING") == "PENDING") {
                    batch.update(document.reference, "status", newStatus)
                }
            }
            batch.update(firestore.collection("tasks").document(task.id), mapOf(
                "status" to "ASSIGNED", "assignedHelperId" to application.helperId,
                "assignedHelperName" to application.helperName,
                "agreedPaymentMethod" to application.preferredPaymentMethod
            ))
            if (task.taskType == "PAID") {
                val payment = newPayment(task, application.helperId, application.helperName, application.preferredPaymentMethod)
                batch.set(firestore.collection("paymentRecords").document(payment.id), payment.toMap())
            }
            batch.commit().await()
        } else {
            updateLocalApplications(localApplications.value.map {
                if (it.id == application.id) it.copy(status = "ACCEPTED")
                else if (it.taskId == task.id && it.status == "PENDING") it.copy(status = "DECLINED") else it
            })
            updateLocalTasks(localTasks.value.map {
                if (it.id == task.id) it.copy(status = "ASSIGNED", assignedHelperId = application.helperId,
                    assignedHelperName = application.helperName, agreedPaymentMethod = application.preferredPaymentMethod) else it
            })
            if (task.taskType == "PAID") {
                val payment = newPayment(task, application.helperId, application.helperName, application.preferredPaymentMethod)
                updateLocalPayments(localPayments.value + payment)
            }
        }
    }

    private fun newPayment(task: TaskItem, helperId: String, helperName: String, method: String) = PaymentRecord(
        id = UUID.randomUUID().toString(), taskId = task.id, taskTitle = task.title,
        payerId = task.ownerId, payeeId = helperId, otherPartyName = "${task.ownerName} and $helperName",
        amount = task.amount, method = method
    )

    suspend fun completeTask(task: TaskItem, user: AppUser) {
        require(task.ownerId == user.uid) { "Only the requester can confirm task completion." }
        require(task.status == "ASSIGNED") { "Only an assigned task can be completed." }
        if (firebaseReady) {
            db!!.collection("tasks").document(task.id).update("status", "COMPLETED").await()
        } else updateLocalTasks(localTasks.value.map { if (it.id == task.id) it.copy(status = "COMPLETED") else it })
    }

    suspend fun reportTask(task: TaskItem, user: AppUser, reason: String, details: String) {
        require(reason.isNotBlank()) { "Select a report reason." }
        val report = TaskReport(UUID.randomUUID().toString(), task.id, user.uid, reason, details.trim())
        if (firebaseReady) db!!.collection("reports").document(report.id).set(report.toMap()).await()
        else updateLocalReports(localReports.value + report)
    }

    suspend fun submitReview(task: TaskItem, reviewer: AppUser, rating: Int, comment: String) {
        require(task.status == "COMPLETED") { "Reviews are available after a task is completed." }
        require(rating in 1..5) { "Choose a rating from 1 to 5." }
        val revieweeId = if (reviewer.uid == task.ownerId) task.assignedHelperId else task.ownerId
        val revieweeName = if (reviewer.uid == task.ownerId) task.assignedHelperName else task.ownerName
        require(revieweeId.isNotBlank() && reviewer.uid in listOf(task.ownerId, task.assignedHelperId)) {
            "Only the requester and accepted helper can review this task."
        }
        if (firebaseReady) {
            val existing = db!!.collection("reviews").whereEqualTo("reviewerId", reviewer.uid)
                .get().await().documents.firstOrNull { it.getString("taskId") == task.id }
            require(existing == null) { "You have already reviewed this task." }
        } else {
            require(localReviews.value.none { it.taskId == task.id && it.reviewerId == reviewer.uid }) {
                "You have already reviewed this task."
            }
        }
        val review = ReviewRecord(UUID.randomUUID().toString(), task.id, reviewer.uid, revieweeId,
            revieweeName, rating, comment.trim())
        if (firebaseReady) db!!.collection("reviews").document(review.id).set(review.toMap()).await()
        else updateLocalReviews(localReviews.value + review)
    }

    suspend fun paymentRecords(userId: String): List<PaymentRecord> {
        if (firebaseReady) {
            val firestore = db ?: error("Firebase database is not available.")
            val outgoing = firestore.collection("paymentRecords").whereEqualTo("payerId", userId).get().await()
            val incoming = firestore.collection("paymentRecords").whereEqualTo("payeeId", userId).get().await()
            return (outgoing.documents + incoming.documents).distinctBy { it.id }
                .mapNotNull { it.toPayment() }.sortedByDescending { it.updatedAt }
        }
        return localPayments.value.filter { it.payerId == userId || it.payeeId == userId }
            .sortedByDescending { it.updatedAt }
    }

    suspend fun markPaymentRecorded(paymentId: String, user: AppUser) {
        if (firebaseReady) {
            val ref = db!!.collection("paymentRecords").document(paymentId)
            val doc = ref.get().await()
            val payer = doc.getString("payerId")
            val payee = doc.getString("payeeId")
            require(user.uid == payer || user.uid == payee) { "You are not part of this payment record." }
            ref.update(mapOf("status" to "MARKED_PAID", "updatedAt" to System.currentTimeMillis())).await()
        } else {
            updateLocalPayments(localPayments.value.map {
                if (it.id == paymentId && user.uid in listOf(it.payerId, it.payeeId)) it.copy(status = "MARKED_PAID", updatedAt = System.currentTimeMillis()) else it
            })
        }
    }

    private fun demoUser(uid: String): AppUser {
        val defaults = if (uid == "demo-requester") listOf("Home Help") else listOf("Pet Care", "Gardening", "Shopping")
        val stored: List<String> = try {
            val array = JSONArray(prefs.getString("categories_$uid", "[]"))
            (0 until array.length()).map { array.optString(it) }
        } catch (_: Exception) { emptyList() }
        val selected = if (stored.isEmpty()) defaults else stored
        return if (uid == "demo-requester") AppUser(uid, "Demo Requester", "requester@getyourhelp.demo", selected)
        else AppUser(uid, "Demo Helper", "helper@getyourhelp.demo", selected)
    }

    private fun seedDemoDataIfNeeded() {
        if (firebaseReady || localTasks.value.isNotEmpty()) return
        val samples = listOf(
            TaskItem("sample-dog-walk", "demo-requester", "Demo Requester", "Walk my dog for one hour",
                "Looking for someone friendly to walk my dog this afternoon. Please message before accepting.",
                "Pet Care", "PAID", 15.0, listOf("CASH", "BANK_TRANSFER"), "Cork City Centre"),
            TaskItem("sample-garden", "demo-requester", "Demo Requester", "Help with garden cleanup",
                "Need help collecting leaves and tidying a small garden. Gloves provided.",
                "Gardening", "PAID", 25.0, listOf("CASH"), "Douglas, Cork"),
            TaskItem("sample-volunteer", "demo-helper", "Demo Helper", "Community food bank volunteer",
                "Help sort donations for a local community food bank. No payment; lunch provided.",
                "Volunteering", "VOLUNTEER", 0.0, emptyList(), "Cork City")
        )
        updateLocalTasks(samples)
    }

    private fun updateLocalTasks(value: List<TaskItem>) { localTasks.value = value; prefs.edit().putString("tasks", JSONArray().apply { value.forEach { put(it.toJson()) } }.toString()).apply() }
    private fun updateLocalApplications(value: List<TaskApplication>) { localApplications.value = value; prefs.edit().putString("applications", JSONArray().apply { value.forEach { put(it.toJson()) } }.toString()).apply() }
    private fun updateLocalPayments(value: List<PaymentRecord>) { localPayments.value = value; prefs.edit().putString("payments", JSONArray().apply { value.forEach { put(it.toJson()) } }.toString()).apply() }
    private fun updateLocalReports(value: List<TaskReport>) { localReports.value = value; prefs.edit().putString("reports", JSONArray().apply { value.forEach { put(it.toJson()) } }.toString()).apply() }
    private fun updateLocalReviews(value: List<ReviewRecord>) { localReviews.value = value; prefs.edit().putString("reviews", JSONArray().apply { value.forEach { put(it.toJson()) } }.toString()).apply() }

    private fun readTasks() = readArray("tasks") { it.toTaskItem() }
    private fun readApplications() = readArray("applications") { it.toApplication() }
    private fun readPayments() = readArray("payments") { it.toPayment() }
    private fun readReports() = readArray("reports") { it.toReport() }
    private fun readReviews() = readArray("reviews") { it.toReview() }
    private fun <T> readArray(key: String, parser: (JSONObject) -> T?): List<T> {
        val array = try { JSONArray(prefs.getString(key, "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until array.length()).mapNotNull { index -> try { parser(array.getJSONObject(index)) } catch (_: Exception) { null } }
    }
}

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

private fun TaskItem.toJson() = JSONObject().apply { toMap().forEach { (k, v) -> put(k, v) } }
private fun TaskApplication.toJson() = JSONObject().apply { toMap().forEach { (k, v) -> put(k, v) } }
private fun PaymentRecord.toJson() = JSONObject().apply { toMap().forEach { (k, v) -> put(k, v) } }
private fun TaskReport.toJson() = JSONObject().apply { toMap().forEach { (k, v) -> put(k, v) } }
private fun ReviewRecord.toJson() = JSONObject().apply { toMap().forEach { (k, v) -> put(k, v) } }

private fun JSONObject.toTaskItem() = TaskItem(
    id = optString("id"), ownerId = optString("ownerId"), ownerName = optString("ownerName"),
    title = optString("title"), description = optString("description"), category = optString("category"),
    taskType = optString("taskType"), amount = optDouble("amount"),
    acceptedPaymentMethods = optJSONArray("acceptedPaymentMethods").toStringList(), location = optString("location"),
    status = optString("status", "OPEN"), assignedHelperId = optString("assignedHelperId"),
    assignedHelperName = optString("assignedHelperName"), agreedPaymentMethod = optString("agreedPaymentMethod"),
    createdAt = optLong("createdAt")
)
private fun JSONObject.toApplication() = TaskApplication(
    optString("id"), optString("taskId"), optString("helperId"), optString("helperName"),
    optString("preferredPaymentMethod"), optString("status", "PENDING"), optLong("createdAt")
)
private fun JSONObject.toPayment() = PaymentRecord(
    optString("id"), optString("taskId"), optString("taskTitle"), optString("payerId"), optString("payeeId"),
    optString("otherPartyName"), optDouble("amount"), optString("method"), optString("status", "PENDING"), optLong("updatedAt")
)
private fun JSONObject.toReport() = TaskReport(
    optString("id"), optString("taskId"), optString("reporterId"), optString("reason"),
    optString("details"), optString("status", "SUBMITTED"), optLong("createdAt")
)
private fun JSONObject.toReview() = ReviewRecord(
    optString("id"), optString("taskId"), optString("reviewerId"), optString("revieweeId"),
    optString("revieweeName"), optInt("rating"), optString("comment"), optLong("createdAt")
)
private fun com.google.firebase.firestore.DocumentSnapshot.toTaskItem(): TaskItem? = try {
    TaskItem(id = id, ownerId = getString("ownerId").orEmpty(), ownerName = getString("ownerName").orEmpty(),
        title = getString("title").orEmpty(), description = getString("description").orEmpty(), category = getString("category").orEmpty(),
        taskType = getString("taskType").orEmpty(), amount = getDouble("amount") ?: 0.0,
        acceptedPaymentMethods = (get("acceptedPaymentMethods") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        location = getString("location").orEmpty(), status = getString("status") ?: "OPEN",
        assignedHelperId = getString("assignedHelperId").orEmpty(), assignedHelperName = getString("assignedHelperName").orEmpty(),
        agreedPaymentMethod = getString("agreedPaymentMethod").orEmpty(), createdAt = getLong("createdAt") ?: 0L)
} catch (_: Exception) { null }
private fun com.google.firebase.firestore.DocumentSnapshot.toApplication(): TaskApplication? = try {
    TaskApplication(id, getString("taskId").orEmpty(), getString("helperId").orEmpty(), getString("helperName").orEmpty(),
        getString("preferredPaymentMethod").orEmpty(), getString("status") ?: "PENDING", getLong("createdAt") ?: 0L)
} catch (_: Exception) { null }
private fun com.google.firebase.firestore.DocumentSnapshot.toPayment(): PaymentRecord? = try {
    PaymentRecord(id, getString("taskId").orEmpty(), getString("taskTitle").orEmpty(), getString("payerId").orEmpty(),
        getString("payeeId").orEmpty(), getString("otherPartyName").orEmpty(), getDouble("amount") ?: 0.0,
        getString("method").orEmpty(), getString("status") ?: "PENDING", getLong("updatedAt") ?: 0L)
} catch (_: Exception) { null }
private fun JSONArray?.toStringList(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }
