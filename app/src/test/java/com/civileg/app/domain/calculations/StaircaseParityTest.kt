package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.aci.ACIStaircase
import com.civileg.app.domain.calculations.base.StaircaseDesign
import com.civileg.app.domain.calculations.base.StaircaseInput
import com.civileg.app.domain.calculations.base.StaircaseResult
import com.civileg.app.domain.calculations.base.DomainStairType
import com.civileg.app.domain.calculations.ecp.ECPStaircase
import com.civileg.app.domain.calculations.sbc.SBCStaircase
import com.civileg.core.calculations.entities.DesignCode
import com.civileg.core.engineering.Aci318Params
import com.civileg.core.engineering.CheckStatus
import com.civileg.core.engineering.ConcreteCodeParams
import com.civileg.core.engineering.ConcreteMaterial
import com.civileg.core.engineering.Ecp203Params
import com.civileg.core.engineering.Sbc304Params
import com.civileg.core.engineering.SteelMaterial
import com.civileg.core.engineering.UnifiedStairDesign
import com.civileg.core.engineering.StairType as CoreStairType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden gate — the unified staircase engine must be numeric-exact with the
 * legacy app implementations it replaces (spec §3 single-producer rule).
 *
 * Same inputs → same geometry, loads, flexure/shear/deflection, and the
 * byte-identical reinforcement strings ("Ø12 @ 166 mm c/c") that flow into the
 * bar schedule and drawings. The app `designStaircase` (ECP/ACI/SBC) is the
 * reference; the core [UnifiedStairDesign] must agree field-for-field — down to
 * the stirrup decision string and every [com.civileg.app.domain.calculations.base.StairSafetyCheck]
 * row. No approximation is acceptable.
 */
class StaircaseParityTest {

    private data class Case(
        val label: String,
        val code: DesignCode,
        val appType: DomainStairType,
        val coreType: CoreStairType,
        val fcu: Double,
        val fy: Double,
        val span: Double,
        val totalRise: Double,
        val stairWidth: Double,
        val waistThickness: Double,
        val deadLoad: Double,
        val liveLoad: Double,
        val riserCount: Int = 0,
        val going: Double = 0.0
    )

    private fun appResult(c: Case): StaircaseResult {
        val staircase: StaircaseDesign = when (c.code) {
            DesignCode.ECP -> ECPStaircase()
            DesignCode.ACI -> ACIStaircase()
            DesignCode.SBC -> SBCStaircase()
        }
        return staircase.designStaircase(
            StaircaseInput(
                stairType = c.appType,
                span = c.span,
                totalRise = c.totalRise,
                stairWidth = c.stairWidth,
                waistThickness = c.waistThickness,
                fcu = c.fcu,
                fy = c.fy,
                deadLoad = c.deadLoad,
                liveLoad = c.liveLoad,
                riserCount = c.riserCount,
                going = c.going
            )
        )
    }

    private fun coreResult(c: Case): UnifiedStairDesign.Outcome {
        val params: ConcreteCodeParams = when (c.code) {
            DesignCode.ECP -> Ecp203Params
            DesignCode.ACI -> Aci318Params
            DesignCode.SBC -> Sbc304Params
        }
        return UnifiedStairDesign(
            params = params,
            concrete = ConcreteMaterial(fcuMpa = c.fcu),
            steel = SteelMaterial(yieldMpa = c.fy, ultimateMpa = 520.0)
        ).designStaircase(
            stairType = c.coreType,
            spanM = c.span,
            totalRiseM = c.totalRise,
            stairWidthM = c.stairWidth,
            waistThicknessMm = c.waistThickness,
            deadLoadKnm2 = c.deadLoad,
            liveLoadKnm2 = c.liveLoad,
            riserCount = c.riserCount,
            goingMmInput = c.going
        )
    }

    private fun assertParity(c: Case) {
        val app = appResult(c)
        val core = coreResult(c)

        // Geometry
        assertEquals("${c.label}: riser", app.riser, core.riser, 1e-9)
        assertEquals("${c.label}: going", app.going, core.going, 1e-9)
        assertEquals("${c.label}: n risers", app.numberOfRisers, core.numberOfRisers)
        assertEquals("${c.label}: n treads", app.numberOfTreads, core.numberOfTreads)
        assertEquals("${c.label}: slope", app.slopeAngle, core.slopeAngle, 1e-9)
        assertEquals("${c.label}: inclinedL", app.inclinedLength, core.inclinedLength, 1e-9)

        // Loads + analysis
        assertEquals("${c.label}: factLoad", app.factoredLoad, core.factoredLoad, 1e-9)
        assertEquals("${c.label}: horizLoad", app.horizontalLoad, core.horizontalLoad, 1e-9)
        assertEquals("${c.label}: moment", app.maxMoment, core.maxMoment, 1e-9)
        assertEquals("${c.label}: shear", app.maxShear, core.maxShear, 1e-9)
        assertEquals("${c.label}: reactionA", app.reactionA, core.reactionA, 1e-9)
        assertEquals("${c.label}: reactionB", app.reactionB, core.reactionB, 1e-9)

        // Flexure — byte-identical bar strings are the core parity contract
        assertEquals("${c.label}: mainRebar", app.mainRebar, core.mainRebar)
        assertEquals("${c.label}: mainArea", app.mainRebarArea, core.mainRebarArea, 1e-9)
        assertEquals("${c.label}: distRebar", app.distributionRebar, core.distributionRebar)
        assertEquals("${c.label}: distArea", app.distributionRebarArea, core.distributionRebarArea, 1e-9)
        assertEquals("${c.label}: d", app.effectiveDepth, core.effectiveDepth, 1e-9)
        assertEquals("${c.label}: rho", app.reinforcementRatio, core.reinforcementRatio, 1e-9)
        assertEquals("${c.label}: rhoMin", app.minSteelRatio, core.minSteelRatio, 1e-9)

        // Shear
        assertEquals("${c.label}: shearCap", app.shearCapacity, core.shearCapacity, 1e-9)
        assertEquals("${c.label}: stirrups", app.requiredStirrups, core.requiredStirrups)
        assertEquals("${c.label}: stirrupDia", app.stirrupDiameter, core.stirrupDiameter, 1e-9)
        assertEquals("${c.label}: stirrupSpacing", app.stirrupSpacing, core.stirrupSpacing, 1e-9)

        // Deflection
        assertEquals("${c.label}: deflection", app.deflection, core.deflection, 1e-9)
        assertEquals("${c.label}: allowDefl", app.allowableDeflection, core.allowableDeflection, 1e-9)
        assertEquals("${c.label}: deflOk", app.deflectionOk, core.deflectionOk)

        // Safety checks — every row, field-for-field
        assertEquals("${c.label}: n checks", app.safetyChecks.size, core.safetyChecks.size)
        app.safetyChecks.zip(core.safetyChecks).forEachIndexed { i, (a, b) ->
            assertEquals("${c.label}: check[$i].name", a.name, b.name)
            assertEquals("${c.label}: check[$i].value", a.value, b.value, 1e-9)
            assertEquals("${c.label}: check[$i].limit", a.limit, b.limit, 1e-9)
            assertEquals("${c.label}: check[$i].unit", a.unit, b.unit)
            assertEquals("${c.label}: check[$i].isSafe", a.isSafe, b.isSafe)
            assertEquals("${c.label}: check[$i].description", a.description, b.description)
        }

        // Element safety + sanity gate
        assertEquals("${c.label}: isSafe", app.isSafe, core.isSafe)
        if (!app.isSafe) {
            assertTrue("${c.label}: overall must FAIL near an unsafe check", core.overallStatus == CheckStatus.FAIL)
        } else {
            assertTrue("${c.label}: sanity must be clean", !core.sanity.hasError)
        }
    }

    // ────────────────────── ECP 203-2020 ──────────────────────

    @Test
    fun ecpBothGiven() = assertParity(
        Case("ecp-both", DesignCode.ECP, DomainStairType.STRAIGHT, CoreStairType.STRAIGHT,
            fcu = 25.0, fy = 360.0,
            span = 3.0, totalRise = 1.8, stairWidth = 1.2, waistThickness = 140.0,
            deadLoad = 6.0, liveLoad = 4.0, riserCount = 10, going = 280.0)
    )

    @Test
    fun ecpRiserOnly() = assertParity(
        Case("ecp-riser", DesignCode.ECP, DomainStairType.STRAIGHT, CoreStairType.STRAIGHT,
            fcu = 25.0, fy = 360.0,
            span = 3.6, totalRise = 1.8, stairWidth = 1.3, waistThickness = 130.0,
            deadLoad = 6.0, liveLoad = 4.0, riserCount = 10, going = 0.0)
    )

    @Test
    fun ecpGoingOnly() = assertParity(
        Case("ecp-going", DesignCode.ECP, DomainStairType.STRAIGHT, CoreStairType.STRAIGHT,
            fcu = 30.0, fy = 420.0,
            span = 3.0, totalRise = 1.65, stairWidth = 1.1, waistThickness = 120.0,
            deadLoad = 6.5, liveLoad = 4.0, riserCount = 0, going = 300.0)
    )

    @Test
    fun ecpFullyAuto() = assertParity(
        Case("ecp-auto", DesignCode.ECP, DomainStairType.STRAIGHT, CoreStairType.STRAIGHT,
            fcu = 25.0, fy = 360.0,
            span = 3.4, totalRise = 1.9, stairWidth = 1.2, waistThickness = 150.0,
            deadLoad = 6.0, liveLoad = 4.0, riserCount = 0, going = 0.0)
    )

    @Test
    fun ecpDogLeg() = assertParity(
        Case("ecp-dogleg", DesignCode.ECP, DomainStairType.DOG_LEG, CoreStairType.DOG_LEG,
            fcu = 25.0, fy = 360.0,
            span = 3.0, totalRise = 1.8, stairWidth = 1.2, waistThickness = 140.0,
            deadLoad = 6.0, liveLoad = 4.0, riserCount = 10, going = 280.0)
    )

    // ────────────────────── ACI 318-19 ──────────────────────

    @Test
    fun aciBothGiven() = assertParity(
        Case("aci-both", DesignCode.ACI, DomainStairType.STRAIGHT, CoreStairType.STRAIGHT,
            fcu = 30.0, fy = 420.0,
            span = 3.0, totalRise = 1.5, stairWidth = 1.0, waistThickness = 200.0,
            deadLoad = 6.0, liveLoad = 4.0, riserCount = 9, going = 279.0)
    )

    @Test
    fun aciRiserOnly() = assertParity(
        Case("aci-riser", DesignCode.ACI, DomainStairType.STRAIGHT, CoreStairType.STRAIGHT,
            fcu = 35.0, fy = 420.0,
            span = 3.4, totalRise = 1.6, stairWidth = 1.1, waistThickness = 200.0,
            deadLoad = 6.0, liveLoad = 4.0, riserCount = 9, going = 0.0)
    )

    @Test
    fun aciGoingOnly() = assertParity(
        Case("aci-going", DesignCode.ACI, DomainStairType.STRAIGHT, CoreStairType.STRAIGHT,
            fcu = 30.0, fy = 420.0,
            span = 3.1, totalRise = 1.52, stairWidth = 0.9, waistThickness = 180.0,
            deadLoad = 6.0, liveLoad = 4.0, riserCount = 0, going = 300.0)
    )

    @Test
    fun aciFullyAuto() = assertParity(
        Case("aci-auto", DesignCode.ACI, DomainStairType.STRAIGHT, CoreStairType.STRAIGHT,
            fcu = 30.0, fy = 420.0,
            span = 3.2, totalRise = 1.6, stairWidth = 1.0, waistThickness = 180.0,
            deadLoad = 6.0, liveLoad = 4.0, riserCount = 0, going = 0.0)
    )

    @Test
    fun aciDogLeg() = assertParity(
        Case("aci-dogleg", DesignCode.ACI, DomainStairType.DOG_LEG, CoreStairType.DOG_LEG,
            fcu = 30.0, fy = 420.0,
            span = 3.0, totalRise = 1.5, stairWidth = 1.0, waistThickness = 200.0,
            deadLoad = 6.0, liveLoad = 4.0, riserCount = 9, going = 279.0)
    )

    // ────────────────────── SBC 304-2018 ──────────────────────

    @Test
    fun sbcBothGiven() = assertParity(
        Case("sbc-both", DesignCode.SBC, DomainStairType.STRAIGHT, CoreStairType.STRAIGHT,
            fcu = 30.0, fy = 420.0,
            span = 3.0, totalRise = 1.5, stairWidth = 1.0, waistThickness = 200.0,
            deadLoad = 6.0, liveLoad = 4.0, riserCount = 9, going = 279.0)
    )

    @Test
    fun sbcRiserOnly() = assertParity(
        Case("sbc-riser", DesignCode.SBC, DomainStairType.STRAIGHT, CoreStairType.STRAIGHT,
            fcu = 25.0, fy = 420.0,
            span = 3.4, totalRise = 1.6, stairWidth = 1.1, waistThickness = 210.0,
            deadLoad = 6.0, liveLoad = 4.0, riserCount = 9, going = 0.0)
    )

    @Test
    fun sbcFullyAuto() = assertParity(
        Case("sbc-auto", DesignCode.SBC, DomainStairType.STRAIGHT, CoreStairType.STRAIGHT,
            fcu = 30.0, fy = 420.0,
            span = 3.2, totalRise = 1.6, stairWidth = 1.0, waistThickness = 180.0,
            deadLoad = 6.0, liveLoad = 4.0, riserCount = 0, going = 0.0)
    )

    @Test
    fun sbcDogLeg() = assertParity(
        Case("sbc-dogleg", DesignCode.SBC, DomainStairType.DOG_LEG, CoreStairType.DOG_LEG,
            fcu = 30.0, fy = 420.0,
            span = 3.0, totalRise = 1.5, stairWidth = 1.0, waistThickness = 200.0,
            deadLoad = 6.0, liveLoad = 4.0, riserCount = 9, going = 279.0)
    )
}