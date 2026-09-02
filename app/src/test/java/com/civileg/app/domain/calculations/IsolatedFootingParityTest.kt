package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.aci.ACIFooting
import com.civileg.app.domain.calculations.base.BoundaryConstraints
import com.civileg.app.domain.calculations.base.FootingDesign
import com.civileg.app.domain.calculations.base.FootingDesignResult
import com.civileg.app.domain.calculations.ecp.ECPFooting
import com.civileg.core.calculations.entities.DesignCode
import com.civileg.core.calculations.entities.LoadCombination
import com.civileg.core.calculations.entities.ReinforcementResult
import com.civileg.core.engineering.CheckStatus
import com.civileg.core.engineering.Aci318Params
import com.civileg.core.engineering.ConcreteMaterial
import com.civileg.core.engineering.Ecp203Params
import com.civileg.core.engineering.FootingConstraints
import com.civileg.core.engineering.FootingDirection
import com.civileg.core.engineering.SteelMaterial
import com.civileg.core.engineering.UnifiedFootingDesign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden gate — the unified footing engine must be numeric-exact with the
 * legacy app implementations it replaces (spec §3 single-producer rule).
 *
 * Same inputs → same dimensions, pressures, punching shear, and reinforcement.
 * The app `designIsolatedFooting` is the reference; the core engine must agree
 * field-for-field. No approximation is acceptable here: the drawing/bill of
 * quantities downstream consume these exact numbers.
 */
class IsolatedFootingParityTest {

    private data class Case(
        val label: String,
        val code: DesignCode,
        val fcu: Double,
        val fy: Double,
        val columnWidth: Double,
        val columnDepth: Double,
        val axialLoad: Double,
        val momentX: Double,
        val momentY: Double,
        val sbc: Double,
        val depth: Double,
        val constraints: BoundaryConstraints = BoundaryConstraints()
    )

    private fun appResult(c: Case): FootingDesignResult {
        val footing: FootingDesign = when (c.code) {
            DesignCode.ECP -> ECPFooting()
            DesignCode.ACI -> ACIFooting()
            else -> error("no app implementation for ${c.code}")
        }
        return footing.designIsolatedFooting(
            fcu = c.fcu, fy = c.fy,
            columnWidth = c.columnWidth, columnDepth = c.columnDepth,
            axialLoad = c.axialLoad, momentX = c.momentX, momentY = c.momentY,
            soilBearingCapacity = c.sbc, footingDepth = c.depth,
            loadCombination = LoadCombination.DEAD_LIVE,
            constraints = c.constraints
        )
    }

    private fun coreResult(c: Case): UnifiedFootingDesign.Outcome {
        val params = when (c.code) {
            DesignCode.ECP -> Ecp203Params
            DesignCode.ACI -> Aci318Params
            else -> error("no params for ${c.code}")
        }
        return UnifiedFootingDesign(
            params = params,
            concrete = ConcreteMaterial(fcuMpa = c.fcu),
            steel = SteelMaterial(yieldMpa = c.fy, ultimateMpa = 520.0)
        ).designIsolatedFooting(
            columnWidth = c.columnWidth, columnDepth = c.columnDepth,
            axialLoad = c.axialLoad, momentX = c.momentX, momentY = c.momentY,
            soilBearingCapacity = c.sbc, footingDepth = c.depth,
            loadCombination = LoadCombination.DEAD_LIVE,
            constraints = c.constraints.toCore()
        )
    }

    private fun BoundaryConstraints.toCore() = FootingConstraints(
        maxLeft = maxLeft, maxRight = maxRight, maxTop = maxTop, maxBottom = maxBottom,
        isCornerColumn = isCornerColumn, isEdgeColumn = isEdgeColumn
    )

    private fun assertParity(c: Case) {
        val app = appResult(c)
        val core = coreResult(c)

        // Geometry + geotech
        assertEquals("${c.label}: B", app.requiredWidth, core.requiredWidth, 1e-9)
        assertEquals("${c.label}: L", app.requiredLength, core.requiredLength, 1e-9)
        assertEquals("${c.label}: h", app.requiredThickness, core.requiredThickness, 1e-9)
        assertEquals("${c.label}: q_avg", app.soilPressure, core.soilPressure, 1e-9)
        assertEquals("${c.label}: q_max", app.maxSoilPressure, core.maxSoilPressure, 1e-9)

        // Punching
        assertEquals("${c.label}: punch applied", app.punchingShearCheck.appliedShear, core.punching.appliedShear, 1e-9)
        assertEquals("${c.label}: punch capacity", app.punchingShearCheck.shearCapacity, core.punching.shearCapacity, 1e-9)
        assertEquals("${c.label}: punch util", app.punchingShearCheck.utilizationRatio, core.punching.utilizationRatio, 1e-9)
        assertEquals("${c.label}: punch safe", app.punchingShearCheck.isSafe, core.punching.isSafe)
        assertEquals("${c.label}: punch b0", app.punchingShearCheck.criticalPerimeter, core.punching.criticalPerimeter, 1e-9)

        // Reinforcement — app keeps governing direction, core keeps both
        val coreMain = if (core.mainDirection == FootingDirection.SHORT) core.shortDir else core.longDir
        val appReinf = app.reinforcement
        assertEquals("${c.label}: As req", appReinf.astRequired, coreMain.astRequired, 1e-9)
        assertEquals("${c.label}: As prov", appReinf.astProvided, coreMain.astProvided, 1e-9)
        assertEquals("${c.label}: bar dia", appReinf.barDiameter, coreMain.barDiameter, 1e-9)
        assertEquals("${c.label}: n bars", appReinf.numberOfBars, coreMain.barsPerMeter)
        assertEquals("${c.label}: spacing", appReinf.spacing, coreMain.spacingMm, 1e-9)
        assertEquals("${c.label}: util", appReinf.utilizationRatio, coreMain.utilization, 1e-9)
        assertEquals("${c.label}: reinf safe", appReinf.isSafe, coreMain.isSafe)

        // Overall safety gate (element-level, app contract: flexure is surfaced
        // through the direction statuses rather than element isSafe)
        assertEquals("${c.label}: isSafe", app.isSafe, core.isSafe)

        // Self-consistency of the drawing gate: a direction that is over-utilised
        // (or a capacity check > 1.0) must fail the overall status; otherwise the
        // element must come out clean — never a silent PASS on a failing check.
        val flexureUnsafe = !core.shortDir.isSafe || !core.longDir.isSafe
        val utilUnsafe = core.punching.utilizationRatio > 1.0 ||
            core.oneWayX.utilizationRatio > 1.0 || core.oneWayY.utilizationRatio > 1.0
        if (flexureUnsafe || utilUnsafe) {
            assertTrue("${c.label}: overall must FAIL near an unsafe check", core.overallStatus == CheckStatus.FAIL)
        } else {
            assertTrue("${c.label}: sanity must be clean", !core.sanity.hasError)
        }
    }

    // ────────────────────── ECP 203-2020 ──────────────────────

    @Test
    fun ecpSquareColumnCenteredLoad() = assertParity(
        Case("ecp-square", DesignCode.ECP, fcu = 25.0, fy = 360.0,
            columnWidth = 600.0, columnDepth = 600.0,
            axialLoad = 1200.0, momentX = 0.0, momentY = 0.0,
            sbc = 150.0, depth = 500.0)
    )

    @Test
    fun ecpEccentricMoments() = assertParity(
        Case("ecp-ecc", DesignCode.ECP, fcu = 25.0, fy = 360.0,
            columnWidth = 500.0, columnDepth = 700.0,
            axialLoad = 1500.0, momentX = 60.0, momentY = 40.0,
            sbc = 180.0, depth = 600.0)
    )

    @Test
    fun ecpCornerBoundaryColumn() = assertParity(
        Case("ecp-corner", DesignCode.ECP, fcu = 30.0, fy = 420.0,
            columnWidth = 600.0, columnDepth = 600.0,
            axialLoad = 1800.0, momentX = 0.0, momentY = 0.0,
            sbc = 200.0, depth = 650.0,
            constraints = BoundaryConstraints(maxLeft = 1200.0, maxTop = 1500.0, isCornerColumn = true))
    )

    @Test
    fun ecpEdgeBoundaryColumnExplicitLimit() = assertParity(
        Case("ecp-edge", DesignCode.ECP, fcu = 30.0, fy = 420.0,
            columnWidth = 500.0, columnDepth = 800.0,
            axialLoad = 1600.0, momentX = 30.0, momentY = 20.0,
            sbc = 175.0, depth = 550.0,
            constraints = BoundaryConstraints(maxRight = 1100.0, isEdgeColumn = true))
    )

    @Test
    fun ecpEdgeBoundaryColumnImplicitDefault() = assertParity(
        Case("ecp-edge-default", DesignCode.ECP, fcu = 25.0, fy = 360.0,
            columnWidth = 500.0, columnDepth = 500.0,
            axialLoad = 1400.0, momentX = 0.0, momentY = 0.0,
            sbc = 160.0, depth = 500.0,
            constraints = BoundaryConstraints(isEdgeColumn = true))
    )

    // ────────────────────── ACI 318-19 ──────────────────────

    @Test
    fun aciSquareColumnCenteredLoad() = assertParity(
        Case("aci-square", DesignCode.ACI, fcu = 25.0, fy = 420.0,
            columnWidth = 600.0, columnDepth = 600.0,
            axialLoad = 1200.0, momentX = 0.0, momentY = 0.0,
            sbc = 150.0, depth = 500.0)
    )

    @Test
    fun aciEccentricMoments() = assertParity(
        Case("aci-ecc", DesignCode.ACI, fcu = 30.0, fy = 420.0,
            columnWidth = 500.0, columnDepth = 700.0,
            axialLoad = 1500.0, momentX = 60.0, momentY = 40.0,
            sbc = 180.0, depth = 600.0)
    )

    @Test
    fun aciCornerBoundaryColumn() = assertParity(
        Case("aci-corner", DesignCode.ACI, fcu = 30.0, fy = 420.0,
            columnWidth = 600.0, columnDepth = 600.0,
            axialLoad = 1800.0, momentX = 0.0, momentY = 0.0,
            sbc = 200.0, depth = 650.0,
            constraints = BoundaryConstraints(maxLeft = 1200.0, maxTop = 1500.0, isCornerColumn = true))
    )

    @Test
    fun aciEdgeBoundaryColumnExplicitLimit() = assertParity(
        Case("aci-edge", DesignCode.ACI, fcu = 30.0, fy = 420.0,
            columnWidth = 500.0, columnDepth = 800.0,
            axialLoad = 1600.0, momentX = 30.0, momentY = 20.0,
            sbc = 175.0, depth = 550.0,
            constraints = BoundaryConstraints(maxRight = 1100.0, isEdgeColumn = true))
    )

    @Test
    fun aciEdgeBoundaryColumnNoLimitIsUnconstrained() = assertParity(
        Case("aci-edge-unconstrained", DesignCode.ACI, fcu = 25.0, fy = 420.0,
            columnWidth = 500.0, columnDepth = 500.0,
            axialLoad = 1400.0, momentX = 0.0, momentY = 0.0,
            sbc = 160.0, depth = 500.0,
            constraints = BoundaryConstraints(isEdgeColumn = true))
    )
}