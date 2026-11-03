import SwiftUI
import UIKit

enum AppThemeColor: String, CaseIterable, Identifiable {
    case red
    case apexPink
    case orange
    case green
    case mint
    case teal
    case blue
    case purple
    case yellow
    case coral
    case roseGold
    case mochaBrown
    case indigo
    case lavender
    case skyCyan
    case graphite
    case babyPink
    case lime

    static let storageKey = "appThemeColor"
    static let defaultColor: AppThemeColor = .red

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .red: return LocalizedDisplayText.text("APEX Red", polish: "Czerwień APEX")
        case .apexPink: return LocalizedDisplayText.text("APEX Crimson", polish: "Karmazyn APEX")
        case .orange: return LocalizedDisplayText.text("Orange", polish: "Pomarańczowy")
        case .green: return LocalizedDisplayText.text("Green", polish: "Zielony")
        case .mint: return LocalizedDisplayText.text("Mint", polish: "Miętowy")
        case .teal: return LocalizedDisplayText.text("Teal", polish: "Turkusowy")
        case .blue: return LocalizedDisplayText.text("Blue", polish: "Niebieski")
        case .purple: return LocalizedDisplayText.text("Purple", polish: "Fioletowy")
        case .yellow: return LocalizedDisplayText.text("Yellow", polish: "Żółty")
        case .coral: return LocalizedDisplayText.text("Coral", polish: "Koralowy")
        case .roseGold: return LocalizedDisplayText.text("Rose Gold", polish: "Różowe złoto")
        case .mochaBrown: return LocalizedDisplayText.text("Mocha Brown", polish: "Brąz mokka")
        case .indigo: return LocalizedDisplayText.text("Indigo", polish: "Indygo")
        case .lavender: return LocalizedDisplayText.text("Lavender", polish: "Lawendowy")
        case .skyCyan: return LocalizedDisplayText.text("Sky Cyan", polish: "Błękit")
        case .graphite: return LocalizedDisplayText.text("Graphite", polish: "Grafitowy")
        case .babyPink: return LocalizedDisplayText.text("Baby Pink", polish: "Pastelowy róż")
        case .lime: return LocalizedDisplayText.text("Lime", polish: "Limonkowy")
        }
    }

    var color: Color {
        Color(hex: startHex)
    }

    var gradientColors: [Color] {
        [Color(hex: startHex), Color(hex: endHex)]
    }

    var alternateIconName: String? {
        switch self {
        case .apexPink: return nil
        case .red: return "AppIconRed"
        case .orange: return "AppIconOrange"
        case .green: return "AppIconGreen"
        case .mint: return "AppIconMint"
        case .teal: return "AppIconTeal"
        case .blue: return "AppIconBlue"
        case .purple: return "AppIconPurple"
        case .yellow: return "AppIconYellow"
        case .coral: return "AppIconCoral"
        case .roseGold: return "AppIconRoseGold"
        case .mochaBrown: return "AppIconMochaBrown"
        case .indigo: return "AppIconIndigo"
        case .lavender: return "AppIconLavender"
        case .skyCyan: return "AppIconSkyCyan"
        case .graphite: return "AppIconGraphite"
        case .babyPink: return "AppIconBabyPink"
        case .lime: return "AppIconLime"
        }
    }

    static var current: AppThemeColor {
        guard let rawValue = UserDefaults.standard.string(forKey: storageKey),
              let themeColor = AppThemeColor(rawValue: rawValue) else {
            return defaultColor
        }
        return themeColor
    }

    static func color(for rawValue: String) -> AppThemeColor {
        AppThemeColor(rawValue: rawValue) ?? defaultColor
    }

    // Menu (dropdown) rows render SwiftUI colors as monochrome templates, so the
    // swatch has to be a pre-rendered UIImage marked alwaysOriginal to keep its color.
    var menuSwatchImage: UIImage {
        let size = CGSize(width: 22, height: 22)
        let image = UIGraphicsImageRenderer(size: size).image { context in
            let rect = CGRect(origin: .zero, size: size)
            UIBezierPath(ovalIn: rect).addClip()
            let colors = [UIColor(color), UIColor(gradientColors.last ?? color)].map(\.cgColor)
            if let gradient = CGGradient(
                colorsSpace: CGColorSpaceCreateDeviceRGB(),
                colors: colors as CFArray,
                locations: [0, 1]
            ) {
                context.cgContext.drawLinearGradient(
                    gradient,
                    start: .zero,
                    end: CGPoint(x: size.width, y: size.height),
                    options: []
                )
            }
        }
        return image.withRenderingMode(.alwaysOriginal)
    }

    @MainActor
    static func applyAppIconIfNeeded(for themeColor: AppThemeColor) {
        let application = UIApplication.shared
        guard application.supportsAlternateIcons,
              application.alternateIconName != themeColor.alternateIconName else {
            return
        }

        application.setAlternateIconName(themeColor.alternateIconName)
    }

    // Internal (not private) so WidgetSnapshotWriter can ship the raw hexes to
    // the Watch, which has no AppThemeColor and rebuilds Colors from the values.
    var startHex: UInt {
        switch self {
        case .red: return 0xE10600
        case .apexPink: return 0xDC143C
        case .orange: return 0xFF6B00
        case .green: return 0x1DB954
        case .mint: return 0x00C7BE
        case .teal: return 0x0EA5E9
        case .blue: return 0x007AFF
        case .purple: return 0x8B5CF6
        case .yellow: return 0xF59E0B
        case .coral: return 0xFF3B30
        case .roseGold: return 0xB91C1C
        case .mochaBrown: return 0x78350F
        case .indigo: return 0x4F46E5
        case .lavender: return 0x9333EA
        case .skyCyan: return 0x06B6D4
        case .graphite: return 0x18181B
        case .babyPink: return 0xE11D48
        case .lime: return 0x65A30D
        }
    }

    var endHex: UInt {
        switch self {
        case .red: return 0xFF2E2E
        case .apexPink: return 0xFF3B30
        case .orange: return 0xFF9F1C
        case .green: return 0x4ADE80
        case .mint: return 0x66E3DC
        case .teal: return 0x38BDF8
        case .blue: return 0x4DA3FF
        case .purple: return 0xB388FF
        case .yellow: return 0xFBBF24
        case .coral: return 0xFF6B6B
        case .roseGold: return 0xEF4444
        case .mochaBrown: return 0xB45309
        case .indigo: return 0x818CF8
        case .lavender: return 0xC084FC
        case .skyCyan: return 0x67E8F9
        case .graphite: return 0x52525B
        case .babyPink: return 0xFB7185
        case .lime: return 0xA3E635
        }
    }
}

enum AppColors {
    //Primary APEX Crimson Red -> Scarlet gradient enforced globally
    static var calorieGradient: [Color] { [Color(hex: 0xE10600), Color(hex: 0xFF2E2E)] }
    static var calorie: Color { Color(hex: 0xE10600) }

    // Protein
    static var proteinGradient: [Color] { [Color(hex: 0xFF453A), Color(hex: 0xFF7A70)] }
    static var protein: Color { Color(hex: 0xFF453A) }

    // Carbs
    static var carbsGradient: [Color] { [Color.white, Color(hex: 0xCBD5E1)] }
    static var carbs: Color { Color.white }

    // Fat
    static var fatGradient: [Color] { [Color(hex: 0xEF4444), Color(hex: 0xFCA5A5)] }
    static var fat: Color { Color(hex: 0xEF4444) }

    // Pure Black background & Obsidian Card enforced globally across APEX
    static let appBackground = Color.black
    static let appCard = Color(hex: 0x0A0A0F)
}

/// Signature APEX smooth ambient animated background (Pure Black, Crimson Red & Crisp White).
/// Mirrors Android's `APEXAnimatedBackground.kt` with identical 22s / 30s Lissajous radial motion.
struct APEXAnimatedBackground: View {
    var body: some View {
        let primaryAccent = Color(hex: 0xE10600)
        let secondaryAccent = Color(hex: 0xFF2E2E)
        let isDark = true

        TimelineView(.animation(minimumInterval: 1.0 / 60.0, paused: false)) { timeline in
            let t = timeline.date.timeIntervalSinceReferenceDate
            let phaseA = (t.truncatingRemainder(dividingBy: 22.0) / 22.0) * (2.0 * .pi)
            let phaseB = (t.truncatingRemainder(dividingBy: 30.0) / 30.0) * (2.0 * .pi)

            Canvas { context, size in
                let w = size.width
                let h = size.height
                let maxDim = max(w, h, 1)

                // 1. Pure Black (#000000) canvas
                context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(.black))

                // 2. Primary APEX Crimson Red drifting ambient orb
                let orb1Center = CGPoint(
                    x: w * (0.24 + 0.22 * cos(phaseA)),
                    y: h * (0.16 + 0.10 * sin(phaseA + 0.4))
                )
                let orb1Radius = maxDim * 0.62
                let orb1Alpha = isDark ? 0.16 : 0.075
                let orb1Rect = CGRect(
                    x: orb1Center.x - orb1Radius,
                    y: orb1Center.y - orb1Radius,
                    width: orb1Radius * 2,
                    height: orb1Radius * 2
                )
                context.fill(
                    Path(ellipseIn: orb1Rect),
                    with: .radialGradient(
                        Gradient(colors: [
                            primaryAccent.opacity(orb1Alpha),
                            primaryAccent.opacity(orb1Alpha * 0.42),
                            .clear
                        ]),
                        center: orb1Center,
                        startRadius: 0,
                        endRadius: orb1Radius
                    )
                )

                // 3. Secondary Scarlet Red counter-drifting ambient orb
                let orb2Center = CGPoint(
                    x: w * (0.78 - 0.24 * sin(phaseB)),
                    y: h * (0.78 - 0.14 * cos(phaseB))
                )
                let orb2Radius = maxDim * 0.58
                let orb2Alpha = isDark ? 0.12 : 0.055
                let orb2Rect = CGRect(
                    x: orb2Center.x - orb2Radius,
                    y: orb2Center.y - orb2Radius,
                    width: orb2Radius * 2,
                    height: orb2Radius * 2
                )
                context.fill(
                    Path(ellipseIn: orb2Rect),
                    with: .radialGradient(
                        Gradient(colors: [
                            secondaryAccent.opacity(orb2Alpha),
                            primaryAccent.opacity(orb2Alpha * 0.35),
                            .clear
                        ]),
                        center: orb2Center,
                        startRadius: 0,
                        endRadius: orb2Radius
                    )
                )

                // 4. Subtle Crisp White atmospheric crown glow
                let crownCenter = CGPoint(
                    x: w * (0.50 + 0.12 * sin(phaseA - phaseB)),
                    y: -h * 0.06
                )
                let crownRadius = maxDim * 0.48
                let crownAlpha = isDark ? 0.045 : 0.45
                let crownRect = CGRect(
                    x: crownCenter.x - crownRadius,
                    y: crownCenter.y - crownRadius,
                    width: crownRadius * 2,
                    height: crownRadius * 2
                )
                context.fill(
                    Path(ellipseIn: crownRect),
                    with: .radialGradient(
                        Gradient(colors: [
                            Color.white.opacity(crownAlpha),
                            .clear
                        ]),
                        center: crownCenter,
                        startRadius: 0,
                        endRadius: crownRadius
                    )
                )
            }
        }
        .ignoresSafeArea()
    }
}

extension Color {
    init(hex: UInt, opacity: Double = 1.0) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}
