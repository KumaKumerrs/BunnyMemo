package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

/**
 * Logs structured Firestore diagnostics without leaking user PII (email, UID, provider data)
 * in release builds, and guards against uninitialized FirebaseApp in local-only mode.
 */
fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val errorCode = (rootError as? FirebaseFirestoreException)?.code?.name ?: rootError.javaClass.simpleName
    val currentUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()

    val errorInfoJson = JSONObject().apply {
        put("code", errorCode)
        put("operationType", operationType.value)
        put("isSignedIn", currentUser != null)
        if (BuildConfig.DEBUG) {
            put("error", rootError.message ?: rootError.toString())
            put("path", path)
            put("emailVerified", currentUser?.isEmailVerified)
        }
    }

    val jsonString = errorInfoJson.toString()
    if (BuildConfig.DEBUG) {
        Log.e("FirestoreError", "Firestore Error: $jsonString")
    } else {
        Log.w("FirestoreError", "Firestore ${operationType.value} failed ($errorCode)")
    }
    return jsonString
}
