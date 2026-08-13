package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.TankDesign
import com.civileg.app.domain.calculations.base.TankResult
import com.civileg.app.domain.calculations.base.TankSafetyCheck
import com.civileg.app.domain.calculations.base.TankType
import com.civileg.app.domain.entities.ReinforcementResult
import kotlin.math.*
import kotlin.collections.mutableListOf

/**
 * تصميم خزانات المياه حسب الكود المصري ECP 203
 * يغطي الخزانات المستطيلة والدائرية (أرضية، مرتفعة، تحت الأرض)
 */
class ECPTank : TankDesign {

    companion object {
        private const val GAMMA_C = 1.5       // معامل أمان الخرسانة
        private const val GAMMA_S = 1.15      // معامل أمان الحديد
        private const val GAMMA_W = 9.81      // وزن وحدة الحجم للماء kN/m³
        private const val CONCRETE_DENSITY = 25.0 // كثافة الخرسانة kN/m³
        private const val MIN_COVER = 50.0    // غطاء أدنى للمنشآت المائية mm
        private const val MIN_WALL_THICKNESS = 200.0 // سمك أدنى للجدران mm
        private const val MIN_BASE_THICKNESS = 250.0 // سمك أدنى للقاعدة mm
        private const val CRACK_WIDTH_LIMIT = 0.2 // أقصى عرض شق mm
        private const val MIN_RHO_WATER = 0.0025 // نسبة تسليح أدنى للمنشآت المائية
    }

    override fun calculateTank(
        length: Double,
        width: Double,
        height: Double,
        waterDepth: Double,
        fcu: Double,
        fy: Double,
        type: TankType
    ): TankResult {
        val warnings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        val safetyChecks = mutableListOf<TankSafetyCheck>()
        val formulas = mutableListOf<String>()

        val L = length / 1000.0
        val B = width / 1000.0
        val H = height / 1000.0
        val hW = waterDepth / 1000.0

        val capacityM3 = if (type.name.contains("CIRCULAR")) (PI * (L/2.0).pow(2) * hW) else (L * B * hW)

        val wallThickness = max(H / 10.0 * 1000, MIN_WALL_THICKNESS).let { ceil(it / 50.0) * 50.0 }
        val baseThickness = max(wallThickness + 100.0, MIN_BASE_THICKNESS).let { ceil(it / 50.0) * 50.0 }

        val maxWaterPressure = GAMMA_W * hW
        formulas.add("Max Water Pressure (Pw) = γw × hW = $GAMMA_W × $hW = ${maxWaterPressure.format(2)} kN/m²")

        val isCircular = type.name.contains("CIRCULAR")
        val isUnderground = type.name.contains("UNDERGROUND")

        // ── Wall Analysis ──
        val wallDesign = if (isCircular) {
            designProfessionalCircularWall(L, H, hW, fcu, fy, wallThickness, formulas, safetyChecks)
        } else {
            designProfessionalRectangularWall(L, B, H, hW, fcu, fy, wallThickness, formulas, safetyChecks)
        }

        // ── Base Analysis ──
        val baseDesign = designProfessionalBase(L, B, H, hW, fcu, fy, baseThickness, wallThickness, isCircular, isUnderground, formulas, safetyChecks)

        // ── Stability & Quantities ──
        val concreteVol = calculateVolume(L, B, H, wallThickness, baseThickness, isCircular)
        val steelWeight = concreteVol * 140.0 // Higher ratio for professional detailing
        
        var upliftFS = 0.0
        if (isUnderground) {
            val totalWeight = concreteVol * CONCRETE_DENSITY
            val buoyancy = L * B * H * GAMMA_W
            upliftFS = totalWeight / buoyancy
            safetyChecks.add(TankSafetyCheck("Flotation Safety", upliftFS, 1.25, "-", upliftFS >= 1.25, "W_total / Buoyancy", "Against groundwater uplift"))
        }

        recommendations.addAll(listOf(
            "Use SBR/Water-stop at all construction joints.",
            "Concrete cover: 50mm (water side), 30mm (outer side).",
            "Curing: Continuous water curing for 7 days minimum.",
            "Leakage test is mandatory before backfilling."
        ))

        return TankResult(
            wallThickness = wallThickness,
            baseThickness = baseThickness,
            wallReinforcement = wallDesign,
            baseReinforcement = baseDesign,
            capacityM3 = capacityM3,
            concreteVolume = concreteVol,
            steelWeight = steelWeight,
            cost = concreteVol * 6500.0 + (steelWeight/1000.0) * 60000.0,
            isSafe = safetyChecks.all { it.isSafe },
            pressure = maxWaterPressure,
            maxMomentWall = if (isCircular) maxWaterPressure * H.pow(2) / 10.0 else maxWaterPressure * H.pow(2) / 6.0,
            structuralSystem = getSystemName(type),
            formulas = formulas,
            designCode = "ECP 203-2020 (Sec. 8.1)",
            recommendations = recommendations,
            safetyChecks = safetyChecks,
            warnings = wallDesign.warnings
        )
    }

    private fun designProfessionalRectangularWall(L: Double, B: Double, H: Double, hW: Double, fcu: Double, fy: Double, t: Double, formulas: MutableList<String>, checks: MutableList<TankSafetyCheck>): ReinforcementResult {
        val d = t - MIN_COVER - 10.0
        val fs = fy / GAMMA_S
        val Mu_factor = 1.6 // Water load factor

        // Coefficient Method (Heger / PCA Tables Approximation)
        val aspect = H / L.coerceAtLeast(1.0)
        val mx_coeff = when {
            aspect > 2.0 -> 0.45 
            aspect > 1.0 -> 0.35
            else -> 0.25
        }
        
        val M_vert = mx_coeff * GAMMA_W * hW.pow(3) / 6.0
        val M_horiz = (1.0 - mx_coeff) * GAMMA_W * hW * L.pow(2) / 12.0
        
        formulas.add("Vertical Moment (Mv) = Cs × γw × h³ / 6 = $mx_coeff × $GAMMA_W × $hW³ / 6 = ${M_vert.format(2)} kN.m/m")
        formulas.add("Horizontal Moment (Mh) = (1-Cs) × γw × h × L² / 12 = ${M_horiz.format(2)} kN.m/m")

        val As_vert = (M_vert * Mu_factor * 1e6) / (fs * 0.85 * d)
        val As_horiz = (M_horiz * Mu_factor * 1e6) / (fs * 0.85 * d)
        val As_min = MIN_RHO_WATER * 1000.0 * d

        val finalAs = max(As_vert, As_min)
        val bars = ceil(finalAs / (PI * 12.0.pow(2) / 4.0)).toInt().coerceIn(7, 15)
        
        // Shear Check
        val Vu = GAMMA_W * hW.pow(2) / 2.0
        val Vc = 0.24 * sqrt(fcu / GAMMA_C) * 1000.0 * d / 1000.0
        checks.add(TankSafetyCheck("Wall Shear", Vu, Vc, "kN", Vu <= Vc, "V = γw×h²/2", "One-way shear at wall base"))

        return ReinforcementResult(
            astRequired = finalAs, astProvided = bars * 113.1, barDiameter = 12.0, numberOfBars = bars,
            tiesDiameter = 10.0, tiesSpacing = 200.0, isSafe = Vu <= Vc, utilizationRatio = finalAs / (bars * 113.1),
            spacing = 1000.0 / bars, description = "${bars}Ø12/m' (Main Vertical)"
        )
    }

    private fun designProfessionalCircularWall(D: Double, H: Double, hW: Double, fcu: Double, fy: Double, t: Double, formulas: MutableList<String>, checks: MutableList<TankSafetyCheck>): ReinforcementResult {
        val R = D / 2.0
        val d = t - MIN_COVER - 10.0
        val fs = fy / GAMMA_S

        // Max Hoop Tension T = γw × h × R
        val T_max = GAMMA_W * hW * R
        formulas.add("Max Hoop Tension (T) = γw × hW × R = $GAMMA_W × $hW × $R = ${T_max.format(2)} kN/m")

        val As_hoop = (T_max * 1.6 * 1000.0) / fs
        val As_min = MIN_RHO_WATER * 1000.0 * d
        val finalAs = max(As_hoop, As_min)

        val bars = ceil(finalAs / (PI * 12.0.pow(2) / 4.0)).toInt().coerceIn(7, 20)
        
        // Tensile Stress Check (Cracking)
        val ft = T_max / (t / 1000.0) / 1000.0 // MPa
        val fct = 0.6 * sqrt(fcu) / 1.5 // Reduced for water retaining
        checks.add(TankSafetyCheck("Concrete Tension", ft, fct, "MPa", ft <= fct, "σ = T / t", "Against water leakage cracking"))

        return ReinforcementResult(
            astRequired = finalAs, astProvided = bars * 113.1, barDiameter = 12.0, numberOfBars = bars,
            tiesDiameter = 12.0, tiesSpacing = 150.0, isSafe = ft <= fct, utilizationRatio = finalAs / (bars * 113.1),
            spacing = 1000.0 / bars, description = "Hoop: ${bars}Ø12/m'"
        )
    }

    private fun designProfessionalBase(L: Double, B: Double, H: Double, hW: Double, fcu: Double, fy: Double, bt: Double, wt: Double, isCirc: Boolean, isUnder: Boolean, formulas: MutableList<String>, checks: MutableList<TankSafetyCheck>): ReinforcementResult {
        val d = bt - MIN_COVER - 12.0
        val fs = fy / GAMMA_S
        
        val q_net = if (isUnder) (GAMMA_W * hW + (bt/1000.0)*CONCRETE_DENSITY) else (GAMMA_W * hW + (bt/1000.0)*CONCRETE_DENSITY)
        val span = if (isCirc) L else min(L, B)
        val M_base = q_net * span.pow(2) / 8.0 // Simplified span moment
        
        formulas.add("Base Pressure (q) = ${q_net.format(2)} kN/m²")
        formulas.add("Base Moment (Mb) = q × L² / 8 = ${M_base.format(2)} kN.m/m")

        val As_base = (M_base * 1.6 * 1e6) / (fs * 0.85 * d)
        val As_min = 0.0025 * 1000.0 * bt
        val finalAs = max(As_base, As_min)
        val bars = ceil(finalAs / (PI * 12.0.pow(2) / 4.0)).toInt().coerceIn(7, 15)

        return ReinforcementResult(
            astRequired = finalAs, astProvided = bars * 113.1, barDiameter = 12.0, numberOfBars = bars,
            tiesDiameter = 12.0, tiesSpacing = 200.0, isSafe = true, utilizationRatio = finalAs / (bars * 113.1),
            spacing = 1000.0 / bars, description = "Bottom: ${bars}Ø12/m' B.W."
        )
    }

    private fun calculateVolume(L: Double, B: Double, H: Double, wt: Double, bt: Double, circ: Boolean): Double {
        val wtM = wt / 1000.0; val btM = bt / 1000.0
        return if (circ) {
            val r_ext = L/2.0 + wtM
            (PI * r_ext.pow(2) * (H + btM)) - (PI * (L/2.0).pow(2) * H)
        } else {
            (L + 2*wtM) * (B + 2*wtM) * (H + btM) - (L * B * H)
        }
    }

    private fun Double.format(n: Int) = String.format("%.${n}f", this)
    
    private fun getSystemName(t: TankType) = when(t) {
        TankType.RECTANGULAR_GROUND -> "Ground Rectangular - Continuity Analysis"
        TankType.CIRCULAR_GROUND -> "Ground Circular - Hoop Tension"
        TankType.RECTANGULAR_ELEVATED -> "Elevated Rectangular - Slab-Wall Frame"
        TankType.CIRCULAR_ELEVATED -> "Elevated Circular - Ring Tension"
        TankType.RECTANGULAR_UNDERGROUND -> "Underground Rectangular - Soil Interaction"
        else -> "Professional Water Tank Design"
    }

    private fun selectBarDiameter(asRequired: Double, memberThickness: Double): Double {
        val availableBars = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0)
        val maxBarsPerMeter = (1000.0 / (min(25.0, memberThickness / 10.0))).toInt().coerceAtMost(25)
        return availableBars.firstOrNull { dia ->
            val area = PI * dia * dia / 4
            ceil(asRequired / area).toInt() <= maxBarsPerMeter
        } ?: 12.0
    }
}
