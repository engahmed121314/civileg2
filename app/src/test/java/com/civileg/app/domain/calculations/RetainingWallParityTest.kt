package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.aci.ACIRetainingWall
import com.civileg.app.domain.calculations.base.RetainingWallDesign
import com.civileg.app.domain.calculations.base.RetainingWallInput as AppRetainingWallInput
import com.civileg.app.domain.calculations.base.RetainingWallResult
import com.civileg.app.domain.calculations.base.WallSafetyCheck
import com.civileg.app.domain.calculations.ecp.ECPRetainingWall
import com.civileg.app.domain.calculations.sbc.SBCRetainingWall
import com.civileg.core.calculations.entities.DesignCode
import com.civileg.core.engineering.Aci318Params
import com.civileg.core.engineering.CheckStatus
import com.civileg.core.engineering.ConcreteCodeParams
import com.civileg.core.engineering.ConcreteMaterial
import com.civileg.core.engineering.Ecp203Params
import com.civileg.core.engineering.Sbc304Params
import com.civileg.core.engineering.SteelMaterial
import com.civileg.core.engineering.UnifiedRetainingWallDesign
import com.civileg.core.engineering.RetainingWallInput as CoreRetainingWallInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden gate — the unified retaining-wall engine must be numeric-exact with the
 * legacy app implementations it replaces (spec §3 single-producer rule).
 *
 * Same inputs → same stability factors, bearing pressures, stem/toe/heel moments
 * and shears, the byte-identical rebar strings that flow into the bar schedule
 * and drawings (stem main + distribution + toe + heel), every
 * [WallSafetyCheck] row field-for-field, and the code notes.
 *
 * Two discriminating cases per family: a plain dry ground wall, plus a wall with
 * a water table at 1.5 m that exercises the layered Rankine model (submerged
 * rectangle + buoyant triangle + hydrostatic) and the hydrostatic code note.
 */
class RetainingWallParityTest {

    private data class Case(
        val label: String,
        val code: DesignCode,
        val fcu: Double,
        val fy: Double,
        val waterTableDepth: Double,
        val soilBearingCapacity: Double = 200.0
    )

    private val appInput: AppRetainingWallInput
        get() = AppRetainingWallInput(
            wallHeight = 4.0,
            stemBaseThickness = 0.4,
            stemTopThickness = 0.2,
            baseWidth = 3.0,
            baseThickness = 0.4,
            toeLength = 0.6,
            heelLength = 2.0,
            soilDensity = 18.0,
            frictionAngle = 30.0,
            surchargeLoad = 10.0,
            waterTableDepth = currentCase.waterTableDepth,
            fcu = currentCase.fcu,
            fy = currentCase.fy,
            baseFrictionCoeff = 0.5,
            soilBearingCapacity = currentCase.soilBearingCapacity
        )

    private lateinit var currentCase: Case

    private fun appResult(c: Case): RetainingWallResult {
        currentCase = c
        val wall: RetainingWallDesign = when (c.code) {
            DesignCode.ECP -> ECPRetainingWall()
            DesignCode.ACI -> ACIRetainingWall()
            DesignCode.SBC -> SBCRetainingWall()
        }
        return wall.designRetainingWall(appInput)
    }

    private fun coreResult(c: Case): UnifiedRetainingWallDesign.Outcome {
        currentCase = c
        val params: ConcreteCodeParams = when (c.code) {
            DesignCode.ECP -> Ecp203Params
            DesignCode.ACI -> Aci318Params
            DesignCode.SBC -> Sbc304Params
        }
        val coreInput = CoreRetainingWallInput(
            wallHeight = 4.0,
            stemBaseThickness = 0.4,
            stemTopThickness = 0.2,
            baseWidth = 3.0,
            baseThickness = 0.4,
            toeLength = 0.6,
            heelLength = 2.0,
            soilDensity = 18.0,
            frictionAngle = 30.0,
            surchargeLoad = 10.0,
            waterTableDepth = c.waterTableDepth,
            fcu = c.fcu,
            fy = c.fy,
            baseFrictionCoeff = 0.5,
            soilBearingCapacity = c.soilBearingCapacity
        )
        return UnifiedRetainingWallDesign(
            params = params,
            concrete = ConcreteMaterial(fcuMpa = c.fcu),
            steel = SteelMaterial(yieldMpa = c.fy, ultimateMpa = 520.0)
        ).designRetainingWall(coreInput)
    }

    private fun assertParity(c: Case) {
        val app = appResult(c)
        val core = coreResult(c)

        // Stability
        assertEquals("${c.label}: overturningFS", app.overturningFS, core.overturningFS, 1e-9)
        assertEquals("${c.label}: slidingFS", app.slidingFS, core.slidingFS, 1e-9)
        assertEquals("${c.label}: bearingFS", app.bearingFS, core.bearingFS, 1e-9)
        assertEquals("${c.label}: maxBearingPressure", app.maxBearingPressure, core.maxBearingPressure, 1e-9)
        assertEquals("${c.label}: minBearingPressure", app.minBearingPressure, core.minBearingPressure, 1e-9)

        // Stem
        assertEquals("${c.label}: stemMoment", app.stemMoment, core.stemMoment, 1e-9)
        assertEquals("${c.label}: stemShear", app.stemShear, core.stemShear, 1e-9)
        assertEquals("${c.label}: stemMainRebar", app.stemMainRebar, core.stemMainRebar)
        assertEquals("${c.label}: stemMainRebarArea", app.stemMainRebarArea, core.stemMainRebarArea, 1e-9)
        assertEquals("${c.label}: stemDistributionRebar", app.stemDistributionRebar, core.stemDistributionRebar)

        // Toe + heel
        assertEquals("${c.label}: toeMoment", app.toeMoment, core.toeMoment, 1e-9)
        assertEquals("${c.label}: toeShear", app.toeShear, core.toeShear, 1e-9)
        assertEquals("${c.label}: toeRebar", app.toeRebar, core.toeRebar)
        assertEquals("${c.label}: heelMoment", app.heelMoment, core.heelMoment, 1e-9)
        assertEquals("${c.label}: heelShear", app.heelShear, core.heelShear, 1e-9)
        assertEquals("${c.label}: heelRebar", app.heelRebar, core.heelRebar)

        // Safety checks — every row, field-for-field
        assertEquals("${c.label}: n checks", app.safetyChecks.size, core.safetyChecks.size)
        app.safetyChecks.zip(core.safetyChecks).forEachIndexed { i, (a, b) ->
            assertEquals("${c.label}: check[$i].name", a.name, b.name)
            assertEquals("${c.label}: check[$i].isSafe", a.isSafe, b.isSafe)
            assertEquals("${c.label}: check[$i].value", a.value, b.value, 1e-9)
            assertEquals("${c.label}: check[$i].limit", a.limit, b.limit, 1e-9)
            assertEquals("${c.label}: check[$i].description", a.description, b.description)
        }

        // Code notes — byte-identical
        assertEquals("${c.label}: codeNotes", app.codeNotes, core.codeNotes)

        // Element safety + sanity gate
        assertEquals("${c.label}: isSafe", app.isSafe, core.isSafe)
        if (app.safetyChecks.any { !it.isSafe }) {
            assertTrue(
                "${c.label}: overall must FAIL near an unsafe check",
                core.overallStatus == CheckStatus.FAIL
            )
        } else {
            assertTrue("${c.label}: sanity must be clean", !core.sanity.hasError)
        }
    }

    // ────────────────────── ECP 203-2020 ──────────────────────

    @Test
    fun ecpDryGroundWall() = assertParity(
        Case("ecp-dry", DesignCode.ECP, fcu = 25.0, fy = 360.0, waterTableDepth = 50.0)
    )

    @Test
    fun ecpWaterTable() = assertParity(
        Case("ecp-water", DesignCode.ECP, fcu = 25.0, fy = 360.0, waterTableDepth = 1.5)
    )

    // ────────────────────── ACI 318-19 ──────────────────────

    @Test
    fun aciDryGroundWall() = assertParity(
        Case("aci-dry", DesignCode.ACI, fcu = 30.0, fy = 420.0, waterTableDepth = 50.0)
    )

    @Test
    fun aciWaterTable() = assertParity(
        Case("aci-water", DesignCode.ACI, fcu = 30.0, fy = 420.0, waterTableDepth = 1.5)
    )

    // ────────────────────── SBC 304-2018 ──────────────────────

    @Test
    fun sbcDryGroundWall() = assertParity(
        Case("sbc-dry", DesignCode.SBC, fcu = 30.0, fy = 420.0, waterTableDepth = 50.0)
    )

    @Test
    fun sbcWaterTable() = assertParity(
        Case("sbc-water", DesignCode.SBC, fcu = 30.0, fy = 420.0, waterTableDepth = 1.5)
    )
}