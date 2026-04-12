package com.civileg.app.utils

import kotlin.math.ceil

object SteelTables {
    
    // Egyptian Standard Steel Bars (B400B, B500B)
    data class BarProperties(
        val diameter: Int, // mm
        val area: Double, // mm²
        val weight: Double, // kg/m
        val perimeter: Double // mm
    )
    
    val standardBars = listOf(
        BarProperties(6, 28.3, 0.222, 18.8),
        BarProperties(8, 50.3, 0.395, 25.1),
        BarProperties(10, 78.5, 0.617, 31.4),
        BarProperties(12, 113.1, 0.888, 37.7),
        BarProperties(14, 153.9, 1.208, 44.0),
        BarProperties(16, 201.1, 1.578, 50.3),
        BarProperties(18, 254.5, 1.998, 56.5),
        BarProperties(20, 314.2, 2.466, 62.8),
        BarProperties(22, 380.1, 2.984, 69.1),
        BarProperties(25, 490.9, 3.853, 78.5),
        BarProperties(28, 615.8, 4.834, 88.0),
        BarProperties(32, 804.2, 6.313, 100.5),
        BarProperties(36, 1017.9, 7.990, 113.1)
    )
    
    fun getBarArea(diameter: Int): Double {
        return standardBars.find { it.diameter == diameter }?.area ?: 0.0
    }
    
    fun getBarWeight(diameter: Int): Double {
        return standardBars.find { it.diameter == diameter }?.weight ?: 0.0
    }
    
    fun selectBars(requiredArea: Double, maxBars: Int = 8): Pair<String, Double> {
        // Try different diameters
        for (bar in standardBars.reversed()) { // Start from larger diameters
            val numBars = ceil(requiredArea / bar.area).toInt()
            if (numBars <= maxBars) {
                val providedArea = numBars * bar.area
                return Pair("${numBars}Ø${bar.diameter}", providedArea)
            }
        }
        // If single layer not enough, suggest two layers
        return selectBarsTwoLayers(requiredArea)
    }
    
    private fun selectBarsTwoLayers(requiredArea: Double): Pair<String, Double> {
        for (bar in standardBars.reversed()) {
            val numBars = ceil(requiredArea / bar.area).toInt()
            val perLayer = ceil(numBars / 2.0).toInt()
            if (perLayer <= 5) {
                val providedArea = numBars * bar.area
                return Pair("2×${perLayer}Ø${bar.diameter}", providedArea)
            }
        }
        return Pair("Review Design", 0.0)
    }
    
    // Development length calculation (Egyptian Code)
    fun calculateDevelopmentLength(
        barDiameter: Int,
        concreteStrength: Double,
        steelStrength: Double,
        isTension: Boolean = true
    ): Double {
        val bondStress = when {
            concreteStrength <= 25 -> 1.0
            concreteStrength <= 30 -> 1.2
            concreteStrength <= 35 -> 1.4
            else -> 1.6
        }
        
        val ld = (0.87 * steelStrength * barDiameter) / (4 * bondStress)
        return if (isTension) ld else 0.8 * ld
    }
    
    // Lap splice length
    fun calculateLapLength(
        barDiameter: Int,
        concreteStrength: Double,
        steelStrength: Double,
        isTension: Boolean = true
    ): Double {
        return calculateDevelopmentLength(barDiameter, concreteStrength, steelStrength, isTension) * 1.3
    }
}
