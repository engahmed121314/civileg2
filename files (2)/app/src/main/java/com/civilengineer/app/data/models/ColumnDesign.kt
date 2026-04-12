package com.civilengineer.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * نموذج تصميم العمود الخرساني
 * يحتوي على جميع معاملات التصميم حسب الأكواد (المصري، السعودي، الأمريكي)
 */
@Entity(tableName = "columns")
data class ColumnDesign(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    // معاملات الإدخال الأساسية
    @SerializedName("column_name")
    val columnName: String,
    
    @SerializedName("length_m")
    val lengthM: Double, // طول العمود بالمتر
    
    @SerializedName("width_m")
    val widthM: Double, // عرض العمود بالمتر
    
    @SerializedName("height_m")
    val heightM: Double, // ارتفاع العمود بالمتر
    
    @SerializedName("axial_load_kn")
    val axialLoadKN: Double, // الحمل المحوري بالكيلونيوتن
    
    @SerializedName("bending_moment_kn_m")
    val bendingMomentKNM: Double, // عزم الانحناء بالكيلونيوتن.متر
    
    @SerializedName("concrete_grade")
    val concreteGrade: String, // درجة الخرسانة (C25, C30, C35, C40)
    
    @SerializedName("steel_grade")
    val steelGrade: String, // درجة الفولاذ (S235, S355)
    
    @SerializedName("code_type")
    val codeType: CodeType, // نوع الكود
    
    @SerializedName("column_type")
    val columnType: ColumnType, // نوع العمود
    
    // نتائج التصميم
    @SerializedName("main_steel_area_mm2")
    val mainSteelAreaMM2: Double = 0.0,
    
    @SerializedName("stirrups_area_mm2")
    val stirrupsAreaMM2: Double = 0.0,
    
    @SerializedName("main_steel_dia_mm")
    val mainSteelDiaMM: Int = 0,
    
    @SerializedName("stirrups_dia_mm")
    val stirrupsDiaMM: Int = 0,
    
    @SerializedName("stirrups_spacing_mm")
    val stirrupsSpacingMM: Int = 0,
    
    @SerializedName("concrete_capacity_kn")
    val concreteCapacityKN: Double = 0.0,
    
    @SerializedName("steel_capacity_kn")
    val steelCapacityKN: Double = 0.0,
    
    @SerializedName("total_capacity_kn")
    val totalCapacityKN: Double = 0.0,
    
    @SerializedName("safety_factor")
    val safetyFactor: Double = 0.0,
    
    @SerializedName("is_safe")
    val isSafe: Boolean = false,
    
    @SerializedName("slenderness_ratio")
    val slendernessRatio: Double = 0.0,
    
    @SerializedName("reduction_factor")
    val reductionFactor: Double = 0.0,
    
    // معاملات إضافية
    @SerializedName("cover_mm")
    val coverMM: Int = 40, // غطاء خرساني
    
    @SerializedName("main_steel_percentage")
    val mainSteelPercentage: Double = 0.0,
    
    @SerializedName("waste_percentage")
    val wastePercentage: Double = 10.0, // نسبة الهالك
    
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
    fun getConcreteVolume(): Double = lengthM * widthM * heightM
    
    fun getSteelWeightTons(): Double {
        val steelVolume = (mainSteelAreaMM2 + stirrupsAreaMM2) / 1_000_000 * heightM
        val steelDensity = 7.85 // كجم/ديسيمتر مكعب
        return (steelVolume * steelDensity * (1 + wastePercentage / 100)) / 1000
    }
}

enum class CodeType {
    EGYPTIAN, // الكود المصري
    SAUDI,    // الكود السعودي
    AMERICAN  // الكود الأمريكي (ACI)
}

enum class ColumnType {
    RECTANGULAR,      // عمود مستطيل
    CIRCULAR,         // عمود دائري
    SQUARE,           // عمود مربع
    COMPOSITE         // عمود مركب
}