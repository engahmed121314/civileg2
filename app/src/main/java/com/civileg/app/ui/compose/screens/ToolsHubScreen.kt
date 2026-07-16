package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ToolItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val bgColor: Color,
    val accentColor: Color,
    val route: String
)

private val toolsList = listOf(
    ToolItem("حاسبة علمية", "عمليات حسابية هندسية", Icons.Default.Calculate, Color(0xFF1B5E20).copy(alpha = 0.12f), Color(0xFF2E7D32), AppScreen.Calculator.route),
    ToolItem("محول الوحدات", "تحويل 7 فئات وحدات", Icons.Default.SwapHoriz, Color(0xFF0D47A1).copy(alpha = 0.12f), Color(0xFF1565C0), AppScreen.UnitConverter.route),
    ToolItem("جداول الحديد", "IPE, HEA, HEB, UPN, زوايا", Icons.Default.TableChart, Color(0xFF4E342E).copy(alpha = 0.12f), Color(0xFF5D4037), AppScreen.SteelTables.route),
    ToolItem("كميات الأعمال", "تقدير التكاليف والجدوى", Icons.Default.Assignment, Color(0xFFE65100).copy(alpha = 0.12f), Color(0xFFEF6C00), AppScreen.BOQ.route),
    ToolItem("مخزن الموقع", "إدارة الحديد والخامات", Icons.Default.Inventory2, Color(0xFFB71C1C).copy(alpha = 0.12f), Color(0xFFD32F2F), AppScreen.Inventory.route)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsHubScreen(
    onNavigateTo: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الأدوات المساعدة", fontWeight = FontWeight.Bold) },
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
                    "أدوات هندسية سريعة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            items(toolsList) { tool ->
                ToolHubCard(tool = tool, onClick = { onNavigateTo(tool.route) })
            }

            item(span = { GridItemSpan(2) }) { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ToolHubCard(tool: ToolItem, onClick: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.surface.luminance < 0.5f
    val cardBg = if (isDark) Color(0xFF1E1E30) else Color.White

    Card(
        onClick = onClick,
        modifier = Modifier.height(150.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tool.bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    tool.icon, null,
                    tint = tool.accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = tool.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (isDark) Color.White else Color(0xFF1A1A2E),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tool.subtitle,
                fontSize = 10.sp,
                color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575),
                maxLines = 2
            )
        }
    }
}

private val Color.luminance: Float
    get() = (red * 299 + green * 587 + blue * 114) / 1000f