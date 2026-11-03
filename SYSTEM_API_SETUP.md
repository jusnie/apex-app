# APEX Built-In System API Setup

APEX runs with a **built-in system AI configuration** — end users never see subscription paywalls, credit packs, or "Bring Your Own API Key" screens during onboarding or inside Settings.

As the app owner, you configure the AI provider(s), models, and API keys once in a single shared system config (or via build-time environment variables / `local.properties`). Both the **iOS** and **Android** apps read from this same configuration automatically.

---

## Option 1: Edit the Shared System Config File (Fastest)

Open:

```text
ios/APEX/Resources/AIConfig/apex-ai-config.json
```

*(Xcode bundles this file automatically into the iOS app, and Gradle packages the exact same directory into Android's `assets/` at build time.)*

### Example: Google Gemini (Food Vision + Coach + Live Voice + Speech)

```json
{
  "defaultProvider": "GEMINI",
  "defaultVisionModel": "",
  "defaultSpeechProvider": "GEMINI",
  "defaultVoiceAssistantProvider": "GEMINI",
  "apiKeys": {
    "GEMINI": "AIzaSyYOUR_SYSTEM_GEMINI_KEY_HERE",
    "USDA_FDC": "YOUR_OPTIONAL_USDA_FDC_KEY"
  }
}
```

### Example: Custom Proxy / Backend Gateway (Recommended for Public Store Releases)

If you host your own lightweight proxy (e.g., Cloudflare Worker, Supabase Edge Function, or LiteLLM) so raw provider keys stay on your server, set `"defaultProvider": "CUSTOM_OPENAI"` (or override any provider's base URL in `"customBaseUrls"`):

```json
{
  "defaultProvider": "CUSTOM_OPENAI",
  "defaultVisionModel": "gpt-4.1-mini",
  "apiKeys": {
    "CUSTOM_OPENAI": "apex-app-client-token"
  },
  "customBaseUrls": {
    "CUSTOM_OPENAI": "https://ai-gateway.yourdomain.com/v1"
  }
}
```

---

## Option 2: Build-Time Environment Variables / `local.properties` (No Secrets in Git)

Environment variables, Android `local.properties` / `gradle.properties`, and iOS `Info.plist` / Xcode Scheme environment variables automatically take precedence over `apex-ai-config.json` when non-empty:

| Key | Description |
| --- | --- |
| `APEX_SYSTEM_DEFAULT_PROVIDER` | Primary AI provider (`GEMINI`, `OPENAI`, `ANTHROPIC`, `XAI`, `OPENROUTER`, `GROQ`, `MISTRAL`, `DEEPSEEK`, `QWEN`, `CUSTOM_OPENAI`, etc.) |
| `APEX_SYSTEM_DEFAULT_VISION_MODEL` | Optional vision model override (leave blank for provider default) |
| `APEX_SYSTEM_DEFAULT_TEXT_MODEL` | Optional text/coach model override |
| `APEX_SYSTEM_DEFAULT_SPEECH_PROVIDER` | Speech-to-text provider (`NATIVE`, `GEMINI`, `OPENAI`, `GROQ`, `MISTRAL`, `DEEPGRAM`, `ASSEMBLYAI`) |
| `APEX_SYSTEM_GEMINI_API_KEY` | Google Gemini API key |
| `APEX_SYSTEM_OPENAI_API_KEY` | OpenAI API key |
| `APEX_SYSTEM_ANTHROPIC_API_KEY` | Anthropic Claude API key |
| `APEX_SYSTEM_XAI_API_KEY` | xAI Grok API key |
| `APEX_SYSTEM_OPENROUTER_API_KEY` | OpenRouter API key |
| `APEX_SYSTEM_GROQ_API_KEY` | Groq API key |
| `APEX_SYSTEM_MISTRAL_API_KEY` | Mistral API key |
| `APEX_SYSTEM_DEEPSEEK_API_KEY` | DeepSeek API key |
| `APEX_SYSTEM_QWEN_API_KEY` | Alibaba Qwen API key |
| `APEX_SYSTEM_CUSTOM_OPENAI_API_KEY` | Custom OpenAI-compatible API key / bearer token |
| `APEX_SYSTEM_CUSTOM_OPENAI_BASE_URL` | Custom OpenAI-compatible base URL |
| `APEX_SYSTEM_DEEPGRAM_API_KEY` | Deepgram Speech-to-Text API key |
| `APEX_SYSTEM_ASSEMBLYAI_API_KEY` | AssemblyAI Speech-to-Text API key |
| `APEX_SYSTEM_USDA_FDC_API_KEY` | Optional USDA FoodData Central barcode fallback key |

### Smart Auto-Selection

If `"defaultProvider"` is left at `"GEMINI"` without a Gemini key, **APEX automatically selects the first vision-capable provider that has a non-empty system key** in `apiKeys` or environment variables (e.g., if you only paste an `"OPENAI"` key, APEX automatically switches its active vision/text provider to OpenAI).
