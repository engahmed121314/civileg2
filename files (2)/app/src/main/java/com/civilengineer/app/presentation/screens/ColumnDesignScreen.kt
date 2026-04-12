package com.civilengineer.app.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.civilengineer.app.data.models.ColumnType
import com.civilengineer.app.presentation.viewmodel.ColumnDesignViewModel

/**
 * شاشة تصميم العمود
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnDesignScreen(
    navController: NavController,
    viewModel: ColumnDesignViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // متغيرات الإدخال
    var columnName by remember { mutableStateOf("عمود ��ديد") }
    var lengthM by remember { mutableStateOf("0.5") }
    var widthM by remember { mutableStateOf("0.5") }
    var heightM by remember { mutableStateOf("3.0") }
    var axialLoadKN by remember { mutableStateOf("500") }
    var bendingMomentKNM by remember { mutableStateOf("50") }
    var selectedConcrete by remember { mutableStateOf("C30") }
    var selectedSteel by remember { mutableStateOf("S400") }
    var selectedCode by remember { mutableStateOf(CodeType.EGYPTIAN) }
    var selectedColumnType by remember { mutableStateOf(ColumnType.RECTANGULAR) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تصميم عمود خرساني") },
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SectionHeader("معلومات العمود")
            }

            item {
                TextInputField(
                    label = "اس�� العمود",
                    value = columnName,
                    onValueChange = { columnName = it }
                )
            }

            item {
                SectionHeader("الأبعاد")
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
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
                    label = "الارتفاع (م)",
                    value = heightM,
                    onValueChange = { heightM = it },
                    keyboardType = KeyboardType.Decimal
                )
            }

            item {
                SectionHeader("الأحمال والعزوم")
            }

            item {
                TextInputField(
                    label = "الحمل المحوري (kN)",
                    value = axialLoadKN,
                    onValueChange = { axialLoadKN = it },
                    keyboardType = KeyboardType.Decimal
                )
            }

            item {
                TextInputField(
                    label = "عزم الانحناء (kN.m)",
                    value = bendingMomentKNM,
                    onValueChange = { bendingMomentKNM = it },
                    keyboardType = KeyboardType.Decimal
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
                SectionHeader("نوع العمود")
            }

            item {
                ColumnTypeSelector(
                    selected = selectedColumnType,
                    onSelectionChange = { selectedColumnType = it }
                )
            }

            item {
                Button(
                    onClick = {
                        viewModel.performDesignCalculation(
                            lengthM = lengthM.toDoubleOrNull() ?: 0.5,
                            widthM = widthM.toDoubleOrNull() ?: 0.5,
                            heightM = heightM.toDoubleOrNull() ?: 3.0,
                            axialLoadKN = axialLoadKN.toDoubleOrNull() ?: 0.0,
                            bendingMomentKNM = bendingMomentKNM.toDoubleOrNull() ?: 0.0,
                            concreteGrade = selectedConcrete,
                            steelGrade = selectedSteel,
                            codeType = selectedCode,
                            columnType = selectedColumnType,
                            columnName = columnName
                        )
                        navController.navigate("column_details/${uiState.selectedColumn?.id ?: 0}")
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
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun TextInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        singleLine = true
    )
}

@Composable
fun DropdownField(
    label: String,
    options: List<String>,
    selected: String,
    onSelectionChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        TextField(
            readOnly = true,
            value = selected,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .height(56.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelectionChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun CodeTypeSelector(
    selected: CodeType,
    onSelectionChange: (CodeType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        CodeType.values().forEach { codeType ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == codeType,
                    onClick = { onSelectionChange(codeType) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (codeType) {
                        CodeType.EGYPTIAN -> "الكود المصري"
                        CodeType.SAUDI -> "الكود السعودي"
                        CodeType.AMERICAN -> "الكود الأمريكي (ACI)"
                    }
                )
            }
        }
    }
}

@Composable
fun ColumnTypeSelector(
    selected: ColumnType,
    onSelectionChange: (ColumnType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        ColumnType.values().forEach { columnType ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == columnType,
                    onClick = { onSelectionChange(columnType) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (columnType) {
                        ColumnType.RECTANGULAR -> "عمود مستطيل"
                        ColumnType.CIRCULAR -> "عمود دائري"
                        ColumnType.SQUARE -> "عمود مربع"
                        ColumnType.COMPOSITE -> "عمود مركب"
                    }
                )
            }
        }
    }
}