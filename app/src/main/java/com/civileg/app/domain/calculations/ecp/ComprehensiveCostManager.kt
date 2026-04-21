package com.civileg.app.domain.utils

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * نظام إدارة التكاليف الشامل للمشاريع الإنشائية
 */
object ComprehensiveCostManager {

    data class MaterialPrices(
        val cementPricePerTon: BigDecimal,
        val steelPricePerTon: BigDecimal,
        val structuralSteelPricePerTon: BigDecimal, // حديد المنشآت المعدنية
        val sandPricePerM3: BigDecimal,
        val gravelPricePerM3: BigDecimal,
        val laborPricePerM3: BigDecimal
    )

    data class CostResult(
        val totalCost: BigDecimal,
        val materialsBreakdown: Map<String, BigDecimal>,
        val wastageAmount: BigDecimal
    )

    fun calculateTotalProjectCost(
        concreteVolume: Double,
        steelWeightKg: Double,
        prices: MaterialPrices,
        wastageFactor: Double = 1.10 // 10% هالك افتراضي
    ): CostResult {
        val vol = BigDecimal.valueOf(concreteVolume)
        val steelTon = BigDecimal.valueOf(steelWeightKg).divide(BigDecimal(1000), 4, RoundingMode.HALF_UP)
        val factor = BigDecimal.valueOf(wastageFactor)

        // حساب تكلفة الخرسانة (افتراض خلطة 350 كجم أسمنت)
        val cementCost = steelTon.multiply(prices.steelPricePerTon)
        val laborCost = vol.multiply(prices.laborPricePerM3)
        
        val subTotal = cementCost.add(laborCost)
        val finalTotal = subTotal.multiply(factor).setScale(2, RoundingMode.HALF_UP)

        return CostResult(
            totalCost = finalTotal,
            materialsBreakdown = mapOf("Steel" to cementCost, "Labor" to laborCost),
            wastageAmount = finalTotal.subtract(subTotal)
        )
    }
}