package com.civilengineer.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * نموذج الأعمال الخرسانية
 */
@Entity(tableName = "concrete_works")
data class ConcreteWork(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @SerializedName("work_name")
    val workName: String,
    
    @SerializedName("work_type")
    val workType: ConcreteWorkType, // نوع العمل
    
    @SerializedName("quantity")
    val quantity: Double, // الكمية
    
    @SerializedName("unit")
    val unit: String, // الوحدة (م، م³، م²)
    
    @SerializedName("unit_cost")
    val unitCost: Double, // ال��كلفة للوحدة الواحدة
    
    @SerializedName("labor_cost_percentage")
    val laborCostPercentage: Double = 15.0, // نسبة أجور العمالة
    
    @SerializedName("waste_percentage")
    val wastePercentage: Double = 5.0, // نسبة الهالك
    
    @SerializedName("total_cost")
    val totalCost: Double = 0.0,
    
    @SerializedName("notes")
    val notes: String = "",
    
    @SerializedName("created_date")
    val createdDate: Long = System.currentTimeMillis()
)

enum class ConcreteWorkType {
    CONCRETE_CASTING,          // صب خرساني
    CONCRETE_FINISHING,        // تشطيب خرساني
    BRICKWORK,                 // أعمال طوب
    CONCRETE_COLUMN,           // أعمدة خرسانية
    CONCRETE_BEAM,             // كمرات خرسانية
    CONCRETE_SLAB,             // بلاطات خرسانية
    FOUNDATION_CONCRETE,       // خرسانة قواعد
    REPAIR_CONCRETE,           // إصلاح خرساني
    CONCRETE_FLOOR,            // أرضيات خرسانية
    CONCRETE_WALL              // جدران خرسانية
}