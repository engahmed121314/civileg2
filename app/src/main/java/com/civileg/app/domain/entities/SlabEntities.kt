package com.civileg.app.domain.entities

import com.civileg.app.domain.calculations.base.*

/**
 * جميع أنواع البلاطات الخرسانية
 */
sealed class SlabType(val displayName: String, val codeReference: String) {
    
    /**
     * بلاطة مصمتة Solid Slab
     */
    data class Solid(
        val thickness: Double,    // mm
        val shortSpan: Double,    // m
        val longSpan: Double,     // m
        val supportConditions: SlabSupportConditions
    ) : SlabType("Solid Slab", "ECP 203-6.2 / ACI 318-8.3")
    
    /**
     * بلاطة ذات اتجاه واحد One-Way Slab
     */
    data class OneWay(
        val thickness: Double,
        val span: Double,
        val width: Double
    ) : SlabType("One-Way Slab", "ECP 203-6.2.1 / ACI 318-8.3.1")
    
    /**
     * بلاطة ذات اتجاهين Two-Way Slab
     */
    data class TwoWay(
        val thickness: Double,
        val shortSpan: Double,
        val longSpan: Double,
        val supportConditions: SlabSupportConditions,
        val dropPanel: Boolean = false,
        val columnCapital: Boolean = false
    ) : SlabType("Two-Way Slab", "ECP 203-6.2.2 / ACI 318-8.4")
    
    /**
     * بلاطة هوردي Hollow Block Slab
     */
    data class Hordi(
        val totalThickness: Double,   // mm
        val ribWidth: Double,         // mm
        val ribSpacing: Double,       // mm
        val blockWidth: Double,       // mm
        val blockHeight: Double,      // mm
        val span: Double,
        val supportConditions: SlabSupportConditions
    ) : SlabType("Hollow Block (Hordi) Slab", "ECP 203-6.4 / SBC 304-8.4")
    
    /**
     * بلاطة وافل Waffle Slab
     */
    data class Waffle(
        val totalThickness: Double,   // mm
        val ribWidth: Double,         // mm
        val ribDepth: Double,         // mm
        val ribSpacing: Double,       // mm
        val shortSpan: Double,        // m
        val longSpan: Double,         // m
        val supportConditions: SlabSupportConditions
    ) : SlabType("Waffle Slab", "ACI 318-8.4 / ECP 203-6.4")
    
    /**
     * بلاطة بوست تنشن Post-Tensioned Slab
     */
    data class PostTensioned(
        val thickness: Double,
        val shortSpan: Double,
        val longSpan: Double,
        val tendonType: TendonType,
        val prestressForce: Double,   // kN/m
        val eccentricity: Double,     // mm
        val supportConditions: SlabSupportConditions
    ) : SlabType("Post-Tensioned Slab", "ACI 318-24 / ECP 203-9")
    
    /**
     * بلاطة مسبقة الإجهاد Precast Slab
     */
    data class Precast(
        val thickness: Double,
        val width: Double,
        val length: Double,
        val toppingThickness: Double, // mm
        val prestressType: PrestressType
    ) : SlabType("Precast Slab", "ACI 318-16 / PCI Design Handbook")
    
    /**
     * بلاطة مسطحة Flat Plate
     */
    data class FlatPlate(
        val thickness: Double,
        val panelLength: Double,
        val panelWidth: Double,
        val columnSize: Double
    ) : SlabType("Flat Plate", "ACI 318-8.4 / ECP 203-6.5")
    
    /**
     * بلاطة مسطحة مع كمرات مخفية Flat Slab with Drop Panels
     */
    data class FlatSlab(
        val thickness: Double,
        val dropPanelThickness: Double,
        val dropPanelSize: Double,
        val panelLength: Double,
        val panelWidth: Double,
        val columnSize: Double
    ) : SlabType("Flat Slab with Drop Panels", "ACI 318-8.4 / ECP 203-6.5")
}

enum class TendonType(val displayName: String) {
    BONDED("Bonded Tendons"),
    UNBONDED("Unbonded Tendons"),
    EXTERNAL("External Tendons")
}

enum class PrestressType(val displayName: String) {
    PRETENSIONED("Pretensioned"),
    POSTTENSIONED("Post-tensioned")
}

/**
 * نتيجة تصميم متقدمة للبلاطات
 */
data class AdvancedSlabResult(
    val slabType: SlabType,
    val flexureResult: SlabDesignResult,
    val shearCheck: ShearCheckResult,
    val deflectionCheck: DeflectionCheckResult,
    val punchingShearCheck: PunchingShearCheckResult?,
    val reinforcementLayout: ReinforcementLayout,
    val concreteVolume: Double,     // m³
    val formworkArea: Double,       // m²
    val inventoryAnalysis: InventoryAnalysisResult?,
    val postTensionCalculations: PostTensionCalculations?,
    val warnings: List<String>,
    val codeNotes: List<String>
)

data class ReinforcementLayout(
    val topBars: BarLayout,
    val bottomBars: BarLayout,
    val distributionBars: BarLayout?,
    val additionalBars: List<BarLayout>
)

data class BarLayout(
    val diameter: Double,
    val spacing: Double,
    val direction: BarDirection,
    val length: Double,
    val numberOfBars: Int
)

enum class BarDirection { SHORT, LONG, BOTH, DIAGONAL }
