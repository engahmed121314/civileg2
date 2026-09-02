package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.civileg.core.calculations.entities.ProjectSummary
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
    onNavigateBack: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Project Executive Summary", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KPICard("Total Cost", "${String.format(Locale.US, "%,.0f", summary.totalCost)}", "EGP", Modifier.weight(1f))
        KPICard("Concrete", "${String.format(Locale.US, "%.1f", summary.totalConcrete)}", "m³", Modifier.weight(1f))
        KPICard("Steel", "${String.format(Locale.US, "%,.0f", summary.totalSteel)}", "kg", Modifier.weight(1f))
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
