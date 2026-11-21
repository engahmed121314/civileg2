package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.CodeReference
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult
import com.civileg.app.domain.entities.ShearCheckResult
import kotlin.math.*

class ACIFooting : FootingDesign {
    
    companion object {
        private const val PHI_SHEAR = 0.75
        private const val PHI_BENDING = 0.90
        private const val MIN_THICKNESS = 300.0  // mm
        private const val GAMMA_CONCRETE = 25.0  // kN/m³
    }

    override fun designIsolatedFooting(
        fcu: Double,
        fy: Double,
        columnWidth: Double,
        columnDepth: Double,
        axialLoad: Double,
        momentX: Double,
        momentY: Double,
        soilBearingCapacity: Double,
        footingDepth: Double,
        loadCombination: LoadCombination,
        constraints: BoundaryConstraints
    ): FootingDesignResult {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("columnWidth", columnWidth)
        InputGuard.positive("columnDepth", columnDepth)
        InputGuard.positive("soilBearingCapacity", soilBearingCapacity)
        InputGuard.positive("footingDepth", footingDepth)
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // 1. فحص السمك الأدنى
        if (footingDepth < MIN_THICKNESS) {
            warnings.add("ACI 318: Minimum footing thickness is 300mm")
        }

        // 2. تحويل الأحمال للخدمة (لحساب أبعاد القاعدة)
        val factor = loadCombination.getFactorForCode(DesignCode.ACI)
        val P_service = axialLoad / factor
        val Mx_service = momentX / factor
        val My_service = momentY / factor

        // 3. حساب المساحة المطلوبة
        val t_m = footingDepth / 1000.0
        val netSBC = soilBearingCapacity - GAMMA_CONCRETE * t_m
        val requiredArea = P_service / netSBC.coerceAtLeast(soilBearingCapacity * 0.8)

        // 4. نسبة الأبعاد (ACI 15.2)
        val ratio = sqrt(columnDepth / columnWidth)
        var B_m = sqrt(requiredArea / ratio)
        var L_m = ratio * B_m

        // 5. تطبيق حدود الجار
        if (constraints.isCornerColumn) {
            val maxDim = min(
                constraints.maxLeft ?: (columnWidth * 3),
                constraints.maxTop ?: (columnDepth * 3)
            )
            B_m = min(B_m, maxDim / 1000.0)
            L_m = min(L_m, maxDim / 1000.0)
        } else if (constraints.isEdgeColumn) {
            constraints.maxRight?.let { maxProj ->
                L_m = min(L_m, (columnWidth + 2 * maxProj) / 1000.0)
            }
        }

        // 6. تقريب الأبعاد لأقرب 50 مم
        val B = ceil(B_m * 1000 / 50.0) * 50.0
        val L = ceil(L_m * 1000 / 50.0) * 50.0
        val A_actual = B * L / 1e6

        // 7. ضغط التربة مع الانحراف (ACI 318-13.3.1)
        val q_avg = P_service / A_actual
        val ex = Mx_service / P_service
        val ey = My_service / P_service
        val q_max_x = q_avg * (1.0 + 6.0 * ex / (B / 1000.0))
        val q_max_y = q_avg * (1.0 + 6.0 * ey / (L / 1000.0))
        val q_max = max(q_max_x, q_max_y)
        val q_min = min(
            q_avg * (1.0 - 6.0 * ex / (B / 1000.0)),
            q_avg * (1.0 - 6.0 * ey / (L / 1000.0))
        )

        if (q_max > soilBearingCapacity) {
            warnings.add(String.format("ACI: q_max=%.1f kPa > SBC=%.1f kPa", q_max, soilBearingCapacity))
        }
        if (q_min < 0) {
            warnings.add("ACI: Footing separation - increase dimensions")
        }

        // Factored soil pressure for flexure and one-way shear design (ultimate level)
        val q_factored = q_avg * factor

        // 8. السمك الفعال
        val d = footingDepth - getMinCover() - 10.0

        // 9. عزم الانحناء في الاتجاهين (الناتئ من وجه العمود) — factored pressure
        val cantX = (B - columnWidth) / 2.0 / 1000.0  // m
        val cantY = (L - columnDepth) / 2.0 / 1000.0  // m
        val Mu_x = q_factored * (L / 1000.0) * cantX * cantX / 2.0  // kN.m
        val Mu_y = q_factored * (B / 1000.0) * cantY * cantY / 2.0  // kN.m

        // 10. فحص القص الأحادي عند بعد d من وجه العمود — ACI 318M-14 §13.2.7.1 (printed p.307)
        // F-03 (owner-approved 2026-11-21): critical section at d, not d/2 — factored pressure
        val Vu_x = q_factored * (L / 1000.0) * max(cantX - d / 1000.0, 0.0)  // kN
        val Vu_y = q_factored * (B / 1000.0) * max(cantY - d / 1000.0, 0.0)  // kN
        // One-way shear capacity: Vc = φ × 0.17√f'c × b × d (ACI 318-22.5.5.1)
        val fc_prime = 0.8 * fcu
        val vc_oneWay = 0.17 * sqrt(fc_prime)  // MPa
        val Vc_x = PHI_SHEAR * vc_oneWay * (L / 1000.0) * d / 1000.0 * 1000.0  // kN
        val Vc_y = PHI_SHEAR * vc_oneWay * (B / 1000.0) * d / 1000.0 * 1000.0  // kN

        if (Vu_x > Vc_x) {
            warnings.add("ACI: One-way shear X exceeds capacity - increase thickness")
        }
        if (Vu_y > Vc_y) {
            warnings.add("ACI: One-way shear Y exceeds capacity - increase thickness")
        }

        codeNotes.add(String.format("One-way shear capacity: %.2f MPa", vc_oneWay))

        // 11. فحص قص الاختراق
        val punchingCheck = checkPunchingShear(fcu, columnWidth, columnDepth, d, axialLoad, loadCombination)

        // 12. تصميم التسليح في الاتجاهين
        val reinfX = flexurePerMeter(fcu, fy, footingDepth, d, Mu_x / (L / 1000.0), FootingDirection.SHORT)
        val reinfY = flexurePerMeter(fcu, fy, footingDepth, d, Mu_y / (B / 1000.0), FootingDirection.LONG)

        // 13. التسليح الرئيسي = الأكبر
        val mainReinf = if (reinfX.astRequired >= reinfY.astRequired) reinfX else reinfY

        // 14. توزيع التسليح — ACI 318M-14 §13.3.3.3 (printed pp.310-311)
        // F-04 (owner-approved 2026-11-21): the old "20% of main steel / 13.3.4" rule
        // cited a clause that does not contain it. Real rule:
        // (a) تسليح الاتجاه الطويل: يوزع بالتساوي على كامل العرض (reinfY كما هو)
        // (b) تسليح الاتجاه القصير: γs = 2/(β+1) من إجمالي التسليح يتركز في شريحة
        //     وسطية عرضها = طول الضلع القصير، والباقي (1−γs) يوزع بالتساوي خارجها
        val longSide = max(B, L)
        val shortSide = min(B, L)
        val betaFooting = longSide / shortSide
        val gammaS = 2.0 / (betaFooting + 1.0)
        // reinfX = تسليح الاتجاه القصير لكل متر، موزع على طول الضلع الطويل
        val asShortTotal = reinfX.astRequired * (longSide / 1000.0)
        val asBandPerM = gammaS * asShortTotal / (shortSide / 1000.0)
        val outsideWidth = longSide - shortSide
        val asOutsidePerM = if (outsideWidth > 0) {
            (1.0 - gammaS) * asShortTotal / (outsideWidth / 1000.0)
        } else {
            0.0
        }
        val bandBarDia = listOf(12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0).firstOrNull { dia ->
            val area = PI * dia * dia / 4.0
            ceil(asBandPerM / area).toInt() in 5..20
        } ?: 16.0
        val bandBarArea = PI * bandBarDia * bandBarDia / 4.0
        val bandBarsPerMeter = if (asBandPerM > 0) ceil(asBandPerM / bandBarArea).toInt() else 0
        val maxDistSpacing = min(450.0, min(3.0 * d, 750.0))
        val bandSpacing = if (bandBarsPerMeter > 0) {
            floor(1000.0 / bandBarsPerMeter).coerceIn(100.0, maxDistSpacing)
        } else {
            maxDistSpacing
        }

        codeNotes.add("ACI 318-25: Isolated Footing Design")
        codeNotes.add(CodeReference.ACI.FOOTING_BEARING)
        codeNotes.add(CodeReference.ACI.FOOTING_SHEAR_PUNCHING)
        codeNotes.add(CodeReference.ACI.FOOTING_REINFORCEMENT)
        codeNotes.add(String.format("B=%.0fxL=%.0f mm, d=%.0f mm", B, L, d))
        codeNotes.add(String.format("q_avg=%.1f, q_max=%.1f, q_min=%.1f kPa", q_avg, q_max, q_min))
        codeNotes.add(String.format("Mu_x=%.1f, Mu_y=%.1f kN.m", Mu_x, Mu_y))
        codeNotes.add(String.format("Short dir: %s", reinfX.barString))
        codeNotes.add(String.format("Long dir: %s", reinfY.barString))
        codeNotes.add(String.format(
            "Band rule 13.3.3.3(b): beta=%.2f, gamma_s=%.3f, band width=%.0f mm",
            betaFooting, gammaS, shortSide
        ))
        if (bandBarsPerMeter > 0) {
            codeNotes.add(String.format(
                "Short-dir band: %dØ%d @ %dmm; outside band: %.0f mm2/m",
                bandBarsPerMeter, bandBarDia.toInt(), bandSpacing.toInt(), asOutsidePerM
            ))
        }

        return FootingDesignResult(
            requiredWidth = B,
            requiredLength = L,
            requiredThickness = footingDepth,
            soilPressure = q_avg,
            maxSoilPressure = q_max,
            reinforcement = mainReinf,
            punchingShearCheck = punchingCheck,
            isSafe = q_max <= soilBearingCapacity
                && q_min >= 0
                && Vu_x <= Vc_x && Vu_y <= Vc_y
                && punchingCheck.isSafe
                && reinfX.isSafe
                && reinfY.isSafe,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    override fun checkPunchingShear(
        fcu: Double,
        columnWidth: Double,
        columnDepth: Double,
        effectiveDepth: Double,
        punchingShearForce: Double,
        loadCombination: LoadCombination
    ): ShearCheckResult {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("columnWidth", columnWidth)
        InputGuard.positive("columnDepth", columnDepth)
        InputGuard.positive("effectiveDepth", effectiveDepth)
        // ACI 318-22: فحص قص الاختراق
        // b0 = 2(c1 + c2) + 4d (محيط الاختراق)
        val b0 = 2.0 * (columnWidth + columnDepth) + 4.0 * effectiveDepth
        // ACI يستخدم f'c (اسطوانة) = 0.8 × fcu (مكعب)
        val fc_prime = 0.8 * fcu
        // ACI 22.6.5.2(b): vc = min(vc1, vc2, vc3) where vc3 = 0.083*(2+αs*d/bo)*λ*√f'c
        // αs = 40 for interior, 30 for edge, 20 for corner columns
        val alphaS = 40.0  // default interior column
        val beta = max(columnDepth, columnWidth) / min(columnDepth, columnWidth).coerceAtLeast(1.0)
        val vn = minOf(
            0.33 * sqrt(fc_prime),
            0.17 * (1.0 + 2.0 / beta) * sqrt(fc_prime),
            0.083 * (2.0 + alphaS * effectiveDepth / b0) * sqrt(fc_prime)
        )
        val capacity = PHI_SHEAR * vn * b0 * effectiveDepth / 1000.0
        // Note: ACI 318 applies φ=0.75 directly on capacity side (vc), so no 0.90 reduction on demand side is needed. SBC/ECP apply 0.90 on Vu side as an approximation for soil reaction within the critical perimeter.
        val isSafe = punchingShearForce <= capacity
        
        return ShearCheckResult(
            appliedShear = punchingShearForce,
            shearCapacity = capacity,
            isSafe = isSafe,
            utilizationRatio = if (capacity > 0) punchingShearForce / capacity else 2.0,
            criticalPerimeter = b0
        )
    }

    override fun calculateFootingReinforcement(
        fcu: Double,
        fy: Double,
        footingWidth: Double,
        footingLength: Double,
        effectiveDepth: Double,
        designMoment: Double,
        direction: FootingDirection
    ): ReinforcementResult {
        // Interface path carries no gross depth; h = d preserves legacy behavior.
        // Internal design flows call flexurePerMeter with the real footing depth.
        return flexurePerMeter(fcu, fy, effectiveDepth, effectiveDepth, designMoment, direction)
    }

    /**
     * ACI 318M-14 flexure per meter width: Rn–ρ strength plus the minimum of
     * Table 7.6.1.1 on the GROSS area (As,min = 0.0020·b·h for fy < 420 MPa;
     * max(0.0018·420/fy, 0.0014)·b·h for fy ≥ 420). Table verified against the
     * owner's ACI 318M-14, printed p.143 — CLAUSE_LOG F-01.
     */
    internal fun flexurePerMeter(
        fcu: Double,
        fy: Double,
        footingDepth: Double,
        effectiveDepth: Double,
        designMoment: Double,
        direction: FootingDirection
    ): ReinforcementResult {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("footingDepth", footingDepth)
        InputGuard.positive("effectiveDepth", effectiveDepth)
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        
        // ACI 318-25 طريقة Rn-ρ (التعادل)
        val Mu = designMoment * 1e6  // N.mm/m
        val b = 1000.0  // mm/م
        val d = effectiveDepth
        
        // f'c = 0.8 × fcu (تحويل من مكعب لأسطوانة)
        val fc_prime = 0.8 * fcu
        
        // Rn = Mu / (φ × b × d²)
        val Rn = Mu / (PHI_BENDING * b * d * d)
        
        // ρ = (0.85 × f'c / fy) × [1 - √(1 - 2×Rn / (0.85×f'c))]
        val discriminant = 1.0 - 2.0 * Rn / (0.85 * fc_prime)
        val rho = if (discriminant > 0) {
            (0.85 * fc_prime / fy) * (1.0 - sqrt(discriminant))
        } else {
            warnings.add("ACI: Section compression failure - increase effective depth")
            0.025  // تصميم مضغوط - يحتاج زيادة العمق
        }
        
        // الحد الأدنى حسب ACI 318M-14 جدول 7.6.1.1 على المساحة الكلية b·h
        // (F-01: يستبدل max(0.0018, 1.33·√f'c/fy) التي لا تطابق أي بند في ACI)
        val rhoMin = if (fy < 420.0) 0.0020 else max(0.0018 * 420.0 / fy, 0.0014)
        val asMin = rhoMin * b * footingDepth
        val rhoMax = 0.025  // ACI 318 الحد الأقصى

        val rhoCapped = rho.coerceAtMost(rhoMax)
        val asRequired = max(rhoCapped * b * d, asMin)
        
        // اختيار القضبان
        val availableBars = listOf(12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0)
        val barDiameter = availableBars.firstOrNull { dia ->
            val area = PI * dia * dia / 4.0
            val barsPerMeter = ceil(asRequired / area).toInt()
            barsPerMeter in 5..20
        } ?: 16.0
        
        val barArea = PI * barDiameter * barDiameter / 4.0
        val nominalBars = ceil(asRequired / barArea).toInt()
        val nominalSpacing = floor(1000.0 / nominalBars)
        
        // ACI 318: max spacing = min(3h, 450mm)
        val maxSpacing = min(450.0, min(3.0 * d, 750.0))
        val finalSpacing = max(nominalSpacing, 100.0).coerceAtMost(maxSpacing)
        val actualBars = ceil(1000.0 / finalSpacing).toInt()
        val asProvided = actualBars * barArea
        
        val utilization = (rhoCapped * b * d) / asRequired.coerceAtLeast(1.0)
        codeNotes.add(String.format("ACI 318M-14: %dØ%d @ %dmm", actualBars, barDiameter.toInt(), finalSpacing.toInt()))
        codeNotes.add(String.format("ρ=%.4f, As,min=%.0f mm²/m (Table 7.6.1.1), Rn=%.2f", rhoCapped, asMin, Rn))
        
        return ReinforcementResult(
            astRequired = asRequired,
            astProvided = asProvided,
            barDiameter = barDiameter,
            numberOfBars = actualBars,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = discriminant > 0 && utilization <= 1.0,
            utilizationRatio = utilization,
            spacing = finalSpacing,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    override fun designCombinedFooting(
        fcu: Double,
        fy: Double,
        axialLoad1: Double,
        axialLoad2: Double,
        distanceBetweenColumns: Double,
        soilBearingCapacity: Double,
        footingDepth: Double,
        loadCombination: LoadCombination,
        columnWidth: Double,
        columnDepth: Double,
        col2Width: Double,   // col2 width passed from ViewModel
        col2Depth: Double    // col2 depth passed from ViewModel
    ): FootingDesignResult {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.atLeastOne("axialLoad1" to axialLoad1, "axialLoad2" to axialLoad2)
        InputGuard.positive("distanceBetweenColumns", distanceBetweenColumns)
        InputGuard.positive("soilBearingCapacity", soilBearingCapacity)
        InputGuard.positive("footingDepth", footingDepth)
        InputGuard.positive("columnWidth", columnWidth)
        InputGuard.positive("columnDepth", columnDepth)

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val code = DesignCode.ACI
        val factor = loadCombination.getFactorForCode(code)
        val p1Working = axialLoad1 / factor
        val p2Working = axialLoad2 / factor
        val totalWorkingLoad = p1Working + p2Working

        // ── Step 1: resultant of (working) column loads, from column 1 centerline ──
        val xResultant = if (totalWorkingLoad > 0) (p2Working * distanceBetweenColumns) / totalWorkingLoad else distanceBetweenColumns / 2.0

        // ── Step 2: footing length — centroid under the resultant AND covering
        // both columns (CF-05: the old L = 2(xR + s1) placed column 2 off the
        // footing whenever P1 > P2, i.e. xR < S/2) ──
        val s1 = (columnDepth / 2.0 + 150.0).coerceIn(300.0, 800.0)   // overhang past column 1
        val s2 = (col2Depth / 2.0 + 150.0).coerceIn(300.0, 800.0)     // overhang past column 2
        val footingLength = 2.0 * maxOf(xResultant + s1, (distanceBetweenColumns - xResultant) + s2)

        // ── Step 3: footing width from service bearing ──
        val requiredArea = totalWorkingLoad / soilBearingCapacity
        val footingWidth = ceil((requiredArea * 1e6) / footingLength / 50.0) * 50.0  // round up to 50mm

        val actualArea = footingLength * footingWidth / 1e6  // m²
        // Centroid sits at the resultant by construction → uniform pressure (CF-03:
        // the old trapezoid block was dead code, e ≡ 0). Reported max = average.
        val soilPressure = totalWorkingLoad / actualArea  // kN/m²
        val maxSoilPressure = soilPressure

        // ── Step 4: inverted-beam model — ACI 318M-14 §13.2.6.2 (any procedure
        // satisfying equilibrium and geometric compatibility). Upward factored
        // pressure, column loads as point loads; critical sections at column
        // faces per Table 13.2.7.1 (CF-01). ──
        val effectiveDepth = footingDepth - getMinCover() - 10.0
        val qu = (axialLoad1 + axialLoad2) / actualArea  // kN/m² factored
        val wLin = qu * footingWidth / 1000.0            // kN/m along the length
        val a1 = (footingLength / 2.0 - xResultant) / 1000.0   // m, col1 center from left edge
        val a2 = a1 + distanceBetweenColumns / 1000.0          // m, col2 center
        val lengthM = footingLength / 1000.0

        fun momentAt(xi: Double) =
            -wLin * xi * xi / 2.0 +
            axialLoad1 * maxOf(0.0, xi - a1) +
            axialLoad2 * maxOf(0.0, xi - a2)

        val xStar = axialLoad1 / wLin   // zero-shear point between columns
        val candidates = mutableListOf(
            a1 - columnDepth / 2000.0, a1 + columnDepth / 2000.0,
            a2 - col2Depth / 2000.0, a2 + col2Depth / 2000.0
        )
        if (xStar in a1..a2) candidates.add(xStar)
        val moments = candidates.filter { it in 0.0..lengthM }.map { it to momentAt(it) }
        val mPos = moments.maxOf { it.second }            // sagging → bottom steel
        val mNeg = moments.minOf { it.second }            // hogging → top steel

        // ── Step 5: longitudinal reinforcement (bottom and top), per meter of width ──
        val widthM = footingWidth / 1000.0
        val bottomReinf = flexurePerMeter(fcu, fy, footingDepth, effectiveDepth, mPos / widthM, FootingDirection.LONG)
        val topReinf = if (mNeg < 0.0)
            flexurePerMeter(fcu, fy, footingDepth, effectiveDepth, -mNeg / widthM, FootingDirection.LONG)
        else null

        // ── Step 6: transverse beam strips under each column (CF-01). Column load
        // spreads over a strip of width (column length + d); the strip cantilevers
        // (B − c)/2 each side of the column face (classic combined-footing method
        // per Das, Principles of Foundation Engineering — §13.2.6.2 equilibrium). ──
        val transverse = mutableListOf<ReinforcementResult>()
        listOf(
            Triple(axialLoad1, columnWidth, columnDepth),
            Triple(axialLoad2, col2Width, col2Depth)
        ).forEachIndexed { idx, (pi, cTrans, cLong) ->
            val stripWidth = (cLong + effectiveDepth).coerceAtMost(footingLength)
            val cantilever = (footingWidth - cTrans) / 2000.0   // m
            val pLine = pi / widthM                             // kN per m of strip length
            val muStrip = pLine * cantilever * cantilever / 2.0 // kN·m total in the strip
            val stripReinf = flexurePerMeter(
                fcu, fy, footingDepth, effectiveDepth,
                muStrip / (stripWidth / 1000.0), FootingDirection.SHORT
            )
            transverse += stripReinf
            codeNotes.add(
                "Transverse strip ${idx + 1}: width ${stripWidth.toInt()}mm, " +
                "Mu = ${"%.1f".format(muStrip)} kN·m, As = ${"%.0f".format(stripReinf.astRequired)} mm²/m"
            )
        }

        // ── Step 7: one-way shear at d from EACH column face, all four sections
        // (CF-02: §13.2.7.2 + §22.5.5.1; the old check dropped the resultant
        // offset and never checked column 2's inner face) ──
        val dM = effectiveDepth / 1000.0
        fun shearAt(xi: Double) =
            wLin * xi -
            (if (xi > a1) axialLoad1 else 0.0) -
            (if (xi > a2) axialLoad2 else 0.0)

        var vuOneWay = 0.0
        val xLeft = a1 - columnDepth / 2000.0 - dM
        if (xLeft > 0.0) vuOneWay = maxOf(vuOneWay, abs(shearAt(xLeft)))
        val xInner1 = a1 + columnDepth / 2000.0 + dM
        if (xInner1 < a2) vuOneWay = maxOf(vuOneWay, abs(shearAt(xInner1)))
        val xInner2 = a2 - col2Depth / 2000.0 - dM
        if (xInner2 > a1) vuOneWay = maxOf(vuOneWay, abs(shearAt(xInner2)))
        val xRight = lengthM - (a2 + col2Depth / 2000.0 + dM)
        if (xRight > 0.0) vuOneWay = maxOf(vuOneWay, wLin * xRight)

        val fcPrime = 0.8 * fcu
        val vcOneWay = PHI_SHEAR * 0.17 * sqrt(fcPrime) * footingWidth * effectiveDepth / 1000.0  // kN (§22.5.5.1)
        val isOneWayShearSafe = vuOneWay <= vcOneWay
        if (!isOneWayShearSafe) {
            warnings.add("ACI: one-way shear Vu=${"%.0f".format(vuOneWay)}kN > φVc=${"%.0f".format(vcOneWay)}kN — increase depth")
        }

        // ── Step 8: punching shear at both columns (Table 22.6.5.2, αs = 40) ──
        val punching1 = checkPunchingShear(fcu, columnWidth, columnDepth, effectiveDepth, axialLoad1, loadCombination)
        val punching2 = checkPunchingShear(fcu, col2Width, col2Depth, effectiveDepth, axialLoad2, loadCombination)

        val isSafe = maxSoilPressure <= soilBearingCapacity * 1.05 &&  // allow 5% over for rounding
                     punching1.isSafe && punching2.isSafe &&
                     isOneWayShearSafe &&
                     bottomReinf.isSafe &&
                     (topReinf?.isSafe ?: true)

        codeNotes.add("ACI 318M-14 §13.2.6.2 inverted beam: M+ = ${"%.1f".format(mPos)} kN·m, M− = ${"%.1f".format(mNeg)} kN·m")
        codeNotes.add(CodeReference.ACI.FOOTING_REINFORCEMENT)

        return FootingDesignResult(
            requiredWidth = footingWidth,
            requiredLength = footingLength,
            requiredThickness = footingDepth,
            soilPressure = soilPressure,
            maxSoilPressure = maxSoilPressure,
            reinforcement = bottomReinf,
            punchingShearCheck = if (punching1.utilizationRatio > punching2.utilizationRatio) punching1 else punching2,
            isSafe = isSafe,
            warnings = warnings,
            codeNotes = codeNotes,
            topReinforcement = topReinf,
            transverseReinforcement = transverse
        )
    }

    override fun designRaftFoundation(
        fcu: Double,
        fy: Double,
        totalLoads: Double,
        totalArea: Double,
        moments: Pair<Double, Double>,
        soilBearingCapacity: Double,
        raftThickness: Double
    ): FootingDesignResult {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("totalArea", totalArea)
        InputGuard.positive("soilBearingCapacity", soilBearingCapacity)
        InputGuard.positive("raftThickness", raftThickness)
        val soilPressure = totalLoads / totalArea
        val effectiveDepth = raftThickness - getMinCover() - 10.0
        
        // ACI Punching check for max column load (estimated)
        val maxColumnLoad = totalLoads * 0.15
        val punchingCheck = checkPunchingShear(fcu, 600.0, 600.0, effectiveDepth, maxColumnLoad, LoadCombination.DEAD_LIVE)
        
        val designMoment = soilPressure * (sqrt(totalArea) / 3.0).pow(2) / 10.0
        
        val reinforcement = flexurePerMeter(
            fcu, fy, raftThickness, effectiveDepth, designMoment, FootingDirection.LONG
        )
        
        return FootingDesignResult(
            requiredWidth = sqrt(totalArea) * 1000,
            requiredLength = sqrt(totalArea) * 1000,
            requiredThickness = raftThickness,
            soilPressure = soilPressure,
            maxSoilPressure = soilPressure * 1.1,
            reinforcement = reinforcement,
            punchingShearCheck = punchingCheck,
            isSafe = soilPressure <= soilBearingCapacity && punchingCheck.isSafe
        )
    }

    override fun designPileCap(
        fcu: Double,
        fy: Double,
        pileLoad: Double,
        numberOfPiles: Int,
        pileDiameter: Double,
        columnLoads: Double
    ): FootingDesignResult {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("numberOfPiles", numberOfPiles)
        InputGuard.positive("pileDiameter", pileDiameter)
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        
        // ترتيب الركائز
        val rows = ceil(sqrt(numberOfPiles.toDouble())).toInt()
        val cols = ceil(numberOfPiles.toDouble() / rows).toInt()
        
        // المسافة بين الركائز ≥ 3× قطر الركيزة (ACI 318-13.4.1)
        val spacing = max(3.0 * pileDiameter, 750.0)
        val edgeDist = pileDiameter + 100.0
        
        val capW = ((rows - 1) * spacing + 2 * edgeDist).let { ceil(it / 50.0) * 50.0 }
        val capL = ((cols - 1) * spacing + 2 * edgeDist).let { ceil(it / 50.0) * 50.0 }
        
        // السمك الأدنى: max(spacing/3, 400mm) حسب ACI 318
        val minThickness = max(spacing / 3.0, 400.0)
        val capThickness = ceil(minThickness / 50.0) * 50.0
        val d = capThickness - getMinCover() - 10.0
        
        // فحص قص الاختراق عند العمود (ACI 318-22.6.5)
        val colSize = 400.0
        val punchingResult = checkPunchingShear(
            fcu, colSize, colSize, d, columnLoads, LoadCombination.DEAD_LIVE
        )
        
        // فحص قص الركيزة (One-way shear at pile perimeter)
        val loadPerPile = columnLoads / numberOfPiles
        val pilePerimeter = PI * pileDiameter
        val pileShearStress = (loadPerPile * 1000.0) / (pilePerimeter * d)
        val fc_prime = 0.8 * fcu
        val pileShearCap = PHI_SHEAR * 0.33 * sqrt(fc_prime)
        
        if (pileShearStress > pileShearCap) {
            warnings.add("ACI: Pile one-way shear exceeds capacity - increase cap thickness")
        }
        
        // عزم الانحناء (ناتئ من وجه العمود لوسط الركيزة)
        val projection = (spacing / 2.0 + pileDiameter / 2.0 - colSize / 2.0) / 1000.0
        val Mu = loadPerPile * projection
        
        val reinf = flexurePerMeter(
            fcu, fy, capThickness, d, Mu / (capW / 1000.0), FootingDirection.SHORT
        )
        
        // فحص زاوية الخرسانة المضغوطة (Strut-and-Tie Model)
        val strutAngle = atan2(d, projection * 1000.0) * 180.0 / PI
        if (strutAngle < 40.0) {
            warnings.add(String.format("ACI: Strut angle %.1f° < 40° - increase thickness", strutAngle))
        }
        
        codeNotes.add("ACI 318: Pile Cap (Strut-and-Tie Model)")
        codeNotes.add(String.format("Layout: %dx%d piles, Spacing: %.0fmm", rows, cols, spacing))
        codeNotes.add(String.format("Strut angle: %.1f° (min 40°)", strutAngle))
        codeNotes.add(String.format("Load/pile: %.0f kN", loadPerPile))
        
        return FootingDesignResult(
            requiredWidth = capW,
            requiredLength = capL,
            requiredThickness = capThickness,
            soilPressure = loadPerPile,
            maxSoilPressure = pileLoad,
            reinforcement = reinf,
            punchingShearCheck = punchingResult,
            isSafe = punchingResult.isSafe
                && pileShearStress <= pileShearCap
                && strutAngle >= 40.0,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    override fun getMinFootingThickness(): Double = 300.0
    override fun getMinCover(): Double = 75.0
    override fun getPunchingShearCapacity(fcu: Double, perimeter: Double, effectiveDepth: Double): Double {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("perimeter", perimeter)
        InputGuard.positive("effectiveDepth", effectiveDepth)
        val fc_prime = 0.8 * fcu
        return PHI_SHEAR * 0.33 * sqrt(fc_prime) * perimeter * effectiveDepth / 1000.0
    }


    // ══════════════════════════════════════════════════════════════════════
    // F-05: rigid-raft rebuild (docs/CLAUSE_LOG.md F-05, entries A-20a..e)
    // Method: ACI 318M-14 §13.2.6.2 / §13.3.4.3 — elastic pressure field under
    // a rigid raft, inverted-slab strips by the three-moment equation,
    // punching per actual column. Applicability limits per CLAUSE_LOG.
    // ══════════════════════════════════════════════════════════════════════

    data class RaftColumn(
        val x: Double, val y: Double,            // column center, mm
        val widthX: Double, val widthY: Double,  // column dimensions, mm
        val serviceLoad: Double,                 // kN (unfactored, §13.3.1.1)
        val factoredLoad: Double                 // kN (Pu, for strength checks)
    )

    enum class RaftPunchingPosition { INTERIOR, EDGE, CORNER }

    data class RaftPunchingResult(
        val columnIndex: Int,
        val position: RaftPunchingPosition,
        val criticalPerimeter: Double,  // mm
        val criticalArea: Double,       // m²
        val vu: Double,                 // kN
        val phiVc: Double,              // kN
        val utilization: Double,
        val isSafe: Boolean
    )

    data class RaftShearCheck(
        val stripTag: String,
        val lineIndex: Int,
        val vu: Double,                 // kN at d from the support face
        val phiVc: Double,              // kN
        val utilization: Double,
        val isSafe: Boolean
    )

    data class RaftStripResult(
        val tag: String,
        val spansDirection: String,      // "X" or "Y"
        val tributaryWidth: Double,      // mm
        val lineLoad: Double,            // kN/m, factored
        val supportMoments: List<Double>,// kNm, BEAM sign (negative → raft BOTTOM steel at the line)
        val spanMoments: List<Double>,   // kNm, max sagging per span (positive → raft TOP steel in the span)
        val asTop: Double,               // mm² within the tributary width (midspan regions)
        val asBottom: Double             // mm² within the tributary width (column-line regions)
    )

    data class RaftDesignResult(
        val lengthX: Double, val widthY: Double, // mm
        val thickness: Double,
        val qMaxService: Double, val qMinService: Double, // kPa
        val fullContact: Boolean,
        val strips: List<RaftStripResult>,
        val punching: List<RaftPunchingResult>,
        val oneWayShear: List<RaftShearCheck>,
        val equilibriumCheck: Boolean,
        val momentEquilibriumX: Boolean,  // X-strip reactions + discretization term reproduce sum(Pu.x)
        val momentEquilibriumY: Boolean,  // Y-strip reactions + discretization term reproduce sum(Pu.y)
        val momentDiscretizationX: Double,  // kN.m: first-moment smoothed by uniform-per-span sampling (X family)
        val momentDiscretizationY: Double   // kN.m: same, Y family
        val isSafe: Boolean,
        val warnings: List<String>,
        val codeNotes: List<String>
    )

    /** Pressure-field parameters (kPa) under a rigid raft: q(x,y) = c0 + cx·(x−xc) + cy·(y−yc), x,y in mm. */
    private data class PressureField(val c0: Double, val cx: Double, val cy: Double, val xc: Double, val yc: Double) {
        fun q(x: Double, y: Double) = c0 + cx * (x - xc) + cy * (y - yc)
    }

    private fun pressureField(columns: List<RaftColumn>, x0: Double, x1: Double, y0: Double, y1: Double, factored: Boolean): PressureField {
        val L = (x1 - x0) / 1000.0   // m
        val B = (y1 - y0) / 1000.0   // m
        val A = L * B
        val xc = (x0 + x1) / 2.0
        val yc = (y0 + y1) / 2.0
        val P = columns.sumOf { if (factored) it.factoredLoad else it.serviceLoad }
        val Mx = columns.sumOf { (if (factored) it.factoredLoad else it.serviceLoad) * (it.y - yc) / 1000.0 } // kN.m about centroidal x
        val My = columns.sumOf { (if (factored) it.factoredLoad else it.serviceLoad) * (it.x - xc) / 1000.0 } // kN.m about centroidal y
        val Ix = L * B * B * B / 12.0   // m⁴, about x-axis
        val Iy = B * L * L * L / 12.0   // m⁴, about y-axis
        return PressureField(P / A, My / (Iy * 1e3), Mx / (Ix * 1e3), xc, yc) // kPa per mm offset (M[kN.m]·dy[mm]/1e3/I[m^4])
    }

    /** Solves the three-moment equation for a continuous beam (supports at [positions]; uniform load wSpan[j] on span j, separate uniform loads on the overhangs). Returns support moments (kNm, hogging negative). */
    private fun continuousBeamSupportMoments(positions: List<Double>, wSpan: List<Double>, wOverLeft: Double, wOverRight: Double, overhangLeft: Double, overhangRight: Double): List<Double> {
        val n = positions.size
        require(wSpan.size == n - 1) { "one line load per span" }
        val m = DoubleArray(n)
        m[0] = -wOverLeft * overhangLeft * overhangLeft / 2.0
        m[n - 1] = -wOverRight * overhangRight * overhangRight / 2.0
        if (n == 2) return m.toList()
        // tridiagonal system for interior supports
        val nv = n - 2
        val a = DoubleArray(nv); val b = DoubleArray(nv); val cc = DoubleArray(nv); val r = DoubleArray(nv)
        for (j in 1 until n - 1) {
            val Lj = positions[j] - positions[j - 1]
            val Lk = positions[j + 1] - positions[j]
            val i = j - 1
            a[i] = Lj
            b[i] = 2 * (Lj + Lk)
            cc[i] = Lk
            r[i] = -(wSpan[j - 1] * Lj * Lj * Lj + wSpan[j] * Lk * Lk * Lk) / 4.0
        }
        r[0] -= a[0] * m[0]
        r[nv - 1] -= cc[nv - 1] * m[n - 1]
        // Thomas algorithm
        for (i in 1 until nv) {
            val f = a[i] / b[i - 1]
            b[i] -= f * cc[i - 1]
            r[i] -= f * r[i - 1]
        }
        for (i in nv - 1 downTo 0) {
            val up = if (i == nv - 1) 0.0 else cc[i] * m[i + 2]
            m[i + 1] = (r[i] - up) / b[i]
        }
        return m.toList()
    }

    /** Exact As for a rectangular section (singly reinforced), mm²; Double.POSITIVE_INFINITY if the section fails. */
    private fun asFlexure(fc: Double, fy: Double, b: Double, d: Double, mu: Double): Double {
        val phi = 0.90
        val k = mu / (phi * 0.85 * fc * b * d * d)
        if (k <= 0.0) return 0.0
        if (1.0 - 2.0 * k <= 0.0) return Double.POSITIVE_INFINITY
        return 0.85 * fc * b * d / fy * (1.0 - sqrt(1.0 - 2.0 * k))
    }

    /** Mat minimum reinforcement per ACI 318M-14 §13.3.4.4 → Table 8.6.1.1 (verified A-20c/A-20d). */
    fun matMinReinforcement(fy: Double, grossWidth: Double, thickness: Double): Double {
        val ratio = if (fy < 420.0) 0.0020 else max(0.0018 * 420.0 / fy, 0.0014)
        return ratio * grossWidth * thickness
    }

    /**
     * Rigid-raft design from the actual column layout (F-05 rebuild).
     * Applicability (CLAUSE_LOG F-05): rectangular plan from the column bounding box plus
     * a uniform edge projection; ≥ 2 column lines in each direction; full soil contact
     * (qmin ≥ 0) is required — uplift/partial contact is outside the rigid method and is
     * returned as an explicit out-of-scope failure, never silently designed.
     * Sign convention (re-derived after review): the strip carries UPWARD pressure and is
     * held down at the column lines, so the raft moment is the NEGATIVE of the
     * downward-loaded beam: TOP steel in the spans, BOTTOM steel at the column lines.
     */
    fun designRaftFoundation(
        fcu: Double,
        fy: Double,
        columns: List<RaftColumn>,
        edgeProjection: Double,
        soilBearingCapacity: Double,
        raftThickness: Double
    ): RaftDesignResult {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("edgeProjection", edgeProjection)
        InputGuard.positive("soilBearingCapacity", soilBearingCapacity)
        InputGuard.positive("raftThickness", raftThickness)
        require(columns.isNotEmpty()) { "raft needs at least one column" }
        // Load provenance: the caller supplies BOTH loads; the engine cannot verify the
        // combination used, but swapped or inconsistent values must fail closed.
        columns.forEachIndexed { i, c ->
            require(c.serviceLoad > 0.0 && c.factoredLoad > 0.0) { "column $i: service and factored loads must both be positive (kN)" }
            require(c.factoredLoad >= c.serviceLoad) { "column $i: factored load < service load - the two look swapped; supply the unfactored (service) and strength-combination (factored) values" }
        }
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val fc = 0.8 * fcu
        val d = raftThickness - getMinCover() - 10.0
        require(d >= 150.0) { "ACI 318M-14 13.3.1.2: effective depth of bottom reinforcement must be at least 150 mm" }

        val x0 = columns.minOf { it.x - it.widthX / 2.0 } - edgeProjection
        val x1 = columns.maxOf { it.x + it.widthX / 2.0 } + edgeProjection
        val y0 = columns.minOf { it.y - it.widthY / 2.0 } - edgeProjection
        val y1 = columns.maxOf { it.y + it.widthY / 2.0 } + edgeProjection

        val qs = pressureField(columns, x0, x1, y0, y1, false)
        val qu = pressureField(columns, x0, x1, y0, y1, true)
        val corners = listOf(x0 to y0, x0 to y1, x1 to y0, x1 to y1)
        val qMaxService = corners.maxOf { qs.q(it.first, it.second) }
        val qMinService = corners.minOf { qs.q(it.first, it.second) }
        val fullContact = qMinService >= 0.0
        if (!fullContact) {
            warnings.add("Uplift / partial contact (qmin < 0): outside the rigid-raft method scope - result is not valid, re-resolve with a method that handles loss of contact")
        }
        columns.forEachIndexed { i, c ->
            if (c.factoredLoad > 2.0 * c.serviceLoad) warnings.add("column $i: factored/service ratio ${"%.2f".format(c.factoredLoad / c.serviceLoad)} exceeds 2.0 - verify the load combination provenance")
        }
        // Rigidity is an assumption of the conventional method, not a verified property.
        warnings.add("Rigidity is ASSUMED, not verified (13.2.6.2): no deflection or soil-structure interaction check is performed. Flexible mats (thin raft / wide column spacing) need a Winkler or FE analysis - not implemented here")

        fun lines(values: List<Double>): List<Double> {
            val out = mutableListOf<Double>()
            for (v in values.sorted()) if (out.isEmpty() || abs(out.last() - v) > 1.0) out += v
            return out
        }
        val xLines = lines(columns.map { it.x })
        val yLines = lines(columns.map { it.y })
        require(xLines.size >= 2 && yLines.size >= 2) { "rigid-raft strip method needs at least 2 column lines in each direction - use a combined footing for single-line layouts" }
        // Full rectangular grid: every strip rests on a column at every crossing line;
        // a missing intersection breaks the continuous-strip assumption - fail closed.
        val gridComplete = yLines.all { yl -> xLines.all { xl -> columns.any { abs(it.x - xl) <= 1.0 && abs(it.y - yl) <= 1.0 } } }
        require(gridComplete) { "rigid-raft strip method needs a COMPLETE rectangular column grid (a column at every grid intersection); missing intersections invalidate the strip assumption - resolve the layout first" }

        val strips = mutableListOf<RaftStripResult>()
        val shearChecks = mutableListOf<RaftShearCheck>()
        val reactionSums = mutableMapOf<String, Double>()
        val momentSums = mutableMapOf<String, Double>()
        val sampledMomentSums = mutableMapOf<String, Double>()
        val discretizationSums = mutableMapOf<String, Double>()

        /**
         * One family of strips: strips span along [spansDir] between the support lines
         * (the column lines perpendicular to [spansDir]); one strip per column line in the
         * other direction, carrying the average factored pressure over its tributary width.
         */
        fun analyzeStrips(
            spansDir: String,
            stripLines: List<Double>,
            supportLines: List<Double>,
            bandOf: (Int) -> Pair<Double, Double>,
            edgeLow: Double,
            edgeHigh: Double
        ) {
            var dirReactionSum = 0.0
            var dirMomentSum = 0.0
            var dirSampledMomentSum = 0.0
            var dirDiscretization = 0.0
            stripLines.forEachIndexed { rowIdx, rowPos ->
                val (widthMm, bandCentroid) = bandOf(rowIdx)
                // Pressure sampled at the band CENTROID across the strip, and per SPAN
                // midpoint along it, so eccentric pressure variation along the strip is
                // carried into the strip moments instead of being smeared into one average.
                fun qAt(spanPos: Double) =
                    if (spansDir == "X") qu.q(spanPos, bandCentroid) else qu.q(bandCentroid, spanPos)
                val n = supportLines.size
                val L = supportLines.map { it / 1000.0 }  // m
                val oLeft = (supportLines.first() - edgeLow) / 1000.0
                val oRight = (edgeHigh - supportLines.last()) / 1000.0
                val wSpan = (0 until n - 1).map { j -> qAt((supportLines[j] + supportLines[j + 1]) / 2.0) * widthMm / 1000.0 }
                val wOhL = qAt((edgeLow + supportLines.first()) / 2.0) * widthMm / 1000.0
                val wOhR = qAt((supportLines.last() + edgeHigh) / 2.0) * widthMm / 1000.0
                val w = qAt((edgeLow + edgeHigh) / 2.0) * widthMm / 1000.0   // strip-average line load (reported)
                val M = continuousBeamSupportMoments(L, wSpan, wOhL, wOhR, oLeft, oRight)
                val R = DoubleArray(n)
                val spanM = MutableList(n - 1) { 0.0 }
                for (j in 0 until n - 1) {
                    val wj = wSpan[j]
                    val Lj = L[j + 1] - L[j]
                    val vLeft = wj * Lj / 2.0 + (M[j + 1] - M[j]) / Lj
                    R[j] += vLeft
                    R[j + 1] += wj * Lj - vLeft
                    if (wj > 1e-9) {
                        val xStar = vLeft / wj
                        if (xStar in 0.0..Lj) spanM[j] = M[j] + vLeft * xStar - wj * xStar * xStar / 2.0
                    }
                    spanM[j] = maxOf(spanM[j], 0.0)
                }
                R[0] += wOhL * oLeft
                R[n - 1] += wOhR * oRight
                dirReactionSum += R.sum()
                dirMomentSum += (0 until n).sumOf { R[it] * L[it] }   // first moment of this strip's reactions (kN.m)
                // first moment of the SAMPLED piecewise-uniform load (what the beam equilibrates)
                var sampled = wOhL * oLeft * ((edgeLow + supportLines.first()) / 2000.0) + wOhR * oRight * ((supportLines.last() + edgeHigh) / 2000.0)
                for (j in 0 until n - 1) sampled += wSpan[j] * (L[j + 1] - L[j]) * ((supportLines[j] + supportLines[j + 1]) / 2000.0)
                dirSampledMomentSum += sampled
                // analytic discretization term: uniform-per-span sampling drops w' * len^3 / 12
                // of first moment per segment (w' = line-load slope along the strip, kN/m per m)
                val wSlope = (if (spansDir == "X") qu.cx else qu.cy) * widthMm
                var segCubes = oLeft * oLeft * oLeft + oRight * oRight * oRight
                for (j in 0 until n - 1) { val Lj = L[j + 1] - L[j]; segCubes += Lj * Lj * Lj }
                dirDiscretization += wSlope * segCubes / 12.0
                // Reinforcement: raft TOP in spans (beam sagging), raft BOTTOM at lines (beam hogging)
                val mSpanMax = (spanM.maxOrNull() ?: 0.0) * 1e6          // N.mm
                val mSupMax = -(M.minOrNull() ?: 0.0) * 1e6              // N.mm, positive magnitude
                val asTopReq = asFlexure(fc, fy, widthMm, d, mSpanMax)
                val asBotReq = asFlexure(fc, fy, widthMm, d, mSupMax)
                val asMin = matMinReinforcement(fy, widthMm, raftThickness)
                strips += RaftStripResult(
                    tag = "$spansDir-strip ${rowIdx + 1}", spansDirection = spansDir,
                    tributaryWidth = widthMm, lineLoad = w,
                    supportMoments = M, spanMoments = spanM,
                    asTop = max(asTopReq, asMin), asBottom = max(asBotReq, asMin)
                )
                // One-way shear at d from each support face (§22.5.5.1 + Table 13.2.7.1/§13.2.7.2; F-03 rule)
                val phiVc = PHI_SHEAR * 0.17 * sqrt(fc) * widthMm * d / 1000.0
                for (j in 0 until n) {
                    // widest column of THIS strip sitting on support line j, half-width in the span direction (m)
                    val colHalf = columns.filter {
                        abs((if (spansDir == "X") it.y else it.x) - rowPos) <= 1.0 &&
                            abs((if (spansDir == "X") it.x else it.y) - supportLines[j]) <= 1.0
                    }.maxOfOrNull { if (spansDir == "X") it.widthX else it.widthY }?.div(2000.0) ?: 0.2
                    val wLeft = if (j > 0) wSpan[j - 1] else 0.0
                    val wRight = if (j < n - 1) wSpan[j] else 0.0
                    val vFaceLeft = if (j > 0) wLeft * (L[j] - L[j - 1]) / 2.0 + (M[j - 1] - M[j]) / (L[j] - L[j - 1]) else 0.0
                    val vFaceRight = if (j < n - 1) wRight * (L[j + 1] - L[j]) / 2.0 + (M[j + 1] - M[j]) / (L[j + 1] - L[j]) else 0.0
                    val wGoverning = max(wLeft, wRight)
                    val vu = max(0.0, max(vFaceLeft, vFaceRight) - wGoverning * (colHalf + d / 1000.0))
                    shearChecks += RaftShearCheck(
                        "$spansDir-strip ${rowIdx + 1}", j, vu, phiVc,
                        if (phiVc > 0) vu / phiVc else Double.POSITIVE_INFINITY, vu <= phiVc
                    )
                }
            }
            reactionSums[spansDir] = dirReactionSum
            momentSums[spansDir] = dirMomentSum
            sampledMomentSums[spansDir] = dirSampledMomentSum
            discretizationSums[spansDir] = dirDiscretization
        }

        fun band(lines: List<Double>, idx: Int, edgeLow: Double, edgeHigh: Double): Pair<Double, Double> {
            val lo = if (idx == 0) edgeLow else (lines[idx - 1] + lines[idx]) / 2.0
            val hi = if (idx == lines.size - 1) edgeHigh else (lines[idx] + lines[idx + 1]) / 2.0
            return (hi - lo) to ((lo + hi) / 2.0)   // width, centroid (mm)
        }
        analyzeStrips("X", yLines, xLines, { band(yLines, it, y0, y1) }, x0, x1)  // x-spanning: one strip per y-row
        analyzeStrips("Y", xLines, yLines, { band(xLines, it, x0, x1) }, y0, y1)  // y-spanning: one strip per x-line

        // Punching per actual column (§22.6.4.1 minimum-perimeter rule, Table 22.6.5.2, §22.6.5.3)
        val punching = columns.mapIndexed { idx, col ->
            val left = col.x - col.widthX / 2.0 - d / 2.0
            val right = col.x + col.widthX / 2.0 + d / 2.0
            val bot = col.y - col.widthY / 2.0 - d / 2.0
            val top = col.y + col.widthY / 2.0 + d / 2.0
            val cl = left < x0; val cr = right > x1; val cb = bot < y0; val ct = top > y1
            val clipped = listOf(cl, cr, cb, ct).count { it }
            val xl = max(left, x0); val xr = min(right, x1)
            val yb = max(bot, y0); val yt = min(top, y1)
            val b0 = 2 * (xr - xl) + 2 * (yt - yb)
            val areaCrit = (xr - xl) / 1000.0 * (yt - yb) / 1000.0   // m²
            val position = when (clipped) { 0 -> RaftPunchingPosition.INTERIOR; 1 -> RaftPunchingPosition.EDGE; else -> RaftPunchingPosition.CORNER }
            val alphaS = when (position) { RaftPunchingPosition.INTERIOR -> 40.0; RaftPunchingPosition.EDGE -> 30.0; RaftPunchingPosition.CORNER -> 20.0 }
            val beta = max(col.widthX, col.widthY) / min(col.widthX, col.widthY)
            val sqrtFc = sqrt(fc)
            val vc = minOf(0.33 * sqrtFc, 0.17 * (1.0 + 2.0 / beta) * sqrtFc, 0.083 * (2.0 + alphaS * d / b0) * sqrtFc)
            val phiVc = PHI_SHEAR * vc * b0 * d / 1000.0
            val vu = col.factoredLoad - qu.q(col.x, col.y) * areaCrit
            RaftPunchingResult(idx, position, b0, areaCrit, vu, phiVc,
                if (phiVc > 0) vu / phiVc else Double.POSITIVE_INFINITY, vu <= phiVc)
        }

        // Global equilibrium self-check: FORCE (strip reactions vs factored loads) and
        // MOMENT (each strip family's reaction first moment vs the applied first moment
        // of the column loads). With per-span pressure sampling over a linear field the
        // moment identity is exact up to solver precision; 0.5% tolerance on both.
        val totalFactored = columns.sumOf { it.factoredLoad }
        val forceOk = reactionSums.values.all { abs(it - totalFactored) <= 0.005 * totalFactored }
        val appliedMomentX = columns.sumOf { it.factoredLoad * it.x / 1000.0 }  // kN.m, first moment in x
        val appliedMomentY = columns.sumOf { it.factoredLoad * it.y / 1000.0 }  // kN.m, first moment in y
        val momTolX = 0.005 * totalFactored * (x1 - x0) / 1000.0
        val momTolY = 0.005 * totalFactored * (y1 - y0) / 1000.0
        val momentDiscX = discretizationSums["X"] ?: 0.0
        val momentDiscY = discretizationSums["Y"] ?: 0.0
        // reacted + analytic discretization term must reproduce the applied first moment
        // (identical to reacted == sampled-load moment; both identities hold to solver precision)
        val momentEquilibriumX = abs((momentSums["X"] ?: 0.0) + momentDiscX - appliedMomentX) <= momTolX
        val momentEquilibriumY = abs((momentSums["Y"] ?: 0.0) + momentDiscY - appliedMomentY) <= momTolY
        val equilibriumCheck = forceOk && momentEquilibriumX && momentEquilibriumY
        if (!forceOk) warnings.add("Internal force-equilibrium check failed: strip reactions do not sum to the factored column loads")
        if (!momentEquilibriumX) warnings.add("Internal moment-equilibrium check failed in X: strip reactions + discretization term do not reproduce the applied first moment sum(Pu.x)")
        if (!momentEquilibriumY) warnings.add("Internal moment-equilibrium check failed in Y: strip reactions + discretization term do not reproduce the applied first moment sum(Pu.y)")
        // Discretization smoothing as a share of the applied ECCENTRIC moment (My for the
        // X family, Mx for the Y family): above 5% the uniform-per-span representation is
        // smoothing a real gradient - advise finer strips or a FE/Winkler model.
        val eccMy = appliedMomentX - totalFactored * (x0 + x1) / 2000.0
        val eccMx = appliedMomentY - totalFactored * (y0 + y1) / 2000.0
        if (abs(eccMy) > 1e-6 && abs(momentDiscX) > 0.05 * abs(eccMy))
            warnings.add("Strip discretization smooths ${"%.1f".format(100 * abs(momentDiscX) / abs(eccMy))}% of the applied eccentric moment My - design moments use per-span sampling but finer strips or a Winkler/FE model are advised for this eccentricity")
        if (abs(eccMx) > 1e-6 && abs(momentDiscY) > 0.05 * abs(eccMx))
            warnings.add("Strip discretization smooths ${"%.1f".format(100 * abs(momentDiscY) / abs(eccMx))}% of the applied eccentric moment Mx - design moments use per-span sampling but finer strips or a Winkler/FE model are advised for this eccentricity")

        codeNotes.add("Rigid-raft conventional method per ACI 318M-14 13.2.6.2/13.3.4.3 (CLAUSE_LOG F-05)")
        codeNotes.add("Elastic pressure field q = P/A ± Mx·y/Ix ± My·x/Iy; bearing at UNFACTORED loads per 13.3.1.1")
        codeNotes.add("Strips: three-moment elastic analysis (13.3.4.2 prohibits DDM); raft TOP steel in spans, BOTTOM at column lines")
        codeNotes.add("Mat min reinforcement per 13.3.4.4 -> Table 8.6.1.1 (R13.3.4.4: consider continuous steel near both faces)")

        val isSafe = fullContact && equilibriumCheck && qMaxService <= soilBearingCapacity &&
            punching.all { it.isSafe } && shearChecks.all { it.isSafe } &&
            strips.all { it.asTop.isFinite() && it.asBottom.isFinite() }
        if (qMaxService > soilBearingCapacity) warnings.add("qmax service exceeds the allowable bearing capacity")
        return RaftDesignResult(
            lengthX = x1 - x0, widthY = y1 - y0, thickness = raftThickness,
            qMaxService = qMaxService, qMinService = qMinService, fullContact = fullContact,
            strips = strips, punching = punching, oneWayShear = shearChecks,
            equilibriumCheck = equilibriumCheck,
            momentEquilibriumX = momentEquilibriumX, momentEquilibriumY = momentEquilibriumY,
            momentDiscretizationX = momentDiscX, momentDiscretizationY = momentDiscY,
            isSafe = isSafe,
            warnings = warnings, codeNotes = codeNotes
        )
    }
}
