package com.civileg.app.utils

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Concrete Mix Design per ACI 211.1.
 * Supports OPC, SRC, PPC cement types with exposure condition adjustments.
 * Standard grades: C20, C25, C30, C35, C40, C45, C50.
 */
object ConcreteMixDesigner {

    // ── Cement Types ──
    enum class CementType(val label: String, val specificGravity: Double) {
        OPC("Ordinary Portland Cement", 3.15),
        SRC("Sulfate Resisting Cement", 3.14),
        PPC("Portland Pozzolana Cement", 3.10)
    }

    // ── Exposure Conditions ──
    enum class Exposure(val label: String, val minCement: Double, val maxWCRatio: Double, val minCover: Double) {
        MILD("Mild (dry interior)", 220.0, 0.60, 25.0),
        MODERATE("Moderate (sheltered)", 260.0, 0.55, 30.0),
        SEVERE("Severe (exposed)", 300.0, 0.50, 40.0),
        VERY_SEVERE("Very Severe (aggressive)", 340.0, 0.45, 50.0),
        EXTREME("Extreme (chemical attack)", 380.0, 0.40, 60.0)
    }

    // ── Standard Concrete Grades ──
    val STANDARD_GRADES = listOf(20, 25, 30, 35, 40, 45, 50)

    // ── Input Parameters ──
    data class MixInput(
        val targetStrength: Double,       // MPa (f'c)
        val standardDeviation: Double,    // MPa (S)
        val maxAggregateSize: Double,     // mm
        val slump: Double,                // mm
        val exposure: Exposure,
        val cementType: CementType,
        val finenessModulus: Double,      // fine aggregate
        val hasAdmixture: Boolean = false,
        val admixtureType: String = "None",
        val admixtureDosage: Double = 0.0, // % of cement weight
        val isPumpable: Boolean = false,
        val weatherCondition: String = "Normal",
        val useNoTestData: Boolean = false,
        val fineAggSG: Double = 2.65,
        val coarseAggSG: Double = 2.70
    )

    // ── Result ──
    data class MixResult(
        val targetStrength: Double,
        val requiredStrength: Double,
        val waterCementRatio: Double,
        val cementContent: Double,
        val waterContent: Double,
        val coarseAggContent: Double,
        val fineAggContent: Double,
        val airContent: Double,
        val admixtureContent: Double,
        val wCmRatio: Double,
        val yield: Double,
        val unitWeight: Double,
        val cementType: String,
        val exposure: String,
        val mixRatio: String,
        val notes: List<String>,
        val trialBatch: TrialBatch
    )

    data class TrialBatch(
        val batchSize: Double,
        val cement: Double,
        val water: Double,
        val fineAgg: Double,
        val coarseAgg: Double,
        val admixture: Double,
        val waterCorrection: Double
    )

    // ══════════════════════════════════════════════════════════════
    //  MAIN MIX DESIGN (ACI 211.1)
    // ══════════════════════════════════════════════════════════════

    fun designMix(input: MixInput): MixResult {
        val notes = mutableListOf<String>()

        // Step 1: Determine required average strength f'cr
        val fcr = if (input.useNoTestData) {
            val estS = estimateStandardDeviation(input.targetStrength)
            val fcrVal = input.targetStrength + 2.33 * estS - 3.45
            notes.add("No test data: estimated S = ${"%.1f".format(estS)} MPa")
            notes.add("f'cr = f'c + 2.33×S - 3.45 = ${"%.1f".format(fcrVal)} MPa")
            fcrVal
        } else {
            val fcrVal = input.targetStrength + 1.34 * input.standardDeviation
            notes.add("f'cr = f'c + 1.34×S = ${input.targetStrength} + 1.34×${input.standardDeviation} = ${"%.1f".format(fcrVal)} MPa")
            fcrVal
        }

        // Step 2: Select water-cement ratio from ACI Table 9.5.1
        val wcRatio = selectWCRatio(fcr)
        notes.add("w/c from ACI Table 9.5.1 for f'cr=${"%.1f".format(fcr)}: ${"%.3f".format(wcRatio)}")

        // Apply exposure limits
        val maxWc = input.exposure.maxWCRatio
        val effectiveWc = minOf(wcRatio, maxWc)
        if (wcRatio > maxWc) {
            notes.add("⚠ Exposure requires max w/c = $maxWc, adjusted")
        }

        // Step 3: Select water content (ACI Table 9.5.3)
        var waterContent = selectWaterContent(input.slump, input.maxAggregateSize)
        notes.add("Water content = ${"%.0f".format(waterContent)} kg/m³")

        // Air content
        val airContent = estimateAirContent(input.maxAggregateSize)
        notes.add("Entrapped air ≈ ${"%.1f".format(airContent)}%")

        // Step 4: Cement content
        var cementContent = waterContent / effectiveWc
        val minCement = input.exposure.minCement
        if (cementContent < minCement) {
            notes.add("⚠ Exposure requires min cement = ${"%.0f".format(minCement)} kg/m³")
            cementContent = minCement
        }
        notes.add("Cement content = ${"%.0f".format(cementContent)} kg/m³")

        val actualWc = waterContent / cementContent

        // Step 5: Coarse aggregate volume fraction (ACI Table 9.5.5)
        val coarseVolFraction = selectCoarseAggregateVolume(
            input.maxAggregateSize, input.finenessModulus
        )
        notes.add("Coarse aggregate vol. fraction = ${"%.3f".format(coarseVolFraction)}")

        // Step 6: Absolute volume method
        val cementVol = cementContent / input.cementType.specificGravity / 1000.0
        val waterVol = waterContent / 1000.0
        val airVol = airContent / 100.0
        val caVolFraction = coarseVolFraction
        val faVol = 1.0 - cementVol - waterVol - airVol - caVolFraction

        val caWeight = caVolFraction * input.coarseAggSG * 1000.0
        val faWeight = maxOf(faVol * input.fineAggSG * 1000.0, 0.0)

        notes.add("Coarse agg = ${"%.0f".format(caWeight)} kg/m³")
        notes.add("Fine agg = ${"%.0f".format(faWeight)} kg/m³")

        // Step 7: Admixture
        var admixtureContent = 0.0
        var effectiveWcRatio = actualWc
        if (input.hasAdmixture && input.admixtureDosage > 0) {
            admixtureContent = cementContent * input.admixtureDosage / 100.0
            val waterReduction = when (input.admixtureType) {
                "Water Reducer" -> 0.10
                "Superplasticizer" -> 0.20
                "Retarder" -> 0.05
                "Accelerator" -> 0.00
                else -> 0.00
            }
            if (waterReduction > 0) {
                val reduced = waterContent * (1.0 - waterReduction)
                effectiveWcRatio = reduced / cementContent
                notes.add("${input.admixtureType}: water -${"%.0f".format(waterReduction * 100)}%, effective w/c = ${"%.3f".format(effectiveWcRatio)}")
            }
            notes.add("Admixture = ${"%.1f".format(admixtureContent)} kg (or L)")
        }

        // Pumpable adjustment
        if (input.isPumpable) {
            notes.add("Pumpable: increase fines, adjust slump to 75-100mm")
        }

        // Weather notes
        when (input.weatherCondition) {
            "Hot" -> notes.add("Hot weather: use ice/cold water, consider retarder")
            "Cold" -> notes.add("Cold weather: use heated water, consider accelerator")
        }

        // Unit weight & yield
        val totalWeight = cementContent + waterContent + caWeight + faWeight
        notes.add("Unit weight ≈ ${"%.0f".format(totalWeight)} kg/m³")

        // Mix ratio
        val ratioFine = if (cementContent > 0) faWeight / cementContent else 0.0
        val ratioCoarse = if (cementContent > 0) caWeight / cementContent else 0.0
        val mixRatio = "1 : ${"%.2f".format(ratioFine)} : ${"%.2f".format(ratioCoarse)}"
        notes.add("Mix ratio (C:FA:CA) = $mixRatio")

        // Trial batch (0.05 m³)
        val tb = computeTrialBatch(
            0.05, cementContent, waterContent, faWeight, caWeight,
            admixtureContent, fineAggMoistureContent(), coarseAggMoistureContent()
        )

        return MixResult(
            targetStrength = input.targetStrength,
            requiredStrength = fcr,
            waterCementRatio = actualWc,
            cementContent = cementContent,
            waterContent = waterContent,
            coarseAggContent = caWeight,
            fineAggContent = faWeight,
            airContent = airContent,
            admixtureContent = admixtureContent,
            wCmRatio = effectiveWcRatio,
            yield = 1.0,
            unitWeight = totalWeight,
            cementType = input.cementType.label,
            exposure = input.exposure.label,
            mixRatio = mixRatio,
            notes = notes,
            trialBatch = tb
        )
    }

    // ══════════════════════════════════════════════════════════════
    //  INTERNAL HELPER: Trial batch
    // ══════════════════════════════════════════════════════════════

    private fun computeTrialBatch(
        volume: Double,
        cement: Double, water: Double, fineAgg: Double, coarseAgg: Double,
        admixture: Double, fineMoisture: Double, coarseMoisture: Double
    ): TrialBatch {
        val tbCement = cement * volume
        val tbWater = water * volume
        val tbFine = fineAgg * volume
        val tbCoarse = coarseAgg * volume
        val tbAdmixture = admixture * volume
        // Water correction for moisture
        val waterCorrection = tbFine * fineMoisture / 100.0 + tbCoarse * coarseMoisture / 100.0
        return TrialBatch(
            batchSize = volume,
            cement = tbCement,
            water = tbWater,
            fineAgg = tbFine,
            coarseAgg = tbCoarse,
            admixture = tbAdmixture,
            waterCorrection = waterCorrection
        )
    }

    // ══════════════════════════════════════════════════════════════
    //  ACI 211.1 TABLES (interpolated)
    // ══════════════════════════════════════════════════════════════

    /**
     * Estimate standard deviation when no test data available (ACI Table 5.3.2.2).
     */
    fun estimateStandardDeviation(fc: Double): Double {
        return when {
            fc < 21 -> 2.8
            fc < 28 -> 3.5
            fc < 35 -> 4.2
            fc < 42 -> 4.8
            else -> 5.5
        }
    }

    /**
     * Water-cement ratio vs compressive strength (ACI Table 9.5.1).
     * Non-air-entrained concrete, approximate.
     */
    fun selectWCRatio(requiredStrength: Double): Double {
        // Data points: (strength MPa, w/c ratio)
        val data = listOf(
            15.0 to 0.70,
            20.0 to 0.62,
            25.0 to 0.55,
            30.0 to 0.50,
            35.0 to 0.45,
            40.0 to 0.42,
            45.0 to 0.38,
            50.0 to 0.35
        )
        return interpolate(data, requiredStrength).coerceIn(0.30, 0.70)
    }

    /**
     * Mixing water content selection (ACI Table 9.5.3).
     * Non-air-entrained concrete, approximate values.
     */
    fun selectWaterContent(slump: Double, maxAggSize: Double): Double {
        // Base water for 75-100mm slump, 20mm agg = 185 kg/m³
        // Adjustments per ACI
        val baseFor20mm = 185.0

        // Slump adjustment: ±3% for each 25mm change from 75mm
        val slumpAdjust = (slump - 75.0) / 25.0 * 0.03

        // Aggregate size adjustment from ACI table (base = 20mm)
        val aggFactor = when {
            maxAggSize <= 10.0 -> 1.20
            maxAggSize <= 12.5 -> 1.12
            maxAggSize <= 20.0 -> 1.00
            maxAggSize <= 25.0 -> 0.93
            maxAggSize <= 40.0 -> 0.81
            maxAggSize <= 50.0 -> 0.75
            maxAggSize <= 75.0 -> 0.66
            else -> 0.60
        }

        return (baseFor20mm * aggFactor * (1.0 + slumpAdjust)).coerceIn(130.0, 230.0)
    }

    /**
     * Estimated air content for non-air-entrained concrete (ACI Table 9.5.4).
     */
    fun estimateAirContent(maxAggSize: Double): Double {
        return when {
            maxAggSize <= 10.0 -> 3.0
            maxAggSize <= 12.5 -> 2.5
            maxAggSize <= 20.0 -> 2.0
            maxAggSize <= 25.0 -> 1.5
            maxAggSize <= 40.0 -> 1.0
            maxAggSize <= 50.0 -> 0.8
            maxAggSize <= 75.0 -> 0.5
            else -> 0.3
        }
    }

    /**
     * Volume of coarse aggregate per unit volume of concrete (ACI Table 9.5.5).
     * Depends on max aggregate size and fineness modulus of fine aggregate.
     */
    fun selectCoarseAggregateVolume(maxAggSize: Double, fm: Double): Double {
        // Table: rows = max agg size, columns = FM of fine agg (2.4 to 3.0)
        val table = mapOf(
            10.0 to listOf(0.50, 0.48, 0.46, 0.44, 0.42),
            12.5 to listOf(0.59, 0.57, 0.55, 0.53, 0.51),
            20.0 to listOf(0.66, 0.64, 0.62, 0.60, 0.58),
            25.0 to listOf(0.71, 0.69, 0.67, 0.65, 0.63),
            40.0 to listOf(0.76, 0.74, 0.72, 0.70, 0.68),
            50.0 to listOf(0.78, 0.76, 0.74, 0.72, 0.70),
            75.0 to listOf(0.81, 0.79, 0.77, 0.75, 0.73)
        )

        // FM columns: 2.4, 2.6, 2.8, 3.0, 3.2
        val fmValues = listOf(2.4, 2.6, 2.8, 3.0, 3.2)

        // Find closest aggregate size
        val aggSizes = table.keys.sorted()
        val lowerIdx = aggSizes.indexOfLast { it <= maxAggSize }.coerceAtLeast(0)
        val upperIdx = (lowerIdx + 1).coerceAtMost(aggSizes.lastIndex)
        val lowerAgg = aggSizes[lowerIdx]
        val upperAgg = aggSizes[upperIdx]

        // Interpolate for FM
        fun interpRow(row: List<Double>): Double {
            val fmLow = fmValues.indexOfLast { it <= fm }.coerceAtLeast(0)
            val fmHigh = (fmLow + 1).coerceAtMost(fmValues.lastIndex)
            val fLo = fmValues[fmLow]
            val fHi = fmValues[fmHigh]
            return if (fHi == fLo) row[fmLow]
            else row[fmLow] + (fm - fLo) / (fHi - fLo) * (row[fmHigh] - row[fmLow])
        }

        val lowerVal = interpRow(table[lowerAgg]!!)
        val upperVal = interpRow(table[upperAgg]!!)
        return if (upperAgg == lowerAgg) lowerVal
        else lowerVal + (maxAggSize - lowerAgg) / (upperAgg - lowerAgg) * (upperVal - lowerVal)
    }

    // ══════════════════════════════════════════════════════════════
    //  QUICK GRADE DESIGN
    // ══════════════════════════════════════════════════════════════

    /**
     * Quick mix design for standard grades with defaults.
     */
    fun quickDesign(grade: Int, exposure: Exposure, cementType: CementType): MixResult {
        return designMix(
            MixInput(
                targetStrength = grade.toDouble(),
                standardDeviation = estimateStandardDeviation(grade.toDouble()),
                maxAggregateSize = 20.0,
                slump = 75.0,
                exposure = exposure,
                cementType = cementType,
                finenessModulus = 2.70
            )
        )
    }

    // ══════════════════════════════════════════════════════════════
    //  UTILITIES
    // ══════════════════════════════════════════════════════════════

    private fun interpolate(data: List<Pair<Double, Double>>, x: Double): Double {
        if (x <= data.first().first) return data.first().second
        if (x >= data.last().first) return data.last().second

        for (i in 0 until data.size - 1) {
            if (x >= data[i].first && x <= data[i + 1].first) {
                val x0 = data[i].first
                val y0 = data[i].second
                val x1 = data[i + 1].first
                val y1 = data[i + 1].second
                return y0 + (x - x0) / (x1 - x0) * (y1 - y0)
            }
        }
        return data.last().second
    }

    fun fineAggMoistureContent(): Double = 3.0
    fun coarseAggMoistureContent(): Double = 1.0
}
