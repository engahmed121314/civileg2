package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.ConcreteSectionProps
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.FrameMaterialType
import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameMemberType
import com.civileg.app.domain.entities.MemberEndForces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P4-4 (§9.38) — تصميم أعضاء الإطار ثلاثي الأكواد: هذه الاختبارات تُثبت أن
 * اختيار المستخدم للكود يغيّر نتائج التصميم (كمية الحديد، الكانات، القدرات)
 * وأن كل نتيجة تستشهد ببنود كودها الصحيح — قبل هذا التعديل كان SBC يُصمَّم
 * بمنهجية ACI ويُعرض بملاحظات ACI، وفرع ACI نفسه كان يستخدم صيغة ECP
 * (ذراع عزم 0.87·fy وثابت 0.893) — الأخطاء الثلاثة مغلقة هنا.
 *
 * الحالة الذهبية: b=250، h=500 (غطاء 50 → d=440)، fcu=25، fy=400،
 * Mu=150 kN·m، Vu=200 kN — قيم ECP/ACI مُتحقق منها بشكل مستقل.
 */
class ConcreteFrameDesignTest {

    private fun beamMember(): FrameMember = FrameMember(
        id = 1,
        nodeI = 1,
        nodeJ = 2,
        materialType = FrameMaterialType.Concrete,
        memberType = FrameMemberType.Beam,
        concreteSection = ConcreteSectionProps(width = 250.0, depth = 500.0, fcu = 25.0, fy = 400.0, cover = 50.0),
        name = "B1"
    )

    private fun forces(): MemberEndForces = MemberEndForces(
        memberId = 1,
        mi_z = 150.0,   // kN·m
        mj_z = -120.0,
        fi_y = 200.0    // kN
    )

    // ── 1. اختيار الكود يغيّر كمية الحديد المطلوبة ──

    @Test
    fun `same beam different codes different required steel`() {
        val member = beamMember()
        val f = forces()

        val ecp = ConcreteFrameDesign.designSingleMember(member, f, null, DesignCode.ECP)
        val aci = ConcreteFrameDesign.designSingleMember(member, f, null, DesignCode.ACI)
        val sbc = ConcreteFrameDesign.designSingleMember(member, f, null, DesignCode.SBC)

        // القيم المستقلة: ECP (K-method) > ACI (Whitney) لنفس المقطع
        assertTrue(
            "ECP As (${ecp.asRequired}) must differ from ACI As (${aci.asRequired})",
            kotlin.math.abs(ecp.asRequired - aci.asRequired) > 1.0
        )
        // SBC يشارك ACI منهجية Whitney → نفس As المطلوب
        assertEquals("SBC As must equal ACI As (same phi approach)", aci.asRequired, sbc.asRequired, 1e-6)
    }

    // ── 2. القيم الذهبية (d = 500 − 50 − 10 = 440) ──

    @Test
    fun `ECP required steel matches the independent K-method golden value`() {
        // ECP: K = 150e6/(25·250·440²) = 0.12397 ≤ Kbal=0.17568
        // z = 440·(0.5 + √(0.25 − 0.12397/0.893)) = 366.7 → As = 1176.0 mm²
        val ecp = ConcreteFrameDesign.designSingleMember(beamMember(), forces(), null, DesignCode.ECP)
        assertEquals(1175.99, ecp.asRequired, 1.5)
    }

    @Test
    fun `ACI required steel matches the independent Whitney golden value`() {
        // ACI: Rn = 150e6/(0.9·250·440²) = 3.4435، m = 23.529، ρ = 0.009720 → As = 1069.3 mm²
        val aci = ConcreteFrameDesign.designSingleMember(beamMember(), forces(), null, DesignCode.ACI)
        assertEquals(1069.25, aci.asRequired, 1.5)
    }

    // ── 3. كل كود يستشهد ببنوده الصحيحة — إغلاق خطأ SBC/ACI ──

    @Test
    fun `code notes cite the selected code - SBC never shows ACI clauses`() {
        val member = beamMember()
        val f = forces()

        val ecp = ConcreteFrameDesign.designSingleMember(member, f, null, DesignCode.ECP)
        val aci = ConcreteFrameDesign.designSingleMember(member, f, null, DesignCode.ACI)
        val sbc = ConcreteFrameDesign.designSingleMember(member, f, null, DesignCode.SBC)

        val ecpNotes = ecp.codeNotes.joinToString(" | ")
        val aciNotes = aci.codeNotes.joinToString(" | ")
        val sbcNotes = sbc.codeNotes.joinToString(" | ")

        assertTrue("ECP notes must cite ECP 203", ecpNotes.contains("ECP 203"))
        assertTrue("ACI notes must cite ACI 318", aciNotes.contains("ACI 318"))
        assertTrue("SBC notes must cite SBC 304", sbcNotes.contains("SBC 304"))
        assertTrue(
            "SBC notes must NEVER cite ACI (the pre-P4-4 bug)",
            !sbcNotes.contains("ACI")
        )
        assertTrue(
            "ECP notes must never cite ACI/SBC",
            !ecpNotes.contains("ACI") && !ecpNotes.contains("SBC 304")
        )
    }

    @Test
    fun `code notes expose the load-combination clause of the selected code`() {
        val ecp = ConcreteFrameDesign.designSingleMember(beamMember(), forces(), null, DesignCode.ECP)
        val sbc = ConcreteFrameDesign.designSingleMember(beamMember(), forces(), null, DesignCode.SBC)

        val ecpNotes = ecp.codeNotes.joinToString(" | ")
        val sbcNotes = sbc.codeNotes.joinToString(" | ")
        assertTrue("ECP combo 1.4D+1.6L", ecpNotes.contains("1.4D + 1.6L"))
        assertTrue("SBC combo 1.2D+1.6L", sbcNotes.contains("1.2D + 1.6L"))
    }

    // ── 4. سعة القص تتبع الكود ──

    @Test
    fun `shear capacity reflects the code concrete-shear formula`() {
        val member = beamMember()
        val f = forces()

        val ecp = ConcreteFrameDesign.designSingleMember(member, f, null, DesignCode.ECP)
        val aci = ConcreteFrameDesign.designSingleMember(member, f, null, DesignCode.ACI)

        // ECP: qcu = 0.24·√(25/1.5)·250·440/1000 = 107.8 kN (خرسانة فقط)
        // ACI: φVc = 0.75·0.17·√20·250·440/1000 = 62.7 kN (خرسانة فقط)
        // كلتاهما < Vu=200 → تُضاف الكانات؛ السعة النهائية = السعة الخرسانية + الكانات
        assertTrue("ECP final V capacity must cover Vu", ecp.vuCapacity >= 199.5)
        assertTrue("ACI final V capacity must cover Vu", aci.vuCapacity >= 199.5)

        // ملاحظة السعة الخرسانية تذكر المعادلة الصحيحة لكل كود
        val ecpNotes = ecp.codeNotes.joinToString(" | ")
        val aciNotes = aci.codeNotes.joinToString(" | ")
        assertTrue("ECP notes cite qcu formula", ecpNotes.contains("qcu"))
        assertTrue("ACI/SBC notes cite φVc", aciNotes.contains("φVc"))
    }

    // ── 5. الغطاء الخرساني حسب الكود يظهر في الملاحظات ──

    @Test
    fun `cover note follows the selected code`() {
        val ecp = ConcreteFrameDesign.designSingleMember(beamMember(), forces(), null, DesignCode.ECP)
        val sbc = ConcreteFrameDesign.designSingleMember(beamMember(), forces(), null, DesignCode.SBC)

        val ecpNotes = ecp.codeNotes.joinToString(" | ")
        val sbcNotes = sbc.codeNotes.joinToString(" | ")
        assertTrue("ECP cover 40mm", ecpNotes.contains("40mm"))
        assertTrue("SBC cover 50mm (Saudi exterior)", sbcNotes.contains("50mm"))
    }

    // ── 6. التسليح الأدنى يحكم الأحمال الصغيرة — بصيغة الكود ──

    @Test
    fun `light loads are governed by code-specific minimum reinforcement`() {
        val member = beamMember()
        val light = MemberEndForces(memberId = 1, mi_z = 20.0, mj_z = -15.0, fi_y = 30.0)

        val ecp = ConcreteFrameDesign.designSingleMember(member, light, null, DesignCode.ECP)
        val aci = ConcreteFrameDesign.designSingleMember(member, light, null, DesignCode.ACI)

        // الطلب المحسوب (قبل الحد الأدنى) — يختلف حسب الكود:
        // ECP K-method: z=431.7→clamp 418 → As=137.6 | ACI Whitney: ρ=0.0011637 → As=128.0
        assertEquals("ECP computed demand (K-method)", 137.56, ecp.asRequired, 1.0)
        assertEquals("ACI computed demand (Whitney)", 128.02, aci.asRequired, 1.0)
        assertNotEquals(ecp.asRequired, aci.asRequired, 1e-9)

        // التسليح المنفَّذ يحكمه الحد الأدنى لكل كود:
        // ECP: ρmin=0.00325·250·440=357.5 | ACI: 0.0035·250·440=385.0
        assertTrue("ECP provided steel must cover ρmin = 357.5 mm²", ecp.asProvided >= 357.5)
        assertTrue("ACI provided steel must cover ρmin = 385.0 mm²", aci.asProvided >= 385.0)

        // الملاحظة تذكر أن الحد الأدنى حكم التصميم
        assertTrue(ecp.codeNotes.joinToString(" | ").contains("التسليح الأدنى"))
        assertTrue(aci.codeNotes.joinToString(" | ").contains("التسليح الأدنى"))
    }

    // ── 7. designAllConcreteMembers يمرر الكود لكل الأعضاء ──

    @Test
    fun `designAllConcreteMembers routes every member through the selected code`() {
        val members = listOf(beamMember(), beamMember().copy(id = 2, name = "B2"))
        val forcesList = listOf(forces(), forces().copy(memberId = 2))
        val diagrams = emptyList<com.civileg.app.domain.entities.MemberDiagram>()

        val results = ConcreteFrameDesign.designAllConcreteMembers(members, forcesList, diagrams, DesignCode.SBC)
        assertEquals(2, results.size)
        assertTrue(results.all { it.codeNotes.joinToString(" | ").contains("SBC 304") })
    }
}
