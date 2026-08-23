package com.civileg.core.calculations.ecp

import com.civileg.core.calculations.base.*
import com.civileg.core.calculations.entities.*
import kotlin.math.*

class ECPBeam : BeamDesign {
    
    companion object {
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15
        private const val BETA_1 = 0.9
        private const val E_S = 200000.0
    }

    override fun calculateFlexureReinforcement(
        fcu: Double,
        fy: Double,
        width: Double,
        effectiveDepth: Double,
        totalDepth: Double,
        designMoment: Double,
        loadCombination: LoadCombination
    ): ReinforcementResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val Mu = designMoment * 1e6
        val K = Mu / (fcu * width * effectiveDepth * effectiveDepth)
        val K_bal = calculateKBal(fcu, fy)
        
        if (K > K_bal) {
            warnings.add("Section is over-reinforced!")
        }
        
        val K_DIVISOR = 0.893
        val leverArm = if (0.25 - K / K_DIVISOR > 0) {
            effectiveDepth * (0.5 + sqrt(0.25 - K / K_DIVISOR))
        } else {
            effectiveDepth * 0.7
        }
        
        val fs = fy / GAMMA_S
        var astRequired = Mu / (fs * leverArm)
        val Ag = width * effectiveDepth
        val minSteel = max(0.26 * sqrt(fcu) / fy, 0.0013) * Ag
        
        if (astRequired < minSteel) {
            astRequired = minSteel
        }
        
        val barDiameter = 16.0
        val barArea = PI * barDiameter * barDiameter / 4
        val numberOfBars = ceil(astRequired / barArea).toInt().coerceIn(2, 12)
        val astProvided = numberOfBars * barArea
        
        val capacity = calculateMomentCapacity(fcu, fy, width, effectiveDepth, astProvided)
        val utilizationRatio = if (capacity > 0) designMoment / capacity else 2.0
        
        codeNotes.add(CodeReference.ECP.BEAM_FLEXURE)
        
        return ReinforcementResult(
            astRequired = astRequired,
            astProvided = astProvided,
            barDiameter = barDiameter,
            numberOfBars = numberOfBars,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = utilizationRatio <= 1.0,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    override fun calculateShearReinforcement(
        fcu: Double,
        fy: Double,
        width: Double,
        effectiveDepth: Double,
        designShear: Double,
        axialLoad: Double,
        loadCombination: LoadCombination
    ): ShearReinforcementResult {
        val Vu = designShear * 1000
        val qcu = 0.24 * sqrt(fcu / GAMMA_C)
        val concreteShearCapacity = qcu * width * effectiveDepth / 1000
        val minStirrups = 0.0015 * width * 1000
        
        var requiredStirrups = if (Vu / 1000 > concreteShearCapacity) {
            val excessShear = (Vu / 1000 - concreteShearCapacity) * 1000
            max(excessShear / (fy / GAMMA_S * effectiveDepth) * 1000, minStirrups)
        } else minStirrups
        
        val stirrupDiameter = 8.0
        val stirrupArea = 2 * PI * stirrupDiameter * stirrupDiameter / 4
        var stirrupSpacing = if (requiredStirrups > 0) stirrupArea * 1000 / requiredStirrups else 200.0
        stirrupSpacing = stirrupSpacing.coerceIn(50.0, 200.0)
        
        val maxShearStress = 0.7 * sqrt(fcu / GAMMA_C)
        val maxShearCapacity = maxShearStress * width * effectiveDepth / 1000
        val isSafe = (Vu / 1000) <= maxShearCapacity
        
        return ShearReinforcementResult(
            concreteShearCapacity = concreteShearCapacity,
            requiredShearReinforcement = requiredStirrups,
            providedShearReinforcement = stirrupArea * 1000 / stirrupSpacing,
            stirrupDiameter = stirrupDiameter,
            stirrupSpacing = stirrupSpacing,
            isSafe = isSafe,
            utilizationRatio = if (maxShearCapacity > 0) (Vu / 1000) / maxShearCapacity else 2.0
        )
    }

    override fun checkDeflection(span: Double, totalDepth: Double, reinforcementRatio: Double, supportCondition: SupportCondition): DeflectionCheckResult = DeflectionCheckResult()
    override fun calculateDevelopmentLength(barDiameter: Double, fy: Double, fcu: Double, barLocation: BarLocation, coating: CoatingType): Double = 0.0
    override fun getMinReinforcementRatio(): Double = 0.0013
    override fun getMaxReinforcementRatio(): Double = 0.04
    override fun getMinShearReinforcementRatio(): Double = 0.0015
    override fun getMaxShearSpacing(): Double = 200.0
    override fun getMinCover(): Double = 40.0
    override fun getDeflectionLimit(span: Double): Double = (span * 1000) / 250

    private fun calculateKBal(fcu: Double, fy: Double): Double {
        val epsilonY = fy / (E_S * GAMMA_S)
        val cOverD = 0.003 / (0.003 + epsilonY)
        val aOverD = BETA_1 * cOverD
        return (0.67 / GAMMA_C) * aOverD * (1.0 - aOverD / 2.0)
    }
    
    private fun calculateMomentCapacity(fcu: Double, fy: Double, width: Double, effectiveDepth: Double, ast: Double): Double {
        val fc = 0.67 * fcu / GAMMA_C
        val fs = fy / GAMMA_S
        val a = (ast * fs) / (fc * width)
        return (ast * fs * (effectiveDepth - a / 2)) / 1e6
    }
}
