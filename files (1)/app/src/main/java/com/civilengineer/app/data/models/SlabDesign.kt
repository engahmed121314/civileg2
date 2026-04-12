package com.civilengineer.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * نموذج تصميم البلاطة الخرسانية
 */
@Entity(tableName = "slabs")
data class SlabDesign(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @SerializedName("slab_name")
    val slabName: String,
    
    @SerializedName("length_m")
    val lengthM: Double, // الطول بالمتر
    
    @SerializedName("width_m")
    val widthM: Double, // العرض بالمتر
    
    @SerializedName("thickness_mm")
    val thicknessMM: Int, // السمك بالمليمتر
    
    @SerializedName("live_load_kn_m2")
    val liveLoadKNM2: Double, // الحمل الحي
    
    @SerializedName("dead_load_kn_m2")
    val deadLoadKNM2: Double, // الحمل الميت
    
    @SerializedName("concrete_grade")
    val concreteGrade: String,
    
    @SerializedName("steel_grade")
    val steelGrade: String,
    
    @SerializedName("code_type")
    val codeType: CodeType,
    
    @SerializedName("slab_type")
    val slabType: SlabType, // نوع البلاطة
    
    @SerializedName("support_type")
    val supportType: SupportType, // نوع الاستناد
    
    // نتائج التصميم
    @SerializedName("max_moment_kn_m")
    val maxMomentKNM: Double = 0.0,
    
    @SerializedName("shear_force_kn")
    val shearForceKN: Double = 0.0,
    
    @SerializedName("bottom_steel_dia_mm")
    val bottomSteelDiaMM: Int = 0,
    
    @SerializedName("bottom_steel_spacing_mm")
    val bottomSteelSpacingMM: Int = 0,
    
    @SerializedName("top_steel_dia_mm")
    val topSteelDiaMM: Int = 0,
    
    @SerializedName("top_steel_spacing_mm")
    val topSteelSpacingMM: Int = 0,
    
    @SerializedName("bottom_steel_area_mm2_m")
    val bottomSteelAreaMM2M: Double = 0.0,
    
    @SerializedName("top_steel_area_mm2_m")
    val topSteelAreaMM2M: Double = 0.0,
    
    @SerializedName("is_safe")
    val isSafe: Boolean = false,
    
    @SerializedName("deflection_mm")
    val deflectionMM: Double = 0.0,
    
    @SerializedName("max_allowed_deflection_mm")
    val maxAllowedDeflectionMM: Double = 0.0,
    
    // معاملات إضافية
    @SerializedName("cover_mm")
    val coverMM: Int = 25,
    
    @SerializedName("waste_percentage")
    val wastePercentage: Double = 10.0,
    
    @SerializedName("unit_currency")
    val unitCurrency: String = "EGP",
    
    @SerializedName("cost_per_ton_steel")
    val costPerTonSteel: Double = 0.0,
    
    @SerializedName("cost_per_m3_concrete")
    val costPerM3Concrete: Double = 0.0,
    
    @SerializedName("total_cost")
    val totalCost: Double = 0.0,
    
    @SerializedName("created_date")
    val createdDate: Long = System.currentTimeMillis(),
    
    @SerializedName("notes")
    val notes: String = "",
    
    @SerializedName("equations_used")
    val equationsUsed: String = ""
) {
    fun getConcreteVolume(): Double = lengthM * widthM * (thicknessMM / 1000.0)
    
    fun getTotalLoad(): Double = (liveLoadKNM2 + deadLoadKNM2) * lengthM * widthM
}

enum class SlabType {
    ONE_WAY,        // بلاطة أحادية الاتجاه
    TWO_WAY,        // بلاطة ثنائية الاتجاه
    HOLLOW_CORE,    // بلاطة فراغية
    RIBBED           // بلاطة متقاطعة
}

enum class SupportType {
    SIMPLY_SUPPORTED,      // مستندة بحرية
    CANTILEVER,            // كابولي
    CONTINUOUS,            // مستمرة
    FIXED                   // مثبتة
}