import SwiftUI

private struct PlateDraft: Identifiable, Codable {
    var id = UUID()
    var weight: String
    var count: String
}
private struct PlateSettings: Codable {
    var bar: String
    var plates: [PlateDraft]
}

struct PlateCalculatorView: View {
    let defaultUnit: WeightUnit
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var unit: WeightUnit = .kg
    @State private var target = ""
    @State private var bar = ""
    @State private var plates: [PlateDraft] = []
    @State private var result: PlateLoadingResult?
    @State private var error: String?
    @State private var isCalculating = false

    private var symbol: String { unit == .kg ? "kg" : "lb" }
    private func defaults(_ unit: WeightUnit) -> PlateSettings {
        let weights = unit == .kg ? ["25", "20", "15", "10", "5", "2.5", "1.25"] : ["45", "35", "25", "10", "5", "2.5", "1.25"]
        let dominant = unit == .kg ? "20" : "45"
        return PlateSettings(bar: unit == .kg ? "20" : "45", plates: weights.map { PlateDraft(weight: $0, count: $0 == dominant ? "4" : "2") })
    }
    private func loadSettings(_ unit: WeightUnit) -> PlateSettings {
        guard let data = UserDefaults.standard.data(forKey: "apexPlateCalculator.\(unit.rawValue)"),
              let saved = try? JSONDecoder().decode(PlateSettings.self, from: data),
              saved.plates.count <= PlateLoadingCalculator.maxRows else { return defaults(unit) }
        return saved
    }
    private func switchUnit(_ next: WeightUnit) {
        guard next != unit else { return }
        unit = next
        let saved = loadSettings(next)
        bar = saved.bar; plates = saved.plates; target = ""; invalidate()
    }
    private func invalidate() { result = nil; error = nil }
    private func plateBinding(_ id: UUID, field: WritableKeyPath<PlateDraft, String>) -> Binding<String> {
        Binding(
            get: { plates.first(where: { $0.id == id })?[keyPath: field] ?? "" },
            set: { value in
                guard let index = plates.firstIndex(where: { $0.id == id }) else { return }
                plates[index][keyPath: field] = value
                invalidate()
            }
        )
    }
    private func weightText(_ value: Int) -> String {
        let whole = value / 1000, fraction = value % 1000
        guard fraction != 0 else { return String(whole) }
        let digits = String(format: "%03d", fraction).replacingOccurrences(of: "0+$", with: "", options: .regularExpression)
        return "\(whole).\(digits)"
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("TRAINING TOOL / OFFLINE").font(.caption2.bold()).tracking(1).foregroundStyle(Color.workoutAccent)
                    Text("Load your bar.").font(.title.bold())
                    Text("Find a balanced plate setup using the equipment you have.").font(.subheadline).foregroundStyle(.secondary)
                    HStack {
                        Button("kg") { switchUnit(.kg) }.buttonStyle(.bordered).tint(unit == .kg ? Color.workoutAccent : .secondary)
                        Button("lb") { switchUnit(.lbs) }.buttonStyle(.bordered).tint(unit == .lbs ? Color.workoutAccent : .secondary)
                    }.disabled(isCalculating)
                    Text("Switching units loads separate saved bar and plate settings.").font(.caption).foregroundStyle(.secondary)
                    HStack(alignment: .top, spacing: 10) {
                        VStack(alignment: .leading) {
                            Text("Target total (\(symbol))").font(.caption)
                            TextField("e.g. 100", text: $target).keyboardType(.decimalPad).textFieldStyle(.roundedBorder).onChange(of: target) { _, _ in invalidate() }
                        }
                        VStack(alignment: .leading) {
                            Text("Bar weight (\(symbol))").font(.caption)
                            TextField("Bar", text: $bar).keyboardType(.decimalPad).textFieldStyle(.roundedBorder).onChange(of: bar) { _, _ in invalidate() }
                        }
                    }.disabled(isCalculating)
                    Text("Target includes the bar. Verify its actual weight; collars are not included.").font(.caption).foregroundStyle(.secondary)
                    Text("Example equipment is prefilled. Edit it to match your gym before calculating.").font(.caption).foregroundStyle(.secondary)
                    Text("Your plates").font(.headline)
                    Text("Enter the total number available, not the number per side. Unpaired plates are left unused.").font(.caption).foregroundStyle(.secondary)
                    ForEach(plates) { plate in
                        HStack(alignment: .bottom, spacing: 8) {
                            VStack(alignment: .leading) {
                                Text("Plate (\(symbol))").font(.caption)
                                TextField("Weight", text: plateBinding(plate.id, field: \.weight)).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                            }
                            VStack(alignment: .leading) {
                                Text("Total plates").font(.caption)
                                TextField("Count", text: plateBinding(plate.id, field: \.count)).keyboardType(.numberPad).textFieldStyle(.roundedBorder)
                            }
                            Button(role: .destructive) { plates.removeAll { $0.id == plate.id }; invalidate() } label: { Image(systemName: "trash") }
                                .accessibilityLabel("Remove plate size")
                                .padding(.bottom, 8)
                        }.disabled(isCalculating)
                    }
                    if plates.count < PlateLoadingCalculator.maxRows {
                        Button { plates.append(PlateDraft(weight: "", count: "2")); invalidate() } label: { Label("Add plate size", systemImage: "plus") }.disabled(isCalculating)
                    }
                    Button { calculate() } label: {
                        HStack { Spacer(); if isCalculating { ProgressView().tint(.white) } else { Text("Calculate plates").bold() }; Spacer() }
                    }.buttonStyle(.borderedProminent).tint(Color.workoutAccent).disabled(isCalculating)
                    if let error { Text(error).font(.caption).foregroundStyle(.red) }
                    if let result {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(result.isExact ? "TARGET MATCHED" : "CLOSEST AT OR BELOW TARGET").font(.caption2.bold()).foregroundStyle(Color.workoutAccent)
                            Text("\(weightText(result.actualMilli)) \(symbol)").font(.title.bold())
                            if !result.isExact { Text("\(weightText(result.shortfallMilli)) \(symbol) below target").font(.caption).foregroundStyle(.secondary) }
                            Text("Each side").font(.subheadline.bold())
                            if result.perSide.isEmpty {
                                Text(result.isExact ? "No plates needed." : "No matching pairs can be loaded without exceeding the target.").font(.caption)
                            }
                            ForEach(result.perSide, id: \.weightMilli) { plate in
                                Text("\(plate.count) × \(weightText(plate.weightMilli)) \(symbol)").font(.subheadline)
                            }
                            Text("Bar: \(weightText(result.barMilli)) \(symbol) · same plates on both sides").font(.caption).foregroundStyle(.secondary)
                        }.frame(maxWidth: .infinity, alignment: .leading).padding(16)
                            .background(Color.workoutAccent.opacity(0.12), in: RoundedRectangle(cornerRadius: 16))
                        Text("Double-check the bar, plates, and collars before lifting. This setup excludes collar weight.").font(.caption).foregroundStyle(.secondary)
                    }
                }.padding(20)
            }
            .background(WorkoutBackground())
            .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: result != nil)
            .navigationTitle("Plate Calculator")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Done") { dismiss() }.disabled(isCalculating) } }
            .interactiveDismissDisabled(isCalculating)
            .onAppear {
                unit = defaultUnit
                let saved = loadSettings(defaultUnit)
                bar = saved.bar; plates = saved.plates
            }
        }
    }

    private func calculate() {
        guard let desired = PlateLoadingCalculator.parseWeight(target), desired > 0 else { error = "Enter a target from 0.001 to 1,000 \(symbol)."; return }
        guard let barWeight = PlateLoadingCalculator.parseWeight(bar) else { error = "Enter a valid bar weight up to 1,000 \(symbol)."; return }
        guard desired >= barWeight else { error = "The target cannot be less than the bar weight."; return }
        var inventory: [PlateInventory] = []
        for plate in plates {
            guard let weight = PlateLoadingCalculator.parseWeight(plate.weight), weight > 0,
                  let count = Int(plate.count), (0...PlateLoadingCalculator.maxCount).contains(count) else {
                error = "Enter a positive plate weight and a total count from 0 to 20 for each row."; return
            }
            inventory.append(PlateInventory(weightMilli: weight, totalCount: count))
        }
        if let data = try? JSONEncoder().encode(PlateSettings(bar: bar, plates: plates)) {
            UserDefaults.standard.set(data, forKey: "apexPlateCalculator.\(unit.rawValue)")
        }
        let input = inventory
        isCalculating = true; error = nil; result = nil
        Task {
            let calculated = await Task.detached(priority: .userInitiated) {
                PlateLoadingCalculator.calculate(targetMilli: desired, barMilli: barWeight, inventory: input)
            }.value
            result = calculated
            if calculated == nil { error = "Unable to calculate this setup." }
            isCalculating = false
        }
    }
}
