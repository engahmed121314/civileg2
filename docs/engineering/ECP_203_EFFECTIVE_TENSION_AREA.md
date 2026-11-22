# Effective concrete tension area, ECP 203-2020

`Ecp203EffectiveTensionArea` implements the explicit geometry cases in printed pages 4-69 to 4-71, Figure 4-24. Its inputs are dimensions in mm and its areas are mm2. It does not choose a member type, calculate steel stress or grant a cracking/member verdict.

## Supported geometry

| Explicit case | Effective depth | Area contract |
| --- | --- | --- |
| Figure 4-24(a), constant-width beam tension band | `2.5 * a`, where `a` is the actual area-weighted tension-steel centroid distance from the tension face | Width must be constant through the entire band. No solid-slab one-third cap is imported. A band deeper than the whole section is outside this bounded case, rather than silently clipped. |
| Figure 4-24(b), solid slab with one bar layer | `min(2.5 * (Cc + phi/2), (t-c)/3)` | `Cc` is clear cover to the main bar, not cover to links; `c` is the cracked neutral axis measured from the compression face. |
| Figure 4-24(c), pure axial tension with equal single-layer face cages | `min(2.5 * (Cc + phi/2), t/2)` per face | Reports both per-face and two-face total areas. The total belongs with steel area summed over both equal faces. Unequal cages and one-sided reinforcement are not silently generalized. |

The slab cap is applied as written, without adding an unprinted lower bound based on the bar centre. Zero cover can be evaluated as geometry; it is not a cover-compliance result. Cover, bar spacing, exposure and other detailing requirements remain separate.

For a T-rib, the beam-band method can only receive a width that is actually constant over that whole tension band. It cannot silently use the top flange width for bars confined to the bottom web, or use web width when the band crosses a change of section. No T-rib classification or intersection producer is supplied by this component.

## Independent vectors

- A 200 mm wide constant band with tension-steel centroid `25 + 8 + 20/2 = 43 mm` from the tension face gives depth 107.5 mm and area 21,500 mm2.
- Equal-diameter bars in two layers, three at 43 mm and one at 93 mm, have centroid 55.5 mm. At width 200 mm, the band depth is 138.75 mm and area 27,750 mm2.
- A solid-slab strip 1000 mm wide, `t=200`, `Cc=20`, `phi=12`, `c=50` gives depth 50 mm and area 50,000 mm2.
- Equal axial face cages at `t=100`, `Cc=20`, `phi=12`, width 1000 give 50,000 mm2 per face and 100,000 mm2 total. The equal-face reinforcement ratio is unchanged when both numerator and denominator are summed.

The tests cover both slab branches and their crossover, the absence of an imported slab cap for beams, equal-face counting, invalid tension geometry, nonfinite inputs and overflow. This geometry component does not resolve the service-stress model, stress location, duration/exposure input or coupling to `Ecp203LoadCrackWidth`.
