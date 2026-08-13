package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult
import com.civileg.app.domain.entities.ShearCheckResult
import kotlin.math.*

/**
 * محرك تصميم القواعد حسب الكود السعودي SBC 304-2018
 */
class SBCFooting : FootingDesign {
    
    companion object {
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15
        private const val MIN_REIN_RATIO = 0.0018
        private const val MIN_THICKNESS = 300.0
        private const val PHI_SHEAR = 0.75
        private const val PHI_BENDING = 0.90
    }
    
    override fun designIsolatedFooting(
        fcu: Double, fy: Double, columnWidth: Double, columnDepth: Double,
        axialLoad: Double, momentX: Double, momentY: Double,
        soilBearingCapacity: Double, footingDepth: Double, loadCombination: LoadCombination,
        constraints: BoundaryConstraints
    ): FootingDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        
        val factor = loadCombination.getFactorForCode(DesignCode.SBC)
        val P_service = axialLoad / factor
        val Mx_service = momentX / factor
        val My_service = momentY / factor
        
        val t_m = footingDepth / 1000.0
        val netSBC = soilBearingCapacity - 25.0 * t_m
        val areaRequired = P_service * 1.1 / netSBC.coerceAtLeast(soilBearingCapacity * 0.8)
        
        var footingLength = sqrt(areaRequired) * 1000.0
        var footingWidth = footingLength
        
        val roundedWidth = ceil(footingWidth / 50.0) * 50.0
        val roundedLength = ceil(footingLength / 50.0) * 50.0
        val actualArea = (roundedWidth * roundedLength) / 1e6
        
        val q_avg = P_service / actualArea
        val ex = Mx_service / P_service
        val q_max = q_avg * (1.0 + 6.0 * ex / (roundedWidth / 1000.0))
        
        val d = footingDepth - getMinCover() - 10.0
        val cantX = (roundedWidth - columnWidth) / 2.0 / 1000.0
        val Mu_x = q_avg * (roundedLength / 1000.0) * cantX * cantX / 2.0

        val punchingCheck = checkPunchingShear(fcu, columnWidth, columnDepth, d, axialLoad, loadCombination)
        val reinfX = calculateFootingReinforcement(fcu, fy, roundedWidth, roundedLength, d, Mu_x / (roundedLength / 1000.0), FootingDirection.SHORT)
        val mainReinf = reinfX

        val formulas = mutableListOf<String>()
        formulas.add("Req Area A = P / q_net = ${P_service.format(1)} / ${netSBC.format(1)} = ${areaRequired.format(2)} m\u00B2")
        formulas.add("Max Bearing Pressure q_max = ${q_max.format(1)} kPa")

        val checks = mutableListOf<WallSafetyCheck>()
        checks.add(WallSafetyCheck("Soil Bearing", q_max <= soilBearingCapacity, q_max, soilBearingCapacity, "kPa", "q \u2264 q_all", "SBC soil check"))
        checks.add(WallSafetyCheck("Punching Shear", punchingCheck.isSafe, punchingCheck.appliedShear, punchingCheck.shearCapacity, "MPa", "qu \u2264 \u03C6Vc", "Punching check"))
        checks.add(WallSafetyCheck("Flexural Rebar", mainReinf.astProvided >= mainReinf.astRequired, mainReinf.astProvided, mainReinf.astRequired, "mm\u00B2", "As \u2265 As_min", "Flexure check"))

        return FootingDesignResult(
            requiredWidth = roundedWidth, requiredLength = roundedLength, requiredThickness = footingDepth,
            soilPressure = q_avg, maxSoilPressure = q_max, reinforcement = mainReinf,
            punchingShearCheck = punchingCheck, isSafe = checks.all { it.isSafe },
            designCodeName = "SBC 304-2018", formulas = formulas, safetyChecks = checks,
            warnings = warnings, codeNotes = codeNotes
        )
    }

    override fun checkPunchingShear(fcu: Double, columnWidth: Double, columnDepth: Double, effectiveDepth: Double, punchingShearForce: Double, loadCombination: LoadCombination): ShearCheckResult {
        val b0 = 2.0 * (columnWidth + columnDepth) + 4.0 * effectiveDepth
        val fc_prime = 0.8 * fcu
        val vc = minOf(0.33 * sqrt(fc_prime), 0.17 * (1.0 + 2.0) * sqrt(fc_prime))
        val Vu = (punchingShearForce * 1000.0) / (b0 * effectiveDepth)
        return ShearCheckResult(appliedShear = Vu, shearCapacity = PHI_SHEAR * vc, isSafe = Vu <= PHI_SHEAR * vc)
    }

    override fun calculateFootingReinforcement(fcu: Double, fy: Double, footingWidth: Double, footingLength: Double, effectiveDepth: Double, designMoment: Double, direction: FootingDirection): ReinforcementResult {
        val Mu = designMoment * 1e6 * 1.6
        val d = effectiveDepth; val b = 1000.0
        val fc = 0.8 * fcu
        val Rn = Mu / (PHI_BENDING * b * d * d)
        val rho = (0.85 * fc / fy) * (1 - sqrt(max(0.0, 1 - (2 * Rn) / (0.85 * fc))))
        val finalRho = max(rho, MIN_REIN_RATIO)
        val As = finalRho * b * d
        val bars = ceil(As / 113.1).toInt().coerceIn(7, 15)
        return ReinforcementResult(astRequired = As, astProvided = bars * 113.1, barDiameter = 12.0, numberOfBars = bars, tiesDiameter = 0.0, tiesSpacing = 0.0, isSafe = true, utilizationRatio = As/(bars*113.1), spacing = 1000.0/bars, description = "${bars}\u03A612/m'")
    }

    override fun designCombinedFooting(fcu: Double, fy: Double, axialLoad1: Double, axialLoad2: Double, distanceBetweenColumns: Double, soilBearingCapacity: Double, footingDepth: Double, loadCombination: LoadCombination, columnWidth: Double, columnDepth: Double) = throw UnsupportedOperationException()
    override fun designRaftFoundation(fcu: Double, fy: Double, totalLoads: Double, totalArea: Double, moments: Pair<Double, Double>, soilBearingCapacity: Double, raftThickness: Double) = throw UnsupportedOperationException()
    override fun designPileCap(fcu: Double, fy: Double, pileLoad: Double, numberOfPiles: Int, pileDiameter: Double, columnLoads: Double) = throw UnsupportedOperationException()
    override fun getMinFootingThickness() = MIN_THICKNESS
    override fun getMinCover() = 50.0
    override fun getPunchingShearCapacity(fcu: Double, perimeter: Double, effectiveDepth: Double) = 0.33 * sqrt(0.8 * fcu) * perimeter * effectiveDepth

    private fun Double.format(n: Int) = String.format("%.${n}f", this)
}
