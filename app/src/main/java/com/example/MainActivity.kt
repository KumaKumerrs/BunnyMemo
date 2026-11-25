package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.example.data.LocalPlannerDatabase
import com.example.data.LocalPlannerRepository
import com.example.data.PlannerDataSource
import com.example.data.PlannerItem
import com.example.data.PlannerRepository
import com.example.data.onGoogleSignInClicked
import com.example.data.signOut
import com.example.notifications.ReminderScheduler
import com.example.ui.components.BunnyMemoCloudSyncModal
import com.example.ui.components.ClassScheduleEditorModal
import com.example.ui.components.ForgetMeNotFlower
import com.example.ui.components.ForgetMeNotStationeryBackground
import com.example.ui.components.HoppingBunnyLoadingScreen
import com.example.ui.components.ItemEditorDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.components.adaptiveDetailPaneWidth
import com.example.ui.components.rememberReducedMotion
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.ClassScheduleScreen
import com.example.ui.screens.DedicationScreen
import com.example.ui.screens.ItemDetailScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.theme.CloudWhite
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.ForgetMeNotTheme
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.OverdueTerracotta
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SkyCanvasTop
import com.example.ui.theme.SubtleBorder
import com.example.viewmodel.PlannerUiState
import com.example.viewmodel.PlannerViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import android.graphics.Color as AndroidColor
import androidx.activity.SystemBarStyle

private const val MODE_PREFS_NAME = "bunny_memo_mode_prefs"
private const val KEY_LOCAL_MODE_ENABLED = "local_mode_enabled"

class MainActivity : ComponentActivity() {
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
        val initialDeepLinkItemId = intent?.getStringExtra(ReminderScheduler.EXTRA_ITEM_ID)
        setContent {
            ForgetMeNotTheme {
                AppContent(initialDeepLinkItemId = initialDeepLinkItemId)
            }
        }
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
fun AppContent(initialDeepLinkItemId: String? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val usageTracker = remember(context) { AppUsageTracker.getInstance(context) }
    val usageSummary by usageTracker.usageSummary.collectAsStateWithLifecycle()

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
    var showLaunchSplash by rememberSaveable { mutableStateOf(true) }
    var migrationBanner by remember { mutableStateOf<String?>(null) }
    val reducedMotion = rememberReducedMotion()

    LaunchedEffect(Unit) {
        delay(1250L)
        showLaunchSplash = false
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    // Automatically migrate any local memos to Firestore when user signs in with Google
    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid ?: return@LaunchedEffect
        val localRepo = LocalPlannerRepository(
            LocalPlannerDatabase.getInstance(context).plannerDao()
        )
        val snapshot = localRepo.readMigrationSnapshot()
        if (snapshot.hasUserContent) {
            val dbId = context.getString(R.string.firestore_database_id)
            val cloudRepo = PlannerRepository(FirebaseFirestore.getInstance(dbId))
            cloudRepo.importLocalDataToCloud(uid, snapshot)
                .onSuccess { importedCount ->
                    localRepo.clearAllAfterMigration()
                    migrationBanner = if (importedCount > 0) {
                        "Signed in! Synced $importedCount local memo(s) to your private cloud."
                    } else {
                        "Signed in! Your private cloud sync is now active."
                    }
                }
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
                val sessionKey = if (isLocalMode) LocalPlannerRepository.LOCAL_USER_ID else user!!.uid

                // Key by sessionKey so transitioning between local Room storage and signed-in Firestore cleanly swaps ViewModel
                key(sessionKey) {
                    PlannerWorkspaceShell(
                        isLocalMode = isLocalMode,
                        currentUserId = sessionKey,
                        userEmail = user?.email,
                        usageSummary = usageSummary,
                        initialDeepLinkItemId = initialDeepLinkItemId,
                        externalBannerMessage = migrationBanner,
                        onClearExternalBanner = { migrationBanner = null },
                        onSignedInFromLocal = {
                            currentUser = Firebase.auth.currentUser
                        },
                        onSwitchToLocalAfterSignOut = {
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
    initialDeepLinkItemId: String?,
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
                val dataSource: PlannerDataSource = if (isLocalMode) {
                    LocalPlannerRepository(LocalPlannerDatabase.getInstance(app).plannerDao())
                } else {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    PlannerRepository(FirebaseFirestore.getInstance(databaseId))
                }
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
    var selectedItemId by rememberSaveable { mutableStateOf(initialDeepLinkItemId) }

    var showEditorDialog by remember { mutableStateOf(false) }
    var editorPrefillDate by remember { mutableStateOf(CalendarDate.today()) }
    var editingItem by remember { mutableStateOf<PlannerItem?>(null) }
    var showClassEditorModal by remember { mutableStateOf(false) }
    var editingClassSchedule by remember { mutableStateOf<ClassScheduleItem?>(null) }
    var classPrefillDayOfWeek by remember { mutableStateOf<Int?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var forceShowDedication by remember { mutableStateOf(false) }

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
                        val useNavigationRail = maxWidth >= 600.dp
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
                                                                text = "Bunny Memo",
                                                                style = MaterialTheme.typography.titleLarge,
                                                                color = MidnightNavy
                                                            )
                                                            if (isLocalMode) {
                                                                Text(
                                                                    text = stringResource(R.string.local_mode_badge),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                    }
                                                },
                                                actions = {
                                                    if (isLocalMode) {
                                                        IconButton(
                                                            onClick = {
                                                                cloudSignInError = null
                                                                showCloudSyncModal = true
                                                            },
                                                            modifier = Modifier.testTag("top_bar_cloud_backup_button")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.CloudUpload,
                                                                contentDescription = "Sign in with Google to back up memos",
                                                                tint = DeepBlue
                                                            )
                                                        }
                                                    }
                                                    IconButton(
                                                        onClick = { forceShowDedication = true },
                                                        modifier = Modifier.testTag("top_bar_dedication_button")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.FavoriteBorder,
                                                            contentDescription = "View dedication note",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { showSettingsDialog = true },
                                                        modifier = Modifier.testTag("top_bar_settings_button")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.Settings,
                                                            contentDescription = "Planner settings",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                                        if (!useNavigationRail) {
                                            Column {
                                                HorizontalDivider(
                                                    thickness = 0.75.dp,
                                                    color = SubtleBorder.copy(alpha = 0.9f)
                                                )
                                                NavigationBar(
                                                    containerColor = PureWhite,
                                                    windowInsets = WindowInsets.navigationBars,
                                                    tonalElevation = 0.dp
                                                ) {
                                                    PlannerTab.entries.forEach { tab ->
                                                        val selected = activeTab == tab
                                                        NavigationBarItem(
                                                            selected = selected,
                                                            onClick = { activeTab = tab },
                                                            icon = {
                                                                TabIcon(
                                                                    tab = tab,
                                                                    selected = selected,
                                                                    badgeCount = if (tab == PlannerTab.NOTIFICATIONS) dueInboxCount else 0
                                                                )
                                                            },
                                                            label = { Text(stringResource(tab.labelRes)) },
                                                            colors = NavigationBarItemDefaults.colors(
                                                                selectedIconColor = DeepBlue,
                                                                selectedTextColor = MidnightNavy,
                                                                indicatorColor = SkyBlue.copy(alpha = 0.65f),
                                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                            ),
                                                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    floatingActionButton = {
                                        ExtendedFloatingActionButton(
                                            onClick = {
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
                                            },
                                            containerColor = DeepBlue,
                                            contentColor = PureWhite,
                                            shape = RoundedCornerShape(100.dp),
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = null
                                                )
                                            },
                                            text = { Text(if (activeTab == PlannerTab.CLASSES) "Class" else "Add") },
                                            modifier = Modifier.testTag("fab_add_item")
                                        )
                                    }
                                ) { innerPadding ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding)
                                    ) {
                                        if (useNavigationRail) {
                                            NavigationRail(
                                                containerColor = PureWhite,
                                                modifier = Modifier.fillMaxHeight()
                                            ) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                PlannerTab.entries.forEach { tab ->
                                                    val selected = activeTab == tab
                                                    NavigationRailItem(
                                                        selected = selected,
                                                        onClick = { activeTab = tab },
                                                        icon = {
                                                            TabIcon(
                                                                tab = tab,
                                                                selected = selected,
                                                                badgeCount = if (tab == PlannerTab.NOTIFICATIONS) dueInboxCount else 0
                                                            )
                                                        },
                                                        label = { Text(stringResource(tab.labelRes)) },
                                                        colors = NavigationRailItemDefaults.colors(
                                                            selectedIconColor = DeepBlue,
                                                            selectedTextColor = MidnightNavy,
                                                            indicatorColor = SkyBlue.copy(alpha = 0.65f),
                                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                        ),
                                                        modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                                                    )
                                                }
                                            }
                                            VerticalDivider(color = SubtleBorder)
                                        }

                                        // Primary pane with crisp, lag-free directional section transitions
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        ) {
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
