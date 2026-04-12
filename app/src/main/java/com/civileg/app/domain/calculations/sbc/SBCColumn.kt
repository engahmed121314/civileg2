package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.base.ColumnDesign
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
        
        // SBC 304: Pn = 0.85*fc'*(Ag-Ast) + fy*Ast
        val concreteCapacity = 0.85 * fcu * (Ag - Ast)
        val steelCapacity = fy * Ast
        val nominalCapacity = concreteCapacity + steelCapacity
        
        // Strength reduction factor phi for tied columns (SBC 304)
        val phi = 0.65
        
        // Factor 0.80 for tied columns
        return phi * 0.80 * nominalCapacity / 1000.0 // kN
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
        val Pu = axialLoad * 1000.0 / loadCombination.factor
        
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // SBC 304/ACI 318 approach
        val phi = 0.65
        val requiredSteelArea = (Pu / phi - 0.85 * fcu * Ag) / (fy - 0.85 * fcu)
        
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
}
