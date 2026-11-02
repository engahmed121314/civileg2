package com.civileg.app.domain.calculations

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Course Test Vectors Unit Test Suite (§10)
 * Validates all required course test vectors against SBC 304-2024 / ACI 318-19 / SBC 301.
 */
class CourseTestVectorsTest {

    @Test
    fun testEcForFprimeC30() {
        val fprimeC = 30.0
        val ec = 4700.0 * sqrt(fprimeC)
        // Expected: 25,742.96 -> 25,743 MPa
        assertEquals(25743.0, ec, 1.0)
    }

    @Test
    fun testWallShearExample() {
        // f'c 30 MPa, lw 4.0 m, h 0.25 m, d = 0.8 * 4 = 3.2 m, fyt 420, 2Ø12 @ 200 (Av = 226 mm²)
        val fprimeC = 30.0
        val bw = 250.0 // mm (h of wall)
        val d = 3200.0 // mm
        val fyt = 420.0
        val av = 226.0 // mm²
        val s = 200.0 // mm

        val vc = 0.17 * sqrt(fprimeC) * bw * d / 1000.0 // kN
        val vs = av * fyt * d / (s * 1000.0) // kN
        val vnMax = 0.83 * sqrt(fprimeC) * bw * d / 1000.0 // kN
        val vn = minOf(vnMax, vc + vs)

        assertEquals(745.0, vc, 5.0)
        assertEquals(1519.0, vs, 10.0)
        assertEquals(3637.0, vnMax, 20.0)
        assertEquals(2264.0, vn, 15.0)
    }

    @Test
    fun testColumnAvMin() {
        // f'c 30, bw 300, s 200, fyt 420
        val fprimeC = 30.0
        val bw = 300.0
        val fyt = 420.0
        val s = 200.0
        val avMin = maxOf(
            0.062 * sqrt(fprimeC) * bw / fyt * s,
            0.35 * bw / fyt * s
        )
        // avMin approx 50.0 mm²
        assertEquals(50.0, avMin, 1.0)
    }

    @Test
    fun testTwoWaySlabMinThickness() {
        // ln = 6.0 m (6000 mm), short = 4.0 m (4000 mm) -> beta = 1.5
        val ln = 6000.0
        val short = 4000.0
        val beta = ln / short
        val h = ln / (30.0 + 3.0 * beta)
        val roundedH = ceil(h / 5.0) * 5.0
        assertEquals(175.0, roundedH, 0.1)
    }

    @Test
    fun testSeismicPeriodTa() {
        // hn = 80 m, "all other systems" Ct = 0.0488, x = 0.75 -> Ta = 1.305 s
        val hn = 80.0
        val ct = 0.0488
        val x = 0.75
        val ta = ct * hn.pow(x)
        assertEquals(1.305, ta, 0.02)

        // S_D1 = 0.004 -> Cu = 1.7 -> Cu * Ta = 2.22 s
        val cu = 1.7
        val cuTa = cu * ta
        assertEquals(2.22, cuTa, 0.05)
    }

    @Test
    fun testWindQz() {
        // V = 50 m/s, Kd = 0.85, Kz = 0.85 -> qz = 0.613 * Kz * Kzt * Kd * Ke * V^2
        val v = 50.0
        val kd = 0.85
        val kz = 0.85
        val kzt = 1.0
        val ke = 1.0
        val qz = 0.613 * kz * kzt * kd * ke * (v * v) / 1000.0 // kN/m²
        assertEquals(1.107, qz, 0.02)
    }

    @Test
    fun testColumnBarsFromSteelRatio() {
        // 300 x 1000 mm at 1% -> 3000 mm² / 201 (Ø16 area = 201 mm²) = 14.92 -> 16T16
        val ag = 300.0 * 1000.0
        val astReq = 0.01 * ag
        val barArea = 201.0 // mm² for Ø16
        val numBars = ceil(astReq / barArea).toInt()
        assertEquals(15, numBars) // Course mentions 15 -> 16T16 (even symmetry)
    }

    @Test
    fun testPhiStrainCompatibility() {
        val fy = 420.0
        val es = 200000.0
        val ety = fy / es // 0.0021
        assertEquals(0.0021, ety, 1e-4)
    }
}
