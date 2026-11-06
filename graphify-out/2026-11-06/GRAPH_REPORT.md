# Graph Report - PIT IN  (2026-11-06)

## Corpus Check
- 53 files · ~16,555 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 51 file(s) not represented in the graph (top: .xcconfig 8, (none) 7, .xml 7)
- WARNING: 51 of 150 files seen (34%) have no supported extractor and are missing from this graph entirely. It may look complete while representing only part of the codebase.

## Summary
- 523 nodes · 680 edges · 26 communities (18 shown, 8 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 17 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `9334e11c`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Windows window lifecycle
- Flutter entry and tests
- Apple platform integration
- Vehicle editing forms
- Personal setup flow
- Calendar and care forms
- App shell and history
- Linux desktop integration
- Care history storage
- Home vehicle dashboard
- Condition and profile
- Windows app startup
- Brand and model setup
- Flutter dependencies
- Shared UI components
- Windows plugin registration
- Web app manifest
- Flutter widget types
- Analyzer configuration
- Android app startup
- Vehicle model catalog

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
  lib/presentation/screens/pit_in_calendar_screen.dart → lib/presentation/data/care_history.dart

## Import Cycles
- None detected.

## Communities (26 total, 8 thin omitted)

### Community 0 - "Windows window lifecycle"
Cohesion: 0.06
Nodes (22): FlutterWindow, flutter_controller_, project_, EnableFullDpiSupportIfAvailable(), EnableNonClientDpiScaling, Point, x, y (+14 more)

### Community 1 - "Flutter entry and tests"
Cohesion: 0.07
Nodes (16): build, _CarPainter, paint, shouldRepaint, load, main, main, vehicle (+8 more)

### Community 2 - "Apple platform integration"
Cohesion: 0.06
Nodes (14): Cocoa, Flutter, FlutterMacOS, Foundation, AppDelegate, SceneDelegate, RunnerTests, RegisterGeneratedPlugins() (+6 more)

### Community 3 - "Vehicle editing forms"
Cohesion: 0.05
Nodes (29): _action, _badge, _blue, _bottomNav, build, _cardDecoration, color, detailValue (+21 more)

### Community 4 - "Personal setup flow"
Cohesion: 0.06
Nodes (25): _advanceCareIfReady, build, createState, _date, dispose, _electric, _form, _fuelUnknown (+17 more)

### Community 5 - "Calendar and care forms"
Cohesion: 0.08
Nodes (21): CareRecord, _all, build, color, createState, _date, _day, _detail (+13 more)

### Community 6 - "App shell and history"
Cohesion: 0.07
Nodes (23): build, createState, _edit, _fuelThreshold, _history, _historyError, initialModel, initialPersonal (+15 more)

### Community 7 - "Linux desktop integration"
Cohesion: 0.09
Nodes (14): fl_register_plugins(), main(), first_frame_cb(), my_application_activate(), my_application_class_init(), my_application_dispose(), my_application_init(), my_application_local_command_line() (+6 more)

### Community 8 - "Care history storage"
Cohesion: 0.08
Nodes (21): careDateLabel, careDay, CareHistory, date, detail, fromJson, isFutureCareDay, kind (+13 more)

### Community 9 - "Home vehicle dashboard"
Cohesion: 0.08
Nodes (24): _brand, build, _controller, createState, _dateRow, dispose, _form, _fuel (+16 more)

### Community 10 - "Condition and profile"
Cohesion: 0.07
Nodes (22): clean, dirty, fuel, hungry, pitInCondition, build, createState, _fuelAlert (+14 more)

### Community 11 - "Windows app startup"
Cohesion: 0.12
Nodes (4): wWinMain(), CreateAndAttachConsole(), GetCommandLineArguments(), Utf8FromUtf16()

### Community 12 - "Brand and model setup"
Cohesion: 0.08
Nodes (20): PitInShell, _PitInShellState, _blue, BrandSetupScreen, _BrandSetupScreenState, build, _choice, _choosingModel (+12 more)

### Community 13 - "Flutter dependencies"
Cohesion: 0.11
Nodes (17): dependencies, cupertino_icons, flutter, shared_preferences, description, dev_dependencies, flutter_lints, flutter_test (+9 more)

### Community 14 - "Shared UI components"
Cohesion: 0.07
Nodes (21): build, main, MyApp, PitInHomeScreen, _StatCard, _WeatherCard, PitInCarCharacter, build (+13 more)

### Community 16 - "Web app manifest"
Cohesion: 0.18
Nodes (10): background_color, description, display, icons, name, orientation, prefer_related_applications, short_name (+2 more)

### Community 17 - "Flutter widget types"
Cohesion: 0.67
Nodes (3): CareKind, CareKindLabel, Extension on CareKind

### Community 18 - "Analyzer configuration"
Cohesion: 0.33
Nodes (5): analyzer, exclude, include, linter, rules

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
- **Why does `VehicleStatus` connect `Vehicle editing forms` to `Home vehicle dashboard`, `Condition and profile`, `App shell and history`?**
  _High betweenness centrality (0.037) - this node is a cross-community bridge._
- **Should `Flutter entry and tests` be split into smaller, more focused modules?**
  _Cohesion score 0.06970128022759602 - nodes in this community are weakly interconnected._
- **Why does `FlutterWindow` connect `Windows window lifecycle` to `Windows app startup`, `Windows plugin registration`?**
  _High betweenness centrality (0.025) - this node is a cross-community bridge._
- **Should `Apple platform integration` be split into smaller, more focused modules?**
  _Cohesion score 0.06153846153846154 - nodes in this community are weakly interconnected._