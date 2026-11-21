package com.civileg.app.features.quality

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.db.QualityRecord
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.ui.defaultDesignCode
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor
import com.civileg.app.ui.DesignCodeStore
import com.civileg.app.ui.compose.components.*
import com.civileg.app.ui.compose.components.charts.*
import com.civileg.app.ui.compose.components.drawings.*
import com.civileg.app.ui.compose.components.drawings.DisciplineQcGenericDrawing
import com.civileg.app.utils.SoilBearingCalculator
import com.civileg.app.utils.SoilBearingInput
import com.civileg.app.utils.SoilBearingResult
import com.civileg.app.utils.BearingMethod
import com.civileg.app.utils.QualityControlEngine
import com.civileg.app.utils.QualityControlEngineNew
import com.civileg.app.utils.QcLimits
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.utils.SoilType
import com.civileg.app.features.quality.QualityEngineerViewModel
import com.civileg.app.viewmodel.ReportsQCState
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.*

internal fun runQcInputAction(
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    action: () -> Unit
) {
    try {
        action()
    } catch (error: IllegalArgumentException) {
        scope.launch {
            snackbarHostState.showSnackbar(error.message ?: "Please check input values / يرجى مراجعة المدخلات")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityEngineerScreen(
    onNavigateBack: () -> Unit,
    viewModel: QualityEngineerViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedGroup by rememberSaveable { mutableStateOf(QcGroup.CONCRETE) }
    var selectedTab by rememberSaveable { mutableStateOf(QcTab.CONCRETE) }
    val groupTabs = QcTab.entries.filter { it.group == selectedGroup }
    // Derived from the selected module so it stays correct after process restoration.
    val selectedTabIndex = groupTabs.indexOf(selectedTab).coerceAtLeast(0)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { AppText(stringResource(R.string.home_quality_engineer)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )

            // Level 1 — discipline group (5 instead of 25 tabs)
            ScrollableTabRow(
                selectedTabIndex = QcGroup.entries.indexOf(selectedGroup),
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 0.dp
            ) {
                QcGroup.entries.forEach { group ->
                    Tab(
                        selected = selectedGroup == group,
                        onClick = {
                            if (selectedGroup != group) {
                                selectedGroup = group
                                selectedTab = QcTab.entries.first { it.group == group }
                            }
                        },
                        text = {
                            AppText(
                                stringResource(group.titleRes),
                                fontSize = 12.sp,
                                maxLines = 1,
                                fontWeight = if (selectedGroup == group) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Level 2 — modules inside the selected group
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex.coerceIn(0, (groupTabs.size - 1).coerceAtLeast(0)),
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                groupTabs.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                        },
                        text = {
                            AppText(
                                stringResource(tab.titleRes),
                                fontSize = 11.sp,
                                maxLines = 1,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                QcTab.CONCRETE -> ConcreteQCTab(viewModel, snackbarHostState, scope)
                QcTab.SOIL -> SoilFoundationQCTab(viewModel, snackbarHostState, scope)
                QcTab.REBAR -> RebarQCTab(viewModel, snackbarHostState, scope)
                QcTab.STEEL -> SteelStructureQCTab(viewModel, snackbarHostState, scope)
                QcTab.PRECAST -> DisciplineQcTab(viewModel, QcDiscipline.PRECAST, snackbarHostState, scope)
                QcTab.POST_TENSION -> DisciplineQcTab(viewModel, QcDiscipline.POST_TENSION, snackbarHostState, scope)
                QcTab.GROUTING -> DisciplineQcTab(viewModel, QcDiscipline.GROUTING, snackbarHostState, scope)
                QcTab.EARTHWORKS -> DisciplineQcTab(viewModel, QcDiscipline.EARTHWORKS, snackbarHostState, scope)
                QcTab.PILING -> DisciplineQcTab(viewModel, QcDiscipline.PILING, snackbarHostState, scope)
                QcTab.RETAINING -> DisciplineQcTab(viewModel, QcDiscipline.RETAINING_WALLS, snackbarHostState, scope)
                QcTab.SOIL_IMPROVEMENT -> DisciplineQcTab(viewModel, QcDiscipline.SOIL_IMPROVEMENT, snackbarHostState, scope)
                QcTab.WATER_TANKS -> DisciplineQcTab(viewModel, QcDiscipline.WATER_TANKS, snackbarHostState, scope)
                QcTab.DRAINAGE -> DisciplineQcTab(viewModel, QcDiscipline.DRAINAGE, snackbarHostState, scope)
                QcTab.ROADS -> RoadsQCTab(viewModel, snackbarHostState, scope)
                QcTab.BRIDGES -> BridgesQCTab(viewModel, snackbarHostState, scope)
                QcTab.TUNNELS -> TunnelsQCTab(viewModel, snackbarHostState, scope)
                QcTab.BUILDINGS -> BuildingsQCTab(viewModel, snackbarHostState, scope)
                QcTab.MASONRY -> DisciplineQcTab(viewModel, QcDiscipline.MASONRY, snackbarHostState, scope)
                QcTab.FLOORING -> DisciplineQcTab(viewModel, QcDiscipline.FLOORING, snackbarHostState, scope)
                QcTab.WATERPROOFING -> DisciplineQcTab(viewModel, QcDiscipline.WATERPROOFING, snackbarHostState, scope)
                QcTab.PAINTING -> DisciplineQcTab(viewModel, QcDiscipline.PAINTING, snackbarHostState, scope)
                QcTab.SITE_WORKS -> DisciplineQcTab(viewModel, QcDiscipline.SITE_WORKS, snackbarHostState, scope)
                QcTab.FIRE_PROTECTION -> DisciplineQcTab(viewModel, QcDiscipline.FIRE_PROTECTION, snackbarHostState, scope)
                QcTab.REPORTS -> QualityReportsTab(viewModel, snackbarHostState, scope)
                QcTab.COMPLIANCE -> QualityComplianceScreen()
            }
        }
        SnackbarHost(snackbarHostState)
    }
}

// ── Quality navigation model ────────────────────────────────────────────
// The screen used to expose 25 flat tabs in a single scroll row, which made the
// discipline you needed hard to find. Groups + rememberSaveable keep the last
// module open across configuration changes and keep the row short.

private enum class QcGroup(val titleRes: Int) {
    CONCRETE(R.string.qc_group_concrete),
    GROUND(R.string.qc_group_ground),
    INFRA(R.string.qc_group_infra),
    BUILDING(R.string.qc_group_building),
    REPORTS(R.string.qc_group_reports)
}

private enum class QcTab(val group: QcGroup, val titleRes: Int) {
    CONCRETE(QcGroup.CONCRETE, R.string.quality_tab_concrete),
    REBAR(QcGroup.CONCRETE, R.string.quality_tab_rebar),
    STEEL(QcGroup.CONCRETE, R.string.quality_tab_steel),
    PRECAST(QcGroup.CONCRETE, R.string.quality_tab_precast),
    POST_TENSION(QcGroup.CONCRETE, R.string.quality_tab_post_tension),
    GROUTING(QcGroup.CONCRETE, R.string.quality_tab_grouting),

    SOIL(QcGroup.GROUND, R.string.quality_tab_soil),
    EARTHWORKS(QcGroup.GROUND, R.string.quality_tab_earthworks),
    PILING(QcGroup.GROUND, R.string.quality_tab_piling),
    RETAINING(QcGroup.GROUND, R.string.quality_tab_retaining),
    SOIL_IMPROVEMENT(QcGroup.GROUND, R.string.quality_tab_soil_improvement),
    WATER_TANKS(QcGroup.GROUND, R.string.quality_tab_water_tanks),
    DRAINAGE(QcGroup.GROUND, R.string.quality_tab_drainage),

    ROADS(QcGroup.INFRA, R.string.quality_tab_roads),
    BRIDGES(QcGroup.INFRA, R.string.quality_tab_bridges),
    TUNNELS(QcGroup.INFRA, R.string.quality_tab_tunnels),

    BUILDINGS(QcGroup.BUILDING, R.string.quality_tab_buildings),
    MASONRY(QcGroup.BUILDING, R.string.quality_tab_masonry),
    FLOORING(QcGroup.BUILDING, R.string.quality_tab_flooring),
    WATERPROOFING(QcGroup.BUILDING, R.string.quality_tab_waterproofing),
    PAINTING(QcGroup.BUILDING, R.string.quality_tab_painting),
    SITE_WORKS(QcGroup.BUILDING, R.string.quality_tab_site_works),
    FIRE_PROTECTION(QcGroup.BUILDING, R.string.quality_tab_fire_protection),

    REPORTS(QcGroup.REPORTS, R.string.quality_tab_reports),
    COMPLIANCE(QcGroup.REPORTS, R.string.quality_tab_compliance)
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 1: Concrete Quality Control
// ═══════════════════════════════════════════════════════════════════════

@Composable
