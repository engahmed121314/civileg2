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
import com.civilengineer.app.data.models.SlabDesign
import com.civilengineer.app.presentation.viewmodel.SlabDesignViewModel

/**
 * شاشة تفاصيل نتائج تصميم البلاطة
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlabDetailsScreen(
    slabId: Int,
    navController: NavController,
    viewModel: SlabDesignViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedSlab = uiState.selectedSlab
    var showEquations by remember { mutableStateOf(false) }

    if (selectedSlab == null) {
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
                title = { Text("تفاصيل تصميم البلاطة") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "عودة")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Export to PDF */ }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "تنزيل")
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (selectedSlab.isSafe)
                                    Color.Green.copy(alpha = 0.1f)
                                else
                                    Color.Red.copy(alpha = 0.1f)
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (selectedSlab.isSafe) "التصميم آمن ✓" else "التصميم غير آمن ✗",
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (selectedSlab.isSafe)
                                    Color(0xFF2E7D32)
                                else
                                    Color(0xFFC62828)
                            )
                            Text(
                                text = "الانحراف: %.2f ملم من أصل %.2f ملم".format(
                                    selectedSlab.deflectionMM,
                                    selectedSlab.maxAllowedDeflectionMM
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Icon(
                            imageVector = if (selectedSlab.isSafe)
                                Icons.Filled.CheckCircle
                            else
                                Icons.Filled.Error,
                            contentDescription = "الحالة",
                            modifier = Modifier.size(48.dp),
                            tint = if (selectedSlab.isSafe)
                                Color(0xFF2E7D32)
                            else
                                Color(0xFFC62828)
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "معلومات البلاطة",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ResultRow("اسم البلاطة", selectedSlab.slabName)
                        ResultRow("الطول", "%.2f م".format(selectedSlab.lengthM))
                        ResultRow("العرض", "%.2f م".format(selectedSlab.widthM))
                        ResultRow("السمك", "%d ملم".format(selectedSlab.thicknessMM))
                        ResultRow("النوع", selectedSlab.slabType.toString())
                        ResultRow("نوع الاستناد", selectedSlab.supportType.toString())
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "الأحمال",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ResultRow(
                            "الحمل الميت",
                            "%.2f kN/m²".format(selectedSlab.deadLoadKNM2)
                        )
                        ResultRow(
                            "الحمل الحي",
                            "%.2f kN/m²".format(selectedSlab.liveLoadKNM2)
                        )
                        ResultRow(
                            "الحمل الكلي",
                            "%.2f kN".format(selectedSlab.getTotalLoad())
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "نتائج التصميم",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ResultRow(
                            "أقصى عزم انحناء",
                            "%.2f kN.m".format(selectedSlab.maxMomentKNM)
                        )
                        ResultRow(
                            "قوة القص الأقصى",
                            "%.2f kN".format(selectedSlab.shearForceKN)
                        )
                        ResultRow(
                            "الفولاذ السفلي (قطر/تباعد)",
                            "%d / %d ملم".format(
                                selectedSlab.bottomSteelDiaMM,
                                selectedSlab.bottomSteelSpacingMM
                            )
                        )
                        ResultRow(
                            "الفولاذ العلوي (قطر/تباعد)",
                            "%d / %d ملم".format(
                                selectedSlab.topSteelDiaMM,
                                selectedSlab.topSteelSpacingMM
                            )
                        )
                        ResultRow(
                            "مساحة الفولاذ السفلي",
                            "%.2f mm²/m".format(selectedSlab.bottomSteelAreaMM2M)
                        )
                        ResultRow(
                            "مساحة الفولاذ العلوي",
                            "%.2f mm²/m".format(selectedSlab.topSteelAreaMM2M)
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "الانحراف",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ResultRow(
                            "الانحراف المحسوب",
                            "%.2f ملم".format(selectedSlab.deflectionMM)
                        )
                        ResultRow(
                            "الانحراف المسموح به",
                            "%.2f ملم".format(selectedSlab.maxAllowedDeflectionMM)
                        )
                        ResultRow(
                            "النسبة",
                            "%.2f%%".format(
                                (selectedSlab.deflectionMM / selectedSlab.maxAllowedDeflectionMM) * 100
                            )
                        )
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
                            "حجم الخرسانة",
                            "%.3f م³".format(selectedSlab.getConcreteVolume())
                        )
                        ResultRow(
                            "التكلفة الإجمالية",
                            "%.2f %s".format(selectedSlab.totalCost, selectedSlab.unitCurrency)
                        )
                        ResultRow(
                            "التكلفة لكل م²",
                            "%.2f %s".format(
                                selectedSlab.totalCost / (selectedSlab.lengthM * selectedSlab.widthM),
                                selectedSlab.unitCurrency
                            )
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