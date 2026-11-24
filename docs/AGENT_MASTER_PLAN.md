# CivilEG2 — Agent Master Prompt & Development Plan

**Version:** 2026-11-15 · **Baseline:** commit `7129fd9` (2026-11-09, "prepare public release…")
**Audit method:** static inspection only (file tree, grep, line counts) + web verification of Play/toolchain requirements. **Nothing in this audit was compiled or run.** Every number below is reproducible with the one-liners in Appendix A. Name-based heuristics are flagged as such.

---

## 0. How to use this file

1. Save as `docs/AGENT_MASTER_PLAN.md`. Add one line to `AGENTS.md`: *"Before any task, read docs/AGENT_MASTER_PLAN.md and obey §1 and §9."*
2. Start the agent (Claude Code, repo root) with:
   > Read `docs/AGENT_MASTER_PLAN.md`. Execute **Batch 0.1 only**. Follow §9. Stop at the review gate and wait for my "go".
3. §1 (rules) and §9 (batch loop) are the binding prompt. §2–§8 are ground truth, backlog, standards and tooling. §11 lists decisions only the owner can make.

---

## 1. Role and non-negotiable rules

You are a senior structural-engineering software engineer working on **CivilEG2 / "Civil Engineer Pro"**: an Android app (Kotlin, Jetpack Compose, Hilt, Room) that designs RC and steel elements to **ECP, ACI 318-19 and SBC 304**, and produces screens, drawings, PDF reports, DXF files and BOQ/BBS.

Wrong numbers can end up in real buildings. Correctness and traceability outrank feature count and speed.

| # | Rule |
|---|------|
| **R1 Single source of truth** | One engine call → one result object → screen, drawing, PDF, DXF, BOQ all read it. UI and exporters never recompute, re-round, or re-derive a design value. |
| **R2 Three-code parity** | A feature added for one code is added for ECP, ACI and SBC **in the same batch**, or the gap is written into the parity matrix (§2.4) with a reason. |
| **R3 Material conventions** | ECP uses `fcu` (cube). ACI uses `fc'`. SBC engines in this repo convert internally (e.g. `fc' = 0.8·fcu` in `SBCAdvancedColumn`). **Reuse each file's existing conversion; never add a second one.** Units: mm, N, MPa, kN, kN·m unless the file already uses otherwise. |
| **R4 Research before formula** | Before writing any new equation, open a real source (official code text, university course notes quoting the clause, a worked textbook example) and record clause number + source + date in `docs/CLAUSE_LOG.md`. Cite in code: `// ACI 318-19 §22.7.4.1 (verified 2026-11-15, <source>)`. Cannot verify → `// UNVERIFIED-CLAUSE` + log entry + say so in the batch report. Never present a remembered clause number as verified. ECP 203/201 are hard to verify online: ask the owner for the clause text rather than guessing. |
| **R5 English-only exports** | PDF and DXF content is English only, regardless of UI language (Arabic shaping/encoding is unreliable there). The UI stays bilingual AR/EN with RTL. |
| **R6 Tests are the spec** | Every new/changed engine function ships with ≥1 **golden vector** (hand calculation from a cited example), boundary cases, an input-guard test, and a cross-code divergence test. Tolerance: 1 % for areas/moments/forces; exact for bar counts and spacings. No batch is "done" on a red or skipped test. |
| **R7 Scope discipline** | One element per batch (§9). Do not touch unrelated files. Do not "clean up" while adding features. Refactors get their own batch. |
| **R8 Free-only release** | No ads, no billing, no paid features active in the release the owner ships first. Do not add monetization code. |
| **R9 Honesty about verification** | State exactly what you ran (`./gradlew …` output) and what you did not (e.g. emulator not available). Never write "builds" / "tests pass" without having run them. Code pasted in earlier chat sessions is **reference only and was never compiled** — adapt to the real signatures, don't paste. |
| **R10 No silent behavior change** | If a fix changes an existing result, say which test/value changed and why, with the code clause. |
| **R11 Secrets and licensing** | Never commit keystores, passwords, real AdMob IDs, or copyrighted code books/PDFs. Keep code documents local (gitignored `docs/codes/`). |
| **R12 Stop conditions** | See §9.3. When in doubt, stop and ask; a question is cheaper than a wrong design equation. |

---

## 2. Ground truth (measured 2026-11-15)

### 2.1 Size and shape

| Metric | Value |
|---|---|
| Kotlin files / lines (all `app/`) | **520 / 182,195** |
| `src/main` / `src/test` / `src/androidTest` files | 422 / **96** / **2** |
| Test assertions (grep `assert*(`) | 2,941 |
| Lines by package (main) | `ui` 60,556 · `domain` 56,296 · `utils` 24,154 · `viewmodel` 11,092 · `db` 2,107 · `views` 1,936 · `security` 499 · `pricing` 497 · `data` 482 · `ads` 436 · `billing` 250 |
| Second Gradle module `:core:calculations` | 32 files, **3,486 lines**, imported by only 9 app files |
| Screens | ~43 screen files in `ui/compose/screens` |
| Strings | `values` 1,732 = `values-ar` 1,732 (parity OK) |
| Largest files | `PdfDrawingGenerator` 3,757 · `QualityEngineerScreen` 3,547 · `SteelDesignScreen` 3,136 · `CalculatorEngine` 2,809 · `AISCSteelDesignEngine` 2,114 |
| Toolchain | AGP 9.5.0 · Kotlin 2.2.20 · compileSdk/targetSdk **36** · minSdk 26 · Compose BOM **2025.06.01** · Room 2.7.1 · Hilt 2.56.1 · iText 8.0.5 · Billing 7.1.1 · versionName 1.7.0 (code 7) |

### 2.2 Corrections to earlier assumptions (the repo moved on)

- **Strap footing now exists for all three codes** (`ECPStrapFooting`, `ACIStrapFooting`, `SBCStrapFooting`, `base/StrapFootingDesign`, plus `StrapFootingParityTest` and `StrapFootingAdvancedTest`). **Settlement** exists (`SettlementAnalysis`, `SettlementAnalysisTest`). Earlier chat versions of `ECPStrapFooting.kt` / `StrapFootingDesign.kt` are **superseded — do not paste them.**
- **Wind load exists** (`utils/WindLoadCalculator.kt`, 609 lines, `WindLoadScreen`, `WindLoadViewModel`) but it is **code-agnostic ("IS 875-3 / ASCE 7 style") and lives in `utils/`**, not selectable per ECP/ACI/SBC. Plus `SteelWindEngine` (in `aci/`, untested).
- **Still open (verified by function-name grep):** concrete-beam **torsion for ACI and SBC**; **strong-column/weak-beam for ECP and ACI**; **seismic column confinement for ECP and ACI**; no dedicated **lap-splice (Class A/B) design function** in any code. The earlier torsion/ductile-detailing snippets were never integrated.

### 2.3 Release/compliance facts (verified from official pages, 2026-11-15)

- **Target API:** new apps and updates must target **API 36** by 2026-08-31 (extension to 2026-11-01 — now past). The app targets 36 → **compliant**. Existing apps below API 35 stay available to new users only on devices at or below their target API.
- **Play Billing Library:** updates must use **version 8 or later** by 2026-08-31 (extension to 2026-11-01); 9 is the newest. The app uses **7.1.1 and declares `com.android.vending.BILLING` → non-compliant for any update that ships billing.**
- **Compose 1.12 (BOM 2026.08.00)** raises `compileSdk` to 37 and needs AGP ≥ 9.1.1. The repo's BOM (2025.06.01) is >1 year old; AGP 9.5.0 is listed as an October 2026 release (search-result title only, page not opened).

### 2.4 Cross-code parity matrix (file-name based — verify before relying)

| Element | ECP | ACI | SBC | Notes |
|---|:-:|:-:|:-:|---|
| Beam (basic / advanced / doubly) | ✔ | ✔ | ✔ | **Advanced engines have no test referencing them** |
| Column (basic / advanced) | ✔ | ✔ | ✔ | Advanced untested |
| Solid slab / advanced slab | ✔ | ✔ | ✔ | Advanced untested |
| Flat slab · Waffle · Hordi/Joist | ✔ | ✔ | ✔ | `ACIJoistSlab`, `SBCJoistSlab`, `*Hordi*` untested |
| Isolated footing | ✔ | ✔ | ✔ | |
| Combined footing | `ECPCombinedFooting` | inside `ACIFooting`? | inside `SBCFooting`? | **verify** |
| Strap footing | ✔ | ✔ | ✔ | tested |
| Pile foundation / pile group | ✔ | ✔ | ✔ | |
| Retaining wall · Tank · Staircase | ✔ | ✔ | ✔ | |
| Shear wall · Wall opening | ✔ | ✔ | ✔ | `ACIShearWall`, `SBCShearWall` untested |
| Post-tensioned | ✔ | ✔ | ✔ | `PostTensionedDesign` untested |
| Seismic · Soil-structure | ✔ | ✔ | ✔ | |
| Steel member | `SteelDesignEngine` | `AISCSteelDesignEngine` | `SBCSteelDesignEngine` | |
| Steel connections / base plate | `SteelConnectionDesign`, `SteelBasePlateDesign` (ecp only) | AISC adapter | — | connection untested |
| Wind | generic util only | `SteelWindEngine` | (in SBC steel) | see 2.2 |
| Deep beam · Composite beam · Fire · Settlement | single, not obviously code-aware | | | **verify** |

Engines with **no direct test reference** (name heuristic): `ACI/ECP/SBC AdvancedBeam·AdvancedColumn·AdvancedSlab`, `ACIJoistSlab`, `SBCJoistSlab`, `ECPHordiSlabDesign`, `SBCHordiSlabDesign`, `ACIShearWall`, `SBCShearWall`, `PostTensionedDesign`, `SteelConnectionDesign`, `SteelWindEngine`, `InteractionDiagram`, `TrialRunManager`, `BeamDesignEngine_Part2`, `BuildingLoadDistributionEngine`. Confirm with a coverage report (Batch 1.2).

### 2.5 Output layer facts

- **Three independent drawing implementations per element**: Compose (`ui/compose/components/drawings/*`, ~24 files), PDF (`utils/PdfDrawingGenerator.kt`, 3,757 lines), DXF (`utils/ElementDxfDrawings.kt`, 13 element functions + `FrameDxfBuilder`). No shared scene model exists (grep for DrawingScene/primitive types: none). `DrawingSummaryFactory` only maps check lists into the drawing header.
- **DXF writer** (`DxfExporter`, 870 lines): AC1027, mm units, layer table, LWPOLYLINE/TEXT/MTEXT/HATCH/CIRCLE/ARC, dimensions, sheet frame, rebar dots, stirrups with hooks, leader callouts, BBS table, site layout. Strong base. **Elements with a Compose drawing but no `ElementDxfDrawings` function:** strap footing, flat slab, waffle, hordi, wall opening, post-tensioned, seismic, soil-structure, shear wall (verify other DXF paths first).
- **English-only violations in PDF code (lines containing Arabic, code not comments):** `PdfGenerator.kt` 46 · `PdfDrawingGenerator.kt` 70 (bilingual drawing titles like `"FRONT ELEVATION | مسقط رأسي"`) · `FrameAnalysisPdfExporter.kt` 8. `ProfessionalEnglishPdfReporter.kt` is clean (1 Arabic map key used for lookup). DXF exporters are clean; `FrameDxfBuilder` transliterates Arabic→Latin (acceptable, keep).
- **At least five PDF paths:** `PdfGenerator`, `PdfDrawingGenerator`, `PdfExportHelper`, `ProfessionalEnglishPdfReporter`, `FrameAnalysisPdfExporter` (+ `SteelWarehouseProPdfExporter`, `PostTensionedExporter`).

### 2.6 Repo hygiene facts

- A **Next.js admin panel lives inside the Android repo**, even inside the `app/` folder: `app/admin/*`, `app/auth/signin/page.tsx`, `app/api/auth/[...nextauth]`, `Caddyfile`, `components.json`, `tsconfig.tsbuildinfo`, `.env.example` (NextAuth/DB), `.WORKFLOW_SUMMARY.md`. The sign-in page **prints demo credentials `admin@example.com / admin123`** and the summary file repeats them. `AGENTS.md` contains a Next.js block that misleads Android agents.
- Tracked: `app/google-services.json`, `_agent_screenshot.png`, `tsconfig.tsbuildinfo`. (Firebase config is not a secret, but restrict the API key to package + SHA-1.)
- `PREMIUM_GEOMETRIC_ENHANCEMENT_SUMMARY.md` is an agent status note, not documentation.
- **Dead code:** the 8 classes in `views/` (1,936 lines) have **zero references**; also check `ui/{beam,column,slab,stairs,watertank,custom}/*View.kt` and `adapter/` (RecyclerView/Glide).
- **Two ad managers** (`ads/AdManager.kt`, `utils/AdsManager.kt`); `MobileAds.initialize` runs unconditionally; **no product flavors, no kill switch**; AdMob defaults to Google *test* IDs.
- Manifest: `BILLING` permission; `FOREGROUND_SERVICE` declared with **no `<service>` in the app's own manifest** (looks unused; Play may ask for a foreground-service declaration); `READ_MEDIA_IMAGES` (Play photo/video permission policy — prefer the system Photo Picker); a stale "prevent screen capture" comment sits above `FOREGROUND_SERVICE`.
- Room `AppDatabase`: version 13, `exportSchema = true`, migrations 6→13 exist, **but `fallbackToDestructiveMigration(true)` is on** — a missed migration silently deletes users' saved projects.
- CI: `lintDebug` is non-blocking (`continue-on-error`), `./gradlew test` runs, no coverage gate, no DXF/PDF validation, no static analysis.
- AGENTS.md emulator workflow is **PowerShell/Windows only**.

---

## 3. Prioritised backlog

| ID | Pri | Finding | Action | Acceptance |
|---|:-:|---|---|---|
| H1 | P0 | Admin panel + demo credentials in public Android repo | Move web panel to its own repo or delete; remove Caddyfile/.env.example/components.json/tsbuildinfo/.WORKFLOW_SUMMARY; strip Next.js block from AGENTS.md; if it was ever deployed, rotate | `git ls-files` shows no `.tsx/.ts/Caddyfile`; no `admin123` anywhere |
| H2 | P0 | Billing 7.1.1 + ads in a free-only release | Product flavor `free` with **no** BILLING permission, no billing/ads deps, no AdMob meta-data, no UMP; keep `pro` flavor dormant; merge the two ad managers | `free` release manifest (merged) has no BILLING/AD_ID/ads meta-data; `bundleRelease` ok |
| H3 | P0 | Arabic in PDF code paths | Replace with English strings; consolidate to one PDF path (§6.4) | Guard script (Skill 7) passes; `pdftotext` of every sample report has zero Arabic glyphs |
| H4 | P0 | Destructive Room fallback | Restrict to `fallbackToDestructiveMigrationFrom(<versions < 6>)`; add migration tests for every step 6→13 | `MigrationTestHelper` tests green |
| H5 | P0 | Unused/risky permissions | Remove `FOREGROUND_SERVICE`; replace `READ_MEDIA_IMAGES` with Photo Picker; fix stale comment | merged manifest diff reviewed |
| T1 | P1 | Advanced engines untested (~8k lines) | Golden-vector tests per §9 for each | coverage ≥ 70 % on `domain/calculations` (jacoco) |
| T2 | P1 | `:core:calculations` partially migrated, duplicates `ECPColumn/ECPBeam/ECPFooting` | Owner decision D1; then finish or revert; add duplicate-engine guard test | exactly one implementation per engine |
| C1 | P1 | Torsion ACI/SBC; SCWB ECP/ACI; confinement ECP/ACI; lap splice all codes | §5 catalogue, one element per batch | parity matrix cells flip to ✔ with tests |
| C2 | P1 | Wind not per-code | Per-code wind strategies in `domain/calculations/wind/` | §5.7 |
| D1 | P1 | 3 drawing implementations, DXF coverage gaps | `DrawingScene` + 3 renderers (§6) | parity test: same scene → same entity counts/labels across backends |
| R1 | P1 | No unified result/trace model | `ResultEnvelope` + `CalcStep` trace (§7.2) | PDF shows step-by-step with clause refs |
| Q1 | P2 | Dead legacy views, god files | Delete `views/` after build; split `PdfDrawingGenerator` only after §6 migration | build green; files < 1,500 lines |
| Q2 | P2 | CI weak | Blocking lint on new code, jacoco, DXF/PDF validation job | CI red on any guard failure |
| Q3 | P2 | Stale Compose stack | Isolated PR: BOM ≥ 2026.08.00 (check for newer), compileSdk 37, align `navigation 2.9.6` vs `compose-navigation 2.7.7` | full test + emulator run green |

---

## 4. Phased plan

**Phase 0 — Hygiene and release safety** (no calculation changes)
0.1 H1 repo cleanup · 0.2 H2 `free` flavor + ad-manager merge · 0.3 H5 permissions · 0.4 H4 Room safety · 0.5 delete dead `views/` · 0.6 H3 English-only PDF text (strings only, no layout change).

**Phase 1 — Safety net**
1.1 Guard scripts + CI (Skills 6–7) · 1.2 jacoco report + gap list · 1.3 golden vectors for every untested engine in §2.4 (one engine family per batch) · 1.4 parity/divergence tests per element (pattern already used in `*CodeDivergenceTest`).

**Phase 2 — One source of truth**
2.1 D1 decision executed (`core` vs `app`) · 2.2 `ResultEnvelope` + `CalcStep` introduced via adapters over existing result types (no rewrite) · 2.3 ViewModels expose the envelope · 2.4 screen/PDF/DXF read only the envelope.

**Phase 3 — Calculation depth** (§5; one element per batch, three codes each)
Order: **Beam → Column → Slab → Footing/foundations → Seismic → Wind → Retaining wall/Tank/Stair → Shear wall/Wall opening/PT → Steel → Frame analysis → BOQ/BBS.**

**Phase 4 — Drawings** (§6): `DrawingScene`, renderers, element-by-element migration, DXF gaps, validation.

**Phase 5 — Reports/exports** (§6.4): one PDF engine, per-element detailed sections, calc trace, BBS/BOQ tables.

**Phase 6 — Product and toolchain**
6.1 Compose/AGP bump (Q3) · 6.2 onboarding flow (splash → language → user type: normal user vs civil engineer, each unlocking its own tool set, separate nav graphs under shared onboarding) · 6.3 UX polish · 6.4 Play listing checklist (Skill 8).

---

## 5. Calculation expansion catalogue

Legend: ✔ exists · ◐ exists but partial/unverified · ✘ not found (name-based grep) · ? not searched — **investigate first, don't assume**. Every item: research (R4) → engine → trace → tests → UI → drawing → PDF → DXF → BOQ.

### 5.1 Beams
- ✘ Torsion — ACI, SBC (✔ ECP `designTorsion`; generic `BeamDesignEngine_Part2.designTorsion` exists, check whether it is wired). Threshold torque, compatibility vs equilibrium, shear+torsion interaction, closed stirrups, longitudinal torsion steel.
- ✘ Strong-column/weak-beam — ECP, ACI (✔ SBC `checkStrongColumnWeakBeam`; **its `rhoSum` uses a hard-coded 250 mm width — fix while porting**).
- ? Special-frame beam detailing (hoop zones, bar-splice restrictions, min/max steel at joints).
- ✘ **Lap splice & development length as first-class results** (Class A/B, tension/compression, spacing/cover/coating factors) feeding drawings and BBS; development length itself exists in several files — consolidate into one `DevelopmentLength` per code.
- ◐ Crack width (mentioned in all three; verify equations and parity) · ✔ deflection (add long-term creep/shrinkage parity check: only 1–2 files mention it).
- ? Bar curtailment/cut-off points, skin reinforcement, continuous-beam moment redistribution, deep-beam parity (`DeepBeamDesign` not code-aware?).

### 5.2 Columns
- ✘ Seismic confinement — ECP, ACI (one file each mentions confinement; no design function). ✔ SBC `designSeismicConfinement` exists, but it computes Ash/s without ever selecting or checking a hoop size against it, and its outside-zone spacing `min(2·s_hinge, 150)` differs from ACI's `min(6·db, 150)` — resolve against the clause text, don't copy it blindly.
- ✘ Lap-splice zone and confinement-zone rules · ? capacity-design column shear · ? beam-column joint shear.
- ◐ Biaxial, slenderness (sway/non-sway), interaction diagram parity — add tests (`InteractionDiagram` untested).
- ? Column-to-footing dowels/starter bars, columns with openings/composite.

### 5.3 Slabs
- ? Crack control, long-term deflection parity · ◐ punching shear (present in all three: add **openings near column, moment transfer, shear studs/heads**) · ? two-way methods (direct design / equivalent frame) vs code coefficients · ? ribbed/Hordi/waffle parity tests (untested).

### 5.4 Foundations
- ✘ **Bearing capacity engine in domain** (Terzaghi/Meyerhof/Hansen with water table, eccentricity, inclination) — today only UI/QC mention it.
- ✔ Settlement — wire `SettlementAnalysis` results into every footing/pile/raft result and drawing.
- ✘ **Raft/mat foundation** (rigid and flexible/Winkler; ties into `soilstructure`) · ◐ combined footing parity (verify ACI/SBC) · ? eccentric/biaxial footing, uplift/overturning/sliding checks, pile-cap truss/strut-and-tie.

### 5.5 Retaining walls, tanks, stairs
- ? Retaining wall: seismic earth pressure, surcharge cases, base-key, global stability (overturning/sliding/bearing/overall slope).
- ? Tanks: crack-controlled design / durability-driven steel stress limits, flotation check, hoop tension at wall–base joint, roof slab and openings.
- ? Stairs: dog-leg, helical, cantilever/folded plate variants; vibration/serviceability.

### 5.6 Seismic
- ✔ base shear/response spectrum (all three) · ✘ **torsional irregularity & accidental eccentricity**, ✘ **diaphragm and collector design**, ✘ liquefaction screening · ? story drift & stability index (`P-Delta` is mentioned in 4 files — verify it is a drift/stability check and not just a note) · ? vertical distribution, soil-profile/site-class selection UI.

### 5.7 Wind (new domain module)
- Create `domain/calculations/wind/` with a `WindCode` strategy per ECP / ASCE 7 (ACI) / SBC 301: basic wind speed by map/zone, exposure, topographic and directionality factors, gust factor (rigid/flexible), MWFRS and C&C pressures, parapets, roofs, open buildings. Keep `utils/WindLoadCalculator` as the legacy façade until parity tests pass. Feed results into `LoadCombination` and `FrameAnalysis`.

### 5.8 Load combinations (single place)
- Audit `domain/entities/LoadCombination.kt`: one table per code (ECP 201, ASCE 7-16/-22, SBC 301) including wind, seismic (with overstrength Ω0/ρ where applicable), soil/fluid pressure, and strength vs serviceability sets. Engines receive combination factors only from here.

### 5.9 Steel
- ✔ member design (3 engines) · ◐ connections (only `SteelConnectionDesign`, in ecp/, untested) → add bolted/welded/shear-tab/moment/end-plate with AISC + ECP 205 + SBC 306 parity · ✔ base plate (extend to anchor-rod tension/shear/prying, blocks) · ? plate girders, bracing/gusset (Whitmore, block shear), fatigue, composite columns · ✔ fire (`FireResistanceDesign`: add code parity, steel temperature method).

### 5.10 Frame analysis
- ✔ `FrameAnalysisEngine`. Add/verify: **second-order (P-Δ / P-δ)**, pattern loading and envelopes, member releases, support settlements/springs, temperature loads, moment-shear-deflection diagrams in PDF, automatic hand-off of envelopes to member design (`ConcreteFrameDesign`).

### 5.11 Quantities
- BBS: shape codes per the regional standard (verify: BS 8666 vs ACI detailing manual vs ECP practice), lap/development lengths from §5.1, cutting-length optimisation (`LayoutOptimizer` exists), weight tables from one `RebarCatalog`. BOQ: remove placeholders (e.g. `CalculateElementBoq` has "2x2m, 10cm thick placeholder" drop-panel volume and a lean-concrete placeholder).

---

## 6. Drawings, DXF and reports

### 6.1 Architecture: `DrawingScene`
```
Result envelope ──► XDrawingBuilder (pure Kotlin, JVM-testable)
                          │
                    DrawingScene  (sheet, views, layers, primitives, tables, notes)
                          │
        ┌─────────────────┼──────────────────┐
  ComposeRenderer     PdfRenderer        DxfRenderer
  (screen)            (vector/bitmap)    (existing DxfExporter as backend)
```
- Primitives: Line, Polyline, Circle, Arc, Text(**English only**), Hatch, Dimension, RebarDot, Stirrup(hooks), Leader, Table, SymbolRef.
- `Layer(name, aciColor, lineweight, linetype)` defined once; DXF uses it directly; Compose/PDF map to colors/strokes.
- Migrate **one element per batch**, beam first (most mature). After migration the old per-backend drawing function is deleted, not kept in parallel.
- Parity test: for the same envelope, entity counts per layer and the set of text labels are identical across the three renderers (renderer-specific styling excluded).

### 6.2 Rebar-drawing standard (every RC element)
- **Three integrated views:** plan, section(s), elevation; section cuts labelled and cross-referenced.
- Bar marks + diameters from one `RebarCatalog` of commercial sizes; spacing and cover shown; hooks/bends per governing code (verify per code).
- **Congestion highlighting:** flag bar groups whose clear spacing violates the governing code minimum (min spacing, aggregate size) and splice zones with doubled steel.
- **Splice and development lengths drawn** (from §5.1) and listed; confinement zones (seismic) hatched/colored with spacing callouts.
- Title block: project, element, code **+ version**, date, scale, app version; notes; BBS table; units mm.
- Same numbers as the screen (R1) — drawings take values from the envelope only.

### 6.3 DXF specifics
- Keep AC1027 and the existing layer naming (e.g. `0_REBAR_BOTTOM`); document the full layer list in `docs/DXF_LAYERS.md`.
- Add: real `DIMENSION` entities (verify whether `addDimensionH/V` already emit them), `BLOCK/INSERT` for repeated symbols (no INSERT usage found), paper-space layouts with scaled viewports, text styles, and an `$EXTMIN/$EXTMAX` that match content.
- Fill the DXF gap list from §2.5 (strap footing, flat slab, waffle, hordi, wall opening, post-tensioned, seismic, soil-structure, shear wall, deep beam, composite beam) — via `DxfRenderer`, not new hand-written code.
- Validation (Skill 6): every exported sample must load with `ezdxf`, pass `audit()`, have expected layers, non-empty entity counts, a bounding box inside the sheet frame, and **zero non-ASCII text**.

### 6.4 PDF/report
- **One PDF engine:** keep `ProfessionalEnglishPdfReporter` as the path; port `FrameAnalysisPdfExporter`, `SteelWarehouseProPdfExporter`, `PostTensionedExporter`, `PdfGenerator` onto it; delete the rest once nothing references them. iText 8.0.5 is in use (verify no AGPL implications for your distribution model — see D6).
- Sections per element: inputs (echo), assumptions, step-by-step calculation (`CalcStep` list with clause refs), checks table with utilization ratios, reinforcement summary, drawings (from `DrawingScene`), BBS/BOQ extract, warnings, disclaimer, code + version, app version, date.
- Pagination and fonts: embed fonts; verify with `pdffonts`; no Arabic glyphs (R5).

---

## 7. Results model

### 7.1 Rules
Rounding only at render time. One utilization definition (demand/capacity, ≤ 1.00 passes) everywhere. Every check carries its clause. Warnings are structured (severity, code, text), not free strings.

### 7.2 Proposed shape (adapt names to the repo; build via adapters over today's `CalculatorEngine.*Result` and `GenericSafetyCheck`)
```kotlin
data class DesignCheck(
    val name: String, val demand: Double, val capacity: Double, val unit: String,
    val utilization: Double, val passed: Boolean, val clause: String?
)
data class CalcStep(
    val symbol: String, val formula: String, val substitution: String,
    val value: Double, val unit: String, val clause: String?
)
data class ResultEnvelope<T>(
    val element: ElementType, val code: DesignCode, val codeVersion: String,
    val inputs: Map<String, String>, val result: T,
    val checks: List<DesignCheck>, val assumptions: List<String>,
    val warnings: List<Warning>, val trace: List<CalcStep>
)
```

---

## 8. Skills and tools to create

Create under `.claude/skills/<name>/SKILL.md` (frontmatter `name`, `description` = when to trigger; body = numbered steps; keep each ≤ 80 lines; scripts in the skill folder).

| # | Skill | Trigger | Steps / output |
|---|---|---|---|
| 1 | `civileg-parity-audit` | Start of every batch; "what's missing" | Regenerate the §2.4 matrix for the element: engine files per code, result entity, ViewModel, screen, drawing, PDF, DXF, BOQ, tests. Output a table + gap list. |
| 2 | `civileg-clause-research` | Before any new equation | Search/open real sources; extract clause text + variables + units + limits; write `docs/clauses/<element>-<code>.md` and append to `docs/CLAUSE_LOG.md`; flag UNVERIFIED. For ECP, ask the owner for the clause text. |
| 3 | `civileg-engine-batch` | Any calculation work | Runs §9 loop: engine + trace + tests + wiring + verification + batch report. Contains templates for engine class, golden-vector test, divergence test, ViewModel wiring. |
| 4 | `civileg-golden-vectors` | Writing tests | Procedure to build a vector from a cited worked example; tolerance policy; where to store (`src/test/resources/vectors/*.json` with source field). |
| 5 | `civileg-drawing-scene` | Drawing work | Builder skeleton, layer registry, renderer parity test, "delete old drawing code" checklist. |
| 6 | `civileg-dxf-validate` | After any DXF change; CI | `python -I tools/guards/dxf_validate.py <dir>` using `ezdxf`: load, `audit()`, layer inventory, entity counts, bbox vs frame, ASCII-only text; optional PNG render for visual diff. |
| 7 | `civileg-pdf-english-guard` | After any PDF/DXF/export text change; CI | `tools/guards/no_arabic_in_exports.py` (scans export sources for Arabic in string literals) + `pdftotext`/`pdffonts` on generated sample PDFs. |
| 8 | `civileg-release-check` | Before tagging a release | Checks: targetSdk 36, merged manifest of `free` flavor (no BILLING/ads/FGS/READ_MEDIA), signing config, versionCode bump, R8/ProGuard rules, Data Safety answers match merged dependencies (Crashlytics/Firebase!), privacy policy URL, screenshots, `bundleRelease` ok. |

**Tools**

| Tool | Use |
|---|---|
| Gradle: `./gradlew :app:testDebugUnitTest :core:calculations:test lintDebug assembleDebug bundleRelease` | build/test truth; run before claiming anything |
| `tools/emulator/*.ps1` | UI verification on Windows. Add bash equivalents (or a GitHub Actions emulator job) so cloud agents can verify UI too (D4) |
| Python 3 + `ezdxf` | DXF validation/preview (Skill 6) |
| poppler-utils (`pdftotext`, `pdffonts`) | PDF language/font checks (Skill 7) |
| jacoco | coverage gate on `domain/calculations` |
| Robolectric (already 4.14.1) | JVM Canvas/PDF smoke tests; evaluate native graphics mode for snapshot-style drawing tests |
| detekt/ktlint (optional) | style on changed files only, to avoid mass diffs |
| Subagents: **Explore** (broad grep sweeps, "very thorough"), **Plan** (batch design), **general-purpose** (clause research) | main agent keeps ownership of edits and verification |
| WebSearch/WebFetch | clause research (R4), Play/toolchain checks |
| Hooks (pre-commit/CI) | guard scripts, `TODO|FIXME|placeholder` ban in `domain/**` |

---

## 9. Autonomous batch loop (binding)

### 9.1 One batch = one element × up to three codes (or one hygiene item)
1. **DISCOVER** — run Skill 1 for the element; read the existing engine, result entity, ViewModel, screen, drawing, PDF/DXF, tests, `git log -- <files>`. Write what already exists.
2. **RESEARCH** — Skill 2. No clause, no formula.
3. **PLAN** — ≤ 15 lines: files to touch, new types, test vectors, what will *not* be touched.
4. **IMPLEMENT** — engine first, with `CalcStep` trace; then adapters/ViewModel; then screen; then drawing/PDF/DXF via the shared model; then BOQ.
5. **TEST** — golden vectors, boundaries, input guards, cross-code divergence, parity (screen=PDF=DXF values).
6. **VERIFY** — run Gradle tasks (§8). Record exact commands and outcomes. Emulator run if available; otherwise say "UI not verified".
7. **REPORT + GATE** — post the report (§10) and **stop**. Do not start the next batch without "go".

### 9.2 Branch/commit
`feat/<element>-<topic>` or `chore/<topic>`; commit message `<area>: <what> (<codes>) [clauses: …]`; no unrelated formatting changes.

### 9.3 Hard stops (ask the owner)
- an existing passing test would change value · a clause can't be verified and is safety-critical · ECP clause text needed · build/test red at the start · change would touch >1 element · security or licensing issue appears · two code sources disagree · anything that affects data in users' Room database.

---

## 10. Batch report template (post this, nothing longer)

```
Batch: <id> <element/topic> · codes: <ECP/ACI/SBC>
Done: <2–4 lines>
Clauses: <list> — verified: <n>/<total>; UNVERIFIED: <list>
Tests: added <n>; changed <n> (why); commands run + result
Parity matrix: cells changed
Not verified: <e.g. emulator/UI>
Risks / follow-ups: <max 3>
Files: <paths>
```

**Definition of done:** R1–R12 satisfied · tests green with the commands shown · parity matrix updated · no new `TODO/placeholder` in `domain/**` · exports English-only · report posted.

---

## 11. Decisions only the owner can make (agent must ask, with a default)

| ID | Question | Recommended default |
|---|---|---|
| D1 | Engines in pure-Kotlin `:core:calculations` (fast JVM tests, no Android) or in `app/`? | Finish the migration toward `:core`, one element per batch, starting with new work; guard against duplicates |
| D2 | Free release: build **flavor** vs runtime flag? | Flavor `free` (nothing monetization-related in its manifest); keep `pro` dormant; if revived, Billing ≥ 8 |
| D3 | Admin panel: separate repo or delete? | Separate private repo; rotate anything that was deployed |
| D4 | Verification environment for agents (Windows scripts vs cloud Linux)? | Add bash scripts + CI emulator job |
| D5 | Compose/AGP bump timing | After Phase 1, isolated PR |
| D6 | iText 8 licensing (AGPL/commercial) vs distribution plan | Confirm before Play release |
| D7 | Where do ECP 203/201 clause texts come from? | Owner supplies excerpts locally; never committed |
| D8 | Regional BBS shape-code standard | Owner picks; agent verifies |

---

## Appendix A — Reproduce the audit

```bash
cd civileg2
find app -name '*.kt' | wc -l; find app -name '*.kt' -print0 | xargs -0 cat | wc -l
for s in main test androidTest; do find app/src/$s -name '*.kt' | wc -l; done
grep -rhoE 'assert[A-Za-z]*\(' app/src/test | wc -l
grep -rn "fun .*[Tt]orsion\|fun .*StrongColumn\|fun .*[Cc]onfinement" app/src/main/java/com/civileg/app/domain
grep -rl 'com.civileg.core' app/src
grep -n "fallbackToDestructiveMigration" app/src/main/java/com/civileg.app.db/AppDatabase.kt
python3 -I - <<'EOF'   # Arabic in export code
import re,pathlib
ar=re.compile(r'[؀-ۿ]')
for f in pathlib.Path('app/src/main/java/com/civileg/app/utils').rglob('*.kt'):
    n=sum(1 for l in f.read_text(encoding='utf-8').splitlines() if ar.search(l) and not l.strip().startswith(('//','*','/*')))
    if n: print(n,f)
EOF
git ls-files | grep -iE 'google-services|\.tsx?$|Caddyfile|\.env'
```

## Appendix B — Sources (verified 2026-11-15)

- Google Play — target API level requirements: https://support.google.com/googleplay/android-developer/answer/11926878
- Play Billing Library deprecation FAQ (v8 required for updates after 2026-08-31, v9 newest): https://developer.android.com/google/play/billing/deprecation-faq
- Jetpack Compose August 2026 release (BOM 2026.08.00 = Compose 1.12; compileSdk 37; AGP ≥ 9.1.1): https://android-developers.googleblog.com/2026/08/jetpack-compose-august-2026-release.html
- AGP 9.5.0 (October 2026) — seen only as a search-result title on developer.android.com; not opened.
- Not verified: latest Kotlin/KSP/Hilt/Room versions; whether a Compose BOM newer than 2026.08.00 exists.


## 12. نسخة iOS ثانية من CivilEG2 | iOS edition roadmap

**Added:** 2026-11-24, at the owner's request. **Status:** planned, not implemented. This is a separate product workstream from the current Android stabilization batch. Adding this goal does not approve a port, a rewrite, a framework, a merge, spending, or an App Store release. No delivery date is committed.

### 12.1 الهدف وحدود القرار

إضافة نسخة ثانية من تطبيق CivilEG2 تعمل على iOS، مع الحفاظ على نسخة Android وعدم تعطيل تثبيتها أو تغيير نتائج الحسابات لمجرد نقل المنصة. نطاق النسخة الأولى، والأجهزة المدعومة، وطريقة مشاركة الحسابات والبيانات، ومسار التنفيذ تظل قرارات للمراجعة والموافقة قبل البرمجة. لا تعني هذه الإضافة أن نسخة iOS موجودة أو أن جميع وظائف Android قابلة للنقل كما هي.

### 12.2 Current architecture, rechecked for this addition

The current repository is Android-oriented: `settings.gradle.kts` includes `:app` and `:core:calculations`; `app/build.gradle.kts` applies the Android application and Compose plugins. Despite the earlier pure-Kotlin migration proposal, `core/calculations/build.gradle.kts` currently applies `com.android.library`, Kotlin Android and `kotlin-parcelize`, and declares a Firebase Crashlytics dependency. It is not already a verified platform-neutral iOS library. This dated observation does not refresh the older audit metrics or mark its backlog complete.

### 12.3 Investigation and review gates before implementation

| Gate | Planned work | Required evidence / acceptance |
|---|---|---|
| IOS-0: agree first-version scope | Inventory Android screens, element types, selected design codes, inputs, outputs, drawings, reports, BOQ, local prices, language and offline behavior. Identify the proposed iOS subset, unsupported cases and later additions. | Owner-reviewed screen × case × code × output matrix, including explicit unsupported/incomplete states; no blanket feature-parity claim. |
| IOS-1: dependency and reuse audit | Trace Android/JVM dependencies in calculation engines, result models, serialization, file APIs, storage, drawing, PDF/DXF and services. Assess licensing and source/service restrictions. | File-level dependency map and tested evidence for any proposed reusable boundary; migration risks and costs recorded. No assumption that the existing `core` module can compile for iOS. |
| IOS-2: architecture decision | Compare a native iOS implementation with suitable shared-code approaches against the agreed scope, numerical parity, UI/RTL quality, maintainability and platform dependencies. Include any proposed repository arrangement. | Written options with trade-offs, limitations and a small feasibility proposal. Owner approves the approach and pilot scope before any port or prototype starts; no framework is selected by this roadmap. |
| IOS-3: approved feasibility pilot | After the separate implementation approval, prove one bounded vertical slice: inputs → calculation result → checks/warnings → drawing/export as applicable. | Reproducible iOS build and run evidence on a named simulator/device configuration; compared numerical fixtures and inspected Arabic/English pixels. Failure remains a finding, not an implied commitment to a rewrite. |
| IOS-4: incremental implementation | Proceed only in approved slices using the verified engine/result boundary. Define persistence, export/import and any Android-to-iOS transfer separately; do not assume account sync or cloud storage. | Each slice passes cited golden vectors, boundary/invalid-input tests, code-specific parity, returned-check state tests and screen/drawing/export consistency checks. Android regression tests remain passing; no silent engineering-value changes. |
| IOS-5: iOS acceptance | Agree supported iPhone/iPad and OS scope; test RTL/LTR, Arabic fonts, accessibility, screen sizes, keyboard and units, offline behavior, save/reopen, file sharing and export readability. | Actual simulator/device evidence for the agreed matrix, including negative and incomplete cases. No clipping, misleading global safety status or unsupported feature presented as complete. Known limitations recorded. |
| IOS-6: distribution readiness | Verify then-current Apple tooling, signing, privacy, third-party service/licensing and distribution requirements using permitted official sources. Identify needed owner accounts, hardware and costs. | Owner-approved release checklist, costs and distribution route. Account setup, paid services, signing assets, beta distribution and App Store submission each require the relevant approval; none is performed by this plan edit. |

### 12.4 Relationship to Android and completion reporting

- Android stabilization and its current review gates continue independently. This roadmap edit changes documentation only, not application code, build configuration, CI or release behavior.
- The iOS workstream is not counted as completed Android stabilization work. Its progress denominator will be defined from the approved iOS scope, not guessed from Android line counts or reused file counts.
- Engineering results require independent validation on both platforms. Matching one fixture or obtaining a successful build is not engineering certification or proof of whole-app parity.
- **Next decision after investigation:** approve the initial iOS scope, architecture and bounded pilot. Until then: roadmap recorded, implementation not started, no merge or release authorized.
