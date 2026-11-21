package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
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
    val geometryInvalidMessage = stringResource(com.civileg.app.R.string.quantity_geometry_invalid)
    LaunchedEffect(model) {
        val prices = PriceManager.getAllPrices(context).mapValues { it.value.price }
        quantitySummary = try {
            BuildingQuantityEngine.calculateQuantities(model, prices)
        } catch (exception: IllegalArgumentException) {
            snackbarHostState.showSnackbar(geometryInvalidMessage)
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AppText("المصمم الإنشائي وحساب الكميات (2D Building Designer)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    val livePricesUnavailable = stringResource(com.civileg.app.R.string.live_prices_unavailable)
                    IconButton(onClick = {
                        scope.launch {
                            LivePriceFetcher.fetchLivePrices(context)
                            snackbarHostState.showSnackbar(livePricesUnavailable)
                        }
                    }) {
                        Icon(Icons.Default.CloudSync, contentDescription = "تحديث الأسعار")
                    }
                    IconButton(onClick = { showBoqDialog = true }) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = "جدول الكميات والتكلفة")
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
                            geometryInvalidMessage,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        AppText(
                            "التكلفة التقديرية: ${summary.totalCost} ج.م",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    AppButton(
                        label = "تشغيل التصميم والتحليل",
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
            title = { AppText("جدول الكميات والتكلفة التقديرية (BOQ)") },
            text = {
                val summary = quantitySummary
                if (summary == null) {
                    AppText(geometryInvalidMessage)
                } else {
                    Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                        AppText(
                            context.getString(com.civileg.app.R.string.quantity_estimate_notice),
                            style = MaterialTheme.typography.bodySmall
                        )
                        AppText("إجمالي الخرسانة: ${summary.totalConcreteVolumeM3} م³", style = MaterialTheme.typography.bodyLarge)
                        AppText("إجمالي حديد التسليح: ${summary.totalRebarWeightTon} طن", style = MaterialTheme.typography.bodyLarge)
                        AppText("إجمالي حديد الستيل الهيكلي: ${summary.totalSteelProfileWeightTon} طن", style = MaterialTheme.typography.bodyLarge)
                        AppText("إجمالي الشدات الخشبية: ${summary.totalFormworkAreaM2} م²", style = MaterialTheme.typography.bodyLarge)
                        AppText("إجمالي الحفر: ${summary.totalExcavationVolumeM3} م³", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(12.dp))
                        AppText("التكلفة الإجمالية التقديرية: ${summary.totalCost} ج.م", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        AppText("بنود جدول الكميات (BOQ Items):", style = MaterialTheme.typography.titleSmall)
                        summary.boqItems.forEach { item ->
                            AppText("• ${item.descriptionAr}: ${item.quantity} ${item.unitAr} (${item.totalPrice} ج.م)")
                        }
                    }
                }
            },
            confirmButton = {
                AppButton(
                    label = "إغلاق",
                    onClick = { showBoqDialog = false },
                    style = AppButtonStyle.Text,
                    fillMaxWidth = false
                )
            }
        )
    }
}
