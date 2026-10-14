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
            CalculatorEngine.DesignCode.EGYPTIAN,
            CalculatorEngine.DesignCode.ACI,
            CalculatorEngine.DesignCode.SAUDI
        )) {
            val r = engine.calculateSteelMember(section, SteelMemberType.COLUMN, inputs, code)
            assertTrue("$code axial capacity must be > 0", r.axialCapacity > 0.0)
            assertTrue("$code flexural capacity must be > 0", r.flexuralCapacity > 0.0)
            assertTrue("$code shear capacity must be > 0", r.shearCapacity > 0.0)
            assertTrue("$code axial finite", r.axialCapacity.isFinite())
            assertTrue("$code flexural finite", r.flexuralCapacity.isFinite())
        }
    }

    @Test
    fun `ECP buckling slenderness is sane (no x1000 length bug)`() {
        val r = engine.calculateSteelMember(
            section, SteelMemberType.COLUMN, inputs, CalculatorEngine.DesignCode.EGYPTIAN
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

        val ecp = engine.calculateSteelMember(section, SteelMemberType.COLUMN, inputs, CalculatorEngine.DesignCode.EGYPTIAN)
        val aci = engine.calculateSteelMember(section, SteelMemberType.COLUMN, inputs, CalculatorEngine.DesignCode.ACI)

        val axialDiffers = abs(ecp.axialCapacity - oldAxial) / oldAxial > 0.01 ||
            abs(aci.axialCapacity - oldAxial) / oldAxial > 0.01
        val flexDiffers = abs(ecp.flexuralCapacity - oldFlex) / oldFlex > 0.01 ||
            abs(aci.flexuralCapacity - oldFlex) / oldFlex > 0.01

        assertTrue("At least one code must diverge from the old stub axial capacity", axialDiffers)
        assertTrue("At least one code must diverge from the old stub flexural capacity", flexDiffers)
    }
}
