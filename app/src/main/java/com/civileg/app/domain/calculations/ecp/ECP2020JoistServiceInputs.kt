package com.civileg.app.domain.calculations.ecp

/** Additive SLS preparation contract. Not persisted or connected to acceptance.
 * Service moments are independent analysis results in kN.m per rib. The
 * sustained load fraction in the legacy form is NOT substituted for them:
 * cracked stiffness can change with load. No ultimate demand is accepted here.
 */
data class ECP2020JoistServiceInputs(
    val demands: ServiceDemands? = null,
    val section: SectionAnalysis? = null,
    val history: LoadHistory? = null,
    val exposureClass: ExposureClass? = null
) {
    enum class ExposureClass { I, II, III, IV }
    data class ServiceDemands(
        val deadMomentKnMPerRib: Double?,
        val deadPlusLiveMomentKnMPerRib: Double?,
        val sustainedMomentKnMPerRib: Double?
    )
    /** These are analysis outputs, not free geometry guesses. Providing a
     * provenance label records a lead for review; it does not verify the model.
     * A zero compression area is explicit evidence to review, never a default.
     * No compression-ratio denominator is chosen by this contract.
     */
    data class SectionAnalysis(
        val grossInertiaMm4: Double?,
        val crackedInertiaMm4: Double?,
        val extremeTensionFibreMm: Double?,
        val tensionSteelAreaMm2: Double?,
        val compressionSteelAreaMm2: Double?,
        val compressionSteelCentroidFromTopMm: Double?,
        val analysisReference: String?
    )
    data class LoadHistory(
        val sustainedDurationMonths: Double?,
        val attachments: Attachments?,
        val installationMonthsAfterLoading: Double?
    )
    enum class Attachments { NONE, DEFLECTION_SENSITIVE, NOT_DEFLECTION_SENSITIVE }
}

/** Readiness is not compliance. Even a complete contract stays blocked until
 * a verified physical analysis and the applicable ECP limits are implemented.
 * Missing facts belong to the project owner; code interpretation and analysis
 * implementation belong to engineering review, not a user preference vote.
 */
object ECP2020JoistServiceReadiness {
    enum class State { MISSING_INPUTS, INVALID_INPUTS, ENGINEERING_REVIEW_REQUIRED }
    enum class Owner { PROJECT_INPUT, ENGINEERING_SOURCE, ENGINEERING_IMPLEMENTATION }
    enum class Check { IMMEDIATE_DEFLECTION, LONG_TERM_DEFLECTION, ATTACHMENT_INCREMENT, CRACK_CONTROL }
    data class Issue(val key: String, val owner: Owner, val detail: String)
    data class Result(val state: State, val issues: List<Issue>) {
        val acceptanceAvailable: Boolean get() = false
        val constructionReady: Boolean get() = false
        val checks: Map<Check, State> get() = Check.entries.associateWith { state }
    }

    fun review(input: ECP2020JoistServiceInputs?): Result {
        val missing = mutableListOf<Issue>()
        val invalid = mutableListOf<Issue>()
        fun need(key: String, detail: String) { missing += Issue(key, Owner.PROJECT_INPUT, detail) }
        fun number(key: String, value: Double?, zeroAllowed: Boolean = true) {
            if (value == null) need(key, "Explicit $key is required")
            else if (!value.isFinite() || value < 0 || (!zeroAllowed && value == 0.0))
                invalid += Issue(key, Owner.PROJECT_INPUT, "Invalid $key")
        }
        val demands = input?.demands
        number("dead service moment", demands?.deadMomentKnMPerRib)
        number("dead plus live service moment", demands?.deadPlusLiveMomentKnMPerRib)
        number("sustained service moment", demands?.sustainedMomentKnMPerRib)
        // Bounded positive-moment gravity case only; no subtraction to invent
        // a live-only cracked response and no ultimate/service conversion.
        if (demands != null) {
            val total = demands.deadPlusLiveMomentKnMPerRib
            val dead = demands.deadMomentKnMPerRib
            val sustained = demands.sustainedMomentKnMPerRib
            if (total != null && total.isFinite() &&
                ((dead != null && dead.isFinite() && dead > total) ||
                 (sustained != null && sustained.isFinite() && sustained > total)))
                invalid += Issue("service demand envelope", Owner.PROJECT_INPUT,
                    "Dead and sustained positive moments cannot exceed the total for this bounded gravity case")
        }
        val section = input?.section
        number("gross inertia", section?.grossInertiaMm4, false)
        number("cracked inertia", section?.crackedInertiaMm4, false)
        number("tension fibre distance", section?.extremeTensionFibreMm, false)
        number("tension steel area", section?.tensionSteelAreaMm2, false)
        number("compression steel area", section?.compressionSteelAreaMm2)
        if (section?.compressionSteelAreaMm2?.let { it.isFinite() && it > 0 } == true)
            number("compression steel centroid", section.compressionSteelCentroidFromTopMm, false)
        else section?.compressionSteelCentroidFromTopMm?.let { number("compression steel centroid", it, false) }
        if (section?.analysisReference.isNullOrBlank()) need("analysis reference", "Section analysis provenance is required and must then be verified")
        if (section?.grossInertiaMm4 != null && section.crackedInertiaMm4 != null &&
            section.crackedInertiaMm4 > section.grossInertiaMm4)
            invalid += Issue("inertia bounds", Owner.PROJECT_INPUT, "Cracked inertia exceeds gross inertia")
        val history = input?.history
        number("sustained duration", history?.sustainedDurationMonths, false)
        if (history?.attachments == null) need("attachments", "Explicit absence or sensitivity of attached elements is required")
        if (history?.attachments != null && history.attachments != ECP2020JoistServiceInputs.Attachments.NONE)
            number("attachment installation time", history.installationMonthsAfterLoading)
        else history?.installationMonthsAfterLoading?.let { number("attachment installation time", it) }
        if (history?.installationMonthsAfterLoading != null && history.sustainedDurationMonths != null &&
            history.installationMonthsAfterLoading > history.sustainedDurationMonths)
            invalid += Issue("installation time range", Owner.PROJECT_INPUT, "Installation occurs after the evaluation duration")
        if (input?.exposureClass == null) need("ECP exposure class", "Exposure classification I to IV must be justified against Table4-11; legacy labels are not auto-mapped")
        val engineering = listOf(
            Issue("compression ratio definition", Owner.ENGINEERING_SOURCE, "Verify Eq4-73 compression-ratio denominator and T-section applicability"),
            Issue("physical section analysis", Owner.ENGINEERING_IMPLEMENTATION, "Verify transformed cracked T-section geometry, neutral axis, steel response and each service-load stiffness"),
            Issue("deflection evaluation", Owner.ENGINEERING_IMPLEMENTATION, "Implement immediate, sustained and attachment-time response and applicable limits without treating load fractions as deflection fractions"),
            Issue("crack acceptance", Owner.ENGINEERING_IMPLEMENTATION, "Verify service steel stress and Tables4-14/15 or direct crack-width route; no topping-compression flange waiver"),
            Issue("optional deflection waiver", Owner.ENGINEERING_SOURCE, "If pursuing a waiver, resolve6m boundary, Table4-10b reference and T-factor applicability; direct analysis does not assume a waiver")
        )
        return Result(when {
            invalid.isNotEmpty() -> State.INVALID_INPUTS
            missing.isNotEmpty() -> State.MISSING_INPUTS
            else -> State.ENGINEERING_REVIEW_REQUIRED
        }, (invalid + missing + engineering).toList())
    }
}
