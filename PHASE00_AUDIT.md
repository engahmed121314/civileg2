# PHASE 00 — FULL PROJECT AUDIT REPORT
**Project:** civileg2 (CivilEG) · `C:\Users\ahmed\AndroidStudioProjects\civileg2`
**Audited against:** CIVILEG PROFESSIONAL Master Development Specification
**Date:** 2026-08-24 · **Build status at audit time:** ✅ `:app:compileDebugKotlin` + `:core:calculations:compileKotlin` PASS
**Revision 2 — 2026-08-25:** Round-2 equation-level deep dive completed across all element families (RC beam/column/slab, foundations/piles/retaining/tanks/shear walls/seismic, steel/frame/BOQ) and the delivery layer (PDF/DXF/data/CI/security). New findings in **Addendum A** below; several are more severe than anything in the original tables.

---

## 1. Executive Summary

The codebase is currently a **collection of per-element calculators with export utilities**, not the unified platform of the spec. The core architectural principles — One Source of Truth, centralized CodeRuleEngine, single Load/Combination engine, DetailingModel → DrawingModel pipeline, traceable checks (`INPUT→FORMULA→RESULT→LIMIT→STATUS`) — **do not exist yet**.

| Metric | Value |
|---|---|
| Main source files | 349 (`app`) + 17 (`core:calculations`), ~5.9 MB |
| Test files | 15 unit + 1 boilerplate instrumented (**~4% file coverage**) |
| Room tables | 16 vs spec's 21-model schema (~14–29% coverage) |
| DXF writers found | **8 competing implementations, none shared geometry** |
| PDF generators found | **13, of which 5 confirmed dead (~4,200 lines)** |
| Independent load-combination definition sites | ≥5 |
| Confirmed engineering defects (WRONG-class) | 12 critical/high |
| TODO/FIXME markers | 0 (hygiene good; risk hides in silent fallbacks instead) |

**Verdict: NOT COMPLETE by spec Definition-of-Done for any module.** No element satisfies even the Beam DoD list (§81): torsion, crack width, development/lap/curtailment, optimization, BBS-from-rebar-model are all missing or defective.

---

## 2. Architecture Map vs Spec Target (spec §5)

| Spec layer | Current state | Verdict |
|---|---|---|
| `core/math, matrix, geometry, units, tolerance, ids` | **Absent.** Only `utils/UnitConverter.kt`; no typed units, no matrix/solver lib outside FrameAnalysisEngine internals, no tolerance system | MISSING |
| `domain/project` (StructuralProject model §10) | Absent. No project tree; designs stored as JSON blobs per element | MISSING |
| `domain/site` | `SiteLayoutScreen.kt` only (visual layout + crude DXF); no survey/levels/setbacks data model | STUB |
| `domain/materials` | Hardcoded constants scattered (γc=25 re-hardcoded ~15×); no MaterialProperties engine | MISSING |
| `domain/sections` | Steel sections as hardcoded arrays (`SteelTables.kt`); no SectionProperties/GeometryEngine computing A/Ix/Zx from geometry | PARTIAL |
| `domain/loads` + combinations (§16–17) | No load engine. Combos defined ad-hoc in ≥5 places; frame combos **never applied** to solver | MISSING/BROKEN |
| `domain/analysis` (§18) | Real 2D stiffness-matrix frame solver (`FrameAnalysisEngine.kt`) ✔; three-moment continuous beam (UDL-only) ✔; **no 3D/truss/grillage/plate/shell/P-delta/modal/pattern loads/envelope** | PARTIAL (2D static only) |
| `codes/*` CodeRuleEngine (§13–14) | **Absent.** Instead: 3 parallel calculator families (ECP 22 files / ACI 15 / SBC 15) with duplicated formulas; steel stack is AISC mechanics mislabeled "ECP 205" | WRONG ARCHITECTURE |
| `application/detailing` (§31–32) | Live shallow model (`CalculatorDetailingV4`) + **orphaned superior package** (`utils/detailing/`: CadGeometry+DetailingModel+DrawingValidator+DxfWriter, zero production callers). Bend deduction nonexistent anywhere | FRAGMENTED |
| `application/dxf` (§45–47) | 8 writers; best one orphaned; no standard layers (A-GRID/S-CONC/S-REBAR absent); Arabic impossible in DXF; validation only in V7 + orphaned DxfWriter | FRAGMENTED |
| `application/pdf` (§48–53) | iText 8 pipeline works incl. Arabic shaping (recent fixes); but 13 generators, 5 dead; no calculation-trace renderer; vector drawings via bitmap screenshots (Canvas→PNG→PDF), not true vector | WORKING / UNCONSOLIDATED |
| `application/bbs` (§33–34) | 4 disconnected implementations; live one has qty≡1 bug; MasterBBS screen renders permanently empty; cutting optimization exists and is genuinely good (`AnalyzeRebarInventory`, BFD+BnB, kerf) but fed by engines not BBS | BROKEN |
| `application/boq` (§55) | In-memory estimation only; hardcoded ratios/prices; never persisted; not derived from structural model | STUB |
| `data` (§60) | 16 flat per-element tables; results as JSON blobs; no revisions/nodes/members/load_cases/analysis_results/bbs/drawings tables; **`fallbackToDestructiveMigration()` while a written migration sits unused** | GAP |
| `security` (§59) | CryptoUtils/AppIntegrityChecker exist but are **unwired**; PlayIntegrity logs only; plain unencrypted Room; release builds silently fall back to debug signing if keystore.properties missing | SCAFFOLD ONLY |
| `ui` (§70–73) | Per-element Compose screens (good quality); no PROJECT/MODEL/ANALYSIS/DESIGN hub architecture; no 3D; Design Review color-coding partial | PARTIAL |

---

## 3. Equation Classification (spec §63 taxonomy)

### 3.1 CRITICAL — WRONG (must fix before anything else)

| # | Defect | Location | Evidence |
|---|---|---|---|
| W1 | **Entire steel stack is AISC 360 presented as "ECP 205"** — wrong philosophy, curves, limits for Egyptian users | `ecp/SteelDesignEngine.kt`, `SteelConnectionDesign.kt`, `SteelBasePlateDesign.kt` | LTB F2 breakpoints, E3 column curve, H1-1 interaction all AISC; comment admits "ECP 205 / AISC 360" |
| W2 | Elastic LTB uses `ry` where AISC F2-4 requires `rt` — unconservative Mn on long unbraced spans, replicated in all 3 independent steel engines | `AISCSteelDesignEngine.kt:1286`, `SBCSteelDesignEngine.kt`, `ecp/SteelDesignEngine.kt:385` | `Lb/ry` in Fcr elastic branch |
| W3 | Hordi/Waffle K_bal dimensionally incoherent (returns ≈0.044 vs true ≈0.12–0.17) → false over-reinforced failures; waffle silently falls back to hardcoded 0.186 | `ECPHordiWaffleSlab.kt:565–574, 976, 1058` | `xBal=(600·30)/(600+fy)` with magic constants |
| W4 | Doubly-reinforced beam conflates K_bal with neutral-axis ratio (`rBal=kBal(1−kBal/2)`, `zBalanced=d(1−kBal/2)`); β1=0.8 contradicts package-wide 0.9 | `ECPDoublyReinforcedBeam.kt:101–103, 191, 238–300` | Category error; conservative direction but wrong math |
| W5 | Column Euler capacity off by ~1000× (`EI/1e6` instead of `/1e9`) and omits effective-length K; no 0.80φPn,max cap; no minimum eccentricity; slenderness limit 22 hardcoded ignoring sway/braced | `ACIColumn.kt:78–91`, mirrored `SBCColumn.kt` | Unit conversion error |
| W6 | Footing one-way shear tributary uses d/2 (punching distance) instead of d → understated Vu | `ACIFooting.kt:104–105`, mirrored `SBCFooting.kt` | Comment even states d/2 |
| W7 | SBC seismic computes base shear with ACI zone factors then relabels result with Saudi factors without recomputation | `SBCSeismic.kt:36–46` | ZONE_4: computed @SDS=0.40, reported @0.25 |
| W8 | SBC pile applies LF_DL=1.4 to **total service load** (no live factor ever applied) | `SBCPileFoundation.kt:590` | vs its own header claiming 1.4DL+1.6LL |
| W9 | ECP column max steel capped at 8% Ag; ECP limit is 6% (4% at laps) — and a test asserts the wrong value | `ECPColumn.kt` + `ECPColumnTest` test 4 | Unconservative in splice zones |
| W10 | Development length ψs inverted (large bars get 0.8 instead of ≤19 mm bars) → shortens ld for big bars | `ACIBeam.kt:289` | ACI Table 25.4.2.5 violation |
| W11 | **BBS quantity always 1** for every spaced bar → exported tonnage grossly understated | `CalculatorDetailingV4.kt:89` | `spacingMm?.let{1} ?: 1` both branches = 1 |
| W12 | Master BBS screen wired to `emptyList()` — feature permanently empty | `MainActivity.kt:357` | Comment admits "needs wiring" |

### 3.2 HIGH — INCOMPLETE / APPROXIMATE

| # | Finding | Location |
|---|---|---|
| I1 | Frame load combinations defined in settings but **never factored into any solve**; `loadCase` metadata collected in UI, ignored by solver; users must pre-factor loads manually | `FrameEntities.kt:150,162,319–322`; `FrameAnalysisViewModel.kt:223` |
| I2 | Combination logic scattered across ≥5 sites with contradictions (core enum carries `factor=1.5` AND `getLoadFactors()=1.4/1.6` internally) | `core/.../LoadCombination.kt:9 vs :31` |
| I3 | SBC family is a patchwork: wrappers over ACI (Beam/Seismic/RetainingWall), verbatim ACI copies (Column/Slab/Tank/Staircase/Footing), and **ECP mechanics mislabeled "SBC 304"** (FlatSlab/ShearWall/PileFoundation/AdvancedSlab — γc=1.5, 0.67fcu/γc, ECP wall shear, ECP 1.4D+1.6L) | Duplication matrix §3.4 |
| I4 | ACISlab two-way labeled "Direct Design Method" but uses invented coefficients (no Mo=q·ln²·l2/8, no 65/35 split); `utilizationRatio=rho/rhoMin` is not strength utilization | `ACISlab.kt:90+` |
| I5 | Flat-slab αs chosen by column size (≥2000mm) instead of location interior/edge/corner (40/30/20); punching moment transfer (γv·Mu) absent everywhere | `SBCFlatSlab.kt:383–386` |
| I6 | ECPShearWall/ECPAdvancedColumn/ECPAdvancedBeam embed ACI mechanics (Whitney block, Branson Ie, φ-reduced interaction curves) inside ECP engines — inconsistent with sibling engines' ECP formulation | per-file notes |
| I7 | Pile Nq=40 constant regardless of φ (true range ≈10–60); end-bearing unreliable outside medium-dense sand | `ECPPileFoundation.kt` |
| I8 | "Marcus method" two-way coefficient actually 1/(1+r); edge-column punching enhancement dead-coded (`isEdgeColumn=false` permanent) | `ECPHordiWaffleSlab.kt:714,1256` |
| I9 | Tank flexure φ=0.65 attributed to ACI 350 (real value 0.9 tension-controlled) | `ACITank.kt:22` |
| I10 | Wind calculator is IS 875/ASCE hybrid under an app that advertises ECP 205 | `WindLoadCalculator.kt:266,274,321` |
| I11 | Seismic (all codes): equivalent lateral force only, invented zone→SDS maps, Cs,max at fixed T=0.1s never governs, k=1 fixed vertical distribution, no drift/torsion/orthogonal checks | `ACISeismic.kt`, `SBCSeismic.kt`, `ECPSeismic.kt` |
| I12 | Steel local buckling: λr values used as compact limits (flange 0.56√(E/Fy) vs compact 0.38√(E/Fy)); web axial limit used for flexure; φshear=0.9 vs AISC 1.00; rupture/block-shear φ=0.90 vs required 0.75 | `AISCSteelDesignEngine.kt:113–117,834`; `ecp/SteelDesignEngine.kt` |
| I13 | Base plate λ=max(√2m,√2n,√(m²+n²)) inflates plate ~41% vs DG1 max(m,n); anchor arm fudge 0.4·min(B,L); √2 concentration assumed without A2 | `SteelBasePlateDesign.kt:371–375` |

### 3.3 MISSING checks (vs spec §20–28 check lists)

Torsion design (only partial in AdvancedBeams) · lap splice length & Class A/B (absent everywhere) · curtailment/development detailing · seismic confinement-zone geometry (hinge length lo, s₀, 135° hooks) in columns · punching moment transfer · crack-width control (only tanks/retaining walls) · long-term deflection (only AdvancedBeams proper) · slab openings/corner reinforcement · pattern (skip) live loading · envelope generation · settlement analysis (foundations) · global stability (retaining) · uplift/shear-friction at joints (walls/tanks).

### 3.4 DUPLICATED (One Source of Truth violations)

- **Code-twin duplication:** SBC↔ACI overlap 44–95% per element (identical formula lines quoted side-by-side in audit); Staircase trio ~63% common template; pile geotech cores byte-identical across all three families.
- **Two parallel design stacks:** legacy `CalculatorEngine.kt` (2,019 lines, primary path for ≥10 ViewModels) coexists with `domain/calculations/{ecp,aci,sbc}` — same input can yield different results depending on screen.
- **Concrete density 25 kN/m³ hardcoded ~15×** despite existing `Constants.CONCRETE_DENSITY`.
- Dev length recomputed inline in UI (`BeamScreen.kt:415–427`) instead of consuming engine results.
- Two repository layers (live `db/DesignRepository` vs dead `domain.repository`+`DesignRepositoryImpl` trio — an unwired Hilt landmine).
- Two parallel base-design packages: `app/domain/calculations/base/*` vs `core:calculations/base/*`.
- Bar-weight formula consistent (✔ 0.006165d²) but implemented in 4 separate files.

### 3.5 LEGACY / DEAD CODE inventory

| Item | Lines | Status |
|---|---|---|
| `NativePdfExporter.kt` (native Skia crash history) | 673 | Dead, delete |
| `AdvancedPdfExporter.kt` | 399 | Dead, delete |
| `SteelEnglishReportExporter.kt` | 1,833 | Dead, delete |
| `PileFoundationPdfExporter.kt` | 414 | Dead, delete |
| `ShearWallPdfExporter.kt` | 430 | Dead, delete |
| `FlatSlabPdfExporter.kt` | 450 | Dead, delete |
| DXF writers #2/#4/#5/#8 (`exporters/DxfExporter`, both `DxfExportEngine`s, `DxfStructuralExporter`) | ~3,500 | Dead chain, harvest then delete |
| `utils/detailing/` package (best DXF writer + validator + geometry) | ~2,230 | **Orphaned — wire in, do not delete** |
| `BbsGenerator.kt` generators | — | Dead (placeholder dims) |
| Tombstone stubs (`db/Design.kt`, `db/Project.kt`, adapters, `ecp/ComprehensiveCostManager`, …) | — | Safe cleanup |
| `model/DesignItem.kt` | — | Zero references |

### 3.6 CORRECT (verified sound)

FrameAnalysisEngine (true stiffness method) · ContinuousBeamAnalysis three-moment (UDL-only limitation) · SoilBearingCalculator (Meyerhof/Hansen/Vesic Nγ correct) · ECP base beam/column/slab/footing/stair/tank/retaining core formulas · ACI base anchors (Rn–ρ, β₁ interpolation, As,min, Vc, Av,min, punching trio) · ACIAdvancedBeam Branson deflection implementation · AnalyzeRebarInventory cutting optimizer · bar-weight math · SoilBearingCalculator eccentricity/water-table handling.

---

## 4. Data, Security, Testing, Build

**Data (spec §60):** Schema gap 14–29%. Missing entirely: project_revisions, site_data, stories, nodes, members, load_cases, load_combinations, analysis_results, engineering_checks, rebar, drawings, drawing_revisions, bbs, boq, site_reports. Results stored as JSON blobs — defeats traceability (§15, §58). **Both DB builders call `fallbackToDestructiveMigration()` while `MIGRATION_6_7` exists unused — next schema change wipes user data.**

**Security (spec §59):** R8 minify ✔, backup rules ✔, cleartext blocked ✔, exported components minimal ✔. BUT: CryptoUtils + AppIntegrityChecker unwired; signature-pinning fingerprint empty string; Play Integrity token logged not enforced; plain Room (no SQLCipher); **release builds silently debug-signed when keystore.properties absent**.

**Testing (spec §76–77):** 84 test methods vs 366 sources; zero coverage on every ACI/SBC twin, all Advanced variants, all steel/frame engines, entire core module. Existing numeric tests are largely tautological (recompute expected via same formula); one test enshrines the wrong 8% cap (W9). No benchmark-case suite (spec §77) exists.

**Build:** AGP 8.7.3 / Kotlin 2.1.0 / Room 2.6.1 / Hilt 2.54 / iText 8.0.5 / compileSdk 35 — modern and healthy. `resValues=false` default may conflict with 5 registered resValue strings. Crashlytics plugin commented out while deps remain.

---

## 5. Gap Map vs Implementation Phases (spec §87)

| Phase | Status |
|---|---|
| 00 Audit | ✅ this report |
| 01 Dependency+Code map | ⚠️ partial (this report §2) |
| 02 Core/Math/Units | ❌ absent |
| 03 Geometry engine | ❌ absent (orphaned CadGeometry is seed material) |
| 04 Materials+Sections | ❌ scattered constants; steel arrays only |
| 05 Project model | ❌ absent |
| 06 Load engine | ❌ absent (types collected but not modeled) |
| 07 Combinations | 🔴 broken/scattered (I1, I2) |
| 08 Analysis engine | 🟡 2D static only; no factoring/envelope/patterns |
| 09 Code engine | 🔴 wrong architecture — parallel families + mislabels (W1, I3) |
| 10 Beam | 🟡 core flexure/shear OK per code; torsion/dev/lap/curtailment/crack missing; doubly path wrong (W4) |
| 11 Column | 🔴 W5, W9; no real interaction for simple path |
| 12 Slab | 🟡 one-way OK; two-way fake DDM (I4); punching gaps (I5) |
| 13 Foundation | 🔴 W6; settlement absent |
| 14 Retaining wall | 🟡 basics OK; seismic/global stability missing |
| 15 Tank | 🟡 serviceability focus OK; φ error (I9) |
| 16 Stair | 🟡 OK basics; empirical coefficients uncited |
| 17 Steel | 🔴 mislabeled stack + shared LTB bug (W1, W2, I12) |
| 18 Seismic | 🟡 ELF only; label bugs (W7, I11) |
| 19 Optimization | 🟡 TrialRunManager is smoke-test, not optimizer; bar selection heuristic only |
| 20 Constructability | ❌ absent |
| 21 Rebar detailing | 🟡 shallow live model; superior package orphaned; bend deduction missing |
| 22 BBS | 🔴 broken (W11, W12) |
| 23 Cutting optimization | 🟡 good algorithm, wrong inputs |
| 24–25 Drawing model/CAD layout | ❌ decorative Canvas composables parameterized from results (not a DrawingDocument); no sheets/title blocks/borders standard |
| 26–27 DXF + validation | 🔴 fragmented ×8; no standard layers/Arabic/validation (except orphans) |
| 28–30 PDF | 🟡 working bilingual reports; 5 dead exporters; bitmaps not vectors; no calc-trace format |
| 31 BOQ | ❌ stub with hardcoded prices |
| 32 Site module | ❌ stub |
| 33 Revision+Audit | ❌ absent |
| 34 Security | 🟡 scaffold unwired |
| 35 DB migration | 🔴 destructive migration active |
| 36 Performance | ⚠️ coroutines used ad-hoc; no cache/dispatcher policy |
| 37 Regression tests | ❌ ~4% coverage, no benchmarks |
| 38 UI professionalization | 🟡 good screens, wrong IA for platform goal |
| 39–40 Package/Release | ❌ no GENERATE PROFESSIONAL PACKAGE pipeline |

---

## 6. Prioritized Remediation Roadmap (proposal)

**P0 — Safety stops (fix before new features):**
1. W5 column Pc unit error + caps/min-ecc · 2. W6 footing shear section · 3. W9 ECP 6%/4% cap · 4. W10 ψs inversion · 5. W11 BBS qty · 6. W7/W8 SBC seismic & pile factors (or hide SBC until fixed) · 7. Wire migrations / remove destructive fallback · 8. Fail-fast release signing.

**P1 — Consolidate (stop the duplication bleed):**
Delete 6 dead PDF exporters + 4-dead-DXF-writer chain (~7,700 lines) after harvesting; adopt `CalculatorCadExporterV7` + port orphaned `detailing/DxfWriter` auto-layer/QA tech; bind-or-delete dual repositories; route all ViewModels off `CalculatorEngine.kt` onto domain engines; single combination engine consumed by everything (I1, I2).

**P2 — Foundation build (enables everything else):**
core/math/units/tolerance → Materials/Sections engine → Project Model + relational schema (nodes/members/load_cases/results/checks) → CodeRuleEngine with per-clause rule objects replacing calculator families → Calculation Trace type (INPUT/FORMULA/RESULT/LIMIT/STATUS) rendered in UI/PDF.

**P3 — Complete elements per DoD lists (spec §81–85), one at a time**, starting Beam (torsion → dev/lap/curtailment → crack/deflection → optimization → detailing → BBS → elevation/sections → PDF/DXF → tests → mark COMPLETE).

---

*Report generated by PHASE 00 audit agents; all findings carry file:line evidence in agent transcripts. No production code was modified during this audit.*

---

## ADDENDUM A — ROUND-2 EQUATION-LEVEL DEEP DIVE (2026-08-25)

Method: four parallel deep-audit passes (RC beam/column/slab · foundations/piles/retaining/tanks/shear-wall/seismic · steel/frame/detailing/BOQ · PDF/DXF/data/CI/security), each with line-level derivation checks. Findings below are **additional** to §3; where they overlap, the round-2 evidence is sharper.

### A.1 NEW CRITICAL/WRONG defects (not in original tables)

| # | Defect | Location | Impact |
|---|---|---|---|
| A1 | **Frame solver equivalent-nodal-load sign error**: `getFixedEndForces()` returns −FEF, then assembly negates again (`F -= fefGlobal`); member recovery `f = k·u − fef`. Numeric trace of SS beam + UDL: displacements/reactions sign-flipped, end moments off by ±wL²/6, midspan moment 7wL²/24 vs true wL²/8 (shears coincidentally exact). | `FrameAnalysisEngine.kt:82-84 vs :162 vs :294-341` | Every frame result shown to users is invalid |
| A2 | **Steel modulus ×1000 in frame solver**: `settings.eSteel * 1e3` applied to a value already in kN/m² → E=2×10¹¹ kN/m²; steel members behave near-rigid in mixed frames | `FrameAnalysisEngine.kt:437` | Invalid mixed steel-concrete frames |
| A3 | **Circular tank crack check off ~1000×**: hoop stress computed as kN/m ÷ m = **kPa**, compared to `fct=0.6√fcu` **MPa** — check inert in all three code variants | `ECPTank.kt:330-332`, `ACITank.kt:303-305`, `SBCTank.kt:272-274` | Watertightness assurance illusory for circular tanks |
| A4 | **Retaining wall water-table physics broken**: soil below WT contributes nothing (`gammaSub` computed then discarded); hydrostatic overturning arm adds `hSoil` to centroid arm (double-count). Copied identically into ACI variant | `ECPRetainingWall.kt:43-54`, `ACIRetainingWall.kt:41-51` | Unconservative overturning demand under high GWT |
| A5 | **Tank uplift modeled on internal water** `U = L·B·H·γw`; external groundwater not an input — governing case (empty tank, high GWT) unreachable | `ECPTank.kt:109-117`, `ACITank.kt:102-111`, `SBCTank.kt:97-107` | Flotation failures possible with PASS verdict |
| A6 | **Pile group capacity disconnected from soil**: ACI/SBC hardcode single-pile 500 kN anchor; ECP invents reference pile (cu=50, φ=30) regardless of user soil | `ACIPileFoundation.kt:481`, `SBCPileFoundation.kt:487`, `ECPPileFoundation.kt:690-702` | Headline group-capacity result wrong by multiples |
| A7 | **Pile-head deflection silently capped at the allowable**: `.coerceAtMost(25.0)` — lateral serviceability check can never fail | `ECPPileFoundation.kt:777-780`, `ACIPileFoundation.kt:536`, `SBCPileFoundation.kt:546` | Failure mode erased rather than flagged |
| A8 | **Pile settlement formulas dimensionally invalid** (two different styles, neither resolves to mm); consolidation `Cc=0.009(cu−25)` goes negative for cu<25 with no clamp | `ECPPileFoundation.kt:631-636`, `ACIPileFoundation.kt:448`, `SBCPileFoundation.kt:451` | Fabricated settlement numbers |
| A9 | **Shear wall neutral-axis iteration exits after first pass**: convergence test `abs(c − newA/beta1) < 0.1` evaluated immediately after `c = newA/beta1` → always 0. Block depth additionally clamped to tension-controlled limit inside loop; capacity-design `checkOverstrength()` defined but never called (all three variants) | `ECPShearWall.kt:673-680,278-280,913-917` + ACI/SBC copies | Wall capacity overstated for compression-controlled sections; seismic capacity design not enforced |
| A10 | **Eurocode-2 minimum-steel pair mislabeled as ECP** in ≥5 files: `max(0.26√fcu/fy, 0.0013)` is EC2 (fctm/fyk form); real ECP pair is ACI-form (0.25√fcu/fy, 1.4/fy) | `ECPBeam.kt:287`, `ECPSlab.kt:64`, `BeamDesignEngine.kt:437`, `ECPDoublyReinforcedBeam.kt:161`, `ECPAdvancedBeam.kt:270,451` | Wrong code basis for the most-applied limit |
| A11 | **Two-way slab moments ×1000**: ECP K-method receives span in mm, divides mm² by 1e3 instead of 1e6 | `ECPSlab.kt:134,141` + `ECPAdvancedSlab.kt:362` | Garbage Mu on two-way path |
| A12 | **Flat-slab flexure lever-arm divisor 1.25 vs 0.893** used everywhere else in the same K-method family → flat slabs systematically under-reinforced relative to beams | `ECPFlatSlab.kt:609` vs `ECPBeam.kt:61` | Under-design on live DDM path |
| A13 | **Enhanced biaxial column check is dead code via overload resolution**: genuine Bresler reciprocal + load-contour exists but call site binds to crude 5-arg overload assuming As=1%Ag | `ECPAdvancedColumn.kt:169-171` vs `:268-318` vs `:916-1020` | Best column math unreachable |
| A14 | **Four conflicting ECP development-length implementations** (bond coeff 0.3 vs 0.6√fcu; prefactor ¼ vs ½) all citing "§5-2"; combined spread ≈4× between shortest and longest Ld | `ECPBeam.kt:258-284`, `BeamDesignEngine_Part2.kt:373-381`, `ECPDoublyReinforcedBeam.kt:475-479`, `ECPAdvancedBeam.kt:1351-1357` | Anchorage lengths implementation-dependent |
| A15 | **AISC angle shear-lag U inverted** vs Table D3.1 Case 5 (code: ≥4 bolts→0.60 else 0.80; spec: ≥4→0.80, 3→0.60): 2–3-bolt angles overcapacity up to +33% | `AISCSteelDesignEngine.kt:771-775` | Unsafe tension rupture capacity |
| A16 | **Composite stud Qn carries phantom `(Es/Fu)`≈444 multiplier**, collapsing effective Qn to bare As·Fu cap | `AISCSteelDesignEngine.kt:1870-1873` | Composite capacities wrong |
| A17 | **AISC deflection check fabricated**: service load back-derived from factored Mu (8Mu/L²), span:=Lb, dimensionally broken `E/1e6` inside 5wL⁴/384EI | `AISCSteelDesignEngine.kt:1136-1148` | Serviceability numbers meaningless |
| A18 | **Three incompatible ECP slenderness regimes**: λ=KHo/t limits 15/30 vs λ=KL/(t/√12) same limit (3.46× discrepancy) vs L/r + ad-hoc capacity×0.8 | `ColumnDesignEngine.kt:101-109`, `ECPAdvancedColumn.kt:108-114`, `CalculatorEngine.kt:687-697` | Classification implementation-dependent |
| A19 | **Main UI runs the least-conformant math**: Compose screens bypass CalculationFactory and call god-engine whose ECP axial formula omits material partial factors entirely (0.35fcu·Ag + 0.67fy·Ast) and invents P_equiv = Pu+Mx(8/b)+My(8/h) | `CalculatorEngine.kt:648,694,716-725` reached from `BeamScreen.kt:346`, `ColumnScreen.kt:37`, etc. | Shipping product may use worst available formulas |
| A20 | **Torsion designed twice with different theories** (At/s = Tu/(2Ao·fs·sinθ) vs Tu/(1.5Ao·fs·cotθ)) and threshold depth d vs h | `BeamDesignEngine_Part2.kt:167-233` vs `ECPAdvancedBeam.kt:1175-1313` | Same clause, two answers |

### A.2 Additional HIGH findings

- **CI never triggers automatically** — `android.yml` is `workflow_dispatch:` only despite header claiming push/PR triggers; lint non-blocking (`continue-on-error`). Regressions ship undetected. (`.github/workflows/android.yml`)
- **Production PDFs are English-only**: all ~12 design ViewModels route through `ProfessionalEnglishPdfReporter.generateReportLegacy` while four purpose-built bilingual exporters sit dead (FlatSlab/ShearWall/PileFoundation/SteelEnglish). (utils/exporters)
- **Fabricated numbers shipped**: BBS bar lengths fixed at 5000/3000 mm placeholders (`BbsGenerator.kt:34,68`); BOQ drop volume hardcoded 2×2×0.10 m (`CalculateElementBoq.kt:124`); stair "deflection" synthesized from span/depth ratio, not computed (`ECPStaircase.kt:235-239`); PT slab returns literal `isSafe=true` (`ECPAdvancedSlab.kt:228-229`).
- **Seismic**: ASCE Cs,max evaluated at fixed T=0.1s so it never governs; SD1:=SDS/2 arbitrary; 0.5·S1 floor missing; two conflicting site-factor maps in one file; OTM semantics differ per code variant. No drift/torsion/irregularity/P-Δ anywhere. (`ACISeismic.kt:48-56,27-33,168-174`)
- **Combined footing mis-modeled** in ACI/SBC: simply-supported qu(L/2)²/8 for longitudinal steel, punching checked at column 1 only; meanwhile a *second, different* ECP combined-footing engine coexists (`ECPCombinedFooting.kt`) whose min-steel drops √fcu (`:553`) contradicting its own corrected sibling (`:416`).
- **ECP shear-formula split within one code family**: `0.24√(fcu/γc)` (footing/pile/retaining) vs `0.24√fcu/γc` (`ECPCombinedFooting.kt:268`) — ≈22% capacity swing depending on file.
- **Silent understrength paths**: `ColumnDesignEngine.calculateBarSelection` caps bar count to fit section and returns reduced area as "provided" with no failure flag (`:357-366`); unknown steel section falls back silently to IPE 300 (`FrameAnalysisEngine.kt:454-460`).
- **Security layer inert**: CryptoUtils (AES-GCM) has zero call sites; integrity checker fingerprint empty string; no Keystore anywhere.
- **DB destructive migration active in two unwired builders; Migrations.kt dead; schemas not exported** (stale 7.json at v8) — next schema bump wipes all user data.

### A.3 Sharper architecture finding

There are **four** parallel RC implementation stacks, not two:
1. Root god-engines `BeamDesignEngine(+Part2)/ColumnDesignEngine` — orphaned, no ViewModel caller
2. Strategy classes `{ECP,ACI,SBC}{Beam,Column,Slab}` — reachable only via ColumnComposeViewModel/FlatSlabViewModel; aggregate-only results (no step trace)
3. `*Advanced*` variants — factory methods exist, **zero production callers** (newest, partly best math, all dead)
4. `utils/CalculatorEngine.kt` (2,019 ln) — what the main UI actually uses, and the least code-conformant of the four (A19)

All `getAdvanced*Design`, `getDoublyReinforcedBeamDesign`, Hordi/Waffle factory methods are production-dead. The only advanced-tier code wired to UI is FlatSlab DDM (with defect A12).

### A.4 Test-suite verdict (round-2)

Zero independent numeric verification exists. `ECPBeamTest`: 100% smoke. `ECPColumnTest:22-30`: tautological — expected value recomputed with the implementation's own formula, **locking in the φ·α double-count** (W5). `CalculationFactoryTest`: plumbing only. `BeamCantileverParityTest`: relational smoke, no absolute benchmark. Combined with aggregate-only `isSafe` on reachable strategy classes, no mechanism exists today to detect any defect in §3 or Addendum A.

### A.5 Revised P0 list (merges §6 P0 with round-2)

**Tier 0 — results-invalidating bugs (fix behind golden-number tests, in order):**
1. A1+A2 frame solver sign error + E_steel ×1000 (add SS-beam/portal analytic parity tests first)
2. A11 two-way slab ×1000 · A3 tank crack units · A12 flat-slab divisor
3. A4 retaining GWT physics · A5 tank uplift model
4. A13 wire the real Bresler/load-contour biaxial path; then W5/A18/A19 column formula unification (kill CalculatorEngine column math)
5. A6+A7 pile group anchor & deflection cap · A8 settlement units
6. A9 shear-wall iteration break + enable overstrength call
7. W6 footing shear section · W10 ψs inversion · A14 dev-length unification · A10 EC2→ECP min steel correction
8. W11/W12 BBS quantity + Master BBS wiring · A16/A15/A17 AISC fixes

**Tier 1 — delivery stops:** wire migrations/export schemas/remove destructive fallback · fix CI triggers (push+PR) · route Arabic users off English-only reporter (or delete dead bilingual exporters) · fail-fast release signing.

**Tier 2 — consolidation (unchanged from §6 P1/P2):** delete dead exporters/DXF chain after harvest; route all ViewModels onto domain engines; single combination engine; then core/math/units kernel → CodeRuleEngine → Calculation Trace type.

### A.6 STEP 1 STATUS — Golden-number regression suite (2026-08-25)

Created `app/src/test/java/com/civileg/app/engineering/`:
- **`BeamGoldenBenchmarkTest`** (11 tests): hand-derived ECP K-method flexure/shear/dev-length and ACI Rn–ρ flexure/shear/dev-length benchmarks, min-steel governing cases, over-reinforced flagging, cross-code divergence guard. All values computed independently of the implementation. **All pass** — confirms the audited beam equations reproduce their documented formulas exactly.
- **`FrameSolverParityTest`** (7 tests): cantilever tip-load closed form (δ=PL³/3EI, θ=PL²/2EI, reactions), pure axial bar (PL/EA), SS-beam UDL & midspan point load (reactions ±wL/2, M=wL²/8, PL/4), continuous two-span three-moment classics (M_B=−wL²/8, R=3wL/8·10wL/8·3wL/8), single-span identity.

Defect lifecycle during STEP 1:
| Defect | Status |
|---|---|
| A1 frame FEF sign | **FIXED & VERIFIED** — assembly now adds the P_eq vector (`F += fefGlobal`); recovery treats table output as pure equivalent nodal loads; parity tests green with textbook values |
| CBA-1 continuous-beam reaction reconstruction sign | **FIXED & VERIFIED** — `vLeft=(wL/2)+(mRight−mLeft)/l`; two-span case returns classic 22.5 / 75 / 22.5 kN |
| D-1 diagram closure at J-end (new finding, found by cantilever parity test: old `M=mI+vI·x` gave 2·PL at a free tip) | **FIXED & VERIFIED** — diagram now interpolates `M(x)=mI(1−t)+mJ·t+M_free(x)` satisfying both boundary conditions |
| A11 two-way slab moments/shears ×1000 | **FIXED & VERIFIED (STEP 2)** — golden test `TwoWaySlabUnitRegressionTest` proved pre-fix As=250,584 mm²/m & util=159.5 vs hand-derived 808.9 mm²/m & 0.219; `ECPSlab.designTwoWaySlab` now converts spans mm→m for Mu (`coeff·w·lx²`) and shear (`w·lx/2`), matching the ACI/SBC convention. Note: the defect also inflated SHEAR (`totalLoad*shortSpan/2`), not only moments as originally reported. Full suite 121/0/0 after fix |
| A3 tank crack units | **FIXED & VERIFIED** (parallel session) — all three code variants now compute σ=T/t in MPa; locked by `Tier0SlabTankGoldenTest` incl. explicit FAIL cases |
| A12 flat-slab lever-arm divisor | **FIXED & VERIFIED** (parallel session) — 0.893 family constant restored in `ECPFlatSlab`; locked by golden test |
| A4 retaining-wall water-table physics | **FIXED & VERIFIED (STEP 2)** — layered Rankine model (dry triangle @ hw+zw/3, submerged rectangle @ hw/2, buoyant triangle @ hw/3, separate hydrostatic triangle) replaces the old form that ignored γ′ and double-counted hSoil. Golden gate `RetainingWallWaterTableTest`: deep-GWT case OT_FS 3.920→3.351, sliding FS 1.748→1.429 vs hand-derived 3.351/1.429; dry case invariant locked. Applied identically to ECP and ACI variants |
| A5 tank uplift on internal water | **FIXED & VERIFIED** (parallel session) — locked by `Tier0RetainingUpliftGoldenTest` (7 tests, green) |
| A6 pile-group capacity anchor · A7 deflection cap · W11/W12 BBS wiring | **FIXED & VERIFIED** (parallel session impls; STEP-2 session completed the missing base-package contract resolution and got `Tier0ColumnPileGoldenTest`, `BbsQuantityGoldenTest` compiling/green) |
| A13 biaxial Bresler/load-contour wiring & interaction-diagram math | **FIXED & VERIFIED (STEP 2)** — three-layer fix: ① overload-resolution wiring to the genuine contour path (`fcu,fy` args); ② edge semantics: demand outside the diagram envelope now fails loudly (99.0 sentinel / breslerSafe=false) instead of silently safe; ③ root-cause math repair in `generateInteractionDiagram`: steel moment sign flipped back to sagging-positive `(h/2−di)` AND the omitted concrete-block moment `(h/2−a/2)` restored (hand-check at c=150mm for 400×600/fcu30/ρ1%: Mn=263 kN·m ✓). Locked by `Tier0ColumnPileGoldenTest` + `ColumnBiaxialBenchmarkTest` + `ColumnInteractionDiagramTest` |
| Toolchain note | `ProjectViewModel.getProjectBbs` migrated off removed `androidx.lifecycle.Transformations` → `MediatorLiveData` (W12 follow-through) |

Full suite at close of STEP 1: **102 tests / 0 failures / 0 skipped**.
Note: fixes were applied by a parallel working session between audit runs; the golden tests above are now the permanent regression gate guarding them (spec §78 Regression Policy).

Next per §A.7 execution order: remaining Tier-0 defects (A11 slab ×1000, A3 tank units, A12 flat-slab divisor, A4/A5 retaining+uplift physics, A13 biaxial wiring, A6–A9 pile/wall cluster, W6/W10/A14/A10 beam-family unification) each follow the same cycle: hand-derived golden test → fix → green → regression.

### A.8 STEP 3 STATUS — Core kernel seed (PHASE 02–03, 2026-08-26)

`com.civileg.core.math` created inside `:core:calculations` (pure JVM, additive-only):
**Tolerance** (rel+abs comparisons) · **SafeMath** (guarded div/sqrt/ratio, loud failures) · **RootFinding** (bracketed bisection, safeguarded Newton, no-extrapolation interpolation) · **Units** (typed value classes mm/N/N·mm/MPa/mm² with explicit conversions & cross-dimension operators) · **Geometry2D** (PolygonSection shoelace properties incl. centroidal Ix/Iy/Ixy; Rectangle/Circle closed forms).

Gate: 14 hand-derived golden tests in `core/calculations/src/test` — **14/14 green**.
Two defects caught by the gate during bring-up (documented in worklog): member-function shadowing of `kotlin.math.sqrt` inside `SafeMath` (infinite recursion) and a tolerance-scaling expectation error — both resolved.

Next per §A.7: STEP 4 (materials/sections/project model/load+combination engine) building on this kernel, or Beam DoD cycle (§81) — decision pending product priority.

### A.9 STEP 4 STATUS — Materials, combinations & trace layer (PHASE 04/06/07/15-seed, 2026-08-26)

`com.civileg.core.engineering` created in `:core:calculations`:
**Materials** (concrete/steel/rebar table; Ec·fct·β₁ per code family; single-source partial factors) · **Loads** (ECP 203 §2-3-1-1 + ACI 318-19 §5.3.1 gravity sets with citations, envelope selection, dead-reducing flag) · **CalculationTrace** (INPUT→FORMULA→SUBSTITUTION→RESULT→LIMIT→UTILIZATION→STATUS with PASS/WARNING/FAIL/**NOT_CHECKED** distinct, spec §15/§62) · **EcpBeamChecks** (pilot One-Source-of-Truth beam flexure+shear emitting full traces).

Gate: **24/24 core golden tests green** (math 9 · geometry 5 · engineering 10), including E1/S1 cross-validation against the STEP-1 legacy-engine benchmarks through an independent implementation.

Semantic decision recorded: the kernel implements the **corrected post-A10** ECP minimum-steel pair (0.25√fcu/fy → 434.03 mm² on the benchmark section); legacy engines stay pinned to pre-A10 characterization values until consolidation migrates them onto kernel constants.

Next per §A.7: STEP 5 CodeRuleEngine parameterization → stack consolidation (Beam DoD §81 first).

### A.10 SESSION 2 STATUS — CodeRuleEngine + unified flexure (PHASE 09, STEP 5, 2026-08-26)

`CodeParameters.kt`: `ConcreteCodeParams` interface — one source per family for block stress, β₁, Ec, min/max steel formulas, φ-vs-γ policy, K_bal, and **bar menu** (metric vs US soft-metric; the menu divergence was caught by the golden gate as 6Ø20-vs-6Ø19 and resolved by parameterization).

`UnifiedBeamFlexure.kt`: ONE skeleton (validation → injected family solve → shared minimum gate → trace → bar selection). Over-reinforced demands route loudly to the doubly path. Traces carry the governing combination citation end-to-end (pipeline test: envelope → engine).

Gate: **31/31 core tests green** across four suites. Both legacy benchmark sets (ECP E1 · ACI A1) now reproduce through the single engine — the consolidation drift-gate for migrating the remaining checks and the four historical stacks.

Next: extend the pattern (shear → deflection → torsion), then route ViewModels onto unified services and retire `CalculatorEngine` paths (STEP 6).

### A.11 SESSION 3 STATUS — Unified shear (STEP 5 extension, 2026-08-26)

`ConcreteCodeParams` extended with the full shear rulebook (capacity, absolute cap, minimum steel, spacing tiers, stirrup-diameter policy — each differing per family and cited). `UnifiedBeamShear.kt` consolidates the check with one skeleton.

Gate: **36/36 core tests green** (five suites). Both legacy shear benchmarks reproduce through the single engine: ECP S1 (Vc=122.47 kN, min 375 mm²/m governs, Ø10@200, util 0.42) · ACI A2 (φVc=101.19, Av/s=463.4, Ø10 @ d/2 cap 270, util 0.3643).

Semantic decision recorded: **WARNING ⇔ demand-driven design** (Vu > Vc/φVc); prescriptive minimum stirrups below concrete capacity are code defaults reported PASS-with-note — uniform across families, replacing the legacy habit of warning on routine conditions (spec §62 discipline).

Remaining before ViewModel routing (STEP 6): deflection + torsion on this pattern.

### A.12 SESSION 4 STATUS — Unified deflection + ACI de-contamination (STEP 5 extension, 2026-08-26)

Deflection rules authored into `ConcreteCodeParams` (replacing neutral placeholders left by the parallel session): ECP MF = 0.55+477/(fy·ρ%) with actual fy (fixes legacy hardcode M2); ACI footnote MF = 0.4+fy/700; **ACI basic table corrected from contaminated ECP values (20/28/8) to Table 24.2.2's own (16/21/8)** — live instance of the cross-code contamination class flagged in §3 (I6).

`UnifiedBeamDeflection.kt`: two layers — span/depth screening, plus a computed-deflection layer reported **NOT_CHECKED** when no service-load analysis exists and upgraded honestly when supplied. Overall status ordering FAIL > WARNING > NOT_CHECKED > PASS demonstrated by test.

Gate: **40/40 core tests green** across six golden suites.

Next: torsion + crack control on the pattern; then STEP 6 ViewModel routing / `CalculatorEngine` retirement.

### A.7 Execution order recommendation (spec §87 alignment)

Per the spec's "complete the page then move" rule, the sequence is:

```
STEP 1  Golden-number regression suite for existing engines (spec §76-78)   ← before ANY refactor
STEP 2  Tier-0 bug fixes above (each: fix + test + regression green)
STEP 3  PHASE 02-03: core/math/units/tolerance + geometry kernel
STEP 4  PHASE 04-07: materials/sections/project-model/load+combination engine
STEP 5  PHASE 09: CodeRuleEngine (parameterized rules; codes inject coefficients)
STEP 6  Consolidate stacks 1-4 into ONE engine per element behind CalculationFactory
        (Beam first, full DoD §81 cycle end-to-end incl. detailing/BBS/DXF/PDF/tests)
        then Column (§82) → Slab (§83) → Foundation (§84)
STEP 7  Remaining phases per §87 order
```
