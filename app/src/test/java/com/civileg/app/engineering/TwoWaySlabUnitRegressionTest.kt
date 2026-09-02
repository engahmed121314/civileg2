package com.civileg.app.engineering

import com.civileg.app.domain.calculations.ecp.ECPSlab
import com.civileg.app.domain.entities.EdgeCondition
import com.civileg.app.domain.entities.SlabSupportConditions
import com.civileg.core.calculations.entities.LoadCombination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE 00 STEP 2 — DEFECT A11 regression gate (PHASE00_AUDIT.md Addendum A).
 *
 * Contract (base/SlabDesign.kt): spans in MM, totalLoad kN/m², results kN.m/m & kN/m.
 * ACISlab/SBCSlab convert internally (lx = shortSpan/1000). ECPSlab divided mm² by 1000
 * instead of 1e6 -> moments AND shears inflated x1000 on any two-way path fed by ECPAdvancedSlab
 * (which passes shortSpan*1000).
 *
 * Hand-derived case TW-1 (post-fix expected):
 *   fcu=25, fy=360, h=250mm, lx=5m, ly=6m (ratio 1.2), 4 simply-supported edges, w=12 kN/m²
 *   coefficients (not-all-fixed, r<=1.2): positiveShort=0.041, positiveLong=0.032
 *   Mu_short = 0.041*12*5² = 12.30 kN.m/m ; V_short = 12*5/2 = 30 kN/m
 *   Mu_long  = 0.032*12*6² = 13.82 kN.m/m ; V_long  = 12*6/2 = 36 kN/m
 *   One-way module (d=224mm): K≈0.0098 -> As_req=177 mm²/m << min steel
 *   min post-A10 = max(0.25*sqrt(25)/360, 0.0013)*1000*224 = 777.78 mm²/m  -> MINIMUM GOVERNS
 *   Ø8 -> s = 50.27e3/777.78 = 64.6mm ; shear cap = 0.9798*224 = 219.5 kN/m >> 36 -> safe
 *   minThickness: 5000/25=200 <= 250 ok ; 6000/25=240 <= 250 ok -> isSafe both directions
 */
class TwoWaySlabUnitRegressionTest {

    private val simplySupportedAll = SlabSupportConditions(
        edgeA = EdgeCondition.SIMPLY_SUPPORTED,
        edgeB = EdgeCondition.SIMPLY_SUPPORTED,
        edgeC = EdgeCondition.SIMPLY_SUPPORTED,
        edgeD = EdgeCondition.SIMPLY_SUPPORTED
    )

    @Test
    fun twoway_A11_momentsAndShearUseMeters_notMillimeters() {
        val r = ECPSlab().designTwoWaySlab(
            fcu = 25.0, fy = 360.0, slabThickness = 250.0,
            shortSpan = 5000.0, longSpan = 6000.0,
            supportConditions = simplySupportedAll,
            totalLoad = 12.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        // Minimum steel governs both directions (flexure demand is far below minimum).
        // Pre-fix (x1000): Mu=12300 kN.m/m -> K>>K_bal -> fallback lever arm + huge As +
        // "over-reinforced" warning. Post-fix: clean minimum-steel result, no such warning.
        assertEquals(777.78, r.shortDirection.requiredReinforcement, 777.78 * 0.02)
        assertEquals(777.78, r.longDirection.requiredReinforcement, 777.78 * 0.02)
        assertEquals(8.0, r.shortDirection.barDiameter, 0.001)
        assertEquals(64.6, r.shortDirection.barSpacing, 1.0)
        assertTrue(r.shortDirection.warnings.none { it.contains("over-reinforced", ignoreCase = true) })
        assertTrue(r.isSafe)
    }

    @Test
    fun twoway_A11_utilizationReflectsTrueMoment_notInflated() {
        val r = ECPSlab().designTwoWaySlab(
            fcu = 25.0, fy = 360.0, slabThickness = 250.0,
            shortSpan = 5000.0, longSpan = 6000.0,
            supportConditions = simplySupportedAll,
            totalLoad = 12.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        // util post-A10: provided*fs*z/1e6 = 777.78*313.04*221.5/1e6 = 53.9 kN.m
        // util_short = 12.30/53.9 = 0.228
        assertEquals(0.228, r.shortDirection.utilizationRatio, 0.228 * 0.05)
    }
}
