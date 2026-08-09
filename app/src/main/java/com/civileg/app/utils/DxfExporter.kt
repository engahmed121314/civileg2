package com.civileg.app.utils

import java.io.File
import java.io.FileOutputStream

object DxfExporter {

    /**
     * Exports site layout to a DXF file.
     */
    fun exportSiteLayout(
        columns: List<ColumnLoad>,
        plotWidth: Double,
        plotLength: Double,
        outputPath: String
    ): File {
        val sb = StringBuilder()
        
        // Header
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        
        // Tables (Layers)
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nAXES\n70\n0\n62\n1\n") // Red
        sb.append("0\nLAYER\n2\nCOLUMNS\n70\n0\n62\n4\n") // Cyan
        sb.append("0\nLAYER\n2\nFOOTINGS\n70\n0\n62\n3\n") // Green
        sb.append("0\nENDTAB\n0\nENDSEC\n")

        // Entities
        sb.append("0\nSECTION\n2\nENTITIES\n")

        // 1. Draw Plot Boundary
        drawRect(sb, 0.0, 0.0, plotWidth * 1000.0, plotLength * 1000.0, "0")

        // 2. Draw Axes
        val axesX = columns.map { it.x }.distinct().sorted()
        val axesY = columns.map { it.y }.distinct().sorted()
        
        axesX.forEach { x ->
            drawLine(sb, x, -1000.0, x, plotLength * 1000.0 + 1000.0, "AXES")
        }
        axesY.forEach { y ->
            drawLine(sb, -1000.0, y, plotWidth * 1000.0 + 1000.0, y, "AXES")
        }

        // 3. Draw Columns and Footings (Professional Layout)
        columns.forEach { col ->
            // Column Layer
            drawRect(sb, col.x - col.width/2.0, col.y - col.depth/2.0, col.width, col.depth, "COLUMNS")
            
            // Label with Load
            drawText(sb, col.x + col.width/2.0, col.y + col.depth/2.0, "${col.id} (${col.axialLoad}kN)", "0", 150.0)
        }
        
        val rec = LayoutOptimizer.analyzeLayout(plotWidth, plotLength, columns, 200.0, CalculatorEngine.DesignCode.EGYPTIAN)
        
        // 4. Draw Calculated Footing Boundaries
        rec.footingBounds.forEach { fb ->
            val color = if(fb.type == "PileCap") 4 else (if(fb.type == "Boundary") 1 else 3)
            drawRect(sb, fb.centerX - fb.width/2.0, fb.centerY - fb.length/2.0, fb.width, fb.length, "FOOTINGS", color)
        }

        // 5. Draw Axis Labels
        rec.axesX.forEach { axis ->
            drawText(sb, axis.coordinate, -1500.0, axis.label, "AXES", 250.0)
        }
        rec.axesY.forEach { axis ->
            drawText(sb, -2000.0, axis.coordinate, axis.label, "AXES", 250.0)
        }

        // 6. Draw Professional Bill of Quantities Table on Drawing
        drawBOQTable(sb, (plotWidth * 1000.0 + 2000.0).toFloat(), (plotLength * 1000.0).toFloat(), rec)

        sb.append("0\nENDSEC\n0\nEOF\n")

        val file = File(outputPath)
        FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
        return file
    }

    private fun drawLine(sb: StringBuilder, x1: Double, y1: Double, x2: Double, y2: Double, layer: String, color: Int = -1) {
        sb.append("0\nLINE\n8\n$layer\n")
        if (color != -1) sb.append("62\n$color\n")
        sb.append("10\n$x1\n20\n$y1\n30\n0.0\n")
        sb.append("11\n$x2\n21\n$y2\n31\n0.0\n")
    }

    private fun drawRect(sb: StringBuilder, x: Double, y: Double, w: Double, d: Double, layer: String, color: Int = -1) {
        drawLine(sb, x, y, x + w, y, layer, color)
        drawLine(sb, x + w, y, x + w, y + d, layer, color)
        drawLine(sb, x + w, y + d, x, y + d, layer, color)
        drawLine(sb, x, y + d, x, y, layer, color)
    }

    private fun drawText(sb: StringBuilder, x: Double, y: Double, text: String, layer: String, height: Double, color: Int = -1) {
        sb.append("0\nTEXT\n8\n$layer\n")
        if (color != -1) sb.append("62\n$color\n")
        sb.append("10\n$x\n20\n$y\n30\n0.0\n")
        sb.append("40\n$height\n1\n$text\n")
    }

    private fun drawBOQTable(sb: StringBuilder, x: Float, y: Float, rec: LayoutRecommendation) {
        val startX = x.toDouble()
        val startY = y.toDouble()
        val rowH = 350.0
        val colW = 3500.0
        
        drawText(sb, startX, startY + rowH, "BILL OF QUANTITIES (FOUNDATION)", "0", 250.0, 2)
        
        drawRect(sb, startX, startY - 3000, colW * 2, 4000.0, "0", 7)
        drawLine(sb, startX + colW, startY + rowH, startX + colW, startY - 3000, "0")
        
        var currentY = startY - rowH
        fun addRow(label: String, value: String) {
            drawText(sb, startX + 100, currentY, label, "0", 180.0)
            drawText(sb, startX + colW + 100, currentY, value, "0", 180.0)
            drawLine(sb, startX, currentY - 100, startX + colW * 2, currentY - 100, "0")
            currentY -= rowH
        }
        
        addRow("Foundation Type", rec.suggestedType)
        addRow("Estimated Concrete", "${"%.1f".format(rec.totalConcreteEst)} m3")
        addRow("Estimated Steel", "${"%.2f".format(rec.totalSteelEst / 1000.0)} Tons")
        addRow("Footing Coverage", "${(rec.coverageRatio * 100).toInt()} %")
        addRow("Report Date", java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date()))
    }
}
