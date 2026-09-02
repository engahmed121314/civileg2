package com.civileg.app.ui.compose.screens.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.civileg.app.R
import com.civileg.app.ui.designsystem.EngineeringStatus
import com.civileg.app.ui.designsystem.components.EngineeringCard
import com.civileg.app.ui.designsystem.components.EngineeringPropertyRow
import com.civileg.app.ui.designsystem.components.EngineeringSectionHeader
import com.civileg.app.ui.designsystem.components.EngineeringStatusBadge
import com.civileg.app.ui.designsystem.components.UtilizationBar
import com.civileg.app.ui.designsystem.engineeringType
import androidx.compose.material3.Text
import com.civileg.app.utils.CalculatorEngine

@Composable
fun ColumnResultHeader(
    result: CalculatorEngine.ColumnResult,
    modifier: Modifier = Modifier,
    elementId: String = "C-101"
) {
    val status = elementOverallStatus(result.utilizationRatio, result.isSafe)
    val type = engineeringType()
    EngineeringCard(modifier = modifier) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column {
                Text("COLUMN $elementId", style = type.sectionTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${result.width.toInt()} × ${result.depth.toInt()} mm",
                    style = type.valueMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            EngineeringStatusBadge(status = status)
        }
        Spacer(Modifier.height(12.dp))
        UtilizationBar(utilization = result.utilizationRatio, status = status)
        Spacer(Modifier.height(8.dp))
        EngineeringPropertyRow(label = "Pu", value = "%.1f".format(result.pu), unit = "kN")
        if (result.muX != 0.0) EngineeringPropertyRow(label = "Mx", value = "%.1f".format(result.muX), unit = "kN.m")
        if (result.muY != 0.0) EngineeringPropertyRow(label = "My", value = "%.1f".format(result.muY), unit = "kN.m")
    }
}

@Composable
fun ColumnReinforcementSummary(
    result: CalculatorEngine.ColumnResult,
    modifier: Modifier = Modifier
) {
    EngineeringCard(modifier = modifier) {
        EngineeringSectionHeader(title = stringResource(R.string.eg_reinforcement))
        EngineeringPropertyRow(
            label = stringResource(R.string.column_main_reinforcement),
            value = result.reinforcement.barString
        )
        EngineeringPropertyRow(
            label = stringResource(R.string.stirrups),
            value = result.stirrups.description
        )
        EngineeringPropertyRow(
            label = stringResource(R.string.eg_reinforcement_ratio),
            value = "%.2f".format(result.reinforcementRatio),
            unit = "%"
        )
        if (result.isDuctile && result.confinementLength > 0) {
            EngineeringPropertyRow(
                label = stringResource(R.string.eg_confinement_length),
                value = "%.0f".format(result.confinementLength),
                unit = "mm"
            )
        }
    }
}

fun columnCodeRef(code: CalculatorEngine.AppDesignCode): String = when (code) {
    CalculatorEngine.AppDesignCode.ACI -> "ACI 318"
    CalculatorEngine.AppDesignCode.SAUDI -> "SBC 304"
    else -> "ECP 203"
}

@Composable
fun ColumnDesignResultsSection(
    result: CalculatorEngine.ColumnResult,
    modifier: Modifier = Modifier,
    elementId: String = "C-101"
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ColumnResultHeader(result = result, elementId = elementId)
        ColumnReinforcementSummary(result = result)
        ElementCheckCenter(checks = result.safetyChecks, codeRef = columnCodeRef(result.code))
    }
}
