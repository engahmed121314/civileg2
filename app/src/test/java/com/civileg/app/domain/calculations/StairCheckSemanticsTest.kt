package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.aci.ACIStaircase
import com.civileg.app.domain.calculations.base.StairCheckKind
import com.civileg.app.domain.calculations.base.StairSupportCondition
import com.civileg.app.domain.calculations.base.StairType
import com.civileg.app.domain.calculations.base.StaircaseDesign
import com.civileg.app.domain.calculations.base.StaircaseInput
import com.civileg.app.domain.calculations.ecp.ECPStaircase
import com.civileg.app.domain.calculations.sbc.SBCStaircase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import com.civileg.app.domain.calculations.base.StairSafetyCheck
import com.civileg.app.domain.calculations.base.mainSteelStepPass
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An unverified table applicability or unverified restraint must never read as a pass, and a row that is not a code
 * strength check (practical minimum, model flag, IBC geometry) must never count as code-passed.
 */
class StairCheckSemanticsTest {

    private val lanes: Map<String, StaircaseDesign> = mapOf(
        "ECP" to ECPStaircase(), "ACI" to ACIStaircase(), "SBC" to SBCStaircase()
    )

    private fun input(support: StairSupportCondition) = StaircaseInput(
        stairType = StairType.STRAIGHT, span = 3.0, totalRise = 1.8, stairWidth = 1.2, waistThickness = 200.0,
        fcu = 25.0, fy = 400.0, supportCondition = support
    )

    @Test
    fun `deflection is never a pass because partition applicability is not verified`() {
        for ((name, lane) in lanes) for (support in listOf(StairSupportCondition.UNSPECIFIED, StairSupportCondition.SIMPLY_SUPPORTED)) {
            val row = lane.designStaircase(input(support)).safetyChecks.single { it.name.startsWith("Deflection") }
            assertTrue("$name $support", row.notAssessed)
            assertFalse("$name $support", row.isSafe)
            assertTrue("$name $support", row.name.contains("NOT ASSESSED"))
        }
    }

    @Test
    fun `unspecified restraint is not assessed, declared simply supported is`() {
        for ((name, lane) in lanes) {
            val unspec = lane.designStaircase(input(StairSupportCondition.UNSPECIFIED)).safetyChecks.single { it.name == "Case model support" }
            assertTrue(name, unspec.notAssessed)
            assertFalse(name, unspec.isSafe)
            val declared = lane.designStaircase(input(StairSupportCondition.SIMPLY_SUPPORTED)).safetyChecks.single { it.name == "Case model support" }
            assertFalse(name, declared.notAssessed)
            assertTrue(name, declared.isSafe)
        }
    }

    @Test
    fun `non code rows carry their kind and never count as code checks`() {
        for ((name, lane) in lanes) {
            val checks = lane.designStaircase(input(StairSupportCondition.SIMPLY_SUPPORTED)).safetyChecks
            for (c in checks.filter { it.name.startsWith("2R + G") || it.name.startsWith("Riser") || it.name.startsWith("Going") })
                assertEquals("$name ${c.name}", StairCheckKind.GEOMETRY, c.kind)
            for (c in checks.filter { it.name.startsWith("Waist Thickness") || it.name.startsWith("Case model support") || it.name.startsWith("Waist >=") })
                assertEquals("$name ${c.name}", StairCheckKind.ASSUMPTION, c.kind)
            assertTrue("$name ${checks.map { it.name }}", checks.any { it.kind == StairCheckKind.CODE && it.name.startsWith("Flexure") })
            assertTrue(name, checks.filter { it.kind != StairCheckKind.CODE }.all { it.displayName != it.name })
        }
    }

    @Test
    fun `result is never all clear while deflection applicability is unverified`() {
        for ((name, lane) in lanes) {
            val r = lane.designStaircase(input(StairSupportCondition.SIMPLY_SUPPORTED))
            assertFalse(name, r.safetyChecks.all { it.isSafe })
            assertTrue(name, r.safetyChecks.any { it.notAssessed })
        }
    }

    @Test
    fun `main steel step follows flexure not the overall not-assessed result`() {
        val flexOk = StairSafetyCheck(name = "Flexure (K <= K_bal)", value = 0.1, limit = 0.2, unit = "", isSafe = true, description = "")
        val defl = StairSafetyCheck(name = "Deflection NOT ASSESSED: x", value = 1.0, limit = 2.0, unit = "", isSafe = false, notAssessed = true, description = "")
        assertEquals(true, mainSteelStepPass(listOf(flexOk, defl)))
        val flexBad = flexOk.copy(isSafe = false)
        assertEquals(false, mainSteelStepPass(listOf(flexBad, defl)))
        assertNull(mainSteelStepPass(listOf(defl)))
    }
}
