package com.civileg.app.domain.calculations.aci

import android.os.Parcelable
import com.civileg.app.domain.entities.*
import kotlinx.parcelize.Parcelize
import kotlin.math.*

// =====================================================================
// AISC 360-16 LRFD Steel Design Engine — محرك تصميم المنشآت المعدنية
// Comprehensive implementation following AISC 360-16 specifications
// =====================================================================

@Parcelize
enum class ElementClassification : Parcelable { COMPACT, NONCOMPACT, SLENDER }

@Parcelize
data class SectionClassification(
    val flangeStatus: ElementClassification,
    val webStatus: ElementClassification,
    val overall: String,
    val flangeSlenderness: Double,
    val webSlenderness: Double,
    val flangeCompactLimit: Double,
    val flangeSlenderLimit: Double
) : Parcelable

@Parcelize
data class SteelTensionResult(
    val grossYielding: Double,
    val netRupture: Double,
    val blockShear: Double,
    val designCapacity: Double,
    val utilizationRatio: Double,
    val isSafe: Boolean,
    val governingCase: String,
    val warnings: List<String>,
    val codeNotes: List<String>
) : Parcelable

@Parcelize
data class SteelCompressionResult(
    val Fcr: Double,
    val Pn: Double,
    val phiPn: Double,
    val slendernessRatio: Double,
    val bucklingMode: String,
    val utilizationRatio: Double,
    val isSafe: Boolean,
    val warnings: List<String>,
    val codeNotes: List<String>
) : Parcelable

@Parcelize
data class SteelFlexuralResult(
    val momentCapacityX: Double,
    val momentCapacityY: Double,
    val shearCapacityX: Double,
    val shearCapacityY: Double,
    val ltbCapacity: Double,
    val deflectionRatio: Double,
    val isSafe: Boolean,
    val governingCheck: String,
    val warnings: List<String>,
    val codeNotes: List<String>
) : Parcelable

@Parcelize
data class CombinedLoadingResult(
    val interactionRatio: Double,
    val axialRatio: Double,
    val flexuralRatioX: Double,
    val flexuralRatioY: Double,
    val equation: String,
    val isSafe: Boolean,
    val warnings: List<String>,
    val codeNotes: List<String>
) : Parcelable

@Parcelize
data class SteelBracingResult(
    val compressionCapacity: Double,
    val slendernessRatio: Double,
    val isSafe: Boolean,
    val connectionAdequate: Boolean,
    val warnings: List<String>,
    val codeNotes: List<String>
) : Parcelable

@Parcelize
data class CompositeBeamResult(
    val compositeMomentCapacity: Double,
    val studsRequired: Int,
    val studsProvided: Int,
    val shearStudsAdequate: Boolean,
    val isSafe: Boolean,
    val warnings: List<String>,
    val codeNotes: List<String>
) : Parcelable

class AISCSteelDesignEngine {

    companion object {
        const val PHI_TENSION = 0.90
        const val PHI_COMPRESSION = 0.90
        const val PHI_FLEXURE = 0.90
        const val PHI_SHEAR = 0.90
        const val PHI_BEARING = 0.75
        const val E_STEEL = 200000.0
        const val G_STEEL = 76923.0
        const val STEEL_DENSITY = 7.85e-6
        const val MAX_SLENDERNESS_COMPRESSION = 200.0
        const val MAX_SLENDERNESS_TENSION = 300.0
        const val MAX_SLENDERNESS_BRACING = 200.0
    }

    private fun getIy(section: SteelSectionType): Double = section.iy
    private fun getRy(section: SteelSectionType): Double = section.ry
    private fun getJ(section: SteelSectionType): Double = section.j
    private fun getCw(section: SteelSectionType): Double = section.cw
    private fun getH0(section: SteelSectionType): Double = section.depth - section.flangeThickness

    // Flexural design - Main LTB Logic
    fun calculateNominalFlexuralStrength(
        section: SteelSectionType,
        Fy: Double,
        Lb: Double,
        Cb: Double,
        isLaterallyBraced: Boolean,
        classification: SectionClassification,
        codeNotes: MutableList<String>
    ): Double {
        val Zx = section.zx
        val Sx = section.sx
        val ry = getRy(section)
        val Iy = getIy(section)
        val Cw = getCw(section)
        val J = getJ(section)
        val h0 = getH0(section)
        val c = 1.0

        val Mp = Fy * Zx / 1e6
        val Lp = 1.76 * ry * sqrt(E_STEEL / Fy)
        val rt = sqrt(sqrt(Iy * Cw) / Sx.coerceAtLeast(1.0))
        val Lr = 1.95 * rt * sqrt(E_STEEL / (0.7 * Fy))

        codeNotes.add("Lp = ${"%.0f".format(Lp)} mm, Lr = ${"%.0f".format(Lr)} mm, rt = ${"%.1f".format(rt)} mm")

        return if (isLaterallyBraced || Lb <= Lp) {
            Mp
        } else if (Lb <= Lr) {
            val MnLr = 0.7 * Fy * Sx / 1e6
            val MnLtb = Cb * Mp - (Cb * Mp - MnLr) * ((Lb - Lp) / (Lr - Lp).coerceAtLeast(1.0))
            MnLtb.coerceAtMost(Mp)
        } else {
            // [P038/W2 FIX] Elastic LTB — AISC F2-4 uses (Lb/rt)²
            val FcrLtb = Cb * PI * PI * E_STEEL / ((Lb / rt.coerceAtLeast(1e-6)).pow(2)) * 
                         sqrt(1.0 + 0.078 * (J * c / (Sx * h0)) * (Lb / rt).pow(2))
            val MnLtb = FcrLtb * Sx / 1e6
            MnLtb.coerceAtMost(Mp)
        }
    }

    fun designCompositeBeam(
        steelSection: SteelSectionType,
        grade: SteelGrade,
        fcu: Double,
        slabThickness: Double,
        effectiveWidth: Double,
        studDiameter: Double,
        studSpacing: Double,
        Mu: Double,
        span: Double
    ): CompositeBeamResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val Fy = grade.fy
        val Ec = 4700.0 * sqrt(fcu)
        val concreteModularRatio = E_STEEL / Ec
        val As = steelSection.area
        val d = steelSection.depth
        val Zx = steelSection.zx
        val Ac = effectiveWidth * slabThickness
        val AsStud = PI * studDiameter * studDiameter / 4.0
        val Qn = minOf(0.5 * AsStud * sqrt(fcu * Ec), 450.0 * AsStud) // Simplified Qn

        val Vh = minOf(0.85 * fcu * Ac / 1000.0, Fy * As / 1000.0)
        val studsRequired = if (Qn > 0) ceil(Vh * 1000.0 / Qn).toInt() else 0
        val studsProvided = if (studSpacing > 0) (span / studSpacing).toInt() + 1 else 0
        val compositeMomentCapacity = (Fy * As * (d / 2.0 + slabThickness - (Fy * As / (0.85 * fcu * effectiveWidth)) / 2.0)) / 1e6
        
        val phiMn = PHI_FLEXURE * compositeMomentCapacity
        val isSafe = (Mu <= phiMn) && (studsProvided >= studsRequired)

        return CompositeBeamResult(compositeMomentCapacity, studsRequired, studsProvided, studsProvided >= studsRequired, isSafe, warnings, codeNotes)
    }
}
