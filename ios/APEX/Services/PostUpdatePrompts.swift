import SwiftUI

/// Public community links shared by the About section and the one-time
/// "Meet the developer" prompt so the URLs live in exactly one place.
enum APEXLinks {
    static let x = URL(string: "https://x.com/apexapp")!
    static let instagram = URL(string: "https://www.instagram.com/apex.fg?mdxt=MTZodnlzY2JwajJ2cQ==")!
    static let facebook = URL(string: "https://www.facebook.com/apexfitnessgym")!
    static let productHunt = URL(string: "https://www.producthunt.com/products/apex")!
    static let appStore = URL(string: "https://apps.apple.com/us/app/apex-workout-tracker/id6758935726")!
}

/// One-time prompts shown to *existing* users the first time they open the app
/// after updating. Fresh installs never see them: onboarding marks every prompt
/// as seen right before it flips `hasCompletedOnboarding`, so only users whose
/// defaults predate these keys qualify. Same once-only pattern as ReviewPrompter.
enum PostUpdatePrompts {
    /// Versioned so the Hosted AI upsell is deliberately re-armed once for users who already
    /// dismissed it (the previous unversioned key was consumed the moment the alert was shown).
    /// Bump the suffix only when the upsell should fire again.
    static let hostedUpsellSeenKey = "hasSeenHostedUpsellPrompt.2026-09-28"
    /// Named "completed" (not "seen") so only Done counts — opening Instagram must not consume it.
    static let meetDeveloperSeenKey = "hasCompletedMeetDeveloperPrompt"
    /// Separate from Meet the developer so people who already tapped Done still get the launch-day sheet.
    static let productHuntLaunchPromptSeenKey = "hasSeenProductHuntLaunchPrompt.2026-09-29"
    /// The Live Voice "What's new" pop-up. Set only when it is finished, so a swipe,
    /// a system tear-down or a killed app shows it again on the next launch.
    static let whatsNewSeenKey = "hasSeenWhatsNew.liveVoice"

    static var hasSeenHostedUpsell: Bool {
        get { UserDefaults.standard.bool(forKey: hostedUpsellSeenKey) }
        set { UserDefaults.standard.set(newValue, forKey: hostedUpsellSeenKey) }
    }

    static var hasSeenMeetDeveloper: Bool {
        get { UserDefaults.standard.bool(forKey: meetDeveloperSeenKey) }
        set { UserDefaults.standard.set(newValue, forKey: meetDeveloperSeenKey) }
    }

    static var hasSeenProductHuntLaunchPrompt: Bool {
        get { UserDefaults.standard.bool(forKey: productHuntLaunchPromptSeenKey) }
        set { UserDefaults.standard.set(newValue, forKey: productHuntLaunchPromptSeenKey) }
    }

    static var hasSeenWhatsNew: Bool {
        get { UserDefaults.standard.bool(forKey: whatsNewSeenKey) }
        set { UserDefaults.standard.set(newValue, forKey: whatsNewSeenKey) }
    }

    /// Ends the What's new pop-up for good. Its last page covers Meet the developer and its
    /// access page the one-time Plus/Pro upsell, so those older prompts are consumed with it.
    /// - Parameters:
    ///   - showedPlans: the access page offered Plus and Pro.
    ///   - reachedEnd: closed from the last page, rather than leaving early to add a key.
    @MainActor
    static func finishWhatsNew(showedPlans: Bool, reachedEnd: Bool) {
        hasSeenWhatsNew = true
        if reachedEnd { hasSeenMeetDeveloper = true }
        if showedPlans || RevenueCatManager.shared.hasHostedEntitlement { hasSeenHostedUpsell = true }
    }

    /// Called when onboarding completes so a brand-new user is never treated as
    /// an "existing user who just updated".
    static func markAllSeenForFreshInstall() {
        hasSeenHostedUpsell = true
        hasSeenMeetDeveloper = true
        hasSeenWhatsNew = true
    }

    /// The hosted upsell is only relevant to BYOK users without an active Plus/Pro
    /// entitlement. Anyone already on hosted has nothing to be upsold.
    ///
    /// Also gated on RevenueCat actually resolving purchasable Plus/Pro packages: while the
    /// IAPs are not live the offerings come back empty, and showing the alert would only
    /// dead-end on "Plans are unavailable right now." In that case the prompt is left
    /// unconsumed so it appears once the plans go live.
    @MainActor
    static var isHostedUpsellEligible: Bool {
        AIModeSettings.mode == .byok
            && !RevenueCatManager.shared.hasHostedEntitlement
            && RevenueCatManager.shared.hasPurchasableHostedPlans
    }
}

/// Friendly one-time sheet pointing existing users at the developer's socials
/// and the Product Hunt page. Links reuse `APEXLinks` (same as About).
/// Stays up while opening socials — only `onDone` (Done button) dismisses it.
struct MeetDeveloperSheet: View {
    var onDone: () -> Void
    @Environment(\.openURL) private var openURL

    var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(spacing: 10) {
                        Image("onboardingLogo")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 56, height: 56)
                            .accessibilityHidden(true)
                        Text(verbatim: "Dev: Yasuda | Gian")
                            .font(.system(.title3, design: .rounded, weight: .bold))
                        Text("I build APEX on my own, in the open. Come say hi, share feedback, or just follow along — it genuinely helps.")
                            .font(.system(.subheadline, design: .rounded))
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                }

                Section {
                    socialLinkRow("Follow on Instagram", assetImage: "social_instagram", url: APEXLinks.instagram)
                    socialLinkRow("Follow on Facebook", assetImage: "social_facebook", url: APEXLinks.facebook)
                }
                .listRowBackground(AppColors.appCard)
            }
            .navigationTitle("Meet the developer")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done", action: onDone)
                }
            }
        }
        .presentationDetents([.medium, .large])
        // Swipe-down must not count as "seen" — only Done closes the prompt.
        .interactiveDismissDisabled()
    }

    private func socialLinkRow(_ title: LocalizedStringKey, assetImage: String, url: URL) -> some View {
        Button {
            openURL(url)
        } label: {
            Label {
                Text(title)
            } icon: {
                Image(assetImage)
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 20, height: 20)
                    .foregroundStyle(AppColors.calorie)
            }
        }
        .tint(.primary)
    }

    private func linkRow(_ title: LocalizedStringKey, systemImage: String, url: URL) -> some View {
        // Button + openURL keeps the sheet up when jumping to Instagram/X/etc.
        // (SwiftUI `Link` can dismiss the presenting sheet when leaving the app.)
        Button {
            openURL(url)
        } label: {
            Label {
                Text(title)
            } icon: {
                Image(systemName: systemImage)
                    .foregroundStyle(AppColors.calorie)
            }
        }
        .tint(.primary)
    }
}

/// One-time launch-day sheet. Shows during the Product Hunt window even if Meet the developer was already dismissed.
struct ProductHuntLaunchSheet: View {
    var onVote: () -> Void
    var onNotNow: () -> Void
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(spacing: 16) {
            Image("onboardingLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 56, height: 56)
                .accessibilityHidden(true)
            Text("APEX is live on Product Hunt")
                .font(.system(.title3, design: .rounded, weight: .bold))
                .multilineTextAlignment(.center)
            Text("We just launched. A vote helps more people find APEX.")
                .font(.system(.subheadline, design: .rounded))
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Button {
                openURL(APEXLinks.productHunt)
                onVote()
            } label: {
                Text("Vote on Product Hunt")
                    .font(.system(.body, design: .rounded, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(AppColors.calorie, in: RoundedRectangle(cornerRadius: 14))
            }
            Button("Not now", action: onNotNow)
                .font(.system(.body, design: .rounded, weight: .semibold))
                .foregroundStyle(.secondary)
        }
        .padding(24)
        .presentationDetents([.medium])
        .interactiveDismissDisabled()
    }
}
