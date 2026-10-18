package com.civileg.app.utils

import com.civileg.app.domain.entities.DesignCode
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADR-010 regression guard for the two CalculatorEngine entries that were still under-validated
 * when the coverage matrix was re-verified:
 * - `calculateStrapFooting` now validates every dimension/material it reads.
 * - `calculateSeismicLoads` now validates the zone, soil type, importance and reduction factor,
 *   which previously fell through to ZONE_5 / Soil C / a silent `coerceAtLeast(1.0)` on NaN or
 *   zero input instead of failing loudly.
 *
 * `preferredDiameter` is intentionally untested: `calculateStrapFooting` never reads it.
 */
class CalculatorEngineInputGuardTest {

    private val engine = CalculatorEngine(mockk(relaxed = true))

    // ─── calculateStrapFooting ───────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `strap footing rejects zero column width`() {
        engine.calculateStrapFooting(
            col1Load = 500.0, col2Load = 300.0, distance = 4000.0,
            col1W = 0.0, col1D = 500.0, col2W = 300.0, col2D = 500.0,
            soil = 150.0, fcu = 25.0, fy = 360.0,
            code = DesignCode.ECP, preferredDiameter = 16, strapWidth = 400.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `strap footing rejects zero fcu`() {
        engine.calculateStrapFooting(
            col1Load = 500.0, col2Load = 300.0, distance = 4000.0,
            col1W = 300.0, col1D = 500.0, col2W = 300.0, col2D = 500.0,
            soil = 150.0, fcu = 0.0, fy = 360.0,
            code = DesignCode.ECP, preferredDiameter = 16, strapWidth = 400.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `strap footing rejects negative fy`() {
        engine.calculateStrapFooting(
            col1Load = 500.0, col2Load = 300.0, distance = 4000.0,
            col1W = 300.0, col1D = 500.0, col2W = 300.0, col2D = 500.0,
            soil = 150.0, fcu = 25.0, fy = -360.0,
            code = DesignCode.ECP, preferredDiameter = 16, strapWidth = 400.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `strap footing rejects zero strap width`() {
        engine.calculateStrapFooting(
            col1Load = 500.0, col2Load = 300.0, distance = 4000.0,
            col1W = 300.0, col1D = 500.0, col2W = 300.0, col2D = 500.0,
            soil = 150.0, fcu = 25.0, fy = 360.0,
            code = DesignCode.ECP, preferredDiameter = 16, strapWidth = 0.0
        )
    }

    @Test
    fun `strap footing computes for valid inputs`() {
        val res = engine.calculateStrapFooting(
            col1Load = 500.0, col2Load = 300.0, distance = 4000.0,
            col1W = 300.0, col1D = 500.0, col2W = 300.0, col2D = 500.0,
            soil = 150.0, fcu = 25.0, fy = 360.0,
            code = DesignCode.ECP, preferredDiameter = 16, strapWidth = 400.0
        )
        assertTrue("footing volume must be > 0", res.concreteVolume > 0.0)
        assertTrue("footing widths must be > 0", res.footing1.width > 0.0 && res.footing2.width > 0.0)
    }

    // ─── calculateSeismicLoads ───────────────────────────────────────────

    private fun seismic(
        zone: Double = 0.20, importance: Double = 1.0, soilType: String = "C",
        height: Double = 15.0, totalWeight: Double = 5000.0, reductionFactor: Double = 5.0
    ) = CalculatorEngine.SeismicInput(zone, importance, soilType, height, totalWeight, "Frames", reductionFactor)

    @Test(expected = IllegalArgumentException::class)
    fun `seismic rejects NaN zone instead of silently mapping to zone 5`() {
        engine.calculateSeismicLoads(seismic(zone = Double.NaN))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `seismic rejects out of range zone`() {
        engine.calculateSeismicLoads(seismic(zone = 0.75))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `seismic rejects blank soil type`() {
        engine.calculateSeismicLoads(seismic(soilType = "   "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `seismic rejects zero importance factor`() {
        engine.calculateSeismicLoads(seismic(importance = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `seismic rejects zero reduction factor`() {
        engine.calculateSeismicLoads(seismic(reductionFactor = 0.0))
    }

    @Test
    fun `seismic computes for valid inputs`() {
        val res = engine.calculateSeismicLoads(seismic())
        assertTrue("base shear must be > 0", res.baseShear > 0.0)
        assertTrue("time period must be > 0", res.timePeriod > 0.0)
    }
}
