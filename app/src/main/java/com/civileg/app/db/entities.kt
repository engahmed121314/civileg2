package com.civileg.app.db

import com.civileg.app.domain.entities.DesignCode

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val location: String = "",
    val clientName: String = "",
    val description: String = "",
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val code: String = "ECP", // "ECP", "SBC", "ACI"
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)

enum class ProjectStatus {
    ACTIVE, COMPLETED, ON_HOLD, CANCELLED
}

@Entity(
    tableName = "designs",
    indices = [androidx.room.Index(value = ["projectId"])],
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Design(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val type: DesignType,
    val name: String,
    val inputData: String, // JSON string (contains specific input for each type)
    val results: String,   // JSON string (contains specific result for each type)
    val isSafe: Boolean,
    val utilizationRatio: Double = 0.0,
    val codeUsed: String,
    val concreteVolume: Double = 0.0,
    val steelWeight: Double = 0.0,
    val totalCost: Double = 0.0,
    val createdAt: Date = Date()
)

enum class DesignType {
    BEAM, COLUMN, FOOTING, SLAB, STAIRCASE, RETAINING_WALL, WATER_TANK, PILE, SEISMIC, STEEL_MEMBER, STEEL_WAREHOUSE, FRAME_ANALYSIS, PILE_FOUNDATION, FLAT_SLAB, SHEAR_WALL, CONCRETE_MIX, SOIL_BEARING, WIND_LOAD, WAFFLE_SLAB, HORDI_SLAB, COMBINED_FOOTING
}

@Entity(
    tableName = "footings",
    indices = [androidx.room.Index(value = ["projectId"])],
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Footing(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val type: String, 
    val load: Double,
    val soilPressure: Double,
    val fcu: Double,
    val fy: Double,
    val colWidth: Double,
    val colDepth: Double,
    val width: Double,
    val length: Double,
    val thickness: Double,
    val reinforcementBottom: String, 
    val reinforcementTop: String?,   
    val concreteVolume: Double,
    val steelWeight: Double,
    val cost: Double,
    val utilizationRatio: Double = 0.0
)

@Entity(
    tableName = "columns_table",
    indices = [androidx.room.Index(value = ["projectId"])],
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ColumnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val load: Double,
    val fcu: Double,
    val fy: Double,
    val width: Double,
    val depth: Double,
    val reinforcement: String, 
    val ties: String,          
    val concreteVolume: Double,
    val steelWeight: Double,
    val cost: Double,
    val utilizationRatio: Double = 0.0
)

@Entity(
    tableName = "slabs",
    indices = [androidx.room.Index(value = ["projectId"])],
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Slab(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val type: String, 
    val spanX: Double,
    val spanY: Double,
    val thickness: Double,
    val load: Double,
    val fcu: Double,
    val fy: Double,
    val reinforcement: String, 
    val concreteVolume: Double,
    val steelWeight: Double,
    val cost: Double,
    val utilizationRatio: Double = 0.0
)

@Entity(
    tableName = "beams",
    indices = [androidx.room.Index(value = ["projectId"])],
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Beam(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val span: Double,
    val load: Double,
    val fcu: Double,
    val fy: Double,
    val width: Double,
    val depth: Double,
    val reinforcement: String, 
    val stirrups: String,      
    val concreteVolume: Double,
    val steelWeight: Double,
    val cost: Double,
    val utilizationRatio: Double = 0.0
)

@Entity(
    tableName = "stairs",
    indices = [androidx.room.Index(value = ["projectId"])],
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Stair(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val thickness: Double,
    val load: Double,
    val fcu: Double,
    val fy: Double,
    val reinforcement: String,
    val concreteVolume: Double,
    val steelWeight: Double,
    val cost: Double,
    val utilizationRatio: Double = 0.0
)

@Entity(
    tableName = "retaining_walls",
    indices = [androidx.room.Index(value = ["projectId"])],
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class RetainingWall(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val height: Double,
    val stemThickness: Double,
    val baseWidth: Double,
    val baseThickness: Double,
    val reinforcement: String,
    val concreteVolume: Double,
    val steelWeight: Double,
    val cost: Double,
    val utilizationRatio: Double = 0.0
)

@Entity(
    tableName = "tanks",
    indices = [androidx.room.Index(value = ["projectId"])],
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Tank(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val length: Double,
    val width: Double,
    val height: Double,
    val wallThickness: Double,
    val baseThickness: Double,
    val reinforcement: String,
    val concreteVolume: Double,
    val steelWeight: Double,
    val cost: Double,
    val utilizationRatio: Double = 0.0
)

@Entity(
    tableName = "materials",
    indices = [androidx.room.Index(value = ["projectId"])],
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class MaterialItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val name: String,
    val unit: String,
    val quantity: Double,
    val unitPrice: Double,
    val totalPrice: Double,
    val category: MaterialCategory,
    val createdAt: Date = Date()
)

enum class MaterialCategory {
    CONCRETE, STEEL, FORMWORK, FINISHING, MECHANICAL, ELECTRICAL
}

@Entity(tableName = "inventory")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: InventoryType,
    val quantity: Double,
    val unit: String,
    val registrationDate: Date = Date(),
    val alertQuantity: Double = 0.0,
    val lastUpdated: Date = Date(),
    val notes: String = ""
)

enum class InventoryType {
    EQUIPMENT, TOOLS, RAW_MATERIAL, ACCESSORIES
}

@Entity(
    tableName = "pour_logs",
    indices = [androidx.room.Index(value = ["projectId"])],
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class PourLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val elementId: String, // e.g., "BEAM-01"
    val date: Date = Date(),
    val volumeM3: Double,
    val slumpMm: Double,
    val truckId: String = "",
    val sampleId: String = "",
    val strength7Days: Double = 0.0,
    val strength28Days: Double = 0.0,
    val status: String = "PENDING"
)

@Entity(
    tableName = "site_inspections",
    indices = [androidx.room.Index(value = ["projectId"]), androidx.room.Index(value = ["designId"])],
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Design::class,
            parentColumns = ["id"],
            childColumns = ["designId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class SiteInspection(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val designId: Long? = null,
    val inspectorName: String,
    val date: Date = Date(),
    val formworkSafe: Boolean = false,
    val rebarMatchesDesign: Boolean = false,
    val coverAdequate: Boolean = false,
    val cleanlinessPassed: Boolean = false,
    val comments: String = ""
)

/**
 * Generic quality-control record captured by the Quality Engineer module.
 *
 * One row per performed check, covering every discipline
 * (CONCRETE, SOIL, REBAR, STEEL, ROADS, BRIDGES, TUNNELS, BUILDINGS).
 * Inputs, computed values and safety checks are stored as a JSON payload so the
 * record remains forward-compatible without a schema change per discipline.
 */
@Entity(
    tableName = "quality_records",
    indices = [androidx.room.Index(value = ["category"]), androidx.room.Index(value = ["createdAt"])]
)
data class QualityRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long = 0,
    val category: String,
    val elementId: String = "",
    val designCode: String = "ECP",
    val passed: Boolean = false,
    val utilizationRatio: Double = 0.0,
    val summary: String = "",
    val dataJson: String = "{}",
    val notes: String = "",
    val createdAt: Date = Date(),
    // Extended fields for professional use
    val inspectorName: String = "",
    val location: String = "",
    val weatherConditions: String = "",
    val complianceStandard: String = "",
    val inspectionType: String = "routine", // routine, special, final
    val photoPathsJson: String = "[]" // JSON array of photo file paths
)

/**
 * Inspection & Test Plan (ITP) line item for a discipline work activity.
 *
 * Captures the planned inspection points, acceptance criteria and outcome so the
 * quality engineer can track progress and witnesses per activity.
 */
@Entity(
    tableName = "inspection_test_plans",
    indices = [androidx.room.Index(value = ["discipline"]), androidx.room.Index(value = ["status"])]
)
data class InspectionTestPlan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long = 0,
    val discipline: String = "",
    val activity: String = "",
    val inspectionItem: String = "",
    val referenceCode: String = "",
    val acceptanceCriteria: String = "",
    val frequency: String = "",
    val responsibleParty: String = "",
    val status: String = "pending", // pending, in_progress, passed, failed
    val resultValue: String = "",
    val checkedBy: String = "",
    val witness: String = "",
    val inspectionDate: Date = Date(),
    val remarks: String = "",
    val createdAt: Date = Date()
)

/**
 * Non-Conformity Report (NCR) raised when a check or inspection fails.
 */
@Entity(
    tableName = "non_conformities",
    indices = [
        androidx.room.Index(value = ["severity"]),
        androidx.room.Index(value = ["status"]),
        androidx.room.Index(value = ["discipline"])
    ]
)
data class NonConformity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long = 0,
    val ncrNumber: String = "",
    val discipline: String = "",
    val elementId: String = "",
    val description: String = "",
    val referenceCode: String = "",
    val severity: String = "minor", // minor, major, critical
    val rootCause: String = "",
    val correctiveAction: String = "",
    val preventiveAction: String = "",
    val responsibleParty: String = "",
    val targetDate: Date = Date(),
    val status: String = "open", // open, in_progress, closed
    val closedBy: String = "",
    val closedDate: Date = Date(),
    val createdAt: Date = Date()
)
