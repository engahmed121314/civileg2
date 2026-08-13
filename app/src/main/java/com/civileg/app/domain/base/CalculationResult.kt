package com.civileg.app.domain.base

sealed class CalculationResult<out T> {
    data class Success<T>(val data: T): CalculationResult<T>()
    data class Error(val message: String, val code: ErrorCode): CalculationResult<Nothing>()
    object Loading: CalculationResult<Nothing>()
}

enum class ErrorCode {
    INVALID_INPUT, 
    CODE_VIOLATION, 
    CONVERGENCE_FAILED, 
    UNIT_MISMATCH,
    UNKNOWN
}
