package com.civileg.app.domain.entities

import kotlin.math.*

/**
 * جميع أنواع الكمرات المدعومة
 */
sealed class BeamType(val displayName: String, val codeReference: String) {
    
    /**
     * كمرة بسيطة Simply Supported
     */
    data class SimplySupported(
        val span: Double  // m
    ) : BeamType("Simply Supported Beam", "ECP 203-4.2 / ACI 318-8.3")
    
    /**
     * كمرة ثابتة Fixed
     */
    data class Fixed(
        val span: Double
    ) : BeamType("Fixed Beam", "ECP 203-4.2 / ACI 318-8.3")
    
    /**
     * كمرة مستمرة Continuous
     */
    data class Continuous(
        val spans: List<Double>,  // m - طول كل بحر
        val supportConditions: List<EndCondition>  // ظروف التثبيت عند كل دعامة
    ) : BeamType("Continuous Beam", "ECP 203-4.2 / ACI 318-8.3")
    
    /**
     * كمرة كابولي Cantilever
     */
    data class Cantilever(
        val length: Double  // m
    ) : BeamType("Cantilever Beam", "ECP 203-4.2 / ACI 318-8.3")
    
    /**
     * كمرة على 4 نقاط (Fixed-Hinged-Roller)
     */
    data class MultiSupport(
        val totalLength: Double,
        val supportPositions: List<Double>,  // m - موقع كل دعامة من البداية
        val supportTypes: List<EndCondition>  // نوع كل دعامة
    ) : BeamType("Multi-Support Beam", "ACI 318-8.3 / Structural Analysis")
    
    /**
     * كمرة متغيرة المقطع Haunched
     */
    data class Haunched(
        val span: Double,
        val depthAtSupport: Double,  // mm
        val depthAtMidspan: Double   // mm
    ) : BeamType("Haunched Beam", "ACI 318-8.3 / Special Section")
    
    /**
     * كمرة Vierendeel (بدون أقطار)
     */
    data class Vierendeel(
        val span: Double,
        val panelWidth: Double,
        val depth: Double
    ) : BeamType("Vierendeel Beam", "AISC 360 / Special Section")
    
    fun getMaxMoment(totalLoad: Double): Double = when (this) {
        is SimplySupported -> totalLoad * span * span / 8
        is Fixed -> totalLoad * span * span / 12
        is Cantilever -> totalLoad * length * length / 2
        is Continuous -> totalLoad * (spans.maxOrNull() ?: 1.0).pow(2) / 10
        is MultiSupport -> totalLoad * totalLength * totalLength / 10
        is Haunched -> totalLoad * span * span / 10
        is Vierendeel -> totalLoad * span * span / 8
    }
    
    fun getMaxShear(totalLoad: Double): Double = when (this) {
        is SimplySupported -> totalLoad * span / 2
        is Fixed -> totalLoad * span / 2
        is Cantilever -> totalLoad * length
        is Continuous -> (spans.maxOrNull() ?: 1.0) * totalLoad * 0.6
        is MultiSupport -> totalLoad * totalLength * 0.6
        is Haunched -> totalLoad * span / 2
        is Vierendeel -> totalLoad * span / 2
    }
}

/**
 * نتيجة تصميم متقدمة للكمرات
 */
data class AdvancedBeamResult(
    val beamType: BeamType,
    val sectionType: BeamSectionType,
    val flexureResult: ReinforcementResult,
    val shearResult: ShearReinforcementResult,
    val deflectionCheck: DeflectionCheckResult,
    val momentDiagram: List<Pair<Double, Double>>,  // (position, moment)
    val shearDiagram: List<Pair<Double, Double>>,   // (position, shear)
    val inventoryAnalysis: InventoryAnalysisResult?,
    val crackWidthCheck: CrackWidthCheckResult?,
    val developmentLengthCheck: DevelopmentLengthCheckResult?,
    val warnings: List<String>,
    val codeNotes: List<String>
)

enum class BeamSectionType(val displayName: String) {
    RECTANGULAR("Rectangular"),
    T_SECTION("T-Section"),
    L_SECTION("L-Section"),
    CIRCULAR("Circular"),
    COMPOSITE("Composite Steel-Concrete")
}
