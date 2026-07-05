package com.civileg.app.domain.calculations.ecp

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * نظام إدارة التكاليف الشامل للمشاريع الإنشائية حسب ECP
 */
object ComprehensiveCostManager {

    data class MaterialPrices(
        val cementPricePerTon: BigDecimal,
        val steelPricePerTon: BigDecimal,
        val structuralSteelPricePerTon: BigDecimal,
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
        wastageFactor: Double = 1.10
    ): CostResult {
        val vol = BigDecimal.valueOf(concreteVolume)
        val steelTon = BigDecimal.valueOf(steelWeightKg).divide(BigDecimal(1000), 4, RoundingMode.HALF_UP)
        val factor = BigDecimal.valueOf(wastageFactor)

        // حساب تكلفة الخرسانة
        // خلطة خرسانية عادية: 350 كجم أسمنت/م³ → 0.35 طن/م³
        val cementContentPerM3 = BigDecimal.valueOf(0.35) // طن أسمنت/م³
        val sandContentPerM3 = BigDecimal.valueOf(0.50) // م³ رمل/م³
        val gravelContentPerM3 = BigDecimal.valueOf(0.80) // م³ زلط/م³

        val cementCost = vol.multiply(cementContentPerM3).multiply(prices.cementPricePerTon)
        val sandCost = vol.multiply(sandContentPerM3).multiply(prices.sandPricePerM3)
        val gravelCost = vol.multiply(gravelContentPerM3).multiply(prices.gravelPricePerM3)
        val steelCost = steelTon.multiply(prices.steelPricePerTon)
        val laborCost = vol.multiply(prices.laborPricePerM3)

        val subTotal = cementCost.add(sandCost).add(gravelCost).add(steelCost).add(laborCost)
        val wastageAmount = subTotal.multiply(BigDecimal.valueOf(wastageFactor - 1.0))
        val finalTotal = subTotal.add(wastageAmount).setScale(2, RoundingMode.HALF_UP)

        return CostResult(
            totalCost = finalTotal,
            materialsBreakdown = mapOf(
                "Cement" to cementCost,
                "Sand" to sandCost,
                "Gravel" to gravelCost,
                "Steel" to steelCost,
                "Labor" to laborCost
            ),
            wastageAmount = wastageAmount.setScale(2, RoundingMode.HALF_UP)
        )
    }
}