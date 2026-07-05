package com.civileg.app.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.utils.CalculatorEngine

// ============================================================================
// UNIFIED DESIGN CODE SELECTOR
// ============================================================================

/**
 * Professional design code selector chip group.
 * Shows ECP / ACI / SBC as selectable chips.
 */
@Composable
fun DesignCodeSelectorRow(
    selectedCode: CalculatorEngine.DesignCode,
    onCodeSelected: (CalculatorEngine.DesignCode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            "الكود التصميمي",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            CalculatorEngine.DesignCode.entries.forEach { code ->
                FilterChip(
                    selected = code == selectedCode,
                    onClick = { onCodeSelected(code) },
                    label = {
                        Text(
                            code.displayName,
                            fontSize = 13.sp,
                            fontWeight = if (code == selectedCode) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ============================================================================
// ANIMATED UTILIZATION GAUGE
// ============================================================================

/**
 * Professional circular progress indicator with utilization percentage
 * and color-coded status text.
 */
@Composable
fun UtilizationGauge(
    ratio: Float,
    modifier: Modifier = Modifier,
    size: Int = 64,
    strokeWidth: Int = 6
) {
    val color = DesignSystem.statusColor(ratio)
    val animatedRatio by animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1.2f),
        animationSpec = tween(1000),
        label = "util_ratio"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        CircularProgressIndicator(
            progress = { animatedRatio },
            modifier = Modifier.size(size.dp),
            strokeWidth = strokeWidth.dp,
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            "${(ratio * 100).toInt()}%",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = color
        )
    }
}

// ============================================================================
// PROFESSIONAL SECTION HEADER
// ============================================================================

/**
 * Consistent section header with icon used across all design screens.
 */
@Composable
fun DesignSectionHeader(
    title: String,
    icon: ImageVector = Icons.Default.Tune,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

// ============================================================================
// PROFESSIONAL RESULT ROW
// ============================================================================

/**
 * A labeled result row showing key-value pair.
 */
@Composable
fun DesignResultRow(
    label: String,
    value: String,
    labelColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = labelColor)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = valueColor)
    }
}

// ============================================================================
// STATUS BANNER CARD
// ============================================================================

/**
 * Shows design status with icon, text, and utilization gauge.
 * Green = safe/ideal, Orange = high load, Red = unsafe, Blue = uneconomical.
 */
@Composable
fun DesignStatusBanner(
    ratio: Float,
    isSafe: Boolean,
    modifier: Modifier = Modifier
) {
    val color = DesignSystem.statusColor(ratio)
    val statusText = DesignSystem.statusText(ratio)
    val statusIcon = when {
        ratio > 1.0f -> Icons.Default.Dangerous
        ratio > 0.9f -> Icons.Default.Warning
        ratio > 0.4f -> Icons.Default.Verified
        else -> Icons.Default.Info
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(statusIcon, contentDescription = null, tint = color)
                    Spacer(Modifier.width(8.dp))
                    Text(statusText, fontWeight = FontWeight.Bold, color = color)
                }
                Text(
                    "نسبة الاستخدام",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            UtilizationGauge(ratio = ratio)
        }
    }
}

// ============================================================================
// PROFESSIONAL INPUT FIELD
// ============================================================================

/**
 * Consistent styled input field for all design screens.
 */
@Composable
fun DesignInputField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

// ============================================================================
// DETAILED RESULTS EXPANDABLE CARD
// ============================================================================

/**
 * Expandable card showing detailed calculation results with sections.
 */
@Composable
fun DetailedResultsCard(
    results: Map<String, String>,
    title: String = "📊 نتائج التصميم التفصيلية",
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    results.entries.forEachIndexed { index, (key, value) ->
                        DesignResultRow(key, value)
                        if (index < results.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// BEAM ANALYSIS RESULTS CARD
// ============================================================================

/**
 * Professional card showing beam moment/shear results with visual indicators.
 */
@Composable
fun BeamAnalysisResultsCard(
    appliedMoment: Double,
    appliedShear: Double,
    mu: Double,
    vu: Double,
    isFlexureSafe: Boolean,
    isShearSafe: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "📊 التحليل الإنشائي",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Moment section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (isFlexureSafe) Icons.Default.Verified else Icons.Default.Dangerous,
                    contentDescription = null,
                    tint = if (isFlexureSafe) Color(0xFF2E7D32) else Color.Red,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("الانحناء", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    "Mu = ${"%.1f".format(appliedMoment)} kN.m",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Shear section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (isShearSafe) Icons.Default.Verified else Icons.Default.Dangerous,
                    contentDescription = null,
                    tint = if (isShearSafe) Color(0xFF2E7D32) else Color.Red,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("القص", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    "Vu = ${"%.1f".format(appliedShear)} kN",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ============================================================================
// DESIGN SYSTEM COLORS & HELPERS
// ============================================================================

object DesignSystem {
    fun statusColor(ratio: Float): Color = when {
        ratio > 1.0f -> Color(0xFFE53935)  // Red - unsafe
        ratio > 0.9f -> Color(0xFFFF9800)  // Orange - high
        ratio > 0.4f -> Color(0xFF2E7D32)  // Green - safe
        else -> Color(0xFF1976D2)           // Blue - uneconomical
    }

    fun statusText(ratio: Float): String = when {
        ratio > 1.0f -> "غير آمن"
        ratio > 0.9f -> "تحميل عالي"
        ratio > 0.4f -> "آمن ومقبول"
        else -> "غير اقتصادي"
    }
}

// ============================================================================
// SAFETY CHECK ROW
// ============================================================================

/**
 * Professional check row showing safety verification with icon.
 */
@Composable
fun SafetyCheckRow(
    label: String,
    isSafe: Boolean,
    detail: String = "",
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Icon(
            if (isSafe) Icons.Default.Verified else Icons.Default.Dangerous,
            contentDescription = null,
            tint = if (isSafe) Color(0xFF2E7D32) else Color(0xFFE53935),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (detail.isNotEmpty()) {
            Text(detail, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (isSafe) Color(0xFF2E7D32) else Color(0xFFE53935))
        }
    }
}

// ============================================================================
// PROFESSIONAL ACTION BUTTON
// ============================================================================

/**
 * Consistent styled action button for calculate/export actions.
 */
@Composable
fun DesignActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Card(
        onClick = { if (!isLoading && enabled) onClick() },
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) containerColor else containerColor.copy(alpha = 0.5f),
            disabledContainerColor = containerColor.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = contentColor,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (enabled) contentColor else contentColor.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// ============================================================================
// FORMULA DISPLAY CARD
// ============================================================================

/**
 * Card showing engineering formulas with monospace font.
 */
@Composable
fun FormulaCard(
    title: String = "المعادلات التصميمية",
    formulas: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null, modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    formulas.forEach { (name, formula) ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(name, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(formula, fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// QUICK ACTIONS ROW
// ============================================================================

/**
 * Horizontal row of quick action chips/buttons.
 */
@Composable
fun QuickActionsRow(
    actions: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        actions.forEach { (text, onClick) ->
            Card(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(vertical = 10.dp)
                ) {
                    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}