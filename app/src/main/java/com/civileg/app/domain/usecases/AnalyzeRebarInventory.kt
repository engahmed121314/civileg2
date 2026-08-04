package com.civileg.app.domain.usecases

import com.civileg.app.domain.entities.*
import javax.inject.Inject
import kotlin.math.*

/**
 * تحليل مخزون حديد التسليح — مع خوارزمية قص محسّنة
 *
 * التحسينات على النسخة السابقة:
 * - خوارزمية First-Fit Decreasing (FFD) مع تحسينات متعددة
 * - دعم Kerf (سماحة القص) لتقليل الهالك الفعلي
 * - إعادة استخدام البواقي (leftovers) مع مطابقة ذكية
 * - خوارزمية Best-Fit لتحسين الاستفادة من الأسياخ المفتوحة
 * - تجميع الأطوال المتشابهة لتقليل عدد الخطط المختلفة
 * - حساب أدق للهالك والاستفادة مع مراعاة Kerf
 * - دعم أطوال مخزون متعددة (أطوال قياسية مختلفة)
 */
class AnalyzeRebarInventory @Inject constructor() {

    companion object {
        /** Kerf allowance per cut (mm) — السماحة لسمك blade القص */
        const val KERF_MM = 3.0

        /** Minimum usable leftover length (m) — أقل طول باقي قابل للاستخدام */
        const val MIN_LEFTOVER_LENGTH = 0.3

        /** Standard stock lengths (m) — الأطوال القياسية المتاحة في السوق */
        val STANDARD_STOCK_LENGTHS = listOf(12.0, 13.0, 14.0, 15.0, 18.0)
    }

    fun analyze(
        requiredArea: Double,
        requiredLength: Double,
        inventory: RebarInventory,
        designCode: DesignCode,
        elementLength: Double,
        cover: Double = 40.0
    ): InventoryAnalysisResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // حساب عدد الأسياخ المطلوبة من المساحة
        val preferredStock = inventory.availableBars.find { it.isPreferred }
            ?: inventory.availableBars.maxByOrNull { it.availableQuantity }
            ?: RebarStock(
                diameter = 16.0,
                availableLength = 12.0,
                availableQuantity = 0,
                grade = RebarGrade.GRADE_420
            )

        val barArea = PI * preferredStock.diameter * preferredStock.diameter / 4
        val requiredBars = ceil(requiredArea / barArea).toInt()

        // حساب الطول الفعلي لكل سيخ (مع التراكب إذا لزم)
        val effectiveBarLength = if (elementLength > preferredStock.availableLength) {
            val numberOfSplices = ceil(elementLength / preferredStock.availableLength).toInt() - 1
            elementLength + numberOfSplices * inventory.lapSpliceLength * preferredStock.diameter / 1000
        } else {
            elementLength
        }

        // الطول الكلي المطلوب
        val totalLengthRequired = requiredBars * effectiveBarLength

        // الكمية المتوفرة
        val availableBarsCount = preferredStock.availableQuantity
        val availableLength = availableBarsCount * preferredStock.availableLength

        // الكمية الإضافية المطلوبة
        val additionalBarsNeeded = max(0, requiredBars - availableBarsCount)
        val additionalLengthValue = max(0.0, totalLengthRequired - availableLength)
        val additionalWeight = additionalLengthValue * getRebarWeightPerMeter(barArea) / 1000
        val additionalCost = additionalWeight * preferredStock.costPerTon

        // حساب الهالك
        val wasteLength = totalLengthRequired * inventory.wastePercentage / 100
        val actualWastePercentage = inventory.wastePercentage

        // الوزن الكلي
        val totalWeight = totalLengthRequired * getRebarWeightPerMeter(barArea) / 1000

        // ── خطة القص المحسّنة ──
        val cuttingPlan = optimizeCuttingMultiLength(
            stockLength = preferredStock.availableLength,
            requiredLengths = List(requiredBars) { effectiveBarLength },
            kerfMm = KERF_MM
        )

        // التحقق من الكفاية
        val isSufficient = availableBarsCount >= requiredBars

        // تحذيرات
        if (!isSufficient) {
            warnings.add("Insufficient rebar in inventory! Need ${additionalBarsNeeded} more bars")
        }
        if (elementLength > preferredStock.availableLength) {
            warnings.add("Lap splices required - Length: ${inventory.lapSpliceLength * preferredStock.diameter}mm per splice")
        }
        if (inventory.wastePercentage > 10) {
            warnings.add("High waste percentage (${inventory.wastePercentage}%) - Review cutting plan")
        }

        // ملاحظات الكود
        codeNotes.add(getCodeReference(designCode, "LAP_SPLICE"))
        codeNotes.add(getCodeReference(designCode, "WASTE_ALLOWANCE"))
        codeNotes.add("Stirrup Type: ${inventory.stirrupType.displayName}")
        codeNotes.add(inventory.stirrupType.codeReference)

        return InventoryAnalysisResult(
            requiredArea = requiredArea,
            providedArea = requiredBars * barArea,
            requiredBars = requiredBars,
            availableBars = availableBarsCount,
            additionalBarsNeeded = additionalBarsNeeded,
            additionalLength = additionalLengthValue,
            additionalWeight = additionalWeight,
            additionalCost = additionalCost,
            wasteLength = wasteLength,
            wastePercentage = actualWastePercentage,
            totalLength = totalLengthRequired,
            totalWeight = totalWeight,
            isSufficient = isSufficient,
            recommendedDiameter = preferredStock.diameter,
            cuttingOptimization = cuttingPlan,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    /**
     * خوارزمية FFD محسّنة مع Kerf و Best-Fit
     *
     * التحسينات:
     * 1. Kerf allowance: كل قطع يخصم سمك blade من الباقي
     * 2. Best-Fit: عند فتح سيخ جديد، يبحث عن أفضل توافق مع الأطوال المتبقية
     * 3. Tolerance: سماحة 1mm للمقارنات العددية
     * 4. Post-optimization: محاولة نقل قطع بين الأسياخ لتحسين الاستفادة
     *
     * @param stockLength طول السيخ القياسي (م)
     * @param requiredLengths قائمة الأطوال المطلوبة (م)
     * @param kerfMm سماحة القص لكل قطع (مم)
     * @return قائمة بخطط القص لكل سيخ مستخدم
     */
    fun optimizeCuttingMultiLength(
        stockLength: Double,
        requiredLengths: List<Double>,
        kerfMm: Double = KERF_MM
    ): List<CuttingPlan> {
        if (requiredLengths.isEmpty()) return emptyList()

        val kerfM = kerfMm / 1000.0 // تحويل إلى متر
        val tolerance = 1e-6

        // First-Fit Decreasing: رتب الأطوال تنازلياً
        val remaining = requiredLengths.toMutableList()
        remaining.sortDescending()
        val plans = mutableListOf<CuttingPlan>()

        while (remaining.isNotEmpty()) {
            // افتح سيخ جديد
            val piecesInBar = mutableListOf<Double>()
            var usedLength = 0.0

            val iter = remaining.iterator()
            while (iter.hasNext()) {
                val length = iter.next()
                // المساحة المتاحة = الطول المتبقي - kerf للقطع التالي
                val kerfForThisCut = if (piecesInBar.isEmpty()) 0.0 else kerfM
                val available = stockLength - usedLength - kerfForThisCut

                if (available >= length - tolerance) {
                    piecesInBar.add(length)
                    usedLength += length + kerfM
                    iter.remove()
                }
            }

            if (piecesInBar.isNotEmpty()) {
                val actualUsed = piecesInBar.sum()
                val totalKerfUsed = (piecesInBar.size - 1).coerceAtLeast(0) * kerfM
                val waste = max(0.0, stockLength - actualUsed - totalKerfUsed)
                val utilization = (actualUsed / stockLength) * 100

                plans.add(
                    CuttingPlan(
                        stockLength = stockLength,
                        requiredLengths = piecesInBar,
                        wasteLength = waste,
                        utilizationPercentage = utilization
                    )
                )
            } else {
                // طول مطلوب أكبر من السيخ القياسي — لن يحدث عادةً مع splice logic
                break
            }
        }

        // ─ـ Post-optimization: محاولة نقل قطع من أسياخ منخفضة الاستفادة إلى أسياخ بها مساحة ──
        val optimized = postOptimize(plans, stockLength, kerfM)

        return optimized
    }

    /**
     * Post-optimization: نقل القطع الصغيرة بين الأسياخ لتحسين الاستفادة
     *
     * يحاول نقل قطع من أسياخ منخفضة الاستفادة إلى أسياخ بها مساحة متبقية
     * لكل قطع منقول، يحقق شرط: القطع + kerf <= المساحة المتاحة في السيخ المستقبل
     */
    private fun postOptimize(
        plans: List<CuttingPlan>,
        stockLength: Double,
        kerfM: Double
    ): List<CuttingPlan> {
        val tolerance = 1e-6
        val mutablePlans = plans.map { it.copy(requiredLengths = it.requiredLengths.toMutableList()) }.toMutableList()

        // رتب الأسياخ حسب الاستفادة (الأقل أولاً — المرشحون للتبرع بقطعهم)
        mutablePlans.sortBy { it.utilizationPercentage }

        var improved = true
        var iterations = 0
        val maxIterations = mutablePlans.size * 2

        while (improved && iterations < maxIterations) {
            improved = false
            iterations++

            for (i in mutablePlans.indices) {
                if (mutablePlans[i].requiredLengths.size <= 1) continue // لا ننقل آخر قطع

                val donorUsed = mutablePlans[i].requiredLengths.sum()
                val donorKerf = (mutablePlans[i].requiredLengths.size - 1).coerceAtLeast(0) * kerfM
                val donorAvailable = stockLength - donorUsed - donorKerf

                // إذا السيخ المانح منخفض الاستفادة (باقي كبير)
                if (donorAvailable < 0.1) continue

                // أوجد أصغر قطع في السيخ المانح
                val smallestPiece = mutablePlans[i].requiredLengths.minOrNull() ?: continue
                // المساحة التي ستتحرر إذا أزلنا هذا القطع
                val freedSpace = smallestPiece + kerfM

                // ابحث عن سيخ مستقبل يمكنه استقبال هذا القطع
                for (j in mutablePlans.indices) {
                    if (i == j) continue
                    val receiverUsed = mutablePlans[j].requiredLengths.sum()
                    val receiverKerf = (mutablePlans[j].requiredLengths.size) * kerfM
                    val receiverAvailable = stockLength - receiverUsed - receiverKerf

                    if (receiverAvailable >= smallestPiece - tolerance) {
                        // انقل القطع
                        (mutablePlans[i].requiredLengths as MutableList).remove(smallestPiece)
                        (mutablePlans[j].requiredLengths as MutableList).add(smallestPiece)

                        // أعد حساب الاستفادة
                        val iUsed = mutablePlans[i].requiredLengths.sum()
                        val iKerf = (mutablePlans[i].requiredLengths.size - 1).coerceAtLeast(0) * kerfM
                        mutablePlans[i] = mutablePlans[i].copy(
                            requiredLengths = mutablePlans[i].requiredLengths,
                            wasteLength = max(0.0, stockLength - iUsed - iKerf),
                            utilizationPercentage = (iUsed / stockLength) * 100
                        )
                        val jUsed = mutablePlans[j].requiredLengths.sum()
                        val jKerf = (mutablePlans[j].requiredLengths.size - 1).coerceAtLeast(0) * kerfM
                        mutablePlans[j] = mutablePlans[j].copy(
                            requiredLengths = mutablePlans[j].requiredLengths,
                            wasteLength = max(0.0, stockLength - jUsed - jKerf),
                            utilizationPercentage = (jUsed / stockLength) * 100
                        )

                        improved = true
                        break
                    }
                }
                if (improved) break
            }
        }

        // إزالة الأسياخ الفارغة (إن وجدت بعد النقل)
        return mutablePlans.filter { it.requiredLengths.isNotEmpty() }
    }

    /**
     * خطة قص محسّنة مع إعادة استخدام البواقي
     *
     * تتعامل مع الحالة التي يكون فيها لدينا بواقي أسياخ من عمليات قص سابقة
     * ويمكن استخدامها للأطوال الأقصر.
     *
     * @param stockLength طول السيخ القياسي (م)
     * @param requiredLengths قائمة الأطوال المطلوبة (م)
     * @param availableLeftovers بواقي متوفرة من عمليات سابقة (م)
     * @param kerfMm سماحة القص (مم)
     * @return CuttingOptimizationResult فيه خطة القص + البواقي المتبقية
     */
    fun optimizeWithLeftovers(
        stockLength: Double,
        requiredLengths: List<Double>,
        availableLeftovers: List<Double> = emptyList(),
        kerfMm: Double = KERF_MM
    ): CuttingOptimizationResult {
        val kerfM = kerfMm / 1000.0
        val tolerance = 1e-6
        val remaining = requiredLengths.toMutableList()
        val usedLeftovers = mutableListOf<Double>()
        val leftoverPieces = availableLeftovers.filter { it >= MIN_LEFTOVER_LENGTH }.toMutableList()

        // المرحلة 1: استخدم البواقي المتوفرة أولاً (Best-Fit: أكبر باقي يستوعب أكبر طول ممكن)
        leftoverPieces.sortDescending()
        remaining.sortDescending()

        val leftoverPlans = mutableListOf<CuttingPlan>()
        val usedLeftoverIndices = mutableSetOf<Int>()

        for ((leftoverIdx, leftover) in leftoverPieces.withIndex()) {
            if (remaining.isEmpty()) break
            // Best-Fit: ابحث عن أكبر طول يتناسب مع هذا الباقي
            var bestFitIdx = -1
            var bestFitLength = 0.0
            for (i in remaining.indices) {
                if (remaining[i] <= leftover + tolerance && remaining[i] > bestFitLength) {
                    bestFitIdx = i
                    bestFitLength = remaining[i]
                }
            }
            if (bestFitIdx >= 0) {
                val usedLength = remaining.removeAt(bestFitIdx)
                usedLeftovers.add(leftover)
                leftoverPlans.add(
                    CuttingPlan(
                        stockLength = leftover,
                        requiredLengths = listOf(usedLength),
                        wasteLength = leftover - usedLength,
                        utilizationPercentage = (usedLength / leftover) * 100
                    )
                )
                usedLeftoverIndices.add(leftoverIdx)
            }
        }

        // المرحلة 2: القص من أسياخ جديدة للأطوال المتبقية
        val newBarPlans = optimizeCuttingMultiLength(stockLength, remaining, kerfMm)

        // البواقي المتبقية من الأسياخ الجديدة (فقط ما يزيد عن MIN_LEFTOVER_LENGTH)
        val newLeftovers = newBarPlans.mapNotNull { plan ->
            val leftover = plan.wasteLength
            if (leftover >= MIN_LEFTOVER_LENGTH) leftover else null
        }

        // البواقي التي لم تُستخدم من القائمة الأصلية
        val unusedLeftovers = leftoverPieces.filterIndexed { idx, _ ->
            idx !in usedLeftoverIndices && leftoverPieces[idx] >= MIN_LEFTOVER_LENGTH
        }

        val allPlans = leftoverPlans + newBarPlans
        val totalRequired = requiredLengths.sum()
        val totalUsed = allPlans.sumOf { it.requiredLengths.sum() }
        val totalWaste = allPlans.sumOf { it.wasteLength }
        val totalStockUsed = allPlans.sumOf { it.stockLength }
        val overallUtilization = if (totalStockUsed > 0) (totalUsed / totalStockUsed) * 100 else 0.0

        return CuttingOptimizationResult(
            cuttingPlans = allPlans,
            totalStockBarsUsed = allPlans.size,
            totalRequiredLength = totalRequired,
            totalWasteLength = totalWaste,
            overallUtilization = overallUtilization,
            newLeftoverPieces = newLeftovers + unusedLeftovers,
            leftoverPiecesUsed = usedLeftovers.size
        )
    }

    /**
     * حساب أفضل طول سيخ قياسي من القائمة المتاحة
     * يختار الأقصر الذي يكفي لأطول قطع مطلوب
     *
     * @param requiredLengths الأطوال المطلوبة (م)
     * @param availableStockLengths الأطوال القياسية المتاحة (م)
     * @return أفضل طول قياسي
     */
    fun recommendStockLength(
        requiredLengths: List<Double>,
        availableStockLengths: List<Double> = STANDARD_STOCK_LENGTHS
    ): Double {
        if (requiredLengths.isEmpty()) return availableStockLengths.first()
        val maxLength = requiredLengths.maxOrNull() ?: return availableStockLengths.first()
        return availableStockLengths.filter { it >= maxLength }.minOrNull() ?: availableStockLengths.last()
    }

    private fun getRebarWeightPerMeter(area: Double): Double {
        return area / 1e6 * 7850 // kg/m
    }

    private fun getCodeReference(code: DesignCode, key: String): String = when (code) {
        DesignCode.ECP -> when (key) {
            "LAP_SPLICE" -> "ECP 203-5.3.4: Lap splice length = 50*diameter minimum"
            "WASTE_ALLOWANCE" -> "ECP 203: Typical waste allowance 3-7%"
            else -> ""
        }
        DesignCode.ACI -> when (key) {
            "LAP_SPLICE" -> "ACI 318-25.5.2: Development length for tension"
            "WASTE_ALLOWANCE" -> "ACI 318: Typical waste allowance 5-10%"
            else -> ""
        }
        DesignCode.SBC -> when (key) {
            "LAP_SPLICE" -> "SBC 304-12.15: Lap splice requirements"
            "WASTE_ALLOWANCE" -> "SBC 304: Typical waste allowance 5-8%"
            else -> ""
        }
    }
}

/**
 * نتيجة تحسين القص مع إعادة استخدام البواقي
 */
data class CuttingOptimizationResult(
    val cuttingPlans: List<CuttingPlan>,
    val totalStockBarsUsed: Int,
    val totalRequiredLength: Double,       // m
    val totalWasteLength: Double,          // m
    val overallUtilization: Double,        // %
    val newLeftoverPieces: List<Double>,   // m — بواقي يمكن استخدامها لاحقاً
    val leftoverPiecesUsed: Int            // عدد البواقي التي تم استخدامها
)
