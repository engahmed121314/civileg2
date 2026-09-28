package com.civileg.app.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * DxfExporter — AC1027 (AutoCAD 2013/R2007) DXF writer.
 *
 * Supports structural engineering drawings: beams, columns, slabs, footings, etc.
 * Implements DxfExporter interface for ExportManager integration.
 *
 * ADR-004: DXF canonical line = detailing/V7 (DxfWriter AC1027 + DrawingValidator + CalculatorCadExporterV7)
 * ADR-009: English ONLY in DXF exports.
 */
class DxfExporter(private val context: Context) {

    companion object {
        private const val TAG = "DxfExporter"
        private const val DXF_VERSION = "AC1027" // AutoCAD 2013 / R2007
    }

    private val sb = StringBuilder()
    private var currentLayer = "0"

    /**
     * Export a DxfDrawable to DXF file.
     *
     * @param drawing The drawing implementing DxfDrawable
     * @param outputPath Output file path (must end with .dxf)
     * @return File if successful, null otherwise
     */
    fun exportDxf(drawing: DxfDrawable, outputPath: String): File? {
        val sb = StringBuilder()
        currentLayer = "0"

        // Header
        writeHeader(sb)
        writeLayers(sb)
        writeBlocks(sb)

        // Entities
        sb.append("0\nSECTION\n2\nENTITIES\n")
        drawing.exportToDxf(this)
        sb.append("0\nENDSEC\n")

        // End of file
        sb.append("0\nEOF\n")

        // Write to file
        val file = File(outputPath)
        file.parentFile?.mkdirs()
        return try {
            FileOutputStream(file).use { out ->
                out.write(sb.toString().toByteArray())
            }
            File(outputPath)
        } catch (e: Exception) {
            Log.e(TAG, "DXF export failed: ${e.message}", e)
            null
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // HEADER
    // ───────────────────────────────────────────────────────────────────────────

    private fun writeHeader(sb: StringBuilder) {
        sb.append("0\nSECTION\n2\nHEADER\n")
        sb.append("9\n\$ACADVER\n1\nAC1027\n") // AC1027 = AutoCAD 2013
        sb.append("9\n\$INSBASE\n10\n0.0\n20\n0.0\n30\n0.0\n")
        sb.append("9\n\$EXTMIN\n10\n0.0\n20\n0.0\n30\n0.0\n")
        sb.append("9\n\$EXTMAX\n10\n100000.0\n20\n100000.0\n30\n0.0\n")
        sb.append("9\n\$LIMMIN\n10\n0.0\n20\n0.0\n")
        sb.append("9\n\$LIMMAX\n10\n100000.0\n20\n100000.0\n")
        sb.append("9\n\$ORTHOMODE\n70\n0\n")
        sb.append("9\n\$REGENMODE\n70\n1\n")
        sb.append("9\n\$FILLETRAD\n40\n0.0\n")
        sb.append("9\n\$ANGBASE\n50\n0.0\n")
        sb.append("9\n\$ANGDIR\n70\n0\n")
        sb.append("9\n\$PDMODE\n70\n0\n")
        sb.append("9\n\$PDSIZE\n40\n0.0\n")
        sb.append("0\nENDSEC\n")
    }

    private fun writeLayers(sb: StringBuilder) {
        sb.append("0\nSECTION\n2\nTABLES\n")
        // LAYER table
        sb.append("0\nTABLE\n2\nLAYER\n70\n10\n") // 10 layers

        // Standard structural layers (ACI colors)
        val layers = listOf(
            "0" to 7,          // White - default
            "AXES" to 1,       // Red - grid axes
            "COLUMNS" to 4,    // Cyan - columns
            "BEAMS" to 5,      // Blue - beams
            "SLABS" to 3,      // Green - slabs
            "FOOTINGS" to 3,   // Green - footings
            "WALLS" to 5,      // Blue - walls
            "REBAR" to 1,      // Red - reinforcement
            "STIRRUPS" to 6,   // Magenta - stirrups
            "DIMENSIONS" to 2, // Yellow - dimensions
            "TEXT" to 7,       // White - text
            "HATCH" to 8       // Dark gray - hatching
        )

        layers.forEach { (name, color) ->
            sb.append("0\nLAYER\n")
            sb.append("2\n$name\n")
            sb.append("70\n0\n")
            sb.append("62\n$color\n")
            sb.append("6\nCONTINUOUS\n")
        }

        sb.append("0\nENDTAB\n")

        // LTYPE table
        sb.append("0\nTABLE\n2\nLTYPE\n70\n3\n")
        sb.append("0\nLTYPE\n2\nCONTINUOUS\n70\n0\n3\nSolid line\n72\n65\n73\n0\n40\n0.0\n")
        sb.append("0\nLTYPE\n2\nDASHED\n70\n0\n3\nDashed line\n72\n65\n73\n0\n40\n12.7\n49\n-6.35\n49\n6.35\n")
        sb.append("0\nLTYPE\n2\nDOTTED\n70\n0\n3\nDotted line\n72\n65\n73\n0\n40\n0.0\n49\n3.175\n")
        sb.append("0\nENDTAB\n")

        // STYLE table (text styles)
        sb.append("0\nTABLE\n2\nSTYLE\n70\n1\n")
        sb.append("0\nSTYLE\n2\nStandard\n70\n0\n40\n0.0\n41\n1.0\n50\n0.0\n71\n0\n42\n\n3\nStandard\n4\n\n")
        sb.append("0\nENDTAB\n")

        sb.append("0\nENDSEC\n")
    }

    private fun writeBlocks(sb: StringBuilder) {
        sb.append("0\nSECTION\n2\nBLOCKS\n")
        // Could add block definitions for common symbols (north arrow, section marks, etc.)
        sb.append("0\nENDSEC\n")
    }

    // ───────────────────────────────────────────────────────────────────────────
    // PUBLIC API FOR DxfDrawable
    // ───────────────────────────────────────────────────────────────────────────

    fun addLine3D(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double, layer: String = "0") {
        sb.append("0\nLINE\n8\n$layer\n")
        sb.append("10\n$x1\n20\n$y1\n30\n$z1\n")
        sb.append("11\n$x2\n21\n$y2\n31\n$z2\n")
    }

    fun addLine2D(x1: Double, y1: Double, x2: Double, y2: Double, layer: String = "0") {
        addLine3D(x1, y1, 0.0, x2, y2, 0.0, layer)
    }

    fun addCircle(centerX: Double, centerY: Double, radius: Double, layer: String = "0") {
        sb.append("0\nCIRCLE\n8\n$layer\n")
        sb.append("10\n$centerX\n20\n$centerY\n30\n0.0\n")
        sb.append("40\n$radius\n")
    }

    fun addArc(centerX: Double, centerY: Double, radius: Double, startAngle: Double, endAngle: Double, layer: String = "0") {
        sb.append("0\nARC\n8\n$layer\n")
        sb.append("10\n$centerX\n20\n$centerY\n30\n0.0\n")
        sb.append("40\n$radius\n")
        sb.append("50\n$startAngle\n")
        sb.append("51\n$endAngle\n")
    }

    fun addPolyline(points: List<Pair<Double, Double>>, layer: String = "0", closed: Boolean = false) {
        sb.append("0\nLWPOLYLINE\n8\n$layer\n")
        sb.append("90\n${points.size}\n")
        sb.append("70\n${if (closed) 1 else 0}\n")
        points.forEach { (x, y) ->
            sb.append("10\n$x\n20\n$y\n")
        }
    }

    fun addText(x: Double, y: Double, text: String, height: Double = 100.0, layer: String = "TEXT", rotation: Double = 0.0) {
        sb.append("0\nTEXT\n8\n$layer\n")
        sb.append("10\n$x\n20\n$y\n30\n0.0\n")
        sb.append("40\n100.0\n")
        sb.append("1\n$text\n")
        sb.append("50\n$rotation\n")
    }

    fun addMText(x: Double, y: Double, text: String, height: Double = 100.0, layer: String = "TEXT", width: Double = 1000.0) {
        sb.append("0\nMTEXT\n8\n$layer\n")
        sb.append("10\n$x\n20\n$y\n30\n0.0\n")
        sb.append("40\n$height\n")
        sb.append("41\n$width\n")
        sb.append("1\n$text\n")
    }

    fun addHatch(pattern: String, scale: Double, points: List<Pair<Double, Double>>, layer: String = "HATCH") {
        sb.append("0\nHATCH\n8\n$layer\n")
        sb.append("2\n$pattern\n")
        sb.append("41\n$scale\n")
        sb.append("91\n1\n")
        sb.append("92\n${points.size}\n")
        points.forEach { (x, y) ->
            sb.append("10\n$x\n20\n$y\n")
        }
        sb.append("93\n1\n")
    }

    fun setCurrentLayer(layer: String) {
        currentLayer = layer
    }

    // ───────────────────────────────────────────────────────────────────────────
    // EXPORT
    // ───────────────────────────────────────────────────────────────────────────

    fun exportToFile(outputPath: String): File? {
        val sb = StringBuilder(this.sb)
        sb.append("0\nENDSEC\n") // END ENTITIES
        sb.append("0\nEOF\n")

        val file = File(outputPath)
        file.parentFile?.mkdirs()
        return try {
            FileOutputStream(File(outputPath)).use { out ->
                out.write(sb.toString().toByteArray())
            }
            File(outputPath)
        } catch (e: Exception) {
            Log.e(TAG, "DXF export failed: ${e.message}", e)
            null
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // LEGACY SUPPORT (SiteLayout export)
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Legacy export for site layout (backward compatibility).
     */
    fun exportSiteLayout(
        columns: List<ColumnLoad>,
        plotWidth: Double,
        plotLength: Double,
        outputPath: String
    ): File {
        val sb = StringBuilder()
        currentLayer = "0"

        writeHeader(sb)
        writeLayers(sb)

        sb.append("0\nSECTION\n2\nENTITIES\n")

        // Plot boundary
        drawRect(sb, 0.0, 0.0, plotWidth * 1000.0, plotLength * 1000.0, "0")

        // Axes
        val axesX = columns.map { it.x }.distinct().sorted()
        val axesY = columns.map { it.y }.distinct().sorted()

        axesX.forEach { x ->
            drawLine(sb, x, -1000.0, x, plotLength * 1000.0 + 1000.0, "AXES")
        }
        axesY.forEach { y ->
            drawLine(sb, -1000.0, y, plotWidth * 1000.0 + 1000.0, y, "AXES")
        }

        // Columns and Footings
        columns.forEach { col ->
            drawRect(sb, col.x - col.width / 2.0, col.y - col.depth / 2.0, col.width, col.depth, "COLUMNS")
            val footingSide = Math.sqrt((col.axialLoad * 1.1) / 200.0) * 1000.0
            drawRect(sb, col.x - footingSide / 2.0, col.y - footingSide / 2.0, footingSide, footingSide, "FOOTINGS")
            drawText(sb, col.x + col.width / 2.0, col.y + col.depth / 2.0, col.id, "0", 150.0)
        }

        sb.append("0\nENDSEC\n0\nEOF\n")

        val file = File(outputPath)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
        return file
    }

    // ════════════════════════════════════════════════════════════════════════
    // PRIMITIVES
    // ════════════════════════════════════════════════════════════════════════

    private fun drawLine(sb: StringBuilder, x1: Double, y1: Double, x2: Double, y2: Double, layer: String) {
        sb.append("0\nLINE\n8\n$layer\n")
        sb.append("10\n$x1\n20\n$y1\n30\n0.0\n")
        sb.append("11\n$x2\n21\n$y2\n31\n0.0\n")
    }

    private fun drawRect(sb: StringBuilder, x: Double, y: Double, w: Double, d: Double, layer: String) {
        drawLine(sb, x, y, x + w, y, layer)
        drawLine(sb, x + w, y, x + w, y + d, layer)
        drawLine(sb, x + w, y + d, x, y + d, layer)
        drawLine(sb, x, y + d, x, y, layer)
    }

    private fun drawText(sb: StringBuilder, x: Double, y: Double, text: String, layer: String, height: Double) {
        sb.append("0\nTEXT\n8\n$layer\n")
        sb.append("10\n$x\n20\n$y\n30\n0.0\n")
        sb.append("40\n$height\n1\n$text\n")
    }
}