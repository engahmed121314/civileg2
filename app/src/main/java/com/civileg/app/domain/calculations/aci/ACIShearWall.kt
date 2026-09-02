package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.*
import com.civileg.app.domain.calculations.base.ShearWallDesign
import kotlin.math.*

/**
 * ACI 318-19 Shear Wall Design Implementation
 *
 * References:
 *  - ACI 318-19 Ch.11: Walls (general)
 *  - ACI 318-19 §18.10: Special structural walls
 *  - ACI 318-19 §18.10.4: Shear strength
 *  - ACI 318-19 §18.10.6: Boundary elements
 *  - ACI 318-19 §18.10.7: Coupling beams
 *  - ACI 318-19 §22.5: Shear strength provisions
 *  - ACI 318-19 §25.4: Development length
 *
 * Strength reduction factors:
 *  φ_flexure = 0.9 (tension-controlled)
 *  φ_compression = 0.65 (compression-controlled)
 *  φ_shear = 0.75
 *
 * Key design rules:
 *  - Special walls: ρv ≥ 0.0025, ρh ≥ 0.0025
 *  - Shear: Vn = Acv × (αc × √fc' + ρt × fy) for special walls
 *  - Boundary elements: when c ≥ hw/6 or c ≥ 400mm (compression-controlled)
 *  - Coupling beam: diagonal reinforcement when Vu × ln / (2 × Mn) > 0.5
 *  - fcu (cube) converted to f'c (cylinder) via f'c = 0.8 × fcu
 */
class ACIShearWall : ShearWallDesign {

    companion object {
        // Strength reduction factors — ACI 318-19 Table 21.2.1
        private const val PHI_FLEXURE = 0.9
        private const val PHI_COMPRESSION = 0.65
        private const val PHI_SHEAR = 0.75
        private const val PHI_AXIAL = 0.65

        // Stress block
        private const val BETA_1_BASE = 0.85
        private const val EPSILON_CU = 0.003
        private const val EPSILON_T_LIMIT = 0.005  // tension-controlled limit
        private const val ES = 200000.0  // MPa

        // Reinforcement limits — ACI 318
        private const val RHO_V_MIN = 0.0025
        private const val RHO_H_MIN = 0.0025
        private const val RHO_V_MAX = 0.06

        // Available bars
        private val BAR_DIAMETERS = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0, 28.0, 32.0)
        private val SMALL_BAR_DIAMETERS = listOf(8.0, 10.0, 12.0, 14.0, 16.0)

        // Cube to cylinder conversion
        private const val CUBE_TO_CYLINDER = 0.8
    }

    // ══════════════════════════════════════════════════════════════════
    // 1. MAIN ENTRY POINT
    // ══════════════════════════════════════════════════════════════════

    override fun designWall(input: ShearWallInput): ShearWallResult {
        com.civileg.app.domain.calculations.InputGuard.positive(
            "fcu" to input.fcu, "fy" to input.fy,
            "wallLength" to input.wallLength,
            "wallThickness" to input.wallThickness,
            "wallHeight" to input.wallHeight
        )
        com.civileg.app.domain.calculations.InputGuard.atLeastOne("numberOfStories", input.numberOfStories)
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val safetyChecks = mutableListOf<ShearWallSafetyCheck>()

        codeNotes.add("ACI 318-19: Shear Wall Design")
        codeNotes.add("Wall Type: ${input.wallType.displayName}")
        codeNotes.add("φ_flex = $PHI_FLEXURE, φ_comp = $PHI_COMPRESSION, φ_shear = $PHI_SHEAR")

        // Convert fcu (cube) to f'c (cylinder)
        val fc = input.fcu * CUBE_TO_CYLINDER
        val fy = input.fy
        val fyv = input.fyv

        // Effective section
        val Lw = input.wallLength
        val bw = input.wallThickness
        val Hw = input.wallHeight * input.numberOfStories
        val d = 0.8 * Lw
        val Ag = Lw * bw
        val cover = input.clearCover

        // Flange
        val flangeW = if (input.flangeWidth > 0) {
            min(input.flangeWidth, min(6.0 * bw, Lw / 2.0))
        } else 0.0
        if (flangeW > 0) {
            codeNotes.add(String.format("Flange width = %.0f mm", flangeW))
        }

        // Input checks
        if (Lw < 5 * bw) {
            warnings.add("ACI 18.10.1: Wall length < 5× thickness — design as column")
        }
        if (bw < 150.0 && input.wallType == WallType.SPECIAL) {
            warnings.add("ACI 18.10.1.1: Special wall min thickness = 150mm (seismic)")
        }

        codeNotes.add(String.format("f'c = %.1f MPa (from fcu = %.1f MPa)", fc, input.fcu))
        codeNotes.add(String.format("Lw = %.0f mm, bw = %.0f mm, Hw = %.0f mm (%d stories)",
            Lw, bw, Hw, input.numberOfStories))

        // ── 1. Flexural design ──────────────────────────────────────
        val (Mn, Pn) = calculateFlexuralStrength(input)
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

        // ── 2. Compression zone depth ──────────────────────────────
        val beta1 = calculateBeta1(fc)
        val c = calculateNeutralAxisDepth(input)
        val a = beta1 * c
        codeNotes.add(String.format("Neutral axis c = %.1f mm, a = β1×c = %.1f mm", c, a))

        // ── 3. Vertical reinforcement ───────────────────────────────
        val vertRebar = designVerticalReinforcement(input, a, d, c)
        codeNotes.add(String.format("Vertical: %dΦ%d @ %d mm (As = %.0f, req = %.0f mm²)",
            vertRebar.bars, vertRebar.diameter, vertRebar.spacing,
            vertRebar.providedArea, vertRebar.requiredArea))

        // ── 4. Shear design ─────────────────────────────────────────
        val (Vc, Vs) = calculateShearStrength(input)
        val phiVn = PHI_SHEAR * (Vc + Vs)
        val shearOk = input.shearForce <= phiVn
        val shearUtilRatio = if (phiVn > 0) input.shearForce / phiVn else 2.0
        safetyChecks.add(ShearWallSafetyCheck(
            "Shear (Vu/φVn)", input.shearForce, phiVn, "kN", shearOk
        ))
        codeNotes.add(String.format("φVc = %.1f kN, φVs = %.1f kN, φVn = %.1f kN",
            PHI_SHEAR * Vc, PHI_SHEAR * Vs, phiVn))

        // Horizontal reinforcement
        val horzRebar = designHorizontalReinforcement(input, Vc, d)
        codeNotes.add(String.format("Horizontal: %dΦ%d @ %d mm (As = %.0f, req = %.0f mm²)",
            horzRebar.bars, horzRebar.diameter, horzRebar.spacing,
            horzRebar.providedArea, horzRebar.requiredArea))

        // Max shear check: ACI 18.10.4.1 — Vn ≤ 0.66 × √fc' × Acv
        val VnMax = 0.66 * sqrt(fc) * bw * d / 1000.0
        val maxShearOk = input.shearForce <= PHI_SHEAR * VnMax
        safetyChecks.add(ShearWallSafetyCheck(
            "Max Shear (Vu/φVmax)", input.shearForce, PHI_SHEAR * VnMax, "kN", maxShearOk
        ))

        // ── Capacity design — flexural overstrength (A9-FIX) ───────
        // ACI 318-19 §18.10.2.4 capacity-design principle: the wall shear
        // strength must exceed the moment overstrength demand so a ductile
        // flexure mechanism forms first (SPECIAL/COUPLED walls).
        val overstrengthOk = if (input.wallType != WallType.ORDINARY) {
            val overstrengthDemand = 1.2 * Mn * 1000.0 / Lw / 1000.0  // kN
            val ok = checkOverstrength(input, Mn, Vc + Vs)
            safetyChecks.add(ShearWallSafetyCheck(
                "Overstrength Vn>=1.2Mn/Lw", Vc + Vs, overstrengthDemand, "kN", ok
            ))
            ok
        } else true

        // ── 5. Boundary elements ────────────────────────────────────
        val (beType, beRebar) = designBoundaryElements(input)
        if (beType != BoundaryElementType.NONE) {
            codeNotes.add(String.format("Boundary element: %s (ACI 18.10.6)", beType.displayName))
            beRebar?.let {
                codeNotes.add(String.format("  BE: %dΦ%d @ %d mm", it.bars, it.diameter, it.spacing))
            }
        }

        // ── 6. Coupling beam ────────────────────────────────────────
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

        // ── 7. Slenderness ──────────────────────────────────────────
        val (slenderOk, slendernessRatio) = checkSlenderness(input)
        safetyChecks.add(ShearWallSafetyCheck(
            "Slenderness (H/t)", slendernessRatio, 25.0, "ratio", slenderOk
        ))

        // ── 8. Quantities ───────────────────────────────────────────
        val concreteVol = Lw * bw * input.wallHeight / 1e9
        val vertBars = (Lw / vertRebar.spacing) + 1
        val vertWeight = vertBars * input.wallHeight / 1000.0 * PI * (vertRebar.diameter / 2.0).pow(2) / 1e6 * 7850.0
        val horzBars = (input.wallHeight / horzRebar.spacing) + 1
        val horzWeight = horzBars * Lw / 1000.0 * PI * (horzRebar.diameter / 2.0).pow(2) / 1e6 * 7850.0

        // ── 9. Overall ──────────────────────────────────────────────
        val overallSafe = flexOk && shearOk && slenderOk && maxShearOk && overstrengthOk &&
            (couplingResult?.isSafe ?: true)
        val maxUtil = maxOf(flexUtilRatio, shearUtilRatio,
            couplingResult?.utilizationRatio ?: 0.0)

        return ShearWallResult(
            isSafe = overallSafe,
            utilizationRatio = maxUtil,
            flexuralOk = flexOk,
            momentCapacity = phiMn,
            axialCapacity = phiPn,
            compressionDepth = a,
            verticalReinforcement = vertRebar,
            boundaryElementType = beType,
            boundaryElementReinforcement = beRebar,
            shearOk = shearOk,
            shearCapacity = phiVn,
            concreteShearCapacity = PHI_SHEAR * Vc,
            steelShearCapacity = PHI_SHEAR * Vs,
            horizontalReinforcement = horzRebar,
            slendernessOk = slenderOk,
            slendernessRatio = slendernessRatio,
            couplingBeamResult = couplingResult,
            concreteVolumePerStory = concreteVol,
            steelWeightPerStory = vertWeight + horzWeight,
            warnings = warnings,
            codeNotes = codeNotes,
            safetyChecks = safetyChecks
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. FLEXURAL STRENGTH — ACI 318 §22.2 / §18.10
    // ══════════════════════════════════════════════════════════════════

    /**
     * Calculate nominal moment (Mn) and axial (Pn) capacities.
     *
     * ACI 318-19 §18.10.2: Walls designed as columns under combined
     * axial load and bending.
     *
     * Uses iterative solution for neutral axis with:
     *  - Whitney stress block: a = β1 × c
     *  - φ = 0.9 for tension-controlled (εt ≥ 0.005)
     *  - φ = 0.65 for compression-controlled (εt ≤ 0.002)
     *  - Linear transition between
     *
     * @return Pair<Mn_kN_m, Pn_kN>
     */
    override fun calculateFlexuralStrength(input: ShearWallInput): Pair<Double, Double> {
        val fc = input.fcu * CUBE_TO_CYLINDER
        val fy = input.fy
        val Lw = input.wallLength
        val bw = input.wallThickness
        val Mu = input.bendingMoment
        val Pu = input.axialLoad
        val d = 0.8 * Lw
        val MuNmm = Mu * 1e6
        val PuN = Pu * 1000.0
        val beta1 = calculateBeta1(fc)

        // ── Iterative solution for compression block depth a ────────
        var a = 50.0
        for (i in 1..60) {
            val leverArm = d - a / 2.0
            if (leverArm <= 0) break

            // As from moment equilibrium: φMn = As × fy × (d - a/2) + Pu × (d - Lw/2)
            val AsTension = max(0.0, (MuNmm / PHI_FLEXURE - PuN * (d - Lw / 2.0)) / (fy * leverArm))

            // Update a: C = T + Pu → 0.85 × fc × bw × a = As × fy + Pu
            a = if (bw > 0 && fc > 0) {
                (AsTension * fy + PuN) / (0.85 * fc * bw)
            } else a

            // Tension-controlled limit: c/d ≤ 0.375 (εt ≥ 0.005)
            val cNew = a / beta1.coerceAtLeast(0.01)
            a = (cNew.coerceAtMost(0.375 * d)) * beta1
        }

        // ── Final As calculation ───────────────────────────────────
        val leverArm = d - a / 2.0
        val AsReq = if (leverArm > 0) {
            max(0.0, (MuNmm / PHI_FLEXURE - PuN * (d - Lw / 2.0)) / (fy * leverArm))
        } else 0.0

        // Distributed minimum reinforcement
        val distAsPerMeter = RHO_V_MIN * bw * 1000.0

        // Select tension bars
        var tenDia = 16.0
        var tenCount = 4
        var tenSpacing = 200.0
        val maxSp = minOf(Lw / 3.0, 3 * bw, 450.0)
        for (dia in BAR_DIAMETERS) {
            val area = PI * dia * dia / 4
            val count = ceil(AsReq / area).toInt().coerceAtLeast(4)
            val spacing = if (count > 1) (Lw - 2 * input.clearCover) / (count - 1) else Lw
            if (spacing >= 100.0 && spacing <= maxSp) {
                tenDia = dia; tenCount = count; tenSpacing = spacing; break
            }
        }
        val AsProv = tenCount * PI * tenDia * tenDia / 4

        // Compression bars
        val compDia = tenDia
        val compCount = max(2, tenCount / 3)
        val AsCompProv = compCount * PI * compDia * compDia / 4

        // ── Nominal moment capacity Mn ─────────────────────────────
        val Mn = (AsProv * fy * leverArm + PuN * (d - Lw / 2.0)) / 1e6

        // ── Nominal axial capacity Pn ──────────────────────────────
        // ACI 22.4.2: Pn = 0.85 × f'c × (Ag - Ast) + fy × Ast
        val totalAs = AsProv + AsCompProv + distAsPerMeter * Lw / 1000.0
        val AstCapped = totalAs.coerceAtMost(0.06 * Lw * bw)
        val Pn = 0.85 * (0.85 * fc * (Lw * bw - AstCapped) + fy * AstCapped) / 1000.0

        return Pair(Mn, Pn)
    }

    // ══════════════════════════════════════════════════════════════════
    // 3. SHEAR STRENGTH — ACI 318 §18.10.4 / §22.5
    // ══════════════════════════════════════════════════════════════════

    /**
     * Calculate concrete (Vc) and steel (Vs) shear capacities.
     *
     * ACI 18.10.4.1: For special structural walls:
     *   Vn = Acv × (αc × λ × √f'c + ρt × fy)
     *   where Acv = bw × d, αc = 0.17 for special walls
     *
     * For ordinary walls (ACI 11.5.4):
     *   Vc = 0.17 × λ × √f'c × bw × d  (simplified)
     *
     * Axial compression increases Vc.
     *
     * @return Pair<Vc_kN, Vs_kN>
     */
    override fun calculateShearStrength(input: ShearWallInput): Pair<Double, Double> {
        val fc = input.fcu * CUBE_TO_CYLINDER
        val fyv = input.fyv
        val Lw = input.wallLength
        val bw = input.wallThickness
        val d = 0.8 * Lw
        val Nu = input.axialLoad
        val Vu = input.shearForce
        val NuN = Nu * 1000.0
        val Acv = bw * d
        val lambda = 1.0  // normal weight concrete

        // ── Concrete shear capacity ────────────────────────────────
        // ACI 18.10.4.1 for special walls:
        // Vc = αc × λ × √f'c × Acv × (1 + Nu / (4 × Acv))
        val alphaC = when (input.wallType) {
            WallType.SPECIAL -> 0.17
            WallType.COUPLED -> 0.17
            WallType.ORDINARY -> 0.17  // ACI 11.5.4
        }

        val axialFactor = if (NuN > 0) 1.0 + NuN / (4.0 * Acv) else 1.0
        val Vc = alphaC * lambda * sqrt(fc) * Acv * axialFactor / 1000.0  // kN

        // ── Maximum Vc ─────────────────────────────────────────────
        // ACI 18.10.4.1: Vn_max = 0.66 × √f'c × Acv
        val VcMax = 0.66 * sqrt(fc) * Acv / 1000.0

        // ── Steel shear needed ─────────────────────────────────────
        val VnRequired = Vu / PHI_SHEAR
        val VsRequired = max(0.0, VnRequired - Vc)

        // Minimum shear reinforcement: ρt ≥ 0.0025
        val minRhoT = RHO_H_MIN
        val minAsvPerS = minRhoT * bw
        val designAsvPerS = max(VsRequired * 1000.0 / (fyv * d).coerceAtLeast(1.0), minAsvPerS)

        // ── Select horizontal bars ─────────────────────────────────
        val maxSpacing = minOf(Lw / 5.0, 3 * bw, 450.0)
        var selDia = 10.0
        var selSpacing = 200.0

        for (dia in SMALL_BAR_DIAMETERS) {
            val asBar = PI * dia * dia / 4
            val spacing = (asBar / designAsvPerS).coerceIn(75.0, maxSpacing)
            if (spacing >= 75.0 && spacing <= maxSpacing) {
                selDia = dia; selSpacing = spacing; break
            }
        }
        selSpacing = selSpacing.coerceAtMost(maxSpacing)

        // ── Provided Vs ────────────────────────────────────────────
        val asProvided = PI * selDia * selDia / 4 / selSpacing
        val Vs = asProvided * fyv * d / 1000.0

        return Pair(Vc, Vs)
    }

    // ══════════════════════════════════════════════════════════════════
    // 4. BOUNDARY ELEMENTS — ACI 18.10.6
    // ══════════════════════════════════════════════════════════════════

    /**
     * Check and design boundary elements per ACI 18.10.6.
     *
     * ACI 18.10.6.2: Boundary elements required if:
     *  - c ≥ lw / (600 × δu / hw) for special walls (displacement-based)
     *  - Simplified (ACI 18.10.6.3): c ≥ lw / 6 for special walls
     *    when δu/hw ≥ 0.005
     *
     * Alternative: if extreme fiber compression strain > 0.003
     *
     * Confinement (ACI 18.10.6.4):
     *  - ρs = 0.12 × f'c / fy × (Ag/Ach - 1)
     *  - Tie spacing: min(hc/4, 6db, 100mm) for special
     */
    override fun designBoundaryElements(input: ShearWallInput): Pair<BoundaryElementType, RebarResult?> {
        val fc = input.fcu * CUBE_TO_CYLINDER
        val fy = input.fy
        val Lw = input.wallLength
        val bw = input.wallThickness
        val d = 0.8 * Lw
        val cover = input.clearCover

        // Neutral axis depth
        val c = calculateNeutralAxisDepth(input)
        val cOverLw = c / Lw

        // ── Determine if BE needed ──────────────────────────────────
        val beType = when (input.wallType) {
            WallType.SPECIAL -> {
                // ACI 18.10.6.3: c ≥ Lw/6 or Pu > 0.35 × f'c × Ag
                val puRatio = input.axialLoad * 1000.0 / (0.35 * fc * Lw * bw).coerceAtLeast(1.0)
                when {
                    cOverLw > 0.167 || puRatio > 1.0 -> BoundaryElementType.SPECIAL
                    cOverLw > 0.10 -> BoundaryElementType.STANDARD
                    else -> BoundaryElementType.NONE
                }
            }
            WallType.COUPLED -> {
                when {
                    cOverLw > 0.167 -> BoundaryElementType.SPECIAL
                    cOverLw > 0.10 -> BoundaryElementType.STANDARD
                    else -> BoundaryElementType.NONE
                }
            }
            WallType.ORDINARY -> {
                when {
                    cOverLw > 0.25 -> BoundaryElementType.SPECIAL
                    cOverLw > 0.15 -> BoundaryElementType.STANDARD
                    else -> BoundaryElementType.NONE
                }
            }
        }

        if (beType == BoundaryElementType.NONE) {
            return Pair(BoundaryElementType.NONE, null)
        }

        // ── BE length ───────────────────────────────────────────────
        val beLength = if (input.endZoneLength > 0) {
            input.endZoneLength
        } else {
            max(c, Lw / 6.0).coerceAtMost(Lw / 3.0)
        }.coerceAtMost(Lw / 3.0).coerceAtLeast(2 * cover + 100.0)

        // ── Longitudinal reinforcement ──────────────────────────────
        val beArea = beLength * bw
        val minLongitAs = 0.01 * beArea
        val PuBe = input.axialLoad * 1000.0 * (beLength / Lw)
        var reqAsBe = if (fc > 0) {
            max(minLongitAs, (PuBe - 0.85 * fc * beArea) / (fy - 0.85 * fc).coerceAtLeast(1.0))
        } else minLongitAs
        reqAsBe = max(0.0, reqAsBe)

        var beBarDia = 16.0
        var beBarCount = 4
        var beBarSpacing = 100.0
        for (dia in BAR_DIAMETERS) {
            val area = PI * dia * dia / 4
            val count = ceil(reqAsBe / area).toInt().coerceAtLeast(4)
            val nLayers = ceil(count / 2.0).toInt()
            val spacing = if (nLayers > 1) (beLength - 2 * 25.0) / (nLayers - 1) else beLength
            if (spacing >= 75.0 && spacing <= 200.0 && count <= 12) {
                beBarDia = dia; beBarCount = count; beBarSpacing = spacing; break
            }
        }
        val provAsBe = beBarCount * PI * beBarDia * beBarDia / 4

        // ── Confinement ties — ACI 18.10.6.4 ───────────────────────
        // ρs = 0.12 × f'c / fy × (Ag/Ach - 1)
        val tieCover = 25.0
        val coreL = beLength - 2 * tieCover
        val coreT = bw - 2 * tieCover
        val Ach = coreL * coreT
        val reqVolRatio = if (Ach > 0) 0.12 * fc / fy * (beArea / Ach - 1.0) else 0.0

        val tieDia = max(10.0, beBarDia / 4.0).coerceAtLeast(10.0)
        val tieArea = PI * tieDia * tieDia / 4
        val maxTieSp = when (beType) {
            BoundaryElementType.SPECIAL -> minOf(beLength / 4.0, 6 * beBarDia, 100.0)
            BoundaryElementType.STANDARD -> minOf(beLength / 3.0, 8 * beBarDia, 150.0)
            BoundaryElementType.NONE -> 200.0
        }
        val tieSpacing = if (reqVolRatio > 0 && coreL > 0) {
            (tieArea * (coreL - 2 * tieDia) / (reqVolRatio * Ach))
                .coerceIn(50.0, maxTieSp)
        } else maxTieSp

        return Pair(beType, RebarResult(
            bars = beBarCount,
            diameter = beBarDia.toInt(),
            spacing = tieSpacing.toInt(),
            providedArea = provAsBe,
            requiredArea = reqAsBe,
            ratio = if (reqAsBe > 0) provAsBe / reqAsBe else 1.0
        ))
    }

    // ══════════════════════════════════════════════════════════════════
    // 5. COUPLING BEAM — ACI 18.10.7
    // ══════════════════════════════════════════════════════════════════

    /**
     * Design coupling beam per ACI 18.10.7.
     *
     * ACI 18.10.7.4: Diagonal reinforcement when:
     *   Vb × ln / (2 × Mn) > 0.5
     *   OR Vb > 0.33 × √f'c × bw × d
     *
     * Diagonal bars: Asd = Vb / (2 × fy × sin θ)
     * θ = arctan(hb / ln)
     */
    override fun designCouplingBeam(input: ShearWallInput): CouplingBeamResult? {
        if (input.wallType != WallType.COUPLED) return null
        if (input.couplingBeamLength <= 0 || input.couplingBeamHeight <= 0) return null

        val fc = input.fcu * CUBE_TO_CYLINDER
        val fy = input.fy
        val Lb = input.couplingBeamClearSpan.let { if (it > 0) it else input.couplingBeamLength }
        val hb = input.couplingBeamHeight
        val bw = input.wallThickness

        // Estimate coupling beam shear
        val Vb = input.shearForce * 0.3
        val Mb = input.bendingMoment * 0.15

        // ACI 18.10.7.4: check if diagonal needed
        val ratio = if (Mb > 0) Vb * Lb / (2.0 * Mb * 1e6) else 0.0
        val vuCheck = 0.33 * sqrt(fc) * bw * (hb - 50.0) / 1000.0
        val needsDiag = ratio > 0.5 || Vb > vuCheck

        if (!needsDiag) {
            return CouplingBeamResult(
                diagonalBars = 0, diagonalBarDiameter = 0,
                transverseBarsDiameter = 10, transverseBarsSpacing = 150,
                isSafe = true, utilizationRatio = 0.5
            )
        }

        // ── Diagonal reinforcement ──────────────────────────────────
        val theta = atan(hb / Lb)
        val sinT = sin(theta)
        val AsdPerLeg = if (sinT > 0.1) Vb * 1000.0 / (2.0 * fy * sinT) else 0.0

        var diagDia = 20.0
        var diagCount = 4
        for (dia in BAR_DIAMETERS) {
            val area = PI * dia * dia / 4
            val count = ceil(AsdPerLeg / area).toInt().coerceAtLeast(2)
            if (count <= 8) { diagDia = dia; diagCount = count * 2; break }
        }

        // Transverse reinforcement
        val transDia = max(10.0, diagDia / 3.0).coerceAtLeast(10.0)
        val transSpacing = minOf(hb / 4.0, 6 * diagDia, 100.0).toInt().coerceAtLeast(50)

        // Utilization
        val provAs = diagCount / 2 * PI * diagDia * diagDia / 4
        val capV = if (sinT > 0.1) 2.0 * provAs * fy * sinT / 1000.0 else 0.0
        val utilRatio = if (capV > 0) Vb / capV else 2.0

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
    // 6. SLENDERNESS CHECK
    // ══════════════════════════════════════════════════════════════════

    override fun checkSlenderness(input: ShearWallInput): Pair<Boolean, Double> {
        val Hw = input.wallHeight * input.numberOfStories
        val t = input.wallThickness
        val ratio = Hw / t
        return Pair(ratio <= 25.0, ratio)
    }

    // ══════════════════════════════════════════════════════════════════
    // HELPER: β1 FACTOR
    // ══════════════════════════════════════════════════════════════════

    /**
     * ACI 318 Table 22.2.2.4.3: β1 for rectangular stress block.
     * β1 = 0.85 for f'c ≤ 28 MPa, reduce by 0.05 per 7 MPa above.
     * Minimum β1 = 0.65.
     */
    private fun calculateBeta1(fc: Double): Double {
        return if (fc <= 28.0) BETA_1_BASE
        else (BETA_1_BASE - 0.05 * ((fc - 28.0) / 7.0)).coerceAtLeast(0.65)
    }

    // ══════════════════════════════════════════════════════════════════
    // HELPER: NEUTRAL AXIS DEPTH
    // ══════════════════════════════════════════════════════════════════

    private fun calculateNeutralAxisDepth(input: ShearWallInput): Double {
        val fc = input.fcu * CUBE_TO_CYLINDER
        val fy = input.fy
        val Lw = input.wallLength
        val bw = input.wallThickness
        val d = 0.8 * Lw
        val MuNmm = input.bendingMoment * 1e6
        val PuN = input.axialLoad * 1000.0
        val beta1 = calculateBeta1(fc)

        var c = 50.0
        var cNext = c
        for (i in 1..60) {
            val a = beta1 * c
            val leverArm = d - a / 2.0
            if (leverArm <= 0) break
            val As = max(0.0, (MuNmm / PHI_FLEXURE - PuN * (d - Lw / 2.0)) / (fy * leverArm))
            val newA = if (bw > 0 && fc > 0) (As * fy + PuN) / (0.85 * fc * bw) else a
            // A9-FIX: compare against the PREVIOUS iterate (old check always
            // broke after the first pass).
            cNext = newA / beta1
            if (abs(cNext - c) < 0.1) break
            c = cNext
        }
        return cNext.coerceIn(0.0, 0.5 * d)
    }

    // ══════════════════════════════════════════════════════════════════
    // HELPER: VERTICAL REINFORCEMENT DESIGN
    // ══════════════════════════════════════════════════════════════════

    private fun designVerticalReinforcement(
        input: ShearWallInput, a: Double, d: Double, c: Double
    ): RebarResult {
        val Lw = input.wallLength
        val bw = input.wallThickness
        val fy = input.fy
        val cover = input.clearCover
        val MuNmm = input.bendingMoment * 1e6
        val PuN = input.axialLoad * 1000.0

        val leverArm = d - a / 2.0
        val AsReq = if (leverArm > 0) {
            max(0.0, (MuNmm / PHI_FLEXURE - PuN * (d - Lw / 2.0)) / (fy * leverArm))
        } else 0.0

        val minDistAs = RHO_V_MIN * bw * 1000.0
        val totalAsNeeded = max(AsReq, minDistAs * Lw / 1000.0)
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
            bars = selCount,
            diameter = selDia.toInt(),
            spacing = selSpacing.toInt(),
            providedArea = provArea,
            requiredArea = AsReq,
            ratio = if (AsReq > 0) provArea / AsReq else 1.0
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // HELPER: HORIZONTAL REINFORCEMENT DESIGN
    // ══════════════════════════════════════════════════════════════════

    private fun designHorizontalReinforcement(
        input: ShearWallInput, Vc: Double, d: Double
    ): RebarResult {
        val Lw = input.wallLength
        val bw = input.wallThickness
        val fyv = input.fyv
        val Vu = input.shearForce
        val VnRequired = Vu / PHI_SHEAR
        val VsRequired = max(0.0, VnRequired - Vc)

        val minAsvPerS = RHO_H_MIN * bw
        val designAsvPerS = max(
            if (d > 0 && fyv > 0) VsRequired * 1000.0 / (fyv * d) else 0.0,
            minAsvPerS
        )
        val reqAreaPerMeter = designAsvPerS * 1000.0

        val maxSpacing = minOf(Lw / 5.0, 3 * bw, 450.0)
        var selDia = 10.0
        var selSpacing = 200.0
        var selCount = 5

        for (dia in SMALL_BAR_DIAMETERS) {
            val barArea = PI * dia * dia / 4
            val spacing = (barArea / designAsvPerS).coerceIn(75.0, maxSpacing)
            if (spacing >= 75.0 && spacing <= maxSpacing) {
                selDia = dia; selSpacing = spacing
                selCount = (1000.0 / spacing).toInt().coerceAtLeast(2)
                break
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
    // CAPACITY DESIGN: OVERSTRENGTH (A9-FIX — was absent entirely in ACI)
    // ══════════════════════════════════════════════════════════════════

    /**
     * ACI 318-19 §18.10.2.4 capacity design (simplified 1.2 factor consistent
     * with the ECP/SBC siblings): wall shear strength must develop the flexural
     * overstrength before shear failure.
     */
    private fun checkOverstrength(input: ShearWallInput, Mn: Double, Vn: Double): Boolean {
        val Lw = input.wallLength
        val Voverstrength = 1.2 * Mn * 1000.0 / Lw / 1000.0  // kN
        return Vn >= Voverstrength
    }
}
