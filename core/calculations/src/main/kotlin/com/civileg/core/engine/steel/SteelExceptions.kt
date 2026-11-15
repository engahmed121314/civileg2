package com.civileg.core.engine.steel

import com.civileg.core.engine.exceptions.StructuralEngineeringException

/**
 * استثناء أساسي لأخطاء التصميم المعدني.
 */
abstract class SteelEngineeringException(
    message: String,
    codeReference: String = "AISC 360-16",
    cause: Throwable? = null
) : StructuralEngineeringException(message, codeReference, cause)

/**
 * Raised when section parameters are invalid (e.g., negative section modulus, zero radius of gyration).
 */
class InvalidSectionException(
    val parameter: String,
    val value: Double,
    private val codeRef: String = "AISC 360-16 Table B4.1",
    message: String = "Invalid section parameter '$parameter' = $value. Must be positive."
) : SteelEngineeringException(message, codeRef)

/**
 * Raised when the unbraced length exceeds the limit for lateral-torsional buckling (Lr),
 * requiring slender-column or lateral-bracing design.
 */
class ExcessiveUnbracedLengthException(
    val Lb: Double,
    val Lr: Double,
    private val codeRef: String = "AISC 360-16 Cl. F2",
    message: String = "Unbraced length Lb = ${String.format("%.1f", Lb)} mm exceeds limiting length Lr = ${String.format("%.1f", Lr)} mm. Provide lateral bracing or reduce unbraced length."
) : SteelEngineeringException(message, codeRef)

/**
 * Raised when bolt connection capacity is insufficient for the applied load.
 */
class InsufficientBoltCapacityException(
    val appliedLoadN: Double,
    val designCapacityN: Double,
    private val codeRef: String = "AISC 360-16 Chapter J",
    message: String = "Applied load ${String.format("%.1f", appliedLoadN)} N exceeds bolt design capacity ${String.format("%.1f", designCapacityN)} N. Increase bolt diameter, grade, or number of bolts."
) : SteelEngineeringException(message, codeRef)
