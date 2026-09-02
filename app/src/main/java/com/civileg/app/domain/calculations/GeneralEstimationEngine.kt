package com.civileg.app.domain.calculations

import kotlin.math.*

/**
 * GeneralEstimationEngine - موديول حسابات المستخدم العادي
 * 
 * Provides simple engineering estimations for finishing, building, and material weights
 * per standard Egyptian and Gulf practices.
 */
object GeneralEstimationEngine {

    // ========== 1. FINISHING CALCULATIONS (التشطيبات) ==========

    /**
     * حساب كميات المحارة (Plastering)
     * @param areaM2 المساحة بالمتر المربع
     * @param thicknessCm سمك الطبقة (افتراضي 2 سم)
     */
    fun calculatePlaster(areaM2: Double, thicknessCm: Double = 2.0): PlasterResult {
        val volumeM3 = areaM2 * (thicknessCm / 100.0)
        // المونة: 1 متر مكعب رمل يحتاج 300 كجم أسمنت (6 شكاير)
        val cementBags = ceil(volumeM3 * 6.0).toInt()
        val sandM3 = volumeM3 * 1.0
        return PlasterResult(areaM2, cementBags, sandM3, volumeM3)
    }

    /**
     * حساب كميات النقاشة (Painting)
     * @param areaM2 المساحة بالمتر المربع
     * @param numCoats عدد السكاكين/الأوجه (افتراضي 3)
     */
    fun calculatePaint(areaM2: Double, numCoats: Int = 3): PaintResult {
        // الجالون (3.75 لتر) يفرد حوالي 10 متر مربع وجهين
        val litersRequired = (areaM2 / 10.0) * (numCoats / 2.0) * 3.75
        val gallons = ceil(litersRequired / 3.75).toInt()
        return PaintResult(areaM2, litersRequired, gallons)
    }

    // ========== 2. BRICKWORK (المباني) ==========

    /**
     * حساب عدد الطوب (Brickwork)
     * @param wallAreaM2 مساحة الحائط
     * @param wallThicknessCm سمك الحائط (12 أو 25 سم)
     * @param brickType نوع الطوب (محدد الأبعاد)
     */
    fun calculateBricks(wallAreaM2: Double, wallThicknessCm: Double = 12.0): BrickResult {
        // طوب أحمر قياسي (25x12x6 سم)
        // المتر المربع سمك 12 سم يحتاج حوالي 55 طوبة
        // المتر المربع سمك 25 سم يحتاج حوالي 110 طوبة
        val bricksPerM2 = if (wallThicknessCm > 15.0) 110 else 55
        val totalBricks = ceil(wallAreaM2 * bricksPerM2).toInt()
        
        // المونة للمباني: 1000 طوبة تحتاج 3 شكاير أسمنت و 0.5 متر رمل
        val cementBags = ceil((totalBricks / 1000.0) * 3.0).toInt()
        val sandM3 = (totalBricks / 1000.0) * 0.5
        
        return BrickResult(totalBricks, cementBags, sandM3)
    }

    // ========== 3. WEIGHTS (الأوزان) ==========

    /**
     * وزن المتر الطولي لسيخ الحديد (Rebar Weight)
     * @param diameterMm القطر بالمم
     */
    fun getRebarWeightPerMeter(diameterMm: Int): Double {
        return (diameterMm * diameterMm) / 162.0
    }

    /**
     * أوزان المواد الإنشائية (Standard Densities)
     */
    fun getMaterialDensity(material: String): Double {
        return when (material.lowercase()) {
            "concrete", "خرسانة" -> 2500.0 // kg/m3
            "steel", "حديد" -> 7850.0
            "brick", "طوب" -> 1800.0
            "sand", "رمل" -> 1600.0
            "water", "ماء" -> 1000.0
            else -> 0.0
        }
    }

    // ========== DATA CLASSES ==========

    data class PlasterResult(val area: Double, val cementBags: Int, val sandM3: Double, val volumeM3: Double)
    data class PaintResult(val area: Double, val liters: Double, val gallons: Int)
    data class BrickResult(val totalBricks: Int, val cementBags: Int, val sandM3: Double)
}
