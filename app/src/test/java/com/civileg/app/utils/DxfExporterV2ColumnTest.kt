package com.civileg.app.utils

import com.civileg.app.utils.CalculatorDetailingV4.MemberType
import com.civileg.app.utils.detailing.ColumnDetailingEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files

/**
 * §16/§17/§40 — DxfExporterV2 column detailed export:
 *  - engine tie zones mapped VERBATIM into V4 stirrup definitions (Pillar-2)
 *  - main bars at the TRUE cut length (clear height + code Ld) with lap marker
 *  - BBS rows with genuine cut lengths and zone-aggregated quantities
 *  - full end-to-end sheet + manifest + QA pass through CalculatorCadExporterV7
 */
class DxfExporterV2ColumnTest {

    private fun eng() = CalculatorEngine(mockk(relaxed = true))

    private fun result() = eng().designColumn(
        width = 300.0, depth = 500.0,
        pu = 1500.0, mx = 120.0, my = 40.0,
        fcu = 25.0, fy = 400.0,
        code = CalculatorEngine.DesignCode.EGYPTIAN,
        clearHeight = 3200.0,
        preferredDiameter = 16
    )

    private fun det(res: CalculatorEngine.ColumnResult = result()) =
        ColumnDetailingEngine.build(
            result = res, clearHeightMm = 3200.0, coverMm = 40.0,
            fcuMPa = 25.0, fyMPa = 400.0, codeName = "ECP 203-2020"
        )

    @Test
    fun `column package maps engine zones verbatim into v4 stirrups`() {
        val d = det()
        val pkg = DxfExporterV2.columnPackageFrom(d, 300.0, 500.0, 3200.0, 40.0)

        assertEquals(MemberType.COLUMN, pkg.memberType)
        assertEquals(d.mark, pkg.memberId)
        assertEquals(300.0, pkg.geometry["width"] ?: -1.0, 1e-9)
        assertEquals(500.0, pkg.geometry["depth"] ?: -1.0, 1e-9)
        assertEquals(3200.0, pkg.geometry["height"] ?: -1.0, 1e-9)
        assertEquals(40.0, pkg.geometry["cover"] ?: -1.0, 1e-9)

        val main = pkg.bars.single()
        assertEquals(d.mainMark, main.mark)
        assertEquals(CalculatorDetailingV4.BarShape.STRAIGHT, main.shape)
        assertEquals(d.mainDiaMm, main.diameterMm)
        assertEquals(d.mainCutLengthMm, main.straightLengthMm ?: -1.0, 1e-9)
        assertEquals(d.mainCount, main.quantity)
        assertEquals(1, main.lapLocations.size)
        assertEquals(d.lapZoneStartMm, main.lapLocations[0].positionFromStartMm, 1e-9)
        assertEquals((d.lapZoneEndMm - d.lapZoneStartMm), main.lapLocations[0].lengthMm, 1e-9)

        assertTrue("tie zones must be one V4 definition per engine zone",
            pkg.stirrups.size == d.tieZones.size)
        pkg.stirrups.zip(d.tieZones).forEach { (v4, zone) ->
            assertEquals(zone.mark, v4.mark)
            assertEquals(CalculatorDetailingV4.BarShape.STIRRUP_135, v4.shape)
            assertEquals(zone.diameterMm, v4.diameterMm)
            assertEquals(zone.spacingMm, v4.spacingMm ?: -1.0, 1e-9)
            assertEquals(zone.count, v4.quantity)
        }
    }

    @Test
    fun `engine spacing flows through zone list verbatim never recomputed`() {
        val res = result()
        val d = det(res)
        val pkg = DxfExporterV2.columnPackageFrom(d, 300.0, 500.0, 3200.0, 40.0)

        if (res.stirrups.zones.isNotEmpty()) {
            assertEquals(res.stirrups.zones.size, pkg.stirrups.size)
            pkg.stirrups.zip(res.stirrups.zones).forEach { (v4, zone) ->
                assertEquals(zone.spacing, v4.spacingMm ?: -1.0, 1e-9)
                assertEquals(zone.diameter, v4.diameterMm)
            }
        } else {
            val fallback = pkg.stirrups.single()
            assertEquals(res.stirrups.spacing, fallback.spacingMm ?: -1.0, 1e-9)
            assertEquals(res.stirrups.diameter, fallback.diameterMm)
        }
    }

    @Test
    fun `bbs cut lengths are genuine and quantities aggregate all tie zones`() {
        val d = det()
        val pkg = DxfExporterV2.columnPackageFrom(d, 300.0, 500.0, 3200.0, 40.0)
        val bbs = CalculatorDetailingV4.buildBarSchedule(listOf(pkg))

        // Main bar: straight cut length must equal engine clear-height+Ld exactly
        val mainRow = bbs.rows.first { it.mark == d.mainMark }
        assertEquals(d.mainCutLengthMm, mainRow.individualLengthMm, 1e-6)
        assertEquals(d.mainCount, mainRow.quantity)

        // Each tie: closed perimeter (2·(b-2c-Ø)+2·(h-2c-Ø)) + 135° hook (12Ø)
        val tieMarks = d.tieZones.map { it.mark }.toSet()
        val tieRows = bbs.rows.filter { it.mark in tieMarks }
        assertEquals(d.tieZones.size, tieRows.size)
        tieRows.indices.forEach { i ->
            val zone = d.tieZones[i]
            val row = tieRows[i]
            val expectedCut = 2 * (300.0 - 2 * 40.0 - zone.diameterMm) +
                2 * (500.0 - 2 * 40.0 - zone.diameterMm) + 12 * zone.diameterMm
            assertEquals(expectedCut, row.individualLengthMm, 1e-6)
            assertEquals(zone.count, row.quantity)
        }

        val totalTies = tieRows.sumOf { it.quantity }
        assertEquals(d.tieZones.sumOf { it.count }, totalTies)
        assertEquals(1 + tieRows.size, bbs.rows.size)
    }

    @Test
    fun `export column detailed writes sheet manifest bbs and passes qa`() {
        val tmp = Files.createTempDirectory("v2col_e2e").toFile()
        tmp.mkdirs()
        val ctx = mockk<android.content.Context>(relaxed = true)
        every { ctx.getExternalFilesDir(any()) } returns tmp
        every { ctx.filesDir } returns tmp

        val out = DxfExporterV2.exportColumnDetailed(
            context = ctx, result = result(),
            clearHeightMm = 3200.0, coverMm = 40.0,
            fcuMPa = 25.0, fyMPa = 400.0, projectName = "T"
        )
        assertNotNull("detailed column export must not fail", out)
        assertTrue("QA must pass", out!!.qaPassed)
        assertTrue("at least one column sheet", out.sheetFiles.isNotEmpty())
        assertTrue("sheet file must be the RC_COLUMN drawing",
            out.sheetFiles.any { it.name.contains("RC_COLUMN") })
        assertNotNull(out.manifestFile)
        assertTrue("manifest must be written", out.manifestFile!!.exists())

        val sheetText = out.sheetFiles.first().readText().replace("\r\n", "\n")
        assertTrue("sheet must contain DXF terminator", sheetText.contains("0\nEOF\n"))

        val bbsFile = out.directory.resolve("S-98_MASTER_BBS.dxf")
        assertTrue("master BBS sheet must exist", bbsFile.exists())
        val bbsText = bbsFile.readText()
        assertTrue("BBS must list the main column mark", bbsText.contains("MASTER BAR BENDING SCHEDULE"))
    }

    @Test
    fun `column export guards loudly on invalid inputs`() {
        val tmp = Files.createTempDirectory("v2col_guard").toFile()
        tmp.mkdirs()
        val ctx = mockk<android.content.Context>(relaxed = true)
        every { ctx.getExternalFilesDir(any()) } returns tmp
        every { ctx.filesDir } returns tmp

        // zero clear height -> hard guard (same contract as the beam export)
        try {
            DxfExporterV2.exportColumnDetailed(
                context = ctx, result = result(),
                clearHeightMm = 0.0, coverMm = 40.0,
                fcuMPa = 25.0, fyMPa = 400.0
            )
            fail("Expected IllegalArgumentException for zero clear height")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("clear height"))
        }
    }
}
