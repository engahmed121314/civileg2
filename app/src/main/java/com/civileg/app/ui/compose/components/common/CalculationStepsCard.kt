package com.civileg.app.ui.compose.components.common

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextBlock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.civileg.app.R
import com.civileg.app.domain.entities.CalculationStep
import com.civileg.app.domain.entities.StepStatus

/**
 * Shared, element-agnostic calculation trace. Each row shows the step formula with its
 * substituted values, the computed result and the code clause, so any element's numbers can be
 * audited without leaving the screen.
 */
@Composable
fun CalculationStepsCard(
    steps: List<CalculationStep>,
    modifier: Modifier = Modifier,
    title: String = "Calculation Steps"
) {
    if (steps.isEmpty()) return
    val sorted = steps.sortedBy { it.stepNumber }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Functions,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                AppText(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            sorted.forEachIndexed { index, step ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                }
                StepRow(step)
            }
        }
    }
}


/** Display-only Arabic rendering of engine step text; engine strings stay English keys. */
@Composable
private fun rememberStepLocalizer(): (String) -> String {
    if (!isArabicUi()) return { it }
    val pairs = listOf(
        "Base punching shear (surrogate)" to stringResource(R.string.stp_punching),
        "Tank Geometry" to stringResource(R.string.stp_geometry),
        "Water Pressure" to stringResource(R.string.stp_pressure),
        "Wall Thickness" to stringResource(R.string.stp_wall_t),
        "Wall Reinforcement" to stringResource(R.string.stp_wall_r),
        "Base Reinforcement" to stringResource(R.string.stp_base_r),
        "Safety Checks" to stringResource(R.string.stp_safety),
        "Flexure · shear · crack width" to stringResource(R.string.stp_flex_shear_crack),
        "Applicability, load model and critical perimeter unverified; no verdict" to stringResource(R.string.stp_punch_note),
        "Not assessed" to stringResource(R.string.stp_not_assessed_lc),
        "NOT ASSESSED" to stringResource(R.string.stp_not_assessed),
        "t ≥ tmin (code crack/serviceability)" to stringResource(R.string.stp_t_min),
        "at water depth" to stringResource(R.string.stp_at_depth),
        "checks passed" to stringResource(R.string.stp_checks_passed),
        "mm²/m; provided" to stringResource(R.string.stp_provided),
        "mm²/m at " to stringResource(R.string.stp_mm2_at),
        "mm spacing" to stringResource(R.string.stp_spacing),
        "(edition not verified)" to stringResource(R.string.stp_edition)
    )
    val unsafe = stringResource(R.string.stp_unsafe)
    val safe = stringResource(R.string.stp_safe)
    return { text ->
        var out = text
        pairs.forEach { (en, ar) -> out = out.replace(en, ar) }
        out = Regex("\\bUNSAFE\\b").replace(out, unsafe)
        Regex("\\bSAFE\\b").replace(out, safe)
    }
}

@Composable
private fun StepRow(step: CalculationStep) {
    val loc = rememberStepLocalizer()
    val statusColor = when (step.status) {
        StepStatus.CHECK_PASS -> Color(0xFF2E7D32)
        StepStatus.CHECK_FAIL -> MaterialTheme.colorScheme.error
        StepStatus.WARNING -> Color(0xFFE67E22)
        else -> MaterialTheme.colorScheme.primary
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(statusColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AppText(
                text = step.stepNumber.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText(
                    text = loc(step.title),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                when (step.status) {
                    StepStatus.CHECK_PASS -> Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    StepStatus.CHECK_FAIL -> Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    else -> Unit
                }
            }
            if (step.formula.isNotBlank()) {
                AppTextBlock(
                    text = loc(step.formula),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (step.formulaWithValues.isNotBlank()) {
                AppTextBlock(
                    text = loc(step.formulaWithValues),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            AppTextBlock(
                text = buildString {
                    append(loc(step.result))
                    if (step.unit.isNotBlank()) append("  [${step.unit}]")
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = statusColor
            )
            if (step.codeReference.isNotBlank()) {
                AppTextBlock(
                    text = loc(step.codeReference),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
