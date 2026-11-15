package com.civileg.core.math

import com.civileg.core.math.SafeMath.requirePositive

/**
 * قياس الطول مع النوع الآمن للوحدة.
 *
 * // ECP 203 / ACI 318 / SBC 304 — الأطوال بالملليمتر كالمعتاد في التصميم الإنشائي.
 */
data class Length(
    val value: Double,
    val unit: LengthUnit
) {
    init {
        requirePositive(value, "Length value")
    }

    /** القيمة المحوّلة إلى الوحدة الأساسية (مم) */
    val toBaseMm: Double get() = value * unit.toBaseMm

    /**
     * يحول هذه القياسة إلى وحدة هدف معينة ويعيد القيمة الرقمية بتلك الوحدة.
     * // Astm E380 / SI Brochure — عامل التحويل دقيق.
     */
    fun to(targetUnit: LengthUnit): Double =
        toBaseMm / targetUnit.toBaseMm

    // --- خصائص ملائمة للوحدات الشائعة ---
    val mm: Double get() = to(LengthUnit.MM)
    val cm: Double get() = to(LengthUnit.CM)
    val m: Double get() = to(LengthUnit.M)
    val km: Double get() = to(LengthUnit.KM)
    val inch: Double get() = to(LengthUnit.IN)
    val foot: Double get() = to(LengthUnit.FT)
    val yard: Double get() = to(LengthUnit.YD)
    val mile: Double get() = to(LengthUnit.MILE)

    /** يضيف طولًا آخر بعد التحويل إلى الوحدة الأساسية. */
    operator fun plus(other: Length): Length =
        Length((toBaseMm + other.toBaseMm) / LengthUnit.MM.toBaseMm, LengthUnit.MM)

    /** يطرح طولًا آخر بعد التحويل إلى الوحدة الأساسية. */
    operator fun minus(other: Length): Length =
        Length((toBaseMm - other.toBaseMm) / LengthUnit.MM.toBaseMm, LengthUnit.MM)

    /** يضرب في معامل عددية (يعيد Length بنفس الوحدة). */
    operator fun times(scalar: Double): Length = Length(value * scalar, unit)

    /** يقسم على معامل عددية. */
    operator fun div(scalar: Double): Length = Length(value / scalar, unit)

    override fun toString(): String = "%.4f %s".format(value, unit.name)

    companion object {
        /** منشئات ملائمة لإنشاء قياسات طولة بوحدات شائعة. */
        fun mm(value: Double) = Length(value, LengthUnit.MM)
        fun cm(value: Double) = Length(value, LengthUnit.CM)
        fun m(value: Double) = Length(value, LengthUnit.M)
        fun km(value: Double) = Length(value, LengthUnit.KM)
        fun inch(value: Double) = Length(value, LengthUnit.IN)
        fun foot(value: Double) = Length(value, LengthUnit.FT)
        fun yard(value: Double) = Length(value, LengthUnit.YD)
        fun mile(value: Double) = Length(value, LengthUnit.MILE)
    }
}

/**
 * قياس القوة مع النوع الآمن للوحدة.
 *
 * // ECP 203 / ACI 318 / SBC 304 — القوى بالكيلونيوتن أو النيوتن.
 */
data class Force(
    val value: Double,
    val unit: ForceUnit
) {
    init {
        requirePositive(value, "Force value")
    }

    /** القيمة المحوّلة إلى الوحدة الأساسية (نيوتن) */
    val toBaseN: Double get() = value * unit.toBaseN

    /**
     * يحول هذه القياسة إلى وحدة هدف معينة ويعيد القيمة الرقمية بتلك الوحدة.
     */
    fun to(targetUnit: ForceUnit): Double =
        toBaseN / targetUnit.toBaseN

    // --- خصائص ملائمة ---
    val n: Double get() = to(ForceUnit.N)
    val kn: Double get() = to(ForceUnit.kN)
    val mn: Double get() = to(ForceUnit.MN)
    val gn: Double get() = to(ForceUnit.GN)
    val lbf: Double get() = to(ForceUnit.lbf)
    val kip: Double get() = to(ForceUnit.kip)
    val kgf: Double get() = to(ForceUnit.kgf)
    val tf: Double get() = to(ForceUnit.tf)

    /** يضيف قوة أخرى بعد التحويل إلى الوحدة الأساسية. */
    operator fun plus(other: Force): Force =
        Force((toBaseN + other.toBaseN) / ForceUnit.N.toBaseN, ForceUnit.N)

    /** يطرح قوة أخرى بعد التحويل إلى الوحدة الأساسية. */
    operator fun minus(other: Force): Force =
        Force((toBaseN - other.toBaseN) / ForceUnit.N.toBaseN, ForceUnit.N)

    /** يضرب في معامل عددية. */
    operator fun times(scalar: Double): Force = Force(value * scalar, unit)

    /** يقسم على معامل عددية. */
    operator fun div(scalar: Double): Force = Force(value / scalar, unit)

    override fun toString(): String = "%.4f %s".format(value, unit.name)

    companion object {
        /** منشئات ملائمة لإنشاء قياسات قوة بوحدات شائعة. */
        fun n(value: Double) = Force(value, ForceUnit.N)
        fun kn(value: Double) = Force(value, ForceUnit.kN)
        fun mn(value: Double) = Force(value, ForceUnit.MN)
        fun lbf(value: Double) = Force(value, ForceUnit.lbf)
        fun kip(value: Double) = Force(value, ForceUnit.kip)
        fun kgf(value: Double) = Force(value, ForceUnit.kgf)
        fun tf(value: Double) = Force(value, ForceUnit.tf)
    }
}

/**
 * قياس الإجهاد مع النوع الآمن للوحدة.
 *
 * // ECP 203 Cl. 4-2-1 / ACI 318 Cl. 9.3 / SBC 304 — الإجهاد بالميغا باسكال (MPa = N/mm²).
 */
data class Stress(
    val value: Double,
    val unit: StressUnit
) {
    init {
        requirePositive(value, "Stress value")
    }

    /** القيمة المحوّلة إلى الوحدة الأساسية (MPa) */
    val toBaseMpa: Double get() = value * unit.toBaseMpa

    /**
     * يحول هذه القياسة إلى وحدة هدف معينة ويعيد القيمة الرقمية بتلك الوحدة.
     */
    fun to(targetUnit: StressUnit): Double =
        toBaseMpa / targetUnit.toBaseMpa

    // --- خصائص ملائمة ---
    val pa: Double get() = to(StressUnit.Pa)
    val kpa: Double get() = to(StressUnit.kPa)
    val mpa: Double get() = to(StressUnit.MPa)
    val gpa: Double get() = to(StressUnit.GPa)
    val psi: Double get() = to(StressUnit.psi)
    val ksi: Double get() = to(StressUnit.ksi)
    val psf: Double get() = to(StressUnit.psf)
    val kgfCm2: Double get() = to(StressUnit.kgfCm2)

    /** يضيف إجهادًا آخر بعد التحويل إلى الوحدة الأساسية. */
    operator fun plus(other: Stress): Stress =
        Stress((toBaseMpa + other.toBaseMpa) / StressUnit.MPa.toBaseMpa, StressUnit.MPa)

    /** يطرح إجهادًا آخر بعد التحويل إلى الوحدة الأساسية. */
    operator fun minus(other: Stress): Stress =
        Stress((toBaseMpa - other.toBaseMpa) / StressUnit.MPa.toBaseMpa, StressUnit.MPa)

    /** يضرب في معامل عددية. */
    operator fun times(scalar: Double): Stress = Stress(value * scalar, unit)

    /** يقسم على معامل عددية. */
    operator fun div(scalar: Double): Stress = Stress(value / scalar, unit)

    override fun toString(): String = "%.4f %s".format(value, unit.name)

    companion object {
        /** منشئات ملائمة لإنشاء قياسات إجهاد بوحدات شائعة. */
        fun pa(value: Double) = Stress(value, StressUnit.Pa)
        fun kpa(value: Double) = Stress(value, StressUnit.kPa)
        fun mpa(value: Double) = Stress(value, StressUnit.MPa)
        fun gpa(value: Double) = Stress(value, StressUnit.GPa)
        fun psi(value: Double) = Stress(value, StressUnit.psi)
        fun ksi(value: Double) = Stress(value, StressUnit.ksi)
        fun psf(value: Double) = Stress(value, StressUnit.psf)
        fun kgfCm2(value: Double) = Stress(value, StressUnit.kgfCm2)
    }
}

/**
 * قياس العزوم مع النوع الآمن للوحدة.
 *
 * // ECP 203 Cl. 4-2-1 / ACI 318 Cl. 9.3 / SBC 304 — العزوم بالكيلونيوتن·متر أو نيوتن·مم.
 */
data class Moment(
    val value: Double,
    val unit: MomentUnit
) {
    init {
        requirePositive(value, "Moment value")
    }

    /** القيمة المحوّلة إلى الوحدة الأساسية (N·mm) */
    val toBaseNmm: Double get() = value * unit.toBaseNmm

    /**
     * يحول هذه القياسة إلى وحدة هدف معينة ويعيد القيمة الرقمية بتلك الوحدة.
     */
    fun to(targetUnit: MomentUnit): Double =
        toBaseNmm / targetUnit.toBaseNmm

    // --- خصائص ملائمة ---
    val nmm: Double get() = to(MomentUnit.Nmm)
    val nm: Double get() = to(MomentUnit.Nm)
    val knmm: Double get() = to(MomentUnit.kNmm)
    val knm: Double get() = to(MomentUnit.kNm)
    val lbfIn: Double get() = to(MomentUnit.lbfIn)
    val lbfFt: Double get() = to(MomentUnit.lbfFt)
    val kipIn: Double get() = to(MomentUnit.kipIn)
    val kipFt: Double get() = to(MomentUnit.kipFt)

    /** يضيف عزومًا آخر بعد التحويل إلى الوحدة الأساسية. */
    operator fun plus(other: Moment): Moment =
        Moment((toBaseNmm + other.toBaseNmm) / MomentUnit.Nmm.toBaseNmm, MomentUnit.Nmm)

    /** يطرح عزومًا آخر بعد التحويل إلى الوحدة الأساسية. */
    operator fun minus(other: Moment): Moment =
        Moment((toBaseNmm - other.toBaseNmm) / MomentUnit.Nmm.toBaseNmm, MomentUnit.Nmm)

    /** يضرب في معامل عددية. */
    operator fun times(scalar: Double): Moment = Moment(value * scalar, unit)

    /** يقسم على معامل عددية. */
    operator fun div(scalar: Double): Moment = Moment(value / scalar, unit)

    override fun toString(): String = "%.4f %s".format(value, unit.name)

    companion object {
        /** منشئات ملائمة لإنشاء قياسات عزوم بوحدات شائعة. */
        fun nmm(value: Double) = Moment(value, MomentUnit.Nmm)
        fun nm(value: Double) = Moment(value, MomentUnit.Nm)
        fun knmm(value: Double) = Moment(value, MomentUnit.kNmm)
        fun knm(value: Double) = Moment(value, MomentUnit.kNm)
        fun lbfIn(value: Double) = Moment(value, MomentUnit.lbfIn)
        fun lbfFt(value: Double) = Moment(value, MomentUnit.lbfFt)
        fun kipIn(value: Double) = Moment(value, MomentUnit.kipIn)
        fun kipFt(value: Double) = Moment(value, MomentUnit.kipFt)
    }
}
