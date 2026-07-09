package com.civileg.app.domain.calculations.utils

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.pow

data class RectangularSection(val width: Double, val depth: Double)

/**
 * تخطيط التسليح المدخل لحساب منحنى التفاعل
 */
data class InteractionReinforcementInput(
    val totalArea: Double,
    val distributionType: String = "Uniform",
    val cover: Double = 40.0,
    val numBarsPerFace: Int = 3
)

data class InteractionPoint(val P: Double, val M: Double)

object InteractionDiagramSolver {
    private const val GAMMA_C = 1.5
    private const val GAMMA_S = 1.15
    private const val EPSILON_CU = 0.003  // انفعال الخرسانة الأقصى
    private const val ES = 200000.0       // معامل مرونة الحديد MPa

    /**
     * حساب نقطة واحدة على منحنى التفاعل بطريقة التوازن
     * ECP 203-2020 البند 4-2-3
     *
     * @param c عمق المحور المحايد من الضغط الأقصى (mm)
     */
    fun solvePnMn(
        fcu: Double, fy: Double,
        section: RectangularSection,
        reinforcement: InteractionReinforcementInput,
        strainLimit: Double = EPSILON_CU,
        c: Double = section.depth * 0.5  // عمق المحور المحايد
    ): InteractionPoint {
        val b = section.width
        val h = section.depth

        // معامل كتلة الإجهاد المكافئ (ECP 203)
        val beta1 = if (fcu <= 30.0) 0.85 else (0.85 - 0.05 * (fcu - 30.0) / 5.0).coerceAtLeast(0.65)
        val a = beta1 * c  // عمق كتلة الإجهاد المكافئ

        // إجهاد الخرسانة التصميمي
        val concreteStress = 0.67 * fcu / GAMMA_C

        // قوة ضغط الخرسانة
        val Cc = concreteStress * a * b

        // توزيع الحديد: نفترض توزيع منتظم على الوجوه الأربعة
        val d_top = reinforcement.cover + 10.0  // عمق مركز الحديد العلوي
        val d_bot = h - d_top                   // عمق مركز الحديد السفلي
        val d_side = h / 2.0                    // الحديد الجانبي في المنتصف

        val As_total = reinforcement.totalArea
        val n_faces = 4
        val As_per_face = As_total / n_faces

        // حساب انفعال وإجهاد كل طبقة من الحديد
        // εs = εcu × (d - c) / c
        fun steelForce(d_bar: Double, As: Double): Double {
            val epsilon_s = strainLimit * (d_bar - c) / c.coerceAtLeast(1.0)
            val fs = (ES * epsilon_s).coerceIn(-fy / GAMMA_S, fy / GAMMA_S)
            return As * fs
        }

        // الحديد السفلي (شد أو ضغط حسب موقع المحور المحايد)
        val Fs_bot = steelForce(d_bot, As_per_face)
        // الحديد العلوي (عادة ضغط)
        val Fs_top = steelForce(d_top, As_per_face)
        // الحديد الجانبي
        val Fs_side = steelForce(d_side, 2.0 * As_per_face)

        // محصلة القوى المحورية (الضغط موجب)
        val Pn = Cc + Fs_top + Fs_side + Fs_bot

        // محصلة العزوم حول محور المنتصف
        // Mn = Cc*(h/2 - a/2) + Fs_top*(h/2 - d_top) + Fs_bot*(d_bot - h/2) + Fs_side*(d_side - h/2)
        val Mn = Cc * (h / 2.0 - a / 2.0)
            + Fs_top * (h / 2.0 - d_top)
            + Fs_bot * (d_bot - h / 2.0)
            + Fs_side * (d_side - h / 2.0)

        return InteractionPoint(
            P = Pn / 1000.0,  // kN
            M = abs(Mn) / 1e6  // kN.m
        )
    }

    /**
     * توليد منحنى التفاعل الكامل (P-M Interaction Diagram)
     * بتغيير عمق المحور المحايد من الضغط الخالص (c كبير) إلى الشد الخالص (c صغير)
     *
     * النقاط الرئيسية:
     * 1. الضغط الخالص (c = ∞ تقريباً)
     * 2. توازن (c = cb)
     * 3. الشد الخالص مع حمل محوري صفري
     *
     * @return قائمة نقاط المنحنى (P in kN, M in kN.m)
     */
    fun generateCurve(
        fcu: Double, fy: Double,
        section: RectangularSection,
        reinforcement: InteractionReinforcementInput
    ): List<InteractionPoint> {
        val points = mutableListOf<InteractionPoint>()
        val h = section.depth
        val d = h - reinforcement.cover - 10.0

        // نقطة 1: الضغط الخالص (c = 3h)
        points.add(solvePnMn(fcu, fy, section, reinforcement, c = 3.0 * h))

        // نقطة 2: ضغط مع عزم صغير (c = 1.5h)
        points.add(solvePnMn(fcu, fy, section, reinforcement, c = 1.5 * h))

        // نقطة 3: نقطة التوازن (c = cb)
        // cb = d * εcu / (εcu + εy) حيث εy = fy/Es
        val epsilon_y = fy / ES
        val cb = d * EPSILON_CU / (EPSILON_CU + epsilon_y)
        points.add(solvePnMn(fcu, fy, section, reinforcement, c = cb))

        // نقاط بين التوازن والشد
        val c_values = listOf(
            cb * 0.8, cb * 0.6, cb * 0.4, cb * 0.3,
            cb * 0.2, cb * 0.15, cb * 0.1, cb * 0.05
        )
        for (c_val in c_values) {
            points.add(solvePnMn(fcu, fy, section, reinforcement, c = c_val))
        }

        // نقطة الشد الخالص التقريبية (c = 10mm صغير جداً)
        points.add(solvePnMn(fcu, fy, section, reinforcement, c = 10.0))

        return points
    }

    /**
     * حساب نقطة التوازن (Balanced Point)
     * عندما يصل الخرسانة والحديد للتشبع معاً
     */
    fun getBalancedPoint(
        fcu: Double, fy: Double,
        section: RectangularSection,
        reinforcement: InteractionReinforcementInput
    ): InteractionPoint {
        val d = section.depth - reinforcement.cover - 10.0
        val epsilon_y = fy / ES
        val cb = d * EPSILON_CU / (EPSILON_CU + epsilon_y)
        return solvePnMn(fcu, fy, section, reinforcement, c = cb)
    }
}