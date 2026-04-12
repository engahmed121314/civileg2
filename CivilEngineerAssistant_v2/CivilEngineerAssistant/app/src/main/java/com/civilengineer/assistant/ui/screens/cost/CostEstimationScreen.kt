package com.civilengineer.assistant.ui.screens.cost

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.civilengineer.assistant.models.*
import com.civilengineer.assistant.ui.components.*
import com.civilengineer.assistant.ui.theme.*
import com.civilengineer.assistant.utils.EngineeringConstants

@Composable
fun CostEstimationScreen(navController: NavController) {
    var selectedCurrency by remember { mutableStateOf(Currency.EGP) }

    // كميات المشروع
    var concreteVolText by remember { mutableStateOf("") }
    var steelWeightText by remember { mutableStateOf("") }
    var formworkAreaText by remember { mutableStateOf("") }
    var excavationVolText by remember { mutableStateOf("") }
    var backfillVolText by remember { mutableStateOf("") }

    // أسعار مخصصة
    var useCustomPrices by remember { mutableStateOf(false) }
    var concretePriceText by remember { mutableStateOf("") }
    var steelPriceText by remember { mutableStateOf("") }
    var formworkPriceText by remember { mutableStateOf("") }
    var laborPriceText by remember { mutableStateOf("") }
    var excavationPriceText by remember { mutableStateOf("") }

    var showResults by remember { mutableStateOf(false) }

    // تحديث الأسعار عند تغيير العملة
    LaunchedEffect(selectedCurrency) {
        val prices = EngineeringConstants.getDefaultPrices(selectedCurrency.name)
        concretePriceText = prices.concretePerM3.toString()
        steelPriceText = prices.steelPerTon.toString()
        formworkPriceText = prices.formworkPerM2.toString()
        laborPriceText = prices.laborPerM3.toString()
        excavationPriceText = "50"
    }

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = "حساب التكلفة",
                subtitle = "Cost Estimation",
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
            EngineeringDropdown(
                label = "العملة",
                items = Currency.entries,
                selectedItem = selectedCurrency,
                onItemSelected = { selectedCurrency = it },
                itemLabel = { "${it.displayName} (${it.symbol})" },
                icon = Icons.Default.Payments
            )

            CollapsibleSection(title = "كميات المشروع", icon = Icons.Default.Assessment, initiallyExpanded = true) {
                EngineeringInputField(label = "حجم الخرسانة", value = concreteVolText,
                    onValueChange = { concreteVolText = it }, unit = "م³", hint = "100", icon = Icons.Default.Layers)
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(label = "وزن الحديد", value = steelWeightText,
                    onValueChange = { steelWeightText = it }, unit = "طن", hint = "10", icon = Icons.Default.LinearScale)
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(label = "مساحة الشدات", value = formworkAreaText,
                    onValueChange = { formworkAreaText = it }, unit = "م²", hint = "500", icon = Icons.Default.GridOn)
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(label = "حجم الحفر", value = excavationVolText,
                    onValueChange = { excavationVolText = it }, unit = "م³", hint = "50", icon = Icons.Default.Terrain)
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(label = "حجم الردم", value = backfillVolText,
                    onValueChange = { backfillVolText = it }, unit = "م³", hint = "20")
            }

            // أسعار مخصصة
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("تعديل الأسعار الافتراضية", fontWeight = FontWeight.Medium)
                Switch(checked = useCustomPrices, onCheckedChange = { useCustomPrices = it })
            }

            if (useCustomPrices) {
                CollapsibleSection(title = "أسعار الوحدة", icon = Icons.Default.Edit, initiallyExpanded = true) {
                    EngineeringInputField(label = "سعر م³ خرسانة", value = concretePriceText,
                        onValueChange = { concretePriceText = it }, unit = selectedCurrency.symbol)
                    Spacer(modifier = Modifier.height(8.dp))
                    EngineeringInputField(label = "سعر طن حديد", value = steelPriceText,
                        onValueChange = { steelPriceText = it }, unit = selectedCurrency.symbol)
                    Spacer(modifier = Modifier.height(8.dp))
                    EngineeringInputField(label = "سعر م² شدات", value = formworkPriceText,
                        onValueChange = { formworkPriceText = it }, unit = selectedCurrency.symbol)
                    Spacer(modifier = Modifier.height(8.dp))
                    EngineeringInputField(label = "سعر م³ عمالة", value = laborPriceText,
                        onValueChange = { laborPriceText = it }, unit = selectedCurrency.symbol)
                    Spacer(modifier = Modifier.height(8.dp))
                    EngineeringInputField(label = "سعر م³ حفر", value = excavationPriceText,
                        onValueChange = { excavationPriceText = it }, unit = selectedCurrency.symbol)
                }
            }

            CalculateButton(
                onClick = { showResults = true },
                text = "حساب التكلفة الإجمالية"
            )

            if (showResults) {
                val conVol = concreteVolText.toDoubleOrNull() ?: 0.0
                val steelW = steelWeightText.toDoubleOrNull() ?: 0.0
                val formA = formworkAreaText.toDoubleOrNull() ?: 0.0
                val excVol = excavationVolText.toDoubleOrNull() ?: 0.0
                val backVol = backfillVolText.toDoubleOrNull() ?: 0.0

                val conPrice = concretePriceText.toDoubleOrNull() ?: 0.0
                val steelPrice = steelPriceText.toDoubleOrNull() ?: 0.0
                val formPrice = formworkPriceText.toDoubleOrNull() ?: 0.0
                val laborPrice = laborPriceText.toDoubleOrNull() ?: 0.0
                val excPrice = excavationPriceText.toDoubleOrNull() ?: 0.0

                val concreteCost = conVol * conPrice
                val steelCost = steelW * steelPrice
                val formworkCost = formA * formPrice
                val laborCost = conVol * laborPrice
                val excavationCost = excVol * excPrice
                val backfillCost = backVol * excPrice * 0.5

                val subtotal = concreteCost + steelCost + formworkCost + laborCost + excavationCost + backfillCost
                val overhead = subtotal * 0.15  // 15% مصاريف إدارية
                val profit = subtotal * 0.10    // 10% ربح
                val total = subtotal + overhead + profit

                ResultCard(title = "تقدير التكلفة") {
                    ResultRow("خرسانة (${String.format("%.1f", conVol)} م³)", "${String.format("%,.0f", concreteCost)} ${selectedCurrency.symbol}")
                    ResultRow("حديد تسليح (${String.format("%.2f", steelW)} طن)", "${String.format("%,.0f", steelCost)} ${selectedCurrency.symbol}")
                    ResultRow("شدات (${String.format("%.0f", formA)} م²)", "${String.format("%,.0f", formworkCost)} ${selectedCurrency.symbol}")
                    ResultRow("عمالة خرسانة", "${String.format("%,.0f", laborCost)} ${selectedCurrency.symbol}")
                    ResultRow("حفر (${String.format("%.1f", excVol)} م³)", "${String.format("%,.0f", excavationCost)} ${selectedCurrency.symbol}")
                    ResultRow("ردم (${String.format("%.1f", backVol)} م³)", "${String.format("%,.0f", backfillCost)} ${selectedCurrency.symbol}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ResultRow("المجموع الفرعي", "${String.format("%,.0f", subtotal)} ${selectedCurrency.symbol}")
                    ResultRow("مصاريف إدارية (15%)", "${String.format("%,.0f", overhead)} ${selectedCurrency.symbol}")
                    ResultRow("ربح المقاول (10%)", "${String.format("%,.0f", profit)} ${selectedCurrency.symbol}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ResultRow("التكلفة الإجمالية", "${String.format("%,.0f", total)} ${selectedCurrency.symbol}",
                        isHighlighted = true, icon = Icons.Default.Payments)
                    ResultRow("سعر م³ خرسانة شامل", "${String.format("%,.0f", if (conVol > 0) total / conVol else 0.0)} ${selectedCurrency.symbol}/م³",
                        icon = Icons.Default.Analytics)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
