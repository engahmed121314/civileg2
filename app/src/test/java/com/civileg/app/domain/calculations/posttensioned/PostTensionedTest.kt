package com.civileg.app.domain.calculations.posttensioned

import com.civileg.app.domain.calculations.aci.ACIPostTensioned
import com.civileg.app.domain.calculations.base.posttensioned.ECP_PT_PARAMS
import com.civileg.app.domain.calculations.base.posttensioned.PTDesignEngine
import com.civileg.app.domain.calculations.base.posttensioned.PTExposureClass
import com.civileg.app.domain.calculations.base.posttensioned.PTSupportType
import com.civileg.app.domain.calculations.base.posttensioned.PTTendonType
import com.civileg.app.domain.calculations.base.posttensioned.PostTensionedElementType
import com.civileg.app.domain.calculations.base.posttensioned.PostTensionedInput
import com.civileg.app.domain.calculations.ecp.ECPPostTensioned
import com.civileg.app.domain.calculations.sbc.SBCPostTensioned
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * اختبارات محرك تصميم الخرسانة مسبقة الإجهاد (Post-Tensioned).
 * تغطي: خصائص المقطع، فقد الإجهاد، الإجهادات، المقاومة القصوى،
 * الترخيم، منطقة التثبيت، والتحقق من المدخلات.
 */
class PostTensionedTest {

    private lateinit var ecp: ECPPostTensioned
    private lateinit var aci: ACIPostTensioned
    private lateinit var sbc: SBCPostTensioned

    @Before
    fun setup() {
        ecp = ECPPostTensioned()
        aci = ACIPostTensioned()
        sbc = SBCPostTensioned()
    }

    private fun beamInput(
        span: Double = 10.0,
        width: Double = 300.0,
        depth: Double = 800.0,
        fcu: Double = 40.0,
        fy: Double = 400.0,
        dead: Double = 15.0,
        live: Double = 10.0,
        tendons: Int = 6,
        code: DesignCode = DesignCode.ECP
    ) = PostTensionedInput(
        elementType = PostTensionedElementType.Beam,
        span = span,
        width = width,
        depth = depth,
        fcu = fcu,
        fy = fy,
        fpk = 1860.0,
        fp01k = 1610.0,
        tendonType = PTTendonType.Strand_7Wire_12_7mm,
        numTendons = tendons,
        tendonArea = 98.7,
        deadLoad = dead,
        liveLoad = live,
        supportType = PTSupportType.SimplySupported,
        cover = 40.0,
        designCode = code,
        exposureClass = PTExposureClass.Moderate
    )

    // ═══════════════════════════════════════════════════════════════
    // Section properties
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `rectangular section properties are correct`() {
        val engine = PTDesignEngine(com.civileg.app.domain.calculations.base.posttensioned.ECP_PT_PARAMS)
        val props = engine.sectionProperties(beamInput())
        assertEquals(300.0 * 800.0, props.area, 1e-6)
        assertEquals(300.0 * 800.0 * 800.0 * 800.0 / 12.0, props.ixx, 1e-3)
        assertEquals(400.0, props.yTop, 1e-6)
        assertEquals(400.0, props.yBottom, 1e-6)
        assertEquals(props.ixx / 400.0, props.zTop, 1e-6)
        assertEquals(props.ixx / 400.0, props.zBottom, 1e-6)
    }

    @Test
    fun `T-section properties shift centroid upward`() {
        val engine = PTDesignEngine(com.civileg.app.domain.calculations.base.posttensioned.ECP_PT_PARAMS)
        val input = beamInput().copy(flangeWidth = 900.0, flangeThickness = 150.0)
        val props = engine.sectionProperties(input)
        assertTrue("Area must increase with flange", props.area > 300.0 * 800.0)
        assertTrue("Centroid must rise with top flange", props.centroidFromBottom > 400.0)
        assertTrue(props.yBottom > props.yTop)
    }

    // ═══════════════════════════════════════════════════════════════
    // Jacking stress and losses
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `jacking stress is limited by fpk and fp01k`() {
        val input = beamInput()
        val engine = PTDesignEngine(com.civileg.app.domain.calculations.base.posttensioned.ECP_PT_PARAMS)
        val expected = minOf(0.80 * input.fpk, 0.90 * input.fp01k)
        assertEquals(expected, engine.jackingStress(input), 1e-6)
    }

    @Test
    fun `losses reduce effective stress and stay within range`() {
        val input = beamInput()
        val losses = ecp.calculateLosses(input)
        assertTrue(losses.total > 0.0)
        assertTrue(losses.effectiveStress > 0.0)
        assertTrue(losses.effectiveStress < ecp.calculateRequiredPrestress(input).initialStress)
        assertTrue(losses.totalPercent in 0.0..100.0)
        assertTrue("Losses should be below 25% for a normal beam", losses.totalPercent < 25.0)
    }

    @Test
    fun `friction loss increases with wobble coefficient and drape`() {
        val lowWobble = ecp.calculateLosses(beamInput().copy(wobbleCoefficient = 0.001))
        val highWobble = ecp.calculateLosses(beamInput().copy(wobbleCoefficient = 0.01))
        assertTrue(highWobble.friction > lowWobble.friction)

        val shallow = ecp.calculateLosses(beamInput(depth = 500.0))
        val deep = ecp.calculateLosses(beamInput(depth = 900.0))
        assertTrue(deep.friction > shallow.friction)
    }

    // ═══════════════════════════════════════════════════════════════
    // Prestress design
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `required prestress produces adequate tendons`() {
        val input = beamInput()
        val design = ecp.calculateRequiredPrestress(input)
        assertTrue(design.requiredForce > 0.0)
        assertTrue(design.jackingForce >= design.requiredForce)
        assertTrue(design.providedEccentricity >= 0.0)
        assertTrue(design.providedEccentricity <= 400.0 - input.cover)
        assertTrue(design.numTendons >= 1)
        assertTrue(design.isAdequate)
    }

    // ═══════════════════════════════════════════════════════════════
    // Stress checks
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `stress check for a well proportioned beam is safe`() {
        // 11 tendons satisfy the ECP service tension limit once losses are
        // evaluated at transfer using the self-weight only.
        val input = beamInput(tendons = 11)
        val losses = ecp.calculateLosses(input)
        val check = ecp.checkStresses(input, losses)
        assertTrue(check.overallSafe)
        assertTrue(check.isTransferSafe)
        assertTrue(check.isServiceSafe)
        assertTrue(check.serviceBottomCompression >= 0.0)
    }

    @Test
    fun `allowable stresses follow ECP factors`() {
        val input = beamInput()
        val check = ecp.checkStresses(input, ecp.calculateLosses(input))
        assertEquals(0.25 * kotlin.math.sqrt(input.fcu), check.allowableServiceTens, 1e-6)
        assertEquals(0.45 * input.fcu, check.allowableServiceComp, 1e-6)
    }

    // ═══════════════════════════════════════════════════════════════
    // Ultimate strength
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `flexural capacity is positive and required moment matches statics`() {
        val input = beamInput()
        val ultimate = ecp.checkUltimateStrength(input, ecp.calculateLosses(input))
        assertTrue(ultimate.flexuralCapacity > 0.0)
        // Mu = 1.4*wD*L²/8 + 1.6*wL*L²/8
        val wDead = input.totalDeadLoad()
        val expectedMu = (1.4 * wDead + 1.6 * input.liveLoad) * input.span * input.span / 8.0
        assertEquals(expectedMu, ultimate.requiredMoment, 1e-3)
        assertNotNull(ultimate.requiredStirrups)
    }

    @Test
    fun `shear capacity includes stirrups and prestress contribution`() {
        val input = beamInput()
        val ultimate = ecp.checkUltimateStrength(input, ecp.calculateLosses(input))
        assertTrue(ultimate.shearCapacity > 0.0)
        assertTrue(ultimate.requiredShear > 0.0)
        assertTrue(ultimate.requiredStirrups!!.spacing in 50.0..300.0)
    }

    @Test
    fun `very shallow section keeps a positive stirrup spacing`() {
        val input = beamInput(depth = 120.0, tendons = 1)
        val ultimate = ecp.checkUltimateStrength(input, ecp.calculateLosses(input))
        assertNotNull(ultimate.requiredStirrups)
        assertTrue(ultimate.requiredStirrups!!.spacing > 0.0)
    }

    @Test
    fun `under reinforced beam passes ultimate strength`() {
        val input = beamInput(tendons = 9)
        val ultimate = ecp.checkUltimateStrength(input, ecp.calculateLosses(input))
        assertTrue(ultimate.isFlexureSafe)
        assertTrue(ultimate.isShearSafe)
        assertTrue(ultimate.overallSafe)
    }

    // ═══════════════════════════════════════════════════════════════
    // Anchorage zone
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `anchorage zone produces bursting reinforcement`() {
        val input = beamInput()
        val prestress = ecp.calculateRequiredPrestress(input)
        val anchorage = ecp.designAnchorageZone(input, prestress.jackingForce)
        assertTrue(anchorage.burstingForce > 0.0)
        assertTrue(anchorage.burstingReinforcement > 0.0)
        assertTrue(anchorage.stirrupSpacing in 50.0..150.0)
        assertTrue(anchorage.isAdequate)
    }

    // ═══════════════════════════════════════════════════════════════
    // Deflection
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `prestress deflection is upward and total is within L over 250`() {
        val input = beamInput()
        val deflection = ecp.calculateDeflection(input, ecp.calculateLosses(input))
        assertTrue("Prestress camber must be upward (negative)", deflection.prestressDeflection < 0.0)
        assertEquals(input.span * 1000.0 / 250.0, deflection.allowableDeflection, 1e-6)
        assertTrue(deflection.totalDeflection > 0.0)
        assertTrue(deflection.isSafe)
    }

    // ═══════════════════════════════════════════════════════════════
    // Full design
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `full ECP design returns a safe and complete result`() {
        val result = ecp.designPostTensioned(beamInput())
        assertTrue(result.isSafe)
        assertTrue(result.prestressDesign.requiredForce > 0.0)
        assertTrue(result.losses.total > 0.0)
        assertTrue(result.ultimateStrength.overallSafe)
        assertTrue(result.deflection.isSafe)
        assertTrue(result.quantity.concreteVolume > 0.0)
        assertTrue(result.quantity.ptTendonWeight > 0.0)
        assertTrue(result.codeNotes.isNotEmpty())
    }

    @Test
    fun `all three codes produce finite safe results`() {
        val ecpResult = ecp.designPostTensioned(beamInput(code = DesignCode.ECP))
        val aciResult = aci.designPostTensioned(beamInput(code = DesignCode.ACI))
        val sbcResult = sbc.designPostTensioned(beamInput(code = DesignCode.SBC))

        listOf(ecpResult, aciResult, sbcResult).forEach { r ->
            assertTrue(r.losses.effectiveForce.isFinite())
            assertTrue(r.ultimateStrength.flexuralCapacity.isFinite())
            assertTrue(r.deflection.totalDeflection.isFinite())
            assertTrue(r.isSafe)
        }
        // ACI/SBC use 1.2D while ECP uses 1.4D → different required moments
        assertTrue(abs(ecpResult.ultimateStrength.requiredMoment -
            aciResult.ultimateStrength.requiredMoment) > 1.0)
        assertEquals(aciResult.ultimateStrength.requiredMoment,
            sbcResult.ultimateStrength.requiredMoment, 1e-6)
    }

    @Test
    fun `slender section requires more prestress than deep section`() {
        val slender = ecp.calculateRequiredPrestress(beamInput(depth = 600.0))
        val deep = ecp.calculateRequiredPrestress(beamInput(depth = 1000.0))
        assertTrue(slender.requiredForce > deep.requiredForce)
    }

    // ═══════════════════════════════════════════════════════════════
    // Input validation
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `negative span is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ecp.designPostTensioned(beamInput(span = -5.0))
        }
    }

    @Test
    fun `zero concrete strength is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ecp.designPostTensioned(beamInput(fcu = 0.0))
        }
    }

    @Test
    fun `negative live load is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ecp.designPostTensioned(beamInput(live = -1.0))
        }
    }

    @Test
    fun `overstressed shallow beam is flagged unsafe`() {
        val result = ecp.designPostTensioned(
            beamInput(span = 20.0, depth = 400.0, live = 40.0, tendons = 2)
        )
        assertFalse(result.isSafe)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `T-section self weight includes the flange`() {
        val input = beamInput().copy(flangeWidth = 900.0, flangeThickness = 150.0)
        val expectedArea = 300.0 * 800.0 + (900.0 - 300.0) * 150.0
        assertEquals(expectedArea * 25.0 / 1e6, input.selfWeight(), 1e-9)
        assertTrue(input.selfWeight() > beamInput().selfWeight())
    }

    @Test
    fun `tendon quantities scale with the number of tendons`() {
        val engine = PTDesignEngine(ECP_PT_PARAMS)
        val six = beamInput(tendons = 6)
        val nine = six.copy(numTendons = 9)
        val q6 = engine.quantities(six, engine.calculateLosses(six))
        val q9 = engine.quantities(nine, engine.calculateLosses(nine))
        assertEquals(1.5, q9.ductWeight / q6.ductWeight, 1e-9)
        assertTrue(q9.groutVolume > q6.groutVolume)
        assertTrue(q9.anchorageWeight > q6.anchorageWeight)
    }

    @Test
    fun `design raises a low tendon count until the prestress is adequate`() {
        val result = ecp.designPostTensioned(beamInput(tendons = 1))
        assertTrue(result.input.numTendons > 1)
        assertEquals(result.prestressDesign.numTendons, result.input.numTendons)
        assertFalse(result.warnings.any { it.contains("did not converge") })
    }

    @Test
    fun `cover below the exposure minimum is reported`() {
        val result = ecp.designPostTensioned(beamInput().copy(cover = 20.0))
        assertTrue(result.warnings.any { it.contains("cover") })
    }
}