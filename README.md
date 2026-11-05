# APEX Fitness Gym

APEX is a mobile fitness and nutrition app for Android and iOS. It combines a workout diary, exercise library, training tools, food tracking, and an AI assistant in one app.

**Development status:** the newest workout features are first-pass implementations and have not yet been built or tested on devices. Google account login and cross-device sync are planned but are not implemented. See [continuation.md](continuation.md) for the current handoff and outstanding work.

## Workout features

- Plan and log workouts, exercises, sets, reps, weights, and effort.
- Browse the exercise library and view workout history.
- Use the offline Plate Calculator to find a balanced plate setup that does not exceed your target.
- Review weekly training, record a readiness check, view logged weight milestones, and find exercise alternatives.
- Explore the body map, seven-day activity-based recovery estimate, Machine Guide, Training Coach, Consistency view, and workout sharing. These recent additions need native verification before release.

Training suggestions, readiness notes, and recovery estimates are informational, not medical advice. Confirm equipment and loads before lifting. Machine identification is uncertain and must be reviewed.

## Nutrition and assistant

APEX includes food and nutrition logging, progress tracking, and an AI assistant. AI features depend on a working provider configuration and may send content to that provider when used. Review the applicable privacy disclosures and configuration before release. The app does not require a general account to use its local features.

## For developers

1. Clone [jusnie/apex-app](https://github.com/jusnie/apex-app).
2. For Android, open `android/` in Android Studio and install the SDK versions specified in `android/app/build.gradle.kts`.
3. For iOS, open `ios/APEX.xcodeproj` in Xcode and select the shared `APEX` scheme.
4. Read [continuation.md](continuation.md) for project structure, setup, feature status, testing priorities, and the unfinished Google login and sync work.
5. Read [SYSTEM_API_SETUP.md](SYSTEM_API_SETUP.md) before configuring AI. Never commit secrets or production signing credentials.

Android unit tests can be run from `android/` with `./gradlew testDebugUnitTest` on a configured development machine. Run iOS tests with the `APEX` scheme and an installed simulator in Xcode. The newest native code has not yet been compiled or device-tested in this workspace; test it before distributing a build.

## Privacy and sync status

Workout and training data are primarily local. Existing optional Android Google Drive backup and iOS iCloud backup are separate platform-specific features. They are not cross-platform Google account sync. Before implementing account sync, define consent, private per-user access, safe conflict handling, and an offline migration strategy. Never silently overwrite a user's existing data.

## Contributing and support

Use the repository's [issues](https://github.com/jusnie/apex-app/issues) and pull requests to report problems or propose changes. Remove sensitive data from logs and screenshots. Report security issues privately through the repository's security reporting feature when available.

## License and notices

See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Exercise-dataset notices are in `ios/APEX/Resources/FreeExerciseDB/`. Other dependencies may carry their own licenses and notices; retain all applicable attribution when distributing the app.
