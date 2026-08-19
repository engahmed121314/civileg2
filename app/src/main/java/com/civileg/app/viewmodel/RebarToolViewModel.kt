package com.civileg.app.viewmodel

import androidx.lifecycle.ViewModel
import com.civileg.app.utils.RebarCalculator
import com.civileg.app.utils.RebarCalculator.WeightResult
import com.civileg.app.utils.RebarCalculator.DevelopmentLengthResult
import com.civileg.app.utils.RebarCalculator.LapSpliceResult
import com.civileg.app.utils.RebarCalculator.RebarScheduleResult
import com.civileg.app.utils.RebarCalculator.CrackWidthResult
import com.civileg.app.utils.RebarCalculator.DesignCode
import com.civileg.app.utils.RebarCalculator.LapType
import com.civileg.app.utils.RebarCalculator.BarProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the Rebar Tool module.
 * Manages state for weight, development length, lap splice,
 * rebar schedule, and crack width calculations.
 */
class RebarToolViewModel : ViewModel() {

    // ── Tab state ──
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun selectTab(index: Int) { _selectedTab.value = index }

    // ══════════════════════════════════════════════════════════════
    //  WEIGHT CALCULATOR
    // ══════════════════════════════════════════════════════════════
    private val _weightResult = MutableStateFlow<WeightResult?>(null)
    val weightResult: StateFlow<WeightResult?> = _weightResult.asStateFlow()

    fun calculateWeight(diameter: Double, length: Double, quantity: Int) {
        val result = RebarCalculator.totalWeight(diameter, length, quantity)
        _weightResult.value = result
    }

    // ══════════════════════════════════════════════════════════════
    //  DEVELOPMENT LENGTH
    // ══════════════════════════════════════════════════════════════
    private val _devLengthResult = MutableStateFlow<DevelopmentLengthResult?>(null)
    val devLengthResult: StateFlow<DevelopmentLengthResult?> = _devLengthResult.asStateFlow()

    fun calculateDevelopmentLength(
        diameter: Double,
        fy: Double,
        fcu: Double,
        isTopBar: Boolean,
        isConfined: Boolean,
        excessRatio: Double,
        code: DesignCode
    ) {
        val result = when (code) {
            DesignCode.ECP_203 -> RebarCalculator.developmentLengthECP(
                diameter, fy, fcu, isTopBar, isConfined, excessRatio
            )
            DesignCode.ACI_318 -> {
                val fyPsi = fy * 145.038
                val fcPsi = fcu * 145.038
                RebarCalculator.developmentLengthACI(
                    diameter, fyPsi, fcPsi, isTopBar, isConfined, excessRatio
                )
            }
        }
        _devLengthResult.value = result
    }

    // ══════════════════════════════════════════════════════════════
    //  LAP SPLICE
    // ══════════════════════════════════════════════════════════════
    private val _lapResult = MutableStateFlow<LapSpliceResult?>(null)
    val lapResult: StateFlow<LapSpliceResult?> = _lapResult.asStateFlow()

    fun calculateLapSplice(
        diameter: Double,
        fy: Double,
        fcu: Double,
        lapType: LapType,
        code: DesignCode,
        isTopBar: Boolean,
        isConfined: Boolean,
        spliceClass: String
    ) {
        val result = RebarCalculator.lapSpliceLength(
            diameter, fy, fcu, lapType, code, isTopBar, isConfined, spliceClass
        )
        _lapResult.value = result
    }

    // ══════════════════════════════════════════════════════════════
    //  REBAR SCHEDULE
    // ══════════════════════════════════════════════════════════════
    private val _scheduleResult = MutableStateFlow<RebarScheduleResult?>(null)
    val scheduleResult: StateFlow<RebarScheduleResult?> = _scheduleResult.asStateFlow()

    fun generateBeamSchedule(
        width: Double, depth: Double, length: Double, spans: Int,
        topDia: Int, topCount: Int, botDia: Int, botCount: Int,
        stirrupDia: Int, stirrupSpacing: Double, cover: Double
    ) {
        _scheduleResult.value = RebarCalculator.generateBeamSchedule(
            width, depth, length, spans, topDia, topCount, botDia, botCount,
            stirrupDia, stirrupSpacing, cover
        )
    }

    fun generateSlabSchedule(
        slabL: Double, slabW: Double, thickness: Double,
        botShortDia: Int, botShortSp: Double,
        botLongDia: Int, botLongSp: Double,
        topShortDia: Int, topShortSp: Double, cover: Double
    ) {
        _scheduleResult.value = RebarCalculator.generateSlabSchedule(
            slabL, slabW, thickness, botShortDia, botShortSp,
            botLongDia, botLongSp, topShortDia, topShortSp, cover
        )
    }

    fun generateColumnSchedule(
        colW: Double, colD: Double, height: Double,
        mainDia: Int, mainCount: Int,
        tieDia: Int, tieSp: Double, cover: Double
    ) {
        _scheduleResult.value = RebarCalculator.generateColumnSchedule(
            colW, colD, height, mainDia, mainCount, tieDia, tieSp, cover
        )
    }

    // ══════════════════════════════════════════════════════════════
    //  CRACK WIDTH
    // ══════════════════════════════════════════════════════════════
    private val _crackResult = MutableStateFlow<CrackWidthResult?>(null)
    val crackResult: StateFlow<CrackWidthResult?> = _crackResult.asStateFlow()

    fun calculateCrackWidth(
        steelStress: Double,
        barDiameter: Double,
        barSpacing: Double,
        coverToBarCenter: Double,
        limitingWidth: Double
    ) {
        _crackResult.value = RebarCalculator.crackWidth(
            steelStress, barDiameter, barSpacing, coverToBarCenter,
            limitingWidth = limitingWidth
        )
    }

    // ── Bar table ──
    fun getBarTable(): List<BarProperties> = RebarCalculator.getAllBarProperties()

    fun clearResults() {
        _weightResult.value = null
        _devLengthResult.value = null
        _lapResult.value = null
        _scheduleResult.value = null
        _crackResult.value = null
    }
}
