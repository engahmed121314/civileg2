package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.CodeReference
import com.civileg.app.domain.entities.DesignCode
import kotlin.math.*

class ECPSeismic : SeismicDesign {
    
    /**
     * معاملات التربة حسب الكود المصري للمنشآت الخرسانية (ECP 201)
     * s: Soil Factor, tb: بداية مرحلة الثبات، tc: نهاية مرحلة الثبات، td: بداية مرحلة الانتقال للإزاحة
     */
    private data class SoilParameters(val s: Double, val tb: Double, val tc: Double, val td: Double)

    companion object {
        // عجلة الأرض التصميمية (ag/g) - الخريطة الزلزالية المصرية الحديثة
        private val ZONE_FACTORS = mapOf(
            SeismicZone.ZONE_1 to 0.10,
            SeismicZone.ZONE_2 to 0.125,
            SeismicZone.ZONE_3 to 0.15,
            SeismicZone.ZONE_4 to 0.20,
            SeismicZone.ZONE_5 to 0.25
        )
        
        // معاملات التربة (طيف النوع الأول - يستخدم لمعظم مناطق الجمهورية)
        private val SOIL_PARAMS = mapOf(
            SoilType.A to SoilParameters(1.0, 0.05, 0.25, 1.2),
            SoilType.B to SoilParameters(1.35, 0.05, 0.25, 1.2),
            SoilType.C to SoilParameters(1.5, 0.10, 0.25, 1.2),
            SoilType.D to SoilParameters(1.8, 0.10, 0.30, 1.2),
            SoilType.E to SoilParameters(1.6, 0.10, 0.25, 1.2)
        )
    }

    /**
     * حساب القص القاعدي (Base Shear) وفقاً لطيف الاستجابة التصميمي Sd(T)
     * V = Sd(T1) * W
     * @param totalWeight الوزن الكلي للمنشأ (W)
     * @param seismicZone المنطقة الزلزالية حسب الخريطة
     * @param soilType نوع التربة بالموقع
     * @param importanceFactor معامل الأهمية (I)
     * @param responseModificationFactor معامل تعديل الاستجابة (R)
     */
    override fun calculateBaseShear(
        totalWeight: Double,
        seismicZone: SeismicZone,
        soilType: SoilType,
        importanceFactor: Double,
        responseModificationFactor: Double,
        buildingHeight: Double
    ): SeismicBaseShearResult {
        val warnings = mutableListOf<String>()
        
        // ارتفاع المنشأ - يُقدر من الوزن إذا لم يُحدد
        // تقدير: W ≈ 12 kN/m² × A_floor × n_floors, H ≈ 3m × n_floors
        val h = if (buildingHeight > 0) buildingHeight else {
            val estimatedFloors = max(1.0, totalWeight / 500.0).coerceAtMost(30.0)
            val h_est = 3.0 * estimatedFloors
            warnings.add("Estimated building height: %.1fm (from weight %.0f kN). Pass buildingHeight for accuracy.".format(h_est, totalWeight))
            h_est
        }
        
        val ag = ZONE_FACTORS[seismicZone] ?: 0.15
        val params = SOIL_PARAMS[soilType] ?: SOIL_PARAMS[SoilType.C]!!
        
        // حساب T1 = Ct * H^0.75 (حسب الكود المصري للمنشآت الإطارية)
        val period = 0.075 * h.pow(0.75) 
        
        // حساب قيمة طيف الاستجابة التصميمي Sd(T)
        val sdValue = calculateSd(period, ag, importanceFactor, params, responseModificationFactor)
        
        val baseShear = sdValue * totalWeight
        val minBaseShear = 0.02 * totalWeight
        
        if (baseShear < minBaseShear) {
            warnings.add("Base shear increased to minimum (2% of weight) per ECP")
        }
        
        return SeismicBaseShearResult(
            baseShear = max(baseShear, minBaseShear),
            zoneFactor = ag,
            soilFactor = params.s,
            importanceFactor = importanceFactor,
            responseModification = responseModificationFactor,
            calculationFormula = "V = Sd(T1) × W",
            codeReference = CodeReference.ECP.SEISMIC_BASE_SHEAR,
            warnings = warnings
        )
    }

    private fun calculateSd(t: Double, ag: Double, i: Double, p: SoilParameters, r: Double): Double {
        val common = ag * i * p.s
        val rEff = r.coerceAtLeast(1.0)
        
        return when {
            t <= p.tb -> common * ((2.0 / 3.0) + (t / p.tb) * (2.5 / rEff - 2.0 / 3.0))
            t <= p.tc -> common * (2.5 / rEff)
            t <= p.td -> max(common * (2.5 / rEff) * (p.tc / t), 0.2 * ag * i)
            else -> max(common * (2.5 / rEff) * (p.tc * p.td / (t * t)), 0.2 * ag * i)
        }
    }

    override fun getResponseSpectrum(
        period: Double,
        dampingRatio: Double,
        soilType: SoilType,
        peakGroundAcceleration: Double,
        importanceFactor: Double
    ): SpectrumValue {
        val ag = if (peakGroundAcceleration > 0) peakGroundAcceleration else 0.15
        val i = importanceFactor
        val p = SOIL_PARAMS[soilType] ?: SOIL_PARAMS[SoilType.C]!!
        val eta = sqrt(10.0 / (5.0 + dampingRatio * 100.0)).coerceAtLeast(0.55)
        
        val spectralAcceleration = when {
            period <= p.tb -> ag * i * p.s * (1.0 + (period / p.tb) * (2.5 * eta - 1.0))
            period <= p.tc -> ag * i * p.s * 2.5 * eta
            period <= p.td -> ag * i * p.s * 2.5 * eta * (p.tc / period)
            else -> ag * i * p.s * 2.5 * eta * (p.tc * p.td / (period * period))
        }
        
        val usingDefaults = peakGroundAcceleration <= 0
        val desc = if (usingDefaults) {
            "ECP 201 Response Spectrum [default ag=0.15, I=%.1f, %s]".format(i, soilType.displayName)
        } else {
            "ECP 201 Response Spectrum [ag=%.2f, I=%.1f, %s]".format(ag, i, soilType.displayName)
        }
        
        return SpectrumValue(
            spectralAcceleration = spectralAcceleration,
            period = period,
            dampingRatio = dampingRatio,
            description = desc
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
    
    override fun getSeismicZones(): List<SeismicZone> = SeismicZone.entries.toList()
    
    override fun getZoneFactors(): Map<SeismicZone, Double> = ZONE_FACTORS
    
    override fun getSoilFactors(): Map<SoilType, Double> = SOIL_PARAMS.mapValues { it.value.s }
}
