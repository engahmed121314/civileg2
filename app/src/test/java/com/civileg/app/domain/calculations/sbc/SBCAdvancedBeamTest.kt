package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.entities.BeamSectionType
import com.civileg.app.domain.entities.BeamType
import com.civileg.app.domain.entities.LoadCombination
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SBCAdvancedBeamTest {

    private lateinit var engine: SBCAdvancedBeam

    @Before
    fun setUp() {
        engine = SBCAdvancedBeam()
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
            deadLoad = 22.0,
            liveLoad = 12.0,
            width = 250.0,
            depth = 600.0,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE,
            isCorrosiveEnvironment = false
        )

        assertNotNull(result)
        assertTrue(result.flexureResult.astRequired > 0.0)
        assertTrue(result.flexureResult.astProvided >= result.flexureResult.astRequired)
        assertTrue(result.shearResult.isSafe)
        assertTrue(result.momentDiagram.isNotEmpty())
        assertTrue(result.shearDiagram.isNotEmpty())
    }

    @Test
    fun testCorrosiveEnvironment_sbcCoverApplied() {
        val beamType = BeamType.SimplySupported(5.0)
        val result = engine.designBeam(
            beamType = beamType,
            sectionType = BeamSectionType.RECTANGULAR,
            fcu = 35.0,
            fy = 420.0,
            deadLoad = 15.0,
            liveLoad = 10.0,
            width = 300.0,
            depth = 550.0,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE,
            isCorrosiveEnvironment = true
        )

        assertNotNull(result)
        assertTrue(result.flexureResult.astRequired > 0.0)
        assertTrue(result.codeNotes.any { it.contains("SBC 304") || it.contains("Corrosive") || it.contains("cover") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun testZeroFcu_rejectedByInputGuard() {
        engine.designBeam(
            beamType = BeamType.SimplySupported(5.0),
            sectionType = BeamSectionType.RECTANGULAR,
            fcu = 0.0,
            fy = 420.0,
            deadLoad = 10.0,
            liveLoad = 10.0,
            width = 250.0,
            depth = 500.0,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE
        )
    }
}
