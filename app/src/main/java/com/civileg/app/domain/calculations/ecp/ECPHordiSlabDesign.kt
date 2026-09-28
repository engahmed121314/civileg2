package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.HordiSlabDesign
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.SlabDesignResult
import kotlin.math.*

/**
 * ECP 203-2020 Hordi/Ribbed/Joist Slab Design Implementation
 *
 * References:
 *  - ECP 203-2020 §6-3: Ribbed slabs (horidi slabs)
 *  - ECP 203-2020 §4-2: Flexural design
 *  - ECP 203-2020 §4-3: Shear design
 */
class ECPHordiSlabDesign : HordiSlabDesign {

    companion object {
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15
        private const val PHI_FLEXURE = 0.9
        private const val PHI_SHEAR = 0.75
    }

    override fun designHordiSlab(
        fcu: Double,
        fy: Double,
        ribWidth: Double,
        ribSpacing: Double,
        totalThickness: Double,
        toppingThickness: Double,
        span: Double,
        designMoment: Double,
        designShear: Double,
        loadCombination: LoadCombination
    ): SlabDesignResult {
        val fc = fcu / GAMMA_C
        val d = totalThickness - 20.0 - 10.0 // cover + half bar diameter

        // Required area of steel per rib
        val Mu = designMoment * 1e6 // N.mm
        val Rn = Mu / (PHI_FLEXURE * ribWidth * d * d)
        val m = fy / (0.85 * fc)
        val rho = (1.0 - sqrt(max(0.0, 1.0 - 2.0 * m * Rn / fy))) / m
        val AsReq = rho * ribWidth * d

        // Min steel
        val minAs = max(0.15 / 100.0 * ribWidth * d, 1.3 * AsReq / 100.0)
        val AsProv = maxOf(AsReq, minAs)

        // Bar selection
        val barDia = 12.0
        val barArea = PI * barDia * barDia / 4.0
        val nBars = ceil(AsProv / barArea).toInt().coerceIn(1, 4)
        val AsProvActual = nBars * barArea

        // Shear capacity
        val Vc = 0.24 * sqrt(fc) * ribWidth * d / 1000.0 * PHI_SHEAR

        val utilizationRatio = if (Vc > 0) designShear / Vc else 2.0
        val isSafe = designShear <= Vc

        return SlabDesignResult(
            requiredReinforcement = AsReq,
            providedReinforcement = AsProvActual,
            barDiameter = barDia,
            barSpacing = ribSpacing,
            minThickness = totalThickness,
            shearCapacity = Vc,
            isSafe = isSafe,
            utilizationRatio = utilizationRatio
        )
    }
}