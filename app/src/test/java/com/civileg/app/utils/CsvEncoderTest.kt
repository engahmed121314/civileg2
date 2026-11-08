package com.civileg.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvEncoderTest {
    @Test
    fun quotesEveryCellAndEscapesCsvSpecialCharacters() {
        assertEquals(
            "\"plain\",\"comma, value\",\"say \"\"hello\"\"\",\"line 1\nline 2\",\"\"",
            CsvEncoder.row(listOf("plain", "comma, value", "say \"hello\"", "line 1\nline 2", null))
        )
    }
}
