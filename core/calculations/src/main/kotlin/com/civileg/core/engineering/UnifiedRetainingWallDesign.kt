package com.civileg.core.engineering

import com.civileg.core.calculations.entities.DesignCode
import com.civileg.core.math.SafeMath
import com.civileg.core.sanity.EngineeringSanityEngine
import com.civileg.core.sanity.SanityReport
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Unified cantilever earth-retaining wall design.
 *
 * ONE skeleton for ECP 203-2020 (K-method, γc/γs), ACI 318-19 (Rn–ρ, φ) and
 * SBC 304-2018 (ACI 318 maths with Saudi FS and note text); golden-matched to
 * the legacy app ECPRetainingWall / ACIRetainingWall / SBCRetainingWall.
 *
 * The shared geotechnical model (identical in all three app classes) is the
 * A4-FIX layered Rankine active pressure with a water table at depth [zwt]:
 * a dry triangle, a submerged rectangle + buoyant triangle, a hydrostatic
 * triangle, and the surcharge — every resultant's lever arm about the toe is
 * transcribed from the legacy source so the stability factors are byte-exact.
 *
 * The families diverge only in the structural design of the stem, toe and heel:
 *
 *  - ECP: K = Mu/((fcu/γc)·b·d²), K/γc → z = d·(0.5+√(0.25−K/0.893)),
 *    fs = fy/γs, AsMin = max(0.26√fcu/fy, ρmin)·b·d, distribution = 0.0025·b·d,
 *    stem shear qu vs qcu = 0.24√(fcu/γc).
 *  - ACI/SBC: Rn = Mu/(φ·b·d²), ρ = (0.85f'c/fy)·(1−√(1−2Rn/(0.85f'c)))
 *    coerced into [ρmin = 0.0018, 0.025], distribution = max(ρmin·b·d/4, 100),
 *    shear Vu ≤ φ·0.17√f'c·b·d. SBC keeps ACI's maths but raises the
 *    overturning target and re-words the notes (legacy wraps ACIRetainingWall).
 *
 * Every COEFFICIENT comes from [ConcreteCodeParams] (the asymmetries of the
 * legacy — e.g. ECP exports the toe moment dead-factored but the toe shear
 * raw, ACI exports the heel moment dead-factored but the heel shear raw — are
 * family-exact UI channels, not recomputed). Code-fixed PHYSICAL rules —
 * γw = 9.81 kN/m³, R.C. density 25 kN/m³, the 0.893 K-method lever-arm
 * divisor, the 0.5 passive-released factor and the layered water-table arms —
 * are documented inline with their section references.
 */
class UnifiedRetainingWallDesign(
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
    private val isSbc get() = params.family == CodeFamily.SBC_304

    /** γs — ECP service steel stress fs = fy/γs. */
    private val gammaS get() = params.gammas?.gammaS ?: 1.0
    /** γc — ECP partial concrete factor. */
    private val gammaC get() = params.gammas?.gammaC ?: 1.0
    /** ACI cylinder strength f'c = 0.8×fcu (single explicit conversion point). */
    private val fc get() = concrete.cylinderStrengthMpa

    /**
     * Design a cantilever retaining wall (per metre run).
     *
     * @param input geometry, soil properties and material strengths — units
     *        exactly as the app's [RetainingWallInput] (metres for lengths,
     *        kN/m³ for density, degrees for the friction angle, kPa for
     *        surcharge and soil bearing capacity).
     */
    fun designRetainingWall(input: RetainingWallInput): Outcome {
        SafeMath.requirePositive(input.wallHeight, "wallHeight")
        SafeMath.requirePositive(input.stemBaseThickness, "stemBaseThickness")
        SafeMath.requirePositive(input.stemTopThickness, "stemTopThickness")
        SafeMath.requirePositive(input.baseWidth, "baseWidth")
        SafeMath.requirePositive(input.baseThickness, "baseThickness")
        SafeMath.requirePositive(input.toeLength, "toeLength")
        SafeMath.requirePositive(input.heelLength, "heelLength")
        SafeMath.requirePositive(input.soilDensity, "soilDensity")
        SafeMath.requirePositive(input.baseFrictionCoeff, "baseFrictionCoeff")
        SafeMath.requireNonNegative(input.surchargeLoad, "surchargeLoad")
        SafeMath.requireNonNegative(input.waterTableDepth, "waterTableDepth")
        require(input.frictionAngle > 0.0 && input.frictionAngle < 90.0) {
            "Invalid input: frictionAngle must be within (0, 90) degrees"
        }

        val H = input.wallHeight
        val tBase = input.stemBaseThickness
        val tTop = input.stemTopThickness
        val B = input.baseWidth
        val tFooting = input.baseThickness
        val toe = input.toeLength
        val heel = input.heelLength
        val gamma = input.soilDensity
        val phi = input.frictionAngle
        val q = input.surchargeLoad
        val zwt = input.waterTableDepth
        val mu = input.baseFrictionCoeff
        val bearingCapacity = input.soilBearingCapacity

        // ── Layered Rankine active pressure (A4-FIX, all families share it) ──
        val phiRad = Math.toRadians(phi)
        val ka = tan(PI / 4.0 - phiRad / 2.0).pow(2.0)

        val zw = if (zwt >= H) H else zwt.coerceAtLeast(0.0)
        val hw = (H - zw).coerceAtLeast(0.0)
        val gammaSub = gamma - GAMMA_W

        // Pressure trapezoids per metre run (kN/m).
        val pDry = 0.5 * ka * gamma * zw * zw
        val pSubRect = ka * gamma * zw * hw
        val pSubTri = 0.5 * ka * gammaSub * hw * hw
        val paWater = 0.5 * GAMMA_W * hw * hw
        val paSurcharge = ka * q * H
        val totalPa = pDry + pSubRect + pSubTri + paWater + paSurcharge

        // Each resultant acts at its centroid height above the base.
        val momentOverturning =
            pDry * (hw + zw / 3.0) +          // dry triangle acts at zw/3 above the gwt
                pSubRect * (hw / 2.0) +           // submerged rectangle at hw/2
                pSubTri * (hw / 3.0) +            // buoyant triangle at hw/3
                paWater * (hw / 3.0) +            // hydrostatic triangle at hw/3
                paSurcharge * (H / 2.0)           // surcharge uniform over H

        // Self-weight components (per metre run, R.C. density 25 kN/m³).
        val stemWeight = 0.5 * (tBase + tTop) * H * CONCRETE_DENSITY
        val baseWeight = B * tFooting * CONCRETE_DENSITY
        val soilOnHeel = heel * (H - tFooting) * gamma
        val totalWeight = stemWeight + baseWeight + soilOnHeel

        // Lever arms from the toe.
        val stemArm = toe + (tBase * (H + 2.0 * tTop)) / (3.0 * (tBase + tTop))
        val baseArm = B / 2.0
        val heelSoilArm = toe + tBase / 2.0 + heel / 2.0

        val momentResisting =
            stemWeight * stemArm + baseWeight * baseArm + soilOnHeel * heelSoilArm

        // Overturning / sliding / bearing (all families identical).
        val overturningFS = momentResisting / momentOverturning

        val passivePressure =
            0.5 * gamma * tFooting.pow(2.0) * tan(PI / 4.0 + phiRad / 2.0).pow(2.0)
        // Passive is conservatively released to 50 % in the legacy model.
        val slidingFS = (mu * totalWeight + passivePressure * 0.5) / totalPa

        val eccentricity = (momentResisting - momentOverturning) / totalWeight
        val e = abs(B / 2.0 - eccentricity)
        val maxBearing = totalWeight / B * (1.0 + 6.0 * e / B)
        val minBearing = max(0.0, totalWeight / B * (1.0 - 6.0 * e / B))
        val bearingFS = if (maxBearing > 0.0) bearingCapacity / maxBearing else 0.0

        // ── Structural design (family branch) ──
        val stemH = H - tFooting

        return if (isEcp) {
            designEcp(
                input, H, tBase, tFooting, toe, heel, gamma, q,
                ka, stemH, maxBearing, minBearing, bearingCapacity,
                overturningFS, slidingFS, bearingFS, hw, zwt
            )
        } else {
            designAciOrSbc(
                input, H, tBase, tFooting, toe, heel, gamma, q,
                ka, stemH, maxBearing, minBearing, bearingCapacity,
                overturningFS, slidingFS, bearingFS, hw, zwt
            )
        }
    }

    // ─────────────────────────────── ECP 203 ───────────────────────────────

    private fun designEcp(
        input: RetainingWallInput,
        H: Double, tBase: Double, tFooting: Double,
        toe: Double, heel: Double, gamma: Double, q: Double,
        ka: Double, stemH: Double,
        maxBearing: Double, minBearing: Double, bearingCapacity: Double,
        overturningFS: Double, slidingFS: Double, bearingFS: Double,
        hw: Double, zwt: Double
    ): Outcome {
        val checks = mutableListOf<WallCheck>()
        val codeNotes = mutableListOf<String>()

        // ── Stem (cantilever from the base) — K-method (§4-2-2-1) ──
        val stemMomentUnfactored = (ka * gamma * stemH.pow(3.0) / 6.0) + (ka * q * stemH.pow(2.0) / 2.0)
        val stemShearUnfactored = (ka * gamma * stemH.pow(2.0) / 2.0) + (ka * q * stemH)
        val muStem = params.retainingWallLateralLoadFactor() * stemMomentUnfactored
        val vuStem = params.retainingWallLateralLoadFactor() * stemShearUnfactored
        val b = 1000.0
        val d = tBase * 1000.0 - params.retainingWallCoverMm() - params.retainingWallStirrupEstimateMm()

        val fs = steel.yieldMpa / gammaS
        val r = muStem * 1e6 / (concrete.fcuMpa / gammaC * b * d * d)
        val k = r / gammaC
        val z = d * (0.5 + sqrt(max(0.0, 0.25 - k / ECP_LEVER_DIVISOR)))
        val asFlexural = muStem * 1e6 / (fs * z)
        val asMin = max(
            params.retainingWallMinSteelStressCoef() * sqrt(concrete.fcuMpa) / steel.yieldMpa,
            params.retainingWallMinSteelRatio()
        ) * b * d
        val asRequired = max(asFlexural, asMin)

        val stemSel = selectBars(asRequired)
        val asProvided = stemSel.count * PI * (stemSel.diameter / 2.0).pow(2.0)

        val distAs = params.retainingWallDistributionAreaMm2(b, d)
        val distSel = selectBars(distAs)

        // ── Stem shear (§4-3-1-2) ──
        val qu = vuStem * 1000.0 / (b * d)
        val qcu = 0.24 * sqrt(concrete.fcuMpa / gammaC)
        val needStirrups = qu > qcu   // legacy direct comparison

        // ── Toe (cantilever under upward bearing) ──
        val toeMomentRaw = max(0.0, maxBearing * toe * toe / 2.0 - minBearing * toe * toe / 6.0)
        val toeShearRaw = max(0.0, (maxBearing + minBearing) / 2.0 * toe)
        val toeD = tFooting * 1000.0 - params.retainingWallCoverMm() - params.retainingWallStirrupEstimateMm()
        val toeK = toeMomentRaw * 1e6 / (concrete.fcuMpa / gammaC * b * toeD * toeD) / gammaC
        val toeZ = toeD * (0.5 + sqrt(max(0.0, 0.25 - toeK / ECP_LEVER_DIVISOR)))
        val toeAsFlexural = toeMomentRaw * 1e6 / (fs * toeZ)
        val toeAsFinal = max(
            toeAsFlexural,
            max(
                params.retainingWallMinSteelStressCoef() * sqrt(concrete.fcuMpa) / steel.yieldMpa,
                params.retainingWallMinSteelRatio()
            ) * b * toeD
        )
        val toeSel = selectBars(toeAsFinal)

        // ── Heel (cantilever under soil + surcharge) ──
        val heelLoad = (H - tFooting) * gamma + q
        val heelMoment = heelLoad * heel * heel / 2.0 * params.retainingWallDeadLoadFactor()
        val heelShear = heelLoad * heel * params.retainingWallDeadLoadFactor()
        val heelD = tFooting * 1000.0 - params.retainingWallCoverMm() - params.retainingWallStirrupEstimateMm()
        val heelK = heelMoment * 1e6 / (concrete.fcuMpa / gammaC * b * heelD * heelD) / gammaC
        val heelZ = heelD * (0.5 + sqrt(max(0.0, 0.25 - heelK / ECP_LEVER_DIVISOR)))
        val heelAsFlexural = heelMoment * 1e6 / (fs * heelZ)
        val heelAsFinal = max(
            heelAsFlexural,
            max(
                params.retainingWallMinSteelStressCoef() * sqrt(concrete.fcuMpa) / steel.yieldMpa,
                params.retainingWallMinSteelRatio()
            ) * b * heelD
        )
        val heelSel = selectBars(heelAsFinal)

        // ── Report rows (byte-exact ECP wording) ──
        checks += WallCheck(
            "OT FS",
            overturningFS >= params.retainingWallOtFsLimit(),
            overturningFS, params.retainingWallOtFsLimit(),
            "Overturning: ${"%.2f".format(overturningFS)} >= ${params.retainingWallOtFsLimit()}"
        )
        checks += WallCheck(
            "Sliding FS",
            slidingFS >= params.retainingWallSlidingFsLimit(),
            slidingFS, params.retainingWallSlidingFsLimit(),
            "Sliding: ${"%.2f".format(slidingFS)} >= ${params.retainingWallSlidingFsLimit()}"
        )
        checks += WallCheck(
            "Bearing",
            bearingFS >= 1.0, bearingFS, bearingCapacity,
            "Max: ${"%.1f".format(maxBearing)} kN/m\u00B2 <= ${"%.1f".format(bearingCapacity)}"
        )
        checks += WallCheck(
            "Stem Flexure",
            asProvided >= asRequired, asProvided, asRequired,
            "As=${"%.0f".format(asProvided)} >= Req=${"%.0f".format(asRequired)} mm\u00B2/m"
        )
        checks += WallCheck(
            "Shear",
            !needStirrups, qu, 0.67 * qcu,
            "qu=${"%.2f".format(qu)} <= ${"%.2f".format(0.67 * qcu)}"
        )
        val isSafe = checks.all { it.isSafe }

        codeNotes += "ECP 203: \u03B3c=${gammaC}, \u03B3s=${gammaS}"
        codeNotes += "Ka = ${"%.3f".format(ka)} (Rankine)"
        codeNotes += "Cover = ${params.retainingWallCoverMm()}mm (earth contact)"
        codeNotes += "Min steel = ${"%.4f".format(params.retainingWallMinSteelRatio())} " +
            "(${params.retainingWallMinSteelRatio() * 100}%)"
        if (hw > 0) {
            codeNotes += "Water table at ${"%.1f".format(zwt)}m - hydrostatic pressure included"
        }

        return buildOutcome(
            isSafe, overturningFS, slidingFS, bearingFS,
            maxBearing, minBearing,
            muStem, vuStem,
            stemSel, asProvided, distSel,
            toeMomentRaw * params.retainingWallDeadLoadFactor(), toeShearRaw, toeSel,
            heelMoment, heelShear, heelSel,
            checks, codeNotes
        )
    }

    // ───────────────────────── ACI 318 / SBC 304 ─────────────────────────

    private fun designAciOrSbc(
        input: RetainingWallInput,
        H: Double, tBase: Double, tFooting: Double,
        toe: Double, heel: Double, gamma: Double, q: Double,
        ka: Double, stemH: Double,
        maxBearing: Double, minBearing: Double, bearingCapacity: Double,
        overturningFS: Double, slidingFS: Double, bearingFS: Double,
        hw: Double, zwt: Double
    ): Outcome {
        val checks = mutableListOf<WallCheck>()
        val codeNotes = mutableListOf<String>()
        val phi = params.phiFlexure ?: 0.9
        val rhoMin = params.retainingWallMinSteelRatio()

        // ── Stem (cantilever from the base) — Rn–ρ (§22.2) ──
        val muStemKn = params.retainingWallLateralLoadFactor() *
            (ka * gamma * stemH.pow(3.0) / 6.0 + ka * q * stemH.pow(2.0) / 2.0)
        val vuStem = params.retainingWallLateralLoadFactor() *
            (ka * gamma * stemH.pow(2.0) / 2.0 + ka * q * stemH)
        val muStem = muStemKn * 1e6 // N·mm
        val b = 1000.0
        val d = tBase * 1000.0 - params.retainingWallCoverMm() - params.retainingWallStirrupEstimateMm()

        // Legacy ACI keeps the raw sqrt (no guard) — a degenerate disc mirrors it.
        val rn = muStem / (phi * b * d * d)
        val disc = 1.0 - 2.0 * rn / (0.85 * fc)
        val rho = (0.85 * fc / steel.yieldMpa) * (1.0 - sqrt(disc))
        val rhoFinal = rho.coerceIn(rhoMin, params.retainingWallMaxFlexuralSteelRatio())
        val asRequired = rhoFinal * b * d

        val stemSel = selectBars(asRequired)
        val asProvided = stemSel.count * PI * (stemSel.diameter / 2.0).pow(2.0)

        val distAs = params.retainingWallDistributionAreaMm2(b, d)
        val distSel = selectBars(distAs)

        // ── Stem shear (§22.5.5.1) ──
        val qu = vuStem * 1000.0 / (b * d)
        val phiVc = (params.retainingWallShearPhi ?: 0.75) * 0.17 * sqrt(fc) * b * d
        val shearOk = vuStem * 1000.0 <= phiVc

        // ── Toe ──
        val toeMomentRaw = max(0.0, (maxBearing * toe * toe / 2.0 - minBearing * toe * toe / 6.0))
        val toeMu = toeMomentRaw * 1e6
        val toeD = tFooting * 1000.0 - params.retainingWallCoverMm() - params.retainingWallStirrupEstimateMm()
        val toeRn = toeMu / (phi * b * toeD * toeD)
        val toeDisc = 1.0 - 2.0 * toeRn / (0.85 * fc)
        val toeRho = (0.85 * fc / steel.yieldMpa) * (1.0 - sqrt(max(0.0, toeDisc)))
        val toeAs = max(toeRho, rhoMin) * b * toeD
        val toeSel = selectBars(toeAs)

        // ── Heel (soil + surcharge are DEAD load) ──
        val heelLoad = (H - tFooting) * gamma + q
        val heelMu = heelLoad * heel * heel / 2.0 * params.retainingWallDeadLoadFactor() * 1e6
        val heelD = tFooting * 1000.0 - params.retainingWallCoverMm() - params.retainingWallStirrupEstimateMm()
        val heelRn = heelMu / (phi * b * heelD * heelD)
        val heelDisc = 1.0 - 2.0 * heelRn / (0.85 * fc)
        val heelRho = (0.85 * fc / steel.yieldMpa) * (1.0 - sqrt(max(0.0, heelDisc)))
        val heelAs = max(heelRho, rhoMin) * b * heelD
        val heelSel = selectBars(heelAs)

        // ── Report rows (byte-exact family wording) ──
        checks += WallCheck(
            "OT FS",
            overturningFS >= params.retainingWallOtFsLimit(),
            overturningFS, params.retainingWallOtFsLimit(),
            otFsDescription(overturningFS)
        )
        checks += WallCheck(
            "Sliding FS",
            slidingFS >= params.retainingWallSlidingFsLimit(),
            slidingFS, params.retainingWallSlidingFsLimit(),
            slidingFsDescription(slidingFS)
        )
        checks += WallCheck(
            "Bearing",
            maxBearing <= bearingCapacity, maxBearing, bearingCapacity,
            "${"%.1f".format(maxBearing)} <= ${"%.1f".format(bearingCapacity)} kN/m\u00B2"
        )
        checks += WallCheck(
            "Stem Flexure",
            asProvided >= asRequired * 0.95, asProvided, asRequired,
            "\u03C1=${"%.4f".format(rhoFinal)} (min=${rhoMin})"
        )
        checks += WallCheck(
            "Shear",
            shearOk, qu, phiVc / (b * d),
            "Vu=${"%.0f".format(qu)} <= \u03C6Vc=${"%.0f".format(phiVc / (b * d))} N/mm\u00B2"
        )
        val isSafe = checks.all { it.isSafe }

        if (isSbc) {
            codeNotes += "SBC 304: Based on ACI 318 methodology with Saudi modifications"
            codeNotes += "Min \u03C1 = ${params.retainingWallMinSteelRatioNote()} (hot/arid climate durability)"
            codeNotes += "Cover: 65.0mm exterior, 40.0mm interior"
            codeNotes += "Coastal areas: 75.0mm cover recommended"
            if (overturningFS < 2.0) {
                codeNotes += "Consider seismic earth pressure increment (0.5\u00D7Ka) for seismic zones"
            }
        } else {
            codeNotes += "ACI 318: \u03C6f=${phi}, \u03C6v=${params.retainingWallShearPhi ?: 0.75}"
            codeNotes += "f'c = 0.8\u00D7fcu = ${"%.0f".format(fc)} MPa"
            codeNotes += "Ka = ${"%.3f".format(ka)} (Rankine)"
            codeNotes += "Cover = ${params.retainingWallCoverMm()}mm (earth contact)"
            codeNotes += "Min \u03C1 = ${rhoMin}"
            if (hw > 0) {
                codeNotes += "Water table at ${"%.1f".format(zwt)}m - hydrostatic pressure included"
            }
        }

        return buildOutcome(
            isSafe, overturningFS, slidingFS, bearingFS,
            maxBearing, minBearing,
            muStem / 1e6, vuStem,
            stemSel, asProvided, distSel,
            toeMu / 1e6, maxBearing * toe, toeSel,
            heelMu / 1e6, heelLoad * heel, heelSel,
            checks, codeNotes
        )
    }

    private fun otFsDescription(v: Double): String = when (params.family) {
        CodeFamily.ECP_203 -> "Overturning: ${"%.2f".format(v)} >= ${params.retainingWallOtFsLimit()}"
        CodeFamily.ACI_318 -> "ACI: FS=${"%.2f".format(v)} >= ${params.retainingWallOtFsLimit()}"
        CodeFamily.SBC_304 -> "SBC 304: FS=${"%.2f".format(v)} >= ${params.retainingWallOtFsLimit()}"
    }

    private fun slidingFsDescription(v: Double): String = when (params.family) {
        CodeFamily.ECP_203 -> "Sliding: ${"%.2f".format(v)} >= ${params.retainingWallSlidingFsLimit()}"
        CodeFamily.ACI_318 -> "FS=${"%.2f".format(v)} >= ${params.retainingWallSlidingFsLimit()}"
        CodeFamily.SBC_304 -> "SBC 304: FS=${"%.2f".format(v)} >= ${params.retainingWallSlidingFsLimit()}"
    }

    // ────────────────────── outcome assembly ──────────────────────

    private fun buildOutcome(
        isSafe: Boolean,
        overturningFS: Double, slidingFS: Double, bearingFS: Double,
        maxBearing: Double, minBearing: Double,
        stemMoment: Double, stemShear: Double,
        stemSel: BarSelection, stemArea: Double, distSel: BarSelection,
        toeMoment: Double, toeShear: Double, toeSel: BarSelection,
        heelMoment: Double, heelShear: Double, heelSel: BarSelection,
        checks: List<WallCheck>, codeNotes: List<String>
    ): Outcome {
        val trace = CalculationTrace()
        checks.forEach { c ->
            trace.add(
                c.name, "", "", fmt0(c.value), fmt0(c.limit),
                if (c.isSafe) CheckStatus.PASS else CheckStatus.FAIL,
                utilization = if (c.limit != 0.0) c.value / c.limit else null,
                codeReference = c.description
            )
        }
        val stemSpacing = 1000.0 / stemSel.count
        val distSpacing = 1000.0 / distSel.count
        val toeSpacing = 1000.0 / toeSel.count
        val heelSpacing = 1000.0 / heelSel.count

        val outcome = Outcome(
            code = code,
            isSafe = isSafe,
            overturningFS = overturningFS,
            slidingFS = slidingFS,
            bearingFS = bearingFS,
            maxBearingPressure = maxBearing,
            minBearingPressure = minBearing,
            stemMoment = stemMoment,
            stemShear = stemShear,
            stemMainRebar = formatRebar(stemSel.count, stemSel.diameter),
            stemMainRebarCount = stemSel.count,
            stemMainRebarDiameter = stemSel.diameter,
            stemMainRebarSpacingMm = stemSpacing,
            stemMainRebarArea = stemArea,
            stemDistributionRebar = formatRebar(distSel.count, distSel.diameter),
            distributionBarsCount = distSel.count,
            distributionBarsDiameter = distSel.diameter,
            distributionSpacingMm = distSpacing,
            toeMoment = toeMoment,
            toeShear = toeShear,
            toeRebar = formatRebar(toeSel.count, toeSel.diameter),
            toeRebarCount = toeSel.count,
            toeRebarDiameter = toeSel.diameter,
            toeSpacingMm = toeSpacing,
            heelMoment = heelMoment,
            heelShear = heelShear,
            heelRebar = formatRebar(heelSel.count, heelSel.diameter),
            heelRebarCount = heelSel.count,
            heelRebarDiameter = heelSel.diameter,
            heelSpacingMm = heelSpacing,
            safetyChecks = checks,
            codeNotes = codeNotes,
            trace = trace,
            overallStatus = trace.overall,
            sanity = SanityReport("RetainingWallOutcome", emptyList())
        )
        val sanityReport = EngineeringSanityEngine.check(outcome)
        val overall = when {
            sanityReport.hasError -> CheckStatus.FAIL
            sanityReport.hasWarning && trace.overall == CheckStatus.PASS -> CheckStatus.WARNING
            else -> trace.overall
        }
        return outcome.copy(overallStatus = overall, sanity = sanityReport)
    }

    // ──────────────────────── bar selection ────────────────────────

    /**
     * First-fit market selection over count-then-diameter (the legacy
     * RetainingWallDesign.selectBars): smallest n × π·(d/2)² ≥ required.
     */
    private fun selectBars(requiredArea: Double, maxBars: Int = 12): BarSelection {
        val menu = params.retainingWallBarMenuMm()
        for (n in 1..maxBars) {
            for (d in menu) {
                val area = n * PI * (d / 2.0).pow(2.0)
                if (area >= requiredArea) return BarSelection(n, d)
            }
        }
        return BarSelection(maxBars, menu.last())
    }

    private fun formatRebar(count: Int, dia: Double): String = "${count}\u03A6${dia.toInt()}"

    private fun fmt0(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.0f", v)

    private data class BarSelection(val count: Int, val diameter: Double)

    data class Outcome(
        val code: DesignCode,
        val isSafe: Boolean,
        val overturningFS: Double,
        val slidingFS: Double,
        val bearingFS: Double,
        val maxBearingPressure: Double,
        val minBearingPressure: Double,
        val stemMoment: Double,
        val stemShear: Double,
        val stemMainRebar: String,
        val stemMainRebarCount: Int,
        val stemMainRebarDiameter: Double,
        val stemMainRebarSpacingMm: Double,
        val stemMainRebarArea: Double,
        val stemDistributionRebar: String,
        val distributionBarsCount: Int,
        val distributionBarsDiameter: Double,
        val distributionSpacingMm: Double,
        val toeMoment: Double,
        val toeShear: Double,
        val toeRebar: String,
        val toeRebarCount: Int,
        val toeRebarDiameter: Double,
        val toeSpacingMm: Double,
        val heelMoment: Double,
        val heelShear: Double,
        val heelRebar: String,
        val heelRebarCount: Int,
        val heelRebarDiameter: Double,
        val heelSpacingMm: Double,
        val safetyChecks: List<WallCheck>,
        val codeNotes: List<String>,
        val trace: CalculationTrace,
        val overallStatus: CheckStatus,
        val sanity: SanityReport
    )
}

/** Retaining-wall geometry + soil inputs (mirror of the app RetainingWallInput). */
data class RetainingWallInput(
    val wallHeight: Double,
    val stemBaseThickness: Double,
    val stemTopThickness: Double,
    val baseWidth: Double,
    val baseThickness: Double,
    val toeLength: Double,
    val heelLength: Double,
    val soilDensity: Double,
    val frictionAngle: Double,
    val surchargeLoad: Double,
    val waterTableDepth: Double,
    val fcu: Double,
    val fy: Double,
    val baseFrictionCoeff: Double = 0.5,
    val soilBearingCapacity: Double = 200.0
)

/** One safety-check row of the wall outcome (mirror of the app WallSafetyCheck). */
data class WallCheck(
    val name: String,
    val isSafe: Boolean,
    val value: Double,
    val limit: Double,
    val description: String
)

/** Unit weight of water — physical constant (kN/m³). */
private const val GAMMA_W = 9.81

/** Unit weight of reinforced concrete — physical constant (kN/m³). */
private const val CONCRETE_DENSITY = 25.0

/** ECP 203 K-method lever-arm divisor (§4-2-2-1). */
private const val ECP_LEVER_DIVISOR = 0.893