package com.apex.app.ui.onboarding

import com.apex.app.services.hosted.AiAccessMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateTest {

    @Test
    fun onboardingDoesNotContainProviderOrSubscriptionStep() {
        assertFalse(OnboardingStep.entries.map { it.name }.contains("PROVIDER"))
        assertEquals(OnboardingStep.BUILDING_PLAN, OnboardingStep.entries[OnboardingStep.HEALTH_CONNECT.ordinal + 1])
    }

    @Test
    fun allStepsAdvanceWithoutUserApiKeyOrSubscription() {
        for (step in OnboardingStep.entries) {
            val state = OnboardingState(step = step, apiKey = "", hostedEntitled = false)
            assertTrue("Expected step $step to advance without user API key", state.canAdvance)
            assertEquals(AiAccessMode.BYOK, state.accessMode)
        }
    }
}
