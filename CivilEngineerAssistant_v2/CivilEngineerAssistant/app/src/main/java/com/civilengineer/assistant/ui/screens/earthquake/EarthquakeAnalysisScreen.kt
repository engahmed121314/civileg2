package com.civilengineer.assistant.ui.screens.earthquake

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
import com.civilengineer.assistant.utils.EngineeringCalculations

@Composable
fun EarthquakeAnalysisScreen(navController: NavController) {
    var selectedCode by remember { mutableStateOf(DesignCode.EGYPTIAN) }
    var selectedZone by remember { mutableStateOf(SeismicZone.ZONE_3) }
    var selectedImportance by remember { mutableStateOf(ImportanceCategory.CATEGORY_2) }
    var selectedSoilType by remember { mutableStateOf(SoilType.MEDIUM_SAND) }
    var selectedSystem by remember { mutableStateOf(SeismicResistanceSystem.MOMENT_FRAME_ORDINARY) }

    var numStoriesText by remember { mutableStateOf("") }
    var storyHeightText by remember { mutableStateOf("3.0") }
    var storyWeightText by remember { mutableStateOf("") }

    var showResults by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SeismicResult?>(null) }

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = "تحليل الزلازل",
                subtitle = "القوة الاستاتيكية المكافئة - ${selectedCode.shortName}",
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

            CollapsibleSection(title = "معاملات الزلازل", icon = Icons.Default.Vibration, initiallyExpanded = true) {
                EngineeringDropdown(label = "المنطقة الزلزالية", items = SeismicZone.entries,
                    selectedItem = selectedZone, onItemSelected = { selectedZone = it },
                    itemLabel = { it.displayName }, icon = Icons.Default.LocationOn)
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringDropdown(label = "فئة أهمية المبنى", items = ImportanceCategory.entries,
                    selectedItem = selectedImportance, onItemSelected = { selectedImportance = it },
                    itemLabel = { it.displayName }, icon = Icons.Default.Star)
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringDropdown(label = "نوع التربة", items = SoilType.entries.filter { it != SoilType.FILL },
                    selectedItem = selectedSoilType, onItemSelected = { selectedSoilType = it },
                    itemLabel = { it.displayName }, icon = Icons.Default.Terrain)
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringDropdown(label = "نظام المقاومة الإنشائي", items = SeismicResistanceSystem.entries,
                    selectedItem = selectedSystem, onItemSelected = { selectedSystem = it },
                    itemLabel = { "${it.displayName} (R=${it.responseFactor})" }, icon = Icons.Default.AccountBalance)
            }

            CollapsibleSection(title = "بيانات المبنى", icon = Icons.Default.Apartment, initiallyExpanded = true) {
                EngineeringInputField(label = "عدد الأدوار", value = numStoriesText,
                    onValueChange = { numStoriesText = it }, unit = "دور", hint = "6", icon = Icons.Default.Apartment)
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(label = "ارتفاع الدور النموذجي", value = storyHeightText,
                    onValueChange = { storyHeightText = it }, unit = "m", hint = "3.0", icon = Icons.Default.Height)
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringInputField(label = "وزن الدور النموذجي", value = storyWeightText,
                    onValueChange = { storyWeightText = it }, unit = "kN", hint = "5000", icon = Icons.Default.FitnessCenter)
            }

            CalculateButton(
                onClick = {
                    val numStories = numStoriesText.toIntOrNull() ?: 6
                    val storyH = storyHeightText.toDoubleOrNull() ?: 3.0
                    val storyW = storyWeightText.toDoubleOrNull() ?: 5000.0
                    val totalH = numStories * storyH
                    val totalW = numStories * storyW

                    val heights = List(numStories) { storyH }
                    val weights = List(numStories) { storyW }

                    result = EngineeringCalculations.equivalentStaticAnalysis(
                        totalWeight = totalW,
                        buildingHeight = totalH,
                        storyHeights = heights,
                        storyWeights = weights,
                        zone = selectedZone,
                        importance = selectedImportance,
                        soilType = selectedSoilType,
                        resistanceSystem = selectedSystem,
                        code = selectedCode
                    )
                    showResults = true
                },
                text = "حساب تحليل الزلازل"
            )

            if (showResults && result != null) {
                val r = result!!

                ResultCard(title = "نتائج تحليل الزلازل") {
                    ResultRow("قوة القص القاعدية V", String.format("%.1f", r.baseShear), "kN", isHighlighted = true,
                        icon = Icons.Default.Vibration)
                    ResultRow("الزمن الدوري T", String.format("%.3f", r.naturalPeriod), "sec")
                    ResultRow("معامل الاستجابة Sd/Cs", String.format("%.4f", r.seismicCoefficient))
                    ResultRow("معامل الاستجابة R", String.format("%.1f", r.responseFactor))
                    ResultRow("معامل الأهمية I", String.format("%.2f", r.importanceFactor))
                    ResultRow("معامل التربة S", String.format("%.2f", r.soilFactor))
                    ResultRow("معامل المنطقة", String.format("%.2f", r.zoneFactor))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // توزيع القوى على الأدوار
                ResultCard(title = "توزيع القوى الأفقية على الأدوار") {
                    // رأس الجدول
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text("الدور", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = PrimaryBlueDark)
                        Text("h (m)", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = PrimaryBlueDark)
                        Text("Fi (kN)", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = PrimaryBlueDark)
                        Text("Vi (kN)", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = PrimaryBlueDark)
                    }
                    HorizontalDivider()

                    r.storyForces.forEach { sf ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("${sf.storyNumber}", modifier = Modifier.weight(1f))
                            Text(String.format("%.1f", sf.height), modifier = Modifier.weight(1f))
                            Text(String.format("%.1f", sf.force), modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Medium, color = EarthquakeCardColor)
                            Text(String.format("%.1f", sf.shear), modifier = Modifier.weight(1f))
                        }
                        HorizontalDivider(color = PrimaryBlueVeryLight)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                CollapsibleSection(title = "خطوات الحل والمعادلات", icon = Icons.Default.Calculate) {
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
