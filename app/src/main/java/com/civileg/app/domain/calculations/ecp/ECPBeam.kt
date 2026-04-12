package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.CodeReference
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult
import com.civileg.app.domain.entities.ShearReinforcementResult
import com.civileg.app.domain.entities.DeflectionCheckResult
import kotlin.math.*

class ECPBeam : BeamDesign {
    
    companion object {
        private const val GAMMA_C = 1.5      // معامل أمان الخرسانة
        private const val GAMMA_S = 1.15     // معامل أمان الحديد
        private const val BETA_1 = 0.8       // عامل كتلة الإجهاد
        private const val E_C = 4400.0       // معامل مرونة الخرسانة (تقريبي)
        private const val E_S = 200000.0     // معامل مرونة الحديد
    }

    override fun calculateFlexureReinforcement(
        fcu: Double,
        fy: Double,
        width: Double,
        effectiveDepth: Double,
        totalDepth: Double,
        designMoment: Double,
        loadCombination: LoadCombination
    ): ReinforcementResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        
        // تحويل العزم إلى نيوتن.مم
        val Mu = designMoment * 1e6 / loadCombination.factor
        
        // معاملات التصميم حسب الكود المصري
        val fc = 0.67 * fcu / GAMMA_C          // إجهاد الخرسانة المصمم
        val fs = fy / GAMMA_S                   // إجهاد الحديد المصمم
        
        // حساب معامل K
        val K = Mu / (fc * width * effectiveDepth * effectiveDepth)
        val K_bal = 0.3                          // قيمة K المتوازنة تقريباً
        
        // التحقق من أن المقطع غير مفرط التسليح
        if (K > K_bal) {
            warnings.add("Section is over-reinforced! Consider increasing dimensions")
            codeNotes.add(CodeReference.ECP.BEAM_REINFORCEMENT_MAX)
        }
        
        // حساب ذراع العزم الداخلي
        val leverArm = if (0.25 - K / 0.9 > 0) {
            effectiveDepth * (0.5 + sqrt(0.25 - K / 0.9))
        } else {
            effectiveDepth * 0.7 // Fallback for over-reinforced
        }
        
        // مساحة التسليح المطلوبة
        var astRequired = Mu / (fs * leverArm)
        
        // تطبيق حدود التسليح
        val Ag = width * effectiveDepth
        val minSteel = getMinReinforcementRatio() * Ag
        val maxSteel = getMaxReinforcementRatio() * Ag
        
        if (astRequired < minSteel) {
            astRequired = minSteel
            warnings.add("Minimum reinforcement applied per ${CodeReference.ECP.BEAM_REINFORCEMENT_MIN}")
        }
        
        // اختيار قطر حديد مناسب
        val availableBars = listOf(12.0, 16.0, 20.0, 22.0, 25.0)
        val barDiameter = availableBars.firstOrNull { 
            val area = PI * it * it / 4
            ceil(astRequired / area) <= 6  // أقصى 6 أسياخ في صف واحد
        } ?: 16.0
        
        val barArea = PI * barDiameter * barDiameter / 4
        val numberOfBars = ceil(astRequired / barArea).toInt().coerceIn(2, 12)
        val astProvided = numberOfBars * barArea
        
        // التحقق من التباعد بين الأسياخ
        val clearSpacing = if (numberOfBars > 1) {
            (width - 2 * getMinCover() - 2 * 10 - numberOfBars * barDiameter) / (numberOfBars - 1)
        } else {
            width - 2 * getMinCover()
        }
        
        if (clearSpacing < 25 || (numberOfBars > 1 && clearSpacing < barDiameter)) {
            warnings.add("Bar spacing may be insufficient - consider two layers")
        }
        
        // نسبة الاستغلال
        val capacity = calculateMomentCapacity(fcu, fy, width, effectiveDepth, astProvided)
        val utilizationRatio = if (capacity > 0) designMoment / capacity else 2.0
        
        codeNotes.add(CodeReference.ECP.BEAM_FLEXURE)
        codeNotes.add(CodeReference.ECP.BEAM_REINFORCEMENT_MIN)
        
        return ReinforcementResult(
            astRequired = astRequired,
            astProvided = astProvided,
            barDiameter = barDiameter,
            numberOfBars = numberOfBars,
            tiesDiameter = 0.0,  // ليس للكمرات
            tiesSpacing = 0.0,
            isSafe = utilizationRatio <= 1.0 && astRequired <= maxSteel,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    override fun calculateShearReinforcement(
        fcu: Double,
        fy: Double,
        width: Double,
        effectiveDepth: Double,
        designShear: Double,
        axialLoad: Double,
        loadCombination: LoadCombination
    ): ShearReinforcementResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        
        val Vu = designShear * 1000 / loadCombination.factor  // N
        
        // قدرة الخرسانة على تحمل القص حسب الكود المصري
        // qcu = 0.24 * sqrt(fcu/γc) / γc
        val qcu = 0.24 * sqrt(fcu / GAMMA_C) / GAMMA_C  // MPa
        val concreteShearCapacity = qcu * width * effectiveDepth / 1000  // kN
        
        // إذا كان القص أقل من قدرة الخرسانة، نضع تسليح أدنى
        val minStirrups = getMinShearReinforcementRatio() * width * 1000 // mm²/m
        
        var requiredStirrups = 0.0
        if (Vu / 1000 > concreteShearCapacity) {
            // حساب تسليح القص المطلوب
            val excessShear = (Vu / 1000 - concreteShearCapacity) * 1000 // N
            requiredStirrups = excessShear / (fy / GAMMA_S * effectiveDepth) * 1000  // mm²/m
            requiredStirrups = max(requiredStirrups, minStirrups)
        } else {
            requiredStirrups = minStirrups
            warnings.add("Shear reinforcement at minimum per code")
        }
        
        // اختيار قطر الكانة (8, 10, 12 مم)
        val stirrupDiameter = if (width < 250) 8.0 else 10.0
        val stirrupArea = 2 * PI * stirrupDiameter * stirrupDiameter / 4  // كانة مغلقة = فرعين
        
        // حساب التباعد
        var stirrupSpacing = if (requiredStirrups > 0) stirrupArea * 1000 / requiredStirrups else getMaxShearSpacing()
        stirrupSpacing = stirrupSpacing.coerceIn(50.0, getMaxShearSpacing())
        
        // التحقق من الحد الأقصى للقص
        val maxShearStress = 0.7 * sqrt(fcu / GAMMA_C) / GAMMA_C  // MPa
        val maxShearCapacity = maxShearStress * width * effectiveDepth / 1000  // kN
        val isSafe = (Vu / 1000) <= maxShearCapacity
        
        if (!isSafe) {
            warnings.add("WARNING: Shear stress exceeds maximum limit! Increase section or concrete strength")
        }
        
        codeNotes.add(CodeReference.ECP.BEAM_SHEAR)
        codeNotes.add("qcu = ${"%.3f".format(qcu)} MPa")
        
        return ShearReinforcementResult(
            concreteShearCapacity = concreteShearCapacity,
            requiredShearReinforcement = requiredStirrups,
            providedShearReinforcement = stirrupArea * 1000 / stirrupSpacing,
            stirrupDiameter = stirrupDiameter,
            stirrupSpacing = stirrupSpacing,
            isSafe = isSafe,
            utilizationRatio = if (maxShearCapacity > 0) (Vu / 1000) / maxShearCapacity else 2.0,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    override fun checkDeflection(
        span: Double,
        totalDepth: Double,
        reinforcementRatio: Double,
        supportCondition: SupportCondition
    ): DeflectionCheckResult {
        // طريقة النسبة (Span/Depth) المبسطة حسب الكود المصري
        val basicRatio = when (supportCondition) {
            SupportCondition.SIMPLY_SUPPORTED -> 20.0
            SupportCondition.CONTINUOUS -> 26.0
            SupportCondition.CANTILEVER -> 7.0
        }
        
        // تعديل النسبة حسب نسبة التسليح
        val modificationFactor = 0.55 + 0.0075 * (400.0 / reinforcementRatio.coerceAtLeast(0.01)).coerceIn(0.0, 10.0)
        val allowableRatio = basicRatio * modificationFactor
        
        val actualRatio = (span * 1000) / totalDepth  // تحويل span إلى مم
        val ratio = actualRatio / allowableRatio
        
        val calculatedDeflection = if (ratio > 1.0) {
            // تقدير تقريبي للانحراف الزائد
            (span * 1000) / 250 * ratio  // mm
        } else {
            (span * 1000) / 250  // الانحراف المسموح الأساسي
        }
        
        val allowableDeflection = getDeflectionLimit(span)
        
        return DeflectionCheckResult(
            calculatedDeflection = calculatedDeflection,
            allowableDeflection = allowableDeflection,
            ratio = ratio,
            isSafe = ratio <= 1.0,
            recommendation = if (ratio > 1.0) 
                "Increase depth to ${((span * 1000) / allowableRatio * 1.1).toInt()} mm minimum" 
                else "Deflection OK"
        )
    }

    override fun calculateDevelopmentLength(
        barDiameter: Double,
        fy: Double,
        fcu: Double,
        barLocation: BarLocation,
        coating: CoatingType
    ): Double {
        // حسب الكود المصري: Ld = (fy/γs) * φ / (4 * fb)
        // حيث fb = إجهاد التماسك
        
        val fs = fy / GAMMA_S
        val fb = when {
            fcu <= 30 -> 1.0 + 0.25 * (fcu - 20) / 10
            else -> 1.5 + 0.1 * (fcu - 30) / 40
        } * sqrt(fcu / GAMMA_C) / 10  // MPa
        
        var Ld = fs * barDiameter / (4 * fb.coerceAtLeast(0.1))
        
        // عوامل التعديل
        if (barLocation == BarLocation.TOP) Ld *= 1.3  // حديد علوي
        if (coating == CoatingType.EPOXY_COATED) Ld *= 1.2
        
        // حد أدنى
        Ld = max(Ld, 350.0)  // 350 مم كحد أدنى
        
        // تقريب لأعلى لأقرب 50 مم
        return ceil(Ld / 50) * 50
    }

    override fun getMinReinforcementRatio(): Double = 0.005  // 0.5% للكمرات
    override fun getMaxReinforcementRatio(): Double = 0.04   // 4%
    override fun getMinShearReinforcementRatio(): Double = 0.0015  // 0.15%
    override fun getMaxShearSpacing(): Double = 200.0  // 200 مم كحد أقصى للكانات
    override fun getMinCover(): Double = 40.0
    
    override fun getDeflectionLimit(span: Double): Double {
        // الانحراف المسموح: L/250 للأحمال الكلية
        return (span * 1000) / 250
    }
    
    // دالة مساعدة لحساب قدرة العزم
    private fun calculateMomentCapacity(
        fcu: Double, fy: Double,
        width: Double, effectiveDepth: Double,
        ast: Double
    ): Double {
        val fc = 0.67 * fcu / GAMMA_C
        val fs = fy / GAMMA_S
        
        // عمق كتلة الإجهاد
        val a = (ast * fs) / (fc * width)
        val leverArm = effectiveDepth - a / 2
        
        // قدرة العزم
        val Mn = ast * fs * leverArm
        return Mn / 1e6  // kN.m
    }
}
