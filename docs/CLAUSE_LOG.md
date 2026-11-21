# Clause log (R4)

Every formula or limit used by a new golden vector is recorded here **before**
the test is written. The official ASCE 7-16 text is paywalled and was not
opened; the sources below are public reproductions or engineering publications
that quote the clause/tables. The nature of each source is stated explicitly.

All rows accessed: **2026-11-19**.

| ID | Clause / value | Source (accessed 2026-11-19) | Status |
|---|---|---|---|
| C-01 | ASCE 7-16 §12.8.2.1 Eq. 12.8-7: `Ta = Ct · hn^x`; Table 12.8-2 imperial coefficients: steel MRF 0.028/0.80, concrete MRF 0.016/0.90, all other 0.020/0.75; `hn` in ft | [Bentley RAM Structural System Help — Approximate Fundamental Period (§12.8.2.1)](https://docs.bentley.com/LiveContent/web/RAM%20Structural%20System%20Help-v4/en/GUID-7C0568C6-D473-4EF0-B1A3-25C2CC31A10B.html) | Verified (secondary: software manual quoting the code) |
| C-02 | Metric form used by this repo: `Ct_m = Ct_ft × 3.28084^x` → 0.0724/0.80, 0.0466/0.90, 0.0488/0.75; `hn` in m. Values already used by `CourseTestVectorsTest` | Unit conversion derived from C-01; arithmetic: 0.020·3.28084^0.75 = 0.04875 ≈ 0.0488; 0.016·3.28084^0.90 = 0.0466; 0.028·3.28084^0.80 = 0.0724 | Derived (unit conversion, not a clause) |
| C-03 | ASCE 7-16 Table 12.8-1 `Cu`: `S_D1 ≥ 0.4 → 1.4`, `0.3 → 1.4`, `0.2 → 1.5`, `0.15 → 1.6`, `≤ 0.1 → 1.7` | [ideCAD — Determination of the Building's Fundamental Period per ASCE 7-16](https://help.idecad.com/ideCAD/12-8-2-determination-of-the-building-s-fundamental) | Verified (secondary: software manual reproducing the table) |
| C-04 | ASCE 7-16 §12.9.1.1: at least 90% combined modal mass participation in each orthogonal horizontal direction | [ideCAD — Modal Response Spectrum Analysis per ASCE 7-16 §12.9.1](https://help.idecad.com/ideCAD/12-9-1-modal-response-spectrum-analysis) | Verified (secondary) |
| C-05 | ASCE 7-16 §12.8.6: `δx = Cd · δxe / Ie`; drift = relative displacement / story height | [STRUCTURE Magazine — ASCE 7-16 Provisions for Lateral Drift Determination](https://www.structuremag.org/article/asce-7-16-provisions-for-lateral-drift-determination/) and ideCAD §12.9.1 page ("displacement values multiplied by Cd/Ie") | Verified (secondary) |
| C-06 | ASCE 7-16 §12.8.7: P-Δ effects required when stability coefficient θ > 0.10; θ uses `Px`, story drift, `Ie`, `Vx`, `hsx`, `Cd` | Same STRUCTURE Magazine article (§ "Drift and P-Delta Effects") | **Partial**: threshold 0.10 verified; the exact θ expression was not reproduced in the fetched text — the repo's expression is treated as existing behavior, not as an independently verified clause |
| U-01 | Table 12.12-1 drift limit 0.015 (default in `StoryDriftInput`) | Not opened in an authoritative source | **UNVERIFIED-CLAUSE** — the value is caller-supplied in the golden vector; the engine does not select it |
| U-02 | Table 12.3-1 torsional irregularity ratios 1.2 / 1.4 | Not opened | **UNVERIFIED-CLAUSE** — characterization tests only |
| U-03 | Diaphragm flexibility thresholds 0.5 / 2.0; eccentricity limit 15% | Not opened | **UNVERIFIED-CLAUSE** — characterization tests only |
| U-04 | Base-shear scaling threshold 0.85·V (§12.9.1.4) | Not opened | **UNVERIFIED-CLAUSE** — characterization tests only |

## Use in this batch (Phase 1.3)

- Golden vector: C-01 + C-02 (`Ta`, `Cu·Ta`, `Tused`), C-03 (`Cu`), C-04
  (mass participation), C-05 (story drift ratio), C-06 threshold (P-Δ status).
- Characterization only (no claim of code correctness): U-01…U-04 and the
  exact C-06 θ expression.

No engineering formula was added or changed. No ECP clause was used.

## Phase 1.3, batch 2 — `SBCJoistSlab` (accessed 2026-11-19)

The only publicly accessible full SBC 304 text found is the **2007 edition**
(GDA-hosted PDF, 274 pages), not SBC 304-2024 claimed in the engine comments.
Clause numbers and several limits differ, so every 2024-specific value is
labelled UNVERIFIED-2024 and is tested as characterization only.

| ID | Clause / value | Source (accessed 2026-11-19) | Status |
|---|---|---|---|
| S-01 | SBC 304-2007 §8.11.2: rib width ≥ 100 mm; rib depth ≤ 3.5 × rib width | [SBC 304-2007 official PDF hosted by GDA](https://www.gda.com.sa/pdf/SBC304.pdf), text extracted via [r.jina.ai](https://r.jina.ai/https://www.gda.com.sa/pdf/SBC304.pdf) | Verified for 2007 edition (PDF extraction renders "3½" as "213"; 3.5 is corroborated by S-03) |
| S-02 | SBC 304-2007 §8.11.3: clear spacing between ribs ≤ 800 mm | Same PDF | Verified for 2007. The engine's 700 mm limit attributed to "§8.4.1 (2024)" is **UNVERIFIED-2024**; characterized only |
| S-03 | ACI 318-19 §8.8 joist limits: rib width ≥ 4 in (~100 mm), depth ≤ 3.5 × width, clear spacing ≤ 30 in (~750 mm), `Vc` may be taken as 1.1 × base value | [ideCAD — Nonprestressed two-way joist systems (waffle slabs) per ACI 318-19 §8.8](https://help.idecad.com/ideCAD/nonprestressed-two-way-joist-systems-waffle-slabs) | Verified (secondary: software manual quoting ACI) |
| S-04 | SBC 304-2007 §11.3.1.1: `Vc = 0.17·√fc'·bw·d` (N, MPa) | Same PDF | Verified for 2007. The engine's `0.25·√(fcu/1.5)·0.75` partial-factor form and the 10% joist enhancement are **UNVERIFIED-2024**; characterized only |
| S-05 | γc=1.5, γs=1.15, φflex=0.9, φshear=0.75, β₁ 0.85→0.65, min As = max(0.15%·bw·d, 1.3%·As), ρmax = min(4%, 0.375·β₁·fc/fsd) | Engine constants only (`SBCJoistSlab.kt`) | **UNVERIFIED-2024** — characterization only; no compliance claim |

Use in this batch: golden vector and geometry boundaries use S-01/S-03 where
they agree with the engine; S-02's 700 mm, S-04's shear coefficients and S-05
are locked as current behaviour, explicitly not as verified code clauses.
No production formula was added or changed.

## Stability track, batch — `ACIPileFoundation` (accessed 2026-11-21)

| ID | Clause / value | Source (accessed 2026-11-21) | Status |
|---|---|---|---|
| P-01 | Deep foundation in clay: end bearing `Qb = Nc·cu·Ab` with Skempton `Nc = 9` | [LibreTexts Example 6.1 — pile capacity in clay](https://eng.libretexts.org/Bookshelves/Civil_Engineering/Fundamentals_of_Foundation_Engineering_(Tsiampousi)/06:_Pile_Foundations/6.31:_Example_-_Pile_capacity_in_clay) | Verified (secondary: textbook example) |
| P-02 | Alpha shaft method; API RP 2A-WSD §6.4.2 form `α = 0.5·ψ^-0.5` (ψ≤1), `α = 0.5·ψ^-0.25` (ψ>1), with `α ≤ 1.0`; the engine's high band `α = 0.5·sqrt(100/cu)` matches the ψ≤1 form with p'0 = 100 kPa | [OpenBrIM docs — API RP 2A-WSD clay shaft friction](https://docs.openbrim.org/) (API alpha method page) | Verified for the high band; the engine's middle band `α = 1−(cu−25)/100` and the flat 0.5 band (50–100 kPa) are engine-specific — characterized only |
| P-03 | Beta method (sand) `Qs = K·tan(φ)·σ'v·As`; Berezantzev `Nq = e^(π·tanφ)·tan²(45°+φ/2)` | Standard textbook form (Das, Principles of Foundation Engineering); not re-opened this batch | UNVERIFIED-SOURCE — characterization only |
| P-04 | ACI 318-19 Table 22.6.5.2: two-way shear `vc` = least of (a) 0.33·λ√f'c, (b) 0.17·(1+2/β)·λ√f'c, (c) 0.083·(2+αs·d/bo)·λ√f'c; αs = 40 interior / 30 edge / 20 corner | [ideCAD — two-way (punching) shear per ACI 318-19](https://help.idecad.com/ideCAD/two-way-shear-punching) | Verified (secondary: software manual quoting ACI table structure and αs values) |
| P-05 | One-way shear `Vc = 0.17·λ·√f'c·b·d` (ACI 318-19 §22.5.5.1), φshear = 0.75, φflex = 0.9 (§21.2.1) | ideCAD shear pages + engine constants | Verified (secondary) |
| U-P1 | Alpha curve discontinuity at cu = 50 kPa: middle band yields 0.75 just below 50, next band pins 0.5 | Engine inspection | **KNOWN DEFECT — FLAGGED, not fixed** (needs an engineering decision on curve smoothing) |
| U-P2 | Group efficiency is a Converse-Labarre variant: uses atan(s/d) and only the (m−1) term; standard form uses θ = atan(d/s) with both terms (3×3 @ s=3d: standard ≈0.73, engine ≈0.65) | Engine inspection vs standard formula (Das) | **FLAGGED, not fixed** — conservative direction; pinned by characterization test |

Engineering fixes applied in this batch (documented, with golden vectors):

1. **Pile-cap flexure moment unit error** — `Mu` held kN·mm but was converted to
   N·mm with ×1e6 instead of ×1e3, inflating Rn 1000×, pinning ρ at the 0.025 cap
   and producing As ≈ 7× the correct value (22,720 vs 3,181 mm² on the golden
   vector). Fixed to ×1e3.
2. **Pile-cap bar selection could under-provide** — the selection loop accepted a
   diameter whose bar count fit spacing limits without checking the provided area;
   after spacing coercion it could provide less steel than required. Now a
   diameter is accepted only when provided ≥ required.

No other production formula was added or changed. Group-efficiency and the
alpha-curve shape remain as-is pending owner engineering decisions (U-P1, U-P2).

## Owner-decision batch — pile alpha curve + group efficiency (accessed 2026-11-21)

The owner reviewed U-P1/U-P2 (from the ACIPileFoundation batch) and directed:
"apply the correct and most accurate engineering option" for both.

| ID | Clause / value | Source (accessed 2026-11-21) | Status |
|---|---|---|---|
| P-06 | API RP 2A-WSD §6.4.2 alpha method as a continuous curve: `α = 0.5·ψ^-0.5` (ψ ≤ 1), `α = 0.5·ψ^-0.25` (ψ > 1), `α ≤ 1.0`, ψ = cu/p'0; applied with the engine's implicit p'0 = 100 kPa. Replaces the banded curve that jumped 0.75 → 0.5 at cu = 50 kPa (U-P1, RESOLVED). Continuous at cu = 25 (α = 1.0) and cu = 100 (α = 0.5). | OpenBrIM API RP 2A-WSD alpha method page (opened earlier today, this session) | Verified (secondary) |
| P-07 | Standard Converse-Labarre group efficiency: `η = 1 − θ·[(m−1)·nc + (nc−1)·m] / (90·m·nc)`, θ = atan(d/s) in degrees. Replaces the engine variant (atan(s/d), single term; U-P2, RESOLVED). 3×3 @ s=3d: η = 0.7269 (was 0.6466). | [FireCAD — Pile Group Efficiency (Converse–Labarre)](https://calc.firecad.com/civil/foundations/pile-group-efficiency); [Converse-Labarre calculator reference](https://calculatgeek.com/pile-group-efficiency-calculator-converse-labarre/) | Verified (secondary; textbook-standard form) |

Behavior changes (intended, owner-approved):
- Clay shaft capacity for 25 < cu ≤ 100 kPa increases (e.g. cu = 50: α 0.5 →
  0.707); for cu > 100 kPa it also increases slightly (ψ^-0.25 form).
- Group efficiency increases to the standard values (3×3 @ 3d: 0.727; 2×2 @
  3d: 0.795). The engine's 0.5..1.0 clamp is retained.
- Golden vectors updated on this branch: `verified alpha is the continuous
  API RP 2A curve`, `verified clay alpha high-strength band matches API
  psi^-0p25 form`, `verified group efficiency is standard Converse-Labarre`.

## Stability track, batch — `AISCSteelDesignEngine` (accessed 2026-11-21)

| ID | Clause / value | Source (accessed 2026-11-21) | Status |
|---|---|---|---|
| A-01 | AISC 360-16 D2-1 gross yielding `φt·Fy·Ag`, φt = 0.90 | [ideCAD — Steel Column/Member Design per AISC 360-16](https://help.idecad.com/ideCAD/steel-column-design-aisc-360-16) | Verified (secondary) |
| A-02 | AISC 360-16 D2-2 net rupture `φt·Fu·Ae`, φt = 0.75 | Same page family | Verified (secondary) |
| A-03 | AISC 360-16 E3-2/E3-3/E3-4: `Fe = π²E/(KL/r)²`; inelastic `Fcr = 0.658^(Fy/Fe)·Fy` for KL/r ≤ 4.71√(E/Fy), elastic `Fcr = 0.877·Fe` otherwise; φc = 0.90 | [ideCAD — Flexural Buckling per AISC 360-16](https://help.idecad.com/ideCAD/flexural-buckling) | Verified (secondary) |
| A-04 | AISC 360-16 J4.3 block shear `Rn = min(0.6·Fy·Agv + Ubs·Fu·Ant, 0.6·Fu·Anv + Ubs·Fu·Ant)`, φ = 0.75 | Standard AISC form; engine geometry (gauge/edge/pitch estimates) is an engine simplification | Formula verified; engine geometry CHARACTERIZED |
| A-05 | Section properties: box `Ix = (b·h³−(b−2t)(h−2t)³)/12`, I-shape open-form Ix/Iy | Standard strength-of-materials formulas | Verified |
| U-A1 | Welded tension: engine returns a block-shear "placeholder" `0.75·0.6·Fy·Ag` that GOVERNS the design (cuts welded tension capacity ~50%). AISC J4.3 has no block-shear limit state for full welded connections | Engine inspection | **FLAGGED, not fixed** — conservative direction; owner engineering decision |
| U-A2 | Torsional buckling (E4) branch selection uses the FLEXURAL slenderness for the E3-3/E3-4 choice and `min(KxLx, KyLy)` as the torsional length instead of Kz·Lz | Engine inspection | **FLAGGED, not fixed** — pinned by characterization test |
| U-A3 | Plastic modulus `Zx = 1.12·Sx` (shape-factor approximation) for I-sections | Engine constant | Approximation, characterized; within typical rolled-shape range |

Golden vectors: A-01/A-02 (tension bolted, net rupture governs), A-03
(RHS inelastic + I-section elastic), A-04 block-shear arithmetic. Torsional
path and welded placeholder pinned as characterization (U-A1/U-A2).
No production formula was added or changed in this batch.

## Owner-decision batch — AISC steel corrections (accessed 2026-11-21)

The owner directed the engineering-correct fix for all three AISC findings
from the member-design batch (his standing rule: fix correctly, document,
separate PR, nothing merges without his OK).

| ID | Clause / value | Source (accessed 2026-11-21) | Status |
|---|---|---|---|
| A-06 | AISC 360-16 J4.3 block shear applies to BOLTED (holed) connections; welded and pressed connections have no block-shear limit state. The placeholder `0.75·0.6·Fy·Ag` that governed welded tension is removed; those paths now return +infinity (excluded from the governing min). Welded tension capacity rises ~2x to the D2-1/D2-2 value. U-A1 RESOLVED. | [ideCAD — Steel Member Design per AISC 360-16](https://help.idecad.com/ideCAD/steel-column-design-aisc-360-16) (J4.3 scope, secondary) | Verified (secondary) |
| A-07 | AISC 360-16 E4: Fcr for torsional/flexural-torsional buckling comes from the E3 equations applied to the TORSIONAL elastic stress Fe, with the inelastic/elastic branch at Fy/Fe = 2.25. The branch no longer keys on the flexural slenderness. U-A2 RESOLVED. Remaining simplification: L = min(KxLx, KyLy) pending a Kz·Lz input (U-A2a, conservative). | [ideCAD — Flexural Buckling per AISC 360-16](https://help.idecad.com/ideCAD/flexural-buckling) | Verified (secondary) |
| A-08 | Exact plastic section modulus replaces the 1.12·Sx approximation: I-shape `Zx = bf·tf·(h−tf) + tw·(h−2tf)²/4`; CHS/Pipe `(D³−(D−2t)³)/6`; RHS `(w·h²−(w−2t)(h−2t)²)/4`. Singly symmetric shapes (C/L/T/PG) keep shape factors pending plastic-NA work. U-A3 RESOLVED for I/CHS/RHS/Pipe. | Standard strength-of-materials exact forms (Zx of I 300x150x10x7 = 572,200 mm³, shape factor 1.131 vs the old flat 1.12) | Verified (textbook) |

Behavior changes (intended, owner-approved): welded/pressed tension capacity
increases to the correct D2 values; torsional branch selection corrected;
flexural capacities via Zx shift slightly (I-shapes up to ~1%, more accurate).
Golden vectors updated on this branch (welded tension governs D2-1;
torsional-governed compression re-pinned).

## Stability track, batch — `SBCSteelDesignEngine` (accessed 2026-11-21)

| ID | Clause / value | Source (accessed 2026-11-21) | Status |
|---|---|---|---|
| S-01 | Resistance factors: φt = φc = φb = φv = 0.90; φ = 0.75 for bolt shear/tension, weld, net rupture, plate bearing, block shear; φc(concrete bearing) = 0.65 | Official SBC 306 PDF, alriyadh.gov.sa archive (LRFD design basis, Sections 1.5); equation-factor cross-check via [ideCAD AISC 360 pages](https://help.idecad.com/ideCAD/steel-column-design-aisc-360-16) (secondary) | Verified (official excerpt + secondary) |
| S-02 | Column curve: Fe = π²E/(KL/r)²; Fcr = 0.658^(Fy/Fe)·Fy for KL/r ≤ 4.71√(E/Fy), else Fcr = 0.877·Fe | [ideCAD — Flexural Buckling per AISC 360-16](https://help.idecad.com/ideCAD/flexural-buckling); same curve family in AISC 360-05, the licensed SBC 306 basis | Verified (secondary) |
| S-03 | Compact-section flexure Mn = Fy·Zx (Chapter F2). Engine NONCOMPACT path returns min(Mp, My) — conservative vs the AISC F3 flange-local-buckling interpolation. Engine SLENDER path (My·(1−0.3·…) blend) is not an AISC/SBC equation | AISC 360 F2/F3 forms via ideCAD (secondary) | Compact verified (secondary); NONCOMPACT/SLENDER CHARACTERIZED — U-S1 flagged, conservative direction |
| S-04 | Shear Vn = 0.6·Fy·Aw with Aw = (d−2tf)·tw and no Cv factor. Cv = 1.0 is exact for webs with h/tw ≤ 2.24√(E/Fy) (all standard rolled I-shapes); slender webs are overestimated | AISC 360 G2 via ideCAD (secondary) | Verified for standard sections; CHARACTERIZED otherwise — U-S1 note |
| S-05 | LTB: Lp = 1.76·ry·√(E/Fy), checked against ry (weak axis) | AISC 360 F2 via ideCAD (secondary) | Verified (secondary); LTB vectors deferred to a later batch |
| S-06 | Fillet weld (J2.4): throat = 0.707·w, Fw = 0.60·FEXX, φ = 0.75; base-metal shear check 0.6·Fu with φ = 0.90 | AISC 360 J2 via ideCAD (secondary) | Verified (secondary) |
| S-07 | Bolt shear 0.45·Fu·Ab φ = 0.75 (threads-included value of AISC Table J3.2 — engine has no thread-condition input, conservative for A325-N); bearing 2.4·Fu·d·t φ = 0.75 (J3.10); block shear J4.3 with Ubs = 1.0; engine block-shear geometry lc = 3·db is approximate | AISC 360 J3/J4 via ideCAD (secondary) | Formulas verified (secondary); 0.45Fu and lc = 3db CHARACTERIZED |
| S-08 | Combined loading H1-1a / H1-1b with the 0.2 axial threshold | AISC 360 H1 via ideCAD (secondary) | Verified (secondary) |
| S-09 | Base plate concrete bearing φc·0.85·f'c·A1·√(A2/A1), √ ratio capped at 2 (AISC Design Guide 1 / J8). Engine extras: a 0.5 factor inside requiredArea (plate sizing only, conservative) and cantilever X = max(m, n) without the λn' term | AISC DG1 J8 form (secondary) | Bearing formula verified (secondary); sizing/cantilever CHARACTERIZED — U-S2 flagged, conservative |
| S-10 | Bracing tension D2: φFyAg vs φFuAn with An = 0.85·Ag hole deduction approximation | AISC 360 D2 via ideCAD (secondary) | Formula verified (secondary); 0.85·Ag CHARACTERIZED — U-S3 flagged |
| S-11 | SBC-specific engine constants: coastal corrosion capacity reduction ÷1.1, minimum plate thickness 6 mm in corrosive environments, load factors 1.2D/1.6L, seismic width-thickness limits | No public SBC 306 clause text located for these specific values | **UNVERIFIED** (engine constants; plausible vs common practice, not opened in the source) |
| S-12 | Live-load deflection limit L/360 | Common serviceability practice (AISC 360 Chapter L is non-mandatory guidance) | CHARACTERIZED |
| U-S4 | Bracing CHS radius of gyration r = D/4; exact thin-tube r ≈ D/(2√2) ≈ 0.354·D, so the engine overestimates slenderness ~41% for CHS compression bracing | Engine inspection vs standard formula | **FLAGGED, not fixed** — conservative direction |
| U-S5 | Engine header and codeNotes cite "AISC 360-16" as the SBC 306 basis; the official SBC 306 text licenses AISC 360-05/341-05 and AISC LRFD 1999. The column-curve and φ values used are identical in both, so results are unaffected; the reference label is wrong | Official SBC 306 PDF vs engine comments | **FLAGGED, documentation-only** |

Golden vectors: S-01/S-02 (column inelastic + elastic branches), S-03 compact
(beam flexure), S-04 (beam shear), S-06 (fillet weld), S-08 (H1-1a passing
case), deflection S-12. Characterization pins: coastal factor, bolted
connection governing stack, base-plate sizing, bracing tension.
No production formula was added or changed in this batch.

### S-13 - SBC vector corrections (test-only, engine unchanged) - 2026-11-21

Three SBC golden vectors asserted values the engine never produced (latent since
the batch was authored; masked by the axialRatio compile error fixed in #50):

- Bracing tests used a fillet weld 8x300 E70XX whose engine capacity is 368 kN,
  below the applied loads (600/800 kN), so isSafe could never be true. Welds
  resized to 5x1100 E70XX (capacity 843.5 kN per the engine's AISC J2-4 weld
  path vs 2120.6 kN base-metal path, J4.2) so the member curve governs, as the
  tests intend. Source: engine designWeldedConnection, SBCSteelDesignEngine.kt.
- Base plate anchors at Pu=1000 kN: engine bucket is Pu<1000 -> Ø24 else
  Pu<2000 -> Ø27, so Ø27 x 324 mm (12*27 > 250) is the characterized output.
  Source: designBasePlate anchor bucket, SBCSteelDesignEngine.kt.

No production formula was added or changed in this batch.

### E-01 - ECP beam deflection uses actual fy (salvage) - 2026-11-21

| ID | Claim | Source | Status |
|----|-------|--------|--------|
| E-01 | ECP 203 §6-3-1 deflection modification factor MF = 0.55 + 477 / (fy × ρ%), capped [1.0, 2.5], uses the member's actual design fy. Previously hardcoded fyEff = 360 regardless of the designed steel grade. Interface `BeamDesign.checkDeflection` gained `fy: Double = 360.0` (default preserves prior behavior for unconverted callers); ECP implementation uses the passed fy coerced to [240, 600]; ACI/SBC implementations accept the parameter for parity (their §24.2/§9.5 tables do not use it). | Engine code comments citing ECP 203-2025 §6-3-1; formula shape from salvage branch publish/advanced-quality-fixes | **UNVERIFIED** against the official ECP 203 text (source document not opened); formula unchanged from master's own comment, only the fy source was fixed |

Golden guard: ECPBeamDeflectionFyTest pins MF behavior at fy = 240/360/400
(span 6 m, depth 500 mm, ρ = 1.0%): ratio 0.240 / 0.320 / 0.344 respectively.
---

## Owner-decision batch - ECPSlab two-way bending coefficients (accessed 2026-11-21)

Replacement of the legacy simplified two-way slab moment table (BS 8110 / IS 456
family values mislabeled as ECP) with the verified ECP 203-2020 method. Source:
the owner's own copy of the official Arabic ECP 203-2020 PDF, sent via WhatsApp
on 2026-11-21. Every row below was read directly from that PDF and is VERIFIED
(primary source, first-party code text).

| ID | Clause / value | Source (accessed 2026-11-21) | Status |
|---|---|---|---|
| TW-01 | ECP 203-2020 §6-1-2-6-3 (simplified method, two-way solid slabs, uniform loads): `M_a = alpha·w·a²/d`, `M_b = beta·w·b²/d`; d = 8 span simply supported both ends, d = 10 continuous one end, d = 12 continuous both ends [Eq. 6-7a/b/c] | Owner-provided ECP 203-2020 PDF, printed page 6-9 (PDF p. 242) | Verified (primary) |
| TW-02 | Table 6-1 (slabs cast monolithically with beams, LL ≤ 5 kN/m²): alpha = 0.35, 0.40, 0.45, 0.50, 0.55, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85 and beta = 0.35, 0.29, 0.25, 0.21, 0.18, 0.16, 0.14, 0.12, 0.11, 0.09, 0.08 for r = 1.0..2.0; closed form Eq. 6-8: `alpha = 0.5r − 0.15`, `beta = 0.35/r²` | Same PDF, printed page 6-9 | Verified (primary) |
| TW-03 | Method scope (§6-1-2-6-3 + §6-2-1-6-1): rectangular panels supported on four sides, cast monolithically with beams, rectangularity ratio r ≤ 2 (r > 2 → design one-way); LL > 5 kN/m² uses Table 6-3 (not yet implemented) | Same PDF, printed pages 6-8/6-9 | Verified (primary) |
| TW-04 | Wall-supported variant: Table 6-1(2a) alpha 0.396..0.849, beta 0.396..0.053 (not yet implemented; engine assumes monolithic-with-beams) | Same PDF, printed page 6-10 (PDF p. 243) | Verified (primary), implementation deferred |
| TW-05 | Negative support moment between adjacent panels: `M_c = (M1·L1 + M2·L2)/(L1 + L2)` [Eq. 6-9]; single-panel scope: negative coefficient equals the continuous-span positive coefficient, zero for simply supported spans | Same PDF, printed page 6-10 | Verified (primary); single-panel mapping is an engine convention, stated in code comments |

Removed provenance: the previous table ("جداول مبسطة من الكود المصري (قريبة من قيم
ماركوس)") and the advanced-quality-fixes branch's 8-case table labeled "ECP 203
جدول 6-1" are BS 8110 Table 3.13 / IS 456 Table 26 family values (interior-panel
0.031/0.024 at r = 1.0 fingerprint), not ECP 203. Owner decision 2026-11-21:
forget the Indian code and remove everything related to it; ECP 203-2020 and
SBC PDFs are the references of record, ACI PDF to follow.

## ACI 318M-14 primary verification batch — ACIPileFoundation structural clauses (accessed 2026-11-21)

Source of record: the owner's ACI 318M-14 Arabic/English edition (4 PDF parts,
801 pages, forwarded by the owner 2026-11-21). Printed page numbers below are the
code's own page footers; the PDF part/page locators point into the owner's files.
This batch upgrades P-04/P-05 from secondary-source to primary-source status.

| ID | Clause / value | Source (accessed 2026-11-21) | Status |
|---|---|---|---|
| A-01 | Table 21.2.1 strength reduction factors: shear φ = 0.75 (row b); moment/axial φ = 0.65–0.90 per §21.2.2 (tension-controlled 0.90). Engine uses φshear = 0.75, φflex = 0.90. **Upgrades P-05 to primary.** | ACI 318M-14 Table 21.2.1, printed p. 544 (part 3, PDF p. 153) | **Verified (primary)** |
| A-02 | §22.5.5.1 one-way shear for nonprestressed members without axial force: Vc = 0.17·λ·√f'c·bw·d (Eq. 22.5.5.1). Engine: 0.17·√f'c·b·d. **Upgrades P-05 to primary.** | ACI 318M-14 §22.5.5.1, printed p. 563 (part 3, PDF p. 172) | **Verified (primary)** |
| A-03 | Table 22.6.5.2 two-way shear: vc = least of (a) 0.33·λ√f'c, (b) 0.17·(1+2/β)·λ√f'c, (c) 0.083·(2+αs·d/bo)·λ√f'c; §22.6.5.3 αs = 40 interior / 30 edge / 20 corner. Engine matches, with αs = 40 (interior). **Upgrades P-04 to primary.** | ACI 318M-14 Table 22.6.5.2, printed p. 579 (part 3, PDF p. 188); §22.6.5.3, printed p. 580 (part 3, PDF p. 189) | **Verified (primary)** |
| A-04 | §22.6.4.1 two-way shear critical section: perimeter bo at d/2 from edges of column / concentrated loads / reaction areas. Engine: punching perimeter at d/2 from column face. | ACI 318M-14 §22.6.4.1, printed p. 575 (part 3, PDF p. ~175) | **Verified (primary)** |
| A-05 | Table 13.2.7.1 critical section for Mu in shallow foundations and pile caps: column or pedestal → face of column or pedestal; §13.2.7.2 shear critical sections measured from the Mu critical section location. Engine: moment at column face, one-way shear at d from column face. | ACI 318M-14 Table 13.2.7.1 + §13.2.7.2, printed p. 307 (part 2, PDF p. 116) | **Verified (primary)** |
| A-06 | §9.6.1.2 minimum flexural reinforcement: As,min = greater of (a) 0.25·√f'c/fy·bw·d and (b) 1.4/fy·bw·d. Engine's ρmin = 0.0035 = 1.4/400 (fy = 400 MPa); with f'c = 24 MPa, case (a) = 0.0031 < 0.0035, so (b) governs. The engine applies the beam minimum to pile caps — conservative and consistent with the slab-tab note in §9.6.1.1 (engine design choice, not an error). | ACI 318M-14 §9.6.1.2, printed p. 222 (part 2, PDF p. 31) | **Verified (primary)** |

Convention note (not a clause): the engine takes fcu (cube strength) and
converts f'c = 0.8·fcu internally — an app-wide input convention, since ACI
318M-14 works on the specified cylinder strength f'c directly. No code clause
involved; flagged here so future audits don't misread the 0.8 factor as an ACI
provision.

Not in this batch: P-01, P-02, P-03, P-06, P-07 are geotechnical capacity
formulas (Skempton, API RP 2A, Berezantzev, Converse-Labarre). They are not
ACI 318 provisions and remain verified against their own secondary sources.

No production formula was added or changed in this batch.

### ACI combined footing audit — `ACIFooting.designCombinedFooting` (same batch)

Shared checks verified against the owner PDF (entries A-01..A-06 apply here too):
punching uses b0 = 2(c1+c2)+4d (perimeter at d/2, §22.6.4.1), vc = min of the
three Table 22.6.5.2 cases with αs = 40, one-way Vc = 0.17·√f'c·b·d
(§22.5.5.1), φshear = 0.75 / φflex = 0.90 (Table 21.2.1).

| ID | Finding | Evidence | Status |
|---|---|---|---|
| CF-01 | Longitudinal bending uses the simply-supported simplification M = qu·B·L²/8. ACI 318M-14 §13.2.6.2 permits any procedure satisfying equilibrium and geometric compatibility; this form overstates mid-span sagging (≈ wL²/8 vs wL²/8 − P·S/2 for symmetric loads) but never designs top (hogging) reinforcement at the columns, and computes no transverse reinforcement under each column (beam-strip action). | Engine code; ACI 318M-14 §13.2.6.2, printed p. 307 (part 2, PDF p. 116) | **FLAGGED, not fixed** — needs an owner engineering decision (invert-beam shear/moment diagram + transverse strips is the recommended correct method) |
| CF-02 | One-way shear check omits the xResultant offset in the shear span (overestimates Vu — conservative direction) and never checks the inner face of column 2. | Engine code (Step 9) | **FLAGGED, not fixed** — bundle with CF-01 |
| CF-03 | The eccentricity/trapezoid block is dead code: the footing centroid is placed at the load resultant by construction, so e ≡ 0 and maxSoilPressure always equals soilPressure. Reported max/min pressures are misleading. | Engine code (Steps 2–4) | **FLAGGED, not fixed** — cosmetic/reporting |

Existing test coverage: ACIFootingTest covers designCombinedFooting with a
smoke test only (valid inputs produce a result; zero distance throws). No
golden vectors pin combined-footing numbers; a fix for CF-01..CF-03 should
land with golden vectors per R4.

No production formula was added or changed in this batch.

### F-08 — ACI column axial-moment interaction (strain compatibility), batch 2026-11-21

**Finding:** `ACIColumn.calculateReinforcement` amplified the axial-formula steel
area by an invented `max(1, 1 + 2e/h)` factor for e > 0.05h. The expression
appears in no code; it ignored section depth, bar position and the actual
interaction-diagram branch, and under-reinforced high-moment cases (e.g.
Pu=1500 kN / Mx=150 kNm on 400×400/fc'=24: heuristic 12Ø14 vs 18Ø14 required by
strain compatibility). **Fixed:** the heuristic is removed; bar count is now
chosen by a uniaxial strain-compatibility interaction check per axis
(φPn(c) = Pu solved by bisection, φMn(c) ≥ Mu), plus the Table 22.4.2.1 axial
cap. Biaxial bending is checked per axis independently (no Bresler combination)
— documented limitation. Sources opened and verified 2026-11-21 against the
owner's bilingual ACI 318M-14 PDF (rendered pages):

| ID | Clause / value | Source (accessed 2026-11-21) | Status |
|---|---|---|---|
| A-19a | §22.2.1.1 equilibrium + §22.2.1.2 plane sections remain plane | ACI 318M-14 PDF, printed p. 552 (part 3, PDF p. 161) | Verified (owner PDF, printed page) |
| A-19b | §22.2.2.1 εcu = 0.003; §22.2.2.2 tensile concrete strength neglected | ACI 318M-14 PDF, printed p. 553 (part 3, PDF p. 162) | Verified (owner PDF, printed page) |
| A-19c | §22.2.2.4.1 stress block 0.85fc' over a = β1·c; Table 22.2.2.4.3 β1 = 0.85 (fc'≤28), 0.85−0.05(fc'−28)/7, 0.65 (fc'≥55) | ACI 318M-14 PDF, printed p. 554 (part 3, PDF p. 163); β1 table first logged A-08 | Verified (owner PDF, printed page) |
| A-19d | §20.2.2.1 fs = Es·εs below yield else fy; §20.2.2.2 Es = 200 000 MPa | ACI 318M-14 PDF, printed p. 522 (part 3, PDF p. 131) | Verified (owner PDF, printed page) |
| A-19e | §21.2.2.1 εty = fy/Es (= 0.002 for Gr420); Table 21.2.2 tied-column φ: 0.65 compression-controlled, 0.90 at εt ≥ 0.005, transition φ = 0.65 + 0.25(εt − εty)/(0.005 − εty) | ACI 318M-14 PDF, printed pp. 545 and 547 (part 3, PDF pp. 154 and 156) | Verified (owner PDF, printed page) |
| A-19f | Table 22.4.2.1 design axial cap φPn,max = 0.80·φ·Po (tied), used as the steel-iteration gate | ACI 318M-14 PDF, printed p. 557 (part 3, PDF p. 166); first logged A-14 | Verified (owner PDF, printed page) |

Golden vectors (hand-computed, `ACIColumnInteractionTest`): 8Ø20/400×400/fc'=24
at Pu=1500 kN → c=283.31, φ=0.65, φMn=144.52 kNm; same at Pu=2000 kN →
φMn=103.49 kNm (descending branch); 10Ø20 at Pu=1500 kN → φMn=162.22 kNm;
8Ø20 at Pu=400 kN → εt=0.005679, φ=0.90, φMn=190.04 kNm (tension-controlled);
null past φPo. End-to-end: Pu=1500/Mx=150 kNm → 18Ø14; Pu=3000 kN axial → 10Ø29.

**F-08 addendum (same day) — biaxial scope and constructibility.** Review found
the first cut checked each axis independently, which is unconservative for
simultaneous Mx/My (8Ø20, Pu=1500 kN: per-axis allows 144.5 kNm each, true 45°
contour radius = 130.60 kNm). Fixed: `checkBiaxial` scans the neutral-axis
orientation, solves φPn(c)=Pu per direction, and tests the demand vector
against the φM contour (§22.2.1.1 equilibrium at each section; §22.2.4.1
compression zone "bounded by edges of the cross section and a line parallel to
the neutral axis" at a=β1c; §22.2.4.2 c perpendicular to the NA — printed
p. 554, part 3 PDF p. 163, rendered 2026-11-21). Consistency: contour radius
along an axis = the uniaxial solve (144.516 kNm). Bar clear spacing for
columns max(40 mm, 1.5db, (4/3)dagg) per §25.2.3 (printed p. 645, part 4 PDF
p. 54, text-extracted 2026-11-21; the dagg term is inapplicable without an
aggregate-size input). The design loop escalates bar diameter when spacing
binds before declaring the section inadequate. Vectors: Mx=My=80 kNm passes
8Ø20 (113.14 < 130.595); Mx=My=100 fails 8Ø20 and 10Ø20 (140.024), passes
12Ø20 (149.646); end-to-end Pu=1500/Mx=My=100 → 18Ø16 (Ø14 spacing-blocked).

**F-08 addendum 2 (same day) — independent benchmark, convergence, dagg exposure.**
(a) The contour evaluation is verified against an INDEPENDENT closed-form
derivation (analytic polygon geometry by hand, not the clipping code): 4
corner Ø20, 400×400, fc'=24, θ=45°, c=500 mm → φPn = 2 039 460.215 N,
φMnx = φMny = 45 278 623.759 N·mm; the engine reproduces every digit.
(b) The pass/fail test no longer interpolates a scanned contour polygon (which
would have needed a convexity assumption). It matches the moment-vector
direction to the demand direction (360-direction scan + bisection brackets)
and compares magnitudes at the matched orientation; multiple matches take the
LOWEST capacity and ties fail closed — conservative boundary handling.
Convergence: c-bisection 60 iterations over [1e-3, 1e12] mm (|Δc| < 1e-6 mm),
direction error < 1e-15 rad. Verified equivalence: direction-match capacity
at 45° = 130.5950 kNm (scan+polygon agreed), at 90° = 144.5159 kNm = the
uniaxial solve 144.5159.
(c) Aggregate size: no dagg input exists anywhere in the design path (UI →
CalculatorEngine → engine signature carry none; the pricing module's aggregate
entries are material names only). The §25.2.3 (4/3)dagg term is therefore NOT
checked; every result now carries an explicit code note saying the spacing was
checked against max(40 mm, 1.5db) only and the (4/3)dagg term is UNVERIFIED.
Constructibility is not claimed as fully code-checked.

---

**E-BS-1 (2026-11-21) — ECPBeam shear engine rebuilt against ECP 203-2020 printed pages.**
Source of record: owner's ECP 203-2020 PDF (489-page scan), verified by direct
page renders this date (PDF pp.96-102 = printed 4-16..4-22, الباب الرابع).
All page numbers below are the printed page of the PDF of record.

1. **Concrete-only shear threshold** — Eq. [4-29], printed 4-18:
   q_cu(uncracked) = 0.16 × √(fcu/γc). Applied as the capacity below which only
   minimum web reinforcement is required.
2. **Concrete share in stirrup design** — Eq. [4-30] + Eq. [4-33], printed 4-20:
   q_cu(cracked) = 0.12 × √(fcu/γc); q_su = q_u − q_cu(cracked).
   PREVIOUS CODE USED 0.24 × √(fcu/γc) FOR BOTH ROLES, MIS-CITED AS "§4-3-1-2".
   0.24 exists in the code ONLY as Eq. [4-39] (printed 4-22), a cap on the
   bent-up-bars share q_sub — never a concrete shear resistance. The error
   overstated concrete capacity 1.5–2× and understated required stirrups.
3. **Web reinforcement amount** — Eq. [4-34], printed 4-21:
   μ_st = A_st/(b·s) = q_su/(fy/γs) (algebra unchanged; inputs corrected by 2).
4. **Minimum web reinforcement ratio** — Eq. [4-40] + §4-2-2-1-6(أ), printed 4-22:
   μ_min = 0.4/fy, with floors 0.15% (mild, yield 240) / 0.10% (HSS).
   Previous code hardcoded 0.15% for all grades (understated for mild 240 by ~11%).
5. **Stirrup legs for wide beams** — §4-2-2-1-6(ب), printed 4-22: web width
   ≥ 400 mm → at least 4 legs, leg spacing ≤ 250 mm. Previous code always
   assumed 2 legs ("كانة مغلقة = فرعين"), halving shear steel for wide beams.
6. **Web steel yield cap** — §4-2-2-1-6(هـ), printed 4-22: fy taken ≤ 420 MPa
   in stirrup strength. Previous code used full fy (up to 600).
7. **Maximum stirrup spacing** — §4-2-2-1-6(و), printed 4-22: s ≤ 200 mm along
   the member axis (the ≤ d limit in the same item applies to bent bars, which
   this engine does not provide). Previous code already correct (200 mm).
8. **Maximum shear stress** — Eq. [4-27], printed 4-17: q_umax = 0.70√(fcu/γc)
   with an absolute cap of 4.4 MPa. Previous code missed the 4.4 MPa cap.

Golden vectors: ECPBeamShearTest (6 cases: stirrup design, min-only branch,
4-leg wide beam, fy=600 cap, mild-240 min-ratio floor, q_umax 4.4 cap).

---

## SBC 304-CR-2018 joist rebuild batch — `SBCJoistSlab` (accessed 2026-11-22)

Source of record: owner's SBC 304-CR-18 English PDF (284 pp, Edition 200923,
complete, text-extractable + page renders), cross-confirmed against the owner's
ACI 318M-14 bilingual PDF (cover-render-confirmed edition). All SBC page
numbers are the document's printed page. The engine is relabelled
"SBC 304-CR-2018"; edition differences are isolated for a future 2024 upgrade
(user decision 2026-11-22: work on the supplied English 2018 copies now, leave
room for 2024).

| ID | Clause | Source | Status |
|---|---|---|---|
| SJ-1 | One-way joist geometry: §9.8.1.2 rib width ≥ 100 mm; §9.8.1.3 overall rib depth ≤ 3.5 × min width; §9.8.1.4 clear rib spacing ≤ 750 mm; §9.8.1.8 non-conforming joists designed as slabs and beams | SBC 304-CR-18 §9.8, printed p. 72 (rendered 2026-11-22); identical in ACI 318M-14 §9.8.1.2-9.8.1.8, printed pp. 243-244 (rendered 2026-11-22) | **Verified (primary, rendered)** |
| SJ-2 | Topping thickness: §9.8.3.1 (other/removable forms) ≥ greater of 1/12 clear rib distance and 50 mm; §9.8.2.1.1 (structural fillers) ≥ greater of 1/12 clear distance and 40 mm | SBC 304-CR-18 §9.8.2.1.1/§9.8.3.1, printed p. 72 (rendered 2026-11-22) | **Verified (primary, rendered)** |
| SJ-3 | Effective flange width (T-beam): Table 6.3.2.1 — overhang each side of web = least of 8h, sw/2, ln/8 (one side: 6h, sw/2, ln/12) | SBC 304-CR-18 Table 6.3.2.1, printed p. 40 (rendered 2026-11-22) | **Verified (primary, rendered)** |
| SJ-4 | Strength reduction: Table 21.2.1(a) moment/axial 0.65-0.90 per 21.2.2, (b) shear φ = 0.75; Table 21.2.2 tension-controlled φ = 0.90 | SBC 304-CR-18 Tables 21.2.1/21.2.2, printed p. 169 (rendered 2026-11-22) | **Verified (primary, rendered)** |
| SJ-5 | Minimum flexural reinforcement: §9.6.1.2 As,min = greater of (a) 0.25·√fc′/fy·bw·d and (b) 1.4/fy·bw·d; §9.6.1.3 exemption when As provided ≥ 4/3 As required | SBC 304-CR-18 §9.6.1.2/9.6.1.3, printed p. 68 (rendered 2026-11-22) | **Verified (primary, rendered)** |
| SJ-6 | Stress block: §22.2.2.4.1 a = β1·c; Table 22.2.2.4.3 β1 = 0.85 (17 ≤ fc′ ≤ 28), 0.85 − 0.05(fc′−28)/7 (28 < fc′ < 55), 0.65 (fc′ ≥ 55) | SBC 304-CR-18 §22.2.2.4.1 printed p. 172 (rendered 2026-11-22); Table 22.2.2.4.3 printed p. 173 (text layer; identical ACI 318M-14 table render-verified printed p. 554) | **Verified (primary)** |
| SJ-7 | One-way shear Vc: §9.8.1.5 permits Vc = 1.1 × the §22.5 value (printed p. 72, rendered) — BUT the base Eq (22.5.5.1) printed p. 174 (rendered) carries the content "Vu ≤ φ(Vc + 0.66√fc′ bw d)", a duplicate of Eq (22.5.1.2) that is circular as a definition of Vc. Independent SBC copy shows the same duplication (publication-level artifact hypothesis). SBC's own Table 22.5.5.1 (printed p. 182) matches ACI 318M-14's detailed method, whose simple-equation counterpart in ACI 318M-14 §22.5.5.1 (printed p. 563) is Vc = 0.17·λ·√fc′·bw·d | SBC 304-CR-18 pp. 72/174/182 (rendered); ACI 318M-14 §22.5.5.1 (dump + form-feed map); no official SBCNC erratum found | **UNVERIFIED — discrepancy held. Engine carries the shear check as BLOCKED: no capacity computed, no joist 1.1 factor applied, result never a design pass. Awaiting official erratum or owner decision; NOT resolved by substitution** |
| SJ-8 | Concrete strength basis: SBC 304-CR-18 is specified on cylinder strength fc′. Engine input is cube strength fcu (UI semantics preserved); engine converts fc′ = 0.8·fcu | Engine input contract; conversion is an approximation, not a code clause | **UNVERIFIED — approximation flagged in every result; user should enter the cylinder-equivalent strength** |
| SJ-9 | Detailing notes carried as code notes (not computed): §9.8.1.6 ≥ 1 continuous bottom bar per joist developed at supports; §9.8.1.7 perpendicular reinforcement per §24.4 | SBC 304-CR-18 §9.8.1.6/9.8.1.7, printed p. 72 (rendered 2026-11-22) | **Verified (primary, rendered)** |

Replaces the previous fabricated attributions ("SBC 304-2024 §8.4/§4.2/§4.3",
ECP-style γc/γs partial factors, 0.25 shear coefficient, spacing-ratio shear
enhancement, 700 mm spacing limit, "§8.4.4" min-steel pattern), which are
removed wholesale. Golden vectors cover geometry, flexure, min-steel and the
blocked-shear semantics only; no shear-capacity vector exists while SJ-7 is
held.
