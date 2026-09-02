package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.*
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.SteelTables

/**
 * Engine to find the most efficient (lightest) steel section from a library.
 */
object SteelOptimizationEngine {

    fun suggestLightestSection(
        calc: CalculatorEngine,
        memberType: SteelMemberType,
        inputs: SteelInputs,
        code: com.civileg.core.calculations.entities.DesignCode,
        library: List<SteelTables.SectionProperties>
    ): SteelTables.SectionProperties? {
        return library.sortedBy { it.weight }.firstOrNull { sec ->
            // Convert SectionProperties to SteelSectionType for calculation
            val steelSec = convertToSteelSectionType(sec)
            val res = calc.calculateSteelMember(
                steelSec,
                memberType,
                inputs,
                com.civileg.app.utils.CalculatorEngine.AppDesignCode.fromDomain(code)
            )
            res.isSafe
        }
    }

    private fun convertToSteelSectionType(sec: SteelTables.SectionProperties): SteelSectionType {
        return if (sec.name.startsWith("IPE") || sec.name.startsWith("HE")) {
            SteelSectionType.ISection(sec.depth, sec.width, sec.tf, sec.tw, SteelGrade.ST37, sec.name)
        } else if (sec.name.startsWith("UPN")) {
            SteelSectionType.CSection(sec.depth, sec.width, sec.tf, sec.tw, SteelGrade.ST37, sec.name)
        } else if (sec.name.startsWith("L")) {
            SteelSectionType.LSection(sec.depth, sec.width, sec.tf, SteelGrade.ST37, sec.name)
        } else {
            SteelSectionType.RHS(sec.width, sec.depth, sec.tw, SteelGrade.ST37, sec.name)
        }
    }
}
