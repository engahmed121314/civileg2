package com.civilengineer.app.domain.calculator

/**
 * محسبة التكاليف
 */
class CostCalculator {

    /**
     * حساب تكلفة الخرسانة
     */
    fun calculateConcreteCost(
        concreteVolume: Double, // بالمتر المكعب
        costPerM3: Double,
        wastePercentage: Double = 5.0
    ): Double {
        val totalVolume = concreteVolume * (1 + wastePercentage / 100)
        return totalVolume * costPerM3
    }

    /**
     * حساب تكلفة الفولاذ
     */
    fun calculateSteelCost(
        steelAreaMM2: Double,
        lengthM: Double,
        steelDensity: Double = 7.85, // كجم/ديسيمتر مكعب
        costPerTon: Double,
        wastePercentage: Double = 10.0
    ): Double {
        // تحويل المساحة إلى حجم
        val steelVolume = steelAreaMM2 / 1_000_000 * lengthM // متر مكعب
        
        // حساب الوزن
        val steelWeight = steelVolume * steelDensity // كجم
        
        // إضافة الهالك
        val totalWeight = steelWeight * (1 + wastePercentage / 100)
        
        // التكلفة بالطن
        return (totalWeight / 1000) * costPerTon
    }

    /**
     * حساب التكلفة الإجمالية
     */
    fun calculateTotalCost(
        concreteCost: Double,
        steelCost: Double,
        laborPercentage: Double = 15.0 // نسبة الأجور
    ): Double {
        val materialCost = concreteCost + steelCost
        val laborCost = materialCost * (laborPercentage / 100)
        return materialCost + laborCost
    }

    /**
     * حساب التكلفة لكل متر مربع
     */
    fun calculateCostPerM2(
        totalCost: Double,
        areaM2: Double
    ): Double {
        return totalCost / areaM2
    }

    /**
     * حساب التكلفة لكل متر مكعب من الخرسانة
     */
    fun calculateCostPerM3Concrete(
        concreteCost: Double,
        volumeM3: Double
    ): Double {
        return concreteCost / volumeM3
    }

    /**
     * حساب التكلفة لكل طن من الفولاذ
     */
    fun calculateCostPerTonSteel(
        steelCost: Double,
        weightTons: Double
    ): Double {
        return steelCost / weightTons
    }

    /**
     * حساب نسبة الفولاذ إلى الخرسانة بالتكلفة
     */
    fun calculateSteelToConcreteCostRatio(
        steelCost: Double,
        concreteCost: Double
    ): Double {
        return steelCost / concreteCost
    }

    /**
     * تقدير التكلفة بناءً على معاملات معروفة
     */
    fun estimateCost(
        concreteVolume: Double,
        steelWeightTons: Double,
        concreteUnitCost: Double,
        steelUnitCost: Double
    ): Double {
        val concreteCost = concreteVolume * concreteUnitCost
        val steelCost = steelWeightTons * steelUnitCost
        return concreteCost + steelCost
    }
}