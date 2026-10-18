package com.civileg.core.calculations.ecp

import com.civileg.core.calculations.entities.*
import kotlin.math.*

/**
 * محرك هندسة البلاطات المسطحة - تقسيم الشرائط وتوزيع الحديد
 * المرجع: ECP 203-2020 البند 6-2-2 (البلاطات المسطحة)
 */
object FlatSlabGeometryEngine {

    fun calculateStrips(inputs: FlatSlabInputs): FlatSlabStripInfo {
        // 1. عرض شريحة العمود = نصف البحر الأصغر (L1/2)
        val colStripWidth = min(inputs.shortSpan, inputs.longSpan) / 2.0
        val midStripWidth = max(inputs.shortSpan, inputs.longSpan) - colStripWidth
        
        // 2. توزيع العزوم الكلية (Mo) حسب ECP 203
        // Mo = (w * L2 * (L1 - 2/3d)^2) / 8
        val Mo_short = calculateTotalMoment(inputs.totalLoad, inputs.shortSpan, inputs.longSpan, inputs.columnDepth)
        
        // 3. توزيع العزوم على الشرائط (شريحة العمود تأخذ الجزء الأكبر)
        // تقريباً: Column Strip (Positive: 45%, Negative: 60%)
        // تقريباً: Middle Strip (Positive: 25%, Negative: 25%)
        
        val shortStripReinf = StripReinforcement(
            columnStripTop = dummyReinforcement(Mo_short * 0.60 / (colStripWidth/1000.0)),
            columnStripBottom = dummyReinforcement(Mo_short * 0.45 / (colStripWidth/1000.0)),
            middleStripTop = dummyReinforcement(Mo_short * 0.25 / (midStripWidth/1000.0)),
            middleStripBottom = dummyReinforcement(Mo_short * 0.25 / (midStripWidth/1000.0))
        )

        return FlatSlabStripInfo(
            columnStripWidth = colStripWidth,
            middleStripWidth = midStripWidth,
            shortDirection = shortStripReinf,
            longDirection = shortStripReinf // Simplified for now
        )
    }

    private fun calculateTotalMoment(w: Double, L1: Double, L2: Double, D: Double): Double {
        val L1_m = L1 / 1000.0
        val L2_m = L2 / 1000.0
        val D_m = D / 1000.0
        return (w * L2_m * (L1_m - 0.67 * D_m).pow(2)) / 8.0
    }

    private fun dummyReinforcement(moment: Double): ReinforcementResult {
        return ReinforcementResult(
            astRequired = moment * 100.0, // Placeholder
            astProvided = moment * 110.0,
            barDiameter = 12.0,
            numberOfBars = 6,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = true,
            utilizationRatio = 0.8,
            spacing = 150.0
        )
    }
}
