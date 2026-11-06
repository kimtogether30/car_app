# Graph Report - car_app1  (2026-11-06)

## Corpus Check
- Corpus is ~16,532 words - fits in a single context window. You may not need a graph.

## Summary
- 523 nodes · 679 edges · 26 communities (18 shown, 8 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 17 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

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
  lib/presentation/screens/caron_calendar_screen.dart → lib/presentation/data/care_history.dart

## Import Cycles
- None detected.

## Communities (26 total, 8 thin omitted)

### Community 0 - "Windows window lifecycle"
Cohesion: 0.06
Nodes (22): FlutterWindow, flutter_controller_, project_, EnableFullDpiSupportIfAvailable(), EnableNonClientDpiScaling, Point, x, y (+14 more)

### Community 1 - "Flutter entry and tests"
Cohesion: 0.06
Nodes (19): build, main, MyApp, build, _CarPainter, paint, shouldRepaint, load (+11 more)

### Community 2 - "Apple platform integration"
Cohesion: 0.06
Nodes (14): Cocoa, Flutter, FlutterMacOS, Foundation, AppDelegate, SceneDelegate, RunnerTests, RegisterGeneratedPlugins() (+6 more)

### Community 3 - "Vehicle editing forms"
Cohesion: 0.06
Nodes (32): _brand, build, CaronTextEdit, _CaronTextEditState, CaronVehicleEdit, CaronVehicleForm, _CaronVehicleFormState, _controller (+24 more)

### Community 4 - "Personal setup flow"
Cohesion: 0.06
Nodes (25): _advanceCareIfReady, build, createState, _date, dispose, _electric, _form, _fuelUnknown (+17 more)

### Community 5 - "Calendar and care forms"
Cohesion: 0.08
Nodes (26): CaronShell, _CaronShellState, _all, build, CareRecordForm, _CareRecordFormState, CaronCalendarScreen, _CaronCalendarScreenState (+18 more)

### Community 6 - "App shell and history"
Cohesion: 0.07
Nodes (23): build, createState, _edit, _fuelThreshold, _history, _historyError, initialModel, initialPersonal (+15 more)

### Community 7 - "Linux desktop integration"
Cohesion: 0.09
Nodes (14): fl_register_plugins(), main(), first_frame_cb(), my_application_activate(), my_application_class_init(), my_application_dispose(), my_application_init(), my_application_local_command_line() (+6 more)

### Community 8 - "Care history storage"
Cohesion: 0.07
Nodes (25): careDateLabel, careDay, CareHistory, CareKind, CareKindLabel, CareRecord, date, detail (+17 more)

### Community 9 - "Home vehicle dashboard"
Cohesion: 0.07
Nodes (21): _action, _badge, _blue, _bottomNav, build, _cardDecoration, color, detailValue (+13 more)

### Community 10 - "Condition and profile"
Cohesion: 0.07
Nodes (22): caronCondition, clean, dirty, fuel, hungry, build, CaronProfileScreen, _CaronProfileScreenState (+14 more)

### Community 11 - "Windows app startup"
Cohesion: 0.12
Nodes (4): wWinMain(), CreateAndAttachConsole(), GetCommandLineArguments(), Utf8FromUtf16()

### Community 12 - "Brand and model setup"
Cohesion: 0.10
Nodes (14): _blue, BrandSetupScreen, _BrandSetupScreenState, build, _choice, _choosingModel, _continue, createState (+6 more)

### Community 13 - "Flutter dependencies"
Cohesion: 0.11
Nodes (17): dependencies, cupertino_icons, flutter, shared_preferences, description, dev_dependencies, flutter_lints, flutter_test (+9 more)

### Community 14 - "Shared UI components"
Cohesion: 0.13
Nodes (10): build, caronBlue, caronCard, caronInk, caronMint, caronNavy, caronOrange, navigationBar (+2 more)

### Community 16 - "Web app manifest"
Cohesion: 0.18
Nodes (10): background_color, description, display, icons, name, orientation, prefer_related_applications, short_name (+2 more)

### Community 17 - "Flutter widget types"
Cohesion: 0.22
Nodes (8): CaronHomeScreen, _StatCard, _WeatherCard, CaronCarCharacter, CaronPage, CaronPageHeader, CaronSampleNotice, CaronSheetHeader

### Community 18 - "Analyzer configuration"
Cohesion: 0.33
Nodes (5): analyzer, exclude, include, linter, rules

## Knowledge Gaps
- **207 isolated node(s):** `main`, `build`, `initialModel`, `initialPersonal`, `_tab` (+202 more)
  These have ≤1 connection - possible missing edges. (Counts symbols only; 324 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **8 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `VehicleStatus` connect `Vehicle editing forms` to `Home vehicle dashboard`, `Condition and profile`, `App shell and history`?**
  _High betweenness centrality (0.020) - this node is a cross-community bridge._
- **What connects `main`, `build`, `initialModel` to the rest of the system?**
  _207 weakly-connected nodes found - possible missing edges._
- **Should `Windows window lifecycle` be split into smaller, more focused modules?**
  _Cohesion score 0.06170598911070781 - nodes in this community are weakly interconnected._
- **Why does `Win32Window` connect `Windows window lifecycle` to `Windows app startup`?**
  _High betweenness centrality (0.010) - this node is a cross-community bridge._
- **Should `Flutter entry and tests` be split into smaller, more focused modules?**
  _Cohesion score 0.05813953488372093 - nodes in this community are weakly interconnected._
- **Why does `FlutterWindow` connect `Windows window lifecycle` to `Windows app startup`, `Windows plugin registration`?**
  _High betweenness centrality (0.006) - this node is a cross-community bridge._
- **Should `Apple platform integration` be split into smaller, more focused modules?**
  _Cohesion score 0.06153846153846154 - nodes in this community are weakly interconnected._
## Scope and limitations

Code-only local AST extraction; no external AI API was called. Documentation and 35 images were excluded. The scan found 51 unsupported files. linux/runner/my_application.h had a parser error; eight code files yielded no symbols. Diagnostics found 26 dangling-endpoint edges and 9 same-endpoint edge collapses in the undirected representation. This graph is a navigation aid, not a complete or verified program model.

Extraction model tokens: 0 input / 0 output. Host assistant orchestration tokens are not measured here.

`graphify-out/` is generated in the Git working tree and is not automatically ignored.
