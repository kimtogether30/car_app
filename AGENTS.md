# Project working requirements

- Preserve the user's PC performance budget (16 GB RAM). Avoid unnecessary animations, polling, heavy dependencies, main-thread disk I/O, and repeated work during Compose recomposition. Keep Gradle worker limits unless measurements justify changing them.
- Validate vehicle and phone emulators one at a time. Close the current emulator before starting the other unless a specific test actually requires both devices running together.
- Keep phone verification APKs on the phone AVD, not Pleos. The old `com.example.vemoring.phone.verification` install was disabled for Pleos user10 to remove the duplicate VEMORING launcher entry while preserving its data. Do not re-enable/reinstall it on Pleos during routine vehicle checks.
- The phone verification package is also disabled for phone user0 after tests so only the real phone app appears. Enable it on the phone AVD when running authorized verification tests, then disable it again after testing; preserve both apps' data. Its test runner `com.example.vemoring.phone.VerificationRunner` also hides the test copy's launcher entries when every run ends (and restores the normal entry when a run starts), so a forgotten disable no longer leaves a second VEMORING icon. Use that runner name in `am instrument` commands.
- Develop and validate for the Hyundai Motor Group vehicle app development contest and its linked official Pleos documentation. Verify current official requirements before changing SDK, emulator, vehicle integration, or submission assumptions. Do not assume that generic Android emulator optimizations are compatible with Pleos.
- Keep Pleos Connect v2.1.0 / API 34 emulator with its official device profile and 4 GB RAM. Use Cold Boot: official documentation states Quick Boot cannot use VHAL. Do not lower resolution or memory to mask performance problems.
- Preserve the required Android minimum API 28 or higher, Java/Kotlin development, and Gradle 8 or higher. Check official documents for updates rather than interpreting these as permanently fixed versions.
- Design readable vehicle-screen interfaces: generous text and touch targets, balanced positioning, bounded content widths, and scrolling for long forms. Check actual Pleos screenshots after visual changes. Preserve user data when reinstalling or testing; do not wipe profiles.
- Official sources: https://appdevchallenge.auto-hmg.io/faq and https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator and https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator

## Deferred Bluetooth validation and wash policy

- Keep physical-device Bluetooth checks together in `docs/BLUETOOTH_VALIDATION_CHECKLIST.md`; append new cases there and distinguish actual radio transport with synthetic fixtures from actual vehicle SDK/sensor validation. On 2026-11-13 the user supplied a Galaxy S24 (phone role) and Tab S7 (vehicle-app role) for the deferred radio checks; these are not Pleos vehicle hardware. Keep test garages/frames in isolated verification application IDs.
- The user's later 2026-11-12 instruction supersedes the earlier radius-only policy: production estimates require a distinguishable mapped wash area. Conveyor: observed three-minute in-area dwell including movement, corroborated N motion, then D and vehicle exit. Stationary: three minutes of actual stationary in-area observations, then vehicle exit. Same-place resume exception (latest user instruction): saved in-area zero-speed fix with fresh gear, receiving gap, then fresh in-area zero-speed fix with fresh gear within20m may count elapsed wall time toward a3-minute EstimatedVisit for either wash type, followed by vehicle exit and the confirmation banner. Missing data alone, nearby POI, fuel/waiting areas and P/N alone never qualify. See `docs/STRICT_WASH_IMPLEMENTATION.md` for thresholds and map-coverage limits.
- Apply only EstimatedVisit and ask “세차한 것 같아!” with “맞아요” / “세차 안 했어요”. No answer or yes keeps the applied estimate; no cancels and preserves rejection tombstones across offline edits/replay. Keep the older `WashVisitDetector` as the explicit-evidence/test path. Phone fixes cannot supply vehicle stationary/motion/gear evidence or finish a visit alone. Old radius-only raw evidence must not generate new estimates after this policy change; preserve existing user records and cancellations.

## Multiple vehicles, deletion, and receiving

- Keep each registration's persisted vehicleId stable across model, nickname, fuel and optional-plate edits. Use model/plate hashes only to migrate existing single-vehicle keys; never identify two new registrations by an empty/equal plate.
- Viewing another saved car must not rebind the vehicle SDK source. Keep the receiving car separate and require explicit confirmation to change it. Never publish test/preview garages into the real companion stream.
- An unknown date preserves existing records. Deletion is a separate confirmed action; preserve deletion IDs/tombstones through offline edits, stale replies and retransmission. A genuinely new record gets a new ID.
- Background vehicle receiving and launcher-icon updates are independent settings. Keep the shared SDK lease while the receiving service is allowed to run, even if the activity is hidden or icon updates are off. Do not fabricate availability when the SDK, permissions, OS or transport actually prevent receiving.

## Graphify usage

- Graphify is a development-only local navigation aid; do not add it to Android dependencies or change contest runtime requirements for it.
- Use `tools\graphify.cmd query "symbol or relationship" --budget 1500` for relevant structural questions when `graphify-out/graph.json` exists. Confirm important findings in source, especially inferred relationships and Compose callbacks.
- Refresh on demand with `tools\graphify.cmd` (code-only, one AST worker). This wrapper also limits numeric-library threads. Do not run a watcher, local LLM, automatic full rebuild on every edit, or large visualization during emulator/build work.
- Keep generated indexes out of Git. No paid cloud, semantic document/media processing, external uploads, or agent hooks are configured. The Codex Graphify skill is installed for optional explicit use; these project resource limits still apply.

## Engine oil policy (2026-11-15)

- General maintenance and warning-light product paths are retired. Keep only legacy serialization compatibility; only explicitly marked ENGINE_OIL records reset the oil cycle.
- Follow `docs/engine-oil/ENGINE_OIL_POLICY.md` and the supplied JSON. The latest 30-minute verified vehicle-power OFF→ON policy supersedes the earlier 20-minute continuous-dwell proposal. Do not use Bluetooth loss, engine idle stop, RPM=0, P/N or zero speed as vehicle power OFF.
- Use model-specific severe recurring intervals and label model-only shortest references honestly. Preserve enrollment anchors; initial history may use 20km/day, but later actual odometer gaps must never also receive average distance.
- No vehicle power API is currently validated in Vehicle SDK 2.3.2. Keep automatic garage confirmation inactive until the required real power/location evidence is supported and tested; manual engine-oil records remain available. Do not call a fixture a physical ignition/garage test.
