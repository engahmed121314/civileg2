package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.ui.compose.components.drawings.drawTextAnnotated
import com.civileg.app.utils.*
import com.civileg.app.viewmodel.SiteLayoutViewModel
import java.io.File
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteLayoutScreen(
    viewModel: SiteLayoutViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val columns by viewModel.columns.observeAsState(emptyList())
    val recommendation by viewModel.recommendation.observeAsState()
    val plotW by viewModel.plotWidth.observeAsState(20.0)
    val plotL by viewModel.plotLength.observeAsState(30.0)

    var widthInput by remember { mutableStateOf(plotW.toString()) }
    var lengthInput by remember { mutableStateOf(plotL.toString()) }
    var soilInput by remember { mutableStateOf("200") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Site Layout & Axes Generator", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clear() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    }
                }
            )
        }
    ) { padding ->
        Row(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Left Panel: Controls
            Column(modifier = Modifier.weight(0.35f).fillMaxHeight().padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Site Parameters", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = widthInput, 
                    onValueChange = { 
                        widthInput = it
                        it.toDoubleOrNull()?.let { w -> viewModel.setPlotSize(w, lengthInput.toDoubleOrNull() ?: 30.0) }
                    }, 
                    label = { Text("Plot Width (m)") }, 
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = lengthInput, 
                    onValueChange = { 
                        lengthInput = it
                        it.toDoubleOrNull()?.let { l -> viewModel.setPlotSize(widthInput.toDoubleOrNull() ?: 20.0, l) }
                    }, 
                    label = { Text("Plot Length (m)") }, 
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = soilInput, 
                    onValueChange = { 
                        soilInput = it
                        it.toDoubleOrNull()?.let { s -> viewModel.setSoilCapacity(s) }
                    }, 
                    label = { Text("Soil Capacity (kPa)") }, 
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                // --- Staking Point Form ---
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Add Exact Point (Manual Staking)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        var nextX by remember { mutableStateOf("") }
                        var nextY by remember { mutableStateOf("") }
                        var nextP by remember { mutableStateOf("1000") }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = nextX, onValueChange = { nextX = it }, label = { Text("X Coord (m)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            OutlinedTextField(value = nextY, onValueChange = { nextY = it }, label = { Text("Y Coord (m)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        }
                        OutlinedTextField(value = nextP, onValueChange = { nextP = it }, label = { Text("Working Load P (kN)", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        
                        Button(
                            onClick = {
                                val x = nextX.toDoubleOrNull() ?: 0.0
                                val y = nextY.toDoubleOrNull() ?: 0.0
                                val p = nextP.toDoubleOrNull() ?: 1000.0
                                viewModel.addColumn(x * 1000.0, y * 1000.0, load = p)
                                nextX = ""; nextY = ""
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.AddLocation, null); Spacer(Modifier.width(8.dp)); Text("Add Point")
                        }
                    }
                }

                Text("Foundation Strategy", style = MaterialTheme.typography.titleMedium)
                
                recommendation?.let { rec ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(rec.suggestedType, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(rec.description, fontSize = 12.sp, lineHeight = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { rec.coverageRatio.toFloat() }, 
                                modifier = Modifier.fillMaxWidth(),
                                color = if (rec.coverageRatio > 0.7) Color.Red else Color.Green
                            )
                            Text("Coverage: ${(rec.coverageRatio * 100).toInt()}%", fontSize = 10.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // --- Phase 3 & 2: Project Summary Dashboard ---
                recommendation?.let { rec ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("SITE BUDGET & QUANTITIES", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Est. Concrete", fontSize = 10.sp, color = Color.Gray)
                                    Text("${"%.1f".format(rec.totalConcreteEst)} m³", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Est. Steel", fontSize = 10.sp, color = Color.Gray)
                                    Text("${"%.2f".format(rec.totalSteelEst / 1000.0)} Tons", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                            
                            val estCost = rec.totalConcreteEst * 5000 + (rec.totalSteelEst / 1000.0) * 55000
                            Spacer(Modifier.height(6.dp))
                            Text("Estimated Cost", fontSize = 10.sp, color = Color.Gray)
                            Text("${"%,.0f".format(estCost)} EGP", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                
                val csvPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let { viewModel.importPointsFromCsv(context, it) }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { csvPickerLauncher.launch("text/comma-separated-values") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.FileUpload, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Import CSV", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            // Automatic Column Generation (Auto-Staking)
                            viewModel.clear()
                            val spacing = 5.0 // 5m typical bay
                            var x = 0.0
                            while (x <= plotW) {
                                var y = 0.0
                                while (y <= plotL) {
                                    val isBoundary = x == 0.0 || x == plotW || y == 0.0 || y == plotL
                                    viewModel.addColumn(x * 1000.0, y * 1000.0, isNeighbor = isBoundary)
                                    y += spacing
                                }
                                x += spacing
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
                    ) {
                        Icon(Icons.Default.AutoFixHigh, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Auto-Layout", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        val file = DxfExporter.exportSiteLayout(
                            columns, plotW, plotL,
                            soilInput.toDoubleOrNull() ?: 200.0,
                            File(context.cacheDir, "Site_Layout_Pro.dxf").absolutePath
                        )
                        ExportUtils.openFile(context, file, "application/dxf")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Icon(Icons.Default.FileDownload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Export DXF (AutoCAD)")
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Columns (${columns.size})", style = MaterialTheme.typography.labelLarge)
                columns.forEach { col ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${col.id}: (${col.x.toInt()}, ${col.y.toInt()})", fontSize = 11.sp)
                            val fb = recommendation?.footingBounds?.find { it.id == col.id }
                            if ((fb?.numPiles ?: 0) > 0) {
                                Text("Piles: ${fb?.numPiles}", fontSize = 9.sp, color = Color.Cyan, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(onClick = { viewModel.removeColumn(col.id) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Right Panel: Interactive Canvas
            Box(modifier = Modifier.weight(0.65f).fillMaxHeight().background(Color(0xFF1E1E1E))) {
                var snapToGrid by remember { mutableStateOf(true) }
                var isNeighborMode by remember { mutableStateOf(false) }
                
                SiteInteractiveCanvas(
                    columns = columns,
                    plotW = plotW,
                    plotL = plotL,
                    recommendation = recommendation,
                    snapToGrid = snapToGrid,
                    onAddColumn = { x, y -> viewModel.addColumn(x, y, isNeighbor = isNeighborMode) }
                )
                
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = snapToGrid, onCheckedChange = { snapToGrid = it }, colors = CheckboxDefaults.colors(uncheckedColor = Color.Gray))
                        Text("Snap to 0.5m Grid", color = Color.White, fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = isNeighborMode, onCheckedChange = { isNeighborMode = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Red))
                        Spacer(Modifier.width(8.dp))
                        Text("Boundary/Neighbor Mode", color = if(isNeighborMode) Color.Red else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Text(
                    "Click on site to place columns", 
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun SiteInteractiveCanvas(
    columns: List<ColumnLoad>,
    plotW: Double,
    plotL: Double,
    recommendation: LayoutRecommendation?,
    snapToGrid: Boolean = true,
    onAddColumn: (Double, Double) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cw = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
        val ch = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
        
        val scale = minOf(cw / plotW.toFloat(), ch / plotL.toFloat()) * 0.9f
        val dw = plotW.toFloat() * scale
        val dl = plotL.toFloat() * scale
        
        val ox = (cw - dw) / 2f
        val oy = (ch - dl) / 2f

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        var rx = ((offset.x - ox) / scale).toDouble()
                        var ry = ((offset.y - oy) / scale).toDouble()
                        
                        if (snapToGrid) {
                            // Snap to nearest 0.5m (500mm)
                            rx = (rx * 2).roundToInt() / 2.0
                            ry = (ry * 2).roundToInt() / 2.0
                        }

                        if (rx in 0.0..plotW && ry in 0.0..plotL) {
                            onAddColumn(rx * 1000.0, ry * 1000.0) // Store in mm
                        }
                    }
                }
        ) {
            drawRect(Color.White, Offset(ox, oy), Size(dw, dl), style = Stroke(2f))
            
            val gridStep = 5.0f * scale
            var gx = ox
            while (gx <= ox + dw + 0.1f) {
                drawLine(Color.DarkGray.copy(alpha = 0.3f), Offset(gx, oy), Offset(gx, oy + dl), 1f)
                gx += gridStep
            }
            var gy = oy
            while (gy <= oy + dl + 0.1f) {
                drawLine(Color.DarkGray.copy(alpha = 0.3f), Offset(ox, gy), Offset(ox + dw, gy), 1f)
                gy += gridStep
            }

            recommendation?.let { rec ->
                // Draw Calculated Footing Boundaries
                rec.footingBounds.forEach { fb ->
                    val fx = ox + fb.centerX.toFloat() * scale / 1000.0f
                    val fy = oy + fb.centerY.toFloat() * scale / 1000.0f
                    val fw = fb.width.toFloat() * scale / 1000.0f
                    val fl = fb.length.toFloat() * scale / 1000.0f
                    
                    val fColor = when(fb.type) {
                        "Boundary" -> Color(0xFFE57373)
                        else -> Color(0xFF81C784)
                    }
                    
                    drawRect(
                        color = fColor.copy(alpha = 0.25f),
                        topLeft = Offset(fx - fw/2f, fy - fl/2f),
                        size = Size(fw, fl)
                    )
                    drawRect(
                        color = fColor,
                        topLeft = Offset(fx - fw/2f, fy - fl/2f),
                        size = Size(fw, fl),
                        style = Stroke(1.5f)
                    )
                }

                rec.axesX.forEach { axis ->
                    val sx = ox + axis.coordinate.toFloat() * scale / 1000.0f
                    drawLine(Color(0xFFE91E63).copy(alpha = 0.7f), Offset(sx, oy - 20f), Offset(sx, oy + dl + 20f), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f)))
                    drawTextAnnotated(axis.label, sx, oy - 25f, Color(0xFFE91E63), 10f * density, center = true)
                }
                rec.axesY.forEach { axis ->
                    val sy = oy + axis.coordinate.toFloat() * scale / 1000.0f
                    drawLine(Color(0xFFE91E63).copy(alpha = 0.7f), Offset(ox - 20f, sy), Offset(ox + dw + 20f, sy), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f)))
                    drawTextAnnotated(axis.label, ox - 30f, sy, Color(0xFFE91E63), 10f * density, center = true)
                }
            }

            columns.forEach { col ->
                val cx = ox + col.x.toFloat() * scale / 1000.0f
                val cy = oy + col.y.toFloat() * scale / 1000.0f
                
                // Column body
                drawRect(if(col.isNeighbor) Color.Red else Color.Cyan, Offset(cx - 6f, cy - 6f), Size(12f, 12f))
                drawRect(Color.White, Offset(cx - 6f, cy - 6f), Size(12f, 12f), style = Stroke(1f))
                
                drawTextAnnotated(col.id, cx + 10f, cy - 10f, Color.White, 8f * density)
            }
        }
    }
}
