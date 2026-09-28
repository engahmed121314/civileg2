package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.ecp.ECPDoublyReinforcedBeam
import com.civileg.app.domain.calculations.aci.ACIDoublyReinforcedBeam
import com.civileg.app.domain.calculations.sbc.SBCDoublyReinforcedBeam
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Doubly Reinforced Beam Design (ECP 203-2020, ACI 318-19, SBC 304-2018).
 * Tests verify compression steel calculation when moment exceeds singly reinforced capacity.
 */
class DoublyReinforcedBeamTest {

    private lateinit var ecpBeam: ECPDoublyReinforcedBeam
    private lateinit var aciBeam: ACIDoublyReinforcedBeam
    private lateinit var sbcBeam: SBCDoublyReinforcedBeam

    @Before
    fun setup() {
        ecpBeam = ECPDoublyReinforcedBeam()
        aciBeam = ACIDoublyReinforcedBeam()
        sbcBeam = SBCDoublyReinforcedBeam()
    }

    @Test
    fun `All DoublyReinforcedBeam instances created successfully`() {
        assertNotNull(ecpBeam)
        assertNotNull(aciBeam)
        assertNotNull(sbcBeam)
    }

    @Test
    fun `ECP doubly reinforced - singly reinforced sufficient case`() {
        // Small moment - should NOT need compression steel
        val result = ecpBeam.design(
            mu = 50.0,   // kN.m
            b = 250.0,   // mm
            h = 600.0,   // mm
            fcu = 30.0,  // MPa
            fy = 400.0   // MPa
        )

        assertNotNull(result)
        assertFalse(result.needsCompressionSteel)
        assertEquals(0.0, result.asCompression, 0.001)
        assertTrue(result.asRequired > 0.0)
        assertTrue(result.isSafe)
    }

    @Test
    fun `ECP doubly reinforced - needs compression steel case`() {
        // Large moment - SHOULD need compression steel
        val result = ecpBeam.design(
            mu = 500.0,  // kN.m - large moment
            b = 250.0,
            h = 600.0,
            fcu = 30.0,
            fy = 400.0
        )

        assertNotNull(result)
        assertTrue(result.needsCompressionSteel)
        assertTrue(result.asCompression > 0.0)
        assertTrue(result.asTensionFromCompression > 0.0)
        assertTrue(result.asRequired > result.asTensionFromConcrete)
    }

    @Test
    fun `ACI doubly reinforced - singly reinforced sufficient case`() {
        val result = aciBeam.design(
            mu = 50.0,
            b = 250.0,
            h = 600.0,
            fcPrime = 24.0,  // f'c = 0.8 * fcu = 24.0 MPa
            fy = 420.0   // Grade 60 ksi = 420 MPa
        )

        assertNotNull(result)
        assertFalse(result.needsCompressionSteel)
        assertEquals(0.0, result.asCompression, 0.001)
        assertTrue(result.asRequired > 0.0)
    }

    @Test
    fun `ACI doubly reinforced - needs compression steel case`() {
        val result = aciBeam.design(
            mu = 500.0,
            b = 250.0,
            h = 600.0,
            fcPrime = 24.0,
            fy = 420.0
        )

        assertNotNull(result)
        assertTrue(result.needsCompressionSteel)
        assertTrue(result.asCompression > 0.0)
        assertTrue(result.asTensionFromCompression > 0.0)
    }

    @Test
    fun `SBC doubly reinforced - singly reinforced sufficient case`() {
        val result = sbcBeam.design(
            mu = 50.0,
            b = 250.0,
            h = 600.0,
            fcu = 30.0,
            fy = 400.0
        )

        assertNotNull(result)
        assertFalse(result.needsCompressionSteel)
        assertEquals(0.0, result.asCompression, 0.001)
    }

    @Test
    fun `SBC doubly reinforced - needs compression steel case`() {
        val result = sbcBeam.design(
            mu = 500.0,
            b = 250.0,
            h = 600.0,
            fcu = 30.0,
            fy = 400.0
        )

        assertNotNull(result)
        assertTrue(result.needsCompressionSteel)
        assertTrue(result.asCompression > 0.0)
    }

    @Test
    fun `K_bal calculation returns valid value for all codes`() {
        val ecpKbal = ecpBeam.calculateKBal(30.0, 400.0)
        val aciKbal = aciBeam.calculateKBal(24.0, 420.0)
        val sbcKbal = sbcBeam.calculateKBal(30.0, 400.0)

        assertTrue(ecpKbal > 0.0 && ecpKbal < 1.0)
        assertTrue(aciKbal > 0.0 && aciKbal < 1.0)
        assertTrue(sbcKbal > 0.0 && sbcKbal < 1.0)
    }

    @Test
    fun `R_bal calculation returns valid value`() {
        val ecpKbal = ecpBeam.calculateKBal(30.0, 400.0)
        val ecpRbal = ecpBeam.calculateRBal(ecpKbal)

        assertTrue(ecpRbal > 0.0 && ecpRbal < ecpKbal)
    }

    @Test
    fun `Bar selection produces valid bar counts`() {
        val result = ecpBeam.design(
            mu = 200.0,
            b = 250.0,
            h = 600.0,
            fcu = 30.0,
            fy = 400.0
        )

        assertTrue(result.tensionBarCount >= 2 && result.tensionBarCount <= 8)
        assertTrue(result.tensionBarDia in listOf(16, 20, 22, 25, 28, 32))

        if (result.needsCompressionSteel) {
            assertTrue(result.compressionBarCount >= 2 && result.compressionBarCount <= 4)
            assertTrue(result.compressionBarDia in listOf(12, 14, 16, 20))
        }
    }

    @Test
    fun `Minimum steel check applied`() {
        // Very small moment should trigger minimum steel
        val result = ecpBeam.design(
            mu = 5.0,   // Very small moment
            b = 250.0,
            h = 600.0,
            fcu = 30.0,
            fy = 400.0
        )

        assertTrue(result.asRequired >= result.asMin - 1.0) // Allow small tolerance
    }

    @Test
    fun `CalculationFactory returns DoublyReinforcedBeam for all codes`() {
        val factory = CalculationFactory

        val ecp = factory.getDoublyReinforcedBeamDesign(DesignCode.ECP)
        val aci = factory.getDoublyReinforcedBeamDesign(DesignCode.ACI)
        val sbc = factory.getDoublyReinforcedBeamDesign(DesignCode.SBC)

        assertEquals("com.civileg.app.domain.calculations.ecp.ECPDoublyReinforcedBeam", ecp.javaClass.name)
        assertEquals("com.civileg.app.domain.calculations.aci.ACIDoublyReinforcedBeam", aci.javaClass.name)
        assertEquals("com.civileg.app.domain.calculations.sbc.SBCDoublyReinforcedBeam", sbc.javaClass.name)
    }

    @Test
    fun `Codes produce different results for same input`() {
        // Same geometric and material properties
        val mu = 500.0
        val b = 250.0
        val h = 600.0
        val fcu = 30.0
        val fy = 400.0

        val ecpResult = ecpBeam.design(mu, b, h, fcu, fy)
        val aciResult = aciBeam.design(mu, b, h, fcu * 0.8, fy)
        val sbcResult = sbcBeam.design(mu, b, h, fcu, fy)

        // All should need compression steel for this large moment
        assertTrue(ecpResult.needsCompressionSteel)
        assertTrue(aciResult.needsCompressionSteel)
        assertTrue(sbcResult.needsCompressionSteel)

        // But steel areas should differ due to different formulas/safety factors
        val allDifferent = (ecpResult.asRequired != aciResult.asRequired) ||
                          (aciResult.asRequired != sbcResult.asRequired) ||
                          (ecpResult.asRequired != sbcResult.asRequired)
        assertTrue(allDifferent)
    }

    @Test
    fun `Utilization ratio within valid range`() {
        val result = ecpBeam.design(
            mu = 200.0,
            b = 250.0,
            h = 600.0,
            fcu = 30.0,
            fy = 400.0
        )

        assertTrue(result.utilizationRatio >= 0.0)
        assertTrue(result.utilizationRatio <= 2.0) // Allow up to 2.0 for over-reinforced
    }
}