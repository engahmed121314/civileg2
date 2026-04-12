package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.CodeReference
import com.civileg.app.domain.entities.DesignCode
import kotlin.math.*

class ECPSeismic : SeismicDesign {
    
    companion object {
        // عوامل الزلازل حسب الخريطة الزلزالية المصرية (ECP 201)
        private val ZONE_FACTORS = mapOf(
            SeismicZone.ZONE_1 to 0.08,
            SeismicZone.ZONE_2 to 0.12,
            SeismicZone.ZONE_3 to 0.16,
            SeismicZone.ZONE_4 to 0.20,
            SeismicZone.ZONE_5 to 0.25
        )
        
        // عوامل التربة حسب ECP 201
        private val SOIL_FACTORS = mapOf(
            SoilType.A to 1.0,
            SoilType.B to 1.2,
            SoilType.C to 1.5,
            SoilType.D to 1.8,
            SoilType.E to 2.0
        )
        
        // فترات الانتقال لطيف الاستجابة (مثال لتربة نوع C)
        private const val T0 = 0.10   // ثانية
        private const val TC = 0.40   // ثانية
        private const val TD = 2.0    // ثانية
    }

    override fun calculateBaseShear(
        totalWeight: Double,
        seismicZone: SeismicZone,
        soilType: SoilType,
        importanceFactor: Double,
        responseModificationFactor: Double
    ): SeismicBaseShearResult {
        val warnings = mutableListOf<String>()
        
        val Z = ZONE_FACTORS[seismicZone] ?: 0.15
        val S = SOIL_FACTORS[soilType] ?: 1.2
        
        // معادلة القص الأساسي المبسطة: V = (Z * I * S / R) * W
        // ملاحظة: في الكود المصري الفعلي تعتمد على طيف الاستجابة Sa(T1)
        val baseShear = (Z * importanceFactor * S / responseModificationFactor.coerceAtLeast(1.0)) * totalWeight
        
        // حدود حسب الكود المصري (عادة 2% كحد أدنى)
        val minBaseShear = 0.02 * totalWeight
        val finalBaseShear = max(baseShear, minBaseShear)
        
        if (baseShear < minBaseShear) {
            warnings.add("Base shear increased to minimum (2% of weight) per ECP")
        }
        
        return SeismicBaseShearResult(
            baseShear = finalBaseShear,
            zoneFactor = Z,
            soilFactor = S,
            importanceFactor = importanceFactor,
            responseModification = responseModificationFactor,
            calculationFormula = "V = (Z × I × S / R) × W",
            codeReference = CodeReference.ECP.SEISMIC_BASE_SHEAR,
            warnings = warnings
        )
    }

    override fun getResponseSpectrum(
        period: Double,
        dampingRatio: Double
    ): SpectrumValue {
        // طيف الاستجابة التصميمي Sd(T) حسب الكود المصري
        val S = 1.2 // مثال لعامل التربة
        val eta = sqrt(10.0 / (5.0 + dampingRatio * 100.0)).coerceAtLeast(0.5)
        
        val spectralAcceleration = when {
            period <= T0 -> S * (1.0 + (period / T0) * (2.5 * eta - 1.0))
            period <= TC -> S * 2.5 * eta
            period <= TD -> S * 2.5 * eta * (TC / period)
            else -> S * 2.5 * eta * (TC * TD / (period * period))
        }
        
        return SpectrumValue(
            spectralAcceleration = spectralAcceleration,
            period = period,
            dampingRatio = dampingRatio,
            description = "ECP 201 Response Spectrum"
        )
    }

    override fun distributeSeismicForces(
        baseShear: Double,
        floorWeights: List<Double>,
        floorHeights: List<Double>
    ): List<SeismicForceDistribution> {
        val n = floorWeights.size
        if (n == 0 || n != floorHeights.size) return emptyList()
        
        // Fi = (Wi * hi) / Σ(Wj * hj) * V
        val whSum = floorWeights.zip(floorHeights).sumOf { it.first * it.second }
        
        val results = mutableListOf<SeismicForceDistribution>()
        var currentStoryShear = baseShear
        
        for (i in n - 1 downTo 0) {
            val lateralForce = if (whSum > 0) {
                (floorWeights[i] * floorHeights[i] / whSum) * baseShear
            } else 0.0
            
            val distribution = SeismicForceDistribution(
                floorIndex = i,
                floorWeight = floorWeights[i],
                floorHeight = floorHeights[i],
                lateralForce = lateralForce,
                storyShear = currentStoryShear,
                overturningMoment = currentStoryShear * (if (i > 0) floorHeights[i] - floorHeights[i-1] else floorHeights[i])
            )
            
            results.add(distribution)
            currentStoryShear -= lateralForce
        }
        
        return results.reversed()
    }

    override fun getCodeName(): DesignCode = DesignCode.ECP
    
    override fun getSeismicZones(): List<SeismicZone> = SeismicZone.values().toList()
    
    override fun getZoneFactors(): Map<SeismicZone, Double> = ZONE_FACTORS
    
    override fun getSoilFactors(): Map<SoilType, Double> = SOIL_FACTORS
}
