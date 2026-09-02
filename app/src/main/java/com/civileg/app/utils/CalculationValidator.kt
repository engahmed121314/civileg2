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
        if (result.pu > result.axialCapacity + 1.0) {
            if (result.isSafe) errors.add("CRITICAL: Column marked SAFE but Pu (${result.pu}) > Capacity Pn (${result.axialCapacity})")
        }

        // 2. Reinforcement Ratio Audit
        val rho = result.reinforcementRatio
        val minRho = if (result.code == AppDesignCode.EGYPTIAN) 0.8 else 1.0
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
     * Validates a Staircase calculation result with focus on geometry and reinforcement.
     */
    fun validateStair(result: StairResult): ValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. Geometry checks
        if (result.riser <= 0 || result.tread <= 0) {
            errors.add("CRITICAL: Riser and tread must be positive values.")
        } else {
            // 2*R + G = 600-640mm rule
            val sum = 2 * result.riser + result.tread
            if (sum < 550 || sum > 700) {
                warnings.add("Logic Warning: 2R+G = ${String.format(java.util.Locale.US, "%.0f", sum)}mm is outside the typical comfort range (550-700mm).")
            }
        }

        // 2. Thickness reasonableness
        if (result.thickness < 100.0) {
            warnings.add("Logic Warning: Stair slab thickness (${result.thickness}mm) is very thin. Minimum 120-150mm recommended.")
        }

        // 3. Non-zero concrete volume
        if (result.concreteVolume <= 0) {
            if (result.isSafe) errors.add("CRITICAL: Concrete volume is zero or negative but element marked SAFE.")
        }

        // 4. Utilization check
        if (result.utilizationRatio > 0.95 && result.isSafe) {
            warnings.add("Warning: Stair design is at 95%+ utilization. Consider increasing thickness.")
        }

        return ValidationReport(errors.isEmpty(), errors, warnings)
    }

    /**
     * Validates a Tank (Water Structure) calculation result.
     */
    fun validateTank(result: TankResult): ValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. Positive dimensions
        if (result.length <= 0) errors.add("CRITICAL: Tank length must be positive.")
        if (result.width <= 0) errors.add("CRITICAL: Tank width must be positive.")
        if (result.height <= 0) errors.add("CRITICAL: Tank height must be positive.")

        // 2. Reasonable wall/base thickness ratio
        if (result.wallThickness > 0 && result.baseThickness > 0) {
            val ratio = result.wallThickness / result.baseThickness
            if (ratio > 2.0) {
                warnings.add("Logic Warning: Wall-to-base thickness ratio ($ratio) is very high. Consider increasing base thickness.")
            }
            if (ratio < 0.3) {
                warnings.add("Logic Warning: Wall-to-base thickness ratio ($ratio) is very low. Verify design assumptions.")
            }
        }

        // 3. Non-zero concrete volume
        if (result.concreteVolume <= 0) {
            if (result.isSafe) errors.add("CRITICAL: Concrete volume is zero or negative but tank is marked SAFE.")
        }

        // 4. Reinforcement ratio within 0.1%-4%
        val wallReinfArea = result.wallReinforcement.area // mm² per meter
        val wallSection = result.wallThickness * 1000.0 // mm² per meter (b=1000mm)
        if (wallSection > 0) {
            val rho = (wallReinfArea / wallSection) * 100.0
            if (rho < 0.1) warnings.add("Logic Warning: Wall reinforcement ratio (${String.format(java.util.Locale.US, "%.2f", rho)}%) is below 0.1%. Ensure minimum shrinkage/temp reinforcement.")
            if (rho > 4.0) warnings.add("Logic Warning: Wall reinforcement ratio (${String.format(java.util.Locale.US, "%.2f", rho)}%) exceeds 4%. Congestion risk.")
        }

        // 5. Water-tightness: minimum wall thickness
        if (result.wallThickness < 200.0) {
            warnings.add("Logic Warning: Wall thickness (${result.wallThickness}mm) is less than recommended 200mm for water-tightness.")
        }

        // 6. Check structural weight vs uplift for underground tanks
        if (result.type == TankType.UNDERGROUND) {
            val upliftCheck = result.safetyChecks.find { it.name.contains("Uplift", ignoreCase = true) }
            if (upliftCheck != null && !upliftCheck.isSafe) {
                errors.add("CRITICAL: Underground tank fails uplift check. Needs more weight or mechanical anchors.")
            }
        }

        // 7. High utilization audit
        if (result.utilizationRatio > 0.95 && result.isSafe) {
            warnings.add("Warning: Section is at 95%+ capacity. Consider increasing thickness for better crack control in water structures.")
        }

        return ValidationReport(errors.isEmpty(), errors, warnings)
    }

    /**
     * Validates a Retaining Wall calculation result with focus on soil-structure interaction.
     */
    fun validateRetainingWall(result: RetainingWallResult): ValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. Positive dimensions
        if (result.height <= 0) errors.add("CRITICAL: Wall height must be positive.")
        if (result.stemThickness <= 0) errors.add("CRITICAL: Stem thickness must be positive.")
        if (result.baseWidth <= 0) errors.add("CRITICAL: Base width must be positive.")

        // 2. Base width >= stem thickness
        if (result.baseWidth > 0 && result.stemThickness > 0 && result.baseWidth < result.stemThickness) {
            errors.add("CRITICAL: Base width (${result.baseWidth}mm) is less than stem thickness (${result.stemThickness}mm).")
        }

        // 3. Overturning stability (FS > 1.5)
        if (result.factorOfSafetyOverturning < 1.5) {
            if (result.isSafe) errors.add("CRITICAL: Wall fails overturning check (FS=${String.format(java.util.Locale.US, "%.2f", result.factorOfSafetyOverturning)} < 1.5)")
        }

        // 4. Sliding stability (FS > 1.5)
        if (result.factorOfSafetySliding < 1.5) {
            if (result.isSafe) errors.add("CRITICAL: Wall fails sliding check (FS=${String.format(java.util.Locale.US, "%.2f", result.factorOfSafetySliding)} < 1.5)")
        }

        // 5. Bearing pressure check
        if (result.maxBearingPressure > 0) {
            if (result.minBearingPressure < 0) {
                errors.add("CRITICAL: Negative bearing pressure detected (toe lift-off). Base width may be insufficient.")
            }
        }

        // 6. Geometrical consistency
        if (result.baseWidth < result.height * 0.4) {
            warnings.add("Logic: Base width is very narrow compared to wall height (< 0.4H).")
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
     * Validate footing result: check soil pressure ratio, dimensional consistency, reinforcement presence.
     */
    fun validateFooting(result: FootingResult): ValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (result.width <= 0 || result.length <= 0) errors.add("Footing dimensions must be positive.")
        if (result.thickness <= 0) errors.add("Footing thickness must be positive.")
        if (result.soilPressure <= 0) errors.add("Soil pressure must be positive.")
        if (result.allowablePressure <= 0) errors.add("Allowable bearing pressure must be positive.")

        if (result.soilPressure > result.allowablePressure) {
            warnings.add("Applied soil pressure (${String.format("%.1f", result.soilPressure)} kPa) exceeds allowable (${String.format("%.1f", result.allowablePressure)} kPa).")
        }
        if (result.barsX <= 0 || result.barsY <= 0) {
            warnings.add("No reinforcement bars provided in one or both directions.")
        }
        return ValidationReport(errors.isEmpty(), errors, warnings)
    }

    /**
     * General result validation (dispatches to specific types)
     */
    fun validate(result: Any): ValidationReport {
        return when (result) {
            is BeamResult -> validateBeam(result)
            is ColumnResult -> validateColumn(result)
            is SlabResult -> validateSlab(result)
            is StairResult -> validateStair(result)
            is TankResult -> validateTank(result)
            is RetainingWallResult -> validateRetainingWall(result)
            is FootingResult -> validateFooting(result)
            else -> ValidationReport(true, emptyList(), emptyList())
        }
    }
}
