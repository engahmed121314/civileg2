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

        // 3. Draw Columns and Footings
        columns.forEach { col ->
            // Column
            drawRect(sb, col.x - col.width/2.0, col.y - col.depth/2.0, col.width, col.depth, "COLUMNS")
            
            // Footing (Estimated size for DXF)
            val footingSide = Math.sqrt((col.axialLoad * 1.1) / 200.0) * 1000.0
            drawRect(sb, col.x - footingSide/2.0, col.y - footingSide/2.0, footingSide, footingSide, "FOOTINGS")
            
            // Label
            drawText(sb, col.x + col.width/2.0, col.y + col.depth/2.0, col.id, "0", 150.0)
        }

        sb.append("0\nENDSEC\n0\nEOF\n")

        val file = File(outputPath)
        FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
        return file
    }

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
