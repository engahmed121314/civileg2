package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.R
import com.civileg.app.utils.*
import java.io.File

@Composable
fun SiteLayoutScreen() {
    val context = LocalContext.current
    var plotWidth by remember { mutableStateOf("20") }
    var plotLength by remember { mutableStateOf("30") }
    var soilCapacity by remember { mutableStateOf("200") }
    
    val columns = remember { mutableStateListOf<ColumnLoad>() }
    var recommendation by remember { mutableStateOf<LayoutRecommendation?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.site_layout_title), style = MaterialTheme.typography.headlineSmall)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = plotWidth, onValueChange = { plotWidth = it }, label = { Text(stringResource(R.string.plot_width_label_m)) }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = plotLength, onValueChange = { plotLength = it }, label = { Text(stringResource(R.string.plot_length_label_m)) }, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        Button(onClick = {
            columns.add(ColumnLoad("C${columns.size + 1}", 2000.0 * columns.size, 2000.0 * columns.size, 1000.0))
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(stringResource(R.string.add_column_manual))
        }

        LazyColumn(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            items(columns) { col ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(col.id, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("X: ${col.x.toInt()} Y: ${col.y.toInt()} | P: ${col.axialLoad} kN")
                    }
                }
            }
        }

        Button(
            onClick = {
                recommendation = LayoutOptimizer.analyzeLayout(
                    plotWidth.toDoubleOrNull() ?: 20.0,
                    plotLength.toDoubleOrNull() ?: 30.0,
                    columns,
                    soilCapacity.toDoubleOrNull() ?: 200.0,
                    CalculatorEngine.DesignCode.EGYPTIAN
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Default.Analytics, contentDescription = null)
            Text(stringResource(R.string.analyze_generate_axes))
        }

        recommendation?.let { rec ->
            RecommendationCard(rec)
            
            Button(
                onClick = {
                    val file = DxfExporter.exportSiteLayout(
                        columns,
                        plotWidth.toDoubleOrNull() ?: 20.0,
                        plotLength.toDoubleOrNull() ?: 30.0,
                        File(context.cacheDir, "Site_Layout.dxf").absolutePath
                    )
                    ExportUtils.openFile(context, file, "application/dxf")
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.export_autocad_dxf))
            }
        }
    }
}

@Composable
fun RecommendationCard(rec: LayoutRecommendation) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.expert_recommendation), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(rec.suggestedType, style = MaterialTheme.typography.headlineSmall)
            Text(rec.description, style = MaterialTheme.typography.bodyMedium)
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Text(stringResource(R.string.site_coverage_overlaps_format, (rec.coverageRatio * 100).toInt(), rec.overlapsFound), style = MaterialTheme.typography.labelLarge)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color.White).border(1.dp, Color.Gray)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    rec.axesX.forEach { x ->
                        drawLine(Color.Red, Offset(x.toFloat() * 0.01f, 0f), Offset(x.toFloat() * 0.01f, size.height), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f)))
                    }
                    rec.axesY.forEach { y ->
                        drawLine(Color.Red, Offset(0f, y.toFloat() * 0.01f), Offset(size.width, y.toFloat() * 0.01f), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f)))
                    }
                }
                Text(stringResource(R.string.site_axes_map_preview), modifier = Modifier.align(Alignment.Center), color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}
