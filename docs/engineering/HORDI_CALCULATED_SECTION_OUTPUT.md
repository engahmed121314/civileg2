# Calculated Hordi section output contract

The source calculation now produces a typed `SlabCalculatedSection` alongside its numeric result. `HordiSlabDesignSnapshot.capture` freezes both in the existing completed input/result record. `HordiCalculatedSectionSnapshot.from(frozen)` validates the record fingerprint and input/geometry correspondence, then returns immutable rendering data with that same revision ID. It does not solve the section again or parse printed calculation notes. Older results without geometry return null, never a layout reconstructed from defaults.

## Coordinate and scope contract

- All dimensions and bar centres are mm. Bar x is measured from the left of the rib web; bar y is measured upward from the physical bottom of the slab section.
- The actual entered rib pitch is a one-pitch cell width, not a supplied full-panel width. Cell boundaries are cuts through repeated construction, not free slab edges.
- The analytical effective flange width is separate from the physical entered pitch. In the negative-moment case the analytical width can equal the web width while the actual topping still extends over the entered pitch.
- Horizontal placement is an explicit adopted calculation pattern, not claimed as as-built or user-entered x coordinates. One bar is centered. Multiple bars are spread symmetrically between the two covered cage limits. The existing assessed vertical layer centres are unchanged. The pattern checks minimum clear spacing; it does not alter the section's steel area, effective centroid or resistance calculation.
- At more than4096 individual bars, coordinate export is null without changing the numerical section result. No renderer may invent omitted dots.

The payload names every placed bar's layer, within-layer index, diameter and coordinate. Renderers must consume those values rather than invent their own spacing or substitute a different diameter. For a one-pitch view, translate x by `(enteredRibPitchMm-ribWidthMm)/2`; do not change the source web-relative coordinates.

## Bounded output, not full member detailing

This payload supports a real cross-section cut with adopted bar positions and actual supplied link labels. It does not supply link bend radii, closures/hooks, longitudinal offsets, bar paths/lengths, support anchorage, curtailment, topping/distribution mesh, full-panel width or actual cross-rib provision. Those remain explicit incomplete geometry. A link diameter/leg count/spacing label is not authority to draw an invented closed stirrup shape.

`DesignDrawingView` with kind SECTION and the backend's single-scene PDF route can show this bounded section. Do not duplicate or relabel it as Plan, Side and Elevation to satisfy a complete-member document constructor. Full primary views and 3D bar paths require their own explicit geometry producers.

The revision identity is a consistency digest, not a signature or engineering approval. Section/member holds remain visible, and neither a drawing nor a successful rendering test changes the calculation's safety status.
