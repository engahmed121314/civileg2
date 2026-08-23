package com.civileg.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.domain.*
import com.civileg.app.ui.compose.components.DesignInputField
import com.civileg.app.ui.compose.components.DesignResultRow
import com.civileg.app.ui.compose.components.DesignSectionHeader
import com.civileg.app.ui.compose.components.DesignStatusBanner
import com.civileg.app.ui.compose.components.UtilizationGauge
import com.civileg.app.ui.components.drawings.ProfessionalShearWallDrawing
import com.civileg.app.viewmodel.ShearWallViewModel
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShearWallScreen(
    onNavigateBack: () -> Unit,
    viewModel: ShearWallViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sw_title), fontWeight = FontWeight.Bold) },
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
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Design Code & Wall Type ──────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.sw_design_code), style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf("ECP", "ACI").forEach { code ->
                        FilterChip(
                            selected = uiState.designCode == code,
                            onClick = { viewModel.updateDesignCode(code) },
                            label = { Text(code, fontSize = 13.sp,
                                fontWeight = if (uiState.designCode == code) FontWeight.Bold else FontWeight.Normal) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.sw_wall_type), style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            WallType.entries.forEach { type ->
                        FilterChip(
                            selected = uiState.wallType == type,
                            onClick = { viewModel.updateWallType(type) },
                            label = {
                                Text(type.name, fontSize = 12.sp,
                                    fontWeight = if (uiState.wallType == type) FontWeight.Bold else FontWeight.Normal)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.sw_wall_shape), style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf("Rectangular", "L-shaped", "T-shaped").forEach { shape ->
                        FilterChip(
                            selected = uiState.wallShape == shape,
                            onClick = { viewModel.updateWallShape(shape) },
                            label = { Text(shape, fontSize = 12.sp,
                                fontWeight = if (uiState.wallShape == shape) FontWeight.Bold else FontWeight.Normal) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                        }
                    }
                }
            }

            // ── Geometry ────────────────────────────────────────────
            item { DesignSectionHeader(stringResource(R.string.sw_geometry_section), Icons.Default.SquareFoot) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DesignInputField(uiState.wallLength, stringResource(R.string.sw_length),
                        { viewModel.updateGeometry(wallLength = it) }, Modifier.weight(1f))
                    DesignInputField(uiState.wallThickness, stringResource(R.string.sw_thickness),
                        { viewModel.updateGeometry(wallThickness = it) }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DesignInputField(uiState.storyHeight, stringResource(R.string.sw_story_height),
                        { viewModel.updateGeometry(storyHeight = it) }, Modifier.weight(1f))
                    DesignInputField(uiState.numberOfStories, stringResource(R.string.sw_num_stories),
                        { viewModel.updateGeometry(numberOfStories = it) }, Modifier.weight(1f))
                }
            }

            // ── Flange (for L/T walls) ───────────────────────────────
            if (uiState.wallShape != "Rectangular") {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.sw_flange_section), style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DesignInputField(uiState.flangeWidth, stringResource(R.string.sw_flange_width),
                                    { viewModel.updateFlange(flangeWidth = it) }, Modifier.weight(1f))
                                DesignInputField(uiState.flangeThickness, stringResource(R.string.sw_flange_thk),
                                    { viewModel.updateFlange(flangeThickness = it) }, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // ── Loads ───────────────────────────────────────────────
            item { DesignSectionHeader(stringResource(R.string.sw_loads_section), Icons.Default.ArrowDownward) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DesignInputField(uiState.axialLoad, stringResource(R.string.sw_axial_load),
                        { viewModel.updateLoads(axialLoad = it) }, Modifier.weight(1f))
                    DesignInputField(uiState.shearForce, stringResource(R.string.sw_shear_force),
                        { viewModel.updateLoads(shearForce = it) }, Modifier.weight(1f))
                }
            }

            item {
                DesignInputField(uiState.bendingMoment, stringResource(R.string.sw_bending_moment),
                    { viewModel.updateLoads(bendingMoment = it) }, Modifier.fillMaxWidth())
            }

            // ── Materials ───────────────────────────────────────────
            item { DesignSectionHeader(stringResource(R.string.sw_materials_section), Icons.Default.Science) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DesignInputField(uiState.fcu, stringResource(R.string.sw_fcu),
                        { viewModel.updateMaterials(fcu = it) }, Modifier.weight(1f))
                    DesignInputField(uiState.fy, stringResource(R.string.sw_fy),
                        { viewModel.updateMaterials(fy = it) }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DesignInputField(uiState.fyv, stringResource(R.string.sw_fyv),
                        { viewModel.updateMaterials(fyv = it) }, Modifier.weight(1f))
                    DesignInputField(uiState.clearCover, stringResource(R.string.sw_cover),
                        { viewModel.updateMaterials(clearCover = it) }, Modifier.weight(1f))
                }
            }

            // ── Coupling Beam (for Coupled walls) ───────────────────
            if (uiState.wallType == WallType.COUPLED) {
                item { DesignSectionHeader(stringResource(R.string.sw_coupling_section), Icons.Default.CropSquare) }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DesignInputField(uiState.couplingBeamLength, stringResource(R.string.length_label),
                            { viewModel.updateCoupling(couplingBeamLength = it) }, Modifier.weight(1f))
                        DesignInputField(uiState.couplingBeamHeight, stringResource(R.string.height_label),
                            { viewModel.updateCoupling(couplingBeamHeight = it) }, Modifier.weight(1f))
                    }
                }
                item {
                    DesignInputField(uiState.couplingBeamClearSpan, stringResource(R.string.sw_clear_span),
                        { viewModel.updateCoupling(couplingBeamClearSpan = it) }, Modifier.fillMaxWidth())
                }
            }

            // ── Calculate Button ────────────────────────────────────
            item {
                Button(
                    onClick = { viewModel.calculate() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sw_design_button), fontWeight = FontWeight.Bold)
                }
            }

            // ── Loading indicator ───────────────────────────────────
            if (uiState.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            // ── Results ─────────────────────────────────────────────
            uiState.result?.let { result ->
                // Status banner
                item {
                    DesignStatusBanner(
                        ratio = result.utilizationRatio.toFloat(),
                        isSafe = result.isSafe
                    )
                }

                // Safety checks warnings
                result.warnings.forEach { warning ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(warning, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                // Flexural Design Results
                item { DesignSectionHeader(stringResource(R.string.sw_flexural_design), Icons.Default.Square) }

                item {
                    ResultCard(isSafe = result.flexuralOk) {
                        DesignResultRow(stringResource(R.string.moment_capacity),
                            "${"%.1f".format(result.momentCapacity)} kN.m")
                        DesignResultRow(stringResource(R.string.axial_capacity),
                            "${"%.1f".format(result.axialCapacity)} kN")
                        DesignResultRow("Compression Depth (a)",
                            "${"%.1f".format(result.compressionDepth)} mm")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                        Text(stringResource(R.string.sw_num_stories), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DesignResultRow("Provided",
                            "${result.verticalReinforcement.bars}Φ${result.verticalReinforcement.diameter} @ ${result.verticalReinforcement.spacing} mm")
                        DesignResultRow("As Provided",
                            "${"%.0f".format(result.verticalReinforcement.providedArea)} mm²")
                        DesignResultRow("As Required",
                            "${"%.0f".format(result.verticalReinforcement.requiredArea)} mm²")
                        DesignResultRow("Ratio",
                            "${"%.2f".format(result.verticalReinforcement.ratio)}")
                    }
                }

                // Shear Design Results
                item { DesignSectionHeader(stringResource(R.string.sw_shear_design), Icons.Default.CompareArrows) }

                item {
                    ResultCard(isSafe = result.shearOk) {
                        DesignResultRow(stringResource(R.string.shear_capacity),
                            "${"%.1f".format(result.shearCapacity)} kN")
                        DesignResultRow("Concrete (φVc)",
                            "${"%.1f".format(result.concreteShearCapacity)} kN")
                        DesignResultRow("Steel (φVs)",
                            "${"%.1f".format(result.steelShearCapacity)} kN")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                        Text(stringResource(R.string.sw_shear_design), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DesignResultRow("Provided",
                            "${result.horizontalReinforcement.bars}Φ${result.horizontalReinforcement.diameter} @ ${result.horizontalReinforcement.spacing} mm")
                        DesignResultRow("As Provided",
                            "${"%.0f".format(result.horizontalReinforcement.providedArea)} mm²")
                        DesignResultRow("As Required",
                            "${"%.0f".format(result.horizontalReinforcement.requiredArea)} mm²")
                    }
                }

                // Boundary Element
                item { DesignSectionHeader(stringResource(R.string.sw_boundary_element), Icons.Default.SquareFoot) }

                item {
                    ResultCard(isSafe = result.boundaryElementType == BoundaryElementType.NONE ||
                        result.boundaryElementReinforcement?.let { it.ratio >= 0.9 } == true) {
                        DesignResultRow(stringResource(R.string.type), result.boundaryElementType.displayName)
                        result.boundaryElementReinforcement?.let { be ->
                            DesignResultRow("Rebar", "${be.bars}Φ${be.diameter} @ ${be.spacing} mm")
                            DesignResultRow("As Provided", "${"%.0f".format(be.providedArea)} mm²")
                            DesignResultRow("As Required", "${"%.0f".format(be.requiredArea)} mm²")
                        }
                    }
                }

                // Coupling Beam
                result.couplingBeamResult?.let { cb ->
                    item { DesignSectionHeader(stringResource(R.string.sw_coupling_section), Icons.Default.CropSquare) }

                    item {
                        ResultCard(isSafe = cb.isSafe) {
                            DesignResultRow("Diagonal Bars", "${cb.diagonalBars}Φ${cb.diagonalBarDiameter}")
                            DesignResultRow("Transverse", "Φ${cb.transverseBarsDiameter} @ ${cb.transverseBarsSpacing} mm")
                            DesignResultRow(stringResource(R.string.steel_util_ratio), "${(cb.utilizationRatio * 100).toInt()}%")
                        }
                    }
                }

                // Slenderness
                item { DesignSectionHeader(stringResource(R.string.sw_stability), Icons.Default.Height) }

                item {
                    ResultCard(isSafe = result.slendernessOk) {
                        DesignResultRow(stringResource(R.string.sw_h_t_ratio), "${"%.1f".format(result.slendernessRatio)}")
                        DesignResultRow(stringResource(R.string.sw_limit), "25.0")
                        DesignResultRow(stringResource(R.string.status),
                            if (result.slendernessOk) stringResource(R.string.pile_ok) else "Exceeds limit — 2nd order analysis needed")
                    }
                }

                // Quantities
                item { DesignSectionHeader(stringResource(R.string.sw_quantities_story), Icons.Default.Inventory2) }

                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            DesignResultRow(stringResource(R.string.concrete_vol),
                                "${"%.3f".format(result.concreteVolumePerStory)} m³")
                            DesignResultRow(stringResource(R.string.steel_weight),
                                "${"%.1f".format(result.steelWeightPerStory)} kg")
                        }
                    }
                }

                // Safety Checks
                item { DesignSectionHeader(stringResource(R.string.safety_checks), Icons.Default.VerifiedUser) }

                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (result.isSafe)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            result.safetyChecks.forEach { check ->
                                SafetyCheckRow(check)
                            }
                        }
                    }
                }

                // Code Notes
                if (result.codeNotes.isNotEmpty()) {
                    item { DesignSectionHeader("Code Notes", Icons.Default.Description) }

                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                result.codeNotes.forEach { note ->
                                    Text(note, fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 1.dp))
                                }
                            }
                        }
                    }
                }

                // Drawing
                item { DesignSectionHeader(stringResource(R.string.sw_wall_drawing), Icons.Default.Draw) }

                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1A1A2E)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ProfessionalShearWallDrawing(
                            wallLength = uiState.wallLength.toDoubleOrNull() ?: 4000.0,
                            wallThickness = uiState.wallThickness.toDoubleOrNull() ?: 300.0,
                            totalHeight = (uiState.storyHeight.toDoubleOrNull() ?: 3.0) * 1000.0 *
                                (uiState.numberOfStories.toIntOrNull() ?: 10),
                            storyHeight = (uiState.storyHeight.toDoubleOrNull() ?: 3.0) * 1000.0,
                            verticalRebar = result.verticalReinforcement,
                            horizontalRebar = result.horizontalReinforcement,
                            boundaryElementType = result.boundaryElementType,
                            boundaryRebar = result.boundaryElementReinforcement,
                            couplingBeamResult = result.couplingBeamResult,
                            wallType = uiState.wallType,
                            wallShape = uiState.wallShape,
                            axialLoad = uiState.axialLoad.toDoubleOrNull() ?: 0.0,
                            shearForce = uiState.shearForce.toDoubleOrNull() ?: 0.0,
                            bendingMoment = uiState.bendingMoment.toDoubleOrNull() ?: 0.0,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Export Button
                item {
                    Button(
                        onClick = { viewModel.exportToPdf(context) { /* done */ } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sw_export_pdf), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Reusable Components ─────────────────────────────────────────────

@Composable
private fun ResultCard(isSafe: Boolean, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSafe)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isSafe) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isSafe) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isSafe) "PASS" else "FAIL",
                    fontWeight = FontWeight.Bold,
                    color = if (isSafe) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SafetyCheckRow(check: ShearWallSafetyCheck) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (check.isSafe) "✓" else "✗",
            color = if (check.isSafe) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold, fontSize = 14.sp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(check.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                "${"%.2f".format(check.value)} / ${"%.2f".format(check.limit)} ${check.unit}",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
