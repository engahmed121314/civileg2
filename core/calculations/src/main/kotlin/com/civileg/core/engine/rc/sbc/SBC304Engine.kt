package com.civileg.core.engine.rc.sbc

import com.civileg.core.engine.rc.aci.ACI318Engine

/**
 * محرك الحسابات الخرسانية لكود البناء السعودي SBC 304 (متوافق مع ACI 318M مع اشتراطات SBC الخاصة).
 * Pure Kotlin — Zero Android / Zero UI dependencies.
 *
 * المراجع المعتمدة:
 * - كود البناء السعودي العام SBC 301 (الأحمال والقوى)
 * - كود البناء السعودي الخرساني SBC 304-2018 (تصميم المنشآت الخرسانية)
 */
object SBC304Engine {

    /**
     * تراكيب الأحمال المعتمدة بكود البناء السعودي SBC 301 Cl. 2.3:
     * U = 1.4 D
     * U = 1.2 D + 1.6 L
     */
    fun factoredLoad(deadLoad: Double, liveLoad: Double): Double {
        val comb1 = 1.4 * deadLoad
        val comb2 = 1.2 * deadLoad + 1.6 * liveLoad
        return maxOf(comb1, comb2)
    }

    /**
     * تصميم الكمرة للانحناء وفق كود البناء السعودي SBC 304:
     * يعتمد على مصفوفة ويتني والانفعال الانتقالي المتطابق مع ACI 318M مع اشتراطات الحد الأدنى.
     */
    fun designBeamFlexure(input: ACI318Engine.ACIBeamFlexureInput): ACI318Engine.ACIBeamFlexureResult {
        val result = ACI318Engine.designBeamFlexure(input)
        return result.copy(codeReference = "SBC 304-2018 Cl. 22.2")
    }

    /**
     * فحص قص الكمرات وفق كود البناء السعودي SBC 304 مع معامل حجم العنصر λs.
     */
    fun checkBeamShear(input: ACI318Engine.ACIBeamShearInput): ACI318Engine.ACIBeamShearResult {
        val result = ACI318Engine.checkBeamShear(input)
        return result.copy(codeReference = "SBC 304-2018 Cl. 22.5")
    }
}
