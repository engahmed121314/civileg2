package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.civileg.app.domain.entities.ProjectSummary
import com.civileg.app.viewmodel.ProjectViewModel
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSummaryScreen(
    summary: ProjectSummary,
    projectName: String,
    projectId: Long,
    viewModel: ProjectViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Project Executive Summary", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.exportProjectSummaryToPdf(context, projectId, projectName)
                        }
                    ) {
                        Icon(Icons.Default.PictureAsPdf, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(projectName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            
            SummaryKPIRow(summary)
            
            Text("Cost Breakdown by Element", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                AndroidView(factory = { context ->
                    PieChart(context).apply {
                        description.isEnabled = false
                        legend.isEnabled = true
                        legend.textColor = android.graphics.Color.GRAY
                        setEntryLabelColor(android.graphics.Color.BLACK)
                        animateY(1000)
                    }
                }, update = { chart ->
                    val entries = summary.costBreakdown.map { PieEntry(it.value.toFloat(), it.key) }
                    val dataSet = PieDataSet(entries, "")
                    dataSet.colors = ColorTemplate.VORDIPLOM_COLORS.toList()
                    dataSet.valueTextSize = 12f
                    chart.data = PieData(dataSet)
                    chart.invalidate()
                })
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Efficiency Insights", fontWeight = FontWeight.Bold)
                    Text("Construction IQ Index: ${String.format(Locale.US, "%.2f", summary.costEfficiencyIndex)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Potential waste savings identified: 5-8% via optimized BBS.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
fun SummaryKPIRow(summary: ProjectSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KPICard("Total Cost", "${String.format(Locale.US, "%,.0f", summary.totalCost)}", "EGP", Modifier.weight(1f))
            KPICard("Concrete", "${String.format(Locale.US, "%.1f", summary.totalConcrete)}", "m³", Modifier.weight(1f))
            KPICard("Steel", "${String.format(Locale.US, "%,.0f", summary.totalSteel)}", "kg", Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val steelDensity = if (summary.totalConcrete > 0) summary.totalSteel / summary.totalConcrete else 0.0
            KPICard("Steel Density", "${String.format(Locale.US, "%.1f", steelDensity)}", "kg/m³", Modifier.weight(1f))
            KPICard("Design Count", "${summary.designCount}", "Elements", Modifier.weight(1f))
            KPICard("Avg Cost/Elem", "${if(summary.designCount > 0) (summary.totalCost/summary.designCount).toInt() else 0}", "EGP", Modifier.weight(1f))
        }
    }
}

@Composable
fun KPICard(label: String, value: String, unit: String, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}
