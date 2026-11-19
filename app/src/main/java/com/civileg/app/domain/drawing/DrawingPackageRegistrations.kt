package com.civileg.app.domain.drawing

/**
 * Registers all calculation screens and their drawing-package status.
 * Called during app initialization (or from tests) to populate [DrawingPackageRegistry].
 *
 * Statuses per §13.11: TODO → LEGACY → PACKAGE. May only move toward PACKAGE.
 */
object DrawingPackageRegistrations {

    fun registerAll() {
        DrawingPackageRegistry.register(PackageEntry("BeamScreen", "beam", listOf("BEAM"), PackageStatus.PACKAGE))
        DrawingPackageRegistry.register(PackageEntry("ColumnScreen", "column", listOf("COLUMN"), PackageStatus.PACKAGE))
        DrawingPackageRegistry.register(PackageEntry("SlabScreen", "slab", listOf("SLAB"), PackageStatus.PACKAGE))
        DrawingPackageRegistry.register(PackageEntry("FootingScreen", "footing", listOf("FOOTING"), PackageStatus.PACKAGE))
        DrawingPackageRegistry.register(PackageEntry("CombinedFootingScreen", "combinedFooting", listOf("COMBINED"), PackageStatus.PACKAGE))
        DrawingPackageRegistry.register(PackageEntry("StrapFootingScreen", "strapFooting", listOf("STRAP"), PackageStatus.PACKAGE))
        DrawingPackageRegistry.register(PackageEntry("PileCapScreen", "pileCap", listOf("PILE"), PackageStatus.PACKAGE))
        DrawingPackageRegistry.register(PackageEntry("RetainingWallScreen", "retainingWall", listOf("WALL"), PackageStatus.PACKAGE))
        DrawingPackageRegistry.register(PackageEntry("TankScreen", "tank", listOf("TANK"), PackageStatus.PACKAGE))
        DrawingPackageRegistry.register(PackageEntry("StairScreen", "stair", listOf("STAIR"), PackageStatus.PACKAGE))
        DrawingPackageRegistry.register(PackageEntry("ShearWallScreen", "shearWall", listOf("SHEAR"), PackageStatus.PACKAGE))
        DrawingPackageRegistry.register(PackageEntry("WallOpeningScreen", "wallOpening", listOf("OPENING"), PackageStatus.PACKAGE))
        DrawingPackageRegistry.register(PackageEntry("PostTensionedScreen", "postTensioned", listOf("PT"), PackageStatus.PACKAGE))
        DrawingPackageRegistry.register(PackageEntry("SteelScreen", "steel", listOf("STEEL"), PackageStatus.PACKAGE))
        DrawingPackageRegistry.register(PackageEntry("SeismicScreen", "seismic", listOf("SEISMIC"), PackageStatus.PACKAGE))
        DrawingPackageRegistry.register(PackageEntry("WindScreen", "wind", listOf("WIND"), PackageStatus.PACKAGE))

        // TODO elements
        DrawingPackageRegistry.register(PackageEntry("SoilStructureScreen", "soilStructure", listOf("SOIL"), PackageStatus.TODO))
        DrawingPackageRegistry.register(PackageEntry("AdvancedDesignScreen", "advancedDesign", listOf("ADVANCED"), PackageStatus.TODO))
        DrawingPackageRegistry.register(PackageEntry("ConcreteMixScreen", "concreteMix", listOf("MIX"), PackageStatus.TODO))
    }
}
