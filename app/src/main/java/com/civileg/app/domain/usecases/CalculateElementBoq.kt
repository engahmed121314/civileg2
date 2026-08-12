package com.civileg.app.domain.usecases

import com.civileg.app.domain.entities.*
import javax.inject.Inject
import kotlin.math.PI

/**
 * حالة استخدام لحساب الكميات من نتائج التصميم
 */
class CalculateElementBoq @Inject constructor() {
    
    /**
     * حساب كميات العمود
     */
    fun calculateColumnBoq(
        width: Double,      // mm
        depth: Double,      // mm
        height: Double,     // mm
        reinforcementResult: ReinforcementResult,
        prices: MaterialPrices
    ): List<BoqItem> {
        val items = mutableListOf<BoqItem>()
        
        // 1. الخرسانة
        val concreteVolume = (width * depth * height) / 1e9  // m³
        items += BoqItem(
            itemId = "COL_CONC_001",
            description = "Concrete for column ${width}x${depth}mm",
            category = BoqCategory.CONCRETE,
            unit = "m³",
            quantity = concreteVolume,
            unitPrice = prices.concretePerM3,
            codeReference = "Volume = b × d × h"
        )
        
        // 2. حديد التسليح
        val steelWeight = (reinforcementResult.astProvided * height / 1e6) * 7850 / 1000  // ton
        items += BoqItem(
            itemId = "COL_REINF_001",
            description = "Reinforcement steel Ø${reinforcementResult.barDiameter}mm",
            category = BoqCategory.REINFORCEMENT,
            unit = "ton",
            quantity = steelWeight,
            unitPrice = prices.steelPerTon,
            codeReference = "Weight = As × L × 7850"
        )
        
        // 3. الكانات
        val tiesWeight = calculateTiesWeight(
            width, depth, height,
            reinforcementResult.tiesDiameter,
            reinforcementResult.tiesSpacing
        )
        items += BoqItem(
            itemId = "COL_TIES_001",
            description = "Ties Ø${reinforcementResult.tiesDiameter}mm",
            category = BoqCategory.REINFORCEMENT,
            unit = "ton",
            quantity = tiesWeight,
            unitPrice = prices.steelPerTon
        )
        
        // 4. الشدة الخشبية
        val formworkArea = 2 * (width + depth) * height / 1e6  // m²
        items += BoqItem(
            itemId = "COL_FORM_001",
            description = "Formwork for column",
            category = BoqCategory.FORMWORK,
            unit = "m²",
            quantity = formworkArea,
            unitPrice = prices.formworkPerM2
        )
        
        return items
    }
    
    /**
     * حساب كميات الكمرة
     */
    fun calculateBeamBoq(
        width: Double,
        depth: Double,
        span: Double,
        flexureResult: ReinforcementResult,
        shearResult: ShearReinforcementResult,
        prices: MaterialPrices
    ): List<BoqItem> {
        val items = mutableListOf<BoqItem>()
        
        // الخرسانة
        val concreteVolume = (width * depth * span) / 1e9
        items += BoqItem(
            itemId = "BEAM_CONC_001",
            description = "Concrete for beam ${width}x${depth}mm, L=${span}mm",
            category = BoqCategory.CONCRETE,
            unit = "m³",
            quantity = concreteVolume,
            unitPrice = prices.concretePerM3
        )
        
        // الحديد الرئيسي
        val mainSteelWeight = (flexureResult.astProvided * span / 1e6) * 7850 / 1000
        items += BoqItem(
            itemId = "BEAM_REINF_001",
            description = "Main reinforcement Ø${flexureResult.barDiameter}mm",
            category = BoqCategory.REINFORCEMENT,
            unit = "ton",
            quantity = mainSteelWeight,
            unitPrice = prices.steelPerTon
        )
        
        // الكانات
        val stirrupsWeight = calculateStirrupsWeight(
            width, depth, span,
            shearResult.stirrupDiameter,
            shearResult.stirrupSpacing
        )
        items += BoqItem(
            itemId = "BEAM_STIR_001",
            description = "Stirrups Ø${shearResult.stirrupDiameter}mm @ ${shearResult.stirrupSpacing}mm",
            category = BoqCategory.REINFORCEMENT,
            unit = "ton",
            quantity = stirrupsWeight,
            unitPrice = prices.steelPerTon
        )
        
        // الشدة الخشبية (3 وجوه عادة)
        val formworkArea = (2 * depth + width) * span / 1e6
        items += BoqItem(
            itemId = "BEAM_FORM_001",
            description = "Formwork for beam (3 sides)",
            category = BoqCategory.FORMWORK,
            unit = "m²",
            quantity = formworkArea,
            unitPrice = prices.formworkPerM2
        )
        
        return items
    }
    
    // دوال مساعدة لحساب وزن الكانات
    private fun calculateTiesWeight(
        colWidth: Double, colDepth: Double, colHeight: Double,
        tiesDiameter: Double, tiesSpacing: Double
    ): Double {
        if (tiesSpacing <= 0) return 0.0
        // محيط الكانة الواحدة (مع خصم الغطاء الخرساني 40مم من كل جهة)
        val tiePerimeter = 2 * (colWidth - 2 * 40 + colDepth - 2 * 40) + 24.0  // +24 للخطافين (مبسط)
        val tieLength = tiePerimeter / 1000.0  // m
        
        // عدد الكانات
        val numberOfTies = (colHeight / tiesSpacing).toInt() + 1
        
        // مساحة مقطع السيخ
        val barArea = PI * tiesDiameter * tiesDiameter / 4.0
        
        // وزن الكانة الواحدة (حجم × كثافة الحديد 7850 كجم/م3)
        val tieWeight = (barArea / 1e6) * tieLength * 7850.0  // kg
        
        return (tieWeight * numberOfTies) / 1000.0  // ton
    }
    
    private fun calculateStirrupsWeight(
        beamWidth: Double, beamDepth: Double, beamSpan: Double,
        stirrupDiameter: Double, stirrupSpacing: Double
    ): Double {
        if (stirrupSpacing <= 0) return 0.0
        val stirrupPerimeter = 2 * (beamWidth - 2 * 40 + beamDepth - 2 * 40) + 24.0
        val stirrupLength = stirrupPerimeter / 1000.0
        val numberOfStirrups = (beamSpan / stirrupSpacing).toInt() + 1
        
        val barArea = PI * stirrupDiameter * stirrupDiameter / 4.0
        val stirrupWeight = (barArea / 1e6) * stirrupLength * 7850.0
        
        return (stirrupWeight * numberOfStirrups) / 1000.0
    }
}

data class MaterialPrices(
    val concretePerM3: Double = 1200.0,    // EGP
    val steelPerTon: Double = 18000.0,     // EGP
    val formworkPerM2: Double = 150.0,     // EGP
    val excavationPerM3: Double = 50.0     // EGP
)
