package com.civileg.app.domain.calculations.deepbeam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.atan
import kotlin.math.tan

class DeepBeamDesignTest {

    private lateinit var design: DeepBeamDesign

    @Before
    fun setup() {
        design = DeepBeamDesign()
    }

    private fun input(
        code: DeepBeamCode = DeepBeamCode.ECP,
        span: Double = 2.0,
        width: Double = 300.0,
        depth: Double = 1000.0,
        fcu: Double = 30.0,
        fy: Double = 400.0,
        dead: Double = 10.0,
        live: Double = 10.0,
        point: Double = 0.0,
        providedTie: Double = 0.0,
        continuous: Boolean = false
    ) = DeepBeamInput(
        code = code,
        span = span,
        width = width,
        depth = depth,
        fcu = fcu,
        fy = fy,
        deadLoad = dead,
        liveLoad = live,
        pointLoad = point,
        providedTieArea = providedTie,
        isContinuous = continuous
    )

    // ── Classification ────────────────────────────────────────────────

    @Test
    fun `ECP simply supported beam is classified as deep`() {
        val result = design.design(input())
        assertTrue(result.isDeepBeam)
        assertEquals(2.0, result.spanToDepthRatio, 1e-9)
        assertEquals(2.5, result.spanToDepthLimit, 1e-9)
    }

    @Test
    fun `ACI simply supported limit is four and continuous limit is two point five`() {
        assertEquals(4.0, DeepBeamDesign.spanToDepthLimit(DeepBeamCode.ACI, false), 1e-9)
        assertEquals(2.5, DeepBeamDesign.spanToDepthLimit(DeepBeamCode.ACI, true), 1e-9)

        val deep = design.design(input(code = DeepBeamCode.ACI, span = 3.0, depth = 800.0))
        assertTrue(deep.isDeepBeam)

        val notDeep = design.design(
            input(code = DeepBeamCode.ACI, span = 3.0, depth = 800.0, continuous = true)
        )
        assertFalse(notDeep.isDeepBeam)
    }

    @Test
    fun `ECP continuous limit is two`() {
        assertEquals(2.0, DeepBeamDesign.spanToDepthLimit(DeepBeamCode.ECP, true), 1e-9)

        val simplySupported = design.design(input(span = 1.8, depth = 800.0))
        assertTrue(simplySupported.isDeepBeam)

        val continuous = design.design(input(span = 1.8, depth = 800.0, continuous = true))
        assertFalse(continuous.isDeepBeam)
    }

    @Test
    fun `companion classification helper matches design result`() {
        assertTrue(DeepBeamDesign.isDeepBeam(2.0, 1000.0))
        assertFalse(DeepBeamDesign.isDeepBeam(6.0, 400.0))
    }

    // ── Loads ─────────────────────────────────────────────────────────

    @Test
    fun `self weight and ECP factored loads are correct`() {
        val result = design.design(input())
        assertEquals(7.5, result.selfWeight, 1e-9)          // 0.3 × 1.0 × 25
        assertEquals(40.5, result.factoredLoad, 1e-9)       // 1.4 × 17.5 + 1.6 × 10
        assertEquals(20.25, result.factoredMoment, 1e-9)    // wu·L²/8
        assertEquals(40.5, result.factoredShear, 1e-9)      // wu·L/2
    }

    @Test
    fun `ACI uses a dead load factor of one point two`() {
        val result = design.design(input(code = DeepBeamCode.ACI))
        assertEquals(37.0, result.factoredLoad, 1e-9)       // 1.2 × 17.5 + 1.6 × 10
    }

    @Test
    fun `point load is factored and added to shear`() {
        val result = design.design(input(point = 100.0))
        assertEquals(120.5, result.factoredShear, 1e-9)     // 40.5 + 1.6 × 100 / 2
    }

    // ── Strut-and-tie ─────────────────────────────────────────────────

    @Test
    fun `strut angle and tie force follow the geometry`() {
        val result = design.design(input())
        val theta = atan(950.0 / 1000.0)                    // (h − cover − 10) / (L/2)
        assertEquals(Math.toDegrees(theta), result.strutAngleDeg, 1e-9)
        assertEquals(40.5 / tan(theta), result.tieForce, 1e-6)
        assertEquals(40.5 / kotlin.math.sin(theta), result.strutForce, 1e-6)
    }

    @Test
    fun `strut angle decreases as span increases`() {
        val shortSpan = design.design(input(span = 1.5))
        val longSpan = design.design(input(span = 2.5))
        assertTrue(shortSpan.strutAngleDeg > longSpan.strutAngleDeg)
    }

    @Test
    fun `minimum reinforcement is zero point two five percent of b h`() {
        val result = design.design(input())
        assertEquals(0.0025 * 300.0 * 1000.0, result.minimumReinforcement, 1e-9)
        assertEquals(result.minimumReinforcement, result.tieAreaProvided, 1e-9)
    }

    @Test
    fun `provided tie area is reported when larger than the minimum`() {
        val result = design.design(input(providedTie = 1200.0))
        assertEquals(1200.0, result.tieAreaProvided, 1e-9)
    }

    // ── Safety checks ─────────────────────────────────────────────────

    @Test
    fun `well proportioned deep beam is safe`() {
        val result = design.design(input())
        assertTrue(result.isSafe)
        assertTrue(result.checks.all { it.passed })
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `insufficient tie reinforcement makes the design unsafe`() {
        val result = design.design(input(providedTie = 50.0))
        assertFalse(result.isSafe)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `shallow beam is not a deep beam and is unsafe`() {
        val result = design.design(input(span = 6.0, depth = 400.0))
        assertFalse(result.isDeepBeam)
        assertFalse(result.isSafe)
        assertTrue(result.warnings.any { it.contains("not classified") })
    }

    // ── Validation ────────────────────────────────────────────────────

    @Test
    fun `negative span is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            design.design(input(span = -1.0))
        }
    }

    @Test
    fun `cover greater than depth is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            design.design(input().copy(clearCover = 1200.0))
        }
    }

    @Test
    fun `zero concrete strength is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            design.design(input(fcu = 0.0))
        }
    }
}
