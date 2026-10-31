package com.civileg.app.domain.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P4-4 (§9.38) — أحكام الكودود الحسابية: هذه الاختبارات تُثبت أن اختيار المستخدم
 * للكود يغيّر فعلياً المعاملات التي تقود الحسابات (معاملات التحميل، φ/γ، ρmin،
 * مقاومة القص، الأغطية) — لا مجرد نصوص العرض.
 *
 * الأرقام الذهبية مُتحقق منها بشكل مستقل (scripts/verify_p44_golden.py):
 * b=250mm، d=450mm، fcu=25، fy=400، Mu=150 kN·m.
 */
class CodeProvisionsTest {

    private val b = 250.0
    private val d = 450.0
    private val fcu = 25.0
    private val fy = 400.0

    // ── توليفات الأحمال: ECP ≠ ACI/SBC ──

    @Test
    fun `load factors follow the selected code`() {
        val ecp = CodeProvisions.forCode(DesignCode.ECP)
        val aci = CodeProvisions.forCode(DesignCode.ACI)
        val sbc = CodeProvisions.forCode(DesignCode.SBC)

        assertEquals("ECP dead factor γg=1.4", 1.4, ecp.loadFactorDead, 1e-9)
        assertEquals("ECP live factor γq=1.6", 1.6, ecp.loadFactorLive, 1e-9)
        assertEquals("ACI dead factor 1.2", 1.2, aci.loadFactorDead, 1e-9)
        assertEquals("SBC dead factor 1.2", 1.2, sbc.loadFactorDead, 1e-9)
        assertEquals("SBC live factor 1.6", 1.6, sbc.loadFactorLive, 1e-9)

        // الاتساق مع دوال DesignCode الموجودة (منع التباعد بين المصدرين)
        for (code in DesignCode.entries) {
            val p = CodeProvisions.forCode(code)
            assertEquals("$code dead factor must match DesignCode.getDeadLoadFactor()",
                code.getDeadLoadFactor(), p.loadFactorDead, 1e-12)
            assertEquals("$code live factor must match DesignCode.getLiveLoadFactor()",
                code.getLiveLoadFactor(), p.loadFactorLive, 1e-12)
        }
    }

    @Test
    fun `factored gravity load differs by code selection`() {
        val dead = 10.0
        val live = 5.0
        val ecp = CodeProvisions.forCode(DesignCode.ECP)
        val aci = CodeProvisions.forCode(DesignCode.ACI)
        // 1.4×10 + 1.6×5 = 22 vs 1.2×10 + 1.6×5 = 20 — اختيار الكود يغير الحمل المصمم عليه
        assertEquals(22.0, ecp.loadFactorDead * dead + ecp.loadFactorLive * live, 1e-9)
        assertEquals(20.0, aci.loadFactorDead * dead + aci.loadFactorLive * live, 1e-9)
    }

    // ── منهجية المقاومة: معاملات جزئية ضد معامل اختزال ──

    @Test
    fun `strength approach differs ECP partial factors vs ACI-SBC phi`() {
        val ecp = CodeProvisions.forCode(DesignCode.ECP)
        val aci = CodeProvisions.forCode(DesignCode.ACI)

        assertEquals(CodeProvisions.FlexureApproach.PARTIAL_FACTORS, ecp.flexureApproach)
        assertEquals(CodeProvisions.FlexureApproach.PHI_STRENGTH, aci.flexureApproach)
        assertEquals(1.5, ecp.gammaC, 1e-9)
        assertEquals(1.15, ecp.gammaS, 1e-9)
        assertEquals(0.9, aci.phiFlexure, 1e-9)
        assertEquals(0.75, aci.phiShear, 1e-9)
    }

    // ── الأغطية: SBC أكبر من ACI (بيئة المملكة) ──

    @Test
    fun `SBC covers exceed ACI covers for Saudi exposure`() {
        val aci = CodeProvisions.forCode(DesignCode.ACI)
        val sbc = CodeProvisions.forCode(DesignCode.SBC)

        assertTrue("SBC beam cover 50 > ACI 40", sbc.coverBeam > aci.coverBeam)
        assertTrue("SBC slab cover 40 > ACI 20", sbc.coverSlab > aci.coverSlab)
        assertEquals(50.0, sbc.coverBeam, 1e-9)
        assertEquals(20.0, aci.coverSlab, 1e-9)
    }

    // ── التسليح الأدنى: صيغتان مختلفتان ──

    @Test
    fun `beam rho-min uses ECP formula for ECP and ACI formula for ACI-SBC`() {
        val ecpRho = CodeProvisions.beamRhoMin(DesignCode.ECP, fcu, fy)
        val aciRho = CodeProvisions.beamRhoMin(DesignCode.ACI, fcu, fy)
        val sbcRho = CodeProvisions.beamRhoMin(DesignCode.SBC, fcu, fy)

        // ECP: max(0.26·√25/400, 0.0013) = max(0.00325, 0.0013) = 0.00325
        assertEquals(0.00325, ecpRho, 1e-6)
        // ACI: max(0.25·√20/400, 1.4/400) = max(0.002795, 0.0035) = 0.0035
        assertEquals(0.0035, aciRho, 1e-6)
        assertEquals("SBC shares the ACI rho-min formula", aciRho, sbcRho, 1e-12)
        assertNotEquals("ECP and ACI rho-min must differ", ecpRho, aciRho, 1e-9)
    }

    // ── مقاومة القص الخرسانية: ECP qcu مقابل φVc ──

    @Test
    fun `concrete shear capacity differs by code`() {
        val ecpVc = CodeProvisions.concreteShearCapacity(DesignCode.ECP, b, d, fcu)
        val aciVc = CodeProvisions.concreteShearCapacity(DesignCode.ACI, b, d, fcu)

        // ECP: qcu = 0.24·√(25/1.5) = 0.9798 MPa → 110.2 kN
        assertEquals(110.23, ecpVc, 0.1)
        // ACI: φ·0.17·√20·b·d = 64.15 kN
        assertEquals(64.15, aciVc, 0.1)
        assertTrue("ECP concrete shear ≠ ACI φVc", kotlin.math.abs(ecpVc - aciVc) > 1.0)
    }

    // ── التسليح المطلوب للانحناء: أرقام ذهبية لكل كود ──

    @Test
    fun `required flexural steel golden values per code`() {
        val mu = 150.0 // kN·m

        val (asEcp, kbalEcp, okEcp) = CodeProvisions.requiredFlexuralSteel(DesignCode.ECP, mu, b, d, fcu, fy)
        val (asAci, rhoMaxAci, okAci) = CodeProvisions.requiredFlexuralSteel(DesignCode.ACI, mu, b, d, fcu, fy)
        val (asSbc, _, okSbc) = CodeProvisions.requiredFlexuralSteel(DesignCode.SBC, mu, b, d, fcu, fy)

        // ECP طريقة K: K=0.1185 ≤ Kbal=0.1757، z=379.1 → As = 1137.5 mm²
        assertTrue("ECP section must be singly-reinforced", okEcp)
        assertEquals(1137.54, asEcp, 0.5)
        assertEquals("ECP K_bal (fy=400)", 0.175679, kbalEcp, 1e-4)

        // ACI Whitney: ρ=0.009233 → As = 1038.8 mm²
        assertTrue("ACI section must be singly-reinforced", okAci)
        assertEquals(1038.77, asAci, 0.5)
        assertEquals("ACI ρ_max = 0.75·ρbal (tension-controlled)", 0.016256, rhoMaxAci, 1e-4)

        // SBC يشارك ACI المنهجية → نفس As
        assertEquals("SBC shares the ACI Whitney approach", asAci, asSbc, 1e-9)
        assertTrue(okSbc)

        // اختيار الكود يغيّر كمية الحديد المطلوبة
        assertNotEquals("ECP As must differ from ACI As", asEcp, asAci, 1e-6)
    }

    // ── قدرة العزم: الاتساق الداخلي لكل كود ──

    @Test
    fun `moment capacity is internally consistent with required steel`() {
        val mu = 150.0
        val (asEcp, _, _) = CodeProvisions.requiredFlexuralSteel(DesignCode.ECP, mu, b, d, fcu, fy)
        val (asAci, _, _) = CodeProvisions.requiredFlexuralSteel(DesignCode.ACI, mu, b, d, fcu, fy)

        val capEcp = CodeProvisions.momentCapacity(DesignCode.ECP, b, d, asEcp, fcu, fy)
        val capAci = CodeProvisions.momentCapacity(DesignCode.ACI, b, d, asAci, fcu, fy)

        // القدرة المحسوبة بالتسليح المطلوب يجب أن تغطي العزم المطلوب (±1%)
        assertTrue("ECP capacity $capEcp must cover Mu=$mu", capEcp >= mu * 0.99)
        assertTrue("ACI capacity $capAci must cover Mu=$mu", capAci >= mu * 0.99)
        assertTrue("ECP capacity must not overshoot Mu by >2%", capEcp <= mu * 1.02)
        assertTrue("ACI capacity must not overshoot Mu by >2%", capAci <= mu * 1.02)
    }

    // ── حدود الأعمدة ──

    @Test
    fun `column reinforcement limits follow the code`() {
        val ecp = CodeProvisions.forCode(DesignCode.ECP)
        val aci = CodeProvisions.forCode(DesignCode.ACI)

        assertEquals("ECP ρmin أعمدة = 0.8%", 0.008, ecp.columnRhoMin, 1e-9)
        assertEquals("ACI ρmin أعمدة = 1.0%", 0.01, aci.columnRhoMin, 1e-9)
        assertEquals("ρmax أعمدة = 8% للجميع", 0.08, ecp.columnRhoMax, 1e-9)
        assertEquals(0.08, aci.columnRhoMax, 1e-9)
    }

    // ── المراجع: كل بند يذكر كوده ──

    @Test
    fun `every provision carries its clause reference`() {
        for (code in DesignCode.entries) {
            val p = CodeProvisions.forCode(code)
            val prefix = when (code) {
                DesignCode.ECP -> "ECP"
                DesignCode.ACI -> "ACI"
                DesignCode.SBC -> "SBC"
            }
            assertTrue("$code load-combination ref", p.refLoadCombination.contains(prefix))
            assertTrue("$code flexure ref", p.refBeamFlexure.contains(prefix))
            assertTrue("$code shear ref", p.refBeamShear.contains(prefix))
            assertTrue("$code rho-min ref", p.refBeamRhoMin.contains(prefix))
            assertTrue("$code cover ref", p.refBeamCover.contains(prefix))
            assertTrue("$code column ref", p.refColumnAxial.contains(prefix))
        }
    }
}
