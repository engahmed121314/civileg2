package com.civileg.app.utils

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * DxfExporter — standards-compliant AC1027 (AutoCAD 2007+) DXF writer.
 *
 * The writer emits the full object graph required by modern CAD applications:
 * HEADER, TABLES (VPORT/LTYPE/LAYER/STYLE/VIEW/UCS/APPID/DIMSTYLE/BLOCK_RECORD),
 * BLOCKS (model + paper space), ENTITIES and OBJECTS (dictionaries + layouts).
 * Every entity carries a unique handle, an owner handle and the required
 * subclass markers, which is what AutoCAD, BricsCAD, LibreCAD, ODA-based
 * viewers and online converters expect.
 *
 * Coordinates are millimetres ($INSUNITS = 4).
 *
 * ADR-004: DXF canonical line. ADR-009: English/ASCII only in DXF exports.
 */
class DxfExporter {

    companion object {
        private const val TAG = "DxfExporter"
        private const val DXF_VERSION = "AC1027"
        private const val MODEL_SPACE_BLOCK_RECORD = "1F"
        private const val PAPER_SPACE_BLOCK_RECORD = "1B"
        private const val MODEL_SPACE_BLOCK = "20"
        private const val MODEL_SPACE_ENDBLK = "21"
        private const val PAPER_SPACE_BLOCK = "22"
        private const val PAPER_SPACE_ENDBLK = "23"
        private const val LAYER_TABLE = "2"
        private const val LTYPE_TABLE = "5"
        private const val STYLE_TABLE = "3"
        private const val VIEW_TABLE = "6"
        private const val UCS_TABLE = "7"
        private const val VPORT_TABLE = "8"
        private const val APPID_TABLE = "9"
        private const val DIMSTYLE_TABLE = "A"
        private const val BLOCK_RECORD_TABLE = "1"
        private const val ROOT_DICTIONARY = "C"
        private const val GROUP_DICTIONARY = "D"
        private const val LAYOUT_DICTIONARY = "E"
        private const val MODEL_LAYOUT = "1A"
        private const val PAPER_LAYOUT = "1C"
        private const val FIRST_ENTITY_HANDLE = 0x1000
        private const val FIRST_LAYER_HANDLE = 0x100

        /** Layer name -> ACI colour used for the standard structural layers. */
        private val STANDARD_LAYERS = linkedMapOf(
            "0" to 7,
            "AXES" to 1,
            "COLUMNS" to 4,
            "BEAMS" to 5,
            "SLABS" to 3,
            "FOOTINGS" to 3,
            "WALLS" to 5,
            "REBAR" to 1,
            "STIRRUPS" to 6,
            "DIMENSIONS" to 2,
            "TEXT" to 7,
            "HATCH" to 8,
            "BBS" to 7,
            "SHEET" to 7,
            "TITLE" to 7
        )

        private fun fmt(value: Double): String {
            if (!value.isFinite()) return "0.0"
            return String.format(Locale.US, "%.4f", value)
                .trimEnd('0')
                .trimEnd('.')
                .ifEmpty { "0.0" }
        }

        /**
         * Write a DXF companion (`<pdfName>.dxf`) next to [pdfFile].
         * Best-effort: returns the DXF file, or null if writing failed.
         */
        @JvmStatic
        fun writeSibling(
            pdfFile: File,
            drawing: DxfDrawable,
            bbsEntries: List<BbsEntry> = emptyList()
        ): File? = try {
            val dxf = File(pdfFile.parentFile, pdfFile.nameWithoutExtension + ".dxf")
            DxfExporter().exportDxf(drawing, dxf.absolutePath, bbsEntries)
        } catch (e: Exception) {
            Log.e(TAG, "DXF companion export failed: ${e.message}", e)
            null
        }
    }

    private val entities = StringBuilder()
    private val usedLayers = linkedSetOf("0")
    private var currentLayer = "0"
    private var handleCounter = FIRST_ENTITY_HANDLE
    private var layerHandleCounter = FIRST_LAYER_HANDLE

    private var minX = Double.POSITIVE_INFINITY
    private var maxX = Double.NEGATIVE_INFINITY
    private var minY = Double.POSITIVE_INFINITY
    private var maxY = Double.NEGATIVE_INFINITY
    private var minZ = Double.POSITIVE_INFINITY
    private var maxZ = Double.NEGATIVE_INFINITY
    private var boundsInitialized = false

    // ───────────────────────────────────────────────────────────────────────────
    // BOOKKEEPING
    // ───────────────────────────────────────────────────────────────────────────

    private fun reset() {
        entities.setLength(0)
        usedLayers.clear()
        usedLayers.add("0")
        currentLayer = "0"
        handleCounter = FIRST_ENTITY_HANDLE
        layerHandleCounter = FIRST_LAYER_HANDLE
        minX = Double.POSITIVE_INFINITY
        maxX = Double.NEGATIVE_INFINITY
        minY = Double.POSITIVE_INFINITY
        maxY = Double.NEGATIVE_INFINITY
        minZ = Double.POSITIVE_INFINITY
        maxZ = Double.NEGATIVE_INFINITY
        boundsInitialized = false
    }

    private fun nextHandle(): String = (handleCounter++).toString(16).uppercase(Locale.US)

    private fun nextLayerHandle(): String = (layerHandleCounter++).toString(16).uppercase(Locale.US)

    private fun updateBounds(x: Double, y: Double, z: Double = 0.0) {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
        if (z < minZ) minZ = z
        if (z > maxZ) maxZ = z
        boundsInitialized = true
    }

    /** DXF strings are ASCII only (ADR-009). Unsupported characters become '?'. */
    private fun ascii(text: String): String = buildString(text.length) {
        text.forEach { ch ->
            when {
                ch == '\n' || ch == '\r' || ch == '\t' -> append(' ')
                ch.code in 32..126 -> append(ch)
                else -> append('?')
            }
        }
    }

    private fun layerOf(layer: String): String {
        val cleaned = layer.trim().ifEmpty { currentLayer }
        usedLayers.add(cleaned)
        return cleaned
    }

    private fun header(type: String, layer: String): String {
        val handle = nextHandle()
        entities.append("0\n").append(type).append('\n')
        entities.append("5\n").append(handle).append('\n')
        entities.append("330\n").append(MODEL_SPACE_BLOCK_RECORD).append('\n')
        entities.append("100\nAcDbEntity\n")
        entities.append("8\n").append(layerOf(layer)).append('\n')
        return handle
    }

    // ───────────────────────────────────────────────────────────────────────────
    // PUBLIC API — EXPORT
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Export a [DxfDrawable] to a `.dxf` file.
     *
     * @return the written file, or null when the drawing could not be persisted.
     */
    fun exportDxf(
        drawing: DxfDrawable,
        outputPath: String,
        bbsEntries: List<BbsEntry> = emptyList()
    ): File? {
        val content = renderToString(drawing, bbsEntries)
        return try {
            val file = File(outputPath)
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out -> out.write(content.toByteArray(Charsets.US_ASCII)) }
            file
        } catch (e: Exception) {
            Log.e(TAG, "DXF export failed: ${e.message}", e)
            null
        }
    }

    /** Serialize a [DxfDrawable] to a DXF string (no file I/O). Useful for tests/validation. */
    fun renderToString(
        drawing: DxfDrawable,
        bbsEntries: List<BbsEntry> = emptyList()
    ): String {
        reset()
        drawing.exportToDxf(this)
        appendBbsTableToBuffer(bbsEntries)
        return buildDocument()
    }


    // ───────────────────────────────────────────────────────────────────────────
    // PUBLIC API — ENTITIES
    // ───────────────────────────────────────────────────────────────────────────

    fun setCurrentLayer(layer: String) {
        currentLayer = layer.trim().ifEmpty { "0" }
    }

    fun addLine3D(
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
        layer: String = "0"
    ) {
        header("LINE", layer)
        entities.append("100\nAcDbLine\n")
        entities.append("10\n").append(fmt(x1)).append("\n20\n").append(fmt(y1)).append("\n30\n").append(fmt(z1)).append('\n')
        entities.append("11\n").append(fmt(x2)).append("\n21\n").append(fmt(y2)).append("\n31\n").append(fmt(z2)).append('\n')
        updateBounds(x1, y1, z1)
        updateBounds(x2, y2, z2)
    }

    fun addLine2D(x1: Double, y1: Double, x2: Double, y2: Double, layer: String = "0") {
        addLine3D(x1, y1, 0.0, x2, y2, 0.0, layer)
    }

    fun addCircle(centerX: Double, centerY: Double, radius: Double, layer: String = "0") {
        header("CIRCLE", layer)
        entities.append("100\nAcDbCircle\n")
        entities.append("10\n").append(fmt(centerX)).append("\n20\n").append(fmt(centerY)).append("\n30\n0\n")
        entities.append("40\n").append(fmt(kotlin.math.abs(radius).coerceAtLeast(0.0))).append('\n')
        updateBounds(centerX - radius, centerY - radius)
        updateBounds(centerX + radius, centerY + radius)
    }

    fun addArc(
        centerX: Double, centerY: Double, radius: Double,
        startAngle: Double, endAngle: Double, layer: String = "0"
    ) {
        header("ARC", layer)
        entities.append("100\nAcDbCircle\n")
        entities.append("10\n").append(fmt(centerX)).append("\n20\n").append(fmt(centerY)).append("\n30\n0\n")
        entities.append("40\n").append(fmt(kotlin.math.abs(radius).coerceAtLeast(0.0))).append('\n')
        entities.append("100\nAcDbArc\n")
        entities.append("50\n").append(fmt(startAngle)).append("\n51\n").append(fmt(endAngle)).append('\n')
        updateBounds(centerX - radius, centerY - radius)
        updateBounds(centerX + radius, centerY + radius)
    }

    fun addPolyline(points: List<Pair<Double, Double>>, layer: String = "0", closed: Boolean = false) {
        if (points.isEmpty()) return
        header("LWPOLYLINE", layer)
        entities.append("100\nAcDbPolyline\n")
        entities.append("90\n").append(points.size).append('\n')
        entities.append("70\n").append(if (closed) "1" else "0").append('\n')
        entities.append("43\n0\n")
        points.forEach { (x, y) ->
            entities.append("10\n").append(fmt(x)).append("\n20\n").append(fmt(y)).append('\n')
            updateBounds(x, y)
        }
    }

    fun addText(
        x: Double, y: Double, text: String,
        height: Double = 100.0, layer: String = "TEXT", rotation: Double = 0.0
    ) {
        val value = ascii(text)
        if (value.isBlank()) return
        header("TEXT", layer)
        entities.append("100\nAcDbText\n")
        entities.append("10\n").append(fmt(x)).append("\n20\n").append(fmt(y)).append("\n30\n0\n")
        entities.append("40\n").append(fmt(height.coerceAtLeast(0.1))).append('\n')
        entities.append("1\n").append(value).append('\n')
        entities.append("50\n").append(fmt(rotation)).append('\n')
        entities.append("7\nStandard\n")
        updateBounds(x, y)
        updateBounds(x + value.length * height * 0.62, y + height)
    }

    fun addMText(
        x: Double, y: Double, text: String,
        height: Double = 100.0, layer: String = "TEXT", width: Double = 1000.0
    ) {
        val value = ascii(text)
        if (value.isBlank()) return
        header("MTEXT", layer)
        entities.append("100\nAcDbMText\n")
        entities.append("10\n").append(fmt(x)).append("\n20\n").append(fmt(y)).append("\n30\n0\n")
        entities.append("40\n").append(fmt(height.coerceAtLeast(0.1))).append('\n')
        entities.append("41\n").append(fmt(width.coerceAtLeast(1.0))).append('\n')
        entities.append("71\n1\n")
        entities.append("72\n1\n")
        entities.append("1\n").append(value).append('\n')
        entities.append("7\nStandard\n")
        entities.append("50\n0\n")
        updateBounds(x, y)
        updateBounds(x + width, y + height * 2)
    }

    /**
     * Solid or pattern hatch bounded by a closed polygon.
     * Emits the full AcDbHatch record so strict readers accept it.
     */
    fun addHatch(pattern: String, scale: Double, points: List<Pair<Double, Double>>, layer: String = "HATCH") {
        if (points.size < 3) return
        val solid = pattern.equals("SOLID", ignoreCase = true) || pattern.isBlank()
        header("HATCH", layer)
        entities.append("100\nAcDbHatch\n")
        entities.append("10\n0\n20\n0\n30\n0\n")
        entities.append("210\n0\n220\n0\n230\n1\n")
        entities.append("2\n").append(if (solid) "SOLID" else ascii(pattern).uppercase(Locale.US)).append('\n')
        entities.append("70\n").append(if (solid) "1" else "0").append('\n')
        entities.append("71\n0\n")
        entities.append("91\n1\n")
        entities.append("92\n3\n")   // external boundary + polyline
        entities.append("72\n0\n")   // no bulge
        entities.append("73\n1\n")   // closed
        entities.append("93\n").append(points.size).append('\n')
        points.forEach { (x, y) ->
            entities.append("10\n").append(fmt(x)).append("\n20\n").append(fmt(y)).append('\n')
            updateBounds(x, y)
        }
        entities.append("97\n0\n")
        entities.append("75\n1\n")
        entities.append("76\n1\n")
        entities.append("52\n45\n")
        entities.append("41\n").append(fmt(scale.coerceAtLeast(0.0001))).append('\n')
        entities.append("77\n0\n")
        entities.append("78\n0\n")
        entities.append("47\n0\n")
        entities.append("98\n0\n")
    }

    /**
     * Horizontal dimension line at [y] between [x1] and [x2].
     * Extension lines run from the dimension line towards the element by [offset].
     */
    fun addDimensionH(
        x1: Double, x2: Double, y: Double, text: String,
        layer: String = "DIMENSIONS", offset: Double = 60.0
    ) {
        val ext = kotlin.math.abs(offset)
        addLine2D(x1, y, x1, y + ext, layer)
        addLine2D(x2, y, x2, y + ext, layer)
        addLine2D(x1, y, x2, y, layer)
        val tick = ext * 0.2
        addLine2D(x1, y, x1 + tick, y + tick * 0.7, layer)
        addLine2D(x1, y, x1 + tick, y - tick * 0.7, layer)
        addLine2D(x2, y, x2 - tick, y + tick * 0.7, layer)
        addLine2D(x2, y, x2 - tick, y - tick * 0.7, layer)
        val mid = (x1 + x2) / 2.0
        val h = (ext * 0.28).coerceAtLeast(12.0)
        addText(mid - text.length * h * 0.3, y + h * 0.4, text, h, layer)
    }

    /** Vertical dimension line at [x] between [y1] and [y2] (see [addDimensionH]). */
    fun addDimensionV(
        y1: Double, y2: Double, x: Double, text: String,
        layer: String = "DIMENSIONS", offset: Double = 60.0
    ) {
        val ext = kotlin.math.abs(offset)
        addLine2D(x, y1, x + ext, y1, layer)
        addLine2D(x, y2, x + ext, y2, layer)
        addLine2D(x, y1, x, y2, layer)
        val tick = ext * 0.2
        addLine2D(x, y1, x + tick * 0.7, y1 + tick, layer)
        addLine2D(x, y1, x - tick * 0.7, y1 + tick, layer)
        addLine2D(x, y2, x + tick * 0.7, y2 - tick, layer)
        addLine2D(x, y2, x - tick * 0.7, y2 - tick, layer)
        val h = (ext * 0.28).coerceAtLeast(12.0)
        addText(x - h * 0.4, (y1 + y2) / 2.0 - text.length * h * 0.3, text, h, layer, rotation = 90.0)
    }

    /** Draws a bordered drawing sheet with a compact title block in the lower-right corner. */
    fun addSheetFrame(
        contentWidth: Double,
        contentHeight: Double,
        title: String,
        subtitle: String = "",
        scaleText: String = "AS SHOWN",
        project: String = "CIVIL EG",
        date: String = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
    ) {
        val pad = (maxOf(contentWidth, contentHeight) * 0.08).coerceIn(40.0, 260.0)
        val blockWidth = (contentWidth * 0.55).coerceIn(150.0, 2100.0)
        val blockHeight = blockWidth * 0.28
        val left = -pad
        val right = contentWidth + pad
        val bottom = -pad - blockHeight - 40.0
        val top = contentHeight + pad

        // Sheet border
        addPolyline(
            listOf(left to bottom, right to bottom, right to top, left to top),
            "SHEET",
            closed = true
        )

        // Title block rows
        val bx = right - blockWidth
        val by = bottom
        addPolyline(
            listOf(bx to by, right to by, right to by + blockHeight, bx to by + blockHeight),
            "TITLE",
            closed = true
        )
        addLine2D(bx, by + blockHeight * 0.42, right, by + blockHeight * 0.42, "TITLE")
        addLine2D(bx + blockWidth * 0.55, by, bx + blockWidth * 0.55, by + blockHeight * 0.42, "TITLE")
        addLine2D(bx + blockWidth * 0.55, by + blockHeight * 0.42, bx + blockWidth * 0.55, by + blockHeight, "TITLE")

        val titleHeight = (blockHeight * 0.16).coerceIn(6.0, 64.0)
        addText(bx + blockWidth * 0.02, by + blockHeight * 0.62, title, titleHeight, "TITLE")
        if (subtitle.isNotBlank()) {
            addText(bx + blockWidth * 0.02, by + blockHeight * 0.5, subtitle, titleHeight * 0.66, "TITLE")
        }
        addText(bx + blockWidth * 0.02, by + blockHeight * 0.13, project, titleHeight * 0.66, "TITLE")
        addText(bx + blockWidth * 0.57, by + blockHeight * 0.13, "SCALE: $scaleText", titleHeight * 0.56, "TITLE")
        addText(bx + blockWidth * 0.57, by + blockHeight * 0.52, "DATE: $date", titleHeight * 0.56, "TITLE")

        // Drawing notes area (left of the title block)
        addText(bx - blockWidth * 0.02, bottom + blockHeight * 0.55, "ALL DIMENSIONS IN MILLIMETRES", titleHeight * 0.56, "TITLE")
        addText(bx - blockWidth * 0.02, bottom + blockHeight * 0.25, "DO NOT SCALE FROM DRAWING", titleHeight * 0.56, "TITLE")
    }

    // ───────────────────────────────────────────────────────────────────────────
    // BAR BENDING SCHEDULE
    // ───────────────────────────────────────────────────────────────────────────

    /** Append a Bar Bending Schedule table below the element drawing on layer `BBS`. */
    fun appendBbsTable(entries: List<BbsEntry>) = appendBbsTableToBuffer(entries)

    private fun appendBbsTableToBuffer(entries: List<BbsEntry>) {
        if (entries.isEmpty()) return
        val x = 0.0
        val rowHeight = 300.0
        var y = -2500.0
        val colX = doubleArrayOf(0.0, 1200.0, 1900.0, 2600.0, 3300.0, 4900.0, 6300.0)

        addText(x, y, "BAR BENDING SCHEDULE", 220.0, "BBS")
        y -= rowHeight
        val headers = listOf("Member", "Bar", "Dia (mm)", "Shape", "Qty", "Length/bar (mm)", "Total (kg)")
        headers.forEachIndexed { i, h -> addText(x + colX[i], y, h, 160.0, "BBS") }
        y -= rowHeight

        for (entry in entries) {
            val cells = listOf(
                entry.memberMark,
                entry.barMark,
                entry.diameter.toString(),
                entry.shapeCode.toString(),
                entry.count.toString(),
                String.format(Locale.US, "%.0f", entry.totalLengthPerBar),
                String.format(Locale.US, "%.2f", entry.totalWeightKg)
            )
            cells.forEachIndexed { i, cell -> addText(x + colX[i], y, cell, 150.0, "BBS") }
            y -= rowHeight
        }

        val total = entries.sumOf { it.totalWeightKg }
        addText(
            x + colX[5],
            y - rowHeight * 0.4,
            "TOTAL STEEL: ${String.format(Locale.US, "%.2f", total)} kg",
            180.0,
            "BBS"
        )
    }

    // ───────────────────────────────────────────────────────────────────────────
    // DOCUMENT ASSEMBLY
    // ───────────────────────────────────────────────────────────────────────────

    private fun buildDocument(): String {
        usedLayers.add("0")

        val extMinX = if (boundsInitialized) minX else 0.0
        val extMinY = if (boundsInitialized) minY else 0.0
        val extMaxX = if (boundsInitialized && maxX > minX) maxX else 420.0
        val extMaxY = if (boundsInitialized && maxY > minY) maxY else 297.0

        val tables = buildTables()
        val blocks = buildBlocks()
        val objects = buildObjects()
        val handSeed = (handleCounter + 0x20).toString(16).uppercase(Locale.US)

        return buildString(entities.length + tables.length + 4096) {
            append(buildHeader(handSeed, extMinX, extMinY, extMaxX, extMaxY))
            append(tables)
            append(blocks)
            append("0\nSECTION\n2\nENTITIES\n")
            append(entities)
            append("0\nENDSEC\n")
            append(objects)
            append("0\nEOF\n")
        }
    }

    private fun buildHeader(
        handSeed: String,
        extMinX: Double, extMinY: Double,
        extMaxX: Double, extMaxY: Double
    ): String = buildString {
        append("0\nSECTION\n2\nHEADER\n")
        append("9\n\$ACADVER\n1\n").append(DXF_VERSION).append('\n')
        append("9\n\$HANDSEED\n5\n").append(handSeed).append('\n')
        append("9\n\$INSBASE\n10\n0\n20\n0\n30\n0\n")
        append("9\n\$EXTMIN\n10\n").append(fmt(extMinX)).append("\n20\n").append(fmt(extMinY)).append("\n30\n0\n")
        append("9\n\$EXTMAX\n10\n").append(fmt(extMaxX)).append("\n20\n").append(fmt(extMaxY)).append("\n30\n0\n")
        append("9\n\$LIMMIN\n10\n").append(fmt(extMinX)).append("\n20\n").append(fmt(extMinY)).append('\n')
        append("9\n\$LIMMAX\n10\n").append(fmt(extMaxX)).append("\n20\n").append(fmt(extMaxY)).append('\n')
        append("9\n\$INSUNITS\n70\n4\n")        // millimetres
        append("9\n\$MEASUREMENT\n70\n1\n")      // metric
        append("9\n\$LTSCALE\n40\n1.0\n")
        append("9\n\$TEXTSTYLE\n7\nStandard\n")
        append("9\n\$CLAYER\n8\n0\n")
        append("9\n\$TEXTSIZE\n40\n2.5\n")
        append("9\n\$TILEMODE\n70\n1\n")
        append("9\n\$ORTHOMODE\n70\n0\n")
        append("9\n\$REGENMODE\n70\n1\n")
        append("9\n\$FILLETRAD\n40\n0.0\n")
        append("9\n\$ANGBASE\n50\n0.0\n")
        append("9\n\$ANGDIR\n70\n0\n")
        append("9\n\$PDMODE\n70\n0\n")
        append("9\n\$PDSIZE\n40\n0.0\n")
        append("9\n\$DWGCODEPAGE\n3\nANSI_1252\n")
        append("0\nENDSEC\n")
    }

    private fun buildTables(): String = buildString {
        append("0\nSECTION\n2\nTABLES\n")

        // ── VPORT ──────────────────────────────────────────────────────────
        append("0\nTABLE\n2\nVPORT\n5\n").append(VPORT_TABLE).append('\n')
        append("330\n0\n100\nAcDbSymbolTable\n70\n1\n")
        append("0\nVPORT\n5\n2F\n330\n").append(VPORT_TABLE).append('\n')
        append("100\nAcDbSymbolTableRecord\n100\nAcDbViewportTableRecord\n")
        append("2\n*Active\n70\n0\n")
        append("10\n0\n20\n0\n11\n1\n21\n1\n")
        append("12\n0\n22\n0\n13\n0\n23\n0\n")
        append("14\n10\n24\n10\n15\n10\n25\n10\n")
        append("16\n0\n26\n0\n36\n1\n17\n0\n27\n0\n37\n0\n")
        append("40\n1000\n41\n1.6\n42\n50\n43\n0\n44\n0\n")
        append("50\n0\n51\n0\n71\n0\n72\n100\n73\n1\n74\n3\n")
        append("75\n0\n76\n0\n77\n0\n78\n0\n")
        append("0\nENDTAB\n")

        // ── LTYPE ──────────────────────────────────────────────────────────
        append("0\nTABLE\n2\nLTYPE\n5\n").append(LTYPE_TABLE).append('\n')
        append("330\n0\n100\nAcDbSymbolTable\n70\n3\n")
        append("0\nLTYPE\n5\n14\n330\n").append(LTYPE_TABLE).append('\n')
        append("100\nAcDbSymbolTableRecord\n100\nAcDbLinetypeTableRecord\n")
        append("2\nCONTINUOUS\n70\n0\n3\nSolid line\n72\n65\n73\n0\n40\n0.0\n")
        append("0\nLTYPE\n5\n15\n330\n").append(LTYPE_TABLE).append('\n')
        append("100\nAcDbSymbolTableRecord\n100\nAcDbLinetypeTableRecord\n")
        append("2\nDASHED\n70\n0\n3\nDashed __ __ __\n72\n65\n73\n2\n40\n12.7\n49\n-6.35\n49\n6.35\n")
        append("0\nLTYPE\n5\n16\n330\n").append(LTYPE_TABLE).append('\n')
        append("100\nAcDbSymbolTableRecord\n100\nAcDbLinetypeTableRecord\n")
        append("2\nDOTTED\n70\n0\n3\nDotted . . .\n72\n65\n73\n2\n40\n3.175\n49\n0.0\n49\n-3.175\n")
        append("0\nENDTAB\n")

        // ── LAYER ──────────────────────────────────────────────────────────
        val layers = LinkedHashMap(STANDARD_LAYERS)
        usedLayers.forEach { name -> layers.putIfAbsent(name, 7) }
        append("0\nTABLE\n2\nLAYER\n5\n").append(LAYER_TABLE).append('\n')
        append("330\n0\n100\nAcDbSymbolTable\n70\n").append(layers.size).append('\n')
        layers.forEach { (name, color) ->
            append("0\nLAYER\n5\n").append(nextLayerHandle()).append('\n')
            append("330\n").append(LAYER_TABLE).append('\n')
            append("100\nAcDbSymbolTableRecord\n100\nAcDbLayerTableRecord\n")
            append("2\n").append(ascii(name)).append('\n')
            append("70\n0\n")
            append("62\n").append(color).append('\n')
            append("6\nCONTINUOUS\n")
            append("370\n-3\n")
        }
        append("0\nENDTAB\n")

        // ── STYLE ──────────────────────────────────────────────────────────
        append("0\nTABLE\n2\nSTYLE\n5\n").append(STYLE_TABLE).append('\n')
        append("330\n0\n100\nAcDbSymbolTable\n70\n1\n")
        append("0\nSTYLE\n5\n11\n330\n").append(STYLE_TABLE).append('\n')
        append("100\nAcDbSymbolTableRecord\n100\nAcDbTextStyleTableRecord\n")
        append("2\nStandard\n70\n0\n40\n0.0\n41\n1.0\n50\n0.0\n71\n0\n42\n2.5\n3\ntxt\n4\n\n")
        append("0\nENDTAB\n")

        // ── VIEW / UCS ─────────────────────────────────────────────────────
        append("0\nTABLE\n2\nVIEW\n5\n").append(VIEW_TABLE).append('\n')
        append("330\n0\n100\nAcDbSymbolTable\n70\n0\n0\nENDTAB\n")
        append("0\nTABLE\n2\nUCS\n5\n").append(UCS_TABLE).append('\n')
        append("330\n0\n100\nAcDbSymbolTable\n70\n0\n0\nENDTAB\n")

        // ── APPID ──────────────────────────────────────────────────────────
        append("0\nTABLE\n2\nAPPID\n5\n").append(APPID_TABLE).append('\n')
        append("330\n0\n100\nAcDbSymbolTable\n70\n1\n")
        append("0\nAPPID\n5\n12\n330\n").append(APPID_TABLE).append('\n')
        append("100\nAcDbSymbolTableRecord\n100\nAcDbRegAppTableRecord\n2\nACAD\n70\n0\n")
        append("0\nENDTAB\n")

        // ── DIMSTYLE ───────────────────────────────────────────────────────
        append("0\nTABLE\n2\nDIMSTYLE\n5\n").append(DIMSTYLE_TABLE).append('\n')
        append("330\n0\n100\nAcDbSymbolTable\n70\n1\n100\nAcDbDimStyleTable\n71\n0\n")
        append("0\nDIMSTYLE\n105\n13\n330\n").append(DIMSTYLE_TABLE).append('\n')
        append("100\nAcDbSymbolTableRecord\n100\nAcDbDimStyleTableRecord\n2\nStandard\n70\n0\n")
        append("0\nENDTAB\n")

        // ── BLOCK_RECORD ───────────────────────────────────────────────────
        append("0\nTABLE\n2\nBLOCK_RECORD\n5\n").append(BLOCK_RECORD_TABLE).append('\n')
        append("330\n0\n100\nAcDbSymbolTable\n70\n2\n")
        append("0\nBLOCK_RECORD\n5\n").append(MODEL_SPACE_BLOCK_RECORD).append('\n')
        append("330\n").append(BLOCK_RECORD_TABLE).append('\n')
        append("100\nAcDbSymbolTableRecord\n100\nAcDbBlockTableRecord\n")
        append("2\n*Model_Space\n")
        append("0\nBLOCK_RECORD\n5\n").append(PAPER_SPACE_BLOCK_RECORD).append('\n')
        append("330\n").append(BLOCK_RECORD_TABLE).append('\n')
        append("100\nAcDbSymbolTableRecord\n100\nAcDbBlockTableRecord\n")
        append("2\n*Paper_Space\n")
        append("0\nENDTAB\n")

        append("0\nENDSEC\n")
    }

    private fun buildBlocks(): String = buildString {
        append("0\nSECTION\n2\nBLOCKS\n")
        append("0\nBLOCK\n5\n").append(MODEL_SPACE_BLOCK).append('\n')
        append("330\n").append(MODEL_SPACE_BLOCK_RECORD).append('\n')
        append("100\nAcDbEntity\n8\n0\n100\nAcDbBlockBegin\n")
        append("2\n*Model_Space\n70\n0\n10\n0\n20\n0\n30\n0\n3\n*Model_Space\n1\n\n")
        append("0\nENDBLK\n5\n").append(MODEL_SPACE_ENDBLK).append('\n')
        append("330\n").append(MODEL_SPACE_BLOCK_RECORD).append('\n')
        append("100\nAcDbEntity\n8\n0\n100\nAcDbBlockEnd\n")

        append("0\nBLOCK\n5\n").append(PAPER_SPACE_BLOCK).append('\n')
        append("330\n").append(PAPER_SPACE_BLOCK_RECORD).append('\n')
        append("100\nAcDbEntity\n8\n0\n100\nAcDbBlockBegin\n")
        append("2\n*Paper_Space\n70\n0\n10\n0\n20\n0\n30\n0\n3\n*Paper_Space\n1\n\n")
        append("0\nENDBLK\n5\n").append(PAPER_SPACE_ENDBLK).append('\n')
        append("330\n").append(PAPER_SPACE_BLOCK_RECORD).append('\n')
        append("100\nAcDbEntity\n8\n0\n100\nAcDbBlockEnd\n")
        append("0\nENDSEC\n")
    }

    private fun buildObjects(): String = buildString {
        append("0\nSECTION\n2\nOBJECTS\n")

        append("0\nDICTIONARY\n5\n").append(ROOT_DICTIONARY).append('\n')
        append("330\n0\n100\nAcDbDictionary\n281\n1\n")
        append("3\nACAD_GROUP\n350\n").append(GROUP_DICTIONARY).append('\n')
        append("3\nACAD_LAYOUT\n350\n").append(LAYOUT_DICTIONARY).append('\n')

        append("0\nDICTIONARY\n5\n").append(GROUP_DICTIONARY).append('\n')
        append("330\n").append(ROOT_DICTIONARY).append('\n')
        append("100\nAcDbDictionary\n281\n0\n")

        append("0\nDICTIONARY\n5\n").append(LAYOUT_DICTIONARY).append('\n')
        append("330\n").append(ROOT_DICTIONARY).append('\n')
        append("100\nAcDbDictionary\n281\n2\n")
        append("3\nModel\n350\n").append(MODEL_LAYOUT).append('\n')
        append("3\nLayout1\n350\n").append(PAPER_LAYOUT).append('\n')

        append(layoutObject(MODEL_LAYOUT, "Model", 1, MODEL_SPACE_BLOCK_RECORD))
        append(layoutObject(PAPER_LAYOUT, "Layout1", 0, PAPER_SPACE_BLOCK_RECORD))

        append("0\nENDSEC\n")
    }

    private fun layoutObject(handle: String, name: String, tabOrder: Int, blockRecord: String): String = buildString {
        append("0\nLAYOUT\n5\n").append(handle).append('\n')
        append("330\n").append(LAYOUT_DICTIONARY).append('\n')
        append("100\nAcDbPlotSettings\n")
        append("1\n\n2\nnone\n4\n\n6\n\n")
        append("40\n0\n41\n0\n42\n0\n43\n0\n44\n0\n45\n0\n46\n0\n47\n0\n48\n0\n49\n0\n")
        append("140\n0\n141\n0\n142\n1\n143\n1\n")
        append("70\n0\n72\n1\n73\n0\n74\n0\n7\n\n75\n0\n")
        append("147\n1\n148\n0\n149\n0\n")
        append("100\nAcDbLayout\n")
        append("1\n").append(name).append('\n')
        append("70\n").append(tabOrder).append('\n')
        append("71\n0\n")
        append("12\n0\n22\n0\n32\n0\n")
        append("14\n").append(fmt(if (boundsInitialized && maxX > minX) maxX else 420.0))
        append("\n24\n").append(fmt(if (boundsInitialized && maxY > minY) maxY else 297.0)).append("\n34\n0\n")
        append("10\n").append(fmt(if (boundsInitialized) minX else 0.0))
        append("\n20\n").append(fmt(if (boundsInitialized) minY else 0.0)).append("\n30\n0\n")
        append("15\n").append(fmt(if (boundsInitialized && maxX > minX) maxX else 420.0))
        append("\n25\n").append(fmt(if (boundsInitialized && maxY > minY) maxY else 297.0)).append("\n35\n0\n")
        append("146\n0\n")
        append("13\n0\n23\n0\n33\n0\n")
        append("16\n1\n26\n0\n36\n0\n17\n0\n27\n1\n37\n0\n")
        append("76\n0\n")
        append("330\n").append(blockRecord).append('\n')
    }

    // ───────────────────────────────────────────────────────────────────────────
    // LEGACY SUPPORT (Site Layout export)
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Legacy export for site layout (backward compatibility).
     * Writes a complete DXF sheet: plot boundary, axes, columns and footings.
     */
    fun exportSiteLayout(
        columns: List<ColumnLoad>,
        plotWidth: Double,
        plotLength: Double,
        soilCapacity: Double = 200.0,
        outputPath: String
    ): File {
        val widthMm = plotWidth * 1000.0
        val lengthMm = plotLength * 1000.0
        val drawing = DxfDrawable { ex ->
            ex.addSheetFrame(
                contentWidth = widthMm,
                contentHeight = lengthMm,
                title = "SITE GENERAL LAYOUT",
                subtitle = "Column positions and footing outlines",
                scaleText = "1:100"
            )
            ex.addPolyline(
                listOf(0.0 to 0.0, widthMm to 0.0, widthMm to lengthMm, 0.0 to lengthMm),
                "0",
                closed = true
            )
            columns.map { it.x }.distinct().sorted().forEach { x ->
                ex.addLine2D(x, -1000.0, x, lengthMm + 1000.0, "AXES")
            }
            columns.map { it.y }.distinct().sorted().forEach { y ->
                ex.addLine2D(-1000.0, y, widthMm + 1000.0, y, "AXES")
            }
            columns.forEach { col ->
                ex.addPolyline(
                    listOf(
                        (col.x - col.width / 2.0) to (col.y - col.depth / 2.0),
                        (col.x + col.width / 2.0) to (col.y - col.depth / 2.0),
                        (col.x + col.width / 2.0) to (col.y + col.depth / 2.0),
                        (col.x - col.width / 2.0) to (col.y + col.depth / 2.0)
                    ),
                    "COLUMNS",
                    closed = true
                )
                val footingSide = Math.sqrt((col.axialLoad * 1.1) / soilCapacity) * 1000.0
                ex.addPolyline(
                    listOf(
                        (col.x - footingSide / 2.0) to (col.y - footingSide / 2.0),
                        (col.x + footingSide / 2.0) to (col.y - footingSide / 2.0),
                        (col.x + footingSide / 2.0) to (col.y + footingSide / 2.0),
                        (col.x - footingSide / 2.0) to (col.y + footingSide / 2.0)
                    ),
                    "FOOTINGS",
                    closed = true
                )
                ex.addText(col.x + col.width / 2.0, col.y + col.depth / 2.0, col.id, 150.0, "TEXT")
            }
        }
        val file = File(outputPath)
        exportDxf(drawing, file.absolutePath)
        return file
    }
}
