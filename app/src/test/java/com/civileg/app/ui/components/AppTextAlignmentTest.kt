package com.civileg.app.ui.components

import androidx.compose.ui.text.style.TextAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppTextAlignmentTest {
    @Test fun arabicSentenceAlignsRight() = assertEquals(TextAlign.Right, alignmentFromText("حمل ميت (kN/m²)"))
    @Test fun englishLabelAlignsLeft() = assertEquals(TextAlign.Left, alignmentFromText("Egyptian Code"))
    @Test fun leadingDigitsAndPunctuationAreSkipped() = assertEquals(TextAlign.Right, alignmentFromText("20.0 م"))
    @Test fun englishAfterNumberAlignsLeft() = assertEquals(TextAlign.Left, alignmentFromText("(1) Total steel"))
    @Test fun digitsOnlyKeepsDefault() = assertNull(alignmentFromText("12.5"))
    @Test fun emptyKeepsDefault() = assertNull(alignmentFromText(""))
}
