package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.entities.ColumnType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SBCAdvancedBeam.checkStrongColumnWeakBeam and SBCAdvancedColumn.designSeismicConfinement.
 */
class ColumnSeismicCheckTest {

    private lateinit var advancedBeam: SBCAdvancedBeam
    private lateinit var advancedColumn: SBCAdvancedColumn

    @Before
    fun setup() {
        advancedBeam = SBCAdvancedBeam()
        advancedColumn = SBCAdvancedColumn()
    }

    @Test
    fun checkStrongColumnWeakBeam_safeCase_returnsTrue() {
        val (_, isSafe, notes) = advancedBeam.checkStrongColumnWeakBeam(
            columnSumMoment = 300.0, // kN.m
            beamTopReinforcement = 1200.0, // mm2
            beamBottomReinforcement = 800.0, // mm2
            effectiveDepth = 500.0, // mm
            fy = 420.0
        )
        assertNotNull(notes)
        assertFalse("300 kN.m column moment is less than required 504 kN.m", isSafe)
    }

    @Test
    fun checkStrongColumnWeakBeam_highColumnMoment_returnsSafe() {
        val (ratio, isSafe, _) = advancedBeam.checkStrongColumnWeakBeam(
            columnSumMoment = 600.0, // kN.m
            beamTopReinforcement = 1000.0,
            beamBottomReinforcement = 800.0,
            effectiveDepth = 500.0,
            fy = 420.0
        )
        assertTrue("Ratio >= 1.0", ratio >= 1.0)
        assertTrue("Should be safe", isSafe)
    }

    @Test
    fun designSeismicConfinement_validInput_producesResult() {
        val result = advancedColumn.designSeismicConfinement(
            columnType = ColumnType.Rectangular(300.0, 600.0),
            mainBarDiameter = 20.0,
            numberOfBars = 8,
            isSpiral = false,
            fy = 420.0,
            fcu = 30.0
        )
        assertNotNull(result)
        assertTrue("Hoop spacing in hinge zone > 0", result.hoopSpacingHingeZone > 0)
        assertTrue("Confinement zone length > 0", result.confinementZoneLength > 0)
        assertTrue("Num hinge ties > 0", result.numHingeTies > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun designSeismicConfinement_throwsForZeroFy() {
        advancedColumn.designSeismicConfinement(
            columnType = ColumnType.Rectangular(300.0, 600.0),
            mainBarDiameter = 20.0,
            numberOfBars = 8,
            isSpiral = false,
            fy = 0.0,
            fcu = 30.0
        )
    }
}
