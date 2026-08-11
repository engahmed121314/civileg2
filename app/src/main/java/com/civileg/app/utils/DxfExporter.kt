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
        soilCapacity: Double,
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
        drawRect(sb, 0.0, 0.0, plotWidth * 1000.0, plotLength * 1000.0, "0", 7) // White
        drawText(sb, plotWidth * 500.0, plotLength * 1000.0 + 300.0, "PROJECT PLOT BOUNDARY", "0", 200.0, 7)

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
            drawText(sb, col.x + col.width/2.0 + 50.0, col.y + col.depth/2.0 + 50.0, "${col.id} (P=${col.axialLoad}kN)", "0", 140.0)
        }
        
        val rec = LayoutOptimizer.analyzeLayout(plotWidth, plotLength, columns, soilCapacity, CalculatorEngine.DesignCode.EGYPTIAN)
        
        // 4. Draw Calculated Footing Boundaries with Type Labels
        rec.footingBounds.forEach { fb ->
            val color = if(fb.type == "PileCap") 4 else (if(fb.type == "Boundary") 1 else 3)
            drawRect(sb, fb.centerX - fb.width/2.0, fb.centerY - fb.length/2.0, fb.width, fb.length, "FOOTINGS", color)
            
            // SIGN FOOTING TYPE AND DIMENSIONS
            val labelText = "${fb.type} [${fb.width.toInt()}x${fb.length.toInt()}]"
            drawText(sb, fb.centerX, fb.centerY, labelText, "FOOTINGS", 130.0, color)
        }

        // 5. Draw Axis Labels with Circles (Bubbles)
        rec.axesX.forEach { axis ->
            drawLine(sb, axis.coordinate, -1000.0, axis.coordinate, plotLength * 1000.0 + 1000.0, "AXES", 1)
            drawCircle(sb, axis.coordinate, -1800.0, 300.0, "AXES", 1)
            drawText(sb, axis.coordinate, -1800.0, axis.label, "AXES", 250.0, 1)
        }
        rec.axesY.forEach { axis ->
            drawLine(sb, -1000.0, axis.coordinate, plotWidth * 1000.0 + 1000.0, axis.coordinate, "AXES", 1)
            drawCircle(sb, -2500.0, axis.coordinate, 300.0, "AXES", 1)
            drawText(sb, -2500.0, axis.coordinate, axis.label, "AXES", 250.0, 1)
        }

        // 6. Draw Professional Bill of Quantities Table on Drawing (shifted right to avoid overlap)
        drawBOQTable(sb, (plotWidth * 1000.0 + 5000.0).toFloat(), (plotLength * 1000.0).toFloat(), rec)

        sb.append("0\nENDSEC\n0\nEOF\n")

        val file = File(outputPath)
        FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
        return file
    }

    /**
     * Exports a detailed footing element (workshop drawing) to DXF.
     */
    fun exportFootingDetailed(
        result: CalculatorEngine.FootingResult,
        colWidth: Double,
        colDepth: Double,
        outputPath: String
    ): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        
        // Layers
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n") // White
        sb.append("0\nLAYER\n2\nREBAR\n70\n0\n62\n1\n")    // Red
        sb.append("0\nLAYER\n2\nDIMENSIONS\n70\n0\n62\n5\n") // Blue
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")      // Green
        sb.append("0\nENDTAB\n0\nENDSEC\n")

        sb.append("0\nSECTION\n2\nENTITIES\n")

        // 1. FOOTING PLAN VIEW
        val planX = 0.0
        val planY = 0.0
        val fl = result.length
        val fw = result.width
        
        // Outline
        drawRect(sb, planX, planY, fl, fw, "CONCRETE")
        
        // Column
        val cx = planX + (fl - colDepth) / 2.0
        val cy = planY + (fw - colWidth) / 2.0
        drawRect(sb, cx, cy, colDepth, colWidth, "CONCRETE", 4)
        
        // Bottom Reinforcement X-Dir (Longitudinal)
        val cover = 70.0
        val barSpacing = result.reinforcementBottom.spacing.coerceAtLeast(100.0)
        var curY = planY + cover
        while (curY <= planY + fw - cover + 0.1) {
            drawLine(sb, planX + cover, curY, planX + fl - cover, curY, "REBAR")
            curY += barSpacing
        }
        
        // Bottom Reinforcement Y-Dir (Cross)
        var curX = planX + cover
        while (curX <= planX + fl - cover + 0.1) {
            drawLine(sb, curX, planY + cover, curX, planY + fw - cover, "REBAR")
            curX += barSpacing
        }

        // Labels Plan
        drawText(sb, planX + fl/2, planY - 300.0, "FOOTING PLAN VIEW", "TEXT", 150.0)
        drawText(sb, planX + fl/2, planY + fw + 300.0, "${result.type.displayNameEn} Footing: ${fl.toInt()}x${fw.toInt()}x${result.thickness.toInt()}mm", "TEXT", 100.0)

        // 2. FOOTING SECTION VIEW
        val secX = 0.0
        val secY = fw + 2000.0
        val thk = result.thickness
        
        // Concrete outline section
        drawRect(sb, secX, secY, fl, thk, "CONCRETE")
        
        // Column starter
        drawRect(sb, cx, secY + thk, colDepth, 800.0, "CONCRETE", 4)
        
        // Reinforcement in section
        drawLine(sb, secX + cover, secY + cover, secX + fl - cover, secY + cover, "REBAR") // Bottom bar
        
        // Dimension lines
        drawLine(sb, secX, secY - 400.0, secX + fl, secY - 400.0, "DIMENSIONS") // Length dim
        drawText(sb, secX + fl/2, secY - 600.0, "L = ${fl.toInt()}mm", "DIMENSIONS", 80.0)
        
        drawLine(sb, secX - 400.0, secY, secX - 400.0, secY + thk, "DIMENSIONS") // Height dim
        drawText(sb, secX - 800.0, secY + thk/2, "t=${thk.toInt()}", "DIMENSIONS", 80.0)

        drawText(sb, secX + fl/2, secY - 1000.0, "FOOTING SECTION VIEW", "TEXT", 150.0)

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

    private fun drawCircle(sb: StringBuilder, x: Double, y: Double, radius: Double, layer: String, color: Int = -1) {
        sb.append("0\nCIRCLE\n8\n$layer\n")
        if (color != -1) sb.append("62\n$color\n")
        sb.append("10\n$x\n20\n$y\n30\n0.0\n")
        sb.append("40\n$radius\n")
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
