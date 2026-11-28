package com.example.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun resolveDefaultWebClientId(context: Context): String? {
    val direct = runCatching {
        context.getString(R.string.default_web_client_id).trim().takeIf { it.isNotBlank() }
    }.getOrNull()
    if (direct != null) return direct
    return runCatching {
        @Suppress("DiscouragedApi")
        val resId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (resId == 0) null else context.getString(resId).trim().takeIf { it.isNotBlank() }
    }.getOrNull()
}

/**
 * Silent Auto-Sign-In on startup using GetGoogleIdOption (one credential option per request).
 * Bounded by short timeouts so an offline device or emulator without Google Play Services never stalls launch.
 */
fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager?,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
    if (currentUser != null) {
        onAuthSuccess()
        return
    }
    if (credentialManager == null) {
        onUnauthenticated()
        return
    }
    val clientId = resolveDefaultWebClientId(context)
    if (clientId == null) {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
    scope.launch {
        try {
            val activity = context.findActivity() ?: run {
                onUnauthenticated()
                return@launch
            }
            val cred = withTimeout(2500L) {
                credentialManager.getCredential(activity, request).credential
            }
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                withTimeout(5000L) {
                    Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                }
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: TimeoutCancellationException) {
            Log.w("Auth", "Silent auto-sign-in timed out; continuing to sign-in screen.")
            onUnauthenticated()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

/**
 * Interactive Google Sign-In using GetSignInWithGoogleOption (one credential option per request).
 */
fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager?,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    if (credentialManager == null) {
        onAuthError("Google Sign-In is unavailable on this device. You can continue locally anytime.")
        return
    }
    val clientId = resolveDefaultWebClientId(context)
    if (clientId == null) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()
    scope.launch {
        try {
            val activity = context.findActivity()
            if (activity == null) {
                onAuthError("Unable to find active window for sign-in.")
                return@launch
            }
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                withTimeout(15_000L) {
                    Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                }
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (_: TimeoutCancellationException) {
            onAuthError("Sign-in timed out. Please check your connection and try again.")
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed. Please try again.")
        }
    }
}

/**
 * Clean sign-out clearing Credential Manager state inside NonCancellable before Firebase signOut.
 */
fun signOut(
    credentialManager: CredentialManager?,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager?.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                runCatching { Firebase.auth.signOut() }
                onSignOutComplete()
            }
        }
    }
}

/**
 * Permanently deletes the signed-in user's cloud data (all user subcollections, owned shared
 * calendar invites + replies, user profile doc), deletes the Firebase Auth account, and wipes
 * all local Room/media/preference data.
 */
suspend fun deleteAccountAndCloudData(
    context: Context,
    credentialManager: CredentialManager?
): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        val appCtx = context.applicationContext
        val auth = runCatching { Firebase.auth }.getOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Account service unavailable."))
        val user = auth.currentUser
            ?: return@withContext Result.failure(IllegalStateException("You must be signed in to delete your account."))
        val uid = user.uid
        val db = runCatching {
            FirebaseFirestore.getInstance(appCtx.getString(R.string.firestore_database_id))
        }.getOrNull()

        RoomFirestoreSyncService.getInstance(appCtx).stopCloudSync()

        if (db != null) {
            val refsToDelete = mutableListOf<DocumentReference>()
            val userDoc = db.collection("users").document(uid)

            val subCollections = listOf(
                "reminders",
                "steps",
                "items",
                "classSchedules",
                "habitCheckIns",
                "dailyHabits"
            )
            for (sub in subCollections) {
                val snap = userDoc.collection(sub).get().await()
                snap.documents.forEach { refsToDelete.add(it.reference) }
            }
            refsToDelete.add(userDoc)

            val ownedInvites = db.collection("calendarInvites")
                .whereEqualTo("ownerUid", uid)
                .get()
                .await()
            for (inviteDoc in ownedInvites.documents) {
                val replies = inviteDoc.reference.collection("replies").get().await()
                replies.documents.forEach { refsToDelete.add(it.reference) }
                refsToDelete.add(inviteDoc.reference)
            }

            // Delete in chunks <= 450 ops (Firestore WriteBatch hard limit is 500)
            for (chunk in refsToDelete.chunked(450)) {
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
        }

        user.delete().await()

        withContext(NonCancellable) {
            runCatching { credentialManager?.clearCredentialState(ClearCredentialStateRequest()) }
            runCatching { auth.signOut() }
            LocalPlannerRepository.wipeAllUserLocalData(appCtx)
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (_: FirebaseAuthRecentLoginRequiredException) {
        Result.failure(
            IllegalStateException("For security, please sign out and sign back in with Google before deleting your account.")
        )
    } catch (e: Exception) {
        Result.failure(e)
    }
}
