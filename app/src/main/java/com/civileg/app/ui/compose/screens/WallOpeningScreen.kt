package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.ui.compose.components.DesignInputField
import com.civileg.app.ui.compose.components.DesignResultRow
import com.civileg.app.ui.compose.components.DesignSectionHeader
import com.civileg.app.ui.compose.components.DesignStatusBanner
import com.civileg.app.ui.compose.components.drawings.DrawingResultSummary
import com.civileg.app.ui.compose.components.drawings.ProfessionalWallOpeningDrawing
import com.civileg.app.viewmodel.WallOpeningViewModel
import com.civileg.app.ui.defaultDesignCode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallOpeningScreen(
    onNavigateBack: () -> Unit,
    viewModel: WallOpeningViewModel = hiltViewModel()
) {
    val uiState by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()

    var wallHeightMm by remember { mutableStateOf("3000") }
    var wallThicknessMm by remember { mutableStateOf("250") }
    var openingWidthMm by remember { mutableStateOf("1000") }
    var openingHeightMm by remember { mutableStateOf("2000") }
    var openingFromBaseMm by remember { mutableStateOf("800") }
    var wallLengthMm by remember { mutableStateOf("5000") }
    var fcu by remember { mutableStateOf("25") }
    var fy by remember { mutableStateOf("360") }
    var loadAboveOpeningKNm by remember { mutableStateOf("20") }
    var selectedCode by remember { mutableStateOf(defaultDesignCode()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تصميم فتحات الحوائط", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "عودة")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            DesignSectionHeader(title = "بيانات الفتحة والحائط")

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                item {
                    DesignInputField(value = wallHeightMm, label = "ارتفاع الحائط (mm)", onValueChange = { wallHeightMm = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                item {
                    DesignInputField(value = wallThicknessMm, label = "سماكة الحائط (mm)", onValueChange = { wallThicknessMm = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                item {
                    DesignInputField(value = openingWidthMm, label = "عرض الفتحة (mm)", onValueChange = { openingWidthMm = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                item {
                    DesignInputField(value = openingHeightMm, label = "ارتفاع الفتحة (mm)", onValueChange = { openingHeightMm = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                item {
                    DesignInputField(value = openingFromBaseMm, label = "ارتفاع الفتحة من القاعدة (mm)", onValueChange = { openingFromBaseMm = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                item {
                    DesignInputField(value = wallLengthMm, label = "طول الحائط (mm)", onValueChange = { wallLengthMm = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                item {
                    DesignInputField(value = fcu, label = "fcu (MPa)", onValueChange = { fcu = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                item {
                    DesignInputField(value = fy, label = "fy (MPa)", onValueChange = { fy = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                item {
                    DesignInputField(value = loadAboveOpeningKNm, label = "الحمل فوق الفتحة (kN/m)", onValueChange = { loadAboveOpeningKNm = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.calculate(
                            wallHeightMm = wallHeightMm.toDoubleOrNull() ?: 3000.0,
                            wallThicknessMm = wallThicknessMm.toDoubleOrNull() ?: 250.0,
                            openingWidthMm = openingWidthMm.toDoubleOrNull() ?: 1000.0,
                            openingHeightMm = openingHeightMm.toDoubleOrNull() ?: 2000.0,
                            openingFromBaseMm = openingFromBaseMm.toDoubleOrNull() ?: 800.0,
                            wallLengthMm = wallLengthMm.toDoubleOrNull() ?: 5000.0,
                            fcu = fcu.toDoubleOrNull() ?: 25.0,
                            fy = fy.toDoubleOrNull() ?: 360.0,
                            loadAboveOpeningKNm = loadAboveOpeningKNm.toDoubleOrNull() ?: 20.0,
                            code = selectedCode
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("حساب")
                }
                OutlinedButton(
                    onClick = { viewModel.reset() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("إعادة تعيين")
                }
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            error?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(err, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                }
            }

            uiState?.let { res ->
                val context = androidx.compose.ui.platform.LocalContext.current

                Button(
                    onClick = {
                        viewModel.exportToPdf(
                            context = context,
                            wallHeightMm = wallHeightMm.toDoubleOrNull() ?: 3000.0,
                            wallThicknessMm = wallThicknessMm.toDoubleOrNull() ?: 250.0,
                            openingWidthMm = openingWidthMm.toDoubleOrNull() ?: 1000.0,
                            openingHeightMm = openingHeightMm.toDoubleOrNull() ?: 2000.0,
                            openingFromBaseMm = openingFromBaseMm.toDoubleOrNull() ?: 800.0,
                            wallLengthMm = wallLengthMm.toDoubleOrNull() ?: 5000.0,
                            fcu = fcu.toDoubleOrNull() ?: 25.0,
                            fy = fy.toDoubleOrNull() ?: 360.0
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("تصدير تقرير PDF برو (3 قطاعات)", fontWeight = FontWeight.Bold)
                }

                DesignSectionHeader(title = "الرسم الهندسي لفتحة الحائط")

                Text(
                    text = androidx.compose.ui.res.stringResource(
                        com.civileg.app.R.string.design_status_not_verified
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth().height(350.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    ProfessionalWallOpeningDrawing(
                        wallHeightMm = wallHeightMm.toDoubleOrNull() ?: 3000.0,
                        wallThicknessMm = wallThicknessMm.toDoubleOrNull() ?: 250.0,
                        openingWidthMm = openingWidthMm.toDoubleOrNull() ?: 1000.0,
                        openingHeightMm = openingHeightMm.toDoubleOrNull() ?: 2000.0,
                        openingFromBaseMm = openingFromBaseMm.toDoubleOrNull() ?: 800.0,
                        wallLengthMm = wallLengthMm.toDoubleOrNull() ?: 5000.0,
                        headerBarDia = 16.0,
                        headerBarCount = 3,
                        sillBarDia = 12.0,
                        sillBarCount = 2,
                        verticalBarDia = 12.0,
                        verticalBarCount = 2,
                        cover = 25.0,
                        viewMode = 0,
                        designCode = selectedCode,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                DesignSectionHeader(title = "النتيجة")
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DesignResultRow(
                        label = "حالة التحقق",
                        value = androidx.compose.ui.res.stringResource(
                            com.civileg.app.R.string.design_status_not_verified
                        )
                    )
                    DesignResultRow(
                        label = "أقصى إجهاد",
                        value = "${"%.2f".format(res.maxStressMpa)} MPa"
                    )
                    DesignResultRow(
                        label = "تسليح الفتحة (مم²)",
                        value = "${"%.0f".format(res.openingReinforcement.areaProvidedMm2)}"
                    )
                    DesignResultRow(
                        label = "تسليح الكمر العلوي (مم²)",
                        value = "${"%.0f".format(res.headerBeamReinforcement.areaProvidedMm2)}"
                    )
                    DesignResultRow(
                        label = "تسليح العتبة (مم²)",
                        value = "${"%.0f".format(res.sillReinforcement.areaProvidedMm2)}"
                    )
                }

                res.codeNotes.forEach { noteText ->
                    Text(noteText, style = MaterialTheme.typography.bodySmall)
                }

                res.warnings.forEach { warningText ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(warningText, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
