package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.utils.JoistDetailCase
import org.junit.Assert.*
import org.junit.Test

class ECP2020JoistBottomTakeoffTest {
    private val support = JoistDetailCase.Support(JoistDetailCase.Restraint.SIMPLE,
        JoistDetailCase.SupportMaterial.CONCRETE, 1000.0, 0.0, 900.0)
    private val full = JoistDetailCase(8, 5000.0, 500.0, support, support,
        JoistDetailCase.LoadBasis(4.0, 3.0, 20.0, 5.0, 12.0, true),
        JoistDetailCase.Serviceability(JoistDetailCase.Exposure.PROTECTED_INTERIOR, 0.5, 60.0, null, false),
        JoistDetailCase.Detailing(25.0, 0.0, 20.0, 16, 2, 12000.0, false, 1000.0, 1000.0))
    private fun takeoff(c: JoistDetailCase = full) = ECP2020JoistBottomTakeoff.build(c, 30.0, 400.0, 150.0, 500.0, 250.0, 50.0)

    @Test fun `actual rib multiplicity and full straight development conserved`() {
        val s = takeoff().schedule!!
        val g = s.groups.single()
        val ld = ECP2020JoistRules.straightTensionDevelopmentMm(30.0, 400.0, 16, 33.0)
        assertEquals(16, g.totalCount)
        assertEquals(5000 + 2 * ld, g.cutLengthMm, 1e-8)
        assertEquals(-ld, g.startStationMm, 0.0)
        assertEquals(5000 + ld, g.endStationMm, 0.0)
        assertFalse(s.executionReady)
        assertEquals(g.totalCount * g.cutLengthMm / 1000 * 0.0061654 * 16 * 16, s.totalWeightKg, 1e-8)
    }
    @Test fun `stock overrun requires a splice strategy not an invented lap`() {
        val r = takeoff(full.copy(detailing = full.detailing!!.copy(stockLengthMm = 5500.0)))
        assertNull(r.schedule)
        assertTrue(r.reasons.single().contains("stock length"))
    }
    @Test fun `insufficient anchorage cannot make a straight schedule`() {
        assertNull(takeoff(full.copy(leftSupport = support.copy(availableAnchorageMm = 100.0))).schedule)
    }
    @Test fun `high shear cannot silently omit required links`() {
        assertNull(takeoff(full.copy(loadBasis = full.loadBasis!!.copy(ultimateShearKnPerRib = 100.0))).schedule)
    }
    @Test fun `incomplete actual rib count cannot become one rib`() {
        assertNull(takeoff(full.copy(actualRibCount = null)).schedule)
    }
    private fun review(c: JoistDetailCase = full, fcu: Double = 30.0, fy: Double = 400.0,
        web: Double = 150.0, pitch: Double = 500.0, depth: Double = 250.0, topping: Double = 50.0) =
        ECP2020JoistCaseReview.review(c, fcu, fy, web, pitch, depth, topping)

    private fun assertIncomplete(r: ECP2020JoistCaseReview.Result) {
        assertTrue(r.missingInputs.isNotEmpty())
        assertNull(r.section)
        assertNull(r.concreteOnlyShearKn)
        assertNull(r.bottomStraightDevelopmentMm)
        assertNull(r.crossRibsRequired)
        assertFalse(r.constructionReady)
    }
    @Test fun `nonfinite geometry produces a named incomplete review not an exception`() {
        for (v in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0)) {
            assertIncomplete(review(web = v))
            assertIncomplete(review(pitch = v))
            assertIncomplete(review(depth = v))
            assertIncomplete(review(topping = v))
        }
    }
    @Test fun `unsupported material range cannot escape as a helper exception`() {
        for (v in listOf(Double.NaN, 19.9, 60.1)) assertIncomplete(review(fcu = v))
        for (v in listOf(Double.NaN, 240.0, 460.0)) assertIncomplete(review(fy = v))
        assertIncomplete(review(fcu = 25.0, fy = 500.0))
    }
    @Test fun `flange below web never enters section arithmetic`() {
        assertIncomplete(review(full.copy(effectiveFlangeWidthMm = 149.0)))
    }
    @Test fun `cover consuming depth never enters section arithmetic`() {
        assertIncomplete(review(full.copy(detailing = full.detailing!!.copy(coverToLinkOutsideMm = 242.0))))
    }
    @Test fun `non hollow geometry and inverted topping are incomplete`() {
        assertIncomplete(review(web = 500.0))
        assertIncomplete(review(topping = 250.0))
    }
    @Test fun `bad geometry cannot produce even a partial bar schedule`() {
        assertNull(ECP2020JoistBottomTakeoff.build(full, 30.0, 400.0, Double.NaN, 500.0, 250.0, 50.0).schedule)
    }
    @Test fun `valid review stays partial and exposes effective span`() {
        val r = review()
        assertTrue(r.missingInputs.isEmpty())
        assertNotNull(r.section)
        assertEquals(5250.0, r.effectiveSpanMm!!, 0.0)
        assertFalse(r.constructionReady)
    }
    @Test fun `masonry width alone cannot authorize concrete bond development`() {
        val masonry = support.copy(material = JoistDetailCase.SupportMaterial.MASONRY)
        val c = full.copy(leftSupport = masonry)
        val r = review(c)
        assertTrue(r.unsupported.any { it.contains("anchorage region") })
        assertNull(r.bottomStraightDevelopmentMm)
        assertNull(r.leftAnchorageFits)
        assertNull(takeoff(c).schedule)
    }
    @Test fun `unknown support material cannot be silently treated as concrete`() {
        val other = support.copy(material = JoistDetailCase.SupportMaterial.OTHER)
        val c = full.copy(rightSupport = other)
        assertTrue(review(c).unsupported.isNotEmpty())
        assertNull(takeoff(c).schedule)
    }
    @Test fun `failed flexural section cannot produce adopted body quantities either`() {
        val r = review(full.copy(loadBasis = full.loadBasis!!.copy(ultimatePositiveMomentKnMPerRib = 1000.0)))
        assertNotNull(r.section)
        assertFalse(r.section!!.sectionPasses)
        assertFalse(ECP2020JoistCaseReview.permitsPartialBodyTakeoff(r))
    }
    @Test fun `failed concrete only shear cannot produce adopted body quantities`() {
        val r = review(full.copy(loadBasis = full.loadBasis!!.copy(ultimateShearKnPerRib = 100.0)))
        assertTrue(r.section!!.sectionPasses)
        assertFalse(ECP2020JoistCaseReview.permitsPartialBodyTakeoff(r))
    }
    @Test fun `incomplete and unsupported cases cannot produce body quantities`() {
        assertFalse(ECP2020JoistCaseReview.permitsPartialBodyTakeoff(review(full.copy(actualRibCount = null))))
        assertFalse(ECP2020JoistCaseReview.permitsPartialBodyTakeoff(review(full.copy(detailing = full.detailing!!.copy(seismicDetailingRequired = true)))))
        assertTrue(ECP2020JoistCaseReview.permitsPartialBodyTakeoff(review()))
    }
    @Test fun `duplicated calculation inputs must bind to the same physical detail case`() {
        val bind = com.civileg.app.domain.calculations.utils.HordiCaseBinding
        assertTrue(bind.conflicts(full, 5000.0, 20.0, 5.0, 25.0).isEmpty())
        assertEquals(1, bind.conflicts(full, 6000.0, 20.0, 5.0, 25.0).size)
        assertEquals(1, bind.conflicts(full, 5000.0, 21.0, 5.0, 25.0).size)
        assertEquals(1, bind.conflicts(full, 5000.0, 20.0, 6.0, 25.0).size)
        assertEquals(1, bind.conflicts(full, 5000.0, 20.0, 5.0, 30.0).size)
    }
    @Test fun `binding does not fill missing detail inputs from the engine defaults`() {
        val c = JoistDetailCase()
        assertTrue(com.civileg.app.domain.calculations.utils.HordiCaseBinding.conflicts(c, 5000.0, 20.0, 5.0, 25.0).isEmpty())
        assertTrue(c.unresolved().isNotEmpty())
        assertNull(c.clearSpanMm)
        assertNull(c.detailing)
    }
    @Test fun `bounded accepted partial schedule conserves bars over material and span cases`() {
        var issued = 0
        for (fcu in listOf(20.0, 30.0, 60.0)) for (fy in listOf(350.0, 400.0, 420.0, 500.0)) {
            if (fy == 500.0 && fcu < 30.0) continue
            for (count in listOf(1, 8, 31)) for (span in listOf(3000.0, 5000.0, 7000.0)) {
                val c = full.copy(actualRibCount = count, clearSpanMm = span,
                    loadBasis = full.loadBasis!!.copy(ultimatePositiveMomentKnMPerRib = 5.0))
                val r = ECP2020JoistBottomTakeoff.build(c, fcu, fy, 150.0, 500.0, 250.0, 50.0)
                val g = r.schedule?.groups?.single() ?: continue
                issued++
                val ld = ECP2020JoistRules.straightTensionDevelopmentMm(fcu, fy, 16, 33.0)
                assertEquals(count * 2, g.totalCount)
                assertEquals(span + 2 * ld, g.cutLengthMm, 1e-8)
                assertEquals(g.cutLengthMm, g.endStationMm - g.startStationMm, 1e-8)
                assertFalse(r.schedule!!.executionReady)
                assertTrue(g.cutLengthMm <= 12000.0)
            }
        }
        assertTrue("The grid must exercise issued schedules, not only rejected cases", issued > 0)
    }
}
