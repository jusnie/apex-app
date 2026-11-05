import SwiftUI
import UIKit

// MARK: - Anatomical SVG Model & 3D Biometric Parser

enum BodyMapTabMode: String, CaseIterable {
    case target = "Target"
    case recovery = "7d Recovery"
}

enum BodyMapSideOption: String, CaseIterable {
    case both = "Front + Back"
    case front = "Front"
    case back = "Back"
}

struct BodyMuscleMetadata {
    let label: String
    let catalogMuscles: Set<String>
    let synergists: Set<String>
}

enum BodyAnatomyCatalog {
    static let muscles: [String: BodyMuscleMetadata] = [
        "chest": BodyMuscleMetadata(label: "Chest (Pectorals)", catalogMuscles: ["chest"], synergists: ["deltoids", "triceps"]),
        "deltoids": BodyMuscleMetadata(label: "Shoulders (Deltoids)", catalogMuscles: ["shoulders"], synergists: ["trapezius", "triceps"]),
        "biceps": BodyMuscleMetadata(label: "Biceps", catalogMuscles: ["biceps"], synergists: ["forearm"]),
        "triceps": BodyMuscleMetadata(label: "Triceps", catalogMuscles: ["triceps"], synergists: ["deltoids", "chest"]),
        "forearm": BodyMuscleMetadata(label: "Forearms", catalogMuscles: ["forearms"], synergists: ["biceps"]),
        "abs": BodyMuscleMetadata(label: "Core & Abs", catalogMuscles: ["abdominals"], synergists: ["obliques", "lower-back"]),
        "obliques": BodyMuscleMetadata(label: "Obliques", catalogMuscles: ["abdominals"], synergists: ["abs", "lower-back"]),
        "trapezius": BodyMuscleMetadata(label: "Traps", catalogMuscles: ["traps"], synergists: ["upper-back", "deltoids", "neck"]),
        "upper-back": BodyMuscleMetadata(label: "Lats & Upper Back", catalogMuscles: ["lats", "middle back"], synergists: ["trapezius", "biceps", "lower-back"]),
        "lower-back": BodyMuscleMetadata(label: "Lower Back", catalogMuscles: ["lower back"], synergists: ["gluteal", "hamstring", "abs"]),
        "gluteal": BodyMuscleMetadata(label: "Glutes", catalogMuscles: ["glutes"], synergists: ["hamstring", "quadriceps", "lower-back"]),
        "quadriceps": BodyMuscleMetadata(label: "Quadriceps", catalogMuscles: ["quadriceps"], synergists: ["gluteal", "calves", "adductors"]),
        "hamstring": BodyMuscleMetadata(label: "Hamstrings", catalogMuscles: ["hamstrings"], synergists: ["gluteal", "calves", "lower-back"]),
        "adductors": BodyMuscleMetadata(label: "Adductors & Abductors", catalogMuscles: ["adductors", "abductors"], synergists: ["quadriceps", "gluteal"]),
        "calves": BodyMuscleMetadata(label: "Calves", catalogMuscles: ["calves"], synergists: ["tibialis", "hamstring"]),
        "tibialis": BodyMuscleMetadata(label: "Tibialis & Shins", catalogMuscles: ["calves"], synergists: ["calves"]),
        "neck": BodyMuscleMetadata(label: "Neck", catalogMuscles: ["neck"], synergists: ["trapezius"])
    ]

    static let nonInteractiveSlugs: Set<String> = ["head", "hair", "hands", "feet", "knees", "ankles"]

    static let splitPresets: [(label: String, slugs: Set<String>)] = [
        ("Push", ["chest", "deltoids", "triceps"]),
        ("Pull", ["upper-back", "trapezius", "biceps", "forearm"]),
        ("Legs", ["quadriceps", "hamstring", "gluteal", "calves", "adductors"]),
        ("Core", ["abs", "obliques", "lower-back"])
    ]

    static func catalogMuscles(from slugs: Set<String>) -> Set<String> {
        slugs.reduce(into: Set<String>()) { acc, slug in
            if let meta = muscles[slug] {
                acc.formUnion(meta.catalogMuscles)
            }
        }
    }

    static func slugs(fromCatalogMuscles rawMuscles: Set<String>) -> Set<String> {
        let lower = Set(rawMuscles.map { $0.lowercased() })
        guard !lower.isEmpty else { return [] }
        return Set(muscles.compactMap { slug, meta in
            meta.catalogMuscles.contains(where: { lower.contains($0) }) ? slug : nil
        })
    }
}

struct ParsedBodyPart {
    let slug: String
    let paths: [Path]
}

struct ParsedBodyFigure {
    let viewBox: CGRect
    let outlines: [Path]
    let parts: [ParsedBodyPart]
}

private struct RawAnatomyFile: Decodable {
    struct RawPart: Decodable {
        let slug: String
        let path: [String: [String]]
    }
    struct RawGender: Decodable {
        let frontViewBox: String
        let backViewBox: String
        let outlines: [String: [String]]
        let front: [RawPart]
        let back: [RawPart]
    }
    let male: RawGender
    let female: RawGender
}

final class BodyAnatomyDataset {
    static let shared = BodyAnatomyDataset()

    let maleFront: ParsedBodyFigure
    let maleBack: ParsedBodyFigure
    let femaleFront: ParsedBodyFigure
    let femaleBack: ParsedBodyFigure

    private init() {
        guard let url = Bundle.main.url(forResource: "body_anatomy_paths", withExtension: "json")
                ?? Bundle.main.url(forResource: "body_anatomy_paths", withExtension: "json", subdirectory: "BodyMap"),
              let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode(RawAnatomyFile.self, from: data)
        else {
            let empty = ParsedBodyFigure(viewBox: CGRect(x: 0, y: 0, width: 724, height: 1448), outlines: [], parts: [])
            maleFront = empty; maleBack = empty; femaleFront = empty; femaleBack = empty
            return
        }

        maleFront = Self.buildFigure(raw: decoded.male, side: "front", parts: decoded.male.front)
        maleBack = Self.buildFigure(raw: decoded.male, side: "back", parts: decoded.male.back)
        femaleFront = Self.buildFigure(raw: decoded.female, side: "front", parts: decoded.female.front)
        femaleBack = Self.buildFigure(raw: decoded.female, side: "back", parts: decoded.female.back)
    }

    func figure(gender: Gender, isBack: Bool) -> ParsedBodyFigure {
        if gender == .female {
            return isBack ? femaleBack : femaleFront
        }
        return isBack ? maleBack : maleFront
    }

    private static func buildFigure(raw: RawAnatomyFile.RawGender, side: String, parts: [RawAnatomyFile.RawPart]) -> ParsedBodyFigure {
        let vbRaw = side == "back" ? raw.backViewBox : raw.frontViewBox
        let vbTokens = vbRaw
            .split(whereSeparator: { $0.isWhitespace })
            .compactMap { Double($0) }
        let vb = vbTokens.count >= 4
            ? CGRect(x: vbTokens[0], y: vbTokens[1], width: vbTokens[2], height: vbTokens[3])
            : CGRect(x: 0, y: 0, width: 724, height: 1448)

        let outlines = (raw.outlines[side] ?? []).map(parseNormalizedSVGPath)
        let parsedParts = parts.map { rp -> ParsedBodyPart in
            var pList: [Path] = []
            for key in ["common", "left", "right"] {
                if let list = rp.path[key] {
                    pList.append(contentsOf: list.map(parseNormalizedSVGPath))
                }
            }
            return ParsedBodyPart(slug: rp.slug, paths: pList)
        }
        return ParsedBodyFigure(viewBox: vb, outlines: outlines, parts: parsedParts)
    }

    /// Parses pre-normalized space-delimited `M`, `L`, `C`, `Q`, `Z` SVG paths.
    private static func parseNormalizedSVGPath(_ d: String) -> Path {
        var path = Path()
        let tokens = d.split(separator: " ")
        var idx = 0
        while idx < tokens.count {
            let cmd = tokens[idx]
            idx += 1
            switch cmd {
            case "M":
                guard idx + 1 < tokens.count,
                      let x = Double(tokens[idx]),
                      let y = Double(tokens[idx + 1]) else { break }
                idx += 2
                path.move(to: CGPoint(x: x, y: y))
            case "L":
                guard idx + 1 < tokens.count,
                      let x = Double(tokens[idx]),
                      let y = Double(tokens[idx + 1]) else { break }
                idx += 2
                path.addLine(to: CGPoint(x: x, y: y))
            case "C":
                guard idx + 5 < tokens.count,
                      let x1 = Double(tokens[idx]),
                      let y1 = Double(tokens[idx + 1]),
                      let x2 = Double(tokens[idx + 2]),
                      let y2 = Double(tokens[idx + 3]),
                      let x = Double(tokens[idx + 4]),
                      let y = Double(tokens[idx + 5]) else { break }
                idx += 6
                path.addCurve(
                    to: CGPoint(x: x, y: y),
                    control1: CGPoint(x: x1, y: y1),
                    control2: CGPoint(x: x2, y: y2)
                )
            case "Q":
                guard idx + 3 < tokens.count,
                      let x1 = Double(tokens[idx]),
                      let y1 = Double(tokens[idx + 1]),
                      let x = Double(tokens[idx + 2]),
                      let y = Double(tokens[idx + 3]) else { break }
                idx += 4
                path.addQuadCurve(
                    to: CGPoint(x: x, y: y),
                    control: CGPoint(x: x1, y: y1)
                )
            case "Z":
                path.closeSubpath()
            default:
                break
            }
        }
        return path
    }
}

struct SevenDayRecoverySnapshot {
    let setsBySlug: [String: Int]

    static func compute(from dayPlans: [String: StrengthWorkoutDayPlan]) -> SevenDayRecoverySnapshot {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"

        var counts: [String: Int] = Dictionary(uniqueKeysWithValues: BodyAnatomyCatalog.muscles.keys.map { ($0, 0) })
        for dayOffset in 0..<7 {
            guard let date = calendar.date(byAdding: .day, value: -dayOffset, to: today) else { continue }
            let key = formatter.string(from: date)
            guard let plan = dayPlans[key] else { continue }
            for exercise in plan.exercises {
                let completedSets = max(1, exercise.sets.count)
                let exLower = Set(exercise.primaryMuscles.map { $0.lowercased() })
                for (slug, meta) in BodyAnatomyCatalog.muscles where meta.catalogMuscles.contains(where: { exLower.contains($0) }) {
                    counts[slug, default: 0] += completedSets
                }
            }
        }
        return SevenDayRecoverySnapshot(setsBySlug: counts)
    }
}

// MARK: - Interactive Biometric Body Map Panel

struct BiometricBodyMapPanelView: View {
    @Binding var selectedSlugs: Set<String>
    let dayPlans: [String: StrengthWorkoutDayPlan]
    let defaultGender: Gender
    let matchedCount: Int

    @State private var tabMode: BodyMapTabMode = .target
    @State private var bodyGender: Gender = .male
    @State private var sideOption: BodyMapSideOption = .both

    private var recovery: SevenDayRecoverySnapshot {
        SevenDayRecoverySnapshot.compute(from: dayPlans)
    }

    private var synergistSlugs: Set<String> {
        guard tabMode == .target else { return [] }
        let allSynergists = selectedSlugs.reduce(into: Set<String>()) { acc, slug in
            if let s = BodyAnatomyCatalog.muscles[slug]?.synergists {
                acc.formUnion(s)
            }
        }
        return allSynergists.subtracting(selectedSlugs)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 11) {
            headerRow
            presetAndGenderRow
            figuresStage
            footerBar
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(Color(red: 0.086, green: 0.106, blue: 0.157).opacity(0.84))
                .overlay(
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .stroke(Color.white.opacity(0.10), lineWidth: 1)
                )
        )
        .onAppear {
            bodyGender = defaultGender == .female ? .female : .male
        }
    }

    private var headerRow: some View {
        HStack(spacing: 8) {
            HStack(spacing: 7) {
                Circle()
                    .fill(Color(red: 0.882, green: 0.024, blue: 0.0))
                    .frame(width: 8, height: 8)
                Text("BIOMETRIC BODY MAP")
                    .font(.system(size: 11, weight: .heavy))
                    .tracking(1.1)
                    .foregroundStyle(.white)
            }
            Spacer()
            HStack(spacing: 3) {
                ForEach(BodyMapTabMode.allCases, id: \.rawValue) { item in
                    let active = tabMode == item
                    Button {
                        withAnimation(.snappy(duration: 0.22)) {
                            tabMode = item
                        }
                    } label: {
                        Text(item.rawValue)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(active ? .white : .white.opacity(0.60))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(
                                Capsule()
                                    .fill(
                                        active
                                            ? LinearGradient(
                                                colors: [Color(red: 0.882, green: 0.024, blue: 0.0), Color(red: 1.0, green: 0.18, blue: 0.18)],
                                                startPoint: .leading,
                                                endPoint: .trailing
                                            )
                                            : LinearGradient(colors: [.clear, .clear], startPoint: .leading, endPoint: .trailing)
                                    )
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(3)
            .background(Capsule().fill(Color.white.opacity(0.06)))
        }
    }

    private var presetAndGenderRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                miniChip(title: bodyGender == .female ? "Female" : "Male", isSelected: true) {
                    withAnimation(.snappy(duration: 0.2)) {
                        bodyGender = (bodyGender == .female) ? .male : .female
                    }
                }
                miniChip(title: sideOption.rawValue, isSelected: false) {
                    withAnimation(.snappy(duration: 0.2)) {
                        let all = BodyMapSideOption.allCases
                        let nextIdx = ((all.firstIndex(of: sideOption) ?? 0) + 1) % all.count
                        sideOption = all[nextIdx]
                    }
                }
                if tabMode == .target {
                    ForEach(BodyAnatomyCatalog.splitPresets, id: \.label) { preset in
                        let active = selectedSlugs == preset.slugs
                        miniChip(title: preset.label, isSelected: active) {
                            withAnimation(.snappy(duration: 0.2)) {
                                selectedSlugs = active ? [] : preset.slugs
                            }
                        }
                    }
                    if !selectedSlugs.isEmpty {
                        miniChip(title: "Clear", isSelected: false) {
                            withAnimation(.snappy(duration: 0.2)) {
                                selectedSlugs = []
                            }
                        }
                    }
                }
            }
        }
    }

    private var figuresStage: some View {
        HStack(spacing: 6) {
            if sideOption == .both || sideOption == .front {
                InteractiveAnatomyCanvasView(
                    figure: BodyAnatomyDataset.shared.figure(gender: bodyGender, isBack: false),
                    caption: "ANTERIOR",
                    tabMode: tabMode,
                    selectedSlugs: selectedSlugs,
                    synergistSlugs: synergistSlugs,
                    recoverySets: recovery.setsBySlug,
                    onTapSlug: toggleMuscleSlug
                )
                .frame(height: sideOption == .both ? 276 : 320)
            }
            if sideOption == .both || sideOption == .back {
                InteractiveAnatomyCanvasView(
                    figure: BodyAnatomyDataset.shared.figure(gender: bodyGender, isBack: true),
                    caption: "POSTERIOR",
                    tabMode: tabMode,
                    selectedSlugs: selectedSlugs,
                    synergistSlugs: synergistSlugs,
                    recoverySets: recovery.setsBySlug,
                    onTapSlug: toggleMuscleSlug
                )
                .frame(height: sideOption == .both ? 276 : 320)
            }
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(
                    RadialGradient(
                        colors: [
                            Color(red: 0.882, green: 0.024, blue: 0.0).opacity(0.16),
                            Color(red: 0.055, green: 0.071, blue: 0.114).opacity(0.95)
                        ],
                        center: .center,
                        startRadius: 12,
                        endRadius: 210
                    )
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .stroke(Color.white.opacity(0.08), lineWidth: 1)
                )
        )
    }

    @ViewBuilder
    private var footerBar: some View {
        if tabMode == .target {
            HStack(spacing: 8) {
                let summary: String = {
                    if selectedSlugs.isEmpty {
                        return "Tap any muscle group to filter 317 video workouts"
                    }
                    return selectedSlugs
                        .compactMap { BodyAnatomyCatalog.muscles[$0]?.label }
                        .joined(separator: " • ")
                }()
                Text(summary)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.84))
                    .lineLimit(1)
                Spacer()
                Text("\(matchedCount) MOVES")
                    .font(.system(size: 10.5, weight: .heavy))
                    .foregroundStyle(Color(red: 1.0, green: 0.42, blue: 0.42))
                    .padding(.horizontal, 9)
                    .padding(.vertical, 4)
                    .background(
                        Capsule()
                            .fill(Color(red: 0.882, green: 0.024, blue: 0.0).opacity(0.22))
                            .overlay(Capsule().stroke(Color(red: 1.0, green: 0.35, blue: 0.35).opacity(0.45), lineWidth: 1))
                    )
            }
        } else {
            HStack(spacing: 14) {
                legendDot(color: Color(red: 0.882, green: 0.024, blue: 0.0), label: "Peak (10+ sets)")
                legendDot(color: Color(red: 0.96, green: 0.62, blue: 0.04), label: "Recovering (4–9)")
                legendDot(color: Color(red: 0.06, green: 0.72, blue: 0.50), label: "Fresh (0–3)")
            }
        }
    }

    private func toggleMuscleSlug(_ slug: String) {
        withAnimation(.snappy(duration: 0.2)) {
            if tabMode == .recovery {
                tabMode = .target
                selectedSlugs = [slug]
            } else if selectedSlugs.contains(slug) {
                selectedSlugs.remove(slug)
            } else {
                selectedSlugs.insert(slug)
            }
        }
    }

    private func miniChip(title: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 11.5, weight: .bold))
                .foregroundStyle(isSelected ? .white : .white.opacity(0.75))
                .padding(.horizontal, 11)
                .padding(.vertical, 6)
                .background(
                    Capsule()
                        .fill(
                            isSelected
                                ? LinearGradient(
                                    colors: [Color(red: 0.882, green: 0.024, blue: 0.0), Color(red: 1.0, green: 0.18, blue: 0.18)],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                                : LinearGradient(
                                    colors: [Color.white.opacity(0.06), Color.white.opacity(0.06)],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                        )
                        .overlay(
                            Capsule().stroke(
                                isSelected ? Color(red: 1.0, green: 0.35, blue: 0.35) : Color.white.opacity(0.11),
                                lineWidth: 1
                            )
                        )
                )
        }
        .buttonStyle(.plain)
    }

    private func legendDot(color: Color, label: String) -> some View {
        HStack(spacing: 5) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(label)
                .font(.system(size: 10.5, weight: .semibold))
                .foregroundStyle(.white.opacity(0.76))
        }
    }
}

private struct InteractiveAnatomyCanvasView: View {
    let figure: ParsedBodyFigure
    let caption: String
    let tabMode: BodyMapTabMode
    let selectedSlugs: Set<String>
    let synergistSlugs: Set<String>
    let recoverySets: [String: Int]
    let onTapSlug: (String) -> Void

    var body: some View {
        VStack(spacing: 3) {
            GeometryReader { geo in
                let size = geo.size
                let vb = figure.viewBox
                let scale = min(size.width / max(1, vb.width), size.height / max(1, vb.height))
                let tx = (size.width - vb.width * scale) * 0.5 - vb.minX * scale
                let ty = (size.height - vb.height * scale) * 0.5 - vb.minY * scale
                let transform = CGAffineTransform(translationX: tx, y: ty).scaledBy(x: scale, y: scale)

                Canvas { context, _ in
                    for outline in figure.outlines {
                        let p = outline.applying(transform)
                        context.fill(p, with: .color(Color(red: 0.086, green: 0.106, blue: 0.157).opacity(0.95)))
                        context.stroke(p, with: .color(Color.white.opacity(0.18)), lineWidth: 1.4)
                    }

                    for part in figure.parts {
                        let isInteractive = BodyAnatomyCatalog.muscles[part.slug] != nil && !BodyAnatomyCatalog.nonInteractiveSlugs.contains(part.slug)
                        let (cTop, cMid, cBot, strokeCol, strokeW) = colorsForPart(slug: part.slug, isInteractive: isInteractive)

                        for rawPath in part.paths {
                            let p = rawPath.applying(transform)
                            let bounds = p.boundingRect
                            let shading = GraphicsContext.Shading.linearGradient(
                                Gradient(colors: [cTop, cMid, cBot]),
                                startPoint: CGPoint(x: bounds.minX, y: bounds.minY),
                                endPoint: CGPoint(x: bounds.maxX, y: bounds.maxY)
                            )
                            context.fill(p, with: shading)
                            context.stroke(p, with: .color(strokeCol), lineWidth: strokeW)
                        }
                    }
                }
                .contentShape(Rectangle())
                .gesture(
                    SpatialTapGesture()
                        .onEnded { event in
                            let inv = transform.inverted()
                            let svgPoint = event.location.applying(inv)
                            for part in figure.parts.reversed() {
                                guard BodyAnatomyCatalog.muscles[part.slug] != nil,
                                      !BodyAnatomyCatalog.nonInteractiveSlugs.contains(part.slug) else { continue }
                                if part.paths.contains(where: { $0.cgPath.contains(svgPoint) }) {
                                    onTapSlug(part.slug)
                                    break
                                }
                            }
                        }
                )
            }
            Text(caption)
                .font(.system(size: 9.5, weight: .heavy))
                .tracking(1.4)
                .foregroundStyle(.white.opacity(0.45))
        }
    }

    private func colorsForPart(slug: String, isInteractive: Bool) -> (Color, Color, Color, Color, CGFloat) {
        if !isInteractive {
            return (
                Color(red: 0.133, green: 0.165, blue: 0.235),
                Color(red: 0.090, green: 0.114, blue: 0.173),
                Color(red: 0.059, green: 0.075, blue: 0.125),
                Color.white.opacity(0.11),
                0.7
            )
        }
        if tabMode == .recovery {
            let sets = recoverySets[slug] ?? 0
            if sets >= 10 {
                return (Color(red: 1.0, green: 0.28, blue: 0.28), Color(red: 0.882, green: 0.024, blue: 0.0), Color(red: 0.56, green: 0.01, blue: 0.0), Color(red: 1.0, green: 0.55, blue: 0.55), 1.3)
            } else if sets >= 4 {
                return (Color(red: 0.988, green: 0.827, blue: 0.302), Color(red: 0.961, green: 0.620, blue: 0.043), Color(red: 0.706, green: 0.325, blue: 0.035), Color(red: 0.99, green: 0.90, blue: 0.54), 1.1)
            } else {
                return (Color(red: 0.204, green: 0.827, blue: 0.600), Color(red: 0.063, green: 0.725, blue: 0.506), Color(red: 0.016, green: 0.471, blue: 0.341), Color(red: 0.43, green: 0.91, blue: 0.72), 1.0)
            }
        }
        if selectedSlugs.contains(slug) {
            return (Color(red: 1.0, green: 0.298, blue: 0.298), Color(red: 0.882, green: 0.024, blue: 0.0), Color(red: 0.60, green: 0.012, blue: 0.0), Color(red: 1.0, green: 0.56, blue: 0.56), 1.45)
        }
        if synergistSlugs.contains(slug) {
            return (Color(red: 1.0, green: 0.620, blue: 0.400), Color(red: 0.851, green: 0.306, blue: 0.122), Color(red: 0.486, green: 0.157, blue: 0.063), Color(red: 1.0, green: 0.70, blue: 0.54), 1.1)
        }
        return (
            Color(red: 0.227, green: 0.278, blue: 0.388),
            Color(red: 0.145, green: 0.180, blue: 0.267),
            Color(red: 0.090, green: 0.114, blue: 0.180),
            Color.white.opacity(0.22),
            0.85
        )
    }
}

// MARK: - Full-Screen Modal Body Map Picker for Workout Log

struct WorkoutBodyMapSheetView: View {
    let selectedDate: Date
    let libraryItems: [ExerciseLibraryItem]
    let addedExerciseIDs: Set<String>
    let dayPlans: [String: StrengthWorkoutDayPlan]
    let defaultGender: Gender
    let onToggleExercise: (ExerciseLibraryItem) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var selectedSlugs: Set<String> = ["chest"]
    @State private var searchQuery: String = ""

    private var filteredExercises: [ExerciseLibraryItem] {
        let targetMuscles = BodyAnatomyCatalog.catalogMuscles(from: selectedSlugs)
        let q = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return libraryItems.filter { item in
            let matchesMuscle = targetMuscles.isEmpty || item.primaryMuscles.contains(where: { targetMuscles.contains($0.lowercased()) })
            let matchesQuery = q.isEmpty || item.name.lowercased().contains(q) || item.rawEquipment.lowercased().contains(q)
            return matchesMuscle && matchesQuery
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    BiometricBodyMapPanelView(
                        selectedSlugs: $selectedSlugs,
                        dayPlans: dayPlans,
                        defaultGender: defaultGender,
                        matchedCount: filteredExercises.count
                    )

                    HStack(spacing: 10) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(.white.opacity(0.55))
                        TextField("Filter matching video workouts...", text: $searchQuery)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 11)
                    .background(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(Color.white.opacity(0.06))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .stroke(Color.white.opacity(0.11), lineWidth: 1)
                            )
                    )

                    LazyVStack(spacing: 9) {
                        ForEach(filteredExercises.prefix(80)) { item in
                            let isAdded = addedExerciseIDs.contains(item.id)
                            HStack(spacing: 12) {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(item.name)
                                        .font(.system(size: 14.5, weight: .bold))
                                        .foregroundStyle(.white)
                                        .lineLimit(1)
                                    Text("\(item.primaryMuscles.joined(separator: ", ").capitalized) • \(item.rawEquipment.isEmpty ? "Bodyweight" : item.rawEquipment.capitalized)")
                                        .font(.system(size: 11.5, weight: .semibold))
                                        .foregroundStyle(.white.opacity(0.60))
                                        .lineLimit(1)
                                }
                                Spacer()
                                Button {
                                    onToggleExercise(item)
                                } label: {
                                    Text(isAdded ? "Added" : "Add")
                                        .font(.system(size: 12, weight: .heavy))
                                        .foregroundStyle(.white)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 7)
                                        .background(
                                            Capsule()
                                                .fill(
                                                    isAdded
                                                        ? LinearGradient(colors: [Color.white.opacity(0.16), Color.white.opacity(0.16)], startPoint: .leading, endPoint: .trailing)
                                                        : LinearGradient(colors: [Color(red: 0.882, green: 0.024, blue: 0.0), Color(red: 1.0, green: 0.18, blue: 0.18)], startPoint: .leading, endPoint: .trailing)
                                                )
                                        )
                                }
                                .buttonStyle(.plain)
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 11)
                            .background(
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .fill(Color.white.opacity(0.05))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                                            .stroke(
                                                isAdded ? Color(red: 0.882, green: 0.024, blue: 0.0).opacity(0.55) : Color.white.opacity(0.08),
                                                lineWidth: 1
                                            )
                                    )
                            )
                        }
                    }
                }
                .padding(.horizontal, 18)
                .padding(.bottom, 28)
            }
            .background(Color(red: 0.067, green: 0.078, blue: 0.118).ignoresSafeArea())
            .navigationTitle("Muscle Navigator")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.white)
                }
            }
        }
    }
}
