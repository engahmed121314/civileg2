package com.civilengineer.assistant.ui.screens.converter

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.civilengineer.assistant.ui.components.*
import com.civilengineer.assistant.ui.theme.*

data class UnitCategory(
    val name: String,
    val units: List<UnitItem>
)

data class UnitItem(
    val name: String,
    val symbol: String,
    val toBase: Double  // conversion factor TO base unit
)

val unitCategories = listOf(
    UnitCategory("الطول", listOf(
        UnitItem("ملليمتر", "mm", 1.0),
        UnitItem("سنتيمتر", "cm", 10.0),
        UnitItem("متر", "m", 1000.0),
        UnitItem("كيلومتر", "km", 1000000.0),
        UnitItem("بوصة", "in", 25.4),
        UnitItem("قدم", "ft", 304.8),
        UnitItem("ياردة", "yd", 914.4),
    )),
    UnitCategory("المساحة", listOf(
        UnitItem("ملليمتر²", "mm²", 1.0),
        UnitItem("سنتيمتر²", "cm²", 100.0),
        UnitItem("متر²", "m²", 1000000.0),
        UnitItem("بوصة²", "in²", 645.16),
        UnitItem("قدم²", "ft²", 92903.04),
    )),
    UnitCategory("الحجم", listOf(
        UnitItem("سنتيمتر³", "cm³", 1.0),
        UnitItem("متر³", "m³", 1000000.0),
        UnitItem("لتر", "L", 1000.0),
        UnitItem("قدم³", "ft³", 28316.85),
        UnitItem("جالون", "gal", 3785.41),
    )),
    UnitCategory("القوة", listOf(
        UnitItem("نيوتن", "N", 1.0),
        UnitItem("كيلونيوتن", "kN", 1000.0),
        UnitItem("كجم.قوة", "kgf", 9.80665),
        UnitItem("طن.قوة", "tf", 9806.65),
        UnitItem("رطل.قوة", "lbf", 4.44822),
        UnitItem("كيب", "kip", 4448.22),
    )),
    UnitCategory("الإجهاد / الضغط", listOf(
        UnitItem("باسكال", "Pa", 1.0),
        UnitItem("كيلو باسكال", "kPa", 1000.0),
        UnitItem("ميجا باسكال (N/mm²)", "MPa", 1000000.0),
        UnitItem("كجم/سم²", "kgf/cm²", 98066.5),
        UnitItem("طن/م²", "tf/m²", 9806.65),
        UnitItem("psi", "psi", 6894.76),
        UnitItem("ksi", "ksi", 6894760.0),
        UnitItem("بار", "bar", 100000.0),
    )),
    UnitCategory("العزم", listOf(
        UnitItem("نيوتن.متر", "N.m", 1.0),
        UnitItem("كيلونيوتن.متر", "kN.m", 1000.0),
        UnitItem("طن.متر", "t.m", 9806.65),
        UnitItem("كجم.سم", "kgf.cm", 0.0980665),
        UnitItem("رطل.قدم", "lb.ft", 1.35582),
        UnitItem("كيب.قدم", "kip.ft", 1355.82),
    )),
    UnitCategory("الوزن / الكتلة", listOf(
        UnitItem("جرام", "g", 1.0),
        UnitItem("كيلوجرام", "kg", 1000.0),
        UnitItem("طن متري", "t", 1000000.0),
        UnitItem("رطل", "lb", 453.592),
        UnitItem("أوقية", "oz", 28.3495),
    )),
    UnitCategory("درجة الحرارة", listOf(
        UnitItem("مئوي", "°C", 1.0),
        UnitItem("فهرنهايت", "°F", 1.0),
        UnitItem("كلفن", "K", 1.0),
    )),
)

@Composable
fun UnitConverterScreen(navController: NavController) {
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var fromUnitIndex by remember { mutableIntStateOf(0) }
    var toUnitIndex by remember { mutableIntStateOf(1) }
    var inputValue by remember { mutableStateOf("") }

    val category = unitCategories[selectedCategoryIndex]

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = "محول الوحدات",
                subtitle = "Unit Converter",
                onBackClick = { navController.popBackStack() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // اختيار الفئة
            EngineeringDropdown(
                label = "نوع الوحدة",
                items = unitCategories.mapIndexed { i, c -> i to c.name },
                selectedItem = selectedCategoryIndex to category.name,
                onItemSelected = {
                    selectedCategoryIndex = it.first
                    fromUnitIndex = 0
                    toUnitIndex = 1
                },
                itemLabel = { it.second },
                icon = Icons.Default.Category
            )

            // إدخال القيمة
            EngineeringInputField(
                label = "القيمة",
                value = inputValue,
                onValueChange = { inputValue = it },
                unit = category.units.getOrNull(fromUnitIndex)?.symbol ?: "",
                hint = "أدخل القيمة",
                icon = Icons.Default.Edit
            )

            // من
            EngineeringDropdown(
                label = "من",
                items = category.units.mapIndexed { i, u -> i to u },
                selectedItem = fromUnitIndex to (category.units.getOrNull(fromUnitIndex) ?: category.units[0]),
                onItemSelected = { fromUnitIndex = it.first },
                itemLabel = { "${it.second.name} (${it.second.symbol})" }
            )

            // زر التبديل
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = {
                    val temp = fromUnitIndex
                    fromUnitIndex = toUnitIndex
                    toUnitIndex = temp
                }) {
                    Icon(Icons.Default.SwapVert, contentDescription = "تبديل", tint = PrimaryBlue,
                        modifier = Modifier.size(32.dp))
                }
            }

            // إلى
            EngineeringDropdown(
                label = "إلى",
                items = category.units.mapIndexed { i, u -> i to u },
                selectedItem = toUnitIndex to (category.units.getOrNull(toUnitIndex) ?: category.units[0]),
                onItemSelected = { toUnitIndex = it.first },
                itemLabel = { "${it.second.name} (${it.second.symbol})" }
            )

            // النتيجة
            val input = inputValue.toDoubleOrNull()
            if (input != null) {
                val fromUnit = category.units.getOrNull(fromUnitIndex)
                val toUnit = category.units.getOrNull(toUnitIndex)

                if (fromUnit != null && toUnit != null) {
                    val result: Double = if (category.name == "درجة الحرارة") {
                        convertTemperature(input, fromUnit.symbol, toUnit.symbol)
                    } else {
                        input * fromUnit.toBase / toUnit.toBase
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PrimaryBlueVeryLight.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("النتيجة", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${formatResult(result)} ${toUnit.symbol}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlueDark
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$inputValue ${fromUnit.symbol} = ${formatResult(result)} ${toUnit.symbol}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun convertTemperature(value: Double, from: String, to: String): Double {
    val celsius = when (from) {
        "°C" -> value
        "°F" -> (value - 32) * 5.0 / 9.0
        "K" -> value - 273.15
        else -> value
    }
    return when (to) {
        "°C" -> celsius
        "°F" -> celsius * 9.0 / 5.0 + 32
        "K" -> celsius + 273.15
        else -> celsius
    }
}

private fun formatResult(value: Double): String {
    return when {
        value == 0.0 -> "0"
        kotlin.math.abs(value) >= 1e6 -> String.format("%.4e", value)
        kotlin.math.abs(value) < 0.001 -> String.format("%.6e", value)
        kotlin.math.abs(value) < 1 -> String.format("%.6f", value)
        kotlin.math.abs(value) < 100 -> String.format("%.4f", value)
        else -> String.format("%.2f", value)
    }
}
