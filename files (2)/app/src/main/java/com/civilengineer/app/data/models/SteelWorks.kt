package com.civilengineer.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * نموذج الأعمال المعدنية
 */
@Entity(tableName = "steel_works")
data class SteelWork(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @SerializedName("work_name")
    val workName: String,
    
    @SerializedName("work_type")
    val workType: SteelWorkType,
    
    @SerializedName("steel_type")
    val steelType: String, // نوع الفولاذ (أسياخ، حديد مشغول، إلخ)
    
    @SerializedName("diameter_mm")
    val diameterMM: Int = 0, // القطر بالملم (للأسياخ)
    
    @SerializedName("length_m")
    val lengthM: Double = 0.0, // الطول بالمتر
    
    @SerializedName("weight_kg")
    val weightKg: Double, // الوزن بالكيلوجرام
    
    @SerializedName("unit_cost_per_kg")
    val unitCostPerKg: Double, // سعر الكيلوجرام الواحد
    
    @SerializedName("fabrication_cost_percentage")
    val fabricationCostPercentage: Double = 20.0, // نسبة تكاليف التصنيع
    
    @SerializedName("waste_percentage")
    val wastePercentage: Double = 8.0,
    
    @SerializedName("total_cost")
    val totalCost: Double = 0.0,
    
    @SerializedName("specifications")
    val specifications: String = "", // مواصفات الفولاذ
    
    @SerializedName("notes")
    val notes: String = "",
    
    @SerializedName("created_date")
    val createdDate: Long = System.currentTimeMillis()
)

enum class SteelWorkType {
    REINFORCEMENT_BARS,        // أسياخ التسليح
    STRUCTURAL_STEEL,          // فولاذ إنشائي
    STEEL_COLUMNS,             // أعمدة معدنية
    STEEL_BEAMS,               // كمرات معدنية
    STEEL_ROOF,                // أسقف معدنية
    STEEL_DOORS,               // أبواب معدنية
    STEEL_WINDOWS,             // شبابيك معدنية
    STEEL_STAIRS,              // درجات معدنية
    ORNAMENTAL_STEEL,          // أعمال معدنية زخرفية
    STEEL_RAILINGS             // حواجز معدنية
}