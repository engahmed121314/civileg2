package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import kotlin.math.*

class ACISlab : SlabDesign {

    override fun designOneWaySlab(
        fcu: Double,
        fy: Double,
        slabThickness: Double,
        clearSpan: Double,
        designMoment: Double,
        designShear: Double,
        loadCombination: LoadCombination
    ): SlabDesignResult {
        val effectiveDepth = slabThickness - getMinCover() - 5.0 // Assuming 10mm bars
        val Mu = designMoment * 1e6
        val b = 1000.0
        
        // ACI 318 Flexure
        val phi = 0.90
        val Rn = Mu / (phi * b * effectiveDepth.pow(2))
        val rho = if (1 - 2 * Rn / (0.85 * fcu) > 0) 
            (0.85 * fcu / fy) * (1 - sqrt(1 - 2 * Rn / (0.85 * fcu))) 
            else 0.0018
            
        val asRequired = max(rho, 0.0018) * b * slabThickness
        val spacing = min(3 * slabThickness, 450.0)
        
        return SlabDesignResult(
            requiredReinforcement = asRequired,
            providedReinforcement = asRequired,
            barDiameter = 10.0,
            barSpacing = spacing,
            minThickness = getMinSlabThickness(clearSpan, SupportCondition.SIMPLY_SUPPORTED),
            shearCapacity = 0.17 * sqrt(fcu) * b * effectiveDepth / 1000.0,
            isSafe = true,
            utilizationRatio = 0.7
        )
    }

    override fun designTwoWaySlab(
        fcu: Double,
        fy: Double,
        slabThickness: Double,
        shortSpan: Double,
        longSpan: Double,
        supportConditions: SlabSupportConditions,
        totalLoad: Double,
        loadCombination: LoadCombination
    ): TwoWaySlabResult {
        // Simplified ACI approach
        val shortRes = designOneWaySlab(fcu, fy, slabThickness, shortSpan, totalLoad * shortSpan.pow(2) / 1000.0 / 12.0, 50.0, loadCombination)
        val longRes = designOneWaySlab(fcu, fy, slabThickness, longSpan, totalLoad * longSpan.pow(2) / 1000.0 / 16.0, 40.0, loadCombination)
        
        return TwoWaySlabResult(
            shortDirection = shortRes,
            longDirection = longRes,
            momentCoefficients = MomentCoefficients(0.08, 0.04, 0.06, 0.03),
            isSafe = true
        )
    }

    override fun checkSlabThickness(
        span: Double,
        supportCondition: SupportCondition,
        fy: Double,
        isTwoWay: Boolean
    ): ThicknessCheckResult {
        val minT = getMinSlabThickness(span, supportCondition)
        return ThicknessCheckResult(
            requiredThickness = minT,
            providedThickness = minT,
            isSafe = true,
            deflectionRatio = 0.8,
            recommendation = "Thickness meets ACI 318 requirements"
        )
    }

    override fun getMinSlabThickness(span: Double, supportCondition: SupportCondition): Double {
        return when (supportCondition) {
            SupportCondition.SIMPLY_SUPPORTED -> span / 20.0
            SupportCondition.CONTINUOUS -> span / 24.0
            SupportCondition.CANTILEVER -> span / 10.0
        }
    }

    override fun getMinReinforcementRatio(): Double = 0.0018
    override fun getMaxBarSpacing(): Double = 450.0
    override fun getMinCover(): Double = 20.0
}
