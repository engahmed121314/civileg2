package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.base.BeamDesign
import com.civileg.app.domain.calculations.ecp.ECPBeam
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ECP Beam Design (Egyptian Code ECP 203).
 * Tests verify fundamental structural design behavior.
 */
class ECPBeamTest {

    private lateinit var beamDesign: BeamDesign

    @Before
    fun setup() {
        beamDesign = ECPBeam()
    }

    @Test
    fun `ECPBeam instance is created successfully`() {
        assertNotNull(beamDesign)
    }

    @Test
    fun `ECPBeam is instance of BeamDesign`() {
        assertTrue(beamDesign is BeamDesign)
    }

    @Test
    fun `CalculationFactory returns ECPBeam for ECP code`() {
        val factory = CalculationFactory
        val result = factory.getBeamDesign(DesignCode.ECP)
        assertTrue(result is ECPBeam)
    }

    @Test
    fun `CalculationFactory returns correct implementation for each code`() {
        val factory = CalculationFactory

        val ecpBeam = factory.getBeamDesign(DesignCode.ECP)
        val aciBeam = factory.getBeamDesign(DesignCode.ACI)
        val sbcBeam = factory.getBeamDesign(DesignCode.SBC)

        assertEquals("com.civileg.app.domain.calculations.ecp.ECPBeam", ecpBeam.javaClass.name)
        assertEquals("com.civileg.app.domain.calculations.aci.ACIBeam", aciBeam.javaClass.name)
        assertEquals("com.civileg.app.domain.calculations.sbc.SBCBeam", sbcBeam.javaClass.name)
    }

    @Test
    fun `CalculationFactory returns all 7 element types for ECP`() {
        val factory = CalculationFactory
        val code = DesignCode.ECP

        assertNotNull(factory.getColumnDesign(code))
        assertNotNull(factory.getBeamDesign(code))
        assertNotNull(factory.getSlabDesign(code))
        assertNotNull(factory.getFootingDesign(code))
        assertNotNull(factory.getTankDesign(code))
        assertNotNull(factory.getRetainingWallDesign(code))
        assertNotNull(factory.getStaircaseDesign(code))
    }
}
