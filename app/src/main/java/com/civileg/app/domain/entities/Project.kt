package com.civileg.app.domain.entities

data class Project(
    val id: Int = 0,
    val name: String,
    val date: Long = System.currentTimeMillis(),
    val designCode: DesignCode,
    val elementType: ElementType,
    val inputs: Map<String, Double>,
    val results: Map<String, Any?>,
    val notes: String = ""
)
