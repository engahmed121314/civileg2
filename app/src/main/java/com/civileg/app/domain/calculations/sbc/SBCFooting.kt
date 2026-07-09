package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult
import com.civileg.app.domain.entities.ShearCheckResult
import kotlin.math.*

class SBCFooting : FootingDesign {
    
    companion object {
        // SBC 304 follows ACI 318 for footing design
        // معاملات أمان ACI/SBC
        private const val GAMMA_C = 1.5  // ACI doesn't divide f'c by gamma, but SBC uses similar approach
        private const val GAMMA_S = 1.15
        private const val MIN_REIN_RATIO = 0.0018  // 0.18% min for footings (ACI 318-13.3.1)
        private const val MIN_THICKNESS = 300.0  // mm
    }
    
    // SBC 304 follows ACI 318 closely for footing design
    
    override fun designIsolatedFooting(
        fcu: Double, fy: Double, columnWidth: Double, columnDepth: Double,
        axialLoad: Double, momentX: Double, momentY: Double,
        soilBearingCapacity: Double, footingDepth: Double, loadCombination: LoadCombination,
        constraints: BoundaryConstraints
    ): FootingDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        
        // 1. تحويل الأحمال للخدمة (لحساب أبعاد القاعدة)
        val factor = loadCombination.getFactorForCode(DesignCode.SBC)
        val P_service = axialLoad / factor
        val Mx_service = momentX / factor
        val My_service = momentY / factor
        
        // 2. حساب المساحة المطلوبة
        val t_m = footingDepth / 1000.0
        val netSBC = soilBearingCapacity - 25.0 * t_m  // خصم وزن الخرسانة
        val areaRequired = P_service * 1.1 / netSBC.coerceAtLeast(soilBearingCapacity * 0.8)
        
        var side = sqrt(areaRequired) * 1000.0
        var footingLength = side
        var footingWidth = side
        
        // 3. تطبيق حدود الجار
        constraints.maxLeft?.let { if (footingWidth / 2 > it) { 
            footingWidth = it * 2
            footingLength = (areaRequired * 1e6) / footingWidth
            warnings.add("Width restricted by boundary (SBC)")
        }}
        
        constraints.maxTop?.let { if (footingLength / 2 > it) { 
            footingLength = it * 2
            footingWidth = (areaRequired * 1e6) / footingLength
            warnings.add("Length restricted by boundary (SBC)")
        }}

        val roundedWidth = ceil(footingWidth / 50.0) * 50.0
        val roundedLength = ceil(footingLength / 50.0) * 50.0
        
        val actualArea = (roundedWidth * roundedLength) / 1e6
        
        // 4. ضغط التربة مع الانحراف (eccentricity)
        val q_avg = P_service / actualArea
        val ex = Mx_service / P_service
        val ey = My_service / P_service
        val q_max_x = q_avg * (1.0 + 6.0 * ex / (roundedWidth / 1000.0))
        val q_max_y = q_avg * (1.0 + 6.0 * ey / (roundedLength / 1000.0))
        val q_max = max(q_max_x, q_max_y)
        val q_min = min(
            q_avg * (1.0 - 6.0 * ex / (roundedWidth / 1000.0)),
            q_avg * (1.0 - 6.0 * ey / (roundedLength / 1000.0))
        )
        
        if (q_max > soilBearingCapacity) {
            warnings.add(String.format("SBC: q_max=%.1f kPa > SBC=%.1f kPa", q_max, soilBearingCapacity))
        }
        if (q_min < 0) {
            warnings.add("SBC: Footing separation - increase dimensions")
        }
        
        // 5. السمك الفعال
        val d = footingDepth - getMinCover() - 10.0
        
        // 6. عزم الانحناء في الاتجاهين (الناتئ من وجه العمود)
        val cantX = (roundedWidth - columnWidth) / 2.0 / 1000.0  // m
        val cantY = (roundedLength - columnDepth) / 2.0 / 1000.0  // m
        val Mu_x = q_avg * (roundedLength / 1000.0) * cantX * cantX / 2.0  // kN.m
        val Mu_y = q_avg * (roundedWidth / 1000.0) * cantY * cantY / 2.0  // kN.m

        // 7. فحص القص الأحادي عند بعد d/2 من وجه العمود (SBC 304 / ACI 318-22.5)
        val Vu_x = q_avg * (roundedLength / 1000.0) * max(cantX - d / 2000.0, 0.0)  // kN
        val Vu_y = q_avg * (roundedWidth / 1000.0) * max(cantY - d / 2000.0, 0.0)  // kN
        // One-way shear capacity: Vc = 0.17 × √f'c × b × d (ACI 318-22.5.5.1)
        val fc_prime = 0.67 * fcu / GAMMA_C
        val vc_oneWay = 0.17 * sqrt(fc_prime)  // MPa
        val phi = 0.75
        val Vc_x = phi * vc_oneWay * (roundedLength / 1000.0) * d / 1000.0 * 1000.0  // kN
        val Vc_y = phi * vc_oneWay * (roundedWidth / 1000.0) * d / 1000.0 * 1000.0  // kN

        if (Vu_x > Vc_x) {
            warnings.add("SBC: One-way shear X exceeds capacity - increase thickness")
        }
        if (Vu_y > Vc_y) {
            warnings.add("SBC: One-way shear Y exceeds capacity - increase thickness")
        }

        // 8. فحص قص الاختراق
        val punchingCheck = checkPunchingShear(fcu, columnWidth, columnDepth, d, axialLoad, loadCombination)

        // 9. تصميم التسليح في الاتجاهين
        val reinfX = calculateFootingReinforcement(fcu, fy, roundedWidth, roundedLength, d, Mu_x / (roundedLength / 1000.0), FootingDirection.SHORT)
        val reinfY = calculateFootingReinforcement(fcu, fy, roundedWidth, roundedLength, d, Mu_y / (roundedWidth / 1000.0), FootingDirection.LONG)

        // 10. التسليح الرئيسي والتوزيعي
        val mainReinf = if (reinfX.astRequired >= reinfY.astRequired) reinfX else reinfY
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

        codeNotes.add("SBC 304-2018: Isolated Footing Design (ACI 318 based)")
        codeNotes.add(String.format("B=%.0fxL=%.0f mm, d=%.0f mm", roundedWidth, roundedLength, d))
        codeNotes.add(String.format("q_avg=%.1f, q_max=%.1f, q_min=%.1f kPa", q_avg, q_max, q_min))
        codeNotes.add(String.format("Mu_x=%.1f, Mu_y=%.1f kN.m", Mu_x, Mu_y))
        codeNotes.add(String.format("Short dir: %s", reinfX.barString))
        codeNotes.add(String.format("Long dir: %s", reinfY.barString))
        if (distBarsPerMeter > 0) {
            codeNotes.add(String.format("Distribution: %d Ø%d @ %dmm", 
                distBarsPerMeter, distBarDia.toInt(), distSpacing.toInt()
            ))
        }
        codeNotes.add(String.format("One-way shear capacity: %.2f MPa", vc_oneWay))

        return FootingDesignResult(
            requiredWidth = roundedWidth,
            requiredLength = roundedLength,
            requiredThickness = footingDepth,
            soilPressure = q_avg,
            maxSoilPressure = q_max,
            reinforcement = mainReinf,
            punchingShearCheck = punchingCheck,
            isSafe = q_max <= soilBearingCapacity && q_min >= 0
                && Vu_x <= Vc_x && Vu_y <= Vc_y
                && punchingCheck.isSafe,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    override fun checkPunchingShear(fcu: Double, columnWidth: Double, columnDepth: Double, effectiveDepth: Double, punchingShearForce: Double, loadCombination: LoadCombination): ShearCheckResult {
        // SBC 304-2018 / ACI 318: فحص قص الاختراق
        // محيط الاختراق عند بعد d/2 من وجه العمود
        val b0 = 2.0 * (columnWidth + columnDepth) + 4.0 * effectiveDepth
        // ACI 318-22: vc = min(0.17√f'c, 0.33√f'c, ...) - نستخدم الحد الأعلى
        // SBC 304 يستخدم f'c = 0.67×fcu/γc كإجهاد مكافئ
        val fc_prime = 0.67 * fcu / GAMMA_C
        // قدرة قص الاختراق: vc = 0.33 × √f'c (ACI 318 Eq. 22.6.5.2)
        val vc = 0.33 * sqrt(fc_prime)  // MPa
        // معامل المقاومة للقص φ = 0.75 (ACI 318)
        val phi = 0.75
        // القوة القاطعة المطبقة (خصم رد فعل التربة داخل المحيط)
        val punchingArea = (columnWidth + 2.0 * effectiveDepth) * (columnDepth + 2.0 * effectiveDepth)
        val V_net = punchingShearForce * 0.90  // 90% تقريباً من الحمل
        val vp_applied = (V_net * 1000.0) / (b0 * effectiveDepth)  // MPa

        val capacity = phi * vc * b0 * effectiveDepth / 1000.0  // kN
        val isSafe = vp_applied <= phi * vc

        return ShearCheckResult(
            appliedShear = vp_applied,
            shearCapacity = phi * vc,
            isSafe = isSafe,
            utilizationRatio = if (phi * vc > 0) vp_applied / (phi * vc) else 2.0,
            criticalPerimeter = b0,
            warnings = if (!isSafe) listOf(String.format("SBC: قص الاختراق %.2f > %.2f MPa - زِد السمك", vp_applied, phi * vc)) else emptyList()
        )
    }

    override fun calculateFootingReinforcement(fcu: Double, fy: Double, footingWidth: Double, footingLength: Double, effectiveDepth: Double, designMoment: Double, direction: FootingDirection): ReinforcementResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        
        // SBC 304 / ACI 318: K-method
        // K = Mu / (f'c × b × d²) - SBC uses f'c ≈ 0.67×fcu/γc (equivalent concrete stress)
        val Mu = designMoment * 1e6  // N.mm/m
        val b = 1000.0  // mm per meter width
        val d = effectiveDepth
        
        // ACI/SBC: K = Mu / (f'c × b × d²) حيث f'c = 0.67×fcu/γc
        val fc_prime = 0.67 * fcu / GAMMA_C
        val K = Mu / (fc_prime * b * d * d)
        
        // K_bal calculated dynamically using Rn method (ACI/SBC tension-controlled max)
        val fc = 0.67 * fcu / GAMMA_C
        val beta1 = if (fc <= 28.0) 0.85 else max(0.65, 0.85 - 0.05 * (fc - 28.0) / 7.0)
        val rho_bal = 0.85 * beta1 * (fc / fy) * (0.003 / (0.003 + fy / 200000.0))
        val K_bal = rho_bal * fy * (1.0 - 0.5 * rho_bal * fy / (0.85 * fc))
        
        if (K > K_bal) {
            warnings.add(String.format("SBC: K=%.3f > K_bal=%.3f - increase depth", K, K_bal))
        }
        
        // ذراع القوة (ACI approach): z = d × (0.5 + √(0.25 - K/1.25))
        val z = d * (0.5 + sqrt(max(0.0, 0.25 - K / 1.25)))
        
        // As = Mu / (fy/γs × z)
        val fs = fy / GAMMA_S
        val asRequired = Mu / (fs * z)
        
        // الحد الأدنى للتسليح (ACI 318-13.3.1): 0.18% × b × d
        val asMin = MIN_REIN_RATIO * b * d
        val asFinal = max(asRequired, asMin)
        
        // اختيار القضبان
        val availableBars = listOf(12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0)
        val barDiameter = availableBars.firstOrNull { dia ->
            val area = PI * dia * dia / 4.0
            val barsPerMeter = ceil(asFinal / area).toInt()
            barsPerMeter in 5..20
        } ?: 16.0
        
        val barArea = PI * barDiameter * barDiameter / 4.0
        val nominalBars = ceil(asFinal / barArea).toInt()
        val nominalSpacing = floor(1000.0 / nominalBars)
        
        // ACI 318: max spacing = min(3h, 450mm) for footings
        val maxSpacing = min(450.0, min(3.0 * d, 750.0))
        val finalSpacing = max(nominalSpacing, 100.0).coerceAtMost(maxSpacing)
        val actualBars = ceil(1000.0 / finalSpacing).toInt()
        val asProvided = actualBars * barArea
        
        val utilization = asRequired / asProvided
        codeNotes.add(String.format("SBC 304/ACI 318: %dØ%d @ %dmm", actualBars, barDiameter.toInt(), finalSpacing.toInt()))
        
        return ReinforcementResult(
            astRequired = asRequired,
            astProvided = asProvided,
            barDiameter = barDiameter,
            numberOfBars = actualBars,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = utilization <= 1.0 && K <= K_bal,
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
        // SBC 304 closely follows ACI 318
        val p1Working = axialLoad1 / loadCombination.getFactorForCode(DesignCode.SBC)
        val p2Working = axialLoad2 / loadCombination.getFactorForCode(DesignCode.SBC)
        val totalWorkingLoad = p1Working + p2Working
        
        val xResultant = (p2Working * distanceBetweenColumns) / totalWorkingLoad
        val s1 = 600.0 // Distance from edge to first column
        val footingLength = 2 * (xResultant + s1)
        
        val requiredArea = (totalWorkingLoad * 1.1) / soilBearingCapacity
        var footingWidth = (requiredArea * 1e6) / footingLength
        footingWidth = ceil(footingWidth / 50) * 50
        
        val actualArea = (footingLength * footingWidth) / 1e6
        val soilPressure = totalWorkingLoad / actualArea
        
        val effectiveDepth = footingDepth - getMinCover() - 10.0
        val qu_ultimate = (axialLoad1 + axialLoad2) / actualArea
        
        // Simplified moment for reinforcement (SBC/ACI)
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
        
        // SBC/ACI Punching check for estimated max column load
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
        
        // المسافة بين الركائز ≥ 3× قطر الركيزة (SBC 304 / ACI 318)
        val spacing = max(3.0 * pileDiameter, 750.0)
        val edgeDist = pileDiameter + 100.0
        
        val capW = ((rows - 1) * spacing + 2 * edgeDist).let { ceil(it / 50.0) * 50.0 }
        val capL = ((cols - 1) * spacing + 2 * edgeDist).let { ceil(it / 50.0) * 50.0 }
        
        // السمك الأدنى: max(spacing/3, 400mm)
        val minThickness = max(spacing / 3.0, 400.0)
        val capThickness = ceil(minThickness / 50.0) * 50.0
        val d = capThickness - getMinCover() - 10.0
        
        // فحص قص الاختراق عند العمود (SBC 304 / ACI 318-22.6.5)
        val colSize = 400.0
        val punchingResult = checkPunchingShear(
            fcu, colSize, colSize, d, columnLoads, LoadCombination.DEAD_LIVE
        )
        
        // فحص قص الركيزة
        val loadPerPile = columnLoads / numberOfPiles
        val pilePerimeter = PI * pileDiameter
        val pileShearStress = (loadPerPile * 1000.0) / (pilePerimeter * d)
        val fc_prime = 0.67 * fcu / GAMMA_C
        val pileShearCap = 0.75 * 0.33 * sqrt(fc_prime)
        
        if (pileShearStress > pileShearCap) {
            warnings.add("SBC: Pile shear exceeds capacity - increase cap thickness")
        }
        
        // عزم الانحناء
        val projection = (spacing / 2.0 + pileDiameter / 2.0 - colSize / 2.0) / 1000.0
        val Mu = loadPerPile * projection
        
        val reinf = calculateFootingReinforcement(
            fcu, fy, capW, capL, d, Mu / (capW / 1000.0), FootingDirection.SHORT
        )
        
        // فحص زاوية الخرسانة المضغوطة
        val strutAngle = atan2(d, projection * 1000.0) * 180.0 / PI
        if (strutAngle < 40.0) {
            warnings.add(String.format("SBC: Strut angle %.1f° < 40° - increase thickness", strutAngle))
        }
        
        codeNotes.add("SBC 304: Pile Cap (Strut-and-Tie Model)")
        codeNotes.add(String.format("Layout: %dx%d piles, Spacing: %.0fmm", rows, cols, spacing))
        codeNotes.add(String.format("Strut angle: %.1f°, Load/pile: %.0f kN", strutAngle, loadPerPile))
        
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
        // SBC 304 / ACI 318: φ × vc × bo × d
        val fc_prime = 0.67 * fcu / GAMMA_C
        val vc = 0.33 * sqrt(fc_prime)  // MPa
        val phi = 0.75
        return phi * vc * perimeter * effectiveDepth / 1000.0  // kN
    }
}
