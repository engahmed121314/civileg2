package com.civilengineer.assistant.ui.screens.beams

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
import kotlin.math.ceil

@Composable
fun BeamDesignScreen(
    navController: NavController,
    beamType: BeamType
) {
    var selectedCode by remember { mutableStateOf(DesignCode.EGYPTIAN) }
    var selectedConcreteGrade by remember { mutableStateOf(ConcreteGrade.C25) }
    var selectedSteelGrade by remember { mutableStateOf(SteelGrade.ST_360) }
    var selectedCurrency by remember { mutableStateOf(Currency.EGP) }

    var spanText by remember { mutableStateOf("") }
    var bText by remember { mutableStateOf("") }
    var hText by remember { mutableStateOf("") }
    var dlText by remember { mutableStateOf("") }
    var llText by remember { mutableStateOf("") }
    var tributaryWidthText by remember { mutableStateOf("") }

    // لكمرة T
    var flangeWidthText by remember { mutableStateOf("") }
    var flangeThicknessText by remember { mutableStateOf("") }

    var showResults by remember { mutableStateOf(false) }
    var flexResult by remember { mutableStateOf<EngineeringCalculations.FlexureResult?>(null) }
    var shearResult by remember { mutableStateOf<EngineeringCalculations.ShearDesignResult?>(null) }

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = beamType.displayName,
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
                    label = "رتبة الخرسانة",
                    items = ConcreteGrade.entries,
                    selectedItem = selectedConcreteGrade,
                    onItemSelected = { selectedConcreteGrade = it },
                    itemLabel = { it.displayName }
                )
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringDropdown(
                    label = "رتبة حديد التسليح",
                    items = SteelGrade.entries,
                    selectedItem = selectedSteelGrade,
                    onItemSelected = { selectedSteelGrade = it },
                    itemLabel = { it.displayName }
                )
            }

            CollapsibleSection(title = "أبعاد الكمرة", icon = Icons.Default.Straighten, initiallyExpanded = true) {
                EngineeringInputField(
                    label = "البحر L",
                    value = spanText,
                    onValueChange = { spanText = it },
                    unit = "m",
                    hint = "6.0",
                    icon = Icons.Default.Straighten
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    EngineeringInputField(
                        label = "العرض b",
                        value = bText,
                        onValueChange = { bText = it },
                        unit = "mm",
                        hint = "250",
                        modifier = Modifier.weight(1f)
                    )
                    EngineeringInputField(
                        label = "الارتفاع الكلي h",
                        value = hText,
                        onValueChange = { hText = it },
                        unit = "mm",
                        hint = "600",
                        modifier = Modifier.weight(1f)
                    )
                }

                if (beamType == BeamType.SIMPLE_T || beamType == BeamType.SIMPLE_L) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        EngineeringInputField(
                            label = "عرض الشفة bf",
                            value = flangeWidthText,
                            onValueChange = { flangeWidthText = it },
                            unit = "mm",
                            hint = "1000",
                            modifier = Modifier.weight(1f)
                        )
                        EngineeringInputField(
                            label = "سمك الشفة tf",
                            value = flangeThicknessText,
                            onValueChange = { flangeThicknessText = it },
                            unit = "mm",
                            hint = "120",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            CollapsibleSection(title = "الأحمال", icon = Icons.Default.FitnessCenter, initiallyExpanded = true) {
                EngineeringInputField(
                    label = "عرض المحمل (Tributary Width)",
                    value = tributaryWidthText,
                    onValueChange = { tributaryWidthText = it },
                    unit = "m",
                    hint = "4.0 (المسافة بين الكمرات)",
                    icon = Icons.Default.SpaceBar
                )
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(
                    label = "حمل ميت على البلاطة",
                    value = dlText,
                    onValueChange = { dlText = it },
                    unit = "kN/m²",
                    hint = "8.0 (وزن ذاتي + تشطيبات)",
                    icon = Icons.Default.Layers
                )
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(
                    label = "حمل حي على البلاطة",
                    value = llText,
                    onValueChange = { llText = it },
                    unit = "kN/m²",
                    hint = "2.0",
                    icon = Icons.Default.People
                )
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
                    val span = spanText.toDoubleOrNull() ?: 6.0
                    val b = bText.toDoubleOrNull() ?: 250.0
                    val h = hText.toDoubleOrNull() ?: 600.0
                    val dl = dlText.toDoubleOrNull() ?: 8.0
                    val ll = llText.toDoubleOrNull() ?: 2.0
                    val tw = tributaryWidthText.toDoubleOrNull() ?: 4.0
                    val fcu = selectedConcreteGrade.fcu
                    val fy = selectedSteelGrade.fy
                    val cover = 25.0 + 8.0 + 10.0  // cover + stirrup + half bar
                    val d = h - cover

                    // حمل موزع على الكمرة
                    val beamSelfWeight = b / 1000 * h / 1000 * 25.0  // kN/m
                    val slabLoadOnBeam = (dl + ll) * tw  // kN/m
                    val wallLoad = 0.0  // يمكن إضافته

                    val (df, lf) = EngineeringConstants.getUltimateLoadFactor(selectedCode)
                    val wuDead = df * (beamSelfWeight + dl * tw)
                    val wuLive = lf * ll * tw
                    val wu = wuDead + wuLive

                    // العزوم والقص
                    val momentFactor = when (beamType) {
                        BeamType.SIMPLE_RECTANGULAR, BeamType.SIMPLE_T, BeamType.SIMPLE_L -> 8.0
                        BeamType.CONTINUOUS -> 10.0  // approximate
                        BeamType.CANTILEVER -> 2.0
                        else -> 8.0
                    }

                    val Mu = wu * span * span / momentFactor  // kN.m
                    val Vu = wu * span / 2.0  // kN

                    flexResult = EngineeringCalculations.flexureDesign(Mu, b, d, fcu, fy, selectedCode)
                    shearResult = EngineeringCalculations.shearDesign(Vu, b, d, fcu, fy, selectedCode)

                    showResults = true
                },
                text = "حساب تصميم الكمرة"
            )

            if (showResults && flexResult != null && shearResult != null) {
                val fr = flexResult!!
                val sr = shearResult!!
                val b = bText.toDoubleOrNull() ?: 250.0
                val h = hText.toDoubleOrNull() ?: 600.0
                val span = spanText.toDoubleOrNull() ?: 6.0
                val cover = 43.0
                val d = h - cover

                // اختيار الحديد
                val rebar = EngineeringCalculations.selectRebar(fr.requiredAs, b, h, 25.0)
                val rhoMin = EngineeringConstants.getBeamMinRatio(selectedCode, selectedConcreteGrade.fcu, selectedSteelGrade.fy)
                val rhoMax = EngineeringConstants.getBeamMaxRatio(selectedCode, selectedConcreteGrade.fcu, selectedSteelGrade.fy)

                ResultCard(title = "نتائج تصميم الكمرة", isSafe = sr.isSectionAdequate) {
                    ResultRow("تسليح الشد", "${rebar.count}φ${rebar.barDiameter.toInt()}", "", isHighlighted = true)
                    ResultRow("As مطلوب", String.format("%.0f", fr.requiredAs), "mm²")
                    ResultRow("As مقدم", String.format("%.0f", rebar.totalArea), "mm²")
                    ResultRow("نسبة التسليح ρ", String.format("%.4f", fr.ratio))
                    ResultRow("ρ_min", String.format("%.4f", rhoMin))
                    ResultRow("ρ_max", String.format("%.4f", rhoMax))
                    ResultRow("تسليح مزدوج؟", if (fr.isDoublyReinforced) "نعم" else "لا")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ResultRow("كانات", "φ${sr.stirrupDiameter.toInt()}/${sr.stirrupLegs} أفرع @${sr.stirrupSpacing.toInt()}", "mm", isHighlighted = true)
                    ResultRow("Vc (خرسانة)", String.format("%.1f", sr.concreteCapacity), "kN")
                    ResultRow("Vs (حديد)", String.format("%.1f", sr.steelRequired), "kN")
                    ResultRow("القطاع كافٍ؟", if (sr.isSectionAdequate) "نعم ✓" else "لا ✗ يجب تكبير القطاع")
                }

                Spacer(modifier = Modifier.height(8.dp))

                CollapsibleSection(title = "خطوات حل الانحناء", icon = Icons.Default.Calculate) {
                    fr.equations.forEach { step ->
                        EquationStepCard(step)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                CollapsibleSection(title = "خطوات حل القص", icon = Icons.Default.Calculate) {
                    sr.equations.forEach { step ->
                        EquationStepCard(step)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // الحصر
                val vol = b * h * span * 1000 / 1e9
                val mainSteelW = rebar.totalArea * span * 1000 / 1e6 * 7850 / 1000
                val nStirrup = ceil(span * 1000 / sr.stirrupSpacing).toInt()
                val stirrupLength = 2 * (b + h - 8 * 25) / 1000.0
                val stirrupW = nStirrup * RebarDiameter.fromDiameter(sr.stirrupDiameter).weight * stirrupLength
                val totalSteel = mainSteelW + stirrupW
                val wc = EngineeringConstants.WasteFactors.CONCRETE_BEAMS
                val ws = EngineeringConstants.WasteFactors.STEEL_BENT

                Spacer(modifier = Modifier.height(8.dp))
                QuantitySurveyCard(
                    result = QuantitySurveyResult(vol, totalSteel, 2 * (b + h) / 1000 * span, wc,
                        totalSteel * (1 + ws), vol * (1 + wc),
                        rebarDetails = listOf(
                            RebarDetail("تسليح شد رئيسي", rebar.barDiameter, span, rebar.count, mainSteelW),
                            RebarDetail("كانات", sr.stirrupDiameter, stirrupLength, nStirrup, stirrupW)
                        )
                    )
                )

                val prices = EngineeringConstants.getDefaultPrices(selectedCurrency.name)
                val vc = vol * (1 + wc)
                val sw = totalSteel * (1 + ws)
                val fw = 2 * (b + h) / 1000 * span

                Spacer(modifier = Modifier.height(8.dp))
                CostEstimationCard(
                    result = CostResult(
                        vc * prices.concretePerM3, sw / 1000 * prices.steelPerTon,
                        fw * prices.formworkPerM2, vc * prices.laborPerM3,
                        vc * prices.concretePerM3 + sw / 1000 * prices.steelPerTon + fw * prices.formworkPerM2 + vc * prices.laborPerM3,
                        selectedCurrency,
                        listOf(
                            CostItem("خرسانة", vc, "م³", prices.concretePerM3, vc * prices.concretePerM3),
                            CostItem("حديد", sw / 1000, "طن", prices.steelPerTon, sw / 1000 * prices.steelPerTon),
                            CostItem("شدات", fw, "م²", prices.formworkPerM2, fw * prices.formworkPerM2),
                            CostItem("عمالة", vc, "م³", prices.laborPerM3, vc * prices.laborPerM3)
                        )
                    )
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
