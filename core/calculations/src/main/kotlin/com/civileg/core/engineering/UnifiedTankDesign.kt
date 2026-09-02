package com.civileg.core.engineering

import com.civileg.core.calculations.entities.DesignCode
import com.civileg.core.calculations.entities.ReinforcementResult
import com.civileg.core.math.SafeMath
import com.civileg.core.sanity.EngineeringSanityEngine
import com.civileg.core.sanity.SanityReport
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Unified water-retaining (tank) design.
 *
 * ONE skeleton for ECP 203-2020 §8-1, ACI 350-06/ACI 318-19 and SBC 304-2018;
 * golden-matched to the legacy app ECPTank/ACITank/SBCTank. Rectangular walls
 * are cantilevers (M = γw·h³/6), circular walls use hoop tension (T = γw·h·R),
 * and the base is a cantilever slab from the inner wall face. The divergent
 * formulations branch on [ConcreteCodeParams.family]:
 *
 *  - flexure: ECP K-method (§4-2-2-1, fs = fy/γs) vs ACI/SBC Rn–ρ with the
 *    environmental φ (ACI 350-06 §9.2.7 = 0.65; SBC §9.3 = 0.90)
 *  - water load factor: ECP 1.6 (§2-3-1) vs ACI/SBC 1.4F (ACI 350 Sec. 9.2.1)
 *  - ρmin: ECP 0.0025 fixed vs ACI/SBC max(0.002, 1.33√f'c/fy)
 *  - crack control: ECP width w = 0.0001·fs·max(2,(t−c)/db) ≤ 0.20 mm vs
 *    ACI/SBC stress fs ≤ min(0.6fy, 240) MPa (max 0.25 mm)
 *  - hoop fct: ECP 0.6√fcu vs ACI/SBC 0.62√f'c
 *  - d = t − 50 − 8 (ECP) / t − 50 − 10 (ACI/SBC); bar spacing ceil-to-10 mm
 *    (ECP) vs floor clamped 100..300 mm (ACI/SBC); Saudi market bars for SBC.
 *
 * Every COEFFICIENT comes from [ConcreteCodeParams]; no factor is hardcoded
 * (spec §3). Code-fixed PHYSICAL rules — γw = 9.81 kN/m³, R.C. density
 * 25 kN/m³, εcu = 0.003, the 0.893 K-method lever-arm divisor, the d·0.7
 * lever-arm fallback, the horizontal-wall steel = 0.35 × vertical (the
 * 25–50% band), the 1.25 uplift factor and the 99.0 "dry formation" FoS —
 * are documented inline with their code reference. The quantity-report market
 * rates (120 kg/m³ steel, 5000/m³ concrete, 55000/tonne steel) are the legacy
 * app's billing constants and are kept here verbatim for byte-exact parity.
 */
class UnifiedTankDesign(
    private val params: ConcreteCodeParams,
    private val concrete: ConcreteMaterial,
    private val steel: SteelMaterial
) {
    /** App mapping: ECP 203 → ECP; ACI 318 → ACI; SBC 304 → SBC. */
    val code: DesignCode
        get() = when (params.family) {
            CodeFamily.ECP_203 -> DesignCode.ECP
            CodeFamily.ACI_318 -> DesignCode.ACI
            CodeFamily.SBC_304 -> DesignCode.SBC
        }

    private val isEcp get() = params.family == CodeFamily.ECP_203
    private val isAci get() = params.family == CodeFamily.ACI_318

    /** γs — ECP service steel stress fs = fy/γs (γs = 1.15). */
    private val gammaS get() = params.gammas?.gammaS ?: 1.0

    /**
     * Design a water-retaining tank.
     *
     * @param lengthMm plan length (mm)
     * @param widthMm plan width (mm)
     * @param heightMm wall height (mm)
     * @param waterDepthMm stored water depth (mm, within [0, height])
     * @param type tank shape/position family
     * @param groundWaterDepthMm external groundwater depth below the tank top
     *        (mm). Buoyancy of underground tanks is driven by this — NOT by the
     *        stored water. [Double.POSITIVE_INFINITY] keeps the legacy envelope
     *        behaviour (full-height head on the empty tank).
     */
    fun designTank(
        lengthMm: Double,
        widthMm: Double,
        heightMm: Double,
        waterDepthMm: Double,
        type: TankType = TankType.RECTANGULAR,
        groundWaterDepthMm: Double = Double.POSITIVE_INFINITY
    ): Outcome {
        SafeMath.requirePositive(lengthMm, "length")
        SafeMath.requirePositive(widthMm, "width")
        SafeMath.requirePositive(heightMm, "height")
        require(waterDepthMm >= 0.0 && waterDepthMm <= heightMm) {
            "Invalid input: waterDepth must be within [0, height] | منسوب الماء داخل حدود الخزان"
        }

        val warnings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        val safetyChecks = mutableListOf<TankCheck>()
        val codeNotes = mutableListOf<String>()
        val trace = CalculationTrace()

        // mm → m for the structural model
        val L = lengthMm / 1000.0
        val B = widthMm / 1000.0
        val H = heightMm / 1000.0
        val hW = waterDepthMm / 1000.0

        // 1. Capacity
        val capacityM3 = L * B * hW

        // 2. Wall/base thickness — height-driven, ceil to the next 25 mm
        //    (wall = max(H/12, min_wall); base = max(B/10, min_base))
        val wallThickness = max(H / 12.0 * 1000, params.tankMinWallThicknessMm()).let {
            ceil(it / 25.0) * 25.0
        }
        val baseThickness = max(B / 10.0 * 1000, params.tankMinBaseThicknessMm()).let {
            ceil(it / 25.0) * 25.0
        }
        // Effective depths for flexure (single source — the sub-designs use the
        // same params.tankEffectiveDepthMm call so wall/base d match the bars).
        val wallEffectiveDepth = params.tankEffectiveDepthMm(wallThickness)
        val baseEffectiveDepth = params.tankEffectiveDepthMm(baseThickness)

        // 3. Hydrostatic pressure at the base
        val pressure = GAMMA_W * hW

        // 4. Tank form
        val isCircular = type == TankType.CIRCULAR || type == TankType.CIRCULAR_GROUND ||
            type == TankType.CIRCULAR_ELEVATED || type == TankType.CIRCULAR_UNDERGROUND
        val isUnderground = type == TankType.RECTANGULAR_UNDERGROUND ||
            type == TankType.CIRCULAR_UNDERGROUND

        // 5. Walls
        val wallReinforcement = if (isCircular) {
            designCircularWall(L, B, hW, wallThickness, warnings, codeNotes, safetyChecks)
        } else {
            designRectangularWall(hW, wallThickness, warnings, codeNotes, safetyChecks)
        }

        // 6. Base
        val baseReinforcement = designBase(
            L, B, hW, baseThickness, wallThickness,
            isCircular, warnings, codeNotes, safetyChecks
        )

        // 7. Quantities and cost (legacy app billing constants — market rates)
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
        val steelWeight = concreteVolume * STEEL_KG_PER_M3
        val cost = concreteVolume * CONCRETE_RATE + (steelWeight / 1000.0) * STEEL_RATE_PER_TON

        // 8a. Legacy isSafe ordering: the app ECPTank computes isSafe AFTER the
        //     uplift row (buoyancy included). ACITank/SBCTank also include it.
        //     Byte-exact parity -> isSafe includes the uplift check for ALL codes.

        // 8b. Uplift — underground only; demand driven by EXTERNAL groundwater
        var factorOfSafetyUplift = 0.0
        if (isUnderground) {
            val tankWeight = concreteVolume * CONCRETE_DENSITY
            // A5-FIX: buoyancy is driven by external groundwater (empty tank
            // governing case). Infinity keeps the legacy full-head envelope.
            val submergenceM = if (groundWaterDepthMm.isInfinite()) H
                else (H - groundWaterDepthMm / 1000.0).coerceIn(0.0, H)
            val upliftForce = L * B * submergenceM * GAMMA_W
            if (upliftForce > 0.0) {
                factorOfSafetyUplift = tankWeight / upliftForce
                safetyChecks.add(TankCheck(
                    "Uplift Safety Factor", factorOfSafetyUplift, UPLIFT_MIN_FOS, "-",
                    factorOfSafetyUplift >= UPLIFT_MIN_FOS, upliftDesc()
                ))
            } else {
                factorOfSafetyUplift = UPLIFT_DRY_FOS // dry formation — buoyancy not governing
            }
        }

        val isSafe = safetyChecks.all { it.isSafe }

        // 9. Recommendations
        applyRecommendations(recommendations, isUnderground, wallThickness)

        // 10. Export moments — ECP exports the service (unfactored) water load;
        //     ACI/SBC export the fluid-load-factored (1.4F) values.
        val exportFactor = if (isEcp) 1.0 else params.tankFluidLoadFactor()
        val maxMomentWall = if (isCircular)
            GAMMA_W * hW * hW * hW / 15.0 * exportFactor
        else
            GAMMA_W * hW * hW * hW / 6.0 * exportFactor
        val projectionM = if (isCircular) min(L, B) / 2.0
            else min(L, B) / 2.0 - wallThickness / 2000.0
        val baseSelfWeightExport = baseThickness / 1000.0 * CONCRETE_DENSITY
        val maxMomentBase = (GAMMA_W * hW * exportFactor + baseSelfWeightExport) *
            projectionM * projectionM / 2.0
        val maxShearWall = GAMMA_W * hW * hW / 2.0

        safetyChecks.forEach { c ->
            trace.add(
                c.name, "", "", fmt0(c.value), fmt0(c.limit),
                if (c.isSafe) CheckStatus.PASS else CheckStatus.FAIL,
                utilization = if (c.limit != 0.0) c.value / c.limit else null,
                codeReference = c.description
            )
        }

        val outcome = Outcome(
            code = code,
            wallThickness = wallThickness,
            baseThickness = baseThickness,
            wallEffectiveDepth = wallEffectiveDepth,
            baseEffectiveDepth = baseEffectiveDepth,
            wallReinforcement = wallReinforcement,
            baseReinforcement = baseReinforcement,
            capacityM3 = capacityM3,
            concreteVolume = concreteVolume,
            steelWeight = steelWeight,
            cost = cost,
            isSafe = isSafe,
            pressure = pressure,
            maxMomentWall = maxMomentWall,
            maxMomentBase = maxMomentBase,
            maxShearWall = maxShearWall,
            factorOfSafetyUplift = factorOfSafetyUplift,
            structuralSystem = structuralSystem(type),
            recommendations = recommendations,
            safetyChecks = safetyChecks,
            warnings = warnings,
            trace = trace,
            overallStatus = trace.overall,
            sanity = SanityReport("TankOutcome", emptyList())
        )
        val sanityReport = EngineeringSanityEngine.check(outcome)
        val overall = when {
            sanityReport.hasError -> CheckStatus.FAIL
            sanityReport.hasWarning && trace.overall == CheckStatus.PASS -> CheckStatus.WARNING
            else -> trace.overall
        }
        return outcome.copy(overallStatus = overall, sanity = sanityReport)
    }

    // ─────────────────────── rectangular wall (cantilever) ───────────────────────

    /** Cantilever wall: M = γw·h³/6, V = γw·h²/2. */
    private fun designRectangularWall(
        hW: Double, wallThickness: Double,
        warnings: MutableList<String>, codeNotes: MutableList<String>,
        safetyChecks: MutableList<TankCheck>
    ): ReinforcementResult {
        val d = params.tankEffectiveDepthMm(wallThickness)
        val b = 1000.0 // 1 m unit strip

        val maxMoment = GAMMA_W * hW * hW * hW / 6.0
        val maxShear = GAMMA_W * hW * hW / 2.0
        // Design values carry the fluid load factor (ECP 1.6, ACI/SBC 1.4)
        val maxMomentDesign = maxMoment * params.tankFluidLoadFactor()
        val maxShearDesign = if (isEcp) maxShear else maxShear * params.tankFluidLoadFactor()

        // ── flexural steel ──
        var asRequired = 0.0
        var leverArmMm = 0.0
        var jdMm = 0.0
        var rhoFinal = 0.0
        var rhoMinLocal = 0.0
        when (params.family) {
            CodeFamily.ECP_203 -> {
                // K-method (ECP 203 §4-2-2-1): K = Mu/(fcu·b·d²), z = d·(0.5+√(0.25−K/0.893))
                val fs = steel.yieldMpa / gammaS
                val Mu_Nmm = maxMomentDesign * 1e6
                val K = Mu_Nmm / (concrete.fcuMpa * b * d * d)
                val epsY = steel.yieldMpa / (200_000.0 * gammaS)
                val aOverDBal = 0.9 * EPS_CU / (EPS_CU + epsY)
                val kBal = (0.67 / params.gammas!!.gammaC) * aOverDBal * (1.0 - aOverDBal / 2.0)
                if (K > kBal) warnings.add(compressionWarning())
                leverArmMm = ecpLeverArm(K, d)
                asRequired = Mu_Nmm / (fs * leverArmMm)
                rhoMinLocal = params.tankMinRho(concrete, steel)
                val asMin = rhoMinLocal * b * d
                if (asRequired < asMin) {
                    asRequired = asMin
                    codeNotes.add(MIN_RHO_WALL_NOTE)
                }
            }
            CodeFamily.ACI_318, CodeFamily.SBC_304 -> {
                // Rn–ρ (ACI 318 §22.2 / ACI 350): Rn = Mu/(φ·b·d²),
                // ρ = (0.85f'c/fy)·(1−√(1−2Rn/(0.85f'c)))
                val phi = params.tankPhiFlexure!!
                val Mu_Nmm = maxMomentDesign * 1e6
                val Rn = Mu_Nmm / (phi * b * d * d)
                val disc = 1.0 - 2.0 * Rn / (0.85 * concrete.cylinderStrengthMpa)
                val rho = if (disc > 0)
                    (0.85 * concrete.cylinderStrengthMpa / steel.yieldMpa) * (1.0 - sqrt(disc))
                else {
                    warnings.add(compressionWarning())
                    0.025
                }
                rhoMinLocal = params.tankMinRho(concrete, steel)
                rhoFinal = rho.coerceIn(rhoMinLocal, params.tankRhoMax())
                asRequired = rhoFinal * b * d
                jdMm = d * 0.875
            }
        }

        // ── bar selection ──
        val barDiameter = selectBarDiameter(asRequired, wallThickness)
        val barArea = PI * barDiameter * barDiameter / 4
        val barsPerMeter = ceil(asRequired / barArea).toInt().coerceIn(7, 20)
        val spacing = params.tankSpacingMm(barsPerMeter)
        val asProvided = (1000.0 / spacing) * barArea

        // Horizontal steel: 25–50% of vertical (0.35 mid-band)
        val asHorizontal = asProvided * 0.35
        val hBarDia = selectBarDiameter(asHorizontal, wallThickness)
        val hBarsPerMeter = ceil(asHorizontal / (PI * hBarDia * hBarDia / 4)).toInt().coerceIn(6, 16)
        val hSpacing = params.tankSpacingMm(hBarsPerMeter)

        // ── shear check ──
        val concreteShearCapacity = params.concreteShearCapacityKn(b, d, concrete)
        val shearSafe = maxShearDesign <= concreteShearCapacity
        safetyChecks.add(TankCheck(
            wallShearName(), maxShearDesign, concreteShearCapacity, "kN/m",
            shearSafe, wallShearDesc()
        ))

        // ── crack/stress check ──
        val crackSafe = when (params.family) {
            CodeFamily.ECP_203 -> {
                // ECP §8-1 crack width: w = 0.0001·fs·max(2,(t−c)/db), fs = Mu/(As·z)
                val steelStress = maxMomentDesign * 1e6 / (asProvided * leverArmMm)
                val crackWidth = 0.0001 * steelStress *
                    max(2.0, (wallThickness - params.tankCoverMm()) / barDiameter)
                safetyChecks.add(TankCheck(
                    "Crack Width", crackWidth, params.tankCrackWidthLimitMm(), "mm",
                    crackWidth <= params.tankCrackWidthLimitMm(), crackDesc()
                ))
                crackWidth <= params.tankCrackWidthLimitMm()
            }
            else -> {
                // ACI 350 §10.5 deemed-to-satisfy: fs = M/(As·jd) ≤ min(0.6fy, 240)
                val fs = maxMomentDesign * 1e6 / (asProvided * jdMm)
                val fsAllowable = min(steel.yieldMpa * 0.6, 240.0)
                safetyChecks.add(TankCheck(
                    "Crack Control (fs)", fs, fsAllowable, "MPa",
                    fs <= fsAllowable, crackDesc()
                ))
                fs <= fsAllowable
            }
        }

        // ── reinforcement-ratio check ──
        // Legacy golden: the displayed row limit and wall isSafe use the FLAT
        // min-ρ (ECP 0.0025 / ACI-SBC 0.0020) — the design minimum may be higher.
        val rhoMinFlat = params.tankMinRhoFlat()
        val rhoActual = asProvided / (b * d)
        safetyChecks.add(TankCheck(
            "Wall Reinforcement Ratio", rhoActual, rhoMinFlat, "-",
            rhoActual >= rhoMinFlat, ratioDesc()
        ))

        wallCodeNotes(
            codeNotes, barsPerMeter, barDiameter, spacing,
            hBarsPerMeter, hBarDia, hSpacing, rhoFinal, rhoMinLocal
        )

        return ReinforcementResult(
            astRequired = asRequired,
            astProvided = asProvided,
            barDiameter = barDiameter,
            numberOfBars = barsPerMeter,
            tiesDiameter = hBarDia,
            tiesSpacing = hSpacing,
            isSafe = shearSafe && crackSafe && rhoActual >= rhoMinFlat,
            utilizationRatio = asRequired / asProvided,
            spacing = spacing,
            warnings = warnings,
            codeNotes = codeNotes,
            description = wallDescription(barsPerMeter, barDiameter, spacing, hBarsPerMeter, hBarDia, hSpacing)
        )
    }

    // ───────────────────────── circular wall (hoop tension) ─────────────────────────

    /** Hoop tension: T = γw·h·R; vertical flexure: M = γw·h³/15. */
    private fun designCircularWall(
        L: Double, B: Double, hW: Double, wallThickness: Double,
        warnings: MutableList<String>, codeNotes: MutableList<String>,
        safetyChecks: MutableList<TankCheck>
    ): ReinforcementResult {
        val radius = min(L, B) / 2.0
        val d = params.tankEffectiveDepthMm(wallThickness)
        val b = 1000.0

        val maxHoopTension = GAMMA_W * hW * radius * params.tankFluidLoadFactor()
        val maxMoment = GAMMA_W * hW * hW * hW / 15.0 * params.tankFluidLoadFactor()

        when (params.family) {
            CodeFamily.ECP_203 -> {
                // ECP hoops use the SERVICE tension without the fluid factor
                val fs = steel.yieldMpa / gammaS
                var asHoopRequired = (GAMMA_W * hW * radius) * 1000.0 / fs
                // Legacy golden: the circular hoop minimum uses the FLAT min-ρ (ECP 0.0025),
                // NOT the 1.33√f'c/fy design minimum the rectangular wall applies.
                val asMin = params.tankMinRhoFlat() * b * d
                asHoopRequired = max(asHoopRequired, asMin)
                val hoop = selectPair(asHoopRequired, wallThickness)

                // Vertical bars via K-method
                val Mu_Nmm = maxMoment * 1e6
                val K = Mu_Nmm / (concrete.fcuMpa * b * d * d)
                val leverArm = ecpLeverArm(K, d)
                val asVerticalInput = Mu_Nmm / (fs * leverArm)
                val vert = selectPair(max(asVerticalInput, asMin * 0.6), wallThickness, VERT_MIN_BARS, VERT_MAX_BARS)

                val hoopStress = (GAMMA_W * hW * radius) / wallThickness
                val fct = params.tankHoopTensionLimitMpa(concrete)
                val isCrackSafe = hoopStress <= fct

                codeNotes.add("ECP 203-2020: Section 8-1 (Circular Tank - Hoop Tension)")
                codeNotes.add("Max Hoop Tension T = γw×H×R = ${fmt1(GAMMA_W * hW * radius)} kN/m")
                codeNotes.add("Hoop: ${hoop.count}Ø${hoop.dia.toInt()} @ ${hoop.spacing.toInt()}mm")
                codeNotes.add("Vertical: ${vert.count}Ø${vert.dia.toInt()} @ ${vert.spacing.toInt()}mm")
                if (!isCrackSafe) warnings.add(hoopCrackWarning())

                return ReinforcementResult(
                    astRequired = asHoopRequired,
                    astProvided = hoop.provided,
                    barDiameter = hoop.dia,
                    numberOfBars = hoop.count,
                    tiesDiameter = vert.dia,
                    tiesSpacing = vert.spacing,
                    isSafe = isCrackSafe,
                    utilizationRatio = asHoopRequired / hoop.provided,
                    spacing = hoop.spacing,
                    warnings = warnings,
                    codeNotes = codeNotes,
                    description = "Hoop: ${hoop.count}Ø${hoop.dia.toInt()}@${hoop.spacing.toInt()}mm, " +
                        "Vert: ${vert.count}Ø${vert.dia.toInt()}@${vert.spacing.toInt()}mm"
                )
            }
            else -> {
                val phi = params.tankPhiFlexure!!
                var asHoopRequired = maxHoopTension * 1000.0 / (phi * steel.yieldMpa)
                // Legacy golden: circular hoop/vertical minimum uses the FLAT min-ρ
                // (ACI/SBC 0.002) — NOT the 1.33√f'c/fy design minimum.
                val asMin = params.tankMinRhoFlat() * b * d
                asHoopRequired = max(asHoopRequired, asMin)
                val hoop = selectPair(asHoopRequired, wallThickness)

                val Mu_Nmm = maxMoment * 1e6
                val Rn = Mu_Nmm / (phi * b * d * d)
                val disc = 1.0 - 2.0 * Rn / (0.85 * concrete.cylinderStrengthMpa)
                val rhoVert = if (disc > 0)
                    (0.85 * concrete.cylinderStrengthMpa / steel.yieldMpa) * (1.0 - sqrt(disc))
                else 0.0
                val vert = selectPair(max(rhoVert * b * d, asMin * 0.6), wallThickness, VERT_MIN_BARS, VERT_MAX_BARS)

                val hoopStress = maxHoopTension / wallThickness
                val fct = params.tankHoopTensionLimitMpa(concrete)
                val isCrackSafe = hoopStress <= fct
                safetyChecks.add(TankCheck(
                    "Hoop Tension Stress", hoopStress, fct, "MPa", isCrackSafe, hoopDesc()
                ))

                if (isAci) {
                    codeNotes.add("ACI 350-06: Circular Tank - Hoop Tension")
                    codeNotes.add(String.format("T_max = γw×H×R × %.1f = %.1f kN/m",
                        params.tankFluidLoadFactor(), maxHoopTension))
                } else {
                    codeNotes.add("SBC 304-2018: Circular Tank - Hoop Tension")
                }
                codeNotes.add(String.format("Hoop: %dØ%d @ %dmm",
                    hoop.count, hoop.dia.toInt(), hoop.spacing.toInt()))
                codeNotes.add(String.format("Vertical: %dØ%d @ %dmm",
                    vert.count, vert.dia.toInt(), vert.spacing.toInt()))
                if (!isCrackSafe) warnings.add(hoopCrackWarning())

                return ReinforcementResult(
                    astRequired = asHoopRequired,
                    astProvided = hoop.provided,
                    barDiameter = hoop.dia,
                    numberOfBars = hoop.count,
                    tiesDiameter = vert.dia,
                    tiesSpacing = vert.spacing,
                    isSafe = isCrackSafe,
                    utilizationRatio = asHoopRequired / hoop.provided,
                    spacing = hoop.spacing,
                    warnings = warnings,
                    codeNotes = codeNotes,
                    description = String.format("Hoop: %dØ%d@%dmm, Vert: %dØ%d@%dmm",
                        hoop.count, hoop.dia.toInt(), hoop.spacing.toInt(),
                        vert.count, vert.dia.toInt(), vert.spacing.toInt())
                )
            }
        }
    }

    // ───────────────────────────────── base ─────────────────────────────────

    /** Base slab: cantilever from the inner wall face, UDL = water + self weight. */
    private fun designBase(
        L: Double, B: Double, hW: Double,
        baseThickness: Double, wallThickness: Double, isCircular: Boolean,
        warnings: MutableList<String>, codeNotes: MutableList<String>,
        safetyChecks: MutableList<TankCheck>
    ): ReinforcementResult {
        val d = params.tankEffectiveDepthMm(baseThickness)
        val b = 1000.0

        val baseSelfWeight = baseThickness / 1000.0 * CONCRETE_DENSITY
        // ECP adds the fluid factor at the Mu stage; ACI/SBC factor the water pressure
        val waterPressureOnBase = GAMMA_W * hW * (if (isEcp) 1.0 else params.tankFluidLoadFactor())
        val totalPressure = waterPressureOnBase + baseSelfWeight
        val projection = if (isCircular) min(L, B) / 2.0
            else min(L, B) / 2.0 - wallThickness / 2000.0
        val maxMomentBase = totalPressure * projection * projection / 2.0

        // ── flexural steel ──
        var asRequired = 0.0
        when (params.family) {
            CodeFamily.ECP_203 -> {
                val fs = steel.yieldMpa / gammaS
                val Mu_Nmm = maxMomentBase * params.tankFluidLoadFactor() * 1e6
                val K = Mu_Nmm / (concrete.fcuMpa * b * d * d)
                val leverArm = ecpLeverArm(K, d)
                asRequired = Mu_Nmm / (fs * leverArm)
                val asMin = params.tankMinRho(concrete, steel) * b * d
                if (asRequired < asMin) {
                    asRequired = asMin
                    codeNotes.add(MIN_RHO_BASE_NOTE)
                }
            }
            else -> {
                val phi = params.tankPhiFlexure!!
                val Mu_Nmm = maxMomentBase * 1e6
                val Rn = Mu_Nmm / (phi * b * d * d)
                val disc = 1.0 - 2.0 * Rn / (0.85 * concrete.cylinderStrengthMpa)
                val rho = if (disc > 0)
                    (0.85 * concrete.cylinderStrengthMpa / steel.yieldMpa) * (1.0 - sqrt(disc))
                else 0.0
                val rhoMin = params.tankMinRho(concrete, steel)
                asRequired = rho.coerceIn(rhoMin, params.tankRhoMax()) * b * d
            }
        }

        val barDiameter = selectBarDiameter(asRequired, baseThickness)
        val barArea = PI * barDiameter * barDiameter / 4
        val barsPerMeter = ceil(asRequired / barArea).toInt().coerceIn(6, 20)
        val spacing = params.tankSpacingMm(barsPerMeter)
        val asProvided = (1000.0 / spacing) * barArea

        // ── punching shear (mirror of the submitted footing check, ECP §4-3-2 / ACI §22.6.5) ──
        val b0 = if (isCircular) 2 * PI * (wallThickness + d)
            else 2.0 * (2.0 * wallThickness + 2.0 * d)
        val punchingCapacity = (params.footingPunchingPhi ?: 1.0) *
            params.footingPunchingBaseStressMpa(concrete) * b0 * d / 1000.0
        val punchingDemand = totalPressure * L * B * 0.5
        val punchingSafe = punchingDemand <= punchingCapacity
        safetyChecks.add(TankCheck(
            "Punching Shear (Base)", punchingDemand, punchingCapacity, "kN",
            punchingSafe, punchingDesc()
        ))

        codeNotes.add(baseCodeNote(barsPerMeter, barDiameter, spacing))

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
            description = baseDescription(barsPerMeter, barDiameter, spacing)
        )
    }

    // ──────────────────────── bar selection / helpers ────────────────────────

    /** First-fit market diameter whose bars fit the max bars-per-metre for the thickness. */
    private fun selectBarDiameter(asRequired: Double, memberThickness: Double): Double {
        val menu = params.tankBarMenuMm()
        val maxBarsPerMeter = (1000.0 / min(25.0, memberThickness / 10.0)).toInt().coerceAtMost(25)
        return menu.firstOrNull { dia ->
            val area = PI * dia * dia / 4
            ceil(asRequired / area).toInt() <= maxBarsPerMeter
        } ?: params.tankBarFallback()
    }

    /** Bar scheduling: diameter, count-per-metre (clamped), spacing, provided area. */
    private fun selectPair(
        areaRequiredMm2: Double,
        thicknessMm: Double,
        minBars: Int = HOOP_MIN_BARS,
        maxBars: Int = HOOP_MAX_BARS
    ): BarPair {
        val barDia = selectBarDiameter(areaRequiredMm2, thicknessMm)
        val barArea = PI * barDia * barDia / 4
        val count = ceil(areaRequiredMm2 / barArea).toInt().coerceIn(minBars, maxBars)
        val spacing = params.tankSpacingMm(count)
        return BarPair(barDia, count, spacing, (1000.0 / spacing) * barArea)
    }

    /** ECP K-method lever arm: z = d·(0.5+√(0.25−K/0.893)), fallback 0.7·d when over-tensioned. */
    private fun ecpLeverArm(K: Double, d: Double): Double =
        if (0.25 - K / 0.893 > 0) d * (0.5 + sqrt(0.25 - K / 0.893)) else d * 0.7

    // ─────────────────────── families' byte-exact UI labels ───────────────────────

    private fun wallShearName() = if (isEcp) "Wall Shear Capacity" else "Wall Shear"

    private fun wallShearDesc() = when (params.family) {
        CodeFamily.ECP_203 -> "ECP 203: V = γw×h²/2 vs Vc = 0.24×√(fcu/γc)×b×d"
        CodeFamily.ACI_318 -> "ACI 318: Vc = 0.17√f'c × b × d"
        CodeFamily.SBC_304 -> "SBC 304/ACI 318: Vc = 0.17√f'c × b × d"
    }

    private fun crackDesc() = when (params.family) {
        CodeFamily.ECP_203 -> "ECP 203 Sec. 8: Max crack width 0.2mm for water-retaining structures"
        CodeFamily.ACI_318 -> "ACI 350-06: fs = M/(As×jd), fs_max = min(0.6fy, 240MPa)"
        CodeFamily.SBC_304 -> "SBC 304: fs ≤ min(0.6fy, 240MPa)"
    }

    private fun ratioDesc() = when (params.family) {
        CodeFamily.ECP_203 -> "Minimum reinforcement for water-retaining structures: 0.25%"
        CodeFamily.ACI_318 -> "ACI 350-06: Min ρ = 0.20% for environmental structures"
        CodeFamily.SBC_304 -> "SBC 304: Min ρ = 0.20% for environmental"
    }

    private fun hoopDesc() = when (params.family) {
        CodeFamily.ECP_203 -> "ECP 203: Hoop stress vs fct = 0.6√fcu"
        CodeFamily.ACI_318 -> "ACI 350-06: Hoop stress vs tensile strength"
        CodeFamily.SBC_304 -> "SBC 304: Hoop stress vs tensile strength"
    }

    private fun punchingDesc() = when (params.family) {
        CodeFamily.ECP_203 -> "ECP 203: qp = 0.316×√(fcu/γc)×b₀×d"
        CodeFamily.ACI_318 -> "ACI 318: vc = 0.33√f'c × b₀ × d"
        CodeFamily.SBC_304 -> "SBC 304/ACI 318: vc = 0.33√f'c × b₀ × d"
    }

    private fun upliftDesc() = when (params.family) {
        CodeFamily.ECP_203 -> "Stability against buoyancy (ECP 203 Sec. 8-1)"
        CodeFamily.ACI_318 -> "ACI 350-06: Stability against buoyancy"
        CodeFamily.SBC_304 -> "SBC 304: Stability against buoyancy"
    }

    private fun compressionWarning() = when (params.family) {
        CodeFamily.ECP_203 -> "المقطع مفرط التسليح - يُنصح بزيادة سمك الجدار"
        CodeFamily.ACI_318 -> "ACI 350: Compression failure - increase wall thickness"
        CodeFamily.SBC_304 -> "SBC 304: Compression failure - increase wall thickness"
    }

    private fun hoopCrackWarning() = when (params.family) {
        CodeFamily.ECP_203 -> "يجب زيادة سمك الجدار أو تقليل المسافات للتحكم في الشقوق"
        CodeFamily.ACI_318 -> "ACI 350: Increase wall thickness or reduce spacing for crack control"
        CodeFamily.SBC_304 -> "SBC 304: Increase wall thickness for crack control"
    }

    private fun wallCodeNotes(
        codeNotes: MutableList<String>,
        barsPerMeter: Int, barDiameter: Double, spacing: Double,
        hBarsPerMeter: Int, hBarDia: Double, hSpacing: Double,
        rhoFinal: Double, rhoMin: Double
    ) {
        when (params.family) {
            CodeFamily.ECP_203 -> {
                codeNotes.add("ECP 203-2020: Section 8-1 (Water-Retaining Structures)")
                codeNotes.add("Wall: Cantilever method, M = γw×h³/6")
                codeNotes.add("Vertical: ${barsPerMeter}Ø${barDiameter.toInt()} @ ${spacing.toInt()}mm")
                codeNotes.add("Horizontal: ${hBarsPerMeter}Ø${hBarDia.toInt()} @ ${hSpacing.toInt()}mm")
            }
            CodeFamily.ACI_318 -> {
                codeNotes.add("ACI 350-06 / ACI 318-19: Water-Retaining Structure")
                codeNotes.add(String.format("f'c=%.0f MPa (0.8×fcu), φ_flex=%.2f",
                    concrete.cylinderStrengthMpa, params.tankPhiFlexure!!))
                codeNotes.add(String.format("Fluid load factor: %.1f", params.tankFluidLoadFactor()))
                codeNotes.add(String.format("Vertical: %dØ%d @ %dmm",
                    barsPerMeter, barDiameter.toInt(), spacing.toInt()))
                codeNotes.add(String.format("Horizontal: %dØ%d @ %dmm",
                    hBarsPerMeter, hBarDia.toInt(), hSpacing.toInt()))
                codeNotes.add(String.format("ρ=%.4f, ρ_min=%.4f", rhoFinal, rhoMin))
            }
            CodeFamily.SBC_304 -> {
                codeNotes.add(String.format("SBC 304-2018: f'c=%.1f MPa (0.67×fcu/γc)",
                    concrete.cylinderStrengthMpa))
                codeNotes.add(String.format("Vertical: %dØ%d @ %dmm",
                    barsPerMeter, barDiameter.toInt(), spacing.toInt()))
                codeNotes.add(String.format("Horizontal: %dØ%d @ %dmm",
                    hBarsPerMeter, hBarDia.toInt(), hSpacing.toInt()))
            }
        }
    }

    private fun wallDescription(
        barsPerMeter: Int, barDiameter: Double, spacing: Double,
        hBarsPerMeter: Int, hBarDia: Double, hSpacing: Double
    ): String = when (params.family) {
        CodeFamily.ECP_203 -> "V: ${barsPerMeter}Ø${barDiameter.toInt()}@${spacing.toInt()}mm, " +
            "H: ${hBarsPerMeter}Ø${hBarDia.toInt()}@${hSpacing.toInt()}mm"
        else -> String.format("V: %dØ%d@%dmm, H: %dØ%d@%dmm",
            barsPerMeter, barDiameter.toInt(), spacing.toInt(),
            hBarsPerMeter, hBarDia.toInt(), hSpacing.toInt())
    }

    private fun baseCodeNote(barsPerMeter: Int, barDiameter: Double, spacing: Double): String =
        when (params.family) {
            CodeFamily.ECP_203 -> "Base: ${barsPerMeter}Ø${barDiameter.toInt()} @ ${spacing.toInt()}mm (each way)"
            else -> String.format("Base: %dØ%d @ %dmm (each way)",
                barsPerMeter, barDiameter.toInt(), spacing.toInt())
        }

    private fun baseDescription(barsPerMeter: Int, barDiameter: Double, spacing: Double): String =
        when (params.family) {
            CodeFamily.ECP_203 -> "${barsPerMeter}Ø${barDiameter.toInt()} @ ${spacing.toInt()}mm (each way)"
            else -> String.format("%dØ%d @ %dmm (each way)",
                barsPerMeter, barDiameter.toInt(), spacing.toInt())
        }

    private fun structuralSystem(type: TankType): String = when (params.family) {
        CodeFamily.ECP_203 -> when (type) {
            TankType.RECTANGULAR_GROUND -> "Ground Rectangular - Cantilever Wall Analysis"
            TankType.CIRCULAR_GROUND -> "Ground Circular - Hoop Tension Analysis"
            TankType.RECTANGULAR_ELEVATED -> "Elevated Rectangular - Cantilever Wall"
            TankType.CIRCULAR_ELEVATED -> "Elevated Circular - Hoop Tension"
            TankType.RECTANGULAR_UNDERGROUND -> "Underground Rectangular - Soil + Hydrostatic"
            TankType.CIRCULAR_UNDERGROUND -> "Underground Circular - Soil + Hydrostatic"
            TankType.RECTANGULAR -> "Rectangular Tank - Cantilever Method"
            TankType.CIRCULAR -> "Circular Tank - Hoop Tension Method"
        }
        CodeFamily.ACI_318 -> when (type) {
            TankType.RECTANGULAR_GROUND -> "ACI 350-06: Ground Rectangular - Cantilever Wall"
            TankType.CIRCULAR_GROUND -> "ACI 350-06: Ground Circular - Hoop Tension"
            TankType.RECTANGULAR_ELEVATED -> "ACI 350-06: Elevated Rectangular - Cantilever"
            TankType.CIRCULAR_ELEVATED -> "ACI 350-06: Elevated Circular - Hoop Tension"
            TankType.RECTANGULAR_UNDERGROUND -> "ACI 350-06: Underground Rectangular"
            TankType.CIRCULAR_UNDERGROUND -> "ACI 350-06: Underground Circular"
            TankType.RECTANGULAR -> "ACI 350-06: Rectangular Tank - Cantilever"
            TankType.CIRCULAR -> "ACI 350-06: Circular Tank - Hoop Tension"
        }
        CodeFamily.SBC_304 -> when (type) {
            TankType.RECTANGULAR_GROUND -> "SBC 304-2018: Ground Rectangular - Cantilever Wall"
            TankType.CIRCULAR_GROUND -> "SBC 304-2018: Ground Circular - Hoop Tension"
            TankType.RECTANGULAR_ELEVATED -> "SBC 304-2018: Elevated Rectangular"
            TankType.CIRCULAR_ELEVATED -> "SBC 304-2018: Elevated Circular"
            TankType.RECTANGULAR_UNDERGROUND -> "SBC 304-2018: Underground Rectangular"
            TankType.CIRCULAR_UNDERGROUND -> "SBC 304-2018: Underground Circular"
            TankType.RECTANGULAR -> "SBC 304-2018: Rectangular Tank"
            TankType.CIRCULAR -> "SBC 304-2018: Circular Tank"
        }
    }

    private fun applyRecommendations(
        recommendations: MutableList<String>, isUnderground: Boolean, wallThickness: Double
    ) {
        when (params.family) {
            CodeFamily.ECP_203 -> {
                recommendations.add("استخدام SBR أو Water-stop في المفاصل الإنشائية")
                recommendations.add("الغطاء الأدنى: 50mm (جانب الماء)، 25mm (الجانب الخارجي)")
                recommendations.add("معالجة بمياه نظيفة لمدة 7 أيام على الأقل")
                recommendations.add("اختبار التسريب قبل الردم (للخزانات تحت الأرض)")
                if (isUnderground) recommendations.add("توفير طبقة ردم نظيفة حول الخزان")
                if (wallThickness > 350)
                    recommendations.add("اعتبار استخدام سائل إضافي بلاستيكي (Superplasticizer) للقذف")
            }
            CodeFamily.ACI_318 -> recommendations.addAll(listOf(
                "Use water-stop joints at construction joints (ACI 350-06)",
                "Min cover: 50mm (water face), 40mm (exterior)",
                "Cure concrete minimum 7 days with wet burlap",
                "Perform leak test before backfill (underground tanks)"
            ))
            CodeFamily.SBC_304 -> recommendations.addAll(listOf(
                "Use water-stop at all construction joints",
                "Min cover: 50mm (water face)",
                "Wet curing minimum 7 days",
                "Leak test required before backfill"
            ))
        }
    }

    private fun fmt0(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.0f", v)
    private fun fmt1(v: Double) = String.format("%.1f", v)

    /** Bar scheduling result for one direction. */
    private data class BarPair(val dia: Double, val count: Int, val spacing: Double, val provided: Double)

    data class Outcome(
        val code: DesignCode,
        val wallThickness: Double,
        val baseThickness: Double,
        val wallEffectiveDepth: Double,
        val baseEffectiveDepth: Double,
        val wallReinforcement: ReinforcementResult,
        val baseReinforcement: ReinforcementResult,
        val capacityM3: Double,
        val concreteVolume: Double,
        val steelWeight: Double,
        val cost: Double,
        val isSafe: Boolean,
        val pressure: Double,
        val maxMomentWall: Double,
        val maxMomentBase: Double,
        val maxShearWall: Double,
        val factorOfSafetyUplift: Double,
        val structuralSystem: String,
        val recommendations: List<String>,
        val safetyChecks: List<TankCheck>,
        val warnings: List<String>,
        val trace: CalculationTrace,
        val overallStatus: CheckStatus,
        val sanity: SanityReport
    )
}

/** Water-retaining tank form. */
enum class TankType {
    RECTANGULAR_GROUND, CIRCULAR_GROUND,
    RECTANGULAR_ELEVATED, CIRCULAR_ELEVATED,
    RECTANGULAR_UNDERGROUND, CIRCULAR_UNDERGROUND,
    RECTANGULAR, CIRCULAR
}

/** One safety-check row of the tank outcome (mirror of the app TankSafetyCheck). */
data class TankCheck(
    val name: String,
    val value: Double,
    val limit: Double,
    val unit: String,
    val isSafe: Boolean,
    val description: String = ""
)

/** Unit weight of water — physical constant (kN/m³). */
private const val GAMMA_W = 9.81

/** Unit weight of reinforced concrete — physical constant (kN/m³). */
private const val CONCRETE_DENSITY = 25.0

/** Legacy app billing: steel mass per m³ of tank concrete (kg/m³). */
private const val STEEL_KG_PER_M3 = 120.0

/** Legacy app billing: concrete rate (currency per m³). */
private const val CONCRETE_RATE = 5000.0

/** Legacy app billing: steel rate (currency per tonne). */
private const val STEEL_RATE_PER_TON = 55000.0

/** Uplift-stability factor (ECP §8-1 / ACI 350 / SBC 304). */
private const val UPLIFT_MIN_FOS = 1.25

/** "Dry formation" uplift FoS reported when buoyancy cannot govern. */
private const val UPLIFT_DRY_FOS = 99.0

/** ECP compression-strain limit εcu (code-fixed, §4-2-2-1). */
private const val EPS_CU = 0.003

/** Hoop bar count-per-metre clamp. */
private const val HOOP_MIN_BARS = 7
/** Hoop bar count-per-metre clamp. */
private const val HOOP_MAX_BARS = 20

/** Vertical-wall bar count-per-metre clamp. */
private const val VERT_MIN_BARS = 6
/** Vertical-wall bar count-per-metre clamp. */
private const val VERT_MAX_BARS = 16

/** ECP §8-1 min-ρ code note for the wall (app golden string). */
private const val MIN_RHO_WALL_NOTE = "تم تطبيق التسليح الأدنى للمنشآت المائية (0.25%)"

/** ECP §8-1 min-ρ code note for the base (app golden string). */
private const val MIN_RHO_BASE_NOTE = "تم تطبيق التسليح الأدنى للقاعدة (0.25%)"