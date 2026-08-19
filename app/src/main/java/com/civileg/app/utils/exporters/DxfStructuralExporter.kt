package com.civileg.app.utils.exporters

/**
 * DxfStructuralExporter - High-level structural element drawing exporter.
 *
 * Uses [DxfExportEngine] to produce DXF drawings for all major structural
 * element types: beams, columns, slabs, footings, staircases, retaining
 * walls, water tanks, frame analysis diagrams, and steel members.
 *
 * Each draw method creates a complete section/detail drawing with:
 *   - Concrete outlines with section hatching
 *   - Reinforcement bars with labels
 *   - Tie/stirrup details
 *   - Proper dimensioning
 *   - Text annotations
 *   - Center lines
 */
public class DxfStructuralExporter(private val engine: DxfExportEngine) {

    // ============================================================
    // BEAM SECTION EXPORTERS
    // ============================================================

    data class BeamRectInput(
        val width: Double,       // mm
        val depth: Double,       // mm
        val topBars: String,     // e.g. "3#16"
        val bottomBars: String,  // e.g. "4#20"
        val stirrups: String,    // e.g. "T8@150"
        val cover: Double = 40.0,
        val clearCover: Double = 25.0,
        val title: String = "BEAM SECTION",
        val scaleX: Double = 1.0,
        val scaleY: Double = 1.0
    )

    /**
     * Draw a rectangular beam cross-section with reinforcement.
     * Typical layout (elevation view looking at cross-section):
     *
     *   -------------------  <- top
     *   o    o    o         <- top bars
     *       |
     *   =================== <- stirrup
     *       |
     *   o  o o  o           <- bottom bars
     *   -------------------  <- bottom
     */
    fun drawBeamRectSection(input: BeamRectInput, ox: Double = 0.0, oy: Double = 0.0) {
        val w = input.width * input.scaleX
        val d = input.depth * input.scaleY
        val cov = input.cover * input.scaleX
        val cc = input.clearCover * input.scaleY
        val rebarR = 2.0 * Math.max(input.scaleX, input.scaleY)

        // Center the beam
        val cx = ox
        val cy = oy

        // Concrete outline
        engine.addRectangle(cx, cy, w, d, layer = "CONCRETE", color = 7)
        // Section hatching (concrete)
        engine.addRectangleHatch(cx, cy, w, d, patternName = "AR-CONC", scale = 1.0, layer = "HATCH", color = 3)

        // Center lines
        val midX = cx + w / 2
        val midY = cy + d / 2
        engine.addCenterCross(midX, midY, Math.max(w, d) * 0.6)

        // --- Stirrup (rectangle inside concrete) ---
        val sx = cx + cov
        val sy = cy + cc
        val sw = w - 2 * cov
        val sd = d - 2 * cc
        if (sw > 0 && sd > 0) {
            engine.addRectangle(sx, sy, sw, sd, layer = "STEEL", color = 5)
            // Stirrup hook at top-right
            engine.addLine(sx + sw, sy + sd * 0.1, sx + sw + cov * 0.5, sy + sd * 0.1, layer = "STEEL", color = 5)
            engine.addLine(sx + sw + cov * 0.5, sy + sd * 0.1, sx + sw + cov * 0.5, sy + sd * 0.3, layer = "STEEL", color = 5)
        }

        // --- Top reinforcement bars ---
        val topY = sy + rebarR + 1
        val topBarsCount = extractBarCount(input.topBars)
        val topBarSpacing = if (topBarsCount > 1) sw / (topBarsCount + 1) else 0.0
        for (i in 1..topBarsCount) {
            val bx = if (topBarsCount > 1) sx + topBarSpacing * i else midX
            engine.addRebarSymbol(bx, topY, rebarR)
        }

        // --- Bottom reinforcement bars ---
        val botY = sy + sd - rebarR - 1
        val botBarsCount = extractBarCount(input.bottomBars)
        val botBarSpacing = if (botBarsCount > 1) sw / (botBarsCount + 1) else 0.0
        for (i in 1..botBarsCount) {
            val bx = if (botBarsCount > 1) sx + botBarSpacing * i else midX
            engine.addRebarSymbol(bx, botY, rebarR)
        }

        // --- Dimensions ---
        val dimOff = 15.0
        // Width dimension (bottom)
        engine.addHorizontalDimension(cx, cx + w, cy, cy - dimOff, input.width.toInt().toString() + " mm")
        // Depth dimension (right)
        engine.addVerticalDimension(cy, cy + d, cx + w, cx + w + dimOff, input.depth.toInt().toString() + " mm")

        // --- Labels ---
        engine.addText(input.title, midX, cy - dimOff - 10, height = 5.0, layer = "TEXT", hJustify = 1)
        // Top bars label
        engine.addLeader(midX + w / 2 + 5, topY, midX + w / 2 + 30, topY - 10, input.topBars, layer = "DIMENSIONS")
        // Bottom bars label
        engine.addLeader(midX + w / 2 + 5, botY, midX + w / 2 + 30, botY + 10, input.bottomBars, layer = "DIMENSIONS")
        // Stirrup label
        engine.addLeader(sx + sw + 2, sy + sd / 2, sx + sw + 30, sy + sd / 2, input.stirrups, layer = "DIMENSIONS")
    }

    data class BeamTeeInput(
        val flangeWidth: Double,
        val flangeThickness: Double,
        val webWidth: Double,
        val totalDepth: Double,
        val topBars: String = "3#16",
        val bottomBars: String = "3#20",
        val stirrups: String = "T8@150",
        val cover: Double = 40.0,
        val title: String = "T-BEAM SECTION"
    )

    /**
     * Draw a T-beam cross-section.
     * Flange on top, web below.
     */
    fun drawBeamTeeSection(input: BeamTeeInput, ox: Double = 0.0, oy: Double = 0.0) {
        val fw = input.flangeWidth
        val ft = input.flangeThickness
        val ww = input.webWidth
        val td = input.totalDepth
        val webDepth = td - ft

        // Concrete outline (T-shape using polyline)
        val pts = listOf(
            DxfExportEngine.PolyPoint(ox, oy),
            DxfExportEngine.PolyPoint(ox + fw, oy),
            DxfExportEngine.PolyPoint(ox + fw, oy + ft),
            DxfExportEngine.PolyPoint(ox + (fw + ww) / 2, oy + ft),
            DxfExportEngine.PolyPoint(ox + (fw + ww) / 2, oy + td),
            DxfExportEngine.PolyPoint(ox + (fw - ww) / 2, oy + td),
            DxfExportEngine.PolyPoint(ox + (fw - ww) / 2, oy + ft),
            DxfExportEngine.PolyPoint(ox, oy + ft)
        )
        engine.addPolyline(pts, closed = true, layer = "CONCRETE", color = 7)

        // Hatch the T-shape - hatch the flange rectangle and web rectangle separately
        engine.addRectangleHatch(ox, oy, fw, ft, patternName = "AR-CONC", scale = 1.0, layer = "HATCH")
        engine.addRectangleHatch(ox + (fw - ww) / 2, oy + ft, ww, webDepth, patternName = "AR-CONC", scale = 1.0, layer = "HATCH")

        // Center lines
        val midX = ox + fw / 2
        val midY = oy + td / 2
        engine.addCenterCross(midX, midY, Math.max(fw, td) * 0.5)

        // Stirrup in web
        val cov = input.cover
        val sx = ox + (fw - ww) / 2 + cov
        val sy = oy + ft
        val sw = ww - 2 * cov
        val sd = webDepth - cov
        if (sw > 0 && sd > 0) {
            engine.addRectangle(sx, sy, sw, sd, layer = "STEEL", color = 5)
        }

        // Top bars (in flange)
        val topRebarY = oy + cov + 2
        val topN = extractBarCount(input.topBars)
        val topSpacing = fw / (topN + 1)
        for (i in 1..topN) {
            engine.addRebarSymbol(ox + topSpacing * i, topRebarY, 2.0)
        }

        // Bottom bars (in web)
        val botRebarY = oy + td - cov - 2
        val botN = extractBarCount(input.bottomBars)
        val botSpacing = if (botN > 1) sw / (botN + 1) else 0.0
        for (i in 1..botN) {
            val bx = if (botN > 1) sx + botSpacing * i else midX
            engine.addRebarSymbol(bx, botRebarY, 2.0)
        }

        // Dimensions
        val dimOff = 15.0
        engine.addHorizontalDimension(ox, ox + fw, oy, oy - dimOff, input.flangeWidth.toInt().toString() + " mm")
        engine.addVerticalDimension(oy, oy + td, ox + fw, ox + fw + dimOff, input.totalDepth.toInt().toString() + " mm")
        engine.addVerticalDimension(oy, oy + ft, ox + fw + 30, ox + fw + 30 + dimOff, input.flangeThickness.toInt().toString() + " mm")

        // Labels
        engine.addText(input.title, midX, oy - dimOff - 10, height = 5.0, layer = "TEXT", hJustify = 1)
        engine.addLeader(midX, topRebarY, midX + fw / 2 + 20, topRebarY - 15, input.topBars)
        engine.addLeader(midX, botRebarY, midX + fw / 2 + 20, botRebarY + 15, input.bottomBars)
    }

    // ============================================================
    // COLUMN SECTION EXPORTERS
    // ============================================================

    data class ColumnRectInput(
        val width: Double,
        val depth: Double,
        val longitudinalBars: String,  // e.g. "8#20"
        val ties: String,              // e.g. "T10@200"
        val cover: Double = 40.0,
        val title: String = "COLUMN SECTION"
    )

    /**
     * Draw a rectangular column cross-section with reinforcement and ties.
     */
    fun drawColumnRectSection(input: ColumnRectInput, ox: Double = 0.0, oy: Double = 0.0) {
        val w = input.width
        val d = input.depth
        val cov = input.cover
        val rebarR = 2.5

        // Concrete outline
        engine.addRectangle(ox, oy, w, d, layer = "CONCRETE", color = 7)
        engine.addRectangleHatch(ox, oy, w, d, patternName = "AR-CONC", scale = 1.0, layer = "HATCH")

        // Center lines
        val midX = ox + w / 2
        val midY = oy + d / 2
        engine.addCenterCross(midX, midY, Math.max(w, d) * 0.6)

        // Tie (rectangle)
        val tx = ox + cov
        val ty = oy + cov
        val tw = w - 2 * cov
        val td = d - 2 * cov
        if (tw > 0 && td > 0) {
            engine.addRectangle(tx, ty, tw, td, layer = "STEEL", color = 5)
            // Tie hooks (135 deg bend)
            val hookLen = cov * 0.4
            engine.addLine(tx, ty + td, tx - hookLen, ty + td - hookLen, layer = "STEEL", color = 5)
            engine.addLine(tx, ty, tx - hookLen, ty + hookLen, layer = "STEEL", color = 5)
        }

        // Longitudinal bars - distribute evenly around the perimeter
        val nBars = extractBarCount(input.longitudinalBars)
        val barsPerSide = nBars / 4
        val extraBars = nBars % 4
        val barPositions = mutableListOf<Pair<Double, Double>>()

        // Corners always get bars
        barPositions.add(Pair(tx + rebarR + 1, ty + rebarR + 1))
        barPositions.add(Pair(tx + tw - rebarR - 1, ty + rebarR + 1))
        barPositions.add(Pair(tx + tw - rebarR - 1, ty + td - rebarR - 1))
        barPositions.add(Pair(tx + rebarR + 1, ty + td - rebarR - 1))

        // Distribute remaining bars along each side
        var remaining = nBars - 4
        val sides = listOf(
            // Top side: from top-left corner to top-right corner
            Triple(tx + rebarR + 1, ty + rebarR + 1, tx + tw - rebarR - 1),
            // Right side: from top-right to bottom-right
            Triple(ty + rebarR + 1, ty + td - rebarR - 1, tx + tw - rebarR - 1),
            // Bottom side: from bottom-right to bottom-left
            Triple(tx + tw - rebarR - 1, ty + td - rebarR - 1, tx + rebarR + 1),
            // Left side: from bottom-left to top-left
            Triple(ty + td - rebarR - 1, ty + rebarR + 1, tx + rebarR + 1)
        )

        for (sideIdx in 0 until 4) {
            val barsForSide = if (remaining > 0) (remaining + 3) / 4 else 0
            for (i in 1..barsForSide) {
                val t = i.toDouble() / (barsForSide + 1)
                if (sideIdx == 0 || sideIdx == 2) {
                    val x1 = if (sideIdx == 0) sides[0].first else sides[2].first
                    val x2 = if (sideIdx == 0) sides[0].third else sides[2].third
                    val y = if (sideIdx == 0) sides[0].second else sides[2].second
                    barPositions.add(Pair(x1 + (x2 - x1) * t, y))
                } else {
                    val y1 = if (sideIdx == 1) sides[1].first else sides[3].first
                    val y2 = if (sideIdx == 1) sides[1].second else sides[3].second
                    val x = if (sideIdx == 1) sides[1].third else sides[3].third
                    barPositions.add(Pair(x, y1 + (y2 - y1) * t))
                }
                remaining--
            }
        }

        for ((bx, by) in barPositions) {
            engine.addRebarSymbol(bx, by, rebarR)
        }

        // Dimensions
        val dimOff = 15.0
        engine.addHorizontalDimension(ox, ox + w, oy, oy - dimOff, input.width.toInt().toString() + " mm")
        engine.addVerticalDimension(oy, oy + d, ox + w, ox + w + dimOff, input.depth.toInt().toString() + " mm")

        // Labels
        engine.addText(input.title, midX, oy - dimOff - 10, height = 5.0, layer = "TEXT", hJustify = 1)
        val labelX = ox + w + 40.0
        engine.addLeader(tx + tw / 2, ty + td / 2, labelX, ty + td / 2, input.longitudinalBars)
        engine.addText("Ties: " + input.ties, labelX + 2, ty + td / 2 + 8, height = 3.0, layer = "TEXT")
    }

    data class ColumnCircInput(
        val diameter: Double,
        val longitudinalBars: String,
        val spiralTies: String,  // e.g. "T10@150"
        val cover: Double = 40.0,
        val title: String = "CIRCULAR COLUMN SECTION"
    )

    /**
     * Draw a circular column cross-section with spiral reinforcement.
     */
    fun drawColumnCircSection(input: ColumnCircInput, ox: Double = 0.0, oy: Double = 0.0) {
        val dia = input.diameter
        val r = dia / 2
        val cov = input.cover
        val rebarR = 2.5

        // Concrete circle
        engine.addCircle(ox, oy, r, layer = "CONCRETE", color = 7)
        // Section hatching approximation - use SOLID or skip for circles
        // We draw cross-hatching lines for the section
        val hatchR = r - 1
        for (i in -10..10) {
            val offset = i * (hatchR / 5)
            val halfChord = Math.sqrt((hatchR * hatchR - offset * offset).coerceAtLeast(0.0))
            if (halfChord > 0) {
                engine.addLine(ox - halfChord, oy + offset, ox + halfChord, oy + offset,
                    layer = "HATCH", color = 3)
            }
        }

        // Center lines
        engine.addCenterCross(ox, oy, r + 15)

        // Spiral (represented as a circle at tie diameter)
        val tieR = r - cov
        if (tieR > 0) {
            engine.addCircle(ox, oy, tieR, layer = "STEEL", color = 5)
            // Spiral pitch indicator
            engine.addText("Spiral: " + input.spiralTies, ox + r + 10, oy + r - 20, height = 3.0, layer = "TEXT")
        }

        // Longitudinal bars arranged in a circle
        val nBars = extractBarCount(input.longitudinalBars)
        val barCircleR = r - cov - rebarR - 2
        if (barCircleR > 0 && nBars > 0) {
            val angleStep = 360.0 / nBars
            for (i in 0 until nBars) {
                val angle = Math.toRadians(i * angleStep)
                val bx = ox + barCircleR * Math.cos(angle)
                val by = oy + barCircleR * Math.sin(angle)
                engine.addRebarSymbol(bx, by, rebarR)
            }
        }

        // Diameter dimension
        engine.addHorizontalDimension(ox - r, ox + r, oy, oy - r - 15, input.diameter.toInt().toString() + " mm")

        // Label
        engine.addText(input.title, ox, oy - r - 30, height = 5.0, layer = "TEXT", hJustify = 1)
        engine.addLeader(ox + r * 0.7, oy + r * 0.7, ox + r + 20, oy + r + 10, input.longitudinalBars)
    }

    // ============================================================
    // SLAB SECTION EXPORTERS
    // ============================================================

    data class SlabSectionInput(
        val thickness: Double,     // mm
        val span: Double,          // mm (for drawing context)
        val topBarsShort: String,  // e.g. "T12@200"
        val topBarsLong: String,   // e.g. "T10@250"
        val bottomBarsShort: String, // e.g. "T12@150"
        val bottomBarsLong: String,  // e.g. "T10@200"
        val cover: Double = 20.0,
        val isTwoWay: Boolean = false,
        val title: String = "SLAB SECTION"
    )

    /**
     * Draw a slab cross-section showing reinforcement in both directions.
     * Shows a cut-through section with top and bottom bars.
     */
    fun drawSlabSection(input: SlabSectionInput, ox: Double = 0.0, oy: Double = 0.0) {
        val t = input.thickness
        val drawLen = Math.min(input.span, 400.0)
        val cov = input.cover

        // Concrete outline (long horizontal section)
        engine.addRectangle(ox, oy, drawLen, t, layer = "CONCRETE", color = 7)
        engine.addRectangleHatch(ox, oy, drawLen, t, patternName = "AR-CONC", scale = 0.8, layer = "HATCH")

        // Bottom bars (shown as circles along the bottom)
        val botBarY = oy + cov + 2
        val botBarSpacing = 25.0  // visual spacing
        val nBotBars = (drawLen / botBarSpacing).toInt()
        for (i in 0..nBotBars) {
            val bx = ox + 10 + i * botBarSpacing
            if (bx < ox + drawLen - 5) {
                engine.addRebarSymbol(bx, botBarY, 1.5)
            }
        }

        // Top bars (near the top of slab)
        val topBarY = oy + t - cov - 2
        for (i in 0..nBotBars) {
            val bx = ox + 10 + i * botBarSpacing
            if (bx < ox + drawLen - 5) {
                engine.addRebarSymbol(bx, topBarY, 1.5)
            }
        }

        // Cross bars indicator (perpendicular direction - shown as dots with different styling)
        val crossY = (botBarY + topBarY) / 2
        for (i in 0..nBotBars) {
            val bx = ox + 10 + botBarSpacing / 2 + i * botBarSpacing
            if (bx < ox + drawLen - 5) {
                engine.addPoint(bx, crossY, layer = "STEEL", color = 5)
            }
        }

        // Dimensions
        val dimOff = 12.0
        engine.addVerticalDimension(oy, oy + t, ox - dimOff, ox - dimOff - 10, t.toInt().toString() + " mm")

        // Labels
        engine.addText(input.title + if (input.isTwoWay) " (Two-Way)" else " (One-Way)",
            ox + drawLen / 2, oy - dimOff - 5, height = 4.5, layer = "TEXT", hJustify = 1)

        // Bar labels with leaders
        val labelX = ox + drawLen + 10
        engine.addLeader(ox + drawLen - 5, topBarY, labelX, topBarY - 10, "Top: " + input.topBarsShort)
        engine.addLeader(ox + drawLen - 5, botBarY, labelX, botBarY + 10, "Bot: " + input.bottomBarsShort)
        engine.addText("Dist: " + input.bottomBarsLong, labelX, (topBarY + botBarY) / 2, height = 2.5, layer = "TEXT")
    }

    // ============================================================
    // FOOTING EXPORTERS
    // ============================================================

    data class FootingPlanInput(
        val length: Double,           // mm
        val width: Double,            // mm
        val thickness: Double,        // mm
        val columnWidth: Double,      // mm
        val columnDepth: Double,      // mm
        val bottomBarsX: String,      // e.g. "T16@150"
        val bottomBarsY: String,      // e.g. "T12@200"
        val cover: Double = 75.0,
        val title: String = "FOOTING PLAN"
    )

    /**
     * Draw an isolated footing plan view with reinforcement.
     */
    fun drawFootingPlan(input: FootingPlanInput, ox: Double = 0.0, oy: Double = 0.0) {
        val fl = input.length
        val fw = input.width
        val cw = input.columnWidth
        val cd = input.columnDepth
        val cov = input.cover

        // Footing outline
        engine.addRectangle(ox, oy, fl, fw, layer = "CONCRETE", color = 7)
        // Footing hatch
        engine.addRectangleHatch(ox, oy, fl, fw, patternName = "AR-CONC", scale = 1.5, layer = "HATCH")

        // Column outline (centered on footing)
        val colOx = ox + (fl - cw) / 2
        val colOy = oy + (fw - cd) / 2
        engine.addRectangle(colOx, colOy, cw, cd, layer = "CONCRETE", color = 7)
        // Column hatch (different pattern)
        engine.addRectangleHatch(colOx, colOy, cw, cd, patternName = "ANSI31", scale = 3.0, layer = "HATCH")

        // Center lines
        val fCx = ox + fl / 2
        val fCy = oy + fw / 2
        engine.addCenterCross(fCx, fCy, Math.max(fl, fw) * 0.55)

        // Bottom bars in X direction (shown as lines)
        val barSpacing = 20.0  // visual
        val barStartX = ox + cov
        val barEndX = ox + fl - cov
        val nBarsY = ((fw - 2 * cov) / barSpacing).toInt()
        for (i in 0..nBarsY) {
            val by = oy + cov + i * barSpacing
            if (by < oy + fw - cov) {
                engine.addLine(barStartX, by, barEndX, by, layer = "REBAR", color = 1)
            }
        }

        // Bottom bars in Y direction (shown as lines)
        val nBarsX = ((fl - 2 * cov) / barSpacing).toInt()
        for (i in 0..nBarsX) {
            val bx = ox + cov + i * barSpacing
            if (bx < ox + fl - cov) {
                engine.addLine(bx, oy + cov, bx, oy + fw - cov, layer = "REBAR", color = 2)
            }
        }

        // Dimensions
        val dimOff = 15.0
        engine.addHorizontalDimension(ox, ox + fl, oy, oy - dimOff, fl.toInt().toString() + " mm")
        engine.addVerticalDimension(oy, oy + fw, ox, ox - dimOff, fw.toInt().toString() + " mm")
        engine.addHorizontalDimension(colOx, colOx + cw, colOy + cd, colOy + cd + dimOff, cw.toInt().toString() + " mm")

        // Labels
        engine.addText(input.title, fCx, oy - dimOff - 10, height = 5.0, layer = "TEXT", hJustify = 1)
        val lblX = ox + fl + 15
        engine.addText(input.bottomBarsX + " (X-dir)", lblX, fCy - 8, height = 3.0, layer = "TEXT")
        engine.addText(input.bottomBarsY + " (Y-dir)", lblX, fCy + 5, height = 3.0, layer = "TEXT")
    }

    data class FootingSectionInput(
        val footingLength: Double,
        val footingThickness: Double,
        val columnWidth: Double,
        val columnHeight: Double,
        val bottomBars: String,
        val cover: Double = 75.0,
        val title: String = "FOOTING SECTION"
    )

    /**
     * Draw a footing cross-section showing the footing, column, and reinforcement.
     */
    fun drawFootingSection(input: FootingSectionInput, ox: Double = 0.0, oy: Double = 0.0) {
        val fl = input.footingLength
        val ft = input.footingThickness
        val cw = input.columnWidth
        val ch = input.columnHeight
        val cov = input.cover

        // Footing rectangle
        engine.addRectangle(ox, oy, fl, ft, layer = "CONCRETE", color = 7)
        engine.addRectangleHatch(ox, oy, fl, ft, patternName = "AR-CONC", scale = 1.5, layer = "HATCH")

        // Column on top of footing (centered)
        val colOx = ox + (fl - cw) / 2
        val colOy = oy + ft
        engine.addRectangle(colOx, colOy, cw, ch, layer = "CONCRETE", color = 7)
        engine.addRectangleHatch(colOx, colOy, cw, ch, patternName = "ANSI31", scale = 3.0, layer = "HATCH")

        // Ground line (dashed)
        engine.addLine(ox - 20, oy + ft, ox + fl + 20, oy + ft, layer = "HIDDEN", lineType = "HIDDEN")
        engine.addText("GL", ox - 35, oy + ft - 2, height = 3.0, layer = "TEXT", hJustify = 2)

        // Bottom reinforcement bars in footing
        val barY = oy + cov + 2
        val barSpacing = 20.0
        val nBars = ((fl - 2 * cov) / barSpacing).toInt()
        for (i in 0..nBars) {
            val bx = ox + cov + i * barSpacing
            if (bx < ox + fl - cov) {
                engine.addRebarSymbol(bx, barY, 2.0)
            }
        }

        // Dimensions
        val dimOff = 15.0
        engine.addHorizontalDimension(ox, ox + fl, oy, oy - dimOff, fl.toInt().toString() + " mm")
        engine.addVerticalDimension(oy, oy + ft, ox + fl, ox + fl + dimOff, ft.toInt().toString() + " mm")
        engine.addVerticalDimension(colOy, colOy + ch, ox + fl + 30, ox + fl + 30 + dimOff, ch.toInt().toString() + " mm")
        engine.addHorizontalDimension(colOx, colOx + cw, colOy + ch, colOy + ch + dimOff, cw.toInt().toString() + " mm")

        // Label
        engine.addText(input.title, ox + fl / 2, oy - dimOff - 10, height = 5.0, layer = "TEXT", hJustify = 1)
        engine.addLeader(ox + fl / 2, barY, ox + fl + 30, barY - 15, input.bottomBars)
    }

    // ============================================================
    // STAIRCASE EXPORTERS
    // ============================================================

    data class StaircaseInput(
        val totalWidth: Double,      // horizontal projection mm
        val totalHeight: Double,     // total rise mm
        val waistThickness: Double,  // mm
        val treadDepth: Double,      // mm
        val riserHeight: Double,     // mm
        val topBars: String = "T12@200",
        val bottomBars: String = "T12@150",
        val title: String = "STAIRCASE SECTION"
    )

    /**
     * Draw a staircase longitudinal section showing steps, waist slab, and reinforcement.
     */
    fun drawStaircaseSection(input: StaircaseInput, ox: Double = 0.0, oy: Double = 0.0) {
        val tw = input.totalWidth
        val th = input.totalHeight
        val wt = input.waistThickness
        val td = input.treadDepth
        val rh = input.riserHeight
        val nSteps = (th / rh).toInt().coerceIn(1, 30)

        // Draw waist slab (inclined bottom line)
        val stepW = tw / nSteps
        val actualRise = th / nSteps

        // Bottom of waist line points
        val bottomPts = mutableListOf<DxfExportEngine.PolyPoint>()
        val topPts = mutableListOf<DxfExportEngine.PolyPoint>()

        for (i in 0..nSteps) {
            val x = ox + i * stepW
            val y = oy + th - i * actualRise - wt
            bottomPts.add(DxfExportEngine.PolyPoint(x, y))
        }
        for (i in 0..nSteps) {
            val x = ox + i * stepW
            val y = oy + th - i * actualRise
            topPts.add(DxfExportEngine.PolyPoint(x, y))
        }

        // Waist slab outline (polygon: top-left going right, then bottom-right going left)
        val waistPts = mutableListOf<DxfExportEngine.PolyPoint>()
        // Top edge (going right, stepping)
        waistPts.add(DxfExportEngine.PolyPoint(ox, oy + th))
        for (i in 1..nSteps) {
            waistPts.add(DxfExportEngine.PolyPoint(ox + i * stepW, oy + th))
            waistPts.add(DxfExportEngine.PolyPoint(ox + i * stepW, oy + th - i * actualRise))
        }
        // Bottom edge (going left, at waist bottom)
        waistPts.add(DxfExportEngine.PolyPoint(ox + tw, oy - wt))
        waistPts.add(DxfExportEngine.PolyPoint(ox, oy - wt))

        engine.addPolyline(waistPts, closed = true, layer = "CONCRETE", color = 7)

        // Draw step lines
        for (i in 1..nSteps) {
            val x = ox + i * stepW
            val yTop = oy + th - (i - 1) * actualRise
            val yBot = oy + th - i * actualRise
            engine.addLine(x, yTop, x, yBot, layer = "CONCRETE", color = 7)
        }
        // Top horizontal line
        engine.addLine(ox, oy + th, ox + tw, oy + th, layer = "CONCRETE", color = 7)

        // Landing at bottom
        engine.addRectangle(ox - 20, oy - wt, 20.0, wt, layer = "CONCRETE", color = 7)

        // Bottom reinforcement (along waist)
        val rebarOffset = 5.0
        for (i in 0 until nSteps) {
            val x1 = ox + i * stepW + 5
            val x2 = ox + (i + 1) * stepW - 5
            val y1 = oy + th - i * actualRise - wt + rebarOffset
            val y2 = oy + th - (i + 1) * actualRise - wt + rebarOffset
            engine.addLine(x1, y1, x2, y2, layer = "REBAR", color = 1)
        }

        // Top reinforcement at mid-span (shown at top of waist)
        for (i in 1 until nSteps) {
            val x1 = ox + i * stepW + 3
            val x2 = ox + (i + 1) * stepW - 3
            val y1 = oy + th - i * actualRise - rebarOffset
            val y2 = oy + th - (i + 1) * actualRise - rebarOffset
            engine.addLine(x1, y1, x2, y2, layer = "REBAR", color = 2)
        }

        // Ground lines
        engine.addLine(ox - 40, oy, ox + tw + 40, oy, layer = "GRID", lineType = "DASHED")
        engine.addLine(ox - 20, oy + th, ox + tw + 20, oy + th, layer = "GRID", lineType = "DASHED")

        // Dimensions
        val dimOff = 15.0
        engine.addHorizontalDimension(ox, ox + tw, oy, oy - dimOff, tw.toInt().toString() + " mm")
        engine.addVerticalDimension(oy, oy + th, ox - dimOff, ox - dimOff - 10, th.toInt().toString() + " mm")
        // One tread dimension
        engine.addHorizontalDimension(ox, ox + stepW, oy + th + 5, oy + th + 5 + dimOff, td.toInt().toString())
        // One riser dimension
        engine.addVerticalDimension(oy + th - actualRise, oy + th, ox + tw + 5, ox + tw + 5 + dimOff, rh.toInt().toString())

        // Label
        engine.addText(input.title, ox + tw / 2, oy - dimOff - 10, height = 5.0, layer = "TEXT", hJustify = 1)
        engine.addText("${nSteps} steps", ox + tw / 2, oy - dimOff - 18, height = 3.0, layer = "TEXT", hJustify = 1)
        engine.addText(input.bottomBars, ox + tw / 2, oy - wt - 8, height = 3.0, layer = "TEXT", hJustify = 1)
    }

    /**
     * Draw a staircase plan view (looking from above).
     */
    fun drawStaircasePlan(input: StaircaseInput, ox: Double = 0.0, oy: Double = 0.0) {
        val tw = input.totalWidth
        val stairW = 1200.0  // typical stair width
        val td = input.treadDepth
        val nSteps = (input.totalHeight / input.riserHeight).toInt().coerceIn(1, 30)
        val stepW = tw / nSteps

        // Staircase outline
        engine.addRectangle(ox, oy, tw, stairW, layer = "CONCRETE", color = 7)

        // Draw step lines
        for (i in 1 until nSteps) {
            val x = ox + i * stepW
            engine.addLine(x, oy, x, oy + stairW, layer = "CONCRETE", color = 7)
            // Direction arrow (every few steps)
            if (i % 3 == 0) {
                engine.addLine(x - stepW / 2, oy + stairW / 2 - 5, x - stepW / 2, oy + stairW / 2 + 5, layer = "DIMENSIONS")
                engine.addLine(x - stepW / 2, oy + stairW / 2 + 5, x - stepW / 2 + 3, oy + stairW / 2, layer = "DIMENSIONS")
                engine.addLine(x - stepW / 2, oy + stairW / 2 + 5, x - stepW / 2 - 3, oy + stairW / 2, layer = "DIMENSIONS")
            }
        }

        // Up arrow
        engine.addLine(ox + 10, oy + stairW / 2, ox + tw - 10, oy + stairW / 2, layer = "DIMENSIONS", color = 1)
        engine.addLine(ox + tw - 10, oy + stairW / 2, ox + tw - 18, oy + stairW / 2 + 4, layer = "DIMENSIONS", color = 1)
        engine.addLine(ox + tw - 10, oy + stairW / 2, ox + tw - 18, oy + stairW / 2 - 4, layer = "DIMENSIONS", color = 1)
        engine.addText("UP", ox + tw / 2, oy + stairW / 2 + 8, height = 4.0, layer = "TEXT", hJustify = 1)

        // Dimensions
        engine.addHorizontalDimension(ox, ox + tw, oy, oy - 12, tw.toInt().toString() + " mm")
        engine.addVerticalDimension(oy, oy + stairW, ox - 12, ox - 24, stairW.toInt().toString() + " mm")

        engine.addText(input.title + " - PLAN", ox + tw / 2, oy - 22, height = 5.0, layer = "TEXT", hJustify = 1)
    }

    // ============================================================
    // RETAINING WALL EXPORTER
    // ============================================================

    data class RetainingWallInput(
        val totalHeight: Double,     // mm
        val baseWidth: Double,       // mm (at bottom)
        val topThickness: Double,    // mm (stem top)
        val baseThickness: Double,   // mm (foundation slab)
        val toeLength: Double,       // mm
        val heelLength: Double,      // mm
        val stemBars: String = "T16@200",
        val baseBarsTop: String = "T12@200",
        val baseBarsBot: String = "T16@150",
        val cover: Double = 50.0,
        val title: String = "RETAINING WALL SECTION"
    )

    /**
     * Draw a cantilever retaining wall cross-section with reinforcement.
     * Layout: [toe] [stem/base] [heel]
     */
    fun drawRetainingWallSection(input: RetainingWallInput, ox: Double = 0.0, oy: Double = 0.0) {
        val h = input.totalHeight
        val bt = input.baseThickness
        val tt = input.topThickness
        val bb = input.baseThickness
        val toe = input.toeLength
        val heel = input.heelLength
        val cov = input.cover

        // The stem sits on the base. Base extends toe-length to left and heel-length to right.
        // The base bottom-left corner is at origin
        val baseLeft = ox
        val baseRight = ox + toe + bt + heel
        val baseTop = oy + bb
        val baseBot = oy

        // Stem: tapers from baseThickness at bottom to topThickness at top
        val stemBotLeft = ox + toe
        val stemBotRight = ox + toe + bt
        val stemTopLeft = ox + toe + (bt - tt) / 2
        val stemTopRight = ox + toe + (bt + tt) / 2
        val stemTop = oy + bb + h

        // Draw base slab
        engine.addRectangle(baseLeft, baseBot, toe + bt + heel, bb, layer = "CONCRETE", color = 7)
        engine.addRectangleHatch(baseLeft, baseBot, toe + bt + heel, bb, patternName = "AR-CONC", scale = 1.5, layer = "HATCH")

        // Draw stem (trapezoid)
        val stemPts = listOf(
            DxfExportEngine.PolyPoint(stemBotLeft, baseTop),
            DxfExportEngine.PolyPoint(stemBotRight, baseTop),
            DxfExportEngine.PolyPoint(stemTopRight, stemTop),
            DxfExportEngine.PolyPoint(stemTopLeft, stemTop)
        )
        engine.addPolyline(stemPts, closed = true, layer = "CONCRETE", color = 7)
        // Hatch the stem
        engine.addRectangleHatch(stemTopLeft, baseTop, tt, h, patternName = "AR-CONC", scale = 1.0, layer = "HATCH")

        // Ground line
        engine.addLine(baseLeft - 30, baseTop, baseRight + 30, baseTop, layer = "HIDDEN", lineType = "HIDDEN")
        // Retained earth (behind wall, to the right)
        engine.addLine(baseRight, baseTop, baseRight, stemTop + 10, layer = "HIDDEN", lineType = "HIDDEN")
        // Earth hatch on retained side
        val earthPts = listOf(
            DxfExportEngine.PolyPoint(baseRight, baseTop),
            DxfExportEngine.PolyPoint(baseRight + 40, baseTop),
            DxfExportEngine.PolyPoint(baseRight + 40, stemTop + 10),
            DxfExportEngine.PolyPoint(stemTopRight, stemTop),
            DxfExportEngine.PolyPoint(stemBotRight, baseTop)
        )
        engine.addHatch(listOf(earthPts), patternName = "AR-SAND", scale = 1.0, layer = "HATCH")

        // Stem main reinforcement (vertical bars on retained side)
        val nStemBars = (h / 30).toInt()
        val stemBarX = stemBotRight - cov - 2
        for (i in 0..nStemBars) {
            val t = i.toDouble() / nStemBars
            // Bar x position follows the taper
            val barX = stemBotRight - cov - 2 + t * ((bt - tt) / 2)
            val barY = baseTop + cov + i * ((h - 2 * cov) / nStemBars)
            engine.addRebarSymbol(barX, barY, 2.0)
        }

        // Base bottom reinforcement
        val baseBarY = baseBot + cov + 2
        val barSpacing = 20.0
        val nBaseBars = ((toe + bt + heel - 2 * cov) / barSpacing).toInt()
        for (i in 0..nBaseBars) {
            val bx = baseLeft + cov + i * barSpacing
            if (bx < baseRight - cov) {
                engine.addRebarSymbol(bx, baseBarY, 2.0)
            }
        }

        // Dimensions
        val dimOff = 15.0
        engine.addVerticalDimension(baseBot, stemTop, baseLeft - dimOff, baseLeft - dimOff - 10, h.toInt().toString() + " mm")
        engine.addHorizontalDimension(baseLeft, baseRight, baseBot, baseBot - dimOff, (toe + bt + heel).toInt().toString() + " mm")
        engine.addVerticalDimension(baseBot, baseTop, baseRight + dimOff, baseRight + dimOff + 10, bb.toInt().toString() + " mm")

        // Labels
        val labelX = baseRight + 50
        engine.addText(input.title, ox + (toe + bt + heel) / 2, baseBot - dimOff - 10, height = 5.0, layer = "TEXT", hJustify = 1)
        engine.addLeader(stemBotRight - 5, baseTop + h / 2, labelX, baseTop + h / 2, input.stemBars)
        engine.addText("Base bot: " + input.baseBarsBot, labelX, baseTop - 5, height = 2.5, layer = "TEXT")
        engine.addText("Base top: " + input.baseBarsTop, labelX, baseTop - 12, height = 2.5, layer = "TEXT")
    }

    // ============================================================
    // WATER TANK EXPORTER
    // ============================================================

    data class WaterTankInput(
        val innerLength: Double,
        val innerWidth: Double,
        val wallThickness: Double,
        val baseThickness: Double,
        val height: Double,
        val wallBarsOuter: String = "T12@200",
        val wallBarsInner: String = "T12@200",
        val baseBarsTop: String = "T12@200",
        val baseBarsBot: String = "T16@150",
        val cover: Double = 40.0,
        val title: String = "WATER TANK SECTION"
    )

    /**
     * Draw a water tank cross-section through the wall and base.
     */
    fun drawWaterTankSection(input: WaterTankInput, ox: Double = 0.0, oy: Double = 0.0) {
        val il = input.innerLength
        val wt = input.wallThickness
        val bt = input.baseThickness
        val h = input.height
        val totalL = il + 2 * wt
        val cov = input.cover

        // Base slab
        engine.addRectangle(ox, oy, totalL, bt, layer = "CONCRETE", color = 7)
        engine.addRectangleHatch(ox, oy, totalL, bt, patternName = "AR-CONC", scale = 1.5, layer = "HATCH")

        // Left wall
        engine.addRectangle(ox, oy + bt, wt, h, layer = "CONCRETE", color = 7)
        engine.addRectangleHatch(ox, oy + bt, wt, h, patternName = "AR-CONC", scale = 1.0, layer = "HATCH")

        // Right wall
        val rwx = ox + il + wt
        engine.addRectangle(rwx, oy + bt, wt, h, layer = "CONCRETE", color = 7)
        engine.addRectangleHatch(rwx, oy + bt, wt, h, patternName = "AR-CONC", scale = 1.0, layer = "HATCH")

        // Water level indication
        val waterY = oy + bt + h * 0.8
        engine.addLine(ox + wt, waterY, ox + il + wt, waterY, layer = "HIDDEN", lineType = "PHANTOM")
        engine.addText("WL", ox + il / 2 + wt, waterY + 3, height = 3.0, layer = "TEXT", hJustify = 1, color = 4)

        // Water fill (light blue hatching area)
        val waterPts = listOf(
            DxfExportEngine.PolyPoint(ox + wt, oy + bt),
            DxfExportEngine.PolyPoint(ox + il + wt, oy + bt),
            DxfExportEngine.PolyPoint(ox + il + wt, waterY),
            DxfExportEngine.PolyPoint(ox + wt, waterY)
        )
        engine.addHatch(listOf(waterPts), patternName = "SOLID", layer = "HATCH", color = 4)

        // Left wall reinforcement
        // Vertical bars on inner face (tension side for internal hydrostatic pressure)
        val nWallBars = (h / 25).toInt()
        for (i in 0..nWallBars) {
            val by = oy + bt + cov + i * ((h - 2 * cov) / nWallBars)
            // Inner face bars (right side of left wall)
            engine.addRebarSymbol(ox + wt - cov - 2, by, 1.8)
            // Outer face bars (left side of left wall)
            engine.addRebarSymbol(ox + cov + 2, by, 1.8)
        }

        // Right wall reinforcement
        for (i in 0..nWallBars) {
            val by = oy + bt + cov + i * ((h - 2 * cov) / nWallBars)
            // Inner face bars (left side of right wall)
            engine.addRebarSymbol(rwx + cov + 2, by, 1.8)
            // Outer face bars
            engine.addRebarSymbol(rwx + wt - cov - 2, by, 1.8)
        }

        // Base reinforcement
        val baseBarY1 = oy + cov + 2  // bottom
        val baseBarY2 = oy + bt - cov - 2  // top
        val nBaseBars = ((totalL - 2 * cov) / 20).toInt()
        for (i in 0..nBaseBars) {
            val bx = ox + cov + i * 20.0
            if (bx < ox + totalL - cov) {
                engine.addRebarSymbol(bx, baseBarY1, 1.8)
                engine.addRebarSymbol(bx, baseBarY2, 1.8)
            }
        }

        // Dimensions
        val dimOff = 15.0
        engine.addHorizontalDimension(ox, ox + totalL, oy, oy - dimOff, totalL.toInt().toString() + " mm")
        engine.addVerticalDimension(oy, oy + bt + h, ox - dimOff, ox - dimOff - 10, (bt + h).toInt().toString() + " mm")
        engine.addHorizontalDimension(ox + wt, ox + wt + il, oy + bt + h, oy + bt + h + dimOff, il.toInt().toString() + " mm")
        engine.addVerticalDimension(oy, oy + bt, ox + totalL + dimOff, ox + totalL + dimOff + 10, bt.toInt().toString() + " mm")

        // Labels
        engine.addText(input.title, ox + totalL / 2, oy - dimOff - 10, height = 5.0, layer = "TEXT", hJustify = 1)
        val lblX = ox + totalL + 25
        engine.addText("Wall outer: " + input.wallBarsOuter, lblX, oy + bt + h / 2, height = 2.5, layer = "TEXT")
        engine.addText("Wall inner: " + input.wallBarsInner, lblX, oy + bt + h / 2 + 7, height = 2.5, layer = "TEXT")
        engine.addText("Base top: " + input.baseBarsTop, lblX, oy + bt / 2 - 3, height = 2.5, layer = "TEXT")
        engine.addText("Base bot: " + input.baseBarsBot, lblX, oy + bt / 2 + 5, height = 2.5, layer = "TEXT")
    }

    // ============================================================
    // FRAME ANALYSIS DIAGRAMS
    // ============================================================

    data class FrameAnalysisInput(
        val spans: List<Double>,         // span lengths in mm
        val supports: List<Int>,         // 0=free, 1=pin, 2=fixed, 3=roller
        val bmdValues: List<Double>,     // BMD ordinates at each node (+ for sagging)
        val sfdValues: List<Double>,     // SFD ordinates at each node (+ for upward)
        val loads: List<String> = listOf(),  // load descriptions
        val title: String = "FRAME ANALYSIS"
    )

    /**
     * Draw BMD (Bending Moment Diagram) for a continuous beam.
     * Positive moments (sagging) drawn below the beam line, negative (hogging) above.
     */
    fun drawBMD(input: FrameAnalysisInput, ox: Double = 0.0, oy: Double = 0.0) {
        val spans = input.spans
        val moments = input.bmdValues
        if (moments.size < 2) return

        val totalLen = spans.sum()
        val scale = 0.1  // moment scale factor for visual height
        val maxMoment = moments.map { Math.abs(it) }.maxOrNull() ?: 1.0
        val diagramScale = 60.0 / maxMoment  // normalize to 60mm max height

        // Beam line
        engine.addLine(ox, oy, ox + totalLen, oy, layer = "OUTLINE", color = 7, lineWeight = 50)

        // Support symbols
        var xPos = ox
        for (i in input.supports.indices) {
            when (input.supports[i]) {
                1 -> drawPinSupport(xPos, oy)
                2 -> drawFixedSupport(xPos, oy)
                3 -> drawRollerSupport(xPos, oy)
            }
            if (i < spans.size) xPos += spans[i]
        }

        // BMD curve
        val bmdPts = mutableListOf<DxfExportEngine.PolyPoint>()
        // Interpolate between moment values for smooth curve
        val nNodes = moments.size
        val totalPts = nNodes * 5
        for (i in 0..totalPts) {
            val t = i.toDouble() / totalPts
            val x = ox + t * totalLen
            // Find which span segment we're in
            var cumLen = 0.0
            var moment = 0.0
            for (j in 0 until nNodes - 1) {
                val segLen = if (j < spans.size) spans[j] else 0.0
                val segStart = cumLen
                val segEnd = cumLen + segLen
                val localT = if (segLen > 0) (t * totalLen - segStart) / segLen else 0.0
                if (t * totalLen >= segStart && t * totalLen <= segEnd) {
                    // Linear interpolation within segment
                    moment = moments[j] + (moments[j + 1] - moments[j]) * localT
                    break
                }
                cumLen = segEnd
            }
            // Sagging positive = below baseline
            val y = oy - moment * diagramScale
            bmdPts.add(DxfExportEngine.PolyPoint(x, y))
        }

        if (bmdPts.size >= 2) {
            engine.addPolyline(bmdPts, closed = false, layer = "DIAGRAM", color = 2)
            // Fill under the curve
            val fillPts = mutableListOf(bmdPts.first())
            fillPts.addAll(bmdPts)
            fillPts.add(DxfExportEngine.PolyPoint(bmdPts.last().x, oy))
            fillPts.add(DxfExportEngine.PolyPoint(bmdPts.first().x, oy))
            engine.addHatch(listOf(fillPts), patternName = "SOLID", layer = "DIAGRAM", color = 2)
        }

        // Baseline for reference
        engine.addLine(ox, oy, ox + totalLen, oy, layer = "GRID", lineType = "DASHED")

        // Moment values at key points
        xPos = ox
        for (i in moments.indices) {
            val y = oy - moments[i] * diagramScale
            if (Math.abs(moments[i]) > maxMoment * 0.05) {
                engine.addText(formatValue(moments[i]), xPos, y - 5, height = 2.5, layer = "TEXT", hJustify = 1)
            }
            if (i < spans.size) xPos += spans[i]
        }

        // Title
        engine.addText(input.title + " - BMD", ox + totalLen / 2, oy + 25, height = 5.0, layer = "TEXT", hJustify = 1)
        engine.addText("+M (Sagging) below, -M (Hogging) above", ox + totalLen / 2, oy + 32, height = 2.5, layer = "TEXT", hJustify = 1)
    }

    /**
     * Draw SFD (Shear Force Diagram) for a continuous beam.
     */
    fun drawSFD(input: FrameAnalysisInput, ox: Double = 0.0, oy: Double = 0.0) {
        val spans = input.spans
        val shears = input.sfdValues
        if (shears.size < 2) return

        val totalLen = spans.sum()
        val maxShear = shears.map { Math.abs(it) }.maxOrNull() ?: 1.0
        val diagramScale = 60.0 / maxShear

        // Beam line
        engine.addLine(ox, oy, ox + totalLen, oy, layer = "OUTLINE", color = 7, lineWeight = 50)

        // Support symbols
        var xPos = ox
        for (i in input.supports.indices) {
            when (input.supports[i]) {
                1 -> drawPinSupport(xPos, oy)
                2 -> drawFixedSupport(xPos, oy)
                3 -> drawRollerSupport(xPos, oy)
            }
            if (i < spans.size) xPos += spans[i]
        }

        // SFD curve
        val sfdPts = mutableListOf<DxfExportEngine.PolyPoint>()
        val nNodes = shears.size
        val totalPts = nNodes * 5
        for (i in 0..totalPts) {
            val t = i.toDouble() / totalPts
            val x = ox + t * totalLen
            var cumLen = 0.0
            var shear = 0.0
            for (j in 0 until nNodes - 1) {
                val segLen = if (j < spans.size) spans[j] else 0.0
                val segStart = cumLen
                val segEnd = cumLen + segLen
                if (t * totalLen >= segStart && t * totalLen <= segEnd) {
                    val localT = if (segLen > 0) (t * totalLen - segStart) / segLen else 0.0
                    shear = shears[j] + (shears[j + 1] - shears[j]) * localT
                    break
                }
                cumLen = segEnd
            }
            val y = oy + shear * diagramScale
            sfdPts.add(DxfExportEngine.PolyPoint(x, y))
        }

        if (sfdPts.size >= 2) {
            engine.addPolyline(sfdPts, closed = false, layer = "DIAGRAM", color = 3)
            // Fill
            val fillPts = mutableListOf(sfdPts.first())
            fillPts.addAll(sfdPts)
            fillPts.add(DxfExportEngine.PolyPoint(sfdPts.last().x, oy))
            fillPts.add(DxfExportEngine.PolyPoint(sfdPts.first().x, oy))
            engine.addHatch(listOf(fillPts), patternName = "SOLID", layer = "DIAGRAM", color = 3)
        }

        engine.addLine(ox, oy, ox + totalLen, oy, layer = "GRID", lineType = "DASHED")

        // Shear values
        xPos = ox
        for (i in shears.indices) {
            val y = oy + shears[i] * diagramScale
            if (Math.abs(shears[i]) > maxShear * 0.05) {
                engine.addText(formatValue(shears[i]), xPos, y + 5, height = 2.5, layer = "TEXT", hJustify = 1)
            }
            if (i < spans.size) xPos += spans[i]
        }

        engine.addText(input.title + " - SFD", ox + totalLen / 2, oy + 25, height = 5.0, layer = "TEXT", hJustify = 1)
    }

    // ============================================================
    // STEEL MEMBER EXPORTERS
    // ============================================================

    data class SteelISectionInput(
        val designation: String,      // e.g. "W310x107"
        val depth: Double,            // mm
        val flangeWidth: Double,      // mm
        val flangeThickness: Double,  // mm
        val webThickness: Double,     // mm
        val title: String = "STEEL I-SECTION"
    )

    /**
     * Draw a steel I-beam cross-section.
     */
    fun drawSteelISection(input: SteelISectionInput, ox: Double = 0.0, oy: Double = 0.0) {
        val d = input.depth
        val fw = input.flangeWidth
        val ft = input.flangeThickness
        val wt = input.webThickness

        val cx = ox + fw / 2

        // Top flange
        engine.addRectangle(ox, oy + d - ft, fw, ft, layer = "STEEL", color = 5)
        // Bottom flange
        engine.addRectangle(ox, oy, fw, ft, layer = "STEEL", color = 5)
        // Web
        engine.addRectangle(cx - wt / 2, oy + ft, wt, d - 2 * ft, layer = "STEEL", color = 5)

        // Center lines
        engine.addCenterCross(cx, oy + d / 2, Math.max(fw, d) * 0.6)

        // Dimensions
        val dimOff = 15.0
        engine.addHorizontalDimension(ox, ox + fw, oy, oy - dimOff, fw.toInt().toString() + " mm")
        engine.addVerticalDimension(oy, oy + d, ox + fw, ox + fw + dimOff, d.toInt().toString() + " mm")
        engine.addHorizontalDimension(cx - wt / 2, cx + wt / 2, oy + d, oy + d + dimOff, wt.toInt().toString() + " mm")
        engine.addVerticalDimension(oy, oy + ft, ox - dimOff, ox - dimOff - 10, ft.toInt().toString() + " mm")

        // Labels
        engine.addText(input.title, cx, oy - dimOff - 10, height = 5.0, layer = "TEXT", hJustify = 1)
        engine.addText(input.designation, cx, oy + d / 2, height = 4.0, layer = "TEXT", hJustify = 1, vJustify = 2)
    }

    data class SteelChannelInput(
        val designation: String,
        val depth: Double,
        val flangeWidth: Double,
        val flangeThickness: Double,
        val webThickness: Double,
        val title: String = "STEEL CHANNEL SECTION"
    )

    /**
     * Draw a steel channel (C-shape) cross-section.
     */
    fun drawSteelChannelSection(input: SteelChannelInput, ox: Double = 0.0, oy: Double = 0.0) {
        val d = input.depth
        val fw = input.flangeWidth
        val ft = input.flangeThickness
        val wt = input.webThickness

        // Channel: web on left, flanges extend to the right
        // Web
        engine.addRectangle(ox, oy + ft, wt, d - 2 * ft, layer = "STEEL", color = 5)
        // Top flange
        engine.addRectangle(ox, oy + d - ft, fw, ft, layer = "STEEL", color = 5)
        // Bottom flange
        engine.addRectangle(ox, oy, fw, ft, layer = "STEEL", color = 5)

        // Center lines
        val cx = ox + fw / 2
        val cy = oy + d / 2
        engine.addCenterCross(cx, cy, Math.max(fw, d) * 0.6)

        // Dimensions
        val dimOff = 15.0
        engine.addHorizontalDimension(ox, ox + fw, oy, oy - dimOff, fw.toInt().toString() + " mm")
        engine.addVerticalDimension(oy, oy + d, ox + fw, ox + fw + dimOff, d.toInt().toString() + " mm")

        engine.addText(input.title, cx, oy - dimOff - 10, height = 5.0, layer = "TEXT", hJustify = 1)
        engine.addText(input.designation, cx, cy, height = 4.0, layer = "TEXT", hJustify = 1, vJustify = 2)
    }

    data class SteelAngleInput(
        val designation: String,
        val legLength: Double,
        val thickness: Double,
        val isEqualLeg: Boolean = true,
        val otherLeg: Double = 0.0,
        val title: String = "STEEL ANGLE SECTION"
    )

    /**
     * Draw a steel angle (L-shape) cross-section.
     */
    fun drawSteelAngleSection(input: SteelAngleInput, ox: Double = 0.0, oy: Double = 0.0) {
        val l1 = input.legLength
        val l2 = if (input.isEqualLeg) l1 else input.otherLeg
        val t = input.thickness

        // Vertical leg
        engine.addRectangle(ox, oy, t, l1, layer = "STEEL", color = 5)
        // Horizontal leg
        engine.addRectangle(ox, oy, l2, t, layer = "STEEL", color = 5)

        // Center lines
        engine.addCenterCross(ox + l2 / 2, oy + l1 / 2, Math.max(l1, l2) * 0.5)

        // Dimensions
        val dimOff = 12.0
        engine.addHorizontalDimension(ox, ox + l2, oy, oy - dimOff, l2.toInt().toString())
        engine.addVerticalDimension(oy, oy + l1, ox + l2, ox + l2 + dimOff, l1.toInt().toString())
        engine.addText("t=" + t.toInt(), ox + l2 + 5, oy + l1 / 2, height = 3.0, layer = "TEXT")

        engine.addText(input.title, ox + l2 / 2, oy - dimOff - 8, height = 5.0, layer = "TEXT", hJustify = 1)
        engine.addText(input.designation, ox + l2 / 2, oy + l1 / 2, height = 4.0, layer = "TEXT", hJustify = 1, vJustify = 2)
    }

    data class SteelBoxInput(
        val designation: String,
        val depth: Double,
        val width: Double,
        val wallThickness: Double,
        val title: String = "STEEL BOX SECTION"
    )

    /**
     * Draw a hollow steel box (RHS/SHS) cross-section.
     */
    fun drawSteelBoxSection(input: SteelBoxInput, ox: Double = 0.0, oy: Double = 0.0) {
        val d = input.depth
        val w = input.width
        val t = input.wallThickness

        // Outer rectangle
        engine.addRectangle(ox, oy, w, d, layer = "STEEL", color = 5)
        // Inner void (shown as hidden line)
        engine.addRectangle(ox + t, oy + t, w - 2 * t, d - 2 * t, layer = "HIDDEN", lineType = "HIDDEN", color = 7)

        // Center lines
        engine.addCenterCross(ox + w / 2, oy + d / 2, Math.max(w, d) * 0.6)

        // Dimensions
        val dimOff = 15.0
        engine.addHorizontalDimension(ox, ox + w, oy, oy - dimOff, w.toInt().toString() + " mm")
        engine.addVerticalDimension(oy, oy + d, ox + w, ox + w + dimOff, d.toInt().toString() + " mm")

        engine.addText(input.title, ox + w / 2, oy - dimOff - 10, height = 5.0, layer = "TEXT", hJustify = 1)
        engine.addText(input.designation, ox + w / 2, oy + d / 2, height = 4.0, layer = "TEXT", hJustify = 1, vJustify = 2)
        engine.addText("t=" + t.toInt() + " mm", ox + w / 2, oy + d / 2 + 8, height = 3.0, layer = "TEXT", hJustify = 1)
    }

    // ============================================================
    // SUPPORT SYMBOL HELPERS
    // ============================================================

    private fun drawPinSupport(x: Double, y: Double) {
        val s = 8.0
        // Triangle
        engine.addLine(x, y, x - s, y - s * 1.5, layer = "OUTLINE", color = 7)
        engine.addLine(x, y, x + s, y - s * 1.5, layer = "OUTLINE", color = 7)
        engine.addLine(x - s, y - s * 1.5, x + s, y - s * 1.5, layer = "OUTLINE", color = 7)
        // Ground hatching
        for (i in -2..2) {
            val hx = x + i * 4.0
            engine.addLine(hx, y - s * 1.5, hx - 3, y - s * 1.5 - 4, layer = "OUTLINE", color = 7)
        }
    }

    private fun drawFixedSupport(x: Double, y: Double) {
        val h = 12.0
        // Vertical line for wall
        engine.addLine(x, y - h, x, y + h, layer = "OUTLINE", color = 7, lineWeight = 50)
        // Hatch lines on the wall side (assume left)
        for (i in -3..3) {
            val hy = y + i * 3.5
            engine.addLine(x, hy, x - 6, hy + 4, layer = "OUTLINE", color = 7)
        }
    }

    private fun drawRollerSupport(x: Double, y: Double) {
        val s = 8.0
        // Triangle (smaller)
        engine.addLine(x, y, x - s * 0.7, y - s, layer = "OUTLINE", color = 7)
        engine.addLine(x, y, x + s * 0.7, y - s, layer = "OUTLINE", color = 7)
        engine.addLine(x - s * 0.7, y - s, x + s * 0.7, y - s, layer = "OUTLINE", color = 7)
        // Rollers (circles)
        engine.addCircle(x - 3, y - s - 2, 2.0, layer = "OUTLINE", color = 7)
        engine.addCircle(x + 3, y - s - 2, 2.0, layer = "OUTLINE", color = 7)
        // Ground line
        engine.addLine(x - 8, y - s - 4, x + 8, y - s - 4, layer = "OUTLINE", color = 7)
    }

    // ============================================================
    // UTILITY HELPERS
    // ============================================================

    /**
     * Extract the number of bars from a string like "3#16" or "4T20".
     * Returns 1 if parsing fails.
     */
    private fun extractBarCount(barStr: String): Int {
        // Patterns: "3#16", "4T20", "T12@200", "T16@150"
        val match = Regex("^(\\d+)").find(barStr)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }

    private fun formatValue(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format("%.1f", value)
        }
    }
}
