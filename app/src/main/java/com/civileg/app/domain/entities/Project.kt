package com.civileg.app.domain.entities

import com.civileg.core.calculations.entities.DesignCode
import com.civileg.core.calculations.entities.ElementType

/** Domain-level project model. */
data class DomainProject(
    val id: Int = 0,
    val name: String,
    val date: Long = System.currentTimeMillis(),
    val designCode: DesignCode,
    val elementType: ElementType,
    val inputs: Map<String, Double>,
    val results: Map<String, Any?>,
    val notes: String = ""
)

/** Type alias for backward compatibility with existing code referencing Project. */
typealias Project = DomainProject
