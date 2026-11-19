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
- Drawing package layer (`domain/drawing`): shared `DrawingScene` model,
  label placement, sheet composition, and Compose/PDF/DXF renderers, plus
  beam, column and slab package builders and a `BbsEngine`. Covered by 24
  unit tests. This layer is internal for now — no screen uses it yet, so the
  visible UI is unchanged.

### Changed

- Repository cleanup: 33 unreachable source/resource files deleted, 5 unused
  Gradle dependencies removed, `viewBinding`/`dataBinding` disabled.
- `versionCode` 7 → 8, `versionName` 1.7.0 → 1.8.0.
- CI: fixed an invalid `upload-artifact` path block that made every
  `android.yml` run fail at startup with no jobs. CI now runs guards, lint,
  unit tests and both debug builds; tag pushes no longer build releases.
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

- **No design-engine formula, clause or safety factor was modified.** All
  concrete, steel, seismic and frame-analysis engines, their tests and the
  exported PDF content use the same logic as 1.7.0. This release is packaging,
  cleanup and an optional donation prompt.
- One output detail did change: beam bar-bending schedules now derive the bar
  run from the beam's own `span` when it is set, instead of always falling back
  to the storey height. Previously the storey height was used even when the
  span was known, which could overstate main-bar lengths.

---

## [1.7.0] - 2026-10

Baseline public release: reinforced concrete, steel, seismic, frame analysis,
quantity surveying, drawings and PDF reports for ECP / ACI / SBC.
