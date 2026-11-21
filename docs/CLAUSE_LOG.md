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
