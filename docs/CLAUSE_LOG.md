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
alpha-curve shape remain as-is pending engineering decisions (U-P1, U-P2).

## Decision batch — pile alpha curve + group efficiency (accessed 2026-11-21)

Decision on U-P1/U-P2 (from the ACIPileFoundation batch):
apply the correct and most accurate engineering option for both.

| ID | Clause / value | Source (accessed 2026-11-21) | Status |
|---|---|---|---|
| P-06 | API RP 2A-WSD §6.4.2 alpha method as a continuous curve: `α = 0.5·ψ^-0.5` (ψ ≤ 1), `α = 0.5·ψ^-0.25` (ψ > 1), `α ≤ 1.0`, ψ = cu/p'0; applied with the engine's implicit p'0 = 100 kPa. Replaces the banded curve that jumped 0.75 → 0.5 at cu = 50 kPa (U-P1, RESOLVED). Continuous at cu = 25 (α = 1.0) and cu = 100 (α = 0.5). | OpenBrIM API RP 2A-WSD alpha method page (opened earlier today, this session) | Verified (secondary) |
| P-07 | Standard Converse-Labarre group efficiency: `η = 1 − θ·[(m−1)·nc + (nc−1)·m] / (90·m·nc)`, θ = atan(d/s) in degrees. Replaces the engine variant (atan(s/d), single term; U-P2, RESOLVED). 3×3 @ s=3d: η = 0.7269 (was 0.6466). | [FireCAD — Pile Group Efficiency (Converse–Labarre)](https://calc.firecad.com/civil/foundations/pile-group-efficiency); [Converse-Labarre calculator reference](https://calculatgeek.com/pile-group-efficiency-calculator-converse-labarre/) | Verified (secondary; textbook-standard form) |

Behavior changes (intended):
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
| U-A1 | Welded tension: engine returns a block-shear "placeholder" `0.75·0.6·Fy·Ag` that GOVERNS the design (cuts welded tension capacity ~50%). AISC J4.3 has no block-shear limit state for full welded connections | Engine inspection | **FLAGGED, not fixed** — conservative direction; deferred engineering decision |
| U-A2 | Torsional buckling (E4) branch selection uses the FLEXURAL slenderness for the E3-3/E3-4 choice and `min(KxLx, KyLy)` as the torsional length instead of Kz·Lz | Engine inspection | **FLAGGED, not fixed** — pinned by characterization test |
| U-A3 | Plastic modulus `Zx = 1.12·Sx` (shape-factor approximation) for I-sections | Engine constant | Approximation, characterized; within typical rolled-shape range |

Golden vectors: A-01/A-02 (tension bolted, net rupture governs), A-03
(RHS inelastic + I-section elastic), A-04 block-shear arithmetic. Torsional
path and welded placeholder pinned as characterization (U-A1/U-A2).
No production formula was added or changed in this batch.

## Decision batch — AISC steel corrections (accessed 2026-11-21)

Applied the engineering-correct fix for all three AISC findings
from the member-design batch (standing rule: fix correctly, document,
separate PR, nothing merges without his OK).

| ID | Clause / value | Source (accessed 2026-11-21) | Status |
|---|---|---|---|
| A-06 | AISC 360-16 J4.3 block shear applies to BOLTED (holed) connections; welded and pressed connections have no block-shear limit state. The placeholder `0.75·0.6·Fy·Ag` that governed welded tension is removed; those paths now return +infinity (excluded from the governing min). Welded tension capacity rises ~2x to the D2-1/D2-2 value. U-A1 RESOLVED. | [ideCAD — Steel Member Design per AISC 360-16](https://help.idecad.com/ideCAD/steel-column-design-aisc-360-16) (J4.3 scope, secondary) | Verified (secondary) |
| A-07 | AISC 360-16 E4: Fcr for torsional/flexural-torsional buckling comes from the E3 equations applied to the TORSIONAL elastic stress Fe, with the inelastic/elastic branch at Fy/Fe = 2.25. The branch no longer keys on the flexural slenderness. U-A2 RESOLVED. Remaining simplification: L = min(KxLx, KyLy) pending a Kz·Lz input (U-A2a, conservative). | [ideCAD — Flexural Buckling per AISC 360-16](https://help.idecad.com/ideCAD/flexural-buckling) | Verified (secondary) |
| A-08 | Exact plastic section modulus replaces the 1.12·Sx approximation: I-shape `Zx = bf·tf·(h−tf) + tw·(h−2tf)²/4`; CHS/Pipe `(D³−(D−2t)³)/6`; RHS `(w·h²−(w−2t)(h−2t)²)/4`. Singly symmetric shapes (C/L/T/PG) keep shape factors pending plastic-NA work. U-A3 RESOLVED for I/CHS/RHS/Pipe. | Standard strength-of-materials exact forms (Zx of I 300x150x10x7 = 572,200 mm³, shape factor 1.131 vs the old flat 1.12) | Verified (textbook) |

Behavior changes (intended): welded/pressed tension capacity
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

## Decision batch - ECPSlab two-way bending coefficients (accessed 2026-11-21)

Replacement of the legacy simplified two-way slab moment table (BS 8110 / IS 456
family values mislabeled as ECP) with the verified ECP 203-2020 method. Source:
the official Arabic ECP 203-2020 PDF
on 2026-11-21. Every row below was read directly from that PDF and is VERIFIED
(primary source, first-party code text).

| ID | Clause / value | Source (accessed 2026-11-21) | Status |
|---|---|---|---|
| TW-01 | ECP 203-2020 §6-1-2-6-3 (simplified method, two-way solid slabs, uniform loads): `M_a = alpha·w·a²/d`, `M_b = beta·w·b²/d`; d = 8 span simply supported both ends, d = 10 continuous one end, d = 12 continuous both ends [Eq. 6-7a/b/c] | ECP 203-2020 PDF, printed page 6-9 (PDF p. 242) | Verified (primary) |
| TW-02 | Table 6-1 (slabs cast monolithically with beams, LL ≤ 5 kN/m²): alpha = 0.35, 0.40, 0.45, 0.50, 0.55, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85 and beta = 0.35, 0.29, 0.25, 0.21, 0.18, 0.16, 0.14, 0.12, 0.11, 0.09, 0.08 for r = 1.0..2.0; closed form Eq. 6-8: `alpha = 0.5r − 0.15`, `beta = 0.35/r²` | Same PDF, printed page 6-9 | Verified (primary) |
| TW-03 | Method scope (§6-1-2-6-3 + §6-2-1-6-1): rectangular panels supported on four sides, cast monolithically with beams, rectangularity ratio r ≤ 2 (r > 2 → design one-way); LL > 5 kN/m² uses Table 6-3 (not yet implemented) | Same PDF, printed pages 6-8/6-9 | Verified (primary) |
| TW-04 | Wall-supported variant: Table 6-1(2a) alpha 0.396..0.849, beta 0.396..0.053 (not yet implemented; engine assumes monolithic-with-beams) | Same PDF, printed page 6-10 (PDF p. 243) | Verified (primary), implementation deferred |
| TW-05 | Negative support moment between adjacent panels: `M_c = (M1·L1 + M2·L2)/(L1 + L2)` [Eq. 6-9]; single-panel scope: negative coefficient equals the continuous-span positive coefficient, zero for simply supported spans | Same PDF, printed page 6-10 | Verified (primary); single-panel mapping is an engine convention, stated in code comments |

Removed provenance: the previous table ("جداول مبسطة من الكود المصري (قريبة من قيم
ماركوس)") and the advanced-quality-fixes branch's 8-case table labeled "ECP 203
جدول 6-1" are BS 8110 Table 3.13 / IS 456 Table 26 family values (interior-panel
0.031/0.024 at r = 1.0 fingerprint), not ECP 203. Decision 2026-11-21:
forget the Indian code and remove everything related to it; ECP 203-2020 and
SBC PDFs are the references of record, ACI PDF to follow.

## ACI 318M-14 primary verification batch — ACIPileFoundation structural clauses (accessed 2026-11-21)

Source of record: the ACI 318M-14 Arabic/English edition (4 PDF parts,
801 pages, accessed 2026-11-21). Printed page numbers below are the
code's own page footers; the PDF part/page locators point into the PDF parts.
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

Shared checks verified against the ACI 318M-14 PDF (entries A-01..A-06 apply here too):
punching uses b0 = 2(c1+c2)+4d (perimeter at d/2, §22.6.4.1), vc = min of the
three Table 22.6.5.2 cases with αs = 40, one-way Vc = 0.17·√f'c·b·d
(§22.5.5.1), φshear = 0.75 / φflex = 0.90 (Table 21.2.1).

| ID | Finding | Evidence | Status |
|---|---|---|---|
| CF-01 | Longitudinal bending uses the simply-supported simplification M = qu·B·L²/8. ACI 318M-14 §13.2.6.2 permits any procedure satisfying equilibrium and geometric compatibility; this form overstates mid-span sagging (≈ wL²/8 vs wL²/8 − P·S/2 for symmetric loads) but never designs top (hogging) reinforcement at the columns, and computes no transverse reinforcement under each column (beam-strip action). | Engine code; ACI 318M-14 §13.2.6.2, printed p. 307 (part 2, PDF p. 116) | **FLAGGED, not fixed** — needs an engineering decision (invert-beam shear/moment diagram + transverse strips is the recommended correct method) |
| CF-02 | One-way shear check omits the xResultant offset in the shear span (overestimates Vu — conservative direction) and never checks the inner face of column 2. | Engine code (Step 9) | **FLAGGED, not fixed** — bundle with CF-01 |
| CF-03 | The eccentricity/trapezoid block is dead code: the footing centroid is placed at the load resultant by construction, so e ≡ 0 and maxSoilPressure always equals soilPressure. Reported max/min pressures are misleading. | Engine code (Steps 2–4) | **FLAGGED, not fixed** — cosmetic/reporting |

Existing test coverage: ACIFootingTest covers designCombinedFooting with a
smoke test only (valid inputs produce a result; zero distance throws). No
golden vectors pin combined-footing numbers; a fix for CF-01..CF-03 should
land with golden vectors per R4.

No production formula was added or changed in this batch.
## ACI footing min-steel fix — F-01 (accessed 2026-11-21)

Found while building the combined-footing golden vectors: the shared ACI
footing flexure helper's minimum-steel formula matched no ACI provision.

| ID | Clause / value | Source (accessed 2026-11-21) | Status |
|---|---|---|---|
| F-01 | ACI 318M-14 Table 7.6.1.1: As,min for nonprestressed one-way slabs and footings on the GROSS area Ag = b·h: deformed bars fy < 420 MPa → 0.0020·Ag; fy ≥ 420 → greater of 0.0018·420/fy·Ag and 0.0014·Ag. The helper's previous `rhoMin = max(0.0018, 1.33·√f'c/fy)` matches no ACI provision (likely a garbled reading of the 4/3 relaxation in §9.6.1.3) and produced a 1.63% "minimum" at f'c = 24, fy = 400 — about 8× the code value, inflating steel in every ACI footing result. Fixed to the table values on b·h; internal design flows now pass the real footing depth (the interface path keeps h = d). | ACI 318M-14, Table 7.6.1.1, printed p. 143 (part 1, PDF p. 152) | **Verified (primary), RESOLVED** |
| F-02 | `SBCFooting` carries the same `max(0.0018, 1.33·√f'c/fy)` formula (line ~274). Not fixed here: SBC 304's own min-steel clause must be verified against the SBC PDF first, and the SBC batch awaits a decision (U-S1..U-S5). | Engine inspection | **FLAGGED, not fixed** |

## ACI combined footing rebuild (accessed 2026-11-21)

Decision 2026-11-21: rebuild
`ACIFooting.designCombinedFooting` with the inverted-beam method, transverse
beam strips, full one-way shear checks, and removal of the dead eccentricity
block. The shared min-steel fix it depends on is F-01 (previous commit).

| ID | Clause / value | Source (accessed 2026-11-21) | Status |
|---|---|---|---|
| CF-01 | RESOLVED: longitudinal bending now uses the inverted-beam model (upward factored pressure, column point loads), with critical sections at column faces per Table 13.2.7.1 and the zero-shear point between columns. Both sagging (bottom) and hogging (top) reinforcement are designed; transverse beam strips under each column (strip width = column length + d, cantilever (B−c)/2) are designed per the classic combined-footing method. Also removes the old total-moment-passed-as-per-meter conversion error. | ACI 318M-14 §13.2.6.2 + Table 13.2.7.1, printed p. 307 (verified primary, A-05); transverse strip method: Das, Principles of Foundation Engineering (secondary, textbook-standard) | **RESOLVED** with golden vectors |
| CF-02 | RESOLVED: one-way shear now checked at d from each column face on all four sections (two outer cantilevers + both inner faces), Vu vs φVc = 0.75·0.17·√f'c·B·d (§22.5.5.1, verified A-02). | ACI 318M-14 §13.2.7.2, printed p. 307 | **RESOLVED** with golden vectors |
| CF-03 | RESOLVED: dead eccentricity/trapezoid block removed; pressure is uniform by construction (centroid at resultant) and reported max = average. | Engine code | **RESOLVED** |
| CF-05 | New finding during the rebuild: the old L = 2·(xR + s1) places column 2 OFF the footing whenever P1 > P2 (xR < S/2). Fixed: L = 2·max(xR + s1, S − xR + s2). | Engine inspection + golden vector B | **RESOLVED** |

Golden guard: ACIFootingTest `designCombinedFooting_goldenVector_symmetric`
(P1 = P2 = 1400 kN, S = 3 m: L = 3700, B = 2750, q = 196.56 kPa, M+ = 805.0
kN·m, bottom/top at the 1400 mm²/m Table 7.6.1.1 minimum, transverse strips
strength-governed at 1604.9 mm²/m, Vu = 518.4 ≤ 1056.4 kN) and
`designCombinedFooting_goldenVector_unequalLoads` (P1 = 2000, P2 = 900 kN:
L = 5665.5 covers column 2 — CF-05 — M− = −549.7 kN·m hogging at column 1).

## ACI isolated footing batch (accessed 2026-11-21)

| ID | Clause / value | Source (accessed 2026-11-21) | Status |
|---|---|---|---|
| A-07 | §13.3.3 two-way isolated footings: square → uniform both directions; rectangular → long-direction uniform, short-direction portion γs = 2/(β+1) of As in the central band of width = short side, remainder outside (β = L/B). | ACI 318M-14 §13.3.3.2–13.3.3.3, printed pp. 310–311 (part 2, PDF pp. ~119–120) | **Verified (primary)** |
| F-03 | `designIsolatedFooting` evaluates one-way shear at d/2 from the column face (comment cites §22.5) instead of d (§13.2.7.2 → §7.4.3.1/§22.5). The checked demand is LARGER than the code's, so the deviation is conservative. Characterized; relaxation to d needs a decision. | Engine inspection vs verified §13.2.7.2 (A-05) | **FLAGGED (conservative), not fixed** |
| F-04 | `designIsolatedFooting` adds "distribution steel = 20% of main" citing "ACI 318-13.3.4". No such rule exists: §13.3.4 covers combined/mat foundations, and the real distribution rule is the §13.3.3.3(b) band rule (A-07). Affects reporting only (codeNotes); main reinforcement and safety checks are unaffected. | Engine inspection vs verified §13.3.3/§13.3.4 | **FLAGGED, not fixed — replace with the band rule (deferred)** |
| F-05 | `designRaftFoundation` is a crude placeholder: max column load estimated as 15% of total, design moment = q·(√A/3)²/10 (no clause basis), punching checked for a guessed 600×600 column. §13.3.4.3 requires pressure distribution consistent with factored loads; a real raft needs a grid/strip method. | Engine inspection; ACI 318M-14 §13.3.4.1–13.3.4.3, printed p. 311 | **FLAGGED, not fixed — raft needs a proper method decision** |

Golden guard: ACIFootingTest `designIsolatedFooting_goldenVector_squareNoMoment`
(P = 1500 kN, 400×400 column, qa = 200, h = 600: B = L = 2450 mm, q = 178.50
kPa, Mu = 321.62 kN·m, As = 1200 mm²/m at the Table 7.6.1.1 minimum,
Vu = 469.9 ≤ 719.4 kN, punching φVn = 2086.3 kN).
## F-03 — Isolated footing one-way shear checked at d/2 instead of d (RESOLVED)
- **Date**: 2026-11-21 (19:53 EET) — relaxation approved.
- **Clause**: ACI 318M-14 §13.2.7.1 + Table 13.2.7.1 (VERIFIED printed p.307, part 2 PDF p.116, A-02): one-way shear critical section for footings at distance d from the column face. Engine used d/2 (more demanding, safe direction, but rejects valid designs).
- **Fix**: critical section moved to d in both directions.
- **Golden vector**: fcu=30, fy=400, col 400x900, P=2000 kN factored, SBC=200, h=500 → B=2300, L=3400, d=415: Vu_y@d = 491.2 ≤ φVc = 596.2 (passes); at the old d/2 section Vu_y = 613.2 would have failed. Test locks the relaxed result.

## F-04 — "20% distribution steel" cited nonexistent ACI rule (RESOLVED)
- **Date**: 2026-11-21 (19:53 EET) — replacement approved.
- **Old code**: comment cited "ACI 318-13.3.4: no less than 20% of main reinforcement" — §13.3.4 contains no such rule (verified); added 20% of main steel as extra "distribution" bars.
- **Real rule**: ACI 318M-14 §13.3.3.3 (VERIFIED printed pp.310-311, A-07): (a) long-direction reinforcement uniform across full width; (b) short-direction reinforcement: γs = 2/(β+1) of the total concentrated in a central band whose width equals the short side; remainder (1−γs) uniform outside the band.
- **Fix**: 20% block removed; band-rule distribution computed and detailed (band bars, spacing, outside-band area in codeNotes).
- **Golden vector**: same case as F-03 → β=1.4783, γs=0.8070, band width=2300 mm; short-dir steel min-governed 1000 mm²/m → total 3400; band 1192.9 mm²/m → 11Ø12 @ 100mm; outside band 596 mm²/m.

### F-05 — ACI raft foundation rebuild: method decision (2026-11-21, before code per R4)

**Finding:** `ACIFooting.designRaftFoundation` assumes uniform pressure
(total/area, no eccentricity), checks punching against a fabricated 600×600
column carrying 15% of the total load, and sizes bending from
q·(√A/3)²/10 with no code or mechanics basis. Rebuild approved
with a column-layout overload (2026-11-21).

**Method decision (engineering choice, authorized by §13.2.6.2):** rigid-raft
conventional method.
1. Geometry from the actual column layout plus a uniform edge projection.
2. Elastic pressure field under the rigid raft: q(x,y) = P/A ± Mx·y/Ix ±
   My·x/Iy (strength of materials — not a code clause; equilibrium-based,
   permitted because §13.2.6.2 allows "any procedure" satisfying equilibrium
   and geometric compatibility). Bearing check at SERVICE loads against qa.
3. Inverted-slab strips: each column-line strip is a continuous beam over the
   column-line supports carrying the tributary average FACTORED pressure;
   elastic moments/shears by the three-moment equation (exact for prismatic
   equal-section strips — equilibrium + compatibility). SIGN/FACE CONVENTION
   (re-derived 2026-11-21 after review caught a reversal): the strip carries
   UPWARD soil pressure and is held down at the column lines, so its moment
   diagram is the negative of a downward-loaded continuous beam — HOGGING at
   midspan (TOP steel) and SAGGING at the column lines (BOTTOM steel). This
   holds under the rigid/uniform-pressure assumption; pressure concentration
   near columns under an elastic bed is outside the chosen method. Moment
   critical section at the support face per Table 13.2.7.1 (already
   verified, entry A-05, printed p. 307);
   one-way shear at d from the support face per §22.5.5.1 (verified A-02,
   printed p. 563) and §13.2.7.2 (verified A-05).
4. Punching per ACTUAL column: Vu = column factored load − average factored
   pressure × area inside the critical perimeter; b0 per §22.6.4.1 whose
   "perimeter bo is a minimum" rule reduces the perimeter at free raft edges
   for edge/corner columns (§22.6.4.1 printed p. 575, part 3 PDF p. 184,
   text-extracted 2026-11-21; free-edge critical sections also shown in
   R22.6.4.3 Fig.); αs = 40/30/20 by interior/edge/corner position per
   §22.6.5.3 (verified A-03, printed p. 580); vc = least of Table 22.6.5.2
   (verified A-03, printed p. 579).
5. Flexural design reuses calculateFootingReinforcement (§9.6.1.2 minimum —
   the engine's documented conservative choice from entry A-06).

**Open verification:** §13.3.1 (minimum reinforcement scope for footings/rafts)
still to be opened before the strip min-steel rule is pinned; the existing
§9.6.1.2 engine choice carries over until then. Golden vectors planned: 2×2
symmetric grid (closed-form pressure + strip moments via three-moment) and
2×3 eccentric grid.


**Applicability limits (added same review):** the rigid-raft method requires
full soil contact — qmin >= 0 over the whole raft (eccentricities within the
kern: |ex| <= Lx/6, |ey| <= Ly/6 for a rectangular raft). If qmin < 0 the
result is returned with isSafe=false and an explicit out-of-scope warning
(uplift/partial contact is outside the rigid method), not silently designed
with tension "pressure". Non-rectangular plans, openings, and strongly
irregular column layouts are outside the strip idealization and rejected with
an explicit error. Raft rigidity itself (relative raft-soil stiffness) is not
a 318 provision and is stated as a method assumption, not claimed as
code-verified. Every vector will include an independent equilibrium/benchmark
check (hand-derived, not a twin of the implementation), and the engine will
assert global equilibrium: strip reactions must sum to the column loads and
the pressure field resultants must reproduce P, Mx, My.

**F-05 clause verification (2026-11-21, before code):**

| ID | Clause / value | Source (accessed 2026-11-21) | Status |
|---|---|---|---|
| A-20a | §13.3.1.1 base area from UNFACTORED forces/moments and permissible bearing pressure; §13.3.1.2 effective depth of bottom reinforcement ≥ 150 mm | ACI 318M-14, printed p. 309 (part 2, PDF p. 118), text-extracted | Verified (PDF) |
| A-20b | §13.3.4.1 mat foundations per §13.3.4 + applicable Chapter 8 provisions (ACI 336.2R noted in commentary, not opened); §13.3.4.2 the DIRECT DESIGN METHOD of §8.10 shall NOT be used for mats — the chosen strip method is an elastic (three-moment) analysis, not DDM, so it is compliant; §13.3.4.3 bearing-pressure distribution consistent with soil properties and established soil/rock mechanics principles (rigid uniform/linear field is the limiting simple case; R8.10.2.5 notes a frame analysis should be performed even with uniform reaction — the elastic strip analysis satisfies this) | ACI 318M-14, printed pp. 311 and 195 (part 2, PDF pp. 120 and 4), text-extracted | Verified (PDF) |
| A-20c | §13.3.4.4: minimum reinforcement in nonprestressed MAT foundations per §8.6.1.1 — NOT the beam minimum §9.6.1.2 used elsewhere in the engine; §8.6.1.1: As,min near the tension face per Table 8.6.1.1 | ACI 318M-14, printed p. 312 (part 2, PDF p. 121), text-extracted; §8.6.1.1 printed p. 172 (part 1, PDF p. 181), text-extracted | Verified (PDF) |
| A-20d | Table 8.6.1.1: fy < 420 → As,min = 0.0020·Ag; fy ≥ 420 → greater of (0.0018×420/fy)·Ag and 0.0014·Ag | ACI 318M-14, printed p. 173 (part 1, PDF p. 182), RENDERED (image-only table) | Verified (PDF, printed page) |
| A-20e | R13.3.4.4 (commentary): consider continuous reinforcement in each direction near BOTH faces of mat foundations | ACI 318M-14, printed p. 312 (part 2, PDF p. 121), text-extracted | Verified (PDF commentary) |

**F-05 vectors (hand-derived before implementation):** V1 — 2×2 symmetric grid
(400×400 columns at (0,0)/(6000,0)/(0,6000)/(6000,6000), service 1200 kN,
factored 1500 kN each, edge projection 1000 mm, t=500 mm, fc'=24, fy=400,
qa=200 kPa): raft 8400×8400, q_service=68.03 kPa, q_u=85.03 kPa; strip
w=357.14 kN/m, overhang a=1200, span 6000 → beam support moment −257.14 kNm
(raft +257.14, BOTTOM steel at column lines), beam midspan +1350.00 kNm
(raft −1350.00, TOP steel at midspan); reaction 1500 kN = column factored
load (equilibrium self-check); punching b0=3260 mm, Vu=1443.5 kN,
vc governing case (a) 1.617 MPa, φVc=1640.3 kN, util=0.880; one-way shear at
d=415 from face: Vu=852.2 kN vs φVc=1088.5 kN; As top governed by min
0.0020·Ag (Table 8.6.1.1, fy=400<420) vs required — computed in test. V2 — 2×3
eccentric grid: derived in the test source comments.

### F-05 verification addendum (2026-11-21, after review feedback)

Review-driven hardening of the raft implementation (no new clauses opened; these are
method-integrity measures on top of A-20a..e):

1. **Sign/face convention re-derived from first principles.** A raft strip carries
   UPWARD soil pressure, so its moment diagram is the negative of a downward-loaded
   beam: raft TOP steel in spans, BOTTOM steel at column lines. An early draft had the
   beam convention carried over unchanged; corrected before implementation review.
2. **Applicability limits (fail closed).** Full contact required (qmin >= 0 at all
   corners; uplift returns an explicit out-of-scope failure); COMPLETE rectangular
   column grid required (a column at every grid intersection — a missing intersection
   breaks the continuous-strip support assumption and is rejected, not averaged over);
   single-line layouts rejected toward combined footing; rigidity is an ASSUMPTION of
   §13.2.6.2 and is surfaced as a standing warning (no deflection/SSI check exists;
   flexible mats need Winkler/FE — not implemented).
3. **Load provenance.** The caller supplies both service and factored loads; the
   combination is not verifiable by the engine. Swapped values fail closed
   (factored >= service required per column); ratio > 2.0 warns.
4. **Pressure sampling.** Strip line loads are sampled per SPAN midpoint (and per
   overhang midpoint), not as one strip average, so eccentric pressure variation
   ALONG a strip reaches the strip moments. Across the strip, sampling is at the
   tributary-band centroid (exact for a linear field).
5. **Force AND moment equilibrium (runtime self-check).** Force: strip reactions sum
   to the factored column loads (exact). Moment: each strip family's reaction first
   moment must reproduce the first moment of its sampled piecewise-uniform load
   (verified exact to 1e-9); equivalently, reactions + the analytic discretization
   term (uniform-per-span sampling drops w'·len³/12 of first moment per segment)
   reproduce the applied first moment sum(Pu·x) / sum(Pu·y) exactly. Both identities
   are checked at runtime with 0.5% tolerance; failure marks the result unsafe.
6. **Discretization smoothing is reported, not hidden.** The discretization term is
   exposed on the result (momentDiscretizationX/Y, kN·m) and a warning fires when it
   exceeds 5% of the applied eccentric moment (V2: 934.9 kN·m = 11.7% of Mx=8000).
   Design moments already use per-span sampling; the warning advises finer strips or
   Winkler/FE for strong eccentricity.
7. **V2 vector re-based at t = 600 mm.** At t = 500 the heavy edge X-strip fails
   wide-beam shear (Vu = 877 kN vs φVc = 830 kN) — a genuine design outcome, not a
   solver artifact. The failure path remains covered by V3 (corner punching + shear).
8. **Benchmark independence.** V1 strip moments, punching and equilibrium were
   cross-checked by closed-form hand arithmetic (values in the F-05 vector note);
   the three-moment/tridiagonal solver is additionally pinned by the exact force and
   moment identities above, which do not share code with the solver's moment output.

### F-05 verification addendum — REVISION (2026-11-21, supersedes items 4-6 above)

Review found that uniform-per-span sampling only equilibrates a SMOOTHED load (V2: 11.7%
of the eccentric moment Mx never reached the strips) and that reporting the analytic
correction term is bookkeeping, not physics. Replaced with **exact trapezoidal strip
loads**: pressure is sampled AT each support line and raft edge (band centroid across
the strip), and the three-moment equation uses the exact linear-load RHS
-[Lj^3(8w0+7w1) + Lk^3(7w1+8w2)]/60 with trapezoidal end shears L(2w1+w2)/6, L(w1+2w2)/6
and overhang fixed moments a^2(2w_edge + w_support)/6. For the linear pressure field the
strip loads are then EXACT integrals of the field, and the field equilibrates the applied
loads by construction, so **force and moment equilibrium are exact by construction** (V2:
reacted = applied = 40000.0000 kN.m, diff 1e-9) with no correction term and no smoothing
to disclose. The discretization metric/warning is removed. Formula verification:
reduction to the uniform-load closed form (M = -wL^2/8 two-span, -wL^2/12 fixed ends),
the known fixed-fixed triangular-load reactions (3wL/20, 7wL/20), and an independent
Mohr-integral (force method) numerical solution of a 3-support trapezoidal case
(-400.0000 kN.m both routes). One-way shear at d from the support face now subtracts the
exactly integrated local trapezoidal load. Remaining documented limitations (unchanged):
rigid-mat assumption (standing warning, no deflection/SSI check), complete rectangular
grid required, load provenance caller-supplied, no settlement analysis, ACI 336.2R never
opened.
