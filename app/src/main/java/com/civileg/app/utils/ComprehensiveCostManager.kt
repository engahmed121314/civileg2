package com.civileg.app.utils

import com.civileg.app.utils.CalculatorEngine

/**
 * Comprehensive Cost Management System - V2
 * يدير تكاليف جميع المواد (حديد، رمل، سن، إسمنت) لكافة العناصر الإنشائية
 */
object ComprehensiveCostManager {

    data class MaterialPrices(
        var steelPricePerTon: Double = 45000.0,
        var concretePricePerM3: Double = 1500.0,
        var sandPricePerM3: Double = 250.0,
        var gravelPricePerM3: Double = 350.0,
        var cementPricePerBag: Double = 100.0, // 50kg bag
        var laborCostPerM3: Double = 300.0
    )

    data class BOQItem(
        val name: String,
        val unit: String,
        val quantity: Double,
        val unitPrice: Double,
        val total: Double = quantity * unitPrice
    )

    data class FullBOQReport(
        val items: List<BOQItem>,
        val totalCost: Double,
        val concreteM3: Double,
        val steelKg: Double,
        val cementBags: Int
    )

    /**
     * حساب تكاليف الخزانات (خزانات علوية، أرضية، تحت أرضية)
     */
    fun calculateTankBOQ(
        result: CalculatorEngine.TankResult,
        prices: MaterialPrices
    ): FullBOQReport {
        val vol = result.concreteVolume
        val steelKg = result.steelWeight
        
        val items = mutableListOf<BOQItem>()
        items.add(BOQItem("Ready-mix Concrete (${result.code.displayName})", "m3", vol, prices.concretePricePerM3))
        items.add(BOQItem("Reinforcement Steel", "kg", steelKg, prices.steelPricePerTon / 1000.0))
        
        // Detailed breakdown
        val cementBags = (vol * 7.0).toInt() // 7 bags per m3 for tanks
        items.add(BOQItem("Cement (Estimated)", "bags", cementBags.toDouble(), prices.cementPricePerBag))
        items.add(BOQItem("Sand", "m3", vol * 0.4, prices.sandPricePerM3))
        items.add(BOQItem("Gravel/Stone", "m3", vol * 0.8, prices.gravelPricePerM3))
        items.add(BOQItem("Waterproofing/Insulation", "m2", vol * 5.0, 150.0)) // Approx 5m2 per m3 of structure
        items.add(BOQItem("Labor & Formwork", "m3", vol, prices.laborCostPerM3))

        return FullBOQReport(items, items.sumOf { it.total }, vol, steelKg, cementBags)
    }

    /**
     * حساب تكاليف حوائط السند
     */
    fun calculateRetainingWallBOQ(
        result: CalculatorEngine.RetainingWallResult,
        prices: MaterialPrices
    ): FullBOQReport {
        val vol = result.concreteVolume
        val steelKg = result.steelWeight
        
        val items = mutableListOf<BOQItem>()
        items.add(BOQItem("Concrete", "m3", vol, prices.concretePricePerM3))
        items.add(BOQItem("High Tensile Steel", "kg", steelKg, prices.steelPricePerTon / 1000.0))
        items.add(BOQItem("Excavation/Backfill", "m3", vol * 2.0, 100.0))
        items.add(BOQItem("Labor", "m3", vol, prices.laborCostPerM3))

        return FullBOQReport(items, items.sumOf { it.total }, vol, steelKg, (vol * 7).toInt())
    }

    /**
     * حساب تقديري لكامل المشروع بناء على نوعه
     */
    fun estimateProjectTotal(
        projectType: Any?, // Use Any? to avoid enum issues temporarily or pass explicit type
        landArea: Double,
        buildingRatio: Double,
        floors: Int,
        prices: MaterialPrices
    ): FullBOQReport {
        val builtArea = landArea * (buildingRatio / 100.0) * floors
        
        // Ratios (approx)
        val concreteRatio = 0.45 // Default Residential
        val steelRatio = 110.0 // kg/m3 concrete
        
        val totalVol = builtArea * concreteRatio
        val totalSteel = totalVol * steelRatio
        val totalCement = (totalVol * 7).toInt()

        val items = listOf(
            BOQItem("Total Concrete Structural Work", "m3", totalVol, prices.concretePricePerM3),
            BOQItem("Total Reinforcement Steel", "kg", totalSteel, prices.steelPricePerTon / 1000.0),
            BOQItem("Project Labor Estimation", "m2", builtArea, 500.0)
        )

        return FullBOQReport(items, items.sumOf { it.total }, totalVol, totalSteel, totalCement)
    }
}
