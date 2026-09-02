package com.civileg.core.engineering

import com.civileg.core.calculations.entities.SupportCondition
import org.junit.Assert.*
import org.junit.Test

/**
 * Golden tests for [UnifiedCrackControl] — ECP 203 (300 mm fixed limit)
 * and ACI 318-19 (§24.3.2 spacing formula).
 *
 * Regression gate: any change to the crack-control logic must keep these
 * green before merge (spec §76-78).
 */
class UnifiedCrackControlGoldenTest {

    // ── ECP 203: fixed 300 mm limit (§4-2-3-5-2) ──

    @Test
    fun `ECP - spacing within limit is PASS`() {
        val engine = UnifiedCrackControl(Ecp203Params)
        val result = engine.check(tensionBarSpacingMm = 250.0, clearCoverMm = 30.0, fyMpa = 360.0)
        assertEquals(CheckStatus.PASS, result.overall)
        assertTrue(result.isCompliant)
        assertEquals(300.0, result.maxAllowedMm!!, 1e-6)
    }

    @Test
    fun `ECP - spacing at limit is PASS`() {
        val engine = UnifiedCrackControl(Ecp203Params)
        val result = engine.check(tensionBarSpacingMm = 300.0, clearCoverMm = 30.0, fyMpa = 360.0)
        assertEquals(CheckStatus.PASS, result.overall)
        assertTrue(result.isCompliant)
    }

    @Test
    fun `ECP - spacing above limit is FAIL`() {
        val engine = UnifiedCrackControl(Ecp203Params)
        val result = engine.check(tensionBarSpacingMm = 350.0, clearCoverMm = 30.0, fyMpa = 360.0)
        assertEquals(CheckStatus.FAIL, result.overall)
        assertFalse(result.isCompliant)
    }

    @Test
    fun `ECP trace contains code reference`() {
        val engine = UnifiedCrackControl(Ecp203Params)
        val result = engine.check(tensionBarSpacingMm = 200.0, clearCoverMm = 25.0, fyMpa = 400.0)
        val entry = result.trace.all.single { it.title.contains("Crack") }
        assertEquals("ECP 203-2020", entry.codeReference)
        assertEquals(CheckStatus.PASS, entry.status)
    }

    // ── ACI 318: §24.3.2 spacing formula ──
    // s_max = 380(280/fy) − 2.5·cc, clamped [150, 300]

    @Test
    fun `ACI - low cover yields clamped 300 mm`() {
        val engine = UnifiedCrackControl(Aci318Params)
        // 380*(280/420) - 2.5*15 = 253.33 - 37.5 = 215.83 → within [150,300]
        val result = engine.check(tensionBarSpacingMm = 215.0, clearCoverMm = 15.0, fyMpa = 420.0)
        assertEquals(CheckStatus.PASS, result.overall)
        assertTrue(result.isCompliant)
        // expected limit: 380*(280/420) - 2.5*15 = 215.833...
        assertEquals(215.833, result.maxAllowedMm!!, 0.5)
    }

    @Test
    fun `ACI - high cover clamps to 150 floor`() {
        val engine = UnifiedCrackControl(Aci318Params)
        // 380*(280/420) - 2.5*75 = 253.33 - 187.5 = 65.83 → clamped to 150
        val result = engine.check(tensionBarSpacingMm = 140.0, clearCoverMm = 75.0, fyMpa = 420.0)
        assertEquals(CheckStatus.PASS, result.overall)
        assertEquals(150.0, result.maxAllowedMm!!, 1e-6)
    }

    @Test
    fun `ACI - fy=360 gives higher spacing limit`() {
        val engine = UnifiedCrackControl(Aci318Params)
        // 380*(280/360) - 2.5*30 = 295.56 - 75 = 220.56
        val result = engine.check(tensionBarSpacingMm = 220.0, clearCoverMm = 30.0, fyMpa = 360.0)
        assertEquals(CheckStatus.PASS, result.overall)
        assertEquals(220.556, result.maxAllowedMm!!, 0.5)
    }

    @Test
    fun `ACI - spacing above computed limit is FAIL`() {
        val engine = UnifiedCrackControl(Aci318Params)
        // limit ≈ 215.8; spacing = 250 → FAIL
        val result = engine.check(tensionBarSpacingMm = 250.0, clearCoverMm = 15.0, fyMpa = 420.0)
        assertEquals(CheckStatus.FAIL, result.overall)
        assertFalse(result.isCompliant)
    }

    @Test
    fun `ACI trace has correct formula text`() {
        val engine = UnifiedCrackControl(Aci318Params)
        val result = engine.check(tensionBarSpacingMm = 200.0, clearCoverMm = 30.0, fyMpa = 420.0)
        val entry = result.trace.all.single { it.title.contains("Crack") }
        assertEquals("ACI 318-19", entry.codeReference)
        assertTrue(entry.formula.contains("s_max"))
    }
}
