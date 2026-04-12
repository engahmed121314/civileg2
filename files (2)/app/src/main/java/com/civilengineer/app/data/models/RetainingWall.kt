package com.civilengineer.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * نموذج تصميم حوائط السند
 */
@Entity(tableName = "retaining_walls")
data class RetainingWall(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @SerializedName("wall_name")
    val wallName: String,
    
    @SerializedName("wall_height_m")
    val wallHeightM: Double, // ارتفاع الجدار
    
    @SerializedName("soil_height_behind_m")
    val soilHeightBehindM: Double, // ارتفاع التربة خلف الجدار
    
    @SerializedName("soil_unit_weight_kn_m3")
    val soilUnitWeightKNM3: Double, // وزن التربة الحجمي
    
    @SerializedName("soil_friction_angle_deg")
    val soilFrictionAngleDeg: Double, // زاوية احتكاك التربة
    
    @SerializedName("soil_cohesion_kn_m2")
    val soilCohesionKNM2: Double, // تماسك التربة
    
    @SerializedName("bearing_capacity_kn_m2")
    val bearingCapacityKNM2: Double, // تحمل التربة أسفل الجدار
    
    @SerializedName("concrete_grade")
    val concreteGrade: String,
    
    @SerializedName("steel_grade")
    val steelGrade: String,
    
    @SerializedName("code_type")
    val codeType: CodeType,
    
    @SerializedName("wall_type")
    val wallType: WallType,
    
    // معاملات الجدار
    @SerializedName("base_width_m")
    val baseWidthM: Double = 0.0,
    
    @SerializedName("top_width_m")
    val topWidthM: Double = 0.0,
    
    @SerializedName("stem_thickness_m")
    val stemThicknessM: Double = 0.0,
    
    @SerializedName("base_thickness_m")
    val baseThicknessM: Double = 0.0,
    
    // نتائج التحليل
    @SerializedName("active_pressure_kn_m")
    val activePressureKNM: Double = 0.0,
    
    @SerializedName("passive_pressure_kn_m")
    val passivePressureKNM: Double = 0.0,
    
    @SerializedName("total_horizontal_force_kn")
    val totalHorizontalForceKN: Double = 0.0,
    
    @SerializedName("total_vertical_load_kn")
    val totalVerticalLoadKN: Double = 0.0,
    
    @SerializedName("overturning_moment_kn_m")
    val overturbingMomentKNM: Double = 0.0,
    
    @SerializedName("resistance_moment_kn_m")
    val resistanceMomentKNM: Double = 0.0,
    
    @SerializedName("factor_of_safety_overturning")
    val factorOfSafetyOverturning: Double = 0.0,
    
    @SerializedName("is_safe_overturning")
    val isSafeOverturning: Boolean = false,
    
    @SerializedName("sliding_friction_kn")
    val slidingFrictionKN: Double = 0.0,
    
    @SerializedName("factor_of_safety_sliding")
    val factorOfSafetySliding: Double = 0.0,
    
    @SerializedName("is_safe_sliding")
    val isSafeSliding: Boolean = false,
    
    @SerializedName("eccentricity_m")
    val eccentricityM: Double = 0.0,
    
    @SerializedName("max_toe_pressure_kn_m2")
    val maxToePressureKNM2: Double = 0.0,
    
    @SerializedName("min_heel_pressure_kn_m2")
    val minHeelPressureKNM2: Double = 0.0,
    
    @SerializedName("is_safe_bearing")
    val isSafeBearing: Boolean = false,
    
    @SerializedName("stem_steel_area_mm2")
    val stemSteelAreaMM2: Double = 0.0,
    
    @SerializedName("base_steel_area_mm2")
    val baseSteelAreaMM2: Double = 0.0,
    
    // معاملات إضافية
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

enum class WallType {
    GRAVITY_WALL,       // جدار الثقل
    CANTILEVER_WALL,    // جدار كابولي
    SHEET_PILING,       // جدران الألواح المضغوطة
    ANCHORED_WALL       // جدار مثبت بالمراسي
}