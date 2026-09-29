package com.civileg.app.domain.usecases

import javax.inject.Inject

/**
 * Centralized input validation for structural design calculations.
 * Each method returns null if valid, or an error message string if invalid.
 */
class ValidateInputsUseCase @Inject constructor() {

    fun validatePositiveValue(value: Double, fieldName: String): String? {
        return if (value > 0) null else "$fieldName must be positive"
    }

    fun validateBeamInputs(
        span: Double, width: Double, depth: Double, load: Double, fcu: Double, fy: Double
    ): List<String> {
        val errors = mutableListOf<String>()
        if (span <= 0) errors.add("Span must be positive")
        if (width <= 0) errors.add("Width must be positive")
        if (depth <= 0) errors.add("Depth must be positive")
        if (fcu <= 0) errors.add("f'cu must be positive")
        if (fy <= 0) errors.add("fy must be positive")
        return errors
    }

    fun validateColumnInputs(
        load: Double, width: Double, depth: Double, fcu: Double, fy: Double
    ): List<String> {
        val errors = mutableListOf<String>()
        if (load <= 0) errors.add("Axial load must be positive")
        if (width <= 0) errors.add("Width must be positive")
        if (depth <= 0) errors.add("Depth must be positive")
        if (fcu <= 0) errors.add("f'cu must be positive")
        if (fy <= 0) errors.add("fy must be positive")
        return errors
    }

    fun validateSlabInputs(
        spanX: Double, spanY: Double, load: Double, fcu: Double, fy: Double
    ): List<String> {
        val errors = mutableListOf<String>()
        if (spanX <= 0) errors.add("Span X must be positive")
        if (spanY <= 0) errors.add("Span Y must be positive")
        if (fcu <= 0) errors.add("f'cu must be positive")
        if (fy <= 0) errors.add("fy must be positive")
        return errors
    }

    fun validateFootingInputs(
        load: Double, fcu: Double, fy: Double, allowablePressure: Double
    ): List<String> {
        val errors = mutableListOf<String>()
        if (load <= 0) errors.add("Load must be positive")
        if (fcu <= 0) errors.add("f'cu must be positive")
        if (fy <= 0) errors.add("fy must be positive")
        if (allowablePressure <= 0) errors.add("Allowable soil pressure must be positive")
        return errors
    }

    fun validateTankInputs(
        length: Double, width: Double, height: Double, fcu: Double, fy: Double
    ): List<String> {
        val errors = mutableListOf<String>()
        if (length <= 0) errors.add("Length must be positive")
        if (width <= 0) errors.add("Width must be positive")
        if (height <= 0) errors.add("Height must be positive")
        if (fcu <= 0) errors.add("f'cu must be positive")
        if (fy <= 0) errors.add("fy must be positive")
        return errors
    }
}