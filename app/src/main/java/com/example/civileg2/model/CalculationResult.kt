package com.example.civileg2.model

data class CalculationResult(
    val title: String,
    val value: Double,
    val unit: String,
    val isSafe: Boolean = true
)
