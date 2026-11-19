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
