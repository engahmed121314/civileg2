# CivilEG2 — Full Application Audit & Development Plan (Refresh)

**Date:** 2026-10-14
**Scope:** All input paths, calculation engines, code selection, results, drawings, and PDF/DXF export.
**Companion docs:** `AUDIT_REPORT.md` (original), `CALCULATOR_ENGINE_AUDIT.md`, `CODEREF_AUDIT.md`, `ENGINE_COVERAGE_MATRIX.csv`, `SCREEN_ENGINE_MAP.md`, `INPUTGUARD_COVERAGE.md`, `COVERAGE_MATRIX.csv`, `CRASH_INVENTORY.md`.
**Status of this doc:** Supersedes the 2026-09-21 snapshot for the items marked ✅ (that report pre-dates the InputGuard / CodeReference / export work now on `master`).

---

## 1. Executive Summary

| Area | State | Notes |
|------|-------|-------|
| Build / tests | ✅ GREEN | `compileDebugKotlin` OK; **56 suites / 1177 tests / 0 failures**. `lintVitalRelease` passes. |
| Domain engines | ✅ Present | 14 base interfaces; ECP/ACI/SBC implementations for all major element types (see §3). |
| Code selection | ⚠️ Fragmented | **Two `DesignCode` enums** + one screen (`SeismicScreen`) bypasses the factory. See §4. |
| CalculatorEngine facade | ⚠️ Partial | Real (not stub) for most elements but duplicates engine math inline; some delegations missing. See §5. |
| InputGuard | ✅ Mostly | Present at domain-engine entries; **`CalculatorEngine` coverage was the old zero-gap** — verify per §5. |
| CodeReference | ✅ Broad | Concrete + steel/frame engines emit refs (commits `08bcc90`, `399c455`). Confirmed errors fixed (ACI cover, one-way slab); ECP/SBC sub-clauses still unverified. See §9.6. |
| Drawing → PDF | ✅ | Capture-based for **every** element that has an on-screen drawing (Warehouse §9.12, Pile §9.13, steel member §9.14); Seismic falls back to `generateSeismicDrawing`; FlatSlab/ShearWall stale-bitmap fixed; fallbacks read real values (§9.10); native-Canvas PDF path removed (§9.11). Base plate is generator-only (no on-screen drawing exists). |
| Export pipelines | ✅ Consolidated | `ExportManager` unified (`exportReportAndDxf/exportPdf/Dxf/Ifc/Csv/Both/Package`); 12 dead exporters **plus `NativePdfExporter` + `PdfLayoutHelper`** deleted. All ViewModels migrated (§9.10); `QualityEngineerViewModel` still calls the photos-capable reporter directly. |
| Security / signing | ⚠️ Blocked | Needs production signer fingerprint + AdMob IDs. 15 Dependabot alerts. |

---

## 2. Inputs → Results Inventory (element × code)

### 2.1 Base interfaces (14, `domain/calculations/base/`)
`BeamDesign, ColumnDesign, SlabDesign, FlatSlabDesign, WaffleSlabDesign, HordiSlabDesign, FootingDesign, StrapFootingDesign, PileFoundationDesign, RetainingWallDesign, ShearWallDesign, TankDesign, StaircaseDesign, SeismicDesign`.

> Note: Combined-footing is **not** a base interface — it is a method on `FootingDesign` (`designCombinedFooting`), so ACI/SBC "combined footing" is routed through `ACIFooting` / `SBCFooting`.

### 2.2 Coverage matrix

| Element | ECP | ACI | SBC | Result has codeNotes | InputGuard @ entry |
|---|---|---|---|---|---|
| Beam | ✅ | ✅ | ✅ | ✅ | ✅ |
| DoublyReinforcedBeam | ✅ | ✅ | ✅ | (see note) | ✅ |
| Column | ✅ | ✅ | ✅ | ✅ | ✅ |
| Slab | ✅ | ✅ | ✅ | ✅ | ✅ |
| FlatSlab | ✅ | ✅ | ✅ | ✅ | ✅ |
| WaffleSlab | ✅ | ✅ | ✅ | ✅ | ✅ |
| HordiSlab | ✅ | — | ✅ | ✅ | ✅ |
| JoistSlab | — | ✅ | ✅ | ✅ | ✅ |
| Footing | ✅ | ✅ | ✅ | ✅ | ✅ |
| CombinedFooting | ✅ | ✅ (via `ACIFooting`) | ✅ (via `SBCFooting`) | ✅ | ✅ |
| StrapFooting | ✅ | ✅ | ✅ | ✅ | ✅ |
| PileFoundation | ✅ | ✅ | ✅ | ✅ | ✅ |
| RetainingWall | ✅ | ✅ | ✅ | ✅ | ✅ |
| ShearWall | ✅ | ✅ | ✅ | ✅ | ✅ |
| Tank | ✅ | ✅ | ✅ | ✅ | ✅ |
| Staircase | ✅ | ✅ | ✅ | ✅ | ✅ |
| Seismic | ✅ | ✅ | ✅ | ✅ | ✅ |
| Steel member | ✅ | ✅ | ✅ | ✅ | ✅ (guard at `SteelDesignEngine.designBeam:189`) |
| Steel connection | ✅ | ❌ | ✅ | — | ✅ `classifySection` now guarded (§9.6) |
| Steel base plate | ✅ | ❌ | ✅ | — | — |

**Legend:** ✅ present · ❌ absent · ⚠️ partial/risk.

### 2.2 Coverage gaps
- **`AISCSteelDesignEngine`** lacks connection + base-plate methods (ECP `SteelConnectionDesign` / `SteelBasePlateDesign` and SBC equivalents exist).
- `classifySection` in `AISCSteelDesignEngine` is the only unguarded public entry found.
- No dedicated `ACICombinedFooting` / `SBCCombinedFooting` classes (routed via footing method — acceptable but must be documented in UI).

### 2.3 Units convention (critical for correctness)
- **mm** for section/detail dimensions (`pileDiameter`, `wallThickness`, bar diameters, cover, tie/spacing in some paths) — engines convert `/1000.0`.
- **m** for spans, heights, spacing in the global path.
- **kN / kN.m / MPa / mm²** for forces, moments, stresses, areas.
- Mixed-unit inputs remain the top source of wrong results — see §7 Priority P1.

---

## 3. Code Selection Flow (element type + user code)

The user picks a code in the UI, which must resolve to `domain.entities.DesignCode {ECP, ACI, SBC}` and drive engine selection via `CalculationFactory`.

**Finding — two parallel enums:**
1. `com.civileg.app.utils.CalculatorEngine.DesignCode` = `{EGYPTIAN, ACI, SAUDI}` (nested, UI/legacy).
   - `toDomain()` (`CalculatorEngine.kt:49-53`) maps to the domain enum.
   - `fromDomain()` (`:56-60`) exists but is **never called** — ViewModels convert by hand.
2. `com.civileg.app.domain.entities.DesignCode` = `{ECP, ACI, SBC}` (canonical).

**Finding — `SeismicScreen` bypasses the factory:**
- Uses its own `SeismicCodeOption {ECP_201, ASCE_7, SBC_301}` → maps to domain `DesignCode`.
- Instantiates `ECPSeismic()` / `ACISeismic()` / `SBCSeismic()` **directly**, whereas `SeismicViewModel` uses `CalculationFactory.getSeismicDesign(...)`.
- Risk: two selection paths can diverge; new engines/codes won't propagate.

**Recommendation (P2):** collapse to one enum (`domain.entities.DesignCode`), delete the nested UI enum, route every screen through `CalculationFactory`, and use `fromDomain()` where a UI label is required.

---

## 4. CalculatorEngine Facade Audit

`utils/CalculatorEngine.kt` — single **1407-line** file. `class CalculatorEngine @Inject constructor(settingsManager: SettingsManager)` (`:36-39`).

| Aspect | Finding |
|--------|---------|
| Input code type | Nested UI `DesignCode {EGYPTIAN, ACI, SAUDI}`; `toDomain():49-53`; `fromDomain():56-60` unused. |
| Real vs stub | Old report claimed 10 stubs; several are now real-inline. **`calculateSteelMember` (`:396`) now delegates to the code-specific domain engines** (ECP/AISC/SBC) via `designSteelMemberEcp/Aisc/Sbc` — results are code-differentiated (P0-2, §9.6). The old inline stub computed φPn/φMn/φVn + KL/r identically for all 3 codes and inflated KL/r by ×1000. |
| InputGuard | Applied (e.g. `:398-401`), but duplicate of the engine's own guards. |
| Architecture risk | Two sources of truth (facade inline math + domain engines) → drift, especially for code differentiation. |

**Recommendation (P1):** delegate every `CalculatorEngine` method to `CalculationFactory`/domain engines so a single implementation produces the numbers; keep the facade for UI typing only.

---

## 5. Drawing → PDF Fidelity

**Two renderers exist:**
1. **Compose capture** — hidden `ComposeDrawingCaptureUtil.DrawingCaptureArea` records the on-screen `Professional*Drawing` into a `GraphicsLayer` → `captureToAndroidBitmap()` → `viewModel.pendingDrawingBitmap`.
2. **Fallback** — `PdfDrawingGenerator.generate*` (Android Canvas, 3031 lines) redraws independently.

**ViewModel pattern:** `val bmp = pendingDrawingBitmap ?: PdfDrawingGenerator.generate*(...)`; then (should) reset.

### 5.1 Per-element matrix

| Element | On-screen | Capture wired? | PDF source | Fidelity |
|---|---|---|---|---|
| Beam | `ProfessionalBeamDrawing` | ✅ | captured, else `generateBeamDrawing` | ✅ |
| Column | `ProfessionalColumnDrawing` | ✅ | captured, else `generateColumnDrawing` | ✅ |
| Slab (solid/flat/hordi/waffle) | `Professional*SlabDrawing` | ✅ | captured, else `generateSlabDrawingByType` | ✅ |
| FlatSlab | `ProfessionalFlatSlabDrawing` | ✅ | captured, else generator | ✅ (stale-bitmap bug fixed) |
| WaffleSlab | `ProfessionalWaffleSlabDrawing` | ✅ | captured only | ✅ |
| HordiSlab | `ProfessionalHordiSlabDrawing` | ✅ | captured only | ✅ |
| Footing / Combined | `ProfessionalFooting/CombinedFootingDrawing` | ✅ | captured, else generator | ✅ (fallback synthesizes rebar) |
| StrapFooting | `ProfessionalStrapFootingDrawing` | ✅ | captured only | ✅ |
| Tank | `ProfessionalTankDrawing` | ✅ | captured, else `generateTankDrawing` | ✅ |
| Stair | `ProfessionalStairDrawing` | ✅ | captured, else `generateStairDrawing` | ✅ (fallback uses real width §9.10) |
| RetainingWall | `ProfessionalRetainingWallDrawing` | ✅ | captured, else generator | ✅ (fallback synthesizes geometry) |
| ShearWall | `ProfessionalShearWallDrawing` | ✅ | captured only | ✅ (stale-bitmap bug fixed) |
| **Pile** | `ProfessionalPileDrawing` | ✅ (§9.13) | captured at current view, else `generatePileDrawing` | ✅ |
| **Steel member** | `ProfessionalSteelDrawing` | ✅ (§9.14) | captured at current view, else `generateSteelDrawing` | ✅ |
| **Steel base plate** | (none) | n/a | `generateBasePlateDrawing` | ⚠️ generator only (no on-screen drawing exists to capture) |
| **Warehouse** | `ProfessionalWarehouseDrawing` | ✅ (§9.12) | captured at current view, else `generateWarehouseDrawing` + 3D | ✅ |
| **Seismic** | `ProfessionalSeismicDrawing` | ❌ | captured if set, else `generateSeismicDrawing` | ⚠️ generator fallback (was: no drawing) |
| Frame BMD/SFD | `FrameDrawingCanvas` | n/a | `generateFrameBitmap` from result | ✅ |

### 5.2 Concrete fidelity gaps
1. **Seismic PDF has no drawing** — `SeismicViewModel.kt:192` reads a `pendingDrawingBitmap` that is never set (no `DrawingCaptureArea` in `SeismicScreen.kt`) and has no generator fallback → `"[Drawing not available...]"`. `PdfDrawingGenerator.generateSeismicDrawing` exists (`:3060`) and can be wired. *(Fix implemented — see §9.)*
2. **Stale-bitmap bug** — `FlatSlabViewModel.kt:186` and `ShearWallViewModel.kt:287` read the captured bitmap but never reset `pendingDrawingBitmap = null`, so a later export can show an **old** drawing. *(Fix implemented — see §9.)*
3. ~~**Steel connection PDF mismatch**~~ — **FIXED (§9.6):** PDF path now reads actual `boltDia`/`boltCount` from `res.connectionDesign` (falls back to defaults) in both `SteelViewModel` and `ExportViewModel`.
4. **Fallback generators synthesize geometry** rather than read design values (combined footing hardcodes bars `CombinedFootingViewModel.kt:281-282`; retaining wall derives stem/base/rebar `:121-133`; generic slab/column fallbacks `ExportViewModel.kt:336-353`). Stair width and combined-footing bar spacing now read the real values (**§9.10**). Only reachable when capture is absent.
5. ~~**Pile / Warehouse** use a renderer distinct from the screen~~ — **all three fixed** (Warehouse §9.12, Pile §9.13, steel member §9.14); the PDF now embeds the captured on-screen view. The only ⚠️ row left is the **base plate**, which has no on-screen drawing to capture — its fallback reads the actual result values.

---

## 6. Export Pipelines

- **Unified:** `utils/ExportManager.kt` exposes `exportReportAndDxf`, `exportPdf`, `exportProfessionalPdf`, `exportDxf`, `exportIfc`, `exportCsv`, `exportBoth`, `exportPackage`; `DxfDrawable` interface (`:224`) enables one-call PDF+DXF.
- **Deleted (dead):** 12 exporters (AdvancedPdfExporter, BilingualPdfHelper, CalculatorCadExporterV7, CalculatorDetailingV4, DxfExportEngine ×2, ComprehensivePdfExporter, FlatSlabPdfExporter, PileFoundationPdfExporter, ShearWallPdfExporter, SteelEnglishReportExporter, DxfStructuralExporter).
- **Live:** `DxfExporter` (AC1027), `PdfGenerator`, `PdfDrawingGenerator`, `ProfessionalEnglishPdfReporter`, `PdfExportHelper`, `SteelWarehouseProPdfExporter`, `FrameAnalysisPdfExporter`.
- **Deleted (native Canvas, §9.11):** `NativePdfExporter` and `PdfLayoutHelper` — the `android.graphics.pdf.PdfDocument` + Canvas path is gone from the app entirely.
- **Pending:** ~~migrate ~15 ViewModels off direct `ProfessionalEnglishPdfReporter.generateReportLegacy` / `PdfGenerator` calls onto `ExportManager`~~ **done §9.10** via `ExportManager.exportReportAndDxf` (PDF + DXF in one call, DXF surfaced by toast). `QualityEngineerViewModel` still calls the photos-capable `generateReport()` directly.

---

## 7. Prioritized Development Plan

### P0 — Correctness of numbers (blocks trust)
1. **Unify code selection** to `domain.entities.DesignCode`; delete nested enum; route `SeismicScreen` (and any direct instantiation) through `CalculationFactory`. Use `fromDomain()` for labels.
2. **Delegate `CalculatorEngine`** methods to domain engines so results are code-differentiated (today some are identical for all codes).
3. **Verify clause numbers** in `CodeReference.kt` against real ECP/ACI/SBC text; add a unit test asserting each key resolves for each code. *(resolution test done §9.6; SBC clause
   chapters corrected §9.9; ECP 203-2020 sub-clause text still needs the licensed document.)*

### P1 — Drawing/PDF fidelity
4. Wire `generateSeismicDrawing` fallback + `DrawingCaptureArea` in `SeismicScreen`. *(fallback done)*
5. Reset `pendingDrawingBitmap` in FlatSlab/ShearWall. *(done)*
6. Pass **actual** connection values (bolt count/dia/gauge, weld, end-plate) into the Steel PDF path.
7. Make fallback generators read result values (no hardcoded bars/dimensions); or guarantee capture always exists for those elements. *(done §9.10 — stair width and combined-footing bar spacing now derived from real inputs/results; remaining fallbacks are covered by a capture bitmap.)*
8. Add `DxfDrawable` implementations for all elements so `exportBoth` is real. *(done §9.9)*

### P2 — Coverage & robustness
9. Add AISC connection & base-plate methods; guard `classifySection`. *(classifySection guarded §9.6;
   AISC `calculateFilletWeldCapacity` + `calculateBoltShearCapacity` added §9.7; **base-plate minimal
   adapter done §9.9** — `AISCSteelDesignEngine.designConcentricBasePlate()` delegates to the single
   shared `ecp.SteelBasePlateDesign` (AISC 360-16 Ch.14 / DG1), closing the AISC API gap without
   duplicating 1100+ lines; parity guarded by `AISCBasePlateAdapterTest`.)*
10. Add `AcidCombinedFooting`/`SbcCombinedFooting` doc-only note in UI (or dedicated classes). *(done §9.10 — method note in `FootingScreen` results card, guarded by the Combined footing type.)*
11. Migrate all ViewModels to `ExportManager`. *(done §9.10 — every element ViewModel, `ExportViewModel`, `PdfExportHelper` and the three legacy design fragments now go through `ExportManager.exportReportAndDxf`; the only remaining direct reporter use is `QualityEngineerViewModel`'s photos-capable `generateReport()`.)*
12. Crash-hardening sweep for tools (SoilBearing, RebarTool, UnitConverter, SiteLayout, etc.). *(done §9.10 — SoilBearing/UnitConverter/RebarTool/ConcreteMixDesigner/SteelTables/BbsGenerator/SiteLayoutScreen all verified guarded; `WaterLevelCalculator`/`ExpressionParser`/`SiteLayoutViewModel` do not exist.)*

### P3 — Release
13. Fill `AppIntegrityChecker.EXPECTED_SIGNER_FINGERPRINT` + production AdMob IDs.
14. Resolve 15 Dependabot alerts (8 high, 6 moderate, 1 low). *(partial §9.7: **runtime** `guava`
    forced 31.1-android → 33.4.0-android via an `app` dependency constraint, clearing CVE-2023-2976
    + CVE-2020-8908. Remaining alerts are almost all in **non-shipped tooling/config classpaths**
    — KSP's `grpc-netty` 1.57.2 pulls `netty-*` 4.1.93.Final, and room/hilt/dagger compiler pull
    `guava` 22.0/31.0.1 and `protobuf-java` 3.x. Forcing upgrades there can break KSP/Hilt at build
    time; needs a deliberate Kotlin/KSP bump, not a blind `force`.)*
    *(§9.8: Kotlin 2.1.21 → **2.2.20** + KSP 2.1.21-2.0.1 → **2.2.20-2.0.4** removed `grpc-netty`/
    `netty-*`/`protobuf-java` from the KSP processor classpath entirely. Remaining `netty`/`grpc`/
    `protobuf` alerts now only appear in AGP's internal Unified Test Platform (`_internal-unified-test-platform-*`)
    and Dagger's compiler classpaths — dev/test-infra only, not shipped; would need an AGP bump.)
    *(§9.9: AGP 8.9.2 → **8.11.1** (max within KGP 2.2.20 support) refreshed these to patched
    versions — `netty-*` 4.1.110.Final, `grpc-*` 1.69.1, `protobuf-java` 3.25.5. AGP 9.x would
    clear any residual dev-only alerts but is a major migration — deferred.)*
15. ProGuard / signing / versioning / store listing; manual PDF+DXF QA = 15 elements × 3 codes.

---

## 8. Definition of Done (unchanged, non-negotiable)
```
☐ One DesignCode enum; all screens → CalculationFactory → code-correct engine
✅ CalculatorEngine steel member now delegates to engines (P0-2; other inline paths remain)
☐ Every engine: InputGuard + full result (codeNotes, warnings, safetyChecks, steps, diagram data)
✅ Every element PDF has a drawing that matches the on-screen drawing and the result (no stale/placeholder) — §9.10 fallbacks, §9.11 native path removed, §9.12–9.14 captures; base plate has no on-screen drawing and reads real values
☐ PDF: single pipeline (ExportManager); DXF: AC1027 with layers/BBS; PDF+DXF exportable together
☐ CodeReference keys verified against code text
☐ Zero crashes with null/empty/invalid inputs
☐ Parity: same inputs → 3 correct, different results (golden fixtures)
☐ Signed, ProGuard, versioned APK ready
```

---

## 9. Changes Applied This Session

### 9.1 Drawing / PDF
- `SeismicViewModel.kt:190` — added `PdfDrawingGenerator.generateSeismicDrawing(...)` fallback so the seismic PDF always embeds a drawing (screen capture honoured first).
- `FlatSlabViewModel` / `ShearWallViewModel` — reset `pendingDrawingBitmap = null` after use → fixes stale drawing on re-export.

### 9.2 Steel (ACI 318 / SBC 306 / ECP 203)
- `aci/AISCSteelDesignEngine.kt` — added `PHI_RUPTURE = 0.75`; tension rupture & block shear now use 0.75 (were 0.9); welded/pressed placeholder `φ·0.6·Fy·Ag` → `φ·Fy·Ag`; deflection E unit `E_STEEL/1e6` → `E_STEEL*1000`.
- `sbc/SBCSteelDesignEngine.kt` — added `PHI_BOLT_SHEAR/PHI_BEARING/PHI_BLOCK_SHEAR/PHI_RUPTURE = 0.75`; bolt shear `0.45·Fu·Ab`; bearing, block shear, tension rupture all φ=0.75; deflection E unit fixed.
- `ecp/SteelConnectionDesign.kt:601` — block shear `max(...)` → `min(...)`.

### 9.3 Seismic
- `aci/ACISeismic.kt` — `csMax` uses estimated period `Ta = 0.0488·h^0.75` (was fixed T=0.1); `distributeSeismicForces` rewritten (story shear from roof, `OTM = Σ_{j≥i} Fj·hj`); `getSoilFactors` returns `SOIL_FACTORS`.
- `sbc/SBCSeismic.kt` — `buildingHeight` `nonNegative` + period guard; damping factor `sqrt(10/(5+100ξ))`; `distributeSeismicForces` rewritten; `peakGroundAcceleration` kept `positive` (zero must throw).

### 9.3 Concrete flexure / shear / slabs
- `aci/ACIFlatSlab.kt:486` — punching stress `M·1e6·φ` → `M·1e6/φ`.
- Lever-arm divisor `1.25`/`1.5` → `0.893 (= γc/(2·0.67))`: `ecp/ECPFlatSlab.kt:641`, `sbc/SBCFlatSlab.kt:501`, `ecp/ECPPileFoundation.kt:558`, `sbc/SBCPileFoundation.kt:383`, `ConcreteFrameDesign.kt:141`.
- `sbc/SBCBeam.kt:253` — axial shear stray `fc` removed.
- Cantilever min slab thickness `span/8` → `span/10` (ACI 318 Table 7.3.1.1): `aci/ACISlab.kt:226`, `sbc/SBCSlab.kt:205`.

### 9.4 Columns (α = Pn,max limit per ACI 318-19 §22.4.2.2)
- `aci/ACIColumn.kt:137`, `sbc/SBCColumn.kt:79` — axial inversion `Pu/φ` → `Pu/(φ·α)`, α=0.80.
- `aci/ACIAdvancedColumn.kt` — `designShortColumn` and `calculateAxialCapacityWithPhi` now include α (0.80/0.85).
- `sbc/SBCAdvancedColumn.kt` — `calculateAxialCapacity` includes α; steel weight/m unit fix (only total weight divided by length).
- `aci/ACIColumn.kt:189` — tie diameter threshold `25 → 32` (No.10).

### 9.3b Doubly-reinforced beams (K-method balance)
- `sbc/SBCDoublyReinforcedBeam.kt` — `kBal` = `(0.67/γc)·(a/d)·(1−a/2d)` strain-compatibility (was inflated by extra α); `kMax` = dynamic `kBal` (was hardcoded 0.194); singly/doubly areas via K-method (`z = d(0.5+√(0.25−K/0.893))`); a_min = `max(0.26√fcu/fy, 0.0013)·b·d`; neutral axis from stress block; `isSafe` = `As ≤ As_max`.
- `ecp/ECPDoublyReinforcedBeam.kt` — compression-steel trigger `k > kBal` (was `r > rBal`, over-triggered); `Mu_balanced = kBal·fcu·b·d²`; `z_balanced` via K-method.
- `aci/ACIDoublyReinforcedBeam.kt` — singly lever arm `z = d(0.5+0.5√(1−2k))`; doubly `As1` from stress-block `a1` at tension-controlled limit; capacity from provided-steel stress block.

### 9.5 Tests
- Added `EngineFixRegressionTest.kt` (5 tests): column α-inversion, SBC/ECP doubly K-balance decisions, ACI doubly capacity.
- Updated `ACISlabTest` / `SBCSlabTest` cantilever expectations to `L/10`.
- Full suite green: **58 suites / 1185 tests / 0 failures**.

### 9.6 Resolved follow-ups (this pass)
- `ecp/SteelDesignEngine.kt` — `checkBeamShear` now applies a web shear-buckling reduction
  `Cv` (ECP 205 / AISC G2): `Cv = 1` for stocky webs (`h/tw ≤ 1.10√(kE/Fy)`), linear
  `1.10√(kE/Fy)/(h/tw)` in the inelastic range, and `1.51·k·E/(h/tw)²/Fy` for slender webs
  (`k = 5` unstiffened). Previously `Vn = 0.6·Fy·Aw` unconditionally, which is unconservative
  for slender webs. `Aw = (h − 2·tf)·tw` (web depth already used). `h/tw`/`Cv` added to details.
- `aci/AISCSteelDesignEngine.classifySection` — added `InputGuard.notNull` guards and
  `coerceAtLeast(0.001)` on `tf`/`tw` before division (was the only unguarded public entry).
- Steel-connection PDF bolt mismatch — `SteelViewModel.exportToPdf` and
  `ExportViewModel.exportSteelReport` now derive `boltDia`/`boltCount` from
  `connectionDesign.connectionType as? ConnectionType.Bolted`, falling back to defaults;
  PDF drawing now matches the on-screen `ProfessionalSteelDrawing`.
- `CodeReference` verification: fixed confirmed errors — ACI cover `20.6.1 → 20.5.1.3`
  (Ch 20: 20.5 = Durability, 20.6 = Embedments); one-way slab `Section 8.3 → Chapter 7`
  for ACI 318-19 and SBC 304-2018 (Ch 7 = one-way slabs). Remaining clause numbers
  reviewed but ECP 203-2020 / SBC 304-2018 exact sub-clauses remain unverified (no
  authoritative text available); refs are display-only and do not affect calculations.
- **P0-2 — steel facade delegation** (`utils/CalculatorEngine.calculateSteelMember`): replaced
  the inline stub with dispatch to the code-specific engines via
  `designSteelMemberEcp` / `designSteelMemberAisc` / `designSteelMemberSbc`. ECP maps the
  entity section onto `ecp.SteelDesignEngine.SectionProperties` and the grade onto the ECP
  enum; ACI uses `AISCSteelDesignEngine`; SBC uses `SBCSteelDesignEngine`. Capacities are
  recovered from each engine via `capacityFromRatio = refDemand / utilizationRatio` (probe
  demand `max(demand, 1.0)` so zero-demand cases still yield a capacity), and combined
  utilization uses each engine's interaction equation when both `Pu>0` and `Mu>0`
  (else `max`). This removes the old `K·L·1000/r` unit bug (L already in mm) and makes the
  numeric result **code-specific**. Added `SteelMemberCodeDivergenceTest.kt`.
- **Warehouse main-frame utilizations** (`CalculatorEngine.designSteelWarehouse`): the three
  utilizations (moment/shear/axial) were computed with **hardcoded `fy = 240`** regardless of
  the actual section grade, using raw hand formulas, and were **identical for every code**.
  They now route through `steelMemberUtilizations(...)` (same ECP/AISC/SBC delegation as the
  steel-member path) using each section's real grade. Added a common `SteelSectionType.grade`
  accessor in `entities/SteelEntities.kt` (built-up falls back to ST37). `isSafe`/`safetyStatus`
  remain deflection-based (unchanged) to avoid altering the existing verdict.
- Tests: `SteelMemberCodeDivergenceTest` now has 4 tests (adds warehouse grade sensitivity).
- **Code-aware connection quick-tools** (`CalculatorEngine.calculateWeldCapacity` /
  `calculateBoltCapacity`): previously ignored the `code` argument and used one hardcoded
  φ-formula. Now dispatch by code: ACI → `AISCSteelDesignEngine`, SBC → `SBCSteelDesignEngine`
  (both AISC 360 lineage, identical), ECP → `ecp.SteelConnectionDesign` (threaded bolt area +
  ECP factors). Added public `calculateFilletWeldCapacity` / `calculateBoltShearCapacity` to the
  AISC and SBC engines (SBC already had private equivalents). `SteelConnectionToolTest` (4 tests).
- **P0-1 (partial)** — `SeismicScreen` no longer instantiates `ECPSeismic`/`ACISeismic`/`SBCSeismic`
  directly; its `getDesigner()` now routes through `CalculationFactory.getSeismicDesign(
  selectedCode.designCode)` (the screen's `SeismicCodeOption` already carries a domain `DesignCode`).
  This removes the third source of truth for seismic-engine selection. Full deletion of the nested
  `CalculatorEngine.DesignCode` enum (≈40 ViewModel/Activity/Screen files map between it and
  `domain.DesignCode`) is deferred as a separate mechanical refactor — too broad to change safely
  without UI-level tests.
- **CodeReference resolution test** (`CodeReferenceTest`, 3 tests): asserts the three code objects
  expose the same key set and that every key resolves for every code (no silent
  "Reference not found" reaching the UI).
- Full suite green: **60 suites / 1193 tests / 0 failures**.

### 9.7 Still open
- `CodeReference` ECP 203-2020 sub-clause numbers: authoritative text still needed for the
  Egyptian code (SBC verified — see §9.8).
- warehouse-vs-simple-beam divergence — **resolved in §9.6**, not by the drawing work: both
  `CalculatorEngine.calculateSteelMember` (`:393-400`) and `designSteelWarehouse` (via
  `steelMemberUtilizations`, `:621-628`) now call the *same* `designSteelMemberEcp/Aisc/Sbc`
  helpers, so identical section + grade + loads + code yield identical utilizations. Any remaining
  difference is the legitimate modelling input (warehouse rafter uses `Lb = bay·1000`, column uses
  `L = Lb = eave·1000`). Note the three helpers ignore their `memberType` argument and always run
  both the flexural/shear and the column-compression check — conservative and identical for both
  callers.
- Pile drawing divergence — **resolved (§9.13)**.

### 9.8 Deferred items completed (DesignCode unification round 2)

- **Full `DesignCode` unification (P0-1, now complete)** — nested `CalculatorEngine.DesignCode`
  deleted; all ≈49 referencing files migrated to `domain.entities.DesignCode`. Domain
  `.displayName` deliberately kept English (it is persisted to the DB by `DesignRepository`).
  UI labels preserved via new `displayNameAr` / `displayNameEn` / `localizedName` extensions in
  `CalculatorEngine.kt`. Old domain↔nested `toDomain()`/`fromDomain()` conversions removed.
- **AISC base-plate adapter (P2-9, complete)** — `AISCSteelDesignEngine.designConcentricBasePlate(
  SteelBasePlateDesign.ConcentricInput)` added as a thin delegate to the single shared AISC 360-16
  Ch.14 / DG1 implementation (`ecp.SteelBasePlateDesign`), giving `getSteelDesignEngine(...)` a
  complete API for every code without duplicating ≈1100 lines. `AISCBasePlateAdapterTest` (2 tests)
  pins delegate parity + positive plate output.
- **CodeReference SBC clause numbers (P0-3, SBC verified)** — corrected against the official
  SBC 304-2018 table of contents (follows ACI 318-14 chapter layout). Fixed wrong chapter pointers:
  beam shear → 22.5, development length → 25.4, footings/pile caps/combined footings → 13.3
  (not 15), column cover → 20.5.1 (not 7.7), seismic → 18 (not 21), slab deflection → 24.2.
  `CodeReferenceTest` extended to 4 tests (adds a regression test pinning these chapters).
- **Dependabot (P3-14)** — Two AGP bumps attempted. **AGP 8.9.2 → 8.11.1**, then
  **AGP 8.11.1 → 9.4.1 + Gradle 9.4.1 → 9.6.0** (AGP 9.4 requires Gradle 9.6). AGP 9.0+ enables
  built-in Kotlin + a new DSL by default; the project still applies `org.jetbrains.kotlin.android`,
  so it uses the documented temporary opt-out (`android.newDsl=false`,
  `android.builtInKotlin=false` — removed in AGP 10). Also `buildFeatures { resValues = true }`
  (AGP 9 no longer auto-enables it despite `android.defaults.buildfeatures.resValues=false`, but
  `defaultConfig` calls `resValue()`). Build + suite green on AGP 9.4.1.
  **Key finding:** the Dependabot count (15) was *unchanged* by either bump, and
  `netty`/`grpc`/`protobuf` are not on `debugRuntimeClasspath` — i.e. the alerts are not in the
  AGP buildscript classpath that Dependabot scans. Runtime `guava` is already forced to
  33.4.0-android via an app constraint. The repo had **no lockfile and no dependency-submission
  workflow**, so Dependabot relied on its own scheduled Graph resolver.
  **Action taken:** added `.github/workflows/dependency-submission.yml`
  (`gradle/actions/dependency-submission@v6`) to publish an accurate, promptly-refreshed graph so
  the remaining alerts can be enumerated and fixed precisely.
- Full suite green after all changes: **61 suites / 1196 tests / 0 failures**.

### 9.9 DXF pipeline made real (P1-8) + steel connection values (P1-6)

- **DXF entity-loss bug fixed** — `DxfExporter.exportDxf()` was shadowing the shared
  `StringBuilder` with a local one, so every primitive appended by the drawing
  (`addLine*`, `addCircle`, …) was dropped and the emitted ENTITIES section was empty.
  The method now resets and writes the field buffer; a new `renderToString(DxfDrawable)`
  allows file-free validation. The unused `Context` constructor param was dropped
  (`DxfExporter()`), making the writer JVM-testable; `ExportManager` and `SiteLayoutScreen`
  call sites updated. `DxfDrawable` is now a `fun interface`.
- **`ElementDxfDrawings` factories added (P1-8)** — data-driven `DxfDrawable` producers for
  every element: beam (section + elevation), column, slab, footing, combined footing, pile,
  retaining wall, stair, tank, shear-wall elevation (`wallElevation`), steel I-section, base
  plate, plus a generic fallback. Each draws in mm with reinforcement on the conventional
  layers (REBAR/STIRRUPS/BEAMS/WALLS/…).
- **`ExportManager.exportBoth` now exercisable** — `ExportViewModel` (Column/Beam/Steel) and
  the element ViewModels (Beam, Column, Footing, Slab, Flat Slab, Hordi Slab, Waffle Slab,
  Combined Footing, Retaining Wall, Stair, Tank, Pile, Shear Wall) write a DXF companion
  (`<name>.dxf`) next to the PDF via `DxfExporter.writeSibling(...)` (best-effort; a DXF
  failure never fails the PDF). **Strap footing and seismic** have no faithful section factory
  (distance/positions are not carried in the result object), so they are intentionally not
  wired rather than drawing fabricated geometry (relates to P1-7). `exportBoth` migration
  remains P2-11.
- **Steel connection values (P1-6)** — new `SteelConnectionDrawing.from()` reads the **actual**
  bolt diameter/count and weld size from `ConnectionDesignResult` (including `Hybrid`,
  which the old `as? Bolted` cast silently ignored) and derives gauge/pitch from the bolt
  diameter via AISC 360 J3.3 preferred 3d spacing (rounded to 5 mm), replacing the fixed
  90/75 literals. Used by both the on-screen `ProfessionalSteelDrawing` and the PDF path.
- **Tests** — `DxfExporterTest` (3) + `SteelConnectionDrawingTest` (4; caught the `Hybrid`
  cast bug). Suite now **63 suites / 1203 tests / 0 failures**.
- **Crash inventory re-checked (P2-12, partial)** — `SoilBearingCalculator` is already fully
  defensively clamped (ADR-010) and `UnitConverter` defaults unknown units to a factor of 1.0
  (no exception), so those "high risk" entries are stale. Remaining ViewModel-level
  string-parsing/null-spinner guards still to sweep.

### 9.10 Fallback honesty (P1-7), combined-footing note (P2-10), export unification (P2-11), tool sweep (P2-12)

- **P1-7 — no fabricated fallback geometry.** The two fallback drawing generators that still
  invented values now read the real ones:
  - `StairViewModel` — the flight width is carried into the calculation
    (`calculateStairPro(..., stairWidthMm = ...)`, set from `StairScreen`'s actual input) and used
    by the fallback bitmap instead of a hardcoded `1200.0`; the legacy `calculateStair` overload
    passes its own `width` through.
  - `CombinedFootingViewModel` — long/transverse bar spacing is derived from the bar count and the
    footing dimensions (`(length − 2·cover)/(count − 1)`, clamped to 100–250 mm) for both the
    fallback bitmap and the DXF sibling, instead of fixed 150/200 mm literals.
  - The remaining fallbacks were audited: every other element either derives its geometry from the
    result object or is guaranteed a Compose capture bitmap. Strap footing and seismic stay
    deliberately unwired (§9.9) — their result objects do not carry enough geometry to draw a
    faithful section.
- **P2-10 — combined-footing method note.** `FootingScreen`'s results card now states which engine
  produced the design (guarded by `selectedType == COMBINED`): ECP 203-2020 uses the dedicated
  `ECPCombinedFooting` engine, while ACI 318/SBC 304 route through the shared footing design
  interface — no `AciCombinedFooting`/`SbcCombinedFooting` classes are implied.
- **P2-11 — every export now goes through `ExportManager`.** New
  `ExportManager.exportReportAndDxf(...)` wraps the professional English reporter (the path all
  element screens used) and the AC1027 DXF companion in one call, returning an `ExportBundle`, and
  **surfaces the DXF to the user with a toast** (`export_dxf_created`, EN + AR) so the companion
  drawing is never written silently. Migrated: Beam, Column, Slab, Flat Slab, Hordi Slab, Waffle
  Slab, Footing, Combined Footing, Retaining Wall, Tank, Stair, Shear Wall, Pile, Strap Footing,
  Seismic, Soil Settlement, Project, Steel (4 reports), `ExportViewModel` (4 reports),
  `PdfExportHelper`, and the `Slab`/`Beam`/`Column` design fragments. The now-dead
  `ExportViewModel.writeDxfSibling` helper and every stale
  `ProfessionalEnglishPdfReporter` import were removed. The only remaining direct reporter use is
  `QualityEngineerViewModel`, which calls the photos-capable `generateReport()` (a signature
  `exportReportAndDxf` deliberately does not cover).
- **P2-12 — tool sweep closed.** Verified already safe: `SoilBearingCalculator`, `UnitConverter`,
  `RebarCalculator`, `ConcreteMixDesigner` (all denominators clamped/positive by construction and
  `ConcreteMixViewModel` guards every input with `InputGuard`), `SteelTables` (pure constants +
  nullable lookups), `BbsGenerator` (every entry point already has `InputGuard`), `SiteLayoutScreen`
  (`toDoubleOrNull()` + `catch (Throwable)` → error card), and the Room/DataStore `.first()` calls
  in `QualityEngineerViewModel`. `WaterLevelCalculator` and `ExpressionParser` **do not exist**
  (expression evaluation is inline in `CalculatorScreen` and already wrapped), and
  `SiteLayoutViewModel` was deleted in `d7c4ab9`. `RebarToolViewModel` was hardened separately
  (§9.x): every public method now has `InputGuard` + `try/catch` and clears stale results.
- Full suite green after all changes: **63 suites / 1203 tests / 0 failures**.

### 9.11 Last native-Canvas PDF path removed — `android.graphics.pdf` is gone (commit `b3512bd`)

**Finding.** `PdfExportHelper` documents that the Android-native
`android.graphics.pdf.PdfDocument` + Canvas pipeline killed the app with an **uncatchable Skia
SIGSEGV** (not caught by Java `try/catch`), which is why everything was moved to iText. The sweep
showed that migration was incomplete: four **live** PDF writers were still drawing on Canvas —
`PdfGenerator.generateBOQReport`, `generateEstimationReport` and `generateInventoryReport`
(reached from the Compose `BOQScreen` / `InventoryScreen`), and `WaterLevelScreen.exportWaterLevelPdf`.
The legacy Fragments/Activities that call `generateDesignReport`/`generateProfessionalReport` are
**unreachable** (`activity_main.xml`, the only host of `NavHostFragment`, is never inflated;
`MainActivity` is Compose-only; `res/navigation/nav_graph.xml` is self-labelled "LEGACY — UNUSED"),
so they were left as-is rather than migrated.

**Changes.**
- `PdfGenerator.generateBOQReport` / `generateEstimationReport` / `generateInventoryReport`
  rewritten on iText, reusing the object's shared helpers (`createStyledParagraph`, `addArabicCell`,
  `createHeaderCell`, `createReportHeader`, `getArabicFont`) so Arabic keeps going through
  `PdfTextSegmenter` Presentation-Forms shaping (ADR-005/009). All Canvas/Paint drawing removed.
- New `PdfGenerator.generateWaterLevelReport(context, title, summary, headers, rows, footer)`
  plus the `WaterLevelRow` data holder — a generic tabular survey report. `WaterLevelScreen` now
  builds plain rows and no longer imports `Canvas`/`Paint`/`PdfDocument`/`File`.
- `ExportManager.exportPdf` and `exportBoth` (both had **zero callers**) re-routed to
  `ProfessionalEnglishPdfReporter.generateReport(ReportType.GENERIC, …)`; their `safetyChecks`
  parameter is now `ProfessionalEnglishPdfReporter.SafetyCheck`.
- **Deleted:** `NativePdfExporter.kt` (the SIGSEGV source) and `PdfLayoutHelper.kt` (the canvas
  helper, now unreferenced).
- `ArabicFontProvider.getArabicTypeface` **kept** — it is still used by `DrawingUtils` for
  on-screen/drawing bitmaps; its KDoc no longer claims a PDF use case.
- Comment/KDoc corrections so the dead API is never resurrected: `PdfExportHelper`,
  `ElementDxfDrawings`, `ExportManager`, and the eight ViewModels that still carried a
  "CRITICAL FIX … use NativePdfExporter" note (they had called `exportReportAndDxf` since §9.10).

**Verified.** `rg "android\.graphics\.pdf" app/src` returns **only comments** — no executable
reference remains. `:app:compileDebugKotlin` OK; full suite **63 suites / 1203 tests / 0 failures**.

### 9.12 Warehouse drawing divergence closed (commit `745f125`)

**Finding.** The warehouse was the last matrix row marked "⚠️ divergent" (§5.2 item 5). The screen
renders `ProfessionalWarehouseDrawing` (Front / Plan / Side / 3D tabs), but
`SteelWarehouseProPdfExporter.addDrawingsPage` **always** called
`PdfDrawingGenerator.generateWarehouseDrawing` — a completely independent renderer — so the PDF
sketch could not match what the user was looking at. The exporter had no bitmap parameter and
`SteelViewModel.exportWarehouseProToPdf` never read the `pendingDrawingBitmap` that other elements
use (it already existed on the class, reset at calculate time).

**Changes.**
- `SteelWarehouseProPdfExporter.exportToDownload(..., drawingBitmap: Bitmap? = null)` — the sketch
  image is now `drawingBitmap ?: PdfDrawingGenerator.generateWarehouseDrawing(inputs, result)`;
  the supplementary 3D isometric block is unchanged.
- `SteelViewModel.exportWarehouseProToPdf` reads and **consumes** `pendingDrawingBitmap` on the
  caller thread before launching the IO work, so a later export can never show a stale view
  (the reset-on-calculate at `SteelViewModel.kt:107/:137` stays as the second guard).
- `SteelDesignScreen` follows the BeamScreen capture pattern exactly:
  `SteelWarehouseTab` owns `rememberDrawingCaptureLayer()` + screen px, the export dialog's
  confirm button runs `pdfCaptureLayer.captureToAndroidBitmap()` **before** calling the ViewModel,
  and `SteelWarehouseVisualizer` renders an invisible `DrawingCaptureArea` duplicate of
  `ProfessionalWarehouseDrawing` at the *current* `viewMode` — so whatever tab is on screen is what
  lands in the PDF.

**Result.** Matrix row **Warehouse** is now ✅. Remaining alternate-renderer elements at this point:
**Pile** (closed next, §9.13) and the **steel member / base plate** drawings (no capture area wired).

**Verified.** `:app:compileDebugKotlin` OK; full suite **63 suites / 1203 tests / 0 failures**.

### 9.13 Pile drawing capture wired (commit `c24c10d`)

**Finding.** Pile was the last element still marked "⚠️ alternate renderer" *and* the ViewModel side
was already complete: `PileFoundationViewModel` already owns `pendingDrawingBitmap` (reset on
calculate at `:82`, consumed at `:224-225`) and already falls back to
`PdfDrawingGenerator.generatePileDrawing` before calling `ExportManager.exportReportAndDxf`. The
**only** missing piece was screen-side — `PileFoundationScreen` had no capture layer and its export
button called `viewModel.exportToPdf(context)` directly, so `pendingDrawingBitmap` was always null
and the generator path always won.

**Changes (`PileFoundationScreen.kt` only).**
- Capture layer + `captureScope` + screen px added at the top of `PileFoundationScreen`.
- `selectedViewMode` **hoisted** out of the drawing `item {}` to the composable scope, so the
  invisible capture area can render the same tab the user has selected (Front/Plan/Section/Elevation).
- Export button now runs `pdfCaptureLayer.captureToAndroidBitmap()` inside `captureScope.launch`
  and assigns `viewModel.pendingDrawingBitmap` before `exportToPdf`.
- An invisible `DrawingCaptureArea` duplicate of `ProfessionalPileDrawing` is composed **after the
  `LazyColumn`** (not inside an item), guarded by `result?.let { res -> … }` — following BeamScreen.
  This matters because `LazyColumn` items are only composed when visible: an area placed inside the
  drawing item would leave the capture layer empty whenever the drawing is scrolled off-screen.

**Result.** Matrix row **Pile** is now ✅. (Steel member closed next, §9.14; the base plate has no
on-screen drawing to capture.)

**Verified.** `:app:compileDebugKotlin` OK; full suite **63 suites / 1203 tests / 0 failures**.

### 9.14 Steel member drawing capture wired (commit `f274318`)

**Finding.** `SteelViewModel` already owned `pendingDrawingBitmap` (reset on calculate) but
`exportToPdf` never read it — it always ran `PdfDrawingGenerator.generateSteelDrawing(...)`, an
independent renderer, while the screen drew `ProfessionalSteelDrawing` inside
`AppInteractiveDrawingScreen`. That was the last ⚠️ row in §5.1 with a real on-screen drawing.

**Changes.**
- `SteelViewModel.exportToPdf` consumes `pendingDrawingBitmap` on the caller thread before
  launching the IO work and uses `capturedDrawing ?: generateSteelDrawing(...)` — the generator
  (which reads the real section + connection properties) remains the fallback.
- `SteelSectionTab` follows the same three-part pattern as Pile/Warehouse: capture layer + screen px
  at function scope, `selectedViewMode` hoisted out of the drawing `item {}`, export button captures
  first, and an invisible `DrawingCaptureArea` of `ProfessionalSteelDrawing` is composed **after**
  the `LazyColumn` so it stays composed regardless of scroll position.
- The duplicate passes the identical argument list as the visible drawing, including
  `SteelConnectionDrawing.from(res.connectionDesign)` and the fixed `endPlateThickness = 12.0` /
  `hasStiffener = false`, so capture and screen cannot drift.

**Result.** §5.1 is now fully ✅ except **Steel base plate**, which legitimately has no on-screen
drawing (`rg` finds no `ProfessionalBasePlateDrawing` composable — only
`PdfDrawingGenerator.generateBasePlateDrawing`), so there is nothing to capture; its fallback reads
the real result values.

**Verified.** `:app:compileDebugKotlin` OK; full suite **63 suites / 1203 tests / 0 failures**.
