# ECP 203-2025 flat slab: drop panel, column head, stiffness, punching (evidence)
Chapter 4: PDF page = printed + 80. Chapter 6: PDF page = printed + 191. Renders in docs/evidence/img/ecp-pdfNNN.png.

| Item | Printed page (PDF) | Read from the page image |
|---|---|---|
| Punching sections, Eq 4-44 | 4-26 (106) | d/2 from the load, and at the perimeter of the thickness change (drop panel). Fig 4-13 on 4-27 (107) |
| Eq 4-46-a/b/c, 1.70 N/mm2 cap | 4-28 (108) | transcribed in ECPFlatSlab and the new qcup() |
| Eq 4-47, qup-max = 0.45 sqrt(fcu/gc) | 4-28/4-29 (108, 109) | stud reinforcement; reinforced zone and fig 4-14 |
| Fig 6-6 critical sections (4 cases: no drop/drop x no head/head) | 6-17 (208) | d/2 sections, 90 deg cone lines, "D+d" and drop width labels. Read visually only |
| Table 6-3 min thickness | 6-18 (209) | by fy 350/420/500/600/700; thickness must also meet 4-3-1-1 |
| Column head | 6-18 (209) | item 1: slope <= 45 deg from vertical; item 2: effective D <= quarter of shorter span, D = largest circle in column/head section |
| Column min size | 6-18 (209) | not less than 1/20 of span, 1/15 storey height, 300 mm (waivable by exact calc per 6-2-5-7) |
| Drop panel | 6-19 (210) | 6-2-5-2-d: thickness below slab >= t/4; extends from column axis >= 1/6 of longer span in that direction, <= 1/4 of shorter span |
| Strips | 6-19 (210), Fig 6-7 6-20 (211) | column strip = half width of shorter span, or drop width if drop; middle strip = the rest |
| Frame method | 6-21..6-22 (212, 213) | 6-2-5-4: frame width = bay width; vertical loads gross width; lateral loads width = column + 3 t (or 1/3 span max); equivalent column Eq 6-16a..d, Kec, Kt, torsion constant C |
| Ie of equivalent column | 6-23 (214) | Eq 6-17a..c, psi |
| Strip shares of moment | 6-23 (214) Table 6-4; 6-24 (215) Table 6-5 | interior negative 75/25, exterior negative 80/20, positive 55/45 (column/middle %). Eq 6-18 for the empirical method |

## NOT FOUND (do not invent)
- Any column/middle strip deflection procedure or load distribution factors (pp.4-60..4-66, 6-17..6-24).
- Any reduced (cracked) slab inertia for deflection of flat slab frames, other than Ie Eq 4-70/4-72. 6-2-5-4 uses gross width and Ig-type stiffness for analysis (p.6-21/6-22). Reading of the second half of p.6-21 (gross width for vertical loads) is from the page image.
- Reduction of the punching shear by the load inside the outer (drop edge) perimeter. The engine uses full Vu (conservative).
- Eq 4-46-b meaning of a,b for the outer section: taken as the drop panel plan sides (assumption, flagged).
- Printed value of the 2nd edge/corner perimeter orientation: smaller perimeter used (conservative), same as ECPFlatSlab.

## Estimate (not a code method)
Midpoint = column-strip deflection in one direction + middle-strip deflection in the other, averaged (ACI practice, StructurePoint ACI 318-14 flat plate example section 5). Strip deflection is exact beam statics with Ie from Eq 4-70/4-72. Label: "Estimate using ECP 203-2025 general deflection provisions (4-3-1). The code gives no flat slab column/middle strip method."
