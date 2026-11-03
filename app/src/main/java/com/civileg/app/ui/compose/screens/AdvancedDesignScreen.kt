package com.civileg.app.ui.compose.screens

import com.civileg.app.ui.responsive.dimens

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
import com.civileg.app.domain.calculations.compositebeam.CompositeDesignCode
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

/** حالة إدخال واحدة لكل الوحدات حتى لا تُفقد القيم عند تبديل التبويبات. */
private class AdvancedFormState {
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
                title = { Text(stringResource(R.string.home_advanced_design), fontWeight = FontWeight.Bold) },
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
                            Text(
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
            label = { it.name },
            onSelected = { form.deepCode = it }
        )
    }
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Continuous beam / كمرة مستمرة", modifier = Modifier.weight(1f))
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
        Text(
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
        Text(
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

@Composable
private fun SectionTitle(english: String, arabic: String) {
    Column {
        Text(english, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
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
                label = { Text(label(option)) }
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
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

@Composable
private fun CalculateButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Icon(Icons.Default.Calculate, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Calculate / احسب", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusBanner(isSafe: Boolean, incomplete: Boolean = false) {
    val color = when {
        incomplete -> Color(0xFFEF6C00)
        isSafe -> Color(0xFF2E7D32)
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
                if (isSafe && !incomplete) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = color
            )
            Text(
                when {
                    incomplete -> "INCOMPLETE / غير مكتمل"
                    isSafe -> "SAFE / آمن"
                    else -> "UNSAFE / غير آمن"
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
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
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
            Text("Design checks / فحوصات التصميم", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            checks.forEach { check ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (check.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (check.passed) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
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
                Text("Warnings / تحذيرات", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                HorizontalDivider()
            }
            if (notes.isNotEmpty()) {
                Text("Notes / ملاحظات", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private fun fmt(value: Double, decimals: Int = 2): String =
    if (value.isFinite()) "%.${decimals}f".format(value) else "—"

private fun yesNo(value: Boolean): String = if (value) "Yes / نعم" else "No / لا"

private fun String.num(name: String): Double =
    trim().toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number for $name")

private fun String.int(name: String): Int =
    trim().toIntOrNull() ?: throw IllegalArgumentException("Invalid integer for $name")

private fun String.intOrNull(name: String): Int? {
    val text = trim()
    if (text.isEmpty()) return null
    val value = text.toIntOrNull() ?: throw IllegalArgumentException("Invalid integer for $name")
    return if (value <= 0) null else value
}
