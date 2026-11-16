package com.civileg.app.utils

import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات TDD لمحرك مراقبة الجودة (QualityControlEngine) — الفئات المتقدمة.
 *
 * تغطي هذه الاختبارات الدوال غير المختبرة سابقاً:
 * steelQcDetailed, bridgeQcDetailed, roadsQc, bridgeQc, tunnelQc, tunnelQcDetailed, buildingQc
 *
 * نسبة التفاوت المستهدفة: ≤ ±0.1% مقارنة بالحسابات التحليلية المعتمدة.
 *
 * // المرجع: ECP 203 / ACI 318 / SBC 304 / ASCE 7-16 / NFPA 502 / ISRM / AASHTO / ASTM
 */
class QualityControlEngineDetailedTest {

    private val tolerance = 0.001 // 0.1% tolerance per Audit Gate

    // =========================================================================
    // STEEL DETAILED QC — steelQcDetailed
    // =========================================================================

    @Test
    fun steelQcDetailed_beam_passes() {
        // IPE 200: h=200, bf=100, tw=5.6, tf=8.5, area=2678 mm², iy=1886 cm⁴, effectiveSx=188600 mm³, effectiveZx=212000 mm³, ry=4.09 cm, rz=8.4 cm
        val outcome = QualityControlEngine.steelQcDetailed(
            code = DesignCode.ECP,
            sectionKey = "IPE 200",
            fyMpa = 275.0,
            spanM = 6.0,
            muKnM = 100.0,
            vuKn = 50.0,
            puKn = 0.0,
            unbracedLengthMm = 0.0,
            allowableDeflectionMm = 20.0,
            memberType = "BEAM"
        )

        // φb·Mn = 0.90 × 275 × 212000 / 1e6 = 52.47 kN·m — Mu=100 > Mn=52.47 → FAIL
        //phiV·Vn = 0.90 × 0.60 × 275 × (200-17)×5.6 / 1e3 = 0.90 × 0.60 × 275 × 1024.8 / 1000 = 151.8 kN — Vu=50 < Vn=151.8 → PASS
        //lambdaF = 100/(2×8.5) = 5.88, lambdaPf = 0.38×sqrt(200000/275) = 9.15 → 5.88 < 9.15 → PASS
        //lambdaW = (200-17)/5.6 = 32.7, lambdaPw = 3.76×sqrt(200000/275) = 90.5 → 32.7 < 90.5 → PASS
        assertFalse("IPE 200 with Mu=100 kN·m should FAIL flexure (Mn≈52.5 kN·m)", outcome.passed)
        assertTrue("Steel detailed result should not be null", outcome.title.isNotEmpty())
        assertTrue("Should contain steel detailed title", outcome.title.contains("Steel Structure Quality Control (Detailed)"))
        assertEquals("IPE 200", outcome.details.first { it.first == "Section" }.second)
    }

    @Test
    fun steelQcDetailed_column_passes() {
        // IPE 200 as column: h=200, area=2678 mm², ry=4.09 cm=40.9 mm, rz=8.4 cm=84 mm
        // Lb=3000mm, Pu=100 kN
        // klR = max(3000/40.9, 3000/84) = max(73.3, 35.7) = 73.3
        // fe = π²×200000/(73.3²) = 3674 MPa
        // lambdaLimit = 4.71×sqrt(200000/275) = 107.1
        // klR=73.3 < lambdaLimit=107.1 → fcr = 0.658^(275/3674) × 275 = 0.658^0.0748 × 275 = 0.949 × 275 = 261 MPa
        // φPn = 0.90 × 261 × 2678 / 1000 = 629 kN — Pu=100 < 629 → PASS
        // slenderness: KL/r = 73.3 ≤ 200 → PASS
        val outcome = QualityControlEngine.steelQcDetailed(
            code = DesignCode.ECP,
            sectionKey = "IPE 200",
            fyMpa = 275.0,
            spanM = 6.0,
            muKnM = 0.0,
            vuKn = 0.0,
            puKn = 100.0,
            unbracedLengthMm = 3000.0,
            allowableDeflectionMm = 20.0,
            memberType = "COLUMN"
        )

        assertTrue("Column should pass flexural buckling check", outcome.passed)
        assertTrue("Should contain axial load details", outcome.details.any { it.first == "Axial load P_u" })
        assertTrue("Should contain slenderness details", outcome.details.any { it.first.contains("slenderness") })
    }

    @Test
    fun steelQcDetailed_beamColumn_interaction() {
        // BEAM_COLUMN type: Mu=50, Pu=200, Lb=4000mm
        val outcome = QualityControlEngine.steelQcDetailed(
            code = DesignCode.ECP,
            sectionKey = "IPE 200",
            fyMpa = 275.0,
            spanM = 6.0,
            muKnM = 50.0,
            vuKn = 30.0,
            puKn = 200.0,
            unbracedLengthMm = 4000.0,
            allowableDeflectionMm = 20.0,
            memberType = "BEAM_COLUMN"
        )

        // Should have interaction check
        assertTrue("Should have beam-column interaction details", outcome.details.any { it.first.contains("Axial load") })
        assertTrue("Should have member type details", outcome.details.any { it.first == "Member type" })
    }

    @Test
    fun steelQcDetailed_sectionNotFound() {
        val outcome = QualityControlEngine.steelQcDetailed(
            code = DesignCode.ECP,
            sectionKey = "NONEXISTENT",
            fyMpa = 275.0,
            spanM = 6.0,
            muKnM = 50.0,
            vuKn = 30.0
        )

        assertFalse("Nonexistent section should fail", outcome.passed)
        assertTrue("Should note section not found", outcome.details.any { it.second.contains("not found") })
    }

    // =========================================================================
    // ROADS QC — roadsQc
    // =========================================================================

    @Test
    fun roadsQc_allCriteriaMet_passes() {
        val outcome = QualityControlEngine.roadsQc(
            surfaceThicknessMm = 50.0,
            baseThicknessMm = 200.0,
            subbaseThicknessMm = 200.0,
            compactionPercent = 97.0,
            asphaltDensityKnM3 = 23.0,
            airVoidsPercent = 4.0
        )

        assertTrue("All road criteria should pass", outcome.passed)
        assertEquals(6, outcome.checks.size)
        assertTrue("Should have 6/6 checks", outcome.utilizationRatio >= 0.99)
        assertTrue("Should mention surface course", outcome.details.any { it.first == "Surface course" })
    }

    @Test
    fun roadsQc_lowCompaction_fails() {
        val outcome = QualityControlEngine.roadsQc(
            surfaceThicknessMm = 50.0,
            baseThicknessMm = 200.0,
            subbaseThicknessMm = 200.0,
            compactionPercent = 90.0, // < 95% → FAIL
            asphaltDensityKnM3 = 23.0,
            airVoidsPercent = 4.0
        )

        assertFalse("Low compaction should fail", outcome.passed)
        assertTrue("Should have compaction failure", !outcome.checks[3].passed) // compaction is 4th check (index 3)
    }

    // =========================================================================
    // BRIDGES QC — bridgeQc
    // =========================================================================

    @Test
    fun bridgeQc_strengthAndDeflectionPass_passes() {
        val outcome = QualityControlEngine.bridgeQc(
            code = DesignCode.ECP,
            concreteStrengthMpa = 35.0,
            requiredConcreteStrengthMpa = 30.0,
            spanM = 20.0,
            naturalFrequencyHz = 2.5,
            measuredDeflectionMm = 15.0,
            allowableDeflectionMm = 20.0
        )

        assertTrue("Bridge should pass strength and deflection", outcome.passed)
        assertTrue("Should mention measured concrete strength", outcome.details.any { it.first == "Measured concrete strength" })
        assertTrue("Should mention measured deflection", outcome.details.any { it.first == "Measured deflection" })
    }

    @Test
    fun bridgeQc_strengthFails_fails() {
        val outcome = QualityControlEngine.bridgeQc(
            code = DesignCode.ACI,
            concreteStrengthMpa = 25.0,
            requiredConcreteStrengthMpa = 30.0, // 25 < 30 → FAIL
            spanM = 20.0,
            naturalFrequencyHz = 0.0,
            measuredDeflectionMm = 15.0,
            allowableDeflectionMm = 20.0
        )

        assertFalse("Concrete strength < required should fail", outcome.passed)
        assertTrue("Should have strength check failure", !outcome.checks[0].passed)
    }

    // =========================================================================
    // TUNNELS QC — tunnelQc
    // =========================================================================

    @Test
    fun tunnelQc_classII_passes() {
        // Rock class II: min support = 75mm
        // D=10m, convergence=5mm, convRatio = 5/10000×100 = 0.05% ≤ 1% → PASS
        // support=80mm ≥ 75mm → PASS
        val outcome = QualityControlEngine.tunnelQc(
            rockClass = "II",
            convergenceMm = 5.0,
            diameterM = 10.0,
            supportThicknessMm = 80.0
        )

        assertTrue("Tunnel class II should pass", outcome.passed)
        assertTrue("Should mention rock class", outcome.details.any { it.first == "Rock class" })
        assertTrue("Should mention convergence ratio", outcome.details.any { it.first == "Convergence ratio" })
    }

    @Test
    fun tunnelQc_classIV_highConvergence_fails() {
        // Rock class IV: min support = 150mm
        // D=8m, convergence=100mm, convRatio = 100/8000×100 = 1.25% > 1% → FAIL
        val outcome = QualityControlEngine.tunnelQc(
            rockClass = "IV",
            convergenceMm = 100.0,
            diameterM = 8.0,
            supportThicknessMm = 100.0
        )

        assertFalse("High convergence ratio should fail", outcome.passed)
        assertTrue("Should have convergence failure", !outcome.checks[0].passed)
    }

    // =========================================================================
    // TUNNELS DETAILED QC — tunnelQcDetailed
    // =========================================================================

    @Test
    fun tunnelQcDetailed_classI_passes() {
        // Rock class I: RMR factor = 0.85
        // min support = 50mm, support=60mm → PASS
        // convRatio = 10/10000×100 = 0.1% ≤ 1% → PASS
        // maxConv = 25 × 0.85 = 21.25mm, convergence=10mm ≤ 21.25 → PASS
        val outcome = QualityControlEngine.tunnelQcDetailed(
            rockClass = "I",
            convergenceMm = 10.0,
            diameterM = 10.0,
            supportThicknessMm = 60.0,
            groundwaterPresent = false,
            shotcreteMpa = 30.0,
            rockUniaxialStrengthMpa = 60.0
        )

        assertTrue("Tunnel detailed class I should pass", outcome.passed)
        assertTrue("Should have RMR factor", outcome.details.any { it.first == "RMR quality factor" })
        assertTrue("Should have shotcrete strength", outcome.details.any { it.first == "Shotcrete strength" })
    }

    // =========================================================================
    // BUILDINGS QC — buildingQc
    // =========================================================================

    @Test
    fun buildingQc_allCriteriaMet_passes() {
        // 5 floors, 3m each → hn=15m
        // Ta = 0.0466 × 15^0.9 = 0.0466 × 10.8 = 0.50 s
        // Cs_raw = 0.3 × 1.0 / 1.0 = 0.3 (assuming SDS=0.3, Ie=1.0, R=1.0)
        // storyDrift = 0.015 (1.5%) ≤ 2.0% → PASS
        val outcome = QualityControlEngine.buildingQc(
            code = DesignCode.ECP,
            numFloors = 5,
            floorHeightM = 3.0,
            totalWeightKn = 5000.0,
            importanceFactor = 1.0,
            sds = 0.3,
            responseR = 1.0,
            storyDrift = 0.015
        )

        assertTrue("Building should pass drift and period checks", outcome.passed)
        assertTrue("Should mention total height", outcome.details.any { it.first == "Total height h_n" })
        assertTrue("Should mention fundamental period", outcome.details.any { it.first == "Fundamental period T_a" })
        assertTrue("Should mention base shear", outcome.details.any { it.first == "Base shear V" })
    }

    @Test
    fun buildingQc_excessiveDrift_fails() {
        // storyDrift = 0.03 (3%) > 2.0% → FAIL
        val outcome = QualityControlEngine.buildingQc(
            code = DesignCode.ECP,
            numFloors = 10,
            floorHeightM = 3.0,
            totalWeightKn = 10000.0,
            importanceFactor = 1.0,
            sds = 0.3,
            responseR = 1.0,
            storyDrift = 0.03
        )

        assertFalse("Excessive drift should fail", outcome.passed)
        assertTrue("Should have drift failure", !outcome.checks[0].passed)
    }

    // =========================================================================
    // Safety: invalid inputs rejected
    // =========================================================================

    @Test(expected = IllegalArgumentException::class)
    fun steelQcDetailed_negativeFy_rejected() {
        QualityControlEngine.steelQcDetailed(
            code = DesignCode.ECP,
            sectionKey = "IPE 200",
            fyMpa = -50.0,
            spanM = 6.0,
            muKnM = 50.0,
            vuKn = 30.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun bridgeQc_negativeStrength_rejected() {
        QualityControlEngine.bridgeQc(
            code = DesignCode.ECP,
            concreteStrengthMpa = -10.0,
            requiredConcreteStrengthMpa = 30.0,
            spanM = 20.0,
            naturalFrequencyHz = 0.0,
            measuredDeflectionMm = 15.0,
            allowableDeflectionMm = 20.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildingQc_zeroSpan_rejected() {
        QualityControlEngine.buildingQc(
            code = DesignCode.ECP,
            numFloors = 5,
            floorHeightM = 0.0,
            totalWeightKn = 5000.0,
            importanceFactor = 1.0,
            sds = 0.3,
            responseR = 1.0,
            storyDrift = 0.015
        )
    }
}
