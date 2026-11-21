package com.civileg.app.features.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.domain.calculations.base.WallOpeningDesign
import com.civileg.app.ui.compose.components.DesignCodeSelectorRow
import com.civileg.app.ui.compose.components.DesignInputField
import com.civileg.app.ui.compose.components.DesignResultRow
import com.civileg.app.ui.compose.components.DesignSectionHeader
import com.civileg.app.ui.compose.components.DesignStatusBanner
import com.civileg.app.ui.compose.components.drawings.ProfessionalWallOpeningDrawing
import com.civileg.app.ui.defaultDesignCode
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor
import com.civileg.app.features.design.WallOpeningViewModel

/**
 * Wall opening (lintel / sill / jamb) design screen.
 *
 * Layout note: every section — inputs, action row, drawing, results and export —
 * lives inside a single [LazyColumn]. A previous version nested a weighted
 * LazyColumn inside a non-scrollable Column, which collapsed the inputs to zero
 * height once results appeared and pushed the results off-screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallOpeningScreen(
    onNavigateBack: () -> Unit,
    viewModel: WallOpeningViewModel = hiltViewModel()
) {
    val uiState by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()
    val context = LocalContext.current

    var wallHeightMm by rememberSaveable { mutableStateOf("3000") }
    var wallThicknessMm by rememberSaveable { mutableStateOf("250") }
    var openingWidthMm by rememberSaveable { mutableStateOf("1000") }
    var openingHeightMm by rememberSaveable { mutableStateOf("2000") }
    var openingFromBaseMm by rememberSaveable { mutableStateOf("800") }
    var wallLengthMm by rememberSaveable { mutableStateOf("5000") }
    var fcu by rememberSaveable { mutableStateOf("25") }
    var fy by rememberSaveable { mutableStateOf("360") }
    var loadAboveOpeningKNm by rememberSaveable { mutableStateOf("20") }
    var selectedCode by rememberSaveable { mutableStateOf(defaultDesignCode()) }

    val validation = validateWallOpeningInputs(
        wallHeightMm = wallHeightMm,
        wallThicknessMm = wallThicknessMm,
        openingWidthMm = openingWidthMm,
        openingHeightMm = openingHeightMm,
        openingFromBaseMm = openingFromBaseMm,
        wallLengthMm = wallLengthMm,
        fcu = fcu,
        fy = fy,
        loadAboveOpeningKNm = loadAboveOpeningKNm,
        numberMessage = stringResource(R.string.wo_error_number),
        positiveMessage = stringResource(R.string.wo_error_positive),
        openingWidthMessage = stringResource(R.string.wo_error_opening_width),
        openingHeightMessage = stringResource(R.string.wo_error_opening_height)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AppText(stringResource(R.string.wo_title), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.reset() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reset))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { DesignSectionHeader(title = stringResource(R.string.wo_section_inputs)) }

            item {
                DesignCodeSelectorRow(
                    selectedCode = selectedCode,
                    onCodeSelected = { selectedCode = it }
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DesignInputField(
                        value = wallHeightMm,
                        label = stringResource(R.string.wo_wall_height),
                        onValueChange = { wallHeightMm = it },
                        modifier = Modifier.weight(1f),
                        isError = validation.errors.containsKey(Field.WALL_HEIGHT),
                        supportingText = validation.errors[Field.WALL_HEIGHT]
                    )
                    DesignInputField(
                        value = wallThicknessMm,
                        label = stringResource(R.string.wo_wall_thickness),
                        onValueChange = { wallThicknessMm = it },
                        modifier = Modifier.weight(1f),
                        isError = validation.errors.containsKey(Field.WALL_THICKNESS),
                        supportingText = validation.errors[Field.WALL_THICKNESS]
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DesignInputField(
                        value = wallLengthMm,
                        label = stringResource(R.string.wo_wall_length),
                        onValueChange = { wallLengthMm = it },
                        modifier = Modifier.weight(1f),
                        isError = validation.errors.containsKey(Field.WALL_LENGTH),
                        supportingText = validation.errors[Field.WALL_LENGTH]
                    )
                    DesignInputField(
                        value = openingFromBaseMm,
                        label = stringResource(R.string.wo_opening_from_base),
                        onValueChange = { openingFromBaseMm = it },
                        modifier = Modifier.weight(1f),
                        isError = validation.errors.containsKey(Field.OPENING_FROM_BASE),
                        supportingText = validation.errors[Field.OPENING_FROM_BASE]
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DesignInputField(
                        value = openingWidthMm,
                        label = stringResource(R.string.wo_opening_width),
                        onValueChange = { openingWidthMm = it },
                        modifier = Modifier.weight(1f),
                        isError = validation.errors.containsKey(Field.OPENING_WIDTH),
                        supportingText = validation.errors[Field.OPENING_WIDTH]
                    )
                    DesignInputField(
                        value = openingHeightMm,
                        label = stringResource(R.string.wo_opening_height),
                        onValueChange = { openingHeightMm = it },
                        modifier = Modifier.weight(1f),
                        isError = validation.errors.containsKey(Field.OPENING_HEIGHT),
                        supportingText = validation.errors[Field.OPENING_HEIGHT]
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DesignInputField(
                        value = fcu,
                        label = stringResource(R.string.sw_fcu),
                        onValueChange = { fcu = it },
                        modifier = Modifier.weight(1f),
                        isError = validation.errors.containsKey(Field.FCU),
                        supportingText = validation.errors[Field.FCU]
                    )
                    DesignInputField(
                        value = fy,
                        label = stringResource(R.string.sw_fy),
                        onValueChange = { fy = it },
                        modifier = Modifier.weight(1f),
                        isError = validation.errors.containsKey(Field.FY),
                        supportingText = validation.errors[Field.FY]
                    )
                }
            }

            item {
                DesignInputField(
                    value = loadAboveOpeningKNm,
                    label = stringResource(R.string.wo_load_above),
                    onValueChange = { loadAboveOpeningKNm = it },
                    modifier = Modifier.fillMaxWidth(),
                    isError = validation.errors.containsKey(Field.LOAD_ABOVE),
                    supportingText = validation.errors[Field.LOAD_ABOVE]
                )
            }

            if (validation.errors.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppText(
                            text = stringResource(R.string.wo_error_fix_inputs),
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppButton(
                        label = stringResource(R.string.wo_calculate),
                        onClick = {
                            viewModel.calculate(
                                wallHeightMm = wallHeightMm.toDoubleOrNull() ?: return@AppButton,
                                wallThicknessMm = wallThicknessMm.toDoubleOrNull() ?: return@AppButton,
                                openingWidthMm = openingWidthMm.toDoubleOrNull() ?: return@AppButton,
                                openingHeightMm = openingHeightMm.toDoubleOrNull() ?: return@AppButton,
                                openingFromBaseMm = openingFromBaseMm.toDoubleOrNull() ?: return@AppButton,
                                wallLengthMm = wallLengthMm.toDoubleOrNull() ?: return@AppButton,
                                fcu = fcu.toDoubleOrNull() ?: return@AppButton,
                                fy = fy.toDoubleOrNull() ?: return@AppButton,
                                loadAboveOpeningKNm = loadAboveOpeningKNm.toDoubleOrNull() ?: return@AppButton,
                                code = selectedCode
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled = validation.isValid && !isLoading,
                        loading = isLoading
                    )
                    AppButton(
                        label = stringResource(R.string.reset),
                        onClick = { viewModel.reset() },
                        modifier = Modifier.weight(1f),
                        style = AppButtonStyle.Outline
                    )
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            error?.let { err ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        AppText(
                            text = err,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            uiState?.let { res ->
                item { DesignStatusBanner(ratio = res.steelUtilization().toFloat(), isSafe = res.isSafe) }

                res.warnings.forEach { warningText ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            AppText(
                                text = warningText,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                item { DesignSectionHeader(title = stringResource(R.string.wo_section_drawing)) }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        ProfessionalWallOpeningDrawing(
                            wallHeightMm = wallHeightMm.toDoubleOrNull() ?: 3000.0,
                            wallThicknessMm = wallThicknessMm.toDoubleOrNull() ?: 250.0,
                            openingWidthMm = openingWidthMm.toDoubleOrNull() ?: 1000.0,
                            openingHeightMm = openingHeightMm.toDoubleOrNull() ?: 2000.0,
                            openingFromBaseMm = openingFromBaseMm.toDoubleOrNull() ?: 800.0,
                            wallLengthMm = wallLengthMm.toDoubleOrNull() ?: 5000.0,
                            headerBarDia = res.headerBeamReinforcement.barDiameterMm,
                            headerBarCount = res.headerBeamReinforcement.numberOfBars,
                            sillBarDia = res.sillReinforcement.barDiameterMm,
                            sillBarCount = res.sillReinforcement.numberOfBars,
                            verticalBarDia = res.verticalReinforcementEachSide.barDiameterMm,
                            verticalBarCount = res.verticalReinforcementEachSide.numberOfBars,
                            cover = 25.0,
                            viewMode = 0,
                            designCode = selectedCode,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                item {
                    AppButton(
                        label = stringResource(R.string.wo_export_pdf),
                        onClick = {
                            viewModel.exportToPdf(
                                context = context,
                                wallHeightMm = wallHeightMm.toDoubleOrNull() ?: 3000.0,
                                wallThicknessMm = wallThicknessMm.toDoubleOrNull() ?: 250.0,
                                openingWidthMm = openingWidthMm.toDoubleOrNull() ?: 1000.0,
                                openingHeightMm = openingHeightMm.toDoubleOrNull() ?: 2000.0,
                                openingFromBaseMm = openingFromBaseMm.toDoubleOrNull() ?: 800.0,
                                wallLengthMm = wallLengthMm.toDoubleOrNull() ?: 5000.0,
                                fcu = fcu.toDoubleOrNull() ?: 25.0,
                                fy = fy.toDoubleOrNull() ?: 360.0,
                                loadAboveOpeningKNm = loadAboveOpeningKNm.toDoubleOrNull() ?: 0.0,
                                code = selectedCode
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        fontWeight = FontWeight.Bold,
                        icon = Icons.Default.PictureAsPdf,
                        containerOverride = MaterialTheme.colorScheme.tertiary,
                        contentOverride = onColorFor(MaterialTheme.colorScheme.tertiary)
                    )
                }

                item { DesignSectionHeader(title = stringResource(R.string.wo_section_results)) }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            DesignResultRow(
                                label = stringResource(R.string.wo_max_stress),
                                value = "${"%.2f".format(res.maxStressMpa)} MPa"
                            )
                            DesignResultRow(
                                label = stringResource(R.string.wo_opening_rebar),
                                value = "${"%.0f".format(res.openingReinforcement.areaProvidedMm2)}"
                            )
                            DesignResultRow(
                                label = stringResource(R.string.wo_header_rebar),
                                value = "${"%.0f".format(res.headerBeamReinforcement.areaProvidedMm2)}"
                            )
                            DesignResultRow(
                                label = stringResource(R.string.wo_sill_rebar),
                                value = "${"%.0f".format(res.sillReinforcement.areaProvidedMm2)}"
                            )
                            DesignResultRow(
                                label = stringResource(R.string.wo_vertical_rebar),
                                value = "${"%.0f".format(res.verticalReinforcementEachSide.areaProvidedMm2)}"
                            )
                            DesignResultRow(
                                label = stringResource(R.string.sw_design_code),
                                value = selectedCode.displayName
                            )
                        }
                    }
                }

                if (res.codeNotes.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                res.codeNotes.forEach { noteText ->
                                    AppText(
                                        text = noteText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Input validation
// ═══════════════════════════════════════════════════════════════════════

internal enum class Field {
    WALL_HEIGHT, WALL_THICKNESS, WALL_LENGTH,
    OPENING_WIDTH, OPENING_HEIGHT, OPENING_FROM_BASE,
    FCU, FY, LOAD_ABOVE
}

internal data class WallOpeningValidation(
    val errors: Map<Field, String>,
    val isValid: Boolean
)

internal fun validateWallOpeningInputs(
    wallHeightMm: String,
    wallThicknessMm: String,
    openingWidthMm: String,
    openingHeightMm: String,
    openingFromBaseMm: String,
    wallLengthMm: String,
    fcu: String,
    fy: String,
    loadAboveOpeningKNm: String,
    numberMessage: String,
    positiveMessage: String,
    openingWidthMessage: String,
    openingHeightMessage: String
): WallOpeningValidation {
    val errors = LinkedHashMap<Field, String>()

    fun requirePositive(field: Field, raw: String): Double? {
        val value = raw.trim().toDoubleOrNull()
        if (value == null) {
            errors[field] = numberMessage
            return null
        }
        if (value <= 0.0) {
            errors[field] = positiveMessage
            return null
        }
        return value
    }

    val wallHeight = requirePositive(Field.WALL_HEIGHT, wallHeightMm)
    requirePositive(Field.WALL_THICKNESS, wallThicknessMm)
    val wallLength = requirePositive(Field.WALL_LENGTH, wallLengthMm)
    val openingWidth = requirePositive(Field.OPENING_WIDTH, openingWidthMm)
    val openingHeight = requirePositive(Field.OPENING_HEIGHT, openingHeightMm)
    requirePositive(Field.FCU, fcu)
    requirePositive(Field.FY, fy)

    // Offset from base may legitimately be zero (opening sitting on the wall base).
    val openingFromBase = openingFromBaseMm.trim().toDoubleOrNull()
    if (openingFromBase == null) {
        errors[Field.OPENING_FROM_BASE] = numberMessage
    } else if (openingFromBase < 0.0) {
        errors[Field.OPENING_FROM_BASE] = positiveMessage
    }

    val loadAbove = loadAboveOpeningKNm.trim().toDoubleOrNull()
    if (loadAbove == null) {
        errors[Field.LOAD_ABOVE] = numberMessage
    } else if (loadAbove < 0.0) {
        errors[Field.LOAD_ABOVE] = positiveMessage
    }

    if (openingWidth != null && wallLength != null && openingWidth > wallLength) {
        errors[Field.OPENING_WIDTH] = openingWidthMessage
    }
    if (openingHeight != null && wallHeight != null && openingFromBase != null &&
        openingHeight + openingFromBase > wallHeight
    ) {
        errors[Field.OPENING_HEIGHT] = openingHeightMessage
    }

    return WallOpeningValidation(errors = errors, isValid = errors.isEmpty())
}

/**
 * Steel utilization of the governing reinforcement set (required / provided).
 * The design result exposes no single utilization ratio, so the worst of the
 * four reinforcement groups is used for the status banner.
 */
private fun WallOpeningDesign.Result.steelUtilization(): Double {
    val groups = listOf(
        openingReinforcement,
        headerBeamReinforcement,
        sillReinforcement,
        verticalReinforcementEachSide
    )
    return groups.maxOfOrNull { group ->
        if (group.areaProvidedMm2 > 0.0) group.areaRequiredMm2 / group.areaProvidedMm2 else 0.0
    } ?: 0.0
}
