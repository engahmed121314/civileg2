package com.civileg.core.engine.rc.ecp

import kotlin.math.min

/**
 * Explicit Figure4-24 geometry, ECP203-2020 printed4-69..4-71.
 * The caller selects the actual member/face case. A rib is not silently treated
 * as a solid slab, and a tension flange is not credited by a rectangular band.
 * This geometry alone supplies neither steel stress nor a crack/member verdict.
 */
object Ecp203EffectiveTensionArea {
    enum class Case { RECTANGULAR_BEAM_BAND, SOLID_SLAB_SINGLE_LAYER, AXIAL_EQUAL_FACES }

    data class Assessment(
        val case: Case,
        val effectiveDepthPerFaceMm: Double,
        val effectiveAreaPerFaceMm2: Double,
        val reinforcedFaces: Int,
        val effectiveTotalAreaMm2: Double,
        val source: String
    )

    /**
     * Figure4-24(a): the actual area-weighted tension-steel centroid, not the
     * cover to links or the centre of the outermost bar. Constant width over the
     * resulting band is required. Bands deeper than the section are outside
     * this bounded rectangular-band case, not silently capped as solid slabs.
     */
    fun rectangularBeamBand(widthMm: Double, totalDepthMm: Double,
        tensionSteelCentroidFromTensionFaceMm: Double): Assessment {
        requirePositive(widthMm, totalDepthMm, tensionSteelCentroidFromTensionFaceMm)
        val depth = 2.5 * tensionSteelCentroidFromTensionFaceMm
        require(depth.isFinite() && depth <= totalDepthMm) {
            "The Figure4-24(a) constant-width band must fit inside the section; no slab clipping rule is substituted."
        }
        return area(Case.RECTANGULAR_BEAM_BAND, widthMm, depth, 1, "Fig4-24(a), printed4-70")
    }

    /** Figure4-24(b): c is the cracked neutral axis measured from compression. */
    fun solidSlabSingleLayer(widthMm: Double, totalDepthMm: Double,
        clearCoverToMainBarMm: Double, individualBarDiameterMm: Double,
        crackedNeutralAxisFromCompressionFaceMm: Double): Assessment {
        requirePositive(widthMm, totalDepthMm, individualBarDiameterMm,
            crackedNeutralAxisFromCompressionFaceMm)
        require(clearCoverToMainBarMm.isFinite() && clearCoverToMainBarMm >= 0.0)
        val centroidCover = clearCoverToMainBarMm + individualBarDiameterMm / 2.0
        require(clearCoverToMainBarMm + individualBarDiameterMm < totalDepthMm)
        require(crackedNeutralAxisFromCompressionFaceMm < totalDepthMm - centroidCover) {
            "The specified main bar must be on the tension side of the cracked neutral axis."
        }
        val depth = min(2.5 * centroidCover,
            (totalDepthMm - crackedNeutralAxisFromCompressionFaceMm) / 3.0)
        return area(Case.SOLID_SLAB_SINGLE_LAYER, widthMm, depth, 1, "Fig4-24(b), printed4-70")
    }

    /**
     * Figure4-24(c), pure axial tension with equal single-layer face cages.
     * The reported total belongs with steel area summed over BOTH faces.
     * Unequal face cages or one-sided reinforcement need a separate treatment.
     */
    fun axialTensionEqualFaces(widthMm: Double, totalThicknessMm: Double,
        clearCoverToMainBarMm: Double, individualBarDiameterMm: Double): Assessment {
        requirePositive(widthMm, totalThicknessMm, individualBarDiameterMm)
        require(clearCoverToMainBarMm.isFinite() && clearCoverToMainBarMm >= 0.0)
        require(2.0 * (clearCoverToMainBarMm + individualBarDiameterMm) <= totalThicknessMm) {
            "The two specified face-bar layers must fit inside the thickness."
        }
        val depth = min(2.5 * (clearCoverToMainBarMm + individualBarDiameterMm / 2.0),
            totalThicknessMm / 2.0)
        return area(Case.AXIAL_EQUAL_FACES, widthMm, depth, 2, "Fig4-24(c), printed4-71")
    }

    private fun requirePositive(vararg values: Double) = require(values.all { it.isFinite() && it > 0.0 })

    private fun area(case: Case, widthMm: Double, depthMm: Double, faces: Int,
        source: String): Assessment {
        val perFace = widthMm * depthMm
        val total = perFace * faces
        requirePositive(depthMm, perFace, total)
        return Assessment(case, depthMm, perFace, faces, total, "ECP203-2020 $source")
    }
}
