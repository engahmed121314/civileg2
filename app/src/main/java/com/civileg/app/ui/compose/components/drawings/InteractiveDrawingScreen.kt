package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.R

/**
 * Interactive drawing wrapper that adds:
 * - Pinch-to-zoom (0.5x to 5x)
 * - Pan/drag
 * - Double-tap to reset
 * - View mode tabs (Elevation / Section / Plan / All)
 * - Info overlay with drawing title
 * - Dark card background
 *
 * Used to wrap ProfessionalBeamDrawing, ProfessionalColumnDrawing, etc.
 */
@Composable
fun InteractiveDrawingScreen(
    title: String = "Engineering Drawing",
    subtitle: String = "Structural Detail",
    viewModes: List<String> = emptyList(),
    selectedViewMode: Int = 0,
    onViewModeChanged: (Int) -> Unit = {},
    drawingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showInfo by remember { mutableStateOf(false) }

    val resolvedViewModes = if (viewModes.isEmpty()) listOf(
        stringResource(R.string.view_modes_all),
        stringResource(R.string.view_modes_longitudinal),
        stringResource(R.string.view_modes_cross),
        stringResource(R.string.view_modes_plan)
    ) else viewModes

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top toolbar
            DrawingToolbar(
                title = title,
                subtitle = subtitle,
                scale = scale,
                onResetZoom = {
                    scale = 1f
                    offset = Offset.Zero
                },
                onToggleInfo = { showInfo = !showInfo },
                showInfo = showInfo
            )

            // View mode tabs
            if (resolvedViewModes.size > 1) {
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
                        resolvedViewModes.forEachIndexed { index, mode ->
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

            // Drawing area with zoom/pan
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .background(Color(0xFF1A1A2E))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            offset = Offset(
                                x = (offset.x + pan.x * scale).coerceIn(-500f, 500f),
                                y = (offset.y + pan.y * scale).coerceIn(-500f, 500f)
                            )
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                scale = 1f
                                offset = Offset.Zero
                            }
                        )
                    }
            ) {
                // Apply transform to drawing content
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Empty canvas - actual drawing is below
                }

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                ) {
                    drawingContent()
                }
            }

            // Info overlay
            if (showInfo) {
                Surface(
                    color = Color(0xCC000000),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        InfoRow("📌 ${stringResource(R.string.info_label_title)}", title)
                        InfoRow("📐 ${stringResource(R.string.info_label_type)}", subtitle)
                        InfoRow("🔍 ${stringResource(R.string.info_label_zoom)}", "${"%.0f".format(scale * 100)}%")
                        InfoRow("👆 ${stringResource(R.string.info_label_gesture)}", stringResource(R.string.info_gesture_hint))
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawingToolbar(
    title: String,
    subtitle: String,
    scale: Float,
    onResetZoom: () -> Unit,
    onToggleInfo: () -> Unit,
    showInfo: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Draw,
                contentDescription = null,
                tint = Color(0xFF4A90D9),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 14.sp,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    subtitle,
                    color = Color(0xAAFFFFFF),
                    fontSize = 10.sp
                )
            }
        }

        Row {
            // Zoom indicator
            Surface(
                color = Color(0x33FFFFFF),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    "${"%.0f".format(scale * 100)}%",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))

            // Reset zoom
            IconButton(onClick = onResetZoom, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.ZoomOutMap,
                    contentDescription = "Reset",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Info toggle
            IconButton(onClick = onToggleInfo, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Info",
                    tint = if (showInfo) Color(0xFF4A90D9) else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xAAFFFFFF), fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 12.sp)
    }
}