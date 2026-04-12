package com.civilengineer.app.domain.calculator

/**
 * محول الوحدات الهندسية
 */
object UnitConverter {

    // تحويلات الطول
    enum class LengthUnit(val toMeter: Double) {
        METER(1.0),
        CENTIMETER(0.01),
        MILLIMETER(0.001),
        KILOMETER(1000.0),
        FEET(0.3048),
        INCH(0.0254)
    }

    // تحويلات الوزن/القوة
    enum class ForceUnit(val toNewton: Double) {
        NEWTON(1.0),
        KILONEWTON(1000.0),
        MEGANEWTON(1_000_000.0),
        KGF(9.80665),
        TON_FORCE(9806.65),
        POUND_FORCE(4.44822)
    }

    // تحويلات الضغط
    enum class PressureUnit(val toPascal: Double) {
        PASCAL(1.0),
        KILOPASCAL(1000.0),
        MEGAPASCAL(1_000_000.0),
        BAR(100_000.0),
        KGF_CM2(98_066.5),
        PSI(6894.76),
        MMHG(133.322)
    }

    // تحويلات المساحة
    enum class AreaUnit(val toM2: Double) {
        SQUARE_METER(1.0),
        SQUARE_CM(0.0001),
        SQUARE_MM(0.000001),
        HECTARE(10_000.0),
        SQUARE_FEET(0.092903),
        SQUARE_INCH(0.00064516)
    }

    fun convertLength(value: Double, from: LengthUnit, to: LengthUnit): Double {
        return value * from.toMeter / to.toMeter
    }

    fun convertForce(value: Double, from: ForceUnit, to: ForceUnit): Double {
        return value * from.toNewton / to.toNewton
    }

    fun convertPressure(value: Double, from: PressureUnit, to: PressureUnit): Double {
        return value * from.toPascal / to.toPascal
    }

    fun convertArea(value: Double, from: AreaUnit, to: AreaUnit): Double {
        return value * from.toM2 / to.toM2
    }

    // دالات التحويل المباشرة
    fun metersToFeet(meters: Double): Double = meters * 3.28084
    fun feetToMeters(feet: Double): Double = feet * 0.3048
    fun cmToMeters(cm: Double): Double = cm * 0.01
    fun metersToCm(meters: Double): Double = meters * 100
    fun mmToMeters(mm: Double): Double = mm * 0.001
    fun metersToMm(meters: Double): Double = meters * 1000
    
    fun knToKgf(kn: Double): Double = kn * 102.04
    fun kgfToKn(kgf: Double): Double = kgf / 102.04
    fun knToTonf(kn: Double): Double = kn / 9.80665
    fun tonfToKn(tonf: Double): Double = tonf * 9.80665
    
    fun kpaToMpa(kpa: Double): Double = kpa * 0.001
    fun mpaTokpa(mpa: Double): Double = mpa * 1000
    fun kpaToBar(kpa: Double): Double = kpa * 0.01
    fun barToKpa(bar: Double): Double = bar * 100
}