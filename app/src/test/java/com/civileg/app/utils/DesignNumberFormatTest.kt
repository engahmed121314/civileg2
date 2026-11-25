package com.civileg.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DesignNumberFormatTest {
    @Test fun finiteValuesFormatNormally() {
        assertEquals("905.0 mm²", DesignNumberFormat.areaMm2(905.0))
        assertEquals("55%", DesignNumberFormat.percent(0.55))
        assertEquals("55.3%", DesignNumberFormat.percent(0.5534, 1))
    }
    @Test fun nonFiniteValuesNeverPrintAsNumbers() {
        for (v in listOf(Double.POSITIVE_INFINITY, Double.NaN, Double.NEGATIVE_INFINITY)) {
            val a = DesignNumberFormat.areaMm2(v); val p = DesignNumberFormat.percent(v)
            assertFalse(a.contains("Infinity") || a.contains("NaN"))
            assertFalse(p.contains("2147483647") || p.contains("Infinity") || p.contains("NaN"))
        }
    }
    @Test fun progressIsClamped() {
        assertEquals(1f, DesignNumberFormat.progress(Double.POSITIVE_INFINITY), 0f)
        assertEquals(1f, DesignNumberFormat.progress(2.5), 0f)
        assertEquals(0f, DesignNumberFormat.progress(-1.0), 0f)
    }
}
