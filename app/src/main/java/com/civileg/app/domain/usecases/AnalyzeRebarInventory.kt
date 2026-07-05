package com.civileg.app.domain.usecases

import com.civileg.app.domain.entities.*
import javax.inject.Inject
import kotlin.math.*

/**
 * حالة استخدام لتحليل المخزون
 */
class AnalyzeRebarInventory @Inject constructor() {
    
    fun analyze(
        requiredArea: Double,
        requiredLength: Double,
        inventory: RebarInventory,
        designCode: DesignCode,
        elementLength: Double,  // طول العنصر الإنشائي
        cover: Double = 40.0    // الغطاء الخرساني
    ): InventoryAnalysisResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        
        // حساب المساحة المطلوبة مع الهالك
        val totalRequiredLength = requiredLength * (1 + inventory.wastePercentage / 100)
        // Note: getRebarWeightPerMeter is defined below
        
        // البحث عن أفضل قطر متاح في المخزون
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
        
        // حساب الطول الفعلي لكل سيخ (طول العنصر + طول التراكب إذا لزم)
        val effectiveBarLength = if (elementLength > preferredStock.availableLength) {
            // يحتاج تراكب
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
        
        // خطة القص المثلى
        val cuttingPlan = optimizeCutting(
            stockLength = preferredStock.availableLength,
            requiredLength = effectiveBarLength,
            requiredBars = requiredBars
        )
        
        // التحقق من الكفاية
        val isSufficient = availableBarsCount >= requiredBars
        
        // تحذيرات
        if (!isSufficient) {
            warnings.add("⚠️ Insufficient rebar in inventory! Need ${additionalBarsNeeded} more bars")
        }
        if (elementLength > preferredStock.availableLength) {
            warnings.add("⚠️ Lap splices required - Length: ${inventory.lapSpliceLength * preferredStock.diameter}mm per splice")
        }
        if (inventory.wastePercentage > 10) {
            warnings.add("⚠️ High waste percentage (${inventory.wastePercentage}%) - Review cutting plan")
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
    
    private fun getRebarWeightPerMeter(area: Double): Double {
        // الوزن = المساحة × كثافة الحديد (7850 kg/m³)
        return area / 1e6 * 7850  // kg/m
    }
    
    private fun optimizeCutting(
        stockLength: Double,
        requiredLength: Double,
        requiredBars: Int
    ): List<CuttingPlan> {
        val plans = mutableListOf<CuttingPlan>()
        var remainingBars = requiredBars
        
        while (remainingBars > 0) {
            val barsPerStock = floor(stockLength / requiredLength).toInt().coerceAtLeast(1)
            val usedLength = barsPerStock * requiredLength
            val wasteLength = stockLength - usedLength
            val utilization = (usedLength / stockLength) * 100
            
            plans.add(
                CuttingPlan(
                    stockLength = stockLength,
                    requiredLengths = List(barsPerStock) { requiredLength },
                    wasteLength = wasteLength,
                    utilizationPercentage = utilization
                )
            )
            
            remainingBars -= barsPerStock
        }
        
        return plans
    }
    
    private fun getCodeReference(code: DesignCode, key: String): String = when (code) {
        DesignCode.ECP -> when (key) {
            "LAP_SPLICE" -> "ECP 203-5.3.4: Lap splice length = 50×diameter minimum"
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
