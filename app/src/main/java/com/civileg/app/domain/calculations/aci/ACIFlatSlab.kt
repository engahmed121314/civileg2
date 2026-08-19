package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.*
import com.civileg.app.domain.calculations.base.FlatSlabDesign
import kotlin.math.*

/**
 * ACI 318-19 Flat Slab Design Implementation
 *
 * References:
 *  - ACI 318-19 Ch.8: Two-way slabs
 *  - ACI 318-19 §8.10: Direct Design Method (DDM)
 *  - ACI 318-19 §8.11: Equivalent Frame Method (EFM)
 *  - ACI 318-19 §22.6: Punching shear
 *  - ACI 318-19 §24.2: Deflection
 *  - ACI 318-19 §8.3.1.1: Minimum thickness
 *  - ACI 318-19 §8.2.4: Drop panel requirements
 *
 * Uses φ factors: φ_flexure = 0.9, φ_shear = 0.75
 * Material: f'c (cylinder), fy (yield)
 * Load combination: U = 1.2DL + 1.6LL (ACI Table 5.3.1)
 */
class ACIFlatSlab : FlatSlabDesign {

    companion object {
        private const val PHI_FLEXURE = 0.9
        private const val PHI_SHEAR = 0.75
        private const val BETA_1_LIMIT = 0.85
        private const val EPSILON_CU = 0.003
        private const val ES_MPA = 200000.0
        private const val CONCRETE_UNIT_WEIGHT = 24.0  // kN/m3 (ACI normal weight)
        private const val STEEL_UNIT_WEIGHT = 7850.0
    }

    // ══════════════════════════════════════════════════════════════
    // MAIN ENTRY POINT
    // ══════════════════════════════════════════════════════════════

    override fun design(input: FlatSlabInput): FlatSlabResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val safetyChecks = mutableListOf<SafetyCheckItem>()

        codeNotes.add("ACI 318-19 Ch.8: Two-Way Slab Systems")
        codeNotes.add("Method: ${input.designMethod.displayName}")
        codeNotes.add("φ_flexure = $PHI_FLEXURE, φ_shear = $PHI_SHEAR")

        // Convert fcu (cube) to f'c (cylinder) if needed
        // f'c ≈ 0.8 × fcu
        val fc = input.fcu * 0.8
        val fy = input.fy

        // ── DDM applicability check (ACI 8.10.1) ──────────────────
        val lnX = input.lx - input.columnWidth
        val lnY = input.ly - input.columnDepth
        if (lnX <= 0 || lnY <= 0) {
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
                warnings = listOf("Invalid spans"),
                codeNotes = emptyList(), safetyChecks = emptyList()
            )
        }

        // ACI 8.10.1 checks
        val lnXm = lnX / 1000.0
        val lnYm = lnY / 1000.0
        if (input.designMethod == DesignMethod.DDM) {
            val aspectRatio = max(lnXm, lnYm) / min(lnXm, lnYm).coerceAtLeast(0.1)
            if (aspectRatio > 2.0) {
                warnings.add("ACI 8.10.1.2: Aspect ratio > 2.0 — DDM may not be applicable")
            }
            val selfWt = input.slabThickness / 1000.0 * CONCRETE_UNIT_WEIGHT
            val dl = selfWt + input.floorFinish
            if (input.liveLoad > 2.0 * dl) {
                warnings.add("ACI 8.10.1.5: LL > 2×DL — DDM not applicable, use EFM")
            }
        }

        // ── Dead load ───────────────────────────────────────────────
        val selfWeight = input.slabThickness / 1000.0 * CONCRETE_UNIT_WEIGHT
        val totalDeadLoad = selfWeight + input.floorFinish
        codeNotes.add(String.format(
            "Self-weight = %.0f/1000 × %.0f = %.2f kN/m²",
            input.slabThickness, CONCRETE_UNIT_WEIGHT, selfWeight
        ))

        // ── Factored load: U = 1.2DL + 1.6LL (ACI Table 5.3.1) ─────
        val wu = getFactoredLoad(totalDeadLoad, input.liveLoad)
        codeNotes.add(String.format(
            "U = 1.2×%.2f + 1.6×%.2f = %.2f kN/m²",
            totalDeadLoad, input.liveLoad, wu
        ))

        // ── Static moments (DDM) ───────────────────────────────────
        val MoX = calculateStaticMoment(wu, lnXm, lnYm)
        val MoY = calculateStaticMoment(wu, lnYm, lnXm)
        codeNotes.add(String.format("MoX = %.1f kN.m", MoX))
        codeNotes.add(String.format("MoY = %.1f kN.m", MoY))

        // ── Moment distribution coefficients ────────────────────────
        val coeffs = getMomentCoefficients(input.panelType)

        // ── Effective depths ────────────────────────────────────────
        val dSlab = input.slabThickness - input.clearCover - 6.0
        val dNeg = if (input.dropThickness > 0) {
            input.slabThickness + input.dropThickness - input.clearCover - 6.0
        } else dSlab
        val dPos = dSlab

        // ── Strip widths ────────────────────────────────────────────
        val colStripWidthX = getColumnStripWidth(lnY, input.columnDepth, input.panelType)
        val midStripWidthX = lnY - colStripWidthX
        val colStripWidthY = getColumnStripWidth(lnX, input.columnWidth, input.panelType)
        val midStripWidthY = lnX - colStripWidthY

        // ── Moment factors for panel type ───────────────────────────
        val momentFactors = getACIMomentFactors(input.panelType)

        // X-direction moments
        val mNegExtX = MoX * momentFactors.negExterior
        val mPosX = MoX * momentFactors.positive
        val mNegIntX = MoX * momentFactors.negInterior

        // Column strip X
        val colNegExtX = mNegExtX * coeffs.colNegExterior
        val colPosX = mPosX * coeffs.colPositive
        val colNegIntX = mNegIntX * coeffs.colNegInterior

        // Middle strip X
        val midNegExtX = mNegExtX * coeffs.midNegExterior
        val midPosX = mPosX * coeffs.midPositive
        val midNegIntX = mNegIntX * coeffs.midNegInterior

        codeNotes.add(String.format(
            "X-dir Col strip: +M=%.1f, -M_int=%.1f kN.m", colPosX, colNegIntX
        ))

        // ── Design reinforcement ────────────────────────────────────
        val colTopRebarX = designRebarForResult(
            maxOf(colNegExtX, colNegIntX), fc, fy, dNeg, colStripWidthX,
            input.clearCover, warnings, codeNotes, "Col-Top-X"
        )
        val colBotRebarX = designRebarForResult(
            colPosX, fc, fy, dPos, colStripWidthX,
            input.clearCover, warnings, codeNotes, "Col-Bot-X"
        )
        val midTopRebarX = designRebarForResult(
            maxOf(midNegExtX, midNegIntX), fc, fy, dSlab, midStripWidthX,
            input.clearCover, warnings, codeNotes, "Mid-Top-X"
        )
        val midBotRebarX = designRebarForResult(
            midPosX, fc, fy, dSlab, midStripWidthX,
            input.clearCover, warnings, codeNotes, "Mid-Bot-X"
        )

        // ── Punching shear (ACI 22.6) ───────────────────────────────
        val tribX = (input.lx / 1000.0) / 2.0
        val tribY = (input.ly / 1000.0) / 2.0
        val Vu = wu * tribX * tribY
        codeNotes.add(String.format("Vu (punching) = %.1f kN", Vu))

        val punchingResult = checkPunchingShear(
            vu = Vu, fcu = fc, fy = fy,
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
        if (!punchingResult.isSafe) warnings.add("Punching shear exceeds capacity — φVn < Vu")
        codeNotes.add(String.format(
            "Punching: Vu=%.1f, φVc=%.1f kN, U=%.0f%%",
            punchingResult.vu, punchingResult.vc, punchingResult.utilizationRatio * 100
        ))

        // ── Deflection (ACI 24.2) ──────────────────────────────────
        val serviceMoment = colPosX / 1.2  // approximate unfactored moment
        val deflectionResult = checkDeflection(
            span = lnX, slabThickness = input.slabThickness,
            fcu = fc, fy = fy,
            serviceMoment = serviceMoment,
            effectiveDepth = dSlab,
            providedAs = colBotRebarX.providedArea
        )

        safetyChecks.add(SafetyCheckItem(
            "Deflection", deflectionResult.longTerm, deflectionResult.allowable,
            "mm", deflectionResult.isSafe
        ))
        codeNotes.add(String.format(
            "Deflection: δ=%.2f mm, allow=%.2f mm → %s",
            deflectionResult.longTerm, deflectionResult.allowable,
            if (deflectionResult.isSafe) "OK" else "FAIL"
        ))

        // ── Minimum thickness (ACI Table 8.3.1.1) ──────────────────
        val minThk = getMinimumThickness(lnX, lnY, input.dropThickness > 0, fc, fy)
        val minThkOk = input.slabThickness >= minThk
        safetyChecks.add(SafetyCheckItem(
            "Min. Thickness", input.slabThickness, minThk,
            "mm", minThkOk
        ))
        codeNotes.add(String.format(
            "ACI Table 8.3.1.1: h_min=%.0f mm, prov=%.0f → %s",
            minThk, input.slabThickness, if (minThkOk) "OK" else "FAIL"
        ))

        // ── Drop panel check (ACI 8.2.4) ──────────────────────────
        val dropRequired = !punchingResult.isSafe && !punchingResult.needsReinforcement
        val recommendedDropThk = if (dropRequired) max(input.slabThickness / 4.0, 50.0)
        else input.dropThickness

        if (input.dropThickness > 0) {
            val minDropThk = input.slabThickness / 4.0
            codeNotes.add(String.format(
                "Drop: hd=%.0f mm (ACI 8.2.4 min=%.0f)",
                input.dropThickness, minDropThk
            ))
        }

        // ── Quantities ─────────────────────────────────────────────
        val panelArea = (input.lx / 1000.0) * (input.ly / 1000.0)
        val slabVol = panelArea * input.slabThickness / 1000.0
        val dropVol = if (input.dropThickness > 0 && input.dropSizeX > 0) {
            (input.dropSizeX / 1000.0) * (input.dropSizeY / 1000.0) * input.dropThickness / 1000.0
        } else 0.0
        val concreteVolume = slabVol + dropVol

        val totalAsX = colTopRebarX.providedArea + colBotRebarX.providedArea +
                midTopRebarX.providedArea + midBotRebarX.providedArea
        val totalAsY = totalAsX * (lnYm / lnXm).coerceAtLeast(0.5)
        val steelWeight = (totalAsX + totalAsY) * (input.lx + input.ly) / 1e6 * STEEL_UNIT_WEIGHT

        // ── Overall utilization ─────────────────────────────────────
        val maxUtil = maxOf(punchingResult.utilizationRatio, deflectionResult.ratio)
        val overallSafe = punchingResult.isSafe && deflectionResult.isSafe && minThkOk

        val punchingReinforcement = if (punchingResult.needsReinforcement && punchingResult.studDia > 0) {
            val studArea = PI * punchingResult.studDia * punchingResult.studDia / 4
            val studsPerRow = max(4, (punchingResult.bo / punchingResult.studSpacing).toInt())
            val totalStuds = studsPerRow * punchingResult.studRows
            RebarResult(
                bars = totalStuds,
                diameter = punchingResult.studDia.toInt(),
                spacing = punchingResult.studSpacing.toInt(),
                providedArea = studArea * totalStuds,
                requiredArea = studArea * totalStuds * 0.9,
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
            dropRequired = dropRequired, dropThickness = recommendedDropThk,
            deflectionOk = deflectionResult.isSafe,
            deflection = deflectionResult.longTerm,
            allowableDeflection = deflectionResult.allowable,
            concreteVolumePerPanel = concreteVolume,
            steelWeightPerPanel = steelWeight,
            warnings = warnings, codeNotes = codeNotes,
            safetyChecks = safetyChecks
        )
    }

    // ══════════════════════════════════════════════════════════════
    // STATIC MOMENT
    // ══════════════════════════════════════════════════════════════

    override fun calculateStaticMoment(wu: Double, ln: Double, l2: Double): Double {
        return wu * l2 * ln * ln / 8.0
    }

    // ══════════════════════════════════════════════════════════════
    // MOMENT FACTORS — ACI Table 8.10.4.1
    // ══════════════════════════════════════════════════════════════

    private data class ACIMomentFactors(
        val negExterior: Double,
        val positive: Double,
        val negInterior: Double
    )

    private fun getACIMomentFactors(panelType: PanelType): ACIMomentFactors {
        return when (panelType) {
            PanelType.INTERIOR -> ACIMomentFactors(
                negExterior = 0.00,
                positive = 0.35,
                negInterior = 0.65
            )
            PanelType.EDGE -> ACIMomentFactors(
                negExterior = 0.26,
                positive = 0.52,
                negInterior = 0.70
            )
            PanelType.CORNER -> ACIMomentFactors(
                negExterior = 0.30,
                positive = 0.50,
                negInterior = 0.70
            )
        }
    }

    // ══════════════════════════════════════════════════════════════
    // MOMENT DISTRIBUTION COEFFICIENTS — ACI Table 8.10.4.2
    // ══════════════════════════════════════════════════════════════

    override fun getMomentCoefficients(
        panelType: PanelType, hasBeams: Boolean
    ): MomentCoefficients {
        // ACI 318-19 Table 8.10.4.2 for flat slabs (α = 0, no beams)
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
    // COLUMN STRIP WIDTH — ACI 8.4.1.5
    // ══════════════════════════════════════════════════════════════

    override fun getColumnStripWidth(l2: Double, columnSize: Double, panelType: PanelType): Double {
        return l2 / 2.0
    }

    // ══════════════════════════════════════════════════════════════
    // PUNCHING SHEAR — ACI 22.6
    // ══════════════════════════════════════════════════════════════

    override fun checkPunchingShear(
        vu: Double, fcu: Double, fy: Double,
        slabThickness: Double, dropThickness: Double,
        columnWidth: Double, columnDepth: Double,
        cover: Double
    ): PunchingShearResult {
        val fc = fcu  // already f'c
        val d = if (dropThickness > 0) {
            slabThickness + dropThickness - cover - 6.0
        } else {
            slabThickness - cover - 6.0
        }

        // Critical section at d/2 from column face (ACI 22.6.4)
        val b1 = columnWidth + d
        val b2 = columnDepth + d
        val bo = 2.0 * (b1 + b2)

        // Vc = min of three expressions (ACI 22.6.5.2)
        val beta = max(b1, b2) / min(b1, b2).coerceAtLeast(1.0)
        val alphaS = 40.0  // interior column
        val lambda = 1.0   // normal weight concrete

        val sqrtFc = sqrt(fc)
        // 1) Vc = 0.17 × (1 + 2/β) × λ × √f'c × bo × d
        val vc1 = 0.17 * (1.0 + 2.0 / beta) * lambda * sqrtFc * bo * d / 1000.0  // kN
        // 2) Vc = 0.083 × (2 + αs×d/bo) × λ × √f'c × bo × d
        val vc2 = 0.083 * (2.0 + alphaS * d / bo) * lambda * sqrtFc * bo * d / 1000.0
        // 3) Vc = 0.33 × λ × √f'c × bo × d
        val vc3 = 0.33 * lambda * sqrtFc * bo * d / 1000.0

        val Vc = minOf(vc1, vc2, vc3)
        val phiVc = PHI_SHEAR * Vc

        val isSafe = vu <= phiVc
        val utilizationRatio = if (phiVc > 0) vu / phiVc else 2.0

        // Shear reinforcement if needed (ACI 22.6.6)
        var needsReinf = false
        var studDia = 0.0
        var studSpacing = 0.0
        var studRows = 0
        var vsProvided = 0.0

        if (!isSafe) {
            needsReinf = true
            val vcMax = PHI_SHEAR * 1.5 * Vc  // ACI 22.6.6.1
            if (vu > vcMax) {
                // Max capacity exceeded — need thicker slab
            } else {
                val vsNeeded = (vu - phiVc) / PHI_SHEAR
                val availableStuds = listOf(10.0, 12.0, 14.0, 16.0)
                val maxSpacing = min(0.75 * d, 300.0)
                for (sDia in availableStuds) {
                    val asStud = PI * sDia * sDia / 4.0
                    val studsPerRow = max(4, (bo / maxSpacing).toInt().coerceAtLeast(4))
                    // Vs per row = n × As × fy × d / s
                    val vsPerRow = studsPerRow * asStud * fy * d / (maxSpacing * 1000.0)
                    val rows = ceil(vsNeeded / vsPerRow).toInt().coerceAtLeast(1)
                    if (rows * vsPerRow >= vsNeeded) {
                        studDia = sDia
                        studSpacing = maxSpacing
                        studRows = rows
                        vsProvided = rows * vsPerRow
                        break
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
    // REINFORCEMENT — ACI 318 §22.2 / §24.4
    // ══════════════════════════════════════════════════════════════

    override fun designReinforcement(
        moment: Double, fcu: Double, fy: Double,
        effectiveDepth: Double, stripWidth: Double,
        cover: Double
    ): ReinforcementDesign {
        val fc = fcu
        if (moment <= 0.001 || effectiveDepth <= 0) {
            val minAs = getMinReinforcementRatio(fy) * stripWidth * effectiveDepth
            val barArea = PI * 12.0 * 12.0 / 4.0
            val spacing = (barArea * stripWidth / minAs).coerceIn(50.0, getMaxBarSpacing())
            return ReinforcementDesign(minAs, minAs, 12.0, spacing, 1, true)
        }

        val Mu = moment * 1e6 * PHI_FLEXURE  // factored N.mm (with φ)
        val b = stripWidth
        val d = effectiveDepth

        // R = Mu / (b × d²)
        val R = Mu / (b * d * d)
        // ρ = (0.85 × fc / fy) × [1 - √(1 - 2R / (0.85 × fc))]
        val arg = 1.0 - 2.0 * R / (0.85 * fc)
        val rho = if (arg > 0) {
            (0.85 * fc / fy) * (1.0 - sqrt(arg))
        } else 0.025  // over-reinforced — capped

        var As = rho * b * d

        // Minimum: max(0.0018, 0.0014 for fy>420) × b × h (ACI 7.6.1.1)
        val minAs = getMinReinforcementRatio(fy) * stripWidth * effectiveDepth
        val isMinSteel = As < minAs
        if (isMinSteel) As = minAs

        // Select bars
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
        moment: Double, fc: Double, fy: Double,
        effectiveDepth: Double, stripWidth: Double,
        cover: Double,
        warnings: MutableList<String>,
        codeNotes: MutableList<String>,
        label: String
    ): RebarResult {
        val design = designReinforcement(moment, fc, fy, effectiveDepth, stripWidth, cover)
        codeNotes.add(String.format(
            "%s: As_req=%.0f, As_prov=%.0f mm², φ%d@%dmm%s",
            label, design.asRequired, design.asProvided,
            design.barDia.toInt(), design.barSpacing.toInt(),
            if (design.isMinSteel) " (min)" else ""
        ))
        return RebarResult(
            bars = design.barsCount,
            diameter = design.barDia.toInt(),
            spacing = design.barSpacing.toInt(),
            providedArea = design.asProvided,
            requiredArea = design.asRequired,
            ratio = if (design.asRequired > 0) design.asProvided / design.asRequired else 1.0
        )
    }

    // ══════════════════════════════════════════════════════════════
    // DEFLECTION — ACI 24.2
    // ══════════════════════════════════════════════════════════════

    override fun checkDeflection(
        span: Double, slabThickness: Double,
        fcu: Double, fy: Double,
        serviceMoment: Double, effectiveDepth: Double,
        providedAs: Double
    ): DeflectionResult {
        val fc = fcu
        val Ec = 4700.0 * sqrt(fc)
        val Ig = 1000.0 * slabThickness.pow(3) / 12.0
        val n = ES_MPA / Ec
        val fr = 0.62 * sqrt(fc)  // modulus of rupture
        val yt = slabThickness / 2.0
        val Mcr = fr * Ig / yt / 1e6

        // Branson equation
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

        // ACI 24.2.4.1: long-term multiplier
        // λ = ξ / (1 + 50ρ') for 5+ years: ξ = 2.0
        // Simplified: λ = 2.0
        val longTermMult = 2.0
        val longTerm = immediate * longTermMult
        val allowable = span / 240.0  // ACI Table 24.2.2
        val ratio = if (allowable > 0) longTerm / allowable else 0.0

        return DeflectionResult(
            immediate = immediate, longTerm = longTerm,
            allowable = allowable, isSafe = longTerm <= allowable, ratio = ratio
        )
    }

    // ══════════════════════════════════════════════════════════════
    // MINIMUM THICKNESS — ACI Table 8.3.1.1
    // ══════════════════════════════════════════════════════════════

    override fun getMinimumThickness(
        clearSpan: Double, transverseSpan: Double,
        hasDropPanel: Boolean, fcu: Double, fy: Double
    ): Double {
        val ln = clearSpan / 1000.0
        val fyKsi = fy / 6.895  // MPa to ksi
        val factor = if (hasDropPanel) 36.0 else 33.0
        // ACI 8.3.1.1 correction factor for reinforcement yield
        val correction = 0.4 + 0.6 * min(fyKsi / 60.0, 1.0)
        val minH = ln * 1000.0 / (factor / correction)
        return max(minH, 125.0)  // ACI minimum 5 in ≈ 127mm
    }

    // ══════════════════════════════════════════════════════════════
    // CODE LIMITS
    // ══════════════════════════════════════════════════════════════

    override fun getMinCover(): Double = 20.0  // ACI 20.5.1.3

    override fun getMaxBarSpacing(): Double = 450.0  // ACI 24.3: min(2h, 450mm)

    override fun getMinReinforcementRatio(fy: Double): Double {
        // ACI 7.6.1.1: max(0.0018, 0.0014 for fy > 420 MPa)
        return if (fy > 420) 0.0014 else 0.0018
    }

    override fun getCodeName(): String = "ACI 318-19"

    override fun getFactoredLoad(deadLoad: Double, liveLoad: Double): Double {
        // ACI Table 5.3.1: U = 1.2DL + 1.6LL
        return 1.2 * deadLoad + 1.6 * liveLoad
    }
}
