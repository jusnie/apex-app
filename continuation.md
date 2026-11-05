# APEX developer handoff

Last updated: 5 November 2026. This is a development handoff, not a release checklist. The main `README.md` describes the project; verify implementation details in the current code and this handoff.

## Get the project

Repository: <https://github.com/jusnie/apex-app>. Clone it, open `android/` in Android Studio, or open `ios/APEX.xcodeproj` in Xcode. The shared Xcode scheme is `APEX`. Android uses Kotlin and Jetpack Compose; iOS uses Swift and SwiftUI. The project also includes Apple Watch, widgets, and a share extension. App version in the current configuration is 2.5.8 (258); verify this before any release.

Requirements and versions can change: consult `android/gradle/libs.versions.toml`, `android/gradle/wrapper/gradle-wrapper.properties`, `android/app/build.gradle.kts`, and the Xcode project. The current Android config specifies JDK 17 in CI, Gradle 9.8.1, AGP 9.4.1, compile SDK 37.2, and minimum SDK 26. iOS deployment target for the main app is 17.6; use an Xcode/Swift toolchain compatible with the checked-in project and its dependencies. Install platform SDKs and resolve dependencies on your development machine.

## Current status

The recent workout features are **first-pass implementations, not release-verified**. In this environment there was no Android SDK or Swift/Xcode compiler, so no full Android or iOS build, Swift typecheck, simulator test, or device test was performed. A standalone Kotlin check ran seven plate-calculator unit tests plus 3,000 randomized comparisons against brute force. Three Training Tools domain tests were also run with minimal model stubs; that is not a test of native integration. Syntax parsing and `git diff --check` were performed. Other native test files were added but have not been run.

- **Plate Calculator**: accessible from the workout diary on Android and iOS. Offline, editable bar and total plate inventory, kg/lb, equal sides, exact or nearest non-exceeding load. Counts for duplicate plate sizes are combined. The bar is included; collars are excluded. Equipment settings are stored separately per unit after a valid calculation. The prefilled inventory is an example and needs user verification.
- **Training Tools**: weekly summary and local reflection, self-reported readiness, heaviest-logged-weight milestones, and metadata-based exercise alternatives. A swap requires confirmation and only replaces an untouched entry; it must never discard logged sets or a started timer. These tools are preliminary and should be checked with real data. The readiness screen does not diagnose or automatically change a workout.
- **Machine Guide**: optional image analysis after user action. Treat identification as uncertain and review privacy before sending photos.
- **Training Coach / Consistency**: historical workout suggestions and monthly summaries. Logged workload is not a measurement of recovery. Review suggestions before applying them; sharing and AI calls require testing.
- **Body map, recovery heatmap, and workout share**: earlier first-pass features remain in the codebase and need native/device validation. The seven-day display is an estimate based on logged activity, not a medical recovery measurement.
- **Streak Shield**: not implemented; rules have not been specified.

### Important: Google login and account sync are not implemented

The owner asked for optional Google login and cross-device sync. The Supabase project named `Apex Fitness Gym` was restored with the owner's approval; restoring it can resume service costs. Google is not configured/enabled in the project as last checked. The project already contains sync-related tables and an RPC; inspect the existing schema and policies before changing anything. **Do not treat existing backup code or the restored project as functioning Google account sync.**

To configure Google, the owner should set up a Google OAuth web client and enable Google in the project's Supabase Authentication provider settings. The Google authorized redirect URI for this project is:

```text
https://titfgsbwicqphxtvhppm.supabase.co/auth/v1/callback
```

Enter the client secret directly into the provider configuration, not the repository or chat. After configuration, the app still needs a properly implemented native OAuth flow, secure session/token storage, sign-out and account-deletion handling, and a tested sync engine. Consider Apple App Store sign-in requirements when designing equivalent iOS login options. Never embed a Supabase service-role key or Google client secret in either app.

Sync must be designed before enabling it: define a shared cross-platform data schema, migration strategy, local-first behavior, conflict resolution, deletion/tombstones, retries, account switching, consent/privacy disclosures, and user-scoped server-side access controls. Explicitly test two-device edits, offline changes, deletion, first sign-in with existing data, and failed restores. Never overwrite local history silently. Do not promise that all data, images, equipment settings, or new readiness/check-in data sync until each category is implemented and verified. Existing Android Google Drive backup and iOS iCloud backup are separate platform-specific mechanisms, not the requested cross-platform sync.

## Where to work

| Area | Android | iOS |
| --- | --- | --- |
| Workout log entry point | `android/app/src/main/java/com/apex/app/ui/workouts/WorkoutDiaryScreen.kt` | `ios/APEX/Views/WorkoutLogView.swift` |
| Plate calculator | `models/PlateLoadingCalculator.kt`, `ui/workouts/PlateCalculatorSheet.kt` under `android/app/src/main/java/com/apex/app/` | `ios/APEX/Models/PlateLoadingCalculator.swift`, `ios/APEX/Views/PlateCalculatorView.swift` |
| Weekly, readiness, records, swaps | `models/TrainingTools.kt`, `ui/workouts/TrainingToolsSheet.kt` | `ios/APEX/Models/TrainingTools.swift`, `ios/APEX/Views/TrainingToolsView.swift` |
| Workout persistence and safe swap | `android/app/src/main/java/com/apex/app/data/WorkoutRepository.kt` | `ios/APEX/Stores/StrengthWorkoutStore.swift` |
| Coach and consistency | `models/WorkoutInsights.kt`, `ui/workouts/TrainingInsightsSheet.kt` | `ios/APEX/Models/WorkoutInsights.swift`, `ios/APEX/Views/TrainingInsightsView.swift` |
| Machine Guide | `ui/workouts/MachineGuideSheet.kt`, `services/ai/WorkoutFeatureAI.kt` | `ios/APEX/Views/MachineGuideView.swift` and related services |
| Body map and share | `ui/workouts/WorkoutBodyMapSheet.kt`, `WorkoutShareSheet.kt` | `ios/APEX/Views/WorkoutBodyMapView.swift`, `WorkoutShareSheetView.swift` |
| Assistant response policy | `services/ai/AssistantOutputPolicy.kt`, voice/chat integration | Search `ios/APEX` for `AssistantOutputPolicy` |

Android paths in the short entries above are relative to `android/app/src/main/java/com/apex/app/`. Tests are under `android/app/src/test/` and `ios/APEXTests/`. In particular, see `PlateLoadingCalculatorTest(s)`, `TrainingToolsTest(s)`, the workout repository/store tests, and workout insight tests. The iOS project uses synchronized source groups; do not add app-only source files to the widget extension target's inclusion set.

## Development and validation

1. Check `git status` and create a branch before changing code. Keep local user data and signing files out of commits.
2. Inspect the current models and persistence before modifying screens. Handle older saved data and unit conversions explicitly. Keep both platforms consistent where practical.
3. With the owner's approval to run native builds/tests, run Android unit tests from `android/` using `./gradlew testDebugUnitTest`. For iOS, select the `APEX` scheme and an installed simulator in Xcode and run the test suite; `xcodebuild -list -project ios/APEX.xcodeproj` lists schemes. Resolve SDK/dependency or compile errors rather than assuming the current code builds.
4. Test fresh installs, upgrades containing existing data, orientation, small screens, accessibility, keyboard behavior, reduced motion, both units, offline use, and error/empty states. Test swap refusal with logged sets/timers and safe persistence failure. Validate photo sharing and permissions on devices.
5. Do not create APK/IPA releases or run release workflows unless the owner explicitly asks. The existing `.github/workflows/build-apk.yml` is manually dispatched; inspect its signing behavior and secrets before any release. Its fallback-generated signing key is not an established production signing identity.
6. Review `git diff --check`, tests, and the complete diff before committing. Never force-push or overwrite a remote branch without explicit authorization.

## AI configuration and secrets

Android's manual GitHub APK workflow reads system AI values from repository secrets. The checked-in iOS `ios/APEX/Resources/AIConfig/apex-ai-config.json` has empty key values, and there is currently **no configured iOS CI secret-injection workflow**. See `SYSTEM_API_SETUP.md` for the current configuration caveats; it is not proof of a working iOS key or a secure release architecture. Keys bundled into mobile applications can be extracted; use a properly authenticated server-side gateway for production where possible. Do not commit API keys, OAuth secrets, signing keystores, or service-role credentials. `android/oauth.properties`, `local.properties`, and `ios/Local.xcconfig` are local-only.

## Remaining release-link audit

Some existing runtime URLs and store identifiers predate this handoff, including `apex.app` links, app-store destinations, asset hosting, and Product Hunt references. Their ownership and current availability have not been verified. Before release, replace or configure them with owner-approved destinations and verify legal/privacy pages, deep links, notifications, sharing, and exercise assets on both platforms. Do not blindly change functional endpoints or claim another project’s certifications.

## Design and project notes

The owner's preference is a crisp modern interface with simple vector icons, no decorative reaction graphics, and no pictographic characters in assistant output. Preserve accessibility and reduced-motion behavior. Earlier HTML previews were samples only and were removed during workspace cleanup; use the native code as the current source of truth. The owner previously rejected an unrelated calorie UI redesign. User uploads are not part of the repository.
