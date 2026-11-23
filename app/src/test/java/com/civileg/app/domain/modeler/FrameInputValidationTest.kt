package com.civileg.app.domain.modeler

import com.civileg.app.domain.entities.MemberLoadType
import org.junit.Assert.*
import org.junit.Test

class FrameInputValidationTest {
    @Test fun malformedBlankNonFiniteAndOverflowAreNeverDefaults() {
        listOf("", " ", "bad", "1,5", "NaN", "Infinity", "-Infinity", "1e400").forEach { assertNull(it, FrameInputValidation.number(it)) }
        assertNull(FrameInputValidation.id("2147483648"))
        assertNull(FrameInputValidation.id("-1")); assertNull(FrameInputValidation.id("1.5"))
    }
    @Test fun coordinatesAndLoadsMayBeZeroOrNegativeAndArabicDigitsWork() {
        assertEquals(-10.0, FrameInputValidation.number("-10")!!, 0.0)
        assertEquals(0.0, FrameInputValidation.number("0")!!, 0.0)
        assertEquals(12.5, FrameInputValidation.number("١٢٫٥")!!, 0.0)
        assertEquals(-2.0, FrameInputValidation.number("−۲")!!, 0.0)
        assertEquals(12, FrameInputValidation.id("١٢")!!.toInt())
    }
    @Test fun sectionAndLoadCaseRulesDoNotInventEngineeringUpperBounds() {
        assertFalse(FrameInputValidation.valid("0", FrameInputValidation.Rule.POSITIVE))
        assertFalse(FrameInputValidation.valid("-2", FrameInputValidation.Rule.NON_NEGATIVE))
        assertTrue(FrameInputValidation.valid("0", FrameInputValidation.Rule.NON_NEGATIVE))
        assertTrue(FrameInputValidation.valid("1000000", FrameInputValidation.Rule.POSITIVE))
        assertFalse(FrameInputValidation.valid(" ", FrameInputValidation.Rule.REQUIRED))
    }
    @Test fun positionUsesActualLengthAndOnlyRelevantFields() {
        for(type in listOf(MemberLoadType.PointLoad, MemberLoadType.Moment)) {
            assertTrue(FrameInputValidation.positionValid(type,"0",4.0))
            assertTrue(FrameInputValidation.positionValid(type,"4",4.0))
            listOf("-0.1","4.1","NaN", "").forEach { assertFalse(FrameInputValidation.positionValid(type,it,4.0)) }
        }
        assertTrue(FrameInputValidation.positionValid(MemberLoadType.Trapezoidal,"2",4.0))
        assertFalse(FrameInputValidation.positionValid(MemberLoadType.Trapezoidal,"0",4.0))
        assertFalse(FrameInputValidation.positionValid(MemberLoadType.Trapezoidal,"2.1",4.0))
        assertTrue(FrameInputValidation.positionValid(MemberLoadType.UDL,"ignored",4.0))
        assertTrue(FrameInputValidation.positionValid(MemberLoadType.LinearVarying,"ignored",4.0))
    }
}
