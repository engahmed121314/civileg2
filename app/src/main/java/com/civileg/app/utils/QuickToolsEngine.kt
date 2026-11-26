package com.civileg.app.utils

import kotlin.math.*

/**
 * Civil Engineering Quick Tools Engine.
 * Provides high-precision field calculations for site engineers and structural designers:
 * 1. Instant Rebar Weight & Count ($W = D^2 / 162$).
 * 2. Equivalent Rebar Substitution ($A_{s,\text{req}} \rightarrow A_{s,\text{prov}}$).
 * 3. Development Length ($L_d$) & Lap Splice ($L_{\text{splice}}$) under ECP 203, ACI 318, and SBC 304.
 * 4. Concrete Volume & Material Batch Estimator (Cement bags/tons, sand, gravel, water).
 * 5. Structural Pre-Dimensioning (Beams, solid slabs, flat slabs, cantilevers, hollow-block).
 * 6. Slope, Level Difference & Ramp Grading ($\Delta H, S\%, 1:X, \theta$).
 * 7. Brickwork & Mortar Estimator (Wall area, brick count, cement bags, sand).
 * 8. Formwork Stripping Times according to code rules ($2L+2$, $4L+2$).
 * 9. Universal Unit Converter (Length, Area, Volume, Mass, Force, Stress, Moment).
 * 10. Earthwork Volume (Average End Area + Prismoidal correction).
 * 11. Plaster & Paint Estimator (mortar volume, cement bags, sand, paint liters).
 */
object QuickToolsEngine {

    // ══════════════════════════════════════════════════════════════
    // 1. REBAR WEIGHT & COUNT CALCULATOR
    // ══════════════════════════════════════════════════════════════

    data class RebarWeightResult(
        val diameterMm: Double,
        val lengthPerBarM: Double,
        val numberOfBars: Int,
        val totalLengthM: Double,
        val weightPerMeterKg: Double,
        val totalWeightKg: Double,
        val totalWeightTon: Double,
        val singleBarAreaMm2: Double,
        val totalAreaMm2: Double,
        val totalAreaCm2: Double,
        val bars12mPerTon: Int
    )

    fun calculateRebarWeight(
        diameterMm: Double,
        lengthPerBarM: Double,
        numberOfBars: Int
    ): RebarWeightResult {
        val safeDiameter = max(4.0, diameterMm)
        val safeLength = max(0.01, lengthPerBarM)
        val safeCount = max(1, numberOfBars)

        // Theoretical metric weight formula: D^2 / 162.197 kg/m
        val weightPerMeter = (safeDiameter * safeDiameter) / 162.197
        val totalLength = safeLength * safeCount
        val totalWeightKg = weightPerMeter * totalLength
        val totalWeightTon = totalWeightKg / 1000.0

        val singleBarArea = (PI * safeDiameter * safeDiameter) / 4.0
        val totalAreaMm2 = singleBarArea * safeCount
        val totalAreaCm2 = totalAreaMm2 / 100.0

        val weight12mBar = weightPerMeter * 12.0
        val barsPerTon = if (weight12mBar > 0) (1000.0 / weight12mBar).toInt() else 0

        return RebarWeightResult(
            diameterMm = safeDiameter,
            lengthPerBarM = safeLength,
            numberOfBars = safeCount,
            totalLengthM = totalLength,
            weightPerMeterKg = weightPerMeter,
            totalWeightKg = totalWeightKg,
            totalWeightTon = totalWeightTon,
            singleBarAreaMm2 = singleBarArea,
            totalAreaMm2 = totalAreaMm2,
            totalAreaCm2 = totalAreaCm2,
            bars12mPerTon = barsPerTon
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 2. EQUIVALENT REBAR SUBSTITUTION
    // ══════════════════════════════════════════════════════════════

    data class RebarSubstitutionResult(
        val origCount: Int,
        val origDiameterMm: Double,
        val origAreaMm2: Double,
        val newDiameterMm: Double,
        val requiredCount: Int,
        val providedAreaMm2: Double,
        val areaDifferenceMm2: Double,
        val percentageChange: Double,
        val isSafe: Boolean
    )

    fun calculateRebarSubstitution(
        origCount: Int,
        origDiameterMm: Double,
        newDiameterMm: Double
    ): RebarSubstitutionResult {
        val count1 = max(1, origCount)
        val d1 = max(4.0, origDiameterMm)
        val d2 = max(4.0, newDiameterMm)

        val area1Single = (PI * d1 * d1) / 4.0
        val origTotalArea = area1Single * count1

        val area2Single = (PI * d2 * d2) / 4.0
        val count2 = ceil(origTotalArea / area2Single).toInt()
        val provTotalArea = count2 * area2Single

        val diff = provTotalArea - origTotalArea
        val pct = if (origTotalArea > 0) (diff / origTotalArea) * 100.0 else 0.0

        return RebarSubstitutionResult(
            origCount = count1,
            origDiameterMm = d1,
            origAreaMm2 = origTotalArea,
            newDiameterMm = d2,
            requiredCount = count2,
            providedAreaMm2 = provTotalArea,
            areaDifferenceMm2 = diff,
            percentageChange = pct,
            isSafe = provTotalArea >= origTotalArea
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 3. DEVELOPMENT LENGTH & LAP SPLICE
    // ══════════════════════════════════════════════════════════════

    enum class CodeStandard { ECP_203, ACI_318, SBC_304 }
    enum class StressType { TENSION, COMPRESSION }
    enum class BarPosition { BOTTOM, TOP }

    data class DevelopmentLengthResult(
        val code: CodeStandard,
        val diameterMm: Double,
        val basicLdCm: Double,
        val basicLdMultiple: Double,
        val tensionSpliceClassACm: Double,
        val tensionSpliceClassBCm: Double,
        val compressionSpliceCm: Double,
        val standard90HookMm: Double,
        val standard180HookMm: Double,
        val explanation: String
    )

    fun calculateDevelopmentAndSplice(
        code: CodeStandard,
        diameterMm: Double,
        fcOrFcuMpa: Double,
        fyMpa: Double,
        stressType: StressType = StressType.TENSION,
        barPosition: BarPosition = BarPosition.BOTTOM
    ): DevelopmentLengthResult {
        val d = max(6.0, diameterMm)
        val fc = max(15.0, fcOrFcuMpa)
        val fy = max(240.0, fyMpa)

        val topBarFactor = if (barPosition == BarPosition.TOP) 1.3 else 1.0

        val (ldMm, multiple, exp) = when (code) {
            CodeStandard.ECP_203 -> {
                // ECP 203-2020: fbu = 0.3 * sqrt(fcu / 1.5)
                val fcu = fc
                val fbu = 0.3 * sqrt(fcu / 1.5)
                val alpha = 1.0 // straight bar
                val beta = topBarFactor // 1.3 for top bar (>30cm fresh concrete)
                val eta = 1.0 // deformed high grade
                val gammaS = 1.15
                val ldBasic = (alpha * beta * eta * (fy / gammaS) / (4.0 * fbu)) * d
                val minLd = max(40.0 * d, 350.0)
                val finalLd = max(ldBasic, minLd)
                val m = finalLd / d
                Triple(finalLd, m, "ECP 203: fbu = 0.3√(fcu/1.5)=${round(fbu*100)/100} MPa, β=${beta}")
            }
            CodeStandard.ACI_318 -> {
                // ACI 318-19: Simplified equation Ld = (fy * psi_t / (1.7 * lambda * sqrt(f'c))) * db for d >= 22mm
                // or / 2.1 for d <= 19mm
                val divider = if (d <= 19.0) 2.1 else 1.7
                val ldBasic = (fy * topBarFactor / (divider * sqrt(fc))) * d
                val minLd = max(300.0, ldBasic)
                val m = minLd / d
                Triple(minLd, m, "ACI 318-19: Simplified tension equation with ψt=${topBarFactor}")
            }
            CodeStandard.SBC_304 -> {
                // SBC 304-2018 adopts ACI 318 criteria
                val divider = if (d <= 19.0) 2.1 else 1.7
                val ldBasic = (fy * topBarFactor / (divider * sqrt(fc))) * d
                val minLd = max(300.0, ldBasic)
                val m = minLd / d
                Triple(minLd, m, "SBC 304-2018: Section 25.4 criteria with ψt=${topBarFactor}")
            }
        }

        val ldCm = round((ldMm / 10.0) * 10.0) / 10.0
        val spliceClassA = ldCm * 1.0
        val spliceClassB = round((ldCm * 1.3) * 10.0) / 10.0

        val compSpliceMm = when (code) {
            CodeStandard.ECP_203 -> max(0.75 * ldMm, max(40.0 * d, 350.0))
            else -> max(0.071 * fy * d, max(0.0005 * fy * d, 300.0))
        }
        val compSpliceCm = round((compSpliceMm / 10.0) * 10.0) / 10.0

        // Standard hook extensions (ACI 318 & ECP):
        // 90 deg hook extension = 12 * d
        val hook90 = 12.0 * d
        // 180 deg hook extension = 4 * d (min 65mm)
        val hook180 = max(4.0 * d, 65.0)

        return DevelopmentLengthResult(
            code = code,
            diameterMm = d,
            basicLdCm = ldCm,
            basicLdMultiple = round(multiple * 10.0) / 10.0,
            tensionSpliceClassACm = spliceClassA,
            tensionSpliceClassBCm = spliceClassB,
            compressionSpliceCm = compSpliceCm,
            standard90HookMm = hook90,
            standard180HookMm = hook180,
            explanation = exp
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 4. CONCRETE VOLUME & BATCH ESTIMATOR
    // ══════════════════════════════════════════════════════════════

    data class ConcreteBatchResult(
        val volumeM3: Double,
        val cementContentKgPerM3: Double,
        val totalCementKg: Double,
        val totalCementBags50Kg: Int,
        val totalCementTon: Double,
        val sandVolumeM3: Double,
        val gravelVolumeM3: Double,
        val waterLiters: Double
    )

    fun calculateConcreteBatch(
        volumeM3: Double,
        cementBagsPerM3: Double = 7.0 // 350 kg/m3 is typical 7 bags
    ): ConcreteBatchResult {
        val safeVol = max(0.01, volumeM3)
        val safeBagsPerM3 = max(4.0, cementBagsPerM3)
        val cementKgPerM3 = safeBagsPerM3 * 50.0

        val totalCementKg = safeVol * cementKgPerM3
        val totalBags = ceil(totalCementKg / 50.0).toInt()
        val totalTon = totalCementKg / 1000.0

        // Standard nominal proportions for 1 m3 of hardened concrete:
        // Requires ~0.44 m3 sand and ~0.88 m3 gravel (allowing for 10% compaction & site waste)
        val sandM3 = round((safeVol * 0.44) * 100.0) / 100.0
        val gravelM3 = round((safeVol * 0.88) * 100.0) / 100.0

        // Water based on water-cement ratio w/c ~ 0.48
        val waterLiters = round(totalCementKg * 0.48)

        return ConcreteBatchResult(
            volumeM3 = round(safeVol * 100.0) / 100.0,
            cementContentKgPerM3 = cementKgPerM3,
            totalCementKg = round(totalCementKg),
            totalCementBags50Kg = totalBags,
            totalCementTon = round(totalTon * 100.0) / 100.0,
            sandVolumeM3 = sandM3,
            gravelVolumeM3 = gravelM3,
            waterLiters = waterLiters
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 5. STRUCTURAL PRE-DIMENSIONING
    // ══════════════════════════════════════════════════════════════

    enum class StructuralMemberType {
        BEAM_SIMPLE,
        BEAM_CONTINUOUS_ONE_END,
        BEAM_CONTINUOUS_BOTH_ENDS,
        BEAM_CANTILEVER,
        SOLID_SLAB_ONE_WAY_SIMPLE,
        SOLID_SLAB_ONE_WAY_CONTINUOUS,
        SOLID_SLAB_CANTILEVER,
        FLAT_SLAB_WITHOUT_DROPS,
        FLAT_SLAB_WITH_DROPS,
        HORDI_SLAB_ONE_WAY
    }

    data class PreDimensioningResult(
        val memberType: StructuralMemberType,
        val spanM: Double,
        val recommendedDepthCm: Double,
        val recommendedTotalThicknessCm: Double,
        val recommendedWidthCm: Double,
        val formulaUsed: String,
        val notes: String
    )

    fun calculatePreDimensioning(
        memberType: StructuralMemberType,
        spanM: Double
    ): PreDimensioningResult {
        val L = max(0.5, spanM)
        val lCm = L * 100.0

        val (depth, totalT, width, formula, note) = when (memberType) {
            StructuralMemberType.BEAM_SIMPLE -> {
                val d = ceil(lCm / 10.0)
                val t = d + 5.0
                val b = max(20.0, ceil(t / 3.0))
                Quint(d, t, b, "d = L / 10, b ≈ t / 3", "Simple beam minimum depth to control deflection")
            }
            StructuralMemberType.BEAM_CONTINUOUS_ONE_END -> {
                val d = ceil(lCm / 12.0)
                val t = d + 5.0
                val b = max(20.0, ceil(t / 3.0))
                Quint(d, t, b, "d = L / 12, b ≈ t / 3", "Continuous beam (one end) deflection control")
            }
            StructuralMemberType.BEAM_CONTINUOUS_BOTH_ENDS -> {
                val d = ceil(lCm / 14.0)
                val t = d + 5.0
                val b = max(20.0, ceil(t / 3.0))
                Quint(d, t, b, "d = L / 14, b ≈ t / 3", "Continuous beam (both ends) deflection control")
            }
            StructuralMemberType.BEAM_CANTILEVER -> {
                val d = ceil(lCm / 5.0)
                val t = d + 5.0
                val b = max(25.0, ceil(t / 3.0))
                Quint(d, t, b, "d = L / 5, b ≥ 25cm", "Cantilever beam depth (critical for deflection & vibration)")
            }
            StructuralMemberType.SOLID_SLAB_ONE_WAY_SIMPLE -> {
                val t = max(10.0, ceil(lCm / 30.0))
                val d = t - 2.5
                Quint(d, t, 100.0, "ts = L / 30 (min 10cm)", "One-way simply supported solid slab")
            }
            StructuralMemberType.SOLID_SLAB_ONE_WAY_CONTINUOUS -> {
                val t = max(10.0, ceil(lCm / 36.0))
                val d = t - 2.5
                Quint(d, t, 100.0, "ts = L / 36 (min 10cm)", "One-way continuous solid slab")
            }
            StructuralMemberType.SOLID_SLAB_CANTILEVER -> {
                val t = max(12.0, ceil(lCm / 10.0))
                val d = t - 2.5
                Quint(d, t, 100.0, "ts = L / 10 (min 12cm)", "Cantilever balcony solid slab")
            }
            StructuralMemberType.FLAT_SLAB_WITHOUT_DROPS -> {
                val t = max(16.0, ceil(lCm / 32.0))
                val d = t - 3.0
                Quint(d, t, 100.0, "ts = L / 32 (min 16cm)", "Flat slab without drop panels (ACI / ECP)")
            }
            StructuralMemberType.FLAT_SLAB_WITH_DROPS -> {
                val t = max(15.0, ceil(lCm / 36.0))
                val d = t - 3.0
                Quint(d, t, 100.0, "ts = L / 36 (min 15cm)", "Flat slab with drop panels")
            }
            StructuralMemberType.HORDI_SLAB_ONE_WAY -> {
                val t = max(25.0, ceil(lCm / 20.0))
                val d = t - 3.0
                Quint(d, t, 100.0, "ts = L / 20 (typical 27-32cm)", "One-way hollow block / ribbed slab")
            }
        }

        return PreDimensioningResult(
            memberType = memberType,
            spanM = L,
            recommendedDepthCm = depth,
            recommendedTotalThicknessCm = totalT,
            recommendedWidthCm = width,
            formulaUsed = formula,
            notes = note
        )
    }

    private data class Quint<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )

    // ══════════════════════════════════════════════════════════════
    // 6. SLOPE & LEVEL DIFFERENCE CALCULATOR
    // ══════════════════════════════════════════════════════════════

    enum class SlopeDirection { ASCENDING, DESCENDING, LEVEL }

    data class SlopeLevelResult(
        val level1M: Double,
        val level2M: Double,
        val horizontalDistanceM: Double,
        val elevationDifferenceM: Double,
        val elevationDifferenceCm: Double,
        val slopePercentage: Double,
        val slopeRatio1ToX: Double,
        val slopeAngleDegrees: Double,
        val direction: SlopeDirection
    )

    fun calculateSlope(
        level1M: Double,
        level2M: Double,
        horizontalDistanceM: Double
    ): SlopeLevelResult {
        val L = max(0.001, horizontalDistanceM)
        val diffM = abs(level2M - level1M)
        val diffCm = diffM * 100.0

        val slopePct = (diffM / L) * 100.0
        val ratioX = if (diffM > 0.0001) L / diffM else 0.0
        val angleDeg = atan(diffM / L) * (180.0 / PI)

        val direction = when {
            level2M > level1M + 0.0001 -> SlopeDirection.ASCENDING
            level2M < level1M - 0.0001 -> SlopeDirection.DESCENDING
            else -> SlopeDirection.LEVEL
        }

        return SlopeLevelResult(
            level1M = level1M,
            level2M = level2M,
            horizontalDistanceM = L,
            elevationDifferenceM = round(diffM * 1000.0) / 1000.0,
            elevationDifferenceCm = round(diffCm * 10.0) / 10.0,
            slopePercentage = round(slopePct * 100.0) / 100.0,
            slopeRatio1ToX = round(ratioX * 10.0) / 10.0,
            slopeAngleDegrees = round(angleDeg * 100.0) / 100.0,
            direction = direction
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 7. BRICKWORK & MORTAR ESTIMATOR
    // ══════════════════════════════════════════════════════════════

    enum class BrickType {
        RED_BRICK_25_12_6,   // Clay red brick 25x12x6 cm
        CONCRETE_BLOCK_40_20_20, // Cement block 40x20x20 cm
        CONCRETE_BLOCK_40_20_12  // Cement partition block 40x20x12 cm
    }

    enum class WallThickness {
        HALF_BRICK_12CM,
        FULL_BRICK_25CM,
        BLOCK_20CM
    }

    data class MasonryResult(
        val wallAreaM2: Double,
        val brickType: BrickType,
        val numberOfBricks: Int,
        val mortarVolumeM3: Double,
        val cementBags50Kg: Int,
        val sandVolumeM3: Double
    )

    fun calculateMasonry(
        wallAreaM2: Double,
        brickType: BrickType = BrickType.RED_BRICK_25_12_6,
        wallThickness: WallThickness = WallThickness.HALF_BRICK_12CM
    ): MasonryResult {
        val area = max(0.1, wallAreaM2)

        val (bricksPerM2, mortarM3PerM2) = when (brickType) {
            BrickType.RED_BRICK_25_12_6 -> {
                if (wallThickness == WallThickness.FULL_BRICK_25CM) {
                    Pair(115.0, 0.065) // full brick wall with 5% waste
                } else {
                    Pair(58.0, 0.025) // half brick wall (12cm) with 5% waste
                }
            }
            BrickType.CONCRETE_BLOCK_40_20_20 -> Pair(13.0, 0.020)
            BrickType.CONCRETE_BLOCK_40_20_12 -> Pair(13.0, 0.015)
        }

        val totalBricks = ceil(area * bricksPerM2).toInt()
        val totalMortarM3 = round((area * mortarM3PerM2) * 100.0) / 100.0

        // Mortar: 350 kg cement per m3 (7 bags/m3) and 1.0 m3 sand
        val cementBags = max(1, ceil(totalMortarM3 * 7.0).toInt())
        val sandM3 = round((totalMortarM3 * 1.05) * 100.0) / 100.0

        return MasonryResult(
            wallAreaM2 = area,
            brickType = brickType,
            numberOfBricks = totalBricks,
            mortarVolumeM3 = totalMortarM3,
            cementBags50Kg = cementBags,
            sandVolumeM3 = sandM3
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 8. FORMWORK STRIPPING TIME
    // ══════════════════════════════════════════════════════════════

    enum class FormworkElementType {
        VERTICAL_SIDES,         // Column, wall & beam vertical sides
        SLAB_BEAM_SOFFIT,       // Beam and slab bottom soffits
        CANTILEVER              // Balconies, canopies and cantilevers
    }

    data class FormworkStrippingResult(
        val elementType: FormworkElementType,
        val spanM: Double,
        val minimumDaysOrdinaryCement: Int,
        val minimumDaysRapidCement: Int,
        val codeFormula: String,
        val safetyRule: String
    )

    fun calculateFormworkStrippingTime(
        elementType: FormworkElementType,
        spanM: Double = 4.0
    ): FormworkStrippingResult {
        val L = max(0.5, spanM)

        return when (elementType) {
            FormworkElementType.VERTICAL_SIDES -> FormworkStrippingResult(
                elementType = elementType,
                spanM = L,
                minimumDaysOrdinaryCement = 2,
                minimumDaysRapidCement = 1,
                codeFormula = "24 - 48 Hours (1 - 2 Days)",
                safetyRule = "Concrete must resist self-weight and local damage before stripping."
            )
            FormworkElementType.SLAB_BEAM_SOFFIT -> {
                // T = 2 * L + 2 days, min 7 days
                val days = max(7, ceil(2.0 * L + 2.0).toInt())
                val rapid = max(4, ceil(days * 0.55).toInt())
                FormworkStrippingResult(
                    elementType = elementType,
                    spanM = L,
                    minimumDaysOrdinaryCement = days,
                    minimumDaysRapidCement = rapid,
                    codeFormula = "T = 2L + 2 (Minimum 7 days)",
                    safetyRule = "Shoring must remain untouched until required cube/cylinder strength is verified."
                )
            }
            FormworkElementType.CANTILEVER -> {
                // T = 4 * L + 2 days, min 14 days
                val days = max(14, ceil(4.0 * L + 2.0).toInt())
                val rapid = max(7, ceil(days * 0.55).toInt())
                FormworkStrippingResult(
                    elementType = elementType,
                    spanM = L,
                    minimumDaysOrdinaryCement = days,
                    minimumDaysRapidCement = rapid,
                    codeFormula = "T = 4L + 2 (Minimum 14 days)",
                    safetyRule = "Never strip cantilevers before adjacent span is fully cured and hardened."
                )
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 9. UNIVERSAL UNIT CONVERTER
    // ══════════════════════════════════════════════════════════════

    enum class QuantityType(val label: String) {
        LENGTH("Length"),
        AREA("Area"),
        VOLUME("Volume"),
        MASS("Mass"),
        FORCE("Force"),
        STRESS("Stress"),
        MOMENT("Moment")
    }

    private const val KSF_IN_MPA = 0.04788025898033584

    /** Factor of each unit relative to its SI base (m, m2, m3, kg, kN, MPa, kN.m). */
    private val UNIT_TABLE: Map<QuantityType, Map<String, Double>> = mapOf(
        QuantityType.LENGTH to mapOf(
            "m" to 1.0, "cm" to 0.01, "mm" to 0.001, "km" to 1000.0,
            "in" to 0.0254, "ft" to 0.3048, "yd" to 0.9144, "mi" to 1609.344
        ),
        QuantityType.AREA to mapOf(
            "m²" to 1.0, "cm²" to 0.0001, "mm²" to 0.000001,
            "ft²" to 0.09290304, "in²" to 0.00064516,
            "hectare" to 10000.0, "acre" to 4046.8564224, "feddan" to 4200.0
        ),
        QuantityType.VOLUME to mapOf(
            "m³" to 1.0, "cm³" to 0.000001, "L" to 0.001,
            "ft³" to 0.028316846592, "yd³" to 0.764554857984,
            "US gal" to 0.003785411784, "UK gal" to 0.00454609
        ),
        QuantityType.MASS to mapOf(
            "kg" to 1.0, "g" to 0.001, "tonne" to 1000.0, // metric tonne (1000 kg)
            "lb" to 0.45359237, "oz" to 0.028349523125
        ),
        QuantityType.FORCE to mapOf(
            "kN" to 1.0, "N" to 0.001, "MN" to 1000.0, "kgf" to 0.00980665, "tonf" to 9.80665,
            "lbf" to 0.0044482216152605, "kip" to 4.4482216152605
        ),
        QuantityType.STRESS to mapOf(
            "MPa" to 1.0, "N/mm²" to 1.0, "kPa" to 0.001, "Pa" to 0.000001, "bar" to 0.1,
            "kgf/cm²" to 0.0980665, "psi" to 0.006894757293168361, "ksi" to 6.894757293168361,
            "tonf/m²" to 0.00980665, // tonne-force per m2
            // ksf = 1000 lbf/ft2, tsf = 2000 lbf/ft2 (lbf = 0.45359237 kg x 9.80665 m/s2, ft = 0.3048 m)
            "ksf" to KSF_IN_MPA, "tsf" to 2.0 * KSF_IN_MPA
        ),
        QuantityType.MOMENT to mapOf(
            "kN.m" to 1.0, "N.m" to 0.001, "N.mm" to 0.000001, "tonf.m" to 9.80665,
            "kN.cm" to 0.01, "kgf.m" to 0.00980665, "lb.ft" to 0.0013558179483314, "kip.ft" to 1.3558179483314
        )
    )

    fun unitsFor(type: QuantityType): List<String> = UNIT_TABLE[type]?.keys?.toList() ?: emptyList()

    fun convertUnit(
        type: QuantityType,
        value: Double,
        fromUnit: String,
        toUnit: String
    ): Double {
        val table = UNIT_TABLE[type] ?: error("Unknown quantity type: $type")
        val f = table[fromUnit] ?: error("Unknown unit '$fromUnit' for $type")
        val t = table[toUnit] ?: error("Unknown unit '$toUnit' for $type")
        return value * f / t
    }

    // ══════════════════════════════════════════════════════════════
    // 10. EARTHWORK VOLUME (Average End Area + Prismoidal)
    // ══════════════════════════════════════════════════════════════

    data class EarthworkVolumeResult(
        val endArea1M2: Double,
        val endArea2M2: Double,
        val stationDistanceM: Double,
        val averageEndAreaVolumeM3: Double,   // (A1+A2)/2 * L
        val prismoidalVolumeM3: Double,       // L/6 * (A1 + 4*Am + A2)
        val midSectionAreaM2: Double,
        val prismoidalCorrectionPct: Double,
        val recommendedVolumeM3: Double,
        val volumePerMeterRunM3: Double
    )

    fun calculateEarthworkVolume(
        endArea1M2: Double,
        endArea2M2: Double,
        stationDistanceM: Double,
        midSectionAreaM2: Double = -1.0 // <=0 => estimated as (A1+A2)/2
    ): EarthworkVolumeResult {
        val a1 = max(0.0, endArea1M2)
        val a2 = max(0.0, endArea2M2)
        val L = max(0.01, stationDistanceM)
        val avgArea = (a1 + a2) / 2.0
        val linearVolume = avgArea * L

        val am = if (midSectionAreaM2 > 0.0) midSectionAreaM2 else avgArea
        val prismoidal = (L / 6.0) * (a1 + 4.0 * am + a2)

        val correctionPct = if (linearVolume > 0.0) ((prismoidal - linearVolume) / linearVolume) * 100.0 else 0.0

        return EarthworkVolumeResult(
            endArea1M2 = a1,
            endArea2M2 = a2,
            stationDistanceM = L,
            averageEndAreaVolumeM3 = round(linearVolume * 100.0) / 100.0,
            prismoidalVolumeM3 = round(prismoidal * 100.0) / 100.0,
            midSectionAreaM2 = round(am * 100.0) / 100.0,
            prismoidalCorrectionPct = round(correctionPct * 100.0) / 100.0,
            recommendedVolumeM3 = round(prismoidal * 100.0) / 100.0,
            volumePerMeterRunM3 = round(avgArea * 100.0) / 100.0
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 11. PLASTER & PAINT ESTIMATOR
    // ══════════════════════════════════════════════════════════════

    data class PlasterAndPaintResult(
        val grossAreaM2: Double,
        val openingsAreaM2: Double,
        val netAreaM2: Double,
        val plasterThicknessCm: Double,
        val plasterMortarVolumeM3: Double,
        val plasterCementBags50Kg: Int,
        val plasterSandVolumeM3: Double,
        val paintCoats: Int,
        val paintCoverageM2PerL: Double,
        val paintRequiredLiters: Double
    )

    fun calculatePlasterAndPaint(
        wallAreaM2: Double,
        openingsAreaM2: Double = 0.0,
        plasterThicknessCm: Double = 2.0,
        paintCoats: Int = 2,
        paintCoverageM2PerL: Double = 10.0
    ): PlasterAndPaintResult {
        val gross = max(0.1, wallAreaM2)
        val open = max(0.0, openingsAreaM2).coerceAtMost(gross * 0.9)
        val net = gross - open

        val t = max(0.5, plasterThicknessCm) / 100.0 // m
        val mortarM3 = net * t
        val cementBags = ceil((mortarM3 * 7.0).coerceAtLeast(0.0)).toInt()
        val sandM3 = mortarM3 * 1.05

        val coats = max(1, paintCoats)
        val coverage = max(1.0, paintCoverageM2PerL)
        val paintLiters = (net * coats / coverage) * 1.1 // +10% waste

        return PlasterAndPaintResult(
            grossAreaM2 = round(gross * 100.0) / 100.0,
            openingsAreaM2 = round(open * 100.0) / 100.0,
            netAreaM2 = round(net * 100.0) / 100.0,
            plasterThicknessCm = max(0.5, plasterThicknessCm),
            plasterMortarVolumeM3 = round(mortarM3 * 100.0) / 100.0,
            plasterCementBags50Kg = cementBags,
            plasterSandVolumeM3 = round(sandM3 * 100.0) / 100.0,
            paintCoats = coats,
            paintCoverageM2PerL = coverage,
            paintRequiredLiters = round(paintLiters * 10.0) / 10.0
        )
    }
}
