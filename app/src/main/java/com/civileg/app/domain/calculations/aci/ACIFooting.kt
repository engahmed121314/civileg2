package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult
import com.civileg.app.domain.entities.ShearCheckResult
import kotlin.math.*

class ACIFooting : FootingDesign {
    
    companion object {
        private const val PHI_SHEAR = 0.75
        private const val PHI_BENDING = 0.90
        private const val MIN_REIN_RATIO = 0.0018  // ACI 318-13.3.1: 0.18% للقواعد
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
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // 1. فحص السمك الأدنى
        if (footingDepth < MIN_THICKNESS) {
            warnings.add("ACI 318: Minimum footing thickness is 300mm")
        }

        // 2. تحويل الأحمال للخدمة (لحساب أبعاد القاعدة)
        val P_service = axialLoad / loadCombination.getFactorForCode(DesignCode.ACI)
        val Mx_service = momentX / loadCombination.getFactorForCode(DesignCode.ACI)
        val My_service = momentY / loadCombination.getFactorForCode(DesignCode.ACI)

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

        // 8. السمك الفعال
        val d = footingDepth - getMinCover() - 10.0

        // 9. عزم الانحناء في الاتجاهين (الناتئ من وجه العمود)
        val cantX = (B - columnWidth) / 2.0 / 1000.0  // m
        val cantY = (L - columnDepth) / 2.0 / 1000.0  // m
        val Mu_x = q_avg * (L / 1000.0) * cantX * cantX / 2.0  // kN.m
        val Mu_y = q_avg * (B / 1000.0) * cantY * cantY / 2.0  // kN.m

        // 10. فحص القص الأحادي عند بعد d/2 من وجه العمود (ACI 318-22.5)
        val Vu_x = q_avg * (L / 1000.0) * max(cantX - d / 2000.0, 0.0)  // kN
        val Vu_y = q_avg * (B / 1000.0) * max(cantY - d / 2000.0, 0.0)  // kN
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
        val reinfX = calculateFootingReinforcement(fcu, fy, B, L, d, Mu_x / (L / 1000.0), FootingDirection.SHORT)
        val reinfY = calculateFootingReinforcement(fcu, fy, B, L, d, Mu_y / (B / 1000.0), FootingDirection.LONG)

        // 13. التسليح الرئيسي = الأكبر
        val mainReinf = if (reinfX.astRequired >= reinfY.astRequired) reinfX else reinfY

        // 14. التسليح التوزيعي - ACI 318-13.3.4: لا يقل عن 20% من التسليح الرئيسي
        // في الاتجاه الطويل يتم وضع تسليح توزيعي = 20% من As الرئيسي
        val mainAs = max(reinfX.astRequired, reinfY.astRequired)
        val distAs = 0.20 * mainAs
        val distBarDia = listOf(12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0).firstOrNull { dia ->
            val area = PI * dia * dia / 4.0
            val bars = ceil(distAs / area).toInt()
            bars in 5..20
        } ?: 12.0
        val distBarArea = PI * distBarDia * distBarDia / 4.0
        val distBarsPerMeter = if (distAs > 0) ceil(distAs / distBarArea).toInt() else 0
        val distSpacing = if (distBarsPerMeter > 0) {
            val maxSp = min(450.0, min(3.0 * d, 750.0))
            floor(1000.0 / distBarsPerMeter).coerceIn(100.0, maxSp)
        } else {
            200.0
        }

        codeNotes.add("ACI 318-19: Isolated Footing Design")
        codeNotes.add(String.format("B=%.0fxL=%.0f mm, d=%.0f mm", B, L, d))
        codeNotes.add(String.format("q_avg=%.1f, q_max=%.1f, q_min=%.1f kPa", q_avg, q_max, q_min))
        codeNotes.add(String.format("Mu_x=%.1f, Mu_y=%.1f kN.m", Mu_x, Mu_y))
        codeNotes.add(String.format("Short dir: %s", reinfX.barString))
        codeNotes.add(String.format("Long dir: %s", reinfY.barString))
        if (distBarsPerMeter > 0) {
            codeNotes.add(String.format("Distribution: %dØ%d @ %dmm", 
                distBarsPerMeter, distBarDia.toInt(), distSpacing.toInt()
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
                && punchingCheck.isSafe,
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
        // ACI 318-22: فحص قص الاختراق
        // b0 = 2(c1 + c2) + 4d (محيط الاختراق)
        val b0 = 2.0 * (columnWidth + columnDepth) + 4.0 * effectiveDepth
        // ACI يستخدم f'c (اسطوانة) = 0.8 × fcu (مكعب)
        val fc_prime = 0.8 * fcu
        // vn = min(0.33√f'c, 0.17(1+2/β)√f'c, 0.083(2+4d/bo)√f'c)
        val beta = max(columnDepth, columnWidth) / min(columnDepth, columnWidth).coerceAtLeast(1.0)
        val vn = minOf(
            0.33 * sqrt(fc_prime),
            0.17 * (1.0 + 2.0 / beta) * sqrt(fc_prime),
            0.083 * (2.0 + 4.0 * effectiveDepth / b0) * sqrt(fc_prime)
        )
        val capacity = PHI_SHEAR * vn * b0 * effectiveDepth / 1000.0
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
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        
        // ACI 318-19 طريقة Rn-ρ (التعادل)
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
        
        // ρ_min = max(0.18%, 1.33×√f'c/fy) حسب ACI 318-13.3.1
        val rhoMin = max(MIN_REIN_RATIO, 1.33 * sqrt(fc_prime) / fy)
        val rhoMax = 0.025  // ACI 318 الحد الأقصى
        
        val rhoFinal = rho.coerceIn(rhoMin, rhoMax)
        val asRequired = rhoFinal * b * d
        
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
        
        val utilization = rho / rhoFinal.coerceAtLeast(0.001)
        codeNotes.add(String.format("ACI 318-19: %dØ%d @ %dmm", actualBars, barDiameter.toInt(), finalSpacing.toInt()))
        codeNotes.add(String.format("ρ=%.4f, ρ_min=%.4f, Rn=%.2f", rhoFinal, rhoMin, Rn))
        
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
        columnDepth: Double
    ): FootingDesignResult {
        val p1Working = axialLoad1 / loadCombination.getFactorForCode(DesignCode.ACI)
        val p2Working = axialLoad2 / loadCombination.getFactorForCode(DesignCode.ACI)
        val totalWorkingLoad = p1Working + p2Working
        
        val xResultant = (p2Working * distanceBetweenColumns) / totalWorkingLoad
        val s1 = 600.0 // Default offset
        val footingLength = 2 * (xResultant + s1)
        
        val requiredArea = (totalWorkingLoad * 1.1) / soilBearingCapacity
        var footingWidth = (requiredArea * 1e6) / footingLength
        footingWidth = ceil(footingWidth / 50) * 50
        
        val actualArea = (footingLength * footingWidth) / 1e6
        val soilPressure = totalWorkingLoad / actualArea
        
        val effectiveDepth = footingDepth - getMinCover() - 10.0
        val qu_ultimate = (axialLoad1 + axialLoad2) / actualArea
        
        // Simplified moment for reinforcement
        val maxMoment = qu_ultimate * (footingLength / 2000.0).pow(2) / 8.0 
        
        val reinforcement = calculateFootingReinforcement(
            fcu, fy, footingWidth, footingLength, effectiveDepth,
            maxMoment, FootingDirection.LONG
        )
        
        val punching1 = checkPunchingShear(fcu, columnWidth, columnDepth, effectiveDepth, axialLoad1, loadCombination)
        
        return FootingDesignResult(
            requiredWidth = footingWidth,
            requiredLength = footingLength,
            requiredThickness = footingDepth,
            soilPressure = soilPressure,
            maxSoilPressure = soilPressure,
            reinforcement = reinforcement,
            punchingShearCheck = punching1,
            isSafe = soilPressure <= soilBearingCapacity && punching1.isSafe
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
        val soilPressure = totalLoads / totalArea
        val effectiveDepth = raftThickness - getMinCover() - 10.0
        
        // ACI Punching check for max column load (estimated)
        val maxColumnLoad = totalLoads * 0.15
        val punchingCheck = checkPunchingShear(fcu, 600.0, 600.0, effectiveDepth, maxColumnLoad, LoadCombination.DEAD_LIVE)
        
        val designMoment = soilPressure * (sqrt(totalArea) / 3.0).pow(2) / 10.0
        
        val reinforcement = calculateFootingReinforcement(
            fcu, fy, sqrt(totalArea)*1000, sqrt(totalArea)*1000,
            effectiveDepth, designMoment, FootingDirection.LONG
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
        
        val reinf = calculateFootingReinforcement(
            fcu, fy, capW, capL, d, Mu / (capW / 1000.0), FootingDirection.SHORT
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
        val fc_prime = 0.8 * fcu
        return PHI_SHEAR * 0.33 * sqrt(fc_prime) * perimeter * effectiveDepth / 1000.0
    }
}
