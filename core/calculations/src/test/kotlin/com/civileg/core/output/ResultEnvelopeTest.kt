package com.civileg.core.output

import com.civileg.core.calculations.entities.DesignCode
import com.civileg.core.calculations.entities.ElementType
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ResultEnvelope and DesignCheck.
 */
class ResultEnvelopeTest {

    @Test
    fun designCheck_utilizationAndPassedComputedCorrectly() {
        val checkPass = DesignCheck(name = "Flexure", demand = 80.0, capacity = 100.0, unit = "kN.m")
        assertEquals(0.8, checkPass.utilization, 0.001)
        assertTrue("Check should pass when utilization <= 1.0", checkPass.passed)

        val checkFail = DesignCheck(name = "Shear", demand = 150.0, capacity = 100.0, unit = "kN")
        assertEquals(1.5, checkFail.utilization, 0.001)
        assertFalse("Check should fail when utilization > 1.0", checkFail.passed)
    }

    @Test
    fun resultEnvelope_isSafeWhenAllChecksPass() {
        val check1 = DesignCheck("Flexure", 80.0, 100.0, "kN.m")
        val check2 = DesignCheck("Shear", 50.0, 100.0, "kN")

        val envelope = ResultEnvelope(
            element = ElementType.BEAM,
            code = DesignCode.ECP,
            inputs = mapOf("b" to "250", "h" to "500"),
            result = "SUCCESS",
            checks = listOf(check1, check2)
        )

        assertTrue("Envelope should be safe when all checks pass", envelope.isSafe)
        assertEquals(0.8, envelope.maxUtilization, 0.001)
    }

    @Test
    fun resultEnvelope_isUnsafeWhenAnyCheckFailsOrCriticalWarning() {
        val check1 = DesignCheck("Flexure", 80.0, 100.0, "kN.m")
        val check2 = DesignCheck("Shear", 120.0, 100.0, "kN")

        val envelope = ResultEnvelope(
            element = ElementType.BEAM,
            code = DesignCode.ACI,
            inputs = mapOf("b" to "250", "h" to "500"),
            result = "FAILED",
            checks = listOf(check1, check2),
            warnings = listOf(DesignWarning("CRIT_01", "Shear exceeds max limit", isCritical = true))
        )

        assertFalse("Envelope should be unsafe", envelope.isSafe)
        assertEquals(1.2, envelope.maxUtilization, 0.001)
    }
}
