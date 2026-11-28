package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.audio.BunnyFeedbackManager
import com.example.audio.TaskAudioMemoManager
import com.example.data.AppUsageTracker
import com.example.data.CalendarDate
import com.example.data.CalendarInvite
import com.example.data.ClassScheduleItem
import com.example.data.ItemType
import com.example.data.LocalPlannerRepository
import com.example.data.PlannerDateUtils
import com.example.data.PlannerItem
import com.example.data.RoomFirestoreSyncService
import com.example.data.RoomSyncStatus
import com.example.data.SharedCalendarRepository
import com.example.data.SyncPhase
import com.example.data.TaskCategory
import com.example.data.deleteAccountAndCloudData
import com.example.data.findActivity
import com.example.data.onGoogleSignInClicked
import com.example.data.signOut
import com.example.focus.DoomscrollGuardManager
import com.example.focus.FocusSessionManager
import com.example.notifications.ReminderScheduler
import com.example.ui.components.BunnyMemoCloudSyncModal
import com.example.ui.components.BunnyMemoConfirmModal
import com.example.ui.components.ClassScheduleEditorModal
import com.example.ui.components.DoomscrollGuardModal
import com.example.ui.components.FocusSessionModal
import com.example.ui.components.ForgetMeNotFlower
import com.example.ui.components.ForgetMeNotStationeryBackground
import com.example.ui.components.FriendScheduleDetailModal
import com.example.ui.components.HoppingBunnyLoadingScreen
import com.example.ui.components.InviteFriendToCalendarDialog
import com.example.ui.components.ItemEditorDialog
import com.example.ui.components.MonthBunnyOverlay
import com.example.ui.components.PlannerSearchBar
import com.example.ui.components.PlannerSearchResultsView
import com.example.ui.components.QuickNoteModal
import com.example.ui.components.SettingsDialog
import com.example.ui.components.adaptiveDetailPaneWidth
import com.example.ui.components.adaptiveHorizontalPadding
import com.example.ui.components.rememberReducedMotion
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.ClassScheduleScreen
import com.example.ui.screens.DedicationScreen
import com.example.ui.screens.ItemDetailScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.CloudWhite
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.ForgetMeNotTheme
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.OverdueTerracotta
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SkyCanvasTop
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.ThemePreferencesManager
import com.example.viewmodel.PlannerUiState
import com.example.viewmodel.PlannerViewModel
import com.example.widgets.WidgetIntents
import com.example.widgets.WidgetUpdateManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape

private const val MODE_PREFS_NAME = "bunny_memo_mode_prefs"
private const val KEY_LOCAL_MODE_ENABLED = "local_mode_enabled"

data class LaunchDirective(
    val id: Long = System.nanoTime(),
    val itemId: String? = null,
    val tab: String? = null,
    val action: String? = null
)

class MainActivity : ComponentActivity() {
    private var latestDirective by mutableStateOf<LaunchDirective?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Call enableEdgeToEdge() before setContent() so window insets & decor are configured on frame 1
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        latestDirective = consumeLaunchDirective(intent)
        val themePrefs = ThemePreferencesManager.getInstance(applicationContext)
        setContent {
            val themeMode by themePrefs.themeMode.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            LaunchedEffect(isDark) {
                val barStyle = if (isDark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        AndroidColor.TRANSPARENT,
                        AndroidColor.TRANSPARENT
                    )
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }
            ForgetMeNotTheme(darkTheme = isDark) {
                AppContent(
                    launchDirective = latestDirective,
                    onDirectiveConsumed = { consumedId ->
                        if (latestDirective?.id == consumedId) {
                            latestDirective = null
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLaunchDirective(intent)?.let { latestDirective = it }
    }

    /**
     * Parses launch extras and immediately strips them from the Intent so configuration changes
     * or Activity recreation never replay old widget/notification actions.
     */
    private fun consumeLaunchDirective(intent: Intent?): LaunchDirective? {
        if (intent == null) return null
        val itemId = intent.getStringExtra(ReminderScheduler.EXTRA_ITEM_ID)
        val tab = intent.getStringExtra(WidgetIntents.EXTRA_OPEN_TAB)
        val action = intent.getStringExtra(WidgetIntents.EXTRA_OPEN_ACTION)
        intent.removeExtra(ReminderScheduler.EXTRA_ITEM_ID)
        intent.removeExtra(WidgetIntents.EXTRA_OPEN_TAB)
        intent.removeExtra(WidgetIntents.EXTRA_OPEN_ACTION)
        if (itemId.isNullOrBlank() && tab.isNullOrBlank() && action.isNullOrBlank()) {
            return null
        }
        return LaunchDirective(itemId = itemId, tab = tab, action = action)
    }
}

private enum class PlannerTab(val labelRes: Int) {
    TODAY(R.string.nav_today),
    CALENDAR(R.string.nav_calendar),
    CLASSES(R.string.nav_classes),
    NOTIFICATIONS(R.string.nav_notifications),
    REPORTS(R.string.nav_reports),
    PROFILE(R.string.nav_profile)
}

private val PRIMARY_NAV_TABS = listOf(
    PlannerTab.TODAY,
    PlannerTab.CALENDAR,
    PlannerTab.CLASSES,
    PlannerTab.REPORTS,
    PlannerTab.PROFILE
)

private enum class AppRootStage { AUTH, PLANNER }

private data class InviteFriendPrefill(
    val title: String,
    val dateIso: String,
    val timeLabel: String? = null,
    val notes: String? = null,
    val itemId: String? = null,
    val itemType: ItemType = ItemType.EVENT,
    val category: TaskCategory = TaskCategory.PERSONAL
)

@Composable
fun AppContent(
    launchDirective: LaunchDirective? = null,
    onDirectiveConsumed: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val usageTracker = remember(context) { AppUsageTracker.getInstance(context) }
    val syncService = remember(context) { RoomFirestoreSyncService.getInstance(context) }
    val syncStatus by syncService.syncStatus.collectAsStateWithLifecycle()
    val feedbackManager = remember(context) { BunnyFeedbackManager.getInstance(context) }
    val audioMemoManager = remember(context) { TaskAudioMemoManager.getInstance(context) }

    DisposableEffect(lifecycleOwner, usageTracker, feedbackManager, audioMemoManager) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            usageTracker.onForegroundResume()
            feedbackManager.onForegroundResume()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    usageTracker.onForegroundResume()
                    feedbackManager.onForegroundResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    usageTracker.onForegroundPause()
                    feedbackManager.onForegroundPause()
                    audioMemoManager.onAppBackgrounded()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            usageTracker.onForegroundPause()
            feedbackManager.onForegroundPause()
            audioMemoManager.onAppBackgrounded()
        }
    }

    val modePrefs = remember(context) {
        context.applicationContext.getSharedPreferences(MODE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    var currentUser by remember {
        mutableStateOf(runCatching { Firebase.auth.currentUser }.getOrNull())
    }
    var useLocalMode by remember {
        mutableStateOf(modePrefs.getBoolean(KEY_LOCAL_MODE_ENABLED, false))
    }
    var migrationBanner by remember { mutableStateOf<String?>(null) }
    val reducedMotion = rememberReducedMotion()

    DisposableEffect(Unit) {
        val firebaseAuth = runCatching { Firebase.auth }.getOrNull()
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        firebaseAuth?.addAuthStateListener(listener)
        onDispose { firebaseAuth?.removeAuthStateListener(listener) }
    }

    // Activate automatic Room-to-Firestore synchronization when signed in with Google
    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid
        if (uid.isNullOrBlank()) {
            syncService.stopCloudSync()
        } else {
            syncService.startCloudSyncForUser(uid)
        }
    }

    // Surface connection-restored sync confirmations in the snackbar
    LaunchedEffect(syncService) {
        syncService.syncEvents.collect { msg ->
            migrationBanner = msg
        }
    }

    val stage = when {
        currentUser == null && !useLocalMode -> AppRootStage.AUTH
        else -> AppRootStage.PLANNER
    }

    AnimatedContent(
        targetState = stage,
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(tween(180)) togetherWith fadeOut(tween(140))
            } else {
                (fadeIn(tween(340, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = 0.985f, animationSpec = tween(340, easing = FastOutSlowInEasing))) togetherWith
                    fadeOut(tween(220, easing = FastOutSlowInEasing))
            }
        },
        label = "app_root_transition"
    ) { targetStage ->
        when (targetStage) {
            AppRootStage.AUTH -> AuthScreen(
                onAuthSuccess = {
                    currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
                },
                onContinueLocally = {
                    modePrefs.edit().putBoolean(KEY_LOCAL_MODE_ENABLED, true).apply()
                    useLocalMode = true
                }
            )

            AppRootStage.PLANNER -> {
                val user = currentUser
                val isLocalMode = user == null
                val sessionKey = if (isLocalMode) LocalPlannerRepository.LOCAL_USER_ID else user.uid

                key(sessionKey) {
                    PlannerWorkspaceShell(
                        isLocalMode = isLocalMode,
                        currentUserId = sessionKey,
                        userDisplayName = user?.displayName,
                        userEmail = user?.email,
                        userPhotoUrl = user?.photoUrl?.toString(),
                        syncStatus = syncStatus,
                        onRequestManualSync = { syncService.requestSync(restoredFromOffline = false) },
                        launchDirective = launchDirective,
                        onDirectiveConsumed = onDirectiveConsumed,
                        externalBannerMessage = migrationBanner,
                        onClearExternalBanner = { migrationBanner = null },
                        onSignedInFromLocal = {
                            currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
                        },
                        onSignedOutAndWiped = { bannerText ->
                            syncService.stopCloudSync()
                            modePrefs.edit().putBoolean(KEY_LOCAL_MODE_ENABLED, false).apply()
                            useLocalMode = false
                            currentUser = null
                            if (!bannerText.isNullOrBlank()) {
                                migrationBanner = bannerText
                            }
                        },
                        onFallbackToLocalMode = {
                            syncService.stopCloudSync()
                            modePrefs.edit().putBoolean(KEY_LOCAL_MODE_ENABLED, true).apply()
                            useLocalMode = true
                        }
                    )
                }
            }
        }
    }
}

private enum class PlannerShellStage { LOADING, ERROR, DEDICATION, WORKSPACE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlannerWorkspaceShell(
    isLocalMode: Boolean,
    currentUserId: String,
    userDisplayName: String?,
    userEmail: String?,
    userPhotoUrl: String?,
    syncStatus: RoomSyncStatus,
    onRequestManualSync: () -> Unit,
    launchDirective: LaunchDirective?,
    onDirectiveConsumed: (Long) -> Unit,
    externalBannerMessage: String?,
    onClearExternalBanner: () -> Unit,
    onSignedInFromLocal: () -> Unit,
    onSignedOutAndWiped: (String?) -> Unit,
    onFallbackToLocalMode: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    val credentialManager = remember(context) {
        runCatching { CredentialManager.create(context) }.getOrNull()
    }
    val feedbackManager = remember(context) { BunnyFeedbackManager.getInstance(context) }
    val syncService = remember(context) { RoomFirestoreSyncService.getInstance(context) }
    val sharedCalendarRepo = remember(context) { SharedCalendarRepository(context) }

    val viewModel: PlannerViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val dataSource = RoomFirestoreSyncService.getInstance(app).createOfflineFirstDataSource()
                PlannerViewModel(
                    repository = dataSource,
                    reminderScheduler = ReminderScheduler(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val celebratingItemId by viewModel.celebratingItemId.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    val calendarInvites by remember(sharedCalendarRepo, isLocalMode, currentUserId, userEmail) {
        if (isLocalMode) {
            flowOf(emptyList())
        } else {
            sharedCalendarRepo.observeAllVisibleInvites(currentUserId, userEmail)
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val sharedStreamError by sharedCalendarRepo.lastStreamError.collectAsStateWithLifecycle()

    LaunchedEffect(calendarInvites, currentUserId, isLocalMode) {
        if (!isLocalMode) {
            sharedCalendarRepo.notifyUnseenUpdates(calendarInvites, currentUserId)
        }
    }

    LaunchedEffect(celebratingItemId) {
        if (celebratingItemId != null) {
            feedbackManager.onCelebrateBloom()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.feedbackEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }
    LaunchedEffect(externalBannerMessage) {
        externalBannerMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearExternalBanner()
        }
    }
    LaunchedEffect(sharedStreamError) {
        sharedStreamError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Notification runtime permission (Android 13+) refreshed on ON_RESUME + Settings fallback
    fun checkNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    var hasNotificationPermission by remember { mutableStateOf(checkNotificationPermission()) }
    var hasRequestedNotificationOnce by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = checkNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }
    val requestNotifPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val activity = context.findActivity()
            val permanentlyDenied = hasRequestedNotificationOnce &&
                activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            if (permanentlyDenied) {
                runCatching {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }.onFailure {
                    runCatching {
                        val fallback = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(fallback)
                    }
                }
            } else {
                hasRequestedNotificationOnce = true
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var activeTab by rememberSaveable { mutableStateOf(PlannerTab.TODAY) }
    var selectedDateIso by rememberSaveable { mutableStateOf(CalendarDate.today().toIsoString()) }
    val selectedDate = CalendarDate.parse(selectedDateIso) ?: CalendarDate.today()
    var selectedItemId by rememberSaveable { mutableStateOf<String?>(null) }

    var showEditorDialog by remember { mutableStateOf(false) }
    var editorPrefillDate by remember { mutableStateOf(CalendarDate.today()) }
    var editingItem by remember { mutableStateOf<PlannerItem?>(null) }
    var showClassEditorModal by remember { mutableStateOf(false) }
    var editingClassSchedule by remember { mutableStateOf<ClassScheduleItem?>(null) }
    var classPrefillDayOfWeek by remember { mutableStateOf<Int?>(null) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showFocusSessionModal by rememberSaveable { mutableStateOf(false) }
    var showDoomscrollGuardModal by rememberSaveable { mutableStateOf(false) }
    var forceShowDedication by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showQuickNoteModal by rememberSaveable { mutableStateOf(false) }
    var quickNotePrefillText by rememberSaveable { mutableStateOf("") }
    var invitePrefill by remember { mutableStateOf<InviteFriendPrefill?>(null) }
    var inspectedSharedInvite by remember { mutableStateOf<CalendarInvite?>(null) }

    // Sign-Out & Delete Account confirmation modal states
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var signOutPendingOpsCount by remember { mutableIntStateOf(0) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }

    val inspectedInviteReplies by remember(sharedCalendarRepo, inspectedSharedInvite?.id) {
        val inviteId = inspectedSharedInvite?.id
        if (inviteId.isNullOrBlank()) flowOf(emptyList())
        else sharedCalendarRepo.observeReplies(inviteId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val liveInspectedInvite = inspectedSharedInvite?.let { currentInv ->
        calendarInvites.firstOrNull { it.id == currentInv.id } ?: currentInv
    }

    val requestSignOutFlow: () -> Unit = {
        scope.launch {
            signOutPendingOpsCount = syncService.getPendingOpCount()
            showSignOutConfirm = true
        }
    }

    val performCleanSignOut: () -> Unit = {
        scope.launch {
            syncService.stopCloudSync()
            ReminderScheduler(context).cancelAll()
            DoomscrollGuardManager.setGuardEnabled(context, false)
            FocusSessionManager.reset(context)
            FocusSessionManager.resetInMemoryForReload()
            AppUsageTracker.getInstance(context).resetAllUsage()
            LocalPlannerRepository.wipeAllUserLocalData(context)
            WidgetUpdateManager.refreshAllWidgetsSuspend(context)
            signOut(credentialManager = credentialManager, scope = scope)
            onSignedOutAndWiped("Signed out and cleared local account data.")
        }
    }

    // Apply home screen widget & notification deep-link directives cleanly
    LaunchedEffect(launchDirective?.id) {
        val directive = launchDirective ?: return@LaunchedEffect
        when (directive.tab) {
            WidgetIntents.TAB_TODAY -> activeTab = PlannerTab.TODAY
            WidgetIntents.TAB_CALENDAR -> activeTab = PlannerTab.CALENDAR
            WidgetIntents.TAB_CLASSES -> activeTab = PlannerTab.CLASSES
            WidgetIntents.TAB_NOTIFICATIONS -> activeTab = PlannerTab.NOTIFICATIONS
            WidgetIntents.TAB_REPORTS -> activeTab = PlannerTab.REPORTS
        }
        if (!directive.itemId.isNullOrBlank()) {
            selectedItemId = directive.itemId
        }
        when (directive.action) {
            WidgetIntents.ACTION_OPEN_QUICK_NOTE -> {
                quickNotePrefillText = ""
                showQuickNoteModal = true
            }
            WidgetIntents.ACTION_OPEN_ADD_ITEM -> {
                editingItem = null
                editorPrefillDate = CalendarDate.today()
                showEditorDialog = true
            }
            WidgetIntents.ACTION_OPEN_ADD_CLASS -> {
                editingClassSchedule = null
                classPrefillDayOfWeek = CalendarDate.today().dayOfWeekIndex()
                showClassEditorModal = true
            }
        }
        onDirectiveConsumed(directive.id)
    }

    // Optional Google Sign-In modal state for local-mode users
    var showCloudSyncModal by rememberSaveable { mutableStateOf(false) }
    var isSigningInToCloud by remember { mutableStateOf(false) }
    var cloudSignInError by remember { mutableStateOf<String?>(null) }

    val triggerGoogleSignInFromLocal: () -> Unit = {
        cloudSignInError = null
        isSigningInToCloud = true
        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isSigningInToCloud = false
                showCloudSyncModal = false
                onSignedInFromLocal()
            },
            onAuthError = { msg ->
                isSigningInToCloud = false
                cloudSignInError = msg
                showCloudSyncModal = true
            },
            scope = scope,
            onAuthCancelled = {
                isSigningInToCloud = false
            }
        )
    }

    val shellStage = when (val current = uiState) {
        is PlannerUiState.Loading -> PlannerShellStage.LOADING
        is PlannerUiState.Error -> PlannerShellStage.ERROR
        is PlannerUiState.Ready -> {
            if (!current.profile.hasSeenDedication || forceShowDedication) {
                PlannerShellStage.DEDICATION
            } else {
                PlannerShellStage.WORKSPACE
            }
        }
    }

    AnimatedContent(
        targetState = shellStage,
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(tween(180)) togetherWith fadeOut(tween(140))
            } else {
                (fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 28 }) togetherWith
                    fadeOut(tween(200, easing = FastOutSlowInEasing))
            }
        },
        label = "planner_shell_stage_transition"
    ) { targetStage ->
        when (targetStage) {
            PlannerShellStage.LOADING -> {
                HoppingBunnyLoadingScreen(
                    caption = if (isLocalMode) {
                        "Opening your local Bunny Memo…"
                    } else {
                        "Syncing your Bunny Memo…"
                    }
                )
            }

            PlannerShellStage.ERROR -> {
                val errMessage = (uiState as? PlannerUiState.Error)?.message ?: "Unable to load planner right now."
                ForgetMeNotStationeryBackground {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = PureWhite,
                            shape = MaterialTheme.shapes.large,
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier
                                .widthIn(max = 420.dp)
                                .testTag("planner_error_recovery_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Could not load planner",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MidnightNavy
                                )
                                Text(
                                    text = errMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OverdueTerracotta
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { viewModel.retryLoad() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DeepBlue,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("error_retry_button")
                                ) {
                                    Text("Retry")
                                }
                                if (!isLocalMode) {
                                    OutlinedButton(
                                        onClick = onFallbackToLocalMode,
                                        border = BorderStroke(1.dp, SubtleBorder),
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("error_continue_locally_button")
                                    ) {
                                        Text("Continue locally", color = MidnightNavy)
                                    }
                                    OutlinedButton(
                                        onClick = performCleanSignOut,
                                        border = BorderStroke(1.dp, OverdueTerracotta.copy(alpha = 0.5f)),
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("error_sign_out_button")
                                    ) {
                                        Text("Sign out", color = OverdueTerracotta)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            PlannerShellStage.DEDICATION -> {
                val ready = uiState as? PlannerUiState.Ready ?: return@AnimatedContent
                val isReopened = ready.profile.hasSeenDedication && forceShowDedication
                BackHandler(enabled = isReopened) {
                    forceShowDedication = false
                }
                DedicationScreen(
                    initialDedication = ready.profile.dedicationText,
                    isSaving = isSaving,
                    onOpenPlanner = { editedText ->
                        viewModel.saveDedication(editedText, markSeen = true)
                        forceShowDedication = false
                    },
                    onCancel = if (isReopened) {
                        { forceShowDedication = false }
                    } else null
                )
            }

            PlannerShellStage.WORKSPACE -> {
                val current = uiState as? PlannerUiState.Ready ?: return@AnimatedContent
                val selectedItem = selectedItemId?.let { id ->
                    current.items.firstOrNull { it.id == id }
                }
                val dueInboxCount = remember(current.inboxEntries, current.nowMillis) {
                    current.inboxEntries.count { it.triggerUtcMillis <= current.nowMillis }
                }
                val showFab = activeTab == PlannerTab.TODAY ||
                    activeTab == PlannerTab.CALENDAR ||
                    activeTab == PlannerTab.CLASSES

                ForgetMeNotStationeryBackground {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val useTabletRail = maxWidth >= 720.dp
                        val detailPaneWidth = adaptiveDetailPaneWidth(maxWidth)
                        val showingCompactDetail = !useTabletRail && selectedItem != null

                        // Tablet BackHandler closes side detail pane before leaving app
                        BackHandler(enabled = useTabletRail && selectedItem != null) {
                            selectedItemId = null
                            viewModel.clearCelebration()
                        }

                        val drawerState = rememberDrawerState(DrawerValue.Closed)
                        BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
                        var createMenuOpen by remember { mutableStateOf(false) }

                        val detailCallbacks = remember(current, viewModel, feedbackManager) {
                            object {
                                fun onEdit(item: PlannerItem) {
                                    editingItem = item
                                    editorPrefillDate = item.primaryCalendarDate() ?: current.todayDate()
                                    showEditorDialog = true
                                }
                                fun onInvite(item: PlannerItem) {
                                    val timeLabel = when {
                                        item.startAtUtcMillis != null -> PlannerDateUtils.formatEventTimeRange(
                                            item.startAtUtcMillis,
                                            item.endAtUtcMillis
                                        )
                                        item.dueTimeMinutes != null -> PlannerDateUtils.formatTimeOfDay(item.dueTimeMinutes)
                                        else -> null
                                    }
                                    invitePrefill = InviteFriendPrefill(
                                        title = item.title,
                                        dateIso = (item.primaryCalendarDate() ?: current.todayDate()).toIsoString(),
                                        timeLabel = timeLabel,
                                        notes = item.notes,
                                        itemId = item.id,
                                        itemType = item.itemType(),
                                        category = item.taskCategory()
                                    )
                                }
                            }
                        }

                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            gesturesEnabled = !showingCompactDetail,
                            drawerContent = {
                                ModalDrawerSheet(drawerContainerColor = PureWhite) {
                                    Column(
                                        modifier = Modifier
                                            .verticalScroll(rememberScrollState())
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            ForgetMeNotFlower(bloomed = true, size = 32.dp)
                                            Column {
                                                Text(
                                                    text = stringResource(R.string.app_name),
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    color = MidnightNavy
                                                )
                                                Text(
                                                    text = if (isLocalMode) stringResource(R.string.local_mode_badge)
                                                    else (userEmail ?: stringResource(R.string.sync_status_synced)),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MutedNavy,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        val drawerItemColors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = SkyBlue.copy(alpha = 0.72f),
                                            selectedIconColor = DeepBlue,
                                            selectedTextColor = MidnightNavy,
                                            unselectedContainerColor = Color.Transparent,
                                            unselectedIconColor = MutedNavy,
                                            unselectedTextColor = MidnightNavy
                                        )

                                        HorizontalDivider(
                                            modifier = Modifier.padding(bottom = 10.dp),
                                            color = SubtleBorder
                                        )

                                        NavigationDrawerItem(
                                            label = { Text(if (isLocalMode) "Back up memos" else "Sync now") },
                                            selected = false,
                                            icon = {
                                                Icon(
                                                    imageVector = if (isLocalMode) Icons.Outlined.CloudUpload else Icons.Outlined.CloudSync,
                                                    contentDescription = null,
                                                    tint = DeepBlue
                                                )
                                            },
                                            colors = drawerItemColors,
                                            onClick = {
                                                feedbackManager.onLightTick()
                                                scope.launch { drawerState.close() }
                                                if (isLocalMode) {
                                                    cloudSignInError = null
                                                    showCloudSyncModal = true
                                                } else {
                                                    onRequestManualSync()
                                                }
                                            }
                                        )
                                        NavigationDrawerItem(
                                            label = { Text("Dedication") },
                                            selected = false,
                                            icon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = DeepBlue) },
                                            colors = drawerItemColors,
                                            onClick = {
                                                feedbackManager.onPopOpen()
                                                scope.launch { drawerState.close() }
                                                forceShowDedication = true
                                            }
                                        )
                                        NavigationDrawerItem(
                                            label = { Text("Settings") },
                                            selected = false,
                                            icon = { Icon(Icons.Outlined.Settings, contentDescription = null, tint = DeepBlue) },
                                            colors = drawerItemColors,
                                            onClick = {
                                                feedbackManager.onPopOpen()
                                                scope.launch { drawerState.close() }
                                                showSettingsDialog = true
                                            }
                                        )

                                        if (!isLocalMode) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                color = SubtleBorder
                                            )
                                            NavigationDrawerItem(
                                                label = { Text(stringResource(R.string.profile_sign_out_cta)) },
                                                selected = false,
                                                icon = {
                                                    Icon(
                                                        Icons.AutoMirrored.Outlined.Logout,
                                                        contentDescription = null,
                                                        tint = MutedNavy
                                                    )
                                                },
                                                colors = drawerItemColors,
                                                onClick = {
                                                    feedbackManager.onLightTick()
                                                    scope.launch { drawerState.close() }
                                                    requestSignOutFlow()
                                                }
                                            )
                                            NavigationDrawerItem(
                                                label = {
                                                    Text(
                                                        text = stringResource(R.string.profile_delete_account_cta),
                                                        color = OverdueTerracotta
                                                    )
                                                },
                                                selected = false,
                                                icon = {
                                                    Icon(
                                                        Icons.Outlined.DeleteForever,
                                                        contentDescription = null,
                                                        tint = OverdueTerracotta
                                                    )
                                                },
                                                colors = drawerItemColors,
                                                onClick = {
                                                    feedbackManager.onLightTick()
                                                    scope.launch { drawerState.close() }
                                                    showDeleteAccountConfirm = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Main workspace stays mounted behind compact detail overlay so scroll & Calendar state never reset
                                Scaffold(
                                    containerColor = Color.Transparent,
                                    contentWindowInsets = WindowInsets.safeDrawing,
                                    snackbarHost = { SnackbarHost(snackbarHostState) },
                                    topBar = {
                                        Column {
                                            TopAppBar(
                                                colors = TopAppBarDefaults.topAppBarColors(
                                                    containerColor = SkyCanvasTop.copy(alpha = 0.90f),
                                                    titleContentColor = MidnightNavy
                                                ),
                                                title = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        ForgetMeNotFlower(bloomed = true, size = 24.dp)
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column {
                                                            Text(
                                                                text = stringResource(activeTab.labelRes),
                                                                style = MaterialTheme.typography.titleLarge,
                                                                color = MidnightNavy,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            val statusText = when {
                                                                isLocalMode -> stringResource(R.string.local_mode_badge)
                                                                syncStatus.phase == SyncPhase.SYNCING -> stringResource(
                                                                    R.string.sync_status_syncing,
                                                                    syncStatus.pendingCount.coerceAtLeast(1)
                                                                )
                                                                syncStatus.phase == SyncPhase.OFFLINE_QUEUED -> stringResource(
                                                                    R.string.sync_status_offline_pending,
                                                                    syncStatus.pendingCount
                                                                )
                                                                syncStatus.phase == SyncPhase.OFFLINE_IDLE -> stringResource(
                                                                    R.string.sync_status_offline_idle
                                                                )
                                                                syncStatus.phase == SyncPhase.ERROR -> stringResource(
                                                                    R.string.sync_status_error,
                                                                    syncStatus.pendingCount
                                                                )
                                                                else -> stringResource(R.string.sync_status_synced)
                                                            }
                                                            val statusColor = when {
                                                                !isLocalMode && (syncStatus.phase == SyncPhase.OFFLINE_QUEUED || syncStatus.phase == SyncPhase.ERROR) -> OverdueTerracotta
                                                                !isLocalMode && syncStatus.phase == SyncPhase.SYNCING -> DeepBlue
                                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                            }
                                                            Text(
                                                                text = statusText,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = statusColor,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.testTag("top_bar_sync_status_text")
                                                            )
                                                        }
                                                    }
                                                },
                                                navigationIcon = {
                                                    IconButton(
                                                        onClick = {
                                                            feedbackManager.onLightTick()
                                                            scope.launch { drawerState.open() }
                                                        },
                                                        modifier = Modifier.testTag("top_bar_drawer_button")
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.Menu,
                                                            contentDescription = "Open planner navigation",
                                                            tint = MidnightNavy
                                                        )
                                                    }
                                                },
                                                actions = {
                                                    // Pomodoro Focus Timer quick action
                                                    IconButton(
                                                        onClick = {
                                                            feedbackManager.onPopOpen()
                                                            showFocusSessionModal = true
                                                        },
                                                        modifier = Modifier.testTag("top_bar_focus_button")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.Timer,
                                                            contentDescription = stringResource(R.string.focus_session_cd),
                                                            tint = DeepBlue
                                                        )
                                                    }
                                                    // Badged Inbox bell directly in TopAppBar for instant 1-tap access
                                                    IconButton(
                                                        onClick = {
                                                            feedbackManager.onTabSwitch()
                                                            activeTab = PlannerTab.NOTIFICATIONS
                                                            searchQuery = ""
                                                            searchOpen = false
                                                        },
                                                        modifier = Modifier.testTag("top_bar_inbox_button")
                                                    ) {
                                                        TabIcon(
                                                            tab = PlannerTab.NOTIFICATIONS,
                                                            selected = activeTab == PlannerTab.NOTIFICATIONS,
                                                            badgeCount = dueInboxCount
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            feedbackManager.onPopOpen()
                                                            searchOpen = !searchOpen
                                                            if (!searchOpen) searchQuery = ""
                                                        },
                                                        modifier = Modifier.testTag("top_bar_search_button")
                                                    ) {
                                                        Icon(
                                                            imageVector = if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                                                            contentDescription = if (searchOpen) "Close search" else "Search planner",
                                                            tint = MidnightNavy
                                                        )
                                                    }
                                                }
                                            )
                                            HorizontalDivider(
                                                thickness = 0.75.dp,
                                                color = SubtleBorder.copy(alpha = 0.85f)
                                            )
                                        }
                                    },
                                    bottomBar = {
                                        if (!useTabletRail) {
                                            Column {
                                                HorizontalDivider(
                                                    thickness = 0.75.dp,
                                                    color = SubtleBorder.copy(alpha = 0.85f)
                                                )
                                                NavigationBar(
                                                    containerColor = PureWhite,
                                                    contentColor = MidnightNavy,
                                                    modifier = Modifier.testTag("planner_bottom_nav_bar")
                                                ) {
                                                    PRIMARY_NAV_TABS.forEach { tab ->
                                                        val isSelected = activeTab == tab
                                                        NavigationBarItem(
                                                            selected = isSelected,
                                                            onClick = {
                                                                if (!isSelected) {
                                                                    feedbackManager.onTabSwitch()
                                                                    activeTab = tab
                                                                    selectedItemId = null
                                                                    searchQuery = ""
                                                                    searchOpen = false
                                                                }
                                                            },
                                                            icon = { TabIcon(tab = tab, selected = isSelected, badgeCount = 0) },
                                                            label = {
                                                                Text(
                                                                    text = stringResource(tab.labelRes),
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            },
                                                            colors = NavigationBarItemDefaults.colors(
                                                                selectedIconColor = DeepBlue,
                                                                selectedTextColor = DeepBlue,
                                                                indicatorColor = SkyBlue.copy(alpha = 0.72f),
                                                                unselectedIconColor = MutedNavy,
                                                                unselectedTextColor = MutedNavy
                                                            ),
                                                            modifier = Modifier.testTag("bottom_nav_${tab.name.lowercase()}")
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    floatingActionButton = {
                                        if (showFab) {
                                            Box {
                                                ExtendedFloatingActionButton(
                                                    onClick = {
                                                        feedbackManager.onPopOpen()
                                                        createMenuOpen = true
                                                    },
                                                    containerColor = DeepBlue,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                                    text = { Text("Create") },
                                                    modifier = Modifier.testTag("workspace_create_fab")
                                                )
                                                DropdownMenu(
                                                    expanded = createMenuOpen,
                                                    onDismissRequest = { createMenuOpen = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                if (activeTab == PlannerTab.CLASSES) "Add class"
                                                                else "Add planner item"
                                                            )
                                                        },
                                                        onClick = {
                                                            feedbackManager.onPopOpen()
                                                            createMenuOpen = false
                                                            if (activeTab == PlannerTab.CLASSES) {
                                                                editingClassSchedule = null
                                                                classPrefillDayOfWeek = current.todayDate().dayOfWeekIndex()
                                                                showClassEditorModal = true
                                                            } else {
                                                                editingItem = null
                                                                editorPrefillDate = if (activeTab == PlannerTab.CALENDAR) {
                                                                    selectedDate
                                                                } else {
                                                                    current.todayDate()
                                                                }
                                                                showEditorDialog = true
                                                            }
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.action_quick_note)) },
                                                        onClick = {
                                                            feedbackManager.onPopOpen()
                                                            createMenuOpen = false
                                                            quickNotePrefillText = ""
                                                            showQuickNoteModal = true
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                ) { innerPadding ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding)
                                    ) {
                                        // Persistent Tablet NavigationRail (>= 720dp)
                                        if (useTabletRail) {
                                            NavigationRail(
                                                containerColor = PureWhite.copy(alpha = 0.94f),
                                                contentColor = MidnightNavy,
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .testTag("planner_nav_rail")
                                            ) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                PRIMARY_NAV_TABS.forEach { tab ->
                                                    val isSelected = activeTab == tab
                                                    NavigationRailItem(
                                                        selected = isSelected,
                                                        onClick = {
                                                            if (!isSelected) {
                                                                feedbackManager.onTabSwitch()
                                                                activeTab = tab
                                                                selectedItemId = null
                                                                searchQuery = ""
                                                                searchOpen = false
                                                            }
                                                        },
                                                        icon = { TabIcon(tab = tab, selected = isSelected, badgeCount = 0) },
                                                        label = {
                                                            Text(
                                                                text = stringResource(tab.labelRes),
                                                                maxLines = 1
                                                            )
                                                        },
                                                        colors = NavigationRailItemDefaults.colors(
                                                            selectedIconColor = DeepBlue,
                                                            selectedTextColor = DeepBlue,
                                                            indicatorColor = SkyBlue.copy(alpha = 0.72f),
                                                            unselectedIconColor = MutedNavy,
                                                            unselectedTextColor = MutedNavy
                                                        ),
                                                        modifier = Modifier.testTag("nav_rail_${tab.name.lowercase()}")
                                                    )
                                                }
                                            }
                                            VerticalDivider(color = SubtleBorder.copy(alpha = 0.85f))
                                        }

                                        BackHandler(enabled = searchOpen && drawerState.isClosed && !showingCompactDetail) {
                                            searchQuery = ""
                                            searchOpen = false
                                        }

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        ) {
                                            val searchHorizontalPad = adaptiveHorizontalPadding(this@BoxWithConstraints.maxWidth)
                                            if (searchOpen) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(
                                                            start = searchHorizontalPad,
                                                            end = searchHorizontalPad,
                                                            top = 12.dp,
                                                            bottom = 6.dp
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    PlannerSearchBar(
                                                        query = searchQuery,
                                                        onQueryChange = { searchQuery = it },
                                                        modifier = Modifier.widthIn(max = 760.dp)
                                                    )
                                                }
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxWidth()
                                            ) {
                                                val isSearching = searchQuery.isNotBlank()
                                                AnimatedContent(
                                                    targetState = isSearching,
                                                    transitionSpec = {
                                                        fadeIn(tween(150, easing = FastOutSlowInEasing)) togetherWith
                                                            fadeOut(tween(120, easing = FastOutSlowInEasing))
                                                    },
                                                    label = "planner_search_mode_transition"
                                                ) { showingSearch ->
                                                    if (showingSearch) {
                                                        PlannerSearchResultsView(
                                                            query = searchQuery,
                                                            state = current,
                                                            selectedItemId = selectedItemId,
                                                            onSelectItem = { id -> selectedItemId = id },
                                                            onToggleTask = { task, checked ->
                                                                feedbackManager.onTaskToggled(checked)
                                                                viewModel.toggleItemCompleted(task, checked)
                                                            },
                                                            onEditClass = { cls ->
                                                                editingClassSchedule = cls
                                                                classPrefillDayOfWeek = null
                                                                showClassEditorModal = true
                                                            },
                                                            onClearSearch = { searchQuery = "" },
                                                            onSaveQueryAsQuickNote = { prefill ->
                                                                quickNotePrefillText = prefill
                                                                showQuickNoteModal = true
                                                            }
                                                        )
                                                    } else {
                                                        AnimatedContent(
                                                            targetState = activeTab,
                                                            transitionSpec = {
                                                                if (reducedMotion) {
                                                                    fadeIn(tween(130)) togetherWith fadeOut(tween(100))
                                                                } else {
                                                                    val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                                                    (slideInHorizontally(
                                                                        animationSpec = tween(210, easing = FastOutSlowInEasing)
                                                                    ) { width -> direction * (width / 24) } +
                                                                        fadeIn(tween(190, easing = FastOutSlowInEasing))) togetherWith
                                                                        (slideOutHorizontally(
                                                                            animationSpec = tween(160, easing = FastOutSlowInEasing)
                                                                        ) { width -> -direction * (width / 28) } +
                                                                            fadeOut(tween(140, easing = FastOutSlowInEasing)))
                                                                }
                                                            },
                                                            label = "section_tab_transition"
                                                        ) { targetTab ->
                                                            when (targetTab) {
                                                                PlannerTab.TODAY -> TodayScreen(
                                                                    state = current,
                                                                    selectedItemId = selectedItemId,
                                                                    onSelectItem = { id -> selectedItemId = id },
                                                                    onToggleTask = { task, checked ->
                                                                        feedbackManager.onTaskToggled(checked)
                                                                        viewModel.toggleItemCompleted(task, checked)
                                                                    },
                                                                    onToggleStep = { step, checked ->
                                                                        feedbackManager.onTaskToggled(checked)
                                                                        viewModel.toggleUnifiedStep(step, checked)
                                                                    },
                                                                    onAddClick = { date ->
                                                                        editingItem = null
                                                                        editorPrefillDate = date
                                                                        showEditorDialog = true
                                                                    },
                                                                    onEditClass = { cls ->
                                                                        editingClassSchedule = cls
                                                                        classPrefillDayOfWeek = null
                                                                        showClassEditorModal = true
                                                                    },
                                                                    onOpenReports = {
                                                                        feedbackManager.onTabSwitch()
                                                                        activeTab = PlannerTab.REPORTS
                                                                    },
                                                                    onOpenFocusSession = {
                                                                        feedbackManager.onPopOpen()
                                                                        showFocusSessionModal = true
                                                                    },
                                                                    onOpenFocusGuard = {
                                                                        feedbackManager.onPopOpen()
                                                                        showDoomscrollGuardModal = true
                                                                    },
                                                                    calendarInvites = calendarInvites,
                                                                    currentUserId = if (isLocalMode) null else currentUserId,
                                                                    onInviteFriendClick = {
                                                                        invitePrefill = InviteFriendPrefill(
                                                                            title = "",
                                                                            dateIso = current.todayDate().toIsoString()
                                                                        )
                                                                    },
                                                                    onOpenSharedInvite = { invite ->
                                                                        inspectedSharedInvite = invite
                                                                    }
                                                                )

                                                                PlannerTab.CALENDAR -> CalendarScreen(
                                                                    state = current,
                                                                    selectedDate = selectedDate,
                                                                    selectedItemId = selectedItemId,
                                                                    onSelectDate = { d -> selectedDateIso = d.toIsoString() },
                                                                    onSelectItem = { id -> selectedItemId = id },
                                                                    onAddForDate = { d ->
                                                                        editingItem = null
                                                                        editorPrefillDate = d
                                                                        showEditorDialog = true
                                                                    },
                                                                    onEditClass = { cls ->
                                                                        editingClassSchedule = cls
                                                                        classPrefillDayOfWeek = null
                                                                        showClassEditorModal = true
                                                                    },
                                                                    onToggleItemCompleted = { item, completed ->
                                                                        viewModel.toggleItemCompleted(item, completed)
                                                                    },
                                                                    calendarInvites = calendarInvites,
                                                                    currentUserId = if (isLocalMode) null else currentUserId,
                                                                    onInviteFriendClick = { date ->
                                                                        invitePrefill = InviteFriendPrefill(
                                                                            title = "",
                                                                            dateIso = date.toIsoString()
                                                                        )
                                                                    },
                                                                    onOpenSharedInvite = { invite ->
                                                                        inspectedSharedInvite = invite
                                                                    }
                                                                )

                                                                PlannerTab.CLASSES -> ClassScheduleScreen(
                                                                    state = current,
                                                                    hasNotificationPermission = hasNotificationPermission,
                                                                    onRequestNotificationPermission = requestNotifPermission,
                                                                    onAddClassForDay = { dayIdx ->
                                                                        editingClassSchedule = null
                                                                        classPrefillDayOfWeek = dayIdx
                                                                        showClassEditorModal = true
                                                                    },
                                                                    onEditClass = { cls ->
                                                                        editingClassSchedule = cls
                                                                        classPrefillDayOfWeek = null
                                                                        showClassEditorModal = true
                                                                    }
                                                                )

                                                                PlannerTab.NOTIFICATIONS -> NotificationsScreen(
                                                                    state = current,
                                                                    hasNotificationPermission = hasNotificationPermission,
                                                                    onRequestNotificationPermission = requestNotifPermission,
                                                                    onToggleDailyDigest = { enabled ->
                                                                        feedbackManager.onLightTick()
                                                                        viewModel.setDailyDigestEnabled(enabled)
                                                                    },
                                                                    onUpdateDailyDigestTime = { h, m ->
                                                                        feedbackManager.onSaveSuccess()
                                                                        viewModel.updateDailyDigestTime(h, m)
                                                                    },
                                                                    onSendDailyDigestPreview = {
                                                                        feedbackManager.onPopOpen()
                                                                        viewModel.sendDailyDigestPreview()
                                                                    },
                                                                    onOpenItem = { id -> selectedItemId = id },
                                                                    onRescheduleItem = { item ->
                                                                        editingItem = item
                                                                        editorPrefillDate = item.primaryCalendarDate() ?: current.todayDate()
                                                                        showEditorDialog = true
                                                                    },
                                                                    onSnoozeReminder = { rem, mins ->
                                                                        viewModel.snoozeReminder(rem, mins)
                                                                    },
                                                                    onDismissReminder = { rem ->
                                                                        viewModel.dismissReminder(rem)
                                                                    },
                                                                    calendarInvites = calendarInvites,
                                                                    currentUserId = if (isLocalMode) null else currentUserId,
                                                                    onOpenSharedInvite = { invite ->
                                                                        inspectedSharedInvite = invite
                                                                    },
                                                                    onInviteFriendClick = {
                                                                        invitePrefill = InviteFriendPrefill(
                                                                            title = "",
                                                                            dateIso = current.todayDate().toIsoString()
                                                                        )
                                                                    }
                                                                )

                                                                PlannerTab.REPORTS -> ReportsScreen(
                                                                    state = current,
                                                                    onSelectItem = { id -> selectedItemId = id }
                                                                )

                                                                PlannerTab.PROFILE -> ProfileScreen(
                                                                    state = current,
                                                                    userDisplayName = userDisplayName,
                                                                    userEmail = userEmail,
                                                                    userPhotoUrl = userPhotoUrl,
                                                                    isLocalMode = isLocalMode,
                                                                    syncStatus = syncStatus,
                                                                    calendarInvites = calendarInvites,
                                                                    isExpanded = useTabletRail,
                                                                    onEditDedication = {
                                                                        forceShowDedication = true
                                                                    },
                                                                    onOpenSettings = {
                                                                        showSettingsDialog = true
                                                                    },
                                                                    onInviteFriendClick = {
                                                                        invitePrefill = InviteFriendPrefill(
                                                                            title = "",
                                                                            dateIso = current.todayDate().toIsoString()
                                                                        )
                                                                    },
                                                                    onTriggerSync = {
                                                                        onRequestManualSync()
                                                                    },
                                                                    onGoogleSignIn = {
                                                                        cloudSignInError = null
                                                                        showCloudSyncModal = true
                                                                    },
                                                                    onSignOut = requestSignOutFlow,
                                                                    onDeleteAccount = if (!isLocalMode) {
                                                                        { showDeleteAccountConfirm = true }
                                                                    } else null
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Tablet/expanded detail side panel
                                        AnimatedVisibility(
                                            visible = useTabletRail && selectedItem != null,
                                            enter = fadeIn(tween(180)) + expandHorizontally(
                                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                                                expandFrom = Alignment.Start
                                            ),
                                            exit = fadeOut(tween(140)) + shrinkHorizontally(
                                                animationSpec = tween(180, easing = FastOutSlowInEasing),
                                                shrinkTowards = Alignment.Start
                                            )
                                        ) {
                                            if (selectedItem != null) {
                                                Row(
                                                    modifier = Modifier
                                                        .width(detailPaneWidth)
                                                        .fillMaxHeight()
                                                ) {
                                                    VerticalDivider(color = SubtleBorder)
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxHeight()
                                                    ) {
                                                        ItemDetailScreen(
                                                            item = selectedItem,
                                                            state = current,
                                                            isCelebrating = celebratingItemId == selectedItem.id,
                                                            showBackButton = false,
                                                            onBack = {
                                                                selectedItemId = null
                                                                viewModel.clearCelebration()
                                                            },
                                                            onEditItem = detailCallbacks::onEdit,
                                                            onToggleItemCompleted = { item, checked ->
                                                                feedbackManager.onTaskToggled(checked)
                                                                viewModel.toggleItemCompleted(item, checked)
                                                            },
                                                            onDeleteItem = { item ->
                                                                feedbackManager.onDeleteAction()
                                                                viewModel.deleteItem(item) { selectedItemId = null }
                                                            },
                                                            onAddStep = { itemId, title, due ->
                                                                feedbackManager.onSaveSuccess()
                                                                viewModel.addStep(itemId, title, due, null)
                                                            },
                                                            onToggleStep = { step, checked ->
                                                                feedbackManager.onTaskToggled(checked)
                                                                viewModel.toggleUnifiedStep(step, checked)
                                                            },
                                                            onDeleteStep = { step ->
                                                                feedbackManager.onDeleteAction()
                                                                viewModel.deleteStep(step)
                                                            },
                                                            onAddReminder = { item, kind, offset ->
                                                                feedbackManager.onSaveSuccess()
                                                                viewModel.addReminder(
                                                                    item = item,
                                                                    kind = kind,
                                                                    offsetMinutes = offset,
                                                                    localHour = current.profile.defaultReminderHour,
                                                                    localMinute = current.profile.defaultReminderMinute
                                                                )
                                                            },
                                                            onDeleteReminder = { rem ->
                                                                feedbackManager.onDeleteAction()
                                                                viewModel.deleteReminder(rem)
                                                            },
                                                            onUpdatePhotos = { item, paths ->
                                                                feedbackManager.onLightTick()
                                                                viewModel.updateItemPhotos(item, paths)
                                                            },
                                                            onUpdateAudioMemo = { item, path, durationSec ->
                                                                viewModel.updateItemAudioMemo(item, path, durationSec)
                                                            },
                                                            onUpdatePriority = { item, priority ->
                                                                viewModel.updateItemPriority(item, priority)
                                                            },
                                                            onUpdateCategory = { item, category ->
                                                                viewModel.updateItemCategory(item, category)
                                                            },
                                                            onLogStepTime = { step, mins ->
                                                                viewModel.logStepTimeSpent(step, mins)
                                                            },
                                                            onInviteFriend = detailCallbacks::onInvite,
                                                            onDismissCelebration = { viewModel.clearCelebration() }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Companion bunny sits cleanly above BottomNavBar on phones
                                if (!showingCompactDetail) {
                                    MonthBunnyOverlay(
                                        state = current,
                                        onOpenItem = { itemId -> selectedItemId = itemId },
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(bottom = if (useTabletRail) 4.dp else 82.dp)
                                    )
                                }

                                // Compact-phone full-screen ItemDetailScreen overlay (keeps active tab mounted beneath!)
                                AnimatedVisibility(
                                    visible = showingCompactDetail,
                                    enter = if (reducedMotion) fadeIn(tween(140)) else slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 16 } + fadeIn(tween(200)),
                                    exit = if (reducedMotion) fadeOut(tween(110)) else slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it / 16 } + fadeOut(tween(150))
                                ) {
                                    if (selectedItem != null) {
                                        Surface(
                                            color = CloudWhite,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            ForgetMeNotStationeryBackground {
                                                Scaffold(
                                                    containerColor = Color.Transparent,
                                                    contentWindowInsets = WindowInsets.safeDrawing,
                                                    snackbarHost = { SnackbarHost(snackbarHostState) }
                                                ) { innerPadding ->
                                                    ItemDetailScreen(
                                                        item = selectedItem,
                                                        state = current,
                                                        isCelebrating = celebratingItemId == selectedItem.id,
                                                        showBackButton = true,
                                                        onBack = {
                                                            selectedItemId = null
                                                            viewModel.clearCelebration()
                                                        },
                                                        onEditItem = detailCallbacks::onEdit,
                                                        onToggleItemCompleted = { item, checked ->
                                                            feedbackManager.onTaskToggled(checked)
                                                            viewModel.toggleItemCompleted(item, checked)
                                                        },
                                                        onDeleteItem = { item ->
                                                            feedbackManager.onDeleteAction()
                                                            viewModel.deleteItem(item) { selectedItemId = null }
                                                        },
                                                        onAddStep = { itemId, title, due ->
                                                            feedbackManager.onSaveSuccess()
                                                            viewModel.addStep(itemId, title, due, null)
                                                        },
                                                        onToggleStep = { step, checked ->
                                                            feedbackManager.onTaskToggled(checked)
                                                            viewModel.toggleUnifiedStep(step, checked)
                                                        },
                                                        onDeleteStep = { step ->
                                                            feedbackManager.onDeleteAction()
                                                            viewModel.deleteStep(step)
                                                        },
                                                        onAddReminder = { item, kind, offset ->
                                                            feedbackManager.onSaveSuccess()
                                                            viewModel.addReminder(
                                                                item = item,
                                                                kind = kind,
                                                                offsetMinutes = offset,
                                                                localHour = current.profile.defaultReminderHour,
                                                                localMinute = current.profile.defaultReminderMinute
                                                            )
                                                        },
                                                        onDeleteReminder = { rem ->
                                                            feedbackManager.onDeleteAction()
                                                            viewModel.deleteReminder(rem)
                                                        },
                                                        onUpdatePhotos = { item, paths ->
                                                            feedbackManager.onLightTick()
                                                            viewModel.updateItemPhotos(item, paths)
                                                        },
                                                        onUpdateAudioMemo = { item, path, durationSec ->
                                                            viewModel.updateItemAudioMemo(item, path, durationSec)
                                                        },
                                                        onUpdatePriority = { item, priority ->
                                                            viewModel.updateItemPriority(item, priority)
                                                        },
                                                        onUpdateCategory = { item, category ->
                                                            viewModel.updateItemCategory(item, category)
                                                        },
                                                        onLogStepTime = { step, mins ->
                                                            viewModel.logStepTimeSpent(step, mins)
                                                        },
                                                        onInviteFriend = detailCallbacks::onInvite,
                                                        onDismissCelebration = { viewModel.clearCelebration() },
                                                        modifier = Modifier.padding(innerPadding)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showEditorDialog) {
                    val plansForLinking = current.items.filter {
                        it.itemType() == ItemType.PLAN && it.id != editingItem?.id
                    }
                    val existingRem = editingItem?.let { current.remindersFor(it.id) } ?: emptyList()
                    ItemEditorDialog(
                        prefillDate = editorPrefillDate,
                        existingItem = editingItem,
                        existingReminders = existingRem,
                        availablePlans = plansForLinking,
                        defaultReminderHour = current.profile.defaultReminderHour,
                        defaultReminderMinute = current.profile.defaultReminderMinute,
                        isSaving = isSaving,
                        onDismiss = {
                            showEditorDialog = false
                            editingItem = null
                        },
                        onSaveNew = { type, title, notes, startDate, dueDate, dueTime, startUtc, endUtc, parentPlanId, steps, reminders, imagePaths, priority, category ->
                            viewModel.createItem(
                                type = type,
                                title = title,
                                notes = notes,
                                startDate = startDate,
                                dueDate = dueDate,
                                dueTimeMinutes = dueTime,
                                startAtUtcMillis = startUtc,
                                endAtUtcMillis = endUtc,
                                parentPlanId = parentPlanId,
                                steps = steps,
                                reminders = reminders,
                                imagePaths = imagePaths,
                                priority = priority,
                                category = category
                            ) { createdId ->
                                feedbackManager.onSaveSuccess()
                                showEditorDialog = false
                                selectedItemId = createdId
                            }
                        },
                        onSaveEdit = { item, type, title, notes, startDate, dueDate, dueTime, startUtc, endUtc, parentPlanId, reminders, imagePaths, priority, category ->
                            viewModel.updateItem(
                                item = item,
                                type = type,
                                title = title,
                                notes = notes,
                                startDate = startDate,
                                dueDate = dueDate,
                                dueTimeMinutes = dueTime,
                                startAtUtcMillis = startUtc,
                                endAtUtcMillis = endUtc,
                                parentPlanId = parentPlanId,
                                updatedReminders = reminders,
                                imagePaths = imagePaths,
                                priority = priority,
                                category = category
                            ) {
                                feedbackManager.onSaveSuccess()
                                showEditorDialog = false
                                editingItem = null
                            }
                        }
                    )
                }

                if (showSettingsDialog) {
                    SettingsDialog(
                        profile = current.profile,
                        dailyReminderConfig = current.dailyReminderConfig,
                        isLocalMode = isLocalMode,
                        userEmail = userEmail,
                        syncStatus = syncStatus,
                        hasNotificationPermission = hasNotificationPermission,
                        isSaving = isSaving,
                        onDismiss = { showSettingsDialog = false },
                        onSaveDedication = { text -> viewModel.saveDedication(text, markSeen = true) },
                        onUpdateDefaultReminderTime = { h, m -> viewModel.updateDefaultReminderTime(h, m) },
                        onToggleDailyDigest = { enabled -> viewModel.setDailyDigestEnabled(enabled) },
                        onUpdateDailyDigestTime = { h, m -> viewModel.updateDailyDigestTime(h, m) },
                        onRequestNotificationPermission = requestNotifPermission,
                        onSyncNow = onRequestManualSync,
                        onPinWidget = { kind ->
                            val pinned = WidgetUpdateManager.requestPinWidget(context, kind)
                            WidgetUpdateManager.refreshAllWidgets(context)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (pinned) {
                                        "Follow your launcher prompt to place the widget on your home screen."
                                    } else {
                                        "Long-press your home screen and choose Widgets → Bunny Memo to add it."
                                    }
                                )
                            }
                        },
                        onOpenFocusGuard = {
                            showSettingsDialog = false
                            showDoomscrollGuardModal = true
                        },
                        onSignInWithGoogle = {
                            cloudSignInError = null
                            showCloudSyncModal = true
                        },
                        onSignOut = {
                            showSettingsDialog = false
                            requestSignOutFlow()
                        }
                    )
                }

                if (showFocusSessionModal) {
                    FocusSessionModal(
                        activeTasks = current.items.filter { !it.completed && it.itemType() == ItemType.TASK },
                        onOpenBlockedAppsSetup = {
                            showFocusSessionModal = false
                            showDoomscrollGuardModal = true
                        },
                        onDismiss = { showFocusSessionModal = false }
                    )
                }

                if (showDoomscrollGuardModal) {
                    DoomscrollGuardModal(
                        onDismiss = { showDoomscrollGuardModal = false }
                    )
                }

                if (showClassEditorModal) {
                    ClassScheduleEditorModal(
                        existing = editingClassSchedule,
                        prefillDayOfWeek = classPrefillDayOfWeek,
                        prefillDate = if (activeTab == PlannerTab.CALENDAR) selectedDate else current.todayDate(),
                        isSaving = isSaving,
                        onDismiss = {
                            showClassEditorModal = false
                            editingClassSchedule = null
                        },
                        onSave = { subject, room, repeatMode, daysOfWeek, dayOfMonth, startMin, endMin, semStart, semMonths, notifyEnabled, notifyOffset, colorKey ->
                            val existing = editingClassSchedule
                            if (existing == null) {
                                viewModel.createClassSchedule(
                                    subject = subject,
                                    room = room,
                                    repeatMode = repeatMode,
                                    daysOfWeek = daysOfWeek,
                                    dayOfMonth = dayOfMonth,
                                    startTimeMinutes = startMin,
                                    endTimeMinutes = endMin,
                                    semesterStartDate = semStart,
                                    semesterMonths = semMonths,
                                    notifyEnabled = notifyEnabled,
                                    notifyOffsetMinutes = notifyOffset,
                                    colorKey = colorKey
                                ) {
                                    feedbackManager.onSaveSuccess()
                                    showClassEditorModal = false
                                    editingClassSchedule = null
                                }
                            } else {
                                viewModel.updateClassSchedule(
                                    scheduleId = existing.id,
                                    subject = subject,
                                    room = room,
                                    repeatMode = repeatMode,
                                    daysOfWeek = daysOfWeek,
                                    dayOfMonth = dayOfMonth,
                                    startTimeMinutes = startMin,
                                    endTimeMinutes = endMin,
                                    semesterStartDate = semStart,
                                    semesterMonths = semMonths,
                                    notifyEnabled = notifyEnabled,
                                    notifyOffsetMinutes = notifyOffset,
                                    colorKey = colorKey
                                ) {
                                    feedbackManager.onSaveSuccess()
                                    showClassEditorModal = false
                                    editingClassSchedule = null
                                }
                            }
                        },
                        onDelete = { toDelete ->
                            viewModel.deleteClassSchedule(toDelete) {
                                feedbackManager.onDeleteAction()
                                showClassEditorModal = false
                                editingClassSchedule = null
                            }
                        }
                    )
                }

                if (showQuickNoteModal) {
                    QuickNoteModal(
                        todayDate = current.todayDate(),
                        initialText = quickNotePrefillText,
                        isSaving = isSaving,
                        onDismiss = {
                            showQuickNoteModal = false
                            quickNotePrefillText = ""
                        },
                        onSaveNote = { rawText, dueIso, priority, category ->
                            viewModel.createQuickNote(
                                rawText = rawText,
                                dueDateIso = dueIso,
                                priority = priority,
                                category = category
                            ) {
                                feedbackManager.onSaveSuccess()
                                showQuickNoteModal = false
                                quickNotePrefillText = ""
                            }
                        }
                    )
                }

                if (showCloudSyncModal && isLocalMode) {
                    BunnyMemoCloudSyncModal(
                        isSigningIn = isSigningInToCloud,
                        errorMessage = cloudSignInError,
                        onSignInWithGoogle = triggerGoogleSignInFromLocal,
                        onDismiss = {
                            if (!isSigningInToCloud) {
                                showCloudSyncModal = false
                                cloudSignInError = null
                            }
                        }
                    )
                }

                if (showSignOutConfirm) {
                    val msg = if (signOutPendingOpsCount > 0) {
                        "You have $signOutPendingOpsCount unsynced change(s) waiting to upload. Signing out clears this device's local planner data, photos, and voice memos so your account stays private."
                    } else {
                        "Signing out clears this device's local planner cache, device-only photos, and voice memos so your account stays private. Your synced cloud memos remain safe."
                    }
                    BunnyMemoConfirmModal(
                        title = "Sign out of Bunny Memo?",
                        message = msg,
                        confirmLabel = "Sign out & clear",
                        cancelLabel = "Stay signed in",
                        isDestructive = true,
                        confirmTestTag = "confirm_sign_out_button",
                        onConfirm = {
                            showSignOutConfirm = false
                            performCleanSignOut()
                        },
                        onDismiss = { showSignOutConfirm = false }
                    )
                }

                if (showDeleteAccountConfirm && !isLocalMode) {
                    BunnyMemoConfirmModal(
                        title = "Delete account & all data?",
                        message = if (isDeletingAccount) {
                            "Deleting your cloud planner data and account…"
                        } else {
                            "This permanently deletes your cloud memos, habits, shared calendar invites, local data, and Firebase account. This cannot be undone."
                        },
                        confirmLabel = if (isDeletingAccount) "Deleting…" else "Delete forever",
                        cancelLabel = "Keep account",
                        isDestructive = true,
                        confirmTestTag = "confirm_delete_account_button",
                        onConfirm = {
                            if (!isDeletingAccount) {
                                isDeletingAccount = true
                                scope.launch {
                                    syncService.stopCloudSync()
                                    ReminderScheduler(context).cancelAll()
                                    DoomscrollGuardManager.setGuardEnabled(context, false)
                                    FocusSessionManager.reset(context)
                                    FocusSessionManager.resetInMemoryForReload()
                                    AppUsageTracker.getInstance(context).resetAllUsage()
                                    val res = deleteAccountAndCloudData(context, credentialManager)
                                    isDeletingAccount = false
                                    showDeleteAccountConfirm = false
                                    res.onSuccess {
                                        WidgetUpdateManager.refreshAllWidgetsSuspend(context)
                                        onSignedOutAndWiped("Your account and all planner data have been deleted.")
                                    }.onFailure { err ->
                                        snackbarHostState.showSnackbar(
                                            err.localizedMessage
                                                ?: "Please sign in again recently before deleting your account."
                                        )
                                    }
                                }
                            }
                        },
                        onDismiss = {
                            if (!isDeletingAccount) showDeleteAccountConfirm = false
                        }
                    )
                }

                invitePrefill?.let { prefill ->
                    InviteFriendToCalendarDialog(
                        initialTitle = prefill.title,
                        initialDateIso = prefill.dateIso,
                        initialTimeLabel = prefill.timeLabel,
                        initialNotes = prefill.notes,
                        initialItemType = prefill.itemType,
                        initialCategory = prefill.category,
                        isSignedIn = !isLocalMode,
                        onDismiss = { invitePrefill = null },
                        onSendInvite = { email, title, plannedDate, timeLabel, notes, sendViaEmailApp ->
                            scope.launch {
                                sharedCalendarRepo.sendCalendarInvite(
                                    inviteeEmailRaw = email,
                                    title = title,
                                    plannedDate = plannedDate,
                                    scheduleTimeLabel = timeLabel,
                                    notes = notes,
                                    itemId = prefill.itemId,
                                    itemType = prefill.itemType,
                                    category = prefill.category
                                ).onSuccess { createdInvite ->
                                    feedbackManager.onSaveSuccess()
                                    invitePrefill = null
                                    if (sendViaEmailApp) {
                                        sharedCalendarRepo.launchEmailInvitation(context, createdInvite)
                                    }
                                    snackbarHostState.showSnackbar("Calendar invite shared with $email.")
                                }.onFailure { err ->
                                    snackbarHostState.showSnackbar(err.localizedMessage ?: "Could not share calendar invite.")
                                }
                            }
                        }
                    )
                }

                liveInspectedInvite?.let { invite ->
                    FriendScheduleDetailModal(
                        invite = invite,
                        replies = inspectedInviteReplies,
                        currentUserId = if (isLocalMode) null else currentUserId,
                        onDismiss = { inspectedSharedInvite = null },
                        onSendReply = { rsvpStatus, message ->
                            scope.launch {
                                sharedCalendarRepo.replyToScheduleInvite(
                                    invite = invite,
                                    rsvpStatus = rsvpStatus,
                                    messageRaw = message
                                ).onSuccess {
                                    feedbackManager.onSaveSuccess()
                                    snackbarHostState.showSnackbar("Reply sent (${rsvpStatus.label}).")
                                }.onFailure { err ->
                                    snackbarHostState.showSnackbar(err.localizedMessage ?: "Could not send reply.")
                                }
                            }
                        },
                        onResendEmail = {
                            feedbackManager.onLightTick()
                            sharedCalendarRepo.launchEmailInvitation(context, invite)
                        },
                        onDeleteInvite = {
                            scope.launch {
                                sharedCalendarRepo.deleteInvite(invite.id)
                                    .onSuccess {
                                        feedbackManager.onDeleteAction()
                                        inspectedSharedInvite = null
                                        snackbarHostState.showSnackbar("Shared calendar invite removed.")
                                    }
                                    .onFailure { err ->
                                        snackbarHostState.showSnackbar(err.localizedMessage ?: "Could not remove invite.")
                                    }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TabIcon(
    tab: PlannerTab,
    selected: Boolean,
    badgeCount: Int
) {
    val icon = when (tab) {
        PlannerTab.TODAY -> if (selected) Icons.Filled.Today else Icons.Outlined.Today
        PlannerTab.CALENDAR -> if (selected) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth
        PlannerTab.CLASSES -> if (selected) Icons.Filled.School else Icons.Outlined.School
        PlannerTab.NOTIFICATIONS -> if (selected) Icons.Filled.Notifications else Icons.Outlined.Notifications
        PlannerTab.REPORTS -> if (selected) Icons.Filled.AutoStories else Icons.Outlined.AutoStories
        PlannerTab.PROFILE -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
    }
    val tint = if (selected) DeepBlue else MutedNavy
    if (badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = OverdueTerracotta,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Text(badgeCount.coerceAtMost(99).toString())
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
        }
    } else {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
    }
}
