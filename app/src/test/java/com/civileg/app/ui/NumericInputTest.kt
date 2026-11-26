package com.civileg.app.ui

import com.civileg.app.ui.compose.components.normalizeNumericInput
import org.junit.Assert.assertEquals
import org.junit.Test

class NumericInputTest {
    @Test fun arabicIndicDigitsBecomeWestern() = assertEquals("350", normalizeNumericInput("\u0663\u0665\u0660"))
    @Test fun extendedArabicIndicDigits() = assertEquals("12.5", normalizeNumericInput("\u06F1\u06F2.\u06F5"))
    @Test fun arabicDecimalSeparator() = assertEquals("2.75", normalizeNumericInput("2\u066B75"))
    @Test fun singleCommaIsDecimal() = assertEquals("1.5", normalizeNumericInput("1,5"))
    @Test fun commaWithDotUntouched() = assertEquals("1,000.5", normalizeNumericInput("1,000.5"))
    @Test fun invalidTextStaysInvalid() = assertEquals("abc", normalizeNumericInput("abc"))
}
