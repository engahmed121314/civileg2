package com.civileg.app.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.civileg.app.R
import com.civileg.app.ui.designsystem.EngineeringStatus
import com.civileg.app.ui.designsystem.engineeringType

data class CalculationInput(val label: String, val value: String, val unit: String? = null)

@Composable
fun EngineeringCalculationViewer(
    title: String,
    formula: String,
    codeRef: String,
    status: EngineeringStatus,
    utilization: Double,
    modifier: Modifier = Modifier,
    inputs: List<CalculationInput> = emptyList(),
    substitution: String? = null,
    resultValue: String? = null,
    limitValue: String? = null,
    interpretation: String? = null
) {
    val type = engineeringType()
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        if (inputs.isNotEmpty()) {
            ViewerSection(stringResource(R.string.eg_calc_inputs)) {
                inputs.forEach { input ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(input.label, style = type.metricLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${input.value}${input.unit?.let { " $it" } ?: ""}",
                            style = type.valueSmall
                        )
                    }
                }
            }
        }

        ViewerSection(stringResource(R.string.eg_calc_formula)) {
            Text(formula, style = type.formula, color = MaterialTheme.colorScheme.primary)
        }

        if (substitution != null) {
            ViewerSection(stringResource(R.string.eg_calc_substitution)) {
                Text(substitution, style = type.formula)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (resultValue != null) {
                Column {
                    Text(stringResource(R.string.eg_calc_result), style = type.metricLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(resultValue, style = type.valueMedium, fontWeight = FontWeight.Bold)
                }
            }
            if (limitValue != null) {
                Column {
                    Text(stringResource(R.string.eg_calc_limit), style = type.metricLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(limitValue, style = type.valueMedium)
                }
            }
        }

        Column {
            Text(stringResource(R.string.eg_utilization), style = type.metricLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            UtilizationBar(utilization = utilization, status = status)
        }

        EngineeringStatusBadge(status = status)

        if (interpretation != null) {
            Text(interpretation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        Text(
            text = stringResource(R.string.eg_calc_code_ref, codeRef),
            style = type.codeRef,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ViewerSection(label: String, content: @Composable () -> Unit) {
    val colors = com.civileg.app.ui.designsystem.engineeringColors()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(0.dp)
    ) {
        Text(label.uppercase(), style = engineeringType().sectionTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp)
        ) { content() }
    }
}
