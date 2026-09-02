package com.civileg.app.ui.compose.screens.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import com.civileg.app.utils.CalculatorEngine

@Composable
fun slabOverallStatus(result: CalculatorEngine.SlabResult): EngineeringStatus =
    elementOverallStatus(result.utilizationRatio, result.isSafe)

@Composable
fun SlabResultHeader(
    result: CalculatorEngine.SlabResult,
    modifier: Modifier = Modifier,
    elementId: String = "S-101"
) {
    val status = slabOverallStatus(result)
    val type = engineeringType()
    EngineeringCard(modifier = modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("SLAB $elementId", style = type.sectionTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${result.type.displayName} · ${result.thickness.toInt()} mm",
                    style = type.valueMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            EngineeringStatusBadge(status = status)
        }
        Spacer(Modifier.height(12.dp))
        UtilizationBar(utilization = result.utilizationRatio, status = status)
    }
}

@Composable
fun SlabReinforcementSummary(
    result: CalculatorEngine.SlabResult,
    modifier: Modifier = Modifier
) {
    EngineeringCard(modifier = modifier) {
        EngineeringSectionHeader(title = stringResource(R.string.eg_reinforcement))
        if (result.reinforcementMain.numBars > 0 || result.reinforcementMain.spacing > 0) {
            EngineeringPropertyRow(
                label = stringResource(R.string.eg_slab_main_rebar),
                value = result.reinforcementMain.barString
            )
        }
        if (result.reinforcementSecondary.numBars > 0 || result.reinforcementSecondary.spacing > 0) {
            EngineeringPropertyRow(
                label = stringResource(R.string.eg_slab_secondary_rebar),
                value = result.reinforcementSecondary.barString
            )
        }
    }
}

@Composable
fun SlabDesignResultsSection(
    result: CalculatorEngine.SlabResult,
    elementId: String = "S-101"
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SlabResultHeader(result = result, elementId = elementId)
        SlabReinforcementSummary(result = result)
        ElementCheckCenter(checks = result.safetyChecks, codeRef = columnCodeRef(result.code))
    }
}
