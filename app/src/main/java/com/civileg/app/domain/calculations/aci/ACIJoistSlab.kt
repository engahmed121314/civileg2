package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.HordiSlabDesign
import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * تصميم البلاطات الهوردي (Joist Construction) حسب ACI 318-19 Section 9.8
 */
class ACIJoistSlab : HordiSlabDesign {

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
        // ── InputGuard: loud failures, no silent zeros (ADR-010) ──
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("ribWidth", ribWidth)
        InputGuard.positive("ribSpacing", ribSpacing)
        InputGuard.positive("totalThickness", totalThickness)
        InputGuard.positive("toppingThickness", toppingThickness)
        InputGuard.positive("span", span)
        InputGuard.nonNegative("designMoment", designMoment)
        InputGuard.nonNegative("designShear", designShear)

        val fc = fcu * 0.8
        val b = ribSpacing
        val bw = ribWidth
        val h = totalThickness
        val hf = toppingThickness
        val d = h - 25.0 // Effective depth

        // ACI 9.8.1.1 to 9.8.1.3 Limits
        val isGeometrySafe = bw >= 100.0 && 
                             (h - hf) <= 3.5 * bw &&
                             (ribSpacing - bw) <= 762.0

        // Flexure Design
        val phi = 0.9
        val Rn = (designMoment * 1e6) / (phi * b * d * d)
        val m = fy / (0.85 * fc)
        val rho = if (m > 0) (1.0 / m) * (1 - sqrt(max(0.0, 1 - 2 * m * Rn / fy))) else 0.0
        val astRequired = rho * b * d
        
        // Shear Design
        val Vc = 0.17 * sqrt(fc) * bw * d / 1000.0
        val phiVc = 0.75 * 1.1 * Vc

        val isSafe = isGeometrySafe && designShear <= phiVc

        return SlabDesignResult(
            requiredReinforcement = astRequired,
            providedReinforcement = astRequired,
            barDiameter = 12.0,
            barSpacing = ribSpacing,
            minThickness = h,
            shearCapacity = phiVc,
            isSafe = isSafe,
            utilizationRatio = if (phiVc > 0) designShear / phiVc else 0.0
        )
    }
}
