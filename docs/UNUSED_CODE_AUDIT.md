# Unused-code audit — 1.8.0

Scope: `app/src/**` and `core/**` as of 2026-11-18 (`chore/batch-0-2-free-flavor`).

Method: every top-level `class` / `object` / `interface` / `fun` declaration in
`app/src/main` was matched against all identifiers in `app/src/main`,
`app/src/free`, `app/src/play`, `app/src/test`, `app/src/androidTest` and
`core/`. A file was deleted only when **all** of its declarations had zero
external references **and** it was not reachable through Hilt, Room, reflection,
XML or `AndroidManifest.xml`. XML layouts were cross-checked with
`R.layout.<name>` / `@layout/<name>`.

The audit is reproducible with:

```powershell
python tools/audit/dead_code_audit.py
```

## Deleted (33 files)

| Area | Files | Why |
|---|---|---|
| Legacy Views | `ui/beam/BeamDrawingView.kt`, `ui/column/ColumnDrawingView.kt`, `ui/slab/SlabDrawingView.kt`, `ui/stairs/StairDrawingView.kt`, `ui/watertank/TankDrawingView.kt`, `ui/custom/StaircaseView.kt`, `ui/custom/DesignReportView.kt` | Replaced by Compose screens; no Kotlin, XML or manifest reference. |
| Adapter | `adapter/DesignHistoryAdapter.kt`, `res/layout/item_design_history.xml` | Only user of RecyclerView. |
| Models | `model/DesignItem.kt`, `model/DetailDrawing.kt` | Used only by the deleted legacy Views. |
| Utilities | `utils/DrawingCaptureUtil.kt`, `utils/ValidationUtils.kt`, `utils/ResponsiveDesignManager.kt`, `utils/FoundationDesignComparator.kt`, `utils/telegram/TelegramConfig.kt` | Zero references. `TelegramConfig` held only placeholder credentials. |
| Validators/DI | `domain/validators/ColumnInputValidator.kt`, `di/CalculationModule.kt`, `di/DesignCodeModule.kt` | Replaced by `InputGuard`; Hilt modules had no injection sites (`Map<DesignCode, ColumnDesign>` is never injected). |
| Compose components | `components/beam/BeamCalculationStepsCard.kt`, `components/column/ColumnCalculationStepsCard.kt`, `components/ColumnSectionCanvas.kt`, `components/drawings/ConnectionDetailDrawing.kt`, `components/charts/MomentShearDiagramChart.kt`, `components/interactive/InteractiveBeamSection.kt`, `components/interactive/InteractiveTankSection.kt`, `components/editor/InteractiveFrameCanvas.kt` | Zero call sites. |
| ViewModels | `viewmodel/SoilBearingViewModel.kt`, `viewmodel/SoilSettlementViewModel.kt` | Their screens no longer exist. |
| Layouts | `res/layout/dialog_add_project.xml`, `item_home_card.xml`, `nav_header.xml`, `view_design_report.xml` | No `R.layout` / `@layout` reference. |

Dependencies removed with the code: Glide (+ KSP compiler), RecyclerView,
`navigation-fragment-ktx`, `navigation-ui-ktx`.
`viewBinding` and `dataBinding` were disabled because no XML binding class is used.

## Intentionally kept (looks unused but is not deleted)

| Item | Reason |
|---|---|
| `domain/calculations/sbc/SBCJoistSlab.kt` | Verified design engine and part of ECP/ACI/SBC parity; engines are assets, not UI dead weight. |
| `domain/modelcheck/ModelCheckEngine.kt` | Domain feature scaffold, no UI yet. |
| `utils/ContinuousBeamAnalysis.kt` | Calculation asset used by future continuous-beam work. |
| `ui/compose/screens/ExportScreen.kt`, `viewmodel/ExportViewModel.kt` | Complete export feature that is not yet wired into navigation; deleting it would remove working functionality. |
| `app/src/play/**` (`AdComponents`, `BillingManager`) | Dormant `play` flavor; required when that distribution is revived. |
| `domain/usecases/**` | Referenced by unit tests and part of the repository architecture. |

## Rule for future batches

Run the two scripts above before deleting anything. Delete only when the file is
unreachable **and** is not a calculation engine, database entity, DI binding,
manifest/XML target or test fixture.
