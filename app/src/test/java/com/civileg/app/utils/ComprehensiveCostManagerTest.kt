package com.civileg.app.utils

import com.civileg.app.pricing.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبار شامل لنظام التكلفة الشامل (ComprehensiveCostManager V2).
 * يغطي: التسعير المتغير، تقارير BOQ، التكاليف غير الإنشائية، التقارير المقارنية، الصيانة، حساسية الأسعار.
 */
class ComprehensiveCostManagerTest {

    // ════════════════════════════════════════════════════════════════════════════
    // أسعار اختبار افتراضية
    // ════════════════════════════════════════════════════════════════════════════

    private val testPrices = ComprehensiveCostManager.MaterialPrices(
        steelPricePerTon = 30000.0,
        concretePricePerM3 = 1300.0,
        sandPricePerM3 = 280.0,
        gravelPricePerM3 = 380.0,
        cementPricePerBag = 120.0,
        laborCostPerM3 = 350.0,
        formworkPricePerM2 = 300.0
    )

    // ════════════════════════════════════════════════════════════════════════════
    // 1. تقارير BOQ للعناصر الفردية
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun footingBOQReportHasCorrectStructure() {
        val report = ComprehensiveCostManager.calculateFootingBOQ(
            concreteVolumeM3 = 10.0,
            steelWeightKg = 800.0,
            prices = testPrices,
            elementName = "Footing F1"
        )

        assertEquals("FOOTING", report.elementType)
        assertEquals("Footing F1", report.elementName)
        assertEquals(10.0, report.concreteM3, 0.001)
        assertEquals(800.0, report.steelKg, 0.001)
        assertTrue("يجب أن تحتوي التقرير على بنود تكلفة", report.items.isNotEmpty())
        assertTrue("التكلفة الإجمالية يجب أن تكون موجبة", report.totalCost > 0.0)
        assertTrue("الأسمنت يجب أن يُحسب", report.cementBags > 0)
    }

    @Test
    fun columnBOQIncludesFormwork() {
        val report = ComprehensiveCostManager.calculateColumnBOQ(
            concreteVolumeM3 = 5.0,
            steelWeightKg = 400.0,
            prices = testPrices,
            elementName = "Column C1"
        )

        assertEquals("COLUMN", report.elementType)
        // التحقق من وجود بند الشدات (formwork)
        val hasFormwork = report.items.any { it.name.contains("Formwork", ignoreCase = true) }
        assertTrue("تقرير العمود يجب أن يتضمن بند الشدات", hasFormwork)
    }

    @Test
    fun shearWallBOQHasTwoSidesFormwork() {
        val report = ComprehensiveCostManager.calculateShearWallBOQ(
            concreteVolumeM3 = 15.0,
            steelWeightKg = 1200.0,
            prices = testPrices,
            elementName = "SW-01"
        )

        assertEquals("SHEAR_WALL", report.elementType)
        // حوائط القص لها شدات على وجهين
        assertTrue("تقرير حائط القص يجب أن يذكر شدات وجهين", report.items.any { it.name.contains("2 faces", ignoreCase = true) })
    }

    @Test
    fun pileFoundationBOQIncludesBoring() {
        val report = ComprehensiveCostManager.calculatePileFoundationBOQ(
            concreteVolumeM3 = 20.0,
            steelWeightKg = 1500.0,
            prices = testPrices,
            elementName = "Pile Group"
        )

        assertEquals("PILE", report.elementType)
        val hasBoring = report.items.any { it.name.contains("Boring", ignoreCase = true) || it.name.contains("حفر", ignoreCase = true) }
        assertTrue("تقرير الركائز يجب أن يتضمن بند الحفر/البoring", hasBoring)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 2. تقرير المشروع الشامل (Project BOQ)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun projectBOQAggregatesMultipleElements() {
        val elements = listOf(
            ComprehensiveCostManager.ElementBOQInput("Footing 1", "FOOTING", 10.0, 800.0),
            ComprehensiveCostManager.ElementBOQInput("Column 1", "COLUMN", 5.0, 400.0),
            ComprehensiveCostManager.ElementBOQInput("Beam 1", "BEAM", 8.0, 600.0),
            ComprehensiveCostManager.ElementBOQInput("Slab 1", "SLAB", 12.0, 900.0)
        )

        val report = ComprehensiveCostManager.calculateProjectBOQ(elements, testPrices)

        assertEquals("PROJECT", report.elementType)
        assertEquals(35.0, report.concreteM3, 0.001) // 10+5+8+12
        assertEquals(2700.0, report.steelKg, 0.001)  // 800+400+600+900
        assertTrue("تقرير المشروع يجب أن يحتوي على بنود متعددة", report.items.size >= 4)
        assertTrue("التكلفة الإجمالية للمشروع يجب أن تكون موجبة", report.totalCost > 0.0)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 3. التكاليف غير الإنشائية
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun nonStructuralCostsCalculation() {
        val items = listOf(
            ComprehensiveCostManager.NonStructuralInput("نقل", "نقل مواد", 10.0, "طن", 150.0),
            ComprehensiveCostManager.NonStructuralInput("معدات", "إيجار رافعة", 5.0, "يوم", 500.0),
            ComprehensiveCostManager.NonStructuralInput("تراخيص", "ترخيص بناء", 1.0, "مجموع", 2000.0)
        )

        val costItems = ComprehensiveCostManager.calculateNonStructuralCosts(items, "EGP")

        assertEquals(3, costItems.size)
        assertEquals(1500.0, costItems[0].total, 0.001)   // 10 * 150
        assertEquals(2500.0, costItems[1].total, 0.001)   // 5 * 500
        assertEquals(2000.0, costItems[2].total, 0.001)   // 1 * 2000
        assertEquals(6000.0, costItems.sumOf { it.total }, 0.001)
    }

    @Test
    fun comprehensiveProjectCostIncludesNonStructural() {
        val structural = ComprehensiveCostManager.calculateFootingBOQ(10.0, 800.0, testPrices)
        val nonStructural = listOf(
            ComprehensiveCostManager.NonStructuralCostItem("نقل", "نقل مواد", 10.0, "طن", 150.0)
        )

        val comprehensive = ComprehensiveCostManager.calculateComprehensiveProjectCost(
            structuralReport = structural,
            nonStructuralItems = nonStructural,
            projectName = "مشروع تجريبي",
            projectLocation = "القاهرة",
            designCode = "ECP 203"
        )

        assertEquals("مشروع تجريبي", comprehensive.projectName)
        assertEquals("القاهرة", comprehensive.projectLocation)
        assertEquals("ECP 203", comprehensive.designCode)
        assertEquals(1500.0, comprehensive.totalNonStructural, 0.001)
        assertEquals(structural.totalCost + 1500.0, comprehensive.grandTotal, 0.001)
        assertTrue("المبلغ الإجمالي يجب أن يكون أكبر من التكلفة الإنشائية وحدها", comprehensive.grandTotal > comprehensive.structuralCost.totalCost)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 4. التقارير المقارنية (Comparative Reports)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun comparativeReportIdentifiesCheaperOption() {
        val optionA = ComprehensiveCostManager.calculateFootingBOQ(10.0, 800.0, testPrices)
        val optionB = ComprehensiveCostManager.calculateFootingBOQ(8.0, 600.0, testPrices)

        val comparison = ComprehensiveCostManager.compareCosts(optionA, optionB)

        assertEquals(optionB.totalCost - optionA.totalCost, comparison.difference, 0.001)
        assertEquals("B", comparison.cheaperOption)
        assertTrue("يجب أن يكون هناك فرق نسبي", comparison.differencePercent != 0.0)
    }

    @Test
    fun comparativeReportEqualCosts() {
        val optionA = ComprehensiveCostManager.calculateFootingBOQ(10.0, 800.0, testPrices)
        val optionB = ComprehensiveCostManager.calculateFootingBOQ(10.0, 800.0, testPrices)

        val comparison = ComprehensiveCostManager.compareCosts(optionA, optionB)

        assertEquals(0.0, comparison.difference, 0.001)
        assertEquals("EQUAL", comparison.cheaperOption)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 5. تقدير تكاليف الصيانة
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun maintenanceCostCalculation() {
        val maintenance = ComprehensiveCostManager.estimateMaintenanceCost(
            elementType = "جدار خرساني",
            concreteVolumeM3 = 50.0,
            concretePricePerM3 = 1300.0,
            exposureFactor = 1.5,
            currency = "EGP"
        )

        assertEquals("جدار خرساني", maintenance.elementType)
        assertTrue("تكلفة الفحص السنوي يجب أن تكون موجبة", maintenance.annualInspectionCost > 0.0)
        assertTrue("تكلفة الإصلاح السنوي يجب أن تكون موجبة", maintenance.annualRepairCost > 0.0)
        assertEquals(
            maintenance.annualInspectionCost + maintenance.annualRepairCost,
            maintenance.totalAnnualMaintenance,
            0.001
        )
        assertEquals(
            maintenance.totalAnnualMaintenance * 10.0,
            maintenance.tenYearMaintenance,
            0.001
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 6. تحليل حساسية الأسعار
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun priceSensitivityShowsSteelImpact() {
        val report = ComprehensiveCostManager.calculateFootingBOQ(10.0, 800.0, testPrices)

        val sensitivity = ComprehensiveCostManager.analyzePriceSensitivity(
            report,
            steelPriceChangePercent = 20.0, // ارتفاع سعر الحديد 20%
            concretePriceChangePercent = 10.0,
            laborPriceChangePercent = 5.0
        )

        assertTrue("يجب أن يحتوي التحليل على عناصر", sensitivity.isNotEmpty())
        // الحديد يجب أن يكون له أكبر تأثير لأنه أثقل مادة تكلفة
        val steelItem = sensitivity.find { it.material.contains("حديد") }
        assertTrue("يجب أن يظهر عنصر الحديد في التحليل", steelItem != null)
        assertTrue("تأثير تغيير سعر الحديد يجب أن يكون موجباً", steelItem!!.costImpactPercent > 0.0)
        assertTrue("التكلفة الجديدة يجب أن تكون أكبر من الأساسية", steelItem.newTotalCost > report.totalCost)
    }

    @Test
    fun priceSensitivityNoChangeWhenZeroPercent() {
        val report = ComprehensiveCostManager.calculateFootingBOQ(10.0, 800.0, testPrices)

        val sensitivity = ComprehensiveCostManager.analyzePriceSensitivity(
            report,
            steelPriceChangePercent = 0.0,
            concretePriceChangePercent = 0.0,
            laborPriceChangePercent = 0.0
        )

        sensitivity.forEach { item ->
            assertEquals(0.0, item.costImpactPercent, 0.001)
            assertEquals(report.totalCost, item.newTotalCost, 0.001)
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 7. تحويل العملة
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun convertReportToUSD() {
        val report = ComprehensiveCostManager.calculateFootingBOQ(10.0, 800.0, testPrices)
        val rate = 0.022 // 1 EGP = 0.022 USD (تقريبي)

        val converted = ComprehensiveCostManager.convertReport(report, Currency.USD, rate)

        assertEquals("USD", converted.currency)
        assertEquals(rate, converted.exchangeRate, 0.000001)
        assertEquals(report.totalCost * rate, converted.totalCost, 0.001)
        // جميع البنود يجب أن تتحول
        converted.items.forEachIndexed { index, item ->
            assertEquals(report.items[index].unitPrice * rate, item.unitPrice, 0.001)
            assertEquals(report.items[index].total * rate, item.total, 0.001)
        }
    }

    @Test
    fun convertReportSameCurrencyReturnsOriginal() {
        val report = ComprehensiveCostManager.calculateFootingBOQ(10.0, 800.0, testPrices)

        val converted = ComprehensiveCostManager.convertReport(report, Currency.EGP, 1.0)

        assertEquals(report.totalCost, converted.totalCost, 0.001)
        assertEquals("EGP", converted.currency)
    }
}
