# APEX AI configuration

APEX contains app-owner configuration for AI providers. The checked-in configuration at `ios/APEX/Resources/AIConfig/apex-ai-config.json` contains empty API key values. A provider-dependent feature is not ready until an appropriate configuration exists and has been tested.

## Security first

Do not commit API keys or place production provider secrets in mobile app resources, `BuildConfig`, `Info.plist`, or client-side code. Values embedded in an APK or iOS app can be extracted, even if injected by CI. For a production release, prefer a properly authenticated server-side gateway that holds provider credentials, applies authorization and rate limits, and follows the provider's terms. Review privacy and data-retention policies for photos, voice, and workout summaries before sending them to any service.

## Current implementation

- **Android:** `android/app/src/main/java/com/apex/app/data/APEXSystemAIConfig.kt` reads the packaged JSON and build-time overrides declared in `android/app/build.gradle.kts`. The manual APK workflow can inject selected `APEX_SYSTEM_*` values from GitHub Actions secrets. Injected values become part of the built client and are not a secure secret store.
- **iOS:** `ios/APEX/Services/APEXSystemAIConfig.swift` reads the bundled JSON and supports process-environment or Info.plist overrides. A developer can use environment values for local testing, but they are not a deployment mechanism for installed apps. The current repository has no iOS CI workflow configured to inject a Gemini key. Info.plist values bundled into an app are extractable.
- The checked-in JSON is shared as a configuration resource; that does not mean both platforms have a working key. Confirm the actual provider and model at runtime and test the requested feature on each platform.

Provider and model overrides, aliases, and fallback behavior are implemented in those source files. See the Gradle file for the complete supported Android override names. Do not assume every field has the same override path on both platforms.

## Local development

Use disposable, restricted development credentials and the platform's local environment or ignored configuration where supported. Do not paste credentials into committed files, documentation, screenshots, issues, or chat. Keep `android/local.properties`, `android/oauth.properties`, `ios/Local.xcconfig`, keystores, and private keys out of Git.

## Google login is separate

AI provider configuration is unrelated to Google account login. Google login and cross-device account sync are not implemented in the current app. See [continuation.md](continuation.md) for the approved Supabase project and remaining setup and engineering work.
