package com.civileg.app.domain.modeler

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
    private const val MM = 1000.0

    private data class Layer(val name: String, val color: Int)
    private data class Bounds(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double)

    private val invalidLayerChars = Regex("[^A-Z0-9_]+")

    fun build(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        slabs: List<SlabPanel>
    ): String {
        val nodeById = nodes.associateBy { it.id }
        val loadLayers = buildLoadLayers(
            (nodalLoads.map { it.loadCase } + memberLoads.map { it.loadCase })
                .map { normalizeLoadCase(it) }
                .distinct()
        )
        val baseLayers = listOf(
            Layer(GRID, 8),
            Layer(SLABS, 4),
            Layer(MEMBERS, 5),
            Layer(JOINTS, 7),
            Layer(SUPPORTS, 3),
            Layer(TEXT, 7)
        )

        val writer = DxfWriter()
        val bounds = modelBounds(nodes)
        writeHeader(writer, bounds)
        writeLayerTable(writer, baseLayers + loadLayers.values)
        writer.pair(0, "SECTION")
        writer.pair(2, "BLOCKS")
        writer.pair(0, "ENDSEC")
        writer.pair(0, "SECTION")
        writer.pair(2, "ENTITIES")

        if (nodes.isNotEmpty() && bounds != null) {
            writeGrid(writer, bounds)
            writeSlabs(writer, slabs, nodeById)
            writeMembers(writer, members, nodeById)
            writeSupports(writer, nodes)
            writeJoints(writer, nodes)
            writeNodalLoads(writer, nodalLoads, nodeById, loadLayers)
            writeMemberLoads(writer, memberLoads, members, nodeById, loadLayers)
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
        writer.pair(1, "AC1009")
        writer.pair(9, "\$INSUNITS")
        writer.pair(70, 4)
        writer.pair(9, "\$EXTMIN")
        writer.pair(10, minX)
        writer.pair(20, minY)
        writer.pair(30, 0.0)
        writer.pair(9, "\$EXTMAX")
        writer.pair(10, maxX)
        writer.pair(20, maxY)
        writer.pair(30, 0.0)
        writer.pair(9, "\$DWGCODEPAGE")
        writer.pair(3, "ANSI_1252")
        writer.pair(0, "ENDSEC")
    }

    private fun writeLayerTable(writer: DxfWriter, layers: List<Layer>) {
        writer.pair(0, "SECTION")
        writer.pair(2, "TABLES")
        writer.pair(0, "TABLE")
        writer.pair(2, "LTYPE")
        writer.pair(70, 1)
        writer.pair(0, "LTYPE")
        writer.pair(2, "CONTINUOUS")
        writer.pair(70, 64)
        writer.pair(3, "Solid line")
        writer.pair(72, 65)
        writer.pair(73, 0)
        writer.pair(40, 0.0)
        writer.pair(0, "ENDTAB")
        writer.pair(0, "TABLE")
        writer.pair(2, "LAYER")
        writer.pair(70, layers.size)
        layers.forEach { layer ->
            writer.pair(0, "LAYER")
            writer.pair(2, layer.name)
            writer.pair(70, 0)
            writer.pair(62, layer.color)
            writer.pair(6, "CONTINUOUS")
        }
        writer.pair(0, "ENDTAB")
        writer.pair(0, "TABLE")
        writer.pair(2, "STYLE")
        writer.pair(70, 1)
        writer.pair(0, "STYLE")
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
            writer.polyline(SLABS, vertices)
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
                MemberLoadType.Trapezoidal -> {
                    // المرحلة الثالثة: خطا ارتفاع + هضبة + أسهم (شكل شبه المنحرف)
                    val tA = (load.position / length).coerceIn(0.02, 0.5)
                    val r1x = x1 + (x2 - x1) * tA + nx * offset
                    val r1y = y1 + (y2 - y1) * tA + ny * offset
                    val r2x = x2 - (x2 - x1) * tA + nx * offset
                    val r2y = y2 - (y2 - y1) * tA + ny * offset
                    writer.line(layer, x1 + nx * 60.0, y1 + ny * 60.0, r1x, r1y)
                    writer.line(layer, x2 + nx * 60.0, y2 + ny * 60.0, r2x, r2y)
                    writer.line(layer, r1x, r1y, r2x, r2y)
                    for (step in 0..4) {
                        val t = tA + (1.0 - 2.0 * tA) * step / 4.0
                        val px = x1 + (x2 - x1) * t
                        val py = y1 + (y2 - y1) * t
                        writer.arrow(
                            layer,
                            px + nx * offset,
                            py + ny * offset,
                            px,
                            py,
                            160.0
                        )
                    }
                    writer.text(
                        TEXT,
                        (r1x + r2x) / 2.0,
                        (r1y + r2y) / 2.0 + ny * 220.0,
                        155.0,
                        "w=${fmt(load.value)} a=${fmt(load.position)} (trap)"
                    )
                }
            }
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
            val base = "LOAD_" + loadCase.uppercase(Locale.US)
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

        fun line(layer: String, x1: Double, y1: Double, x2: Double, y2: Double) {
            pair(0, "LINE")
            pair(8, layer)
            pair(10, x1)
            pair(20, y1)
            pair(30, 0.0)
            pair(11, x2)
            pair(21, y2)
            pair(31, 0.0)
        }

        fun circle(layer: String, x: Double, y: Double, radius: Double) {
            pair(0, "CIRCLE")
            pair(8, layer)
            pair(10, x)
            pair(20, y)
            pair(30, 0.0)
            pair(40, radius)
        }

        fun text(layer: String, x: Double, y: Double, height: Double, value: String) {
            pair(0, "TEXT")
            pair(8, layer)
            pair(10, x)
            pair(20, y)
            pair(30, 0.0)
            pair(40, height)
            pair(1, value)
            pair(7, "STANDARD")
        }

        fun polyline(layer: String, points: List<Pair<Double, Double>>) {
            pair(0, "POLYLINE")
            pair(8, layer)
            pair(66, 1)
            pair(70, 1)
            pair(10, 0.0)
            pair(20, 0.0)
            pair(30, 0.0)
            points.forEach { (x, y) ->
                pair(0, "VERTEX")
                pair(8, layer)
                pair(10, x)
                pair(20, y)
                pair(30, 0.0)
            }
            pair(0, "SEQEND")
            pair(8, layer)
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
}
