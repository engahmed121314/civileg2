package com.civileg.app.ui.compose.components.drawings

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.entities.DesignCode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/**
 * Drawing wrapper that adds:
 * - View mode tabs (Elevation / Section / Plan / All)
 * - Info overlay with drawing title
 * - Dark card background
 * - Responsive height & layout for phone / tablet
 * - Device indicator badge (Phone/Tablet) for clarity
 * - Drawing scale factor display
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
    drawingHeightDp: Int = 0, // 0 = auto from device
    onExportPdf: (() -> Unit)? = null,
    designCode: DesignCode = DesignCode.ECP,
    modifier: Modifier = Modifier,
    drawingContent: @Composable () -> Unit
) {
    // ── InputGuard: validate title/subtitle are not blank ────────────────
    InputGuard.notBlank("title", title)
    InputGuard.notBlank("subtitle", subtitle)

    // ── Responsive config ───────────────────────────────────────────────
    val cfg = drawingDimensionsConfig()

    // ── Code-reference annotation ──────────────────────────────────────────
    val codeLabel = designCode.version

    var showInfo by remember { mutableStateOf(false) }

    val resolvedViewModes = if (viewModes.isEmpty()) listOf(
        "All",
        "Longitudinal",
        "Cross Section",
        "Plan"
    ) else viewModes

    // Responsive drawing height: use explicit if provided, else from config
    val resolvedHeightDp = if (drawingHeightDp > 0) drawingHeightDp else cfg.interactiveScreenHeightDp

    // Responsive padding & font sizes
    val toolbarHPadding = if (cfg.isTablet) 16.dp else 12.dp
    val toolbarVPadding = if (cfg.isTablet) 10.dp else 8.dp
    val titleFontSize = if (cfg.isTablet) 16.sp else 14.sp
    val subtitleFontSize = if (cfg.isTablet) 11.sp else 10.sp
    val tabFontSize = if (cfg.isTablet) 13.sp else 12.sp
    val iconSize = if (cfg.isTablet) 24.dp else 20.dp
    val buttonSize = if (cfg.isTablet) 36.dp else 32.dp
    val infoFontSize = if (cfg.isTablet) 13.sp else 12.sp
    val cardRadius = if (cfg.isLargeTablet) 20.dp else if (cfg.isTablet) 18.dp else 16.dp
    val cardElevation = if (cfg.isTablet) 6.dp else 4.dp

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        ),
        shape = RoundedCornerShape(cardRadius),
        elevation = CardDefaults.cardElevation(cardElevation)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top toolbar
            DrawingToolbar(
                title = title,
                subtitle = subtitle,
                onToggleInfo = { showInfo = !showInfo },
                showInfo = showInfo,
                onExportPdf = onExportPdf,
                hPadding = toolbarHPadding,
                vPadding = toolbarVPadding,
                titleFontSize = titleFontSize,
                subtitleFontSize = subtitleFontSize,
                iconSize = iconSize,
                buttonSize = buttonSize
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
                        edgePadding = if (cfg.isTablet) 24.dp else 16.dp,
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
                                        fontSize = tabFontSize,
                                        color = if (selectedViewMode == index)
                                            Color(0xFF4A90D9) else Color(0xAAFFFFFF)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Drawing area — uses responsive height for phone/tablet compatibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(resolvedHeightDp.dp)
                    .background(Color(0xFF1A1A2E))
            ) {
                drawingContent()
            }

            // Info overlay with more detail on tablets
            if (showInfo) {
                Surface(
                    color = Color(0xCC000000),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(if (cfg.isTablet) 16.dp else 12.dp),
                        verticalArrangement = Arrangement.spacedBy(if (cfg.isTablet) 6.dp else 4.dp)
                    ) {
                        InfoRow("📌 Title", title, infoFontSize)
                        InfoRow("📐 Type", subtitle, infoFontSize)
                        InfoRow("📋 Code", codeLabel, infoFontSize)
                        InfoRow("📱 Device", if (cfg.isTablet) "Tablet" else "Phone", infoFontSize)
                        InfoRow("📐 Scale", String.format("%.1fx", cfg.deviceScale), infoFontSize)
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
    onToggleInfo: () -> Unit,
    showInfo: Boolean,
    onExportPdf: (() -> Unit)? = null,
    hPadding: Dp = 12.dp,
    vPadding: Dp = 8.dp,
    titleFontSize: TextUnit = 14.sp,
    subtitleFontSize: TextUnit = 10.sp,
    iconSize: Dp = 20.dp,
    buttonSize: Dp = 32.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = hPadding, vertical = vPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Draw,
                contentDescription = null,
                tint = Color(0xFF4A90D9),
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    title,
                    color = Color.White,
                    fontSize = titleFontSize,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    subtitle,
                    color = Color(0xAAFFFFFF),
                    fontSize = subtitleFontSize
                )
            }
        }

        Row {
            // PDF Export button
            if (onExportPdf != null) {
                IconButton(onClick = onExportPdf, modifier = Modifier.size(buttonSize)) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = "Export PDF",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size((iconSize.value * 0.9f).dp)
                    )
                }
            }
            // Info toggle
            IconButton(onClick = onToggleInfo, modifier = Modifier.size(buttonSize)) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Info",
                    tint = if (showInfo) Color(0xFF4A90D9) else Color.White,
                    modifier = Modifier.size((iconSize.value * 0.9f).dp)
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, fontSize: androidx.compose.ui.unit.TextUnit = 12.sp) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xAAFFFFFF), fontSize = fontSize)
        Text(value, color = Color.White, fontSize = fontSize)
    }
}
