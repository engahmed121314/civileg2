package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.*
import com.civileg.app.domain.calculations.base.FlatSlabDesign
import kotlin.math.*

/**
 * SBC 304 Flat Slab Design Implementation
 *
 * References:
 *  - SBC 304 §6-5: Flat slabs (slab systems without beams)
 *  - SBC 304 §4-2: Flexural design (K-method with fcu directly)
 *  - SBC 304 §4-3: Shear and punching shear
 *  - SBC 304 §6-5-2: Minimum thickness
 *  - SBC 304 §3-2: Load factors (γc=1.5, γs=1.15)
 *
 * Key SBC 304 differences from ACI 318:
 *  - Uses fcu directly (cube strength), not f'c = 0.8×fcu
 *  - Material safety factors: γc = 1.5, γs = 1.15
 *  - Strength reduction: φ_flexure = 0.9, φ_shear = 0.75
 *  - Load factors: Wu = 1.4×DL + 1.6×LL
 *
 * DDM: Total static moment Mo = wu × l2 × ln² / 8
 * Distribution: column strip 60-75%, middle strip 25-40%
 * Punching shear: Vc = min of three expressions × φ_shear
 * Deflection: Branson effective moment of inertia
 */
class SBCFlatSlab : FlatSlabDesign {

    companion object {
        private const val GAMMA_C = 1.5      // SBC 304 §4
        private const val GAMMA_S = 1.15     // SBC 304 §4
        private const val PHI_FLEXURE = 0.9  // SBC 304 flexure reduction
        private const val PHI_SHEAR = 0.75   // SBC 304 shear reduction
        private const val EPSILON_CU = 0.003
        private const val ES = 200000.0      // MPa (steel modulus)
        private const val CONCRETE_UNIT_WEIGHT = 25.0  // kN/m³
        private const val STEEL_UNIT_WEIGHT = 7850.0  // kg/m³
    }

    // ══════════════════════════════════════════════════════════════
    // MAIN ENTRY POINT
    // ══════════════════════════════════════════════════════════════

    override fun design(input: FlatSlabInput): FlatSlabResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val safetyChecks = mutableListOf<SafetyCheckItem>()

        codeNotes.add("SBC 304 §6-5: Flat Slab Design")
        codeNotes.add("Method: ${input.designMethod.displayName}")
        codeNotes.add("γc = $GAMMA_C, γs = $GAMMA_S, φ_flex = $PHI_FLEXURE, φ_shear = $PHI_SHEAR")

        // ── 1. Material checks ─────────────────────────────────────
        if (input.fcu < 25) warnings.add("SBC 304: fcu ≥ 25 MPa recommended for flat slabs")
        if (input.fy < 240 || input.fy > 500) warnings.add("SBC 304: fy should be 240–500 MPa")
        if (input.lx <= input.columnWidth || input.ly <= input.columnDepth) {
            return FlatSlabResult(
                isSafe = false, utilizationRatio = 2.0,
                totalDeadLoad = 0.0, totalFactoredLoad = 0.0,
                panelMomentX = 0.0, panelMomentY = 0.0,
                columnStripMomentPos = 0.0, columnStripMomentNeg = 0.0,
                middleStripMomentPos = 0.0, middleStripMomentNeg = 0.0,
                columnStripWidthX = 0.0, columnStripWidthY = 0.0,
                columnStripTopRebar = RebarResult(0, 0, 0, 0.0, 0.0, 0.0),
                columnStripBotRebar = RebarResult(0, 0, 0, 0.0, 0.0, 0.0),
                middleStripTopRebar = RebarResult(0, 0, 0, 0.0, 0.0, 0.0),
                middleStripBotRebar = RebarResult(0, 0, 0, 0.0, 0.0, 0.0),
                punchingShearOk = false, punchingShearVu = 0.0,
                punchingShearVc = 0.0, punchingPerimeter = 0.0,
                punchingReinforcement = null,
                dropRequired = false, dropThickness = 0.0,
                deflectionOk = false, deflection = 0.0, allowableDeflection = 0.0,
                concreteVolumePerPanel = 0.0, steelWeightPerPanel = 0.0,
                warnings = listOf("Invalid spans — column wider than span"),
                codeNotes = emptyList(), safetyChecks = emptyList()
            )
        }

        // ── 2. Dead load (self-weight + finishes) ───────────────────
        val selfWeight = input.slabThickness / 1000.0 * CONCRETE_UNIT_WEIGHT  // kN/m²
        val totalDeadLoad = selfWeight + input.floorFinish
        codeNotes.add(String.format(
            "Self-weight = %.0f/1000 × %.0f = %.2f kN/m²",
            input.slabThickness, CONCRETE_UNIT_WEIGHT, selfWeight
        ))
        codeNotes.add(String.format(
            "Total DL = %.2f + %.2f = %.2f kN/m²",
            selfWeight, input.floorFinish, totalDeadLoad
        ))

        // ── 3. Factored load: Wu = 1.4×DL + 1.6×LL ─────────────────
        val wu = getFactoredLoad(totalDeadLoad, input.liveLoad)
        codeNotes.add(String.format(
            "Wu = 1.4×%.2f + 1.6×%.2f = %.2f kN/m²",
            totalDeadLoad, input.liveLoad, wu
        ))

        // ── 4. Clear spans ────────────────────────────────────────
        val lnX = input.lx - input.columnWidth   // mm
        val lnY = input.ly - input.columnDepth   // mm
        val lnXm = lnX / 1000.0                   // m
        val lnYm = lnY / 1000.0

        // ── 5. Static moments Mo ──────────────────────────────────
        val MoX = calculateStaticMoment(wu, lnXm, lnYm)
        val MoY = calculateStaticMoment(wu, lnYm, lnXm)
        codeNotes.add(String.format(
            "MoX = %.2f × %.2f × %.2f² / 8 = %.1f kN.m", wu, lnYm, lnXm, MoX
        ))
        codeNotes.add(String.format(
            "MoY = %.2f × %.2f × %.2f² / 8 = %.1f kN.m", wu, lnXm, lnYm, MoY
        ))

        // ── 6. Moment distribution coefficients ───────────────────
        val coeffs = getMomentCoefficients(input.panelType)
        codeNotes.add(String.format(
            "Coefficients (col strip): -M_ext=%.0f%%, +M=%.0f%%, -M_int=%.0f%%",
            coeffs.colNegExterior * 100, coeffs.colPositive * 100, coeffs.colNegInterior * 100
        ))

        // ── 7. Effective depths ────────────────────────────────────
        val dSlab = input.slabThickness - input.clearCover - 6.0
        val dNeg = if (input.dropThickness > 0) {
            input.slabThickness + input.dropThickness - input.clearCover - 6.0
        } else dSlab
        val dPos = dSlab

        // ── 8. Column strip / middle strip widths ─────────────────
        val colStripWidthX = getColumnStripWidth(lnY, input.columnDepth, input.panelType)
        val midStripWidthX = lnY - colStripWidthX
        val colStripWidthY = getColumnStripWidth(lnX, input.columnWidth, input.panelType)

        // ── 9. Design X-direction (primary span) ───────────────────
        val momentFactorsX = getMomentFactorsForPanel(input.panelType)
        val mNegExtX = MoX * momentFactorsX.negExterior
        val mPosX = MoX * momentFactorsX.positive
        val mNegIntX = MoX * momentFactorsX.negInterior

        // Column strip X moments
        val colNegExtX = mNegExtX * coeffs.colNegExterior
        val colPosX = mPosX * coeffs.colPositive
        val colNegIntX = mNegIntX * coeffs.colNegInterior

        // Middle strip X moments
        val midNegExtX = mNegExtX * coeffs.midNegExterior
        val midPosX = mPosX * coeffs.midPositive
        val midNegIntX = mNegIntX * coeffs.midNegInterior

        // Design reinforcement for column strip X
        val colTopRebarX = designRebarForResult(
            maxOf(colNegExtX, colNegIntX), input.fcu, input.fy,
            dNeg, colStripWidthX, input.clearCover, warnings, codeNotes, "Col-Top-X"
        )
        val colBotRebarX = designRebarForResult(
            colPosX, input.fcu, input.fy,
            dPos, colStripWidthX, input.clearCover, warnings, codeNotes, "Col-Bot-X"
        )

        // Design reinforcement for middle strip X
        val midTopRebarX = designRebarForResult(
            maxOf(midNegExtX, midNegIntX), input.fcu, input.fy,
            dSlab, midStripWidthX, input.clearCover, warnings, codeNotes, "Mid-Top-X"
        )
        val midBotRebarX = designRebarForResult(
            midPosX, input.fcu, input.fy,
            dSlab, midStripWidthX, input.clearCover, warnings, codeNotes, "Mid-Bot-X"
        )

        // ── 10. Punching shear (SBC 304 with φ = 0.75) ───────────
        val tributaryAreaX = (input.lx / 1000.0) / 2.0
        val tributaryAreaY = (input.ly / 1000.0) / 2.0
        val Vu = wu * tributaryAreaX * tributaryAreaY

        val punchingResult = checkPunchingShear(
            vu = Vu, fcu = input.fcu, fy = input.fy,
            slabThickness = input.slabThickness,
            dropThickness = input.dropThickness,
            columnWidth = input.columnWidth,
            columnDepth = input.columnDepth,
            cover = input.clearCover
        )

        safetyChecks.add(SafetyCheckItem(
            "Punching Shear", punchingResult.vu, punchingResult.vc,
            "kN", punchingResult.isSafe
        ))
        if (!punchingResult.isSafe) {
            warnings.add("Punching shear FAILED — increase slab thickness, add drop panel, or shear studs")
        }
        codeNotes.add(String.format(
            "Punching: Vu=%.1f kN, φVc=%.1f kN, bo=%.0f mm, U=%.0f%%",
            punchingResult.vu, punchingResult.vc, punchingResult.bo,
            punchingResult.utilizationRatio * 100
        ))

        // ── 11. Deflection check ────────────────────────────────────
        val serviceMoment = colPosX / 1.4
        val deflectionResult = checkDeflection(
            span = lnX, slabThickness = input.slabThickness,
            fcu = input.fcu, fy = input.fy,
            serviceMoment = serviceMoment,
            effectiveDepth = dSlab,
            providedAs = colBotRebarX.providedArea
        )

        safetyChecks.add(SafetyCheckItem(
            "Deflection", deflectionResult.longTerm, deflectionResult.allowable,
            "mm", deflectionResult.isSafe
        ))
        if (!deflectionResult.isSafe) warnings.add("Deflection exceeds SBC 304 allowable limit")

        // ── 12. Minimum thickness check ─────────────────────────────
        val minThk = getMinimumThickness(lnX, lnY, input.dropThickness > 0, input.fcu, input.fy)
        val minThkOk = input.slabThickness >= minThk
        safetyChecks.add(SafetyCheckItem(
            "Min. Thickness", input.slabThickness, minThk, "mm", minThkOk
        ))
        if (!minThkOk) warnings.add("Slab thickness below SBC 304 minimum")

        // ── 13. Drop panel check ────────────────────────────────────
        val dropRequired = !punchingResult.isSafe && !punchingResult.needsReinforcement
        val recommendedDropThickness = if (dropRequired) {
            max(input.slabThickness / 4.0, 50.0)
        } else input.dropThickness

        // ── 14. Quantities ─────────────────────────────────────────
        val panelArea = (input.lx / 1000.0) * (input.ly / 1000.0)
        val slabVol = panelArea * input.slabThickness / 1000.0
        val dropVol = if (input.dropThickness > 0 && input.dropSizeX > 0) {
            (input.dropSizeX / 1000.0) * (input.dropSizeY / 1000.0) * input.dropThickness / 1000.0
        } else 0.0
        val concreteVolume = slabVol + dropVol

        val totalAsX = colTopRebarX.providedArea + colBotRebarX.providedArea +
                       midTopRebarX.providedArea + midBotRebarX.providedArea
        val totalAsY = totalAsX * (lnYm / lnXm).coerceAtLeast(0.5)
        val totalAs = totalAsX + totalAsY
        val steelWeight = totalAs * (input.lx + input.ly) / 1e6 * STEEL_UNIT_WEIGHT

        // ── 15. Overall utilization ─────────────────────────────────
        val maxUtil = maxOf(
            punchingResult.utilizationRatio,
            deflectionResult.ratio,
            if (punchingResult.vc > 0) punchingResult.vu / punchingResult.vc else 0.0
        )
        val overallSafe = punchingResult.isSafe && deflectionResult.isSafe && minThkOk

        // Build punching reinforcement result
        val punchingReinforcement = if (punchingResult.needsReinforcement && punchingResult.studDia > 0) {
            val studArea = PI * punchingResult.studDia * punchingResult.studDia / 4
            val studsPerRow = max(4, (punchingResult.bo / punchingResult.studSpacing).toInt())
            val totalStuds = studsPerRow * punchingResult.studRows
            val totalAs = studArea * totalStuds
            RebarResult(
                bars = totalStuds, diameter = punchingResult.studDia.toInt(),
                spacing = punchingResult.studSpacing.toInt(),
                providedArea = totalAs, requiredArea = totalAs * 0.9,
                ratio = 1.0 / punchingResult.utilizationRatio
            )
        } else null

        return FlatSlabResult(
            isSafe = overallSafe, utilizationRatio = maxUtil,
            totalDeadLoad = totalDeadLoad, totalFactoredLoad = wu,
            panelMomentX = MoX, panelMomentY = MoY,
            columnStripMomentPos = colPosX,
            columnStripMomentNeg = maxOf(colNegExtX, colNegIntX),
            middleStripMomentPos = midPosX,
            middleStripMomentNeg = maxOf(midNegExtX, midNegIntX),
            columnStripWidthX = colStripWidthX, columnStripWidthY = colStripWidthY,
            columnStripTopRebar = colTopRebarX, columnStripBotRebar = colBotRebarX,
            middleStripTopRebar = midTopRebarX, middleStripBotRebar = midBotRebarX,
            punchingShearOk = punchingResult.isSafe,
            punchingShearVu = punchingResult.vu, punchingShearVc = punchingResult.vc,
            punchingPerimeter = punchingResult.bo,
            punchingReinforcement = punchingReinforcement,
            dropRequired = dropRequired, dropThickness = recommendedDropThickness,
            deflectionOk = deflectionResult.isSafe,
            deflection = deflectionResult.longTerm, allowableDeflection = deflectionResult.allowable,
            concreteVolumePerPanel = concreteVolume, steelWeightPerPanel = steelWeight,
            warnings = warnings, codeNotes = codeNotes, safetyChecks = safetyChecks
        )
    }

    // ══════════════════════════════════════════════════════════════
    // STATIC MOMENT — DDM
    // ══════════════════════════════════════════════════════════════

    override fun calculateStaticMoment(wu: Double, ln: Double, l2: Double): Double {
        return wu * l2 * ln * ln / 8.0  // kN.m
    }

    // ══════════════════════════════════════════════════════════════
    // MOMENT FACTORS PER PANEL TYPE
    // ══════════════════════════════════════════════════════════════

    private data class MomentFactors(
        val negExterior: Double, val positive: Double, val negInterior: Double
    )

    /**
     * Total moment distribution as fractions of Mo.
     * SBC 304 / ACI 318 Table 8.10.4.1 adapted for flat slabs.
     */
    private fun getMomentFactorsForPanel(panelType: PanelType): MomentFactors {
        return when (panelType) {
            PanelType.INTERIOR -> MomentFactors(0.00, 0.35, 0.65)
            PanelType.EDGE -> MomentFactors(0.26, 0.52, 0.70)
            PanelType.CORNER -> MomentFactors(0.30, 0.50, 0.70)
        }
    }

    // ══════════════════════════════════════════════════════════════
    // MOMENT DISTRIBUTION COEFFICIENTS
    // ══════════════════════════════════════════════════════════════

    override fun getMomentCoefficients(
        panelType: PanelType, hasBeams: Boolean
    ): MomentCoefficients {
        return when (panelType) {
            PanelType.INTERIOR -> MomentCoefficients(
                colNegExterior = 0.75, colPositive = 0.60, colNegInterior = 0.75,
                midNegExterior = 0.25, midPositive = 0.40, midNegInterior = 0.25
            )
            PanelType.EDGE -> MomentCoefficients(
                colNegExterior = 1.00, colPositive = 0.60, colNegInterior = 0.75,
                midNegExterior = 0.00, midPositive = 0.40, midNegInterior = 0.25
            )
            PanelType.CORNER -> MomentCoefficients(
                colNegExterior = 1.00, colPositive = 0.60, colNegInterior = 1.00,
                midNegExterior = 0.00, midPositive = 0.40, midNegInterior = 0.00
            )
        }
    }

    // ══════════════════════════════════════════════════════════════
    // COLUMN STRIP WIDTH
    // ══════════════════════════════════════════════════════════════

    override fun getColumnStripWidth(
        l2: Double, columnSize: Double, panelType: PanelType
    ): Double {
        return l2 / 2.0  // SBC 304 / ACI 8.4.1.5
    }

    // ══════════════════════════════════════════════════════════════
    // PUNCHING SHEAR — SBC 304 §4-3
    // ══════════════════════════════════════════════════════════════

    /**
     * SBC 304 punching shear with φ_shear = 0.75.
     * Uses fcu directly (cube strength) unlike ACI which uses f'c.
     *
     * Three Vc expressions (same form as ACI 318 but with fcu):
     *  1) Vc = 0.24 × √fcu × bo × d
     *  2) Vc = 0.16 × (1 + 2/β) × √fcu × bo × d
     *  3) Vc = 0.08 × (2 + αs×d/bo) × √fcu × bo × d
     *
     * Design capacity: φ × Vc where φ = 0.75
     */
    override fun checkPunchingShear(
        vu: Double, fcu: Double, fy: Double,
        slabThickness: Double, dropThickness: Double,
        columnWidth: Double, columnDepth: Double,
        cover: Double
    ): PunchingShearResult {
        val d = if (dropThickness > 0) {
            slabThickness + dropThickness - cover - 6.0
        } else {
            slabThickness - cover - 6.0
        }

        val b1 = columnWidth + d
        val b2 = columnDepth + d
        val bo = 2.0 * (b1 + b2)

        val beta = max(b1, b2) / min(b1, b2).coerceAtLeast(1.0)
        val alphaS = when {
            columnWidth >= 2000 && columnDepth >= 2000 -> 40.0  // interior
            else -> 30.0  // edge (conservative)
        }

        val sqrtFcu = sqrt(fcu)
        // Nominal Vc expressions (using fcu directly per SBC 304)
        val vc1 = 0.24 * sqrtFcu * bo * d
        val vc2 = 0.16 * (1.0 + 2.0 / beta) * sqrtFcu * bo * d
        val vc3 = 0.08 * (2.0 + alphaS * d / bo) * sqrtFcu * bo * d
        val VcNominal = minOf(vc1, vc2, vc3) / 1000.0  // kN

        // Apply φ_shear = 0.75 (SBC 304)
        val Vc = PHI_SHEAR * VcNominal

        val isSafe = vu <= Vc
        val utilizationRatio = if (Vc > 0) vu / Vc else 2.0

        // Shear reinforcement if needed
        var needsReinf = false
        var studDia = 0.0
        var studSpacing = 0.0
        var studRows = 0
        var vsProvided = 0.0

        if (!isSafe) {
            needsReinf = true
            val vcMax = 1.5 * Vc  // SBC 304: max with reinforcement
            if (vu <= vcMax) {
                val vsRequired = vu - Vc
                val fyDesign = fy / GAMMA_S
                val maxSpacing = min(0.75 * d, 300.0)

                val availableStuds = listOf(10.0, 12.0, 14.0, 16.0)
                for (sDia in availableStuds) {
                    val asStud = PI * sDia * sDia / 4.0
                    val studsPerRow = max(4, (bo / maxSpacing).toInt().coerceAtLeast(4))
                    val vsPerRow = studsPerRow * asStud * fyDesign * d / (maxSpacing * 1000.0)
                    val rows = ceil(vsRequired / vsPerRow).toInt().coerceAtLeast(1)
                    val totalVs = rows * vsPerRow
                    if (totalVs >= vsRequired) {
                        studDia = sDia; studSpacing = maxSpacing
                        studRows = rows; vsProvided = totalVs; break
                    }
                }
            }
        }

        return PunchingShearResult(
            vu = vu, vc = Vc, bo = bo, d = d,
            isSafe = isSafe || (needsReinf && studDia > 0),
            utilizationRatio = utilizationRatio,
            needsReinforcement = needsReinf,
            studDia = studDia, studSpacing = studSpacing,
            studRows = studRows, vsProvided = vsProvided
        )
    }

    // ══════════════════════════════════════════════════════════════
    // REINFORCEMENT DESIGN — SBC 304 K-method with fcu
    // ══════════════════════════════════════════════════════════════

    override fun designReinforcement(
        moment: Double, fcu: Double, fy: Double,
        effectiveDepth: Double, stripWidth: Double, cover: Double
    ): ReinforcementDesign {
        if (moment <= 0.001 || effectiveDepth <= 0) {
            val minAs = getMinReinforcementRatio(fy) * stripWidth * effectiveDepth
            val barArea = PI * 12.0 * 12.0 / 4.0
            val spacing = (barArea * stripWidth / minAs).coerceIn(50.0, getMaxBarSpacing())
            return ReinforcementDesign(minAs, minAs, 12.0, spacing, 1, true)
        }

        val Mu = moment * 1e6  // N.mm
        val b = stripWidth     // mm
        val d = effectiveDepth
        val fs = fy / GAMMA_S

        // K = Mu / (fcu × b × d²) — SBC uses fcu directly
        val K = Mu / (fcu * b * d * d)

        // K_bal with fcu directly
        val epsilonY = fy / (ES * GAMMA_S)
        val aOverD = 0.9 * EPSILON_CU / (EPSILON_CU + epsilonY)
        val K_bal = (0.67 / GAMMA_C) * aOverD * (1.0 - aOverD / 2.0)

        // Lever arm
        val leverArm = if (0.25 - K / 1.25 > 0) {
            d * (0.5 + sqrt(0.25 - K / 1.25))
        } else d * 0.7

        // As = Mu / (fs × z)
        var As = Mu / (fs * leverArm)

        // Minimum reinforcement per SBC 304
        val minAs1 = 0.6 / fy * b * d
        val minAs2 = 0.0018 * b * effectiveDepth  // SBC uses 0.18% (ACI-based)
        val minAs = max(minAs1, minAs2)
        val isMinSteel = As < minAs
        if (isMinSteel) As = minAs

        // Select bar diameter and spacing
        val availableBars = listOf(8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0)
        var selectedDia = 12.0
        var spacing = 200.0

        for (dia in availableBars) {
            val area = PI * dia * dia / 4.0
            val s = (area * stripWidth / As).coerceIn(50.0, getMaxBarSpacing())
            if (s >= 50) { selectedDia = dia; spacing = s; break }
        }

        val asProvided = PI * selectedDia * selectedDia / 4.0 * stripWidth / spacing
        val barsCount = (stripWidth / spacing).toInt().coerceAtLeast(1)

        return ReinforcementDesign(
            asRequired = As, asProvided = asProvided,
            barDia = selectedDia, barSpacing = spacing,
            barsCount = barsCount, isMinSteel = isMinSteel
        )
    }

    private fun designRebarForResult(
        moment: Double, fcu: Double, fy: Double,
        effectiveDepth: Double, stripWidth: Double, cover: Double,
        warnings: MutableList<String>, codeNotes: MutableList<String>, label: String
    ): RebarResult {
        val design = designReinforcement(moment, fcu, fy, effectiveDepth, stripWidth, cover)
        if (design.isMinSteel) {
            codeNotes.add(String.format("%s: Min. steel governs, As=%.0f mm²", label, design.asProvided))
        } else {
            codeNotes.add(String.format(
                "%s: As_req=%.0f, As_prov=%.0f mm², φ%d@%dmm",
                label, design.asRequired, design.asProvided,
                design.barDia.toInt(), design.barSpacing.toInt()
            ))
        }
        return RebarResult(
            bars = design.barsCount, diameter = design.barDia.toInt(),
            spacing = design.barSpacing.toInt(),
            providedArea = design.asProvided, requiredArea = design.asRequired,
            ratio = if (design.asRequired > 0) design.asProvided / design.asRequired else 1.0
        )
    }

    // ══════════════════════════════════════════════════════════════
    // DEFLECTION — SBC 304 / Branson method
    // ══════════════════════════════════════════════════════════════

    override fun checkDeflection(
        span: Double, slabThickness: Double,
        fcu: Double, fy: Double,
        serviceMoment: Double, effectiveDepth: Double,
        providedAs: Double
    ): DeflectionResult {
        // Ec = 4700 × √(fcu) MPa (SBC uses fcu directly)
        val Ec = 4700.0 * sqrt(fcu)
        val Ig = 1000.0 * slabThickness * slabThickness * slabThickness / 12.0
        val n = ES / Ec
        val rho = if (effectiveDepth > 0) providedAs / (1000.0 * effectiveDepth) else 0.0
        val fr = 0.62 * sqrt(fcu)  // modulus of rupture (fcu directly per SBC)
        val yt = slabThickness / 2.0
        val Mcr = fr * Ig / yt / 1e6
        val Icr = n * providedAs * effectiveDepth * effectiveDepth * 0.5

        val Ma = serviceMoment
        val Ie = if (Ma > Mcr && Mcr > 0) {
            val ratio = min((Mcr / Ma).pow(3), 1.0)
            ratio * Ig + (1.0 - ratio) * Icr.coerceAtLeast(Ig * 0.3)
        } else Ig

        val spanMm = span
        val immediate = if (Ec > 0 && Ie > 0) {
            5.0 * serviceMoment * 1e6 * spanMm * spanMm / (48.0 * Ec * Ie)
        } else 0.0

        // Long-term multiplier (SBC 304): λ = 2.0 for sustained loads
        val longTermMult = 2.0
        val longTerm = immediate * longTermMult

        // Allowable: span / 250 (SBC 304 for flat slabs)
        val allowable = span / 250.0
        val ratio = if (allowable > 0) longTerm / allowable else 0.0

        return DeflectionResult(
            immediate = immediate, longTerm = longTerm, allowable = allowable,
            isSafe = longTerm <= allowable, ratio = ratio
        )
    }

    // ══════════════════════════════════════════════════════════════
    // MINIMUM THICKNESS — SBC 304 §6-5-2
    // ══════════════════════════════════════════════════════════════

    override fun getMinimumThickness(
        clearSpan: Double, transverseSpan: Double,
        hasDropPanel: Boolean, fcu: Double, fy: Double
    ): Double {
        val ln = clearSpan / 1000.0  // m
        // SBC 304: h_min = ln / 33 (without drop), ln / 36 (with drop)
        val factor = if (hasDropPanel) 36.0 else 33.0
        val fromSpan = ln * 1000.0 / factor
        return max(fromSpan, 150.0)  // SBC minimum 150mm
    }

    // ══════════════════════════════════════════════════════════════
    // CODE LIMITS
    // ══════════════════════════════════════════════════════════════

    override fun getMinCover(): Double = 25.0

    override fun getMaxBarSpacing(): Double = 250.0  // SBC 304: min(2h, 250mm)

    override fun getMinReinforcementRatio(fy: Double): Double {
        // SBC 304: max(0.6/fy, 0.0018)
        return max(0.6 / fy, 0.0018)
    }

    override fun getCodeName(): String = "SBC 304"

    override fun getFactoredLoad(deadLoad: Double, liveLoad: Double): Double {
        return 1.4 * deadLoad + 1.6 * liveLoad  // SBC 304 §3-2
    }
}