package com.civilengineer.app.domain.calculator

import com.civilengineer.app.data.models.CodeType
import com.civilengineer.app.data.models.ColumnDesign
import com.civilengineer.app.data.models.ColumnType
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * محسبة الأعمدة الخرسانية
 * تقوم بحساب جميع معاملات التصميم حسب الأكواد المختلفة
 */
class ColumnCalculator {

    /**
     * حساب النسبة الرشاقة (Slenderness Ratio)
     */
    fun calculateSlendernessRatio(
        height: Double,
        dimension: Double
    ): Double {
        val radius = dimension / 2
        return height / radius
    }

    /**
     * حساب معامل التخفيض (Reduction Factor) حسب الكود المصري
     */
    fun calculateReductionFactorEgyptian(
        slendernessRatio: Double,
        steelPercentage: Double
    ): Double {
        // الصيغة: φ = (1 + 1.25 * n * fy / Ec)^-1
        // حيث n = نسبة الفولاذ
        val factor = 1 + (1.25 * steelPercentage * 0.45)
        return 1.0 / factor
    }

    /**
     * حساب معامل التخفيض حسب الكود الأمريكي (ACI)
     */
    fun calculateReductionFactorACI(
        slendernessRatio: Double,
        steelPercentage: Double
    ): Double {
        return when {
            slendernessRatio <= 22 -> 0.85 * (1 - 0.08 * slendernessRatio / 100)
            else -> 0.75
        }
    }

    /**
     * حساب مساحة الفولاذ الرئيسي المطلوبة
     * Ast = As / fy = (Pu * e + M) / (fy * (d - d'))
     */
    fun calculateRequiredSteelArea(
        axialLoad: Double,
        bendingMoment: Double,
        concreteGrade: String,
        steelGrade: String,
        codeType: CodeType
    ): Double {
        val fcK = getConcreteStrength(concreteGrade) / 1.5 // fck/1.5
        val fy = getSteelStrength(steelGrade)
        
        // حساب التحمل الخرساني
        val fcc = 0.67 * fcK
        
        // عزم الانحناء المكافئ
        val equivalentMoment = bendingMoment + (axialLoad * 0.05)
        
        // مساحة الفولاذ المطلوبة (سم²)
        return (equivalentMoment * 100) / (fy * 0.8)
    }

    /**
     * حساب تحمل التصميم للعمود
     */
    fun calculateColumnCapacity(
        lengthM: Double,
        widthM: Double,
        heightM: Double,
        steelAreaMM2: Double,
        concreteGrade: String,
        steelGrade: String,
        codeType: CodeType
    ): Triple<Double, Double, Double> {
        val area = (lengthM * widthM) * 1_000_000 // تحويل إلى mm²
        val fcK = getConcreteStrength(concreteGrade)
        val fy = getSteelStrength(steelGrade)
        
        // تحمل الخرسانة
        val concreteCapacity = when (codeType) {
            CodeType.EGYPTIAN -> {
                val fcc = 0.67 * fcK / 1.5
                (fcc * area) / 1000 // تحويل إلى kN
            }
            CodeType.AMERICAN -> {
                val fcc = 0.85 * 0.65 * fcK / 1.4
                (fcc * area) / 1000
            }
            CodeType.SAUDI -> {
                val fcc = 0.6 * fcK / 1.5
                (fcc * area) / 1000
            }
        }
        
        // تحمل الفولاذ
        val steelCapacity = when (codeType) {
            CodeType.EGYPTIAN -> (fy * steelAreaMM2) / 1000
            CodeType.AMERICAN -> (0.87 * fy * steelAreaMM2) / 1000
            CodeType.SAUDI -> (0.87 * fy * steelAreaMM2) / 1000
        }
        
        // التحمل الكلي
        val totalCapacity = concreteCapacity + steelCapacity
        
        return Triple(concreteCapacity, steelCapacity, totalCapacity)
    }

    /**
     * اختيار قطر وتباعد الفولاذ المناسب
     */
    fun selectSteelDiameter(
        requiredAreaMM2: Double
    ): Pair<Int, Int> {
        // أقطار الفولاذ المتاحة (ملم)
        val availableDiameters = listOf(10, 12, 14, 16, 18, 20, 22, 25, 28, 32)
        
        for (diameter in availableDiameters) {
            val barArea = PI * (diameter / 2.0).pow(2)
            val numberOfBars = ceil(requiredAreaMM2 / barArea).toInt()
            val actualArea = numberOfBars * barArea
            
            if (actualArea >= requiredAreaMM2) {
                val spacing = ceil(1000 / numberOfBars).toInt()
                return Pair(diameter, spacing)
            }
        }
        
        return Pair(32, 100) // الحد الأقصى
    }

    /**
     * حساب التباعد الأقصى للتسليح
     */
    fun calculateMaxSpacing(
        columnDimension: Double,
        steelDiameter: Int,
        codeType: CodeType
    ): Int {
        return when (codeType) {
            CodeType.EGYPTIAN -> minOf((columnDimension * 1000 / 3).toInt(), 300)
            CodeType.AMERICAN -> minOf((columnDimension * 1000 / 3).toInt(), 450)
            CodeType.SAUDI -> minOf((columnDimension * 1000 / 4).toInt(), 350)
        }
    }

    /**
     * حساب معامل الأمان
     */
    fun calculateSafetyFactor(
        appliedLoad: Double,
        capacity: Double
    ): Double {
        return if (appliedLoad > 0) capacity / appliedLoad else 0.0
    }

    /**
     * حساب معاملات إضافية
     */
    private fun getConcreteStrength(grade: String): Double {
        return when (grade) {
            "C20" -> 20.0
            "C25" -> 25.0
            "C30" -> 30.0
            "C35" -> 35.0
            "C40" -> 40.0
            "C45" -> 45.0
            "C50" -> 50.0
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

    private fun ceil(value: Double): Double {
        return kotlin.math.ceil(value)
    }

    /**
     * إنشاء معادلات شرح التصميم
     */
    fun createDesignEquations(
        columnDesign: ColumnDesign
    ): String {
        return buildString {
            append("معادلات التصميم المستخدمة:\n\n")
            
            append("1. حساب تحمل العمود:\n")
            append("Pu = 0.67 * fck * Ac + 0.87 * fy * Ast\n")
            append("حيث:\n")
            append("fck = درجة الخرسانة = ${columnDesign.concreteGrade}\n")
            append("fy = درجة الفولاذ = ${columnDesign.steelGrade}\n")
            append("Ast = مساحة الفولاذ = ${columnDesign.mainSteelAreaMM2} mm²\n\n")
            
            append("2. معامل الأمان:\n")
            append("SF = Pu / Pa\n")
            append("حيث Pa = الحمل المطبق = ${columnDesign.axialLoadKN} kN\n")
            append("SF = ${columnDesign.safetyFactor}\n\n")
            
            append("3. نسبة الرشاقة:\n")
            append("λ = L / b = ${columnDesign.slendernessRatio}\n\n")
            
            append("4. معامل التخفيض:\n")
            append("φ = ${columnDesign.reductionFactor}\n\n")
            
            append("5. النسبة المئوية للفولاذ:\n")
            append("ρ = Ast / Ac = ${columnDesign.mainSteelPercentage}%\n")
        }
    }
}