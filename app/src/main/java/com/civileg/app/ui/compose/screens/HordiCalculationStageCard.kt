package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.civileg.app.R
import com.civileg.app.domain.entities.SlabCalculationStage
import com.civileg.app.ui.components.AppText

/** Localized headings around unchanged LTR engineering equations and values. */
@Composable
internal fun HordiCalculationStageCard(stage: SlabCalculationStage, number: Int) {
    val headingId = when (stage.key) {
        "steel_area" -> R.string.ecp_stage_steel_area
        "effective_depth" -> R.string.ecp_stage_effective_depth
        "material_strength" -> R.string.ecp_stage_material_strength
        "flexure" -> R.string.ecp_stage_flexure
        "shear" -> R.string.ecp_stage_shear
        "service_loads" -> R.string.ecp_stage_service_loads
        "elastic_modulus" -> R.string.ecp_stage_elastic_modulus
        "section_stiffness" -> R.string.ecp_stage_section_stiffness
        "cracking_moment" -> R.string.ecp_stage_cracking_moment
        "stiffness_dead" -> R.string.ecp_stage_stiffness_dead
        "stiffness_total" -> R.string.ecp_stage_stiffness_total
        "immediate_dead" -> R.string.ecp_stage_immediate_dead
        "immediate_total" -> R.string.ecp_stage_immediate_total
        "immediate_live" -> R.string.ecp_stage_immediate_live
        "sustained_load" -> R.string.ecp_stage_sustained_load
        "stiffness_sustained" -> R.string.ecp_stage_stiffness_sustained
        "immediate_sustained" -> R.string.ecp_stage_immediate_sustained
        "creep_factor" -> R.string.ecp_stage_creep_factor
        "creep_increment" -> R.string.ecp_stage_creep_increment
        "mechanical_total" -> R.string.ecp_stage_mechanical_total
        "post_installation" -> R.string.ecp_stage_post_installation
        "limit_total" -> R.string.ecp_stage_limit_total
        "limit_live" -> R.string.ecp_stage_limit_live
        "limit_after" -> R.string.ecp_stage_limit_after
        else -> null
    }
    val heading = headingId?.let { stringResource(it) } ?: stage.heading
    Card(modifier = Modifier.fillMaxWidth().testTag("ecp-stage-${stage.key}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppText(stringResource(R.string.ecp_stage_numbered_heading, number, heading),
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StageMathLine(stringResource(R.string.ecp_stage_equation), stage.equation)
            StageMathLine(stringResource(R.string.ecp_stage_substitution), stage.substitution)
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppText(stringResource(R.string.ecp_stage_result), fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium)
                AppText(stage.result, modifier = Modifier.fillMaxWidth().testTag("ecp-stage-result-${stage.key}"),
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Left,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
            }
            AppText(stage.citation, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Left,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr))
        }
    }
}

@Composable
private fun StageMathLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        AppText(label, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppText(value, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Left,
            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
    }
}
