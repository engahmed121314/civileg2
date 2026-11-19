package com.civileg.app.domain.calculations.column

import com.civileg.app.domain.entities.ColumnDesignResult
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Test

class ColumnDrawingPackageBuilderTest {

    @Test
    fun `test standard column drawing package invariants G1 to G4`() {
        val result = ColumnDesignResult(
            columnWidth = 300.0,
            columnDepth = 600.0,
            totalHeight = 3000.0,
            clearHeightIn = 2700.0,
            clearHeightOut = 2700.0,
            fcu = 30.0,
            fy = 400.0,
            Pu = 1500.0,
            MextIn = 50.0,
            MextOut = 0.0,
            isBraced = true,
            topCond = 1,
            botCond = 1,
            beamDepthIn = 600.0,
            beamDepthOut = 600.0,
            cover = 40.0,
            designCode = DesignCode.ECP,
            KfactorIn = 0.8,
            KfactorOut = 0.8,
            lambdaIn = 8.0,
            lambdaOut = 16.0,
            lambdaMax = 16.0,
            lambdaLimitShort = 15.0,
            lambdaLimitLong = 30.0,
            columnClassification = "Short",
            deflectionIn = 0.0,
            deflectionOut = 0.0,
            MaddIn = 0.0,
            MaddOut = 0.0,
            MdesIn = 50.0,
            MdesOut = 0.0,
            eccentricityIn = 33.3,
            eccentricityOut = 0.0,
            Phi = 0.65,
            alphaFactor = 0.8,
            Pu0 = 2500.0,
            axialCapacity = 2000.0,
            utilizationRatio = 0.75,
            AsRequired = 1800.0,
            AsMin = 1440.0,
            AsMax = 7200.0,
            AsProvided = 2412.0,
            rhoActual = 1.34,
            rhoMin = 0.8,
            rhoMax = 4.0,
            finalBars = "8Ø20",
            finalBarCount = 8,
            finalBarDia = 20,
            tieDia = 8,
            tieSpacingMax = 200.0,
            tieSpacingDense = 100.0,
            tieSpacingNormal = 150.0,
            condensationZoneLength = 600.0,
            isSafe = true
        )

        val layout = ColumnDrawingPackageBuilder.buildPackage(result, memberMark = "C101")

        // G1: Exactly 3 cuts (1-1, 2-2, 3-3)
        assertTrue("G1 failed", layout.validateG1())
        assertEquals(3, layout.cuts.size)
        assertEquals("1-1", layout.cuts[0].id)
        assertEquals("2-2", layout.cuts[1].id)
        assertEquals("3-3", layout.cuts[2].id)

        // G2: Mark consistency
        assertTrue("G2 failed", layout.validateG2())

        // G3: Provided As >= Required As
        assertTrue("G3 failed", layout.validateG3())

        // G4: Weight sum consistency
        assertTrue("G4 failed", layout.validateG4())

        // Ensure 8 bars have Cross-ties (Group 04)
        assertTrue(layout.groups.any { it.mark == "04" })
    }

    @Test
    fun `test buildDrawingScene for Column creates valid A3 primitives`() {
        val result = ColumnDesignResult(
            columnWidth = 400.0,
            columnDepth = 800.0,
            totalHeight = 4000.0,
            clearHeightIn = 3500.0,
            clearHeightOut = 3500.0,
            fcu = 35.0,
            fy = 400.0,
            Pu = 3000.0,
            MextIn = 120.0,
            MextOut = 0.0,
            isBraced = true,
            topCond = 1,
            botCond = 1,
            beamDepthIn = 600.0,
            beamDepthOut = 600.0,
            cover = 40.0,
            designCode = DesignCode.ACI,
            KfactorIn = 0.8,
            KfactorOut = 0.8,
            lambdaIn = 10.0,
            lambdaOut = 20.0,
            lambdaMax = 20.0,
            lambdaLimitShort = 22.0,
            lambdaLimitLong = 40.0,
            columnClassification = "Short",
            deflectionIn = 0.0,
            deflectionOut = 0.0,
            MaddIn = 0.0,
            MaddOut = 0.0,
            MdesIn = 120.0,
            MdesOut = 0.0,
            eccentricityIn = 40.0,
            eccentricityOut = 0.0,
            Phi = 0.65,
            alphaFactor = 0.8,
            Pu0 = 4500.0,
            axialCapacity = 3800.0,
            utilizationRatio = 0.79,
            AsRequired = 3200.0,
            AsMin = 2560.0,
            AsMax = 12800.0,
            AsProvided = 3801.0,
            rhoActual = 1.19,
            rhoMin = 0.8,
            rhoMax = 4.0,
            finalBars = "12Ø20",
            finalBarCount = 12,
            finalBarDia = 20,
            tieDia = 10,
            tieSpacingMax = 150.0,
            tieSpacingDense = 100.0,
            tieSpacingNormal = 150.0,
            condensationZoneLength = 800.0,
            isSafe = true
        )

        val layout = ColumnDrawingPackageBuilder.buildPackage(result, memberMark = "C201")
        val scene = ColumnDrawingPackageBuilder.buildDrawingScene(layout)

        assertTrue(scene.primitiveCount() > 0)
        assertTrue(scene.layerNames().contains("0_OUTLINE"))
        assertTrue(scene.layerNames().contains("0_CENTERLINE"))
        assertTrue(scene.layerNames().contains("0_TITLE_BLOCK"))
    }
}
