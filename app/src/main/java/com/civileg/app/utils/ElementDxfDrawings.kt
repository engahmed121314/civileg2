package com.civileg.app.utils

/**
 * ElementDxfDrawings — data-driven [DxfDrawable] factories for structural elements.
 *
 * ADR-004: DXF canonical line (DxfWriter AC1027). ADR-009: English ONLY in DXF exports.
 *
 * Every factory draws a schematic in millimetres with the origin at the element's
 * bottom-left corner and writes reinforcement onto the conventional layers
 * (REBAR / STIRRUPS / BEAMS / COLUMNS / SLABS / FOOTINGS / WALLS / TEXT).
 *
 * These factories make [ExportManager.exportBoth] real: pass the produced
 * [DxfDrawable] alongside the PDF result to emit PDF + DXF in one call.
 */
object ElementDxfDrawings {

    // ───────────────────────────────────────────────────────────────────────────
    // CONCRETE ELEMENTS
    // ───────────────────────────────────────────────────────────────────────────

    /** Beam cross-section: outline, closed stirrup and longitudinal bars. */
    fun beamSection(
        title: String,
        widthMm: Double,
        depthMm: Double,
        coverMm: Double,
        mainBarDiaMm: Double,
        mainBarCount: Int,
        stirrupDiaMm: Double,
        stirrupSpacingMm: Double
    ): DxfDrawable = DxfDrawable { ex ->
        ex.sheetFrame(widthMm, depthMm)
        ex.rect(0.0, 0.0, widthMm, depthMm, "BEAMS")
        ex.stirrupRect(coverMm, coverMm, widthMm - 2 * coverMm, depthMm - 2 * coverMm)
        ex.perimeterPoints(coverMm, coverMm, widthMm - 2 * coverMm, depthMm - 2 * coverMm, mainBarCount)
            .forEach { (x, y) -> ex.rebar(x, y, mainBarDiaMm) }
        ex.notes(
            title,
            widthMm,
            depthMm,
            "STIRRUPS Ø${fmt(stirrupDiaMm)} @ ${fmt(stirrupSpacingMm)} mm",
            "MAIN ${mainBarCount}Ø${fmt(mainBarDiaMm)}"
        )
    }

    /** Beam elevation: span outline with top/bottom bars and stirrup ticks. */
    fun beamElevation(
        title: String,
        spanMm: Double,
        depthMm: Double,
        coverMm: Double,
        mainBarDiaMm: Double,
        stirrupDiaMm: Double,
        stirrupSpacingMm: Double
    ): DxfDrawable = DxfDrawable { ex ->
        ex.sheetFrame(spanMm, depthMm)
        ex.rect(0.0, 0.0, spanMm, depthMm, "BEAMS")
        val ins = coverMm + stirrupDiaMm
        ex.hLine(ins, depthMm - ins, spanMm - ins, "REBAR")
        ex.hLine(ins, ins, spanMm - ins, "REBAR")
        var s = coverMm
        while (s <= spanMm - coverMm) {
            ex.vLine(s, coverMm, depthMm - coverMm, "STIRRUPS")
            s += stirrupSpacingMm.coerceAtLeast(50.0)
        }
        ex.notes(title, spanMm, depthMm, "SPAN ${fmt(spanMm)} mm", "MAIN Ø${fmt(mainBarDiaMm)}")
    }

    /** Column cross-section: outline, tie and longitudinal bars. */
    fun columnSection(
        title: String,
        widthMm: Double,
        depthMm: Double,
        coverMm: Double,
        mainBarDiaMm: Double,
        mainBarCount: Int,
        tieDiaMm: Double,
        tieSpacingMm: Double
    ): DxfDrawable = DxfDrawable { ex ->
        ex.sheetFrame(widthMm, depthMm)
        ex.rect(0.0, 0.0, widthMm, depthMm, "COLUMNS")
        ex.stirrupRect(coverMm, coverMm, widthMm - 2 * coverMm, depthMm - 2 * coverMm)
        ex.perimeterPoints(coverMm, coverMm, widthMm - 2 * coverMm, depthMm - 2 * coverMm, mainBarCount)
            .forEach { (x, y) -> ex.rebar(x, y, mainBarDiaMm) }
        ex.notes(
            title,
            widthMm,
            depthMm,
            "TIES Ø${fmt(tieDiaMm)} @ ${fmt(tieSpacingMm)} mm",
            "MAIN ${mainBarCount}Ø${fmt(mainBarDiaMm)}"
        )
    }

    /** Slab section: thickness outline with top and bottom reinforcement. */
    fun slabSection(
        title: String,
        spanMm: Double,
        thicknessMm: Double,
        coverMm: Double,
        mainBarDiaMm: Double,
        mainSpacingMm: Double,
        temperatureBarDiaMm: Double = mainBarDiaMm
    ): DxfDrawable = DxfDrawable { ex ->
        ex.sheetFrame(spanMm, thicknessMm)
        ex.rect(0.0, 0.0, spanMm, thicknessMm, "SLABS")
        val botY = coverMm + mainBarDiaMm / 2.0
        val topY = thicknessMm - coverMm - temperatureBarDiaMm / 2.0
        var x = coverMm
        while (x <= spanMm - coverMm) {
            ex.rebar(x, botY, mainBarDiaMm)
            ex.rebar(x, topY, temperatureBarDiaMm)
            x += mainSpacingMm.coerceAtLeast(50.0)
        }
        ex.notes(
            title,
            spanMm,
            thicknessMm,
            "MAIN Ø${fmt(mainBarDiaMm)} @ ${fmt(mainSpacingMm)} mm",
            "T=${fmt(thicknessMm)} mm"
        )
    }

    /** Isolated footing section with column stub and bottom reinforcement. */
    fun footingSection(
        title: String,
        widthMm: Double,
        thicknessMm: Double,
        coverMm: Double,
        barDiaMm: Double,
        barSpacingMm: Double,
        columnWidthMm: Double = 0.0,
        columnStubHeightMm: Double = thicknessMm
    ): DxfDrawable = DxfDrawable { ex ->
        ex.sheetFrame(widthMm, thicknessMm + columnStubHeightMm)
        ex.rect(0.0, 0.0, widthMm, thicknessMm, "FOOTINGS")
        if (columnWidthMm > 0.0) {
            val cx = (widthMm - columnWidthMm) / 2.0
            ex.rect(cx, thicknessMm, columnWidthMm, columnStubHeightMm, "COLUMNS")
        }
        val barY = coverMm + barDiaMm / 2.0
        var x = coverMm
        while (x <= widthMm - coverMm) {
            ex.rebar(x, barY, barDiaMm)
            x += barSpacingMm.coerceAtLeast(50.0)
        }
        ex.notes(
            title,
            widthMm,
            thicknessMm + columnStubHeightMm,
            "BOTTOM Ø${fmt(barDiaMm)} @ ${fmt(barSpacingMm)} mm",
            "T=${fmt(thicknessMm)} mm"
        )
    }

    /** Shear wall elevation: outline with vertical and horizontal reinforcement grid. */
    fun wallElevation(
        title: String,
        widthMm: Double,
        heightMm: Double,
        coverMm: Double,
        verticalBarDiaMm: Double,
        verticalSpacingMm: Double,
        horizontalBarDiaMm: Double,
        horizontalSpacingMm: Double
    ): DxfDrawable = DxfDrawable { ex ->
        ex.sheetFrame(widthMm, heightMm)
        ex.rect(0.0, 0.0, widthMm, heightMm, "WALLS")
        var x = coverMm
        while (x <= widthMm - coverMm) {
            ex.vLine(x, coverMm, heightMm - coverMm, "REBAR")
            x += verticalSpacingMm.coerceAtLeast(50.0)
        }
        var y = coverMm
        while (y <= heightMm - coverMm) {
            ex.hLine(coverMm, y, widthMm - coverMm, "STIRRUPS")
            y += horizontalSpacingMm.coerceAtLeast(50.0)
        }
        ex.notes(
            title,
            widthMm,
            heightMm,
            "VERT Ø${fmt(verticalBarDiaMm)} @ ${fmt(verticalSpacingMm)}",
            "HORZ Ø${fmt(horizontalBarDiaMm)} @ ${fmt(horizontalSpacingMm)}"
        )
    }

    /** Combined footing section with two column stubs. */
    fun combinedFootingSection(
        title: String,
        lengthMm: Double,
        thicknessMm: Double,
        coverMm: Double,
        barDiaMm: Double,
        barSpacingMm: Double,
        columnWidthMm: Double,
        columnPositionsMm: List<Double>,
        columnStubHeightMm: Double = thicknessMm
    ): DxfDrawable = DxfDrawable { ex ->
        ex.sheetFrame(lengthMm, thicknessMm + columnStubHeightMm)
        ex.rect(0.0, 0.0, lengthMm, thicknessMm, "FOOTINGS")
        columnPositionsMm.forEach { c ->
            ex.rect(c - columnWidthMm / 2.0, thicknessMm, columnWidthMm, columnStubHeightMm, "COLUMNS")
        }
        val barY = coverMm + barDiaMm / 2.0
        // bottom, top and mid reinforcement mats
        listOf(barY, thicknessMm - coverMm - barDiaMm / 2.0, thicknessMm / 2.0).forEach { y ->
            var x = coverMm
            while (x <= lengthMm - coverMm) {
                ex.rebar(x, y, barDiaMm)
                x += barSpacingMm.coerceAtLeast(50.0)
            }
        }
        ex.notes(
            title,
            lengthMm,
            thicknessMm + columnStubHeightMm,
            "MAIN Ø${fmt(barDiaMm)} @ ${fmt(barSpacingMm)} mm",
            "L=${fmt(lengthMm)} mm"
        )
    }

    /** Pile elevation: shaft outline, vertical bars and ties. */
    fun pileElevation(
        title: String,
        lengthMm: Double,
        diameterMm: Double,
        coverMm: Double,
        mainBarDiaMm: Double,
        mainBarCount: Int,
        tieDiaMm: Double,
        tieSpacingMm: Double
    ): DxfDrawable = DxfDrawable { ex ->
        ex.sheetFrame(diameterMm, lengthMm)
        ex.rect(0.0, 0.0, diameterMm, lengthMm, "FOOTINGS")
        val r = diameterMm / 2.0 - coverMm
        val cx = diameterMm / 2.0
        val cy = lengthMm / 2.0
        ex.perimeterPoints(cx - r, cy - r, 2 * r, 2 * r, mainBarCount)
            .forEach { (x, _) -> ex.vLine(x, coverMm, lengthMm - coverMm, "REBAR") }
        var y = coverMm
        while (y <= lengthMm - coverMm) {
            ex.hLine(coverMm, y, diameterMm - coverMm, "STIRRUPS")
            y += tieSpacingMm.coerceAtLeast(50.0)
        }
        ex.notes(title, diameterMm, lengthMm, "Ø${fmt(diameterMm)} mm", "TIES Ø${fmt(tieDiaMm)}")
    }

    /** Retaining wall section: base slab + stem with reinforcement. */
    fun retainingWallSection(
        title: String,
        baseWidthMm: Double,
        baseThicknessMm: Double,
        stemHeightMm: Double,
        stemThicknessMm: Double,
        coverMm: Double,
        barDiaMm: Double,
        barSpacingMm: Double
    ): DxfDrawable = DxfDrawable { ex ->
        val totalH = baseThicknessMm + stemHeightMm
        ex.sheetFrame(baseWidthMm, totalH)
        ex.rect(0.0, 0.0, baseWidthMm, baseThicknessMm, "FOOTINGS")
        val stemX = baseWidthMm - stemThicknessMm
        ex.rect(stemX, baseThicknessMm, stemThicknessMm, stemHeightMm, "WALLS")
        val barX = stemX + coverMm + barDiaMm / 2.0
        var y = baseThicknessMm + coverMm
        while (y <= totalH - coverMm) {
            ex.rebar(barX, y, barDiaMm)
            y += barSpacingMm.coerceAtLeast(50.0)
        }
        ex.notes(
            title,
            baseWidthMm,
            totalH,
            "STEM H=${fmt(stemHeightMm)} mm",
            "MAIN Ø${fmt(barDiaMm)} @ ${fmt(barSpacingMm)}"
        )
    }

    /** Stair flight section: waist slab, steps and main reinforcement. */
    fun stairSection(
        title: String,
        runMm: Double,
        riseMm: Double,
        steps: Int,
        waistThicknessMm: Double,
        coverMm: Double,
        barDiaMm: Double,
        barSpacingMm: Double
    ): DxfDrawable = DxfDrawable { ex ->
        val totalRise = riseMm * steps
        ex.sheetFrame(runMm, totalRise + waistThicknessMm)
        val stepRun = runMm / steps
        val profile = ArrayList<Pair<Double, Double>>(steps * 2 + 2)
        profile += 0.0 to waistThicknessMm
        for (i in 0 until steps) {
            val x0 = i * stepRun
            profile += x0 to (waistThicknessMm + i * riseMm)
            profile += x0 to (waistThicknessMm + (i + 1) * riseMm)
        }
        profile += runMm to (waistThicknessMm + totalRise)
        ex.addPolyline(profile, "SLABS", closed = false)
        val barLen = kotlin.math.hypot(runMm, totalRise)
        var d = coverMm
        while (d <= barLen - coverMm) {
            val t = if (barLen > 0.0) d / barLen else 0.0
            ex.rebar(coverMm + t * runMm, waistThicknessMm / 2.0 + t * totalRise, barDiaMm)
            d += barSpacingMm.coerceAtLeast(50.0)
        }
        ex.notes(title, runMm, totalRise + waistThicknessMm, "$steps steps", "MAIN Ø${fmt(barDiaMm)} @ ${fmt(barSpacingMm)}")
    }

    /** Rectangular tank section: base slab, walls and reinforcement. */
    fun tankSection(
        title: String,
        widthMm: Double,
        heightMm: Double,
        wallThicknessMm: Double,
        coverMm: Double,
        barDiaMm: Double,
        barSpacingMm: Double
    ): DxfDrawable = DxfDrawable { ex ->
        ex.sheetFrame(widthMm, heightMm)
        // base
        ex.rect(0.0, 0.0, widthMm, wallThicknessMm, "FOOTINGS")
        // walls
        ex.rect(0.0, wallThicknessMm, wallThicknessMm, heightMm - wallThicknessMm, "WALLS")
        ex.rect(widthMm - wallThicknessMm, wallThicknessMm, wallThicknessMm, heightMm - wallThicknessMm, "WALLS")
        val ins = wallThicknessMm / 2.0
        var y = wallThicknessMm + coverMm
        while (y <= heightMm - coverMm) {
            ex.rebar(coverMm + ins, y, barDiaMm)
            ex.rebar(widthMm - coverMm - ins, y, barDiaMm)
            y += barSpacingMm.coerceAtLeast(50.0)
        }
        ex.notes(title, widthMm, heightMm, "WALL t=${fmt(wallThicknessMm)} mm", "MAIN Ø${fmt(barDiaMm)} @ ${fmt(barSpacingMm)}")
    }

    // ───────────────────────────────────────────────────────────────────────────
    // STEEL ELEMENTS
    // ───────────────────────────────────────────────────────────────────────────

    /** Steel I-section outline (flanges + web). */
    fun steelISection(
        title: String,
        heightMm: Double,
        widthMm: Double,
        webThicknessMm: Double,
        flangeThicknessMm: Double
    ): DxfDrawable = DxfDrawable { ex ->
        ex.sheetFrame(widthMm, heightMm)
        val fw = (widthMm - webThicknessMm) / 2.0
        // bottom flange
        ex.rect(0.0, 0.0, widthMm, flangeThicknessMm, "COLUMNS")
        // top flange
        ex.rect(0.0, heightMm - flangeThicknessMm, widthMm, flangeThicknessMm, "COLUMNS")
        // web
        ex.rect(fw, flangeThicknessMm, webThicknessMm, heightMm - 2 * flangeThicknessMm, "COLUMNS")
        ex.notes(title, widthMm, heightMm, "h=${fmt(heightMm)} mm", "b=${fmt(widthMm)} mm")
    }

    /** Steel base plate: plate, anchor bolts and column footprint. */
    fun basePlate(
        title: String,
        plateWidthMm: Double,
        plateLengthMm: Double,
        plateThicknessMm: Double,
        boltDiaMm: Double,
        boltCount: Int,
        columnWidthMm: Double,
        columnDepthMm: Double,
        edgeDistanceMm: Double = 50.0
    ): DxfDrawable = DxfDrawable { ex ->
        ex.sheetFrame(plateWidthMm, plateLengthMm)
        ex.rect(0.0, 0.0, plateWidthMm, plateLengthMm, "FOOTINGS")
        val cx = (plateWidthMm - columnWidthMm) / 2.0
        val cy = (plateLengthMm - columnDepthMm) / 2.0
        ex.rect(cx, cy, columnWidthMm, columnDepthMm, "COLUMNS")
        ex.perimeterPoints(
            edgeDistanceMm,
            edgeDistanceMm,
            plateWidthMm - 2 * edgeDistanceMm,
            plateLengthMm - 2 * edgeDistanceMm,
            boltCount
        ).forEach { (x, y) -> ex.rebar(x, y, boltDiaMm, "REBAR") }
        ex.notes(title, plateWidthMm, plateLengthMm, "PLATE t=${fmt(plateThicknessMm)} mm", "$boltCount x Ø${fmt(boltDiaMm)}")
    }

    // ───────────────────────────────────────────────────────────────────────────
    // GENERIC FALLBACK
    // ───────────────────────────────────────────────────────────────────────────

    /** Generic rectangular element outline with a title. */
    fun generic(title: String, widthMm: Double, heightMm: Double): DxfDrawable = DxfDrawable { ex ->
        ex.sheetFrame(widthMm, heightMm)
        ex.rect(0.0, 0.0, widthMm, heightMm, "0")
        ex.notes(title, widthMm, heightMm, "W=${fmt(widthMm)} mm", "H=${fmt(heightMm)} mm")
    }

    // ───────────────────────────────────────────────────────────────────────────
    // PRIVATE DRAWING HELPERS
    // ───────────────────────────────────────────────────────────────────────────

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(java.util.Locale.US, "%.1f", v)

    private fun DxfExporter.rect(x: Double, y: Double, w: Double, h: Double, layer: String) {
        addLine2D(x, y, x + w, y, layer)
        addLine2D(x + w, y, x + w, y + h, layer)
        addLine2D(x + w, y + h, x, y + h, layer)
        addLine2D(x, y + h, x, y, layer)
    }

    private fun DxfExporter.stirrupRect(x: Double, y: Double, w: Double, h: Double) {
        rect(x, y, w, h, "STIRRUPS")
    }

    private fun DxfExporter.rebar(x: Double, y: Double, dia: Double, layer: String = "REBAR") {
        addCircle(x, y, (dia / 2.0).coerceAtLeast(1.0), layer)
    }

    private fun DxfExporter.hLine(x1: Double, y: Double, x2: Double, layer: String) {
        addLine2D(x1, y, x2, y, layer)
    }

    private fun DxfExporter.vLine(x: Double, y1: Double, y2: Double, layer: String) {
        addLine2D(x, y1, x, y2, layer)
    }

    /** Drawing border sized to the element, so the model is comfortably framed. */
    private fun DxfExporter.sheetFrame(contentWidth: Double, contentHeight: Double) {
        val pad = (maxOf(contentWidth, contentHeight) * 0.12).coerceAtLeast(25.0)
        val w = contentWidth + 2 * pad
        val h = contentHeight + 2 * pad
        rect(pad, pad, w - 2 * pad, h - 2 * pad, "0")
    }

    private fun DxfExporter.notes(title: String, widthMm: Double, heightMm: Double, line1: String, line2: String) {
        val th = (minOf(widthMm, heightMm) / 12.0).coerceIn(15.0, 60.0)
        addText(0.0, -th * 2.2, title, th, "TEXT")
        addText(0.0, -th * 4.0, line1, th * 0.8, "TEXT")
        addText(widthMm * 0.55, -th * 4.0, line2, th * 0.8, "TEXT")
    }

    /**
     * [count] points spread at equal arc-length intervals around the perimeter of the
     * rectangle, starting at the bottom-left corner. Used to lay out longitudinal bars.
     */
    private fun DxfExporter.perimeterPoints(
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        count: Int
    ): List<Pair<Double, Double>> {
        if (count <= 0 || w <= 0.0 || h <= 0.0) return emptyList()
        val perim = 2.0 * (w + h)
        return (0 until count).map { i -> edgePoint(x, y, w, h, perim * i / count) }
    }

    private fun edgePoint(x: Double, y: Double, w: Double, h: Double, sIn: Double): Pair<Double, Double> {
        var s = sIn
        if (s <= w) return (x + s) to y
        s -= w
        if (s <= h) return (x + w) to (y + s)
        s -= h
        if (s <= w) return (x + w - s) to (y + h)
        s -= w
        return x to (y + h - s)
    }
}
