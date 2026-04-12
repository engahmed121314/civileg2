package com.civileg.app.utils

/**
 * Ratio Management System
 * يدير النسب المختلفة المستخدمة في الحسابات (نسبة الهالك، النسب المصنعية، إلخ)
 */
object RatioManager {

    // ==================== Material Waste Factors ====================
    data class WastageFactors(
        var steelWastage: Double = 1.10,            // 10% wastage for steel
        var concreteWastage: Double = 1.05,         // 5% wastage for concrete
        var sandWastage: Double = 1.08,             // 8% wastage for sand
        var stoneChippingsWastage: Double = 1.08,   // 8% wastage for stone
        var cementWastage: Double = 1.05,           // 5% wastage for cement
        var laborWastage: Double = 1.15             // 15% wastage for labor
    ) {
        fun isValid(): Boolean {
            return steelWastage >= 1.0 && concreteWastage >= 1.0 &&
                    sandWastage >= 1.0 && stoneChippingsWastage >= 1.0 &&
                    cementWastage >= 1.0 && laborWastage >= 1.0
        }

        fun getAverageWastage(): Double {
            return (steelWastage + concreteWastage + sandWastage + stoneChippingsWastage + cementWastage) / 5
        }
    }

    // ==================== Concrete Mix Ratios ====================
    data class ConcreteMixRatio(
        val name: String,
        val description: String,
        val cementRatio: Double,
        val sandRatio: Double,
        val stoneRatio: Double,
        val waterRatio: Double,
        val strengthMPa: Double,
        val slump: Int                  // mm
    )

    val concreteMixRatios = mapOf(
        "1:2:4" to ConcreteMixRatio(
            "1:2:4 Mix",
            "Weak concrete for leveling",
            1.0, 2.0, 4.0, 0.6, 15.0, 50
        ),
        "1:1.5:3" to ConcreteMixRatio(
            "1:1.5:3 Mix",
            "Ordinary concrete - 20 MPa",
            1.0, 1.5, 3.0, 0.5, 20.0, 75
        ),
        "1:1.5:3 (C25)" to ConcreteMixRatio(
            "1:1.5:3 Mix",
            "Good concrete - 25 MPa",
            1.0, 1.5, 3.0, 0.45, 25.0, 100
        ),
        "1:1:2 (C30)" to ConcreteMixRatio(
            "1:1:2 Mix",
            "High strength concrete - 30 MPa",
            1.0, 1.0, 2.0, 0.40, 30.0, 100
        ),
        "1:0.8:1.6 (C35)" to ConcreteMixRatio(
            "1:0.8:1.6 Mix",
            "Very high strength - 35 MPa",
            1.0, 0.8, 1.6, 0.35, 35.0, 125
        ),
        "1:0.7:1.4 (C40)" to ConcreteMixRatio(
            "1:0.7:1.4 Mix",
            "Premium concrete - 40 MPa",
            1.0, 0.7, 1.4, 0.30, 40.0, 150
        )
    )

    // ==================== Presets ====================
    object RatioPresets {
        val STANDARD_CONSTRUCTION = WastageFactors(
            steelWastage = 1.10,
            concreteWastage = 1.05,
            sandWastage = 1.08,
            stoneChippingsWastage = 1.08,
            cementWastage = 1.05,
            laborWastage = 1.15
        )

        val EFFICIENT_CONSTRUCTION = WastageFactors(
            steelWastage = 1.05,
            concreteWastage = 1.03,
            sandWastage = 1.05,
            stoneChippingsWastage = 1.05,
            cementWastage = 1.03,
            laborWastage = 1.10
        )

        val CONSERVATIVE_CONSTRUCTION = WastageFactors(
            steelWastage = 1.15,
            concreteWastage = 1.08,
            sandWastage = 1.10,
            stoneChippingsWastage = 1.10,
            cementWastage = 1.08,
            laborWastage = 1.20
        )

        val SITE_CONDITIONS_POOR = WastageFactors(
            steelWastage = 1.20,
            concreteWastage = 1.12,
            sandWastage = 1.15,
            stoneChippingsWastage = 1.15,
            cementWastage = 1.12,
            laborWastage = 1.25
        )

        val SITE_CONDITIONS_EXCELLENT = WastageFactors(
            steelWastage = 1.03,
            concreteWastage = 1.02,
            sandWastage = 1.03,
            stoneChippingsWastage = 1.03,
            cementWastage = 1.02,
            laborWastage = 1.08
        )
    }

    // ==================== Design Code Safety Factors ====================
    data class DesignSafetyFactors(
        val concreteStrengthFactor: Double,
        val steelStrengthFactor: Double,
        val loadFactor: Double,
        val description: String
    )

    val designCodeFactors = mapOf(
        "EGYPTIAN" to DesignSafetyFactors(
            concreteStrengthFactor = 1.0,
            steelStrengthFactor = 1.0,
            loadFactor = 1.5,
            description = "Egyptian Code - ECP 203"
        ),
        "ACI" to DesignSafetyFactors(
            concreteStrengthFactor = 0.8,
            steelStrengthFactor = 0.9,
            loadFactor = 1.2,
            description = "ACI 318 - American Code"
        ),
        "SAUDI" to DesignSafetyFactors(
            concreteStrengthFactor = 0.85,
            steelStrengthFactor = 0.95,
            loadFactor = 1.35,
            description = "SBC - Saudi Building Code"
        )
    )

    // ==================== Cover Adjustments ====================
    data class CoverAdjustment(
        val environmentalClass: String,
        val coverIncreaseFactor: Double,
        val description: String
    )

    val coverAdjustments = mapOf(
        "INDOOR" to CoverAdjustment(
            "Indoor - Dry",
            1.0,
            "Protected from moisture and aggressive environment"
        ),
        "HUMID" to CoverAdjustment(
            "Humid/Splash",
            1.15,
            "Exposed to humidity or splash zones"
        ),
        "MARINE" to CoverAdjustment(
            "Marine",
            1.30,
            "Exposed to seawater or salt spray"
        ),
        "AGGRESSIVE" to CoverAdjustment(
            "Aggressive Chemical",
            1.40,
            "Exposed to aggressive chemicals or sewage"
        ),
        "WATER_RETAINING" to CoverAdjustment(
            "Water Retaining",
            1.25,
            "Water retaining structures or underground"
        )
    )

    // ==================== Bar Spacing Adjustments ====================
    data class SpacingAdjustment(
        val spacingType: String,
        val minimumSpacingMm: Double,
        val description: String
    )

    val spacingAdjustments = mapOf(
        "MINIMUM" to SpacingAdjustment(
            "Minimum",
            25.0,
            "Minimum clear spacing between bars"
        ),
        "STANDARD" to SpacingAdjustment(
            "Standard",
            30.0,
            "Standard spacing for normal casting"
        ),
        "LARGE_AGGREGATE" to SpacingAdjustment(
            "Large Aggregate",
            40.0,
            "For large stone chippings (20mm+)"
        ),
        "CONGESTED" to SpacingAdjustment(
            "Congested Area",
            50.0,
            "For heavily reinforced sections"
        )
    )

    // ==================== Concrete Curing Adjustments ====================
    data class CuringAdjustment(
        val curingType: String,
        val daysRequired: Int,
        val strengthGainFactor: Double,
        val description: String
    )

    val curingAdjustments = mapOf(
        "STANDARD" to CuringAdjustment(
            "Standard (Wet)",
            7,
            1.0,
            "Normal wet curing - 100% strength at 28 days"
        ),
        "ACCELERATED" to CuringAdjustment(
            "Accelerated Heat",
            3,
            1.05,
            "Hot water or steam curing - faster strength gain"
        ),
        "WINTER" to CuringAdjustment(
            "Winter Conditions",
            14,
            0.85,
            "Extended curing in cold weather - 85% strength at 28 days"
        ),
        "DRY_CLIMATE" to CuringAdjustment(
            "Dry Climate",
            10,
            0.90,
            "Additional curing days due to evaporation - 90% strength"
        ),
        "HIGH_STRENGTH" to CuringAdjustment(
            "High Strength",
            28,
            1.00,
            "Extended curing for high strength concrete"
        )
    )

    // ==================== Deflection Multipliers ====================
    data class DeflectionMultiplier(
        val condition: String,
        val factor: Double,
        val description: String
    )

    val deflectionMultipliers = mapOf(
        "SIMPLY_SUPPORTED" to DeflectionMultiplier(
            "Simply Supported",
            1.0,
            "Base case for simply supported beams"
        ),
        "CONTINUOUS" to DeflectionMultiplier(
            "Continuous",
            0.65,
            "Continuous beams - less deflection"
        ),
        "CANTILEVER" to DeflectionMultiplier(
            "Cantilever",
            2.5,
            "Cantilever - more deflection"
        ),
        "ONE_END_CONTINUOUS" to DeflectionMultiplier(
            "One End Continuous",
            0.85,
            "One end continuous - moderate deflection"
        )
    )

    // ==================== Ratio Configuration ====================
    data class RatioConfiguration(
        val name: String,
        val wastageFactors: WastageFactors,
        val concreteMixRatio: ConcreteMixRatio,
        val safetyFactors: DesignSafetyFactors,
        val coverAdjustment: CoverAdjustment,
        val curingAdjustment: CuringAdjustment,
        val createdDate: Long = System.currentTimeMillis(),
        val isDefault: Boolean = false
    )

    // ==================== Management Functions ====================
    fun getDefaultConfiguration(): RatioConfiguration {
        return RatioConfiguration(
            name = "Standard Configuration",
            wastageFactors = RatioPresets.STANDARD_CONSTRUCTION,
            concreteMixRatio = concreteMixRatios["1:1.5:3 (C25)"]!!,
            safetyFactors = designCodeFactors["EGYPTIAN"]!!,
            coverAdjustment = coverAdjustments["INDOOR"]!!,
            curingAdjustment = curingAdjustments["STANDARD"]!!,
            isDefault = true
        )
    }

    fun createCustomConfiguration(
        name: String,
        wastageFactors: WastageFactors,
        mixRatioKey: String,
        codeKey: String,
        environmentKey: String,
        curingKey: String
    ): RatioConfiguration? {
        return if (wastageFactors.isValid()) {
            RatioConfiguration(
                name = name,
                wastageFactors = wastageFactors,
                concreteMixRatio = concreteMixRatios[mixRatioKey] ?: concreteMixRatios["1:1.5:3 (C25)"]!!,
                safetyFactors = designCodeFactors[codeKey] ?: designCodeFactors["EGYPTIAN"]!!,
                coverAdjustment = coverAdjustments[environmentKey] ?: coverAdjustments["INDOOR"]!!,
                curingAdjustment = curingAdjustments[curingKey] ?: curingAdjustments["STANDARD"]!!
            )
        } else null
    }

    fun getConfigurationByName(name: String): RatioConfiguration? {
        return when (name) {
            "Standard" -> RatioConfiguration(
                name = "Standard Configuration",
                wastageFactors = RatioPresets.STANDARD_CONSTRUCTION,
                concreteMixRatio = concreteMixRatios["1:1.5:3 (C25)"]!!,
                safetyFactors = designCodeFactors["EGYPTIAN"]!!,
                coverAdjustment = coverAdjustments["INDOOR"]!!,
                curingAdjustment = curingAdjustments["STANDARD"]!!
            )
            "Efficient" -> RatioConfiguration(
                name = "Efficient Configuration",
                wastageFactors = RatioPresets.EFFICIENT_CONSTRUCTION,
                concreteMixRatio = concreteMixRatios["1:1.5:3 (C25)"]!!,
                safetyFactors = designCodeFactors["EGYPTIAN"]!!,
                coverAdjustment = coverAdjustments["INDOOR"]!!,
                curingAdjustment = curingAdjustments["STANDARD"]!!
            )
            "Conservative" -> RatioConfiguration(
                name = "Conservative Configuration",
                wastageFactors = RatioPresets.CONSERVATIVE_CONSTRUCTION,
                concreteMixRatio = concreteMixRatios["1:1.5:3 (C25)"]!!,
                safetyFactors = designCodeFactors["EGYPTIAN"]!!,
                coverAdjustment = coverAdjustments["HUMID"]!!,
                curingAdjustment = curingAdjustments["STANDARD"]!!
            )
            "Poor Site Conditions" -> RatioConfiguration(
                name = "Poor Site Conditions",
                wastageFactors = RatioPresets.SITE_CONDITIONS_POOR,
                concreteMixRatio = concreteMixRatios["1:1.5:3 (C25)"]!!,
                safetyFactors = designCodeFactors["EGYPTIAN"]!!,
                coverAdjustment = coverAdjustments["HUMID"]!!,
                curingAdjustment = curingAdjustments["WINTER"]!!
            )
            "Excellent Site Conditions" -> RatioConfiguration(
                name = "Excellent Site Conditions",
                wastageFactors = RatioPresets.SITE_CONDITIONS_EXCELLENT,
                concreteMixRatio = concreteMixRatios["1:1.5:3 (C25)"]!!,
                safetyFactors = designCodeFactors["EGYPTIAN"]!!,
                coverAdjustment = coverAdjustments["INDOOR"]!!,
                curingAdjustment = curingAdjustments["STANDARD"]!!
            )
            else -> null
        }
    }

    fun getAvailablePresets(): List<String> {
        return listOf(
            "Standard",
            "Efficient",
            "Conservative",
            "Poor Site Conditions",
            "Excellent Site Conditions"
        )
    }

    fun validateConfiguration(config: RatioConfiguration): List<String> {
        val errors = mutableListOf<String>()
        
        if (!config.wastageFactors.isValid()) {
            errors.add("Invalid wastage factors - must be >= 1.0")
        }
        
        if (config.concreteMixRatio.strengthMPa <= 0) {
            errors.add("Invalid concrete strength")
        }
        
        if (config.curingAdjustment.daysRequired <= 0) {
            errors.add("Invalid curing days")
        }

        return errors
    }
}

