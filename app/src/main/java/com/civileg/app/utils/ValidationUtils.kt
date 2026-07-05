package com.civileg.app.utils

object ValidationUtils {
    
    fun validatePositive(value: Double?, fieldName: String): ValidationResult {
        return when {
            value == null -> ValidationResult.Error("$fieldName is required")
            value <= 0 -> ValidationResult.Error("$fieldName must be positive")
            else -> ValidationResult.Success
        }
    }
    
    fun validateRange(value: Double?, min: Double, max: Double, fieldName: String): ValidationResult {
        return when {
            value == null -> ValidationResult.Error("$fieldName is required")
            value < min -> ValidationResult.Error("$fieldName must be at least $min")
            value > max -> ValidationResult.Error("$fieldName must not exceed $max")
            else -> ValidationResult.Success
        }
    }
    
    fun validateBeamInputs(
        width: Double?,
        height: Double?,
        span: Double?,
        moment: Double?
    ): List<ValidationResult> {
        return listOf(
            validatePositive(width, "Width"),
            validatePositive(height, "Height"),
            validateRange(span, 0.5, 50.0, "Span"),
            validatePositive(moment, "Moment")
        )
    }
    
    sealed class ValidationResult {
        object Success : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }
}