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
| Drawing → PDF | ⚠️ Gaps | Capture-based for most; **Seismic has no drawing; Pile/Steel/Warehouse use a divergent renderer**. Stale-bitmap bug in FlatSlab + ShearWall. Steel connection bolt params fixed in PDF path (§9.6). See §6. |
| Export pipelines | ✅ Consolidated | `ExportManager` unified (`exportPdf/Dxf/Ifc/Csv/Both/Package`); 12 dead exporters deleted. Migrations to `ExportManager` still pending. |
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
| FlatSlab | `ProfessionalFlatSlabDrawing` | ✅ | captured only | ✅ but **stale-bitmap bug** |
| WaffleSlab | `ProfessionalWaffleSlabDrawing` | ✅ | captured only | ✅ |
| HordiSlab | `ProfessionalHordiSlabDrawing` | ✅ | captured only | ✅ |
| Footing / Combined | `ProfessionalFooting/CombinedFootingDrawing` | ✅ | captured, else generator | ✅ (fallback synthesizes rebar) |
| StrapFooting | `ProfessionalStrapFootingDrawing` | ✅ | captured only | ✅ |
| Tank | `ProfessionalTankDrawing` | ✅ | captured, else `generateTankDrawing` | ✅ |
| Stair | `ProfessionalStairDrawing` | ✅ | captured, else `generateStairDrawing` | ✅ (fallback width fixed 1200) |
| RetainingWall | `ProfessionalRetainingWallDrawing` | ✅ | captured, else generator | ✅ (fallback synthesizes geometry) |
| ShearWall | `ProfessionalShearWallDrawing` | ✅ | captured only | ✅ but **stale-bitmap bug** |
| **Pile** | `ProfessionalPileDrawing` | ❌ | `generatePileDrawing` only | ⚠️ alternate renderer |
| **Steel member** | `ProfessionalSteelDrawing` | ❌ | `generateSteelDrawing` only | ⚠️ **bolt count/dia mismatch** |
| **Steel base plate** | (none) | ❌ | `generateBasePlateDrawing` | ⚠️ generator only |
| **Warehouse** | `ProfessionalWarehouseDrawing` | ✅/partial | `generateWarehouseDrawing` + 3D | ⚠️ divergent |
| **Seismic** | `ProfessionalSeismicDrawing` | ❌ | **none → placeholder** | ❌ biggest gap |
| Frame BMD/SFD | `FrameDrawingCanvas` | n/a | `generateFrameBitmap` from result | ✅ |

### 5.2 Concrete fidelity gaps
1. **Seismic PDF has no drawing** — `SeismicViewModel.kt:192` reads a `pendingDrawingBitmap` that is never set (no `DrawingCaptureArea` in `SeismicScreen.kt`) and has no generator fallback → `"[Drawing not available...]"`. `PdfDrawingGenerator.generateSeismicDrawing` exists (`:3060`) and can be wired. *(Fix implemented — see §9.)*
2. **Stale-bitmap bug** — `FlatSlabViewModel.kt:186` and `ShearWallViewModel.kt:287` read the captured bitmap but never reset `pendingDrawingBitmap = null`, so a later export can show an **old** drawing. *(Fix implemented — see §9.)*
3. ~~**Steel connection PDF mismatch**~~ — **FIXED (§9.6):** PDF path now reads actual `boltDia`/`boltCount` from `res.connectionDesign` (falls back to defaults) in both `SteelViewModel` and `ExportViewModel`.
4. **Fallback generators synthesize geometry** rather than read design values (combined footing hardcodes bars `CombinedFootingViewModel.kt:281-282`; retaining wall derives stem/base/rebar `:121-133`; stair width fixed `1200` `:135`; generic slab/column fallbacks `ExportViewModel.kt:336-353`). Only reachable when capture is absent.
5. **Pile / Warehouse** use a renderer distinct from the screen — same-element divergence possible.

---

## 6. Export Pipelines

- **Unified:** `utils/ExportManager.kt` exposes `exportPdf`, `exportProfessionalPdf`, `exportDxf`, `exportIfc`, `exportCsv`, `exportBoth`, `exportPackage`; `DxfDrawable` interface (`:224`) enables one-call PDF+DXF.
- **Deleted (dead):** 12 exporters (AdvancedPdfExporter, BilingualPdfHelper, CalculatorCadExporterV7, CalculatorDetailingV4, DxfExportEngine ×2, ComprehensivePdfExporter, FlatSlabPdfExporter, PileFoundationPdfExporter, ShearWallPdfExporter, SteelEnglishReportExporter, DxfStructuralExporter).
- **Live:** `NativePdfExporter`, `DxfExporter` (AC1027), `PdfGenerator`, `PdfDrawingGenerator`, `ProfessionalEnglishPdfReporter`, `PdfExportHelper`, `SteelWarehouseProPdfExporter`, `FrameAnalysisPdfExporter`.
- **Pending:** migrate ~15 ViewModels off direct `ProfessionalEnglishPdfReporter.generateReportLegacy` / `PdfGenerator` calls onto `ExportManager`; add per-element `DxfDrawable` implementations (none exist today → `exportBoth` not exercised).

---

## 7. Prioritized Development Plan

### P0 — Correctness of numbers (blocks trust)
1. **Unify code selection** to `domain.entities.DesignCode`; delete nested enum; route `SeismicScreen` (and any direct instantiation) through `CalculationFactory`. Use `fromDomain()` for labels.
2. **Delegate `CalculatorEngine`** methods to domain engines so results are code-differentiated (today some are identical for all codes).
3. **Verify clause numbers** in `CodeReference.kt` against real ECP/ACI/SBC text; add a unit test asserting each key resolves for each code.

### P1 — Drawing/PDF fidelity
4. Wire `generateSeismicDrawing` fallback + `DrawingCaptureArea` in `SeismicScreen`. *(fallback done)*
5. Reset `pendingDrawingBitmap` in FlatSlab/ShearWall. *(done)*
6. Pass **actual** connection values (bolt count/dia/gauge, weld, end-plate) into the Steel PDF path.
7. Make fallback generators read result values (no hardcoded bars/dimensions); or guarantee capture always exists for those elements.
8. Add `DxfDrawable` implementations for all elements so `exportBoth` is real.

### P2 — Coverage & robustness
9. Add AISC connection & base-plate methods; guard `classifySection`. *(classifySection guarded §9.6;
   AISC `calculateFilletWeldCapacity` + `calculateBoltShearCapacity` added §9.7; **base-plate port
   deferred** — `ecp.SteelBasePlateDesign` already implements AISC 360-16 Ch.14 / DG1 and is the only
   base-plate engine; duplicating 1100+ lines into ACI/SBC with no numeric change is unwarranted.)*
10. Add `AcidCombinedFooting`/`SbcCombinedFooting` doc-only note in UI (or dedicated classes).
11. Migrate all ViewModels to `ExportManager`.
12. Crash-hardening sweep for tools (SoilBearing, RebarTool, UnitConverter, SiteLayout, etc.).

### P3 — Release
13. Fill `AppIntegrityChecker.EXPECTED_SIGNER_FINGERPRINT` + production AdMob IDs.
14. Resolve 15 Dependabot alerts (8 high, 6 moderate, 1 low). *(partial §9.7: **runtime** `guava`
    forced 31.1-android → 33.4.0-android via an `app` dependency constraint, clearing CVE-2023-2976
    + CVE-2020-8908. Remaining alerts are almost all in **non-shipped tooling/config classpaths**
    — KSP's `grpc-netty` 1.57.2 pulls `netty-*` 4.1.93.Final, and room/hilt/dagger compiler pull
    `guava` 22.0/31.0.1 and `protobuf-java` 3.x. Forcing upgrades there can break KSP/Hilt at build
    time; needs a deliberate Kotlin/KSP bump, not a blind `force`.)*
15. ProGuard / signing / versioning / store listing; manual PDF+DXF QA = 15 elements × 3 codes.

---

## 8. Definition of Done (unchanged, non-negotiable)
```
☐ One DesignCode enum; all screens → CalculationFactory → code-correct engine
✅ CalculatorEngine steel member now delegates to engines (P0-2; other inline paths remain)
☐ Every engine: InputGuard + full result (codeNotes, warnings, safetyChecks, steps, diagram data)
☐ Every element PDF has a drawing that matches the on-screen drawing and the result (no stale/placeholder)
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
- `CodeReference` ECP 203-2020 and SBC sub-clause numbers: pass an automated "every key
  resolves per code" test (planned), but authoritative sub-clause text still needed.
- AISC connection & base-plate methods; warehouse-vs-simple-beam divergence.

*(See git diff for exact lines.)*
