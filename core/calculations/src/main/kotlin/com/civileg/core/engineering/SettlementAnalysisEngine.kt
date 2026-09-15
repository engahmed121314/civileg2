package com.civileg.core.engineering

import kotlin.math.*

/**
 * محرك تحليل هبوط التربة (Soil Settlement Analysis)
 * يدعم الهبوط الفوري وهبوط التصلب لطبقات التربة المتعددة.
 */
object SettlementAnalysisEngine {

    data class SoilLayer(
        val name: String,
        val thickness: Double,      // m
        val unitWeight: Double,     // kN/m3
        val elasticModulus: Double, // kPa (Es)
        val poissonRatio: Double = 0.3,
        val cc: Double? = null,     // Compression Index (Consolidation)
        val cr: Double? = null,     // Recompression Index
        val e0: Double? = null,     // Initial void ratio
        val pc: Double? = null      // Pre-consolidation pressure
    )

    data class SettlementResult(
        val immediateSettlement: Double,      // mm
        val consolidationSettlement: Double,  // mm
        val totalSettlement: Double,          // mm
        val isSafe: Boolean,
        val notes: List<String> = emptyList()
    )

    /**
     * حساب الهبوط لمجموعة أحمال على مساحة محددة
     */
    fun calculateSettlement(
        bearingPressure: Double, // kPa (Service)
        width: Double,           // m (Footing width)
        length: Double,          // m
        layers: List<SoilLayer>,
        limit: Double = 50.0      // mm (Standard limit)
    ): SettlementResult {
        var si = 0.0
        var sc = 0.0
        val notes = mutableListOf<String>()

        // 1. Immediate Settlement (Schmertmann or Elastic)
        // Si = q * B * (1-μ²)/Es * I
        val I = 1.0 // Shape factor (Simplified)
        layers.forEach { layer ->
            val mu = layer.poissonRatio
            si += bearingPressure * width * (1 - mu.pow(2)) / layer.elasticModulus * I * 1000.0
        }

        // 2. Consolidation Settlement (One-dimensional)
        // Sc = Σ [Cc/(1+e0) * H * log10(σ0 + Δσ / σ0)]
        var currentDepth = 0.0
        var overBurden = 0.0
        
        layers.forEach { layer ->
            val sigma0 = overBurden + (layer.thickness / 2.0) * layer.unitWeight
            // Simplified stress distribution (2:1 Method)
            val deltaSigma = (bearingPressure * width * length) / ((width + currentDepth) * (length + currentDepth))
            
            if (layer.cc != null && layer.e0 != null) {
                val s_layer = (layer.cc / (1 + layer.e0)) * layer.thickness * log10((sigma0 + deltaSigma) / sigma0)
                sc += s_layer * 1000.0
            }
            
            overBurden += layer.thickness * layer.unitWeight
            currentDepth += layer.thickness
        }

        val total = si + sc
        notes.add("Immediate: ${"%.1f".format(si)} mm")
        notes.add("Consolidation: ${"%.1f".format(sc)} mm")

        return SettlementResult(
            immediateSettlement = si,
            consolidationSettlement = sc,
            totalSettlement = total,
            isSafe = total <= limit,
            notes = notes
        )
    }
}
