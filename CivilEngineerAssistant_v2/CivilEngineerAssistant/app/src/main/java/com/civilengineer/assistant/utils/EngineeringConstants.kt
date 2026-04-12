package com.civilengineer.assistant.utils

import com.civilengineer.assistant.models.DesignCode

/**
 * ثوابت هندسية أساسية تستخدم في جميع الحسابات
 * Engineering constants used across all calculations
 */
object EngineeringConstants {

    // ═══════════════════════════════════════════════════════════
    // معاملات الأمان - Safety Factors
    // ═══════════════════════════════════════════════════════════

    fun getConcretePartialSafetyFactor(code: DesignCode): Double = when (code) {
        DesignCode.EGYPTIAN -> 1.50   // γc = 1.50 (ECP 203)
        DesignCode.SAUDI -> 1.50      // γc = 1.50 (SBC 304)
        DesignCode.AMERICAN -> 1.0    // ACI uses φ factor approach
    }

    fun getSteelPartialSafetyFactor(code: DesignCode): Double = when (code) {
        DesignCode.EGYPTIAN -> 1.15   // γs = 1.15 (ECP 203)
        DesignCode.SAUDI -> 1.15      // γs = 1.15 (SBC 304)
        DesignCode.AMERICAN -> 1.0    // ACI uses φ factor approach
    }

    // معامل تخفيض المقاومة (ACI)
    object PhiFactor {
        const val FLEXURE = 0.90
        const val SHEAR = 0.75
        const val COMPRESSION_TIED = 0.65
        const val COMPRESSION_SPIRAL = 0.75
        const val BEARING = 0.65
        const val TORSION = 0.75
    }

    // ═══════════════════════════════════════════════════════════
    // معاملات الأحمال - Load Factors
    // ═══════════════════════════════════════════════════════════

    fun getUltimateLoadFactor(code: DesignCode): Pair<Double, Double> = when (code) {
        DesignCode.EGYPTIAN -> Pair(1.40, 1.60)   // 1.4D + 1.6L (ECP)
        DesignCode.SAUDI -> Pair(1.40, 1.60)       // 1.4D + 1.6L (SBC)
        DesignCode.AMERICAN -> Pair(1.20, 1.60)    // 1.2D + 1.6L (ACI)
    }

    fun getDeadLoadFactor(code: DesignCode): Double = getUltimateLoadFactor(code).first
    fun getLiveLoadFactor(code: DesignCode): Double = getUltimateLoadFactor(code).second

    // تجميعات الأحمال
    fun getLoadCombinations(code: DesignCode): List<LoadCombination> = when (code) {
        DesignCode.EGYPTIAN -> listOf(
            LoadCombination("U1", "1.4D + 1.6L", 1.4, 1.6, 0.0, 0.0),
            LoadCombination("U2", "0.8(1.4D + 1.6L + 1.6W)", 1.12, 1.28, 1.28, 0.0),
            LoadCombination("U3", "1.12D + αL + S", 1.12, 0.5, 0.0, 1.0),
            LoadCombination("U4", "0.9D + 1.3W", 0.9, 0.0, 1.3, 0.0),
        )
        DesignCode.SAUDI -> listOf(
            LoadCombination("U1", "1.4D + 1.6L", 1.4, 1.6, 0.0, 0.0),
            LoadCombination("U2", "1.2D + 1.0L + 1.0E", 1.2, 1.0, 0.0, 1.0),
            LoadCombination("U3", "1.2D + 1.6L + 0.5Lr", 1.2, 1.6, 0.0, 0.0),
            LoadCombination("U4", "0.9D + 1.0E", 0.9, 0.0, 0.0, 1.0),
        )
        DesignCode.AMERICAN -> listOf(
            LoadCombination("U1", "1.4D", 1.4, 0.0, 0.0, 0.0),
            LoadCombination("U2", "1.2D + 1.6L", 1.2, 1.6, 0.0, 0.0),
            LoadCombination("U3", "1.2D + 1.0L + 1.0E", 1.2, 1.0, 0.0, 1.0),
            LoadCombination("U4", "1.2D + 1.6L + 0.5Lr", 1.2, 1.6, 0.0, 0.0),
            LoadCombination("U5", "1.2D + 1.0W + 1.0L", 1.2, 1.0, 1.0, 0.0),
            LoadCombination("U6", "0.9D + 1.0W", 0.9, 0.0, 1.0, 0.0),
            LoadCombination("U7", "0.9D + 1.0E", 0.9, 0.0, 0.0, 1.0),
        )
    }

    data class LoadCombination(
        val name: String,
        val expression: String,
        val deadFactor: Double,
        val liveFactor: Double,
        val windFactor: Double,
        val seismicFactor: Double
    )

    // ═══════════════════════════════════════════════════════════
    // الغطاء الخرساني - Concrete Cover
    // ═══════════════════════════════════════════════════════════

    fun getCover(code: DesignCode, exposure: ExposureCondition = ExposureCondition.MODERATE): Double = when (code) {
        DesignCode.EGYPTIAN -> when (exposure) {
            ExposureCondition.MILD -> 25.0
            ExposureCondition.MODERATE -> 30.0
            ExposureCondition.SEVERE -> 35.0
            ExposureCondition.VERY_SEVERE -> 40.0
            ExposureCondition.EXTREME -> 50.0
        }
        DesignCode.SAUDI -> when (exposure) {
            ExposureCondition.MILD -> 25.0
            ExposureCondition.MODERATE -> 30.0
            ExposureCondition.SEVERE -> 40.0
            ExposureCondition.VERY_SEVERE -> 45.0
            ExposureCondition.EXTREME -> 50.0
        }
        DesignCode.AMERICAN -> when (exposure) {
            ExposureCondition.MILD -> 20.0
            ExposureCondition.MODERATE -> 25.0
            ExposureCondition.SEVERE -> 38.0
            ExposureCondition.VERY_SEVERE -> 50.0
            ExposureCondition.EXTREME -> 63.0
        }
    }

    enum class ExposureCondition(val displayName: String) {
        MILD("بيئة معتدلة - داخلي"),
        MODERATE("بيئة متوسطة"),
        SEVERE("بيئة شديدة"),
        VERY_SEVERE("بيئة شديدة جداً"),
        EXTREME("بيئة متطرفة - بحري")
    }

    // ═══════════════════════════════════════════════════════════
    // نسب التسليح - Reinforcement Ratios
    // ═══════════════════════════════════════════════════════════

    // نسب تسليح الأعمدة
    fun getColumnMinRatio(code: DesignCode): Double = when (code) {
        DesignCode.EGYPTIAN -> 0.008  // 0.8%
        DesignCode.SAUDI -> 0.01     // 1.0%
        DesignCode.AMERICAN -> 0.01  // 1.0%
    }

    fun getColumnMaxRatio(code: DesignCode): Double = when (code) {
        DesignCode.EGYPTIAN -> 0.04  // 4% (6% at splices)
        DesignCode.SAUDI -> 0.04    // 4%
        DesignCode.AMERICAN -> 0.08 // 8%
    }

    // نسب تسليح الكمرات
    fun getBeamMinRatio(code: DesignCode, fcu: Double, fy: Double): Double = when (code) {
        DesignCode.EGYPTIAN -> maxOf(0.25 * Math.sqrt(fcu) / fy, 1.3 / fy)
        DesignCode.SAUDI -> maxOf(0.25 * Math.sqrt(fcu) / fy, 1.4 / fy)
        DesignCode.AMERICAN -> maxOf(0.25 * Math.sqrt(fcu * 0.8) / fy, 1.4 / fy)
    }

    fun getBeamMaxRatio(code: DesignCode, fcu: Double, fy: Double): Double {
        val fc = fcu * 0.8
        val beta1 = if (fc <= 28) 0.85 else maxOf(0.65, 0.85 - 0.05 * (fc - 28) / 7)
        return when (code) {
            DesignCode.EGYPTIAN -> 0.67 * beta1 * (fcu / 1.5) / (fy / 1.15) * (600.0 / (600.0 + fy / 1.15))
            DesignCode.SAUDI -> 0.75 * beta1 * fc / fy * (600.0 / (600.0 + fy))
            DesignCode.AMERICAN -> 0.75 * beta1 * 0.85 * fc / fy * (600.0 / (600.0 + fy))
        }
    }

    // نسب تسليح البلاطات
    fun getSlabMinRatio(code: DesignCode, fy: Double): Double = when (code) {
        DesignCode.EGYPTIAN -> if (fy <= 360) 0.0025 else 0.0018
        DesignCode.SAUDI -> if (fy <= 420) 0.0020 else 0.0018
        DesignCode.AMERICAN -> if (fy <= 420) 0.0020 else 0.0018
    }

    // ═══════════════════════════════════════════════════════════
    // الأحمال الحية - Live Loads
    // ═══════════════════════════════════════════════════════════

    val LIVE_LOADS = mapOf(
        "سكني" to 2.0,
        "مكتبي" to 2.5,
        "تجاري" to 4.0,
        "مخازن خفيفة" to 5.0,
        "مخازن ثقيلة" to 8.0,
        "مستشفى" to 3.0,
        "مدرسة" to 3.0,
        "مسجد" to 5.0,
        "جراج سيارات" to 2.5,
        "جراج شاحنات" to 5.0,
        "سلالم" to 4.0,
        "أسطح (مع وصول)" to 1.5,
        "أسطح (بدون وصول)" to 1.0,
        "بلكونات" to 3.0,
        "مكتبات" to 6.0,
        "قاعات اجتماعات" to 5.0,
        "مصانع خفيفة" to 5.0,
        "مصانع ثقيلة" to 10.0,
    )

    // ═══════════════════════════════════════════════════════════
    // معاملات الهالك - Waste Factors
    // ═══════════════════════════════════════════════════════════

    object WasteFactors {
        const val CONCRETE_COLUMNS = 0.025       // 2.5%
        const val CONCRETE_BEAMS = 0.025          // 2.5%
        const val CONCRETE_SLABS = 0.025          // 2.5%
        const val CONCRETE_FOUNDATIONS = 0.05     // 5%
        const val CONCRETE_WALLS = 0.03           // 3%
        const val CONCRETE_STAIRS = 0.05          // 5%

        const val STEEL_STRAIGHT = 0.03           // 3%
        const val STEEL_BENT = 0.05               // 5%
        const val STEEL_STIRRUPS = 0.07           // 7%
        const val STEEL_MESH = 0.10               // 10%
        const val STEEL_SPIRAL = 0.08             // 8%

        const val FORMWORK_STANDARD = 0.05        // 5%
        const val FORMWORK_COMPLEX = 0.10         // 10%

        fun getConcreteWaste(elementType: String): Double = when (elementType) {
            "column" -> CONCRETE_COLUMNS
            "beam" -> CONCRETE_BEAMS
            "slab" -> CONCRETE_SLABS
            "foundation" -> CONCRETE_FOUNDATIONS
            "wall" -> CONCRETE_WALLS
            "stairs" -> CONCRETE_STAIRS
            else -> 0.05
        }

        fun getSteelWaste(rebarType: String): Double = when (rebarType) {
            "straight" -> STEEL_STRAIGHT
            "bent" -> STEEL_BENT
            "stirrup" -> STEEL_STIRRUPS
            "mesh" -> STEEL_MESH
            "spiral" -> STEEL_SPIRAL
            else -> 0.05
        }
    }

    // ═══════════════════════════════════════════════════════════
    // أسعار تقريبية (للتكلفة) - Unit Prices
    // ═══════════════════════════════════════════════════════════

    data class UnitPrices(
        val concretePerM3: Double,
        val steelPerTon: Double,
        val formworkPerM2: Double,
        val laborPerM3: Double,
        val currency: String
    )

    fun getDefaultPrices(currencyCode: String): UnitPrices = when (currencyCode) {
        "EGP" -> UnitPrices(3500.0, 42000.0, 120.0, 350.0, "ج.م")
        "SAR" -> UnitPrices(350.0, 3500.0, 45.0, 80.0, "ر.س")
        "USD" -> UnitPrices(120.0, 900.0, 15.0, 25.0, "$")
        "AED" -> UnitPrices(450.0, 4000.0, 55.0, 100.0, "د.إ")
        else -> UnitPrices(120.0, 900.0, 15.0, 25.0, "$")
    }

    // ═══════════════════════════════════════════════════════════
    // وحدات القياس - Units Conversion
    // ═══════════════════════════════════════════════════════════

    object UnitConversion {
        // Length
        const val M_TO_CM = 100.0
        const val M_TO_MM = 1000.0
        const val CM_TO_MM = 10.0
        const val FT_TO_M = 0.3048
        const val INCH_TO_MM = 25.4
        const val M_TO_FT = 3.28084
        const val MM_TO_INCH = 0.03937

        // Area
        const val M2_TO_CM2 = 10000.0
        const val M2_TO_MM2 = 1000000.0
        const val M2_TO_FT2 = 10.7639

        // Volume
        const val M3_TO_CM3 = 1000000.0
        const val M3_TO_LITERS = 1000.0
        const val M3_TO_FT3 = 35.3147
        const val M3_TO_GALLON = 264.172

        // Force
        const val KN_TO_N = 1000.0
        const val KN_TO_TON = 0.10197
        const val KN_TO_KGF = 101.97
        const val KN_TO_LBF = 224.809
        const val KN_TO_KIP = 0.2248

        // Stress
        const val MPA_TO_KPA = 1000.0
        const val MPA_TO_PSI = 145.038
        const val MPA_TO_KSI = 0.145038
        const val KGF_CM2_TO_MPA = 0.09807

        // Weight
        const val KG_TO_LB = 2.20462
        const val TON_TO_KG = 1000.0
        const val KG_TO_TON = 0.001

        // Moment
        const val KNM_TO_TM = 0.10197
        const val KNM_TO_KIPFT = 0.7376
    }

    // ═══════════════════════════════════════════════════════════
    // أوزان المواد - Material Densities
    // ═══════════════════════════════════════════════════════════

    object MaterialDensity {
        const val REINFORCED_CONCRETE = 25.0      // kN/m³
        const val PLAIN_CONCRETE = 23.0           // kN/m³
        const val STEEL = 78.5                     // kN/m³
        const val SOIL_AVERAGE = 18.0             // kN/m³
        const val WATER = 10.0                     // kN/m³
        const val BRICK = 19.0                     // kN/m³
        const val SAND = 16.0                      // kN/m³
        const val GRAVEL = 17.0                    // kN/m³
        const val MORTAR = 21.0                    // kN/m³
        const val PLASTER = 20.0                   // kN/m³
        const val TILES = 22.0                     // kN/m³
        const val INSULATION = 3.0                 // kN/m³
    }

    // سمك البلاطات المبدئي
    fun getInitialSlabThickness(span: Double, slabType: String, code: DesignCode): Double {
        return when (slabType) {
            "one_way" -> when (code) {
                DesignCode.EGYPTIAN -> span * 1000.0 / 25.0  // L/25
                DesignCode.SAUDI -> span * 1000.0 / 24.0
                DesignCode.AMERICAN -> span * 1000.0 / 24.0  // Simply supported L/20, Continuous L/24
            }
            "two_way" -> when (code) {
                DesignCode.EGYPTIAN -> span * 1000.0 / 32.0  // L/32
                DesignCode.SAUDI -> span * 1000.0 / 30.0
                DesignCode.AMERICAN -> span * 1000.0 / 33.0
            }
            "flat_slab" -> when (code) {
                DesignCode.EGYPTIAN -> span * 1000.0 / 32.0
                DesignCode.SAUDI -> span * 1000.0 / 30.0
                DesignCode.AMERICAN -> span * 1000.0 / 33.0
            }
            "hollow_block" -> when (code) {
                DesignCode.EGYPTIAN -> span * 1000.0 / 20.0  // L/20
                DesignCode.SAUDI -> span * 1000.0 / 18.0
                DesignCode.AMERICAN -> span * 1000.0 / 18.5
            }
            "cantilever" -> when (code) {
                DesignCode.EGYPTIAN -> span * 1000.0 / 10.0  // L/10
                DesignCode.SAUDI -> span * 1000.0 / 10.0
                DesignCode.AMERICAN -> span * 1000.0 / 10.0
            }
            else -> span * 1000.0 / 25.0
        }
    }
}
