package com.civileg.app.utils

import kotlin.math.*

/**
 * Comprehensive Rebar Calculator supporting ECP 203 and ACI 318.
 * Covers weight, area, development length, lap splice, spacing, bundling,
 * rebar scheduling, and crack width calculations.
 */
object RebarCalculator {

    // ── Standard metric bar diameters (mm) ──
    val STANDARD_DIAMETERS = listOf(
        6, 8, 10, 12, 14, 16, 18, 20, 22, 25, 28, 32, 36, 40
    )

    // ── Design Code enum ──
    enum class DesignCode { ECP_203, ACI_318 }

    // ── Lap type ──
    enum class LapType { TENSION, COMPRESSION }

    // ── Element type for schedule ──
    enum class ElementType { BEAM, SLAB, COLUMN }

    // ══════════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ══════════════════════════════════════════════════════════════

    data class BarProperties(
        val diameter: Double,       // mm
        val weightPerMeter: Double, // kg/m
        val area: Double,           // mm²
        val weightPerBundle: Double // kg (20m bundle)
    )

    data class WeightResult(
        val weightPerMeter: Double,
        val totalWeight: Double,
        val totalLength: Double,
        val quantity: Int,
        val diameter: Double
    )

    data class DevelopmentLengthResult(
        val basicLd: Double,
        val modifiedLd: Double,
        val code: DesignCode,
        val topBarModifier: Double,
        val confinementModifier: Double,
        val excessRebarModifier: Double,
        val notes: List<String>
    )

    data class LapSpliceResult(
        val lapLength: Double,
        val lapType: LapType,
        val basicLd: Double,
        val modifier: Double,
        val code: DesignCode
    )

    data class SpacingResult(
        val minimumSpacing: Double,
        val clearSpacing: Double,
        val governingCriterion: String,
        val maxBarsInWidth: Int,
        val requiredWidth: Double
    )

    data class BundledBarResult(
        val numberOfBars: Int,
        val individualDiameter: Double,
        val equivalentDiameter: Double,
        val equivalentArea: Double,
        val equivalentWeightPerMeter: Double
    )

    data class RebarScheduleItem(
        val mark: String,
        val diameter: Int,
        val numberOfBars: Int,
        val barLength: Double,
        val totalLength: Double,
        val weightPerMeter: Double,
        val totalWeight: Double,
        val shape: String,
        val description: String
    )

    data class RebarScheduleResult(
        val elementType: ElementType,
        val items: List<RebarScheduleItem>,
        val totalWeight: Double,
        val totalBars: Int
    )

    data class CrackWidthResult(
        val crackWidth: Double,
        val meanStrain: Double,
        val effectiveModularRatio: Double,
        val steelStress: Double,
        val barSpacing: Double,
        val coverToBarCenter: Double,
        val isAcceptable: Boolean,
        val limitingCrackWidth: Double,
        val notes: List<String>
    )

    // ══════════════════════════════════════════════════════════════
    //  WEIGHT CALCULATIONS
    // ══════════════════════════════════════════════════════════════

    /**
     * Weight per meter for metric rebar: w = d² / 162.2 (kg/m)
     * Derived from: w = ρ * A = 7850 kg/m³ * π * d² / (4 * 1e6)
     */
    fun weightPerMeter(diameterMm: Double): Double {
        return diameterMm.pow(2) / 162.2
    }

    /**
     * Total weight for given quantity and bar length.
     */
    fun totalWeight(
        diameterMm: Double,
        barLengthM: Double,
        quantity: Int
    ): WeightResult {
        val wpm = weightPerMeter(diameterMm)
        val totalLength = barLengthM * quantity
        return WeightResult(
            weightPerMeter = wpm,
            totalWeight = wpm * totalLength,
            totalLength = totalLength,
            quantity = quantity,
            diameter = diameterMm
        )
    }

    /**
     * Weight per bundle (standard 20m bars).
     */
    fun weightPerBundle(diameterMm: Double, barsPerBundle: Int = 1, bundleLengthM: Double = 20.0): Double {
        return weightPerMeter(diameterMm) * barsPerBundle * bundleLengthM
    }

    /**
     * Get complete bar properties for a given diameter.
     */
    fun getBarProperties(diameterMm: Double): BarProperties {
        val wpm = weightPerMeter(diameterMm)
        val area = barArea(diameterMm)
        return BarProperties(
            diameter = diameterMm,
            weightPerMeter = wpm,
            area = area,
            weightPerBundle = wpm * 20.0
        )
    }

    /**
     * Get properties for all standard diameters.
     */
    fun getAllBarProperties(): List<BarProperties> {
        return STANDARD_DIAMETERS.map { getBarProperties(it.toDouble()) }
    }

    // ══════════════════════════════════════════════════════════════
    //  AREA CALCULATIONS
    // ══════════════════════════════════════════════════════════════

    /**
     * Cross-sectional area of a single bar: A = π * d² / 4
     */
    fun barArea(diameterMm: Double): Double {
        return PI * diameterMm.pow(2) / 4.0
    }

    /**
     * Total area for multiple bars.
     */
    fun totalArea(diameterMm: Double, numberOfBars: Int): Double {
        return barArea(diameterMm) * numberOfBars
    }

    /**
     * Find the minimum number of bars of given diameter to achieve required area.
     */
    fun minBarsForArea(requiredArea: Double, diameterMm: Double): Int {
        val singleArea = barArea(diameterMm)
        return ceil(requiredArea / singleArea).toInt().coerceAtLeast(1)
    }

    /**
     * Find bars that provide area >= required, returning (diameter, count) pairs.
     */
    fun findBarCombination(requiredArea: Double): List<Pair<Int, Int>> {
        val results = mutableListOf<Pair<Int, Int>>()
        for (dia in STANDARD_DIAMETERS) {
            val count = minBarsForArea(requiredArea, dia.toDouble())
            results.add(dia to count)
        }
        return results
    }

    // ══════════════════════════════════════════════════════════════
    //  DEVELOPMENT LENGTH
    // ══════════════════════════════════════════════════════════════

    /**
     * Calculate development length per ECP 203.
     * Ld = (0.5 * fy / sqrt(fcu)) * dia * modifiers
     */
    fun developmentLengthECP(
        diameterMm: Double,
        fy: Double,     // N/mm² (yield strength)
        fcu: Double,    // N/mm² (concrete compressive strength)
        isTopBar: Boolean = false,
        isConfined: Boolean = false,
        excessReinforcementRatio: Double = 1.0, // As provided / As required
        barType: String = "deformed" // "deformed" or "plain"
    ): DevelopmentLengthResult {
        val notes = mutableListOf<String>()

        // Basic development length
        var basicLd = (0.5 * fy / sqrt(fcu)) * diameterMm
        notes.add("Basic Ld = (0.5 × $fy / √$fcu) × $diameterMm = ${"%.1f".format(basicLd)} mm")

        // Bar type modifier
        val barTypeModifier = if (barType == "plain") 1.4 else 1.0
        if (barType == "plain") notes.add("Plain bar modifier: ×1.4")

        // Top bar modifier (ECP 203: 1.3 for horizontal bars with >300mm fresh concrete below)
        val topBarModifier = if (isTopBar) 1.3 else 1.0
        if (isTopBar) notes.add("Top bar modifier (fresh concrete > 300mm below): ×1.3")

        // Confinement modifier
        val confinementModifier = if (isConfined) 0.7 else 1.0
        if (isConfined) notes.add("Confinement modifier (tied/stirrup confined): ×0.7")

        // Excess reinforcement modifier
        val excessModifier = if (excessReinforcementRatio > 1.0) {
            val m = 1.0 / excessReinforcementRatio
            m
        } else {
            1.0
        }
        if (excessReinforcementRatio > 1.0) {
            notes.add("Excess reinforcement (As/As,req = ${"%.2f".format(excessReinforcementRatio)}): ×${"%.2f".format(excessModifier)}")
        }

        val modifiedLd = basicLd * barTypeModifier * topBarModifier * confinementModifier * excessModifier

        // Minimum development length per ECP 203
        val minLd = maxOf(12.0 * diameterMm, 200.0)
        val finalLd = maxOf(modifiedLd, minLd)

        if (modifiedLd < minLd) {
            notes.add("Minimum Ld = max(12d, 200mm) = $minLd mm applied")
        }

        return DevelopmentLengthResult(
            basicLd = basicLd,
            modifiedLd = finalLd,
            code = DesignCode.ECP_203,
            topBarModifier = topBarModifier,
            confinementModifier = confinementModifier,
            excessRebarModifier = excessModifier,
            notes = notes
        )
    }

    /**
     * Calculate development length per ACI 318.
     * Ld = (fy * ψt * ψe * ψs) / (1.1 * λ * sqrt(f'c)) * db
     * Simplified for normal weight concrete, deformed bars.
     */
    fun developmentLengthACI(
        diameterMm: Double,
        fy: Double,       // psi
        fPrimeC: Double,  // psi
        isTopBar: Boolean = false,
        isConfined: Boolean = false,
        excessReinforcementRatio: Double = 1.0,
        epoxyCoated: Boolean = false,
        lightweightConcrete: Boolean = false
    ): DevelopmentLengthResult {
        val notes = mutableListOf<String>()

        // Convert mm to inches for ACI formulas (1 inch = 25.4 mm)
        val db = diameterMm / 25.4

        // Location modifier ψt
        val psiT = if (isTopBar) 1.3 else 1.0
        if (isTopBar) notes.add("Top bar modifier ψt = 1.3")

        // Coating modifier ψe
        val psiE = when {
            epoxyCoated && diameterMm <= 19.0 -> 1.5
            epoxyCoated -> 1.2
            else -> 1.0
        }
        if (epoxyCoated) notes.add("Epoxy coating modifier ψe = $psiE")

        // Bar size modifier ψs
        val psiS = if (diameterMm <= 19.0) 1.0 else 1.0
        notes.add("Bar size modifier ψs = $psiS")

        // Lightweight modifier λ
        val lambda = if (lightweightConcrete) 0.75 else 1.0
        if (lightweightConcrete) notes.add("Lightweight concrete modifier λ = 0.75")

        // Basic development length (inches)
        var basicLdIn = (fy * psiT * psiE * psiS) / (1.1 * lambda * sqrt(fPrimeC)) * db
        notes.add("Basic Ld = (${"%.0f".format(fy)} × $psiT × $psiE × $psiS) / (1.1 × $lambda × √${"%.0f".format(fPrimeC)}) × ${"%.3f".format(db)}")

        // Convert to mm
        var basicLdMm = basicLdIn * 25.4

        // Confinement modifier
        val confinementModifier = if (isConfined) 0.67 else 1.0
        if (isConfined) notes.add("Confinement modifier: ×0.67")

        // Excess reinforcement
        val excessModifier = if (excessReinforcementRatio > 1.0) {
            1.0 / excessReinforcementRatio
        } else 1.0
        if (excessReinforcementRatio > 1.0) {
            notes.add("Excess reinforcement: ×${"%.2f".format(excessModifier)}")
        }

        val modifiedLdMm = basicLdMm * confinementModifier * excessModifier

        // Minimum per ACI 318
        val minLd = 300.0
        val finalLd = maxOf(modifiedLdMm, minLd)

        if (modifiedLdMm < minLd) {
            notes.add("Minimum Ld = 300mm applied")
        }

        return DevelopmentLengthResult(
            basicLd = basicLdMm,
            modifiedLd = finalLd,
            code = DesignCode.ACI_318,
            topBarModifier = psiT,
            confinementModifier = confinementModifier,
            excessRebarModifier = excessModifier,
            notes = notes
        )
    }

    // ══════════════════════════════════════════════════════════════
    //  LAP SPLICE LENGTH
    // ══════════════════════════════════════════════════════════════

    /**
     * Lap splice length calculation.
     * Tension: Class A splice = 1.0*Ld, Class B splice = 1.3*Ld
     * Compression: 1.0*Ld
     */
    fun lapSpliceLength(
        diameterMm: Double,
        fy: Double,
        fcu: Double,
        lapType: LapType,
        code: DesignCode,
        isTopBar: Boolean = false,
        isConfined: Boolean = false,
        spliceClass: String = "B" // "A" or "B"
    ): LapSpliceResult {
        val devResult = when (code) {
            DesignCode.ECP_203 -> developmentLengthECP(
                diameterMm, fy, fcu, isTopBar, isConfined
            )
            DesignCode.ACI_318 -> {
                // Convert to psi for ACI
                val fyPsi = fy * 145.038
                val fcPsi = fcu * 145.038
                developmentLengthACI(
                    diameterMm, fyPsi, fcPsi, isTopBar, isConfined
                )
            }
        }

        val modifier = when (lapType) {
            LapType.TENSION -> {
                when (code) {
                    DesignCode.ECP_203 -> if (spliceClass == "A") 1.0 else 1.3
                    DesignCode.ACI_318 -> if (spliceClass == "A") 1.0 else 1.3
                }
            }
            LapType.COMPRESSION -> 1.0
        }

        val lapLength = devResult.modifiedLd * modifier

        return LapSpliceResult(
            lapLength = lapLength,
            lapType = lapType,
            basicLd = devResult.modifiedLd,
            modifier = modifier,
            code = code
        )
    }

    // ══════════════════════════════════════════════════════════════
    //  MINIMUM SPACING
    // ══════════════════════════════════════════════════════════════

    /**
     * Minimum spacing between parallel bars.
     * max(25mm, diameter, aggregate max size + 5mm)
     * Per ECP 203 / ACI 318.
     */
    fun minimumSpacing(
        diameterMm: Double,
        maxAggregateSize: Double = 20.0,
        memberWidth: Double = 0.0
    ): SpacingResult {
        val s1 = 25.0
        val s2 = diameterMm
        val s3 = maxAggregateSize + 5.0

        val minSpacing = maxOf(s1, s2, s3)

        val governingCriterion = when {
            minSpacing == s1 -> "25 mm (code minimum)"
            minSpacing == s2 -> "Bar diameter ($diameterMm mm)"
            else -> "Aggregate size + 5mm ($maxAggregateSize + 5 = $s3 mm)"
        }

        val maxBars = if (memberWidth > 0) {
            val clearCover = 40.0 // typical cover
            val stirrupDia = 10.0 // typical
            val available = memberWidth - 2 * (clearCover + stirrupDia) - diameterMm
            floor(available / (diameterMm + minSpacing)).toInt().coerceAtLeast(0) + 1
        } else 0

        val requiredWidth = if (memberWidth <= 0 && maxBars > 0) {
            2 * 50.0 + (maxBars - 1) * (diameterMm + minSpacing) + diameterMm
        } else memberWidth

        return SpacingResult(
            minimumSpacing = minSpacing,
            clearSpacing = minSpacing,
            governingCriterion = governingCriterion,
            maxBarsInWidth = maxBars,
            requiredWidth = requiredWidth
        )
    }

    // ══════════════════════════════════════════════════════════════
    //  BUNDLED BARS
    // ══════════════════════════════════════════════════════════════

    /**
     * Equivalent diameter for bundled bars.
     * deq = d * √n
     */
    fun bundledBarProperties(
        numberOfBars: Int,
        individualDiameter: Double
    ): BundledBarResult {
        require(numberOfBars in 2..4) { "Bundled bars must be 2 to 4 in a group" }

        val equivalentDiameter = individualDiameter * sqrt(numberOfBars.toDouble())
        val equivalentArea = barArea(individualDiameter) * numberOfBars
        val equivalentWpm = weightPerMeter(individualDiameter) * numberOfBars

        return BundledBarResult(
            numberOfBars = numberOfBars,
            individualDiameter = individualDiameter,
            equivalentDiameter = equivalentDiameter,
            equivalentArea = equivalentArea,
            equivalentWeightPerMeter = equivalentWpm
        )
    }

    // ══════════════════════════════════════════════════════════════
    //  REBAR SCHEDULE GENERATOR
    // ══════════════════════════════════════════════════════════════

    /**
     * Generate a standard rebar schedule for a beam.
     */
    fun generateBeamSchedule(
        beamWidth: Double,       // mm
        beamDepth: Double,       // mm
        beamLength: Double,      // mm
        spanCount: Int,
        topBarsDia: Int,
        topBarsCount: Int,
        bottomBarsDia: Int,
        bottomBarsCount: Int,
        stirrupDia: Int,
        stirrupSpacing: Double,  // mm (c/c)
        cover: Double = 40.0     // mm
    ): RebarScheduleResult {
        val items = mutableListOf<RebarScheduleItem>()
        var markNum = 1

        // Bottom longitudinal bars (straight)
        val bottomBarLength = beamLength * spanCount / 1000.0 // convert to meters
        val bottomItem = createScheduleItem(
            mark = "${markNum++}",
            diameter = bottomBarsDia,
            count = bottomBarsCount * spanCount,
            barLength = bottomBarLength,
            shape = "Straight",
            description = "Bottom longitudinal bars"
        )
        items.add(bottomItem)

        // Top longitudinal bars (over supports, assume 1/3 span each side)
        val topBarLength = beamLength * 2.0 / 3.0 / 1000.0
        val topItem = createScheduleItem(
            mark = "${markNum++}",
            diameter = topBarsDia,
            count = topBarsCount * (spanCount + 1),
            barLength = topBarLength,
            shape = "Straight",
            description = "Top bars over supports"
        )
        items.add(topItem)

        // Stirrups
        val stirrupWidth = beamWidth - 2 * cover + 2 * stirrupDia
        val stirrupHeight = beamDepth - 2 * cover + 2 * stirrupDia
        val stirrupPerimeter = 2 * (stirrupWidth + stirrupHeight) + 2 * 10 * stirrupDia // 10d hook
        val stirrupLengthMm = stirrupPerimeter
        val totalBeamLengthMm = beamLength * spanCount
        val numberOfStirrups = ceil(totalBeamLengthMm / stirrupSpacing).toInt() + 1

        val stirrupItem = createScheduleItem(
            mark = "${markNum++}",
            diameter = stirrupDia,
            count = numberOfStirrups,
            barLength = stirrupLengthMm / 1000.0,
            shape = "Stirrup (rectangular)",
            description = "Shear stirrups @ ${stirrupSpacing}mm c/c"
        )
        items.add(stirrupItem)

        // Top bars in mid-span (hanger bars, 2 bars)
        val hangerItem = createScheduleItem(
            mark = "${markNum++}",
            diameter = minOf(topBarsDia, 12),
            count = 2 * spanCount,
            barLength = bottomBarLength,
            shape = "Straight",
            description = "Top hanger bars"
        )
        items.add(hangerItem)

        return RebarScheduleResult(
            elementType = ElementType.BEAM,
            items = items,
            totalWeight = items.sumOf { it.totalWeight },
            totalBars = items.sumOf { it.numberOfBars }
        )
    }

    /**
     * Generate a standard rebar schedule for a slab.
     */
    fun generateSlabSchedule(
        slabLength: Double,       // m
        slabWidth: Double,        // m
        slabThickness: Double,    // mm
        bottomShortDia: Int,
        bottomShortSpacing: Double, // mm
        bottomLongDia: Int,
        bottomLongSpacing: Double,  // mm
        topShortDia: Int,
        topShortSpacing: Double,
        cover: Double = 20.0
    ): RebarScheduleResult {
        val items = mutableListOf<RebarScheduleItem>()
        var markNum = 1

        // Bottom short direction bars
        val shortBarsCount = ceil(slabWidth * 1000.0 / bottomShortSpacing).toInt() + 1
        val shortItem = createScheduleItem(
            mark = "${markNum++}",
            diameter = bottomShortDia,
            count = shortBarsCount,
            barLength = slabLength,
            shape = "Straight",
            description = "Bottom short span bars @ ${bottomShortSpacing}mm c/c"
        )
        items.add(shortItem)

        // Bottom long direction bars
        val longBarsCount = ceil(slabLength * 1000.0 / bottomLongSpacing).toInt() + 1
        val longItem = createScheduleItem(
            mark = "${markNum++}",
            diameter = bottomLongDia,
            count = longBarsCount,
            barLength = slabWidth,
            shape = "Straight",
            description = "Bottom long span bars @ ${bottomLongSpacing}mm c/c"
        )
        items.add(longItem)

        // Top short direction bars (over supports)
        if (topShortDia > 0) {
            val topShortCount = ceil(slabWidth * 1000.0 / topShortSpacing).toInt() + 1
            val topShortLength = slabLength * 0.25 // 1/4 span each side
            val topItem = createScheduleItem(
                mark = "${markNum++}",
                diameter = topShortDia,
                count = topShortCount,
                barLength = topShortLength,
                shape = "Straight",
                description = "Top short span bars @ ${topShortSpacing}mm c/c"
            )
            items.add(topItem)
        }

        return RebarScheduleResult(
            elementType = ElementType.SLAB,
            items = items,
            totalWeight = items.sumOf { it.totalWeight },
            totalBars = items.sumOf { it.numberOfBars }
        )
    }

    /**
     * Generate a standard rebar schedule for a column.
     */
    fun generateColumnSchedule(
        columnWidth: Double,    // mm
        columnDepth: Double,    // mm
        columnHeight: Double,   // m
        mainBarsDia: Int,
        mainBarsCount: Int,
        tiesDia: Int,
        tiesSpacing: Double,    // mm
        cover: Double = 40.0
    ): RebarScheduleResult {
        val items = mutableListOf<RebarScheduleItem>()
        var markNum = 1

        // Main longitudinal bars
        val mainItem = createScheduleItem(
            mark = "${markNum++}",
            diameter = mainBarsDia,
            count = mainBarsCount,
            barLength = columnHeight + 0.6, // 0.6m for lapping into footing/slab above
            shape = "Straight",
            description = "Main longitudinal bars"
        )
        items.add(mainItem)

        // Ties / links
        val tieWidth = columnWidth - 2 * cover + 2 * tiesDia
        val tieDepth = columnDepth - 2 * cover + 2 * tiesDia
        val tiePerimeter = 2 * (tieWidth + tieDepth) + 2 * 10 * tiesDia
        val numberOfTies = ceil(columnHeight * 1000.0 / tiesSpacing).toInt() + 1

        val tieItem = createScheduleItem(
            mark = "${markNum++}",
            diameter = tiesDia,
            count = numberOfTies,
            barLength = tiePerimeter / 1000.0,
            shape = "Tie (rectangular)",
            description = "Lateral ties @ ${tiesSpacing}mm c/c"
        )
        items.add(tieItem)

        return RebarScheduleResult(
            elementType = ElementType.COLUMN,
            items = items,
            totalWeight = items.sumOf { it.totalWeight },
            totalBars = items.sumOf { it.numberOfBars }
        )
    }

    private fun createScheduleItem(
        mark: String,
        diameter: Int,
        count: Int,
        barLength: Double,
        shape: String,
        description: String
    ): RebarScheduleItem {
        val wpm = weightPerMeter(diameter.toDouble())
        return RebarScheduleItem(
            mark = mark,
            diameter = diameter,
            numberOfBars = count,
            barLength = barLength,
            totalLength = barLength * count,
            weightPerMeter = wpm,
            totalWeight = wpm * barLength * count,
            shape = shape,
            description = description
        )
    }

    // ══════════════════════════════════════════════════════════════
    //  CRACK WIDTH CALCULATION
    // ══════════════════════════════════════════════════════════════

    /**
     * Simplified crack width calculation.
     * wk = 3.4 * εm * acr
     *
     * where:
     *   εm = mean steel strain allowing for tension stiffening
     *   acr = distance from the point considered to the surface of the nearest longitudinal bar
     *
     * Mean strain: εm = εs - (1 - As,req/As,prov) * (fctm/Es)
     * Simplified: εm ≈ fs / Es (for design purposes)
     *
     * Limiting crack width: 0.3mm (normal) or 0.2mm (aggressive)
     */
    fun crackWidth(
        steelStress: Double,       // N/mm² (service stress in reinforcement)
        barDiameter: Double,       // mm
        barSpacing: Double,        // mm (c/c)
        coverToBarCenter: Double,  // mm (from concrete surface to bar center)
        es: Double = 200000.0,     // N/mm² (steel modulus)
        limitingWidth: Double = 0.3 // mm
    ): CrackWidthResult {
        val notes = mutableListOf<String>()

        // Mean strain
        val meanStrain = steelStress / es
        notes.add("Mean strain εm = fs/Es = $steelStress/$es = ${"%.6f".format(meanStrain)}")

        // Distance to nearest bar surface (acr)
        val acr = coverToBarCenter
        notes.add("Distance to bar center acr = $acr mm")

        // Crack width: wk = 3.4 * εm * acr
        val wk = 3.4 * meanStrain * acr
        notes.add("Crack width wk = 3.4 × ${"%.6f".format(meanStrain)} × $acr = ${"%.3f".format(wk)} mm")

        // Bar spacing check
        if (barSpacing > 0) {
            val maxSpacing = when {
                steelStress > 200 -> 125.0
                steelStress > 160 -> 150.0
                steelStress > 120 -> 175.0
                else -> 200.0
            }
            notes.add("Max spacing for fs=${"%.0f".format(steelStress)} N/mm²: ${"%.0f".format(maxSpacing)} mm")
        }

        // Bar diameter check
        val maxDia = when {
            steelStress > 200 -> 10.0
            steelStress > 160 -> 12.0
            steelStress > 120 -> 16.0
            else -> 20.0
        }
        if (barDiameter > maxDia) {
            notes.add("⚠ Bar diameter ${"%.0f".format(barDiameter)}mm > max recommended ${"%.0f".format(maxDia)}mm for this stress level")
        }

        val isAcceptable = wk <= limitingWidth
        notes.add(
            if (isAcceptable) {
                "✓ Crack width ${"%.3f".format(wk)}mm ≤ limit ${limitingWidth}mm — OK"
            } else {
                "✗ Crack width ${"%.3f".format(wk)}mm > limit ${limitingWidth}mm — NOT OK"
            }
        )

        return CrackWidthResult(
            crackWidth = wk,
            meanStrain = meanStrain,
            effectiveModularRatio = es / 30000.0, // assuming Ec for reference
            steelStress = steelStress,
            barSpacing = barSpacing,
            coverToBarCenter = coverToBarCenter,
            isAcceptable = isAcceptable,
            limitingCrackWidth = limitingWidth,
            notes = notes
        )
    }

    // ══════════════════════════════════════════════════════════════
    //  HELPER FORMATTING
    // ══════════════════════════════════════════════════════════════

    fun formatWeight(value: Double): String = "%.3f".format(value)
    fun formatArea(value: Double): String = "%.1f".format(value)
    fun formatLength(value: Double): String = "%.1f".format(value)
}
