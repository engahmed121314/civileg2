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
