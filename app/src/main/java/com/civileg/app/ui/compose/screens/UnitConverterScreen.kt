package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.ui.responsive.dimens
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor
import com.civileg.app.domain.validation.FormValidator
import com.civileg.app.utils.QuickToolsEngine
import com.civileg.app.utils.QuickToolsEngine.QuantityType
import com.civileg.app.utils.UnitConversionFormat
import com.civileg.app.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitConverterScreen(
    onNavigateBack: () -> Unit = {}
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedCategory by remember { mutableStateOf(QuantityType.LENGTH) }
    var inputValue by remember { mutableStateOf("1.0") }
    // Units are keyed by category so a category switch never pairs a unit with the wrong table.
    val units = remember(selectedCategory) { QuickToolsEngine.unitsFor(selectedCategory) }
    var fromUnit by remember(selectedCategory) { mutableStateOf(units.first()) }
    var toUnit by remember(selectedCategory) { mutableStateOf(if (units.size > 1) units[1] else units.first()) }

    // Blank or invalid input has no result. It used to be read as 0 and shown as a result.
    val parsed = FormValidator.parseNumber(inputValue)
    val result: Double? = remember(parsed, fromUnit, toUnit, selectedCategory) {
        parsed?.let { QuickToolsEngine.convertUnit(selectedCategory, it, fromUnit, toUnit) }
    }
    val inputInvalid = parsed == null
    // A finite input can still overflow in the conversion (e.g. km to mm): say so on the result field.
    val resultOutOfRange = result != null && !result.isFinite()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { AppText(stringResource(R.string.screen_unit_converter_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = MaterialTheme.dimens.formMaxWidth)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.dimens.screenHorizontalPadding, vertical = MaterialTheme.dimens.screenVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.dimens.sectionSpacing)
        ) {
            // Category Selector
            ScrollableTabRow(
                selectedTabIndex = QuantityType.values().indexOf(selectedCategory),
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                listOf(
                    QuantityType.LENGTH to R.string.converter_length,
                    QuantityType.AREA to R.string.converter_area,
                    QuantityType.VOLUME to R.string.converter_volume,
                    QuantityType.MASS to R.string.steel_tables_weight_label,
                    QuantityType.FORCE to R.string.converter_force,
                    QuantityType.STRESS to R.string.converter_pressure,
                    QuantityType.MOMENT to R.string.converter_moment
                ).forEach { (type, label) ->
                    CategoryTab(stringResource(label), selectedCategory == type) { selectedCategory = type }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(if (landscape) 8.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(if (landscape) 0.dp else 16.dp)) {
                    // From Input
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = inputValue,
                            onValueChange = { inputValue = it },
                            label = { AppText(stringResource(R.string.converter_input_value)) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = inputInvalid,
                            supportingText = if (inputInvalid) {
                                { AppText(stringResource(R.string.converter_invalid_number), color = MaterialTheme.colorScheme.error) }
                            } else null,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        UnitDropdown(fromUnit, units) { fromUnit = it }
                    }

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        IconButton(modifier = Modifier.size(if (landscape) 28.dp else 48.dp), onClick = {
                            val temp = fromUnit
                            fromUnit = toUnit
                            toUnit = temp
                        }) {
                            Icon(Icons.Default.SwapVert, "Swap", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // To Output
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = if (resultOutOfRange) "" else result?.let { UnitConversionFormat.format(it) } ?: "",
                            isError = resultOutOfRange,
                            supportingText = if (resultOutOfRange) {
                                { AppText(stringResource(R.string.converter_result_out_of_range), color = MaterialTheme.colorScheme.error) }
                            } else null,
                            onValueChange = {},
                            readOnly = true,
                            label = { AppText(stringResource(R.string.converter_result)) },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        UnitDropdown(toUnit, units) { toUnit = it }
                    }
                }
            }

            // Quick Info Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(12.dp))
                    AppText(
                        "1 $fromUnit = ${UnitConversionFormat.format(QuickToolsEngine.convertUnit(selectedCategory, 1.0, fromUnit, toUnit))} $toUnit",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun CategoryTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = { AppText(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.width(100.dp)
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { unit ->
                DropdownMenuItem(
                    text = { AppText(unit) },
                    onClick = {
                        onSelect(unit)
                        expanded = false
                    }
                )
            }
        }
    }
}
