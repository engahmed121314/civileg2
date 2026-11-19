package com.civileg.app.domain.calculations.beam

import com.civileg.app.domain.entities.BeamDesignResult
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Test

class BeamDrawingPackageBuilderTest {

    @Test
    fun `test standard beam drawing package layout invariants G1 to G4`() {
        val result = BeamDesignResult(
            beamWidth = 300.0,
            beamDepth = 500.0,
            span = 4.0,
            clearSpan = 3.6,
            effectiveDepth = 450.0,
            fcu = 30.0,
            fy = 400.0,
            deadLoad = 15.0,
            liveLoad = 10.0,
            designCode = DesignCode.ECP,
            supportType = "SS",
            sectionType = "RECTANGULAR",
            bottomBarCount = 4,
            bottomBarDia = 16,
            topBarCount = 2,
            topBarDia = 12,
            stirrupDia = 8,
            stirrupSpacingSupport = 100.0,
            stirrupSpacingMidspan = 150.0,
            asRequired = 650.0
        )

        val layout = BeamDrawingPackageBuilder.buildPackage(result, memberMark = "B101")

        assertTrue("G1 failed", layout.validateG1())
        assertEquals(3, layout.cuts.size)
        assertEquals("1-1", layout.cuts[0].id)
        assertEquals("2-2", layout.cuts[1].id)
        assertEquals("3-3", layout.cuts[2].id)

        assertTrue("G2 failed", layout.validateG2())
        assertTrue("G3 failed: cuts=${layout.cuts.map { "${it.id}: prov=${it.providedAsMm2} req=${it.requiredAsMm2}" }}", layout.validateG3())
        assertTrue("G4 failed: total=${layout.totals.totalWeightKg} sum=${layout.schedule.sumOf { it.totalWeightKg }}", layout.validateG4())

        assertEquals(40.0, layout.coverMm, 0.01)
        assertFalse(layout.groups.any { it.mark == "05" })
    }

    @Test
    fun `test deep beam drawing package includes skin reinforcement`() {
        val deepResult = BeamDesignResult(
            beamWidth = 300.0,
            beamDepth = 750.0,
            span = 6.0,
            clearSpan = 5.5,
            effectiveDepth = 700.0,
            fcu = 35.0,
            fy = 400.0,
            deadLoad = 25.0,
            liveLoad = 15.0,
            designCode = DesignCode.ACI,
            supportType = "SS",
            sectionType = "RECTANGULAR",
            bottomBarCount = 6,
            bottomBarDia = 20,
            topBarCount = 2,
            topBarDia = 12,
            stirrupDia = 10,
            stirrupSpacingSupport = 100.0,
            stirrupSpacingMidspan = 150.0,
            asRequired = 1400.0
        )

        val layout = BeamDrawingPackageBuilder.buildPackage(deepResult, memberMark = "B201")

        assertTrue("G1 failed", layout.validateG1())
        assertTrue("G2 failed", layout.validateG2())
        assertTrue("G3 failed", layout.validateG3())

        val skinGroup = layout.groups.find { it.mark == "05" }
        assertNotNull(skinGroup)
        assertEquals(10, skinGroup?.diameterMm)
        assertTrue(layout.details.any { it.id == "D3" })
    }

    @Test
    fun `test buildDrawingScene creates valid primitives and title block`() {
        val result = BeamDesignResult(
            beamWidth = 300.0,
            beamDepth = 600.0,
            span = 5.0,
            clearSpan = 4.6,
            effectiveDepth = 550.0,
            fcu = 30.0,
            fy = 400.0,
            deadLoad = 20.0,
            liveLoad = 10.0,
            designCode = DesignCode.SBC,
            supportType = "SS",
            sectionType = "RECTANGULAR"
        )

        val layout = BeamDrawingPackageBuilder.buildPackage(result, memberMark = "B301")
        val scene = BeamDrawingPackageBuilder.buildDrawingScene(layout)

        assertTrue(scene.primitiveCount() > 0)
        assertTrue(scene.layerNames().contains("0_OUTLINE"))
        assertTrue(scene.layerNames().contains("0_CENTERLINE"))
        assertTrue(scene.layerNames().contains("0_TITLE_BLOCK"))
    }
}
