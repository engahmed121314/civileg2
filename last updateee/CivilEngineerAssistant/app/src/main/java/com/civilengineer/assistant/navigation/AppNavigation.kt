package com.civilengineer.assistant.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * مسارات التنقل في التطبيق
 */
sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Home : Screen("home", "الرئيسية", Icons.Default.Home)

    // تصميم الأعمدة
    data object ColumnsMenu : Screen("columns_menu", "تصميم الأعمدة", Icons.Default.ViewColumn)
    data object ColumnShortRect : Screen("column_short_rect", "عمود قصير مستطيل", Icons.Default.ViewColumn)
    data object ColumnShortCirc : Screen("column_short_circ", "عمود قصير دائري", Icons.Default.Circle)
    data object ColumnLongRect : Screen("column_long_rect", "عمود طويل مستطيل", Icons.Default.ViewColumn)
    data object ColumnLongCirc : Screen("column_long_circ", "عمود طويل دائري", Icons.Default.Circle)
    data object ColumnBiaxial : Screen("column_biaxial", "عمود ثنائي المحور", Icons.Default.Crop)

    // تصميم البلاطات
    data object SlabsMenu : Screen("slabs_menu", "تصميم البلاطات", Icons.Default.Layers)
    data object SlabSolidOneWay : Screen("slab_solid_one_way", "بلاطة مصمتة اتجاه واحد", Icons.Default.Layers)
    data object SlabSolidTwoWay : Screen("slab_solid_two_way", "بلاطة مصمتة اتجاهين", Icons.Default.GridOn)
    data object SlabHollowBlock : Screen("slab_hollow_block", "بلاطة هوردي", Icons.Default.ViewModule)
    data object SlabFlat : Screen("slab_flat", "فلات سلاب", Icons.Default.ViewStream)
    data object SlabRibbed : Screen("slab_ribbed", "بلاطة مضلعة", Icons.Default.Reorder)
    data object SlabCantilever : Screen("slab_cantilever", "بلاطة كابولية", Icons.Default.CallMade)

    // تصميم القواعد
    data object FoundationsMenu : Screen("foundations_menu", "تصميم القواعد", Icons.Default.Foundation)
    data object FootingIsolated : Screen("footing_isolated", "قاعدة منفصلة", Icons.Default.CropSquare)
    data object FootingCombined : Screen("footing_combined", "قاعدة مشتركة", Icons.Default.ViewAgenda)
    data object FootingStrip : Screen("footing_strip", "قاعدة شريطية", Icons.Default.ViewDay)
    data object FootingRaft : Screen("footing_raft", "لبشة (حصيرة)", Icons.Default.Grid3x3)
    data object FootingPile : Screen("footing_pile", "خوازيق", Icons.Default.PushPin)

    // تصميم الكمرات
    data object BeamsMenu : Screen("beams_menu", "تصميم الكمرات", Icons.Default.Straighten)
    data object BeamSimpleRect : Screen("beam_simple_rect", "كمرة بسيطة مستطيلة", Icons.Default.Straighten)
    data object BeamSimpleT : Screen("beam_simple_t", "كمرة T", Icons.Default.TableChart)
    data object BeamContinuous : Screen("beam_continuous", "كمرة مستمرة", Icons.Default.LinearScale)
    data object BeamDeep : Screen("beam_deep", "كمرة عميقة", Icons.Default.Straighten)
    data object BeamCantilever : Screen("beam_cantilever", "كمرة كابولية", Icons.Default.CallMade)
    data object BeamDoubly : Screen("beam_doubly", "كمرة مزدوجة التسليح", Icons.Default.Straighten)

    // حوائط السند
    data object RetainingMenu : Screen("retaining_menu", "حوائط السند", Icons.Default.ViewSidebar)
    data object RetainingGravity : Screen("retaining_gravity", "حائط جاذبي", Icons.Default.ViewSidebar)
    data object RetainingCantilever : Screen("retaining_cantilever", "حائط كابولي", Icons.Default.ViewSidebar)
    data object RetainingCounterfort : Screen("retaining_counterfort", "حائط بدعامات", Icons.Default.ViewSidebar)

    // الخزانات
    data object TanksMenu : Screen("tanks_menu", "تصميم الخزانات", Icons.Default.Water)
    data object TankUndergroundRect : Screen("tank_underground_rect", "خزان أرضي مستطيل", Icons.Default.Water)
    data object TankUndergroundCirc : Screen("tank_underground_circ", "خزان أرضي دائري", Icons.Default.Water)
    data object TankElevated : Screen("tank_elevated", "خزان علوي", Icons.Default.Water)

    // السلالم
    data object StairsMenu : Screen("stairs_menu", "تصميم السلالم", Icons.Default.Stairs)
    data object StairsStraight : Screen("stairs_straight", "سلم مستقيم", Icons.Default.Stairs)
    data object StairsDogLegged : Screen("stairs_dog_legged", "سلم متعرج", Icons.Default.Stairs)
    data object StairsSpiral : Screen("stairs_spiral", "سلم حلزوني", Icons.Default.RotateRight)

    // تحليل الزلازل
    data object EarthquakeMenu : Screen("earthquake_menu", "تحليل الزلازل", Icons.Default.Vibration)
    data object EarthquakeEquivalentStatic : Screen("earthquake_static", "قوة استاتيكية مكافئة", Icons.Default.Vibration)
    data object EarthquakeResponseSpectrum : Screen("earthquake_spectrum", "طيف الاستجابة", Icons.Default.ShowChart)

    // التحليل الإنشائي
    data object StructuralAnalysis : Screen("structural_analysis", "التحليل الإنشائي", Icons.Default.Analytics)
    data object AnalysisSimpleBeam : Screen("analysis_simple_beam", "كمرة بسيطة", Icons.Default.Analytics)
    data object AnalysisContinuousBeam : Screen("analysis_continuous_beam", "كمرة مستمرة", Icons.Default.Analytics)
    data object AnalysisFrame : Screen("analysis_frame", "إطار", Icons.Default.Analytics)

    // الحصر والتكلفة
    data object QuantitySurvey : Screen("quantity_survey", "الحصر الشامل", Icons.Default.Assessment)
    data object CostEstimation : Screen("cost_estimation", "حساب التكلفة", Icons.Default.Payments)

    // محول الوحدات
    data object UnitConverter : Screen("unit_converter", "محول الوحدات", Icons.Default.SwapHoriz)

    // مرجع الأكواد
    data object CodeReference : Screen("code_reference", "مرجع الأكواد", Icons.Default.MenuBook)

    // الإعدادات
    data object Settings : Screen("settings", "الإعدادات", Icons.Default.Settings)
}
