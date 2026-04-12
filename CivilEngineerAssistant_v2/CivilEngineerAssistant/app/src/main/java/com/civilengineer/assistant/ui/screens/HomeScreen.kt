package com.civilengineer.assistant.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.civilengineer.assistant.navigation.Screen
import com.civilengineer.assistant.ui.components.CategoryCard
import com.civilengineer.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "الإعدادات", tint = Color.White)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ═══════════════════════════════════════════
            // الهيدر - رأس الصفحة
            // ═══════════════════════════════════════════
            item(span = { GridItemSpan(2) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(PrimaryBlue, PrimaryBlueDark)
                            ),
                            RoundedCornerShape(20.dp)
                        )
                ) {
                    // أيقونة خلفية
                    Icon(
                        Icons.Default.Architecture,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier
                            .size(150.dp)
                            .align(Alignment.CenterEnd)
                            .offset(x = 20.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "مساعد المهندس المدني",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Civil Engineer Assistant",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "تصميم إنشائي شامل حسب الأكواد المصرية والسعودية والأمريكية",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════
            // عنوان قسم التصميم
            // ═══════════════════════════════════════════
            item(span = { GridItemSpan(2) }) {
                SectionHeader("التصميم الإنشائي", Icons.Default.Engineering)
            }

            // ═══════════════════════════════════════════
            // بطاقات التصميم
            // ═══════════════════════════════════════════

            // الأعمدة
            item {
                CategoryCard(
                    title = "الأعمدة",
                    subtitle = "قصيرة / طويلة / دائرية",
                    icon = Icons.Default.ViewColumn,
                    backgroundColor = ColumnCardColor,
                    onClick = { navController.navigate(Screen.ColumnsMenu.route) }
                )
            }

            // البلاطات
            item {
                CategoryCard(
                    title = "البلاطات",
                    subtitle = "مصمتة / هوردي / فلات",
                    icon = Icons.Default.Layers,
                    backgroundColor = SlabCardColor,
                    onClick = { navController.navigate(Screen.SlabsMenu.route) }
                )
            }

            // القواعد
            item {
                CategoryCard(
                    title = "القواعد",
                    subtitle = "منفصلة / مشتركة / لبشة",
                    icon = Icons.Default.Foundation,
                    backgroundColor = FoundationCardColor,
                    onClick = { navController.navigate(Screen.FoundationsMenu.route) }
                )
            }

            // الكمرات
            item {
                CategoryCard(
                    title = "الكمرات",
                    subtitle = "بسيطة / مستمرة / عميقة",
                    icon = Icons.Default.Straighten,
                    backgroundColor = BeamCardColor,
                    onClick = { navController.navigate(Screen.BeamsMenu.route) }
                )
            }

            // حوائط السند
            item {
                CategoryCard(
                    title = "حوائط السند",
                    subtitle = "جاذبي / كابولي / بدعامات",
                    icon = Icons.Default.ViewSidebar,
                    backgroundColor = RetainingCardColor,
                    onClick = { navController.navigate(Screen.RetainingMenu.route) }
                )
            }

            // الخزانات
            item {
                CategoryCard(
                    title = "الخزانات",
                    subtitle = "أرضي / علوي / حمام سباحة",
                    icon = Icons.Default.Water,
                    backgroundColor = TankCardColor,
                    onClick = { navController.navigate(Screen.TanksMenu.route) }
                )
            }

            // السلالم
            item {
                CategoryCard(
                    title = "السلالم",
                    subtitle = "مستقيم / متعرج / حلزوني",
                    icon = Icons.Default.Stairs,
                    backgroundColor = StairsCardColor,
                    onClick = { navController.navigate(Screen.StairsMenu.route) }
                )
            }

            // الزلازل
            item {
                CategoryCard(
                    title = "تحليل الزلازل",
                    subtitle = "استاتيكي / ديناميكي",
                    icon = Icons.Default.Vibration,
                    backgroundColor = EarthquakeCardColor,
                    onClick = { navController.navigate(Screen.EarthquakeMenu.route) }
                )
            }

            // ═══════════════════════════════════════════
            // عنوان الأدوات
            // ═══════════════════════════════════════════
            item(span = { GridItemSpan(2) }) {
                SectionHeader("الأدوات والحصر", Icons.Default.Build)
            }

            // الحصر
            item {
                CategoryCard(
                    title = "الحصر الشامل",
                    subtitle = "كميات وأقطار وأطوال",
                    icon = Icons.Default.Assessment,
                    backgroundColor = QuantityCardColor,
                    onClick = { navController.navigate(Screen.QuantitySurvey.route) }
                )
            }

            // التكلفة
            item {
                CategoryCard(
                    title = "حساب التكلفة",
                    subtitle = "عملات متعددة",
                    icon = Icons.Default.Payments,
                    backgroundColor = CostCardColor,
                    onClick = { navController.navigate(Screen.CostEstimation.route) }
                )
            }

            // محول الوحدات
            item {
                CategoryCard(
                    title = "محول الوحدات",
                    subtitle = "طول / قوة / إجهاد",
                    icon = Icons.Default.SwapHoriz,
                    backgroundColor = ConverterCardColor,
                    onClick = { navController.navigate(Screen.UnitConverter.route) }
                )
            }

            // مرجع الأكواد
            item {
                CategoryCard(
                    title = "مرجع الأكواد",
                    subtitle = "ECP / SBC / ACI",
                    icon = Icons.Default.MenuBook,
                    backgroundColor = CodeRefCardColor,
                    onClick = { navController.navigate(Screen.CodeReference.route) }
                )
            }

            // مسافة سفلية
            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = PrimaryBlue,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlueDark
        )
    }
}
