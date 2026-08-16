package com.civileg.app.utils

import com.civileg.app.utils.CalculatorEngine.*
import com.civileg.app.domain.entities.SteelSectionType
import com.civileg.app.domain.entities.SteelMemberResult
import com.civileg.app.domain.entities.SteelInputs
import com.civileg.app.domain.entities.SteelWarehouseInputs
import com.civileg.app.domain.entities.SteelWarehouseAnalysisResult
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameAnalysisResult
import com.civileg.app.domain.entities.SupportType as FrameSupportType
import com.civileg.app.domain.entities.depth
import com.civileg.app.domain.entities.width
import com.civileg.app.domain.entities.webThickness
import com.civileg.app.domain.entities.flangeThickness
import kotlin.math.*
import java.util.Locale

/**
 * Professional AutoCAD DXF Export Engine.
 *
 * Generates fully valid DXF (AC1015 / AutoCAD 2000) text content for
 * concrete element workshop drawings.  Every method is pure-Kotlin
 * (no Android framework imports) so it can be unit-tested on the JVM.
 *
 * **Layer convention**
 * | Layer         | ACI colour | Purpose                       |
 * |---------------|------------|-------------------------------|
 * | CONCRETE      | 7 (white)  | Concrete outlines / hatching  |
 * | REBAR         | 1 (red)    | Main reinforcement bars       |
 * | STIRRUPS      | 2 (yellow) | Shear links / ties            |
 * | DIMENSIONS    | 5 (blue)   | Dimension lines + ticks       |
 * | TEXT          | 3 (green)  | Annotations / labels / tables |
 * | CENTER_LINE   | 1 (red)    | Center-lines / grid axes      |
 * | HATCH         | 9 (cyan)   | Section hatching              |
 * | LOADS         | 6 (magenta)| Load diagrams / arrows        |
 *
 * All coordinates are in **millimetres**.
 */
object DxfExportEngine {

    // ── Standard layers used by every drawing ──────────────────────
    private val STANDARD_LAYERS = listOf(
        LayerDef("CONCRETE",    7),
        LayerDef("REBAR",       1),
        LayerDef("STIRRUPS",    2),
        LayerDef("DIMENSIONS",  5),
        LayerDef("TEXT",        3),
        LayerDef("CENTER_LINE", 1),
        LayerDef("HATCH",       9),
        LayerDef("LOADS",       6)
    )

    private data class LayerDef(val name: String, val color: Int)

    // Handle counter for valid DXF entity references
    private var nextHandle = 0x100L
    private fun nextH(): String = (nextHandle++).toString(16).uppercase()
    private fun fmt(v: Double) = String.format(Locale.US, "%.4f", v)
    private var layerTableHandle = ""
    private var ltypeTableHandle = ""
    private var styleTableHandle = ""
    private var viewTableHandle = ""
    private var ucsTableHandle = ""
    private var appidTableHandle = ""
    private var vportTableHandle = ""
    private var dictHandle = ""

    // ═══════════════════════════════════════════════════════════════
    //  PUBLIC API  –  one method per element type, returns DXF String
    // ═══════════════════════════════════════════════════════════════

    // ── 1. BEAM ────────────────────────────────────────────────────
    /**
     * Generates a full beam workshop DXF drawing.
     * @param spanM  span in metres (converted internally to mm)
     */
    fun generateBeamDxf(
        result: BeamResult,
        spanM: Double,
        fcu: Double = 25.0,
        fy: Double = 360.0
    ): String {
        val sb = StringBuilder()
        val span = spanM * 1000.0
        val w = result.width
        val h = result.depth
        val cover = 50.0

        writeHeader(sb)
        writeClassesSection(sb)
        writeLayerTable(sb, STANDARD_LAYERS)
        writeBlocksSection(sb)
        sb.append("0\nSECTION\n2\nENTITIES\n")

        // ── Title block ──
        drawText(sb, 0.0, -1200.0, "BEAM REINFORCEMENT DETAIL", "TEXT", 250.0, 7)
        val codeName = when (result.code) {
            DesignCode.ACI -> "ACI 318"
            DesignCode.SAUDI -> "SBC 304"
            else -> "ECP 203"
        }
        drawText(sb, 0.0, -1600.0, "Code: $codeName  |  Span: ${spanM}m  |  Section: ${w.toInt()}x${h.toInt()} mm", "TEXT", 140.0, 3)

        // ── ELEVATION VIEW ──
        val elY = 0.0
        // Concrete outline
        drawRect(sb, 0.0, elY, span, h, "CONCRETE")
        // Support symbols (triangles)
        drawSupportSymbol(sb, 0.0, elY + h, if (result.supportType == SupportType.FIXED_FIXED || result.supportType == SupportType.FIXED_HINGED) 0.0 else 200.0)
        drawSupportSymbol(sb, span, elY + h, if (result.supportType == SupportType.FIXED_FIXED) 0.0 else 200.0)

        // Bottom reinforcement (main bars)
        val botY = elY + cover
        val botDia = result.reinforcementBottom.diameter.toDouble()
        val nBot = result.reinforcementBottom.numBars.coerceAtLeast(1)
        val barSpacing = if (nBot > 1) (w - 2 * cover) / (nBot - 1) else 0.0
        for (i in 0 until nBot) {
            val bx = cover + i * barSpacing
            drawRebarLine(sb, -300.0, bx, span + 300.0, bx, botDia, "REBAR")
        }
        // Bottom bar label
        drawText(sb, span + 400.0, botY + 50.0,
            result.reinforcementBottom.barString, "TEXT", 120.0, 1)

        // Top reinforcement (if any)
        if (result.reinforcementTop.numBars > 0) {
            val topY = elY + h - cover
            val topDia = result.reinforcementTop.diameter.toDouble()
            val nTop = result.reinforcementTop.numBars
            val topSpacing = if (nTop > 1) (w - 2 * cover) / (nTop - 1) else 0.0
            for (i in 0 until nTop) {
                val tx = cover + i * topSpacing
                drawRebarLine(sb, -300.0, tx, span + 300.0, tx, topDia, "REBAR")
            }
            drawText(sb, span + 400.0, topY + 50.0,
                result.reinforcementTop.barString, "TEXT", 120.0, 1)
        }

        // Stirrups
        val stDia = result.stirrups.diameter.toDouble()
        val condLen = result.stirrups.condensationZoneLength
        val spSupport = result.stirrups.spacingAtSupport.let { if (it <= 0.0) result.stirrups.spacing else it }
        val spMid = result.stirrups.spacingAtMidspan.let { if (it <= 0.0) result.stirrups.spacing else it }

        // Draw stirrups – condensation zone near each support
        var sx = 0.0
        val effectiveCondLen = if (condLen > 0.0) condLen else span * 0.15
        while (sx < effectiveCondLen) {
            drawStirrup(sb, sx, elY, w, h, cover, stDia)
            sx += spSupport
        }
        // Mid-span zone
        while (sx < span - effectiveCondLen) {
            drawStirrup(sb, sx, elY, w, h, cover, stDia)
            sx += spMid
        }
        // Right condensation zone
        while (sx < span) {
            drawStirrup(sb, sx, elY, w, h, cover, stDia)
            sx += spSupport
        }

        // Stirrup labels
        if (spSupport != spMid) {
            drawText(sb, effectiveCondLen / 2, elY + h + 300.0,
                "%%c${stDia.toInt()}@${spSupport.toInt()}", "TEXT", 100.0, 2)
            drawText(sb, span / 2, elY + h + 300.0,
                "%%c${stDia.toInt()}@${spMid.toInt()}", "TEXT", 100.0, 2)
        } else {
            drawText(sb, span / 2, elY + h + 300.0,
                "%%c${stDia.toInt()}@${spMid.toInt()}", "TEXT", 100.0, 2)
        }

        // ── CROSS-SECTION VIEW ──
        val csX = span + 6000.0
        val csY = 0.0
        drawText(sb, csX, csY - 800.0, "SECTION A-A", "TEXT", 200.0, 7)
        // Concrete section
        drawRect(sb, csX, csY, w, h, "CONCRETE")
        // Stirrup outline in section
        drawRect(sb, csX + cover, csY + cover, w - 2 * cover, h - 2 * cover, "STIRRUPS", 2)
        // Bottom bars (circles in section)
        for (i in 0 until nBot) {
            val bx = csX + cover + i * barSpacing
            val by = csY + cover + botDia / 2
            drawCircle(sb, bx, by, botDia / 2, "REBAR", 1)
        }
        // Top bars
        if (result.reinforcementTop.numBars > 0) {
            val nTop = result.reinforcementTop.numBars
            val topDia = result.reinforcementTop.diameter.toDouble()
            val topSpacing = if (nTop > 1) (w - 2 * cover) / (nTop - 1) else 0.0
            for (i in 0 until nTop) {
                val tx = csX + cover + i * topSpacing
                val ty = csY + h - cover - topDia / 2
                drawCircle(sb, tx, ty, topDia / 2, "REBAR", 1)
            }
        }
        // Section dimensions
        drawHorizontalDimension(sb, csX, csX + w, csY - 400.0, "b=${w.toInt()}", 5)
        drawVerticalDimension(sb, csY, csY + h, csX - 500.0, "h=${h.toInt()}", 5)

        // ── Elevation dimensions ──
        drawHorizontalDimension(sb, 0.0, span, elY - 500.0, "L = ${span.toInt()} mm (${spanM}m)", 5)
        drawVerticalDimension(sb, elY, elY + h, -600.0, "${h.toInt()}", 5)

        // ── DESIGN TABLE ──
        val tableX = csX + 6000.0
        val tableY = 3000.0
        drawTitleBlock(sb, tableX, tableY, "BEAM DESIGN DATA", listOf(
            "Max Moment Mu" to "%.1f kN.m".format(result.appliedMoment),
            "Max Shear Vu" to "%.1f kN".format(result.appliedShear),
            "Moment Capacity" to "%.1f kN.m".format(result.momentCapacity),
            "Shear Capacity" to "%.1f kN".format(result.shearCapacity),
            "Bottom Steel" to result.reinforcementBottom.barString,
            "Top Steel" to result.reinforcementTop.barString,
            "Steel Ratio" to "%.4f".format(result.steelRatio),
            "f'cu" to "%.0f MPa".format(fcu),
            "fy" to "%.0f MPa".format(fy),
            "Concrete Vol" to "%.3f m3".format(result.concreteVolume),
            "Steel Wt" to "%.1f kg".format(result.steelWeight),
            "Status" to if (result.isSafe) "SAFE" else "UNSAFE"
        ))

        sb.append("0\nENDSEC\n"); writeObjectsSection(sb); sb.append("0\nEOF\n")
        return sb.toString()
    }

    // ── 2. COLUMN ───────────────────────────────────────────────────
    fun generateColumnDxf(
        result: ColumnResult,
        heightM: Double = 3.0,
        fcu: Double = 25.0,
        fy: Double = 400.0
    ): String {
        val sb = StringBuilder()
        val w = result.width
        val d = result.depth
        val h = heightM * 1000.0
        val cover = 40.0

        writeHeader(sb)
        writeClassesSection(sb)
        writeLayerTable(sb, STANDARD_LAYERS)
        writeBlocksSection(sb)
        sb.append("0\nSECTION\n2\nENTITIES\n")

        // Title
        drawText(sb, 0.0, -1200.0, "COLUMN REINFORCEMENT DETAIL", "TEXT", 250.0, 7)
        drawText(sb, 0.0, -1600.0, "${result.columnType}  |  ${w.toInt()}x${d.toInt()} mm  |  H=${heightM}m", "TEXT", 140.0, 3)

        // ── ELEVATION VIEW ──
        // Slabs at top and bottom
        val slabThk = 250.0
        drawRect(sb, -1200.0, -slabThk, w + 2400.0, slabThk, "CONCRETE")
        drawRect(sb, -1200.0, h, w + 2400.0, slabThk, "CONCRETE")
        // Column outline
        drawRect(sb, 0.0, 0.0, w, h, "CONCRETE")
        // Center-line (dashed simulation)
        drawLine(sb, w / 2, -slabThk - 500.0, w / 2, h + slabThk + 500.0, "CENTER_LINE", 1)

        // Main vertical bars
        val nBars = result.reinforcement.numBars.coerceAtLeast(4)
        val barDia = result.reinforcement.diameter.toDouble()
        // Distribute bars on all 4 faces
        val barsPerFace = nBars / 4 + 1
        val faceBars = nBars - (barsPerFace - 1) * 4  // remaining bars
        val spFace = if (barsPerFace > 1) (w - 2 * cover) / (barsPerFace) else 0.0

        var barIdx = 0
        // Bottom face bars
        for (i in 0 until barsPerFace) {
            if (barIdx >= nBars) break
            val bx = cover + (i + 0.5) * spFace
            drawRebarLine(sb, bx, -slabThk, bx, h + slabThk, barDia, "REBAR")
            // Hook at top
            drawLine(sb, bx, h + slabThk, bx - 25.0, h + slabThk + 200.0, "REBAR", 1)
            barIdx++
        }
        // Top face bars
        for (i in 0 until barsPerFace) {
            if (barIdx >= nBars) break
            val bx = cover + (i + 0.5) * spFace
            val ty = d - cover
            drawRebarLine(sb, bx, -slabThk, bx, h + slabThk, barDia, "REBAR")
            drawLine(sb, bx, h + slabThk, bx - 25.0, h + slabThk + 200.0, "REBAR", 1)
            barIdx++
        }
        // Side face bars (in cross-section they appear, in elevation they overlap)
        val remaining = nBars - barIdx
        for (i in 0 until remaining) {
            val sx = cover + (i + 1) * spFace
            drawRebarLine(sb, sx, -slabThk, sx, h + slabThk, barDia, "REBAR")
            barIdx++
        }

        // Main bar label
        drawText(sb, w + 300.0, h / 2, result.reinforcement.barString, "TEXT", 130.0, 1)

        // Stirrups (ties)
        val stDia = result.stirrups.diameter.toDouble()
        val condLen = result.stirrups.condensationZoneLength
        val spSupport = result.stirrups.spacingAtSupport.let { if (it <= 0.0) result.stirrups.spacing else it }
        val spMid = result.stirrups.spacingAtMidspan.let { if (it <= 0.0) result.stirrups.spacing else it }
        val effectiveCondLen = if (condLen > 0.0) condLen else h * 0.15

        var sy = 0.0
        while (sy < effectiveCondLen) {
            drawStirrup(sb, 0.0, sy, w, min(d, h) * 0.3, cover, stDia) // simplified for elevation
            sy += spSupport
        }
        while (sy < h - effectiveCondLen) {
            drawStirrup(sb, 0.0, sy, w, min(d, h) * 0.3, cover, stDia)
            sy += spMid
        }
        while (sy < h) {
            drawStirrup(sb, 0.0, sy, w, min(d, h) * 0.3, cover, stDia)
            sy += spSupport
        }

        if (spSupport != spMid) {
            drawText(sb, w + 300.0, effectiveCondLen / 2,
                "%%c${stDia.toInt()}@${spSupport.toInt()}", "TEXT", 100.0, 2)
            drawText(sb, w + 300.0, h / 2,
                "%%c${stDia.toInt()}@${spMid.toInt()}", "TEXT", 100.0, 2)
        } else {
            drawText(sb, w + 300.0, h / 2,
                "%%c${stDia.toInt()}@${spMid.toInt()}", "TEXT", 100.0, 2)
        }

        // ── CROSS-SECTION ──
        val csX = w + 8000.0
        val csY = 0.0
        drawText(sb, csX, csY - 800.0, "SECTION B-B", "TEXT", 200.0, 7)
        drawRect(sb, csX, csY, w, d, "CONCRETE")
        drawRect(sb, csX + cover, csY + cover, w - 2 * cover, d - 2 * cover, "STIRRUPS", 2)
        // Bars in section
        var csIdx = 0
        for (i in 0 until barsPerFace) {
            if (csIdx >= nBars) break
            val bx = csX + cover + (i + 0.5) * spFace
            drawCircle(sb, bx, csY + cover + barDia / 2, barDia / 2, "REBAR", 1)
            csIdx++
        }
        for (i in 0 until barsPerFace) {
            if (csIdx >= nBars) break
            val bx = csX + cover + (i + 0.5) * spFace
            drawCircle(sb, bx, csY + d - cover - barDia / 2, barDia / 2, "REBAR", 1)
            csIdx++
        }
        for (i in 0 until (nBars - csIdx)) {
            val sx2 = csX + cover + (i + 1) * spFace
            drawCircle(sb, sx2, csY + d / 2, barDia / 2, "REBAR", 1)
            csIdx++
        }
        // Section dims
        drawHorizontalDimension(sb, csX, csX + w, csY - 400.0, "${w.toInt()}", 5)
        drawVerticalDimension(sb, csY, csY + d, csX - 500.0, "${d.toInt()}", 5)

        // ── Dimensions ──
        drawVerticalDimension(sb, 0.0, h, -600.0, "H=${h.toInt()}", 5)

        // ── Table ──
        val tblX = csX + 6000.0
        drawTitleBlock(sb, tblX, 3000.0, "COLUMN DESIGN DATA", listOf(
            "Load Pu" to "%.1f kN".format(result.appliedAxial),
            "Axial Capacity" to "%.1f kN".format(result.axialCapacity),
            "Main Steel" to result.reinforcement.barString,
            "Steel Ratio" to "%.4f".format(result.reinforcementRatio),
            "Slenderness" to "%.1f".format(result.slenderness),
            "Is Slender" to if (result.isSlender) "Yes" else "No",
            "f'cu" to "%.0f MPa".format(fcu),
            "fy" to "%.0f MPa".format(fy),
            "Concrete Vol" to "%.3f m3".format(result.concreteVolume),
            "Steel Wt" to "%.1f kg".format(result.steelWeight),
            "Status" to if (result.isSafe) "SAFE" else "UNSAFE"
        ))

        sb.append("0\nENDSEC\n"); writeObjectsSection(sb); sb.append("0\nEOF\n")
        return sb.toString()
    }

    // ── 3. FOOTING ──────────────────────────────────────────────────
    fun generateFootingDxf(
        result: FootingResult,
        colWidth: Double,
        colDepth: Double
    ): String {
        val sb = StringBuilder()
        val fl = result.length
        val fw = result.width
        val thk = result.thickness
        val cover = 70.0

        writeHeader(sb)
        writeClassesSection(sb)
        writeLayerTable(sb, STANDARD_LAYERS)
        writeBlocksSection(sb)
        sb.append("0\nSECTION\n2\nENTITIES\n")

        drawText(sb, 0.0, -1200.0, "FOOTING REINFORCEMENT DETAIL", "TEXT", 250.0, 7)
        drawText(sb, 0.0, -1600.0, "${result.type.displayName}  |  ${fl.toInt()}x${fw.toInt()} mm  |  t=${thk.toInt()} mm", "TEXT", 140.0, 3)

        // ── PLAN VIEW ──
        drawRect(sb, 0.0, 0.0, fl, fw, "CONCRETE")
        // Column outline in plan
        val cx = (fl - colDepth) / 2.0
        val cy = (fw - colWidth) / 2.0
        drawRect(sb, cx, cy, colDepth, colWidth, "CONCRETE", 4)
        drawText(sb, cx + colDepth / 2, cy + colWidth / 2, "COL", "TEXT", 120.0, 4)

        // Bottom rebar X-direction
        val nX = result.barsX
        val barDiaX = result.barDiameter.toDouble()
        val spX = if (nX > 1) (fw - 2 * cover) / (nX - 1) else 0.0
        for (i in 0 until nX) {
            val ry = cover + i * spX
            drawRebarLine(sb, cover, ry, fl - cover, ry, barDiaX, "REBAR")
        }
        drawText(sb, fl / 2, fw + 300.0, "${nX}%%c${result.barDiameter} (X-dir)", "TEXT", 120.0, 1)

        // Bottom rebar Y-direction
        val nY = result.barsY
        val spY = if (nY > 1) (fl - 2 * cover) / (nY - 1) else 0.0
        for (i in 0 until nY) {
            val rx = cover + i * spY
            drawRebarLine(sb, rx, cover, rx, fw - cover, barDiaX, "REBAR")
        }
        drawText(sb, fl + 300.0, fw / 2, "${nY}%%c${result.barDiameter} (Y-dir)", "TEXT", 120.0, 1)

        // Top bars if present
        if (result.topBarDiameter > 0) {
            val nTX = result.reinforcementTopX
            val nTY = result.reinforcementTopY
            for (i in 0 until nTX) {
                val ry = fw - cover - i * if (nTX > 1) (fw - 2 * cover) / (nTX - 1) else 0.0
                drawLine(sb, cover, ry, fl - cover, ry, "STIRRUPS", 2)
            }
            for (i in 0 until nTY) {
                val rx = fl - cover - i * if (nTY > 1) (fl - 2 * cover) / (nTY - 1) else 0.0
                drawLine(sb, rx, cover, rx, fw - cover, "STIRRUPS", 2)
            }
        }

        // Plan dimensions
        drawHorizontalDimension(sb, 0.0, fl, -500.0, "L = ${fl.toInt()} mm", 5)
        drawVerticalDimension(sb, 0.0, fw, fl + 800.0, "W = ${fw.toInt()} mm", 5)

        // ── SECTION VIEW ──
        val secY = fw + 5000.0
        drawText(sb, 0.0, secY - 800.0, "SECTION 1-1", "TEXT", 200.0, 7)
        // Footing base
        drawRect(sb, 0.0, secY, fl, thk, "CONCRETE")
        // Column pedestal on top
        drawRect(sb, cx, secY + thk, colDepth, 400.0, "CONCRETE", 4)
        // Bottom rebar in section
        drawRebarLine(sb, cover, secY + cover, fl - cover, secY + cover, barDiaX, "REBAR")
        // Column starter bars
        drawRebarLine(sb, cx + 50.0, secY + cover, cx + 50.0, secY + thk + 300.0, barDiaX, "REBAR")
        drawRebarLine(sb, cx + colDepth - 50.0, secY + cover, cx + colDepth - 50.0, secY + thk + 300.0, barDiaX, "REBAR")
        // Ground line
        drawLine(sb, -1000.0, secY, fl + 1000.0, secY, "CENTER_LINE", 1)
        drawText(sb, fl + 1200.0, secY + 100.0, "GL", "TEXT", 120.0, 1)
        // Section dims
        drawHorizontalDimension(sb, 0.0, fl, secY - 500.0, "${fl.toInt()}", 5)
        drawVerticalDimension(sb, secY, secY + thk, fl + 800.0, "t=${thk.toInt()}", 5)

        // ── Design Table ──
        drawTitleBlock(sb, fl + 6000.0, 3000.0, "FOOTING DESIGN DATA", listOf(
            "Type" to result.type.displayName,
            "Size L x W" to "${fl.toInt()} x ${fw.toInt()} mm",
            "Thickness" to "${thk.toInt()} mm",
            "Soil Pressure" to "%.1f kN/m2".format(result.soilPressure),
            "Allowable Pressure" to "%.1f kN/m2".format(result.allowablePressure),
            "Bottom Steel X" to "${result.barsX}%%c${result.barDiameter}",
            "Bottom Steel Y" to "${result.barsY}%%c${result.barDiameter}",
            "Concrete Vol" to "%.3f m3".format(result.concreteVolume),
            "Steel Wt" to "%.1f kg".format(result.steelWeight),
            "Status" to if (result.isSafe) "SAFE" else "UNSAFE"
        ))

        sb.append("0\nENDSEC\n"); writeObjectsSection(sb); sb.append("0\nEOF\n")
        return sb.toString()
    }

    // ── 4. SLAB ─────────────────────────────────────────────────────
    fun generateSlabDxf(
        result: SlabResult,
        lxM: Double,
        lyM: Double
    ): String {
        val sb = StringBuilder()
        val lx = lxM * 1000.0
        val ly = lyM * 1000.0
        val ts = result.thickness
        val cover = 20.0

        writeHeader(sb)
        writeClassesSection(sb)
        writeLayerTable(sb, STANDARD_LAYERS)
        writeBlocksSection(sb)
        sb.append("0\nSECTION\n2\nENTITIES\n")

        drawText(sb, 0.0, -1200.0, "SLAB REINFORCEMENT DETAIL", "TEXT", 250.0, 7)
        drawText(sb, 0.0, -1600.0, "${result.type.displayName}  |  ${lxM}x${lyM}m  |  t=${ts.toInt()} mm", "TEXT", 140.0, 3)

        // ── PLAN VIEW ──
        drawRect(sb, 0.0, 0.0, lx, ly, "CONCRETE")
        // Support lines (thick lines at edges to indicate supports)
        drawLine(sb, 0.0, 0.0, lx, 0.0, "CONCRETE", 4) // bottom support
        drawLine(sb, 0.0, ly, lx, ly, "CONCRETE", 4) // top support

        // Main bottom reinforcement (short span - X direction)
        val mainSpacing = result.reinforcementMain.spacing
        val mainDia = result.reinforcementMain.diameter.toDouble()
        if (mainSpacing > 0) {
            var ry = mainSpacing / 2.0
            while (ry < ly) {
                drawRebarLine(sb, 50.0, ry, lx - 50.0, ry, mainDia, "REBAR")
                ry += mainSpacing * 3 // Show every 3rd bar for clarity
            }
        } else {
            // Individual bars
            val nMain = result.reinforcementMain.numBars
            if (nMain > 0) {
                val sp = if (nMain > 1) (ly - 2 * cover) / (nMain - 1) else 0.0
                for (i in 0 until nMain) {
                    val ry = cover + i * sp
                    drawRebarLine(sb, 50.0, ry, lx - 50.0, ry, mainDia, "REBAR")
                }
            }
        }
        drawText(sb, lx / 2, ly + 300.0, result.reinforcementMain.barString + " (bottom main)", "TEXT", 120.0, 1)

        // Secondary bottom reinforcement (long span - Y direction)
        val secSpacing = result.reinforcementSecondary.spacing
        val secDia = result.reinforcementSecondary.diameter.toDouble()
        if (secSpacing > 0) {
            var rx = secSpacing / 2.0
            while (rx < lx) {
                drawRebarLine(sb, rx, 50.0, rx, ly - 50.0, secDia, "REBAR")
                rx += secSpacing * 3
            }
        } else {
            val nSec = result.reinforcementSecondary.numBars
            if (nSec > 0) {
                val sp = if (nSec > 1) (lx - 2 * cover) / (nSec - 1) else 0.0
                for (i in 0 until nSec) {
                    val rx = cover + i * sp
                    drawRebarLine(sb, rx, 50.0, rx, ly - 50.0, secDia, "REBAR")
                }
            }
        }
        drawText(sb, lx + 300.0, ly / 2, result.reinforcementSecondary.barString + " (bottom sec)", "TEXT", 120.0, 1)

        // Plan dimensions
        drawHorizontalDimension(sb, 0.0, lx, -500.0, "Lx = ${lx.toInt()} mm (${lxM}m)", 5)
        drawVerticalDimension(sb, 0.0, ly, lx + 800.0, "Ly = ${ly.toInt()} mm (${lyM}m)", 5)

        // ── SECTION VIEW ──
        val secY = ly + 5000.0
        drawText(sb, 0.0, secY - 800.0, "SECTION A-A", "TEXT", 200.0, 7)
        drawRect(sb, 0.0, secY, lx, ts, "CONCRETE")
        // Bottom bars in section
        drawRebarLine(sb, cover, secY + cover, lx - cover, secY + cover, mainDia, "REBAR")
        // Support symbols
        drawSupportSymbol(sb, 0.0, secY, 200.0)
        drawSupportSymbol(sb, lx, secY, 200.0)
        // Section dims
        drawHorizontalDimension(sb, 0.0, lx, secY - 400.0, "${lxM}m", 5)
        drawVerticalDimension(sb, secY, secY + ts, lx + 600.0, "t=${ts.toInt()}", 5)

        // ── Table ──
        drawTitleBlock(sb, lx + 6000.0, 3000.0, "SLAB DESIGN DATA", listOf(
            "Type" to result.type.displayName,
            "Span Lx x Ly" to "${lxM} x ${lyM} m",
            "Thickness" to "${ts.toInt()} mm",
            "Moment Mx" to "%.1f kN.m/m".format(result.momentX),
            "Moment My" to "%.1f kN.m/m".format(result.momentY),
            "Total Load" to "%.1f kN/m2".format(result.totalLoad),
            "Main Steel" to result.reinforcementMain.barString,
            "Sec Steel" to result.reinforcementSecondary.barString,
            "Concrete Vol" to "%.3f m3".format(result.concreteVolume),
            "Steel Wt" to "%.1f kg".format(result.steelWeight),
            "Status" to if (result.isSafe) "SAFE" else "UNSAFE"
        ))

        sb.append("0\nENDSEC\n"); writeObjectsSection(sb); sb.append("0\nEOF\n")
        return sb.toString()
    }

    // ── 5. STAIR ────────────────────────────────────────────────────
    fun generateStairDxf(
        result: StairResult
    ): String {
        val sb = StringBuilder()
        val span = result.span * 1000.0
        val riser = result.riser
        val tread = result.tread
        val waist = result.thickness
        val numSteps = (span / tread).toInt().coerceIn(1, 25)

        writeHeader(sb)
        writeClassesSection(sb)
        writeLayerTable(sb, STANDARD_LAYERS)
        writeBlocksSection(sb)
        sb.append("0\nSECTION\n2\nENTITIES\n")

        drawText(sb, 0.0, -1200.0, "STAIRCASE REINFORCEMENT DETAIL", "TEXT", 250.0, 7)
        drawText(sb, 0.0, -1600.0, "${result.type.displayName}  |  Span: ${result.span}m  |  R=${riser.toInt()} T=${tread.toInt()}", "TEXT", 140.0, 3)

        // ── SIDE ELEVATION VIEW ──
        var curX = 0.0
        var curY = 0.0
        for (idx in 0 until numSteps) {
            // Riser
            drawLine(sb, curX, curY, curX, curY + riser, "CONCRETE")
            // Tread
            drawLine(sb, curX, curY + riser, curX + tread, curY + riser, "CONCRETE")
            curX += tread
            curY += riser
        }
        // Closing line at bottom and top
        drawLine(sb, 0.0, 0.0, 0.0, -waist, "CONCRETE")
        drawLine(sb, curX, curY, curX, curY - waist, "CONCRETE")
        // Waist slab (inclined soffit)
        drawLine(sb, 0.0, -waist, curX, curY - waist, "CONCRETE")
        // Top landing line
        drawLine(sb, curX, curY, curX, curY - waist, "CONCRETE")

        // Main reinforcement (along the inclination)
        val mainDia = result.reinforcement.diameter.toDouble()
        val distDia = result.distributionReinforcement.diameter.toDouble()
        // Draw a few representative bars along the waist
        for (i in 0 until 3) {
            val offset = waist - 30.0 - i * 20.0
            val totalRun = span
            val totalRise = numSteps * riser
            val inclLen = sqrt(totalRun * totalRun + totalRise * totalRise)
            // Simplified: draw as straight lines along incline
            drawLine(sb, -200.0, -offset, curX + 200.0, curY - offset, "REBAR", 1)
        }
        drawText(sb, curX / 2, curY + 500.0, result.reinforcement.barString, "TEXT", 130.0, 1)
        drawText(sb, curX / 2, curY + 900.0, "Dist: ${result.distributionReinforcement.barString}", "TEXT", 110.0, 1)

        // Support symbols
        drawSupportSymbol(sb, 0.0, 0.0, 200.0)
        drawSupportSymbol(sb, curX, curY, 200.0)

        // ── SECTION VIEW ──
        val secX = curX + 5000.0
        val secY = 0.0
        drawText(sb, secX, secY - 800.0, "SECTION A-A", "TEXT", 200.0, 7)
        // Step profile in section
        drawLine(sb, secX, secY, secX, secY + riser, "CONCRETE")
        drawLine(sb, secX, secY + riser, secX + tread, secY + riser, "CONCRETE")
        drawLine(sb, secX + tread, secY + riser, secX + tread, secY, "CONCRETE")
        drawLine(sb, secX + tread, secY, secX, secY, "CONCRETE")
        // Waist below
        drawLine(sb, secX, secY, secX + tread, secY, "CONCRETE")
        drawLine(sb, secX, secY, secX, secY - waist, "CONCRETE")
        drawLine(sb, secX, secY - waist, secX + tread, secY - waist, "CONCRETE")
        drawLine(sb, secX + tread, secY - waist, secX + tread, secY, "CONCRETE")
        // Rebar in section
        drawCircle(sb, secX + tread / 2, secY - waist + 30.0, mainDia / 2, "REBAR", 1)
        // Section dims
        drawHorizontalDimension(sb, secX, secX + tread, secY - waist - 500.0, "T=${tread.toInt()}", 5)
        drawVerticalDimension(sb, secY, secY + riser, secX - 500.0, "R=${riser.toInt()}", 5)
        drawVerticalDimension(sb, secY, secY - waist, secX + tread + 500.0, "t=${waist.toInt()}", 5)

        // ── Table ──
        drawTitleBlock(sb, secX + 6000.0, 3000.0, "STAIR DESIGN DATA", listOf(
            "Type" to result.type.displayName,
            "Span" to "${result.span} m",
            "Riser" to "${riser.toInt()} mm",
            "Tread" to "${tread.toInt()} mm",
            "No. of Steps" to "$numSteps",
            "Waist Thickness" to "${waist.toInt()} mm",
            "Main Steel" to result.reinforcement.barString,
            "Dist Steel" to result.distributionReinforcement.barString,
            "f'cu" to "%.0f MPa".format(result.fcu),
            "fy" to "%.0f MPa".format(result.fy),
            "Concrete Vol" to "%.3f m3".format(result.concreteVolume),
            "Steel Wt" to "%.1f kg".format(result.steelWeight),
            "Status" to if (result.isSafe) "SAFE" else "UNSAFE"
        ))

        sb.append("0\nENDSEC\n"); writeObjectsSection(sb); sb.append("0\nEOF\n")
        return sb.toString()
    }

    // ── 6. WATER TANK ───────────────────────────────────────────────
    fun generateTankDxf(
        result: TankResult
    ): String {
        val sb = StringBuilder()
        val l = result.length * 1000.0
        val h = result.height * 1000.0
        val tw = result.wallThickness
        val tb = result.baseThickness

        writeHeader(sb)
        writeClassesSection(sb)
        writeLayerTable(sb, STANDARD_LAYERS + LayerDef("WATER", 5))
        writeBlocksSection(sb)
        sb.append("0\nSECTION\n2\nENTITIES\n")

        drawText(sb, 0.0, -1200.0, "WATER TANK REINFORCEMENT DETAIL", "TEXT", 250.0, 7)
        drawText(sb, 0.0, -1600.0, "${result.type.displayName}  |  ${result.length}x${result.width}x${result.height}m", "TEXT", 140.0, 3)

        // ── LONGITUDINAL SECTION ──
        val secY = 0.0
        // Base slab
        drawRect(sb, 0.0, secY, l + 2 * tw, tb, "CONCRETE")
        // Left wall
        drawRect(sb, 0.0, secY + tb, tw, h, "CONCRETE")
        // Right wall
        drawRect(sb, l + tw, secY + tb, tw, h, "CONCRETE")
        // Water level indication
        val waterTop = secY + tb + h * 0.9
        drawLine(sb, tw, waterTop, l + tw, waterTop, "WATER", 5)
        // Water hatching lines
        var whY = waterTop + 100.0
        while (whY < secY + tb + h) {
            drawLine(sb, tw + 50.0, whY, l + tw - 50.0, whY, "WATER", 5)
            whY += 300.0
        }
        drawText(sb, l / 2 + tw, waterTop + (h * 0.1) / 2, "WATER", "TEXT", 200.0, 5)

        // Wall reinforcement
        val wallDia = result.wallReinforcement.diameter.toDouble()
        // Horizontal rebar in left wall (shown as dots at wall edges)
        val wallSpacing = result.wallReinforcement.spacing
        if (wallSpacing > 0) {
            var wy = secY + tb + 50.0
            while (wy < secY + tb + h - 50.0) {
                drawCircle(sb, tw / 2, wy, wallDia / 2, "REBAR", 1)
                drawCircle(sb, l + tw + tw / 2, wy, wallDia / 2, "REBAR", 1)
                wy += wallSpacing * 3
            }
        }
        drawText(sb, -tw - 300.0, secY + tb + h / 2,
            result.wallReinforcement.barString, "TEXT", 110.0, 1)

        // Base reinforcement
        val baseDia = result.baseReinforcement.diameter.toDouble()
        drawRebarLine(sb, tw, secY + 50.0, l + tw, secY + 50.0, baseDia, "REBAR")
        drawText(sb, l / 2 + tw, secY - 300.0,
            "Base: ${result.baseReinforcement.barString}", "TEXT", 110.0, 1)

        // Ground line
        drawLine(sb, -1500.0, secY, l + 2 * tw + 1500.0, secY, "CENTER_LINE", 1)
        drawText(sb, l + 2 * tw + 1800.0, secY + 100.0, "GL", "TEXT", 120.0, 1)

        // Dimensions
        drawHorizontalDimension(sb, 0.0, l + 2 * tw, secY - 600.0, "${result.length + 2 * result.wallThickness / 1000.0}m", 5)
        drawVerticalDimension(sb, secY, secY + tb + h, l + 2 * tw + 800.0, "H=${result.height}m", 5)
        drawText(sb, -tw / 2 - 100.0, secY + tb + h + 300.0, "tw=${tw.toInt()}", "TEXT", 100.0, 3)

        // ── Table ──
        drawTitleBlock(sb, l + 2 * tw + 6000.0, 3000.0, "TANK DESIGN DATA", listOf(
            "Type" to result.type.displayName,
            "Length x Width" to "${result.length} x ${result.width} m",
            "Height" to "${result.height} m",
            "Wall Thickness" to "${tw.toInt()} mm",
            "Base Thickness" to "${tb.toInt()} mm",
            "Capacity" to "%.1f m3".format(result.capacityM3),
            "Water Pressure" to "%.1f kPa".format(result.waterPressure),
            "Wall Reinforcement" to result.wallReinforcement.barString,
            "Base Reinforcement" to result.baseReinforcement.barString,
            "Concrete Vol" to "%.3f m3".format(result.concreteVolume),
            "Steel Wt" to "%.1f kg".format(result.steelWeight),
            "Status" to if (result.isSafe) "SAFE" else "UNSAFE"
        ))

        sb.append("0\nENDSEC\n"); writeObjectsSection(sb); sb.append("0\nEOF\n")
        return sb.toString()
    }

    // ── 7. RETAINING WALL ────────────────────────────────────────────
    fun generateRetainingWallDxf(
        result: RetainingWallResult
    ): String {
        val sb = StringBuilder()
        val h = result.height * 1000.0
        val stemTw = result.stemThickness
        val baseW = result.baseWidth
        val cover = 50.0

        writeHeader(sb)
        writeClassesSection(sb)
        writeLayerTable(sb, STANDARD_LAYERS)
        writeBlocksSection(sb)
        sb.append("0\nSECTION\n2\nENTITIES\n")

        drawText(sb, 0.0, -1200.0, "RETAINING WALL REINFORCEMENT DETAIL", "TEXT", 250.0, 7)
        drawText(sb, 0.0, -1600.0, "H=${result.height}m  |  Base=${baseW.toInt()}mm  |  Stem=${stemTw.toInt()}mm", "TEXT", 140.0, 3)

        // ── CROSS SECTION ──
        // Base slab
        drawRect(sb, 0.0, 0.0, baseW, stemTw, "CONCRETE")
        // Stem wall (vertical, starting from base top)
        val stemX = baseW / 3.0
        drawLine(sb, stemX, stemTw, stemX, stemTw + h, "CONCRETE")
        drawLine(sb, stemX + stemTw, stemTw, stemX + stemTw, stemTw + h, "CONCRETE")
        drawLine(sb, stemX, stemTw + h, stemX + stemTw, stemTw + h, "CONCRETE")

        // Ground line (backfill side)
        drawLine(sb, -2000.0, stemTw + h, stemX, stemTw + h, "CENTER_LINE", 1)
        drawLine(sb, stemX, stemTw + h, stemX, 0.0, "CENTER_LINE", 1)

        // Stem main reinforcement (vertical bars on tension side)
        val stemDia = result.stemReinforcement.diameter.toDouble()
        val nStemBars = result.stemReinforcement.numBars.coerceAtLeast(1)
        for (i in 0 until min(nStemBars, 4)) {
            val bx = stemX + stemTw - cover
            drawRebarLine(sb, bx, stemTw, bx, stemTw + h - cover, stemDia, "REBAR")
        }
        drawText(sb, stemX + stemTw + 200.0, stemTw + h / 2,
            result.stemReinforcement.barString, "TEXT", 130.0, 1)

        // Base reinforcement
        val baseDia = result.baseReinforcement.diameter.toDouble()
        drawRebarLine(sb, cover, cover, baseW - cover, cover, baseDia, "REBAR")
        drawText(sb, baseW / 2, stemTw - 400.0,
            "Base: ${result.baseReinforcement.barString}", "TEXT", 110.0, 1)

        // Active earth pressure diagram (triangle on backfill side)
        val pa = result.pa
        if (pa > 0) {
            // Arrow from top
            drawLine(sb, stemX - 200.0, stemTw + h, stemX - 200.0, stemTw, "LOADS", 6)
            // Pressure arrows
            val nArrows = 6
            for (i in 0 until nArrows) {
                val frac = i.toDouble() / (nArrows - 1)
                val ay = stemTw + h - frac * h
                val arrowLen = 200.0 + frac * 800.0
                drawLine(sb, stemX - 200.0 - arrowLen, ay, stemX - 200.0, ay, "LOADS", 6)
                // Arrowhead
                drawLine(sb, stemX - 200.0, ay, stemX - 200.0 - 100.0, ay + 60.0, "LOADS", 6)
                drawLine(sb, stemX - 200.0, ay, stemX - 200.0 - 100.0, ay - 60.0, "LOADS", 6)
            }
            drawText(sb, stemX - 1800.0, stemTw + h + 400.0, "Pa=%.1f kN/m".format(pa), "TEXT", 110.0, 6)
            drawText(sb, stemX - 1800.0, stemTw + h + 700.0, "Ka=%.3f".format(result.ka), "TEXT", 110.0, 6)
        }

        // Toe and heel dimensions
        val toeLen = baseW - stemX - stemTw
        drawHorizontalDimension(sb, stemX + stemTw, baseW, -500.0, "Toe=${toeLen.toInt()}", 5)
        drawHorizontalDimension(sb, 0.0, stemX, -500.0, "Heel=${stemX.toInt()}", 5)
        drawVerticalDimension(sb, 0.0, stemTw + h, baseW + 600.0, "H=${h.toInt()}", 5)

        // Ground line
        drawLine(sb, -2500.0, 0.0, baseW + 1500.0, 0.0, "CENTER_LINE", 1)
        drawText(sb, baseW + 1800.0, 100.0, "GL", "TEXT", 120.0, 1)

        // ── Table ──
        drawTitleBlock(sb, baseW + 6000.0, 3000.0, "RETAINING WALL DESIGN DATA", listOf(
            "Height" to "${result.height} m",
            "Base Width" to "${baseW.toInt()} mm",
            "Stem Thickness" to "${stemTw.toInt()} mm",
            "Ka" to "%.3f".format(result.ka),
            "Active Pressure Pa" to "%.1f kN/m".format(result.pa),
            "FS Overturning" to "%.2f".format(result.factorOfSafetyOverturning),
            "FS Sliding" to "%.2f".format(result.factorOfSafetySliding),
            "Max Bearing" to "%.1f kN/m2".format(result.maxBearingPressure),
            "Min Bearing" to "%.1f kN/m2".format(result.minBearingPressure),
            "Stem Steel" to result.stemReinforcement.barString,
            "Base Steel" to result.baseReinforcement.barString,
            "f'cu" to "%.0f MPa".format(result.fcu),
            "fy" to "%.0f MPa".format(result.fy),
            "Concrete Vol" to "%.3f m3".format(result.concreteVolume),
            "Steel Wt" to "%.1f kg".format(result.steelWeight),
            "Status" to if (result.isSafe) "SAFE" else "UNSAFE"
        ))

        sb.append("0\nENDSEC\n"); writeObjectsSection(sb); sb.append("0\nEOF\n")
        return sb.toString()
    }

    // ── 8. STEEL SECTION ────────────────────────────────────────────
    /**
     * Generates a steel member cross-section DXF drawing with section
     * properties and design check results.
     */
    fun generateSteelSectionDxf(
        result: SteelMemberResult,
        memberLength: Double,
        inputs: SteelInputs
    ): String {
        val sb = StringBuilder()
        val sec = result.sectionType
        val h = sec.depth      // mm
        val b = sec.width      // mm
        val tw = sec.webThickness
        val tf = sec.flangeThickness
        val lengthMm = memberLength  // already in mm from SteelInputs
        val lengthM = memberLength / 1000.0  // for display in metres

        val steelLayers = listOf(
            LayerDef("STEEL", 7),
            LayerDef("CENTER_LINE", 1),
            LayerDef("DIMENSIONS", 5),
            LayerDef("TEXT", 3),
            LayerDef("LOADS", 6)
        )

        writeHeader(sb)
        writeClassesSection(sb)
        writeLayerTable(sb, steelLayers)
        writeBlocksSection(sb)
        sb.append("0\nSECTION\n2\nENTITIES\n")

        drawText(sb, 0.0, -1200.0, "STEEL MEMBER DESIGN", "TEXT", 250.0, 7)
        drawText(sb, 0.0, -1600.0, "${sec.displayName}  |  L=${lengthM}m  |  ${result.memberType}", "TEXT", 140.0, 3)

        // ── CROSS-SECTION VIEW ──
        val csX = 2000.0
        val csY = 0.0
        drawText(sb, csX, csY - 800.0, "SECTION A-A", "TEXT", 200.0, 7)

        // I-section (or rectangular for hollow sections)
        when {
            sec is SteelSectionType.ISection ||
            sec.sectionName.startsWith("W") || sec.sectionName.startsWith("HEA") ||
            sec.sectionName.startsWith("HEB") ||
            sec.sectionName.startsWith("UB") || sec.sectionName.startsWith("UC") -> {
                // I-beam profile
                drawRect(sb, csX, csY, b, tf, "STEEL")                                  // Bottom flange
                drawRect(sb, csX + (b - tw) / 2, csY + tf, tw, h - 2 * tf, "STEEL")     // Web
                drawRect(sb, csX, csY + h - tf, b, tf, "STEEL")                          // Top flange
            }
            sec.sectionName.startsWith("CHS") || sec.sectionName.startsWith("PIPE") -> {
                // Circular hollow section
                drawCircle(sb, csX + b / 2, csY + h / 2, h / 2, "STEEL", 7)
                drawCircle(sb, csX + b / 2, csY + h / 2, h / 2 - tw, "STEEL", 7)
            }
            sec.sectionName.startsWith("RHS") || sec.sectionName.startsWith("SHS") ||
            sec.sectionName.startsWith("BOX") -> {
                // Rectangular hollow section
                drawRect(sb, csX, csY, b, h, "STEEL")
                drawRect(sb, csX + tw, csY + tw, b - 2 * tw, h - 2 * tw, "STEEL")
            }
            else -> {
                // Generic: draw as I-beam
                drawRect(sb, csX, csY, b, tf, "STEEL")
                drawRect(sb, csX + (b - tw) / 2, csY + tf, tw, h - 2 * tf, "STEEL")
                drawRect(sb, csX, csY + h - tf, b, tf, "STEEL")
            }
        }

        // Section dimensions
        drawHorizontalDimension(sb, csX, csX + b, csY - 500.0, "b=${b.toInt()}", 5)
        drawVerticalDimension(sb, csY, csY + h, csX - 600.0, "h=${h.toInt()}", 5)

        // Center-lines
        drawLine(sb, csX + b / 2, csY - 800.0, csX + b / 2, csY + h + 800.0, "CENTER_LINE", 1)
        drawLine(sb, csX - 800.0, csY + h / 2, csX + b + 800.0, csY + h / 2, "CENTER_LINE", 1)

        // ── ELEVATION VIEW ──
        val elY = h + 5000.0
        drawText(sb, 0.0, elY - 800.0, "ELEVATION", "TEXT", 200.0, 7)
        // Member outline (simplified as rectangle)
        val memberH = min(h, 400.0) // Cap display height for long members
        drawRect(sb, 0.0, elY, lengthMm, memberH, "STEEL")
        // Support symbols
        drawSupportSymbol(sb, 0.0, elY + memberH, 200.0)
        drawSupportSymbol(sb, lengthMm, elY + memberH, 200.0)
        // Load arrows if applied loads exist
        if (inputs.moment > 0 || inputs.shear > 0) {
            val nArrows = 5
            for (i in 0 until nArrows) {
                val ax = lengthMm * (i + 1) / (nArrows + 1)
                drawLine(sb, ax, elY - 400.0, ax, elY, "LOADS", 6)
                drawLine(sb, ax, elY, ax - 80.0, elY + 80.0, "LOADS", 6)
                drawLine(sb, ax, elY, ax + 80.0, elY + 80.0, "LOADS", 6)
            }
        }
        // Length dimension
        drawHorizontalDimension(sb, 0.0, lengthMm, elY - 800.0, "L = ${lengthMm.toInt()} mm (${lengthM}m)", 5)

        // ── DESIGN TABLE ──
        val tableX = csX + b + 8000.0
        val tableY = 5000.0
        val tableData = mutableListOf(
            "Section" to sec.displayName,
            "Member Type" to result.memberType.name,
            "Length" to "${lengthM} m",
            "Axial Capacity" to "%.1f kN".format(result.axialCapacity),
            "Moment Capacity" to "%.1f kN.m".format(result.flexuralCapacity),
            "Shear Capacity" to "%.1f kN".format(result.shearCapacity),
            "Weight" to "%.2f kg/m".format(result.weight),
            "Utilization Ratio" to "%.2f".format(result.utilizationRatio),
            "Status" to if (result.isSafe) "SAFE" else "UNSAFE"
        )
        result.bucklingCheck?.let {
            tableData.add("Buckling" to if (it.isSafe) "PASS (\u03bb=%.1f)".format(it.slendernessRatio) else "FAIL (\u03bb=%.1f)".format(it.slendernessRatio))
        }
        result.deflectionCheck?.let {
            tableData.add("Deflection" to if (it.isSafe) "PASS (%.1f mm)".format(it.calculatedDeflection) else "FAIL (%.1f mm)".format(it.calculatedDeflection))
        }
        drawTitleBlock(sb, tableX, tableY, "STEEL DESIGN DATA", tableData)

        sb.append("0\nENDSEC\n"); writeObjectsSection(sb); sb.append("0\nEOF\n")
        return sb.toString()
    }

    // ── 9. STEEL WAREHOUSE ────────────────────────────────────────────
    /**
     * Generates a steel warehouse portal frame DXF drawing.
     */
    fun generateSteelWarehouseDxf(
        inputs: SteelWarehouseInputs,
        result: SteelWarehouseAnalysisResult
    ): String {
        val sb = StringBuilder()
        val span = inputs.span * 1000.0
        val eh = inputs.eaveHeight * 1000.0
        val rh = inputs.ridgeHeight * 1000.0
        val midX = span / 2.0

        val warehouseLayers = listOf(
            LayerDef("FRAME", 7),
            LayerDef("BRACING", 6),
            LayerDef("PURLIN", 4),
            LayerDef("DIMENSIONS", 5),
            LayerDef("TEXT", 3),
            LayerDef("LOADS", 2)
        )

        writeHeader(sb)
        writeClassesSection(sb)
        writeLayerTable(sb, warehouseLayers)
        writeBlocksSection(sb)
        sb.append("0\nSECTION\n2\nENTITIES\n")

        drawText(sb, 0.0, -1200.0, "STEEL WAREHOUSE DESIGN", "TEXT", 250.0, 7)
        drawText(sb, 0.0, -1600.0, "Span: ${inputs.span}m  |  Eave: ${inputs.eaveHeight}m  |  Ridge: ${inputs.ridgeHeight}m", "TEXT", 140.0, 3)

        // ── FRONT ELEVATION ──
        val elY = 0.0
        // Left column
        drawRect(sb, 0.0, elY, 300.0, eh, "FRAME")
        // Right column
        drawRect(sb, span - 300.0, elY, 300.0, eh, "FRAME")
        // Left rafter
        drawLine(sb, 300.0, elY + eh, midX, elY + rh, "FRAME", 7)
        // Right rafter
        drawLine(sb, span - 300.0, elY + eh, midX, elY + rh, "FRAME", 7)
        // Ridge beam
        drawLine(sb, midX - 200.0, elY + rh, midX + 200.0, elY + rh, "FRAME", 7)

        // Haunch triangles at eave connections
        val haunchW = 600.0
        val haunchH = 400.0
        drawLine(sb, 300.0, elY + eh, 300.0 + haunchW, elY + eh + haunchH, "FRAME", 7)
        drawLine(sb, 300.0 + haunchW, elY + eh, 300.0 + haunchW, elY + eh + haunchH, "FRAME", 7)
        drawLine(sb, span - 300.0, elY + eh, span - 300.0 - haunchW, elY + eh + haunchH, "FRAME", 7)
        drawLine(sb, span - 300.0 - haunchW, elY + eh, span - 300.0 - haunchW, elY + eh + haunchH, "FRAME", 7)

        // Gable posts at ridge
        drawRect(sb, midX - 75.0, elY + rh - 800.0, 150.0, 800.0, "FRAME")

        // Purlins (simplified — dashed lines along rafters)
        if (inputs.usePurlins) {
            val purlinSp = inputs.purlinSpacing * 1000.0
            val rafterLen = sqrt((midX - 300.0).pow(2) + (rh - eh).pow(2))
            val numPurlins = (rafterLen / purlinSp).toInt().coerceIn(1, 12)
            for (i in 1..numPurlins) {
                val frac = i.toDouble() / (numPurlins + 1)
                val px = 300.0 + frac * (midX - 300.0)
                val py = eh + frac * (rh - eh)
                // Left side purlin
                drawLine(sb, px, py, px, py + 300.0, "PURLIN", 4)
                // Right side purlin (mirrored)
                drawLine(sb, span - px, py, span - px, py + 300.0, "PURLIN", 4)
            }
        }

        // X-bracing in side wall
        drawLine(sb, 150.0, elY + 500.0, 150.0 + (eh - 1000.0), elY + eh - 500.0, "BRACING", 6)
        drawLine(sb, span - 150.0, elY + 500.0, span - 150.0 - (eh - 1000.0), elY + eh - 500.0, "BRACING", 6)

        // Wind load arrows
        val nWind = 6
        for (i in 0 until nWind) {
            val frac = (i + 1).toDouble() / (nWind + 1)
            val wy = elY + frac * eh
            val wLen = 300.0 + frac * 500.0
            drawLine(sb, -wLen, wy, 0.0, wy, "LOADS", 2)
            drawLine(sb, 0.0, wy, -80.0, wy + 60.0, "LOADS", 2)
            drawLine(sb, 0.0, wy, -80.0, wy - 60.0, "LOADS", 2)
        }
        drawText(sb, -2000.0, elY + eh / 2, "Wind", "TEXT", 130.0, 2)

        // Ground line
        drawLine(sb, -1500.0, elY, span + 1500.0, elY, "DIMENSIONS", 5)
        drawText(sb, span + 1800.0, elY + 100.0, "GL", "TEXT", 120.0, 5)

        // Dimensions
        drawHorizontalDimension(sb, 0.0, span, elY - 600.0, "Span = ${inputs.span} m", 5)
        drawVerticalDimension(sb, elY, elY + eh, -800.0, "Eave = ${inputs.eaveHeight}m", 5)
        drawVerticalDimension(sb, elY, elY + rh, midX + 800.0, "Ridge = ${inputs.ridgeHeight}m", 5)

        // Column/rafter section labels
        drawText(sb, 150.0, elY + eh / 2, result.mainFrame.columnSection.sectionName, "TEXT", 120.0, 7)
        val labelFrac = 0.4
        val labelX = 300.0 + labelFrac * (midX - 300.0)
        val labelY = eh + labelFrac * (rh - eh)
        drawText(sb, labelX + 200.0, labelY, result.mainFrame.rafterSection.sectionName, "TEXT", 120.0, 7)

        // ── SIDE ELEVATION ──
        val sideY = elY + rh + 6000.0
        drawText(sb, 0.0, sideY - 800.0, "SIDE ELEVATION", "TEXT", 200.0, 7)
        val sideLen = inputs.length * 1000.0
        drawRect(sb, 0.0, sideY, 300.0, eh, "FRAME")
        drawRect(sb, sideLen - 300.0, sideY, 300.0, eh, "FRAME")
        drawLine(sb, 0.0, sideY + eh, midX, sideY + rh, "FRAME", 7)
        drawLine(sb, sideLen, sideY + eh, midX, sideY + rh, "FRAME", 7)
        // Side bracing
        drawLine(sb, 300.0, sideY + 500.0, sideLen - 300.0, sideY + eh - 500.0, "BRACING", 6)
        drawHorizontalDimension(sb, 0.0, sideLen, sideY - 600.0, "Length = ${inputs.length} m", 5)

        // ── DESIGN TABLE ──
        val tableX = sideLen + 6000.0
        val tableY = 5000.0
        drawTitleBlock(sb, tableX, tableY, "WAREHOUSE DESIGN DATA", listOf(
            "Span" to "${inputs.span} m",
            "Length" to "${inputs.length} m",
            "Eave Height" to "${inputs.eaveHeight} m",
            "Ridge Height" to "${inputs.ridgeHeight} m",
            "Bay Spacing" to "${inputs.baySpacing} m",
            "Column Section" to result.mainFrame.columnSection.sectionName,
            "Rafter Section" to result.mainFrame.rafterSection.sectionName,
            "Total Weight" to "%.2f Tons".format(result.totalWeight),
            "Cladding Area" to "%.1f m2".format(result.totalCladdingArea),
            "Weight/m2" to "%.1f kg/m2".format(result.weightPerM2),
            "Safety Status" to if (result.safetyStatus) "SAFE" else "CHECK REQUIRED"
        ))

        sb.append("0\nENDSEC\n"); writeObjectsSection(sb); sb.append("0\nEOF\n")
        return sb.toString()
    }

    // ── 10. FRAME ANALYSIS ────────────────────────────────────────────
    /**
     * Generates a frame analysis DXF with geometry, BMD labels, and reactions.
     */
    fun generateFrameAnalysisDxf(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        result: FrameAnalysisResult
    ): String {
        val sb = StringBuilder()
        val scale = 500.0

        val frameLayers = listOf(
            LayerDef("GEOM", 7),
            LayerDef("BMD", 1),
            LayerDef("SFD", 2),
            LayerDef("REACTIONS", 5),
            LayerDef("TEXT", 3),
            LayerDef("NODES", 4)
        )

        writeHeader(sb)
        writeClassesSection(sb)
        writeLayerTable(sb, frameLayers)
        writeBlocksSection(sb)
        sb.append("0\nSECTION\n2\nENTITIES\n")

        drawText(sb, 0.0, -1200.0, "FRAME ANALYSIS RESULTS", "TEXT", 250.0, 7)
        drawText(sb, 0.0, -1600.0, "Nodes: ${nodes.size}  |  Members: ${members.size}", "TEXT", 140.0, 3)

        // Draw members
        members.forEach { m ->
            val n1 = nodes.find { it.id == m.nodeI }
            val n2 = nodes.find { it.id == m.nodeJ }
            if (n1 != null && n2 != null) {
                drawLine(sb, n1.x * scale, n1.y * scale, n2.x * scale, n2.y * scale, "GEOM", 7)
            }
        }

        // Draw nodes
        nodes.forEach { node ->
            val nx = node.x * scale
            val ny = node.y * scale
            // Draw support symbols
            when (node.support) {
                FrameSupportType.Pin -> {
                    drawSupportSymbol(sb, nx, ny, 200.0)
                }
                FrameSupportType.Fixed -> {
                    // Fixed support — hatched ground line
                    drawLine(sb, nx - 250.0, ny, nx + 250.0, ny, "GEOM", 7)
                    for (i in -2..2) {
                        val hx = nx + i * 100.0
                        drawLine(sb, hx, ny, hx - 50.0, ny - 80.0, "GEOM", 7)
                    }
                }
                else -> { /* Free — just a dot */ }
            }
            // Node dot
            drawCircle(sb, nx, ny, 40.0, "NODES", 4)
            drawText(sb, nx + 60.0, ny - 60.0, "${node.id}", "TEXT", 100.0, 4)
        }

        // BMD labels on members
        result.memberEndForces.forEach { f ->
            val m = members.find { it.id == f.memberId } ?: return@forEach
            val n1 = nodes.find { it.id == m.nodeI } ?: return@forEach
            val n2 = nodes.find { it.id == m.nodeJ } ?: return@forEach
            val mx = (n1.x + n2.x) / 2 * scale
            val my = (n1.y + n2.y) / 2 * scale + 80.0
            val maxM = max(abs(f.mi_z), abs(f.mj_z))
            if (maxM > 0.01) {
                drawText(sb, mx, my, "M=%.1f kN.m".format(maxM), "BMD", 130.0, 1)
            }
        }

        // Reaction labels at supports
        result.nodeResults.forEach { nr ->
            val node = nodes.find { it.id == nr.nodeId } ?: return@forEach
            if (node.support != FrameSupportType.Free) {
                val rx = node.x * scale
                val ry = node.y * scale - 300.0
                val hasReaction = abs(nr.reactionFx) > 0.01 || abs(nr.reactionFy) > 0.01
                if (hasReaction) {
                    drawText(sb, rx - 400.0, ry, "R=%.1f kN".format(nr.reactionFy), "REACTIONS", 110.0, 5)
                }
            }
        }

        // ── SUMMARY TABLE ──
        val maxReaction = result.nodeResults.maxOfOrNull { abs(it.reactionFy) } ?: 0.0
        val maxMoment = result.memberEndForces.maxOfOrNull {
            max(abs(it.mi_z), abs(it.mj_z))
        } ?: 0.0
        val maxShear = result.memberEndForces.maxOfOrNull {
            max(abs(it.fi_y), abs(it.fj_y))
        } ?: 0.0

        drawTitleBlock(sb, 10000.0, 5000.0, "FRAME ANALYSIS SUMMARY", listOf(
            "Nodes" to "${nodes.size}",
            "Members" to "${members.size}",
            "Max Moment" to "%.1f kN.m".format(maxMoment),
            "Max Shear" to "%.1f kN".format(maxShear),
            "Max Reaction" to "%.1f kN".format(maxReaction),
            "Status" to if (result.isSolved) "SOLVED" else "NOT SOLVED"
        ))

        sb.append("0\nENDSEC\n"); writeObjectsSection(sb); sb.append("0\nEOF\n")
        return sb.toString()
    }

    // DXF header variable names (with $ prefix as required by DXF format)
    private const val ACADVER = "\$ACADVER"
    private const val INSBASE = "\$INSBASE"
    private const val EXTMIN = "\$EXTMIN"
    private const val EXTMAX = "\$EXTMAX"

    // ═══════════════════════════════════════════════════════════════
    //  DXF STRUCTURE WRITERS
    // ═══════════════════════════════════════════════════════════════

    private fun writeHeader(sb: StringBuilder) {
        nextHandle = 0x100L
        val h = { nextH() }
        layerTableHandle = h()
        ltypeTableHandle = h()
        styleTableHandle = h()
        viewTableHandle = h()
        ucsTableHandle = h()
        appidTableHandle = h()
        vportTableHandle = h()
        dictHandle = h()
        h() // root handle

        val d = "$"
        sb.append("0\nSECTION\n2\nHEADER\n")
        sb.append("9\n${d}ACADVER\n1\nAC1015\n")
        sb.append("9\n${d}INSBASE\n10\n0.0\n20\n0.0\n30\n0.0\n")
        sb.append("9\n${d}EXTMIN\n10\n0.0\n20\n0.0\n30\n0.0\n")
        sb.append("9\n${d}EXTMAX\n10\n1.0\n20\n1.0\n30\n0.0\n")
        sb.append("9\n${d}HANDSEED\n5\n${h()}\n")
        sb.append("0\nENDSEC\n")
    }

    private fun writeLayerTable(sb: StringBuilder, layers: List<LayerDef>) {
        sb.append("0\nSECTION\n2\nTABLES\n")
        // VPORT table
        sb.append("0\nTABLE\n2\nVPORT\n5\n$vportTableHandle\n100\nAcDbSymbolTable\n70\n0\n")
        sb.append("0\nENDTAB\n")
        // LTYPE table
        sb.append("0\nTABLE\n2\nLTYPE\n5\n$ltypeTableHandle\n100\nAcDbSymbolTable\n70\n1\n")
        sb.append("0\nLTYPE\n5\n${nextH()}\n100\nAcDbSymbolTableRecord\n100\nAcDbLinetypeTableRecord\n2\nCONTINUOUS\n70\n0\n3\nSolid line\n72\n65\n73\n0\n40\n0.0\n")
        sb.append("0\nENDTAB\n")
        // LAYER table
        sb.append("0\nTABLE\n2\nLAYER\n5\n$layerTableHandle\n100\nAcDbSymbolTable\n70\n${layers.size}\n")
        layers.forEach { layer ->
            val lh = nextH()
            sb.append("0\nLAYER\n5\n$lh\n100\nAcDbSymbolTableRecord\n100\nAcDbLayerTableRecord\n2\n${layer.name}\n70\n0\n62\n${layer.color}\n6\nCONTINUOUS\n")
        }
        sb.append("0\nENDTAB\n")
        // STYLE table
        sb.append("0\nTABLE\n2\nSTYLE\n5\n$styleTableHandle\n100\nAcDbSymbolTable\n70\n1\n")
        sb.append("0\nSTYLE\n5\n${nextH()}\n100\nAcDbSymbolTableRecord\n100\nAcDbTextStyleTableRecord\n2\nSTANDARD\n70\n0\n40\n0.0\n41\n1.0\n50\n0.0\n71\n0\n42\n2.5\n3\ntxt\n4\n\n")
        sb.append("0\nENDTAB\n")
        // VIEW, UCS, APPID tables
        sb.append("0\nTABLE\n2\nVIEW\n5\n$viewTableHandle\n100\nAcDbSymbolTable\n70\n0\n0\nENDTAB\n")
        sb.append("0\nTABLE\n2\nUCS\n5\n$ucsTableHandle\n100\nAcDbSymbolTable\n70\n0\n0\nENDTAB\n")
        sb.append("0\nTABLE\n2\nAPPID\n5\n$appidTableHandle\n100\nAcDbSymbolTable\n70\n1\n")
        sb.append("0\nAPPID\n5\n${nextH()}\n100\nAcDbSymbolTableRecord\n100\nAcDbRegAppTableRecord\n2\nACAD\n70\n0\n0\nENDTAB\n")
        sb.append("0\nENDSEC\n")
    }

    private fun writeClassesSection(sb: StringBuilder) {
        sb.append("0\nSECTION\n2\nCLASSES\n0\nENDSEC\n")
    }

    private fun writeBlocksSection(sb: StringBuilder) {
        sb.append("0\nSECTION\n2\nBLOCKS\n")
        sb.append("0\nBLOCK\n5\n${nextH()}\n100\nAcDbEntity\n8\n0\n100\nAcDbBlockBegin\n2\n*MODEL_SPACE\n70\n0\n10\n0.0\n20\n0.0\n30\n0.0\n3\n*MODEL_SPACE\n1\n\n")
        sb.append("0\nENDBLK\n5\n${nextH()}\n100\nAcDbEntity\n8\n0\n100\nAcDbBlockEnd\n")
        sb.append("0\nBLOCK\n5\n${nextH()}\n100\nAcDbEntity\n67\n1\n8\n0\n100\nAcDbBlockBegin\n2\n*PAPER_SPACE\n70\n0\n10\n0.0\n20\n0.0\n30\n0.0\n3\n*PAPER_SPACE\n1\n\n")
        sb.append("0\nENDBLK\n5\n${nextH()}\n100\nAcDbEntity\n67\n1\n8\n0\n100\nAcDbBlockEnd\n")
        sb.append("0\nENDSEC\n")
    }

    private fun writeObjectsSection(sb: StringBuilder) {
        sb.append("0\nSECTION\n2\nOBJECTS\n")
        sb.append("0\nDICTIONARY\n5\n$dictHandle\n100\nAcDbDictionary\n281\n1\n3\nACAD_GROUP\n350\n${nextH()}\n")
        sb.append("0\nDICTIONARY\n5\n${nextH()}\n100\nAcDbDictionary\n281\n1\n")
        sb.append("0\nENDSEC\n")
    }

    // ═══════════════════════════════════════════════════════════════
    //  DXF ENTITY WRITERS
    // ═══════════════════════════════════════════════════════════════

    private fun drawLine(
        sb: StringBuilder,
        x1: Double, y1: Double,
        x2: Double, y2: Double,
        layer: String,
        color: Int = -1
    ) {
        val h = nextH()
        sb.append("0\nLINE\n5\n$h\n100\nAcDbEntity\n8\n$layer\n")
        if (color > 0) sb.append("62\n$color\n")
        sb.append("100\nAcDbLine\n10\n${fmt(x1)}\n20\n${fmt(y1)}\n30\n0.0\n11\n${fmt(x2)}\n21\n${fmt(y2)}\n31\n0.0\n")
    }

    /** Draw a line with explicit rebar diameter visual weight. */
    private fun drawRebarLine(
        sb: StringBuilder,
        x1: Double, y1: Double,
        x2: Double, y2: Double,
        diameter: Double,
        layer: String
    ) {
        // DXF doesn't support line weight in basic format, so we draw
        // the line plus two offset lines to simulate bar thickness
        drawLine(sb, x1, y1, x2, y2, layer, 1)
        if (diameter >= 16) {
            // Draw slightly offset parallel lines for thicker bars
            drawLine(sb, x1, y1 - diameter / 4, x2, y2 - diameter / 4, layer, 1)
            drawLine(sb, x1, y1 + diameter / 4, x2, y2 + diameter / 4, layer, 1)
        }
    }

    private fun drawRect(
        sb: StringBuilder,
        x: Double, y: Double,
        w: Double, h: Double,
        layer: String,
        color: Int = -1
    ) {
        drawLine(sb, x, y, x + w, y, layer, color)
        drawLine(sb, x + w, y, x + w, y + h, layer, color)
        drawLine(sb, x + w, y + h, x, y + h, layer, color)
        drawLine(sb, x, y + h, x, y, layer, color)
    }

    private fun drawCircle(
        sb: StringBuilder,
        cx: Double, cy: Double,
        radius: Double,
        layer: String,
        color: Int = -1
    ) {
        val h = nextH()
        sb.append("0\nCIRCLE\n5\n$h\n100\nAcDbEntity\n8\n$layer\n")
        if (color > 0) sb.append("62\n$color\n")
        sb.append("100\nAcDbCircle\n10\n${fmt(cx)}\n20\n${fmt(cy)}\n30\n0.0\n40\n${fmt(radius)}\n")
    }

    private fun drawText(
        sb: StringBuilder,
        x: Double, y: Double,
        text: String,
        layer: String,
        height: Double,
        color: Int = -1
    ) {
        val h = nextH()
        sb.append("0\nTEXT\n5\n$h\n100\nAcDbEntity\n8\n$layer\n")
        if (color > 0) sb.append("62\n$color\n")
        sb.append("100\nAcDbText\n10\n${fmt(x)}\n20\n${fmt(y)}\n30\n0.0\n40\n${fmt(height)}\n1\n$text\n50\n0.0\n7\nSTANDARD\n")
    }

    /** Draw a polyline (open) for complex shapes. */
    private fun drawPolyline(
        sb: StringBuilder,
        points: List<Pair<Double, Double>>,
        layer: String,
        color: Int = -1,
        closed: Boolean = false
    ) {
        if (points.size < 2) return
        val h = nextH()
        val flag = if (closed) 1 else 0
        sb.append("0\nLWPOLYLINE\n5\n$h\n100\nAcDbEntity\n8\n$layer\n")
        if (color > 0) sb.append("62\n$color\n")
        sb.append("100\nAcDbPolyline\n90\n${points.size}\n70\n$flag\n43\n0.0\n")
        points.forEach { (px, py) -> sb.append("10\n${fmt(px)}\n20\n${fmt(py)}\n") }
    }

    /** Draw a solid-filled hatch pattern (simplified using SOLID entity). */
    private fun drawSolid(
        sb: StringBuilder,
        x1: Double, y1: Double,
        x2: Double, y2: Double,
        x3: Double, y3: Double,
        x4: Double, y4: Double,
        layer: String,
        color: Int = -1
    ) {
        val h = nextH()
        sb.append("0\nSOLID\n5\n$h\n100\nAcDbEntity\n8\n$layer\n")
        if (color > 0) sb.append("62\n$color\n")
        sb.append("10\n${fmt(x1)}\n20\n${fmt(y1)}\n30\n0.0\n")
        sb.append("11\n${fmt(x2)}\n21\n${fmt(y2)}\n31\n0.0\n")
        sb.append("12\n${fmt(x3)}\n22\n${fmt(y3)}\n32\n0.0\n")
        sb.append("13\n${fmt(x4)}\n23\n${fmt(y4)}\n33\n0.0\n")
    }

    // ── DIMENSION HELPERS ────────────────────────────────────────────

    private fun drawHorizontalDimension(
        sb: StringBuilder,
        x1: Double, x2: Double,
        y: Double,
        text: String,
        color: Int = 5
    ) {
        val tick = 120.0
        // Dimension line
        drawLine(sb, x1, y, x2, y, "DIMENSIONS", color)
        // Extension lines
        drawLine(sb, x1, y - tick, x1, y + tick, "DIMENSIONS", color)
        drawLine(sb, x2, y - tick, x2, y + tick, "DIMENSIONS", color)
        // Text above dimension line
        drawText(sb, (x1 + x2) / 2, y + 200.0, text, "DIMENSIONS", 130.0, color)
    }

    private fun drawVerticalDimension(
        sb: StringBuilder,
        y1: Double, y2: Double,
        x: Double,
        text: String,
        color: Int = 5
    ) {
        val tick = 120.0
        drawLine(sb, x, y1, x, y2, "DIMENSIONS", color)
        drawLine(sb, x - tick, y1, x + tick, y1, "DIMENSIONS", color)
        drawLine(sb, x - tick, y2, x + tick, y2, "DIMENSIONS", color)
        drawText(sb, x - 800.0, (y1 + y2) / 2, text, "DIMENSIONS", 130.0, color)
    }

    // ── SPECIAL DRAWING HELPERS ──────────────────────────────────────

    /** Draw a triangular support symbol. */
    private fun drawSupportSymbol(
        sb: StringBuilder,
        x: Double, y: Double,
        size: Double
    ) {
        drawLine(sb, x - size / 2, y, x + size / 2, y, "CONCRETE", 7)
        drawLine(sb, x - size / 2, y, x, y + size * 0.6, "CONCRETE", 7)
        drawLine(sb, x + size / 2, y, x, y + size * 0.6, "CONCRETE", 7)
        // Ground hatching below support
        val hatchLen = size * 1.2
        for (i in -2..2) {
            val hx = x + i * size * 0.2
            drawLine(sb, hx, y, hx - size * 0.15, y - size * 0.2, "CONCRETE", 7)
        }
    }

    /** Draw a stirrup (tie) rectangle outline at position x,y for a section w x h. */
    private fun drawStirrup(
        sb: StringBuilder,
        x: Double, y: Double,
        w: Double, h: Double,
        cover: Double,
        diameter: Double
    ) {
        // Draw as a rectangle with the stirrup line style
        drawRect(sb, x + cover, y + cover, w - 2 * cover, h - 2 * cover, "STIRRUPS", 2)
    }

    /** Draw a professional title block with design data table. */
    private fun drawTitleBlock(
        sb: StringBuilder,
        x: Double, y: Double,
        title: String,
        data: List<Pair<String, String>>
    ) {
        val rowH = 400.0
        val colW = 5000.0
        val totalW = colW * 2
        val totalH = (data.size + 2) * rowH

        // Border
        drawRect(sb, x, y - totalH, totalW, totalH, "TEXT", 7)
        // Header separator
        drawLine(sb, x, y - rowH, x + totalW, y - rowH, "TEXT", 7)
        // Column separator
        drawLine(sb, x + colW, y, x + colW, y - totalH, "TEXT", 7)

        // Title
        drawText(sb, x + 200.0, y - rowH / 2 + 60.0, title, "TEXT", 180.0, 7)
        // Column headers
        drawText(sb, x + 200.0, y - rowH - rowH / 2 + 60.0, "Parameter", "TEXT", 130.0, 7)
        drawText(sb, x + colW + 200.0, y - rowH - rowH / 2 + 60.0, "Value", "TEXT", 130.0, 7)
        drawLine(sb, x, y - 2 * rowH, x + totalW, y - 2 * rowH, "TEXT", 7)

        // Data rows
        data.forEachIndexed { i, (key, value) ->
            val rowY = y - (i + 2) * rowH
            drawText(sb, x + 200.0, rowY + rowH / 2, key, "TEXT", 110.0, 7)
            drawText(sb, x + colW + 200.0, rowY + rowH / 2, value, "TEXT", 110.0, 4)
            // Row separator
            if (i < data.size - 1) {
                drawLine(sb, x, rowY, x + totalW, rowY, "TEXT", 7)
            }
        }

        // Footer with timestamp
        val footer = "Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(java.util.Date())}"
        drawText(sb, x + 200.0, y - totalH + 100.0, footer, "TEXT", 90.0, 7)
    }
}
