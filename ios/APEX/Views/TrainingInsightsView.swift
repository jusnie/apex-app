import SwiftUI
import UIKit

struct TrainingInsightsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(StrengthWorkoutStore.self) private var workoutStore
    let selectedDate: Date
    let unit: WeightUnit
    let initialCoach: Bool
    @State private var coach = true
    @State private var selectedItemID: String?
    @State private var targetMode = "base"
    @State private var showConfirm = false
    @State private var isLoading = false
    @State private var brief: String?
    @State private var error: String?
    @State private var month = Date.now
    @State private var story = false
    @State private var graphite = false
    @State private var selectedDay: Date?

    private var exercises: [StrengthPlannedExercise] {
        workoutStore.dayPlans[StrengthWorkoutDate.key(for: selectedDate)]?.exercises.filter { !$0.isCardio } ?? []
    }
    private var selected: StrengthPlannedExercise? { exercises.first { $0.itemID == selectedItemID } ?? exercises.first }
    private var suggestion: TrainingSuggestion? {
        guard let selected else { return nil }
        return WorkoutInsights.suggestion(plans: workoutStore.dayPlans, itemID: selected.itemID, before: selectedDate, fallback: unit)
    }
    private var summary: MonthlyWorkoutInsights { WorkoutInsights.month(plans: workoutStore.dayPlans, month: month, fallback: unit) }
    private let red = Color(red: 0.92, green: 0.34, blue: 0.31)
    private func formattedWeight(_ kg: Double) -> String {
        let value = unit == .lbs ? kg * 2.2046226218 : kg
        return String(format: "%.1f", value).replacingOccurrences(of: "\\.0$", with: "", options: .regularExpression)
    }
    private var selectedKg: Double { guard let s = suggestion else { return 0 }; return targetMode == "deload" ? s.lastKg * 0.88 : targetMode == "reps" ? s.lastKg : s.suggestedKg }
    private func targetReps(_ suggestion: TrainingSuggestion) -> Int { suggestion.suggestedReps + (targetMode == "reps" ? 1 : 0) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Picker("View", selection: $coach) { Text("Coach").tag(true); Text("Consistency").tag(false) }.pickerStyle(.segmented)
                    if coach { coachContent } else { consistencyContent }
                }.padding(20)
            }.background(WorkoutBackground())
                .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: coach)
                .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: targetMode)
                .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: story)
                .navigationTitle(coach ? "Training Coach" : "Consistency")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Done") { dismiss() } } }
                .onAppear { coach = initialCoach; month = selectedDate }
                .onChange(of: coach) { _, _ in brief = nil; error = nil }
        }
    }

    private var coachContent: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("TRAINING COACH").font(.caption2.bold()).tracking(1).foregroundStyle(red)
            Text("Your next set, made clearer.").font(.title2.bold())
            Text("Suggestions use earlier logged workouts. Review the load and your technique before using them.").font(.caption).foregroundStyle(.secondary)
            if exercises.isEmpty { Text("Add a strength exercise to this day to view a suggestion.").font(.subheadline) }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack { ForEach(exercises) { item in Button(item.name) { selectedItemID = item.itemID; targetMode = "base"; brief = nil; error = nil }.buttonStyle(.bordered) } }
            }
            if let selected, let suggestion {
                VStack(alignment: .leading, spacing: 8) {
                    Text(selected.name).font(.subheadline.bold())
                    Text("\(formattedWeight(selectedKg)) \(unit.rawValue) × \(targetReps(suggestion))").font(.title.bold())
                    Text(targetMode == "deload" ? "Optional 12% reduction from your last top set; adjust to available plates." : targetMode == "reps" ? "Optional one-rep increase at your previous load; maintain controlled technique." : suggestion.rationale).font(.caption).foregroundStyle(.secondary)
                    Text("Based on \(suggestion.recentSessions) previous session(s).").font(.caption).foregroundStyle(.secondary)
                    Text("Last 7 days: \(suggestion.primarySetsLast7Days) sets for primary muscles (workload, not measured recovery).").font(.caption).foregroundStyle(.secondary)
                    if let drift = suggestion.rpeDrift { Text("RPE change across three sessions: \(String(format: "%+.1f", drift))").font(.caption).foregroundStyle(.secondary) }
                    if let change = suggestion.estimatedOneRepMaxChangeKg { Text("Estimated 1RM change across three sessions: \(String(format: "%+.1f", unit == .lbs ? change * 2.2046226218 : change)) \(unit.rawValue)").font(.caption).foregroundStyle(.secondary) }
                    if let one = suggestion.estimatedOneRepMaxKg { Text("Estimated 1RM: \(formattedWeight(one)) \(unit.rawValue)").font(.caption).foregroundStyle(.secondary) }
                }.frame(maxWidth: .infinity, alignment: .leading).padding(16).background(red.opacity(0.12), in: RoundedRectangle(cornerRadius: 16))
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack { Button("Base target") { targetMode = "base" }; Button("Add rep") { targetMode = "reps" }; Button("Lighter set") { targetMode = "deload" } }.buttonStyle(.bordered)
                }
                Text("A formula estimate, not a recovery measurement or medical advice. Stop for pain and seek qualified help.").font(.caption).foregroundStyle(.secondary)
                Button("Use in first set") { showConfirm = true }.buttonStyle(.borderedProminent).tint(red).disabled(selected.sets.isEmpty)
                    .confirmationDialog("Update the first set?", isPresented: $showConfirm, titleVisibility: .visible) {
                        Button("Update to \(formattedWeight(selectedKg)) \(unit.rawValue) × \(targetReps(suggestion))") {
                            guard let first = selected.sets.first else { return }
                            workoutStore.updateSet(exerciseID: selected.id, setID: first.id, on: selectedDate, weight: formattedWeight(selectedKg), weightUnit: unit, reps: String(targetReps(suggestion)))
                        }
                        Button("Cancel", role: .cancel) {}
                    } message: { Text("This replaces the first set's weight and reps.") }
                Button("Ask Jarvis strategy") {
                    let context = "Exercise: \(suggestion.exerciseName); previous top sets: \(suggestion.historyText); strength RPE change: \(suggestion.rpeDrift.map { String($0) } ?? "unavailable"); estimated 1RM change in kg: \(suggestion.estimatedOneRepMaxChangeKg.map { String($0) } ?? "unavailable"); primary muscle workload over the preceding 7 days: \(suggestion.primarySetsLast7Days) sets; proposed target: \(selectedKg) kg × \(targetReps(suggestion)) reps. Workload is not measured recovery. No other data provided."
                    requestBrief(context)
                }.buttonStyle(.bordered).disabled(isLoading)
                Text("Asking Jarvis sends this exercise summary to your configured AI provider.").font(.caption2).foregroundStyle(.secondary)
            } else if let selected {
                Text("No earlier weighted sets found for \(selected.name). Log a prior session to get started.").font(.subheadline)
            }
            response
        }
    }

    private var consistencyContent: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("TRAINING INSIGHTS").font(.caption2.bold()).tracking(1).foregroundStyle(red)
            Text("Your consistency.").font(.title2.bold())
            Text("\(summary.days.count) training days").font(.title2.bold())
            Text("\(summary.performedSets) logged sets · \(Int(summary.volumeKg)) kg lifted · \(summary.trainingWeekStreak) training weeks").font(.caption).foregroundStyle(.secondary)
            HStack {
                Button { moveMonth(-1) } label: { Image(systemName: "chevron.left") }
                Spacer(); Text(month.formatted(.dateTime.month(.wide).year())).font(.subheadline.bold()); Spacer()
                Button { moveMonth(1) } label: { Image(systemName: "chevron.right") }
            }.padding(.vertical, 5)
            calendarGrid
            if let selectedDay {
                let key = StrengthWorkoutDate.key(for: selectedDay)
                Text("\(key) · \(summary.days.contains(key) ? "\(summary.daySetCounts[key] ?? 0) logged set(s)" : "No logged workout")").font(.caption).foregroundStyle(.secondary)
            }
            Text("Shading reflects logged sets (darker: fewer, brighter: more). Timed-only workouts use the lowest shade. Training days include performed diary work; streak counts consecutive Monday-based weeks through the latest training week in this month.").font(.caption).foregroundStyle(.secondary)
            Picker("Card format", selection: $story) { Text("1:1 Square").tag(false); Text("9:16 Story").tag(true) }.pickerStyle(.segmented)
            Picker("Card theme", selection: $graphite) { Text("Crimson").tag(false); Text("Graphite").tag(true) }.pickerStyle(.segmented)
            MonthlyRecapCanvas(month: month, summary: summary, graphite: graphite).aspectRatio(story ? 9.0 / 16.0 : 1, contentMode: .fit).clipShape(RoundedRectangle(cornerRadius: 14))
            Button("Share recap") { shareRecap() }.buttonStyle(.borderedProminent).tint(red)
            Button("Ask Jarvis for a monthly brief") {
                requestBrief("For \(month.formatted(.dateTime.month(.wide).year())): \(summary.days.count) training days, \(summary.performedSets) sets, \(Int(summary.volumeKg)) kg total volume, \(summary.trainingWeekStreak) consecutive training weeks. No other data provided.")
            }.buttonStyle(.bordered).disabled(isLoading)
            Text("Asking Jarvis sends these monthly totals to your configured AI provider.").font(.caption2).foregroundStyle(.secondary)
            response
        }
    }
    private var response: some View {
        Group {
            if isLoading { ProgressView() }
            if let brief { Text(brief).font(.subheadline) }
            if let error { Text(error).font(.caption).foregroundStyle(.red) }
        }
    }
    private var calendarGrid: some View {
        let calendar = Calendar.current
        let range = calendar.range(of: .day, in: .month, for: month) ?? 1..<1
        let components = calendar.dateComponents([.year, .month], from: month)
        let first = calendar.date(from: components) ?? month
        let offset = (calendar.component(.weekday, from: first) + 5) % 7
        let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: 7)
        return VStack(spacing: 5) {
            HStack { ForEach(["M", "T", "W", "T", "F", "S", "S"].indices, id: \.self) { i in Text(["M", "T", "W", "T", "F", "S", "S"][i]).font(.caption2).frame(maxWidth: .infinity).foregroundStyle(.secondary) } }
            LazyVGrid(columns: columns, spacing: 4) {
                ForEach(0..<(offset + range.count), id: \.self) { slot in
                    let day = slot - offset + 1
                    let date = day > 0 ? calendar.date(byAdding: .day, value: day - 1, to: first) : nil
                    let active = date.map { summary.days.contains(StrengthWorkoutDate.key(for: $0)) } ?? false
                    let daySets = date.map { summary.daySetCounts[StrengthWorkoutDate.key(for: $0)] ?? 0 } ?? 0
                    let shade: Color = !active ? Color.primary.opacity(0.07) : daySets >= 10 ? Color(red: 0.96, green: 0.47, blue: 0.41) : daySets >= 5 ? red : Color(red: 0.53, green: 0.23, blue: 0.25)
                    let isSelected = date.map { current in selectedDay.map { calendar.isDate($0, inSameDayAs: current) } ?? false } ?? false
                    Button { if let date { selectedDay = date } } label: {
                        Text(day > 0 ? String(day) : "").font(.caption2.bold()).frame(maxWidth: .infinity).aspectRatio(1, contentMode: .fit)
                            .background(day > 0 ? shade : .clear, in: RoundedRectangle(cornerRadius: 7))
                            .overlay(RoundedRectangle(cornerRadius: 7).stroke(isSelected ? Color.white : .clear, lineWidth: 1))
                    }.buttonStyle(.plain).disabled(date == nil)
                        .accessibilityLabel(day > 0 ? "Day \(day), \(active ? "workout logged" : "no workout logged")" : "")
                }
            }
        }
    }
    private func moveMonth(_ delta: Int) { if let next = Calendar.current.date(byAdding: .month, value: delta, to: month) { month = next; selectedDay = nil; brief = nil; error = nil } }
    private func requestBrief(_ context: String) {
        Task { isLoading = true; error = nil; defer { isLoading = false }; do { brief = try await GeminiService.workoutInsightBrief(context: context) } catch { self.error = error.localizedDescription } }
    }
    @MainActor private func shareRecap() {
        let canvas = MonthlyRecapCanvas(month: month, summary: summary, graphite: graphite).frame(width: 360, height: story ? 640 : 360)
        let renderer = ImageRenderer(content: canvas); renderer.scale = 3
        guard let data = renderer.uiImage?.pngData() else { error = "Unable to render the recap."; return }
        do {
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("APEX-Month-\(UUID().uuidString).png")
            try data.write(to: url, options: .atomic)
            let activity = UIActivityViewController(activityItems: [url], applicationActivities: nil)
            guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene, let root = scene.keyWindow?.rootViewController else { error = "Unable to open sharing."; return }
            var top = root; while let presented = top.presentedViewController { top = presented }
            activity.popoverPresentationController?.sourceView = top.view; top.present(activity, animated: true)
        } catch { self.error = error.localizedDescription }
    }
}

private struct MonthlyRecapCanvas: View {
    let month: Date
    let summary: MonthlyWorkoutInsights
    let graphite: Bool
    var body: some View {
        VStack(alignment: .leading) {
            HStack { Text("APEX FITNESS GYM"); Spacer(); Text(month.formatted(.dateTime.month(.abbreviated).year()).uppercased()) }.font(.caption2.bold())
            Spacer()
            Text("Built by\nconsistency.").font(.system(size: 30, weight: .bold)).lineLimit(2).minimumScaleFactor(0.6)
            Text("Every session counts.").font(.caption).foregroundStyle(.white.opacity(0.7))
            Spacer()
            VStack(alignment: .leading, spacing: 3) {
                Text("\(summary.days.count) DAYS    \(summary.performedSets) SETS    \(summary.trainingWeekStreak) WK")
                Text("\(Int(summary.volumeKg)) KG LIFTED")
            }.font(.caption.bold()).lineLimit(1).minimumScaleFactor(0.6)
        }.foregroundStyle(.white).padding(20).frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(LinearGradient(colors: [graphite ? Color(red: 0.21, green: 0.26, blue: 0.33) : Color(red: 0.31, green: 0.15, blue: 0.19), Color(red: 0.08, green: 0.11, blue: 0.16)], startPoint: .topLeading, endPoint: .bottomTrailing))
    }
}
