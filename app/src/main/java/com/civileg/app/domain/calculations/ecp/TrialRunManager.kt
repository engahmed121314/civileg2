package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.utils.ComprehensiveCostManager
import com.civileg.app.utils.TimelineManager
import com.civileg.app.utils.RatioManager

/**
 * محرك التشغيل التجريبي لضمان تكامل جميع الأجزاء (Integration Run)
 */
object TrialRunManager {

    fun runFullProjectSimulation(): String {
        val report = StringBuilder()
        report.append("=== تشغيل تجريبي لنظام CivilEG v2.0 ===\n\n")

        // 1. تجربة محرك الزلازل (ECP)
        val seismic = ECPSeismic()
        val baseShear = seismic.calculateBaseShear(
            totalWeight = 5000.0, // kN
            seismicZone = SeismicZone.ZONE_3,
            soilType = SoilType.C,
            importanceFactor = 1.0,
            responseModificationFactor = 5.0
        )
        report.append("1. حسابات الزلازل: القص القاعدي = ${String.format("%.2f", baseShear.baseShear)} kN\n")

        // 2. تجربة تصميم القواعد
        val footingEngine = ECPFooting()
        val footing = footingEngine.designIsolatedFooting(
            fcu = 25.0, fy = 360.0, columnWidth = 300.0, columnDepth = 600.0,
            axialLoad = 1200.0, momentX = 0.0, momentY = 0.0,
            soilBearingCapacity = 200.0, footingDepth = 600.0,
            loadCombination = LoadCombination.DEAD_LIVE,
            constraints = BoundaryConstraints(maxLeft = null, maxRight = null, maxTop = null, maxBottom = null)
        )
        report.append("2. تصميم القواعد: العرض المطلوب = ${footing.requiredWidth} mm\n")
        report.append("   حالة القص الثاقب: ${if(footing.punchingShearCheck.isSafe) "آمن ✅" else "خطر ❌"}\n")

        // 3. تجربة حساب التكاليف الشاملة
        val prices = ComprehensiveCostManager.MaterialPrices(
            cementPricePerBag = 100.0,
            steelPricePerTon = 42000.0,
            sandPricePerM3 = 150.0,
            gravelPricePerM3 = 250.0,
            laborCostPerM3 = 400.0
        )
        
        val costs = ComprehensiveCostManager.estimateProjectTotal(
            projectType = null,
            landArea = 200.0,
            buildingRatio = 75.0,
            floors = 5,
            prices = prices
        )
        report.append("3. تقدير التكاليف: الإجمالي = ${costs.totalCost} EGP\n")
        report.append("   كمية الخرسانة المقدرة: ${costs.concreteM3} m3\n")

        // 4. تجربة نظام إدارة النسب (RatioManager)
        val ratioConfig = RatioManager.getConfigurationByName("Standard")
        report.append("4. إدارة النسب: استخدام إعداد [${ratioConfig?.name ?: "Default"}] - معامل هالك الحديد: ${ratioConfig?.wastageFactors?.steelWastage ?: 0.05}\n")

        // 5. تجربة الجدولة الزمنية (TimelineManager)
        val tasks = TimelineManager.generateBeamTimeline(
            beamCount = 10,
            steelWeightKg = 2500.0,
            concreteVolume = 15.0,
            formworkArea = 100.0
        )
        val totalDays = tasks.sumOf { it.durationInDays }
        report.append("5. الجدولة الزمنية: تم توليد ${tasks.size} مهام عمل. المدة التقديرية: $totalDays يوم\n\n")

        report.append("✅ تمت عملية التكامل والتشغيل التجريبي بنجاح.")
        return report.toString()
    }
}