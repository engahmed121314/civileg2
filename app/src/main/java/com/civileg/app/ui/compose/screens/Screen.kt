package com.civileg.app.ui.compose.screens

import com.civileg.app.R

/**
 * تعريف الشاشات للتنقل في التطبيق مع دعم أيقونات Drawable المخصصة
 * titleResId: string resource ID – resolves to the correct language at runtime
 */
sealed class AppScreen(val route: String, val titleResId: Int, val iconRes: Int) {
    object Home : AppScreen("home", R.string.nav_home, R.drawable.ic_home)
    object ColumnDesign : AppScreen("column", R.string.home_column, R.drawable.ic_column)
    object BeamDesign : AppScreen("beam", R.string.home_beam, R.drawable.ic_beam)
    object SlabDesign : AppScreen("slab", R.string.home_slab, R.drawable.ic_slab)
    object TankDesign : AppScreen("tank", R.string.home_tank, R.drawable.ic_water)
    object RetainingWall : AppScreen("retaining_wall", R.string.home_retaining_wall, R.drawable.ic_wall)
    object FootingDesign : AppScreen("footing", R.string.home_footing, R.drawable.ic_footing)
    object StairDesign : AppScreen("stairs", R.string.home_stair, R.drawable.ic_stairs)
    object SeismicAnalysis : AppScreen("seismic", R.string.home_seismic, R.drawable.ic_design)
    object SteelDesign : AppScreen("steel", R.string.home_steel, R.drawable.ic_steel)
    object FrameAnalysis : AppScreen("frame_analysis", R.string.home_frame, R.drawable.ic_frame)
    object BOQ : AppScreen("boq", R.string.quantity_surveying, R.drawable.ic_quantity)
    object UnitConverter : AppScreen("converter", R.string.unit_converter, R.drawable.ic_converter)
    object Projects : AppScreen("projects", R.string.nav_projects, R.drawable.ic_folder)
    object Settings : AppScreen("settings", R.string.nav_settings, R.drawable.ic_settings)
    object Inventory : AppScreen("inventory", R.string.nav_inventory, R.drawable.ic_costing)
    object Calculator : AppScreen("calculator", R.string.home_calculator, R.drawable.ic_calculator)
    object SteelTables : AppScreen("steel_tables", R.string.home_steel_tables, R.drawable.ic_steel)
    object WaterLevel : AppScreen("water_level", R.string.home_water_level, R.drawable.ic_water)
}