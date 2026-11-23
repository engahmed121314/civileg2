package com.civileg.app.ui.compose.screens

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.civileg.app.R
import com.civileg.app.utils.ComplianceMatrix
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor

/**
 * شاشة دليل الامتثال: قوائم فحص جاهزة حسب كود التصميم والتخصص.
 * تتيح للمفتش وضع خانات الاختيار يدويًا لبناء قائمة فحص مباشرة في الموقع.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityComplianceScreen() {
    val standards = remember { ComplianceMatrix.getStandards() }
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("quality_compliance_checklist", Context.MODE_PRIVATE)
    }
    var selectedCode by remember {
        val saved = prefs.getString("selected_code", null)
        mutableStateOf(
            standards.firstOrNull { it.code == saved }?.code
                ?: standards.firstOrNull()?.code
                ?: "ECP"
        )
    }
    var disciplineFilter by remember {
        mutableStateOf(prefs.getString("discipline_filter", null))
    }
    var showResetConfirm by remember { mutableStateOf(false) }

    val checked = remember {
        mutableStateMapOf<String, Boolean>().apply {
            prefs.getStringSet("checked_keys", emptySet())
                ?.forEach { put(it, true) }
        }
    }

    LaunchedEffect(selectedCode, disciplineFilter) {
        prefs.edit()
            .putString("selected_code", selectedCode)
            .putString("discipline_filter", disciplineFilter)
            .apply()
    }

    val standard = standards.firstOrNull { it.code == selectedCode }
    val standardCode = standard?.code ?: ""
    val items = standard?.checklist
        ?.filter { disciplineFilter == null || it.discipline == disciplineFilter }
        ?: emptyList()

    val checkedCount = items.count { checked["$standardCode:${it.code}"] == true }
    val progress = if (items.isNotEmpty()) checkedCount * 100f / items.size else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // اختيار الكود
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                AppText(
                    androidx.compose.ui.res.stringResource(com.civileg.app.R.string.qc_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    standards.forEach { s ->
                        FilterChip(
                            selected = selectedCode == s.code,
                            onClick = { selectedCode = s.code },
                            label = { AppText(s.code) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                AppText(
                    standard?.let { androidx.compose.ui.res.stringResource(com.civileg.app.R.string.qc_version, it.name, it.version) } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // تصفية التخصص + تقدم الحصر
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        AppText(androidx.compose.ui.res.stringResource(com.civileg.app.R.string.qc_progress), style = MaterialTheme.typography.titleSmall)
                        AppText(
                            androidx.compose.ui.res.stringResource(com.civileg.app.R.string.qc_progress_count, checkedCount, items.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { showResetConfirm = true },
                        enabled = checked.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = androidx.compose.ui.res.stringResource(com.civileg.app.R.string.qc_reset))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))
                AppText(
                    androidx.compose.ui.res.stringResource(com.civileg.app.R.string.qc_discipline),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                var filterExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = filterExpanded,
                    onExpandedChange = { filterExpanded = !filterExpanded }
                ) {
                    OutlinedTextField(
                        value = disciplineFilter?.let { disciplineLabel(it) } ?: androidx.compose.ui.res.stringResource(com.civileg.app.R.string.qc_all_disciplines),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = filterExpanded,
                        onDismissRequest = { filterExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { AppText(androidx.compose.ui.res.stringResource(com.civileg.app.R.string.qc_all_disciplines)) },
                            onClick = { disciplineFilter = null; filterExpanded = false }
                        )
                        ALL_DISCIPLINES.forEach { key ->
                            DropdownMenuItem(
                                text = { AppText(disciplineLabel(key)) },
                                onClick = { disciplineFilter = key; filterExpanded = false }
                            )
                        }
                    }
                }
            }
        }

        // قائمة الفحص
        if (items.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    AppText(
                        androidx.compose.ui.res.stringResource(com.civileg.app.R.string.qc_no_items),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    AppText(
                        androidx.compose.ui.res.stringResource(com.civileg.app.R.string.qc_checklist, items.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    items.forEach { item ->
                        val key = "$standardCode:${item.code}"
                        val isChecked = checked[key] == true
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { isCheckedNow ->
                                    if (isCheckedNow) checked[key] = true else checked.remove(key)
                                    prefs.edit()
                                        .putStringSet(
                                            "checked_keys",
                                            checked.filterValues { it }.keys
                                        )
                                        .apply()
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AppText(
                                        item.code,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    if (!item.mandatory) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            AppText(
                                                androidx.compose.ui.res.stringResource(com.civileg.app.R.string.qc_optional),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                                AppText(
                                    item.description,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                AppText(
                                    androidx.compose.ui.res.stringResource(com.civileg.app.R.string.qc_reference, item.reference),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            if (isChecked) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { AppText(androidx.compose.ui.res.stringResource(com.civileg.app.R.string.qc_reset_title)) },
            text = { AppText(androidx.compose.ui.res.stringResource(com.civileg.app.R.string.qc_reset_message)) },
            confirmButton = {
                AppButton(
                    label = androidx.compose.ui.res.stringResource(com.civileg.app.R.string.qc_reset_confirm),
                    fillMaxWidth = false,
                    style = AppButtonStyle.Text,
                    onClick = {
                        checked.clear()
                        prefs.edit().remove("checked_keys").apply()
                        showResetConfirm = false
                    }
                )
            },
            dismissButton = {
                AppButton(
                    label = stringResource(R.string.cancel),
                    onClick = { showResetConfirm = false },
                    style = AppButtonStyle.Text,
                    fillMaxWidth = false
                )
            }
        )
    }
}

private val ALL_DISCIPLINES = listOf(
    "CONCRETE", "SOIL", "REBAR", "STEEL", "ROADS", "BRIDGES", "TUNNELS", "BUILDINGS"
)

@Composable
private fun disciplineLabel(key: String): String = when (key) {
    "CONCRETE" -> stringResource(R.string.qc_discipline_concrete)
    "SOIL" -> stringResource(R.string.qc_discipline_soil)
    "REBAR" -> stringResource(R.string.qc_discipline_rebar)
    "STEEL" -> stringResource(R.string.qc_discipline_steel)
    "ROADS" -> stringResource(R.string.qc_discipline_roads)
    "BRIDGES" -> stringResource(R.string.qc_discipline_bridges)
    "TUNNELS" -> stringResource(R.string.qc_discipline_tunnels)
    "BUILDINGS" -> stringResource(R.string.qc_discipline_buildings)
    else -> key
}