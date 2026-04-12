package com.civilengineer.app.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.civilengineer.app.data.models.FootingDesign
import com.civilengineer.app.presentation.viewmodel.FootingDesignViewModel

/**
 * شاشة تفاصيل نتائج تصميم القاعدة
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FootingDetailsScreen(
    footingId: Int,
    navController: NavController,
    viewModel: FootingDesignViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedFooting = uiState.selectedFooting
    var showEquations by remember { mutableStateOf(false) }

    if (selectedFooting == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تفاصيل تصميم القاعدة") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "عودة")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SafetyStatusMultiCard(footing = selectedFooting)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "معلومات القاعدة",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ResultRow("اسم القاعدة", selectedFooting.footingName)
                        ResultRow("نوع القاعدة", selectedFooting.footingType.toString())
                        ResultRow("مساحة القاعدة", "%.2f m²".format(selectedFooting.footingAreaM2))
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "الأحمال والضغوط",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ResultRow("حمل العمود", "%.2f kN".format(selectedFooting.columnLoadKN))
                        ResultRow(
                            "الضغط الفعلي على التربة",
                            "%.2f kN/m²".format(selectedFooting.actualSoilPressureKNM2)
                        )
                        ResultRow(
                            "تحمل التربة المسموح به",
                            "%.2f kN/m²".format(selectedFooting.soilBearingCapacityKNM2)
                        )
                        ResultRow(
                            "نسبة التحمل",
                            "%.2f".format(selectedFooting.bearingCapacityRatio)
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (selectedFooting.isSafeBearing)
                                    Color.Green.copy(alpha = 0.1f)
                                else
                                    Color.Red.copy(alpha = 0.1f)
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = if (selectedFooting.isSafeBearing)
                                        "التحمل آمن ✓"
                                    else
                                        "التحمل غير آمن ✗",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (selectedFooting.isSafeBearing)
                                        Color(0xFF2E7D32)
                                    else
                                        Color(0xFFC62828)
                                )
                            }
                            Icon(
                                imageVector = if (selectedFooting.isSafeBearing)
                                    Icons.Filled.CheckCircle
                                else
                                    Icons.Filled.Error,
                                contentDescription = "حالة التحمل",
                                tint = if (selectedFooting.isSafeBearing)
                                    Color(0xFF2E7D32)
                                else
                                    Color(0xFFC62828),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "نتائج الفحص",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ResultRow(
                            "أقصى عزم انحناء",
                            "%.2f kN.m".format(selectedFooting.maxMomentKNM)
                        )
                        ResultRow(
                            "قوة القص الأقصى",
                            "%.2f kN".format(selectedFooting.shearForceKN)
                        )
                        ResultRow(
                            "مساحة الفولاذ المطلوبة",
                            "%.2f mm²".format(selectedFooting.steelAreaMM2)
                        )
                        ResultRow(
                            "الحد الأدنى من الفولاذ",
                            "%.2f mm²".format(selectedFooting.minSteelAreaMM2)
                        )
                        ResultRow(
                            "قطر الفولاذ / تباعد",
                            "%d / %d ملم".format(selectedFooting.steelDiaMM, selectedFooting.steelSpacingMM)
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (selectedFooting.isSafeFlexure)
                                    Color.Green.copy(alpha = 0.1f)
                                else
                                    Color.Red.copy(alpha = 0.1f)
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = if (selectedFooting.isSafeFlexure)
                                        "الانحناء آمن ✓"
                                    else
                                        "الانحناء غير آمن ✗",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (selectedFooting.isSafeFlexure)
                                        Color(0xFF2E7D32)
                                    else
                                        Color(0xFFC62828)
                                )
                            }
                            Icon(
                                imageVector = if (selectedFooting.isSafeFlexure)
                                    Icons.Filled.CheckCircle
                                else
                                    Icons.Filled.Error,
                                contentDescription = "حالة الانحناء",
                                tint = if (selectedFooting.isSafeFlexure)
                                    Color(0xFF2E7D32)
                                else
                                    Color(0xFFC62828),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "التكاليف",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ResultRow(
                            "التكلفة الإجمالية",
                            "%.2f %s".format(selectedFooting.totalCost, selectedFooting.unitCurrency)
                        )
                        ResultRow(
                            "سعر الفولاذ لكل طن",
                            "%.2f %s".format(selectedFooting.costPerTonSteel, selectedFooting.unitCurrency)
                        )
                        ResultRow(
                            "سعر الخرسانة لكل م³",
                            "%.2f %s".format(selectedFooting.costPerM3Concrete, selectedFooting.unitCurrency)
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = { showEquations = !showEquations },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.Functions, contentDescription = "معادلات")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("عرض المعادلات المستخدمة")
                }
            }

            if (showEquations) {
                item {
                    EquationsCard(equations = uiState.designEquations)
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SafetyStatusMultiCard(footing: FootingDesign) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // بطاقة التحمل
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (footing.isSafeBearing)
                        Color.Green.copy(alpha = 0.1f)
                    else
                        Color.Red.copy(alpha = 0.1f)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("تحمل التربة", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = if (footing.isSafeBearing) "آمن" else "غير آمن",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Icon(
                    imageVector = if (footing.isSafeBearing)
                        Icons.Filled.CheckCircle
                    else
                        Icons.Filled.Error,
                    contentDescription = null,
                    tint = if (footing.isSafeBearing)
                        Color(0xFF2E7D32)
                    else
                        Color(0xFFC62828)
                )
            }
        }

        // بطاقة الانحناء
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (footing.isSafeFlexure)
                        Color.Green.copy(alpha = 0.1f)
                    else
                        Color.Red.copy(alpha = 0.1f)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("الانحناء", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = if (footing.isSafeFlexure) "آمن" else "غير آمن",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Icon(
                    imageVector = if (footing.isSafeFlexure)
                        Icons.Filled.CheckCircle
                    else
                        Icons.Filled.Error,
                    contentDescription = null,
                    tint = if (footing.isSafeFlexure)
                        Color(0xFF2E7D32)
                    else
                        Color(0xFFC62828)
                )
            }
        }

        // بطاقة القص
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (footing.isSafeShear)
                        Color.Green.copy(alpha = 0.1f)
                    else
                        Color.Red.copy(alpha = 0.1f)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("القص", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = if (footing.isSafeShear) "آمن" else "غير آمن",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Icon(
                    imageVector = if (footing.isSafeShear)
                        Icons.Filled.CheckCircle
                    else
                        Icons.Filled.Error,
                    contentDescription = null,
                    tint = if (footing.isSafeShear)
                        Color(0xFF2E7D32)
                    else
                        Color(0xFFC62828)
                )
            }
        }
    }
}