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
import com.civilengineer.app.data.models.SlabDesign
import com.civilengineer.app.data.models.SlabType
import com.civilengineer.app.data.models.SupportType
import com.civilengineer.app.presentation.viewmodel.SlabDesignViewModel

/**
 * شاشة تصميم البلاطة الخرسانية
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlabDesignScreen(
    navController: NavController,
    viewModel: SlabDesignViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var slabName by remember { mutableStateOf("بلاطة جديدة") }
    var lengthM by remember { mutableStateOf("5.0") }
    var widthM by remember { mutableStateOf("4.0") }
    var thicknessMM by remember { mutableStateOf("150") }
    var liveLoadKNM2 by remember { mutableStateOf("5.0") }
    var deadLoadKNM2 by remember { mutableStateOf("3.5") }
    var selectedConcrete by remember { mutableStateOf("C30") }
    var selectedSteel by remember { mutableStateOf("S400") }
    var selectedCode by remember { mutableStateOf(CodeType.EGYPTIAN) }
    var selectedSlabType by remember { mutableStateOf(SlabType.ONE_WAY) }
    var selectedSupportType by remember { mutableStateOf(SupportType.SIMPLY_SUPPORTED) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تصميم بلاطة خرسانية") },
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
                SectionHeader("معلومات البلاطة")
            }

            item {
                TextInputField(
                    label = "اسم البلاطة",
                    value = slabName,
                    onValueChange = { slabName = it }
                )
            }

            item {
                SectionHeader("الأبعاد")
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextInputField(
                        label = "الطول (م)",
                        value = lengthM,
                        onValueChange = { lengthM = it },
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f)
                    )
                    TextInputField(
                        label = "العرض (م)",
                        value = widthM,
                        onValueChange = { widthM = it },
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                TextInputField(
                    label = "السمك (ملم)",
                    value = thicknessMM,
                    onValueChange = { thicknessMM = it },
                    keyboardType = KeyboardType.Decimal
                )
            }

            item {
                SectionHeader("الأحمال")
            }

            item {
                TextInputField(
                    label = "الحمل الميت (kN/m²)",
                    value = deadLoadKNM2,
                    onValueChange = { deadLoadKNM2 = it },
                    keyboardType = KeyboardType.Decimal
                )
            }

            item {
                TextInputField(
                    label = "الحمل الحي (kN/m²)",
                    value = liveLoadKNM2,
                    onValueChange = { liveLoadKNM2 = it },
                    keyboardType = KeyboardType.Decimal
                )
            }

            item {
                SectionHeader("نوع البلاطة")
            }

            item {
                SlabTypeSelector(
                    selected = selectedSlabType,
                    onSelectionChange = { selectedSlabType = it }
                )
            }

            item {
                SectionHeader("نوع الاستناد")
            }

            item {
                SupportTypeSelector(
                    selected = selectedSupportType,
                    onSelectionChange = { selectedSupportType = it }
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
                            lengthM = lengthM.toDoubleOrNull() ?: 5.0,
                            widthM = widthM.toDoubleOrNull() ?: 4.0,
                            thicknessMM = thicknessMM.toIntOrNull() ?: 150,
                            liveLoadKNM2 = liveLoadKNM2.toDoubleOrNull() ?: 5.0,
                            deadLoadKNM2 = deadLoadKNM2.toDoubleOrNull() ?: 3.5,
                            concreteGrade = selectedConcrete,
                            steelGrade = selectedSteel,
                            codeType = selectedCode,
                            slabType = selectedSlabType,
                            supportType = selectedSupportType,
                            slabName = slabName
                        )
                        navController.navigate("slab_details/${uiState.selectedSlab?.id ?: 0}")
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
fun SlabTypeSelector(
    selected: SlabType,
    onSelectionChange: (SlabType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        SlabType.values().forEach { slabType ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == slabType,
                    onClick = { onSelectionChange(slabType) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (slabType) {
                        SlabType.ONE_WAY -> "بلاطة أحادية الاتجاه"
                        SlabType.TWO_WAY -> "بلاطة ثنائية الاتجاه"
                        SlabType.HOLLOW_CORE -> "بلاطة فراغية"
                        SlabType.RIBBED -> "بلاطة متقاطعة"
                    }
                )
            }
        }
    }
}

@Composable
fun SupportTypeSelector(
    selected: SupportType,
    onSelectionChange: (SupportType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        SupportType.values().forEach { supportType ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == supportType,
                    onClick = { onSelectionChange(supportType) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (supportType) {
                        SupportType.SIMPLY_SUPPORTED -> "مستندة بحرية"
                        SupportType.CANTILEVER -> "كابولي"
                        SupportType.CONTINUOUS -> "مستمرة"
                        SupportType.FIXED -> "مثبتة"
                    }
                )
            }
        }
    }
}