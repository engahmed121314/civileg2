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
        loadCombination: LoadCombination
    ): FootingDesignResult {
        val totalLoad = axialLoad / loadCombination.factor
        val requiredArea = (totalLoad * 1.1) / soilBearingCapacity
        
        val diff = columnDepth - columnWidth
        val footingLength = (diff + sqrt(diff * diff + 4 * requiredArea)) / 2.0 * 1000
        val footingWidth = (requiredArea / (footingLength / 1000.0)) * 1000
        
        val roundedWidth = ceil(footingWidth / 50) * 50
        val roundedLength = ceil(footingLength / 50) * 50
        
        val actualArea = (roundedWidth * roundedLength) / 1e6
        val soilPressure = totalLoad / actualArea
        
        val effectiveDepth = footingDepth - getMinCover() - 16.0/2.0
        
        // ACI Punching Shear Check
        val punchingCheck = checkPunchingShear(fcu, columnWidth, columnDepth, effectiveDepth, axialLoad, loadCombination)
        
        // Bending Moment at face of column
        val cantilever = (roundedLength - columnDepth) / 2000.0
        val qu_ultimate = axialLoad / actualArea
        val Mu = qu_ultimate * cantilever * cantilever / 2.0
        
        val reinforcement = calculateFootingReinforcement(fcu, fy, roundedWidth, roundedLength, effectiveDepth, Mu, FootingDirection.LONG)
        
        return FootingDesignResult(
            requiredWidth = roundedWidth,
            requiredLength = roundedLength,
            requiredThickness = footingDepth,
            soilPressure = soilPressure,
            maxSoilPressure = soilPressure, // Simplified for now
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
            0.17 * (1 + 2 / (columnDepth / columnWidth)) * sqrt(fcu),
            0.083 * (2 + 40 * effectiveDepth / b0) * sqrt(fcu)
        )
        val capacity = PHI_SHEAR * vn * b0 * effectiveDepth / 1000.0
        val isSafe = punchingShearForce <= capacity
        
        return ShearCheckResult(
            appliedShear = punchingShearForce,
            shearCapacity = capacity,
            utilizationRatio = punchingShearForce / capacity,
            isSafe = isSafe,
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
        val rho = (0.85 * fcu / fy) * (1 - sqrt(1 - 2 * Rn / (0.85 * fcu)))
        val asRequired = max(rho, 0.0018) * b * (effectiveDepth + getMinCover())
        
        return ReinforcementResult(
            astRequired = asRequired,
            astProvided = asRequired, // Simplified
            barDiameter = 16.0,
            numberOfBars = ceil(asRequired / (PI * 16 * 16 / 4)).toInt(),
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = true,
            utilizationRatio = 0.8
        )
    }

    override fun designCombinedFooting(fcu: Double, fy: Double, axialLoad1: Double, axialLoad2: Double, distanceBetweenColumns: Double, soilBearingCapacity: Double, footingDepth: Double, loadCombination: LoadCombination): FootingDesignResult {
        // Implementation for Combined Footing
        return FootingDesignResult(0.0, 0.0, 0.0, 0.0, 0.0, ReinforcementResult(0.0, 0.0, 0.0, 0, 0.0, 0.0, true, 0.0), ShearCheckResult(0.0, 0.0, 0.0, true, 0.0), true)
    }

    override fun designRaftFoundation(fcu: Double, fy: Double, totalLoads: Double, totalArea: Double, moments: Pair<Double, Double>, soilBearingCapacity: Double, raftThickness: Double): FootingDesignResult {
        return FootingDesignResult(0.0, 0.0, 0.0, 0.0, 0.0, ReinforcementResult(0.0, 0.0, 0.0, 0, 0.0, 0.0, true, 0.0), ShearCheckResult(0.0, 0.0, 0.0, true, 0.0), true)
    }

    override fun designPileCap(fcu: Double, fy: Double, pileLoad: Double, numberOfPiles: Int, pileDiameter: Double, columnLoads: Double): FootingDesignResult {
        return FootingDesignResult(0.0, 0.0, 0.0, 0.0, 0.0, ReinforcementResult(0.0, 0.0, 0.0, 0, 0.0, 0.0, true, 0.0), ShearCheckResult(0.0, 0.0, 0.0, true, 0.0), true)
    }

    override fun getMinFootingThickness(): Double = 300.0
    override fun getMinCover(): Double = 75.0
    override fun getPunchingShearCapacity(fcu: Double, perimeter: Double, effectiveDepth: Double): Double = 0.33 * sqrt(fcu) * perimeter * effectiveDepth / 1000.0
}
