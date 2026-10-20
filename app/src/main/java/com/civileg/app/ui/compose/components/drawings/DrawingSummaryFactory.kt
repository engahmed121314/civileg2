package com.civileg.app.ui.compose.components.drawings

import com.civileg.app.domain.entities.GenericSafetyCheck
import com.civileg.app.utils.CalculatorEngine

/**
 * Maps engine-level safety checks into the drawing-level [GenericSafetyCheck]
 * model so a drawing sheet can display the same checks the results screen shows.
 */
fun CalculatorEngine.DesignSafetyCheck.asDrawingCheck(): GenericSafetyCheck =
    GenericSafetyCheck(
        name = name,
        calculated = value,
        limit = limit,
        unit = unit,
        passed = isSafe
    )

private fun List<CalculatorEngine.DesignSafetyCheck>.asDrawingChecks(): List<GenericSafetyCheck> =
    map { it.asDrawingCheck() }

fun beamDrawingSummary(result: CalculatorEngine.BeamResult): DrawingResultSummary =
    DrawingResultSummary(
        isSafe = result.isSafe,
        utilizationRatio = result.utilizationRatio,
        concreteVolume = result.concreteVolume,
        steelWeight = result.steelWeight,
        safetyChecks = result.safetyChecks.asDrawingChecks(),
        codeNotes = result.codeNotes,
        designCodeLabel = result.code.version
    )

fun columnDrawingSummary(result: CalculatorEngine.ColumnResult): DrawingResultSummary =
    DrawingResultSummary(
        isSafe = result.isSafe,
        utilizationRatio = result.utilizationRatio,
        concreteVolume = result.concreteVolume,
        steelWeight = result.steelWeight,
        safetyChecks = result.safetyChecks.asDrawingChecks(),
        codeNotes = result.codeNotes,
        designCodeLabel = result.code.version,
        axialLoadRatio = if (result.axialCapacity > 0.0) result.appliedAxial / result.axialCapacity else 0.0
    )

fun footingDrawingSummary(result: CalculatorEngine.FootingResult): DrawingResultSummary =
    DrawingResultSummary(
        isSafe = result.isSafe,
        utilizationRatio = result.utilizationRatio,
        concreteVolume = result.concreteVolume,
        steelWeight = result.steelWeight,
        safetyChecks = result.safetyChecks.asDrawingChecks(),
        designCodeLabel = result.code.version
    )

fun slabDrawingSummary(result: CalculatorEngine.SlabResult): DrawingResultSummary =
    DrawingResultSummary(
        isSafe = result.isSafe,
        utilizationRatio = result.utilizationRatio,
        concreteVolume = result.concreteVolume,
        steelWeight = result.steelWeight,
        safetyChecks = result.safetyChecks.asDrawingChecks(),
        designCodeLabel = result.code.version
    )

fun flatSlabDrawingSummary(result: com.civileg.app.domain.FlatSlabResult): DrawingResultSummary =
    DrawingResultSummary(
        isSafe = result.isSafe,
        utilizationRatio = result.utilizationRatio,
        concreteVolume = result.concreteVolumePerPanel,
        steelWeight = result.steelWeightPerPanel,
        safetyChecks = result.safetyChecks.map {
            GenericSafetyCheck(
                name = it.name,
                calculated = it.calculated,
                limit = it.limit,
                unit = it.unit,
                passed = it.passed
            )
        },
        codeNotes = result.codeNotes,
        punchingShearRatio = if (result.punchingShearVc > 0.0) result.punchingShearVu / result.punchingShearVc else 0.0,
        deflectionRatio = if (result.allowableDeflection > 0.0) result.deflection / result.allowableDeflection else 0.0
    )
