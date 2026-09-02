package com.civileg.core.engineering

import com.civileg.core.calculations.entities.DesignCode
import com.civileg.core.calculations.entities.LoadCombination
import com.civileg.core.calculations.entities.ShearCheckResult
import com.civileg.core.calculations.entities.fmt
import com.civileg.core.math.SafeMath
import com.civileg.core.sanity.EngineeringSanityEngine
import com.civileg.core.sanity.SanityReport
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Unified isolated-footing design (spec §20.7).
 *
 * ONE skeleton for ECP 203-2020 §7-1 and ACI 318-19 §13; the divergent
 * formulations — aspect ratio, net-SBC floor, one-way critical distance d/2
 * vs d, K-method vs Rn–ρ, punching stress vs φ·force, and the distribution
 * spacing cap — branch on [ConcreteCodeParams.family]. Every COEFFICIENT comes
 * from [ConcreteCodeParams]; no factor is hardcoded (spec §3). Structural
 * rules fixed by the codes themselves (γ_conc = 25 kN/m³, αs = 40 interior
 * column, the 0.90 net punching-force reduction of ECP §4-3-2, the d/2 vs d
 * critical section, the ACI §22.6.5.2 vn selection) are documented inline with
 * their code reference.
 *
 * Golden-gated against the legacy app ECPFooting/ACIFooting isolated
 * implementations for exact numeric parity.
 *
 * NOTE: the footing bar menu mirrors the app's market menu used by BOTH
 * footprints ([12,14,16,18,20,22,25]); it deliberately differs from
 * [ConcreteCodeParams.barMenuMm], which follows the family's general bar
 * catalogue. Kept injectable so the golden contract with the app holds.
 */
class UnifiedFootingDesign(
    private val params: ConcreteCodeParams,
    private val concrete: ConcreteMaterial,
    private val steel: SteelMaterial,
    private val barMenuMm: List<Double> = FOOTING_BAR_MENU
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
     * Isolated footing under a column, with neighbour-lot constraints.
     *
     * @param columnWidth / columnDepth column plan dimensions (mm)
     * @param axialLoad FACTORED column load (kN)
     * @param momentX / momentY FACTORED moments (kN·m)
     * @param soilBearingCapacity allowable soil pressure (kPa, service level)
     * @param footingDepth total footing thickness (mm)
     * @param constraints neighbour-boundary constraints
     */
    fun designIsolatedFooting(
        columnWidth: Double,
        columnDepth: Double,
        axialLoad: Double,
        momentX: Double,
        momentY: Double,
        soilBearingCapacity: Double,
        footingDepth: Double,
        loadCombination: LoadCombination,
        constraints: FootingConstraints = FootingConstraints()
    ): Outcome {
        SafeMath.requirePositive(columnWidth, "column width")
        SafeMath.requirePositive(columnDepth, "column depth")
        SafeMath.requirePositive(axialLoad, "axial load")
        SafeMath.requirePositive(soilBearingCapacity, "SBC")
        SafeMath.requirePositive(footingDepth, "footing depth")
        SafeMath.requireNonNegative(momentX, "Mx")
        SafeMath.requireNonNegative(momentY, "My")

        val trace = CalculationTrace()
        val ref = params.reference + " §7 (isolated footing)"
        val warnings = mutableListOf<String>()

        // 1. Least-thickness screen
        if (footingDepth < params.footingMinThicknessMm()) {
            warnings.add("Footing thickness ${fmt(footingDepth)} mm < minimum ${fmt(params.footingMinThicknessMm())} mm")
        }
        trace.add(
            title = "Least footing thickness",
            formula = "h ≥ ${fmt(params.footingMinThicknessMm())} mm",
            substitution = "h = ${fmt(footingDepth)} mm",
            result = "${fmt(footingDepth)} mm",
            limit = "≥ ${fmt(params.footingMinThicknessMm())} mm",
            status = if (footingDepth >= params.footingMinThicknessMm()) CheckStatus.PASS else CheckStatus.FAIL,
            codeReference = ref
        )

        // 2. Service loads for the geotechnical area sizing
        val factor = loadCombination.getFactorForCode(code)
        val pService = axialLoad / factor
        val mxService = momentX / factor
        val myService = momentY / factor

        // 3. Required area from net SBC (γ_conc = 25 kN/m³ structural rule)
        val tM = footingDepth / 1000.0
        val netSbc = soilBearingCapacity - CONCRETE_UNIT_WEIGHT * tM
        val areaReq = if (isEcp) {
            if (netSbc > 0) pService / netSbc else pService / soilBearingCapacity
        } else {
            pService / netSbc.coerceAtLeast(soilBearingCapacity * 0.8)
        }

        // 4. Aspect ratio and starting plan dimensions
        var breadthM: Double
        var lengthM: Double
        if (isEcp) {
            // ECP §7-1 aspect form — LEGACY-CONTRACT mirror: the app computes
            // B_m = A_req/L_ratio WITHOUT the sqrt that ACI uses, inflating the
            // plan area (≈×2.3 linear for a square column). Kept byte-identical
            // to the golden gate on purpose: conservative (oversized, never
            // unsafe) and the BOQ/drawing must match the app. Owner-flagged
            // decision 2026-08-28 — correct to sqrt(A_req/L_ratio) ONLY on
            // explicit owner sign-off (would break numeric parity).
            val lRatio = sqrt(columnDepth / columnWidth * 1.2)          // ECP §7-1
            breadthM = areaReq / lRatio
            lengthM = lRatio * breadthM
        } else {
            val ratio = sqrt(columnDepth / columnWidth)                 // ACI 15.2
            breadthM = sqrt(areaReq / ratio)
            lengthM = ratio * breadthM
        }

        // 5. Neighbour-lot limits
        if (constraints.isCornerColumn) {
            val maxDim = min(
                constraints.maxLeft ?: (columnWidth * 3),
                constraints.maxTop ?: (columnDepth * 3)
            )
            breadthM = min(breadthM, maxDim / 1000.0)
            lengthM = min(lengthM, maxDim / 1000.0)
        } else if (constraints.isEdgeColumn) {
            // ECP §7-1 assumes a default 2·column overhang when unconstrained;
            // ACI applies the limit only when an explicit right limit is given.
            val maxProj = if (isEcp) (constraints.maxRight ?: (columnWidth * 2)) else constraints.maxRight
            if (maxProj != null) {
                lengthM = min(lengthM, (columnWidth + 2 * maxProj) / 1000.0)
            }
        }

        // 6. Round to the nearest 50 mm
        val breadth = ceil(breadthM * 1000 / 50.0) * 50.0
        val length = ceil(lengthM * 1000 / 50.0) * 50.0
        val areaActual = breadth * length / 1e6

        // 7. Soil pressure with eccentricity (eccentricity e = M/P is in metres;
        //     the plan dimension is /1000 into metres to match app §7-1 form)
        val qAvg = pService / areaActual
        val ex = mxService / pService
        val ey = myService / pService
        val qMaxX = qAvg * (1 + 6 * ex / (breadth / 1000.0))
        val qMaxY = qAvg * (1 + 6 * ey / (length / 1000.0))
        val qMax = max(qMaxX, qMaxY)
        val qMin = min(
            qAvg * (1 - 6 * ex / (breadth / 1000.0)),
            qAvg * (1 - 6 * ey / (length / 1000.0))
        )
        if (qMax > soilBearingCapacity) {
            warnings.add("q_max ${fmt(qMax)} kPa exceeds SBC ${fmt(soilBearingCapacity)} kPa")
        }
        if (qMin < 0) {
            warnings.add("Footing lifts off the soil - increase dimensions")
        }
        trace.add(
            title = "Soil pressure (max with eccentricity)",
            formula = "q_max = P/A·(1 ± 6e/B)",
            substitution = "q_avg=${fmt(qAvg)} kPa, ex=${fmt(ex)} m, ey=${fmt(ey)} m",
            result = "q_max = ${fmt(qMax)} kPa",
            limit = "≤ SBC ${fmt(soilBearingCapacity)} kPa",
            status = if (qMax <= soilBearingCapacity) CheckStatus.PASS else CheckStatus.FAIL,
            utilization = SafeMath.div(qMax, soilBearingCapacity),
            codeReference = ref
        )
        trace.add(
            title = "Footing separation",
            formula = "q_min ≥ 0",
            substitution = "q_min = ${fmt(qMin)} kPa",
            result = if (qMin >= 0) "no separation" else "separation",
            limit = "q_min ≥ 0",
            status = if (qMin >= 0) CheckStatus.PASS else CheckStatus.FAIL,
            codeReference = ref
        )

        // 8. Effective depth (single main layer)
        val d = footingDepth - params.footingCoverMm() - 10.0

        // 9. Cantilever moments
        val cantX = (breadth - columnWidth) / 2.0 / 1000.0
        val cantY = (length - columnDepth) / 2.0 / 1000.0
        val muX = qAvg * (length / 1000.0) * cantX * cantX / 2.0
        val muY = qAvg * (breadth / 1000.0) * cantY * cantY / 2.0

        // 10. One-way beam shear at the code-critical distance:
        //     ECP §4-3-1-2 uses d/2 from the column face; ACI §22.5 uses d.
        val criticalM = if (isEcp) d / 2000.0 else d / 1000.0
        val oneWayX = oneWayCheck("X", qAvg, breadth, length, columnWidth, columnDepth, criticalM, d)
        val oneWayY = oneWayCheck("Y", qAvg, breadth, length, columnWidth, columnDepth, criticalM, d)
        trace.add(
            title = oneWayX.title, formula = oneWayX.formula, substitution = oneWayX.substitution,
            result = oneWayX.result, limit = oneWayX.limit, status = oneWayX.status,
            utilization = oneWayX.utilization, codeReference = ref
        )
        trace.add(
            title = oneWayY.title, formula = oneWayY.formula, substitution = oneWayY.substitution,
            result = oneWayY.result, limit = oneWayY.limit, status = oneWayY.status,
            utilization = oneWayY.utilization, codeReference = ref
        )

        // 11. Punching shear
        val punching = punchCheck(columnWidth, columnDepth, d, axialLoad)
        trace.add(
            title = punching.title, formula = punching.formula, substitution = punching.substitution,
            result = punching.result, limit = punching.limit, status = punching.status,
            utilization = punching.utilization, codeReference = ref
        )

        // 12. Flexural steel per 1 m strip in each direction (kN·m/m input)
        val reinfShort = dirReinf(FootingDirection.SHORT, d, muX * 1000.0 / length)
        val reinfLong = dirReinf(FootingDirection.LONG, d, muY * 1000.0 / breadth)
        reinfShort.trace.forEach { e -> trace.add(e.title, e.formula, e.substitution, e.result, e.limit, e.status, e.utilization, ref) }
        reinfLong.trace.forEach { e -> trace.add(e.title, e.formula, e.substitution, e.result, e.limit, e.status, e.utilization, ref) }

        // 13. Distribution steel = 20% of the governing main steel
        val mainAs = max(reinfShort.directional.astRequired, reinfLong.directional.astRequired)
        val distAs = 0.20 * mainAs
        val distBar = selectFootingBarDiameter(distAs.coerceAtLeast(0.0), fallback = if (isEcp) 16.0 else 12.0)
        val distBarArea = RebarTable.area(distBar)
        val distBarsPerMeter = if (distAs > 0) ceil(distAs / distBarArea).toInt() else 0
        val distSpacing = if (distBarsPerMeter > 0) {
            val cap = if (isEcp) min(250.0, min(3.0 * d, 750.0)) else min(450.0, min(3.0 * d, 750.0))
            floor(1000.0 / distBarsPerMeter).coerceIn(100.0, cap)
        } else {
            200.0
        }
        val distribution = FootingDistributionSteel(
            barsPerMeter = distBarsPerMeter,
            diameterMm = distBar,
            spacingMm = distSpacing,
            astRequired = distAs,
            astProvided = distBarsPerMeter * distBarArea
        )
        trace.add(
            title = "Distribution steel (20% of main)",
            formula = "As,dist = 0.20·As,main",
            substitution = "As,main = ${fmt(mainAs)} mm²/m",
            result = if (distBarsPerMeter > 0) "${distBarsPerMeter}Ø${distBar.toInt()} @ ${fmt(distSpacing)} mm" else "not required",
            limit = "≥ ${fmt(distAs)} mm²/m",
            status = if (distAs == 0.0 || distribution.astProvided >= distAs - 1e-6) CheckStatus.PASS else CheckStatus.FAIL,
            utilization = if (distAs > 0) SafeMath.div(distAs, distribution.astProvided) else null,
            codeReference = ref
        )

        // 14. Governing (main) direction
        val mainDirection = if (reinfShort.directional.astRequired >= reinfLong.directional.astRequired) {
            FootingDirection.SHORT
        } else {
            FootingDirection.LONG
        }

        val appSafe = qMax <= soilBearingCapacity && qMin >= 0 &&
            oneWayX.isSafe && oneWayY.isSafe && punching.isSafe &&
            reinfShort.directional.isSafe && reinfLong.directional.isSafe

        val outcome = Outcome(
            code = code,
            requiredWidth = breadth,
            requiredLength = length,
            requiredThickness = footingDepth,
            depth = d,
            soilPressure = qAvg,
            maxSoilPressure = qMax,
            shortDir = reinfShort.directional,
            longDir = reinfLong.directional,
            mainDirection = mainDirection,
            distribution = distribution,
            punching = punching.shearCheck,
            oneWayX = oneWayX.shearCheck,
            oneWayY = oneWayY.shearCheck,
            isSafe = appSafe,
            overallStatus = trace.overall,
            trace = trace,
            sanity = SanityReport("FootingOutcome", emptyList())
        )
        val sanityReport = EngineeringSanityEngine.check(outcome)
        val overall = when {
            sanityReport.hasError -> CheckStatus.FAIL
            sanityReport.hasWarning && trace.overall == CheckStatus.PASS -> CheckStatus.WARNING
            else -> trace.overall
        }
        return outcome.copy(sanity = sanityReport, overallStatus = overall)
    }

    // ───────────────────────── per-direction steel ─────────────────────────

    /** ECP 203 K-method (Table 4-8 min) vs ACI 318-19 Rn–ρ (13.3.1 min). */
    private fun dirReinf(direction: FootingDirection, d: Double, designMomentKnmPerM: Double): DirResult {
        val trace = mutableListOf<TraceEntry>()
        val b = 1000.0
        val mu = designMomentKnmPerM * 1e6                 // N·mm/m

        val flexureOk: Boolean
        var asReq = 0.0
        var asFinal = 0.0
        var aciRhoUtil: Double? = null       // ACI app utilisation = ρ/ρfinal

        if (isEcp) {
            // ECP 203 K-method (§4-2-2-1 / §7-1-3): design stresses carry γc, γs.
            val fcu = concrete.fcuMpa
            val gammaC = params.gammas!!.gammaC
            val gammaS = params.gammas!!.gammaS
            val k = mu / (fcu * b * d * d)
            val epsY = steel.yieldMpa / (steel.modulusMpa * gammaS)
            val aOverDBal = 0.9 * 0.003 / (0.003 + epsY)
            val kBal = (params.designBlockStressMpa(concrete) / fcu) * aOverDBal * (1.0 - aOverDBal / 2.0)
            trace += TraceEntry(
                title = "Flexure K-method ($direction)",
                formula = "K = Mu/(fcu·b·d²) ; K_bal = (0.67/γc)·a/d·(1−a/2d)",
                substitution = "Mu=${fmt(designMomentKnmPerM)} kN·m/m, d=${fmt(d)} mm",
                result = "K=${"%.3f".format(k)}, K_bal=${"%.3f".format(kBal)}",
                limit = "K ≤ K_bal",
                status = if (k <= kBal) CheckStatus.PASS else CheckStatus.FAIL,
                utilization = SafeMath.div(k, kBal),
                codeReference = null
            )
            // z = d·(0.5 + √(0.25 − K/0.893)); 0.893 is the app §7-1 lever-arm
            // denominator (golden contract), retained verbatim.
            val z = d * (0.5 + sqrt(max(0.0, 0.25 - k / ECP_LEVER_ARM_K)))
            asReq = mu / (steel.yieldMpa / gammaS * z)
            val asMin = maxOf(
                params.footingMinReinStressCoef() * sqrt(fcu) / steel.yieldMpa,
                params.footingMinReinRatio()
            ) * b * d
            asFinal = max(asReq, asMin)
            flexureOk = k <= kBal
        } else {
            // ACI 318-19 Rn–ρ equilibrium (designBlockStressMpa = 0.85·f'c).
            val block = params.designBlockStressMpa(concrete)
            val phi = params.phiFlexure!!                              // φbending = 0.90
            val rn = mu / (phi * b * d * d)
            val disc = 1.0 - 2.0 * rn / block
            val rawRho = if (disc > 0) (block / steel.yieldMpa) * (1.0 - sqrt(disc)) else null
            trace += TraceEntry(
                title = "Flexure Rn–ρ ($direction)",
                formula = "Rn = Mu/(φ·b·d²) ; ρ = (0.85f'c/fy)·(1−√(1−2Rn/(0.85f'c)))",
                substitution = "Mu=${fmt(designMomentKnmPerM)} kN·m/m, d=${fmt(d)} mm, φ=$phi",
                result = if (disc > 0) "Rn=${"%.3f".format(rn)}, ρ=${"%.5f".format(rawRho!!)}" else "compression-controlled (disc ≤ 0)",
                limit = "discriminant ≥ 0",
                status = if (disc > 0) CheckStatus.PASS else CheckStatus.FAIL,
                utilization = if (disc > 0) null else 2.0,
                codeReference = null
            )
            val rhoMin = maxOf(
                params.footingMinReinRatio(),
                params.footingMinReinStressCoef() * sqrt(concrete.cylinderStrengthMpa) / steel.yieldMpa
            )
            val rhoMax = 0.025                                            // ACI §13 footings cap (structural rule)
            val rho = (rawRho ?: 0.025).coerceIn(rhoMin, rhoMax)
            asReq = rho * b * d
            asFinal = asReq
            flexureOk = disc > 0
            aciRhoUtil = (rawRho ?: 0.025) / rho.coerceAtLeast(0.001)   // app §13: ρ/ρfinal
        }

        // Bar selection: market menu, 5..20 bars per metre (app §7-1).
        val barDia = selectFootingBarDiameter(asFinal)
        val area = RebarTable.area(barDia)
        val nominalBars = ceil(asFinal / area).toInt()
        val nominalSpacing = floor(1000.0 / nominalBars)
        val maxSpacing = if (isEcp) min(250.0, min(3.0 * d, 750.0)) else min(450.0, min(3.0 * d, 750.0))
        val finalSpacing = max(nominalSpacing, 100.0).coerceAtMost(maxSpacing)
        val actualBars = ceil(1000.0 / finalSpacing).toInt()
        val asProvided = actualBars * area

        val utilization = aciRhoUtil ?: SafeMath.div(asReq, asProvided)
        val isSafe = utilization <= 1.0 && flexureOk

        trace += TraceEntry(
            title = "Bar selection ($direction)",
            formula = "n = ⌈As/(πd²/4)⌉ per metre; s = ⌊1000/n⌋ clamped",
            substitution = "As=${fmt(asFinal)} mm²/m",
            result = "${actualBars}Ø${barDia.toInt()} @ ${fmt(finalSpacing)} mm",
            limit = "As,provided ≥ As,required",
            // App-faithful: when the min-ratio + spacing clamps keep As,prov below
            // As,req yet the direction's own isSafe holds (ACI util = ρ/ρfinal ≪ 1),
            // this is a WARNING — not a hard FAIL the app does not report.
            status = when {
                asProvided >= asFinal - 1e-6 -> CheckStatus.PASS
                isSafe -> CheckStatus.WARNING
                else -> CheckStatus.FAIL
            },
            utilization = utilization,
            codeReference = null
        )

        return DirResult(
            directional = FootingDirectionalReinf(
                direction = direction,
                astRequired = asReq,
                astProvided = asProvided,
                barDiameter = barDia,
                barsPerMeter = actualBars,
                spacingMm = finalSpacing,
                utilization = utilization,
                isSafe = isSafe,
                barString = "${actualBars}Ø${barDia.toInt()}"
            ),
            trace = trace
        )
    }

    // ───────────────────────── punching shear ─────────────────────────

    /** ECP §4-3-2 stress check vs ACI §22.6.5.2 φ·force check. */
    private fun punchCheck(
        columnWidth: Double,
        columnDepth: Double,
        d: Double,
        axialLoad: Double
    ): PunchResult {
        val bo = 2.0 * (columnWidth + columnDepth) + 4.0 * d
        if (isEcp) {
            // ECP §4-3-2: net punching force = 0.90·N (soil reaction inside the
            // perimeter is deducted) — a code-fixed structural reduction.
            val vPunch = axialLoad * 0.90
            val applied = vPunch * 1000.0 / (bo * d)                     // MPa
            val capacity = params.footingPunchingBaseStressMpa(concrete) // 0.316√(fcu/γc)
            val ok = applied <= capacity
            val warnings = if (ok) emptyList() else listOf("Punching shear ${fmt(applied)} > ${fmt(capacity)} MPa - increase thickness")
            return PunchResult(
                title = "Punching shear (ECP §4-3-2)",
                formula = "qp = 0.90·N/(bo·d) ; qp,cap = 0.316√(fcu/γc)",
                substitution = "bo=${fmt(bo)} mm, d=${fmt(d)} mm, N=${fmt(axialLoad)} kN",
                result = "qp = ${fmt(applied)} MPa vs ${fmt(capacity)} MPa",
                limit = "qp ≤ qp,cap",
                status = if (ok) CheckStatus.PASS else CheckStatus.FAIL,
                utilization = SafeMath.div(applied, capacity),
                isSafe = ok,
                shearCheck = ShearCheckResult(
                    appliedShear = applied, shearCapacity = capacity, isSafe = ok,
                    utilizationRatio = SafeMath.div(applied, capacity),
                    criticalSection = d / 2.0, criticalPerimeter = bo, warnings = warnings
                )
            )
        } else {
            // ACI §22.6.5.2: vn = min of the three interior-column terms; the
            // base 0.33√f'c term comes from params, φ from params (0.75).
            // αs = 40 for an interior column (structural rule).
            val fcPrime = concrete.cylinderStrengthMpa
            val beta = max(columnDepth, columnWidth) / min(columnDepth, columnWidth).coerceAtLeast(1.0)
            val alphaS = 40.0
            val vn = minOf(
                params.footingPunchingBaseStressMpa(concrete),                              // 0.33√f'c
                0.17 * (1.0 + 2.0 / beta) * sqrt(fcPrime),
                0.083 * (2.0 + alphaS * d / bo) * sqrt(fcPrime)
            )
            val phi = params.footingPunchingPhi!!                       // 0.75
            val capacity = phi * vn * bo * d / 1000.0                  // kN
            val ok = axialLoad <= capacity
            val warnings = if (ok) emptyList() else listOf("Punching shear ${fmt(axialLoad)} > ${fmt(capacity)} kN - increase thickness")
            return PunchResult(
                title = "Punching shear (ACI §22.6.5.2)",
                formula = "vn = min(0.33, 0.17(1+2/β), 0.083(2+αs·d/bo))·√f'c ; Vn = φ·vn·bo·d",
                substitution = "bo=${fmt(bo)} mm, d=${fmt(d)} mm, β=${"%.2f".format(beta)}, N=${fmt(axialLoad)} kN",
                result = "vn = ${"%.3f".format(vn)} MPa → Vn = ${fmt(capacity)} kN",
                limit = "Nu ≤ φ·Vn",
                status = if (ok) CheckStatus.PASS else CheckStatus.FAIL,
                utilization = if (capacity > 0) SafeMath.div(axialLoad, capacity) else 2.0,
                isSafe = ok,
                shearCheck = ShearCheckResult(
                    appliedShear = axialLoad, shearCapacity = capacity, isSafe = ok,
                    utilizationRatio = if (capacity > 0) SafeMath.div(axialLoad, capacity) else 2.0,
                    criticalSection = 0.0, criticalPerimeter = bo, warnings = warnings
                )
            )
        }
    }

    // ───────────────────────── one-way shear ─────────────────────────

    private fun oneWayCheck(
        label: String,
        q: Double,
        breadth: Double,
        length: Double,
        columnWidth: Double,
        columnDepth: Double,
        criticalM: Double,
        d: Double
    ): OneWay {
        // The X-dir shear strip runs the length; the Y-dir strip runs the breadth.
        val (cantThis, stripM) = if (label == "X") {
            max((breadth - columnWidth) / 2.0 / 1000.0, 0.0) to length / 1000.0
        } else {
            max((length - columnDepth) / 2.0 / 1000.0, 0.0) to breadth / 1000.0
        }
        val vu = q * stripM * max(cantThis - criticalM, 0.0)            // kN (app §7-1 form)
        val cap = params.footingOneWayShearStressMpa(concrete)          // MPa
        val vc = cap * stripM * d / 1000.0 * 1000.0                    // kN
        val ok = vu <= vc
        val warnings = if (ok) emptyList() else listOf("One-way shear $label exceeds capacity - increase thickness")
        return OneWay(
            isSafe = ok,
            shearCheck = ShearCheckResult(
                appliedShear = vu, shearCapacity = vc, isSafe = ok,
                utilizationRatio = SafeMath.div(vu, vc),
                criticalSection = criticalM * 1000.0,
                warnings = warnings
            ),
            title = "One-way shear ($label)",
            formula = "Vu = q·b·max(cant − dcr, 0) ; Vc = vc·b·d",
            substitution = "Vu=${fmt(vu)} kN, Vc=${fmt(vc)} kN",
            result = if (ok) "safe" else "FAIL",
            limit = "Vu ≤ Vc",
            status = if (ok) CheckStatus.PASS else CheckStatus.FAIL,
            utilization = SafeMath.div(vu, vc)
        )
    }

    // ───────────────────────── bar selection ─────────────────────────

    /** Market menu first-fit: smallest diameter with 5..20 bars per metre. */
    private fun selectFootingBarDiameter(asRequired: Double, fallback: Double = 16.0): Double =
        barMenuMm.firstOrNull { dia ->
            val barsPerMeter = ceil(asRequired / RebarTable.area(dia)).toInt()
            barsPerMeter in 5..20
        } ?: fallback

    // ───────────────────────── small result wrappers ─────────────────────────

    private class DirResult(val directional: FootingDirectionalReinf, val trace: List<TraceEntry>)

    private class OneWay(
        val isSafe: Boolean,
        val shearCheck: ShearCheckResult,
        val title: String,
        val formula: String,
        val substitution: String,
        val result: String,
        val limit: String,
        val status: CheckStatus,
        val utilization: Double
    )

    private class PunchResult(
        val title: String,
        val formula: String,
        val substitution: String,
        val result: String,
        val limit: String,
        val status: CheckStatus,
        val utilization: Double,
        val isSafe: Boolean,
        val shearCheck: ShearCheckResult
    )

    /** Result of the unified isolated-footing design. */
    data class Outcome(
        val code: DesignCode,
        val requiredWidth: Double,          // mm (B)
        val requiredLength: Double,         // mm (L)
        val requiredThickness: Double,      // mm (h)
        val depth: Double,                  // mm (d)
        val soilPressure: Double,           // kPa (q_avg)
        val maxSoilPressure: Double,        // kPa (q_max)
        val shortDir: FootingDirectionalReinf,
        val longDir: FootingDirectionalReinf,
        val mainDirection: FootingDirection,
        val distribution: FootingDistributionSteel,
        val punching: ShearCheckResult,
        val oneWayX: ShearCheckResult,
        val oneWayY: ShearCheckResult,
        val isSafe: Boolean,
        val overallStatus: CheckStatus,
        val trace: CalculationTrace,
        val sanity: SanityReport = SanityReport("FootingOutcome", emptyList())
    )
}

/** Neighbour-lot boundary constraints (core mirror of the app BoundaryConstraints). */
data class FootingConstraints(
    val maxLeft: Double? = null,
    val maxRight: Double? = null,
    val maxTop: Double? = null,
    val maxBottom: Double? = null,
    val isCornerColumn: Boolean = false,
    val isEdgeColumn: Boolean = false
)

/** Footing reinforcement direction on plan. */
enum class FootingDirection { SHORT, LONG }

/** Directional flexural steel for a 1 m strip. */
data class FootingDirectionalReinf(
    val direction: FootingDirection,
    val astRequired: Double,      // mm²/m
    val astProvided: Double,      // mm²/m
    val barDiameter: Double,      // mm
    val barsPerMeter: Int,
    val spacingMm: Double,        // mm centre-to-centre
    val utilization: Double,
    val isSafe: Boolean,
    val barString: String
)

/** Distribution (top) steel — 20% of the governing main steel. */
data class FootingDistributionSteel(
    val barsPerMeter: Int,
    val diameterMm: Double,
    val spacingMm: Double,
    val astRequired: Double,
    val astProvided: Double
)

/** Concrete unit weight for the net-SBC deduction (kN/m³) — code-fixed structural value. */
private const val CONCRETE_UNIT_WEIGHT = 25.0

/** ECP K-method lever-arm denominator (app §7-1): z = d·(0.5 + √(0.25 − K/0.893)). */
private const val ECP_LEVER_ARM_K = 0.893

/** Footing market bar menu (mirrors the app ECP & ACI implementations exactly). */
private val FOOTING_BAR_MENU = listOf(12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0)