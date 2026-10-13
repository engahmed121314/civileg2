package com.civileg.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ItpTemplates] — standard ITP baseline per discipline.
 */
class ItpTemplatesTest {

    @Test
    fun defaultTemplate_isNotEmptyAndCoversAllDisciplines() {
        val itp = ItpTemplates.defaultTemplate()
        assertTrue(itp.isNotEmpty())
        assertTrue(itp.size >= 40)
        // Every key discipline is represented.
        val disciplines = itp.map { it.discipline }.toSet()
        assertTrue(disciplines.contains("CONCRETE"))
        assertTrue(disciplines.contains("REBAR"))
        assertTrue(disciplines.contains("STEEL"))
        assertTrue(disciplines.contains("ROADS"))
        assertTrue(disciplines.contains("EARTHWORKS"))
        assertTrue(disciplines.contains("PILING"))
        assertTrue(disciplines.contains("WATER_TANKS"))
        assertTrue(disciplines.contains("FIRE_PROTECTION"))
    }

    @Test
    fun everyRow_hasCompleteInformation() {
        ItpTemplates.defaultTemplate().forEach { row ->
            assertFalse("discipline blank", row.discipline.isBlank())
            assertFalse("activity blank", row.activity.isBlank())
            assertFalse("inspectionItem blank", row.inspectionItem.isBlank())
            assertFalse("referenceCode blank", row.referenceCode.isBlank())
            assertFalse("acceptanceCriteria blank", row.acceptanceCriteria.isBlank())
            assertFalse("frequency blank", row.frequency.isBlank())
            assertFalse("responsibleParty blank", row.responsibleParty.isBlank())
            assertEquals("pending", row.status)
        }
    }

    @Test
    fun templateFor_filtersByDisciplineCaseInsensitive() {
        val concrete = ItpTemplates.templateFor("concrete")
        assertTrue(concrete.isNotEmpty())
        assertTrue(concrete.all { it.discipline == "CONCRETE" })

        val unknown = ItpTemplates.templateFor("NOT-A-DISCIPLINE")
        assertTrue(unknown.isEmpty())
    }

    @Test
    fun coveredDisciplines_matchesTemplate() {
        val covered = ItpTemplates.coveredDisciplines()
        assertEquals(ItpTemplates.TEMPLATES.map { it.discipline }.toSet(), covered)
        assertEquals(2, ItpTemplates.templateFor("CONCRETE").size)
    }
}