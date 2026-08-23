package com.civileg.core.calculations.ecp

import com.civileg.core.calculations.base.*
import com.civileg.core.calculations.entities.*
import kotlin.math.*

class ECPFooting : FootingDesign {

    companion object {
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15
        private const val GAMMA_CONCRETE = 25.0
        private const val MIN_REIN_RATIO = 0.0015
        private const val MIN_THICKNESS = 300.0
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
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val factor = loadCombination.getFactorForCode(DesignCode.ECP)
        val P_service = axialLoad / factor
        
        val t_m = footingDepth / 1000.0
        val netSBC = soilBearingCapacity - GAMMA_CONCRETE * t_m
        val A_req = P_service / netSBC

        val B_m = sqrt(A_req)
        val L_m = A_req / B_m

        val B = ceil(B_m * 1000 / 50.0) * 50.0
        val L = ceil(L_m * 1000 / 50.0) * 50.0
        
        val q_avg = P_service / (B * L / 1e6)
        val d = footingDepth - getMinCover() - 10.0

        val punchingCheck = checkPunchingShear(fcu, columnWidth, columnDepth, d, axialLoad, loadCombination)
        val reinf = calculateFootingReinforcement(fcu, fy, B, L, d, q_avg * (B-columnWidth)/2000.0, FootingDirection.SHORT)

        return FootingDesignResult(
            requiredWidth = B,
            requiredLength = L,
            requiredThickness = footingDepth,
            soilPressure = q_avg,
            maxSoilPressure = q_avg,
            reinforcement = reinf,
            punchingShearCheck = punchingCheck,
            isSafe = q_avg <= soilBearingCapacity && punchingCheck.isSafe,
            warnings = warnings,
            codeNotes = codeNotes
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
        val bo = 2.0 * (columnWidth + columnDepth) + 4.0 * effectiveDepth
        val qp_applied = (punchingShearForce * 1000.0) / (bo * effectiveDepth)
        val qp_capacity = 0.316 * sqrt(fcu / GAMMA_C)
        return ShearCheckResult(
            appliedShear = qp_applied,
            shearCapacity = qp_capacity,
            isSafe = qp_applied <= qp_capacity,
            utilizationRatio = qp_applied / qp_capacity,
            criticalSection = effectiveDepth / 2.0,
            criticalPerimeter = bo
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
        val d = effectiveDepth
        val K = Mu / (fcu * b * d * d)
        val z = d * (0.5 + sqrt(max(0.0, 0.25 - K / 0.893)))
        val asRequired = Mu / (fy / GAMMA_S * z)
        val asMin = max(0.26 * sqrt(fcu) / fy, MIN_REIN_RATIO) * b * d
        val asFinal = max(asRequired, asMin)
        
        val barDiameter = 16.0
        val barArea = PI * barDiameter * barDiameter / 4.0
        val actualBars = ceil(asFinal / barArea).toInt()
        
        return ReinforcementResult(
            astRequired = asRequired,
            astProvided = actualBars * barArea,
            barDiameter = barDiameter,
            numberOfBars = actualBars,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = true,
            utilizationRatio = asRequired / (actualBars * barArea)
        )
    }

    override fun getMinFootingThickness(): Double = MIN_THICKNESS
    override fun getMinCover(): Double = 50.0
    override fun getPunchingShearCapacity(fcu: Double, perimeter: Double, effectiveDepth: Double): Double = 0.316 * sqrt(fcu/GAMMA_C) * perimeter * effectiveDepth / 1000.0
}
