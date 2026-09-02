package com.civileg.app.ui.compose.components.beam

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
import com.civileg.app.domain.entities.BeamDesignResult
import com.civileg.app.domain.entities.AppCalculationStep
import com.civileg.app.domain.entities.SafetyCheckItem
import kotlin.math.max
import com.civileg.app.domain.entities.StepStatus

/**
 * Comprehensive beam design results showing step-by-step calculations
 * from PDFs 04-12: Load Distribution, BMD/SFD, Flexure, Shear, Torsion,
 * Deflection, Crack Width, Development Length, Moment of Resistance
 */
@Composable
fun BeamComprehensiveResultsCard(
    result: BeamDesignResult,
    modifier: Modifier = Modifier
) {
    var expandedSection by remember { mutableStateOf(-1) }
    
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // LOAD DISTRIBUTION (PDF-05)
        BeamExpandableSection("Load Distribution (PDF-05)", Icons.Default.ArrowDownward, Color(0xFF9B59B6), expandedSection == 0, { expandedSection = if (expandedSection == 0) -1 else 0 }) {
            BeamDetailRow("Self-Weight", "${String.format("%.2f", result.ownWeightLoad)} kN/m")
            BeamDetailRow("Flooring", "${String.format("%.2f", result.flooringLoad)} kN/m")
            BeamDetailRow("Plaster", "${String.format("%.2f", result.plasterLoad)} kN/m")
            if (result.wallLoad > 0) BeamDetailRow("Wall Load", "${String.format("%.2f", result.wallLoad)} kN/m")
            HorizontalDivider(color = Color(0x22FFFFFF))
            BeamDetailRow("Total DL", "${String.format("%.2f", result.totalDeadLoad)} kN/m", bold = true)
            BeamDetailRow("Total LL", "${String.format("%.2f", result.totalLiveLoad)} kN/m", bold = true)
            BeamDetailRow("wu (ultimate)", "${String.format("%.2f", result.ultimateLoad)} kN/m", bold = true, color = Color(0xFFE74C3C))
        }

        // STRUCTURAL ANALYSIS (PDF-04)
        BeamExpandableSection("Structural Analysis - BMD/SFD (PDF-04)", Icons.Default.ShowChart, Color(0xFF3498DB), expandedSection == 1, { expandedSection = if (expandedSection == 1) -1 else 1 }) {
            BeamDetailRow("Max Moment (+)", "${String.format("%.2f", result.maxMomentPos)} kN.m")
            BeamDetailRow("Max Moment (-)", "${String.format("%.2f", result.maxMomentNeg)} kN.m")
            BeamDetailRow("Max Shear (L/R)", "${String.format("%.2f", result.maxShearLeft)} / ${String.format("%.2f", result.maxShearRight)} kN")
            BeamDetailRow("Reactions (L/R)", "${String.format("%.2f", result.reactionLeft)} / ${String.format("%.2f", result.reactionRight)} kN")
            BeamDetailRow("Zero Shear at", "${String.format("%.2f", result.pointOfZeroShear)} m")
        }

        // FLEXURE DESIGN (PDF-07, 08, 12)
        BeamExpandableSection("Flexure Design - First Principles (PDF-07)", Icons.Default.Functions, Color(0xFF27AE60), expandedSection == 2, { expandedSection = if (expandedSection == 2) -1 else 2 }) {
            if (result.K > 0) {
                BeamDetailRow("K / Kbal", "${String.format("%.4f", result.K)} / ${String.format("%.4f", result.Kbal)}")
                BeamDetailRow("Status", if (result.K <= result.Kbal) "Singly Reinforced" else "DOUBLY REINFORCED", color = if (result.K <= result.Kbal) Color(0xFF27AE60) else Color(0xFFE74C3C))
            }
            if (result.Rn > 0) {
                BeamDetailRow("Rn", "${String.format("%.4f", result.Rn)} MPa")
                BeamDetailRow("rho / rho_bal", "${String.format("%.6f", result.rhoActual)} / ${String.format("%.6f", result.rhoBalanced)}")
            }
            HorizontalDivider(color = Color(0x22FFFFFF))
            BeamDetailRow("Neutral Axis (x)", "${String.format("%.1f", result.neutralAxisDepth)} mm")
            BeamDetailRow("Stress Block (a)", "${String.format("%.1f", result.concreteStressBlockDepth)} mm")
            BeamDetailRow("Lever Arm (z)", "${String.format("%.1f", result.leverArmZ)} mm")
            HorizontalDivider(color = Color(0x22FFFFFF))
            BeamDetailRow("As req / min / max", "${String.format("%.0f", result.asRequired)} / ${String.format("%.0f", result.asMin)} / ${String.format("%.0f", result.asMax)} mm\u00B2")
            BeamDetailRow("As provided", "${String.format("%.0f", result.asProvided)} mm\u00B2 = ${result.bottomBars}", bold = true, color = Color(0xFF27AE60))
            if (result.needsCompressionSteel) BeamDetailRow("Compression Steel", result.compressionBars, color = Color(0xFFE67E22))
            HorizontalDivider(color = Color(0x22FFFFFF))
            BeamDetailRow("MR (PDF-12)", "${String.format("%.2f", result.momentOfResistance)} kN.m", bold = true)
            BeamDetailRow("MR/Mu", String.format("%.2f", result.momentOfResistance / kotlin.math.max(result.maxMoment, 0.01)), color = if (result.momentOfResistance >= result.maxMoment) Color(0xFF27AE60) else Color(0xFFE74C3C))
        }

        // SHEAR DESIGN (PDF-09)
        BeamExpandableSection("Shear Design (PDF-09)", Icons.Default.ContentCut, Color(0xFFE74C3C), expandedSection == 3, { expandedSection = if (expandedSection == 3) -1 else 3 }) {
            BeamDetailRow("Applied qu", "${String.format("%.3f", result.appliedShearStress)} MPa")
            BeamDetailRow("Concrete vc", "${String.format("%.3f", result.vc)} MPa")
            BeamDetailRow("Max allowed", "${String.format("%.3f", result.vMax)} MPa")
            HorizontalDivider(color = Color(0x22FFFFFF))
            BeamDetailRow("Stirrups (support)", "\u00D8${result.stirrupDia} @ ${result.stirrupSpacingSupport.toInt()}mm c/c", bold = true)
            BeamDetailRow("Stirrups (midspan)", "\u00D8${result.stirrupDia} @ ${result.stirrupSpacingMidspan.toInt()}mm c/c")
            BeamDetailRow("Condensation zone", "${String.format("%.0f", result.condensationZoneLength)} mm")
        }

        // DEFLECTION CHECK
        BeamExpandableSection("Deflection Check (Enhanced Ie)", Icons.Default.Height, Color(0xFF3498DB), expandedSection == 4, { expandedSection = if (expandedSection == 4) -1 else 4 }) {
            BeamDetailRow("Ig / Icr / Ie", "${String.format("%.2e", result.grossMomentOfInertia)} / ${String.format("%.2e", result.crackedMomentOfInertia)} / ${String.format("%.2e", result.effectiveMomentOfInertia)}")
            BeamDetailRow("Mcr", "${String.format("%.2f", result.crackingMoment)} kN.m")
            BeamDetailRow("Immediate \u0394", "${String.format("%.2f", result.immediateDeflection)} mm")
            BeamDetailRow("Long-term \u0394", "${String.format("%.2f", result.longTermDeflection)} mm", bold = true, color = if (result.deflectionIsSafe) Color(0xFF27AE60) else Color(0xFFE74C3C))
            BeamDetailRow("Allowable", "${String.format("%.2f", result.allowableDeflection)} mm (${result.deflectionSpanRatio})")
        }

        // ADDITIONAL CHECKS
        BeamExpandableSection("Crack Width & Dev. Length", Icons.Default.VerifiedUser, Color(0xFFF39C12), expandedSection == 5, { expandedSection = if (expandedSection == 5) -1 else 5 }) {
            BeamDetailRow("Crack Width wk", "${String.format("%.3f", result.crackWidthCalculated)} mm \u2264 ${result.crackWidthAllowable} mm", color = if (result.crackIsSafe) Color(0xFF27AE60) else Color(0xFFE74C3C))
            BeamDetailRow("Dev. Length Ld", "${String.format("%.0f", result.developmentLengthRequired)} mm")
            BeamDetailRow("Lap Length", "${String.format("%.0f", result.lapLength)} mm")
            BeamDetailRow("Bond Stress fbd", "${String.format("%.2f", result.bondStress)} MPa")
        }

        // STEP-BY-STEP CALCULATIONS
        BeamExpandableSection("Step-by-Step Calculations (${result.calculationSteps.size} steps)", Icons.Default.FormatListNumbered, Color(0xFF1ABC9C), expandedSection == 6, { expandedSection = if (expandedSection == 6) -1 else 6 }) {
            result.calculationSteps.forEach { step ->
                BeamCalcStepItem(step)
            }
        }

        // SAFETY CHECKS
        BeamExpandableSection("Safety Checks", Icons.Default.Shield, if (result.isSafe) Color(0xFF27AE60) else Color(0xFFE74C3C), expandedSection == 7, { expandedSection = if (expandedSection == 7) -1 else 7 }) {
            result.safetyChecks.forEach { check -> BeamSafetyRow(check) }
            if (result.warnings.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                result.warnings.forEach { Text("\u26A0 $it", color = Color(0xFFE67E22), fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        // ECONOMY
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Economy & Quantities", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                BeamDetailRow("Concrete Volume", "${String.format("%.3f", result.concreteVolume)} m\u00B3")
                BeamDetailRow("Steel Weight", "${String.format("%.1f", result.steelWeight)} kg")
                BeamDetailRow("Overall Utilization", "${(result.utilizationRatio * 100).toInt()}%", color = when { result.utilizationRatio > 1.0 -> Color.Red; result.utilizationRatio > 0.9 -> Color(0xFFFF9800); result.utilizationRatio > 0.4 -> Color(0xFF4CAF50); else -> Color(0xFF2196F3) })
            }
        }
    }
}

@Composable
private fun BeamExpandableSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, isExpanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
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
private fun BeamDetailRow(label: String, value: String, bold: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color, fontFamily = if (bold) FontFamily.Monospace else FontFamily.SansSerif)
    }
}

@Composable
private fun BeamCalcStepItem(step: AppCalculationStep) {
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
private fun BeamSafetyRow(check: SafetyCheckItem) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(if (check.isSafe) Icons.Default.CheckCircle else Icons.Default.Cancel, null, tint = if (check.isSafe) Color(0xFF27AE60) else Color(0xFFE74C3C), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(check.name, fontSize = 12.sp)
        }
        Text(if (check.isSafe) "PASS" else "FAIL", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (check.isSafe) Color(0xFF27AE60) else Color(0xFFE74C3C))
    }
}
