package com.civileg.app.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateFloatAsState
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
import com.civileg.app.ui.theme.PremiumTypography
import com.civileg.app.ui.theme.PremiumColorsLight
import com.civileg.app.ui.theme.PremiumColorsDark
import com.civileg.app.ui.theme.PremiumTypography.ArabicTypography
import com.civileg.app.ui.theme.PremiumTypography.EnglishTypography
import kotlin.math.min

// ============================================================================
// PREMIUM DESIGN SYSTEM - Colors & Shapes
// ============================================================================

object PremiumDesignSystem {
    // Gradient colors - Premium Geometric Gradients
    val PrimaryGradient = Brush.horizontalGradient(
        listOf(PremiumColorsLight.accentGradientStart, PremiumColorsLight.accentGradientEnd)
    )
    val SuccessGradient = Brush.horizontalGradient(
        listOf(PremiumColorsLight.successGradientStart, PremiumColorsLight.successGradientEnd)
    )
    val DangerGradient = Brush.horizontalGradient(
        listOf(PremiumColorsLight.dangerGradientStart, PremiumColorsLight.dangerGradientEnd)
    )
    val WarningGradient = Brush.horizontalGradient(
        listOf(PremiumColorsLight.warningBright, Color(0xFFFFF59D))
    )
    val InfoGradient = Brush.horizontalGradient(
        listOf(PremiumColorsLight.primary, PremiumColorsLight.primaryContainer)
    )
    val DarkCardGradient = Brush.verticalGradient(
        listOf(Color(0xFF0F172A), Color(0xFF1E293B))
    )
    val GlassEffect = Color(0x1AFFFFFF)

    // Premium Geometric Shapes
    val CardShape = RoundedCornerShape(20.dp)
    val InputShape = RoundedCornerShape(14.dp)
    val ButtonShape = RoundedCornerShape(14.dp)
    val ChipShape = RoundedCornerShape(10.dp)
    val SmallCardShape = RoundedCornerShape(12.dp)
    val PillShape = RoundedCornerShape(50.dp)
    val GhostShape = RoundedCornerShape(16.dp)

    // Status colors with animation
    @Composable
    fun statusColor(ratio: Float): Color = animateColorAsState(
        targetValue = when {
            ratio > 1.0f -> PremiumColorsLight.unsafeBright
            ratio > 0.9f -> PremiumColorsLight.warningBright
            ratio > 0.4f -> PremiumColorsLight.safeBright
            else -> PremiumColorsLight.primary
        },
        animationSpec = tween(800), label = "statusColor"
    ).value

    @Composable
    fun statusText(ratio: Float): String = when {
        ratio > 1.0f -> "Unsafe Section"
        ratio > 0.9f -> "High Load Warning"
        ratio > 0.4f -> "Ideal Economic"
        else -> "Oversized"
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

    // Premium Geometric Gradient definitions
    val gradientNavyToBlue = Brush.horizontalGradient(
        listOf(PremiumColorsLight.navyDeep, PremiumColorsLight.navyMedium)
    )
    val gradientBlueToPurple = Brush.horizontalGradient(
        listOf(PremiumColorsLight.navyMedium, PremiumColorsLight.outlineVariant)
    )
    val gradientPurpleToAmber = Brush.horizontalGradient(
        listOf(PremiumColorsLight.outlineVariant, PremiumColorsLight.tertiary)
    )
    val gradientAmberToOrange = Brush.horizontalGradient(
        listOf(PremiumColorsLight.tertiary, PremiumColorsLight.accent)
    )
}

// ============================================================================
// PREMIUM SECTION HEADER WITH GEOMETRIC GRADIENT
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
                shape = PremiumDesignSystem.InputShape,
                color = PremiumColorsLight.primaryContainer.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = PremiumColorsLight.primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = PremiumColorsLight.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = PremiumColorsLight.onSurfaceVariant
                )
            }
        }
        // Decorative geometric line
        Box(
            modifier = Modifier
                .weight(0.3f)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(PremiumColorsLight.primary.copy(alpha = 0.6f), Color.Transparent)
                    ),
                    RoundedCornerShape(1.dp)
                )
        )
    }
}

// ============================================================================
// PREMIUM INPUT FIELD WITH GEOMETRIC SHAPES
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
        leadingIcon = leadingIcon?.let { icon -> {
            {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp),
                    tint = if (enabled) PremiumColorsLight.primary else Color.Gray)
            }
        }
        },
        suffix = suffix?.let { s -> {
            {
                Text(s, fontSize = 11.sp, color = PremiumColorsLight.onSurfaceVariant)
            }
        }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PremiumColorsLight.primary,
            unfocusedBorderColor = PremiumColorsLight.outlineVariant.copy(alpha = 0.5f),
            focusedLabelColor = PremiumColorsLight.primary,
            cursorColor = PremiumColorsLight.primary
        )
    )
}

// ============================================================================
// SAFETY STATUS CARD WITH ANIMATED GEOMETRIC RING
// ============================================================================

@Composable
fun SafetyStatusCard(
    utilizationRatio: Double,
    isSafe: Boolean,
    title: String = "",
    modifier: Modifier = Modifier
) {
    val resolvedTitle = if (title.isEmpty()) "Design Evaluation" else title
    val ratio = utilizationRatio.toFloat()
    val statusColor = PremiumDesignSystem.statusColor(ratio)
    val animatedRatio by animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1.5f),
        animationSpec = tween(1200), label = "ratio"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = PremiumColorsLight.surface.copy(alpha = 0.5f)
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
            // Animated circular progress with geometric styling
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                // Background geometric ring
                Canvas(modifier = Modifier.size(80.dp)) {
                    // Draw geometric background arc
                    drawArc(
                        color = Color(0x1AFFFFFF),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx())
                    )
                    // Progress arc with geometric color
                    drawArc(
                        color = statusColor,
                        startAngle = -90f,
                        sweepAngle = animatedRatio * 360f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx())
                    )
                }
                // Percentage text in geometric circle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(utilizationRatio * 100).toInt()}%",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = statusColor
                    )
                    Text(
                        "Utilization Ratio",
                        fontSize = 8.sp,
                        color = PremiumColorsLight.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Status info column
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
                    color = PremiumColorsLight.onSurfaceVariant
                )

                // Mini premium progress bar
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
// RESULT DATA CARD WITH PREMIUM GEOMETRIC STYLING
// ============================================================================

@Composable
fun ResultDataCard(
    title: String,
    results: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Assessment,
    accentColor: Color = PremiumColorsLight.primary
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = PremiumColorsLight.surface.copy(alpha = 0.3f)
        ),
        shape = PremiumDesignSystem.CardShape,
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with geometric accent
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
                color = PremiumColorsLight.onSurface.copy(alpha = 0.1f)
            )

            // Result rows with geometric padding
            results.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, fontSize = 13.sp, color = PremiumColorsLight.onSurfaceVariant)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentColor.copy(alpha = 0.08f)
                    ) {
                        Text(
                            value,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = PremiumColorsLight.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// SAFETY CHECK LIST WITH PREMIUM GEOMETRIC BORDERS
// ============================================================================

@Composable
fun SafetyCheckList(
    checks: List<Triple<String, String, Boolean>>, // (name, detail, isSafe)
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = PremiumColorsLight.surface.copy(alpha = 0.3f)
        ),
        shape = PremiumDesignSystem.CardShape
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Code Checks",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = PremiumColorsLight.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            checks.forEach { (name, detail, isSafe) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(
                            width = 1.dp,
                            color = if (isSafe) PremiumColorsLight.safe else PremiumColorsLight.unsafe,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isSafe) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (isSafe) PremiumColorsLight.safe else PremiumColorsLight.unsafe,
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
                            color = PremiumColorsLight.onSurfaceVariant
                        )
                    }
                    Text(
                        if (isSafe) "PASS ✓" else "FAIL ✗",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isSafe) PremiumColorsLight.safe else PremiumColorsLight.unsafe
                    )
                }
            }
        }
    }
}

// ============================================================================
// PREMIUM ACTION BUTTONS ROW WITH GEOMETRIC STYLING
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
        // Export PDF button - Primary Geometric
        Button(
            onClick = onExportPdf,
            modifier = Modifier.weight(1f),
            shape = PremiumDesignSystem.ButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = PremiumColorsLight.primaryGradient
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
                Text("PDF Report", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // Save button - Secondary Outline Geometric
        OutlinedButton(
            onClick = onSave,
            modifier = Modifier.weight(1f),
            shape = PremiumDesignSystem.ButtonShape,
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp, PremiumColorsLight.secondary
            )
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp),
                tint = PremiumColorsLight.secondary)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                color = PremiumColorsLight.secondary)
        }

        extraActions()
    }
}

// ============================================================================
// FORMULA CARD WITH GEOMETRIC STYLING
// ============================================================================

@Composable
fun FormulaCard(
    formulas: List<String>,
    title: String = "",
    modifier: Modifier = Modifier
) {
    val resolvedTitle = if (title.isEmpty()) "Engineering Equations" else title
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(),
        modifier = modifier
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = PremiumColorsLight.surfaceVariant.copy(alpha = 0.2f)
            ),
            shape = PremiumDesignSystem.CardShape
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Functions, contentDescription = null,
                        tint = PremiumColorsLight.secondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(resolvedTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PremiumColorsLight.secondary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                formulas.forEach { formula ->
                    Text(
                        text = formula,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(vertical = 1.5.dp),
                        color = PremiumColorsLight.secondary.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

// ============================================================================
// CODE SELECTOR CHIPS WITH PREMIUM GEOMETRIC STYLING
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
                shape = if (isSelected) PremiumDesignSystem.PillShape else PremiumDesignSystem.ChipShape,
                color = if (isSelected) PremiumColorsLight.primary
                   else PremiumColorsLight.surfaceVariant.copy(alpha = 0.5f),
                shadowElevation = if (isSelected) 4.dp else 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = name,
                        color = if (isSelected) PremiumColorsLight.onPrimary
                           else PremiumColorsLight.onSurfaceVariant,
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
// LOADING OVERLAY WITH PREMIUM GEOMETRIC DESIGN
// ============================================================================

@Composable
fun LoadingOverlay(message: String = "") {
    val resolvedMessage = if (message.isEmpty()) "Analyzing..." else message
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PremiumColorsLight.surface.copy(alpha = 0.9f)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Card(
                shape = PremiumDesignSystem.CardShape,
                colors = CardDefaults.cardColors(
                    containerColor = PremiumColorsLight.surface
                ),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = PremiumColorsLight.primary,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        resolvedMessage,
                        fontWeight = FontWeight.Medium,
                        color = PremiumColorsLight.onSurface
                    )
                }
            }
        }
    }
}

// ============================================================================
// PREMIUM GEOMETRIC ICON BUTTON WITH RIPPLE EFFECT
// ============================================================================

@Composable
fun PremiumIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    backgroundColor: Color = PremiumColorsLight.surface,
    tintColor: Color = PremiumColorsLight.onSurface,
    rippleColor: Color = PremiumColorsLight.primaryContainer
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(size).clip(PremiumDesignSystem.PillShape),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            rippleColor = rippleColor
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tintColor,
            modifier = Modifier.size(size - 8.dp)
        )
    }
}

// ============================================================================
// PREMIUM PULSE ANIMATION
// ============================================================================

@Composable
fun PremiumPulse(
    progress: Float = 1f,
    color: Color = PremiumColorsLight.primary,
    modifier: Modifier = Modifier,
    speed: Long = 800L
) {
    val scale by animateFloatAsState(
        targetValue = 1f + (0.3f * sin((progress * 3.14159f / speed) * 2)),
        animationSpec = tween(speed)
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .color(color)
    )
}

// ============================================================================
// PREMIUM GEOMETRIC PROGRESS BAR WITH SEGMENTS
// ============================================================================

@Composable
fun PremiumProgressBar(
    value: Float,
    target: Float = 1f,
    modifier: Modifier = Modifier,
    segmentCount: Int = 8
) {
    val progress = value.coerceIn(0f, target)
    val segmentProgress = progress / segmentCount
    
    Row(
        modifier = modifier
            .height(8.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(segmentCount) { index ->
            val isActive = index < progress.roundToInt()
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isActive) PremiumColorsLight.primary
                        else PremiumColorsLight.outlineVariant.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

// ============================================================================
// PREMIUM ANIMATED AVATAR WITH GEOMETRIC BOORDER
// ============================================================================

@Composable
fun PremiumAvatar(
    imageVector: ImageVector?,
    name: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = imageVector?.let { painterResource(it) } ?: rememberImagePainter(null),
        contentDescription = name,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(
                width = 2.dp,
                color = PremiumColorsLight.primary,
                shape = CircleShape
            )
    )
}

// ============================================================================
// PREMIUM TOAST/SNACKBAR WITH GEOMETRIC STYLING
// ============================================================================

@Composable
fun PremiumSnackBar(
    message: String,
    action: (() -> Unit)? = null,
    actionLabel: String? = null
) {
    SnackbarHostState().showSnackBar(
        Snackbar(
            modifier = Modifier.fillMaxWidth(),
            text = message,
            actionLabel = actionLabel,
            action = {
                if (action != null) {
                    Button(
                        onClick = action,
                        style = ButtonStyle(
                            shape = RoundedCornerShape(8.dp),
                            backgroundColor = Brush.color(PremiumColorsLight.primary)
                        )
                    )
                }
            },
            snackBarColor = PremiumColorsLight.primaryContainer,
            duration = SnackbarDuration.LengthLong,
        )
    )
}