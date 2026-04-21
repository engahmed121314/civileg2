package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult
import com.civileg.app.domain.entities.ShearCheckResult
import kotlin.math.*

/**
 * محرك تصميم القواعد المنفصلة حسب الكود المصري ECP 203
 */
class ECPFooting : FootingDesign {

    override fun designIsolatedFooting(
        fcu: Double,
        fy: Double,
        columnWidth: Double,      // mm
        columnDepth: Double,      // mm
        axialLoad: Double,        // kN
        momentX: Double,          // kN.m
        momentY: Double,          // kN.m
        soilBearingCapacity: Double, // kPa
        footingDepth: Double,     // mm
        loadCombination: LoadCombination,
        constraints: BoundaryConstraints
    ): FootingDesignResult {
        // 1. حساب المساحة المطلوبة (Working)
        val area = (axialLoad * 1.1) / soilBearingCapacity 
        val side = sqrt(area) * 1000 // Convert to mm
        
        val width = ceil(side / 50.0) * 50.0 // التقريب لأقرب 50 مم زيادة
        val length = width
        val d = footingDepth - getMinCover()
        
        // حساب العزم الفعلي عند وجه العمود (Mu = qu * L^2 / 2)
        val quNet = (axialLoad * 1.5) / (width * length / 1e6)
        val projection = (width - columnWidth) / 2000.0 // الرفرفة بالمتر
        val actualMu = quNet * projection * projection / 2.0

        // 2. التحقق من قص الاختراق
        val punchingResult = checkPunchingShear(
            fcu, columnWidth, columnDepth, d, 
            axialLoad * 1.5, // Pu
            loadCombination
        )
        
        val reinf = calculateFootingReinforcement(
            fcu, fy, width, length, d,
            actualMu,
            FootingDirection.LONG
        )

        return FootingDesignResult(
            requiredWidth = width,
            requiredLength = length,
            requiredThickness = footingDepth,
            soilPressure = axialLoad / (width * length / 1e6),
            maxSoilPressure = axialLoad / (width * length / 1e6),
            reinforcement = reinf,
            punchingShearCheck = punchingResult,
            isSafe = punchingResult.isSafe,
            warnings = if (!punchingResult.isSafe) listOf("Punching shear failure") else emptyList()
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
        val b0 = 2 * (columnWidth + effectiveDepth + columnDepth + effectiveDepth)
        val capacity = getPunchingShearCapacity(fcu, b0, effectiveDepth)
        val actualStress = (punchingShearForce * 1000) / (b0 * effectiveDepth)
        
        return ShearCheckResult(
            appliedShear = actualStress,
            shearCapacity = capacity,
            isSafe = actualStress <= capacity,
            utilizationRatio = actualStress / capacity,
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
        val j = 0.826 // Constant for ECP simplified
        val asRequired = (designMoment * 1e6) / (fy / 1.15 * j * effectiveDepth)
        val asMin = max(0.0015 * 1000 * effectiveDepth, 5.0 * PI * 6.0 * 6.0) // Placeholder for 5 T12
        
        val asProvided = max(asRequired, asMin)
        
        return ReinforcementResult(
            astRequired = asRequired,
            astProvided = asProvided,
            barDiameter = 16.0,
            numberOfBars = (asProvided / (PI * 8.0 * 8.0)).toInt().coerceAtLeast(5),
            tiesDiameter = 8.0,
            tiesSpacing = 200.0,
            isSafe = true,
            utilizationRatio = asRequired / asProvided,
            spacing = 150.0
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
        return designIsolatedFooting(fcu, fy, 500.0, 500.0, axialLoad1 + axialLoad2, 0.0, 0.0, soilBearingCapacity, footingDepth, loadCombination)
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
        return designIsolatedFooting(fcu, fy, 500.0, 500.0, totalLoads, moments.first, moments.second, soilBearingCapacity, raftThickness, LoadCombination.DEAD_LIVE)
    }

    override fun designPileCap(
        fcu: Double,
        fy: Double,
        pileLoad: Double,
        numberOfPiles: Int,
        pileDiameter: Double,
        columnLoads: Double
    ): FootingDesignResult {
        return designIsolatedFooting(fcu, fy, 500.0, 500.0, columnLoads, 0.0, 0.0, 100.0, 1000.0, LoadCombination.DEAD_LIVE)
    }

    override fun getMinFootingThickness(): Double = 400.0
    override fun getMinCover(): Double = 50.0
    
    override fun getPunchingShearCapacity(fcu: Double, perimeter: Double, effectiveDepth: Double): Double {
        // qp = 0.316 * sqrt(fcu / gamma_c)
        return 0.316 * sqrt(fcu / 1.5)
    }
}
