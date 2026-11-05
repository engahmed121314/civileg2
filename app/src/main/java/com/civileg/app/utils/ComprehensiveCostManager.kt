package com.civileg.app.utils

import android.content.Context
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.pricing.PriceManager
import com.civileg.app.pricing.Currency

/**
 * Comprehensive Cost Management System - V2
 * يدير تكاليف جميع المواد (حديد، رمل، سن، إسمنت) لكافة العناصر الإنشائية
 * مع دعم التسعير المالي المتغير (Variable Pricing) عبر PriceManager و SettingsManager.
 *
 * الأسعار تُجلب ديناميكياً من PriceManager (SharedPreferences / تحديث من الإنترنت)
 * بدلاً من كونها قيم ثابتة (hard-coded)، مما يسمح للمستخدم بتعديل أسعار المواد
 * لكل مشروع أو لكل دولة.
 */
object ComprehensiveCostManager {

    // ════════════════════════════════════════════════════════════════════════════
    // Models
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * أسعار المواد المستخدمة في حسابات التكلفة (EGP افتراضياً، قابلة للتعديل ديناميكياً).
     */
    data class MaterialPrices(
        var steelPricePerTon: Double = 28000.0,
        var concretePricePerM3: Double = 1200.0,
        var sandPricePerM3: Double = 250.0,
        var gravelPricePerM3: Double = 350.0,
        var cementPricePerBag: Double = 110.0,
        var laborCostPerM3: Double = 300.0,
        var formworkPricePerM2: Double = 280.0
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
        val cementBags: Int,
        val currency: String = "EGP",
        val exchangeRate: Double = 1.0,
        val elementType: String? = null,
        val elementName: String? = null
    )

    data class ElementBOQInput(
        val name: String,
        val elementType: String,
        val concreteVolumeM3: Double,
        val steelWeightKg: Double
    )

    // ════════════════════════════════════════════════════════════════════════════
    // Variable Pricing — بناء الأسعار ديناميكياً من PriceManager
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * يبني [MaterialPrices] من [PriceManager] بناءً على الأسعار المحفوظة في SharedPreferences
     * (أو المحدثة من الإنترنت). إذا لم يوجد سعر محفوظ يُستخدم افتراضي (fallback).
     *
     * هذه الدالة هي مدخلة "التسعير المالي المتغير" — الأسعار لا تُركّب في الكود بل تُقرأ من
     * إعدادات المستخدم ويمكن تحديثها anytime عبر PriceManager.updatePrice / updatePricesFromInternet.
     */
    fun materialPricesFromContext(context: Context): MaterialPrices {
        val pm = PriceManager

        // سعر الحديد للطن (EGP/ton) — المعرّف steel_16 كمرجع قياسي
        val steelPricePerTon = pm.getMaterialPrice(context, "steel_16")?.price ?: 28000.0

        // سعر الخرسانة للمتر المكعب (EGP/m3) — C25 كافتراضي
        val concretePricePerM3 = pm.getMaterialPrice(context, "conc_c25")?.price ?: 1200.0

        // سعر الرمل للمتر المكعب (EGP/m3)
        val sandPricePerM3 = pm.getMaterialPrice(context, "sand")?.price ?: 250.0

        // سعر السن/الزلط للمتر المكعب (EGP/m3)
        val gravelPricePerM3 = pm.getMaterialPrice(context, "agg_20")?.price ?: 350.0

        // سعر الأسمنت للطن → تحويل إلى كيس 50 كجم
        val cementPricePerTon = pm.getMaterialPrice(context, "cement_ordinary")?.price ?: 2200.0
        val cementPricePerBag = cementPricePerTon * 0.05 // 50 kg = 0.05 ton

        // سعر الشدات الخشبية للمتر المربع (EGP/m2)
        val formworkPricePerM2 = pm.getMaterialPrice(context, "formwork_wood")?.price ?: 280.0

        // تكلفة العمل للمتر المكعب (EGP/m3) — fallback إذا لم يوجد سعر عمل منفصل
        val laborCostPerM3 = pm.getMaterialPrice(context, "labor_concrete")?.price ?: 300.0

        return MaterialPrices(
            steelPricePerTon = steelPricePerTon,
            concretePricePerM3 = concretePricePerM3,
            sandPricePerM3 = sandPricePerM3,
            gravelPricePerM3 = gravelPricePerM3,
            cementPricePerBag = cementPricePerBag,
            laborCostPerM3 = laborCostPerM3,
            formworkPricePerM2 = formworkPricePerM2
        )
    }

    private fun item(name: String, unit: String, qty: Double, unitPrice: Double): BOQItem =
        BOQItem(name, unit, qty, unitPrice)

    /**
     * حساب تكاليف قاعدة منفردة (Isolated Footing).
     */
    fun calculateFootingBOQ(
        concreteVolumeM3: Double,
        steelWeightKg: Double,
        prices: MaterialPrices,
        elementName: String? = null
    ): FullBOQReport {
        val vol = concreteVolumeM3
        val steelKg = steelWeightKg
        val cementBags = (vol * 7.0).toInt()

        val items = mutableListOf<BOQItem>()
        items.add(item("Ready-mix Concrete (Footing)", "m3", vol, prices.concretePricePerM3))
        items.add(item("Reinforcement Steel", "kg", steelKg, prices.steelPricePerTon / 1000.0))
        items.add(item("Cement (Estimated)", "bags", cementBags.toDouble(), prices.cementPricePerBag))
        items.add(item("Sand", "m3", vol * 0.4, prices.sandPricePerM3))
        items.add(item("Gravel/Stone", "m3", vol * 0.8, prices.gravelPricePerM3))
        items.add(item("Wooden Formwork", "m2", vol * 5.0, prices.formworkPricePerM2))
        items.add(item("Excavation & Backfill", "m3", vol * 1.5, 100.0))
        items.add(item("Labor", "m3", vol, prices.laborCostPerM3))

        return FullBOQReport(
            items, items.sumOf { it.total }, vol, steelKg, cementBags,
            elementType = "FOOTING", elementName = elementName
        )
    }

    /**
     * حساب تكاليف عمود (Column).
     */
    fun calculateColumnBOQ(
        concreteVolumeM3: Double,
        steelWeightKg: Double,
        prices: MaterialPrices,
        elementName: String? = null
    ): FullBOQReport {
        val vol = concreteVolumeM3
        val steelKg = steelWeightKg
        val cementBags = (vol * 7.0).toInt()

        val items = mutableListOf<BOQItem>()
        items.add(item("Ready-mix Concrete (Column)", "m3", vol, prices.concretePricePerM3))
        items.add(item("Reinforcement Steel", "kg", steelKg, prices.steelPricePerTon / 1000.0))
        items.add(item("Cement (Estimated)", "bags", cementBags.toDouble(), prices.cementPricePerBag))
        items.add(item("Sand", "m3", vol * 0.4, prices.sandPricePerM3))
        items.add(item("Gravel/Stone", "m3", vol * 0.8, prices.gravelPricePerM3))
        items.add(item("Column Formwork", "m2", vol * 6.0, prices.formworkPricePerM2))
        items.add(item("Labor", "m3", vol, prices.laborCostPerM3))

        return FullBOQReport(
            items, items.sumOf { it.total }, vol, steelKg, cementBags,
            elementType = "COLUMN", elementName = elementName
        )
    }

    /**
     * حساب تكاليف كورة (Beam).
     */
    fun calculateBeamBOQ(
        concreteVolumeM3: Double,
        steelWeightKg: Double,
        prices: MaterialPrices,
        elementName: String? = null
    ): FullBOQReport {
        val vol = concreteVolumeM3
        val steelKg = steelWeightKg
        val cementBags = (vol * 7.0).toInt()

        val items = mutableListOf<BOQItem>()
        items.add(item("Ready-mix Concrete (Beam)", "m3", vol, prices.concretePricePerM3))
        items.add(item("Reinforcement Steel", "kg", steelKg, prices.steelPricePerTon / 1000.0))
        items.add(item("Cement (Estimated)", "bags", cementBags.toDouble(), prices.cementPricePerBag))
        items.add(item("Sand", "m3", vol * 0.4, prices.sandPricePerM3))
        items.add(item("Gravel/Stone", "m3", vol * 0.8, prices.gravelPricePerM3))
        items.add(item("Beam Formwork", "m2", vol * 5.0, prices.formworkPricePerM2))
        items.add(item("Labor", "m3", vol, prices.laborCostPerM3))

        return FullBOQReport(
            items, items.sumOf { it.total }, vol, steelKg, cementBags,
            elementType = "BEAM", elementName = elementName
        )
    }

    /**
     * حساب تكاليف بلاطة (Slab).
     */
    fun calculateSlabBOQ(
        concreteVolumeM3: Double,
        steelWeightKg: Double,
        prices: MaterialPrices,
        elementName: String? = null
    ): FullBOQReport {
        val vol = concreteVolumeM3
        val steelKg = steelWeightKg
        val cementBags = (vol * 7.0).toInt()

        val items = mutableListOf<BOQItem>()
        items.add(item("Ready-mix Concrete (Slab)", "m3", vol, prices.concretePricePerM3))
        items.add(item("Reinforcement Steel", "kg", steelKg, prices.steelPricePerTon / 1000.0))
        items.add(item("Cement (Estimated)", "bags", cementBags.toDouble(), prices.cementPricePerBag))
        items.add(item("Sand", "m3", vol * 0.4, prices.sandPricePerM3))
        items.add(item("Gravel/Stone", "m3", vol * 0.8, prices.gravelPricePerM3))
        items.add(item("Slab Formwork", "m2", vol * 6.0, prices.formworkPricePerM2))
        items.add(item("Labor", "m3", vol, prices.laborCostPerM3))

        return FullBOQReport(
            items, items.sumOf { it.total }, vol, steelKg, cementBags,
            elementType = "SLAB", elementName = elementName
        )
    }

    /**
     * حساب تكاليف درج (Staircase).
     */
    fun calculateStaircaseBOQ(
        concreteVolumeM3: Double,
        steelWeightKg: Double,
        prices: MaterialPrices,
        elementName: String? = null
    ): FullBOQReport {
        val vol = concreteVolumeM3
        val steelKg = steelWeightKg
        val cementBags = (vol * 7.0).toInt()

        val items = mutableListOf<BOQItem>()
        items.add(item("Ready-mix Concrete (Stair)", "m3", vol, prices.concretePricePerM3))
        items.add(item("Reinforcement Steel", "kg", steelKg, prices.steelPricePerTon / 1000.0))
        items.add(item("Cement (Estimated)", "bags", cementBags.toDouble(), prices.cementPricePerBag))
        items.add(item("Sand", "m3", vol * 0.4, prices.sandPricePerM3))
        items.add(item("Gravel/Stone", "m3", vol * 0.8, prices.gravelPricePerM3))
        items.add(item("Stair Formwork", "m2", vol * 5.0, prices.formworkPricePerM2))
        items.add(item("Labor", "m3", vol, prices.laborCostPerM3))

        return FullBOQReport(
            items, items.sumOf { it.total }, vol, steelKg, cementBags,
            elementType = "STAIR", elementName = elementName
        )
    }

    /**
     * حساب تكاليف حائط قص (Shear Wall).
     */
    fun calculateShearWallBOQ(
        concreteVolumeM3: Double,
        steelWeightKg: Double,
        prices: MaterialPrices,
        elementName: String? = null
    ): FullBOQReport {
        val vol = concreteVolumeM3
        val steelKg = steelWeightKg
        val cementBags = (vol * 7.0).toInt()

        val items = mutableListOf<BOQItem>()
        items.add(item("Ready-mix Concrete (Shear Wall)", "m3", vol, prices.concretePricePerM3))
        items.add(item("High Tensile Steel", "kg", steelKg, prices.steelPricePerTon / 1000.0))
        items.add(item("Cement (Estimated)", "bags", cementBags.toDouble(), prices.cementPricePerBag))
        items.add(item("Sand", "m3", vol * 0.4, prices.sandPricePerM3))
        items.add(item("Gravel/Stone", "m3", vol * 0.8, prices.gravelPricePerM3))
        items.add(item("Shear Wall Formwork (2 faces)", "m2", vol * 4.0, prices.formworkPricePerM2))
        items.add(item("Labor", "m3", vol, prices.laborCostPerM3))

        return FullBOQReport(
            items, items.sumOf { it.total }, vol, steelKg, cementBags,
            elementType = "SHEAR_WALL", elementName = elementName
        )
    }

    /**
     * حساب تكاليف أسس ركائز (Pile Foundation).
     */
    fun calculatePileFoundationBOQ(
        concreteVolumeM3: Double,
        steelWeightKg: Double,
        prices: MaterialPrices,
        elementName: String? = null
    ): FullBOQReport {
        val vol = concreteVolumeM3
        val steelKg = steelWeightKg
        val cementBags = (vol * 7.0).toInt()

        val items = mutableListOf<BOQItem>()
        items.add(item("Ready-mix Concrete (Pile)", "m3", vol, prices.concretePricePerM3))
        items.add(item("Reinforcement Steel", "kg", steelKg, prices.steelPricePerTon / 1000.0))
        items.add(item("Cement (Estimated)", "bags", cementBags.toDouble(), prices.cementPricePerBag))
        items.add(item("Sand", "m3", vol * 0.4, prices.sandPricePerM3))
        items.add(item("Gravel/Stone", "m3", vol * 0.8, prices.gravelPricePerM3))
        items.add(item("Pile Boring/Excavation", "m3", vol * 1.2, 120.0))
        items.add(item("Pile Formwork", "m2", vol * 4.0, prices.formworkPricePerM2))
        items.add(item("Labor", "m3", vol, prices.laborCostPerM3))

        return FullBOQReport(
            items, items.sumOf { it.total }, vol, steelKg, cementBags,
            elementType = "PILE", elementName = elementName
        )
    }

    /**
     * حساب تكاليف بلاطة مسطحة (Flat Slab).
     */
    fun calculateFlatSlabBOQ(
        concreteVolumeM3: Double,
        steelWeightKg: Double,
        prices: MaterialPrices,
        elementName: String? = null
    ): FullBOQReport {
        val vol = concreteVolumeM3
        val steelKg = steelWeightKg
        val cementBags = (vol * 7.0).toInt()

        val items = mutableListOf<BOQItem>()
        items.add(item("Ready-mix Concrete (Flat Slab)", "m3", vol, prices.concretePricePerM3))
        items.add(item("Reinforcement Steel", "kg", steelKg, prices.steelPricePerTon / 1000.0))
        items.add(item("Cement (Estimated)", "bags", cementBags.toDouble(), prices.cementPricePerBag))
        items.add(item("Sand", "m3", vol * 0.4, prices.sandPricePerM3))
        items.add(item("Gravel/Stone", "m3", vol * 0.8, prices.gravelPricePerM3))
        items.add(item("Flat Slab Formwork", "m2", vol * 6.0, prices.formworkPricePerM2))
        items.add(item("Labor", "m3", vol, prices.laborCostPerM3))

        return FullBOQReport(
            items, items.sumOf { it.total }, vol, steelKg, cementBags,
            elementType = "FLAT_SLAB", elementName = elementName
        )
    }

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
        items.add(item("Ready-mix Concrete (${result.code.displayName})", "m3", vol, prices.concretePricePerM3))
        items.add(item("Reinforcement Steel", "kg", steelKg, prices.steelPricePerTon / 1000.0))

        // Detailed breakdown
        val cementBags = (vol * 7.0).toInt() // 7 bags per m3 for tanks
        items.add(item("Cement (Estimated)", "bags", cementBags.toDouble(), prices.cementPricePerBag))
        items.add(item("Sand", "m3", vol * 0.4, prices.sandPricePerM3))
        items.add(item("Gravel/Stone", "m3", vol * 0.8, prices.gravelPricePerM3))
        items.add(item("Waterproofing/Insulation", "m2", vol * 5.0, 150.0)) // Approx 5m2 per m3 of structure
        items.add(item("Labor & Formwork", "m3", vol, prices.laborCostPerM3))

        return FullBOQReport(items, items.sumOf { it.total }, vol, steelKg, cementBags,
            elementType = "TANK", elementName = "Tank")
    }

    /**
     * حساب تكاليف حوائط السند (Retaining Wall)
     */
    fun calculateRetainingWallBOQ(
        result: CalculatorEngine.RetainingWallResult,
        prices: MaterialPrices
    ): FullBOQReport {
        val vol = result.concreteVolume
        val steelKg = result.steelWeight

        val items = mutableListOf<BOQItem>()
        items.add(item("Concrete", "m3", vol, prices.concretePricePerM3))
        items.add(item("High Tensile Steel", "kg", steelKg, prices.steelPricePerTon / 1000.0))
        items.add(item("Excavation/Backfill", "m3", vol * 2.0, 100.0))
        items.add(item("Labor", "m3", vol, prices.laborCostPerM3))

        return FullBOQReport(items, items.sumOf { it.total }, vol, steelKg, (vol * 7).toInt(),
            elementType = "RETAINING_WALL", elementName = "Retaining Wall")
    }

    /**
     * حساب تقديري لكامل المشروع بناء على مساحته ونسبة البناء وعدد الطوابق
     * (نفس المنطق السابق، مع إمكانية تمرير أسعار متغيرة).
     */
    fun estimateProjectTotal(
        projectType: Any?,
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
            item("Total Concrete Structural Work", "m3", totalVol, prices.concretePricePerM3),
            item("Total Reinforcement Steel", "kg", totalSteel, prices.steelPricePerTon / 1000.0),
            item("Project Labor Estimation", "m2", builtArea, 500.0)
        )

        return FullBOQReport(items, items.sumOf { it.total }, totalVol, totalSteel, totalCement,
            elementType = "PROJECT", elementName = "Project Estimate")
    }

    /**
     * حساب تكلفة مشروع كامل من قائمة العناصر الإنشائية، مع تجميع التكاليف حسب النوع.
     */
    fun calculateProjectBOQ(
        elements: List<ElementBOQInput>,
        prices: MaterialPrices
    ): FullBOQReport {
        var totalConcrete = 0.0
        var totalSteel = 0.0
        var totalCement = 0
        val allItems = mutableListOf<BOQItem>()

        elements.forEach { element ->
            val report = when (element.elementType.uppercase()) {
                "FOOTING" -> calculateFootingBOQ(element.concreteVolumeM3, element.steelWeightKg, prices, element.name)
                "COLUMN" -> calculateColumnBOQ(element.concreteVolumeM3, element.steelWeightKg, prices, element.name)
                "BEAM" -> calculateBeamBOQ(element.concreteVolumeM3, element.steelWeightKg, prices, element.name)
                "SLAB" -> calculateSlabBOQ(element.concreteVolumeM3, element.steelWeightKg, prices, element.name)
                "STAIR", "STAIRCASE" -> calculateStaircaseBOQ(element.concreteVolumeM3, element.steelWeightKg, prices, element.name)
                "SHEAR_WALL" -> calculateShearWallBOQ(element.concreteVolumeM3, element.steelWeightKg, prices, element.name)
                "PILE", "PILE_FOUNDATION" -> calculatePileFoundationBOQ(element.concreteVolumeM3, element.steelWeightKg, prices, element.name)
                "FLAT_SLAB" -> calculateFlatSlabBOQ(element.concreteVolumeM3, element.steelWeightKg, prices, element.name)
                "TANK" -> {
                    // خزانات لا تدخل عبر هذا المسار عادة، لكن نتعامل معها كخرسانة عادية
                    calculateFootingBOQ(element.concreteVolumeM3, element.steelWeightKg, prices, element.name)
                }
                "RETAINING_WALL" -> {
                    // Retaining walls share the same concrete/steel BOQ as a footing
                    calculateFootingBOQ(element.concreteVolumeM3, element.steelWeightKg, prices, element.name)
                }
                else -> calculateFootingBOQ(element.concreteVolumeM3, element.steelWeightKg, prices, element.name)
            }

            allItems.addAll(report.items)
            totalConcrete += report.concreteM3
            totalSteel += report.steelKg
            totalCement += report.cementBags
        }

        return FullBOQReport(
            items = allItems,
            totalCost = allItems.sumOf { it.total },
            concreteM3 = totalConcrete,
            steelKg = totalSteel,
            cementBags = totalCement,
            elementType = "PROJECT", elementName = "Project Summary"
        )
    }

    /**
     * تحويل تقرير التكلفة إلى عملة أخرى باستخدام سعر الصرف المحدد.
     * إذا كانت [targetCurrency] مساوية للعملة الحالية يُعاد التقرير بدون تغيير.
     */
    fun convertReport(
        report: FullBOQReport,
        targetCurrency: Currency,
        rate: Double
    ): FullBOQReport {
        if (report.currency.equals(targetCurrency.name, ignoreCase = true)) {
            return report
        }
        val convertedItems = report.items.map { item ->
            item.copy(unitPrice = item.unitPrice * rate, total = item.total * rate)
        }
        return report.copy(
            items = convertedItems,
            totalCost = report.totalCost * rate,
            currency = targetCurrency.name,
            exchangeRate = rate
        )
    }


    // ════════════════════════════════════════════════════════════════════════════
    // Extended Models — تكاليف غير إنشائية وتقارير مقارنية
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * فئة التكاليف غير الإنشائية (نقل، معدات، تراخيص، استشارات، إلخ).
     */
    data class NonStructuralCostItem(
        val category: String,           // "نقل", "معدات", "تراخيص", "استشارات", "تأمين"
        val itemName: String,
        val quantity: Double,
        val unit: String,
        val unitPrice: Double,
        val total: Double = quantity * unitPrice
    )

    /**
     * تقرير التكلفة الشامل للمشروع بما في ذلك التكاليف غير الإنشائية.
     */
    data class ComprehensiveProjectCost(
        val structuralCost: FullBOQReport,
        val nonStructuralItems: List<NonStructuralCostItem>,
        val totalNonStructural: Double,
        val grandTotal: Double,
        val currency: String,
        val exchangeRate: Double,
        val projectName: String?,
        val projectLocation: String?,
        val designCode: String?,
        val preparedBy: String?,
        val date: Long = System.currentTimeMillis()
    )

    /**
     * مدخل للتكلفة غير الإنشائية.
     */
    data class NonStructuralInput(
        val category: String,
        val itemName: String,
        val quantity: Double,
        val unit: String,
        val unitPrice: Double
    )

    /**
     * تقرير مقارني بين تصميمين أو خيارين مختلفين.
     */
    data class ComparativeCostReport(
        val optionA: FullBOQReport,
        val optionB: FullBOQReport,
        val difference: Double,           // B - A
        val differencePercent: Double,    // % differenza
        val cheaperOption: String,        // "A" أو "B" أو "EQUAL"
        val currency: String
    )

    /**
     * تكلفة الصيانة السنوية المقدرة للعنصر الإنشائي.
     */
    data class MaintenanceCost(
        val elementType: String,
        val annualInspectionCost: Double,
        val annualRepairCost: Double,
        val totalAnnualMaintenance: Double,
        val tenYearMaintenance: Double,
        val currency: String
    )

    // ════════════════════════════════════════════════════════════════════════════
    // Non-Structural Cost Calculations
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * حساب التكاليف غير الإنشائية من قائمة المدخلات.
     */
    fun calculateNonStructuralCosts(
        items: List<NonStructuralInput>,
        currency: String = "EGP"
    ): List<NonStructuralCostItem> {
        return items.map { input ->
            NonStructuralCostItem(
                category = input.category,
                itemName = input.itemName,
                quantity = input.quantity,
                unit = input.unit,
                unitPrice = input.unitPrice,
                total = input.quantity * input.unitPrice
            )
        }
    }

    /**
     * بناء تقرير تكلفة شامل للمشروع incluant التكاليف الإنشائية وغير الإنشائية.
     */
    fun calculateComprehensiveProjectCost(
        structuralReport: FullBOQReport,
        nonStructuralItems: List<NonStructuralCostItem>,
        projectName: String? = null,
        projectLocation: String? = null,
        designCode: String? = null,
        preparedBy: String? = null
    ): ComprehensiveProjectCost {
        val totalNonStructural = nonStructuralItems.sumOf { it.total }
        val grandTotal = structuralReport.totalCost + totalNonStructural

        return ComprehensiveProjectCost(
            structuralCost = structuralReport,
            nonStructuralItems = nonStructuralItems,
            totalNonStructural = totalNonStructural,
            grandTotal = grandTotal,
            currency = structuralReport.currency,
            exchangeRate = structuralReport.exchangeRate,
            projectName = projectName,
            projectLocation = projectLocation,
            designCode = designCode,
            preparedBy = preparedBy
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Comparative Cost Reports — تقارير مقارنية بين خيارات التصميم
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * مقارنة بين تقريرين تكلفيين (خيار A وخيار B) وإرجاع تقرير مقارني.
     */
    fun compareCosts(
        optionA: FullBOQReport,
        optionB: FullBOQReport
    ): ComparativeCostReport {
        val difference = optionB.totalCost - optionA.totalCost
        val differencePercent = if (optionA.totalCost > 0) {
            (difference / optionA.totalCost) * 100.0
        } else 0.0

        val cheaperOption = when {
            difference > 0 -> "A" // A أرخص
            difference < 0 -> "B" // B أرخص
            else -> "EQUAL"
        }

        return ComparativeCostReport(
            optionA = optionA,
            optionB = optionB,
            difference = difference,
            differencePercent = differencePercent,
            cheaperOption = cheaperOption,
            currency = optionA.currency
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Maintenance Cost Estimation — تقدير تكاليف الصيانة السنوية
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * تقدير تكلفة الصيانة السنوية لعنصر إنشائي بناءً على نوعه وحجم الخرسانة.
     * الافتراضات: الصيانة السنوية ≈ 0.5% من تكلفة الإنشاء للعناصر الخرسانية العادية،
     * و 1.0% للعناصر المعرضة للعوامل الجوية (حوائط، خزانات).
     */
    fun estimateMaintenanceCost(
        elementType: String,
        concreteVolumeM3: Double,
        concretePricePerM3: Double,
        exposureFactor: Double = 1.0, // 1.0 عادي، 1.5 معرض للرطوبة، 2.0 ملحوي
        currency: String = "EGP"
    ): MaintenanceCost {
        val constructionCost = concreteVolumeM3 * concretePricePerM3
        val annualInspection = constructionCost * 0.005 * exposureFactor // 0.5% للفحص السنوي
        val annualRepair = constructionCost * 0.01 * exposureFactor // 1.0% للإصلاح السنوي
        val totalAnnual = annualInspection + annualRepair
        val tenYear = totalAnnual * 10.0

        return MaintenanceCost(
            elementType = elementType,
            annualInspectionCost = annualInspection,
            annualRepairCost = annualRepair,
            totalAnnualMaintenance = totalAnnual,
            tenYearMaintenance = tenYear,
            currency = currency
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Price Sensitivity Analysis — تحليل حساسية الأسعار
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * تحليل تأثير تغيير أسعار المواد على تكلفة المشروع الإجمالية.
     * يعيد قائمة بالتأثير النسبي لكل مادة رئيسية.
     */
    data class PriceSensitivityItem(
        val material: String,
        val basePrice: Double,
        val priceChangePercent: Double,
        val costImpactPercent: Double,
        val newTotalCost: Double
    )

    fun analyzePriceSensitivity(
        report: FullBOQReport,
        steelPriceChangePercent: Double = 0.0,
        concretePriceChangePercent: Double = 0.0,
        laborPriceChangePercent: Double = 0.0
    ): List<PriceSensitivityItem> {
        val results = mutableListOf<PriceSensitivityItem>()

        // حساب التكلفة الأساسية للمواد الرئيسية من التقرير
        val baseSteelCost = report.items.filter { it.name.contains("Steel", ignoreCase = true) }.sumOf { it.total }
        val baseConcreteCost = report.items.filter { it.name.contains("Concrete", ignoreCase = true) }.sumOf { it.total }
        val baseLaborCost = report.items.filter { it.name.contains("Labor", ignoreCase = true) }.sumOf { it.total }

        val impacts = listOf(
            Triple("حديد (Steel)", baseSteelCost, steelPriceChangePercent),
            Triple("خرسانة (Concrete)", baseConcreteCost, concretePriceChangePercent),
            Triple("أعمال (Labor)", baseLaborCost, laborPriceChangePercent)
        )

        impacts.forEach { (material, baseCost, changePercent) ->
            if (baseCost > 0) {
                val costImpact = (changePercent / 100.0) * baseCost
                val costImpactPercent = (costImpact / report.totalCost) * 100.0
                val newTotal = report.totalCost + costImpact
                results.add(PriceSensitivityItem(
                    material = material,
                    basePrice = baseCost,
                    priceChangePercent = changePercent,
                    costImpactPercent = costImpactPercent,
                    newTotalCost = newTotal
                ))
            }
        }

        return results.sortedByDescending { it.costImpactPercent }
    }
}
