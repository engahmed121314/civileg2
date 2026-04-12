package com.civilengineer.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.civilengineer.assistant.navigation.Screen
import com.civilengineer.assistant.ui.components.CategoryCard
import com.civilengineer.assistant.ui.components.EngineeringTopBar
import com.civilengineer.assistant.ui.theme.*

// ═══════════════════════════════════════════════════════════
// شاشة قائمة الأعمدة
// ═══════════════════════════════════════════════════════════

@Composable
fun ColumnsMenuScreen(navController: NavController) {
    SubMenuLayout(
        title = "تصميم الأعمدة",
        subtitle = "اختر نوع العمود",
        navController = navController,
        items = listOf(
            SubMenuItem("عمود قصير مستطيل", "Short Rectangular Column", Icons.Default.ViewColumn, ColumnCardColor) {
                navController.navigate(Screen.ColumnShortRect.route)
            },
            SubMenuItem("عمود قصير دائري", "Short Circular Column", Icons.Default.Circle, ColumnCardColor.copy(alpha = 0.85f)) {
                navController.navigate(Screen.ColumnShortCirc.route)
            },
            SubMenuItem("عمود طويل مستطيل", "Long Rectangular Column", Icons.Default.ViewColumn, ColumnCardColor.copy(alpha = 0.7f)) {
                navController.navigate(Screen.ColumnLongRect.route)
            },
            SubMenuItem("عمود طويل دائري", "Long Circular Column", Icons.Default.Circle, ColumnCardColor.copy(alpha = 0.6f)) {
                navController.navigate(Screen.ColumnLongCirc.route)
            },
            SubMenuItem("عمود ثنائي المحور", "Biaxial Column", Icons.Default.Crop, ColumnCardColor.copy(alpha = 0.5f)) {
                navController.navigate(Screen.ColumnBiaxial.route)
            },
        )
    )
}

// ═══════════════════════════════════════════════════════════
// شاشة قائمة البلاطات
// ═══════════════════════════════════════════════════════════

@Composable
fun SlabsMenuScreen(navController: NavController) {
    SubMenuLayout(
        title = "تصميم البلاطات",
        subtitle = "اختر نوع البلاطة",
        navController = navController,
        items = listOf(
            SubMenuItem("بلاطة مصمتة باتجاه واحد", "One-Way Solid Slab", Icons.Default.Layers, SlabCardColor) {
                navController.navigate(Screen.SlabSolidOneWay.route)
            },
            SubMenuItem("بلاطة مصمتة باتجاهين", "Two-Way Solid Slab", Icons.Default.GridOn, SlabCardColor.copy(alpha = 0.85f)) {
                navController.navigate(Screen.SlabSolidTwoWay.route)
            },
            SubMenuItem("بلاطة هوردي", "Hollow Block Slab", Icons.Default.ViewModule, SlabCardColor.copy(alpha = 0.7f)) {
                navController.navigate(Screen.SlabHollowBlock.route)
            },
            SubMenuItem("فلات سلاب", "Flat Slab", Icons.Default.ViewStream, SlabCardColor.copy(alpha = 0.6f)) {
                navController.navigate(Screen.SlabFlat.route)
            },
            SubMenuItem("بلاطة مضلعة", "Ribbed Slab", Icons.Default.Reorder, SlabCardColor.copy(alpha = 0.5f)) {
                navController.navigate(Screen.SlabRibbed.route)
            },
            SubMenuItem("بلاطة كابولية", "Cantilever Slab", Icons.Default.CallMade, SlabCardColor.copy(alpha = 0.4f)) {
                navController.navigate(Screen.SlabCantilever.route)
            },
        )
    )
}

// ═══════════════════════════════════════════════════════════
// شاشة قائمة القواعد
// ═══════════════════════════════════════════════════════════

@Composable
fun FoundationsMenuScreen(navController: NavController) {
    SubMenuLayout(
        title = "تصميم القواعد",
        subtitle = "اختر نوع القاعدة",
        navController = navController,
        items = listOf(
            SubMenuItem("قاعدة منفصلة", "Isolated Footing", Icons.Default.CropSquare, FoundationCardColor) {
                navController.navigate(Screen.FootingIsolated.route)
            },
            SubMenuItem("قاعدة مشتركة", "Combined Footing", Icons.Default.ViewAgenda, FoundationCardColor.copy(alpha = 0.85f)) {
                navController.navigate(Screen.FootingCombined.route)
            },
            SubMenuItem("قاعدة شريطية", "Strip Footing", Icons.Default.ViewDay, FoundationCardColor.copy(alpha = 0.7f)) {
                navController.navigate(Screen.FootingStrip.route)
            },
            SubMenuItem("لبشة (حصيرة)", "Raft Foundation", Icons.Default.Grid3x3, FoundationCardColor.copy(alpha = 0.6f)) {
                navController.navigate(Screen.FootingRaft.route)
            },
            SubMenuItem("خوازيق", "Pile Foundation", Icons.Default.PushPin, FoundationCardColor.copy(alpha = 0.5f)) {
                navController.navigate(Screen.FootingPile.route)
            },
        )
    )
}

// ═══════════════════════════════════════════════════════════
// شاشة قائمة الكمرات
// ═══════════════════════════════════════════════════════════

@Composable
fun BeamsMenuScreen(navController: NavController) {
    SubMenuLayout(
        title = "تصميم الكمرات",
        subtitle = "اختر نوع الكمرة",
        navController = navController,
        items = listOf(
            SubMenuItem("كمرة بسيطة مستطيلة", "Simple Rectangular Beam", Icons.Default.Straighten, BeamCardColor) {
                navController.navigate(Screen.BeamSimpleRect.route)
            },
            SubMenuItem("كمرة T", "T-Beam", Icons.Default.TableChart, BeamCardColor.copy(alpha = 0.85f)) {
                navController.navigate(Screen.BeamSimpleT.route)
            },
            SubMenuItem("كمرة مستمرة", "Continuous Beam", Icons.Default.LinearScale, BeamCardColor.copy(alpha = 0.7f)) {
                navController.navigate(Screen.BeamContinuous.route)
            },
            SubMenuItem("كمرة عميقة", "Deep Beam", Icons.Default.Straighten, BeamCardColor.copy(alpha = 0.6f)) {
                navController.navigate(Screen.BeamDeep.route)
            },
            SubMenuItem("كمرة كابولية", "Cantilever Beam", Icons.Default.CallMade, BeamCardColor.copy(alpha = 0.5f)) {
                navController.navigate(Screen.BeamCantilever.route)
            },
            SubMenuItem("كمرة مزدوجة التسليح", "Doubly Reinforced", Icons.Default.Straighten, BeamCardColor.copy(alpha = 0.4f)) {
                navController.navigate(Screen.BeamDoubly.route)
            },
        )
    )
}

// ═══════════════════════════════════════════════════════════
// شاشة قائمة حوائط السند
// ═══════════════════════════════════════════════════════════

@Composable
fun RetainingMenuScreen(navController: NavController) {
    SubMenuLayout(
        title = "حوائط السند",
        subtitle = "اختر نوع حائط السند",
        navController = navController,
        items = listOf(
            SubMenuItem("حائط سند جاذبي", "Gravity Retaining Wall", Icons.Default.ViewSidebar, RetainingCardColor) {
                navController.navigate(Screen.RetainingGravity.route)
            },
            SubMenuItem("حائط سند كابولي", "Cantilever Retaining Wall", Icons.Default.ViewSidebar, RetainingCardColor.copy(alpha = 0.75f)) {
                navController.navigate(Screen.RetainingCantilever.route)
            },
            SubMenuItem("حائط سند بدعامات", "Counterfort Retaining Wall", Icons.Default.ViewSidebar, RetainingCardColor.copy(alpha = 0.55f)) {
                navController.navigate(Screen.RetainingCounterfort.route)
            },
        )
    )
}

// ═══════════════════════════════════════════════════════════
// شاشة قائمة الخزانات
// ═══════════════════════════════════════════════════════════

@Composable
fun TanksMenuScreen(navController: NavController) {
    SubMenuLayout(
        title = "تصميم الخزانات",
        subtitle = "اختر نوع الخزان",
        navController = navController,
        items = listOf(
            SubMenuItem("خزان أرضي مستطيل", "Underground Rectangular Tank", Icons.Default.Water, TankCardColor) {
                navController.navigate(Screen.TankUndergroundRect.route)
            },
            SubMenuItem("خزان أرضي دائري", "Underground Circular Tank", Icons.Default.Water, TankCardColor.copy(alpha = 0.75f)) {
                navController.navigate(Screen.TankUndergroundCirc.route)
            },
            SubMenuItem("خزان علوي", "Elevated Tank", Icons.Default.Water, TankCardColor.copy(alpha = 0.55f)) {
                navController.navigate(Screen.TankElevated.route)
            },
        )
    )
}

// ═══════════════════════════════════════════════════════════
// شاشة قائمة السلالم
// ═══════════════════════════════════════════════════════════

@Composable
fun StairsMenuScreen(navController: NavController) {
    SubMenuLayout(
        title = "تصميم السلالم",
        subtitle = "اختر نوع السلم",
        navController = navController,
        items = listOf(
            SubMenuItem("سلم مستقيم", "Straight Staircase", Icons.Default.Stairs, StairsCardColor) {
                navController.navigate(Screen.StairsStraight.route)
            },
            SubMenuItem("سلم متعرج", "Dog-Legged Staircase", Icons.Default.Stairs, StairsCardColor.copy(alpha = 0.75f)) {
                navController.navigate(Screen.StairsDogLegged.route)
            },
            SubMenuItem("سلم حلزوني", "Spiral Staircase", Icons.Default.RotateRight, StairsCardColor.copy(alpha = 0.55f)) {
                navController.navigate(Screen.StairsSpiral.route)
            },
        )
    )
}

// ═══════════════════════════════════════════════════════════
// شاشة قائمة الزلازل
// ═══════════════════════════════════════════════════════════

@Composable
fun EarthquakeMenuScreen(navController: NavController) {
    SubMenuLayout(
        title = "تحليل الزلازل",
        subtitle = "اختر طريقة التحليل",
        navController = navController,
        items = listOf(
            SubMenuItem("القوة الاستاتيكية المكافئة", "Equivalent Static Force", Icons.Default.Vibration, EarthquakeCardColor) {
                navController.navigate(Screen.EarthquakeEquivalentStatic.route)
            },
        )
    )
}

// ═══════════════════════════════════════════════════════════
// قالب شاشة القائمة الفرعية
// ═══════════════════════════════════════════════════════════

data class SubMenuItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
fun SubMenuLayout(
    title: String,
    subtitle: String,
    navController: NavController,
    items: List<SubMenuItem>
) {
    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = title,
                subtitle = subtitle,
                onBackClick = { navController.popBackStack() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items.forEach { item ->
                CategoryCard(
                    title = item.title,
                    subtitle = item.subtitle,
                    icon = item.icon,
                    backgroundColor = item.color,
                    onClick = item.onClick
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
