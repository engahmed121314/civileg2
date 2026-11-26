package com.civileg.app.utils

import com.civileg.app.utils.QuickToolsEngine.QuantityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UnitConverterAuditTest {

    private fun conv(t: QuantityType, v: Double, f: String, to: String) = QuickToolsEngine.convertUnit(t, v, f, to)

    @Test fun everyUnitPairRoundTrips() {
        for (t in QuantityType.values()) {
            val units = QuickToolsEngine.unitsFor(t)
            assertTrue("no units for $t", units.size > 1)
            for (a in units) for (b in units) {
                val back = conv(t, conv(t, 123.456, a, b), b, a)
                assertEquals("$t $a->$b->$a", 123.456, back, 123.456 * 1e-12)
            }
        }
    }

    @Test fun knownValuesFromDefinitions() {
        assertEquals(304.8, conv(QuantityType.LENGTH, 1.0, "ft", "mm"), 1e-9)
        assertEquals(1609.344, conv(QuantityType.LENGTH, 1.0, "mi", "m"), 1e-9)
        assertEquals(0.45359237 * 9.80665 / 1000.0, conv(QuantityType.FORCE, 1.0, "lbf", "kN"), 1e-12)
        assertEquals(1000.0, conv(QuantityType.FORCE, 1.0, "MN", "kN"), 1e-9)
        assertEquals(6894.757293168, conv(QuantityType.STRESS, 1.0, "psi", "Pa"), 1e-6)
        // ksf = 1000 lbf/ft2, tsf = 2 x ksf
        assertEquals(2.0, conv(QuantityType.STRESS, 1.0, "tsf", "ksf"), 1e-12)
        assertEquals(0.45359237 * 9.80665 * 1000.0 / (0.3048 * 0.3048), conv(QuantityType.STRESS, 1.0, "ksf", "Pa"), 1e-6)
        assertEquals(0.028349523125, conv(QuantityType.MASS, 1.0, "oz", "kg"), 1e-15)
        // 1 mm2 in m2 must not vanish
        assertEquals(1e-6, conv(QuantityType.AREA, 1.0, "mm²", "m²"), 1e-18)
        assertEquals(9.80665, conv(QuantityType.MOMENT, 1.0, "kgf.m", "N.m"), 1e-9)
        assertEquals(9.80665, conv(QuantityType.MOMENT, 1.0, "tonf.m", "kN.m"), 1e-9)
        assertEquals(1000.0, conv(QuantityType.MASS, 1.0, "tonne", "kg"), 1e-9)
        assertEquals(0.0980665, conv(QuantityType.STRESS, 1.0, "kgf/cm²", "MPa"), 1e-12)
        // UK gallon is defined as exactly 4.54609 L, US gallon as 3.785411784 L
        assertEquals(4.54609, conv(QuantityType.VOLUME, 1.0, "UK gal", "L"), 1e-12)
        assertEquals(3.785411784, conv(QuantityType.VOLUME, 1.0, "US gal", "L"), 1e-12)
        assertTrue(QuickToolsEngine.unitsFor(QuantityType.MASS).none { it == "ton" })
    }

    @Test fun overflowGivesNonFiniteSoTheScreenCanFlagIt() {
        val r = conv(QuantityType.LENGTH, Double.MAX_VALUE, "km", "mm")
        assertTrue(!r.isFinite())
    }

    @Test fun unknownUnitIsAnErrorNotFactorOne() {
        try {
            conv(QuantityType.LENGTH, 1.0, "m", "gal")
            fail("expected error")
        } catch (_: IllegalStateException) {
        }
    }

    @Test fun formatKeepsSmallAndLargeValues() {
        assertEquals("0", UnitConversionFormat.format(0.0))
        assertEquals("0.000123", UnitConversionFormat.format(0.000123))
        assertEquals("3.2808399", UnitConversionFormat.format(1.0 / 0.3048))
        assertEquals("1.0000000e-06", UnitConversionFormat.format(1e-6))
        assertEquals("1.0000000e+09", UnitConversionFormat.format(1e9))
        assertEquals("-", UnitConversionFormat.format(Double.NaN))
    }
}
