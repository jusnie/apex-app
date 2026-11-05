package com.apex.app.models

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

/** Read-only summaries based on performed diary work, not planned exercises or burn estimates. */
data class MonthlyWorkoutInsights(
    val days: Set<LocalDate>,
    val daySetCounts: Map<LocalDate, Int>,
    val performedSets: Int,
    val volumeKg: Double,
    val trainingWeekStreak: Int
)

data class TrainingSuggestion(
    val exerciseName: String,
    val lastKg: Double,
    val lastReps: Int,
    val estimatedOneRepMaxKg: Double?,
    val recentSessions: Int,
    val estimatedOneRepMaxChangeKg: Double?,
    val rpeDrift: Double?,
    val primarySetsLast7Days: Int,
    val historyText: String,
    val suggestedKg: Double,
    val suggestedReps: Int,
    val rationale: String
)

object WorkoutInsights {
    private const val LBS_PER_KG = 2.2046226218
    private fun weightKg(set: PlannedSet, fallback: WorkoutWeightUnit): Double? {
        val value = set.weight.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return null
        return if ((set.weightUnit ?: fallback) == WorkoutWeightUnit.LBS) value / LBS_PER_KG else value
    }

    fun month(plans: Map<String, WorkoutDayPlan>, month: YearMonth, fallback: WorkoutWeightUnit): MonthlyWorkoutInsights {
        val days = mutableSetOf<LocalDate>()
        val daySetCounts = mutableMapOf<LocalDate, Int>()
        var sets = 0
        var volumeKg = 0.0
        val allTrainingDays = mutableSetOf<LocalDate>()
        for ((key, plan) in plans) {
            val date = WorkoutDate.parse(key) ?: continue
            val performed = plan.exercises.filter { it.hasCalculableWork }
            if (performed.isEmpty()) continue
            allTrainingDays.add(date)
            if (YearMonth.from(date) != month) continue
            days.add(date)
            for (exercise in performed) for (set in exercise.sets) {
                val reps = set.reps.toIntOrNull()?.takeIf { it > 0 } ?: continue
                sets++
                daySetCounts[date] = (daySetCounts[date] ?: 0) + 1
                val kg = weightKg(set, fallback) ?: continue
                volumeKg += kg * reps
            }
        }
        // Consecutive training weeks ending at the latest recorded week within the selected month.
        val monthEnd = month.atEndOfMonth()
        val latest = allTrainingDays.filter { it <= monthEnd && YearMonth.from(it) == month }.maxOrNull()
        var streak = 0
        if (latest != null) {
            var week = latest.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weeks = allTrainingDays.filter { it <= monthEnd }.mapTo(hashSetOf()) { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            while (week in weeks) { streak++; week = week.minusWeeks(1) }
        }
        return MonthlyWorkoutInsights(days, daySetCounts, sets, volumeKg, streak)
    }

    /** Conservative, transparent baseline: no fabricated fatigue or unrecorded RPE. */
    fun suggestion(plans: Map<String, WorkoutDayPlan>, itemId: String, before: LocalDate, fallback: WorkoutWeightUnit): TrainingSuggestion? {
        val sessions = plans.entries.asSequence().mapNotNull { (key, plan) ->
            val date = WorkoutDate.parse(key) ?: return@mapNotNull null
            if (date >= before) return@mapNotNull null
            val exercise = plan.exercises.firstOrNull { it.itemId == itemId && !it.isCardio } ?: return@mapNotNull null
            val valid = exercise.sets.mapNotNull setLoop@ { set ->
                val kg = weightKg(set, fallback) ?: return@setLoop null
                val reps = set.reps.toIntOrNull()?.takeIf { it in 1..30 } ?: return@setLoop null
                Triple(kg, reps, set)
            }
            val top = valid.maxByOrNull { (kg, reps, _) -> kg * (1 + reps / 30.0) } ?: return@mapNotNull null
            Triple(date, exercise.name, top)
        }.sortedByDescending { it.first }.take(3).toList()
        if (sessions.isEmpty()) return null
        val (kg, reps, _) = sessions.first().third
        fun strengthRpe(set: PlannedSet): Double? = if (set.rpeScale == WorkoutRpeScale.STRENGTH)
            set.rpe.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in 1.0..10.0 } else null
        val recentEfforts = sessions.map { strengthRpe(it.third.third) }
        val rpeDrift = if (sessions.size == 3 && recentEfforts.all { it != null }) recentEfforts.first()!! - recentEfforts.last()!! else null
        val oneRmChange = if (sessions.size == 3 && sessions.all { it.third.second <= 12 }) {
            val first = sessions.first().third; val last = sessions.last().third
            first.first * (1 + first.second / 30.0) - last.first * (1 + last.second / 30.0)
        } else null
        val increase = sessions.size == 3 && reps in 8..12 && recentEfforts.all { it != null && it <= 8.0 } &&
            rpeDrift != null && rpeDrift <= 1.0 && oneRmChange != null && oneRmChange >= 0.0
        val primary = plans[sessions.first().first.toString()]?.exercises?.firstOrNull { it.itemId == itemId }
            ?.primaryMuscles?.map { it.lowercase() }?.toSet().orEmpty()
        val sevenDaySets = if (primary.isEmpty()) 0 else plans.entries.sumOf { (key, plan) ->
            val day = WorkoutDate.parse(key)
            if (day == null || day < before.minusDays(7) || day >= before) 0 else plan.exercises
                .filter { ex -> ex.primaryMuscles.any { it.lowercase() in primary } }
                .sumOf { ex -> ex.sets.count { (it.reps.toIntOrNull() ?: 0) > 0 } }
        }
        val history = sessions.joinToString("; ") { (date, _, top) ->
            val effort = strengthRpe(top.third)?.let { ", strength RPE $it" } ?: ", RPE unavailable"
            "$date: ${top.first} kg × ${top.second}$effort"
        }
        val suggested = if (increase) kg + 2.5 else kg
        return TrainingSuggestion(sessions.first().second, kg, reps, if (reps <= 12) kg * (1 + reps / 30.0) else null,
            sessions.size, oneRmChange, rpeDrift, sevenDaySets, history, suggested, reps,
            if (increase) "Three logged sessions show stable estimated strength and manageable recorded effort."
            else "Repeat your last top set; more consistent history and effort data may be needed before increasing.")
    }
}
