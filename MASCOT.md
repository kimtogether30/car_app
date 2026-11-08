# Character and launcher states

The in-app character, speech bubble and launcher face share `MascotMood`.
`VehicleViewModel` owns the single live vehicle connection. `careCondition` converts
real Available readings, saved maintenance records and confirmed maintenance settings
into `CareSignals`. Unknown, error, unsupported and Demo values never become real fuel
warnings. The optional `GarageApp(careSignals = ...)` override remains for previews.
Fuel and SOC use the user's alert threshold and powertrain. Cleanliness is explicitly
a record/confirmed-distance/optional recent air-quality estimate, never a dirt sensor.
Scheduled oil change requires a matching oil record, user-confirmed distance unit,
replacement mileage and maintenance interval. Oil level/pressure warnings stay separate
as vehicle safety alerts. Demo mode does not change the real launcher icon. Disconnecting
the foreground vehicle session does not reset the launcher's last known mood.
The character changes immediately. The launcher update is queued and applied in
MainActivity.onStop, after the user leaves the screen: Pleos may remove the task of
a disabled launcher alias even with DONT_KILL_APP, so foreground switching is unsafe.

| Signals | State |
| --- | --- |
| No confirmed issues | Blue normal |
| Fuel only | Yellow hungry |
| Wash only | Brown dirty |
| Oil change only | Purple puzzled |
| Any two | Red angry |
| All three | Charcoal exhausted |

Artwork is packaged under `drawable-nodpi`: full-body images at 720px maximum edge,
launcher faces at 256px. Originals are retained separately. Static resources avoid
network loads, animation loops and extra dependencies. Only the visible image is decoded.

Six launcher aliases target an always-enabled MainActivity. Never disable the screen
activity itself to change its color: live data can otherwise close the running UI.
MainActivity preserves singleTop for compatibility with Pleos embedded app windows.
On API 33+ launcher changes are
batched atomically; older versions enable the next alias before disabling the old.
Updates run on an IO dispatcher, serialize across lifecycle requests and skip unchanged entries. The launcher may cache
the old image temporarily. No background monitoring has been added.

Regression verification on Pleos: all six component states expose exactly one launcher
entry; changing states and launching each twice keeps one app task. The normal entry
is LauncherNormal after the cycle. The dock must be visually checked after the cycle.
Historical duplicate dock records were removed by re-registering this app for user 10
with data retained (`pm uninstall -k --user 10`, then `install-existing --user 10`).
The saved profile/records file was backed up and byte-compared unchanged afterward.
Do not clear launcher data or wipe the AVD to repair old shortcuts.

Selected default source: `design/character-default-blue.png`.
The five state previews are now packaged as runtime assets, without adding unrequested
copies to the design folder. `tools/prepare-mascot.ps1` records source paths and sizing.
