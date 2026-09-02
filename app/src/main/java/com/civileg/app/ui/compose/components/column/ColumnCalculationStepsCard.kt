package com.civileg.app.ui.compose.components.column

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.domain.entities.ColumnDesignResult
import com.civileg.app.domain.entities.AppCalculationStep
import com.civileg.app.domain.entities.SafetyCheckItem
import com.civileg.app.domain.entities.StepStatus

/**
 * Comprehensive column design results with step-by-step calculations
 * Based on ECP 203 / ACI 318 / SBC 304
 * Engineering reference: Eng. Yasser El-Leathy Column Design Notes
 */
@Composable
fun ColumnCalculationStepsCard(
    result: ColumnDesignResult,
    modifier: Modifier = Modifier
) {
    var expandedSection by remember { mutableIntStateOf(-1) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // SLENDERNESS
        ColumnExpandableSection(
            "Slenderness Classification", Icons.Default.Straighten,
            if (result.lambdaMax > result.lambdaLimitLong) Color(0xFFE74C3C)
            else if (result.lambdaMax > result.lambdaLimitShort) Color(0xFFE67E22)
            else Color(0xFF27AE60),
            expandedSection == 0, { expandedSection = if (expandedSection == 0) -1 else 0 }
        ) {
            ColDetailRow("K_in / K_out", "${String.format("%.2f", result.KfactorIn)} / ${String.format("%.2f", result.KfactorOut)}")
            ColDetailRow("Ho_in / Ho_out", "${String.format("%.0f", result.clearHeightIn)} / ${String.format("%.0f", result.clearHeightOut)} mm")
            ColDetailRow("lambda_in / lambda_out", "${String.format("%.2f", result.lambdaIn)} / ${String.format("%.2f", result.lambdaOut)}")
            ColDetailRow("lambda_max", "${String.format("%.2f", result.lambdaMax)}",
                color = if (result.lambdaMax > result.lambdaLimitLong) Color(0xFFE74C3C) else if (result.lambdaMax > result.lambdaLimitShort) Color(0xFFE67E22) else Color(0xFF27AE60))
            ColDetailRow("Short limit / Long limit", "${String.format("%.1f", result.lambdaLimitShort)} / ${String.format("%.1f", result.lambdaLimitLong)}")
            ColDetailRow("Classification", result.columnClassification,
                color = when (result.columnClassification) {
                    "Short" -> Color(0xFF27AE60); "Long" -> Color(0xFFE67E22)
                    else -> Color(0xFFE74C3C)
                })
        }

        // ADDITIONAL MOMENT
        ColumnExpandableSection(
            "Additional Moment (M_add)", Icons.Default.SwapHoriz,
            Color(0xFF3498DB),
            expandedSection == 1, { expandedSection = if (expandedSection == 1) -1 else 1 }
        ) {
            ColDetailRow("Deflection in", "${String.format("%.4f", result.deflectionIn * 1000)} mm")
            ColDetailRow("Deflection out", "${String.format("%.4f", result.deflectionOut * 1000)} mm")
            ColDetailRow("M_add in", "${String.format("%.2f", result.MaddIn)} kN.m")
            ColDetailRow("M_add out", "${String.format("%.2f", result.MaddOut)} kN.m")
            ColDetailRow("M_des in", "${String.format("%.2f", result.MdesIn)} kN.m", bold = true)
            ColDetailRow("M_des out", "${String.format("%.2f", result.MdesOut)} kN.m")
        }

        // REINFORCEMENT
        ColumnExpandableSection(
            "Reinforcement Design", Icons.Default.Build,
            Color(0xFF27AE60),
            expandedSection == 2, { expandedSection = if (expandedSection == 2) -1 else 2 }
        ) {
            ColDetailRow("As required", "${String.format("%.1f", result.AsRequired)} mm\u00B2")
            ColDetailRow("As min (${String.format("%.2f", result.rhoMin)}%)", "${String.format("%.1f", result.AsMin)} mm\u00B2")
            ColDetailRow("As max (${String.format("%.2f", result.rhoMax)}%)", "${String.format("%.1f", result.AsMax)} mm\u00B2")
            ColDetailRow("As provided", "${String.format("%.1f", result.AsProvided)} mm\u00B2 = ${result.finalBars}",
                bold = true, color = Color(0xFF27AE60))
            ColDetailRow("rho actual", "${String.format("%.3f", result.rhoActual)}%")
        }

        // TIES
        ColumnExpandableSection(
            "Tie/Stirrup Design", Icons.Default.Dashboard,
            Color(0xFF9B59B6),
            expandedSection == 3, { expandedSection = if (expandedSection == 3) -1 else 3 }
        ) {
            ColDetailRow("Max spacing", "${String.format("%.0f", result.tieSpacingMax)} mm")
            ColDetailRow("Dense spacing", "${String.format("%.0f", result.tieSpacingDense)} mm")
            ColDetailRow("Normal spacing", "${String.format("%.0f", result.tieSpacingNormal)} mm")
            ColDetailRow("Condensation zone", "${String.format("%.0f", result.condensationZoneLength)} mm")
            ColDetailRow("Tie description", result.tieDescription, bold = true)
        }

        // CAPACITY
        ColumnExpandableSection(
            "Capacity Verification", Icons.Default.Verified,
            if (result.isSafe) Color(0xFF27AE60) else Color(0xFFE74C3C),
            expandedSection == 4, { expandedSection = if (expandedSection == 4) -1 else 4 }
        ) {
            ColDetailRow("Pn0 (no moment)", "${String.format(".1f", result.Pu0)} kN")
            ColDetailRow("Pn (with moment)", "${String.format(".1f", result.axialCapacity)} kN", bold = true)
            ColDetailRow("Applied Pu", "${String.format(".1f", result.Pu)} kN")
            ColDetailRow("Utilization", "${String.format(".1f", result.utilizationRatio * 100)}%",
                color = if (result.utilizationRatio <= 1.0) Color(0xFF27AE60) else Color(0xFFE74C3C))
        }

        // STEP-BY-STEP
        ColumnExpandableSection(
            "Step-by-Step Calculations (${result.calculationSteps.size} steps)",
            Icons.Default.FormatListNumbered, Color(0xFF1ABC9C),
            expandedSection == 5, { expandedSection = if (expandedSection == 5) -1 else 5 }
        ) {
            for (step in result.calculationSteps) {
                ColumnCalcStepItem(step)
            }
        }

        // SAFETY
        ColumnExpandableSection(
            "Safety Checks Summary", Icons.Default.Shield,
            if (result.isSafe) Color(0xFF27AE60) else Color(0xFFE74C3C),
            expandedSection == 6, { expandedSection = if (expandedSection == 6) -1 else 6 }
        ) {
            for (check in result.safetyChecks) {
                ColumnSafetyRow(check)
            }
            if (result.warnings.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                for (warning in result.warnings) {
                    Text("\u26A0 $warning", color = Color(0xFFE67E22), fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        // ECONOMY
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Economy & Quantities", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                ColDetailRow("Concrete Volume", "${String.format(".3f", result.concreteVolume)} m\u00B3")
                ColDetailRow("Steel Weight", "${String.format(".1f", result.steelWeight)} kg")
                ColDetailRow("Overall Utilization", "${(result.utilizationRatio * 100).toInt()}%",
                    color = when {
                        result.utilizationRatio > 1.0 -> Color.Red
                        result.utilizationRatio > 0.9 -> Color(0xFFFF9800)
                        result.utilizationRatio > 0.4 -> Color(0xFF4CAF50)
                        else -> Color(0xFF2196F3)
                    })
            }
        }
    }
}

// ===== SUB-COMPONENTS =====

@Composable
private fun ColumnExpandableSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, isExpanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (isExpanded) color.copy(alpha = 0.08f) else Color.Transparent), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp).clickable { onToggle() }, verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color, modifier = Modifier.weight(1f))
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = color, modifier = Modifier.size(20.dp))
            }
            if (isExpanded) Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).padding(bottom = 12.dp)) { content() }
        }
    }
}

@Composable
private fun ColDetailRow(label: String, value: String, bold: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color, fontFamily = if (bold) FontFamily.Monospace else FontFamily.SansSerif)
    }
}

@Composable
private fun ColumnCalcStepItem(step: AppCalculationStep) {
    val bgColor = when (step.status) { StepStatus.CHECK_PASS -> Color(0xFF27AE60).copy(alpha = 0.1f); StepStatus.CHECK_FAIL -> Color(0xFFE74C3C).copy(alpha = 0.1f); StepStatus.WARNING -> Color(0xFFE67E22).copy(alpha = 0.1f); StepStatus.SECTION_HEADER -> Color(0xFF3498DB).copy(alpha = 0.1f); else -> Color.Transparent }
    val accent = when (step.status) { StepStatus.CHECK_PASS -> Color(0xFF27AE60); StepStatus.CHECK_FAIL -> Color(0xFFE74C3C); StepStatus.WARNING -> Color(0xFFE67E22); StepStatus.SECTION_HEADER -> Color(0xFF3498DB); else -> Color(0xFF1ABC9C) }
    Column(modifier = Modifier.fillMaxWidth().background(bgColor, RoundedCornerShape(8.dp)).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step.status == StepStatus.CHECK_PASS) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF27AE60), modifier = Modifier.size(16.dp))
            else if (step.status == StepStatus.CHECK_FAIL) Icon(Icons.Default.Cancel, null, tint = Color(0xFFE74C3C), modifier = Modifier.size(16.dp))
            else Text("${step.stepNumber}.", color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text(step.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = accent)
        }
        if (step.codeReference.isNotEmpty()) Text(step.codeReference, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 22.dp, top = 2.dp))
        if (step.formulaWithValues.isNotEmpty()) Text(step.formulaWithValues, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 22.dp, top = 4.dp), lineHeight = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(step.result, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = accent, modifier = Modifier.padding(start = 22.dp))
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ColumnSafetyRow(check: SafetyCheckItem) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(if (check.isSafe) Icons.Default.CheckCircle else Icons.Default.Cancel, null, tint = if (check.isSafe) Color(0xFF27AE60) else Color(0xFFE74C3C), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(check.name, fontSize = 12.sp)
        }
        Text(if (check.isSafe) "PASS" else "FAIL", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (check.isSafe) Color(0xFF27AE60) else Color(0xFFE74C3C))
    }
}
