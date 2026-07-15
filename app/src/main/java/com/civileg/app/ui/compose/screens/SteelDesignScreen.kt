package com.civileg.app.ui.compose.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.domain.calculations.ecp.BoltDesignResult
import com.civileg.app.domain.calculations.ecp.BlockShearResult
import com.civileg.app.domain.calculations.ecp.SteelBasePlateDesign
import com.civileg.app.domain.calculations.ecp.SteelConnectionDesign
import com.civileg.app.domain.calculations.ecp.WeldDesignResult
import com.civileg.app.domain.entities.*
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.PdfGenerator
import com.civileg.app.viewmodel.SteelViewModel
import com.civileg.app.ui.compose.components.drawings.ProfessionalSteelDrawing
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen
import java.io.File
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteelDesignScreen(
    viewModel: SteelViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val result by viewModel.result.observeAsState()
    val warehouseResult by viewModel.warehouseResult.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val errorMessage by viewModel.errorMessage.observeAsState()
    
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("المستودعات", "القطاعات", "اللحام", "المسامير", "قواعد معدنية", "وصلات")

    // Handle error messages from ViewModel
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تصميم المنشآت المعدنية", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetResult() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "إعادة تعيين")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index, 
                        onClick = { 
                            if (selectedTab != index) {
                                viewModel.resetResult()
                                selectedTab = index 
                            }
                        }, 
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> SteelWarehouseTab(viewModel, warehouseResult, isLoading)
                1 -> SteelSectionTab(viewModel, result, isLoading)
                2 -> WeldDesignTab(viewModel)
                3 -> BoltDesignTab(viewModel)
                4 -> BasePlateDesignTab()
                5 -> ConnectionDesignTab()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteelWarehouseTab(viewModel: SteelViewModel, result: SteelWarehouseAnalysisResult?, isLoading: Boolean) {
    val context = LocalContext.current
    var span by remember { mutableStateOf("12") }
    var length by remember { mutableStateOf("30") }
    var eaveHeight by remember { mutableStateOf("6") }
    var ridgeHeight by remember { mutableStateOf("7.5") }
    var baySpacing by remember { mutableStateOf("6") }
    var floors by remember { mutableStateOf("1") }
    var selectedCode by remember { mutableStateOf(CalculatorEngine.DesignCode.EGYPTIAN) }

    val currentInputs = remember(span, length, eaveHeight, ridgeHeight, baySpacing, floors, selectedCode) {
        SteelWarehouseInputs(
            span = span.toDoubleOrNull() ?: 12.0,
            length = length.toDoubleOrNull() ?: 30.0,
            eaveHeight = eaveHeight.toDoubleOrNull() ?: 6.0,
            ridgeHeight = ridgeHeight.toDoubleOrNull() ?: 7.5,
            baySpacing = baySpacing.toDoubleOrNull() ?: 6.0,
            numberOfStories = floors.toIntOrNull() ?: 1,
            code = when(selectedCode) {
                CalculatorEngine.DesignCode.EGYPTIAN -> DesignCode.ECP
                CalculatorEngine.DesignCode.SAUDI -> DesignCode.SBC
                else -> DesignCode.ACI
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SectionHeader("🏢 مدخلات تصميم المنشأ المعدني", R.drawable.ic_steel) }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الكود المعتمد", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CalculatorEngine.DesignCode.entries.forEach { code ->
                            FilterChip(
                                selected = selectedCode == code,
                                onClick = { selectedCode = code },
                                label = { Text(code.name, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SteelInputField(span, "البحر Span (m)", { span = it }, Modifier.weight(1f))
                SteelInputField(length, "الطول (m)", { length = it }, Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SteelInputField(eaveHeight, "الارتفاع (m)", { eaveHeight = it }, Modifier.weight(1f))
                SteelInputField(ridgeHeight, "ارتفاع القمة (m)", { ridgeHeight = it }, Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SteelInputField(baySpacing, "الباكية Bay (m)", { baySpacing = it }, Modifier.weight(1f))
                SteelInputField(floors, "الأدوار", { floors = it }, Modifier.weight(1f))
            }
        }

        item {
            Button(
                onClick = { viewModel.calculateWarehouse(currentInputs) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                else Text("تحليل وتصميم المنشأ")
            }
        }

        result?.let { res ->
            item { WarehouseResultSummary(res) }
            
            item { 
                Text("📐 تفاصيل المنظور الإنشائي", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                SteelWarehouseVisualizer(currentInputs, res)
            }

            item {
                Text("📊 تحليل الإجهادات والأحمال", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                StructuralAnalysisVisualizer(currentInputs, res)
            }

            item {
                Button(
                    onClick = {
                        viewModel.exportWarehouseProToPdf(
                            context = context,
                            clientAr = "عميل افتراضي",
                            clientEn = "Default Client",
                            projAr = "مشروع مستودع معدني",
                            projEn = "Steel Warehouse Project"
                        ) { /* handled by VM */ }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إصدار التقرير الفني الهندسي (Pro Drawings)")
                }
            }

            item { Text("📈 نتائج التحليل الحسابي", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
            item { AnalysisDetailCard(res.mainFrame) }

            item { Text("🏗️ تفاصيل القطاعات", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
            item { SectionResultCard("الأعمدة الرئيسية", res.mainFrame.columnSection) }
            item { SectionResultCard("الكمرات (Rafters)", res.mainFrame.rafterSection) }
            item { SecondaryMembersCard(res.secondaryMembers) }
            
            item { Text("🔗 وصلات الربط (Connections)", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
            items(res.connections) { conn ->
                ConnectionDetailCard(conn)
            }

            item { RecommendationsCard(res.recommendations) }
        }
    }
}

@Composable
fun WarehouseResultSummary(res: SteelWarehouseAnalysisResult) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("💰 دراسة الجدوى الاقتصادية", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            SummaryLine("إجمالي وزن الحديد", "%.2f Ton".format(res.totalWeight), isBold = true)
            SummaryLine("التكلفة التقديرية", "%,.0f EGP".format(res.estimatedTotalCost), isBold = true)
            SummaryLine("تكلفة المتر المسطح", "%.0f EGP/m²".format(res.costPerM2))
            SummaryLine("صافي الربح المتوقع", "%,.0f EGP".format(res.netProfit))
            SummaryLine("العائد على الاستثمار (ROI)", "%.1f %%".format(res.roi), isBold = true)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(res.resultsByCode, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        }
    }
}

@Composable
fun StructuralAnalysisVisualizer(inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
    InteractiveDrawingScreen(
        title = "تحليل الإطار الإنشائي",
        subtitle = "Frame Analysis & BMD"
    ) {
        ComposeCanvas(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
            val padding = 60f
            val w = size.width - 2 * padding
            val h = size.height - 2 * padding
            val baseY = size.height - padding

            // Scale calculations
            val scale = (w / inputs.span).toFloat() * 0.75f
            val eh = (inputs.eaveHeight * scale).toFloat()
            val rh = (inputs.ridgeHeight * scale).toFloat()
            val midX = padding + w / 2
            val colLX = (padding + (w - inputs.span * scale) / 2).toFloat()
            val colRX = colLX + (inputs.span * scale).toFloat()

            // ── Grid ──
            val gridColor = Color(0x1AFFFFFF)
            for (i in 0..20) {
                val x = padding + (i * w / 20)
                drawLine(gridColor, Offset(x, padding), Offset(x, size.height - padding), strokeWidth = 0.5f)
            }
            for (i in 0..12) {
                val y = padding + (i * h / 12)
                drawLine(gridColor, Offset(padding, y), Offset(size.width - padding, y), strokeWidth = 0.5f)
            }

            // ── Ground Line with hatching ──
            val groundColor = Color(0xFF4A4A4A)
            drawLine(groundColor, Offset(colLX - 30f, baseY), Offset(colRX + 30f, baseY), strokeWidth = 3f)
            for (i in 0..24) {
                val x = (colLX - 30f) + (i * (colRX - colLX + 60f) / 24)
                drawLine(groundColor, Offset(x, baseY), Offset(x - 8f, baseY + 10f), strokeWidth = 1f)
            }

            // ── Pin Supports ──
            val supportColor = Color(0xFF8A8A8A)
            // Left support
            drawPath(Path().apply {
                moveTo(colLX - 12f, baseY)
                lineTo(colLX + 12f, baseY)
                lineTo(colLX, baseY + 18f)
                close()
            }, supportColor, style = Stroke(2f))
            // Right support
            drawPath(Path().apply {
                moveTo(colRX - 12f, baseY)
                lineTo(colRX + 12f, baseY)
                lineTo(colRX, baseY + 18f)
                close()
            }, supportColor, style = Stroke(2f))

            // ── Main Frame with 3D effect ──
            val frameColor = Color(0xFFB0BEC5)
            val frameShadow = Color(0xFF546E7A)
            val frameHighlight = Color(0xFFECEFF1)
            val memberWidth = 6f

            // Columns (draw shadow, main, highlight for 3D look)
            // Left column
            drawLine(frameShadow, Offset(colLX + 2f, baseY), Offset(colLX + 2f, baseY - eh), strokeWidth = memberWidth + 2f)
            drawLine(frameColor, Offset(colLX, baseY), Offset(colLX, baseY - eh), strokeWidth = memberWidth)
            drawLine(frameHighlight, Offset(colLX - 1f, baseY), Offset(colLX - 1f, baseY - eh), strokeWidth = 1f)

            // Right column
            drawLine(frameShadow, Offset(colRX + 2f, baseY), Offset(colRX + 2f, baseY - eh), strokeWidth = memberWidth + 2f)
            drawLine(frameColor, Offset(colRX, baseY), Offset(colRX, baseY - eh), strokeWidth = memberWidth)
            drawLine(frameHighlight, Offset(colRX - 1f, baseY), Offset(colRX - 1f, baseY - eh), strokeWidth = 1f)

            // Rafters (left)
            drawLine(frameShadow, Offset(colLX + 2f, baseY - eh + 2f), Offset(midX + 1f, baseY - rh + 1f), strokeWidth = memberWidth + 2f)
            drawLine(frameColor, Offset(colLX, baseY - eh), Offset(midX, baseY - rh), strokeWidth = memberWidth)
            // Rafters (right)
            drawLine(frameShadow, Offset(colRX + 2f, baseY - eh + 2f), Offset(midX + 1f, baseY - rh + 1f), strokeWidth = memberWidth + 2f)
            drawLine(frameColor, Offset(colRX, baseY - eh), Offset(midX, baseY - rh), strokeWidth = memberWidth)

            // ── Joint Circles ──
            val jointColor = Color(0xFFFF9800)
            listOf(
                Offset(colLX, baseY - eh),
                Offset(colRX, baseY - eh),
                Offset(midX, baseY - rh)
            ).forEach { joint ->
                drawCircle(Color(0x33FF9800), 14f, joint)
                drawCircle(jointColor, 6f, joint)
                drawCircle(Color.White, 3f, joint)
            }

            // ── Distributed Load Arrows (on rafters) ──
            val loadColor = Color(0xFFEF5350)
            for (i in 0..14) {
                val t = i / 14f
                // Left rafter
                val xL = colLX + t * (midX - colLX)
                val yL = baseY - eh + t * (rh - eh) * (-1f)
                val nxL = -(baseY - eh - (baseY - rh)) // normal direction
                val lenL = (midX - colLX)
                val nxNorm = ((baseY - rh) - (baseY - eh)) / lenL
                val nyNorm = (midX - colLX) / lenL
                val arrowLen = 25f
                drawLine(loadColor, Offset(xL - nxNorm * arrowLen.toFloat(), yL - nyNorm * arrowLen.toFloat()), Offset(xL, yL), strokeWidth = 1.5f)
                // Arrow head
                drawLine(loadColor, Offset(xL - 4f, yL - 5f), Offset(xL, yL), strokeWidth = 1.5f)
                drawLine(loadColor, Offset(xL + 4f, yL - 5f), Offset(xL, yL), strokeWidth = 1.5f)

                // Right rafter
                val xR = midX + t * (colRX - midX)
                val yR = (baseY - rh) + t * ((baseY - eh) - (baseY - rh))
                drawLine(loadColor, Offset(xR + nxNorm * arrowLen.toFloat(), yR - nyNorm * arrowLen.toFloat()), Offset(xR, yR), strokeWidth = 1.5f)
                drawLine(loadColor, Offset(xR - 4f, yR - 5f), Offset(xR, yR), strokeWidth = 1.5f)
                drawLine(loadColor, Offset(xR + 4f, yR - 5f), Offset(xR, yR), strokeWidth = 1.5f)
            }

            // Load label
            drawContext.canvas.nativeCanvas.drawText(
                "w = %.1f kN/m".format((result.mainFrame.maxShear / max(inputs.span, 1.0)).toFloat()),
                (colLX + colRX) / 2f - 60f, baseY - rh - 35f,
                android.graphics.Paint().apply { color = loadColor.toArgbInt(); textSize = 28f; isFakeBoldText = true }
            )

            // ── Bending Moment Diagram (filled) ──
            val momentColor = Color(0xFF42A5F5)
            val maxMoment = result.mainFrame.maxMoment.toFloat()
            val momentScale = min(eh * 0.35f / max(maxMoment, 1f), 0.5f)

            // Left rafter moment (parabolic)
            val momentPath = Path().apply {
                moveTo(colLX, baseY - eh)
                for (i in 0..20) {
                    val t = i / 20f
                    val x = colLX + t * (midX - colLX)
                    val y = baseY - eh + t * (rh - eh) * (-1f)
                    val m = 4 * maxMoment * t * (1 - t)
                    val nxN = ((baseY - rh) - (baseY - eh)) / (midX - colLX)
                    val nyN = (midX - colLX) / (midX - colLX)
                    lineTo(x + nxN * m * momentScale, y + nyN * m * momentScale)
                }
                lineTo(midX, baseY - rh)
                close()
            }
            drawPath(momentPath, momentColor.copy(alpha = 0.2f))
            drawPath(momentPath, momentColor, style = Stroke(2f))

            // Right rafter moment (mirror)
            val momentPathR = Path().apply {
                moveTo(colRX, baseY - eh)
                for (i in 0..20) {
                    val t = i / 20f
                    val x = colRX + t * (midX - colRX)
                    val y = (baseY - eh) + t * ((baseY - rh) - (baseY - eh))
                    val m = 4 * maxMoment * t * (1 - t)
                    val nxN = ((baseY - rh) - (baseY - eh)) / (midX - colRX)
                    val nyN = (midX - colRX) / (midX - colRX)
                    lineTo(x + nxN * m * momentScale, y + nyN * m * momentScale)
                }
                lineTo(midX, baseY - rh)
                close()
            }
            drawPath(momentPathR, momentColor.copy(alpha = 0.2f))
            drawPath(momentPathR, momentColor, style = Stroke(2f))

            // Column moment (triangular)
            val colMoment = maxMoment * 0.3f
            drawPath(Path().apply {
                moveTo(colLX, baseY - eh)
                lineTo(colLX - colMoment * momentScale * 0.5f, baseY - eh / 2)
                lineTo(colLX, baseY)
                close()
            }, momentColor.copy(alpha = 0.15f))
            drawPath(Path().apply {
                moveTo(colLX, baseY - eh)
                lineTo(colLX - colMoment * momentScale * 0.5f, baseY - eh / 2)
                lineTo(colLX, baseY)
            }, momentColor, style = Stroke(1.5f))

            drawPath(Path().apply {
                moveTo(colRX, baseY - eh)
                lineTo(colRX + colMoment * momentScale * 0.5f, baseY - eh / 2)
                lineTo(colRX, baseY)
                close()
            }, momentColor.copy(alpha = 0.15f))
            drawPath(Path().apply {
                moveTo(colRX, baseY - eh)
                lineTo(colRX + colMoment * momentScale * 0.5f, baseY - eh / 2)
                lineTo(colRX, baseY)
            }, momentColor, style = Stroke(1.5f))

            // ── Dimension Lines ──
            val dimColor = Color(0xFFB0BEC5)
            val dimTextColor = Color.White
            val textPaint = android.graphics.Paint().apply { color = dimTextColor.toArgbInt(); textSize = 24f; isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER }

            // Span dimension
            val dimY = baseY + 35f
            drawLine(dimColor, Offset(colLX, baseY + 8f), Offset(colLX, dimY + 5f), strokeWidth = 1f)
            drawLine(dimColor, Offset(colRX, baseY + 8f), Offset(colRX, dimY + 5f), strokeWidth = 1f)
            drawLine(dimColor, Offset(colLX, dimY), Offset(colRX, dimY), strokeWidth = 1.5f)
            drawContext.canvas.nativeCanvas.drawText("%.1f m".format(inputs.span), (colLX + colRX) / 2f, dimY - 8f, textPaint)

            // Eave height dimension (left)
            val dimX = colLX - 35f
            drawLine(dimColor, Offset(colLX - 8f, baseY), Offset(dimX - 5f, baseY), strokeWidth = 1f)
            drawLine(dimColor, Offset(colLX - 8f, baseY - eh), Offset(dimX - 5f, baseY - eh), strokeWidth = 1f)
            drawLine(dimColor, Offset(dimX, baseY), Offset(dimX, baseY - eh), strokeWidth = 1.5f)
            drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.rotate(-90f, dimX - 12f, baseY - eh / 2f)
            drawContext.canvas.nativeCanvas.drawText("%.1f m".format(inputs.eaveHeight), dimX - 12f, baseY - eh / 2f + 8f, textPaint)
            drawContext.canvas.nativeCanvas.restore()

            // Ridge height dimension (right)
            val dimXR = colRX + 35f
            drawLine(dimColor, Offset(colRX + 8f, baseY), Offset(dimXR + 5f, baseY), strokeWidth = 1f)
            drawLine(dimColor, Offset(colRX + 8f, baseY - rh), Offset(dimXR + 5f, baseY - rh), strokeWidth = 1f)
            drawLine(dimColor, Offset(dimXR, baseY), Offset(dimXR, baseY - rh), strokeWidth = 1.5f)
            drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.rotate(-90f, dimXR + 12f, baseY - rh / 2f)
            drawContext.canvas.nativeCanvas.drawText("%.1f m".format(inputs.ridgeHeight), dimXR + 12f, baseY - rh / 2f + 8f, textPaint)
            drawContext.canvas.nativeCanvas.restore()

            // ── Section Labels ──
            val labelPaint = android.graphics.Paint().apply { color = Color(0xFF4FC3F7).toArgbInt(); textSize = 22f; isFakeBoldText = true }
            drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.rotate(-90f, colLX - 12f, baseY - eh / 2f + 40f)
            drawContext.canvas.nativeCanvas.drawText(result.mainFrame.columnSection.displayName, colLX - 12f, baseY - eh / 2f + 40f, labelPaint)
            drawContext.canvas.nativeCanvas.restore()

            // ── Title Block ──
            val tbX = size.width - 320f
            val tbY = size.height - 90f
            drawRect(Color(0x22FFFFFF), Offset(tbX, tbY), Size(300f, 75f))
            drawRect(Color(0xFF4A90D9), Offset(tbX, tbY), Size(300f, 75f), style = Stroke(1.5f))
            val tbPaint = android.graphics.Paint().apply { color = Color.White.toArgbInt(); textSize = 20f }
            val tbBold = android.graphics.Paint().apply { color = Color.White.toArgbInt(); textSize = 22f; isFakeBoldText = true }
            drawContext.canvas.nativeCanvas.drawText("CivilEG - Steel Warehouse", tbX + 12f, tbY + 24f, tbBold)
            drawContext.canvas.nativeCanvas.drawText("M_max = %.1f kN.m | V_max = %.1f kN".format(maxMoment, result.mainFrame.maxShear), tbX + 12f, tbY + 48f, tbPaint)
            drawContext.canvas.nativeCanvas.drawText("Frame Analysis Diagram", tbX + 12f, tbY + 68f, tbPaint)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, isBold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = FontWeight.ExtraBold, color = if (isBold) MaterialTheme.colorScheme.primary else Color.Unspecified)
    }
}

@Composable
fun AnalysisDetailCard(res: MainFrameResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("إجهادات التصميم القصوى", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            ResultRow("أقصى عزم (M_max)", "%.1f kN.m".format(res.maxMoment))
            ResultRow("أقصى قص (V_max)", "%.1f kN".format(res.maxShear))
            ResultRow("حمل محوري (P_max)", "%.1f kN".format(res.maxAxial))
            ResultRow("الترخيم (Deflection)", "%.1f mm".format(res.maxDeflection))
            Text(if (res.isSafe) "تحليل الأمان: آمن ✅" else "تحليل الأمان: تجاوز الحدود ❌", 
                fontWeight = FontWeight.Bold, 
                color = if (res.isSafe) Color(0xFF2E7D32) else Color.Red,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun SteelWarehouseVisualizer(inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
    var viewMode by remember { mutableStateOf(0) } // 0: Front, 1: Plan, 2: Side, 3: 3D

    InteractiveDrawingScreen(
        title = "المنظور الإنشائي",
        subtitle = "Structural Views",
        viewModes = listOf("الواجهة الأمامية", "المسقط الأفقي", "الواجهة الجانبية", "رسم ثلاثي الأبعاد"),
        selectedViewMode = viewMode,
        onViewModeChanged = { viewMode = it }
    ) {
        ComposeCanvas(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
            val padding = 60f
            val w = size.width - 2 * padding
            val h = size.height - 2 * padding

            // ── Grid ──
            val gridColor = Color(0x12FFFFFF)
            for (i in 0..20) {
                val x = padding + (i * w / 20)
                drawLine(gridColor, Offset(x, padding), Offset(x, size.height - padding), strokeWidth = 0.5f)
            }
            for (i in 0..14) {
                val y = padding + (i * h / 14)
                drawLine(gridColor, Offset(padding, y), Offset(size.width - padding, y), strokeWidth = 0.5f)
            }

            val scale = minOf(
                (w * 0.7f) / max(inputs.span, inputs.length).toFloat(),
                (h * 0.7f) / max(inputs.ridgeHeight, inputs.span).toFloat()
            )
            val startX = (padding + (w - inputs.span * scale) / 2).toFloat()
            val baseY = size.height - padding - 20f

            val textColor = Color.White
            val textPaint = android.graphics.Paint().apply {
                color = textColor.toArgbInt()
                textSize = 22f
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val dimPaint = android.graphics.Paint().apply {
                color = Color(0xFFB0BEC5).toArgbInt()
                textSize = 20f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val sectionPaint = android.graphics.Paint().apply {
                color = Color(0xFF4FC3F7).toArgbInt()
                textSize = 20f
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
            }

            when (viewMode) {
                0 -> { // Front Elevation — Professional
                    val colLX = startX
                    val colRX = startX + (inputs.span * scale).toFloat()
                    val eaveY = baseY - (inputs.eaveHeight * scale).toFloat()
                    val ridgeY = baseY - (inputs.ridgeHeight * scale).toFloat()
                    val midX = startX + (inputs.span * scale / 2).toFloat()
                    val memberW = 8f

                    // Ground hatching
                    val groundY = baseY
                    drawLine(Color(0xFF5A5A5A), Offset(colLX - 40f, groundY), Offset(colRX + 40f, groundY), strokeWidth = 3f)
                    for (i in 0..28) {
                        val x = (colLX - 40f) + (i * (colRX - colLX + 80f) / 28)
                        drawLine(Color(0xFF5A5A5A), Offset(x, groundY), Offset(x - 10f, groundY + 12f), strokeWidth = 1f)
                    }

                    // Pin supports
                    val supC = Color(0xFF9E9E9E)
                    listOf(colLX, colRX).forEach { cx ->
                        drawPath(Path().apply {
                            moveTo(cx - 16f, groundY)
                            lineTo(cx + 16f, groundY)
                            lineTo(cx, groundY + 22f)
                            close()
                        }, supC, style = Stroke(2.5f))
                        drawLine(supC, Offset(cx - 20f, groundY + 22f), Offset(cx + 20f, groundY + 22f), strokeWidth = 3f)
                    }

                    // Columns (3D effect)
                    val colC = Color(0xFFB0BEC5)
                    val colS = Color(0xFF607D8B)
                    listOf(colLX, colRX).forEach { cx ->
                        drawLine(colS, Offset(cx + 2f, groundY), Offset(cx + 2f, eaveY), strokeWidth = memberW + 3f)
                        drawLine(colC, Offset(cx, groundY), Offset(cx, eaveY), strokeWidth = memberW)
                        drawLine(Color(0xFFE0E0E0), Offset(cx - 1.5f.toFloat(), groundY), Offset(cx - 1.5f.toFloat(), eaveY), strokeWidth = 1f)
                    }

                    // Rafters (3D)
                    listOf(Pair(colLX, colRX)).forEach { (l, r) ->
                        drawLine(colS, Offset(l + 2f, eaveY + 2f), Offset(midX + 1f, ridgeY + 1f), strokeWidth = memberW + 3f)
                        drawLine(colC, Offset(l, eaveY), Offset(midX, ridgeY), strokeWidth = memberW)
                        drawLine(colS, Offset(r + 2f, eaveY + 2f), Offset(midX + 1f, ridgeY + 1f), strokeWidth = memberW + 3f)
                        drawLine(colC, Offset(r, eaveY), Offset(midX, ridgeY), strokeWidth = memberW)
                    }

                    // Ridge joint
                    drawCircle(Color(0xFF37474F), 12f, Offset(midX, ridgeY))
                    drawCircle(Color(0xFFFF9800), 7f, Offset(midX, ridgeY))
                    drawCircle(Color.White, 3f, Offset(midX, ridgeY))

                    // Eave joints
                    listOf(Offset(colLX, eaveY), Offset(colRX, eaveY)).forEach { jt ->
                        drawCircle(Color(0xFF37474F), 10f, jt)
                        drawCircle(Color(0xFFFF9800), 5f, jt)
                    }

                    // Section labels
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.rotate(-90f, colLX - 14f, (groundY + eaveY) / 2)
                    drawContext.canvas.nativeCanvas.drawText(result.mainFrame.columnSection.displayName, colLX - 14f, (groundY + eaveY) / 2, sectionPaint)
                    drawContext.canvas.nativeCanvas.restore()

                    // Dimension lines
                    val dimC = Color(0xFF90A4AE)
                    // Span
                    val dY = groundY + 40f
                    drawLine(dimC, Offset(colLX, groundY + 8f), Offset(colLX, dY + 5f), strokeWidth = 1f)
                    drawLine(dimC, Offset(colRX, groundY + 8f), Offset(colRX, dY + 5f), strokeWidth = 1f)
                    drawLine(dimC, Offset(colLX, dY), Offset(colRX, dY), strokeWidth = 1.5f)
                    drawContext.canvas.nativeCanvas.drawText("%.1f m".format(inputs.span), (colLX + colRX) / 2, dY - 6f, dimPaint)

                    // Eave height
                    val dXL = colLX - 40f
                    drawLine(dimC, Offset(colLX - 8f, groundY), Offset(dXL - 5f, groundY), strokeWidth = 1f)
                    drawLine(dimC, Offset(colLX - 8f, eaveY), Offset(dXL - 5f, eaveY), strokeWidth = 1f)
                    drawLine(dimC, Offset(dXL, groundY), Offset(dXL, eaveY), strokeWidth = 1.5f)
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.rotate(-90f, dXL - 10f, (groundY + eaveY) / 2)
                    drawContext.canvas.nativeCanvas.drawText("%.1f m".format(inputs.eaveHeight), dXL - 10f, (groundY + eaveY) / 2 + 6f, dimPaint)
                    drawContext.canvas.nativeCanvas.restore()

                    // Ridge height
                    val dXR = colRX + 40f
                    drawLine(dimC, Offset(colRX + 8f, groundY), Offset(dXR + 5f, groundY), strokeWidth = 1f)
                    drawLine(dimC, Offset(colRX + 8f, ridgeY), Offset(dXR + 5f, ridgeY), strokeWidth = 1f)
                    drawLine(dimC, Offset(dXR, groundY), Offset(dXR, ridgeY), strokeWidth = 1.5f)
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.rotate(-90f, dXR + 10f, (groundY + ridgeY) / 2)
                    drawContext.canvas.nativeCanvas.drawText("%.1f m".format(inputs.ridgeHeight), dXR + 10f, (groundY + ridgeY) / 2 + 6f, dimPaint)
                    drawContext.canvas.nativeCanvas.restore()

                    // View title
                    drawContext.canvas.nativeCanvas.drawText("FRONT ELEVATION", size.width / 2, padding - 10f, textPaint)

                    // Title block
                    val tbX = size.width - 300f
                    val tbY = size.height - 80f
                    drawRect(Color(0x22FFFFFF), Offset(tbX, tbY), Size(280f, 65f))
                    drawRect(Color(0xFF4A90D9), Offset(tbX, tbY), Size(280f, 65f), style = Stroke(1.5f))
                    val tbP = android.graphics.Paint().apply { color = Color.White.toArgbInt(); textSize = 20f }
                    drawContext.canvas.nativeCanvas.drawText("CivilEG - Warehouse Front View", tbX + 10f, tbY + 22f, android.graphics.Paint().apply { color = Color.White.toArgbInt(); textSize = 20f; isFakeBoldText = true })
                    drawContext.canvas.nativeCanvas.drawText("Col: ${result.mainFrame.columnSection.displayName} | Rafter: ${result.mainFrame.rafterSection.displayName}", tbX + 10f, tbY + 45f, tbP)
                }

                1 -> { // Plan View — Professional
                    val planW = (inputs.span * scale).toFloat()
                    val planL = (inputs.length * scale).toFloat()
                    val planLeft = startX + (w - planW) / 2
                    val planTop = padding + (h - planL) / 2

                    // Boundary with fill
                    drawRect(Color(0x1A4A90D9), Offset(planLeft, planTop), Size(planW, planL))
                    drawRect(Color(0xFFB0BEC5), Offset(planLeft, planTop), Size(planW, planL), style = Stroke(2f))

                    // Bay lines and column markers
                    val numBays = ceil(inputs.length / inputs.baySpacing).toInt()
                    for (i in 0..numBays) {
                        val y = planTop + (i * inputs.baySpacing * scale).toFloat()
                        if (y <= planTop + planL + 1f) {
                            drawLine(Color(0xFF607D8B), Offset(planLeft, y), Offset(planLeft + planW, y), strokeWidth = 1f)
                            // Column markers (I-beam symbol)
                            val colS = 8f
                            listOf(planLeft, planLeft + planW).forEach { cx ->
                                drawRect(Color(0xFFFF9800), Offset(cx - colS, y - 2f), Size(colS * 2, 4f))
                                drawRect(Color(0xFFFF9800), Offset(cx - 2f, y - colS), Size(4f, colS * 2))
                            }
                            // Bay label
                            if (i < numBays) {
                                val bayMidY = y + (inputs.baySpacing * scale / 2).toFloat()
                                drawContext.canvas.nativeCanvas.drawText(
                                    "%.1f m".format(inputs.baySpacing),
                                    planLeft + planW + 40f, bayMidY + 6f, dimPaint
                                )
                            }
                        }
                    }

                    // Purlin markers (dashed lines along span)
                    val numPurlins = (inputs.span / 1.5).toInt().coerceIn(2, 10)
                    for (i in 1 until numPurlins) {
                        val x = planLeft + (i * planW / numPurlins)
                        drawLine(Color(0x40FFFFFF), Offset(x, planTop), Offset(x, planTop + planL), strokeWidth = 0.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                    }

                    // Bracing X in first and last bay
                    if (numBays >= 1) {
                        listOf(0, max(numBays - 1, 0)).forEach { bayIdx ->
                            val y1 = planTop + (bayIdx * inputs.baySpacing * scale).toFloat()
                            val y2 = y1 + (inputs.baySpacing * scale).toFloat().coerceAtMost(planL - (y1 - planTop))
                            val brC = Color(0x66FF5722)
                            drawLine(brC, Offset(planLeft, y1), Offset(planLeft + planW, y2), strokeWidth = 1.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f)))
                            drawLine(brC, Offset(planLeft + planW, y1), Offset(planLeft, y2), strokeWidth = 1.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f)))
                        }
                    }

                    // Dimension: Span (bottom)
                    val dimB = planTop + planL + 35f
                    drawLine(Color(0xFF90A4AE), Offset(planLeft, planTop + planL + 8f), Offset(planLeft, dimB + 5f), strokeWidth = 1f)
                    drawLine(Color(0xFF90A4AE), Offset(planLeft + planW, planTop + planL + 8f), Offset(planLeft + planW, dimB + 5f), strokeWidth = 1f)
                    drawLine(Color(0xFF90A4AE), Offset(planLeft, dimB), Offset(planLeft + planW, dimB), strokeWidth = 1.5f)
                    drawContext.canvas.nativeCanvas.drawText("Span: %.1f m".format(inputs.span), planLeft + planW / 2, dimB - 6f, dimPaint)

                    // Dimension: Length (right)
                    val dimR = planLeft + planW + 35f
                    drawLine(Color(0xFF90A4AE), Offset(planLeft + planW + 8f, planTop), Offset(dimR + 5f, planTop), strokeWidth = 1f)
                    drawLine(Color(0xFF90A4AE), Offset(planLeft + planW + 8f, planTop + planL), Offset(dimR + 5f, planTop + planL), strokeWidth = 1f)
                    drawLine(Color(0xFF90A4AE), Offset(dimR, planTop), Offset(dimR, planTop + planL), strokeWidth = 1.5f)

                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.rotate(-90f, dimR + 10f, planTop + planL / 2)
                    drawContext.canvas.nativeCanvas.drawText("Length: %.1f m".format(inputs.length), dimR + 10f, planTop + planL / 2 + 6f, dimPaint)
                    drawContext.canvas.nativeCanvas.restore()

                    drawContext.canvas.nativeCanvas.drawText("PLAN VIEW", size.width / 2, padding - 10f, textPaint)
                }

                2 -> { // Side Elevation — Professional
                    val planL = (inputs.length * scale).toFloat()
                    val eaveH = (inputs.eaveHeight * scale).toFloat()
                    val sideLeft = startX + (w - planL) / 2
                    val groundY = baseY
                    val eaveY = groundY - eaveH

                    // Ground hatching
                    drawLine(Color(0xFF5A5A5A), Offset(sideLeft - 20f, groundY), Offset(sideLeft + planL + 20f, groundY), strokeWidth = 3f)
                    for (i in 0..30) {
                        val x = (sideLeft - 20f) + (i * (planL + 40f) / 30)
                        drawLine(Color(0xFF5A5A5A), Offset(x, groundY), Offset(x - 8f, groundY + 10f), strokeWidth = 1f)
                    }

                    // Columns and eave line
                    val numBays = ceil(inputs.length / inputs.baySpacing).toInt()
                    val memberW = 6f
                    for (i in 0..numBays) {
                        val x = sideLeft + (i * inputs.baySpacing * scale).toFloat()
                        if (x <= sideLeft + planL + 1f) {
                            drawLine(Color(0xFF607D8B), Offset(x + 1f, groundY), Offset(x + 1f, eaveY), strokeWidth = memberW + 2f)
                            drawLine(Color(0xFFB0BEC5), Offset(x, groundY), Offset(x, eaveY), strokeWidth = memberW)

                            // Small pin support
                            drawPath(Path().apply {
                                moveTo(x - 10f, groundY)
                                lineTo(x + 10f, groundY)
                                lineTo(x, groundY + 14f)
                                close()
                            }, Color(0xFF9E9E9E), style = Stroke(1.5f))
                        }
                    }

                    // Eave line
                    drawLine(Color(0xFFB0BEC5), Offset(sideLeft, eaveY), Offset(sideLeft + planL, eaveY), strokeWidth = memberW)
                    // Ground line
                    drawLine(Color(0xFF5A5A5A), Offset(sideLeft - 20f, groundY), Offset(sideLeft + planL + 20f, groundY), strokeWidth = 3f)

                    // Purlin lines (along side view, shown as short vertical ticks on eave)
                    val numPurlins = (inputs.span / 1.5).toInt().coerceIn(2, 8)
                    for (i in 1 until numPurlins) {
                        val x = sideLeft + (i * planL / numPurlins)
                        drawLine(Color(0xFF4FC3F7), Offset(x, eaveY - 8f), Offset(x, eaveY + 8f), strokeWidth = 2f)
                    }

                    // Bracing in first bay
                    if (numBays >= 1) {
                        val bx1 = sideLeft
                        val bx2 = sideLeft + (inputs.baySpacing * scale).toFloat().coerceAtMost(planL)
                        drawLine(Color(0x66FF5722), Offset(bx1, groundY), Offset(bx2, eaveY), strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f)))
                        drawLine(Color(0x66FF5722), Offset(bx2, groundY), Offset(bx1, eaveY), strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f)))
                    }

                    // Girt markers on columns
                    val numGirts = (inputs.eaveHeight / 1.5).toInt().coerceIn(1, 4)
                    for (i in 1..numGirts) {
                        val gy = groundY - (i * eaveH / (numGirts + 1))
                        drawLine(Color(0x40FF9800), Offset(sideLeft, gy), Offset(sideLeft + planL, gy), strokeWidth = 0.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                    }

                    // Dimensions
                    drawContext.canvas.nativeCanvas.drawText("SIDE ELEVATION", size.width / 2, padding - 10f, textPaint)

                    // Length dimension
                    val dY = groundY + 35f
                    drawLine(Color(0xFF90A4AE), Offset(sideLeft, groundY + 8f), Offset(sideLeft, dY + 5f), strokeWidth = 1f)
                    drawLine(Color(0xFF90A4AE), Offset(sideLeft + planL, groundY + 8f), Offset(sideLeft + planL, dY + 5f), strokeWidth = 1f)
                    drawLine(Color(0xFF90A4AE), Offset(sideLeft, dY), Offset(sideLeft + planL, dY), strokeWidth = 1.5f)
                    drawContext.canvas.nativeCanvas.drawText("%.1f m".format(inputs.length), sideLeft + planL / 2, dY - 6f, dimPaint)

                    // Eave height dimension
                    val dX = sideLeft - 35f
                    drawLine(Color(0xFF90A4AE), Offset(sideLeft - 8f, groundY), Offset(dX - 5f, groundY), strokeWidth = 1f)
                    drawLine(Color(0xFF90A4AE), Offset(sideLeft - 8f, eaveY), Offset(dX - 5f, eaveY), strokeWidth = 1f)
                    drawLine(Color(0xFF90A4AE), Offset(dX, groundY), Offset(dX, eaveY), strokeWidth = 1.5f)
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.rotate(-90f, dX - 10f, (groundY + eaveY) / 2)
                    drawContext.canvas.nativeCanvas.drawText("%.1f m".format(inputs.eaveHeight), dX - 10f, (groundY + eaveY) / 2 + 6f, dimPaint)
                    drawContext.canvas.nativeCanvas.restore()
                }

                3 -> { // 3D Isometric — Professional
                    val s = scale * 0.65f
                    val depthOff = 40f
                    val numFrames = 5

                    for (frameIdx in numFrames downTo 0) {
                        val off = frameIdx * depthOff
                        val x0 = startX + off
                        val y0 = baseY - off
                        val spanW = (inputs.span * s).toFloat()
                        val eaveH = (inputs.eaveHeight * s).toFloat()
                        val ridgeH = (inputs.ridgeHeight * s).toFloat()
                        val midXf = x0 + spanW / 2

                        val alpha = if (frameIdx == 0) 1f else 0.2f + 0.15f * frameIdx
                        val color = Color.White.copy(alpha = alpha)
                        val stroke = if (frameIdx == 0) 3f else 1.5f

                        // Columns
                        drawLine(color, Offset(x0, y0), Offset(x0, y0 - eaveH), strokeWidth = stroke)
                        drawLine(color, Offset(x0 + spanW, y0), Offset(x0 + spanW, y0 - eaveH), strokeWidth = stroke)
                        // Rafters
                        drawLine(color, Offset(x0, y0 - eaveH), Offset(midXf, y0 - ridgeH), strokeWidth = stroke)
                        drawLine(color, Offset(x0 + spanW, y0 - eaveH), Offset(midXf, y0 - ridgeH), strokeWidth = stroke)

                        // Eave line (longitudinal)
                        if (frameIdx > 0) {
                            val prevOff = (frameIdx - 1) * depthOff
                            val px = startX + prevOff
                            val py = baseY - prevOff
                            val lineC = Color.White.copy(alpha = 0.1f + 0.08f * frameIdx)
                            drawLine(lineC, Offset(x0, y0 - eaveH), Offset(px, py - eaveH), strokeWidth = 1f)
                            drawLine(lineC, Offset(x0 + spanW, y0 - eaveH), Offset(px + spanW, py - eaveH), strokeWidth = 1f)
                            drawLine(lineC, Offset(midXf, y0 - ridgeH), Offset(px + spanW / 2.toFloat(), py - ridgeH), strokeWidth = 1f)
                        }
                    }

                    // Ground plane
                    val groundColor = Color(0x15FFFFFF)
                    val gOff = numFrames * depthOff
                    drawRect(groundColor, Offset(startX, baseY - gOff - 10f), Size((inputs.span * s).toFloat(), gOff + 10f))

                    drawContext.canvas.nativeCanvas.drawText("3D ISOMETRIC VIEW", size.width / 2, padding - 10f, textPaint)
                }
            }
        }
    }
}

fun createWarehouseBitmap(inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult): Bitmap {
    val bitmap = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    
    val p = Paint().apply { color = android.graphics.Color.BLACK; strokeWidth = 4f; style = Paint.Style.STROKE }
    val textP = Paint().apply { color = android.graphics.Color.BLUE; textSize = 30f; isFakeBoldText = true }

    // Draw 4 Quadrants for 4 Views
    
    // 1. Elevation (Top Left)
    drawCanvasElevation(canvas, inputs, 100f, 400f, 0.5f, result)
    canvas.drawText("الواجهة الأمامية", 100f, 50f, textP)
    
    // 2. Plan (Top Right)
    drawCanvasPlan(canvas, inputs, 700f, 400f, 0.4f)
    canvas.drawText("المسقط الأفقي", 700f, 50f, textP)
    
    // 3. Side (Bottom Left)
    drawCanvasSide(canvas, inputs, 100f, 900f, 0.4f)
    canvas.drawText("الواجهة الجانبية", 100f, 550f, textP)
    
    // 4. 3D/Schedule Info (Bottom Right)
    canvas.drawText("ملخص التصميم", 700f, 550f, textP)
    val infoP = Paint().apply { color = android.graphics.Color.BLACK; textSize = 24f }
    canvas.drawText("إجمالي الوزن: ${"%.2f".format(result.totalWeight)} طن", 700f, 600f, infoP)
    canvas.drawText("التكلفة/م²: ${"%.0f".format(result.costPerM2)} جنيه", 700f, 640f, infoP)
    canvas.drawText("العائد على الاستثمار: ${"%.1f".format(result.roi)} %", 700f, 680f, infoP)

    return bitmap
}

private fun drawCanvasElevation(canvas: Canvas, inputs: SteelWarehouseInputs, x: Float, y: Float, scale: Float, result: SteelWarehouseAnalysisResult? = null) {
    val p = Paint().apply { 
        color = android.graphics.Color.BLACK
        strokeWidth = 6f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    val dimPaint = Paint().apply {
        color = android.graphics.Color.DKGRAY
        strokeWidth = 2f
        textSize = 24f
        isAntiAlias = true
    }
    val textP = Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 22f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    
    val s = scale * 0.7f
    val w = inputs.span.toFloat() * s
    val eh = inputs.eaveHeight.toFloat() * s
    val rh = inputs.ridgeHeight.toFloat() * s
    val midX = x + w/2
    
    // Draw Main Frame (Columns & Rafters)
    canvas.drawLine(x, y, x, y - eh, p)
    canvas.drawLine(x + w, y, x + w, y - eh, p)
    canvas.drawLine(x, y - eh, midX, y - rh, p)
    canvas.drawLine(x + w, y - eh, midX, y - rh, p)
    
    // Foundations
    val footP = Paint().apply { color = android.graphics.Color.LTGRAY; style = Paint.Style.FILL }
    canvas.drawRect(x - 20f, y, x + 20f, y + 10f, footP)
    canvas.drawRect(x + w - 20f, y, x + w + 20f, y + 10f, footP)
    canvas.drawRect(x - 20f, y, x + 20f, y + 10f, p.apply { strokeWidth = 2f })
    canvas.drawRect(x + w - 20f, y, x + w + 20f, y + 10f, p)
    p.strokeWidth = 6f
    
    // --- Dimension Lines ---
    // Span Dimension
    drawDimensionLine(canvas, x, y + 60f, x + w, y + 60f, "${inputs.span} m", dimPaint)
    
    // Eave Height Dimension
    drawDimensionLine(canvas, x - 60f, y, x - 60f, y - eh, "${inputs.eaveHeight} m", dimPaint)
    
    // Ridge Height Dimension
    drawDimensionLine(canvas, x + w + 60f, y, x + w + 60f, y - rh, "${inputs.ridgeHeight} m", dimPaint)

    // --- Section Labels ---
    result?.let { res ->
        val sectionP = Paint().apply { color = android.graphics.Color.BLUE; textSize = 20f; isFakeBoldText = true }
        
        // Column Label
        canvas.save()
        canvas.rotate(-90f, x + 25f, y - eh/2)
        canvas.drawText(res.mainFrame.columnSection.displayName, x + 25f, y - eh/2, sectionP.apply { textAlign = Paint.Align.CENTER })
        canvas.restore()
        
        // Rafter Label
        canvas.save()
        val angle = Math.toDegrees(Math.atan2((rh - eh).toDouble(), (w/2).toDouble())).toFloat()
        canvas.rotate(-angle, x + w/4, y - eh - (rh-eh)/4 - 15f)
        canvas.drawText(res.mainFrame.rafterSection.displayName, x + w/4, y - eh - (rh-eh)/4 - 15f, sectionP.apply { textAlign = Paint.Align.CENTER })
        canvas.restore()
    }

    // Connection Markers (Circles at joints)
    val connPaint = Paint().apply { color = android.graphics.Color.RED; style = Paint.Style.STROKE; strokeWidth = 2f }
    canvas.drawCircle(x, y - eh, 15f, connPaint) // Eave joint left
    canvas.drawCircle(x + w, y - eh, 15f, connPaint) // Eave joint right
    canvas.drawCircle(midX, y - rh, 15f, connPaint) // Apex joint
}

private fun drawDimensionLine(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, text: String, paint: Paint) {
    val tickSize = 15f
    canvas.drawLine(x1, y1, x2, y2, paint)
    
    // Draw Ticks (45 degree lines)
    val angle = Math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
    val tickAngle = angle + Math.PI / 4
    
    canvas.drawLine(x1 - (Math.cos(tickAngle) * tickSize).toFloat(), y1 - (Math.sin(tickAngle) * tickSize).toFloat(), 
                    x1 + (Math.cos(tickAngle) * tickSize).toFloat(), y1 + (Math.sin(tickAngle) * tickSize).toFloat(), paint)
    canvas.drawLine(x2 - (Math.cos(tickAngle) * tickSize).toFloat(), y2 - (Math.sin(tickAngle) * tickSize).toFloat(), 
                    x2 + (Math.cos(tickAngle) * tickSize).toFloat(), y2 + (Math.sin(tickAngle) * tickSize).toFloat(), paint)

    // Text label
    val midX = (x1 + x2) / 2
    val midY = (y1 + y2) / 2
    val textPaint = Paint(paint).apply { textAlign = Paint.Align.CENTER }
    
    if (x1 == x2) { // Vertical
        canvas.save()
        canvas.rotate(-90f, midX - 10f, midY)
        canvas.drawText(text, midX - 10f, midY, textPaint)
        canvas.restore()
    } else { // Horizontal
        canvas.drawText(text, midX, midY - 10f, textPaint)
    }
}

private fun drawCanvasPlan(canvas: Canvas, inputs: SteelWarehouseInputs, x: Float, y: Float, scale: Float) {
    val p = Paint().apply { color = android.graphics.Color.BLACK; strokeWidth = 4f; style = Paint.Style.STROKE; isAntiAlias = true }
    val dimPaint = Paint().apply { color = android.graphics.Color.DKGRAY; strokeWidth = 1.5f; textSize = 20f; isAntiAlias = true }
    
    val s = scale * 0.6f
    val w = inputs.span.toFloat() * s
    val l = inputs.length.toFloat() * s
    val planTop = y - l
    
    canvas.drawRect(x, planTop, x + w, y, p)
    
    val baySpacing = inputs.baySpacing.toFloat() * s
    var curY = planTop
    var count = 0
    while (curY <= y + 0.1f) {
        canvas.drawLine(x, curY, x + w, curY, p.apply { strokeWidth = 2f })
        
        // Column markers
        canvas.drawRect(x - 5f, curY - 5f, x + 5f, curY + 5f, Paint().apply { color = android.graphics.Color.BLACK })
        canvas.drawRect(x + w - 5f, curY - 5f, x + w + 5f, curY + 5f, Paint().apply { color = android.graphics.Color.BLACK })
        
        if (curY + baySpacing <= y + 0.1f) {
            drawDimensionLine(canvas, x - 40f, curY, x - 40f, curY + baySpacing, "${inputs.baySpacing}m", dimPaint)
        }
        
        curY += baySpacing
        count++
    }
    
    // Span Dimension
    drawDimensionLine(canvas, x, y + 40f, x + w, y + 40f, "البحر: ${inputs.span}m", dimPaint)
    // Total Length Dimension
    drawDimensionLine(canvas, x + w + 40f, planTop, x + w + 40f, y, "الطول: ${inputs.length}m", dimPaint)
}

private fun drawCanvasSide(canvas: Canvas, inputs: SteelWarehouseInputs, x: Float, y: Float, scale: Float) {
    val p = Paint().apply { color = android.graphics.Color.BLACK; strokeWidth = 5f; isAntiAlias = true }
    val textP = Paint().apply { color = android.graphics.Color.DKGRAY; textSize = 22f; textAlign = Paint.Align.CENTER }
    
    val s = scale * 0.6f
    val l = inputs.length.toFloat() * s
    val eh = inputs.eaveHeight.toFloat() * s
    
    canvas.drawLine(x, y, x + l, y, p) // Ground
    val bay = inputs.baySpacing.toFloat() * s
    var cur = x
    while (cur <= x + l + 0.1f) {
        canvas.drawLine(cur, y, cur, y - eh, p)
        cur += bay
    }
    canvas.drawLine(x, y - eh, x + l, y - eh, p)
    
    canvas.drawText("الطول الكلي: ${inputs.length}m", x + l/2, y + 35f, textP)
}

fun openPdf(context: Context, file: File) {
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "لا يوجد قارئ PDF", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SectionResultCard(title: String, section: SteelSectionType) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ComposeCanvas(modifier = Modifier.size(45.dp)) {
                val w = size.width; val h = size.height; val t = 10f
                drawRect(Color.DarkGray, Offset(0f, 0f), Size(w, t))
                drawRect(Color.DarkGray, Offset(0f, h - t), Size(w, t))
                drawRect(Color.DarkGray, Offset((w - t) / 2.toFloat(), 0f), Size(t, h))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(section.displayName, style = MaterialTheme.typography.bodyMedium)
                if (section is SteelSectionType.ISection) {
                    Text("h=${section.depth.toInt()}, b=${section.width.toInt()}, tw=${section.webThickness}, tf=${section.flangeThickness} (mm)", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun SecondaryMembersCard(res: SecondaryMembersResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("العناصر الثانوية والتربيط", fontWeight = FontWeight.Bold)
            ResultRow("المدادات (Purlins)", res.purlinSection.displayName)
            ResultRow("مدادات الجوانب (Girts)", res.girtSection.displayName)
            ResultRow("البريسينج (Bracing)", res.bracingSection.displayName)
            ResultRow("عدد المدادات الكلي", "${res.purlinCount}")
        }
    }
}

@Composable
fun ConnectionDetailCard(conn: SteelConnectionDetail) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        border = BorderStroke(1.dp, if (conn.isSafe) Color(0xFF81C784) else Color(0xFFE57373))
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(conn.name, fontWeight = FontWeight.Bold)
                Text(conn.type.displayName, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (conn.isSafe) "آمن ✅" else "فشل ❌", color = if (conn.isSafe) Color(0xFF2E7D32) else Color.Red, fontWeight = FontWeight.Bold)
                Text("U.R: %.2f".format(conn.demand / conn.capacity), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun RecommendationsCard(recs: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)), border = BorderStroke(1.dp, Color(0xFFFBC02D))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFFBC02D))
                Spacer(modifier = Modifier.width(8.dp))
                Text("توصيات التصميم والنصائح", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            recs.forEach { rec ->
                Text("• $rec", fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteelSectionTab(viewModel: SteelViewModel, result: SteelMemberResult?, isLoading: Boolean) {
    var selectedCategory by remember { mutableStateOf("IPE (European I-Beams)") }
    var selectedSection by remember { mutableStateOf<SteelSectionType?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedCategory by remember { mutableStateOf(false) }
    var expandedSection by remember { mutableStateOf(false) }
    
    // User Inputs
    var axialLoad by remember { mutableStateOf("500") }
    var moment by remember { mutableStateOf("100") }
    var shear by remember { mutableStateOf("50") }
    var length by remember { mutableStateOf("3") }
    var selectedMemberType by remember { mutableStateOf(SteelMemberType.COLUMN) }
    var selectedCode by remember { mutableStateOf(CalculatorEngine.DesignCode.EGYPTIAN) }
    
    val searchResults by viewModel.searchResults.observeAsState(emptyList())
    val library = viewModel.sectionLibrary
    val categories = library.keys.toList()

    val steelCodes = listOf("ECP 205", "AISC 360-16", "SBC 306")
    var selectedSteelCode by remember { mutableStateOf(steelCodes[0]) }
    var expandedSteelCode by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SectionHeader("📐 قاموس القطاعات والتحليل الذكي", R.drawable.ic_steel) }

        // Steel Code Selector
        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("كود التصميم المعدني", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = expandedSteelCode,
                        onExpandedChange = { expandedSteelCode = !expandedSteelCode }
                    ) {
                        OutlinedTextField(
                            value = selectedSteelCode,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSteelCode) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        ExposedDropdownMenu(
                            expanded = expandedSteelCode,
                            onDismissRequest = { expandedSteelCode = false }
                        ) {
                            steelCodes.forEach { code ->
                                DropdownMenuItem(
                                    text = { Text(code) },
                                    onClick = { selectedSteelCode = code; expandedSteelCode = false }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Smart Search Section
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    viewModel.searchSections(it)
                },
                label = { Text("ابحث برقم القطاع (مثال: 300)") },
                placeholder = { Text("IPE 300, HEB 240...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = ""; viewModel.searchSections("") }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Show Search Results if searching
        if (searchQuery.isNotEmpty()) {
            item {
                Text("نتائج البحث:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    searchResults.take(8).forEach { section ->
                        Surface(
                            onClick = { 
                                searchQuery = "" // Clear search first
                                viewModel.searchSections("")
                                // Find which category this section belongs to
                                categories.find { cat -> library[cat]?.any { s -> s.sectionName == section.sectionName } == true }?.let { cat ->
                                    selectedCategory = cat
                                    // Give Compose a chance to update the list before setting the section
                                    selectedSection = section
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Architecture, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(section.displayName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
        }
        
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("فئة القطاع", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    ExposedDropdownMenuBox(
                        expanded = expandedCategory,
                        onExpandedChange = { expandedCategory = !expandedCategory }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        ExposedDropdownMenu(
                            expanded = expandedCategory,
                            onDismissRequest = { expandedCategory = false }
                        ) {
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category) },
                                    onClick = {
                                        selectedCategory = category
                                        selectedSection = library[category]?.firstOrNull()
                                        expandedCategory = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text("القطاع المحدد", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    ExposedDropdownMenuBox(
                        expanded = expandedSection,
                        onExpandedChange = { expandedSection = !expandedSection }
                    ) {
                        OutlinedTextField(
                            value = selectedSection?.sectionName ?: "اختر قطاعاً",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSection) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        ExposedDropdownMenu(
                            expanded = expandedSection,
                            onDismissRequest = { expandedSection = false }
                        ) {
                            library[selectedCategory]?.forEach { section ->
                                DropdownMenuItem(
                                    text = { Text(section.sectionName) },
                                    onClick = {
                                        selectedSection = section
                                        expandedSection = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        selectedSection?.let { section ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("الخصائص الهندسية للقطاع:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                PropertyLine("المساحة (A)", "%.2f cm²".format(section.getArea()/100.0))
                                PropertyLine("الوزن (W)", "%.1f kg/m".format(section.weight))
                                PropertyLine("الارتفاع (h)", "${section.depth} mm")
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                PropertyLine("الشفة (bf)", "${section.width} mm")
                                PropertyLine("سمك الشفة (tf)", "${section.flangeThickness} mm")
                                PropertyLine("سمك العصب (tw)", "${section.webThickness} mm")
                            }
                        }
                        
                        if (section.area > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    PropertyLine("Inertia Ix", "%.0f cm⁴".format(section.ix))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    PropertyLine("Modulus Sx", "%.1f cm³".format(section.sx))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مدخلات التصميم", fontWeight = FontWeight.Bold)
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        SteelMemberType.entries.forEach { type ->
                            FilterChip(
                                selected = selectedMemberType == type,
                                onClick = { selectedMemberType = type },
                                label = { Text(type.name, fontSize = 10.sp) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(axialLoad, "Pu (kN)", { axialLoad = it }, Modifier.weight(1f))
                        SteelInputField(moment, "Mu (kN.m)", { moment = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(shear, "Vu (kN)", { shear = it }, Modifier.weight(1f))
                        SteelInputField(length, "L (m)", { length = it }, Modifier.weight(1f))
                    }

                    Text("كود التصميم", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CalculatorEngine.DesignCode.entries.forEach { code ->
                            FilterChip(
                                selected = selectedCode == code,
                                onClick = { selectedCode = code },
                                label = { Text(code.name, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { 
                    selectedSection?.let { sec ->
                        viewModel.calculateSteelMember(
                            section = sec,
                            memberType = selectedMemberType,
                            inputs = SteelInputs(
                                axialLoad = axialLoad.toDoubleOrNull() ?: 0.0,
                                moment = moment.toDoubleOrNull() ?: 0.0,
                                shear = shear.toDoubleOrNull() ?: 0.0,
                                unbracedLength = length.toDoubleOrNull() ?: 1.0,
                                length = length.toDoubleOrNull() ?: 1.0
                            ),
                            code = selectedCode
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && selectedSection != null
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("تحليل وتصميم القطاع")
            }
        }

        result?.let { res ->
            item {
                val urColor = when {
                    res.utilizationRatio > 1.0 -> Color.Red
                    res.utilizationRatio > 0.9 -> Color(0xFFFF9800)
                    res.utilizationRatio > 0.4 -> Color(0xFF4CAF50)
                    else -> Color(0xFF2196F3)
                }
                val animatedRatio by animateFloatAsState(
                    targetValue = res.utilizationRatio.toFloat().coerceAtMost(1.5f),
                    animationSpec = tween(1000), label = ""
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("معامل الاستغلال", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (res.isSafe) "القطاع آمن ✅" else "القطاع غير آمن ❌",
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.isSafe) Color(0xFF2E7D32) else Color.Red,
                                    fontSize = 13.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    progress = { animatedRatio.coerceAtMost(1f) },
                                    modifier = Modifier.size(64.dp),
                                    strokeWidth = 7.dp,
                                    color = urColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${(res.utilizationRatio * 100).toInt()}%",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = urColor
                                )
                            }
                        }
                    }
                }
            }
            item { SteelResultCard(res) }
            item {
                InteractiveDrawingScreen(
                    title = "رسم القطاع الحديدي",
                    subtitle = "Steel Member Detail",
                    viewModes = listOf("الكل", "المقطع الطولي", "المقطع العرضي", "التوصيلات"),
                    drawingContent = {
                        ProfessionalSteelDrawing(
                            sectionType = res.sectionType.displayName,
                            sectionName = res.sectionType.sectionName,
                            memberLength = (length.toDoubleOrNull() ?: 6.0) * 1000.0,
                            depth = res.sectionType.depth,
                            flangeWidth = res.sectionType.width,
                            flangeThickness = res.sectionType.flangeThickness,
                            webThickness = res.sectionType.webThickness,
                            radius = res.sectionType.rootRadius,
                            area = res.sectionType.area,
                            ix = res.sectionType.ix,
                            sx = res.sectionType.sx,
                            zx = res.sectionType.zx,
                            weightPerMeter = res.sectionType.weight,
                            isColumn = res.memberType == SteelMemberType.COLUMN,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                )
            }
            
            // Economy Indicator
            item {
                val color = if (res.utilizationRatio in 0.7..0.95) Color(0xFF2E7D32) 
                           else if (res.utilizationRatio > 1.0) Color.Red 
                           else Color(0xFFF57C00)
                val status = if (res.utilizationRatio in 0.7..0.95) "مثالي ومقتصد" 
                            else if (res.utilizationRatio > 1.0) "غير آمن — يحتاج قطاع أكبر" 
                            else "تصميم محافظ — هدر في الحديد"
                            
                Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)), border = BorderStroke(1.dp, color)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (res.utilizationRatio <= 1.0) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null, tint = color)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("مؤشر التوفير والأمان", fontWeight = FontWeight.Bold, color = color)
                            Text(status, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WeldDesignTab(viewModel: SteelViewModel) {
    var size by remember { mutableStateOf("6") }
    var length by remember { mutableStateOf("200") }
    var selectedElectrode by remember { mutableStateOf(ElectrodeType.E70XX) }
    var selectedCode by remember { mutableStateOf(CalculatorEngine.DesignCode.EGYPTIAN) }
    var capacity by remember { mutableDoubleStateOf(0.0) }

    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeader("🔥 تصميم وصلات اللحام الاحترافي", R.drawable.ic_steel) }
        
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SteelInputField(size, "مقاس اللحام s (mm)", { size = it }, Modifier.weight(1f))
                SteelInputField(length, "طول اللحام L (mm)", { length = it }, Modifier.weight(1f))
            }
        }
        
        item {
            Text("نوع الإلكترود (Electrode)", fontWeight = FontWeight.Bold)
            FlowRow {
                ElectrodeType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedElectrode == type,
                        onClick = { selectedElectrode = type },
                        label = { Text(type.displayName, fontSize = 10.sp) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }

        item {
            Text("كود التصميم", fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CalculatorEngine.DesignCode.entries.forEach { code ->
                    FilterChip(
                        selected = selectedCode == code,
                        onClick = { selectedCode = code },
                        label = { Text(code.name, fontSize = 11.sp) }
                    )
                }
            }
        }

        item {
            Button(onClick = {
                val s = size.toDoubleOrNull() ?: 6.0
                val l = length.toDoubleOrNull() ?: 200.0
                capacity = viewModel.calculateWeldCapacity(s, l, selectedElectrode, selectedCode)
            }, modifier = Modifier.fillMaxWidth()) {
                Text("حساب مقاومة القص (Design Rn)")
            }
        }

        if (capacity > 0) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("مقاومة اللحام التصميمية (Design Capacity):", fontWeight = FontWeight.Bold)
                        Text("${"%.2f".format(capacity)} kN", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text("مساحة الحلق الفعالة: ${"%.1f".format(0.707 * (size.toDoubleOrNull() ?: 0.0) * (length.toDoubleOrNull() ?: 0.0))} mm²", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BoltDesignTab(viewModel: SteelViewModel) {
    var diameter by remember { mutableStateOf("16") }
    var selectedGrade by remember { mutableStateOf(BoltGrade.GRADE_8_8) }
    var selectedCode by remember { mutableStateOf(CalculatorEngine.DesignCode.EGYPTIAN) }
    var numBolts by remember { mutableStateOf("1") }
    var capacity by remember { mutableDoubleStateOf(0.0) }

    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeader("🔩 تصميم وصلات المسامير الاحترافي", R.drawable.ic_steel) }
        
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SteelInputField(diameter, "قطر المسمار M (mm)", { diameter = it }, Modifier.weight(1f))
                SteelInputField(numBolts, "عدد المسامير", { numBolts = it }, Modifier.weight(1f))
            }
        }

        item {
            Text("رتبة المسمار (Grade)", fontWeight = FontWeight.Bold)
            FlowRow {
                for (grade in BoltGrade.entries) {
                    FilterChip(
                        selected = selectedGrade == grade,
                        onClick = { selectedGrade = grade },
                        label = { Text(grade.displayName, fontSize = 10.sp) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }

        item {
            Text("كود التصميم", fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CalculatorEngine.DesignCode.entries.forEach { code ->
                    FilterChip(
                        selected = selectedCode == code,
                        onClick = { selectedCode = code },
                        label = { Text(code.name, fontSize = 11.sp) }
                    )
                }
            }
        }

        item {
            Button(onClick = {
                val d = diameter.toDoubleOrNull() ?: 16.0
                val n = numBolts.toIntOrNull() ?: 1
                capacity = viewModel.calculateBoltCapacity(d, selectedGrade, n, selectedCode)
            }, modifier = Modifier.fillMaxWidth()) {
                Text("حساب مقاومة القص للمجموعة")
            }
        }

        if (capacity > 0) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("مقاومة القص الكلية (Total Rn):", fontWeight = FontWeight.Bold)
                        Text("${"%.2f".format(capacity)} kN", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(4.dp))
                        Text("مقاومة المسمار الواحد: ${"%.1f".format(capacity / (numBolts.toIntOrNull() ?: 1))} kN", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SteelResultCard(res: SteelMemberResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ResultRow("مساحة القطاع", "%.1f mm²".format(res.sectionType.getArea()))
            ResultRow("سعة الضغط", "%.1f kN".format(res.axialCapacity))
            ResultRow("سعة الانحناء", "%.1f kN.m".format(res.flexuralCapacity))
            ResultRow("نسبة الاستخدام", "%.2f".format(res.utilizationRatio))
            Text(if (res.isSafe) "آمن ✅" else "غير آمن ❌", fontWeight = FontWeight.Bold, color = if (res.isSafe) Color(0xFF2E7D32) else Color.Red)
        }
    }
}

@Composable
fun SteelSectionDrawing(section: SteelSectionType) {
    Box(modifier = Modifier.fillMaxWidth().height(250.dp).padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ComposeCanvas(modifier = Modifier.size(150.dp)) {
                val w = size.width
                val h = size.height
                
                when (section) {
                    is SteelSectionType.ISection -> {
                        val tf = (section.flangeThickness / section.depth * h).toFloat().coerceIn(5f, 25f)
                        val tw = (section.webThickness / section.width * w).toFloat().coerceIn(5f, 20f)
                        val bf = (section.width / section.depth * w).toFloat().coerceAtMost(w)
                        val offsetBF = (w - bf) / 2
                        
                        // Top Flange
                        drawRect(Color.DarkGray, Offset(offsetBF, 0f), Size(bf, tf))
                        // Bottom Flange
                        drawRect(Color.DarkGray, Offset(offsetBF, h - tf), Size(bf, tf))
                        // Web
                        drawRect(Color.DarkGray, Offset((w - tw) / 2.toFloat(), tf), Size(tw, h - 2 * tf))
                        
                        // Stress Distribution (Schematic)
                        drawLine(Color.Red.copy(alpha = 0.3f), Offset(w + 20f, 0f), Offset(w + 20f, h), strokeWidth = 2f)
                        drawLine(Color.Red, Offset(w + 20f, 0f), Offset(w + 50f, 0f), strokeWidth = 3f)
                        drawLine(Color.Red, Offset(w + 20f, h), Offset(w - 10f, h), strokeWidth = 3f)
                        drawLine(Color.Red, Offset(w + 50f, 0f), Offset(w - 10f, h), strokeWidth = 2f)
                    }
                    is SteelSectionType.LSection -> {
                        val t = (section.thickness / section.legA * h).toFloat().coerceIn(5f, 20f)
                        drawRect(Color.DarkGray, Offset(0f, 0f), Size(t, h))
                        drawRect(Color.DarkGray, Offset(t, h - t), Size(w - t, t))
                    }
                    is SteelSectionType.RHS -> {
                        val t = (section.thickness / section.height * h).toFloat().coerceIn(5f, 20f)
                        drawRect(Color.DarkGray, Offset(0f, 0f), size, style = androidx.compose.ui.graphics.drawscope.Stroke(t))
                    }
                    else -> {
                        drawRect(Color.DarkGray, Offset(0f, 0f), size)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("توزيع الإجهادات المرن", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BasePlateDesignTab() {
    var colSection by remember { mutableStateOf("HEB 300") }
    var bf by remember { mutableStateOf("300") }
    var dc by remember { mutableStateOf("300") }
    var axialLoad by remember { mutableStateOf("500") }
    var momentM by remember { mutableStateOf("0") }
    var fpc by remember { mutableStateOf("25") }
    var fy by remember { mutableStateOf("250") }
    var expandedBoltGrade by remember { mutableStateOf(false) }
    var selectedBoltGrade by remember { mutableStateOf("4.6") }
    var bpResult by remember { mutableStateOf<SteelBasePlateDesign.BasePlateResult?>(null) }

    val boltGradeOptions = com.civileg.app.domain.calculations.ecp.SteelBasePlateDesign.Companion.BoltGrade.entries.map { it.getGradeName() }

    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeader("🔧 تصميم القواعد المعدنية", R.drawable.ic_footing) }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("بيانات العمود", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(colSection, "القطاع (مثال: HEB 300)", { colSection = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(bf, "عرض الشفة bf (mm)", { bf = it }, Modifier.weight(1f))
                        SteelInputField(dc, "عمق العمود dc (mm)", { dc = it }, Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الأحمال المؤثرة", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(axialLoad, "القوة المحورية P (kN)", { axialLoad = it }, Modifier.weight(1f))
                        SteelInputField(momentM, "العزم M (kN.m) — اختياري", { momentM = it }, Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("خواص المواد", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(fpc, "مقاومة الخرسانة f'c (MPa)", { fpc = it }, Modifier.weight(1f))
                        SteelInputField(fy, "إجهاد الخضوع Fy (MPa)", { fy = it }, Modifier.weight(1f))
                    }

                    Text("درجة براغي الارتكاز", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    ExposedDropdownMenuBox(
                        expanded = expandedBoltGrade,
                        onExpandedChange = { expandedBoltGrade = !expandedBoltGrade }
                    ) {
                        OutlinedTextField(
                            value = "Grade $selectedBoltGrade",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBoltGrade) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        ExposedDropdownMenu(
                            expanded = expandedBoltGrade,
                            onDismissRequest = { expandedBoltGrade = false }
                        ) {
                            for (grade in boltGradeOptions) {
                                DropdownMenuItem(
                                    text = { Text("Grade $grade") },
                                    onClick = { selectedBoltGrade = grade; expandedBoltGrade = false }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(onClick = {
                val designer = SteelBasePlateDesign()
                val boltGrade = com.civileg.app.domain.calculations.ecp.SteelBasePlateDesign.Companion.BoltGrade.entries.find { it.getGradeName() == selectedBoltGrade }
                    ?: com.civileg.app.domain.calculations.ecp.SteelBasePlateDesign.Companion.BoltGrade.GRADE_4_6
                val input = SteelBasePlateDesign.ConcentricInput(
                    Pu = axialLoad.toDoubleOrNull() ?: 500.0,
                    Mux = momentM.toDoubleOrNull() ?: 0.0,
                    Muy = 0.0,
                    Vu = 0.0,
                    bf = bf.toDoubleOrNull() ?: 300.0,
                    dc = dc.toDoubleOrNull() ?: 300.0,
                    Fy = fy.toDoubleOrNull() ?: 250.0,
                    fpc = fpc.toDoubleOrNull() ?: 25.0,
                    boltGrade = boltGrade
                )
                bpResult = designer.designConcentricBasePlate(input)
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("تصميم القاعدة المعدنية")
            }
        }

        bpResult?.let { res ->
            item {
                val urColor = if (res.utilizationRatio <= 1.0) Color(0xFF2E7D32) else Color.Red
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, urColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (res.isSafe) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null, tint = urColor)
                            Spacer(Modifier.width(8.dp))
                            Text(if (res.isSafe) "التصميم آمن ✅" else "التصميم غير آمن ❌", fontWeight = FontWeight.Bold, color = urColor, fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        ResultRow("أبعاد اللوح (B × N)", "%.0f × %.0f mm".format(res.plateWidth, res.plateLength))
                        ResultRow("سماكة اللوح tp", "%.0f mm".format(res.plateThickness))
                        ResultRow("أقصى ضغط تحمل", "%.2f MPa".format(res.maxBearingPressure))
                        ResultRow("سعة الخرسانة", "%.2f MPa".format(res.concreteCapacity))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("البراغي الارتكازية", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        ResultRow("عدد البراغي", "${res.anchorBolts.numberOfBolts}")
                        ResultRow("قطر البرغي", "M${res.anchorBolts.boltDiameter.toInt()} (${res.anchorBolts.boltGrade})")
                        ResultRow("مسافة بين البراغي", "%.0f mm".format(res.anchorBolts.boltSpacing))
                        ResultRow("المسافة من الحافة", "%.0f mm".format(res.anchorBolts.edgeDistance))
                        ResultRow("سعة الشد لكل برغي", "%.1f kN".format(res.anchorBolts.tensionCapacity))
                        ResultRow("طول التثبيت", "%.0f mm".format(res.anchorBolts.embedmentLength))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("المونة (Grout)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        ResultRow("سماكة المونة", "%.0f mm".format(res.grout.thickness))
                        ResultRow("مقاومة المونة", "%.0f MPa".format(res.grout.providedStrength))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        ResultRow("معامل الاستغلال (U.R)", "%.2f".format(res.utilizationRatio))

                        if (res.warnings.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("⚠️ تحذيرات:", fontWeight = FontWeight.Bold, color = Color(0xFFF57C00), fontSize = 12.sp)
                            res.warnings.forEach { w ->
                                Text("• $w", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConnectionDesignTab() {
    var connectionType by remember { mutableIntStateOf(0) }
    val connTypes = listOf("وصلة مساميرية قص", "وصلة مساميرية عزم", "وصلة ملحومة", "وصلة مركبة")

    // Bolted inputs
    var boltDiameter by remember { mutableStateOf("20") }
    var numBolts by remember { mutableStateOf("4") }
    var boltSpacing by remember { mutableStateOf("75") }
    var edgeDistance by remember { mutableStateOf("40") }
    var appliedShear by remember { mutableStateOf("100") }
    var appliedTension by remember { mutableStateOf("0") }
    var expandedBoltGrade by remember { mutableStateOf(false) }
    var selectedBoltGrade by remember { mutableStateOf(BoltGrade.GRADE_8_8) }
    var expandedPattern by remember { mutableStateOf(false) }
    var selectedPattern by remember { mutableStateOf(BoltPattern.DOUBLE_ROW) }

    // Welded inputs
    var weldSize by remember { mutableStateOf("6") }
    var weldLength by remember { mutableStateOf("200") }
    var expandedElectrode by remember { mutableStateOf(false) }
    var selectedElectrode by remember { mutableStateOf(ElectrodeType.E70XX) }
    var appliedWeldForce by remember { mutableStateOf("100") }

    var boltResult by remember { mutableStateOf<BoltDesignResult?>(null) }
    var weldResult by remember { mutableStateOf<WeldDesignResult?>(null) }

    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeader("🔗 تصميم الوصلات الإنشائية", R.drawable.ic_frame) }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("نوع الوصلة", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow {
                        connTypes.forEachIndexed { idx, name ->
                            FilterChip(
                                selected = connectionType == idx,
                                onClick = { connectionType = idx; boltResult = null; weldResult = null },
                                label = { Text(name, fontSize = 11.sp) },
                                modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        if (connectionType < 3) {
            // Bolted or Combined
            item {
                Card(elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("بيانات المسامير", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SteelInputField(boltDiameter, "قطر المسمار (mm)", { boltDiameter = it }, Modifier.weight(1f))
                            SteelInputField(numBolts, "عدد المسامير", { numBolts = it }, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SteelInputField(boltSpacing, "المسافة بين المسامير (mm)", { boltSpacing = it }, Modifier.weight(1f))
                            SteelInputField(edgeDistance, "المسافة من الحافة (mm)", { edgeDistance = it }, Modifier.weight(1f))
                        }

                        Text("رتبة المسمار", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        ExposedDropdownMenuBox(
                            expanded = expandedBoltGrade,
                            onExpandedChange = { expandedBoltGrade = !expandedBoltGrade }
                        ) {
                            OutlinedTextField(
                                value = selectedBoltGrade.displayName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBoltGrade) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape = RoundedCornerShape(12.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            ExposedDropdownMenu(
                                expanded = expandedBoltGrade,
                                onDismissRequest = { expandedBoltGrade = false }
                            ) {
                                for (grade in BoltGrade.entries) {
                                    DropdownMenuItem(
                                        text = { Text(grade.displayName) },
                                        onClick = { selectedBoltGrade = grade; expandedBoltGrade = false }
                                    )
                                }
                            }
                        }

                        Text("نمط التوزيع", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        ExposedDropdownMenuBox(
                            expanded = expandedPattern,
                            onExpandedChange = { expandedPattern = !expandedPattern }
                        ) {
                            OutlinedTextField(
                                value = selectedPattern.displayName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPattern) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape = RoundedCornerShape(12.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            ExposedDropdownMenu(
                                expanded = expandedPattern,
                                onDismissRequest = { expandedPattern = false }
                            ) {
                                for (pattern in BoltPattern.entries) {
                                    DropdownMenuItem(
                                        text = { Text(pattern.displayName) },
                                        onClick = { selectedPattern = pattern; expandedPattern = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("الأحمال المؤثرة", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SteelInputField(appliedShear, "القص Vu (kN)", { appliedShear = it }, Modifier.weight(1f))
                            if (connectionType != 0) {
                                SteelInputField(appliedTension, "الشد Tu (kN)", { appliedTension = it }, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        if (connectionType >= 2) {
            // Welded or Combined
            item {
                Card(elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("بيانات اللحام", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SteelInputField(weldSize, "مقاس اللحام (mm)", { weldSize = it }, Modifier.weight(1f))
                            SteelInputField(weldLength, "طول اللحام (mm)", { weldLength = it }, Modifier.weight(1f))
                        }
                        SteelInputField(appliedWeldForce, "القوة المؤثرة (kN)", { appliedWeldForce = it }, Modifier.fillMaxWidth())

                        Text("نوع القطب (Electrode)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        ExposedDropdownMenuBox(
                            expanded = expandedElectrode,
                            onExpandedChange = { expandedElectrode = !expandedElectrode }
                        ) {
                            OutlinedTextField(
                                value = selectedElectrode.displayName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedElectrode) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape = RoundedCornerShape(12.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            ExposedDropdownMenu(
                                expanded = expandedElectrode,
                                onDismissRequest = { expandedElectrode = false }
                            ) {
                                ElectrodeType.entries.forEach { electrode ->
                                    DropdownMenuItem(
                                        text = { Text(electrode.displayName) },
                                        onClick = { selectedElectrode = electrode; expandedElectrode = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(onClick = {
                val designer = SteelConnectionDesign()
                if (connectionType < 3) {
                    val connType = when (connectionType) {
                        0 -> BoltConnectionType.BEARING
                        1 -> BoltConnectionType.COMBINED
                        else -> BoltConnectionType.BEARING
                    }
                    boltResult = designer.designBoltedConnection(
                        boltDiameter = boltDiameter.toDoubleOrNull() ?: 20.0,
                        boltGrade = selectedBoltGrade,
                        numberOfBolts = numBolts.toIntOrNull() ?: 4,
                        boltPattern = selectedPattern,
                        boltConnectionType = connType,
                        appliedShear = appliedShear.toDoubleOrNull() ?: 0.0,
                        appliedTension = appliedTension.toDoubleOrNull() ?: 0.0,
                        edgeDistance = edgeDistance.toDoubleOrNull(),
                        spacing = boltSpacing.toDoubleOrNull()
                    )
                }
                if (connectionType >= 2) {
                    weldResult = designer.designWeldedConnection(
                        weldType = WeldType.FILLET,
                        weldSize = weldSize.toDoubleOrNull() ?: 6.0,
                        weldLength = weldLength.toDoubleOrNull() ?: 200.0,
                        electrodeType = selectedElectrode,
                        appliedForce = appliedWeldForce.toDoubleOrNull() ?: 100.0
                    )
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("تصميم وفحص الوصلة")
            }
        }

        boltResult?.let { res ->
            item {
                val urColor = if (res.isSafe) Color(0xFF2E7D32) else Color.Red
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, urColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (res.isSafe) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null, tint = urColor)
                            Spacer(Modifier.width(8.dp))
                            Text("نتيجة الوصلة المساميرية — ${if (res.isSafe) "آمنة ✅" else "فاشلة ❌"}", fontWeight = FontWeight.Bold, color = urColor)
                        }
                        Spacer(Modifier.height(12.dp))
                        ResultRow("مقاومة القص لكل مسمار", "%.1f kN".format(res.shearCapacity))
                        ResultRow("مقاومة التحمل لكل مسمار", "%.1f kN".format(res.bearingCapacity))
                        ResultRow("مقاومة الشد لكل مسمار", "%.1f kN".format(res.tensionCapacity))
                        ResultRow("المقاومة الحاكمة", "%.1f kN".format(res.controllingCapacity))
                        ResultRow("معامل الاستغلال (U.R)", "%.2f".format(res.utilizationRatio))
                        ResultRow("المسافة من الحافة", "%.0f mm".format(res.edgeDistance))
                        ResultRow("المسافة بين المسامير", "%.0f mm".format(res.boltSpacing))
                        res.blockShearCheck?.let { bs ->
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("فحص القص الكتلي (Block Shear)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            ResultRow("سعة القص الكتلي", "%.1f kN".format(bs.capacity))
                            ResultRow("معامل الاستغلال", "%.2f".format(bs.utilizationRatio))
                        }
                        if (res.warnings.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            for (w in res.warnings) { Text("• $w", fontSize = 11.sp, color = Color.Gray) }
                        }
                    }
                }
            }
        }

        weldResult?.let { res ->
            item {
                val urColor = if (res.isSafe) Color(0xFF2E7D32) else Color.Red
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, urColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (res.isSafe) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null, tint = urColor)
                            Spacer(Modifier.width(8.dp))
                            Text("نتيجة اللحام — ${if (res.isSafe) "آمن ✅" else "فاشل ❌"}", fontWeight = FontWeight.Bold, color = urColor)
                        }
                        Spacer(Modifier.height(12.dp))
                        ResultRow("نوع اللحام", res.weldType.displayName)
                        ResultRow("مقاس اللحام", "%.1f mm".format(res.weldSize))
                        ResultRow("الطول الفعال", "%.1f mm".format(res.weldLength))
                        ResultRow("مساحة الحلق الفعالة", "%.1f mm²".format(res.throatArea))
                        ResultRow("مقاومة اللحام", "%.2f kN".format(res.capacity))
                        ResultRow("معامل الاستغلال (U.R)", "%.2f".format(res.utilizationRatio))
                        if (res.warnings.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            for (w in res.warnings) { Text("• $w", fontSize = 11.sp, color = Color.Gray) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, iconRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(painterResource(id = iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SteelInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PropertyLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}


private fun Color.toArgbInt(): Int {
    val a = (alpha * 255).toInt() and 0xFF
    val r = (red * 255).toInt() and 0xFF
    val g = (green * 255).toInt() and 0xFF
    val b = (blue * 255).toInt() and 0xFF
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
