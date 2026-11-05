import SwiftUI

/// The shared floating entry point on Home and the workout diary:
/// an obsidian-glass tactical housing enclosing the live APEX Reactor Core.
struct VoiceAssistantHomeButton: View {
    let action: () -> Void
    var size: CGFloat = 60

    var body: some View {
        Button(action: action) {
            ZStack {
                Circle()
                    .fill(Color(red: 0.03, green: 0.03, blue: 0.045).opacity(0.88))
                LiveOrbView(
                    mode: .idle,
                    tint: AppColors.calorie,
                    minimumFrameInterval: 1.0 / 30,
                    clearBackground: true
                )
                .scaleEffect(1 / 0.78)
                Circle()
                    .strokeBorder(
                        LinearGradient(
                            colors: [
                                AppColors.calorie.opacity(0.65),
                                Color.white.opacity(0.22),
                                AppColors.calorie.opacity(0.30)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1.2
                    )
            }
            .frame(width: size, height: size)
            .clipShape(Circle())
            .shadow(color: AppColors.calorie.opacity(0.28), radius: 10, y: 3)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("Talk to APEX"))
        .accessibilityIdentifier("voiceAssistant.homeButton")
    }
}
