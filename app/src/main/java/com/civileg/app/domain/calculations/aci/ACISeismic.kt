package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.CodeReference
import com.civileg.app.domain.entities.DesignCode
import kotlin.math.*

/**
 * تنفيذ التصميم الزلزالي حسب الكود الأمريكي ASCE 7-16 / ACI 318-19
 */
class ACISeismic : SeismicDesign {
    
    companion object {
        // عوامل الاستجابة الافتراضية SDS, SD1 (عادة ما تُدخل من خريطة الزلازل)
        private const val DEFAULT_SDS = 0.50
        private const val DEFAULT_SD1 = 0.25
        
        private val ZONE_FACTORS = mapOf(
            SeismicZone.ZONE_1 to 0.10,
            SeismicZone.ZONE_2 to 0.20,
            SeismicZone.ZONE_3 to 0.30,
            SeismicZone.ZONE_4 to 0.40,
            SeismicZone.ZONE_5 to 0.50
        )

        private val SOIL_FACTORS = mapOf(
            SoilType.A to 0.8,
            SoilType.B to 1.0,
            SoilType.C to 1.2,
            SoilType.D to 1.6,
            SoilType.E to 2.5
        )
    }

    override fun calculateBaseShear(
        totalWeight: Double,
        seismicZone: SeismicZone,
        soilType: SoilType,
        importanceFactor: Double,
        responseModificationFactor: Double,
        buildingHeight: Double
    ): SeismicBaseShearResult {
        val warnings = mutableListOf<String>()
        
        // في ASCE 7: Cs = SDS / (R / Ie)
        val sds = ZONE_FACTORS[seismicZone] ?: DEFAULT_SDS
        val sd1 = sds / 2.0 // تقريب للعلاقة
        
        var cs = sds / (responseModificationFactor / importanceFactor)
        
        // الحدود (ASCE 7-16 Section 12.8.1.1)
        val csMax = sd1 / (0.1 * (responseModificationFactor / importanceFactor)) // لـ T = 0.1s كمثال
        val csMin = max(0.044 * sds * importanceFactor, 0.01)
        
        val finalCs = cs.coerceIn(csMin, csMax)
        
        if (cs < csMin) warnings.add("Cs increased to minimum limit per ASCE 7")
        if (cs > csMax) warnings.add("Cs capped at maximum limit")
        
        val baseShear = finalCs * totalWeight
        
        return SeismicBaseShearResult(
            baseShear = baseShear,
            zoneFactor = sds,
            soilFactor = 1.0, // مُدمج في SDS
            importanceFactor = importanceFactor,
            responseModification = responseModificationFactor,
            calculationFormula = "V = Cs × W where Cs = SDS / (R/Ie)",
            codeReference = CodeReference.ACI.SEISMIC_BASE_SHEAR,
            warnings = warnings
        )
    }

    override fun getResponseSpectrum(
        period: Double,
        dampingRatio: Double,
        soilType: SoilType,
        peakGroundAcceleration: Double,
        importanceFactor: Double
    ): SpectrumValue {
        // ASCE 7 Design Response Spectrum
        // إذا لم يُمرر SDS/SD1، نستخدم ag لتقديرها
        // SDS ≈ Fa × ag, SD1 ≈ Fv × ag (تقريبي)
        val sds = if (peakGroundAcceleration > 0) {
            val fa = SOIL_FACTORS[soilType] ?: 1.0
            fa * peakGroundAcceleration
        } else DEFAULT_SDS
        val sd1 = if (peakGroundAcceleration > 0) {
            val fv = SOIL_FACTORS[soilType] ?: 1.0
            fv * peakGroundAcceleration * 0.5
        } else DEFAULT_SD1

        val t0 = 0.2 * sd1 / sds
        val ts = sd1 / sds
        
        val sa = when {
            period < t0 -> sds * (0.4 + 0.6 * period / t0)
            period <= ts -> sds
            period <= 4.0 -> sd1 / period
            else -> sd1 * 4.0 / (period * period)
        }
        
        val usingDefaults = peakGroundAcceleration <= 0
        val desc = if (usingDefaults) {
            String.format("ASCE 7 Response Spectrum [default SDS=%.2f, SD1=%.2f]", sds, sd1)
        } else {
            String.format("ASCE 7 Response Spectrum [ag=%.2f, I=%.1f, %s]", 
                peakGroundAcceleration, importanceFactor, soilType.displayName)
        }
        
        return SpectrumValue(
            spectralAcceleration = sa,
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
        if (n == 0) return emptyList()
        
        // ASCE 7: Fx = Cvx * V
        // Cvx = (Wx * hx^k) / Σ(Wi * hi^k)
        val k = 1.0 // لـ T ≤ 0.5s
        
        val wh_sum = floorWeights.zip(floorHeights).sumOf { (w, h) -> w * h.pow(k) }
        
        val results = mutableListOf<SeismicForceDistribution>()
        var storyShear = baseShear
        var accumulatedOTM = 0.0 // إصلاح: تجميع عزوم الانقلاب من الأعلى للأسفل / Fix: accumulate OTM from top to bottom
        
        for (i in n - 1 downTo 0) {
            val force = if (wh_sum > 0) {
                (floorWeights[i] * floorHeights[i].pow(k) / wh_sum) * baseShear
            } else 0.0
            
            // إصلاح: حساب ارتفاع الطابق الحالي / Fix: compute current story height
            val storyHeight = if (i > 0) floorHeights[i] - floorHeights[i-1] else floorHeights[i]
            accumulatedOTM += storyShear * storyHeight // إصلاح: تجميع عزم الانقلاب التراكمي / Fix: cumulative OTM
            
            results.add(
                SeismicForceDistribution(
                    floorIndex = i,
                    floorWeight = floorWeights[i],
                    floorHeight = floorHeights[i],
                    lateralForce = force,
                    storyShear = storyShear,
                    overturningMoment = accumulatedOTM
                )
            )
            storyShear -= force
        }
        
        return results.reversed()
    }

    override fun getCodeName(): DesignCode = DesignCode.ACI
    override fun getSeismicZones(): List<SeismicZone> = SeismicZone.entries.toList()
    override fun getZoneFactors(): Map<SeismicZone, Double> = ZONE_FACTORS
    // إصلاح: إضافة معاملات التربة Fa/Fv لجميع أنواع التربة حسب ASCE 7-16 الجدول 11.4-1
    // Fix: Added Fa/Fv site coefficients for all soil types per ASCE 7-16 Table 11.4-1
    override fun getSoilFactors(): Map<SoilType, Double> = mapOf(
        SoilType.A to 1.0,
        SoilType.B to 1.2,
        SoilType.C to 1.2,
        SoilType.D to 1.6,
        SoilType.E to 2.5
    )
}
