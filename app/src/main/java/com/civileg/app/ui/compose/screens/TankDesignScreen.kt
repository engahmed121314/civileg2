package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.CalculatorEngine.TankType
import com.civileg.app.utils.CalculatorEngine.DesignCode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TankDesignScreen(
    calculator: CalculatorEngine,
    onResultGenerated: (CalculatorEngine.TankResult) -> Unit
) {
    var capacity by remember { mutableStateOf("50") }
    var height by remember { mutableStateOf("3.0") }
    var fcu by remember { mutableStateOf("30") }
    var fy by remember { mutableStateOf("400") }
    var selectedType by remember { mutableStateOf(TankType.RECTANGULAR_GROUND) }
    var selectedCode by remember { mutableStateOf(DesignCode.EGYPTIAN) }
    
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text("تصميم الخزانات المائية", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))

        // Tank Type Dropdown
        var expandedType by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expandedType, onExpandedChange = { expandedType = !expandedType }) {
            OutlinedTextField(
                value = selectedType.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("نوع الخزان") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                TankType.values().forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.displayName) },
                        onClick = {
                            selectedType = type
                            expandedType = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(value = capacity, onValueChange = { capacity = it }, label = { Text("السعة المطلوبة (m³)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text("ارتفاع المياه (m)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = fcu, onValueChange = { fcu = it }, label = { Text("إجهاد الخرسانة fcu (MPa)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = fy, onValueChange = { fy = it }, label = { Text("إجهاد الحديد fy (MPa)") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val res = calculator.designTank(
                    type = selectedType,
                    capacity = capacity.toDoubleOrNull() ?: 50.0,
                    height = height.toDoubleOrNull() ?: 3.0,
                    fcu = fcu.toDoubleOrNull() ?: 30.0,
                    fy = fy.toDoubleOrNull() ?: 400.0,
                    code = selectedCode
                )
                onResultGenerated(res)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("إجراء الحسابات وتصميم الخزان", fontSize = 16.sp)
        }
    }
}
