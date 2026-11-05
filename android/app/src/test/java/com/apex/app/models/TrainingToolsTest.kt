package com.apex.app.models

import com.apex.app.data.ExerciseItem
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TrainingToolsTest {
    private fun item(id: String, muscle: String = "Chest", mechanic: String = "Compound") = ExerciseItem(id, id, "Beginner", emptyList(), "Push", mechanic, "Strength", "Barbell", listOf(muscle), emptyList(), emptyList())
    private fun plan(day: String, id: String, weight: String, reps: String, unit: WorkoutWeightUnit = WorkoutWeightUnit.KG): WorkoutDayPlan {
        val exercise = PlannedExercise.from(item(id)).copy(sets = listOf(PlannedSet(weight = weight, weightUnit = unit, reps = reps)))
        return WorkoutDayPlan(day, listOf(exercise))
    }
    @Test fun weekCountsOnlyLoggedWorkThroughSelectedDay() {
        val plans = mapOf("2026-11-02" to plan("2026-11-02", "bench", "100", "10", WorkoutWeightUnit.LBS), "2026-11-04" to plan("2026-11-04", "bench", "80", ""), "2026-11-05" to plan("2026-11-05", "bench", "100", "5"))
        val summary = TrainingTools.weekly(plans, LocalDate.parse("2026-11-04"), WorkoutWeightUnit.KG)
        assertEquals(LocalDate.parse("2026-11-02"), summary.start)
        assertEquals(1, summary.trainingDays); assertEquals(1, summary.sets)
        assertEquals(453.592, summary.volumeKg, 0.01)
    }
    @Test fun recordsOnlyNewHeaviestWithRepsAndSeparateExercises() {
        val plans = listOf("2026-11-01" to "50", "2026-11-02" to "45", "2026-11-03" to "60", "2026-11-04" to "80").associate { (d, w) -> d to plan(d, "bench", w, if (d.endsWith("04")) "" else "5") }
        val records = TrainingTools.records(plans, LocalDate.parse("2026-11-04"), WorkoutWeightUnit.KG)
        assertEquals(listOf(60.0, 50.0), records.map { it.weightKg })
    }
    @Test fun matchesMusclesAndExcludesAddedOrDifferentMuscles() {
        val original = PlannedExercise.from(item("bench"))
        val options = listOf(item("press"), item("row", "Back"), item("added"), item("bench"))
        assertEquals(listOf("press"), TrainingTools.alternatives(original, options, setOf("added")).map { it.id })
    }
}
