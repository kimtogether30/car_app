# Character and launcher states

The in-app character, speech bubble and launcher face share `MascotMood`.
`VehicleViewModel` owns the single live vehicle connection. `careCondition` converts
real Available readings, saved maintenance records and confirmed maintenance settings
into `CareSignals`. Unknown, error, unsupported and Demo values never become real fuel
warnings. The optional `GarageApp(careSignals = ...)` override remains for previews.
Fuel and SOC use the user's alert threshold and powertrain. Cleanliness is explicitly
a record/confirmed-distance/optional recent air-quality estimate, never a dirt sensor.
Scheduled oil change requires a matching oil record, user-confirmed distance unit,
replacement mileage and maintenance interval. Oil level/pressure warnings now drive purple
as vehicle safety alerts, independently of scheduled replacement. Demo mode does not change the real launcher icon. Disconnecting
the foreground vehicle session does not reset the launcher's last known mood.
The character changes immediately. VehicleIconMonitor keeps a shared SDK connection
alive while another app is visible and evaluates live care signals there. MainActivity
visibility defers icon changes until the screen is no longer visible: Pleos may remove
the task of a disabled launcher alias even with DONT_KILL_APP.

| Signals | State |
| --- | --- |
| No confirmed issues | Blue normal |
| Fuel only | Yellow hungry |
| Wash only | Brown dirty |
| Vehicle warning only (excluding low fuel) | Purple puzzled |
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
the old image temporarily. A specialUse foreground service shows a quiet ongoing
monitoring notification and can be disabled in My Car settings. The service shares
one SDK session with the screen, uses existing SDK subscriptions and the existing
30-second public telltale check, and batches signal callbacks for 500ms. It adds no
wake lock, animation, high-frequency polling, location, or network request.
Boot completion requests restoration; incomplete setup or a disabled setting stops
the service. Force-stop, OS termination, vehicle sleep, or missing SDK permissions
can interrupt monitoring; no freshness guarantee is implied by a retained icon.
Unknown readings retain known warning signals within the active monitoring session.
No available energy reading means the last launcher icon is preserved.

Foreground specialUse works on Pleos 2.1.0/API34 in this test environment. This is
not proof of Pleos App Market approval for that service type; submission policy and
real-vehicle background SDK behavior still need confirmation.

BackgroundIconMonitorTest verifies all six states automatically while the actual
navigation app is visible, exactly one enabled launcher entry, no forced app opening,
and a fresh real SOC callback after leaving VEMORING. Debug-only explicit broadcasts
provide in-memory samples through the same service/condition/launcher pipeline.
Release has an immutable empty test source and no test receiver. Tests clear the
override and restore the live source without writing profiles or VHAL values.

IMPORTANT ACTUAL DISPLAY LIMITATION (verified again 2026-11-09, Pleos emulator 2.1.0):
The application drawer displays the new yellow fuel icon, but the bottom dock
retains the old blue icon even though LauncherFuel is the only enabled entry.
See build/diagnostics/icon-monitor-list.png and icon-monitor-fuel.png. Component
and background tests do NOT prove dock refresh. The driver's always-visible
status-icon requirement is therefore not yet satisfied on this launcher. No
documented public dock-refresh API was found. Do not clear system UI data,
restart vehicle UI, create duplicate dock entries, or open our Activity to force
refresh. Pleos support/launcher capability must resolve this part.
Actual favorite-slot pinning is now verified: Vehicle settings, Navigation, Phone,
VEMORING, App library. Phone was restored to the favorite area. Both yellow fuel
and red angry states update the drawer while the pinned entry stays blue. During
alias changes the old pinned entry also fails to open; after restoring live data
and LauncherNormal it opens normally again. See docs/PLEOS_GNB_ICON_REPORT.md.

## First-run GNB guidance

First-run setup now asks whether to show instructions for adding VEMORING to the
bottom bar. Accepting shows the guide only after profile saving succeeds; declining
continues without opening it. The guide can also be reopened from My Car settings.
The guide never claims a pin was created or verifies a pin from consent alone.

Official GNB documentation distinguishes editable favorite slots from two recent
apps. Drivers enter editing by long-pressing an app in the library and drag it to
an editable slot; the recent-app area is not editable:
https://document.pleos.ai/en/docs/connect/guide/docs-design/components/gnb

On the tested emulator, the default HOME is ai.umos.homescreen/.HomeActivity and
querying android.content.pm.action.CONFIRM_PIN_SHORTCUT for user 10 returns no
activities. Standard requestPinShortcut therefore does not provide a supported
automatic-GNB-pin path here. No private settings writes, accessibility automation,
hidden broadcasts, or launcher data resets are used. A consent question cannot
grant a missing launcher API or fix the retained bottom-bar image.

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

Purple now follows confirmed non-fuel vehicle telltales (oil level/pressure, brake fluid, washer fluid, smart key). Scheduled oil replacement remains separate maintenance information. The OIL mood/LauncherOil identifiers are retained for installed launcher and phone compatibility.
