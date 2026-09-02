package com.civileg.core.sanity

/**
 * Fluent collector of [SanityFinding]s. Every method appends a finding when its
 * condition is violated and returns `this` for chaining. Crucially, a check NEVER
 * throws — invalid inputs become findings, so the sanity gate can run on the
 * output of any (possibly buggy) calculation without itself crashing.
 */
class SanityContext(private val source: String) {
    private val findings = mutableListOf<SanityFinding>()

    fun finite(name: String, value: Double, code: String = "SAN-NAN"): SanityContext {
        if (!value.isFinite()) add(code, SanitySeverity.ERROR, "$name is not finite (NaN/Inf) = $value", value)
        return this
    }

    fun nonNegative(name: String, value: Double, code: String = "SAN-NEG"): SanityContext {
        if (value < 0.0) add(code, SanitySeverity.ERROR, "$name must be ≥ 0, got $value", value)
        return this
    }

    fun inRange(name: String, value: Double, min: Double, max: Double, code: String = "SAN-RANGE", codeReference: String? = null): SanityContext {
        if (value < min || value > max)
            add(code, SanitySeverity.WARNING, "$name = $value outside plausible range [$min, $max]", value, max, codeReference)
        return this
    }

    fun utilization(name: String, value: Double, limit: Double = 1.0, code: String = "SAN-UTIL", codeReference: String? = null): SanityContext {
        if (value > limit)
            add(code, SanitySeverity.ERROR, "$name utilization = $value exceeds limit $limit", value, limit, codeReference)
        return this
    }

    fun capacityVsDemand(name: String, demand: Double, capacity: Double, code: String = "SAN-CAP", codeReference: String? = null): SanityContext {
        if (demand > capacity)
            add(code, SanitySeverity.ERROR, "$name demand = $demand exceeds capacity = $capacity", demand, capacity, codeReference)
        return this
    }

    fun reinforcementRatio(name: String, rho: Double, rhoMin: Double, rhoMax: Double, codeReference: String? = null): SanityContext {
        if (rho < rhoMin) add("SAN-RHO-MIN", SanitySeverity.WARNING, "$name ρ = $rho below code minimum $rhoMin", rho, rhoMin, codeReference)
        if (rho > rhoMax) add("SAN-RHO-MAX", SanitySeverity.ERROR, "$name ρ = $rho above code maximum $rhoMax (over-reinforced / ductility risk)", rho, rhoMax, codeReference)
        return this
    }

    fun info(message: String, code: String = "SAN-INFO"): SanityContext { add(code, SanitySeverity.INFO, message); return this }
    fun warn(message: String, code: String): SanityContext { add(code, SanitySeverity.WARNING, message); return this }
    fun error(message: String, code: String): SanityContext { add(code, SanitySeverity.ERROR, message); return this }

    private fun add(code: String, severity: SanitySeverity, message: String, value: Double? = null, limit: Double? = null, codeReference: String? = null) {
        findings += SanityFinding(code, severity, message, value, limit, codeReference)
    }

    fun build(): SanityReport = SanityReport(source, findings.toList())
}
