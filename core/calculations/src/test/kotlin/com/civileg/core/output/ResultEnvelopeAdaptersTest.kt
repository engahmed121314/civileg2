package com.civileg.core.output

import com.civileg.core.calculations.entities.DesignCode
import com.civileg.core.calculations.entities.ElementType
import com.civileg.core.engine.rc.ecp.ECP203Engine
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ResultEnvelopeAdapters.
 */
class ResultEnvelopeAdaptersTest {

    @Test
    fun fromBeamFlexure_adaptsResultSuccessfully() {
        val input = ECP203Engine.BeamFlexureInput(
            muKnM = 150.0,
            fcuMpa = 30.0,
            fyMpa = 400.0,
            widthMm = 250.0,
            depthMm = 500.0
        )
        val beamResult = ECP203Engine.designBeamFlexure(input)

        val envelope = ResultEnvelopeAdapters.fromBeamFlexure(input, beamResult)

        assertNotNull(envelope)
        assertEquals(ElementType.BEAM, envelope.element)
        assertEquals(DesignCode.ECP, envelope.code)
        assertTrue("Envelope should contain trace steps", envelope.trace.isNotEmpty())
        assertTrue("Envelope should contain design checks", envelope.checks.isNotEmpty())
        assertTrue("Envelope should be safe", envelope.isSafe)
    }
}
