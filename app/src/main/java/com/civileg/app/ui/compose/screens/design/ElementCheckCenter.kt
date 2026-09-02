package com.civileg.app.ui.compose.screens.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.civileg.app.R
import com.civileg.app.ui.designsystem.EngineeringStatus
import com.civileg.app.ui.designsystem.components.CalculationInput
import com.civileg.app.ui.designsystem.components.EngineeringCalculationViewer
import com.civileg.app.ui.designsystem.components.EngineeringCard
import com.civileg.app.ui.designsystem.components.EngineeringCheckRow
import com.civileg.app.ui.designsystem.components.EngineeringSectionHeader
import com.civileg.app.utils.CalculatorEngine
import java.util.Locale

fun checkUtilization(check: CalculatorEngine.DesignSafetyCheck): Double {
    val raw = when {
        check.limit <= 0.0 || check.value <= 0.0 -> if (check.isSafe) 0.5 else 1.1
        check.name.contains("Strength", ignoreCase = true) -> check.limit / check.value
        else -> check.value / check.limit
    }
    return raw.coerceIn(0.0, 1.25)
}

fun checkStatusOf(check: CalculatorEngine.DesignSafetyCheck): EngineeringStatus {
    val util = checkUtilization(check)
    return when {
        !check.isSafe && util > 1.05 -> EngineeringStatus.FAIL
        !check.isSafe -> EngineeringStatus.WARNING
        util >= 0.9 -> EngineeringStatus.WARNING
        else -> EngineeringStatus.PASS
    }
}

@Composable
fun elementOverallStatus(
    utilizationRatio: Double,
    isSafe: Boolean,
    warnThreshold: Double = 0.90
): EngineeringStatus = when {
    !isSafe -> if (utilizationRatio > warnThreshold) EngineeringStatus.FAIL else EngineeringStatus.WARNING
    utilizationRatio >= warnThreshold -> EngineeringStatus.WARNING
    else -> EngineeringStatus.PASS
}

@Composable
fun localizedCheckName(check: CalculatorEngine.DesignSafetyCheck): String =
    when {
        check.name.contains("Flexural Strength", ignoreCase = true) -> stringResource(R.string.eg_check_flexure)
        check.name.contains("Shear Stress", ignoreCase = true) -> stringResource(R.string.eg_check_shear_stress)
        check.name.contains("Deflection", ignoreCase = true) -> stringResource(R.string.eg_check_deflection)
        check.name.contains("Slenderness", ignoreCase = true) -> stringResource(R.string.eg_check_slenderness)
        check.name.contains("Biaxial", ignoreCase = true) -> stringResource(R.string.eg_check_biaxial)
        check.name.contains("Min Reinforcement", ignoreCase = true) -> stringResource(R.string.eg_check_min_reinforcement)
        check.name.contains("Punching", ignoreCase = true) -> stringResource(R.string.eg_check_punching)
        check.name.contains("Soil Pressure", ignoreCase = true) -> stringResource(R.string.eg_check_soil_pressure)
        check.name.contains("Min Thickness", ignoreCase = true) -> stringResource(R.string.eg_check_min_thickness)
        check.name.contains("Crack Control", ignoreCase = true) -> stringResource(R.string.eg_check_crack_control)
        check.name.contains("Overturning", ignoreCase = true) -> stringResource(R.string.eg_check_overturning)
        check.name.contains("Sliding", ignoreCase = true) -> stringResource(R.string.eg_check_sliding)
        else -> check.name
    }

private fun checkFormula(check: CalculatorEngine.DesignSafetyCheck): String = when {
    check.name.contains("Flexural Strength", ignoreCase = true) -> "Mn ≥ Mu"
    check.name.contains("Biaxial", ignoreCase = true) -> "Pu / φPn,max + m(Mux/φMnx + Muy/φMny) ≤ 1.0"
    check.name.contains("Slenderness", ignoreCase = true) -> "λ ≤ λ_limit"
    check.name.contains("Min Reinforcement", ignoreCase = true) -> "As,prov ≥ ρmin · Ag"
    check.name.contains("Punching", ignoreCase = true) -> "v_u ≤ v_c"
    check.name.contains("Soil Pressure", ignoreCase = true) -> "q_actual ≤ q_allow"
    check.name.contains("Deflection", ignoreCase = true) -> "Δ ≤ L / 250"
    else -> "value ≤ limit"
}

private fun checkInterpretationRes(status: EngineeringStatus): Int = when (status) {
    EngineeringStatus.PASS -> R.string.eg_interp_pass
    EngineeringStatus.WARNING -> R.string.eg_interp_warning
    else -> R.string.eg_interp_fail
}

private fun formatValue(v: Double): String = String.format(Locale.US, "%.2f", v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElementCheckCenter(
    checks: List<CalculatorEngine.DesignSafetyCheck>,
    modifier: Modifier = Modifier,
    codeRef: String = ""
) {
    var selected by remember { mutableStateOf<CalculatorEngine.DesignSafetyCheck?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        EngineeringSectionHeader(title = stringResource(R.string.eg_check_center))
        EngineeringCard {
            checks.forEach { check ->
                val status = checkStatusOf(check)
                val util = checkUtilization(check)
                EngineeringCheckRow(
                    title = localizedCheckName(check),
                    status = status,
                    detail = "${formatValue(check.value)} ${check.unit} · ${(util * 100).toInt()}%",
                    onClick = { selected = check }
                )
            }
        }
    }

    selected?.let { check ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            val status = checkStatusOf(check)
            val util = checkUtilization(check)
            val isCapacitySemantics = check.name.contains("Strength", ignoreCase = true)
            EngineeringCalculationViewer(
                title = localizedCheckName(check),
                formula = checkFormula(check),
                codeRef = codeRef,
                status = status,
                utilization = util,
                inputs = listOf(
                    CalculationInput(stringResource(R.string.eg_calc_value), formatValue(check.value), check.unit),
                    CalculationInput(stringResource(R.string.eg_calc_allowed), formatValue(check.limit), check.unit)
                ),
                resultValue = "${formatValue(if (isCapacitySemantics) check.limit / check.value.coerceAtLeast(1e-9) else check.value / check.limit.coerceAtLeast(1e-9))}",
                interpretation = stringResource(checkInterpretationRes(status)),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
