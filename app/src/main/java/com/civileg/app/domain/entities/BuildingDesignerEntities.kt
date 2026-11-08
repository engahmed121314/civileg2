package com.civileg.app.domain.entities

enum class StructureType {
    REINFORCED_CONCRETE,
    STEEL_STRUCTURE
}

enum class BuildingFootingCategory {
    ISOLATED,
    COMBINED,
    STRAP,
    RAFT,
    PILE_CAP
}

enum class BuildingColumnCategory {
    RC_RECTANGULAR,
    RC_CIRCULAR,
    SHEAR_WALL,
    STEEL_IPE,
    STEEL_HEB,
    STEEL_HSS
}

enum class BuildingSlabCategory {
    SOLID_SLAB,
    FLAT_SLAB,
    HORDI_RIBBED,
    WAFFLE,
    POST_TENSIONED,
    STEEL_COMPOSITE_DECK
}

data class BuildingNode(
    val id: String,
    val x: Double, // meters
    val y: Double  // meters
)

data class BuildingFooting(
    val id: String,
    val x: Double,
    val y: Double,
    val category: BuildingFootingCategory = BuildingFootingCategory.ISOLATED,
    val length: Double = 1.8,   // m
    val width: Double = 1.8,    // m
    val depth: Double = 0.6,    // m
    val rebarDia: Int = 16,     // mm
    val rebarSpacing: Double = 0.15, // m
    val axialLoad: Double = 600.0,   // kN
    val status: String = "OK"
)

data class BuildingColumn(
    val id: String,
    val x: Double,
    val y: Double,
    val category: BuildingColumnCategory = BuildingColumnCategory.RC_RECTANGULAR,
    val width: Double = 0.3,    // m or profile width
    val depth: Double = 0.6,    // m or profile depth
    val height: Double = 3.5,   // m
    val steelProfileName: String = "IPE 300",
    val rebarDia: Int = 16,
    val rebarCount: Int = 8,
    val axialLoad: Double = 500.0, // kN
    val status: String = "OK"
)

data class BuildingSlab(
    val id: String,
    val nodeIds: List<String>,
    val slabCategory: BuildingSlabCategory = BuildingSlabCategory.FLAT_SLAB,
    val thickness: Double = 0.22,  // m
    val liveLoad: Double = 2.5,    // kN/m²
    val deadLoad: Double = 2.0,    // kN/m² (finishing/partitions)
    val rebarDia: Int = 12,        // mm
    val rebarMeshSpacing: Double = 0.15 // m
)

data class BuildingSteelMember(
    val id: String,
    val startNodeId: String,
    val endNodeId: String,
    val profileName: String = "IPE 300",
    val length: Double = 6.0,
    val unitWeightKgM: Double = 42.2
)

data class BuildingModel(
    val id: String = "BLD_${System.currentTimeMillis()}",
    val name: String = "مشروع مبنى 2D",
    val structureType: StructureType = StructureType.REINFORCED_CONCRETE,
    val plotWidth: Double = 20.0,  // m
    val plotLength: Double = 30.0, // m
    val gridSpacing: Double = 1.0,  // m
    val nodes: List<BuildingNode> = emptyList(),
    val footings: List<BuildingFooting> = emptyList(),
    val columns: List<BuildingColumn> = emptyList(),
    val slabs: List<BuildingSlab> = emptyList(),
    val steelMembers: List<BuildingSteelMember> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class BbsItem(
    val mark: String,
    val elementType: String,
    val elementId: String,
    val barDiaMm: Int,
    val lengthM: Double,
    val count: Int,
    val totalWeightKg: Double,
    val shapeDescription: String
)

data class BuildingBoqItem(
    val id: String,
    val itemCode: String,
    val descriptionAr: String,
    val descriptionEn: String,
    val unitAr: String,
    val quantity: Double,
    val unitPrice: Double,
    val totalPrice: Double,
    val category: String
)

data class BuildingQuantitySummary(
    val totalConcreteVolumeM3: Double = 0.0,
    val totalRebarWeightTon: Double = 0.0,
    val totalSteelProfileWeightTon: Double = 0.0,
    val totalFormworkAreaM2: Double = 0.0,
    val totalExcavationVolumeM3: Double = 0.0,
    val totalCost: Double = 0.0,
    val rebarByDiameterTon: Map<Int, Double> = emptyMap(),
    val boqItems: List<BuildingBoqItem> = emptyList(),
    val bbsItems: List<BbsItem> = emptyList()
)
