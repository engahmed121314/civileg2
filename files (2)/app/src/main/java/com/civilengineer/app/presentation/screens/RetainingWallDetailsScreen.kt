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
import com.civilengineer.app.data.models.RetainingWall
import com.civilengineer.app.presentation.viewmodel.RetainingWallDesignViewModel

/**
 * شاشة تفاصيل نتائج تصميم حائط السند
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetainingWallDetailsScreen(
    wallId: Int,
    navController: NavController,
    viewModel: RetainingWallDesignViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedWall = uiState.selectedWall
    var showEquations by remember { mutableStateOf(false) }

    if (selectedWall == null) {
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
                title = { Text("تفاصيل تصميم حائط السند") },
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
                WallSafetyStatusCard(wall = selectedWall)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "معلومات الحائط",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ResultRow("اسم الحائط", selectedWall.wallName)
                        ResultRow("نوع الحائط", selectedWall.wallType.toString())
                        ResultRow("ارتفاع الحائط", "%.2f م".format(selectedWall.wallHeightM))
                        ResultRow("ارتفاع التربة خلفه", "%.2f م".format(selectedWall.soilHeightBehindM))
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "خصائص التربة",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ResultRow(
                            "الوزن الحجمي",
                            "%.2f kN/m³".format(selectedWall.soilUnitWeightKNM3)
                        )
                        ResultRow(
                            "زاوية الاحتكاك",
                            "%.1f°".format(selectedWall.soilFrictionAngleDeg)
                        )
                        ResultRow(
                            "التماسك",
                            "%.2f kN/m²".format(selectedWall.soilCohesionKNM2)
                        )
                        ResultRow(
                            "تحمل التربة",
                            "%.2f kN/m²".format(selectedWall.bearingCapacityKNM2)
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "نتائج التحليل",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ResultRow(
                            "الضغط النشط",
                            "%.2f kN/m".format(selectedWall.activePressureKNM)
                        )
                        ResultRow(
                            "الضغط السلبي",
                            "%.2f kN/m".format(selectedWall.passivePressureKNM)
                        )
                        ResultRow(
                            "قوة أفقية كلية",
                            "%.2f kN".format(selectedWall.totalHorizontalForceKN)
                        )
                        ResultRow(
                            "حمل رأسي كلي",
                            "%.2f kN".format(selectedWall.totalVerticalLoadKN)
                        )
                        ResultRow(
                            "عزم الانقلاب",
                            "%.2f kN.m".format(selectedWall.overturbingMomentKNM)
                        )
                        ResultRow(
                            "عزم المقاومة",
                            "%.2f kN.m".format(selectedWall.resistanceMomentKNM)
                        )
                    }
                }
            }

            item {
                SafetyFactorsCard(wall = selectedWall)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "ضغوط الأساس",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ResultRow(
                            "الضغط الأقصى (الطرف الأمامي)",
                            "%.2f kN/m²".format(selectedWall.maxToePressureKNM2)
                        )
                        ResultRow(
                            "الضغط الأدنى (الطرف الخلفي)",
                            "%.2f kN/m²".format(selectedWall.minHeelPressureKNM2)
                        )
                        ResultRow(
                            "الانحراف عن المركز",
                            "%.2f م".format(selectedWall.eccentricityM)
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "نتائج التسليح",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ResultRow(
                            "فولاذ الساق",
                            "%.2f mm²".format(selectedWall.stemSteelAreaMM2)
                        )
                        ResultRow(
                            "فولاذ القاعدة",
                            "%.2f mm²".format(selectedWall.baseSteelAreaMM2)
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
                            "التكلفة الإجمالية",
                            "%.2f %s".format(selectedWall.totalCost, selectedWall.unitCurrency)
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
fun WallSafetyStatusCard(wall: RetainingWall) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (wall.isSafeOverturning)
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
                    Text("الانقلاب", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "%.2f (آمن إذا > 1.5)".format(wall.factorOfSafetyOverturning),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(
                    imageVector = if (wall.isSafeOverturning)
                        Icons.Filled.CheckCircle
                    else
                        Icons.Filled.Error,
                    contentDescription = null,
                    tint = if (wall.isSafeOverturning)
                        Color(0xFF2E7D32)
                    else
                        Color(0xFFC62828)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (wall.isSafeSliding)
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
                    Text("الانزلاق", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "%.2f (آمن إذا > 1.5)".format(wall.factorOfSafetySliding),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(
                    imageVector = if (wall.isSafeSliding)
                        Icons.Filled.CheckCircle
                    else
                        Icons.Filled.Error,
                    contentDescription = null,
                    tint = if (wall.isSafeSliding)
                        Color(0xFF2E7D32)
                    else
                        Color(0xFFC62828)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (wall.isSafeBearing)
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
                        text = if (wall.isSafeBearing) "آمن" else "غير آمن",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(
                    imageVector = if (wall.isSafeBearing)
                        Icons.Filled.CheckCircle
                    else
                        Icons.Filled.Error,
                    contentDescription = null,
                    tint = if (wall.isSafeBearing)
                        Color(0xFF2E7D32)
                    else
                        Color(0xFFC62828)
                )
            }
        }
    }
}

@Composable
fun SafetyFactorsCard(wall: RetainingWall) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "معاملات الأمان",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            ResultRow(
                "معامل الأمان للانقلاب",
                "%.2f".format(wall.factorOfSafetyOverturning)
            )
            ResultRow(
                "معامل الأمان للانزلاق",
                "%.2f".format(wall.factorOfSafetySliding)
            )
        }
    }
}