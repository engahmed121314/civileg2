package com.civileg.app.domain.safety

import com.civileg.core.sanity.SanityFinding
import com.civileg.core.sanity.SanityReport
import com.civileg.core.sanity.SanitySeverity
import com.civileg.app.utils.CalculationValidator
import org.junit.Assert.*
import org.junit.Test

class EngineeringSanityEngineTest {

    @Test
    fun from_mapsCoreErrorToBlocked() {
        val report = SanityReport(
            "x",
            listOf(SanityFinding("SAN-NEG", SanitySeverity.ERROR, "As < 0", codeReference = "ECP §"))
        )
        val r = EngineeringSanityEngine.from(report)
        assertEquals(SanityStatus.ERROR, r.status)
        assertTrue(r.blockedFromOutput)
        assertTrue(r.checks.any { it.rule == "SAN-NEG" })
    }

    @Test
    fun from_mapsCoreWarningToWarning() {
        val report = SanityReport(
            "x",
            listOf(SanityFinding("SAN-WARN", SanitySeverity.WARNING, "near limit"))
        )
        val r = EngineeringSanityEngine.from(report)
        assertEquals(SanityStatus.WARNING, r.status)
        assertFalse(r.blockedFromOutput)
    }

    @Test
    fun validateGeometry_flagsCoverVsDepth() {
        val r = EngineeringSanityEngine.validateGeometry(
            dimensions = mapOf("b" to 300.0, "h" to 500.0),
            reinforcement = mapOf("As" to 1000.0),
            cover = 60.0, effectiveDepth = 50.0,
            maxBarDiameter = 20.0, minMemberDimension = 300.0
        )
        assertEquals(SanityStatus.ERROR, r.status)
        assertTrue(r.blockedFromOutput)
        assertTrue(r.checks.any { it.rule == "COVER_VS_DEPTH" })
    }

    @Test
    fun validateGeometry_flagsNegativeDimensionAndBarFit() {
        val r = EngineeringSanityEngine.validateGeometry(
            dimensions = mapOf("b" to 0.0),
            reinforcement = mapOf("As" to -5.0),
            cover = 30.0, effectiveDepth = 450.0,
            maxBarDiameter = 500.0, minMemberDimension = 300.0
        )
        assertTrue(r.checks.any { it.rule == "GEOMETRY_POSITIVE" })
        assertTrue(r.checks.any { it.rule == "REBAR_NEGATIVE" })
        assertTrue(r.checks.any { it.rule == "BAR_FITS_SECTION" })
        assertEquals(SanityStatus.ERROR, r.status)
    }

    @Test
    fun validateGeometry_good_passes() {
        val r = EngineeringSanityEngine.validateGeometry(
            dimensions = mapOf("b" to 300.0, "h" to 500.0),
            reinforcement = mapOf("As" to 1000.0),
            cover = 40.0, effectiveDepth = 450.0,
            maxBarDiameter = 25.0, minMemberDimension = 300.0
        )
        assertEquals(SanityStatus.OK, r.status)
        assertFalse(r.blockedFromOutput)
    }

    @Test
    fun fromValidation_mapsWarningsAndErrorsToUnifiedContract() {
        val report = CalculationValidator.ValidationReport(
            isConsistent = false,
            errors = listOf("CRITICAL: Mu > Mn but marked SAFE"),
            warnings = listOf("Logic: Steel ratio is very low", "Audit: utilization 90%+")
        )
        val r = EngineeringSanityEngine.fromValidation(report)

        assertEquals(SanityStatus.ERROR, r.status)
        assertTrue(r.blockedFromOutput)
        assertEquals(3, r.checks.size)
        assertEquals(1, r.checks.count { it.severity == SanityStatus.ERROR })
        assertEquals(2, r.checks.count { it.severity == SanityStatus.WARNING })
        assertTrue(r.checks.all { it.rule == "VALIDATION" })
    }

    @Test
    fun fromValidation_cleanWarningsOnlyIsWarningStatus() {
        val report = CalculationValidator.ValidationReport(
            isConsistent = true,
            errors = emptyList(),
            warnings = listOf("Audit: verify site tolerances")
        )
        val r = EngineeringSanityEngine.fromValidation(report)

        assertEquals(SanityStatus.WARNING, r.status)
        assertFalse(r.blockedFromOutput)
    }

    @Test
    fun fromValidation_cleanReportIsOk() {
        val r = EngineeringSanityEngine.fromValidation(
            CalculationValidator.ValidationReport(true, emptyList(), emptyList())
        )
        assertEquals(SanityStatus.OK, r.status)
        assertTrue(r.checks.isEmpty())
    }
}
