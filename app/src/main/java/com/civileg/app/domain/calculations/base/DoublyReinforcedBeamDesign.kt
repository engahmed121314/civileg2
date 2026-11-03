package com.civileg.app.domain.calculations.base

import com.civileg.app.domain.entities.DoublyReinforcedResult

/**
 * واجهة موحدة لتصميم الكمرة مضاعفة التسليح (Doubly-Reinforced Beam)
 * تُعيد [DoublyReinforcedResult] بشكل آمن بدل Any حسب ADR-010.
 *
 * تُطبَّق في: [com.civileg.app.domain.calculations.ecp.ECPDoublyReinforcedBeam]
 *             [com.civileg.app.domain.calculations.aci.ACIDoublyReinforcedBeam]
 *             [com.civileg.app.domain.calculations.sbc.SBCDoublyReinforcedBeam]
 */
interface DoublyReinforcedBeamDesign {

    /**
     * @param designMoment  عزم التصميم (kN.m)
     * @param width         عرض الكمرة b (mm)
     * @param depth         العمق الكلي h (mm)
     * @param fcu           مقاومة الخرسانة المكعبة fcu (MPa)
     * @param fy            إجهاد خضوع الحديد fy (MPa)
     * @param compressionSteelDia  قطر حديد الضغط المفضَّل (mm), default 16
     * @param dPrime        المسافة من وجه الضغط لمركز حديد الضغط d' (mm), default 50
     */
    fun calculateDoublyReinforcedBeam(
        designMoment: Double,
        width: Double,
        depth: Double,
        fcu: Double,
        fy: Double,
        compressionSteelDia: Double = 16.0,
        dPrime: Double = 50.0
    ): DoublyReinforcedResult
}
