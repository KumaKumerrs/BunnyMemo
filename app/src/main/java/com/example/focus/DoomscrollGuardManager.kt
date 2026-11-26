package com.example.focus

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WatchedAppCandidate(
    val packageName: String,
    val displayName: String,
    val category: String,
    val isInstalled: Boolean = false,
    val isCustom: Boolean = false
)

data class DoomscrollGuardConfig(
    val isEnabled: Boolean = false,
    val watchedPackages: Set<String> = DEFAULT_WATCHED_PACKAGES,
    val customPackages: Set<String> = emptySet(),
    val maxScrollSeconds: Int = 180, // 3 minutes default until full-screen bunny takeover
    val snoozeUntilUtcMillis: Long = 0L,
    val takeoversCount: Int = 0
) {
    fun isSnoozed(nowMillis: Long = System.currentTimeMillis()): Boolean =
        snoozeUntilUtcMillis > nowMillis

    companion object {
        val DEFAULT_WATCHED_PACKAGES: Set<String> = setOf(
            "com.facebook.katana",
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.google.android.youtube"
        )
    }
}

data class DoomscrollLiveStatus(
    val isServiceRunning: Boolean = false,
    val activeWatchedPackage: String? = null,
    val activeAppName: String? = null,
    val elapsedSeconds: Int = 0,
    val maxScrollSeconds: Int = 180,
    val progressFraction: Float = 0f,
    val isFullTakeover: Boolean = false
)

/**
 * Manages persisted configuration, Play-compliant app discovery (using manifest `<queries>`),
 * Usage Access & Overlay permission checks, and service lifecycle for the Anti-Doomscroll Bunny Guard.
 */
object DoomscrollGuardManager {
    private const val PREFS_NAME = "bunny_memo_doomscroll_guard_v1"
    private const val KEY_ENABLED = "guard_enabled"
    private const val KEY_WATCHED = "watched_packages"
    private const val KEY_CUSTOM = "custom_packages"
    private const val KEY_MAX_SECONDS = "max_scroll_seconds"
    private const val KEY_SNOOZE_UNTIL = "snooze_until_utc"
    private const val KEY_TAKEOVERS = "takeovers_count"

    private val curatedSocialApps = listOf(
        WatchedAppCandidate("com.facebook.katana", "Facebook", "Social"),
        WatchedAppCandidate("com.facebook.lite", "Facebook Lite", "Social"),
        WatchedAppCandidate("com.instagram.android", "Instagram", "Social"),
        WatchedAppCandidate("com.zhiliaoapp.musically", "TikTok", "Short Video"),
        WatchedAppCandidate("com.ss.android.ugc.trill", "TikTok (Asia)", "Short Video"),
        WatchedAppCandidate("com.google.android.youtube", "YouTube", "Video"),
        WatchedAppCandidate("com.twitter.android", "X (Twitter)", "Social"),
        WatchedAppCandidate("com.reddit.frontpage", "Reddit", "Social"),
        WatchedAppCandidate("com.snapchat.android", "Snapchat", "Social"),
        WatchedAppCandidate("com.instagram.barcelona", "Threads", "Social"),
        WatchedAppCandidate("com.pinterest", "Pinterest", "Social"),
        WatchedAppCandidate("tv.twitch.android.app", "Twitch", "Video"),
        WatchedAppCandidate("com.discord", "Discord", "Chat"),
        WatchedAppCandidate("com.facebook.orca", "Messenger", "Chat"),
        WatchedAppCandidate("com.whatsapp", "WhatsApp", "Chat"),
        WatchedAppCandidate("org.telegram.messenger", "Telegram", "Chat"),
        WatchedAppCandidate("com.android.chrome", "Chrome Browser", "Web")
    )

    private val _config = MutableStateFlow(DoomscrollGuardConfig())
    val config: StateFlow<DoomscrollGuardConfig> = _config.asStateFlow()

    private val _liveStatus = MutableStateFlow(DoomscrollLiveStatus())
    val liveStatus: StateFlow<DoomscrollLiveStatus> = _liveStatus.asStateFlow()

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedWatched = prefs.getStringSet(KEY_WATCHED, null)
            val savedCustom = prefs.getStringSet(KEY_CUSTOM, null) ?: emptySet()
            val enabled = prefs.getBoolean(KEY_ENABLED, false)
            val maxSeconds = prefs.getInt(KEY_MAX_SECONDS, 180).coerceIn(20, 1800)
            val snoozeUntil = prefs.getLong(KEY_SNOOZE_UNTIL, 0L)
            val takeovers = prefs.getInt(KEY_TAKEOVERS, 0)

            _config.value = DoomscrollGuardConfig(
                isEnabled = enabled,
                watchedPackages = savedWatched?.toSet() ?: DoomscrollGuardConfig.DEFAULT_WATCHED_PACKAGES,
                customPackages = savedCustom.toSet(),
                maxScrollSeconds = maxSeconds,
                snoozeUntilUtcMillis = snoozeUntil,
                takeoversCount = takeovers
            )
            initialized = true
        }
    }

    private fun saveConfig(context: Context, updated: DoomscrollGuardConfig) {
        _config.value = updated
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, updated.isEnabled)
            .putStringSet(KEY_WATCHED, updated.watchedPackages.toSet())
            .putStringSet(KEY_CUSTOM, updated.customPackages.toSet())
            .putInt(KEY_MAX_SECONDS, updated.maxScrollSeconds)
            .putLong(KEY_SNOOZE_UNTIL, updated.snoozeUntilUtcMillis)
            .putInt(KEY_TAKEOVERS, updated.takeoversCount)
            .apply()
    }

    fun setGuardEnabled(context: Context, enabled: Boolean) {
        initialize(context)
        val updated = _config.value.copy(
            isEnabled = enabled,
            snoozeUntilUtcMillis = if (enabled) 0L else _config.value.snoozeUntilUtcMillis
        )
        saveConfig(context, updated)
        syncServiceState(context)
    }

    fun toggleWatchedPackage(context: Context, packageName: String) {
        initialize(context)
        val clean = packageName.trim()
        if (clean.isEmpty()) return
        val current = _config.value.watchedPackages
        val updatedSet = if (clean in current) current - clean else current + clean
        saveConfig(context, _config.value.copy(watchedPackages = updatedSet))
    }

    fun addCustomPackage(context: Context, rawPackageName: String): Boolean {
        initialize(context)
        val clean = rawPackageName.trim().lowercase()
        if (clean.length < 3 || !clean.contains('.') || clean.any { it.isWhitespace() }) {
            return false
        }
        val updated = _config.value.copy(
            customPackages = _config.value.customPackages + clean,
            watchedPackages = _config.value.watchedPackages + clean
        )
        saveConfig(context, updated)
        return true
    }

    fun removeCustomPackage(context: Context, packageName: String) {
        initialize(context)
        val updated = _config.value.copy(
            customPackages = _config.value.customPackages - packageName,
            watchedPackages = _config.value.watchedPackages - packageName
        )
        saveConfig(context, updated)
    }

    fun setMaxScrollSeconds(context: Context, seconds: Int) {
        initialize(context)
        val safe = seconds.coerceIn(20, 1800)
        saveConfig(context, _config.value.copy(maxScrollSeconds = safe))
    }

    fun snoozeGuard(context: Context, durationMinutes: Int = 5) {
        initialize(context)
        val until = System.currentTimeMillis() + durationMinutes.coerceIn(1, 60) * 60_000L
        saveConfig(context, _config.value.copy(snoozeUntilUtcMillis = until))
    }

    fun clearSnooze(context: Context) {
        initialize(context)
        saveConfig(context, _config.value.copy(snoozeUntilUtcMillis = 0L))
    }

    fun incrementTakeoversCount(context: Context) {
        initialize(context)
        val updated = _config.value.copy(takeoversCount = _config.value.takeoversCount + 1)
        saveConfig(context, updated)
    }

    internal fun updateLiveStatus(status: DoomscrollLiveStatus) {
        _liveStatus.value = status
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return try {
            Settings.canDrawOverlays(context.applicationContext)
        } catch (_: Exception) {
            false
        }
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun hasAllRequiredPermissions(context: Context): Boolean =
        hasOverlayPermission(context) && hasUsageStatsPermission(context)

    fun openOverlayPermissionSettings(context: Context) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun openUsageAccessSettings(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun getAvailableCandidates(context: Context): List<WatchedAppCandidate> {
        initialize(context)
        val pm = context.packageManager
        val curated = curatedSocialApps.map { app ->
            app.copy(isInstalled = isPackageInstalled(pm, app.packageName))
        }
        val knownPkgs = curated.map { it.packageName }.toSet()
        val customs = _config.value.customPackages
            .filter { it !in knownPkgs }
            .sorted()
            .map { pkg ->
                val label = resolveLabelOrPackage(pm, pkg)
                WatchedAppCandidate(
                    packageName = pkg,
                    displayName = label,
                    category = "Custom",
                    isInstalled = isPackageInstalled(pm, pkg),
                    isCustom = true
                )
            }
        return (curated + customs).sortedWith(
            compareByDescending<WatchedAppCandidate> { it.isInstalled }
                .thenBy { it.isCustom }
                .thenBy { it.displayName }
        )
    }

    fun resolveAppDisplayName(context: Context, packageName: String): String {
        curatedSocialApps.firstOrNull { it.packageName == packageName }?.let { return it.displayName }
        return resolveLabelOrPackage(context.packageManager, packageName)
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveLabelOrPackage(pm: PackageManager, packageName: String): String {
        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString().ifBlank { packageName }
        } catch (_: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    fun syncServiceState(context: Context) {
        initialize(context)
        val appContext = context.applicationContext
        val cfg = _config.value
        val focusBlocking = FocusSessionManager.isBlockingAppsNow()
        val shouldRun = (cfg.isEnabled || focusBlocking) &&
            hasAllRequiredPermissions(appContext) &&
            cfg.watchedPackages.isNotEmpty()
        val intent = Intent(appContext, DoomscrollGuardService::class.java)
        if (shouldRun) {
            runCatching {
                ContextCompat.startForegroundService(appContext, intent)
            }
        } else {
            runCatching {
                appContext.stopService(intent)
            }
            updateLiveStatus(DoomscrollLiveStatus(isServiceRunning = false, maxScrollSeconds = cfg.maxScrollSeconds))
        }
    }

    fun formatDurationShort(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val mins = safe / 60
        val secs = safe % 60
        return when {
            mins == 0 -> "${secs}s"
            secs == 0 -> "${mins}m"
            else -> "${mins}m ${secs}s"
        }
    }
}
