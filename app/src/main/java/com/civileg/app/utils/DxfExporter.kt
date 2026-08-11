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
        
        // Concrete outline
        drawRect(sb, elevX, elevY, sMm, height, "CONCRETE")
        
        // Main bottom reinforcement
        drawLine(sb, elevX + cover, elevY + cover, elevX + sMm - cover, elevY + cover, "REBAR_MAIN")
        drawText(sb, elevX + sMm/2, elevY + cover - 100, "${result.reinforcementBottom.numBars}%%c${result.reinforcementBottom.diameter} Bottom", "TEXT", 80.0)
        
        // Main top reinforcement
        drawLine(sb, elevX + cover, elevY + height - cover, elevX + sMm - cover, elevY + height - cover, "REBAR_MAIN")
        drawText(sb, elevX + sMm/2, elevY + height - cover + 50, "${result.reinforcementTop.numBars}%%c${result.reinforcementTop.diameter} Top", "TEXT", 80.0)

        // Stirrups in elevation
        val stirrupSpacing = result.stirrups.spacing
        var curX = elevX + cover + 50.0
        while (curX <= elevX + sMm - cover - 50.0) {
            drawLine(sb, curX, elevY + cover, curX, elevY + height - cover, "STIRRUPS")
            curX += stirrupSpacing
        }
        
        // Labels Elevation
        drawText(sb, elevX + sMm/2, elevY - 400.0, "BEAM ELEVATION VIEW", "TEXT", 150.0)
        drawText(sb, elevX + sMm/2, elevY - 600.0, "Span = ${span}m, Section: ${width.toInt()}x${height.toInt()}mm", "TEXT", 100.0)

        // 2. BEAM CROSS SECTION
        val secX = sMm + 1000.0
        val secY = 0.0
        drawRect(sb, secX, secY, width, height, "CONCRETE")
        
        // Bottom bars in section (circles)
        val bCount = result.reinforcementBottom.numBars
        val bSpace = (width - 2 * cover) / (bCount - 1).coerceAtLeast(1)
        for (i in 0 until bCount) {
            drawCircle(sb, secX + cover + i * bSpace, secY + cover, 10.0, "REBAR_MAIN", 1)
        }
        
        // Top bars in section
        val tCount = result.reinforcementTop.numBars
        val tSpace = (width - 2 * cover) / (tCount - 1).coerceAtLeast(1)
        for (i in 0 until tCount) {
            drawCircle(sb, secX + cover + i * tSpace, secY + height - cover, 10.0, "REBAR_MAIN", 1)
        }
        
        // Closed stirrup in section
        drawRect(sb, secX + cover, secY + cover, width - 2*cover, height - 2*cover, "STIRRUPS")

        drawText(sb, secX + width/2, secY - 400.0, "CROSS SECTION", "TEXT", 150.0)

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
        val hMm = height * 1.0 // Height is already in mm usually in inputs but let's be safe
        
        // 1. COLUMN ELEVATION
        val elevX = 0.0
        val elevY = 0.0
        drawRect(sb, elevX, elevY, width, hMm, "CONCRETE")
        
        // Main vertical bars
        drawLine(sb, elevX + cover, elevY, elevX + cover, elevY + hMm, "REBAR_MAIN")
        drawLine(sb, elevX + width - cover, elevY, elevX + width - cover, elevY + hMm, "REBAR_MAIN")
        
        // Ties (Stirrups) in elevation
        val tieSpacing = result.stirrups.spacing
        var curY = elevY + 100.0
        while (curY <= elevY + hMm - 100.0) {
            drawLine(sb, elevX + cover, curY, elevX + width - cover, curY, "STIRRUPS")
            curY += tieSpacing
        }

        drawText(sb, elevX + width/2, elevY - 400.0, "COLUMN ELEVATION", "TEXT", 150.0)

        // 2. COLUMN CROSS SECTION
        val secX = width + 1000.0
        val secY = 0.0
        drawRect(sb, secX, secY, width, depth, "CONCRETE")
        
        // Closed tie
        drawRect(sb, secX + cover, secY + cover, width - 2*cover, depth - 2*cover, "STIRRUPS")
        
        // Corner bars
        drawCircle(sb, secX + cover, secY + cover, 10.0, "REBAR_MAIN", 1)
        drawCircle(sb, secX + width - cover, secY + cover, 10.0, "REBAR_MAIN", 1)
        drawCircle(sb, secX + cover, secY + depth - cover, 10.0, "REBAR_MAIN", 1)
        drawCircle(sb, secX + width - cover, secY + depth - cover, 10.0, "REBAR_MAIN", 1)

        drawText(sb, secX + width/2, secY - 400.0, "CROSS SECTION", "TEXT", 150.0)
        drawText(sb, secX + width/2, secY - 600.0, "${result.reinforcement.numBars}%%c${result.reinforcement.diameter}", "TEXT", 100.0)

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
        sb.append("0\nLAYER\n2\nREBAR_X\n70\n0\n62\n1\n")
        sb.append("0\nLAYER\n2\nREBAR_Y\n70\n0\n62\n2\n")
        sb.append("0\nLAYER\n2\nTEXT\n70\n0\n62\n3\n")
        sb.append("0\nENDTAB\n0\nENDSEC\n")

        sb.append("0\nSECTION\n2\nENTITIES\n")

        val lxMm = lx * 1000.0
        val lyMm = ly * 1000.0
        
        // 1. SLAB PLAN
        drawRect(sb, 0.0, 0.0, lxMm, lyMm, "CONCRETE")
        
        // Rebar mesh (simplified for DXF)
        val spacing = 200.0
        var x = spacing
        while (x < lxMm) {
            drawLine(sb, x, 0.0, x, lyMm, "REBAR_X")
            x += spacing * 5 // Draw every 5th bar for clarity in CAD
        }
        var y = spacing
        while (y < lyMm) {
            drawLine(sb, 0.0, y, lxMm, y, "REBAR_Y")
            y += spacing * 5
        }

        drawText(sb, lxMm/2, -500.0, "SLAB REINFORCEMENT PLAN", "TEXT", 300.0)
        drawText(sb, lxMm/2, -900.0, "Type: ${result.type.displayNameEn}, t=${result.thickness}mm", "TEXT", 200.0)
        drawText(sb, lxMm/2, -1200.0, "Main: ${result.reinforcementMain.barString}", "TEXT", 150.0)

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
        val detailX = span + 2000.0
        drawSteelSectionShape(sb, detailX, eh, result.mainFrame.columnSection, "Column: ${result.mainFrame.columnSection.sectionName}")
        drawSteelSectionShape(sb, detailX, eh - 2000.0, result.mainFrame.rafterSection, "Rafter: ${result.mainFrame.rafterSection.sectionName}")

        sb.append("0\nENDSEC\n0\nEOF\n")

        val file = File(outputPath)
        FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
        return file
    }

    private fun drawSteelSectionShape(sb: StringBuilder, x: Double, y: Double, section: com.civileg.app.domain.entities.SteelSectionType, label: String) {
        val h = section.depth
        val b = section.width
        val tf = section.flangeThickness
        val tw = section.webThickness
        
        // I-Section drawing
        drawRect(sb, x - b/2, y, b, tf, "MAIN_FRAME") // Top flange
        drawRect(sb, x - b/2, y - h + tf, b, tf, "MAIN_FRAME") // Bottom flange
        drawRect(sb, x - tw/2, y - h + tf, tw, h - 2*tf, "MAIN_FRAME") // Web
        
        drawText(sb, x, y - h - 300.0, label, "TEXT", 100.0)
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
