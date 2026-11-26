package com.civileg.app.viewmodel

import com.civileg.app.domain.entities.SteelMemberResult
import com.civileg.app.domain.entities.SteelMemberType
import com.civileg.app.domain.entities.SteelSectionType
import com.civileg.app.domain.entities.SteelGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * patch3: the engine's assessmentComplete flag must reach the PDF "Status" label.
 * A failed check outranks incompleteness; an incomplete assessment never reads as an all-clear.
 */
class SteelMemberPdfStatusTest {

    private fun member(isSafe: Boolean, complete: Boolean) = SteelMemberResult(
        sectionType = SteelSectionType.ISection(
            h = 300.0, bf = 150.0, tf = 10.7, tw = 7.1,
            grade = SteelGrade.ST37, customName = "IPE 300"
        ),
        memberType = SteelMemberType.BEAM,
        axialCapacity = 500.0,
        flexuralCapacity = 120.0,
        shearCapacity = 200.0,
        utilizationRatio = if (isSafe) 0.5 else 1.4,
        isSafe = isSafe,
        connectionDesign = null,
        bucklingCheck = null,
        weight = 42.2,
        cost = 0.0,
        warnings = emptyList(),
        codeNotes = emptyList(),
        assessmentComplete = complete,
        incompleteReasons = if (complete) emptyList() else listOf("test reason")
    )

    @Test
    fun failedCheckOutranksIncompleteness() {
        assertEquals("CHECK FAILS", steelMemberPdfStatus(member(isSafe = false, complete = false)))
        assertEquals("CHECK FAILS", steelMemberPdfStatus(member(isSafe = false, complete = true)))
    }

    @Test
    fun incompleteAssessmentNeverReadsAsAllClear() {
        assertTrue(steelMemberPdfStatus(member(isSafe = true, complete = false)).startsWith("NOT ASSESSED"))
    }

    @Test
    fun completePassingAssessmentReadsPreliminary() {
        assertEquals("CHECK PASSES (PRELIMINARY)", steelMemberPdfStatus(member(isSafe = true, complete = true)))
    }
}
