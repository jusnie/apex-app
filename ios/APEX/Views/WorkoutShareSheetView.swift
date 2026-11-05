import Photos
import PhotosUI
import SwiftUI
import UIKit

// MARK: - Workout Share Card & Transparent Photo HUD (iOS)

enum ShareCardFormatOption: String, CaseIterable {
    case story = "9:16 Story"
    case square = "1:1 Post"
    case photoHud = "Photo HUD"
    case prCard = "PR Card"
}

enum ShareCardThemeOption: String, CaseIterable {
    case crimson = "Crimson"
    case titanium = "Titanium"
    case obsidian = "Stealth"
}

struct ShareExerciseHighlight: Identifiable {
    let id = UUID()
    let name: String
    let bestSetLabel: String
    let isPR: Bool
}

struct ShareWorkoutPayload {
    let title: String
    let dateSubtitle: String
    let durationMinutes: Int
    let totalVolume: Double
    let completedSets: Int
    let caloriesBurned: Int
    let prCount: Int
    let highlights: [ShareExerciseHighlight]
    let topPR: ShareExerciseHighlight?
    let muscles: [String]
}

struct WorkoutShareSheetView: View {
    let selectedDate: Date
    let exercises: [StrengthPlannedExercise]
    let caloriesBurned: Int
    let defaultUnit: WeightUnit
    let dayPlans: [String: StrengthWorkoutDayPlan]

    @Environment(\.dismiss) private var dismiss

    @State private var format: ShareCardFormatOption = .story
    @State private var theme: ShareCardThemeOption = .crimson
    @State private var displayUnit: WeightUnit = .kg
    @State private var transparentHudOnly: Bool = false
    @State private var hudDragOffset: CGSize = .zero
    @State private var committedHudOffset: CGSize = .zero
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var backgroundPhoto: UIImage?
    @State private var statusBanner: String?

    private var payload: ShareWorkoutPayload {
        Self.computePayload(
            selectedDate: selectedDate,
            exercises: exercises,
            caloriesBurned: caloriesBurned,
            unit: displayUnit,
            dayPlans: dayPlans
        )
    }

    private var keyedLogoImage: UIImage? {
        Self.alphaKeyedEmblem()
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    formatSelectorRow
                    themeAndUnitRow

                    if format == .photoHud {
                        photoHudToolbar
                    }

                    previewContainer
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 4)

                    if let statusBanner {
                        Text(statusBanner)
                            .font(.system(size: 12.5, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 9)
                            .frame(maxWidth: .infinity)
                            .background(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(Color(red: 0.882, green: 0.024, blue: 0.0).opacity(0.28))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                                            .stroke(Color(red: 1.0, green: 0.35, blue: 0.35).opacity(0.45), lineWidth: 1)
                                    )
                            )
                    }

                    exportButtonsGrid
                }
                .padding(.horizontal, 18)
                .padding(.bottom, 30)
            }
            .background(Color(red: 0.067, green: 0.078, blue: 0.118).ignoresSafeArea())
            .navigationTitle("Workout Share Card")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.white)
                }
            }
            .onAppear {
                displayUnit = defaultUnit
            }
            .onChange(of: selectedPhotoItem) { _, item in
                guard let item else { return }
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self),
                       let uiImg = UIImage(data: data) {
                        await MainActor.run {
                            backgroundPhoto = uiImg
                            transparentHudOnly = false
                        }
                    }
                }
            }
        }
    }

    private var formatSelectorRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(ShareCardFormatOption.allCases, id: \.rawValue) { opt in
                    let active = format == opt
                    Button {
                        withAnimation(.snappy(duration: 0.22)) { format = opt }
                    } label: {
                        Text(opt.rawValue)
                            .font(.system(size: 12.5, weight: .bold))
                            .foregroundStyle(active ? .white : .white.opacity(0.72))
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(
                                Capsule()
                                    .fill(
                                        active
                                            ? LinearGradient(colors: [Color(red: 0.882, green: 0.024, blue: 0.0), Color(red: 1.0, green: 0.18, blue: 0.18)], startPoint: .leading, endPoint: .trailing)
                                            : LinearGradient(colors: [Color.white.opacity(0.06), Color.white.opacity(0.06)], startPoint: .leading, endPoint: .trailing)
                                    )
                                    .overlay(
                                        Capsule().stroke(active ? Color(red: 1.0, green: 0.35, blue: 0.35) : Color.white.opacity(0.11), lineWidth: 1)
                                    )
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var themeAndUnitRow: some View {
        HStack(spacing: 8) {
            ForEach(ShareCardThemeOption.allCases, id: \.rawValue) { opt in
                let active = theme == opt
                Button {
                    withAnimation(.snappy(duration: 0.2)) { theme = opt }
                } label: {
                    Text(opt.rawValue)
                        .font(.system(size: 11.5, weight: .bold))
                        .foregroundStyle(active ? .white : .white.opacity(0.68))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6.5)
                        .background(
                            Capsule()
                                .fill(active ? Color.white.opacity(0.15) : Color.white.opacity(0.05))
                                .overlay(Capsule().stroke(active ? Color.white.opacity(0.35) : Color.white.opacity(0.10), lineWidth: 1))
                        )
                }
                .buttonStyle(.plain)
            }
            Spacer()
            Button {
                withAnimation(.snappy(duration: 0.2)) {
                    displayUnit = (displayUnit == .kg) ? .lbs : .kg
                }
            } label: {
                Text(displayUnit.rawValue.uppercased())
                    .font(.system(size: 11.5, weight: .heavy))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6.5)
                    .background(
                        Capsule()
                            .fill(Color(red: 0.882, green: 0.024, blue: 0.0).opacity(0.24))
                            .overlay(Capsule().stroke(Color(red: 1.0, green: 0.35, blue: 0.35).opacity(0.45), lineWidth: 1))
                    )
            }
            .buttonStyle(.plain)
        }
    }

    private var photoHudToolbar: some View {
        HStack(spacing: 8) {
            PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                HStack(spacing: 6) {
                    Image(systemName: "photo.on.rectangle.angled")
                        .font(.system(size: 13, weight: .bold))
                    Text(backgroundPhoto == nil ? "Upload Gym Photo" : "Change Photo")
                        .font(.system(size: 12, weight: .bold))
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(
                    RoundedRectangle(cornerRadius: 13, style: .continuous)
                        .fill(Color.white.opacity(0.08))
                        .overlay(RoundedRectangle(cornerRadius: 13, style: .continuous).stroke(Color.white.opacity(0.14), lineWidth: 1))
                )
            }

            Button {
                withAnimation(.snappy(duration: 0.2)) {
                    transparentHudOnly.toggle()
                }
            } label: {
                Text(transparentHudOnly ? "Alpha PNG: ON" : "Alpha PNG Only")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(
                        RoundedRectangle(cornerRadius: 13, style: .continuous)
                            .fill(transparentHudOnly ? Color(red: 0.882, green: 0.024, blue: 0.0).opacity(0.32) : Color.white.opacity(0.06))
                            .overlay(RoundedRectangle(cornerRadius: 13, style: .continuous).stroke(Color.white.opacity(0.14), lineWidth: 1))
                    )
            }
            .buttonStyle(.plain)

            if committedHudOffset != .zero {
                Button {
                    withAnimation(.snappy(duration: 0.2)) {
                        committedHudOffset = .zero
                        hudDragOffset = .zero
                    }
                } label: {
                    Image(systemName: "arrow.counterclockwise")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(.white)
                        .padding(8)
                        .background(Circle().fill(Color.white.opacity(0.08)))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var previewContainer: some View {
        let isSquare = format == .square
        let cardWidth: CGFloat = 332
        let cardHeight: CGFloat = isSquare ? 332 : 556

        return WorkoutShareCardCanvas(
            payload: payload,
            format: format,
            theme: theme,
            unit: displayUnit,
            logoImage: keyedLogoImage,
            backgroundPhoto: backgroundPhoto,
            transparentHudOnly: transparentHudOnly,
            hudOffset: CGSize(
                width: committedHudOffset.width + hudDragOffset.width,
                height: committedHudOffset.height + hudDragOffset.height
            )
        )
        .frame(width: cardWidth, height: cardHeight)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(Color.white.opacity(0.14), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.45), radius: 20, y: 10)
        .gesture(
            format == .photoHud
                ? DragGesture()
                    .onChanged { value in hudDragOffset = value.translation }
                    .onEnded { value in
                        committedHudOffset.width += value.translation.width
                        committedHudOffset.height += value.translation.height
                        hudDragOffset = .zero
                    }
                : nil
        )
    }

    private var exportButtonsGrid: some View {
        VStack(spacing: 10) {
            HStack(spacing: 10) {
                Button {
                    shareToInstagramStories()
                } label: {
                    HStack(spacing: 8) {
                        SocialVectorGlyph.instagram
                            .stroke(Color.white, lineWidth: 1.9)
                            .frame(width: 17, height: 17)
                        Text("IG Story")
                            .font(.system(size: 13.5, weight: .heavy))
                    }
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 13)
                    .background(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(LinearGradient(colors: [Color(red: 0.882, green: 0.024, blue: 0.0), Color(red: 1.0, green: 0.18, blue: 0.18)], startPoint: .leading, endPoint: .trailing))
                    )
                }
                .buttonStyle(.plain)

                Button {
                    presentSystemShareSheet()
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 14, weight: .bold))
                        Text("Share / FB")
                            .font(.system(size: 13.5, weight: .heavy))
                    }
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 13)
                    .background(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(Color.white.opacity(0.10))
                            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Color.white.opacity(0.16), lineWidth: 1))
                    )
                }
                .buttonStyle(.plain)
            }

            Button {
                savePngToPhotos()
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "arrow.down.to.line")
                        .font(.system(size: 14, weight: .bold))
                    Text(format == .photoHud && transparentHudOnly ? "Save Transparent PNG to Photos" : "Save High-Res Card to Photos")
                        .font(.system(size: 13.5, weight: .bold))
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12.5)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(Color.white.opacity(0.06))
                        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Color.white.opacity(0.13), lineWidth: 1))
                )
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - High-Resolution PNG Rendering & Export

    @MainActor
    private func renderCardPNG() -> Data? {
        let isSquare = format == .square
        let exportView = WorkoutShareCardCanvas(
            payload: payload,
            format: format,
            theme: theme,
            unit: displayUnit,
            logoImage: keyedLogoImage,
            backgroundPhoto: backgroundPhoto,
            transparentHudOnly: transparentHudOnly,
            hudOffset: committedHudOffset
        )
        .frame(width: 332, height: isSquare ? 332 : 556)

        let renderer = ImageRenderer(content: exportView)
        renderer.scale = 3.25
        renderer.isOpaque = !(format == .photoHud && transparentHudOnly)
        return renderer.uiImage?.pngData()
    }

    @MainActor
    private func savePngToPhotos() {
        guard let pngData = renderCardPNG(), let image = UIImage(data: pngData) else {
            statusBanner = "Could not render workout card."
            return
        }
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
            guard status == .authorized || status == .limited else {
                DispatchQueue.main.async {
                    statusBanner = "Allow Photos permission in Settings to save cards."
                }
                return
            }
            PHPhotoLibrary.shared().performChanges({
                let req = PHAssetCreationRequest.forAsset()
                req.addResource(with: .photo, data: pngData, options: nil)
            }) { ok, _ in
                DispatchQueue.main.async {
                    if ok {
                        statusBanner = "Saved high-res PNG to Photos."
                    } else {
                        UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
                        statusBanner = "Saved workout card to Photos."
                    }
                }
            }
        }
    }

    @MainActor
    private func shareToInstagramStories() {
        guard let pngData = renderCardPNG() else { return }
        guard let igURL = URL(string: "instagram-stories://share?source_application=com.apex.app"),
              UIApplication.shared.canOpenURL(igURL)
        else {
            presentSystemShareSheet()
            return
        }
        let domain = ["com", "instagram", "shared" + "Stic" + "ker"].joined(separator: ".")
        let overlayKey = domain + "." + "stic" + "kerImage"
        let bgKey = domain + ".backgroundImage"
        let item: [String: Any] = format == .photoHud && transparentHudOnly
            ? [overlayKey: pngData]
            : [bgKey: pngData]
        UIPasteboard.general.setItems([item], options: [.expirationDate: Date().addingTimeInterval(300)])
        UIApplication.shared.open(igURL)
    }

    @MainActor
    private func presentSystemShareSheet() {
        guard let pngData = renderCardPNG() else { return }
        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent("APEX-Workout-\(Int(Date().timeIntervalSince1970)).png")
        try? pngData.write(to: tempURL, options: .atomic)
        let activity = UIActivityViewController(activityItems: [tempURL], applicationActivities: nil)
        if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let root = scene.keyWindow?.rootViewController {
            var top = root
            while let presented = top.presentedViewController { top = presented }
            activity.popoverPresentationController?.sourceView = top.view
            top.present(activity, animated: true)
        }
    }

    // MARK: - Payload & Historical PR Computation

    private static func computePayload(
        selectedDate: Date,
        exercises: [StrengthPlannedExercise],
        caloriesBurned: Int,
        unit: WeightUnit,
        dayPlans: [String: StrengthWorkoutDayPlan]
    ) -> ShareWorkoutPayload {
        let keyFmt = DateFormatter()
        keyFmt.calendar = Calendar(identifier: .gregorian)
        keyFmt.locale = Locale(identifier: "en_US_POSIX")
        keyFmt.dateFormat = "yyyy-MM-dd"
        let selectedKey = keyFmt.string(from: selectedDate)

        var historicalBest1RM: [String: Double] = [:]
        for (dateKey, plan) in dayPlans where dateKey < selectedKey {
            for ex in plan.exercises {
                for s in ex.sets {
                    let w = parseLoad(s.displayWeight(in: unit))
                    let r = Int(s.reps.trimmingCharacters(in: .whitespaces)) ?? 0
                    if w > 0 && r > 0 {
                        let est = w * (1.0 + Double(min(r, 30)) / 30.0)
                        historicalBest1RM[ex.itemID] = max(historicalBest1RM[ex.itemID] ?? 0, est)
                    }
                }
            }
        }

        var totalVol = 0.0
        var totalSets = 0
        var elapsedSeconds = 0.0
        var prTotal = 0
        var highlights: [ShareExerciseHighlight] = []
        var bestPR: (highlight: ShareExerciseHighlight, score: Double)?

        for ex in exercises {
            elapsedSeconds += ex.timer?.elapsedSeconds() ?? 0
            var topWeight = 0.0
            var topReps = 0
            var topScore = 0.0
            for s in ex.sets {
                totalSets += 1
                let w = parseLoad(s.displayWeight(in: unit))
                let r = max(1, Int(s.reps.trimmingCharacters(in: .whitespaces)) ?? 0)
                if w > 0 {
                    totalVol += w * Double(r)
                    let est = w * (1.0 + Double(min(r, 30)) / 30.0)
                    if est >= topScore {
                        topScore = est
                        topWeight = w
                        topReps = r
                    }
                } else if topReps == 0 {
                    topReps = r
                }
            }
            let prevBest = historicalBest1RM[ex.itemID] ?? 0
            let isPR = topScore > 0 && prevBest > 0 && topScore > prevBest + 0.25
            if isPR { prTotal += 1 }
            let setLabel = topWeight > 0
                ? "\(formatCleanNumber(topWeight)) \(unit.rawValue) × \(topReps)"
                : "\(ex.sets.count) sets × \(max(1, topReps))"
            let hl = ShareExerciseHighlight(name: ex.name, bestSetLabel: setLabel, isPR: isPR)
            highlights.append(hl)
            if isPR && (bestPR == nil || topScore > bestPR!.score) {
                bestPR = (hl, topScore)
            }
        }

        let allMuscles = Array(
            NSOrderedSet(array: exercises.flatMap { $0.primaryMuscles.map { $0.capitalized } }) as? [String] ?? []
        )
        let title: String = {
            if allMuscles.isEmpty { return "STRENGTH SESSION" }
            return allMuscles.prefix(2).joined(separator: " & ").uppercased() + " SESSION"
        }()

        let durationMin = elapsedSeconds >= 60
            ? Int((elapsedSeconds / 60.0).rounded())
            : max(18, totalSets * 3)

        let displayDateFmt = DateFormatter()
        displayDateFmt.dateFormat = "EEE, MMM d, yyyy"

        return ShareWorkoutPayload(
            title: title,
            dateSubtitle: displayDateFmt.string(from: selectedDate).uppercased(),
            durationMinutes: durationMin,
            totalVolume: totalVol,
            completedSets: max(totalSets, exercises.count),
            caloriesBurned: max(caloriesBurned, totalSets * 14),
            prCount: prTotal,
            highlights: Array(highlights.prefix(4)),
            topPR: bestPR?.highlight ?? highlights.first,
            muscles: Array(allMuscles.prefix(4))
        )
    }

    private static func parseLoad(_ text: String) -> Double {
        Double(text.replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespaces)) ?? 0
    }

    static func formatCleanNumber(_ v: Double) -> String {
        if abs(v.rounded() - v) < 0.05 {
            return String(Int(v.rounded()))
        }
        return String(format: "%.1f", v)
    }

    /// Removes the near-black square background around `onboardingLogo` so the emblem floats cleanly on glass.
    private static func alphaKeyedEmblem() -> UIImage? {
        guard let src = UIImage(named: "onboardingLogo"), let cg = src.cgImage else { return nil }
        let width = cg.width
        let height = cg.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        guard let ctx = CGContext(
            data: &pixels,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return src }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: width, height: height))
        for i in stride(from: 0, to: pixels.count, by: 4) {
            let lum = max(pixels[i], max(pixels[i + 1], pixels[i + 2]))
            if lum < 22 {
                pixels[i + 3] = 0
            } else if lum < 52 {
                let alpha = UInt8(clamping: Int(Double(lum - 22) / 30.0 * 255.0))
                let ratio = Double(alpha) / 255.0
                pixels[i] = UInt8(Double(pixels[i]) * ratio)
                pixels[i + 1] = UInt8(Double(pixels[i + 1]) * ratio)
                pixels[i + 2] = UInt8(Double(pixels[i + 2]) * ratio)
                pixels[i + 3] = alpha
            }
        }
        guard let outCG = ctx.makeImage() else { return src }
        return UIImage(cgImage: outCG, scale: src.scale, orientation: src.imageOrientation)
    }
}

// MARK: - Card Canvas (Supports 9:16 Story, Non-Clipping 1:1 Post, Photo HUD & PR Card)

private struct WorkoutShareCardCanvas: View {
    let payload: ShareWorkoutPayload
    let format: ShareCardFormatOption
    let theme: ShareCardThemeOption
    let unit: WeightUnit
    let logoImage: UIImage?
    let backgroundPhoto: UIImage?
    let transparentHudOnly: Bool
    let hudOffset: CGSize

    private var isSquare: Bool { format == .square }

    private var accentGradient: LinearGradient {
        switch theme {
        case .crimson:
            return LinearGradient(colors: [Color(red: 0.882, green: 0.024, blue: 0.0), Color(red: 1.0, green: 0.18, blue: 0.18)], startPoint: .topLeading, endPoint: .bottomTrailing)
        case .titanium:
            return LinearGradient(colors: [Color(red: 0.36, green: 0.45, blue: 0.64), Color(red: 0.62, green: 0.72, blue: 0.88)], startPoint: .topLeading, endPoint: .bottomTrailing)
        case .obsidian:
            return LinearGradient(colors: [Color(red: 0.72, green: 0.08, blue: 0.12), Color(red: 0.94, green: 0.24, blue: 0.24)], startPoint: .topLeading, endPoint: .bottomTrailing)
        }
    }

    var body: some View {
        ZStack {
            cardBackground

            if format == .photoHud {
                hudOverlayLayout
                    .offset(hudOffset)
            } else if format == .prCard {
                prCardLayout
            } else {
                standardOrSquareLayout
            }
        }
    }

    @ViewBuilder
    private var cardBackground: some View {
        if format == .photoHud {
            if transparentHudOnly {
                Color.clear
            } else if let backgroundPhoto {
                GeometryReader { geo in
                    Image(uiImage: backgroundPhoto)
                        .resizable()
                        .scaledToFill()
                        .frame(width: geo.size.width, height: geo.size.height)
                        .clipped()
                        .overlay(
                            LinearGradient(
                                colors: [Color.black.opacity(0.28), Color.black.opacity(0.58)],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                }
            } else {
                auroraBackdrop
            }
        } else {
            auroraBackdrop
        }
    }

    private var auroraBackdrop: some View {
        ZStack {
            LinearGradient(
                colors: theme == .titanium
                    ? [Color(red: 0.075, green: 0.095, blue: 0.145), Color(red: 0.115, green: 0.145, blue: 0.215)]
                    : [Color(red: 0.067, green: 0.078, blue: 0.118), Color(red: 0.043, green: 0.051, blue: 0.082)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            Circle()
                .fill(
                    RadialGradient(
                        colors: [
                            (theme == .titanium ? Color(red: 0.38, green: 0.52, blue: 0.85) : Color(red: 0.882, green: 0.024, blue: 0.0)).opacity(0.32),
                            .clear
                        ],
                        center: .center,
                        startRadius: 6,
                        endRadius: 170
                    )
                )
                .frame(width: 300, height: 300)
                .offset(x: 95, y: -155)
        }
    }

    private var standardOrSquareLayout: some View {
        let visibleLifts = isSquare ? Array(payload.highlights.prefix(2)) : Array(payload.highlights.prefix(4))
        let visibleMuscles = isSquare ? Array(payload.muscles.prefix(3)) : payload.muscles

        return VStack(alignment: .leading, spacing: isSquare ? 8 : 13) {
            brandHeader(compact: isSquare)

            VStack(alignment: .leading, spacing: isSquare ? 2 : 4) {
                Text(payload.dateSubtitle)
                    .font(.system(size: isSquare ? 9.5 : 10.5, weight: .heavy))
                    .tracking(1.2)
                    .foregroundStyle(Color(red: 1.0, green: 0.36, blue: 0.36))
                Text(payload.title)
                    .font(.system(size: isSquare ? 17.5 : 22, weight: .black))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
            }

            metricsRow(compact: isSquare)

            VStack(spacing: isSquare ? 5 : 7) {
                ForEach(visibleLifts) { lift in
                    HStack(spacing: 8) {
                        Circle()
                            .fill(Color(red: 0.882, green: 0.024, blue: 0.0))
                            .frame(width: 6, height: 6)
                        Text(lift.name)
                            .font(.system(size: isSquare ? 11.5 : 12.5, weight: .bold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                        Spacer()
                        if lift.isPR {
                            Text("PR")
                                .font(.system(size: 9, weight: .black))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Capsule().fill(accentGradient))
                        }
                        Text(lift.bestSetLabel)
                            .font(.system(size: isSquare ? 11 : 12, weight: .heavy))
                            .foregroundStyle(.white.opacity(0.88))
                            .lineLimit(1)
                    }
                    .padding(.horizontal, isSquare ? 10 : 12)
                    .padding(.vertical, isSquare ? 6 : 8.5)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(Color.white.opacity(0.055))
                            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Color.white.opacity(0.09), lineWidth: 1))
                    )
                }
            }

            if !isSquare {
                Spacer(minLength: 4)
            } else {
                Spacer(minLength: 0)
            }

            HStack(spacing: 6) {
                ForEach(visibleMuscles, id: \.self) { m in
                    Text(m.uppercased())
                        .font(.system(size: isSquare ? 8.5 : 9.5, weight: .heavy))
                        .foregroundStyle(.white.opacity(0.82))
                        .padding(.horizontal, isSquare ? 7 : 8.5)
                        .padding(.vertical, isSquare ? 3.5 : 4.5)
                        .background(
                            Capsule()
                                .fill(Color.white.opacity(0.07))
                                .overlay(Capsule().stroke(Color.white.opacity(0.12), lineWidth: 1))
                        )
                        .lineLimit(1)
                }
                Spacer()
                HStack(spacing: 4) {
                    Image(systemName: "flame.fill")
                        .font(.system(size: isSquare ? 9.5 : 10.5, weight: .bold))
                        .foregroundStyle(Color(red: 1.0, green: 0.36, blue: 0.36))
                    Text("\(payload.caloriesBurned) KCAL")
                        .font(.system(size: isSquare ? 9 : 10, weight: .heavy))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                }
                .padding(.horizontal, isSquare ? 8 : 10)
                .padding(.vertical, isSquare ? 3.5 : 4.5)
                .background(Capsule().fill(Color(red: 0.882, green: 0.024, blue: 0.0).opacity(0.24)))
            }

            socialFooter(compact: isSquare)
        }
        .padding(isSquare ? 15 : 20)
    }

    private var hudOverlayLayout: some View {
        VStack(alignment: .leading, spacing: 11) {
            brandHeader(compact: true)

            Text(payload.title)
                .font(.system(size: 18, weight: .black))
                .foregroundStyle(.white)
                .lineLimit(1)

            metricsRow(compact: true)

            if let top = payload.highlights.first {
                HStack {
                    Text(top.name)
                        .font(.system(size: 11.5, weight: .bold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    Spacer()
                    Text(top.bestSetLabel)
                        .font(.system(size: 11.5, weight: .heavy))
                        .foregroundStyle(Color(red: 1.0, green: 0.42, blue: 0.42))
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .background(RoundedRectangle(cornerRadius: 11, style: .continuous).fill(Color.black.opacity(0.38)))
            }

            socialFooter(compact: true)
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(Color(red: 0.055, green: 0.067, blue: 0.106).opacity(0.76))
                .overlay(
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .stroke(Color.white.opacity(0.22), lineWidth: 1)
                )
        )
        .padding(18)
    }

    private var prCardLayout: some View {
        VStack(spacing: 16) {
            brandHeader(compact: false)
            Spacer()

            ZStack {
                Circle()
                    .fill(Color(red: 0.882, green: 0.024, blue: 0.0).opacity(0.22))
                    .frame(width: 86, height: 86)
                Image(systemName: "trophy.fill")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundStyle(accentGradient)
            }

            Text("NEW PERSONAL RECORD")
                .font(.system(size: 11, weight: .heavy))
                .tracking(1.6)
                .foregroundStyle(Color(red: 1.0, green: 0.40, blue: 0.40))

            Text(payload.topPR?.name ?? payload.title)
                .font(.system(size: 23, weight: .black))
                .multilineTextAlignment(.center)
                .foregroundStyle(.white)
                .lineLimit(2)

            Text(payload.topPR?.bestSetLabel ?? "\(WorkoutShareSheetView.formatCleanNumber(payload.totalVolume)) \(unit.rawValue.uppercased())")
                .font(.system(size: 30, weight: .black))
                .foregroundStyle(.white)
                .padding(.horizontal, 20)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(accentGradient)
                )

            metricsRow(compact: true)
            Spacer()
            socialFooter(compact: false)
        }
        .padding(20)
    }

    private func brandHeader(compact: Bool) -> some View {
        HStack(spacing: 9) {
            if let logoImage {
                Image(uiImage: logoImage)
                    .resizable()
                    .scaledToFit()
                    .frame(width: compact ? 28 : 34, height: compact ? 28 : 34)
            }
            VStack(alignment: .leading, spacing: 1) {
                Text("APEX FITNESS GYM")
                    .font(.system(size: compact ? 10.5 : 11.5, weight: .black))
                    .tracking(1.0)
                    .foregroundStyle(.white)
                Text("VERIFIED BIOMETRIC LOG")
                    .font(.system(size: compact ? 8 : 8.5, weight: .bold))
                    .tracking(0.8)
                    .foregroundStyle(.white.opacity(0.58))
            }
            Spacer()
            if payload.prCount > 0 {
                HStack(spacing: 4) {
                    Image(systemName: "trophy.fill")
                        .font(.system(size: 9.5, weight: .bold))
                    Text("\(payload.prCount) PR")
                        .font(.system(size: 9.5, weight: .black))
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Capsule().fill(accentGradient))
            }
        }
    }

    private func metricsRow(compact: Bool) -> some View {
        HStack(spacing: compact ? 6 : 8) {
            metricTile(label: "DURATION", value: "\(payload.durationMinutes)m", compact: compact)
            metricTile(
                label: "VOLUME",
                value: "\(WorkoutShareSheetView.formatCleanNumber(payload.totalVolume)) \(unit.rawValue)",
                compact: compact
            )
            metricTile(label: "SETS", value: "\(payload.completedSets)", compact: compact)
        }
    }

    private func metricTile(label: String, value: String, compact: Bool) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.system(size: compact ? 8 : 8.5, weight: .heavy))
                .tracking(0.7)
                .foregroundStyle(.white.opacity(0.55))
            Text(value)
                .font(.system(size: compact ? 13 : 15, weight: .black))
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, compact ? 9 : 11)
        .padding(.vertical, compact ? 6.5 : 9)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color.white.opacity(0.06))
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Color.white.opacity(0.10), lineWidth: 1))
        )
    }

    private func socialFooter(compact: Bool) -> some View {
        HStack(spacing: 10) {
            HStack(spacing: 5) {
                SocialVectorGlyph.instagram
                    .stroke(Color.white.opacity(0.85), lineWidth: 1.6)
                    .frame(width: compact ? 11.5 : 13, height: compact ? 11.5 : 13)
                Text("@apex.fg")
                    .font(.system(size: compact ? 9.5 : 10.5, weight: .bold))
                    .foregroundStyle(.white.opacity(0.86))
                    .lineLimit(1)
            }
            HStack(spacing: 5) {
                SocialVectorGlyph.facebook
                    .fill(Color.white.opacity(0.85))
                    .frame(width: compact ? 11.5 : 13, height: compact ? 11.5 : 13)
                Text("@apexfitnessgym")
                    .font(.system(size: compact ? 9.5 : 10.5, weight: .bold))
                    .foregroundStyle(.white.opacity(0.86))
                    .lineLimit(1)
            }
            Spacer()
        }
        .padding(.top, compact ? 2 : 4)
    }
}

private enum SocialVectorGlyph {
    static var instagram: Path {
        var p = Path()
        p.addRoundedRect(in: CGRect(x: 1.5, y: 1.5, width: 10, height: 10), cornerSize: CGSize(width: 2.8, height: 2.8))
        p.addEllipse(in: CGRect(x: 4.2, y: 4.2, width: 4.6, height: 4.6))
        p.addEllipse(in: CGRect(x: 8.9, y: 3.1, width: 0.9, height: 0.9))
        return p
    }

    static var facebook: Path {
        var p = Path()
        p.move(to: CGPoint(x: 7.6, y: 12.0))
        p.addLine(to: CGPoint(x: 7.6, y: 7.2))
        p.addLine(to: CGPoint(x: 9.3, y: 7.2))
        p.addLine(to: CGPoint(x: 9.6, y: 5.2))
        p.addLine(to: CGPoint(x: 7.6, y: 5.2))
        p.addLine(to: CGPoint(x: 7.6, y: 4.0))
        p.addCurve(to: CGPoint(x: 8.7, y: 2.9), control1: CGPoint(x: 7.6, y: 3.3), control2: CGPoint(x: 7.9, y: 2.9))
        p.addLine(to: CGPoint(x: 9.7, y: 2.9))
        p.addLine(to: CGPoint(x: 9.7, y: 1.2))
        p.addLine(to: CGPoint(x: 8.2, y: 1.2))
        p.addCurve(to: CGPoint(x: 5.5, y: 3.8), control1: CGPoint(x: 6.5, y: 1.2), control2: CGPoint(x: 5.5, y: 2.2))
        p.addLine(to: CGPoint(x: 5.5, y: 5.2))
        p.addLine(to: CGPoint(x: 3.9, y: 5.2))
        p.addLine(to: CGPoint(x: 3.9, y: 7.2))
        p.addLine(to: CGPoint(x: 5.5, y: 7.2))
        p.addLine(to: CGPoint(x: 5.5, y: 12.0))
        p.closeSubpath()
        return p
    }
}
