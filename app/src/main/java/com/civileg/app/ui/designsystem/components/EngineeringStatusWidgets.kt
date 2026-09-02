package com.civileg.app.ui.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.R
import com.civileg.app.ui.designsystem.EngineeringStatus
import com.civileg.app.ui.designsystem.engineeringColors
import com.civileg.app.ui.designsystem.engineeringType

@Composable
fun EngineeringStatusBadge(
    status: EngineeringStatus,
    modifier: Modifier = Modifier,
    labelOverride: String? = null,
    compact: Boolean = false
) {
    val color = status.color()
    val container = status.containerColor()
    Row(
        modifier = modifier
            .background(container, RoundedCornerShape(4.dp))
            .padding(horizontal = if (compact) 6.dp else 10.dp, vertical = if (compact) 2.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = status.icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(if (compact) 12.dp else 16.dp)
        )
        Text(
            text = labelOverride ?: stringResource(status.labelRes),
            style = engineeringType().statusLabel.copy(fontSize = if (compact) 11.sp else 13.sp),
            color = color
        )
    }
}

@Composable
fun UtilizationBar(
    utilization: Double,
    modifier: Modifier = Modifier,
    showMarkerLabel: Boolean = true,
    status: EngineeringStatus? = null
) {
    val colors = engineeringColors()
    val clamped = utilization.coerceIn(0.0, 1.25).toFloat()
    val barColor = status?.color()
        ?: if (utilization > 1.0) colors.fail else if (utilization >= 0.9) colors.warning else colors.safe

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            val trackY = size.height / 2f
            drawLine(
                color = colors.gridLine,
                start = Offset(0f, trackY),
                end = Offset(size.width, trackY),
                strokeWidth = size.height * 0.45f,
                cap = StrokeCap.Round
            )
            val filledEnd = size.width * (clamped / 1.25f)
            drawLine(
                color = barColor,
                start = Offset(0f, trackY),
                end = Offset(filledEnd, trackY),
                strokeWidth = size.height * 0.45f,
                cap = StrokeCap.Round
            )
            val limitX = size.width * (1.0f / 1.25f)
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(limitX - 2.dp.toPx(), 0f),
                end = Offset(limitX - 2.dp.toPx(), size.height),
                strokeWidth = 2.dp.toPx()
            )
            drawCircle(
                color = barColor,
                radius = size.height * 0.30f,
                center = Offset(filledEnd.coerceIn(0f, size.width), trackY)
            )
        }
        if (showMarkerLabel) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0%", style = engineeringType().unit, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${(utilization * 100).toInt()}%",
                    style = engineeringType().valueSmall,
                    color = barColor
                )
                Text("100%", style = engineeringType().unit, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AsComparisonBar(
    required: Double,
    provided: Double,
    modifier: Modifier = Modifier,
    unit: String = "mm²"
) {
    val colors = engineeringColors()
    val safe = provided >= required && required > 0
    val maxVal = maxOf(required, provided, 1e-6)
    Column(modifier = modifier.fillMaxWidth()) {
        BarRow(stringResource(R.string.eg_as_required), required, maxVal, colors.notChecked, unit)
        Spacer(Modifier.height(4.dp))
        BarRow(
            stringResource(R.string.eg_as_provided), provided, maxVal,
            if (safe) colors.safe else colors.fail, unit
        )
    }
}

@Composable
private fun BarRow(label: String, value: Double, max: Double, color: Color, unit: String) {
    val type = engineeringType()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = type.metricLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.28f)
        )
        Box(Modifier.weight(0.52f).height(8.dp)) {
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                drawRoundRect(
                    color = color,
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    size = Size(size.width * (value / max).toFloat(), size.height)
                )
            }
        }
        Text(
            text = "${formatEngineering(value)} $unit",
            style = type.valueSmall,
            modifier = Modifier.weight(0.20f),
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun formatEngineering(v: Double): String =
    if (v >= 1000) "%.0f".format(v) else "%.1f".format(v)

@Composable
fun EngineeringCheckRow(
    title: String,
    status: EngineeringStatus,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (detail != null) {
                Text(detail, style = engineeringType().unit, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        EngineeringStatusBadge(status = status, compact = true)
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
fun EngineeringEmptyState(
    title: String,
    actionLabel: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Architecture,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun EngineeringErrorState(
    reason: String,
    fix: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = engineeringColors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = colors.failContainer
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Error, null, tint = colors.fail, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.eg_design_not_completed),
                    style = engineeringType().statusLabel,
                    color = colors.fail
                )
            }
            Text(reason, style = MaterialTheme.typography.bodyMedium)
            if (fix != null) {
                Text(fix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun EngineeringWarningBanner(
    message: String,
    modifier: Modifier = Modifier,
    suggestedAction: String? = null,
    onViewCheck: (() -> Unit)? = null
) {
    val colors = engineeringColors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = colors.warningContainer
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.WarningAmber, null, tint = colors.warning, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.eg_review_required),
                    style = engineeringType().statusLabel,
                    color = colors.warning
                )
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (suggestedAction != null) {
                Text(suggestedAction, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onViewCheck != null) {
                TextButton(onClick = onViewCheck) {
                    Text(stringResource(R.string.eg_view_check))
                }
            }
        }
    }
}
