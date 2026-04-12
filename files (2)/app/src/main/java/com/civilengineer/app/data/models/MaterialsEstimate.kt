package com.civilengineer.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * نموذج حصر الكميات والمواد
 */
@Entity(tableName = "materials_estimates")
data class MaterialsEstimate(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @SerializedName("estimate_name")
    val estimateName: String,
    
    @SerializedName("estimate_date")
    val estimateDate: Long = System.currentTimeMillis(),
    
    @SerializedName("project_name")
    val projectName: String,
    
    @SerializedName("description")
    val description: String = "",
    
    // الخرسانة
    @SerializedName("concrete_volume_m3")
    val concreteVolumeM3: Double = 0.0,
    
    @SerializedName("concrete_unit_cost")
    val concreteUnitCost: Double = 0.0,
    
    @SerializedName("concrete_total_cost")
    val concreteTotalCost: Double = 0.0,
    
    // الفولاذ
    @SerializedName("steel_weight_tons")
    val steelWeightTons: Double = 0.0,
    
    @SerializedName("steel_unit_cost")
    val steelUnitCost: Double = 0.0,
    
    @SerializedName("steel_total_cost")
    val steelTotalCost: Double = 0.0,
    
    // الطوب
    @SerializedName("brick_quantity")
    val brickQuantity: Int = 0, // بالألف طابوقة
    
    @SerializedName("brick_unit_cost")
    val brickUnitCost: Double = 0.0,
    
    @SerializedName("brick_total_cost")
    val brickTotalCost: Double = 0.0,
    
    // الرمل
    @SerializedName("sand_volume_m3")
    val sandVolumeM3: Double = 0.0,
    
    @SerializedName("sand_unit_cost")
    val sandUnitCost: Double = 0.0,
    
    @SerializedName("sand_total_cost")
    val sandTotalCost: Double = 0.0,
    
    // الحصى
    @SerializedName("gravel_volume_m3")
    val gravelVolumeM3: Double = 0.0,
    
    @SerializedName("gravel_unit_cost")
    val gravelUnitCost: Double = 0.0,
    
    @SerializedName("gravel_total_cost")
    val gravelTotalCost: Double = 0.0,
    
    // الإسمنت
    @SerializedName("cement_bags")
    val cementBags: Int = 0,
    
    @SerializedName("cement_unit_cost")
    val cementUnitCost: Double = 0.0,
    
    @SerializedName("cement_total_cost")
    val cementTotalCost: Double = 0.0,
    
    // التكاليف الإجمالية
    @SerializedName("materials_total_cost")
    val materialsTotalCost: Double = 0.0,
    
    @SerializedName("labor_cost_percentage")
    val laborCostPercentage: Double = 15.0,
    
    @SerializedName("labor_total_cost")
    val laborTotalCost: Double = 0.0,
    
    @SerializedName("grand_total_cost")
    val grandTotalCost: Double = 0.0,
    
    @SerializedName("currency")
    val currency: String = "EGP",
    
    @SerializedName("notes")
    val notes: String = ""
)