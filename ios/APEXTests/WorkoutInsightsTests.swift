import Foundation
import Testing
@testable import APEX

@MainActor
struct WorkoutInsightsTests {
    private func plan(_ date: String, weight: String, unit: String = "kg", reps: String, rpe: String = "", scale: StrengthWorkoutRPEScale? = nil) -> StrengthWorkoutDayPlan {
        let item = ExerciseLibraryItem(id: "bench", name: "Bench", category: "strength", primaryMuscles: ["chest"])
        var exercise = StrengthPlannedExercise(item: item)
        exercise.sets = [StrengthPlannedSet(weight: weight, weightUnit: unit, reps: reps, rpe: rpe, rpeScale: scale)]
        return StrengthWorkoutDayPlan(dateKey: date, exercises: [exercise])
    }
    @Test func monthCountsPerformedAndConvertsUnits() throws {
        let plans = ["2026-10-01": plan("2026-10-01", weight: "100", unit: "lbs", reps: "10"), "2026-10-02": plan("2026-10-02", weight: "20", reps: "")]
        let month = try #require(StrengthWorkoutDate.date(for: "2026-10-01"))
        let value = WorkoutInsights.month(plans: plans, month: month, fallback: .kg)
        #expect(value.days.count == 1)
        #expect(value.performedSets == 1)
        #expect(abs(value.volumeKg - 453.592) < 0.01)
    }
    @Test func ignoresCurrentDayAndUsesThreeEarlierSessions() throws {
        let dates = ["2026-09-21", "2026-09-28", "2026-10-05", "2026-10-12"]
        let plans = Dictionary(uniqueKeysWithValues: dates.map { ($0, plan($0, weight: "60", reps: "8", rpe: "7", scale: .strength)) })
        let date = try #require(StrengthWorkoutDate.date(for: "2026-10-12"))
        let value = try #require(WorkoutInsights.suggestion(plans: plans, itemID: "bench", before: date, fallback: .kg))
        #expect(value.recentSessions == 3)
        #expect(abs(value.suggestedKg - 62.5) < 0.001)
        #expect(abs((value.estimatedOneRepMaxKg ?? 0) - 76) < 0.001)
        #expect(value.rpeDrift == 0)
        #expect(value.estimatedOneRepMaxChangeKg == 0)
        #expect(value.primarySetsLast7Days == 1)
        var hardPlans = plans
        hardPlans["2026-10-05"] = plan("2026-10-05", weight: "60", reps: "8", rpe: "9.5", scale: .strength)
        let hard = try #require(WorkoutInsights.suggestion(plans: hardPlans, itemID: "bench", before: date, fallback: .kg))
        #expect(hard.suggestedKg == 60)
    }
}
