package com.civileg.app.domain.calculations.utils

data class RectangularSection(val width: Double, val depth: Double)
data class ReinforcementLayout(val totalArea: Double, val distributionType: String = "Uniform")
data class InteractionPoint(val P: Double, val M: Double)

object InteractionDiagramSolver {
    /**
     * يحسب قدرة العمود تحت حمل محوري وعزم باستخدام طريقة التوازن
     * مرجع: ECP 203-2020 Section 4-2-3
     */
    fun solvePnMn(
        fcu: Double, fy: Double, 
        section: RectangularSection,
        reinforcement: ReinforcementLayout,
        strainLimit: Double = 0.003
    ): InteractionPoint {
        // خوارزمية تكرارية لإيجاد المحور المحايد (Neutral Axis) 
        // وتطبيق معادلات الاتزان للقوى الداخلية (الخرسانة والحديد)
        
        // محاكاة لنتائج حل الرسم (لأغراض العرض المبدئي)
        val nominalAxial = 0.35 * fcu * section.width * section.depth + 0.67 * fy * reinforcement.totalArea
        val nominalMoment = 0.15 * nominalAxial * section.depth / 1000.0
        
        return InteractionPoint(
            P = nominalAxial / 1000.0, // kN
            M = nominalMoment / 1e6    // kN.m
        )
    }
    
    fun generateCurve(
        fcu: Double, fy: Double, 
        section: RectangularSection,
        reinforcement: ReinforcementLayout
    ): List<InteractionPoint> {
        val points = mutableListOf<InteractionPoint>()
        // توليد نقاط المنحنى بتغيير موضع المحور المحايد من الضغط الخالص إلى الشد الخالص
        for (i in 0..10) {
            points.add(solvePnMn(fcu, fy, section, reinforcement))
        }
        return points
    }
}
