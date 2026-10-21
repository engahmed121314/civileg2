package com.civileg.app.utils

import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.utils.CalculatorEngine.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoqEstimatorTest {

    @Test
    fun `beam boq contains concrete steel and formwork`() {
        val result = BeamResult(
            width = 300.0, depth = 600.0, span = 6.0,
            concreteVolume = 1.08, steelWeight = 120.0,
            isSafe = true, code = DesignCode.ECP
        )

        val rows = BoqEstimator.beamRows(result, "B1")

        assertEquals(3, rows.size)
        assertEquals(listOf("m3", "kg", "m2"), rows.map { it.unit })
        assertEquals(1.08, rows[0].quantity, 1e-9)
        // 6.0 * (2 * 600 + 300) / 1000 = 9.0 m2
        assertEquals(9.0, rows[2].quantity, 1e-9)
        assertTrue(rows.all { it.quantity >= 0.0 })
    }

    @Test
    fun `tank boq includes wall and base formwork`() {
        val result = TankResult(
            type = TankType.UNDERGROUND,
            length = 4.0, width = 3.0, height = 2.5,
            wallThickness = 0.25, baseThickness = 0.3,
            wallReinforcement = ReinforcementBar(diameter = 12, spacing = 200.0, numBars = 0),
            isSafe = true, concreteVolume = 12.0, steelWeight = 600.0,
            cost = 0.0, code = DesignCode.ECP
        )

        val rows = BoqEstimator.tankRows(result, "T1")

        assertEquals(4, rows.size)
        // 2 * (4 + 3) * 2.5 = 35 m2 walls
        assertEquals(35.0, rows[2].quantity, 1e-9)
        // 4 * 3 = 12 m2 base
        assertEquals(12.0, rows[3].quantity, 1e-9)
    }

    @Test
    fun `retaining wall boq scales with wall length`() {
        val result = RetainingWallResult(
            height = 4.0, stemThickness = 300.0, baseWidth = 2500.0,
            stemReinforcement = ReinforcementBar(diameter = 16, spacing = 150.0, numBars = 0),
            baseReinforcement = ReinforcementBar(diameter = 16, spacing = 200.0, numBars = 0),
            isSafe = true, concreteVolume = 3.0, steelWeight = 200.0,
            cost = 0.0, code = DesignCode.ECP
        )

        val perMetre = BoqEstimator.retainingWallRows(result, 1.0, "RW1")
        val perTenMetres = BoqEstimator.retainingWallRows(result, 10.0, "RW1")

        assertEquals(3.0, perMetre[0].quantity, 1e-9)
        assertEquals(30.0, perTenMetres[0].quantity, 1e-9)
        assertEquals(200.0, perMetre[1].quantity, 1e-9)
        assertEquals(2000.0, perTenMetres[1].quantity, 1e-9)
        assertEquals(8.0, perMetre[2].quantity, 1e-9)
        assertEquals(80.0, perTenMetres[2].quantity, 1e-9)
    }

    @Test
    fun `slab boq derives soffit formwork from panel dimensions`() {
        val result = SlabResult(
            thickness = 200.0,
            reinforcementMain = ReinforcementBar(diameter = 12, spacing = 150.0, numBars = 0),
            reinforcementSecondary = ReinforcementBar(diameter = 10, spacing = 200.0, numBars = 0),
            isSafe = true, concreteVolume = 4.0, steelWeight = 300.0, cost = 0.0,
            code = DesignCode.ECP
        )

        val rows = BoqEstimator.slabRows(result, 4.0, 5.0, "S1")

        assertEquals(3, rows.size)
        assertEquals(20.0, rows[2].quantity, 1e-9)
    }

    @Test
    fun `pile boq combines pile and cap quantities`() {
        val result = com.civileg.app.domain.PileDesignResult(
            pileType = "BORED", soilType = "SAND",
            pileDiameterMm = 600.0, pileLengthM = 12.0, numberOfPiles = 4,
            fcu = 30.0, fy = 400.0,
            capacityResult = com.civileg.app.domain.calculations.base.PileCapacityResult(
                3000.0, 1500.0, 1800.0, 1200.0, 2.0, 0.8
            ),
            groupResult = com.civileg.app.domain.calculations.base.PileGroupResult(
                0.85, 5100.0, 1500.0, 1800.0, 4, "2x2"
            ),
            settlementResult = com.civileg.app.domain.calculations.base.PileSettlementResult(
                5.0, 3.0, 8.0, 25.0, true
            ),
            capResult = com.civileg.app.domain.calculations.base.PileCapResult(
                capWidth = 2400.0, capLength = 2400.0, capThickness = 800.0,
                punchingShearOk = true, punchingShearStress = 1.2, punchingShearCapacity = 2.0,
                beamShearOk = true, beamShearStress = 0.8, beamShearCapacity = 1.5,
                flexuralReinforcement = com.civileg.app.domain.calculations.base.RebarDetail(
                    12, 16, 150, 2412.0, 2000.0, 0.83
                ),
                punchingReinforcement = null,
                concreteVolume = 4.6, steelWeight = 380.0
            ),
            lateralCapacity = 200.0, lateralUtilizationRatio = 0.4,
            negativeSkinFriction = 0.0,
            pileReinforcement = com.civileg.app.domain.PileReinforcementResult(
                8, 20, 2513.0, 2000.0, 8, 200, true, 0.01
            ),
            isSafe = true, utilizationRatio = 0.8
        )

        val rows = BoqEstimator.pileRows(result, "PG1")

        assertEquals(4, rows.size)
        // 4 piles * pi * 0.3^2 * 12 = 13.57 m3 + 4.6 cap
        assertEquals(4 * Math.PI * 0.09 * 12 + 4.6, rows[0].quantity, 1e-6)
        assertEquals(2 * (2400.0 + 2400.0) * 800.0 / 1_000_000.0, rows[3].quantity, 1e-9)
        assertTrue(rows.all { it.quantity > 0.0 })
    }

    @Test
    fun `strap footing boq adds pad and strap formwork`() {
        fun pad(w: Double, l: Double) = FootingResult(
            type = FootingType.ISOLATED, width = w, length = l, thickness = 500.0,
            soilPressure = 150.0, allowablePressure = 200.0,
            reinforcementBottom = ReinforcementBar(numBars = 12, diameter = 16, spacing = 150.0),
            isSafe = true, code = DesignCode.ECP,
            concreteVolume = 1.2, steelWeight = 90.0, cost = 0.0
        )
        val result = StrapFootingResult(
            footing1 = pad(2000.0, 2000.0), footing2 = pad(1800.0, 1800.0),
            strapBeamWidth = 400.0, strapBeamDepth = 800.0,
            strapTopReinforcement = ReinforcementBar(numBars = 4, diameter = 16, spacing = 150.0),
            strapBottomReinforcement = ReinforcementBar(numBars = 4, diameter = 16, spacing = 150.0),
            reactions = Pair(500.0, 600.0), isSafe = true,
            concreteVolume = 3.0, steelWeight = 250.0, utilizationRatio = 0.7
        )

        val rows = BoqEstimator.strapFootingRows(result, distanceM = 5.0, mark = "SF1")

        assertEquals(3, rows.size)
        // pads: 2*(2+2)*0.5 + 2*(1.8+1.8)*0.5 = 7.6; strap sides: 2*0.8*5 = 8.0
        assertEquals(15.6, rows[2].quantity, 1e-6)
    }

    @Test
    fun `waffle slab boq uses design volumes and panel soffit`() {
        val result = com.civileg.app.domain.calculations.base.WaffleSlabDesign.WaffleSlabResult(
            isSafe = true, utilizationRatio = 0.9,
            concreteVolume = 18.0, steelWeight = 900.0
        )

        val rows = BoqEstimator.waffleSlabRows(result, 6.0, 7.5, "WS1")

        assertEquals(3, rows.size)
        assertEquals(18.0, rows[0].quantity, 1e-9)
        assertEquals(45.0, rows[2].quantity, 1e-9)
    }

    @Test
    fun `combined footing boq derives perimeter formwork`() {
        val result = com.civileg.app.domain.calculations.ecp.CombinedFootingResult(
            footingLength = 5.2, footingWidth = 2.4, footingThickness = 600.0,
            effectiveDepth = 515.0, qMax = 220.0, qMin = 150.0,
            centroidFromCol1 = 2.6, longBottomAs = 18.0, longTopAs = 12.0,
            longBottomBars = "8\u00d816", longTopBars = "6\u00d816",
            transBottomAs = 14.0, transBottomBars = "12\u00d816",
            oneWayShearVu = 300.0, oneWayShearVc = 450.0,
            oneWayShearSafe = true, punchingCol1Safe = true, punchingCol2Safe = true,
            concreteVolume = 7.5, steelWeight = 380.0, isSafe = true,
            warnings = emptyList()
        )

        val rows = BoqEstimator.combinedFootingRows(result, "CF1")

        assertEquals(3, rows.size)
        assertEquals(7.5, rows[0].quantity, 1e-9)
        assertEquals(380.0, rows[1].quantity, 1e-9)
        assertEquals(2 * (5.2 + 2.4) * 0.6, rows[2].quantity, 1e-9)
    }

    @Test
    fun `excavation and blinding use working space offset`() {
        val footing = CalculatorEngine.FootingResult(
            type = FootingType.ISOLATED, width = 2000.0, length = 2000.0, thickness = 500.0,
            soilPressure = 150.0, allowablePressure = 200.0,
            reinforcementBottom = ReinforcementBar(numBars = 12, diameter = 16, spacing = 150.0),
            isSafe = true, code = DesignCode.ECP,
            concreteVolume = 2.0, steelWeight = 80.0, cost = 0.0
        )

        val rows = BoqEstimator.footingRows(
            footing, "F1", excavationDepthM = 1.5, workingSpaceM = 0.5, blindingThicknessM = 0.1
        )

        assertEquals(5, rows.size)
        // (2 + 1) * (2 + 1) * 0.1
        assertEquals(0.9, rows[3].quantity, 1e-9)
        // (2 + 1) * (2 + 1) * 1.5
        assertEquals(13.5, rows[4].quantity, 1e-9)
    }

    @Test
    fun `retaining wall adds excavation and earth-face waterproofing`() {
        val result = CalculatorEngine.RetainingWallResult(
            height = 4.0, stemThickness = 300.0, baseWidth = 2500.0,
            stemReinforcement = ReinforcementBar(numBars = 5, diameter = 16, spacing = 150.0),
            baseReinforcement = ReinforcementBar(numBars = 5, diameter = 16, spacing = 150.0),
            isSafe = true, concreteVolume = 2.0, steelWeight = 100.0, cost = 0.0,
            code = DesignCode.ECP
        )

        val rows = BoqEstimator.retainingWallRows(
            result, wallLengthM = 10.0, excavationDepthM = 2.0, waterproofingAreaM2 = 40.0
        )

        assertEquals(6, rows.size)
        assertEquals(20.0, rows[4].quantity, 1e-9)
        assertEquals(40.0, rows[5].quantity, 1e-9)
    }

    @Test
    fun `underground tank waterproofing covers base and walls`() {
        val rect = BoqEstimator.tankWaterproofingArea(false, 5.0, 4.0, 3.0)
        val circular = BoqEstimator.tankWaterproofingArea(true, 4.0, 4.0, 3.0)

        assertEquals(74.0, rect, 1e-9)
        assertEquals(Math.PI * 4.0 * 4.0 / 4.0 + Math.PI * 4.0 * 3.0, circular, 1e-9)
    }
}
