package com.civileg.app.utils.detailing

import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.domain.calculations.utils.InteractionDiagramSolver
import com.civileg.app.domain.calculations.utils.InteractionPoint
import com.civileg.app.domain.calculations.utils.RectangularSection
import com.civileg.app.domain.calculations.utils.InteractionReinforcementInput
import com.civileg.core.calculations.entities.DesignCode
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * §16 — COLUMN DETAILING ENGINE.
 *
 * Converts CalculatorEngine.ColumnResult into a full detailing payload:
 *  - ColumnRebarLayout: ACTUAL bar coordinates (corners + balanced side bars)
 *  - Tie densification zones verbatim from the engine
 *  - Lap/splice zone anchored to the engine's support-zone extent
 *  - P-M interaction CURVE from the genuine fiber solver (§17) — ECP path only;
 *    for ACI/SBC the curve is omitted (never fabricated) and a warning is raised.
 *  - BBS rows with real cut lengths + multi-sheet StructuralDrawing S-COL-%03d.
 */
object ColumnDetailingEngine {

    /** One longitudinal bar placed at an exact section coordinate. */
    data class BarCoordinate(
        val xMm: Double, val yMm: Double,
        val isCorner: Boolean,
        val face: Face
    ) { enum class Face { BOTTOM, TOP, LEFT, RIGHT, CORNER } }

    data class ColumnRebarLayout(
        val widthMm: Double,
        val depthMm: Double,
        val coverMm: Double,
        val barDiaMm: Int,
        val cornerBars: List<BarCoordinate>,
        val sideBars: List<BarCoordinate>
    ) {
        val allBars: List<BarCoordinate> get() = cornerBars + sideBars

        /** Minimum free distance between any two bars (mm). */
        fun minBarSpacing(): Double {
            var min = Double.MAX_VALUE
            val pts = allBars
            for (i in pts.indices) for (j in i + 1 until pts.size) {
                val dx = pts[i].xMm - pts[j].xMm
                val dy = pts[i].yMm - pts[j].yMm
                val d = sqrt(dx * dx + dy * dy)
                if (d < min) min = d
            }
            return if (min == Double.MAX_VALUE) 0.0 else min
        }

        companion object {
            /**
             * Distribute [totalBars] on a rectangular section:
             * 4 corners first; the rest split between the four faces, long faces
             * receiving one extra bar each time so counts stay balanced.
             * Coordinates are inset by cover + Ø/2 from every concrete face.
             */
            fun distribute(
                widthMm: Double, depthMm: Double,
                coverMm: Double, barDiaMm: Int,
                totalBars: Int
            ): ColumnRebarLayout {
                require(widthMm > 0 && depthMm > 0) { "ColumnRebarLayout: section dims must be > 0" }
                require(coverMm >= 0) { "ColumnRebarLayout: cover must be >= 0" }
                require(totalBars >= 4) { "ColumnRebarLayout: minimum 4 bars (corners), got $totalBars" }

                val inset = coverMm + barDiaMm / 2.0
                val x0 = inset; val x1 = widthMm - inset
                val y0 = inset; val y1 = depthMm - inset
                require(x1 > x0 && y1 > y0) {
                    "ColumnRebarLayout: cover+Ø/2 leaves no room (w=$widthMm d=$depthMm c=$coverMm Ø$barDiaMm)"
                }

                val corners = listOf(
                    BarCoordinate(x0, y0, true, BarCoordinate.Face.CORNER),
                    BarCoordinate(x1, y0, true, BarCoordinate.Face.CORNER),
                    BarCoordinate(x1, y1, true, BarCoordinate.Face.CORNER),
                    BarCoordinate(x0, y1, true, BarCoordinate.Face.CORNER)
                )

                var remaining = totalBars - 4
                // [P038 FIX — was WRONG] Only 1 bar per face was placed regardless
                // of the remaining count (62-bar sections silently became 8).
                // Robust placement for ANY upstream count: long faces take the
                // larger half (±1 when odd — standard site practice), short
                // faces split what remains.
                val longTotal = (remaining + 1) / 2
                val shortTotal = remaining - longTotal

                val sides = mutableListOf<BarCoordinate>()
                fun fill(face: BarCoordinate.Face, count: Int) {
                    if (count <= 0) return
                    when (face) {
                        BarCoordinate.Face.LEFT -> {
                            val step = (y1 - y0) / (count + 1)
                            for (k in 1..count) sides += BarCoordinate(x0, y0 + k * step, false, face)
                        }
                        BarCoordinate.Face.RIGHT -> {
                            val step = (y1 - y0) / (count + 1)
                            for (k in 1..count) sides += BarCoordinate(x1, y0 + k * step, false, face)
                        }
                        BarCoordinate.Face.BOTTOM -> {
                            val step = (x1 - x0) / (count + 1)
                            for (k in 1..count) sides += BarCoordinate(x0 + k * step, y0, false, face)
                        }
                        BarCoordinate.Face.TOP -> {
                            val step = (x1 - x0) / (count + 1)
                            for (k in 1..count) sides += BarCoordinate(x0 + k * step, y1, false, face)
                        }
                        else -> {}
                    }
                }
                val longFaces = if (depthMm >= widthMm)
                    listOf(BarCoordinate.Face.LEFT, BarCoordinate.Face.RIGHT)
                else
                    listOf(BarCoordinate.Face.BOTTOM, BarCoordinate.Face.TOP)
                val shortFaces = if (depthMm >= widthMm)
                    listOf(BarCoordinate.Face.BOTTOM, BarCoordinate.Face.TOP)
                else
                    listOf(BarCoordinate.Face.LEFT, BarCoordinate.Face.RIGHT)

                fill(longFaces[0], longTotal / 2); fill(longFaces[1], longTotal - longTotal / 2)
                fill(shortFaces[0], shortTotal / 2); fill(shortFaces[1], shortTotal - shortTotal / 2)

                return ColumnRebarLayout(widthMm, depthMm, coverMm, barDiaMm, corners, sides)
            }
        }
    }

    /** Spec §16 — full column detailing payload. */
    data class ColumnDetailingData(
        val mark: String,
        val widthMm: Double,
        val depthMm: Double,
        val clearHeightMm: Double,
        val coverMm: Double,
        val mainMark: String,
        val mainDiaMm: Int,
        val mainCount: Int,
        val mainCutLengthMm: Double,
        val layout: ColumnRebarLayout,
        val tieZones: List<RebarZone>,
        val tieTypeLabel: String,
        val confinementLengthMm: Double,
        val lapZoneStartMm: Double,
        val lapZoneEndMm: Double,
        val pmCurve: List<InteractionPoint>,
        val designPuKn: Double,
        val designMuKnM: Double,
        val schedule: RebarSchedule,
        val calcStrip: List<Pair<String, String>>,
        val drawing: StructuralDrawing,
        val warnings: List<String>
    )

    /**
     * @param biaxial optional result of the advanced Load-Contour check (caller passes
     *                it when the advanced engine was used); surfaced in calc strip.
     */
    fun build(
        result: CalculatorEngine.ColumnResult,
        clearHeightMm: Double,
        coverMm: Double,
        fcuMPa: Double,
        fyMPa: Double,
        codeName: String = "ECP 203",
        beamIdOffset: Int = 1,
        biaxial: com.civileg.app.domain.entities.BiaxialCheckResult? = null
    ): ColumnDetailingData {
        // ── Hard guards ──
        require(clearHeightMm > 0.0) { "Column DXF export requires a valid clear height." }
        require(result.width > 0 && result.depth > 0) { "Column section dims missing." }
        require(result.reinforcement.numBars >= 4) {
            "Column needs ≥4 longitudinal bars (corners), got ${result.reinforcement.numBars}"
        }
        require(fcuMPa > 0 && fyMPa > 0) { "ColumnDetailingEngine requires real fcu/fy." }

        val marks = BarMarkRegistry()
        val warnings = mutableListOf<String>()

        // ── §16 layout: real coordinates ──
        val layout = ColumnRebarLayout.distribute(
            widthMm = result.width, depthMm = result.depth,
            coverMm = coverMm, barDiaMm = result.reinforcement.diameter,
            totalBars = result.reinforcement.numBars
        )

        // ── §27 Constructability: flag congestion honestly ──
        // DESIGN SAFE BUT CONSTRUCTION DIFFICULT must be surfaced, never buried —
        // the detailing layer places exactly what the design engine demands and
        // warns when that demand is physically unbuildable in this section.
        run {
            val minClear = layout.minBarSpacing() - result.reinforcement.diameter
            if (layout.allBars.size >= 2 && minClear < 40.0) {
                warnings.add(
                    "CONSTRUCTABILITY: clear bar spacing %.0f mm < 40 mm for %dØ%d in %dx%d mm — enlarge the section or reduce the demand."
                        .format(minClear, result.reinforcement.numBars,
                            result.reinforcement.diameter,
                            result.width.toInt(), result.depth.toInt())
                )
            }
        }

        // Main bar cut length: clear height + base anchorage (Ld, code-driven)
        val ld = CodeLengths.development(fyMPa, result.reinforcement.diameter.toDouble(), fcuMPa)
        val mainCut = round25(clearHeightMm + ld.value)

        val mainMark = marks.next("C", "${result.reinforcement.numBars}T${result.reinforcement.diameter} vertical")

        // ── Tie zones verbatim (fallback: honest single zone) ──
        val zones: List<RebarZone> = if (result.stirrups.zones.isNotEmpty()) {
            result.stirrups.zones.mapIndexed { i, z ->
                RebarZone(z.startLocation, z.endLocation, z.diameter, z.spacing,
                    marks.register("T${i + 1}", z.description.ifEmpty { "Ties" }),
                    z.name)
            }
        } else {
            listOf(RebarZone(0.0, clearHeightMm, result.stirrups.diameter, result.stirrups.spacing,
                marks.register("T1", "Ties"), "FULL HEIGHT"))
        }
        val confinementLen = zones.first().endMm
        // Lap/splice zone anchored to the bottom support-zone extent (engine-derived)
        val lapStart = 50.0                       // above floor finish — site convention noted in notes
        val lapEnd = maxOf(confinementLen, lapStart + ld.value)

        val isCircular = result.columnType.contains("CIRC", true)
        val tieTypeLabel = if (isCircular) "SPIRAL / HOOP" else
            "CLOSED TIED — 135° hooks (${result.stirrups.numLegs} legs)"

        // ── §17 P-M interaction: genuine fiber curve (ECP solver) or honest omission ──
        var pmCurve: List<InteractionPoint> = emptyList()
        if (result.code == CalculatorEngine.AppDesignCode.EGYPTIAN) {
            pmCurve = InteractionDiagramSolver.generateCurve(
                fcu = fcuMPa, fy = fyMPa,
                section = RectangularSection(width = result.width, depth = result.depth),
                reinforcement = InteractionReinforcementInput(
                    totalArea = result.reinforcementArea.takeIf { it > 0 }
                        ?: (result.reinforcement.numBars * PI * result.reinforcement.diameter.toDouble().pow(2) / 4.0),
                    distributionType = "Uniform",
                    cover = coverMm,
                    numBarsPerFace = ceil(result.reinforcement.numBars / 4.0).toInt().coerceAtLeast(2)
                )
            )
        } else {
            warnings.add("P-M interaction curve omitted: fiber solver implements ECP 203 §4-2-3 only.")
        }
        val designMu = sqrt(result.muX * result.muX + result.muY * result.muY)

        // ── BBS rows ──
        val rows = buildList {
            add(RebarScheduleRow(
                mark = mainMark, memberLocation = "FULL HEIGHT",
                diameterMm = result.reinforcement.diameter, shapeCode = "00",
                qty = result.reinforcement.numBars,
                dimA = mainCut, cuttingLengthMm = mainCut,
                totalLengthM = mainCut * result.reinforcement.numBars / 1000.0,
                unitWeightKgM = RebarScheduleRow.unitWeight(result.reinforcement.diameter),
                totalWeightKg = BarBendingEngine.totalWeightKg(mainCut, result.reinforcement.diameter, result.reinforcement.numBars)
            ))
            zones.forEach { z ->
                val cut = BarBendingEngine.stirrupCutLength(
                    outWidthMm = result.width, outHeightMm = result.depth,
                    coverMm = coverMm, diaMm = z.diameterMm
                )
                add(RebarScheduleRow(
                    mark = z.mark, memberLocation = z.description,
                    diameterMm = z.diameterMm, shapeCode = "11",
                    qty = z.count,
                    dimA = (result.width - 2 * coverMm - z.diameterMm),
                    dimB = (result.depth - 2 * coverMm - z.diameterMm),
                    cuttingLengthMm = cut,
                    totalLengthM = cut * z.count / 1000.0,
                    unitWeightKgM = RebarScheduleRow.unitWeight(z.diameterMm),
                    totalWeightKg = BarBendingEngine.totalWeightKg(cut, z.diameterMm, z.count)
                ))
            }
        }
        val schedule = RebarSchedule(rows)

        // ── Calc strip (real outputs only) ──
        val strip = buildList {
            add("Pu" to "%.1f kN".format(result.pu))
            add("Mx" to "%.1f kN.m".format(result.muX))
            add("My" to "%.1f kN.m".format(result.muY))
            add("Axial capacity" to "%.1f kN".format(result.axialCapacity))
            biaxial?.let {
                add("Biaxial method" to it.formula.take(40))
                add("Interaction factor" to "%.3f".format(it.interactionFactor))
            }
            add("Slenderness λ" to "%.1f%s".format(result.slenderness, if (result.isSlender) " (SLENDER)" else ""))
            add("Utilization" to "%.0f%%".format(result.utilizationRatio * 100))
            add("Status" to if (result.isSafe) "SAFE" else "UNSAFE")
        }

        // ── Multi-sheet drawing ──
        val prefix = "S-COL-%03d".format(beamIdOffset)
        val tb = TitleBlock(
            project = "CivilEG Project",
            drawingTitle = "COLUMN ${result.width.toInt()}x${result.depth.toInt()} — $prefix",
            drawingNumber = "$prefix-DET",
            date = java.time.LocalDate.now().toString(),
            scale = "NTS",
            designCode = codeName
        )
        fun sheet(no: Int, title: String, type: DrawingViewType): DrawingSheet {
            val views = buildList {
                add(DrawingView(type = type, title = title, originX = 25.0, originY = 25.0,
                    scale = ScaleEngine.fitDrawingToSheet(maxOf(result.width, result.depth), clearHeightMm).scale))
                if (type == DrawingViewType.INTERACTION && pmCurve.isNotEmpty()) {
                    add(DrawingView(type = DrawingViewType.CALCULATION,
                        title = "P-M INTERACTION (ECP §4-2-3 fiber)",
                        originX = 25.0, originY = 25.0, scale = DrawingScale.NTS))
                }
            }
            return DrawingSheet(sheetNumber = "$prefix-%03d".format(no), title = title,
                titleBlock = tb.copy(sheet = "$no/${sheetsCount(codeName, pmCurve)}"),
                views = views)
        }
        var n = 1
        val sheets = buildList {
            add(sheet(n++, "ELEVATION & TIE ZONES", DrawingViewType.ELEVATION))
            add(sheet(n++, "PLAN LAYOUT & SECTION", DrawingViewType.PLAN))
            if (pmCurve.isNotEmpty()) add(sheet(n++, "P-M INTERACTION DIAGRAM", DrawingViewType.INTERACTION))
            add(sheet(n++, "BAR BENDING SCHEDULE", DrawingViewType.BBS))
            add(sheet(n++, "CALCULATION SUMMARY", DrawingViewType.CALCULATION))
        }

        return ColumnDetailingData(
            mark = "$prefix-C${beamIdOffset}",
            widthMm = result.width, depthMm = result.depth,
            clearHeightMm = clearHeightMm, coverMm = coverMm,
            mainMark = mainMark, mainDiaMm = result.reinforcement.diameter,
            mainCount = result.reinforcement.numBars, mainCutLengthMm = mainCut,
            layout = layout, tieZones = zones, tieTypeLabel = tieTypeLabel,
            confinementLengthMm = confinementLen,
            lapZoneStartMm = lapStart, lapZoneEndMm = lapEnd,
            pmCurve = pmCurve, designPuKn = result.pu, designMuKnM = designMu,
            schedule = schedule, calcStrip = strip,
            drawing = StructuralDrawing(
                drawingId = "$prefix-PKG",
                title = "RC COLUMN DETAILED",
                elementType = "COLUMN",
                sheets = sheets,
                notes = listOf(
                    "Cover ${coverMm} mm. Lap splice starts ${lapStart.toInt()} mm above floor.",
                    "Lap length Ld-based = ${"%.0f".format(ld.value)} mm (ECP).",
                    "All dims in mm."
                ),
                schedules = listOf(schedule),
                warnings = warnings
            ),
            warnings = warnings
        )
    }

    private fun sheetsCount(codeName: String, pmCurve: List<*>) =
        if (pmCurve.isNotEmpty() || !codeName.contains("ECP")) 5 else 4

    private fun round25(v: Double) = ceil(v / 25.0) * 25.0
}
