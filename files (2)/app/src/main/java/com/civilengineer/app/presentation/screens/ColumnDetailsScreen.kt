package com.civilengineer.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.civilengineer.app.data.models.ColumnDesign
import com.civilengineer.app.presentation.viewmodel.ColumnDesignViewModel

/**
 * شاشة تفاصيل نتائج التصميم
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnDetailsScreen(
    columnId: Int,
    navController: NavController,
    viewModel: ColumnDesignViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedColumn = uiState.selectedColumn
    var showEquations by remember { mutableStateOf(false) }

    LaunchedEffect(columnId) {
        if (selectedColumn == null) {
            // تحميل العمود إذا لم يكن محملاً
        }
    }

    if (selectedColumn == null) {
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
                title = { Text("تفاصيل التصميم") },
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
                SafetyStatusCard(column = selectedColumn)
            }

            item {
                ResultsSummaryCard(column = selectedColumn)
            }

            item {
                DimensionsCard(column = selectedColumn)
            }

            item {
                LoadsCard(column = selectedColumn)
            }

            item {
                DesignResultsCard(column = selectedColumn)
            }

            item {
                CostBreakdownCard(column = selectedColumn)
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
fun SafetyStatusCard(column: ColumnDesign) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (column.isSafe)
                    Color.Green.copy(alpha = 0.1f)
                else
                    Color.Red.copy(alpha = 0.1f)
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (column.isSafe) "التصميم آمن ✓" else "التصميم غير آمن ✗",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (column.isSafe)
                        Color(0xFF2E7D32)
                    else
                        Color(0xFFC62828)
                )
                Text(
                    text = "معامل الأمان: %.2f".format(column.safetyFactor),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Icon(
                imageVector = if (column.isSafe)
                    Icons.Filled.CheckCircle
                else
                    Icons.Filled.Error,
                contentDescription = "الحالة",
                modifier = Modifier.size(48.dp),
                tint = if (column.isSafe)
                    Color(0xFF2E7D32)
                else
                    Color(0xFFC62828)
            )
        }
    }
}

@Composable
fun ResultsSummaryCard(column: ColumnDesign) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "ملخص النتائج",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            ResultRow(
                label = "التحمل الكلي",
                value = "%.2f kN".format(column.totalCapacityKN)
            )
            ResultRow(
                label = "الحمل المطبق",
                value = "%.2f kN".format(column.axialLoadKN)
            )
            ResultRow(
                label = "التحمل الخرساني",
                value = "%.2f kN".format(column.concreteCapacityKN)
            )
            ResultRow(
                label = "تحمل الفولاذ",
                value = "%.2f kN".format(column.steelCapacityKN)
            )
        }
    }
}

@Composable
fun DimensionsCard(column: ColumnDesign) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "الأبعاد",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            ResultRow(
                label = "الطول",
                value = "%.2f م".format(column.lengthM)
            )
            ResultRow(
                label = "العرض",
                value = "%.2f م".format(column.widthM)
            )
            ResultRow(
                label = "الارتفاع",
                value = "%.2f م".format(column.heightM)
            )
            ResultRow(
                label = "حجم الخرسانة",
                value = "%.3f م³".format(column.getConcreteVolume())
            )
        }
    }
}

@Composable
fun LoadsCard(column: ColumnDesign) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "الأحمال والعزوم",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            ResultRow(
                label = "الحمل المحوري",
                value = "%.2f kN".format(column.axialLoadKN)
            )
            ResultRow(
                label = "عزم الانحناء",
                value = "%.2f kN.m".format(column.bendingMomentKNM)
            )
        }
    }
}

@Composable
fun DesignResultsCard(column: ColumnDesign) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "نتائج التصميم",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            ResultRow(
                label = "مساحة الفولاذ الرئيسي",
                value = "%.2f mm²".format(column.mainSteelAreaMM2)
            )
            ResultRow(
                label = "قطر الفولاذ الرئيسي",
                value = "%d ملم".format(column.mainSteelDiaMM)
            )
            ResultRow(
                label = "تباعد الكانات",
                value = "%d ملم".format(column.stirrupsSpacingMM)
            )
            ResultRow(
                label = "نسبة الفولاذ",
                value = "%.2f%%".format(column.mainSteelPercentage)
            )
            ResultRow(
                label = "النسبة الرشاقة",
                value = "%.2f".format(column.slendernessRatio)
            )
            ResultRow(
                label = "معامل التخفيض",
                value = "%.3f".format(column.reductionFactor)
            )
            ResultRow(
                label = "وزن الفولاذ",
                value = "%.2f طن".format(column.getSteelWeightTons())
            )
        }
    }
}

@Composable
fun CostBreakdownCard(column: ColumnDesign) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "تحليل التكاليف",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            ResultRow(
                label = "التكلفة الإجمالية",
                value = "%.2f %s".format(column.totalCost, column.unitCurrency)
            )
            ResultRow(
                label = "سعر الفولاذ لكل طن",
                value = "%.2f %s".format(column.costPerTonSteel, column.unitCurrency)
            )
            ResultRow(
                label = "سعر الخرسانة لكل م³",
                value = "%.2f %s".format(column.costPerM3Concrete, column.unitCurrency)
            )
            ResultRow(
                label = "التكلفة لكل م²",
                value = "%.2f %s".format(
                    column.totalCost / (column.lengthM * column.widthM),
                    column.unitCurrency
                )
            )
        }
    }
}

@Composable
fun EquationsCard(equations: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "المعادلات المستخدمة",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = equations,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
    Divider(modifier = Modifier.padding(vertical = 2.dp))
}