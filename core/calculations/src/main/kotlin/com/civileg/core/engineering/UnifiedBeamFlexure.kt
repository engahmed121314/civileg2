package com.civileg.core.engineering

import com.civileg.core.math.SafeMath
import kotlin.math.sqrt

/**
 * Unified singly-reinforced rectangular flexure design (STEP 5 consolidation
 * pilot, spec §20/§81). ONE skeleton — validation, trace, min/max steel, bar
 * selection — with the family stress-block solve injected via [ConcreteCodeParams].
 *
 * Replaces the four historical parallel stacks for this check; golden-gated
 * against both legacy benchmark sets.
 */
class UnifiedBeamFlexure(
    private val params: ConcreteCodeParams,
    private val concrete: ConcreteMaterial,
    private val steel: SteelMaterial
) {

    fun design(b: Double, d: Double, h: Double, muKnm: Double, loadCombo: FactoredCombination? = null, puN: Double = 0.0): Outcome {
        require(b > 0 && d > 0 && h >= d) { "flexure: invalid section b=$b d=$d h=$h" }
        SafeMath.requirePositive(muKnm, "Mu")
        val trace = CalculationTrace()
        // Minimum-moment gate (accidental eccentricity): Mu,min = Pu·e_min, e_min = 15 + 0.03·h [mm].
        // Default puN = 0 keeps pure-beam behaviour unchanged; only axial members activate it.
        val eMinMm = 15.0 + 0.03 * h
        val muMinKnm = (puN * eMinMm) / 1e6
        val muEffKnm = maxOf(muKnm, muMinKnm)
        val minMomentGoverns = muEffKnm > muKnm + 1e-9
        val muNmm = muEffKnm * 1e6
        val ref = "${params.reference}" + (loadCombo?.let { " · ${it.name} (${it.reference})" } ?: "")

        val asRequired: Double
        var governingNote: String? = null

        when (params.family) {
            CodeFamily.ECP_203 -> {
                val k = SafeMath.div(muNmm, concrete.fcuMpa * b * d * d)
                val kBal = (params as Ecp203Params).kBalanced(steel)
                if (k > kBal) error("UnifiedBeamFlexure: K=$k > K_bal=${"%.4f".format(kBal)} — route to doubly-reinforced path")

                // ECP K-method lever arm; 0.893 = γc/(2×0.67)
                val z = d * (0.5 + sqrt(0.25 - k / 0.893))
                val fsd = steel.yieldMpa / params.gammas!!.gammaS
                asRequired = SafeMath.div(muNmm, fsd * z)

                trace.add(
                    title = "Flexural K factor",
                    formula = "K = Mu / (fcu·b·d²)",
                    substitution = "K = ${f(muNmm)}/(${concrete.fcuMpa}·$b·$d²) = ${f(k)}",
                    result = f(k),
                    limit = "≤ K_bal = ${f(kBal)}",
                    status = CheckStatus.PASS,
                    utilization = k / kBal,
                    codeReference = ref
                )
                trace.add(
                    title = "Required tension steel",
                    formula = "z = d(0.5+√(0.25−K/0.893)) ; As = Mu/(fsd·z)",
                    substitution = "z = ${f(z)} mm ; fsd = ${f(fsd)} MPa",
                    result = "${f(asRequired)} mm²",
                    limit = "≥ As,min = ${f(params.minFlexuralSteelRatio(concrete, steel) * b * d)} mm²",
                    status = CheckStatus.PASS,
                    codeReference = ref
                )
            }

            CodeFamily.ACI_318, CodeFamily.SBC_304 -> {
                val phi = params.phiFlexure!!
                val fc = concrete.cylinderStrengthMpa
                val rn = SafeMath.div(muNmm, phi * b * d * d)
                val m = SafeMath.div(steel.yieldMpa, 0.85 * fc)
                val discriminant = 1.0 - 2.0 * m * rn / steel.yieldMpa
                if (discriminant < 0.0) error("UnifiedBeamFlexure: Rn=$rn beyond section limit — route to doubly path")
                val rho = (1.0 - sqrt(discriminant)) / m
                asRequired = rho * b * d

                trace.add(
                    title = "Resistance factor Rn",
                    formula = "Rn = Mu / (φ·b·d²)",
                    substitution = "Rn = ${f(muNmm)}/($phi·$b·$d²) = ${f(rn)}",
                    result = f(rn),
                    limit = "ρ ≤ ρmax(tc) = ${f(params.maxFlexuralSteelRatio(concrete, steel))}",
                    status = if (rho <= params.maxFlexuralSteelRatio(concrete, steel)) CheckStatus.PASS else CheckStatus.FAIL,
                    utilization = SafeMath.div(rho, params.maxFlexuralSteelRatio(concrete, steel)),
                    codeReference = ref
                )
                trace.add(
                    title = "Required tension steel",
                    formula = "ρ = (1−√(1−2mRn/fy))/m ; As = ρ·b·d",
                    substitution = "m = ${f(m)} ; ρ = ${f(rho)}",
                    result = "${f(asRequired)} mm²",
                    limit = "≥ As,min = ${f(params.minFlexuralSteelRatio(concrete, steel) * b * d)} mm²",
                    status = CheckStatus.PASS,
                    codeReference = ref
                )
            }
        }

        // Minimum-moment gate (accidental eccentricity) — documented after the family solve
        trace.add(
            title = "Minimum-moment gate (accidental eccentricity)",
            formula = "e_min = 15 + 0.03·h ; Mu,min = Pu·e_min",
            substitution = "e_min = ${f(eMinMm)} mm ; Mu,min = ${f(muMinKnm)} kN·m ; governing Mu = ${f(muEffKnm)} kN·m",
            result = "${f(muEffKnm)} kN·m${if (minMomentGoverns) " (Mu,min governs)" else ""}",
            limit = "≥ applied Mu = ${f(muKnm)} kN·m",
            status = CheckStatus.PASS,
            codeReference = params.reference
        )

        // Shared minimum-steel gate (family-injected formula)
        val minAs = params.minFlexuralSteelRatio(concrete, steel) * b * d
        val finalAs = maxOf(asRequired, minAs)
        if (asRequired < minAs) governingNote = "minimum steel governs"
        if (minMomentGoverns) governingNote = (governingNote?.let { "$it; " } ?: "") + "minimum moment Mu,min governs"
        val minStatus = if (governingNote == null) CheckStatus.PASS else CheckStatus.WARNING
        trace.add(
            title = "Minimum reinforcement gate",
            formula = params.family.let { fam ->
                if (fam == CodeFamily.ECP_203) "As,min = max(0.25√fcu/fy, 0.0013)·b·d"
                else "As,min = max(0.25√f'c/fy, 1.4/fy)·b·d"
            },
            substitution = "${f(minAs)} mm² vs required ${f(asRequired)} mm²",
            result = "${f(finalAs)} mm²${governingNote?.let { " ($it)" } ?: ""}",
            limit = "governing value adopted",
            status = minStatus,
            codeReference = params.reference
        )

        // Shared bar selection (family bar menu injected)
        val (n, dia) = RebarTable.select(finalAs, diameters = params.barMenuMm)
        val asProvided = n * RebarTable.area(dia)
        trace.add(
            title = "Bar selection",
            formula = "n·πØ²/4 ≥ As,req",
            substitution = "${n}Ø${dia.toInt()} → ${f(asProvided)} mm²",
            result = f(asProvided),
            limit = "≥ ${f(finalAs)} mm²",
            status = if (asProvided >= finalAs - 1e-6) CheckStatus.PASS else CheckStatus.FAIL,
            utilization = SafeMath.div(finalAs, asProvided)
        )

        return Outcome(asRequiredMm2 = finalAs, asProvidedMm2 = asProvided, bars = "${n}Ø${dia.toInt()}", governingNote = governingNote, trace = trace)
    }

    data class Outcome(
        val asRequiredMm2: Double,
        val asProvidedMm2: Double,
        val bars: String,
        val governingNote: String?,
        val trace: CalculationTrace
    ) {
        /** Legacy-engine-compatible raw required area BEFORE the minimum-steel gate. */
        @Deprecated("use asRequiredMm2 (post-gate)") val preGateRequired = asRequiredMm2
    }

    private fun f(v: Double) = String.format("%.3f", v).trimEnd('0').trimEnd('.')
}
