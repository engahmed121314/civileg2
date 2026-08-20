package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * تصميم العناصر الخرسانية في الإطار (تسليح الانحناء + القص)
 * يدعم ECP 203-2020 و ACI 318-19
 */
object ConcreteFrameDesign {

    // Standard rebar areas (mm²)
    private val REBAR_AREAS = mapOf(
        10.0 to 78.5, 12.0 to 113.1, 13.0 to 132.7, 14.0 to 153.9,
        16.0 to 201.1, 18.0 to 254.5, 20.0 to 314.2, 22.0 to 380.1,
        25.0 to 490.9, 28.0 to 615.8, 32.0 to 804.2, 36.0 to 1017.9, 40.0 to 1256.6
    )

    private const val GAMMA_C_ECP = 1.5
    private const val GAMMA_S_ECP = 1.15
    private const val GAMMA_C_ACI = 1.5  // φ factor applied separately
    private const val BETA1_LIMIT = 0.85

    /**
     * تصميم جميع الأعضاء الخرسانية في الإطار
     */
    fun designAllConcreteMembers(
        members: List<FrameMember>,
        memberForces: List<MemberEndForces>,
        memberDiagrams: List<MemberDiagram>,
        code: DesignCode
    ): List<ConcreteMemberDesignResult> {
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
        val section = member.concreteSection ?: ConcreteSectionProps(250.0, 500.0)
        val b = section.width
        val h = section.depth
        val d = section.effectiveDepth
        val fcu = section.fcu
        val fy = section.fy

        val maxM = diagram?.maxMoment ?: forces?.maxMoment ?: 0.0
        val maxV = diagram?.maxShear ?: forces?.maxShear ?: 0.0
        val Pu = forces?.axialForce ?: 0.0

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // === FLEXURE DESIGN ===
        val (asReq, isSafeMoment, kBal) = designFlexure(maxM, b, d, fcu, fy, code, warnings, codeNotes)

        // Select bars (top and bottom for beams)
        val (barDia, numTop, numBot, asTop, asBot) = selectBars(
            member.memberType, asReq, b, d, fcu, fy, code, warnings
        )

        // === SHEAR DESIGN ===
        val (stirrupDia, stirrupSpacing, vuCap) = designShear(
            maxV, b, d, fcu, fy, Pu, code, warnings, codeNotes
        )

        // === CHECKS ===
        val muCapacity = calculateMomentCapacity(b, d, asBot, fcu, fy, code)
        val momentUtil = if (muCapacity > 0) maxM / muCapacity else 999.0
        val shearUtil = if (vuCap > 0) maxV / vuCap else 999.0

        if (code == DesignCode.ECP) codeNotes.add("ECP 203-2020 - البند 6-2")
        else codeNotes.add("ACI 318-19 - Chapter 22")

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
     * تصميم الانحناء - حساب مساحة الحديد المطلوبة
     * @return (As required mm², isSafe, K_bal)
     */
    private fun designFlexure(
        Mu: Double, b: Double, d: Double, fcu: Double, fy: Double,
        code: DesignCode, warnings: MutableList<String>, codeNotes: MutableList<String>
    ): Triple<Double, Boolean, Double> {
        if (Mu <= 0) return Triple(0.0, true, 0.0)

        val gammaC = if (code == DesignCode.ECP) GAMMA_C_ECP else GAMMA_C_ACI
        val gammaS = if (code == DesignCode.ECP) GAMMA_S_ECP else 1.0 // ACI uses φ differently

        // K = M / (f'c * b * d²)
        val K = Mu * 1e6 / (fcu * b * d * d) // Mu in kN.m -> N.mm

        // K_bal calculation per code
        val Kbal = calculateKbal(fcu, fy, code, gammaC, gammaS)

        codeNotes.add("K = $K, K_bal = $Kbal")

        if (K <= Kbal) {
            // Singly reinforced
            val z = d * (0.5 + sqrt(0.25 - K / (1.5 * if (code == DesignCode.ECP) 1.0 else 1.0)))
            val zClamped = min(z, 0.95 * d)
            val As = Mu * 1e6 / (0.87 * fy * zClamped)
            return Triple(As, true, Kbal)
        } else {
            // Need compression reinforcement (doubly reinforced)
            warnings.add("القسم يحتاج تسليح مضغوط (معادلة من درجتين)")
            val Kp = 0.156 // K' for compression steel design
            val As1 = Kp * fcu * b * d * d / (0.87 * fy * d)
            val M2 = Mu * 1e6 - Kp * fcu * b * d * d
            val dPrime = 50.0 // assume 50mm to compression steel
            val As2 = M2 / (0.87 * fy * (d - dPrime))
            return Triple(As1 + As2, true, Kbal)
        }
    }

    /**
     * حساب K_bal حسب الكود
     */
    private fun calculateKbal(fcu: Double, fy: Double, code: DesignCode, gammaC: Double, gammaS: Double): Double {
        return if (code == DesignCode.ECP) {
            // ECP 203: K_bal depends on fcu and fy
            val alpha = 0.67 / gammaC
            val beta = if (fcu <= 30.0) 0.85 else max(0.85 - 0.05 * ((fcu - 30.0) / 7.0), 0.65)
            val xb = 600.0 * beta / (600.0 + fy / gammaS)
            alpha * xb * (1.0 - 0.5 * xb)
        } else {
            // ACI 318-19: use ρ_bal approach
            val beta1 = if (fcu <= 28.0) 0.85 else max(0.85 - 0.05 * ((fcu - 28.0) / 7.0), 0.65)
            val aBalOverD = beta1 * 87000.0 / (87000.0 + fy)
            val rhoBal = 0.85 * fcu / fy * aBalOverD
            rhoBal * fy / fcu * (1.0 - 0.59 * rhoBal * fy / fcu) * 1.5
        }
    }

    /**
     * اختيار التسليح (عدد وأقطار الأسياخ)
     */
    private fun selectBars(
        memberType: FrameMemberType,
        asRequired: Double,
        b: Double, d: Double, fcu: Double, fy: Double,
        code: DesignCode, warnings: MutableList<String>
    ): Tuple4<Double, Int, Int, Double, Double> {
        if (asRequired <= 0) return Tuple4(0.0, 0, 0, 0.0, 0.0)

        // Try bars from 12mm to 25mm
        val barDias = listOf(12.0, 16.0, 18.0, 20.0, 22.0, 25.0)
        val areaPerBar = REBAR_AREAS

        var selectedDia = 16.0
        var numBars = ceil(asRequired / (areaPerBar[selectedDia] ?: 201.1)).toInt()
        if (numBars < 2) numBars = 2 // minimum 2 bars

        // Check if bars fit in width
        val minSpacing = max(selectedDia + 25.0, 25.0) // mm
        val maxBars = ((b - 2 * 50.0) / (selectedDia + minSpacing)).toInt().coerceAtLeast(2)

        for (dia in barDias) {
            val areaOne = areaPerBar[dia] ?: continue
            val n = ceil(asRequired / areaOne).toInt().coerceAtLeast(2)
            val maxForDia = ((b - 2 * 50.0) / (dia + 25.0)).toInt()
            if (n <= maxForDia && n <= 8) {
                selectedDia = dia
                numBars = n
                break
            }
        }

        val asProvided = numBars * (areaPerBar[selectedDia] ?: 201.1)

        // For beams: top and bottom reinforcement
        val numTop = if (memberType == FrameMemberType.Beam) {
            val asTop = asRequired * 0.5
            ceil(asTop / (areaPerBar[selectedDia] ?: 201.1)).toInt().coerceAtLeast(2)
        } else numBars

        val numBot = numBars
        val asTop = numTop * (areaPerBar[selectedDia] ?: 201.1)
        val asBot = asProvided

        // Check min/max reinforcement
        val asMin = 0.26 * (fcu / fy) * b * d // ACI min
        if (asBot < asMin) warnings.add("مساحة التسليح أقل من الحد الأدنى (${asMin.toInt()} mm²)")
        val asMax = 0.04 * b * d
        if (asBot > asMax) warnings.add("مساحة التسليح تتجاوز الحد الأقصى (${asMax.toInt()} mm²)")

        return Tuple4(selectedDia, numTop, numBot, asTop, asBot)
    }

    /**
     * تصميم القص
     */
    private fun designShear(
        Vu: Double, b: Double, d: Double, fcu: Double, fy: Double,
        Nu: Double, code: DesignCode,
        warnings: MutableList<String>, codeNotes: MutableList<String>
    ): Triple<Double, Double, Double> {
        if (Vu <= 0) return Triple(0.0, 0.0, 0.0)

        val gammaC = if (code == DesignCode.ECP) GAMMA_C_ECP else GAMMA_C_ACI
        val gammaS = if (code == DesignCode.ECP) GAMMA_S_ECP else 1.0

        // Concrete shear capacity
        val vc = 0.5 * sqrt(fcu / gammaC) * b * d / 1000.0 // kN (simplified)

        // Axial load effect
        val vcAdjusted = if (Nu > 0) {
            vc * (1.0 + Nu / (6.0 * b * d * fcu / 1e6))
        } else vc

        val vuCapacity = if (Vu <= vcAdjusted) {
            // Minimum shear reinforcement
            val sMax = min(d / 2.0, 300.0)
            Triple(8.0, sMax, vcAdjusted)
        } else {
            // Calculate required steel shear reinforcement
            val vsRequired = Vu - vcAdjusted
            // Vs = (Asv * fy * d) / (s * gammaS)
            // Try 8mm stirrups
            val stirrupDia = 8.0
            val asv = 2.0 * 3.14159 * (stirrupDia / 2.0).pow(2) // 2 legs
            val s = (asv * fy / gammaS * d / vsRequired) // spacing in mm
            val sClamped = min(max(s, 75.0), min(d, 400.0))

            val vsActual = asv * fy / gammaS * d / sClamped / 1000.0
            val totalVc = vcAdjusted + vsActual
            Triple(stirrupDia, sClamped, totalVc)
        }

        return vuCapacity
    }

    private fun calculateMomentCapacity(
        b: Double, d: Double, As: Double, fcu: Double, fy: Double, code: DesignCode
    ): Double {
        if (As <= 0) return 0.0
        val gammaC = if (code == DesignCode.ECP) GAMMA_C_ECP else GAMMA_C_ACI
        val gammaS = if (code == DesignCode.ECP) GAMMA_S_ECP else 1.0
        val rho = As / (b * d)
        val alpha = 0.67 / gammaC
        val a = As * fy / gammaS / (0.67 * fcu / gammaC * b)
        val phi = if (code == DesignCode.ECP) 1.0 else 0.9
        return phi * As * fy / gammaS * (d - a / 2.0) / 1e6 // kN.m
    }
}

/** Helper data class for returning multiple values */
private data class Tuple4<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)