package com.example.data

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Lean Firestore realtime observer & profile initializer used by [RoomFirestoreSyncService].
 * All mutations flow exclusively through [LocalPlannerRepository] + the transactional Room outbox
 * so offline-first sync and `firestore.rules` schemas never diverge.
 */
class PlannerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        }
        if (expectedUserId != null && uid != expectedUserId) {
            throw IllegalStateException("Signed-in user changed; refusing cross-user access.")
        }
        return uid
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun itemsCol(uid: String) = userDoc(uid).collection("items")
    private fun stepsCol(uid: String) = userDoc(uid).collection("steps")
    private fun remindersCol(uid: String) = userDoc(uid).collection("reminders")
    private fun classSchedulesCol(uid: String) = userDoc(uid).collection("classSchedules")

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid"
        emitAll(
            userDoc(uid).snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    fun observeItems(userId: String): Flow<List<PlannerItem>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/items"
        emitAll(
            itemsCol(uid).snapshots()
                .map { snap ->
                    snap.toObjects(
                        PlannerItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeSteps(userId: String): Flow<List<PlannerStep>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/steps"
        emitAll(
            stepsCol(uid).snapshots()
                .map { snap ->
                    snap.toObjects(
                        PlannerStep::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeReminders(userId: String): Flow<List<PlannerReminder>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/reminders"
        emitAll(
            remindersCol(uid).snapshots()
                .map { snap ->
                    snap.toObjects(
                        PlannerReminder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeClassSchedules(userId: String): Flow<List<ClassScheduleItem>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/classSchedules"
        emitAll(
            classSchedulesCol(uid).snapshots()
                .map { snap ->
                    snap.toObjects(
                        ClassScheduleItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.startTimeMinutes }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun ensureProfileExists(userId: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val docRef = userDoc(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "dedicationText" to UserProfile.DEFAULT_DEDICATION,
                    "hasSeenDedication" to false,
                    "defaultReminderHour" to 9,
                    "defaultReminderMinute" to 0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        op: OperationType,
        path: String,
        uid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }
}
