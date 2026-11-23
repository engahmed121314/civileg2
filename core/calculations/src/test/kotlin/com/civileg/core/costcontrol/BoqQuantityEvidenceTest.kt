package com.civileg.core.costcontrol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoqQuantityEvidenceTest {
    private fun evidence(origin: QuantityOrigin, quantity: CostDecimal? = CostDecimal("25"),
                         missing: List<String> = emptyList(), reviewed: Boolean = true,
                         revision: String? = null) = BoqQuantityEvidence(origin, quantity, "m2", "User room dimensions",
        revision, "Declared net geometry after supplied openings", missing, reviewed)

    @Test fun manualAndReviewedGeometryCanSupplyAnItemButAssumptionsCannot() {
        assertTrue(evidence(QuantityOrigin.MANUAL).readyForNonEstimatedItem)
        assertTrue(evidence(QuantityOrigin.DECLARED_GEOMETRY).readyForNonEstimatedItem)
        assertFalse(evidence(QuantityOrigin.ASSUMED).readyForNonEstimatedItem)
        assertFalse(evidence(QuantityOrigin.MISSING_INPUT, quantity = null, missing = listOf("wall heights")).readyForNonEstimatedItem)
        assertFalse(evidence(QuantityOrigin.DECLARED_GEOMETRY, missing = listOf("openings")).readyForNonEstimatedItem)
        assertFalse(evidence(QuantityOrigin.MANUAL, reviewed = false).readyForNonEstimatedItem)
    }

    @Test fun verifiedDesignRequiresRevisionAndQuantityScopeStillNeedsReview() {
        var rejected = false
        try { evidence(QuantityOrigin.VERIFIED_DESIGN) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertTrue(evidence(QuantityOrigin.VERIFIED_DESIGN, revision = "frozen-design-revision").readyForNonEstimatedItem)
    }
}
