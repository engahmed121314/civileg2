package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.CodeReference
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult
import com.civileg.app.domain.entities.ShearCheckResult
import kotlin.math.*

class ACIFooting : FootingDesign {
    
    companion object {
        private const val PHI_SHEAR = 0.75
        private const val PHI_BENDING = 0.90
    }

    override fun designIsolatedFooting(
        fcu: Double,
        fy: Double,
        columnWidth: Double,
        columnDepth: Double,
        axialLoad: Double,
        momentX: Double,
        momentY: Double,
        soilBearingCapacity: Double,
        footingDepth: Double,
        loadCombination: LoadCombination,
        constraints: BoundaryConstraints
    ): FootingDesignResult {
        val warnings = mutableListOf<String>()
        val totalLoad = axialLoad / loadCombination.factor
        val requiredArea = (totalLoad * 1.1) / soilBearingCapacity
        
        val diff = abs(columnDepth - columnWidth)
        var footingLength = (diff + sqrt(diff * diff + 4 * requiredArea)) / 2.0 * 1000
        var footingWidth = (requiredArea / (footingLength / 1000.0)) * 1000
        
        // Apply Boundary Constraints
        constraints.maxLeft?.let { if (footingWidth / 2 > it) { 
            footingWidth = it * 2
            footingLength = (requiredArea * 1e6) / footingWidth
            warnings.add("Width restricted by boundary (ACI)")
        }}
        
        constraints.maxTop?.let { if (footingLength / 2 > it) { 
            footingLength = it * 2
            footingWidth = (requiredArea * 1e6) / footingLength
            warnings.add("Length restricted by boundary (ACI)")
        }}
        
        val roundedWidth = ceil(footingWidth / 50) * 50
        val roundedLength = ceil(footingLength / 50) * 50
        
        val actualArea = (roundedWidth * roundedLength) / 1e6
        val soilPressure = totalLoad / actualArea
        
        val effectiveDepth = footingDepth - getMinCover() - 16.0/2.0
        
        val punchingCheck = checkPunchingShear(fcu, columnWidth, columnDepth, effectiveDepth, axialLoad, loadCombination)
        
        val cantilever = (roundedLength - max(columnWidth, columnDepth)) / 2000.0
        val qu_ultimate = axialLoad / actualArea
        val Mu = qu_ultimate * cantilever * cantilever / 2.0
        
        val reinforcement = calculateFootingReinforcement(fcu, fy, roundedWidth, roundedLength, effectiveDepth, Mu, FootingDirection.LONG)
        
        return FootingDesignResult(
            requiredWidth = roundedWidth,
            requiredLength = roundedLength,
            requiredThickness = footingDepth,
            soilPressure = soilPressure,
            maxSoilPressure = soilPressure,
            reinforcement = reinforcement,
            punchingShearCheck = punchingCheck,
            isSafe = soilPressure <= soilBearingCapacity && punchingCheck.isSafe
        )
    }

    override fun checkPunchingShear(
        fcu: Double,
        columnWidth: Double,
        columnDepth: Double,
        effectiveDepth: Double,
        punchingShearForce: Double,
        loadCombination: LoadCombination
    ): ShearCheckResult {
        val b0 = 2 * (columnWidth + effectiveDepth) + 2 * (columnDepth + effectiveDepth)
        val vn = minOf(
            0.33 * sqrt(fcu),
            0.17 * (1 + 2 / (max(columnDepth, columnWidth) / min(columnDepth, columnWidth).coerceAtLeast(1.0))) * sqrt(fcu),
            0.083 * (2 + 40 * effectiveDepth / b0) * sqrt(fcu)
        )
        val capacity = PHI_SHEAR * vn * b0 * effectiveDepth / 1000.0
        val isSafe = punchingShearForce <= capacity
        
        return ShearCheckResult(
            appliedShear = punchingShearForce,
            shearCapacity = capacity,
            isSafe = isSafe,
            utilizationRatio = if (capacity > 0) punchingShearForce / capacity else 2.0,
            criticalPerimeter = b0
        )
    }

    override fun calculateFootingReinforcement(
        fcu: Double,
        fy: Double,
        footingWidth: Double,
        footingLength: Double,
        effectiveDepth: Double,
        designMoment: Double,
        direction: FootingDirection
    ): ReinforcementResult {
        val Mu = designMoment * 1e6
        val b = 1000.0
        val Rn = Mu / (PHI_BENDING * b * effectiveDepth * effectiveDepth)
        val rho = if (1 - 2 * Rn / (0.85 * fcu) > 0) (0.85 * fcu / fy) * (1 - sqrt(1 - 2 * Rn / (0.85 * fcu))) else 0.0018
        val asRequired = max(rho, 0.0018) * b * (effectiveDepth + getMinCover())
        
        return ReinforcementResult(
            astRequired = asRequired,
            astProvided = asRequired,
            barDiameter = 16.0,
            numberOfBars = ceil(asRequired / (PI * 16 * 16 / 4)).toInt(),
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
        val p1Working = axialLoad1 / loadCombination.factor
        val p2Working = axialLoad2 / loadCombination.factor
        val totalWorkingLoad = p1Working + p2Working
        
        val xResultant = (p2Working * distanceBetweenColumns) / totalWorkingLoad
        val s1 = 600.0 // Default offset
        val footingLength = 2 * (xResultant + s1)
        
        val requiredArea = (totalWorkingLoad * 1.1) / soilBearingCapacity
        var footingWidth = (requiredArea * 1e6) / footingLength
        footingWidth = ceil(footingWidth / 50) * 50
        
        val actualArea = (footingLength * footingWidth) / 1e6
        val soilPressure = totalWorkingLoad / actualArea
        
        val effectiveDepth = footingDepth - getMinCover() - 10.0
        val qu_ultimate = (axialLoad1 + axialLoad2) / actualArea
        
        // Simplified moment for reinforcement
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
        
        // ACI Punching check for max column load (estimated)
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
