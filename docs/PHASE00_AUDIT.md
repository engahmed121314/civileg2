# PHASE 00 — FULL PROJECT AUDIT
**Project:** civileg2 / "SiteEngineerPro" (`com.civileg.app`) — audited against the CivilEG Professional Master Specification
**Date:** 2026-08-24 · **Scope:** full codebase (364 Kotlin files, ~117k LOC, modules `:app` + `:core:calculations`)

---

## 0. Executive Summary

The app is currently a **dual-persona calculator hub** (ENGINEER hubs + NORMAL simplified tools) with per-element calculators × 3 codes (ECP/ACI/SBC) and a partially working export chain. It does **not** yet satisfy the spec's core principle of ONE SOURCE OF TRUTH: equations are duplicated across ~45 engine files with intra-ECP algebraic inconsistencies, there is no unified Project Model, no DrawingModel layer (three parallel drawing implementations), no revision system, no audit trail, destructive DB migrations, and a 2,157-line legacy calculation engine running in parallel with the governed engines.

**Overall readiness vs the spec's 40-phase plan: Phase 02–04 partially exist (with violations), Phases 05–08 largely missing, Phase 09 violated by design (per-file equations instead of CodeRuleEngine), element phases (10–17) exist as calculators but not as complete cycles, Phases 18–40 mostly missing.**

| Spec area | Status |
|---|---|
| Calculation engines (element × code) | 🟡 Exists (45 impls) but duplicated/inconsistent |
| Code Engine (centralized rules) | ❌ Violated (per-file equations + fallbacks) |
| Units system | ❌ Raw Doubles, implicit SI |
| Calculation Trace (Input→Formula→Result→Limit→Status) | ❌ Ad hoc; no NOT_CHECKED status |
| Load/Combination Engine | ❌ Self-inconsistent entity, no central factors |
| Structural Analysis | 🟡 2D frame FE exists, untested |
| Optimization/Constructability | ❌ First-fit only; primitive bin packing |
| Rebar Detailing/BBS | 🟡 Real backend math; UI stubbed |
| DrawingModel | ❌ Three parallel geometry stacks |
| DXF | 🟡 One live pipeline (+3 dead duplicate engines) |
| PDF | 🟡 Bitmap-based drawings embedded; 4 exporter families |
| BOQ | ✅ Real, model-derived; CSV-only export |
| Database schema | ❌ Flat result tables, JSON blobs, destructive migrations |
| Revision + Audit Trail | ❌ Absent (static "00" title-block string) |
| Security (Keystore/encrypted DB/R8) | ❌ CryptoUtils dead code; keep-all ProGuard rule |
| i18n AR/EN + RTL | ✅ Complete parity (1383/1383 strings), RTL on |
| Testing | 🔴 84 unit tests, mostly wiring/tautological; core module zero tests |

---

## 1. Repository & Toolchain Facts

- **Modules:** `settings.gradle.kts` includes `:app` and `:core:calculations` only. Root project name is **"SiteEngineerPro"** while `gradle.properties` says "CivilEG2".
- **Repo hygiene:** an unrelated Next.js/Prisma web app coexists at repo root (`package.json`, `prisma/`, `db/custom.db`, `.env`, `bun.lock`, `tool-results/`); stray artifacts under `app/src/main/java/com/civileg/app/.artifacts/`.
- **Toolchain:** AGP 8.7.3, Kotlin 2.1.0 (KSP), Gradle wrapper 9.4.1, compileSdk/targetSdk 35, minSdk 26, JDK 17 (CI mixes 21).
- **Key deps:** Compose BOM 2024.12.01 + Material3, Room 2.6.1, Hilt 2.54, Coroutines 1.10.2, iText 8.0.5, MPAndroidChart, Gson 2.10.1, Glide, Play Billing 7.1.1, AdMob 23.5.0 + UMP, Play Integrity 1.4.0, Firebase BOM (google-services plugin **commented out** → Firebase is dead weight). **No math library** (all numerics hand-written).
- **Legacy UI:** old View system (`nav_graph.xml`, HomeFragment, BeamDesignFragment, InputActivities) is orphaned from the Compose entry point.
- A second working copy exists at `%USERPROFILE%\civileg2_analysis` (same project + worklog).

---

## 2. Calculation Layer Findings (spec §13–15, §63)

### 2.1 Inventory
- Governed engines: `domain/calculations/{ecp,aci,sbc}` ≈ 45 implementations covering Beam(+Advanced/Doubly), Column(+Advanced), Slab(+Advanced/Hordi/Waffle/Flat), Footing(Combined/Strap), RetainingWall, Tank, Staircase, PileFoundation, ShearWall, Seismic, Steel (+Connections/BasePlate).
- Root-level parallel engines: `BeamDesignEngine.kt(+_Part2)` (42KB), `ColumnDesignEngine`, `FrameAnalysisEngine` (hand-written 2D FE), `ConcreteFrameDesign`, `SteelFrameDesign`, `CalculationFactory`, `InputGuard`.
- **Legacy:** `utils/CalculatorEngine.kt` — 2,157 lines running in parallel with everything above.

### 2.2 Equation duplication (spec rule: no copied equations)
Concrete examples:
- ECP one-way shear in **3 algebraically different forms**:
  1. `0.24*sqrt(fcu/γc)` — ECPSlab:84, ECPBeam:161, ECPColumn:185, ECPFooting:124, ECPTank:230, ECPRetainingWall:106, ECPStaircase:190, ECPPileFoundation:447, ECPHordiWaffleSlab:239, BeamDesignEngine_Part2:49
  2. `0.24*sqrt(fcu)/γc` — ECPCombinedFooting:268,517
  3. `sqrt(fcu/1.5)` hardcoded — ECPAdvancedSlab:301,427,483
- Known γc-inside-sqrt bug fixed **only** in ECPAdvancedColumn:233-235; same wrong form still live in ECPFooting:208,552,607, ECPCombinedFooting:279,528, ECPPileFoundation:427, ECPTank:422, ECPHordiWaffleSlab:1252, ECPAdvancedSlab:301.
- ACI/SBC punching (`φ·0.33√f'c`) and one-way shear (`φ·0.17√f'c`) re-implemented in ≥8 files each.
- SBCPileFoundation:345-356 omits cube→cylinder conversion its ACI twin performs (ACIPileFoundation:343) — same check on different material basis.
- Column axial capacity implemented 4× identically (ACIColumn, SBCColumn, ColumnDesignEngine, CalculatorEngine); two independent strain-compatibility interaction-diagram solvers (ECPAdvancedColumn vs utils/InteractionDiagram.kt).
- `0.8·fcu` conversion inlined ~50× across 20+ files (named constant exists in only 2 files).

### 2.3 Code-fallback violations (spec rule: every code independent)
- Direct delegation: SBCBeam.kt:31 →ACI, SBCSeismic.kt:14 →ACI, SBCRetainingWall.kt:18 →ACI (patching FS limit to 1.5).
- Factory-level silent fallbacks to ECP regardless of selected code: Hordi/Waffle/DoublyReinforced/CombinedFooting (CalculationFactory.kt).

### 2.4 Methodology hybrids (correctness risk)
- SBCFlatSlab.checkPunchingShear (L366-439): verbatim ECP port (0.24/0.16/0.08√fcu, αs keyed on columnWidth≥2000 instead of edge location, missing corner-case coefficient) with φ=0.75 swapped in; mixes `fy/γs` limit-state design under LRFD φ.
- SBCShearWall header documents dual factoring (γc/γs AND φ) applied together.
- `LoadCombination` entity self-inconsistent: DEAD_LIVE factor=1.5 vs getLoadFactors(ECP)=(1.4,1.6); wind absent from pairs; DEAD_EARTHQUAKE=0.9 without E term.

### 2.5 Magic numbers / undocumented factors (spec §89)
- L/250 & L/180 deflection limits hardcoded in 11+ files.
- CalculatorEngine placeholders: `-> 1000.0` (L459), prestress 0.80, minTs=L/45, voidRatio=0.55, dowels As=0.005Ag+fixed Ø16 (L2148-53), joint ld α=1.0/1.25.
- ACIBeam: mutable companion var `ACI_MIN_DEVELOPMENT_LENGTH = 300.0`.
- StrapFootingDesignEngine: thickness fixed 800mm; fabricated results (`isSafe=true, utilizationRatio=0.5`) — **default-PASS violation**.

### 2.6 Trace / status / units / validation
- No typed units anywhere in engine signatures; UnitConverter is UI-side only (spec §8 unmet).
- DesignStatus has ERROR but **no NOT_CHECKED** (spec §62 unmet).
- No unified trace object; formula strings ad hoc.
- InputGuard used by only 11 of 45 engines.

### 2.7 Stubs found
CalculatorEngine:459 · CalculateElementBoq:124 (fixed 2×2×0.10m drop volume) · BbsGenerator:34 (bar len 5000 placeholder) · ECPAdvancedBeam:659 (DEEP_BEAM silently→RECTANGULAR) · ComprehensivePdfExporter:324 · StrapFooting fabricated results.

---

## 3. UI / Drawings / Export Findings (spec §35–54)

### 3.1 Screens
39 screens total: 32 Compose (`ui/compose/screens/`), 4 legacy-dir Compose (ConcreteMix, ShearWall, SoilBearing, WindLoad), 3 NORMAL-persona tools. Dual persona via `UserType {NORMAL, ENGINEER}` (PreferencesManager DataStore; MainActivity routes accordingly). ArchiveScreen is a real CRUD project manager. Workflow shape is **calculator-hub**, not PROJECT→MODEL→…→QA pipeline (spec §70 gap).

### 3.2 Drawing architecture — three parallel stacks, no DrawingModel (spec §35–37)
1. Live Compose Canvas components (`ui/compose/components/drawings/Professional*Drawing.kt` + shared DrawingUtils palette) — parameter-driven from real design results ✔ but direct draw calls.
2. `PdfDrawingGenerator.kt` (~3,100 lines) duplicates the same geometry as Android-Canvas **bitmaps** for PDF embedding (spec §53 vector requirement unmet).
3. DXF writer path: CadDxfExporter → CalculatorDetailingV4 → CalculatorCadExporterV7 (multi-sheet AC1015, index sheet, master BBS sheet, revision table "00", validate()/qaScan() QA).
Design→drawRectangle anti-pattern confirmed structurally (no DetailingModel→DrawingModel abstraction outside detailing package which is orphaned — see below).

### 3.3 Dead/orphaned code (confirmed zero production callers)
`utils/exporters/DxfExportEngine.kt` · `DxfStructuralExporter.kt` · `exporters/DxfExporter.kt` · entire `utils/detailing/` package (CadGeometry/CadEntity model, DrawingValidator, DxfWriter) · `utils/AdvancedPdfExporter.kt` · `utils/BilingualPdfHelper.kt` · legacy View nav graph + fragments · ColumnComposeViewModel (self-deprecated).
Note irony: the orphaned `detailing/` package contains exactly the DrawingValidator/CadEntity abstraction the spec requires.

### 3.4 PDF
Four families coexist: NativePdfExporter (9 ViewModels, post-"CRITICAL FIX 2026-07-27"), iText 8 family incl. ComprehensivePdfExporter.generateReportSafe (explicitly built as safe replacement), bitmap PdfDrawingGenerator embeds, PdfGenerator (BOQ/inventory). Not vector; Arabic shaping solved pragmatically.

### 3.5 BBS / BOQ / Cutting
- BBS backend math is real (bend allowance, laps, anchorage → BarSchedule rows in CalculatorDetailingV4) feeding V7's master-BBS DXF sheet; **but MasterBbsScreen UI receives `emptyList()` (stub)**; standalone BbsGenerator uses placeholder lengths; cutting optimization is basic FFD bin-packing returning a summary string only (spec §34 unmet).
- BOQ: real end-to-end (`CalculateElementBoq` use case + EstimationEngine + BOQViewModel), CSV-only Excel export (UTF-8 BOM), no POI/Xlsx.

### 3.6 i18n
✅ Full AR/EN parity (values-ar = values = 1383 strings), `supportsRtl=true`, LocaleHelper persists language+direction, bilingual PDF text via StaticLayout/BIDI. This satisfies much of spec §54 for UI/PDF text.

### 3.7 Revisions/QA
No R0-Rn system anywhere; static `"00"` string in V7 title block. ExecutionLogScreen provides site pour logs/inspections (useful seed for spec §56 Site Module).

---

## 4. Data / Security / Infra Findings (spec §57–60)

### 4.1 Database (16 tables, version 8)
`projects`, `designs` (JSON blobs), plus wide flat tables: footings, columns_table, slabs, beams, stairs, retaining_walls, tanks, materials, inventory, pour_logs, site_inspections, flat_slabs (~45 cols), pile_foundations, shear_walls. TypeConverters silently fall back to enum defaults.
**Missing vs spec §60:** project_revisions, site_data, sections, stories, nodes, members, load_cases, load_combinations, analysis_results, design_results, engineering_checks, rebar, drawings, drawing_revisions, bbs, boq, site_reports. No FKs (loose projectId Longs). No audit hash/engine-version fields (spec §58 unmet).
**Critical:** `.fallbackToDestructiveMigration()` in BOTH AppDatabase.kt:61 and AppModule.kt:30; defined MIGRATION_6_7 never registered; `exportSchema=false`; two competing singleton builders (companion + Hilt). Any schema change wipes user data.

### 4.2 DI
Hilt 2.54 (KSP). Modules: AppModule (DB+DAOs+repos+exporter), CalculationModule (@Named strategies), DesignCodeModule (multibinding map). Pattern inconsistency: some VMs bypass repositories/use-cases and inject DAOs directly; LiveData vs StateFlow mix.

### 4.3 Security (spec §59 mostly unmet)
- `CryptoUtils.kt` AES-256-GCM: **dead code, zero callers**, caller-supplied keys, no Keystore.
- No SQLCipher/EncryptedFile/MasterKey anywhere → DB plaintext.
- AppIntegrityChecker: root/emulator/Frida detection present but signature fingerprint constant empty; PlaySafetyChecker requests Integrity token but never verifies/enforces server-side.
- R8 enabled BUT `proguard-rules.pro:113 -keep class com.civileg.app.** { *; }` negates obfuscation.
- Manifest posture good otherwise: allowBackup=false, cleartext off, FLAG_SECURE, FileProvider scoped, exported=false except launcher.
- **Committed weak secret:** `.github/workflows/build-apk.yml:32-48` hardcodes keystore password `civileg2024` and generates throwaway release keystore in CI. AdMob still on Google **test App ID** ("REPLACE before release").
- Monetization: AdMob interstitials/native/banners (test IDs), Play Billing subscriptions with client-side-only entitlement; engineering flows remain fully offline ✔.

### 4.4 Tests (spec §76–78 largely unmet)
84 @Test methods / 14 unit files; 1 template instrumented file; **zero tests in :core:calculations**; predominantly wiring/factory assertions or tautological recomputation (expected values computed with same formula under test); only real numeric test recomputes inline; no benchmark/regression suite; CI workflows inconsistent (JDK mismatch, manual dispatch, plaintext keystore).

---

## 5. Verdict per Spec Rule

| Spec rule (§3) | Status | Evidence |
|---|---|---|
| No duplicated equations | ❌ VIOLATED | §2.2 above |
| No code fallback | ❌ VIOLATED | 3 delegation files + 4 factory fallbacks |
| Each code independent | ⚠️ PARTIAL | SBC identity unresolved (ECP-port-with-φ hybrids) |
| Every result traceable | ❌ | No trace object, no NOT_CHECKED |
| No default PASS | ❌ VIOLATED | StrapFooting fabricated results |
| No legacy duplication | ❌ VIOLATED | CalculatorEngine 2,157 lines parallel |
| DXF+PDF from same DrawingModel | ❌ VIOLATED | 3 geometry stacks, raster PDF embeds |
| BBS from Rebar Model | ⚠️ PARTIAL | Real in V4/V7 chain; UI stubbed; standalone generator fake |
| BOQ from model | ✅ Mostly | CalculateElementBoq from design JSON |
| Complete pages before moving on | ❌ | Multiple half-wired features (MasterBBS UI, dead exporters) |

---

## 6. Remediation Roadmap (ordered, maps to spec phases)

**R0 — Stop the bleeding (pre-phase hygiene)**
1. Register real Room migrations; remove both `fallbackToDestructiveMigration()` calls; enable schema export; single DB provider.
2. Quarantine legacy: mark `CalculatorEngine.kt`, root-level Beam/ColumnDesignEngine, dead exporters/`detailing/` package, orphaned View UI as `@Deprecated` behind an adapter boundary (spec §79) — do not delete until replacements pass regression.
3. Remove committed keystore password from build-apk.yml; rotate; replace AdMob test IDs before any release.

**PHASE 02–03 equivalent — Core kernel**
4. Introduce `core/math` + `core/units`: typed quantities (Length/Force/Stress) OR at minimum central UnitConverter used by engines; NaN/∞/singular-matrix guards (spec §7).
5. Extract ONE shared shear/punching/torsion/flexure equation set per code into `codes/ecp`, `codes/aci`, `codes/sbc` rule objects; migrate all 45 engines to call them (spec §14). Propagate the ECPAdvancedColumn γc fix everywhere (§2.2).

**PHASE 06–07 equivalent**
6. Rewrite `LoadCombination` entity: per-code factor tables (ECP 1.4G+1.6Q etc.), envelope support; forbid calculators from inventing factors.

**PHASE 05 equivalent**
7. Add spec tables incrementally (projects_revisions first, then nodes/members/sections/load_cases…) alongside flat tables; adapters map old↔new during transition.

**Trace + status (cross-cutting)**
8. Add `NOT_CHECKED` to DesignStatus; introduce CalculationTrace(input/formula/substitution/result/limit/utilization/status/codeRef) returned by every engine check; wire InputGuard into all 45 engines.

**PHASE 21–27 equivalent**
9. Revive `utils/detailing/` CadEntity+DrawingValidator as the seed of the single DetailingModel→DrawingModel→{Compose renderer, DXF writer, vector PDF} pipeline; delete the two dead DXF stacks after migration; make MasterBbsScreen consume the real V4 schedule; implement cutting plan output (spec §34).

**Testing (spec §77–78)**
10. Create benchmark cases (Beam-001 etc.) with independently hand-computed expected values + tolerance; wire regression suite to run on any engine change; add :core:calculations tests.

---

## Appendix A — Element × Code matrix (current)

| Element | ECP | ACI | SBC |
|---|---|---|---|
| Beam (+Advanced) | ✔ | ✔ | ⚠️ delegates→ACI |
| Column (+Advanced) | ✔ | ✔ | ✔ (4th parallel impl exists) |
| Slab (+Advanced/Hordi/Waffle/Flat) | ✔ | ✔ | ⚠️ hybrid methodology |
| ShearWall | ✔ | ✔ | ⚠️ dual factoring |
| Footing (Isolated/Combined/Strap*) | ✔ | ✔ | ✔ (*Strap fabricated) |
| RetainingWall | ✔ | ✔ | ⚠️ delegates→ACI |
| Tank | ✔ | ✔ | ✔ |
| Staircase | ✔ | ✔ | ✔ |
| PileFoundation | ✔ | ✔ | ⚠️ missing fcu conversion |
| Seismic | ✔ | ✔ (ASCE7) | ⚠️ delegates→ACI (zone map override) |
| Steel (+Conn/BasePlate) | ✔ (φ=0.9 blanket) | ✔ (AISC) | ✔ |

## Appendix B — Confirmed dead code list
`utils/exporters/DxfExportEngine.kt` · `utils/exporters/DxfStructuralExporter.kt` · `utils/exporters/DxfExporter.kt` · `utils/detailing/*` (3 files) · `utils/AdvancedPdfExporter.kt` · `utils/BilingualPdfHelper.kt` · `res/navigation/nav_graph.xml` + legacy fragments/InputActivities · `security/CryptoUtils.kt` · `db/Project.kt`, `db/Design.kt` (empty placeholders) · Firebase deps (plugin disabled) · ColumnComposeViewModel.
