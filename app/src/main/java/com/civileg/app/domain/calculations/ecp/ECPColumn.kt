package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.app.domain.entities.ColumnShearDesignResult
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult
import kotlin.math.*

class ECPColumn : ColumnDesign {
    
    companion object {
        private const val ALPHA = 0.8          // عامل اختزال الخرسانة
        private const val GAMMA_C = 1.5        // معامل أمان الخرسانة
        private const val GAMMA_S = 1.15       // معامل أمان الحديد
        private const val PHI_SHEAR = 0.75
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
        
        // ── لحظة: تحقق من العزوم الكبيرة وزيادة التسليح حسب اللامركزية ──
        val h = max(width, depth)
        if (eccentricity > 0.05 * h) {
            // Simplified interaction approach: if e > 0.05h, increase As by factor
            val momentFactor = max(1.0, 1.0 + 2.0 * eccentricity / h)
            requiredSteelArea *= momentFactor
            codeNotes.add("ECP 203: Significant moment (e=${"%.1f".format(eccentricity)}mm > 0.05h), As increased by factor ${"%.2f".format(momentFactor)}")
        }
        
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

    // ── Shear Design per ECP 203 §4-2-5 ────────────────────────────────────────

    /**
     * تصميم كانات القص للأعمدة — ECP 203 البند 4-2-5
     * @param Vu   factored shear force (kN)
     * @param width   column width b (mm)
     * @param depth   column depth h (mm)
     * @param fcu      concrete cube strength (MPa)
     * @param fy       steel yield strength (MPa)
     * @param cover    concrete cover (mm), default 40
     * @return ColumnShearDesignResult
     */
    fun calculateShearDesign(
        Vu: Double,
        width: Double,
        depth: Double,
        fcu: Double,
        fy: Double,
        cover: Double = 40.0
    ): ColumnShearDesignResult {
        val b = width
        val d = depth - cover  // effective depth (mm)
        val codeNotes = mutableListOf<String>()

        // Vc = 0.24 × √(fcu) × b × d / γc   (γc = 1.5)
        val Vc = 0.24 * sqrt(fcu) * b * d / GAMMA_C / 1000.0  // kN
        val phiVc = PHI_SHEAR * Vc

        val needsStirrups = Vu > phiVc

        // Asv/s = (Vu - φVc) / (φ × (fy/γs) × d)
        val fyDesign = fy / GAMMA_S  // MPa
        val requiredAsvPerS = if (needsStirrups) {
            (Vu - phiVc) * 1000.0 / (PHI_SHEAR * fyDesign * d)  // mm²/mm
        } else 0.0

        // Maximum spacing = min(15×db_tie, b, 300mm)
        val dbTie = 10.0  // assume 10mm tie as starting point
        val maxSpacing = minOf(15.0 * dbTie, b, 300.0)

        // Minimum Asv/s = 0.0025 × b × s  → Asv/s_min = 0.0025 × b (per mm)
        val minAsvPerS = 0.0025 * b

        val designAsvPerS = max(requiredAsvPerS, if (needsStirrups) minAsvPerS else 0.0)

        // Select stirrup diameter and spacing
        val availableTies = listOf(8.0, 10.0, 12.0, 16.0)
        var selectedDia = 8.0
        var selectedSpacing = maxSpacing

        if (designAsvPerS > 0) {
            for (dia in availableTies) {
                val asv = 2.0 * PI * dia * dia / 4.0  // 2 legs
                val spacing = asv / designAsvPerS  // mm
                if (spacing <= maxSpacing && spacing >= getMinSpacing()) {
                    selectedDia = dia
                    selectedSpacing = min(spacing, maxSpacing)
                    break
                }
                // If spacing < minSpacing, try larger dia
                if (spacing < getMinSpacing()) {
                    selectedDia = dia
                    selectedSpacing = getMinSpacing()
                }
            }
        }

        val providedAsvPerS = if (designAsvPerS > 0) {
            2.0 * PI * selectedDia * selectedDia / 4.0 / selectedSpacing
        } else 0.0

        val totalCapacity = phiVc + if (needsStirrups) PHI_SHEAR * fyDesign * providedAsvPerS * d / 1000.0 else 0.0
        val utilizationRatio = if (totalCapacity > 0) Vu / totalCapacity else 2.0

        codeNotes.add("ECP 203 §4-2-5: Column Shear Design")
        codeNotes.add("Vc = 0.24√fcu·b·d / γc = ${"%.1f".format(Vc)} kN")
        codeNotes.add("φVc = ${"%.1f".format(phiVc)} kN  (φ=${PHI_SHEAR})")
        if (needsStirrups) {
            codeNotes.add("Vu (${"%.1f".format(Vu)} kN) > φVc → Stirrups required")
            codeNotes.add("Asv/s = ${"%.3f".format(designAsvPerS)} mm²/mm")
            codeNotes.add("${selectedDia.toInt()}mm ties @ ${selectedSpacing.toInt()}mm c/c")
        } else {
            codeNotes.add("Vu (${"%.1f".format(Vu)} kN) ≤ φVc → Concrete alone sufficient")
        }

        return ColumnShearDesignResult(
            Vu = Vu,
            Vc = Vc,
            phiVc = phiVc,
            asvPerS = requiredAsvPerS,
            minAsvPerS = minAsvPerS,
            designAsvPerS = designAsvPerS,
            stirrupDiameter = selectedDia,
            stirrupSpacing = selectedSpacing,
            providedAsvPerS = providedAsvPerS,
            maxSpacing = maxSpacing,
            needsStirrups = needsStirrups,
            isSafe = Vu <= totalCapacity,
            utilizationRatio = utilizationRatio,
            codeNotes = codeNotes
        )
    }
}
