package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.R

private data class MoreItem(
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
    val accentColor: Color,
    val route: String
)

private val moreItems = listOf(
    MoreItem(R.string.nav_projects,    R.string.more_projects_sub,    Icons.Default.Folder,     Color(0xFF1565C0), AppScreen.Projects.route),
    MoreItem(R.string.nav_settings,    R.string.more_settings_sub,    Icons.Default.Settings,   Color(0xFF37474F), AppScreen.Settings.route),
    MoreItem(R.string.nav_inventory,   R.string.more_inventory_sub,   Icons.Default.Inventory2, Color(0xFF2E7D32), AppScreen.Inventory.route),
    MoreItem(R.string.home_frame,      R.string.more_frame_sub,       Icons.Default.AccountTree,Color(0xFF1A237E), AppScreen.FrameAnalysis.route),
    MoreItem(R.string.home_seismic,    R.string.more_seismic_sub,     Icons.Default.Warning,    Color(0xFFBF360C), AppScreen.SeismicAnalysis.route)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreHubScreen(
    onNavigateTo: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hub_more_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.hub_more_management),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            items(moreItems.size) { index ->
                val item = moreItems[index]
                val isDark = MaterialTheme.colorScheme.surface.luminance < 0.5f
                val cardBg = if (isDark) Color(0xFF1E1E30) else Color.White

                Card(
                    onClick = { onNavigateTo(item.route) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(item.accentColor.copy(alpha = 0.04f))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(item.accentColor.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(item.icon, null, tint = item.accentColor, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(item.titleRes),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isDark) Color.White else Color(0xFF1A1A2E)
                            )
                            Text(
                                text = stringResource(item.subtitleRes),
                                fontSize = 11.sp,
                                color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            null,
                            tint = if (isDark) Color(0xFF4A4A6A) else Color(0xFFBDBDBD),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

private val Color.luminance: Float
    get() = (red * 299 + green * 587 + blue * 114) / 1000f