# ECP 203-2020 flexure, web shear and rib geometry components

These components implement bounded section and geometry calculations. The dedicated ECP Hordi path consumes an explicit actual-layout context. This is not acceptance of a complete Hordi or Waffle member; shared/Advanced callers remain unconverted.

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
the irrelevant second-factor selector is omitted from the source ECP input path. Other-code input paths retain their existing selector.

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

## Immediate simply supported UDL deflection

`Ecp203ShortTermDeflection` implements a separate characteristic-load elastic
calculation, not a reuse of the factored section moment/shear. The dedicated rib
context optionally supplies a simply supported analysis span and characteristic
dead area load including all self weight. The existing characteristic live area
load is used independently of the supplied ULS force. Multiplying each area load
by the actual rib-center spacing gives its characteristic line load.

This bounded gravity model requires top-flange compression and analysis span
identical to the explicitly supplied zero-moment distance L2. Continuous spans,
cantilevers, uplift, variable stiffness and loading patterns are not substituted.

- Printed2-23 Eq2-1: `Ec=4400*sqrt(fcu)` MPa; printed4-2: Es200000MPa.
- Gross T-section centroid and Ig use the web and flange geometry, ignoring steel
  as required for Ig. Flange-at-tension-face geometry has its own extreme-fiber
  distance, not the compression-flange cracking moment.
- Cracked transformed-section equilibrium excludes tensile concrete. Each actual
  steel layer is retained; compression steel replaces concrete with factor n-1.
  Rectangular strips are integrated through the neutral axis. Icr used for
  deflection is capped at Ig, per the printed4-60 definition.
- Printed4-60 Eq4-71: `fctr=0.6*sqrt(fcu)`, `Mcr=fctr*Ig/yt`.
- Printed4-59 Eq4-70: `Ie=(Mcr/Ma)^3*Ig+[1-(Mcr/Ma)^3]*Icr<=Ig`.
  Uncracked cases use Ig directly, including zero moment.
- Immediate midspan deflection uses the elastic simply supported UDL solution
  `5*q*L^4/(384*Ec*Ie)`, with q in N/mm, numerically equal to kN/m.
- Printed4-62 Eq4-77: live-load increment is `delta(D+L)-delta(D)`, using each
  load case's own moment and stiffness. Using one stiffness for both is wrong.
- A fully cracked elastic tension-steel stress bound is exposed. The coupled rib
  path rejects cracked cases above characteristic steel yield rather than
  presenting an elastic result as valid after steel yielding.

Independent high-precision T vector: fcu30, bw200, B600, tf60, h350, two20mm
bars at307mm from the compression face. Ec24099.7925302MPa, n8.2988266289,
gross centroid137.9787234mm, Ig1097549290.7801mm4, cracked neutral axis
64.9848905mm, Icr360278972.7326mm4 and Mcr17.0120428kNm. At span5000mm,
qD4 and qL8kN/m, deltaD1.23066954mm and deltaD+L9.44311333mm give the
correct live increment8.21244379mm. At the same gross section with the flange
at the tension face, Mcr26.1410958kNm and live increment5.96096472mm differ.
The latter is a component geometry test, not permission to model a negative
support region as a simply supported gravity rib.

The actual coupled rib vector with dead area load6kN/m2 and live8kN/m2 at
600mm centers uses line loads3.6 and4.8kN/m: deltaD1.10760259mm,
deltaD+L5.05652550mm and live increment3.94892292mm. The ULS62kNm supplied
section force and required area611.1777816mm2 are unchanged by this service
calculation. The EN/AR flow verifies explicit service inputs, rejects a
mismatched analysis span and invalidates results when service loads change.

This is immediate deflection only. Long-term creep/shrinkage, load age, finish
installation, deflection-sensitive attachments, crack width and full limits
remain unapproved. There is no new whole-serviceability or member SAFE result.
The trace records the actual equations, separate load cases, stiffness and
limits. Failed section geometry/flexure/shear also carries source-specific
remedial actions, not an instruction to add bars or links without code limits.

## Completed-source snapshot and accessible calculation trace

`HordiSlabDesignSnapshot` freezes the actual completed inputs and result, including
all supplied layers, links, section forces and optional characteristic service
loads. A shared `FrozenDesignRecord` identifies the exact JSON pair by SHA256 and
schema version. It is a data identity/check, not a digital signature or method
approval. Fresh decoding prevents a later form/list edit from mutating the pair.
The ViewModel clears the result and snapshot together, including before a failed
new calculation, and publishes a new pair only after successful calculation.

The source input records that section actions are already factored and are not
refactored by the retained legacy selector. No panel width, bar length or member
quantity is inferred. This producer is not completion of the legacy Save writer;
its quantity-basis/repository consumer requires a separate coordinated repair.

Source equations now use individually reachable list items rather than one
oversized item and a second duplicated formula dump. EN/AR flow tests require the
actual immediate-deflection equation to be displayed before saving its image.
Current-head Android execution and actual pixel inspection remain required.

## Common-age long-term simply supported UDL deflection

ECP203-2020 printed4-61..4-63, Eqs4-73..4-80, now has an explicit optional
calculation coupled to the same actual rib section and characteristic loads.
The sustained case D+psiL receives its own moment and effective inertia.
It is not a fraction of the full-load deflection across the cracking transition.
With explicitly no beneficial compression-steel credit, alpha=xi. This is not a
claim that actual compression reinforcement is absent. Source duration anchors
are0/3/6/12 months, or the actual integer month>=60 where xi=2. No curve
interpolation is invented. The end age cannot precede the installation age,
including when both are above60 months. Installation zero means just after the
initial instantaneous sustained-load response, not before loading.

For this declared common-application-age model:

- deltaS=delta(D+psiL), using IeS.
- Additional creep/shrinkage=alphaEnd*deltaS.
- Mechanical total=delta(D+L)+alphaEnd*deltaS.
- Eq4-80 post-installation=deltaS*(alphaEnd-alphaInstall)+delta(D+L)-deltaS.

Clear span, not the analysis span, sets the applicable250/360/480/240 divisors.
The360 live-only check applies to attached deflection-insensitive elements;
post-installation uses480 for sensitive and240 for insensitive elements. No
attachment is a distinct explicit selection, not an assumed input default.
The total and post-installation checks require separately supplied adverse
thermal contributions. Blank is NOT_EVALUATED, not zero. Explicit zero is
accepted as an engineering input from a separate thermal analysis. There is no
thermal model or camber credit here. Staggered sustained-load ages are rejected,
not silently approximated by one age. Cracked steel beyond yield remains outside
the elastic model. High-strength/high-stress review is separate from numeric
limit status, and no crack-width, detailing or whole-member approval follows.

Independent T-rib vector: fcu30,bw200,B600,tf60,h350,2phi20 atd307,analysis5000,
clear4800,D6/L8kPa,centres600,psi0.7,end60months,installation6months:
deltaS3.295961692232mm,additional6.591923384464mm,mechanical total11.648448887758mm,
mechanical post-installation4.397333164848mm. Thermal inputs1.5/0.5mm produce
13.148448887758/4.897333164848mm for their respective checks. Original ultimate
strength results remain unchanged. These numbers are method vectors, not a
completed member design.

## Typed calculation stages

The explicit-context Hordi adapter now creates `SlabCalculationStage` values from the same completed assessment as the numeric result. Each stage contains a stable key, English heading, equation, numeric substitution or actual iterative-solver inputs, result with units, and source citation. The UI numbers the stages, localizes headings and field labels, and keeps engineering mathematics LTR in both locales. Display substitutions are rounded; the calculation uses full precision.

Immediate dead-load, total-load and live-only deflections are separate stages. Sustained-load stiffness and deflection, duration factors, creep/shrinkage, mechanical total, installation increment, and thermal-inclusive limits remain separate stages. Unknown thermal values are never shown as zero. Non-applicable attachment checks remain distinct from unknown checks. Warnings and complete source assumptions are separate from the calculation steps.

The optional nullable result field preserves older snapshots that contain no typed stages. New frozen records include the exact stage values in their existing input/result fingerprint. Decoding an older record does not rebuild stages, change its stored numbers, or rewrite its fingerprint. This presentation change neither changes the section calculations nor closes any member, output or detailing hold.

## Load-induced crack-width component

`Ecp203LoadCrackWidth` implements printed4-66..4-70, Eqs4-83/84/85, for already-cracked sections with individual equal-diameter bars. The caller supplies actual cracked service steel stress, stress at first load-induced cracking at the same location, steel area and effective concrete tension area from Figure4-24. It does not substitute ULS yield stress, gross slab area or a guessed beam/slab rule. Pure bending uses k2=0.5; pure axial tension uses k2=1.0. Combined strain, bundles, oblique cracks and imposed-deformation cracking are outside this component.

The load-induced beta is1.7. Deformed bars use beta1=0.8 and k1=0.8; smooth mild bars use beta1=0.5 and k1=1.6. Duration beta2 is1.0 for short loading or0.5 for permanent/repeated loading. The component applies the printed mean-relative-strain formula without importing a strain floor from another code. It uses Es=200000MPa from printed4-2, Figure4-1. The maximum characteristic service envelope must be at or above first cracking; unloading history and yielded service steel are not approximated.

Exposure is explicit, never inferred from a location. Table4-12 limits are0.30/0.20/0.15/0.10mm for the four Table4-11 classes. The separately referenced Table(m1-1), printedm1-2, supplies steel service limits140/200/220/230MPa for fy240/350/400/420. It contains no fy500 row. For fy500 the width calculation remains available, but the source stress-cap check and a successful combined bounded verdict remain unknown, not a fabricated extrapolated pass. A known width failure still fails even when that cap is unknown.

Independent rational vectors include: deformed permanent loading, fs150MPa, fsr100MPa, As600mm2, Aceff20000mm2, phi20mm and pure bending gives rho0.03, srm350/3mm, relative mean strain37/60000 and wk4403/36000mm. Short loading gives3451/36000mm. Smooth mild fs140/fsr90 with the same areas/diameter gives131461/672000mm. Geometry/stress production and UI integration are not supplied by this component alone. A bounded width/stress verdict is not full cracking, durability or whole-member approval.

## Frozen JSON under release shrinking

The frozen Hordi input root is in the app entity package, but its section context contains nine core input/enum types. Keeping only the app entity classes does not protect those nested fields, enum names or reflective construction. The app shrinker rules now name those nine schema types explicitly. Calculation algorithms and unrelated core classes are not covered by a broad keep rule.

`HordiSnapshotSerializationContractTest` walks both frozen input/result object graphs, including generic list elements, and checks the current nested core type set against explicit stable-name keep rules. A newly reachable core model requires a deliberate schema/keep-rule review. The generic signature attribute remains required.

A supplemental JVM classfile experiment with R8 8.13.25 and Gson 2.13.1 used the actual core context models and the app shrinker rules. Before the targeted rules, shrinking changed/removed reflected fields and the synthetic context failed even a same-build JSON round-trip. With the targeted rules, the same-build round-trip and decoding the original source-named JSON both passed, including layered bars, link data, duration values and attachment/surface/region enums. This isolated experiment does not replace the configured AGP release build, release APK migration/entry tests, production certificate compatibility or an on-device crash trace.
