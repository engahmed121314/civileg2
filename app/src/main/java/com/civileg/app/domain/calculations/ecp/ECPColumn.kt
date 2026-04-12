package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult
import kotlin.math.*

class ECPColumn : ColumnDesign {
    
    companion object {
        private const val ALPHA = 0.8          // عامل اختزال الخرسانة
        private const val GAMMA_C = 1.5        // معامل أمان الخرسانة
        private const val GAMMA_S = 1.15       // معامل أمان الحديد
    }

    override fun calculateAxialCapacity(
        fcu: Double,
        fy: Double,
        width: Double,
        depth: Double,
        reinforcementArea: Double,
        loadCombination: LoadCombination
    ): Double {
        val Ag = width * depth                          // مساحة المقطع الكلية (mm²)
        val Ast = reinforcementArea.coerceAtMost(Ag * 0.08) // حد أقصى 8%
        
        // مقاومة الخرسانة: 0.67 * fcu / γc
        val concreteStress = 0.67 * fcu / GAMMA_C
        // مقاومة الحديد: fy / γs
        val steelStress = fy / GAMMA_S
        
        // القدرة المحورية: α(0.67*fcu/γc * (Ag-Ast) + fy/γs * Ast)
        val concreteCapacity = concreteStress * (Ag - Ast)
        val steelCapacity = steelStress * Ast
        val nominalCapacity = ALPHA * (concreteCapacity + steelCapacity)
        
        // معامل الاختزال φ حسب نوع التحميل
        val phi = when (loadCombination) {
            LoadCombination.DEAD_ONLY -> 0.65
            LoadCombination.DEAD_LIVE -> 0.65
            LoadCombination.DEAD_LIVE_EARTHQUAKE -> 0.75
            else -> 0.65
        }
        
        // التحويل من نيوتن إلى كيلو نيوتن
        return phi * nominalCapacity / 1000.0
    }

    override fun calculateReinforcement(
        fcu: Double,
        fy: Double,
        width: Double,
        depth: Double,
        axialLoad: Double,
        momentX: Double,
        momentY: Double,
        loadCombination: LoadCombination
    ): ReinforcementResult {
        val Ag = width * depth
        val Pu = axialLoad * 1000.0 / loadCombination.factor // تحويل إلى نيوتن مع عامل التحميل
        val Mu = sqrt(momentX.pow(2) + momentY.pow(2)) * 1e6 // N.mm
        
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // حساب العزم اللامركزي
        val eccentricity = if (Pu > 0) Mu / Pu else 0.0
        val minEccentricity = max(20.0, width / 20) // أقل لا مركزية 20 مم أو b/20
        
        // طريقة مبسطة لحساب التسليح (لأعمدة قصيرة)
        val phi = 0.65
        val concreteStress = 0.67 * fcu / GAMMA_C
        val steelStress = fy / GAMMA_S
        
        // معادلة تقريبية: Pu = φ[α(0.67fcu/γc(Ag-Ast) + fy/γs*Ast)]
        // نحلها لإيجاد Ast المطلوبة
        val numerator = Pu / phi - ALPHA * concreteStress * Ag
        val denominator = ALPHA * (steelStress - concreteStress)
        var requiredSteelArea = if (denominator != 0.0) numerator / denominator else 0.0
        
        // تطبيق حدود التسليح
        val minSteel = getMinReinforcementRatio() * Ag
        val maxSteel = getMaxReinforcementRatio() * Ag
        
        if (requiredSteelArea < minSteel) {
            requiredSteelArea = minSteel
            warnings.add("Minimum reinforcement applied")
        }
        
        if (requiredSteelArea > maxSteel) {
            warnings.add("WARNING: Reinforcement exceeds maximum limit! Consider increasing section size.")
        }
        
        // اختيار قطر حديد مناسب (12, 16, 20, 22, 25 مم)
        val availableBars = listOf(12.0, 16.0, 20.0, 22.0, 25.0)
        val barDiameter = availableBars.firstOrNull { 
            val area = PI * it * it / 4
            ceil(requiredSteelArea / area) <= 12 // أقصى 12 سيخ في الوجه
        } ?: 16.0
        
        val barArea = PI * barDiameter * barDiameter / 4
        val numberOfBars = ceil(requiredSteelArea / barArea).toInt().coerceIn(4, 32)
        val astProvided = numberOfBars * barArea
        
        // حساب الكانات
        val tiesDiameter = max(10.0, barDiameter / 4).coerceAtLeast(8.0)
        val tiesSpacing = calculateTiesSpacing(barDiameter, width, depth)
        
        // حساب نسبة الاستغلال
        val capacity = calculateAxialCapacity(fcu, fy, width, depth, astProvided, loadCombination)
        val utilizationRatio = if (capacity > 0) axialLoad / capacity else 2.0
        
        // ملاحظات الكود
        codeNotes.add("ECP 203-2020: Section 4-2-3")
        codeNotes.add("Cover: ${getMinCover()}mm minimum")
        if (eccentricity > minEccentricity) {
            codeNotes.add("Eccentricity check: e=${"%.1f".format(eccentricity)}mm > emin=${minEccentricity}mm")
        }
        
        return ReinforcementResult(
            astRequired = requiredSteelArea,
            astProvided = astProvided,
            barDiameter = barDiameter,
            numberOfBars = numberOfBars,
            tiesDiameter = tiesDiameter,
            tiesSpacing = tiesSpacing,
            isSafe = utilizationRatio <= 1.0 && requiredSteelArea <= maxSteel,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    private fun calculateTiesSpacing(barDiameter: Double, width: Double, depth: Double): Double {
        // حسب الكود المصري: أقل من (15*قطر السيخ، أقل بعد في المقطع، 200 مم)
        return minOf(15 * barDiameter, width, depth, 200.0).coerceIn(getMinSpacing(), getMaxSpacing())
    }

    override fun getMinReinforcementRatio(): Double = 0.008  // 0.8%
    override fun getMaxReinforcementRatio(): Double = 0.04   // 4% for interior columns
    override fun getMinSpacing(): Double = 100.0
    override fun getMaxSpacing(): Double = 200.0
    override fun getMinCover(): Double = 40.0
}
