package com.apex.app.services.ai

import com.apex.app.models.PlannedExercise
import com.apex.app.models.PlannedSet
import com.apex.app.models.WorkoutDayPlan
import com.apex.app.models.WorkoutInsights
import com.apex.app.models.WorkoutRpeScale
import com.apex.app.models.WorkoutWeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class WorkoutInsightsTest {
    private fun exercise(set: PlannedSet) = PlannedExercise(itemId = "bench", name = "Bench", level = "beginner", imagePaths = emptyList(), force = "push", mechanic = "compound", category = "strength", equipment = "barbell", primaryMuscles = listOf("chest"), secondaryMuscles = emptyList(), instructions = emptyList(), sets = listOf(set))
    private fun plan(date: String, set: PlannedSet) = WorkoutDayPlan(date, listOf(exercise(set)))

    @Test fun monthCountsPerformedWorkAndConvertsWeight() {
        val plans = mapOf(
            "2026-10-01" to plan("2026-10-01", PlannedSet(weight = "100", weightUnit = WorkoutWeightUnit.LBS, reps = "10")),
            "2026-10-02" to plan("2026-10-02", PlannedSet(weight = "20"))
        )
        val summary = WorkoutInsights.month(plans, YearMonth.of(2026, 10), WorkoutWeightUnit.KG)
        assertEquals(1, summary.days.size)
        assertEquals(1, summary.performedSets)
        assertEquals(453.592, summary.volumeKg, 0.01)
    }

    @Test fun suggestionIgnoresCurrentDayAndRequiresRealHistory() {
        val set = PlannedSet(weight = "60", weightUnit = WorkoutWeightUnit.KG, reps = "8", rpe = "7", rpeScale = WorkoutRpeScale.STRENGTH)
        val current = mapOf("2026-10-12" to plan("2026-10-12", set))
        assertNull(WorkoutInsights.suggestion(current, "bench", LocalDate.parse("2026-10-12"), WorkoutWeightUnit.KG))
        val history = current + listOf("2026-09-21", "2026-09-28", "2026-10-05").associateWith { plan(it, set) }
        val result = WorkoutInsights.suggestion(history, "bench", LocalDate.parse("2026-10-12"), WorkoutWeightUnit.KG)!!
        assertEquals(3, result.recentSessions)
        assertEquals(62.5, result.suggestedKg, 0.001)
        assertEquals(76.0, result.estimatedOneRepMaxKg!!, 0.001)
        assertEquals(0.0, result.rpeDrift!!, 0.001)
        assertEquals(0.0, result.estimatedOneRepMaxChangeKg!!, 0.001)
        assertEquals(1, result.primarySetsLast7Days)
        val hardSet = set.copy(rpe = "9.5")
        val hardHistory = history + ("2026-10-05" to plan("2026-10-05", hardSet))
        assertEquals(60.0, WorkoutInsights.suggestion(hardHistory, "bench", LocalDate.parse("2026-10-12"), WorkoutWeightUnit.KG)!!.suggestedKg, 0.001)
    }
}
