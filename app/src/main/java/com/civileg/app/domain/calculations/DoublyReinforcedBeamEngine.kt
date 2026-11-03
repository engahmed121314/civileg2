package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.aci.ACIDoublyReinforcedBeam
import com.civileg.app.domain.calculations.ecp.DoublyReinforcedBeamResult
import com.civileg.app.domain.calculations.ecp.ECPDoublyReinforcedBeam
import com.civileg.app.domain.calculations.sbc.SBCDoublyReinforcedBeam

/**
 * Sealed class تُغلّف محركات الكمرة المضاعفة الثلاثة بنوع آمن بدل Any.
 *
 * الاستخدام:
 * ```kotlin
 * val engine = CalculationFactory.getDoublyReinforcedBeamDesign(code)
 * val result = engine.design(mu, b, h, fcu, fy)
 * ```
 *
 * جميع الأنواع تعيد [DoublyReinforcedBeamResult] الموحّد.
 * المعامل fcu دائماً مقاومة المكعب (MPa) — التحويل لـ fc' يجري داخل ACI engine.
 */
sealed class DoublyReinforcedBeamEngine {

    /**
     * تصميم الكمرة المضاعفة بالمعاملات المعطاة.
     *
     * @param mu       عزم التصميم (kN.m)
     * @param b        عرض الكمرة b (mm)
     * @param h        العمق الكلي h (mm)
     * @param fcu      مقاومة الخرسانة المكعبة fcu (MPa)
     * @param fy       إجهاد خضوع الحديد fy (MPa)
     * @param cover    الغطاء الخرساني (mm), default 50
     * @param tensionBarDia   قطر حديد الشد المبدئي (mm int), default 20
     * @param compBarDia      قطر حديد الضغط المبدئي (mm int), default 16
     * @return [DoublyReinforcedBeamResult] بنتائج التصميم وحالة الأمان
     */
    abstract fun design(
        mu: Double,
        b: Double,
        h: Double,
        fcu: Double,
        fy: Double,
        cover: Double = 50.0,
        tensionBarDia: Int = 20,
        compBarDia: Int = 16
    ): DoublyReinforcedBeamResult

    /** محرك ECP 203-2020 */
    class ECP(private val engine: ECPDoublyReinforcedBeam) : DoublyReinforcedBeamEngine() {
        override fun design(mu: Double, b: Double, h: Double, fcu: Double, fy: Double,
                            cover: Double, tensionBarDia: Int, compBarDia: Int) =
            engine.design(mu, b, h, fcu, fy, cover, tensionBarDia, compBarDia)
    }

    /**
     * محرك ACI 318-19 — يُحوّل fcu (مكعب) إلى fc' (أسطوانة) داخلياً (fc'=0.8·fcu)
     * لأن ACI يعمل بمقاومة الأسطوانة.
     */
    class ACI(private val engine: ACIDoublyReinforcedBeam) : DoublyReinforcedBeamEngine() {
        override fun design(mu: Double, b: Double, h: Double, fcu: Double, fy: Double,
                            cover: Double, tensionBarDia: Int, compBarDia: Int): DoublyReinforcedBeamResult {
            val fcPrime = 0.8 * fcu   // تحويل مقاومة المكعب → أسطوانة حسب ACI
            return engine.design(mu, b, h, fcPrime, fy, cover, tensionBarDia, compBarDia)
        }
    }

    /** محرك SBC 304-2018 */
    class SBC(private val engine: SBCDoublyReinforcedBeam) : DoublyReinforcedBeamEngine() {
        override fun design(mu: Double, b: Double, h: Double, fcu: Double, fy: Double,
                            cover: Double, tensionBarDia: Int, compBarDia: Int) =
            engine.design(mu, b, h, fcu, fy, cover, tensionBarDia, compBarDia)
    }
}
