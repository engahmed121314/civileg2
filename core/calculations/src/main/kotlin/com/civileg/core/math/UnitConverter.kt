package com.civileg.core.math

/**
 * محول الوحدات الموحّد للنظام المترى والإمبراطوري.
 *
 * جميع عوامل التحويل مستمدة من [LengthUnit] و [ForceUnit] و [StressUnit] و [MomentUnit]
 * — لا توجد قيم حرفيّة مكشوفة هنا (no hardcoded constants).
 *
 * // المرجع: SI Brochure (BIPM); ASTM E380 / IEEE-ASTM SI 10 للتحويل للبريطاني-الأمريكي.
 */
object UnitConverter {

    /**
     * يحول قيمة طول من وحدة إلى أخرى.
     *
     * // ASTM E380 — تحويل الطول بين النظام المتري والإمبراطوري.
     */
    fun convertLength(value: Double, from: LengthUnit, to: LengthUnit): Double =
        Length(value, from).to(to)

    /**
     * يحول قيمة قوة من وحدة إلى أخرى.
     *
     * // SI Brochure / ASTM E380 — تحويل القوة بين النظام المتري والإمبراطوري.
     */
    fun convertForce(value: Double, from: ForceUnit, to: ForceUnit): Double =
        Force(value, from).to(to)

    /**
     * يحول قيمة إجهاد من وحدة إلى أخرى.
     *
     * // SI Brochure / ASTM E380 — تحويل الإجهاد بين النظام المتري والإمبراطوري.
     */
    fun convertStress(value: Double, from: StressUnit, to: StressUnit): Double =
        Stress(value, from).to(to)

    /**
     * يحول قيمة عزوم من وحدة إلى أخرى.
     *
     * // SI Brochure — تحويل العزوم بين النظام المتري والإمبراطوري.
     */
    fun convertMoment(value: Double, from: MomentUnit, to: MomentUnit): Double =
        Moment(value, from).to(to)

    /**
     * يحول [Length] إلى وحدة الهدف ويعيد كائن [Length] جديد.
     */
    fun convertLength(measure: Length, to: LengthUnit): Length =
        Length(measure.to(to), to)

    /**
     * يحول [Force] إلى وحدة الهدف ويعيد كائن [Force] جديد.
     */
    fun convertForce(measure: Force, to: ForceUnit): Force =
        Force(measure.to(to), to)

    /**
     * يحول [Stress] إلى وحدة الهدف ويعيد كائن [Stress] جديد.
     */
    fun convertStress(measure: Stress, to: StressUnit): Stress =
        Stress(measure.to(to), to)

    /**
     * يحول [Moment] إلى وحدة الهدف ويعيد كائن [Moment] جديد.
     */
    fun convertMoment(measure: Moment, to: MomentUnit): Moment =
        Moment(measure.to(to), to)
}
