package com.civileg.app.domain.calculations.ecp

import org.junit.Assert.*
import org.junit.Test

class ECP2020JoistServiceInputsTest {
    private fun complete() = ECP2020JoistServiceInputs(
        ECP2020JoistServiceInputs.ServiceDemands(5.0, 9.0, 6.0),
        ECP2020JoistServiceInputs.SectionAnalysis(1e9, 4e8, 190.0, 402.0, 0.0, null, "Project analysis A"),
        ECP2020JoistServiceInputs.LoadHistory(60.0, ECP2020JoistServiceInputs.Attachments.NONE, null),
        ECP2020JoistServiceInputs.ExposureClass.I
    )
    @Test fun `missing contract does not invent section loads exposure or history`() {
        val result = ECP2020JoistServiceReadiness.review(null)
        assertEquals(ECP2020JoistServiceReadiness.State.MISSING_INPUTS, result.state)
        assertTrue(result.issues.any { it.key == "compression steel area" })
        assertTrue(result.issues.any { it.key == "sustained service moment" })
        assertTrue(result.issues.any { it.key == "ECP exposure class" })
        assertFalse(result.acceptanceAvailable)
        assertFalse(result.constructionReady)
    }
    @Test fun `complete data stays engineering review rather than pass`() {
        val result = ECP2020JoistServiceReadiness.review(complete())
        assertEquals(ECP2020JoistServiceReadiness.State.ENGINEERING_REVIEW_REQUIRED, result.state)
        assertTrue(result.issues.none { it.owner == ECP2020JoistServiceReadiness.Owner.PROJECT_INPUT })
        assertTrue(result.issues.any { it.key == "compression ratio definition" })
        assertFalse(result.acceptanceAvailable)
        assertFalse(result.constructionReady)
    }
    @Test fun `explicit zero compression steel does not require invented centroid`() {
        assertTrue(ECP2020JoistServiceReadiness.review(complete()).issues.none { it.key == "compression steel centroid" })
        val data = complete().copy(section = complete().section!!.copy(compressionSteelAreaMm2 = 100.0))
        assertTrue(ECP2020JoistServiceReadiness.review(data).issues.any { it.key == "compression steel centroid" })
    }
    @Test fun `invalid section inertia and nonfinite loads are named`() {
        val data = complete().copy(
            section = complete().section!!.copy(crackedInertiaMm4 = 2e9),
            demands = complete().demands!!.copy(sustainedMomentKnMPerRib = Double.NaN))
        val result = ECP2020JoistServiceReadiness.review(data)
        assertEquals(ECP2020JoistServiceReadiness.State.INVALID_INPUTS, result.state)
        assertTrue(result.issues.any { it.key == "inertia bounds" })
        assertTrue(result.issues.any { it.key == "sustained service moment" })
    }
    @Test fun `positive gravity service envelope is not silently reconciled`() {
        val result = ECP2020JoistServiceReadiness.review(complete().copy(
            demands = ECP2020JoistServiceInputs.ServiceDemands(10.0, 9.0, 6.0)))
        assertEquals(ECP2020JoistServiceReadiness.State.INVALID_INPUTS, result.state)
        assertTrue(result.issues.any { it.key == "service demand envelope" })
    }
    @Test fun `ordinary as well as sensitive attachments require installation time`() {
        for (kind in listOf(ECP2020JoistServiceInputs.Attachments.DEFLECTION_SENSITIVE,
            ECP2020JoistServiceInputs.Attachments.NOT_DEFLECTION_SENSITIVE)) {
            val data = complete().copy(history = complete().history!!.copy(attachments = kind))
            assertTrue(ECP2020JoistServiceReadiness.review(data).issues.any { it.key == "attachment installation time" })
        }
    }
    @Test fun `installation after evaluation is invalid`() {
        val data = complete().copy(history = ECP2020JoistServiceInputs.LoadHistory(
            6.0, ECP2020JoistServiceInputs.Attachments.NOT_DEFLECTION_SENSITIVE, 12.0))
        assertEquals(ECP2020JoistServiceReadiness.State.INVALID_INPUTS,
            ECP2020JoistServiceReadiness.review(data).state)
    }
    @Test fun `provenance is not optional and its presence is not verification`() {
        val data = complete().copy(section = complete().section!!.copy(analysisReference = " "))
        assertTrue(ECP2020JoistServiceReadiness.review(data).issues.any { it.key == "analysis reference" })
        assertEquals(ECP2020JoistServiceReadiness.State.ENGINEERING_REVIEW_REQUIRED,
            ECP2020JoistServiceReadiness.review(complete()).state)
    }
}
