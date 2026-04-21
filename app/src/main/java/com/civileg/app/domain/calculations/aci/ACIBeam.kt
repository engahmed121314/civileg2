package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import kotlin.math.*

class ACIBeam : BeamDesign {
    
    companion object {
        private const val PHI_FLEXURE = 0.9      // معامل الاختزال للانحناء
        private const val PHI_SHEAR = 0.75       // معامل الاختزال للقص
        private const val BETA_1 = 0.85          // عامل كتلة الإجهاد (لـ fc' ≤ 28 MPa)
        private const val LAMBDA = 1.0           // عامل الوزن للخرسانة العادية
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
        
        val Mu = designMoment * 1e6 / loadCombination.factor  // N.mm
        val fc = fcu  // ACI uses fc' directly
        
        // Calculate Rn
        val denominator = PHI_FLEXURE * width * effectiveDepth * effectiveDepth
        val Rn = if (denominator > 0) Mu / denominator else 0.0
        
        // Calculate reinforcement ratio ρ
        val m = if (fc > 0) fy / (0.85 * fc) else 0.0
        val rho = if (m > 0 && (1 - 2 * m * Rn / fy) >= 0) {
            (1 - sqrt(1 - 2 * m * Rn / fy)) / m
        } else {
            0.0
        }
        
        // Check maximum reinforcement (tension-controlled limit)
        val beta1 = calculateBeta1(fc)
        val rho_max = 0.85 * beta1 * (fc / fy) * (0.003 / (0.003 + 0.004))
        if (rho > rho_max) {
            warnings.add("Section exceeds tension-controlled limit (ρ > ρmax)")
            codeNotes.add(CodeReference.ACI.BEAM_REINFORCEMENT_MAX)
        }
        
        // Required reinforcement area
        var astRequired = rho * width * effectiveDepth
        
        // Minimum reinforcement per ACI 9.6.1
        val minSteel1 = 0.25 * sqrt(fc) / fy * width * effectiveDepth
        val minSteel2 = 1.4 / fy * width * effectiveDepth
        val minSteel = max(minSteel1, minSteel2)
        
        if (astRequired < minSteel) {
            astRequired = minSteel
            warnings.add("Minimum reinforcement applied per ${CodeReference.ACI.BEAM_REINFORCEMENT_MIN}")
        }
        
        // Choose bar diameter
        val availableBars = listOf(16.0, 19.0, 22.0, 25.0, 29.0)  // No.5 to No.9
        val barDiameter = availableBars.firstOrNull { 
            val area = PI * it * it / 4
            ceil(astRequired / area) <= 6
        } ?: 19.0
        
        val barArea = PI * barDiameter * barDiameter / 4
        val numberOfBars = ceil(astRequired / barArea).toInt().coerceIn(2, 12)
        val astProvided = numberOfBars * barArea
        
        // Check spacing
        val clearSpacing = if (numberOfBars > 1) {
            (width - 2 * getMinCover() - 2 * 10 - numberOfBars * barDiameter) / (numberOfBars - 1)
        } else {
            width - 2 * getMinCover()
        }
        
        if (clearSpacing < max(25.0, barDiameter)) {
            warnings.add("Consider two layers for bar spacing")
        }
        
        // Calculate actual moment capacity for verification
        val a = if (fc > 0) astProvided * fy / (0.85 * fc * width) else 0.0
        val Mn = astProvided * fy * (effectiveDepth - a / 2)
        val capacity = PHI_FLEXURE * Mn / 1e6  // kN.m
        val utilizationRatio = if (capacity > 0) designMoment / capacity else 2.0
        
        codeNotes.add(CodeReference.ACI.BEAM_FLEXURE)
        codeNotes.add("φ = $PHI_FLEXURE for tension-controlled sections")
        
        return ReinforcementResult(
            astRequired = astRequired,
            astProvided = astProvided,
            barDiameter = barDiameter,
            numberOfBars = numberOfBars,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = utilizationRatio <= 1.0 && rho <= rho_max,
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
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        
        val Vu = designShear * 1000 / loadCombination.factor  // N
        
        // Concrete shear capacity per ACI 22.5.5.1
        val Vc = 0.17 * LAMBDA * sqrt(fcu) * width * effectiveDepth  // N
        
        // Check if reinforcement is needed
        val phiVc = PHI_SHEAR * Vc / 1000  // kN
        
        var requiredStirrups = 0.0
        if (Vu / 1000 > phiVc / 2) {
            // Minimum reinforcement
            val minAv_s = max(
                0.062 * sqrt(fcu) * width / fy,
                0.35 * width / fy
            ) * 1000  // mm²/m
            
            if (Vu / 1000 > phiVc) {
                // Calculate required reinforcement
                val Vs = (Vu / 1000 - phiVc) / PHI_SHEAR  // kN
                requiredStirrups = Vs * 1000 / (fy * effectiveDepth.coerceAtLeast(1.0)) * 1000  // mm²/m
                requiredStirrups = max(requiredStirrups, minAv_s)
            } else {
                requiredStirrups = minAv_s
                warnings.add("Minimum shear reinforcement applied per ACI 9.6.3")
            }
        }
        
        // Choose stirrup diameter
        val stirrupDiameter = if (Vu / 1000 > 0.5 * phiVc) 10.0 else 8.0
        val stirrupArea = 2 * PI * stirrupDiameter * stirrupDiameter / 4  // 2 legs
        
        // Calculate spacing
        var stirrupSpacing = if (requiredStirrups > 0) stirrupArea * 1000 / requiredStirrups else getMaxShearSpacing()
        
        // Spacing limits per ACI 9.7.6.2
        val maxVsLimit = 0.33 * sqrt(fcu) * width * effectiveDepth / 1000
        val VsActual = if (Vu / 1000 > phiVc) (Vu / 1000 - phiVc) / PHI_SHEAR else 0.0
        
        val maxSpacing1 = if (VsActual <= maxVsLimit) {
            minOf(effectiveDepth / 2, 600.0)
        } else {
            minOf(effectiveDepth / 4, 300.0)
        }
        stirrupSpacing = minOf(stirrupSpacing, maxSpacing1, getMaxShearSpacing())
        stirrupSpacing = max(stirrupSpacing, 50.0)
        
        // Maximum shear limit
        val maxVs = 0.66 * sqrt(fcu) * width * effectiveDepth / 1000  // kN
        val maxShearCapacity = phiVc + PHI_SHEAR * maxVs
        val isSafe = (Vu / 1000) <= maxShearCapacity
        
        if (!isSafe) {
            warnings.add("WARNING: Shear exceeds ACI limits - increase section or fc'")
        }
        
        codeNotes.add(CodeReference.ACI.BEAM_SHEAR)
        codeNotes.add("Vc = ${"%.1f".format(Vc/1000)} kN, φVc = ${"%.1f".format(phiVc)} kN")
        
        return ShearReinforcementResult(
            concreteShearCapacity = Vc / 1000,
            requiredShearReinforcement = requiredStirrups,
            providedShearReinforcement = stirrupArea * 1000 / stirrupSpacing,
            stirrupDiameter = stirrupDiameter,
            stirrupSpacing = stirrupSpacing,
            isSafe = isSafe,
            utilizationRatio = if (maxShearCapacity > 0) (Vu / 1000) / maxShearCapacity else 2.0,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    override fun checkDeflection(
        span: Double,
        totalDepth: Double,
        reinforcementRatio: Double,
        supportCondition: SupportCondition
    ): DeflectionCheckResult {
        // Simplified ratio method per ACI Table 24.2.2
        val basicRatio = when (supportCondition) {
            SupportCondition.SIMPLY_SUPPORTED -> 16.0
            SupportCondition.CONTINUOUS -> 21.0
            SupportCondition.CANTILEVER -> 8.0
        }
        
        // Modification for fy ≠ 420 MPa
        val fyFactor = 0.4 + 400.0 / 700.0 // Simplified for fy=400, or use exact
        
        val actualRatio = (span * 1000) / totalDepth
        val allowableRatio = basicRatio * fyFactor
        
        val ratio = actualRatio / allowableRatio
        val allowableDeflection = getDeflectionLimit(span)
        
        val calculatedDeflection = if (ratio > 1.0) {
            allowableDeflection * ratio * 1.2
        } else {
            allowableDeflection * 0.7
        }
        
        return DeflectionCheckResult(
            calculatedDeflection = calculatedDeflection,
            allowableDeflection = allowableDeflection,
            ratio = ratio,
            isSafe = ratio <= 1.0,
            recommendation = if (ratio > 1.0) 
                "Increase depth or perform detailed deflection analysis per ACI 24.2"
                else "Span/depth ratio acceptable"
        )
    }

    override fun calculateDevelopmentLength(
        barDiameter: Double,
        fy: Double,
        fcu: Double,
        barLocation: BarLocation,
        coating: CoatingType
    ): Double {
        // ACI 25.4.2: Ld = (fy * ψt * ψe * ψs * λ) / (1.7 * λ * sqrt(fc')) * db
        
        var psi_t = 1.0
        if (barLocation == BarLocation.TOP) psi_t = 1.3
        
        var psi_e = 1.0
        if (coating == CoatingType.EPOXY_COATED) psi_e = 1.2
        
        val psi_s = if (barDiameter <= 22.0) 1.0 else 0.8
        val lambda = LAMBDA
        
        val numerator = fy * psi_t * psi_e * psi_s * lambda
        val denominator = 1.7 * lambda * sqrt(fcu.coerceAtLeast(1.0))
        
        var Ld = (numerator / denominator) * barDiameter
        
        Ld = max(Ld, 300.0)
        return ceil(Ld / 25) * 25
    }

    private fun calculateBeta1(fc: Double): Double {
        return when {
            fc <= 28 -> 0.85
            fc >= 55 -> 0.65
            else -> 0.85 - (0.05 * (fc - 28) / 7)
        }
    }

    override fun getMinReinforcementRatio(): Double = 0.0033
    override fun getMaxReinforcementRatio(): Double = 0.025
    override fun getMinShearReinforcementRatio(): Double = 0.0015
    override fun getMaxShearSpacing(): Double = 300.0
    override fun getMinCover(): Double = 40.0
    
    override fun getDeflectionLimit(span: Double): Double {
        return (span * 1000) / 240
    }
}
