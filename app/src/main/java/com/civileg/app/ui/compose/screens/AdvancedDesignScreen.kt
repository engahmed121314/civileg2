package com.civileg.app.ui.compose.screens

import com.civileg.app.ui.responsive.dimens
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.civileg.app.R
import com.civileg.app.domain.calculations.compositebeam.CompositeBeamDesign
import com.civileg.app.domain.calculations.compositebeam.CompositeBeamDesignResult
import com.civileg.app.domain.calculations.compositebeam.CompositeBeamInput
import com.civileg.app.domain.calculations.aci.ACIAdvancedBeam
import com.civileg.app.domain.calculations.aci.ACIAdvancedColumn
import com.civileg.app.domain.calculations.aci.ACIAdvancedSlab
import com.civileg.app.domain.calculations.compositebeam.CompositeDesignCode
import com.civileg.app.domain.calculations.ecp.ECPAdvancedBeam
import com.civileg.app.domain.calculations.ecp.ECPAdvancedColumn
import com.civileg.app.domain.calculations.ecp.ECPAdvancedSlab
import com.civileg.app.domain.calculations.sbc.SBCAdvancedBeam
import com.civileg.app.domain.calculations.sbc.SBCAdvancedColumn
import com.civileg.app.domain.calculations.sbc.SBCAdvancedSlab
import com.civileg.app.domain.entities.AdvancedBeamResult
import com.civileg.app.domain.entities.BeamSectionType
import com.civileg.app.domain.entities.BeamType
import com.civileg.app.domain.entities.AdvancedColumnResult
import com.civileg.app.domain.entities.ColumnEndConditions
import com.civileg.app.domain.entities.ColumnType
import com.civileg.app.domain.entities.ConnectedSlabType
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.AdvancedSlabResult
import com.civileg.app.domain.entities.EdgeCondition
import com.civileg.app.domain.entities.PrestressType
import com.civileg.app.domain.entities.SlabSupportConditions
import com.civileg.app.domain.entities.SlabType
import com.civileg.app.domain.entities.TendonType
import com.civileg.app.domain.entities.EndCondition
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.calculations.deepbeam.DeepBeamCode
import com.civileg.app.domain.calculations.deepbeam.DeepBeamDesign
import com.civileg.app.domain.calculations.deepbeam.DeepBeamInput
import com.civileg.app.domain.calculations.deepbeam.DeepBeamResult
import com.civileg.app.domain.calculations.fireresistance.FireCode
import com.civileg.app.domain.calculations.fireresistance.FireElementType
import com.civileg.app.domain.calculations.fireresistance.FireResistanceDesign
import com.civileg.app.domain.calculations.fireresistance.FireResistanceInput
import com.civileg.app.domain.calculations.fireresistance.FireResistanceResult
import com.civileg.app.domain.calculations.pilegroup.PileGroupAnalysis
import com.civileg.app.domain.calculations.pilegroup.PileGroupInput
import com.civileg.app.domain.calculations.pilegroup.PileGroupResult
import com.civileg.app.domain.calculations.settlement.SettlementAnalysis
import com.civileg.app.domain.calculations.settlement.SettlementInput
import com.civileg.app.domain.calculations.settlement.SettlementResult

private enum class AdvancedModule(val title: String, val titleAr: String) {
    Beam("Beam", "كمرة"),
    Column("Column", "عمود"),
    Slab("Slab", "بلاطة"),
    CompositeBeam("Composite", "مركبة"),
    FireResistance("Fire", "حريق"),
    DeepBeam("Deep Beam", "كمرة عميقة"),
    PileGroup("Piles", "خوازيق"),
    Settlement("Settlement", "هبوط")
}

private data class UiCheck(
    val name: String,
    val calculated: Double,
    val limit: Double,
    val unit: String,
    val passed: Boolean
)

/** أنواع الكمرات المدعومة في تبويب الكمرة المتقدمة. */
private enum class BeamKind(val title: String) {
    SIMPLY_SUPPORTED("Simple / بسيطة"),
    FIXED("Fixed / ثابتة"),
    CANTILEVER("Cantilever / كابولي")
}

/** حالة إدخال واحدة لكل الوحدات حتى لا تُفقد القيم عند تبديل التبويبات. */
private class AdvancedFormState {
    // Advanced beam (ECP/SBC/ACI)
    var beamCode by mutableStateOf(DesignCode.ECP)
    var beamKind by mutableStateOf(BeamKind.SIMPLY_SUPPORTED)
    var beamCombo by mutableStateOf(LoadCombination.DEAD_LIVE)
    var bSpan by mutableStateOf("6.0")
    var bWidth by mutableStateOf("250")
    var bDepth by mutableStateOf("600")
    var bFcu by mutableStateOf("30")
    var bFy by mutableStateOf("400")
    var bDead by mutableStateOf("20")
    var bLive by mutableStateOf("10")

    // Advanced column (ECP/SBC/ACI)
    var colCode by mutableStateOf(DesignCode.ECP)
    var colCombo by mutableStateOf(LoadCombination.DEAD_LIVE)
    var colTop by mutableStateOf(EndCondition.PINNED)
    var colBottom by mutableStateOf(EndCondition.FIXED)
    var colSlab by mutableStateOf(ConnectedSlabType.SOLID)
    var colHasCap by mutableStateOf(true)
    var colWidth by mutableStateOf("300")
    var colDepth by mutableStateOf("600")
    var colFcu by mutableStateOf("30")
    var colFy by mutableStateOf("400")
    var colAxial by mutableStateOf("1500")
    var colMx by mutableStateOf("50")
    var colMy by mutableStateOf("0")
    var colLength by mutableStateOf("3.0")

    // Advanced slab (ECP/SBC/ACI)
    var slabCode by mutableStateOf(DesignCode.ECP)
    var slabKind by mutableStateOf(SlabKind.SOLID)
    var slabCombo by mutableStateOf(LoadCombination.DEAD_LIVE)
    var slFcu by mutableStateOf("30")
    var slFy by mutableStateOf("400")
    var slDead by mutableStateOf("6")
    var slLive by mutableStateOf("4")
    var slThickness by mutableStateOf("200")
    var slSpan1 by mutableStateOf("5.0")
    var slSpan2 by mutableStateOf("6.0")
    var slWidth by mutableStateOf("1.0")
    var slRibWidth by mutableStateOf("120")
    var slRibSpacing by mutableStateOf("500")
    var slRibDepth by mutableStateOf("280")
    var slBlockWidth by mutableStateOf("380")
    var slBlockHeight by mutableStateOf("200")
    var slColumnSize by mutableStateOf("400")
    var slDropThickness by mutableStateOf("300")
    var slDropSize by mutableStateOf("2.0")
    var slEdge by mutableStateOf(EdgeCondition.SIMPLY_SUPPORTED)
    var slDropPanel by mutableStateOf(false)
    var slColCapital by mutableStateOf(false)
    var slTendon by mutableStateOf(TendonType.BONDED)
    var slPrestressForce by mutableStateOf("1000")
    var slEccentricity by mutableStateOf("50")
    var slTopping by mutableStateOf("50")
    var slPrestressType by mutableStateOf(PrestressType.POSTTENSIONED)

    // Composite beam
    var compositeCode by mutableStateOf(CompositeDesignCode.ECP)
    var cSpan by mutableStateOf("8.0")
    var cSpacing by mutableStateOf("3.0")
    var cSlab by mutableStateOf("120")
    var cSteelDepth by mutableStateOf("450")
    var cSteelArea by mutableStateOf("7200")
    var cSteelInertia by mutableStateOf("210000000")
    var cSteelFy by mutableStateOf("355")
    var cFcu by mutableStateOf("30")
    var cLive by mutableStateOf("5.0")
    var cSuperDead by mutableStateOf("2.0")
    var cFlangeWidth by mutableStateOf("200")
    var cPartial by mutableStateOf("1.0")
    var cDeflection by mutableStateOf("360")
    var cStuds by mutableStateOf("0")

    // Fire resistance
    var fireCode by mutableStateOf(FireCode.ECP)
    var fireElement by mutableStateOf(FireElementType.BEAM)
    var fireRating by mutableStateOf("120")
    var fireCover by mutableStateOf("40")
    var fireWidth by mutableStateOf("300")
    var fireDepth by mutableStateOf("700")
    var fireFcu by mutableStateOf("30")
    var fireFy by mutableStateOf("420")
    var fireRho by mutableStateOf("0.010")

    // Deep beam
    var deepCode by mutableStateOf(DeepBeamCode.ECP)
    var deepContinuous by mutableStateOf(false)
    var dSpan by mutableStateOf("2.4")
    var dWidth by mutableStateOf("300")
    var dDepth by mutableStateOf("1200")
    var dFcu by mutableStateOf("30")
    var dFy by mutableStateOf("400")
    var dDead by mutableStateOf("12.0")
    var dLive by mutableStateOf("8.0")
    var dPoint by mutableStateOf("0.0")
    var dCover by mutableStateOf("40")
    var dBearing by mutableStateOf("300")
    var dTieArea by mutableStateOf("0")
    var dStirrupArea by mutableStateOf("157")
    var dStirrupSpacing by mutableStateOf("200")

    // Pile group
    var pNx by mutableStateOf("3")
    var pNy by mutableStateOf("3")
    var pSx by mutableStateOf("1800")
    var pSy by mutableStateOf("1800")
    var pDia by mutableStateOf("600")
    var pLength by mutableStateOf("18.0")
    var pCapacity by mutableStateOf("1200")
    var pLoad by mutableStateOf("8000")
    var pMx by mutableStateOf("0.0")
    var pMy by mutableStateOf("0.0")
    var pH by mutableStateOf("0.0")
    var pModulus by mutableStateOf("25000")
    var pColW by mutableStateOf("600")
    var pColD by mutableStateOf("600")
    var pAllowSettle by mutableStateOf("25")

    // Settlement
    var sWidth by mutableStateOf("2.5")
    var sLength by mutableStateOf("2.5")
    var sPressure by mutableStateOf("180")
    var sModulus by mutableStateOf("25")
    var sPoisson by mutableStateOf("0.30")
    var sInfluence by mutableStateOf("1.0")
    var sLayer by mutableStateOf("4.0")
    var sCc by mutableStateOf("0.30")
    var sCr by mutableStateOf("0.0")
    var sE0 by mutableStateOf("0.90")
    var sSigma0 by mutableStateOf("100")
    var sSigmaP by mutableStateOf("0")
    var sCv by mutableStateOf("2.0")
    var sDrainage by mutableStateOf("2.0")
    var sTime by mutableStateOf("1.0")
    var sAllowSettle by mutableStateOf("25")
}

private fun inputSignature(vararg values: Any?): String =
    values.joinToString(separator = "\u001F") { it.toString() }

private fun AdvancedFormState.compositeSignature() = inputSignature(
    compositeCode, cSpan, cSpacing, cSlab, cSteelDepth, cSteelArea, cSteelInertia,
    cSteelFy, cFcu, cLive, cSuperDead, cFlangeWidth, cPartial, cDeflection, cStuds
)

private fun AdvancedFormState.fireSignature() = inputSignature(
    fireCode, fireElement, fireRating, fireCover, fireWidth, fireDepth, fireFcu, fireFy, fireRho
)

private fun AdvancedFormState.deepSignature() = inputSignature(
    deepCode, deepContinuous, dSpan, dWidth, dDepth, dFcu, dFy, dDead, dLive, dPoint,
    dCover, dBearing, dTieArea, dStirrupArea, dStirrupSpacing
)

private fun AdvancedFormState.pileSignature() = inputSignature(
    pNx, pNy, pSx, pSy, pDia, pLength, pCapacity, pLoad, pMx, pMy, pH, pModulus,
    pColW, pColD, pAllowSettle
)

private fun AdvancedFormState.beamSignature() = inputSignature(
    beamCode, beamKind, beamCombo, bSpan, bWidth, bDepth, bFcu, bFy, bDead, bLive
)

private fun AdvancedFormState.columnSignature() = inputSignature(
    colCode, colCombo, colTop, colBottom, colSlab, colHasCap,
    colWidth, colDepth, colFcu, colFy, colAxial, colMx, colMy, colLength
)

private fun AdvancedFormState.slabSignature() = inputSignature(
    slabCode, slabKind, slabCombo, slFcu, slFy, slDead, slLive, slThickness,
    slSpan1, slSpan2, slWidth, slRibWidth, slRibSpacing, slRibDepth,
    slBlockWidth, slBlockHeight, slColumnSize, slDropThickness, slDropSize,
    slEdge, slDropPanel, slColCapital, slTendon, slPrestressForce,
    slEccentricity, slTopping, slPrestressType
)

private fun AdvancedFormState.settlementSignature() = inputSignature(
    sWidth, sLength, sPressure, sModulus, sPoisson, sInfluence, sLayer, sCc, sCr,
    sE0, sSigma0, sSigmaP, sCv, sDrainage, sTime, sAllowSettle
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedDesignScreen(onNavigateBack: () -> Unit = {}) {
    val form = remember { AdvancedFormState() }
    var moduleIndex by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var compositeResult by remember { mutableStateOf<CompositeBeamDesignResult?>(null) }
    var fireResult by remember { mutableStateOf<FireResistanceResult?>(null) }
    var deepResult by remember { mutableStateOf<DeepBeamResult?>(null) }
    var pileResult by remember { mutableStateOf<PileGroupResult?>(null) }
    var settlementResult by remember { mutableStateOf<SettlementResult?>(null) }
    var compositeResultSignature by remember { mutableStateOf<String?>(null) }
    var fireResultSignature by remember { mutableStateOf<String?>(null) }
    var deepResultSignature by remember { mutableStateOf<String?>(null) }
    var pileResultSignature by remember { mutableStateOf<String?>(null) }
    var settlementResultSignature by remember { mutableStateOf<String?>(null) }
    var beamResult by remember { mutableStateOf<AdvancedBeamResult?>(null) }
    var beamResultSignature by remember { mutableStateOf<String?>(null) }
    var columnResult by remember { mutableStateOf<AdvancedColumnResult?>(null) }
    var columnResultSignature by remember { mutableStateOf<String?>(null) }
    var slabResult by remember { mutableStateOf<AdvancedSlabResult?>(null) }
    var slabResultSignature by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }

    fun calculateComposite() {
        val signature = form.compositeSignature()
        compositeResult = null
        compositeResultSignature = null
        runCatching {
            CompositeBeamInput(
                code = form.compositeCode,
                spanM = form.cSpan.num("span"),
                beamSpacingM = form.cSpacing.num("beam spacing"),
                slabThicknessMm = form.cSlab.num("slab thickness"),
                steelBeamDepthMm = form.cSteelDepth.num("steel depth"),
                steelBeamAreaMm2 = form.cSteelArea.num("steel area"),
                steelBeamInertiaMm4 = form.cSteelInertia.num("steel inertia"),
                steelYieldStrengthMPa = form.cSteelFy.num("steel fy"),
                concreteStrengthMPa = form.cFcu.num("concrete strength"),
                liveLoadKPa = form.cLive.num("live load"),
                superimposedDeadLoadKPa = form.cSuperDead.num("superimposed dead load"),
                steelFlangeWidthMm = form.cFlangeWidth.num("flange width"),
                partialShearConnectionRatio = form.cPartial.num("partial shear ratio"),
                deflectionLimitRatio = form.cDeflection.num("deflection limit"),
                providedStuds = form.cStuds.intOrNull("provided studs")
            )
        }.mapCatching { CompositeBeamDesign.design(it) }
            .onSuccess {
                compositeResult = it
                compositeResultSignature = signature
            }
            .onFailure { errorMessage = it.message ?: "Composite beam calculation failed" }
    }

    fun calculateBeam() {
        val signature = form.beamSignature()
        beamResult = null
        beamResultSignature = null
        runCatching {
            val span = form.bSpan.num("span")
            val beamType = when (form.beamKind) {
                BeamKind.SIMPLY_SUPPORTED -> BeamType.SimplySupported(span)
                BeamKind.FIXED -> BeamType.Fixed(span)
                BeamKind.CANTILEVER -> BeamType.Cantilever(span)
            }
            val fcu = form.bFcu.num("fcu")
            val fy = form.bFy.num("fy")
            val dead = form.bDead.num("dead load")
            val live = form.bLive.num("live load")
            val width = form.bWidth.num("width")
            val depth = form.bDepth.num("depth")
            when (form.beamCode) {
                DesignCode.ECP -> ECPAdvancedBeam().designBeam(
                    beamType, BeamSectionType.RECTANGULAR, fcu, fy, dead, live, width, depth,
                    inventory = null, loadCombination = form.beamCombo
                )
                DesignCode.SBC -> SBCAdvancedBeam().designBeam(
                    beamType, BeamSectionType.RECTANGULAR, fcu, fy, dead, live, width, depth,
                    inventory = null, loadCombination = form.beamCombo
                )
                DesignCode.ACI -> ACIAdvancedBeam().designBeam(
                    beamType, BeamSectionType.RECTANGULAR, fcu, fy, dead, live, width, depth,
                    inventory = null, loadCombination = form.beamCombo
                )
            }
        }.onSuccess {
            beamResult = it
            beamResultSignature = signature
        }.onFailure { errorMessage = it.message ?: "Beam calculation failed" }
    }

    fun calculateColumn() {
        val signature = form.columnSignature()
        columnResult = null
        columnResultSignature = null
        runCatching {
            val section = ColumnType.Rectangular(
                width = form.colWidth.num("column width"),
                depth = form.colDepth.num("column depth")
            )
            val fcu = form.colFcu.num("fcu")
            val fy = form.colFy.num("fy")
            val axial = form.colAxial.num("axial load")
            val mx = form.colMx.num("moment X")
            val my = form.colMy.num("moment Y")
            val length = form.colLength.num("unsupported length")
            val ends = ColumnEndConditions(form.colTop, form.colBottom)
            when (form.colCode) {
                DesignCode.ECP -> ECPAdvancedColumn().designAdvancedColumn(
                    section, fcu, fy, axial, mx, my, length, ends,
                    form.colSlab, form.colHasCap, null, form.colCombo
                )
                DesignCode.SBC -> SBCAdvancedColumn().designAdvancedColumn(
                    section, fcu, fy, axial, mx, my, length, ends,
                    form.colSlab, form.colHasCap, null, form.colCombo
                )
                DesignCode.ACI -> ACIAdvancedColumn().designAdvancedColumn(
                    section, fcu, fy, axial, mx, my, length, ends,
                    form.colSlab, form.colHasCap, null, form.colCombo
                )
            }
        }.onSuccess {
            columnResult = it
            columnResultSignature = signature
        }.onFailure { errorMessage = it.message ?: "Column calculation failed" }
    }

    fun calculateSlab() {
        val signature = form.slabSignature()
        slabResult = null
        slabResultSignature = null
        runCatching {
            val supports = SlabSupportConditions(form.slEdge, form.slEdge, form.slEdge, form.slEdge)
            val type = when (form.slabKind) {
                SlabKind.SOLID -> SlabType.Solid(
                    thickness = form.slThickness.num("thickness"),
                    shortSpan = form.slSpan1.num("short span"),
                    longSpan = form.slSpan2.num("long span"),
                    supportConditions = supports
                )
                SlabKind.ONE_WAY -> SlabType.OneWay(
                    thickness = form.slThickness.num("thickness"),
                    span = form.slSpan1.num("span"),
                    width = form.slWidth.num("width")
                )
                SlabKind.TWO_WAY -> SlabType.TwoWay(
                    thickness = form.slThickness.num("thickness"),
                    shortSpan = form.slSpan1.num("short span"),
                    longSpan = form.slSpan2.num("long span"),
                    supportConditions = supports,
                    dropPanel = form.slDropPanel,
                    columnCapital = form.slColCapital
                )
                SlabKind.HORDI -> SlabType.Hordi(
                    totalThickness = form.slThickness.num("total thickness"),
                    ribWidth = form.slRibWidth.num("rib width"),
                    ribSpacing = form.slRibSpacing.num("rib spacing"),
                    blockWidth = form.slBlockWidth.num("block width"),
                    blockHeight = form.slBlockHeight.num("block height"),
                    span = form.slSpan1.num("span"),
                    supportConditions = supports
                )
                SlabKind.WAFFLE -> SlabType.Waffle(
                    totalThickness = form.slThickness.num("total thickness"),
                    ribWidth = form.slRibWidth.num("rib width"),
                    ribDepth = form.slRibDepth.num("rib depth"),
                    ribSpacing = form.slRibSpacing.num("rib spacing"),
                    shortSpan = form.slSpan1.num("short span"),
                    longSpan = form.slSpan2.num("long span"),
                    supportConditions = supports
                )
                SlabKind.POST_TENSIONED -> SlabType.PostTensioned(
                    thickness = form.slThickness.num("thickness"),
                    shortSpan = form.slSpan1.num("short span"),
                    longSpan = form.slSpan2.num("long span"),
                    tendonType = form.slTendon,
                    prestressForce = form.slPrestressForce.num("prestress force"),
                    eccentricity = form.slEccentricity.num("eccentricity"),
                    supportConditions = supports
                )
                SlabKind.FLAT_PLATE -> SlabType.FlatPlate(
                    thickness = form.slThickness.num("thickness"),
                    panelLength = form.slSpan1.num("panel length"),
                    panelWidth = form.slSpan2.num("panel width"),
                    columnSize = form.slColumnSize.num("column size")
                )
                SlabKind.FLAT_SLAB -> SlabType.FlatSlab(
                    thickness = form.slThickness.num("thickness"),
                    dropPanelThickness = form.slDropThickness.num("drop panel thickness"),
                    dropPanelSize = form.slDropSize.num("drop panel size"),
                    panelLength = form.slSpan1.num("panel length"),
                    panelWidth = form.slSpan2.num("panel width"),
                    columnSize = form.slColumnSize.num("column size")
                )
                SlabKind.PRECAST -> SlabType.Precast(
                    thickness = form.slThickness.num("thickness"),
                    width = form.slWidth.num("width"),
                    length = form.slSpan1.num("length"),
                    toppingThickness = form.slTopping.num("topping thickness"),
                    prestressType = form.slPrestressType
                )
            }
            val fcu = form.slFcu.num("fcu")
            val fy = form.slFy.num("fy")
            val dead = form.slDead.num("dead load")
            val live = form.slLive.num("live load")
            when (form.slabCode) {
                DesignCode.ECP -> ECPAdvancedSlab().designSlab(type, fcu, fy, dead, live, null, form.slabCombo)
                DesignCode.SBC -> SBCAdvancedSlab().designSlab(type, fcu, fy, dead, live, null, form.slabCombo)
                DesignCode.ACI -> ACIAdvancedSlab().designSlab(type, fcu, fy, dead, live, null, form.slabCombo)
            }
        }.onSuccess {
            slabResult = it
            slabResultSignature = signature
        }.onFailure { errorMessage = it.message ?: "Slab calculation failed" }
    }

    fun calculateFire() {
        val signature = form.fireSignature()
        fireResult = null
        fireResultSignature = null
        runCatching {
            FireResistanceInput(
                code = form.fireCode,
                elementType = form.fireElement,
                requiredRatingMinutes = form.fireRating.int("required rating"),
                coverMm = form.fireCover.num("cover"),
                sectionWidthMm = form.fireWidth.num("section width"),
                sectionDepthMm = form.fireDepth.num("section depth"),
                concreteStrengthMPa = form.fireFcu.num("concrete strength"),
                steelStrengthMPa = form.fireFy.num("steel strength"),
                reinforcementRatio = form.fireRho.num("reinforcement ratio")
            )
        }.mapCatching { FireResistanceDesign.design(it) }
            .onSuccess {
                fireResult = it
                fireResultSignature = signature
            }
            .onFailure { errorMessage = it.message ?: "Fire resistance calculation failed" }
    }

    fun calculateDeep() {
        val signature = form.deepSignature()
        deepResult = null
        deepResultSignature = null
        runCatching {
            DeepBeamInput(
                code = form.deepCode,
                span = form.dSpan.num("span"),
                width = form.dWidth.num("width"),
                depth = form.dDepth.num("depth"),
                fcu = form.dFcu.num("fcu"),
                fy = form.dFy.num("fy"),
                deadLoad = form.dDead.num("dead load"),
                liveLoad = form.dLive.num("live load"),
                pointLoad = form.dPoint.num("point load"),
                clearCover = form.dCover.num("clear cover"),
                bearingPlateWidth = form.dBearing.num("bearing plate"),
                providedTieArea = form.dTieArea.num("tie area"),
                providedStirrupArea = form.dStirrupArea.num("stirrup area"),
                stirrupSpacing = form.dStirrupSpacing.num("stirrup spacing"),
                isContinuous = form.deepContinuous
            )
        }.mapCatching { DeepBeamDesign().design(it) }
            .onSuccess {
                deepResult = it
                deepResultSignature = signature
            }
            .onFailure { errorMessage = it.message ?: "Deep beam calculation failed" }
    }

    fun calculatePile() {
        val signature = form.pileSignature()
        pileResult = null
        pileResultSignature = null
        runCatching {
            PileGroupInput(
                pilesX = form.pNx.int("piles X"),
                pilesY = form.pNy.int("piles Y"),
                spacingX = form.pSx.num("spacing X"),
                spacingY = form.pSy.num("spacing Y"),
                pileDiameter = form.pDia.num("pile diameter"),
                pileLength = form.pLength.num("pile length"),
                pileCapacity = form.pCapacity.num("pile capacity"),
                verticalLoad = form.pLoad.num("vertical load"),
                momentX = form.pMx.num("moment X"),
                momentY = form.pMy.num("moment Y"),
                horizontalLoad = form.pH.num("horizontal load"),
                pileModulus = form.pModulus.num("pile modulus"),
                columnWidth = form.pColW.num("column width"),
                columnDepth = form.pColD.num("column depth"),
                allowableSettlement = form.pAllowSettle.num("allowable settlement")
            )
        }.mapCatching { PileGroupAnalysis().analyze(it) }
            .onSuccess {
                pileResult = it
                pileResultSignature = signature
            }
            .onFailure { errorMessage = it.message ?: "Pile group analysis failed" }
    }

    fun calculateSettlement() {
        val signature = form.settlementSignature()
        settlementResult = null
        settlementResultSignature = null
        runCatching {
            SettlementInput(
                footingWidth = form.sWidth.num("footing width"),
                footingLength = form.sLength.num("footing length"),
                appliedPressure = form.sPressure.num("applied pressure"),
                elasticModulus = form.sModulus.num("elastic modulus"),
                poissonRatio = form.sPoisson.num("Poisson ratio"),
                influenceFactor = form.sInfluence.num("influence factor"),
                layerThickness = form.sLayer.num("layer thickness"),
                compressionIndex = form.sCc.num("compression index"),
                recompressionIndex = form.sCr.num("recompression index"),
                initialVoidRatio = form.sE0.num("initial void ratio"),
                initialEffectiveStress = form.sSigma0.num("initial effective stress"),
                preconsolidationPressure = form.sSigmaP.num("preconsolidation pressure"),
                coefficientOfConsolidation = form.sCv.num("coefficient of consolidation"),
                drainagePath = form.sDrainage.num("drainage path"),
                timeYears = form.sTime.num("time"),
                allowableSettlement = form.sAllowSettle.num("allowable settlement")
            )
        }.mapCatching { SettlementAnalysis().analyze(it) }
            .onSuccess {
                settlementResult = it
                settlementResultSignature = signature
            }
            .onFailure { errorMessage = it.message ?: "Settlement analysis failed" }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { AppText(stringResource(R.string.home_advanced_design), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            ScrollableTabRow(
                selectedTabIndex = moduleIndex,
                edgePadding = 12.dp
            ) {
                AdvancedModule.entries.forEachIndexed { index, module ->
                    Tab(
                        selected = moduleIndex == index,
                        onClick = { moduleIndex = index },
                        text = {
                            AppText(
                                "${module.title} / ${module.titleAr}",
                                fontWeight = if (moduleIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.dimens.screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                when (AdvancedModule.entries[moduleIndex]) {
                    AdvancedModule.Beam ->                     beamContent(
                        form,
                        beamResult.takeIf { beamResultSignature == form.beamSignature() },
                        ::calculateBeam
                    )
                    AdvancedModule.Column ->                     columnContent(
                        form,
                        columnResult.takeIf { columnResultSignature == form.columnSignature() },
                        ::calculateColumn
                    )
                    AdvancedModule.Slab ->                     slabContent(
                        form,
                        slabResult.takeIf { slabResultSignature == form.slabSignature() },
                        ::calculateSlab
                    )
                    AdvancedModule.CompositeBeam ->                     compositeContent(
                        form,
                        compositeResult.takeIf { compositeResultSignature == form.compositeSignature() },
                        ::calculateComposite
                    )
                    AdvancedModule.FireResistance ->                     fireContent(
                        form,
                        fireResult.takeIf { fireResultSignature == form.fireSignature() },
                        ::calculateFire
                    )
                    AdvancedModule.DeepBeam ->                     deepContent(
                        form,
                        deepResult.takeIf { deepResultSignature == form.deepSignature() },
                        ::calculateDeep
                    )
                    AdvancedModule.PileGroup ->                     pileContent(
                        form,
                        pileResult.takeIf { pileResultSignature == form.pileSignature() },
                        ::calculatePile
                    )
                    AdvancedModule.Settlement ->                     settlementContent(
                        form,
                        settlementResult.takeIf { settlementResultSignature == form.settlementSignature() },
                        ::calculateSettlement
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

// ───────────────────────────── Advanced beam (ECP/SBC/ACI) ─────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.beamContent(
    form: AdvancedFormState,
    result: AdvancedBeamResult?,
    onCalculate: () -> Unit
) {
    item {
        SectionTitle("Advanced Beam Design", "تصميم كمرة متقدم")
        Spacer(Modifier.height(8.dp))
        CodeChips(
            options = DesignCode.entries,
            selected = form.beamCode,
            label = { it.name },
            onSelected = { form.beamCode = it }
        )
        Spacer(Modifier.height(8.dp))
        CodeChips(
            options = BeamKind.entries,
            selected = form.beamKind,
            label = { it.title },
            onSelected = { form.beamKind = it }
        )
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Span (m)", form.bSpan, Modifier.weight(1f)) { form.bSpan = it }
            NumField("Width (mm)", form.bWidth, Modifier.weight(1f)) { form.bWidth = it }
            NumField("Depth (mm)", form.bDepth, Modifier.weight(1f)) { form.bDepth = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("fcu (MPa)", form.bFcu, Modifier.weight(1f)) { form.bFcu = it }
            NumField("fy (MPa)", form.bFy, Modifier.weight(1f)) { form.bFy = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Dead (kN/m)", form.bDead, Modifier.weight(1f)) { form.bDead = it }
            NumField("Live (kN/m)", form.bLive, Modifier.weight(1f)) { form.bLive = it }
        }
    }
    item {
        CodeChips(
            options = LoadCombination.entries.toList(),
            selected = form.beamCombo,
            label = { it.name },
            onSelected = { form.beamCombo = it }
        )
    }
    item { CalculateButton(onCalculate) }
    result?.let { r ->
        item {
            StatusBanner(
                isSafe = r.flexureResult.isSafe && r.shearResult.isSafe && r.deflectionCheck.isSafe
            )
        }
        item {
            ResultCard("Flexure / الانحناء") {
                ResultRow("As required", "%.0f mm²".format(r.flexureResult.astRequired), bold = true)
                ResultRow("As provided", "%.0f mm²".format(r.flexureResult.astProvided))
                ResultRow("Bars", "${r.flexureResult.numberOfBars}Ø${r.flexureResult.barDiameter.toInt()}")
                ResultRow("Utilization", "%.2f".format(r.flexureResult.utilizationRatio))
            }
        }
        item {
            ResultCard("Shear / القص") {
                ResultRow("Stirrups", "Ø${r.shearResult.stirrupDiameter.toInt()} @ ${r.shearResult.stirrupSpacing.toInt()} mm", bold = true)
                ResultRow("Utilization", "%.2f".format(r.shearResult.utilizationRatio))
            }
        }
        item {
            ResultCard("Deflection / الترخيم") {
                ResultRow("Calculated", "%.1f mm".format(r.deflectionCheck.calculatedDeflection), bold = true)
                ResultRow("Allowable", "%.1f mm".format(r.deflectionCheck.allowableDeflection))
                if (r.deflectionCheck.message.isNotBlank()) ResultRow("Note", r.deflectionCheck.message)
            }
        }
        r.crackWidthCheck?.let { cw ->
            item {
                ResultCard("Crack width / عرض الشقوق") {
                    ResultRow("Calculated", "%.3f mm".format(cw.calculatedWidth), bold = true)
                    ResultRow("Allowable", "%.3f mm".format(cw.allowableWidth))
                }
            }
        }
        item { NotesCard(r.warnings, r.codeNotes) }
    }
}

// ───────────────────────────── Advanced column (ECP/SBC/ACI) ─────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.columnContent(
    form: AdvancedFormState,
    result: AdvancedColumnResult?,
    onCalculate: () -> Unit
) {
    item {
        SectionTitle("Advanced Column Design", "تصميم عمود متقدم")
        Spacer(Modifier.height(8.dp))
        CodeChips(
            options = DesignCode.entries,
            selected = form.colCode,
            label = { it.name },
            onSelected = { form.colCode = it }
        )
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Width (mm)", form.colWidth, Modifier.weight(1f)) { form.colWidth = it }
            NumField("Depth (mm)", form.colDepth, Modifier.weight(1f)) { form.colDepth = it }
            NumField("Length (m)", form.colLength, Modifier.weight(1f)) { form.colLength = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("fcu (MPa)", form.colFcu, Modifier.weight(1f)) { form.colFcu = it }
            NumField("fy (MPa)", form.colFy, Modifier.weight(1f)) { form.colFy = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Axial (kN)", form.colAxial, Modifier.weight(1f)) { form.colAxial = it }
            NumField("Mx (kN.m)", form.colMx, Modifier.weight(1f)) { form.colMx = it }
            NumField("My (kN.m)", form.colMy, Modifier.weight(1f)) { form.colMy = it }
        }
    }
    item {
        AppText("Top end / طرف أعلى", style = MaterialTheme.typography.bodySmall)
        CodeChips(
            options = EndCondition.entries.toList(),
            selected = form.colTop,
            label = { it.displayName },
            onSelected = { form.colTop = it }
        )
        Spacer(Modifier.height(4.dp))
        AppText("Bottom end / طرف أسفل", style = MaterialTheme.typography.bodySmall)
        CodeChips(
            options = EndCondition.entries.toList(),
            selected = form.colBottom,
            label = { it.displayName },
            onSelected = { form.colBottom = it }
        )
    }
    item {
        CodeChips(
            options = ConnectedSlabType.entries.toList(),
            selected = form.colSlab,
            label = { it.displayName },
            onSelected = { form.colSlab = it }
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText("Column cap / تاج عمود", modifier = Modifier.weight(1f))
            Switch(checked = form.colHasCap, onCheckedChange = { form.colHasCap = it })
        }
    }
    item {
        CodeChips(
            options = LoadCombination.entries.toList(),
            selected = form.colCombo,
            label = { it.name },
            onSelected = { form.colCombo = it }
        )
    }
    item { CalculateButton(onCalculate) }
    result?.let { r ->
        item {
            ResultCard("Status / الحالة") {
                ResultRow("Overall", "PRELIMINARY - NOT FULLY CHECKED / نتيجة أولية: لم يكتمل التحقق", bold = true)
                ResultRow("Reinforcement check", if (r.reinforcementResult.isSafe) "Passes engine check / يجتاز فحص المحرك" else "Fails engine check / لا يجتاز فحص المحرك")
                ResultRow("Note", "Moment capacity is not validated for the actual bar positions / لم تُراجع قدرة العزم على مواضع الأسياخ الفعلية")
            }
        }
        item {
            ResultCard("Capacity / القدرة") {
                ResultRow("Axial capacity", "%.0f kN".format(r.axialCapacity), bold = true)
                ResultRow("Moment capacity X", "%.1f kN.m".format(r.momentCapacityX))
                ResultRow("Moment capacity Y", "%.1f kN.m".format(r.momentCapacityY))
            }
        }
        item {
            ResultCard("Slenderness / النحافة") {
                ResultRow("Effective length", "%.2f m".format(r.effectiveLength))
                ResultRow("Slenderness ratio", "%.1f".format(r.slendernessRatio), bold = true)
                ResultRow("Slender?", if (r.isSlender) "Yes / نعم" else "No / لا")
            }
        }
        item {
            ResultCard("Reinforcement / التسليح") {
                ResultRow("As required", "%.0f mm²".format(r.reinforcementResult.astRequired), bold = true)
                ResultRow("As provided", "%.0f mm²".format(r.reinforcementResult.astProvided))
                ResultRow("Bars", "${r.reinforcementResult.numberOfBars}Ø${r.reinforcementResult.barDiameter.toInt()}")
                ResultRow("Utilization", "%.2f".format(r.reinforcementResult.utilizationRatio))
            }
        }
        item { NotesCard(r.warnings, r.codeNotes) }
    }
}

// ───────────────────────────── Composite beam ─────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.compositeContent(
    form: AdvancedFormState,
    result: CompositeBeamDesignResult?,
    onCalculate: () -> Unit
) {
    item {
        SectionTitle("Composite Steel–Concrete Beam", "كمرة مركبة من الصلب والخرسانة")
        CodeChips(
            options = CompositeDesignCode.entries,
            selected = form.compositeCode,
            label = { it.code },
            onSelected = { form.compositeCode = it }
        )
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Span (m)", form.cSpan, Modifier.weight(1f)) { form.cSpan = it }
            NumField("Beam spacing (m)", form.cSpacing, Modifier.weight(1f)) { form.cSpacing = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Slab thickness (mm)", form.cSlab, Modifier.weight(1f)) { form.cSlab = it }
            NumField("Steel depth (mm)", form.cSteelDepth, Modifier.weight(1f)) { form.cSteelDepth = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Steel area (mm²)", form.cSteelArea, Modifier.weight(1f)) { form.cSteelArea = it }
            NumField("Steel inertia (mm⁴)", form.cSteelInertia, Modifier.weight(1f)) { form.cSteelInertia = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Steel fy (MPa)", form.cSteelFy, Modifier.weight(1f)) { form.cSteelFy = it }
            NumField("Concrete f'c (MPa)", form.cFcu, Modifier.weight(1f)) { form.cFcu = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Live load (kPa)", form.cLive, Modifier.weight(1f)) { form.cLive = it }
            NumField("Superimposed DL (kPa)", form.cSuperDead, Modifier.weight(1f)) { form.cSuperDead = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Flange width (mm)", form.cFlangeWidth, Modifier.weight(1f)) { form.cFlangeWidth = it }
            NumField("Partial shear ratio", form.cPartial, Modifier.weight(1f)) { form.cPartial = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Deflection limit L/x", form.cDeflection, Modifier.weight(1f)) { form.cDeflection = it }
            NumField("Provided studs (0=auto)", form.cStuds, Modifier.weight(1f)) { form.cStuds = it }
        }
    }
    item { CalculateButton(onCalculate) }

    if (result != null) {
        item {
            StatusBanner(result.flexureOk && result.shearConnectionOk && result.deflectionOk)
        }
        item {
            ResultCard("Flexural design / التصميم للانحناء") {
                ResultRow("Effective width", "${fmt(result.effectiveWidthMm, 0)} mm")
                ResultRow("Design moment", "${fmt(result.designMomentKNm)} kN·m")
                ResultRow("Flexural capacity", "${fmt(result.flexuralCapacityKNm)} kN·m")
                ResultRow("Utilization", fmt(result.momentUtilization, 3))
                ResultRow("Concrete block depth", "${fmt(result.compressionBlockDepthMm, 0)} mm")
            }
        }
        item {
            ResultCard("Shear connection / وصلة القص") {
                ResultRow("Stud capacity", "${fmt(result.shearStudCapacityKN)} kN")
                ResultRow("Required studs", "${result.requiredStuds}")
                ResultRow("Required rows", "${result.requiredStudRows}")
                ResultRow("Provided studs", "${result.providedStuds}")
            }
        }
        item {
            ResultCard("Serviceability / الحدود الخدمية") {
                ResultRow("Modular ratio", fmt(result.modularRatio, 2))
                ResultRow("Transformed inertia", "${fmt(result.transformedInertiaMm4, 0)} mm⁴")
                ResultRow("Deflection", "${fmt(result.deflectionMm)} mm")
                ResultRow("Limit", "${fmt(result.deflectionLimitMm)} mm")
            }
        }
        item { NotesCard(result.warnings, result.notes) }
    }
}

// ───────────────────────────── Fire resistance ────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.fireContent(
    form: AdvancedFormState,
    result: FireResistanceResult?,
    onCalculate: () -> Unit
) {
    item {
        SectionTitle("Fire Resistance (ISO 834)", "مقاومة الحريق وفق منحنى ISO 834")
        CodeChips(
            options = FireCode.entries,
            selected = form.fireCode,
            label = { it.label },
            onSelected = { form.fireCode = it }
        )
        Spacer(Modifier.height(6.dp))
        CodeChips(
            options = FireElementType.entries,
            selected = form.fireElement,
            label = { it.label },
            onSelected = { form.fireElement = it }
        )
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Required rating (min)", form.fireRating, Modifier.weight(1f)) { form.fireRating = it }
            NumField("Cover (mm)", form.fireCover, Modifier.weight(1f)) { form.fireCover = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Section width (mm)", form.fireWidth, Modifier.weight(1f)) { form.fireWidth = it }
            NumField("Section depth (mm)", form.fireDepth, Modifier.weight(1f)) { form.fireDepth = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Concrete f'c (MPa)", form.fireFcu, Modifier.weight(1f)) { form.fireFcu = it }
            NumField("Steel fy (MPa)", form.fireFy, Modifier.weight(1f)) { form.fireFy = it }
        }
    }
    item { NumField("Reinforcement ratio ρ", form.fireRho, Modifier.fillMaxWidth()) { form.fireRho = it } }
    item { CalculateButton(onCalculate) }

    if (result != null) {
        item { StatusBanner(result.coverOk && result.dimensionOk && result.ratingOk) }
        item {
            ResultCard("Fire performance / الأداء تحت الحريق") {
                ResultRow("Required rating", "${result.requiredRatingMinutes} min")
                ResultRow("Achieved rating", "${result.achievedRatingMinutes} min")
                ResultRow("ISO 834 temperature", "${fmt(result.iso834TemperatureC, 0)} °C")
                ResultRow("Concrete strength factor", fmt(result.concreteStrengthFactor, 3))
                ResultRow("Reduced concrete strength", "${fmt(result.reducedConcreteStrengthMPa)} MPa")
                ResultRow("Reduced steel strength", "${fmt(result.reducedSteelStrengthMPa)} MPa")
            }
        }
        item {
            ResultCard("Detailing limits / حدود التفاصيل") {
                ResultRow("Minimum cover", "${fmt(result.minimumCoverMm, 0)} mm")
                ResultRow("Actual cover", "${fmt(result.actualCoverMm, 0)} mm")
                ResultRow("Minimum dimension", "${fmt(result.minimumDimensionMm, 0)} mm")
                ResultRow("Actual minimum dimension", "${fmt(result.actualMinimumDimensionMm, 0)} mm")
            }
        }
        item { NotesCard(result.warnings, result.notes) }
    }
}

// ───────────────────────────── Deep beam ─────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.deepContent(
    form: AdvancedFormState,
    result: DeepBeamResult?,
    onCalculate: () -> Unit
) {
    item {
        SectionTitle("Deep Beam (Strut-and-Tie)", "كمرة عميقة (Strut-and-Tie)")
        CodeChips(
            options = DeepBeamCode.entries,
            selected = form.deepCode,
            label = { "${it.displayName} · ${it.version}" },
            onSelected = { form.deepCode = it }
        )
    }
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText("Continuous beam / كمرة مستمرة", modifier = Modifier.weight(1f))
            Switch(checked = form.deepContinuous, onCheckedChange = { form.deepContinuous = it })
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Span (m)", form.dSpan, Modifier.weight(1f)) { form.dSpan = it }
            NumField("Width (mm)", form.dWidth, Modifier.weight(1f)) { form.dWidth = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Depth (mm)", form.dDepth, Modifier.weight(1f)) { form.dDepth = it }
            NumField("Clear cover (mm)", form.dCover, Modifier.weight(1f)) { form.dCover = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("fcu (MPa)", form.dFcu, Modifier.weight(1f)) { form.dFcu = it }
            NumField("fy (MPa)", form.dFy, Modifier.weight(1f)) { form.dFy = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Dead load (kN/m)", form.dDead, Modifier.weight(1f)) { form.dDead = it }
            NumField("Live load (kN/m)", form.dLive, Modifier.weight(1f)) { form.dLive = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Point load (kN)", form.dPoint, Modifier.weight(1f)) { form.dPoint = it }
            NumField("Bearing plate (mm)", form.dBearing, Modifier.weight(1f)) { form.dBearing = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Tie area provided (mm²)", form.dTieArea, Modifier.weight(1f)) { form.dTieArea = it }
            NumField("Stirrup area (mm²)", form.dStirrupArea, Modifier.weight(1f)) { form.dStirrupArea = it }
        }
    }
    item { NumField("Stirrup spacing (mm)", form.dStirrupSpacing, Modifier.fillMaxWidth()) { form.dStirrupSpacing = it } }
    item { CalculateButton(onCalculate) }

    if (result != null) {
        item { StatusBanner(result.isSafe) }
        item {
            ResultCard("Analysis / التحليل") {
                ResultRow("Classification ln/h", fmt(result.spanToDepthRatio, 2))
                ResultRow("Limit", fmt(result.spanToDepthLimit, 2))
                ResultRow("Deep beam", yesNo(result.isDeepBeam))
                ResultRow("Factored moment", "${fmt(result.factoredMoment)} kN·m")
                ResultRow("Factored shear", "${fmt(result.factoredShear)} kN")
                ResultRow("Strut angle", "${fmt(result.strutAngleDeg, 1)}°")
                ResultRow("Strut force", "${fmt(result.strutForce)} kN")
                ResultRow("Strut capacity", "${fmt(result.strutCapacity)} kN")
            }
        }
        item {
            ResultCard("Reinforcement / التسليح") {
                ResultRow("Tie force", "${fmt(result.tieForce)} kN")
                ResultRow("Tie area required", "${fmt(result.tieAreaRequired, 0)} mm²")
                ResultRow("Tie area provided", "${fmt(result.tieAreaProvided, 0)} mm²")
                ResultRow("Minimum reinforcement", "${fmt(result.minimumReinforcement, 0)} mm²")
                ResultRow("Shear capacity", "${fmt(result.shearCapacity)} kN")
                ResultRow("Bearing stress", "${fmt(result.bearingStress)} MPa")
                ResultRow("Bearing capacity", "${fmt(result.bearingCapacity)} MPa")
            }
        }
        item { ChecksCard(result.checks.map { UiCheck(it.name, it.calculated, it.limit, it.unit, it.passed) }) }
        item { NotesCard(result.warnings, result.notes) }
    }
}

// ───────────────────────────── Pile group ─────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.pileContent(
    form: AdvancedFormState,
    result: PileGroupResult?,
    onCalculate: () -> Unit
) {
    item {
        SectionTitle("Pile Group (Rigid Cap)", "مجموعة خوازيق بقاعدة صلبة")
        AppText(
            "Converse–Labarre efficiency and rigid-cap load distribution",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Piles X", form.pNx, Modifier.weight(1f)) { form.pNx = it }
            NumField("Piles Y", form.pNy, Modifier.weight(1f)) { form.pNy = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Spacing X (mm)", form.pSx, Modifier.weight(1f)) { form.pSx = it }
            NumField("Spacing Y (mm)", form.pSy, Modifier.weight(1f)) { form.pSy = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Pile diameter (mm)", form.pDia, Modifier.weight(1f)) { form.pDia = it }
            NumField("Pile length (m)", form.pLength, Modifier.weight(1f)) { form.pLength = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Pile capacity (kN)", form.pCapacity, Modifier.weight(1f)) { form.pCapacity = it }
            NumField("Vertical load (kN)", form.pLoad, Modifier.weight(1f)) { form.pLoad = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Mx (kN·m)", form.pMx, Modifier.weight(1f)) { form.pMx = it }
            NumField("My (kN·m)", form.pMy, Modifier.weight(1f)) { form.pMy = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Horizontal load (kN)", form.pH, Modifier.weight(1f)) { form.pH = it }
            NumField("Pile modulus (MPa)", form.pModulus, Modifier.weight(1f)) { form.pModulus = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Column width (mm)", form.pColW, Modifier.weight(1f)) { form.pColW = it }
            NumField("Column depth (mm)", form.pColD, Modifier.weight(1f)) { form.pColD = it }
        }
    }
    item { NumField("Allowable settlement (mm)", form.pAllowSettle, Modifier.fillMaxWidth()) { form.pAllowSettle = it } }
    item { CalculateButton(onCalculate) }

    if (result != null) {
        item {
            StatusBanner(
                isSafe = result.isSafe,
                incomplete = form.pH.toDoubleOrNull()?.let { it > 0.0 } == true
            )
        }
        item {
            ResultCard("Group capacity / قدرة المجموعة") {
                ResultRow("Number of piles", "${result.numberOfPiles}")
                ResultRow("Efficiency Eg", fmt(result.efficiency, 3))
                ResultRow("Group capacity", "${fmt(result.groupCapacity)} kN")
                ResultRow("Average pile load", "${fmt(result.averagePileLoad)} kN")
                ResultRow("Maximum pile load", "${fmt(result.maxPileLoad)} kN")
                ResultRow("Minimum pile load", "${fmt(result.minPileLoad)} kN")
                ResultRow("Max utilization", fmt(result.maxPileUtilization, 3))
            }
        }
        item {
            ResultCard("Cap and settlement / القاعدة والهبوط") {
                ResultRow("Cap moment Mx", "${fmt(result.capMomentX)} kN·m")
                ResultRow("Cap moment My", "${fmt(result.capMomentY)} kN·m")
                ResultRow("Interaction factor", fmt(result.interactionFactor, 3))
                ResultRow("Group settlement factor", fmt(result.groupSettlementFactor, 3))
                ResultRow("Single pile settlement", "${fmt(result.singlePileSettlement)} mm")
                ResultRow("Group settlement", "${fmt(result.groupSettlement)} mm")
                ResultRow("Allowable settlement", "${fmt(result.allowableSettlement)} mm")
            }
        }
        item { ChecksCard(result.checks.map { UiCheck(it.name, it.calculated, it.limit, it.unit, it.passed) }) }
        item { NotesCard(result.warnings, result.notes) }
    }
}

// ───────────────────────────── Settlement ─────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.settlementContent(
    form: AdvancedFormState,
    result: SettlementResult?,
    onCalculate: () -> Unit
) {
    item {
        SectionTitle("Settlement (Elastic + Consolidation)", "الهبوط (مرن + انضغاطي)")
        AppText(
            "Immediate elastic settlement and 1-D consolidation with time rate",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Footing width B (m)", form.sWidth, Modifier.weight(1f)) { form.sWidth = it }
            NumField("Footing length L (m)", form.sLength, Modifier.weight(1f)) { form.sLength = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Pressure q (kPa)", form.sPressure, Modifier.weight(1f)) { form.sPressure = it }
            NumField("Elastic modulus Es (MPa)", form.sModulus, Modifier.weight(1f)) { form.sModulus = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Poisson ν", form.sPoisson, Modifier.weight(1f)) { form.sPoisson = it }
            NumField("Influence factor Is", form.sInfluence, Modifier.weight(1f)) { form.sInfluence = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Layer thickness H (m)", form.sLayer, Modifier.weight(1f)) { form.sLayer = it }
            NumField("Compression index Cc", form.sCc, Modifier.weight(1f)) { form.sCc = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Recompression Cr (0=0.1Cc)", form.sCr, Modifier.weight(1f)) { form.sCr = it }
            NumField("Initial void ratio e0", form.sE0, Modifier.weight(1f)) { form.sE0 = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("σ'0 (kPa)", form.sSigma0, Modifier.weight(1f)) { form.sSigma0 = it }
            NumField("σ'p (kPa, 0=none)", form.sSigmaP, Modifier.weight(1f)) { form.sSigmaP = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("cv (m²/year)", form.sCv, Modifier.weight(1f)) { form.sCv = it }
            NumField("Drainage path Hd (m)", form.sDrainage, Modifier.weight(1f)) { form.sDrainage = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Time (years)", form.sTime, Modifier.weight(1f)) { form.sTime = it }
            NumField("Allowable settlement (mm)", form.sAllowSettle, Modifier.weight(1f)) { form.sAllowSettle = it }
        }
    }
    item { CalculateButton(onCalculate) }

    if (result != null) {
        item { StatusBanner(result.isSafe) }
        item {
            ResultCard("Settlement / الهبوط") {
                ResultRow("Immediate settlement", "${fmt(result.immediateSettlement)} mm")
                ResultRow("Consolidation settlement", "${fmt(result.consolidationSettlement)} mm")
                ResultRow("Total settlement", "${fmt(result.totalSettlement)} mm")
                ResultRow("Allowable settlement", "${fmt(result.allowableSettlement)} mm")
                ResultRow("Stress increase", "${fmt(result.stressIncrease)} kPa")
                ResultRow("Consolidation applicable", yesNo(result.isConsolidationApplicable))
            }
        }
        item {
            ResultCard("Time rate / معدل الهبوط الزمني") {
                ResultRow("Degree of consolidation U", "${fmt(result.degreeOfConsolidation * 100.0, 1)} %")
                ResultRow("Settlement at time t", "${fmt(result.settlementAtTime)} mm")
                ResultRow("Time for 90% consolidation", "${fmt(result.timeFor90Consolidation)} years")
            }
        }
        item { ChecksCard(result.checks.map { UiCheck(it.name, it.calculated, it.limit, it.unit, it.passed) }) }
        item { NotesCard(result.warnings, result.notes) }
    }
}

// ───────────────────────────── Shared UI ─────────────────────────────────

// ───────────────────────────── Advanced slab (ECP/SBC/ACI) ─────────────────────────────

private enum class SlabKind(val title: String, val titleAr: String) {
    SOLID("Solid", "مصمتة"),
    ONE_WAY("One-Way", "أحادية الاتجاه"),
    TWO_WAY("Two-Way", "ثنائية الاتجاه"),
    HORDI("Hordi", "هوردي"),
    WAFFLE("Waffle", "وافل"),
    POST_TENSIONED("Post-Tensioned", "لاحقة الإجهاد"),
    FLAT_PLATE("Flat Plate", "لاكمرية"),
    FLAT_SLAB("Flat Slab", "فلات سلاب بإسقاطات"),
    PRECAST("Precast", "سابقة الصب")
}

private fun androidx.compose.foundation.lazy.LazyListScope.slabContent(
    form: AdvancedFormState,
    result: AdvancedSlabResult?,
    onCalculate: () -> Unit
) {
    item {
        SectionTitle("Advanced Slab Design", "تصميم بلاطة متقدم")
        Spacer(Modifier.height(8.dp))
        CodeChips(
            options = DesignCode.entries,
            selected = form.slabCode,
            label = { it.name },
            onSelected = { form.slabCode = it }
        )
        Spacer(Modifier.height(8.dp))
        CodeChips(
            options = SlabKind.entries,
            selected = form.slabKind,
            label = { "${it.title} / ${it.titleAr}" },
            onSelected = { form.slabKind = it }
        )
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("fcu (MPa)", form.slFcu, Modifier.weight(1f)) { form.slFcu = it }
            NumField("fy (MPa)", form.slFy, Modifier.weight(1f)) { form.slFy = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Dead (kN/m²)", form.slDead, Modifier.weight(1f)) { form.slDead = it }
            NumField("Live (kN/m²)", form.slLive, Modifier.weight(1f)) { form.slLive = it }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Thickness (mm)", form.slThickness, Modifier.weight(1f)) { form.slThickness = it }
            when (form.slabKind) {
                SlabKind.ONE_WAY, SlabKind.PRECAST ->
                    NumField("Width (m)", form.slWidth, Modifier.weight(1f)) { form.slWidth = it }
                else ->
                    NumField(
                        if (form.slabKind == SlabKind.FLAT_PLATE || form.slabKind == SlabKind.FLAT_SLAB) "Panel length (m)" else "Short span (m)",
                        form.slSpan1, Modifier.weight(1f)
                    ) { form.slSpan1 = it }
            }
        }
    }
    if (form.slabKind != SlabKind.ONE_WAY && form.slabKind != SlabKind.PRECAST) {
        item {
            NumField(
                if (form.slabKind == SlabKind.FLAT_PLATE || form.slabKind == SlabKind.FLAT_SLAB) "Panel width (m)" else "Long span (m)",
                form.slSpan2, Modifier.fillMaxWidth()
            ) { form.slSpan2 = it }
        }
    }
    if (form.slabKind == SlabKind.PRECAST) {
        item {
            NumField("Length (m)", form.slSpan1, Modifier.fillMaxWidth()) { form.slSpan1 = it }
        }
    }
    when (form.slabKind) {
        SlabKind.HORDI -> item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Rib width (mm)", form.slRibWidth, Modifier.weight(1f)) { form.slRibWidth = it }
                NumField("Rib spacing (mm)", form.slRibSpacing, Modifier.weight(1f)) { form.slRibSpacing = it }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Block width (mm)", form.slBlockWidth, Modifier.weight(1f)) { form.slBlockWidth = it }
                NumField("Block height (mm)", form.slBlockHeight, Modifier.weight(1f)) { form.slBlockHeight = it }
            }
        }
        SlabKind.WAFFLE -> item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Rib width (mm)", form.slRibWidth, Modifier.weight(1f)) { form.slRibWidth = it }
                NumField("Rib spacing (mm)", form.slRibSpacing, Modifier.weight(1f)) { form.slRibSpacing = it }
                NumField("Rib depth (mm)", form.slRibDepth, Modifier.weight(1f)) { form.slRibDepth = it }
            }
        }
        SlabKind.TWO_WAY -> item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText("Drop panel / إسقاط", modifier = Modifier.weight(1f))
                Switch(checked = form.slDropPanel, onCheckedChange = { form.slDropPanel = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText("Column capital / تاج عمود", modifier = Modifier.weight(1f))
                Switch(checked = form.slColCapital, onCheckedChange = { form.slColCapital = it })
            }
        }
        SlabKind.FLAT_PLATE -> item {
            NumField("Column size (mm)", form.slColumnSize, Modifier.fillMaxWidth()) { form.slColumnSize = it }
        }
        SlabKind.FLAT_SLAB -> item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Drop thickness (mm)", form.slDropThickness, Modifier.weight(1f)) { form.slDropThickness = it }
                NumField("Drop size (m)", form.slDropSize, Modifier.weight(1f)) { form.slDropSize = it }
                NumField("Column (mm)", form.slColumnSize, Modifier.weight(1f)) { form.slColumnSize = it }
            }
        }
        SlabKind.POST_TENSIONED -> item {
            CodeChips(
                options = TendonType.entries.toList(),
                selected = form.slTendon,
                label = { it.displayName },
                onSelected = { form.slTendon = it }
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Prestress (kN)", form.slPrestressForce, Modifier.weight(1f)) { form.slPrestressForce = it }
                NumField("Eccentricity (mm)", form.slEccentricity, Modifier.weight(1f)) { form.slEccentricity = it }
            }
        }
        SlabKind.PRECAST -> item {
            CodeChips(
                options = PrestressType.entries.toList(),
                selected = form.slPrestressType,
                label = { it.displayName },
                onSelected = { form.slPrestressType = it }
            )
            Spacer(Modifier.height(8.dp))
            NumField("Topping (mm)", form.slTopping, Modifier.fillMaxWidth()) { form.slTopping = it }
        }
        else -> {}
    }
    if (form.slabKind == SlabKind.SOLID || form.slabKind == SlabKind.TWO_WAY ||
        form.slabKind == SlabKind.HORDI || form.slabKind == SlabKind.WAFFLE ||
        form.slabKind == SlabKind.POST_TENSIONED
    ) {
        item {
            AppText("Edges (all four) / الأطراف", style = MaterialTheme.typography.bodySmall)
            CodeChips(
                options = EdgeCondition.entries.toList(),
                selected = form.slEdge,
                label = { it.name },
                onSelected = { form.slEdge = it }
            )
        }
    }
    item {
        CodeChips(
            options = LoadCombination.entries.toList(),
            selected = form.slabCombo,
            label = { it.name },
            onSelected = { form.slabCombo = it }
        )
    }
    item { CalculateButton(onCalculate) }
    result?.let { r ->
        item { StatusBanner(isSafe = r.flexureResult.isSafe) }
        item {
            ResultCard("Flexure / الانحناء") {
                ResultRow("As required", "%.0f mm²/m".format(r.flexureResult.requiredReinforcement), bold = true)
                ResultRow("As provided", "%.0f mm²/m".format(r.flexureResult.providedReinforcement))
                ResultRow("Bars", "Ø${r.flexureResult.barDiameter.toInt()} @ ${r.flexureResult.barSpacing.toInt()} mm")
                ResultRow("Min thickness", "%.0f mm".format(r.flexureResult.minThickness))
                ResultRow("Utilization", "%.2f".format(r.flexureResult.utilizationRatio))
            }
        }
        item {
            ResultCard("Shear / القص") {
                ResultRow("Applied", "%.1f kN".format(r.shearCheck.appliedShear))
                ResultRow("Capacity", "%.1f kN".format(r.shearCheck.shearCapacity), bold = true)
                ResultRow("Utilization", "%.2f".format(r.shearCheck.utilizationRatio))
                ResultRow("Check passed?", if (r.shearCheck.isSafe) "Yes / نعم" else "No / لا")
            }
        }
        item {
            ResultCard("Deflection / الترخيم") {
                ResultRow("Calculated", "%.2f mm".format(r.deflectionCheck.calculatedDeflection))
                ResultRow("Allowable", "%.2f mm".format(r.deflectionCheck.allowableDeflection))
                ResultRow("Ratio", "%.2f".format(r.deflectionCheck.ratio), bold = true)
                ResultRow("Check passed?", if (r.deflectionCheck.isSafe) "Yes / نعم" else "No / لا")
            }
        }
        r.punchingShearCheck?.let { p ->
            item {
                ResultCard("Punching shear / القص الاختراقي") {
                    ResultRow("Applied", "%.1f kN".format(p.appliedShear))
                    ResultRow("Capacity", "%.1f kN".format(p.shearCapacity), bold = true)
                    ResultRow("Utilization", "%.2f".format(p.utilizationRatio))
                    ResultRow("Check passed?", if (p.isSafe) "Yes / نعم" else "No / لا")
                }
            }
        }
        item {
            ResultCard("Quantities / الكميات") {
                ResultRow("Concrete", "%.2f m³".format(r.concreteVolume))
                ResultRow("Formwork", "%.1f m²".format(r.formworkArea))
            }
        }
        item { NotesCard(r.warnings, r.codeNotes) }
    }
}

@Composable
private fun SectionTitle(english: String, arabic: String) {
    Column {
        AppText(english, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        AppText(
            arabic,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun <T> CodeChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelected(option) },
                label = { AppText(label(option)) }
            )
        }
    }
}

@Composable
private fun NumField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { AppText(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

@Composable
private fun CalculateButton(onClick: () -> Unit) {
    AppButton(
        label = "Calculate / احسب",
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        fontWeight = FontWeight.Bold,
        icon = Icons.Default.Calculate
    )
}

@Composable
private fun StatusBanner(isSafe: Boolean, incomplete: Boolean = false) {
    val color = when {
        incomplete -> Color(0xFFEF6C00)
        isSafe -> Color(0xFFEF6C00) // passing checks are never shown green: the assessment is not complete
        else -> Color(0xFFC62828)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = color
            )
            AppText(
                when {
                    incomplete -> "INCOMPLETE / غير مكتمل"
                    isSafe -> "PRELIMINARY: the checks run passed; assessment not complete / أولي: الفحوص المنفذة اجتازت والتقييم غير مكتمل"
                    else -> "A check did not pass / أحد الفحوص لم يجتز"
                },
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ResultCard(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppText(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AppText(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        AppText(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun ChecksCard(checks: List<UiCheck>) {
    if (checks.isEmpty()) return
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppText("Design checks / فحوصات التصميم", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            checks.forEach { check ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (check.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (check.passed) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                    Spacer(Modifier.width(8.dp))
                    AppText(
                        "${check.name}: ${fmt(check.calculated)} / ${fmt(check.limit)} ${check.unit}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesCard(warnings: List<String>, notes: List<String>) {
    if (warnings.isEmpty() && notes.isEmpty()) return
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (warnings.isNotEmpty()) {
                AppText("Warnings / تحذيرات", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                warnings.forEach { AppText("• $it", style = MaterialTheme.typography.bodySmall) }
                HorizontalDivider()
            }
            if (notes.isNotEmpty()) {
                AppText("Notes / ملاحظات", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                notes.forEach { AppText("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private fun fmt(value: Double, decimals: Int = 2): String =
    if (value.isFinite()) "%.${decimals}f".format(value) else "—"

private fun yesNo(value: Boolean): String = if (value) "Yes / نعم" else "No / لا"

/** Western digits and a dot decimal separator, from Arabic-Indic digits and Arabic/comma separators. */
internal fun normalizeNumericText(raw: String): String {
    val sb = StringBuilder()
    for (c in raw.trim()) {
        val piece: String = when (c) {
            in '\u0660'..'\u0669' -> ('0' + (c - '\u0660')).toString()
            in '\u06F0'..'\u06F9' -> ('0' + (c - '\u06F0')).toString()
            '\u066B' -> "."
            '\u066C' -> ""
            else -> c.toString()
        }
        sb.append(piece)
    }
    val t = sb.toString()
    // A single comma with no dot: "2,5" is a decimal comma. "1,250" is ambiguous (1.25 or 1250), so it is
    // left unchanged and fails to parse instead of silently guessing. With a dot, commas are thousands separators.
    if (t.contains('.')) {
        // Dot decimal with comma thousands only: every comma group must be exactly 3 digits and precede the dot.
        // "1.250,5" (European) or "1,25.0" is ambiguous or malformed, so it is left unchanged and fails to parse.
        val intPart = t.substringBefore('.')
        val ok = !t.substringAfter('.').contains(',') && intPart.split(',').drop(1).all { it.length == 3 }
        return if (ok) t.replace(",", "") else t
    }
    if (t.count { it == ',' } == 1) {
        val after = t.substringAfter(',')
        return if (after.length == 3) t else t.replace(',', '.')
    }
    return t
}

internal fun String.num(name: String): Double {
    val n = normalizeNumericText(this)
    // Kotlin/Java also accept "2.5f", "1d", "1e5" and hex floats; none of those are valid entries here.
    if (!Regex("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)").matches(n)) throw IllegalArgumentException("Invalid number for $name")
    val v = n.toDoubleOrNull()
        ?: throw IllegalArgumentException("Invalid number for $name")
    // "NaN" and "Infinity" parse as doubles but are never valid inputs.
    if (!v.isFinite()) throw IllegalArgumentException("Invalid number for $name")
    return v
}

internal fun String.int(name: String): Int =
    normalizeNumericText(this).toIntOrNull() ?: throw IllegalArgumentException("Invalid integer for $name")

internal fun String.intOrNull(name: String): Int? {
    val text = normalizeNumericText(this)
    if (text.isEmpty()) return null
    val value = text.toIntOrNull() ?: throw IllegalArgumentException("Invalid integer for $name")
    // Blank or 0 means "not provided". A negative count is a mistake and must not be ignored silently.
    if (value < 0) throw IllegalArgumentException("$name must not be negative")
    return if (value == 0) null else value
}
