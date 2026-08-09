package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.ReinforcementResult
import kotlin.math.*

/**
 * تصميم خزانات المياه حسب SBC 304-2018
 */
class SBCTank : TankDesign {

    companion object {
        private const val PHI_FLEXURE = 0.90
        private const val PHI_SHEAR = 0.75
        private const val GAMMA_W = 9.81
        private const val CONCRETE_DENSITY = 25.0
        private const val MIN_COVER = 50.0
        private const val MIN_WALL_THICKNESS = 200.0
        private const val MIN_BASE_THICKNESS = 250.0
        private const val MIN_RHO_ENV = 0.0020
        private const val FLUID_LOAD_FACTOR = 1.4
    }

    override fun calculateTank(
        length: Double, width: Double, height: Double,
        waterDepth: Double, fcu: Double, fy: Double, type: TankType
    ): TankResult {
        val formulas = mutableListOf<String>()
        val checks = mutableListOf<TankSafetyCheck>()
        
        val L = length / 1000.0
        val B = width / 1000.0
        val H = height / 1000.0
        val hW = waterDepth / 1000.0
        val fcPrime = 0.8 * fcu

        val capacityM3 = if (type.name.contains("CIRCULAR")) (PI * (L/2.0).pow(2) * hW) else (L * B * hW)
        val wallT = max(H / 10.0 * 1000, MIN_WALL_THICKNESS).let { ceil(it / 50.0) * 50.0 }
        val baseT = max(wallT + 100.0, MIN_BASE_THICKNESS).let { ceil(it / 50.0) * 50.0 }

        val isCircular = type.name.contains("CIRCULAR")
        val isUnderground = type.name.contains("UNDERGROUND")

        val wallDesign = if (isCircular) {
            designProCircularSBC(L, H, hW, fcPrime, fy, wallT, formulas, checks)
        } else {
            designProRectangularSBC(L, B, H, hW, fcPrime, fy, wallT, formulas, checks)
        }

        val baseDesign = designProBaseSBC(L, B, H, hW, fcPrime, fy, baseT, wallT, isCircular, isUnderground, formulas, checks)

        val concreteVol = calculateVolume(L, B, H, wallT, baseT, isCircular)
        val steelWeight = concreteVol * 145.0 

        return TankResult(
            wallThickness = wallT, baseThickness = baseT,
            wallReinforcement = wallDesign, baseReinforcement = baseDesign,
            capacityM3 = capacityM3, concreteVolume = concreteVol, steelWeight = steelWeight,
            cost = concreteVol * 2500.0 + (steelWeight/1000.0) * 4500.0,
            isSafe = checks.all { it.isSafe }, pressure = GAMMA_W * hW,
            maxMomentWall = if (isCircular) GAMMA_W * hW.pow(3)/15.0 else GAMMA_W * hW.pow(3)/6.0,
            structuralSystem = "SBC 304 Environmental Structure",
            formulas = formulas, designCode = "SBC 304-2018 / ACI 350",
            recommendations = listOf("Use water-stop at joints", "Min cover 50mm", "Continuous curing 7 days"),
            safetyChecks = checks
        )
    }

    private fun designProRectangularSBC(L: Double, B: Double, H: Double, hW: Double, fc: Double, fy: Double, t: Double, formulas: MutableList<String>, checks: MutableList<TankSafetyCheck>): ReinforcementResult {
        val d = t - MIN_COVER - 14.0
        val aspect = H / L.coerceAtLeast(1.0)
        val Cs = if (aspect > 1.5) 0.42 else 0.32
        val Mu = Cs * GAMMA_W * hW.pow(3) / 6.0 * FLUID_LOAD_FACTOR
        formulas.add("SBC Mu = 1.4F × Cs × (γw × h³/6) = ${Mu.format(2)} kN.m/m")
        val Rn = (Mu * 1e6) / (PHI_FLEXURE * 1000.0 * d.pow(2))
        val rho = (0.85 * fc / fy) * (1 - sqrt(max(0.0, 1 - (2 * Rn) / (0.85 * fc))))
        val rhoMin = max(MIN_RHO_ENV, 0.0018 * 420.0 / fy)
        val finalAs = max(rho, rhoMin) * 1000.0 * d
        val bars = ceil(finalAs / 153.9).toInt().coerceIn(7, 15)

        // Shear
        val Vu = GAMMA_W * hW.pow(2) / 2.0 * FLUID_LOAD_FACTOR
        val Vc = PHI_SHEAR * 0.17 * sqrt(fc) * 1000.0 * d / 1000.0
        checks.add(TankSafetyCheck("Wall Shear", Vu, Vc, "kN", Vu <= Vc, "Vc = 0.17√f'c", "SBC 304 Shear check"))

        return ReinforcementResult(
            astRequired = finalAs, astProvided = bars * 153.9, barDiameter = 14.0, numberOfBars = bars,
            tiesDiameter = 10.0, tiesSpacing = 200.0, isSafe = Vu <= Vc, utilizationRatio = finalAs / (bars * 153.9),
            spacing = 1000.0 / bars, description = "V: ${bars}Ø14/m' (Main)"
        )
    }

    private fun designProCircularSBC(D: Double, H: Double, hW: Double, fc: Double, fy: Double, t: Double, formulas: MutableList<String>, checks: MutableList<TankSafetyCheck>): ReinforcementResult {
        val R = D / 2.0
        val T = GAMMA_W * hW * R * FLUID_LOAD_FACTOR
        formulas.add("SBC Tu = 1.4F × (γw × h × R) = ${T.format(2)} kN/m")
        val As = (T * 1000.0) / (PHI_FLEXURE * fy)
        val bars = ceil(As / 153.9).toInt().coerceIn(7, 20)
        val ft = (T / FLUID_LOAD_FACTOR * 1000.0) / (bars * 153.9)
        val ftAllow = 160.0
        checks.add(TankSafetyCheck("Steel Service Stress", ft, ftAllow, "MPa", ft <= ftAllow, "ft = Ts / As", "SBC 304 durability"))

        return ReinforcementResult(
            astRequired = As, astProvided = bars * 153.9, barDiameter = 14.0, numberOfBars = bars,
            tiesDiameter = 14.0, tiesSpacing = 200.0, isSafe = ft <= ftAllow, utilizationRatio = As / (bars * 153.9),
            spacing = 1000.0 / bars, description = "Hoop: ${bars}Ø14/m'"
        )
    }

    private fun designProBaseSBC(L: Double, B: Double, H: Double, hW: Double, fc: Double, fy: Double, bt: Double, wt: Double, isCirc: Boolean, isUnder: Boolean, formulas: MutableList<String>, checks: MutableList<TankSafetyCheck>): ReinforcementResult {
        val d = bt - MIN_COVER - 14.0
        val q = (GAMMA_W * hW * FLUID_LOAD_FACTOR) + (bt/1000.0)*CONCRETE_DENSITY*1.2
        val M = q * (if (isCirc) L else min(L,B)).pow(2) / 10.0
        val Rn = (M * 1e6) / (PHI_FLEXURE * 1000.0 * d.pow(2))
        val rho = (0.85 * fc / fy) * (1 - sqrt(max(0.0, 1 - (2 * Rn) / (0.85 * fc))))
        val finalAs = max(rho, 0.002) * 1000.0 * d
        val bars = ceil(finalAs / 153.9).toInt().coerceIn(7, 15)

        return ReinforcementResult(
            astRequired = finalAs, astProvided = bars * 153.9, barDiameter = 14.0, numberOfBars = bars,
            tiesDiameter = 14.0, tiesSpacing = 200.0, isSafe = true, utilizationRatio = finalAs / (bars * 153.9),
            spacing = 1000.0 / bars, description = "Base: ${bars}Ø14/m' E.W."
        )
    }

    private fun calculateVolume(L: Double, B: Double, H: Double, wt: Double, bt: Double, circ: Boolean): Double {
        val wtM = wt / 1000.0; val btM = bt / 1000.0
        return if (circ) (PI * (L/2.0 + wtM).pow(2) * (H + btM)) - (PI * (L/2.0).pow(2) * H)
        else (L + 2*wtM) * (B + 2*wtM) * (H + btM) - (L * B * H)
    }

    private fun Double.format(n: Int) = String.format("%.${n}f", this)
}
