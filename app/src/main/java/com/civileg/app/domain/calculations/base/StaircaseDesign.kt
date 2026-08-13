package com.civileg.app.domain.calculations.base

import com.civileg.app.domain.entities.DesignCode

/**
 * أنواع السلالم المدعومة
 */
enum class StairType(val displayName: String) {
    STRAIGHT("Straight Stair"),
    DOG_LEG("Dog-Leg / Quarter-Turn"),
    SPIRAL("Spiral Stair"),
    OPEN_WELL("Open Well Stair")
}

/**
 * بيانات الإدخال لتصميم السلم
 */
data class StaircaseInput(
    val stairType: StairType = StairType.STRAIGHT,
    val span: Double,            // m - horizontal projection of stair flight
    val totalRise: Double,       // m - total vertical height
    val stairWidth: Double,      // m - width of stair
    val waistThickness: Double,  // mm - thickness of waist slab
    val fcu: Double,             // MPa - concrete cube strength
    val fy: Double,              // MPa - steel yield strength
    val deadLoad: Double = 6.0,  // kN/m² - self-weight + finishes
    val liveLoad: Double = 4.0,  // kN/m² - variable load
    val riserCount: Int = 0,     // 0 = auto-calculate
    val going: Double = 0.0      // 0 = auto-calculate
)

/**
 * نتيجة فحص أمان
 */
data class StairSafetyCheck(
    val name: String,
    val value: Double,
    val limit: Double,
    val unit: String,
    val isSafe: Boolean,
    val description: String = ""
)

/**
 * نتيجة تصميم السلم الشاملة
 */
data class StaircaseResult(
    val isSafe: Boolean,
    val designCode: DesignCode,

    // التصميم الهندسي
    val riser: Double,          // mm - rise per step
    val going: Double,          // mm - going per step
    val numberOfRisers: Int,
    val numberOfTreads: Int,
    val slopeAngle: Double,     // degrees
    val inclinedLength: Double, // m - along slope

    // الأحمال
    val factoredLoad: Double,   // kN/m² on slope
    val horizontalLoad: Double, // kN/m² projected horizontal

    // نتائج التحليل
    val maxMoment: Double,      // kN.m per meter width
    val maxShear: Double,       // kN per meter width
    val reactionA: Double,      // kN
    val reactionB: Double,      // kN

    // تصميم الانحناء
    val mainRebar: String,      // e.g., "5Φ12"
    val mainRebarArea: Double,  // mm²/m
    val distributionRebar: String,
    val distributionRebarArea: Double,
    val effectiveDepth: Double, // mm
    val reinforcementRatio: Double,
    val minSteelRatio: Double,

    // تصميم القص
    val shearCapacity: Double,
    val requiredStirrups: String,
    val stirrupDiameter: Double,
    val stirrupSpacing: Double,

    // الانحراف
    val deflection: Double,
    val allowableDeflection: Double,
    val deflectionOk: Boolean,

    // فحوصات الأمان
    val safetyChecks: List<StairSafetyCheck>,
    val codeNotes: List<String>
)

/**
 * واجهة موحدة لتصميم السلالم حسب أي كود إنشائي
 * Stair modeled as simply supported one-way slab (beam per meter width)
 */
interface StaircaseDesign {
    fun designStaircase(input: StaircaseInput): StaircaseResult
}