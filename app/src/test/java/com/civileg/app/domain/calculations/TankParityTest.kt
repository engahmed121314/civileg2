package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.aci.ACITank
import com.civileg.app.domain.calculations.base.TankDesign
import com.civileg.app.domain.calculations.base.TankResult
import com.civileg.app.domain.calculations.base.TankType
import com.civileg.app.domain.calculations.ecp.ECPTank
import com.civileg.app.domain.calculations.sbc.SBCTank
import com.civileg.core.calculations.entities.DesignCode
import com.civileg.core.calculations.entities.ReinforcementResult
import com.civileg.core.engineering.Aci318Params
import com.civileg.core.engineering.CheckStatus
import com.civileg.core.engineering.ConcreteCodeParams
import com.civileg.core.engineering.ConcreteMaterial
import com.civileg.core.engineering.Ecp203Params
import com.civileg.core.engineering.Sbc304Params
import com.civileg.core.engineering.SteelMaterial
import com.civileg.core.engineering.UnifiedTankDesign
import com.civileg.core.engineering.TankType as CoreTankType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden gate — the unified tank engine must be numeric-exact with the legacy
 * app implementations it replaces (spec §3 single-producer rule).
 *
 * Same inputs → same thicknesses, capacity, quantities, cost, uplift, every
 * [com.civileg.app.domain.calculations.base.TankSafetyCheck] row and both
 * [ReinforcementResult]s (wall vertical/horizontal + base) — down to the
 * byte-identical bar strings that flow into the bar schedule and drawings.
 *
 * Includes the A3 punching-perimeter fix and (as discriminating cases) an
 * underground buoyant tank whose uplift row FAILS — locking the legacy ECP
 * isSafe-before-uplift ordering vs the ACI/SBC isSafe-after-uplift ordering.
 */
class TankParityTest {

    private data class Case(
        val label: String,
        val code: DesignCode,
        val appType: TankType,
        val coreType: CoreTankType,
        val fcu: Double,
        val fy: Double,
        val lengthMm: Double,
        val widthMm: Double,
        val heightMm: Double,
        val waterDepthMm: Double,
        val groundWaterDepthMm: Double = Double.POSITIVE_INFINITY
    )

    private fun appResult(c: Case): TankResult {
        val tank: TankDesign = when (c.code) {
            DesignCode.ECP -> ECPTank()
            DesignCode.ACI -> ACITank()
            DesignCode.SBC -> SBCTank()
        }
        return tank.calculateTank(
            length = c.lengthMm,
            width = c.widthMm,
            height = c.heightMm,
            waterDepth = c.waterDepthMm,
            fcu = c.fcu,
            fy = c.fy,
            type = c.appType,
            groundWaterDepth = c.groundWaterDepthMm
        )
    }

    private fun coreResult(c: Case): UnifiedTankDesign.Outcome {
        val params: ConcreteCodeParams = when (c.code) {
            DesignCode.ECP -> Ecp203Params
            DesignCode.ACI -> Aci318Params
            DesignCode.SBC -> Sbc304Params
        }
        return UnifiedTankDesign(
            params = params,
            concrete = ConcreteMaterial(fcuMpa = c.fcu),
            steel = SteelMaterial(yieldMpa = c.fy, ultimateMpa = 520.0)
        ).designTank(
            lengthMm = c.lengthMm,
            widthMm = c.widthMm,
            heightMm = c.heightMm,
            waterDepthMm = c.waterDepthMm,
            type = c.coreType,
            groundWaterDepthMm = c.groundWaterDepthMm
        )
    }

    private fun assertReinforcement(label: String, app: ReinforcementResult, core: ReinforcementResult) {
        assertEquals("$label: astRequired", app.astRequired, core.astRequired, 1e-9)
        assertEquals("$label: astProvided", app.astProvided, core.astProvided, 1e-9)
        assertEquals("$label: barDiameter", app.barDiameter, core.barDiameter, 1e-9)
        assertEquals("$label: numberOfBars", app.numberOfBars, core.numberOfBars)
        assertEquals("$label: tiesDiameter", app.tiesDiameter, core.tiesDiameter, 1e-9)
        assertEquals("$label: tiesSpacing", app.tiesSpacing, core.tiesSpacing, 1e-9)
        assertEquals("$label: isSafe", app.isSafe, core.isSafe)
        assertEquals("$label: utilizationRatio", app.utilizationRatio, core.utilizationRatio, 1e-9)
        assertEquals("$label: spacing", app.spacing, core.spacing, 1e-9)
        assertEquals("$label: barString", app.barString, core.barString)
        assertEquals("$label: warnings", app.warnings, core.warnings)
        assertEquals("$label: codeNotes", app.codeNotes, core.codeNotes)
        assertEquals("$label: description", app.description, core.description)
    }

    private fun assertParity(c: Case) {
        val app = appResult(c)
        val core = coreResult(c)

        // Geometry
        assertEquals("${c.label}: wallThickness", app.wallThickness, core.wallThickness, 1e-9)
        assertEquals("${c.label}: baseThickness", app.baseThickness, core.baseThickness, 1e-9)

        // Reinforcement (bar-to-bar, byte-identical strings)
        assertReinforcement("${c.label}: wall", app.wallReinforcement, core.wallReinforcement)
        assertReinforcement("${c.label}: base", app.baseReinforcement, core.baseReinforcement)

        // Quantities + cost
        assertEquals("${c.label}: capacityM3", app.capacityM3, core.capacityM3, 1e-9)
        assertEquals("${c.label}: concreteVolume", app.concreteVolume, core.concreteVolume, 1e-9)
        assertEquals("${c.label}: steelWeight", app.steelWeight, core.steelWeight, 1e-9)
        assertEquals("${c.label}: cost", app.cost, core.cost, 1e-9)

        // Loads + export moments
        assertEquals("${c.label}: pressure", app.pressure, core.pressure, 1e-9)
        assertEquals("${c.label}: maxMomentWall", app.maxMomentWall, core.maxMomentWall, 1e-9)
        assertEquals("${c.label}: maxMomentBase", app.maxMomentBase, core.maxMomentBase, 1e-9)
        assertEquals("${c.label}: maxShearWall", app.maxShearWall, core.maxShearWall, 1e-9)
        assertEquals("${c.label}: upliftFoS", app.factorOfSafetyUplift, core.factorOfSafetyUplift, 1e-9)

        // Text channels
        assertEquals("${c.label}: structuralSystem", app.structuralSystem, core.structuralSystem)
        assertEquals("${c.label}: recommendations", app.recommendations, core.recommendations)
        assertEquals("${c.label}: warnings", app.warnings, core.warnings)

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
    fun ecpRectangularGround() = assertParity(
        Case("ecp-rect-ground", DesignCode.ECP, TankType.RECTANGULAR_GROUND, CoreTankType.RECTANGULAR_GROUND,
            fcu = 25.0, fy = 360.0,
            lengthMm = 6000.0, widthMm = 4000.0, heightMm = 3000.0, waterDepthMm = 1500.0)
    )

    @Test
    fun ecpCircularGround() = assertParity(
        Case("ecp-circ-ground", DesignCode.ECP, TankType.CIRCULAR_GROUND, CoreTankType.CIRCULAR_GROUND,
            fcu = 30.0, fy = 360.0,
            lengthMm = 6000.0, widthMm = 6000.0, heightMm = 3000.0, waterDepthMm = 1500.0)
    )

    @Test
    fun ecpUndergroundBuoyant() = assertParity(
        Case("ecp-underground", DesignCode.ECP, TankType.RECTANGULAR_UNDERGROUND, CoreTankType.RECTANGULAR_UNDERGROUND,
            fcu = 25.0, fy = 360.0,
            lengthMm = 8000.0, widthMm = 6000.0, heightMm = 3000.0, waterDepthMm = 1500.0,
            groundWaterDepthMm = 0.0)
    )

    @Test
    fun ecpElevatedRectangular() = assertParity(
        Case("ecp-elevated", DesignCode.ECP, TankType.RECTANGULAR_ELEVATED, CoreTankType.RECTANGULAR_ELEVATED,
            fcu = 30.0, fy = 420.0,
            lengthMm = 4000.0, widthMm = 4000.0, heightMm = 3000.0, waterDepthMm = 2000.0)
    )

    // ────────────────────── ACI 350-06 / 318-19 ──────────────────────

    @Test
    fun aciRectangularGround() = assertParity(
        Case("aci-rect-ground", DesignCode.ACI, TankType.RECTANGULAR_GROUND, CoreTankType.RECTANGULAR_GROUND,
            fcu = 30.0, fy = 420.0,
            lengthMm = 5000.0, widthMm = 5000.0, heightMm = 3000.0, waterDepthMm = 2000.0)
    )

    @Test
    fun aciCircularGround() = assertParity(
        Case("aci-circ-ground", DesignCode.ACI, TankType.CIRCULAR_GROUND, CoreTankType.CIRCULAR_GROUND,
            fcu = 30.0, fy = 420.0,
            lengthMm = 6000.0, widthMm = 6000.0, heightMm = 3000.0, waterDepthMm = 2000.0)
    )

    @Test
    fun aciUndergroundBuoyant() = assertParity(
        Case("aci-underground", DesignCode.ACI, TankType.RECTANGULAR_UNDERGROUND, CoreTankType.RECTANGULAR_UNDERGROUND,
            fcu = 30.0, fy = 420.0,
            lengthMm = 8000.0, widthMm = 6000.0, heightMm = 3000.0, waterDepthMm = 1500.0,
            groundWaterDepthMm = 0.0)
    )

    @Test
    fun aciElevatedRectangular() = assertParity(
        Case("aci-elevated", DesignCode.ACI, TankType.RECTANGULAR_ELEVATED, CoreTankType.RECTANGULAR_ELEVATED,
            fcu = 35.0, fy = 420.0,
            lengthMm = 4000.0, widthMm = 4000.0, heightMm = 3000.0, waterDepthMm = 2000.0)
    )

    // ────────────────────── SBC 304-2018 ──────────────────────

    @Test
    fun sbcRectangularGround() = assertParity(
        Case("sbc-rect-ground", DesignCode.SBC, TankType.RECTANGULAR_GROUND, CoreTankType.RECTANGULAR_GROUND,
            fcu = 30.0, fy = 420.0,
            lengthMm = 5000.0, widthMm = 5000.0, heightMm = 3000.0, waterDepthMm = 2000.0)
    )

    @Test
    fun sbcCircularGround() = assertParity(
        Case("sbc-circ-ground", DesignCode.SBC, TankType.CIRCULAR_GROUND, CoreTankType.CIRCULAR_GROUND,
            fcu = 30.0, fy = 420.0,
            lengthMm = 6000.0, widthMm = 6000.0, heightMm = 3000.0, waterDepthMm = 2000.0)
    )

    @Test
    fun sbcUndergroundBuoyant() = assertParity(
        Case("sbc-underground", DesignCode.SBC, TankType.RECTANGULAR_UNDERGROUND, CoreTankType.RECTANGULAR_UNDERGROUND,
            fcu = 25.0, fy = 420.0,
            lengthMm = 8000.0, widthMm = 6000.0, heightMm = 3000.0, waterDepthMm = 1500.0,
            groundWaterDepthMm = 0.0)
    )

    @Test
    fun sbcElevatedRectangular() = assertParity(
        Case("sbc-elevated", DesignCode.SBC, TankType.RECTANGULAR_ELEVATED, CoreTankType.RECTANGULAR_ELEVATED,
            fcu = 30.0, fy = 420.0,
            lengthMm = 4000.0, widthMm = 4000.0, heightMm = 3000.0, waterDepthMm = 2000.0)
    )
}