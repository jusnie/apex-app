import PhotosUI
import SwiftUI

/// The root of the assistant's window: the live session (expanded or compact),
/// or the screens shown when it can't run.
struct VoiceAssistantOverlay: View {
    @Bindable var controller: VoiceAssistantController
    /// Reports where the assistant's controls are, and whether it covers the app.
    let reportRegions: ([CGRect], Bool) -> Void
    let onStatusBarStyle: (UIStatusBarStyle) -> Void

    @AppStorage(AppThemeColor.storageKey) private var appThemeColorRaw = AppThemeColor.defaultColor.rawValue
    @AppStorage("appearanceMode") private var appearanceMode = "dark"

    private var coversApp: Bool {
        switch controller.presentation {
        case .hidden: false
        case .session: controller.isExpanded
        case .access, .outOfMinutes, .failed: true
        }
    }

    private var colorScheme: ColorScheme? {
        switch appearanceMode {
        case "light": .light
        case "dark": .dark
        default: nil
        }
    }

    var body: some View {
        content
            .photosPicker(
                isPresented: $controller.photoPickerPresented,
                selection: $controller.photoPickerItems,
                maxSelectionCount: controller.photoPickerLimit,
                selectionBehavior: .ordered,
                matching: .images
            )
            .onChange(of: controller.photoPickerItems) { _, items in
                controller.photosPicked(items)
            }
            .onChange(of: controller.photoPickerPresented) { _, presented in
                if !presented { controller.photoPickerDismissed() }
            }
            .onPreferenceChange(VoiceAssistantRegionsKey.self) { regions in
                reportRegions(regions, coversApp)
            }
            .onChange(of: coversApp, initial: true) { _, covers in
                onStatusBarStyle(covers ? .lightContent : .default)
            }
            .tint(AppThemeColor.color(for: appThemeColorRaw).color)
            .preferredColorScheme(colorScheme)
            .animation(.spring(response: 0.42, dampingFraction: 0.86), value: controller.isExpanded)
            .animation(.easeInOut(duration: 0.25), value: controller.presentation)
    }

    @ViewBuilder
    private var content: some View {
        switch controller.presentation {
        case .hidden:
            Color.clear
        case .session:
            if controller.isExpanded {
                VoiceAssistantExpandedView(controller: controller)
                    .transition(.opacity)
            } else {
                VoiceAssistantCompactView(controller: controller)
                    .transition(.opacity)
            }
        case .access(let reason):
            VoiceAssistantPanel(controller: controller) {
                VoiceAssistantAccessContent(controller: controller, reason: reason)
            }
        case .outOfMinutes:
            VoiceAssistantPanel(controller: controller) {
                VoiceAssistantOutOfMinutesContent(controller: controller)
            }
        case .failed(let failure):
            VoiceAssistantPanel(controller: controller) {
                VoiceAssistantFailureContent(controller: controller, failure: failure)
            }
        }
    }
}

// MARK: - Expanded

/// The full-screen assistant: a large orb, live captions, the camera when open,
/// and cards.
private struct VoiceAssistantExpandedView: View {
    @Bindable var controller: VoiceAssistantController

    var body: some View {
        ZStack {
            ZStack {
                Color(red: 0.015, green: 0.015, blue: 0.022)
                    .opacity(0.96)
                RadialGradient(
                    colors: [
                        AppColors.calorie.opacity(0.16),
                        Color.clear
                    ],
                    center: .center,
                    startRadius: 20,
                    endRadius: 340
                )
                TacticalHudCornerBrackets(color: AppColors.calorie.opacity(0.36))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
            }
            .ignoresSafeArea()
            .accessibilityHidden(true)

            VStack(spacing: 14) {
                header

                if controller.cameraMode != nil {
                    VoiceAssistantCameraPanel(controller: controller)
                        .padding(.horizontal, 20)
                    HStack(spacing: 14) {
                        orb(size: 76)
                        VoiceAssistantCaptions(controller: controller, compact: true)
                    }
                    .padding(.horizontal, 20)
                } else {
                    Spacer(minLength: 0)
                    orb(size: 300)
                    VoiceAssistantCaptions(controller: controller, compact: false)
                        .padding(.horizontal, 28)
                    Spacer(minLength: 0)
                }

                VoiceAssistantCardStack(controller: controller, maximumCards: controller.cameraMode == nil ? 3 : 2)
                    .padding(.horizontal, 16)

                if let banner = controller.banner {
                    Text(banner)
                        .font(.system(.footnote, design: .rounded, weight: .medium))
                        .foregroundStyle(.white.opacity(0.75))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                        .transition(.opacity)
                }
            }
            .padding(.bottom, 12)
        }
        .environment(\.colorScheme, .dark)
        .accessibilityAction(.escape) { controller.minimize() }
    }

    private var header: some View {
        HStack {
            Button {
                controller.minimize()
            } label: {
                Image(systemName: "chevron.down")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(.white.opacity(0.9))
                    .frame(width: 44, height: 44)
                    .background(Color.white.opacity(0.06), in: Circle())
                    .overlay(Circle().strokeBorder(Color.white.opacity(0.16), lineWidth: 0.8))
            }
            .accessibilityLabel(Text("Minimize"))
            .accessibilityIdentifier("voiceAssistant.minimize")

            Spacer()

            VoiceAssistantStatusLabel(controller: controller)

            Spacer()

            Button {
                controller.dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(.white.opacity(0.9))
                    .frame(width: 44, height: 44)
                    .background(AppColors.calorie.opacity(0.18), in: Circle())
                    .overlay(Circle().strokeBorder(AppColors.calorie.opacity(0.55), lineWidth: 0.9))
            }
            .accessibilityLabel(Text("End conversation"))
            .accessibilityIdentifier("voiceAssistant.end")
        }
        .padding(.horizontal, 16)
        .padding(.top, 4)
    }

    private func orb(size: CGFloat) -> some View {
        LiveOrbView(
            mode: controller.orbMode,
            levelSource: { controller.currentLevel() },
            tint: AppColors.calorie,
            clearBackground: true
        )
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// Tactical Jarvis HUD telemetry pill ("APEX // Listening…").
private struct VoiceAssistantStatusLabel: View {
    let controller: VoiceAssistantController

    var body: some View {
        VStack(spacing: 3) {
            HStack(spacing: 7) {
                Circle()
                    .fill(AppColors.calorie)
                    .frame(width: 6, height: 6)
                    .shadow(color: AppColors.calorie.opacity(0.9), radius: 4)
                Text("APEX")
                    .font(.system(size: 11, weight: .heavy, design: .monospaced))
                    .tracking(1.6)
                    .foregroundStyle(AppColors.calorie)
                Text("//")
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .foregroundStyle(.white.opacity(0.35))
                Text(VoiceAssistantStatusText.text(for: controller).uppercased())
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .tracking(0.8)
                    .foregroundStyle(.white.opacity(0.94))
            }
            .padding(.horizontal, 13)
            .padding(.vertical, 6)
            .background(Color.white.opacity(0.05), in: Capsule())
            .overlay(Capsule().strokeBorder(AppColors.calorie.opacity(0.42), lineWidth: 0.8))

            if let detail {
                Text(detail.uppercased())
                    .font(.system(size: 9.5, weight: .semibold, design: .monospaced))
                    .tracking(1.1)
                    .foregroundStyle(.white.opacity(0.45))
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.updatesFrequently)
    }

    private var detail: String? {
        guard let provider = controller.provider else { return nil }
        if provider == .hosted, let seconds = controller.hostedSecondsRemaining {
            let minutes = max(0, seconds / 60)
            return String(localized: "\(minutes) min left")
        }
        return provider.localizedName
    }
}

enum VoiceAssistantStatusText {
    static func text(for controller: VoiceAssistantController) -> String {
        if controller.phase == .connecting { return String(localized: "Connecting…") }
        switch controller.orbMode {
        case .idle: return String(localized: "Connecting…")
        case .listening: return String(localized: "Listening…")
        case .thinking: return String(localized: "Thinking…")
        case .speaking: return String(localized: "Speaking…")
        }
    }
}

/// What the user said and what the assistant is saying.
private struct VoiceAssistantCaptions: View {
    let controller: VoiceAssistantController
    let compact: Bool

    var body: some View {
        VStack(alignment: compact ? .leading : .center, spacing: 8) {
            if !controller.userCaption.isEmpty {
                Text(controller.userCaption)
                    .font(.system(compact ? .footnote : .callout, design: .rounded))
                    .foregroundStyle(.white.opacity(0.55))
                    .lineLimit(2)
                    .truncationMode(.head)
                    .accessibilityLabel(Text("You said: \(controller.userCaption)"))
            }
            if !controller.assistantCaption.isEmpty {
                Text(controller.assistantCaption)
                    .font(.system(compact ? .subheadline : .title3, design: .rounded, weight: .medium))
                    .foregroundStyle(.white.opacity(0.92))
                    .lineLimit(compact ? 3 : 5)
                    .truncationMode(.head)
            }
        }
        .multilineTextAlignment(compact ? .leading : .center)
        .frame(maxWidth: .infinity, alignment: compact ? .leading : .center)
        .contentTransition(.opacity)
        .animation(.easeInOut(duration: 0.2), value: controller.assistantCaption)
        .accessibilityElement(children: .contain)
    }
}

// MARK: - Compact

/// A floating orb bubble with a caption pill above the tab bar, so the app stays
/// visible while the assistant navigates.
private struct VoiceAssistantCompactView: View {
    @Bindable var controller: VoiceAssistantController

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Spacer(minLength: 0)
            VoiceAssistantCardStack(controller: controller, maximumCards: 2)
                .frame(maxWidth: 360, alignment: .leading)
            HStack(alignment: .bottom, spacing: 10) {
                Button {
                    controller.isExpanded = true
                } label: {
                    VoiceAssistantOrbBubble(controller: controller)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("Voice assistant"))
                .accessibilityValue(Text(VoiceAssistantStatusText.text(for: controller)))
                .accessibilityHint(Text("Expands the assistant"))
                .accessibilityIdentifier("voiceAssistant.bubble")
                .voiceAssistantInteractive()

                captionPill
                    .voiceAssistantInteractive()
            }
        }
        .padding(.leading, 24)
        .padding(.trailing, 96)
        .padding(.bottom, controller.navigator.contentBottomInset + 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
        .ignoresSafeArea(edges: .bottom)
    }

    private var captionText: String {
        if controller.isAssistantSpeaking || controller.userCaption.isEmpty {
            if !controller.assistantCaption.isEmpty { return controller.assistantCaption }
        }
        if !controller.userCaption.isEmpty { return controller.userCaption }
        return VoiceAssistantStatusText.text(for: controller)
    }

    private var captionPill: some View {
        HStack(alignment: .center, spacing: 8) {
            Circle()
                .fill(AppColors.calorie)
                .frame(width: 6, height: 6)
                .shadow(color: AppColors.calorie.opacity(0.85), radius: 3)
                .accessibilityHidden(true)
            Text(captionText)
                .font(.system(.footnote, design: .rounded, weight: .semibold))
                .foregroundStyle(.white)
                .lineLimit(2)
                .truncationMode(.head)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentTransition(.opacity)
            Button {
                controller.dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.white.opacity(0.85))
                    .frame(width: 28, height: 28)
                    .background(AppColors.calorie.opacity(0.22), in: Circle())
                    .overlay(Circle().strokeBorder(AppColors.calorie.opacity(0.55), lineWidth: 0.8))
            }
            .accessibilityLabel(Text("End conversation"))
            .accessibilityIdentifier("voiceAssistant.compactEnd")
        }
        .padding(.leading, 14)
        .padding(.trailing, 8)
        .padding(.vertical, 8)
        .frame(minHeight: 44)
        .background(Color(red: 0.04, green: 0.04, blue: 0.06).opacity(0.94), in: Capsule())
        .overlay(Capsule().strokeBorder(AppColors.calorie.opacity(0.38), lineWidth: 0.9))
        .shadow(color: .black.opacity(0.35), radius: 12, y: 5)
    }
}

/// The floating tactical reactor core in the compact assistant.
struct VoiceAssistantOrbBubble: View {
    let controller: VoiceAssistantController
    var size: CGFloat = 60

    var body: some View {
        ZStack {
            Circle()
                .fill(Color(red: 0.03, green: 0.03, blue: 0.05).opacity(0.90))
            LiveOrbView(
                mode: controller.orbMode,
                levelSource: { controller.currentLevel() },
                tint: AppColors.calorie,
                clearBackground: true
            )
            .scaleEffect(1 / 0.78)
            Circle()
                .strokeBorder(AppColors.calorie.opacity(0.55), lineWidth: 1.0)
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .shadow(color: AppColors.calorie.opacity(0.32), radius: 8, y: 2)
        .contentShape(Circle())
    }
}

// MARK: - Cards

private struct VoiceAssistantCardStack: View {
    let controller: VoiceAssistantController
    let maximumCards: Int

    var body: some View {
        VStack(spacing: 8) {
            ForEach(controller.cards.cards.suffix(maximumCards)) { card in
                VoiceAssistantCardView(controller: controller, card: card)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .voiceAssistantInteractive()
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: controller.cards.cards.map(\.id))
    }
}

private struct VoiceAssistantCardView: View {
    let controller: VoiceAssistantController
    let card: VoiceAssistantCard
    @AppStorage(EnergyUnit.storageKey) private var energyUnit = EnergyUnit.defaultUnit

    var body: some View {
        Group {
            switch card.content {
            case .draft(let draft):
                draftCard(draft)
            case .confirmation(let confirmation):
                confirmationCard(confirmation)
            case .undo(let undo):
                undoCard(undo)
            case .notice(let notice):
                noticeCard(notice)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .shadow(color: .black.opacity(0.12), radius: 8, y: 3)
    }

    private func draftCard(_ draft: VoiceAssistantCard.Draft) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 12) {
                if let thumbnail = draft.thumbnail {
                    Image(uiImage: thumbnail)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 48, height: 48)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                        .accessibilityHidden(true)
                } else {
                    Image(systemName: draft.isWorkout ? "figure.strengthtraining.traditional" : "fork.knife")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(AppColors.calorie)
                        .frame(width: 48, height: 48)
                        .background(AppColors.calorie.opacity(0.14), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                        .accessibilityHidden(true)
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text(draft.title)
                        .font(.system(.subheadline, design: .rounded, weight: .semibold))
                        .lineLimit(2)
                    ForEach(Array(draft.lines.prefix(4).enumerated()), id: \.offset) { _, line in
                        Text(line)
                            .font(.system(.caption, design: .rounded))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    if draft.lines.count > 4 {
                        Text("+\(draft.lines.count - 4) more")
                            .font(.system(.caption, design: .rounded))
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer(minLength: 0)
            }
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 1) {
                    if let calories = draft.calories {
                        Text(draft.isWorkout ? String(localized: "≈ \(energyUnit.format(kcal: calories)) burned") : energyUnit.format(kcal: calories))
                            .font(.system(.subheadline, design: .rounded, weight: .bold))
                            .foregroundStyle(AppColors.calorie)
                    }
                    if let macros = draft.macros {
                        Text(macros)
                            .font(.system(.caption2, design: .rounded, weight: .medium))
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
                if draft.isLogged {
                    Label(draft.isWorkout ? String(localized: "Added") : String(localized: "Logged"), systemImage: "checkmark.circle.fill")
                        .font(.system(.subheadline, design: .rounded, weight: .semibold))
                        .foregroundStyle(AppColors.calorie)
                } else {
                    Button {
                        controller.logDraft(draft.draftID)
                    } label: {
                        Text(draft.isWorkout ? LocalizedStringKey("Add") : LocalizedStringKey("Log This"))
                            .font(.system(.subheadline, design: .rounded, weight: .semibold))
                            .padding(.horizontal, 18)
                            .frame(minHeight: 36)
                            .foregroundStyle(.white)
                            .background(AppColors.calorie, in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("voiceAssistant.card.log")
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("voiceAssistant.card.draft")
    }

    private func confirmationCard(_ confirmation: VoiceAssistantCard.Confirmation) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Label {
                Text(confirmation.text)
                    .font(.system(.subheadline, design: .rounded, weight: .semibold))
                    .fixedSize(horizontal: false, vertical: true)
            } icon: {
                Image(systemName: "questionmark.circle.fill")
                    .foregroundStyle(AppColors.calorie)
            }
            HStack(spacing: 10) {
                Button {
                    controller.cancelCard(card.id)
                } label: {
                    Text("Cancel")
                        .font(.system(.subheadline, design: .rounded, weight: .semibold))
                        .frame(maxWidth: .infinity, minHeight: 38)
                        .background(.secondary.opacity(0.15), in: Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("voiceAssistant.card.cancel")
                Button {
                    controller.confirmCard(card.id)
                } label: {
                    Text("Confirm")
                        .font(.system(.subheadline, design: .rounded, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity, minHeight: 38)
                        .background(AppColors.calorie, in: Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("voiceAssistant.card.confirm")
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("voiceAssistant.card.confirmation")
    }

    private func undoCard(_ undo: VoiceAssistantCard.Undo) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(AppColors.calorie)
                    .accessibilityHidden(true)
                Text(undo.text)
                    .font(.system(.subheadline, design: .rounded, weight: .semibold))
                    .lineLimit(2)
                Spacer(minLength: 0)
                Button {
                    controller.undoCard(card.id)
                } label: {
                    Text("Undo")
                        .font(.system(.subheadline, design: .rounded, weight: .semibold))
                        .padding(.horizontal, 14)
                        .frame(minHeight: 34)
                        .background(.secondary.opacity(0.15), in: Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("voiceAssistant.card.undo")
            }
            ProgressView(timerInterval: Date.now...max(undo.expiresAt, Date.now.addingTimeInterval(0.1)), countsDown: true) {
                EmptyView()
            } currentValueLabel: {
                EmptyView()
            }
            .progressViewStyle(.linear)
            .tint(AppColors.calorie)
            .accessibilityHidden(true)
        }
        .accessibilityElement(children: .contain)
    }

    private func noticeCard(_ notice: VoiceAssistantCard.Notice) -> some View {
        HStack(alignment: .center, spacing: 10) {
            Image(systemName: "exclamationmark.circle.fill")
                .foregroundStyle(AppColors.calorie)
                .accessibilityHidden(true)
            Text(notice.text)
                .font(.system(.subheadline, design: .rounded, weight: .medium))
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
            switch notice.action {
            case .none:
                EmptyView()
            case .buyCredits:
                Button(String(localized: "Buy Credits")) { controller.noticeAction(.buyCredits) }
                    .font(.system(.subheadline, design: .rounded, weight: .semibold))
                    .buttonStyle(.borderless)
            case .openAISettings:
                Button(String(localized: "AI Settings")) { controller.noticeAction(.openAISettings) }
                    .font(.system(.subheadline, design: .rounded, weight: .semibold))
                    .buttonStyle(.borderless)
            }
            Button {
                controller.dismissCard(card.id)
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(.secondary)
                    .frame(width: 28, height: 28)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("Dismiss"))
        }
    }
}

// MARK: - Camera

/// The assistant's camera: a live preview with a shutter for food, or a scan
/// frame for barcodes.
private struct VoiceAssistantCameraPanel: View {
    let controller: VoiceAssistantController

    var body: some View {
        ZStack {
            VoiceAssistantCameraPreview(
                session: controller.camera.session,
                onFocus: { [camera = controller.camera] devicePoint in camera.focus(at: devicePoint) }
            )
            .accessibilityLabel(Text("Camera preview"))

            if controller.cameraMode == .barcode {
                // Decoration only: taps go through to the preview to focus.
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .strokeBorder(.white.opacity(0.9), lineWidth: 3)
                    .frame(height: 140)
                    .padding(.horizontal, 36)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
                VStack {
                    Spacer()
                    Text("Point the camera at the barcode")
                        .font(.system(.subheadline, design: .rounded, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(.black.opacity(0.45), in: Capsule())
                        .padding(.bottom, 16)
                }
                .allowsHitTesting(false)
            } else {
                VStack {
                    Spacer()
                    Button {
                        controller.shutterTapped()
                    } label: {
                        ZStack {
                            Circle()
                                .strokeBorder(.white, lineWidth: 4)
                                .frame(width: 70, height: 70)
                            Circle()
                                .fill(.white.opacity(controller.isCapturingPhoto ? 0.4 : 0.95))
                                .frame(width: 56, height: 56)
                            if controller.isCapturingPhoto {
                                ProgressView()
                                    .tint(.black)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .disabled(controller.isCapturingPhoto)
                    .accessibilityLabel(Text("Take photo"))
                    .accessibilityIdentifier("voiceAssistant.camera.shutter")
                    .padding(.bottom, 16)
                }
            }

            VStack {
                HStack {
                    HStack(spacing: 6) {
                        Circle()
                            .fill(AppColors.calorie)
                            .frame(width: 6, height: 6)
                        Text(controller.cameraMode == .barcode ? "BARCODE // LOCK" : "OPTICAL // SCAN")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .tracking(1.1)
                            .foregroundStyle(.white.opacity(0.92))
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.black.opacity(0.55), in: Capsule())
                    .overlay(Capsule().strokeBorder(AppColors.calorie.opacity(0.45), lineWidth: 0.8))
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)

                    Spacer()
                    Button {
                        controller.closeCameraTapped()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 36, height: 36)
                            .background(.black.opacity(0.55), in: Circle())
                            .overlay(Circle().strokeBorder(Color.white.opacity(0.22), lineWidth: 0.8))
                    }
                    .accessibilityLabel(Text("Close camera"))
                    .accessibilityIdentifier("voiceAssistant.camera.close")
                }
                Spacer()
            }
            .padding(12)

            TacticalHudCornerBrackets(color: AppColors.calorie.opacity(0.72), length: 22, lineWidth: 2.0)
                .padding(18)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
        .aspectRatio(3 / 4, contentMode: .fit)
        .frame(maxHeight: 460)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(AppColors.calorie.opacity(0.55), lineWidth: 1.2))
    }
}

/// Precision corner reticle brackets used on the Tactical Jarvis HUD & optical viewfinder.
private struct TacticalHudCornerBrackets: View {
    var color: Color
    var length: CGFloat = 18
    var lineWidth: CGFloat = 1.4

    var body: some View {
        Canvas { context, size in
            var path = Path()
            let w = size.width
            let h = size.height
            let l = min(length, min(w, h) * 0.25)

            // Top-left
            path.move(to: CGPoint(x: 0, y: l))
            path.addLine(to: CGPoint(x: 0, y: 0))
            path.addLine(to: CGPoint(x: l, y: 0))
            // Top-right
            path.move(to: CGPoint(x: w - l, y: 0))
            path.addLine(to: CGPoint(x: w, y: 0))
            path.addLine(to: CGPoint(x: w, y: l))
            // Bottom-left
            path.move(to: CGPoint(x: 0, y: h - l))
            path.addLine(to: CGPoint(x: 0, y: h))
            path.addLine(to: CGPoint(x: l, y: h))
            // Bottom-right
            path.move(to: CGPoint(x: w - l, y: h))
            path.addLine(to: CGPoint(x: w, y: h))
            path.addLine(to: CGPoint(x: w, y: h - l))

            context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: lineWidth, lineCap: .round, lineJoin: .round))
        }
    }
}

// MARK: - Panels shown when there is no session

/// A dimmed backdrop with a centered card; tapping outside closes it.
private struct VoiceAssistantPanel<Content: View>: View {
    let controller: VoiceAssistantController
    @ViewBuilder let content: Content

    var body: some View {
        ZStack {
            Color.black.opacity(0.55)
                .ignoresSafeArea()
                .onTapGesture { controller.dismiss() }
                .accessibilityHidden(true)
            VStack(spacing: 18) {
                LiveOrbView(mode: .idle, tint: AppColors.calorie, minimumFrameInterval: 1.0 / 30)
                    .frame(width: 96, height: 96)
                    .clipShape(Circle())
                    .accessibilityHidden(true)
                content
            }
            .padding(24)
            .frame(maxWidth: 420)
            .background(AppColors.appBackground, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
            .padding(.horizontal, 20)
        }
    }
}

private struct VoiceAssistantPanelButton: View {
    let title: String
    let prominent: Bool
    let identifier: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(.body, design: .rounded, weight: .semibold))
                .foregroundStyle(prominent ? .white : AppColors.calorie)
                .frame(maxWidth: .infinity, minHeight: 50)
                .background(prominent ? AppColors.calorie : AppColors.calorie.opacity(0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(identifier)
    }
}

private struct VoiceAssistantAccessContent: View {
    let controller: VoiceAssistantController
    let reason: VoiceAssistantAccessReason

    var body: some View {
        VStack(spacing: 16) {
            VStack(spacing: 8) {
                Text("Talk to APEX")
                    .font(.system(.title2, design: .rounded, weight: .bold))
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("voiceAssistant.access.title")
                Text(message)
                    .font(.system(.callout, design: .rounded))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            VStack(spacing: 10) {
                VoiceAssistantPanelButton(title: String(localized: "Close"), prominent: true, identifier: "voiceAssistant.access.notNow") {
                    controller.dismiss()
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("voiceAssistantAccessExplanation")
    }

    private var message: String {
        switch reason {
        case .noProvider, .needsSubscription:
            String(localized: "Live voice assistant is not configured in the built-in system AI settings yet.")
        case .missingKey(let provider):
            String(localized: "\(provider.displayName) voice is not configured in the built-in system AI settings yet.")
        }
    }
}

private struct VoiceAssistantOutOfMinutesContent: View {
    let controller: VoiceAssistantController

    var body: some View {
        VStack(spacing: 16) {
            VStack(spacing: 8) {
                Text("Voice Assistant Unavailable")
                    .font(.system(.title2, design: .rounded, weight: .bold))
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                Text("Please try again shortly.")
                    .font(.system(.callout, design: .rounded))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            VStack(spacing: 10) {
                VoiceAssistantPanelButton(title: String(localized: "Close"), prominent: true, identifier: "voiceAssistant.minutes.close") {
                    controller.dismiss()
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("voiceAssistantOutOfMinutes")
    }
}

private struct VoiceAssistantFailureContent: View {
    let controller: VoiceAssistantController
    let failure: VoiceAssistantFailure

    var body: some View {
        VStack(spacing: 16) {
            Text(failure.message)
                .font(.system(.body, design: .rounded, weight: .medium))
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            VStack(spacing: 10) {
                switch failure.action {
                case .retry:
                    VoiceAssistantPanelButton(title: String(localized: "Try Again"), prominent: true, identifier: "voiceAssistant.failure.retry") {
                        controller.retry()
                    }
                case .openSystemSettings:
                    VoiceAssistantPanelButton(title: String(localized: "Open Settings"), prominent: true, identifier: "voiceAssistant.failure.settings") {
                        controller.openSystemSettings()
                    }
                case .openAISettings:
                    VoiceAssistantPanelButton(title: String(localized: "AI Settings"), prominent: true, identifier: "voiceAssistant.failure.aiSettings") {
                        controller.openAISettings()
                    }
                case nil:
                    EmptyView()
                }
                Button(String(localized: "Close")) {
                    controller.dismiss()
                }
                .font(.system(.body, design: .rounded, weight: .medium))
                .foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("voiceAssistantFailure")
    }
}
