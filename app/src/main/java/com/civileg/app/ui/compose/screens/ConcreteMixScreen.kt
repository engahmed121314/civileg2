package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.utils.ConcreteMixDesigner
import com.civileg.app.utils.ConcreteMixDesigner.CementType
import com.civileg.app.utils.ConcreteMixDesigner.Exposure
import com.civileg.app.utils.ConcreteMixDesigner.STANDARD_GRADES
import com.civileg.app.utils.ConcreteMixDesigner.MixResult
import com.civileg.app.viewmodel.ConcreteMixViewModel
import kotlin.math.*
import java.util.Locale

// ============================================================
// Color palette
// ============================================================

private val CementGray    = Color(0xFF78909C)
private val WaterBlue     = Color(0xFF42A5F5)
private val FineSandBeige = Color(0xFFFFCC80)
private val CoarseBrown   = Color(0xFFA1887F)
private val AdmixtureGreen = Color(0xFF66BB6A)
private val AccentTeal    = Color(0xFF26A69A)
private val HighlightAmber = Color(0xFFFFB300)

// ============================================================
// Main screen composable
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcreteMixScreen(
    viewModel: ConcreteMixViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val mixResult by viewModel.mixResult.collectAsState()
    val calculationError by viewModel.errorMessage.observeAsState()

    // ── Local UI state ──
    var targetStrength by remember { mutableStateOf("30") }
    var standardDeviation by remember { mutableStateOf("5.0") }
    val selectedGrade = targetStrength.toDoubleOrNull()
    var selectedExposure by remember { mutableStateOf(Exposure.MODERATE) }
    var maxAggSize by remember { mutableStateOf(20.0) }
    var finenessModulus by remember { mutableStateOf("2.70") }
    var fineAggSG by remember { mutableStateOf("2.65") }
    var coarseAggSG by remember { mutableStateOf("2.70") }
    var selectedSlump by remember { mutableStateOf("75-100") }
    var selectedCementType by remember { mutableStateOf(CementType.OPC) }
    var hasAdmixture by remember { mutableStateOf(false) }
    var selectedAdmixture by remember { mutableStateOf("Water Reducer") }
    var admixtureDosage by remember { mutableStateOf("1.0") }
    val targetStrengthValue = targetStrength.toDoubleOrNull()
    val standardDeviationValue = standardDeviation.toDoubleOrNull()
    val finenessModulusValue = finenessModulus.toDoubleOrNull()
    val fineAggSGValue = fineAggSG.toDoubleOrNull()
    val coarseAggSGValue = coarseAggSG.toDoubleOrNull()
    val admixtureDosageValue = admixtureDosage.toDoubleOrNull()
    val inputIsValid =
        targetStrengthValue?.let { it.isFinite() && it > 0.0 } == true &&
        standardDeviationValue?.let { it.isFinite() && it > 0.0 } == true &&
        finenessModulusValue?.let { it.isFinite() && it in 2.4..3.2 } == true &&
        fineAggSGValue?.let { it.isFinite() && it > 0.0 } == true &&
        coarseAggSGValue?.let { it.isFinite() && it > 0.0 } == true &&
        (!hasAdmixture || admixtureDosageValue?.let { it.isFinite() && it > 0.0 } == true)

    // Derived slump mid-point value for calculation
    val slumpValue = when (selectedSlump) {
        "25-50"   -> 37.5
        "50-75"   -> 62.5
        "75-100"  -> 87.5
        "100-150" -> 125.0
        "150-200" -> 175.0
        else      -> 87.5
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.mix_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 1. Target Strength & Grade Selection ──
            SectionCard(title = stringResource(R.string.mix_target_strength)) {
                InputRow(
                    label = stringResource(R.string.mix_fc_target),
                    value = targetStrength,
                    onValueChange = { targetStrength = it }
                )
                InputRow(
                    label = stringResource(R.string.mix_std_dev),
                    value = standardDeviation,
                    onValueChange = { standardDeviation = it }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.mix_quick_grade),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    STANDARD_GRADES.forEach { grade ->
                        FilterChip(
                            selected = grade.toDouble() == selectedGrade,
                            onClick = {
                                targetStrength = grade.toString()
                                standardDeviation = "%.1f".format(
                                    Locale.US,
                                    ConcreteMixDesigner.estimateStandardDeviation(grade.toDouble())
                                )
                            },
                            label = { Text("C$grade") },
                            leadingIcon = if (grade.toDouble() == selectedGrade) {
                                { Text("●", color = MaterialTheme.colorScheme.primary) }
                            } else null
                        )
                    }
                }
            }

            // ── 2. Exposure Condition ──
            SectionCard(title = stringResource(R.string.mix_exposure)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    Exposure.entries.forEach { exposure ->
                        val labelRes = when (exposure) {
                            Exposure.MILD -> R.string.exposure_mild
                            Exposure.MODERATE -> R.string.exposure_moderate
                            Exposure.SEVERE -> R.string.exposure_severe
                            Exposure.VERY_SEVERE -> R.string.exposure_very_severe
                            Exposure.EXTREME -> R.string.exposure_extreme
                        }
                        FilterChip(
                            selected = exposure == selectedExposure,
                            onClick = { selectedExposure = exposure },
                            label = {
                                Text(
                                    stringResource(labelRes),
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = if (exposure == selectedExposure) {
                                { Text("●", color = MaterialTheme.colorScheme.primary) }
                            } else null
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Min cement: ${selectedExposure.minCement.toInt()} kg/m³  •  Max w/c: ${selectedExposure.maxWCRatio}  •  Min cover: ${selectedExposure.minCover.toInt()} mm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── 3. Aggregate Properties ──
            SectionCard(title = stringResource(R.string.mix_agg_props)) {
                // Max aggregate size dropdown
                var aggExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = aggExpanded,
                    onExpandedChange = { aggExpanded = !aggExpanded }
                ) {
                    OutlinedTextField(
                        value = "${maxAggSize.toInt()} mm",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.mix_max_agg)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = aggExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = aggExpanded,
                        onDismissRequest = { aggExpanded = false }
                    ) {
                        listOf(10.0, 20.0, 25.0, 40.0).forEach { size ->
                            DropdownMenuItem(
                                text = { Text("${size.toInt()} mm") },
                                onClick = {
                                    maxAggSize = size
                                    aggExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                InputRow(
                    label = stringResource(R.string.mix_fm),
                    value = finenessModulus,
                    onValueChange = { finenessModulus = it }
                )
                InputRow(
                    label = stringResource(R.string.mix_fine_sg),
                    value = fineAggSG,
                    onValueChange = { fineAggSG = it }
                )
                InputRow(
                    label = stringResource(R.string.mix_coarse_sg),
                    value = coarseAggSG,
                    onValueChange = { coarseAggSG = it }
                )
            }

            // ── 4. Slump Selector ──
            SectionCard(title = stringResource(R.string.mix_slump)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf("25-50", "50-75", "75-100", "100-150", "150-200").forEach { range ->
                        FilterChip(
                            selected = range == selectedSlump,
                            onClick = { selectedSlump = range },
                            label = { Text("$range mm") },
                            leadingIcon = if (range == selectedSlump) {
                                { Text("●", color = MaterialTheme.colorScheme.primary) }
                            } else null
                        )
                    }
                }
            }

            // ── 5. Cement Type Selector ──
            SectionCard(title = stringResource(R.string.mix_cement_type_label)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CementType.entries.forEach { ct ->
                        FilterChip(
                            selected = ct == selectedCementType,
                            onClick = { selectedCementType = ct },
                            label = { Text(ct.name) },
                            leadingIcon = if (ct == selectedCementType) {
                                { Text("●", color = MaterialTheme.colorScheme.primary) }
                            } else null
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                val cementLabel = when (selectedCementType) {
                    CementType.OPC -> stringResource(R.string.cement_opc)
                    CementType.SRC -> stringResource(R.string.cement_src)
                    CementType.PPC -> stringResource(R.string.cement_ppc)
                }
                Text(
                    text = "$cementLabel  (SG = ${selectedCementType.specificGravity})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── 6. Admixtures Toggle ──
            SectionCard(title = stringResource(R.string.mix_admixtures)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.mix_use_admix), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = hasAdmixture,
                        onCheckedChange = { hasAdmixture = it }
                    )
                }
                if (hasAdmixture) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf("Water Reducer", "Plasticizer", "Superplasticizer").forEach { adm ->
                            FilterChip(
                                selected = adm == selectedAdmixture,
                                onClick = { selectedAdmixture = adm },
                                label = { Text(adm, fontSize = 12.sp) },
                                leadingIcon = if (adm == selectedAdmixture) {
                                    { Text("●", color = MaterialTheme.colorScheme.primary) }
                                } else null
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    InputRow(
                        label = stringResource(R.string.mix_dosage),
                        value = admixtureDosage,
                        onValueChange = { admixtureDosage = it }
                    )
                }
            }

            // ── 7. Calculate Button ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        val target = targetStrengthValue ?: return@Button
                        val deviation = standardDeviationValue ?: return@Button
                        val fm = finenessModulusValue ?: return@Button
                        val fineSG = fineAggSGValue ?: return@Button
                        val coarseSG = coarseAggSGValue ?: return@Button
                        val dosage = if (hasAdmixture) admixtureDosageValue ?: return@Button else 0.0
                        viewModel.designMix(
                            targetStrength = target,
                            standardDeviation = deviation,
                            maxAggSize = maxAggSize,
                            slump = slumpValue,
                            exposure = selectedExposure,
                            cementType = selectedCementType,
                            fm = fm,
                            hasAdmixture = hasAdmixture,
                            admixtureType = if (hasAdmixture) selectedAdmixture else "None",
                            admixtureDosage = dosage,
                            isPumpable = false,
                            weatherCondition = "Normal",
                            useNoTestData = false,
                            fineAggSG = fineSG,
                            coarseAggSG = coarseSG
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = inputIsValid
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.mix_calc_button))
                }
                IconButton(
                    onClick = {
                        targetStrength = "30"
                        standardDeviation = "5.0"
                        selectedExposure = Exposure.MODERATE
                        maxAggSize = 20.0
                        finenessModulus = "2.70"
                        fineAggSG = "2.65"
                        coarseAggSG = "2.70"
                        selectedSlump = "75-100"
                        selectedCementType = CementType.OPC
                        hasAdmixture = false
                        selectedAdmixture = "Water Reducer"
                        admixtureDosage = "1.0"
                        viewModel.clearResults()
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reset))
                }
            }

            if (!inputIsValid) {
                Text(
                    stringResource(R.string.mix_input_validation),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            calculationError?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // ── 8. Results Section ──
            mixResult?.let { result ->
                val context = androidx.compose.ui.platform.LocalContext.current

                Button(
                    onClick = { viewModel.exportToPdf(context, slump = slumpValue) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("تصدير تقرير PDF برو (3 قطاعات)", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(4.dp))
                MixResultCard(result = result)
                Text(
                    stringResource(R.string.mix_preliminary_notice),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )

                // Mix Proportion Bar Chart
                Spacer(modifier = Modifier.height(4.dp))
                SectionCard(title = stringResource(R.string.mix_proportion_chart)) {
                    MixProportionBarChart(result = result)
                }

                // Notes
                if (result.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    SectionCard(title = stringResource(R.string.mix_design_notes)) {
                        result.notes.forEach { note ->
                            Text(
                                text = "• $note",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ============================================================
// Reusable Section Card
// ============================================================

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

// ============================================================
// Input Row
// ============================================================

@Composable
private fun InputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal
        ),
        singleLine = true,
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp)
    )
}

// ============================================================
// Result Row
// ============================================================

@Composable
private fun ResultRow(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = if (highlight) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            color = if (highlight) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ============================================================
// Mix Result Card
// ============================================================

@Composable
private fun MixResultCard(result: MixResult) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.mix_results_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "%.1f MPa".format(Locale.US, result.targetStrength),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            HorizontalDivider()

            // Key results
            ResultRow(
                stringResource(R.string.mix_req_strength),
                "%.1f MPa".format(Locale.US, result.requiredStrength)
            )
            ResultRow(
                stringResource(R.string.mix_wc_ratio),
                "%.3f".format(Locale.US, result.waterCementRatio),
                highlight = true
            )
            if (result.admixtureContent > 0) {
                ResultRow(
                    stringResource(R.string.mix_effective_wc),
                    "%.3f".format(Locale.US, result.wCmRatio)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                stringResource(R.string.mix_material_quantities),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            ResultRow(
                stringResource(R.string.mix_cement_content),
                "%.0f kg/m³".format(Locale.US, result.cementContent),
                highlight = true
            )
            ResultRow(
                stringResource(R.string.mix_water_content),
                "%.0f liters/m³".format(Locale.US, result.waterContent)
            )
            ResultRow(
                stringResource(R.string.mix_fine_agg),
                "%.0f kg/m³".format(Locale.US, result.fineAggContent)
            )
            ResultRow(
                stringResource(R.string.mix_coarse_agg),
                "%.0f kg/m³".format(Locale.US, result.coarseAggContent)
            )
            if (result.admixtureContent > 0) {
                ResultRow(
                    stringResource(R.string.mix_admix_content),
                    "%.1f kg/m³".format(Locale.US, result.admixtureContent)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            ResultRow(
                stringResource(R.string.mix_air_content),
                "%.1f %%".format(Locale.US, result.airContent)
            )
            ResultRow(
                stringResource(R.string.mix_unit_weight),
                "%.0f kg/m³".format(Locale.US, result.unitWeight)
            )
            ResultRow(
                stringResource(R.string.mix_yield_label),
                "%.2f m³".format(Locale.US, result.yield)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Mix Ratio prominently displayed
            Text(
                stringResource(R.string.mix_ratio_prominent),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    buildMixRatioString(result),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            val resCementLabel = when (result.cementType) {
                "Ordinary Portland Cement" -> stringResource(R.string.cement_opc)
                "Sulfate Resisting Cement" -> stringResource(R.string.cement_src)
                "Portland Pozzolana Cement" -> stringResource(R.string.cement_ppc)
                else -> result.cementType
            }
            val resExposureLabel = when (result.exposure) {
                "Mild (dry interior)" -> stringResource(R.string.exposure_mild)
                "Moderate (sheltered)" -> stringResource(R.string.exposure_moderate)
                "Severe (exposed)" -> stringResource(R.string.exposure_severe)
                "Very Severe (aggressive)" -> stringResource(R.string.exposure_very_severe)
                "Extreme (chemical attack)" -> stringResource(R.string.exposure_extreme)
                else -> result.exposure
            }
            Text(
                "$resCementLabel  •  $resExposureLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Builds full mix ratio string: 1 : x.xx : x.xx : x.xx
 */
private fun buildMixRatioString(result: MixResult): String {
    val c = result.cementContent
    if (c <= 0) return "—"
    val fa = result.fineAggContent / c
    val ca = result.coarseAggContent / c
    val w = result.waterContent / c
    return "1 : ${"%.2f".format(Locale.US, fa)} : ${"%.2f".format(Locale.US, ca)} : ${"%.2f".format(Locale.US, w)}"
}

// ============================================================
// Mix Proportion Bar Chart (Canvas)
// ============================================================

@Composable
private fun MixProportionBarChart(result: MixResult) {
    val cement = result.cementContent
    val water = result.waterContent
    val fine = result.fineAggContent
    val coarse = result.coarseAggContent
    val total = cement + water + fine + coarse

    if (total <= 0) return

    val segments = listOf(
        stringResource(R.string.mix_cement_content) to ((cement / total) to CementGray),
        stringResource(R.string.mix_water_content)  to ((water / total)  to WaterBlue),
        stringResource(R.string.mix_fine_agg) to ((fine / total) to FineSandBeige),
        stringResource(R.string.mix_coarse_agg) to ((coarse / total) to CoarseBrown)
    ).filter { (_, pair) -> pair.first > 0.0 }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Stacked horizontal bar
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            val w = size.width
            val h = size.height
            val cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            var xOffset = 0f

            segments.forEachIndexed { index, (label, pair) ->
                val (fractionDouble, color) = pair
                val fraction = fractionDouble.toFloat()
                val barWidth = (w * fraction).coerceIn(0f, w - xOffset)
                val isLast = index == segments.lastIndex
                val segmentWidth = if (isLast) (w - xOffset).coerceAtLeast(0f) else barWidth

                if (segmentWidth > 0f) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(xOffset, 0f),
                        size = Size(segmentWidth, h),
                        cornerRadius = if (xOffset <= 0f || isLast) cornerRadius else CornerRadius.Zero
                    )
                }

                // Draw percentage label if segment is wide enough
                if (segmentWidth > 40.dp.toPx()) {
                    drawContext.canvas.nativeCanvas.drawText(
                        "${"%.0f".format(Locale.US, fraction * 100)}%",
                        xOffset + segmentWidth / 2f,
                        h / 2f + 10f,
                        android.graphics.Paint().apply {
                            this.setColor(android.graphics.Color.WHITE)
                            this.textSize = 28f
                            this.isFakeBoldText = true
                            this.textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }

                xOffset += segmentWidth
            }

            // Border
            drawRoundRect(
                color = Color(0xFF455A64),
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = cornerRadius,
                style = Stroke(width = 1.5f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Legend & individual bars
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            segments.forEach { (label, pair) ->
                val (fraction, color) = pair
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Color swatch
                    Canvas(
                        modifier = Modifier.size(16.dp)
                    ) {
                        drawRoundRect(
                            color = color,
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Label
                    Text(
                        label,
                        modifier = Modifier.width(90.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    // Bar
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp)
                    ) {
                        val barW = (size.width * fraction.toFloat()).toFloat()
                        drawRoundRect(
                            color = color.copy(alpha = 0.7f),
                            size = Size(barW, size.height),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                        // Border
                        drawRoundRect(
                            color = color,
                            size = Size(barW, size.height),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                            style = Stroke(width = 1f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Value
                    Text(
                        "${"%.0f".format(Locale.US, fraction * total)} kg",
                        modifier = Modifier.width(80.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Total: ${"%.0f".format(Locale.US, total)} kg/m³  (excl. air & admixture)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
