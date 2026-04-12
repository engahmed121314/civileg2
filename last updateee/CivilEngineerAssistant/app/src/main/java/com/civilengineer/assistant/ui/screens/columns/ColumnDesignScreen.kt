package com.civilengineer.assistant.ui.screens.columns

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.civilengineer.assistant.models.*
import com.civilengineer.assistant.ui.components.*
import com.civilengineer.assistant.ui.theme.*
import com.civilengineer.assistant.utils.EngineeringCalculations
import com.civilengineer.assistant.utils.EngineeringConstants

@Composable
fun ColumnDesignScreen(
    navController: NavController,
    columnType: ColumnType
) {
    // حالة المدخلات
    var selectedCode by remember { mutableStateOf(DesignCode.EGYPTIAN) }
    var selectedConcreteGrade by remember { mutableStateOf(ConcreteGrade.C25) }
    var selectedSteelGrade by remember { mutableStateOf(SteelGrade.ST_360) }
    var selectedCurrency by remember { mutableStateOf(Currency.EGP) }

    var puText by remember { mutableStateOf("") }
    var muxText by remember { mutableStateOf("") }
    var muyText by remember { mutableStateOf("") }
    var bText by remember { mutableStateOf("") }
    var hText by remember { mutableStateOf("") }
    var lengthText by remember { mutableStateOf("") }
    var diameterText by remember { mutableStateOf("") } // للعمود الدائري

    var showResults by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<EngineeringCalculations.ColumnDesignResult?>(null) }

    val isCircular = columnType == ColumnType.SHORT_CIRCULAR || columnType == ColumnType.LONG_CIRCULAR

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = columnType.displayName,
                subtitle = selectedCode.shortName,
                onBackClick = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { /* Export PDF */ }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "تصدير PDF")
                    }
                    IconButton(onClick = { /* Share */ }) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة")
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
            // ═══════════════════════════════════════════
            // اختيار الكود
            // ═══════════════════════════════════════════
            EngineeringDropdown(
                label = "الكود المستخدم",
                items = DesignCode.entries,
                selectedItem = selectedCode,
                onItemSelected = { selectedCode = it },
                itemLabel = { it.displayName },
                icon = Icons.Default.MenuBook
            )

            // ═══════════════════════════════════════════
            // خصائص المواد
            // ═══════════════════════════════════════════
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

            // ═══════════════════════════════════════════
            // الأحمال
            // ═══════════════════════════════════════════
            CollapsibleSection(
                title = "الأحمال التصميمية",
                icon = Icons.Default.FitnessCenter,
                initiallyExpanded = true
            ) {
                EngineeringInputField(
                    label = "الحمل المحوري التصميمي Pu",
                    value = puText,
                    onValueChange = { puText = it },
                    unit = "kN",
                    hint = "مثال: 1500",
                    icon = Icons.Default.ArrowDownward
                )
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(
                    label = "العزم التصميمي Mux",
                    value = muxText,
                    onValueChange = { muxText = it },
                    unit = "kN.m",
                    hint = "مثال: 50 (اكتب 0 إذا لم يوجد)",
                    icon = Icons.Default.RotateRight
                )
                if (columnType == ColumnType.BIAXIAL) {
                    Spacer(modifier = Modifier.height(8.dp))
                    EngineeringInputField(
                        label = "العزم التصميمي Muy",
                        value = muyText,
                        onValueChange = { muyText = it },
                        unit = "kN.m",
                        hint = "العزم في الاتجاه الآخر",
                        icon = Icons.Default.RotateLeft
                    )
                }
            }

            // ═══════════════════════════════════════════
            // أبعاد العمود
            // ═══════════════════════════════════════════
            CollapsibleSection(
                title = "أبعاد العمود",
                icon = Icons.Default.Straighten,
                initiallyExpanded = true
            ) {
                if (isCircular) {
                    EngineeringInputField(
                        label = "قطر العمود D",
                        value = diameterText,
                        onValueChange = { diameterText = it },
                        unit = "mm",
                        hint = "مثال: 400",
                        icon = Icons.Default.Circle
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        EngineeringInputField(
                            label = "العرض b",
                            value = bText,
                            onValueChange = { bText = it },
                            unit = "mm",
                            hint = "250",
                            modifier = Modifier.weight(1f)
                        )
                        EngineeringInputField(
                            label = "الارتفاع h",
                            value = hText,
                            onValueChange = { hText = it },
                            unit = "mm",
                            hint = "600",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(
                    label = "طول العمود Lu",
                    value = lengthText,
                    onValueChange = { lengthText = it },
                    unit = "mm",
                    hint = "مثال: 3000",
                    icon = Icons.Default.Height
                )
            }

            // ═══════════════════════════════════════════
            // اختيار العملة
            // ═══════════════════════════════════════════
            EngineeringDropdown(
                label = "العملة لحساب التكلفة",
                items = Currency.entries,
                selectedItem = selectedCurrency,
                onItemSelected = { selectedCurrency = it },
                itemLabel = { "${it.displayName} (${it.symbol})" },
                icon = Icons.Default.Payments
            )

            // ═══════════════════════════════════════════
            // زر الحساب
            // ═══════════════════════════════════════════
            CalculateButton(
                onClick = {
                    val pu = puText.toDoubleOrNull() ?: 0.0
                    val mux = muxText.toDoubleOrNull() ?: 0.0
                    val b: Double
                    val h: Double

                    if (isCircular) {
                        val d = diameterText.toDoubleOrNull() ?: 400.0
                        b = d
                        h = d
                    } else {
                        b = bText.toDoubleOrNull() ?: 250.0
                        h = hText.toDoubleOrNull() ?: 600.0
                    }
                    val length = lengthText.toDoubleOrNull() ?: 3000.0

                    result = EngineeringCalculations.columnDesign(
                        Pu = pu,
                        Mu = mux,
                        b = b,
                        h = h,
                        length = length,
                        fcu = selectedConcreteGrade.fcu,
                        fy = selectedSteelGrade.fy,
                        code = selectedCode,
                        columnType = columnType
                    )
                    showResults = true
                },
                text = "حساب تصميم العمود"
            )

            // ═══════════════════════════════════════════
            // عرض النتائج
            // ═══════════════════════════════════════════
            if (showResults && result != null) {
                val r = result!!

                // بطاقة النتيجة الرئيسية
                ResultCard(
                    title = "نتائج تصميم العمود",
                    isSafe = r.isSafe
                ) {
                    ResultRow("نوع العمود", if (r.isShortColumn) "قصير" else "طويل", icon = Icons.Default.Info)
                    ResultRow("نسبة النحافة λ", String.format("%.2f", r.slendernessRatio))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ResultRow("مساحة التسليح المطلوبة As", String.format("%.0f", r.requiredAs), "mm²", isHighlighted = true)
                    ResultRow("مساحة التسليح المقدمة", String.format("%.0f", r.providedAs), "mm²")
                    ResultRow("عدد الأسياخ", "${r.numberOfBars}", "أسياخ")
                    ResultRow("قطر السيخ φ", "${r.barDiameter.toInt()}", "mm")
                    ResultRow("نسبة التسليح ρ", String.format("%.2f%%", r.ratio * 100))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ResultRow("قطر الكانة", "${r.stirrupDiameter.toInt()}", "mm")
                    ResultRow("تباعد الكانات", "${r.stirrupSpacing.toInt()}", "mm")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ResultRow("سعة العمود Pu_cap", String.format("%.0f", r.capacity), "kN", isHighlighted = true)
                    ResultRow("معامل الأمان", String.format("%.2f", r.safetyFactor), icon = Icons.Default.Shield)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // فحوصات الكود
                if (r.codeChecks.isNotEmpty()) {
                    CollapsibleSection(
                        title = "فحوصات الكود (Safety Checks)",
                        icon = Icons.Default.Checklist,
                        initiallyExpanded = true
                    ) {
                        r.codeChecks.forEach { check ->
                            CodeCheckCard(check)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // خطوات الحل
                CollapsibleSection(
                    title = "خطوات الحل والمعادلات",
                    icon = Icons.Default.Calculate,
                    initiallyExpanded = false
                ) {
                    r.equations.forEach { step ->
                        EquationStepCard(step)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // الحصر والتكلفة
                val b = if (isCircular) diameterText.toDoubleOrNull() ?: 400.0 else bText.toDoubleOrNull() ?: 250.0
                val h = if (isCircular) b else hText.toDoubleOrNull() ?: 600.0
                val length = lengthText.toDoubleOrNull() ?: 3000.0

                val concreteVol = b * h * length / 1e9
                val steelWeight = r.providedAs * length / 1e6 * 7850 / 1000 // kg
                val formworkArea = 2 * (b + h) * length / 1e6

                val wasteFactorConcrete = EngineeringConstants.WasteFactors.CONCRETE_COLUMNS
                val wasteFactorSteel = EngineeringConstants.WasteFactors.STEEL_STRAIGHT

                val qs = QuantitySurveyResult(
                    concreteVolume = concreteVol,
                    steelWeight = steelWeight,
                    formworkArea = formworkArea,
                    wasteFactor = wasteFactorConcrete,
                    steelWithWaste = steelWeight * (1 + wasteFactorSteel),
                    concreteWithWaste = concreteVol * (1 + wasteFactorConcrete),
                    rebarDetails = listOf(
                        RebarDetail(
                            "تسليح طولي",
                            r.barDiameter,
                            length / 1000.0,
                            r.numberOfBars,
                            r.numberOfBars * RebarDiameter.fromDiameter(r.barDiameter).weight * length / 1000.0
                        ),
                        RebarDetail(
                            "كانات",
                            r.stirrupDiameter,
                            2 * (b + h - 8 * 40) / 1000.0,
                            (length / r.stirrupSpacing).toInt(),
                            (length / r.stirrupSpacing).toInt() * RebarDiameter.fromDiameter(r.stirrupDiameter).weight * 2 * (b + h - 320) / 1000.0
                        )
                    )
                )
                QuantitySurveyCard(result = qs)

                Spacer(modifier = Modifier.height(8.dp))

                // التكلفة
                val prices = EngineeringConstants.getDefaultPrices(selectedCurrency.name)
                val concreteCost = qs.concreteWithWaste * prices.concretePerM3
                val steelCost = qs.steelWithWaste / 1000.0 * prices.steelPerTon
                val formworkCost = qs.formworkArea * prices.formworkPerM2
                val laborCost = qs.concreteWithWaste * prices.laborPerM3
                val totalCost = concreteCost + steelCost + formworkCost + laborCost

                val costResult = CostResult(
                    concreteCost = concreteCost,
                    steelCost = steelCost,
                    formworkCost = formworkCost,
                    laborCost = laborCost,
                    totalCost = totalCost,
                    currency = selectedCurrency,
                    breakdown = listOf(
                        CostItem("خرسانة", qs.concreteWithWaste, "م³", prices.concretePerM3, concreteCost),
                        CostItem("حديد تسليح", qs.steelWithWaste / 1000.0, "طن", prices.steelPerTon, steelCost),
                        CostItem("شدات", qs.formworkArea, "م²", prices.formworkPerM2, formworkCost),
                        CostItem("عمالة", qs.concreteWithWaste, "م³", prices.laborPerM3, laborCost)
                    )
                )
                CostEstimationCard(result = costResult)

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
