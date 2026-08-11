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
        drawText(sb, plotWidth * 500.0, plotLength * 1000.0 + 500.0, "PROJECT PLOT BOUNDARY", "0", 300.0, 7)
        drawHorizontalDimension(sb, 0.0, plotWidth * 1000.0, -1000.0, "${plotWidth.toInt()} m")
        drawVerticalDimension(sb, 0.0, plotLength * 1000.0, -1000.0, "${plotLength.toInt()} m")

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

        val fl = result.length
        val fw = result.width
        val thk = result.thickness
        val cover = 70.0

        // 1. FOOTING PLAN VIEW
        val planX = 0.0
        val planY = 0.0
        drawRect(sb, planX, planY, fl, fw, "CONCRETE")
        
        // Column
        val cx = planX + (fl - colDepth) / 2.0
        val cy = planY + (fw - colWidth) / 2.0
        drawRect(sb, cx, cy, colDepth, colWidth, "CONCRETE", 4)
        
        // Reinforcement X (Longitudinal)
        val numX = result.barsX
        val spX = (fw - 2 * cover) / (numX - 1).coerceAtLeast(1)
        for (i in 0 until numX) {
            val y = planY + cover + i * spX
            drawLine(sb, planX + cover, y, planX + fl - cover, y, "REBAR")
        }
        
        // Reinforcement Y (Cross)
        val numY = result.barsY
        val spY = (fl - 2 * cover) / (numY - 1).coerceAtLeast(1)
        for (i in 0 until numY) {
            val x = planX + cover + i * spY
            drawLine(sb, x, planY + cover, x, planY + fw - cover, "REBAR")
        }

        // Labels & Dims Plan
        drawText(sb, planX + fl/2, planY - 600.0, "FOOTING PLAN VIEW", "TEXT", 200.0)
        drawHorizontalDimension(sb, planX, planX + fl, planY - 300.0, "${fl.toInt()} mm")
        drawVerticalDimension(sb, planY, planY + fw, planX - 300.0, "${fw.toInt()} mm")

        // 2. FOOTING SECTION VIEW
        val secX = 0.0
        val secY = fw + 3000.0
        drawRect(sb, secX, secY, fl, thk, "CONCRETE")
        drawRect(sb, cx, secY + thk, colDepth, 1200.0, "CONCRETE", 4) // Column starter
        
        // Rebar in section
        drawLine(sb, secX + cover, secY + cover, secX + fl - cover, secY + cover, "REBAR")
        // Vertical bars
        drawLine(sb, cx + 50, secY + cover, cx + 50, secY + thk + 1000, "REBAR")
        drawLine(sb, cx + colDepth - 50, secY + cover, cx + colDepth - 50, secY + thk + 1000, "REBAR")
        
        // Labels & Dims Section
        drawText(sb, secX + fl/2, secY - 600.0, "FOOTING SECTION VIEW", "TEXT", 200.0)
        drawVerticalDimension(sb, secY, secY + thk, secX - 300.0, "t=${thk.toInt()}")
        drawHorizontalDimension(sb, secX, secX + fl, secY - 300.0, "L=${fl.toInt()}")

        // 3. REINFORCEMENT TABLE
        drawResultTable(sb, fl + 3000.0, secY + 1000.0, "REINFORCEMENT DATA", listOf(
            "Type" to result.type.displayNameEn,
            "Concrete" to "${"%.2f".format(result.concreteVolume)} m3",
            "Steel" to "${"%.1f".format(result.steelWeight)} kg",
            "Bottom X" to "${result.barsX} %%c ${result.barDiameter}",
            "Bottom Y" to "${result.barsY} %%c ${result.barDiameter}",
            "Soil Pressure" to "${"%.2f".format(result.soilPressure)} kN/m2",
            "Code" to result.designCodeName
        ))

        sb.append("0\nENDSEC\n0\nEOF\n")

        val file = File(outputPath)
        FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
        return file
    }

    /**
     * Exports a detailed beam element (workshop drawing) to DXF.
     */
    fun exportBeamDetailed(
        result: CalculatorEngine.BeamResult,
        width: Double,
        height: Double,
        span: Double,
        outputPath: String
    ): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        
        // Layers
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n") // White
        sb.append("0\nLAYER\n2\nREBAR_MAIN\n70\n0\n62\n1\n") // Red
        sb.append("0\nLAYER\n2\nSTIRRUPS\n70\n0\n62\n2\n")   // Yellow
        sb.append("0\nLAYER\n2\nDIMENSIONS\n70\n0\n62\n5\n") // Blue
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")      // Green
        sb.append("0\nENDTAB\n0\nENDSEC\n")

        sb.append("0\nSECTION\n2\nENTITIES\n")

        val scale = 1.0
        val cover = 50.0
        
        // 1. BEAM ELEVATION (LONGITUDINAL)
        val elevX = 0.0
        val elevY = 0.0
        val sMm = span * 1000.0
        
        // Concrete outline with hatching for section cuts
        drawRect(sb, elevX, elevY, sMm, height, "CONCRETE")
        
        // Main bottom reinforcement with hooks (schematic)
        val mbY = elevY + cover
        drawLine(sb, elevX + cover, mbY, elevX + sMm - cover, mbY, "REBAR_MAIN")
        drawLine(sb, elevX + cover, mbY, elevX + cover, mbY + 100, "REBAR_MAIN") // hook
        drawLine(sb, elevX + sMm - cover, mbY, elevX + sMm - cover, mbY + 100, "REBAR_MAIN") // hook
        drawText(sb, elevX + sMm/2, mbY - 120, "${result.reinforcementBottom.numBars}%%c${result.reinforcementBottom.diameter} Bottom (L=${(sMm + 200).toInt()})", "TEXT", 100.0)
        
        // Main top reinforcement
        val mtY = elevY + height - cover
        drawLine(sb, elevX + cover, mtY, elevX + sMm - cover, mtY, "REBAR_MAIN")
        drawText(sb, elevX + sMm/2, mtY + 100, "${result.reinforcementTop.numBars}%%c${result.reinforcementTop.diameter} Top", "TEXT", 100.0)

        // Stirrup Zones (Professional Confinement Detailing)
        if (result.stirrups.zones.isNotEmpty()) {
            result.stirrups.zones.forEach { zone ->
                val zStart = elevX + zone.startLocation
                val zEnd = elevX + zone.endLocation
                var curX = zStart
                while (curX < zEnd - 1.0) {
                    drawLine(sb, curX, elevY + cover, curX, elevY + height - cover, "STIRRUPS")
                    curX += zone.spacing
                }
                // Zone label bubble
                drawCircle(sb, (zStart+zEnd)/2.0, elevY + height + 600.0, 150.0, "TEXT")
                drawText(sb, (zStart + zEnd) / 2.0, elevY + height + 600.0, "%%c${zone.diameter}@${zone.spacing.toInt()}", "TEXT", 100.0)
            }
        }
        
        // Labels & Dims Elevation
        drawText(sb, elevX + sMm/2, elevY - 600.0, "REINFORCED BEAM ELEVATION VIEW", "TEXT", 250.0)
        drawHorizontalDimension(sb, elevX, elevX + sMm, elevY - 400.0, "Span = ${span}m")
        drawVerticalDimension(sb, elevY, elevY + height, elevX - 400.0, "h=${height.toInt()}")

        // 2. BEAM CROSS SECTIONS (Typical & Support)
        val secX = sMm + 2000.0
        val secY = 0.0
        drawRect(sb, secX, secY, width, height, "CONCRETE")
        drawRect(sb, secX + cover, secY + cover, width - 2*cover, height - 2*cover, "STIRRUPS")
        
        // Bottom bars in section
        val bCount = result.reinforcementBottom.numBars
        val bSpace = (width - 2 * cover) / (bCount - 1).coerceAtLeast(1)
        for (i in 0 until bCount) {
            drawCircle(sb, secX + cover + i * bSpace, secY + cover, 12.0, "REBAR_MAIN", 1)
        }
        // Top bars
        val tCount = result.reinforcementTop.numBars
        val tSpace = (width - 2 * cover) / (tCount - 1).coerceAtLeast(1)
        for (i in 0 until tCount) {
            drawCircle(sb, secX + cover + i * tSpace, secY + height - cover, 10.0, "REBAR_MAIN", 1)
        }
        
        drawText(sb, secX + width/2, secY - 600.0, "TYPICAL CROSS SECTION", "TEXT", 200.0)
        drawVerticalDimension(sb, secY, secY + height, secX + width + 400.0, "h=${height.toInt()}")
        drawHorizontalDimension(sb, secX, secX + width, secY + height + 400.0, "b=${width.toInt()}")

        // 3. PROJECT DATA & BOQ TABLE
        drawResultTable(sb, sMm + 6000.0, 4000.0, "BEAM ENGINEERING DATA", listOf(
            "Design Code" to result.designCodeName,
            "Moment Mu" to "${"%.1f".format(result.appliedMoment)} kN.m",
            "Shear Vu" to "${"%.1f".format(result.appliedShear)} kN",
            "Deflection" to "${"%.2f".format(result.deflection)} mm",
            "Status" to if(result.isSafe) "SAFE" else "UNSAFE",
            "Concrete Vol" to "${"%.2f".format(result.concreteVolume)} m3",
            "Steel Weight" to "${"%.1f".format(result.steelWeight)} kg"
        ))

        sb.append("0\nENDSEC\n0\nEOF\n")

        val file = File(outputPath)
        FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
        return file
    }

    /**
     * Exports a detailed column element to DXF.
     */
    fun exportColumnDetailed(
        result: CalculatorEngine.ColumnResult,
        width: Double,
        depth: Double,
        height: Double,
        outputPath: String
    ): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        
        // Layers
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nREBAR_MAIN\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nSTIRRUPS\n70\n0\n62\n2\n")
        sb.append("0\nLAYER\n2\nDIMENSIONS\n70\n0\n62\n5\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")

        sb.append("0\nSECTION\n2\nENTITIES\n")

        val cover = 40.0
        val hMm = height
        
        // 1. COLUMN ELEVATION
        val elevX = 0.0
        val elevY = 0.0
        drawRect(sb, elevX, elevY, width, hMm, "CONCRETE")
        
        // All vertical bars based on count (simplified distribution)
        val numBars = result.reinforcement.numBars
        val barsPerFace = (numBars / 4) + 1
        val spFace = (width - 2 * cover) / (barsPerFace - 1).coerceAtLeast(1)
        for (i in 0 until barsPerFace) {
            val bx = elevX + cover + i * spFace
            drawLine(sb, bx, elevY, bx, elevY + hMm, "REBAR_MAIN")
        }
        
        // Confinement Zones (Ties)
        result.stirrups.zones.forEach { zone ->
            val zStart = elevY + zone.startLocation
            val zEnd = elevY + zone.endLocation
            var curY = zStart
            while (curY < zEnd - 1.0) {
                drawLine(sb, elevX + cover, curY, elevX + width - cover, curY, "STIRRUPS")
                curY += zone.spacing
            }
            drawText(sb, elevX - 300.0, (zStart + zEnd) / 2.0, "%%c${zone.diameter}@${zone.spacing.toInt()}", "TEXT", 80.0)
        }

        drawText(sb, elevX + width/2, elevY - 400.0, "COLUMN ELEVATION", "TEXT", 150.0)

        // 2. COLUMN CROSS SECTION
        val secX = width + 2000.0
        val secY = 0.0
        drawRect(sb, secX, secY, width, depth, "CONCRETE")
        drawRect(sb, secX + cover, secY + cover, width - 2*cover, depth - 2*cover, "STIRRUPS")
        
        // Corner bars
        drawCircle(sb, secX + cover, secY + cover, 10.0, "REBAR_MAIN", 1)
        drawCircle(sb, secX + width - cover, secY + cover, 10.0, "REBAR_MAIN", 1)
        drawCircle(sb, secX + cover, secY + depth - cover, 10.0, "REBAR_MAIN", 1)
        drawCircle(sb, secX + width - cover, secY + depth - cover, 10.0, "REBAR_MAIN", 1)

        drawText(sb, secX + width/2, secY - 400.0, "CROSS SECTION", "TEXT", 150.0)
        
        // 3. TABLE
        drawResultTable(sb, width + 5000.0, 3000.0, "COLUMN DESIGN DATA", listOf(
            "Load Pu" to "${result.appliedAxial.toInt()} kN",
            "Section" to "${width.toInt()}x${depth.toInt()} mm",
            "Reinforcement" to "${result.reinforcement.numBars} %%c ${result.reinforcement.diameter}",
            "Stirrups" to "%%c ${result.stirrups.diameter} @ ${result.stirrups.spacing.toInt()}",
            "Status" to if(result.isSafe) "SAFE" else "UNSAFE"
        ))

        sb.append("0\nENDSEC\n0\nEOF\n")

        val file = File(outputPath)
        FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
        return file
    }

    /**
     * Exports a detailed slab element to DXF.
     */
    fun exportSlabDetailed(
        result: CalculatorEngine.SlabResult,
        lx: Double,
        ly: Double,
        outputPath: String
    ): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        
        // Layers
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nREBAR_MAIN\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nREBAR_DIST\n70\n0\n62\n2\n")
        sb.append("0\nLAYER\n2\nREBAR_TOP\n70\n0\n62\n6\n") // Magenta
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")

        sb.append("0\nSECTION\n2\nENTITIES\n")

        val lxMm = lx * 1000.0
        val lyMm = ly * 1000.0
        
        // 1. SLAB REINFORCEMENT PLAN
        drawRect(sb, 0.0, 0.0, lxMm, lyMm, "CONCRETE")
        
        // Main bottom reinforcement (X-Dir)
        val mSp = result.reinforcementMain.spacing
        var curY = mSp / 2
        while (curY < lyMm) {
            drawLine(sb, 100.0, curY, lxMm - 100.0, curY, "REBAR_MAIN")
            curY += mSp * 5 // Plot subset for clarity
        }
        
        // Dist bottom reinforcement (Y-Dir)
        val dSp = result.reinforcementSecondary.spacing
        var curX = dSp / 2
        while (curX < lxMm) {
            drawLine(sb, curX, 100.0, curX, lyMm - 100.0, "REBAR_DIST")
            curX += dSp * 5
        }

        // Top reinforcement over supports (schematic)
        val topLen = lxMm * 0.25
        drawLine(sb, 0.0, lyMm/2, topLen, lyMm/2, "REBAR_TOP")
        drawLine(sb, lxMm - topLen, lyMm/2, lxMm, lyMm/2, "REBAR_TOP")

        drawText(sb, lxMm/2, -800.0, "SLAB REINFORCEMENT PLAN (WORKSHOP DRAWING)", "TEXT", 300.0)
        drawHorizontalDimension(sb, 0.0, lxMm, -400.0, "Lx = ${lxMm.toInt()} mm")
        drawVerticalDimension(sb, 0.0, lyMm, -400.0, "Ly = ${lyMm.toInt()} mm")

        // 2. DESIGN DATA TABLE
        drawResultTable(sb, lxMm + 3000.0, lyMm / 2.0, "SLAB DESIGN DATA", listOf(
            "Slab Type" to result.type.displayNameEn,
            "Thickness" to "${result.thickness.toInt()} mm",
            "Main Rebar" to result.reinforcementMain.barString,
            "Dist Rebar" to result.reinforcementSecondary.barString,
            "Concrete Vol" to "${"%.2f".format(result.concreteVolume)} m3",
            "Steel Weight" to "${"%.1f".format(result.steelWeight)} kg",
            "Status" to if(result.isSafe) "SAFE" else "UNSAFE"
        ))

        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath)
        FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
        return file
    }
    
    /**
     * Exports a detailed steel warehouse portal frame and components to DXF.
     */
    fun exportSteelWarehouseDetailed(
        inputs: com.civileg.app.domain.entities.SteelWarehouseInputs,
        result: com.civileg.app.domain.entities.SteelWarehouseAnalysisResult,
        outputPath: String
    ): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        
        // Layers
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nMAIN_FRAME\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nPURLINS\n70\n0\n62\n4\n")
        sb.append("0\nLAYER\n2\nLOADS\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nDIMENSIONS\n70\n0\n62\n5\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")

        sb.append("0\nSECTION\n2\nENTITIES\n")

        val span = inputs.span * 1000.0
        val eh = inputs.eaveHeight * 1000.0
        val rh = inputs.ridgeHeight * 1000.0
        val midX = span / 2.0
        
        // 1. PORTAL FRAME ELEVATION
        // Columns
        drawRect(sb, 0.0, 0.0, 300.0, eh, "MAIN_FRAME") // Left column approx
        drawRect(sb, span - 300.0, 0.0, 300.0, eh, "MAIN_FRAME") // Right column
        
        // Rafters
        drawLine(sb, 0.0, eh, midX, rh, "MAIN_FRAME")
        drawLine(sb, span, eh, midX, rh, "MAIN_FRAME")
        
        // Purlins
        val numPurlins = 5
        for (i in 1..numPurlins) {
            val t = i.toDouble() / numPurlins
            val px = t * midX
            val py = eh + t * (rh - eh)
            drawCircle(sb, px, py, 50.0, "PURLINS")
            
            val px2 = span - t * midX
            drawCircle(sb, px2, py, 50.0, "PURLINS")
        }

        // Loads
        drawText(sb, midX, rh + 500.0, "W = ${inputs.deadLoad + inputs.liveLoad} kN/m2", "LOADS", 200.0)

        // Dimensions
        drawLine(sb, 0.0, -500.0, span, -500.0, "DIMENSIONS")
        drawText(sb, midX, -800.0, "Span = ${inputs.span}m", "DIMENSIONS", 150.0)

        // 2. COMPONENT DETAILS (Draw Section Shapes)
        val detailX = span + 3000.0
        drawSteelSectionShape(sb, detailX, eh, result.mainFrame.columnSection, "Column: ${result.mainFrame.columnSection.sectionName}")
        drawSteelSectionShape(sb, detailX, eh - 3000.0, result.mainFrame.rafterSection, "Rafter: ${result.mainFrame.rafterSection.sectionName}")

        // 3. LEGEND & LOADS TABLE
        drawResultTable(sb, span + 8000.0, eh, "STEEL WAREHOUSE DATA", listOf(
            "Span" to "${inputs.span} m",
            "Height" to "${inputs.eaveHeight} m",
            "Dead Load" to "${inputs.deadLoad} kN/m2",
            "Live Load" to "${inputs.liveLoad} kN/m2",
            "Total Weight" to "${"%.2f".format(result.totalWeight)} Tons"
        ))

        sb.append("0\nENDSEC\n0\nEOF\n")

        val file = File(outputPath)
        FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
        return file
    }

    /**
     * Exports a detailed retaining wall element to DXF.
     */
    fun exportRetainingWallDetailed(
        result: CalculatorEngine.RetainingWallResult,
        outputPath: String
    ): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        
        // Layers
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nREBAR\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nDIMENSIONS\n70\n0\n62\n5\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")

        sb.append("0\nSECTION\n2\nENTITIES\n")

        val h = result.height * 1000.0
        val tw = result.stemThickness
        val bw = result.baseWidth
        val thk = result.stemThickness // assumed same as stem for base
        val cover = 50.0

        // 1. WALL SECTION
        val ox = 0.0; val oy = 0.0
        // Base
        drawRect(sb, ox, oy, bw, thk, "CONCRETE")
        // Stem (tapered schematic)
        val toeW = bw / 3.0
        drawLine(sb, ox + toeW, oy + thk, ox + toeW, oy + thk + h, "CONCRETE") // Back face
        drawLine(sb, ox + toeW + tw, oy + thk, ox + toeW + tw * 0.6, oy + thk + h, "CONCRETE") // Front face tapered
        drawLine(sb, ox + toeW, oy + thk + h, ox + toeW + tw * 0.6, oy + thk + h, "CONCRETE") // Top face
        
        // Reinforcement
        val mainDia = result.stemReinforcement.diameter
        val mainSp = result.stemReinforcement.spacing
        drawLine(sb, ox + toeW + tw - cover, oy + thk, ox + toeW + tw - cover, oy + thk + h - cover, "REBAR") // Main vertical
        drawLine(sb, ox + cover, oy + cover, ox + bw - cover, oy + cover, "REBAR") // Main bottom
        
        // Shear Key (schematic)
        drawRect(sb, ox + toeW + tw/4, oy - 300.0, tw/2, 300.0, "CONCRETE")

        // Labels & Dims
        drawText(sb, ox + bw/2, oy - 600.0, "RETAINING WALL SECTION", "TEXT", 200.0)
        drawVerticalDimension(sb, oy, oy + thk + h, ox - 500.0, "H_tot=${(h+thk).toInt()}")
        drawHorizontalDimension(sb, ox, ox + bw, oy - 350.0, "B=${bw.toInt()}")

        // 2. SOIL PRESSURE DIAGRAM
        val pX = -2000.0
        val pY = 0.0
        drawLine(sb, pX, pY, pX - 800.0, pY, "LOADS") // Max pressure at toe
        drawLine(sb, pX, pY + bw, pX - 200.0, pY + bw, "LOADS") // Min pressure at heel
        drawLine(sb, pX - 800.0, pY, pX - 200.0, pY + bw, "LOADS")
        drawText(sb, pX - 1000.0, pY, "q_max", "TEXT", 80.0)
        drawText(sb, pX - 400.0, pY + bw, "q_min", "TEXT", 80.0)

        // 3. DATA TABLE
        drawResultTable(sb, bw + 3000.0, h / 2.0, "RETAINING WALL DESIGN DATA", listOf(
            "Wall Height" to "${result.height} m",
            "Stem Thickness" to "${result.stemThickness.toInt()} mm",
            "Base Width" to "${result.baseWidth.toInt()} mm",
            "Active Ka" to "%.3f".format(result.ka),
            "F.S. Overturning" to "%.2f".format(result.factorOfSafetyOverturning),
            "F.S. Sliding" to "%.2f".format(result.factorOfSafetySliding),
            "Stem Rebar" to result.stemReinforcement.barString,
            "Base Rebar" to result.baseReinforcement.barString
        ))

        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath)
        FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
        return file
    }

    /**
     * Exports a detailed stair element (elevation) to DXF.
     */
    fun exportStairDetailed(
        result: CalculatorEngine.StairResult,
        outputPath: String
    ): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        
        // Layers
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nREBAR\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")

        sb.append("0\nSECTION\n2\nENTITIES\n")

        val s = result.span * 1000.0
        val r = result.riser
        val t = result.tread
        val thk = result.thickness
        val numSteps = (s / t).toInt().coerceIn(1, 25)
        val cover = 25.0

        // 1. STAIR ELEVATION
        val ox = 0.0; val oy = 0.0
        var curX = ox; var curY = oy
        
        // Concrete Profile
        for (i in 0 until numSteps) {
            drawLine(sb, curX, curY, curX, curY + r, "CONCRETE") // Riser
            drawLine(sb, curX, curY + r, curX + t, curY + r, "CONCRETE") // Tread
            curX += t; curY += r
        }
        
        // Soffit
        val angle = atan2(r, t)
        val dx = thk * sin(angle)
        val dy = thk * cos(angle)
        drawLine(sb, ox + dx, oy - dy, curX + dx, curY - dy, "CONCRETE")
        drawLine(sb, ox, oy, ox + dx, oy - dy, "CONCRETE")
        drawLine(sb, curX, curY, curX + dx, curY - dy, "CONCRETE")

        // Main Longitudinal Rebar
        val mbStartX = ox + dx - cover * sin(angle)
        val mbStartY = oy - dy + cover * cos(angle)
        val mbEndX = curX + dx - cover * sin(angle)
        val mbEndY = curY - dy + cover * cos(angle)
        drawLine(sb, mbStartX, mbStartY, mbEndX, mbEndY, "REBAR")
        
        // Transverse bars (circles in elevation)
        val numTrans = 10
        for (i in 0..numTrans) {
            val tx = mbStartX + (i.toDouble() / numTrans) * (mbEndX - mbStartX)
            val ty = mbStartY + (i.toDouble() / numTrans) * (mbEndY - mbStartY)
            drawCircle(sb, tx, ty, 10.0, "REBAR")
        }

        // Labels & Dims
        drawText(sb, ox + s/2, oy - 1200.0, "STAIRCASE REINFORCEMENT DETAIL", "TEXT", 250.0)
        drawHorizontalDimension(sb, ox, curX, oy - 600.0, "Total Span = ${result.span} m")
        drawVerticalDimension(sb, oy, curY, ox - 600.0, "Total Height = ${(numSteps*r).toInt()} mm")

        // 2. DESIGN DATA TABLE
        drawResultTable(sb, s + 3000.0, curY / 2.0, "STAIRCASE DESIGN DATA", listOf(
            "Type" to result.type.displayNameEn,
            "Riser x Tread" to "${r.toInt()} x ${t.toInt()} mm",
            "Waist Thick" to "${result.thickness.toInt()} mm",
            "Main Rebar" to result.reinforcement.barString,
            "Dist. Rebar" to result.distributionReinforcement.barString,
            "Concrete Vol" to "${"%.2f".format(result.concreteVolume)} m3",
            "Status" to if(result.isSafe) "SAFE" else "UNSAFE"
        ))

        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath)
        FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
        return file
    }

    private fun drawSteelSectionShape(sb: StringBuilder, x: Double, y: Double, section: SteelSectionType, label: String) {
        val h = section.depth
        val b = section.width
        val tf = section.flangeThickness
        val tw = section.webThickness
        
        // I-Section drawing
        drawRect(sb, x - b/2, y, b, tf, "MAIN_FRAME") // Top flange
        drawRect(sb, x - b/2, y - h + tf, b, tf, "MAIN_FRAME") // Bottom flange
        drawRect(sb, x - tw/2, y - h + tf, tw, h - 2*tf, "MAIN_FRAME") // Web
        
        // Draw bolt holes (openings) schematically
        drawCircle(sb, x - b/4, y + tf/2, 20.0, "MAIN_FRAME")
        drawCircle(sb, x + b/4, y + tf/2, 20.0, "MAIN_FRAME")

        drawText(sb, x, y - h - 300.0, label, "TEXT", 150.0, 4)
        
        // Add Dimensions
        drawVerticalDimension(sb, y - h, y, x + b/2 + 200.0, "h=${h.toInt()}")
        drawHorizontalDimension(sb, x - b/2, x + b/2, y + 500.0, "b=${b.toInt()}")
    }

    /**
     * Exports a detailed frame analysis with diagrams to DXF.
     */
    fun exportFrameAnalysisDetailed(
        nodes: List<com.civileg.app.domain.entities.FrameNode>, 
        members: List<com.civileg.app.domain.entities.FrameMember>,
        result: com.civileg.app.domain.entities.FrameAnalysisResult,
        outputPath: String
    ): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        
        // Layers
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nGEOMETRY\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nBMD\n70\n0\n62\n1\n") // Red for Moment
        sb.append("0\nLAYER\n2\nSFD\n70\n0\n62\n4\n") // Cyan for Shear
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")

        sb.append("0\nSECTION\n2\nENTITIES\n")

        val scale = 500.0 // Scale coordinates to units for visibility
        val diagramScale = 1.0 // Scale for moments/shear
        
        // 1. DRAW GEOMETRY
        members.forEach { member ->
            val n1 = nodes.find { it.id == member.nodeI } ?: return@forEach
            val n2 = nodes.find { it.id == member.nodeJ } ?: return@forEach
            drawLine(sb, n1.x * scale, n1.y * scale, n2.x * scale, n2.y * scale, "GEOMETRY")
            
            // Label member ID
            drawText(sb, (n1.x + n2.x) / 2.0 * scale, (n1.y + n2.y) / 2.0 * scale + 50, "Member ${member.id}", "TEXT", 40.0)
        }

        // 2. DRAW BENDING MOMENT DIAGRAM (BMD)
        result.memberEndForces.forEach { forces ->
            val member = members.find { it.id == forces.memberId } ?: return@forEach
            val n1 = nodes.find { it.id == member.nodeI } ?: return@forEach
            val n2 = nodes.find { it.id == member.nodeJ } ?: return@forEach
            
            val dx = n2.x - n1.x
            val dy = n2.y - n1.y
            val angle = atan2(dy, dx)
            val perpAngle = angle + PI / 2.0
            
            val m1 = forces.mi_z
            val m2 = -forces.mj_z 
            
            val p1_off = Pair(
                (n1.x * scale + m1 * diagramScale * cos(perpAngle)),
                (n1.y * scale + m1 * diagramScale * sin(perpAngle))
            )
            val p2_off = Pair(
                (n2.x * scale + m2 * diagramScale * cos(perpAngle)),
                (n2.y * scale + m2 * diagramScale * sin(perpAngle))
            )
            
            drawLine(sb, n1.x * scale, n1.y * scale, p1_off.first, p1_off.second, "BMD")
            drawLine(sb, p1_off.first, p1_off.second, p2_off.first, p2_off.second, "BMD")
            drawLine(sb, p2_off.first, p2_off.second, n2.x * scale, n2.y * scale, "BMD")
            
            // Value labels
            drawText(sb, p1_off.first, p1_off.second, String.format(Locale.US, "%.1f", m1), "TEXT", 30.0)
            drawText(sb, p2_off.first, p2_off.second, String.format(Locale.US, "%.1f", m2), "TEXT", 30.0)
        }

        drawText(sb, 0.0, -1000.0, "FRAME ANALYSIS: GEOMETRY & BENDING MOMENT DIAGRAM", "TEXT", 150.0)

        // 3. REACTIONS TABLE
        val rtX = 2000.0 * scale
        val rtY = 0.0
        drawResultTable(sb, rtX, rtY, "NODAL REACTIONS", result.nodeResults.filter { abs(it.reactionFy) > 0.1 }.map { 
            "Node ${it.nodeId}" to "Ry=${String.format(Locale.US, "%.1f", it.reactionFy)} kN" 
        })
        
        // 4. NODAL DISPLACEMENTS TABLE
        drawResultTable(sb, rtX + 7000.0, rtY, "MAX DISPLACEMENTS (mm)", result.nodeResults.take(10).map {
            "Node ${it.nodeId}" to "dy=${String.format(Locale.US, "%.3f", it.dy * 1000.0)}"
        })

        sb.append("0\nENDSEC\n0\nEOF\n")

        val file = File(outputPath)
        FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
        return file
    }

    private fun drawHorizontalDimension(sb: StringBuilder, x1: Double, x2: Double, y: Double, text: String, color: Int = 5) {
        drawLine(sb, x1, y, x2, y, "DIMENSIONS", color)
        drawLine(sb, x1, y + 100.0, x1, y - 100.0, "DIMENSIONS", color)
        drawLine(sb, x2, y + 100.0, x2, y - 100.0, "DIMENSIONS", color)
        drawText(sb, (x1 + x2) / 2.0, y + 100.0, text, "DIMENSIONS", 100.0, color)
    }

    private fun drawVerticalDimension(sb: StringBuilder, y1: Double, y2: Double, x: Double, text: String, color: Int = 5) {
        drawLine(sb, x, y1, x, y2, "DIMENSIONS", color)
        drawLine(sb, x + 100.0, y1, x - 100.0, y1, "DIMENSIONS", color)
        drawLine(sb, x + 100.0, y2, x - 100.0, y2, "DIMENSIONS", color)
        drawText(sb, x - 150.0, (y1 + y2) / 2.0, text, "DIMENSIONS", 100.0, color)
    }

    /**
     * Exports a detailed water tank element (section/plan) to DXF.
     */
    fun exportTankDetailed(
        result: CalculatorEngine.TankResult,
        outputPath: String
    ): File {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        
        // Layers
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n")
        sb.append("0\nLAYER\n2\nCONCRETE\n70\n0\n62\n7\n")
        sb.append("0\nLAYER\n2\nREBAR\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nWATER\n70\n0\n62\n5\n") // Blue
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")

        sb.append("0\nSECTION\n2\nENTITIES\n")

        val l = result.length * 1000.0
        val w = result.width * 1000.0
        val h = result.height * 1000.0
        val tw = result.wallThickness
        val tb = result.baseThickness
        val cover = 50.0

        // 1. TANK SECTION VIEW
        val ox = 0.0; val oy = 0.0
        // Base
        drawRect(sb, ox, oy, l + 2*tw, tb, "CONCRETE")
        // Walls
        drawRect(sb, ox, oy + tb, tw, h, "CONCRETE")
        drawRect(sb, ox + l + tw, oy + tb, tw, h, "CONCRETE")
        
        // Water line (dynamic)
        val wl = h * 0.9
        drawLine(sb, ox + tw + 100, oy + tb + wl, ox + tw + l - 100, oy + tb + wl, "WATER")
        drawText(sb, ox + tw + l/2, oy + tb + wl + 150, "MAX WATER LEVEL", "WATER", 80.0)
        
        // Internal pressure schematic
        val numArr = 5
        for (i in 1..numArr) {
            val y = oy + tb + (i.toDouble() / numArr) * wl
            val arrowLen = (i.toDouble() / numArr) * 400.0
            drawLine(sb, ox + tw, y, ox + tw + arrowLen, y, "LOADS")
            drawLine(sb, ox + tw + l, y, ox + tw + l - arrowLen, y, "LOADS")
        }
        
        // Reinforcement
        drawLine(sb, ox + tw - cover, oy + tb, ox + tw - cover, oy + tb + h, "REBAR")
        drawLine(sb, ox + tw + l + cover, oy + tb, ox + tw + l + cover, oy + tb + h, "REBAR")
        
        // Labels & Dims
        drawText(sb, ox + l/2 + tw, oy - 600.0, "TANK CROSS SECTION", "TEXT", 200.0)
        drawVerticalDimension(sb, oy, oy + h + tb, ox - 500.0, "H_tot=${(h+tb).toInt()}")
        drawHorizontalDimension(sb, ox, ox + l + 2*tw, oy - 350.0, "B_tot=${(l+2*tw).toInt()}")

        // 2. TANK PLAN VIEW
        val planX = 0.0
        val planY = h + 4000.0
        drawRect(sb, planX, planY, l + 2*tw, w + 2*tw, "CONCRETE") // Outer
        drawRect(sb, planX + tw, planY + tw, l, w, "CONCRETE") // Inner
        drawText(sb, planX + l/2 + tw, planY - 600.0, "TANK PLAN VIEW", "TEXT", 200.0)

        // 3. DESIGN DATA TABLE
        drawResultTable(sb, l + 4000.0, h / 2.0, "WATER TANK DESIGN DATA", listOf(
            "Location" to result.type.displayNameEn,
            "Capacity" to "${"%.1f".format(result.capacityM3)} m3",
            "Height" to "${result.height} m",
            "Wall Thick" to "${result.wallThickness.toInt()} mm",
            "Base Thick" to "${result.baseThickness.toInt()} mm",
            "Wall Rebar" to result.wallReinforcement.barString,
            "Base Rebar" to result.baseReinforcement.barString,
            "Max Pressure" to "${"%.1f".format(result.waterPressure)} kN/m2"
        ))

        sb.append("0\nENDSEC\n0\nEOF\n")
        val file = File(outputPath)
        FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
        return file
    }

    private fun drawResultTable(sb: StringBuilder, x: Double, y: Double, title: String, data: List<Pair<String, String>>) {
        val rowH = 300.0
        val colW = 3000.0
        drawText(sb, x, y + rowH, title, "TEXT", 150.0, 2)
        drawRect(sb, x, y - data.size * rowH, colW * 2, (data.size + 1) * rowH, "TEXT")
        drawLine(sb, x + colW, y + rowH, x + colW, y - data.size * rowH, "TEXT")
        
        data.forEachIndexed { i, (k, v) ->
            val curY = y - i * rowH
            drawText(sb, x + 100.0, curY, k, "TEXT", 100.0)
            drawText(sb, x + colW + 100.0, curY, v, "TEXT", 100.0)
            drawLine(sb, x, curY - rowH / 2.0, x + colW * 2, curY - rowH / 2.0, "TEXT")
        }
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
