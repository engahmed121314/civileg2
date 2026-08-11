package com.civileg.app.utils

import java.io.File
import java.io.FileOutputStream
import com.civileg.app.domain.entities.*
import kotlin.math.*
import java.util.Locale

/**
 * Advanced AutoCAD (DXF) Export Engine - Genius Engineering Edition.
 * Generates highly detailed workshop drawings (SMRF standards) for all structural elements.
 */
object DxfExporter {

    // ─── SITE LAYOUT EXPORT ───────────────────────────────────────────

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

    // ─── FOOTING ELEMENT EXPORT ───────────────────────────────────────

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
        val elevX = 0.0; val elevY = 0.0

        // PLAN
        drawRect(sb, elevX, elevY, fl, fw, "CONCRETE")
        val cx = elevX + (fl - colDepth) / 2.0; val cy = elevY + (fw - colWidth) / 2.0
        drawRect(sb, cx, cy, colDepth, colWidth, "CONCRETE", 4)
        
        for (i in 0 until result.barsX) { 
            val y = elevY + cover + i * ((fw - 2 * cover) / (result.barsX - 1).coerceAtLeast(1))
            drawLine(sb, elevX + cover, y, elevX + fl - cover, y, "REBAR") 
        }
        for (i in 0 until result.barsY) { 
            val x = elevX + cover + i * ((fl - 2 * cover) / (result.barsY - 1).coerceAtLeast(1))
            drawLine(sb, x, elevY + cover, x, elevY + fw - cover, "REBAR") 
        }
        
        drawText(sb, fl/2, -600.0, "FOOTING PLAN VIEW", "TEXT", 200.0)
        drawHorizontalDimension(sb, elevX, elevX + fl, elevY - 300.0, "${fl.toInt()} mm")
        drawVerticalDimension(sb, elevY, elevY + fw, elevX - 300.0, "${fw.toInt()} mm")

        // SECTION
        val secY = elevY + fw + 3000.0
        drawRect(sb, elevX, secY, fl, thk, "CONCRETE")
        drawRect(sb, cx, secY + thk, colDepth, 1200.0, "CONCRETE", 4) // Column starter
        drawLine(sb, elevX + cover, secY + cover, elevX + fl - cover, secY + cover, "REBAR")
        // Starter bars
        drawLine(sb, cx + 50, secY + cover, cx + 50, secY + thk + 1000, "REBAR")
        drawLine(sb, cx + colDepth - 50, secY + cover, cx + colDepth - 50, secY + thk + 1000, "REBAR")
        
        drawText(sb, fl/2, secY - 600.0, "FOOTING SECTION VIEW", "TEXT", 200.0)
        drawVerticalDimension(sb, secY, secY + thk, elevX - 300.0, "t=${thk.toInt()}")

        // TABLE
        drawResultTable(sb, fl + 3000.0, secY + 1000.0, "FOOTING DESIGN DATA", listOf(
            "Concrete" to "%.2f m3".format(result.concreteVolume), 
            "Steel" to "%.1f kg".format(result.steelWeight),
            "Bottom X" to "${result.barsX} %%c ${result.barDiameter}", 
            "Bottom Y" to "${result.barsY} %%c ${result.barDiameter}",
            "Soil Capacity" to "%.1f kN/m2".format(result.allowablePressure)
        ))

        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    // ─── BEAM ELEMENT EXPORT ──────────────────────────────────────────

    fun exportBeamDetailed(result: CalculatorEngine.BeamResult, width: Double, height: Double, span: Double, outputPath: String): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nREBAR_MAIN\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nSTIRRUPS\n70\n0\n62\n2\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nENTITIES\n")

        val cover = 50.0; val sMm = span * 1000.0
        val elevX = 0.0; val elevY = 0.0
        
        // SUPPORTS
        drawRect(sb, -400.0, -800.0, 400.0, 800.0, "CONCRETE")
        drawRect(sb, sMm, -800.0, 400.0, 800.0, "CONCRETE")

        // ELEVATION
        drawRect(sb, elevX, elevY, sMm, height, "CONCRETE")
        
        // REBAR WITH HOOKS
        val mbY = cover
        drawLine(sb, elevX - 300, mbY, elevX + sMm + 300, mbY, "REBAR_MAIN")
        drawLine(sb, elevX - 300, mbY, elevX - 300, mbY + 200, "REBAR_MAIN")
        drawLine(sb, elevX + sMm + 300, mbY, elevX + sMm + 300, mbY + 200, "REBAR_MAIN")
        
        val mtY = height - cover
        drawLine(sb, elevX - 300, mtY, elevX + sMm + 300, mtY, "REBAR_MAIN")
        
        // STIRRUP ZONES
        result.stirrups.zones.forEach { zone ->
            var curX = zone.startLocation
            while (curX < zone.endLocation - 1.0) {
                drawLine(sb, curX, cover, curX, height - cover, "STIRRUPS")
                curX += zone.spacing
            }
            drawText(sb, (zone.startLocation + zone.endLocation) / 2.0, height + 400.0, "%%c${zone.diameter}@${zone.spacing.toInt()}", "TEXT", 100.0)
        }

        drawText(sb, sMm/2, -800.0, "BEAM REINFORCEMENT ELEVATION", "TEXT", 250.0)
        drawHorizontalDimension(sb, 0.0, sMm, -400.0, "Span = ${span}m")
        
        // CROSS SECTION
        val secX = sMm + 2000.0
        drawRect(sb, secX, 0.0, width, height, "CONCRETE")
        drawRect(sb, secX + cover, cover, width - 2*cover, height - 2*cover, "STIRRUPS")
        for (i in 0 until result.reinforcementBottom.numBars) {
            val bx = secX + cover + i * ((width - 2*cover)/(result.reinforcementBottom.numBars-1).coerceAtLeast(1))
            drawCircle(sb, bx, cover, 12.0, "REBAR_MAIN", 1)
        }

        drawResultTable(sb, sMm + 4000.0, 4000.0, "BEAM DESIGN DATA", listOf(
            "Moment Mu" to "%.1f kNm".format(result.appliedMoment),
            "Section" to "${width.toInt()}x${height.toInt()} mm",
            "Bottom" to result.reinforcementBottom.barString,
            "Steel Wt" to "%.1f kg".format(result.steelWeight)
        ))

        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    // ─── COLUMN ELEMENT EXPORT ────────────────────────────────────────

    fun exportColumnDetailed(result: CalculatorEngine.ColumnResult, width: Double, depth: Double, height: Double, outputPath: String): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nREBAR_MAIN\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nSTIRRUPS\n70\n0\n62\n2\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nENTITIES\n")

        val cover = 40.0; val hMm = height; val slab = 250.0
        // Slabs
        drawRect(sb, -1000.0, -slab, width + 2000.0, slab, "CONCRETE")
        drawRect(sb, -1000.0, hMm, width + 2000.0, slab, "CONCRETE")

        // Elevation
        drawRect(sb, 0.0, 0.0, width, hMm, "CONCRETE")
        
        val nBars = result.reinforcement.numBars; val bFace = (nBars / 4) + 1
        val spFace = (width - 2 * cover) / (bFace - 1).coerceAtLeast(1)
        for (i in 0 until bFace) {
            val bx = cover + i * spFace
            drawLine(sb, bx, -slab, bx, hMm + slab, "REBAR_MAIN")
            drawLine(sb, bx, hMm, bx - 20, hMm + 150, "REBAR_MAIN") // Crank
        }
        
        result.stirrups.zones.forEach { zone ->
            var curY = zone.startLocation
            while (curY < zone.endLocation - 1.0) {
                drawLine(sb, cover, curY, width - cover, curY, "STIRRUPS")
                curY += zone.spacing
            }
            drawText(sb, width + 200, (zone.startLocation + zone.endLocation)/2, "lo zone: %%c${zone.diameter}@${zone.spacing.toInt()}", "TEXT", 80.0)
        }
        
        // Section
        val secX = width + 3000.0
        drawRect(sb, secX, 0.0, width, depth, "CONCRETE")
        drawRect(sb, secX + cover, cover, width - 2*cover, depth - 2*cover, "STIRRUPS")
        drawCircle(sb, secX + cover, cover, 12.0, "REBAR_MAIN", 1)
        drawCircle(sb, secX + width - cover, cover, 12.0, "REBAR_MAIN", 1)
        drawCircle(sb, secX + cover, depth - cover, 12.0, "REBAR_MAIN", 1)
        drawCircle(sb, secX + width - cover, depth - cover, 12.0, "REBAR_MAIN", 1)

        drawResultTable(sb, secX + 5000.0, 4000.0, "COLUMN ENGINEERING DATA", listOf(
            "Load Pu" to "${result.appliedAxial.toInt()} kN",
            "Clear Hn" to "${height.toInt()} mm",
            "Rebar" to "${result.reinforcement.numBars} %%c ${result.reinforcement.diameter}",
            "Splice" to "Class A tension"
        ))

        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    // ─── SLAB ELEMENT EXPORT ──────────────────────────────────────────

    fun exportSlabDetailed(result: CalculatorEngine.SlabResult, lx: Double, ly: Double, outputPath: String): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nREBAR_MAIN\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nREBAR_TOP\n70\n0\n62\n6\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nENTITIES\n")

        val lxMm = lx * 1000.0; val lyMm = ly * 1000.0
        drawRect(sb, 0.0, 0.0, lxMm, lyMm, "CONCRETE")
        
        var curY = result.reinforcementMain.spacing / 2.0; while (curY < lyMm) { drawLine(sb, 100.0, curY, lxMm - 100.0, curY, "REBAR_MAIN"); curY += result.reinforcementMain.spacing * 5 }
        val topLen = lxMm * 0.25; drawLine(sb, 0.0, lyMm/2, topLen, lyMm/2, "REBAR_TOP")

        drawText(sb, lxMm/2, -800.0, "SLAB WORKSHOP PLAN", "TEXT", 300.0)
        drawHorizontalDimension(sb, 0.0, lxMm, -400.0, "Lx = ${lxMm.toInt()}")
        drawResultTable(sb, lxMm + 3000.0, lyMm / 2.0, "SLAB DATA", listOf(
            "Thick" to "${result.thickness.toInt()} mm",
            "Main Rebar" to result.reinforcementMain.barString,
            "Concrete" to "%.2f m3".format(result.concreteVolume)
        ))

        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    // ─── RETAINING WALL EXPORT ────────────────────────────────────────

    fun exportRetainingWallDetailed(result: CalculatorEngine.RetainingWallResult, outputPath: String): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nREBAR\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nLOADS\n70\n0\n62\n2\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nENTITIES\n")

        val h = result.height * 1000.0; val tw = result.stemThickness; val bw = result.baseWidth; val cover = 50.0
        drawRect(sb, 0.0, 0.0, bw, tw, "CONCRETE")
        val toeW = bw / 3.0
        drawLine(sb, toeW, tw, toeW, tw + h, "CONCRETE") // Stem
        drawLine(sb, toeW + tw, tw, toeW + tw * 0.7, tw + h, "CONCRETE") // Tapered
        drawLine(sb, toeW, tw + h, toeW + tw * 0.7, tw + h, "CONCRETE")
        
        drawLine(sb, toeW + tw - cover, tw, toeW + tw - cover, tw + h - cover, "REBAR")
        drawLine(sb, cover, cover, bw - cover, cover, "REBAR")
        
        // Pressure diag
        drawLine(sb, -1000.0, 0.0, -1800.0, 0.0, "LOADS"); drawLine(sb, -1000.0, bw, -1200.0, bw, "LOADS")
        drawLine(sb, -1800.0, 0.0, -1200.0, bw, "LOADS")

        drawResultTable(sb, bw + 3000.0, h/2, "WALL DATA", listOf(
            "Height" to "${result.height} m",
            "F.S. Over" to "%.2f".format(result.factorOfSafetyOverturning),
            "F.S. Slid" to "%.2f".format(result.factorOfSafetySliding)
        ))

        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    // ─── WATER TANK EXPORT ────────────────────────────────────────────

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
        
        val wl = h * 0.9; drawLine(sb, tw, tb+wl, tw+l, tb+wl, "WATER")
        drawText(sb, tw + l/2, tb+wl+150, "W.L.", "WATER", 100.0)
        
        drawResultTable(sb, l+4000.0, h/2, "TANK DATA", listOf(
            "Capacity" to "%.1f m3".format(result.capacityM3),
            "Wall Thick" to "${result.wallThickness.toInt()} mm",
            "Max Pr" to "%.1f kN/m2".format(result.waterPressure)
        ))

        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    // ─── STAIRCASE EXPORT ─────────────────────────────────────────────

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
        
        drawResultTable(sb, s+3000.0, curY/2, "STAIR DATA", listOf("Span" to "${result.span} m", "Waist" to "${result.thickness.toInt()} mm"))
        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    // ─── STEEL WAREHOUSE EXPORT ───────────────────────────────────────

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
        
        val detailX = span + 3000.0
        drawSteelSectionShape(sb, detailX, eh, result.mainFrame.columnSection, "Column: ${result.mainFrame.columnSection.sectionName}")
        
        drawResultTable(sb, span+7000.0, eh, "STEEL DATA", listOf("Span" to "${inputs.span} m", "Weight" to "%.2f Tons".format(result.totalWeight)))
        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath); FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }; return file
    }

    // ─── FRAME ANALYSIS EXPORT ────────────────────────────────────────

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

    // ─── SHARED HELPERS ───────────────────────────────────────────────

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
        val rowH = 350.0; val colW = 3500.0; drawText(sb, x, y + rowH, title, "TEXT", 180.0, 2); drawRect(sb, x, y - data.size * rowH, colW * 2, (data.size + 1) * rowH, "TEXT", 7)
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

    private fun drawSteelSectionShape(sb: StringBuilder, x: Double, y: Double, section: SteelSectionType, label: String) {
        val h = section.depth; val b = section.width; val tf = section.flangeThickness; val tw = section.webThickness
        drawRect(sb, x-b/2, y, b, tf, "FRAME"); drawRect(sb, x-b/2, y-h+tf, b, tf, "FRAME"); drawRect(sb, x-tw/2, y-h+tf, tw, h-2*tf, "FRAME")
        drawText(sb, x, y-h-400, label, "TEXT", 140.0, 4); drawVerticalDimension(sb, y-h, y, x+b/2+250, "h=${h.toInt()}"); drawHorizontalDimension(sb, x-b/2, x+b/2, y+600, "b=${b.toInt()}")
    }

    private fun drawBOQTable(sb: StringBuilder, x: Float, y: Float, rec: LayoutRecommendation) {
        val startX = x.toDouble(); val startY = y.toDouble(); val rowH = 350.0; val colW = 3500.0
        drawText(sb, startX, startY + rowH, "BILL OF QUANTITIES", "0", 250.0, 2); drawRect(sb, startX, startY-3000, colW*2, 4000.0, "0", 7); drawLine(sb, startX+colW, startY+rowH, startX+colW, startY-3000, "0")
        var cY = startY-rowH; fun addR(l: String, v: String) { drawText(sb, startX+150, cY, l, "0", 180.0); drawText(sb, startX+colW+150, cY, v, "0", 180.0, 4); drawLine(sb, startX, cY-100, startX+colW*2, cY-100, "0"); cY -= rowH }
        addR("Foundation", rec.suggestedType); addR("Concrete", "%.1f m3".format(rec.totalConcreteEst)); addR("Steel", "%.2f Tons".format(rec.totalSteelEst/1000.0)); addR("Date", java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date()))
    }
}
