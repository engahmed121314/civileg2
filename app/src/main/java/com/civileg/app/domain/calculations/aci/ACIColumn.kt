package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.app.domain.entities.ColumnShearDesignResult
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult
import kotlin.math.*

class ACIColumn : ColumnDesign {
    
    companion object {
        private const val BETA_1 = 0.85  // Concrete stress block factor
        private const val PHI_TIED = 0.65     // معامل الاختزال للأعمدة المربوطة (ACI 318-21.2.1)
        private const val PHI_SPIRAL = 0.75   // معامل الاختزال للأعمدة الحلزونية (ACI 318-21.2.1)
    }

    override fun calculateAxialCapacity(
        fcu: Double,
        fy: Double,
        width: Double,
        depth: Double,
        reinforcementArea: Double,
        loadCombination: LoadCombination
    ): Double {
        val Ag = width * depth
        val Ast = reinforcementArea.coerceAtMost(Ag * 0.08)
        
        // ACI 318: Pn = 0.85*fc'*(Ag-Ast) + fy*Ast
        // fc' = 0.8 × fcu (cube to cylinder conversion)
        val fc_prime = 0.8 * fcu
        val concreteCapacity = 0.85 * fc_prime * (Ag - Ast)
        val steelCapacity = fy * Ast
        val nominalCapacity = concreteCapacity + steelCapacity
        
        // معامل الاختزال حسب نوع التسليح (ACI 318-21.2.1)
        // φ = 0.65 للأعمدة المربوطة (Tied Columns)
        // φ = 0.75 للأعمدة الحلزونية (Spiral Columns) - زيادة 15% بسبب أداء أفضل
        // يُفترض φ = 0.65 كافتراضي (يُحدد نوع العمود من التسليح الخارجي)
        val phi = PHI_TIED
        return phi * nominalCapacity / 1000.0 // kN
    }
    
    /**
     * حساب القدرة المحورية مع اختيار نوع العمود (مربوط/حلزوني)
     * ACI 318-21.2.1: φ=0.65 (tied) أو φ=0.75 (spiral)
     */
    fun calculateAxialCapacityWithPhi(
        fcu: Double, fy: Double, width: Double, depth: Double,
        reinforcementArea: Double, isSpiral: Boolean
    ): Double {
        val Ag = width * depth
        val Ast = reinforcementArea.coerceAtMost(Ag * 0.08)
        val fc_prime = 0.8 * fcu
        val concreteCapacity = 0.85 * fc_prime * (Ag - Ast)
        val steelCapacity = fy * Ast
        val nominalCapacity = concreteCapacity + steelCapacity
        val phi = if (isSpiral) PHI_SPIRAL else PHI_TIED
        return phi * nominalCapacity / 1000.0
    }
    
    /**
     * حساب نسبة النحافة ونسبة التحميل المسموحة
     * ACI 318-22.4: λ = KL/r, Pn = Fcr × Ag
     */
    fun calculateSlendernessEffect(
        unsupportedLength: Double, // m
        effectiveLengthFactor: Double, // K
        width: Double, depth: Double, // mm
        fcu: Double, reinforcementRatio: Double = 0.01
    ): Triple<Double, Boolean, Double> {
        val Ag = width * depth
        // نصف القطر الدوراني (المحور الأضع)
        val minDimension = min(width, depth)
        val r = minDimension / sqrt(12.0)
        val L_mm = unsupportedLength * 1000.0
        val slendernessRatio = effectiveLengthFactor * L_mm / r
        
        // ACI 318-22.4.2.1: العمود قصير إذا λ ≤ 22 (مربوط) أو λ ≤ 28 (حلزوني)
        val isShort = slendernessRatio <= 22.0
        
        // حساب إجهاد الانبعاج الحرج Fcr
        val fc_prime = 0.8 * fcu
        val EI = 0.4 * 4700.0 * sqrt(fc_prime) * (Ag / 2.0) +  // تقريبي
                200000.0 * reinforcementRatio * Ag * (minDimension * 0.4).pow(2)
        val PI2_EI = PI * PI * EI / 1000000.0  // kN.m²
        val Pc = PI2_EI / (unsupportedLength * unsupportedLength)
        
        return Triple(slendernessRatio, !isShort, Pc)
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
        val Pu = axialLoad * 1000.0  // N - الحمل المحوري التصميمي (مضروب في معامل التحميل بالفعل)
        
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // ACI 318-19: Simplified reinforcement calculation
        // fc' = 0.8 × fcu (cube to cylinder)
        val fc_prime = 0.8 * fcu
        val phi = 0.65
        var requiredSteelArea = (Pu / phi - 0.85 * fc_prime * Ag) / (fy - 0.85 * fc_prime)
        
        // ── Moment consideration: increase As when eccentricity is significant ──
        val Mu = sqrt(momentX.pow(2) + momentY.pow(2)) * 1e6 // N.mm
        val eccentricity = if (Pu > 0) Mu / Pu else 0.0
        val h = max(width, depth)
        if (eccentricity > 0.05 * h) {
            val momentFactor = max(1.0, 1.0 + 2.0 * eccentricity / h)
            requiredSteelArea *= momentFactor
            codeNotes.add("ACI 318: Significant moment (e=${String.format("%.1f", eccentricity)}mm > 0.05h), As increased by factor ${String.format("%.2f", momentFactor)}")
        }
        
        // Reinforcement limits per ACI
        val minSteel = getMinReinforcementRatio() * Ag
        val maxSteel = getMaxReinforcementRatio() * Ag
        
        var astRequired = requiredSteelArea.coerceIn(minSteel, maxSteel)
        
        if (requiredSteelArea < minSteel) {
            warnings.add("Minimum reinforcement (1%) applied per ACI 318-10.6.1")
        }
        
        // Selection of bar diameters مع بدائل اقتصادية وآمنة
        val availableBars = listOf(14.0, 16.0, 19.0, 22.0, 25.0, 29.0, 32.0) // No.4 to No.10
        var selectedBarDia = availableBars.firstOrNull { 
            val area = PI * it * it / 4
            ceil(astRequired / area) <= 12
        } ?: 19.0
        
        // حساب البدائل
        val alternatives = mutableListOf<String>()
        for (dia in availableBars) {
            val area = PI * dia * dia / 4
            val numBars = ceil(astRequired / area).toInt().coerceIn(4, 16)
            val asProv = numBars * area
            val cap = calculateAxialCapacity(fcu, fy, width, depth, asProv, loadCombination)
            val util = if (cap > 0) axialLoad / cap else 2.0
            if (util in 0.5..1.0 && dia != selectedBarDia) {
                alternatives.add("${numBars}Ø${dia.toInt()} (${(util*100).toInt()}%)")
            }
        }
        if (alternatives.size >= 2) {
            codeNotes.add("Economical: ${alternatives.first()}")
            codeNotes.add("Safest: ${alternatives.last()}")
        }
        
        val barArea = PI * selectedBarDia * selectedBarDia / 4
        val numberOfBars = ceil(astRequired / barArea).toInt().coerceIn(4, 16)
        val astProvided = numberOfBars * barArea
        val barDiameter = selectedBarDia
        
        // Ties per ACI 318-25.7.2
        val tiesDiameter = if (barDiameter <= 25.0) 10.0 else 12.0
        val tiesSpacing = minOf(
            16 * barDiameter,           // 16 x longitudinal bar diameter
            48 * tiesDiameter,          // 48 x tie bar diameter
            width, depth,               // smallest dimension of member
            300.0                        // 300 mm max
        ).coerceIn(getMinSpacing(), getMaxSpacing())
        
        // Safety check
        val capacity = calculateAxialCapacity(fcu, fy, width, depth, astProvided, loadCombination)
        val utilizationRatio = axialLoad / capacity
        
        codeNotes.add("ACI 318-19: Chapter 10 - Columns")
        codeNotes.add("phi = 0.65 for tied columns")
        codeNotes.add("Min cover: ${getMinCover()}mm for cast-in-place")
        
        return ReinforcementResult(
            astRequired = astRequired,
            astProvided = astProvided,
            barDiameter = barDiameter,
            numberOfBars = numberOfBars,
            tiesDiameter = tiesDiameter,
            tiesSpacing = tiesSpacing,
            isSafe = utilizationRatio <= 1.0,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    override fun getMinReinforcementRatio(): Double = 0.01   // 1% ACI 10.6.1
    override fun getMaxReinforcementRatio(): Double = 0.08   // 8% ACI 10.6.1
    override fun getMinSpacing(): Double = 40.0
    override fun getMaxSpacing(): Double = 300.0
    override fun getMinCover(): Double = 40.0

    // ── Shear Design per ACI 318-19 §22.5 ─────────────────────────────────────

    /**
     * Column shear reinforcement design — ACI 318-19 §22.5
     * @param Vu     factored shear force (kN)
     * @param width  column width b (mm)
     * @param depth  column depth h (mm)
     * @param fcu    concrete cube strength (MPa);  f'c = 0.8 × fcu
     * @param fy     steel yield strength (MPa)
     * @param cover  concrete cover (mm), default 40
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
        val d = depth - cover
        val fc = 0.8 * fcu  // cylinder strength
        val phi = 0.75
        val codeNotes = mutableListOf<String>()

        // Vc = 0.17 × √(f'c) × b × d   (ACI 318-22.5.5.1)
        val Vc = 0.17 * sqrt(fc) * b * d / 1000.0  // kN
        val phiVc = phi * Vc

        val needsStirrups = Vu > phiVc

        // Asv/s = (Vu - φVc) / (φ × fy × d)
        val requiredAsvPerS = if (needsStirrups) {
            (Vu - phiVc) * 1000.0 / (phi * fy * d)  // mm²/mm
        } else 0.0

        // Max spacing = min(d/2, 48×db_tie, 300mm) for columns
        val dbTie = 10.0
        val maxSpacing = minOf(d / 2.0, 48.0 * dbTie, 300.0)

        // Min Asv per ACI 22.5.10.1: Av,min = 0.062√f'c × b×s / fy  (but not less than 0.35×b×s/fy)
        // As Av/s:  minAvPerS = max(0.062√f'c × b / fy,  0.35 × b / fy)
        val minAsvPerS = max(0.062 * sqrt(fc) * b / fy, 0.35 * b / fy)

        val designAsvPerS = max(requiredAsvPerS, if (needsStirrups) minAsvPerS else 0.0)

        // Select stirrup diameter and spacing
        val availableTies = listOf(9.5, 12.7, 15.9, 19.1)  // #3, #4, #5, #6
        var selectedDia = 9.5
        var selectedSpacing = maxSpacing

        if (designAsvPerS > 0) {
            for (dia in availableTies) {
                val asv = 2.0 * PI * dia * dia / 4.0
                val spacing = asv / designAsvPerS
                if (spacing <= maxSpacing && spacing >= getMinSpacing()) {
                    selectedDia = dia
                    selectedSpacing = min(spacing, maxSpacing)
                    break
                }
                if (spacing < getMinSpacing()) {
                    selectedDia = dia
                    selectedSpacing = getMinSpacing()
                }
            }
        }

        val providedAsvPerS = if (designAsvPerS > 0) {
            2.0 * PI * selectedDia * selectedDia / 4.0 / selectedSpacing
        } else 0.0

        val totalCapacity = phiVc + if (needsStirrups) phi * fy * providedAsvPerS * d / 1000.0 else 0.0
        val utilizationRatio = if (totalCapacity > 0) Vu / totalCapacity else 2.0

        codeNotes.add("ACI 318-19 §22.5: Column Shear Design")
        codeNotes.add("f'c = 0.8×fcu = ${String.format("%.0f", fc)} MPa")
        codeNotes.add("Vc = 0.17√f'c·b·d = ${String.format("%.1f", Vc)} kN")
        codeNotes.add("φVc = ${String.format("%.1f", phiVc)} kN  (φ=$phi)")
        if (needsStirrups) {
            codeNotes.add("Vu (${String.format("%.1f", Vu)} kN) > φVc → Stirrups required")
            codeNotes.add("Asv/s = ${String.format("%.3f", designAsvPerS)} mm²/mm")
            codeNotes.add(String.format("%.1fmm ties @ %.0fmm c/c", selectedDia, selectedSpacing))
        } else {
            codeNotes.add("Vu (${String.format("%.1f", Vu)} kN) ≤ φVc → Concrete alone sufficient")
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
