package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.aci.ACISeismic
import com.civileg.app.domain.calculations.base.*
import com.civileg.core.calculations.entities.CodeReference
import com.civileg.core.calculations.entities.DesignCode
import kotlin.math.*

/**
 * تنفيذ التصميم الزلزالي حسب الكود السعودي SBC 301
 */
class SBCSeismic : SeismicDesign {
    
    private val aciSeismic = ACISeismic()
    
    companion object {
        // W7-FIX: Official Saudi Seismic Hazard Map zone factors (SBC 301-2007).
        // These are the Z (PGA-based) zone factors used directly as the design
        // spectral parameter (SDS) in the closed form. Previous version used
        // incorrectly-doubled SDS-only values.
        private val SAUDI_ZONE_FACTORS = mapOf(
            SeismicZone.ZONE_1 to 0.05, // Low
            SeismicZone.ZONE_2 to 0.10,
            SeismicZone.ZONE_3 to 0.15,
            SeismicZone.ZONE_4 to 0.25, // High (e.g. Gulf of Aqaba)
            SeismicZone.ZONE_5 to 0.35
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
        // W7-FIX: the old path delegated to ACISeismic (whose ZONE_FACTORS
        // differ) and then only RELABELED zoneFactor with the Saudi value —
        // base shear stayed computed at the ACI hazard level (ZONE_4 was
        // computed @0.40 but reported @0.25). SBC 301 adopts ASCE 7 mechanics
        // with the SAUDI hazard map, so the full expression is recomputed here.
        val warnings = mutableListOf<String>()

        val sds = SAUDI_ZONE_FACTORS[seismicZone] ?: 0.10
        val sd1 = sds / 2.0

        var cs = sds / (responseModificationFactor / importanceFactor)

        // Upper/lower bounds (ASCE 7-16 §12.8.1.1, adopted by SBC 301)
        val csMax = sd1 / (0.1 * (responseModificationFactor / importanceFactor))
        val csMin = max(0.044 * sds * importanceFactor, 0.01)

        val finalCs = cs.coerceIn(csMin, csMax)

        if (cs < csMin) warnings.add("Cs increased to minimum limit per SBC 301/ASCE 7")
        if (cs > csMax) warnings.add("Cs capped at maximum limit")

        return SeismicBaseShearResult(
            baseShear = finalCs * totalWeight,
            zoneFactor = sds,
            soilFactor = 1.0, // مُدمج في SDS
            importanceFactor = importanceFactor,
            responseModification = responseModificationFactor,
            calculationFormula = "SBC 301: V = Cs × W, Cs = SDS/(R/Ie) at Saudi SDS",
            codeReference = CodeReference.SBC.SEISMIC_BASE_SHEAR,
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
        val result = aciSeismic.getResponseSpectrum(
            period, dampingRatio, soilType, peakGroundAcceleration, importanceFactor
        )
        return result.copy(
            description = "SBC 301 " + result.description.removePrefix("ASCE 7 ")
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
    override fun getSeismicZones(): List<SeismicZone> = SeismicZone.entries.toList()
    override fun getZoneFactors(): Map<SeismicZone, Double> = SAUDI_ZONE_FACTORS
    override fun getSoilFactors(): Map<SoilType, Double> = aciSeismic.getSoilFactors()
}
