package com.civileg.app.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.civileg.app.R
import kotlin.math.min

// ============================================================================
// DESIGN SYSTEM - Colors & Shapes
// ============================================================================

object PremiumDesignSystem {
    // Gradient colors
    val PrimaryGradient = Brush.horizontalGradient(
        listOf(Color(0xFF1565C0), Color(0xFF1E88E5), Color(0xFF42A5F5))
    )
    val SuccessGradient = Brush.horizontalGradient(
        listOf(Color(0xFF2E7D32), Color(0xFF43A047))
    )
    val DangerGradient = Brush.horizontalGradient(
        listOf(Color(0xFFC62828), Color(0xFFE53935))
    )
    val WarningGradient = Brush.horizontalGradient(
        listOf(Color(0xFFE65100), Color(0xFFF57C00))
    )
    val InfoGradient = Brush.horizontalGradient(
        listOf(Color(0xFF0277BD), Color(0xFF039BE5))
    )
    val DarkCardGradient = Brush.verticalGradient(
        listOf(Color(0xFF1E1E3F), Color(0xFF1A1A2E))
    )
    val GlassEffect = Color(0x1AFFFFFF)

    // Shapes
    val CardShape = RoundedCornerShape(20.dp)
    val InputShape = RoundedCornerShape(14.dp)
    val ButtonShape = RoundedCornerShape(14.dp)
    val ChipShape = RoundedCornerShape(10.dp)
    val SmallCardShape = RoundedCornerShape(12.dp)

    // Status colors
    @Composable
    fun statusColor(ratio: Float): Color = animateColorAsState(
        targetValue = when {
            ratio > 1.0f -> Color(0xFFE53935)  // Red - Unsafe
            ratio > 0.9f -> Color(0xFFFF9800)   // Orange - Warning
            ratio > 0.4f -> Color(0xFF4CAF50)   // Green - Good
            else -> Color(0xFF2196F3)            // Blue - Under-utilized
        },
        animationSpec = tween(800), label = "statusColor"
    ).value

    @Composable
    fun statusText(ratio: Float): String = when {
        ratio > 1.0f -> stringResource(R.string.status_section_unsafe)
        ratio > 0.9f -> stringResource(R.string.status_high_load_warning)
        ratio > 0.4f -> stringResource(R.string.status_ideal_economic)
        else -> stringResource(R.string.status_oversized)
    }

    @Composable
    fun statusIcon(ratio: Float): ImageVector = when {
        ratio > 1.0f -> Icons.Default.Dangerous
        ratio > 0.9f -> Icons.Default.Warning
        ratio > 0.4f -> Icons.Default.Verified
        else -> Icons.Default.Info
    }

    @Composable
    fun statusEmoji(ratio: Float): String = when {
        ratio > 1.0f -> "❌"
        ratio > 0.9f -> "⚠️"
        ratio > 0.4f -> "✅"
        else -> "🔵"
    }
}

// ============================================================================
// PREMIUM SECTION HEADER
// ============================================================================

@Composable
fun PremiumSectionHeader(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Decorative line
        Box(
            modifier = Modifier
                .weight(0.3f)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), Color.Transparent)
                    ),
                    RoundedCornerShape(1.dp)
                )
        )
    }
}

// ============================================================================
// PREMIUM INPUT FIELD
// ============================================================================

@Composable
fun PremiumInputField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    suffix: String? = null,
    leadingIcon: ImageVector? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
        enabled = enabled,
        shape = PremiumDesignSystem.InputShape,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.SemiBold
        ),
        leadingIcon = leadingIcon?.let { icon ->
            {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.primary else Color.Gray)
            }
        },
        suffix = suffix?.let { s ->
            {
                Text(s, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

// ============================================================================
// SAFETY STATUS CARD WITH ANIMATED RING
// ============================================================================

@Composable
fun SafetyStatusCard(
    utilizationRatio: Double,
    isSafe: Boolean,
    title: String = "",
    modifier: Modifier = Modifier
) {
    val resolvedTitle = if (title.isEmpty()) stringResource(R.string.design_evaluation) else title
    val ratio = utilizationRatio.toFloat()
    val statusColor = PremiumDesignSystem.statusColor(ratio)
    val animatedRatio by animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1.5f),
        animationSpec = tween(1200), label = "ratio"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ),
        shape = PremiumDesignSystem.CardShape,
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated circular progress
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                // Background ring
                Canvas(modifier = Modifier.size(80.dp)) {
                    drawArc(
                        color = Color(0x1AFFFFFF),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx())
                    )
                    // Progress arc
                    drawArc(
                        color = statusColor,
                        startAngle = -90f,
                        sweepAngle = animatedRatio * 360f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx())
                    )
                }
                // Percentage text
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(utilizationRatio * 100).toInt()}%",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = statusColor
                    )
                    Text(
                        stringResource(R.string.utilization_ratio),
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Status info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        PremiumDesignSystem.statusIcon(ratio),
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "${PremiumDesignSystem.statusEmoji(ratio)} ${PremiumDesignSystem.statusText(ratio)}",
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        fontSize = 15.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    resolvedTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Mini progress bar
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0x1AFFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedRatio.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(3.dp))
                            .background(statusColor)
                    )
                }
            }
        }
    }
}

// ============================================================================
// RESULT DATA CARD
// ============================================================================

@Composable
fun ResultDataCard(
    title: String,
    results: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Assessment,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
        ),
        shape = PremiumDesignSystem.CardShape,
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.15f)
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp).padding(4.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = accentColor)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )

            // Result rows
            results.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentColor.copy(alpha = 0.08f)
                    ) {
                        Text(
                            value,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// SAFETY CHECK LIST
// ============================================================================

@Composable
fun SafetyCheckList(
    checks: List<Triple<String, String, Boolean>>, // (name, detail, isSafe)
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
        ),
        shape = PremiumDesignSystem.CardShape
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.code_checks),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            checks.forEach { (name, detail, isSafe) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(
                            width = 1.dp,
                            color = if (isSafe) Color(0xFF2E7D32).copy(alpha = 0.3f) else Color(0xFFE53935).copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isSafe) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (isSafe) Color(0xFF2E7D32) else Color(0xFFE53935),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            name,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Text(
                            detail,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        if (isSafe) "PASS ✓" else "FAIL ✗",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isSafe) Color(0xFF2E7D32) else Color(0xFFE53935)
                    )
                }
            }
        }
    }
}

// ============================================================================
// PREMIUM ACTION BUTTONS ROW
// ============================================================================

@Composable
fun PremiumActionButtons(
    onExportPdf: () -> Unit,
    onSave: () -> Unit,
    isExporting: Boolean = false,
    modifier: Modifier = Modifier,
    extraActions: @Composable (RowScope.() -> Unit) = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Export PDF button
        Button(
            onClick = onExportPdf,
            modifier = Modifier.weight(1f),
            shape = PremiumDesignSystem.ButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            enabled = !isExporting
        ) {
            if (isExporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.pdf_report), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // Save button
        OutlinedButton(
            onClick = onSave,
            modifier = Modifier.weight(1f),
            shape = PremiumDesignSystem.ButtonShape,
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp, MaterialTheme.colorScheme.secondary
            )
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.save), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary)
        }

        extraActions()
    }
}

// ============================================================================
// FORMULA CARD
// ============================================================================

@Composable
fun FormulaCard(
    formulas: List<String>,
    title: String = "",
    modifier: Modifier = Modifier
) {
    val resolvedTitle = if (title.isEmpty()) stringResource(R.string.engineering_equations) else title
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(),
        modifier = modifier
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ),
            shape = PremiumDesignSystem.CardShape
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Functions, contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(resolvedTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                formulas.forEach { formula ->
                    Text(
                        text = formula,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(vertical = 1.5.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

// ============================================================================
// CODE SELECTOR CHIPS
// ============================================================================

@Composable
fun CodeSelectorChips(
    selectedCode: String,
    codes: List<Pair<String, String>>, // (code, displayName)
    onCodeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        codes.forEach { (code, name) ->
            val isSelected = code == selectedCode
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clickable { onCodeSelected(code) },
                shape = PremiumDesignSystem.ChipShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shadowElevation = if (isSelected) 4.dp else 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = name,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
// LOADING OVERLAY
// ============================================================================

@Composable
fun LoadingOverlay(message: String = "") {
    val resolvedMessage = if (message.isEmpty()) stringResource(R.string.analyzing) else message
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.5f)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Card(
                shape = PremiumDesignSystem.CardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        resolvedMessage,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}