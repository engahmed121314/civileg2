# ECP 203-2020 flexure, web shear and rib geometry components

These components implement bounded section and geometry calculations. They are not acceptance of a complete Hordi or Waffle member and are not yet connected to those member calculations.

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

## T-section flexure

`Ecp203TSectionFlexure` integrates the equivalent rectangular compression block over the actual flange and web. Printed4-3 paragraph9 and printed4-4 Figure4-4 permit the equivalent block for T/L sections. Force and first-moment equilibrium cover both block-in-flange and block-below-flange cases. The no-redistribution neutral-axis envelope is inherited from Table4-1, not imposed as a web-only rectangular moment cap. Minimum tension reinforcement uses the web width.

The flange must be in compression. A negative-moment region must use its applicable web rectangle rather than assume flange action. The effective-width helper implements min(16tf+b, L2/5+b, available width) from printed6-42 clause6-3-1-8(a). L2 is supplied explicitly; it is not silently treated as the span.

An independent below-flange vector is fcu30, fy400, bw150, B600, tf50, d300, a80: C462300N, Mu124.7205kNm and As1329.1125mm2. Tests also cover the flange boundary, width limits, web-only equivalence, provided steel and impossible sections.

## Web shear

`Ecp203WebShear` covers constant-depth sections with zero axial force and no torsion. The caller supplies the applicable factored section shear; this component does not select the critical section or redistribute loads. Sources are printed4-17 to4-22, Eqs4-24/27/29/30/33/34/40 and clause4-2-2-1-6.

- q = V/(bw*d); qmax = min(0.70 sqrt(fcu/1.50), 4.4) MPa.
- Uncracked concrete contribution is 0.16 sqrt(fcu/1.50); cracked contribution is 0.12 sqrt(fcu/1.50).
- Printed4-22(d)(2) requires concrete-only sizing for h <= max(250mm, 2.5tf, bw/2), including hollow-block and embedded beams. Links cannot rescue a section that fails this route.
- Outside that route, actual vertical links are checked. The design yield value is capped at 420 MPa; fy500 material is not prohibited. Minimum ratio is the larger of 0.4/fy and the applicable smooth/deformed floor. The optional reduced ratio for wide shallow beams is not used.
- Link checks include minimum 8mm diameter, at most 200mm longitudinal spacing and the printed wide-web four-leg/transverse-spacing requirement. No links in an ordinary section fails detailing even at zero shear. Diagonal bars, variable depth and axial-force cases are outside scope.

## One-way rib geometry

`Ecp203RibbedGeometry` implements printed6-15/16, Figure6-4: e is the clear gap, calculated from center spacing minus web width. Checks include e <=700mm, bw >= max(100mm,h/3) and topping >= max(50mm,e/10).

The cross-rib count uses characteristic live load, not total or factored load. At live load <=3kN/m2, a span above 5m requires at least one midpoint cross-rib. Above 3kN/m2 the source's distinct threshold is 4m: spans from 4m through 7m require one and spans above 7m require three. Their section sizes, reinforcement and the separate topping/distribution requirements still need member-level checks. A returned count alone does not establish those details.

## Explicit individual-bar layout

`Ecp203BarLayout` uses printed7-6/7-7, clause7-3-3-1 and Figure7-2a. It checks separate, nonbundled bar layers against the available width inside the links and the vertical depth. Clear gap is at least max(largest individual diameter, 1.5 nominal maximum aggregate size), applied in both directions. The cover, link diameter, aggregate size and actual layer counts/diameters are explicit inputs. Effective depth comes from the actual reinforcement area centroid, not h minus a constant or the first layer alone. A layout that does not fit returns no effective depth.

This is not approval of the chosen cover: exposure, durability and fire requirements still need separate verification. It does not check anchorage, bends, crack width or maximum longitudinal spacing. Bundles are excluded because their source spacing rule is different. Tests cover two-layer and unequal-layer centroids, diameter/aggregate gap control, horizontal and vertical fit, cover propagation and invalid inputs.

## Coupled dedicated Hordi section path

`Ecp203RibbedSection` composes the source components using actual tension layers,
cover outside links, aggregate size, steel surface, vertical links and moment
region. Positive flange compression uses the explicit L2 effective width capped
by interior rib spacing. Flange tension uses the web rectangle. The same actual
bar centroid feeds flexure and shear. Input forces are already factored per rib;
the load-combination selector does not multiply those forces again.

The dedicated Hordi screen/ViewModel/wrapper forwards this context to the ECP
engine. Actual bar and link inputs are visible; the L2 field starts empty rather
than quietly equating it to the span. Impossible layout or out-of-domain demand
has an explicit error, not a four-bar cap, h-minus-30 depth or square-root clamp.
The result includes actual layout, d, effective flange width, moment resistance,
link route, dimensional failures and required cross-rib count in its trace.
The entered thickness and rib spacing are labelled as such, not as a computed
minimum depth or longitudinal bar spacing. Input changes invalidate the prior
result. The legacy renderer is not used to invent a bottom-only drawing for a
top-tension or multilayer source assessment.

An independent coupled vector is fcu30, fy400, bw200, center spacing600, h350,
tf60, cover25, links8, aggregate20 and two20mm tension bars: d307mm,
As628.3185307mm2, B600mm, MRd63.7388172kNm/rib, Asreq611.1777816mm2 at62kNm.
Increasing cover to45mm lowers d to287mm and fails that moment with the same bars.
Moving the flange into tension also fails that demand. These changes are tested.

## Remaining member integration scope

This is a section assessment, not a completed member design. Whole-member safety
and execution exports stay unapproved until serviceability, durability/fire cover,
topping/transverse steel, cross-rib provision/detailing and anchorage are checked.
Advanced/shared Hordi and waffle paths do not yet consume this context and retain
their previous explicit method holds. The legacy no-context preview remains only
for those unconverted callers. ACI/SBC numerical methods are unchanged and reject
an ECP-only context instead of silently applying it. Existing saved data is not
rewritten and no database schema change is included.

## Material application range

Printed2-21, clause2-3-2-1 and Table2-13, defines the characteristic strength from
150mm cubes at28days, requires reinforced-concrete fcu>=20MPa and limits design
fcu to60MPa. Both standalone flexure and web-shear components now enforce20..60.
The minimum-ratio test previously used fcu16; it now uses20 and still tests the
same controlling1.1/fy expression and exact103.125mm2 result. The4.4MPa shear-cap
fixture now uses60, where the cap still governs, rather than the invalid100MPa.
Boundary acceptance and rejection tests cover20,60,19.999,60.001 and100MPa.

The printed4-1/4-2 preference for fcu>=30 when fy500 is used is not a universal
mandatory prohibition. It is not converted into one. Specific grade/exposure
restrictions, including B500-DWR in exposureIII/IV (printed4-73), still require
full material and environmental classification before member approval.


## Layer-by-layer strain compatibility

The coupled rib section no longer assumes that every physically fitting bar layer
reaches design yield. Printed4-1 requires equilibrium and strain compatibility;
printed4-2 Fig4-1 gives Es=200000MPa and the design stress plateau fy/1.15;
printed4-3 gives epsilon_cu=0.003. The new layered calculation solves the neutral
axis using each actual layer depth and its capped elastic design stress. Required
area is solved for the same relative layer distribution and positions, rather
than treating it as a bar schedule or silently moving bars.

The previous no-redistribution table envelope is retained, including its
conservative total-steel ceiling. The design resistance also retains the0.95d
area-centroid lever-arm ceiling; the uncapped equilibrium moment is separately
available. This can be conservative when inner layers remain elastic. A selected
layout requiring reinforcement on the compression side of the neutral axis is
explicitly outside this singly reinforced component, not assigned a false zero
or a passing centroid-only result.

Independent rectangular regression: fcu30/fy400, bw200/h350, cover25, links8,
aggregate20, seven layers of one8mm bar give layer depths313,275,237,199,161,123,85mm.
As=351.8583772mm2 and centroid d=199mm. The centroid-yield approximation gives
MRd21.5602757kNm and incorrectly passes21.5kNm. Layer compatibility gives
c56.1541221mm, innermost-layer stress308.2147150MPa rather than347.8260870MPa,
and MRd21.4812192kNm, so the supplied layout fails that demand.

Independent T regression: fcu20/fy400, bw200/B600/tf60/h350, same cover/links/
aggregate, seven layers of one12mm bar give c55.9167340mm and innermost stress
33.0841859MPa. Equilibrium moment43.4798985kNm is further limited to42.1397454kNm
by the retained conservative0.95d ceiling. The single-layer coupled golden vector
above is unchanged. Tests cover both independent vectors, the previously false
pass, required area, source stress law, domain boundaries and the actual wrapper.
