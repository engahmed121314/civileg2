package com.civileg.app.utils

import com.civileg.app.domain.FlatSlabResult
import com.civileg.app.utils.CalculatorEngine.*
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.BoqRow
import kotlin.math.sqrt

/**
 * Builds Bill of Quantities rows from design results.
 *
 * Concrete and reinforcement come straight from the design result. Formwork
 * areas are derived from the element geometry and are intentionally kept as a
 * separate row so the estimator stays usable even when a result does not carry
 * a dedicated quantity.
 */
object BoqEstimator {

    private fun item(no: Int, description: String, unit: String, quantity: Double): BoqRow =
        BoqRow(no.toString(), description, unit, quantity.coerceAtLeast(0.0))

    fun beamRows(result: BeamResult, mark: String = "Beam"): List<BoqRow> {
        val formwork = result.span * (2 * result.depth + result.width) / 1000.0
        return listOf(
            item(1, "Concrete - $mark", "m3", result.concreteVolume),
            item(2, "Reinforcement steel - $mark", "kg", result.steelWeight),
            item(3, "Formwork - $mark (sides + soffit)", "m2", formwork)
        )
    }

    fun columnRows(result: ColumnResult, heightM: Double, mark: String = "Column"): List<BoqRow> {
        val formwork = 2 * (result.width + result.depth) * heightM / 1000.0
        return listOf(
            item(1, "Concrete - $mark", "m3", result.concreteVolume),
            item(2, "Reinforcement steel - $mark", "kg", result.steelWeight),
            item(3, "Formwork - $mark (four faces)", "m2", formwork)
        )
    }

    fun footingRows(result: FootingResult, mark: String = "Footing"): List<BoqRow> {
        val formwork = 2 * (result.width + result.length) * result.thickness / 1_000_000.0
        return listOf(
            item(1, "Concrete - $mark", "m3", result.concreteVolume),
            item(2, "Reinforcement steel - $mark", "kg", result.steelWeight),
            item(3, "Formwork - $mark (perimeter)", "m2", formwork)
        )
    }

    fun slabRows(
        result: SlabResult,
        lxM: Double,
        lyM: Double,
        mark: String = "Slab"
    ): List<BoqRow> = listOf(
        item(1, "Concrete - $mark", "m3", result.concreteVolume),
        item(2, "Reinforcement steel - $mark", "kg", result.steelWeight),
        item(3, "Formwork - $mark (soffit)", "m2", lxM * lyM)
    )

    fun stairRows(
        result: StairResult,
        widthM: Double,
        mark: String = "Stair"
    ): List<BoqRow> {
        val runM = result.span
        val risers = if (result.tread > 0.0) (runM * 1000.0 / result.tread).toInt().coerceAtLeast(1) else 0
        val riseM = risers * result.riser / 1000.0
        val inclinedLengthM = sqrt(runM * runM + riseM * riseM)
        return listOf(
            item(1, "Concrete - $mark", "m3", result.concreteVolume),
            item(2, "Reinforcement steel - $mark", "kg", result.steelWeight),
            item(3, "Formwork - $mark (inclined soffit)", "m2", inclinedLengthM * widthM)
        )
    }

    fun tankRows(result: TankResult, mark: String = "Tank"): List<BoqRow> = listOf(
        item(1, "Concrete - $mark", "m3", result.concreteVolume),
        item(2, "Reinforcement steel - $mark", "kg", result.steelWeight),
        item(3, "Formwork - $mark (wall faces)", "m2", 2 * (result.length + result.width) * result.height),
        item(4, "Formwork - $mark (base soffit)", "m2", result.length * result.width)
    )

    fun retainingWallRows(
        result: RetainingWallResult,
        wallLengthM: Double,
        mark: String = "Retaining wall"
    ): List<BoqRow> = listOf(
        item(1, "Concrete - $mark", "m3", result.concreteVolume * wallLengthM),
        item(2, "Reinforcement steel - $mark", "kg", result.steelWeight * wallLengthM),
        item(3, "Formwork - $mark (both faces)", "m2", 2 * result.height * wallLengthM),
        item(4, "Formwork - $mark (base soffit)", "m2", result.baseWidth / 1000.0 * wallLengthM)
    )

    fun flatSlabRows(
        result: FlatSlabResult,
        lxM: Double,
        lyM: Double,
        mark: String = "Flat slab panel"
    ): List<BoqRow> = listOf(
        item(1, "Concrete - $mark", "m3", result.concreteVolumePerPanel),
        item(2, "Reinforcement steel - $mark", "kg", result.steelWeightPerPanel),
        item(3, "Formwork - $mark (soffit)", "m2", lxM * lyM)
    )
}
