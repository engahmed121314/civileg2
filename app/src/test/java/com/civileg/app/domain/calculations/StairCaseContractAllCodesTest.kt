package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.aci.ACIStaircase
import com.civileg.app.domain.calculations.base.StairAnalysisStatus
import com.civileg.app.domain.calculations.base.StairSupportCondition
import com.civileg.app.domain.calculations.base.StairType
import com.civileg.app.domain.calculations.base.StaircaseDesign
import com.civileg.app.domain.calculations.base.StaircaseInput
import com.civileg.app.domain.calculations.ecp.ECPStaircase
import com.civileg.app.domain.calculations.sbc.SBCStaircase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stair case/support contract must be honoured identically by the ECP, ACI and SBC lanes. */
class StairCaseContractAllCodesTest {

    private val lanes: Map<String, StaircaseDesign> = mapOf(
        "ECP" to ECPStaircase(), "ACI" to ACIStaircase(), "SBC" to SBCStaircase()
    )

    private fun input(
        type: StairType = StairType.STRAIGHT,
        support: StairSupportCondition = StairSupportCondition.UNSPECIFIED,
        landing: Boolean = false
    ) = StaircaseInput(
        stairType = type, span = 3.0, totalRise = 1.8, stairWidth = 1.2, waistThickness = 200.0,
        fcu = 25.0, fy = 400.0, supportCondition = support,
        landingLengthMm = if (landing) 1200.0 else null,
        landingWidthMm = if (landing) 1200.0 else null,
        landingThicknessMm = if (landing) 150.0 else null
    )

    @Test
    fun `straight flight status follows the declared support in every lane`() {
        for ((name, lane) in lanes) {
            assertEquals(name, StairAnalysisStatus.CASE_MODEL_IMPLEMENTED,
                lane.designStaircase(input(support = StairSupportCondition.SIMPLY_SUPPORTED)).analysisStatus)
            assertEquals(name, StairAnalysisStatus.CONSERVATIVE_FALLBACK_RESTRAINT_UNVERIFIED,
                lane.designStaircase(input()).analysisStatus)
        }
    }

    @Test
    fun `no lane credits an ungrounded landing restraint`() {
        for ((name, lane) in lanes) {
            val straight = lane.designStaircase(input())
            for (r in listOf(
                lane.designStaircase(input(StairType.DOG_LEG)),
                lane.designStaircase(input(StairType.DOG_LEG, StairSupportCondition.CONTINUOUS_OVER_LANDING)),
                lane.designStaircase(input(StairType.DOG_LEG, StairSupportCondition.CONTINUOUS_OVER_LANDING, landing = true))
            )) {
                assertEquals(name, StairAnalysisStatus.CONSERVATIVE_FALLBACK_RESTRAINT_UNVERIFIED, r.analysisStatus)
                assertEquals(name, straight.maxMoment, r.maxMoment, 1e-9)
                assertEquals(name, "", r.flightTopRebar)
                assertEquals(name, "", r.landingRebar)
            }
        }
    }

    @Test
    fun `declared continuity with landing dimensions is reported as not credited`() {
        for ((name, lane) in lanes) {
            val r = lane.designStaircase(input(StairType.DOG_LEG, StairSupportCondition.CONTINUOUS_OVER_LANDING, landing = true))
            assertTrue(name, r.codeNotes.any { it.contains("NOT credited") })
        }
    }

    @Test
    fun `open well and spiral are unsupported in every lane and never a passing case model`() {
        for ((name, lane) in lanes) {
            for (t in listOf(StairType.OPEN_WELL, StairType.SPIRAL)) {
                val r = lane.designStaircase(input(t, StairSupportCondition.SIMPLY_SUPPORTED))
                assertEquals("$name $t", StairAnalysisStatus.UNSUPPORTED_CASE, r.analysisStatus)
                assertTrue("$name $t must fail the case check", r.safetyChecks.any { it.name == "Case model support" && !it.isSafe })
                assertTrue("$name $t must not be safe", !r.isSafe)
            }
        }
    }
}

class StairDeflectionTable731Test {
    private fun check(lane: StaircaseDesign, t: Double, fy: Double = 420.0) =
        lane.designStaircase(StaircaseInput(span = 3.0, totalRise = 1.8, stairWidth = 1.2, waistThickness = t, fcu = 25.0, fy = fy))
            .safetyChecks.first { it.name.startsWith("Deflection") }

    @Test
    fun `ACI and SBC use l over 20 divided by the fy factor and flag not computed`() {
        for (lane in listOf<StaircaseDesign>(ACIStaircase(), SBCStaircase())) {
            assertEquals(20.0, check(lane, 200.0).limit, 1e-9)
            assertEquals(20.0 / (0.4 + 360.0 / 700.0), check(lane, 200.0, 360.0).limit, 1e-9)
            check(lane, 200.0).let { assertTrue(it.value <= it.limit && it.notAssessed && !it.isSafe) }
            val thin = check(lane, 120.0)
            assertTrue(!thin.isSafe && thin.name.contains("NOT ASSESSED"))
        }
    }
}
