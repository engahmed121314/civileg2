package com.civileg.app.domain.calculations.aci

import com.civileg.core.calculations.entities.BarLocation
import com.civileg.core.calculations.entities.CoatingType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * W10 golden regression gate (PHASE00_AUDIT §3.1 W10):
 * psi_s was inverted — large bars received 0.8 and small bars 1.0, shortening
 * development length for big bars (ACI 318-19 Table 25.4.2.5 violation).
 *
 * Hand-derived per ACI 25.4.2.3a (fcu=25 cube -> fc'=20 MPa cylinder, lambda=1):
 *   Ld = (fy * psi_t * psi_e * psi_s) / (1.7 * lambda * sqrt(fc')) * db, min 300mm
 */
class ACIBeamDevelopmentLengthTest {

    private val beam = ACIBeam()

    @Test
    fun `W10 small bar gets reduced factor 0_8`() {
        // Ø16 bottom uncoated: psi_s = 0.8
        // Ld = (420*0.8)/(1.7*sqrt(20)) * 16 = 707.12mm -> rounded up to 725
        val ld = beam.calculateDevelopmentLength(
            barDiameter = 16.0, fy = 420.0, fcu = 25.0,
            barLocation = BarLocation.BOTTOM, coating = CoatingType.UNCOATED
        )
        assertEquals(725.0, ld, 0.0)
    }

    @Test
    fun `W10 large bar keeps full factor 1_0`() {
        // Ø25 bottom uncoated: psi_s = 1.0 (was wrongly 0.8 before the fix)
        // Ld = (420)/(1.7*sqrt(20)) * 25 = 1381.09mm -> rounded up to 1400
        val ld = beam.calculateDevelopmentLength(
            barDiameter = 25.0, fy = 420.0, fcu = 25.0,
            barLocation = BarLocation.BOTTOM, coating = CoatingType.UNCOATED
        )
        assertEquals(1400.0, ld, 0.0)
    }

    @Test
    fun `W10 large bar develops longer than small bar per unit diameter`() {
        // Per-mm-of-bar development length must not DECREASE with bar size.
        // Before the fix: ld/db was smaller for Ø25 than for Ø16 (unconservative).
        val ld16 = beam.calculateDevelopmentLength(
            barDiameter = 16.0, fy = 420.0, fcu = 25.0,
            barLocation = BarLocation.BOTTOM, coating = CoatingType.UNCOATED
        )
        val ld25 = beam.calculateDevelopmentLength(
            barDiameter = 25.0, fy = 420.0, fcu = 25.0,
            barLocation = BarLocation.BOTTOM, coating = CoatingType.UNCOATED
        )
        assert(ld25 / 25.0 > ld16 / 16.0) {
            "ld/db must increase with bar size once psi_s no longer shrinks it"
        }
    }
}
