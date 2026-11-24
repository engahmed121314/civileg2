package com.civileg.app.domain.calculations.utils

import com.civileg.app.domain.calculations.ecp.ECP2020JoistCaseReview
import org.junit.Assert.*
import org.junit.Test

class JoistDetailCaseTest {
    private val support = JoistDetailCase.Support(JoistDetailCase.Restraint.SIMPLE,
        JoistDetailCase.SupportMaterial.CONCRETE, 1000.0, 0.0, 900.0)
    private val full = JoistDetailCase(8, 5000.0, 500.0, support, support,
        JoistDetailCase.LoadBasis(4.0, 3.0, 20.0, 5.0, 12.0, true),
        JoistDetailCase.Serviceability(JoistDetailCase.Exposure.PROTECTED_INTERIOR, 0.5, 60.0, null, false),
        JoistDetailCase.Detailing(25.0, 0.0, 20.0, 16, 2, 12000.0, false, 1000.0, 1000.0))
    private fun review(p: JoistDetailCase) = ECP2020JoistCaseReview.review(p, 30.0, 400.0, 150.0, 500.0, 250.0, 50.0)

    @Test fun `empty case reports missing groups instead of adopting defaults`() {
        val r = review(JoistDetailCase())
        assertEquals(8, r.missingInputs.size)
        assertNull(r.section)
        assertFalse(r.constructionReady)
    }
    @Test fun `explicit complete case unlocks bounded checks not approval`() {
        assertTrue(full.unresolved().isEmpty())
        val r = review(full)
        assertNotNull(r.section)
        assertTrue(r.section!!.sectionPasses)
        assertTrue(r.leftAnchorageFits!!)
        assertTrue(r.shearDemandWithinConcreteThreshold!!)
        assertEquals(1, r.crossRibsRequired)
        assertEquals(5250.0, r.effectiveSpanMm!!, 0.0)
        assertFalse(r.constructionReady)
    }
    @Test fun `negative moment is unsupported not ignored`() {
        val r = review(full.copy(leftSupport = support.copy(ultimateNegativeMomentKnM = 10.0)))
        assertTrue(r.unsupported.any { it.contains("Negative-moment") })
        assertNull(r.section)
    }
    @Test fun `actual rib count is required even when pitch is known`() {
        val r = review(full.copy(actualRibCount = null))
        assertTrue(r.missingInputs.any { it.contains("rib count") })
        assertNull(r.section)
    }
    @Test fun `short anchorage space fails without inventing hooks`() {
        val r = review(full.copy(leftSupport = support.copy(availableAnchorageMm = 100.0)))
        assertFalse(r.leftAnchorageFits!!)
        assertTrue(r.rightAnchorageFits!!)
        assertFalse(r.constructionReady)
    }
    @Test fun `link design is not replaced by a no-link calculation`() {
        val r = review(full.copy(detailing = full.detailing!!.copy(linkDiameterMm = 8.0)))
        assertTrue(r.unsupported.any { it.contains("web reinforcement") })
        assertNull(r.section)
    }
    @Test fun `sustained fraction and duration are checked`() {
        val p = full.copy(serviceability = full.serviceability!!.copy(sustainedLoadFraction = 2.0, durationMonths = Double.NaN))
        assertEquals(2, p.unresolved().size)
    }
    @Test fun `partition timing is not inferred`() {
        val p = full.copy(serviceability = full.serviceability!!.copy(deflectionSensitivePartitions = true))
        assertTrue(p.unresolved().contains("Partition installation time"))
    }
    @Test fun `seismic requirement blocks ordinary strategy`() {
        assertTrue(review(full.copy(detailing = full.detailing!!.copy(seismicDetailingRequired = true))).unsupported.any { it.contains("Seismic") })
    }
    @Test fun `deep rib cannot use no-link exception`() {
        val r = ECP2020JoistCaseReview.review(full, 30.0, 400.0, 150.0, 500.0, 300.0, 50.0)
        assertNull(r.section)
        assertTrue(r.unsupported.any { it.contains("concrete-only beam exception") })
    }
    @Test fun `minimum clear spacing cannot be replaced by bar area check`() {
        val p = full.copy(detailing = full.detailing!!.copy(barsPerRib = 4))
        assertTrue(review(p).unsupported.any { it.contains("clear spacing") })
    }
    @Test fun `higher exposure cover cannot use protected interior cover`() {
        val p = full.copy(serviceability = full.serviceability!!.copy(exposure = JoistDetailCase.Exposure.CORROSIVE))
        assertTrue(review(p).unsupported.any { it.contains("cover") })
    }
    @Test fun `panel count overflow is a validation error before schedule generation`() {
        val p = full.copy(actualRibCount = Int.MAX_VALUE)
        assertTrue(p.unresolved().any { it.contains("supported range") })
        assertNull(review(p).section)
    }
    @Test fun `anchorage cannot exceed the stated support geometry`() {
        val p = full.copy(leftSupport = support.copy(widthMm = 300.0, availableAnchorageMm = 900.0))
        assertTrue(review(p).unsupported.any { it.contains("support width") })
    }
    @Test fun `masonry bearing minimum is not silently treated as concrete`() {
        val p = full.copy(leftSupport = support.copy(material = JoistDetailCase.SupportMaterial.MASONRY,
            widthMm = 199.0, availableAnchorageMm = 150.0))
        assertTrue(review(p).unsupported.any { it.contains("Masonry bearing") })
    }
    @Test fun `rib flange cannot overlap neighboring periodic tributary width`() {
        assertTrue(review(full.copy(effectiveFlangeWidthMm = 501.0)).unsupported.any { it.contains("rib pitch") })
    }
}
