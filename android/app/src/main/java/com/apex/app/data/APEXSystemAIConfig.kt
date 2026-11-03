package com.apex.app.data

import android.content.Context
import com.apex.app.BuildConfig
import com.apex.app.models.AIProvider
import com.apex.app.models.SpeechProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Built-in system AI configuration configured by the app owner in
 * `ios/APEX/Resources/AIConfig/apex-ai-config.json` (packaged into Android `assets/`)
 * or overridden at build time via `local.properties` / `gradle.properties` / environment variables.
 *
 * End users never need a subscription or their own API keys.
 */
class APEXSystemAIConfig private constructor(
    private val payload: SystemAIConfigPayload
) {
    fun apiKeyFor(provider: AIProvider): String? {
        val envOverride = when (provider) {
            AIProvider.GEMINI -> BuildConfig.APEX_SYSTEM_GEMINI_API_KEY
            AIProvider.OPENAI -> BuildConfig.APEX_SYSTEM_OPENAI_API_KEY
            AIProvider.ANTHROPIC -> BuildConfig.APEX_SYSTEM_ANTHROPIC_API_KEY
            AIProvider.XAI -> BuildConfig.APEX_SYSTEM_XAI_API_KEY
            AIProvider.OPENROUTER -> BuildConfig.APEX_SYSTEM_OPENROUTER_API_KEY
            AIProvider.GROQ -> BuildConfig.APEX_SYSTEM_GROQ_API_KEY
            AIProvider.MISTRAL -> BuildConfig.APEX_SYSTEM_MISTRAL_API_KEY
            AIProvider.DEEPSEEK -> BuildConfig.APEX_SYSTEM_DEEPSEEK_API_KEY
            AIProvider.QWEN -> BuildConfig.APEX_SYSTEM_QWEN_API_KEY
            AIProvider.CUSTOM_OPENAI -> BuildConfig.APEX_SYSTEM_CUSTOM_OPENAI_API_KEY
            else -> ""
        }.trim()
        if (envOverride.isNotEmpty()) return envOverride

        for (alias in aliasesFor(provider)) {
            val fromJson = payload.apiKeys[alias]?.trim()
            if (!fromJson.isNullOrEmpty()) return fromJson
        }
        return null
    }

    fun speechApiKeyFor(provider: SpeechProvider): String? {
        val envOverride = when (provider) {
            SpeechProvider.DEEPGRAM -> BuildConfig.APEX_SYSTEM_DEEPGRAM_API_KEY
            SpeechProvider.ASSEMBLY_AI -> BuildConfig.APEX_SYSTEM_ASSEMBLYAI_API_KEY
            else -> ""
        }.trim()
        if (envOverride.isNotEmpty()) return envOverride

        val aliases = when (provider) {
            SpeechProvider.DEEPGRAM -> listOf("DEEPGRAM")
            SpeechProvider.ASSEMBLY_AI -> listOf("ASSEMBLYAI", "ASSEMBLY_AI")
            SpeechProvider.GEMINI -> listOf("GEMINI_SPEECH", "GEMINI")
            SpeechProvider.OPENAI -> listOf("OPENAI_SPEECH", "OPENAI")
            SpeechProvider.GROQ -> listOf("GROQ_SPEECH", "GROQ")
            SpeechProvider.MISTRAL -> listOf("MISTRAL_SPEECH", "MISTRAL")
            SpeechProvider.NATIVE, SpeechProvider.LOCAL_WHISPER -> emptyList()
        }
        for (alias in aliases) {
            val fromJson = payload.apiKeys[alias]?.trim()
            if (!fromJson.isNullOrEmpty()) return fromJson
        }
        return provider.matchingAIProvider?.let(::apiKeyFor)
    }

    fun usdaFdcApiKey(): String? {
        val envOverride = BuildConfig.APEX_SYSTEM_USDA_FDC_API_KEY.trim()
        if (envOverride.isNotEmpty()) return envOverride
        return payload.apiKeys["USDA_FDC"]?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun customBaseUrlFor(provider: AIProvider): String? {
        if (provider == AIProvider.CUSTOM_OPENAI) {
            val envUrl = BuildConfig.APEX_SYSTEM_CUSTOM_OPENAI_BASE_URL.trim()
            if (envUrl.isNotEmpty()) return envUrl
        }
        for (alias in aliasesFor(provider)) {
            val fromJson = payload.customBaseUrls[alias]?.trim()
            if (!fromJson.isNullOrEmpty()) return fromJson
        }
        return null
    }

    fun defaultVisionProvider(): AIProvider {
        val configuredRaw = BuildConfig.APEX_SYSTEM_DEFAULT_PROVIDER.trim()
            .ifEmpty { payload.defaultProvider.trim() }
        val configured = parseAIProvider(configuredRaw)?.takeIf { it.supportsVision }
        if (configured != null && (!configured.requiresApiKey || apiKeyFor(configured) != null)) {
            return configured
        }
        // Auto-select the first vision-capable provider that has a configured system key.
        AIProvider.remoteVisionProviders.firstOrNull { apiKeyFor(it) != null }?.let { return it }
        return configured ?: AIProvider.GEMINI
    }

    fun defaultVisionModel(provider: AIProvider = defaultVisionProvider()): String {
        val raw = BuildConfig.APEX_SYSTEM_DEFAULT_VISION_MODEL.trim()
            .ifEmpty { payload.defaultVisionModel.trim() }
        return if (raw.isNotEmpty()) provider.supportedModelOrDefault(raw) else provider.defaultModel
    }

    fun defaultTextProvider(): AIProvider {
        val raw = payload.defaultTextProvider.trim()
        val parsed = parseAIProvider(raw)?.takeIf { it in AIProvider.textProviders }
        if (parsed != null && (!parsed.requiresApiKey || apiKeyFor(parsed) != null)) {
            return parsed
        }
        return defaultVisionProvider()
    }

    fun defaultTextModel(provider: AIProvider = defaultTextProvider()): String {
        val raw = BuildConfig.APEX_SYSTEM_DEFAULT_TEXT_MODEL.trim()
            .ifEmpty { payload.defaultTextModel.trim() }
        return if (raw.isNotEmpty()) provider.supportedTextModelOrDefault(raw) else provider.defaultTextModel
    }

    fun defaultSpeechProvider(): SpeechProvider {
        val raw = BuildConfig.APEX_SYSTEM_DEFAULT_SPEECH_PROVIDER.trim()
            .ifEmpty { payload.defaultSpeechProvider.trim() }
        val parsed = parseSpeechProvider(raw)
        if (parsed != null) {
            if (!parsed.requiresApiKey || speechApiKeyFor(parsed) != null) return parsed
        }
        val matched = SpeechProvider.matchingPrimaryAIProvider(defaultVisionProvider())
        if (matched != null && speechApiKeyFor(matched) != null) return matched
        return SpeechProvider.NATIVE
    }

    @Serializable
    data class SystemAIConfigPayload(
        val defaultProvider: String = "GEMINI",
        val defaultVisionModel: String = "",
        val separateTextProviderEnabled: Boolean = false,
        val defaultTextProvider: String = "",
        val defaultTextModel: String = "",
        val fallbackEnabled: Boolean = false,
        val fallbackProvider: String = "",
        val fallbackModel: String = "",
        val defaultSpeechProvider: String = "NATIVE",
        val defaultVoiceAssistantProvider: String = "AUTOMATIC",
        val apiKeys: Map<String, String> = emptyMap(),
        val customBaseUrls: Map<String, String> = emptyMap()
    )

    companion object {
        private const val ASSET_FILE_NAME = "apex-ai-config.json"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        @Volatile
        private var cached: APEXSystemAIConfig? = null

        fun get(context: Context): APEXSystemAIConfig {
            cached?.let { return it }
            return synchronized(this) {
                cached ?: loadFromAssets(context.applicationContext).also { cached = it }
            }
        }

        private fun loadFromAssets(context: Context): APEXSystemAIConfig {
            val parsed = runCatching {
                context.assets.open(ASSET_FILE_NAME).bufferedReader().use { reader ->
                    json.decodeFromString(SystemAIConfigPayload.serializer(), reader.readText())
                }
            }.getOrDefault(SystemAIConfigPayload())
            return APEXSystemAIConfig(parsed)
        }

        private fun aliasesFor(provider: AIProvider): List<String> = when (provider) {
            AIProvider.GEMINI -> listOf("GEMINI", "Google Gemini")
            AIProvider.OPENAI -> listOf("OPENAI", "OpenAI")
            AIProvider.ANTHROPIC -> listOf("ANTHROPIC", "Anthropic Claude")
            AIProvider.XAI -> listOf("XAI", "xAI Grok")
            AIProvider.OPENROUTER -> listOf("OPENROUTER", "OpenRouter")
            AIProvider.TOGETHER_AI -> listOf("TOGETHER_AI", "TOGETHERAI", "Together AI")
            AIProvider.GROQ -> listOf("GROQ", "Groq")
            AIProvider.HUGGING_FACE -> listOf("HUGGING_FACE", "HUGGINGFACE", "Hugging Face")
            AIProvider.FIREWORKS -> listOf("FIREWORKS", "Fireworks AI")
            AIProvider.DEEP_INFRA -> listOf("DEEP_INFRA", "DEEPINFRA", "DeepInfra")
            AIProvider.MISTRAL -> listOf("MISTRAL", "Mistral")
            AIProvider.DEEPSEEK -> listOf("DEEPSEEK", "DeepSeek")
            AIProvider.QWEN -> listOf("QWEN", "Alibaba Qwen")
            AIProvider.CEREBRAS -> listOf("CEREBRAS", "Cerebras")
            AIProvider.LOCAL_GEMMA -> listOf("LOCAL_GEMMA", "GEMMA4LOCAL")
            AIProvider.OLLAMA -> listOf("OLLAMA", "Ollama (Local)")
            AIProvider.CUSTOM_OPENAI -> listOf("CUSTOM_OPENAI", "CUSTOMOPENAI", "Custom (OpenAI-compatible)")
        }

        private fun parseAIProvider(raw: String): AIProvider? {
            if (raw.isBlank()) return null
            val normalized = raw.trim().uppercase().replace("-", "_").replace(" ", "_")
            return AIProvider.entries.firstOrNull { entry ->
                entry.name.equals(normalized, ignoreCase = true) ||
                    aliasesFor(entry).any { it.equals(raw.trim(), ignoreCase = true) }
            }
        }

        private fun parseSpeechProvider(raw: String): SpeechProvider? {
            if (raw.isBlank()) return null
            return when (raw.trim().uppercase().replace("-", "_").replace(" ", "_")) {
                "NATIVE", "NATIVE_IOS", "ON_DEVICE" -> SpeechProvider.NATIVE
                "LOCAL_WHISPER", "WHISPER_BASE", "WHISPER" -> SpeechProvider.LOCAL_WHISPER
                "GEMINI", "GEMINI_AUDIO" -> SpeechProvider.GEMINI
                "OPENAI", "OPENAI_WHISPER" -> SpeechProvider.OPENAI
                "GROQ", "GROQ_WHISPER" -> SpeechProvider.GROQ
                "MISTRAL", "MISTRAL_VOXTRAL" -> SpeechProvider.MISTRAL
                "DEEPGRAM" -> SpeechProvider.DEEPGRAM
                "ASSEMBLYAI", "ASSEMBLY_AI" -> SpeechProvider.ASSEMBLY_AI
                else -> null
            }
        }
    }
}
