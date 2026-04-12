package com.civilengineer.assistant.ui.screens.stairs

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

@Composable
fun StairsDesignScreen(
    navController: NavController,
    stairsType: StairsType
) {
    var selectedCode by remember { mutableStateOf(DesignCode.EGYPTIAN) }
    var selectedConcreteGrade by remember { mutableStateOf(ConcreteGrade.C25) }
    var selectedSteelGrade by remember { mutableStateOf(SteelGrade.ST_360) }

    var floorHeightText by remember { mutableStateOf("") }
    var stairWidthText by remember { mutableStateOf("") }
    var riserText by remember { mutableStateOf("160") }
    var treadText by remember { mutableStateOf("280") }
    var liveLoadText by remember { mutableStateOf("4.0") }

    var showResults by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<EngineeringCalculations.StairsDesignResult?>(null) }

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = stairsType.displayName,
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

            CollapsibleSection(title = "أبعاد السلم", icon = Icons.Default.Stairs, initiallyExpanded = true) {
                EngineeringInputField(label = "ارتفاع الدور", value = floorHeightText,
                    onValueChange = { floorHeightText = it }, unit = "m", hint = "3.0", icon = Icons.Default.Height)
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(label = "عرض السلم", value = stairWidthText,
                    onValueChange = { stairWidthText = it }, unit = "m", hint = "1.2", icon = Icons.Default.SpaceBar)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    EngineeringInputField(label = "ارتفاع القائمة R", value = riserText,
                        onValueChange = { riserText = it }, unit = "mm", hint = "160", modifier = Modifier.weight(1f))
                    EngineeringInputField(label = "عرض النائمة T", value = treadText,
                        onValueChange = { treadText = it }, unit = "mm", hint = "280", modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(label = "حمل حي", value = liveLoadText,
                    onValueChange = { liveLoadText = it }, unit = "kN/m²", hint = "4.0", icon = Icons.Default.People)
            }

            CalculateButton(
                onClick = {
                    result = EngineeringCalculations.stairsDesign(
                        floorHeight = floorHeightText.toDoubleOrNull() ?: 3.0,
                        stairWidth = stairWidthText.toDoubleOrNull() ?: 1.2,
                        riserHeight = riserText.toDoubleOrNull() ?: 160.0,
                        treadWidth = treadText.toDoubleOrNull() ?: 280.0,
                        liveLoad = liveLoadText.toDoubleOrNull() ?: 4.0,
                        fcu = selectedConcreteGrade.fcu,
                        fy = selectedSteelGrade.fy,
                        code = selectedCode
                    )
                    showResults = true
                },
                text = "حساب تصميم السلم"
            )

            if (showResults && result != null) {
                val r = result!!
                ResultCard(title = "نتائج تصميم السلم", isSafe = r.isSafe) {
                    ResultRow("عدد الدرجات", "${r.numberOfRisers}", "درجة")
                    ResultRow("ارتفاع القائمة الفعلي", String.format("%.1f", r.actualRiser), "mm")
                    ResultRow("فحص الراحة (2R+T)", String.format("%.0f", r.comfortCheck), "mm",
                        isHighlighted = true, icon = if (r.comfortCheck in 580.0..640.0) Icons.Default.CheckCircle else Icons.Default.Warning)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ResultRow("سمك البلاطة", String.format("%.0f", r.thickness), "mm", isHighlighted = true)
                    ResultRow("الطول المائل", String.format("%.2f", r.inclinedLength), "m")
                    ResultRow("البحر الفعال", String.format("%.2f", r.effectiveSpan), "m")
                    ResultRow("الحمل التصميمي", String.format("%.2f", r.ultimateLoad), "kN/m²")
                    ResultRow("العزم Mu", String.format("%.2f", r.moment), "kN.m")
                    ResultRow("قوة القص Vu", String.format("%.2f", r.shear), "kN")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ResultRow("تسليح رئيسي As", String.format("%.0f", r.mainAs), "mm²/m", isHighlighted = true)
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
