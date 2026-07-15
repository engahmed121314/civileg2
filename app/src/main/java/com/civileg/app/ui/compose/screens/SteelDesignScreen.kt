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
import androidx.compose.ui.graphics.Path
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
    Card(
        modifier = Modifier.fillMaxWidth().height(220.dp).padding(vertical = 8.dp),
        border = BorderStroke(1.dp, Color.LightGray)
    ) {
        ComposeCanvas(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
            val padding = 40.dp.toPx()
            val w = size.width - 2 * padding
            val h = size.height - 2 * padding
            val baseY = size.height - padding
            
            // Scale calculations
            val scale = (w / inputs.span).toFloat()
            val eh = (inputs.eaveHeight * scale).toFloat()
            val rh = (inputs.ridgeHeight * scale).toFloat()
            val midX = padding + w / 2
            
            // Draw Main Frame Outline
            val framePath = Path().apply {
                moveTo(padding, baseY)
                lineTo(padding, baseY - eh)
                lineTo(midX, baseY - rh)
                lineTo(padding + w, baseY - eh)
                lineTo(padding + w, baseY)
            }
            drawPath(framePath, Color.Gray, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
            
            // Draw Distributed Load Arrows
            val loadColor = Color(0xFFE53935)
            for (i in 0..12) {
                val x = padding + (i * w / 12)
                val y = if (x <= midX) {
                    baseY - eh - (x - padding) / (midX - padding) * (rh - eh)
                } else {
                    baseY - rh + (x - midX) / (padding + w - midX) * (rh - eh)
                }
                drawLine(loadColor, Offset(x, y - 25f), Offset(x, y), strokeWidth = 2f)
                // Arrow heads
                drawLine(loadColor, Offset(x - 4f, y - 6f), Offset(x, y), strokeWidth = 2f)
                drawLine(loadColor, Offset(x + 4f, y - 6f), Offset(x, y), strokeWidth = 2f)
            }
            
            // Draw Moment Diagram (Simplified)
            val momentColor = Color(0xFF1E88E5)
            val momentPath = Path().apply {
                moveTo(padding, baseY - eh)
                // Simulated parabolic curve for moment
                cubicTo(
                    padding + w * 0.25f, baseY - eh + 50f,
                    padding + w * 0.75f, baseY - eh + 50f,
                    padding + w, baseY - eh
                )
            }
            drawPath(momentPath, momentColor.copy(alpha = 0.15f))
            drawPath(momentPath, momentColor, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            
            // Labels
            // Note: NativeCanvas is used for text if needed, but we'll keep it simple
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
    val views = listOf("الواجهة الأمامية", "المسقط الأفقي", "الواجهة الجانبية", "رسم ثلاثي الأبعاد")

    Column {
        ScrollableTabRow(selectedTabIndex = viewMode, edgePadding = 0.dp, containerColor = Color.Transparent) {
            views.forEachIndexed { index, title ->
                Tab(selected = viewMode == index, onClick = { viewMode = index }, text = { Text(title, fontSize = 10.sp) })
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().height(350.dp).padding(vertical = 8.dp),
            border = BorderStroke(1.dp, Color.LightGray)
        ) {
            ComposeCanvas(modifier = Modifier.fillMaxSize().background(Color.White)) {
                val padding = 50.dp.toPx()
                val scale = minOf(
                    (size.width - 2 * padding) / max(inputs.span, inputs.length),
                    (size.height - 2 * padding) / max(inputs.ridgeHeight, inputs.span)
                )
                val startX = padding
                val baseY = size.height - padding

                when (viewMode) {
                    0 -> { // Front Elevation
                        drawFrontElevation(inputs, scale, startX, baseY)
                    }
                    1 -> { // Plan View
                        drawPlanView(inputs, scale, startX, baseY)
                    }
                    2 -> { // Side View
                        drawSideElevation(inputs, scale, startX, baseY)
                    }
                    3 -> { // 3D Perspective
                        draw3DView(inputs, scale, startX, baseY)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFrontElevation(inputs: SteelWarehouseInputs, scale: Double, startX: Float, baseY: Float) {
    val colLX = startX
    val colRX = startX + (inputs.span * scale).toFloat()
    val eaveY = baseY - (inputs.eaveHeight * scale).toFloat()
    val ridgeY = baseY - (inputs.ridgeHeight * scale).toFloat()
    val midX = startX + (inputs.span * scale / 2).toFloat()

    // Columns
    drawLine(Color.Black, Offset(colLX, baseY), Offset(colLX, eaveY), strokeWidth = 8f)
    drawLine(Color.Black, Offset(colRX, baseY), Offset(colRX, eaveY), strokeWidth = 8f)
    // Rafters
    drawLine(Color.Black, Offset(colLX, eaveY), Offset(midX, ridgeY), strokeWidth = 8f)
    drawLine(Color.Black, Offset(colRX, eaveY), Offset(midX, ridgeY), strokeWidth = 8f)
    
    // Foundations
    drawRect(Color.Gray, Offset(colLX - 15f, baseY), Size(30f, 15f))
    drawRect(Color.Gray, Offset(colRX - 15f, baseY), Size(30f, 15f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlanView(inputs: SteelWarehouseInputs, scale: Double, startX: Float, baseY: Float) {
    val planW = (inputs.span * scale).toFloat()
    val planL = (inputs.length * scale).toFloat()
    val planTop = baseY - planL
    
    // Boundary
    drawRect(Color.Black, Offset(startX, planTop), Size(planW, planL), style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
    
    // Grid Lines (Bays)
    val numBays = ceil(inputs.length / inputs.baySpacing).toInt()
    for (i in 0..numBays) {
        val y = planTop + (i * inputs.baySpacing * scale).toFloat()
        drawLine(Color.LightGray, Offset(startX, y), Offset(startX + planW, y), strokeWidth = 1f)
        // Columns markers
        drawCircle(Color.Black, 4f, Offset(startX, y))
        drawCircle(Color.Black, 4f, Offset(startX + planW, y))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSideElevation(inputs: SteelWarehouseInputs, scale: Double, startX: Float, baseY: Float) {
    val planL = (inputs.length * scale).toFloat()
    val eaveH = (inputs.eaveHeight * scale).toFloat()
    val eaveY = baseY - eaveH
    
    // Ground
    drawLine(Color.Gray, Offset(startX, baseY), Offset(startX + planL, baseY), strokeWidth = 2f)
    
    // Columns
    val numBays = ceil(inputs.length / inputs.baySpacing).toInt()
    for (i in 0..numBays) {
        val x = startX + (i * inputs.baySpacing * scale).toFloat()
        drawLine(Color.Black, Offset(x, baseY), Offset(x, eaveY), strokeWidth = 6f)
    }
    // Eave Line
    drawLine(Color.Black, Offset(startX, eaveY), Offset(startX + planL, eaveY), strokeWidth = 6f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.draw3DView(inputs: SteelWarehouseInputs, scale: Double, startX: Float, baseY: Float) {
    val s = scale.toFloat() * 0.65f
    val depthOff = 35f
    
    // Draw from back to front for better perspective look
    for (i in 4 downTo 0) {
        val off = i * depthOff
        val x0 = startX + off
        val y0 = baseY - off
        val spanW = (inputs.span * s).toFloat()
        val eaveH = (inputs.eaveHeight * s).toFloat()
        val ridgeH = (inputs.ridgeHeight * s).toFloat()
        
        val color = if (i == 0) Color.Black else Color.Gray.copy(alpha = 0.4f)
        val stroke = if (i == 0) 3f else 1.5f
        
        // Sections of the frame
        drawLine(color, Offset(x0, y0), Offset(x0, y0 - eaveH), strokeWidth = stroke)
        drawLine(color, Offset(x0 + spanW, y0), Offset(x0 + spanW, y0 - eaveH), strokeWidth = stroke)
        drawLine(color, Offset(x0, y0 - eaveH), Offset(x0 + spanW / 2, y0 - ridgeH), strokeWidth = stroke)
        drawLine(color, Offset(x0 + spanW, y0 - eaveH), Offset(x0 + spanW / 2, y0 - ridgeH), strokeWidth = stroke)
        
        // Purlins/Girts connections (longitudinal)
        if (i > 0) {
            val px = x0 - depthOff
            val py = y0 + depthOff
            val lineCol = Color.LightGray.copy(alpha = 0.5f)
            drawLine(lineCol, Offset(x0, y0 - eaveH), Offset(px, py - eaveH), strokeWidth = 1f)
            drawLine(lineCol, Offset(x0 + spanW, y0 - eaveH), Offset(px + spanW, py - eaveH), strokeWidth = 1f)
            drawLine(lineCol, Offset(x0 + spanW / 2, y0 - ridgeH), Offset(px + spanW / 2, py - ridgeH), strokeWidth = 1f)
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
                drawRect(Color.DarkGray, Offset((w - t) / 2, 0f), Size(t, h))
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
                Text("🎨 الرسم الهندسي للقطاع", fontWeight = FontWeight.Bold)
                SteelSectionDrawing(res.sectionType)
            }

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
                        drawRect(Color.DarkGray, Offset((w - tw) / 2, tf), Size(tw, h - 2 * tf))
                        
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
