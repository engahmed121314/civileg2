package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.ecp.ECPColumn
import com.civileg.app.domain.calculations.aci.ACIColumn
import com.civileg.app.domain.calculations.sbc.SBCColumn
import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Test

/**
 * Detailed tests for Column design strategy pattern.
 */
class ECPColumnDetailedTest {

    @Test
    fun `all column implementations are instances of ColumnDesign`() {
 val ecp = ECPColumn()
 val aci = ACIColumn()
 val sbc = SBCColumn()
 
 assertTrue("ECPColumn should implement ColumnDesign", ecp is ColumnDesign)
 assertTrue("ACIColumn should implement ColumnDesign", aci is ColumnDesign)
 assertTrue("SBCColumn should implement ColumnDesign", sbc is ColumnDesign)
    }

    @Test
    fun `factory returns distinct instances for each code`() {
 val factory = CalculationFactory
 val ecp = factory.getColumnDesign(DesignCode.ECP)
 val aci = factory.getColumnDesign(DesignCode.ACI)
 val sbc = factory.getColumnDesign(DesignCode.SBC)
 
 assertNotSame("ECP and ACI should be different instances", ecp, aci)
 assertNotSame("ECP and SBC should be different instances", ecp, sbc)
 assertNotSame("ACI and SBC should be different instances", aci, sbc)
    }

    @Test
    fun `advanced column factories return distinct types`() {
 val factory = CalculationFactory
 val ecpAdv = factory.getAdvancedColumnDesign(DesignCode.ECP)
 val aciAdv = factory.getAdvancedColumnDesign(DesignCode.ACI)
 val sbcAdv = factory.getAdvancedColumnDesign(DesignCode.SBC)
 
 assertNotSame(ecpAdv, aciAdv)
 assertNotSame(aciAdv, sbcAdv)
    }

    @Test
    fun `footing design factory returns correct types`() {
 val factory = CalculationFactory
 
 val ecpFooting = factory.getFootingDesign(DesignCode.ECP)
 val aciFooting = factory.getFootingDesign(DesignCode.ACI)
 val sbcFooting = factory.getFootingDesign(DesignCode.SBC)
 
 assertEquals("com.civileg.app.domain.calculations.ecp.ECPFooting", ecpFooting.javaClass.name)
 assertEquals("com.civileg.app.domain.calculations.aci.ACIFooting", aciFooting.javaClass.name)
 assertEquals("com.civileg.app.domain.calculations.sbc.SBCFooting", sbcFooting.javaClass.name)
    }

    @Test
    fun `tank design factory returns correct types`() {
 val factory = CalculationFactory
 
 val ecpTank = factory.getTankDesign(DesignCode.ECP)
 val aciTank = factory.getTankDesign(DesignCode.ACI)
 val sbcTank = factory.getTankDesign(DesignCode.SBC)
 
 assertEquals("com.civileg.app.domain.calculations.ecp.ECPTank", ecpTank.javaClass.name)
 assertEquals("com.civileg.app.domain.calculations.aci.ACITank", aciTank.javaClass.name)
 assertEquals("com.civileg.app.domain.calculations.sbc.SBCTank", sbcTank.javaClass.name)
    }

    @Test
    fun `retaining wall factory returns correct types`() {
 val factory = CalculationFactory
 
 val ecpWall = factory.getRetainingWallDesign(DesignCode.ECP)
 val aciWall = factory.getRetainingWallDesign(DesignCode.ACI)
 val sbcWall = factory.getRetainingWallDesign(DesignCode.SBC)
 
 assertEquals("com.civileg.app.domain.calculations.ecp.ECPRetainingWall", ecpWall.javaClass.name)
 assertEquals("com.civileg.app.domain.calculations.aci.ACIRetainingWall", aciWall.javaClass.name)
 assertEquals("com.civileg.app.domain.calculations.sbc.SBCRetainingWall", sbcWall.javaClass.name)
    }

    @Test
    fun `staircase factory returns correct types`() {
 val factory = CalculationFactory
 
 val ecpStair = factory.getStaircaseDesign(DesignCode.ECP)
 val aciStair = factory.getStaircaseDesign(DesignCode.ACI)
 val sbcStair = factory.getStaircaseDesign(DesignCode.SBC)
 
 assertEquals("com.civileg.app.domain.calculations.ecp.ECPStaircase", ecpStair.javaClass.name)
 assertEquals("com.civileg.app.domain.calculations.aci.ACIStaircase", aciStair.javaClass.name)
 assertEquals("com.civileg.app.domain.calculations.sbc.SBCStaircase", sbcStair.javaClass.name)
    }

    @Test
    fun `slab design factory returns correct types`() {
 val factory = CalculationFactory
 
 val ecpSlab = factory.getSlabDesign(DesignCode.ECP)
 val aciSlab = factory.getSlabDesign(DesignCode.ACI)
 val sbcSlab = factory.getSlabDesign(DesignCode.SBC)
 
 assertEquals("com.civileg.app.domain.calculations.ecp.ECPSlab", ecpSlab.javaClass.name)
 assertEquals("com.civileg.app.domain.calculations.aci.ACISlab", aciSlab.javaClass.name)
 assertEquals("com.civileg.app.domain.calculations.sbc.SBCSlab", sbcSlab.javaClass.name)
    }

    @Test
    fun `steel design engine factory returns correct types`() {
 val factory = CalculationFactory
 
 val ecpSteel = factory.getSteelDesignEngine(DesignCode.ECP)
 val aciSteel = factory.getSteelDesignEngine(DesignCode.ACI)
 val sbcSteel = factory.getSteelDesignEngine(DesignCode.SBC)
 
 assertEquals("com.civileg.app.domain.calculations.ecp.SteelDesignEngine", ecpSteel.javaClass.name)
 assertEquals("com.civileg.app.domain.calculations.aci.AISCSteelDesignEngine", aciSteel.javaClass.name)
 assertEquals("com.civileg.app.domain.calculations.sbc.SBCSteelDesignEngine", sbcSteel.javaClass.name)
    }
}
