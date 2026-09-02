package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.*
import com.civileg.core.calculations.entities.*
import com.civileg.core.engineering.CodeRuleEngine
import com.civileg.core.engineering.ConcreteMaterial
import com.civileg.core.engineering.SteelMaterial
import com.civileg.core.engineering.UnifiedBeamTorsion
import kotlin.math.*

object BeamDesignEngine {

    fun designBeam(
        b: Double, h: Double, span: Double,
        deadLoad: Double, liveLoad: Double,
        fcu: Double, fy: Double,
        preferredDia: Int,
        code: DesignCode,
        supportType: String,
        cover: Double = 50.0,
        slabThickness: Double = 0.0,
        slabType: String = "",
        tributaryWidth: Double = 0.0,
        wallThickness: Double = 0.0,
        wallHeight: Double = 0.0,
        floorFinishLoad: Double = 2.0,
        plasterLoad: Double = 0.5,
        torsionalMoment: Double = 0.0,
        flangeWidth: Double = 0.0,
        flangeThickness: Double = 0.0,
    ): BeamDesignResult {
        val steps = mutableListOf<AppCalculationStep>()
        val d = h - cover - 12.0
        val wu = code.getDeadLoadFactor() * deadLoad + code.getLiveLoadFactor() * liveLoad

        var needsTorsionDesign = false
        var torsionalThreshold = 0.0
        var torsionalLongitudinalBars = ""
        var torsionalReinforcement = ""
        var torsionalStirrupSpacing = 0.0
        var torsionIsSafe = true

        if (torsionalMoment != 0.0) {
            val concrete = ConcreteMaterial(fcuMpa = fcu)
            val steel = SteelMaterial(yieldMpa = fy, ultimateMpa = fy * 1.5)
            val engine = when (code) {
                DesignCode.ECP -> CodeRuleEngine.forEcp(concrete, steel)
                DesignCode.ACI, DesignCode.SBC -> CodeRuleEngine.forAci(concrete, steel)
            }
            val torsionOutcome = engine.designBeamTorsion(
                b = b, h = h, coverMm = cover, tuKnm = torsionalMoment, dMm = d
            )
            needsTorsionDesign = torsionOutcome.torsionState != UnifiedBeamTorsion.TorsionState.NONE
            torsionalThreshold = torsionOutcome.tuThKnm
            torsionalLongitudinalBars = "${torsionOutcome.longitudinalBars}Ø${torsionOutcome.longitudinalDiaMm.toInt()}"
            torsionalReinforcement = if (needsTorsionDesign) {
                "Ø${torsionOutcome.stirrupDiaMm.toInt()} @ ${torsionOutcome.stirrupSpacingMm.toInt()} mm c/c"
            } else {
                "No torsion reinforcement required"
            }
            torsionalStirrupSpacing = torsionOutcome.stirrupSpacingMm
            torsionIsSafe = torsionOutcome.isSafe
        }

        return BeamDesignResult(
            beamWidth = b,
            beamDepth = h,
            span = span,
            clearSpan = span - 0.3,
            effectiveDepth = d,
            fcu = fcu,
            fy = fy,
            deadLoad = deadLoad,
            liveLoad = liveLoad,
            designCode = code,
            supportType = supportType,
            sectionType = "RECTANGULAR",
            ultimateLoad = wu,
            calculationSteps = steps,
            isSafe = true,
            torsionalMoment = torsionalMoment,
            torsionalThreshold = torsionalThreshold,
            needsTorsionDesign = needsTorsionDesign,
            torsionalReinforcement = torsionalReinforcement,
            torsionIsSafe = torsionIsSafe,
            torsionalStirrupSpacing = torsionalStirrupSpacing,
            torsionalLongitudinalBars = torsionalLongitudinalBars,
        )
    }
}
