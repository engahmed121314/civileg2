package com.civileg.app.utils.tools

import org.junit.Assert.*
import org.junit.Test

private class MemPrefs : FloatPrefs {
    val m = HashMap<String, Float>()
    override fun getFloat(key: String): Float? = m[key]
    override fun putFloat(key: String, value: Float) { m[key] = value }
    override fun remove(key: String) { m.remove(key) }
}

class SpiritLevelMathTest {
    private val g = 9.80665

    @Test fun flatFaceUpIsZero() {
        val t = SpiritLevelMath.tilt(0.0, 0.0, g)!!
        assertEquals(0.0, t.xDeg, 1e-12); assertEquals(0.0, t.yDeg, 1e-12)
        assertTrue(SpiritLevelMath.isFaceUp(g)); assertFalse(SpiritLevelMath.isFaceUp(-g))
    }

    @Test fun rightEdgeHigherIsPositiveX() {
        // +x axis tilted up by 5 degrees: sensor reads +g*sin(5) on x.
        val a = Math.toRadians(5.0)
        val t = SpiritLevelMath.tilt(g * Math.sin(a), 0.0, g * Math.cos(a))!!
        assertEquals(5.0, t.xDeg, 1e-9); assertEquals(0.0, t.yDeg, 1e-9)
    }

    @Test fun topEdgeHigherIsPositiveY() {
        val a = Math.toRadians(-3.0)
        val t = SpiritLevelMath.tilt(0.0, g * Math.sin(a), g * Math.cos(a))!!
        assertEquals(-3.0, t.yDeg, 1e-9)
    }

    @Test fun unusableVectorsGiveNull() {
        assertNull(SpiritLevelMath.tilt(0.0, 0.0, 0.0))
        assertNull(SpiritLevelMath.tilt(Double.NaN, 0.0, g))
        assertNull(SpiritLevelMath.tilt(Double.POSITIVE_INFINITY, 0.0, g))
    }

    @Test fun rotationMapping() {
        assertEquals(1.0 to 2.0, SpiritLevelMath.toScreenAxes(1.0, 2.0, 0))
        assertEquals(-2.0 to 1.0, SpiritLevelMath.toScreenAxes(1.0, 2.0, 90))
        assertEquals(-1.0 to -2.0, SpiritLevelMath.toScreenAxes(1.0, 2.0, 180))
        assertEquals(2.0 to -1.0, SpiritLevelMath.toScreenAxes(1.0, 2.0, 270))
        assertEquals(1.0 to 2.0, SpiritLevelMath.toScreenAxes(1.0, 2.0, 360))
        try { SpiritLevelMath.toScreenAxes(1.0, 2.0, 45); fail() } catch (e: IllegalArgumentException) {}
    }

    @Test fun fourRotationsReturnToStart() {
        var p = 3.0 to -4.0
        repeat(4) { p = SpiritLevelMath.toScreenAxes(p.first, p.second, 90) }
        assertEquals(3.0 to -4.0, p)
    }

    @Test fun zeroOffsetSubtracts() {
        val t = SpiritLevelMath.withZero(SpiritLevelMath.Tilt(1.5, -0.5), 1.0, -1.0)
        assertEquals(0.5, t.xDeg, 1e-12); assertEquals(0.5, t.yDeg, 1e-12)
    }

    @Test fun levelOnlyWhenReadoutIsZeroPointZero() {
        assertTrue(SpiritLevelMath.displayedLevel(SpiritLevelMath.Tilt(0.04, -0.04)))
        assertFalse(SpiritLevelMath.displayedLevel(SpiritLevelMath.Tilt(0.05, 0.0)))
    }

    @Test fun bubbleOffsetSignAndPin() {
        assertEquals(0.5, SpiritLevelMath.bubbleOffset(5.0, 10.0), 1e-12)
        assertEquals(-1.0, SpiritLevelMath.bubbleOffset(-30.0, 10.0), 1e-12)
        try { SpiritLevelMath.bubbleOffset(1.0, 0.0); fail() } catch (e: IllegalArgumentException) {}
    }

    @Test fun smoothingStartsAtFirstSampleAndConverges() {
        assertEquals(4.0, SpiritLevelMath.smooth(null, 4.0, 0.2), 0.0)
        var v = 0.0; repeat(100) { v = SpiritLevelMath.smooth(v, 2.0, 0.2) }
        assertEquals(2.0, v, 1e-6)
    }

    @Test fun slopePercentOf45DegreesIs100() = assertEquals(100.0, SpiritLevelMath.slopePercent(45.0), 1e-9)
}

class RulerMathTest {
    @Test fun pxPerMmUsesDpiAndFactor() {
        assertEquals(254.0 / 25.4, RulerMath.pxPerMm(254f, 1.0), 1e-12)
        assertEquals(2 * 254.0 / 25.4, RulerMath.pxPerMm(254f, 2.0), 1e-12)
    }

    @Test fun invalidDpiOrFactorRejected() {
        for (bad in listOf(0f, -1f, Float.NaN)) try { RulerMath.pxPerMm(bad, 1.0); fail() } catch (e: IllegalArgumentException) {}
        try { RulerMath.pxPerMm(160f, 0.0); fail() } catch (e: IllegalArgumentException) {}
    }

    @Test fun recalibrateMatchesCardExample() {
        assertEquals(88.0 / 85.6, RulerMath.recalibrate(1.0, 85.6, 88.0), 1e-12)
        // Applying the factor makes the ruler show the card as exactly 85.6 mm.
        val dpi = 400f
        val before = RulerMath.pxPerMm(dpi, 1.0)
        val handlePx = 88.0 * before
        val f = RulerMath.recalibrate(1.0, 85.6, RulerMath.pxToMm(handlePx, before))
        assertEquals(85.6, RulerMath.pxToMm(handlePx, RulerMath.pxPerMm(dpi, f)), 1e-9)
    }

    @Test fun recalibrateDirectionAndSecondPass() {
        // Reads too long (88 for 85.6): factor must rise (more px per mm). Reads too short: factor must fall.
        assertTrue(RulerMath.recalibrate(1.0, 85.6, 88.0) > 1.0)
        assertTrue(RulerMath.recalibrate(1.0, 85.6, 80.0) < 1.0)
        // Already correct: unchanged.
        assertEquals(1.3, RulerMath.recalibrate(1.3, 85.6, 85.6), 1e-12)
        // Two passes from a non-unit factor still end at the real length.
        val dpi = 300f; val px = 330.0
        val f1 = RulerMath.recalibrate(1.2, 85.6, RulerMath.pxToMm(px, RulerMath.pxPerMm(dpi, 1.2)))
        assertEquals(85.6, RulerMath.pxToMm(px, RulerMath.pxPerMm(dpi, f1)), 1e-9)
        val f2 = RulerMath.recalibrate(f1, 85.6, RulerMath.pxToMm(px, RulerMath.pxPerMm(dpi, f1)))
        assertEquals(f1, f2, 1e-12)
    }

    @Test fun ticksKinds() {
        val t = RulerMath.ticks(20.5)
        assertEquals(21, t.size)
        assertEquals(RulerMath.TickKind.CM, t[0].kind); assertEquals(RulerMath.TickKind.CM, t[10].kind)
        assertEquals(RulerMath.TickKind.HALF_CM, t[5].kind); assertEquals(RulerMath.TickKind.HALF_CM, t[15].kind)
        assertEquals(RulerMath.TickKind.MM, t[1].kind)
        assertEquals(2, t.count { it.kind == RulerMath.TickKind.CM } - 1)
        assertTrue(RulerMath.ticks(-1.0).isEmpty()); assertTrue(RulerMath.ticks(Double.NaN).isEmpty())
    }

    @Test fun storedFactorValidation() {
        assertNull(RulerMath.validStoredFactor(0f)); assertNull(RulerMath.validStoredFactor(-1f))
        assertNull(RulerMath.validStoredFactor(Float.NaN)); assertEquals(1.25, RulerMath.validStoredFactor(1.25f)!!, 0.0)
    }
}

class ToolCalibrationStoreTest {
    @Test fun rulerPersistsAcrossStoreInstances() {
        val p = MemPrefs()
        ToolCalibrationStore(p).saveRuler(0.97, 420f)
        assertEquals(0.97, ToolCalibrationStore(p).rulerFactorFor(420f)!!, 1e-6)
    }

    @Test fun rulerIgnoredWhenDensityChanged() {
        val p = MemPrefs(); val s = ToolCalibrationStore(p)
        s.saveRuler(0.97, 420f)
        assertNull(s.rulerFactorFor(440f))
    }

    @Test fun rulerClearAndInvalidStored() {
        val p = MemPrefs(); val s = ToolCalibrationStore(p)
        assertNull(s.rulerFactorFor(420f))
        s.saveRuler(1.1, 420f); s.clearRuler(); assertNull(s.rulerFactorFor(420f))
        p.putFloat(ToolCalibrationStore.K_RULER_FACTOR, -2f); p.putFloat(ToolCalibrationStore.K_RULER_DPI, 420f)
        assertNull(s.rulerFactorFor(420f))
        try { s.saveRuler(0.0, 420f); fail() } catch (e: IllegalArgumentException) {}
    }

    @Test fun levelZeroPersistsAndClears() {
        val p = MemPrefs(); val s = ToolCalibrationStore(p)
        assertNull(s.levelZero())
        s.saveLevelZero(0.4, -0.2)
        val z = ToolCalibrationStore(p).levelZero()!!
        assertEquals(0.4, z.xDeg, 1e-6); assertEquals(-0.2, z.yDeg, 1e-6)
        s.clearLevelZero(); assertNull(s.levelZero())
        p.putFloat(ToolCalibrationStore.K_ZERO_X, Float.NaN); p.putFloat(ToolCalibrationStore.K_ZERO_Y, 0f)
        assertNull(s.levelZero())
    }
}
