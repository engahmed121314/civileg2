package com.civileg.app.ui.compose.screens.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.civileg.app.ui.designsystem.EngineeringStatus
import com.civileg.app.ui.designsystem.components.EngineeringCard
import com.civileg.app.ui.designsystem.components.EngineeringPropertyRow
import com.civileg.app.ui.designsystem.components.EngineeringStatusBadge
import com.civileg.app.ui.designsystem.components.UtilizationBar
import com.civileg.app.ui.designsystem.engineeringType
import com.civileg.app.utils.CalculatorEngine

data class ElementProperty(val label: String, val value: String, val unit: String? = null)

@Composable
fun genericElementStatus(
    utilizationRatio: Double,
    isSafe: Boolean
): EngineeringStatus = elementOverallStatus(utilizationRatio, isSafe)

@Composable
fun GenericElementResultsSection(
    elementLabel: String,
    elementId: String,
    subtitle: String,
    utilizationRatio: Double,
    isSafe: Boolean,
    codeRef: String,
    checks: List<CalculatorEngine.DesignSafetyCheck>,
    modifier: Modifier = Modifier,
    properties: List<ElementProperty> = emptyList(),
    reinforcementRows: List<ElementProperty> = emptyList()
) {
    val status = genericElementStatus(utilizationRatio, isSafe)
    val type = engineeringType()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EngineeringCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("$elementLabel $elementId", style = type.sectionTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(subtitle, style = type.valueMedium, fontWeight = FontWeight.Bold)
                }
                EngineeringStatusBadge(status = status)
            }
            Spacer(Modifier.height(12.dp))
            UtilizationBar(utilization = utilizationRatio, status = status)
        }

        if (properties.isNotEmpty()) {
            EngineeringCard {
                properties.forEach { p ->
                    EngineeringPropertyRow(label = p.label, value = p.value, unit = p.unit)
                }
            }
        }

        if (reinforcementRows.isNotEmpty()) {
            EngineeringCard {
                EngineeringSectionHeaderLocal(titleRes = com.civileg.app.R.string.eg_reinforcement)
                reinforcementRows.forEach { r ->
                    EngineeringPropertyRow(label = r.label, value = r.value, unit = r.unit)
                }
            }
        }

        if (checks.isNotEmpty()) {
            ElementCheckCenter(checks = checks, codeRef = codeRef)
        }
    }
}

@Composable
private fun EngineeringSectionHeaderLocal(titleRes: Int) {
    androidx.compose.material3.Text(
        text = androidx.compose.ui.res.stringResource(titleRes).uppercase(),
        style = engineeringType().sectionTitle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}
