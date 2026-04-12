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
import com.civilengineer.app.data.models.FootingType
import com.civilengineer.app.presentation.viewmodel.FootingDesignViewModel

/**
 * شاشة تصميم القاعدة الخرسانية
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FootingDesignScreen(
    navController: NavController,
    viewModel: FootingDesignViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var footingName by remember { mutableStateOf("قاعدة جديدة") }
    var columnLoad by remember { mutableStateOf("1000") }
    var soilBearingCapacity by remember { mutableStateOf("250") }
    var selectedConcrete by remember { mutableStateOf("C30") }
    var selectedSteel by remember { mutableStateOf("S400") }
    var selectedCode by remember { mutableStateOf(CodeType.EGYPTIAN) }
    var selectedFootingType by remember { mutableStateOf(FootingType.ISOLATED_RECTANGULAR) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تصميم قاعدة خرسانية") },
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
                SectionHeader("معلومات القاعدة")
            }

            item {
                TextInputField(
                    label = "اسم القاعدة",
                    value = footingName,
                    onValueChange = { footingName = it }
                )
            }

            item {
                SectionHeader("الأحمال والتحمل")
            }

            item {
                TextInputField(
                    label = "حمل العمود (kN)",
                    value = columnLoad,
                    onValueChange = { columnLoad = it },
                    keyboardType = KeyboardType.Decimal
                )
            }

            item {
                TextInputField(
                    label = "تحمل التربة (kN/m²)",
                    value = soilBearingCapacity,
                    onValueChange = { soilBearingCapacity = it },
                    keyboardType = KeyboardType.Decimal
                )
            }

            item {
                SectionHeader("نوع القاعدة")
            }

            item {
                FootingTypeSelector(
                    selected = selectedFootingType,
                    onSelectionChange = { selectedFootingType = it }
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
                            columnLoadKN = columnLoad.toDoubleOrNull() ?: 1000.0,
                            soilBearingCapacityKNM2 = soilBearingCapacity.toDoubleOrNull() ?: 250.0,
                            concreteGrade = selectedConcrete,
                            steelGrade = selectedSteel,
                            codeType = selectedCode,
                            footingType = selectedFootingType,
                            footingName = footingName
                        )
                        navController.navigate("footing_details/${uiState.selectedFooting?.id ?: 0}")
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
fun FootingTypeSelector(
    selected: FootingType,
    onSelectionChange: (FootingType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        FootingType.values().forEach { footingType ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == footingType,
                    onClick = { onSelectionChange(footingType) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (footingType) {
                        FootingType.ISOLATED_RECTANGULAR -> "قاعدة معزولة مستطيلة"
                        FootingType.ISOLATED_CIRCULAR -> "قاعدة معزولة دائرية"
                        FootingType.ISOLATED_SQUARE -> "قاعدة معزولة مربعة"
                        FootingType.STRIP_FOOTING -> "قاعدة شريطية"
                        FootingType.COMBINED -> "قاعدة مشتركة"
                        FootingType.PILED -> "قاعدة على خوازيق"
                    }
                )
            }
        }
    }
}