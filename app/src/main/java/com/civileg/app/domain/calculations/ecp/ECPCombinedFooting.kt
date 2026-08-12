package com.civileg.app.domain.calculations.ecp

import kotlin.math.*

/**
 * Combined Footing Design per ECP 203-2020
 *
 * Design of a rectangular combined footing supporting two columns.
 *
 * Steps:
 * 1. Determine footing area from soil bearing capacity
 * 2. Locate centroid to balance column loads
 * 3. Calculate bearing pressure distribution
 * 4. Design for longitudinal bending (cantilever and center span moments)
 * 5. Design for transverse bending
 * 6. One-way shear check
 * 7. Punching shear check at each column
 * 8. Calculate required reinforcement
 *
 * Reference:
 * - ECP 203-2020 Clause 7-1 (Foundations)
 * - ECP 203-2020 Clause 4-3-2 (Punching Shear)
 * - ECP 203-2020 Clause 4-2-1-2 (Minimum Reinforcement)
 */
data class CombinedFootingResult(
    val footingLength: Double,       // m
    val footingWidth: Double,        // m
    val footingThickness: Double,    // mm
    val effectiveDepth: Double,      // mm
    val qMax: Double,                // kN/m^2 max soil pressure (ultimate)
    val qMin: Double,                // kN/m^2 min soil pressure (ultimate)
    val centroidFromCol1: Double,    // m - distance from col1 center to resultant
    // Longitudinal reinforcement
    val longBottomAs: Double,        // cm^2 - bottom steel (positive moment)
    val longTopAs: Double,           // cm^2 - top steel (negative moment between columns)
    val longBottomBars: String,
    val longTopBars: String,
    // Transverse reinforcement
    val transBottomAs: Double,       // cm^2 - transverse bottom steel at each column
    val transBottomBars: String,
    // Shear checks
    val oneWayShearVu: Double,       // kN - max one-way shear
    val oneWayShearVc: Double,       // kN - one-way shear capacity
    val oneWayShearSafe: Boolean,
    val punchingCol1Safe: Boolean,
    val punchingCol2Safe: Boolean,
    // General
    val concreteVolume: Double,      // m^3
    val steelWeight: Double,         // kg
    val isSafe: Boolean,
    val warnings: List<String>
)

class ECPCombinedFooting {

    companion object {
        const val GAMMA_C = 1.5       // ECP 203 concrete safety factor
        const val GAMMA_S = 1.15      // ECP 203 steel safety factor
        const val GAMMA_CONCRETE = 25.0  // kN/m^3 - unit weight of concrete
        const val GAMMA_G = 1.4       // Dead load factor
        const val GAMMA_Q = 1.6       // Live load factor
        private const val MIN_THICKNESS = 300.0  // mm
        private const val MIN_COVER = 75.0       // mm - footing cover per ECP 203
        private const val MIN_REIN_RATIO = 0.0015  // 0.15% for footings per ECP 203 Table 4-8
        private const val STEEL_DENSITY = 7850.0  // kg/m^3
    }

    /**
     * Design a combined footing for two columns.
     *
     * @param p1 Ultimate axial load on column 1 (kN)
     * @param p2 Ultimate axial load on column 2 (kN)
     * @param col1Width Column 1 width in the transverse direction (mm)
     * @param col1Depth Column 1 depth in the longitudinal direction (mm)
     * @param col2Width Column 2 width in the transverse direction (mm)
     * @param col2Depth Column 2 depth in the longitudinal direction (mm)
     * @param distanceBetweenColumns Center-to-center distance between columns (mm)
     * @param soilBearingCapacity Allowable net soil bearing capacity (kN/m^2) at service level
     * @param footingThickness Footing thickness (mm), default 500
     * @param fcu Concrete compressive strength (MPa)
     * @param fy Steel yield strength (MPa)
     * @return CombinedFootingResult
     */
    fun design(
        p1: Double,
        p2: Double,
        col1Width: Double,
        col1Depth: Double,
        col2Width: Double,
        col2Depth: Double,
        distanceBetweenColumns: Double,
        soilBearingCapacity: Double,
        footingThickness: Double = 500.0,
        fcu: Double = 25.0,
        fy: Double = 360.0
    ): CombinedFootingResult {
        val warnings = mutableListOf<String>()

        // ==================== 1. Service Loads ====================
        // Approximate service loads (assuming dominant dead load; use average factor)
        val avgFactor = (GAMMA_G + GAMMA_Q) / 2.0  // ~1.5
        val p1Service = p1 / avgFactor
        val p2Service = p2 / avgFactor
        val totalService = p1Service + p2Service
        val totalUltimate = p1 + p2

        // Column spacing in meters
        val Lcol = distanceBetweenColumns / 1000.0  // m

        // ==================== 2. Minimum Thickness Check ====================
        if (footingThickness < MIN_THICKNESS) {
            warnings.add("Footing thickness ${String.format("%.0f", footingThickness)} mm < ${String.format("%.0f", MIN_THICKNESS)} mm minimum per ECP 203")
        }

        // ==================== 3. Footing Area ====================
        // Net SBC accounting for footing self-weight
        val tMeters = footingThickness / 1000.0
        val netSBC = soilBearingCapacity - GAMMA_CONCRETE * tMeters
        val effectiveSBC = max(netSBC, soilBearingCapacity * 0.8)

        // Required area with 10% margin for eccentricity
        val areaRequired = totalService * 1.1 / effectiveSBC

        // ==================== 4. Locate Centroid of Loads ====================
        // Distance from column 1 to resultant of loads (service level)
        val centroidFromCol1 = p2Service * Lcol / totalService  // m

        // ==================== 5. Footing Dimensions ====================
        // Length: must accommodate centroid with projections on both sides
        // Minimum projection beyond columns: 150 mm (0.15 m)
        val minProjection = 0.15
        val minLengthFromCentroid = centroidFromCol1 + minProjection
        val minProjectionRight = (Lcol - centroidFromCol1) + minProjection
        val minLength = 2.0 * max(minLengthFromCentroid, minProjectionRight)
        val lengthM = max(sqrt(areaRequired * 2.5), minLength)  // L/B ~ 2.5
        val length = ceil(lengthM * 20.0) / 20.0  // Round up to 50 mm

        // Width from area requirement
        val widthM = areaRequired / length
        val width = ceil(widthM * 20.0) / 20.0  // Round up to 50 mm

        // Verify actual area
        val actualArea = length * width
        val actualNetSBC = totalService / actualArea

        // Projections from centroid
        val projLeft = centroidFromCol1          // m (left edge to col1 center)
        val projRight = length - centroidFromCol1 // m (col1 center to right edge, includes col2)
        val col2FromLeft = Lcol                   // col2 position from left edge reference

        // ==================== 6. Effective Depth ====================
        val effectiveDepth = footingThickness - MIN_COVER - 10.0  // mm (cover + half bar)
        val d = effectiveDepth

        // ==================== 7. Soil Pressure (Ultimate Level) ====================
        // For combined footing, soil pressure is approximately uniform when centroid
        // of loads coincides with centroid of footing
        val qUniform = totalUltimate / (length * width * 1e6) * 1e6  // kN/m^2
        // q = P_total / (L * B) in consistent units
        val qSoilUlt = totalUltimate / (length * width)  // kN/m^2

        // For eccentric loading, compute max/min pressure
        // Resultant location relative to footing centroid
        val footingCentroid = length / 2.0
        val eccentricity = abs(footingCentroid - centroidFromCol1)
        val momentAboutBase = totalUltimate * eccentricity  // kN.m
        val sectionModulus = width * length * length / 6.0   // m^3
        val qMax = qSoilUlt + momentAboutBase / sectionModulus
        val qMin = max(qSoilUlt - momentAboutBase / sectionModulus, 0.0)

        // Check soil bearing capacity at ultimate (approximately SBC * avgFactor)
        val ultSBC = soilBearingCapacity * 1.5
        if (qMax > ultSBC) {
            warnings.add("Max soil pressure ${String.format("%.1f", qMax)} kN/m^2 may exceed capacity - verify SBC at ultimate level")
        }
        if (qMin < 0) {
            warnings.add("Negative soil pressure (separation) detected - increase footing length")
        }

        // ==================== 8. Longitudinal Bending Design ====================
        // Use uniform pressure for design (conservative when eccentricity is small)

        // --- Positive moment (bottom steel) ---
        // Critical sections at column faces (cantilever portions)
        // Cantilever left of col1
        val cantLeft = centroidFromCol1 - col1Depth / 2000.0  // m (from left edge to col1 face)
        // Cantilever right of col2
        val col2FromRightEdge = length - col2FromLeft
        val cantRight = col2FromRightEdge - col2Depth / 2000.0  // m (from col2 face to right edge)

        // Positive moment at cantilever tips (worst case)
        val muPosLeft = qSoilUlt * width * cantLeft * cantLeft / 2.0   // kN.m
        val muPosRight = qSoilUlt * width * cantRight * cantRight / 2.0 // kN.m
        val muPosMax = max(muPosLeft, muPosRight)

        // Design bottom reinforcement for max positive moment (per meter width of footing)
        val longBottomAsMm2 = designFlexuralSteel(
            mu = muPosMax,
            b = width * 1000.0,   // mm - full footing width
            d = d,
            fcu = fcu,
            fy = fy,
            isFooting = true
        )

        // --- Negative moment (top steel between columns) ---
        // Approximate negative moment at mid-span between columns as:
        // M_neg ~ q * B * (cantilever)^2 / 2 (cantilever of the longer side)
        // Or more accurately, the moment between columns using cantilever analogy
        val muNeg = qSoilUlt * width * cantLeft * cantLeft / 2.0  // kN.m

        val longTopAsMm2 = if (muNeg > 0) {
            designFlexuralSteel(
                mu = muNeg,
                b = width * 1000.0,
                d = d,
                fcu = fcu,
                fy = fy,
                isFooting = true
            )
        } else {
            0.0
        }

        // ==================== 9. Transverse Bending Design ====================
        // Transverse bending at each column face
        // Critical width for transverse design = column width + 0.75*d each side (per ECP 203)
        val transWidthCol1 = col1Width + 1.5 * d  // mm
        val transWidthCol2 = col2Width + 1.5 * d  // mm

        // Transverse cantilever at column 1
        val transCantCol1 = (width * 1000.0 - col1Width) / 2.0  // mm
        // Transverse moment per meter at col1: M = q * cant^2 / 2 (per meter length along footing)
        val muTransCol1 = qSoilUlt * (transCantCol1 / 1000.0).pow(2) / 2.0  // kN.m/m

        // Transverse cantilever at column 2
        val transCantCol2 = (width * 1000.0 - col2Width) / 2.0
        val muTransCol2 = qSoilUlt * (transCantCol2 / 1000.0).pow(2) / 2.0

        val muTransMax = max(muTransCol1, muTransCol2)

        val transBottomAsMm2 = designFlexuralSteel(
            mu = muTransMax,
            b = 1000.0,  // per meter width
            d = d,
            fcu = fcu,
            fy = fy,
            isFooting = true
        )

        // ==================== 10. One-Way Shear Check ====================
        // Critical section at d/2 from column face (in longitudinal direction)
        // Check at the column with larger cantilever
        val criticalShearDist = d / 2000.0  // m (d/2 converted to meters)

        // Shear at left cantilever (col1 face + d/2)
        val shearSectionLeft = max(cantLeft - criticalShearDist, 0.0)
        val vuLeft = qSoilUlt * width * shearSectionLeft  // kN

        // Shear at right cantilever (col2 face + d/2)
        val shearSectionRight = max(cantRight - criticalShearDist, 0.0)
        val vuRight = qSoilUlt * width * shearSectionRight  // kN

        val vuMax = max(vuLeft, vuRight)

        // One-way shear capacity: Vc = qcu * B * d
        // qcu = 0.24 * sqrt(fcu) / gamma_c per ECP 203 Clause 4-3-1-2
        val qcu = 0.24 * sqrt(fcu) / GAMMA_C  // MPa
        val vc = qcu * (width * 1000.0) * d / 1000.0  // kN (B in mm, d in mm -> convert)

        val oneWayShearSafe = vuMax <= vc
        if (!oneWayShearSafe) {
            warnings.add("One-way shear V_u = ${String.format("%.1f", vuMax)} kN > V_c = ${String.format("%.1f", vc)} kN - increase thickness or concrete grade")
        }

        // ==================== 11. Punching Shear Check ====================
        // ECP 203 Clause 4-3-2: Punching at each column
        // Punching capacity: qp = 0.316 * sqrt(fcu / gamma_c) (MPa)
        val qpCapacity = 0.316 * sqrt(fcu / GAMMA_C)  // MPa

        // --- Column 1 punching ---
        val punchingCol1Safe = checkPunchingShear(
            columnWidth = col1Width,
            columnDepth = col1Depth,
            d = d,
            load = p1,
            qpCapacity = qpCapacity
        )
        if (!punchingCol1Safe) {
            warnings.add("Punching shear at Column 1 not satisfied - increase thickness")
        }

        // --- Column 2 punching ---
        val punchingCol2Safe = checkPunchingShear(
            columnWidth = col2Width,
            columnDepth = col2Depth,
            d = d,
            load = p2,
            qpCapacity = qpCapacity
        )
        if (!punchingCol2Safe) {
            warnings.add("Punching shear at Column 2 not satisfied - increase thickness")
        }

        // ==================== 12. Select Reinforcement ====================
        // Longitudinal bottom steel - bars distributed across footing width
        val longBottomBarResult = selectFootingBars(longBottomAsMm2, (length * 1000.0).toInt())
        val longTopBarResult = if (longTopAsMm2 > 0) {
            selectFootingBars(longTopAsMm2, (length * 1000.0).toInt())
        } else {
            Pair(0, "None")
        }

        // Transverse bottom steel - bars distributed along footing length
        // Place concentrated under each column (within transverse effective width)
        val transBarCount = max(
            ceil(transWidthCol1 / 1000.0 * (width * 1000.0) / 200.0).toInt(),
            5
        ).coerceAtMost(15)
        val transBottomBarResult = selectFootingBars(transBottomAsMm2, transBarCount, minSpacing = 100.0, maxSpacing = 200.0)

        // ==================== 13. Quantities ====================
        val concreteVolume = length * width * tMeters  // m^3

        // Steel weight estimate
        val longBottomWeight = if (longBottomBarResult.first > 0) {
            longBottomBarResult.first * PI * (20.0 / 2.0).pow(2) / 1e6 * (width * 1000.0) * STEEL_DENSITY
        } else 0.0
        val longTopWeight = if (longTopBarResult.first > 0) {
            longTopBarResult.first * PI * (16.0 / 2.0).pow(2) / 1e6 * (width * 1000.0) * STEEL_DENSITY
        } else 0.0
        val transWeight = if (transBottomBarResult.first > 0) {
            transBottomBarResult.first * PI * (16.0 / 2.0).pow(2) / 1e6 * (length * 1000.0) * STEEL_DENSITY
        } else 0.0
        val steelWeight = longBottomWeight + longTopWeight + transWeight

        // ==================== 14. Final Safety Assessment ====================
        val isSafe = oneWayShearSafe && punchingCol1Safe && punchingCol2Safe
                && qMin >= 0 && longBottomAsMm2 > 0

        return CombinedFootingResult(
            footingLength = length,
            footingWidth = width,
            footingThickness = footingThickness,
            effectiveDepth = d,
            qMax = qMax,
            qMin = qMin,
            centroidFromCol1 = centroidFromCol1,
            longBottomAs = longBottomAsMm2 / 100.0,      // mm^2 -> cm^2
            longTopAs = longTopAsMm2 / 100.0,
            longBottomBars = longBottomBarResult.second,
            longTopBars = longTopBarResult.second,
            transBottomAs = transBottomAsMm2 / 100.0,
            transBottomBars = transBottomBarResult.second,
            oneWayShearVu = vuMax,
            oneWayShearVc = vc,
            oneWayShearSafe = oneWayShearSafe,
            punchingCol1Safe = punchingCol1Safe,
            punchingCol2Safe = punchingCol2Safe,
            concreteVolume = concreteVolume,
            steelWeight = steelWeight,
            isSafe = isSafe,
            warnings = warnings
        )
    }

    // ==================== Private Helper Methods ====================

    /**
     * Design flexural reinforcement for a given moment using the K-method.
     * ECP 203 Clause 4-2-2-1.
     *
     * @param mu Design moment (kN.m)
     * @param b Section width (mm)
     * @param d Effective depth (mm)
     * @param fcu Concrete strength (MPa)
     * @param fy Steel yield strength (MPa)
     * @param isFooting If true, uses footing-specific min steel ratio (0.15%)
     * @return Required steel area (mm^2)
     */
    private fun designFlexuralSteel(
        mu: Double,
        b: Double,
        d: Double,
        fcu: Double,
        fy: Double,
        isFooting: Boolean = false
    ): Double {
        if (mu <= 0 || b <= 0 || d <= 0) return 0.0

        val Mu = mu * 1e6  // N.mm
        val fs = fy / GAMMA_S

        // K = Mu / (fcu * b * d^2)
        val K = Mu / (fcu * b * d * d)

        // K_bal (dynamically calculated per ECP 203)
        val kBal = calculateKBalForFooting(fcu, fy)

        // Lever arm: z = d * (0.5 + sqrt(0.25 - K/1.25))
        val discriminant = 0.25 - K / 1.25
        val z = if (discriminant >= 0) {
            d * (0.5 + sqrt(discriminant))
        } else {
            // Over-reinforced: use maximum lever arm limited by K_bal
            d * (0.5 + sqrt(0.25 - kBal / 1.25))
        }

        // Required steel
        var asRequired = Mu / (fs * z)

        // Minimum steel per ECP 203
        val asMin = if (isFooting) {
            MIN_REIN_RATIO * b * d
        } else {
            max(0.26 * (fcu / fy), 0.0013) * b * d
        }

        asRequired = max(asRequired, asMin)

        return asRequired
    }

    /**
     * Calculate K_bal for footing design.
     * Uses strain compatibility per ECP 203 Clause 4-2-2-1.
     * For fcu=25, fy=360: K_bal ~ 0.186
     */
    private fun calculateKBalForFooting(fcu: Double, fy: Double): Double {
        val Es = 200000.0
        val epsilonCu = 0.003
        val epsilonY = fy / (Es * GAMMA_S)
        val cOverD = epsilonCu / (epsilonCu + epsilonY)
        val beta = 0.8  // Whitney stress block factor
        val aOverD = beta * cOverD
        return (0.67 / GAMMA_C) * aOverD * (1.0 - aOverD / 2.0)
    }

    /**
     * Check punching shear at a column per ECP 203 Clause 4-3-2.
     *
     * Critical perimeter at d/2 from column face.
     * Perimeter bo = 2 * (col_w + col_d) + 4 * d (for rectangular column)
     * Applied stress: qp_applied = V * 0.9 / (bo * d)   [0.9 accounts for soil relief]
     * Capacity: qp = 0.316 * sqrt(fcu / gamma_c)
     *
     * @return true if punching shear is adequate
     */
    private fun checkPunchingShear(
        columnWidth: Double,
        columnDepth: Double,
        d: Double,
        load: Double,         // kN - ultimate column load
        qpCapacity: Double    // MPa - punching shear capacity of concrete
    ): Boolean {
        // Critical punching perimeter at d/2 from column face
        val bo = 2.0 * (columnWidth + columnDepth) + 4.0 * d  // mm

        // Net punching force (reduced by soil pressure inside critical perimeter)
        val punchArea = (columnWidth + 2.0 * d) * (columnDepth + 2.0 * d)  // mm^2
        val Vpunch = load * 0.90  // kN (10% reduction for soil pressure inside perimeter)

        // Applied punching stress
        val qpApplied = (Vpunch * 1000.0) / (bo * d)  // MPa

        return qpApplied <= qpCapacity
    }

    /**
     * Select footing bar arrangement (bars spread across a given width).
     *
     * @param requiredArea Required total steel area (mm^2)
     * @param spreadingWidth Width over which bars are distributed (mm)
     * @param minSpacing Minimum bar spacing (mm)
     * @param maxSpacing Maximum bar spacing (mm)
     * @return Pair of (number of bars, bar string description)
     */
    private fun selectFootingBars(
        requiredArea: Double,
        spreadingWidth: Int,
        minSpacing: Double = 100.0,
        maxSpacing: Double = 200.0
    ): Pair<Int, String> {
        if (requiredArea <= 0) return 0 to "None"

        // Choose bar diameter based on required area
        val availableBars = listOf(12, 14, 16, 18, 20, 22, 25)
        val barDia = availableBars.firstOrNull { dia ->
            val barArea = PI * (dia.toDouble() / 2.0).pow(2)
            val spacing = spreadingWidth / ceil(requiredArea / barArea).toInt()
            spacing >= minSpacing && spacing <= maxSpacing
        } ?: 16

        val barArea = PI * (barDia.toDouble() / 2.0).pow(2)
        var numBars = ceil(requiredArea / barArea).toInt().coerceAtLeast(3)

        // Verify spacing
        val spacing = (spreadingWidth - 2 * MIN_COVER) / (numBars - 1).coerceAtLeast(1)
        if (spacing > maxSpacing) {
            numBars = ceil((spreadingWidth - 2 * MIN_COVER) / maxSpacing + 1).toInt()
        }
        if (spacing < minSpacing && numBars > 1) {
            numBars = floor((spreadingWidth - 2 * MIN_COVER) / minSpacing + 1).toInt().coerceAtLeast(2)
        }

        return numBars to "${numBars}\u03A6${barDia}"
    }

    /**
     * Calculate one-way shear capacity of concrete per ECP 203.
     * qcu = 0.24 * sqrt(fcu) / gamma_c
     *
     * @param fcu Concrete strength (MPa)
     * @return Shear stress capacity (MPa)
     */
    fun calculateOneWayShearCapacity(fcu: Double): Double {
        return 0.24 * sqrt(fcu) / GAMMA_C
    }

    /**
     * Calculate punching shear capacity of concrete per ECP 203.
     * qp = 0.316 * sqrt(fcu / gamma_c)
     *
     * @param fcu Concrete strength (MPa)
     * @return Punching shear stress capacity (MPa)
     */
    fun calculatePunchingShearCapacity(fcu: Double): Double {
        return 0.316 * sqrt(fcu / GAMMA_C)
    }

    /**
     * Calculate minimum reinforcement area per ECP 203.
     * For footings: As_min = 0.0015 * b * d (Table 4-8)
     * For beams: As_min = max(0.26 * fcu/fy, 0.0013) * b * d
     *
     * @param b Width (mm)
     * @param d Effective depth (mm)
     * @param fcu Concrete strength (MPa)
     * @param fy Steel yield strength (MPa)
     * @param isFooting True for footing elements
     * @return Minimum steel area (mm^2)
     */
    fun calculateMinSteel(
        b: Double,
        d: Double,
        fcu: Double,
        fy: Double,
        isFooting: Boolean = true
    ): Double {
        return if (isFooting) {
            MIN_REIN_RATIO * b * d
        } else {
            max(0.26 * (fcu / fy), 0.0013) * b * d
        }
    }

    /**
     * Calculate development (anchorage) length per ECP 203.
     * La = 0.5 * fy * dia / fbd
     * fbd = 0.6 * sqrt(fcu) for deformed bars
     * fbd = 0.3 * sqrt(fcu) for plain bars
     *
     * @param fy Steel yield strength (MPa)
     * @param dia Bar diameter (mm)
     * @param fcu Concrete strength (MPa)
     * @param isDeformed True for deformed bars (default)
     * @return Development length (mm)
     */
    fun calculateDevelopmentLength(
        fy: Double,
        dia: Double,
        fcu: Double,
        isDeformed: Boolean = true
    ): Double {
        val fbd = if (isDeformed) 0.6 * sqrt(fcu) else 0.3 * sqrt(fcu)
        return 0.5 * fy * dia / fbd.coerceAtLeast(0.1)
    }

    /**
     * Quick check: estimate required footing thickness for given loads.
     * Iterates to find minimum thickness satisfying one-way and punching shear.
     *
     * @param p1 Ultimate load column 1 (kN)
     * @param p2 Ultimate load column 2 (kN)
     * @param col1Width Column 1 width (mm)
     * @param col1Depth Column 1 depth (mm)
     * @param col2Width Column 2 width (mm)
     * @param col2Depth Column 2 depth (mm)
     * @param distanceBetweenColumns Distance between columns (mm)
     * @param soilBearingCapacity SBC (kN/m^2)
     * @param fcu Concrete strength (MPa)
     * @return Recommended minimum thickness (mm)
     */
    fun estimateMinimumThickness(
        p1: Double,
        p2: Double,
        col1Width: Double,
        col1Depth: Double,
        col2Width: Double,
        col2Depth: Double,
        distanceBetweenColumns: Double,
        soilBearingCapacity: Double,
        fcu: Double
    ): Double {
        // Start from minimum thickness and increment by 50mm
        var thickness = MIN_THICKNESS
        for (i in 0..20) {
            val result = design(
                p1 = p1, p2 = p2,
                col1Width = col1Width, col1Depth = col1Depth,
                col2Width = col2Width, col2Depth = col2Depth,
                distanceBetweenColumns = distanceBetweenColumns,
                soilBearingCapacity = soilBearingCapacity,
                footingThickness = thickness,
                fcu = fcu
            )
            if (result.oneWayShearSafe && result.punchingCol1Safe && result.punchingCol2Safe) {
                return thickness
            }
            thickness += 50.0
        }
        return thickness  // Return last tried value if no solution found
    }
}