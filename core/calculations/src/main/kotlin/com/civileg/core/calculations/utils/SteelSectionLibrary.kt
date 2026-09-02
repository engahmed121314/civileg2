package com.civileg.core.calculations.utils

import kotlin.math.sqrt

data class SteelSectionProperties(
    val name: String,
    val depth: Double,       // h (mm)
    val width: Double,       // b (mm)
    val tw: Double,          // web thickness (mm)
    val tf: Double,          // flange thickness (mm)
    val area: Double,        // cm²
    val weight: Double,      // kg/m
    val Ix: Double,          // cm⁴
    val Iy: Double,          // cm⁴
    val Sx: Double,          // cm³
    val Sy: Double,          // cm³
    val rx: Double,          // cm
    val ry: Double,          // cm
    val Zx: Double = 0.0,    // cm³
    val Zy: Double = 0.0,    // cm³
    val J: Double = 0.0,
    val Cw: Double = 0.0
)

/**
 * Centralized library for Steel Section properties.
 */
object SteelSectionLibrary {

    val ipeSections = listOf(
        SteelSectionProperties("IPE 80", 80.0, 46.0, 3.8, 5.2, 7.64, 6.0, 80.1, 8.49, 20.0, 3.69, 3.24, 1.05, 23.2, 5.8, 1.04),
        SteelSectionProperties("IPE 100", 100.0, 55.0, 4.1, 5.7, 10.3, 8.1, 171.0, 15.9, 34.2, 5.79, 4.07, 1.24, 39.4, 9.15, 1.41, 5770.0),
        SteelSectionProperties("IPE 120", 120.0, 64.0, 4.4, 6.3, 13.2, 10.4, 318.0, 27.7, 53.0, 8.65, 4.90, 1.45, 60.7, 13.6, 2.58),
        SteelSectionProperties("IPE 140", 140.0, 73.0, 4.7, 6.9, 16.4, 12.9, 541.0, 44.9, 77.3, 12.3, 5.74, 1.65, 88.3, 19.2, 4.40),
        SteelSectionProperties("IPE 160", 160.0, 82.0, 5.0, 7.4, 20.1, 15.8, 869.0, 68.3, 109.0, 16.7, 6.58, 1.84, 124.0, 26.1, 7.27),
        SteelSectionProperties("IPE 180", 180.0, 91.0, 5.3, 8.0, 23.9, 18.8, 1317.0, 101.0, 146.0, 22.2, 7.42, 2.05, 166.0, 34.6, 11.3),
        SteelSectionProperties("IPE 200", 200.0, 100.0, 5.6, 8.5, 28.5, 22.4, 1943.0, 142.0, 194.0, 28.5, 8.26, 2.24, 220.0, 43.9, 14.1, 54900.0),
        SteelSectionProperties("IPE 220", 220.0, 110.0, 5.9, 9.2, 33.4, 26.2, 2772.0, 205.0, 252.0, 37.3, 9.11, 2.48, 285.0, 58.1, 20.7),
        SteelSectionProperties("IPE 240", 240.0, 120.0, 6.2, 9.8, 39.1, 30.7, 3892.0, 284.0, 324.0, 47.3, 9.97, 2.69, 367.0, 73.9, 29.5),
        SteelSectionProperties("IPE 270", 270.0, 135.0, 6.6, 10.2, 45.9, 36.1, 5790.0, 420.0, 429.0, 62.2, 11.2, 3.02, 484.0, 97.0, 43.8),
        SteelSectionProperties("IPE 300", 300.0, 150.0, 7.1, 10.7, 53.8, 42.2, 8356.0, 604.0, 557.0, 80.5, 12.5, 3.35, 628.0, 123.0, 39.7, 197000.0)
    )

    fun getAllSections(): List<SteelSectionProperties> = ipeSections

    fun getLightestSectionForLoads(
        maxMoment: Double, // kN.m
        maxShear: Double,  // kN
        axialLoad: Double, // kN
        fy: Double = 240.0,
        phi: Double = 0.9
    ): SteelSectionProperties? {
        return getAllSections().sortedBy { it.weight }.firstOrNull { sec ->
            val Mn = phi * fy * (sec.Zx * 1e3) / 1e6
            val Vn = phi * 0.6 * fy * (sec.depth * sec.tw) / 1e3
            val Pn = phi * fy * (sec.area * 100.0) / 1e3
            
            maxMoment <= Mn && maxShear <= Vn && axialLoad <= Pn
        }
    }
}
