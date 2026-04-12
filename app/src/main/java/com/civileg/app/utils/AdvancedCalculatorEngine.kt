package com.civileg.app.utils

import kotlin.math.pow

class AdvancedCalculatorEngine {

    // Constants for safety factors
    private val safetyFactorConcrete = 1.5
    private val safetyFactorSteel = 1.15

    // Egyptian ECP 203 calculations
    fun calculateEgyptianBeam(
        width: Double,
        height: Double,
        length: Double,
        load: Double
    ): Double {
        val area = width * height
        val momentOfInertia = (width * height.pow(3)) / 12
        val maxMoment = load * length.pow(2) / 8
        checkSafety(maxMoment, area)
        return maxMoment
    }

    // American ACI 318 calculations
    fun calculateAmericanColumn(
        width: Double,
        height: Double,
        load: Double
    ): Double {
        val area = width * height
        val axialStress = load / area
        checkSafety(axialStress, area)
        return axialStress
    }

    // Saudi SBC 304 calculations for slabs
    fun calculateSaudiSlab(
        length: Double,
        width: Double,
        thickness: Double,
        load: Double
    ): Double {
        val volume = length * width * thickness
        val moment = (load * length.pow(2)) / 12
        checkSafety(moment, volume)
        return moment
    }

    // Check safety compliance for different calculations
    private fun checkSafety(value: Double, area: Double) {
        val allowableStress = area / (safetyFactorConcrete + safetyFactorSteel)
        if (value > allowableStress) {
            throw IllegalArgumentException("Calculation exceeds safe limits!")
        }
    }

    // Footing calculation
    fun calculateFooting(
        width: Double,
        length: Double,
        load: Double
    ): Double {
        val area = width * length
        val pressure = load / area
        checkSafety(pressure, area)
        return pressure
    }
}