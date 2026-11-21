package com.civileg.app.domain.entities

import kotlin.math.PI

/**
 * Commercial Rebar Catalog providing standard metric diameters, exact cross-sectional areas,
 * unit weights, and bend/mandrel multipliers.
 */
object RebarCatalog {
    val COMMERCIAL_DIAMETERS_MM = listOf(8, 10, 12, 14, 16, 18, 20, 22, 25, 28, 32)
    const val STOCK_BAR_LENGTH_M = 12.0

    /**
     * Calculates unit weight in kg/m: w = 0.0061654 * d^2
     * (Derived from steel density 7850 kg/m^3: PI / 4 * 7850 * d^2 * 1e-6)
     */
    fun unitWeightKgPerM(diameterMm: Int): Double {
        return 0.0061654 * diameterMm * diameterMm
    }

    /**
     * Calculates cross-sectional area of a single bar in mm^2: A = PI * d^2 / 4
     */
    fun areaMm2(diameterMm: Int): Double {
        return PI * diameterMm * diameterMm / 4.0
    }

    /**
     * Calculates total steel area in mm^2 for n bars of given diameter.
     */
    fun totalAreaMm2(count: Int, diameterMm: Int): Double {
        return count * areaMm2(diameterMm)
    }

    /**
     * Validates if a diameter is in the commercial catalog.
     */
    fun isValidDiameter(diameterMm: Int): Boolean {
        return diameterMm in COMMERCIAL_DIAMETERS_MM
    }

    /**
     * Finds the closest standard commercial diameter for a given target diameter.
     */
    fun closestCatalogDiameter(targetDiameterMm: Double): Int {
        return COMMERCIAL_DIAMETERS_MM.minByOrNull { kotlin.math.abs(it - targetDiameterMm) } ?: 12
    }

    /**
     * Recommended minimum pin/mandrel diameter multiplier (d_m / d) per code:
     * - stirrups/ties (d <= 16mm): 4d
     * - main bars (d <= 25mm): 6d
     * - large bars (d > 25mm): 8d
     */
    fun minMandrelMultiplier(diameterMm: Int, isStirrup: Boolean = false): Double {
        return when {
            isStirrup && diameterMm <= 16 -> 4.0
            diameterMm <= 25 -> 6.0
            else -> 8.0
        }
    }
}
