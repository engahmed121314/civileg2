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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.civileg.app.R
import com.civileg.app.ui.designsystem.EngineeringStatus
import com.civileg.app.ui.designsystem.components.AsComparisonBar
import com.civileg.app.ui.designsystem.components.EngineeringCard
import com.civileg.app.ui.designsystem.components.EngineeringPropertyRow
import com.civileg.app.ui.designsystem.components.EngineeringSectionHeader
import com.civileg.app.ui.designsystem.components.EngineeringStatusBadge
import com.civileg.app.ui.designsystem.components.UtilizationBar
import com.civileg.app.ui.designsystem.engineeringType
import com.civileg.app.utils.CalculatorEngine

@Composable
fun beamOverallStatus(result: CalculatorEngine.BeamResult): EngineeringStatus =
    elementOverallStatus(result.utilizationRatio, result.isSafe)

private fun codeRefFor(code: CalculatorEngine.AppDesignCode): String = when (code) {
    CalculatorEngine.AppDesignCode.ACI -> "ACI 318"
    CalculatorEngine.AppDesignCode.SAUDI -> "SBC 304"
    else -> "ECP 203"
}

@Composable
fun BeamResultHeader(
    result: CalculatorEngine.BeamResult,
    modifier: Modifier = Modifier,
    elementId: String = "B-101"
) {
    val status = beamOverallStatus(result)
    val type = engineeringType()
    EngineeringCard(modifier = modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("BEAM $elementId", style = type.sectionTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    }
}

@Composable
fun BeamReinforcementSummary(
    result: CalculatorEngine.BeamResult,
    modifier: Modifier = Modifier
) {
    val type = engineeringType()
    EngineeringCard(modifier = modifier) {
        EngineeringSectionHeader(title = stringResource(R.string.eg_reinforcement))
        EngineeringPropertyRow(
            label = stringResource(R.string.beam_bottom_reinforcement),
            value = result.reinforcementBottom.barString,
            valueColor = MaterialTheme.colorScheme.onSurface
        )
        EngineeringPropertyRow(
            label = stringResource(R.string.beam_top_reinforcement),
            value = result.reinforcementTop.barString,
            valueColor = MaterialTheme.colorScheme.onSurface
        )
        EngineeringPropertyRow(
            label = stringResource(R.string.stirrups),
            value = result.stirrups.description,
            valueColor = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        AsComparisonBar(
            required = result.reinforcementBottom.area / maxOf(result.utilizationRatio, 0.01),
            provided = result.reinforcementBottom.area
        )
        Text(
            text = stringResource(R.string.eg_as_hint),
            style = type.unit,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun BeamDesignResultsSection(
    result: CalculatorEngine.BeamResult,
    elementId: String = "B-101"
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BeamResultHeader(result = result, elementId = elementId)
        BeamReinforcementSummary(result = result)
        ElementCheckCenter(checks = result.safetyChecks, codeRef = codeRefFor(result.code))
    }
}
