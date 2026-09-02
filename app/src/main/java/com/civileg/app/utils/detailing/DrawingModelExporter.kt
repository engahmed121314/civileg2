package com.civileg.app.utils.detailing

import com.civileg.app.utils.detailing.CadLineTypes.CONTINUOUS
import kotlin.math.min
import com.civileg.app.utils.detailing.TitleBlock as AppTitleBlock
import com.civileg.core.calculations.entities.BeamSectionGeometry
import com.civileg.core.calculations.entities.BeamElevationGeometry
import com.civileg.core.calculations.entities.ColumnSectionGeometry
import com.civileg.core.calculations.entities.DrawingModel
import com.civileg.core.calculations.entities.DrawingModelBuilder
import com.civileg.core.calculations.entities.FootingSectionGeometry
import com.civileg.core.calculations.entities.FrameGeometry
import com.civileg.core.calculations.entities.FrameMemberMark
import com.civileg.core.calculations.entities.SeismicChartGeometry
import com.civileg.core.calculations.entities.SectionBar
import com.civileg.core.calculations.entities.RetainingWallSectionGeometry
import com.civileg.core.calculations.entities.ShearWallSectionGeometry
import com.civileg.core.calculations.entities.SteelMemberMark
import com.civileg.core.calculations.entities.SteelSectionGeometry
import com.civileg.core.calculations.entities.SlabSectionGeometryOneWay
import com.civileg.core.calculations.entities.SlabSectionGeometryFlat
import com.civileg.core.calculations.entities.SlabSectionGeometryTwoWay
import com.civileg.core.calculations.entities.StairSectionGeometry
import com.civileg.core.calculations.entities.StirrupGeometry
import com.civileg.core.calculations.entities.TankSectionGeometry
import com.civileg.core.calculations.entities.TitleBlock as CoreTitleBlock
import com.civileg.core.calculations.entities.DrawingStatus as CoreStatus
import com.civileg.core.engineering.CheckStatus

// ─────────────────────────────────────────────────────────────────────────────
// DRAWING MODEL → CAD BRIDGE
//
// Sole bridge between the core DrawingModel (single source of truth) and the
// app-side CAD pipeline.  No geometry is recomputed here — every primitive is
// derived from the model.  Real-space mm output; [DxfWriter] consumes paper
// space, so [writeDxf] applies the scale + translation from [ScaleEngine].
// ─────────────────────────────────────────────────────────────────────────────

data class CadDrawingResult(
    val entities: List<CadEntity>,
    val titleBlock: AppTitleBlock,
    val scale: DrawingScale
)

object DrawingModelExporter {

    private const val MARGIN_MM = 25.0

    fun toCad(model: DrawingModel): CadDrawingResult {
        require(!DrawingModelBuilder.validate(model).hasInvalid) {
            "DrawingModelExporter: model contains NaN/Inf — validate() must pass"
        }

        val entities = mutableListOf<CadEntity>()
        model.beamSection?.let { entities += beamToCad(it) }
        model.columnSection?.let { entities += columnToCad(it) }
        when (val s = model.slabSection) {
            is SlabSectionGeometryOneWay -> entities += slabOneWayToCad(s)
            is SlabSectionGeometryTwoWay -> entities += slabTwoWayToCad(s)
            is SlabSectionGeometryFlat -> entities += slabFlatToCad(s)
            null -> Unit
        }
        model.footingSection?.let { entities += footingToCad(it) }
        model.stairSection?.let { entities += stairToCad(it) }
        model.tankSection?.let { entities += tankToCad(it) }
        model.retainingWallSection?.let { entities += retainingWallToCad(it) }
        model.shearWallSection?.let { entities += shearWallToCad(it) }
        model.steelSection?.let { entities += steelToCad(it) }
        model.frameGeometry?.let { entities += frameToCad(it) }
        model.seismicChart?.let { entities += seismicToCad(it) }
        model.beamElevation?.let { entities += beamElevationToCad(it) }

        entities += model.dimensions.all.map { dim ->
            CadDimLinear(
                x1 = dim.start.x, y1 = dim.start.y,
                x2 = dim.end.x, y2 = dim.end.y,
                offsetMm = 15.0,
                overrideText = dim.value
            )
        }
        entities += model.annotations.all.map { a ->
            CadText(a.text, a.position.x, a.position.y, heightMm = a.height, layer = a.layer)
        }

        val scale = ScaleEngine.fitDrawingToSheet(
            realWidthMm = sectionWidth(model),
            realHeightMm = sectionHeight(model)
        ).scale

        return CadDrawingResult(entities = entities, titleBlock = model.titleBlock.toApp(), scale = scale)
    }

    fun writeDxf(
        model: DrawingModel,
        paperWidthMm: Double = 420.0,
        paperHeightMm: Double = 297.0
    ): String {
        val cad = toCad(model)
        val ratio = cad.scale.ratio
        val factor = 1.0 / ratio
        val b = model.bounds
        val dx = MARGIN_MM - b.minX * factor
        val dy = MARGIN_MM - b.minY * factor
        val scaled = cad.entities.map { it.applied(factor, dx, dy) }
        return DxfWriter().write(scaled, cad.titleBlock, paperWidthMm, paperHeightMm)
    }

    /**
     * P043 — single-sheet live export. Section (+ dimensions + annotations) is
     * scaled into the lower drawing area, the sheet table occupies the reserved
     * band at the top-right, and the title block's SCALE field is filled with
     * the scale actually chosen. The table is the grouped bar schedule
     * ([barScheduleTable]) for reinforced members and the steel member schedule
     * ([steelMemberScheduleTable]) for steel members. Everything (section, dims,
     * table rows) is derived from [model] — nothing is recomputed here.
     */
    fun writeDxfWithSchedule(
        model: DrawingModel,
        paperWidthMm: Double = 420.0,
        paperHeightMm: Double = 297.0
    ): String {
        require(!DrawingModelBuilder.validate(model).hasInvalid) {
            "DrawingModelExporter: model contains NaN/Inf — validate() must pass"
        }

        val table = sheetTable(model)
        val bandH = min(table.totalHeightMm, paperHeightMm / 2.0)
        val gap = 10.0

        val availW = paperWidthMm - 2 * MARGIN_MM
        val availH = paperHeightMm - 2 * MARGIN_MM - bandH - gap

        val b = model.bounds
        val realW = (b.maxX - b.minX).coerceAtLeast(1.0)
        val realH = (b.maxY - b.minY).coerceAtLeast(1.0)

        val scale = DrawingScale.values()
            .filter { it != DrawingScale.NTS }
            .sortedBy { it.ratio }
            .firstOrNull { it.toPaper(realW) <= availW && it.toPaper(realH) <= availH }
            ?: DrawingScale.S_1_100

        val factor = 1.0 / scale.ratio
        val dx = MARGIN_MM - b.minX * factor
        val dy = MARGIN_MM - b.minY * factor

        val cad = toCad(model)
        val scaled = cad.entities.map { it.applied(factor, dx, dy) }

        val tableSheet = table.copy(
            x = paperWidthMm - MARGIN_MM - table.totalWidthMm,
            y = paperHeightMm - MARGIN_MM - bandH
        )

        val titleBlock = cad.titleBlock.copy(scale = scale.label)
        val stamp = failStampEntities(model, paperWidthMm, paperHeightMm)
        return DxfWriter().write(scaled + tableSheet + stamp, titleBlock, paperWidthMm, paperHeightMm)
    }

    /**
     * P2-11 — an unsafe design is stamped, never silently issued.
     * When [DrawingModel.state] overallStatus is FAIL the sheet carries a
     * red, rotated "NOT SAFE - DESIGN FAILS" watermark placed in PAPER space
     * (fixed on the sheet regardless of the drawing scale). EN-only per ADR-009.
     */
    internal fun failStampEntities(model: DrawingModel, paperWidthMm: Double, paperHeightMm: Double): List<CadEntity> {
        if (model.state.overallStatus != CheckStatus.FAIL) return emptyList()
        val cx = paperWidthMm * 0.5
        val cy = paperHeightMm * 0.46
        val boxW = 120.0
        val boxH = 20.0
        return listOf(
            CadPolyline(
                points = listOf(
                    Pt(cx - boxW / 2, cy - boxH / 2), Pt(cx + boxW / 2, cy - boxH / 2),
                    Pt(cx + boxW / 2, cy + boxH / 2), Pt(cx - boxW / 2, cy + boxH / 2)
                ),
                closed = true,
                layer = CadLayers.TEXT,
                color = 1,
                lineWeight = 25
            ),
            CadText(
                text = "NOT SAFE - DESIGN FAILS",
                x = cx + 2.0,
                y = cy - 2.0,
                heightMm = 7.0,
                rotation = 0.0,
                hJustify = 1,
                vJustify = 2,
                layer = CadLayers.TEXT,
                color = 1
            ),
            CadText(
                text = "DO NOT ISSUE FOR CONSTRUCTION",
                x = cx + 2.0,
                y = cy - 12.0,
                heightMm = 3.2,
                rotation = 0.0,
                hJustify = 1,
                vJustify = 2,
                layer = CadLayers.WARNING,
                color = 1
            )
        )
    }

    // ── Sheet table — frames carry a member schedule, steel members a steel
    //    schedule, else the BBS ────────────────────────────────────────────

    internal fun sheetTable(model: DrawingModel): CadTable =
        model.seismicChart?.let { seismicParameterTable(it) }
            ?: if (model.frameMembers.isNotEmpty()) frameMemberScheduleTable(model.frameMembers)
            else if (model.steelMembers.isNotEmpty()) steelMemberScheduleTable(model.steelMembers)
            else barScheduleTable(model)

    /** Seismic parameter ledger — base-shear terms from the chart geometry. */
    internal fun seismicParameterTable(g: SeismicChartGeometry): CadTable {
        val headers = listOf(
            CadTableCell("PARAMETER", 82.0),
            CadTableCell("VALUE", 40.0)
        )
        fun num(v: Double, dec: Int) =
            String.format(java.util.Locale.US, "%.${dec}f", v).trimEnd('0').trimEnd('.')
        val rows = listOf(
            listOf("BASE SHEAR V (kN)", fmtMm(g.baseShearKn)),
            listOf("FUND. PERIOD T1 (s)", num(g.fundamentalPeriod, 3)),
            listOf("SPECTRAL Sa(T1) (g)", num(g.spectralAccel, 4)),
            listOf("ZONE FACTOR Z", num(g.zoneFactor, 3)),
            listOf("SOIL FACTOR S", num(g.soilFactor, 2)),
            listOf("IMPORTANCE I", num(g.importanceFactor, 2)),
            listOf("REDUCTION R", num(g.responseModification, 1)),
            listOf("OVERALL", if (g.isSafe) "OK" else "NOT OK")
        )
        return CadTable(x = 0.0, y = 0.0, headers = headers, rows = rows)
    }

    internal fun frameMemberScheduleTable(members: List<FrameMemberMark>): CadTable {
        val headers = listOf(
            CadTableCell("MARK", 32.0),
            CadTableCell("MEMBER", 38.0),
            CadTableCell("SECTION", 70.0),
            CadTableCell("LENGTH (mm)", 46.0),
            CadTableCell("QTY", 16.0)
        )
        val rows = members.map { m ->
            listOf(m.mark, m.memberType, m.sectionName, fmtMm(m.lengthMm), m.quantity.toString())
        }
        return CadTable(x = 0.0, y = 0.0, headers = headers, rows = rows)
    }

    internal fun steelMemberScheduleTable(members: List<SteelMemberMark>): CadTable {
        val headers = listOf(
            CadTableCell("MARK", 40.0),
            CadTableCell("SECTION", 70.0),
            CadTableCell("LENGTH (mm)", 50.0),
            CadTableCell("QTY", 16.0)
        )
        val rows = members.map { m ->
            listOf(m.mark, m.sectionName, fmtMm(m.lengthMm), m.quantity.toString())
        }
        return CadTable(x = 0.0, y = 0.0, headers = headers, rows = rows)
    }

    // ── Bar schedule (BBS) — grouped so count-driven mesh rows collapse ─────

    internal fun barScheduleTable(model: DrawingModel): CadTable {
        val headers = listOf(
            CadTableCell("MARK", 36.0),
            CadTableCell("Ø", 14.0),
            CadTableCell("LENGTH (mm)", 50.0),
            CadTableCell("QTY", 14.0),
            CadTableCell("SPACING", 34.0)
        )

        data class GroupKey(val diameter: Double, val lengthMm: Double, val spacing: Double?, val element: String)

        val rows = model.reinforcement.all
            .groupBy { GroupKey(it.diameter, it.totalLengthMm, it.spacing, it.element) }
            .values
            .map { bars ->
                val b = bars.first()
                listOf(
                    b.mark,
                    "Ø${fmtMm(b.diameter)}",
                    fmtMm(b.totalLengthMm),
                    bars.size.toString(),
                    b.spacing?.let { "@${fmtMm(it)}" } ?: "-"
                )
            }
            .sortedWith(compareBy({ diamOf(it[1]) }, { it[3].toIntOrNull() ?: 0 }))

        return CadTable(x = 0.0, y = 0.0, headers = headers, rows = rows)
    }

    private fun diamOf(text: String): Double = text.trim('Ø', '0', '.').toDoubleOrNull() ?: 0.0

    private fun fmtMm(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(java.util.Locale.US, "%.1f", v).trimEnd('0').trimEnd('.')

    /** Compact numeric label — trailing zeros trimmed (chart ticks). */
    private fun fmtN(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    // ── Sections ────────────────────────────────────────────────────────────

    private fun beamToCad(g: BeamSectionGeometry): List<CadEntity> {
        val out = mutableListOf<CadEntity>()
        out += rect(g.sectionBounds, CadLayers.CONC)
        out += CadHatch(listOf(HatchBoundaryLoop(corners(g.sectionBounds))), patternName = "AR-CONC")
        out += bars(g.tensionBars, g.sectionBounds, g.concreteCover, layer = CadLayers.REBAR)
        out += bars(g.compressionBars, g.sectionBounds, g.concreteCover, layer = CadLayers.REBAR)
        out += stirrups(g.stirrups, g.sectionBounds, g.concreteCover)
        return out
    }

    private fun columnToCad(g: ColumnSectionGeometry): List<CadEntity> {
        val out = mutableListOf<CadEntity>()
        out += rect(g.sectionBounds, CadLayers.COLUMN)
        out += CadHatch(listOf(HatchBoundaryLoop(corners(g.sectionBounds))), patternName = "AR-CONC")
        out += bars(g.coreBars, g.sectionBounds, g.concreteCover, layer = CadLayers.REBAR)
        out += bars(g.outerBars, g.sectionBounds, g.concreteCover, layer = CadLayers.REBAR)
        out += stirrups(g.ties, g.sectionBounds, g.concreteCover)
        return out
    }

    private fun slabOneWayToCad(g: SlabSectionGeometryOneWay): List<CadEntity> {
        val out = mutableListOf<CadEntity>()
        out += rect(g.sectionBounds, CadLayers.CONC)
        out += bars(g.topBars, g.sectionBounds, g.concreteCover, layer = CadLayers.REBAR)
        out += bars(g.bottomBars, g.sectionBounds, g.concreteCover, layer = CadLayers.REBAR)
        return out
    }

    private fun slabTwoWayToCad(g: SlabSectionGeometryTwoWay): List<CadEntity> {
        val out = mutableListOf<CadEntity>()
        out += rect(g.sectionBounds, CadLayers.CONC)
        out += bars(g.shortTopBars, g.sectionBounds, g.concreteCover, layer = CadLayers.REBAR)
        out += bars(g.shortBottomBars, g.sectionBounds, g.concreteCover, layer = CadLayers.REBAR)
        return out
    }

    /**
     * Flat-slab panel section: panel bounds (long × short) + the four strip
     * rebar groups. Layout device: the panel is split along the long span at
     * the ENGINE's column-strip band width (columnStripWidthMm, clamped into
     * the panel) so column-strip bars render in the left zone and middle-strip
     * bars in the right zone, each as top/bottom face rows like the slab
     * emitters. A thin drop-panel outline (input dropSize × dropDepth) is
     * drawn centred on the top face when both are present — schematic.
     */
    private fun slabFlatToCad(g: SlabSectionGeometryFlat): List<CadEntity> {
        val out = mutableListOf<CadEntity>()
        out += rect(g.sectionBounds, CadLayers.CONC)
        val totalW = g.sectionBounds.maxX - g.sectionBounds.minX
        val colW = g.columnStripWidthMm.coerceIn(0.0, totalW)
        val colBox = com.civileg.core.calculations.entities.BoundingBox(
            g.sectionBounds.minX, g.sectionBounds.minY,
            g.sectionBounds.minX + colW, g.sectionBounds.maxY
        )
        val midBox = com.civileg.core.calculations.entities.BoundingBox(
            g.sectionBounds.minX + colW, g.sectionBounds.minY,
            g.sectionBounds.maxX, g.sectionBounds.maxY
        )
        out += bars(g.columnStripTopBars, colBox, g.concreteCover, layer = CadLayers.REBAR)
        out += bars(g.columnStripBottomBars, colBox, g.concreteCover, layer = CadLayers.REBAR)
        out += bars(g.middleStripTopBars, midBox, g.concreteCover, layer = CadLayers.REBAR)
        out += bars(g.middleStripBottomBars, midBox, g.concreteCover, layer = CadLayers.REBAR)
        if (g.dropDepth > 0 && g.dropSize > 0) {
            val cx = (g.sectionBounds.minX + g.sectionBounds.maxX) / 2.0
            out += CadPolyline(
                points = corners(
                    com.civileg.core.calculations.entities.BoundingBox(
                        cx - g.dropSize / 2.0, g.sectionBounds.maxY - g.dropDepth,
                        cx + g.dropSize / 2.0, g.sectionBounds.maxY
                    )
                ),
                closed = true,
                layer = CadLayers.CONC,
                lineWeight = 18
            )
        }
        return out
    }

    private fun footingToCad(g: FootingSectionGeometry): List<CadEntity> {
        val out = mutableListOf<CadEntity>()
        out += rect(g.sectionBounds, CadLayers.FOOTING)
        out += CadHatch(listOf(HatchBoundaryLoop(corners(g.sectionBounds))), patternName = "AR-CONC")
        out += bars(g.bottomBars, g.sectionBounds, g.concreteCover, layer = CadLayers.REBAR)
        out += bars(g.topBars, g.sectionBounds, g.concreteCover, layer = CadLayers.REBAR)
        return out
    }

    private fun stairToCad(g: StairSectionGeometry): List<CadEntity> {
        val out = mutableListOf<CadEntity>()
        out += rect(g.sectionBounds, CadLayers.CONC)
        out += CadHatch(listOf(HatchBoundaryLoop(corners(g.sectionBounds))), patternName = "AR-CONC")
        out += bars(g.mainBars, g.sectionBounds, g.concreteCover, layer = CadLayers.REBAR)
        out += bars(g.distributionBars, g.sectionBounds, g.concreteCover, layer = CadLayers.REBAR)
        out += stirrups(g.stirrups, g.sectionBounds, g.concreteCover)
        return out
    }

    private fun tankToCad(g: TankSectionGeometry): List<CadEntity> {
        val out = mutableListOf<CadEntity>()
        out += rect(g.sectionBounds, CadLayers.WALL)
        out += CadHatch(listOf(HatchBoundaryLoop(corners(g.sectionBounds))), patternName = "AR-CONC")
        out += bars(g.wallVerticalBars, g.sectionBounds, g.concreteCover, layer = CadLayers.REBAR)
        out += bars(g.wallHorizontalBars, g.sectionBounds, g.concreteCover, layer = CadLayers.REBAR)
        out += bars(g.baseBars, g.sectionBounds, g.concreteCover, layer = CadLayers.REBAR)
        return out
    }

    private fun retainingWallToCad(g: RetainingWallSectionGeometry): List<CadEntity> {
        val out = mutableListOf<CadEntity>()
        out += rect(g.sectionBounds, CadLayers.WALL)
        out += CadHatch(listOf(HatchBoundaryLoop(corners(g.sectionBounds))), patternName = "AR-CONC")
        out += bars(g.stemMainBars, g.sectionBounds, g.concreteCover, layer = CadLayers.REBAR)
        out += bars(g.distributionBars, g.sectionBounds, g.concreteCover, layer = CadLayers.REBAR)
        out += bars(g.toeBars, g.sectionBounds, g.concreteCover, layer = CadLayers.REBAR)
        out += bars(g.heelBars, g.sectionBounds, g.concreteCover, layer = CadLayers.REBAR)
        return out
    }

    /**
     * Shear-wall horizontal section: wall box (length × thickness) + a schematic
     * flange leg for L/T walls, the longitudinal steel distributed along the
     * length at the thickness centre, the horizontal (shear) steel as face rows
     * near both thickness faces, and boundary-element end zones with their
     * confinement tie loops when the engine demands a boundary element.
     */
    private fun shearWallToCad(g: ShearWallSectionGeometry): List<CadEntity> {
        val out = mutableListOf<CadEntity>()
        val wallBox = com.civileg.core.calculations.entities.BoundingBox(
            g.sectionBounds.minX, g.sectionBounds.minY,
            g.wallLength, g.wallThickness
        )
        out += rect(wallBox, CadLayers.WALL)
        out += CadHatch(listOf(HatchBoundaryLoop(corners(wallBox))), patternName = "AR-CONC")

        g.flange?.let { f ->
            val flangeBox = if (f.shape == "L-shaped") {
                com.civileg.core.calculations.entities.BoundingBox(
                    g.sectionBounds.minX, g.wallThickness - f.thicknessMm,
                    0.0, g.wallThickness
                )
            } else {
                com.civileg.core.calculations.entities.BoundingBox(
                    (g.wallLength - f.projectionMm) / 2.0, g.wallThickness,
                    (g.wallLength + f.projectionMm) / 2.0, g.wallThickness + f.thicknessMm
                )
            }
            out += CadPolyline(points = corners(flangeBox), closed = true, layer = CadLayers.WALL, lineWeight = 18)
        }

        out += bars(g.webVerticalBars, wallBox, g.concreteCover, layer = CadLayers.REBAR)
        out += bars(g.horizontalFaceBars, wallBox, g.concreteCover, layer = CadLayers.REBAR)

        // Boundary end zones + confinement ties (mirrors the renderer's zone loops).
        if (g.boundaryElementLengthMm > 0 && g.boundaryTies.isNotEmpty()) {
            val beLen = min(g.boundaryElementLengthMm, g.wallLength / 2.0).coerceAtLeast(1.0)
            val zones = listOf(0.0, g.wallLength - beLen)
            g.boundaryTies.forEach { tie ->
                val step = (beLen / maxOf(tie.spacing, 1.0)).toInt().coerceAtLeast(2)
                for (zoneStart in zones) {
                    out += CadPolyline(
                        points = corners(com.civileg.core.calculations.entities.BoundingBox(
                            zoneStart, g.wallThickness - g.concreteCover,
                            zoneStart + beLen, g.concreteCover
                        )),
                        closed = true, layer = CadLayers.STIRRUP
                    )
                    val cell = beLen / step
                    for (i in 0 until step) {
                        val x1 = zoneStart + i * cell + 2.0
                        val x2 = zoneStart + (i + 1) * cell - 2.0
                        if (x2 > x1 + 1.0) {
                            out += CadPolyline(
                                points = corners(com.civileg.core.calculations.entities.BoundingBox(
                                    x1, g.wallThickness - g.concreteCover - 2.0,
                                    x2, g.concreteCover + 2.0
                                )),
                                closed = true, layer = CadLayers.STIRRUP,
                                lineWeight = 18
                            )
                        }
                    }
                }
            }
        }
        return out
    }

    /**
     * Steel member elevation + cut A-A, mirroring the on-screen renderer's
     * ProfessionalSteelDrawing conventions: a long elevation box (length ×
     * depth) with flange-interface lines and a centreline; the A-A cut sits in
     * the reserved gap right of the elevation and shows the profile plates
     * (top flange / web / bottom flange) bounded by the engine's depth/width/
     * web/flange quantities. The plate faces are hatched ANSI31 (steel). Steel
     * carries no reinforcing bars, so no rebar/stirrup entities are emitted.
     */
    private fun steelToCad(g: SteelSectionGeometry): List<CadEntity> {
        val out = mutableListOf<CadEntity>()
        val L = maxOf(g.memberLengthMm, 1.0)
        val d = maxOf(g.depthMm, 1.0)
        val w = maxOf(g.widthMm, 1.0)
        val tw = g.webThicknessMm.coerceIn(1.0, d / 2.0)
        val tf = g.flangeThicknessMm.coerceIn(1.0, d / 2.0)
        val cx = g.sectionBounds.maxX - w / 2.0

        // Elevation: outer box + flange-interface lines + centreline.
        out += rect(com.civileg.core.calculations.entities.BoundingBox(0.0, 0.0, L, d), CadLayers.STEEL)
        out += CadLine(0.0, tf, L, tf, layer = CadLayers.STEEL)
        out += CadLine(0.0, d - tf, L, d - tf, layer = CadLayers.STEEL)
        out += CadCenterLine(-5.0, d / 2.0, L + 5.0, d / 2.0)

        // Cut A-A: top flange / web / bottom flange plates, hatched steel.
        val plates = listOf(
            com.civileg.core.calculations.entities.BoundingBox(cx - w / 2.0, d - tf, cx + w / 2.0, d),
            com.civileg.core.calculations.entities.BoundingBox(cx - tw / 2.0, tf, cx + tw / 2.0, d - tf),
            com.civileg.core.calculations.entities.BoundingBox(cx - w / 2.0, 0.0, cx + w / 2.0, tf)
        )
        plates.forEach { plate ->
            out += rect(plate, CadLayers.STEEL)
            out += CadHatch(listOf(HatchBoundaryLoop(corners(plate))), patternName = "ANSI31")
        }
        out += CadCenterLine(cx, -5.0, cx, d + 5.0)
        return out
    }

    /**
     * Frame elevation: member rectangles around their centrelines (columns
     * thick, beams thin — outlines pre-deriving in [FrameMemberGeometry]),
     * a centreline per member, the base support symbols (mirroring the canvas
     * drawSupportLarge conventions — fixed = base plate + bolts, pin/roller =
     * hatched triangle + plate) and a ground/soil line with foundation hatch
     * along the base level. Everything is derived from the model carriers;
     * nothing is recomputed here.
     */
    private fun frameToCad(g: FrameGeometry): List<CadEntity> {
        val out = mutableListOf<CadEntity>()
        g.members.forEach { m ->
            val layer = if (m.materialType.equals("STEEL", true)) CadLayers.STEEL else CadLayers.CONC
            out += CadPolyline(points = m.outline.map { Pt(it.x, it.y) }, closed = true, layer = layer)
            out += CadCenterLine(m.start.x, m.start.y, m.end.x, m.end.y)
        }
        // Ground line + foundation hatch along the base level (y = 0).
        out += CadLine(0.0, 0.0, g.totalSpanMm, 0.0, layer = CadLayers.SOIL)
        out += CadHatch(
            listOf(HatchBoundaryLoop(
                listOf(Pt(0.0, -8.0), Pt(g.totalSpanMm, -8.0), Pt(g.totalSpanMm, 0.0), Pt(0.0, 0.0))
            )),
            patternName = "ANSI31", layer = CadLayers.SOIL
        )
        g.supports.forEach { s -> out += supportSymbol(s) }
        return out
    }

    /** Base support symbols — mirror the canvas drawSupportLarge conventions. */
    private fun supportSymbol(s: com.civileg.core.calculations.entities.FrameSupportGeometry): List<CadEntity> {
        val out = mutableListOf<CadEntity>()
        val hw = s.halfWidthMm.coerceAtLeast(20.0)
        when {
            s.supportType.equals("PIN", true) || s.supportType.equals("ROLLER", true) -> {
                val baseY = s.yMm - hw
                out += CadPolyline(
                    points = listOf(Pt(s.xMm, s.yMm), Pt(s.xMm - hw, baseY), Pt(s.xMm + hw, baseY)),
                    closed = true, layer = CadLayers.FOUNDATION
                )
                out += CadLine(s.xMm - hw - 6.0, baseY, s.xMm + hw + 6.0, baseY, layer = CadLayers.FOUNDATION)
                for (i in -2..2) {
                    val hx = s.xMm + i * (hw / 2.2)
                    out += CadLine(hx, baseY, hx - 6.0, baseY - 10.0, layer = CadLayers.FOUNDATION)
                }
            }
            else -> {
                // FIXED / VERTICAL_ROLLER — base plate + anchor bolts.
                out += CadPolyline(
                    points = listOf(
                        Pt(s.xMm - hw, s.yMm), Pt(s.xMm + hw, s.yMm),
                        Pt(s.xMm + hw, s.yMm + 8.0), Pt(s.xMm - hw, s.yMm + 8.0)
                    ),
                    closed = true, layer = CadLayers.FOUNDATION
                )
                for (i in -2..2) {
                    val hx = s.xMm + i * (hw / 2.2)
                    out += CadLine(hx, s.yMm + 8.0, hx - 6.0, s.yMm + 16.0, layer = CadLayers.FOUNDATION)
                }
            }
        }
        return out
    }

    /**
     * P043H — seismic chart panes. Draws the two plot boxes (GRID), the
     * spectrum axes with dashed tick gridlines, the response-spectrum curve
     * (ANALYSIS polyline), the design-period centreline + dot marker, and the
     * per-floor lateral-force bars (LOAD). Everything is taken from the model's
     * chart geometry — points/bars are already normalized by the builder.
     */
    private fun seismicToCad(g: SeismicChartGeometry): List<CadEntity> {
        val out = mutableListOf<CadEntity>()
        val sb = g.spectrumBox
        val fb = g.forceBox

        out += rect(g.spectrumBox, CadLayers.GRID)
        out += rect(g.forceBox, CadLayers.GRID)

        // Spectrum axes + arrowheads
        out += CadLine(sb.minX, sb.minY, sb.maxX, sb.minY, layer = CadLayers.ANALYSIS)
        out += CadLine(sb.minX, sb.minY, sb.minX, sb.maxY, layer = CadLayers.ANALYSIS)
        out += CadArrow(sb.maxX, sb.minY, 0.0, layer = CadLayers.ANALYSIS)
        out += CadArrow(sb.minX, sb.maxY, 90.0, layer = CadLayers.ANALYSIS)

        // Dashed gridlines — T every 0.5 s, Sa in quarter divisions
        val tStep = if (g.maxPeriod >= 1.0) 0.5 else 0.25
        var t = tStep
        while (t < g.maxPeriod - 1e-9) {
            val x = sb.minX + (t / g.maxPeriod) * (sb.maxX - sb.minX)
            out += CadLine(x, sb.minY, x, sb.maxY, layer = CadLayers.GRID, lineType = CadLineTypes.DASHED)
            out += CadText(fmtN(t), x, sb.minY - 4.0, heightMm = 2.0, hJustify = 1, layer = CadLayers.TEXT)
            t += tStep
        }
        for (k in 1..3) {
            val y = sb.minY + (k / 4.0) * (sb.maxY - sb.minY)
            out += CadLine(sb.minX, y, sb.maxX, y, layer = CadLayers.GRID, lineType = CadLineTypes.DASHED)
            out += CadText(
                fmtN(g.maxAcceleration * k / 4.0), sb.minX - 2.0, y - 1.0,
                heightMm = 2.0, hJustify = 2, layer = CadLayers.TEXT
            )
        }

        if (g.spectrumPoints.size >= 2) {
            out += CadPolyline(
                points = g.spectrumPoints.map { Pt(it.xMm, it.yMm) },
                closed = false, layer = CadLayers.ANALYSIS
            )
        }

        if (g.fundamentalPeriod > 0.0 && g.fundamentalPeriod <= g.maxPeriod) {
            val x = sb.minX + (g.fundamentalPeriod / g.maxPeriod) * (sb.maxX - sb.minX)
            val y = sb.minY + (g.spectralAccel.coerceAtLeast(0.0) / g.maxAcceleration) * (sb.maxY - sb.minY)
            out += CadLine(x, sb.minY, x, sb.maxY, layer = CadLayers.CENTER, lineType = CadLineTypes.CENTER)
            out += CadCircle(x, y, 3.0, layer = CadLayers.ANALYSIS)
        }

        // Force-distribution pane — axes + per-floor bars
        out += CadLine(fb.minX, fb.minY, fb.maxX, fb.minY, layer = CadLayers.ANALYSIS)
        out += CadLine(fb.minX, fb.minY, fb.minX, fb.maxY, layer = CadLayers.ANALYSIS)
        out += CadArrow(fb.maxX, fb.minY, 0.0, layer = CadLayers.ANALYSIS)
        out += CadArrow(fb.minX, fb.maxY, 90.0, layer = CadLayers.ANALYSIS)
        g.forceBars.forEach { b ->
            val y0 = b.floorHeightMm - b.barHalfMm
            val y1 = b.floorHeightMm + b.barHalfMm
            out += CadPolyline(
                points = listOf(
                    Pt(fb.minX, y0), Pt(fb.minX + b.barLengthMm, y0),
                    Pt(fb.minX + b.barLengthMm, y1), Pt(fb.minX, y1)
                ),
                closed = true, layer = CadLayers.LOAD
            )
        }
        return out
    }

    // ── Beam elevation (support-cases matrix) ────────────────────────────────

    private fun beamElevationToCad(g: BeamElevationGeometry): List<CadEntity> {
        val out = mutableListOf<CadEntity>()

        out += rect(g.beamBox, CadLayers.CONC)
        out += CadHatch(
            listOf(HatchBoundaryLoop(corners(g.beamBox))),
            patternName = "AR-CONC", layer = CadLayers.CONC_HATCH
        )

        g.supports.forEach { s ->
            val h = s.symbolHeightMm
            val baseY = s.soffitY + h
            when (s.kind) {
                "PIN" -> {
                    out += CadPolyline(
                        points = listOf(Pt(s.xMm, s.soffitY), Pt(s.xMm - 9.0, baseY), Pt(s.xMm + 9.0, baseY)),
                        closed = true, layer = CadLayers.FOUNDATION
                    )
                    out += CadLine(s.xMm - 14.0, baseY, s.xMm + 14.0, baseY, layer = CadLayers.FOUNDATION)
                    out += groundTicks(s.xMm, baseY)
                }
                "ROLLER" -> {
                    out += CadPolyline(
                        points = listOf(Pt(s.xMm, s.soffitY), Pt(s.xMm - 9.0, baseY), Pt(s.xMm + 9.0, baseY)),
                        closed = true, layer = CadLayers.FOUNDATION
                    )
                    out += CadLine(s.xMm - 14.0, baseY, s.xMm + 14.0, baseY, layer = CadLayers.FOUNDATION)
                    out += CadCircle(s.xMm - 4.5, baseY, 2.2, layer = CadLayers.FOUNDATION)
                    out += CadCircle(s.xMm + 4.5, baseY, 2.2, layer = CadLayers.FOUNDATION)
                    out += groundTicks(s.xMm, baseY)
                }
                "FIXED" -> {
                    out += CadPolyline(
                        points = listOf(
                            Pt(s.xMm - 8.0, s.soffitY), Pt(s.xMm + 8.0, s.soffitY),
                            Pt(s.xMm + 8.0, baseY), Pt(s.xMm - 8.0, baseY)
                        ),
                        closed = true, layer = CadLayers.FOUNDATION
                    )
                    out += CadHatch(
                        listOf(
                            HatchBoundaryLoop(
                                corners(
                                    com.civileg.core.calculations.entities.BoundingBox(
                                        s.xMm - 8.0, s.soffitY, s.xMm + 8.0, baseY
                                    )
                                )
                            )
                        ),
                        patternName = "ANSI31", layer = CadLayers.FOUNDATION
                    )
                    out += CadLine(s.xMm - 14.0, baseY, s.xMm + 14.0, baseY, layer = CadLayers.FOUNDATION)
                    out += groundTicks(s.xMm, baseY)
                }
                else -> Unit
            }
        }

        g.loadArrows.forEach { a ->
            out += CadLine(a.xMm, a.shaftTopY, a.xMm, a.headY, layer = CadLayers.LOAD)
            val wingY = a.headY - 9.0
            out += CadPolyline(
                points = listOf(Pt(a.xMm - 3.0, wingY), Pt(a.xMm + 3.0, wingY), Pt(a.xMm, a.headY)),
                closed = true, layer = CadLayers.LOAD
            )
        }

        // R2 (P044): stirrup distribution along the member — vertical ticks are
        // placed verbatim from the engine's confinement zones (dense at support,
        // wider at mid). Layout-only; count/spacing are passthrough values.
        if (g.stirrupZones.isNotEmpty() && g.spanMm > 0.0) {
            val bw = g.beamBox.maxX - g.beamBox.minX
            val innerTop = g.beamBox.maxY - 4.0
            val innerBottom = g.beamBox.minY + 4.0
            g.stirrupZones.forEach { zone ->
                val spacing = zone.spacing.coerceAtLeast(50.0)
                val zStart = g.beamBox.minX + (zone.startLocation / g.spanMm).coerceIn(0.0, 1.0) * bw
                val zEnd = g.beamBox.minX + (zone.endLocation / g.spanMm).coerceIn(0.0, 1.0) * bw
                var x = zStart
                var guard = 0
                while (x <= zEnd && guard < 400) {
                    out += CadLine(x, innerBottom, x, innerTop, layer = CadLayers.STIRRUP)
                    x += spacing / g.spanMm * bw
                    guard++
                }
            }
        }

        out += CadText(g.captionTop, g.captionX, g.captionTopY, heightMm = 3.0, layer = CadLayers.TEXT)
        out += CadText(g.captionBottom, g.captionX, g.captionBottomY, heightMm = 3.0, layer = CadLayers.TEXT)

        val mb = (g.momentPane.minY + g.momentPane.maxY) / 2.0
        out += rect(g.momentPane, CadLayers.GRID)
        out += CadLine(g.momentPane.minX, mb, g.momentPane.maxX, mb, layer = CadLayers.ANALYSIS)
        out += CadArrow(g.momentPane.maxX, mb, 0.0, layer = CadLayers.ANALYSIS)
        out += CadArrow(g.momentPane.minX, g.momentPane.maxY, 90.0, layer = CadLayers.ANALYSIS)
        out += CadText("M (kN.m)", g.momentPane.minX - 1.0, g.momentPane.maxY - 1.0,
            heightMm = 2.5, hJustify = 2, layer = CadLayers.TEXT)
        if (g.momentCurve.size >= 2) {
            out += CadPolyline(g.momentCurve.map { Pt(it.xMm, it.yMm) },
                closed = false, layer = CadLayers.ANALYSIS)
        }
        diagramPeakLabel(g, moment = true)?.let { out += it }

        val sb = (g.shearPane.minY + g.shearPane.maxY) / 2.0
        out += rect(g.shearPane, CadLayers.GRID)
        out += CadLine(g.shearPane.minX, sb, g.shearPane.maxX, sb, layer = CadLayers.ANALYSIS)
        out += CadArrow(g.shearPane.maxX, sb, 0.0, layer = CadLayers.ANALYSIS)
        out += CadArrow(g.shearPane.minX, g.shearPane.maxY, 90.0, layer = CadLayers.ANALYSIS)
        out += CadText("V (kN)", g.shearPane.minX - 1.0, g.shearPane.maxY - 1.0,
            heightMm = 2.5, hJustify = 2, layer = CadLayers.TEXT)
        if (g.shearCurve.size >= 2) {
            out += CadPolyline(g.shearCurve.map { Pt(it.xMm, it.yMm) },
                closed = false, layer = CadLayers.ANALYSIS)
        }
        diagramPeakLabel(g, moment = false)?.let { out += it }

        return out
    }

    /** Ground-hatch ticks under a support symbol. */
    private fun groundTicks(cx: Double, baseY: Double): List<CadEntity> {
        val out = mutableListOf<CadEntity>()
        for (i in -2..2) {
            val hx = cx + i * 5.5
            out += CadLine(hx, baseY, hx - 6.0, baseY + 9.0, layer = CadLayers.SOIL)
        }
        return out
    }

    /** Peak-value tag "Mmax" / "Vmax" next to the extreme ordinate. */
    private fun diagramPeakLabel(g: BeamElevationGeometry, moment: Boolean): CadEntity? {
        val curve = if (moment) g.momentCurve else g.shearCurve
        val value = if (moment) g.appliedMomentKnM else g.appliedShearKn
        if (curve.size < 2 || !value.isFinite()) return null
        val pane = if (moment) g.momentPane else g.shearPane
        val baseline = (pane.minY + pane.maxY) / 2.0
        val peak = curve.maxByOrNull { kotlin.math.abs(it.yMm - baseline) } ?: return null
        val above = peak.yMm > baseline
        return CadText(
            (if (moment) "Mmax " else "Vmax ") + fmtN(value),
            peak.xMm, peak.yMm + (if (above) 3.5 else -1.5),
            heightMm = 2.2, hJustify = 1, layer = CadLayers.TEXT
        )
    }

    // ── Primitives ──────────────────────────────────────────────────────────

    private fun rect(box: com.civileg.core.calculations.entities.BoundingBox, layer: String): CadPolyline =
        CadPolyline(points = corners(box), closed = true, layer = layer)

    private fun corners(box: com.civileg.core.calculations.entities.BoundingBox): List<Pt> = listOf(
        Pt(box.minX, box.minY), Pt(box.maxX, box.minY),
        Pt(box.maxX, box.maxY), Pt(box.minX, box.maxY)
    )

    private fun bars(bars: List<SectionBar>, box: com.civileg.core.calculations.entities.BoundingBox, cover: Double, layer: String): List<CadEntity> {
        if (bars.isEmpty()) return emptyList()
        val usable = maxOf(box.maxX - box.minX - 2 * cover, bars.size.toDouble())
        val step = usable / (bars.size + 1)
        val xs = (1..bars.size).map { i -> box.minX + cover + i * step }
        return bars.zip(xs).map { (bar, x) ->
            CadCircle(cx = x, cy = bar.position, radius = maxOf(bar.diameter, 2.0) / 2.0, layer = layer)
        }
    }

    private fun stirrups(ties: List<StirrupGeometry>, box: com.civileg.core.calculations.entities.BoundingBox, cover: Double): List<CadEntity> {
        if (ties.isEmpty()) return emptyList()
        return ties.map { tie ->
            CadPolyline(
                points = listOf(
                    Pt(box.minX + cover, box.minY + cover),
                    Pt(box.maxX - cover, box.minY + cover),
                    Pt(box.maxX - cover, box.maxY - cover),
                    Pt(box.minX + cover, box.maxY - cover)
                ),
                closed = true,
                layer = CadLayers.STIRRUP,
                lineWeight = 18
            )
        }
    }

    private fun sectionWidth(model: DrawingModel): Double {
        val w = model.bounds.maxX - model.bounds.minX
        return maxOf(w, 100.0)
    }

    private fun sectionHeight(model: DrawingModel): Double {
        val h = model.bounds.maxY - model.bounds.minY
        return maxOf(h, 100.0)
    }

    private fun CoreTitleBlock.toApp(): AppTitleBlock = AppTitleBlock(
        project = project,
        client = client,
        consultant = consultant,
        drawingTitle = drawingTitle,
        drawingNumber = drawingNumber,
        revision = revision,
        date = date,
        scale = scale,
        designCode = designCode,
        sheet = sheet,
        status = when (status) {
            CoreStatus.PRELIMINARY -> com.civileg.app.utils.detailing.DrawingStatus.PRELIMINARY
            CoreStatus.FOR_REVIEW -> com.civileg.app.utils.detailing.DrawingStatus.FOR_REVIEW
            CoreStatus.FOR_CONSTRUCTION -> com.civileg.app.utils.detailing.DrawingStatus.FOR_CONSTRUCTION
            CoreStatus.AS_BUILT -> com.civileg.app.utils.detailing.DrawingStatus.AS_BUILT
        }
    )
}

private fun CadEntity.applied(factor: Double, dx: Double, dy: Double): CadEntity = when (this) {
    is CadPolyline -> copy(points = points.map { Pt(it.x * factor + dx, it.y * factor + dy) })
    is CadCircle -> copy(cx = cx * factor + dx, cy = cy * factor + dy, radius = radius * factor)
    is CadLine -> copy(
        x1 = x1 * factor + dx, y1 = y1 * factor + dy,
        x2 = x2 * factor + dx, y2 = y2 * factor + dy
    )
    is CadCenterLine -> copy(
        x1 = x1 * factor + dx, y1 = y1 * factor + dy,
        x2 = x2 * factor + dx, y2 = y2 * factor + dy
    )
    is CadDimLinear -> copy(
        x1 = x1 * factor + dx, y1 = y1 * factor + dy,
        x2 = x2 * factor + dx, y2 = y2 * factor + dy,
        offsetMm = offsetMm * factor
    )
    is CadText -> copy(x = x * factor + dx, y = y * factor + dy, heightMm = (heightMm * factor).coerceIn(2.0, 7.0))
    is CadMText -> copy(
        x = x * factor + dx, y = y * factor + dy,
        heightMm = (heightMm * factor).coerceIn(2.0, 7.0),
        widthMm = widthMm * factor
    )
    is CadHatch -> copy(
        boundary = boundary.map { HatchBoundaryLoop(it.points.map { p -> Pt(p.x * factor + dx, p.y * factor + dy) }, it.closed) },
        scale = scale * factor
    )
    is CadLeader -> copy(
        vertices = vertices.map { Pt(it.x * factor + dx, it.y * factor + dy) },
        textHeightMm = (textHeightMm * factor).coerceIn(2.0, 7.0)
    )
    else -> this
}