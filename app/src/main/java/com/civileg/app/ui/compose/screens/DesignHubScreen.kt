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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.R

private data class DesignHubItem(
    val screen: AppScreen,
    val titleRes: Int,
    val subtitleRes: Int,
    val codes: List<String>,
    val accentColor: Color,
    val icon: ImageVector
)

private val designHubItems = listOf(
    DesignHubItem(AppScreen.BeamDesign,     R.string.home_beam,     R.string.home_beam_sub,     listOf("ECP", "ACI", "SBC"), Color(0xFF1976D2), Icons.Default.AccountBalance),
    DesignHubItem(AppScreen.ColumnDesign,    R.string.home_column,   R.string.home_column_sub,   listOf("ECP", "ACI", "SBC"), Color(0xFF7B1FA2), Icons.Default.ViewColumn),
    DesignHubItem(AppScreen.SlabDesign,      R.string.home_slab,     R.string.home_slab_sub,     listOf("ECP", "ACI"),        Color(0xFF00838F), Icons.Default.ViewWeek),
    DesignHubItem(AppScreen.FootingDesign,   R.string.home_footing,  R.string.home_footing_sub,  listOf("ECP", "ACI"),        Color(0xFF4E342E), Icons.Default.Layers),
    DesignHubItem(AppScreen.TankDesign,      R.string.home_tank,     R.string.hub_design_tank_sub, listOf("ECP", "ACI"),       Color(0xFF0277BD), Icons.Default.WaterDrop),
    DesignHubItem(AppScreen.RetainingWall,   R.string.home_retaining_wall, R.string.home_retaining_wall_sub, listOf("ECP", "ACI"), Color(0xFF558B2F), Icons.Default.SensorDoor),
    DesignHubItem(AppScreen.StairDesign,     R.string.home_stair,    R.string.home_stair_sub,    listOf("ECP", "ACI"),        Color(0xFFAD1457), Icons.Default.Stairs),
    DesignHubItem(AppScreen.SeismicAnalysis, R.string.home_seismic,  R.string.home_seismic_sub,  listOf("ASCE", "SBC"),       Color(0xFFBF360C), Icons.Default.Warning),
    DesignHubItem(AppScreen.FrameAnalysis,  R.string.home_frame,    R.string.hub_design_frame_sub, listOf("ECP", "ACI", "AISC"), Color(0xFF1A237E), Icons.Default.AccountTree)
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
                title = { Text(stringResource(R.string.home_section_design_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                    stringResource(R.string.hub_design_choose),
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
    val isDark = MaterialTheme.colorScheme.surface.luminance < 0.5f

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
                        text = stringResource(item.titleRes),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        color = if (isDark) Color.White else Color(0xFF1A1A2E)
                    )
                    Text(
                        text = stringResource(item.subtitleRes),
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