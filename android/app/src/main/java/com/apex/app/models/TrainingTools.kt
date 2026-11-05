package com.apex.app.models

import com.apex.app.data.ExerciseItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Read-only workout tools. Reps are the diary's indication that a set was logged. */
data class WeeklyTrainingSummary(val start: LocalDate, val end: LocalDate, val trainingDays: Int, val sets: Int, val volumeKg: Double)
data class WeightRecord(val date: LocalDate, val itemId: String, val name: String, val weightKg: Double, val reps: Int)

object TrainingTools {
    private const val LBS_PER_KG = 2.2046226218
    private fun kg(set: PlannedSet, fallback: WorkoutWeightUnit): Double? {
        val value = set.weight.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return null
        return if ((set.weightUnit ?: fallback) == WorkoutWeightUnit.LBS) value / LBS_PER_KG else value
    }
    fun weekly(plans: Map<String, WorkoutDayPlan>, date: LocalDate, fallback: WorkoutWeightUnit): WeeklyTrainingSummary {
        val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val end = start.plusDays(6)
        var days = 0; var sets = 0; var volume = 0.0
        for ((key, plan) in plans) {
            val day = WorkoutDate.parse(key) ?: continue
            // Do not count future plans as completed work.
            if (day < start || day > end || day > date) continue
            if (plan.exercises.any { it.hasCalculableWork }) days++
            for (ex in plan.exercises.filterNot { it.isCardio }) for (set in ex.sets) {
                val reps = set.reps.toIntOrNull()?.takeIf { it > 0 } ?: continue
                sets++
                kg(set, fallback)?.let { volume += it * reps }
            }
        }
        return WeeklyTrainingSummary(start, end, days, sets, volume)
    }
    /** Each milestone is a new heaviest logged weight for that exercise, not a 1RM. */
    fun records(plans: Map<String, WorkoutDayPlan>, through: LocalDate, fallback: WorkoutWeightUnit): List<WeightRecord> {
        val candidates = plans.entries.flatMap { (key, plan) ->
            val date = WorkoutDate.parse(key)
            if (date == null || date > through) emptyList() else plan.exercises.filterNot { it.isCardio }.flatMap { ex ->
                ex.sets.mapNotNull { set ->
                    val reps = set.reps.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                    val weight = kg(set, fallback) ?: return@mapNotNull null
                    WeightRecord(date, ex.itemId, ex.name, weight, reps)
                }
            }
        }.groupBy { it.date to it.itemId }.values.mapNotNull { day -> day.maxWithOrNull(compareBy<WeightRecord> { it.weightKg }.thenBy { it.reps }) }
            .sortedWith(compareBy<WeightRecord> { it.date }.thenBy { it.itemId })
        val best = mutableMapOf<String, Double>()
        return candidates.filter { record ->
            if (record.weightKg > (best[record.itemId] ?: 0.0) + 1e-8) { best[record.itemId] = record.weightKg; true } else false
        }.asReversed()
    }
    /** Metadata suggestions only: overlap is not a guarantee of equivalence or safety. */
    fun alternatives(original: PlannedExercise, library: List<ExerciseItem>, alreadyAdded: Set<String>): List<ExerciseItem> {
        val targets = original.primaryMuscles.map { it.lowercase() }.toSet()
        if (targets.isEmpty()) return emptyList()
        return library.asSequence().filter { it.id != original.itemId && it.id !in alreadyAdded && it.primaryMuscles.any { muscle -> muscle.lowercase() in targets } }
            .sortedWith(compareByDescending<ExerciseItem> { it.primaryMuscles.count { m -> m.lowercase() in targets } }
                .thenByDescending { it.mechanic.equals(original.mechanic, ignoreCase = true) && it.mechanic != "Unspecified" }
                .thenBy { it.name.lowercase() }).take(20).toList()
    }
}
