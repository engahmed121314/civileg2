package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.*
import com.civileg.app.utils.SteelTables
import kotlin.math.*

/**
 * تصميم العناصر المعدنية في الإطار - اختيار القطاع الأمثل من المكتبة
 * يدعم ECP 205, AISC 360, SBC 304
 */
object SteelFrameDesign {

    /**
     * تصميم جميع الأعضاء المعدنية في الإطار
     */
    fun designAllSteelMembers(
        members: List<FrameMember>,
        memberForces: List<MemberEndForces>,
        memberDiagrams: List<MemberDiagram>,
        code: DesignCode,
        steelFy: Double = 355.0
    ): List<SteelMemberDesignResult> {
        return members.filter { it.materialType == FrameMaterialType.Steel }.map { member ->
            val forces = memberForces.find { it.memberId == member.id }
            val diagram = memberDiagrams.find { it.memberId == member.id }
            designSingleMember(member, forces, diagram, code, steelFy)
        }
    }

    /**
     * تصميم عضو معدني واحد - اختيار القطاع الأمثل
     */
    fun designSingleMember(
        member: FrameMember,
        forces: MemberEndForces?,
        diagram: MemberDiagram?,
        code: DesignCode,
        steelFy: Double
    ): SteelMemberDesignResult {
        val maxM = diagram?.maxMoment ?: forces?.maxMoment ?: 0.0
        val maxV = diagram?.maxShear ?: forces?.maxShear ?: 0.0
        val Pu = forces?.axialForce ?: 0.0

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // Get all available sections sorted by weight (lightest first for optimization)
        val allSections = SteelTables.getAllSections()

        // Find the lightest section that satisfies all checks
        val bestSection = findOptimalSection(allSections, maxM, maxV, Pu, member.memberType, code, steelFy, warnings)

        if (bestSection == null) {
            // Fallback: pick the largest section and mark as unsafe
            val largest = allSections.maxByOrNull { it.weight } ?: SteelTables.ipeSections.last()
            codeNotes.add("لم يتم العثور على قطاع مناسب - يرجى مراجعة الأحمال")
            return SteelMemberDesignResult(
                memberId = member.id,
                memberName = member.name.ifEmpty { "عضو ${member.id}" },
                memberType = member.memberType,
                selectedSection = largest.name,
                sectionIx = largest.iy, sectionSx = largest.effectiveSx, sectionZx = largest.effectiveZx,
                sectionArea = largest.area, sectionWeight = largest.weight,
                maxMoment = maxM, maxShear = maxV, axialForce = Pu,
                flexuralCapacity = 0.0, shearCapacity = 0.0, axialCapacity = 0.0,
                flexuralUtilization = 999.0, shearUtilization = 999.0, axialUtilization = 999.0,
                combinedUtilization = 999.0, isSafe = false,
                warnings = warnings, codeNotes = codeNotes
            )
        }

        // Calculate capacities of selected section
        val phiB = if (code == DesignCode.SBC) 0.9 else 0.9
        val phiV = if (code == DesignCode.SBC) 0.9 else 0.9
        val phiC = when (code) {
            DesignCode.SBC -> 0.9
            else -> 0.9
        }

        // Flexural capacity: Mn = φ * Fy * Zx (plastic) or φ * Fy * Sx (elastic)
        val Sx_mm3 = bestSection.effectiveSx * 1e3  // cm³ -> mm³
        val Zx_mm3 = bestSection.effectiveZx * 1e3  // cm³ -> mm³
        val flexuralCap = phiB * steelFy * Zx_mm3 / 1e6  // N.mm -> kN.m

        // Shear capacity: Vn = 0.6 * Fy * Aw * φ
        val tw_mm = bestSection.tw
        val d_mm = bestSection.depth
        val shearCap = phiV * 0.6 * steelFy * d_mm * tw_mm / 1e3  // N -> kN

        // Axial capacity (simplified - ignoring buckling for now, using Euler with KL=1.0m)
        val A_mm2 = bestSection.area * 100.0  // cm² -> mm²
        val r_mm = bestSection.ry * 10.0      // cm -> mm
        val Kl = 3.0 // Assume KL = 3m default for preliminary design
        val slenderness = Kl * 1000.0 / r_mm
        val Fe = if (slenderness > 0) PI * PI * 200000.0 / (slenderness * slenderness) else steelFy
        val Fcr = if (Fe < steelFy) {
            val lambdaC = sqrt(steelFy / Fe)
            when {
                lambdaC <= 1.5 -> (0.658.pow(lambdaC.pow(2))) * steelFy
                else -> 0.877 * Fe
            }
        } else steelFy
        val axialCap = phiC * Fcr * A_mm2 / 1e3  // N -> kN

        val flexUtil = if (flexuralCap > 0) maxM / flexuralCap else 0.0
        val shearUtil = if (shearCap > 0) maxV / shearCap else 0.0
        val axialUtil = if (axialCap > 0) Pu / axialCap else 0.0

        // AISC interaction formula: Pu/φPn + M/φMn ≤ 1.0 (for axial + bending)
        val combinedUtil = if (Pu > 0.2 * axialCap) {
            Pu / axialCap + 8.0 / 9.0 * (maxM / flexuralCap)
        } else {
            Pu / (2.0 * axialCap) + maxM / flexuralCap
        }

        val isSafe = flexUtil <= 1.0 && shearUtil <= 1.0 && combinedUtil <= 1.0

        if (code == DesignCode.SBC) codeNotes.add("SBC 304-2018 - البند 8")
        else if (code == DesignCode.ECP) codeNotes.add("ECP 205-2007 - البند 6")
        else codeNotes.add("AISC 360-22 - Chapter H")

        return SteelMemberDesignResult(
            memberId = member.id,
            memberName = member.name.ifEmpty { "عضو ${member.id}" },
            memberType = member.memberType,
            selectedSection = bestSection.name,
            sectionIx = bestSection.iy,
            sectionSx = bestSection.effectiveSx,
            sectionZx = bestSection.effectiveZx,
            sectionArea = bestSection.area,
            sectionWeight = bestSection.weight,
            maxMoment = maxM,
            maxShear = maxV,
            axialForce = Pu,
            flexuralCapacity = flexuralCap,
            shearCapacity = shearCap,
            axialCapacity = axialCap,
            flexuralUtilization = flexUtil,
            shearUtilization = shearUtil,
            axialUtilization = axialUtil,
            combinedUtilization = combinedUtil,
            isSafe = isSafe,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    /**
     * البحث عن القطاع الأمثل (الأخف وزناً) الذي يحقق شروط الأمان
     */
    private fun findOptimalSection(
        sections: List<SteelTables.SectionProperties>,
        maxM: Double, maxV: Double, Pu: Double,
        memberType: FrameMemberType,
        code: DesignCode, fy: Double,
        warnings: MutableList<String>
    ): SteelTables.SectionProperties? {
        val phi = 0.9

        return sections.sortedBy { it.weight }.firstOrNull { sec ->
            // Flexural check
            val Zx = sec.effectiveZx * 1e3 // cm³ -> mm³
            val Mn = phi * fy * Zx / 1e6  // kN.m
            val flexOk = maxM <= Mn * 1.0 // utilization ≤ 1.0

            // Shear check
            val Vn = phi * 0.6 * fy * sec.depth * sec.tw / 1e3 // kN
            val shearOk = maxV <= Vn

            // Simplified axial check (short column)
            val A = sec.area * 100.0 // cm² -> mm²
            val Pn = phi * fy * A / 1e3 // kN
            val axialOk = Pu <= Pn

            flexOk && shearOk && axialOk
        }
    }
}