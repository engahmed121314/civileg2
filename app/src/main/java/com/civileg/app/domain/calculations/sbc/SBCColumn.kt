package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.app.domain.entities.ColumnShearDesignResult
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult
import kotlin.math.*

class SBCColumn : ColumnDesign {
    
    // SBC 304 follows ACI 318 closely with some specific local modifications
    
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
        
        // SBC 304 / ACI 318: Pn = 0.85*fc'*(Ag-Ast) + fy*Ast
        // fc' = 0.8 × fcu (تحويل مقاومة المكعب لمقاومة الأسطوانة)
        val fc_prime = 0.8 * fcu
        val concreteCapacity = 0.85 * fc_prime * (Ag - Ast)
        val steelCapacity = fy * Ast
        val nominalCapacity = concreteCapacity + steelCapacity
        
        // معامل الاختزال للأعمدة المربوطة (SBC 304-10.6 / ACI 318-21.2.2)
        val phi = 0.65
        // ملاحظة: معامل 0.80 تم إلغاؤه من ACI/SBC منذ نسخة 2002
        return phi * nominalCapacity / 1000.0 // kN
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
        val Pu = axialLoad * 1000.0  // N - الحمل المحوري التصميمي
        
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // SBC 304 / ACI 318: حل معادلة القدرة المحورية لإيجاد Ast
        // fc' = 0.8 × fcu
        val fc_prime = 0.8 * fcu
        val phi = 0.65
        var requiredSteelArea = (Pu / phi - 0.85 * fc_prime * Ag) / (fy - 0.85 * fc_prime)
        
        // ── Moment consideration: increase As when eccentricity is significant ──
        val Mu = sqrt(momentX.pow(2) + momentY.pow(2)) * 1e6 // N.mm
        val eccentricity = if (Pu > 0) Mu / Pu else 0.0
        val h = max(width, depth)
        if (eccentricity > 0.05 * h) {
            val momentFactor = max(1.0, 1.0 + 2.0 * eccentricity / h)
            var requiredSteelArea = requiredSteelArea * momentFactor
            codeNotes.add("SBC 304: Significant moment (e=${String.format("%.1f", eccentricity)}mm > 0.05h), As increased by factor ${String.format("%.2f", momentFactor)}")
        }
        
        val minSteel = getMinReinforcementRatio() * Ag
        val maxSteel = getMaxReinforcementRatio() * Ag
        
        var astRequired = requiredSteelArea.coerceIn(minSteel, maxSteel)
        
        if (requiredSteelArea < minSteel) {
            warnings.add("Minimum reinforcement (1%) applied per SBC 304")
        }
        
        val availableBars = listOf(14.0, 16.0, 20.0, 25.0, 32.0) // Metric sizes common in KSA
        val barDiameter = availableBars.firstOrNull { 
            val area = PI * it * it / 4
            ceil(astRequired / area) <= 12
        } ?: 16.0
        
        val barArea = PI * barDiameter * barDiameter / 4
        val numberOfBars = ceil(astRequired / barArea).toInt().coerceIn(4, 20)
        val astProvided = numberOfBars * barArea
        
        val tiesDiameter = if (barDiameter <= 32.0) 10.0 else 12.0
        val tiesSpacing = minOf(
            16 * barDiameter,
            48 * tiesDiameter,
            width, depth,
            300.0
        ).coerceIn(getMinSpacing(), getMaxSpacing())
        
        val capacity = calculateAxialCapacity(fcu, fy, width, depth, astProvided, loadCombination)
        val utilizationRatio = axialLoad / capacity
        
        codeNotes.add("SBC 304-2018: Section 10")
        codeNotes.add("phi = 0.65 (Tied)")
        
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

    override fun getMinReinforcementRatio(): Double = 0.01
    override fun getMaxReinforcementRatio(): Double = 0.08
    override fun getMinSpacing(): Double = 40.0
    override fun getMaxSpacing(): Double = 300.0
    override fun getMinCover(): Double = 40.0

    // ── Shear Design per SBC 304 (ACI-based with SBC f'c) ───────────────────

    /**
     * Column shear reinforcement design — SBC 304
     * Uses SBC cylinder strength: f'c_sbc = 0.67 × fcu / 1.5
     * @param Vu     factored shear force (kN)
     * @param width  column width b (mm)
     * @param depth  column depth h (mm)
     * @param fcu    concrete cube strength (MPa)
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
        // SBC f'c = 0.67 × fcu / 1.5  (SBC local conversion)
        val fc = 0.67 * fcu / 1.5
        val phi = 0.75
        val codeNotes = mutableListOf<String>()

        // Vc = 0.17 × √(f'c_sbc) × b × d
        val Vc = 0.17 * sqrt(fc) * b * d / 1000.0  // kN
        val phiVc = phi * Vc

        val needsStirrups = Vu > phiVc

        // Asv/s = (Vu - φVc) / (φ × fy × d)
        val requiredAsvPerS = if (needsStirrups) {
            (Vu - phiVc) * 1000.0 / (phi * fy * d)  // mm²/mm
        } else 0.0

        // SBC seismic stirrup spacing: min(d/2, 48×db_tie, 300mm)
        // For seismic zones, SBC restricts to min(d/4, 100mm) near plastic hinges
        val dbTie = 10.0
        val maxSpacing = minOf(d / 2.0, 48.0 * dbTie, 300.0)
        val seismicMaxSpacing = minOf(d / 4.0, 100.0)  // seismic zone restriction

        // Min Asv per ACI 22.5.10.1 (adopted by SBC)
        val minAsvPerS = max(0.062 * sqrt(fc) * b / fy, 0.35 * b / fy)

        val designAsvPerS = max(requiredAsvPerS, if (needsStirrups) minAsvPerS else 0.0)

        // Select stirrup diameter and spacing
        val availableTies = listOf(10.0, 12.0, 14.0, 16.0)  // Metric sizes common in KSA
        var selectedDia = 10.0
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

        codeNotes.add("SBC 304: Column Shear Design")
        codeNotes.add("f'c_sbc = 0.67×fcu/1.5 = ${String.format("%.0f", fc)} MPa")
        codeNotes.add("Vc = 0.17√f'c·b·d = ${String.format("%.1f", Vc)} kN")
        codeNotes.add("φVc = ${String.format("%.1f", phiVc)} kN  (φ=$phi)")
        if (needsStirrups) {
            codeNotes.add("Vu (${String.format("%.1f", Vu)} kN) > φVc → Stirrups required")
            codeNotes.add("Asv/s = ${String.format("%.3f", designAsvPerS)} mm²/mm")
            codeNotes.add("${selectedDia.toInt()}mm ties @ ${selectedSpacing.toInt()}mm c/c")
            codeNotes.add("Seismic max spacing: ${seismicMaxSpacing.toInt()}mm (near plastic hinges)")
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
