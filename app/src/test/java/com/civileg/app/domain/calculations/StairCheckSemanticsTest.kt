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
import com.civileg.app.domain.calculations.base.FlexureCardState
import com.civileg.app.domain.calculations.base.flexureRatioOf
import com.civileg.app.domain.calculations.base.flexureCardState

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

class StairFlexureRatioTest {
    private fun row(name: String, v: Double, l: Double, na: Boolean = false) =
        com.civileg.app.domain.calculations.base.StairSafetyCheck(name = name, value = v, limit = l, unit = "", isSafe = v <= l && !na, notAssessed = na)

    @Test
    fun `ratio is the unclamped flexure row value over limit with its basis`() {
        val aci = flexureRatioOf(listOf(row("Flexure (ρ ≤ ρ_max_tc)", 0.0123, 0.0100)))!!
        assertEquals(1.23, aci.ratio, 1e-9); assertEquals("rho/rho_max", aci.basis)
        val ecp = flexureRatioOf(listOf(row("Flexure (K ≤ K_bal)", 0.05, 0.156)))!!
        assertEquals(0.05 / 0.156, ecp.ratio, 1e-9); assertEquals("K/K_bal", ecp.basis)
        assertEquals(0.02, flexureRatioOf(listOf(row("Flexure (K ≤ K_bal)", 0.002, 0.1)))!!.ratio, 1e-9)
    }

    @Test
    fun `no assessed flexure row or invalid limit gives no ratio, never a fallback`() {
        val f = ::flexureRatioOf
        assertNull(f(emptyList()))
        assertNull(f(listOf(row("2R + G (Comfort)", 600.0, 630.0))))
        assertNull(f(listOf(row("Flexure (ρ ≤ ρ_max_tc)", 0.01, 0.0))))
        assertNull(f(listOf(row("Flexure (ρ ≤ ρ_max_tc)", 0.01, 0.02, na = true))))
        assertNull(f(listOf(row("Flexure (ρ ≤ ρ_max_tc)", Double.NaN, 0.02))))
    }

    @Test
    fun `card never reads approved while acceptance is incomplete and is unavailable without a ratio`() {
        val S = FlexureCardState.values()
        val c = ::flexureCardState
        assertEquals(FlexureCardState.UNAVAILABLE, c(null, true))
        assertEquals(FlexureCardState.UNAVAILABLE, c(null, false))
        assertEquals(FlexureCardState.PRELIMINARY, c(0.3, false))
        assertEquals(FlexureCardState.PRELIMINARY, c(0.95, false))
        assertEquals(FlexureCardState.EXCEEDS_LIMIT, c(1.2, false))
        assertEquals(FlexureCardState.WITHIN_LIMIT, c(0.3, true))
        assertEquals(4, S.size)
    }

    @Test
    fun `live lanes expose a measured flexure ratio equal to value over limit`() {
        val inp = StaircaseInput(stairType = StairType.STRAIGHT, span = 3.0, totalRise = 1.8, stairWidth = 1.2, waistThickness = 200.0,
            fcu = 25.0, fy = 400.0, supportCondition = StairSupportCondition.SIMPLY_SUPPORTED)
        for ((n, lane) in mapOf("ECP" to ECPStaircase(), "ACI" to ACIStaircase(), "SBC" to SBCStaircase())) {
            val checks = lane.designStaircase(inp).safetyChecks
            val r = flexureRatioOf(checks)
            val row = checks.first { it.name.startsWith("Flexure") }
            if (r != null) assertEquals(n, row.value / row.limit, r.ratio, 1e-12)
        }
    }

    @Test
    fun `drawing strip names the flexure check or says n-a and never prints a generic UTIL`() {
        assertEquals("FLEXURE: n/a", com.civileg.app.ui.compose.components.drawings.stairFlexureStripText(null, ""))
        assertEquals("FLEXURE rho/rho_max: 29%", com.civileg.app.ui.compose.components.drawings.stairFlexureStripText(0.29, "rho/rho_max"))
        assertEquals("FLEXURE K/K_bal: 123%", com.civileg.app.ui.compose.components.drawings.stairFlexureStripText(1.23, "K/K_bal"))
    }

    @Test
    fun `ACI minimum main steel is not assessed for every fy, never a pass`() {
        for (fy in listOf(300.0, 420.0, 500.0)) {
            val r = ACIStaircase().designStaircase(StaircaseInput(stairType = StairType.STRAIGHT, span = 3.0, totalRise = 1.8, stairWidth = 1.2, waistThickness = 200.0, fcu = 25.0, fy = fy, supportCondition = StairSupportCondition.SIMPLY_SUPPORTED))
            val row = r.safetyChecks.single { it.name.startsWith("Minimum main steel NOT ASSESSED") }
            assertTrue("fy=$fy", row.notAssessed)
            assertFalse("fy=$fy", row.isSafe)
            assertTrue(row.description.contains("Table 7.6.1.1") && row.description.contains("not established"))
        }
    }
}
