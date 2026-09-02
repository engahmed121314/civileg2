package com.civileg.core.engineering

import com.civileg.core.calculations.entities.DesignCode

/**
 * Code Version Control registry (Roadmap §1.2).
 *
 * The SINGLE place that binds a [CodeVersion] to the [ConcreteCodeParams]
 * driving the unified engines. Today one edition is registered per family
 * (the active 2020 / 2019 / 2018 editions). Additional editions can be added
 * here — with their own parameter objects — without touching any engine or
 * facade. The engines remain edition-agnostic; they only ever see a
 * [ConcreteCodeParams].
 *
 * SBC 304 is ACI-based, so its active edition resolves to the ACI parameter
 * set (consistent with the existing SBC routing in the app layer).
 */
object CodeVersionRegistry {

    private val editions: List<CodeVersion> = listOf(
        CodeVersion(DesignCode.ECP, "203-2020", "ECP 203-2020", isDefault = true),
        CodeVersion(DesignCode.ACI, "318-19", "ACI 318-19", isDefault = true),
        CodeVersion(DesignCode.SBC, "304-2018", "SBC 304-2018", isDefault = true)
    )

    private val paramsByKey: Map<String, ConcreteCodeParams> = mapOf(
        "ECP:203-2020" to Ecp203Params,
        "ACI:318-19" to Aci318Params,
        "SBC:304-2018" to Aci318Params
    )

    /** All registered editions, grouped by family on demand. */
    fun allVersions(): List<CodeVersion> = editions

    /** Editions registered for a given code family. */
    fun versionsFor(code: DesignCode): List<CodeVersion> = editions.filter { it.code == code }

    /** The active (default) edition for a family. */
    fun defaultFor(code: DesignCode): CodeVersion =
        versionsFor(code).firstOrNull { it.isDefault } ?: versionsFor(code).first()

    /**
     * Resolve the parameter set for a code family + edition.
     * @throws IllegalArgumentException if no parameter set is registered
     */
    fun resolve(code: DesignCode, edition: String = defaultFor(code).edition): ConcreteCodeParams {
        val key = "${code.name}:$edition"
        return paramsByKey[key]
            ?: throw IllegalArgumentException("No ConcreteCodeParams registered for $key")
    }

    /** Resolve directly from a [CodeVersion.key]. */
    fun resolveKey(key: String): ConcreteCodeParams? = paramsByKey[key]
}

/** Convenience: editions available for a code family. */
val DesignCode.versions: List<CodeVersion> get() = CodeVersionRegistry.versionsFor(this)

/** Convenience: the active edition for a code family. */
val DesignCode.defaultVersion: CodeVersion get() = CodeVersionRegistry.defaultFor(this)
