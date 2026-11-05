import SwiftUI

struct TrainingToolsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(StrengthWorkoutStore.self) private var workoutStore
    let selectedDate: Date
    let unit: WeightUnit
    @State private var tab = 0
    @State private var note = ""
    @State private var energy = 0
    @State private var sleep = 0
    @State private var soreness = 0
    @State private var message: String?
    @State private var selectedID: UUID?
    @State private var pending: ExerciseLibraryItem?

    private var date: Date { min(selectedDate, Date.now) }
    private var summary: WeeklyTrainingSummary { TrainingTools.weekly(plans: workoutStore.dayPlans, date: date, fallback: unit) }
    private var dateKey: String { StrengthWorkoutDate.key(for: date) }
    private var weekKey: String { StrengthWorkoutDate.key(for: summary.start) }
    private var exercises: [StrengthPlannedExercise] { workoutStore.exercises(for: selectedDate) }
    private var selected: StrengthPlannedExercise? { exercises.first { $0.id == selectedID } ?? exercises.first }
    private var records: [WeightRecord] { TrainingTools.records(plans: workoutStore.dayPlans, through: date, fallback: unit) }
    private func localKey(_ name: String, _ suffix: String) -> String { "apexTrainingTools.\(name).\(suffix)" }
    private func load() {
        let defaults = UserDefaults.standard
        note = defaults.string(forKey: localKey("week", weekKey)) ?? ""
        energy = defaults.integer(forKey: localKey("energy", dateKey))
        sleep = defaults.integer(forKey: localKey("sleep", dateKey))
        soreness = defaults.integer(forKey: localKey("soreness", dateKey))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("TRAINING TOOLS").font(.caption2.bold()).tracking(1).foregroundStyle(Color.workoutAccent)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(Array(["Weekly Check-in", "Readiness", "Records", "Exercise Swaps"].enumerated()), id: \.offset) { index, label in
                                Button(label) { tab = index; message = nil }.buttonStyle(.bordered).tint(tab == index ? Color.workoutAccent : .secondary)
                            }
                        }
                    }
                    if tab == 0 { weekly }
                    else if tab == 1 { readiness }
                    else if tab == 2 { recordTimeline }
                    else { swaps }
                    if let message { Text(message).font(.caption).foregroundStyle(.secondary) }
                }.padding(20)
            }.background(WorkoutBackground())
                .navigationTitle("Training Tools").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Done") { dismiss() } } }
                .onAppear { load() }
                .confirmationDialog("Swap exercise?", isPresented: Binding(get: { pending != nil }, set: { if !$0 { pending = nil } }), titleVisibility: .visible) {
                    if let pending, let selected {
                        Button("Replace with \(pending.name)") {
                            message = workoutStore.swapUnloggedExercise(selected.id, with: pending, on: selectedDate) ? "Exercise swapped." : "The exercise changed or now contains logged data; no swap was made."
                            self.pending = nil
                        }
                    }
                    Button("Cancel", role: .cancel) { pending = nil }
                } message: { Text("Only untouched planned entries can be replaced. Review the alternative's instructions and choose an appropriate load.") }
        }
    }
    private var weekly: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Your week.").font(.title.bold())
            Text("\(weekKey) to \(StrengthWorkoutDate.key(for: summary.end)) · through \(dateKey)").font(.caption).foregroundStyle(.secondary)
            Text("\(summary.trainingDays) training days · \(summary.sets) logged sets").font(.headline)
            Text("\(String(format: "%.1f", summary.volumeKg)) kg total volume").font(.caption).foregroundStyle(.secondary)
            Text("Volume includes logged weighted strength sets; timed work can count as a training day. This is not a recovery score.").font(.caption).foregroundStyle(.secondary)
            Text("Reflection or next goal").font(.subheadline.bold())
            TextEditor(text: $note).frame(minHeight: 100).scrollContentBackground(.hidden).padding(8).background(Color.secondary.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
                .onChange(of: note) { _, value in if value.count > 500 { note = String(value.prefix(500)) }; message = nil }
            Button("Save check-in") { UserDefaults.standard.set(note, forKey: localKey("week", weekKey)); message = "Check-in saved on this device." }.buttonStyle(.borderedProminent).tint(Color.workoutAccent)
        }
    }
    private var readiness: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("How do you feel?").font(.title.bold())
            Text("A quick self-check for \(dateKey). Choose what feels right; no automated diagnosis or load changes.").font(.caption).foregroundStyle(.secondary)
            rating("Energy", selected: $energy, choices: ["Low", "Okay", "High"])
            rating("Sleep", selected: $sleep, choices: ["Poor", "Okay", "Good"])
            rating("Soreness", selected: $soreness, choices: ["Low", "Moderate", "High"])
            Button("Save readiness check") {
                let defaults = UserDefaults.standard
                defaults.set(energy, forKey: localKey("energy", dateKey)); defaults.set(sleep, forKey: localKey("sleep", dateKey)); defaults.set(soreness, forKey: localKey("soreness", dateKey))
                message = "Readiness check saved on this device."
            }.buttonStyle(.borderedProminent).tint(Color.workoutAccent).disabled(energy == 0 || sleep == 0 || soreness == 0)
            Text("Consider a lighter session or rest if you feel unwell. Stop for pain and seek qualified advice when needed.").font(.caption).foregroundStyle(.secondary)
        }
    }
    private func rating(_ label: String, selected: Binding<Int>, choices: [String]) -> some View {
        VStack(alignment: .leading) {
            Text(label).font(.subheadline.bold())
            Picker(label, selection: selected) {
                Text("Select").tag(0)
                ForEach(Array(choices.enumerated()), id: \.offset) { index, text in Text(text).tag(index + 1) }
            }.pickerStyle(.segmented)
        }
    }
    private var recordTimeline: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Your records.").font(.title.bold())
            Text("New heaviest logged weights, through \(dateKey). Reps must be logged; these are not estimated one-rep maxes.").font(.caption).foregroundStyle(.secondary)
            if records.isEmpty { Text("No weighted sets logged yet.") }
            ForEach(Array(records.prefix(100))) { record in
                VStack(alignment: .leading) {
                    Text("\(StrengthWorkoutDate.key(for: record.date)) · \(record.name)").font(.subheadline.bold())
                    let value = unit == .lbs ? record.weightKg * 2.2046226218 : record.weightKg
                    Text("\(String(format: "%.1f", value)) \(unit.rawValue) × \(record.reps)").font(.caption).foregroundStyle(.secondary)
                }
            }
            if records.count > 100 { Text("Showing the latest 100 milestones.").font(.caption).foregroundStyle(.secondary) }
        }
    }
    private var swaps: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Find an alternative.").font(.title.bold())
            Text("Suggestions share a primary muscle; they are not guaranteed equivalent or safe. Review equipment, technique and any limitations.").font(.caption).foregroundStyle(.secondary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack { ForEach(exercises) { ex in Button(ex.name) { selectedID = ex.id; message = nil }.buttonStyle(.bordered) } }
            }
            if let selected {
                Text("Alternatives for \(selected.name)").font(.headline)
                let locked = selected.sets.contains(where: \.hasLoggedValue) || selected.timer?.hasStarted == true
                if locked { Text("This entry has logged data and cannot be swapped. Add an alternative separately instead.").font(.caption).foregroundStyle(.secondary) }
                let matches = TrainingTools.alternatives(original: selected, library: workoutStore.exerciseLibrary.exercises, alreadyAdded: Set(exercises.map(\.itemID)))
                if matches.isEmpty { Text("No matching alternatives found.") }
                ForEach(matches) { item in
                    HStack {
                        VStack(alignment: .leading) { Text(item.name).font(.subheadline); Text(item.rawEquipment).font(.caption).foregroundStyle(.secondary) }
                        Spacer(); Button("Swap") { pending = item }.buttonStyle(.bordered).disabled(locked)
                    }
                }
            } else { Text("Add an exercise to this day first.") }
        }
    }
}
