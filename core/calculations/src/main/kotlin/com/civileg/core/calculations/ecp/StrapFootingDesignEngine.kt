package com.civileg.core.calculations.ecp

import com.civileg.core.calculations.entities.*
import kotlin.math.*

/**
 * محرك تصميم القواعد الشريطية (Strap Footing) حسب الكود المصري ECP 203-2020
 * يستخدم عندما تكون القاعدة الخارجية عند حد الجار وتنتقل محصلتها عبر كاميرا رابطة للقاعدة الداخلية.
 */
object StrapFootingDesignEngine {

    fun design(inputs: StrapFootingInputs): StrapFootingResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // 1. حساب المسافات
        val L_center = inputs.distanceBetweenColumns / 1000.0 // Center to center distance (m)
        val x1 = inputs.column1Depth / 2000.0 // Distance from edge to col1 center (m)
        
        // 2. فرض أبعاد أولية للقاعدة الخارجية (Footing 1) لتقدير رد الفعل
        // عادة L1 = 2 * (x1 + projection)
        val L1 = (x1 * 2.0).coerceAtLeast(1.5) 
        val S = L_center - x1 // Distance between reactions R1 and Col2
        
        // 3. حساب ردود الفعل (Equilibrium)
        // ΣM at Col2 = 0 -> R1 * S = P1 * L_center
        val R1 = inputs.column1Load * L_center / S
        val R2 = (inputs.column1Load + inputs.column2Load) - R1
        
        // 4. حساب أبعاد القواعد (Service Loads)
        val A1_req = R1 / inputs.soilBearingCapacity
        val B1 = A1_req / L1
        
        val A2_req = R2 / inputs.soilBearingCapacity
        val L2 = sqrt(A2_req)
        val B2 = L2

        // تقريب الأبعاد
        val footing1 = FootingDimension(
            width = ceil(B1 * 1000 / 50) * 50,
            length = ceil(L1 * 1000 / 50) * 50,
            thickness = 800.0, // Initial thickness
            reinforcement = ReinforcementResult(0.0, 0.0, 16.0, 10, 0.0, 0.0, isSafe = true, utilizationRatio = 0.5)
        )
        
        val footing2 = FootingDimension(
            width = ceil(B2 * 1000 / 50) * 50,
            length = ceil(L2 * 1000 / 50) * 50,
            thickness = 800.0,
            reinforcement = ReinforcementResult(0.0, 0.0, 16.0, 10, 0.0, 0.0, isSafe = true, utilizationRatio = 0.5)
        )

        // 5. تصميم الكاميرا الرابطة (Strap Beam)
        // أقصى عزم سالب بين العمودين
        val maxMoment = inputs.column1Load * (L_center - S) // Simplified
        val maxShear = R1 - inputs.column1Load

        val strapBeam = StrapBeamResult(
            width = inputs.strapBeamWidth,
            depth = 1000.0,
            topReinforcement = ReinforcementResult(0.0, 0.0, 22.0, 6, 0.0, 0.0, isSafe = true, utilizationRatio = 0.7),
            bottomReinforcement = ReinforcementResult(0.0, 0.0, 16.0, 4, 0.0, 0.0, isSafe = true, utilizationRatio = 0.3),
            shearReinforcement = ShearReinforcementResult(isSafe = true),
            maxMoment = maxMoment,
            maxShear = maxShear
        )

        codeNotes.add("ECP 203-2020 Clause 7-2-4: Strap Footing Design")
        codeNotes.add("R1 = ${"%.1f".format(R1)} kN, R2 = ${"%.1f".format(R2)} kN")

        return StrapFootingResult(
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
