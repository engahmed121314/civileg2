package com.civileg.app.domain.modelcheck

import com.civileg.app.domain.modelcheck.ModelCheckEngine.CheckResult
import com.civileg.app.domain.modelcheck.ModelCheckEngine.ModelCheckInput
import com.civileg.app.domain.modelcheck.ModelCheckEngine.StoryDriftInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Golden vectors and characterization tests for [ModelCheckEngine] (Phase 1.3).
 *
 * The golden vector uses ASCE 7-16 values traced in docs/CLAUSE_LOG.md
 * (C-01…C-06, accessed 2026-11-19). Thresholds marked U-01…U-04 in that log are
 * characterization only: they lock in current behaviour, not code compliance.
 *
 * Cross-code divergence is not applicable: this is a single code-agnostic
 * verification engine (SBC 301 / ACI 318-19), not an ECP/ACI/SBC design family.
 */
class ModelCheckEngineTest {

    private fun input(
        sumMassUx: Double = 92.5,
        sumMassUy: Double = 91.0,
        modalPeriodTx: Double = 1.5,
        modalPeriodTy: Double = 1.4,
        heightOfBuilding: Double = 80.0,
        sd1: Double = 0.004,
        systemType: String = "other",
        responseSpectrumBaseShearVt: Double = 900.0,
        equivalentLateralForceBaseShearV: Double = 1000.0,
        cd: Double = 5.0,
        ie: Double = 1.0,
        storyDrifts: List<StoryDriftInput> = listOf(
            StoryDriftInput("S1", hsxMm = 3000.0, elasticStoryDrift = 8.0)
        ),
        diaphragmMaxDeflection: Double = 10.0,
        storyAvgDeflection: Double = 40.0,
        buildingDimension: Double = 40.0,
        eccentricity: Double = 4.0,
        maxStoryDriftAtEnd: Double = 45.0,
        avgStoryDrift: Double = 40.0,
        px: Double = 10000.0,
        vx: Double = 2000.0,
        hsx: Double = 3000.0
    ) = ModelCheckInput(
        sumMassUx = sumMassUx,
        sumMassUy = sumMassUy,
        modalPeriodTx = modalPeriodTx,
        modalPeriodTy = modalPeriodTy,
        heightOfBuilding = heightOfBuilding,
        sd1 = sd1,
        systemType = systemType,
        responseSpectrumBaseShearVt = responseSpectrumBaseShearVt,
        equivalentLateralForceBaseShearV = equivalentLateralForceBaseShearV,
        cd = cd,
        ie = ie,
        storyDrifts = storyDrifts,
        diaphragmMaxDeflection = diaphragmMaxDeflection,
        storyAvgDeflection = storyAvgDeflection,
        buildingDimension = buildingDimension,
        eccentricity = eccentricity,
        maxStoryDriftAtEnd = maxStoryDriftAtEnd,
        avgStoryDrift = avgStoryDrift,
        px = px,
        vx = vx,
        hsx = hsx
    )

    private fun List<CheckResult>.check(name: String): CheckResult =
        first { it.checkName == name }

    @Test
    fun `golden vector metric period drift and p-delta`() {
        val results = ModelCheckEngine.runAllChecks(input())

        assertEquals(8, results.size)

        // C-04: 91.0% governs, both directions >= 90%.
        val mass = results.check("Mass Participation Ratio")
        assertEquals(CheckResult.Status.PASS, mass.status)
        assertEquals(91.0, mass.governingValue, 1e-9)
        assertEquals(90.0, mass.allowableOrLimit, 1e-9)

        // C-01/C-02/C-03: Ta = 0.0488 * 80^0.75 = 1.3054 s; Cu = 1.7 (Sd1 = 0.004).
        // Course vector in CourseTestVectorsTest: 1.305 s and Cu*Ta = 2.22 s.
        // governingValue is Tused (modal 1.5 s, between Ta and Cu*Ta); Ta is reported in the message.
        val period = results.check("Fundamental Period (Tx)")
        assertEquals(1.5, period.governingValue, 1e-9)
        assertEquals(2.2192, period.allowableOrLimit, 0.002)
        assertTrue(period.message.contains("Ta = 1.305"))
        assertEquals(CheckResult.Status.PASS, period.status)

        // C-05: delta_x = 5 * 8 / 1 = 40 mm; 40 / 3000 = 0.013333 (<= 0.015 input).
        val drift = results.check("Story Drift (S1)")
        assertEquals(0.0133333, drift.governingValue, 1e-6)
        assertEquals(0.015, drift.allowableOrLimit, 1e-9)
        assertEquals(CheckResult.Status.PASS, drift.status)

        // C-06: theta = 10000*8*1 / (2000*3000*5) = 0.002667 (< 0.10).
        val pDelta = results.check("P-Delta Stability Coefficient (\u03b8)")
        assertEquals(0.0026667, pDelta.governingValue, 1e-7)
        assertEquals(CheckResult.Status.PASS, pDelta.status)

        assertEquals(CheckResult.Status.PASS, results.check("Base-Shear Scaling").status)
    }

    @Test
    fun `metric period coefficients match ASCE table converted to metres`() {
        val ta = 0.0488 * 80.0.pow(0.75)
        assertEquals(1.3054, ta, 0.001)

        val steel = 0.0724 * 80.0.pow(0.80)
        val concrete = 0.0466 * 80.0.pow(0.90)
        assertTrue(steel > concrete && concrete > ta)
    }

    @Test
    fun `cu table boundaries select upper limit coefficient`() {
        val expectedCu = mapOf(
            0.04 to 1.7,
            0.15 to 1.6,
            0.2 to 1.5,
            0.3 to 1.4,
            0.4 to 1.4,
            1.0 to 1.4
        )
        val ta = 0.0488 * 80.0.pow(0.75)
        for ((sd1, cu) in expectedCu) {
            val period = ModelCheckEngine.runAllChecks(input(sd1 = sd1)).check("Fundamental Period (Tx)")
            assertEquals("Cu for Sd1=$sd1", cu * ta, period.allowableOrLimit, 1e-6)
        }
    }

    @Test
    fun `used period is clamped between ta and cu times ta`() {
        val ta = 0.0488 * 80.0.pow(0.75)
        val cuTa = 1.7 * ta

        val below = ModelCheckEngine.runAllChecks(input(modalPeriodTx = 1.0)).check("Fundamental Period (Tx)")
        assertEquals(ta, below.governingValue, 1e-6)

        val middle = ModelCheckEngine.runAllChecks(input(modalPeriodTx = 1.5)).check("Fundamental Period (Tx)")
        assertEquals(1.5, middle.governingValue, 1e-9)

        val above = ModelCheckEngine.runAllChecks(input(modalPeriodTx = 3.0)).check("Fundamental Period (Tx)")
        assertEquals(cuTa, above.governingValue, 1e-6)
    }

    @Test
    fun `mass participation fails below ninety percent in either direction`() {
        val pass = ModelCheckEngine.runAllChecks(input(sumMassUx = 90.0, sumMassUy = 90.0))
        assertEquals(CheckResult.Status.PASS, pass.check("Mass Participation Ratio").status)

        val fail = ModelCheckEngine.runAllChecks(input(sumMassUx = 90.0, sumMassUy = 89.9))
        assertEquals(CheckResult.Status.FAIL, fail.check("Mass Participation Ratio").status)
    }

    @Test
    fun `base shear scaling warns below eighty five percent of elf`() {
        // U-04: characterization of the current 0.85 threshold, not a verified clause.
        val boundary = ModelCheckEngine.runAllChecks(input(responseSpectrumBaseShearVt = 850.0))
        assertEquals(CheckResult.Status.PASS, boundary.check("Base-Shear Scaling").status)

        val low = ModelCheckEngine.runAllChecks(input(responseSpectrumBaseShearVt = 800.0))
        val scaling = low.check("Base-Shear Scaling")
        assertEquals(CheckResult.Status.WARNING, scaling.status)
        assertTrue(scaling.message.contains("1.250"))
    }

    @Test
    fun `story drift status changes at the caller supplied limit`() {
        // U-01: 0.015 is supplied by the caller; this checks the arithmetic only.
        val pass = ModelCheckEngine.runAllChecks(
            input(storyDrifts = listOf(StoryDriftInput("S1", 3000.0, 9.0, 0.015)))
        )
        assertEquals(0.015, pass.check("Story Drift (S1)").governingValue, 1e-9)

        val fail = ModelCheckEngine.runAllChecks(
            input(storyDrifts = listOf(StoryDriftInput("S1", 3000.0, 9.1, 0.015)))
        )
        assertEquals(CheckResult.Status.FAIL, fail.check("Story Drift (S1)").status)
    }

    @Test
    fun `characterization thresholds for diaphragm torsion and eccentricity`() {
        // U-02/U-03: characterization only (unverified clause values).
        val rigid = ModelCheckEngine.runAllChecks(input(diaphragmMaxDeflection = 20.0))
        assertEquals(CheckResult.Status.PASS, rigid.check("Diaphragm Flexibility").status)
        assertTrue(rigid.check("Diaphragm Flexibility").message.contains("Rigid"))

        val semiRigid = ModelCheckEngine.runAllChecks(input(diaphragmMaxDeflection = 30.0))
        assertEquals(CheckResult.Status.WARNING, semiRigid.check("Diaphragm Flexibility").status)

        val flexible = ModelCheckEngine.runAllChecks(input(diaphragmMaxDeflection = 100.0))
        assertEquals(CheckResult.Status.FAIL, flexible.check("Diaphragm Flexibility").status)

        val torsionWarning = ModelCheckEngine.runAllChecks(input(maxStoryDriftAtEnd = 52.0))
        assertEquals(CheckResult.Status.WARNING, torsionWarning.check("Torsional Irregularity Ratio").status)

        val torsionFail = ModelCheckEngine.runAllChecks(input(maxStoryDriftAtEnd = 60.0))
        assertEquals(CheckResult.Status.FAIL, torsionFail.check("Torsional Irregularity Ratio").status)

        val eccentricityFail = ModelCheckEngine.runAllChecks(input(eccentricity = 8.0))
        assertEquals(CheckResult.Status.WARNING, eccentricityFail.check("Center of Mass vs Rigidity Eccentricity").status)
    }

    @Test
    fun `p-delta status spans pass warning and fail`() {
        // C-06 threshold verified; theta expression characterized (see CLAUSE_LOG).
        fun thetaFor(drift: Double) = ModelCheckEngine.runAllChecks(
            input(
                storyDrifts = listOf(StoryDriftInput("S1", 1000.0, drift, 1.0)),
                px = 1000.0,
                vx = 1000.0,
                hsx = 1000.0,
                cd = 1.0
            )
        ).check("P-Delta Stability Coefficient (\u03b8)")

        assertEquals(0.1, thetaFor(100.0).governingValue, 1e-9)
        assertEquals(CheckResult.Status.PASS, thetaFor(100.0).status)
        assertEquals(CheckResult.Status.WARNING, thetaFor(150.0).status)
        assertEquals(CheckResult.Status.FAIL, thetaFor(300.0).status)
    }

    @Test
    fun `zero and empty inputs are handled without division by zero`() {
        val results = ModelCheckEngine.runAllChecks(
            input(
                storyDrifts = emptyList(),
                avgStoryDrift = 0.0,
                storyAvgDeflection = 0.0,
                vx = 0.0
            )
        )

        assertEquals(7, results.size)
        assertTrue(results.none { it.checkName.startsWith("Story Drift") })
        assertEquals(1.0, results.check("Torsional Irregularity Ratio").governingValue, 1e-9)
        assertEquals(0.0, results.check("Diaphragm Flexibility").governingValue, 1e-9)
        assertEquals(0.0, results.check("P-Delta Stability Coefficient (\u03b8)").governingValue, 1e-9)
    }
}
