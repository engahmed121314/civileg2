package com.civileg.app.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DesignDatesTest {
    private fun design(at: Long) = Design(projectId = 1, type = DesignType.BEAM, name = "d", inputData = "{}",
        results = "{}", isSafe = true, codeUsed = "ECP", createdAt = Date(at))

    @Test fun `unknown marker is never formatted as a date`() {
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val unknown = design(UNKNOWN_CREATED_AT_MILLIS)
        assertFalse(unknown.hasKnownCreatedAt)
        assertEquals("Date unknown", unknown.createdAtLabel(fmt, "Date unknown"))
        val known = design(1700000000000L)
        assertTrue(known.hasKnownCreatedAt)
        assertEquals(fmt.format(known.createdAt), known.createdAtLabel(fmt, "Date unknown"))
    }
}
