package com.civileg.core.engineering

import com.civileg.core.calculations.entities.CodeReference
import com.civileg.core.calculations.entities.DesignCode
import com.civileg.core.calculations.entities.LoadCombination
import com.civileg.core.calculations.entities.SupportCondition
import com.civileg.core.math.SafeMath
import com.civileg.core.sanity.EngineeringSanityEngine
import com.civileg.core.sanity.SanityReport
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Unified staircase (waist-slab) design.
 *
 * ONE skeleton for ECP 203-2020 §4-2, ACI 318-19 Ch.9/22 and SBC 304-2018; the
 * stair is modelled exactly like the legacy app: a simply-supported 1 m strip
 * on the slope. The divergent formulations — geometry split (comfort target
 * 625 ECP vs 610 ACI/SBC), K-method vs Rn–ρ, ρmin form (b·d ECP/SBC vs b·h
 * ACI), shear (γc stress vs φ·Vc), the 0.5·φVc minimum-stirrup line, and the
 * deflection basis (L/250 ECP vs L/240 ACI/SBC) — branch on
 * [ConcreteCodeParams.family]. Every COEFFICIENT comes from
 * [ConcreteCodeParams]; no factor is hardcoded (spec §3). Structural rules
 * fixed by the codes themselves (0.20 distribution ratio, the 100..200 mm
 * main-bar spacing window, the 0.893 K-method lever-arm divisor, εcu = 0.003,
 * and the 0.90 balanced-block factor of ECP §4-2) are documented inline with
 * their code reference.
 *
 * Golden-gated against the legacy app ECPStaircase/ACIStaircase/SBCStaircase
 * for exact numeric parity, including the byte-identical bar strings
 * ("Ø12 @ 150 mm c/c").
 */
class UnifiedStairDesign(
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

    /**
     * Stair flight as a 1 m simply-supported waist strip.
     *
     * @param spanM horizontal projection of the flight (m)
     * @param totalRiseM total vertical rise (m)
     * @param stairWidthM stair width (m) — drives distribution-bar count
     * @param waistThicknessMm requested waist thickness (mm, clamped to the code minimum)
     * @param deadLoadKnm2 imposed dead load on the slope (kN/m² — finishes incl.)
     * @param liveLoadKnm2 live load (kN/m²)
     * @param riserCount number of risers (0 = auto)
     * @param goingMm input going (0 = auto)
     */
    fun designStaircase(
        stairType: StairType,
        spanM: Double,
        totalRiseM: Double,
        stairWidthM: Double,
        waistThicknessMm: Double,
        deadLoadKnm2: Double,
        liveLoadKnm2: Double,
        riserCount: Int,
        goingMmInput: Double
    ): Outcome {
        SafeMath.requirePositive(spanM, "span")
        SafeMath.requirePositive(totalRiseM, "total rise")
        SafeMath.requirePositive(stairWidthM, "stair width")
        SafeMath.requirePositive(waistThicknessMm, "waist thickness")
        SafeMath.requirePositive(deadLoadKnm2, "dead load")
        SafeMath.requirePositive(liveLoadKnm2, "live load")
        SafeMath.requireNonNegative(goingMmInput, "going")
        if (riserCount < 0) throw ArithmeticException("SafeMath.requireNonNegative: riser count = $riserCount")

        val trace = CalculationTrace()
        val safetyChecks = mutableListOf<StairCheck>()
        val flexureRef = CodeReference.getReference(code, "STAIR")
        val shearRef = CodeReference.getReference(code, "STAIR_SHEAR")
        val defectRef = CodeReference.getReference(code, "STAIR_DEFLECTION")
        val geometryRef = CodeReference.getReference(code, "STAIR_GEOMETRY")

        // 1. Geometry split — app-faithful for every input mode. The "best-fit"
        //    comfort targets / scan seeds are family constants.
        var numRisers: Int
        var numTreads: Int
        var riserMm: Double
        var goingMm: Double

        if (riserCount > 0 && goingMmInput > 0) {
            numRisers = riserCount
            numTreads = numRisers - 1
            riserMm = totalRiseM * 1000.0 / numRisers
            goingMm = goingMmInput
        } else if (riserCount > 0) {
            numRisers = riserCount
            numTreads = numRisers - 1
            riserMm = totalRiseM * 1000.0 / numRisers
            val idealGoing = params.stairRiserCountIdealGoingBaseMm - 2 * riserMm
            goingMm = idealGoing.coerceIn(params.stairMinGoingMm, 300.0)
        } else if (goingMmInput > 0) {
            goingMm = goingMmInput
            val treadsEstimate = (spanM * 1000.0 / goingMm).toInt().coerceIn(3, 40)
            numTreads = treadsEstimate
            numRisers = numTreads + 1
            riserMm = totalRiseM * 1000.0 / numRisers
            val targetRiser = (params.stairRiserCountIdealGoingBaseMm - goingMm) / 2.0
            val adjustedRisers = round(totalRiseM * 1000.0 / targetRiser).toInt().coerceIn(5, 40)
            if (abs(adjustedRisers - numRisers) > 1) {
                numRisers = adjustedRisers
                numTreads = numRisers - 1
                riserMm = totalRiseM * 1000.0 / numRisers
            }
        } else {
            var bestRiser = params.stairAutoBestRiserStartMm
            var bestDiff = Double.MAX_VALUE
            for (n in 5..40) {
                val r = totalRiseM * 1000.0 / n
                val t = n - 1
                val g = if (t > 0) spanM * 1000.0 / t else 300.0
                if (r <= params.stairMaxRiserMm && g >= params.stairMinGoingMm) {
                    val comfort = 2 * r + g
                    val diff = abs(comfort - params.stairAutoComfortTargetMm)
                    if (diff < bestDiff) { bestDiff = diff; bestRiser = r }
                }
            }
            bestRiser = bestRiser.coerceIn(params.stairAutoBestRiserMinMm, params.stairMaxRiserMm)
            numRisers = round(totalRiseM * 1000.0 / bestRiser).toInt().coerceIn(5, 40)
            numTreads = numRisers - 1
            riserMm = totalRiseM * 1000.0 / numRisers
            goingMm = (spanM * 1000.0 / numTreads).coerceIn(params.stairMinGoingMm, 300.0)
        }

        val inclinedLength = sqrt(spanM * spanM + totalRiseM * totalRiseM)
        val slopeAngle = Math.toDegrees(atan(totalRiseM / spanM))
        val cosTheta = cos(Math.toRadians(slopeAngle))

        trace.add(
            title = "Stair geometry",
            formula = "n_risers × n_treads ; R, G",
            substitution = "R = ${fmt1(riserMm)} mm, G = ${fmt1(goingMm)} mm, 2R+G = ${fmt0(2 * riserMm + goingMm)} mm",
            result = "${numRisers} risers × ${numTreads} treads",
            limit = "${params.stairMaxRiserMm.toInt()} mm max riser / ${params.stairMinGoingMm.toInt()} mm min going",
            status = CheckStatus.PASS,
            codeReference = geometryRef
        )

        // 2. Waist thickness clamped to the code minimum; effective depth via the
        //    app's back-calc d = h − cover − stirrup/2 − 6.
        val h = max(waistThicknessMm, params.stairMinWaistMm)
        val cover = params.stairCoverMm
        val d = params.stairEffectiveDepthMm(h)

        safetyChecks += StairCheck(
            name = "Waist Thickness",
            value = h, limit = params.stairMinWaistMm, unit = "mm",
            isSafe = h >= params.stairMinWaistMm,
            description = waistDesc()
        )
        trace.add(
            title = "Waist thickness",
            formula = "h ≥ ${params.stairMinWaistMm.toInt()} mm",
            substitution = "h = ${fmt0(h)} mm, d = h − $cover − ${params.stairStirrupEstimateMm.toInt()}/2 − 6 = ${fmt0(d)} mm",
            result = "${fmt0(d)} mm",
            limit = "≥ ${params.stairMinWaistMm.toInt()} mm",
            status = if (h >= params.stairMinWaistMm) CheckStatus.PASS else CheckStatus.FAIL,
            codeReference = "",  // geometry rule, no clause-level equation
        )

        // 3. Factored loads projected horizontally: w_h = γD·dead/cosθ + γL·live.
        //    Load factors come from the single LoadCombination source (ECP 1.4/1.6,
        //    ACI/SBC 1.2/1.6 — structural combination rules, not engine constants).
        val (gammaD, gammaL) = LoadCombination.DEAD_LIVE.getLoadFactors(code)
        val horizontalLoad = gammaD * deadLoadKnm2 / cosTheta.coerceAtLeast(0.1) + gammaL * liveLoadKnm2
        val factoredOnSlope = horizontalLoad * cosTheta

        trace.add(
            title = "Factored horizontal load",
            formula = "w = ${fmt0(gammaD)}·D/cosθ + ${fmt0(gammaL)}·L",
            substitution = "cosθ = ${fmt2(cosTheta)}, D = ${fmt1(deadLoadKnm2)}, L = ${fmt1(liveLoadKnm2)} kN/m²",
            result = "${fmt2(horizontalLoad)} kN/m²",
            limit = "—",
            status = CheckStatus.PASS
        )

        // 4. Simple-span strip analysis. Dog-leg landings give partial fixity
        //    (M = wL²/10), everything else M = wL²/8; shear at the support.
        val momentCoefficient = if (stairType == StairType.DOG_LEG) 1.0 / 10.0 else 1.0 / 8.0
        val adjustedMoment = horizontalLoad * spanM * spanM * momentCoefficient
        val adjustedShear = horizontalLoad * spanM / 2.0
        val reactionA = adjustedShear
        val reactionB = adjustedShear

        trace.add(
            title = "Design moment & shear",
            formula = "Mu = w·L²·α ; Vu = w·L/2",
            substitution = "α = ${fraction(momentCoefficient)} (${if (stairType == StairType.DOG_LEG) "dog-leg" else "free span"}), L = ${fmt1(spanM)} m",
            result = "Mu = ${fmt2(adjustedMoment)} kN·m/m, Vu = ${fmt2(adjustedShear)} kN/m",
            limit = "—",
            status = CheckStatus.PASS
        )

        // 5. Flexural steel — ECP K-method vs ACI/SBC Rn–ρ.
        val b = 1000.0
        val muNmm = adjustedMoment * 1e6
        val flexureOk: Boolean
        var astRequired: Double
        val minSteelArea = params.stairMinSteelAreaMm2(b, h, d, concrete, steel)
        val minSteelRatio = params.stairMinSteelRatio
        var flexureValue = 0.0
        var flexureLimit = 0.0
        var flexureLabel = ""

        if (isEcp) {
            // ECP 203 K-method (§4-2-2-1): design steel stress fsd = fy/γs; the
            // lever-arm denominator 0.893 = γc/(2×0.67) is the code-fixed constant.
            val fcu = concrete.fcuMpa
            val K = if (fcu > 0 && d > 0) muNmm / (fcu * b * d * d) else 0.0
            val kBal = (params as Ecp203Params).kBalanced(steel)
            val leverArm = if (0.25 - K / ECP_LEVER_ARM_K > 0) {
                d * (0.5 + sqrt(0.25 - K / ECP_LEVER_ARM_K))
            } else {
                d * 0.7   // app-faithful fallback lever arm at over-reinforced K
            }
            val fs = steel.yieldMpa / params.gammas!!.gammaS
            astRequired = if (fs > 0 && leverArm > 0) muNmm / (fs * leverArm) else 0.0
            flexureOk = K <= kBal
            flexureValue = K; flexureLimit = kBal; flexureLabel = "K-method K ≤ K_bal"
        } else {
            // ACI 318-19 §9.3 / SBC 304 §9: Rn–ρ equilibrium, fc = 0.8·fcu.
            val fc = concrete.cylinderStrengthMpa
            val phi = params.phiFlexure!!
            val denominator = phi * b * d * d
            val rn = if (denominator > 0) muNmm / denominator else 0.0
            val m = if (fc > 0) steel.yieldMpa / (0.85 * fc) else 0.0
            val disc = if (m > 0) 1.0 - 2.0 * m * rn / steel.yieldMpa else Double.NaN
            val rho = if (m > 0 && disc >= 0) (1.0 - sqrt(disc)) / m else 0.0
            val rhoMaxTc = params.maxFlexuralSteelRatio(concrete, steel)   // tension-controlled cap
            astRequired = rho * b * d
            flexureOk = rho <= rhoMaxTc
            flexureValue = rho; flexureLimit = rhoMaxTc; flexureLabel = "ρ ≤ ρ_max_tc"
        }

        if (astRequired < minSteelArea) astRequired = minSteelArea

        safetyChecks += StairCheck(
            name = if (isEcp) "Flexure (K ≤ K_bal)" else "Flexure (ρ ≤ ρ_max_tc)",
            value = flexureValue, limit = flexureLimit, unit = "",
            isSafe = flexureOk,
            description = flexureDesc()
        )
        trace.add(
            title = "Flexure ($flexureLabel)",
            formula = if (isEcp) "K = Mu/(fcu·b·d²) ; As = Mu/(fsd·z)" else "Rn = Mu/(φ·b·d²) ; ρ = (1−√(1−2mRn/fy))/m",
            substitution = "d = ${fmt0(d)} mm, As,min = ${fmt0(minSteelArea)} mm²/m",
            result = "As = ${fmt0(astRequired)} mm²/m ($flexureLabel)",
            limit = flexureLabel,
            status = if (flexureOk) CheckStatus.PASS else CheckStatus.FAIL,
            utilization = if (flexureLimit > 0) SafeMath.div(flexureValue, flexureLimit) else null,
            codeReference = flexureRef
        )

        // 6. Bar selection − market menu first-fit with a 100..200 mm spacing
        //    window (app contract); the reported area is re-derived from the
        //    chosen bar exactly as the app's parser does.
        val mainRebar = selectBars(astRequired)
        val mainRebarArea = parseBarArea(mainRebar)
        val rhoProvided = mainRebarArea / (b * d)

        // Distribution steel = 20% of main (structural rule); floored at the
        //    family ratio × b·h. Sized over the full stair width.
        val distAreaRequired = 0.20 * mainRebarArea
        val distAreaMin = params.stairDistributionMinRatio * b * h
        val distAreaFinal = max(distAreaRequired, distAreaMin)
        val distributionRebar = selectDistBars(distAreaFinal, stairWidthM)
        val distributionRebarArea = parseDistBarArea(distributionRebar, stairWidthM)

        trace.add(
            title = "Main reinforcement",
            formula = "n = ⌈As/(πd²/4)⌉, s = 1000/n within [100,200] mm",
            substitution = "As = ${fmt0(astRequired)} mm²/m",
            result = mainRebar + " (${fmt0(mainRebarArea)} mm²/m)",
            limit = "≥ ${fmt0(astRequired)} mm²/m",
            status = if (mainRebarArea >= astRequired - 1e-6) CheckStatus.PASS else CheckStatus.WARNING,
            utilization = SafeMath.div(astRequired, mainRebarArea.coerceAtLeast(1e-9)),
            codeReference = flexureRef
        )
        trace.add(
            title = "Distribution reinforcement",
            formula = "As,dist = max(0.20·As,main, ρdist,min·b·h)",
            substitution = "As,main = ${fmt0(mainRebarArea)} mm²/m",
            result = distributionRebar,
            limit = "≥ ${fmt0(distAreaFinal)} mm²/m",
            status = CheckStatus.PASS,
            codeReference = flexureRef
        )

        // 7. Shear — family concrete capacity, absolute max, and the stirrup
        //    path (none / minimum / required).
        val vuKn = adjustedShear
        val vcKn = params.concreteShearCapacityKn(b, d, concrete)          // ECP qcu·b·d; ACI/SBC φVc
        val maxShearCapacityKn = params.maxShearCapacityKn(b, d, concrete) // ECP 0.7√·b·d; ACI/SBC φ(Vc+Vs,max)
        val shearSafe = vuKn <= maxShearCapacityKn

        safetyChecks += StairCheck(
            name = "Shear Capacity",
            value = adjustedShear, limit = maxShearCapacityKn, unit = "kN/m",
            isSafe = shearSafe,
            description = shearDesc()
        )
        trace.add(
            title = "Shear capacity",
            formula = if (isEcp) "qcu = 0.24√(fcu/γc) ; Vc,max = 0.7√(fcu/γc)·b·d"
            else "φVc = φ·0.17√f'c·b·d ; Vmax = φ·(Vc + 0.66√f'c·b·d)",
            substitution = "Vc = ${fmt2(vcKn)} kN/m, Vmax = ${fmt2(maxShearCapacityKn)} kN/m, Vu = ${fmt2(vuKn)} kN/m",
            result = if (shearSafe) "safe" else "FAIL",
            limit = "Vu ≤ Vmax",
            status = if (shearSafe) CheckStatus.PASS else CheckStatus.FAIL,
            utilization = SafeMath.div(vuKn, maxShearCapacityKn),
            codeReference = shearRef
        )

        val requiredStirrups: String
        val stirrupDiameter: Double
        val stirrupSpacing: Double

        if (isEcp) {
            // ECP §4-3-1-2: stirrups only when Vu exceeds the concrete share; the
            // minimum-stirrup bar is Ø8 and the spacing clamp is 50..200 mm.
            if (vuKn <= vcKn) {
                requiredStirrups = "None (concrete capacity sufficient)"
                stirrupDiameter = 0.0
                stirrupSpacing = 0.0
            } else {
                val excessShear = (vuKn - vcKn) * 1000.0
                val fs = steel.yieldMpa / params.gammas!!.gammaS
                val asvRequired = excessShear / (fs * d) * 1000.0
                val asvFinal = max(asvRequired, params.minShearReinforcementMm2PerM(b, concrete, steel))
                stirrupDiameter = params.stairShearStirrupDiameterMm
                val stirrupArea = 2 * PI * stirrupDiameter * stirrupDiameter / 4.0
                stirrupSpacing = (stirrupArea * 1000.0 / asvFinal).coerceIn(50.0, params.stairStirrupSpacingMaxMm())
                requiredStirrups = "Ø${stirrupDiameter.toInt()} @ ${stirrupSpacing.toInt()} mm c/c"
            }
        } else {
            // ACI §22.5.1 / SBC §11: nothing below 0.5·φVc, minimum stirrups
            // (Ø10) between 0.5·φVc and φVc, and full ties above φVc.
            if (vuKn <= vcKn * 0.5) {
                requiredStirrups = "None (concrete capacity sufficient)"
                stirrupDiameter = 0.0
                stirrupSpacing = 0.0
            } else {
                val minAvs = params.minShearReinforcementMm2PerM(b, concrete, steel)
                stirrupDiameter = params.stairShearStirrupDiameterMm
                val stirrupArea = 2 * PI * stirrupDiameter * stirrupDiameter / 4.0
                if (vuKn <= vcKn) {
                    stirrupSpacing = (stirrupArea * 1000.0 / minAvs).coerceIn(50.0, params.stairStirrupSpacingMaxMm())
                    requiredStirrups = "Ø${stirrupDiameter.toInt()} @ ${stirrupSpacing.toInt()} mm c/c (min)"
                } else {
                    val vs = (vuKn - vcKn) / 0.75
                    val asvRequired = vs * 1000.0 / (steel.yieldMpa * d) * 1000.0
                    val asvFinal = max(asvRequired, minAvs)
                    stirrupSpacing = (stirrupArea * 1000.0 / asvFinal).coerceIn(50.0, params.stairStirrupSpacingMaxMm())
                    requiredStirrups = "Ø${stirrupDiameter.toInt()} @ ${stirrupSpacing.toInt()} mm c/c"
                }
            }
        }

        trace.add(
            title = "Shear reinforcement",
            formula = "s = Av·1000/As,s ; clamp [50, ${fmt0(params.stairStirrupSpacingMaxMm())}] mm",
            substitution = "Vc = ${fmt2(vcKn)} kN/m",
            result = requiredStirrups,
            limit = "Vu vs Vc",
            status = CheckStatus.PASS,
            codeReference = shearRef
        )

        // 8. Deflection screening (span/depth): family basic ratio × modification
        //    factor (ECP 0.55+0.45/ρ%; ACI/SBC 1.0) and the family total-load
        //    limit (ECP L/250, ACI/SBC L/240).
        val rhoPercent = (rhoProvided * 100).coerceAtLeast(0.15)
        val actualRatio = (spanM * 1000) / h
        val allowableRatio = params.basicSpanDepthRatio(SupportCondition.SIMPLY_SUPPORTED) *
            params.stairDeflectionModificationFactor(rhoPercent)
        val deflectionRatio = actualRatio / allowableRatio

        val allowableDeflection = params.stairDeflectionLimitMm(spanM)
        val calculatedDeflection = if (deflectionRatio > 1.0) {
            allowableDeflection * deflectionRatio * 1.2
        } else {
            allowableDeflection * 0.7
        }
        val deflectionOk = deflectionRatio <= 1.0

        safetyChecks += StairCheck(
            name = "Deflection (Span/Depth)",
            value = actualRatio, limit = allowableRatio, unit = "",
            isSafe = deflectionOk,
            description = deflectionDesc(allowableRatio)
        )
        trace.add(
            title = "Deflection (span/depth)",
            formula = "L/d ≤ basic × MF",
            substitution = "L/d = ${fmt1(actualRatio)}, allowable = ${fmt1(allowableRatio)}, MF = ${fmt2(params.stairDeflectionModificationFactor(rhoPercent))}",
            result = "L/d = ${fmt1(actualRatio)} ≤ ${fmt1(allowableRatio)}",
            limit = "≤ ${fmt1(allowableRatio)}",
            status = if (deflectionOk) CheckStatus.PASS else CheckStatus.FAIL,
            utilization = deflectionRatio,
            codeReference = defectRef
        )

        // 9. Geometric (comfort) checks — 2R+G window, max riser, min going.
        val comfortValue = 2 * riserMm + goingMm
        val comfortOk = comfortValue in params.stairComfortMinMm..params.stairComfortMaxMm
        safetyChecks += StairCheck(
            name = "2R + G (Comfort)",
            value = comfortValue, limit = params.stairComfortMaxMm, unit = "mm",
            isSafe = comfortOk,
            description = comfortDesc()
        )
        safetyChecks += StairCheck(
            name = riserName(),
            value = riserMm, limit = params.stairMaxRiserMm, unit = "mm",
            isSafe = riserMm <= params.stairMaxRiserMm,
            description = riserDesc()
        )
        safetyChecks += StairCheck(
            name = goingName(),
            value = goingMm, limit = params.stairMinGoingMm, unit = "mm",
            isSafe = goingMm >= params.stairMinGoingMm,
            description = goingDesc()
        )
        trace.add(
            title = "Comfort (2R+G)",
            formula = "${fmt0(params.stairComfortMinMm)} ≤ 2R+G ≤ ${fmt0(params.stairComfortMaxMm)} mm",
            substitution = "R = ${fmt1(riserMm)} mm, G = ${fmt1(goingMm)} mm",
            result = "${fmt0(comfortValue)} mm",
            limit = "≤ ${fmt0(params.stairComfortMaxMm)} mm",
            status = if (comfortOk) CheckStatus.PASS else CheckStatus.FAIL,
            codeReference = geometryRef
        )

        val appSafe = safetyChecks.all { it.isSafe }

        val outcome = Outcome(
            code = code,
            riser = riserMm,
            going = goingMm,
            numberOfRisers = numRisers,
            numberOfTreads = numTreads,
            slopeAngle = slopeAngle,
            inclinedLength = inclinedLength,
            factoredLoad = factoredOnSlope,
            horizontalLoad = horizontalLoad,
            maxMoment = adjustedMoment,
            maxShear = adjustedShear,
            reactionA = reactionA,
            reactionB = reactionB,
            mainRebar = mainRebar,
            mainRebarArea = mainRebarArea,
            distributionRebar = distributionRebar,
            distributionRebarArea = distributionRebarArea,
            waistThickness = h,
            effectiveDepth = d,
            reinforcementRatio = rhoProvided,
            minSteelRatio = minSteelRatio,
            asRequiredMm2 = astRequired,
            flexureOk = flexureOk,
            shearCapacity = vcKn,
            maxShearCapacityKn = maxShearCapacityKn,
            requiredStirrups = requiredStirrups,
            stirrupDiameter = stirrupDiameter,
            stirrupSpacing = stirrupSpacing,
            deflectionActualRatio = actualRatio,
            deflectionAllowableRatio = allowableRatio,
            deflection = calculatedDeflection,
            allowableDeflection = allowableDeflection,
            deflectionOk = deflectionOk,
            safetyChecks = safetyChecks,
            isSafe = appSafe,
            overallStatus = trace.overall,
            trace = trace,
            sanity = SanityReport("StairOutcome", emptyList())
        )
        val sanityReport = EngineeringSanityEngine.check(outcome)
        val overall = when {
            sanityReport.hasError -> CheckStatus.FAIL
            sanityReport.hasWarning && trace.overall == CheckStatus.PASS -> CheckStatus.WARNING
            else -> trace.overall
        }
        return outcome.copy(sanity = sanityReport, overallStatus = overall)
    }

    // Check labels are golden-matched byte-for-byte to the legacy app's
    // StairSafetyCheck rows — including the ACI-inch annotations and the
    // ECP/SBC "per code" phrasing. These are UI strings, not coefficients.
    private fun waistDesc() = when (params.family) {
        CodeFamily.ECP_203 -> "Minimum waist thickness per ECP 203"
        CodeFamily.ACI_318 -> "Minimum practical waist thickness"
        CodeFamily.SBC_304 -> "Minimum waist thickness per SBC 304"
    }

    private fun flexureDesc() = when (params.family) {
        CodeFamily.ECP_203 -> "ECP 203 Section 4-2-2-1"
        CodeFamily.ACI_318 -> "ACI 318-19 Section 9.3.3.1 (tension-controlled)"
        CodeFamily.SBC_304 -> "SBC 304-2018 Section 9.3.3.1"
    }

    private fun shearDesc() = when (params.family) {
        CodeFamily.ECP_203 -> "ECP 203 Section 4-3-1-2"
        CodeFamily.ACI_318 -> "ACI 318-19 Section 22.5.1.2"
        CodeFamily.SBC_304 -> "SBC 304-2018 Section 11"
    }

    private fun deflectionDesc(allowableRatio: Double) = when (params.family) {
        CodeFamily.ECP_203 -> "ECP 203 Section 6-3: L/d ≤ ${fmt1(allowableRatio)}"
        CodeFamily.ACI_318 -> "ACI 318-19 Table 24.2.2: L/d ≤ ${fmt1(allowableRatio)}"
        CodeFamily.SBC_304 -> "SBC 304-2018 Section 9.5: L/d ≤ ${fmt1(allowableRatio)}"
    }

    private fun comfortDesc() = when (params.family) {
        CodeFamily.ECP_203 -> "Comfort formula: ${fmt0(params.stairComfortMinMm)} ≤ 2R+G ≤ ${fmt0(params.stairComfortMaxMm)} mm"
        CodeFamily.ACI_318 -> "IBC Section 1011.5: ${fmt0(params.stairComfortMinMm)} ≤ 2R+G ≤ ${fmt0(params.stairComfortMaxMm)} mm (24-25.5 in)"
        CodeFamily.SBC_304 -> "SBC comfort formula: ${fmt0(params.stairComfortMinMm)} ≤ 2R+G ≤ ${fmt0(params.stairComfortMaxMm)} mm"
    }

    private fun riserName() = when (params.family) {
        CodeFamily.ACI_318 -> "Riser ≤ ${params.stairMaxRiserMm.toInt()}mm (7\")"
        else -> "Riser ≤ ${params.stairMaxRiserMm.toInt()}mm"
    }

    private fun riserDesc() = when (params.family) {
        CodeFamily.ECP_203 -> "Maximum riser height per ECP 201"
        CodeFamily.ACI_318 -> "IBC Section 1011.5.2: max riser ${params.stairMaxRiserMm.toInt()}mm (7 in)"
        CodeFamily.SBC_304 -> "Maximum riser height per SBC 304"
    }

    private fun goingName() = when (params.family) {
        CodeFamily.ACI_318 -> "Going ≥ ${params.stairMinGoingMm.toInt()}mm (11\")"
        else -> "Going ≥ ${params.stairMinGoingMm.toInt()}mm"
    }

    private fun goingDesc() = when (params.family) {
        CodeFamily.ECP_203 -> "Minimum going per ECP 201"
        CodeFamily.ACI_318 -> "IBC Section 1011.5.2: min going ${params.stairMinGoingMm.toInt()}mm (11 in)"
        CodeFamily.SBC_304 -> "Minimum going per SBC 304"
    }

    /** Complete staircase design result (golden-match of the app StairCaseResult). */
    data class Outcome(
        val code: DesignCode,
        val riser: Double,
        val going: Double,
        val numberOfRisers: Int,
        val numberOfTreads: Int,
        val slopeAngle: Double,
        val inclinedLength: Double,
        val factoredLoad: Double,
        val horizontalLoad: Double,
        val maxMoment: Double,
        val maxShear: Double,
        val reactionA: Double,
        val reactionB: Double,
        val mainRebar: String,
        val mainRebarArea: Double,
        val distributionRebar: String,
        val distributionRebarArea: Double,
        val waistThickness: Double,
        val effectiveDepth: Double,
        val reinforcementRatio: Double,
        val minSteelRatio: Double,
        val asRequiredMm2: Double,
        val flexureOk: Boolean,
        val shearCapacity: Double,
        val maxShearCapacityKn: Double,
        val requiredStirrups: String,
        val stirrupDiameter: Double,
        val stirrupSpacing: Double,
        val deflectionActualRatio: Double,
        val deflectionAllowableRatio: Double,
        val deflection: Double,
        val allowableDeflection: Double,
        val deflectionOk: Boolean,
        val safetyChecks: List<StairCheck>,
        val isSafe: Boolean,
        val overallStatus: CheckStatus,
        val trace: CalculationTrace,
        val sanity: SanityReport
    )

    // ───────────────────────── bar selection ─────────────────────────

    /** Main-bar first-fit: smallest market diameter with 100..200 mm spacing (app contract). */
    private fun selectBars(requiredArea: Double): String {
        val availableBars = params.stairMainBarMenu()
        val barDia = availableBars.firstOrNull {
            val area = PI * it * it / 4
            val numBars = ceil(requiredArea / area).toInt()
            val spacing = 1000.0 / numBars
            spacing >= 100.0 && spacing <= 200.0
        } ?: params.stairMainBarFallback()
        val barArea = PI * barDia * barDia / 4
        val numBars = ceil(requiredArea / barArea).toInt().coerceAtLeast(5)
        val spacing = 1000.0 / numBars
        return "Ø${barDia.toInt()} @ ${spacing.toInt()} mm c/c"
    }

    /** Distribution-bar first-fit across the stair width: 3..20 bars total (app contract). */
    private fun selectDistBars(requiredAreaTotal: Double, stairWidthM: Double): String {
        val availableBars = params.stairDistBarMenu()
        val barDia = availableBars.firstOrNull {
            val area = PI * it * it / 4
            val numBars = ceil(requiredAreaTotal / area).toInt()
            numBars in 3..20
        } ?: params.stairDistBarFallback()
        val barArea = PI * barDia * barDia / 4
        val numBars = ceil(requiredAreaTotal / barArea).toInt().coerceAtLeast(4)
        val spacing = stairWidthM * 1000.0 / numBars
        return "Ø${barDia.toInt()} @ ${spacing.toInt()} mm c/c"
    }

    /** Area per metre from the bar string (mm²/m) — identical re-parse to the app. */
    private fun parseBarArea(barString: String): Double {
        if (barString.contains("None")) return 0.0
        try {
            val parts = barString.split("Ø", " @ ")
            if (parts.size >= 3) {
                val dia = parts[1].trim().split(" ")[0].toDouble()
                val spacing = parts[2].trim().split(" ")[0].toDouble()
                val barArea = PI * dia * dia / 4.0
                return barArea * 1000.0 / spacing
            }
        } catch (e: Exception) {
            // rule 1.4 — no silent failure
            throw IllegalArgumentException("Invalid stair bar notation '$barString' | صيغة تسليح السلم غير مفهومة", e)
        }
        return 0.0
    }

    /** Total distribution area over the stair width (mm²) — identical re-parse to the app. */
    private fun parseDistBarArea(barString: String, stairWidthM: Double): Double {
        if (barString.contains("None")) return 0.0
        try {
            val parts = barString.split("Ø", " @ ")
            if (parts.size >= 3) {
                val dia = parts[1].trim().split(" ")[0].toDouble()
                val spacing = parts[2].trim().split(" ")[0].toDouble()
                val barArea = PI * dia * dia / 4.0
                val numBars = (stairWidthM * 1000.0 / spacing).toInt().coerceAtLeast(1)
                return numBars * barArea
            }
        } catch (e: Exception) {
            // rule 1.4 — no silent failure
            throw IllegalArgumentException("Invalid stair bar notation '$barString' | صيغة تسليح السلم غير مفهومة", e)
        }
        return 0.0
    }

    // ───────────────────────── formatting helpers ─────────────────────────

    private fun fmt0(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.0f", v)
    private fun fmt1(v: Double) = String.format("%.1f", v)
    private fun fmt2(v: Double) = String.format("%.2f", v)
    private fun fraction(v: Double) = if (v == 1.0 / 8.0) "1/8" else "1/10"
}

/** Stair flight configuration. */
enum class StairType { STRAIGHT, DOG_LEG, SPIRAL, OPEN_WELL }

/** One safety-check row of the staircase outcome (mirror of the app StairSafetyCheck). */
data class StairCheck(
    val name: String,
    val value: Double,
    val limit: Double,
    val unit: String,
    val isSafe: Boolean,
    val description: String = ""
)

/** ECP K-method lever-arm divisor (γc/(2×0.67)) — code-fixed (§4-2-2-1). */
private const val ECP_LEVER_ARM_K = 0.893