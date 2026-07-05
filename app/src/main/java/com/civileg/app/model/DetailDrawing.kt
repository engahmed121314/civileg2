package com.civileg.app.model

data class DetailDrawing(
    val title: String,
    val type: String,
    val drawingNumber: String,
    val description: String,
    val imageUrl: String? = null
)