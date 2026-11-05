import Foundation

struct MonthlyWorkoutInsights {
    let days: Set<String>
    let daySetCounts: [String: Int]
    let performedSets: Int
    let volumeKg: Double
    let trainingWeekStreak: Int
}

struct TrainingSuggestion {
    let exerciseName: String
    let lastKg: Double
    let lastReps: Int
    let estimatedOneRepMaxKg: Double?
    let recentSessions: Int
    let estimatedOneRepMaxChangeKg: Double?
    let rpeDrift: Double?
    let primarySetsLast7Days: Int
    let historyText: String
    let suggestedKg: Double
    let suggestedReps: Int
    let rationale: String
}

enum WorkoutInsights {
    private static let poundsPerKg = 2.2046226218
    private static func weightKg(_ set: StrengthPlannedSet, fallback: WeightUnit) -> Double? {
        guard let number = Double(set.weight.replacingOccurrences(of: ",", with: ".")), number.isFinite, number > 0 else { return nil }
        return (set.weightUnit ?? fallback.rawValue) == WeightUnit.lbs.rawValue ? number / poundsPerKg : number
    }

    static func month(plans: [String: StrengthWorkoutDayPlan], month: Date, fallback: WeightUnit, calendar: Calendar = .current) -> MonthlyWorkoutInsights {
        guard let interval = calendar.dateInterval(of: .month, for: month) else {
            return MonthlyWorkoutInsights(days: [], daySetCounts: [:], performedSets: 0, volumeKg: 0, trainingWeekStreak: 0)
        }
        var days = Set<String>(), allDates: [Date] = [], sets = 0, volume = 0.0
        var daySetCounts: [String: Int] = [:]
        for (key, plan) in plans {
            guard let date = StrengthWorkoutDate.date(for: key) else { continue }
            let performed = plan.exercises.filter(\.hasCalculableWork)
            guard !performed.isEmpty else { continue }
            allDates.append(date)
            guard interval.contains(date) else { continue }
            days.insert(key)
            for exercise in performed {
                for set in exercise.sets {
                    guard let reps = Int(set.reps), reps > 0 else { continue }
                    sets += 1
                    daySetCounts[key, default: 0] += 1
                    if let kg = weightKg(set, fallback: fallback) { volume += kg * Double(reps) }
                }
            }
        }
        // Week boundaries are Monday-based for a consistent, explainable streak.
        var mondayCalendar = calendar
        mondayCalendar.firstWeekday = 2
        let weeks = Set(allDates.filter { $0 < interval.end }.compactMap { mondayCalendar.dateInterval(of: .weekOfYear, for: $0)?.start })
        var streak = 0
        if let latest = allDates.filter({ interval.contains($0) }).max(), var week = mondayCalendar.dateInterval(of: .weekOfYear, for: latest)?.start {
            while weeks.contains(week) {
                streak += 1
                guard let previous = mondayCalendar.date(byAdding: .weekOfYear, value: -1, to: week) else { break }
                week = previous
            }
        }
        return MonthlyWorkoutInsights(days: days, daySetCounts: daySetCounts, performedSets: sets, volumeKg: volume, trainingWeekStreak: streak)
    }

    static func suggestion(plans: [String: StrengthWorkoutDayPlan], itemID: String, before: Date, fallback: WeightUnit) -> TrainingSuggestion? {
        let beforeKey = StrengthWorkoutDate.key(for: before)
        let sessions: [(String, String, Double, Int, StrengthPlannedSet)] = plans.compactMap { key, plan in
            guard key < beforeKey, let exercise = plan.exercises.first(where: { $0.itemID == itemID && !$0.isCardio }) else { return nil }
            let valid: [(Double, Int, StrengthPlannedSet)] = exercise.sets.compactMap { set in
                guard let kg = weightKg(set, fallback: fallback), let reps = Int(set.reps), (1...30).contains(reps) else { return nil }
                return (kg, reps, set)
            }
            guard let top = valid.max(by: { $0.0 * (1 + Double($0.1) / 30) < $1.0 * (1 + Double($1.1) / 30) }) else { return nil }
            return (key, exercise.name, top.0, top.1, top.2)
        }.sorted { $0.0 > $1.0 }.prefix(3).map { $0 }
        guard let last = sessions.first else { return nil }
        func strengthRpe(_ set: StrengthPlannedSet) -> Double? {
            guard set.rpeScale == .strength, let rpe = Double(set.rpe.replacingOccurrences(of: ",", with: ".")), rpe.isFinite, (1...10).contains(rpe) else { return nil }
            return rpe
        }
        let efforts = sessions.map { strengthRpe($0.4) }
        let rpeDrift: Double?
        if sessions.count == 3, let latest = efforts[0], let oldest = efforts[2], efforts[1] != nil {
            rpeDrift = latest - oldest
        } else {
            rpeDrift = nil
        }
        let change: Double? = sessions.count == 3 && sessions.allSatisfy { $0.3 <= 12 } ?
            last.2 * (1 + Double(last.3) / 30) - sessions[2].2 * (1 + Double(sessions[2].3) / 30) : nil
        let increase = sessions.count == 3 && (8...12).contains(last.3) && efforts.allSatisfy { $0.map { $0 <= 8 } == true } && (rpeDrift.map { $0 <= 1 } == true) && (change.map { $0 >= 0 } == true)
        let primary = Set((plans[last.0]?.exercises.first { $0.itemID == itemID }?.primaryMuscles ?? []).map { $0.lowercased() })
        let beforeStart = Calendar.current.startOfDay(for: before)
        let sevenDaysAgo = Calendar.current.date(byAdding: .day, value: -7, to: beforeStart) ?? beforeStart
        let sevenDaySets = plans.reduce(0) { count, entry in
            guard !primary.isEmpty, let day = StrengthWorkoutDate.date(for: entry.key), day >= sevenDaysAgo, day < beforeStart else { return count }
            return count + entry.value.exercises.filter { exercise in exercise.primaryMuscles.contains { primary.contains($0.lowercased()) } }
                .reduce(0) { $0 + $1.sets.filter { (Int($0.reps) ?? 0) > 0 }.count }
        }
        let history = sessions.map { item in
            let effort = strengthRpe(item.4).map { ", strength RPE \($0)" } ?? ", RPE unavailable"
            return "\(item.0): \(item.2) kg × \(item.3)\(effort)"
        }.joined(separator: "; ")
        return TrainingSuggestion(exerciseName: last.1, lastKg: last.2, lastReps: last.3,
            estimatedOneRepMaxKg: last.3 <= 12 ? last.2 * (1 + Double(last.3) / 30) : nil,
            recentSessions: sessions.count, estimatedOneRepMaxChangeKg: change, rpeDrift: rpeDrift,
            primarySetsLast7Days: sevenDaySets, historyText: history,
            suggestedKg: last.2 + (increase ? 2.5 : 0), suggestedReps: last.3,
            rationale: increase ? "Three logged sessions show stable estimated strength and manageable recorded effort." : "Repeat your last top set; more consistent history and effort data may be needed before increasing.")
    }
}
