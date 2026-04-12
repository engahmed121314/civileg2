package com.civileg.app.domain.calculations.base

import com.civileg.app.domain.entities.DesignCode

/**
 * واجهة موحدة للتصميم الزلزالي حسب أي كود إنشائي
 * تغطي: حساب قوة القص الأساسية، طيف الاستجابة، وتوزيع القوى على الأدوار
 */
interface SeismicDesign {
    
    /**
     * حساب قوة القص الأساسية للمبنى
     * حسب المعادلة العامة: V = (Z * I * S / R) * W
     */
    fun calculateBaseShear(
        totalWeight: Double,              // kN - الوزن الكلي للمبنى
        seismicZone: SeismicZone,
        soilType: SoilType,
        importanceFactor: Double,         // I
        responseModificationFactor: Double // R
    ): SeismicBaseShearResult

    /**
     * حساب طيف الاستجابة للعناصر
     */
    fun getResponseSpectrum(
        period: Double,                   // ثانية - الدور الذاتي
        dampingRatio: Double              // نسبة التخميد
    ): SpectrumValue

    /**
     * توزيع قوى الزلزال على الأدوار
     */
    fun distributeSeismicForces(
        baseShear: Double,
        floorWeights: List<Double>,       // kN - وزن كل دور
        floorHeights: List<Double>        // m - ارتفاع كل دور من الأساس
    ): List<SeismicForceDistribution>

    // معلومات الكود
    fun getCodeName(): DesignCode
    fun getSeismicZones(): List<SeismicZone>
    fun getZoneFactors(): Map<SeismicZone, Double>
    fun getSoilFactors(): Map<SoilType, Double>
}

data class SeismicBaseShearResult(
    val baseShear: Double,                // kN
    val zoneFactor: Double,               // Z
    val soilFactor: Double,               // S
    val importanceFactor: Double,         // I
    val responseModification: Double,     // R
    val calculationFormula: String,
    val codeReference: String,
    val warnings: List<String> = emptyList()
)

data class SpectrumValue(
    val spectralAcceleration: Double,     // g أو m/s²
    val period: Double,
    val dampingRatio: Double,
    val description: String
)

data class SeismicForceDistribution(
    val floorIndex: Int,
    val floorWeight: Double,
    val floorHeight: Double,
    val lateralForce: Double,             // kN
    val storyShear: Double,               // kN - القص التراكمي
    val overturningMoment: Double         // kN.m
)

enum class SeismicZone(val displayName: String) {
    ZONE_1("Zone 1 - Low"),
    ZONE_2("Zone 2 - Moderate"),
    ZONE_3("Zone 3 - High"),
    ZONE_4("Zone 4 - Very High"),
    ZONE_5("Zone 5 - Extreme")
}

enum class SoilType(val displayName: String, val description: String) {
    A("Type A", "Hard Rock"),
    B("Type B", "Rock"),
    C("Type C", "Dense Soil"),
    D("Type D", "Medium Soil"),
    E("Type E", "Soft Soil")
}
