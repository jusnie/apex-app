package com.apex.app.ui.navigation

import com.apex.app.models.TabBarSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class APEXRoutesTest {
    @Test
    fun selectedBottomTabMapsNestedSettingsToSettings() {
        assertEquals(APEXRoutes.SETTINGS, APEXRoutes.selectedBottomTab(APEXRoutes.SETTINGS))
        assertEquals(
            APEXRoutes.SETTINGS,
            APEXRoutes.selectedBottomTab(APEXRoutes.QUICK_ACTIONS)
        )
        assertEquals(
            APEXRoutes.SETTINGS,
            APEXRoutes.selectedBottomTab(APEXRoutes.OPTIONAL_NUTRIENT_GOALS)
        )
        assertEquals(APEXRoutes.HOME, APEXRoutes.selectedBottomTab(APEXRoutes.HOME))
        assertNull(APEXRoutes.selectedBottomTab(APEXRoutes.ONBOARDING))
        assertNull(APEXRoutes.selectedBottomTab(null))
        assertEquals(APEXRoutes.SETTINGS, APEXRoutes.selectedBottomTab(APEXRoutes.TAB_BAR))
    }

    @Test
    fun hiddenTabsLeaveHomeAndSettingsInTheBar() {
        assertEquals(APEXRoutes.bottomTabs, APEXRoutes.visibleBottomTabs(emptySet()))
        assertEquals(
            listOf(APEXRoutes.HOME, APEXRoutes.PROGRESS, APEXRoutes.SETTINGS),
            APEXRoutes.visibleBottomTabs(setOf(APEXRoutes.COACH, APEXRoutes.WORKOUTS))
        )
        val everything = APEXRoutes.bottomTabs.toSet()
        assertEquals(listOf(APEXRoutes.HOME, APEXRoutes.SETTINGS), APEXRoutes.visibleBottomTabs(everything))
        assertFalse(APEXRoutes.isHiddenTab(APEXRoutes.HOME, everything))
        assertFalse(APEXRoutes.isHiddenTab(APEXRoutes.SETTINGS, everything))
        assertFalse(APEXRoutes.isHiddenTab(null, everything))
        assertTrue(APEXRoutes.isHiddenTab(APEXRoutes.COACH, setOf(APEXRoutes.COACH)))
    }

    @Test
    fun hiddenTabStorageMatchesTheTabRoutes() {
        assertEquals(
            listOf(APEXRoutes.PROGRESS, APEXRoutes.COACH, APEXRoutes.WORKOUTS),
            TabBarSettings.Hideable
        )
        assertEquals(emptySet<String>(), TabBarSettings.decode(null))
        assertEquals(setOf(APEXRoutes.PROGRESS), TabBarSettings.decode("home, settings,kitchen , progress"))
        assertEquals("coach,workouts", TabBarSettings.encode(setOf(APEXRoutes.WORKOUTS, APEXRoutes.COACH)))
        assertEquals(setOf(APEXRoutes.COACH, APEXRoutes.WORKOUTS), TabBarSettings.decode("coach,workouts"))
    }

    @Test
    fun hidingOneTabKeepsTheOtherStoredTabs() {
        // Coach was hidden by a restore after Settings loaded "": hiding Workouts must keep it.
        assertEquals("coach,workouts", TabBarSettings.withTabHidden("coach", APEXRoutes.WORKOUTS, hidden = true))
        assertEquals("coach", TabBarSettings.withTabHidden("coach,workouts", APEXRoutes.WORKOUTS, hidden = false))
        assertEquals("", TabBarSettings.withTabHidden(null, APEXRoutes.PROGRESS, hidden = false))
        assertEquals("progress", TabBarSettings.withTabHidden("progress", APEXRoutes.PROGRESS, hidden = true))
        // Home and Settings can't be hidden; unknown stored values are dropped.
        assertEquals("coach", TabBarSettings.withTabHidden("coach,kitchen", APEXRoutes.HOME, hidden = true))
    }
}
