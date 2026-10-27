package com.civileg.app.domain.calculations.base.posttensioned

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.entities.DesignCode
import kotlin.math.*

/**
 * معاملات الكود لتصميم الخرسانة مسبقة الإجهاد.
 * تجمع الفروق بين ECP 203 و ACI 318 و SBC 304 في مكان واحد.
 */
data class PTCodeParams(
    val code: DesignCode,
    val gammaC: Double,
    val transferCompRatio: Double,
    val transferTensionFactor: Double,
    val serviceCompRatio: Double,
    val serviceTensionFactor: Double,
    val jackingFpkRatio: Double,
    val jackingFp01kRatio: Double,
    val relaxationPercent: Double,
    val shrinkageStrain: Double,
    val creepCoefficient: Double,
    val flexurePhi: Double,
    val shearPhi: Double,
    val deadFactor: Double,
    val liveFactor: Double,
    val cylinderFactor: Double,
    val ecFactor: Double,
    val shearStrengthDivisor: Double,
    val shearConcreteCoeff: Double,
    val minStirrupRatio: Double,
    val strengthAtTransferRatio: Double,
    val flexureBlockCoeff: Double = 0.85
) {
    fun cylinderStrength(fcu: Double): Double = cylinderFactor * fcu
    fun strengthAtTransfer(fcu: Double): Double = strengthAtTransferRatio * fcu

    /**
     * مرجع مقاومة الخرسانة المستخدم في فحوص الخدمة.
     * ECP يستخدم fcu مباشرة، أما ACI/SBC فيستخدمان f'c الأسطواني.
     */
    fun serviceStrength(fcu: Double): Double =
        if (code == DesignCode.ECP) fcu else cylinderStrength(fcu)

    /**
     * معامل المرونة: ECP 203 يعتمد fcu، وACI 318 يعتمد f'c الأسطواني.
     */
    fun ec(fcu: Double): Double =
        ecFactor * sqrt(if (code == DesignCode.ECP) fcu else cylinderStrength(fcu))

    /** إجهاد كتلة الضغط المستخدم في مقاومة العزم القصوى. */
    fun flexureBlockStress(fcu: Double): Double =
        flexureBlockCoeff * (if (code == DesignCode.ECP) fcu else cylinderStrength(fcu))
}

/**
 * محرك تصميم مشترك للعناصر مسبقة الإجهاد.
 * يُستخدم من ECPPostTensioned / ACIPostTensioned / SBCPostTensioned عبر تمرير المعاملات.
 */
class PTDesignEngine(private val params: PTCodeParams) {

    companion object {
        private const val E_P = 195000.0     // MPa — modulus of prestressing steel
        private const val DENSITY_STEEL = 7850.0 // kg/m³
        private const val DUCT_DIAMETER = 70.0   // mm (assumed for a 12.7mm strand group)
    }

    // ---------------------------------------------------------------------
    // Section properties
    // ---------------------------------------------------------------------

    data class SectionProps(
        val area: Double,       // mm²
        val ixx: Double,        // mm⁴
        val yTop: Double,       // mm from centroid to top fibre
        val yBottom: Double,    // mm from centroid to bottom fibre
        val zTop: Double,       // mm³
        val zBottom: Double,    // mm³
        val centroidFromBottom: Double
    )

    fun sectionProperties(input: PostTensionedInput): SectionProps {
        val b = input.width
        val h = input.depth
        val bw = b
        val bf = if (input.flangeWidth > 0.0) max(input.flangeWidth, b) else 0.0
        val hf = if (bf > 0.0) input.flangeThickness.coerceIn(0.0, h) else 0.0

        val webArea = bw * h
        val webCentroid = h / 2.0
        val flangeExtraArea = if (bf > 0.0) (bf - bw) * hf else 0.0
        val flangeCentroid = if (bf > 0.0) h - hf / 2.0 else 0.0

        val area = webArea + flangeExtraArea
        val centroid = if (area > 0.0) (webArea * webCentroid + flangeExtraArea * flangeCentroid) / area else h / 2.0

        val iWeb = bw * h * h * h / 12.0 + webArea * (webCentroid - centroid).pow(2)
        val iFlange = if (flangeExtraArea > 0.0) {
            (bf - bw) * hf * hf * hf / 12.0 + flangeExtraArea * (flangeCentroid - centroid).pow(2)
        } else 0.0

        val ixx = iWeb + iFlange
        val yTop = h - centroid
        val yBottom = centroid

        return SectionProps(
            area = area,
            ixx = ixx,
            yTop = yTop,
            yBottom = yBottom,
            zTop = if (yTop > 0) ixx / yTop else 0.0,
            zBottom = if (yBottom > 0) ixx / yBottom else 0.0,
            centroidFromBottom = centroid
        )
    }

    // ---------------------------------------------------------------------
    // Loads and moments
    // ---------------------------------------------------------------------

    fun serviceMoments(input: PostTensionedInput): Triple<Double, Double, Double> {
        val l = input.span
        val coeff = input.supportType.momentCoeff
        val mDead = input.totalDeadLoad() * l * l * coeff
        val mLive = input.liveLoad * l * l * coeff
        return Triple(mDead, mLive, mDead + mLive)
    }

    fun serviceShear(input: PostTensionedInput): Double {
        return (input.totalDeadLoad() + input.liveLoad) * input.span * input.supportType.shearCoeff
    }

    // ---------------------------------------------------------------------
    // Jacking stress and losses
    // ---------------------------------------------------------------------

    fun jackingStress(input: PostTensionedInput): Double {
        val fromFpk = params.jackingFpkRatio * input.fpk
        val fromFp01k = params.jackingFp01kRatio * input.fp01k
        return min(fromFpk, fromFp01k)
    }

    private fun relaxationPercent(input: PostTensionedInput): Double {
        return if (input.relaxationClass == 2) params.relaxationPercent else params.relaxationPercent * 3.2
    }

    fun calculateLosses(input: PostTensionedInput): PTLosses {
        input.validate()
        val props = sectionProperties(input)
        // Evaluate losses at the eccentricity actually adopted by the design so
        // that losses, stresses and deflection all refer to the same tendon profile.
        val e = providedEccentricity(input, props)
        return lossesForEccentricity(input, props, e)
    }

    // ---------------------------------------------------------------------
    // Eccentricity solving
    // ---------------------------------------------------------------------

    private fun maxEccentricity(input: PostTensionedInput, props: SectionProps): Double {
        return max(0.0, props.yBottom - input.cover - DUCT_DIAMETER / 2.0)
    }

    /**
     * يبحث عن أكبر انحراف (eccentricity) يُحقّق كل حدود إجهادات النقل والخدمة
     * مع أقل قوة إجهاد مسبق مطلوبة. البحث شبكي (200 خطوة) لتفادي أخطاء إشارات
     * الحل الجبري المباشر.
     */
    fun providedEccentricity(input: PostTensionedInput, props: SectionProps = sectionProperties(input)): Double {
        val eMax = maxEccentricity(input, props)
        if (eMax <= 0.0 || props.zTop <= 0.0 || props.zBottom <= 0.0) return 0.0

        val fpj = jackingStress(input)

        val steps = 200
        var bestE = 0.0
        var bestMargin = -Double.MAX_VALUE
        for (i in steps downTo 0) {
            val e = eMax * i / steps
            // Losses depend on the drape (through fcgp), so re-evaluate them per trial.
            val fpe = lossesForEccentricity(input, props, e).effectiveStress
            if (fpe <= 0.0) continue
            val feasibility = evaluateEccentricity(input, props, e, fpe, fpj)
            if (feasibility.feasible) return e
            if (feasibility.margin > bestMargin) {
                bestMargin = feasibility.margin
                bestE = e
            }
        }
        return bestE
    }

    private data class EccentricityFeasibility(
        val feasible: Boolean,
        val requiredPe: Double,
        val margin: Double
    )

    private fun evaluateEccentricity(
        input: PostTensionedInput,
        props: SectionProps,
        e: Double,
        fpe: Double,
        fpj: Double
    ): EccentricityFeasibility {
        val (_, _, mService) = serviceMoments(input)
        // At transfer only the member self-weight acts (superimposed dead load
        // and live load are applied later).
        val mSelf = input.selfWeight() * input.span * input.span * input.supportType.momentCoeff
        val fci = params.strengthAtTransfer(input.fcu)
        val ftTransfer = params.transferTensionFactor * sqrt(fci)
        val fService = params.serviceStrength(input.fcu)
        val ftService = params.serviceTensionFactor * sqrt(fService)
        val fcTransfer = params.transferCompRatio * fci
        val fcService = params.serviceCompRatio * fService

        val aTop = 1.0 / props.area - e / props.zTop
        val aBot = 1.0 / props.area + e / props.zBottom
        if (aBot <= 0.0) return EccentricityFeasibility(false, 0.0, -Double.MAX_VALUE)

        val mS = mService * 1e6
        val mSelfN = mSelf * 1e6

        var lower = 0.0
        var upper = Double.MAX_VALUE

        // Service bottom tension
        lower = max(lower, (mS / props.zBottom - ftService) / aBot)
        // Service bottom compression
        upper = min(upper, (fcService + mS / props.zBottom) / aBot)

        // Service top compression
        val topCompRhs = fcService - mS / props.zTop
        if (aTop > 0.0) {
            upper = min(upper, topCompRhs / aTop)
        } else if (topCompRhs < 0.0) {
            lower = max(lower, topCompRhs / aTop)
        }

        // Service top tension
        val topTensRhs = -ftService - mS / props.zTop
        if (aTop > 0.0) {
            lower = max(lower, topTensRhs / aTop)
        } else {
            upper = min(upper, topTensRhs / aTop)
        }

        val serviceFeasible = upper >= lower
        val pe = max(lower, 1.0).coerceAtMost(if (upper.isFinite()) upper else max(lower, 1.0))
        val pi = if (fpe > 0.0) pe * fpj / fpe else pe

        val tTop = pi * aTop + mSelfN / props.zTop
        val tBot = pi * aBot - mSelfN / props.zBottom
        val transferOk = tTop <= fcTransfer && tTop >= -ftTransfer &&
            tBot <= fcTransfer && tBot >= -ftTransfer

        val transferMargin = minOf(
            fcTransfer - tTop,
            tTop + ftTransfer,
            fcTransfer - tBot,
            tBot + ftTransfer
        )
        val margin = if (serviceFeasible) transferMargin else min(upper - lower, transferMargin)
        return EccentricityFeasibility(serviceFeasible && transferOk, pe, margin)
    }

    private fun requiredEffectiveForceAtEccentricity(
        input: PostTensionedInput,
        props: SectionProps,
        e: Double
    ): Double {
        val (_, _, mService) = serviceMoments(input)
        val ftService = params.serviceTensionFactor * sqrt(params.serviceStrength(input.fcu))
        val aTop = 1.0 / props.area - e / props.zTop
        val aBot = 1.0 / props.area + e / props.zBottom
        if (aBot <= 0.0) return 0.0
        val mS = mService * 1e6
        var lower = max(0.0, (mS / props.zBottom - ftService) / aBot)
        if (aTop > 0.0) {
            lower = max(lower, (-ftService - mS / props.zTop) / aTop)
        }
        return lower
    }

    private fun lossesForEccentricity(
        input: PostTensionedInput,
        props: SectionProps,
        e: Double
    ): PTLosses {
        val aps = input.tendonTotalArea()
        val fpj = jackingStress(input)
        val lMm = input.span * 1000.0
        // Elastic shortening is evaluated at transfer, where only the member
        // self-weight acts; superimposed dead and live loads are applied later.
        val mSelf = input.selfWeight() * input.span * input.span * input.supportType.momentCoeff

        val alpha = if (lMm > 0) 4.0 * e / lMm else 0.0
        val x = lMm / 2.0 / 1000.0
        val frictionRatio = 1.0 - exp(-(input.frictionCoefficient * alpha + input.wobbleCoefficient * x))
        val frictionLoss = fpj * frictionRatio.coerceIn(0.0, 1.0)
        val anchorageLoss = if (lMm > 0) E_P * input.anchorageSlip / lMm else 0.0
        val immediateLoss = frictionLoss + anchorageLoss
        val fpi = max(0.0, fpj - immediateLoss)
        val pi = fpi * aps

        val fcgp = if (props.area > 0 && props.ixx > 0) {
            pi / props.area + pi * e * e / props.ixx - mSelf * 1e6 * e / props.ixx
        } else 0.0
        val n = E_P / params.ec(input.fcu)
        val elastic = n * fcgp
        val creep = params.creepCoefficient * elastic
        val shrinkage = params.shrinkageStrain * E_P
        val relaxation = (relaxationPercent(input) / 100.0) * max(0.0, fpi - elastic)
        val total = immediateLoss + elastic + creep + shrinkage + relaxation
        val fpe = max(0.0, fpj - total)
        val totalPercent = if (fpj > 0) total / fpj * 100.0 else 0.0

        return PTLosses(
            elasticShortening = elastic,
            creep = creep,
            shrinkage = shrinkage,
            steelRelaxation = relaxation,
            friction = frictionLoss,
            anchorageSlip = anchorageLoss,
            total = total,
            totalPercent = totalPercent,
            effectiveStress = fpe,
            effectiveForce = fpe * aps / 1000.0
        )
    }

    // ---------------------------------------------------------------------
    // Required prestress
    // ---------------------------------------------------------------------

    fun calculateRequiredPrestress(input: PostTensionedInput): PrestressDesign {
        input.validate()
        val props = sectionProperties(input)
        val losses = calculateLosses(input)
        val e = providedEccentricity(input, props)

        val fpe = losses.effectiveStress
        val fpj = jackingStress(input)

        val requiredPe = requiredEffectiveForceAtEccentricity(input, props, e)

        val requiredAps = if (fpe > 0) requiredPe / fpe else 0.0
        val numTendons = max(input.numTendons, ceil(requiredAps / input.tendonArea).toInt().coerceAtLeast(1))
        val providedAps = numTendons * input.tendonArea
        val providedPe = fpe * providedAps / 1000.0
        val jackingForce = fpj * providedAps / 1000.0
        val notes = mutableListOf<String>()

        notes.add("Required Aps = ${"%.1f".format(requiredAps)} mm²")
        notes.add("Provided ${numTendons} × ${input.tendonType.description} = ${"%.1f".format(providedAps)} mm²")
        notes.add("Effective prestress = ${"%.1f".format(fpe)} MPa")
        notes.add("Eccentricity = ${"%.1f".format(e)} mm")

        return PrestressDesign(
            requiredForce = requiredPe / 1000.0,
            jackingForce = jackingForce,
            initialStress = fpj,
            effectiveStress = fpe,
            requiredEccentricity = e,
            providedEccentricity = e,
            numTendons = numTendons,
            tendonProfile = TendonProfile.Parabolic,
            isAdequate = providedPe >= requiredPe / 1000.0 && requiredAps <= providedAps,
            notes = notes
        )
    }

    // ---------------------------------------------------------------------
    // Stress checks
    // ---------------------------------------------------------------------

    fun checkStresses(input: PostTensionedInput, losses: PTLosses): StressCheckResult {
        input.validate()
        val props = sectionProperties(input)
        val e = providedEccentricity(input, props)
        val (_, _, mService) = serviceMoments(input)
        val mSelf = input.selfWeight() * input.span * input.span * input.supportType.momentCoeff
        val aps = input.tendonTotalArea()

        val fpj = jackingStress(input)
        val pi = max(0.0, fpj - losses.friction - losses.anchorageSlip) * aps
        val pe = losses.effectiveStress * aps

        fun topStress(p: Double, m: Double): Double = p / props.area - p * e / props.zTop + m * 1e6 / props.zTop
        fun bottomStress(p: Double, m: Double): Double = p / props.area + p * e / props.zBottom - m * 1e6 / props.zBottom

        val tTopT = topStress(pi, mSelf)
        val tBotT = bottomStress(pi, mSelf)
        val tTopS = topStress(pe, mService)
        val tBotS = bottomStress(pe, mService)

        val fci = params.strengthAtTransfer(input.fcu)
        val fService = params.serviceStrength(input.fcu)
        val allowTransferComp = params.transferCompRatio * fci
        val allowTransferTens = params.transferTensionFactor * sqrt(fci)
        val allowServiceComp = params.serviceCompRatio * fService
        val allowServiceTens = params.serviceTensionFactor * sqrt(fService)

        val transferSafe = tTopT <= allowTransferComp && tBotT <= allowTransferComp &&
            tTopT >= -allowTransferTens && tBotT >= -allowTransferTens
        val serviceSafe = tTopS <= allowServiceComp && tBotS <= allowServiceComp &&
            tTopS >= -allowServiceTens && tBotS >= -allowServiceTens

        val notes = mutableListOf<String>()
        notes.add("Allowable transfer compression = ${"%.2f".format(allowTransferComp)} MPa")
        notes.add("Allowable transfer tension = ${"%.2f".format(allowTransferTens)} MPa")
        notes.add("Allowable service compression = ${"%.2f".format(allowServiceComp)} MPa")
        notes.add("Allowable service tension = ${"%.2f".format(allowServiceTens)} MPa")
        if (!transferSafe) notes.add("Transfer stresses exceed limits — increase fci or reduce eccentricity")
        if (!serviceSafe) notes.add("Service stresses exceed limits — increase prestress or section")

        return StressCheckResult(
            transferTopCompression = max(0.0, tTopT),
            transferBottomCompression = max(0.0, tBotT),
            transferTopTension = max(0.0, -tTopT),
            transferBottomTension = max(0.0, -tBotT),
            serviceTopCompression = max(0.0, tTopS),
            serviceBottomCompression = max(0.0, tBotS),
            serviceTopTension = max(0.0, -tTopS),
            serviceBottomTension = max(0.0, -tBotS),
            allowableTransferComp = allowTransferComp,
            allowableTransferTens = allowTransferTens,
            allowableServiceComp = allowServiceComp,
            allowableServiceTens = allowServiceTens,
            isTransferSafe = transferSafe,
            isServiceSafe = serviceSafe,
            criticalLocation = if (!serviceSafe) "Midspan" else "Midspan",
            notes = notes
        )
    }

    // ---------------------------------------------------------------------
    // Ultimate strength
    // ---------------------------------------------------------------------

    fun checkUltimateStrength(input: PostTensionedInput, losses: PTLosses): UltimateStrengthResult {
        input.validate()
        val props = sectionProperties(input)
        val e = providedEccentricity(input, props)
        val b = input.width
        // Actual tendon depth: distance from the compression face to the tendon.
        val dp = (props.yTop + e).coerceIn(0.0, input.depth)
        val aps = input.tendonTotalArea()
        val fcPrime = params.cylinderStrength(input.fcu)
        val blockStress = params.flexureBlockStress(input.fcu)
        val bf = if (input.flangeWidth > 0.0) max(input.flangeWidth, b) else 0.0
        val hf = if (bf > 0.0) input.flangeThickness.coerceIn(0.0, input.depth) else 0.0
        val bComp = if (bf > 0.0) bf else b

        val (mDead, mLive, _) = serviceMoments(input)
        val muReq = params.deadFactor * mDead + params.liveFactor * mLive
        val vuReq = (params.deadFactor * input.totalDeadLoad() + params.liveFactor * input.liveLoad) *
            input.span * input.supportType.shearCoeff
        // Ultimate flexure per ACI 318 / ECP 203 (bonded tendon)
        val rhoP = if (bComp * dp > 0) aps / (bComp * dp) else 0.0
        val gammaP = if (input.relaxationClass == 2) 0.28 else 0.40
        // β1 is based on fcu for ECP and on the cylinder strength for ACI/SBC.
        val fcRef = if (params.code == DesignCode.ECP) input.fcu else fcPrime
        val beta1Base = if (params.code == DesignCode.ECP) 30.0 else 28.0
        val beta1 = if (fcRef <= beta1Base) {
            0.85
        } else {
            (0.85 - 0.05 * (fcRef - beta1Base) / 7.0).coerceIn(0.65, 0.85)
        }
        val fpsRaw = input.fpk * (1.0 - (gammaP / beta1) * (rhoP * input.fpk / max(fcRef, 1.0)))
        val fps = fpsRaw.coerceIn(0.5 * input.fpk, input.fp01k)
        var a = if (blockStress * bComp > 0) aps * fps / (blockStress * bComp) else 0.0
        val mn = if (bf > 0.0 && a > hf) {
            // T-section: compression in the flange overhang plus the web.
            val cFlange = blockStress * (bf - b) * hf
            val cWeb = aps * fps - cFlange
            a = if (blockStress * b > 0) cWeb / (blockStress * b) else 0.0
            (cFlange * (dp - hf / 2.0) + blockStress * b * a * (dp - a / 2.0)) / 1e6
        } else {
            aps * fps * (dp - a / 2.0) / 1e6
        }
        // ACI/SBC use the tension-controlled transition; ECP keeps its code φ.
        val neutralAxis = if (beta1 > 0.0) a / beta1 else 0.0
        val epsT = if (neutralAxis > 0.0) 0.003 * (dp - neutralAxis) / neutralAxis else 0.0
        val phiFlexure = if (params.code == DesignCode.ECP) {
            params.flexurePhi
        } else {
            when {
                epsT >= 0.005 -> params.flexurePhi
                epsT <= 0.002 -> 0.65
                else -> 0.65 + 0.25 * (epsT - 0.002) / 0.003
            }
        }
        val muCap = phiFlexure * mn

        // Shear
        val shearStrength = input.fcu / params.shearStrengthDivisor
        val vcN = params.shearConcreteCoeff * sqrt(max(shearStrength, 1.0)) * b * dp
        val vpN = if (input.span > 0) peForce(input, losses) * (4.0 * e / (input.span * 1000.0)) else 0.0
        val vc = (vcN + vpN) / 1000.0
        val vsReq = max(0.0, vuReq / params.shearPhi - vc)

        val stirrupDia = if (b < 250.0) 8.0 else 10.0
        val legs = 2
        val av = legs * PI * stirrupDia * stirrupDia / 4.0
        val minAvPerS = maxOf(
            params.minStirrupRatio * b,
            0.062 * sqrt(fcPrime) * b / input.fy,
            0.35 * b / input.fy
        )
        val avPerS = if (input.fy * dp > 0) vsReq * 1000.0 / (input.fy * dp) else minAvPerS
        val avPerSFinal = max(avPerS, minAvPerS)
        var spacing = if (avPerSFinal > 0) av / avPerSFinal else dp / 2.0
        val maxSpacing = min(dp / 2.0, 300.0)
        spacing = spacing.coerceIn(min(50.0, maxSpacing), maxSpacing)
        val providedAvPerS = av / spacing
        val stirrup = StirrupDesign(
            diameter = stirrupDia,
            spacing = spacing,
            legs = legs,
            requiredArea = avPerSFinal * 1000.0,
            providedArea = providedAvPerS * 1000.0,
            isAdequate = providedAvPerS >= avPerSFinal
        )
        val vsCap = params.shearPhi * (vc + input.fy * av / spacing * dp / 1000.0)
        val shearCap = vsCap

        val flexUtil = if (muCap > 0) muReq / muCap else 2.0
        val shearUtil = if (shearCap > 0) vuReq / shearCap else 2.0

        val notes = mutableListOf<String>()
        notes.add("fps = ${"%.1f".format(fps)} MPa")
        notes.add("a = ${"%.1f".format(a)} mm, dp = ${"%.1f".format(dp)} mm")
        notes.add("εt = ${"%.4f".format(epsT)}, φ = ${"%.2f".format(phiFlexure)}")
        notes.add("φMn = ${"%.1f".format(muCap)} kN.m vs Mu = ${"%.1f".format(muReq)} kN.m")
        notes.add("Vc = ${"%.1f".format(vc)} kN, Vs req = ${"%.1f".format(vsReq)} kN")
        if (params.code != DesignCode.ECP && epsT < 0.005) {
            notes.add("Section is not tension-controlled — increase depth or reduce reinforcement")
        }
        if (flexUtil > 1.0) notes.add("Flexural capacity insufficient — increase Aps or depth")
        if (shearUtil > 1.0) notes.add("Shear capacity insufficient — increase section or stirrups")

        return UltimateStrengthResult(
            flexuralCapacity = muCap,
            requiredMoment = muReq,
            shearCapacity = shearCap,
            requiredShear = vuReq,
            flexuralUtilization = flexUtil,
            shearUtilization = shearUtil,
            isFlexureSafe = flexUtil <= 1.0,
            isShearSafe = shearUtil <= 1.0,
            requiredStirrups = stirrup,
            requiredLongitudinal = aps,
            notes = notes
        )
    }

    private fun peForce(input: PostTensionedInput, losses: PTLosses): Double =
        losses.effectiveStress * input.tendonTotalArea() // N

    // ---------------------------------------------------------------------
    // Anchorage zone
    // ---------------------------------------------------------------------

    fun designAnchorageZone(input: PostTensionedInput, jackingForce: Double): AnchorageZoneResult {
        val pjN = max(0.0, jackingForce) * 1000.0
        val h = input.depth
        val plateWidth = min(200.0, 0.5 * h)
        val burstFactor = 0.25 * (1.0 - plateWidth / max(h, 1.0))
        val bursting = burstFactor * pjN / 1000.0 // kN
        val spalling = 0.02 * pjN / 1000.0        // kN

        val fs = 0.87 * input.fy
        val asBurst = if (fs > 0) bursting * 1000.0 / fs else 0.0
        val asSpall = if (fs > 0) spalling * 1000.0 / fs else 0.0

        val dia = if (input.width < 300.0) 8.0 else 10.0
        val legs = 2
        val av = legs * PI * dia * dia / 4.0
        val zoneLength = h
        val spacing = if (asBurst > 0) (av * zoneLength / asBurst).coerceIn(50.0, 150.0) else 100.0
        val providedBurst = if (spacing > 0) av * zoneLength / spacing else 0.0
        val adequate = asBurst <= 0.0 || providedBurst >= asBurst

        val notes = mutableListOf<String>()
        notes.add("Bursting force = ${"%.1f".format(bursting)} kN")
        notes.add("Spalling force = ${"%.1f".format(spalling)} kN")
        notes.add("As,burst = ${"%.0f".format(asBurst)} mm² over ${"%.0f".format(zoneLength)} mm")
        notes.add("Provide ${legs}Ø${dia.toInt()} @ ${"%.0f".format(spacing)} mm")
        if (!adequate) notes.add("Anchorage reinforcement insufficient — reduce spacing")

        return AnchorageZoneResult(
            burstingForce = bursting,
            spallingForce = spalling,
            burstingReinforcement = asBurst,
            spallingReinforcement = asSpall,
            stirrupDiameter = dia,
            stirrupSpacing = spacing,
            stirrupLegs = legs,
            zoneLength = zoneLength,
            isAdequate = adequate,
            notes = notes
        )
    }

    // ---------------------------------------------------------------------
    // Deflection
    // ---------------------------------------------------------------------

    fun calculateDeflection(input: PostTensionedInput, losses: PTLosses): PTDeflectionResult {
        input.validate()
        val props = sectionProperties(input)
        val ec = params.ec(input.fcu)
        val lMm = input.span * 1000.0
        val i = props.ixx

        val wDead = input.totalDeadLoad()   // kN/m = N/mm
        val wLive = input.liveLoad
        val e = providedEccentricity(input, props)
        val peN = peForce(input, losses)

        val deltaDead = if (ec * i > 0) 5.0 * wDead * lMm.pow(4) / (384.0 * ec * i) else 0.0
        val deltaLive = if (ec * i > 0) 5.0 * wLive * lMm.pow(4) / (384.0 * ec * i) else 0.0
        val deltaPrestress = if (ec * i > 0) 5.0 / 48.0 * peN * e * lMm * lMm / (ec * i) else 0.0 // upward

        val immediate = deltaDead + deltaLive - deltaPrestress
        val longTerm = (deltaDead - deltaPrestress) * (1.0 + params.creepCoefficient) + deltaLive
        val total = max(immediate, longTerm)
        val allowable = lMm / 250.0

        val notes = mutableListOf<String>()
        notes.add("δ_dead = ${"%.2f".format(deltaDead)} mm")
        notes.add("δ_live = ${"%.2f".format(deltaLive)} mm")
        notes.add("δ_prestress (upward) = ${"%.2f".format(-deltaPrestress)} mm")
        notes.add("δ_long-term = ${"%.2f".format(longTerm)} mm")
        notes.add("Allowable = L/250 = ${"%.2f".format(allowable)} mm")
        if (total > allowable) notes.add("Deflection exceeds L/250 — increase depth or prestress")

        return PTDeflectionResult(
            deadLoadDeflection = deltaDead,
            liveLoadDeflection = deltaLive,
            prestressDeflection = -deltaPrestress,
            totalImmediateDeflection = immediate,
            longTermDeflection = longTerm,
            totalDeflection = total,
            allowableDeflection = allowable,
            isSafe = total <= allowable,
            preCamber = deltaPrestress,
            notes = notes
        )
    }

    // ---------------------------------------------------------------------
    // Quantities
    // ---------------------------------------------------------------------

    fun quantities(input: PostTensionedInput, losses: PTLosses): PTQuantityEstimate {
        val props = sectionProperties(input)
        val lMm = input.span * 1000.0
        val lM = input.span
        val aps = input.tendonTotalArea()

        val concreteVolume = props.area * lMm / 1e9 // m³

        // Mild steel: 0.4% of concrete area + stirrups ~ 15 kg/m
        val longitudinalSteel = 0.004 * props.area * lMm / 1e9 * DENSITY_STEEL
        val stirrupSteel = 15.0 * lM
        val rebarWeight = longitudinalSteel + stirrupSteel

        val tendonWeight = aps * lMm / 1e9 * DENSITY_STEEL
        // One corrugated duct per tendon.
        val ductWeight = 1.2 * lM * input.numTendons
        val anchorageWeight = 2.0 * input.numTendons
        val ductArea = PI * DUCT_DIAMETER * DUCT_DIAMETER / 4.0
        val groutVolume = ductArea * lMm / 1e9 * input.numTendons
        val perimeter = 2.0 * (input.width + input.depth) / 1000.0
        val formworkArea = perimeter * lM

        val laborHours = 0.5 * concreteVolume * 8.0 + 0.1 * lM

        val costConcrete = concreteVolume * 1200.0
        val costSteel = rebarWeight * 18.0
        val costTendon = tendonWeight * 60.0
        val costDuct = ductWeight * 25.0
        val costAnchorage = anchorageWeight * 350.0
        val costGrout = groutVolume * 800.0
        val costFormwork = formworkArea * 150.0
        val totalCost = costConcrete + costSteel + costTendon + costDuct + costAnchorage + costGrout + costFormwork

        return PTQuantityEstimate(
            concreteVolume = concreteVolume,
            rebarWeight = rebarWeight,
            ptTendonWeight = tendonWeight,
            ductWeight = ductWeight,
            anchorageWeight = anchorageWeight,
            groutVolume = groutVolume,
            formworkArea = formworkArea,
            laborHours = laborHours,
            totalCost = totalCost
        )
    }

    // ---------------------------------------------------------------------
    // Full design
    // ---------------------------------------------------------------------

    fun design(input: PostTensionedInput): PostTensionedResult {
        input.validate()
        // Adopt the tendon count required by the design (the input value is a starting guess).
        // Iterate because losses (elastic shortening) depend on the provided tendon area.
        var designInput = input
        var prestress = calculateRequiredPrestress(designInput)
        var converged = false
        for (iteration in 0 until 6) {
            if (prestress.numTendons == designInput.numTendons) {
                converged = true
                break
            }
            designInput = designInput.copy(numTendons = prestress.numTendons)
            prestress = calculateRequiredPrestress(designInput)
        }
        val losses = calculateLosses(designInput)
        val stressCheck = checkStresses(designInput, losses)
        val ultimate = checkUltimateStrength(designInput, losses)
        val anchorage = designAnchorageZone(designInput, prestress.jackingForce)
        val deflection = calculateDeflection(designInput, losses)
        val quantity = quantities(designInput, losses)

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        if (!converged) warnings.add("Tendon count did not converge within 6 iterations")
        if (!prestress.isAdequate) warnings.add("Provided prestress is less than required")
        if (losses.totalPercent > 25.0) warnings.add("Prestress losses (${"%.1f".format(losses.totalPercent)}%) exceed 25%")
        if (input.cover < input.exposureClass.minCover) {
            warnings.add(
                "Provided cover ${"%.0f".format(input.cover)} mm is below the ${input.exposureClass.displayName} exposure minimum of ${"%.0f".format(input.exposureClass.minCover)} mm"
            )
        }
        if (input.fcu < input.exposureClass.minFcu) {
            warnings.add(
                "Concrete strength ${"%.1f".format(input.fcu)} MPa is below the ${input.exposureClass.displayName} exposure minimum of ${"%.1f".format(input.exposureClass.minFcu)} MPa"
            )
        }
        warnings.addAll(stressCheck.notes.filter { it.contains("exceed") || it.contains("insufficient") })
        warnings.addAll(ultimate.notes.filter { it.contains("insufficient") || it.contains("not tension-controlled") })
        warnings.addAll(deflection.notes.filter { it.contains("exceeds") })
        warnings.addAll(anchorage.notes.filter { it.contains("insufficient") })

        val strengthName = if (params.code == DesignCode.ECP) "fcu" else "f'c"
        val transferName = if (params.code == DesignCode.ECP) "fci" else "f'ci"
        codeNotes.add("${params.code.version}: Post-tensioned design")
        codeNotes.add("Jacking stress ≤ min(${params.jackingFpkRatio} fpk, ${params.jackingFp01kRatio} fp01k)")
        codeNotes.add("Transfer limits: fc ≤ ${params.transferCompRatio} $transferName, ft ≤ ${params.transferTensionFactor}√$transferName")
        codeNotes.add("Service limits: fc ≤ ${params.serviceCompRatio} $strengthName, ft ≤ ${params.serviceTensionFactor}√$strengthName")

        InputGuard.finite("effectiveForce", losses.effectiveForce)
        InputGuard.finite("flexuralCapacity", ultimate.flexuralCapacity)
        InputGuard.finite("totalDeflection", deflection.totalDeflection)

        val isSafe = prestress.isAdequate && stressCheck.overallSafe &&
            ultimate.overallSafe && deflection.isSafe && anchorage.isAdequate

        return PostTensionedResult(
            input = designInput,
            prestressDesign = prestress,
            losses = losses,
            stressCheck = stressCheck,
            ultimateStrength = ultimate,
            anchorageZone = anchorage,
            deflection = deflection,
            isSafe = isSafe,
            warnings = warnings.distinct(),
            codeNotes = codeNotes,
            quantity = quantity
        )
    }

    // ---------------------------------------------------------------------
    // Minimum prestress
    // ---------------------------------------------------------------------

    fun minimumPrestress(input: PostTensionedInput): Double {
        val props = sectionProperties(input)
        // حد أدنى متوسط إجهاد ضغط 0.86 MPa على المقطع (ACI 318 للبلاطات)
        return 0.86 * props.area / 1000.0 // kN
    }
}

/** معاملات ECP 203-2020 */
val ECP_PT_PARAMS = PTCodeParams(
    code = DesignCode.ECP,
    gammaC = 1.5,
    transferCompRatio = 0.50,
    transferTensionFactor = 0.25,
    serviceCompRatio = 0.45,
    serviceTensionFactor = 0.25,
    jackingFpkRatio = 0.80,
    jackingFp01kRatio = 0.90,
    relaxationPercent = 2.5,
    shrinkageStrain = 0.0003,
    creepCoefficient = 2.0,
    flexurePhi = 0.90,
    shearPhi = 0.75,
    deadFactor = 1.40,
    liveFactor = 1.60,
    cylinderFactor = 0.80,
    ecFactor = 4400.0,
    shearStrengthDivisor = 1.5,
    shearConcreteCoeff = 0.24,
    minStirrupRatio = 0.0015,
    strengthAtTransferRatio = 0.80,
    flexureBlockCoeff = 0.447 // 0.67 fcu / γc (γc = 1.5)
)

/** معاملات ACI 318-19 */
val ACI_PT_PARAMS = PTCodeParams(
    code = DesignCode.ACI,
    gammaC = 0.90,
    transferCompRatio = 0.60,
    transferTensionFactor = 0.25,
    serviceCompRatio = 0.45,
    serviceTensionFactor = 0.25,
    jackingFpkRatio = 0.80,
    jackingFp01kRatio = 0.94,
    relaxationPercent = 2.5,
    shrinkageStrain = 0.0003,
    creepCoefficient = 2.0,
    flexurePhi = 0.90,
    shearPhi = 0.75,
    deadFactor = 1.20,
    liveFactor = 1.60,
    cylinderFactor = 0.80,
    ecFactor = 4700.0,
    shearStrengthDivisor = 1.25, // fcu / 1.25 = 0.80 fcu = f'c
    shearConcreteCoeff = 0.17,
    minStirrupRatio = 0.00062,
    strengthAtTransferRatio = 0.60, // f'ci ≈ 0.75 f'c = 0.60 fcu
    flexureBlockCoeff = 0.85
)

/** معاملات SBC 304-2018 (يتبع ACI) */
val SBC_PT_PARAMS = ACI_PT_PARAMS.copy(code = DesignCode.SBC)