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
     * Validates a Beam calculation result with high precision.
     */
    fun validateBeam(result: BeamResult): ValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. Capacity vs Demand Logic
        if (result.appliedMoment > result.momentCapacity + 0.05) {
            if (result.isSafe) errors.add("CRITICAL: Beam marked SAFE but Mu (${result.appliedMoment}) > Capacity Mn (${result.momentCapacity})")
        }

        // 2. Deflection logic for professional aesthetics
        if (result.deflection > result.allowableDeflection + 0.01) {
            if (result.isSafe) errors.add("CRITICAL: Deflection limit exceeded (${result.deflection} > ${result.allowableDeflection}) but element is marked SAFE")
        }

        // 3. Reinforcement Limits (General)
        val rho = result.steelRatio
        if (rho < 0.1) warnings.add("Logic: Steel ratio is very low ($rho%). Ensure minimum shrinkage reinforcement is met.")
        if (rho > 4.0) warnings.add("Logic: Steel ratio is high ($rho%). Congestion might occur during casting.")
        
        // 4. Professional Audit: Utilization > 90%
        if (result.utilizationRatio > 0.90 && result.isSafe) {
            warnings.add("Audit: Design is highly efficient (90%+). Verify site execution tolerances.")
        }

        return ValidationReport(errors.isEmpty(), errors, warnings)
    }

    /**
     * Validates a Column calculation result with code-specific rules.
     */
    fun validateColumn(result: ColumnResult): ValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. Axial Capacity Consistency
        if (result.appliedAxial > result.axialCapacity + 1.0) {
            if (result.isSafe) errors.add("CRITICAL: Column marked SAFE but Pu (${result.appliedAxial}) > Capacity Pn (${result.axialCapacity})")
        }

        // 2. Reinforcement Ratio Audit
        val rho = result.reinforcementRatio
        val minRho = if (result.code == DesignCode.EGYPTIAN) 0.8 else 1.0
        if (rho < minRho - 0.01) {
            warnings.add("Logic: Reinforcement ratio ($rho%) is below code minimum ($minRho%)")
        }
        if (rho > 8.0) {
            errors.add("CRITICAL: Reinforcement ratio ($rho%) exceeds maximum allowable (8%)")
        }

        return ValidationReport(errors.isEmpty(), errors, warnings)
    }

    /**
     * Validates a Slab calculation result.
     */
    fun validateSlab(result: SlabResult): ValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        if (result.thickness < result.minThickness - 0.1) {
             if (result.isSafe) errors.add("Inconsistency: Slab marked SAFE but Ts (${result.thickness}) < MinTs (${result.minThickness})")
        }

        if (result.type == SlabType.FLAT && !result.punchingSafe) {
            if (result.isSafe) errors.add("Inconsistency: Flat slab marked SAFE but Punching Shear check FAILED")
        }

        // Logic check: Reinforcement spacing
        if (result.reinforcementMain.spacing > 250.0) {
            warnings.add("Logic Warning: Main reinforcement spacing (${result.reinforcementMain.spacing}mm) exceeds recommended limit for crack control (200-250mm)")
        }

        return ValidationReport(errors.isEmpty(), errors, warnings)
    }

    /**
     * Validates a Tank (Water Structure) calculation result.
     */
    fun validateTank(result: TankResult): ValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (result.wallThickness < 200.0) {
            warnings.add("Logic Warning: Wall thickness (${result.wallThickness}mm) is less than recommended 200mm for water-tightness.")
        }
        
        // Check structural weight vs uplift
        if (result.type == TankType.UNDERGROUND) {
            val upliftCheck = result.suggestions.find { it.contains("Uplift") }
            if (upliftCheck != null && upliftCheck.contains("Unsafe")) {
                errors.add("CRITICAL: Underground tank fails uplift check. Needs more weight or mechanical anchors.")
            }
        }

        if (result.utilizationRatio > 0.95 && result.isSafe) {
            warnings.add("Warning: Section is at 95%+ capacity. Consider increasing thickness for better crack control in water structures.")
        }

        return ValidationReport(errors.isEmpty(), errors, warnings)
    }

    /**
     * Validates a Retaining Wall calculation result.
     */
    fun validateRetainingWall(result: RetainingWallResult): ValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (result.factorOfSafetyOverturning < 1.5) {
            if (result.isSafe) errors.add("Inconsistency: Wall marked SAFE but FS Overturning (${String.format(java.util.Locale.US, "%.2f", result.factorOfSafetyOverturning)}) < 1.5")
        }

        if (result.factorOfSafetySliding < 1.5) {
            if (result.isSafe) errors.add("Inconsistency: Wall marked SAFE but FS Sliding (${String.format(java.util.Locale.US, "%.2f", result.factorOfSafetySliding)}) < 1.5")
        }

        if (result.bearingFS < 2.0) {
            warnings.add("Logic Warning: Bearing capacity safety factor is low (${String.format(java.util.Locale.US, "%.2f", result.bearingFS)}). Recommended minimum is 2.0-3.0.")
        }

        return ValidationReport(errors.isEmpty(), errors, warnings)
    }

    /**
     * Inspects if Dead Load is reasonably accounted for based on member dimensions.
     * Addressing "تفقد الميت" logic with higher precision.
     */
    fun inspectDeadLoadConsistency(memberType: String, dimensions: Map<String, Double>, reportedDL: Double): ValidationReport {
        val warnings = mutableListOf<String>()
        
        val selfWeight = when(memberType) {
            "BEAM" -> {
                val b = dimensions["width"] ?: 250.0
                val h = dimensions["depth"] ?: 600.0
                val L = dimensions["span"] ?: 1.0
                (b/1000.0) * (h/1000.0) * 25.0 // kN/m
            }
            "COLUMN" -> {
                val b = dimensions["width"] ?: 300.0
                val d = dimensions["depth"] ?: 300.0
                val h = dimensions["height"] ?: 3.0
                (b/1000.0) * (d/1000.0) * h * 25.0 // kN
            }
            "SLAB" -> {
                val ts = dimensions["thickness"] ?: 150.0
                (ts/1000.0) * 25.0 // kN/m2
            }
            else -> 0.0
        }

        // Logic check: if reported DL is significantly lower than self-weight
        if (reportedDL > 0 && reportedDL < selfWeight * 0.95) {
            val msg = if (memberType == "BEAM" || memberType == "SLAB") 
                "Logic Warning: Input Dead Load ($reportedDL) is less than calculated self-weight ($selfWeight). Self-weight should be included in the input or handled by the engine."
            else
                "Logic Warning: Input Axial Dead Load ($reportedDL) is less than estimated column self-weight ($selfWeight). Check load path."
            warnings.add(msg)
        }

        return ValidationReport(warnings.isEmpty(), emptyList(), warnings)
    }

    /**
     * General result validation (dispatches to specific types)
     */
    fun validate(result: Any): ValidationReport {
        return when (result) {
            is BeamResult -> validateBeam(result)
            is ColumnResult -> validateColumn(result)
            is SlabResult -> validateSlab(result)
            is TankResult -> validateTank(result)
            is RetainingWallResult -> validateRetainingWall(result)
            else -> ValidationReport(true, emptyList(), emptyList())
        }
    }
}
