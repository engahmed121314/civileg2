package com.civileg.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DxfExporterTest {

    private fun count(dxf: String, entity: String): Int =
        Regex("\n0\n$entity\n").findAll(dxf).count()

    @Test
    fun beamSection_emitsEntities_insideEntitiesSection() {
        val drawing = ElementDxfDrawings.beamSection(
            title = "BEAM B1",
            widthMm = 300.0,
            depthMm = 600.0,
            coverMm = 40.0,
            mainBarDiaMm = 16.0,
            mainBarCount = 4,
            stirrupDiaMm = 8.0,
            stirrupSpacingMm = 150.0
        )
        val dxf = DxfExporter().renderToString(drawing)

        assertTrue(dxf.startsWith("0\nSECTION"))
        assertTrue(dxf.contains("2\nENTITIES"))
        assertTrue(dxf.endsWith("0\nEOF\n"))

        val entitiesStart = dxf.indexOf("2\nENTITIES")
        val entitiesEnd = dxf.indexOf("0\nENDSEC\n", entitiesStart)
        assertTrue("ENTITIES section must be closed", entitiesEnd > entitiesStart)

        val entityRegion = dxf.substring(entitiesStart, entitiesEnd)
        // regression: entities previously dropped because the file builder shadowed `sb`
        assertTrue("beam outline lines missing", count(entityRegion, "LINE") >= 4)
        assertEquals("one circle per main bar", 4, count(entityRegion, "CIRCLE"))
        assertTrue(entityRegion.contains("0\nTEXT\n"))
    }

    @Test
    fun everyFactory_producesNonEmptyEntitySection() {
        val drawings = listOf(
            ElementDxfDrawings.beamElevation("BEAM", 6000.0, 600.0, 40.0, 16.0, 8.0, 150.0),
            ElementDxfDrawings.columnSection("COLUMN", 400.0, 400.0, 40.0, 16.0, 8, 8.0, 150.0),
            ElementDxfDrawings.slabSection("SLAB", 5000.0, 200.0, 25.0, 12.0, 150.0),
            ElementDxfDrawings.footingSection("FOOTING", 2000.0, 500.0, 50.0, 16.0, 150.0, 400.0),
            ElementDxfDrawings.combinedFootingSection(
                "COMBINED", 6000.0, 600.0, 50.0, 16.0, 150.0, 400.0, listOf(1000.0, 5000.0)
            ),
            ElementDxfDrawings.pileElevation("PILE", 12000.0, 600.0, 50.0, 16.0, 6, 8.0, 200.0),
            ElementDxfDrawings.retainingWallSection("WALL", 2500.0, 400.0, 4000.0, 300.0, 50.0, 16.0, 150.0),
            ElementDxfDrawings.stairSection("STAIR", 3000.0, 175.0, 15, 200.0, 25.0, 12.0, 150.0),
            ElementDxfDrawings.tankSection("TANK", 4000.0, 3000.0, 250.0, 40.0, 16.0, 150.0),
            ElementDxfDrawings.steelISection("IPE 300", 300.0, 150.0, 7.1, 10.7),
            ElementDxfDrawings.basePlate("BASE PLATE", 500.0, 500.0, 25.0, 20.0, 4, 300.0, 300.0),
            ElementDxfDrawings.generic("ELEMENT", 1000.0, 500.0)
        )

        val exporter = DxfExporter()
        drawings.forEachIndexed { i, drawing ->
            val dxf = exporter.renderToString(drawing)
            val start = dxf.indexOf("2\nENTITIES")
            val end = dxf.indexOf("0\nENDSEC\n", start)
            val region = dxf.substring(start, end)
            assertTrue("drawing #$i produced no entities", region.contains("0\nLINE\n"))
            assertTrue("drawing #$i not terminated", dxf.endsWith("0\nEOF\n"))
        }
    }

    @Test
    fun exportBoth_producesDxfFileWithEntities() {
        val drawing = ElementDxfDrawings.generic("TEST", 1000.0, 500.0)
        val dir = File(System.getProperty("java.io.tmpdir"), "civileg-dxf-${System.nanoTime()}")
        dir.mkdirs()
        val out = File(dir, "element.dxf")

        val file = DxfExporter().exportDxf(drawing, out.absolutePath)

        assertNotNull(file)
        val text = out.readText()
        assertTrue(text.contains("0\nEOF"))
        assertTrue("DXF file must contain line entities", count(text, "LINE") >= 4)
        dir.deleteRecursively()
    }

    @Test
    fun exportWithBbs_appendsScheduleTable() {
        val drawing = ElementDxfDrawings.generic("BEAM B1", 1000.0, 500.0)
        val entries = listOf(
            BbsEntry(
                memberMark = "B1", barMark = "01", diameter = 16, shapeCode = 0,
                count = 4, lengthA = 6000.0, totalLengthPerBar = 6300.0,
                totalWeightKg = 4 * 6300.0 / 1000.0 * (16.0 * 16.0 / 162.0)
            ),
            BbsEntry(
                memberMark = "B1", barMark = "02", diameter = 8, shapeCode = 51,
                count = 20, lengthA = 200.0, lengthB = 500.0, totalLengthPerBar = 1400.0,
                totalWeightKg = 20 * 1400.0 / 1000.0 * (8.0 * 8.0 / 162.0)
            )
        )
        val dir = File(System.getProperty("java.io.tmpdir"), "civileg-bbs-${System.nanoTime()}")
        dir.mkdirs()
        val out = File(dir, "beam.dxf")

        val file = DxfExporter().exportDxf(drawing, out.absolutePath, entries)

        assertNotNull(file)
        val text = out.readText()
        assertTrue(text.contains("BAR BENDING SCHEDULE"))
        assertTrue(text.contains("TOTAL STEEL"))
        assertTrue(text.contains("B1"))
        assertTrue(text.contains("51"))
        dir.deleteRecursively()
    }
}
