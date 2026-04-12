package com.civilengineer.assistant.ui.screens.tanks

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.civilengineer.assistant.models.*
import com.civilengineer.assistant.ui.components.*
import com.civilengineer.assistant.utils.EngineeringCalculations
import com.civilengineer.assistant.utils.EngineeringConstants

@Composable
fun TankDesignScreen(
    navController: NavController,
    tankType: TankType
) {
    var selectedCode by remember { mutableStateOf(DesignCode.EGYPTIAN) }
    var selectedConcreteGrade by remember { mutableStateOf(ConcreteGrade.C30) }
    var selectedSteelGrade by remember { mutableStateOf(SteelGrade.ST_360) }
    var selectedCurrency by remember { mutableStateOf(Currency.EGP) }

    var lengthText by remember { mutableStateOf("") }
    var widthText by remember { mutableStateOf("") }
    var waterHeightText by remember { mutableStateOf("") }
    var freeBoardText by remember { mutableStateOf("0.3") }

    var showResults by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<EngineeringCalculations.TankDesignResult?>(null) }

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = tankType.displayName,
                subtitle = selectedCode.shortName,
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
            EngineeringDropdown(label = "الكود", items = DesignCode.entries,
                selectedItem = selectedCode, onItemSelected = { selectedCode = it },
                itemLabel = { it.displayName }, icon = Icons.Default.MenuBook)

            CollapsibleSection(title = "خصائص المواد", icon = Icons.Default.Science, initiallyExpanded = true) {
                EngineeringDropdown(label = "رتبة الخرسانة", items = ConcreteGrade.entries,
                    selectedItem = selectedConcreteGrade, onItemSelected = { selectedConcreteGrade = it },
                    itemLabel = { it.displayName })
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringDropdown(label = "رتبة الحديد", items = SteelGrade.entries,
                    selectedItem = selectedSteelGrade, onItemSelected = { selectedSteelGrade = it },
                    itemLabel = { it.displayName })
            }

            CollapsibleSection(title = "أبعاد الخزان", icon = Icons.Default.Water, initiallyExpanded = true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    EngineeringInputField(label = "الطول", value = lengthText, onValueChange = { lengthText = it },
                        unit = "m", hint = "5.0", modifier = Modifier.weight(1f))
                    EngineeringInputField(label = "العرض", value = widthText, onValueChange = { widthText = it },
                        unit = "m", hint = "4.0", modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    EngineeringInputField(label = "ارتفاع الماء", value = waterHeightText,
                        onValueChange = { waterHeightText = it }, unit = "m", hint = "3.0", modifier = Modifier.weight(1f))
                    EngineeringInputField(label = "الارتفاع الحر", value = freeBoardText,
                        onValueChange = { freeBoardText = it }, unit = "m", hint = "0.3", modifier = Modifier.weight(1f))
                }
            }

            CalculateButton(
                onClick = {
                    result = EngineeringCalculations.rectangularTankDesign(
                        length = lengthText.toDoubleOrNull() ?: 5.0,
                        width = widthText.toDoubleOrNull() ?: 4.0,
                        height = waterHeightText.toDoubleOrNull() ?: 3.0,
                        freeBoard = freeBoardText.toDoubleOrNull() ?: 0.3,
                        fcu = selectedConcreteGrade.fcu,
                        fy = selectedSteelGrade.fy,
                        code = selectedCode,
                        isUnderground = tankType == TankType.UNDERGROUND_RECTANGULAR || tankType == TankType.UNDERGROUND_CIRCULAR
                    )
                    showResults = true
                },
                text = "حساب تصميم الخزان"
            )

            if (showResults && result != null) {
                val r = result!!
                ResultCard(title = "نتائج تصميم الخزان", isSafe = r.isSafe) {
                    ResultRow("سمك الجدران", String.format("%.0f", r.wallThickness), "mm", isHighlighted = true)
                    ResultRow("سمك القاعدة", String.format("%.0f", r.baseThickness), "mm")
                    ResultRow("ضغط الماء عند القاعدة", String.format("%.1f", r.waterPressure), "kN/m²")
                    ResultRow("عزم الجدار", String.format("%.2f", r.wallMoment), "kN.m/m")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ResultRow("تسليح الجدار As", String.format("%.0f", r.wallAs), "mm²/m", isHighlighted = true)
                    ResultRow("تسليح القاعدة As", String.format("%.0f", r.baseAs), "mm²/m")
                    ResultRow("ملاحظة", "نسبة تسليح لا تقل عن 0.3% لمنع التسرب")
                }

                Spacer(modifier = Modifier.height(8.dp))
                CollapsibleSection(title = "خطوات الحل", icon = Icons.Default.Calculate) {
                    r.equations.forEach { step ->
                        EquationStepCard(step)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
