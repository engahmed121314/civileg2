package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.*
import com.civileg.core.calculations.entities.*
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
            isSafe = true
        )
    }
}
