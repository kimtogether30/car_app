# Project working requirements

- Preserve the user's PC performance budget (16 GB RAM). Avoid unnecessary animations, polling, heavy dependencies, main-thread disk I/O, and repeated work during Compose recomposition. Keep Gradle worker limits unless measurements justify changing them.
- Develop and validate for the Hyundai Motor Group vehicle app development contest and its linked official Pleos documentation. Verify current official requirements before changing SDK, emulator, vehicle integration, or submission assumptions. Do not assume that generic Android emulator optimizations are compatible with Pleos.
- Keep Pleos Connect v2.1.0 / API 34 emulator with its official device profile and 4 GB RAM. Use Cold Boot: official documentation states Quick Boot cannot use VHAL. Do not lower resolution or memory to mask performance problems.
- Preserve the required Android minimum API 28 or higher, Java/Kotlin development, and Gradle 8 or higher. Check official documents for updates rather than interpreting these as permanently fixed versions.
- Design readable vehicle-screen interfaces: generous text and touch targets, balanced positioning, bounded content widths, and scrolling for long forms. Check actual Pleos screenshots after visual changes. Preserve user data when reinstalling or testing; do not wipe profiles.
- Official sources: https://appdevchallenge.auto-hmg.io/faq and https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator and https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator

## Deferred Bluetooth validation and wash policy

- Keep deferred physical-device Bluetooth checks together in `docs/BLUETOOTH_VALIDATION_CHECKLIST.md`; append new cases there and do not treat emulator/injected-frame results as radio validation. The user currently has emulators only and wants to run these checks together later.
- Follow the uploaded facility-specific wash criteria: conveyor N/motion/D and stationary confirmed dwell/exit are different patterns. Never impose an N-only rule on stationary/P-based washes. Once the criteria qualify an estimated wash, apply it and show “세차한 것 같아!” with “세차 안 했어요” to reverse that visit; uncertainty about whether actual washing occurred is not an extra veto.
- For a verified dedicated self-wash facility or wash area clearly separated from fuel pumps, the full qualifying visit pattern can use “세차 완료!” instead of a question. Shared/uncertain layouts keep the correction question. Facility entry, a nearby POI, or P/N alone never constitutes completion. This certainty is a product inference, not a claimed SDK water-wash signal.

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
