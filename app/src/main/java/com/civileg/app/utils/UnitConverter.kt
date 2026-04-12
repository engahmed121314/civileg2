package com.civileg.app.utils

object UnitConverter {
    
    sealed class UnitCategory {
        object Length : UnitCategory()
        object Area : UnitCategory()
        object Volume : UnitCategory()
        object Weight : UnitCategory()
        object Force : UnitCategory()
        object Pressure : UnitCategory()
        object Moment : UnitCategory()
    }
    
    fun convert(value: Double, from: String, to: String, category: UnitCategory): Double {
        return when (category) {
            is UnitCategory.Length -> convertLength(value, from, to)
            is UnitCategory.Area -> convertArea(value, from, to)
            is UnitCategory.Volume -> convertVolume(value, from, to)
            is UnitCategory.Weight -> convertWeight(value, from, to)
            is UnitCategory.Force -> convertForce(value, from, to)
            is UnitCategory.Pressure -> convertPressure(value, from, to)
            is UnitCategory.Moment -> convertMoment(value, from, to)
        }
    }
    
    private fun convertLength(value: Double, from: String, to: String): Double {
        val toMeter = when (from) {
            "m" -> 1.0
            "cm" -> 0.01
            "mm" -> 0.001
            "km" -> 1000.0
            "ft" -> 0.3048
            "in" -> 0.0254
            "yd" -> 0.9144
            "mi" -> 1609.344
            else -> 1.0
        }
        
        val fromMeter = when (to) {
            "m" -> 1.0
            "cm" -> 100.0
            "mm" -> 1000.0
            "km" -> 0.001
            "ft" -> 3.28084
            "in" -> 39.3701
            "yd" -> 1.09361
            "mi" -> 0.000621371
            else -> 1.0
        }
        
        return value * toMeter * fromMeter
    }
    
    private fun convertArea(value: Double, from: String, to: String): Double {
        val toSquareMeter = when (from) {
            "m²" -> 1.0
            "cm²" -> 0.0001
            "mm²" -> 0.000001
            "ft²" -> 0.092903
            "in²" -> 0.00064516
            "yd²" -> 0.836127
            "acre" -> 4046.86
            else -> 1.0
        }
        
        val fromSquareMeter = when (to) {
            "m²" -> 1.0
            "cm²" -> 10000.0
            "mm²" -> 1000000.0
            "ft²" -> 10.7639
            "in²" -> 1550.0
            "yd²" -> 1.19599
            "acre" -> 0.000247105
            else -> 1.0
        }
        
        return value * toSquareMeter * fromSquareMeter
    }
    
    private fun convertVolume(value: Double, from: String, to: String): Double {
        val toCubicMeter = when (from) {
            "m³" -> 1.0
            "cm³" -> 0.000001
            "L" -> 0.001
            "ft³" -> 0.0283168
            "gal" -> 0.00378541
            else -> 1.0
        }
        
        val fromCubicMeter = when (to) {
            "m³" -> 1.0
            "cm³" -> 1000000.0
            "L" -> 1000.0
            "ft³" -> 35.3147
            "gal" -> 264.172
            else -> 1.0
        }
        
        return value * toCubicMeter * fromCubicMeter
    }
    
    private fun convertWeight(value: Double, from: String, to: String): Double {
        val toKg = when (from) {
            "kg" -> 1.0
            "g" -> 0.001
            "ton" -> 1000.0
            "lb" -> 0.453592
            "oz" -> 0.0283495
            else -> 1.0
        }
        
        val fromKg = when (to) {
            "kg" -> 1.0
            "g" -> 1000.0
            "ton" -> 0.001
            "lb" -> 2.20462
            "oz" -> 35.274
            else -> 1.0
        }
        
        return value * toKg * fromKg
    }
    
    private fun convertForce(value: Double, from: String, to: String): Double {
        val toNewton = when (from) {
            "N" -> 1.0
            "kN" -> 1000.0
            "MN" -> 1000000.0
            "kgf" -> 9.80665
            "tonf" -> 9806.65
            "lbf" -> 4.44822
            "kip" -> 4448.22
            else -> 1.0
        }
        
        val fromNewton = when (to) {
            "N" -> 1.0
            "kN" -> 0.001
            "MN" -> 0.000001
            "kgf" -> 0.101972
            "tonf" -> 0.000101972
            "lbf" -> 0.224809
            "kip" -> 0.000224809
            else -> 1.0
        }
        
        return value * toNewton * fromNewton
    }
    
    private fun convertPressure(value: Double, from: String, to: String): Double {
        val toPascal = when (from) {
            "Pa" -> 1.0
            "kPa" -> 1000.0
            "MPa" -> 1000000.0
            "bar" -> 100000.0
            "psi" -> 6894.76
            "ksf" -> 47880.3
            "tsf" -> 95760.5
            else -> 1.0
        }
        
        val fromPascal = when (to) {
            "Pa" -> 1.0
            "kPa" -> 0.001
            "MPa" -> 0.000001
            "bar" -> 0.00001
            "psi" -> 0.000145038
            "ksf" -> 0.000020885
            "tsf" -> 0.0000104427
            else -> 1.0
        }
        
        return value * toPascal * fromPascal
    }
    
    private fun convertMoment(value: Double, from: String, to: String): Double {
        val toNewtonMeter = when (from) {
            "N.m" -> 1.0
            "kN.m" -> 1000.0
            "ton.m" -> 9806.65
            "kgf.m" -> 9.80665
            "lb.ft" -> 1.35582
            "kip.ft" -> 1355.82
            else -> 1.0
        }
        
        val fromNewtonMeter = when (to) {
            "N.m" -> 1.0
            "kN.m" -> 0.001
            "ton.m" -> 0.000101972
            "kgf.m" -> 0.101972
            "lb.ft" -> 0.737562
            "kip.ft" -> 0.000737562
            else -> 1.0
        }

        return value * toNewtonMeter * fromNewtonMeter
    }
}