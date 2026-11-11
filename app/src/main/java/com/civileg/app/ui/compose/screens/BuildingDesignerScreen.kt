package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.civileg.app.R
import com.civileg.app.domain.calculations.BuildingQuantityEngine
import com.civileg.app.domain.entities.*
import com.civileg.app.pricing.LivePriceFetcher
import com.civileg.app.pricing.PriceManager
import com.civileg.app.ui.compose.components.editor.BuildingCanvas
import com.civileg.app.ui.compose.components.editor.BuildingDesignerToolbar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildingDesignerScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var model by remember {
        mutableStateOf(
            BuildingModel(
                nodes = listOf(
                    BuildingNode("N1", 0.0, 0.0), BuildingNode("N2", 5.0, 0.0),
                    BuildingNode("N3", 5.0, 5.0), BuildingNode("N4", 0.0, 5.0)
                ),
                footings = listOf(
                    BuildingFooting("F1", 0.0, 0.0), BuildingFooting("F2", 5.0, 0.0),
                    BuildingFooting("F3", 5.0, 5.0), BuildingFooting("F4", 0.0, 5.0)
                ),
                columns = listOf(
                    BuildingColumn("C1", 0.0, 0.0), BuildingColumn("C2", 5.0, 0.0),
                    BuildingColumn("C3", 5.0, 5.0), BuildingColumn("C4", 0.0, 5.0)
                ),
                slabs = listOf(BuildingSlab("S1", listOf("N1", "N2", "N3", "N4")))
            )
        )
    }

    var activeTool by remember { mutableStateOf("Footing") }
    var showBoqDialog by remember { mutableStateOf(false) }
    var quantitySummary by remember { mutableStateOf<BuildingQuantitySummary?>(null) }

    LaunchedEffect(model) {
        val prices = PriceManager.getAllPrices(context).mapValues { it.value.price }
        quantitySummary = try {
            BuildingQuantityEngine.calculateQuantities(model, prices)
        } catch (exception: IllegalArgumentException) {
            snackbarHostState.showSnackbar(context.getString(R.string.quantity_geometry_invalid))
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.building_designer_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            LivePriceFetcher.fetchLivePrices(context)
                            snackbarHostState.showSnackbar(context.getString(R.string.live_prices_unavailable))
                        }
                    }) {
                        Icon(Icons.Default.CloudSync, contentDescription = stringResource(R.string.update_prices))
                    }
                    IconButton(onClick = { showBoqDialog = true }) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = stringResource(R.string.quantity_cost_table))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val summary = quantitySummary
                    if (summary == null) {
                        Text(context.getString(R.string.quantity_geometry_invalid), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("التكلفة التقديرية: ${summary.totalCost} ج.م", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Button(onClick = {
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.load_distribution_unavailable)) }
                    }) {
                        Icon(Icons.Default.Calculate, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.run_design_analysis))
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            BuildingDesignerToolbar(
                activeTool = activeTool,
                onToolSelected = { activeTool = it },
                modifier = Modifier.fillMaxWidth()
            )
            BuildingCanvas(
                model = model,
                activeTool = activeTool,
                onNodeClicked = { x, y ->
                    when (activeTool) {
                        "Footing" -> model = model.copy(footings = model.footings + BuildingFooting("F_${System.currentTimeMillis()}", x, y))
                        "Column" -> model = model.copy(columns = model.columns + BuildingColumn("C_${System.currentTimeMillis()}", x, y))
                        "Steel" -> model = model.copy(steelMembers = model.steelMembers + BuildingSteelMember("M_${System.currentTimeMillis()}", "N1", "N2", "IPE 300", 5.0, 42.2))
                        "Erase" -> model = model.copy(
                            footings = model.footings.filterNot { kotlin.math.hypot(it.x - x, it.y - y) < 1.0 },
                            columns = model.columns.filterNot { kotlin.math.hypot(it.x - x, it.y - y) < 1.0 }
                        )
                        "Slab" -> scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.slab_tool_unavailable)) }
                        else -> model = model.copy(nodes = model.nodes + BuildingNode("N_${System.currentTimeMillis()}", x, y))
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }

    if (showBoqDialog) {
        AlertDialog(
            onDismissRequest = { showBoqDialog = false },
            title = { Text(stringResource(R.string.quantity_cost_table)) },
            text = {
                val summary = quantitySummary
                if (summary == null) {
                    Text(context.getString(R.string.quantity_geometry_invalid))
                } else {
                    Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                        Text(context.getString(R.string.quantity_estimate_notice), style = MaterialTheme.typography.bodySmall)
                        Text("${stringResource(R.string.total_concrete)}: ${summary.totalConcreteVolumeM3} م³", style = MaterialTheme.typography.bodyLarge)
                        Text("${stringResource(R.string.total_rebar)}: ${summary.totalRebarWeightTon} طن", style = MaterialTheme.typography.bodyLarge)
                        Text("${stringResource(R.string.total_structural_steel)}: ${summary.totalSteelProfileWeightTon} طن", style = MaterialTheme.typography.bodyLarge)
                        Text("${stringResource(R.string.total_formwork)}: ${summary.totalFormworkAreaM2} م²", style = MaterialTheme.typography.bodyLarge)
                        Text("${stringResource(R.string.total_excavation)}: ${summary.totalExcavationVolumeM3} م³", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("${stringResource(R.string.total_estimated_cost)}: ${summary.totalCost} ج.م", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.boq_items), style = MaterialTheme.typography.titleSmall)
                        summary.boqItems.forEach { item ->
                            Text("• ${item.descriptionAr}: ${item.quantity} ${item.unitAr} (${item.totalPrice} ج.م)")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showBoqDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}
