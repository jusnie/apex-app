package com.apex.app.testing

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.apex.app.models.CurrentMealSchedule
import com.apex.app.models.UserProfile
import com.apex.app.services.ReviewPrompter
import com.apex.app.services.hosted.AiAccessMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant

/** Reset only before launching an Activity; relaunch/recreation never resets saved state. */
object APEXTestState {
    fun reset(onboarded: Boolean = true) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        requireTestTarget(context)
        val app = context.applicationContext as? APEXTestApplication
            ?: error("APEXTestRunner must create the isolated test Application.")

        runBlocking {
            val prefs = app.container.prefs
            // Use the live DataStore transaction: deleting its file would leave
            // a cached instance and break persistence/relaunch assertions.
            prefs.clearAll()
            prefs.setNotificationsEnabled(false)
            prefs.setHealthConnectEnabled(false)
            prefs.setCloudBackupEnabled(false)
            prefs.setAdaptiveGoalsEnabled(false)
            prefs.setWaterTrackingEnabled(false)
            prefs.setAiAccessMode(AiAccessMode.BYOK)
            if (onboarded) {
                prefs.setUserProfile(UserProfile(
                    name = "Test User",
                    birthday = Instant.parse("2000-01-01T00:00:00Z")
                ))
                prefs.setOnboardingCompleted(true)
            }
            CurrentMealSchedule.value = prefs.mealSchedule.first()
        }
        check(app.getSharedPreferences("apex_workouts", Context.MODE_PRIVATE)
            .edit().clear().commit())
        ReviewPrompter.consumed()
        app.container.analyzingFood.value = false
    }
}
