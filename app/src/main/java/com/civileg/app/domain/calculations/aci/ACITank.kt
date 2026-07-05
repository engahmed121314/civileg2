package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.ReinforcementResult
import kotlin.math.*

/**
 * تصميم خزانات المياه حسب ACI 318-19 / ACI 350-06 (Environmental Concrete Structures)
 * يغطي الخزانات المستطيلة والدائرية (أرضية، مرتفعة، تحت الأرض)
 *
 * الاختلافات عن ECP:
 * - f'c = 0.8 × fcu (تحويل مكعب لأسطوانة)
 * - φ = 0.90 للانحناء، φ = 0.75 للقص
 * - طريقة Rn-ρ بدلاً من K-method
 * - معامل تحميل السائل: 1.4F (ACI 350-06)
 * - أقصى عرض شق: 0.25mm (ACI 350 vs 0.2mm ECP)
 */
class ACITank : TankDesign {

    companion object {
        private const val PHI_FLEXURE = 0.90
        private const val PHI_SHEAR = 0.75
        private const val GAMMA_W = 9.81
        private const val CONCRETE_DENSITY = 25.0
        private const val MIN_COVER = 50.0
        private const val MIN_WALL_THICKNESS = 200.0
        private const val MIN_BASE_THICKNESS = 250.0
        private const val CRACK_WIDTH_LIMIT = 0.25 // ACI 350-06: 0.25mm
        private const val MIN_RHO_ENV = 0.0020  // ACI 350-06: 0.20% min for environmental
        private const val FLUID_LOAD_FACTOR = 1.4  // ACI 350-06 Sec. 9.2.1
    }

    override fun calculateTank(
        length: Double, width: Double, height: Double,
        waterDepth: Double, fcu: Double, fy: Double, type: TankType
    ): TankResult {
        val warnings = mutableListOf<String>()
        val safetyChecks = mutableListOf<TankSafetyCheck>()
        val codeNotes = mutableListOf<String>()

        // تحويل إلى متر
        val L = length / 1000.0
        val B = width / 1000.0
        val H = height / 1000.0
        val hW = waterDepth / 1000.0

        // f'c = 0.8 × fcu (مكعب → أسطوانة)
        val fcPrime = 0.8 * fcu

        // 1. السعة
        val capacityM3 = L * B * hW

        // 2. سمك الجدران والقاعدة
        val wallThickness = max(H / 12.0 * 1000, MIN_WALL_THICKNESS).let { ceil(it / 25.0) * 25.0 }
        val baseThickness = max(B / 10.0 * 1000, MIN_BASE_THICKNESS).let { ceil(it / 25.0) * 25.0 }

        // 3. الضغط الهيدروستاتيكي الأقصى
        val maxPressure = GAMMA_W * hW

        // 4. نوع الخزان
        val isCircular = type in listOf(TankType.CIRCULAR, TankType.CIRCULAR_GROUND,
            TankType.CIRCULAR_ELEVATED, TankType.CIRCULAR_UNDERGROUND)
        val isUnderground = type in listOf(TankType.RECTANGULAR_UNDERGROUND, TankType.CIRCULAR_UNDERGROUND)

        // 5. تصميم الجدران
        val wallDesignResult = if (isCircular) {
            designCircularWall(L, B, H, hW, fcPrime, fy, wallThickness, warnings, codeNotes, safetyChecks)
        } else {
            designRectangularWall(L, B, H, hW, fcPrime, fy, wallThickness, warnings, codeNotes, safetyChecks)
        }

        // 6. تصميم القاعدة
        val baseDesignResult = designBase(
            L, B, H, hW, fcPrime, fy, baseThickness, wallThickness,
            isCircular, isUnderground, warnings, codeNotes, safetyChecks
        )

        // 7. الكميات والتكلفة
        val wallThicknessM = wallThickness / 1000.0
        val baseThicknessM = baseThickness / 1000.0
        val wallArea = if (isCircular) {
            val radius = min(L, B) / 2.0
            2 * PI * radius * H * wallThicknessM
        } else {
            2 * (L + B) * H * wallThicknessM
        }
        val baseArea = L * B * baseThicknessM
        val concreteVolume = wallArea + baseArea
        val steelWeightKgPerM3 = 120.0
        val steelWeight = concreteVolume * steelWeightKgPerM3
        val cost = concreteVolume * 5000.0 + (steelWeight / 1000.0) * 55000.0

        // 8. فحص الرفع للخزانات تحت الأرض
        var upliftFS = 0.0
        if (isUnderground) {
            val tankWeight = concreteVolume * CONCRETE_DENSITY
            val upliftForce = L * B * H * GAMMA_W
            upliftFS = tankWeight / upliftForce
            safetyChecks.add(TankSafetyCheck(
                "Uplift Safety Factor", upliftFS, 1.25, "-",
                upliftFS >= 1.25, "ACI 350-06: Stability against buoyancy"
            ))
        }

        val isSafe = safetyChecks.all { it.isSafe }

        return TankResult(
            wallThickness = wallThickness,
            baseThickness = baseThickness,
            wallReinforcement = wallDesignResult,
            baseReinforcement = baseDesignResult,
            capacityM3 = capacityM3,
            concreteVolume = concreteVolume,
            steelWeight = steelWeight,
            cost = cost,
            isSafe = isSafe,
            pressure = maxPressure,
            maxMomentWall = wallDesignResult.astRequired * 0.15,
            maxMomentBase = baseDesignResult.astRequired * 0.12,
            maxShearWall = GAMMA_W * hW * hW / 2.0,
            factorOfSafetyUplift = upliftFS,
            structuralSystem = when (type) {
                TankType.RECTANGULAR_GROUND -> "ACI 350-06: Ground Rectangular - Cantilever Wall"
                TankType.CIRCULAR_GROUND -> "ACI 350-06: Ground Circular - Hoop Tension"
                TankType.RECTANGULAR_ELEVATED -> "ACI 350-06: Elevated Rectangular - Cantilever"
                TankType.CIRCULAR_ELEVATED -> "ACI 350-06: Elevated Circular - Hoop Tension"
                TankType.RECTANGULAR_UNDERGROUND -> "ACI 350-06: Underground Rectangular"
                TankType.CIRCULAR_UNDERGROUND -> "ACI 350-06: Underground Circular"
                TankType.RECTANGULAR -> "ACI 350-06: Rectangular Tank - Cantilever"
                TankType.CIRCULAR -> "ACI 350-06: Circular Tank - Hoop Tension"
            },
            recommendations = listOf(
                "Use water-stop joints at construction joints (ACI 350-06)",
                "Min cover: 50mm (water face), 40mm (exterior)",
                "Cure concrete minimum 7 days with wet burlap",
                "Perform leak test before backfill (underground tanks)"
            ),
            safetyChecks = safetyChecks,
            warnings = warnings
        )
    }

    /**
     * تصميم جدار مستطيل - طريقة الناتئ (Cantilever Wall)
     * M = γw × h³ / 6  (مع معامل تحميل 1.4F للسوائل)
     * ACI 350-06: Rn-ρ method مع f'c
     */
    private fun designRectangularWall(
        L: Double, B: Double, H: Double, hW: Double,
        fcPrime: Double, fy: Double, wallThickness: Double,
        warnings: MutableList<String>, codeNotes: MutableList<String>,
        safetyChecks: MutableList<TankSafetyCheck>
    ): ReinforcementResult {
        val d = wallThickness - MIN_COVER - 10.0
        val b = 1000.0

        // العزم الأقصى عند القاعدة (معامل 1.4F للسوائل)
        val maxMoment = GAMMA_W * hW * hW * hW / 6.0 * FLUID_LOAD_FACTOR
        val maxShear = GAMMA_W * hW * hW / 2.0 * FLUID_LOAD_FACTOR

        // Rn-ρ method (ACI 318-19)
        val Mu_Nmm = maxMoment * 1e6
        val Rn = Mu_Nmm / (PHI_FLEXURE * b * d * d)

        // β₁ = 0.85 (fc' ≤ 28 MPa)
        val beta1 = if (fcPrime <= 28.0) 0.85 else max(0.65, 0.85 - 0.05 * (fcPrime - 28.0) / 7.0)

        // ρ = (0.85 × f'c / fy) × [1 - √(1 - 2×Rn/(0.85×f'c))]
        val discriminant = 1.0 - 2.0 * Rn / (0.85 * fcPrime)
        val rho = if (discriminant > 0) {
            (0.85 * fcPrime / fy) * (1.0 - sqrt(discriminant))
        } else {
            warnings.add("ACI 350: Compression failure - increase wall thickness")
            0.025
        }

        // ρ_min حسب ACI 350-06
        val rhoMin = max(MIN_RHO_ENV, 1.33 * sqrt(fcPrime) / fy)
        val rhoMax = 0.025
        val rhoFinal = rho.coerceIn(rhoMin, rhoMax)
        var asRequired = rhoFinal * b * d

        // اختيار السيخ العمودي
        val barDiameter = selectBarDiameter(asRequired, wallThickness)
        val barArea = PI * barDiameter * barDiameter / 4.0
        val barsPerMeter = ceil(asRequired / barArea).toInt().coerceIn(7, 20)
        val spacing = floor(1000.0 / barsPerMeter).coerceIn(100.0, 300.0)
        val asProvided = (1000.0 / spacing) * barArea

        // التسليح الأفقي (30-40% من العمودي)
        val asHoriz = asProvided * 0.35
        val hBarDia = selectBarDiameter(asHoriz, wallThickness)
        val hBarArea = PI * hBarDia * hBarDia / 4.0
        val hBarsPerMeter = ceil(asHoriz / hBarArea).toInt().coerceIn(6, 16)
        val hSpacing = floor(1000.0 / hBarsPerMeter).coerceIn(100.0, 300.0)

        // فحص القص (ACI 318-22.5.5.1): Vc = 0.17λ√f'c
        val Vc = PHI_SHEAR * 0.17 * sqrt(fcPrime) * b * d / 1000.0
        val shearSafe = maxShear <= Vc
        safetyChecks.add(TankSafetyCheck(
            "Wall Shear", maxShear, Vc, "kN/m",
            shearSafe, "ACI 318: Vc = 0.17√f'c × b × d"
        ))

        // فحص عرض الشق (ACI 350-06 Sec. 10.5)
        // wk ≈ 0.011 × β × fs × (dc × A)^(1/3) / (dc × (d_c * A)^...)
        // Simplified: fs = M / (As × jd), check fs ≤ allowable
        val jd = d * 0.875
        val fs = maxMoment * 1e6 / (asProvided * jd)  // N/mm²
        val fsAllowable = min(fy * 0.6, 240.0)  // ACI 350-06: fs ≤ 0.6fy or 240 MPa
        val crackSafe = fs <= fsAllowable
        safetyChecks.add(TankSafetyCheck(
            "Crack Control (fs)", fs, fsAllowable, "MPa",
            crackSafe, "ACI 350-06: fs = M/(As×jd), fs_max = min(0.6fy, 240MPa)"
        ))

        // فحص نسبة التسليح
        val rhoActual = asProvided / (b * d)
        safetyChecks.add(TankSafetyCheck(
            "Wall Reinforcement Ratio", rhoActual, MIN_RHO_ENV, "-",
            rhoActual >= MIN_RHO_ENV, "ACI 350-06: Min ρ = 0.20% for environmental structures"
        ))

        codeNotes.add("ACI 350-06 / ACI 318-19: Water-Retaining Structure")
        codeNotes.add("f'c=%.0f MPa (0.8×fcu), φ_flex=%.2f".format(fcPrime, PHI_FLEXURE))
        codeNotes.add("Fluid load factor: %.1f".format(FLUID_LOAD_FACTOR))
        codeNotes.add("Vertical: %dØ%d @ %dmm".format(barsPerMeter, barDiameter.toInt(), spacing.toInt()))
        codeNotes.add("Horizontal: %dØ%d @ %dmm".format(hBarsPerMeter, hBarDia.toInt(), hSpacing.toInt()))
        codeNotes.add("ρ=%.4f, ρ_min=%.4f".format(rhoFinal, rhoMin))

        return ReinforcementResult(
            astRequired = asRequired,
            astProvided = asProvided,
            barDiameter = barDiameter,
            numberOfBars = barsPerMeter,
            tiesDiameter = hBarDia,
            tiesSpacing = hSpacing,
            isSafe = shearSafe && crackSafe && rhoActual >= MIN_RHO_ENV,
            utilizationRatio = asRequired / asProvided,
            spacing = spacing,
            warnings = warnings,
            codeNotes = codeNotes,
            description = "V: %dØ%d@%dmm, H: %dØ%d@%dmm".format(
                barsPerMeter, barDiameter.toInt(), spacing.toInt(),
                hBarsPerMeter, hBarDia.toInt(), hSpacing.toInt()
            )
        )
    }

    /**
     * تصميم جدار دائري - طريقة شد الحلقة (Hoop Tension)
     * T = γw × h × R (معامل 1.4F)
     */
    private fun designCircularWall(
        L: Double, B: Double, H: Double, hW: Double,
        fcPrime: Double, fy: Double, wallThickness: Double,
        warnings: MutableList<String>, codeNotes: MutableList<String>,
        safetyChecks: MutableList<TankSafetyCheck>
    ): ReinforcementResult {
        val radius = min(L, B) / 2.0
        val d = wallThickness - MIN_COVER - 10.0
        val b = 1000.0

        // أقصى شد حلقي عند القاعدة
        val maxHoopTension = GAMMA_W * hW * radius * FLUID_LOAD_FACTOR

        // عزم الانحناء العمودي
        val maxMoment = GAMMA_W * hW * hW * hW / 15.0 * FLUID_LOAD_FACTOR

        // تصميم التسليح الحلقي: As = T / φ × fy
        var asHoopRequired = maxHoopTension * 1000.0 / (PHI_FLEXURE * fy)
        val asMin = MIN_RHO_ENV * b * d
        asHoopRequired = max(asHoopRequired, asMin)

        // تصميم التسليح العمودي (Rn-ρ method)
        val Mu_Nmm = maxMoment * 1e6
        val Rn = Mu_Nmm / (PHI_FLEXURE * b * d * d)
        val disc = 1.0 - 2.0 * Rn / (0.85 * fcPrime)
        val rhoVert = if (disc > 0) (0.85 * fcPrime / fy) * (1.0 - sqrt(disc)) else 0.0
        var asVerticalRequired = max(rhoVert * b * d, asMin * 0.6)

        // اختيار أسياخ الحلقة
        val hoopBarDia = selectBarDiameter(asHoopRequired, wallThickness)
        val hoopBarArea = PI * hoopBarDia * hoopBarDia / 4.0
        val hoopBarsPerMeter = ceil(asHoopRequired / hoopBarArea).toInt().coerceIn(7, 20)
        val hoopSpacing = floor(1000.0 / hoopBarsPerMeter).coerceIn(100.0, 300.0)

        // اختيار أسياخ عمودية
        val vertBarDia = selectBarDiameter(asVerticalRequired, wallThickness)
        val vertBarArea = PI * vertBarDia * vertBarDia / 4.0
        val vertBarsPerMeter = ceil(asVerticalRequired / vertBarArea).toInt().coerceIn(6, 16)
        val vertSpacing = floor(1000.0 / vertBarsPerMeter).coerceIn(100.0, 300.0)

        // فحص الشق الحلقي
        val hoopStress = maxHoopTension / (wallThickness / 1000.0)
        val fct = 0.62 * sqrt(fcPrime)
        val isCrackSafe = hoopStress <= fct

        safetyChecks.add(TankSafetyCheck(
            "Hoop Tension Stress", hoopStress, fct, "kN/m²",
            isCrackSafe, "ACI 350-06: Hoop stress vs tensile strength"
        ))

        codeNotes.add("ACI 350-06: Circular Tank - Hoop Tension")
        codeNotes.add("T_max = γw×H×R × %.1f = %.1f kN/m".format(FLUID_LOAD_FACTOR, maxHoopTension))
        codeNotes.add("Hoop: %dØ%d @ %dmm".format(hoopBarsPerMeter, hoopBarDia.toInt(), hoopSpacing.toInt()))
        codeNotes.add("Vertical: %dØ%d @ %dmm".format(vertBarsPerMeter, vertBarDia.toInt(), vertSpacing.toInt()))

        if (!isCrackSafe) {
            warnings.add("ACI 350: Increase wall thickness or reduce spacing for crack control")
        }

        return ReinforcementResult(
            astRequired = asHoopRequired,
            astProvided = (1000.0 / hoopSpacing) * hoopBarArea,
            barDiameter = hoopBarDia,
            numberOfBars = hoopBarsPerMeter,
            tiesDiameter = vertBarDia,
            tiesSpacing = vertSpacing,
            isSafe = isCrackSafe,
            utilizationRatio = asHoopRequired / ((1000.0 / hoopSpacing) * hoopBarArea),
            spacing = hoopSpacing,
            warnings = warnings,
            codeNotes = codeNotes,
            description = "Hoop: %dØ%d@%dmm, Vert: %dØ%d@%dmm".format(
                hoopBarsPerMeter, hoopBarDia.toInt(), hoopSpacing.toInt(),
                vertBarsPerMeter, vertBarDia.toInt(), vertSpacing.toInt()
            )
        )
    }

    /**
     * تصميم القاعدة - Rn-ρ method
     */
    private fun designBase(
        L: Double, B: Double, H: Double, hW: Double,
        fcPrime: Double, fy: Double, baseThickness: Double, wallThickness: Double,
        isCircular: Boolean, isUnderground: Boolean,
        warnings: MutableList<String>, codeNotes: MutableList<String>,
        safetyChecks: MutableList<TankSafetyCheck>
    ): ReinforcementResult {
        val d = baseThickness - MIN_COVER - 10.0
        val b = 1000.0

        // حمولات القاعدة
        val baseSelfWeight = baseThickness / 1000.0 * CONCRETE_DENSITY
        val waterPressureOnBase = GAMMA_W * hW * FLUID_LOAD_FACTOR
        val totalPressure = waterPressureOnBase + baseSelfWeight

        // العزم التصميمي (ناتئ من وجه الجدار الداخلي)
        val projection = if (isCircular) min(L, B) / 2.0
            else min(L, B) / 2.0 - wallThickness / 2000.0
        val maxMoment = totalPressure * projection * projection / 2.0

        // Rn-ρ method
        val Mu_Nmm = maxMoment * 1e6
        val Rn = Mu_Nmm / (PHI_FLEXURE * b * d * d)
        val disc = 1.0 - 2.0 * Rn / (0.85 * fcPrime)
        val rho = if (disc > 0) (0.85 * fcPrime / fy) * (1.0 - sqrt(disc)) else 0.0
        val rhoMin = max(MIN_RHO_ENV, 1.33 * sqrt(fcPrime) / fy)
        val rhoFinal = rho.coerceIn(rhoMin, 0.025)
        var asRequired = rhoFinal * b * d

        // اختيار التسليح
        val barDiameter = selectBarDiameter(asRequired, baseThickness)
        val barArea = PI * barDiameter * barDiameter / 4.0
        val barsPerMeter = ceil(asRequired / barArea).toInt().coerceIn(6, 20)
        val spacing = floor(1000.0 / barsPerMeter).coerceIn(100.0, 300.0)
        val asProvided = (1000.0 / spacing) * barArea

        // فحص قص الاختراق (ACI 318-22.6.5)
        val b0 = if (isCircular) 2 * PI * (wallThickness / 1000.0 + d / 1000.0)
            else 2.0 * (2.0 * wallThickness / 1000.0 + 2.0 * d / 1000.0)
        val vc_punch = 0.33 * sqrt(fcPrime)  // MPa
        val punchingCap = PHI_SHEAR * vc_punch * b0 * d / 1000.0
        val punchingLoad = totalPressure * L * B * 0.5
        val punchingSafe = punchingLoad <= punchingCap
        safetyChecks.add(TankSafetyCheck(
            "Punching Shear (Base)", punchingLoad, punchingCap, "kN",
            punchingSafe, "ACI 318: vc = 0.33√f'c × b₀ × d"
        ))

        codeNotes.add("Base: %dØ%d @ %dmm (each way)".format(
            barsPerMeter, barDiameter.toInt(), spacing.toInt()))

        return ReinforcementResult(
            astRequired = asRequired,
            astProvided = asProvided,
            barDiameter = barDiameter,
            numberOfBars = barsPerMeter,
            tiesDiameter = barDiameter,
            tiesSpacing = spacing,
            isSafe = punchingSafe,
            utilizationRatio = asRequired / asProvided,
            spacing = spacing,
            warnings = warnings,
            codeNotes = codeNotes,
            description = "%dØ%d @ %dmm (each way)".format(
                barsPerMeter, barDiameter.toInt(), spacing.toInt())
        )
    }

    private fun selectBarDiameter(asRequired: Double, thickness: Double): Double {
        val availableBars = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0)
        val maxBars = (1000.0 / min(25.0, thickness / 10.0)).toInt().coerceAtMost(25)
        return availableBars.firstOrNull { dia ->
            val area = PI * dia * dia / 4.0
            ceil(asRequired / area).toInt() <= maxBars
        } ?: 16.0
    }
}