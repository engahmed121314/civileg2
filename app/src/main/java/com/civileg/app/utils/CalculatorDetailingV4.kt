package com.civileg.app.utils

/**
 * CIVILEG DETAILING MODEL V4
 *
 * Data model for reinforcement detailing — used by CalculatorCadExporterV7.
 * Provides types for bar shapes, segments, lapping, anchorage, and BBS rows.
 * Units: mm unless stated otherwise.
 */
object CalculatorDetailingV4 {

    enum class MemberType {
        BEAM, COLUMN, SLAB, FOOTING, WALL, TANK, STAIR, STEEL_MEMBER, CONNECTION
    }

    enum class BarShape {
        STRAIGHT, L, U, C,
        STIRRUP_90, STIRRUP_135, CROSSTIE_135,
        HOOP, CUSTOM
    }

    data class Segment(
        val angleDeg: Double,
        val length: Double
    )

    data class LapLocation(
        val positionFromStartMm: Double,
        val lengthMm: Double
    )

    data class BarDefinition(
        val mark: String = "",
        val diameterMm: Int = 12,
        val layer: String = "REBAR",
        val spacingMm: Double? = null,
        val shape: BarShape = BarShape.STRAIGHT,
        val straightLengthMm: Double? = null,
        val segments: List<Segment> = emptyList(),
        val hookStartLengthMm: Double? = null,
        val hookEndLengthMm: Double? = null,
        val bendAllowanceMm: Double = 0.0,
        val anchorageLengthMm: Double? = null,
        val lapLengthMm: Double? = null,
        val cutOffFromStartMm: Double? = null,
        val cutOffFromEndMm: Double? = null,
        val lapLocations: List<LapLocation> = emptyList()
    )

    data class DetailingPackage(
        val memberType: MemberType,
        val memberId: String,
        val title: String = "",
        val geometry: Map<String, Double> = emptyMap(),
        val bars: List<BarDefinition> = emptyList(),
        val stirrups: List<BarDefinition> = emptyList()
    )

    data class BbsRow(
        val mark: String,
        val memberId: String,
        val diameterMm: Int,
        val shape: BarShape,
        val quantity: Int,
        val individualLengthMm: Double,
        val totalLengthM: Double,
        val totalWeightKg: Double
    )

    data class BarSchedule(
        val rows: List<BbsRow> = emptyList(),
        val totalWeightKg: Double = 0.0,
        val generatedAt: String = ""
    )

    /**
     * Build a BarSchedule from a list of DetailingPackages.
     * Aggregates all bars and stirrups across members.
     */
    fun buildBarSchedule(packages: List<DetailingPackage>): BarSchedule {
        val rows = mutableListOf<BbsRow>()
        var totalKg = 0.0
        val timestamp = java.time.Instant.now().toString()

        for (pkg in packages) {
            val allBars = pkg.bars + pkg.stirrups
            for (bar in allBars) {
                val cutLen = computeCutLength(bar)
                val qty = bar.spacingMm?.let { 1 } ?: 1
                val totalM = cutLen * qty / 1000.0
                val unitWeight = 0.006165 * bar.diameterMm * bar.diameterMm // kg/m
                val wt = totalM * unitWeight
                totalKg += wt
                rows.add(
                    BbsRow(
                        mark = bar.mark.ifEmpty { "B${rows.size + 1}" },
                        memberId = pkg.memberId,
                        diameterMm = bar.diameterMm,
                        shape = bar.shape,
                        quantity = qty,
                        individualLengthMm = cutLen,
                        totalLengthM = totalM,
                        totalWeightKg = wt
                    )
                )
            }
        }
        return BarSchedule(rows, totalKg, timestamp)
    }

    private fun computeCutLength(b: BarDefinition): Double {
        var len = b.straightLengthMm ?: b.segments.sumOf { it.length }
        len += b.hookStartLengthMm ?: 0.0
        len += b.hookEndLengthMm ?: 0.0
        len += b.bendAllowanceMm
        len += b.anchorageLengthMm ?: 0.0
        len += b.lapLengthMm ?: 0.0
        len -= b.cutOffFromStartMm ?: 0.0
        len -= b.cutOffFromEndMm ?: 0.0
        return kotlin.math.max(0.0, len)
    }
}
