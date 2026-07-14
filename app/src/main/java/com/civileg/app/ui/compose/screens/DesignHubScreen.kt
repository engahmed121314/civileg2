package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class DesignHubItem(
    val screen: AppScreen,
    val title: String,
    val subtitle: String,
    val codes: List<String>,
    val accentColor: Color,
    val icon: ImageVector
)

private val designHubItems = listOf(
    DesignHubItem(AppScreen.BeamDesign, "الكمرات", "تصميم الكمرات الخرسانية", listOf("ECP", "ACI", "SBC"), Color(0xFF1976D2), Icons.Default.AccountBalance),
    DesignHubItem(AppScreen.ColumnDesign, "الأعمدة", "تصميم الأعمدة الخرسانية", listOf("ECP", "ACI", "SBC"), Color(0xFF7B1FA2), Icons.Default.ViewColumn),
    DesignHubItem(AppScreen.SlabDesign, "البلاطات", "البلاطات المصمتة والهوردية", listOf("ECP", "ACI"), Color(0xFF00838F), Icons.Default.ViewWeek),
    DesignHubItem(AppScreen.FootingDesign, "القواعد", "تصميم القواعد المنفصلة", listOf("ECP", "ACI"), Color(0xFF4E342E), Icons.Default.Layers),
    DesignHubItem(AppScreen.TankDesign, "الخزانات", "خزانات المياه", listOf("ECP", "ACI"), Color(0xFF0277BD), Icons.Default.WaterDrop),
    DesignHubItem(AppScreen.RetainingWall, "حوائط السند", "حوائط السند واستقرار التربة", listOf("ECP", "ACI"), Color(0xFF558B2F), Icons.Default.SensorDoor),
    DesignHubItem(AppScreen.StairDesign, "السلالم", "تصميم السلالم الخرسانية", listOf("ECP", "ACI"), Color(0xFFAD1457), Icons.Default.Stairs),
    DesignHubItem(AppScreen.SeismicAnalysis, "الزلازل", "قوى الزلازل والرياح", listOf("ASCE", "SBC"), Color(0xFFBF360C), Icons.Default.Warning),
    DesignHubItem(AppScreen.FrameAnalysis, "تحليل الإطارات", "تحليل الهياكل ثنائية الأبعاد", listOf("ECP", "ACI", "AISC"), Color(0xFF1A237E), Icons.Default.AccountTree)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignHubScreen(
    onNavigateTo: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("التصميم الإنشائي", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    "اختر عنصر التصميم",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(designHubItems) { item ->
                DesignHubCard(item = item, onClick = { onNavigateTo(item.screen.route) })
            }
            item(span = { GridItemSpan(2) }) { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DesignHubCard(item: DesignHubItem, onClick: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.surface luminance < 0.5f

    Card(
        onClick = onClick,
        modifier = Modifier.height(155.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF1E1E30) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(item.accentColor.copy(alpha = 0.06f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(item.accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(item.icon, null, tint = item.accentColor, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        color = if (isDark) Color.White else Color(0xFF1A1A2E)
                    )
                    Text(
                        text = item.subtitle,
                        fontSize = 10.sp,
                        color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575),
                        maxLines = 2,
                        lineHeight = 14.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 4.dp)) {
                        item.codes.forEach { code ->
                            Surface(
                                color = item.accentColor.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(code, fontSize = 8.sp, fontWeight = FontWeight.SemiBold,
                                    color = item.accentColor,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper extension for luminance check
private val Color.luminance: Float
    get() = (red * 299 + green * 587 + blue * 114) / 1000f