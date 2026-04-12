package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.CodeReference
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult
import com.civileg.app.domain.entities.ShearCheckResult
import kotlin.math.*

class ECPFooting : FootingDesign {
    
    companion object {
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15
    }

    override fun designIsolatedFooting(
        fcu: Double,
        fy: Double,
        columnWidth: Double,      // mm
        columnDepth: Double,      // mm
        axialLoad: Double,        // kN
        momentX: Double,          // kN.m
        momentY: Double,          // kN.m
        soilBearingCapacity: Double, // kPa
        footingDepth: Double,     // mm - عمق التأسيس
        loadCombination: LoadCombination
    ): FootingDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        
        // حساب مساحة القاعدة المطلوبة (Working Load)
        val totalLoad = axialLoad / loadCombination.factor
        var requiredArea = if (soilBearingCapacity > 0) totalLoad / soilBearingCapacity else 1.0 // m²
        
        // إضافة وزن القاعدة التقريبي (حوالي 10% من الحمل)
        requiredArea *= 1.1
        
        // أبعاد القاعدة (مستطيلة بنفس نسبة العمود لتقليل العزوم غير المتساوية)
        val columnRatio = if (columnWidth > 0) columnDepth / columnWidth else 1.0
        var footingWidth = sqrt(requiredArea / columnRatio.coerceAtLeast(0.1)) * 1000  // mm
        var footingLength = footingWidth * columnRatio
        
        // تقريب لأقرب 50 مم
        footingWidth = ceil(footingWidth / 50) * 50
        footingLength = ceil(footingLength / 50) * 50
        
        // ضغط التربة الفعلي (Working)
        val actualArea = (footingWidth * footingLength) / 1e6  // m²
        val soilPressure = if (actualArea > 0) totalLoad / actualArea else 0.0
        
        // حساب أقصى ضغط مع العزوم (P/A + M/Z)
        val Mx = abs(momentX / loadCombination.factor)
        val My = abs(momentY / loadCombination.factor)
        
        val Zx = (footingWidth * footingLength * footingLength) / 6e9 // m³
        val Zy = (footingLength * footingWidth * footingWidth) / 6e9 // m³
        
        val maxSoilPressure = soilPressure + (if (Zx > 0) Mx / Zx else 0.0) + (if (Zy > 0) My / Zy else 0.0)
        
        if (maxSoilPressure > soilBearingCapacity * 1.2) {
            warnings.add("Maximum soil pressure exceeds allowable limit (Allowable + 20%)")
        }
        
        // حساب العزوم التصميمية (Ultimate) عند وجه العمود
        // Qu = P_ultimate / Area
        val Qu = axialLoad / actualArea.coerceAtLeast(0.1) // kPa
        
        // العزم في الاتجاه الطويل (حول محور Y)
        val cantileverLengthL = (footingLength - columnDepth) / 2000.0 // m
        val designMomentL = Qu * cantileverLengthL * cantileverLengthL / 2.0 // kN.m/m
        
        // العزم في الاتجاه القصير (حول محور X)
        val cantileverLengthW = (footingWidth - columnWidth) / 2000.0 // m
        val designMomentW = Qu * cantileverLengthW * cantileverLengthW / 2.0 // kN.m/m
        
        val effectiveDepth = footingDepth - getMinCover() - 10.0 // mm
        
        // التحقق من الاختراق (Punching)
        val punchingCheck = checkPunchingShear(
            fcu, columnWidth, columnDepth, effectiveDepth,
            axialLoad, loadCombination
        )
        
        // حساب التسليح (يستخدم الأكبر من الاتجاهين)
        val reinforcement = calculateFootingReinforcement(
            fcu, fy, footingWidth, footingLength, effectiveDepth,
            max(designMomentL, designMomentW), FootingDirection.LONG
        )
        
        codeNotes.add(CodeReference.ECP.FOOTING_BEARING)
        codeNotes.add(CodeReference.ECP.FOOTING_SHEAR_PUNCHING)
        
        return FootingDesignResult(
            requiredWidth = footingWidth,
            requiredLength = footingLength,
            requiredThickness = max(footingDepth, getMinFootingThickness()),
            soilPressure = soilPressure,
            maxSoilPressure = maxSoilPressure,
            reinforcement = reinforcement,
            punchingShearCheck = punchingCheck,
            isSafe = maxSoilPressure <= soilBearingCapacity * 1.2 && punchingCheck.isSafe,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    override fun checkPunchingShear(
        fcu: Double,
        columnWidth: Double,
        columnDepth: Double,
        effectiveDepth: Double,
        punchingShearForce: Double, // kN (Ultimate)
        loadCombination: LoadCombination
    ): ShearCheckResult {
        // محيط القص الحرج: على بعد d/2 من جميع الأوجه
        val b0 = 2 * (columnWidth + effectiveDepth) + 2 * (columnDepth + effectiveDepth)
        
        // مساحة الاختراق
        val punchingArea = (columnWidth + effectiveDepth) * (columnDepth + effectiveDepth) / 1e6 // m²
        
        // قوة الاختراق الفعلية = القوة الكلية - رد فعل التربة داخل مساحة الاختراق
        val actualPunchingForce = punchingShearForce * (1 - punchingArea / 10.0) // تقريب
        
        // قدرة القص للاختراق (الأصغر من 4 قيم حسب الكود المصري)
        val q1 = 0.316 * sqrt(fcu / GAMMA_C) * (0.5 + min(columnWidth, columnDepth) / max(columnWidth, columnDepth)) / GAMMA_C
        val q2 = 0.316 * sqrt(fcu / GAMMA_C) * (0.2 + (effectiveDepth * b0 / 1e6)) / GAMMA_C // مبسطة
        val q3 = 1.6 / GAMMA_C // MPa
        val q4 = 0.8 * sqrt(fcu / GAMMA_C) / GAMMA_C
        
        val qcup = minOf(q1, q2, q3, q4)
        val shearCapacity = qcup * b0 * effectiveDepth / 1000.0 // kN
        
        val utilizationRatio = if (shearCapacity > 0) actualPunchingForce / shearCapacity else 2.0
        
        return ShearCheckResult(
            appliedShear = actualPunchingForce,
            shearCapacity = shearCapacity,
            utilizationRatio = utilizationRatio,
            isSafe = utilizationRatio <= 1.0,
            criticalPerimeter = b0,
            warnings = if (! (utilizationRatio <= 1.0)) listOf("Punching shear fails! Increase footing thickness") else emptyList()
        )
    }

    override fun calculateFootingReinforcement(
        fcu: Double,
        fy: Double,
        footingWidth: Double,
        footingLength: Double,
        effectiveDepth: Double,
        designMoment: Double, // kN.m/m
        direction: FootingDirection
    ): ReinforcementResult {
        val width = 1000.0 // تصميم لشريحة 1 متر
        val Mu = designMoment * 1e6 // N.mm
        val fc = 0.67 * fcu / GAMMA_C
        val fs = fy / GAMMA_S
        
        val K = if (fc > 0 && effectiveDepth > 0) Mu / (fc * width * effectiveDepth * effectiveDepth) else 0.0
        val leverArm = if (0.25 - K / 0.9 > 0) {
            effectiveDepth * (0.5 + sqrt(0.25 - K / 0.9))
        } else {
            effectiveDepth * 0.7
        }
        
        var astRequired = if (fs > 0 && leverArm > 0) Mu / (fs * leverArm) else 0.0
        
        // الحد الأدنى للتسليح (0.15% من مساحة المقطع الخرساني للقواعد)
        val minSteel = 0.0015 * width * (effectiveDepth + getMinCover())
        astRequired = max(astRequired, minSteel)
        
        // اختيار قطر السيخ (عادة 12 أو 16 مم للقواعد)
        val barDiameter = 16.0
        val barArea = PI * barDiameter * barDiameter / 4.0
        val barSpacing = (barArea * 1000.0 / astRequired).coerceIn(100.0, 200.0)
        val astProvided = barArea * 1000.0 / barSpacing
        
        val totalLength = if (direction == FootingDirection.LONG) footingWidth else footingLength
        val numberOfBars = (totalLength / barSpacing).toInt()
        
        return ReinforcementResult(
            astRequired = astRequired,
            astProvided = astProvided,
            barDiameter = barDiameter,
            numberOfBars = numberOfBars,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = true,
            utilizationRatio = if (astProvided > 0) astRequired / astProvided else 2.0,
            codeNotes = listOf(CodeReference.ECP.FOOTING_REINFORCEMENT)
        )
    }

    override fun getMinFootingThickness(): Double = 400.0 // 400 مم للقواعد المسلحة
    override fun getMinCover(): Double = 50.0 // 50-70 مم للأجزاء الملامسة للتربة
    override fun getPunchingShearCapacity(fcu: Double, perimeter: Double, effectiveDepth: Double): Double {
        val qcup = 0.8 * sqrt(fcu / GAMMA_C) / GAMMA_C
        return qcup * perimeter * effectiveDepth / 1000.0
    }
}
