package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.domain.calculations.GeneralEstimationEngine
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralEstimationScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("التشطيبات", "المباني", "الأوزان")
    
    // States for export
    var areaPlaster by remember { mutableStateOf("100") }
    var wallAreaBrick by remember { mutableStateOf("50") }
    var brickThk by remember { mutableStateOf("12") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("حسابات سريعة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val exporter = com.civileg.app.utils.exporters.GeneralEstimationPdfExporter(context)
                        val plaster = GeneralEstimationEngine.calculatePlaster(areaPlaster.toDoubleOrNull() ?: 0.0)
                        val paint = GeneralEstimationEngine.calculatePaint(areaPlaster.toDoubleOrNull() ?: 0.0)
                        val bricks = GeneralEstimationEngine.calculateBricks(wallAreaBrick.toDoubleOrNull() ?: 0.0, brickThk.toDoubleOrNull() ?: 12.0)
                        
                        val file = exporter.exportToDownload(plaster, paint, bricks, "My Estimation")
                        com.civileg.app.utils.ExportUtils.openPdf(context, file)
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 14.sp) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    0 -> PlasterAndPaintSection()
                    1 -> BrickworkSection()
                    2 -> WeightsSection()
                }
            }
        }
    }
}

@Composable
private fun PlasterAndPaintSection() {
    var area by remember { mutableStateOf("100") }
    val plaster = GeneralEstimationEngine.calculatePlaster(area.toDoubleOrNull() ?: 0.0)
    val paint = GeneralEstimationEngine.calculatePaint(area.toDoubleOrNull() ?: 0.0)

    EstimationCard(title = "المحارة والدهانات", icon = Icons.Default.FormatPaint) {
        OutlinedTextField(
            value = area,
            onValueChange = { area = it },
            label = { Text("المساحة (م2)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        ResultRow("أسمنت محارة:", "${plaster.cementBags} شكارة")
        ResultRow("رمل محارة:", "${String.format(Locale.US, "%.2f", plaster.sandM3)} م3")
        ResultRow("دهانات (3 أوجه):", "${String.format(Locale.US, "%.1f", paint.liters)} لتر (~${paint.gallons} جالون)")
    }
}

@Composable
private fun BrickworkSection() {
    var wallArea by remember { mutableStateOf("50") }
    var thickness by remember { mutableStateOf("12") }
    val brick = GeneralEstimationEngine.calculateBricks(
        wallArea.toDoubleOrNull() ?: 0.0,
        thickness.toDoubleOrNull() ?: 12.0
    )

    EstimationCard(title = "أعمال المباني", icon = Icons.Default.Foundation) {
        OutlinedTextField(
            value = wallArea,
            onValueChange = { wallArea = it },
            label = { Text("مساحة الحائط (م2)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = thickness,
            onValueChange = { thickness = it },
            label = { Text("سمك الحائط (12 أو 25 سم)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        ResultRow("عدد الطوب:", "${brick.totalBricks} طوبة")
        ResultRow("أسمنت مباني:", "${brick.cementBags} شكارة")
        ResultRow("رمل مباني:", "${String.format(Locale.US, "%.2f", brick.sandM3)} م3")
    }
}

@Composable
private fun WeightsSection() {
    EstimationCard(title = "جدول أوزان الحديد", icon = Icons.Default.LineWeight) {
        val diameters = listOf(8, 10, 12, 16, 18, 20, 22, 25)
        diameters.forEach { dia ->
            val wt = GeneralEstimationEngine.getRebarWeightPerMeter(dia)
            ResultRow("قطر Φ$dia مم:", "${String.format(Locale.US, "%.3f", wt)} كجم/م")
        }
    }
    
    Spacer(modifier = Modifier.height(8.dp))
    
    EstimationCard(title = "كثافة المواد", icon = Icons.Default.Landscape) {
        listOf("خرسانة", "حديد", "طوب", "رمل").forEach { mat ->
            val density = GeneralEstimationEngine.getMaterialDensity(mat)
            ResultRow(mat, "${density.toInt()} كجم/م3")
        }
    }
}

@Composable
private fun EstimationCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}
