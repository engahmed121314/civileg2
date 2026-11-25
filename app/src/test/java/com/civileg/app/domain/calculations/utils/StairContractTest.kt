package com.civileg.app.domain.calculations.utils

import com.civileg.app.domain.calculations.base.StairAnalysisStatus
import com.civileg.app.domain.calculations.base.StairSupportCondition
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Test

class StairContractTest {
    // This path does not consult SettingsManager; isolate the production wrapper from Android storage.
    private fun engine(): CalculatorEngine {
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        return (field.get(null) as sun.misc.Unsafe).allocateInstance(CalculatorEngine::class.java) as CalculatorEngine
    }
    private fun design(type: CalculatorEngine.StairType = CalculatorEngine.StairType.SINGLE_FLIGHT,
                       width: Double = 1400.0,
                       support: StairSupportCondition = StairSupportCondition.UNSPECIFIED,
                       landing: Double? = null) = engine().designStaircase(
        type, 4.0, 150.0, 300.0, 5.0, 4.0, 25.0, 360.0, 12,
        DesignCode.ECP, width, support, landing, landing, landing)

    @Test fun cantileverDoesNotIssueSingleFlightFallback() {
        val result = design(type = CalculatorEngine.StairType.CANTILEVER)
        assertFalse(result.isSafe)
        assertFalse(result.codeAcceptanceComplete)
        assertEquals(StairAnalysisStatus.UNSUPPORTED_CASE, result.analysisStatus)
        assertEquals(0.0, result.concreteVolume, 0.0)
        assertEquals(0.0, result.steelWeight, 0.0)
        assertTrue(result.qualificationNotes.any { it.contains("no design issued") })
    }
    @Test fun absentLandingIsNotInvented() {
        val result = design()
        assertNull(result.landingLengthMm)
        assertNull(result.landingWidthMm)
        assertNull(result.landingThicknessMm)
        assertEquals(StairSupportCondition.UNSPECIFIED, result.supportCondition)
        assertFalse(result.codeAcceptanceComplete)
        assertEquals(1400.0, result.stairWidthMm, 0.0)
    }
    @Test fun declaredGeometryAndSupportStayInSnapshot() {
        val result = design(support = StairSupportCondition.SIMPLY_SUPPORTED, landing = 1600.0)
        assertEquals(StairSupportCondition.SIMPLY_SUPPORTED, result.supportCondition)
        assertEquals(1600.0, result.landingLengthMm!!, 0.0)
        assertEquals(1600.0, result.landingWidthMm!!, 0.0)
        assertEquals(1600.0, result.landingThicknessMm!!, 0.0)
        assertFalse(result.codeAcceptanceComplete)
    }
    @Test fun widthScalesConcreteWithoutLiveFormAccess() {
        val narrow = design(width = 1000.0)
        val wide = design(width = 2000.0)
        assertTrue(narrow.concreteVolume > 0.0)
        assertEquals(2.0 * narrow.concreteVolume, wide.concreteVolume, 1e-8)
        assertEquals(1000.0, narrow.stairWidthMm, 0.0)
    }
    @Test fun quantitySnapshotDoesNotInventFinalCuttingLengths() {
        val r = design()
        val q = r.quantityReview!!
        assertNull(q.finalSteelKg)
        assertFalse(q.fabricationScheduleAvailable)
        assertEquals(q.main.totalNetWeightKg + q.distribution.totalNetWeightKg, r.steelWeight, 1e-8)
        assertTrue(q.distribution.totalNetWeightKg > 1.0)
        assertEquals(StairFlightGeometry.from(r.span, r.riser, r.tread).let {
            kotlin.math.sqrt(it.runMm * it.runMm + it.riseMm * it.riseMm)
        }, q.main.netFlightLengthMm, 1e-8)
    }
    @Test fun quantityReviewUsesCeilingAndCoverToBarSurface() {
        val q = StairQuantityReviewer.review(1200.0,3900.0,2100.0,25.0,12,125.0,8,250.0)
        assertEquals(11,q.main.count)
        assertEquals(1150.0,q.distribution.netFlightLengthMm,0.0)
        assertTrue((1200.0 - 50.0 - 12.0) / (q.main.count - 1) <= 125.0)
        assertNull(q.finalSteelKg)
    }
    @Test fun unresolvedCuttingShapeNeverBecomesFabricationBbs() {
        val r = design()
        assertTrue(BbsGenerator.generateStairBbs("ST1", r, r.stairWidthMm).isEmpty())
        val steel = BoqEstimator.stairRows(r, r.stairWidthMm / 1000.0, "ST1")[1]
        assertTrue(steel.description.contains("Net flight steel only"))
        assertEquals(r.quantityReview!!.netFlightSteelKg, steel.quantity, 1e-8)
    }
    @Test fun persistedResultRetainsGeometryAndPartialQuantityScope() {
        val original = design(support = StairSupportCondition.SIMPLY_SUPPORTED, landing = 1600.0)
        val gson = com.google.gson.Gson()
        val saved = gson.toJson(original)
        val restored = gson.fromJson(saved, CalculatorEngine.StairResult::class.java)
        assertEquals(original.stairWidthMm, restored.stairWidthMm, 0.0)
        assertEquals(original.supportCondition, restored.supportCondition)
        assertEquals(original.landingLengthMm, restored.landingLengthMm)
        assertEquals(original.quantityReview, restored.quantityReview)
        assertNull(restored.quantityReview!!.finalSteelKg)
        assertFalse(restored.codeAcceptanceComplete)
        assertFalse(restored.quantityReview!!.fabricationScheduleAvailable)
    }
}
