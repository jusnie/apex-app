//
//  AIGate.swift
//  APEX
//

import Foundation

/// Central AI mode (subscriptions/Hosted disabled — always uses built-in system AI).
enum AIModeSettings {
    private static let modeKey = "aiAccessMode"

    static var mode: AIMode {
        get { .byok }
        set { UserDefaults.standard.set(AIMode.byok.rawValue, forKey: modeKey) }
    }

    static var isHosted: Bool { false }
}

/// Hosted quota is enforced by the Worker (see `web/hosted-ai-ledger.ts`): every
/// upstream round-trip is metered server-side against the subscriber's daily
/// pool and credit bank, and exhausted quota surfaces as
/// `HostedAIQuotaError.quotaExceeded` from `HostedAIService`. The client only
/// short-circuits the obvious local cases (BYOK mode, no entitlement) so users
/// get the paywall without a network round-trip.
@MainActor
enum AIGate {
    static func requireHostedEntitlement() throws {
        guard AIModeSettings.isHosted else { return }
        guard RevenueCatManager.shared.hasHostedEntitlement else {
            throw HostedAIQuotaError.noActiveSubscription
        }
    }

    static func runWithHostedQuota<T>(
        _ action: HostedAIAction,
        _ work: () async throws -> T
    ) async throws -> T {
        try requireHostedEntitlement()
        return try await work()
    }
}
