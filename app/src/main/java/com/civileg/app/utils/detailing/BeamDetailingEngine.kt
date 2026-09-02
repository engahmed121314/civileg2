package com.civileg.app.utils.detailing

import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.CalculatorDetailingV4
import com.civileg.core.calculations.entities.DesignCode

/**
 * §14 — BEAM DETAILING ENGINE.
 *
 * Converts a CalculatorEngine.BeamResult (+ its real inputs) into a complete
 * BeamDetailingData: zones, bar marks, development/lap/hooks, cut lengths,
 * schedules and a multi-sheet StructuralDrawing skeleton.
 *
 * HARD RULES honoured here:
 *  - No hidden defaults: every dimension comes from the result or the caller.
 *    Missing data → explicit require() failure with a bilingual message.
 *  - Calculation stays separated from drawing: this engine only ORGANISES the
 *    numbers; CAD geometry rendering lives in CadExporterV7.
 */
object BeamDetailingEngine {

    data class BeamInput(
        val spanMm: Double,
        val coverMm: Double,
        val supportTypeName: String,     // CalculatorEngine.SupportType.name
        val codeName: String,            // e.g. "ECP 203"
        val fcuMPa: Double,
        val fyMPa: Double
    )
    // NOTE: no convenience factory from BeamResult alone — BeamResult does not
    // carry fcu/fy/span/cover, and spec §2 forbids guessing them here. The
    // caller (ViewModel/screen holding the original input) must supply them.

    /** Spec §14 — full detailing payload for one beam. */
    data class BeamDetailingData(
        val mark: String,
        val spanMm: Double,
        val widthMm: Double,
        val depthMm: Double,
        val coverMm: Double,
        val supportTypeName: String,
        val bottomBarsMark: String,
        val topBarsMark: String,
        val extraTopMark: String?,
        val extraBottomMark: String?,
        val bottomCutLengthMm: Double,
        val topCutLengthMm: Double,
        val stirrupZones: List<RebarZone>,
        val stirrupHookMm: Double,
        val stirrupCutLengthMm: Double,
        val development: DevelopmentLengthMm,
        val lap: LapLengthMm,
        val anchorage: AnchorageLengthMm,
        val schedule: RebarSchedule,
        val calcStrip: List<Pair<String, String>>,   // label → value (calc summary sheet)
        val drawing: StructuralDrawing
    )

    /**
     * Build the full detailing package.
     * @param fcuMPa/fyMPa concrete/steel strengths from the ORIGINAL calculator input
     *        (BeamResult does not carry them — caller must supply, no guessing).
     */
    fun build(
        result: CalculatorEngine.BeamResult,
        input: BeamInput,
        beamId: Int = 1
    ): BeamDetailingData {
        // ── Hard guards (rule §2: fail loudly on missing data) ──
        require(input.spanMm > 0.0) { "Beam DXF export requires a valid span." }
        require(input.coverMm >= 0.0) { "Beam DXF export requires a valid cover." }
        require(result.width > 0 && result.depth > 0) {
            "Beam section dims missing (w=${result.width}, h=${result.depth}) — cannot detail"
        }
        require(result.reinforcementBottom.numBars > 0 && result.reinforcementBottom.diameter > 0) {
            "BeamResult has no bottom reinforcement — cannot produce bar marks"
        }
        require(input.fcuMPa > 0 && input.fyMPa > 0) {
            "BeamDetailingEngine requires real fcu/fy from the original calculator input."
        }

        val marks = BarMarkRegistry()
        val isCantilever = input.supportTypeName == "CANTILEVER"

        // ── Code-driven lengths (§27) — anchorage diameter follows the GOVERNING face:
        //    cantilever → top bars are anchored into the support; else bottom bars.
        val governingDia = if (isCantilever) result.reinforcementTop.diameter
                           else result.reinforcementBottom.diameter
        val dev = CodeLengths.development(input.fyMPa, governingDia.toDouble(), input.fcuMPa)
        val lap = CodeLengths.tensionLap(dev)
        val hook = CodeLengths.stirrupHook135(result.stirrups.diameter)

        // ── Main bar cut lengths (shape 00; cantilever top bars get +Ld anchorage into support) ──
        val clearSpan = input.spanMm - 2 * input.coverMm
        val bottomCut = BarBendingEngine.straightCutLength(
            if (isCantilever) clearSpan else clearSpan
        )
        val topCut = BarBendingEngine.straightCutLength(
            if (isCantilever) clearSpan + dev.value          // anchor back into support
            else clearSpan
        ).let { if (isCantilever) round50(it) else it }

        // ── Marks ──
        val bottomMark = marks.next("B", "Bottom ${result.reinforcementBottom.numBars}T${result.reinforcementBottom.diameter}")
        val topMark = marks.next("B", "Top/hangers")
        val extraTopMark = if (result.reinforcementTop.numBars > maxOf(2, result.reinforcementBottom.numBars / 3))
            marks.next("B", "Extra top at supports") else null

        // ── Stirrup zones (§15): reuse engine zones verbatim, else honest single zone ──
        val zones: List<RebarZone> = if (result.stirrups.zones.isNotEmpty()) {
            result.stirrups.zones.mapIndexed { i, z ->
                RebarZone(
                    startMm = z.startLocation, endMm = z.endLocation,
                    diameterMm = z.diameter, spacingMm = z.spacing,
                    mark = marks.register("S${i + 1}", z.description.ifEmpty { "Stirrups" }),
                    description = z.name
                )
            }
        } else {
            listOf(
                RebarZone(0.0, input.spanMm, result.stirrups.diameter, result.stirrups.spacing,
                    marks.register("S1", "Shear ties"), "FULL SPAN")
            )
        }
        val stirrupCut = BarBendingEngine.stirrupCutLength(
            outWidthMm = result.width, outHeightMm = result.depth,
            coverMm = input.coverMm, diaMm = zones.first().diameterMm
        )

        // ── BBS rows (§25) with shape geometry values A/B/C ──
        val rows = buildList {
            add(RebarScheduleRow(
                mark = bottomMark, memberLocation = "SPAN",
                diameterMm = result.reinforcementBottom.diameter, shapeCode = "00",
                qty = result.reinforcementBottom.numBars,
                dimA = bottomCut, cuttingLengthMm = bottomCut,
                totalLengthM = bottomCut * result.reinforcementBottom.numBars / 1000.0,
                unitWeightKgM = RebarScheduleRow.unitWeight(result.reinforcementBottom.diameter),
                totalWeightKg = BarBendingEngine.totalWeightKg(bottomCut, result.reinforcementBottom.diameter, result.reinforcementBottom.numBars)
            ))
            add(RebarScheduleRow(
                mark = topMark, memberLocation = if (isCantilever) "TOP-CANT" else "TOP-HANGERS",
                diameterMm = result.reinforcementTop.diameter, shapeCode = "00",
                qty = result.reinforcementTop.numBars,
                dimA = topCut, cuttingLengthMm = topCut,
                totalLengthM = topCut * result.reinforcementTop.numBars / 1000.0,
                unitWeightKgM = RebarScheduleRow.unitWeight(result.reinforcementTop.diameter),
                totalWeightKg = BarBendingEngine.totalWeightKg(topCut, result.reinforcementTop.diameter, result.reinforcementTop.numBars)
            ))
            zones.forEach { z ->
                add(RebarScheduleRow(
                    mark = z.mark, memberLocation = z.description,
                    diameterMm = z.diameterMm, shapeCode = "11",
                    qty = z.count,
                    dimA = (result.width - 2 * input.coverMm - z.diameterMm),
                    dimB = (result.depth - 2 * input.coverMm - z.diameterMm),
                    cuttingLengthMm = stirrupCut,
                    totalLengthM = stirrupCut * z.count / 1000.0,
                    unitWeightKgM = RebarScheduleRow.unitWeight(z.diameterMm),
                    totalWeightKg = BarBendingEngine.totalWeightKg(stirrupCut, z.diameterMm, z.count)
                ))
            }
        }
        val schedule = RebarSchedule(rows)

        // ── Calculation strip (§14) — only REAL engine outputs, no fabrication ──
        val strip = listOf(
            "Mu" to "%.1f kN.m".format(result.mu),
            "Vu" to "%.1f kN".format(result.vu),
            "Moment capacity" to "%.1f kN.m".format(result.momentCapacity),
            "Shear capacity" to "%.1f kN".format(result.shearCapacity),
            "As provided" to "${result.reinforcementBottom.numBars}T${result.reinforcementBottom.diameter} (${result.steelRatio.toInt()}% ratio)" ,
            "Deflection" to "%.1f / %.1f mm".format(result.deflection, result.allowableDeflection),
            "Utilization" to "%.0f%%".format(result.utilizationRatio * 100),
            "Status" to if (result.isSafe) "SAFE" else "UNSAFE"
        )

        // ── Multi-sheet StructuralDrawing (spec §38 naming) ──
        val prefix = "S-BM-%03d".format(beamId)
        val tb = TitleBlock(
            project = "CivilEG Project",
            drawingTitle = "BEAM ${result.width.toInt()}x${result.depth.toInt()} — $prefix",
            drawingNumber = "$prefix-DET",
            date = java.time.LocalDate.now().toString(),
            scale = "NTS",
            designCode = input.codeName
        )
        fun sheet(no: Int, title: String, type: DrawingViewType) = DrawingSheet(
            sheetNumber = "$prefix-%03d".format(no),
            title = title,
            titleBlock = tb.copy(sheet = "$no/4"),
            views = listOf(DrawingView(type = type, title = title, originX = 25.0, originY = 25.0,
                scale = ScaleEngine.fitDrawingToSheet(input.spanMm, result.depth).scale))
        )
        val drawing = StructuralDrawing(
            drawingId = "$prefix-PKG",
            title = "RC BEAM DETAILED — ${input.supportTypeName.replace('_', '-')}",
            elementType = "BEAM",
            sheets = listOf(
                sheet(1, "GENERAL ELEVATION & ZONES", DrawingViewType.ELEVATION),
                sheet(2, "SECTIONS A-A / B-B", DrawingViewType.SECTION),
                sheet(3, "BAR BENDING SCHEDULE", DrawingViewType.BBS),
                sheet(4, "CALCULATION SUMMARY", DrawingViewType.CALCULATION)
            ),
            notes = listOf(
                "Cover ${input.coverMm} mm all faces.",
                "Development Ld=${"%.0f".format(dev.value)} mm · Lap=${"%.0f".format(lap.value)} mm (ECP 203).",
                "All dims in mm. Concrete C${input.fcuMPa.toInt()}; steel fy=${input.fyMPa.toInt()} MPa."
            ),
            schedules = listOf(schedule),
            warnings = emptyList()
        )

        return BeamDetailingData(
            mark = "$prefix-B$beamId",
            spanMm = input.spanMm, widthMm = result.width, depthMm = result.depth,
            coverMm = input.coverMm, supportTypeName = input.supportTypeName,
            bottomBarsMark = bottomMark, topBarsMark = topMark,
            extraTopMark = extraTopMark, extraBottomMark = null,
            bottomCutLengthMm = bottomCut, topCutLengthMm = topCut,
            stirrupZones = zones, stirrupHookMm = hook.value, stirrupCutLengthMm = stirrupCut,
            development = dev, lap = lap,
            anchorage = AnchorageLengthMm(dev.value),
            schedule = schedule, calcStrip = strip, drawing = drawing
        )
    }

    private fun round50(v: Double) = kotlin.math.ceil(v / 50.0) * 50.0
}
