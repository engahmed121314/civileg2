package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.domain.entities.*
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class SteelWarehouseOutputsTest {
    private val engine = CalculatorEngine(mockk(relaxed = true))
    private val col = SteelSectionType.ISection(h = 300.0, bf = 300.0, tf = 14.0, tw = 8.5, grade = SteelGrade.ST37, customName = "COL")
    private val raf = SteelSectionType.ISection(h = 330.0, bf = 160.0, tf = 11.5, tw = 7.5, grade = SteelGrade.ST37, customName = "RAF")

    private fun run(span: Double = 20.0, live: Double = 0.5) = engine.designSteelWarehouse(
        SteelWarehouseInputs(span = span, length = 60.0, eaveHeight = 6.0, ridgeHeight = 7.0, baySpacing = 6.0,
            liveLoad = live, overrideColumnSection = col, overrideRafterSection = raf)
    )

    @Test fun takeoffIsRealPerMemberWeightNotFiftyFifty() {
        val r = run()
        val c = r.materialTakeoff.entries.first { it.key.startsWith("Columns") }.value
        val f = r.materialTakeoff.entries.first { it.key.startsWith("Rafters") }.value
        // 11 frames (60 m / 6 m + 1); columns 2 x 6 m each; hand: area*7850 kg/m3
        assertEquals(2 * 6.0 * col.area * 7850.0 / 1e9 * 11, c, 1e-9)
        assertNotEquals(c, f, 1e-6)
        val sum = r.materialTakeoff.values.sum()
        assertEquals(r.totalWeight, sum, 1e-9)
        assertTrue(r.materialTakeoff.keys.any { it.contains("COL") })
    }

    @Test fun overloadedUtilizationIsNotClampedTo1_5() {
        val r = run(span = 40.0, live = 20.0)
        assertTrue(r.mainFrame.utilizationMoment > 1.5)
    }

    @Test fun noInventedConnections() {
        assertTrue(run().connections.isEmpty())
    }
}

class SteelWarehouseFootingTest {
    private val engine = CalculatorEngine(mockk(relaxed = true))
    private fun inp(q: Double?) = SteelWarehouseInputs(span = 20.0, length = 60.0, eaveHeight = 6.0, ridgeHeight = 7.0,
        baySpacing = 6.0, deadLoad = 0.25, liveLoad = 0.5, allowableSoilBearingKnM2 = q)

    @Test fun noSoilBearingMeansNotIssued() {
        val f = engine.designSteelWarehouse(inp(null)).footingPreliminary!!
        assertFalse(f.issued)
    }

    @Test fun planSizeFollowsHandFormula() {
        val r = engine.designSteelWarehouse(inp(200.0))
        val f = r.footingPreliminary!!
        // hand: (0.25+0.5)*6*10 = 45 kN gravity + steel share (tons*9.80665/11)/2
        val p = 45.0 + r.totalWeight * 9.80665 / 11 / 2
        assertEquals(p, f.serviceLoadPerColumnKn, 1e-9)
        val side = Math.ceil(Math.sqrt(p / 200.0) * 1000 / 50) * 50
        assertEquals(side, f.squareSideMm, 1e-9)
        // lower bearing must give a larger footing
        assertTrue(engine.designSteelWarehouse(inp(100.0)).footingPreliminary!!.squareSideMm > f.squareSideMm)
    }
}

class SteelWarehouseBracingTest {
    private val engine = CalculatorEngine(mockk(relaxed = true))
    private fun inp(h: Double?, bays: List<Int>? = null) = SteelWarehouseInputs(span = 20.0, length = 60.0, eaveHeight = 6.0,
        ridgeHeight = 7.0, baySpacing = 6.0, deadLoad = 0.25, liveLoad = 0.5, bracedBays = bays, bracingLateralLoadKn = h)

    @Test fun geometryWeightAndForceFollowHandValues() {
        val b = engine.designSteelWarehouse(inp(30.0)).bracing!!
        // hand: L = sqrt(36+36) = 8.485281, angle = 45 deg, 10 bays
        assertEquals(8.485281374, b.lengthM, 1e-6)
        assertEquals(45.0, b.angleDeg, 1e-9)
        // C 160: A = 2*65*7.5 + (160-15)*5.5 = 1772.5 mm2 -> 13.914125 kg/m
        assertEquals(1772.5, b.areaMm2, 1e-9)
        assertEquals(13.9141, b.weightKgM, 1e-4)
        assertEquals(listOf(1, 10), b.bracedBays)
        assertTrue(b.usedDefaultBays)
        assertEquals(8, b.braceCount)                       // 2 bays x 2 walls x 2 diagonals
        assertEquals(13.914125 * 8.485281374 * 8, b.totalWeightKg, 1e-3)   // = 944.5 kg
        // N = 30 / cos45 = 42.4264 kN
        assertEquals(42.42640687, b.braceForceKn!!, 1e-6)
    }

    @Test fun noEnteredLoadMeansForceNotAssessed() {
        val b = engine.designSteelWarehouse(inp(null)).bracing!!
        assertNull(b.braceForceKn)
        assertTrue(b.lines.any { it.contains("NOT ASSESSED") })
    }

    @Test fun userBracedBaysAndInvalidBays() {
        assertEquals(12, engine.designSteelWarehouse(inp(null, listOf(1, 5, 10))).bracing!!.braceCount)
        assertFalse(engine.designSteelWarehouse(inp(null, listOf(11))).bracing!!.issued)
    }

    private fun aci(h: Double?, fy: Double?, fu: Double?, ae: Double?, code: com.civileg.app.domain.entities.DesignCode = com.civileg.app.domain.entities.DesignCode.ACI) =
        inp(h).copy(code = code, braceFyMpa = fy, braceFuMpa = fu, braceNetAreaMm2 = ae)

    @Test fun aiscD2TensionCheckFollowsHandValues() {
        val b = engine.designSteelWarehouse(aci(30.0, 250.0, 400.0, 1500.0)).bracing!!
        // hand: yield 0.90 x 250 x 1772.5 / 1000 = 398.8125 kN; rupture 0.75 x 400 x 1500 / 1000 = 450 kN; N = 42.4264 kN
        assertEquals("PASS", b.tensionCheck)
        assertEquals(42.42640687 / 398.8125, b.tensionUtilisation!!, 1e-9)
        assertEquals("FAIL", engine.designSteelWarehouse(aci(400.0, 250.0, 400.0, 1500.0)).bracing!!.tensionCheck)
        // rupture governs when Ae is small: 0.75 x 400 x 1000 / 1000 = 300 kN
        assertEquals(42.42640687 / 300.0, engine.designSteelWarehouse(aci(30.0, 250.0, 400.0, 1000.0)).bracing!!.tensionUtilisation!!, 1e-9)
    }

    @Test fun aiscD2NotAssessedWithoutInputsOrForOtherCodes() {
        assertNull(engine.designSteelWarehouse(aci(30.0, 250.0, 400.0, null)).bracing!!.tensionCheck)
        assertNull(engine.designSteelWarehouse(aci(30.0, null, 400.0, 1500.0)).bracing!!.tensionCheck)
        assertNull(engine.designSteelWarehouse(aci(30.0, 250.0, 400.0, 1500.0, com.civileg.app.domain.entities.DesignCode.SBC)).bracing!!.tensionCheck)
        assertNull(engine.designSteelWarehouse(aci(30.0, 250.0, 400.0, 5000.0)).bracing!!.tensionCheck)   // Ae > Ag
        assertNull(engine.designSteelWarehouse(aci(null, 250.0, 400.0, 1500.0)).bracing!!.tensionCheck)
    }

    @Test fun ecp205LrfdTensionAndSlendernessFollowHandValues() {
        val ecp = com.civileg.app.domain.entities.DesignCode.ECP
        val b = engine.designSteelWarehouse(aci(30.0, 250.0, 400.0, 1500.0, ecp)).bracing!!
        // ECP 205 LRFD 2008 3.1.1 (p.3-1): phi 0.85 yield = 0.85 x 250 x 1772.5 / 1000 = 376.65625 kN; phi 0.70 fracture = 0.70 x 400 x 1500 / 1000 = 420 kN
        assertEquals("PASS", b.tensionCheck)
        assertEquals(42.42640687 / 376.65625, b.tensionUtilisation!!, 1e-9)
        // fracture governs with a small net area: 0.70 x 400 x 1000 / 1000 = 280 kN
        assertEquals(42.42640687 / 280.0, engine.designSteelWarehouse(aci(30.0, 250.0, 400.0, 1000.0, ecp)).bracing!!.tensionUtilisation!!, 1e-9)
        // Table 2.3 (p.2-8): tension members 300; L/r = 8485.28 / 13.96 = 607.9 -> FAIL
        assertEquals("FAIL", b.slendernessCheck)
        assertNull(engine.designSteelWarehouse(aci(30.0, 250.0, 400.0, 1500.0)).bracing!!.slendernessCheck)   // American selection: no slenderness limit compared
    }
}
