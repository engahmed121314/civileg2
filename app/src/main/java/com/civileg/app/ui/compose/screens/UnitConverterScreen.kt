package com.civileg.app.ui.compose.screens

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
import com.civileg.app.utils.UnitConverter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitConverterScreen(
    onNavigateBack: () -> Unit = {}
) {
    var selectedCategory by remember { mutableStateOf<UnitConverter.UnitCategory>(UnitConverter.UnitCategory.Length) }
    var inputValue by remember { mutableStateOf("1.0") }
    var fromUnit by remember { mutableStateOf("m") }
    var toUnit by remember { mutableStateOf("cm") }
    
    val units = remember(selectedCategory) {
        when(selectedCategory) {
            is UnitConverter.UnitCategory.Length -> listOf("m", "cm", "mm", "km", "ft", "in", "yd", "mi")
            is UnitConverter.UnitCategory.Area -> listOf("m²", "cm²", "mm²", "ft²", "in²", "yd²", "acre")
            is UnitConverter.UnitCategory.Volume -> listOf("m³", "cm³", "L", "ft³", "gal")
            is UnitConverter.UnitCategory.Weight -> listOf("kg", "g", "ton", "lb", "oz")
            is UnitConverter.UnitCategory.Force -> listOf("N", "kN", "MN", "kgf", "tonf", "lbf", "kip")
            is UnitConverter.UnitCategory.Pressure -> listOf("Pa", "kPa", "MPa", "bar", "psi", "ksf", "tsf")
            is UnitConverter.UnitCategory.Moment -> listOf("N.m", "kN.m", "ton.m", "kgf.m", "lb.ft", "kip.ft")
        }
    }

    LaunchedEffect(selectedCategory) {
        fromUnit = units.first()
        toUnit = if (units.size > 1) units[1] else units.first()
    }

    val result = remember(inputValue, fromUnit, toUnit, selectedCategory) {
        val value = inputValue.toDoubleOrNull() ?: 0.0
        UnitConverter.convert(value, fromUnit, toUnit, selectedCategory)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("محول الوحدات الهندسية", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category Selector
            ScrollableTabRow(
                selectedTabIndex = getCategoryIndex(selectedCategory),
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                CategoryTab("طول", selectedCategory is UnitConverter.UnitCategory.Length) { selectedCategory = UnitConverter.UnitCategory.Length }
                CategoryTab("مساحة", selectedCategory is UnitConverter.UnitCategory.Area) { selectedCategory = UnitConverter.UnitCategory.Area }
                CategoryTab("حجم", selectedCategory is UnitConverter.UnitCategory.Volume) { selectedCategory = UnitConverter.UnitCategory.Volume }
                CategoryTab("وزن", selectedCategory is UnitConverter.UnitCategory.Weight) { selectedCategory = UnitConverter.UnitCategory.Weight }
                CategoryTab("قوة", selectedCategory is UnitConverter.UnitCategory.Force) { selectedCategory = UnitConverter.UnitCategory.Force }
                CategoryTab("ضغط", selectedCategory is UnitConverter.UnitCategory.Pressure) { selectedCategory = UnitConverter.UnitCategory.Pressure }
                CategoryTab("عزم", selectedCategory is UnitConverter.UnitCategory.Moment) { selectedCategory = UnitConverter.UnitCategory.Moment }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // From Input
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = inputValue,
                            onValueChange = { inputValue = it },
                            label = { Text("القيمة") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        UnitDropdown(fromUnit, units) { fromUnit = it }
                    }

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        IconButton(onClick = {
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
                            value = "%.4f".format(result),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("النتيجة") },
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
                    Text(
                        "1 $fromUnit = ${"%.4f".format(UnitConverter.convert(1.0, fromUnit, toUnit, selectedCategory))} $toUnit",
                        style = MaterialTheme.typography.bodyMedium
                    )
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
        text = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
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
                    text = { Text(unit) },
                    onClick = {
                        onSelect(unit)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun getCategoryIndex(category: UnitConverter.UnitCategory): Int = when(category) {
    is UnitConverter.UnitCategory.Length -> 0
    is UnitConverter.UnitCategory.Area -> 1
    is UnitConverter.UnitCategory.Volume -> 2
    is UnitConverter.UnitCategory.Weight -> 3
    is UnitConverter.UnitCategory.Force -> 4
    is UnitConverter.UnitCategory.Pressure -> 5
    is UnitConverter.UnitCategory.Moment -> 6
}
