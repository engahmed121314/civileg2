package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Drawing wrapper that provides:
 * - Dark card background
 * - Optional scrollable view mode tabs (only shown when viewModes is non-empty)
 *
 * Used to wrap ProfessionalBeamDrawing, ProfessionalColumnDrawing, etc.
 * Pass viewModes = emptyList() to hide tabs when the drawing doesn't support multiple views.
 */
@Composable
fun InteractiveDrawingScreen(
    title: String = "Engineering Drawing",
    subtitle: String = "Structural Detail",
    viewModes: List<String> = emptyList(),
    selectedViewMode: Int = 0,
    onViewModeChanged: (Int) -> Unit = {},
    drawingHeightDp: Int = 950,
    modifier: Modifier = Modifier,
    drawingContent: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Compact title bar (no duplicate buttons)
            Surface(
                color = Color(0x15FFFFFF),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        "📐 ",
                        color = Color(0xFF4A90D9),
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 13.sp,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        subtitle,
                        color = Color(0xAAFFFFFF),
                        fontSize = 10.sp
                    )
                }
            }

            // View mode tabs — only shown when explicitly provided and non-empty
            if (viewModes.isNotEmpty()) {
                Surface(
                    color = Color(0x22FFFFFF),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedViewMode,
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        edgePadding = 16.dp,
                        divider = {},
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        viewModes.forEachIndexed { index, mode ->
                            Tab(
                                selected = selectedViewMode == index,
                                onClick = { onViewModeChanged(index) },
                                text = {
                                    Text(
                                        mode,
                                        fontSize = 12.sp,
                                        color = if (selectedViewMode == index)
                                            Color(0xFF4A90D9) else Color(0xAAFFFFFF)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Drawing area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(drawingHeightDp.dp)
                    .background(Color(0xFF1A1A2E))
            ) {
                drawingContent()
            }
        }
    }
}
