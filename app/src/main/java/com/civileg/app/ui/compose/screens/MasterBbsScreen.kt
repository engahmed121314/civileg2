package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.utils.BbsEntry
import com.civileg.app.utils.BbsGenerator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterBbsScreen(
    projectName: String,
    allEntries: List<BbsEntry>,
    onNavigateBack: () -> Unit = {}
) {
    val combinedBbs = remember(allEntries) { BbsGenerator.combineProjectBbs(listOf(allEntries)) }
    val optimization = remember(combinedBbs) { BbsGenerator.optimizeCutting(combinedBbs) }

    // §34 cutting plan outputs — computed once from the same optimization
    val cuttingEngine = remember { com.civileg.app.domain.usecases.AnalyzeRebarInventory() }
    val cutList = remember(combinedBbs) {
        cuttingEngine.buildCutList(
            combinedBbs.flatMap { e -> List(e.count) { e.totalLengthPerBar } }
                .filter { it > 0 && it <= 12000.0 }
                .map { it / 1000.0 }
        )
    }
    val cuttingDiagram = remember(combinedBbs) {
        val plans = cuttingEngine.optimizeCuttingMultiLength(
            stockLength = 12.0,
            requiredLengths = combinedBbs.flatMap { e -> List(e.count) { e.totalLengthPerBar } }
                .filter { it > 0 && it <= 12000.0 }
                .map { it / 1000.0 }
        )
        cuttingEngine.buildCuttingDiagram(plans)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Project Master BBS", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(projectName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text("Factory Fabrication List", style = MaterialTheme.typography.bodyMedium)
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Text(optimization, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // §34 Cutting diagram — per-bar layout with waste tail
            if (cuttingDiagram.isNotEmpty()) {
                item {
                    Text("Cutting Plan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                items(cuttingDiagram) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // §34 Cut list — aggregated workshop order
            if (cutList.isNotEmpty()) {
                item {
                    com.civileg.app.ui.designsystem.components.EngineeringTable(
                        columns = listOf(
                            com.civileg.app.ui.designsystem.components.TableColumn("Cut L (m)", 90, numeric = true),
                            com.civileg.app.ui.designsystem.components.TableColumn("Pieces", 70, numeric = true),
                            com.civileg.app.ui.designsystem.components.TableColumn("Total (m)", 90, numeric = true)
                        ),
                        rows = cutList.map { c ->
                            com.civileg.app.ui.designsystem.components.TableRowData(
                                cells = listOf(
                                    "%.2f".format(Locale.US, c.cutLengthM),
                                    "${c.count}",
                                    "%.2f".format(Locale.US, c.totalLengthM)
                                )
                            )
                        },
                        onRowClick = { }
                    )
                }
            }

            item {
                com.civileg.app.ui.designsystem.components.EngineeringTable(
                    columns = listOf(
                        com.civileg.app.ui.designsystem.components.TableColumn("Mark", 70),
                        com.civileg.app.ui.designsystem.components.TableColumn("Dia", 60, numeric = true),
                        com.civileg.app.ui.designsystem.components.TableColumn("Shape", 60, numeric = true),
                        com.civileg.app.ui.designsystem.components.TableColumn("A (mm)", 80, numeric = true),
                        com.civileg.app.ui.designsystem.components.TableColumn("B (mm)", 80, numeric = true),
                        com.civileg.app.ui.designsystem.components.TableColumn("Cut L (mm)", 90, numeric = true),
                        com.civileg.app.ui.designsystem.components.TableColumn("Qty", 60, numeric = true),
                        com.civileg.app.ui.designsystem.components.TableColumn("Weight (kg)", 100, numeric = true)
                    ),
                    rows = combinedBbs.map { entry ->
                        com.civileg.app.ui.designsystem.components.TableRowData(
                            cells = listOf(
                                "${entry.memberMark}/${entry.barMark}",
                                "Ø${entry.diameter}",
                                "${entry.shapeCode}",
                                "%.0f".format(Locale.US, entry.lengthA),
                                "%.0f".format(Locale.US, entry.lengthB),
                                "%.0f".format(Locale.US, entry.totalLengthPerBar),
                                "${entry.count}",
                                "%.1f".format(Locale.US, entry.totalWeightKg)
                            )
                        )
                    },
                    onRowClick = { }
                )
            }
        }
    }
}

@Composable
fun BbsListItem(entry: BbsEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Ø${entry.diameter} - Shape ${entry.shapeCode}", fontWeight = FontWeight.Bold)
                Text("L = ${entry.totalLengthPerBar.toInt()} mm", fontSize = 12.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Qty: ${entry.count}", fontWeight = FontWeight.Black)
                Text("${String.format(Locale.US, "%.1f", entry.totalWeightKg)} kg", fontSize = 11.sp)
            }
        }
    }
}
