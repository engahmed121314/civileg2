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
        
        // القدرة المحورية: Pu = α × [0.67×fcu/γc × (Ag-Ast) + fy/γs × Ast]
        // ECP 203 يستخدم γc و γs فقط - لا يوجد معامل φ منفصل
        val concreteCapacity = concreteStress * (Ag - Ast)
        val steelCapacity = steelStress * Ast
        val designCapacity = ALPHA * (concreteCapacity + steelCapacity)
        
        // التحويل من نيوتن إلى كيلو نيوتن
        return designCapacity / 1000.0
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
        // Pu: الحمل المحوري التصميمي (N) - نستخدمه مباشرة بدون قسمة
        val Pu = axialLoad * 1000.0  // N
        val Mu = sqrt(momentX.pow(2) + momentY.pow(2)) * 1e6 // N.mm
        
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // حساب العزم اللامركزي
        val eccentricity = if (Pu > 0) Mu / Pu else 0.0
        // ECP 203: e_min = max(20mm, b/20, h/20)
        val minEccentricity = maxOf(20.0, width / 20.0, depth / 20.0)
        
        // طريقة مبسطة لحساب التسليح (لأعمدة قصيرة)
        // ECP 203: Pu = α × [0.67×fcu/γc×(Ag-Ast) + fy/γs×Ast]
        // بدون معامل φ إضافي (ECP يستخدم γ فقط)
        val concreteStress = 0.67 * fcu / GAMMA_C
        val steelStress = fy / GAMMA_S
        
        // نحل المعادلة لإيجاد Ast المطلوبة
        // Pu = α(concreteStress × (Ag-Ast) + steelStress × Ast)
        // Pu/α = concreteStress×Ag - concreteStress×Ast + steelStress×Ast
        // Pu/α - concreteStress×Ag = Ast×(steelStress - concreteStress)
        val numerator = Pu / ALPHA - concreteStress * Ag
        val denominator = steelStress - concreteStress
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
        // حسب الكود المصري ECP 203 البند 4-2-6: أقل من (16×قطر السيخ، أقل بعد في المقطع، 300 مم)
        return minOf(16 * barDiameter, width, depth, 300.0).coerceIn(getMinSpacing(), getMaxSpacing())
    }

    override fun getMinReinforcementRatio(): Double = 0.008  // 0.8%
    override fun getMaxReinforcementRatio(): Double = 0.08   // 8% per ECP 203-2020 Section 4-2-3 (4% typical, 6% at splices, 8% max)
    override fun getMinSpacing(): Double = 100.0
    override fun getMaxSpacing(): Double = 300.0
    override fun getMinCover(): Double = 40.0
}
