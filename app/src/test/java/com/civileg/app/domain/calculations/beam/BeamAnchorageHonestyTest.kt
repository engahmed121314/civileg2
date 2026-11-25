package com.civileg.app.domain.calculations.beam

import com.civileg.app.domain.calculations.BeamDesignEnginePart2
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The development/lap step has no verified primary-clause basis yet. These tests pin what the code is
 * allowed to claim, not that the numbers are correct for any code edition.
 */
class BeamAnchorageHonestyTest {

    @Test
    fun developmentStepNamesEditionAndDoesNotInventProvidedAnchorage() {
        for (code in DesignCode.values()) {
            val r = BeamDesignEnginePart2.checkDevelopmentLength(
                fcu = 30.0, fy = 400.0, dia = 16, code = code, span = 6.0, d = 500.0
            )
            assertEquals("provided anchorage must not be a span/3 stand-in ($code)", 0.0, r.provided, 0.0)
            assertTrue("step must say anchorage is not evaluated ($code): ${r.step.title}", r.step.title.contains("not evaluated"))
            assertEquals(com.civileg.app.domain.entities.StepStatus.WARNING, r.step.status)
            assertTrue("code reference must name the edition ($code)", listOf("ECP 203-2020", "SBC 304-CR-2018", "ACI 318M-14").any { r.step.codeReference.contains(it) })
            assertTrue("required length is at least the 300 mm code minimum ($code)", r.required >= 300.0)
        }
    }
}
