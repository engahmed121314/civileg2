package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.base.*
import com.civileg.core.calculations.entities.DesignCode
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for CalculationFactory — verifies all 21 strategy combinations are correctly instantiated.
 */
class CalculationFactoryTest {

    @Test
    fun `factory provides all 7 element types for all 3 codes`() {
        val codes = DesignCode.entries

        for (code in codes) {
            assertNotNull("ColumnDesign null for $code", CalculationFactory.getColumnDesign(code))
            assertNotNull("BeamDesign null for $code", CalculationFactory.getBeamDesign(code))
            assertNotNull("SlabDesign null for $code", CalculationFactory.getSlabDesign(code))
            assertNotNull("FootingDesign null for $code", CalculationFactory.getFootingDesign(code))
            assertNotNull("TankDesign null for $code", CalculationFactory.getTankDesign(code))
            assertNotNull("RetainingWallDesign null for $code", CalculationFactory.getRetainingWallDesign(code))
            assertNotNull("StaircaseDesign null for $code", CalculationFactory.getStaircaseDesign(code))
        }
    }

    @Test
    fun `each factory method returns correct interface type`() {
        val code = DesignCode.ECP

        assertTrue(CalculationFactory.getColumnDesign(code) is ColumnDesign)
        assertTrue(CalculationFactory.getBeamDesign(code) is BeamDesign)
        assertTrue(CalculationFactory.getSlabDesign(code) is SlabDesign)
        assertTrue(CalculationFactory.getFootingDesign(code) is FootingDesign)
        assertTrue(CalculationFactory.getTankDesign(code) is TankDesign)
        assertTrue(CalculationFactory.getRetainingWallDesign(code) is RetainingWallDesign)
        assertTrue(CalculationFactory.getStaircaseDesign(code) is StaircaseDesign)
    }

    @Test
    fun `advanced design factories return non-null instances`() {
        for (code in DesignCode.entries) {
            assertNotNull(CalculationFactory.getAdvancedColumnDesign(code))
            assertNotNull(CalculationFactory.getAdvancedBeamDesign(code))
            assertNotNull(CalculationFactory.getAdvancedSlabDesign(code))
            assertNotNull(CalculationFactory.getSteelDesignEngine(code))
        }
    }

    @Test
    fun `specialized factories return ECP-only implementations`() {
        assertNotNull(CalculationFactory.getHordiSlabDesign(DesignCode.ECP))
        assertNotNull(CalculationFactory.getWaffleSlabDesign(DesignCode.ECP))
        assertNotNull(CalculationFactory.getDoublyReinforcedBeamDesign(DesignCode.ECP))
        assertNotNull(CalculationFactory.getCombinedFootingDesign(DesignCode.ECP))
    }

    @Test
    fun `flat slab, shear wall and pile foundation factories return distinct impls per code`() {
        // Phase 2 wiring: SBCFlatSlab / SBCShearWall / SBCPileFoundation / ACIPileFoundation
        // must be reachable — no silent ECP fallback (ADR-002).
        for (code in DesignCode.entries) {
            val flatSlab = CalculationFactory.getFlatSlabDesign(code)
            val shearWall = CalculationFactory.getShearWallDesign(code)
            val pile = CalculationFactory.getPileFoundationDesign(code)

            assertNotNull("FlatSlabDesign null for $code", flatSlab)
            assertNotNull("ShearWallDesign null for $code", shearWall)
            assertNotNull("PileFoundationDesign null for $code", pile)

            assertTrue(flatSlab is FlatSlabDesign)
            assertTrue(shearWall is ShearWallDesign)
            assertTrue(pile is PileFoundationDesign)
        }

        // Distinct concrete types per code — proves no shared fallback instance class.
        assertEquals(
            3,
            DesignCode.entries.map { CalculationFactory.getFlatSlabDesign(it)::class }.toSet().size
        )
        assertEquals(
            3,
            DesignCode.entries.map { CalculationFactory.getShearWallDesign(it)::class }.toSet().size
        )
        assertEquals(
            3,
            DesignCode.entries.map { CalculationFactory.getPileFoundationDesign(it)::class }.toSet().size
        )
    }

    @Test
    fun `parseDesignCode is case-insensitive and rejects garbage`() {
        assertEquals(DesignCode.ECP, CalculationFactory.parseDesignCode("ecp"))
        assertEquals(DesignCode.ACI, CalculationFactory.parseDesignCode(" ACI "))
        assertEquals(DesignCode.SBC, CalculationFactory.parseDesignCode("SBC"))
        assertNull(CalculationFactory.parseDesignCode(""))
        assertNull(CalculationFactory.parseDesignCode(null))
        assertNull(CalculationFactory.parseDesignCode("BS8110"))
    }
}