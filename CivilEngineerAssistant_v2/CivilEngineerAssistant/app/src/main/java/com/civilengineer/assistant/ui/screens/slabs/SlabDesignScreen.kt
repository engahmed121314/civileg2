package com.civilengineer.assistant.ui.screens.slabs

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
fun SlabDesignScreen(
    navController: NavController,
    slabType: SlabType
) {
    var selectedCode by remember { mutableStateOf(DesignCode.EGYPTIAN) }
    var selectedConcreteGrade by remember { mutableStateOf(ConcreteGrade.C25) }
    var selectedSteelGrade by remember { mutableStateOf(SteelGrade.ST_360) }
    var selectedCurrency by remember { mutableStateOf(Currency.EGP) }

    var spanXText by remember { mutableStateOf("") }
    var spanYText by remember { mutableStateOf("") }
    var deadLoadText by remember { mutableStateOf("") }
    var liveLoadText by remember { mutableStateOf("") }
    var selectedUsage by remember { mutableStateOf("سكني") }

    var showResults by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<EngineeringCalculations.SlabDesignResult?>(null) }

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = slabType.displayName,
                subtitle = selectedCode.shortName,
                onBackClick = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { /* PDF */ }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF")
                    }
                }
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
            // الكود
            EngineeringDropdown(
                label = "الكود المستخدم",
                items = DesignCode.entries,
                selectedItem = selectedCode,
                onItemSelected = { selectedCode = it },
                itemLabel = { it.displayName },
                icon = Icons.Default.MenuBook
            )

            // خصائص المواد
            CollapsibleSection(
                title = "خصائص المواد",
                icon = Icons.Default.Science,
                initiallyExpanded = true
            ) {
                EngineeringDropdown(
                    label = "رتبة الخرسانة fcu",
                    items = ConcreteGrade.entries,
                    selectedItem = selectedConcreteGrade,
                    onItemSelected = { selectedConcreteGrade = it },
                    itemLabel = { it.displayName }
                )
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringDropdown(
                    label = "رتبة حديد التسليح fy",
                    items = SteelGrade.entries,
                    selectedItem = selectedSteelGrade,
                    onItemSelected = { selectedSteelGrade = it },
                    itemLabel = { it.displayName }
                )
            }

            // الأبعاد
            CollapsibleSection(
                title = "أبعاد البلاطة",
                icon = Icons.Default.Straighten,
                initiallyExpanded = true
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EngineeringInputField(
                        label = "البحر Lx (قصير)",
                        value = spanXText,
                        onValueChange = { spanXText = it },
                        unit = "m",
                        hint = "4.0",
                        modifier = Modifier.weight(1f)
                    )
                    EngineeringInputField(
                        label = "البحر Ly (طويل)",
                        value = spanYText,
                        onValueChange = { spanYText = it },
                        unit = "m",
                        hint = "5.0",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // الأحمال
            CollapsibleSection(
                title = "الأحمال",
                icon = Icons.Default.FitnessCenter,
                initiallyExpanded = true
            ) {
                EngineeringDropdown(
                    label = "نوع الاستخدام",
                    items = EngineeringConstants.LIVE_LOADS.keys.toList(),
                    selectedItem = selectedUsage,
                    onItemSelected = {
                        selectedUsage = it
                        liveLoadText = EngineeringConstants.LIVE_LOADS[it]?.toString() ?: ""
                    },
                    itemLabel = { "$it (${EngineeringConstants.LIVE_LOADS[it]} kN/m²)" },
                    icon = Icons.Default.Home
                )
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(
                    label = "حمل ميت إضافي (تشطيبات وحوائط)",
                    value = deadLoadText,
                    onValueChange = { deadLoadText = it },
                    unit = "kN/m²",
                    hint = "2.0 (تشطيبات + حوائط)",
                    icon = Icons.Default.Layers
                )
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(
                    label = "حمل حي",
                    value = liveLoadText,
                    onValueChange = { liveLoadText = it },
                    unit = "kN/m²",
                    hint = "2.0",
                    icon = Icons.Default.People
                )
            }

            // العملة
            EngineeringDropdown(
                label = "العملة",
                items = Currency.entries,
                selectedItem = selectedCurrency,
                onItemSelected = { selectedCurrency = it },
                itemLabel = { "${it.displayName} (${it.symbol})" },
                icon = Icons.Default.Payments
            )

            // زر الحساب
            CalculateButton(
                onClick = {
                    val spanX = spanXText.toDoubleOrNull() ?: 4.0
                    val spanY = spanYText.toDoubleOrNull() ?: 5.0
                    val dl = deadLoadText.toDoubleOrNull() ?: 2.0
                    val ll = liveLoadText.toDoubleOrNull() ?: EngineeringConstants.LIVE_LOADS[selectedUsage] ?: 2.0

                    result = EngineeringCalculations.solidSlabDesign(
                        spanX = spanX,
                        spanY = spanY,
                        deadLoad = dl,
                        liveLoad = ll,
                        fcu = selectedConcreteGrade.fcu,
                        fy = selectedSteelGrade.fy,
                        code = selectedCode,
                        slabType = slabType
                    )
                    showResults = true
                },
                text = "حساب تصميم البلاطة"
            )

            // النتائج
            if (showResults && result != null) {
                val r = result!!

                ResultCard(title = "نتائج تصميم البلاطة", isSafe = true) {
                    ResultRow("اتجاه البلاطة", if (r.isOneWay) "باتجاه واحد" else "باتجاهين")
                    ResultRow("نسبة الأبعاد", String.format("%.2f", r.spanRatio))
                    ResultRow("سمك البلاطة t", String.format("%.0f", r.thickness), "mm", isHighlighted = true)
                    ResultRow("العمق الفعال d", String.format("%.0f", r.effectiveDepth), "mm")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ResultRow("الحمل التصميمي wu", String.format("%.2f", r.ultimateLoad), "kN/m²")
                    ResultRow("عزم الاتجاه القصير", String.format("%.2f", r.momentShortSpan), "kN.m/m")
                    if (!r.isOneWay) {
                        ResultRow("عزم الاتجاه الطويل", String.format("%.2f", r.momentLongSpan), "kN.m/m")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ResultRow("تسليح اتجاه قصير", "φ${r.shortBarDiameter.toInt()}@${r.shortBarSpacing.toInt()}", "mm", isHighlighted = true)
                    ResultRow("As مطلوب (قصير)", String.format("%.0f", r.asShortRequired), "mm²/m")
                    ResultRow("As مقدم (قصير)", String.format("%.0f", r.asShortProvided), "mm²/m")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ResultRow("تسليح اتجاه طويل", "φ${r.longBarDiameter.toInt()}@${r.longBarSpacing.toInt()}", "mm", isHighlighted = true)
                    ResultRow("As مطلوب (طويل)", String.format("%.0f", r.asLongRequired), "mm²/m")
                    ResultRow("As مقدم (طويل)", String.format("%.0f", r.asLongProvided), "mm²/m")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // فحوصات الكود
                if (r.codeChecks.isNotEmpty()) {
                    CollapsibleSection(title = "فحوصات الكود", icon = Icons.Default.Checklist) {
                        r.codeChecks.forEach { check ->
                            CodeCheckCard(check)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // خطوات الحل
                CollapsibleSection(title = "خطوات الحل والمعادلات", icon = Icons.Default.Calculate) {
                    r.equations.forEach { step ->
                        EquationStepCard(step)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // الحصر
                val spanX = spanXText.toDoubleOrNull() ?: 4.0
                val spanY = spanYText.toDoubleOrNull() ?: 5.0
                val area = spanX * spanY
                val concreteVol = area * r.thickness / 1000.0
                val steelWeightShort = r.asShortProvided * spanX * 1000.0 / 1e6 * 7850 / 1000
                val steelWeightLong = r.asLongProvided * spanY * 1000.0 / 1e6 * 7850 / 1000
                val totalSteel = steelWeightShort + steelWeightLong
                val wasteConcrete = EngineeringConstants.WasteFactors.CONCRETE_SLABS
                val wasteSteel = EngineeringConstants.WasteFactors.STEEL_STRAIGHT

                QuantitySurveyCard(
                    result = QuantitySurveyResult(
                        concreteVolume = concreteVol,
                        steelWeight = totalSteel,
                        formworkArea = area,
                        wasteFactor = wasteConcrete,
                        steelWithWaste = totalSteel * (1 + wasteSteel),
                        concreteWithWaste = concreteVol * (1 + wasteConcrete),
                        rebarDetails = listOf(
                            RebarDetail("تسليح اتجاه قصير", r.shortBarDiameter, spanX, (spanY * 1000 / r.shortBarSpacing).toInt(),
                                steelWeightShort),
                            RebarDetail("تسليح اتجاه طويل", r.longBarDiameter, spanY, (spanX * 1000 / r.longBarSpacing).toInt(),
                                steelWeightLong)
                        )
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // التكلفة
                val prices = EngineeringConstants.getDefaultPrices(selectedCurrency.name)
                val concreteWithWaste = concreteVol * (1 + wasteConcrete)
                val steelWithWaste = totalSteel * (1 + wasteSteel)
                CostEstimationCard(
                    result = CostResult(
                        concreteCost = concreteWithWaste * prices.concretePerM3,
                        steelCost = steelWithWaste / 1000.0 * prices.steelPerTon,
                        formworkCost = area * prices.formworkPerM2,
                        laborCost = concreteWithWaste * prices.laborPerM3,
                        totalCost = concreteWithWaste * prices.concretePerM3 + steelWithWaste / 1000.0 * prices.steelPerTon + area * prices.formworkPerM2 + concreteWithWaste * prices.laborPerM3,
                        currency = selectedCurrency,
                        breakdown = listOf(
                            CostItem("خرسانة", concreteWithWaste, "م³", prices.concretePerM3, concreteWithWaste * prices.concretePerM3),
                            CostItem("حديد تسليح", steelWithWaste / 1000.0, "طن", prices.steelPerTon, steelWithWaste / 1000.0 * prices.steelPerTon),
                            CostItem("شدات", area, "م²", prices.formworkPerM2, area * prices.formworkPerM2),
                            CostItem("عمالة", concreteWithWaste, "م³", prices.laborPerM3, concreteWithWaste * prices.laborPerM3)
                        )
                    )
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
