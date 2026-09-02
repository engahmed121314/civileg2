package com.civileg.app.utils

import com.civileg.app.utils.CalculatorDetailingV4.BarDefinition
import com.civileg.app.utils.CalculatorDetailingV4.BarShape
import com.civileg.app.utils.CalculatorDetailingV4.DetailingPackage
import com.civileg.app.utils.CalculatorDetailingV4.MemberType
import com.civileg.app.utils.CalculatorDetailingV4.Segment
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase-4 ADR-004 gate: the canonical V7 DXF pipeline must produce a valid
 * AC1027 skeleton (HEADER/ENTITIES/EOF) for a minimal beam package on pure JVM.
 */
class CadDxfPipelineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun sampleBeamPackage() = DetailingPackage(
        memberType = MemberType.BEAM,
        memberId = "B1",
        title = "BEAM 250x500 — ECP",
        geometry = mapOf("span" to 5000.0, "width" to 250.0, "depth" to 500.0),
        bars = listOf(
            BarDefinition(
                mark = "B1", diameterMm = 16, shape = BarShape.STRAIGHT,
                straightLengthMm = 4920.0, layer = "REBAR-BOTTOM"
            ),
            BarDefinition(
                mark = "B2", diameterMm = 12, shape = BarShape.STRAIGHT,
                straightLengthMm = 4920.0, layer = "REBAR-TOP"
            )
        ),
        stirrups = listOf(
            BarDefinition(
                mark = "S1", diameterMm = 8, shape = BarShape.STIRRUP_135,
                spacingMm = 150.0,
                segments = listOf(
                    Segment(90.0, 170.0), Segment(90.0, 420.0),
                    Segment(90.0, 170.0), Segment(90.0, 420.0),
                    Segment(135.0, 80.0)
                )
            )
        )
    )

    @Test
    fun `v7 exports sheet with dxf skeleton and qa report`() {
        val pkg = sampleBeamPackage()
        val bbs = CalculatorDetailingV4.buildBarSchedule(listOf(pkg))
        assertTrue("BBS rows must be generated from package", bbs.rows.isNotEmpty())

        val outDir = tmp.newFolder("dxf_out")
        val export = CalculatorCadExporterV7.exportProject(
            packages = listOf(pkg), bbs = bbs, outDir = outDir.absolutePath
        )

        assertTrue("At least one member sheet", export.sheets.isNotEmpty())
        val dxfText = export.sheets.first().file.readText()
        assertTrue("DXF must contain ENTITIES section", dxfText.contains("ENTITIES"))
        assertTrue("DXF must terminate with EOF", dxfText.trimEnd().endsWith("EOF"))
        assertTrue("AC1027 writer emits AcDbEntity handles", dxfText.contains("AcDbEntity"))

        if (export.qaFile != null) {
            val qa = export.qaFile.readText()
            assertTrue(qa.isNotEmpty())
        }
    }

    @Test
    fun `bbs aggregates steel weight from bar lengths and diameters`() {
        val pkg = sampleBeamPackage()
        val bbs = CalculatorDetailingV4.buildBarSchedule(listOf(pkg))
        assertTrue("Total weight must be positive", bbs.totalWeightKg > 0.0)
        val marks = bbs.rows.map { it.mark }.toSet()
        assertTrue(marks.containsAll(setOf("B1", "B2", "S1")))
    }

    @Test
    fun `v7 renders footing tank and stair member types`() {
        val straight = { mark: String, dia: Int, len: Double, sp: Double ->
            BarDefinition(
                mark = mark, diameterMm = dia, shape = BarShape.STRAIGHT,
                straightLengthMm = len, spacingMm = sp
            )
        }
        val pkgs = listOf(
            DetailingPackage(
                memberType = MemberType.FOOTING, memberId = "F1",
                geometry = mapOf(
                    "length" to 2500.0, "width" to 2500.0, "cover" to 75.0,
                    "columnWidth" to 400.0, "columnLength" to 400.0
                ),
                bars = listOf(straight("FX", 16, 2350.0, 200.0))
            ),
            DetailingPackage(
                memberType = MemberType.TANK, memberId = "T1",
                geometry = mapOf(
                    "length" to 4000.0, "height" to 3000.0,
                    "wallThickness" to 250.0, "baseThickness" to 300.0,
                    "waterLevel" to 2700.0
                ),
                bars = listOf(straight("WV", 12, 2900.0, 180.0))
            ),
            DetailingPackage(
                memberType = MemberType.STAIR, memberId = "S1",
                geometry = mapOf("span" to 4000.0, "riser" to 160.0, "tread" to 280.0),
                bars = listOf(straight("M1", 12, 3950.0, 150.0))
            )
        )
        val outDir = tmp.newFolder("dxf_multi")
        val export = CalculatorCadExporterV7.exportProject(
            packages = pkgs,
            bbs = CalculatorDetailingV4.buildBarSchedule(pkgs),
            outDir = outDir.absolutePath
        )
        assertTrue("3 sheets expected", export.sheets.size == 3)
        export.sheets.forEach { sheet ->
            val txt = sheet.file.readText()
            assertTrue("${sheet.memberId} missing EOF", txt.trimEnd().endsWith("EOF"))
            assertTrue("${sheet.memberId} missing ENTITIES", txt.contains("ENTITIES"))
        }
    }

    // Golden fixture for external CAD-parser audits (ezdxf) — writes to app/build/cad_fixture
    @Test
    fun `write golden dxf fixture`() {
        val pkg = sampleBeamPackage()
        val bbs = CalculatorDetailingV4.buildBarSchedule(listOf(pkg))
        val outDir = java.io.File(System.getProperty("user.dir"), "build/cad_fixture")
        outDir.deleteRecursively(); outDir.mkdirs()
        val export = CalculatorCadExporterV7.exportProject(listOf(pkg), bbs, outDir.absolutePath)
        assertTrue(export.sheets.isNotEmpty())
    }
}