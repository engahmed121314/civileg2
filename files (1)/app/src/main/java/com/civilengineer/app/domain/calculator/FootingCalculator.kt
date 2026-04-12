package com.civilengineer.app.domain.calculator

import com.civilengineer.app.data.models.CodeType
import com.civilengineer.app.data.models.FootingDesign
import com.civilengineer.app.data.models.FootingType
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * محسبة القواعد الخرسانية
 */
class FootingCalculator {

    /**
     * حساب مساحة القاعدة المطلوبة
     * A = P / qa (حيث qa = تحمل التربة المسموح به)
     */
    fun calculateRequiredFootingArea(
        columnLoad: Double,
        soilBearingCapacity: Double,
        safetyFactor: Double = 1.5
    ): Double {
        val allowableBearingCapacity = soilBearingCapacity / safetyFactor
        return columnLoad / allowableBearingCapacity
    }

    /**
     * حساب الضغط الفعلي على التربة
     */
    fun calculateActualSoilPressure(
        columnLoad: Double,
        footingArea: Double
    ): Double {
        return columnLoad / footingArea
    }

    /**
     * حساب نسبة الأمان للتحمل
     */
    fun calculateBearingCapacityRatio(
        soilBearingCapacity: Double,
        actualPressure: Double
    ): Double {
        return soilBearingCapacity / actualPressure
    }

    /**
     * حساب أقصى عزم انحناء في القاعدة
     */
    fun calculateMaxMomentInFooting(
        footingLength: Double,
        footingWidth: Double,
        columnLength: Double,
        columnWidth: Double,
        soilPressure: Double
    ): Double {
        // الطول البارز
        val cantileverLength = (footingLength - columnLength) / 2
        
        // المساحة البارزة
        val cantileverArea = footingWidth * cantileverLength
        
        // وزن المساحة البارزة
        val cantileveredWeight = cantileverArea * soilPressure
        
        // العزم = القوة × المسافة من المركز
        return cantileveredWeight * (cantileverLength / 2)
    }

    /**
     * حساب قوة القص
     */
    fun calculateShearForceInFooting(
        footingWidth: Double,
        footingLength: Double,
        columnWidth: Double,
        soilPressure: Double
    ): Double {
        val cantileverLength = (footingLength - columnWidth) / 2
        val cantileverArea = footingWidth * cantileverLength
        return cantileverArea * soilPressure
    }

    /**
     * حساب مساحة الفولاذ المطلوبة
     */
    fun calculateRequiredSteelArea(
        bendingMomentKNM: Double,
        footingDepthMM: Int,
        coverMM: Int,
        steelGrade: String,
        concreteGrade: String,
        codeType: CodeType
    ): Double {
        val fy = getSteelStrength(steelGrade)
        val d = footingDepthMM - coverMM - 25 // 25 ملم مسافة أمان
        
        // معامل السعة
        val momentCapacityFactor = 0.87 * fy * (d - d / 2.0)
        
        return (bendingMomentKNM * 1_000_000) / momentCapacityFactor
    }

    /**
     * حساب الحد الأدنى من الفولاذ
     */
    fun calculateMinimumSteelArea(
        footingArea: Double,
        concreteGrade: String,
        codeType: CodeType
    ): Double {
        val minRatio = when (codeType) {
            CodeType.EGYPTIAN -> 0.0012
            CodeType.AMERICAN -> 0.0018
            CodeType.SAUDI -> 0.0015
        }
        
        return minRatio * footingArea * 1_000_000 // تحويل إلى mm²
    }

    /**
     * حساب معامل الأمان للانقلاب (Overturning)
     */
    fun calculateOverturbingSafetyFactor(
        overturbingMoment: Double,
        resistanceMoment: Double
    ): Double {
        return if (overturbingMoment > 0) resistanceMoment / overturbingMoment else 0.0
    }

    /**
     * حساب معامل الأمان للانزلاق (Sliding)
     */
    fun calculateSlidingSafetyFactor(
        slidingFriction: Double,
        horizontalForce: Double
    ): Double {
        return if (horizontalForce > 0) slidingFriction / horizontalForce else 0.0
    }

    /**
     * حساب الانحراف عن المركز (Eccentricity)
     */
    fun calculateEccentricity(
        baseWidth: Double,
        verticalLoad: Double,
        horizontalForce: Double,
        overturingMoment: Double
    ): Double {
        val netMoment = overturingMoment
        return netMoment / verticalLoad
    }

    /**
     * حساب ضغط الأساس والكعب (Toe & Heel Pressure)
     */
    fun calculateToeHeelPressure(
        verticalLoad: Double,
        overturingMoment: Double,
        baseWidth: Double,
        baseLength: Double
    ): Pair<Double, Double> {
        val averagePressure = verticalLoad / (baseWidth * baseLength)
        val momentPressure = (6 * overturingMoment) / (baseWidth * baseLength.pow(2))
        
        val toePressure = averagePressure + momentPressure
        val heelPressure = averagePressure - momentPressure
        
        return Pair(toePressure, heelPressure)
    }

    /**
     * اختيار قطر وتباعد الفولاذ
     */
    fun selectSteelDiameterAndSpacing(
        requiredAreaMM2: Double,
        footingLength: Double
    ): Pair<Int, Int> {
        val availableDiameters = listOf(12, 16, 20, 25, 32)
        
        for (diameter in availableDiameters) {
            val barArea = PI * (diameter / 2.0).pow(2)
            val numberOfBars = (requiredAreaMM2 / barArea).toInt() + 1
            val spacing = (footingLength * 1000) / numberOfBars
            
            if (spacing <= 300) {
                return Pair(diameter, spacing.toInt())
            }
        }
        
        return Pair(32, 100)
    }

    private fun getConcreteStrength(grade: String): Double {
        return when (grade) {
            "C20" -> 20.0
            "C25" -> 25.0
            "C30" -> 30.0
            "C35" -> 35.0
            "C40" -> 40.0
            else -> 30.0
        }
    }

    private fun getSteelStrength(grade: String): Double {
        return when (grade) {
            "S235" -> 235.0
            "S355" -> 355.0
            "S500" -> 500.0
            else -> 400.0
        }
    }

    fun createDesignEquations(footing: FootingDesign): String {
        return buildString {
            append("معادلات تصميم القاعدة:\n\n")
            
            append("1. مساحة القاعدة:\n")
            append("A = P / qa\n")
            append("A = ${footing.columnLoadKN} / ${footing.soilBearingCapacityKNM2}\n")
            append("A = ${footing.footingAreaM2} m²\n\n")
            
            append("2. الضغط الفعلي على التربة:\n")
            append("q = P / A = ${footing.actualSoilPressureKNM2} kN/m²\n\n")
            
            append("3. أقصى عزم انحناء:\n")
            append("M = ${footing.maxMomentKNM} kN.m\n\n")
            
            append("4. قوة القص:\n")
            append("V = ${footing.shearForceKN} kN\n\n")
            
            append("5. معامل الأمان للانقلاب:\n")
            append("FSo = ${footing.factorOfSafetyOverturning}\n\n")
            
            append("6. معامل الأمان للانزلاق:\n")
            append("FSs = ${footing.factorOfSafetySliding}\n\n")
            
            append("7. الانحراف عن المركز:\n")
            append("e = ${footing.eccentricityM} m\n")
        }
    }
}