import Foundation

/// Built-in system AI configuration configured by the app owner in
/// `ios/APEX/Resources/AIConfig/apex-ai-config.json` or overridden via
/// environment variables / `Info.plist` keys (`APEX_SYSTEM_*`).
///
/// End users never need a subscription or their own API keys.
struct APEXSystemAIConfig: Sendable {
    static let shared = APEXSystemAIConfig.load()

    struct Payload: Decodable, Sendable {
        var defaultProvider: String = "GEMINI"
        var defaultVisionModel: String = ""
        var separateTextProviderEnabled: Bool = false
        var defaultTextProvider: String = ""
        var defaultTextModel: String = ""
        var fallbackEnabled: Bool = false
        var fallbackProvider: String = ""
        var fallbackModel: String = ""
        var defaultSpeechProvider: String = "NATIVE"
        var defaultVoiceAssistantProvider: String = "AUTOMATIC"
        var apiKeys: [String: String] = [:]
        var customBaseUrls: [String: String] = [:]
    }

    private let payload: Payload

    private static func load() -> APEXSystemAIConfig {
        let candidates: [URL?] = [
            Bundle.main.url(forResource: "apex-ai-config", withExtension: "json"),
            Bundle.main.url(forResource: "apex-ai-config", withExtension: "json", subdirectory: "AIConfig"),
            Bundle.main.url(forResource: "apex-ai-config", withExtension: "json", subdirectory: "Resources/AIConfig")
        ]
        for url in candidates.compactMap({ $0 }) {
            if let data = try? Data(contentsOf: url),
               let decoded = try? JSONDecoder().decode(Payload.self, from: data) {
                return APEXSystemAIConfig(payload: decoded)
            }
        }
        return APEXSystemAIConfig(payload: Payload())
    }

    private static func externalOverride(_ key: String) -> String? {
        if let env = ProcessInfo.processInfo.environment[key]?.trimmingCharacters(in: .whitespacesAndNewlines),
           !env.isEmpty {
            return env
        }
        if let plist = (Bundle.main.object(forInfoDictionaryKey: key) as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines),
           !plist.isEmpty,
           !plist.hasPrefix("$(") {
            return plist
        }
        return nil
    }

    func apiKey(for provider: AIProvider) -> String? {
        let envKeyName: String? = switch provider {
        case .gemini: "APEX_SYSTEM_GEMINI_API_KEY"
        case .openai: "APEX_SYSTEM_OPENAI_API_KEY"
        case .anthropic: "APEX_SYSTEM_ANTHROPIC_API_KEY"
        case .xai: "APEX_SYSTEM_XAI_API_KEY"
        case .openrouter: "APEX_SYSTEM_OPENROUTER_API_KEY"
        case .groq: "APEX_SYSTEM_GROQ_API_KEY"
        case .mistral: "APEX_SYSTEM_MISTRAL_API_KEY"
        case .deepseek: "APEX_SYSTEM_DEEPSEEK_API_KEY"
        case .qwen: "APEX_SYSTEM_QWEN_API_KEY"
        case .customOpenAI: "APEX_SYSTEM_CUSTOM_OPENAI_API_KEY"
        default: nil
        }
        if let envKeyName, let override = Self.externalOverride(envKeyName) {
            return override
        }
        for alias in Self.aliases(for: provider) {
            if let value = payload.apiKeys[alias]?.trimmingCharacters(in: .whitespacesAndNewlines),
               !value.isEmpty {
                return value
            }
        }
        return nil
    }

    func speechAPIKey(for provider: SpeechProvider) -> String? {
        let envKeyName: String? = switch provider {
        case .deepgram: "APEX_SYSTEM_DEEPGRAM_API_KEY"
        case .assemblyai: "APEX_SYSTEM_ASSEMBLYAI_API_KEY"
        default: nil
        }
        if let envKeyName, let override = Self.externalOverride(envKeyName) {
            return override
        }
        let aliases: [String] = switch provider {
        case .deepgram: ["DEEPGRAM"]
        case .assemblyai: ["ASSEMBLYAI", "ASSEMBLY_AI"]
        case .gemini: ["GEMINI_SPEECH", "GEMINI"]
        case .openai: ["OPENAI_SPEECH", "OPENAI"]
        case .groq: ["GROQ_SPEECH", "GROQ"]
        case .mistral: ["MISTRAL_SPEECH", "MISTRAL"]
        case .nativeIOS, .whisperBase: []
        }
        for alias in aliases {
            if let value = payload.apiKeys[alias]?.trimmingCharacters(in: .whitespacesAndNewlines),
               !value.isEmpty {
                return value
            }
        }
        if let matching = provider.matchingAIProvider {
            return apiKey(for: matching)
        }
        return nil
    }

    var usdaFdcAPIKey: String? {
        if let override = Self.externalOverride("APEX_SYSTEM_USDA_FDC_API_KEY") {
            return override
        }
        if let value = payload.apiKeys["USDA_FDC"]?.trimmingCharacters(in: .whitespacesAndNewlines),
           !value.isEmpty {
            return value
        }
        return nil
    }

    func customBaseURL(for provider: AIProvider) -> String? {
        if provider == .customOpenAI,
           let override = Self.externalOverride("APEX_SYSTEM_CUSTOM_OPENAI_BASE_URL") {
            return override
        }
        for alias in Self.aliases(for: provider) {
            if let value = payload.customBaseUrls[alias]?.trimmingCharacters(in: .whitespacesAndNewlines),
               !value.isEmpty {
                return value
            }
        }
        return nil
    }

    var defaultVisionProvider: AIProvider {
        let raw = Self.externalOverride("APEX_SYSTEM_DEFAULT_PROVIDER") ?? payload.defaultProvider
        if let parsed = Self.parseAIProvider(raw),
           AIProvider.visionProviders.contains(parsed),
           (!parsed.requiresAPIKey || apiKey(for: parsed) != nil) {
            return parsed
        }
        if let firstConfigured = AIProvider.visionProviders.first(where: { $0.requiresAPIKey && apiKey(for: $0) != nil }) {
            return firstConfigured
        }
        if let parsed = Self.parseAIProvider(raw), AIProvider.visionProviders.contains(parsed) {
            return parsed
        }
        return .gemini
    }

    func defaultVisionModel(for provider: AIProvider) -> String {
        let raw = (Self.externalOverride("APEX_SYSTEM_DEFAULT_VISION_MODEL") ?? payload.defaultVisionModel)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return raw.isEmpty ? provider.defaultModel : provider.supportedModelOrDefault(raw)
    }

    var defaultTextProvider: AIProvider {
        if let parsed = Self.parseAIProvider(payload.defaultTextProvider),
           AIProvider.textProviders.contains(parsed),
           (!parsed.requiresAPIKey || apiKey(for: parsed) != nil) {
            return parsed
        }
        return defaultVisionProvider
    }

    func defaultTextModel(for provider: AIProvider) -> String {
        let raw = (Self.externalOverride("APEX_SYSTEM_DEFAULT_TEXT_MODEL") ?? payload.defaultTextModel)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return raw.isEmpty ? provider.defaultTextModel : provider.supportedTextModelOrDefault(raw)
    }

    var defaultSpeechProvider: SpeechProvider {
        let raw = Self.externalOverride("APEX_SYSTEM_DEFAULT_SPEECH_PROVIDER") ?? payload.defaultSpeechProvider
        if let parsed = Self.parseSpeechProvider(raw),
           (!parsed.requiresAPIKey || speechAPIKey(for: parsed) != nil) {
            return parsed
        }
        if let matched = SpeechProvider.matchingPrimaryAIProvider(defaultVisionProvider),
           speechAPIKey(for: matched) != nil {
            return matched
        }
        return .nativeIOS
    }

    private static func aliases(for provider: AIProvider) -> [String] {
        switch provider {
        case .appleIntelligence: ["APPLE_INTELLIGENCE", provider.rawValue]
        case .gemma4Local: ["LOCAL_GEMMA", "GEMMA4LOCAL", provider.rawValue]
        case .gemini: ["GEMINI", provider.rawValue]
        case .openai: ["OPENAI", provider.rawValue]
        case .anthropic: ["ANTHROPIC", provider.rawValue]
        case .xai: ["XAI", provider.rawValue]
        case .openrouter: ["OPENROUTER", provider.rawValue]
        case .togetherai: ["TOGETHER_AI", "TOGETHERAI", provider.rawValue]
        case .groq: ["GROQ", provider.rawValue]
        case .huggingface: ["HUGGING_FACE", "HUGGINGFACE", provider.rawValue]
        case .fireworks: ["FIREWORKS", provider.rawValue]
        case .deepinfra: ["DEEP_INFRA", "DEEPINFRA", provider.rawValue]
        case .mistral: ["MISTRAL", provider.rawValue]
        case .deepseek: ["DEEPSEEK", provider.rawValue]
        case .qwen: ["QWEN", provider.rawValue]
        case .cerebras: ["CEREBRAS", provider.rawValue]
        case .ollama: ["OLLAMA", provider.rawValue]
        case .customOpenAI: ["CUSTOM_OPENAI", "CUSTOMOPENAI", provider.rawValue]
        }
    }

    private static func parseAIProvider(_ raw: String) -> AIProvider? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        if let exact = AIProvider(rawValue: trimmed) { return exact }
        return AIProvider.allCases.first { candidate in
            aliases(for: candidate).contains { $0.caseInsensitiveCompare(trimmed) == .orderedSame }
        }
    }

    private static func parseSpeechProvider(_ raw: String) -> SpeechProvider? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        if let exact = SpeechProvider(rawValue: trimmed) { return exact }
        let normalized = trimmed.uppercased().replacingOccurrences(of: "-", with: "_").replacingOccurrences(of: " ", with: "_")
        return switch normalized {
        case "NATIVE", "NATIVE_IOS", "ON_DEVICE": .nativeIOS
        case "LOCAL_WHISPER", "WHISPER_BASE", "WHISPER": .whisperBase
        case "GEMINI", "GEMINI_AUDIO": .gemini
        case "OPENAI", "OPENAI_WHISPER": .openai
        case "GROQ", "GROQ_WHISPER": .groq
        case "MISTRAL", "MISTRAL_VOXTRAL": .mistral
        case "DEEPGRAM": .deepgram
        case "ASSEMBLYAI", "ASSEMBLY_AI": .assemblyai
        default: nil
        }
    }
}
