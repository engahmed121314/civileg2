package com.civilengineer.app.domain.calculator

import com.civilengineer.app.data.models.CodeType
import com.civilengineer.app.data.models.SlabDesign
import com.civilengineer.app.data.models.SlabType
import com.civilengineer.app.data.models.SupportType
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * محسبة البلاطات الخرسانية
 */
class SlabCalculator {

    /**
     * حساب الحمل الكلي على البلاطة
     * w = (Dead Load + Live Load) * Length * Width
     */
    fun calculateTotalLoad(
        deadLoad: Double,
        liveLoad: Double,
        lengthM: Double,
        widthM: Double
    ): Double {
        return (deadLoad + liveLoad) * lengthM * widthM
    }

    /**
     * حساب أقصى عزم انحناء
     */
    fun calculateMaxBendingMoment(
        lengthM: Double,
        widthM: Double,
        totalLoad: Double,
        slabType: SlabType,
        supportType: SupportType,
        codeType: CodeType
    ): Double {
        // الحمل الموزع
        val w = totalLoad / (lengthM * widthM)
        
        return when (supportType) {
            SupportType.SIMPLY_SUPPORTED -> {
                when (slabType) {
                    SlabType.ONE_WAY -> (w * lengthM.pow(2)) / 8
                    SlabType.TWO_WAY -> (w * lengthM.pow(2)) / 24
                    else -> (w * lengthM.pow(2)) / 8
                }
            }
            SupportType.CANTILEVER -> (w * lengthM.pow(2)) / 2
            SupportType.CONTINUOUS -> (w * lengthM.pow(2)) / 10
            SupportType.FIXED -> (w * lengthM.pow(2)) / 12
        }
    }

    /**
     * حساب قوة القص الأقصى
     */
    fun calculateMaxShearForce(
        lengthM: Double,
        widthM: Double,
        totalLoad: Double,
        supportType: SupportType
    ): Double {
        val w = totalLoad / (lengthM * widthM)
        
        return when (supportType) {
            SupportType.SIMPLY_SUPPORTED -> (w * lengthM) / 2
            SupportType.CANTILEVER -> w * lengthM
            SupportType.CONTINUOUS -> (w * lengthM) / 2
            SupportType.FIXED -> (w * lengthM) / 2
        }
    }

    /**
     * حساب مساحة الفولاذ المطلوبة
     * Ast = M / (0.87 * fy * (d - d'/2))
     */
    fun calculateRequiredSteelArea(
        bendingMomentKNM: Double,
        thicknessMM: Int,
        coverMM: Int,
        steelGrade: String,
        concreteGrade: String,
        codeType: CodeType
    ): Double {
        val fy = getSteelStrength(steelGrade)
        val fcK = getConcreteStrength(concreteGrade)
        
        // الارتفاع الفعال
        val d = thicknessMM - coverMM - 10 // 10 ملم لقطر تقريبي
        
        // Moment Capacity Factor
        val momentCapacityFactor = 0.87 * fy * (d - d / 2.0)
        
        // مساحة الفولاذ المطلوبة
        return (bendingMomentKNM * 1_000_000) / momentCapacityFactor
    }

    /**
     * حساب الحد الأدنى من الفولاذ
     */
    fun calculateMinimumSteelArea(
        lengthM: Double,
        thicknessMM: Int,
        concreteGrade: String,
        codeType: CodeType
    ): Double {
        val fcK = getConcreteStrength(concreteGrade)
        
        return when (codeType) {
            CodeType.EGYPTIAN -> {
                // ρmin = 0.12% من مساحة المقطع
                (0.0012 * lengthM * 1000 * thicknessMM)
            }
            CodeType.AMERICAN -> {
                // ρmin = 1.4 / fy
                val fy = 400.0
                (1.4 / fy * lengthM * 1000 * thicknessMM)
            }
            CodeType.SAUDI -> {
                (0.0015 * lengthM * 1000 * thicknessMM)
            }
        }
    }

    /**
     * اختيار قطر وتباعد الفولاذ
     */
    fun selectSteelDiameterAndSpacing(
        requiredAreaMM2: Double,
        slabLengthM: Double
    ): Pair<Int, Int> {
        val availableDiameters = listOf(8, 10, 12, 14, 16, 18, 20)
        
        for (diameter in availableDiameters) {
            val barArea = PI * (diameter / 2.0).pow(2)
            val spacing = (barArea * slabLengthM * 1000) / requiredAreaMM2
            
            if (spacing <= 300) { // أقصى تباعد 300 ملم
                return Pair(diameter, spacing.toInt())
            }
        }
        
        return Pair(20, 100)
    }

    /**
     * حساب الانحراف
     * δ = (5 * w * L⁴) / (384 * E * I)
     */
    fun calculateDeflection(
        loadKNM2: Double,
        lengthM: Double,
        thicknessMM: Int,
        concreteGrade: String,
        codeType: CodeType
    ): Double {
        val w = loadKNM2 // الحمل الموزع
        val L = lengthM
        val I = (1.0 * thicknessMM.pow(3)) / 12 // عزم القصور الذاتي
        
        // معامل يونج (Young's Modulus)
        val E = when (concreteGrade) {
            "C20" -> 30000.0
            "C25" -> 31000.0
            "C30" -> 32000.0
            "C35" -> 33000.0
            "C40" -> 35000.0
            else -> 30000.0
        }
        
        return (5 * w * L.pow(4)) / (384 * E * I)
    }

    /**
     * حساب الانحراف المسموح به
     */
    fun calculateMaxAllowedDeflection(
        lengthM: Double,
        supportType: SupportType,
        codeType: CodeType
    ): Double {
        val span = lengthM * 1000 // بالملم
        
        return when (codeType) {
            CodeType.EGYPTIAN -> span / 250
            CodeType.AMERICAN -> span / 240
            CodeType.SAUDI -> span / 250
        }
    }

    /**
     * التحقق من الأمان
     */
    fun checkSafety(
        calculatedDeflection: Double,
        maxAllowedDeflection: Double,
        bendingMomentCapacity: Double,
        appliedBendingMoment: Double,
        shearCapacity: Double,
        appliedShearForce: Double
    ): Boolean {
        return calculatedDeflection <= maxAllowedDeflection &&
                bendingMomentCapacity >= appliedBendingMoment &&
                shearCapacity >= appliedShearForce
    }

    private fun getConcreteStrength(grade: String): Double {
        return when (grade) {
            "C20" -> 20.0
            "C25" -> 25.0
            "C30" -> 30.0
            "C35" -> 35.0
            "C40" -> 40.0
            "C45" -> 45.0
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

    fun createDesignEquations(slabDesign: SlabDesign): String {
        return buildString {
            append("معادلات تصميم البلاطة:\n\n")
            
            append("1. أقصى عزم انحناء:\n")
            append("M = ${slabDesign.maxMomentKNM} kN.m\n\n")
            
            append("2. قوة القص الأقصى:\n")
            append("V = ${slabDesign.shearForceKN} kN\n\n")
            
            append("3. مساحة الفولاذ الأسفل:\n")
            append("Ast(bottom) = ${slabDesign.bottomSteelAreaMM2M} mm²/m\n\n")
            
            append("4. الانحراف:\n")
            append("δ = ${slabDesign.deflectionMM} mm\n")
            append("δ(max) = ${slabDesign.maxAllowedDeflectionMM} mm\n\n")
            
            append("5. نوع البلاطة: ${slabDesign.slabType}\n")
            append("6. نوع الاستناد: ${slabDesign.supportType}\n")
        }
    }
}