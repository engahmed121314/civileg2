package com.civileg.app.domain.calculations.utils

import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Test

class BeamShearUnitContractTest {
    @Test fun `wrapper shear capacity uses square millimetres per millimetre and exact link text`() {
        val field=sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible=true }
        val unsafe=field.get(null) as sun.misc.Unsafe
        val settings=unsafe.allocateInstance(com.civileg.app.utils.SettingsManager::class.java) as com.civileg.app.utils.SettingsManager
        val engine=CalculatorEngine(settings)
        for(code in DesignCode.entries) {
            val r=engine.designBeam(250.0,600.0,5.0,25.0,400.0,15.0,10.0,16,code)
            val d=600.0-(if(code==DesignCode.SBC) 50.0 else 40.0)-r.stirrups.diameter-maxOf(16,r.reinforcementTop.diameter,r.reinforcementBottom.diameter)/2.0
            val factory=com.civileg.app.domain.calculations.CalculationFactory.getBeamDesign(code).calculateShearReinforcement(25.0,400.0,250.0,d,r.vu,0.0,com.civileg.app.domain.entities.LoadCombination.DEAD_LIVE)
            val avPerMm=r.stirrups.numLegs*kotlin.math.PI*r.stirrups.diameter*r.stirrups.diameter/4.0/r.stirrups.spacing
            assertEquals(factory.providedShearReinforcement/1000.0,avPerMm,1e-9)
            val vc=if(code==DesignCode.ECP) 0.12*kotlin.math.sqrt(25.0/1.5)*250.0*d/1000.0 else factory.concreteShearCapacity
            val vs=avPerMm*(if(code==DesignCode.ECP) 400.0/1.15 else 400.0)*d/1000.0
            val phi=if(code==DesignCode.ECP) 1.0 else 0.75
            val max=r.vu/factory.utilizationRatio
            assertEquals(minOf(phi*(vc+vs),max),r.shearCapacity,1e-8)
            assertTrue(r.shearCapacity<1000.0)
            assertTrue(r.stirrups.description.contains("${r.stirrups.diameter} mm"))
            val extreme=engine.designBeam(250.0,600.0,5.0,25.0,400.0,1500.0,1000.0,16,code)
            assertFalse(extreme.isSafe)
        }
    }
}
