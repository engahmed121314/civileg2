@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.civileg.app.ui.compose.screens.normal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.R
import com.civileg.app.ui.compose.screens.AppScreen
import com.civileg.app.ui.compose.screens.SettingsScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.civileg.app.utils.NormalTrackCalculators as Calc
import com.civileg.app.billing.BillingManager
import com.civileg.app.billing.PremiumFeature
import com.civileg.app.billing.PremiumPaywallSheet
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * NORMAL user track (research-and-ux-protocol.md §2.2):
 * plain language only — no φ/ρ/clause numbers anywhere in these screens.
 */

/**
 * Standalone navigation graph for the normal track (navigation-architecture.md §3).
 * Fully separate from the engineer NavHost — zero route pollution, loose coupling.
 */
@Composable
fun NormalUserAppHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "normal_home",
        enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260)) +
                androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260)) { it / 10 } },
        exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180)) },
        popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) },
        popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180)) +
                androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(200)) { it / 10 } }
    ) {
        composable("normal_home") {
            NormalUserHomeScreen(
                onOpenTool = { route -> navController.navigate(route) },
                onOpenSettings = { navController.navigate(AppScreen.Settings.route) }
            )
        }
        composable("n_takeoff") { QuantityTakeoffScreen(onBack = { navController.popBackStack() }) }
        composable("n_finishing") { FinishingCalculatorsScreen(onBack = { navController.popBackStack() }) }
        composable("n_concrete") { SimpleConcreteEstimatorScreen(onBack = { navController.popBackStack() }) }
        composable(AppScreen.Settings.route) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HOME — big 2-column card grid
// ─────────────────────────────────────────────────────────────────────────────

private data class NormalTool(val route: String, val icon: ImageVector, val titleRes: Int, val descRes: Int)

@Composable
fun NormalUserHomeScreen(
    onOpenTool: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val tools = listOf(
        NormalTool("n_takeoff", Icons.Default.SquareFoot, R.string.normal_tool_quantity, R.string.normal_tool_quantity_desc),
        NormalTool("n_finishing", Icons.Default.HomeWork, R.string.normal_tool_finishing, R.string.normal_tool_finishing_desc),
        NormalTool("n_concrete", Icons.Default.Calculate, R.string.normal_tool_concrete, R.string.normal_tool_concrete_desc)
    )

    Scaffold(
        topBar = {
            SmallTopAppBarCompat(title = stringResource(R.string.app_name),
                action = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                })
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(padding).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(tools) { tool ->
                Card(
                    onClick = { onOpenTool(tool.route) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Icon(tool.icon, null, tint = MaterialTheme.colorScheme.primary,
                             modifier = Modifier.size(38.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(tool.titleRes),
                             style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(tool.descRes),
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared simple scaffolding + input field for the whole normal track
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SmallTopAppBarCompat(title: String, action: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        actions = { action() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun NormalToolScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) { content() }
    }
}

@Composable
private fun SimpleField(label: String, value: String, onChange: (String) -> Unit, unitHint: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        suffix = { Text(unitHint, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ResultCard(lines: List<Pair<String, String>>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lines.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NormalExportButton(
    enabled: Boolean,
    entries: List<Triple<String, String, Double>>,
    premiumVm: com.civileg.app.viewmodel.PremiumViewModel = hiltViewModel()
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val isPremium = premiumVm.featureFlags.isPremium()
    var showPaywallFor by remember { mutableStateOf<PremiumFeature?>(null) }
    showPaywallFor?.let { feature ->
        PremiumPaywallSheet(feature = feature, billingManager = premiumVm.billingManager, onDismiss = { showPaywallFor = null })
    }
    OutlinedButton(
        onClick = {
            if (!isPremium) { showPaywallFor = PremiumFeature.CSV_EXPORT; return@OutlinedButton }
            exportNormalBoqCsv(ctx, "NormalTools", "NORMAL_TRACK", entries)
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Export BOQ CSV", fontSize = 12.sp)
    }
}

/**
 * R5: minimal BOQ-CSV export for normal-track results.
 * @param entries triples of (description EN, unit, quantity)
 */
private fun exportNormalBoqCsv(
    context: android.content.Context,
    toolName: String,
    category: String,
    entries: List<Triple<String, String, Double>>
): Boolean {
    if (entries.isEmpty()) return false
    val sb = StringBuilder("Category,Description,Unit,Quantity\n")
    entries.forEach { (desc, unit, qty) ->
        sb.append("\"$category\",\"${desc.replace("\"", "\"\"")}\",$unit,$qty\n")
    }
    val file = com.civileg.app.utils.ExcelExporter.exportTextCsv(context, "${toolName}_BOQ", sb.toString())
        ?: return false
    com.civileg.app.utils.ExportUtils.openFile(context, file, "text/csv")
    return true
}

// ─────────────────────────────────────────────────────────────────────────────
// TOOL 1 — Quantity takeoff (slab / beams / columns / wall)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun QuantityTakeoffScreen(onBack: () -> Unit) {
    var mode by remember { mutableStateOf(0) } // 0 slab,1 beams,2 columns,3 wall
    val modes = listOf(
        stringResource(R.string.normal_elem_slab),
        stringResource(R.string.normal_elem_beams),
        stringResource(R.string.normal_elem_columns),
        stringResource(R.string.normal_elem_wall)
    )
    val unitM = stringResource(R.string.unit_m)
    val unitCm = stringResource(R.string.unit_cm)

    // slab / wall
    var len by remember { mutableStateOf("") }; var wid by remember { mutableStateOf("") }
    var thk by remember { mutableStateOf("") }
    // beams
    var count by remember { mutableStateOf("") }; var span by remember { mutableStateOf("") }
    var bw by remember { mutableStateOf("") }; var bd by remember { mutableStateOf("") }
    var hgt by remember { mutableStateOf("") }

    NormalToolScaffold(title = stringResource(R.string.normal_tool_quantity), onBack = onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { i, m ->
                FilterChip(
                    selected = mode == i,
                    onClick = { mode = i },
                    label = { Text(m) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when (mode) {
            0 -> {
                SimpleField(stringResource(R.string.normal_field_length), len, { len = it }, unitM)
                SimpleField(stringResource(R.string.normal_field_width), wid, { wid = it }, unitM)
                SimpleField(stringResource(R.string.normal_field_slab_thickness), thk, { thk = it }, unitCm)
                val l = len.toDoubleOrNull(); val w = wid.toDoubleOrNull(); val t = thk.toDoubleOrNull()
                if (l != null && w != null && t != null && l > 0 && w > 0 && t > 0) {
                    val r = Calc.slabQuantity(l, w, t)
                    ResultCard(listOf(
                        stringResource(R.string.normal_result_concrete) to "${r.concreteM3} ${stringResource(R.string.unit_m3)}",
                        stringResource(R.string.normal_result_formwork_ceiling) to "${r.formworkM2} ${stringResource(R.string.unit_m2)}"
                    ))
                    NormalExportButton(
                        enabled = true,
                        entries = listOf(
                            Triple("Ready-mix concrete C25", "m3", r.concreteM3),
                            Triple("Formwork - soffit", "m2", r.formworkM2)
                        )
                    )
                }
            }
            1 -> {
                SimpleField(stringResource(R.string.normal_field_beam_count), count, { count = it }, stringResource(R.string.unit_none))
                SimpleField(stringResource(R.string.normal_field_beam_length), span, { span = it }, unitM)
                SimpleField(stringResource(R.string.normal_field_beam_width), bw, { bw = it }, unitCm)
                SimpleField(stringResource(R.string.normal_field_beam_depth), bd, { bd = it }, unitCm)
                val c = count.toIntOrNull(); val s = span.toDoubleOrNull()
                val b1 = bw.toDoubleOrNull(); val b2 = bd.toDoubleOrNull()
                if (c != null && s != null && b1 != null && b2 != null && c > 0 && s > 0 && b1 > 0 && b2 > 0) {
                    val r = Calc.beamQuantity(c, s, b1, b2)
                    ResultCard(listOf(
                        stringResource(R.string.normal_result_concrete) to "${r.concreteM3} m3",
                        stringResource(R.string.normal_result_formwork) to "${r.formworkM2} ${stringResource(R.string.unit_m2)}"
                    ))
                    NormalExportButton(
                        enabled = true,
                        entries = listOf(
                            Triple("Ready-mix concrete C25", "m3", r.concreteM3),
                            Triple("Formwork - beams", "m2", r.formworkM2)
                        )
                    )
                }
            }
            2 -> {
                SimpleField(stringResource(R.string.normal_field_column_count), count, { count = it }, stringResource(R.string.unit_none))
                SimpleField(stringResource(R.string.normal_field_width), bw, { bw = it }, unitCm)
                SimpleField(stringResource(R.string.normal_field_depth), bd, { bd = it }, unitCm)
                SimpleField(stringResource(R.string.normal_field_height), hgt, { hgt = it }, unitM)
                val c = count.toIntOrNull(); val b1 = bw.toDoubleOrNull()
                val b2 = bd.toDoubleOrNull(); val h = hgt.toDoubleOrNull()
                if (c != null && b1 != null && b2 != null && h != null && c > 0 && b1 > 0 && b2 > 0 && h > 0) {
                    val r = Calc.columnQuantity(c, b1, b2, h)
                    ResultCard(listOf(
                        stringResource(R.string.normal_result_concrete) to "${r.concreteM3} m3",
                        stringResource(R.string.normal_result_formwork) to "${r.formworkM2} ${stringResource(R.string.unit_m2)}"
                    ))
                    NormalExportButton(
                        enabled = true,
                        entries = listOf(
                            Triple("Ready-mix concrete C25", "m3", r.concreteM3),
                            Triple("Formwork - columns", "m2", r.formworkM2)
                        )
                    )
                }
            }
            else -> {
                SimpleField(stringResource(R.string.normal_field_length), len, { len = it }, unitM)
                SimpleField(stringResource(R.string.normal_field_height), hgt, { hgt = it }, unitM)
                SimpleField(stringResource(R.string.normal_field_thickness), thk, { thk = it }, unitCm)
                val l = len.toDoubleOrNull(); val h = hgt.toDoubleOrNull(); val t = thk.toDoubleOrNull()
                if (l != null && h != null && t != null && l > 0 && h > 0 && t > 0) {
                    val r = Calc.wallQuantity(l, h, t)
                    ResultCard(listOf(
                        stringResource(R.string.normal_result_concrete) to "${r.concreteM3} m3",
                        stringResource(R.string.normal_result_formwork_faces) to "${r.formworkM2} ${stringResource(R.string.unit_m2)}"
                    ))
                    NormalExportButton(
                        enabled = true,
                        entries = listOf(
                            Triple("Ready-mix concrete C25", "m3", r.concreteM3),
                            Triple("Formwork - walls (2 faces)", "m2", r.formworkM2)
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOOL 2 — Finishing calculators (tiles / paint / plaster)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FinishingCalculatorsScreen(onBack: () -> Unit) {
    var tab by remember { mutableStateOf(0) } // 0 tiles,1 paint,2 plaster

    var area by remember { mutableStateOf("") }
    var tl by remember { mutableStateOf("60") }; var tw by remember { mutableStateOf("60") }
    var perBox by remember { mutableStateOf("6") }; var waste by remember { mutableStateOf("10") }
    var coats by remember { mutableStateOf("2") }; var coverage by remember { mutableStateOf("10") }

    NormalToolScaffold(title = stringResource(R.string.normal_tool_finishing), onBack = onBack) {
        val tabs = listOf(
            stringResource(R.string.normal_tab_tiles),
            stringResource(R.string.normal_tab_paint),
            stringResource(R.string.normal_tab_plaster)
        )
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        Spacer(Modifier.height(8.dp))
        SimpleField(stringResource(R.string.normal_field_total_area), area, { area = it }, stringResource(R.string.unit_m2))
        val a = area.toDoubleOrNull()

        when (tab) {
            0 -> {
                SimpleField(stringResource(R.string.normal_field_tile_length), tl, { tl = it }, stringResource(R.string.unit_cm))
                SimpleField(stringResource(R.string.normal_field_tile_width), tw, { tw = it }, stringResource(R.string.unit_cm))
                SimpleField(stringResource(R.string.normal_field_pieces_per_box), perBox, { perBox = it }, stringResource(R.string.unit_none))
                SimpleField(stringResource(R.string.normal_field_waste_pct), waste, { waste = it }, stringResource(R.string.unit_percent))
                val a2 = a; val L = tl.toDoubleOrNull(); val W = tw.toDoubleOrNull()
                val B = perBox.toIntOrNull(); val Ws = waste.toDoubleOrNull()
                if (a2 != null && L != null && W != null && B != null && Ws != null && a2 > 0 && L > 0 && W > 0 && B > 0) {
                    val r = Calc.tiles(a2, L, W, B, Ws)
                    ResultCard(listOf(
                        stringResource(R.string.normal_result_tiles_needed) to "${r.tilesCount}",
                        stringResource(R.string.normal_result_boxes_needed) to "${r.boxesCount}",
                        stringResource(R.string.normal_result_area_with_waste) to "${r.areaWithWasteM2} ${stringResource(R.string.unit_m2)}"
                    ))
                    NormalExportButton(
                        enabled = true,
                        entries = listOf(Triple("Floor tiles incl. waste", "m2", r.areaWithWasteM2))
                    )
                }
            }
            1 -> {
                SimpleField(stringResource(R.string.normal_field_coats), coats, { coats = it }, stringResource(R.string.unit_none))
                SimpleField(stringResource(R.string.normal_field_coverage_per_litre), coverage, { coverage = it }, stringResource(R.string.unit_m2))
                val C = coats.toIntOrNull(); val V = coverage.toDoubleOrNull()
                if (a != null && C != null && V != null && a > 0 && C > 0 && V > 0) {
                    val r = Calc.paint(a, C, V)
                    ResultCard(listOf(
                        stringResource(R.string.normal_result_total_paint_area) to "${r.coatsAreaM2} ${stringResource(R.string.unit_m2)}",
                        stringResource(R.string.normal_result_quantity_needed) to "${r.litersNeeded} ${stringResource(R.string.unit_litre)}",
                        stringResource(R.string.normal_result_gallons_18l) to "${r.gallons}"
                    ))
                    NormalExportButton(
                        enabled = true,
                        entries = listOf(Triple("Plastic paint (all coats)", "litre", r.litersNeeded))
                    )
                }
            }
            else -> {
                if (a != null && a > 0) {
                    val r = Calc.plaster(a)
                    ResultCard(listOf(
                        stringResource(R.string.normal_result_both_faces_area) to "${r.areaBothFacesM2} ${stringResource(R.string.unit_m2)}",
                        stringResource(R.string.normal_result_mortar_approx) to "${r.mortarM3} ${stringResource(R.string.unit_m3)}",
                        stringResource(R.string.normal_result_cement_bags_approx) to "${r.cementBags}"
                    ))
                    NormalExportButton(
                        enabled = true,
                        entries = listOf(
                            Triple("Internal plastering (both faces)", "m2", r.areaBothFacesM2),
                            Triple("Cement (plaster mortar)", "bag", r.cementBags.toDouble())
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOOL 3 — Quick concrete estimator
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SimpleConcreteEstimatorScreen(onBack: () -> Unit) {
    var kind by remember { mutableStateOf(0) } // 0 slab, 1 footing
    var len by remember { mutableStateOf("") }; var wid by remember { mutableStateOf("") }
    var thk by remember { mutableStateOf("") }

    NormalToolScaffold(title = stringResource(R.string.normal_tool_concrete), onBack = onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = kind == 0, onClick = { kind = 0 },
                label = { Text(stringResource(R.string.normal_elem_slab)) })
            FilterChip(selected = kind == 1, onClick = { kind = 1 },
                label = { Text(stringResource(R.string.normal_elem_footing)) })
        }
        SimpleField(stringResource(R.string.normal_field_length), len, { len = it }, stringResource(R.string.unit_m))
        SimpleField(stringResource(R.string.normal_field_width), wid, { wid = it }, stringResource(R.string.unit_m))
        SimpleField(stringResource(R.string.normal_field_thickness), thk, { thk = it }, stringResource(R.string.unit_cm))
        val l = len.toDoubleOrNull(); val w = wid.toDoubleOrNull(); val t = thk.toDoubleOrNull()
        if (l != null && w != null && t != null && l > 0 && w > 0 && t > 0) {
            val v = if (kind == 0) Calc.slabQuantity(l, w, t).concreteM3
                    else Calc.footingVolume(l, w, t)
            ResultCard(listOf(
                stringResource(R.string.normal_result_approx_quantity) to "$v ${stringResource(R.string.unit_m3)}"
            ))
            NormalExportButton(
                enabled = true,
                entries = listOf(
                    Triple(if (kind == 0) "Ready-mix concrete C25 (slab)" else "Ready-mix concrete C25 (footing)", "m3", v)
                )
            )
            Text(
                text = stringResource(R.string.normal_note_concrete_estimate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
