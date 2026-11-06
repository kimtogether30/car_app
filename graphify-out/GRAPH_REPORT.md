# Graph Report - VEMORING  (2026-11-06)

## Corpus Check
- 53 files · ~16,560 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 51 file(s) not represented in the graph (top: .xcconfig 8, (none) 7, .xml 7)
- WARNING: 51 of 150 files seen (34%) have no supported extractor and are missing from this graph entirely. It may look complete while representing only part of the codebase.

## Summary
- 523 nodes · 680 edges · 27 communities (19 shown, 8 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 17 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `9334e11c`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Windows window lifecycle
- package:flutter/material.dart
- Apple platform integration
- vemoring_vehicle_form.dart
- Personal setup flow
- vemoring_calendar_screen.dart
- vemoring_shell.dart
- Linux desktop integration
- Care history storage
- vemoring_home_screen.dart
- vemoring_profile_screen.dart
- Windows app startup
- Brand and model setup
- Flutter dependencies
- vemoring_page.dart
- Windows plugin registration
- Web app manifest
- Flutter widget types
- Analyzer configuration
- MainActivity.kt
- Vehicle model catalog
- StatelessWidget

## God Nodes (most connected - your core abstractions)
1. `Win32Window` - 21 edges
2. `FlutterWindow` - 10 edges
3. `WindowClassRegistrar` - 9 edges
4. `_MyApplication` - 7 edges
5. `AppDelegate` - 5 edges
6. `VehicleStatus` - 5 edges
7. `wWinMain()` - 5 edges
8. `GetCommandLineArguments()` - 5 edges
9. `Point` - 5 edges
10. `Size` - 5 edges

## Surprising Connections (you probably didn't know these)
- `wWinMain()` --calls--> `CreateAndAttachConsole()`  [INFERRED]
  windows/runner/main.cpp → windows/runner/utils.cpp
- `my_application_activate()` --calls--> `fl_register_plugins()`  [INFERRED]
  linux/runner/my_application.cc → linux/flutter/generated_plugin_registrant.cc
- `main()` --calls--> `my_application_new()`  [INFERRED]
  linux/runner/main.cc → linux/runner/my_application.cc
- `wWinMain()` --calls--> `GetCommandLineArguments()`  [INFERRED]
  windows/runner/main.cpp → windows/runner/utils.cpp
- `Extension on CareKind` --extends--> `CareKind`  [EXTRACTED]
  lib/presentation/screens/vemoring_calendar_screen.dart → lib/presentation/data/care_history.dart

## Import Cycles
- None detected.

## Communities (27 total, 8 thin omitted)

### Community 0 - "Windows window lifecycle"
Cohesion: 0.06
Nodes (22): FlutterWindow, flutter_controller_, project_, EnableFullDpiSupportIfAvailable(), EnableNonClientDpiScaling, Point, x, y (+14 more)

### Community 1 - "package:flutter/material.dart"
Cohesion: 0.06
Nodes (19): build, main, MyApp, build, _CarPainter, paint, shouldRepaint, load (+11 more)

### Community 2 - "Apple platform integration"
Cohesion: 0.06
Nodes (14): Cocoa, Flutter, FlutterMacOS, Foundation, AppDelegate, SceneDelegate, RunnerTests, RegisterGeneratedPlugins() (+6 more)

### Community 3 - "vemoring_vehicle_form.dart"
Cohesion: 0.05
Nodes (38): CareRecordForm, _CareRecordFormState, VemoringCalendarScreen, _VemoringCalendarScreenState, VemoringShell, _VemoringShellState, _brand, build (+30 more)

### Community 4 - "Personal setup flow"
Cohesion: 0.06
Nodes (25): _advanceCareIfReady, build, createState, _date, dispose, _electric, _form, _fuelUnknown (+17 more)

### Community 5 - "vemoring_calendar_screen.dart"
Cohesion: 0.08
Nodes (21): CareRecord, _all, build, color, createState, _date, _day, _detail (+13 more)

### Community 6 - "vemoring_shell.dart"
Cohesion: 0.07
Nodes (23): build, createState, _edit, _fuelThreshold, _history, _historyError, initialModel, initialPersonal (+15 more)

### Community 7 - "Linux desktop integration"
Cohesion: 0.09
Nodes (14): fl_register_plugins(), main(), first_frame_cb(), my_application_activate(), my_application_class_init(), my_application_dispose(), my_application_init(), my_application_local_command_line() (+6 more)

### Community 8 - "Care history storage"
Cohesion: 0.08
Nodes (21): careDateLabel, careDay, CareHistory, date, detail, fromJson, isFutureCareDay, kind (+13 more)

### Community 9 - "vemoring_home_screen.dart"
Cohesion: 0.07
Nodes (21): _action, _badge, _blue, _bottomNav, build, _cardDecoration, color, detailValue (+13 more)

### Community 10 - "vemoring_profile_screen.dart"
Cohesion: 0.07
Nodes (22): clean, dirty, fuel, hungry, vemoringCondition, build, createState, _fuelAlert (+14 more)

### Community 11 - "Windows app startup"
Cohesion: 0.12
Nodes (4): wWinMain(), CreateAndAttachConsole(), GetCommandLineArguments(), Utf8FromUtf16()

### Community 12 - "Brand and model setup"
Cohesion: 0.10
Nodes (14): _blue, BrandSetupScreen, _BrandSetupScreenState, build, _choice, _choosingModel, _continue, createState (+6 more)

### Community 13 - "Flutter dependencies"
Cohesion: 0.11
Nodes (17): dependencies, cupertino_icons, flutter, shared_preferences, description, dev_dependencies, flutter_lints, flutter_test (+9 more)

### Community 14 - "vemoring_page.dart"
Cohesion: 0.13
Nodes (10): build, navigationBar, title, trailing, vemoringBlue, vemoringCard, vemoringInk, vemoringMint (+2 more)

### Community 16 - "Web app manifest"
Cohesion: 0.18
Nodes (10): background_color, description, display, icons, name, orientation, prefer_related_applications, short_name (+2 more)

### Community 17 - "Flutter widget types"
Cohesion: 0.67
Nodes (3): CareKind, CareKindLabel, Extension on CareKind

### Community 18 - "Analyzer configuration"
Cohesion: 0.33
Nodes (5): analyzer, exclude, include, linter, rules

### Community 26 - "StatelessWidget"
Cohesion: 0.22
Nodes (8): _StatCard, VemoringHomeScreen, _WeatherCard, VemoringCarCharacter, VemoringPage, VemoringPageHeader, VemoringSampleNotice, VemoringSheetHeader

## Knowledge Gaps
- **207 isolated node(s):** `main`, `build`, `label`, `personalKey`, `detail` (+202 more)
  These have ≤1 connection - possible missing edges. (Counts symbols only; 324 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **8 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Win32Window` connect `Windows window lifecycle` to `Windows app startup`?**
  _High betweenness centrality (0.079) - this node is a cross-community bridge._
- **What connects `main`, `build`, `label` to the rest of the system?**
  _207 weakly-connected nodes found - possible missing edges._
- **Should `Windows window lifecycle` be split into smaller, more focused modules?**
  _Cohesion score 0.06170598911070781 - nodes in this community are weakly interconnected._
- **Why does `VehicleStatus` connect `vemoring_vehicle_form.dart` to `vemoring_home_screen.dart`, `vemoring_profile_screen.dart`, `vemoring_shell.dart`?**
  _High betweenness centrality (0.037) - this node is a cross-community bridge._
- **Should `package:flutter/material.dart` be split into smaller, more focused modules?**
  _Cohesion score 0.05813953488372093 - nodes in this community are weakly interconnected._
- **Why does `FlutterWindow` connect `Windows window lifecycle` to `Windows app startup`, `Windows plugin registration`?**
  _High betweenness centrality (0.025) - this node is a cross-community bridge._
- **Should `Apple platform integration` be split into smaller, more focused modules?**
  _Cohesion score 0.06153846153846154 - nodes in this community are weakly interconnected._