import Foundation

struct WeeklyTrainingSummary {
    let start: Date
    let end: Date
    let trainingDays: Int
    let sets: Int
    let volumeKg: Double
}
struct WeightRecord: Identifiable {
    var id: String { "\(StrengthWorkoutDate.key(for: date))|\(itemID)" }
    let date: Date
    let itemID: String
    let name: String
    let weightKg: Double
    let reps: Int
}

enum TrainingTools {
    private static let poundsPerKg = 2.2046226218
    private static func kg(_ set: StrengthPlannedSet, fallback: WeightUnit) -> Double? {
        guard let value = Double(set.weight.replacingOccurrences(of: ",", with: ".")), value.isFinite, value > 0 else { return nil }
        return (set.weightUnit ?? fallback.rawValue) == WeightUnit.lbs.rawValue ? value / poundsPerKg : value
    }
    static func weekly(plans: [String: StrengthWorkoutDayPlan], date: Date, fallback: WeightUnit, calendar input: Calendar = .current) -> WeeklyTrainingSummary {
        var calendar = input; calendar.firstWeekday = 2; calendar.minimumDaysInFirstWeek = 4
        let day = calendar.startOfDay(for: date)
        let start = calendar.dateInterval(of: .weekOfYear, for: day)?.start ?? day
        let end = calendar.date(byAdding: .day, value: 6, to: start) ?? start
        var days = 0, sets = 0, volume = 0.0
        for (key, plan) in plans {
            guard let parsed = StrengthWorkoutDate.date(for: key, calendar: calendar) else { continue }
            let current = calendar.startOfDay(for: parsed)
            guard current >= start && current <= end && current <= day else { continue }
            if plan.exercises.contains(where: \.hasCalculableWork) { days += 1 }
            for exercise in plan.exercises where !exercise.isCardio {
                for set in exercise.sets {
                    guard let reps = Int(set.reps), reps > 0 else { continue }
                    sets += 1
                    if let weight = kg(set, fallback: fallback) { volume += weight * Double(reps) }
                }
            }
        }
        return WeeklyTrainingSummary(start: start, end: end, trainingDays: days, sets: sets, volumeKg: volume)
    }
    /// Each milestone is a new heaviest logged weight for that exercise, not a 1RM.
    static func records(plans: [String: StrengthWorkoutDayPlan], through: Date, fallback: WeightUnit, calendar: Calendar = .current) -> [WeightRecord] {
        let end = calendar.startOfDay(for: through)
        var daily: [String: WeightRecord] = [:]
        for (key, plan) in plans {
            guard let date = StrengthWorkoutDate.date(for: key, calendar: calendar), calendar.startOfDay(for: date) <= end else { continue }
            for exercise in plan.exercises where !exercise.isCardio {
                for set in exercise.sets {
                    guard let reps = Int(set.reps), reps > 0, let weight = kg(set, fallback: fallback) else { continue }
                    let record = WeightRecord(date: date, itemID: exercise.itemID, name: exercise.name, weightKg: weight, reps: reps)
                    let identity = "\(key)|\(exercise.itemID)"
                    if let existing = daily[identity], existing.weightKg > weight || (existing.weightKg == weight && existing.reps >= reps) { continue }
                    daily[identity] = record
                }
            }
        }
        let sorted = daily.values.sorted { $0.date == $1.date ? $0.itemID < $1.itemID : $0.date < $1.date }
        var best: [String: Double] = [:], milestones: [WeightRecord] = []
        for record in sorted where record.weightKg > (best[record.itemID] ?? 0) + 1e-8 {
            best[record.itemID] = record.weightKg; milestones.append(record)
        }
        return Array(milestones.reversed())
    }
    /// Matching metadata is not a guarantee of equivalence or safety.
    static func alternatives(original: StrengthPlannedExercise, library: [ExerciseLibraryItem], alreadyAdded: Set<String>) -> [ExerciseLibraryItem] {
        let targets = Set(original.primaryMuscles.map { $0.lowercased() })
        guard !targets.isEmpty else { return [] }
        return Array(library.filter { $0.id != original.itemID && !alreadyAdded.contains($0.id) && $0.primaryMuscles.contains { targets.contains($0.lowercased()) } }
            .sorted { lhs, rhs in
                let a = lhs.primaryMuscles.filter { targets.contains($0.lowercased()) }.count
                let b = rhs.primaryMuscles.filter { targets.contains($0.lowercased()) }.count
                if a != b { return a > b }
                let sameA = lhs.mechanic.caseInsensitiveCompare(original.mechanic) == .orderedSame && lhs.mechanic != "Unspecified"
                let sameB = rhs.mechanic.caseInsensitiveCompare(original.mechanic) == .orderedSame && rhs.mechanic != "Unspecified"
                if sameA != sameB { return sameA }
                return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
            }.prefix(20))
    }
}
