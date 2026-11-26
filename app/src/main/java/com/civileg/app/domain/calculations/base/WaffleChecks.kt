package com.civileg.app.domain.calculations.base

import com.civileg.app.domain.RebarResult
import com.civileg.app.domain.entities.ShearReinforcementResult
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Verdict of one check. PASS means the computed check passed with every constant verified against the
 * supplied reference text. PRELIMINARY means it passed but some constant or applicability is not verified.
 * FAIL means the computed demand exceeds capacity (a failure always outranks incompleteness).
 * NOT_ASSESSED means no supported method or no real demand: nothing was checked.
 */
enum class DesignCheckStatus(val label: String) {
    PASS("PASS"),
    PRELIMINARY("PRELIMINARY"),
    FAIL("FAIL"),
    NOT_ASSESSED("NOT ASSESSED");

    /** True only when a computed check did not fail. NOT_ASSESSED is never a pass. */
    val computedOk: Boolean get() = this == PASS || this == PRELIMINARY
}

/**
 * Shared, unit-explicit waffle-slab demand and capacity helpers. Code classes pass their own constants.
 * Assumptions (stated in every result note): single simply supported rib in the Lx direction, flange ignored
 * (conservative), full panel load on the rib tributary width, interior column with concentric load.
 */
object WaffleChecks {
    const val BAR_DIA_MM = 16
    /** Stirrup + half-bar allowance used by the existing code for effective depth: d = h - cover - 10. */
    const val D_ALLOWANCE_MM = 10.0
    const val MIN_CLEAR_SPACING_MM = 25.0 // ACI 318M 25.2.1 (supplied text): at least 25 mm, db, 4/3 dagg
    private const val BAR_AREA = PI * BAR_DIA_MM * BAR_DIA_MM / 4.0

    /** kN/m per rib from a factored panel load in kN/m2 and the rib spacing in mm. */
    fun ribLineLoad(wuPanel: Double, ribSpacingMm: Double): Double = wuPanel * ribSpacingMm / 1000.0

    /** Mid-span moment in kN.m of a simply supported rib. */
    fun ribMoment(wuPanel: Double, ribSpacingMm: Double, spanMm: Double): Double {
        val w = ribLineLoad(wuPanel, ribSpacingMm)
        val l = spanMm / 1000.0
        return w * l * l / 8.0
    }

    /** Support shear in kN of a simply supported rib. */
    fun ribShear(wuPanel: Double, ribSpacingMm: Double, spanMm: Double): Double =
        ribLineLoad(wuPanel, ribSpacingMm) * (spanMm / 1000.0) / 2.0

    /** Rectangular stress-block equilibrium: returns (As mm2, a mm) or null when the section cannot resist Mu. */
    fun flexureEquilibrium(muKNm: Double, fc: Double, fy: Double, b: Double, d: Double, phi: Double): Pair<Double, Double>? {
        val k = 2.0 * muKNm * 1e6 / (0.85 * fc * phi * b)
        val radicand = d * d - k
        if (radicand < 0.0) return null
        val a = d - sqrt(radicand)
        return Pair(0.85 * fc * a * b / fy, a)
    }

    /** Factored panel punching demand in kN at an interior column: wu x (panel area - loaded area inside d/2). */
    fun punchingDemand(wuPanel: Double, lxMm: Double, lyMm: Double, columnMm: Double, totalDepthMm: Double, coverMm: Double): Double {
        val d = totalDepthMm - coverMm - D_ALLOWANCE_MM
        if (d <= 0.0) return 0.0
        val inside = (columnMm + d) / 1000.0
        return wuPanel * (lxMm * lyMm / 1e6 - inside * inside)
    }

    @Suppress("LongParameterList")
    fun ribDesign(
        fc: Double, fy: Double, bw: Double, h: Double, ribSpacing: Double, spanMm: Double,
        wuPanel: Double, cover: Double, phiFlex: Double, phiShear: Double,
        vcCoef: Double, vsMaxCoef: Double?, beta1: Double?,
        minAs: (asReq: Double, bw: Double, d: Double) -> Double,
        passStatus: DesignCheckStatus, codeRef: String
    ): WaffleSlabDesign.RibDesignResult {
        val d = h - cover - D_ALLOWANCE_MM
        val warnings = mutableListOf<String>()
        val notes = mutableListOf<String>()
        val mu = ribMoment(wuPanel, ribSpacing, spanMm)
        val vu = ribShear(wuPanel, ribSpacing, spanMm)
        notes.add("$codeRef: simply supported rib, flange ignored, w = Wu x s = ${"%.2f".format(ribLineLoad(wuPanel, ribSpacing))} kN/m, Mu = ${"%.2f".format(mu)} kN.m, Vu = ${"%.2f".format(vu)} kN")
        notes.add("Dead load input is taken to include slab self-weight (not verified).")

        var flexFail = false
        var asReq = 0.0
        val eq = if (d > 0.0) flexureEquilibrium(mu, fc, fy, bw, d, phiFlex) else null
        if (eq == null) {
            flexFail = true
            warnings.add("FAIL: rib section ${"%.0f".format(bw)} x ${"%.0f".format(h)} mm cannot resist Mu = ${"%.2f".format(mu)} kN.m")
        } else {
            asReq = eq.first
            if (beta1 != null) {
                val cOverD = (eq.second / beta1) / d
                // eps_t >= 0.005 with eps_cu = 0.003 gives c/d <= 0.003 / (0.003 + 0.005) = 0.375
                if (cOverD > 0.375) {
                    flexFail = true
                    warnings.add("FAIL: net tensile strain below 0.005 (c/d = ${"%.3f".format(cOverD)} > 0.375); enlarge the rib. Reduced-phi design is not implemented.")
                }
            }
        }

        val asNeeded = max(asReq, minAs(asReq, bw, d.coerceAtLeast(0.0)))
        val n = max(1, ceil(asNeeded / BAR_AREA).toInt())
        val asProv = n * BAR_AREA
        val edge = cover + D_ALLOWANCE_MM
        val clear = if (n > 1) (bw - 2.0 * edge - n * BAR_DIA_MM) / (n - 1) else bw - 2.0 * edge - BAR_DIA_MM
        if (clear < MIN_CLEAR_SPACING_MM) {
            warnings.add("$n phi$BAR_DIA_MM do not fit one layer in the rib (clear ${"%.0f".format(clear)} mm < 25 mm, ACI 318M 25.2.1). Layers are not designed.")
        }
        val spacing = if (n > 1) ((bw - 2.0 * edge - BAR_DIA_MM) / (n - 1)).toInt() else 0
        val ap = asProv * fy / (0.85 * fc * bw)
        val phiMn = phiFlex * asProv * fy * (d - ap / 2.0) / 1e6
        val flexUtil = if (phiMn > 0.0) mu / phiMn else 2.0

        val vc = vcCoef * sqrt(fc) * bw * (if (d > 0.0) d else 0.0) / 1000.0
        val phiVc = phiShear * vc
        var reqAvS = 0.0
        var shearFail = false
        if (vu > phiVc) {
            val vs = vu / phiShear - vc
            reqAvS = if (d > 0.0) vs * 1000.0 / (fy * d) else 0.0
            warnings.add("Stirrups required: Av/s = ${"%.3f".format(reqAvS)} mm2/mm. Stirrups are not detailed or quantified.")
            if (vsMaxCoef != null && vs > vsMaxCoef * sqrt(fc) * bw * d / 1000.0) {
                shearFail = true
                warnings.add("FAIL: Vs exceeds the cross-section limit; enlarge the rib.")
            }
        }
        val shearUtil = if (phiVc > 0.0) vu / phiVc else 2.0

        val status = if (flexFail || shearFail) DesignCheckStatus.FAIL else passStatus
        return WaffleSlabDesign.RibDesignResult(
            flexureReinforcement = RebarResult(
                bars = n, diameter = BAR_DIA_MM, spacing = spacing,
                providedArea = asProv, requiredArea = asReq, ratio = if (bw * d > 0) asProv / (bw * d) else 0.0
            ),
            shearReinforcement = ShearReinforcementResult(
                concreteShearCapacity = vc,
                requiredShearReinforcement = reqAvS,
                providedShearReinforcement = 0.0,
                isSafe = !shearFail,
                utilizationRatio = shearUtil,
                warnings = warnings.filter { it.contains("tirrup") || it.contains("Vs") },
                codeNotes = listOf("phiVc = ${"%.1f".format(phiVc)} kN, Vu = ${"%.1f".format(vu)} kN")
            ),
            isSafe = status.computedOk,
            utilizationRatio = max(flexUtil, shearUtil),
            status = status,
            warnings = warnings,
            notes = notes
        )
    }

    /** Concentric punching check at the column critical section d/2 from the column face (interior, square column). */
    fun punching(
        columnMm: Double, totalDepthMm: Double, cover: Double, vuKN: Double, phiShear: Double,
        nominalVc: (bo: Double, d: Double, beta: Double) -> Double, passStatus: DesignCheckStatus
    ): WaffleSlabDesign.PunchingShearCheck {
        val d = totalDepthMm - cover - D_ALLOWANCE_MM
        if (d <= 0.0 || vuKN <= 0.0) {
            return WaffleSlabDesign.PunchingShearCheck(
                vu = max(vuKN, 0.0), vc = 0.0, isSafe = false, utilizationRatio = 0.0,
                status = DesignCheckStatus.NOT_ASSESSED,
                note = "No real punching demand or slab depth supplied; nothing was checked."
            )
        }
        val bo = 4.0 * (columnMm + d)
        val vc = phiShear * nominalVc(bo, d, 1.0)
        val failed = vuKN > vc
        val status = if (failed) DesignCheckStatus.FAIL else passStatus
        return WaffleSlabDesign.PunchingShearCheck(
            vu = vuKN, vc = vc, isSafe = status.computedOk, utilizationRatio = if (vc > 0) vuKN / vc else 2.0,
            status = status,
            note = "Concentric shear at d/2 from a square interior column, d = ${"%.0f".format(d)} mm, bo = ${"%.0f".format(bo)} mm. Moment transfer, edge/corner columns and the solid-head edge section are not checked."
        )
    }

    fun solidHeadNotAssessed(): WaffleSlabDesign.SolidHeadDesignResult = WaffleSlabDesign.SolidHeadDesignResult(
        flexureReinforcement = RebarResult(bars = 0, diameter = 0, spacing = 0, providedArea = 0.0, requiredArea = 0.0, ratio = 0.0),
        punchingShear = WaffleSlabDesign.PunchingShearCheck(
            vu = 0.0, vc = 0.0, isSafe = false, utilizationRatio = 0.0,
            status = DesignCheckStatus.NOT_ASSESSED, note = "Solid-head design is not implemented."
        ),
        isSafe = false,
        status = DesignCheckStatus.NOT_ASSESSED,
        note = "Solid-head flexure and reinforcement are not designed. No bars are adopted."
    )

    fun deflectionNotAssessed(): FlatSlabDesign.DeflectionResult = FlatSlabDesign.DeflectionResult(
        immediate = 0.0, longTerm = 0.0, allowable = 0.0, isSafe = false, ratio = 0.0
    )
}
