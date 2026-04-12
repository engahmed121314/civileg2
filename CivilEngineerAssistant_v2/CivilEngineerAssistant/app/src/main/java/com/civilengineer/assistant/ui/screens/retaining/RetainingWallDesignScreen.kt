package com.civilengineer.assistant.ui.screens.retaining

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
fun RetainingWallDesignScreen(
    navController: NavController,
    wallType: RetainingWallType
) {
    var selectedCode by remember { mutableStateOf(DesignCode.EGYPTIAN) }
    var selectedConcreteGrade by remember { mutableStateOf(ConcreteGrade.C25) }
    var selectedSteelGrade by remember { mutableStateOf(SteelGrade.ST_360) }
    var selectedCurrency by remember { mutableStateOf(Currency.EGP) }

    var heightText by remember { mutableStateOf("") }
    var soilDensityText by remember { mutableStateOf("18") }
    var soilAngleText by remember { mutableStateOf("30") }
    var surchargeText by remember { mutableStateOf("10") }

    var showResults by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<EngineeringCalculations.RetainingWallResult?>(null) }

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = wallType.displayName,
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
            EngineeringDropdown(
                label = "الكود",
                items = DesignCode.entries,
                selectedItem = selectedCode,
                onItemSelected = { selectedCode = it },
                itemLabel = { it.displayName },
                icon = Icons.Default.MenuBook
            )

            CollapsibleSection(title = "خصائص المواد", icon = Icons.Default.Science, initiallyExpanded = true) {
                EngineeringDropdown(label = "رتبة الخرسانة", items = ConcreteGrade.entries,
                    selectedItem = selectedConcreteGrade, onItemSelected = { selectedConcreteGrade = it },
                    itemLabel = { it.displayName })
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringDropdown(label = "رتبة الحديد", items = SteelGrade.entries,
                    selectedItem = selectedSteelGrade, onItemSelected = { selectedSteelGrade = it },
                    itemLabel = { it.displayName })
            }

            CollapsibleSection(title = "أبعاد الحائط والتربة", icon = Icons.Default.Terrain, initiallyExpanded = true) {
                EngineeringInputField(label = "ارتفاع الحائط H", value = heightText, onValueChange = { heightText = it },
                    unit = "m", hint = "4.0", icon = Icons.Default.Height)
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(label = "كثافة التربة γ", value = soilDensityText, onValueChange = { soilDensityText = it },
                    unit = "kN/m³", hint = "18", icon = Icons.Default.Terrain)
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(label = "زاوية الاحتكاك φ", value = soilAngleText, onValueChange = { soilAngleText = it },
                    unit = "°", hint = "30", icon = Icons.Default.RotateRight)
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(label = "حمل إضافي (سرشارج)", value = surchargeText, onValueChange = { surchargeText = it },
                    unit = "kN/m²", hint = "10", icon = Icons.Default.ArrowDownward)
            }

            CalculateButton(
                onClick = {
                    result = EngineeringCalculations.retainingWallDesign(
                        wallHeight = heightText.toDoubleOrNull() ?: 4.0,
                        soilDensity = soilDensityText.toDoubleOrNull() ?: 18.0,
                        soilAngle = soilAngleText.toDoubleOrNull() ?: 30.0,
                        surcharge = surchargeText.toDoubleOrNull() ?: 10.0,
                        fcu = selectedConcreteGrade.fcu,
                        fy = selectedSteelGrade.fy,
                        code = selectedCode,
                        wallType = wallType
                    )
                    showResults = true
                },
                text = "حساب تصميم حائط السند"
            )

            if (showResults && result != null) {
                val r = result!!

                ResultCard(title = "نتائج تصميم حائط السند", isSafe = r.isSafe) {
                    ResultRow("عرض القاعدة B", String.format("%.2f", r.baseWidth), "m", isHighlighted = true)
                    ResultRow("طول الإصبع (Toe)", String.format("%.2f", r.toeLength), "m")
                    ResultRow("طول الكعب (Heel)", String.format("%.2f", r.heelLength), "m")
                    ResultRow("سمك الجذع (قاعدة)", String.format("%.2f", r.stemThicknessBase), "m")
                    ResultRow("سمك الجذع (أعلى)", String.format("%.2f", r.stemThicknessTop), "m")
                    ResultRow("سمك القاعدة", String.format("%.2f", r.baseThickness), "m")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ResultRow("ضغط التربة الفعال Pa", String.format("%.1f", r.activePressure), "kN/m")
                    ResultRow("معامل أمان الانقلاب", String.format("%.2f", r.overturningFOS), "", isHighlighted = true,
                        icon = if (r.overturningFOS >= 2.0) Icons.Default.CheckCircle else Icons.Default.Warning)
                    ResultRow("معامل أمان الانزلاق", String.format("%.2f", r.slidingFOS), "", isHighlighted = true,
                        icon = if (r.slidingFOS >= 1.5) Icons.Default.CheckCircle else Icons.Default.Warning)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ResultRow("عزم الجذع", String.format("%.1f", r.stemMoment), "kN.m/m")
                    ResultRow("تسليح الجذع As", String.format("%.0f", r.stemAs), "mm²/m")
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
