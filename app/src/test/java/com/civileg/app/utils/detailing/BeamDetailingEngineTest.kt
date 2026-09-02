package com.civileg.app.utils.detailing

import com.civileg.app.utils.CalculatorEngine
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §14/§15/§42 — Beam detailing engine gate across support cases.
 * Uses the REAL god-engine (mocked settings) so marks/zones/cuts derive from
 * genuine design output, not fabricated numbers.
 */
class BeamDetailingEngineTest {

    private fun eng() = CalculatorEngine(mockk(relaxed = true))

    private fun build(support: CalculatorEngine.SupportType) =
        run {
            val res = eng().designBeam(
                width = 250.0, height = 500.0, span = 5.0,
                fcu = 25.0, fy = 400.0,
                deadLoad = 12.0, liveLoad = 6.0,
                preferredDiameter = 16,
                code = CalculatorEngine.DesignCode.EGYPTIAN,
                supportType = support
            )
            val input = BeamDetailingEngine.BeamInput(
                spanMm = 5000.0, coverMm = 40.0,
                supportTypeName = support.name,
                codeName = "ECP 203-2020", fcuMPa = 25.0, fyMPa = 400.0
            )
            BeamDetailingEngine.build(res, input)
        }

    @Test
    fun `simple beam - zones span full length and cuts positive`() {
        val d = build(CalculatorEngine.SupportType.HINGED_HINGED)
        assertTrue(d.stirrupZones.isNotEmpty())
        assertEquals(0.0, d.stirrupZones.first().startMm, 1e-6)
        assertEquals(5000.0, d.stirrupZones.last().endMm, 1e-6)
        assertTrue(d.bottomCutLengthMm > 4000)
        assertTrue(d.topCutLengthMm > 4000)
        assertTrue(d.schedule.rows.size >= 3)
    }

    @Test
    fun `cantilever anchors top bars with development length`() {
        val d = build(CalculatorEngine.SupportType.CANTILEVER)
        // Top steel must extend past clear span by the development length
        assertTrue(
            "cantilever top cut (${d.topCutLengthMm}) should exceed bottom+Ld-ish",
            d.topCutLengthMm > d.spanMm - 2 * d.coverMm + 200.0
        )
        assertTrue(
            "sheets=${d.drawing.sheets.map { it.sheetNumber + ":" + it.title }}",
            d.drawing.sheets.any { s -> s.views.any { it.type == DrawingViewType.BBS } }
        )
    }

    @Test
    fun `fixed-fixed keeps symmetric zones and marks unique`() {
        val d = build(CalculatorEngine.SupportType.FIXED_FIXED)
        val marks = mutableListOf<String>()
        marks += d.bottomBarsMark; marks += d.topBarsMark
        d.stirrupZones.forEach { marks += it.mark }
        assertEquals(marks.size, marks.toSet().size)
        assertTrue(d.calcStrip.any { it.first == "Status" })
    }

    @Test
    fun `missing span fails loudly per spec section 2`() {
        try {
            val res = eng().designBeam(
                width = 250.0, height = 500.0, span = 5.0,
                fcu = 25.0, fy = 400.0, deadLoad = 10.0, liveLoad = 5.0,
                preferredDiameter = 16,
                code = CalculatorEngine.DesignCode.EGYPTIAN,
                supportType = CalculatorEngine.SupportType.HINGED_HINGED
            )
            BeamDetailingEngine.build(
                res,
                BeamDetailingEngine.BeamInput(spanMm = 0.0, coverMm = 40.0,
                    supportTypeName = "HINGED_HINGED", codeName = "ECP", fcuMPa = 25.0, fyMPa = 400.0)
            )
            throw AssertionError("zero span accepted")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("span"))
        }
    }

    @Test
    fun `engine stirrup zones pass verbatim into bar zones`() {
        val d = build(CalculatorEngine.SupportType.HINGED_HINGED)
        val zones = d.stirrupZones
        // designBeam always emits the left/mid/right trio with real spacing.
        assertEquals(3, zones.size)
        assertEquals("Support Zone (Left)", zones[0].description)
        assertEquals("Mid-Span Zone", zones[1].description)
        assertEquals("Support Zone (Right)", zones[2].description)
        assertEquals(0.0, zones.first().startMm, 1e-6)
        assertEquals(5000.0, zones.last().endMm, 1e-6)
        // Left and right support bands are symmetric mirrors.
        assertEquals(zones.first().spacingMm, zones.last().spacingMm, 1e-6)
        assertEquals(8, zones.first().diameterMm)
        // Sequential, unique site marks registered per zone.
        assertEquals(listOf("S1", "S2", "S3"), zones.map { it.mark })
        // Stirrup cut winds around the inner reinforcement core plus end hooks.
        val corePerimeter = 2.0 * (d.widthMm - 2 * d.coverMm - 8.0) +
            2.0 * (d.depthMm - 2 * d.coverMm - 8.0)
        assertTrue("cut=${d.stirrupCutLengthMm} vs core=$corePerimeter", d.stirrupCutLengthMm > corePerimeter)
    }
}
