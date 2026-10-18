package com.civileg.core.calculations.ecp

import com.civileg.core.calculations.base.ColumnDesign
import com.civileg.core.calculations.entities.*
import kotlin.math.*

class ECPColumn : ColumnDesign {
    
    companion object {
        private const val ALPHA = 0.8
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15
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
        val concreteStress = 0.67 * fcu / GAMMA_C
        val steelStress = fy / GAMMA_S
        val concreteCapacity = concreteStress * (Ag - Ast)
        val steelCapacity = steelStress * Ast
        val designCapacity = ALPHA * (concreteCapacity + steelCapacity)
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
        val Pu = axialLoad * 1000.0
        val Mu = sqrt(momentX.pow(2) + momentY.pow(2)) * 1e6
        
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val eccentricity = if (Pu > 0) Mu / Pu else 0.0
        val h = max(width, depth)
        
        val concreteStress = 0.67 * fcu / GAMMA_C
        val steelStress = fy / GAMMA_S
        
        val numerator = Pu / ALPHA - concreteStress * Ag
        val denominator = steelStress - concreteStress
        var requiredSteelArea = if (denominator != 0.0) numerator / denominator else 0.0
        
        if (eccentricity > 0.05 * h) {
            val momentFactor = max(1.0, 1.0 + 2.0 * eccentricity / h)
            requiredSteelArea *= momentFactor
        }
        
        val minSteel = getMinReinforcementRatio() * Ag
        val maxSteel = getMaxReinforcementRatio() * Ag
        
        if (requiredSteelArea < minSteel) {
            requiredSteelArea = minSteel
            warnings.add("Minimum reinforcement applied")
        }
        
        val barDiameter = 16.0
        val barArea = PI * barDiameter * barDiameter / 4
        val numberOfBars = ceil(requiredSteelArea / barArea).toInt().coerceIn(4, 32)
        val astProvided = numberOfBars * barArea
        
        val tiesDiameter = max(10.0, barDiameter / 4).coerceAtLeast(8.0)
        val tiesSpacing = calculateTiesSpacing(barDiameter, tiesDiameter, width, depth)
        
        val capacity = calculateAxialCapacity(fcu, fy, width, depth, astProvided, loadCombination)
        val utilizationRatio = if (capacity > 0) axialLoad / capacity else 2.0
        
        codeNotes.add(CodeReference.ECP.COLUMN_AXIAL)
        
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

    private fun calculateTiesSpacing(barDiameter: Double, tiesDiameter: Double, width: Double, depth: Double): Double {
        return minOf(16 * barDiameter, 48 * tiesDiameter, width, depth, 300.0).coerceIn(getMinSpacing(), getMaxSpacing())
    }

    override fun getMinReinforcementRatio(): Double = 0.008
    override fun getMaxReinforcementRatio(): Double = 0.08
    override fun getMinSpacing(): Double = 100.0
    override fun getMaxSpacing(): Double = 300.0
    override fun getMinCover(): Double = 40.0
}
