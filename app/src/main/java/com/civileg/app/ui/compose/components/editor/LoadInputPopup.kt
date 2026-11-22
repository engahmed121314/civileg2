package com.civileg.app.ui.compose.components.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.civileg.app.R
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.civileg.app.domain.entities.MemberLoadType
import com.civileg.app.domain.entities.NodalLoad

@Composable
fun NodalLoadDialog(
    nodeId: Int,
    onConfirm: (NodalLoad) -> Unit,
    onDismiss: () -> Unit
) {
    var fxStr by remember { mutableStateOf("0.0") }
    var fyStr by remember { mutableStateOf("-10.0") }
    var mzStr by remember { mutableStateOf("0.0") }
    var loadCase by remember { mutableStateOf("DL") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            AppText(stringResource(R.string.ed_load_on_node, nodeId), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fxStr,
                    onValueChange = { fxStr = it },
                    label = { AppText("Fx (kN)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fyStr,
                    onValueChange = { fyStr = it },
                    label = { AppText("Fy (kN)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mzStr,
                    onValueChange = { mzStr = it },
                    label = { AppText("Mz (kN.m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("DL", "LL", "WL").forEach { case ->
                        FilterChip(
                            selected = loadCase == case,
                            onClick = { loadCase = case },
                            label = { AppText(case) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppButton(
                label = stringResource(R.string.ed_add),
                style = AppButtonStyle.Text,
                fillMaxWidth = false,
                onClick = {
                    val fx = fxStr.toDoubleOrNull() ?: 0.0
                    val fy = fyStr.toDoubleOrNull() ?: 0.0
                    val mz = mzStr.toDoubleOrNull() ?: 0.0
                    onConfirm(NodalLoad(nodeId, fx, fy, mz, loadCase))
                }
            )
        },
        dismissButton = {
            AppButton(label = stringResource(R.string.cancel), style = AppButtonStyle.Text, fillMaxWidth = false, onClick = onDismiss)
        }
    )
}

@Composable
fun MemberLoadDialog(
    memberId: Int,
    onConfirm: (MemberLoadType, Double, Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf(MemberLoadType.UDL) }
    var valStr by remember { mutableStateOf("-10.0") }
    var posStr by remember { mutableStateOf("0.0") }
    var loadCase by remember { mutableStateOf("DL") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            AppText(stringResource(R.string.ed_load_on_member, memberId), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MemberLoadType.entries.forEach { lt ->
                        FilterChip(
                            selected = selectedType == lt,
                            onClick = { selectedType = lt },
                            label = { AppText(lt.displayName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = valStr,
                    onValueChange = { valStr = it },
                    label = { AppText(stringResource(R.string.ed_load_value_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (selectedType == MemberLoadType.PointLoad || selectedType == MemberLoadType.Moment ||
                    selectedType == MemberLoadType.Trapezoidal) {
                    OutlinedTextField(
                        value = posStr,
                        onValueChange = { posStr = it },
                        label = {
                            AppText(
                                if (selectedType == MemberLoadType.Trapezoidal)
                                    stringResource(R.string.ed_load_trapezoid_a)
                                else stringResource(R.string.ed_load_position)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("DL", "LL", "WL").forEach { case ->
                        FilterChip(
                            selected = loadCase == case,
                            onClick = { loadCase = case },
                            label = { AppText(case) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppButton(
                label = stringResource(R.string.ed_add),
                style = AppButtonStyle.Text,
                fillMaxWidth = false,
                onClick = {
                    val v = valStr.toDoubleOrNull() ?: 0.0
                    val p = posStr.toDoubleOrNull() ?: 0.0
                    onConfirm(selectedType, v, p, loadCase)
                }
            )
        },
        dismissButton = {
            AppButton(label = stringResource(R.string.cancel), style = AppButtonStyle.Text, fillMaxWidth = false, onClick = onDismiss)
        }
    )
}
