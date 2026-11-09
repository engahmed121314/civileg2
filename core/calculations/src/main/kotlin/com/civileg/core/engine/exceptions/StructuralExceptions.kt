package com.civileg.core.engine.exceptions

/**
 * Base class for all structural engineering constraint violations.
 */
abstract class StructuralEngineeringException(
    message: String,
    val codeReference: String,
    cause: Throwable? = null
) : RuntimeException("[$codeReference] $message", cause)

/**
 * Raised when the tension reinforcement exceeds the code maximum limit (μ > μ_max or failure of ductility).
 * ECP 203 Cl. 4-2-1-2 / ACI 318 Cl. 9.3.3.1 / SBC 304 Cl. 9.3.3.1
 */
class OverReinforcedException(
    val muActual: Double,
    val muMax: Double,
    codeReference: String = "ECP 203 Cl. 4-2-1-2",
    message: String = "Section is over-reinforced: actual μ = ${String.format("%.4f", muActual)} > μ_max = ${String.format("%.4f", muMax)}. Brittleness hazard, section must be enlarged or doubly-reinforced."
) : StructuralEngineeringException(message, codeReference)

/**
 * Raised when the clear spacing between rebar is less than code minimum, leading to concrete honeycombing.
 * ECP 203 Cl. 4-2-5-1 / ACI 318 Cl. 25.2.1 / SBC 304 Cl. 25.2.1
 */
class RebarCongestionException(
    val clearSpacingMm: Double,
    val minAllowedSpacingMm: Double,
    codeReference: String = "ECP 203 Cl. 4-2-5-1",
    message: String = "Rebar congestion detected: clear spacing = ${String.format("%.1f", clearSpacingMm)} mm < minimum required = ${String.format("%.1f", minAllowedSpacingMm)} mm. Use larger diameter, bundled bars, or multiple layers."
) : StructuralEngineeringException(message, codeReference)

/**
 * Raised when the nominal shear stress exceeds maximum legal limit (diagonal compression failure of the web).
 * ECP 203 Cl. 4-2-3-2 / ACI 318 Cl. 22.5.1.2 / SBC 304 Cl. 22.5.1.2
 */
class SectionUnsafeShearException(
    val quActualMpa: Double,
    val quMaxMpa: Double,
    codeReference: String = "ECP 203 Cl. 4-2-3-2",
    message: String = "Shear stress qu = ${String.format("%.2f", quActualMpa)} MPa exceeds maximum permissible limit qu_max = ${String.format("%.2f", quMaxMpa)} MPa. Increasing stirrups cannot help — web crushing hazard, cross-section (b×d) or f_cu must be increased."
) : StructuralEngineeringException(message, codeReference)
