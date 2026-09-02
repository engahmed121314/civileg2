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
        val lapLocations: List<LapLocation> = emptyList(),
        /**
         * Explicit bar count (W11). When null, buildBarSchedule derives it from
         * [spacingMm] and the member's distribution dimension; bars with neither
         * fall back to 1.
         */
        val quantity: Int? = null
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
                val qty = bar.quantity ?: deriveQuantity(pkg, bar)
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
        // Explicit segments already encode the final cut length (including
        // hooks), so no bend deduction is applied on top — doing so would
        // double-count (ADR-026 is only for straight-length + allowances).
        val d = b.diameterMm.toDouble()
        val deduction = if (b.segments.isNotEmpty()) {
            0.0
        } else {
            when (b.shape) {
                BarShape.L -> 2.0 * d // One 90-deg bend
                BarShape.U -> 4.0 * d // Two 90-deg bends
                BarShape.C -> 4.0 * d
                BarShape.STIRRUP_90 -> 8.0 * d // Four 90-deg bends
                BarShape.STIRRUP_135 -> 10.0 * d // Two 135-deg hooks + three 90-deg bends
                else -> 0.0
            }
        }

        var len = (b.straightLengthMm ?: b.segments.sumOf { it.length }) - deduction
        len += b.hookStartLengthMm ?: 0.0
        len += b.hookEndLengthMm ?: 0.0
        len += b.bendAllowanceMm
        len += b.anchorageLengthMm ?: 0.0
        len += b.lapLengthMm ?: 0.0
        len -= b.cutOffFromStartMm ?: 0.0
        len -= b.cutOffFromEndMm ?: 0.0
        return kotlin.math.max(0.0, len)
    }

    /**
     * W11 fix: derive the bar count for spaced bars from the member geometry.
     * Convention: n = floor(distribution_length / spacing) + 1, where the
     * distribution length is the dimension perpendicular to the bar run:
     *  - BEAM/COLUMN links: span (minus zone cut-offs) / height
     *  - FOOTING mats: clear plan dimension perpendicular to the bar run
     *  - TANK wall bars: along tank length; base mesh: across tank width
     * Members without a usable dimension fall back to 1; callers with exact
     * engine counts should pass [BarDefinition.quantity] instead.
     */
    private fun deriveQuantity(pkg: DetailingPackage, bar: BarDefinition): Int {
        val spacing = bar.spacingMm?.takeIf { it > 0.0 } ?: return 1
        val g = pkg.geometry

        fun count(distributionLength: Double): Int =
            kotlin.math.max(1, kotlin.math.floor(distributionLength / spacing + 1e-9).toInt() + 1)

        return when (pkg.memberType) {
            MemberType.BEAM ->
                if (isLinkShape(bar.shape)) {
                    val span = g["span"] ?: return 1
                    val start = bar.cutOffFromStartMm ?: 0.0
                    val end = bar.cutOffFromEndMm ?: 0.0
                    count(span - start - end)
                } else 1

            MemberType.COLUMN ->
                if (isLinkShape(bar.shape)) g["height"]?.let(::count) ?: 1 else 1

            MemberType.FOOTING -> {
                val cover = g["cover"] ?: 0.0
                val length = g["length"] ?: return 1
                val width = g["width"] ?: return 1
                val runLen = bar.straightLengthMm ?: 0.0
                val distAcross =
                    if (kotlin.math.abs(runLen - (length - 2 * cover)) <=
                        kotlin.math.abs(runLen - (width - 2 * cover))
                    ) width - 2 * cover else length - 2 * cover
                count(distAcross)
            }

            MemberType.TANK ->
                when (bar.layer.uppercase()) {
                    "REBAR-WALL" -> g["length"]?.let(::count) ?: 1
                    "REBAR-BASE" -> g["width"]?.let(::count) ?: 1
                    else -> 1
                }

            else -> 1
        }
    }

    private fun isLinkShape(shape: BarShape): Boolean = when (shape) {
        BarShape.STIRRUP_90, BarShape.STIRRUP_135, BarShape.CROSSTIE_135, BarShape.HOOP -> true
        else -> false
    }
}
