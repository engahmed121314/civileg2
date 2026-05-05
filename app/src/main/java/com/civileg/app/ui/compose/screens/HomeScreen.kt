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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateTo: (String) -> Unit,
    onShowSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_logo),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Civil Engineer Pro", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text("Professional Suite", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onShowSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header Welcome Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    )
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        "أهلاً بك مهندسنا",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "ابدأ مشروعك الإنشائي القادم بدقة واحترافية",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Structural Design Section
                item(span = { GridItemSpan(2) }) {
                    SectionTitle("🏗️ التصميم الإنشائي")
                }
                
                items(designModules) { module ->
                    ProfessionalModuleCard(
                        module = module,
                        onClick = { onNavigateTo(module.screen.route) }
                    )
                }

                // Tools Section Header
                item(span = { GridItemSpan(2) }) {
                    SectionTitle("🔧 الأدوات والكميات")
                }

                items(toolModules) { module ->
                    ProfessionalModuleCard(
                        module = module,
                        onClick = { onNavigateTo(module.screen.route) }
                    )
                }
                
                item(span = { GridItemSpan(2) }) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.onBackground
    )
}

data class ModuleItem(
    val screen: AppScreen,
    val description: String,
    val supportedCodes: List<String>
)

private val designModules = listOf(
    ModuleItem(
        AppScreen.ColumnDesign,
        "تصميم الأعمدة الخرسانية",
        listOf("ECP", "ACI", "SBC")
    ),
    ModuleItem(
        AppScreen.BeamDesign,
        "تصميم الكمرات الخرسانية",
        listOf("ECP", "ACI", "SBC")
    ),
    ModuleItem(
        AppScreen.SlabDesign,
        "البلاطات المصمتة واللاكمرية",
        listOf("ECP", "ACI")
    ),
    ModuleItem(
        AppScreen.SteelDesign,
        "تصميم المنشآت المعدنية",
        listOf("AISC", "ECP")
    ),
    ModuleItem(
        AppScreen.TankDesign,
        "خزانات المياه (علوي/أرضي)",
        listOf("ECP", "ACI")
    ),
    ModuleItem(
        AppScreen.RetainingWall,
        "حوائط السند واستقرار التربة",
        listOf("ECP", "ACI")
    ),
    ModuleItem(
        AppScreen.StairDesign,
        "تصميم السلالم الخرسانية",
        listOf("ECP", "ACI")
    ),
    ModuleItem(
        AppScreen.FootingDesign,
        "تصميم القواعد المنفصلة",
        listOf("ECP", "ACI")
    ),
    ModuleItem(
        AppScreen.SeismicAnalysis,
        "حساب قوى الزلازل والرياح",
        listOf("ASCE", "SBC")
    )
)

private val toolModules = listOf(
    ModuleItem(AppScreen.BOQ, "حساب كميات وتكلفة المشروع", listOf()),
    ModuleItem(AppScreen.Inventory, "إدارة مخزن الموقع والخامات", listOf()),
    ModuleItem(AppScreen.UnitConverter, "محول الوحدات الهندسية", listOf()),
    ModuleItem(AppScreen.Projects, "أرشيف المشاريع المحفوظة", listOf())
)

@Composable
private fun ProfessionalModuleCard(
    module: ModuleItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.height(160.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = module.screen.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Column {
                Text(
                    module.screen.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    lineHeight = 16.sp
                )
            }
            
            if (module.supportedCodes.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    module.supportedCodes.take(3).forEach { code ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                code,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
