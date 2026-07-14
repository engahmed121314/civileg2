package com.civileg.app.ui.compose.screens

import com.civileg.app.R

/**
 * تعريف الشاشات للتنقل في التطبيق مع دعم أيقونات Drawable المخصصة
 */
sealed class AppScreen(val route: String, val title: String, val iconRes: Int) {
    object Home : AppScreen("home", "الرئيسية", R.drawable.ic_home)
    object ColumnDesign : AppScreen("column", "الأعمدة", R.drawable.ic_column)
    object BeamDesign : AppScreen("beam", "الكمرات", R.drawable.ic_beam)
    object SlabDesign : AppScreen("slab", "البلاطات", R.drawable.ic_slab)
    object TankDesign : AppScreen("tank", "خزانات المياه", R.drawable.ic_water)
    object RetainingWall : AppScreen("retaining_wall", "حوائط السند", R.drawable.ic_wall)
    object FootingDesign : AppScreen("footing", "القواعد", R.drawable.ic_footing)
    object StairDesign : AppScreen("stairs", "السلالم", R.drawable.ic_stairs)
    object SeismicAnalysis : AppScreen("seismic", "الأحمال الزلزالية", R.drawable.ic_design)
    object SteelDesign : AppScreen("steel", "المنشآت المعدنية", R.drawable.ic_tools)
    object FrameAnalysis : AppScreen("frame_analysis", "تحليل الإطارات", R.drawable.ic_frame)
    object BOQ : AppScreen("boq", "حساب الكميات", R.drawable.ic_quantity)
    object UnitConverter : AppScreen("converter", "محول الوحدات", R.drawable.ic_converter)
    object Projects : AppScreen("projects", "مشاريعي", R.drawable.ic_folder)
    object Settings : AppScreen("settings", "الإعدادات", R.drawable.ic_settings)
    object Inventory : AppScreen("inventory", "مخزن الموقع", R.drawable.ic_tools)
    object Calculator : AppScreen("calculator", "الحاسبة العلمية", R.drawable.ic_settings)
    object SteelTables : AppScreen("steel_tables", "جداول الحديد", R.drawable.ic_tools)
}