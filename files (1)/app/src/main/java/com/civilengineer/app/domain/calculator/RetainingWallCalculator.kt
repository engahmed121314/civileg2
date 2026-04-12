package com.civilengineer.app.domain.calculator

import com.civilengineer.app.data.models.CodeType
import com.civilengineer.app.data.models.RetainingWall
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * محسبة حوائط السند
 */
class RetainingWallCalculator {

    /**
     * حساب ضغط التربة النشط (Active Pressure)
     * Pa = 0.5 * γ * H² * Ka
     * حيث Ka = tan²(45° - φ/2)
     */
    fun calculateActivePressure(
        soilHeight: Double,
        soilUnitWeight: Double,
        soilFrictionAngle: Double
    ): Double {
        // معامل الضغط النشط
        val kaAngle = 45 - (soilFrictionAngle / 2)
        val kaRad = kaAngle * PI / 180
        val ka = kotlin.math.tan(kaRad).pow(2)
        
        // الضغط النشط الكلي
        return 0.5 * soilUnitWeight * soilHeight.pow(2) * ka
    }

    /**
     * حساب ضغط التربة السلبي (Passive Pressure)
     * Pp = 0.5 * γ * H² * Kp
     * حيث Kp = tan²(45° + φ/2)
     */
    fun calculatePassivePressure(
        soilHeight: Double,
        soilUnitWeight: Double,
        soilFrictionAngle: Double
    ): Double {
        val kpAngle = 45 + (soilFrictionAngle / 2)
        val kpRad = kpAngle * PI / 180
        val kp = kotlin.math.tan(kpRad).pow(2)
        
        return 0.5 * soilUnitWeight * soilHeight.pow(2) * kp
    }

    /**
     * حساب عزم الانقلاب
     */
    fun calculateOverturbingMoment(
        activePressure: Double,
        wallHeight: Double
    ): Double {
        // العزم = الضغط × المسافة من القاعدة
        return activePressure * (wallHeight / 3)
    }

    /**
     * حساب عزم المقاومة
     */
    fun calculateResistanceMoment(
        verticalLoad: Double,
        baseWidth: Double
    ): Double {
        return verticalLoad * (baseWidth / 2)
    }

    /**
     * حساب معامل الأمان للانقلاب
     */
    fun calculateOverturbingSafetyFactor(
        resistanceMoment: Double,
        overturbingMoment: Double
    ): Double {
        return if (overturbingMoment > 0) resistanceMoment / overturbingMoment else 0.0
    }

    /**
     * حساب قوة الاحتكاك (Friction Force)
     */
    fun calculateFrictionForce(
        verticalLoad: Double,
        soilFrictionAngle: Double
    ): Double {
        val frictionCoefficient = kotlin.math.tan(soilFrictionAngle * PI / 180)
        return verticalLoad * frictionCoefficient
    }

    /**
     * حساب معامل الأمان للانزلاق
     */
    fun calculateSlidingSafetyFactor(
        frictionForce: Double,
        horizontalForce: Double
    ): Double {
        return if (horizontalForce > 0) frictionForce / horizontalForce else 0.0
    }

    /**
     * حساب الانحراف عن المركز
     */
    fun calculateEccentricity(
        baseWidth: Double,
        verticalLoad: Double,
        overturbingMoment: Double,
        horizontalForce: Double
    ): Double {
        val totalMoment = overturbingMoment
        return totalMoment / verticalLoad
    }

    /**
     * حساب ضغط الأساس (Base Pressure)
     */
    fun calculateBasePressure(
        verticalLoad: Double,
        baseWidth: Double,
        baseLength: Double,
        eccentricity: Double
    ): Pair<Double, Double> {
        val averagePressure = verticalLoad / (baseWidth * baseLength)
        val momentalPressure = (6 * verticalLoad * eccentricity) / (baseWidth * baseLength.pow(2))
        
        val maxPressure = averagePressure + momentalPressure
        val minPressure = averagePressure - momentalPressure
        
        return Pair(maxPressure, minPressure)
    }

    /**
     * حساب مساحة الفولاذ للساق
     */
    fun calculateStemSteelArea(
        activePressure: Double,
        stemHeight: Double,
        stemThickness: Double,
        steelGrade: String,
        concreteGrade: String,
        codeType: CodeType
    ): Double {
        val fy = getSteelStrength(steelGrade)
        
        // العزم على الساق
        val stemMoment = activePressure * stemHeight.pow(2) / 3
        
        // الارتفاع الفعال
        val d = stemThickness * 100 - 40 // بالملم
        
        val momentCapacityFactor = 0.87 * fy * (d - d / 2.0)
        
        return (stemMoment * 1_000_000) / momentCapacityFactor
    }

    /**
     * حساب مساحة الفولاذ للقاعدة
     */
    fun calculateBaseSteelArea(
        verticalLoad: Double,
        baseWidth: Double,
        baseThickness: Double,
        steelGrade: String,
        concreteGrade: String,
        codeType: CodeType
    ): Double {
        val fy = getSteelStrength(steelGrade)
        
        // الضغط على القاعدة
        val basePressure = verticalLoad / baseWidth
        
        // العزم على القاعدة
        val cantileverLength = (baseWidth - 0.3) / 2 // 0.3 عرض الساق تقريباً
        val baseMoment = basePressure * cantileverLength.pow(2) / 2
        
        val d = baseThickness * 100 - 50
        val momentCapacityFactor = 0.87 * fy * (d - d / 2.0)
        
        return (baseMoment * 1_000_000) / momentCapacityFactor
    }

    /**
     * التحقق من الأمان الشامل
     */
    fun checkOverallSafety(
        fsOverturning: Double,
        fsSliding: Double,
        maxBasePressure: Double,
        bearingCapacity: Double,
        minBasePressure: Double,
        minSafetyOverturning: Double = 1.5,
        minSafetySliding: Double = 1.5
    ): Boolean {
        return fsOverturning >= minSafetyOverturning &&
                fsSliding >= minSafetySliding &&
                maxBasePressure <= bearingCapacity &&
                minBasePressure >= 0
    }

    private fun getSteelStrength(grade: String): Double {
        return when (grade) {
            "S235" -> 235.0
            "S355" -> 355.0
            "S500" -> 500.0
            else -> 400.0
        }
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

    fun createDesignEquations(wall: RetainingWall): String {
        return buildString {
            append("معادلات تصميم حائط السند:\n\n")
            
            append("1. ضغط التربة النشط:\n")
            append("Pa = 0.5 * γ * H² * Ka\n")
            append("Pa = ${wall.activePressureKNM} kN/m\n\n")
            
            append("2. عزم الانقلاب:\n")
            append("Mo = Pa * H/3\n")
            append("Mo = ${wall.overturbingMomentKNM} kN.m\n\n")
            
            append("3. معامل الأمان للانقلاب:\n")
            append("FSo = Mr / Mo\n")
            append("FSo = ${wall.factorOfSafetyOverturning}\n\n")
            
            append("4. قوة الاحتكاك:\n")
            append("f = W * tan(φ)\n")
            append("f = ${wall.slidingFrictionKN} kN\n\n")
            
            append("5. معامل الأمان للانزلاق:\n")
            append("FSs = f / Pa\n")
            append("FSs = ${wall.factorOfSafetySliding}\n\n")
            
            append("6. الضغط الأقصى على القاعدة:\n")
            append("qmax = ${wall.maxToePressureKNM2} kN/m²\n\n")
            
            append("7. الضغط الأدنى على القاعدة:\n")
            append("qmin = ${wall.minHeelPressureKNM2} kN/m²\n")
        }
    }
}