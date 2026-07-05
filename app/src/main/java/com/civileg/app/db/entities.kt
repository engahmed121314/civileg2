package com.civileg.app.db

import androidx.room.Entity
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

@Entity(tableName = "designs")
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
    BEAM, COLUMN, FOOTING, SLAB, STAIRCASE, RETAINING_WALL, WATER_TANK, PILE, SEISMIC, STEEL_MEMBER, STEEL_WAREHOUSE
}

@Entity(tableName = "footings")
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

@Entity(tableName = "columns_table")
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

@Entity(tableName = "slabs")
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

@Entity(tableName = "beams")
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

@Entity(tableName = "stairs")
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

@Entity(tableName = "retaining_walls")
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

@Entity(tableName = "tanks")
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

@Entity(tableName = "materials")
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
