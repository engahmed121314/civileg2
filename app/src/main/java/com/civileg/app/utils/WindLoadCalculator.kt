package com.civileg.app.utils

import kotlin.math.*

// ============================================================
// Enumerations
// ============================================================

enum class TerrainCategory(val label: String, val powerExponent: Double, val description: String) {
    SEA_COAST("Sea Coast", 0.10, "Open sea coast with no obstructions"),
    OPEN_TERRAIN("Open Terrain", 0.14, "Open terrain with few obstructions"),
    SUBURBAN("Suburban", 0.22, "Suburban and industrial areas"),
    URBAN("Urban", 0.33, "Urban areas with closely spaced obstructions")
}

enum class BuildingShape(val label: String) {
    RECTANGULAR("Rectangular"),
    CIRCULAR("Circular"),
    L_SHAPED("L-Shaped")
}

enum class RoofType(val label: String) {
    FLAT("Flat"),
    GABLED("Gabled"),
    HIP("Hip")
}

// ============================================================
// Data classes
// ============================================================

data class WindLoadInput(
    val basicWindSpeed: Double = 30.0,       // m/s (Vb)
    val terrainCategory: TerrainCategory = TerrainCategory.SUBURBAN,
    val buildingHeight: Double = 20.0,       // m (h)
    val buildingWidth: Double = 15.0,        // m (b) – along wind
    val buildingDepth: Double = 10.0,        // m (d) – across wind
    val buildingShape: BuildingShape = BuildingShape.RECTANGULAR,
    val roofType: RoofType = RoofType.FLAT,
    val roofSlope: Double = 0.0,             // degrees (for pitched roofs)
    val importanceFactor: Double = 1.0,      // k1
    val topographyFactor: Double = 1.0,      // k3
    val numberOfFloors: Int = 5,
    val openingsInWindward: Boolean = false,
    val isFlexibleStructure: Boolean = false,
    val naturalFrequency: Double = 1.0,      // Hz (for flexible)
    val dampingRatio: Double = 0.02          // for flexible
)

data class WindLoadResult(
    val designWindSpeed: Double,             // Vz at top (m/s)
    val designWindPressure: Double,          // pz at top (kN/m²)
    val gustFactor: Double,                  // Gf
    val externalPressureWindward: Double,    // kN/m²
    val externalPressureLeeward: Double,     // kN/m²
    val externalPressureSide: Double,        // kN/m²
    val externalPressureRoof: Double,        // kN/m²
    val internalPressure: Double,            // kN/m²
    val netPressureWindward: Double,         // kN/m²
    val netPressureLeeward: Double,          // kN/m²
    val totalBaseShear: Double,              // kN
    val overturningMoment: Double,           // kN·m
    val zVsHeightProfile: List<Pair<Double, Double>>, // (height, Vz)
    val pressureDistribution: List<WindPressureAtHeight> // per floor
)

data class WindPressureAtHeight(
    val height: Double,                      // m
    val velocity: Double,                    // m/s
    val dynamicPressure: Double,             // kN/m²
    val externalPressure: Double,            // kN/m²
    val internalPressure: Double,            // kN/m²
    val netPressure: Double                  // kN/m²
)

// ============================================================
// Constants
// ============================================================

/**
 * Air density ρ = 1.2 kg/m³  →  0.6 * ρ = 0.6 * 1.2 = 0.72
 * Dynamic pressure: pz = 0.6 * Vz² (N/m²) = 0.0006 * Vz² (kN/m²)
 */
private const val AIR_DENSITY = 1.2            // kg/m³
private const val DYNAMIC_PRESSURE_COEFF = 0.5 // 0.5 * ρ
private const val REFERENCE_HEIGHT = 10.0       // m (for terrain multipliers)
private const val MIN_CALCULATION_HEIGHT = 1.0  // m (minimum z for k2)

// Terrain roughness parameters: (power exponent, gradient height in m)
private val TERRAIN_PARAMS = mapOf(
    TerrainCategory.SEA_COAST to Pair(0.10, 250.0),
    TerrainCategory.OPEN_TERRAIN to Pair(0.14, 300.0),
    TerrainCategory.SUBURBAN to Pair(0.22, 350.0),
    TerrainCategory.URBAN to Pair(0.33, 400.0)
)

// ============================================================
// WindLoadCalculator
// ============================================================

class WindLoadCalculator {

    // ----------------------------------------------------------
    // Public API
    // ----------------------------------------------------------

    /**
     * Full wind load calculation per standard provisions.
     * Uses power-law velocity profile, gust factors, and
     * pressure coefficients for rectangular buildings.
     */
    fun calculate(input: WindLoadInput): WindLoadResult {
        val floorHeight = input.buildingHeight / input.numberOfFloors
        val hWdRatio = input.buildingHeight / input.buildingWidth
        val hDdRatio = input.buildingHeight / input.buildingDepth

        // 1) Height-velocity profile at each floor level
        val zVsHeightProfile = mutableListOf<Pair<Double, Double>>()
        for (i in 1..input.numberOfFloors) {
            val z = i * floorHeight
            val k2 = getTerrainHeightMultiplier(input.terrainCategory, z)
            val vz = input.basicWindSpeed * input.importanceFactor * k2 * input.topographyFactor
            zVsHeightProfile.add(Pair(z, vz))
        }

        // 2) Gust factor
        val gustFactor = calculateGustFactor(input)

        // 3) Pressure coefficients at roof level (design level)
        val cpWindward = getExternalPressureCoefficient(
            surface = "windward",
            shape = input.buildingShape,
            hwdRatio = hWdRatio,
            roofSlope = input.roofSlope
        )
        val cpLeeward = getExternalPressureCoefficient(
            surface = "leeward",
            shape = input.buildingShape,
            hwdRatio = hWdRatio,
            roofSlope = input.roofSlope
        ).let { base ->
            // Leeward varies with h/d
            interpolateLeewardCp(hDdRatio, base)
        }
        val cpSide = getExternalPressureCoefficient(
            surface = "side",
            shape = input.buildingShape,
            hwdRatio = hWdRatio,
            roofSlope = input.roofSlope
        )
        val cpRoof = getExternalPressureCoefficient(
            surface = "roof",
            shape = input.buildingShape,
            hwdRatio = hWdRatio,
            roofSlope = input.roofSlope
        )
        val cpi = getInternalPressureCoefficient(input.openingsInWindward)

        // 4) Design wind speed and pressure at roof height
        val designWindSpeed = zVsHeightProfile.lastOrNull()?.second ?: 0.0
        val designDynamicPressure =
            DYNAMIC_PRESSURE_COEFF * AIR_DENSITY * designWindSpeed.pow(2) / 1000.0 // kN/m²
        val designWindPressure = designDynamicPressure * gustFactor

        // 5) External pressures at roof level
        val externalPressureWindward = designWindPressure * cpWindward
        val externalPressureLeeward = designWindPressure * cpLeeward
        val externalPressureSide = designWindPressure * cpSide
        val externalPressureRoof = designWindPressure * cpRoof
        val internalPressure = designWindPressure * cpi

        // 6) Net pressures
        val netPressureWindward = externalPressureWindward - internalPressure
        val netPressureLeeward = externalPressureLeeward + internalPressure  // both suction → add

        // 7) Pressure distribution per floor
        val pressureDistribution = zVsHeightProfile.map { (z, vz) ->
            val dynPressure = DYNAMIC_PRESSURE_COEFF * AIR_DENSITY * vz.pow(2) / 1000.0
            val designPressure = dynPressure * gustFactor
            val extP = designPressure * cpWindward
            val intP = designPressure * cpi
            WindPressureAtHeight(
                height = z,
                velocity = vz,
                dynamicPressure = designPressure,
                externalPressure = extP,
                internalPressure = intP,
                netPressure = extP - intP
            )
        }

        // 8) Base shear
        val totalBaseShear = calculateBaseShear(
            pressures = pressureDistribution,
            width = input.buildingWidth,
            floorHeight = floorHeight
        )

        // 9) Overturning moment
        val shearPerFloor = pressureDistribution.map { it.height to (it.netPressure * input.buildingWidth * floorHeight) }
        val overturningMoment = calculateOverturningMoment(shearPerFloor)

        return WindLoadResult(
            designWindSpeed = designWindSpeed,
            designWindPressure = designWindPressure,
            gustFactor = gustFactor,
            externalPressureWindward = externalPressureWindward,
            externalPressureLeeward = externalPressureLeeward,
            externalPressureSide = externalPressureSide,
            externalPressureRoof = externalPressureRoof,
            internalPressure = internalPressure,
            netPressureWindward = netPressureWindward,
            netPressureLeeward = netPressureLeeward,
            totalBaseShear = totalBaseShear,
            overturningMoment = overturningMoment,
            zVsHeightProfile = zVsHeightProfile,
            pressureDistribution = pressureDistribution
        )
    }

    // ----------------------------------------------------------
    // Terrain height multiplier k2
    // ----------------------------------------------------------

    /**
     * Power-law terrain multiplier: k2 = (z / z_ref) ^ alpha
     * where alpha is the terrain roughness exponent.
     * For z < z_ref, linear interpolation from ground level.
     * For z <= 0, returns a small positive value.
     */
    fun getTerrainHeightMultiplier(terrain: TerrainCategory, height: Double): Double {
        val params = TERRAIN_PARAMS[terrain] ?: TERRAIN_PARAMS[TerrainCategory.SUBURBAN]!!
        val alpha = params.first
        val gradientHeight = params.second

        val z = height.coerceAtLeast(0.0)

        return when {
            z <= MIN_CALCULATION_HEIGHT -> {
                // Linear ramp from 0 at ground to k2 at MIN_CALCULATION_HEIGHT
                val k2AtMin = (MIN_CALCULATION_HEIGHT / REFERENCE_HEIGHT).pow(alpha)
                k2AtMin * (z / MIN_CALCULATION_HEIGHT)
            }
            z <= REFERENCE_HEIGHT -> {
                (z / REFERENCE_HEIGHT).pow(alpha)
            }
            z <= gradientHeight -> {
                (z / REFERENCE_HEIGHT).pow(alpha)
            }
            else -> {
                // Above gradient height → use gradient height value (constant)
                (gradientHeight / REFERENCE_HEIGHT).pow(alpha)
            }
        }
    }

    // ----------------------------------------------------------
    // External pressure coefficient Cp
    // ----------------------------------------------------------

    /**
     * Returns the external pressure coefficient Cp for the given surface.
     *
     * Surface options: "windward", "leeward", "side", "roof"
     *
     * Rectangular buildings (IS 875-3 / ASCE 7 style):
     *   Windward: +0.8 (h/w <= 1), +0.7 (h/w > 1)
     *   Leeward: -0.5 base, varies with h/d
     *   Side: -0.7
     *   Roof flat: -0.7 to -1.2 by zone
     *   Roof pitched < 10°: -0.7 (windward slope), -0.5 (leeward slope)
     *   Roof pitched 10-30°: varies
     *
     * Circular: reduced coefficients per codes
     * L-shaped: conservative rectangular values with shape factor
     */
    fun getExternalPressureCoefficient(
        surface: String,
        shape: BuildingShape,
        hwdRatio: Double,
        roofSlope: Double
    ): Double {
        val shapeFactor = when (shape) {
            BuildingShape.RECTANGULAR -> 1.0
            BuildingShape.CIRCULAR -> 0.8   // reduced drag
            BuildingShape.L_SHAPED -> 1.1   // increased turbulence
        }

        val cp = when (surface.lowercase()) {
            "windward" -> getWindwardCp(hwdRatio)
            "leeward" -> getLeewardBaseCp(hwdRatio)
            "side" -> getSideCp(hwdRatio)
            "roof" -> getRoofCp(roofSlope, hwdRatio)
            else -> 0.8
        }

        return cp * shapeFactor
    }

    // ----------------------------------------------------------
    // Internal pressure coefficient Cpi
    // ----------------------------------------------------------

    /**
     * Internal pressure coefficient:
     *   ±0.5 when there are dominant openings on the windward face
     *   ±0.2 when the building is generally enclosed
     */
    fun getInternalPressureCoefficient(openings: Boolean): Double {
        return if (openings) 0.5 else 0.2
    }

    // ----------------------------------------------------------
    // Gust factor Gf
    // ----------------------------------------------------------

    /**
     * Calculates the gust factor for the design.
     *
     * For rigid structures: Gf = 1 + g_f * r * sqrt(B(1 + β_f²))
     * Simplified approach based on IS 875-3 / ASCE 7:
     *   - Rigid: Gf = 1 + 0.25 * sqrt(B)   (simplified)
     *   - Flexible: accounts for dynamic amplification near natural frequency
     *
     * Where:
     *   B = background turbulence factor (depends on terrain and building size)
     *   g_f = peak factor ≈ 3.5
     *   r = roughness factor
     *   β_f = damping ratio
     */
    fun calculateGustFactor(input: WindLoadInput): Double {
        val terrain = input.terrainCategory
        val h = input.buildingHeight
        val b = input.buildingWidth

        // Background turbulence factor
        val backgroundFactor = calculateBackgroundFactor(terrain, h, b)

        return if (input.isFlexibleStructure) {
            // Dynamic gust factor for flexible structures
            val gf = 3.5   // peak factor
            val beta = input.dampingRatio
            val fn = input.naturalFrequency

            // Size reduction factor
            val Lz = calculateIntegralLengthScale(terrain, h)
            val bOverLz = b / Lz
            val sizeReduction = (1.0 / (1.0 + 18.0 * bOverLz)).coerceIn(0.2, 1.0)

            // Resonant response factor
            val resonanceFactor = if (fn > 0) {
                val n0Lz = fn * Lz / h
                val E = 1.0 / (1.0 + 0.833 * n0Lz.pow(2))
                val Rn = (7.47 * n0Lz * sqrt(E) / (1.0 + 10.0 * beta)).coerceAtLeast(0.0)
                sizeReduction + Rn * gf
            } else {
                sizeReduction + gf * sqrt(backgroundFactor)
            }

            1.0 + gf * sqrt(backgroundFactor) * sizeReduction
        } else {
            // Simplified gust factor for rigid structures
            // Gf ≈ 1 + g * r * sqrt(B)
            val peakFactor = 3.5
            val turbulenceIntensity = getTurbulenceIntensity(terrain, h)
            val gfRigid = 1.0 + peakFactor * turbulenceIntensity * sqrt(backgroundFactor)
            gfRigid.coerceIn(1.5, 3.0)
        }
    }

    // ----------------------------------------------------------
    // Base shear calculation
    // ----------------------------------------------------------

    /**
     * Total base shear: sum of (net pressure × tributary width × floor height)
     * for all floors. Considers both windward and leeward contributions.
     */
    fun calculateBaseShear(
        pressures: List<WindPressureAtHeight>,
        width: Double,
        floorHeight: Double
    ): Double {
        var totalShear = 0.0
        for (i in pressures.indices) {
            // Tributary height: for first floor, full floor height; for others, full floor height
            val tribHeight = floorHeight
            totalShear += abs(pressures[i].netPressure) * width * tribHeight
        }
        return totalShear
    }

    // ----------------------------------------------------------
    // Overturning moment calculation
    // ----------------------------------------------------------

    /**
     * Overturning moment about the base: sum of (shear_i × height_i)
     * where shear_i is the force at each floor level and height_i is
     * the height of that floor above the base.
     */
    fun calculateOverturningMoment(shearPerFloor: List<Pair<Double, Double>>): Double {
        var moment = 0.0
        for ((height, shear) in shearPerFloor) {
            moment += abs(shear) * height
        }
        return moment
    }

    // ============================================================
    // Private helpers – External pressure coefficients
    // ============================================================

    private fun getWindwardCp(hOverW: Double): Double {
        // Windward wall Cp: +0.8 for h/w <= 1, +0.7 for h/w > 1
        // Linear interpolation for intermediate ratios
        return if (hOverW <= 1.0) {
            0.8
        } else if (hOverW <= 2.0) {
            // Linear interpolation from 0.8 to 0.7
            0.8 - 0.1 * (hOverW - 1.0)
        } else {
            0.7
        }
    }

    private fun getLeewardBaseCp(hOverW: Double): Double {
        // Leeward base Cp: -0.5 for typical h/w ratios
        return if (hOverW <= 0.5) {
            -0.3
        } else if (hOverW <= 1.0) {
            -0.3 - 0.4 * (hOverW - 0.5) // -0.3 to -0.5
        } else if (hOverW <= 2.0) {
            -0.5
        } else {
            -0.6
        }
    }

    /**
     * Interpolates leeward Cp based on h/d ratio.
     * h/d <= 0.5: -0.3
     * h/d = 1.0:  -0.5
     * h/d = 2.0:  -0.6
     * h/d >= 4.0: -0.75
     */
    private fun interpolateLeewardCp(hOverD: Double, baseCp: Double): Double {
        return when {
            hOverD <= 0.5 -> -0.3
            hOverD <= 1.0 -> -0.3 - 0.4 * (hOverD - 0.5)   // -0.3 to -0.5
            hOverD <= 2.0 -> -0.5 - 0.1 * (hOverD - 1.0)   // -0.5 to -0.6
            hOverD <= 4.0 -> -0.6 - 0.075 * (hOverD - 2.0) // -0.6 to -0.75
            else -> -0.75
        }
    }

    private fun getSideCp(hOverW: Double): Double {
        // Side wall: -0.7 uniformly for rectangular buildings
        // For tall slender buildings, slightly more suction at top
        return if (hOverW <= 2.0) {
            -0.7
        } else {
            -0.7 - 0.05 * (hOverW - 2.0).coerceAtMost(1.0) // up to -0.75
        }
    }

    /**
     * Roof Cp depends on roof type and slope:
     * - Flat: zone-dependent, here we use the most critical zone (-1.2 at corners, -0.7 mid)
     *   Average: -0.8 for general design
     * - Gabled/Hip < 10°: -0.7 windward slope, -0.5 leeward slope → average -0.6
     * - Gabled/Hip 10-30°: linearly varying from -0.5 to +0.2 windward, -0.5 leeward
     */
    private fun getRoofCp(roofSlope: Double, hOverW: Double): Double {
        // Normalize slope to 0-90 range
        val slope = roofSlope.coerceIn(0.0, 90.0)

        return if (slope < 10.0) {
            // Nearly flat to low slope: -0.7 to -0.8
            // More suction at very low slopes
            if (slope < 5.0) {
                -0.8 - 0.04 * (5.0 - slope)  // -0.8 to -1.0
            } else {
                -0.7 - 0.02 * (10.0 - slope)  // -0.7 to -0.8
            }
        } else if (slope <= 30.0) {
            // Medium slope: transition zone
            // Windward slope goes from -0.7 toward +0.2, leeward stays around -0.5
            // Net average effect
            val fraction = (slope - 10.0) / 20.0
            -0.7 + 0.9 * fraction  // -0.7 to +0.2
        } else {
            // Steep slope > 30°: pressure on windward, suction on leeward
            val fraction = ((slope - 30.0) / 60.0).coerceAtMost(1.0)
            0.2 + 0.3 * fraction  // +0.2 to +0.5
        }
    }

    // ============================================================
    // Private helpers – Gust factor components
    // ============================================================

    /**
     * Background turbulence factor B.
     * Depends on terrain category, building height, and width.
     * B ≈ 1 / (1 + 18 * (b + h) / (2 * Lz))
     */
    private fun calculateBackgroundFactor(
        terrain: TerrainCategory,
        height: Double,
        width: Double
    ): Double {
        val Lz = calculateIntegralLengthScale(terrain, height)
        val bPlusH = width + height
        val ratio = bPlusH / (2.0 * Lz)
        return (1.0 / (1.0 + 18.0 * ratio)).coerceIn(0.1, 1.0)
    }

    /**
     * Integral length scale of turbulence Lz.
     * Lz ≈ 100 * (z / 10)^0.25 for suburban terrain, varies by category.
     */
    private fun calculateIntegralLengthScale(terrain: TerrainCategory, height: Double): Double {
        val z = height.coerceAtLeast(10.0)
        val baseFactor = when (terrain) {
            TerrainCategory.SEA_COAST -> 180.0
            TerrainCategory.OPEN_TERRAIN -> 150.0
            TerrainCategory.SUBURBAN -> 100.0
            TerrainCategory.URBAN -> 70.0
        }
        return baseFactor * (z / 10.0).pow(0.25)
    }

    /**
     * Turbulence intensity I(z) at height z.
     * I(z) = k_rough / ln(z / z0)
     * Simplified per terrain category:
     */
    private fun getTurbulenceIntensity(terrain: TerrainCategory, height: Double): Double {
        val z = height.coerceAtLeast(MIN_CALCULATION_HEIGHT)
        val (z0, kRough) = when (terrain) {
            TerrainCategory.SEA_COAST -> 0.002 to 0.14
            TerrainCategory.OPEN_TERRAIN -> 0.03 to 0.17
            TerrainCategory.SUBURBAN -> 0.3 to 0.22
            TerrainCategory.URBAN -> 1.0 to 0.30
        }
        val lnZz0 = ln(z / z0).coerceAtLeast(0.5)
        return (kRough / lnZz0).coerceIn(0.05, 0.40)
    }

    // ============================================================
    // Utility: generate k2 table for a given terrain
    // ============================================================

    /**
     * Returns a list of (height, k2) pairs at standard heights.
     * Useful for displaying the terrain profile.
     */
    fun getK2Table(terrain: TerrainCategory, maxHeight: Double): List<Pair<Double, Double>> {
        val step = if (maxHeight <= 20) 2.0 else if (maxHeight <= 100) 5.0 else 10.0
        val table = mutableListOf<Pair<Double, Double>>()
        var z = step
        while (z <= maxHeight + 0.001) {
            table.add(Pair(z, getTerrainHeightMultiplier(terrain, z)))
            z += step
        }
        return table
    }

    // ============================================================
    // Utility: along-wind displacement for flexible structures
    // ============================================================

    /**
     * Approximate tip displacement x_H for a cantilever building
     * under wind load:
     *   x_H = Gf * ρ * Vz² * Cd * A * H³ / (3 * E * I_eff)
     * Simplified: returns in mm given assumed stiffness.
     */
    fun estimateTipDisplacement(
        input: WindLoadInput,
        result: WindLoadResult,
        stiffnessEI: Double // kN·m²
    ): Double {
        if (!input.isFlexibleStructure || stiffnessEI <= 0) return 0.0
        // Simplified: F = total base shear, x = F*H³/(3*EI) for cantilever
        val force = result.totalBaseShear
        val h = input.buildingHeight
        return (force * h.pow(3) / (3.0 * stiffnessEI)) * 1000.0 // mm
    }
}
