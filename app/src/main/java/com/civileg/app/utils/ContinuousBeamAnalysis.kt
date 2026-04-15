package com.civileg.app.utils

import kotlin.math.pow

/**
 * محرك تحليل إنشائي للكمرات المستمرة
 */
class ContinuousBeamAnalysis {

    data class Span(
        val length: Double,
        val load: Double, // kN/m (Uniform Distributed Load)
        val inertia: Double = 1.0 // Relative I
    )

    data class AnalysisResult(
        val moments: List<Double>, // Moments at supports (kN.m)
        val shearForces: List<Double>, // Max shear at each span (kN)
        val reactions: List<Double>, // Reaction at each support (kN)
        val points: List<DiagramPoint> // Points for drawing BMD/SFD
    )

    data class DiagramPoint(
        val x: Double,
        val moment: Double,
        val shear: Double
    )

    /**
     * حل الكمرة المستمرة باستخدام معادلة العزوم الثلاثة (Three Moment Equation)
     * يفترض حالياً ركائز بسيطة (Hinged/Roller)
     */
    fun solve(spans: List<Span>): AnalysisResult {
        val n = spans.size
        val moments = DoubleArray(n + 1) { 0.0 } // M0, M1, ... Mn
        
        if (n > 1) {
            // بناء مصفوفة المعادلات (A * M = B)
            // Equation for support i: M_{i-1}*L_i + 2*M_i*(L_i + L_{i+1}) + M_{i+1}*L_{i+1} = -w_i*L_i^3/4 - w_{i+1}*L_{i+1}^3/4
            val matrix = Array(n - 1) { DoubleArray(n - 1) }
            val constants = DoubleArray(n - 1)
            
            for (i in 0 until n - 1) {
                val l1 = spans[i].length
                val l2 = spans[i + 1].length
                val w1 = spans[i].load
                val w2 = spans[i + 1].load
                
                if (i > 0) matrix[i][i - 1] = l1
                matrix[i][i] = 2 * (l1 + l2)
                if (i < n - 2) matrix[i][i + 1] = l2
                
                constants[i] = -(w1 * l1.pow(3) / 4.0) - (w2 * l2.pow(3) / 4.0)
            }
            
            val solvedMoments = solveLinearSystem(matrix, constants)
            for (i in 0 until n - 1) {
                moments[i + 1] = solvedMoments[i]
            }
        }

        // حساب قوى القص وردود الأفعال ونقاط الرسم
        val points = mutableListOf<DiagramPoint>()
        val reactions = DoubleArray(n + 1) { 0.0 }
        val maxShears = mutableListOf<Double>()
        
        var currentX = 0.0
        for (i in 0 until n) {
            val l = spans[i].length
            val w = spans[i].load
            val mLeft = moments[i]
            val mRight = moments[i+1]
            
            // Simple Span Shear + Moment Difference Shear
            val vLeft = (w * l / 2.0) + (mLeft - mRight) / l
            val vRight = (w * l / 2.0) - (mLeft - mRight) / l
            
            reactions[i] += vLeft
            reactions[i+1] += vRight
            maxShears.add(maxOf(vLeft, vRight))
            
            // توليد نقاط الرسم لكل بحر (كل 0.1 متر)
            var stepX = 0.0
            while (stepX <= l) {
                val mx = mLeft * (1 - stepX/l) + mRight * (stepX/l) + (w * stepX / 2.0) * (l - stepX)
                val vx = vLeft - w * stepX
                points.add(DiagramPoint(currentX + stepX, mx, vx))
                stepX += 0.1
            }
            currentX += l
        }

        return AnalysisResult(moments.toList(), maxShears, reactions.toList(), points)
    }

    private fun solveLinearSystem(matrix: Array<DoubleArray>, constants: DoubleArray): DoubleArray {
        val n = constants.size
        // Thomas Algorithm for Tridiagonal Matrix
        val a = DoubleArray(n) { if (it == 0) 0.0 else matrix[it][it - 1] }
        val b = DoubleArray(n) { matrix[it][it] }
        val c = DoubleArray(n) { if (it == n - 1) 0.0 else matrix[it][it + 1] }
        val d = constants.copyOf()
        
        for (i in 1 until n) {
            val m = a[i] / b[i - 1]
            b[i] = b[i] - m * c[i - 1]
            d[i] = d[i] - m * d[i - 1]
        }
        
        val x = DoubleArray(n)
        x[n - 1] = d[n - 1] / b[n - 1]
        for (i in n - 2 downTo 0) {
            x[i] = (d[i] - c[i] * x[i + 1]) / b[i]
        }
        return x
    }
}
