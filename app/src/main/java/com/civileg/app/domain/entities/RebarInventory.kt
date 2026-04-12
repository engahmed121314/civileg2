package com.civileg.app.domain.entities

import kotlin.math.abs

/**
 * نظام إدارة مخزون حديد التسليح الفعلي المتوفر في الموقع
 */
data class RebarInventory(
    val projectId: Int,
    val availableBars: List<RebarStock>,
    val stirrupType: StirrupType = StirrupType.CLOSED,
    val wastePercentage: Double = 5.0,  // هالك افتراضي 5%
    val lapSpliceLength: Double = 50.0, // طول التراكب الافتراضي (قطر السيخ × 50)
    val lastUpdated: Long = System.currentTimeMillis()
)

data class RebarStock(
    val diameter: Double,           // mm
    val availableLength: Double,    // m - الطول المتوفر لكل سيخ (عادة 12م)
    val availableQuantity: Int,     // عدد الأسياخ المتوفرة
    val grade: RebarGrade,          // درجة الحديد
    val supplier: String = "",      // المورد
    val costPerTon: Double = 0.0,   // سعر الطن
    val isPreferred: Boolean = false // مفضل للاستخدام
)

enum class StirrupType(val displayName: String, val codeReference: String) {
    CLOSED("Closed Stirrups (135° hooks)", "ECP 203-4.2.6 / ACI 318-25.7.1"),
    OPEN("Open Stirrups (90° hooks)", "ECP 203-4.2.6 / ACI 318-25.7.1"),
    SEISMIC("Seismic Stirrups (135° + 10d)", "ECP 203-AppB / ACI 318-18.7.5"),
    SPIRAL("Spiral Reinforcement", "ACI 318-10.7.6")
}

enum class RebarGrade(val displayName: String, val fy: Double, val codeReference: String) {
    GRADE_240("Grade 240", 240.0, "ECP 203-2.1"),
    GRADE_360("Grade 360", 360.0, "ECP 203-2.1"),
    GRADE_420("Grade 420", 420.0, "ACI 318-20.2.2 / ECP 203-2.1"),
    GRADE_520("Grade 520", 520.0, "ACI 318-20.2.2"),
    GRADE_690("Grade 690", 690.0, "ACI 318-20.2.2")
}

enum class BarDiameter(val diameter: Double, val area: Double, val weight: Double, val commonName: String) {
    D6(6.0, 28.3, 0.222, "6mm"),
    D8(8.0, 50.3, 0.395, "8mm"),
    D10(10.0, 78.5, 0.617, "10mm"),
    D12(12.0, 113.1, 0.888, "12mm"),
    D14(14.0, 153.9, 1.208, "14mm"),
    D16(16.0, 201.1, 1.578, "16mm"),
    D18(18.0, 254.5, 1.998, "18mm"),
    D19(19.0, 283.5, 2.226, "19mm"),
    D20(20.0, 314.2, 2.466, "20mm"),
    D22(22.0, 380.1, 2.984, "22mm"),
    D25(25.0, 490.9, 3.853, "25mm"),
    D28(28.0, 615.8, 4.834, "28mm"),
    D29(29.0, 660.5, 5.185, "29mm"),
    D32(32.0, 804.2, 6.313, "32mm"),
    D36(36.0, 1017.9, 7.990, "36mm"),
    D40(40.0, 1256.6, 9.865, "40mm");

    companion object {
        fun fromDiameter(diameter: Double): BarDiameter? = 
            entries.find { abs(it.diameter - diameter) < 0.1 }
        
        fun getAvailableDiameters(code: DesignCode): List<BarDiameter> = when(code) {
            DesignCode.ECP -> listOf(D6, D8, D10, D12, D14, D16, D18, D20, D22, D25, D28, D32)
            DesignCode.ACI -> listOf(D10, D12, D16, D19, D22, D25, D29, D32, D36)
            DesignCode.SBC -> listOf(D10, D12, D16, D20, D25, D28, D32, D36, D40)
        }
    }
}

/**
 * نتيجة تحليل المخزون والكميات المطلوبة
 */
data class InventoryAnalysisResult(
    val requiredArea: Double,           // mm² - المساحة المطلوبة
    val providedArea: Double,           // mm² - المساحة المقدمة من المخزون
    val requiredBars: Int,              // عدد الأسياخ المطلوبة
    val availableBars: Int,             // عدد الأسياخ المتوفرة
    val additionalBarsNeeded: Int,      // عدد الأسياخ الإضافية المطلوبة
    val additionalLength: Double,       // m - الطول الإضافي المطلوب
    val additionalWeight: Double,       // ton - الوزن الإضافي المطلوب
    val additionalCost: Double,         // تكلفة الشراء الإضافية
    val wasteLength: Double,            // m - طول الهالك
    val wastePercentage: Double,        // % - نسبة الهالك
    val totalLength: Double,            // m - الطول الكلي (مطلوب + هالك)
    val totalWeight: Double,            // ton - الوزن الكلي
    val isSufficient: Boolean,          // هل المخزون كافٍ؟
    val recommendedDiameter: Double,    // القطر الموصى به
    val cuttingOptimization: List<CuttingPlan>, // خطة القص المثلى
    val warnings: List<String>,
    val codeNotes: List<String>
)

data class CuttingPlan(
    val stockLength: Double,        // m - طول السيخ من المخزون
    val requiredLengths: List<Double>, // m - الأطوال المطلوبة من هذا السيخ
    val wasteLength: Double,        // m - الهالك من هذا السيخ
    val utilizationPercentage: Double // % - نسبة الاستفادة
)
