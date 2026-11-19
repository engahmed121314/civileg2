# CivilEG2 — Drawing Standard & BBS Specification (DR-0)

**Version:** 1.0 (2026-11-18) · **Status:** Confirmed Owner Decisions (D12–D16) & Binding Drawing Specification (R15)

---

## 1. General Export Rules (D14)

* **Sheet Size:** `A3 Landscape` (`420 x 297 mm`) for PDF and DXF drawings. Continuation pages for Bar Bending Schedules (BBS) use `A4 Portrait` or `A3 Landscape` as required by table length.
* **Scales:** Auto-scaling selected from standard engineering scales:
  * **Main Views / Elevations / Plans:** `1:50` or `1:100`.
  * **Cross-Sections (1-1, 2-2, 3-3):** `1:10`, `1:20`, or `1:25`.
  * **Detail Views:** `1:10` or `1:20`.
* **Language (R5):** All exported CAD entities, text callouts, dimensions, titles, notes, and BBS tables in PDF/DXF are strictly **English-only**. The interactive Android UI screen remains bilingual (Arabic/English).
* **Single Source of Truth (R1):** All renderers (Compose on-screen, PDF export, DXF export, BBS, and BOQ) consume the exact same `ReinforcementLayout` generated from the design `ResultEnvelope`.

---

## 2. Bar Catalog & Bending Schedule Specification (D13, §13.6, §13.7)

### 2.1 Commercial Bar Catalog
Commercial diameters available in mm:
$$\text{Diameters (mm)} = \{8, 10, 12, 14, 16, 18, 20, 22, 25, 28, 32\}$$

* **Unit Weight Formula:** $w = 0.006165 \times \varnothing^2 \text{ kg/m}$ (derived from physical density $\frac{\pi}{4} \times 7850 \times \varnothing^2$).

### 2.2 Bar Bending Schedule (BBS) Columns
The BBS table produced by `BbsEngine` contains:
1. **Member Mark:** Identifier (e.g., `B1`, `C1`, `F1`).
2. **Bar Mark:** Unique bar identifier within member (e.g., `01`, `02`, `03`).
3. **Role:** Bar function (`MAIN_BOTTOM`, `MAIN_TOP`, `STIRRUP`, `TIE`, `EXTRA_TOP`, etc.).
4. **Diameter ($\varnothing$):** Bar diameter in mm.
5. **Shape Sketch:** Schematic shape with dimension letters ($A, B, C, D, E, R$).
6. **Dimensions:** Segment lengths in mm.
7. **No. of Members:** Quantity of structural elements.
8. **Bars per Member:** Number of bars of this mark per element.
9. **Total Bars:** Total quantity ($\text{Members} \times \text{Bars/Member}$).
10. **Cut Length ($L_c$):** Individual cut length in mm considering code-specific bend/hook allowances.
11. **Total Length:** Total length in meters ($\text{Total Bars} \times L_c / 1000$).
12. **Total Weight:** Weight in kg ($\text{Total Length} \times w$).

### 2.3 BBS Summaries
- Summary per diameter (Total meters, Total weight kg, number of stock 12m bars).
- Total steel weight per element and project total.
- Steel consumption ratio in $\text{kg/m}^3$ of concrete.

---

## 3. Mandatory 3 Sections per Structural Element (D12, §13.4)

Every physical structural element drawing package contains main longitudinal view(s) and **exactly three labelled cross-sections** (`SECTION 1-1`, `SECTION 2-2`, `SECTION 3-3`):

| Element | Section 1-1 (Station A) | Section 2-2 (Station B) | Section 3-3 (Station C) |
|---|---|---|---|
| **Beam** | Left support (Top steel, anchorage, confined stirrup zone) | Mid-span (Bottom main steel, skin bars if deep beam) | Right support / Cantilever root |
| **Column** | Base / Footing joint (Starter bars, lap splice, confinement ties) | Mid-height (Standard tie spacing, main bar arrangement) | Column-beam joint / Top confinement zone |
| **Solid Slab** | Short-direction mid-span | Long-direction mid-span | Continuous edge support (Top negative steel, distribution bars) |
| **Flat Slab** | Column strip over column (Top steel, punching shear perimeter) | Column strip mid-span | Middle strip mid-span |
| **Hordi / Waffle** | Rib at mid-span (T-section) | Rib at support | Solid head / Transverse cross-rib |
| **Isolated Footing** | X-direction section through column | Y-direction section through column | Column-footing interface (Dowels, development length, punching perimeter) |
| **Combined Footing** | Longitudinal section | Transverse section at Column 1 | Transverse section at Column 2 / Mid-strip |
| **Strap Footing** | Inner footing section | Strap beam mid-span section | Outer footing section |
| **Pile Cap** | Cap X-direction section | Cap Y-direction section | Pile shaft section (Rebar cage, spiral/ties) |
| **Retaining Wall** | Stem base (Max moment, main vertical bars, key) | Stem mid-height (Curtailment station) | Base slab (Toe, heel, key anchorage) |
| **Water Tank** | Wall-base joint (Vertical section, water stop) | Wall corner / Ring section | Base slab or roof slab section |
| **Staircase** | Waist slab flight section | Landing / Flight-landing junction | Support beam / Wall seat detail |
| **Shear Wall** | Wall base (Boundary elements, vertical & horizontal web steel) | Wall mid-height | Coupling beam / Opening lintel section |
| **Wall Opening** | Lintel / Header beam section | Jamb / Vertical edge section | Corner diagonal trim bar detail |
| **Steel Member** | Mid-span section | Support section (Bearing stiffeners) | Connection / Base-plate section |
| **Post-Tensioned** | Mid-span (Max tendon eccentricity) | Support section | Anchorage zone detail |

---

## 4. Case-Driven Detail Drawings (D12, D15, §13.5)

Each element drawing package includes case-specific enlarged detail drawings triggered by the design case:

* **Beam Details:** Stirrup closed hook (135° vs 90°), lap splice zone, bar curtailment, torsion closed stirrups & corner bars, deep beam skin reinforcement.
* **Column Details:** Tie & cross-tie arrangement, lap splice offset, spiral pitch and lap, column-beam joint confinement.
* **Footing Details:** Dowel L-bends & development length $L_d$, pedestal reinforcement, pile embedment into cap.
* **Slab Details:** Top steel chairs, edge U-bars, opening trim bars ($2\varnothing16$ diagonal + parallel).
* **Wall & Tank Details:** Construction joint water-stop, corner wall L/U bar intersection, key anchorage.

---

## 5. Analysis-Only Screens & Execution Order (D16)

* **Analysis Screens (Seismic, Wind, Frame Analysis, Soil Bearing):**
  * Do not produce rebar drawings directly.
  * Produce a **Diagram Package**: Bending Moment Diagrams (BMD), Shear Force Diagrams (SFD), Deflection curves, Story Drift, Mass Eccentricity, and Load Combination Envelopes.
  * Transfer governing internal forces automatically to member design engines (Beams/Columns/Footings) to generate structural rebar packages.
* **Execution Workflow Order:**
  1. **Phase 3 (Calculation Depth):** Complete calculation engines, clause research, golden vectors, and `ResultEnvelope` for the element.
  2. **Phase 4 (Drawing Package):** Implement `ReinforcementLayout` -> `DrawingScene` -> Compose / PDF / DXF renderers for that element.

---

## 6. Acceptance Criteria (Gate G10 & D15)

Every drawing package must satisfy:
1. **G1:** Exactly 3 sections cut at Stations A, B, C with cut line markers on the main view.
2. **G2:** Every bar mark in views exists in the BBS table and vice versa.
3. **G3:** Provided $A_s \ge \text{Required } A_s$ at all cut stations; clear spacing $\ge$ code minimum.
4. **G4:** BBS total weight matches $\sum \text{bar weights}$; cut lengths calculated with exact code bend deductions.
5. **G5:** Screen == PDF == DXF == BBS for identical design inputs.
6. **G6:** DXF validates clean in `ezdxf` audit with 0 non-ASCII characters.
7. **G7:** PDF contains readable section titles, fonts embedded, 0 Arabic glyphs.
8. **G8:** Legibility contract (R13, R14) satisfied: 0 label overlaps via `LabelPlacer`.
