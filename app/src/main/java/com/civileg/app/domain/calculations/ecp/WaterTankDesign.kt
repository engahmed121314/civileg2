package com.civileg.app.domain.calculations.ecp

import kotlin.math.max

class WaterTankDesign {
    
    /**
     * حساب إجهاد الشد في جدران الخزانات الدائرية
     * T = P * R
     */
    fun calculateHoopTension(
        waterHeight: Double,
        radius: Double,
        gammaWater: Double = 10.0
    ): Double {
        val pressureAtBase = gammaWater * waterHeight
        return pressureAtBase * radius // kN/m
    }

    /**
     * تصميم القطاع الخرساني (Working Strain Method) لضمان عدم التشقق
     */
    fun checkCracking(tension: Double, thicknessMm: Double, fcu: Double): Boolean {
        val actualStress = (tension * 1000) / (thicknessMm * 1000)
        // Fct = 0.6 * sqrt(fcu) / eta
        val allowableStress = (0.6 * kotlin.math.sqrt(fcu)) / 1.7 
        return actualStress <= allowableStress
    }
}