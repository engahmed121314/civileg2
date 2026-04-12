package com.civilengineer.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * نموذج دراسة العقار السريعة
 */
@Entity(tableName = "real_estate_studies")
data class RealEstateStudy(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @SerializedName("study_name")
    val studyName: String,
    
    @SerializedName("study_date")
    val studyDate: Long = System.currentTimeMillis(),
    
    @SerializedName("project_type")
    val projectType: ProjectType, // نوع المشروع
    
    @SerializedName("land_area_m2")
    val landAreaM2: Double, // مساحة الأرض بالمتر المربع
    
    @SerializedName("building_area_m2")
    val buildingAreaM2: Double, // مساحة البناء المخطط لها
    
    @SerializedName("build_on_full_land")
    val buildOnFullLand: Boolean, // البناء على كامل الأرض أم جزء منها
    
    @SerializedName("number_of_floors")
    val numberOfFloors: Int, // عدد الأدوار
    
    @SerializedName("building_budget")
    val buildingBudget: Double, // ميزانية المشروع
    
    @SerializedName("cost_per_m2_estimated")
    val costPerM2Estimated: Double = 0.0, // التكلفة المقدرة لكل متر مربع
    
    @SerializedName("estimated_total_cost")
    val estimatedTotalCost: Double = 0.0, // التكلفة الإجمالية المقدرة
    
    @SerializedName("budget_coverage_percentage")
    val budgetCoveragePercentage: Double = 0.0, // نسبة تغطية الميزانية
    
    @SerializedName("cost_breakdown")
    val costBreakdown: CostBreakdown = CostBreakdown(),
    
    @SerializedName("feasibility_status")
    val feasibilityStatus: FeasibilityStatus = FeasibilityStatus.FEASIBLE,
    
    @SerializedName("recommendations")
    val recommendations: String = "",
    
    @SerializedName("notes")
    val notes: String = "",
    
    @SerializedName("currency")
    val currency: String = "EGP"
)

enum class ProjectType {
    RESIDENTIAL,               // سكني
    COMMERCIAL,                // تجاري
    INDUSTRIAL,                // صناعي
    MIXED_USE,                 // استخدام مختلط
    RESIDENTIAL_COMMERCIAL     // سكني تجاري
}

enum class FeasibilityStatus {
    FEASIBLE,                  // ممكن التنفيذ
    FEASIBLE_WITH_REDUCTION,   // ممكن مع تقليل المساحة
    FEASIBLE_WITH_BUDGET_INCREASE, // ممكن مع زيادة الميزانية
    NOT_FEASIBLE               // غير ممكن
}

data class CostBreakdown(
    @SerializedName("foundation_percentage")
    val foundationPercentage: Double = 10.0,
    
    @SerializedName("structural_percentage")
    val structuralPercentage: Double = 25.0,
    
    @SerializedName("finishing_percentage")
    val finishingPercentage: Double = 35.0,
    
    @SerializedName("utilities_percentage")
    val utilitiesPercentage: Double = 15.0,
    
    @SerializedName("miscellaneous_percentage")
    val miscellaneousPercentage: Double = 15.0
)