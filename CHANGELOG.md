# Changelog

All notable changes to Civil Engineer Pro are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning: [Semantic Versioning](https://semver.org/).

## [1.8.0] - 2026-11-18

Release APK: `CivilEngineerPro-1.8.0.apk` (versionCode 8, minSdk 26, targetSdk 36,
`free` flavor — no ads, no billing, no advertising identifier).

### Added

- Optional, non-blocking donation prompt after the third launch. Both
  **Donate** and **Not now** are final: the prompt never reappears on its own.
- Permanent **Settings → Support the app** entry that opens GitHub Sponsors.
- Defensive handling for devices without a browser: the app reports the problem
  with a toast and keeps running.

### Changed

- Repository cleanup: 33 unreachable source/resource files deleted, 5 unused
  Gradle dependencies removed, `viewBinding`/`dataBinding` disabled.
- `versionCode` 7 → 8, `versionName` 1.7.0 → 1.8.0.
- Delivery: signed release APK instead of debug APK. See `docs/RELEASES.md`.

### Removed

- Legacy Android `View` design screens and their layouts
  (`BeamDrawingView`, `ColumnDrawingView`, `SlabDrawingView`,
  `StairDrawingView`, `TankDrawingView`, `StaircaseView`, `DesignReportView`).
- `DesignHistoryAdapter` (RecyclerView) and `item_design_history.xml`.
- Unused utilities: `DrawingCaptureUtil`, `ValidationUtils`,
  `ResponsiveDesignManager`, `FoundationDesignComparator`, `TelegramConfig`,
  `ColumnInputValidator`.
- Unused Compose components: beam/column calculation-steps cards,
  `ColumnSectionCanvas`, `ConnectionDetailDrawing`, `MomentShearDiagramChart`,
  `InteractiveBeamSection`, `InteractiveTankSection`, `InteractiveFrameCanvas`.
- Unused Hilt modules `CalculationModule` and `DesignCodeModule` (no injection sites).
- Unused ViewModels `SoilBearingViewModel` and `SoilSettlementViewModel`.
- Unused layouts `dialog_add_project`, `item_home_card`, `nav_header`,
  `view_design_report`.
- Dependencies: Glide (+ KSP compiler), RecyclerView,
  `navigation-fragment-ktx`, `navigation-ui-ktx`.

### Not changed

- **No calculation, formula, clause or result value was modified.** All design
  engines, tests and exported PDF/DXF content are byte-for-byte the same logic
  as 1.7.0. This release is packaging, cleanup and an optional donation prompt.

---

## [1.7.0] - 2026-10

Baseline public release: reinforced concrete, steel, seismic, frame analysis,
quantity surveying, drawings and PDF reports for ECP / ACI / SBC.
