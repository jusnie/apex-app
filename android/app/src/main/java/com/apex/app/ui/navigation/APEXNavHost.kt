package com.apex.app.ui.navigation

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.apex.app.AppContainer
import com.apex.app.services.update.AndroidUpdateChecker
import com.apex.app.services.update.AndroidUpdateState
import com.apex.app.ui.coach.CoachScreen
import com.apex.app.ui.components.PostUpdatePromptsHost
import com.apex.app.ui.hosted.HostedUiHost
import com.apex.app.ui.home.HomeScreen
import com.apex.app.ui.onboarding.OnboardingScreen
import com.apex.app.ui.progress.BodyMeasurementsScreen
import com.apex.app.ui.progress.ProgressScreen
import com.apex.app.ui.settings.AllergenSensitivitiesScreen
import com.apex.app.ui.settings.CalculationMethodsScreen
import com.apex.app.ui.settings.OptionalNutrientGoalsScreen
import com.apex.app.ui.settings.SettingsScreen
import com.apex.app.ui.settings.SettingsViewModel
import com.apex.app.ui.workouts.WorkoutsScreen
import com.apex.app.models.WorkoutTabMode
import com.apex.app.models.QuickActionRequest
import com.apex.app.ui.settings.AddMenuSettingsScreen
import com.apex.app.ui.settings.ProductInfoSettingsScreen
import com.apex.app.ui.settings.QuickActionsScreen
import com.apex.app.ui.settings.TabBarSettingsScreen
import com.apex.app.ui.voice.VoiceAssistantHost

/**
 * Increments each time the app is opened: 1 on cold launch, then +1 on every
 * return from the background (ON_START after a real ON_STOP). Read by the Home
 * gauge + macro bars to replay their fill-from-zero reveal. It lives above the
 * NavHost, so tab switches (which recompose Home) never change it.
 */
val LocalLaunchFillEpoch = compositionLocalOf { 1 }

private const val WORKOUT_UI_PREFS = "apex_workouts"
private const val WORKOUT_MODE_V2_DEFAULT_KEY = "mode.diary_default.v2"

@Composable
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
fun APEXNavHost(
    container: AppContainer,
    startOnboarding: Boolean,
    quickActionRequest: QuickActionRequest? = null,
    onQuickActionHandled: (Long) -> Unit = {},
    voiceLaunchRequest: Long? = null,
    onVoiceLaunchHandled: (Long) -> Unit = {}
) {
    val nav = rememberNavController()
    // Warm the app-scoped Settings state while Home is visible. By the time the user changes
    // tabs, its local profile/preferences are already ready and the page opens like every other
    // tab instead of constructing empty cards on first entry.
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(container)
    )
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    // Hide the bar while a food analysis is in flight so the AnalyzingOverlay
    // is the only thing on screen — matches iOS, where the analyzing sheet
    // covers the tab bar.
    val context = LocalContext.current
    val analyzing by container.analyzingFood.collectAsState()
    val persistedWorkoutMode by container.workoutRepository.mode.collectAsState(initial = WorkoutTabMode.Default)
    val workoutUiPrefs = remember(context) {
        context.getSharedPreferences(WORKOUT_UI_PREFS, android.content.Context.MODE_PRIVATE)
    }
    var workoutModeV2Initialized by remember(context) {
        mutableStateOf(workoutUiPrefs.getBoolean(WORKOUT_MODE_V2_DEFAULT_KEY, false))
    }
    // Match iOS's versioned AppStorage key: reset the former library-first
    // default once, then keep every user switch persistent after that.
    val workoutMode = if (workoutModeV2Initialized) persistedWorkoutMode else WorkoutTabMode.LOG
    // Nested settings/* screens keep the tab bar visible and highlight Settings,
    // so re-tapping the Settings icon can return to the hub (iOS parity).
    val selectedTabRoute = APEXRoutes.selectedBottomTab(currentRoute)
    // The expanded voice assistant dims the whole app, tab bar included; its compact
    // bubble sits above the bar.
    val voiceUi by container.voiceAssistant.ui.collectAsState()
    // A workout rest can end on any tab; mark it with a haptic while the app is open.
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(container.workoutRestTimer) {
        container.workoutRestTimer.completions.collect {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
    // Null until the stored choice is read, so a hidden tab never flashes in the bar.
    val hiddenTabs by container.prefs.hiddenTabs.collectAsState(initial = null)
    val showTabs = selectedTabRoute != null && hiddenTabs != null && !analyzing &&
        !(voiceUi.visible && voiceUi.expanded)
    // Bumped when the Settings tab is re-tapped so SettingsScreen can pop back to the hub
    // (matches iOS TabView + NavigationStack reselect → root behavior).
    var settingsPopToRootTick by remember { mutableIntStateOf(0) }
    val currentVersion = remember(context) { AndroidUpdateChecker.currentVersion(context) }
    var updateAvailable by remember { mutableStateOf(false) }

    // Settings is intentionally warmed before onboarding finishes. Reload the values that
    // onboarding can change outside Settings so the first visit never shows the pre-onboarding
    // provider/model/API-key snapshot (issue #170). Re-running this on later visits also keeps
    // the app-scoped ViewModel honest if another flow updates the stored AI configuration.
    LaunchedEffect(currentRoute) {
        if (currentRoute == APEXRoutes.SETTINGS) {
            settingsViewModel.refreshAiConfiguration()
        }
    }

    LaunchedEffect(quickActionRequest?.id, currentRoute) {
        if (quickActionRequest != null &&
            currentRoute != APEXRoutes.HOME &&
            currentRoute != APEXRoutes.ONBOARDING
        ) {
            nav.navigate(APEXRoutes.HOME) {
                popUpTo(APEXRoutes.HOME) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(container.workoutRepository, workoutModeV2Initialized) {
        if (!workoutModeV2Initialized) {
            container.workoutRepository.setMode(WorkoutTabMode.LOG)
            workoutUiPrefs.edit().putBoolean(WORKOUT_MODE_V2_DEFAULT_KEY, true).apply()
            workoutModeV2Initialized = true
        }
    }

    LaunchedEffect(currentVersion) {
        if (!container.backgroundServicesEnabled) return@LaunchedEffect
        val state = AndroidUpdateChecker.check(context, currentVersion)
        updateAvailable = state is AndroidUpdateState.Available
        // A newer version is out — fire a one-shot notification (de-duped per version, gated by the
        // "App Updates" toggle) so the user finds out even without opening the About section.
        if (state is AndroidUpdateState.Available &&
            container.prefs.appUpdateNotificationsEnabled.first() &&
            container.notifications.canPostNotifications() &&
            container.prefs.lastNotifiedUpdateVersion.first() != state.latest
        ) {
            container.notifications.showUpdateAvailable()
            container.prefs.setLastNotifiedUpdateVersion(state.latest)
        }
    }

    // App-open epoch for the Home fill-from-zero reveal. Bumped only on ON_START
    // that follows an ON_STOP (a genuine background -> foreground return), so
    // transient pauses (notification shade, permission dialog) don't retrigger it.
    val lifecycleOwner = LocalLifecycleOwner.current
    var launchFillEpoch by remember { mutableIntStateOf(1) }
    var hasStopped by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> hasStopped = true
                Lifecycle.Event.ON_START -> if (hasStopped) { launchFillEpoch++; hasStopped = false }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Shows a tab like a tap on the bar. It reads the live route, so requests from outside
    // composition (the voice assistant) act on what is on screen now.
    fun selectTab(target: String) {
        if (APEXRoutes.isHiddenTab(target, container.navigator.hiddenTabs.value)) return
        val route = nav.current()
        // Re-tapping Settings while already in Settings (hub category or a
        // nested settings/* destination) returns to the Settings hub — same
        // as iOS re-tapping the Settings tab to pop the navigation stack.
        if (target == APEXRoutes.SETTINGS) {
            val onSettingsRoot = route == APEXRoutes.SETTINGS
            val onSettingsChild = route?.startsWith("settings/") == true
            if (onSettingsRoot || onSettingsChild) {
                settingsPopToRootTick++
                if (onSettingsChild) {
                    nav.popBackStack(APEXRoutes.SETTINGS, inclusive = false)
                }
                return
            }
        }
        if (target == APEXRoutes.selectedBottomTab(route)) return
        // Tapping HOME (the start destination) needs popBackStack
        // — `navigate(HOME) { popUpTo(HOME); launchSingleTop = true }`
        // is a no-op because NavController sees HOME at the top of
        // the stack and skips re-emitting currentBackStackEntry, so
        // the bar stays selected on the previous tab.
        if (target == APEXRoutes.HOME) {
            nav.popBackStack(APEXRoutes.HOME, inclusive = false)
        } else {
            nav.navigate(target) {
                popUpTo(APEXRoutes.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // What is on screen, for the voice assistant's get_app_state.
    LaunchedEffect(currentRoute) {
        container.navigator.currentRoute.value = currentRoute
    }

    // A tab hidden in Settings (or by a restored backup) can't stay on screen.
    LaunchedEffect(hiddenTabs, currentRoute) {
        val hidden = hiddenTabs ?: return@LaunchedEffect
        container.navigator.hiddenTabs.value = hidden
        if (APEXRoutes.isHiddenTab(currentRoute, hidden)) {
            nav.popBackStack(APEXRoutes.HOME, inclusive = false)
        }
    }

    // Tabs and Settings sections the voice assistant opens, performed like the user's taps.
    LaunchedEffect(nav) {
        container.navigator.requests.collect { request ->
            val route = nav.current()
            if (route == null || route == APEXRoutes.ONBOARDING) return@collect
            when (request) {
                is NavRequest.Tab -> selectTab(request.route)
                is NavRequest.Settings -> {
                    when {
                        route.startsWith("settings/") -> nav.popBackStack(APEXRoutes.SETTINGS, inclusive = false)
                        route != APEXRoutes.SETTINGS -> nav.navigate(APEXRoutes.SETTINGS) {
                            popUpTo(APEXRoutes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    // A section opens from the Settings screen itself; none means the hub.
                    if (request.section == null) settingsPopToRootTick++
                }
            }
        }
    }

    // The Quick Settings tile, launcher shortcut and widget start a conversation.
    LaunchedEffect(voiceLaunchRequest, currentRoute) {
        val request = voiceLaunchRequest ?: return@LaunchedEffect
        if (currentRoute == null || currentRoute == APEXRoutes.ONBOARDING) return@LaunchedEffect
        container.voiceAssistant.requestStart()
        onVoiceLaunchHandled(request)
    }

    CompositionLocalProvider(LocalLaunchFillEpoch provides launchFillEpoch) {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = androidx.compose.ui.graphics.Color.White,
        bottomBar = {
            AnimatedVisibility(
                visible = showTabs,
                enter = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(
                    animationSpec = tween(180, easing = FastOutSlowInEasing)
                ) { it } + fadeOut(tween(160))
            ) {
                APEXBottomNavBar(
                    currentRoute = selectedTabRoute,
                    showAboutBadge = updateAvailable,
                    workoutMode = workoutMode,
                    hiddenRoutes = hiddenTabs.orEmpty(),
                    onTap = ::selectTab
                )
            }
        }
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            val tabOrder = remember {
                listOf(
                    APEXRoutes.HOME,
                    APEXRoutes.PROGRESS,
                    APEXRoutes.COACH,
                    APEXRoutes.SETTINGS,
                    APEXRoutes.WORKOUTS
                )
            }
            NavHost(
                navController = nav,
                startDestination = if (startOnboarding) APEXRoutes.ONBOARDING else APEXRoutes.HOME,
                enterTransition = {
                    val from = initialState.destination.route.orEmpty()
                    val to = targetState.destination.route.orEmpty()
                    val fromIdx = tabOrder.indexOf(from)
                    val toIdx = tabOrder.indexOf(to)
                    when {
                        to.startsWith("settings/") ->
                            slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 4 } +
                                fadeIn(tween(240))
                        from == APEXRoutes.ONBOARDING ->
                            fadeIn(tween(340, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = 0.96f, animationSpec = tween(340, easing = FastOutSlowInEasing))
                        fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx -> {
                            val dir = if (toIdx > fromIdx) 1 else -1
                            slideInHorizontally(tween(250, easing = FastOutSlowInEasing)) { (it / 10) * dir } +
                                fadeIn(tween(210))
                        }
                        else ->
                            fadeIn(tween(230, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = 0.98f, animationSpec = tween(230, easing = FastOutSlowInEasing))
                    }
                },
                exitTransition = {
                    val from = initialState.destination.route.orEmpty()
                    val to = targetState.destination.route.orEmpty()
                    val fromIdx = tabOrder.indexOf(from)
                    val toIdx = tabOrder.indexOf(to)
                    when {
                        to.startsWith("settings/") ->
                            slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it / 10 } +
                                fadeOut(tween(200))
                        fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx -> {
                            val dir = if (toIdx > fromIdx) -1 else 1
                            slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { (it / 14) * dir } +
                                fadeOut(tween(180))
                        }
                        else ->
                            fadeOut(tween(190, easing = FastOutSlowInEasing))
                    }
                },
                popEnterTransition = {
                    val from = initialState.destination.route.orEmpty()
                    if (from.startsWith("settings/")) {
                        slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it / 10 } +
                            fadeIn(tween(220))
                    } else {
                        fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.98f, animationSpec = tween(220, easing = FastOutSlowInEasing))
                    }
                },
                popExitTransition = {
                    val from = initialState.destination.route.orEmpty()
                    if (from.startsWith("settings/")) {
                        slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 4 } +
                            fadeOut(tween(200))
                    } else {
                        fadeOut(tween(190, easing = FastOutSlowInEasing)) +
                            scaleOut(targetScale = 0.98f, animationSpec = tween(190, easing = FastOutSlowInEasing))
                    }
                }
            ) {
                composable(APEXRoutes.ONBOARDING) {
                    OnboardingScreen(container = container, onComplete = {
                        settingsViewModel.refreshAiConfiguration()
                        nav.navigate(APEXRoutes.HOME) {
                            popUpTo(APEXRoutes.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                    })
                }
                composable(APEXRoutes.HOME) {
                    TabInset {
                        HomeScreen(
                            container = container,
                            quickActionRequest = quickActionRequest,
                            onQuickActionHandled = onQuickActionHandled
                        )
                    }
                }
                composable(APEXRoutes.PROGRESS) { TabInset { ProgressScreen(container = container) } }
                composable(APEXRoutes.COACH) { TabInset { CoachScreen(container = container) } }
                composable(APEXRoutes.SETTINGS) {
                    TabInset {
                        SettingsScreen(
                            container = container,
                            nav = nav,
                            vm = settingsViewModel,
                            popToRootTick = settingsPopToRootTick
                        )
                    }
                }
                composable(APEXRoutes.OPTIONAL_NUTRIENT_GOALS) {
                    OptionalNutrientGoalsScreen(container = container, onBack = { nav.popBackStack() })
                }
                composable(APEXRoutes.CALCULATION_METHODS) {
                    CalculationMethodsScreen(onBack = { nav.popBackStack() })
                }
                composable(APEXRoutes.QUICK_ACTIONS) {
                    QuickActionsScreen(vm = settingsViewModel, onBack = { nav.popBackStack() })
                }
                composable(APEXRoutes.ADD_MENU) {
                    AddMenuSettingsScreen(vm = settingsViewModel, onBack = { nav.popBackStack() })
                }
                composable(APEXRoutes.PRODUCT_INFO) {
                    ProductInfoSettingsScreen(container = container, onBack = { nav.popBackStack() })
                }
                composable(APEXRoutes.TAB_BAR) {
                    TabBarSettingsScreen(
                        vm = settingsViewModel,
                        workoutMode = workoutMode,
                        onBack = { nav.popBackStack() }
                    )
                }
                composable(APEXRoutes.BODY_MEASUREMENTS) {
                    BodyMeasurementsScreen(container = container, onBack = { nav.popBackStack() })
                }
                composable(APEXRoutes.ALLERGEN_SENSITIVITIES) {
                    val settingsUi by settingsViewModel.ui.collectAsState()
                    AllergenSensitivitiesScreen(
                        current = settingsUi.profile?.allergenSensitivities.orEmpty(),
                        onSave = { values -> settingsViewModel.updateProfile { it.copy(allergenSensitivities = values) } },
                        onBack = { nav.popBackStack() },
                        foodAnalysis = container.foodAnalysis
                    )
                }
                composable(APEXRoutes.WORKOUTS) { TabInset { WorkoutsScreen(container = container) } }
            }
            // The live voice assistant over every tab (never during onboarding).
            if (currentRoute != null && currentRoute != APEXRoutes.ONBOARDING) {
                VoiceAssistantHost(container = container)
            }
        }
    }
    }
}

/**
 * Reserves the status-bar space above a tab's content. The top-level Scaffold
 * renders the NavHost full-screen (it discards its inset padding), so each tab
 * would otherwise draw under the status bar. This used to be handled by the ad
 * banner strip that sat above the content; with ads removed, this keeps the
 * exact same clearance. The content Box consumes the status-bar inset so a tab's
 * own Scaffold/TopAppBar doesn't pad for it a second time.
 */
@Composable
private fun TabInset(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) { content() }
    }
}

internal fun NavHostController.current(): String? = currentBackStackEntry?.destination?.route
