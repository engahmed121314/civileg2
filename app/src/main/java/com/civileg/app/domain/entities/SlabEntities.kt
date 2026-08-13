package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * جميع أنواع البلاطات الخرسانية المدعومة في التطبيق
 */
sealed class SlabType(val displayName: String, val codeReference: String) : Parcelable {
    
    @Parcelize
    data class Solid(
        val thickness: Double,
        val shortSpan: Double,
        val longSpan: Double,
        val supportConditions: @RawValue SlabSupportConditions
    ) : SlabType("Solid Slab", "ECP 203-6.2 / ACI 318-8.3")
    
    @Parcelize
    data class OneWay(
        val thickness: Double,
        val span: Double,
        val width: Double
    ) : SlabType("One-Way Slab", "ECP 203-6.2.1 / ACI 318-8.3.1")
    
    @Parcelize
    data class TwoWay(
        val thickness: Double,
        val shortSpan: Double,
        val longSpan: Double,
        val supportConditions: @RawValue SlabSupportConditions,
        val dropPanel: Boolean = false,
        val columnCapital: Boolean = false
    ) : SlabType("Two-Way Slab", "ECP 203-6.2.2 / ACI 318-8.4")
    
    @Parcelize
    data class Hordi(
        val totalThickness: Double,
        val ribWidth: Double,
        val ribSpacing: Double,
        val blockWidth: Double,
        val blockHeight: Double,
        val span: Double,
        val supportConditions: @RawValue SlabSupportConditions
    ) : SlabType("Hollow Block (Hordi) Slab", "ECP 203-6.4 / SBC 304-8.4")
    
    @Parcelize
    data class Waffle(
        val totalThickness: Double,
        val ribWidth: Double,
        val ribDepth: Double,
        val ribSpacing: Double,
        val shortSpan: Double,
        val longSpan: Double,
        val supportConditions: @RawValue SlabSupportConditions
    ) : SlabType("Waffle Slab", "ACI 318-8.4 / ECP 203-6.4")
    
    @Parcelize
    data class PostTensioned(
        val thickness: Double,
        val shortSpan: Double,
        val longSpan: Double,
        val tendonType: TendonType,
        val prestressForce: Double,
        val eccentricity: Double,
        val supportConditions: @RawValue SlabSupportConditions
    ) : SlabType("Post-Tensioned Slab", "ACI 318-24 / ECP 203-9")

    @Parcelize
    data class FlatPlate(
        val thickness: Double,
        val panelLength: Double,
        val panelWidth: Double,
        val columnSize: Double
    ) : SlabType("Flat Plate", "ACI 318-8.4 / ECP 203-6.5")

    @Parcelize
    data class FlatSlab(
        val thickness: Double,
        val dropPanelThickness: Double,
        val dropPanelSize: Double,
        val panelLength: Double,
        val panelWidth: Double,
        val columnSize: Double
    ) : SlabType("Flat Slab with Drop Panels", "ACI 318-8.4 / ECP 203-6.5")

    @Parcelize
    data class Precast(
        val thickness: Double,
        val width: Double,
        val length: Double,
        val toppingThickness: Double,
        val prestressType: PrestressType
    ) : SlabType("Precast Slab", "ACI 318-16 / PCI Design Handbook")
}

@Parcelize
enum class TendonType(val displayName: String) : Parcelable { BONDED("Bonded"), UNBONDED("Unbonded"), EXTERNAL("External") }

@Parcelize
enum class PrestressType(val displayName: String) : Parcelable { PRETENSIONED("Pretensioned"), POSTTENSIONED("Post-tensioned") }

@Parcelize
data class SlabSupportConditions(
    val edgeA: EdgeCondition,
    val edgeB: EdgeCondition,
    val edgeC: EdgeCondition,
    val edgeD: EdgeCondition
) : Parcelable

@Parcelize
enum class EdgeCondition : Parcelable { FIXED, SIMPLY_SUPPORTED, FREE, CONTINUOUS }

@Parcelize
data class SlabDesignResult(
    val requiredReinforcement: Double,
    val providedReinforcement: Double,
    val barDiameter: Double,
    val barSpacing: Double,
    val minThickness: Double,
    val shearCapacity: Double,
    val isSafe: Boolean,
    val utilizationRatio: Double,
    val warnings: List<String> = emptyList(),
    val codeNotes: List<String> = emptyList()
) : Parcelable

@Parcelize
data class AdvancedSlabResult(
    val slabType: SlabType,
    val flexureResult: SlabDesignResult,
    val shearCheck: ShearCheckResult,
    val deflectionCheck: DeflectionCheckResult,
    val punchingShearCheck: PunchingShearCheckResult?,
    val reinforcementLayout: ReinforcementLayout,
    val concreteVolume: Double,
    val formworkArea: Double,
    val inventoryAnalysis: InventoryAnalysisResult?,
    val postTensionCalculations: PostTensionCalculations?,
    val warnings: List<String>,
    val codeNotes: List<String>
) : Parcelable

@Parcelize
data class ReinforcementLayout(
    val topBars: BarLayout,
    val bottomBars: BarLayout,
    val distributionBars: BarLayout?,
    val additionalBars: List<BarLayout>
) : Parcelable

@Parcelize
data class BarLayout(
    val diameter: Double,
    val spacing: Double,
    val direction: BarDirection,
    val length: Double,
    val numberOfBars: Int
) : Parcelable

@Parcelize
enum class BarDirection : Parcelable { SHORT, LONG, BOTH, DIAGONAL }
