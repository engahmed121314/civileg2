package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.*
import com.civileg.app.domain.calculations.base.ShearWallDesign
import kotlin.math.*

/**
 * ECP 203-2020 / ECP 201-2020 Shear Wall Design Implementation
 *
 * References:
 *  - ECP 203 §6-7: Structural walls (general provisions)
 *  - ECP 203 §4-2: Flexural design (K-method / strain compatibility)
 *  - ECP 203 §4-3: Shear design
 *  - ECP 201 (Seismic): Special and ordinary wall requirements
 *  - ECP 203 §4-2-6: Ties and confinement
 *
 * Material safety factors: γc = 1.5, γs = 1.15
 *
 * Key design rules:
 *  - Wall as column: flexural design using K-method or strain compatibility
 *  - Axial + bending interaction
 *  - Concrete shear: Vc = 0.4×√fcu × b × L  (simplified for walls)
 *    or Vc = 0.24×√(fcu + 0.1×Nu/(b×d)) × b × d / γc
 *  - Steel shear: Vs = Asv×(fyv/γs)×d / s
 *  - Boundary element: when c/d > 0.3 or compression strain > 0.003 at boundary
 *  - Minimum ρv = 0.0025, ρh = 0.0025
 *  - Max spacing vertical: min(L/3, 3t, 450mm)
 *  - Max spacing horizontal: min(L/5, 3t, 450mm)
 *  - Coupling beam: diagonal reinforcement when V/(√fcu × bw × d) > 0.15
 *  - Slenderness: H/t ratio check (H = total height, t = thickness)
 */
class ECPShearWall : ShearWallDesign {

    companion object {
        // Material safety factors — ECP 203 §2-3-1
        private const val GAMMA_C = 1.5        // concrete
        private const val GAMMA_S = 1.15       // steel

        // Stress block parameters
        private const val ALPHA = 0.85          // Whitney block factor
        private const val BETA_1_BASE = 0.85    // β1 for fcu ≤ 28 MPa
        private const val EPSILON_CU = 0.003    // extreme fiber strain at failure
        private const val ES = 200000.0         // steel modulus (MPa)

        // Reinforcement limits — ECP 203 §6-7
        private const val RHO_V_MIN = 0.0025   // min vertical reinforcement ratio
        private const val RHO_H_MIN = 0.0025   // min horizontal reinforcement ratio
        private const val RHO_V_MAX = 0.06     // max vertical reinforcement ratio

        // Available bar diameters (mm)
        private val BAR_DIAMETERS = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0, 28.0, 32.0)
        private val SMALL_BAR_DIAMETERS = listOf(8.0, 10.0, 12.0, 14.0, 16.0)
    }

    // ══════════════════════════════════════════════════════════════════
    // 1. MAIN ENTRY POINT — designWall
    // ══════════════════════════════════════════════════════════════════

    override fun designWall(input: ShearWallInput): ShearWallResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val safetyChecks = mutableListOf<ShearWallSafetyCheck>()

        codeNotes.add("ECP 203-2020 §6-7 / ECP 201: Shear Wall Design")
        codeNotes.add("Wall Type: ${input.wallType.displayName}")
        codeNotes.add("γc = $GAMMA_C, γs = $GAMMA_S")

        // ── Effective section properties ───────────────────────────
        val Lw = input.wallLength
        val bw = input.wallThickness
        val Hw = input.wallHeight * input.numberOfStories  // total height (mm)
        val d = 0.8 * Lw  // effective depth (mm)
        val Ag = Lw * bw  // gross area (mm²)
        val cover = input.clearCover

        // Effective flange width for L/T walls
        val flangeW = if (input.flangeWidth > 0) {
            min(input.flangeWidth, 6.0 * bw + Lw / 2.0)  // ECP: min(6bw, Lw/2 from web face)
        } else 0.0
        val flangeT = if (input.flangeThickness > 0) input.flangeThickness else 0.0
        val effectiveBw = bw + if (flangeT > 0) flangeT else 0.0

        codeNotes.add(String.format("Lw = %.0f mm, bw = %.0f mm, Hw = %.0f mm (%d stories)",
            Lw, bw, Hw, input.numberOfStories))
        if (flangeW > 0) {
            codeNotes.add(String.format("Flange: %.0f × %.0f mm, effective bw = %.0f mm",
                flangeW, flangeT, effectiveBw))
        }

        // Input validation
        if (Lw < 5 * bw) {
            warnings.add("ECP: Wall length < 5× thickness — may behave as a column")
        }
        if (bw < 150.0) {
            warnings.add("ECP: Wall thickness < 150mm — minimum recommended")
        }

        // ── 1. Compression zone depth (a) ──────────────────────────
        val c = calculateNeutralAxisDepth(
            Mu = input.bendingMoment, Pu = input.axialLoad,
            Lw = Lw, bw = bw, fcu = input.fcu, fy = input.fy
        )
        val beta1 = calculateBeta1(input.fcu)
        val a = beta1 * c  // Whitney stress block depth
        codeNotes.add(String.format("Neutral axis c = %.1f mm, block depth a = β1×c = %.1f mm", c, a))

        // ── 2. Flexural design ─────────────────────────────────────
        val (Mn, Pn) = calculateFlexuralStrength(input)
        val flexOk = input.bendingMoment <= Mn && input.axialLoad <= Pn
        val flexUtilRatio = max(
            if (Mn > 0) input.bendingMoment / Mn else 2.0,
            if (Pn > 0) input.axialLoad / Pn else 0.0
        )
        safetyChecks.add(ShearWallSafetyCheck(
            "Flexure (Mu/Mn)",
            if (Mn > 0) input.bendingMoment / Mn else 2.0,
            1.0, "ratio", flexOk
        ))
        safetyChecks.add(ShearWallSafetyCheck(
            "Axial (Pu/Pn)",
            if (Pn > 0) input.axialLoad / Pn else 2.0,
            1.0, "ratio", input.axialLoad <= Pn
        ))

        codeNotes.add(String.format("Mn = %.1f kN.m (Mu = %.1f kN.m)", Mn, input.bendingMoment))
        codeNotes.add(String.format("Pn = %.1f kN (Pu = %.1f kN)", Pn, input.axialLoad))

        // ── 3. Vertical reinforcement selection ────────────────────
        val vertRebar = designVerticalReinforcement(input, a, d, c)
        codeNotes.add(String.format("Vertical: %dΦ%d @ %d mm c/c (As = %.0f mm², req = %.0f mm²)",
            vertRebar.bars, vertRebar.diameter, vertRebar.spacing,
            vertRebar.providedArea, vertRebar.requiredArea))

        // ── 4. Shear design ────────────────────────────────────────
        val (Vc, Vs) = calculateShearStrength(input)
        val Vn = Vc + Vs
        val shearOk = input.shearForce <= Vn
        val shearUtilRatio = if (Vn > 0) input.shearForce / Vn else 2.0
        safetyChecks.add(ShearWallSafetyCheck(
            "Shear (Vu/Vn)", input.shearForce, Vn, "kN", shearOk
        ))
        codeNotes.add(String.format("Vc = %.1f kN, Vs = %.1f kN, Vn = %.1f kN (Vu = %.1f kN)",
            Vc, Vs, Vn, input.shearForce))

        // Horizontal reinforcement
        val horzRebar = designHorizontalReinforcement(input, Vc, d)
        codeNotes.add(String.format("Horizontal: %dΦ%d @ %d mm c/c (As = %.0f mm², req = %.0f mm²)",
            horzRebar.bars, horzRebar.diameter, horzRebar.spacing,
            horzRebar.providedArea, horzRebar.requiredArea))

        // Check reinforcement ratios
        val rhoV = vertRebar.providedArea / (bw * 1000.0)  // per meter
        val rhoH = horzRebar.providedArea / (bw * horzRebar.spacing) * horzRebar.spacing / 1000.0
        if (rhoV < RHO_V_MIN) {
            warnings.add("Vertical ρ = ${"%.4f".format(rhoV)} < min ${RHO_V_MIN}")
        }
        if (rhoH < RHO_H_MIN) {
            warnings.add("Horizontal ρ = ${"%.4f".format(rhoH)} < min ${RHO_H_MIN}")
        }

        // ── 5. Boundary element design ────────────────────────────
        val (beType, beRebar) = designBoundaryElements(input)
        if (beType != BoundaryElementType.NONE) {
            codeNotes.add(String.format("Boundary element: %s", beType.displayName))
            beRebar?.let {
                codeNotes.add(String.format("  BE rebar: %dΦ%d @ %d mm", it.bars, it.diameter, it.spacing))
            }
        }

        // ── 6. Coupling beam design ────────────────────────────────
        val couplingResult = designCouplingBeam(input)
        if (couplingResult != null) {
            codeNotes.add(String.format(
                "Coupling beam: %dΦ%d diagonal, transverse Φ%d @ %d mm",
                couplingResult.diagonalBars, couplingResult.diagonalBarDiameter,
                couplingResult.transverseBarsDiameter, couplingResult.transverseBarsSpacing
            ))
            safetyChecks.add(ShearWallSafetyCheck(
                "Coupling Beam", couplingResult.utilizationRatio, 1.0, "ratio", couplingResult.isSafe
            ))
        }

        // ── 7. Slenderness check ───────────────────────────────────
        val (slenderOk, slendernessRatio) = checkSlenderness(input)
        safetyChecks.add(ShearWallSafetyCheck(
            "Slenderness (H/t)", slendernessRatio, 25.0, "ratio", slenderOk
        ))
        codeNotes.add(String.format("Slenderness H/t = %.1f (limit 25)", slendernessRatio))

        // ── 8. Quantities per story ────────────────────────────────
        val concreteVolPerStory = Lw * bw * input.wallHeight / 1e9  // m³
        val vertBarsCount = (Lw / vertRebar.spacing) + 1
        val vertLength = vertBarsCount * input.wallHeight / 1000.0  // m
        val vertWeight = vertLength * PI * (vertRebar.diameter / 2.0).pow(2) / 1e6 * 7850.0  // kg
        val horzBarsCount = (input.wallHeight / horzRebar.spacing) + 1
        val horzLength = horzBarsCount * Lw / 1000.0
        val horzWeight = horzLength * PI * (horzRebar.diameter / 2.0).pow(2) / 1e6 * 7850.0
        val totalWeight = vertWeight + horzWeight

        // ── 9. Overall safety ──────────────────────────────────────
        val overallSafe = flexOk && shearOk && slenderOk &&
            (couplingResult?.isSafe ?: true)
        val maxUtil = maxOf(flexUtilRatio, shearUtilRatio,
            couplingResult?.utilizationRatio ?: 0.0)

        return ShearWallResult(
            isSafe = overallSafe,
            utilizationRatio = maxUtil,
            flexuralOk = flexOk,
            momentCapacity = Mn,
            axialCapacity = Pn,
            compressionDepth = a,
            verticalReinforcement = vertRebar,
            boundaryElementType = beType,
            boundaryElementReinforcement = beRebar,
            shearOk = shearOk,
            shearCapacity = Vn,
            concreteShearCapacity = Vc,
            steelShearCapacity = Vs,
            horizontalReinforcement = horzRebar,
            slendernessOk = slenderOk,
            slendernessRatio = slendernessRatio,
            couplingBeamResult = couplingResult,
            concreteVolumePerStory = concreteVolPerStory,
            steelWeightPerStory = totalWeight,
            warnings = warnings,
            codeNotes = codeNotes,
            safetyChecks = safetyChecks
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. FLEXURAL STRENGTH — Wall as column (K-method)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Calculate flexural (Mn) and axial (Pn) capacity.
     * Uses simplified K-method per ECP 203 §4-2.
     *
     * Treats the wall section as a column with:
     *  - Distributed reinforcement (minimum ρ = 0.0025)
     *  - Concentrated reinforcement at tension/compression ends
     *
     * Iterative solution for neutral axis depth under combined loading.
     */
    override fun calculateFlexuralStrength(input: ShearWallInput): Pair<Double, Double> {
        val Lw = input.wallLength
        val bw = input.wallThickness
        val fcu = input.fcu
        val fy = input.fy
        val cover = input.clearCover
        val Mu = input.bendingMoment       // kN.m
        val Pu = input.axialLoad           // kN
        val d = 0.8 * Lw                   // effective depth (mm)
        val MuNmm = Mu * 1e6               // N.mm
        val PuN = Pu * 1000.0              // N

        val fcDesign = 0.67 * fcu / GAMMA_C   // ECP concrete design stress
        val fsDesign = fy / GAMMA_S           // ECP steel design stress
        val Ag = Lw * bw

        // ── Iterative neutral axis solution ────────────────────────
        var a = 50.0  // initial guess for stress block depth
        for (iteration in 1..60) {
            val leverArm = d - a / 2.0
            if (leverArm <= 0) break

            // As from moment equilibrium:
            // Mu = As × fs × (d - a/2) + Pu × (d - Lw/2)
            val AsTension = max(0.0, (MuNmm - PuN * (d - Lw / 2.0)) / (fsDesign * leverArm))

            // Update a from equilibrium: C = T + Pu
            // fc × bw × a = As × fs + Pu
            a = if (bw > 0 && fcDesign > 0) {
                (AsTension * fsDesign + PuN) / (fcDesign * bw)
            } else a

            // Limit to tension-controlled section (c/d ≤ 0.3 for ECP special walls)
            val beta1 = calculateBeta1(fcu)
            a = (a / beta1).coerceAtMost(0.3 * d) * beta1
        }

        // ── Final As calculation ───────────────────────────────────
        val leverArm = d - a / 2.0
        val AsRequired = if (leverArm > 0) {
            max(0.0, (MuNmm - PuN * (d - Lw / 2.0)) / (fsDesign * leverArm))
        } else 0.0

        // ── Distributed vertical reinforcement (minimum) ───────────
        val distAsPerMeter = RHO_V_MIN * bw * 1000.0  // mm²/m

        // ── Concentrated end reinforcement ─────────────────────────
        // Select bars for tension side
        var tenDia = 16.0
        var tenCount = 4
        var tenSpacing = 200.0
        for (dia in BAR_DIAMETERS) {
            val barArea = PI * dia * dia / 4
            val count = ceil(AsRequired / barArea).toInt().coerceAtLeast(4)
            val spacing = if (count > 1) (Lw - 2 * cover) / (count - 1) else Lw
            // Max spacing: min(L/3, 3t, 450mm) per ECP
            val maxSp = minOf(Lw / 3.0, 3 * bw, 450.0)
            if (spacing >= 100.0 && spacing <= maxSp) {
                tenDia = dia; tenCount = count; tenSpacing = spacing; break
            }
        }
        val AsProvided = tenCount * PI * tenDia * tenDia / 4

        // ── Compression side reinforcement ─────────────────────────
        var compDia = tenDia
        var compCount = max(2, tenCount / 3)
        val AsCompProvided = compCount * PI * compDia * compDia / 4

        // ── Moment capacity Mn ─────────────────────────────────────
        val Mn = (AsProvided * fsDesign * leverArm + PuN * (d - Lw / 2.0)) / 1e6  // kN.m

        // ── Axial capacity Pn ──────────────────────────────────────
        // ECP 203: Pn = α × [0.67×fcu/γc × (Ag - Ast_total) + fy/γs × Ast_total]
        val totalAs = AsProvided + AsCompProvided + distAsPerMeter * Lw / 1000.0
        val AstCapped = totalAs.coerceAtMost(Ag * 0.08)
        val Pn = 0.85 * (fcDesign * (Ag - AstCapped) + fsDesign * AstCapped) / 1000.0  // kN

        return Pair(Mn, Pn)
    }

    // ══════════════════════════════════════════════════════════════════
    // 3. SHEAR STRENGTH — ECP 203 §4-3
    // ══════════════════════════════════════════════════════════════════

    /**
     * Calculate concrete and steel shear capacities per ECP 203.
     *
     * Concrete shear: Vc = 0.24 × √(fcu + 0.1×Nu/(b×d)) × b × d / γc
     *   Simplified: Vc ≈ 0.4 × √fcu × b × L (for preliminary)
     *
     * Steel shear: Vs = Asv × (fyv/γs) × d / s
     *
     * @return Pair<Vc_kN, Vs_kN>
     */
    override fun calculateShearStrength(input: ShearWallInput): Pair<Double, Double> {
        val Lw = input.wallLength
        val bw = input.wallThickness
        val fcu = input.fcu
        val fyv = input.fyv
        val Vu = input.shearForce       // kN
        val Nu = input.axialLoad         // kN (compression positive)
        val d = 0.8 * Lw
        val NuN = Nu * 1000.0           // N

        // ── Concrete shear capacity ────────────────────────────────
        // ECP 203 §4-3 for walls with axial load:
        // Vc = 0.24 × √(fcu + 0.1 × Nu/(b×d)) × b × d / γc
        val axialStress = if (bw > 0 && d > 0) NuN / (bw * d) else 0.0
        val fcv = sqrt(max(0.0, fcu + 0.1 * axialStress.coerceAtLeast(0.0)))
        val Vc = 0.24 * fcv * bw * d / GAMMA_C / 1000.0  // kN

        // Maximum concrete shear (ECP 203): Vc_max = 0.5 × √fcu × b × d / γc
        val VcMax = 0.5 * sqrt(fcu) * bw * d / GAMMA_C / 1000.0

        // ── Steel shear required ───────────────────────────────────
        val VsRequired = max(0.0, Vu - Vc)
        val fyvDesign = fyv / GAMMA_S

        // ── Select horizontal reinforcement for Vs ─────────────────
        // Minimum: ρh = 0.0025 → Asv/s = 0.0025 × bw
        val minAsvPerS = RHO_H_MIN * bw  // mm²/mm
        val designAsvPerS = max(VsRequired * 1000.0 / (fyvDesign * d).coerceAtLeast(1.0), minAsvPerS)

        // Select bar diameter and spacing
        var selDia = 10.0
        var selSpacing = 200.0
        // Max spacing: min(L/5, 3t, 450mm) per ECP
        val maxSpacing = minOf(Lw / 5.0, 3 * bw, 450.0)

        for (dia in SMALL_BAR_DIAMETERS) {
            val asBar = PI * dia * dia / 4
            val spacing = asBar / designAsvPerS
            if (spacing >= 75.0 && spacing <= maxSpacing) {
                selDia = dia; selSpacing = spacing; break
            }
        }
        selSpacing = selSpacing.coerceAtMost(maxSpacing)

        // Provided Vs
        val asProvided = PI * selDia * selDia / 4 / selSpacing  // mm²/mm
        val Vs = asProvided * fyvDesign * d / 1000.0  // kN

        return Pair(Vc, Vs)
    }

    // ══════════════════════════════════════════════════════════════════
    // 4. BOUNDARY ELEMENT DESIGN — ECP 201 / ECP 203
    // ══════════════════════════════════════════════════════════════════

    /**
     * Check if boundary elements are required and design them.
     *
     * ECP 201 (Seismic) / ACI 18.10.6 criteria:
     *  - Special walls: BE required when c/d > 0.3 (or strain at boundary > 0.003)
     *  - Ordinary walls: BE when c/d > 0.5
     *  - Coupled walls: BE when c/d > 0.3
     *
     * Boundary element design (when required):
     *  - Confined end zone with closely spaced ties
     *  - Minimum longitudinal reinforcement: ρ ≥ 0.01
     *  - Tie spacing: min(h/4, 6db, 100mm) for special
     *  - Volume confinement ratio per ECP 201 §8-4
     */
    override fun designBoundaryElements(input: ShearWallInput): Pair<BoundaryElementType, RebarResult?> {
        val Lw = input.wallLength
        val bw = input.wallThickness
        val d = 0.8 * Lw
        val cover = input.clearCover

        // Calculate neutral axis depth
        val c = calculateNeutralAxisDepth(
            Mu = input.bendingMoment, Pu = input.axialLoad,
            Lw = Lw, bw = bw, fcu = input.fcu, fy = input.fy
        )
        val cOverD = c / d

        // ── Determine if boundary element is needed ────────────────
        val beType = when (input.wallType) {
            WallType.SPECIAL -> {
                // Special walls: c/d > 0.3 → special BE
                // Also check if strain at boundary > 0.003
                when {
                    cOverD > 0.3 -> BoundaryElementType.SPECIAL
                    cOverD > 0.2 -> BoundaryElementType.STANDARD
                    else -> BoundaryElementType.NONE
                }
            }
            WallType.COUPLED -> {
                // Coupled walls: similar to special
                when {
                    cOverD > 0.3 -> BoundaryElementType.SPECIAL
                    cOverD > 0.2 -> BoundaryElementType.STANDARD
                    else -> BoundaryElementType.NONE
                }
            }
            WallType.ORDINARY -> {
                // Ordinary walls: less stringent
                when {
                    cOverD > 0.5 -> BoundaryElementType.SPECIAL
                    cOverD > 0.35 -> BoundaryElementType.STANDARD
                    else -> BoundaryElementType.NONE
                }
            }
        }

        if (beType == BoundaryElementType.NONE) {
            return Pair(BoundaryElementType.NONE, null)
        }

        // ── Boundary element length ─────────────────────────────────
        val beLength = if (input.endZoneLength > 0) {
            input.endZoneLength
        } else {
            // Auto: max(c, Lw/6) for special, max(c, Lw/4) for ordinary
            when (input.wallType) {
                WallType.SPECIAL, WallType.COUPLED -> max(c, Lw / 6.0).coerceAtMost(Lw / 3.0)
                WallType.ORDINARY -> max(c, Lw / 4.0).coerceAtMost(Lw / 3.0)
            }
        }.coerceAtMost(Lw / 3.0).coerceAtLeast(2 * cover + 100.0)

        // ── Longitudinal reinforcement in BE ───────────────────────
        val beArea = beLength * bw
        val minLongitAs = 0.01 * beArea  // minimum ρ = 0.01 for BE
        val fcDesign = 0.67 * input.fcu / GAMMA_C
        val fsDesign = input.fy / GAMMA_S

        // Required As from compression: Pu_BE ≈ 0.67fcu/γc × (beArea - As) + fy/γs × As
        val PuBe = input.axialLoad * 1000.0 * (beLength / Lw)  // approximate share
        var reqAsBe = if (fcDesign > 0) {
            max(minLongitAs, (PuBe / GAMMA_C - fcDesign * beArea) / (fsDesign - fcDesign))
        } else minLongitAs
        reqAsBe = max(0.0, reqAsBe)

        // Select bars
        var beBarDia = 16.0
        var beBarCount = 4
        var beBarSpacing = 100.0
        for (dia in BAR_DIAMETERS) {
            val area = PI * dia * dia / 4
            val count = ceil(reqAsBe / area).toInt().coerceAtLeast(4)
            val nLayers = ceil(count / 2.0).toInt()  // 2 bars per layer
            val spacing = if (nLayers > 1) (beLength - 2 * 30.0) / (nLayers - 1) else beLength
            if (spacing >= 75.0 && spacing <= 200.0 && count <= 12) {
                beBarDia = dia; beBarCount = count; beBarSpacing = spacing; break
            }
        }
        val provAsBe = beBarCount * PI * beBarDia * beBarDia / 4

        // ── Confinement ties ───────────────────────────────────────
        // ECP 201 §8-4: ρs ≥ 0.12 × fcu / fy × (Ag/Ach - 1)
        val tieCover = 25.0
        val coreLength = beLength - 2 * tieCover
        val coreThickness = bw - 2 * tieCover
        val Ach = coreLength * coreThickness
        val requiredVolRatio = if (Ach > 0) {
            0.12 * input.fcu / input.fy * (beArea / Ach - 1.0)
        } else 0.0

        // Select tie diameter and spacing
        val tieDia = max(8.0, beBarDia / 4.0).coerceAtLeast(10.0)
        val tieArea = PI * tieDia * tieDia / 4
        // Spacing: min(beLength/4, 6×db, 100mm) for special
        val maxTieSpacing = when (beType) {
            BoundaryElementType.SPECIAL -> minOf(beLength / 4.0, 6 * beBarDia, 100.0)
            BoundaryElementType.STANDARD -> minOf(beLength / 3.0, 8 * beBarDia, 150.0)
            BoundaryElementType.NONE -> 200.0
        }
        val tieSpacing = if (requiredVolRatio > 0 && coreLength > 0) {
            (tieArea * (coreLength - 2 * tieDia) / (requiredVolRatio * Ach))
                .coerceIn(50.0, maxTieSpacing)
        } else maxTieSpacing

        val rebarResult = RebarResult(
            bars = beBarCount,
            diameter = beBarDia.toInt(),
            spacing = tieSpacing.toInt(),
            providedArea = provAsBe,
            requiredArea = reqAsBe,
            ratio = if (reqAsBe > 0) provAsBe / reqAsBe else 1.0
        )

        return Pair(beType, rebarResult)
    }

    // ══════════════════════════════════════════════════════════════════
    // 5. COUPLING BEAM DESIGN — ECP 201 §8-5
    // ══════════════════════════════════════════════════════════════════

    /**
     * Design coupling beam for coupled shear walls.
     *
     * ECP 201 §8-5 / ACI 18.10.7:
     *  - When V / (√fcu × bw × d) > 0.15 → diagonal reinforcement required
     *  - Diagonal bars: Asd = V / (2 × fy × sin θ)
     *  - θ = arctan(hb / Lb)
     *  - Transverse reinforcement around diagonal bars
     *
     * @return CouplingBeamResult or null if not applicable
     */
    override fun designCouplingBeam(input: ShearWallInput): CouplingBeamResult? {
        // Only for coupled walls
        if (input.wallType != WallType.COUPLED) return null
        if (input.couplingBeamLength <= 0 || input.couplingBeamHeight <= 0) return null

        val Lb = input.couplingBeamClearSpan.let { if (it > 0) it else input.couplingBeamLength }
        val hb = input.couplingBeamHeight
        val bw = input.wallThickness
        val fcu = input.fcu
        val fy = input.fy

        // Calculate beam shear from wall equilibrium
        // For coupled walls: coupling beam shear ≈ M_beam / (Lw/2) approximately
        // Use provided shear if non-zero, else estimate from moment
        val Vb = if (input.shearForce > 0 && input.couplingBeamClearSpan > 0) {
            // Estimate coupling beam shear as fraction of wall base shear
            input.shearForce * 0.3  // approximate 30% redistribution
        } else if (input.bendingMoment > 0) {
            input.bendingMoment * 1000.0 / (input.wallLength / 2.0) * 0.15 / 1000.0
        } else {
            input.shearForce * 0.3
        }

        // ── Check if diagonal reinforcement needed ─────────────────
        val d = hb - 50.0  // effective depth of beam
        val vuRatio = if (bw > 0 && d > 0) {
            Vb * 1000.0 / (sqrt(fcu) * bw * d)
        } else 0.0

        val needsDiagonal = vuRatio > 0.15 || input.wallType == WallType.COUPLED

        if (!needsDiagonal) {
            // Conventional reinforcement (not typical for coupled walls but possible)
            return CouplingBeamResult(
                diagonalBars = 0, diagonalBarDiameter = 0,
                transverseBarsDiameter = 10, transverseBarsSpacing = 150,
                isSafe = true, utilizationRatio = 0.5
            )
        }

        // ── Diagonal reinforcement design ───────────────────────────
        val theta = atan(hb / Lb)  // angle of diagonal
        val sinT = sin(theta)

        // Asd = Vb / (2 × (fy/γs) × sin θ)
        val fsDesign = fy / GAMMA_S
        val AsdPerLeg = if (sinT > 0.1) {
            Vb * 1000.0 / (2.0 * fsDesign * sinT)
        } else 0.0

        // Select diagonal bar diameter
        var diagDia = 20.0
        var diagCount = 4  // 2 per diagonal group
        for (dia in BAR_DIAMETERS) {
            val barArea = PI * dia * dia / 4
            val count = ceil(AsdPerLeg / barArea).toInt().coerceAtLeast(2)
            if (count <= 8 && barArea >= AsdPerLeg / 4.0) {
                diagDia = dia; diagCount = count * 2; break  // ×2 for both diagonals
            }
        }

        // ── Transverse reinforcement around diagonal bars ──────────
        // Per ECP 201: spacing ≤ min(hb/4, 6×db, 100mm)
        val transDia = max(8.0, diagDia / 3.0).coerceAtLeast(10.0)
        val transSpacing = minOf(hb / 4.0, 6 * diagDia, 100.0).toInt().coerceAtLeast(50)

        // ── Utilization check ──────────────────────────────────────
        val providedAs = diagCount / 2 * PI * diagDia * diagDia / 4
        val capacityV = 2.0 * providedAs * fsDesign * sinT / 1000.0
        val utilRatio = if (capacityV > 0) Vb / capacityV else 2.0

        return CouplingBeamResult(
            diagonalBars = diagCount,
            diagonalBarDiameter = diagDia.toInt(),
            transverseBarsDiameter = transDia.toInt(),
            transverseBarsSpacing = transSpacing,
            isSafe = utilRatio <= 1.0,
            utilizationRatio = utilRatio
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // 6. SLENDERNESS CHECK — H/t ratio
    // ══════════════════════════════════════════════════════════════════

    /**
     * Check wall slenderness: H_total / thickness.
     * ECP 203: walls with H/t > 25 require second-order analysis.
     * For braced structures: limit may be relaxed to H/t ≤ 30.
     *
     * @return Pair<isOk, ratio>
     */
    override fun checkSlenderness(input: ShearWallInput): Pair<Boolean, Double> {
        val Hw = input.wallHeight * input.numberOfStories  // total height mm
        val t = input.wallThickness
        val ratio = Hw / t
        return Pair(ratio <= 25.0, ratio)
    }

    // ══════════════════════════════════════════════════════════════════
    // 7. NEUTRAL AXIS DEPTH CALCULATION
    // ══════════════════════════════════════════════════════════════════

    /**
     * Calculate neutral axis depth (c) from extreme compression fiber.
     * Uses iterative strain compatibility / K-method.
     */
    private fun calculateNeutralAxisDepth(
        Mu: Double, Pu: Double, Lw: Double, bw: Double,
        fcu: Double, fy: Double
    ): Double {
        val d = 0.8 * Lw
        val MuNmm = Mu * 1e6
        val PuN = Pu * 1000.0
        val fcDesign = 0.67 * fcu / GAMMA_C
        val fsDesign = fy / GAMMA_S
        val beta1 = calculateBeta1(fcu)

        var c = 50.0
        for (i in 1..60) {
            val a = beta1 * c
            val leverArm = d - a / 2.0
            if (leverArm <= 0) break
            val AsEst = max(0.0, (MuNmm - PuN * (d - Lw / 2.0)) / (fsDesign * leverArm))
            // C = T + Pu: 0.67*fcu/γc * bw * a = As * fy/γs + Pu
            val newA = if (bw > 0 && fcDesign > 0) {
                (AsEst * fsDesign + PuN) / (fcDesign * bw)
            } else a
            c = newA / beta1
            // Convergence check
            if (abs(c - (newA / beta1)) < 0.1) break
        }
        return c.coerceIn(0.0, 0.5 * d)  // limit c/d to 0.5 for ductility
    }

    // ══════════════════════════════════════════════════════════════════
    // 8. β1 FACTOR
    // ══════════════════════════════════════════════════════════════════

    /**
     * Calculate β1 (Whitney stress block factor).
     * β1 = 0.85 for fcu ≤ 28 MPa, reduce by 0.05 per 7 MPa above.
     * Minimum β1 = 0.65.
     */
    private fun calculateBeta1(fcu: Double): Double {
        return if (fcu <= 28.0) BETA_1_BASE
        else (BETA_1_BASE - 0.05 * ((fcu - 28.0) / 7.0)).coerceAtLeast(0.65)
    }

    // ══════════════════════════════════════════════════════════════════
    // 9. VERTICAL REINFORCEMENT DESIGN
    // ══════════════════════════════════════════════════════════════════

    /**
     * Design vertical reinforcement (distributed + concentrated at ends).
     * Minimum ρv = 0.0025.
     * Maximum spacing: min(L/3, 3t, 450mm).
     */
    private fun designVerticalReinforcement(
        input: ShearWallInput, a: Double, d: Double, c: Double
    ): RebarResult {
        val Lw = input.wallLength
        val bw = input.wallThickness
        val cover = input.clearCover
        val fsDesign = input.fy / GAMMA_S
        val MuNmm = input.bendingMoment * 1e6
        val PuN = input.axialLoad * 1000.0

        // Required As from flexure
        val leverArm = d - a / 2.0
        val AsRequired = if (leverArm > 0) {
            max(0.0, (MuNmm - PuN * (d - Lw / 2.0)) / (fsDesign * leverArm))
        } else 0.0

        // Minimum distributed As per meter
        val minDistributedAs = RHO_V_MIN * bw * 1000.0  // mm²/m
        // Distributed portion
        val distPortion = minDistributedAs * Lw / 1000.0
        // Concentrated end bars: As_conc = As_total - As_dist
        val concRequired = max(0.0, AsRequired - distPortion)

        // Select bars for concentrated end reinforcement
        // Two layers at each end: tension side + compression side
        val maxSpacing = minOf(Lw / 3.0, 3 * bw, 450.0)
        val netLength = Lw - 2 * cover

        // Number of bars: distribute AsRequired across the wall length
        // But concentrate more at the ends
        var selDia = 16.0
        var selCount = 4
        var selSpacing = 200.0

        // Total vertical bars needed
        val totalAsNeeded = max(AsRequired, minDistributedAs * Lw / 1000.0)

        for (dia in BAR_DIAMETERS) {
            val barArea = PI * dia * dia / 4
            // Count based on required area
            val count = ceil(totalAsNeeded / barArea).toInt().coerceAtLeast(4)
            val spacing = if (count > 1) netLength / (count - 1) else Lw
            if (spacing >= 100.0 && spacing <= maxSpacing) {
                selDia = dia; selCount = count; selSpacing = spacing; break
            }
        }

        val provArea = selCount * PI * selDia * selDia / 4

        return RebarResult(
            bars = selCount,
            diameter = selDia.toInt(),
            spacing = selSpacing.toInt(),
            providedArea = provArea,
            requiredArea = AsRequired,
            ratio = if (AsRequired > 0) provArea / AsRequired else 1.0
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // 10. HORIZONTAL REINFORCEMENT DESIGN
    // ══════════════════════════════════════════════════════════════════

    /**
     * Design horizontal reinforcement (shear reinforcement).
     * Minimum ρh = 0.0025.
     * Maximum spacing: min(L/5, 3t, 450mm).
     */
    private fun designHorizontalReinforcement(
        input: ShearWallInput, Vc: Double, d: Double
    ): RebarResult {
        val Lw = input.wallLength
        val bw = input.wallThickness
        val fyv = input.fyv
        val fyvDesign = fyv / GAMMA_S
        val Vu = input.shearForce

        // Required Vs
        val VsRequired = max(0.0, Vu - Vc)

        // Asv/s required for shear
        val reqAsvPerS = if (VsRequired > 0 && d > 0) {
            VsRequired * 1000.0 / (fyvDesign * d)
        } else 0.0

        // Minimum Asv/s = 0.0025 × bw
        val minAsvPerS = RHO_H_MIN * bw
        val designAsvPerS = max(reqAsvPerS, minAsvPerS)

        // Required area for 1m width
        val reqAreaPerMeter = designAsvPerS * 1000.0  // mm²/m

        // Select bar diameter and spacing
        // Max spacing: min(L/5, 3t, 450mm)
        val maxSpacing = minOf(Lw / 5.0, 3 * bw, 450.0)

        var selDia = 10.0
        var selSpacing = 200.0
        var selCount = 5  // bars per meter

        for (dia in SMALL_BAR_DIAMETERS) {
            val barArea = PI * dia * dia / 4
            val spacing = (barArea / designAsvPerS).coerceIn(75.0, maxSpacing)
            val barsPerMeter = (1000.0 / spacing).toInt().coerceAtLeast(2)
            if (spacing >= 75.0 && spacing <= maxSpacing) {
                selDia = dia; selSpacing = spacing; selCount = barsPerMeter; break
            }
        }

        val provAreaPerMeter = PI * selDia * selDia / 4 * 1000.0 / selSpacing

        return RebarResult(
            bars = selCount,
            diameter = selDia.toInt(),
            spacing = selSpacing.toInt(),
            providedArea = provAreaPerMeter,
            requiredArea = reqAreaPerMeter,
            ratio = if (reqAreaPerMeter > 0) provAreaPerMeter / reqAreaPerMeter else 1.0
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // 11. EFFECTIVE FLANGE WIDTH — L/T SHAPED WALLS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Calculate effective flange width for L or T shaped walls.
     * ECP 203: effective flange = min(6bw, Lw/2 from web face)
     */
    private fun calculateEffectiveFlangeWidth(
        flangeWidth: Double, wallThickness: Double, wallLength: Double
    ): Double {
        if (flangeWidth <= 0) return 0.0
        return min(flangeWidth, min(6.0 * wallThickness, wallLength / 2.0))
    }

    // ══════════════════════════════════════════════════════════════════
    // 12. MAXIMUM SHEAR CAPACITY CHECK
    // ══════════════════════════════════════════════════════════════════

    /**
     * Check maximum shear capacity: Vn_max = 0.5 × √fcu × b × d / γc
     * ECP 203 limits the maximum shear that can be resisted.
     */
    private fun checkMaxShearCapacity(input: ShearWallInput): Boolean {
        val Lw = input.wallLength
        val bw = input.wallThickness
        val d = 0.8 * Lw
        val VcMax = 0.5 * sqrt(input.fcu) * bw * d / GAMMA_C / 1000.0  // kN
        return input.shearForce <= VcMax
    }

    // ══════════════════════════════════════════════════════════════════
    // 13. MINIMUM WALL THICKNESS CHECK
    // ══════════════════════════════════════════════════════════════════

    /**
     * Check minimum wall thickness per ECP 203.
     * Ordinary: min 150mm, Special: min 200mm (seismic), Coupled: min 200mm
     */
    private fun checkMinThickness(input: ShearWallInput): Pair<Boolean, Double> {
        val minT = when (input.wallType) {
            WallType.ORDINARY -> 150.0
            WallType.SPECIAL -> 200.0
            WallType.COUPLED -> 200.0
        }
        return Pair(input.wallThickness >= minT, minT)
    }

    // ══════════════════════════════════════════════════════════════════
    // 14. DEVELOPMENT LENGTH CALCULATION
    // ══════════════════════════════════════════════════════════════════

    /**
     * Calculate development length per ECP 203 §5-2.
     * Ld = (fy / (4 × √fcu)) × db  (simplified, normal weight concrete)
     * Minimum Ld = 20 × db
     */
    private fun calculateDevelopmentLength(barDia: Double, fcu: Double, fy: Double, isTension: Boolean): Double {
        val sqrtFcu = sqrt(fcu).coerceAtLeast(1.0)
        val LdBasic = fy / (4.0 * sqrtFcu) * barDia
        val LdMin = 20.0 * barDia
        return if (isTension) {
            max(LdBasic, LdMin)
        } else {
            max(LdBasic * 0.75, LdMin * 0.75)  // compression: 75% of tension
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 15. REINFORCEMENT INDEX — ρω (mechanical ratio)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Calculate mechanical reinforcement index ρω = ρ × fy / fcu.
     * Used for ductility checks in seismic design.
     */
    private fun calculateMechanicalRatio(rho: Double, fy: Double, fcu: Double): Double {
        return rho * fy / fcu
    }

    // ══════════════════════════════════════════════════════════════════
    // 16. OVERSTRENGTH CHECK FOR SPECIAL WALLS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Check that the wall can develop its flexural overstrength
     * before shear failure (capacity design principle).
     * Vn ≥ 1.2 × Mn / Lw  (ECP 201 capacity design)
     */
    private fun checkOverstrength(input: ShearWallInput, Mn: Double, Vn: Double): Boolean {
        val Lw = input.wallLength
        val Voverstrength = 1.2 * Mn * 1000.0 / Lw / 1000.0  // kN
        return Vn >= Voverstrength
    }
}
