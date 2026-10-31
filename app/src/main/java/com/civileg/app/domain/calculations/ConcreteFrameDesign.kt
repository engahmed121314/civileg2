package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * تصميم العناصر الخرسانية في الإطار (تسليح الانحناء + القص)
 *
 * ── P4-4 (§9.38): ثلاثي الأكواد فعلياً عبر CodeProvisions ──
 * يدعم ECP 203-2020 و ACI 318-19 و SBC 304-2018:
 *  - ECP: معاملات جزئية (γc=1.5، γs=1.15) وطريقة K بذراع العزم — §4-2-1/§4-2-2
 *  - ACI: معامل اختزال φ (0.9 انحناء / 0.75 قص) مع كتلة إجهاد Whitney — Ch.9/Ch.22
 *  - SBC: منهجية ACI (SBC 304 يتبع ACI 318-14) مع الأغطية السعودية §20.5.1
 * كل ملاحظة كود تُستخرج من CodeReference حسب الكود المختار — لم يعد SBC
 * يُعرض بملاحظات ACI.
 */
object ConcreteFrameDesign {

    // Standard rebar areas (mm²)
    private val REBAR_AREAS = mapOf(
        10.0 to 78.5, 12.0 to 113.1, 13.0 to 132.7, 14.0 to 153.9,
        16.0 to 201.1, 18.0 to 254.5, 20.0 to 314.2, 22.0 to 380.1,
        25.0 to 490.9, 28.0 to 615.8, 32.0 to 804.2, 36.0 to 1017.9, 40.0 to 1256.6
    )

    /**
     * تصميم جميع الأعضاء الخرسانية في الإطار
     */
    fun designAllConcreteMembers(
        members: List<FrameMember>,
        memberForces: List<MemberEndForces>,
        memberDiagrams: List<MemberDiagram>,
        code: DesignCode
    ): List<ConcreteMemberDesignResult> {
        // ── InputGuard (ADR-010) ──
        InputGuard.notEmpty("members", members)
        InputGuard.notEmpty("memberForces", memberForces)
        return members.filter { it.materialType == FrameMaterialType.Concrete }.map { member ->
            val forces = memberForces.find { it.memberId == member.id }
            val diagram = memberDiagrams.find { it.memberId == member.id }
            designSingleMember(member, forces, diagram, code)
        }
    }

    /**
     * تصميم عضو خرساني واحد
     */
    fun designSingleMember(
        member: FrameMember,
        forces: MemberEndForces?,
        diagram: MemberDiagram?,
        code: DesignCode
    ): ConcreteMemberDesignResult {
        // ── InputGuard (ADR-010) ──
        InputGuard.notNull("member", member)
        val p = CodeProvisions.forCode(code)
        val section = member.concreteSection ?: ConcreteSectionProps(250.0, 500.0)
        val b = section.width
        val d = section.effectiveDepth
        val fcu = section.fcu
        val fy = section.fy

        val maxM = diagram?.maxMoment ?: forces?.maxMoment ?: 0.0
        val maxV = diagram?.maxShear ?: forces?.maxShear ?: 0.0
        val Pu = forces?.axialForce ?: 0.0

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // ── ملاحظات البنود حسب الكود المختار (لم تعد ثابتة على كود واحد) ──
        codeNotes.add(CodeReference.getReference(code, "FRAME_FLEXURE"))
        codeNotes.add(CodeReference.getReference(code, "FRAME_SHEAR"))
        codeNotes.add(p.refLoadCombination)
        when (p.flexureApproach) {
            CodeProvisions.FlexureApproach.PARTIAL_FACTORS ->
                codeNotes.add("ECP: γc=${p.gammaC}، γs=${p.gammaS} (معاملات جزئية) — ${p.refBeamFlexure}")
            CodeProvisions.FlexureApproach.PHI_STRENGTH ->
                codeNotes.add("${if (code == DesignCode.SBC) "SBC" else "ACI"}: φflexure=${p.phiFlexure}، φshear=${p.phiShear} — ${p.refBeamShear}")
        }
        codeNotes.add("الغطاء الخرساني: ${p.coverBeam.toInt()}mm — ${p.refBeamCover}")

        // === FLEXURE DESIGN ===
        val (asReq, _, singlyOk) = designFlexure(maxM, b, d, fcu, fy, code, codeNotes)
        if (!singlyOk) {
            warnings.add("القسم يحتاج تسليح مضغوط (معادلة من درجتين) — زد العمق أو السماكة")
        }

        // Select bars (top and bottom for beams)
        val (barDia, numTop, numBot, asTop, asBot) = selectBars(
            member.memberType, asReq, b, d, fcu, fy, code, warnings, codeNotes
        )

        // === SHEAR DESIGN ===
        val (stirrupDia, stirrupSpacing, vuCap) = designShear(
            maxV, b, d, fcu, fy, Pu, code, warnings, codeNotes
        )

        // === CHECKS ===
        val muCapacity = CodeProvisions.momentCapacity(code, b, d, asBot, fcu, fy)
        val momentUtil = if (muCapacity > 0) maxM / muCapacity else 999.0
        val shearUtil = if (vuCap > 0) maxV / vuCap else 999.0

        return ConcreteMemberDesignResult(
            memberId = member.id,
            memberName = member.name.ifEmpty { "عضو ${member.id}" },
            memberType = member.memberType,
            section = section,
            maxMoment = maxM,
            maxShear = maxV,
            axialForce = Pu,
            asRequired = asReq,
            asProvided = asBot,
            barDia = barDia,
            numBarsTop = numTop,
            numBarsBot = numBot,
            asTop = asTop,
            asBot = asBot,
            stirrupDia = stirrupDia,
            stirrupSpacing = stirrupSpacing,
            vuCapacity = vuCap,
            momentUtilization = momentUtil,
            shearUtilization = shearUtil,
            isSafe = momentUtil <= 1.0 && shearUtil <= 1.0,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    /**
     * تصميم الانحناء — عبر CodeProvisions.requiredFlexuralSteel
     * (ECP: طريقة K / ACI,SBC: Whitney مع φ — الصيغة تتغير كلياً حسب الكود)
     * @return (As required mm², Kbal/ρbal, singly-reinforced sufficient)
     */
    private fun designFlexure(
        Mu: Double, b: Double, d: Double, fcu: Double, fy: Double,
        code: DesignCode, codeNotes: MutableList<String>
    ): Triple<Double, Double, Boolean> {
        if (Mu <= 0) return Triple(0.0, 0.0, true)

        val (asReq, limitParam, singlyOk) = CodeProvisions.requiredFlexuralSteel(code, Mu, b, d, fcu, fy)

        if (code == DesignCode.ECP) {
            codeNotes.add("ECP §4-2-1: K_bal = ${String.format("%.4f", limitParam)}")
        } else {
            codeNotes.add("${if (code == DesignCode.SBC) "SBC 304" else "ACI 318"}: ρ_max = ${String.format("%.4f", limitParam)} (tension-controlled)")
        }

        return Triple(asReq, limitParam, singlyOk)
    }

    /**
     * اختيار التسليح (عدد وأقطار الأسياخ) — ρmin والغطاء حسب الكود
     */
    private fun selectBars(
        memberType: FrameMemberType,
        asRequired: Double,
        b: Double, d: Double, fcu: Double, fy: Double,
        code: DesignCode, warnings: MutableList<String>, codeNotes: MutableList<String>
    ): Tuple4<Double, Int, Int, Double, Double> {
        val p = CodeProvisions.forCode(code)

        // ── التسليح الأدنى حسب الكود (ECP ≠ ACI/SBC — صيغتان مختلفتان) ──
        val rhoMin = CodeProvisions.beamRhoMin(code, fcu, fy)
        val asMin = rhoMin * b * d

        val targetAs = max(asRequired, asMin)
        if (asRequired < asMin && asRequired > 0) {
            codeNotes.add("التسليح الأدنى حكم التصميم: ρmin=${String.format("%.5f", rhoMin)} — ${p.refBeamRhoMin}")
        }

        // Try bars from 12mm to 25mm — الخلوص يقاس من الغطاء الخرساني لكل كود
        val barDias = listOf(12.0, 16.0, 18.0, 20.0, 22.0, 25.0)
        val areaPerBar = REBAR_AREAS

        var selectedDia = 16.0
        var numBars = ceil(targetAs / (areaPerBar[selectedDia] ?: 201.1)).toInt()
        if (numBars < 2) numBars = 2 // minimum 2 bars

        for (dia in barDias) {
            val areaOne = areaPerBar[dia] ?: continue
            val n = ceil(targetAs / areaOne).toInt().coerceAtLeast(2)
            // عرض متاح داخل الغطاء الخرساني للكود المختار (SBC 50mm خارجي)
            val maxForDia = ((b - 2 * p.coverBeam) / (dia + 25.0)).toInt()
            if (n <= maxForDia && n <= 8) {
                selectedDia = dia
                numBars = n
                break
            }
        }

        val asProvided = numBars * (areaPerBar[selectedDia] ?: 201.1)

        // For beams: top and bottom reinforcement
        val numTop = if (memberType == FrameMemberType.Beam) {
            val asTopMin = asMin * 0.2 // hanger bars
            val asTopReq = asRequired * 0.4 // approx for top steel over supports if designed for midspan moment
            val asTopTarget = maxOf(asTopMin, asTopReq)
            ceil(asTopTarget / (areaPerBar[selectedDia] ?: 201.1)).toInt().coerceAtLeast(2)
        } else numBars

        val numBot = numBars
        val asTop = numTop * (areaPerBar[selectedDia] ?: 201.1)
        val asBot = asProvided

        if (asBot < asMin) warnings.add("مساحة التسليح أقل من الحد الأدنى (${asMin.toInt()} mm²) — ${p.refBeamRhoMin}")
        // الحد الأقصى: ECP 4% من b·d (§4-2-1-2) / ACI,SBC حد عملي 2.5% (tension-controlled)
        val asMax = if (code == DesignCode.ECP) 0.04 * b * d else 0.025 * b * d
        if (asBot > asMax) warnings.add("مساحة التسليح تتجاوز الحد الأقصى (${asMax.toInt()} mm²)")

        return Tuple4(selectedDia, numTop, numBot, asTop, asBot)
    }

    /**
     * تصميم القص — السعة الخرسانية وصيغة الكانات حسب الكود:
     *  - ECP §4-3-1-2: qcu = 0.24·√(fcu/γc) — بدون φ إضافي
     *  - ACI §22.5.5.1 / SBC: φVc = 0.75·0.17·√f'c·b·d
     */
    private fun designShear(
        Vu: Double, b: Double, d: Double, fcu: Double, fy: Double,
        Nu: Double, code: DesignCode,
        warnings: MutableList<String>, codeNotes: MutableList<String>
    ): Triple<Double, Double, Double> {
        if (Vu <= 0) return Triple(0.0, 0.0, 0.0)

        val p = CodeProvisions.forCode(code)

        // ── السعة الخرسانية حسب الكود ──
        val vc = CodeProvisions.concreteShearCapacity(code, b, d, fcu) // kN

        // Axial load effect (compression raises Vc — تقريب متحفظ موحد)
        val vcAdjusted = if (Nu > 0) {
            vc * (1.0 + min(0.25, Nu / (6.0 * b * d * fcu / 1e6)))
        } else vc

        codeNotes.add(
            if (code == DesignCode.ECP)
                "ECP §4-3-1-2: qcu = ${String.format("%.3f", 0.24 * sqrt(fcu / 1.5))} MPa → Vc = ${String.format("%.1f", vcAdjusted)} kN"
            else
                "${if (code == DesignCode.SBC) "SBC 304 §22.5" else "ACI 318 §22.5.5.1"}: φVc = ${String.format("%.1f", vcAdjusted)} kN"
        )

        val vuCapacity = if (Vu <= vcAdjusted) {
            // Minimum shear reinforcement
            val sMax = min(d / 2.0, if (code == DesignCode.ECP) 200.0 else 300.0)
            Triple(8.0, sMax, vcAdjusted)
        } else {
            // Calculate required steel shear reinforcement
            val vsRequired = Vu - vcAdjusted
            // ECP: qsu through Asv·fy/γs / (b·s) — ACI,SBC: Vs = Av·fy·d/s مع φ
            val stirrupDia = 8.0
            val asv = 2.0 * PI * (stirrupDia / 2.0).pow(2) // 2 legs
            val s = if (code == DesignCode.ECP) {
                asv * (fy / 1.15) * d / vsRequired / 1000.0
            } else {
                p.phiShear * asv * fy * d / vsRequired / 1000.0
            }
            val sClamped = min(max(s, 75.0), min(d, 400.0))

            val vsActual = if (code == DesignCode.ECP) {
                asv * (fy / 1.15) * d / sClamped / 1000.0
            } else {
                p.phiShear * asv * fy * d / sClamped / 1000.0
            }
            val totalVc = vcAdjusted + vsActual
            Triple(stirrupDia, sClamped, totalVc)
        }

        return vuCapacity
    }
}

/** Helper data class for returning multiple values */
private data class Tuple4<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
