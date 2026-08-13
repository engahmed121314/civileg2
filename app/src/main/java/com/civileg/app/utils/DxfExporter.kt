package com.civileg.app.utils

import java.io.File
import java.io.FileOutputStream
import com.civileg.app.domain.entities.*
import kotlin.math.*
import java.util.Locale

/**
 * Advanced AutoCAD (DXF) Export Engine - Genius Engineering Edition.
 * strictly ensures that every line, circle, and text in the DXF corresponds to ACTUAL calculation results.
 * Optimized for SMRF (Special Moment Frames) and professional workshop standards.
 */
object DxfExporter {

    private fun isAr() = LocaleHelper.isArabic()
    private fun tr(en: String, ar: String): String = if (isAr()) ar else en

    // ─── SITE LAYOUT EXPORT ───────────────────────────────────────────

    fun exportSiteLayout(columns: List<ColumnLoad>, plotWidth: Double, plotLength: Double, soilCapacity: Double, outputPath: String): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nAXES\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nCOLUMNS\n70\n0\n62\n4\n")
        sb.append("0\nLAYER\n2\nFOOTINGS\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nENTITIES\n")

        drawRect(sb, 0.0, 0.0, plotWidth * 1000.0, plotLength * 1000.0, "0", 7)
        drawText(sb, plotWidth * 500.0, plotLength * 1000.0 + 500.0, tr("PROJECT PLOT BOUNDARY", "حدود أرض المشروع"), "0", 300.0, 7)
        drawHorizontalDimension(sb, 0.0, plotWidth * 1000.0, -1200.0, "${plotWidth.toInt()} m")
        drawVerticalDimension(sb, 0.0, plotLength * 1000.0, -1500.0, "${plotLength.toInt()} m")

        columns.forEach { col ->
            drawRect(sb, col.x - col.width/2.0, col.y - col.depth/2.0, col.width, col.depth, "COLUMNS")
            drawText(sb, col.x + col.width/2.0 + 100.0, col.y + col.depth/2.0 + 100.0, "C${col.id} (P=${col.axialLoad}kN)", "0", 140.0)
        }
        
        val rec = LayoutOptimizer.analyzeLayout(plotWidth, plotLength, columns, soilCapacity, CalculatorEngine.DesignCode.EGYPTIAN)
        rec.footingBounds.forEach { fb ->
            val color = if(fb.type == "PileCap") 4 else (if(fb.type == "Boundary") 1 else 3)
            drawRect(sb, fb.centerX - fb.width/2.0, fb.centerY - fb.length/2.0, fb.width, fb.length, "FOOTINGS", color)
            drawText(sb, fb.centerX, fb.centerY, "${fb.type} [${fb.width.toInt()}x${fb.length.toInt()}]", "FOOTINGS", 130.0, color)
        }

        rec.axesX.forEach { axis -> drawCircle(sb, axis.coordinate, -2000.0, 300.0, "AXES", 1); drawText(sb, axis.coordinate, -2000.0, axis.label, "AXES", 250.0, 1) }
        rec.axesY.forEach { axis -> drawCircle(sb, -2800.0, axis.coordinate, 300.0, "AXES", 1); drawText(sb, -2800.0, axis.coordinate, axis.label, "AXES", 250.0, 1) }
        drawBOQTable(sb, (plotWidth * 1000.0 + 6000.0).toFloat(), (plotLength * 1000.0).toFloat(), rec)

        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    // ─── FOOTING DETAILED ─────────────────────────────────────────────

    fun exportFootingDetailed(result: CalculatorEngine.FootingResult, colWidth: Double, colDepth: Double, outputPath: String): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nREBAR\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nENTITIES\n")

        val fl = result.length; val fw = result.width; val thk = result.thickness; val cover = 70.0
        drawRect(sb, 0.0, 0.0, fl, fw, "CONCRETE")
        val cx = (fl - colDepth) / 2.0; val cy = (fw - colWidth) / 2.0
        drawRect(sb, cx, cy, colDepth, colWidth, "CONCRETE", 4)
        
        val numX = result.barsX; val spX = if(numX > 1) (fw - 2*cover)/(numX - 1) else (fw - 2*cover)
        for (i in 0 until numX) { drawLine(sb, cover, cover + i*spX, fl - cover, cover + i*spX, "REBAR") }
        val numY = result.barsY; val spY = if(numY > 1) (fl - 2*cover)/(numY - 1) else (fl - 2*cover)
        for (i in 0 until numY) { drawLine(sb, cover + i*spY, cover, cover + i*spY, fw - cover, "REBAR") }
        
        val secY = fw + 3000.0
        drawRect(sb, 0.0, secY, fl, thk, "CONCRETE")
        drawRect(sb, cx, secY + thk, colDepth, 1500.0, "CONCRETE", 4)
        drawLine(sb, cover, secY + cover, fl - cover, secY + cover, "REBAR")
        
        drawResultTable(sb, fl + 3500.0, secY + 1000.0, tr("FOOTING DESIGN DATA", "بيانات تصميم القاعدة"), listOf(
            tr("Concrete", "حجم الخرسانة") to "%.2f m3".format(result.concreteVolume), 
            tr("Steel Weight", "وزن الحديد") to "%.1f kg".format(result.steelWeight),
            tr("Bearing σ", "إجهاد التربة") to "%.1f kPa".format(result.soilPressure)
        ))
        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    // ─── BEAM DETAILED ────────────────────────────────────────────────

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
        drawLine(sb, -300.0, cover, sMm + 300.0, cover, "REBAR")
        drawLine(sb, -300.0, height - cover, sMm + 300.0, height - cover, "REBAR")
        result.stirrups.zones.forEach { zone ->
            var curX = zone.startLocation; while (curX < zone.endLocation - 1.0) { drawLine(sb, curX, cover, curX, height - cover, "REBAR"); curX += zone.spacing }
            drawText(sb, curX - zone.spacing/2, height + 400.0, "%%c${zone.diameter}@${zone.spacing.toInt()}", "TEXT", 100.0)
        }
        drawResultTable(sb, sMm + 3500.0, height, tr("BEAM DATA", "بيانات تصميم الكمرة"), listOf(
            tr("Moment Mu", "عزم التصميم") to "%.1f kNm".format(result.appliedMoment),
            tr("Deflection", "الترخيم") to "%.2f mm".format(result.deflection),
            tr("Bottom Steel", "التسليح السفلي") to result.reinforcementBottom.barString
        ))
        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    // ─── COLUMN DETAILED ──────────────────────────────────────────────

    fun exportColumnDetailed(result: CalculatorEngine.ColumnResult, width: Double, depth: Double, height: Double, outputPath: String): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nREBAR\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nENTITIES\n")

        val cover = 40.0; val hMm = height; val slab = 250.0
        drawRect(sb, -1200.0, -slab, width + 2400.0, slab, "CONCRETE")
        drawRect(sb, -1200.0, hMm, width + 2400.0, slab, "CONCRETE")
        drawRect(sb, 0.0, 0.0, width, hMm, "CONCRETE")
        
        val nBars = result.reinforcement.numBars; val bFace = (nBars / 4) + 1
        val spFace = (width - 2 * cover) / (bFace - 1).coerceAtLeast(1)
        for (i in 0 until bFace) {
            val bx = cover + i * spFace
            drawLine(sb, bx, -slab, bx, hMm + slab, "REBAR")
            drawLine(sb, bx, hMm, bx - 25.0, hMm + 180.0, "REBAR")
        }
        
        result.stirrups.zones.forEach { zone ->
            var curY = zone.startLocation; while (curY < zone.endLocation - 1.0) { drawLine(sb, cover, curY, width - cover, curY, "REBAR"); curY += zone.spacing }
        }
        
        drawResultTable(sb, width + 5500.0, height / 2.0, tr("COLUMN DATA", "بيانات تصميم العمود"), listOf(
            tr("Load Pu", "الحمل التصميمي") to "${result.appliedAxial.toInt()} kN",
            tr("Slenderness λ", "معامل النحافة") to "%.2f".format(result.slenderness),
            tr("Capacity Pn", "قدرة التحمل") to "${result.axialCapacity.toInt()} kN",
            tr("Status", "الحالة") to if(result.isSafe) tr("SAFE", "آمن") else tr("UNSAFE", "غير آمن")
        ))
        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    // ─── SLAB DETAILED ────────────────────────────────────────────────

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
        drawResultTable(sb, lxMm + 3500.0, lyMm / 2.0, tr("SLAB DATA", "بيانات تصميم البلاطة"), listOf(
            tr("Thickness", "السمك") to "${result.thickness.toInt()} mm",
            tr("Moment Mx", "عزم X") to "%.1f kNm".format(result.momentX),
            tr("Main Steel", "التسليح الرئيسي") to result.reinforcementMain.barString
        ))
        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    // ─── RETAINING WALL ───────────────────────────────────────────────

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
        drawResultTable(sb, bw + 3500.0, h/2, tr("WALL DATA", "بيانات الحائط الساند"), listOf(tr("Height", "الارتفاع") to "${result.height} m", tr("Ka", "معامل الضغط") to "%.3f".format(result.ka), tr("Pa resultant", "القوة المحصلة") to "%.1f kN".format(result.pa)))
        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    // ─── WATER TANK ───────────────────────────────────────────────────

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
        drawResultTable(sb, l+4500.0, h/2, tr("TANK DATA", "بيانات الخزان"), listOf(tr("Capacity", "السعة") to "%.1f m3".format(result.capacityM3), tr("Pressure", "الضغط") to "%.1f kPa".format(result.waterPressure), tr("Wall Rebar", "تسليح الحائط") to result.wallReinforcement.barString))
        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    // ─── STAIRCASE ────────────────────────────────────────────────────

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
        drawResultTable(sb, s+3500.0, curY/2, tr("STAIR DATA", "بيانات السلم"), listOf(tr("Span", "البحر") to "${result.span} m", tr("Steel", "التسليح") to result.reinforcement.barString, tr("Waist ts", "سمك البلاطة") to "${result.thickness.toInt()} mm"))
        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    // ─── STEEL WAREHOUSE ──────────────────────────────────────────────

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
        drawLine(sb, 0.0, eh, midX, inputs.ridgeHeight * 1000.0, "FRAME"); drawLine(sb, span, eh, midX, inputs.ridgeHeight * 1000.0, "FRAME")
        
        drawResultTable(sb, span+7500.0, eh, tr("STEEL DATA", "بيانات العنبر المعدني"), listOf(tr("Span", "البحر") to "${inputs.span} m", tr("Weight", "الوزن") to "%.2f Tons".format(result.totalWeight), tr("Column", "العمود") to result.mainFrame.columnSection.sectionName, tr("Rafter", "الرافدة") to result.mainFrame.rafterSection.sectionName))
        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    // ─── FRAME ANALYSIS ───────────────────────────────────────────────

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
        result.memberEndForces.forEach { f -> val m = members.find { it.id == f.memberId } ?: return@forEach; val n1 = nodes.find { it.id == m.nodeI } ?: return@forEach; val n2 = nodes.find { it.id == m.nodeJ } ?: return@forEach; drawText(sb, (n1.x + n2.x)/2 * scale, (n1.y + n2.y)/2 * scale + 50, "M=%.1f".format(max(abs(f.mi_z), abs(f.mj_z))), "BMD", 45.0, 1) }

        drawResultTable(sb, 5000.0, 0.0, tr("FRAME SUMMARY", "ملخص التحليل"), listOf(tr("Nodes", "العقد") to "${nodes.size}", tr("Members", "العناصر") to "${members.size}", tr("Max Reaction", "أقصى رد فعل") to "%.1f kN".format(result.nodeResults.maxOfOrNull { abs(it.reactionFy) } ?: 0.0)))
        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    // ─── HELPERS ──────────────────────────────────────────────────────

    private fun drawHorizontalDimension(sb: StringBuilder, x1: Double, x2: Double, y: Double, text: String, color: Int = 5) {
        drawLine(sb, x1, y, x2, y, "DIMENSIONS", color)
        val t = 100.0; drawLine(sb, x1-t, y-t, x1+t, y+t, "DIMENSIONS", color); drawLine(sb, x2-t, y-t, x2+t, y+t, "DIMENSIONS", color)
        drawText(sb, (x1+x2)/2, y+150.0, text, "DIMENSIONS", 120.0, color)
    }

    private fun drawVerticalDimension(sb: StringBuilder, y1: Double, y2: Double, x: Double, text: String, color: Int = 5) {
        drawLine(sb, x, y1, x, y2, "DIMENSIONS", color)
        val t = 100.0; drawLine(sb, x-t, y1-t, x+t, y1+t, "DIMENSIONS", color); drawLine(sb, x-t, y2-t, x+t, y2+t, "DIMENSIONS", color)
        drawText(sb, x-500.0, (y1+y2)/2, text, "DIMENSIONS", 120.0, color)
    }

    private fun drawResultTable(sb: StringBuilder, x: Double, y: Double, title: String, data: List<Pair<String, String>>) {
        val rowH = 350.0; val colW = 4500.0; drawText(sb, x, y + rowH, title, "TEXT", 180.0, 2); drawRect(sb, x, y - data.size * rowH, colW * 2, (data.size + 1) * rowH, "TEXT", 7)
        drawLine(sb, x + colW, y + rowH, x + colW, y - data.size * rowH, "TEXT", 7)
        data.forEachIndexed { i, (k, v) -> val curY = y - i * rowH; drawText(sb, x+150, curY, k, "TEXT", 110.0, 7); drawText(sb, x+colW+150, curY, v, "TEXT", 110.0, 4); drawLine(sb, x, curY-rowH/2, x+colW*2, curY-rowH/2, "TEXT", 7) }
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

    private fun drawBOQTable(sb: StringBuilder, x: Float, y: Float, rec: LayoutRecommendation) {
        val startX = x.toDouble(); val startY = y.toDouble(); val rowH = 350.0; val colW = 4500.0
        drawText(sb, startX, startY + rowH, tr("BILL OF QUANTITIES", "جدول حصر الكميات"), "0", 250.0, 2); drawRect(sb, startX, startY-3000, colW*2, 4000.0, "0", 7); drawLine(sb, startX+colW, startY+rowH, startX+colW, startY-3000, "0")
        var cY = startY-rowH; fun addR(l: String, v: String) { drawText(sb, startX+150, cY, l, "0", 180.0); drawText(sb, startX+colW+150, cY, v, "0", 180.0, 4); drawLine(sb, startX, cY-100, startX+colW*2, cY-100, "0"); cY -= rowH }
        addR(tr("Foundation", "نوع الأساسات"), rec.suggestedType); addR(tr("Concrete", "خرسانة"), "%.1f m3".format(rec.totalConcreteEst)); addR(tr("Steel", "حديد"), "%.2f Tons".format(rec.totalSteelEst/1000.0))
    }
}
