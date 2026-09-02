package com.civileg.app.engineering

import com.civileg.app.domain.PileGroupInput
import com.civileg.app.domain.PileInput
import com.civileg.app.domain.PileType
import com.civileg.app.domain.SoilType
import com.civileg.app.domain.calculations.aci.ACIPileFoundation
import com.civileg.app.domain.calculations.ecp.ECPAdvancedColumn
import com.civileg.app.domain.calculations.ecp.ECPPileFoundation
import com.civileg.app.domain.entities.ColumnEndConditions
import com.civileg.app.domain.entities.ColumnType
import com.civileg.app.domain.entities.ConnectedSlabType
import com.civileg.app.domain.entities.EndCondition
import com.civileg.core.calculations.entities.LoadCombination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tier-0 golden gates: A13 biaxial wiring · A6 group anchor · A7 deflection cap.
 */
class Tier0ColumnPileGoldenTest {

    // ------------------------------------------------------------------
    // A13 — genuine Bresler + Load Contour path must be reachable
    // ------------------------------------------------------------------

    private fun advancedColumn(my: Double) = ECPAdvancedColumn().designAdvancedColumn(
        columnType = ColumnType.Rectangular(width = 400.0, depth = 600.0),
        fcu = 30.0, fy = 400.0,
        // ~46% of phiPn(max) — inside the envelope so contour ratios are meaningful
        axialLoad = 1200.0, momentX = 150.0, momentY = my,
        unsupportedLength = 3.0,
        endConditions = ColumnEndConditions(EndCondition.PINNED, EndCondition.PINNED),
        connectedSlab = ConnectedSlabType.SOLID,
        hasCap = false,
        inventory = null,
        loadCombination = LoadCombination.DEAD_LIVE
    )

    @Test
    fun `A13 biaxial moments trigger the genuine contour implementation`() {
        // DO NOT convert to `elvis + fail()`: JUnit4 fail() returns void, which
        // widens b to Any? and breaks every member access below. Use !! then
        // assert non-null semantics via the check itself.
        val b = requireNotNull(advancedColumn(100.0).biaxialCheck) {
            "biaxial check must run for Mx>0.1 && My>0.1"
        }
        // Wiring gate: the deleted stub printed "Bresler's reciprocal
        // approximation"; the genuine path prints the Hsu alpha + Load Contour.
        assertTrue(
            "expected contour path, got: ${b.formula}",
            b.formula.contains("Contour") && b.formula.contains("Hsu")
        )
    }

    @Test
    fun `A13 interaction demand grows monotonically with My`() {
        val small = advancedColumn(50.0).biaxialCheck!!.interactionFactor
        val large = advancedColumn(300.0).biaxialCheck!!.interactionFactor
        assertTrue("factor(My=300)=$large must exceed factor(My=50)=$small", large > small)
    }

    // ------------------------------------------------------------------
    // A6 — ECP group capacity anchored to supplied single-pile capacity
    // ------------------------------------------------------------------

    @Test
    fun `A6 ECP group uses explicit single-pile capacity instead of invented reference`() {
        val res = ECPPileFoundation().checkGroupEfficiency(
            PileGroupInput(
                numberOfPiles = 4, pileDiameter = 600.0, spacing = 3.0,
                pattern = "2x2", soilType = SoilType.CLAY,
                pileLength = 15.0, pileType = PileType.BORED,
                singleCapacityKn = 800.0
            )
        )
        // Converse-Labarre (ECP impl): theta=atan(D/s)=18.435deg, m=nn=2
        // numerator = D*(1+1) + D*sqrt2*1*1 = 2.04853 ; denom = 4*1.8 = 7.2
        // eta = 1 - (18.435/90)*(2.04853/7.2) = 0.94171
        assertEquals(800.0, res.individualCapacity, 1e-6)
        assertEquals(800.0 * 4 * 0.94171, res.groupCapacity, 1.0)
    }

    // ------------------------------------------------------------------
    // A7 — lateral deflection reported uncapped
    // ------------------------------------------------------------------

    @Test
    fun `A7 ACI deflection can exceed the old silent 25mm cap`() {
        // Rock socket case: Hu = 3*cu*D = 3*500*0.5 = 750 kN
        // delta = Hu*D^3 / (8*25000*pi*(D/2)^4) * 1000
        //       = 750*0.125 / (200000*pi*3.90625e-3) * 1000 = 38.20 mm  (>25)
        val res = ACIPileFoundation().calculateLateralCapacity(
            PileInput(
                pileDiameter = 500.0, pileLength = 12.0,
                soilType = SoilType.ROCK, cu = 500.0, phi = 40.0, gammaSoil = 20.0,
                lateralLoad = 100.0
            )
        )
        assertEquals(38.20, res.deflectionAtHead, 0.2)
    }
}
