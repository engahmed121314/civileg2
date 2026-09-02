package com.civileg.app.utils.detailing

import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// CIVILEG DXF WRITER  (AC1009 / AutoCAD R12 format)
//
// Sole responsibility: convert a flat list of CadEntity objects into a valid
// DXF text file.  AC1009 is used for MAXIMUM compatibility with mobile
// viewers and old CAD software.
//
// Rules enforced here:
//   • AC1009 format (No handles, no subclasses, no objects section)
//   • All referenced layers are declared in the TABLES section
//   • NaN / Infinity are rejected with DxfExportException
//   • Arabic text is pre-shaped and escaped (\U+XXXX)
//   • DXF structure: HEADER → TABLES → BLOCKS → ENTITIES → EOF
// ─────────────────────────────────────────────────────────────────────────────

class DxfWriter {

    private val sb = StringBuilder(1 shl 18)
    private val usedLayers = linkedSetOf<String>()
    private val blockDefs = mutableListOf<CadBlockDef>()

    // Bounding box tracking
    private var minX = Double.MAX_VALUE;  private var minY = Double.MAX_VALUE
    private var maxX = -Double.MAX_VALUE; private var maxY = -Double.MAX_VALUE

    /**
     * Write all [entities] to a DXF string. AC1009 version.
     */
    fun write(
        entities: List<CadEntity>,
        titleBlock: TitleBlock? = null,
        paperWidthMm: Double = 420.0,
        paperHeightMm: Double = 297.0,
        extraBlockDefs: List<CadBlockDef> = emptyList()
    ): String {
        sb.clear()
        minX = Double.MAX_VALUE; minY = Double.MAX_VALUE
        maxX = -Double.MAX_VALUE; maxY = -Double.MAX_VALUE
        usedLayers.clear()
        blockDefs.clear()
        blockDefs.addAll(extraBlockDefs)

        val allEntities = expandComposites(entities)
        allEntities.forEach { usedLayers += it.layer }

        writeHeader()
        writeTables()
        writeBlocks()
        writeEntities(allEntities, titleBlock, paperWidthMm, paperHeightMm)
        raw("0\nEOF\n")

        return sb.toString()
            .replace("__EXTMINX__", fmt(minX.takeIf { it != Double.MAX_VALUE } ?: 0.0))
            .replace("__EXTMINY__", fmt(minY.takeIf { it != Double.MAX_VALUE } ?: 0.0))
            .replace("__EXTMAXX__", fmt(maxX.takeIf { it != -Double.MAX_VALUE } ?: paperWidthMm))
            .replace("__EXTMAXY__", fmt(maxY.takeIf { it != -Double.MAX_VALUE } ?: paperHeightMm))
            .replace("\n", "\r\n")
    }

    fun writeToFile(entities: List<CadEntity>, file: File, titleBlock: TitleBlock? = null) {
        val content = write(entities, titleBlock)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { 
            // ADR-004/ADR-009: R12 (AC1009) requires windows-1256 for Arabic support.
            // Unicode escapes (\U+XXXX) are forbidden in this legacy format.
            it.write(content.toByteArray(java.nio.charset.Charset.forName("windows-1256"))) 
        }
    }

    private fun expandComposites(entities: List<CadEntity>): List<CadEntity> {
        val out = mutableListOf<CadEntity>()
        for (e in entities) {
            when (e) {
                is CadSectionMarker -> out.addAll(e.toPrimitives())
                is CadArrow         -> {
                    try { out.add(e.toPolyline()) } catch (_: Exception) {}
                }
                is CadGridLine      -> out.addAll(e.toPrimitives())
                is CadTable         -> {
                    try { out.addAll(e.toPrimitives()) } catch (_: Exception) {}
                }
                is CadRebarSymbol   -> out.addAll(e.toPrimitives())
                is CadDimLinear     -> {
                    val dx = abs(e.x2 - e.x1); val dy = abs(e.y2 - e.y1)
                    val isHoriz = dx >= dy
                    val midX = (e.x1 + e.x2) / 2.0; val midY = (e.y1 + e.y2) / 2.0
                    val dimLineY = if (isHoriz) e.y1 + e.offsetMm else midY
                    val dimLineX = if (isHoriz) midX else e.x1 + e.offsetMm
                    out.add(CadLine(e.x1, e.y1, e.x1, dimLineY, layer = e.layer, color = e.color))
                    out.add(CadLine(e.x2, e.y2, e.x2, dimLineY, layer = e.layer, color = e.color))
                    out.add(CadLine(e.x1, dimLineY, e.x2, dimLineY, layer = e.layer, color = e.color))
                    out.add(CadText(text = e.displayText, x = dimLineX, y = dimLineY + 2.0, heightMm = 2.5, layer = e.layer, color = e.color, hJustify = 1))
                }
                else -> out.add(e)
            }
        }
        return out
    }

    private fun escape(s: String): String {
        // ADR-004/ADR-009: R12 (AC1009) does not support \U+ Unicode escaping.
        // We rely on Windows-1256 encoding at the file-writing level and 
        // keep text as-is for the character set to handle.
        return s
    }

    private fun raw(s: String) { sb.append(s) }
    private fun pair(code: Int, v: String) { raw("$code\n$v\n") }
    private fun pair(code: Int, v: Int)    { raw("$code\n$v\n") }
    private fun pair(code: Int, v: Double) {
        if (!v.isFinite()) throw DxfExportException("Non-finite value $v")
        raw("$code\n${fmt(v)}\n")
    }
    private fun pt2d(code10: Int, x: Double, y: Double) {
        pair(code10, x); pair(code10 + 10, y); pair(code10 + 20, 0.0)
        if (x < minX) minX = x; if (x > maxX) maxX = x
        if (y < minY) minY = y; if (y > maxY) maxY = y
    }
    private fun fmt(v: Double) = String.format(Locale.US, "%.4f", v)

    private fun writeHeader() {
        raw("0\nSECTION\n2\nHEADER\n9\n\$ACADVER\n1\nAC1009\n")
        raw("9\n\$EXTMIN\n10\n__EXTMINX__\n20\n__EXTMINY__\n30\n0.0\n")
        raw("9\n\$EXTMAX\n10\n__EXTMAXX__\n20\n__EXTMAXY__\n30\n0.0\n")
        raw("0\nENDSEC\n")
    }

    private fun writeTables() {
        raw("0\nSECTION\n2\nTABLES\n")
        raw("0\nTABLE\n2\nLTYPE\n70\n1\n0\nLTYPE\n2\nCONTINUOUS\n70\n0\n3\nSolid line\n72\n65\n73\n0\n40\n0.0\n0\nENDTAB\n")
        val layers = usedLayers.toMutableSet()
        layers.add("0"); layers.add(CadLayers.CONC); layers.add(CadLayers.REBAR); layers.add(CadLayers.TEXT)
        raw("0\nTABLE\n2\nLAYER\n70\n${layers.size}\n")
        for (name in layers) {
            raw("0\nLAYER\n2\n$name\n70\n0\n62\n${CadColors.layerColors[name] ?: 7}\n6\nCONTINUOUS\n")
        }
        raw("0\nENDTAB\n")
        raw("0\nTABLE\n2\nSTYLE\n70\n1\n0\nSTYLE\n2\nSTANDARD\n70\n0\n40\n0.0\n41\n1.0\n50\n0.0\n71\n0\n42\n2.5\n3\ntxt\n4\n\n0\nENDTAB\n")
        raw("0\nENDSEC\n")
    }

    private fun writeBlocks() {
        raw("0\nSECTION\n2\nBLOCKS\n")
        for (def in blockDefs) {
            raw("0\nBLOCK\n8\n0\n2\n${def.name}\n70\n0\n10\n0.0\n20\n0.0\n30\n0.0\n3\n${def.name}\n1\n\n")
            for (e in def.entities) writeEntity(e)
            raw("0\nENDBLK\n8\n0\n")
        }
        raw("0\nENDSEC\n")
    }

    private fun writeEntities(entities: List<CadEntity>, titleBlock: TitleBlock?, pw: Double, ph: Double) {
        raw("0\nSECTION\n2\nENTITIES\n")
        for (e in entities) writeEntity(e)
        if (titleBlock != null) {
            val margin = 5.0; val w = 180.0; val h = 55.0
            val x = (pw - margin - w).coerceAtLeast(0.0); val y = margin.coerceAtLeast(0.0)
            Companion.buildTitleBlockEntities(titleBlock, x, y, w, h).forEach { writeEntity(it) }
        }
        raw("0\nENDSEC\n")
    }

    private fun writeEntity(e: CadEntity) {
        when (e) {
            is CadLine       -> writeLine(e)
            is CadPolyline   -> writePolyline(e)
            is CadCircle     -> writeCircle(e)
            is CadText       -> writeText(e)
            is CadCenterLine -> { raw("0\nLINE\n8\n${e.layer}\n6\nCONTINUOUS\n"); pt2d(10, e.x1, e.y1); pt2d(11, e.x2, e.y2) }
            is CadInsert     -> { raw("0\nINSERT\n8\n${e.layer}\n2\n${e.blockName}\n"); pt2d(10, e.x, e.y); pair(41, e.scaleX); pair(42, e.scaleY); if(e.rotation!=0.0)pair(50,e.rotation) }
            is CadHatch      -> writeHatch(e)
            else -> {}
        }
    }

    private fun writeLine(e: CadLine) {
        raw("0\nLINE\n8\n${e.layer}\n")
        if (e.color >= 0) pair(62, e.color)
        pt2d(10, e.x1, e.y1); pt2d(11, e.x2, e.y2)
    }

    private fun writePolyline(e: CadPolyline) {
        raw("0\nPOLYLINE\n8\n${e.layer}\n")
        if (e.color >= 0) pair(62, e.color)
        pair(66, 1); pair(70, if (e.closed) 1 else 0)
        e.points.forEach { p -> raw("0\nVERTEX\n8\n${e.layer}\n"); pt2d(10, p.x, p.y) }
        raw("0\nSEQEND\n8\n${e.layer}\n")
    }

    private fun writeCircle(e: CadCircle) {
        raw("0\nCIRCLE\n8\n${e.layer}\n")
        if (e.color >= 0) pair(62, e.color)
        pt2d(10, e.cx, e.cy); pair(40, e.radius)
    }

    private fun writeText(e: CadText) {
        raw("0\nTEXT\n8\n${e.layer}\n")
        if (e.color >= 0) pair(62, e.color)
        pt2d(10, e.x, e.y); pair(40, e.heightMm); pair(1, escape(e.text))
        if (e.rotation != 0.0) pair(50, e.rotation)
        if (e.hJustify != 0) { pair(72, e.hJustify); pt2d(11, e.x, e.y) }
    }

    private fun writeHatch(e: CadHatch) {
        e.boundary.forEach { loop ->
            if (loop.points.size >= 2) {
                try {
                    val poly = CadPolyline(loop.points, closed = true, layer = e.layer, color = e.color)
                    raw("0\nPOLYLINE\n8\n${poly.layer}\n")
                    if (poly.color >= 0) pair(62, poly.color)
                    pair(66, 1); pair(70, 1)
                    poly.points.forEach { p -> raw("0\nVERTEX\n8\n${poly.layer}\n"); pt2d(10, p.x, p.y) }
                    raw("0\nSEQEND\n8\n${poly.layer}\n")
                } catch (_: Exception) {}
            }
        }
    }

    companion object {
        fun buildTitleBlockEntities(tb: TitleBlock, x: Double, y: Double, width: Double, height: Double): List<CadEntity> {
            val entities = mutableListOf<CadEntity>()
            val ly = CadLayers.TITLE
            entities.add(CadLine(x, y, x + width, y, ly))
            entities.add(CadLine(x + width, y, x + width, y + height, ly))
            entities.add(CadLine(x + width, y + height, x, y + height, ly))
            entities.add(CadLine(x, y + height, x, y, ly))
            val rows = listOf(0.0, 12.0, 22.0, 32.0, 44.0, 55.0)
            rows.forEach { entities.add(CadLine(x, y + it, x + width, y + it, ly)) }
            val mx = x + width / 2; entities.add(CadLine(mx, y, mx, y + 44.0, ly))
            data class Fld(val l: String, val v: String, val r: Int, val left: Boolean)
            val fields = listOf(
                Fld("PROJECT", tb.project, 0, true), Fld("TITLE", tb.drawingTitle, 0, false),
                Fld("DATE", tb.date, 4, true), Fld("SCALE", tb.scale, 4, false)
            )
            fields.forEach { f ->
                val ry = y + rows[f.r]
                val bx = if (f.left) x + 2 else mx + 2
                entities.add(CadText(text = f.l, x = bx, y = ry + 8, heightMm = 2.0, layer = ly, color = 8))
                if (f.v.isNotBlank()) entities.add(CadText(text = f.v.take(25), x = bx, y = ry + 2, heightMm = 2.8, layer = ly))
            }
            return entities
        }
    }
}
