package com.civileg.app.ui.compose.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
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
import com.civileg.app.domain.entities.*
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.PdfGenerator
import com.civileg.app.viewmodel.SteelViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteelDesignScreen(
    viewModel: SteelViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val result by viewModel.result.observeAsState()
    val warehouseResult by viewModel.warehouseResult.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("المستودعات", "القطاعات", "اللحام", "المسامير")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Steel Structure Design Pro", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }

            when (selectedTab) {
                0 -> SteelWarehouseTab(viewModel, warehouseResult, isLoading)
                1 -> SteelSectionTab(viewModel, result, isLoading)
                2 -> WeldDesignTab()
                3 -> BoltDesignTab()
            }
        }
    }
}

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
        item { SectionHeader("🏢 مدخلات تصميم المنشأ المعدني", R.drawable.ic_tools) }

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
                        val bitmap = createWarehouseBitmap(currentInputs, res)
                        val file = PdfGenerator.generateSteelWarehouseReport(context, currentInputs, res, bitmap)
                        openPdf(context, file)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إصدار التقرير الفني المعتمد (PDF)")
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
            SummaryLine("إجمالي وزن الحديد", "%.2f Ton".format(res.totalWeight), isBold = true)
            SummaryLine("معدل الاستهلاك", "%.1f kg/m²".format(res.weightPerM2))
            SummaryLine("مساحة التغطية", "%.0f m²".format(res.totalCladdingArea))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(res.resultsByCode, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
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
    Card(
        modifier = Modifier.fillMaxWidth().height(350.dp).padding(vertical = 8.dp),
        border = BorderStroke(1.dp, Color.LightGray)
    ) {
        ComposeCanvas(modifier = Modifier.fillMaxSize().background(Color.White)) {
            val padding = 50.dp.toPx()
            val scale = minOf((size.width - 2 * padding) / inputs.span, (size.height - 2 * padding) / (inputs.ridgeHeight + (inputs.numberOfStories-1)*inputs.eaveHeight))
            
            val startX = padding
            val baseY = size.height - padding
            
            // Draw Perspective Depth
            for (i in 1..4) {
                val off = i * 25f
                val dLX = startX + off
                val dRX = startX + (inputs.span * scale).toFloat() + off
                val dBY = baseY - off
                val dEY = dBY - (inputs.eaveHeight * scale).toFloat()
                drawLine(Color.LightGray, Offset(dLX, dBY), Offset(dRX, dBY), strokeWidth = 1f) // Ground
                drawLine(Color.LightGray, Offset(dLX, dBY), Offset(dLX, dEY), strokeWidth = 1f) // Column
            }

            for (floor in 0 until inputs.numberOfStories) {
                val floorBaseY = baseY - (floor * inputs.eaveHeight * scale).toFloat()
                val floorEaveY = floorBaseY - (inputs.eaveHeight * scale).toFloat()
                val colLX = startX
                val colRX = startX + (inputs.span * scale).toFloat()
                
                drawLine(Color.Black, Offset(colLX, floorBaseY), Offset(colLX, floorEaveY), strokeWidth = 6f)
                drawLine(Color.Black, Offset(colRX, floorBaseY), Offset(colRX, floorEaveY), strokeWidth = 6f)
                
                if (floor == inputs.numberOfStories - 1) {
                    val ridgeY = floorBaseY - (inputs.ridgeHeight * scale).toFloat()
                    val midX = startX + (inputs.span * scale / 2).toFloat()
                    drawLine(Color.Black, Offset(colLX, floorEaveY), Offset(midX, ridgeY), strokeWidth = 8f)
                    drawLine(Color.Black, Offset(colRX, floorEaveY), Offset(midX, ridgeY), strokeWidth = 8f)
                    
                    if (inputs.usePurlins) {
                        for (j in 1..6) {
                            val t = j.toFloat() / 6
                            drawCircle(Color.Blue, radius = 4f, center = Offset(colLX + (midX - colLX) * t, floorEaveY + (ridgeY - floorEaveY) * t))
                            drawCircle(Color.Blue, radius = 4f, center = Offset(colRX - (colRX - midX) * t, floorEaveY + (ridgeY - floorEaveY) * t))
                        }
                    }
                } else {
                    drawLine(Color.Black, Offset(colLX, floorEaveY), Offset(colRX, floorEaveY), strokeWidth = 8f)
                }
            }
            
            drawRect(Color.Gray, Offset(startX - 15f, baseY), Size(30f, 15f))
            drawRect(Color.Gray, Offset(startX + (inputs.span * scale).toFloat() - 15f, baseY), Size(30f, 15f))

            val paint = Paint().apply { color = android.graphics.Color.BLACK; textSize = 28f }
            drawContext.canvas.nativeCanvas.drawText("${inputs.span}m", startX + (inputs.span * scale / 2).toFloat(), baseY + 40f, paint)
        }
    }
}

@Composable
fun StructuralAnalysisVisualizer(inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
    Card(
        modifier = Modifier.fillMaxWidth().height(250.dp).padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA))
    ) {
        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
            val padding = 40.dp.toPx()
            val scale = minOf((size.width - 2*padding) / inputs.span, (size.height - 2*padding) / inputs.ridgeHeight)
            val startX = padding
            val baseY = size.height - padding
            
            val colLX = startX
            val colRX = startX + (inputs.span * scale).toFloat()
            val eaveY = baseY - (inputs.eaveHeight * scale).toFloat()
            val midX = startX + (inputs.span * scale / 2).toFloat()

            // Draw Loads (Red Arrows)
            for (i in 0..12) {
                val x = colLX + (colRX - colLX) * (i / 12f)
                drawLine(Color.Red, Offset(x, eaveY - 45f), Offset(x, eaveY), strokeWidth = 2f)
                drawLine(Color.Red, Offset(x - 4f, eaveY - 10f), Offset(x, eaveY), strokeWidth = 2f)
                drawLine(Color.Red, Offset(x + 4f, eaveY - 10f), Offset(x, eaveY), strokeWidth = 2f)
            }
            
            // Simplified BMD (Moment)
            val momentPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(colLX, eaveY)
                quadraticTo(midX, eaveY + 70f, colRX, eaveY)
            }
            drawPath(momentPath, Color.Blue, alpha = 0.3f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
            
            drawContext.canvas.nativeCanvas.drawText("TENSION ZONE", midX - 60f, eaveY + 90f, Paint().apply { textSize = 24f; color = android.graphics.Color.BLUE })
        }
    }
}

fun createWarehouseBitmap(inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult): Bitmap {
    val bitmap = Bitmap.createBitmap(1000, 800, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    
    val p = Paint().apply { color = android.graphics.Color.BLACK; strokeWidth = 6f; style = Paint.Style.STROKE }
    val textP = Paint().apply { color = android.graphics.Color.BLACK; textSize = 24f }

    val scale = minOf(800f / inputs.span.toFloat(), 500f / inputs.ridgeHeight.toFloat())
    val bY = 700f
    val pad = 100f
    val cLX = pad
    val cRX = pad + inputs.span.toFloat() * scale
    val eY = bY - inputs.eaveHeight.toFloat() * scale
    val rY = bY - inputs.ridgeHeight.toFloat() * scale
    val mX = pad + (inputs.span.toFloat() * scale / 2)

    canvas.drawLine(cLX, bY, cLX, eY, p)
    canvas.drawLine(cRX, bY, cRX, eY, p)
    canvas.drawLine(cLX, eY, mX, rY, p)
    canvas.drawLine(cRX, eY, mX, rY, p)
    
    canvas.drawText("TOTAL WEIGHT: ${"%.2f".format(result.totalWeight)} TONS", 100f, 50f, textP)
    canvas.drawText("RATIO: ${"%.1f".format(result.weightPerM2)} kg/m2", 100f, 90f, textP)

    return bitmap
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
        Toast.makeText(context, "No PDF Viewer found", Toast.LENGTH_SHORT).show()
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
                    Text("h=${section.depth}, bf=${section.flangeWidth}, tw=${section.webThickness}, tf=${section.flangeThickness} (mm)", fontSize = 11.sp)
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

@Composable
fun SteelSectionTab(viewModel: SteelViewModel, result: SteelMemberResult?, isLoading: Boolean) {
    var depth by remember { mutableStateOf("300") }
    var width by remember { mutableStateOf("200") }
    var tf by remember { mutableStateOf("12") }
    var tw by remember { mutableStateOf("8") }
    var selectedType by remember { mutableStateOf("I-Section") }
    val sectionTypes = listOf("I-Section", "Channel", "Angle", "Box")

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SectionHeader("📐 تحليل قطاع مفرد", R.drawable.ic_tools) }
        
        item {
            ScrollableTabRow(selectedTabIndex = sectionTypes.indexOf(selectedType), edgePadding = 0.dp, containerColor = Color.Transparent) {
                sectionTypes.forEach { type ->
                    Tab(selected = selectedType == type, onClick = { selectedType = type }, text = { Text(type, fontSize = 12.sp) })
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SteelInputField(depth, "العمق h (mm)", { depth = it }, Modifier.weight(1f))
                SteelInputField(width, "العرض bf (mm)", { width = it }, Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SteelInputField(tf, "سمك الشفة tf", { tf = it }, Modifier.weight(1f))
                SteelInputField(tw, "سمك العصب tw", { tw = it }, Modifier.weight(1f))
            }
        }

        item {
            Button(
                onClick = { 
                    val sec = when(selectedType) {
                        "I-Section" -> SteelSectionType.ISection(depth.toDoubleOrNull() ?: 300.0, width.toDoubleOrNull() ?: 200.0, tf.toDoubleOrNull() ?: 12.0, tw.toDoubleOrNull() ?: 8.0, SteelGrade.ST37)
                        "Channel" -> SteelSectionType.CSection(depth.toDoubleOrNull() ?: 300.0, width.toDoubleOrNull() ?: 100.0, tf.toDoubleOrNull() ?: 10.0, tw.toDoubleOrNull() ?: 6.0, SteelGrade.ST37)
                        else -> SteelSectionType.LSection(width.toDoubleOrNull() ?: 100.0, width.toDoubleOrNull() ?: 100.0, tf.toDoubleOrNull() ?: 10.0, SteelGrade.ST37)
                    }
                    viewModel.calculateSteelMember(
                        section = sec,
                        memberType = SteelMemberType.COLUMN,
                        inputs = SteelInputs(axialLoad = 500.0, moment = 100.0, shear = 50.0, unbracedLength = 3.0),
                        code = CalculatorEngine.DesignCode.EGYPTIAN
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("تحليل وتصميم القطاع")
            }
        }

        result?.let { res ->
            item { SteelResultCard(res) }
            item {
                Text("🎨 الرسم الهندسي للقطاع", fontWeight = FontWeight.Bold)
                SteelSectionDrawing(res.sectionType)
            }
        }
    }
}

@Composable
fun WeldDesignTab() {
    var size by remember { mutableStateOf("6") }
    var length by remember { mutableStateOf("200") }
    var selectedElectrode by remember { mutableStateOf(ElectrodeType.E70XX) }
    var capacity by remember { mutableDoubleStateOf(0.0) }

    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeader("🔥 تصميم وصلات اللحام", R.drawable.ic_tools) }
        
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SteelInputField(size, "مقاس اللحام s (mm)", { size = it }, Modifier.weight(1f))
                SteelInputField(length, "طول اللحام L (mm)", { length = it }, Modifier.weight(1f))
            }
        }
        
        item {
            Text("نوع الإلكترود")
            Row {
                ElectrodeType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedElectrode == type,
                        onClick = { selectedElectrode = type },
                        label = { Text(type.displayName) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }

        item {
            Button(onClick = {
                val s = size.toDoubleOrNull() ?: 6.0
                val l = length.toDoubleOrNull() ?: 200.0
                capacity = 0.707 * s * l * 0.4 * selectedElectrode.tensileStrength / 1000.0
            }, modifier = Modifier.fillMaxWidth()) {
                Text("حساب مقاومة اللحام")
            }
        }

        if (capacity > 0) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("مقاومة القص التصميمية (Rn):", fontWeight = FontWeight.Bold)
                        Text("${"%.2f".format(capacity)} kN", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun BoltDesignTab() {
    var diameter by remember { mutableStateOf("16") }
    var selectedGrade by remember { mutableStateOf(BoltGrade.GRADE_8_8) }
    var capacity by remember { mutableDoubleStateOf(0.0) }

    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeader("🔩 تصميم وصلات المسامير", R.drawable.ic_tools) }
        
        item {
            SteelInputField(diameter, "قطر المسمار (mm)", { diameter = it }, Modifier.fillMaxWidth())
        }

        item {
            Text("رتبة المسمار (Grade)")
            Row {
                BoltGrade.entries.forEach { grade ->
                    FilterChip(
                        selected = selectedGrade == grade,
                        onClick = { selectedGrade = grade },
                        label = { Text(grade.displayName) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }

        item {
            Button(onClick = {
                val d = diameter.toDoubleOrNull() ?: 16.0
                val area = 3.14159 * (d*d) / 4.0
                capacity = 0.6 * selectedGrade.fu * area / 1000.0
            }, modifier = Modifier.fillMaxWidth()) {
                Text("حساب مقاومة المسمار")
            }
        }

        if (capacity > 0) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("مقاومة المسمار الواحد (Rn):", fontWeight = FontWeight.Bold)
                        Text("${"%.2f".format(capacity)} kN", style = MaterialTheme.typography.headlineMedium)
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
    Box(modifier = Modifier.fillMaxWidth().height(200.dp).padding(16.dp), contentAlignment = Alignment.Center) {
        ComposeCanvas(modifier = Modifier.size(150.dp)) {
            val w = size.width
            val h = size.height
            val tf = 12f
            val tw = 8f
            drawRect(Color.DarkGray, Offset(0f, 0f), Size(w, tf))
            drawRect(Color.DarkGray, Offset(0f, h - tf), Size(w, tf))
            drawRect(Color.DarkGray, Offset((w - tw) / 2, tf), Size(tw, h - 2 * tf))
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
