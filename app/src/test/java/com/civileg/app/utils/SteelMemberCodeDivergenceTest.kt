package com.civileg.app.utils

import com.civileg.app.domain.entities.*
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * P0-2 regression guard: CalculatorEngine.calculateSteelMember must delegate to the
 * code-specific domain engines (ECP / AISC / SBC) instead of the old inline stub that
 * produced identical results for every code and inflated KL/r by x1000.
 */
class SteelMemberCodeDivergenceTest {

    private val engine = CalculatorEngine(mockk(relaxed = true))

    private val section = SteelSectionType.ISection(
        h = 300.0, bf = 150.0, tf = 10.7, tw = 7.1,
        grade = SteelGrade.ST37, customName = "IPE 300"
    )

    private val inputs = SteelInputs(
        axialLoad = 300.0,
        moment = 40.0,
        shear = 20.0,
        unbracedLength = 3000.0,
        length = 3000.0,
        grade = SteelGrade.ST37
    )

    @Test
    fun `all three codes produce positive finite capacities`() {
        for (code in listOf(
            DesignCode.ECP,
            DesignCode.ACI,
            DesignCode.SBC
        )) {
            val r = engine.calculateSteelMember(section, SteelMemberType.COLUMN, inputs, code)
            assertTrue("$code axial capacity must be > 0", r.axialCapacity > 0.0)
            assertTrue("$code flexural capacity must be > 0", r.flexuralCapacity > 0.0)
            assertTrue("$code shear capacity must be > 0", r.shearCapacity > 0.0)
            assertTrue("$code axial finite", r.axialCapacity.isFinite())
            assertTrue("$code flexural finite", r.flexuralCapacity.isFinite())
            // AUDIT 9.20 (P1-6): engine-derived connection must be non-null and code-aware
            assertTrue("$code connectionDesign must be non-null", r.connectionDesign != null)
            assertTrue("$code connectionDesign endPlateThickness must be > 0", (r.connectionDesign!!.endPlateThickness) > 0.0)
        }
    }

    @Test
    fun `ECP buckling slenderness is sane (no x1000 length bug)`() {
        val r = engine.calculateSteelMember(
            section, SteelMemberType.COLUMN, inputs, DesignCode.ECP
        )
        val klr = r.bucklingCheck?.slendernessRatio
        assertTrue("Buckling check must be present for a column", klr != null)
        // 3 m IPE300 column → KL/r ≈ 24. The old facade applied x1000 (L already in mm)
        // producing ~24,000 and a bogus "slender" verdict.
        assertTrue("KL/r=$klr must be < 200", klr!! < 200.0)
        assertTrue("KL/r=$klr must be > 0", klr > 0.0)
    }

    @Test
    fun `delegation replaces the old single-formula stub`() {
        // Old stub: φPn = 0.9·fy·Ag/1000 ; φMn = 0.9·fy·Sx/1e6
        val fy = SteelGrade.ST37.fy
        val oldAxial = 0.9 * fy * section.area / 1000.0
        val oldFlex = 0.9 * fy * section.sx / 1e6

        val ecp = engine.calculateSteelMember(section, SteelMemberType.COLUMN, inputs, DesignCode.ECP)
        val aci = engine.calculateSteelMember(section, SteelMemberType.COLUMN, inputs, DesignCode.ACI)

        val axialDiffers = abs(ecp.axialCapacity - oldAxial) / oldAxial > 0.01 ||
            abs(aci.axialCapacity - oldAxial) / oldAxial > 0.01
        val flexDiffers = abs(ecp.flexuralCapacity - oldFlex) / oldFlex > 0.01 ||
            abs(aci.flexuralCapacity - oldFlex) / oldFlex > 0.01

        assertTrue("At least one code must diverge from the old stub axial capacity", axialDiffers)
        assertTrue("At least one code must diverge from the old stub flexural capacity", flexDiffers)
    }

    private fun warehouseInputs(code: DesignCode, rafter: SteelSectionType) = SteelWarehouseInputs(
        span = 10.0, length = 12.0, eaveHeight = 5.0, ridgeHeight = 6.0, baySpacing = 1.0,
        code = code, overrideRafterSection = rafter
    )

    @Test
    fun `warehouse moment utilization drops with higher grade steel`() {
        val st37 = SteelSectionType.ISection(160.0, 82.0, 7.4, 5.0, SteelGrade.ST37, "IPE 160")
        val s355 = SteelSectionType.ISection(160.0, 82.0, 7.4, 5.0, SteelGrade.S355, "IPE 160")

        val r1 = engine.designSteelWarehouse(warehouseInputs(DesignCode.ECP, st37))
        val r2 = engine.designSteelWarehouse(warehouseInputs(DesignCode.ECP, s355))

        assertTrue("utilization must be > 0", r1.mainFrame.utilizationMoment > 0.0)
        assertTrue(
            "Higher grade steel must reduce moment utilization (${r2.mainFrame.utilizationMoment} vs ${r1.mainFrame.utilizationMoment})",
            r2.mainFrame.utilizationMoment < r1.mainFrame.utilizationMoment
        )
    }
}
