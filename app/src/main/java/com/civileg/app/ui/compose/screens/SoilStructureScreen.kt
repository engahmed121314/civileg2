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
import com.civileg.app.ui.compose.components.drawings.ProfessionalSoilStructureDrawing
import com.civileg.app.viewmodel.SoilStructureViewModel
import com.civileg.app.ui.defaultDesignCode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoilStructureScreen(
    onNavigateBack: () -> Unit,
    viewModel: SoilStructureViewModel = hiltViewModel()
) {
    val uiState by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()

    var foundationLengthMm by remember { mutableStateOf("3000") }
    var foundationWidthMm by remember { mutableStateOf("2000") }
    var foundationThicknessMm by remember { mutableStateOf("500") }
    var columnLoadKN by remember { mutableStateOf("1200") }
    var momentKNm by remember { mutableStateOf("50") }
    var soilBearingCapacityKpa by remember { mutableStateOf("150") }
    var soilUnitWeightKNm3 by remember { mutableStateOf("18") }
    var frictionAngleDeg by remember { mutableStateOf("30") }
    var fcu by remember { mutableStateOf("25") }
    var fy by remember { mutableStateOf("360") }
    var embedmentDepthMm by remember { mutableStateOf("1500") }
    var selectedCode by remember { mutableStateOf(defaultDesignCode()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تفاعل المنشأ مع التربة", fontWeight = FontWeight.Bold) },
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
            DesignSectionHeader(title = "بيانات الأساس والتربة")
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                item {
                    DesignInputField(value = foundationLengthMm, label = "طول الأساس (mm)", onValueChange = { foundationLengthMm = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                item {
                    DesignInputField(value = foundationWidthMm, label = "عرض الأساس (mm)", onValueChange = { foundationWidthMm = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                item {
                    DesignInputField(value = foundationThicknessMm, label = "سماكة الأساس (mm)", onValueChange = { foundationThicknessMm = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                item {
                    DesignInputField(value = columnLoadKN, label = "حمل العمود (kN)", onValueChange = { columnLoadKN = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                item {
                    DesignInputField(value = momentKNm, label = "العزم (kN.m)", onValueChange = { momentKNm = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                item {
                    DesignInputField(value = soilBearingCapacityKpa, label = "سعة التربة المسموحة (kPa)", onValueChange = { soilBearingCapacityKpa = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                item {
                    DesignInputField(value = soilUnitWeightKNm3, label = "الوزن النوعي للتربة (kN/m³)", onValueChange = { soilUnitWeightKNm3 = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                item {
                    DesignInputField(value = frictionAngleDeg, label = "زاوية الاحتكاك (درجة)", onValueChange = { frictionAngleDeg = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                item {
                    DesignInputField(value = fcu, label = "مقاومة الخرسانة fcu (MPa)", onValueChange = { fcu = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                item {
                    DesignInputField(value = fy, label = "إجهاد الخضوع fy (MPa)", onValueChange = { fy = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                item {
                    DesignInputField(value = embedmentDepthMm, label = "عمق الدفن (mm)", onValueChange = { embedmentDepthMm = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
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
                            foundationLengthMm = foundationLengthMm.toDoubleOrNull() ?: 3000.0,
                            foundationWidthMm = foundationWidthMm.toDoubleOrNull() ?: 2000.0,
                            foundationThicknessMm = foundationThicknessMm.toDoubleOrNull() ?: 500.0,
                            columnLoadKN = columnLoadKN.toDoubleOrNull() ?: 1200.0,
                            momentKNm = momentKNm.toDoubleOrNull() ?: 50.0,
                            soilBearingCapacityKpa = soilBearingCapacityKpa.toDoubleOrNull() ?: 150.0,
                            soilUnitWeightKNm3 = soilUnitWeightKNm3.toDoubleOrNull() ?: 18.0,
                            frictionAngleDeg = frictionAngleDeg.toDoubleOrNull() ?: 30.0,
                            fcu = fcu.toDoubleOrNull() ?: 25.0,
                            fy = fy.toDoubleOrNull() ?: 360.0,
                            embedmentDepthMm = embedmentDepthMm.toDoubleOrNull() ?: 1500.0,
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
                            foundationLengthMm = foundationLengthMm.toDoubleOrNull() ?: 3000.0,
                            foundationWidthMm = foundationWidthMm.toDoubleOrNull() ?: 2000.0,
                            foundationThicknessMm = foundationThicknessMm.toDoubleOrNull() ?: 500.0,
                            columnLoadKN = columnLoadKN.toDoubleOrNull() ?: 1200.0,
                            momentKNm = momentKNm.toDoubleOrNull() ?: 50.0,
                            soilBearingCapacityKpa = soilBearingCapacityKpa.toDoubleOrNull() ?: 150.0,
                            soilUnitWeightKNm3 = soilUnitWeightKNm3.toDoubleOrNull() ?: 18.0,
                            frictionAngleDeg = frictionAngleDeg.toDoubleOrNull() ?: 30.0,
                            embedmentDepthMm = embedmentDepthMm.toDoubleOrNull() ?: 1500.0
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("تصدير تقرير PDF برو (3 قطاعات)", fontWeight = FontWeight.Bold)
                }

                DesignSectionHeader(title = "الرسم الهندسي لتفاعل المنشأ مع التربة")

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
                    ProfessionalSoilStructureDrawing(
                        foundationLengthMm = foundationLengthMm.toDoubleOrNull() ?: 3000.0,
                        foundationWidthMm = foundationWidthMm.toDoubleOrNull() ?: 2000.0,
                        foundationThicknessMm = foundationThicknessMm.toDoubleOrNull() ?: 500.0,
                        columnLoadKN = columnLoadKN.toDoubleOrNull() ?: 1200.0,
                        momentKNm = momentKNm.toDoubleOrNull() ?: 50.0,
                        soilBearingCapacityKpa = soilBearingCapacityKpa.toDoubleOrNull() ?: 150.0,
                        maxPressureKpa = res.bearing.maxPressureKpa,
                        minPressureKpa = res.bearing.minPressureKpa,
                        averagePressureKpa = res.bearing.averagePressureKpa,
                        settlementMm = res.settlement.totalSettlementMm,
                        overturningFS = res.stability.factorOfSafetyOverturning,
                        slidingFS = res.stability.factorOfSafetySliding,
                        bearingFS = res.stability.factorOfSafetyBearing,
                        cover = 75.0,
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
                        label = "أقصى ضغط تربة",
                        value = "${res.bearing.maxPressureKpa.toInt()} kPa"
                    )
                    DesignResultRow(
                        label = "أقل ضغط تربة",
                        value = "${res.bearing.minPressureKpa.toInt()} kPa"
                    )
                    DesignResultRow(
                        label = "الهبوط",
                        value = "${res.settlement.totalSettlementMm.toInt()} مم"
                    )
                    DesignResultRow(
                        label = "عامل الأمان الانقلاب",
                        value = "%.2f".format(res.stability.factorOfSafetyOverturning)
                    )
                    DesignResultRow(
                        label = "عامل الأمان الانزلاق",
                        value = "%.2f".format(res.stability.factorOfSafetySliding)
                    )
                    DesignResultRow(
                        label = "عامل الأمان التحمل",
                        value = "%.2f".format(res.stability.factorOfSafetyBearing)
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
