package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.FrameMaterialType
import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameMemberType
import com.civileg.app.domain.entities.MemberEndForces
import com.civileg.app.utils.SteelTables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteelFrameDesignTest {

    private fun steelMember(
        section: String?,
        type: FrameMemberType = FrameMemberType.Beam
    ) = FrameMember(
        id = 1,
        nodeI = 1,
        nodeJ = 2,
        materialType = FrameMaterialType.Steel,
        memberType = type,
        steelSectionName = section
    )

    private fun lightestSectionName(): String =
        SteelTables.getAllSections().minByOrNull { it.weight }!!.name

    @Test
    fun manualSectionIsUsedInsteadOfOptimization() {
        val result = SteelFrameDesign.designSingleMember(
            steelMember("IPE 400"), null, null, DesignCode.ECP, 355.0
        )

        assertEquals("IPE 400", result.selectedSection)
        assertTrue(result.isSafe)
        assertTrue(result.codeNotes.any { it.contains("IPE 400") })
    }

    @Test
    fun manualSectionNameIsTrimmedAndCaseInsensitive() {
        val result = SteelFrameDesign.designSingleMember(
            steelMember("  ipe 360  "), null, null, DesignCode.ECP, 355.0
        )

        assertEquals("IPE 360", result.selectedSection)
    }

    @Test
    fun autoSectionSelectsTheLightestSection() {
        val result = SteelFrameDesign.designSingleMember(
            steelMember(SteelFrameDesign.AUTO_SECTION), null, null, DesignCode.ECP, 355.0
        )

        assertEquals(lightestSectionName(), result.selectedSection)
    }

    @Test
    fun missingSectionFallsBackToAutomaticSelection() {
        val result = SteelFrameDesign.designSingleMember(
            steelMember("IPE 9999"), null, null, DesignCode.ECP, 355.0
        )

        assertEquals(lightestSectionName(), result.selectedSection)
        assertTrue(result.warnings.any { it.contains("غير موجود") })
    }

    @Test
    fun unsafeManualSectionIsKeptAndReported() {
        val result = SteelFrameDesign.designSingleMember(
            steelMember("IPE 80"),
            MemberEndForces(memberId = 1, mi_z = 5000.0),
            null,
            DesignCode.ECP,
            355.0
        )

        assertEquals("IPE 80", result.selectedSection)
        assertFalse(result.isSafe)
        assertTrue(result.warnings.any { it.contains("محدد") })
    }
}
