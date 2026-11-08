package com.civileg.app.utils

import com.civileg.app.domain.entities.DesignCode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [QualityControlEngine].
 *
 * Verifies preliminary screening behavior and the selected-code labels used
 * by the exported PDF report.
 */
class QualityControlEngineTest {

    // ==================================================================
    // codeName
    // ==================================================================
    @Test
    fun codeName_mapsEachDesignCode() {
        DesignCode.entries.forEach { code ->
            assertEquals(code.version, QualityControlEngine.codeName(code))
        }
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
        assertTrue(outcome.checks[0].reference?.contains(DesignCode.ECP.version) == true)
        assertTrue(outcome.checks[1].reference?.contains("approved mix") == true)
        assertTrue(QualityControlEngine.SCREENING_DISCLAIMER.contains("not proof of code compliance"))
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
        assertTrue(outcome.checks[0].reference?.contains(DesignCode.ECP.version) == true)
        assertTrue(outcome.checks[0].reference?.contains("not fully modeled") == true)
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

    @Test
    fun steelQcDetailed_convertsSteelTableUnitsAndUsesSelectedCodeBasis() {
        val expectedBases = mapOf(
            DesignCode.ECP to "ECP 205-2007 (steel)",
            DesignCode.ACI to "AISC 360-22 (US steel; ACI 318 is concrete)",
            DesignCode.SBC to "SBC 306-2018 (steel)"
        )

        expectedBases.forEach { (code, basis) ->
            val outcome = QualityControlEngine.steelQcDetailed(
                code = code,
                sectionKey = "IPE 200",
                fyMpa = 275.0,
                spanM = 5.0,
                muKnM = 40.0,
                vuKn = 100.0
            )

            assertTrue(outcome.details.any { it.first.startsWith("Gross area") && it.second == "2850 mm²" })
            assertTrue(outcome.details.any { it.first.startsWith("Major-axis inertia") && it.second == "19430000 mm⁴" })
            assertTrue(outcome.details.contains("Selected steel design basis" to basis))
            assertEquals(54.45, outcome.checks.first().limit, 0.01)
            assertTrue(outcome.checks.all { it.reference?.contains(basis) == true })
        }
    }

    @Test
    fun steelQcDetailed_doesNotClaimAcceptanceWhenLateralBucklingIsUnverified() {
        val outcome = QualityControlEngine.steelQcDetailed(
            code = DesignCode.ACI,
            sectionKey = "IPE 200",
            fyMpa = 275.0,
            spanM = 5.0,
            muKnM = 10.0,
            vuKn = 50.0,
            unbracedLengthMm = 3000.0
        )

        assertFalse(outcome.passed)
        assertTrue(outcome.checks.any {
            it.name == "Lateral-torsional buckling analysis available" && !it.passed
        })
    }

    @Test
    fun steelQcDetailed_columnUsesWeakAxisSlendernessAndBucklingCapacity() {
        val outcome = QualityControlEngine.steelQcDetailed(
            code = DesignCode.SBC,
            sectionKey = "IPE 200",
            fyMpa = 275.0,
            spanM = 3.0,
            muKnM = 0.0,
            vuKn = 0.0,
            puKn = 200.0,
            unbracedLengthMm = 3000.0,
            memberType = "COLUMN"
        )

        assertTrue(outcome.checks.any {
            it.name == "Flexural buckling capacity φP_n ≥ P_u" && it.passed
        })
        assertTrue(outcome.details.contains("Minor-axis slenderness KL/r_y" to "134"))
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

    @Test
    fun roadsQc_zeroAirVoids_failsWithoutSubstitutingHiddenDefaults() {
        val outcome = QualityControlEngine.roadsQc(
            surfaceThicknessMm = 50.0,
            baseThicknessMm = 150.0,
            subbaseThicknessMm = 200.0,
            compactionPercent = 97.0,
            asphaltDensityKnM3 = 23.0,
            airVoidsPercent = 0.0
        )

        assertFalse(outcome.passed)
        assertFalse(outcome.checks.last().passed)
        assertEquals(0.0, outcome.checks.last().calculated, 0.0)
    }

    // ==================================================================
    // BRIDGES
    // ==================================================================
    @Test
    fun bridgeQc_projectStrengthAndDeflectionPass() {
        val outcome = QualityControlEngine.bridgeQc(
            code = DesignCode.ECP,
            concreteStrengthMpa = 30.0,
            requiredConcreteStrengthMpa = 25.0,
            spanM = 20.0,
            naturalFrequencyHz = 1.0,
            measuredDeflectionMm = 40.0,
            allowableDeflectionMm = 50.0
        )
        assertTrue(outcome.passed)
    }

    @Test
    fun bridgeQc_lowMeasuredStrength_fails() {
        val outcome = QualityControlEngine.bridgeQc(
            code = DesignCode.ECP,
            concreteStrengthMpa = 24.0,
            requiredConcreteStrengthMpa = 25.0,
            spanM = 20.0,
            naturalFrequencyHz = 2.5,
            measuredDeflectionMm = 40.0,
            allowableDeflectionMm = 50.0
        )
        assertFalse(outcome.passed)
    }

    @Test
    fun bridgeQc_deflectionOverProjectLimit_fails() {
        val outcome = QualityControlEngine.bridgeQc(
            code = DesignCode.SBC,
            concreteStrengthMpa = 30.0,
            requiredConcreteStrengthMpa = 25.0,
            spanM = 20.0,
            naturalFrequencyHz = 2.5,
            measuredDeflectionMm = 51.0,
            allowableDeflectionMm = 50.0
        )
        assertFalse(outcome.passed)
    }

    @Test
    fun bridgeQc_frequencyDoesNotDetermineAcceptance() {
        val outcome = QualityControlEngine.bridgeQc(
            code = DesignCode.ACI,
            concreteStrengthMpa = 30.0,
            requiredConcreteStrengthMpa = 25.0,
            spanM = 20.0,
            naturalFrequencyHz = 0.5,
            measuredDeflectionMm = 40.0,
            allowableDeflectionMm = 50.0
        )
        assertTrue(outcome.passed)
    }

    @Test
    fun bridgeQcDetailed_usesProjectInputsAndInformationalFrequencyForAllCodes() {
        listOf(DesignCode.ECP, DesignCode.ACI, DesignCode.SBC).forEach { code ->
            val outcome = QualityControlEngine.bridgeQcDetailed(
                code = code,
                spanM = 20.0,
                measuredConcreteStrengthMpa = 30.0,
                requiredConcreteStrengthMpa = 25.0,
                measuredDeflectionMm = 40.0,
                allowableDeflectionMm = 50.0,
                naturalFrequencyHz = 0.0
            )

            assertTrue(outcome.passed)
            assertEquals(2, outcome.checks.size)
            assertTrue(
                outcome.checks.all {
                    it.reference?.contains(QualityControlEngine.codeName(code)) == true
                }
            )
            assertTrue(outcome.details.any { it.second == "Not provided" })
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun bridgeQcDetailed_requiresPositiveProjectDeflectionLimit() {
        QualityControlEngine.bridgeQcDetailed(
            code = DesignCode.SBC,
            spanM = 20.0,
            measuredConcreteStrengthMpa = 30.0,
            requiredConcreteStrengthMpa = 25.0,
            measuredDeflectionMm = 0.0,
            allowableDeflectionMm = 0.0
        )
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

    @Test
    fun buildingQc_baseShearBelowMinimum_fails() {
        val outcome = QualityControlEngine.buildingQc(
            code = DesignCode.ACI,
            numFloors = 2,
            floorHeightM = 3.0,
            totalWeightKn = 5000.0,
            importanceFactor = 1.0,
            sds = 0.02,
            responseR = 5.0,
            storyDrift = 0.015
        )
        assertFalse(outcome.passed)
        // The raw (unclamped) base-shear coefficient is below C_s,min.
        assertFalse(outcome.checks[1].passed)
    }

    // ==================================================================
    // CONCRETE - air content and temperature
    // ==================================================================
    @Test
    fun concreteQc_highAirContent_fails() {
        val outcome = QualityControlEngine.concreteQc(
            code = DesignCode.ACI,
            elementId = "C3",
            volumeM3 = 10.0,
            slumpMm = 75.0,
            fcuDesignMpa = 25.0,
            strength7DaysMpa = 20.0,
            strength28DaysMpa = 30.0,
            airContent = 9.5,
            concreteTemp = 25.0
        )
        assertFalse(outcome.passed)
        assertFalse(outcome.checks[4].passed)
    }

    @Test
    fun concreteQc_highTemperature_fails() {
        val outcome = QualityControlEngine.concreteQc(
            code = DesignCode.ACI,
            elementId = "C4",
            volumeM3 = 10.0,
            slumpMm = 75.0,
            fcuDesignMpa = 25.0,
            strength7DaysMpa = 20.0,
            strength28DaysMpa = 30.0,
            airContent = 4.0,
            concreteTemp = 45.0
        )
        assertFalse(outcome.passed)
        assertFalse(outcome.checks[5].passed)
    }
}
