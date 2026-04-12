package com.civilengineer.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * نموذج تصميم القاعدة (الأساس)
 */
@Entity(tableName = "footings")
data class FootingDesign(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @SerializedName("footing_name")
    val footingName: String,
    
    @SerializedName("column_load_kn")
    val columnLoadKN: Double, // الحمل من العمود
    
    @SerializedName("soil_bearing_capacity_kn_m2")
    val soilBearingCapacityKNM2: Double, // تحمل التربة
    
    @SerializedName("concrete_grade")
    val concreteGrade: String,
    
    @SerializedName("steel_grade")
    val steelGrade: String,
    
    @SerializedName("code_type")
    val codeType: CodeType,
    
    @SerializedName("footing_type")
    val footingType: FootingType,
    
    // للقاعدة المستطيلة
    @SerializedName("length_m")
    val lengthM: Double = 0.0,
    
    @SerializedName("width_m")
    val widthM: Double = 0.0,
    
    @SerializedName("depth_m")
    val depthM: Double = 0.0,
    
    // للقاعدة الدائرية
    @SerializedName("diameter_m")
    val diameterM: Double = 0.0,
    
    // نتائج التصميم
    @SerializedName("footing_area_m2")
    val footingAreaM2: Double = 0.0,
    
    @SerializedName("actual_soil_pressure_kn_m2")
    val actualSoilPressureKNM2: Double = 0.0,
    
    @SerializedName("bearing_capacity_ratio")
    val bearingCapacityRatio: Double = 0.0,
    
    @SerializedName("is_safe_bearing")
    val isSafeBearing: Boolean = false,
    
    @SerializedName("max_moment_kn_m")
    val maxMomentKNM: Double = 0.0,
    
    @SerializedName("shear_force_kn")
    val shearForceKN: Double = 0.0,
    
    @SerializedName("steel_area_mm2")
    val steelAreaMM2: Double = 0.0,
    
    @SerializedName("steel_dia_mm")
    val steelDiaMM: Int = 0,
    
    @SerializedName("steel_spacing_mm")
    val steelSpacingMM: Int = 0,
    
    @SerializedName("min_steel_area_mm2")
    val minSteelAreaMM2: Double = 0.0,
    
    @SerializedName("is_safe_flexure")
    val isSafeFlexure: Boolean = false,
    
    @SerializedName("is_safe_shear")
    val isSafeShear: Boolean = false,
    
    // معاملات إضافية
    @SerializedName("cover_mm")
    val coverMM: Int = 50,
    
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
)

enum class FootingType {
    ISOLATED_RECTANGULAR,  // قاعدة معزولة مستطيلة
    ISOLATED_CIRCULAR,     // قاعدة معزولة دائرية
    ISOLATED_SQUARE,       // قاعدة معزولة مربعة
    STRIP_FOOTING,         // قاعدة شريطية
    COMBINED,              // قاعدة مشتركة
    PILED                  // قاعدة على خوازيق
}