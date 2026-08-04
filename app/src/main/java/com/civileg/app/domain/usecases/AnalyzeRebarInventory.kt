package com.civileg.app.domain.usecases

import com.civileg.app.domain.entities.*
import javax.inject.Inject
import kotlin.math.*

/**
 * تحليل مخزون حديد التسليح — مع خوارزمية قص محسّنة
 *
 * التحسينات على النسخة السابقة:
 * - خوارزمية First-Fit Decreasing (FFD) لتعبئة عدة أطوال في سيخ واحد
 * - إعادة استخدام البواقي (leftovers) لأطوال أقصر
 * - دعم أطوال متعددة من نفس القطر في خطة قص واحدة
 * - حساب أدق للهالك والاستفادة
 */
class AnalyzeRebarInventory @Inject constructor() {

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
            requiredLengths = List(requiredBars) { effectiveBarLength }
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
     * تحليل متقدم: يحسب خطة القص المثلى لقائمة أطوال مختلفة من نفس القطر
     *
     * @param stockLength طول السيخ القياسي (م) — مثلاً 12م
     * @param requiredLengths قائمة الأطوال المطلوبة (م) — مثلاً [3.5, 3.5, 2.8, 2.8, 2.8, 1.5]
     * @return قائمة بخطط القص لكل سيخ مستخدم
     */
    fun optimizeCuttingMultiLength(
        stockLength: Double,
        requiredLengths: List<Double>
    ): List<CuttingPlan> {
        if (requiredLengths.isEmpty()) return emptyList()

        // First-Fit Decreasing: رتب الأطوال تنازلياً
        val sortedLengths = requiredLengths.sortedDescending().toMutableList()
        val plans = mutableListOf<CuttingPlan>()

        // لكل طول مطلوب، حاول وضعه في سيخ موجود أولاً
        for (length in sortedLengths) {
            var placed = false

            // محاولة وضع في سيخ موجود به مساحة كافية
            for (i in plans.indices) {
                val usedSoFar = plans[i].requiredLengths.sum()
                val remaining = stockLength - usedSoFar
                if (remaining >= length - 1e-6) { // سماحة 1 مم
                    plans[i] = plans[i].copy(
                        requiredLengths = plans[i].requiredLengths + length,
                        wasteLength = remaining - length,
                        utilizationPercentage = ((usedSoFar + length) / stockLength) * 100
                    )
                    placed = true
                    break
                }
            }

            // إذا لم يتسع في أي سيخ موجود، افتح سيخ جديد
            if (!placed) {
                val waste = max(0.0, stockLength - length)
                plans.add(
                    CuttingPlan(
                        stockLength = stockLength,
                        requiredLengths = listOf(length),
                        wasteLength = waste,
                        utilizationPercentage = (length / stockLength) * 100
                    )
                )
            }
        }

        return plans
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
     * @return CuttingOptimizationResult فيه خطة القص + البواقي المتبقية
     */
    fun optimizeWithLeftovers(
        stockLength: Double,
        requiredLengths: List<Double>,
        availableLeftovers: List<Double> = emptyList()
    ): CuttingOptimizationResult {
        val remaining = requiredLengths.toMutableList()
        val usedLeftovers = mutableListOf<Double>()
        val leftoverPieces = availableLeftovers.toMutableList()

        // المرحلة 1: استخدم البواقي المتوفرة أولاً (من الأكبر للأصغر)
        val sortedLeftovers = leftoverPieces.sortedDescending()
        val sortedRequired = remaining.sortedDescending().toMutableList()

        val leftoverPlans = mutableListOf<CuttingPlan>()
        for (leftover in sortedLeftovers) {
            if (sortedRequired.isEmpty()) break
            // حاول وضع أكبر طول مطلوب يتناسب مع الباقي
            val fitIndex = sortedRequired.indexOfFirst { it <= leftover + 1e-6 }
            if (fitIndex >= 0) {
                val usedLength = sortedRequired.removeAt(fitIndex)
                usedLeftovers.add(leftover)
                leftoverPlans.add(
                    CuttingPlan(
                        stockLength = leftover,
                        requiredLengths = listOf(usedLength),
                        wasteLength = leftover - usedLength,
                        utilizationPercentage = (usedLength / leftover) * 100
                    )
                )
            }
        }

        // المرحلة 2: القص من أسياخ جديدة للأطوال المتبقية
        val newBarPlans = optimizeCuttingMultiLength(stockLength, sortedRequired)

        // البواقي المتبقية من الأسياخ الجديدة
        val newLeftovers = newBarPlans.mapNotNull { plan ->
            val leftover = plan.wasteLength
            if (leftover >= 0.3) leftover else null // بواقي أقل من 30 سم لا فائدة منها
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
            newLeftoverPieces = newLeftovers,
            leftoverPiecesUsed = usedLeftovers.size
        )
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
