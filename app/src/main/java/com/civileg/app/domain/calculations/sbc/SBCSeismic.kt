package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.aci.ACISeismic
import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.CodeReference
import com.civileg.app.domain.entities.DesignCode
import kotlin.math.*

/**
 * تنفيذ التصميم الزلزالي حسب الكود السعودي SBC 301
 */
class SBCSeismic : SeismicDesign {
    
    private val aciSeismic = ACISeismic()
    
    companion object {
        // عوامل المناطق الزلزالية في المملكة العربية السعودية (SBC 301)
        private val SAUDI_ZONE_FACTORS = mapOf(
            SeismicZone.ZONE_1 to 0.05, // تبوك/جيزان (منخفضة)
            SeismicZone.ZONE_2 to 0.10,
            SeismicZone.ZONE_3 to 0.15,
            SeismicZone.ZONE_4 to 0.25, // خليج العقبة (عالية)
            SeismicZone.ZONE_5 to 0.35
        )
    }

    override fun calculateBaseShear(
        totalWeight: Double,
        seismicZone: SeismicZone,
        soilType: SoilType,
        importanceFactor: Double,
        responseModificationFactor: Double
    ): SeismicBaseShearResult {
        // SBC 301 يتبع ASCE 7 مع خريطة مخاطر سعودية
        val result = aciSeismic.calculateBaseShear(
            totalWeight, seismicZone, soilType, importanceFactor, responseModificationFactor
        )
        
        val saudiFactor = SAUDI_ZONE_FACTORS[seismicZone] ?: 0.10
        
        return result.copy(
            zoneFactor = saudiFactor,
            codeReference = CodeReference.SBC.SEISMIC_BASE_SHEAR,
            calculationFormula = "SBC 301: V = Cs × W"
        )
    }

    override fun getResponseSpectrum(
        period: Double,
        dampingRatio: Double
    ): SpectrumValue {
        return aciSeismic.getResponseSpectrum(period, dampingRatio).copy(
            description = "SBC 301 Design Response Spectrum"
        )
    }

    override fun distributeSeismicForces(
        baseShear: Double,
        floorWeights: List<Double>,
        floorHeights: List<Double>
    ): List<SeismicForceDistribution> {
        return aciSeismic.distributeSeismicForces(baseShear, floorWeights, floorHeights)
    }

    override fun getCodeName(): DesignCode = DesignCode.SBC
    override fun getSeismicZones(): List<SeismicZone> = SeismicZone.values().toList()
    override fun getZoneFactors(): Map<SeismicZone, Double> = SAUDI_ZONE_FACTORS
    override fun getSoilFactors(): Map<SoilType, Double> = aciSeismic.getSoilFactors()
}
