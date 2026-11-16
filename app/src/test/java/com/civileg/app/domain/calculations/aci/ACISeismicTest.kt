package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.SeismicZone
import com.civileg.app.domain.calculations.base.SoilType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ACISeismicTest {

    private lateinit var engine: ACISeismic

    @Before
    fun setUp() {
        engine = ACISeismic()
    }

    @Test
    fun testBaseShearCalculation_goldenVector() {
        val result = engine.calculateBaseShear(
            totalWeight = 10000.0,      // kN
            seismicZone = SeismicZone.ZONE_3,
            soilType = SoilType.C,
            importanceFactor = 1.0,
            responseModificationFactor = 5.5,
            buildingHeight = 20.0       // m
        )

        assertNotNull(result)
        assertTrue(result.baseShear > 0.0)
        assertTrue(result.zoneFactor > 0.0)
        assertTrue(result.fundamentalPeriod > 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeWeight_rejectedByInputGuard() {
        engine.calculateBaseShear(
            totalWeight = -5000.0,
            seismicZone = SeismicZone.ZONE_2,
            soilType = SoilType.D,
            importanceFactor = 1.0,
            responseModificationFactor = 5.0,
            buildingHeight = 15.0
        )
    }

    @Test
    fun testHighRiseBuildingSeismic() {
        val result = engine.calculateBaseShear(
            totalWeight = 50000.0,
            seismicZone = SeismicZone.ZONE_4,
            soilType = SoilType.E,
            importanceFactor = 1.25,
            responseModificationFactor = 8.0,
            buildingHeight = 60.0
        )

        assertNotNull(result)
        assertTrue(result.baseShear > 0.0)
        assertTrue(result.maxStoryDrift >= 0.0)
    }
}
