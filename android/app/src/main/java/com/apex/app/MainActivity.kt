package com.apex.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.apex.app.models.FoodEntry
import com.apex.app.models.QuickActionRequest
import com.apex.app.services.MealShare
import com.apex.app.services.QuickActionShortcutManager
import com.apex.app.services.ReviewPrompter
import com.apex.app.services.update.AndroidUpdateChecker
import com.apex.app.services.voice.VoiceEntryPoints
import com.apex.app.ui.components.APEXAnimatedBackground
import com.apex.app.ui.components.LocalEnergyUnit
import com.apex.app.ui.home.ImportSharedMealSheet
import com.apex.app.ui.navigation.APEXNavHost
import com.apex.app.ui.theme.AppThemeColor
import com.apex.app.ui.theme.APEXTheme
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// AppCompatActivity so AppCompatDelegate.setApplicationLocales applies on API 26–32 (#372).
open class MainActivity : AppCompatActivity() {
    // Shared-meal deep link (issue #107). Non-empty -> the confirm sheet is shown over the app.
    private var pendingSharedMeals by mutableStateOf<List<FoodEntry>>(emptyList())
    private var pendingQuickAction by mutableStateOf<QuickActionRequest?>(null)
    // Tile, launcher shortcut or widget: open straight into a voice conversation.
    private var pendingVoiceLaunch by mutableStateOf<Long?>(null)

    /** Decode a `apex://add-meal` link (if that's what launched us) into pending meals. */
    private fun handleShareIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (!MealShare.handles(uri)) return
        MealShare.meals(uri)?.let { pendingSharedMeals = it }
    }

    private fun handleQuickActionIntent(intent: Intent?) {
        val action = QuickActionShortcutManager.actionFrom(intent) ?: return
        pendingQuickAction = QuickActionRequest(action)
        intent?.action = null
    }

    private fun handleVoiceIntent(intent: Intent?) {
        if (!VoiceEntryPoints.isVoiceLaunch(intent)) return
        pendingVoiceLaunch = System.nanoTime()
        VoiceEntryPoints.consume(intent)
    }

    private fun handlePlayStoreIntent(intent: Intent?) {
        if (intent?.action != AndroidUpdateChecker.ACTION_OPEN_PLAY_STORE) return
        AndroidUpdateChecker.openPlayStore(this)
        intent.action = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
        handleQuickActionIntent(intent)
        handlePlayStoreIntent(intent)
        handleVoiceIntent(intent)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        pendingVoiceLaunch?.let { outState.putLong("pendingVoiceLaunch", it) }
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        // A rest that ends while the app is visible is announced in-app, not by notification.
        (application as APEXApp).container.workoutRestTimer.setAppInForeground(true)
        if (!(application as APEXApp).backgroundServicesEnabled) return
        lifecycleScope.launch {
            // Adaptive Goals auto-runs the full goal calculation about once a week (Energy Burn,
            // when on, supplies the measured-burn anchor it consumes — separate toggle).
            val container = (application as APEXApp).container
            // Keep the Hosted AI plan current (renewals, cancellations, pending purchases)
            // before Adaptive Goals, which may run through Hosted AI. The quota refresh
            // runs alongside so it does not hold up the work below.
            if (container.billing.refreshCustomerInfo() && container.billing.entitlement.value.isEntitled) {
                launch { container.hostedQuota.refresh() }
            }
            container.refreshAdaptiveGoalsIfNeeded()
            // Pull any new external weight / body-fat readings (e.g. a Withings scale)
            // from Health Connect into the app on every foreground (issue #91).
            container.syncHealthConnectReads()
        }
    }

    override fun onStop() {
        super.onStop()
        (application as APEXApp).container.workoutRestTimer.setAppInForeground(false)
        if (!(application as APEXApp).backgroundServicesEnabled) return
        lifecycleScope.launch {
            runCatching { (application as APEXApp).container.cloudBackup.autoBackupIfNeeded() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the system swaps the splash theme
        // back to Theme.APEX before the first frame, preventing a white flash
        // on cold start. The splash uses a transparent foreground mark over
        // the app's light/dark splash background.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        // Launch the Play in-app review card once, right after the first
        // successful food log (see ReviewPrompter). Silently no-ops on devices
        // without Play services or when Play declines to show the card.
        lifecycleScope.launch {
            if (!(application as APEXApp).backgroundServicesEnabled) return@launch
            ReviewPrompter.requestReview.collect { wanted ->
                if (!wanted) return@collect
                ReviewPrompter.consumed()
                delay(1_500)
                runCatching {
                    val manager = ReviewManagerFactory.create(this@MainActivity)
                    val info = manager.requestReview()
                    manager.launchReview(this@MainActivity, info)
                }
            }
        }

        // Support --reset-onboarding launch flag (parallel to iOS CLAUDE.md convention).
        if (intent?.getBooleanExtra("reset_onboarding", false) == true) {
            runBlocking { (application as APEXApp).container.prefs.setOnboardingCompleted(false) }
            intent.removeExtra("reset_onboarding")
        }

        val container = (application as APEXApp).container
        // A apex://add-meal link may have cold-launched us.
        handleShareIntent(intent)
        handleQuickActionIntent(intent)
        handlePlayStoreIntent(intent)
        // Preserve an external request while onboarding or activity recreation delays it.
        if (savedInstanceState?.containsKey("pendingVoiceLaunch") == true) {
            pendingVoiceLaunch = savedInstanceState.getLong("pendingVoiceLaunch")
        } else if (savedInstanceState == null) handleVoiceIntent(intent)

        lifecycleScope.launch {
            combine(
                container.prefs.quickAction1,
                container.prefs.quickAction2,
                container.prefs.quickAction3
            ) { first, second, third -> listOf(first, second, third) }
                .collect { QuickActionShortcutManager.update(this@MainActivity, it) }
        }

        val startOnboarding = runBlocking { !container.prefs.hasCompletedOnboarding.first() }
        val initialAppearance = runBlocking { container.prefs.appearanceMode.first() }
        val initialThemeColorKey = runBlocking { container.prefs.appThemeColor.first() }
        val initialEnergyUnit = runBlocking { container.prefs.energyUnit.first() }

        // Hold the splash on screen until the saved profile has loaded from
        // DataStore so Home doesn't briefly render its 2000/150/220/70 fallback
        // goal numbers before snapping to the user's real targets. Onboarding
        // doesn't show those numbers, so we let the splash dismiss immediately
        // in that case.
        var contentReady = startOnboarding
        splashScreen.setKeepOnScreenCondition { !contentReady }
        if (!startOnboarding) {
            lifecycleScope.launch {
                container.profileRepository.profile.first { it != null }
                contentReady = true
            }
        }

        setContent {
            val energyUnit by container.prefs.energyUnit.collectAsState(initial = initialEnergyUnit)
            APEXTheme(darkTheme = true, themeColor = AppThemeColor.RED) {
                // Above the NavHost and the shared-meal sheet so both show the chosen unit.
                CompositionLocalProvider(LocalEnergyUnit provides energyUnit) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        APEXAnimatedBackground(modifier = Modifier.fillMaxSize())
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.Transparent,
                            contentColor = Color.White
                        ) {
                            APEXNavHost(
                                container = container,
                                startOnboarding = startOnboarding,
                                quickActionRequest = pendingQuickAction,
                                onQuickActionHandled = { requestID ->
                                    if (pendingQuickAction?.id == requestID) pendingQuickAction = null
                                },
                                voiceLaunchRequest = pendingVoiceLaunch,
                                onVoiceLaunchHandled = { requestID ->
                                    if (pendingVoiceLaunch == requestID) pendingVoiceLaunch = null
                                }
                            )

                            if (pendingSharedMeals.isNotEmpty()) {
                                ImportSharedMealSheet(
                                    meals = pendingSharedMeals,
                                    onAdd = { meals ->
                                        lifecycleScope.launch {
                                            for (meal in meals) {
                                                if (!container.foodRepository.addEntry(meal)) {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        getString(R.string.food_blocked_by_active_fast),
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    break
                                                }
                                            }
                                        }
                                        pendingSharedMeals = emptyList()
                                    },
                                    onDismiss = { pendingSharedMeals = emptyList() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
