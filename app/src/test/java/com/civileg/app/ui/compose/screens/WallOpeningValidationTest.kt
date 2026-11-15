package com.civileg.app.ui.compose.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for wall-opening input validation.
 *
 * Bug being guarded: the screen used `toDoubleOrNull() ?: default`, so wrong or
 * impossible geometry (opening wider than the wall, opening poking above the wall)
 * silently produced a design for values the user never entered.
 */
class WallOpeningValidationTest {

    private fun validate(
        wallHeightMm: String = "3000",
        wallThicknessMm: String = "250",
        openingWidthMm: String = "1000",
        openingHeightMm: String = "2000",
        openingFromBaseMm: String = "800",
        wallLengthMm: String = "5000",
        fcu: String = "25",
        fy: String = "360",
        loadAboveOpeningKNm: String = "20"
    ) = validateWallOpeningInputs(
        wallHeightMm = wallHeightMm,
        wallThicknessMm = wallThicknessMm,
        openingWidthMm = openingWidthMm,
        openingHeightMm = openingHeightMm,
        openingFromBaseMm = openingFromBaseMm,
        wallLengthMm = wallLengthMm,
        fcu = fcu,
        fy = fy,
        loadAboveOpeningKNm = loadAboveOpeningKNm,
        numberMessage = "number",
        positiveMessage = "positive",
        openingWidthMessage = "width",
        openingHeightMessage = "height"
    )

    @Test
    fun `default values are accepted`() {
        val result = validate()

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `zero offset from base is allowed`() {
        val result = validate(openingFromBaseMm = "0")

        assertTrue(result.isValid)
        assertNull(result.errors[Field.OPENING_FROM_BASE])
    }

    @Test
    fun `non numeric input is rejected`() {
        val result = validate(openingWidthMm = "abc")

        assertFalse(result.isValid)
        assertEquals("number", result.errors[Field.OPENING_WIDTH])
    }

    @Test
    fun `non positive concrete strength is rejected`() {
        val result = validate(fcu = "0")

        assertFalse(result.isValid)
        assertEquals("positive", result.errors[Field.FCU])
    }

    @Test
    fun `opening wider than the wall is rejected`() {
        val result = validate(openingWidthMm = "6000", wallLengthMm = "5000")

        assertFalse(result.isValid)
        assertEquals("width", result.errors[Field.OPENING_WIDTH])
    }

    @Test
    fun `opening that does not fit inside the wall height is rejected`() {
        val result = validate(openingHeightMm = "2500", openingFromBaseMm = "800", wallHeightMm = "3000")

        assertFalse(result.isValid)
        assertEquals("height", result.errors[Field.OPENING_HEIGHT])
    }

    @Test
    fun `negative load above the opening is rejected`() {
        val result = validate(loadAboveOpeningKNm = "-5")

        assertFalse(result.isValid)
        assertEquals("positive", result.errors[Field.LOAD_ABOVE])
    }
}
