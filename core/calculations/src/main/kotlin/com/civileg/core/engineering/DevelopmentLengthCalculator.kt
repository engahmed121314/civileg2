package com.civileg.core.engineering

import com.civileg.core.calculations.entities.DesignCode
import com.civileg.core.calculations.entities.BarLocation
import com.civileg.core.calculations.entities.CoatingType
import kotlin.math.*

/**
 * Unified Development Length Calculator for ECP, ACI, and SBC.
 * [ADR-014] One Source of Truth for development length.
 */
object DevelopmentLengthCalculator {

    fun calculateLd(
        code: DesignCode,
        barDiameter: Double,
        fy: Double,
        fcu: Double,
        barLocation: BarLocation = BarLocation.BOTTOM,
        coating: CoatingType = CoatingType.UNCOATED,
        isTension: Boolean = true
    ): Double {
        return when (code) {
            DesignCode.ECP -> calculateLdECP(barDiameter, fy, fcu, barLocation, coating, isTension)
            DesignCode.ACI, DesignCode.SBC -> calculateLdACI(barDiameter, fy, fcu, barLocation, coating, isTension)
        }
    }

    private fun calculateLdECP(
        db: Double, fy: Double, fcu: Double,
        loc: BarLocation, coating: CoatingType, isTension: Boolean
    ): Double {
        if (!isTension) return max(20 * db, 200.0) // Compression limit

        // ECP 203 §4-2-5-1: Ld = alpha * beta * eta * (fy/gammaS) / (4 * fbu) * db
        val gammaS = 1.15
        val gammaC = 1.5
        val fbu = 0.3 * sqrt(fcu / gammaC)
        
        var factor = 1.0
        if (loc == BarLocation.TOP) factor *= 1.3
        if (coating == CoatingType.EPOXY_COATED) factor *= 1.2
        
        val alpha = 1.0 // straight bar
        val beta = 1.0  // ribbed bar
        
        var ld = factor * alpha * beta * (fy / gammaS) / (4 * fbu) * db
        return ceil(ld / 50.0) * 50.0
    }

    private fun calculateLdACI(
        db: Double, fy: Double, fcu: Double,
        loc: BarLocation, coating: CoatingType, isTension: Boolean
    ): Double {
        val fc_prime = 0.8 * fcu
        val psi_t = if (loc == BarLocation.TOP) 1.3 else 1.0
        val psi_e = if (coating == CoatingType.EPOXY_COATED) 1.2 else 1.0
        val psi_s = if (db <= 19.0) 0.8 else 1.0
        val lambda = 1.0
        
        // ACI 25.4.2.3 simplified
        val ld = (fy * psi_t * psi_e * psi_s) / (1.7 * lambda * sqrt(fc_prime.coerceAtLeast(1.0))) * db
        return ceil(max(ld, 300.0) / 25.0) * 25.0
    }
}
