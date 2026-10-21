package com.civileg.app.utils

import com.civileg.app.domain.FlatSlabResult
import com.civileg.app.domain.PileDesignResult
import com.civileg.app.domain.calculations.base.WaffleSlabDesign.WaffleSlabResult
import com.civileg.app.domain.calculations.ecp.CombinedFootingResult
import com.civileg.app.utils.CalculatorEngine.*
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.BoqRow
import kotlin.math.pow
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

    fun footingRows(
        result: FootingResult,
        mark: String = "Footing",
        excavationDepthM: Double = 0.0,
        workingSpaceM: Double = 0.5,
        blindingThicknessM: Double = 0.0
    ): List<BoqRow> {
        val formwork = 2 * (result.width + result.length) * result.thickness / 1_000_000.0
        val rows = mutableListOf(
            item(1, "Concrete - $mark", "m3", result.concreteVolume),
            item(2, "Reinforcement steel - $mark", "kg", result.steelWeight),
            item(3, "Formwork - $mark (perimeter)", "m2", formwork)
        )
        if (blindingThicknessM > 0.0) {
            val blL = (result.length / 1000.0) + 2 * workingSpaceM
            val blW = (result.width / 1000.0) + 2 * workingSpaceM
            rows.add(item(rows.size + 1, "Blinding concrete - $mark", "m3", blL * blW * blindingThicknessM))
        }
        if (excavationDepthM > 0.0) {
            val excL = (result.length / 1000.0) + 2 * workingSpaceM
            val excW = (result.width / 1000.0) + 2 * workingSpaceM
            rows.add(item(rows.size + 1, "Excavation - $mark", "m3", excL * excW * excavationDepthM))
        }
        return rows
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
        mark: String = "Retaining wall",
        excavationDepthM: Double = 0.0,
        backfillWidthM: Double = 1.0
    ): List<BoqRow> {
        val rows = mutableListOf(
            item(1, "Concrete - $mark", "m3", result.concreteVolume * wallLengthM),
            item(2, "Reinforcement steel - $mark", "kg", result.steelWeight * wallLengthM),
            item(3, "Formwork - $mark (both faces)", "m2", 2 * result.height * wallLengthM),
            item(4, "Formwork - $mark (base soffit)", "m2", result.baseWidth / 1000.0 * wallLengthM)
        )
        if (excavationDepthM > 0.0 && wallLengthM > 0.0) {
            rows.add(item(rows.size + 1, "Excavation - $mark", "m3", wallLengthM * backfillWidthM * excavationDepthM))
        }
        return rows
    }

    fun combinedFootingRows(
        result: CombinedFootingResult,
        mark: String = "Combined footing",
        excavationDepthM: Double = 0.0,
        workingSpaceM: Double = 0.5,
        blindingThicknessM: Double = 0.0
    ): List<BoqRow> {
        val lengthM = result.footingLength
        val widthM = result.footingWidth
        val formwork = 2 * (lengthM + widthM) * result.footingThickness / 1000.0
        val rows = mutableListOf(
            item(1, "Concrete - $mark", "m3", result.concreteVolume),
            item(2, "Reinforcement steel - $mark", "kg", result.steelWeight),
            item(3, "Formwork - $mark (perimeter)", "m2", formwork)
        )
        if (blindingThicknessM > 0.0) {
            val blL = lengthM + 2 * workingSpaceM
            val blW = widthM + 2 * workingSpaceM
            rows.add(item(rows.size + 1, "Blinding concrete - $mark", "m3", blL * blW * blindingThicknessM))
        }
        if (excavationDepthM > 0.0) {
            val excL = lengthM + 2 * workingSpaceM
            val excW = widthM + 2 * workingSpaceM
            rows.add(item(rows.size + 1, "Excavation - $mark", "m3", excL * excW * excavationDepthM))
        }
        return rows
    }

    fun pileRows(
        result: PileDesignResult,
        mark: String = "Pile group",
        excavationDepthM: Double = 0.0,
        workingSpaceM: Double = 0.5,
        blindingThicknessM: Double = 0.0
    ): List<BoqRow> {
        val n = result.numberOfPiles
        val pileLengthMm = result.pileLengthM * 1000.0
        val d = result.pileDiameterMm
        val pileConcrete = n * Math.PI * (d / 2.0).pow(2) * pileLengthMm / 1_000_000_000.0
        val pileSteel = result.pileReinforcement.longitudinalArea * n * pileLengthMm * 7850.0 / 1_000_000_000.0
        val cap = result.capResult
        val rows = mutableListOf(
            item(1, "Concrete - $mark (piles + cap)", "m3", pileConcrete + cap.concreteVolume),
            item(2, "Reinforcement steel - $mark", "kg", pileSteel + cap.steelWeight),
            item(3, "Formwork - $mark (pile shafts)", "m2", n * Math.PI * d * pileLengthMm / 1_000_000.0),
            item(4, "Formwork - cap sides", "m2", 2 * (cap.capWidth + cap.capLength) * cap.capThickness / 1_000_000.0)
        )
        if (blindingThicknessM > 0.0) {
            val blL = (cap.capLength / 1000.0) + 2 * workingSpaceM
            val blW = (cap.capWidth / 1000.0) + 2 * workingSpaceM
            rows.add(item(rows.size + 1, "Blinding concrete - cap", "m3", blL * blW * blindingThicknessM))
        }
        if (excavationDepthM > 0.0) {
            val excL = (cap.capLength / 1000.0) + 2 * workingSpaceM
            val excW = (cap.capWidth / 1000.0) + 2 * workingSpaceM
            rows.add(item(rows.size + 1, "Excavation - cap", "m3", excL * excW * excavationDepthM))
        }
        return rows
    }

    fun strapFootingRows(
        result: StrapFootingResult,
        distanceM: Double,
        mark: String = "Strap footing"
    ): List<BoqRow> {
        val f1 = result.footing1
        val f2 = result.footing2
        val padFormwork = 2 * (f1.width + f1.length) * f1.thickness / 1_000_000.0 +
            2 * (f2.width + f2.length) * f2.thickness / 1_000_000.0
        val strapFormwork = 2 * result.strapBeamDepth * distanceM / 1000.0
        return listOf(
            item(1, "Concrete - $mark", "m3", result.concreteVolume),
            item(2, "Reinforcement steel - $mark", "kg", result.steelWeight),
            item(3, "Formwork - pads + strap sides", "m2", padFormwork + strapFormwork)
        )
    }

    fun waffleSlabRows(
        result: WaffleSlabResult,
        lxM: Double,
        lyM: Double,
        mark: String = "Waffle slab"
    ): List<BoqRow> = listOf(
        item(1, "Concrete - $mark", "m3", result.concreteVolume),
        item(2, "Reinforcement steel - $mark", "kg", result.steelWeight),
        item(3, "Formwork - $mark (soffit)", "m2", lxM * lyM)
    )

    fun tankRows(
        concreteVolumeM3: Double,
        steelWeightKg: Double,
        lengthM: Double,
        widthM: Double,
        heightM: Double,
        mark: String = "Water tank",
        excavationDepthM: Double = 0.0,
        blindingThicknessM: Double = 0.0
    ): List<BoqRow> {
        val rows = mutableListOf(
            item(1, "Concrete - $mark", "m3", concreteVolumeM3),
            item(2, "Reinforcement steel - $mark", "kg", steelWeightKg),
            item(3, "Formwork - $mark (walls)", "m2", 2 * (lengthM + widthM) * heightM),
            item(4, "Formwork - $mark (base)", "m2", lengthM * widthM)
        )
        if (blindingThicknessM > 0.0) {
            rows.add(item(rows.size + 1, "Blinding concrete - $mark", "m3", (lengthM + 0.5) * (widthM + 0.5) * blindingThicknessM))
        }
        if (excavationDepthM > 0.0) {
            rows.add(item(rows.size + 1, "Excavation - $mark (pit)", "m3", (lengthM + 0.5) * (widthM + 0.5) * excavationDepthM))
        }
        return rows
    }

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
