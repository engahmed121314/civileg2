package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.core.calculations.entities.ColumnShearDesignResult
import com.civileg.core.calculations.entities.LoadCombination
import com.civileg.core.calculations.entities.ReinforcementResult
import kotlin.math.*

class ECPColumn : ColumnDesign {
    
    companion object {
        private const val ALPHA = 0.8          // معامل اللامركزية الصغرى للأعمدة المربوطة (ECP 203 §4-2-2-2)
        private const val PHI_AXIAL = 0.65     // معامل اختزال المقاومة للضغط - أعمدة مربوطة (ECP 203 §4-2-2-2)
        private const val GAMMA_C = 1.5        // معامل أمان الخرسانة (ECP 203 §2-3-1)
        private const val GAMMA_S = 1.15       // معامل أمان الحديد (ECP 203 §2-3-1)
        // ملاحظة: ALPHA ≠ PHI_AXIAL — الأول يعوض اللامركزية العرضية الصغرى، والثاني اختزال مقاومة.
        // كلاهما إلزامي في ECP 203: Pu = φ × [α × (0.67fcu/γc×(Ag−Ast) + fy/γs×Ast)]

        /** 
         * W9-FIX: ECP 203 §4-2-3: maximum longitudinal reinforcement ratio
         * is 6% for general section, but 4% is common in lap locations. 
         */
        const val MAX_REINFORCEMENT_RATIO = 0.06
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
        val Ast = reinforcementArea.coerceAtMost(Ag * MAX_REINFORCEMENT_RATIO) // حد أقصى 6%
        
        // مقاومة الخرسانة: 0.67 * fcu / γc
        val concreteStress = 0.67 * fcu / GAMMA_C
        // مقاومة الحديد: fy / γs
        val steelStress = fy / GAMMA_S
        
        // القدرة التصميمية: Pu = φ × α × [0.67×fcu/γc × (Ag-Ast) + fy/γs × Ast]
        // (ECP 203-2020 §4-2-2-2: φ=0.65 مربوطة + α=0.8 للامركزية الصغرى)
        val concreteCapacity = concreteStress * (Ag - Ast)
        val steelCapacity = steelStress * Ast
        val designCapacity = PHI_AXIAL * ALPHA * (concreteCapacity + steelCapacity)
        
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
        com.civileg.app.domain.calculations.InputGuard.positive(
            "fcu" to fcu, "fy" to fy, "width" to width, "depth" to depth
        )
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
        val tiesSpacing = calculateTiesSpacing(barDiameter, tiesDiameter, width, depth)
        
        // حساب نسبة الاستغلال
        val capacity = calculateAxialCapacity(fcu, fy, width, depth, astProvided, loadCombination)
        val utilizationRatio = if (capacity > 0) axialLoad / capacity else 2.0
        
        // ملاحظات الكود
        codeNotes.add("ECP 203-2020: Section 4-2-3")
        codeNotes.add("Cover: ${getMinCover()}mm minimum")
        if (eccentricity > minEccentricity) {
            codeNotes.add("Eccentricity check: e=${"%.1f".format(eccentricity)}mm > emin=${minEccentricity}mm")
        }
        
        // [Phase 3] Capture Calculation Trace for Transparency
        val traceSteps = mutableListOf<com.civileg.core.calculations.entities.CalculationStep>()
        traceSteps.add(com.civileg.core.calculations.entities.CalculationStep(
            "Concrete Area (Ag)", "Ag = b * h", "$width * $depth", width * depth, "mm2"
        ))
        traceSteps.add(com.civileg.core.calculations.entities.CalculationStep(
            "Reinforcement Ratio (\u03C1)", "\u03C1 = As / Ag", "$astProvided / ${width*depth}", astProvided / (width * depth), ""
        ))
        traceSteps.add(com.civileg.core.calculations.entities.CalculationStep(
            "Axial Capacity (Pu)", "Pu = (0.35*fcu*Ac + 0.67*fy*As)/1000", "(0.35*$fcu*Ac + 0.67*$fy*$astProvided)/1000", capacity, "kN"
        ))

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
            codeNotes = codeNotes,
            trace = com.civileg.core.calculations.entities.DesignTrace(traceSteps)
        )
    }

    private fun calculateTiesSpacing(barDiameter: Double, tiesDiameter: Double, width: Double, depth: Double): Double {
        // حسب الكود المصري ECP 203 البند 4-2-6: أقل من (16×قطر السيخ، 48×قطر الكانة، أقل بعد في المقطع، 300 مم)
        return minOf(16 * barDiameter, 48 * tiesDiameter, width, depth, 300.0).coerceIn(getMinSpacing(), getMaxSpacing())
    }

    override fun getMinReinforcementRatio(): Double = 0.008  // 0.8%
    override fun getMaxReinforcementRatio(): Double = 0.06  // ECP 203 §4-2-3 general-section max (6%)
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

        // Vc = 0.24 × √(fcu/γc) × b × d   (ECP 203 §4-3-1-2)
        val Vc = 0.24 * sqrt(fcu / GAMMA_C) * b * d / 1000.0  // kN

        val needsStirrups = Vu > Vc

        // Asv/s = (Vu - Vc) / ((fy/γs) × d)  — بدون φ إضافي
        val fyDesign = fy / GAMMA_S  // MPa
        val requiredAsvPerS = if (needsStirrups) {
            (Vu - Vc) * 1000.0 / (fyDesign * d)  // mm²/mm
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

        val totalCapacity = Vc + if (needsStirrups) fyDesign * providedAsvPerS * d / 1000.0 else 0.0
        val utilizationRatio = if (totalCapacity > 0) Vu / totalCapacity else 2.0

        codeNotes.add("ECP 203 §4-2-5: Column Shear Design")
        codeNotes.add("Vc = 0.24√fcu·b·d / γc = ${"%.1f".format(Vc)} kN  (design capacity, γc=${GAMMA_C})")
        if (needsStirrups) {
            codeNotes.add("Vu (${"%.1f".format(Vu)} kN) > Vc → Stirrups required")
            codeNotes.add("Asv/s = ${"%.3f".format(designAsvPerS)} mm²/mm")
            codeNotes.add("${selectedDia.toInt()}mm ties @ ${selectedSpacing.toInt()}mm c/c")
        } else {
            codeNotes.add("Vu (${"%.1f".format(Vu)} kN) ≤ Vc → Concrete alone sufficient")
        }

        return ColumnShearDesignResult(
            Vu = Vu,
            Vc = Vc,
            phiVc = Vc,  // ECP 203: no φ factor — phiVc equals Vc (kept for data class compat)
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