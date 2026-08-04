package com.civileg.app.utils

import com.civileg.app.utils.CalculatorEngine.*

/**
 * Utility class to validate the consistency and correctness of engineering calculations.
 * Ensures that the reported "isSafe" status matches the actual numerical results.
 */
object CalculationValidator {

    data class ValidationReport(
        val isConsistent: Boolean,
        val errors: List<String>,
        val warnings: List<String>
    )

    /**
     * Validates a Beam calculation result.
     */
    fun validateBeam(result: BeamResult): ValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. Capacity vs Demand
        if (result.appliedMoment > result.momentCapacity + 0.01) {
            if (result.isSafe) errors.add("Inconsistency: Beam marked SAFE but Mu (${result.appliedMoment}) > Mn (${result.momentCapacity})")
        }

        if (result.appliedShear > result.shearCapacity + 0.01) {
             // Future: Add specific shear consistency check if needed
        }

        // 2. Deflection
        if (result.deflection > result.allowableDeflection + 0.1) {
            if (result.isSafe) errors.add("Inconsistency: Beam marked SAFE but Deflection (${result.deflection}) > Allowable (${result.allowableDeflection})")
        }

        // 3. Reinforcement Limits (General)
        if (result.steelRatio < 0.1) {
            warnings.add("Warning: Reinforcement ratio is very low (${String.format(java.util.Locale.US, "%.3f", result.steelRatio)}%)")
        }
        if (result.steelRatio > 4.0) {
            warnings.add("Warning: High reinforcement ratio (${String.format(java.util.Locale.US, "%.2f", result.steelRatio)}%). Section may be over-reinforced.")
        }

        return ValidationReport(errors.isEmpty(), errors, warnings)
    }

    /**
     * Validates a Column calculation result.
     */
    fun validateColumn(result: ColumnResult): ValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (result.appliedAxial > result.axialCapacity + 0.1) {
            if (result.isSafe) errors.add("Inconsistency: Column marked SAFE but Pu (${result.appliedAxial}) > Pn (${result.axialCapacity})")
        }

        val rho = result.reinforcementRatio
        if (rho < 0.8 && result.code == DesignCode.EGYPTIAN) {
            warnings.add("Warning: Reinforcement ratio ($rho%) is below ECP minimum (0.8%)")
        }

        return ValidationReport(errors.isEmpty(), errors, warnings)
    }

    /**
     * Validates a Slab calculation result.
     */
    fun validateSlab(result: SlabResult): ValidationReport {
        val errors = mutableListOf<String>()
        
        if (result.thickness < result.minThickness - 0.1) {
             if (result.isSafe) errors.add("Inconsistency: Slab marked SAFE but Ts (${result.thickness}) < MinTs (${result.minThickness})")
        }

        if (result.type == SlabType.FLAT && !result.punchingSafe) {
            if (result.isSafe) errors.add("Inconsistency: Flat slab marked SAFE but Punching Shear check FAILED")
        }

        return ValidationReport(errors.isEmpty(), errors, emptyList())
    }

    /**
     * General result validation (dispatches to specific types)
     */
    fun validate(result: Any): ValidationReport {
        return when (result) {
            is BeamResult -> validateBeam(result)
            is ColumnResult -> validateColumn(result)
            is SlabResult -> validateSlab(result)
            else -> ValidationReport(true, emptyList(), emptyList())
        }
    }
}
