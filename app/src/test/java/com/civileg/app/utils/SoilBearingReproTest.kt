package com.civileg.app.utils


import org.junit.Test

/** Reproduces SoilBearingScreen default inputs across all methods (crash hunt). */
class SoilBearingReproTest {

    private fun defaultInput(method: BearingMethod) = SoilBearingInput(
        method = method,
        soilType = SoilType.CLAY,
        foundationWidth = 1.5, foundationLength = 1.5, foundationDepth = 1.0,
        cohesion = 25.0, frictionAngle = 30.0, unitWeight = 18.0,
        waterTableDepth = 5.0,
        eccentricityX = 0.0, eccentricityY = 0.0,
        loadInclinationX = 0.0, loadInclinationY = 0.0,
        safetyFactor = 3.0
    )

    @Test
    fun allFourMethodsWithDefaults_doNotThrow() {
        val calc = SoilBearingCalculator()
        BearingMethod.entries.forEach { m ->
            val r = try {
                when (m) {
                    BearingMethod.TERZAGHI -> calc.calculateTerzaghi(defaultInput(m))
                    BearingMethod.MEYERHOF -> calc.calculateMeyerhof(defaultInput(m))
                    BearingMethod.HANSEN -> calc.calculateHansen(defaultInput(m))
                    BearingMethod.VESIC -> calc.calculateVesic(defaultInput(m))
                }
            } catch (t: Throwable) {
                throw IllegalStateException("CRASH reproduced for $m: ${t.javaClass.simpleName}: ${t.message}", t)
            }
            println("$m -> gross=${r.grossBearingCapacity} net=${r.netBearingCapacity} allowable=${r.allowableBearingCapacity}")
        }
        val map = calc.compareAllMethods(defaultInput(BearingMethod.TERZAGHI))
        println("compare size=${map.size}")
    }
}
