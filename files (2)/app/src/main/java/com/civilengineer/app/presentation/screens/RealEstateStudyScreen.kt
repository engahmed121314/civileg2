package com.civilengineer.app.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.civilengineer.app.data.models.ProjectType
import com.civilengineer.app.presentation.components.CostPieChart
import com.civilengineer.app.presentation.components.ResultsTable
import com.civilengineer.app.presentation.viewmodel.RealEstateStudyViewModel

/**
 * شاشة دراسة العقار السريعة
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealEstateStudyScreen(
    navController: NavController,
    viewModel: RealEstateStudyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var studyName by remember { mutableStateOf("دراسة عقار جديدة") }
    var landAreaM2 by remember { mutableStateOf("500") }
    var buildingBudget by remember { mutableStateOf("100000") }
    var numberOfFloors by remember { mutableStateOf("3") }
    var selectedProjectType by remember { mutableStateOf(ProjectType.RESIDENTIAL) }
    var buildOnFullLand by remember { mutableStateOf(true) }
    var buildingAreaPercentage by remember { mutableStateOf("80") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دراسة العقار السريعة") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader("معلومات الدراسة")
            }

            item {
                TextInputField(
                    label = "اسم الدراسة",
                    value = studyName,
                    onValueChange = { studyName = it }
                )
            }

            item {
                SectionHeader("بيانات الأرض")
            }

            item {
                TextInputField(
                    label = "مساحة الأرض (م²)",
                    value = landAreaM2,
                    onValueChange = { landAreaM2 = it },
                    keyboardType = KeyboardType.Decimal
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("البناء على كامل الأرض:", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = buildOnFullLand,
                        onCheckedChange = { buildOnFullLand = it }
                    )
                }
            }

            if (!buildOnFullLand) {
                item {
                    TextInputField(
                        label = "نسبة مساحة البناء (%)",
                        value = buildingAreaPercentage,
                        onValueChange = { buildingAreaPercentage = it },
                        keyboardType = KeyboardType.Decimal
                    )
                }
            }

            item {
                SectionHeader("بيانات المشروع")
            }

            item {
                Text("نوع المشروع:", style = MaterialTheme.typography.labelMedium)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    ProjectType.values().forEach { projectType ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedProjectType == projectType,
                                onClick = { selectedProjectType = projectType }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(getProjectTypeName(projectType))
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextInputField(
                        label = "عدد الأدوار",
                        value = numberOfFloors,
                        onValueChange = { numberOfFloors = it },
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                    TextInputField(
                        label = "الميزانية ($)",
                        value = buildingBudget,
                        onValueChange = { buildingBudget = it },
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        viewModel.performStudy(
                            studyName = studyName,
                            landAreaM2 = landAreaM2.toDoubleOrNull() ?: 500.0,
                            buildingBudget = buildingBudget.toDoubleOrNull() ?: 100000.0,
                            numberOfFloors = numberOfFloors.toIntOrNull() ?: 3,
                            projectType = selectedProjectType,
                            buildOnFullLand = buildOnFullLand,
                            buildingAreaPercentage = if (buildOnFullLand) 100.0 else (buildingAreaPercentage.toDoubleOrNull() ?: 80.0)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.Calculate, contentDescription = "حساب")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تنفيذ الدراسة")
                }
            }

            if (uiState.selectedStudy != null) {
                item {
                    RealEstateStudyResults(study = uiState.selectedStudy!!)
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (uiState.error != null) {
        ErrorDialog(message = uiState.error ?: "", onDismiss = { viewModel.clearError() })
    }
}

@Composable
fun RealEstateStudyResults(study: com.civilengineer.app.data.models.RealEstateStudy) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // حالة الجدوى
        val feasibilityColor = when (study.feasibilityStatus) {
            com.civilengineer.app.data.models.FeasibilityStatus.FEASIBLE -> Color.Green
            com.civilengineer.app.data.models.FeasibilityStatus.FEASIBLE_WITH_REDUCTION -> Color(0xFFFF9800)
            com.civilengineer.app.data.models.FeasibilityStatus.FEASIBLE_WITH_BUDGET_INCREASE -> Color(0xFFF44336)
            com.civilengineer.app.data.models.FeasibilityStatus.NOT_FEASIBLE -> Color.Red
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .background(feasibilityColor.copy(alpha = 0.1f))
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
                        "حالة الجدوى",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        getFeasibilityStatusName(study.feasibilityStatus),
                        style = MaterialTheme.typography.titleSmall,
                        color = feasibilityColor
                    )
                }
                Icon(
                    Icons.Filled.Assessment,
                    contentDescription = null,
                    tint = feasibilityColor,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // الملخص المالي
        ResultsTable(
            title = "الملخص المالي",
            rows = listOf(
                "مساحة الأرض" to "${"%.2f".format(study.landAreaM2)} م²",
                "مساحة البناء المخطط" to "${"%.2f".format(study.buildingAreaM2)} م²",
                "التكلفة المقدرة للمتر" to "${"%.2f".format(study.costPerM2Estimated)} ${study.currency}",
                "التكلفة الإجمالية المقدرة" to "${"%.2f".format(study.estimatedTotalCost)} ${study.currency}",
                "الميزانية المتاحة" to "${"%.2f".format(study.buildingBudget)} ${study.currency}",
                "نسبة تغطية الميزانية" to "${"%.1f".format(study.budgetCoveragePercentage)}%"
            )
        )

        // توزيع التكاليف
        CostPieChart(
            concretePercentage = study.costBreakdown.foundationPercentage + study.costBreakdown.structuralPercentage,
            steelPercentage = 15.0,
            laborPercentage = study.costBreakdown.utilitiesPercentage,
            otherPercentage = study.costBreakdown.miscellaneousPercentage + study.costBreakdown.finishingPercentage,
            totalCost = study.estimatedTotalCost,
            currency = study.currency
        )

        // التوصيات
        if (study.recommendations.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "التوصيات",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        study.recommendations,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

fun getProjectTypeName(type: ProjectType): String {
    return when (type) {
        ProjectType.RESIDENTIAL -> "سكني"
        ProjectType.COMMERCIAL -> "تجاري"
        ProjectType.INDUSTRIAL -> "صناعي"
        ProjectType.MIXED_USE -> "استخدام مختلط"
        ProjectType.RESIDENTIAL_COMMERCIAL -> "سكني تجاري"
    }
}

fun getFeasibilityStatusName(status: com.civilengineer.app.data.models.FeasibilityStatus): String {
    return when (status) {
        com.civilengineer.app.data.models.FeasibilityStatus.FEASIBLE -> "ممكن التنفيذ"
        com.civilengineer.app.data.models.FeasibilityStatus.FEASIBLE_WITH_REDUCTION -> "ممكن مع تقليل المساحة"
        com.civilengineer.app.data.models.FeasibilityStatus.FEASIBLE_WITH_BUDGET_INCREASE -> "ممكن مع زيادة الميزانية"
        com.civilengineer.app.data.models.FeasibilityStatus.NOT_FEASIBLE -> "غير ممكن التنفيذ"
    }
}