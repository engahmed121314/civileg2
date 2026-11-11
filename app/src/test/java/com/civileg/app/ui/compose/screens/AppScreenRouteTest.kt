package com.civileg.app.ui.compose.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppScreenRouteTest {

    private val allScreens = listOf(
        AppScreen.Home,
        AppScreen.ColumnDesign,
        AppScreen.BeamDesign,
        AppScreen.SlabDesign,
        AppScreen.TankDesign,
        AppScreen.RetainingWall,
        AppScreen.FootingDesign,
        AppScreen.StairDesign,
        AppScreen.SeismicAnalysis,
        AppScreen.SteelDesign,
        AppScreen.FrameAnalysis,
        AppScreen.BOQ,
        AppScreen.UnitConverter,
        AppScreen.Projects,
        AppScreen.Settings,
        AppScreen.Inventory,
        AppScreen.Calculator,
        AppScreen.SteelTables,
        AppScreen.WaterLevel,
        AppScreen.SiteLayout,
        AppScreen.ProjectSummary,
        AppScreen.ExecutionLog,
        AppScreen.MasterBbs,
        AppScreen.PileFoundation,
        AppScreen.StrapFooting,
        AppScreen.FlatSlab,
        AppScreen.WaffleSlab,
        AppScreen.HordiSlab,
        AppScreen.ShearWall,
        AppScreen.PostTensioned,
        AppScreen.AdvancedDesign,
        AppScreen.RebarTool,
        AppScreen.ConcreteMix,
        AppScreen.QualityEngineer,
        AppScreen.WindLoad,
        AppScreen.BuildingDesigner,
        AppScreen.WallOpening,
        AppScreen.SoilStructure
    )

    @Test
    fun routesAreUnique() {
        val routes = allScreens.map { it.route }

        assertEquals(
            "Every AppScreen route must be unique",
            routes.size,
            routes.toSet().size
        )
    }

    @Test
    fun buildingDesignerUsesDedicatedTitleResource() {
        assertEquals("building_designer", AppScreen.BuildingDesigner.route)
        assertEquals(R.string.building_designer_title, AppScreen.BuildingDesigner.titleResId)
    }

    @Test
    fun projectScopedRoutesAreNotDuplicatedAsToolRoutes() {
        val toolRoutes = listOf(
            AppScreen.Calculator.route,
            AppScreen.UnitConverter.route,
            AppScreen.SteelTables.route,
            AppScreen.BOQ.route,
            AppScreen.Inventory.route,
            AppScreen.SiteLayout.route,
            AppScreen.RebarTool.route,
            AppScreen.ConcreteMix.route,
            AppScreen.QualityEngineer.route,
            AppScreen.WindLoad.route,
            AppScreen.BuildingDesigner.route
        )

        assertTrue(toolRoutes.none { it.contains("{") || it.contains("}") })
    }
}
