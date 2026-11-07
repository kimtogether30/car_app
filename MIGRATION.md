# VEMORING Kotlin migration

Destination: `C:\Users\zlxld\AndroidStudioProjects\Vemoring`.

The destination is a standalone Kotlin / Jetpack Compose application. It does not depend on the Flutter runtime.

## Preserved features

- Hyundai / Kia / Genesis vehicle catalog, brand and model onboarding, fuel type, last care dates, oil estimate, nickname, setup completion.
- Home with the original blue car character, status cards, latest care dates, weather example and notifications example.
- Calendar month navigation, date selection, latest/all records, care markers, record creation and editing, title/details/date, future-date rejection.
- Vehicle profile editing including plate, model, fuel type, nickname and latest care dates. Alert thresholds and notification preference persist across restarts.
- Pleos Vehicle SDK `2.3.2` EV battery capability check, initial read, subscriptions and lifecycle cleanup, using the existing Android integration as the reference.
- Imports the existing Android Flutter SharedPreferences profile and history when the same app is updated in place. Other devices' data is not automatically transferred. Original preferences remain untouched. New writes use `vemoring_native` / `garage_v1`.

## Boundaries

- Fuel and cleanliness remain unavailable when no source provides them. Weather and notifications remain explicitly labeled examples, as in the Flutter application.
- SDK unavailable/unsupported states are displayed without fabricating vehicle readings.
- Running on Pleos is not a certification of full contest or App Market compliance. Vehicle permissions, UX restrictions, release signing and final submission requirements still need review before publishing.
- The app retains `com.example.vemoring` so a matching debug signing key can update the existing emulator installation and preserve its data. A signing mismatch must not be fixed by uninstalling without backing up data.

## Development

Open the destination folder in Android Studio. Sync Gradle, select the Pleos Connect API 34 emulator, and run `app`.

The generated project retains its compile/target SDK settings and requires minSdk 28. Vehicle SDK version and emulator version are different components.

`src/` contains the migration source snapshot. `generate-assets.ps1` converts the existing Dart catalog and Canvas paths to Kotlin. Backups of the destination's original source/settings are saved as `native-before-*.zip` before deployment.

Instrumentation tests cover legacy data import, persistent profile/history, latest-record updates without duplication, future-date rejection and the onboarding → record creation/editing → profile editing flow.

## Verified on 2026-11-07

- Debug app and instrumentation APK build succeeded.
- Five instrumentation tests passed on Pleos Connect 2.1.0 API 34 (including the template package-name test).
- One additional test passed using the actual MainActivity launcher entry point, asserting the VEMORING screen is displayed.
- Final regular launch was visually verified outside instrumentation: the Korean brand-selection screen remains visible in the Pleos app panel.
- Added singleTop and configuration-change handling for Pleos task-window changes. The emulator's built-in navigation also produced an ANR during initial verification; it was stopped temporarily while diagnosing the app panel. It was not uninstalled or disabled.
- The active emulator user did not have the original Flutter preference file, so it starts at vehicle setup. Legacy import was verified with isolated test fixtures, without writing sample records into the user's actual garage.
