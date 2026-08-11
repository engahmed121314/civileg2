package com.civileg.app.utils

import java.io.File
import java.io.FileOutputStream
import com.civileg.app.domain.entities.*
import kotlin.math.*
import java.util.Locale

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
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nAXES\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nCOLUMNS\n70\n0\n62\n4\n")
        sb.append("0\nLAYER\n2\nFOOTINGS\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nENTITIES\n")

        drawRect(sb, 0.0, 0.0, plotWidth * 1000.0, plotLength * 1000.0, "0", 7)
        drawText(sb, plotWidth * 500.0, plotLength * 1000.0 + 500.0, "PROJECT PLOT BOUNDARY", "0", 300.0, 7)
        drawHorizontalDimension(sb, 0.0, plotWidth * 1000.0, -1000.0, "${plotWidth.toInt()} m")
        drawVerticalDimension(sb, 0.0, plotLength * 1000.0, -1000.0, "${plotLength.toInt()} m")

        columns.forEach { col ->
            drawRect(sb, col.x - col.width/2.0, col.y - col.depth/2.0, col.width, col.depth, "COLUMNS")
            drawText(sb, col.x + col.width/2.0 + 50.0, col.y + col.depth/2.0 + 50.0, "${col.id} (P=${col.axialLoad}kN)", "0", 140.0)
        }
        
        val rec = LayoutOptimizer.analyzeLayout(plotWidth, plotLength, columns, soilCapacity, CalculatorEngine.DesignCode.EGYPTIAN)
        rec.footingBounds.forEach { fb ->
            val color = if(fb.type == "PileCap") 4 else (if(fb.type == "Boundary") 1 else 3)
            drawRect(sb, fb.centerX - fb.width/2.0, fb.centerY - fb.length/2.0, fb.width, fb.length, "FOOTINGS", color)
            drawText(sb, fb.centerX, fb.centerY, "${fb.type} [${fb.width.toInt()}x${fb.length.toInt()}]", "FOOTINGS", 130.0, color)
        }

        rec.axesX.forEach { axis -> drawCircle(sb, axis.coordinate, -1800.0, 300.0, "AXES", 1); drawText(sb, axis.coordinate, -1800.0, axis.label, "AXES", 250.0, 1) }
        rec.axesY.forEach { axis -> drawCircle(sb, -2500.0, axis.coordinate, 300.0, "AXES", 1); drawText(sb, -2500.0, axis.coordinate, axis.label, "AXES", 250.0, 1) }
        drawBOQTable(sb, (plotWidth * 1000.0 + 5000.0).toFloat(), (plotLength * 1000.0).toFloat(), rec)

        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    fun exportFootingDetailed(result: CalculatorEngine.FootingResult, colWidth: Double, colDepth: Double, outputPath: String): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nREBAR\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nDIMENSIONS\n70\n0\n62\n5\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nENTITIES\n")

        val fl = result.length; val fw = result.width; val thk = result.thickness; val cover = 70.0
        drawRect(sb, 0.0, 0.0, fl, fw, "CONCRETE")
        val cx = (fl - colDepth) / 2.0; val cy = (fw - colWidth) / 2.0
        drawRect(sb, cx, cy, colDepth, colWidth, "CONCRETE", 4)
        
        for (i in 0 until result.barsX) { val y = cover + i * ((fw - 2 * cover) / (result.barsX - 1).coerceAtLeast(1)); drawLine(sb, cover, y, fl - cover, y, "REBAR") }
        for (i in 0 until result.barsY) { val x = cover + i * ((fl - 2 * cover) / (result.barsY - 1).coerceAtLeast(1)); drawLine(sb, x, cover, x, fw - cover, "REBAR") }
        
        drawText(sb, fl/2, -600.0, "FOOTING PLAN", "TEXT", 200.0)
        drawHorizontalDimension(sb, 0.0, fl, -300.0, "${fl.toInt()} mm")
        drawVerticalDimension(sb, 0.0, fw, -300.0, "${fw.toInt()} mm")

        val secY = fw + 3000.0
        drawRect(sb, 0.0, secY, fl, thk, "CONCRETE"); drawRect(sb, cx, secY + thk, colDepth, 1200.0, "CONCRETE", 4)
        drawLine(sb, cover, secY + cover, fl - cover, secY + cover, "REBAR")
        
        drawResultTable(sb, fl + 3000.0, secY + 1000.0, "REINFORCEMENT DATA", listOf(
            "Concrete" to "%.2f m3".format(result.concreteVolume), "Steel" to "%.1f kg".format(result.steelWeight),
            "Bottom X" to "${result.barsX}%%c${result.barDiameter}", "Bottom Y" to "${result.barsY}%%c${result.barDiameter}"
        ))

        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    fun exportBeamDetailed(result: CalculatorEngine.BeamResult, width: Double, height: Double, span: Double, outputPath: String): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nREBAR\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nENTITIES\n")

        val cover = 50.0; val sMm = span * 1000.0
        drawRect(sb, 0.0, 0.0, sMm, height, "CONCRETE")
        drawLine(sb, cover, cover, sMm - cover, cover, "REBAR")
        drawLine(sb, cover, height - cover, sMm - cover, height - cover, "REBAR")
        result.stirrups.zones.forEach { zone ->
            var curX = zone.startLocation; while (curX < zone.endLocation - 1.0) { drawLine(sb, curX, cover, curX, height - cover, "REBAR"); curX += zone.spacing }
        }
        drawResultTable(sb, sMm + 3000.0, 3000.0, "BEAM DATA", listOf("Moment" to "%.1f kNm".format(result.appliedMoment), "Bottom" to result.reinforcementBottom.barString))
        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    fun exportColumnDetailed(result: CalculatorEngine.ColumnResult, width: Double, depth: Double, height: Double, outputPath: String): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nREBAR\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nENTITIES\n")

        val cover = 40.0; val hMm = height
        drawRect(sb, 0.0, 0.0, width, hMm, "CONCRETE")
        result.stirrups.zones.forEach { zone ->
            var curY = zone.startLocation; while (curY < zone.endLocation - 1.0) { drawLine(sb, cover, curY, width - cover, curY, "REBAR"); curY += zone.spacing }
        }
        val secX = width + 2000.0
        drawRect(sb, secX, 0.0, width, depth, "CONCRETE")
        drawRect(sb, secX + cover, cover, width - 2*cover, depth - 2*cover, "REBAR")
        drawResultTable(sb, secX + 5000.0, 3000.0, "COLUMN DATA", listOf("Pu" to "${result.appliedAxial.toInt()} kN", "Rebar" to result.reinforcement.barString))

        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    fun exportSlabDetailed(result: CalculatorEngine.SlabResult, lx: Double, ly: Double, outputPath: String): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nREBAR\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nENTITIES\n")

        val lxMm = lx * 1000.0; val lyMm = ly * 1000.0
        drawRect(sb, 0.0, 0.0, lxMm, lyMm, "CONCRETE")
        var cy = result.reinforcementMain.spacing / 2.0; while (cy < lyMm) { drawLine(sb, 100.0, cy, lxMm - 100.0, cy, "REBAR"); cy += result.reinforcementMain.spacing * 5 }
        drawResultTable(sb, lxMm + 3000.0, lyMm / 2.0, "SLAB DATA", listOf("Thick" to "${result.thickness.toInt()} mm", "Steel" to result.reinforcementMain.barString))
        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    fun exportRetainingWallDetailed(result: CalculatorEngine.RetainingWallResult, outputPath: String): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nREBAR\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nENTITIES\n")

        val h = result.height * 1000.0; val tw = result.stemThickness; val bw = result.baseWidth; val cover = 50.0
        drawRect(sb, 0.0, 0.0, bw, tw, "CONCRETE"); drawLine(sb, bw/3, tw, bw/3, tw+h, "CONCRETE"); drawLine(sb, bw/3+tw, tw, bw/3+tw, tw+h, "CONCRETE")
        drawLine(sb, bw/3+tw-cover, tw, bw/3+tw-cover, tw+h-cover, "REBAR")
        drawResultTable(sb, bw + 3000.0, h/2, "WALL DATA", listOf("Height" to "${result.height} m", "Ka" to "%.3f".format(result.ka)))
        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    fun exportTankDetailed(result: CalculatorEngine.TankResult, outputPath: String): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nWATER\n70\n0\n62\n5\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nENTITIES\n")

        val l = result.length * 1000.0; val h = result.height * 1000.0; val tw = result.wallThickness; val tb = result.baseThickness
        drawRect(sb, 0.0, 0.0, l+2*tw, tb, "CONCRETE"); drawRect(sb, 0.0, tb, tw, h, "CONCRETE"); drawRect(sb, l+tw, tb, tw, h, "CONCRETE")
        drawLine(sb, tw, tb+h*0.9, tw+l, tb+h*0.9, "WATER")
        drawResultTable(sb, l+4000.0, h/2, "TANK DATA", listOf("Cap" to "%.1f m3".format(result.capacityM3)))
        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    fun exportStairDetailed(result: CalculatorEngine.StairResult, outputPath: String): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nREBAR\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nENTITIES\n")

        val s = result.span * 1000.0; val r = result.riser; val t = result.tread; val numSteps = (s/t).toInt().coerceIn(1,25)
        var curX = 0.0; var curY = 0.0; for (idx in 0 until numSteps) { drawLine(sb, curX, curY, curX, curY+r, "CONCRETE"); drawLine(sb, curX, curY+r, curX+t, curY+r, "CONCRETE"); curX += t; curY += r }
        drawResultTable(sb, s+3000.0, curY/2, "STAIR DATA", listOf("Span" to "${result.span} m"))
        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    fun exportSteelWarehouseDetailed(inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult, outputPath: String): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nFRAME\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nENTITIES\n")

        val span = inputs.span * 1000.0; val eh = inputs.eaveHeight * 1000.0; val midX = span / 2.0
        drawRect(sb, 0.0, 0.0, 300.0, eh, "FRAME"); drawRect(sb, span-300.0, 0.0, 300.0, eh, "FRAME")
        drawResultTable(sb, span+5000.0, eh, "STEEL DATA", listOf("Span" to "${inputs.span} m"))
        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    fun exportFrameAnalysisDetailed(nodes: List<FrameNode>, members: List<FrameMember>, result: FrameAnalysisResult, outputPath: String): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nGEOM\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nBMD\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nENTITIES\n")

        val scale = 500.0; members.forEach { m -> val n1 = nodes.find { it.id == m.nodeI }; val n2 = nodes.find { it.id == m.nodeJ }; if (n1 != null && n2 != null) drawLine(sb, n1.x * scale, n1.y * scale, n2.x * scale, n2.y * scale, "GEOM") }
        drawResultTable(sb, 5000.0, 0.0, "FRAME SUMMARY", listOf("Nodes" to "${nodes.size}", "Members" to "${members.size}"))
        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    private fun drawHorizontalDimension(sb: StringBuilder, x1: Double, x2: Double, y: Double, text: String, color: Int = 5) {
        drawLine(sb, x1, y, x2, y, "DIMENSIONS", color); drawLine(sb, x1, y+100, x1, y-100, "DIMENSIONS", color); drawLine(sb, x2, y+100, x2, y-100, "DIMENSIONS", color); drawText(sb, (x1+x2)/2, y+100, text, "DIMENSIONS", 100.0, color)
    }

    private fun drawVerticalDimension(sb: StringBuilder, y1: Double, y2: Double, x: Double, text: String, color: Int = 5) {
        drawLine(sb, x, y1, x, y2, "DIMENSIONS", color); drawLine(sb, x+100, y1, x-100, y1, "DIMENSIONS", color); drawLine(sb, x+100, y2, x-100, y2, "DIMENSIONS", color); drawText(sb, x-150, (y1+y2)/2, text, "DIMENSIONS", 100.0, color)
    }

    private fun drawResultTable(sb: StringBuilder, x: Double, y: Double, title: String, data: List<Pair<String, String>>) {
        val rowH = 300.0; val colW = 3500.0; drawText(sb, x, y + rowH, title, "TEXT", 150.0, 2); drawRect(sb, x, y - data.size * rowH, colW * 2, (data.size + 1) * rowH, "TEXT")
        drawLine(sb, x + colW, y + rowH, x + colW, y - data.size * rowH, "TEXT")
        data.forEachIndexed { i, (k, v) -> val curY = y - i * rowH; drawText(sb, x + 100.0, curY, k, "TEXT", 100.0); drawText(sb, x + colW + 100.0, curY, v, "TEXT", 100.0); drawLine(sb, x, curY - rowH / 2.0, x + colW * 2, curY - rowH / 2.0, "TEXT") }
    }

    private fun drawLine(sb: StringBuilder, x1: Double, y1: Double, x2: Double, y2: Double, layer: String, color: Int = -1) {
        sb.append("0\nLINE\n8\n$layer\n"); if (color != -1) sb.append("62\n$color\n"); sb.append("10\n$x1\n20\n$y1\n30\n0.0\n11\n$x2\n21\n$y2\n31\n0.0\n")
    }

    private fun drawRect(sb: StringBuilder, x: Double, y: Double, w: Double, d: Double, layer: String, color: Int = -1) {
        drawLine(sb, x, y, x + w, y, layer, color); drawLine(sb, x + w, y, x + w, y + d, layer, color); drawLine(sb, x + w, y + d, x, y + d, layer, color); drawLine(sb, x, y + d, x, y, layer, color)
    }

    private fun drawText(sb: StringBuilder, x: Double, y: Double, text: String, layer: String, height: Double, color: Int = -1) {
        sb.append("0\nTEXT\n8\n$layer\n"); if (color != -1) sb.append("62\n$color\n"); sb.append("10\n$x\n20\n$y\n30\n0.0\n40\n$height\n1\n$text\n")
    }

    private fun drawCircle(sb: StringBuilder, x: Double, y: Double, radius: Double, layer: String, color: Int = -1) {
        sb.append("0\nCIRCLE\n8\n$layer\n"); if (color != -1) sb.append("62\n$color\n"); sb.append("10\n$x\n20\n$y\n30\n0.0\n40\n$radius\n")
    }

    private fun drawSteelSectionShape(sb: StringBuilder, x: Double, y: Double, section: SteelSectionType, label: String) {
        val h = section.depth; val b = section.width; val tf = section.flangeThickness; val tw = section.webThickness
        drawRect(sb, x - b/2, y, b, tf, "FRAME"); drawRect(sb, x - b/2, y - h + tf, b, tf, "FRAME"); drawRect(sb, x - tw/2, y - h + tf, tw, h - 2*tf, "FRAME")
        drawText(sb, x, y - h - 300.0, label, "TEXT", 150.0, 4)
    }

    private fun drawBOQTable(sb: StringBuilder, x: Float, y: Float, rec: LayoutRecommendation) {
        val startX = x.toDouble(); val startY = y.toDouble(); val rowH = 350.0; val colW = 3500.0
        drawText(sb, startX, startY + rowH, "BILL OF QUANTITIES", "0", 250.0, 2); drawRect(sb, startX, startY - 3000, colW * 2, 4000.0, "0", 7); drawLine(sb, startX + colW, startY + rowH, startX + colW, startY - 3000, "0")
        var currentY = startY - rowH; fun addRow(l: String, v: String) { drawText(sb, startX + 100, currentY, l, "0", 180.0); drawText(sb, startX + colW + 100, currentY, v, "0", 180.0); drawLine(sb, startX, currentY - 100, startX + colW * 2, currentY - 100, "0"); currentY -= rowH }
        addRow("Foundation", rec.suggestedType); addRow("Concrete", "%.1f m3".format(rec.totalConcreteEst)); addRow("Steel", "%.2f Tons".format(rec.totalSteelEst / 1000.0))
    }
}
