package com.civileg.app.utils

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class EstimationEngine @Inject constructor(
    private val settingsManager: SettingsManager
) {

    enum class ProjectCategory { FULL_PROJECT, APARTMENT_FINISHING, INVESTMENT_STUDY, SPECIFIC_ITEM }
    
    enum class FullProjectType(val displayName: String) {
        RESIDENTIAL("عقار سكني"),
        COMMERCIAL_RESIDENTIAL("سكني تجاري"),
        HOSPITAL("مستشفى"),
        MEDICAL_UNIT("وحدات طبية"),
        FACTORY("مصنع / هنجر")
    }

    enum class FactoryStructureType(val displayName: String) { 
        STEEL_HANGAR("هنجر معدني (Steel)"), 
        CONCRETE("هيكل خرساني كامل"), 
        BOTH("مختلط (أعمدة خرسانية وسقف معدني)") 
    }

    @Parcelize
    data class EstimationResult(
        val category: ProjectCategory,
        val totalCost: Double = 0.0,
        val items: List<EstimateItem> = emptyList(),
        val technicalDetails: List<String> = emptyList(),
        val investmentData: InvestmentData? = null,
        val currencySymbol: String = "EGP"
    ) : Parcelable

    @Parcelize
    data class EstimateItem(
        val name: String,
        val quantity: Double,
        val unit: String,
        val unitPrice: Double,
        val totalPrice: Double,
        val category: String 
    ) : Parcelable

    @Parcelize
    data class InvestmentData(
        val estimatedUnits: Int = 0,
        val costBreakdown: Map<String, Double> = emptyMap(),
        val roi: Double = 0.0,
        val paybackPeriodYears: Double = 0.0,
        val landCost: Double = 0.0,
        val constructionCost: Double = 0.0,
        val expectedRevenue: Double = 0.0,
        val netProfit: Double = 0.0,
        val profitMargin: Double = 0.0,
        val constructionDurationMonths: Int = 0,
        val appreciationRate: Double = 15.0 
    ) : Parcelable

    fun estimateFullProject(
        type: FullProjectType,
        area: Double,
        floors: Int,
        hasBasement: Boolean,
        factoryType: FactoryStructureType? = null,
        landPricePerM2: Double = 0.0,
        expectedSellingPricePerM2: Double = 0.0,
        currencySymbol: String = "EGP"
    ): EstimationResult {
        if (area <= 0) return EstimationResult(category = ProjectCategory.FULL_PROJECT, currencySymbol = currencySymbol)

        if (type == FullProjectType.FACTORY) {
            val factoryRes = estimateFactory(area, factoryType ?: FactoryStructureType.STEEL_HANGAR)
            return factoryRes.copy(currencySymbol = currencySymbol)
        }

        val basementFactor = if (hasBasement) 1.25 else 0.0
        val totalBuiltArea = area * (floors + basementFactor) 
        val resultItems = mutableListOf<EstimateItem>()
        
        val concreteRate = if(type == FullProjectType.HOSPITAL) 0.68 else 0.45
        val steelRate = if(type == FullProjectType.HOSPITAL) 155.0 else 100.0
        
        val concreteVol = totalBuiltArea * concreteRate
        val steelWeightKg = concreteVol * steelRate
        
        val concretePrice = settingsManager.concretePrice
        val steelPrice = settingsManager.steelPrice
        
        val concreteCost = concreteVol * concretePrice
        val steelCost = (steelWeightKg / 1000.0) * steelPrice
        val skeletonCost = concreteCost + steelCost
        
        resultItems.add(EstimateItem("خرسانات الهيكل الإنشائي", concreteVol, "m³", concretePrice, concreteCost, "Structural"))
        resultItems.add(EstimateItem("حديد التسليح", steelWeightKg / 1000.0, "Ton", steelPrice, steelCost, "Structural"))
        
        val finishingRate = when(type) {
            FullProjectType.HOSPITAL -> 13500.0
            FullProjectType.COMMERCIAL_RESIDENTIAL -> 9000.0
            else -> 7000.0
        }
        
        val finishingCost = totalBuiltArea * finishingRate
        val plumbingCost = finishingCost * 0.15
        val electricCost = finishingCost * 0.12
        val masonryCost = finishingCost * 0.10
        val otherFinishes = finishingCost - (plumbingCost + electricCost + masonryCost)

        resultItems.add(EstimateItem("أعمال المباني والتقسيم", totalBuiltArea, "m²", finishingRate * 0.10, masonryCost, "Finishing"))
        resultItems.add(EstimateItem("تأسيس كهرباء وإنارة", totalBuiltArea, "m²", finishingRate * 0.12, electricCost, "Finishing"))
        resultItems.add(EstimateItem("تأسيس سباكة وعزل", totalBuiltArea, "m²", finishingRate * 0.15, plumbingCost, "Finishing"))
        resultItems.add(EstimateItem("دهانات وأرضيات وديكور", totalBuiltArea, "m²", finishingRate * 0.63, otherFinishes, "Finishing"))

        val totalConstructionCost = skeletonCost + finishingCost
        val totalLandCost = area * landPricePerM2
        val totalInvestment = totalConstructionCost + totalLandCost

        val netSellableArea = totalBuiltArea * 0.82 
        val effectiveSellingPrice = if (expectedSellingPricePerM2 > 0) expectedSellingPricePerM2 else (totalInvestment / netSellableArea.coerceAtLeast(1.0)) * 1.40
        
        val expectedRevenue = netSellableArea * effectiveSellingPrice
        val netProfit = expectedRevenue - totalInvestment
        val roi = if (totalInvestment > 0) (netProfit / totalInvestment) * 100 else 0.0

        return EstimationResult(
            category = ProjectCategory.FULL_PROJECT,
            totalCost = totalConstructionCost,
            items = resultItems,
            technicalDetails = listOf(
                "إجمالي المسطحات المبنية: ${totalBuiltArea.toInt()} م٢",
                "حجم الخرسانة التقديري: ${concreteVol.toInt()} م٣",
                "وزن الحديد التقديري: ${(steelWeightKg/1000).toInt()} طن",
                "المساحة البيعية الصافية: ${netSellableArea.toInt()} م٢",
                "المدة الزمنية المقدرة: ${14 + floors*2} شهر"
            ),
            investmentData = InvestmentData(
                estimatedUnits = (netSellableArea / 135).toInt().coerceAtLeast(1),
                costBreakdown = mapOf(
                    "قيمة الأرض" to totalLandCost,
                    "الهيكل الخرساني" to concreteCost,
                    "حديد التسليح" to steelCost,
                    "التشطيبات" to finishingCost
                ),
                roi = roi,
                paybackPeriodYears = if (expectedRevenue > 0) (totalInvestment / (expectedRevenue / ((14 + floors*2)/12.0))).coerceIn(2.0, 10.0) else 0.0,
                landCost = totalLandCost,
                constructionCost = totalConstructionCost,
                expectedRevenue = expectedRevenue,
                netProfit = netProfit,
                profitMargin = if(expectedRevenue > 0) (netProfit / expectedRevenue) * 100 else 0.0,
                constructionDurationMonths = 14 + floors*2
            ),
            currencySymbol = currencySymbol
        )
    }

    private fun estimateFactory(area: Double, type: FactoryStructureType): EstimationResult {
        if (area <= 0) return EstimationResult(category = ProjectCategory.FULL_PROJECT)

        val items = mutableListOf<EstimateItem>()
        val techDetails = mutableListOf<String>()
        val steelPrice = settingsManager.steelPrice
        val concretePrice = settingsManager.concretePrice

        var steelCost = 0.0
        var concreteCost = 0.0
        var claddingCost = 0.0

        if (type == FactoryStructureType.STEEL_HANGAR || type == FactoryStructureType.BOTH) {
            val totalWeightKg = area * 60.0 
            val steelFabricatedPrice = steelPrice * 1.4 
            steelCost = (totalWeightKg / 1000.0) * steelFabricatedPrice
            claddingCost = area * 1.25 * 980.0
            
            items.add(EstimateItem("هيكل معدني مصنع", totalWeightKg / 1000.0, "Ton", steelFabricatedPrice, steelCost, "Steel"))
            items.add(EstimateItem("أغطية ساندوتش بانل", area * 1.25, "m²", 980.0, claddingCost, "Cladding"))
            techDetails.add("وزن المنشأ المعدني: ${"%.2f".format(totalWeightKg/1000.0)} طن")
        }

        if (type == FactoryStructureType.CONCRETE || type == FactoryStructureType.BOTH) {
            val concVol = if(type == FactoryStructureType.BOTH) area * 0.25 else area * 0.75
            concreteCost = concVol * concretePrice * 2.5
            items.add(EstimateItem("خرسانات وأرضيات ليزر", concVol, "m³", concretePrice * 2.5, concreteCost, "Concrete"))
        }

        val total = items.sumOf { it.totalPrice }
        return EstimationResult(
            category = ProjectCategory.FULL_PROJECT,
            totalCost = total,
            items = items,
            technicalDetails = techDetails,
            investmentData = InvestmentData(
                constructionCost = total, 
                costBreakdown = mapOf(
                    "حديد معدني" to steelCost,
                    "تغطيات" to claddingCost,
                    "أعمال خرسانية" to concreteCost
                ),
                roi = 35.0,
                netProfit = total * 0.40,
                constructionDurationMonths = 6
            )
        )
    }

    fun estimateApartmentFinishingPro(area: Double, currencySymbol: String = "EGP"): EstimationResult {
        if (area <= 0) return EstimationResult(category = ProjectCategory.APARTMENT_FINISHING, currencySymbol = currencySymbol)
        val items = mutableListOf<EstimateItem>()
        val rates = mapOf(
            "أعمال السباكة والعزل" to 450.0,
            "أعمال الكهرباء والسمارت" to 600.0,
            "المحارة والمصيص" to 350.0,
            "الدهانات والديكور" to 1100.0,
            "الأرضيات (بورسلين/HDF)" to 1200.0,
            "النجارة والأبواب" to 700.0
        )
        rates.forEach { (name, rate) ->
            items.add(EstimateItem(name, area, "m²", rate, area * rate, "Finishing"))
        }
        val total = items.sumOf { it.totalPrice }
        return EstimationResult(
            category = ProjectCategory.APARTMENT_FINISHING,
            totalCost = total,
            items = items,
            technicalDetails = listOf("المساحة: $area م٢", "مستوى التشطيب: الترا سوبر لوكس", "مدة التنفيذ: 4 أشهر"),
            investmentData = InvestmentData(constructionCost = total, netProfit = total * 0.5, roi = 50.0),
            currencySymbol = currencySymbol
        )
    }

    fun estimateSpecificItem(name: String, qty: Double, price: Double, currencySymbol: String = "EGP"): EstimationResult {
        val total = qty * price
        return EstimationResult(
            category = ProjectCategory.SPECIFIC_ITEM,
            totalCost = total,
            items = listOf(EstimateItem(name, qty, "Unit", price, total, "Manual")),
            currencySymbol = currencySymbol
        )
    }
}
