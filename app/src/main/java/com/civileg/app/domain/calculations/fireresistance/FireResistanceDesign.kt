package com.civileg.app.domain.calculations.fireresistance

import kotlin.math.log10
import kotlin.math.min

enum class FireCode(val label: String) {
    ECP("ECP 203"),
    ACI("ACI 216.1")
}

enum class FireElementType(val label: String) {
    BEAM("Beam"),
    COLUMN("Column"),
    SLAB("Slab"),
    WALL("Wall")
}

data class FireRequirement(
    val code: FireCode,
    val elementType: FireElementType,
    val ratingMinutes: Int,
    val minimumCoverMm: Double,
    val minimumDimensionMm: Double
)

data class FireResistanceInput(
    val code: FireCode,
    val elementType: FireElementType,
    val requiredRatingMinutes: Int,
    val coverMm: Double,
    val sectionWidthMm: Double,
    val sectionDepthMm: Double,
    val concreteStrengthMPa: Double,
    val steelStrengthMPa: Double = 420.0,
    val reinforcementRatio: Double = 0.01
) {
    init {
        require(requiredRatingMinutes in 1..240) {
            "Required fire rating must be between 1 and 240 minutes"
        }
        require(coverMm >= 0.0) { "Concrete cover cannot be negative" }
        require(sectionWidthMm > 0.0) { "Section width must be positive" }
        require(sectionDepthMm > 0.0) { "Section depth must be positive" }
        require(concreteStrengthMPa > 0.0) { "Concrete strength must be positive" }
        require(steelStrengthMPa > 0.0) { "Steel strength must be positive" }
        require(reinforcementRatio in 0.0..0.10) {
            "Reinforcement ratio must be between 0 and 0.10"
        }
    }
}

data class FireResistanceResult(
    val code: FireCode,
    val elementType: FireElementType,
    val requiredRatingMinutes: Int,
    val standardRatingMinutes: Int,
    val iso834TemperatureC: Double,
    val concreteStrengthFactor: Double,
    val reducedConcreteStrengthMPa: Double,
    val steelStrengthFactor: Double,
    val reducedSteelStrengthMPa: Double,
    val reducedCapacityIndex: Double,
    val minimumCoverMm: Double,
    val minimumDimensionMm: Double,
    val actualCoverMm: Double,
    val actualMinimumDimensionMm: Double,
    val coverOk: Boolean,
    val dimensionOk: Boolean,
    val achievedRatingMinutes: Int,
    val ratingOk: Boolean,
    val warnings: List<String>,
    val notes: List<String>
)

object FireResistanceDesign {

    private data class CoverDimension(val coverMm: Double, val dimensionMm: Double)

    private val STANDARD_RATINGS = listOf(30, 60, 90, 120, 180, 240)

    private val CONCRETE_TEMPERATURE_FACTORS = listOf(
        20.0 to 1.00,
        100.0 to 1.00,
        200.0 to 0.95,
        300.0 to 0.85,
        400.0 to 0.75,
        500.0 to 0.60,
        600.0 to 0.45,
        700.0 to 0.30,
        800.0 to 0.15,
        900.0 to 0.08,
        1000.0 to 0.04,
        1100.0 to 0.01
    )

    private val STEEL_TEMPERATURE_FACTORS = listOf(
        20.0 to 1.00,
        100.0 to 1.00,
        200.0 to 1.00,
        300.0 to 1.00,
        400.0 to 1.00,
        500.0 to 0.78,
        600.0 to 0.47,
        700.0 to 0.23,
        800.0 to 0.11,
        900.0 to 0.06,
        1000.0 to 0.04,
        1100.0 to 0.02,
        1200.0 to 0.00
    )

    private val ECP_REQUIREMENTS: Map<FireElementType, Map<Int, CoverDimension>> = mapOf(
        FireElementType.BEAM to mapOf(
            30 to CoverDimension(20.0, 150.0),
            60 to CoverDimension(25.0, 200.0),
            90 to CoverDimension(30.0, 250.0),
            120 to CoverDimension(35.0, 300.0),
            180 to CoverDimension(40.0, 400.0),
            240 to CoverDimension(50.0, 500.0)
        ),
        FireElementType.COLUMN to mapOf(
            30 to CoverDimension(20.0, 200.0),
            60 to CoverDimension(25.0, 250.0),
            90 to CoverDimension(30.0, 300.0),
            120 to CoverDimension(35.0, 350.0),
            180 to CoverDimension(40.0, 450.0),
            240 to CoverDimension(50.0, 500.0)
        ),
        FireElementType.SLAB to mapOf(
            30 to CoverDimension(15.0, 60.0),
            60 to CoverDimension(20.0, 80.0),
            90 to CoverDimension(25.0, 100.0),
            120 to CoverDimension(30.0, 120.0),
            180 to CoverDimension(40.0, 150.0),
            240 to CoverDimension(50.0, 175.0)
        ),
        FireElementType.WALL to mapOf(
            30 to CoverDimension(15.0, 100.0),
            60 to CoverDimension(20.0, 120.0),
            90 to CoverDimension(25.0, 140.0),
            120 to CoverDimension(30.0, 160.0),
            180 to CoverDimension(35.0, 200.0),
            240 to CoverDimension(40.0, 240.0)
        )
    )

    private val ACI_REQUIREMENTS: Map<FireElementType, Map<Int, CoverDimension>> = mapOf(
        FireElementType.BEAM to mapOf(
            30 to CoverDimension(15.0, 100.0),
            60 to CoverDimension(20.0, 125.0),
            90 to CoverDimension(25.0, 150.0),
            120 to CoverDimension(30.0, 200.0),
            180 to CoverDimension(40.0, 250.0),
            240 to CoverDimension(50.0, 300.0)
        ),
        FireElementType.COLUMN to mapOf(
            30 to CoverDimension(15.0, 150.0),
            60 to CoverDimension(20.0, 200.0),
            90 to CoverDimension(25.0, 250.0),
            120 to CoverDimension(30.0, 300.0),
            180 to CoverDimension(40.0, 400.0),
            240 to CoverDimension(50.0, 450.0)
        ),
        FireElementType.SLAB to mapOf(
            30 to CoverDimension(10.0, 50.0),
            60 to CoverDimension(15.0, 65.0),
            90 to CoverDimension(20.0, 80.0),
            120 to CoverDimension(25.0, 100.0),
            180 to CoverDimension(30.0, 125.0),
            240 to CoverDimension(40.0, 150.0)
        ),
        FireElementType.WALL to mapOf(
            30 to CoverDimension(10.0, 75.0),
            60 to CoverDimension(15.0, 100.0),
            90 to CoverDimension(20.0, 125.0),
            120 to CoverDimension(25.0, 150.0),
            180 to CoverDimension(30.0, 175.0),
            240 to CoverDimension(40.0, 200.0)
        )
    )

    fun iso834TemperatureC(minutes: Double): Double {
        require(minutes >= 0.0) { "Time cannot be negative" }
        return 20.0 + 345.0 * log10(8.0 * minutes + 1.0)
    }

    fun iso834TemperatureC(minutes: Int): Double = iso834TemperatureC(minutes.toDouble())

    fun concreteStrengthFactor(temperatureC: Double): Double =
        interpolate(CONCRETE_TEMPERATURE_FACTORS, temperatureC)

    fun steelStrengthFactor(temperatureC: Double): Double =
        interpolate(STEEL_TEMPERATURE_FACTORS, temperatureC)

    fun minimumRequirement(
        code: FireCode,
        elementType: FireElementType,
        ratingMinutes: Int
    ): FireRequirement {
        require(ratingMinutes in 1..240) {
            "Fire rating must be between 1 and 240 minutes"
        }
        val table = requirementTable(code, elementType)
        val standardRating = STANDARD_RATINGS.firstOrNull { it >= ratingMinutes }
            ?: STANDARD_RATINGS.last()
        val requirement = table.getValue(standardRating)
        return FireRequirement(
            code = code,
            elementType = elementType,
            ratingMinutes = standardRating,
            minimumCoverMm = requirement.coverMm,
            minimumDimensionMm = requirement.dimensionMm
        )
    }

    fun achievedRatingMinutes(
        code: FireCode,
        elementType: FireElementType,
        coverMm: Double,
        minimumDimensionMm: Double
    ): Int {
        require(coverMm >= 0.0) { "Concrete cover cannot be negative" }
        require(minimumDimensionMm > 0.0) { "Minimum dimension must be positive" }
        return requirementTable(code, elementType)
            .filterValues { requirement ->
                coverMm >= requirement.coverMm && minimumDimensionMm >= requirement.dimensionMm
            }
            .keys
            .maxOrNull() ?: 0
    }

    fun design(input: FireResistanceInput): FireResistanceResult {
        val requirement = minimumRequirement(
            input.code,
            input.elementType,
            input.requiredRatingMinutes
        )
        val actualMinimumDimensionMm = min(input.sectionWidthMm, input.sectionDepthMm)
        val coverOk = input.coverMm >= requirement.minimumCoverMm
        val dimensionOk = actualMinimumDimensionMm >= requirement.minimumDimensionMm
        val achievedRating = achievedRatingMinutes(
            input.code,
            input.elementType,
            input.coverMm,
            actualMinimumDimensionMm
        )
        val temperatureC = iso834TemperatureC(input.requiredRatingMinutes.toDouble())
        val concreteFactor = concreteStrengthFactor(temperatureC)
        val steelFactor = steelStrengthFactor(temperatureC)
        val reducedConcreteStrengthMPa = input.concreteStrengthMPa * concreteFactor
        val reducedSteelStrengthMPa = input.steelStrengthMPa * steelFactor
        val reducedCapacityIndex = input.reinforcementRatio * steelFactor +
            (1.0 - input.reinforcementRatio) * concreteFactor
        val ratingOk = achievedRating >= input.requiredRatingMinutes

        val warnings = mutableListOf<String>()
        val notes = mutableListOf<String>()

        if (!coverOk) {
            warnings.add(
                "Cover ${format(input.coverMm)} mm is less than the required " +
                    "${format(requirement.minimumCoverMm)} mm for " +
                    "${requirement.ratingMinutes}-minute ${input.elementType.label.lowercase()}."
            )
        }
        if (!dimensionOk) {
            warnings.add(
                "Minimum dimension ${format(actualMinimumDimensionMm)} mm is less than the " +
                    "required ${format(requirement.minimumDimensionMm)} mm for " +
                    "${requirement.ratingMinutes}-minute ${input.elementType.label.lowercase()}."
            )
        }
        if (!ratingOk) {
            warnings.add(
                "Achieved fire rating is $achievedRating minutes, below the required " +
                    "${input.requiredRatingMinutes} minutes."
            )
        }
        if (requirement.ratingMinutes != input.requiredRatingMinutes) {
            notes.add(
                "Required rating ${input.requiredRatingMinutes} minutes is checked against " +
                    "the standard ${requirement.ratingMinutes}-minute requirements."
            )
        }
        if (coverOk && dimensionOk && ratingOk) {
            notes.add("Prescriptive fire resistance requirements are satisfied.")
        }
        notes.add(
            "ISO 834 temperature and reduced strengths are evaluated at the required rating; " +
                "detailed thermal analysis is required for non-standard sections."
        )

        return FireResistanceResult(
            code = input.code,
            elementType = input.elementType,
            requiredRatingMinutes = input.requiredRatingMinutes,
            standardRatingMinutes = requirement.ratingMinutes,
            iso834TemperatureC = temperatureC,
            concreteStrengthFactor = concreteFactor,
            reducedConcreteStrengthMPa = reducedConcreteStrengthMPa,
            steelStrengthFactor = steelFactor,
            reducedSteelStrengthMPa = reducedSteelStrengthMPa,
            reducedCapacityIndex = reducedCapacityIndex,
            minimumCoverMm = requirement.minimumCoverMm,
            minimumDimensionMm = requirement.minimumDimensionMm,
            actualCoverMm = input.coverMm,
            actualMinimumDimensionMm = actualMinimumDimensionMm,
            coverOk = coverOk,
            dimensionOk = dimensionOk,
            achievedRatingMinutes = achievedRating,
            ratingOk = ratingOk,
            warnings = warnings,
            notes = notes
        )
    }

    private fun requirementTable(
        code: FireCode,
        elementType: FireElementType
    ): Map<Int, CoverDimension> = when (code) {
        FireCode.ECP -> ECP_REQUIREMENTS.getValue(elementType)
        FireCode.ACI -> ACI_REQUIREMENTS.getValue(elementType)
    }

    private fun interpolate(
        points: List<Pair<Double, Double>>,
        value: Double
    ): Double {
        val first = points.first()
        val last = points.last()
        if (value <= first.first) return first.second
        if (value >= last.first) return last.second
        for (index in 0 until points.size - 1) {
            val lower = points[index]
            val upper = points[index + 1]
            if (value >= lower.first && value <= upper.first) {
                val ratio = (value - lower.first) / (upper.first - lower.first)
                return lower.second + ratio * (upper.second - lower.second)
            }
        }
        return last.second
    }

    private fun format(value: Double): String = "%.2f".format(value)
}
