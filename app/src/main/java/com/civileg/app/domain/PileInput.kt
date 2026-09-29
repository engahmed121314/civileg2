package com.civileg.app.domain

/**
 * Pile foundation design input parameters.
 * Supports driven, bored, CFA, and micropile types
 * with clay, sand, rock, and mixed soil types.
 */
data class PileInput(
    val pileType: PileType = PileType.BORED,
    val pileDiameter: Double = 600.0,       // mm
    val pileLength: Double = 15.0,          // m
    val numberOfPiles: Int = 4,
    val spacing: Double = 3.0,              // x diameter
    val axialLoad: Double = 2000.0,         // kN
    val lateralLoad: Double = 100.0,        // kN
    val momentLoad: Double = 50.0,          // kN.m
    val fcu: Double = 30.0,                 // MPa
    val fy: Double = 400.0,                 // MPa
    val fyp: Double = 400.0,                // MPa (pile rebar)
    val soilType: SoilType = SoilType.CLAY,
    val cu: Double = 50.0,                  // kPa (undrained shear strength for clay)
    val phi: Double = 30.0,                 // degrees (friction angle for sand)
    val gammaSoil: Double = 18.0,           // kN/m3
    val waterTableDepth: Double = 5.0,      // m
    val embedmentDepth: Double = 1.5,       // m (into bearing layer)
    val safetyFactor: Double = 3.0,
    val pileGroupPattern: String = "2x2",
    val eccentricityX: Double = 0.0,        // m
    val eccentricityY: Double = 0.0,        // m
    val scourDepth: Double = 0.0,           // m
    // Pile cap inputs
    val capConcreteCover: Double = 75.0,    // mm
    val columnWidth: Double = 400.0,        // mm
    val columnLength: Double = 400.0        // mm
)

enum class PileType {
    DRIVEN {
        override val displayName: String = "Driven Pile"
        override val alpha: Double = 1.0
        override val betaFactor: Double = 0.7
    },
    BORED {
        override val displayName: String = "Bored Pile"
        override val alpha: Double = 0.5
        override val betaFactor: Double = 0.5
    },
    CFA {
        override val displayName: String = "CFA Pile"
        override val alpha: Double = 0.7
        override val betaFactor: Double = 0.6
    },
    MICROPILE {
        override val displayName: String = "Micropile"
        override val alpha: Double = 0.4
        override val betaFactor: Double = 0.4
    };

    abstract val displayName: String
    abstract val alpha: Double      // adhesion factor for clay
    abstract val betaFactor: Double // displacement factor for sand
}

enum class SoilType {
    CLAY {
        override val displayName: String = "Clay"
    },
    SAND {
        override val displayName: String = "Sand"
    },
    ROCK {
        override val displayName: String = "Rock"
    },
    MIXED {
        override val displayName: String = "Mixed Soil"
    };

    abstract val displayName: String
}

/**
 * Additional input parameters for pile cap design.
 */
data class PileCapInput(
    val axialLoad: Double,             // kN (total on pile cap)
    val momentX: Double = 0.0,         // kN.m
    val momentY: Double = 0.0,         // kN.m
    val lateralLoad: Double = 0.0,     // kN
    val numberOfPiles: Int = 4,
    val pileDiameter: Double = 600.0,  // mm
    val pileSpacing: Double = 1800.0,  // mm
    val columnWidth: Double = 400.0,   // mm
    val columnLength: Double = 400.0,  // mm
    val fcu: Double = 30.0,            // MPa
    val fy: Double = 400.0,            // MPa
    val cover: Double = 75.0,          // mm
    val pileGroupPattern: String = "2x2",
    val eccentricityX: Double = 0.0,   // m
    val eccentricityY: Double = 0.0    // m
)

/**
 * Additional input parameters for pile group analysis.
 */
data class PileGroupInput(
    val numberOfPiles: Int = 4,
    val pileDiameter: Double = 600.0,  // mm
    val spacing: Double = 3.0,         // x diameter
    val pattern: String = "2x2",
    val soilType: SoilType = SoilType.CLAY,
    val pileLength: Double = 15.0,     // m
    val pileType: PileType = PileType.BORED
)
