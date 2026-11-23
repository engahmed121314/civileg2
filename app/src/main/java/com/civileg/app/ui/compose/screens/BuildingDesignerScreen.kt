package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.civileg.app.domain.calculations.BuildingLoadDistributionEngine
import com.civileg.app.domain.calculations.BuildingQuantityEngine
import com.civileg.app.domain.entities.*
import com.civileg.app.pricing.LivePriceFetcher
import com.civileg.app.pricing.PriceManager
import com.civileg.app.ui.compose.components.editor.BuildingCanvas
import com.civileg.app.ui.compose.components.editor.BuildingDesignerToolbar
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor
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
                    BuildingNode("N1", 0.0, 0.0),
                    BuildingNode("N2", 5.0, 0.0),
                    BuildingNode("N3", 5.0, 5.0),
                    BuildingNode("N4", 0.0, 5.0)
                ),
                footings = listOf(
                    BuildingFooting("F1", 0.0, 0.0),
                    BuildingFooting("F2", 5.0, 0.0),
                    BuildingFooting("F3", 5.0, 5.0),
                    BuildingFooting("F4", 0.0, 5.0)
                ),
                columns = listOf(
                    BuildingColumn("C1", 0.0, 0.0),
                    BuildingColumn("C2", 5.0, 0.0),
                    BuildingColumn("C3", 5.0, 5.0),
                    BuildingColumn("C4", 0.0, 5.0)
                ),
                slabs = listOf(
                    BuildingSlab("S1", listOf("N1", "N2", "N3", "N4"))
                )
            )
        )
    }

    var activeTool by remember { mutableStateOf("Footing") }
    var showBoqDialog by remember { mutableStateOf(false) }
    var quantitySummary by remember {
        mutableStateOf<com.civileg.app.domain.entities.BuildingQuantitySummary?>(null)
    }

    // Re-calculate quantities whenever model changes
    LaunchedEffect(model) {
        val prices = PriceManager.getAllPrices(context).mapValues { it.value.price }
        quantitySummary = try {
            BuildingQuantityEngine.calculateQuantities(model, prices)
        } catch (exception: IllegalArgumentException) {
            snackbarHostState.showSnackbar(
                context.getString(com.civileg.app.R.string.quantity_geometry_invalid)
            )
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AppText(androidx.compose.ui.res.stringResource(com.civileg.app.R.string.bd_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = androidx.compose.ui.res.stringResource(com.civileg.app.R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            LivePriceFetcher.fetchLivePrices(context)
                            snackbarHostState.showSnackbar(
                                context.getString(com.civileg.app.R.string.live_prices_unavailable)
                            )
                        }
                    }) {
                        Icon(Icons.Default.CloudSync, contentDescription = androidx.compose.ui.res.stringResource(com.civileg.app.R.string.bd_refresh_prices))
                    }
                    IconButton(onClick = { showBoqDialog = true }) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = androidx.compose.ui.res.stringResource(com.civileg.app.R.string.bd_boq))
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
                        AppText(
                            context.getString(com.civileg.app.R.string.quantity_geometry_invalid),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        AppText(
                            androidx.compose.ui.res.stringResource(com.civileg.app.R.string.bd_estimated_cost, summary.totalCost),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    AppButton(
                        label = androidx.compose.ui.res.stringResource(com.civileg.app.R.string.bd_run_design),
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(com.civileg.app.R.string.load_distribution_unavailable)
                                )
                            }
                        },
                        fillMaxWidth = false,
                        icon = Icons.Default.Calculate
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Non-overlapping horizontal toolbar at top
            BuildingDesignerToolbar(
                activeTool = activeTool,
                onToolSelected = { activeTool = it },
                modifier = Modifier.fillMaxWidth()
            )

            // Canvas below toolbar taking remaining space
            BuildingCanvas(
                model = model,
                activeTool = activeTool,
                onNodeClicked = { x, y ->
                    when (activeTool) {
                        "Footing" -> {
                            val newFooting = BuildingFooting("F_${System.currentTimeMillis()}", x, y)
                            model = model.copy(footings = model.footings + newFooting)
                        }
                        "Column" -> {
                            val newCol = BuildingColumn("C_${System.currentTimeMillis()}", x, y)
                            model = model.copy(columns = model.columns + newCol)
                        }
                        "Steel" -> {
                            val newMember = BuildingSteelMember("M_${System.currentTimeMillis()}", "N1", "N2", "IPE 300", 5.0, 42.2)
                            model = model.copy(steelMembers = model.steelMembers + newMember)
                        }
                        "Erase" -> {
                            model = model.copy(
                                footings = model.footings.filterNot { kotlin.math.hypot(it.x - x, it.y - y) < 1.0 },
                                columns = model.columns.filterNot { kotlin.math.hypot(it.x - x, it.y - y) < 1.0 }
                            )
                        }
                        "Slab" -> {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(com.civileg.app.R.string.slab_tool_unavailable)
                                )
                            }
                        }
                        else -> {
                            val newNode = BuildingNode("N_${System.currentTimeMillis()}", x, y)
                            model = model.copy(nodes = model.nodes + newNode)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }

    if (showBoqDialog) {
        AlertDialog(
            onDismissRequest = { showBoqDialog = false },
            title = { AppText(androidx.compose.ui.res.stringResource(com.civileg.app.R.string.bd_boq_title)) },
            text = {
                val summary = quantitySummary
                if (summary == null) {
                    AppText(context.getString(com.civileg.app.R.string.quantity_geometry_invalid))
                } else {
                    Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                        AppText(
                            context.getString(com.civileg.app.R.string.quantity_estimate_notice),
                            style = MaterialTheme.typography.bodySmall
                        )
                        AppText(androidx.compose.ui.res.stringResource(com.civileg.app.R.string.bd_total_concrete, summary.totalConcreteVolumeM3), style = MaterialTheme.typography.bodyLarge)
                        AppText(androidx.compose.ui.res.stringResource(com.civileg.app.R.string.bd_total_rebar, summary.totalRebarWeightTon), style = MaterialTheme.typography.bodyLarge)
                        AppText(androidx.compose.ui.res.stringResource(com.civileg.app.R.string.bd_total_steel, summary.totalSteelProfileWeightTon), style = MaterialTheme.typography.bodyLarge)
                        AppText(androidx.compose.ui.res.stringResource(com.civileg.app.R.string.bd_total_formwork, summary.totalFormworkAreaM2), style = MaterialTheme.typography.bodyLarge)
                        AppText(androidx.compose.ui.res.stringResource(com.civileg.app.R.string.bd_total_excavation, summary.totalExcavationVolumeM3), style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(12.dp))
                        AppText(androidx.compose.ui.res.stringResource(com.civileg.app.R.string.bd_total_cost, summary.totalCost), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        AppText(androidx.compose.ui.res.stringResource(com.civileg.app.R.string.bd_boq_items), style = MaterialTheme.typography.titleSmall)
                        summary.boqItems.forEach { item ->
                            AppText(androidx.compose.ui.res.stringResource(com.civileg.app.R.string.bd_boq_item_line, item.descriptionAr, item.quantity, item.unitAr, item.totalPrice))
                        }
                    }
                }
            },
            confirmButton = {
                AppButton(
                    label = androidx.compose.ui.res.stringResource(com.civileg.app.R.string.close),
                    onClick = { showBoqDialog = false },
                    style = AppButtonStyle.Text,
                    fillMaxWidth = false
                )
            }
        )
    }
}
