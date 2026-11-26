package com.example

import android.graphics.Color as AndroidColor
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.NavigationDrawerItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.example.data.AppUsageSummary
import com.example.data.AppUsageTracker
import com.example.data.CalendarDate
import com.example.data.ClassScheduleItem
import com.example.data.LocalPlannerRepository
import com.example.data.PlannerItem
import com.example.data.RoomFirestoreSyncService
import com.example.data.RoomSyncStatus
import com.example.data.SyncPhase
import com.example.data.onGoogleSignInClicked
import com.example.data.signOut
import com.example.notifications.ReminderScheduler
import com.example.ui.components.BunnyMemoCloudSyncModal
import com.example.ui.components.ClassScheduleEditorModal
import com.example.ui.components.ForgetMeNotFlower
import com.example.ui.components.ForgetMeNotStationeryBackground
import com.example.ui.components.HoppingBunnyLoadingScreen
import com.example.ui.components.ItemEditorDialog
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
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.ForgetMeNotTheme
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.OverdueTerracotta
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyCanvasTop
import com.example.ui.theme.SubtleBorder
import com.example.viewmodel.PlannerUiState
import com.example.viewmodel.PlannerViewModel
import com.example.widgets.WidgetIntents
import com.example.widgets.WidgetUpdateManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            )
        )
        latestDirective = parseLaunchDirective(intent)
        setContent {
            ForgetMeNotTheme {
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
        parseLaunchDirective(intent)?.let { latestDirective = it }
    }

    private fun parseLaunchDirective(intent: Intent?): LaunchDirective? {
        if (intent == null) return null
        val itemId = intent.getStringExtra(ReminderScheduler.EXTRA_ITEM_ID)
        val tab = intent.getStringExtra(WidgetIntents.EXTRA_OPEN_TAB)
        val action = intent.getStringExtra(WidgetIntents.EXTRA_OPEN_ACTION)
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
    REPORTS(R.string.nav_reports)
}

private enum class AppRootStage { SPLASH, AUTH, PLANNER }

@Composable
fun AppContent(
    launchDirective: LaunchDirective? = null,
    onDirectiveConsumed: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val usageTracker = remember(context) { AppUsageTracker.getInstance(context) }
    val usageSummary by usageTracker.usageSummary.collectAsStateWithLifecycle()
    val syncService = remember(context) { RoomFirestoreSyncService.getInstance(context) }
    val syncStatus by syncService.syncStatus.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner, usageTracker) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            usageTracker.onForegroundResume()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> usageTracker.onForegroundResume()
                Lifecycle.Event.ON_PAUSE -> usageTracker.onForegroundPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            usageTracker.onForegroundPause()
        }
    }

    val modePrefs = remember(context) {
        context.applicationContext.getSharedPreferences(MODE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var useLocalMode by remember {
        mutableStateOf(modePrefs.getBoolean(KEY_LOCAL_MODE_ENABLED, false))
    }
    var showLaunchSplash by rememberSaveable { mutableStateOf(launchDirective == null) }
    var migrationBanner by remember { mutableStateOf<String?>(null) }
    val reducedMotion = rememberReducedMotion()

    LaunchedEffect(Unit) {
        if (showLaunchSplash) {
            delay(1250L)
            showLaunchSplash = false
        }
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
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
        showLaunchSplash -> AppRootStage.SPLASH
        currentUser == null && !useLocalMode -> AppRootStage.AUTH
        else -> AppRootStage.PLANNER
    }

    AnimatedContent(
        targetState = stage,
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(tween(180)) togetherWith fadeOut(tween(140))
            } else {
                (fadeIn(tween(380, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = 0.985f, animationSpec = tween(380, easing = FastOutSlowInEasing))) togetherWith
                    fadeOut(tween(260, easing = FastOutSlowInEasing))
            }
        },
        label = "app_root_transition"
    ) { targetStage ->
        when (targetStage) {
            AppRootStage.SPLASH -> HoppingBunnyLoadingScreen()

            AppRootStage.AUTH -> AuthScreen(
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
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

                // Key by sessionKey so transitioning between local guest mode and signed-in cloud sync refreshes ViewModel
                key(sessionKey) {
                    PlannerWorkspaceShell(
                        isLocalMode = isLocalMode,
                        currentUserId = sessionKey,
                        userEmail = user?.email,
                        usageSummary = usageSummary,
                        syncStatus = syncStatus,
                        onRequestManualSync = { syncService.requestSync(restoredFromOffline = false) },
                        launchDirective = launchDirective,
                        onDirectiveConsumed = onDirectiveConsumed,
                        externalBannerMessage = migrationBanner,
                        onClearExternalBanner = { migrationBanner = null },
                        onSignedInFromLocal = {
                            currentUser = Firebase.auth.currentUser
                        },
                        onSwitchToLocalAfterSignOut = {
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
    userEmail: String?,
    usageSummary: AppUsageSummary,
    syncStatus: RoomSyncStatus,
    onRequestManualSync: () -> Unit,
    launchDirective: LaunchDirective?,
    onDirectiveConsumed: (Long) -> Unit,
    externalBannerMessage: String?,
    onClearExternalBanner: () -> Unit,
    onSignedInFromLocal: () -> Unit,
    onSwitchToLocalAfterSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: PlannerViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                // Offline-first Room repository wired to RoomFirestoreSyncService:
                // all writes persist immediately to Room and push automatically to Firestore when online
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
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }
    LaunchedEffect(externalBannerMessage) {
        externalBannerMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearExternalBanner()
        }
    }

    // Notification runtime permission (Android 13+)
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }
    val requestNotifPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
    var showSettingsDialog by remember { mutableStateOf(false) }
    var forceShowDedication by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showQuickNoteModal by remember { mutableStateOf(false) }
    var quickNotePrefillText by remember { mutableStateOf("") }

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
    var showCloudSyncModal by remember { mutableStateOf(false) }
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
                (fadeIn(tween(340, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(340, easing = FastOutSlowInEasing)) { it / 28 }) togetherWith
                    fadeOut(tween(220, easing = FastOutSlowInEasing))
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
                val errMessage = (uiState as? PlannerUiState.Error)?.message ?: "Unable to sync right now."
                ForgetMeNotStationeryBackground {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = PureWhite,
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier.widthIn(max = 420.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            }
                        }
                    }
                }
            }

            PlannerShellStage.DEDICATION -> {
                val ready = uiState as? PlannerUiState.Ready ?: return@AnimatedContent
                DedicationScreen(
                    initialDedication = ready.profile.dedicationText,
                    isSaving = isSaving,
                    onOpenPlanner = { editedText ->
                        viewModel.saveDedication(editedText, markSeen = true)
                        forceShowDedication = false
                    }
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

                ForgetMeNotStationeryBackground {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val supportsSideDetailPane = maxWidth >= 720.dp
                        val detailPaneWidth = adaptiveDetailPaneWidth(maxWidth)
                        val showingCompactDetail = !supportsSideDetailPane && selectedItem != null

                        AnimatedContent(
                            targetState = showingCompactDetail,
                            transitionSpec = {
                                if (reducedMotion) {
                                    fadeIn(tween(140)) togetherWith fadeOut(tween(110))
                                } else if (targetState) {
                                    (slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 16 } +
                                        fadeIn(tween(200, easing = FastOutSlowInEasing))) togetherWith
                                        fadeOut(tween(150, easing = FastOutSlowInEasing))
                                } else {
                                    fadeIn(tween(200, easing = FastOutSlowInEasing)) togetherWith
                                        (slideOutVertically(tween(190, easing = FastOutSlowInEasing)) { it / 16 } +
                                            fadeOut(tween(150, easing = FastOutSlowInEasing)))
                                }
                            },
                            label = "compact_detail_transition"
                        ) { isCompactDetail ->
                            if (isCompactDetail && selectedItem != null) {
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
                                        onEditItem = { itemToEdit ->
                                            editingItem = itemToEdit
                                            editorPrefillDate = itemToEdit.primaryCalendarDate() ?: current.todayDate()
                                            showEditorDialog = true
                                        },
                                        onToggleItemCompleted = { item, checked ->
                                            viewModel.toggleItemCompleted(item, checked)
                                        },
                                        onDeleteItem = { item ->
                                            viewModel.deleteItem(item) { selectedItemId = null }
                                        },
                                        onAddStep = { itemId, title, due ->
                                            viewModel.addStep(itemId, title, due, null)
                                        },
                                        onToggleStep = { step, checked ->
                                            viewModel.toggleUnifiedStep(step, checked)
                                        },
                                        onDeleteStep = { step -> viewModel.deleteStep(step) },
                                        onAddReminder = { item, kind, offset ->
                                            viewModel.addReminder(
                                                item = item,
                                                kind = kind,
                                                offsetMinutes = offset,
                                                localHour = current.profile.defaultReminderHour,
                                                localMinute = current.profile.defaultReminderMinute
                                            )
                                        },
                                        onDeleteReminder = { rem -> viewModel.deleteReminder(rem) },
                                        onDismissCelebration = { viewModel.clearCelebration() },
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }
                            } else {
                                val drawerState = rememberDrawerState(DrawerValue.Closed)
                                BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
                                var createMenuOpen by remember { mutableStateOf(false) }
                                ModalNavigationDrawer(
                                    drawerState = drawerState,
                                    gesturesEnabled = drawerState.isOpen,
                                    drawerContent = {
                                        ModalDrawerSheet(drawerContainerColor = PureWhite) {
                                            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    ForgetMeNotFlower(bloomed = true, size = 32.dp)
                                                    Text("Bunny Memo", style = MaterialTheme.typography.headlineSmall, color = MidnightNavy)
                                                }
                                                PlannerTab.entries.forEach { tab ->
                                                    NavigationDrawerItem(
                                                        label = { Text(stringResource(tab.labelRes)) },
                                                        selected = activeTab == tab,
                                                        icon = { TabIcon(tab, activeTab == tab, if (tab == PlannerTab.NOTIFICATIONS) dueInboxCount else 0) },
                                                        onClick = {
                                                            activeTab = tab
                                                            selectedItemId = null
                                                            searchQuery = ""
                                                            searchOpen = false
                                                            scope.launch { drawerState.close() }
                                                        }
                                                    )
                                                }
                                                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                                                DropdownMenuItem(
                                                    text = { Text(if (isLocalMode) "Back up memos" else "Sync now") },
                                                    leadingIcon = { Icon(if (isLocalMode) Icons.Outlined.CloudUpload else Icons.Outlined.CloudSync, contentDescription = null, tint = DeepBlue) },
                                                    onClick = {
                                                        scope.launch { drawerState.close() }
                                                        if (isLocalMode) {
                                                            cloudSignInError = null
                                                            showCloudSyncModal = true
                                                        } else onRequestManualSync()
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Dedication") },
                                                    leadingIcon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = DeepBlue) },
                                                    onClick = {
                                                        scope.launch { drawerState.close() }
                                                        forceShowDedication = true
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Settings") },
                                                    leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null, tint = DeepBlue) },
                                                    onClick = {
                                                        scope.launch { drawerState.close() }
                                                        showSettingsDialog = true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Scaffold(
                                        containerColor = Color.Transparent,
                                        contentWindowInsets = WindowInsets.safeDrawing,
                                        snackbarHost = { SnackbarHost(snackbarHostState) },
                                        topBar = {
                                            Column {
                                                TopAppBar(
                                                    colors = TopAppBarDefaults.topAppBarColors(
                                                        containerColor = SkyCanvasTop.copy(alpha = 0.86f),
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
                                                                    color = MidnightNavy
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
                                                                    modifier = Modifier.testTag("top_bar_sync_status_text")
                                                                )
                                                            }
                                                        }
                                                    },
                                                    navigationIcon = {
                                                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                                            Icon(Icons.Default.Menu, contentDescription = "Open planner navigation")
                                                        }
                                                    },
                                                    actions = {
                                                        IconButton(onClick = {
                                                            searchOpen = !searchOpen
                                                            if (!searchOpen) searchQuery = ""
                                                        }) {
                                                            Icon(
                                                                if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                                                                contentDescription = if (searchOpen) "Close search" else "Search planner"
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
                                        floatingActionButton = {
                                            Box {
                                                ExtendedFloatingActionButton(
                                                    onClick = { createMenuOpen = true },
                                                    containerColor = DeepBlue,
                                                    contentColor = PureWhite,
                                                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                                                    text = { Text("Create") }
                                                )
                                                DropdownMenu(expanded = createMenuOpen, onDismissRequest = { createMenuOpen = false }) {
                                                    DropdownMenuItem(
                                                        text = { Text(if (activeTab == PlannerTab.CLASSES) "Add class" else "Add planner item") },
                                                        onClick = {
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
                                                            createMenuOpen = false
                                                            quickNotePrefillText = ""
                                                            showQuickNoteModal = true
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    ) { innerPadding ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(innerPadding)
                                        ) {

                                            BackHandler(enabled = searchOpen && drawerState.isClosed) {
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
                                                            usageSummary = usageSummary,
                                                            selectedItemId = selectedItemId,
                                                            onSelectItem = { id -> selectedItemId = id },
                                                            onToggleTask = { task, checked ->
                                                                viewModel.toggleItemCompleted(task, checked)
                                                            },
                                                            onToggleStep = { step, checked ->
                                                                viewModel.toggleUnifiedStep(step, checked)
                                                            },
                                                            onAddClick = { date ->
                                                                editingItem = null
                                                                editorPrefillDate = date
                                                                showEditorDialog = true
                                                            },
                                                            onAddSamplePlan = {
                                                                viewModel.seedSampleScrapbookPlan { newId ->
                                                                    selectedItemId = newId
                                                                }
                                                            },
                                                            onEditClass = { cls ->
                                                                editingClassSchedule = cls
                                                                classPrefillDayOfWeek = null
                                                                showClassEditorModal = true
                                                            },
                                                            onOpenReports = {
                                                                activeTab = PlannerTab.REPORTS
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
                                                                viewModel.setDailyDigestEnabled(enabled)
                                                            },
                                                            onUpdateDailyDigestTime = { h, m ->
                                                                viewModel.updateDailyDigestTime(h, m)
                                                            },
                                                            onSendDailyDigestPreview = {
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
                                                            }
                                                        )

                                                        PlannerTab.REPORTS -> ReportsScreen(
                                                            state = current,
                                                            usageSummary = usageSummary,
                                                            onSelectItem = { id -> selectedItemId = id }
                                                        )
                                                    }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Tablet/expanded detail side panel (fixed adaptive width, zero weight oscillation)
                                            AnimatedVisibility(
                                                visible = supportsSideDetailPane && selectedItem != null,
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
                                                                onEditItem = { itemToEdit ->
                                                                    editingItem = itemToEdit
                                                                    editorPrefillDate = itemToEdit.primaryCalendarDate() ?: current.todayDate()
                                                                    showEditorDialog = true
                                                                },
                                                                onToggleItemCompleted = { item, checked ->
                                                                    viewModel.toggleItemCompleted(item, checked)
                                                                },
                                                                onDeleteItem = { item ->
                                                                    viewModel.deleteItem(item) { selectedItemId = null }
                                                                },
                                                                onAddStep = { itemId, title, due ->
                                                                    viewModel.addStep(itemId, title, due, null)
                                                                },
                                                                onToggleStep = { step, checked ->
                                                                    viewModel.toggleUnifiedStep(step, checked)
                                                                },
                                                                onDeleteStep = { step -> viewModel.deleteStep(step) },
                                                                onAddReminder = { item, kind, offset ->
                                                                    viewModel.addReminder(
                                                                        item = item,
                                                                        kind = kind,
                                                                        offsetMinutes = offset,
                                                                        localHour = current.profile.defaultReminderHour,
                                                                        localMinute = current.profile.defaultReminderMinute
                                                                    )
                                                                },
                                                                onDeleteReminder = { rem -> viewModel.deleteReminder(rem) },
                                                                onDismissCelebration = { viewModel.clearCelebration() }
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
                    }
                }

                if (showEditorDialog) {
                    val plansForLinking = current.items.filter { it.itemType().name == "PLAN" }
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
                        onSaveNew = { type, title, notes, startDate, dueDate, dueTime, startUtc, endUtc, parentPlanId, steps, reminders ->
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
                                reminders = reminders
                            ) { createdId ->
                                showEditorDialog = false
                                selectedItemId = createdId
                            }
                        },
                        onSaveEdit = { item, type, title, notes, startDate, dueDate, dueTime, startUtc, endUtc, parentPlanId, reminders ->
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
                                updatedReminders = reminders
                            ) {
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
                        onAddSamplePlan = {
                            viewModel.seedSampleScrapbookPlan { newId -> selectedItemId = newId }
                        },
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
                        onSignInWithGoogle = {
                            cloudSignInError = null
                            showCloudSyncModal = true
                        },
                        onSignOut = {
                            onSwitchToLocalAfterSignOut()
                            signOut(credentialManager = credentialManager, scope = scope)
                        }
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
                                    showClassEditorModal = false
                                    editingClassSchedule = null
                                }
                            }
                        },
                        onDelete = { toDelete ->
                            viewModel.deleteClassSchedule(toDelete) {
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
                        onSaveNote = { rawText, dueIso ->
                            viewModel.createQuickNote(
                                rawText = rawText,
                                dueDateIso = dueIso
                            ) {
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
    }
    if (badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge(containerColor = DeepBlue, contentColor = PureWhite) {
                    Text(badgeCount.coerceAtMost(99).toString())
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = null)
        }
    } else {
        Icon(imageVector = icon, contentDescription = null)
    }
}
