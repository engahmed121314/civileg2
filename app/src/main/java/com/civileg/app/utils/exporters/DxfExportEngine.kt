package com.civileg.app.utils.exporters

/**
 * DxfExportEngine - A comprehensive DXF file format writer for AC1015 (AutoCAD 2000).
 *
 * Produces valid DXF files with proper section structure:
 *   HEADER -> CLASSES -> TABLES -> BLOCKS -> ENTITIES -> OBJECTS -> EOF
 *
 * Supports entity types: LINE, LWPOLYLINE, CIRCLE, ARC, TEXT, MTEXT,
 *   DIMENSION (linear, aligned), HATCH, SOLID, POINT, SPLINE, ELLIPSE, INSERT
 *
 * Supports line types: CONTINUOUS, DASHED, CENTER, PHANTOM, HIDDEN
 * Supports standard structural layers with ACI colors.
 * All coordinates in millimeters. CRLF line endings throughout.
 */
public class DxfExportEngine {

    // ------------------------------------------------------------------
    // Handle allocator
    // ------------------------------------------------------------------

    private var nextHandle: Int = 0x10

    private fun allocHandle(): String {
        val h = nextHandle
        nextHandle += 1
        return h.toString(16).uppercase()
    }

    private fun allocHandles(n: Int): List<String> = (0 until n).map { allocHandle() }

    // ------------------------------------------------------------------
    // Layer definitions
    // ------------------------------------------------------------------

    data class LayerDef(val name: String, val color: Int, val lineType: String = "CONTINUOUS")

    private val defaultLayers = listOf(
        LayerDef("0", 7, "CONTINUOUS"),
        LayerDef("CONCRETE", 7, "CONTINUOUS"),
        LayerDef("STEEL", 5, "CONTINUOUS"),
        LayerDef("DIMENSIONS", 1, "CONTINUOUS"),
        LayerDef("TEXT", 7, "CONTINUOUS"),
        LayerDef("CENTER_LINE", 1, "CENTER"),
        LayerDef("HATCH", 3, "CONTINUOUS"),
        LayerDef("HIDDEN", 7, "HIDDEN"),
        LayerDef("SECTION", 4, "CONTINUOUS"),
        LayerDef("GRID", 3, "DASHED"),
        LayerDef("REBAR", 1, "CONTINUOUS"),
        LayerDef("OUTLINE", 7, "CONTINUOUS"),
        LayerDef("DIAGRAM", 2, "CONTINUOUS"),
    )

    var layers: MutableList<LayerDef> = defaultLayers.toMutableList()

    // ------------------------------------------------------------------
    // Entity buffer
    // ------------------------------------------------------------------

    private val entityBuffer = StringBuilder()

    // ------------------------------------------------------------------
    // DXF pair helper
    // ------------------------------------------------------------------

    private fun pair(code: Int, value: String): String {
        return "${code}\r\n${value}\r\n"
    }

    private fun pair(code: Int, value: Int): String {
        return "${code}\r\n${value}\r\n"
    }

    private fun pair(code: Int, value: Double): String {
        val formatted = if (value == value.toLong().toDouble()) {
            value.toLong().toString() + ".0"
        } else {
            val s = value.toString()
            if (s.contains('.')) s else "$s.0"
        }
        return "${code}\r\n${formatted}\r\n"
    }

    // ------------------------------------------------------------------
    // HEADER section
    // ------------------------------------------------------------------

    private fun writeHeader(sb: StringBuilder) {
        sb.append(pair(0, "SECTION")).append(pair(2, "HEADER"))

        // ACADVER
        sb.append(pair(9, "\$ACADVER")).append(pair(1, "AC1015"))
        // INSBASE
        sb.append(pair(9, "\$INSBASE")).append(pair(10, 0.0)).append(pair(20, 0.0)).append(pair(30, 0.0))
        // EXTMIN
        sb.append(pair(9, "\$EXTMIN")).append(pair(10, 0.0)).append(pair(20, 0.0)).append(pair(30, 0.0))
        // EXTMAX
        sb.append(pair(9, "\$EXTMAX")).append(pair(10, 500.0)).append(pair(20, 500.0)).append(pair(30, 0.0))
        // LIMMIN
        sb.append(pair(9, "\$LIMMIN")).append(pair(10, 0.0)).append(pair(20, 0.0))
        // LIMMAX
        sb.append(pair(9, "\$LIMMAX")).append(pair(10, 420.0)).append(pair(20, 297.0))
        // UNITMODE
        sb.append(pair(9, "\$UNITMODE")).append(pair(70, "0"))
        // DIMSTYLE
        sb.append(pair(9, "\$DIMSTYLE")).append(pair(2, "Standard"))
        // TEXTSTYLE
        sb.append(pair(9, "\$TEXTSTYLE")).append(pair(7, "Standard"))
        // MEASUREMENT - metric
        sb.append(pair(9, "\$MEASUREMENT")).append(pair(70, "1"))
        // LUNITS - decimal
        sb.append(pair(9, "\$LUNITS")).append(pair(70, "2"))
        // LUPREC
        sb.append(pair(9, "\$LUPREC")).append(pair(70, "2"))
        // AUNITS
        sb.append(pair(9, "\$AUNITS")).append(pair(70, "0"))
        // AUPREC
        sb.append(pair(9, "\$AUPREC")).append(pair(70, "2"))
        // DIMLFAC
        sb.append(pair(9, "\$DIMLFAC")).append(pair(40, 1.0))
        // DIMSCALE
        sb.append(pair(9, "\$DIMSCALE")).append(pair(40, 1.0))
        // DIMTXT
        sb.append(pair(9, "\$DIMTXT")).append(pair(40, 3.5))
        // DIMASZ
        sb.append(pair(9, "\$DIMASZ")).append(pair(40, 3.0))
        // DIMEXO
        sb.append(pair(9, "\$DIMEXO")).append(pair(40, 1.0))
        // DIMEXE
        sb.append(pair(9, "\$DIMEXE")).append(pair(40, 1.5))
        // DIMDLI
        sb.append(pair(9, "\$DIMDLI")).append(pair(40, 8.0))
        // DIMCLRD - dim line color (0=ByBlock)
        sb.append(pair(9, "\$DIMCLRD")).append(pair(70, "0"))
        // DIMCLRE - extension line color
        sb.append(pair(9, "\$DIMCLRE")).append(pair(70, "0"))
        // DIMCLRT - text color
        sb.append(pair(9, "\$DIMCLRT")).append(pair(70, "0"))
        // PSLTSCALE
        sb.append(pair(9, "\$PSLTSCALE")).append(pair(70, "1"))
        // LTSCALE
        sb.append(pair(9, "\$LTSCALE")).append(pair(40, 1.0))

        sb.append(pair(0, "ENDSEC"))
    }

    // ------------------------------------------------------------------
    // CLASSES section (empty but required)
    // ------------------------------------------------------------------

    private fun writeClasses(sb: StringBuilder) {
        sb.append(pair(0, "SECTION")).append(pair(2, "CLASSES"))
        sb.append(pair(0, "ENDSEC"))
    }

    // ------------------------------------------------------------------
    // TABLES section
    // ------------------------------------------------------------------

    private fun writeTables(sb: StringBuilder) {
        sb.append(pair(0, "SECTION")).append(pair(2, "TABLES"))
        writeVportTable(sb)
        writeLtypeTable(sb)
        writeLayerTable(sb)
        writeStyleTable(sb)
        writeViewTable(sb)
        writeUcsTable(sb)
        writeAppidTable(sb)
        writeDimstyleTable(sb)
        sb.append(pair(0, "ENDSEC"))
    }

    private fun writeVportTable(sb: StringBuilder) {
        val tableHandle = allocHandle()
        val vportHandle = allocHandle()
        sb.append(pair(0, "TABLE")).append(pair(2, "VPORT"))
            .append(pair(5, tableHandle))
            .append(pair(100, "AcDbSymbolTable"))
            .append(pair(70, "1"))
        sb.append(pair(0, "VPORT")).append(pair(5, vportHandle))
            .append(pair(100, "AcDbSymbolTableRecord"))
            .append(pair(100, "AcDbViewportTableRecord"))
            .append(pair(2, "*Active"))
            .append(pair(70, "0"))
            .append(pair(10, 0.0)).append(pair(20, 0.0))
            .append(pair(11, 1.0)).append(pair(21, 1.0))
            .append(pair(12, 0.0)).append(pair(22, 0.0))
            .append(pair(13, 0.0)).append(pair(23, 0.0))
            .append(pair(14, 10.0)).append(pair(24, 10.0))
            .append(pair(15, 10.0)).append(pair(25, 10.0))
            .append(pair(16, 0.0)).append(pair(26, 0.0))
            .append(pair(36, 1.0))
            .append(pair(17, 0.0)).append(pair(27, 0.0))
            .append(pair(37, 0.0))
            .append(pair(40, 50.0))
            .append(pair(41, 1.5))
            .append(pair(42, 50.0))
            .append(pair(43, 0.0))
            .append(pair(44, 0.0))
            .append(pair(50, 0.0))
            .append(pair(51, 0.0))
            .append(pair(71, "0"))
            .append(pair(72, "100"))
            .append(pair(73, "1"))
            .append(pair(74, "3"))
            .append(pair(75, "0"))
            .append(pair(76, "0"))
            .append(pair(77, "0"))
            .append(pair(78, "0"))
        sb.append(pair(0, "ENDTAB"))
    }

    private fun writeLtypeTable(sb: StringBuilder) {
        val tableHandle = allocHandle()
        val ltypeHandles = allocHandles(5)
        sb.append(pair(0, "TABLE")).append(pair(2, "LTYPE"))
            .append(pair(5, tableHandle))
            .append(pair(100, "AcDbSymbolTable"))
            .append(pair(70, "5"))

        // CONTINUOUS
        sb.append(pair(0, "LTYPE")).append(pair(5, ltypeHandles[0]))
            .append(pair(100, "AcDbSymbolTableRecord"))
            .append(pair(100, "AcDbLinetypeTableRecord"))
            .append(pair(2, "CONTINUOUS")).append(pair(70, "0"))
            .append(pair(3, "Solid line"))
            .append(pair(72, "65")).append(pair(73, "0")).append(pair(40, 0.0))

        // DASHED
        sb.append(pair(0, "LTYPE")).append(pair(5, ltypeHandles[1]))
            .append(pair(100, "AcDbSymbolTableRecord"))
            .append(pair(100, "AcDbLinetypeTableRecord"))
            .append(pair(2, "DASHED")).append(pair(70, "0"))
            .append(pair(3, "Dashed line __  __  __"))
            .append(pair(72, "65")).append(pair(73, "2")).append(pair(40, 0.3))
            .append(pair(49, 0.15)).append(pair(49, -0.1))

        // CENTER
        sb.append(pair(0, "LTYPE")).append(pair(5, ltypeHandles[2]))
            .append(pair(100, "AcDbSymbolTableRecord"))
            .append(pair(100, "AcDbLinetypeTableRecord"))
            .append(pair(2, "CENTER")).append(pair(70, "0"))
            .append(pair(3, "Center line ____ _ ____ _"))
            .append(pair(72, "65")).append(pair(73, "4")).append(pair(40, 1.2))
            .append(pair(49, 0.6)).append(pair(49, -0.15))
            .append(pair(49, 0.15)).append(pair(49, -0.15))

        // PHANTOM
        sb.append(pair(0, "LTYPE")).append(pair(5, ltypeHandles[3]))
            .append(pair(100, "AcDbSymbolTableRecord"))
            .append(pair(100, "AcDbLinetypeTableRecord"))
            .append(pair(2, "PHANTOM")).append(pair(70, "0"))
            .append(pair(3, "Phantom line ______  ____  ______"))
            .append(pair(72, "65")).append(pair(73, "6")).append(pair(40, 2.0))
            .append(pair(49, 0.8)).append(pair(49, -0.15))
            .append(pair(49, 0.15)).append(pair(49, -0.15))
            .append(pair(49, 0.15)).append(pair(49, -0.15))

        // HIDDEN
        sb.append(pair(0, "LTYPE")).append(pair(5, ltypeHandles[4]))
            .append(pair(100, "AcDbSymbolTableRecord"))
            .append(pair(100, "AcDbLinetypeTableRecord"))
            .append(pair(2, "HIDDEN")).append(pair(70, "0"))
            .append(pair(3, "Hidden line __ __ __ __ __"))
            .append(pair(72, "65")).append(pair(73, "2")).append(pair(40, 0.25))
            .append(pair(49, 0.1)).append(pair(49, -0.15))

        sb.append(pair(0, "ENDTAB"))
    }

    private fun writeLayerTable(sb: StringBuilder) {
        val tableHandle = allocHandle()
        val layerHandles = allocHandles(layers.size)
        sb.append(pair(0, "TABLE")).append(pair(2, "LAYER"))
            .append(pair(5, tableHandle))
            .append(pair(100, "AcDbSymbolTable"))
            .append(pair(70, layers.size.toString()))

        for ((i, layer) in layers.withIndex()) {
            sb.append(pair(0, "LAYER"))
                .append(pair(5, layerHandles[i]))
                .append(pair(100, "AcDbSymbolTableRecord"))
                .append(pair(100, "AcDbLayerTableRecord"))
                .append(pair(2, layer.name))
                .append(pair(70, "0"))
                .append(pair(62, layer.color.toString()))
                .append(pair(6, layer.lineType))
        }
        sb.append(pair(0, "ENDTAB"))
    }

    private fun writeStyleTable(sb: StringBuilder) {
        val tableHandle = allocHandle()
        val styleHandle = allocHandle()
        sb.append(pair(0, "TABLE")).append(pair(2, "STYLE"))
            .append(pair(5, tableHandle))
            .append(pair(100, "AcDbSymbolTable"))
            .append(pair(70, "1"))
        sb.append(pair(0, "STYLE")).append(pair(5, styleHandle))
            .append(pair(100, "AcDbSymbolTableRecord"))
            .append(pair(100, "AcDbTextStyleTableRecord"))
            .append(pair(2, "Standard"))
            .append(pair(70, "0"))
            .append(pair(40, "0.0"))
            .append(pair(41, "1.0"))
            .append(pair(50, "0.0"))
            .append(pair(71, "0"))
            .append(pair(42, "0.2"))
            .append(pair(3, "txt"))
            .append(pair(4, ""))
        sb.append(pair(0, "ENDTAB"))
    }

    private fun writeViewTable(sb: StringBuilder) {
        val tableHandle = allocHandle()
        sb.append(pair(0, "TABLE")).append(pair(2, "VIEW"))
            .append(pair(5, tableHandle))
            .append(pair(100, "AcDbSymbolTable"))
            .append(pair(70, "0"))
        sb.append(pair(0, "ENDTAB"))
    }

    private fun writeUcsTable(sb: StringBuilder) {
        val tableHandle = allocHandle()
        sb.append(pair(0, "TABLE")).append(pair(2, "UCS"))
            .append(pair(5, tableHandle))
            .append(pair(100, "AcDbSymbolTable"))
            .append(pair(70, "0"))
        sb.append(pair(0, "ENDTAB"))
    }

    private fun writeAppidTable(sb: StringBuilder) {
        val tableHandle = allocHandle()
        val appidHandle = allocHandle()
        sb.append(pair(0, "TABLE")).append(pair(2, "APPID"))
            .append(pair(5, tableHandle))
            .append(pair(100, "AcDbSymbolTable"))
            .append(pair(70, "1"))
        sb.append(pair(0, "APPID")).append(pair(5, appidHandle))
            .append(pair(100, "AcDbSymbolTableRecord"))
            .append(pair(100, "AcDbRegAppTableRecord"))
            .append(pair(2, "ACAD"))
            .append(pair(70, "0"))
        sb.append(pair(0, "ENDTAB"))
    }

    private fun writeDimstyleTable(sb: StringBuilder) {
        val tableHandle = allocHandle()
        val dimHandle = allocHandle()
        sb.append(pair(0, "TABLE")).append(pair(2, "DIMSTYLE"))
            .append(pair(5, tableHandle))
            .append(pair(100, "AcDbSymbolTable"))
            .append(pair(70, "1"))
            .append(pair(100, "AcDbDimStyleTable"))
            .append(pair(71, "0"))
        // Standard dimension style
        sb.append(pair(0, "DIMSTYLE")).append(pair(105, dimHandle))
            .append(pair(100, "AcDbSymbolTableRecord"))
            .append(pair(100, "AcDbDimStyleTableRecord"))
            .append(pair(2, "Standard"))
            .append(pair(70, "0"))
            .append(pair(3, ""))
            .append(pair(4, ""))
            .append(pair(40, 1.0))    // DIMSCALE
            .append(pair(41, 3.0))    // DIMASZ
            .append(pair(42, 0.0))    // DIMEXO
            .append(pair(43, 1.5))    // DIMEXE
            .append(pair(44, 8.0))    // DIMDLI
            .append(pair(45, 0.0))    // DIMRND
            .append(pair(46, 0.0))    // DIMDLE
            .append(pair(47, 0.0))    // DIMTP
            .append(pair(48, 0.0))    // DIMTM
            .append(pair(140, 3.5))    // DIMTXT
            .append(pair(141, 2.5))    // DIMCEN
            .append(pair(142, 0.0))    // DIMTSZ
            .append(pair(143, 0.0))    // DIMALTF
            .append(pair(144, 1.0))    // DIMLFAC
            .append(pair(145, 0.0))    // DIMTVP
            .append(pair(146, 1.0))    // DIMTFAC
            .append(pair(147, 0.625))  // DIMGAP
            .append(pair(71, 0))       // DIMTOL
            .append(pair(72, 0))       // DIMLIM
            .append(pair(73, 0))       // DIMTIH
            .append(pair(74, 0))       // DIMTOH
            .append(pair(75, 0))       // DIMSE1
            .append(pair(76, 0))       // DIMSE2
            .append(pair(77, 1))       // DIMTAD
            .append(pair(78, 0))       // DIMZIN
            .append(pair(79, 0))       // DIMAZIN
            .append(pair(170, 0))      // DIMALT
            .append(pair(171, 3))      // DIMALTD
            .append(pair(172, 0))      // DIMTOFL
            .append(pair(173, 0))      // DIMSAH
            .append(pair(174, 0))      // DIMTIX
            .append(pair(175, 0))      // DIMSOXD
            .append(pair(176, 0))      // DIMCLRD
            .append(pair(177, 0))      // DIMCLRE
            .append(pair(178, 0))      // DIMCLRT
            .append(pair(271, 2))      // DIMADEC
            .append(pair(272, 2))      // DIMDEC
            .append(pair(273, 0))      // DIMTDEC
            .append(pair(274, 2))      // DIMALU
            .append(pair(275, 0))      // DIMUNIT
            .append(pair(276, 0))      // DIMAUNIT
            .append(pair(277, 2))      // DIMFRAC
            .append(pair(278, 0))      // DIMLUNIT
            .append(pair(279, 0))      // DIMDSEP
            .append(pair(280, 0))      // DIMTMOVE
            .append(pair(281, 0))      // DIMJUST
            .append(pair(282, 0))      // DIMSD1
            .append(pair(283, 0))      // DIMSD2
            .append(pair(284, 0))      // DIMTOLJ
            .append(pair(285, 0))      // DIMTZIN
            .append(pair(286, 0))      // DIMALTZ
            .append(pair(287, 0))      // DIMALTTZ
            .append(pair(288, 0))      // DIMUPT
        sb.append(pair(0, "ENDTAB"))
    }

    // ------------------------------------------------------------------
    // BLOCKS section
    // ------------------------------------------------------------------

    private fun writeBlocks(sb: StringBuilder) {
        val modelBlockHandle = allocHandle()
        val modelEnblkHandle = allocHandle()
        val paperBlockHandle = allocHandle()
        val paperEnblkHandle = allocHandle()

        sb.append(pair(0, "SECTION")).append(pair(2, "BLOCKS"))

        // *Model_Space
        sb.append(pair(0, "BLOCK")).append(pair(8, "0"))
            .append(pair(2, "*Model_Space"))
            .append(pair(70, "0"))
            .append(pair(10, 0.0)).append(pair(20, 0.0)).append(pair(30, 0.0))
            .append(pair(3, "*Model_Space")).append(pair(1, ""))
        sb.append(pair(0, "ENDBLK")).append(pair(5, modelEnblkHandle)).append(pair(8, "0"))

        // *Paper_Space
        sb.append(pair(0, "BLOCK")).append(pair(8, "0"))
            .append(pair(2, "*Paper_Space"))
            .append(pair(70, "0"))
            .append(pair(10, 0.0)).append(pair(20, 0.0)).append(pair(30, 0.0))
            .append(pair(3, "*Paper_Space")).append(pair(1, ""))
        sb.append(pair(0, "ENDBLK")).append(pair(5, paperEnblkHandle)).append(pair(8, "0"))

        sb.append(pair(0, "ENDSEC"))
    }

    // ------------------------------------------------------------------
    // OBJECTS section
    // ------------------------------------------------------------------

    private fun writeObjects(sb: StringBuilder) {
        val dictHandle = allocHandle()
        val dict2Handle = allocHandle()
        sb.append(pair(0, "SECTION")).append(pair(2, "OBJECTS"))
        // Root DICTIONARY
        sb.append(pair(0, "DICTIONARY")).append(pair(5, dictHandle))
            .append(pair(100, "AcDbDictionary"))
            .append(pair(281, "1"))
            .append(pair(3, "ACAD_GROUP")).append(pair(350, dict2Handle))
        // ACAD_GROUP sub-dictionary
        sb.append(pair(0, "DICTIONARY")).append(pair(5, dict2Handle))
            .append(pair(100, "AcDbDictionary"))
            .append(pair(281, "1"))
        sb.append(pair(0, "ENDSEC"))
    }

    // ============================================================
    // PUBLIC ENTITY DRAWING API
    // ============================================================

    // --- LINE ---
    fun addLine(
        x1: Double, y1: Double, x2: Double, y2: Double,
        layer: String = "0",
        color: Int? = null,
        lineType: String? = null,
        lineWeight: Int? = null
    ) {
        val h = allocHandle()
        val eb = entityBuffer
        eb.append(pair(0, "LINE")).append(pair(5, h))
        eb.append(pair(100, "AcDbEntity"))
        eb.append(pair(8, layer))
        if (color != null) eb.append(pair(62, color.toString()))
        if (lineType != null) eb.append(pair(6, lineType))
        if (lineWeight != null) eb.append(pair(370, lineWeight.toString()))
        eb.append(pair(100, "AcDbLine"))
        eb.append(pair(10, x1)).append(pair(20, y1)).append(pair(30, 0.0))
        eb.append(pair(11, x2)).append(pair(21, y2)).append(pair(31, 0.0))
    }

    // --- LWPOLYLINE ---
    data class PolyPoint(val x: Double, val y: Double, val bulge: Double = 0.0)

    fun addPolyline(
        points: List<PolyPoint>,
        closed: Boolean = false,
        layer: String = "0",
        color: Int? = null,
        lineType: String? = null,
        lineWidth: Double = 0.0
    ) {
        if (points.size < 2) return
        val h = allocHandle()
        val eb = entityBuffer
        eb.append(pair(0, "LWPOLYLINE")).append(pair(5, h))
        eb.append(pair(100, "AcDbEntity"))
        eb.append(pair(8, layer))
        if (color != null) eb.append(pair(62, color.toString()))
        if (lineType != null) eb.append(pair(6, lineType))
        eb.append(pair(100, "AcDbPolyline"))
        eb.append(pair(90, points.size.toString()))
        eb.append(pair(70, if (closed) 1 else 0))
        if (lineWidth != 0.0) eb.append(pair(43, lineWidth))
        for (pt in points) {
            eb.append(pair(10, pt.x)).append(pair(20, pt.y))
            if (pt.bulge != 0.0) eb.append(pair(42, pt.bulge))
        }
    }

    // --- CIRCLE ---
    fun addCircle(
        cx: Double, cy: Double, radius: Double,
        layer: String = "0",
        color: Int? = null
    ) {
        val h = allocHandle()
        val eb = entityBuffer
        eb.append(pair(0, "CIRCLE")).append(pair(5, h))
        eb.append(pair(100, "AcDbEntity")).append(pair(8, layer))
        if (color != null) eb.append(pair(62, color.toString()))
        eb.append(pair(100, "AcDbCircle"))
        eb.append(pair(10, cx)).append(pair(20, cy)).append(pair(30, 0.0))
        eb.append(pair(40, radius))
    }

    // --- ARC ---
    fun addArc(
        cx: Double, cy: Double, radius: Double,
        startAngleDeg: Double, endAngleDeg: Double,
        layer: String = "0",
        color: Int? = null
    ) {
        val h = allocHandle()
        val eb = entityBuffer
        eb.append(pair(0, "ARC")).append(pair(5, h))
        eb.append(pair(100, "AcDbEntity")).append(pair(8, layer))
        if (color != null) eb.append(pair(62, color.toString()))
        eb.append(pair(100, "AcDbArc"))
        eb.append(pair(10, cx)).append(pair(20, cy)).append(pair(30, 0.0))
        eb.append(pair(40, radius))
        eb.append(pair(50, startAngleDeg))
        eb.append(pair(51, endAngleDeg))
    }

    // --- TEXT ---
    fun addText(
        text: String,
        x: Double, y: Double,
        height: Double = 3.5,
        layer: String = "TEXT",
        color: Int? = null,
        rotationDeg: Double = 0.0,
        hJustify: Int = 0,  // 0=left, 1=center, 2=right, 3=aligned, etc.
        vJustify: Int = 0   // 0=baseline, 1=bottom, 2=middle, 3=top
    ) {
        val h = allocHandle()
        val eb = entityBuffer
        eb.append(pair(0, "TEXT")).append(pair(5, h))
        eb.append(pair(100, "AcDbEntity")).append(pair(8, layer))
        if (color != null) eb.append(pair(62, color.toString()))
        eb.append(pair(100, "AcDbText"))
        eb.append(pair(10, x)).append(pair(20, y)).append(pair(30, 0.0))
        eb.append(pair(40, height))
        eb.append(pair(1, text))
        eb.append(pair(50, rotationDeg))
        eb.append(pair(7, "Standard"))
        eb.append(pair(72, hJustify.toString()))
        // Second x point for horizontal justification
        if (hJustify != 0) {
            eb.append(pair(11, x)).append(pair(21, y)).append(pair(31, 0.0))
        }
        eb.append(pair(100, "AcDbText"))
        eb.append(pair(73, vJustify.toString()))
    }

    // --- MTEXT ---
    fun addMText(
        text: String,
        x: Double, y: Double,
        height: Double = 3.5,
        width: Double = 100.0,
        layer: String = "TEXT",
        color: Int? = null,
        attachmentPoint: Int = 1  // 1=top left, 2=top center, ... 9=middle center
    ) {
        val h = allocHandle()
        val eb = entityBuffer
        eb.append(pair(0, "MTEXT")).append(pair(5, h))
        eb.append(pair(100, "AcDbEntity")).append(pair(8, layer))
        if (color != null) eb.append(pair(62, color.toString()))
        eb.append(pair(100, "AcDbMText"))
        eb.append(pair(10, x)).append(pair(20, y)).append(pair(30, 0.0))
        eb.append(pair(40, height))
        eb.append(pair(41, width))
        eb.append(pair(71, attachmentPoint.toString()))
        eb.append(pair(1, text))
        eb.append(pair(7, "Standard"))
    }

    // --- DIMENSION (Linear / Aligned) ---
    fun addLinearDimension(
        x1: Double, y1: Double, x2: Double, y2: Double,
        dimLineX: Double, dimLineY: Double,
        textOverride: String? = null,
        layer: String = "DIMENSIONS",
        color: Int? = null
    ) {
        val h = allocHandle()
        val eb = entityBuffer
        // Determine dimension type: 0=linear, 1=aligned
        val dx = x2 - x1
        val dy = y2 - y1
        val isAligned = (dx != 0.0 && dy != 0.0)
        val dimType = if (isAligned) 1 else 0

        eb.append(pair(0, "DIMENSION")).append(pair(5, h))
        eb.append(pair(100, "AcDbEntity")).append(pair(8, layer))
        if (color != null) eb.append(pair(62, color.toString()))
        eb.append(pair(100, "AcDbDimension"))
        eb.append(pair(10, dimLineX)).append(pair(20, dimLineY)).append(pair(30, 0.0))
        // Middle of dimension line for text
        eb.append(pair(11, (x1 + x2) / 2.0)).append(pair(21, dimLineY)).append(pair(31, 0.0))
        if (textOverride != null) {
            eb.append(pair(1, textOverride))
        } else {
            eb.append(pair(1, "<>"))
        }
        eb.append(pair(3, "Standard"))
        eb.append(pair(70, dimType.toString()))

        eb.append(pair(100, if (dimType == 1) "AcDb2LineAngularDimension" else "AcDbAlignedDimension"))
        // Definition point 1
        eb.append(pair(13, x1)).append(pair(23, y1)).append(pair(33, 0.0))
        // Definition point 2
        eb.append(pair(14, x2)).append(pair(24, y2)).append(pair(34, 0.0))
        // Rotation angle for linear dimensions (0 for aligned)
        val angle = Math.toDegrees(Math.atan2(dy, dx))
        eb.append(pair(50, angle))
        // Explicit dimension line position
        eb.append(pair(52, angle))
    }

    // --- HATCH ---
    fun addHatch(
        boundaryPoints: List<List<PolyPoint>>,
        patternName: String = "ANSI31",
        scale: Double = 1.0,
        angle: Double = 0.0,
        layer: String = "HATCH",
        color: Int? = null
    ) {
        val h = allocHandle()
        val eb = entityBuffer
        val totalLoops = boundaryPoints.size

        eb.append(pair(0, "HATCH")).append(pair(5, h))
        eb.append(pair(100, "AcDbEntity")).append(pair(8, layer))
        if (color != null) eb.append(pair(62, color.toString()))
        eb.append(pair(100, "AcDbHatch"))
        // Elevation
        eb.append(pair(10, 0.0)).append(pair(20, 0.0)).append(pair(30, 0.0))
        // Scale
        eb.append(pair(41, scale))
        // Pattern angle
        eb.append(pair(52, angle))
        // Hatch style: 0=Nested, 1=Outer, 2=Ignore
        eb.append(pair(71, "0"))
        // Pattern type: 0=User-defined, 1=Predefined, 2=Custom
        eb.append(pair(75, "1"))
        // Pattern name
        eb.append(pair(2, patternName))
        // Solid fill flag (0 = not solid, 1 = solid)
        eb.append(pair(70, if (patternName == "SOLID") "1" else "0"))
        // Number of boundary paths
        eb.append(pair(91, totalLoops.toString()))

        // Write each boundary loop
        for (loopPts in boundaryPoints) {
            val isClosed = true
            eb.append(pair(92, (if (isClosed) 1 else 0).toString()))
            // Number of edges
            val edges = if (isClosed) loopPts.size else loopPts.size - 1
            eb.append(pair(93, edges.toString()))

            // Each edge is a LINE segment
            for (i in 0 until edges) {
                val p1 = loopPts[i]
                val p2 = if (isClosed) loopPts[(i + 1) % loopPts.size] else loopPts[i + 1]
                // Edge type: 1=Line
                eb.append(pair(72, "1"))
                eb.append(pair(10, p1.x)).append(pair(20, p1.y))
                eb.append(pair(11, p2.x)).append(pair(21, p2.y))
            }
        }
    }

    // --- SOLID (filled triangle/quad) ---
    fun addSolid(
        x1: Double, y1: Double,
        x2: Double, y2: Double,
        x3: Double, y3: Double,
        x4: Double = x3, y4: Double = y3,
        layer: String = "HATCH",
        color: Int? = null
    ) {
        val h = allocHandle()
        val eb = entityBuffer
        eb.append(pair(0, "SOLID")).append(pair(5, h))
        eb.append(pair(100, "AcDbEntity")).append(pair(8, layer))
        if (color != null) eb.append(pair(62, color.toString()))
        eb.append(pair(100, "AcDbTrace"))
        eb.append(pair(10, x1)).append(pair(20, y1)).append(pair(30, 0.0))
        eb.append(pair(11, x2)).append(pair(21, y2)).append(pair(31, 0.0))
        eb.append(pair(12, x3)).append(pair(22, y3)).append(pair(32, 0.0))
        eb.append(pair(13, x4)).append(pair(23, y4)).append(pair(33, 0.0))
    }

    // --- POINT ---
    fun addPoint(
        x: Double, y: Double,
        layer: String = "0",
        color: Int? = null
    ) {
        val h = allocHandle()
        val eb = entityBuffer
        eb.append(pair(0, "POINT")).append(pair(5, h))
        eb.append(pair(100, "AcDbEntity")).append(pair(8, layer))
        if (color != null) eb.append(pair(62, color.toString()))
        eb.append(pair(100, "AcDbPoint"))
        eb.append(pair(10, x)).append(pair(20, y)).append(pair(30, 0.0))
    }

    // --- SPLINE ---
    fun addSpline(
        controlPoints: List<Pair<Double, Double>>,
        fitPoints: List<Pair<Double, Double>>? = null,
        degree: Int = 3,
        closed: Boolean = false,
        layer: String = "0",
        color: Int? = null
    ) {
        if (controlPoints.size < 2) return
        val h = allocHandle()
        val eb = entityBuffer
        eb.append(pair(0, "SPLINE")).append(pair(5, h))
        eb.append(pair(100, "AcDbEntity")).append(pair(8, layer))
        if (color != null) eb.append(pair(62, color.toString()))
        eb.append(pair(100, "AcDbSpline"))
        // Flags: 1=closed, 2=periodic, 4=rational, 8=planar, 16=linear
        var flags = 8  // planar
        if (closed) flags = flags or 1
        eb.append(pair(70, flags.toString()))
        eb.append(pair(71, degree.toString()))
        // Number of knots = n + degree + 1
        val n = controlPoints.size
        val numKnots = n + degree + 1
        eb.append(pair(72, numKnots.toString()))
        eb.append(pair(73, n.toString()))
        if (fitPoints != null) {
            eb.append(pair(74, fitPoints.size.toString()))
        } else {
            eb.append(pair(74, "0"))
        }
        // Knot values (uniform)
        for (i in 0 until numKnots) {
            eb.append(pair(40, i.toDouble()))
        }
        // Control points
        for ((cx, cy) in controlPoints) {
            eb.append(pair(10, cx)).append(pair(20, cy)).append(pair(30, 0.0))
        }
        // Fit points
        if (fitPoints != null) {
            for ((fx, fy) in fitPoints) {
                eb.append(pair(11, fx)).append(pair(21, fy)).append(pair(31, 0.0))
            }
        }
    }

    // --- ELLIPSE ---
    fun addEllipse(
        cx: Double, cy: Double,
        majorAxisX: Double, majorAxisY: Double,
        minorAxisRatio: Double,
        startAngleDeg: Double = 0.0,
        endAngleDeg: Double = 360.0,
        layer: String = "0",
        color: Int? = null
    ) {
        val h = allocHandle()
        val eb = entityBuffer
        eb.append(pair(0, "ELLIPSE")).append(pair(5, h))
        eb.append(pair(100, "AcDbEntity")).append(pair(8, layer))
        if (color != null) eb.append(pair(62, color.toString()))
        eb.append(pair(100, "AcDbEllipse"))
        eb.append(pair(10, cx)).append(pair(20, cy)).append(pair(30, 0.0))
        eb.append(pair(11, majorAxisX)).append(pair(21, majorAxisY)).append(pair(31, 0.0))
        eb.append(pair(40, minorAxisRatio))
        eb.append(pair(41, startAngleDeg * 3.141592653589793 / 180.0))
        eb.append(pair(42, endAngleDeg * 3.141592653589793 / 180.0))
    }

    // --- INSERT (block reference) ---
    fun addInsert(
        blockName: String,
        x: Double, y: Double,
        scaleX: Double = 1.0,
        scaleY: Double = 1.0,
        rotationDeg: Double = 0.0,
        layer: String = "0",
        color: Int? = null
    ) {
        val h = allocHandle()
        val eb = entityBuffer
        eb.append(pair(0, "INSERT")).append(pair(5, h))
        eb.append(pair(100, "AcDbEntity")).append(pair(8, layer))
        if (color != null) eb.append(pair(62, color.toString()))
        eb.append(pair(100, "AcDbBlockReference"))
        eb.append(pair(2, blockName))
        eb.append(pair(10, x)).append(pair(20, y)).append(pair(30, 0.0))
        eb.append(pair(41, scaleX)).append(pair(42, scaleY)).append(pair(43, 1.0))
        eb.append(pair(50, rotationDeg))
    }

    // ============================================================
    // CONVENIENCE METHODS
    // ============================================================

    /** Draw a rectangle as a closed polyline. */
    fun addRectangle(
        x: Double, y: Double, width: Double, height: Double,
        layer: String = "0",
        color: Int? = null,
        lineType: String? = null
    ) {
        addPolyline(
            listOf(
                PolyPoint(x, y),
                PolyPoint(x + width, y),
                PolyPoint(x + width, y + height),
                PolyPoint(x, y + height)
            ),
            closed = true, layer = layer, color = color, lineType = lineType
        )
    }

    /** Draw a centerline cross through a point with a given half-size. */
    fun addCenterCross(
        cx: Double, cy: Double, halfSize: Double,
        layer: String = "CENTER_LINE"
    ) {
        addLine(cx - halfSize, cy, cx + halfSize, cy, layer = layer, lineType = "CENTER")
        addLine(cx, cy - halfSize, cx, cy + halfSize, layer = layer, lineType = "CENTER")
    }

    /** Draw a rebar circle symbol at a position. */
    fun addRebarSymbol(
        cx: Double, cy: Double, radius: Double = 2.0,
        layer: String = "REBAR"
    ) {
        addCircle(cx, cy, radius, layer = layer, color = 1)
    }

    /** Draw a horizontal dimension line with extension lines. */
    fun addHorizontalDimension(
        x1: Double, x2: Double, y: Double, dimY: Double,
        textOverride: String? = null,
        extLineExtend: Double = 2.0
    ) {
        val layer = "DIMENSIONS"
        // Extension lines
        addLine(x1, y - extLineExtend, x1, dimY + extLineExtend, layer = layer)
        addLine(x2, y - extLineExtend, x2, dimY + extLineExtend, layer = layer)
        // Dimension line
        addLine(x1, dimY, x2, dimY, layer = layer)
        // Arrows approximated by short lines
        val arrowLen = 2.5
        addLine(x1, dimY, x1 + arrowLen, dimY + arrowLen * 0.3, layer = layer)
        addLine(x1, dimY, x1 + arrowLen, dimY - arrowLen * 0.3, layer = layer)
        addLine(x2, dimY, x2 - arrowLen, dimY + arrowLen * 0.3, layer = layer)
        addLine(x2, dimY, x2 - arrowLen, dimY - arrowLen * 0.3, layer = layer)
        // Text
        val text = textOverride ?: formatDimension(x2 - x1)
        val textX = (x1 + x2) / 2.0
        addText(text, textX, dimY + 1.5, height = 3.0, layer = layer, hJustify = 1)
    }

    /** Draw a vertical dimension line with extension lines. */
    fun addVerticalDimension(
        y1: Double, y2: Double, x: Double, dimX: Double,
        textOverride: String? = null,
        extLineExtend: Double = 2.0
    ) {
        val layer = "DIMENSIONS"
        addLine(x - extLineExtend, y1, dimX + extLineExtend, y1, layer = layer)
        addLine(x - extLineExtend, y2, dimX + extLineExtend, y2, layer = layer)
        addLine(dimX, y1, dimX, y2, layer = layer)
        val arrowLen = 2.5
        addLine(dimX, y1, dimX + arrowLen * 0.3, y1 + arrowLen, layer = layer)
        addLine(dimX, y1, dimX - arrowLen * 0.3, y1 + arrowLen, layer = layer)
        addLine(dimX, y2, dimX + arrowLen * 0.3, y2 - arrowLen, layer = layer)
        addLine(dimX, y2, dimX - arrowLen * 0.3, y2 - arrowLen, layer = layer)
        val text = textOverride ?: formatDimension(y2 - y1)
        val textY = (y1 + y2) / 2.0
        addText(text, dimX + 2.0, textY, height = 3.0, layer = layer, vJustify = 2)
    }

    /** Draw a leader line with text. */
    fun addLeader(
        x1: Double, y1: Double,
        x2: Double, y2: Double,
        text: String,
        layer: String = "DIMENSIONS"
    ) {
        addLine(x1, y1, x2, y2, layer = layer)
        // Arrow at start
        val dx = x2 - x1
        val dy = y2 - y1
        val len = Math.sqrt(dx * dx + dy * dy)
        if (len > 0) {
            val ux = dx / len
            val uy = dy / len
            val aLen = 2.5
            val aWid = 0.8
            addLine(x1, y1, x1 + ux * aLen + uy * aWid, y1 + uy * aLen - ux * aWid, layer = layer)
            addLine(x1, y1, x1 + ux * aLen - uy * aWid, y1 + uy * aLen + ux * aWid, layer = layer)
        }
        // Text at end
        addText(text, x2 + 2.0, y2, height = 3.0, layer = layer)
    }

    /** Draw section hatching for a rectangular area. */
    fun addRectangleHatch(
        x: Double, y: Double, width: Double, height: Double,
        patternName: String = "ANSI31",
        scale: Double = 3.0,
        layer: String = "HATCH",
        color: Int? = null
    ) {
        val pts = listOf(
            PolyPoint(x, y),
            PolyPoint(x + width, y),
            PolyPoint(x + width, y + height),
            PolyPoint(x, y + height)
        )
        addHatch(listOf(pts), patternName = patternName, scale = scale, layer = layer, color = color)
    }

    /** Draw a title block in the bottom-right corner. */
    fun addTitleBlock(
        title: String,
        subtitle: String = "",
        scale: String = "1:1",
        date: String = "",
        sheetNum: String = "",
        totalSheets: String = "1",
        originX: Double = 0.0,
        originY: Double = 0.0,
        width: Double = 180.0,
        height: Double = 50.0
    ) {
        val layer = "TEXT"
        val bx = originX
        val by = originY

        // Outer border
        addRectangle(bx, by, width, height, layer = "OUTLINE", color = 7)
        // Inner border
        addRectangle(bx + 1, by + 1, width - 2, height - 2, layer = "OUTLINE", color = 7)

        // Vertical divider for left panel
        addLine(bx + 80, by + 1, bx + 80, by + height - 1, layer = "OUTLINE", color = 7)
        // Horizontal dividers
        addLine(bx + 1, by + height - 15, bx + width - 1, by + height - 15, layer = "OUTLINE", color = 7)
        addLine(bx + 1, by + height - 30, bx + width - 1, by + height - 30, layer = "OUTLINE", color = 7)

        // Title (centered in left panel)
        addText(title, bx + 40, by + height - 7, height = 5.0, layer = layer, hJustify = 1, vJustify = 2)
        // Subtitle
        if (subtitle.isNotEmpty()) {
            addText(subtitle, bx + 40, by + height - 22, height = 3.5, layer = layer, hJustify = 1, vJustify = 2)
        }
        // Scale
        addText("Scale: $scale", bx + 40, by + height - 37, height = 3.0, layer = layer, hJustify = 1, vJustify = 2)

        // Right panel labels
        addText("Sheet: $sheetNum / $totalSheets", bx + 80 + (width - 80 - 2) / 2, by + height - 7, height = 3.0, layer = layer, hJustify = 1, vJustify = 2)
        if (date.isNotEmpty()) {
            addText("Date: $date", bx + 80 + (width - 80 - 2) / 2, by + height - 22, height = 3.0, layer = layer, hJustify = 1, vJustify = 2)
        }
        addText("CivilEG - Structural Design", bx + 80 + (width - 80 - 2) / 2, by + height - 37, height = 2.5, layer = layer, hJustify = 1, vJustify = 2)
    }

    /** Draw a drawing border frame. */
    fun addDrawingFrame(
        margin: Double = 10.0,
        sheetWidth: Double = 841.0,   // A1
        sheetHeight: Double = 594.0
    ) {
        addRectangle(margin, margin, sheetWidth - 2 * margin, sheetHeight - 2 * margin,
            layer = "OUTLINE", color = 7)
    }

    // ============================================================
    // UTILITY
    // ============================================================

    private fun formatDimension(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            "${value.toLong()}"
        } else {
            String.format("%.1f", value)
        }
    }

    /** Add a custom layer if it does not already exist. */
    fun addLayer(name: String, color: Int, lineType: String = "CONTINUOUS") {
        if (layers.none { it.name == name }) {
            layers.add(LayerDef(name, color, lineType))
        }
    }

    /** Clear all entities (keeps layers and handles). */
    fun clearEntities() {
        entityBuffer.clear()
    }

    /** Reset the entire engine for a new drawing. */
    fun reset() {
        nextHandle = 0x10
        layers = defaultLayers.toMutableList()
        entityBuffer.clear()
    }

    // ============================================================
    // BUILD FINAL DXF
    // ============================================================

    /** Generate the complete DXF file content as a string. */
    fun build(): String {
        val sb = StringBuilder(8192)
        writeHeader(sb)
        writeClasses(sb)
        writeTables(sb)
        writeBlocks(sb)
        // ENTITIES section
        sb.append(pair(0, "SECTION")).append(pair(2, "ENTITIES"))
        sb.append(entityBuffer)
        sb.append(pair(0, "ENDSEC"))
        writeObjects(sb)
        sb.append(pair(0, "EOF"))
        return sb.toString()
    }
}
