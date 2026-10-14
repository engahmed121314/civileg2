package com.civileg.app.utils

import com.civileg.app.domain.entities.DesignCode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [QualityControlEngine].
 *
 * Verifies code-referenced pass/fail behaviour for every discipline and the
 * code-name mapping used by the exported PDF report.
 */
class QualityControlEngineTest {

    // ==================================================================
    // codeName
    // ==================================================================
    @Test
    fun codeName_mapsEachDesignCode() {
        assertEquals("ECP 203-2020", QualityControlEngine.codeName(DesignCode.ECP))
        assertEquals("ACI 318-19", QualityControlEngine.codeName(DesignCode.ACI))
        assertEquals("SBC 304-2018", QualityControlEngine.codeName(DesignCode.SBC))
    }

    // ==================================================================
    // CONCRETE
    // ==================================================================
    @Test
    fun concreteQc_allCriteriaMet_passes() {
        val outcome = QualityControlEngine.concreteQc(
            code = DesignCode.ECP,
            elementId = "C1",
            volumeM3 = 12.0,
            slumpMm = 75.0,
            fcuDesignMpa = 25.0,
            strength7DaysMpa = 20.0,
            strength28DaysMpa = 30.0
        )
        assertTrue(outcome.passed)
        assertEquals(6, outcome.checks.size)
        // utilization = 30 / 25 = 1.2
        assertEquals(1.2, outcome.utilizationRatio, 1e-6)
    }

    @Test
    fun concreteQc_lowStrengthAndHighSlump_fails() {
        val outcome = QualityControlEngine.concreteQc(
            code = DesignCode.ACI,
            elementId = "B2",
            volumeM3 = 8.0,
            slumpMm = 200.0,
            fcuDesignMpa = 25.0,
            strength7DaysMpa = 10.0,
            strength28DaysMpa = 18.0
        )
        assertFalse(outcome.passed)
        // 28-day strength check must fail
        assertFalse(outcome.checks[1].passed)
        // slump out of range must fail
        assertFalse(outcome.checks[0].passed)
    }

    // ==================================================================
    // REBAR
    // ==================================================================
    @Test
    fun rebarQc_typicalBeamDetail_passes() {
        val outcome = QualityControlEngine.rebarQc(
            code = DesignCode.ECP,
            barDiameterMm = 16.0,
            numBars = 3,
            fyMpa = 400.0,
            fcuMpa = 25.0,
            coverMm = 40.0,
            beamWidthMm = 250.0,
            effectiveDepthMm = 560.0
        )
        assertTrue(outcome.passed)
        assertEquals(4, outcome.checks.size)
    }

    @Test
    fun rebarQc_insufficientCover_fails() {
        val outcome = QualityControlEngine.rebarQc(
            code = DesignCode.ECP,
            barDiameterMm = 16.0,
            numBars = 3,
            fyMpa = 400.0,
            fcuMpa = 25.0,
            coverMm = 20.0,
            beamWidthMm = 250.0,
            effectiveDepthMm = 560.0
        )
        assertFalse(outcome.passed)
    }

    // ==================================================================
    // STEEL
    // ==================================================================
    @Test
    fun steelQc_underloadedIpe200_passes() {
        val outcome = QualityControlEngine.steelQc(
            code = DesignCode.ECP,
            sectionKey = "IPE 200",
            fyMpa = 275.0,
            spanM = 5.0,
            muKnM = 40.0,
            vuKn = 100.0
        )
        assertTrue(outcome.passed)
        assertEquals(4, outcome.checks.size)
    }

    @Test
    fun steelQc_overloadedMoment_fails() {
        val outcome = QualityControlEngine.steelQc(
            code = DesignCode.ECP,
            sectionKey = "IPE 200",
            fyMpa = 275.0,
            spanM = 5.0,
            muKnM = 60.0,
            vuKn = 100.0
        )
        assertFalse(outcome.passed)
    }

    @Test
    fun steelQc_unknownSection_failsGracefully() {
        val outcome = QualityControlEngine.steelQc(
            code = DesignCode.ECP,
            sectionKey = "NOT-A-SECTION",
            fyMpa = 275.0,
            spanM = 5.0,
            muKnM = 10.0,
            vuKn = 10.0
        )
        assertFalse(outcome.passed)
    }

    // ==================================================================
    // ROADS
    // ==================================================================
    @Test
    fun roadsQc_compliantPavement_passes() {
        val outcome = QualityControlEngine.roadsQc(
            surfaceThicknessMm = 50.0,
            baseThicknessMm = 150.0,
            subbaseThicknessMm = 200.0,
            compactionPercent = 97.0,
            asphaltDensityKnM3 = 23.0,
            airVoidsPercent = 4.0
        )
        assertTrue(outcome.passed)
        assertEquals(1.0, outcome.utilizationRatio, 1e-9)
    }

    @Test
    fun roadsQc_excessAirVoids_fails() {
        val outcome = QualityControlEngine.roadsQc(
            surfaceThicknessMm = 50.0,
            baseThicknessMm = 150.0,
            subbaseThicknessMm = 200.0,
            compactionPercent = 97.0,
            asphaltDensityKnM3 = 23.0,
            airVoidsPercent = 8.0
        )
        assertFalse(outcome.passed)
    }

    // ==================================================================
    // BRIDGES
    // ==================================================================
    @Test
    fun bridgeQc_adequateFrequency_passes() {
        val outcome = QualityControlEngine.bridgeQc(
            code = DesignCode.ECP,
            concreteStrengthMpa = 30.0,
            spanM = 20.0,
            naturalFrequencyHz = 2.5
        )
        assertTrue(outcome.passed)
    }

    @Test
    fun bridgeQc_lowFrequency_fails() {
        val outcome = QualityControlEngine.bridgeQc(
            code = DesignCode.ECP,
            concreteStrengthMpa = 30.0,
            spanM = 20.0,
            naturalFrequencyHz = 1.0
        )
        assertFalse(outcome.passed)
    }

    // ==================================================================
    // TUNNELS
    // ==================================================================
    @Test
    fun tunnelQc_stableRockClassIII_passes() {
        val outcome = QualityControlEngine.tunnelQc(
            rockClass = "III",
            convergenceMm = 20.0,
            diameterM = 6.0,
            supportThicknessMm = 150.0
        )
        assertTrue(outcome.passed)
    }

    @Test
    fun tunnelQc_excessiveConvergence_fails() {
        val outcome = QualityControlEngine.tunnelQc(
            rockClass = "III",
            convergenceMm = 100.0,
            diameterM = 6.0,
            supportThicknessMm = 150.0
        )
        assertFalse(outcome.passed)
    }

    // ==================================================================
    // BUILDINGS
    // ==================================================================
    @Test
    fun buildingQc_regularFrame_passes() {
        val outcome = QualityControlEngine.buildingQc(
            code = DesignCode.ACI,
            numFloors = 5,
            floorHeightM = 3.0,
            totalWeightKn = 50000.0,
            importanceFactor = 1.0,
            sds = 0.5,
            responseR = 5.0,
            storyDrift = 0.015
        )
        assertTrue(outcome.passed)
        assertEquals(4, outcome.checks.size)
    }

    @Test
    fun buildingQc_excessiveDrift_fails() {
        val outcome = QualityControlEngine.buildingQc(
            code = DesignCode.ACI,
            numFloors = 5,
            floorHeightM = 3.0,
            totalWeightKn = 50000.0,
            importanceFactor = 1.0,
            sds = 0.5,
            responseR = 5.0,
            storyDrift = 0.030
        )
        assertFalse(outcome.passed)
    }
}
