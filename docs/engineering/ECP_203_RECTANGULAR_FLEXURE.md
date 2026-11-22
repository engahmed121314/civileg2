# ECP 203-2020 rectangular flexure component

`Ecp203RectangularFlexure` implements singly reinforced rectangular-section flexure without moment redistribution. It is a section calculation, not acceptance of a complete Hordi or Waffle member. It is not yet connected to those member calculations.

## Source map

Primary reference: ECP 203-2020, Arabic edition.

| Implemented rule | Printed reference |
| --- | --- |
| Concrete and reinforcement partial factors 1.50 and 1.15 for bending | 3-5, Eqs 3-15-a/b |
| Concrete block stress 0.67 fcu / gamma-c and block depth 0.8c | 4-3 to 4-4 |
| Force equilibrium and moment resistance | 4-4, Eqs 4-1 and 4-2 |
| Lever arm no greater than 0.95d | 4-5 |
| Maximum singly reinforced resistance | 4-5, Eq 4-4 |
| No-redistribution c/d, Rmax and steel-ratio maxima | 4-6, Table 4-1 |
| Minimum tension ratio and separate gross-area floors | 4-9, Eq 4-9 and following text |

Table 4-1 grades are explicit: 240, 350, 400, 420 and 500 MPa. Other grades are not interpolated. Reinforcement surface is an explicit input. The supported smooth-mild table grade is 240 MPa. The source's preference for concrete strength at least 30 MPa with fy500 is not changed into a mandatory prohibition.

## Numerical conventions

- Input strengths: MPa, dimensions: mm, factored moment: kNm, reinforcement area: mm2.
- `normalizedMoment = Mu / (fcu*b*d^2)`, with Mu converted to Nmm. Its bound is Rmax/gamma-c, not Rmax.
- The compression-block depth is limited by each printed Table 4-1 maximum. Applying their minimum respects rounding differences without declaring a code erratum.
- The standard minimum-area route is used. The conditional alternative based on 130% of calculated steel is not selected.
- The minimum ratio acts on b*d. Absolute floors act on gross concrete area b*h. This rectangular component does not substitute flange width for a rib/web width.
- Impossible equilibrium returns no required area or fallback lever arm. Demand outside the singly reinforced domain does not pass.
- Provided-area checks use actual reinforcement area. More bars cannot make an over-reinforced section pass.

## Verification

The test set includes an independently calculated force-equilibrium vector: fcu30, fy400, b150, d250, a50 gives C100500N, z225mm, Mu22.6125kNm and As288.9375mm2. It also checks low-moment lever-arm limits, minimum-area rules, the concrete-strength boundary for the absolute floor, all supported table grades, Rmax normalization, under/over-provision, impossible equilibrium, monotonic demand, zero demand and invalid inputs.

The standard Android workflow now explicitly invokes `:core:calculations:testDebugUnitTest`. The previous Free/Play-flavored task selectors alone did not select the unflavored calculation-library task. Both app and calculation-library test reports are uploaded. This does not add calculation-library JaCoCo coverage measurement.

## Remaining integration scope

A ribbed-member implementation still needs correct force/tributary-width mapping, cover and actual reinforcement centroid, positive/negative region and support classification, applicable T-section behavior, shear, geometry/detailing, transverse reinforcement and serviceability. Existing member-level safety holds are unchanged by this isolated component. Existing saved input is unchanged.
