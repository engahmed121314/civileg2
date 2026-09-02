package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.*
import com.civileg.app.domain.calculations.base.ShearWallDesign
import kotlin.math.*

/**
 * SBC 304 Shear Wall Design Implementation
 *
 * References:
 *  - SBC 304 §6-7: Structural walls (general provisions)
 *  - SBC 304 §4-2: Flexural design (K-method with fcu directly)
 *  - SBC 304 §4-3: Shear design
 *  - SBC 304 §4-2-6: Ties and confinement
 *
 * Key SBC differences from ACI 318:
 *  - Uses fcu directly (cube strength), not f'c = 0.8×fcu
 *  - Material safety factors: γc = 1.5, γs = 1.15
 *  - Strength reduction: φ_flexure = 0.9, φ_shear = 0.75, φ_axial = 0.65
 *  - Load factors: 1.4DL + 1.6LL
 *
 * Key design rules:
 *  - Wall as column: flexural design using K-method or strain compatibility
 *  - Axial + bending interaction with φ factors
 *  - Concrete shear: Vc = 0.24×√(fcu + 0.1×Nu/(b×d)) × b × d
 *    then φ × Vc where φ = 0.75
 *  - Steel shear: Vs = Asv×(fyv/γs)×d / s
 *  - Boundary element: when c/d > 0.3 (special) or c/d > 0.5 (ordinary)
 *  - Minimum ρv = 0.0025, ρh = 0.0025
 *  - Max spacing vertical: min(L/3, 3t, 450mm)
 *  - Max spacing horizontal: min(L/5, 3t, 450mm)
 *  - Coupling beam: diagonal reinforcement when V/(√fcu × bw × d) > 0.15
 */
class SBCShearWall : ShearWallDesign {

    companion object {
        // Material safety factors — SBC 304 §4
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15

        // Strength reduction factors — SBC 304
        private const val PHI_FLEXURE = 0.9
        private const val PHI_SHEAR = 0.75
        private const val PHI_AXIAL = 0.65

        // Stress block parameters
        private const val ALPHA = 0.85
        private const val BETA_1_BASE = 0.85
        private const val EPSILON_CU = 0.003
        private const val ES = 200000.0

        // Reinforcement limits — SBC 304 §6-7
        private const val RHO_V_MIN = 0.0025
        private const val RHO_H_MIN = 0.0025
        private const val RHO_V_MAX = 0.06

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

        codeNotes.add("SBC 304 §6-7: Shear Wall Design")
        codeNotes.add("Wall Type: ${input.wallType.displayName}")
        codeNotes.add("γc = $GAMMA_C, γs = $GAMMA_S, φ_flex = $PHI_FLEXURE, φ_shear = $PHI_SHEAR")

        // ── Effective section properties ───────────────────────────
        val Lw = input.wallLength
        val bw = input.wallThickness
        val Hw = input.wallHeight * input.numberOfStories
        val d = 0.8 * Lw
        val Ag = Lw * bw
        val cover = input.clearCover

        // Effective flange width for L/T walls
        val flangeW = if (input.flangeWidth > 0) {
            min(input.flangeWidth, min(6.0 * bw + Lw / 2.0, Lw))
        } else 0.0
        val effectiveBw = bw + if (input.flangeThickness > 0) input.flangeThickness else 0.0

        codeNotes.add(String.format(
            "Lw = %.0f mm, bw = %.0f mm, Hw = %.0f mm (%d stories)",
            Lw, bw, Hw, input.numberOfStories
        ))

        // Input validation
        if (Lw < 5 * bw) warnings.add("SBC: Wall length < 5× thickness — may behave as a column")
        if (bw < 150.0) warnings.add("SBC: Wall thickness < 150mm — minimum recommended")

        // ── 1. Compression zone depth (a) ──────────────────────────
        val c = calculateNeutralAxisDepth(
            Mu = input.bendingMoment, Pu = input.axialLoad,
            Lw = Lw, bw = bw, fcu = input.fcu, fy = input.fy
        )
        val beta1 = calculateBeta1(input.fcu)
        val a = beta1 * c
        codeNotes.add(String.format("Neutral axis c = %.1f mm, block depth a = β1×c = %.1f mm", c, a))

        // ── 2. Flexural design (SBC with φ = 0.9) ──────────────────
        val (Mn, Pn) = calculateFlexuralStrength(input)
        // Apply φ factors for SBC
        val phiMn = PHI_FLEXURE * Mn
        val phiPn = PHI_AXIAL * Pn
        val flexOk = input.bendingMoment <= phiMn && input.axialLoad <= phiPn
        val flexUtilRatio = max(
            if (phiMn > 0) input.bendingMoment / phiMn else 2.0,
            if (phiPn > 0) input.axialLoad / phiPn else 0.0
        )
        safetyChecks.add(ShearWallSafetyCheck(
            "Flexure (Mu/φMn)",
            if (phiMn > 0) input.bendingMoment / phiMn else 2.0,
            1.0, "ratio", flexOk
        ))
        safetyChecks.add(ShearWallSafetyCheck(
            "Axial (Pu/φPn)",
            if (phiPn > 0) input.axialLoad / phiPn else 2.0,
            1.0, "ratio", input.axialLoad <= phiPn
        ))
        codeNotes.add(String.format("φMn = %.1f kN.m (Mu = %.1f kN.m)", phiMn, input.bendingMoment))
        codeNotes.add(String.format("φPn = %.1f kN (Pu = %.1f kN)", phiPn, input.axialLoad))

        // ── 3. Vertical reinforcement selection ────────────────────
        val vertRebar = designVerticalReinforcement(input, a, d, c)
        codeNotes.add(String.format(
            "Vertical: %dΦ%d @ %d mm c/c (As = %.0f mm², req = %.0f mm²)",
            vertRebar.bars, vertRebar.diameter, vertRebar.spacing,
            vertRebar.providedArea, vertRebar.requiredArea
        ))

        // ── 4. Shear design (SBC with φ = 0.75) ────────────────────
        val (VcNominal, Vs) = calculateShearStrength(input)
        val phiVn = PHI_SHEAR * (VcNominal + Vs)
        val shearOk = input.shearForce <= phiVn
        val shearUtilRatio = if (phiVn > 0) input.shearForce / phiVn else 2.0
        safetyChecks.add(ShearWallSafetyCheck(
            "Shear (Vu/φVn)", input.shearForce, phiVn, "kN", shearOk
        ))
        codeNotes.add(String.format(
            "Vc = %.1f kN, Vs = %.1f kN, φVn = %.1f kN (Vu = %.1f kN)",
            VcNominal, Vs, phiVn, input.shearForce
        ))

        // ── Capacity design — flexural overstrength (A9-FIX) ───────
        // checkOverstrength() existed but was never called. Enforced for
        // SPECIAL/COUPLED walls: Vn ≥ 1.2·Mn/Lw (SBC 304 capacity design).
        val overstrengthOk = if (input.wallType != WallType.ORDINARY) {
            val overstrengthDemand = 1.2 * Mn * 1000.0 / Lw / 1000.0  // kN
            val ok = checkOverstrength(input, Mn, VcNominal + Vs)
            safetyChecks.add(ShearWallSafetyCheck(
                "Overstrength Vn>=1.2Mn/Lw", VcNominal + Vs, overstrengthDemand, "kN", ok
            ))
            ok
        } else true

        // Horizontal reinforcement
        val horzRebar = designHorizontalReinforcement(input, VcNominal, d)
        codeNotes.add(String.format(
            "Horizontal: %dΦ%d @ %d mm c/c (As = %.0f mm², req = %.0f mm²)",
            horzRebar.bars, horzRebar.diameter, horzRebar.spacing,
            horzRebar.providedArea, horzRebar.requiredArea
        ))

        // Check reinforcement ratios
        val rhoV = vertRebar.providedArea / (bw * 1000.0)
        val rhoH = horzRebar.providedArea / (bw * 1000.0)
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
        val concreteVolPerStory = Lw * bw * input.wallHeight / 1e9
        val vertBarsCount = (Lw / vertRebar.spacing) + 1
        val vertLength = vertBarsCount * input.wallHeight / 1000.0
        val vertWeight = vertLength * PI * (vertRebar.diameter / 2.0).pow(2) / 1e6 * 7850.0
        val horzBarsCount = (input.wallHeight / horzRebar.spacing) + 1
        val horzLength = horzBarsCount * Lw / 1000.0
        val horzWeight = horzLength * PI * (horzRebar.diameter / 2.0).pow(2) / 1e6 * 7850.0
        val totalWeight = vertWeight + horzWeight

        // ── 9. Overall safety ──────────────────────────────────────
        val overallSafe = flexOk && shearOk && slenderOk && overstrengthOk && (couplingResult?.isSafe ?: true)
        val maxUtil = maxOf(flexUtilRatio, shearUtilRatio, couplingResult?.utilizationRatio ?: 0.0)

        return ShearWallResult(
            isSafe = overallSafe, utilizationRatio = maxUtil,
            flexuralOk = flexOk, momentCapacity = phiMn, axialCapacity = phiPn,
            compressionDepth = a, verticalReinforcement = vertRebar,
            boundaryElementType = beType, boundaryElementReinforcement = beRebar,
            shearOk = shearOk, shearCapacity = phiVn,
            concreteShearCapacity = PHI_SHEAR * VcNominal, steelShearCapacity = PHI_SHEAR * Vs,
            horizontalReinforcement = horzRebar,
            slendernessOk = slenderOk, slendernessRatio = slendernessRatio,
            couplingBeamResult = couplingResult,
            concreteVolumePerStory = concreteVolPerStory, steelWeightPerStory = totalWeight,
            warnings = warnings, codeNotes = codeNotes, safetyChecks = safetyChecks
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. FLEXURAL STRENGTH — Wall as column (K-method, SBC with fcu)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Calculate nominal flexural (Mn) and axial (Pn) capacity.
     * Uses K-method per SBC 304 §4-2 with fcu directly.
     * φ factors are applied in designWall, not here.
     *
     * @return Pair<Mn_kN_m, Pn_kN> (nominal, before φ)
     */
    override fun calculateFlexuralStrength(input: ShearWallInput): Pair<Double, Double> {
        val Lw = input.wallLength
        val bw = input.wallThickness
        val fcu = input.fcu
        val fy = input.fy
        val d = 0.8 * Lw
        val MuNmm = input.bendingMoment * 1e6
        val PuN = input.axialLoad * 1000.0

        // SBC design stresses (fcu directly, not f'c)
        val fcDesign = 0.67 * fcu / GAMMA_C
        val fsDesign = fy / GAMMA_S
        val Ag = Lw * bw

        // Iterative neutral axis solution
        var a = 50.0
        for (iteration in 1..60) {
            val leverArm = d - a / 2.0
            if (leverArm <= 0) break
            val AsTension = max(0.0, (MuNmm - PuN * (d - Lw / 2.0)) / (fsDesign * leverArm))
            a = if (bw > 0 && fcDesign > 0) {
                (AsTension * fsDesign + PuN) / (fcDesign * bw)
            } else a
            val beta1 = calculateBeta1(fcu)
            a = (a / beta1).coerceAtMost(0.3 * d) * beta1
        }

        val leverArm = d - a / 2.0
        val AsRequired = if (leverArm > 0) {
            max(0.0, (MuNmm - PuN * (d - Lw / 2.0)) / (fsDesign * leverArm))
        } else 0.0

        val distAsPerMeter = RHO_V_MIN * bw * 1000.0

        // Select bars for tension side
        var tenDia = 16.0
        var tenCount = 4
        var tenSpacing = 200.0
        for (dia in BAR_DIAMETERS) {
            val barArea = PI * dia * dia / 4
            val count = ceil(AsRequired / barArea).toInt().coerceAtLeast(4)
            val spacing = if (count > 1) (Lw - 2 * input.clearCover) / (count - 1) else Lw
            val maxSp = minOf(Lw / 3.0, 3 * bw, 450.0)
            if (spacing >= 100.0 && spacing <= maxSp) {
                tenDia = dia; tenCount = count; tenSpacing = spacing; break
            }
        }
        val AsProvided = tenCount * PI * tenDia * tenDia / 4

        // Compression side reinforcement
        var compDia = tenDia
        var compCount = max(2, tenCount / 3)
        val AsCompProvided = compCount * PI * compDia * compDia / 4

        // Nominal moment capacity Mn (before φ)
        val Mn = (AsProvided * fsDesign * leverArm + PuN * (d - Lw / 2.0)) / 1e6

        // Nominal axial capacity Pn (before φ)
        // SBC: Pn = 0.85 × [0.67×fcu/γc × (Ag - Ast) + fy/γs × Ast]
        val totalAs = AsProvided + AsCompProvided + distAsPerMeter * Lw / 1000.0
        val AstCapped = totalAs.coerceAtMost(Ag * 0.08)
        val Pn = 0.85 * (fcDesign * (Ag - AstCapped) + fsDesign * AstCapped) / 1000.0

        return Pair(Mn, Pn)
    }

    // ══════════════════════════════════════════════════════════════════
    // 3. SHEAR STRENGTH — SBC 304 §4-3
    // ══════════════════════════════════════════════════════════════════

    /**
     * Calculate nominal concrete and steel shear capacities per SBC 304.
     * φ = 0.75 applied in designWall, not here.
     *
     * Concrete shear (nominal, using fcu directly):
     *   Vc = 0.24 × √(fcu + 0.1×Nu/(b×d)) × b × d
     *
     * Steel shear (nominal):
     *   Vs = Asv × (fyv/γs) × d / s
     *
     * @return Pair<VcNominal_kN, VsNominal_kN>
     */
    override fun calculateShearStrength(input: ShearWallInput): Pair<Double, Double> {
        val Lw = input.wallLength
        val bw = input.wallThickness
        val fcu = input.fcu
        val fyv = input.fyv
        val Vu = input.shearForce
        val Nu = input.axialLoad
        val d = 0.8 * Lw
        val NuN = Nu * 1000.0

        // Nominal concrete shear using fcu directly (SBC 304)
        val axialStress = if (bw > 0 && d > 0) NuN / (bw * d) else 0.0
        val fcv = sqrt(max(0.0, fcu + 0.1 * axialStress.coerceAtLeast(0.0)))
        val Vc = 0.24 * fcv * bw * d / 1000.0  // kN (nominal)

        // Maximum concrete shear (SBC 304): Vc_max = 0.5 × √fcu × b × d
        val VcMax = 0.5 * sqrt(fcu) * bw * d / 1000.0

        // Steel shear required (against nominal, φ applied separately)
        val VcFactored = PHI_SHEAR * Vc
        val VsRequired = max(0.0, Vu - VcFactored)
        val fyvDesign = fyv / GAMMA_S

        // Select horizontal reinforcement
        val minAsvPerS = RHO_H_MIN * bw
        val designAsvPerS = max(VsRequired * 1000.0 / (PHI_SHEAR * fyvDesign * d).coerceAtLeast(1.0), minAsvPerS)

        var selDia = 10.0
        var selSpacing = 200.0
        val maxSpacing = minOf(Lw / 5.0, 3 * bw, 450.0)

        for (dia in SMALL_BAR_DIAMETERS) {
            val asBar = PI * dia * dia / 4
            val spacing = (asBar / designAsvPerS).coerceIn(75.0, maxSpacing)
            if (spacing >= 75.0 && spacing <= maxSpacing) {
                selDia = dia; selSpacing = spacing; break
            }
        }
        selSpacing = selSpacing.coerceAtMost(maxSpacing)

        val asProvided = PI * selDia * selDia / 4 / selSpacing
        val Vs = asProvided * fyvDesign * d / 1000.0  // kN (nominal)

        return Pair(Vc, Vs)
    }

    // ══════════════════════════════════════════════════════════════════
    // 4. BOUNDARY ELEMENT DESIGN — SBC 304 §6-7
    // ══════════════════════════════════════════════════════════════════

    /**
     * Check if boundary elements are required and design them.
     *
     * SBC 304 criteria (similar to ACI 318):
     *  - Special walls: BE required when c/d > 0.3
     *  - Ordinary walls: BE when c/d > 0.5
     *  - Coupled walls: BE when c/d > 0.3
     */
    override fun designBoundaryElements(input: ShearWallInput): Pair<BoundaryElementType, RebarResult?> {
        val Lw = input.wallLength
        val bw = input.wallThickness
        val d = 0.8 * Lw
        val cover = input.clearCover

        val c = calculateNeutralAxisDepth(
            Mu = input.bendingMoment, Pu = input.axialLoad,
            Lw = Lw, bw = bw, fcu = input.fcu, fy = input.fy
        )
        val cOverD = c / d

        // Determine BE requirement
        val beType = when (input.wallType) {
            WallType.SPECIAL, WallType.COUPLED -> {
                when {
                    cOverD > 0.3 -> BoundaryElementType.SPECIAL
                    cOverD > 0.2 -> BoundaryElementType.STANDARD
                    else -> BoundaryElementType.NONE
                }
            }
            WallType.ORDINARY -> {
                when {
                    cOverD > 0.5 -> BoundaryElementType.SPECIAL
                    cOverD > 0.35 -> BoundaryElementType.STANDARD
                    else -> BoundaryElementType.NONE
                }
            }
        }

        if (beType == BoundaryElementType.NONE) return Pair(BoundaryElementType.NONE, null)

        // Boundary element length
        val beLength = if (input.endZoneLength > 0) {
            input.endZoneLength
        } else {
            when (input.wallType) {
                WallType.SPECIAL, WallType.COUPLED -> max(c, Lw / 6.0).coerceAtMost(Lw / 3.0)
                WallType.ORDINARY -> max(c, Lw / 4.0).coerceAtMost(Lw / 3.0)
            }
        }.coerceAtMost(Lw / 3.0).coerceAtLeast(2 * cover + 100.0)

        // Longitudinal reinforcement in BE
        val beArea = beLength * bw
        val minLongitAs = 0.01 * beArea
        val fcDesign = 0.67 * input.fcu / GAMMA_C
        val fsDesign = input.fy / GAMMA_S

        val PuBe = input.axialLoad * 1000.0 * (beLength / Lw)
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
            val nLayers = ceil(count / 2.0).toInt()
            val spacing = if (nLayers > 1) (beLength - 2 * 30.0) / (nLayers - 1) else beLength
            if (spacing >= 75.0 && spacing <= 200.0 && count <= 12) {
                beBarDia = dia; beBarCount = count; beBarSpacing = spacing; break
            }
        }
        val provAsBe = beBarCount * PI * beBarDia * beBarDia / 4

        // Confinement ties — SBC 304
        val tieCover = 25.0
        val coreLength = beLength - 2 * tieCover
        val coreThickness = bw - 2 * tieCover
        val Ach = coreLength * coreThickness
        val requiredVolRatio = if (Ach > 0) {
            0.12 * input.fcu / input.fy * (beArea / Ach - 1.0)
        } else 0.0

        val tieDia = max(8.0, beBarDia / 4.0).coerceAtLeast(10.0)
        val tieArea = PI * tieDia * tieDia / 4
        val maxTieSpacing = when (beType) {
            BoundaryElementType.SPECIAL -> minOf(beLength / 4.0, 6 * beBarDia, 100.0)
            BoundaryElementType.STANDARD -> minOf(beLength / 3.0, 8 * beBarDia, 150.0)
            BoundaryElementType.NONE -> 200.0
        }
        val tieSpacing = if (requiredVolRatio > 0 && coreLength > 0) {
            (tieArea * (coreLength - 2 * tieDia) / (requiredVolRatio * Ach))
                .coerceIn(50.0, maxTieSpacing)
        } else maxTieSpacing

        return Pair(beType, RebarResult(
            bars = beBarCount, diameter = beBarDia.toInt(),
            spacing = tieSpacing.toInt(),
            providedArea = provAsBe, requiredArea = reqAsBe,
            ratio = if (reqAsBe > 0) provAsBe / reqAsBe else 1.0
        ))
    }

    // ══════════════════════════════════════════════════════════════════
    // 5. COUPLING BEAM DESIGN — SBC 304 §6-7
    // ══════════════════════════════════════════════════════════════════

    override fun designCouplingBeam(input: ShearWallInput): CouplingBeamResult? {
        if (input.wallType != WallType.COUPLED) return null
        if (input.couplingBeamLength <= 0 || input.couplingBeamHeight <= 0) return null

        val Lb = input.couplingBeamClearSpan.let { if (it > 0) it else input.couplingBeamLength }
        val hb = input.couplingBeamHeight
        val bw = input.wallThickness
        val fcu = input.fcu
        val fy = input.fy

        // Coupling beam shear estimate
        val Vb = if (input.shearForce > 0 && input.couplingBeamClearSpan > 0) {
            input.shearForce * 0.3
        } else if (input.bendingMoment > 0) {
            input.bendingMoment * 1000.0 / (input.wallLength / 2.0) * 0.15 / 1000.0
        } else {
            input.shearForce * 0.3
        }

        // Check if diagonal reinforcement needed
        val d = hb - 50.0
        val vuRatio = if (bw > 0 && d > 0) {
            Vb * 1000.0 / (sqrt(fcu) * bw * d)
        } else 0.0

        val needsDiagonal = vuRatio > 0.15 || input.wallType == WallType.COUPLED

        if (!needsDiagonal) {
            return CouplingBeamResult(0, 0, 10, 150, true, 0.5)
        }

        // Diagonal reinforcement design (SBC with φ = 0.9)
        val theta = atan(hb / Lb)
        val sinT = sin(theta)
        val fsDesign = fy / GAMMA_S
        val AsdPerLeg = if (sinT > 0.1) {
            Vb * 1000.0 / (2.0 * PHI_FLEXURE * fsDesign * sinT)
        } else 0.0

        var diagDia = 20.0
        var diagCount = 4
        for (dia in BAR_DIAMETERS) {
            val barArea = PI * dia * dia / 4
            val count = ceil(AsdPerLeg / barArea).toInt().coerceAtLeast(2)
            if (count <= 8 && barArea >= AsdPerLeg / 4.0) {
                diagDia = dia; diagCount = count * 2; break
            }
        }

        // Transverse reinforcement around diagonal bars
        val transDia = max(8.0, diagDia / 3.0).coerceAtLeast(10.0)
        val transSpacing = minOf(hb / 4.0, 6 * diagDia, 100.0).toInt().coerceAtLeast(50)

        // Utilization check
        val providedAs = diagCount / 2 * PI * diagDia * diagDia / 4
        val capacityV = 2.0 * PHI_FLEXURE * providedAs * fsDesign * sinT / 1000.0
        val utilRatio = if (capacityV > 0) Vb / capacityV else 2.0

        return CouplingBeamResult(
            diagonalBars = diagCount, diagonalBarDiameter = diagDia.toInt(),
            transverseBarsDiameter = transDia.toInt(), transverseBarsSpacing = transSpacing,
            isSafe = utilRatio <= 1.0, utilizationRatio = utilRatio
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // 6. SLENDERNESS CHECK — H/t ratio
    // ══════════════════════════════════════════════════════════════════

    /**
     * SBC 304: walls with H/t > 25 require second-order analysis.
     */
    override fun checkSlenderness(input: ShearWallInput): Pair<Boolean, Double> {
        val Hw = input.wallHeight * input.numberOfStories
        val t = input.wallThickness
        val ratio = Hw / t
        return Pair(ratio <= 25.0, ratio)
    }

    // ══════════════════════════════════════════════════════════════════
    // 7. NEUTRAL AXIS DEPTH (iterative K-method)
    // ══════════════════════════════════════════════════════════════════

    private fun calculateNeutralAxisDepth(
        Mu: Double, Pu: Double, Lw: Double, bw: Double,
        fcu: Double, fy: Double
    ): Double {
        val d = 0.8 * Lw
        val MuNmm = Mu * 1e6
        val PuN = Pu * 1000.0
        val fcDesign = 0.67 * fcu / GAMMA_C  // SBC: fcu directly
        val fsDesign = fy / GAMMA_S
        val beta1 = calculateBeta1(fcu)

        var c = 50.0
        var cNext = c
        for (i in 1..60) {
            val a = beta1 * c
            val leverArm = d - a / 2.0
            if (leverArm <= 0) break
            val AsEst = max(0.0, (MuNmm - PuN * (d - Lw / 2.0)) / (fsDesign * leverArm))
            val newA = if (bw > 0 && fcDesign > 0) {
                (AsEst * fsDesign + PuN) / (fcDesign * bw)
            } else a
            // A9-FIX: compare against the PREVIOUS iterate (old check always
            // broke after the first pass).
            cNext = newA / beta1
            if (abs(cNext - c) < 0.1) break
            c = cNext
        }
        return cNext.coerceIn(0.0, 0.5 * d)
    }

    // ══════════════════════════════════════════════════════════════════
    // 8. β1 FACTOR (fcu directly per SBC)
    // ══════════════════════════════════════════════════════════════════

    /**
     * SBC 304: β1 = 0.85 for fcu ≤ 28 MPa, reduce by 0.05 per 7 MPa above.
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
     * Minimum ρv = 0.0025. Max spacing: min(L/3, 3t, 450mm).
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

        val leverArm = d - a / 2.0
        val AsRequired = if (leverArm > 0) {
            max(0.0, (MuNmm - PuN * (d - Lw / 2.0)) / (fsDesign * leverArm))
        } else 0.0

        val minDistributedAs = RHO_V_MIN * bw * 1000.0
        val totalAsNeeded = max(AsRequired, minDistributedAs * Lw / 1000.0)
        val maxSpacing = minOf(Lw / 3.0, 3 * bw, 450.0)
        val netLength = Lw - 2 * cover

        var selDia = 16.0
        var selCount = 4
        var selSpacing = 200.0

        for (dia in BAR_DIAMETERS) {
            val barArea = PI * dia * dia / 4
            val count = ceil(totalAsNeeded / barArea).toInt().coerceAtLeast(4)
            val spacing = if (count > 1) netLength / (count - 1) else Lw
            if (spacing >= 100.0 && spacing <= maxSpacing) {
                selDia = dia; selCount = count; selSpacing = spacing; break
            }
        }

        val provArea = selCount * PI * selDia * selDia / 4

        return RebarResult(
            bars = selCount, diameter = selDia.toInt(),
            spacing = selSpacing.toInt(),
            providedArea = provArea, requiredArea = AsRequired,
            ratio = if (AsRequired > 0) provArea / AsRequired else 1.0
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // 10. HORIZONTAL REINFORCEMENT DESIGN
    // ══════════════════════════════════════════════════════════════════

    /**
     * Design horizontal reinforcement (shear reinforcement).
     * Minimum ρh = 0.0025. Max spacing: min(L/5, 3t, 450mm).
     * SBC applies φ = 0.75 on shear capacity.
     */
    private fun designHorizontalReinforcement(
        input: ShearWallInput, Vc: Double, d: Double
    ): RebarResult {
        val Lw = input.wallLength
        val bw = input.wallThickness
        val fyv = input.fyv
        val fyvDesign = fyv / GAMMA_S
        val Vu = input.shearForce

        // Vs required against factored Vc
        val VcFactored = PHI_SHEAR * Vc
        val VsRequired = max(0.0, Vu - VcFactored)

        val reqAsvPerS = if (VsRequired > 0 && d > 0) {
            VsRequired * 1000.0 / (PHI_SHEAR * fyvDesign * d)
        } else 0.0

        val minAsvPerS = RHO_H_MIN * bw
        val designAsvPerS = max(reqAsvPerS, minAsvPerS)
        val reqAreaPerMeter = designAsvPerS * 1000.0

        val maxSpacing = minOf(Lw / 5.0, 3 * bw, 450.0)

        var selDia = 10.0
        var selSpacing = 200.0
        var selCount = 5

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
            bars = selCount, diameter = selDia.toInt(),
            spacing = selSpacing.toInt(),
            providedArea = provAreaPerMeter, requiredArea = reqAreaPerMeter,
            ratio = if (reqAreaPerMeter > 0) provAreaPerMeter / reqAreaPerMeter else 1.0
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // 11. MAXIMUM SHEAR CAPACITY CHECK
    // ══════════════════════════════════════════════════════════════════

    /**
     * SBC 304: Vn_max = 0.5 × √fcu × b × d (nominal, fcu directly)
     */
    private fun checkMaxShearCapacity(input: ShearWallInput): Boolean {
        val Lw = input.wallLength
        val bw = input.wallThickness
        val d = 0.8 * Lw
        val VcMaxNominal = 0.5 * sqrt(input.fcu) * bw * d / 1000.0  // kN nominal
        val VcMax = PHI_SHEAR * VcMaxNominal  // kN factored
        return input.shearForce <= VcMax
    }

    // ══════════════════════════════════════════════════════════════════
    // 12. DEVELOPMENT LENGTH — SBC 304 §5-2
    // ══════════════════════════════════════════════════════════════════

    /**
     * SBC 304: Ld = (fy / (4 × √fcu)) × db (fcu directly, not f'c)
     * Minimum Ld = 20 × db
     */
    private fun calculateDevelopmentLength(barDia: Double, fcu: Double, fy: Double, isTension: Boolean): Double {
        val sqrtFcu = sqrt(fcu).coerceAtLeast(1.0)
        val LdBasic = fy / (4.0 * sqrtFcu) * barDia
        val LdMin = 20.0 * barDia
        return if (isTension) max(LdBasic, LdMin)
        else max(LdBasic * 0.75, LdMin * 0.75)
    }

    // ══════════════════════════════════════════════════════════════════
    // 13. MINIMUM WALL THICKNESS — SBC 304 §6-7
    // ══════════════════════════════════════════════════════════════════

    private fun checkMinThickness(input: ShearWallInput): Pair<Boolean, Double> {
        val minT = when (input.wallType) {
            WallType.ORDINARY -> 150.0
            WallType.SPECIAL -> 200.0
            WallType.COUPLED -> 200.0
        }
        return Pair(input.wallThickness >= minT, minT)
    }

    // ══════════════════════════════════════════════════════════════════
    // 14. OVERSTRENGTH CHECK — SBC 304 capacity design
    // ══════════════════════════════════════════════════════════════════

    /**
     * SBC 304 capacity design: Vn ≥ 1.2 × Mn / Lw
     * Ensures shear failure doesn't precede flexural failure.
     */
    private fun checkOverstrength(input: ShearWallInput, Mn: Double, Vn: Double): Boolean {
        val Lw = input.wallLength
        val Voverstrength = 1.2 * Mn * 1000.0 / Lw / 1000.0
        return Vn >= Voverstrength
    }
}