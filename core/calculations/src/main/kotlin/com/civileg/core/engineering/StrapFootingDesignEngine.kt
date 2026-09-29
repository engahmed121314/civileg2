package com.civileg.core.engineering

import kotlin.math.*

/**
 * محرك تصميم القواعد الشريطية (Strap Footing) - Unified Engine
 * يستخدم عندما تكون القاعدة الخارجية عند حد الجار وتنتقل محصلتها عبر كاميرا رابطة للقاعدة الداخلية.
 */
object StrapFootingDesignEngine {

    data class Inputs(
        val column1Load: Double,        // kN (External/Edge column)
        val column2Load: Double,        // kN (Internal column)
        val distanceBetweenColumns: Double, // mm (Center to center)
        val column1Width: Double,       // mm
        val column1Depth: Double,       // mm
        val column2Width: Double,       // mm
        val column2Depth: Double,       // mm
        val soilBearingCapacity: Double, // kPa
        val fcu: Double,
        val fy: Double,
        val strapBeamWidth: Double = 400.0,
        val deadLoadFactor: Double = 1.4,
        val liveLoadFactor: Double = 1.6
    )

    data class Result(
        val footing1: FootingDimension,
        val footing2: FootingDimension,
        val strapBeam: StrapBeamResult,
        val reactions: Pair<Double, Double>, // R1, R2
        val isSafe: Boolean,
        val warnings: List<String> = emptyList(),
        val codeNotes: List<String> = emptyList()
    )

    data class FootingDimension(
        val width: Double,
        val length: Double,
        val thickness: Double,
        val reinforcement: ReinforcementResult
    )

    data class StrapBeamResult(
        val width: Double,
        val depth: Double,
        val topReinforcement: ReinforcementResult,
        val bottomReinforcement: ReinforcementResult,
        val maxMoment: Double, // kN.m
        val maxShear: Double   // kN
    )

    data class ReinforcementResult(
        val numberOfBars: Int,
        val barDiameter: Double,
        val isSafe: Boolean = true
    )

    fun design(inputs: Inputs): Result {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // 1. حساب المسافات
        val L_center = inputs.distanceBetweenColumns / 1000.0 // Center to center distance (m)
        val x1 = inputs.column1Depth / 2000.0 // Distance from edge to col1 center (m)
        
        // 2. فرض أبعاد أولية للقاعدة الخارجية (Footing 1) لتقدير رد الفعل
        val L1 = (x1 * 2.0).coerceAtLeast(1.5) 
        val S = L_center - x1 // Distance between reactions R1 and Col2
        
        // 3. حساب ردود الفعل (Equilibrium)
        // ΣM at Col2 = 0 -> R1 * S = P1 * L_center
        val R1 = inputs.column1Load * L_center / S
        val R2 = (inputs.column1Load + inputs.column2Load) - R1
        
        // 4. حساب أبعاد القواعد (Service Loads)
        val A1_req = (R1 * 1.1) / inputs.soilBearingCapacity
        val B1 = A1_req / L1
        
        val A2_req = (R2 * 1.1) / inputs.soilBearingCapacity
        val L2 = sqrt(A2_req)
        val B2 = L2

        val footing1 = FootingDimension(
            width = ceil(B1 * 1000 / 50) * 50,
            length = ceil(L1 * 1000 / 50) * 50,
            thickness = 800.0,
            reinforcement = ReinforcementResult(10, 16.0)
        )
        
        val footing2 = FootingDimension(
            width = ceil(B2 * 1000 / 50) * 50,
            length = ceil(L2 * 1000 / 50) * 50,
            thickness = 800.0,
            reinforcement = ReinforcementResult(10, 16.0)
        )

        // 5. تصميم الكاميرا الرابطة (Strap Beam)
        val maxMoment = inputs.column1Load * (L_center - S)
        val maxShear = R1 - inputs.column1Load

        val strapBeam = StrapBeamResult(
            width = inputs.strapBeamWidth,
            depth = 1000.0,
            topReinforcement = ReinforcementResult(8, 22.0),
            bottomReinforcement = ReinforcementResult(4, 16.0),
            maxMoment = maxMoment,
            maxShear = maxShear
        )

        codeNotes.add("Strap Footing Design - Unified Engine")
        codeNotes.add("R1 = ${"%.1f".format(R1)} kN, R2 = ${"%.1f".format(R2)} kN")

        return Result(
            footing1 = footing1,
            footing2 = footing2,
            strapBeam = strapBeam,
            reactions = R1 to R2,
            isSafe = true,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }
}
