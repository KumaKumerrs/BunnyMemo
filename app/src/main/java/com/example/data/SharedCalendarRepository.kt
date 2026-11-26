package com.example.data

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.notifications.ReminderScheduler
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * Coordinates sharing scheduled dates via email + Firestore (`calendarInvites` & `replies`),
 * receiving friend replies on planned dates, and raising gentle local notifications on updates.
 */
class SharedCalendarRepository(
    private val appContext: Context,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(
        appContext.getString(R.string.firestore_database_id)
    ),
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun invitesCol() = db.collection("calendarInvites")

    fun observeAllVisibleInvites(userId: String?, userEmail: String?): Flow<List<CalendarInvite>> {
        val currentUser = auth.currentUser
        val uid = userId?.takeIf { it.isNotBlank() && currentUser?.uid == it }
            ?: return flowOf(emptyList())
        val normalizedEmail = (userEmail ?: currentUser?.email)?.trim()?.lowercase(Locale.US)
            ?.takeIf { it.contains("@") && currentUser?.isEmailVerified == true }

        val sentFlow = invitesCol()
            .whereEqualTo("ownerUid", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    CalendarInvite::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                logIfFirestore(error, OperationType.LIST, "calendarInvites(owner=$uid)")
                emit(emptyList())
            }

        val receivedFlow = if (normalizedEmail != null) {
            invitesCol()
                .whereEqualTo("inviteeEmail", normalizedEmail)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        CalendarInvite::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logIfFirestore(error, OperationType.LIST, "calendarInvites(invitee=$normalizedEmail)")
                    emit(emptyList())
                }
        } else {
            flowOf(emptyList())
        }

        return combine(sentFlow, receivedFlow) { sent, received ->
            val merged = LinkedHashMap<String, CalendarInvite>()
            sent.forEach { merged[it.id] = it }
            received.forEach { merged[it.id] = it }
            merged.values.sortedWith(
                compareBy<CalendarInvite> { it.plannedDate }
                    .thenByDescending { it.lastReplyAtMillis ?: it.updatedAt?.toDate()?.time ?: 0L }
            )
        }
    }

    fun observeReplies(inviteId: String): Flow<List<CalendarScheduleReply>> = flow {
        if (inviteId.isBlank() || auth.currentUser == null) {
            emit(emptyList())
            return@flow
        }
        val path = "calendarInvites/$inviteId/replies"
        emitAll(
            invitesCol()
                .document(inviteId)
                .collection("replies")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        CalendarScheduleReply::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logIfFirestore(error, OperationType.LIST, path)
                    emit(emptyList())
                }
        )
    }

    suspend fun sendCalendarInvite(
        inviteeEmailRaw: String,
        title: String,
        plannedDate: String,
        endDate: String? = null,
        scheduleTimeLabel: String? = null,
        notes: String? = null,
        itemId: String? = null,
        itemType: ItemType = ItemType.EVENT,
        category: TaskCategory = TaskCategory.PERSONAL
    ): Result<CalendarInvite> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Please sign in with Google to share calendar schedules."))
        val uid = currentUser.uid
        val ownerEmail = currentUser.email?.trim()?.lowercase(Locale.US)
            ?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("Google email is required to send invites."))
        val cleanInviteeEmail = inviteeEmailRaw.trim().lowercase(Locale.US)
        if (!cleanInviteeEmail.contains("@") || !cleanInviteeEmail.contains(".") || cleanInviteeEmail.length > 160) {
            return Result.failure(IllegalArgumentException("Enter a valid friend email address."))
        }
        val cleanTitle = title.trim().take(200)
        if (cleanTitle.isBlank()) {
            return Result.failure(IllegalArgumentException("Schedule title is required."))
        }
        val cleanPlannedDate = CalendarDate.parse(plannedDate)?.toIsoString()
            ?: return Result.failure(IllegalArgumentException("Valid planned date is required."))
        val cleanEndDate = endDate?.let { CalendarDate.parse(it)?.toIsoString() }
        val ownerName = currentUser.displayName?.trim()?.take(80)?.ifBlank { null }
            ?: ownerEmail.substringBefore("@").take(80)
        val ownerPhotoUrl = currentUser.photoUrl?.toString()?.take(500)
        val cleanNotes = notes?.trim()?.take(800)?.takeIf { it.isNotEmpty() }
        val cleanTimeLabel = scheduleTimeLabel?.trim()?.take(80)?.takeIf { it.isNotEmpty() }
        val cleanItemId = itemId?.trim()?.take(120)?.takeIf { it.isNotEmpty() }

        val docRef = invitesCol().document()
        val path = docRef.path
        return try {
            val payload = buildMap<String, Any> {
                put("ownerUid", uid)
                put("ownerName", ownerName)
                put("ownerEmail", ownerEmail)
                if (ownerPhotoUrl != null) put("ownerPhotoUrl", ownerPhotoUrl)
                put("inviteeEmail", cleanInviteeEmail)
                put("plannedDate", cleanPlannedDate)
                if (cleanEndDate != null) put("endDate", cleanEndDate)
                if (cleanTimeLabel != null) put("scheduleTimeLabel", cleanTimeLabel)
                if (cleanItemId != null) put("itemId", cleanItemId)
                put("title", cleanTitle)
                if (cleanNotes != null) put("notes", cleanNotes)
                put("itemType", itemType.name)
                put("category", category.name)
                put("rsvpStatus", InviteRsvpStatus.PENDING.name)
                put("replyCount", 0)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.set(payload).await()
            val created = CalendarInvite(
                id = docRef.id,
                ownerUid = uid,
                ownerName = ownerName,
                ownerEmail = ownerEmail,
                ownerPhotoUrl = ownerPhotoUrl,
                inviteeEmail = cleanInviteeEmail,
                plannedDate = cleanPlannedDate,
                endDate = cleanEndDate,
                scheduleTimeLabel = cleanTimeLabel,
                itemId = cleanItemId,
                title = cleanTitle,
                notes = cleanNotes,
                itemType = itemType.name,
                category = category.name,
                rsvpStatus = InviteRsvpStatus.PENDING.name,
                replyCount = 0
            )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun replyToScheduleInvite(
        invite: CalendarInvite,
        rsvpStatus: InviteRsvpStatus,
        messageRaw: String
    ): Result<Unit> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Please sign in with Google to reply."))
        val uid = currentUser.uid
        val authorEmail = currentUser.email?.trim()?.lowercase(Locale.US) ?: ""
        val authorName = currentUser.displayName?.trim()?.take(80)?.ifBlank { null }
            ?: authorEmail.substringBefore("@").ifBlank { "Friend" }
        val authorPhotoUrl = currentUser.photoUrl?.toString()?.take(500)
        val cleanMessage = messageRaw.trim().take(500).ifBlank {
            when (rsvpStatus) {
                InviteRsvpStatus.GOING -> "Count me in for this date!"
                InviteRsvpStatus.MAYBE -> "I might be able to join — I will confirm soon."
                InviteRsvpStatus.DECLINED -> "Sorry, I can't make this planned date."
                InviteRsvpStatus.PENDING -> "Updated schedule note"
            }
        }
        val now = System.currentTimeMillis()
        val inviteRef = invitesCol().document(invite.id)
        val replyRef = inviteRef.collection("replies").document()
        val isOwner = invite.ownerUid == uid
        val nextCount = (invite.replyCount + 1).coerceIn(1, 500)

        return try {
            val replyPayload = buildMap<String, Any> {
                put("inviteId", invite.id)
                put("ownerUid", invite.ownerUid)
                put("inviteeEmail", invite.inviteeEmail)
                put("authorUid", uid)
                put("authorName", authorName)
                put("authorEmail", authorEmail.ifBlank { invite.inviteeEmail })
                if (authorPhotoUrl != null) put("authorPhotoUrl", authorPhotoUrl)
                put("plannedDate", invite.plannedDate)
                put("rsvpStatus", rsvpStatus.name)
                put("message", cleanMessage)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }

            val inviteUpdate = buildMap<String, Any> {
                if (!isOwner) {
                    put("inviteeUid", uid)
                    put("rsvpStatus", rsvpStatus.name)
                }
                put("replyCount", nextCount)
                put("lastReplyText", cleanMessage)
                put("lastReplyAuthorName", authorName)
                put("lastReplyAtMillis", now)
                put("updatedAt", FieldValue.serverTimestamp())
            }

            val batch = db.batch()
            batch.set(replyRef, replyPayload)
            batch.update(inviteRef, inviteUpdate)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, replyRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteInvite(inviteId: String): Result<Unit> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Sign in required."))
        val inviteRef = invitesCol().document(inviteId)
        return try {
            // Delete child replies first so nothing orphaned stays in subcollection
            val repliesSnap = inviteRef.collection("replies").get().await()
            val batch = db.batch()
            repliesSnap.documents.forEach { batch.delete(it.reference) }
            batch.delete(inviteRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "calendarInvites/$inviteId (uid=${currentUser.uid})")
            Result.failure(e)
        }
    }

    /**
     * Checks incoming invites & new replies from friends and posts a gentle local notification
     * once per unseen state change.
     */
    fun notifyUnseenUpdates(invites: List<CalendarInvite>, currentUid: String?) {
        if (currentUid.isNullOrBlank()) return
        val seenSnapshot = prefs.getStringSet(KEY_SEEN_UPDATES, emptySet())?.toMutableSet() ?: mutableSetOf()
        var changed = false

        invites.forEach { invite ->
            val versionStamp = "${invite.id}:${invite.rsvpStatus}:${invite.replyCount}:${invite.lastReplyAtMillis ?: 0L}"
            if (versionStamp in seenSnapshot) return@forEach

            // Mark seen first so rapid stream emissions never double-notify
            seenSnapshot.add(versionStamp)
            changed = true

            val isIncomingInvite = invite.ownerUid != currentUid && invite.replyCount == 0
            val currentName = auth.currentUser?.displayName?.trim()
            val hasFriendReply = !invite.lastReplyAuthorName.isNullOrBlank() &&
                invite.lastReplyAuthorName != currentName &&
                invite.lastReplyAtMillis != null

            when {
                isIncomingInvite -> {
                    val dateLabel = CalendarDate.parse(invite.plannedDate)?.let {
                        PlannerDateUtils.formatCompactDate(it)
                    } ?: invite.plannedDate
                    postLocalCalendarAlert(
                        notificationId = ("inv_" + invite.id).hashCode(),
                        title = "${invite.ownerName} invited you: ${invite.title}",
                        body = "Planned for $dateLabel${invite.scheduleTimeLabel?.let { " · $it" } ?: ""}. Open Bunny Memo to reply!"
                    )
                }
                hasFriendReply -> {
                    val dateLabel = CalendarDate.parse(invite.plannedDate)?.let {
                        PlannerDateUtils.formatCompactDate(it)
                    } ?: invite.plannedDate
                    postLocalCalendarAlert(
                        notificationId = ("rep_" + invite.id + "_" + invite.replyCount).hashCode(),
                        title = "${invite.lastReplyAuthorName} replied on ${invite.title}",
                        body = "[$dateLabel · ${invite.rsvpStatusEnum().label}] ${invite.lastReplyText.orEmpty()}"
                    )
                }
            }
        }

        if (changed) {
            val trimmed = seenSnapshot.toList().takeLast(200).toSet()
            prefs.edit().putStringSet(KEY_SEEN_UPDATES, trimmed).apply()
        }
    }

    private fun postLocalCalendarAlert(
        notificationId: Int,
        title: String,
        body: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        ReminderScheduler(appContext).ensureNotificationChannel()

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(ReminderScheduler.FORGET_ME_NOT_ACCENT_COLOR)
            .setSubText("Shared Schedule")
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        manager.notify(notificationId, notification)
    }

    fun launchEmailInvitation(context: Context, invite: CalendarInvite): Boolean {
        val dateLabel = CalendarDate.parse(invite.plannedDate)?.let {
            PlannerDateUtils.formatFullDateHeader(it)
        } ?: invite.plannedDate
        val timeLine = invite.scheduleTimeLabel?.let { "\nTime: $it" } ?: ""
        val notesLine = invite.notes?.let { "\nNotes: $it" } ?: ""
        val subject = "Calendar Invite from ${invite.ownerName}: ${invite.title} ($dateLabel)"
        val body = buildString {
            appendLine("Hi there!")
            appendLine()
            appendLine("${invite.ownerName} invited you to a shared schedule in Bunny Memo:")
            appendLine()
            appendLine("Event / Task: ${invite.title}")
            appendLine("Date: $dateLabel$timeLine$notesLine")
            appendLine()
            appendLine("Open Bunny Memo and sign in with ${invite.inviteeEmail} to see it on your calendar, view my profile badge, and reply on this planned date!")
        }

        val mailtoUri = Uri.parse("mailto:${Uri.encode(invite.inviteeEmail)}").buildUpon()
            .appendQueryParameter("subject", subject)
            .appendQueryParameter("body", body)
            .build()
        val intent = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(Intent.createChooser(intent, "Send calendar invite via email"))
            true
        }.getOrElse {
            // Fallback generic SEND intent when emulator has no mailto handler
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(invite.inviteeEmail))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                context.startActivity(Intent.createChooser(shareIntent, "Share calendar invite"))
                true
            }.getOrDefault(false)
        }
    }

    private fun logIfFirestore(error: Throwable, op: OperationType, path: String) {
        val fsErr = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsErr == null && error is CancellationException) return
        val ex = fsErr ?: (error as? Exception) ?: return
        Log.w(TAG, "SharedCalendar stream warning [$path]: ${ex.message}")
        handleFirestoreError(ex, op, path)
    }

    companion object {
        private const val TAG = "SharedCalendarRepo"
        private const val PREFS_NAME = "bunny_shared_calendar_seen_v1"
        private const val KEY_SEEN_UPDATES = "seen_invite_versions"
    }
}
