import Foundation
import Testing
@testable import APEX

@MainActor
struct TrainingToolsTests {
    private func item(_ id: String, muscle: String = "chest") -> ExerciseLibraryItem {
        ExerciseLibraryItem(id: id, name: id, mechanic: "compound", category: "strength", rawEquipment: "barbell", primaryMuscles: [muscle])
    }
    private func plan(_ day: String, _ weight: String, _ reps: String, unit: String = "kg") -> StrengthWorkoutDayPlan {
        var exercise = StrengthPlannedExercise(item: item("bench"))
        exercise.sets = [StrengthPlannedSet(weight: weight, weightUnit: unit, reps: reps)]
        return StrengthWorkoutDayPlan(dateKey: day, exercises: [exercise])
    }
    @Test func weeklySummaryExcludesIncompleteAndFutureSets() throws {
        let plans = ["2026-11-02": plan("2026-11-02", "100", "10", unit: "lbs"), "2026-11-04": plan("2026-11-04", "80", ""), "2026-11-05": plan("2026-11-05", "100", "5")]
        let date = try #require(StrengthWorkoutDate.date(for: "2026-11-04"))
        let summary = TrainingTools.weekly(plans: plans, date: date, fallback: .kg)
        #expect(StrengthWorkoutDate.key(for: summary.start) == "2026-11-02")
        #expect(summary.trainingDays == 1 && summary.sets == 1)
        #expect(abs(summary.volumeKg - 453.592) < 0.01)
    }
    @Test func milestonesOnlyIncreaseForLoggedSets() throws {
        let plans = ["2026-11-01": plan("2026-11-01", "50", "5"), "2026-11-02": plan("2026-11-02", "45", "5"), "2026-11-03": plan("2026-11-03", "60", "5"), "2026-11-04": plan("2026-11-04", "80", "")]
        let date = try #require(StrengthWorkoutDate.date(for: "2026-11-04"))
        let records = TrainingTools.records(plans: plans, through: date, fallback: .kg)
        #expect(records.map(\.weightKg) == [60, 50])
    }
    @Test func filtersAlternativesByPrimaryMuscle() {
        let original = StrengthPlannedExercise(item: item("bench"))
        let alternatives = TrainingTools.alternatives(original: original, library: [item("press"), item("row", muscle: "back"), item("added")], alreadyAdded: ["added"])
        #expect(alternatives.map(\.id) == ["press"])
    }
}
