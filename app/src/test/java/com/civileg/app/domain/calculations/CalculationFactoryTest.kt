package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.DesignCode
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
    fun `all specialized factories work for all codes`() {
        for (code in DesignCode.entries) {
            assertNotNull("HordiSlab null for $code", CalculationFactory.getHordiSlabDesign(code))
            assertNotNull("WaffleSlab null for $code", CalculationFactory.getWaffleSlabDesign(code))
            assertNotNull("FlatSlab null for $code", CalculationFactory.getFlatSlabDesign(code))
            assertNotNull("StrapFooting null for $code", CalculationFactory.getStrapFootingDesign(code))
            assertNotNull("ShearWall null for $code", CalculationFactory.getShearWallDesign(code))
            assertNotNull("PileFoundation null for $code", CalculationFactory.getPileFoundationDesign(code))
            assertNotNull("SeismicDesign null for $code", CalculationFactory.getSeismicDesign(code))
            assertNotNull("DoublyReinforcedBeam null for $code", CalculationFactory.getDoublyReinforcedBeamDesign(code))
        }
    }

    @Test
    fun `ECPCombinedFooting specialized engine is accessible`() {
        assertNotNull(CalculationFactory.getECPCombinedFootingSpecialized())
    }
}