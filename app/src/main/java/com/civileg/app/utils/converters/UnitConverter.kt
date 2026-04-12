package com.civileg.app.utils.converters

object UnitConverter {
    
    // تحويلات الطول
    fun convertLength(value: Double, from: LengthUnit, to: LengthUnit): Double {
        val meters = when (from) {
            LengthUnit.METER -> value
            LengthUnit.CM -> value / 100
            LengthUnit.MM -> value / 1000
            LengthUnit.FOOT -> value * 0.3048
            LengthUnit.INCH -> value * 0.0254
        }
        return when (to) {
            LengthUnit.METER -> meters
            LengthUnit.CM -> meters * 100
            LengthUnit.MM -> meters * 1000
            LengthUnit.FOOT -> meters / 0.3048
            LengthUnit.INCH -> meters / 0.0254
        }
    }
    
    // تحويلات القوة
    fun convertForce(value: Double, from: ForceUnit, to: ForceUnit): Double {
        val newtons = when (from) {
            ForceUnit.N -> value
            ForceUnit.KN -> value * 1000
            ForceUnit.KGF -> value * 9.80665
            ForceUnit.LBF -> value * 4.44822
        }
        return when (to) {
            ForceUnit.N -> newtons
            ForceUnit.KN -> newtons / 1000
            ForceUnit.KGF -> newtons / 9.80665
            ForceUnit.LBF -> newtons / 4.44822
        }
    }
    
    // تحويلات الضغط/الإجهاد
    fun convertPressure(value: Double, from: PressureUnit, to: PressureUnit): Double {
        val pascals = when (from) {
            PressureUnit.PA -> value
            PressureUnit.KPA -> value * 1000
            PressureUnit.MPA -> value * 1_000_000
            PressureUnit.PSI -> value * 6894.76
        }
        return when (to) {
            PressureUnit.PA -> pascals
            PressureUnit.KPA -> pascals / 1000
            PressureUnit.MPA -> pascals / 1_000_000
            PressureUnit.PSI -> pascals / 6894.76
        }
    }
    
    // وحدات الطول
    enum class LengthUnit(val symbol: String) {
        METER("m"), CM("cm"), MM("mm"), FOOT("ft"), INCH("in")
    }
    
    // وحدات القوة
    enum class ForceUnit(val symbol: String) {
        N("N"), KN("kN"), KGF("kgf"), LBF("lbf")
    }
    
    // وحدات الضغط
    enum class PressureUnit(val symbol: String) {
        PA("Pa"), KPA("kPa"), MPA("MPa"), PSI("psi")
    }
}
