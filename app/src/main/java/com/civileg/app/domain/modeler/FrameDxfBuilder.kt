package com.civileg.app.domain.modeler

import com.civileg.app.domain.entities.Dimension
import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.MemberLoad
import com.civileg.app.domain.entities.MemberLoadType
import com.civileg.app.domain.entities.NodalLoad
import com.civileg.app.domain.entities.SlabPanel
import com.civileg.app.domain.entities.SupportType
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object FrameDxfBuilder {

private const val GRID = "GRID"
private const val SLABS = "SLABS"
private const val MEMBERS = "MEMBERS"
private const val JOINTS = "JOINTS"
private const val SUPPORTS = "SUPPORTS"
private const val TEXT = "TEXT"
private const val DIMENSIONS = "DIMENSIONS"
private const val MM = 1000.0

    private data class Layer(val name: String, val color: Int)
    private data class Bounds(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double)

    private val invalidLayerChars = Regex("[^A-Z0-9_]+")

    fun build(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        slabs: List<SlabPanel>,
        dimensions: List<Dimension> = emptyList(),
        layers: ModelerLayerVisibility = ModelerLayerVisibility()
    ): String {
        val nodeById = nodes.associateBy { it.id }
        val loadLayers = buildLoadLayers(
            (nodalLoads.map { it.loadCase } + memberLoads.map { it.loadCase })
                .map { normalizeLoadCase(it) }
                .distinct()
        )
        val baseLayers = listOf(
            Layer("0", 7),
            Layer(GRID, 8),
            Layer(SLABS, 4),
            Layer(MEMBERS, 5),
            Layer(JOINTS, 7),
            Layer(SUPPORTS, 3),
            Layer(TEXT, 7),
            Layer(DIMENSIONS, 6)
        )

        val writer = DxfWriter()
        val bounds = modelBounds(nodes)
        writeHeader(writer, bounds)
        val modelSpaceHandle = writeTables(writer, baseLayers + loadLayers.values)
        writeBlocks(writer, modelSpaceHandle)
        writer.ownerHandle = modelSpaceHandle

        writer.pair(0, "SECTION")
        writer.pair(2, "ENTITIES")

        if (nodes.isNotEmpty() && bounds != null) {
            if (layers.grid) writeGrid(writer, bounds)
            if (layers.slabs) writeSlabs(writer, slabs, nodeById)
            if (layers.members) writeMembers(writer, members, nodeById)
            if (layers.supports) writeSupports(writer, nodes)
            if (layers.joints) writeJoints(writer, nodes)
            if (layers.loads) {
                writeNodalLoads(writer, nodalLoads, nodeById, loadLayers)
                writeMemberLoads(writer, memberLoads, members, nodeById, loadLayers)
            }
            if (layers.dimensions) writeDimensions(writer, dimensions, nodeById)
            writer.text(
                layer = TEXT,
                x = (bounds.minX - 1.0) * MM,
                y = (bounds.maxY + 2.4) * MM,
                height = 320.0,
                value = "FRAME MODEL"
            )
            writer.text(
                layer = TEXT,
                x = (bounds.minX - 1.0) * MM,
                y = (bounds.maxY + 1.8) * MM,
                height = 170.0,
                value = "All dimensions in mm"
            )
        }

        writer.pair(0, "ENDSEC")
        writeObjects(writer)
        writer.pair(0, "EOF")
        return writer.toString()
    }

    private fun writeHeader(writer: DxfWriter, bounds: Bounds?) {
        val minX = ((bounds?.minX ?: 0.0) - 2.0) * MM
        val maxX = ((bounds?.maxX ?: 0.0) + 2.0) * MM
        val minY = ((bounds?.minY ?: 0.0) - 2.0) * MM
        val maxY = ((bounds?.maxY ?: 0.0) + 4.0) * MM

        writer.pair(0, "SECTION")
        writer.pair(2, "HEADER")
        writer.pair(9, "\$ACADVER")
        writer.pair(1, "AC1027") // AutoCAD 2013 (R2013)
        writer.pair(9, "\$HANDSEED")
        writer.pair(5, "FFFFFFFF")
        writer.pair(9, "\$INSUNITS")
        writer.pair(70, 4) // 4 = millimeters
        writer.pair(9, "\$EXTMIN")
        writer.pair(10, minX)
        writer.pair(20, minY)
        writer.pair(30, 0.0)
        writer.pair(9, "\$EXTMAX")
        writer.pair(10, maxX)
        writer.pair(20, maxY)
        writer.pair(30, 0.0)
        writer.pair(9, "\$LIMMIN")
        writer.pair(10, minX)
        writer.pair(20, minY)
        writer.pair(9, "\$LIMMAX")
        writer.pair(10, maxX)
        writer.pair(20, maxY)
        writer.pair(9, "\$ORTHOMODE")
        writer.pair(70, 0)
        writer.pair(9, "\$REGENMODE")
        writer.pair(70, 1)
        writer.pair(9, "\$FILLETRAD")
        writer.pair(40, 0.0)
        writer.pair(9, "\$ANGBASE")
        writer.pair(50, 0.0)
        writer.pair(9, "\$ANGDIR")
        writer.pair(70, 0)
        writer.pair(9, "\$PDMODE")
        writer.pair(70, 0)
        writer.pair(9, "\$PDSIZE")
        writer.pair(40, 0.0)
        writer.pair(9, "\$DWGCODEPAGE")
        writer.pair(3, "ANSI_1252")
        writer.pair(0, "ENDSEC")
    }

    private fun writeTables(writer: DxfWriter, layers: List<Layer>): String {
        writer.pair(0, "SECTION")
        writer.pair(2, "TABLES")

        val ltypeTable = writer.newHandle()
        writer.tableHeader("LTYPE", ltypeTable, 3)
        writer.linetype(ltypeTable, "Continuous", "Solid line", 0.0, emptyList())
        writer.linetype(ltypeTable, "DASHED", "Dashed __ __ __", 12.7, listOf(-6.35, 6.35))
        writer.linetype(ltypeTable, "DOTTED", "Dotted . . .", 6.35, listOf(0.0, -6.35))
        writer.pair(0, "ENDTAB")

        val layerTable = writer.newHandle()
        writer.tableHeader("LAYER", layerTable, layers.size)
        layers.forEach { layer ->
            writer.pair(0, "LAYER")
            writer.pair(5, writer.newHandle())
            writer.pair(330, layerTable)
            writer.pair(100, "AcDbSymbolTableRecord")
            writer.pair(100, "AcDbLayerTableRecord")
            writer.pair(2, layer.name)
            writer.pair(70, 0)
            writer.pair(62, layer.color)
            writer.pair(6, "Continuous")
        }
        writer.pair(0, "ENDTAB")

        val styleTable = writer.newHandle()
        writer.tableHeader("STYLE", styleTable, 1)
        writer.pair(0, "STYLE")
        writer.pair(5, writer.newHandle())
        writer.pair(330, styleTable)
        writer.pair(100, "AcDbSymbolTableRecord")
        writer.pair(100, "AcDbTextStyleTableRecord")
        writer.pair(2, "STANDARD")
        writer.pair(70, 0)
        writer.pair(40, 0.0)
        writer.pair(41, 1.0)
        writer.pair(50, 0.0)
        writer.pair(71, 0)
        writer.pair(42, 2.5)
        writer.pair(3, "txt")
        writer.pair(4, "")
        writer.pair(0, "ENDTAB")

        val appIdTable = writer.newHandle()
        writer.tableHeader("APPID", appIdTable, 1)
        writer.pair(0, "APPID")
        writer.pair(5, writer.newHandle())
        writer.pair(330, appIdTable)
        writer.pair(100, "AcDbSymbolTableRecord")
        writer.pair(100, "AcDbRegAppTableRecord")
        writer.pair(2, "ACAD")
        writer.pair(70, 0)
        writer.pair(0, "ENDTAB")

        val blockRecordTable = writer.newHandle()
        writer.tableHeader("BLOCK_RECORD", blockRecordTable, 1)
        val modelSpaceHandle = writer.newHandle()
        writer.pair(0, "BLOCK_RECORD")
        writer.pair(5, modelSpaceHandle)
        writer.pair(330, blockRecordTable)
        writer.pair(100, "AcDbSymbolTableRecord")
        writer.pair(100, "AcDbBlockTableRecord")
        writer.pair(2, "*Model_Space")
        writer.pair(0, "ENDTAB")

        writer.pair(0, "ENDSEC")
        return modelSpaceHandle
    }

    /**
     * Minimal but complete BLOCKS section: a *Model_Space block that owns all model entities.
     * Required by every DXF version from R13 onwards.
     */
    private fun writeBlocks(writer: DxfWriter, modelSpaceHandle: String) {
        writer.pair(0, "SECTION")
        writer.pair(2, "BLOCKS")

        writer.pair(0, "BLOCK")
        writer.pair(5, writer.newHandle())
        writer.pair(330, modelSpaceHandle)
        writer.pair(100, "AcDbEntity")
        writer.pair(8, "0")
        writer.pair(100, "AcDbBlockBegin")
        writer.pair(2, "*Model_Space")
        writer.pair(70, 0)
        writer.pair(10, 0.0)
        writer.pair(20, 0.0)
        writer.pair(30, 0.0)
        writer.pair(3, "*Model_Space")
        writer.pair(1, "")

        writer.pair(0, "ENDBLK")
        writer.pair(5, writer.newHandle())
        writer.pair(330, modelSpaceHandle)
        writer.pair(100, "AcDbEntity")
        writer.pair(8, "0")
        writer.pair(100, "AcDbBlockEnd")

        writer.pair(0, "ENDSEC")
    }

    /**
     * Root OBJECTS dictionary. AC1027 files must expose at least the named-object dictionary;
     * ACAD_GROUP keeps the file compatible with AutoCAD's object model.
     */
    private fun writeObjects(writer: DxfWriter) {
        writer.pair(0, "SECTION")
        writer.pair(2, "OBJECTS")

        val rootDictionary = writer.newHandle()
        writer.pair(0, "DICTIONARY")
        writer.pair(5, rootDictionary)
        writer.pair(330, "0")
        writer.pair(100, "AcDbDictionary")
        writer.pair(281, 1)

        val groupDictionary = writer.newHandle()
        writer.pair(3, "ACAD_GROUP")
        writer.pair(350, groupDictionary)

        writer.pair(0, "DICTIONARY")
        writer.pair(5, groupDictionary)
        writer.pair(330, rootDictionary)
        writer.pair(100, "AcDbDictionary")
        writer.pair(281, 1)

        writer.pair(0, "ENDSEC")
    }

    private fun writeGrid(writer: DxfWriter, bounds: Bounds) {
        val step = gridStep(max(bounds.maxX - bounds.minX, bounds.maxY - bounds.minY))
        var x = floor(bounds.minX / step) * step
        while (x <= bounds.maxX + 1e-9) {
            writer.line(GRID, x * MM, bounds.minY * MM, x * MM, bounds.maxY * MM)
            x += step
        }
        var y = floor(bounds.minY / step) * step
        while (y <= bounds.maxY + 1e-9) {
            writer.line(GRID, bounds.minX * MM, y * MM, bounds.maxX * MM, y * MM)
            y += step
        }
    }

    private fun writeSlabs(
        writer: DxfWriter,
        slabs: List<SlabPanel>,
        nodeById: Map<Int, FrameNode>
    ) {
        slabs.forEach { slab ->
            val points = slab.nodeIds.mapNotNull { nodeById[it] }
            if (points.size != 4) return@forEach
            val vertices = points.map { it.x * MM to it.y * MM }
            writer.polyline(SLABS, vertices, closed = true)
            writer.text(
                layer = TEXT,
                x = points.sumOf { it.x } / 4.0 * MM,
                y = points.sumOf { it.y } / 4.0 * MM + 250.0,
                height = 170.0,
                value = "S${slab.id} ${fmt(slab.thicknessMm)}mm"
            )
        }
    }

    private fun writeMembers(
        writer: DxfWriter,
        members: List<FrameMember>,
        nodeById: Map<Int, FrameNode>
    ) {
        members.forEach { member ->
            val ni = nodeById[member.nodeI] ?: return@forEach
            val nj = nodeById[member.nodeJ] ?: return@forEach
            writer.line(MEMBERS, ni.x * MM, ni.y * MM, nj.x * MM, nj.y * MM)
            val rawName = member.name.ifBlank { "M${member.id}" }
            val label = if (rawName.all { it.code in 32..126 }) rawName else "M${member.id}"
            writer.text(
                layer = TEXT,
                x = (ni.x + nj.x) / 2.0 * MM + 120.0,
                y = (ni.y + nj.y) / 2.0 * MM + 170.0,
                height = 160.0,
                value = label
            )
        }
    }

    private fun writeSupports(writer: DxfWriter, nodes: List<FrameNode>) {
        nodes.forEach { node ->
            val x = node.x * MM
            val y = node.y * MM
            when (node.support) {
                SupportType.Free -> Unit
                SupportType.Pin -> {
                    writer.line(SUPPORTS, x, y, x - 300.0, y - 500.0)
                    writer.line(SUPPORTS, x - 300.0, y - 500.0, x + 300.0, y - 500.0)
                    writer.line(SUPPORTS, x + 300.0, y - 500.0, x, y)
                    writer.line(SUPPORTS, x - 450.0, y - 500.0, x + 450.0, y - 500.0)
                    for (i in -1..1) {
                        writer.line(
                            SUPPORTS,
                            x + i * 200.0,
                            y - 500.0,
                            x + i * 200.0 - 150.0,
                            y - 650.0
                        )
                    }
                }
                SupportType.Fixed -> {
                    writer.line(SUPPORTS, x - 450.0, y - 350.0, x + 450.0, y - 350.0)
                    for (i in 0..3) {
                        writer.line(
                            SUPPORTS,
                            x - 350.0 + i * 200.0,
                            y - 350.0,
                            x - 500.0 + i * 200.0,
                            y - 550.0
                        )
                    }
                }
                SupportType.Roller -> {
                    writer.line(SUPPORTS, x, y, x - 300.0, y - 350.0)
                    writer.line(SUPPORTS, x - 300.0, y - 350.0, x + 300.0, y - 350.0)
                    writer.line(SUPPORTS, x + 300.0, y - 350.0, x, y)
                    writer.circle(SUPPORTS, x - 170.0, y - 480.0, 120.0)
                    writer.circle(SUPPORTS, x + 170.0, y - 480.0, 120.0)
                    writer.line(SUPPORTS, x - 450.0, y - 600.0, x + 450.0, y - 600.0)
                }
                SupportType.VerticalRoller -> {
                    writer.line(SUPPORTS, x, y, x + 400.0, y - 300.0)
                    writer.line(SUPPORTS, x + 400.0, y - 300.0, x + 400.0, y + 300.0)
                    writer.line(SUPPORTS, x + 400.0, y + 300.0, x, y)
                    writer.circle(SUPPORTS, x + 530.0, y - 160.0, 120.0)
                    writer.circle(SUPPORTS, x + 530.0, y + 160.0, 120.0)
                    writer.line(SUPPORTS, x + 650.0, y - 450.0, x + 650.0, y + 450.0)
                }
            }
        }
    }

    private fun writeJoints(writer: DxfWriter, nodes: List<FrameNode>) {
        nodes.forEach { node ->
            val x = node.x * MM
            val y = node.y * MM
            writer.circle(JOINTS, x, y, 60.0)
            writer.text(TEXT, x + 100.0, y + 120.0, 150.0, "${node.id}")
        }
    }

    private fun writeNodalLoads(
        writer: DxfWriter,
        loads: List<NodalLoad>,
        nodeById: Map<Int, FrameNode>,
        loadLayers: Map<String, Layer>
    ) {
        loads.forEach { load ->
            val node = nodeById[load.nodeId] ?: return@forEach
            val layer = layerFor(load.loadCase, loadLayers)
            val x = node.x * MM
            val y = node.y * MM
            val magnitude = hypot(load.fx, load.fy)
            if (magnitude > 1e-9) {
                val ux = load.fx / magnitude
                val uy = load.fy / magnitude
                val startX = x - ux * 750.0
                val startY = y - uy * 750.0
                writer.arrow(layer, startX, startY, x, y)
                writer.text(
                    layer = TEXT,
                    x = startX + 80.0,
                    y = startY + 80.0,
                    height = 150.0,
                    value = loadLabel(load)
                )
            }
            if (kotlin.math.abs(load.mz) > 1e-9) {
                writer.circle(layer, x + 320.0, y + 320.0, 220.0)
                writer.text(
                    layer = TEXT,
                    x = x + 580.0,
                    y = y + 540.0,
                    height = 150.0,
                    value = "Mz=${fmt(load.mz)}"
                )
            }
        }
    }

    private fun writeMemberLoads(
        writer: DxfWriter,
        loads: List<MemberLoad>,
        members: List<FrameMember>,
        nodeById: Map<Int, FrameNode>,
        loadLayers: Map<String, Layer>
    ) {
        loads.forEach { load ->
            val member = members.find { it.id == load.memberId } ?: return@forEach
            val ni = nodeById[member.nodeI] ?: return@forEach
            val nj = nodeById[member.nodeJ] ?: return@forEach
            val x1m = ni.x
            val y1m = ni.y
            val dx = nj.x - ni.x
            val dy = nj.y - ni.y
            val length = hypot(dx, dy)
            if (length < 1e-9) return@forEach

            val tx = dx / length
            val ty = dy / length
            val side = if (load.value >= 0.0) 1.0 else -1.0
            val nx = -ty * side
            val ny = tx * side
            val offset = min(700.0, length * MM * 0.22)
            val layer = layerFor(load.loadCase, loadLayers)
            val x1 = x1m * MM
            val y1 = y1m * MM
            val x2 = nj.x * MM
            val y2 = nj.y * MM

            when (load.loadType) {
                MemberLoadType.UDL, MemberLoadType.LinearVarying -> {
                    val startFactor = if (load.loadType == MemberLoadType.LinearVarying) 0.2 else 1.0
                    val bx1 = x1 + nx * offset * startFactor
                    val by1 = y1 + ny * offset * startFactor
                    val bx2 = x2 + nx * offset
                    val by2 = y2 + ny * offset
                    writer.line(layer, bx1, by1, bx2, by2)
                    for (step in 0..4) {
                        val t = step / 4.0
                        writer.arrow(
                            layer,
                            bx1 + (bx2 - bx1) * t,
                            by1 + (by2 - by1) * t,
                            x1 + (x2 - x1) * t,
                            y1 + (y2 - y1) * t,
                            160.0
                        )
                    }
                    writer.text(
                        TEXT,
                        (bx1 + bx2) / 2.0,
                        (by1 + by2) / 2.0 + ny * 220.0,
                        155.0,
                        "w=${fmt(load.value)} kN/m"
                    )
                }
                MemberLoadType.PointLoad -> {
                    val t = (load.position / length).coerceIn(0.0, 1.0)
                    val px = x1 + (x2 - x1) * t
                    val py = y1 + (y2 - y1) * t
                    writer.arrow(layer, px + nx * offset, py + ny * offset, px, py, 180.0)
                    writer.text(
                        TEXT,
                        px + nx * offset,
                        py + ny * offset + ny * 180.0,
                        155.0,
                        "P=${fmt(load.value)} kN"
                    )
                }
                MemberLoadType.Moment -> {
                    val t = (load.position / length).coerceIn(0.0, 1.0)
                    val px = x1 + (x2 - x1) * t + nx * 450.0
                    val py = y1 + (y2 - y1) * t + ny * 450.0
                    writer.circle(layer, px, py, 220.0)
                    writer.text(TEXT, px + 280.0, py + 220.0, 150.0, "M=${fmt(load.value)}")
                }
            }
        }
    }

    private fun writeDimensions(
        writer: DxfWriter,
        dimensions: List<Dimension>,
        nodeById: Map<Int, FrameNode>
    ) {
        if (dimensions.isEmpty()) return
        for (dim in dimensions) {
            val p1 = nodeById[dim.point1Id] ?: continue
            val p2 = nodeById[dim.point2Id] ?: continue
            val x1 = p1.x * MM
            val y1 = p1.y * MM
            val x2 = p2.x * MM
            val y2 = p2.y * MM
            val dx = x2 - x1
            val dy = y2 - y1
            val length = hypot(dx, dy)
            if (length < 1.0) continue

            val ux = dx / length
            val uy = dy / length
            val offsetDist = dim.offsetDistance * MM
            val nx = -uy * offsetDist
            val ny = ux * offsetDist
            val extOffset = 200.0 // 200mm extension line offset

            // Dimension line
            val dimStartX = x1 + ux * 200.0 + nx
            val dimStartY = y1 + uy * 200.0 + ny
            val dimEndX = x2 - ux * 200.0 + nx
            val dimEndY = y2 - uy * 200.0 + ny

            writer.line(DIMENSIONS, dimStartX, dimStartY, dimEndX, dimEndY)

            // Extension lines
            writer.line(DIMENSIONS, x1, y1, x1 + ux * 200.0, y1 + uy * 200.0)
            writer.line(DIMENSIONS, x2, y2, x2 - ux * 200.0, y2 - uy * 200.0)

            // Arrows (simple lines)
            val arrowSize = 150.0
            writer.line(DIMENSIONS, dimStartX, dimStartY, dimStartX - ux * 150.0 + uy * 50.0, dimStartY - uy * 150.0 - ux * 50.0)
            writer.line(DIMENSIONS, dimEndX, dimEndY, dimEndX + ux * 150.0 - uy * 50.0, dimEndY + uy * 150.0 + ux * 50.0)

            // Dimension text
            val text = dim.getDisplayText(nodeById.values.toList())
            val midX = (x1 + x2) / 2.0 + nx
            val midY = (y1 + y2) / 2.0 + ny
            writer.text(DIMENSIONS, midX, midY + 300.0, 200.0, text)
        }
    }

    private fun modelBounds(nodes: List<FrameNode>): Bounds? {
        if (nodes.isEmpty()) return null
        return Bounds(
            minX = nodes.minOf { it.x } - 1.0,
            maxX = nodes.maxOf { it.x } + 1.0,
            minY = nodes.minOf { it.y } - 1.0,
            maxY = nodes.maxOf { it.y } + 1.0
        )
    }

    private fun buildLoadLayers(cases: List<String>): Map<String, Layer> {
        val layers = LinkedHashMap<String, Layer>()
        val palette = intArrayOf(1, 5, 4, 6, 3, 2)
        cases.forEachIndexed { index, loadCase ->
            val sanitized = transliterateToAscii(loadCase)
            val base = "LOAD_" + sanitized.uppercase(Locale.US)
                .replace(invalidLayerChars, "_")
                .trim('_')
                .take(20)
                .ifBlank { "OTHER" }
            var name = base
            var suffix = 2
            while (layers.values.any { it.name == name }) {
                name = base.take(26) + "_" + suffix
                suffix++
            }
            val color = when (loadCase.uppercase(Locale.US)) {
                "DL" -> 1
                "LL" -> 5
                "WL" -> 4
                "EQ" -> 6
                "SDL" -> 3
                else -> palette[index % palette.size]
            }
            layers[loadCase] = Layer(name, color)
        }
        return layers
    }

    private fun layerFor(loadCase: String, layers: Map<String, Layer>): String =
        layers[normalizeLoadCase(loadCase)]?.name ?: "LOAD_OTHER"

    private fun normalizeLoadCase(loadCase: String): String =
        loadCase.trim().ifBlank { "OTHER" }.uppercase(Locale.US)

    private fun loadLabel(load: NodalLoad): String {
        val parts = buildList {
            if (kotlin.math.abs(load.fx) > 1e-9) add("Fx=${fmt(load.fx)}")
            if (kotlin.math.abs(load.fy) > 1e-9) add("Fy=${fmt(load.fy)}")
        }
        return if (parts.isEmpty()) "F=0" else parts.joinToString(" ")
    }

    private fun gridStep(maxSpan: Double): Double {
        val candidates = doubleArrayOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0)
        return candidates.firstOrNull { maxSpan / it <= 300.0 } ?: 100.0
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)

    private class DxfWriter {
        private val builder = StringBuilder()
        private var nextHandle = 0x10

        /** Handle of the owning BLOCK_RECORD (the *Model_Space block record for model entities). */
        var ownerHandle: String = "0"

        fun newHandle(): String {
            val handle = Integer.toHexString(nextHandle).uppercase(Locale.US)
            nextHandle++
            return handle
        }

        fun pair(code: Int, value: String) {
            builder.append(code).append('\n').append(value.replace('\n', ' ')).append('\n')
        }

        fun pair(code: Int, value: Int) {
            builder.append(code).append('\n').append(value).append('\n')
        }

        fun pair(code: Int, value: Double) {
            builder.append(code).append('\n')
                .append(String.format(Locale.US, "%.3f", value))
                .append('\n')
        }

        fun tableHeader(name: String, handle: String, count: Int) {
            pair(0, "TABLE")
            pair(2, name)
            pair(5, handle)
            pair(330, "0")
            pair(100, "AcDbSymbolTable")
            pair(70, count)
        }

        fun linetype(
            tableHandle: String,
            name: String,
            description: String,
            patternLength: Double,
            dashes: List<Double>
        ) {
            pair(0, "LTYPE")
            pair(5, newHandle())
            pair(330, tableHandle)
            pair(100, "AcDbSymbolTableRecord")
            pair(100, "AcDbLinetypeTableRecord")
            pair(2, name)
            pair(70, 0)
            pair(3, description)
            pair(72, 65)
            pair(73, dashes.size)
            pair(40, patternLength)
            dashes.forEach { dash ->
                pair(49, dash)
                pair(74, 0)
            }
        }

        /** Writes the mandatory DXF 2000+ entity preamble (handle, owner, subclass markers). */
        private fun entity(type: String, layer: String, subclass: String) {
            pair(0, type)
            pair(5, newHandle())
            pair(330, ownerHandle)
            pair(100, "AcDbEntity")
            pair(8, layer)
            pair(100, subclass)
        }

        fun line(layer: String, x1: Double, y1: Double, x2: Double, y2: Double) {
            entity("LINE", layer, "AcDbLine")
            pair(10, x1)
            pair(20, y1)
            pair(30, 0.0)
            pair(11, x2)
            pair(21, y2)
            pair(31, 0.0)
        }

        fun circle(layer: String, x: Double, y: Double, radius: Double) {
            entity("CIRCLE", layer, "AcDbCircle")
            pair(10, x)
            pair(20, y)
            pair(30, 0.0)
            pair(40, radius)
        }

        fun text(layer: String, x: Double, y: Double, height: Double, value: String) {
            entity("TEXT", layer, "AcDbText")
            pair(10, x)
            pair(20, y)
            pair(30, 0.0)
            pair(40, height)
            pair(1, value)
            pair(7, "STANDARD")
            pair(100, "AcDbText")
        }

        fun mtext(layer: String, x: Double, y: Double, height: Double, value: String, width: Double = 5000.0) {
            entity("MTEXT", layer, "AcDbMText")
            pair(10, x)
            pair(20, y)
            pair(30, 0.0)
            pair(40, height)
            pair(41, width)
            pair(71, 1)
            pair(1, value)
            pair(7, "STANDARD")
        }

        fun polyline(layer: String, points: List<Pair<Double, Double>>, closed: Boolean = false) {
            if (points.isEmpty()) return
            // A closed LWPOLYLINE must not repeat its first vertex: the 70 flag closes it.
            val vertices = if (closed && points.size > 1 && points.first() == points.last()) {
                points.dropLast(1)
            } else {
                points
            }
            entity("LWPOLYLINE", layer, "AcDbPolyline")
            pair(90, vertices.size) // vertex count
            pair(70, if (closed) 1 else 0) // closed flag
            vertices.forEach { (x, y) ->
                pair(10, x)
                pair(20, y)
            }
        }

        fun arrow(
            layer: String,
            startX: Double,
            startY: Double,
            endX: Double,
            endY: Double,
            headLength: Double = 200.0
        ) {
            line(layer, startX, startY, endX, endY)
            val angle = atan2(endY - startY, endX - startX)
            for (delta in doubleArrayOf(2.7, -2.7)) {
                line(
                    layer,
                    endX,
                    endY,
                    endX + cos(angle + delta) * headLength,
                    endY + sin(angle + delta) * headLength
                )
            }
        }

        override fun toString(): String = builder.toString()
    }

    /**
     * Simple Arabic → ASCII transliteration for DXF layer names.
     * Maps common Arabic load case names to unique ASCII identifiers.
     */
    private fun transliterateToAscii(input: String): String {
        val map = mapOf(
            'أ' to "A", 'ب' to "B", 'ت' to "T", 'ث' to "Th", 'ج' to "J",
            'ح' to "H", 'خ' to "Kh", 'د' to "D", 'ذ' to "Dh", 'ر' to "R",
            'ز' to "Z", 'س' to "S", 'ش' to "Sh", 'ص' to "S", 'ض' to "D",
            'ط' to "T", 'ظ' to "Z", 'ع' to "A", 'غ' to "Gh", 'ف' to "F",
            'ق' to "Q", 'ك' to "K", 'ل' to "L", 'م' to "M", 'ن' to "N",
            'ه' to "H", 'و' to "W", 'ي' to "Y", 'ة' to "T", 'ى' to "Y",
            'إ' to "I", 'آ' to "A", 'ؤ' to "U", 'ئ' to "I", 'ء' to "A",
            'ً' to "", 'ٌ' to "", 'ٍ' to "", 'َ' to "", 'ُ' to "", 'ِ' to "",
            'ّ' to "", 'ْ' to ""
        )
        return input.map { c -> map[c] ?: c.toString() }.joinToString("").replace(Regex("[^A-Za-z0-9_]+"), "_")
    }
}
