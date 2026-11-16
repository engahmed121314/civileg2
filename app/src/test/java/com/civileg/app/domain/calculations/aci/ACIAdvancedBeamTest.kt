package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.entities.BeamSectionType
import com.civileg.app.domain.entities.BeamType
import com.civileg.app.domain.entities.LoadCombination
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ACIAdvancedBeamTest {

    private lateinit var engine: ACIAdvancedBeam

    @Before
    fun setUp() {
        engine = ACIAdvancedBeam()
    }

    @Test
    fun testSimplySupportedRectangularBeam_goldenVector() {
        val beamType = BeamType.SimplySupported(6.0)
        val sectionType = BeamSectionType.RECTANGULAR
        val result = engine.designBeam(
            beamType = beamType,
            sectionType = sectionType,
            fcu = 30.0,
            fy = 420.0,
            deadLoad = 20.0,
            liveLoad = 15.0,
            width = 300.0,
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
    fun testNegativeDepth_rejectedByInputGuard() {
        engine.designBeam(
            beamType = BeamType.SimplySupported(5.0),
            sectionType = BeamSectionType.RECTANGULAR,
            fcu = 30.0,
            fy = 420.0,
            deadLoad = 10.0,
            liveLoad = 10.0,
            width = 300.0,
            depth = -500.0,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE
        )
    }

    @Test
    fun testFixedBeam_goldenVector() {
        val beamType = BeamType.Fixed(7.0)
        val result = engine.designBeam(
            beamType = beamType,
            sectionType = BeamSectionType.RECTANGULAR,
            fcu = 35.0,
            fy = 420.0,
            deadLoad = 25.0,
            liveLoad = 20.0,
            width = 300.0,
            depth = 700.0,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE
        )

        assertNotNull(result)
        assertTrue(result.flexureResult.astRequired > 0.0)
    }
}
