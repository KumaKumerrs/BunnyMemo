package com.example.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Foreground specialUse service that monitors foreground usage of user-selected distracting apps
 * (e.g., Facebook, Instagram, TikTok, YouTube) and progressively grows [BunnyOverlayView] over
 * the screen until the user steps back to productivity.
 */
class DoomscrollGuardService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    private var windowManager: WindowManager? = null
    private var overlayView: BunnyOverlayView? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var isOverlayAttached: Boolean = false
    private var lastOverlayWasTakeover: Boolean = false
    private var lastAppliedWidth: Int = Int.MIN_VALUE
    private var lastAppliedHeight: Int = Int.MIN_VALUE
    private var lastAppliedFlags: Int = Int.MIN_VALUE
    private var lastAppliedGravity: Int = Int.MIN_VALUE
    private var lastAppliedY: Int = Int.MIN_VALUE

    private var activeWatchedPkg: String? = null
    private var sessionStartElapsedMs: Long = 0L
    private var elapsedSeconds: Int = 0
    private var takeoverCountedForSession: Boolean = false
    private var lastKnownForegroundPkg: String? = null

    private var isScreenInteractive: Boolean = true
    private var screenReceiverRegistered: Boolean = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenInteractive = false
                    monitorJob?.cancel()
                    monitorJob = null
                    resetSession()
                    removeOverlayOnMain()
                    DoomscrollGuardManager.updateLiveStatus(
                        DoomscrollLiveStatus(
                            isServiceRunning = true,
                            maxScrollSeconds = DoomscrollGuardManager.config.value.maxScrollSeconds
                        )
                    )
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    isScreenInteractive = true
                    startMonitoringLoop()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        DoomscrollGuardManager.initialize(applicationContext)
        FocusSessionManager.initialize(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        isScreenInteractive = pm?.isInteractive ?: true
        ensureNotificationChannel()
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isStopAction = intent?.action == ACTION_STOP_GUARD
        val notifText = if (FocusSessionManager.isBlockingAppsNow()) {
            getString(R.string.focus_guard_notif_pomodoro)
        } else {
            getString(R.string.focus_guard_notif_watching)
        }
        val notification = buildNotification(notifText)

        // Always satisfy Android 14+ foreground service contract before stopping
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }

        if (isStopAction) {
            DoomscrollGuardManager.setGuardEnabled(applicationContext, false)
            if (FocusSessionManager.isBlockingAppsNow()) {
                FocusSessionManager.pause(applicationContext)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        if (isScreenInteractive) {
            startMonitoringLoop()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterScreenReceiver()
        monitorJob?.cancel()
        serviceScope.cancel()
        removeOverlayOnMain()
        DoomscrollGuardManager.updateLiveStatus(
            DoomscrollLiveStatus(
                isServiceRunning = false,
                maxScrollSeconds = DoomscrollGuardManager.config.value.maxScrollSeconds
            )
        )
        super.onDestroy()
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenStateReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenStateReceiver, filter)
            }
            screenReceiverRegistered = true
        }
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        runCatching { unregisterReceiver(screenStateReceiver) }
        screenReceiverRegistered = false
    }

    private fun startMonitoringLoop() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            while (isActive) {
                if (!isScreenInteractive) return@launch

                val cfg = DoomscrollGuardManager.config.value
                val focusState = FocusSessionManager.state.value
                val focusBlocking = focusState.isBlockingDistractionsNow
                if ((!cfg.isEnabled && !focusBlocking) ||
                    cfg.watchedPackages.isEmpty() ||
                    !DoomscrollGuardManager.hasAllRequiredPermissions(applicationContext)
                ) {
                    withContext(Dispatchers.Main) { removeOverlayNow() }
                    stopSelf()
                    return@launch
                }

                val now = System.currentTimeMillis()
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isInteractive = powerManager?.isInteractive ?: true

                if (!isInteractive || (!focusBlocking && cfg.isSnoozed(now))) {
                    if (activeWatchedPkg != null) {
                        resetSession()
                        withContext(Dispatchers.Main) { removeOverlayNow() }
                    }
                    DoomscrollGuardManager.updateLiveStatus(
                        DoomscrollLiveStatus(
                            isServiceRunning = true,
                            maxScrollSeconds = cfg.maxScrollSeconds
                        )
                    )
                    delay(2000L)
                    continue
                }

                val fgPkg = queryForegroundPackage(now)
                if (fgPkg != null && fgPkg in cfg.watchedPackages) {
                    val appName = DoomscrollGuardManager.resolveAppDisplayName(applicationContext, fgPkg)
                    if (focusBlocking) {
                        activeWatchedPkg = fgPkg
                        DoomscrollGuardManager.updateLiveStatus(
                            DoomscrollLiveStatus(
                                isServiceRunning = true,
                                activeWatchedPackage = fgPkg,
                                activeAppName = appName,
                                elapsedSeconds = focusState.totalSeconds - focusState.remainingSeconds,
                                maxScrollSeconds = focusState.totalSeconds,
                                progressFraction = 1f,
                                isFullTakeover = true
                            )
                        )
                        withContext(Dispatchers.Main) {
                            showOrUpdateOverlay(
                                progress = 1f,
                                appName = appName,
                                elapsedSec = focusState.totalSeconds - focusState.remainingSeconds,
                                maxSec = focusState.totalSeconds,
                                focusCountdown = focusState.formattedCountdown(),
                                focusTaskTitle = focusState.linkedTaskTitle
                            )
                        }
                    } else {
                        val nowElapsed = SystemClock.elapsedRealtime()
                        if (fgPkg != activeWatchedPkg) {
                            activeWatchedPkg = fgPkg
                            sessionStartElapsedMs = nowElapsed
                            elapsedSeconds = 1
                            takeoverCountedForSession = false
                        } else {
                            elapsedSeconds = (((nowElapsed - sessionStartElapsedMs) / 1000L).toInt() + 1)
                                .coerceAtLeast(1)
                        }

                        val maxSec = cfg.maxScrollSeconds.coerceAtLeast(20)
                        val progress = (elapsedSeconds.toFloat() / maxSec.toFloat()).coerceIn(0f, 1f)
                        val isTakeover = progress >= 0.995f

                        if (isTakeover && !takeoverCountedForSession) {
                            takeoverCountedForSession = true
                            DoomscrollGuardManager.incrementTakeoversCount(applicationContext)
                        }

                        DoomscrollGuardManager.updateLiveStatus(
                            DoomscrollLiveStatus(
                                isServiceRunning = true,
                                activeWatchedPackage = fgPkg,
                                activeAppName = appName,
                                elapsedSeconds = elapsedSeconds,
                                maxScrollSeconds = maxSec,
                                progressFraction = progress,
                                isFullTakeover = isTakeover
                            )
                        )

                        withContext(Dispatchers.Main) {
                            showOrUpdateOverlay(progress, appName, elapsedSeconds, maxSec)
                        }
                    }
                } else {
                    if (activeWatchedPkg != null) {
                        resetSession()
                        withContext(Dispatchers.Main) { removeOverlayNow() }
                    }
                    DoomscrollGuardManager.updateLiveStatus(
                        DoomscrollLiveStatus(
                            isServiceRunning = true,
                            maxScrollSeconds = cfg.maxScrollSeconds
                        )
                    )
                }

                delay(1000L)
            }
        }
    }

    private fun resetSession() {
        activeWatchedPkg = null
        sessionStartElapsedMs = 0L
        elapsedSeconds = 0
        takeoverCountedForSession = false
        lastKnownForegroundPkg = null
    }

    private fun queryForegroundPackage(nowMillis: Long): String? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
            val events = usm.queryEvents(nowMillis - 30_000L, nowMillis)
            val event = UsageEvents.Event()
            var latestResumedPkg: String? = null
            var latestPausedPkg: String? = null
            var latestResumedTs = 0L
            var latestPausedTs = 0L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName
                if (pkg.isNullOrBlank()) continue
                val isForegroundEvent =
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                            @Suppress("DEPRECATION")
                            event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND)
                val isBackgroundEvent =
                    event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                        event.eventType == UsageEvents.Event.ACTIVITY_STOPPED ||
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                            @Suppress("DEPRECATION")
                            event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND)

                when {
                    isForegroundEvent && event.timeStamp >= latestResumedTs -> {
                        latestResumedPkg = pkg
                        latestResumedTs = event.timeStamp
                    }
                    isBackgroundEvent && event.timeStamp >= latestPausedTs -> {
                        latestPausedPkg = pkg
                        latestPausedTs = event.timeStamp
                    }
                }
            }

            if (latestResumedPkg != null) {
                lastKnownForegroundPkg = latestResumedPkg
            } else if (latestPausedPkg != null &&
                latestPausedPkg == lastKnownForegroundPkg &&
                latestPausedTs > latestResumedTs
            ) {
                // Tracked app moved to background without another resumed event in window
                lastKnownForegroundPkg = null
            }
            lastKnownForegroundPkg
        } catch (e: Exception) {
            Log.w(TAG, "Unable to query foreground app: ${e.message}")
            null
        }
    }

    private fun showOrUpdateOverlay(
        progress: Float,
        appName: String,
        elapsedSec: Int,
        maxSec: Int,
        focusCountdown: String? = null,
        focusTaskTitle: String? = null
    ) {
        if (!Settings.canDrawOverlays(applicationContext)) return
        val wm = windowManager ?: return
        val dm = resources.displayMetrics
        val minScreenPx = min(dm.widthPixels, dm.heightPixels)
        val density = dm.density
        val isTakeover = focusCountdown != null || progress >= 0.995f

        val view = overlayView ?: BunnyOverlayView(
            context = applicationContext,
            onBackToProductivity = {
                // Launch MainActivity BEFORE removing the SYSTEM_ALERT_WINDOW so Android 14-16
                // BAL exemptions remain active while our visible overlay is still attached.
                val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                runCatching { startActivity(launchIntent) }
                resetSession()
                removeOverlayNow()
            },
            onPauseFiveMinutes = {
                if (FocusSessionManager.isBlockingAppsNow()) {
                    FocusSessionManager.pause(applicationContext)
                } else {
                    DoomscrollGuardManager.snoozeGuard(applicationContext, 5)
                }
                resetSession()
                removeOverlayNow()
            }
        ).also { overlayView = it }

        view.updateState(
            progress = progress,
            appName = appName,
            elapsedSeconds = elapsedSec,
            maxSeconds = maxSec,
            focusSessionCountdown = focusCountdown,
            focusTaskTitle = focusTaskTitle
        )

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val flags = if (isTakeover) {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        val minSizePx = (88f * density).roundToInt()
        val maxFloatingSizePx = (minScreenPx * 0.94f).roundToInt().coerceAtLeast(minSizePx)
        val currentSizePx = (minSizePx + (maxFloatingSizePx - minSizePx) * progress).roundToInt()

        val widthPx = if (isTakeover) WindowManager.LayoutParams.MATCH_PARENT else currentSizePx
        val heightPx = if (isTakeover) WindowManager.LayoutParams.MATCH_PARENT else currentSizePx
        val gravity = if (isTakeover) Gravity.CENTER else (Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        val yOffset = if (isTakeover) 0 else ((1f - progress) * 28f * density).roundToInt()

        val params = overlayLayoutParams ?: WindowManager.LayoutParams(
            widthPx,
            heightPx,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
        }.also { overlayLayoutParams = it }

        val layoutChanged = !isOverlayAttached ||
            lastOverlayWasTakeover != isTakeover ||
            lastAppliedWidth != widthPx ||
            lastAppliedHeight != heightPx ||
            lastAppliedFlags != flags ||
            lastAppliedGravity != gravity ||
            lastAppliedY != yOffset

        params.width = widthPx
        params.height = heightPx
        params.flags = flags
        params.gravity = gravity
        params.y = yOffset

        try {
            if (!isOverlayAttached) {
                wm.addView(view, params)
                isOverlayAttached = true
            } else if (layoutChanged) {
                wm.updateViewLayout(view, params)
            }
            lastOverlayWasTakeover = isTakeover
            lastAppliedWidth = widthPx
            lastAppliedHeight = heightPx
            lastAppliedFlags = flags
            lastAppliedGravity = gravity
            lastAppliedY = yOffset
        } catch (e: Exception) {
            Log.w(TAG, "Overlay update error: ${e.message}")
        }
    }

    private fun removeOverlayOnMain() {
        android.os.Handler(mainLooper).post { removeOverlayNow() }
    }

    private fun removeOverlayNow() {
        val wm = windowManager
        val view = overlayView
        if (isOverlayAttached && wm != null && view != null) {
            try {
                wm.removeViewImmediate(view)
            } catch (_: Exception) {
            }
        }
        isOverlayAttached = false
        lastOverlayWasTakeover = false
        lastAppliedWidth = Int.MIN_VALUE
        lastAppliedHeight = Int.MIN_VALUE
        lastAppliedFlags = Int.MIN_VALUE
        lastAppliedGravity = Int.MIN_VALUE
        lastAppliedY = Int.MIN_VALUE
        overlayView = null
        overlayLayoutParams = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.focus_guard_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.focus_guard_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            9101,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            9102,
            Intent(this, DoomscrollGuardService::class.java).apply {
                action = ACTION_STOP_GUARD
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_flower_mono)
            .setContentTitle(getString(R.string.focus_guard_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.focus_guard_stop_action), stopIntent)
            .build()
    }

    companion object {
        private const val TAG = "DoomscrollGuardSvc"
        private const val CHANNEL_ID = "bunny_focus_guard_channel"
        private const val NOTIFICATION_ID = 7742
        private const val ACTION_STOP_GUARD = "com.example.focus.ACTION_STOP_GUARD"
    }
}
