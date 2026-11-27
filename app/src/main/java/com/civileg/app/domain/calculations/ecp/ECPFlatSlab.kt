package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.*
import com.civileg.app.domain.entities.CodeReference
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.flatSlabCalculationSteps
import com.civileg.app.domain.calculations.base.FlatSlabDesign
import com.civileg.app.domain.calculations.base.FlatSlabDesign.MomentCoefficients
import com.civileg.app.domain.calculations.base.FlatSlabDesign.PunchingShearResult
import com.civileg.app.domain.calculations.base.FlatSlabDesign.ReinforcementDesign
import com.civileg.app.domain.calculations.base.FlatSlabDesign.DeflectionResult
import kotlin.math.*

/**
 * ECP 203-2025 Flat Slab Design (empirical method, 6-2-5-5)
 *
 * Sources (ECP 203-2025, Arabic edition, printed page numbers):
 *  - Min. thickness: Table 6-3 (p.6-18); min. column size 6-2-5-2-b (p.6-18)
 *  - Strips: Fig 6-7, column strip = Lmin/4 each side of the column axis (p.6-24)
 *  - Total moment: Eq 6-18, M0 = (W L2 / 8)(L1 - 2D/3)^2 (p.6-24)
 *  - Distribution: Table 6-5 and Fig 6-9 (p.6-25)
 *  - Punching, concrete only: Eq 4-44, 4-46-a/b/c, cap 1.70 N/mm2 (p.4-25 to 4-29)
 *  - Punching reinforcement: Eq 4-47-a/b/c, fy <= 350, h >= 250 mm (p.4-27 to 4-29)
 *  - Moment-transfer shear (simplified): Eq 6-25, beta = 1.15 / 1.30 / 1.50 (p.6-31)
 *  - Min. steel: 6-2-1-2-3 (p.6-6): 0.6/fy and 0.25 % (mild) or 0.15-0.18 % (high grade) of Ac
 *  - Max. bar spacing 200 mm: 6-2-1-3-4-a (p.6-9)
 *  - Flexure: K-method with Kmax from Table 4-1 (via ECPBeam.kMax), z <= 0.95 d (4-1-2-2-a p.4-5)
 * NOT ASSESSED (clause not read or not implemented): deflection (4-3-1), second
 * punching section outside a drop panel, column-head reinforcement (6-2-5-9),
 * equivalent-frame analysis, edge-beam panels, openings (6-2-5-10).
 */
class ECPFlatSlab : FlatSlabDesign {

    companion object {
        private const val GAMMA_C = 1.5      // ECP 203 §2-3-1
        private const val GAMMA_S = 1.15     // ECP 203 §2-3-1
        private const val EPSILON_CU = 0.003
        private const val ES = 200000.0      // MPa (steel modulus)
        private const val CONCRETE_UNIT_WEIGHT = 25.0  // kN/m3
        private const val STEEL_UNIT_WEIGHT = 7850.0  // kg/m3
        private const val ASSUMED_BAR_DIA = 12.0       // d = h - cover - 6 assumes phi12
        private const val STUD_FY_CAP = 350.0          // ECP 203-2025 p.4-27: fy of shear steel <= 350

        /** ECP 203-2025 Table 6-3 (p.6-18): rows fy 350/420/500/600/700; Ln/value.
         *  Columns: no drop (ext no edge beam, ext edge beam, interior), with drop (same). */
        private val TABLE_6_3 = listOf(
            350.0 to doubleArrayOf(32.0, 35.0, 35.0, 35.0, 38.0, 38.0),
            420.0 to doubleArrayOf(30.0, 33.0, 33.0, 33.0, 36.0, 36.0),
            500.0 to doubleArrayOf(28.0, 31.0, 31.0, 31.0, 34.0, 34.0),
            600.0 to doubleArrayOf(24.0, 26.0, 26.0, 26.0, 29.0, 29.0),
            700.0 to doubleArrayOf(20.0, 22.0, 22.0, 22.0, 24.0, 24.0)
        )

        /** Table 6-3 divisor; uses the highest tabulated fy not above the actual fy (conservative, no interpolation). */
        fun table63Divisor(fy: Double, exterior: Boolean, hasDrop: Boolean): Double {
            val row = TABLE_6_3.lastOrNull { it.first <= fy } ?: TABLE_6_3.first()
            val col = (if (hasDrop) 3 else 0) + (if (exterior) 0 else 2)
            return row.second[col]
        }

        /** Table 6-5 fractions of the total panel moment M0 (p.6-25). */
        data class Table65(
            val colNegExt: Double, val colPos: Double, val colNegInt: Double,
            val midNegExt: Double, val midPos: Double, val midNegInt: Double
        )
        /** Interior bay (both supports interior). */
        val BAY_INTERIOR = Table65(0.45, 0.25, 0.45, 0.15, 0.15, 0.15)
        /** Exterior bay, type a (no edge beam): ext-neg 0.30, pos 0.50, int-neg 0.70 of M0 (Fig 6-9). */
        val BAY_EXTERIOR = Table65(0.25, 0.30, 0.50, 0.05, 0.20, 0.20)

        private fun env(a: Table65, b: Table65) = Table65(
            max(a.colNegExt, b.colNegExt), max(a.colPos, b.colPos), max(a.colNegInt, b.colNegInt),
            max(a.midNegExt, b.midNegExt), max(a.midPos, b.midPos), max(a.midNegInt, b.midNegInt)
        )

        /** Edge panel orientation is not an input: envelope of exterior and interior bay. Corner: exterior both ways. */
        fun table65For(panelType: PanelType): Table65 = when (panelType) {
            PanelType.INTERIOR -> BAY_INTERIOR
            PanelType.EDGE -> env(BAY_INTERIOR, BAY_EXTERIOR)
            PanelType.CORNER -> BAY_EXTERIOR
        }

        /** Eq 6-18: M0 = (W L2/8)(L1 - 2D/3)^2, W in kN/m2, lengths in m, result kN.m. */
        fun totalMomentEq618(w: Double, l1: Double, l2: Double, d: Double): Double {
            val e = l1 - 2.0 * d / 3.0
            return w * l2 / 8.0 * e * e
        }

        /** Eq 6-25 beta: 1.15 interior, 1.30 edge, 1.50 corner (p.6-31). */
        fun betaEq625(panelType: PanelType) = when (panelType) {
            PanelType.INTERIOR -> 1.15; PanelType.EDGE -> 1.30; PanelType.CORNER -> 1.50
        }
        /** Share of the panel load carried by the column: 1, 1/2, 1/4. */
        fun tributaryShare(panelType: PanelType) = when (panelType) {
            PanelType.INTERIOR -> 1.0; PanelType.EDGE -> 0.5; PanelType.CORNER -> 0.25
        }
    }

    // ══════════════════════════════════════════════════════════════
    // MAIN ENTRY POINT
    // ══════════════════════════════════════════════════════════════

    override fun design(input: FlatSlabInput): FlatSlabResult {
        // ── InputGuard (ADR-010) — ECP 203 ──
        InputGuard.notNull("input", input)
        InputGuard.positive("fcu", input.fcu)
        InputGuard.positive("fy", input.fy)
        InputGuard.positive("lx", input.lx)
        InputGuard.positive("ly", input.ly)
        InputGuard.positive("slabThickness", input.slabThickness)
        // deadLoad is computed from self-weight, not an input field
        InputGuard.nonNegative("floorFinish", input.floorFinish)
        val deadLoad = CONCRETE_UNIT_WEIGHT * input.slabThickness / 1000.0 + input.floorFinish
        InputGuard.nonNegative("deadLoad", deadLoad)
        InputGuard.nonNegative("liveLoad", input.liveLoad)
        InputGuard.notNull("designMethod", input.designMethod)

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val safetyChecks = mutableListOf<SafetyCheckItem>()

        codeNotes.add(CodeReference.ECP.SLAB_TWO_WAY)
        codeNotes.add("ECP 203-2025 §6-5: Flat Slab Design")
        codeNotes.add("Method: ${input.designMethod.displayName}")
        codeNotes.add("γc = $GAMMA_C, γs = $GAMMA_S")

        // ── 1. Material checks ─────────────────────────────────────
        if (input.fcu < 20) warnings.add("ECP 203: fcu ≥ 20 MPa recommended for flat slabs")
        if (input.fy < 240 || input.fy > 500) warnings.add("ECP 203: fy should be 240–500 MPa")
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
        val selfWeight = input.slabThickness / 1000.0 * CONCRETE_UNIT_WEIGHT  // kN/m2
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

        // ── 4. Spans, strips, applicability (ECP 203-2025 6-2-5-5-a, Fig 6-7) ─────
        val lxM = input.lx / 1000.0
        val lyM = input.ly / 1000.0
        val lnX = input.lx - input.columnWidth   // mm, clear span (reporting / min. thickness)
        val lnY = input.ly - input.columnDepth   // mm
        val lMin = min(input.lx, input.ly)
        val lMax = max(input.lx, input.ly)
        codeNotes.add(String.format("Clear spans: lnX = %.0f mm, lnY = %.0f mm", lnX, lnY))

        var applicable = true
        if (lMax / lMin > 1.3) {
            applicable = false
            warnings.add(String.format(
                "Empirical method not applicable: aspect ratio %.2f > 1.3 (ECP 203-2025 6-2-5-5-a, p.6-24). Moments NOT ASSESSED by this method.",
                lMax / lMin))
        }
        if (input.liveLoad > 2.0 * totalDeadLoad) {
            applicable = false
            warnings.add("Empirical method not applicable: live load > 2 x dead load (6-2-5-5-a, p.6-24).")
        }
        codeNotes.add("Applicability also needs >= 3 rows of panels and adjacent spans within 10 % (6-2-5-5-a); not an input, assumed by the user")
        if (input.designMethod == DesignMethod.EFM) {
            warnings.add("Equivalent-frame method (Table 6-4) is not implemented: results use the empirical method (6-2-5-5).")
        }
        if (input.panelType == PanelType.EDGE) {
            codeNotes.add("Edge panel: orientation is not an input, so the larger of exterior-bay and interior-bay Table 6-5 shares is used per location (envelope); no edge beam assumed (type a)")
        }
        // Minimum column dimension, 6-2-5-2-b (p.6-18)
        val hStoryMm = input.storyHeight * 1000.0
        val minColX = max(max(input.lx / 20.0, hStoryMm / 15.0), 300.0)
        val minColY = max(max(input.ly / 20.0, hStoryMm / 15.0), 300.0)
        if (input.columnWidth < minColX || input.columnDepth < minColY) {
            warnings.add(String.format(
                "Column below ECP 203-2025 6-2-5-2-b minimum (max of L/20, H/15, 300 mm): need %.0f x %.0f mm, have %.0f x %.0f mm",
                minColX, minColY, input.columnWidth, input.columnDepth))
        }

        // ── 5. Effective depths (phi12 assumed, as before) ────────────
        val dSlab = input.slabThickness - input.clearCover - ASSUMED_BAR_DIA / 2.0
        val dropOk = input.dropThickness > 0 && input.dropSizeX >= input.lx / 3.0 && input.dropSizeY >= input.ly / 3.0 &&
            input.dropThickness >= input.slabThickness / 4.0 &&
            input.dropSizeX <= lMin / 2.0 + 1e-6 && input.dropSizeY <= lMin / 2.0 + 1e-6
        if (input.dropThickness > 0 && !dropOk) {
            warnings.add("Drop panel does not meet 6-2-5-2-d (p.6-18): thickness below slab >= t/4, extent >= L/6 each side and <= L/4 of the smaller span (or extent not given). Drop is ignored for flexure.")
        }
        val dNeg = if (dropOk) dSlab + input.dropThickness else dSlab
        val dPos = dSlab
        codeNotes.add(String.format("d (positive) = %.0f mm, d (negative column strip) = %.0f mm", dPos, dNeg))

        // ── 6. Total moments, Eq 6-18 ─────────────────────────────
        val m0X = totalMomentEq618(wu, lxM, lyM, input.columnWidth / 1000.0)
        val m0Y = totalMomentEq618(wu, lyM, lxM, input.columnDepth / 1000.0)
        codeNotes.add(String.format("M0X = (%.2f x %.2f / 8)(%.2f - 2x%.2f/3)^2 = %.1f kN.m (Eq 6-18)", wu, lyM, lxM, input.columnWidth / 1000.0, m0X))
        codeNotes.add(String.format("M0Y = (%.2f x %.2f / 8)(%.2f - 2x%.2f/3)^2 = %.1f kN.m (Eq 6-18)", wu, lxM, lyM, input.columnDepth / 1000.0, m0Y))
        val t65 = table65For(input.panelType)
        codeNotes.add(String.format(
            "Table 6-5 shares of M0: col strip -ext %.2f, +%.2f, -int %.2f; mid strip -ext %.2f, +%.2f, -int %.2f",
            t65.colNegExt, t65.colPos, t65.colNegInt, t65.midNegExt, t65.midPos, t65.midNegInt))

        // ── 7. Strip widths: column strip = Lmin/4 each side (Fig 6-7) ───
        val colStripWidth = lMin / 2.0
        val midStripWidthX = input.ly - colStripWidth   // strips for the X-direction span, width measured along Y
        val midStripWidthY = input.lx - colStripWidth
        codeNotes.add(String.format("Column strip = %.0f mm; middle strip X-dir = %.0f mm, Y-dir = %.0f mm (Fig 6-7)", colStripWidth, midStripWidthX, midStripWidthY))
        if (dropOk) codeNotes.add("With a drop panel the column strip equals the drop width (6-2-5-3): not applied, Lmin/2 used")

        val overReinf = mutableListOf<String>()
        fun strips(m0: Double, dp: Double, dn: Double, midW: Double, tag: String): DirResult {
            val cNE = m0 * t65.colNegExt; val cP = m0 * t65.colPos; val cNI = m0 * t65.colNegInt
            val mNE = m0 * t65.midNegExt; val mP = m0 * t65.midPos; val mNI = m0 * t65.midNegInt
            codeNotes.add(String.format("%s Col strip: -ext=%.1f, +=%.1f, -int=%.1f; Mid strip: -ext=%.1f, +=%.1f, -int=%.1f kN.m", tag, cNE, cP, cNI, mNE, mP, mNI))
            val h = input.slabThickness
            return DirResult(
                cNE, cP, cNI, mNE, mP, mNI,
                designRebarForResult(max(cNE, cNI), input.fcu, input.fy, dn, colStripWidth, h, input.clearCover, warnings, codeNotes, overReinf, "Col-Top-$tag"),
                designRebarForResult(cP, input.fcu, input.fy, dp, colStripWidth, h, input.clearCover, warnings, codeNotes, overReinf, "Col-Bot-$tag"),
                designRebarForResult(max(mNE, mNI), input.fcu, input.fy, dp, midW, h, input.clearCover, warnings, codeNotes, overReinf, "Mid-Top-$tag"),
                designRebarForResult(mP, input.fcu, input.fy, dp, midW, h, input.clearCover, warnings, codeNotes, overReinf, "Mid-Bot-$tag")
            )
        }
        // X bars lie in the outer layer; Y bars sit one bar diameter deeper.
        val dirX = strips(m0X, dPos, dNeg, midStripWidthX, "X")
        val dirY = strips(m0Y, dPos - ASSUMED_BAR_DIA, dNeg - ASSUMED_BAR_DIA, midStripWidthY, "Y")
        // Y mid-strip top uses slab depth minus one bar layer (no drop in the middle strip)
        val colPosX = dirX.cP; val colNegExtX = dirX.cNE; val colNegIntX = dirX.cNI
        val midPosX = dirX.mP; val midNegExtX = dirX.mNE; val midNegIntX = dirX.mNI
        val colTopRebarX = dirX.colTop; val colBotRebarX = dirX.colBot
        val midTopRebarX = dirX.midTop; val midBotRebarX = dirX.midBot
        val colStripWidthX = colStripWidth
        val colStripWidthY = colStripWidth

        // ── 8. Punching shear (Eq 4-44/4-46, Eq 6-25 for moment transfer) ──
        val share = tributaryShare(input.panelType)
        val q = wu * lxM * lyM * share            // kN, design shear at the column (6-2-5-7-3)
        val betaM = betaEq625(input.panelType)
        val Vu = q * betaM
        codeNotes.add(String.format(
            "Column load Q = %.2f x %.2f x %.2f x %.2f = %.1f kN; beta = %.2f (Eq 6-25); Vu = Q x beta = %.1f kN",
            wu, lxM, lyM, share, q, betaM, Vu))
        val punchingResult = punchingCheck(
            vu = Vu, fcu = input.fcu, fy = input.fy,
            slabThickness = input.slabThickness, dropThickness = if (dropOk) input.dropThickness else 0.0,
            columnWidth = input.columnWidth, columnDepth = input.columnDepth,
            cover = input.clearCover, panelType = input.panelType
        )
        safetyChecks.add(SafetyCheckItem("Punching Shear", punchingResult.vu, punchingResult.vc, "kN", punchingResult.isSafe))
        if (!punchingResult.isSafe) {
            warnings.add("Punching shear FAILED (ECP 203-2025 4-2-2-3): increase thickness or column size, add a drop panel (re-check its outer section), or revise reinforcement")
        }
        codeNotes.add(String.format(
            "Punching: Vu=%.1f kN, Vc(concrete)=%.1f kN, bo=%.0f mm, d=%.0f mm, U=%.0f%%",
            punchingResult.vu, punchingResult.vc, punchingResult.bo, punchingResult.d, punchingResult.utilizationRatio * 100))
        if (punchingResult.needsReinforcement && punchingResult.studDia > 0) {
            codeNotes.add(String.format("Shear studs (Eq 4-47, fy<=350): phi%d @ %.0f mm, %d lines, total capacity %.0f kN",
                punchingResult.studDia.toInt(), punchingResult.studSpacing, punchingResult.studRows, punchingResult.vsProvided))
        }
        val pos = when (input.panelType) {
            PanelType.INTERIOR -> ECP2025FlatSlabExtras.Position.INTERIOR
            PanelType.EDGE -> ECP2025FlatSlabExtras.Position.EDGE
            PanelType.CORNER -> ECP2025FlatSlabExtras.Position.CORNER
        }
        var dropEdgeOk = true
        if (dropOk) {
            // Second critical section at d/2 outside the drop panel edge, slab-only d (p.4-26 item 3-a, Fig 4-13 p.4-27)
            val secs = ECP2025FlatSlabExtras.punchingSections(
                Vu, input.fcu, input.slabThickness, input.dropThickness,
                input.columnWidth, input.columnDepth, input.dropSizeX, input.dropSizeY,
                input.clearCover, ASSUMED_BAR_DIA, pos)
            val outer = secs.last()
            dropEdgeOk = outer.ok
            safetyChecks.add(SafetyCheckItem("Punching at drop edge", outer.vuKn, outer.vcKn, "kN", outer.ok))
            codeNotes.add(String.format(
                "Punching at drop panel edge (ECP 203-2025 p.4-26 item 3-a): bo=%.0f mm, d=%.0f mm (slab only), Vu=%.1f kN (Eq 6-25 beta applied by analogy, not printed for the drop edge; no reduction found in code text), Vc=%.1f kN, U=%.0f%% -> %s",
                outer.bo, outer.d, outer.vuKn, outer.vcKn, outer.utilization * 100, if (outer.ok) "OK" else "FAIL"))
            if (!outer.ok) warnings.add("Punching FAILED at the drop panel edge (ECP 203-2025 p.4-26 item 3-a): enlarge the drop panel or increase the slab thickness")
        }
        if (!punchingResult.isSafe && !dropOk && input.dropThickness <= 0.0) {
            // Suggest a drop panel and re-check both sections for it (limits 6-2-5-2-d, p.6-19)
            val sizeX = input.lx / 3.0
            val sizeY = input.ly / 3.0
            var found: Pair<Double, List<ECP2025FlatSlabExtras.Section>>? = null
            var proj = max(ceil(input.slabThickness / 4.0 / 25.0) * 25.0, 50.0)
            while (found == null && proj <= input.slabThickness) {
                if (sizeX <= lMin / 2.0 + 1e-6 && sizeY <= lMin / 2.0 + 1e-6) {
                    val trial = ECP2025FlatSlabExtras.punchingSections(
                        Vu, input.fcu, input.slabThickness, proj, input.columnWidth, input.columnDepth,
                        sizeX, sizeY, input.clearCover, ASSUMED_BAR_DIA, pos)
                    if (trial.all { it.ok }) found = proj to trial
                }
                proj += 25.0
            }
            if (found != null) {
                warnings.add(String.format(
                    "Suggested drop panel: %.0f x %.0f mm, %.0f mm below the slab. Both punching sections pass concrete-only (Vu %.0f kN; Vc %.0f kN at column, %.0f kN at drop edge). Enter these values and recalculate.",
                    sizeX, sizeY, found.first, Vu, found.second.first().vcKn, found.second.last().vcKn))
            } else {
                warnings.add("No drop panel within the 6-2-5-2-d limits (t/4 to t projection, extent L/3 to Lmin/2) passes punching: increase slab thickness or column size")
            }
        }
        val headIssues = if (input.headEffectiveDiameter > 0.0)
            ECP2025FlatSlabExtras.columnHeadIssues(input.headSlopeFromVerticalDeg, input.headEffectiveDiameter, minOf(input.lx, input.ly))
        else emptyList()
        if (input.headEffectiveDiameter > 0.0) {
            safetyChecks.add(SafetyCheckItem("Column head geometry (p.6-18)", input.headEffectiveDiameter, minOf(input.lx, input.ly) / 4.0, "mm", headIssues.isEmpty()))
            headIssues.forEach { warnings.add(it) }
        } else {
            codeNotes.add("Column head slope <= 45 deg and D <= Lmin/4 (p.6-18): no column head given, NOT ASSESSED")
        }
        codeNotes.add("Column-head reinforcement (6-2-5-9) and openings (6-2-5-10) are NOT ASSESSED")

        // ── 9. Deflection: NOT ASSESSED ───────────────────────────
        val defl = estimateDeflection(
            input, totalDeadLoad, wu, dirX, dirY, colStripWidth, midStripWidthX, midStripWidthY, dPos, dNeg
        )
        val deflOk = defl.totalCheck.ok && defl.liveCheck.ok
        safetyChecks.add(SafetyCheckItem("Deflection (estimate)", defl.total, defl.totalCheck.allowable, "mm", defl.totalCheck.ok))
        safetyChecks.add(SafetyCheckItem("Live-load deflection (estimate)", defl.immediateLive, defl.liveCheck.allowable, "mm", defl.liveCheck.ok))
        codeNotes.add(String.format(
            "Deflection ESTIMATE (panel centre, strips combined as in ACI practice): immediate D+L = %.2f mm, live part = %.2f mm (limit L/360 = %.2f, Eq 4-76), alpha = %.2f (Eq 4-73, xi 2.0, rho' 0), total = %.2f mm (limit L/250 = %.2f mm with L = long span, Eq 4-74, p.4-63/4-64) -> %s",
            defl.immediateTotal, defl.immediateLive, defl.liveCheck.allowable, defl.alpha, defl.total, defl.totalCheck.allowable,
            if (deflOk) "OK" else "FAIL"))
        codeNotes.add(ECP2025FlatSlabExtras.ESTIMATE_LABEL)
        codeNotes.add("Estimate assumptions: Ec = 4400 sqrt(fcu) (ECP 203-2025 2-3-3-1, Eq 2-1, p.2-23) used at all load levels, long-term effect only through alpha (Eq 4-73); Es = 200000 N/mm2 (Fig 4-1, p.4-2); strip moments scaled from the factored Table 6-5 moments; Ie by Eq 4-70/4-72 at midspan and both supports; drop panel thickness ignored for stiffness; sustained live load taken as zero; compression steel ignored")
        warnings.add("Deflection is an ESTIMATE using ECP 203-2025 general provisions (4-3-1); the code gives no flat slab strip method. Table 6-3 thickness does not replace it.")
        if (!deflOk) warnings.add("Estimated deflection exceeds the ECP 203-2025 limit (Eq 4-74 L/250 total, Eq 4-76 L/360 live): increase the slab thickness or revise the design")

        // ── 10. Minimum thickness, Table 6-3 ──────────────────────
        val exteriorPanel = input.panelType != PanelType.INTERIOR
        val minThk = getMinimumThickness(lnX, lnY, dropOk, input.fcu, input.fy, exteriorPanel)
        val minThkOk = input.slabThickness >= minThk
        safetyChecks.add(SafetyCheckItem("Min. Thickness", input.slabThickness, minThk, "mm", minThkOk))
        if (!minThkOk) warnings.add("Slab thickness below ECP 203-2025 Table 6-3 minimum")
        val divisor = table63Divisor(input.fy, exteriorPanel, dropOk)
        codeNotes.add(String.format(
            "Min thickness (Table 6-3 p.6-18) = Ln/%.0f = %.0f/%.0f = %.0f mm, provided %.0f mm -> %s (fy row %.0f used; no interpolation)",
            divisor, max(lnX, lnY), divisor, minThk, input.slabThickness, if (minThkOk) "OK" else "FAIL",
            TABLE_6_3.lastOrNull { it.first <= input.fy }?.first ?: TABLE_6_3.first().first))

        // ── 11. Drop panel ──────────────────────────────────────
        val dropRequired = !punchingResult.isSafe && !punchingResult.needsReinforcement
        val recommendedDropThickness = if (dropRequired) max(input.slabThickness / 4.0, 50.0) else input.dropThickness
        if (input.dropThickness > 0) {
            codeNotes.add(String.format("Drop panel: thickness below slab %.0f mm (min t/4 = %.0f mm, 6-2-5-2-d)", input.dropThickness, input.slabThickness / 4.0))
        }

        // ── 12. Quantities (upper-bound estimate: no curtailment, laps or hooks) ──
        val panelArea = lxM * lyM
        val slabVol = panelArea * input.slabThickness / 1000.0
        val dropVol = if (input.dropThickness > 0 && input.dropSizeX > 0) {
            (input.dropSizeX / 1000.0) * (input.dropSizeY / 1000.0) * input.dropThickness / 1000.0
        } else 0.0
        val concreteVolume = slabVol + dropVol
        val asX = colTopRebarX.providedArea + colBotRebarX.providedArea + midTopRebarX.providedArea + midBotRebarX.providedArea
        val asY = dirY.colTop.providedArea + dirY.colBot.providedArea + dirY.midTop.providedArea + dirY.midBot.providedArea
        // mm2 x mm = mm3; 1e9 mm3 per m3
        val steelWeight = (asX * input.lx + asY * input.ly) / 1e9 * STEEL_UNIT_WEIGHT
        codeNotes.add(String.format("Concrete volume = %.3f m3/panel; steel (upper bound) = %.1f kg/panel", concreteVolume, steelWeight))

        // ── 13. Flexure checks, both directions ───────────────────
        val flexGroups = listOf(
            "Col-strip top (X)" to colTopRebarX, "Col-strip bottom (X)" to colBotRebarX,
            "Mid-strip top (X)" to midTopRebarX, "Mid-strip bottom (X)" to midBotRebarX,
            "Col-strip top (Y)" to dirY.colTop, "Col-strip bottom (Y)" to dirY.colBot,
            "Mid-strip top (Y)" to dirY.midTop, "Mid-strip bottom (Y)" to dirY.midBot
        )
        var flexUtil = 0.0
        flexGroups.forEach { (label, r) ->
            val u = if (r.providedArea > 0) r.requiredArea / r.providedArea else 0.0
            flexUtil = maxOf(flexUtil, u)
            safetyChecks.add(SafetyCheckItem(label, r.requiredArea, r.providedArea, "mm2", r.providedArea >= r.requiredArea))
        }
        val flexureOk = flexGroups.all { it.second.providedArea >= it.second.requiredArea } && overReinf.isEmpty()
        if (!flexureOk) warnings.add("Flexure not satisfied in one or more strips (reinforcement insufficient or K > Kmax: increase thickness)")

        val maxUtil = maxOf(punchingResult.utilizationRatio, flexUtil, if (minThk > 0) minThk / input.slabThickness else 0.0)
        val overallSafe = punchingResult.isSafe && headIssues.isEmpty() && dropEdgeOk && deflOk && minThkOk && flexureOk && applicable

        val punchingReinforcement = if (punchingResult.needsReinforcement && punchingResult.studDia > 0) {
            val studArea = PI * punchingResult.studDia * punchingResult.studDia / 4
            val studsPerRow = max(4, ceil(punchingResult.bo / punchingResult.studSpacing).toInt())
            val totalStuds = studsPerRow * punchingResult.studRows
            val totalAsStuds = studArea * totalStuds
            RebarResult(
                bars = totalStuds, diameter = punchingResult.studDia.toInt(),
                spacing = punchingResult.studSpacing.toInt(),
                providedArea = totalAsStuds, requiredArea = totalAsStuds * 0.9,
                ratio = 1.0 / punchingResult.utilizationRatio
            )
        } else null

        return FlatSlabResult(
            isSafe = overallSafe,
            utilizationRatio = maxUtil,
            totalDeadLoad = totalDeadLoad,
            totalFactoredLoad = wu,
            panelMomentX = m0X,
            panelMomentY = m0Y,
            columnStripMomentPos = colPosX,
            columnStripMomentNeg = maxOf(colNegExtX, colNegIntX),
            middleStripMomentPos = midPosX,
            middleStripMomentNeg = maxOf(midNegExtX, midNegIntX),
            columnStripWidthX = colStripWidthX,
            columnStripWidthY = colStripWidthY,
            columnStripTopRebar = colTopRebarX,
            columnStripBotRebar = colBotRebarX,
            middleStripTopRebar = midTopRebarX,
            middleStripBotRebar = midBotRebarX,
            punchingShearOk = punchingResult.isSafe,
            punchingShearVu = punchingResult.vu,
            punchingShearVc = punchingResult.vc,
            punchingPerimeter = punchingResult.bo,
            punchingReinforcement = punchingReinforcement,
            dropRequired = dropRequired,
            dropThickness = recommendedDropThickness,
            deflectionOk = deflOk,
            deflection = defl.total,
            allowableDeflection = defl.totalCheck.allowable,
            concreteVolumePerPanel = concreteVolume,
            steelWeightPerPanel = steelWeight,
            warnings = warnings,
            codeNotes = codeNotes,
            safetyChecks = safetyChecks,
            calculationSteps = flatSlabCalculationSteps(
                code = DesignCode.ECP,
                wu = wu, moX = m0X, moY = m0Y,
                columnStripMomentNeg = maxOf(colNegExtX, colNegIntX),
                columnStripMomentPos = colPosX,
                columnStripTopRebar = colTopRebarX,
                columnStripBotRebar = colBotRebarX,
                punchingVu = punchingResult.vu, punchingVc = punchingResult.vc,
                punchingOk = punchingResult.isSafe,
                deflection = defl.total,
                allowableDeflection = defl.totalCheck.allowable,
                deflectionOk = deflOk,
                punchingStuds = punchingResult.needsReinforcement && punchingResult.studDia > 0,
                deflectionAssessed = true
            ),
            columnStripTopRebarY = dirY.colTop,
            columnStripBotRebarY = dirY.colBot,
            middleStripTopRebarY = dirY.midTop,
            middleStripBotRebarY = dirY.midBot,
            deflectionAssessed = true
        )
    }

    /** Strip deflection estimate: see ECP2025FlatSlabExtras (not a code flat slab method). Units mm, N, N.mm. */
    private fun estimateDeflection(
        input: FlatSlabInput, dead: Double, wu: Double, dirX: DirResult, dirY: DirResult,
        colW: Double, midWX: Double, midWY: Double, dPos: Double, dNeg: Double
    ): ECP2025FlatSlabExtras.DeflectionEstimate {
        val ec = 4400.0 * sqrt(input.fcu)
        val n = 200000.0 / ec
        val t = input.slabThickness
        fun icr(b: Double, d: Double, asArea: Double): Double {
            if (asArea <= 0.0) return 1.0
            // x from b x^2 / 2 = n As (d - x)
            val a = b / 2.0
            val bq = n * asArea
            val x = (-bq + sqrt(bq * bq + 4.0 * a * bq * d)) / (2.0 * a)
            return max(1.0, b * x * x * x / 3.0 + n * asArea * (d - x) * (d - x))
        }
        // one strip, one load level; moments in kN.m (strip total), scale r = w / wu
        fun strip(span: Double, b: Double, w: Double, mA: Double, mMid: Double, mB: Double,
                  asMid: Double, asTop: Double, dMid: Double, dTop: Double): Double {
            val r = w / wu
            val ig = ECP2025FlatSlabExtras.grossInertia(b, t)
            val ie = ECP2025FlatSlabExtras.stripEffectiveInertia(
                input.fcu, input.fy, ig, t / 2.0,
                mMid * r * 1e6, mA * r * 1e6, mB * r * 1e6,
                icr(b, dMid, asMid), icr(b, dTop, asTop), icr(b, dTop, asTop))
            return ECP2025FlatSlabExtras.stripMidspanDeflection(w * b / 1000.0, span, mA * r * 1e6, mB * r * 1e6, ec, ie)
        }
        fun level(w: Double): Double {
            val cx = strip(input.lx, colW, w, dirX.cNE, dirX.cP, dirX.cNI, dirX.colBot.providedArea, dirX.colTop.providedArea, dPos, dNeg)
            val mx = strip(input.lx, midWX, w, dirX.mNE, dirX.mP, dirX.mNI, dirX.midBot.providedArea, dirX.midTop.providedArea, dPos, dPos)
            val cy = strip(input.ly, colW, w, dirY.cNE, dirY.cP, dirY.cNI, dirY.colBot.providedArea, dirY.colTop.providedArea, dPos - ASSUMED_BAR_DIA, dNeg - ASSUMED_BAR_DIA)
            val my = strip(input.ly, midWY, w, dirY.mNE, dirY.mP, dirY.mNI, dirY.midBot.providedArea, dirY.midTop.providedArea, dPos - ASSUMED_BAR_DIA, dPos - ASSUMED_BAR_DIA)
            return ECP2025FlatSlabExtras.panelMidpoint(cx, mx, cy, my).average
        }
        val dD = level(dead)
        val dDL = level(dead + input.liveLoad)
        return ECP2025FlatSlabExtras.estimate(dD, dD, dDL, max(input.lx, input.ly), 2.0, 0.0)
    }

    private data class DirResult(
        val cNE: Double, val cP: Double, val cNI: Double,
        val mNE: Double, val mP: Double, val mNI: Double,
        val colTop: RebarResult, val colBot: RebarResult,
        val midTop: RebarResult, val midBot: RebarResult
    )

    // ══════════════════════════════════════════════════════════════
    // STATIC MOMENT — DDM  Mo = wu × l2 × ln² / 8
    // ══════════════════════════════════════════════════════════════

    override fun calculateStaticMoment(wu: Double, ln: Double, l2: Double): Double {
        // ── InputGuard (ADR-010) — ECP 203 ──
        InputGuard.positive("ln", ln)
        InputGuard.positive("l2", l2)
        return wu * l2 * ln * ln / 8.0  // kN.m
    }

    // ══════════════════════════════════════════════════════════════
    // MOMENT SHARES (interface view of Table 6-5)
    // ══════════════════════════════════════════════════════════════

    /** Column-strip share of each total negative/positive moment, from Table 6-5 (p.6-25). */
    override fun getMomentCoefficients(
        panelType: PanelType,
        hasBeams: Boolean
    ): FlatSlabDesign.MomentCoefficients {
        val t = table65For(panelType)
        fun sh(c: Double, m: Double) = if (c + m > 0) c / (c + m) else 1.0
        return MomentCoefficients(
            colNegExterior = sh(t.colNegExt, t.midNegExt),
            colPositive = sh(t.colPos, t.midPos),
            colNegInterior = sh(t.colNegInt, t.midNegInt),
            midNegExterior = 1.0 - sh(t.colNegExt, t.midNegExt),
            midPositive = 1.0 - sh(t.colPos, t.midPos),
            midNegInterior = 1.0 - sh(t.colNegInt, t.midNegInt)
        )
    }

    /** Column strip = Lmin/4 each side of the column axis, width Lmin/2 (Fig 6-7, p.6-24). Pass the shorter span. */
    override fun getColumnStripWidth(
        l2: Double, columnSize: Double, panelType: PanelType
    ): Double = l2 / 2.0

    // ══════════════════════════════════════════════════════════════
    // PUNCHING SHEAR: ECP 203-2025 4-2-2-3 (p.4-25 to 4-29)
    // ══════════════════════════════════════════════════════════════

    /** Interface entry: interior column, vu already includes any moment-transfer factor. */
    override fun checkPunchingShear(
        vu: Double, fcu: Double, fy: Double,
        slabThickness: Double, dropThickness: Double,
        columnWidth: Double, columnDepth: Double,
        cover: Double
    ): FlatSlabDesign.PunchingShearResult =
        punchingCheck(vu, fcu, fy, slabThickness, dropThickness, columnWidth, columnDepth, cover, PanelType.INTERIOR)

    fun punchingCheck(
        vu: Double, fcu: Double, fy: Double,
        slabThickness: Double, dropThickness: Double,
        columnWidth: Double, columnDepth: Double,
        cover: Double, panelType: PanelType
    ): FlatSlabDesign.PunchingShearResult {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("slabThickness", slabThickness)
        InputGuard.positive("columnWidth", columnWidth)
        InputGuard.positive("columnDepth", columnDepth)

        val h = slabThickness + max(dropThickness, 0.0)
        val d = h - cover - ASSUMED_BAR_DIA / 2.0
        val b1 = columnWidth + d
        val b2 = columnDepth + d
        // Critical section at d/2 (Fig 4-13). Edge/corner orientation is not an input: smaller perimeter (conservative).
        val bo = when (panelType) {
            PanelType.INTERIOR -> 2.0 * (b1 + b2)
            PanelType.EDGE -> min(2.0 * b1 + b2, b1 + 2.0 * b2)
            PanelType.CORNER -> b1 + b2
        }
        val alpha = when (panelType) { PanelType.INTERIOR -> 4.0; PanelType.EDGE -> 3.0; PanelType.CORNER -> 2.0 }
        val lambdaS = min(1.0, (1000.0 / d).pow(0.25))
        val rootF = sqrt(fcu / GAMMA_C)
        val aSide = min(columnWidth, columnDepth)
        val bSide = max(columnWidth, columnDepth)
        val qa = 0.8 * lambdaS * (alpha * d / bo + 0.2) * rootF          // Eq 4-46-a
        val qb = 0.316 * lambdaS * (0.5 + aSide / bSide) * rootF         // Eq 4-46-b
        val qc = 0.316 * lambdaS * rootF                                  // Eq 4-46-c
        val qcup = min(min(qa, min(qb, qc)), 1.70)                        // cap 1.70 N/mm2
        val vc = qcup * bo * d / 1000.0
        val qu = vu * 1000.0 / (bo * d)                                   // Eq 4-44 (vu includes beta)
        val util = if (vc > 0) vu / vc else 2.0
        if (qu <= qcup) {
            return PunchingShearResult(vu, vc, bo, d, true, util, false, 0.0, 0.0, 0, 0.0)
        }
        // Reinforced section, Eq 4-47. Needs h >= 250 mm.
        val qMax = 0.45 * rootF                                           // Eq 4-47-c
        val qConc = 0.12 * lambdaS * rootF
        if (h < 250.0 || qu > qMax) {
            return PunchingShearResult(vu, vc, bo, d, false, util, true, 0.0, 0.0, 0, 0.0)
        }
        val s = floor(d / 2.0 / 5.0) * 5.0                                // s <= d/2 (Fig 4-14)
        val fyS = min(fy, STUD_FY_CAP)
        val qsupReq = max(qu - qConc, 0.0)
        val nPerLine = ceil(bo / s).toInt()
        var dia = 0.0
        var qsupProv = 0.0
        for (cand in listOf(8.0, 10.0, 12.0, 14.0, 16.0)) {
            val ast = nPerLine * PI * cand * cand / 4.0
            val qs = ast * fyS / (s * bo * GAMMA_S)                        // Eq 4-47-b
            if (qs >= qsupReq) { dia = cand; qsupProv = qs; break }
        }
        if (dia == 0.0) return PunchingShearResult(vu, vc, bo, d, false, util, true, 0.0, 0.0, 0, 0.0)
        // Reinforced zone must reach the perimeter where concrete alone resists: bo_out = vu/(qcup d)
        val boOut = vu * 1000.0 / (qcup * d)
        val growth = when (panelType) { PanelType.INTERIOR -> 8.0; PanelType.EDGE -> 4.0; PanelType.CORNER -> 2.0 }
        val reach = max(0.0, (boOut - bo) / growth)
        val lines = max(2, ceil(reach / s).toInt())
        val cap = min(qConc + qsupProv, qMax) * bo * d / 1000.0
        return PunchingShearResult(vu, vc, bo, d, true, util, true, dia, s, lines, cap)
    }

    // ══════════════════════════════════════════════════════════════
    // FLEXURE: K-method, ECP 203-2025 Ch.4 (Table 4-1 via ECPBeam.kMax)
    // ══════════════════════════════════════════════════════════════

    override fun designReinforcement(
        moment: Double, fcu: Double, fy: Double,
        effectiveDepth: Double, stripWidth: Double,
        cover: Double
    ): FlatSlabDesign.ReinforcementDesign =
        designReinforcementH(moment, fcu, fy, effectiveDepth, stripWidth, effectiveDepth + cover + ASSUMED_BAR_DIA / 2.0)

    /** Min. steel ratio on gross area b x h: 6-2-1-2-3 (p.6-6): max(0.6/fy, 0.25 % mild | 0.15-0.18 % high grade). */
    fun minSteelRatio(fcu: Double, fy: Double): Double = max(0.6 / fy, ECPBeam.muMinFloor(fcu, fy))

    fun designReinforcementH(
        moment: Double, fcu: Double, fy: Double,
        effectiveDepth: Double, stripWidth: Double, totalDepth: Double
    ): FlatSlabDesign.ReinforcementDesign {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("effectiveDepth", effectiveDepth)
        InputGuard.positive("stripWidth", stripWidth)
        val b = stripWidth
        val minAs = minSteelRatio(fcu, fy) * b * totalDepth
        val fs = fy / GAMMA_S
        val mu = max(moment, 0.0) * 1e6

        fun asFor(d: Double): Double {
            if (mu <= 0.0) return 0.0
            val k = mu / (fcu * b * d * d)
            val disc = 0.25 - k / 0.893
            val z = min(if (disc > 0) d * (0.5 + sqrt(disc)) else 0.7 * d, 0.95 * d)
            return mu / (fs * z)
        }
        // Pick the smallest bar with spacing >= 100 mm (selection practice, not a code value); d reduced for the bar actually chosen.
        var chosen = 8.0; var asReq = 0.0; var asDes = 0.0; var spacing = getMaxBarSpacing(); var isMin = false
        for (dia in listOf(8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0)) {
            val d = effectiveDepth + ASSUMED_BAR_DIA / 2.0 - dia / 2.0
            val req = asFor(d)
            val des = max(req, minAs)
            val area = PI * dia * dia / 4.0
            val s = min(area * b / des, getMaxBarSpacing())
            chosen = dia; asReq = req; asDes = des; spacing = floor(s / 5.0) * 5.0; isMin = req < minAs
            if (s >= 100.0) break
        }
        spacing = spacing.coerceAtLeast(50.0)
        val area = PI * chosen * chosen / 4.0
        val provided = area * b / spacing
        return ReinforcementDesign(
            asRequired = asReq.coerceAtLeast(if (isMin) minAs else asReq),
            asProvided = provided, barDia = chosen, barSpacing = spacing,
            barsCount = max(1, floor(b / spacing).toInt()), isMinSteel = isMin
        )
    }

    private fun designRebarForResult(
        moment: Double, fcu: Double, fy: Double,
        effectiveDepth: Double, stripWidth: Double, totalDepth: Double,
        cover: Double,
        warnings: MutableList<String>,
        codeNotes: MutableList<String>,
        overReinf: MutableList<String>,
        label: String
    ): RebarResult {
        val kMaxVal = ECPBeam.kMax(fy)
        val k = moment * 1e6 / (fcu * stripWidth * effectiveDepth * effectiveDepth)
        if (k > kMaxVal) {
            overReinf.add(label)
            warnings.add(String.format("%s: K=%.3f > Kmax=%.3f (Table 4-1, p.4-6): compression steel needed, increase thickness", label, k, kMaxVal))
        }
        val design = designReinforcementH(moment, fcu, fy, effectiveDepth, stripWidth, totalDepth)
        if (design.isMinSteel) {
            codeNotes.add(String.format("%s: min. steel governs (As,min=%.0f mm2), phi%d@%dmm", label, design.asProvided, design.barDia.toInt(), design.barSpacing.toInt()))
        } else {
            codeNotes.add(String.format("%s: As_req=%.0f, As_prov=%.0f mm2, phi%d@%dmm", label, design.asRequired, design.asProvided, design.barDia.toInt(), design.barSpacing.toInt()))
        }
        val req = max(design.asRequired, 0.0)
        return RebarResult(
            bars = design.barsCount, diameter = design.barDia.toInt(), spacing = design.barSpacing.toInt(),
            providedArea = design.asProvided, requiredArea = req,
            ratio = if (req > 0) design.asProvided / req else 1.0
        )
    }

    // ══════════════════════════════════════════════════════════════
    // DEFLECTION: NOT ASSESSED
    // ══════════════════════════════════════════════════════════════

    /** NOT ASSESSED: ECP 203-2025 4-3-1 was not read. Returns isSafe=false with zero values so no false pass is shown. */
    override fun checkDeflection(
        span: Double, slabThickness: Double,
        fcu: Double, fy: Double,
        serviceMoment: Double, effectiveDepth: Double,
        providedAs: Double
    ): FlatSlabDesign.DeflectionResult = DeflectionResult(
        immediate = 0.0, longTerm = 0.0, allowable = 0.0, isSafe = false, ratio = 0.0
    )

    // ══════════════════════════════════════════════════════════════
    // MINIMUM THICKNESS: Table 6-3 (p.6-18)
    // ══════════════════════════════════════════════════════════════

    override fun getMinimumThickness(
        clearSpan: Double, transverseSpan: Double,
        hasDropPanel: Boolean, fcu: Double, fy: Double
    ): Double = getMinimumThickness(clearSpan, transverseSpan, hasDropPanel, fcu, fy, true)

    /** Longer clear span governs; exterior panel uses the "no edge beam" column (conservative). */
    fun getMinimumThickness(
        clearSpan: Double, transverseSpan: Double,
        hasDropPanel: Boolean, fcu: Double, fy: Double, exterior: Boolean
    ): Double = max(clearSpan, transverseSpan) / table63Divisor(fy, exterior, hasDropPanel)

    // ══════════════════════════════════════════════════════════════
    // CODE LIMITS
    // ══════════════════════════════════════════════════════════════

    override fun getMinCover(): Double = 25.0

    /** ECP 203-2025 6-2-1-3-4-a (p.6-9): main bar spacing <= 200 mm in moment zones. */
    override fun getMaxBarSpacing(): Double = 200.0

    override fun getMinReinforcementRatio(fy: Double): Double = max(0.6 / fy, 0.0015)

    override fun getCodeName(): String = "ECP 203-2025"

    override fun getFactoredLoad(deadLoad: Double, liveLoad: Double): Double {
        // ECP 203 §2-3: Wu = 1.4 × DL + 1.6 × LL
        return 1.4 * deadLoad + 1.6 * liveLoad
    }
}
