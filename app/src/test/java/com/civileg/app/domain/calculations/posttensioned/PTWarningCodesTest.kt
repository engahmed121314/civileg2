package com.civileg.app.domain.calculations.posttensioned

import com.civileg.app.domain.calculations.base.posttensioned.ECP_PT_PARAMS
import com.civileg.app.domain.calculations.base.posttensioned.PTDesignEngine
import com.civileg.app.domain.calculations.base.posttensioned.PTExposureClass
import com.civileg.app.domain.calculations.base.posttensioned.PTSupportType
import com.civileg.app.domain.calculations.base.posttensioned.PTTendonType
import com.civileg.app.domain.calculations.base.posttensioned.PostTensionedElementType
import com.civileg.app.domain.calculations.base.posttensioned.PostTensionedInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stable warning/code-note keys emitted alongside the canonical English lists.
 * The UI localizes through these keys; reports keep the English strings.
 */
class PTWarningCodesTest {

    private val engine = PTDesignEngine(ECP_PT_PARAMS)

    private fun input(
        cover: Double = 40.0,
        fcu: Double = 40.0,
        dead: Double = 15.0,
        live: Double = 10.0,
        tendons: Int = 6,
        exposure: PTExposureClass = PTExposureClass.Moderate
    ) = PostTensionedInput(
        elementType = PostTensionedElementType.Beam,
        span = 10.0,
        width = 300.0,
        depth = 800.0,
        fcu = fcu,
        fy = 400.0,
        fpk = 1860.0,
        fp01k = 1610.0,
        tendonType = PTTendonType.Strand_7Wire_12_7mm,
        numTendons = tendons,
        tendonArea = 98.7,
        deadLoad = dead,
        liveLoad = live,
        supportType = PTSupportType.SimplySupported,
        cover = cover,
        designCode = com.civileg.app.domain.entities.DesignCode.ECP,
        exposureClass = exposure
    )

    private val knownCodes = setOf(
        "not_converged", "prestress_less", "losses_25", "cover_below", "fcu_below",
        "transfer_stress", "service_stress", "not_tension_controlled",
        "flexure_insufficient", "shear_insufficient", "deflection_exceeds",
        "anchorage_insufficient"
    )

    @Test
    fun `warning codes are stable keys from the known set`() {
        val cases = listOf(
            input(),
            input(cover = 5.0),
            input(fcu = 20.0),
            input(dead = 200.0, live = 200.0, tendons = 1),
            input(cover = 5.0, fcu = 20.0, dead = 300.0, live = 300.0, tendons = 2, exposure = PTExposureClass.Severe)
        )
        for (c in cases) {
            val r = engine.design(c)
            for (w in r.warningCodes) {
                assertTrue("unknown warning code: ${w.code}", w.code in knownCodes)
            }
        }
    }

    @Test
    fun `cover below minimum emits cover_below with values`() {
        val r = engine.design(input(cover = 5.0))
        val w = r.warningCodes.firstOrNull { it.code == "cover_below" }
        assertTrue("expected cover_below, got ${r.warningCodes.map { it.code }}", w != null)
        assertEquals(3, w!!.args.size)
    }

    @Test
    fun `overloaded beam emits prestress and capacity warnings`() {
        val r = engine.design(input(dead = 300.0, live = 300.0, tendons = 2))
        val codes = r.warningCodes.map { it.code }.toSet()
        assertTrue("expected flexure_insufficient in $codes", "flexure_insufficient" in codes)
    }

    @Test
    fun `code note keys are the four stable notes in order`() {
        val r = engine.design(input())
        assertEquals(listOf("version", "jacking", "transfer", "service"), r.codeNoteKeys.map { it.code })
        assertEquals(4, r.codeNotes.size)
    }

    @Test
    fun `canonical English warnings still emitted for reports`() {
        val r = engine.design(input(cover = 5.0, fcu = 20.0))
        assertTrue(r.warnings.isNotEmpty())
        assertTrue(r.warningCodes.isNotEmpty())
        assertTrue(r.warnings.all { it.length > 10 })
    }
}
