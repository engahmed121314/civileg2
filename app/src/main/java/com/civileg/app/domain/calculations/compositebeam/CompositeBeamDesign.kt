package com.civileg.app.domain.calculations.compositebeam

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

enum class CompositeDesignCode(val code: String, val label: String) {
    ECP("ECP 203", "Egyptian Code ECP 203"),
    ACI("ACI 318", "American Concrete Institute ACI 318"),
    SBC("SBC 304", "Saudi Building Code SBC 304")
}

data class CompositeBeamInput(
    val code: CompositeDesignCode,
    val spanM: Double,
    val beamSpacingM: Double,
    val slabThicknessMm: Double,
    val steelBeamDepthMm: Double,
    val steelBeamAreaMm2: Double,
    val steelBeamInertiaMm4: Double,
    val steelYieldStrengthMPa: Double,
    val concreteStrengthMPa: Double,
    val steelFlangeWidthMm: Double = 200.0,
    val shearStudDiameterMm: Double = 19.0,
    val shearStudHeightMm: Double = 100.0,
    val shearStudUltimateStrengthMPa: Double = 450.0,
    val studsPerRow: Int = 1,
    val liveLoadKPa: Double = 5.0,
    val superimposedDeadLoadKPa: Double = 2.0,
    val steelSelfWeightKNPerM: Double = 0.0,
    val partialShearConnectionRatio: Double = 1.0,
    val deflectionLimitRatio: Double = 360.0,
    val steelModulusMPa: Double = 210_000.0,
    val concreteModulusMPa: Double? = null,
    val providedStuds: Int? = null
) {
    init {
        require(spanM > 0.0) { "Span must be positive" }
        require(beamSpacingM > 0.0) { "Beam spacing must be positive" }
        require(slabThicknessMm > 0.0) { "Slab thickness must be positive" }
        require(steelBeamDepthMm > 0.0) { "Steel beam depth must be positive" }
        require(steelBeamAreaMm2 > 0.0) { "Steel beam area must be positive" }
        require(steelBeamInertiaMm4 > 0.0) { "Steel beam inertia must be positive" }
        require(steelYieldStrengthMPa > 0.0) { "Steel yield strength must be positive" }
        require(concreteStrengthMPa > 0.0) { "Concrete strength must be positive" }
        require(steelFlangeWidthMm > 0.0) { "Steel flange width must be positive" }
        require(shearStudDiameterMm > 0.0) { "Shear stud diameter must be positive" }
        require(shearStudHeightMm > 0.0) { "Shear stud height must be positive" }
        require(shearStudUltimateStrengthMPa > 0.0) { "Shear stud ultimate strength must be positive" }
        require(studsPerRow >= 1) { "At least one stud per row is required" }
        require(liveLoadKPa >= 0.0) { "Live load cannot be negative" }
        require(superimposedDeadLoadKPa >= 0.0) { "Superimposed dead load cannot be negative" }
        require(steelSelfWeightKNPerM >= 0.0) { "Steel self weight cannot be negative" }
        require(partialShearConnectionRatio in 0.4..1.0) {
            "Partial shear connection ratio must be between 0.4 and 1.0"
        }
        require(deflectionLimitRatio > 0.0) { "Deflection limit ratio must be positive" }
        require(steelModulusMPa > 0.0) { "Steel modulus must be positive" }
        require(concreteModulusMPa == null || concreteModulusMPa > 0.0) {
            "Concrete modulus must be positive"
        }
        require(providedStuds == null || providedStuds >= 0) { "Provided studs cannot be negative" }
    }
}

data class CompositeBeamDesignResult(
    val effectiveWidthMm: Double,
    val concreteDesignStrengthMPa: Double,
    val beta1: Double,
    val steelTensionForceKN: Double,
    val concreteCompressionForceKN: Double,
    val fullShearConnectionForceKN: Double,
    val designShearConnectionForceKN: Double,
    val compressionBlockDepthMm: Double,
    val nominalFlexuralCapacityKNm: Double,
    val flexuralCapacityKNm: Double,
    val designMomentKNm: Double,
    val momentUtilization: Double,
    val flexureOk: Boolean,
    val shearStudCapacityKN: Double,
    val requiredStuds: Int,
    val requiredStudRows: Int,
    val providedStuds: Int,
    val shearConnectionOk: Boolean,
    val concreteModulusMPa: Double,
    val modularRatio: Double,
    val transformedInertiaMm4: Double,
    val serviceLoadKNPerM: Double,
    val deflectionMm: Double,
    val deflectionLimitMm: Double,
    val deflectionOk: Boolean,
    val warnings: List<String>,
    val notes: List<String>
)

object CompositeBeamDesign {

    const val CONCRETE_UNIT_WEIGHT_KN_M3 = 24.0

    fun effectiveWidthMm(input: CompositeBeamInput): Double {
        val spanLimitMm = input.spanM * 1000.0 / 4.0
        val spacingMm = input.beamSpacingM * 1000.0
        val slabLimitMm = when (input.code) {
            CompositeDesignCode.ECP -> input.steelFlangeWidthMm + 12.0 * input.slabThicknessMm
            CompositeDesignCode.ACI, CompositeDesignCode.SBC ->
                input.steelFlangeWidthMm + 16.0 * input.slabThicknessMm
        }
        return minOf(spanLimitMm, spacingMm, slabLimitMm)
    }

    fun concreteDesignStrengthMPa(input: CompositeBeamInput): Double = when (input.code) {
        CompositeDesignCode.ECP -> 0.67 * input.concreteStrengthMPa / 1.5
        CompositeDesignCode.ACI, CompositeDesignCode.SBC -> 0.85 * input.concreteStrengthMPa
    }

    fun beta1(input: CompositeBeamInput): Double = when (input.code) {
        CompositeDesignCode.ECP -> {
            if (input.concreteStrengthMPa <= 30.0) {
                0.80
            } else {
                (0.80 - (input.concreteStrengthMPa - 30.0) / 70.0).coerceAtLeast(0.65)
            }
        }
        CompositeDesignCode.ACI, CompositeDesignCode.SBC -> {
            if (input.concreteStrengthMPa <= 28.0) {
                0.85
            } else {
                (0.85 - (input.concreteStrengthMPa - 28.0) * 0.05 / 7.0).coerceAtLeast(0.65)
            }
        }
    }

    fun flexuralResistanceFactor(code: CompositeDesignCode): Double = when (code) {
        CompositeDesignCode.ECP -> 0.85
        CompositeDesignCode.ACI, CompositeDesignCode.SBC -> 0.90
    }

    fun concreteModulusMPa(input: CompositeBeamInput): Double =
        input.concreteModulusMPa ?: when (input.code) {
            CompositeDesignCode.ECP -> 4400.0 * sqrt(input.concreteStrengthMPa)
            CompositeDesignCode.ACI, CompositeDesignCode.SBC ->
                4700.0 * sqrt(input.concreteStrengthMPa)
        }

    fun fullShearConnectionForceKN(input: CompositeBeamInput): Double {
        val steelTensionKN = input.steelBeamAreaMm2 * input.steelYieldStrengthMPa / 1000.0
        val concreteCompressionKN = concreteDesignStrengthMPa(input) *
            effectiveWidthMm(input) * input.slabThicknessMm / 1000.0
        return min(steelTensionKN, concreteCompressionKN)
    }

    fun shearStudCapacityKN(input: CompositeBeamInput): Double {
        val areaMm2 = PI * input.shearStudDiameterMm * input.shearStudDiameterMm / 4.0
        return when (input.code) {
            CompositeDesignCode.ECP ->
                0.8 * areaMm2 * input.shearStudUltimateStrengthMPa / 1000.0
            CompositeDesignCode.ACI, CompositeDesignCode.SBC -> {
                val concreteModulus = concreteModulusMPa(input)
                val concreteLimitN = 0.5 * areaMm2 *
                    sqrt(input.concreteStrengthMPa * concreteModulus)
                val steelLimitN = areaMm2 * input.shearStudUltimateStrengthMPa
                min(concreteLimitN, steelLimitN) / 1000.0
            }
        }
    }

    fun deadLoadKNPerM(input: CompositeBeamInput): Double {
        val slabSelfWeight = CONCRETE_UNIT_WEIGHT_KN_M3 *
            (input.slabThicknessMm / 1000.0) * input.beamSpacingM
        val superimposed = input.superimposedDeadLoadKPa * input.beamSpacingM
        return slabSelfWeight + superimposed + input.steelSelfWeightKNPerM
    }

    fun liveLoadKNPerM(input: CompositeBeamInput): Double =
        input.liveLoadKPa * input.beamSpacingM

    fun factoredLoadKNPerM(input: CompositeBeamInput): Double {
        val dead = deadLoadKNPerM(input)
        val live = liveLoadKNPerM(input)
        return when (input.code) {
            CompositeDesignCode.ECP -> 1.4 * dead + 1.6 * live
            CompositeDesignCode.ACI, CompositeDesignCode.SBC -> 1.2 * dead + 1.6 * live
        }
    }

    fun serviceLoadKNPerM(input: CompositeBeamInput): Double =
        deadLoadKNPerM(input) + liveLoadKNPerM(input)

    fun designMomentKNm(input: CompositeBeamInput): Double =
        factoredLoadKNPerM(input) * input.spanM * input.spanM / 8.0

    fun design(input: CompositeBeamInput): CompositeBeamDesignResult {
        val effectiveWidth = effectiveWidthMm(input)
        val concreteStress = concreteDesignStrengthMPa(input)
        val steelTensionKN = input.steelBeamAreaMm2 * input.steelYieldStrengthMPa / 1000.0
        val concreteCompressionKN =
            concreteStress * effectiveWidth * input.slabThicknessMm / 1000.0
        val fullShearForceKN = min(steelTensionKN, concreteCompressionKN)
        val designShearForceKN = fullShearForceKN * input.partialShearConnectionRatio
        val compressionForceKN = min(steelTensionKN, designShearForceKN)
        val compressionBlockDepthMm = compressionForceKN * 1000.0 / (concreteStress * effectiveWidth)
        val nominalMomentKNm = if (compressionBlockDepthMm <= input.slabThicknessMm) {
            compressionForceKN * (
                input.steelBeamDepthMm / 2.0 +
                    input.slabThicknessMm -
                    compressionBlockDepthMm / 2.0
                ) / 1000.0
        } else {
            (
                compressionForceKN * input.steelBeamDepthMm / 2.0 +
                    concreteCompressionKN * input.slabThicknessMm / 2.0
                ) / 1000.0
        }
        val capacityKNm = flexuralResistanceFactor(input.code) * nominalMomentKNm
        val momentKNm = designMomentKNm(input)
        val utilization = if (capacityKNm > 0.0) momentKNm / capacityKNm else Double.POSITIVE_INFINITY

        val studCapacityKN = shearStudCapacityKN(input)
        val requiredStuds = if (studCapacityKN > 0.0) {
            ceil(designShearForceKN / studCapacityKN).toInt().coerceAtLeast(1)
        } else {
            Int.MAX_VALUE
        }
        val providedStuds = input.providedStuds ?: requiredStuds
        val requiredStudRows = ceil(requiredStuds.toDouble() / input.studsPerRow).toInt()

        val concreteModulus = concreteModulusMPa(input)
        val modularRatio = input.steelModulusMPa / concreteModulus
        val transformedSlabWidthMm = effectiveWidth / modularRatio
        val slabAreaMm2 = transformedSlabWidthMm * input.slabThicknessMm
        val steelCentroidMm = input.steelBeamDepthMm / 2.0
        val slabCentroidMm = input.steelBeamDepthMm + input.slabThicknessMm / 2.0
        val centroidMm = (
            slabAreaMm2 * slabCentroidMm +
                input.steelBeamAreaMm2 * steelCentroidMm
            ) / (slabAreaMm2 + input.steelBeamAreaMm2)
        val transformedSlabInertiaMm4 = transformedSlabWidthMm *
            input.slabThicknessMm.pow(3) / 12.0 +
            slabAreaMm2 * (slabCentroidMm - centroidMm).pow(2)
        val steelInertiaMm4 = input.steelBeamInertiaMm4 +
            input.steelBeamAreaMm2 * (steelCentroidMm - centroidMm).pow(2)
        val transformedInertiaMm4 = transformedSlabInertiaMm4 + steelInertiaMm4

        val spanMm = input.spanM * 1000.0
        val serviceLoad = serviceLoadKNPerM(input)
        val deflectionMm = if (transformedInertiaMm4 > 0.0) {
            5.0 * serviceLoad * spanMm.pow(4) /
                (384.0 * input.steelModulusMPa * transformedInertiaMm4)
        } else {
            Double.POSITIVE_INFINITY
        }
        val deflectionLimitMm = spanMm / input.deflectionLimitRatio
        val deflectionOk = deflectionMm <= deflectionLimitMm

        val warnings = mutableListOf<String>()
        val notes = mutableListOf<String>()

        if (steelTensionKN > concreteCompressionKN + 1e-6) {
            warnings.add(
                "Plastic neutral axis lies in the steel section; capacity is limited by the " +
                    "concrete slab compression strength."
            )
        }
        if (utilization > 1.0) {
            warnings.add(
                "Flexural demand ${format(momentKNm)} kN.m exceeds capacity " +
                    "${format(capacityKNm)} kN.m."
            )
        }
        if (!deflectionOk) {
            warnings.add(
                "Service deflection ${format(deflectionMm)} mm exceeds the limit " +
                    "${format(deflectionLimitMm)} mm."
            )
        }
        if (providedStuds < requiredStuds) {
            warnings.add(
                "Provided studs ($providedStuds) are fewer than the required " +
                    "$requiredStuds studs."
            )
        }
        if (input.shearStudHeightMm < 4.0 * input.shearStudDiameterMm) {
            warnings.add("Headed stud height should be at least 4 times its diameter.")
        }
        if (input.partialShearConnectionRatio < 1.0) {
            notes.add(
                "Partial shear connection applied with ratio " +
                    "${format(input.partialShearConnectionRatio)}."
            )
        }
        if (requiredStuds == Int.MAX_VALUE) {
            warnings.add("Shear stud capacity is not positive; check stud properties.")
        }

        return CompositeBeamDesignResult(
            effectiveWidthMm = effectiveWidth,
            concreteDesignStrengthMPa = concreteStress,
            beta1 = beta1(input),
            steelTensionForceKN = steelTensionKN,
            concreteCompressionForceKN = concreteCompressionKN,
            fullShearConnectionForceKN = fullShearForceKN,
            designShearConnectionForceKN = designShearForceKN,
            compressionBlockDepthMm = compressionBlockDepthMm,
            nominalFlexuralCapacityKNm = nominalMomentKNm,
            flexuralCapacityKNm = capacityKNm,
            designMomentKNm = momentKNm,
            momentUtilization = utilization,
            flexureOk = momentKNm <= capacityKNm,
            shearStudCapacityKN = studCapacityKN,
            requiredStuds = requiredStuds,
            requiredStudRows = requiredStudRows,
            providedStuds = providedStuds,
            shearConnectionOk = providedStuds >= requiredStuds,
            concreteModulusMPa = concreteModulus,
            modularRatio = modularRatio,
            transformedInertiaMm4 = transformedInertiaMm4,
            serviceLoadKNPerM = serviceLoad,
            deflectionMm = deflectionMm,
            deflectionLimitMm = deflectionLimitMm,
            deflectionOk = deflectionOk,
            warnings = warnings,
            notes = notes
        )
    }

    private fun format(value: Double): String = "%.2f".format(value)
}
