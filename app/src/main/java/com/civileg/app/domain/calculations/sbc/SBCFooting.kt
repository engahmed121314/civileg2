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
        soilBearingCapacity: Double, footingDepth: Double, loadCombination: LoadCombination
    ): FootingDesignResult {
        // SBC implementation based on SBC 304 (similar to ACI 318-14/19)
        val totalLoad = axialLoad / loadCombination.factor
        val area = (totalLoad * 1.1) / soilBearingCapacity
        val side = sqrt(area) * 1000.0
        val roundedSide = ceil(side / 50.0) * 50.0
        
        val actualArea = (roundedSide * roundedSide) / 1e6
        val pressure = totalLoad / actualArea
        
        return FootingDesignResult(
            requiredWidth = roundedSide,
            requiredLength = roundedSide,
            requiredThickness = footingDepth,
            soilPressure = pressure,
            maxSoilPressure = pressure,
            reinforcement = calculateFootingReinforcement(fcu, fy, roundedSide, roundedSide, footingDepth - 75, 100.0, FootingDirection.LONG),
            punchingShearCheck = checkPunchingShear(fcu, columnWidth, columnDepth, footingDepth - 75, axialLoad, loadCombination),
            isSafe = pressure <= soilBearingCapacity
        )
    }

    override fun checkPunchingShear(fcu: Double, columnWidth: Double, columnDepth: Double, effectiveDepth: Double, punchingShearForce: Double, loadCombination: LoadCombination): ShearCheckResult {
        val b0 = 2 * (columnWidth + columnDepth + 2 * effectiveDepth)
        val vc = 0.33 * sqrt(fcu)
        val capacity = 0.75 * vc * b0 * effectiveDepth / 1000.0
        return ShearCheckResult(punchingShearForce, capacity, punchingShearForce / capacity, punchingShearForce <= capacity, b0)
    }

    override fun calculateFootingReinforcement(fcu: Double, fy: Double, footingWidth: Double, footingLength: Double, effectiveDepth: Double, designMoment: Double, direction: FootingDirection): ReinforcementResult {
        return ReinforcementResult(500.0, 600.0, 16.0, 6, 0.0, 0.0, true, 0.8)
    }

    override fun designCombinedFooting(fcu: Double, fy: Double, axialLoad1: Double, axialLoad2: Double, distanceBetweenColumns: Double, soilBearingCapacity: Double, footingDepth: Double, loadCombination: LoadCombination): FootingDesignResult {
        return createEmptyResult()
    }

    override fun designRaftFoundation(fcu: Double, fy: Double, totalLoads: Double, totalArea: Double, moments: Pair<Double, Double>, soilBearingCapacity: Double, raftThickness: Double): FootingDesignResult {
        return createEmptyResult()
    }

    override fun designPileCap(fcu: Double, fy: Double, pileLoad: Double, numberOfPiles: Int, pileDiameter: Double, columnLoads: Double): FootingDesignResult {
        return createEmptyResult()
    }

    private fun createEmptyResult() = FootingDesignResult(0.0, 0.0, 0.0, 0.0, 0.0, ReinforcementResult(0.0, 0.0, 0.0, 0, 0.0, 0.0, true, 0.0), ShearCheckResult(0.0, 0.0, 0.0, true, 0.0), true)

    override fun getMinFootingThickness(): Double = 300.0
    override fun getMinCover(): Double = 75.0
    override fun getPunchingShearCapacity(fcu: Double, perimeter: Double, effectiveDepth: Double): Double = 0.33 * sqrt(fcu) * perimeter * effectiveDepth / 1000.0
}
