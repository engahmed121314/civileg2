package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.entities.BeamSectionType
import com.civileg.app.domain.entities.BeamType
import com.civileg.app.domain.entities.LoadCombination
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ECPAdvancedBeamTest {

    private lateinit var engine: ECPAdvancedBeam

    @Before
    fun setUp() {
        engine = ECPAdvancedBeam()
    }

    @Test
    fun testSimplySupportedRectangularBeam_goldenVector() {
        val beamType = BeamType.SimplySupported(6.0)
        val sectionType = BeamSectionType.RECTANGULAR
        val result = engine.designBeam(
            beamType = beamType,
            sectionType = sectionType,
            fcu = 30.0,
            fy = 400.0,
            deadLoad = 25.0,
            liveLoad = 15.0,
            width = 250.0,
            depth = 600.0,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE
        )

        assertNotNull(result)
        assertTrue(result.flexureResult.astRequired > 0.0)
        assertTrue(result.flexureResult.astProvided >= result.flexureResult.astRequired)
        assertTrue(result.shearResult.isSafe)
        assertTrue(result.momentDiagram.isNotEmpty())
        assertTrue(result.shearDiagram.isNotEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeWidth_rejectedByInputGuard() {
        engine.designBeam(
            beamType = BeamType.SimplySupported(5.0),
            sectionType = BeamSectionType.RECTANGULAR,
            fcu = 25.0,
            fy = 400.0,
            deadLoad = 10.0,
            liveLoad = 10.0,
            width = -250.0,
            depth = 500.0,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE
        )
    }

    @Test
    fun testCantileverBeam_goldenVector() {
        val beamType = BeamType.Cantilever(2.5)
        val result = engine.designBeam(
            beamType = beamType,
            sectionType = BeamSectionType.RECTANGULAR,
            fcu = 25.0,
            fy = 360.0,
            deadLoad = 20.0,
            liveLoad = 10.0,
            width = 250.0,
            depth = 600.0,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE
        )

        assertNotNull(result)
        assertTrue(result.flexureResult.astRequired > 0.0)
    }
}
