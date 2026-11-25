package com.civileg.app.ui.compose.components.drawings

import com.civileg.app.domain.entities.GenericSafetyCheck
import com.civileg.app.domain.calculations.utils.CalculatorEngine

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
        passed = isSafe,
        notAssessed = notAssessed
    )

private fun List<CalculatorEngine.DesignSafetyCheck>.asDrawingChecks(): List<GenericSafetyCheck> =
    map { it.asDrawingCheck() }

fun beamDrawingSummary(result: CalculatorEngine.BeamResult): DrawingResultSummary =
    DrawingResultSummary(
        isSafe = result.isSafe && result.codeAcceptanceComplete,
        utilizationRatio = result.utilizationRatio,
        concreteVolume = result.concreteVolume,
        steelWeight = result.steelWeight,
        safetyChecks = result.safetyChecks.asDrawingChecks(),
        codeNotes = result.codeNotes + result.warnings,
        designCodeLabel = result.shearCodeReview?.basis ?: "Unverified edition"
    )

fun columnDrawingSummary(result: CalculatorEngine.ColumnResult): DrawingResultSummary =
    DrawingResultSummary(
        isSafe = result.isSafe,
        codeAcceptanceComplete = result.assessmentComplete,
        utilizationRatio = result.utilizationRatio,
        concreteVolume = result.concreteVolume,
        steelWeight = result.steelWeight,
        safetyChecks = result.safetyChecks.asDrawingChecks(),
        codeNotes = (if (!result.axialAssessed)
            listOf("AXIAL CAPACITY NOT ASSESSED / لم تُقيَّم القدرة المحورية: eccentricity outside ECP 203-2020 Eq. 4-12-a domain / اللامركزية خارج نطاق المعادلة 4-12-أ")
            else emptyList()) + (if (result.isSafe && !result.assessmentComplete)
            listOf("PRELIMINARY - NOT FULLY CHECKED / نتيجة أولية لم يكتمل التحقق منها: moment capacity not validated for the actual bar positions / لم تُراجع قدرة العزم على مواضع الأسياخ الفعلية")
            else emptyList()) + result.codeNotes,
        designCodeLabel = result.code.version + (if (!result.axialAssessed) " (axial not assessed)" else if (result.isSafe && !result.assessmentComplete) " (preliminary)" else ""),
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
        isSafe = if(result.type == CalculatorEngine.SlabType.HOLLOW_BLOCK) result.engineeringAcceptance else result.isSafe,
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

fun stairDrawingSummary(result: CalculatorEngine.StairResult): DrawingResultSummary =
    DrawingResultSummary(
        isSafe = result.isSafe,
        codeAcceptanceComplete = result.codeAcceptanceComplete,
        codeNotes = result.qualificationNotes,
        utilizationRatio = result.utilizationRatio,
        concreteVolume = result.concreteVolume,
        steelWeight = result.steelWeight,
        safetyChecks = result.safetyChecks.asDrawingChecks(),
        designCodeLabel = result.code.version
    )

fun tankDrawingSummary(result: CalculatorEngine.TankResult): DrawingResultSummary =
    DrawingResultSummary(
        isSafe = result.isSafe,
        utilizationRatio = result.utilizationRatio,
        concreteVolume = result.concreteVolume,
        steelWeight = result.steelWeight,
        safetyChecks = result.safetyChecks.asDrawingChecks(),
        designCodeLabel = result.code.version
    )

fun retainingWallDrawingSummary(result: CalculatorEngine.RetainingWallResult): DrawingResultSummary =
    DrawingResultSummary(
        isSafe = result.isSafe,
        utilizationRatio = result.utilizationRatio,
        concreteVolume = result.concreteVolume,
        steelWeight = result.steelWeight,
        safetyChecks = result.safetyChecks.asDrawingChecks(),
        designCodeLabel = result.code.version
    )

fun pileDrawingSummary(result: com.civileg.app.domain.PileDesignResult): DrawingResultSummary =
    DrawingResultSummary(
        isSafe = result.isSafe,
        utilizationRatio = result.utilizationRatio,
        safetyChecks = emptyList(),
        codeNotes = result.warnings + result.codeNotes,
        designCodeLabel = ""
    )

fun strapFootingDrawingSummary(result: CalculatorEngine.StrapFootingResult): DrawingResultSummary =
    DrawingResultSummary(
        isSafe = result.isSafe,
        utilizationRatio = result.utilizationRatio,
        concreteVolume = result.concreteVolume,
        steelWeight = result.steelWeight,
        safetyChecks = result.safetyChecks.asDrawingChecks(),
        designCodeLabel = result.footing1.code.version
    )

fun waffleSlabDrawingSummary(
    result: com.civileg.app.domain.calculations.base.WaffleSlabDesign.WaffleSlabResult
): DrawingResultSummary =
    DrawingResultSummary(
        isSafe = result.isSafe,
        utilizationRatio = result.utilizationRatio,
        concreteVolume = result.concreteVolume,
        steelWeight = result.steelWeight,
        safetyChecks = result.safetyChecks.map {
            GenericSafetyCheck(it.name, it.calculated, it.limit, it.unit, it.passed)
        },
        codeNotes = result.warnings + result.codeNotes
    )

fun hordiSlabDrawingSummary(
    result: com.civileg.app.domain.entities.SlabDesignResult
): DrawingResultSummary =
    DrawingResultSummary(
        isSafe = result.isSafe,
        utilizationRatio = result.utilizationRatio,
        codeNotes = result.warnings + result.codeNotes
    )
