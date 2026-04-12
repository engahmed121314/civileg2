package com.civilengineer.app.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.civilengineer.app.data.models.CodeType
import com.civilengineer.app.data.models.WallType
import com.civilengineer.app.presentation.viewmodel.RetainingWallDesignViewModel

/**
 * شاشة تصميم حائط السند
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetainingWallDesignScreen(
    navController: NavController,
    viewModel: RetainingWallDesignViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var wallName by remember { mutableStateOf("حائط سند جديد") }
    var wallHeightM by remember { mutableStateOf("5.0") }
    var soilHeightBehindM by remember { mutableStateOf("4.5") }
    var soilUnitWeight by remember { mutableStateOf("18.0") }
    var soilFrictionAngle by remember { mutableStateOf("30.0") }
    var soilCohesion by remember { mutableStateOf("10.0") }
    var bearingCapacity by remember { mutableStateOf("200.0") }
    var selectedConcrete by remember { mutableStateOf("C30") }
    var selectedSteel by remember { mutableStateOf("S400") }
    var selectedCode by remember { mutableStateOf(CodeType.EGYPTIAN) }
    var selectedWallType by remember { mutableStateOf(WallType.CANTILEVER_WALL) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تصميم حائط سند") },
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
                SectionHeader("معلومات الحائط")
            }

            item {
                TextInputField(
                    label = "اسم الحائط",
                    value = wallName,
                    onValueChange = { wallName = it }
                )
            }

            item {
                SectionHeader("ارتفاعات")
            }

            item {
                TextInputField(
                    label = "ارتفاع الحائط (م)",
                    value = wallHeightM,
                    onValueChange = { wallHeightM = it },
                    keyboardType = KeyboardType.Decimal
                )
            }

            item {
                TextInputField(
                    label = "ارتفاع التربة خلف الحائط (م)",
                    value = soilHeightBehindM,
                    onValueChange = { soilHeightBehindM = it },
                    keyboardType = KeyboardType.Decimal
                )
            }

            item {
                SectionHeader("خصائص التربة")
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextInputField(
                        label = "الوزن الحجمي (kN/m³)",
                        value = soilUnitWeight,
                        onValueChange = { soilUnitWeight = it },
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f)
                    )
                    TextInputField(
                        label = "زاوية الاحتكاك (°)",
                        value = soilFrictionAngle,
                        onValueChange = { soilFrictionAngle = it },
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextInputField(
                        label = "التماسك (kN/m²)",
                        value = soilCohesion,
                        onValueChange = { soilCohesion = it },
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f)
                    )
                    TextInputField(
                        label = "تحمل التربة (kN/m²)",
                        value = bearingCapacity,
                        onValueChange = { bearingCapacity = it },
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                SectionHeader("نوع الحائط")
            }

            item {
                WallTypeSelector(
                    selected = selectedWallType,
                    onSelectionChange = { selectedWallType = it }
                )
            }

            item {
                SectionHeader("خصائص المواد")
            }

            item {
                DropdownField(
                    label = "درجة الخرسانة",
                    options = listOf("C20", "C25", "C30", "C35", "C40"),
                    selected = selectedConcrete,
                    onSelectionChange = { selectedConcrete = it }
                )
            }

            item {
                DropdownField(
                    label = "درجة الفولاذ",
                    options = listOf("S235", "S355", "S400", "S500"),
                    selected = selectedSteel,
                    onSelectionChange = { selectedSteel = it }
                )
            }

            item {
                SectionHeader("نوع الكود")
            }

            item {
                CodeTypeSelector(
                    selected = selectedCode,
                    onSelectionChange = { selectedCode = it }
                )
            }

            item {
                Button(
                    onClick = {
                        viewModel.performDesignCalculation(
                            wallHeightM = wallHeightM.toDoubleOrNull() ?: 5.0,
                            soilHeightBehindM = soilHeightBehindM.toDoubleOrNull() ?: 4.5,
                            soilUnitWeightKNM3 = soilUnitWeight.toDoubleOrNull() ?: 18.0,
                            soilFrictionAngleDeg = soilFrictionAngle.toDoubleOrNull() ?: 30.0,
                            soilCohesionKNM2 = soilCohesion.toDoubleOrNull() ?: 10.0,
                            bearingCapacityKNM2 = bearingCapacity.toDoubleOrNull() ?: 200.0,
                            concreteGrade = selectedConcrete,
                            steelGrade = selectedSteel,
                            codeType = selectedCode,
                            wallType = selectedWallType,
                            wallName = wallName
                        )
                        navController.navigate("wall_details/${uiState.selectedWall?.id ?: 0}")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.Calculate, contentDescription = "حساب")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حساب التصميم")
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun WallTypeSelector(
    selected: WallType,
    onSelectionChange: (WallType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        WallType.values().forEach { wallType ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == wallType,
                    onClick = { onSelectionChange(wallType) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (wallType) {
                        WallType.GRAVITY_WALL -> "جدار الثقل"
                        WallType.CANTILEVER_WALL -> "جدار كابولي"
                        WallType.SHEET_PILING -> "جدران الألواح"
                        WallType.ANCHORED_WALL -> "جدار مثبت"
                    }
                )
            }
        }
    }
}