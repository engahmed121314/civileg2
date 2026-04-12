package com.civilengineer.assistant.ui.screens.foundations

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
fun FoundationDesignScreen(
    navController: NavController,
    foundationType: FoundationType
) {
    var selectedCode by remember { mutableStateOf(DesignCode.EGYPTIAN) }
    var selectedConcreteGrade by remember { mutableStateOf(ConcreteGrade.C25) }
    var selectedSteelGrade by remember { mutableStateOf(SteelGrade.ST_360) }
    var selectedCurrency by remember { mutableStateOf(Currency.EGP) }
    var selectedSoilType by remember { mutableStateOf(SoilType.MEDIUM_SAND) }

    var puText by remember { mutableStateOf("") }
    var muText by remember { mutableStateOf("") }
    var colBText by remember { mutableStateOf("") }
    var colHText by remember { mutableStateOf("") }
    var soilBearingText by remember { mutableStateOf(selectedSoilType.bearingCapacity.toString()) }
    var depthText by remember { mutableStateOf("1.5") }

    var showResults by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<EngineeringCalculations.FootingDesignResult?>(null) }

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = foundationType.displayName,
                subtitle = selectedCode.shortName,
                onBackClick = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF") }
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
            EngineeringDropdown(
                label = "الكود المستخدم",
                items = DesignCode.entries,
                selectedItem = selectedCode,
                onItemSelected = { selectedCode = it },
                itemLabel = { it.displayName },
                icon = Icons.Default.MenuBook
            )

            CollapsibleSection(title = "خصائص المواد", icon = Icons.Default.Science, initiallyExpanded = true) {
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

            CollapsibleSection(title = "خصائص التربة", icon = Icons.Default.Terrain, initiallyExpanded = true) {
                EngineeringDropdown(
                    label = "نوع التربة",
                    items = SoilType.entries.filter { it != SoilType.FILL },
                    selectedItem = selectedSoilType,
                    onItemSelected = {
                        selectedSoilType = it
                        soilBearingText = it.bearingCapacity.toString()
                    },
                    itemLabel = { "${it.displayName} (${it.bearingCapacity} kN/m²)" },
                    icon = Icons.Default.Terrain
                )
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(
                    label = "قدرة تحمل التربة المسموحة",
                    value = soilBearingText,
                    onValueChange = { soilBearingText = it },
                    unit = "kN/m²",
                    hint = "150",
                    icon = Icons.Default.Compress
                )
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(
                    label = "عمق التأسيس",
                    value = depthText,
                    onValueChange = { depthText = it },
                    unit = "m",
                    hint = "1.5",
                    icon = Icons.Default.Height
                )
            }

            CollapsibleSection(title = "الأحمال", icon = Icons.Default.FitnessCenter, initiallyExpanded = true) {
                EngineeringInputField(
                    label = "الحمل المحوري التصميمي Pu",
                    value = puText,
                    onValueChange = { puText = it },
                    unit = "kN",
                    hint = "1000",
                    icon = Icons.Default.ArrowDownward
                )
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(
                    label = "العزم التصميمي Mu",
                    value = muText,
                    onValueChange = { muText = it },
                    unit = "kN.m",
                    hint = "0",
                    icon = Icons.Default.RotateRight
                )
            }

            CollapsibleSection(title = "أبعاد العمود", icon = Icons.Default.ViewColumn, initiallyExpanded = true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    EngineeringInputField(
                        label = "عرض العمود",
                        value = colBText,
                        onValueChange = { colBText = it },
                        unit = "mm",
                        hint = "300",
                        modifier = Modifier.weight(1f)
                    )
                    EngineeringInputField(
                        label = "ارتفاع العمود",
                        value = colHText,
                        onValueChange = { colHText = it },
                        unit = "mm",
                        hint = "600",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            EngineeringDropdown(
                label = "العملة",
                items = Currency.entries,
                selectedItem = selectedCurrency,
                onItemSelected = { selectedCurrency = it },
                itemLabel = { "${it.displayName} (${it.symbol})" },
                icon = Icons.Default.Payments
            )

            CalculateButton(
                onClick = {
                    val pu = puText.toDoubleOrNull() ?: 0.0
                    val mu = muText.toDoubleOrNull() ?: 0.0
                    val colB = colBText.toDoubleOrNull() ?: 300.0
                    val colH = colHText.toDoubleOrNull() ?: 600.0
                    val soilBearing = soilBearingText.toDoubleOrNull() ?: 150.0
                    val depth = depthText.toDoubleOrNull() ?: 1.5

                    result = EngineeringCalculations.isolatedFootingDesign(
                        Pu = pu,
                        Mu = mu,
                        columnB = colB,
                        columnH = colH,
                        soilBearing = soilBearing,
                        fcu = selectedConcreteGrade.fcu,
                        fy = selectedSteelGrade.fy,
                        code = selectedCode,
                        depthBelowGround = depth * 1000
                    )
                    showResults = true
                },
                text = "حساب تصميم القاعدة"
            )

            if (showResults && result != null) {
                val r = result!!

                ResultCard(title = "نتائج تصميم القاعدة", isSafe = r.isSafe) {
                    ResultRow("عرض القاعدة B", String.format("%.2f", r.footingWidth), "m", isHighlighted = true)
                    ResultRow("طول القاعدة L", String.format("%.2f", r.footingLength), "m", isHighlighted = true)
                    ResultRow("سمك القاعدة t", String.format("%.2f", r.footingDepth), "m")
                    ResultRow("العمق الفعال d", String.format("%.0f", r.effectiveDepth), "mm")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ResultRow("إجهاد التربة الفعلي", String.format("%.1f", r.soilPressure), "kN/m²")
                    ResultRow("قدرة التحمل المسموحة", soilBearingText, "kN/m²")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ResultRow("تسليح القاعدة", "φ${r.barDiameter.toInt()}@${r.spacing.toInt()}", "mm", isHighlighted = true)
                    ResultRow("As مطلوب", String.format("%.0f", r.requiredAs), "mm²")
                    ResultRow("As مقدم", String.format("%.0f", r.providedAs), "mm²")
                    ResultRow("عدد الأسياخ", "${r.numberOfBars}")
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (r.codeChecks.isNotEmpty()) {
                    CollapsibleSection(title = "فحوصات الكود", icon = Icons.Default.Checklist) {
                        r.codeChecks.forEach { check ->
                            CodeCheckCard(check)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                CollapsibleSection(title = "خطوات الحل", icon = Icons.Default.Calculate) {
                    r.equations.forEach { step ->
                        EquationStepCard(step)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // الحصر
                val vol = r.footingWidth * r.footingLength * r.footingDepth
                val steelW = r.providedAs * 2 * r.footingWidth * 1000 / 1e6 * 7850 / 1000
                val formwork = 2 * (r.footingWidth + r.footingLength) * r.footingDepth
                val wc = EngineeringConstants.WasteFactors.CONCRETE_FOUNDATIONS
                val ws = EngineeringConstants.WasteFactors.STEEL_STRAIGHT

                Spacer(modifier = Modifier.height(8.dp))
                QuantitySurveyCard(
                    result = QuantitySurveyResult(vol, steelW, formwork, wc, steelW * (1 + ws), vol * (1 + wc))
                )

                val prices = EngineeringConstants.getDefaultPrices(selectedCurrency.name)
                val vc = vol * (1 + wc)
                val sw = steelW * (1 + ws)
                Spacer(modifier = Modifier.height(8.dp))
                CostEstimationCard(
                    result = CostResult(
                        vc * prices.concretePerM3, sw / 1000 * prices.steelPerTon,
                        formwork * prices.formworkPerM2, vc * prices.laborPerM3,
                        vc * prices.concretePerM3 + sw / 1000 * prices.steelPerTon + formwork * prices.formworkPerM2 + vc * prices.laborPerM3,
                        selectedCurrency,
                        listOf(
                            CostItem("خرسانة", vc, "م³", prices.concretePerM3, vc * prices.concretePerM3),
                            CostItem("حديد", sw / 1000, "طن", prices.steelPerTon, sw / 1000 * prices.steelPerTon),
                            CostItem("شدات", formwork, "م²", prices.formworkPerM2, formwork * prices.formworkPerM2),
                            CostItem("عمالة", vc, "م³", prices.laborPerM3, vc * prices.laborPerM3)
                        )
                    )
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
