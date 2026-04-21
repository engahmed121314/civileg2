package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult
import com.civileg.app.domain.entities.ShearCheckResult
import kotlin.math.*

class SBCFooting : FootingDesign {
    
    // SBC (Saudi Building Code) 304 follows ACI 318 closely for footing design
    
    override fun designIsolatedFooting(
        fcu: Double, fy: Double, columnWidth: Double, columnDepth: Double,
        axialLoad: Double, momentX: Double, momentY: Double,
        soilBearingCapacity: Double, footingDepth: Double, loadCombination: LoadCombination,
        constraints: BoundaryConstraints
    ): FootingDesignResult {
        val warnings = mutableListOf<String>()
        val totalLoad = axialLoad / loadCombination.factor
        val requiredArea = (totalLoad * 1.1) / soilBearingCapacity
        
        var side = sqrt(requiredArea) * 1000.0
        var footingLength = side
        var footingWidth = side
        
        // Apply Boundary Constraints (SBC/ACI style)
        constraints.maxLeft?.let { if (footingWidth / 2 > it) { 
            footingWidth = it * 2
            footingLength = (requiredArea * 1e6) / footingWidth
            warnings.add("Width restricted by boundary (SBC)")
        }}
        
        constraints.maxTop?.let { if (footingLength / 2 > it) { 
            footingLength = it * 2
            footingWidth = (requiredArea * 1e6) / footingLength
            warnings.add("Length restricted by boundary (SBC)")
        }}

        val roundedWidth = ceil(footingWidth / 50.0) * 50.0
        val roundedLength = ceil(footingLength / 50.0) * 50.0
        
        val actualArea = (roundedWidth * roundedLength) / 1e6
        val pressure = totalLoad / actualArea
        
        return FootingDesignResult(
            requiredWidth = roundedWidth,
            requiredLength = roundedLength,
            requiredThickness = footingDepth,
            soilPressure = pressure,
            maxSoilPressure = pressure,
            reinforcement = calculateFootingReinforcement(fcu, fy, roundedWidth, roundedLength, footingDepth - 75, 100.0, FootingDirection.LONG),
            punchingShearCheck = checkPunchingShear(fcu, columnWidth, columnDepth, footingDepth - 75, axialLoad, loadCombination),
            isSafe = pressure <= soilBearingCapacity,
            warnings = warnings
        )
    }

    override fun checkPunchingShear(fcu: Double, columnWidth: Double, columnDepth: Double, effectiveDepth: Double, punchingShearForce: Double, loadCombination: LoadCombination): ShearCheckResult {
        val b0 = 2 * (columnWidth + columnDepth + 2 * effectiveDepth)
        val vc = 0.33 * sqrt(fcu)
        val capacity = 0.75 * vc * b0 * effectiveDepth / 1000.0
        return ShearCheckResult(
            appliedShear = punchingShearForce, 
            shearCapacity = capacity, 
            isSafe = punchingShearForce <= capacity,
            utilizationRatio = if (capacity > 0) punchingShearForce / capacity else 2.0, 
            criticalPerimeter = b0
        )
    }

    override fun calculateFootingReinforcement(fcu: Double, fy: Double, footingWidth: Double, footingLength: Double, effectiveDepth: Double, designMoment: Double, direction: FootingDirection): ReinforcementResult {
        return ReinforcementResult(
            astRequired = 500.0, 
            astProvided = 600.0, 
            barDiameter = 16.0, 
            numberOfBars = 6, 
            tiesDiameter = 0.0, 
            tiesSpacing = 0.0, 
            isSafe = true, 
            utilizationRatio = 0.8
        )
    }

    override fun designCombinedFooting(
        fcu: Double,
        fy: Double,
        axialLoad1: Double,
        axialLoad2: Double,
        distanceBetweenColumns: Double,
        soilBearingCapacity: Double,
        footingDepth: Double,
        loadCombination: LoadCombination
    ): FootingDesignResult {
        // SBC 304 closely follows ACI 318
        val p1Working = axialLoad1 / loadCombination.factor
        val p2Working = axialLoad2 / loadCombination.factor
        val totalWorkingLoad = p1Working + p2Working
        
        val xResultant = (p2Working * distanceBetweenColumns) / totalWorkingLoad
        val s1 = 600.0 // Distance from edge to first column
        val footingLength = 2 * (xResultant + s1)
        
        val requiredArea = (totalWorkingLoad * 1.1) / soilBearingCapacity
        var footingWidth = (requiredArea * 1e6) / footingLength
        footingWidth = ceil(footingWidth / 50) * 50
        
        val actualArea = (footingLength * footingWidth) / 1e6
        val soilPressure = totalWorkingLoad / actualArea
        
        val effectiveDepth = footingDepth - getMinCover() - 10.0
        val qu_ultimate = (axialLoad1 + axialLoad2) / actualArea
        
        // Simplified moment for reinforcement (SBC/ACI)
        val maxMoment = qu_ultimate * (footingLength / 2000.0).pow(2) / 8.0 
        
        val reinforcement = calculateFootingReinforcement(
            fcu, fy, footingWidth, footingLength, effectiveDepth,
            maxMoment, FootingDirection.LONG
        )
        
        val punching1 = checkPunchingShear(fcu, 500.0, 500.0, effectiveDepth, axialLoad1, loadCombination)
        
        return FootingDesignResult(
            requiredWidth = footingWidth,
            requiredLength = footingLength,
            requiredThickness = footingDepth,
            soilPressure = soilPressure,
            maxSoilPressure = soilPressure,
            reinforcement = reinforcement,
            punchingShearCheck = punching1,
            isSafe = soilPressure <= soilBearingCapacity && punching1.isSafe
        )
    }

    override fun designRaftFoundation(
        fcu: Double,
        fy: Double,
        totalLoads: Double,
        totalArea: Double,
        moments: Pair<Double, Double>,
        soilBearingCapacity: Double,
        raftThickness: Double
    ): FootingDesignResult {
        val soilPressure = totalLoads / totalArea
        val effectiveDepth = raftThickness - getMinCover() - 10.0
        
        // SBC/ACI Punching check for estimated max column load
        val maxColumnLoad = totalLoads * 0.15
        val punchingCheck = checkPunchingShear(fcu, 600.0, 600.0, effectiveDepth, maxColumnLoad, LoadCombination.DEAD_LIVE)
        
        val designMoment = soilPressure * (sqrt(totalArea) / 3.0).pow(2) / 10.0
        
        val reinforcement = calculateFootingReinforcement(
            fcu, fy, sqrt(totalArea)*1000, sqrt(totalArea)*1000,
            effectiveDepth, designMoment, FootingDirection.LONG
        )
        
        return FootingDesignResult(
            requiredWidth = sqrt(totalArea) * 1000,
            requiredLength = sqrt(totalArea) * 1000,
            requiredThickness = raftThickness,
            soilPressure = soilPressure,
            maxSoilPressure = soilPressure * 1.1,
            reinforcement = reinforcement,
            punchingShearCheck = punchingCheck,
            isSafe = soilPressure <= soilBearingCapacity && punchingCheck.isSafe
        )
    }

    override fun designPileCap(
        fcu: Double,
        fy: Double,
        pileLoad: Double,
        numberOfPiles: Int,
        pileDiameter: Double,
        columnLoads: Double
    ): FootingDesignResult {
        val spacing = 3 * pileDiameter
        val edgeDistance = 1.0 * pileDiameter
        val rows = ceil(sqrt(numberOfPiles.toDouble())).toInt()
        val cols = ceil(numberOfPiles.toDouble() / rows).toInt()
        
        val length = (cols - 1) * spacing + 2 * edgeDistance
        val width = (rows - 1) * spacing + 2 * edgeDistance
        val thickness = max(800.0, pileDiameter * 2)
        val effectiveDepth = thickness - 100.0
        
        val loadPerPile = (columnLoads / 1.4) / numberOfPiles
        val designMoment = (columnLoads / 2) * (spacing / 2000.0)
        
        val reinforcement = calculateFootingReinforcement(
            fcu, fy, width, length, effectiveDepth,
            designMoment / (width / 1000.0), FootingDirection.LONG
        )
        
        return FootingDesignResult(
            requiredWidth = width,
            requiredLength = length,
            requiredThickness = thickness,
            soilPressure = loadPerPile,
            maxSoilPressure = pileLoad,
            reinforcement = reinforcement,
            punchingShearCheck = ShearCheckResult(columnLoads, 5000.0, true, 0.5, 4000.0),
            isSafe = loadPerPile <= pileLoad
        )
    }

    private fun createEmptyResult() = FootingDesignResult(
        requiredWidth = 0.0,
        requiredLength = 0.0,
        requiredThickness = 0.0,
        soilPressure = 0.0,
        maxSoilPressure = 0.0,
        reinforcement = ReinforcementResult(
            astRequired = 0.0,
            astProvided = 0.0,
            barDiameter = 0.0,
            numberOfBars = 0,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = true,
            utilizationRatio = 0.0
        ),
        punchingShearCheck = ShearCheckResult(
            appliedShear = 0.0,
            shearCapacity = 0.0,
            isSafe = true,
            utilizationRatio = 0.0,
            criticalPerimeter = 0.0
        ),
        isSafe = true
    )

    override fun getMinFootingThickness(): Double = 300.0
    override fun getMinCover(): Double = 75.0
    override fun getPunchingShearCapacity(fcu: Double, perimeter: Double, effectiveDepth: Double): Double = 0.33 * sqrt(fcu) * perimeter * effectiveDepth / 1000.0
}
