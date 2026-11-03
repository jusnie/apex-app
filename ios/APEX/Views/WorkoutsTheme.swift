import SwiftUI
import UIKit

// MARK: - Workouts theme bridge (APEX Obsidian Black / Crimson Red / Crisp White)

extension Color {
    /// Screen background — Obsidian Black in dark, Crisp Pure White in light.
    static var workoutBackground: Color { AppColors.appBackground }

    /// Card surface behind rows and hero imagery.
    static var workoutCard: Color { AppColors.appCard }

    /// Elevated panel behind menus / pills — sleek carbon in dark, cool platinum in light.
    static var workoutPanel: Color {
        Color(uiColor: UIColor { traits in
            traits.userInterfaceStyle == .dark
                ? UIColor(red: 0.082, green: 0.082, blue: 0.110, alpha: 1)
                : UIColor(red: 0.945, green: 0.945, blue: 0.961, alpha: 1)
        })
    }

    /// Hairline strokes — crisp divider tones.
    static var workoutHairline: Color {
        Color(uiColor: UIColor { traits in
            traits.userInterfaceStyle == .dark
                ? UIColor(red: 0.137, green: 0.137, blue: 0.176, alpha: 1)
                : UIColor(red: 0.894, green: 0.894, blue: 0.906, alpha: 1)
        })
    }

    /// Primary accent — APEX Crimson Red by default.
    static var workoutAccent: Color { AppColors.calorie }

    /// Companion scarlet accent — gradient end of the theme color.
    static var workoutSecondaryAccent: Color {
        AppThemeColor.current.gradientColors.last ?? AppColors.calorie
    }

    /// Secondary accent alias.
    static var workoutInferno: Color { Color.workoutSecondaryAccent }

    /// Strong text.
    static var workoutCharcoal: Color { Color.primary }

    /// Muted/supporting text.
    static var workoutMutedText: Color { Color.secondary }

    /// Text/icons rendered on top of the accent color.
    static var workoutOnAccent: Color { Color.white }
}

struct WorkoutBackground: View {
    var body: some View {
        APEXAnimatedBackground()
    }
}

extension View {
    func workoutScreen() -> some View {
        background(WorkoutBackground())
            .scrollContentBackground(.hidden)
    }

    @ViewBuilder
    func workoutLiquidBarSurface(cornerRadius: CGFloat = 32) -> some View {
        modifier(WorkoutLiquidBarSurfaceModifier(cornerRadius: cornerRadius))
    }

    func workoutPressable() -> some View {
        buttonStyle(WorkoutPressableButtonStyle())
    }
}

private struct WorkoutLiquidBarSurfaceModifier: ViewModifier {
    let cornerRadius: CGFloat
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)

        if colorScheme == .light {
            content
                .background(Color.white.opacity(0.92), in: shape)
                .overlay(
                    shape.stroke(
                        LinearGradient(
                            colors: [
                                Color.workoutHairline,
                                AppColors.calorie.opacity(0.22),
                                Color.workoutHairline
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 0.75
                    )
                )
        } else if #available(iOS 26.0, *) {
            content
                .glassEffect(.regular.interactive(), in: shape)
        } else {
            content
                .background(Color(hex: 0x0E0E13, opacity: 0.86), in: shape)
                .overlay(
                    shape.stroke(
                        LinearGradient(
                            colors: [
                                Color.white.opacity(0.18),
                                AppColors.calorie.opacity(0.28),
                                Color.white.opacity(0.05)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 0.75
                    )
                )
        }
    }
}

struct WorkoutPressableButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        let isPressed = configuration.isPressed && isEnabled

        configuration.label
            .scaleEffect(isPressed ? 0.975 : 1)
            .opacity(isEnabled ? (isPressed ? 0.90 : 1) : 0.55)
            .animation(.easeOut(duration: 0.14), value: isPressed)
    }
}
