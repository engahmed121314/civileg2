# ECP 203-2025 deflection evidence (chapter 4, clause 4-3-1)

Source: supplied scan of ECP 203-2025 (445 pages). PDF page = printed page + 80 for chapter 4; chapter 6 PDF page = printed page + 191 (checked: PDF 200 = 6-9, PDF 207 = 6-16).
Method: every page below was viewed as an image and the equations were transcribed by eye (no Arabic OCR available). Page renders are attached with the patch (ecp-p4-60.png ... ecp-p6-18.png).
Build status: NOT compiled or run (no Kotlin toolchain in the authoring environment). Lead must run CI.

| Item | Printed page | PDF page | Transcription | Code |
|---|---|---|---|---|
| Eq 4-70 Ie | 4-60 | 140 | Ie = (Mcr/Ma)^3 Ig + [1-(Mcr/Ma)^3] Icr | effectiveInertia |
| Eq 4-71-a Mcr | 4-61 | 141 | Mcr = fctr Ig / yt | crackingMoment |
| Eq 4-71-b/c fctr | 4-61 | 141 | 0.6 sqrt(fcu) for "fy <= 500"; 0.37 sqrt(fcu) for fy > 500 (condition printed on fy, as is) | fctr |
| Icr cap | 4-61 | 141 | "Icr ... not to exceed Ig" | min(icr, ig) |
| Eq 4-72 (two forms) | 4-61 | 141 | 0.70 Ie,mid + 0.15(Ie1+Ie2) both ends continuous; 0.85 Ie,mid + 0.15 Ie1 one end. Both printed as "4-72-a" | continuousInertia* |
| Eq 4-73 | 4-62 | 142 | alpha = xi/(1+50 rho'); xi = 1.0 (3 mo), 1.2 (6 mo), 1.4 (12 mo), 2.0 (5 yr+); Fig 4-31 curve | longTermMultiplier, timeFactorXi |
| Eq 4-74 | 4-63 | 143 | L/250 beams, one- and two-way slabs | Limit |
| Eq 4-75 | 4-63 | 143 | L/450 cantilevers | Limit |
| Eq 4-76, 4-77 | 4-63 | 143 | live-load immediate L/360; dL = d(D+L) - dD | Limit, liveImmediate |
| Eq 4-78 | 4-64 | 144 | L/480 added deflection, elements likely damaged | Limit |
| Eq 4-79 | 4-64 | 144 | L/240 elements not likely damaged | Limit |
| Eq 4-80 | 4-64 | 144 | dIncr = [dD+dLs][(a1-a2)] + [(dL-dLs)] (brackets unbalanced in print) | incrementalDeflection |
| L definition | 4-64 | 144 | L = span; short span for two-way slabs, LONG span for flat slabs | doc only |
| Table 4-10, Eq 4-81 | 4-65 | 145 | span/depth table; fy other than 420 (<=500): table values divided ("qisma") by 0.40+fy/700 | spanToDepthRatio |
| Eq 4-82 | 4-66 | 146 | t = a(0.85+fy/1600) / (15 + 25/(b/a) + 10 betaP), t >= 100 mm; slabs on stiff beams | twoWaySlabOnBeamsMinThickness |
| Table 6-3 (flat slab min thickness) | 6-18 | 209 | already in ECPFlatSlab | unchanged |

## Findings that differ from the brief
1. No column-strip / middle-strip flat slab deflection method exists on pp. 4-60..4-66. Eq 4-82 (p.4-66) is "two-way slabs supported on stiff beams", not flat slabs. The chapter 6 table of contents (flat slabs, 6-2-5: general, dimension limits, analysis, frames, simplified analysis, edge beam loads) lists no deflection item; pages 6-19 onward were not read in full. So the flat slab deflection VALUE is NOT ASSESSED, and the engine keeps `deflectionAssessed = false`.
2. p.4-65 item b says table values are divided by Eq 4-81. ACI 318 multiplies. Implemented as printed; for fy > 420 this lowers the allowed ratio (conservative), for fy < 420 it raises it. Needs the lead's decision or a second reading.
3. Eq 4-71-b/c: condition printed on fy while the formula uses fcu. Implemented as printed.
4. Fig 4-31 curve is not digitised: only the four listed xi values; others return null.
5. Eq 4-72 appears twice with the same label.

## NOT ASSESSED
Flat slab deflection value (strip method), Ie for flat slab strips (section properties), Fig 4-31 intermediate xi, Fig 4-32 (T-beam factor delta, p.4-65, not read in detail), 4-3-1-3 items a-e conditions beyond those coded, cracking limit state 4-3-2 (p.4-66 onward).
