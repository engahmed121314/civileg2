package com.civileg.app.domain.entities

/**
 * Domain-level project model.
 * NOTE: The Room @Entity version of Project lives in com.civileg.app.db.entities (entities.kt).
 * This class serves as the domain-layer representation, mapped to/from the Room entity
 * by ProjectRepositoryImpl. Renamed from "Project" to "DomainProject" to avoid collision
 * with com.civileg.app.db.Project.
 */
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
@Deprecated("Use DomainProject directly. This alias exists for migration convenience.")
typealias Project = DomainProject
