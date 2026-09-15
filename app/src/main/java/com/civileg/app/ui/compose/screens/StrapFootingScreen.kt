package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.ui.compose.components.DesignCodeSelectorRow
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.ExportUtils
import com.civileg.app.viewmodel.StrapFootingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrapFootingScreen(
    viewModel: StrapFootingViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()
    val context = LocalContext.current

    var col1Load by remember { mutableStateOf("1200") }
    var col2Load by remember { mutableStateOf("1800") }
    var distance by remember { mutableStateOf("5.0") }
    var col1W by remember { mutableStateOf("300") }
    var col1D by remember { mutableStateOf("600") }
    var col2W by remember { mutableStateOf("300") }
    var col2D by remember { mutableStateOf("700") }
    var soil by remember { mutableStateOf("150") }
    var fcu by remember { mutableStateOf("25") }
    var fy by remember { mutableStateOf("360") }
    var strapWidth by remember { mutableStateOf("400") }
    var selectedCode by remember { mutableStateOf(CalculatorEngine.DesignCode.EGYPTIAN) }
    var barDia by remember { mutableStateOf("18") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_strap_footing), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.footing_inputs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StrapInputField(col1Load, "P1 (Edge) kN", { col1Load = it }, Modifier.weight(1f))
                    StrapInputField(col2Load, "P2 (Int) kN", { col2Load = it }, Modifier.weight(1f))
                }
            }

            item {
                StrapInputField(distance, "Distance C/C (m)", { distance = it }, Modifier.fillMaxWidth())
            }

            item {
                Text("Column 1 (Edge) Dimensions (mm)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StrapInputField(col1W, "Width (b1)", { col1W = it }, Modifier.weight(1f))
                    StrapInputField(col1D, "Depth (a1)", { col1D = it }, Modifier.weight(1f))
                }
            }

            item {
                Text("Column 2 (Internal) Dimensions (mm)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StrapInputField(col2W, "Width (b2)", { col2W = it }, Modifier.weight(1f))
                    StrapInputField(col2D, "Depth (a2)", { col2D = it }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StrapInputField(soil, "Soil Capacity (kPa)", { soil = it }, Modifier.weight(1f))
                    StrapInputField(strapWidth, "Strap Beam Width (mm)", { strapWidth = it }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StrapInputField(fcu, "fcu (MPa)", { fcu = it }, Modifier.weight(1f))
                    StrapInputField(fy, "fy (MPa)", { fy = it }, Modifier.weight(1f))
                }
            }

            item {
                StrapInputField(barDia, "Main Bar Diameter (mm)", { barDia = it }, Modifier.fillMaxWidth())
            }

            item {
                DesignCodeSelectorRow(
                    selectedCode = selectedCode,
                    onCodeSelected = { selectedCode = it }
                )
            }

            item {
                Button(
                    onClick = {
                        viewModel.calculate(
                            col1Load.toDoubleOrNull() ?: 0.0,
                            col2Load.toDoubleOrNull() ?: 0.0,
                            (distance.toDoubleOrNull() ?: 0.0) * 1000.0,
                            col1W.toDoubleOrNull() ?: 0.0,
                            col1D.toDoubleOrNull() ?: 0.0,
                            col2W.toDoubleOrNull() ?: 0.0,
                            col2D.toDoubleOrNull() ?: 0.0,
                            soil.toDoubleOrNull() ?: 0.0,
                            fcu.toDoubleOrNull() ?: 0.0,
                            fy.toDoubleOrNull() ?: 0.0,
                            selectedCode,
                            barDia.toIntOrNull() ?: 18,
                            strapWidth.toDoubleOrNull() ?: 400.0
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        LoadingIndicator(size = 24.dp)
                    } else {
                        Icon(Icons.Default.Calculate, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.calculate))
                    }
                }
            }

            error?.let {
                item {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }
            }

            result?.let { res ->
                item {
                    StrapFootingResultCard(res)
                }
                
                item {
                    Button(
                        onClick = { viewModel.exportToPdf(context) { file ->
                            if (file != null) ExportUtils.openPdf(context, file)
                        } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.export_pdf))
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun StrapFootingResultCard(res: CalculatorEngine.StrapFootingResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Design Results", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            
            HorizontalDivider()
            
            ResultRow("Footing 1 (Edge)", "${res.footing1.width.toInt()} x ${res.footing1.length.toInt()} x ${res.footing1.thickness.toInt()} mm")
            ResultRow("Reinforcement 1", res.footing1.reinforcementBottom.barString)
            
            Spacer(Modifier.height(8.dp))
            
            ResultRow("Footing 2 (Internal)", "${res.footing2.width.toInt()} x ${res.footing2.length.toInt()} x ${res.footing2.thickness.toInt()} mm")
            ResultRow("Reinforcement 2", res.footing2.reinforcementBottom.barString)
            
            Spacer(Modifier.height(8.dp))
            
            ResultRow("Strap Beam", "${res.strapBeamWidth.toInt()} x ${res.strapBeamDepth.toInt()} mm")
            ResultRow("Top Rebar", res.strapTopReinforcement.barString)
            ResultRow("Bottom Rebar", res.strapBottomReinforcement.barString)
            
            Spacer(Modifier.height(8.dp))
            
            ResultRow("Reactions (R1, R2)", "${res.reactions.first.toInt()}, ${res.reactions.second.toInt()} kN")
            
            if (!res.isSafe) {
                Text("⚠ Design Unsafe - Check dimensions", color = Color.Red, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StrapInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun LoadingIndicator(size: Dp) {
    CircularProgressIndicator(
        modifier = Modifier.size(size),
        strokeWidth = 2.dp
    )
}
