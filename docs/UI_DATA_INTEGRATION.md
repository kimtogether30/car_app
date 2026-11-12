# Local UI and SDK integration

Merged origin/main through aa9d228 while retaining the local centered home, launcher
artwork, Korean setup flow, installed-map picker and asynchronous garage persistence.
The automatic pre-merge stash 621d1a0 is retained as a recovery copy.

- MainActivity owns VehicleViewModel and IntegrationViewModel and forwards foreground
  lifecycle events. The old battery-only PleosVehicle is no longer instantiated.
- The existing home consumes VehicleSnapshot; no duplicate service subscription.
- Data connection opens the contributor's location/weather/nearby/navigation and
  maintenance settings screen. Existing map selection stays installed-app-only.
- Confirmed non-fuel vehicle telltales drive purple. Scheduled oil replacement stays separate; fuel warnings count only as yellow energy signals.
- Maintenance preferences load/save on IO and notify the home through a preference
  listener; changing vehicle or the matching record invalidates old distance assumptions.
- Missing values display no data and do not manufacture warnings. The default is blue
  until a supported signal or valid maintenance estimate establishes an issue.
- SDK requests now complete their accounting on timeout, ignoring late responses and
  allowing closed sessions to release. Only telltales lacking public listeners are
  refreshed every 30 seconds in the foreground, rather than rechecking all safety fields.
- 2026-11-12: after the user selects a forecast region, the shared home weather panel refreshes its forecast while visible; see `WEATHER_UI.md`. It does not request a new SDK location subscription.
- Launcher changes are deferred until MainActivity.onStop. The character changes
  immediately, but Pleos must not disable the visible screen's launcher alias. MainActivity
  is always enabled; only aliases change, and worker-thread operations are serialized.

Checks and limitations: unit tests cover unsupported/Demo values, real 0%, threshold
boundaries, EV/hybrid selection, maintenance-record identity, mileage confirmation and
combined moods. Emulator tests separately verify actual numeric SDK reception. Location
and external API reception require permission and explicit feature use; build success
alone does not establish those services work on every vehicle.

Validation on this PC: 23 JVM tests passed, 2 launcher transition tests passed and
7 UI/SDK tests passed (including numeric real-SDK reception and field-resolution smoke
test). Final APK installed with existing garage data preserved. Historical dock records
were re-registered with keep-data after migrating to the always-enabled screen model.

## Setup vehicle identification

`VehicleGateway.identify` is the explicit vehicle-identification boundary and
`VehicleViewModel.identity` publishes its result. The current Vehicle 2.3.2 adapter
returns Unsupported: it has no confirmed brand/model/powertrain getter. It does not
decode VIN, infer a hybrid from fuel/SOC, or substitute demo data.
Only Available typed identity values prefill setup. Missing fields remain questions;
the question count and back navigation follow the actual remaining steps. Nickname,
care history and alert confirmation stay manual. Supplied plate is retained too.
Detection is frozen once questions begin, has a four-second fallback for future
asynchronous adapters, and supports an immediate manual-entry action. Existing saved
profiles are not overwritten; corrections remain available in My Car settings.
