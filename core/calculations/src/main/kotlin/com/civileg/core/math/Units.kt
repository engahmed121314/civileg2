package com.civileg.core.math

/**
 * Type-safe engineering quantities (PHASE 02, spec §8 — unit system).
 *
 * Internal canonical bases: mm, N, N·mm (= mJ), N/mm² (= MPa), mm².
 * Arithmetic only between compatible quantities; cross-dimension products
 * are explicit operators (Force × Length = Moment, Force / Area = Stress).
 * No implicit numeric mixing — every conversion is a named `.to(unit)`.
 */
enum class LengthUnit(internal val toMm: Double) { MM(1.0), CM(10.0), M(1000.0) }
enum class ForceUnit(internal val toN: Double) { N(1.0), KN(1_000.0), TON(10_000.0) }
enum class StressUnit(internal val toMpa: Double) { MPA(1.0), KPA(1e-3), GPA(1e3) }
enum class MomentUnit(internal val toNmm: Double) { NMM(1.0), KNM(1e6) }

@JvmInline value class Length internal constructor(private val mm: Double) {
    fun value(u: LengthUnit) = mm / u.toMm
    val asMm get() = mm
    operator fun plus(o: Length) = Length(mm + o.mm)
    operator fun minus(o: Length) = Length(mm - o.mm)
    companion object { fun of(v: Double, u: LengthUnit) = Length(SafeMath.requireFinite(v, "length") * u.toMm) }
}

@JvmInline value class Force internal constructor(private val n: Double) {
    fun value(u: ForceUnit) = n / u.toN
    val asN get() = n
    operator fun plus(o: Force) = Force(n + o.n)
    operator fun minus(o: Force) = Force(n - o.n)
    operator fun times(l: Length): Moment = Moment(n * l.asMm)
    companion object { fun of(v: Double, u: ForceUnit) = Force(SafeMath.requireFinite(v, "force") * u.toN) }
}

@JvmInline value class Area internal constructor(private val mm2: Double) {
    fun asMm2() = mm2
    operator fun plus(o: Area) = Area(mm2 + o.mm2)
    companion object { fun ofMm2(v: Double) = Area(SafeMath.requirePositive(v, "area")) }
}

@JvmInline value class Stress internal constructor(private val mpa: Double) {
    fun value(u: StressUnit) = mpa / u.toMpa
    val asMpa get() = mpa
    companion object { fun of(v: Double, u: StressUnit) = Stress(SafeMath.requireFinite(v, "stress") * u.toMpa) }
}

@JvmInline value class Moment internal constructor(private val nmm: Double) {
    fun value(u: MomentUnit) = nmm / u.toNmm
    val asNmm get() = nmm
    /** Lever-arm division: Moment / Length = Force. */
    operator fun div(l: Length): Force = Force(nmm / l.asMm)
    companion object { fun of(v: Double, u: MomentUnit) = Moment(SafeMath.requireFinite(v, "moment") * u.toNmm) }
}

/** Stress = Force / Area (N / mm² = MPa). */
operator fun Force.div(a: Area): Stress = Stress(asN / a.asMm2())

/** Area = Force / Stress (mm²). */
fun forceOverStressToArea(f: Force, s: Stress): Area = Area.ofMm2(f.asN / s.asMpa)
