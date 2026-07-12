package com.civileg.app.domain.calculations.aci

import android.os.Parcelable
import com.civileg.app.domain.entities.*
import kotlinx.parcelize.Parcelize
import kotlin.math.*

// =====================================================================
// AISC 360-16 LRFD Steel Design Engine — محرك تصميم المنشآت المعدنية
// Comprehensive implementation following AISC 360-16 specifications
// =====================================================================

// ======================== RESULT DATA CLASSES ========================

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

// ======================== MAIN ENGINE CLASS ==========================

/**
 * محرك تصميم شامل حسب مواصفة AISC 360-16
 * يغطي: تصنيف المقاطع، الشد، الضغط، الانحناء، القص، الحمل المركب، الكمرات المركبة
 * Comprehensive AISC 360-16 (LRFD) steel design engine
 */
class AISCSteelDesignEngine {

    companion object {
        // AISC 360-16 Resistance Factors
        const val PHI_TENSION = 0.90        // AISC D2
        const val PHI_COMPRESSION = 0.90    // AISC E1
        const val PHI_FLEXURE = 0.90        // AISC F1
        const val PHI_SHEAR = 0.90          // AISC G1
        const val PHI_BEARING = 0.75        // AISC J3.10

        // Material Properties
        const val E_STEEL = 200000.0         // MPa (29,000 ksi)
        const val G_STEEL = 76923.0          // MPa (11,200 ksi)
        const val STEEL_DENSITY = 7.85e-6    // kg/mm³

        // Slenderness Limits (AISC D1, Table B4.1a/b)
        const val MAX_SLENDERNESS_COMPRESSION = 200.0
        const val MAX_SLENDERNESS_TENSION = 300.0
        const val MAX_SLENDERNESS_BRACING = 200.0

        // Standard bolt hole diameter (mm) — AISC J3.3
        const val STANDARD_BOLT_HOLE_INCREMENT = 2.0  // mm added to bolt diameter
    }

    // ===================== SECTION PROPERTY HELPERS =====================

    /** Weak axis moment of inertia Iy — mm⁴ (AISC properties) */
    private fun getIy(section: SteelSectionType): Double = when (section) {
        is SteelSectionType.ISection -> {
            val b = section.bf
            val tf = section.tf
            val h = section.h
            val tw = section.tw
            val hw = (h - 2 * tf).coerceAtLeast(0.001)
            2.0 * (tf * b.pow(3) / 12.0) + (hw * tw.pow(3) / 12.0)
        }
        is SteelSectionType.CSection -> {
            val b = section.bf
            val tf = section.tf
            val h = section.h
            val tw = section.tw
            val hw = (h - 2 * tf).coerceAtLeast(0.001)
            // Simplified Iy for channel (about centroidal y-axis, not shear center)
            2.0 * (tf * b.pow(3) / 12.0) + (hw * tw.pow(3) / 12.0)
        }
        is SteelSectionType.RHS -> {
            val w = section.width
            val h = section.height
            val t = section.thickness
            val hw = (h - 2 * t).coerceAtLeast(0.001)
            val bw = (w - 2 * t).coerceAtLeast(0.001)
            (h * w.pow(3) - hw * bw.pow(3)) / 12.0
        }
        is SteelSectionType.CHS -> {
            // Iy = Ix for circular section
            val D = section.outerDiameter
            val t = section.thickness
            PI / 64.0 * (D.pow(4) - (D - 2 * t).pow(4))
        }
        is SteelSectionType.LSection -> {
            // Approximate Iy for angle
            val a = section.legA
            val b = section.legB
            val t = section.thickness
            val A = (a + b - t) * t
            val IyApprox = (b * t.pow(3) / 12.0) + (t * (a - t) * (b / 2.0).pow(2)) +
                    ((a - t) * t.pow(3) / 12.0) + ((a - t) * t * (t / 2.0).pow(2))
            IyApprox.coerceAtLeast(0.0)
        }
        is SteelSectionType.TSection -> {
            val bf = section.flangeWidth
            val tf = section.flangeThickness
            val dw = section.webDepth
            val tw = section.webThickness
            val IyFlange = tf * bf.pow(3) / 12.0
            val IyWeb = dw * tw.pow(3) / 12.0
            IyFlange + IyWeb
        }
        is SteelSectionType.BuiltUp -> 0.0
        else -> 0.0
    }

    /** Weak axis elastic section modulus Sy — mm³ */
    private fun getSy(section: SteelSectionType): Double {
        val Iy = getIy(section)
        return when (section) {
            is SteelSectionType.ISection -> Iy / (section.bf / 2.0)
            is SteelSectionType.CSection -> Iy / (section.bf / 2.0)
            is SteelSectionType.RHS -> Iy / (section.width / 2.0)
            is SteelSectionType.CHS -> Iy / (section.outerDiameter / 2.0)
            is SteelSectionType.LSection -> Iy / (section.legB / 2.0)
            is SteelSectionType.TSection -> Iy / (section.flangeWidth / 2.0)
            is SteelSectionType.BuiltUp -> 0.0
            else -> 0.0
        }
    }

    /** Weak axis plastic section modulus Zy — mm³ (approximate) */
    private fun getZy(section: SteelSectionType): Double {
        val Sy = getSy(section)
        return when (section) {
            is SteelSectionType.ISection -> Sy * 1.12
            is SteelSectionType.CSection -> Sy * 1.10
            is SteelSectionType.RHS -> Sy * 1.08
            is SteelSectionType.CHS -> Sy * 1.12
            is SteelSectionType.LSection -> Sy * 1.05
            is SteelSectionType.TSection -> Sy * 1.05
            is SteelSectionType.BuiltUp -> 0.0
            else -> 0.0
        }
    }

    /** Weak axis radius of gyration ry — mm */
    private fun getRy(section: SteelSectionType): Double {
        val A = section.area
        return if (A > 0.0) sqrt(getIy(section) / A) else 0.0
    }

    /** St. Venant torsional constant J — mm⁴ */
    private fun getJ(section: SteelSectionType): Double = when (section) {
        is SteelSectionType.ISection -> {
            val b = section.bf
            val tf = section.tf
            val h = section.h
            val tw = section.tw
            val hw = (h - 2 * tf).coerceAtLeast(0.001)
            // J = (2*b*tf³ + hw*tw³) / 3 (simplified AISC)
            (2.0 * b * tf.pow(3) + hw * tw.pow(3)) / 3.0
        }
        is SteelSectionType.CSection -> {
            val b = section.bf
            val tf = section.tf
            val h = section.h
            val tw = section.tw
            val hw = (h - 2 * tf).coerceAtLeast(0.001)
            (2.0 * b * tf.pow(3) + hw * tw.pow(3)) / 3.0
        }
        is SteelSectionType.RHS -> {
            val w = section.width
            val h = section.height
            val t = section.thickness
            val hw = (h - 2 * t).coerceAtLeast(0.001)
            val bw = (w - 2 * t).coerceAtLeast(0.001)
            // J for hollow section = 2*t₁*t₂*(b-t₂)²*(h-t₁)² / (b*t₁ + h*t₂ - 2*t₁*t₂)
            2.0 * t * t * bw * bw * hw * hw / (w * t + h * t - 2.0 * t * t)
        }
        is SteelSectionType.CHS -> {
            val D = section.outerDiameter
            val t = section.thickness
            PI / 32.0 * (D.pow(4) - (D - 2 * t).pow(4))
        }
        is SteelSectionType.LSection -> {
            val a = section.legA
            val b = section.legB
            val t = section.thickness
            (a + b - t) * t.pow(3) / 3.0
        }
        is SteelSectionType.TSection -> {
            val bf = section.flangeWidth
            val tf = section.flangeThickness
            val dw = section.webDepth
            val tw = section.webThickness
            (bf * tf.pow(3) + dw * tw.pow(3)) / 3.0
        }
        is SteelSectionType.BuiltUp -> 0.0
        else -> 0.0
    }

    /** Warping constant Cw — mm⁶ */
    private fun getCw(section: SteelSectionType): Double = when (section) {
        is SteelSectionType.ISection -> {
            val b = section.bf
            val tf = section.tf
            val h = section.h
            val tw = section.tw
            val hw = (h - 2 * tf).coerceAtLeast(0.001)
            // Cw = Iy * h² / 4 (doubly symmetric I-shape, AISC)
            hw * hw * b * b * tf / 12.0
        }
        is SteelSectionType.CSection -> {
            val b = section.bf
            val tf = section.tf
            val h = section.h
            val tw = section.tw
            val hw = (h - 2 * tf).coerceAtLeast(0.001)
            // Approximate for channel
            hw * hw * b * b * tf / 24.0
        }
        is SteelSectionType.TSection -> {
            val bf = section.flangeWidth
            val tf = section.flangeThickness
            val dw = section.webDepth
            val tw = section.webThickness
            bf.pow(3) * tf * dw * dw / 12.0
        }
        is SteelSectionType.RHS -> {
            // Negligible warping for closed sections
            0.0
        }
        is SteelSectionType.CHS -> 0.0
        is SteelSectionType.LSection -> 0.0
        is SteelSectionType.BuiltUp -> 0.0
        else -> 0.0
    }

    /** Polar radius of gyration r₀ — mm (for torsional buckling) */
    private fun getRo(section: SteelSectionType): Double {
        val rx = section.rx
        val ry = getRy(section)
        return sqrt(rx * rx + ry * ry)
    }

    /** Sanitize polar radius of gyration r₀² — mm² (for torsional buckling, AISC E4) */
    private fun getRoSquared(section: SteelSectionType): Double {
        val rx = section.rx
        val ry = getRy(section)
        return rx * rx + ry * ry
    }

    // ===================== SECTION CLASSIFICATION (AISC Table B4.1a/b) =====================

    /**
     * تصنيف المقطع حسب AISC Table B4.1a (للانحناء) و B4.1b (للضغط)
     * Classify section as Compact, Noncompact, or Slender
     */
    fun classifySection(section: SteelSectionType, grade: SteelGrade): SectionClassification {
        val Fy = grade.fy
        val warnings = mutableListOf<String>()

        when (section) {
            is SteelSectionType.ISection -> {
                val bf = section.bf
                val tf = section.tf
                val h = section.h
                val tw = section.tw

                // Flange slenderness (AISC Table B4.1a, Case 1/10)
                val lambdaF = (bf - tw) / (2.0 * tf)
                val lambdaFp = 0.38 * sqrt(E_STEEL / Fy)
                val lambdaFr = 1.0 * sqrt(E_STEEL / Fy)

                // Web slenderness for flexure (AISC Table B4.1a, Case 15)
                val hw = (h - 2 * tf).coerceAtLeast(0.001)
                val lambdaW = hw / tw
                val lambdaWp = 3.76 * sqrt(E_STEEL / Fy)
                val lambdaWr = 5.70 * sqrt(E_STEEL / Fy)

                val flangeStatus = when {
                    lambdaF <= lambdaFp -> ElementClassification.COMPACT
                    lambdaF <= lambdaFr -> ElementClassification.NONCOMPACT
                    else -> ElementClassification.SLENDER
                }
                val webStatus = when {
                    lambdaW <= lambdaWp -> ElementClassification.COMPACT
                    lambdaW <= lambdaWr -> ElementClassification.NONCOMPACT
                    else -> ElementClassification.SLENDER
                }

                val overall = when {
                    flangeStatus == ElementClassification.SLENDER || webStatus == ElementClassification.SLENDER -> "Slender"
                    flangeStatus == ElementClassification.NONCOMPACT || webStatus == ElementClassification.NONCOMPACT -> "Noncompact"
                    else -> "Compact"
                }

                if (overall == "Slender") {
                    warnings.add("المقطع نحيف (Slender) — يجب تطبيق عوامل تخفيض Q")
                }

                return SectionClassification(
                    flangeStatus = flangeStatus,
                    webStatus = webStatus,
                    overall = overall,
                    flangeSlenderness = lambdaF,
                    webSlenderness = lambdaW,
                    flangeCompactLimit = lambdaFp,
                    flangeSlenderLimit = lambdaFr
                )
            }

            is SteelSectionType.CSection -> {
                val bf = section.bf
                val tf = section.tf
                val h = section.h
                val tw = section.tw

                val lambdaF = (bf - tw) / (2.0 * tf)
                val lambdaFp = 0.38 * sqrt(E_STEEL / Fy)
                val lambdaFr = 1.0 * sqrt(E_STEEL / Fy)

                val hw = (h - 2 * tf).coerceAtLeast(0.001)
                val lambdaW = hw / tw
                val lambdaWp = 3.76 * sqrt(E_STEEL / Fy)
                val lambdaWr = 5.70 * sqrt(E_STEEL / Fy)

                val flangeStatus = when {
                    lambdaF <= lambdaFp -> ElementClassification.COMPACT
                    lambdaF <= lambdaFr -> ElementClassification.NONCOMPACT
                    else -> ElementClassification.SLENDER
                }
                val webStatus = when {
                    lambdaW <= lambdaWp -> ElementClassification.COMPACT
                    lambdaW <= lambdaWr -> ElementClassification.NONCOMPACT
                    else -> ElementClassification.SLENDER
                }

                val overall = when {
                    flangeStatus == ElementClassification.SLENDER || webStatus == ElementClassification.SLENDER -> "Slender"
                    flangeStatus == ElementClassification.NONCOMPACT || webStatus == ElementClassification.NONCOMPACT -> "Noncompact"
                    else -> "Compact"
                }

                return SectionClassification(
                    flangeStatus = flangeStatus,
                    webStatus = webStatus,
                    overall = overall,
                    flangeSlenderness = lambdaF,
                    webSlenderness = lambdaW,
                    flangeCompactLimit = lambdaFp,
                    flangeSlenderLimit = lambdaFr
                )
            }

            is SteelSectionType.RHS -> {
                val w = section.width
                val h = section.height
                val t = section.thickness

                // AISC Table B4.1a, Case 12 (flanges of rectangular HSS)
                val lambdaF = (w - 2 * t) / t
                val lambdaFp = 1.12 * sqrt(E_STEEL / Fy)
                val lambdaFr = 1.40 * sqrt(E_STEEL / Fy)

                // AISC Table B4.1a, Case 13 (webs of rectangular HSS)
                val lambdaW = (h - 2 * t) / t
                val lambdaWp = 2.42 * sqrt(E_STEEL / Fy)
                val lambdaWr = 5.70 * sqrt(E_STEEL / Fy)

                val flangeStatus = when {
                    lambdaF <= lambdaFp -> ElementClassification.COMPACT
                    lambdaF <= lambdaFr -> ElementClassification.NONCOMPACT
                    else -> ElementClassification.SLENDER
                }
                val webStatus = when {
                    lambdaW <= lambdaWp -> ElementClassification.COMPACT
                    lambdaW <= lambdaWr -> ElementClassification.NONCOMPACT
                    else -> ElementClassification.SLENDER
                }

                val overall = when {
                    flangeStatus == ElementClassification.SLENDER || webStatus == ElementClassification.SLENDER -> "Slender"
                    flangeStatus == ElementClassification.NONCOMPACT || webStatus == ElementClassification.NONCOMPACT -> "Noncompact"
                    else -> "Compact"
                }

                return SectionClassification(
                    flangeStatus = flangeStatus,
                    webStatus = webStatus,
                    overall = overall,
                    flangeSlenderness = lambdaF,
                    webSlenderness = lambdaW,
                    flangeCompactLimit = lambdaFp,
                    flangeSlenderLimit = lambdaFr
                )
            }

            is SteelSectionType.CHS -> {
                val D = section.outerDiameter
                val t = section.thickness
                // AISC Table B4.1a, Case 9 (round HSS in flexure)
                val lambda = D / t
                val lambdaP = 0.07 * E_STEEL / Fy
                val lambdaR = 0.31 * E_STEEL / Fy

                val status = when {
                    lambda <= lambdaP -> ElementClassification.COMPACT
                    lambda <= lambdaR -> ElementClassification.NONCOMPACT
                    else -> ElementClassification.SLENDER
                }

                return SectionClassification(
                    flangeStatus = status,
                    webStatus = ElementClassification.COMPACT,
                    overall = when (status) {
                        ElementClassification.COMPACT -> "Compact"
                        ElementClassification.NONCOMPACT -> "Noncompact"
                        ElementClassification.SLENDER -> "Slender"
                    },
                    flangeSlenderness = lambda,
                    webSlenderness = 0.0,
                    flangeCompactLimit = lambdaP,
                    flangeSlenderLimit = lambdaR
                )
            }

            is SteelSectionType.LSection -> {
                val a = section.legA
                val b = section.legB
                val t = section.thickness
                // AISC Table B4.1a, Case 3 (legs of angles in flexure)
                val lambda = b / t
                val lambdaP = 0.54 * sqrt(E_STEEL / Fy)
                val lambdaR = 0.91 * sqrt(E_STEEL / Fy)

                val status = when {
                    lambda <= lambdaP -> ElementClassification.COMPACT
                    lambda <= lambdaR -> ElementClassification.NONCOMPACT
                    else -> ElementClassification.SLENDER
                }

                return SectionClassification(
                    flangeStatus = status,
                    webStatus = ElementClassification.COMPACT,
                    overall = when (status) {
                        ElementClassification.COMPACT -> "Compact"
                        ElementClassification.NONCOMPACT -> "Noncompact"
                        ElementClassification.SLENDER -> "Slender"
                    },
                    flangeSlenderness = lambda,
                    webSlenderness = a / t,
                    flangeCompactLimit = lambdaP,
                    flangeSlenderLimit = lambdaR
                )
            }

            is SteelSectionType.TSection -> {
                val bf = section.flangeWidth
                val tf = section.flangeThickness
                val dw = section.webDepth
                val tw = section.webThickness

                // Flange (same as I-shape)
                val lambdaF = (bf - tw) / (2.0 * tf)
                val lambdaFp = 0.38 * sqrt(E_STEEL / Fy)
                val lambdaFr = 1.0 * sqrt(E_STEEL / Fy)

                // Stem of T-section (AISC Table B4.1a, Case 15 with modified limits)
                val lambdaW = dw / tw
                val lambdaWp = 0.84 * sqrt(E_STEEL / Fy)
                val lambdaWr = 1.03 * sqrt(E_STEEL / Fy)

                val flangeStatus = when {
                    lambdaF <= lambdaFp -> ElementClassification.COMPACT
                    lambdaF <= lambdaFr -> ElementClassification.NONCOMPACT
                    else -> ElementClassification.SLENDER
                }
                val webStatus = when {
                    lambdaW <= lambdaWp -> ElementClassification.COMPACT
                    lambdaW <= lambdaWr -> ElementClassification.NONCOMPACT
                    else -> ElementClassification.SLENDER
                }

                val overall = when {
                    flangeStatus == ElementClassification.SLENDER || webStatus == ElementClassification.SLENDER -> "Slender"
                    flangeStatus == ElementClassification.NONCOMPACT || webStatus == ElementClassification.NONCOMPACT -> "Noncompact"
                    else -> "Compact"
                }

                return SectionClassification(
                    flangeStatus = flangeStatus,
                    webStatus = webStatus,
                    overall = overall,
                    flangeSlenderness = lambdaF,
                    webSlenderness = lambdaW,
                    flangeCompactLimit = lambdaFp,
                    flangeSlenderLimit = lambdaFr
                )
            }

            is SteelSectionType.BuiltUp -> {
                return SectionClassification(
                    flangeStatus = ElementClassification.NONCOMPACT,
                    webStatus = ElementClassification.NONCOMPACT,
                    overall = "Noncompact",
                    flangeSlenderness = 0.0,
                    webSlenderness = 0.0,
                    flangeCompactLimit = 0.0,
                    flangeSlenderLimit = 0.0
                )
            }
            else -> {
                return SectionClassification(
                    flangeStatus = ElementClassification.NONCOMPACT,
                    webStatus = ElementClassification.NONCOMPACT,
                    overall = "Noncompact",
                    flangeSlenderness = 0.0,
                    webSlenderness = 0.0,
                    flangeCompactLimit = 0.0,
                    flangeSlenderLimit = 0.0
                )
            }
        }
    }

    // ===================== TENSION MEMBER DESIGN (AISC Chapter D) =====================

    /**
     * تصميم عضو الشد حسب AISC Chapter D
     * @param Pu Factored tension force in kN
     * @param connectionType Type of connection (bolted, welded, etc.)
     */
    fun designTensionMember(
        section: SteelSectionType,
        grade: SteelGrade,
        Pu: Double,
        connectionType: ConnectionType
    ): SteelTensionResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val Ag = section.area  // mm²
        val Fy = grade.fy
        val Fu = grade.fu

        // --- D2: Tensile Strength ---
        // D2-1: Gross section yielding
        // φPn = φ × Fy × Ag
        val PnGross = Fy * Ag / 1000.0  // kN
        val phiPnGross = PHI_TENSION * PnGross

        // D2-2: Net section rupture
        // An = Ag - n_holes × (d_hole + 2mm) × t
        // Ae = An × U (effective net area)
        val (An, U, Ae) = calculateNetArea(section, connectionType, Fy)
        val PnNet = Fu * Ae / 1000.0  // kN
        val phiPnNet = PHI_TENSION * PnNet

        // --- J4.3: Block Shear Strength ---
        // Two cases: (a) yielding on shear + rupture on tension
        //            (b) rupture on shear + rupture on tension
        val phiRnBlockShear = calculateBlockShear(section, grade, connectionType, warnings)

        // Design capacity = minimum of all
        val designCapacity = minOf(phiPnGross, phiPnNet, phiRnBlockShear)
        val utilizationRatio = if (designCapacity > 0) Pu / designCapacity else Double.MAX_VALUE

        val governingCase = when {
            designCapacity == phiPnGross -> "خضوع المقطع الكلي (Gross Section Yielding — D2-1)"
            designCapacity == phiPnNet -> "تمزق المقطع الصافي (Net Section Rupture — D2-2)"
            else -> "قص الكتلة (Block Shear — J4.3)"
        }

        // Code notes
        codeNotes.add("AISC 360-16 Chapter D: تصميم عضو الشد")
        codeNotes.add("Fy = ${Fy.toInt()} MPa, Fu = ${Fu.toInt()} MPa")
        codeNotes.add("Ag = ${"%.0f".format(Ag)} mm²")
        codeNotes.add("φ×Pn (yielding) = ${"%.1f".format(phiPnGross)} kN — D2-1")
        codeNotes.add("φ×Pn (rupture) = ${"%.1f".format(phiPnNet)} kN — D2-2 (U=${"%.2f".format(U)})")
        codeNotes.add("φ×Rn (block shear) = ${"%.1f".format(phiRnBlockShear)} kN — J4.3")
        codeNotes.add("القدرة التصميمية = ${"%.1f".format(designCapacity)} kN")

        // Warnings
        if (utilizationRatio > 1.0) {
            warnings.add("المقطع غير كافٍ للشد — نسبة الإجهاد = ${"%.2f".format(utilizationRatio)} > 1.0")
        }
        if (U < 0.85) {
            warnings.add("معامل التأخير القصي U = ${"%.2f".format(U)} — يُنصح بزيادة طول الوصلة")
        }

        // Slenderness check (AISC D1)
        val minR = minOf(section.rx, getRy(section))
        if (minR > 0) {
            val Lmax = MAX_SLENDERNESS_TENSION * minR
            codeNotes.add("الحد الأقصى للطول غير المدعوم = ${"%.0f".format(Lmax)} mm (KL/r ≤ ${MAX_SLENDERNESS_TENSION.toInt()})")
        }

        return SteelTensionResult(
            grossYielding = phiPnGross,
            netRupture = phiPnNet,
            blockShear = phiRnBlockShear,
            designCapacity = designCapacity,
            utilizationRatio = utilizationRatio,
            isSafe = utilizationRatio <= 1.0,
            governingCase = governingCase,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    /** Calculate net section properties for tension (AISC D2-2, D3) */
    private fun calculateNetArea(
        section: SteelSectionType,
        connectionType: ConnectionType,
        Fy: Double
    ): Triple<Double, Double, Double> {
        val Ag = section.area
        val t = section.flangeThickness

        when (connectionType) {
            is ConnectionType.Bolted -> {
                val dHole = connectionType.boltDiameter + STANDARD_BOLT_HOLE_INCREMENT
                val nBolts = connectionType.numberOfBolts

                // Determine number of holes in the failure path
                val nHoles = when (connectionType.boltPattern) {
                    BoltPattern.SINGLE_ROW -> nBolts
                    BoltPattern.DOUBLE_ROW -> nBolts
                    BoltPattern.STAGGERED -> nBolts  // simplified: one line of holes
                    BoltPattern.GRID -> nBolts       // simplified
                }

                val An = (Ag - nHoles * dHole * t).coerceAtLeast(0.0)

                // Shear lag factor U (AISC Table D3.1)
                val U = calculateShearLagFactor(section, connectionType)
                val Ae = An * U

                return Triple(An, U, Ae)
            }
            is ConnectionType.Welded -> {
                // For welded connections with transverse welds: U = 1.0
                // For welded connections with longitudinal welds: per Table D3.1
                val U = 1.0
                val Ae = Ag * U
                return Triple(Ag, U, Ae)
            }
            is ConnectionType.Hybrid -> {
                // Use bolted portion for conservative estimate
                val dHole = connectionType.bolted.boltDiameter + STANDARD_BOLT_HOLE_INCREMENT
                val nHoles = connectionType.bolted.numberOfBolts
                val An = (Ag - nHoles * dHole * t).coerceAtLeast(0.0)
                val U = calculateShearLagFactor(section, connectionType.bolted)
                val Ae = An * U
                return Triple(An, U, Ae)
            }
            is ConnectionType.Pressed -> {
                // Pressed connection — no holes
                return Triple(Ag, 1.0, Ag)
            }
        }
    }

    /** Shear lag factor U per AISC Table D3.1 */
    private fun calculateShearLagFactor(
        section: SteelSectionType,
        connection: ConnectionType.Bolted
    ): Double {
        val L = when (connection.boltPattern) {
            BoltPattern.SINGLE_ROW, BoltPattern.DOUBLE_ROW -> {
                // Estimate connection length as n_bolts * 3*d (minimum spacing)
                connection.numberOfBolts * 3.0 * connection.boltDiameter
            }
            BoltPattern.STAGGERED -> {
                connection.numberOfBolts * 2.5 * connection.boltDiameter
            }
            BoltPattern.GRID -> {
                val rows = if (connection.boltPattern == BoltPattern.GRID) 2 else 1
                connection.numberOfBolts / rows * 3.0 * connection.boltDiameter
            }
        }

        return when (section) {
            is SteelSectionType.ISection -> {
                // Case 2 (connected by flanges with 3+ bolts, bf ≥ 2/3 d)
                // U = 1 - x̄/L, approximate x̄ for I-section flange connection
                val xBar = section.bf / 2.0
                val uCalc = 1.0 - xBar / L.coerceAtLeast(1.0)
                uCalc.coerceIn(0.6, 1.0)
            }
            is SteelSectionType.CSection -> {
                val xBar = section.bf / 2.0
                val uCalc = 1.0 - xBar / L.coerceAtLeast(1.0)
                uCalc.coerceIn(0.6, 1.0)
            }
            is SteelSectionType.LSection -> {
                // Case 4: Single angle with bolts in one leg
                // U = 0.60 for 4+ bolts, U = 0.80 for 2-3 bolts
                if (connection.numberOfBolts >= 4) 0.60 else 0.80
            }
            is SteelSectionType.RHS -> {
                val xBar = section.width / 2.0
                val uCalc = 1.0 - xBar / L.coerceAtLeast(1.0)
                uCalc.coerceIn(0.6, 1.0)
            }
            is SteelSectionType.CHS -> 0.90
            is SteelSectionType.TSection -> {
                val xBar = section.flangeWidth / 2.0
                val uCalc = 1.0 - xBar / L.coerceAtLeast(1.0)
                uCalc.coerceIn(0.5, 1.0)
            }
            is SteelSectionType.BuiltUp -> 0.80
            else -> 0.80
        }
    }

    /** Block shear strength per AISC J4.3 — returns φRn in kN */
    private fun calculateBlockShear(
        section: SteelSectionType,
        grade: SteelGrade,
        connectionType: ConnectionType,
        warnings: MutableList<String>
    ): Double {
        val Fy = grade.fy
        val Fu = grade.fu
        val t = section.flangeThickness

        when (connectionType) {
            is ConnectionType.Bolted -> {
                val dHole = connectionType.boltDiameter + STANDARD_BOLT_HOLE_INCREMENT
                val nBolts = connectionType.numberOfBolts
                val db = connectionType.boltDiameter

                // Estimate connection geometry
                val gauge = section.width.coerceAtLeast(db * 3.0)  // mm
                val edgeDist = (2.0 * db).coerceAtLeast(30.0)     // mm
                val pitch = (3.0 * db).coerceAtLeast(65.0)        // mm

                // Tension area (Ant): one line through bolt holes at edge
                val Ant = ((gauge - dHole) * t).coerceAtLeast(0.0)

                // Gross shear area (Agv): along two lines of bolts
                val lv = edgeDist + (nBolts - 1) * pitch
                val Agv = 2.0 * lv * t

                // Net shear area (Anv): shear area minus bolt holes
                val Anv = (Agv - (nBolts - 1) * dHole * t).coerceAtLeast(Agv * 0.5)

                // Ubs = 1.0 for uniform tension (AISC J4.3)
                val Ubs = 1.0

                // Case (a): Shear yielding + tension rupture
                val Rn_a = 0.6 * Fy * Agv + Ubs * Fu * Ant

                // Case (b): Shear rupture + tension rupture
                val Rn_b = 0.6 * Fu * Anv + Ubs * Fu * Ant

                val Rn = minOf(Rn_a, Rn_b)
                return PHI_TENSION * Rn / 1000.0  // kN
            }
            is ConnectionType.Welded -> {
                // For welded connections, block shear is not typically governing
                // Return a high value (not applicable)
                val Ag = section.area
                return PHI_TENSION * 0.6 * grade.fy * Ag / 1000.0
            }
            is ConnectionType.Hybrid -> {
                return calculateBlockShear(section, grade, connectionType.bolted, warnings)
            }
            is ConnectionType.Pressed -> {
                val Ag = section.area
                return PHI_TENSION * 0.6 * grade.fy * Ag / 1000.0
            }
        }
    }

    // ===================== COMPRESSION MEMBER DESIGN (AISC Chapter E) =====================

    /**
     * تصميم عضو الضغط حسب AISC Chapter E
     * @param Pu Factored compression force in kN
     * @param Kx, Ky Effective length factors
     * @param Lx, Ly Unbraced lengths in mm
     */
    fun designCompressionMember(
        section: SteelSectionType,
        grade: SteelGrade,
        Pu: Double,
        Kx: Double,
        Ky: Double,
        Lx: Double,
        Ly: Double,
        @Suppress("UNUSED_PARAMETER") isBraced: Boolean = true
    ): SteelCompressionResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val Ag = section.area  // mm²
        val Fy = grade.fy
        val rx = section.rx
        val ry = getRy(section)

        // E2: Effective length and slenderness
        val lambdaX = (Kx * Lx) / rx.coerceAtLeast(0.1)
        val lambdaY = (Ky * Ly) / ry.coerceAtLeast(0.1)
        val lambdaMax = maxOf(lambdaX, lambdaY)

        codeNotes.add("AISC 360-16 Chapter E: تصميم عضو الضغط")
        codeNotes.add("Fy = ${Fy.toInt()} MPa, Ag = ${"%.0f".format(Ag)} mm²")
        codeNotes.add("λx = ${"%.1f".format(lambdaX)}, λy = ${"%.1f".format(lambdaY)}")
        codeNotes.add("نسبة النحافة الحاكمة λ = ${"%.1f".format(lambdaMax)}")

        // Slenderness check (AISC D1)
        if (lambdaMax > MAX_SLENDERNESS_COMPRESSION) {
            warnings.add("نسبة النحافة تتجاوز الحد المسموح (λ=${"%.1f".format(lambdaMax)} > ${MAX_SLENDERNESS_COMPRESSION.toInt()})")
        }

        // E3: Flexural buckling
        val Fe = PI * PI * E_STEEL / (lambdaMax * lambdaMax)  // MPa
        val lambdaC = 4.71 * sqrt(E_STEEL / Fy)

        val FcrFlexural = if (lambdaMax <= lambdaC) {
            // Inelastic buckling — AISC E3-3
            0.658.pow(Fy / Fe) * Fy
        } else {
            // Elastic buckling — AISC E3-4
            0.877 * Fe
        }

        // E4: Torsional buckling (for doubly symmetric sections)
        val FcrTorsional = calculateTorsionalBucklingStress(section, grade, Kx, Lx, Ky, Ly, lambdaMax)

        // E7: Local buckling reduction factors
        val (Qs, Qa) = calculateLocalBucklingFactors(section, grade)

        // Governing critical stress
        val Fcr = minOf(FcrFlexural, FcrTorsional)

        // Apply local buckling factor
        val FcrAdjusted = Qs * Fcr

        // Nominal strength
        val Pn = FcrAdjusted * Ag / 1000.0  // kN
        val phiPn = PHI_COMPRESSION * Pn

        val utilizationRatio = if (phiPn > 0) Pu / phiPn else Double.MAX_VALUE

        // Determine buckling mode
        val bucklingMode = when {
            FcrTorsional < FcrFlexural -> "انبعاج الالتواء (Torsional Buckling — E4)"
            lambdaX > lambdaY -> "انبعاج مرن حول المحور الضعيف (Flexural Buckling — E3, X-axis)"
            else -> "انبعاج مرن حول المحور الضعيف (Flexural Buckling — E3, Y-axis)"
        }

        codeNotes.add("Fe = ${"%.1f".format(Fe)} MPa (Euler)")
        codeNotes.add("Fcr = ${"%.1f".format(Fcr)} MPa")
        codeNotes.add("Qs = ${"%.3f".format(Qs)}, Qa = ${"%.3f".format(Qa)}")
        codeNotes.add("Fcr (مُعدّل) = ${"%.1f".format(FcrAdjusted)} MPa")
        codeNotes.add("φPn = ${"%.1f".format(phiPn)} kN")
        codeNotes.add("نسبة الإجهاد = ${"%.3f".format(utilizationRatio)}")

        if (utilizationRatio > 1.0) {
            warnings.add("المقطع غير كافٍ للضغط — اختر مقطعاً أكبر")
        }
        if (FcrTorsional < 0.95 * FcrFlexural) {
            warnings.add("الانبعاج الالتوائي حاكم — يُنصح باستخدام مقاطع مغلقة أو زيادة الدعم الجانبي")
        }
        if (Qs < 0.99) {
            warnings.add("عوامل تخفيض الانبعاج المحلي فعّالة (Qs=${"%.2f".format(Qs)})")
        }

        return SteelCompressionResult(
            Fcr = FcrAdjusted,
            Pn = Pn,
            phiPn = phiPn,
            slendernessRatio = lambdaMax,
            bucklingMode = bucklingMode,
            utilizationRatio = utilizationRatio,
            isSafe = utilizationRatio <= 1.0,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    /** Torsional buckling stress (AISC E4) */
    private fun calculateTorsionalBucklingStress(
        section: SteelSectionType,
        grade: SteelGrade,
        Kx: Double, Lx: Double,
        Ky: Double, Ly: Double,
        lambdaFlexural: Double
    ): Double {
        val Fy = grade.fy
        val Ag = section.area

        // For closed sections (HSS), torsional buckling is not critical
        when (section) {
            is SteelSectionType.RHS -> return Fy * 2.0  // Not governing
            is SteelSectionType.CHS -> return Fy * 2.0  // Not governing
            else -> {}
        }

        // For open sections, calculate Fe for torsional buckling
        val J = getJ(section)
        val Cw = getCw(section)
        val ro2 = getRoSquared(section)
        val L = minOf(Kx * Lx, Ky * Ly).coerceAtLeast(1.0)

        // AISC E4-3: Fe = [π²×E×Cw/(KL)² + G×J] / (Ar²)
        val term1 = PI * PI * E_STEEL * Cw / (L * L)
        val term2 = G_STEEL * J
        val Fe = (term1 + term2) / (Ag * ro2).coerceAtLeast(1.0)

        // Apply AISC E3-3/E3-4 to get Fcr from Fe
        val lambdaC = 4.71 * sqrt(E_STEEL / Fy)
        return if (lambdaFlexural <= lambdaC) {
            minOf(0.658.pow(Fy / Fe) * Fy, Fy)
        } else {
            minOf(0.877 * Fe, Fy)
        }
    }

    /** Local buckling reduction factors Qs and Qa (AISC E7) */
    private fun calculateLocalBucklingFactors(
        section: SteelSectionType,
        grade: SteelGrade
    ): Pair<Double, Double> {
        val Fy = grade.fy

        val classification = classifySection(section, grade)

        // If section is compact or noncompact, Qs = Qa = 1.0
        if (classification.flangeStatus != ElementClassification.SLENDER &&
            classification.webStatus != ElementClassification.SLENDER
        ) {
            return Pair(1.0, 1.0)
        }

        // E7.1: Slender element reduction factor Qs
        var Qs = 1.0
        var Qa = 1.0

        when (section) {
            is SteelSectionType.ISection -> {
                val bf = section.bf
                val tf = section.tf
                val tw = section.tw
                val h = section.h
                val hw = (h - 2 * tf).coerceAtLeast(0.001)

                // E7.1a: Stiffened elements (webs)
                val lambdaW = hw / tw
                val lambdaR = 1.49 * sqrt(E_STEEL / Fy)  // AISC Table B4.1b, Case 15
                if (lambdaW > lambdaR) {
                    Qa = (1.0 - 0.15 * lambdaR / lambdaW)  // AISC E7-7 approximation
                }

                // E7.1b: Unstiffened elements (flanges)
                val lambdaF = (bf - tw) / (2.0 * tf)
                val lambdaRF = 0.56 * sqrt(E_STEEL / Fy)  // AISC Table B4.1b, Case 10
                if (lambdaF > lambdaRF) {
                    // AISC E7-6: Qs reduction for slender flanges
                    Qs = (lambdaRF / lambdaF).pow(2)
                }

                Qs = Qs.coerceIn(0.0, 1.0)
                Qa = Qa.coerceIn(0.0, 1.0)
            }
            is SteelSectionType.RHS -> {
                val w = section.width
                val h = section.height
                val t = section.thickness

                // Flanges
                val lambdaF = (w - 2 * t) / t
                val lambdaRF = 1.12 * sqrt(E_STEEL / Fy)  // B4.1b Case 12
                if (lambdaF > lambdaRF) {
                    Qs = (lambdaRF / lambdaF).pow(2)
                }

                // Webs
                val lambdaW = (h - 2 * t) / t
                val lambdaRW = 1.40 * sqrt(E_STEEL / Fy)  // B4.1b Case 13
                if (lambdaW > lambdaRW) {
                    Qa = (1.0 - 0.15 * lambdaRW / lambdaW)
                }

                Qs = Qs.coerceIn(0.0, 1.0)
                Qa = Qa.coerceIn(0.0, 1.0)
            }
            is SteelSectionType.CHS -> {
                val D = section.outerDiameter
                val t = section.thickness
                val lambda = D / t
                val lambdaR = 0.11 * E_STEEL / Fy  // B4.1b Case 9
                if (lambda > lambdaR) {
                    Qs = (lambdaR / lambda).pow(2)
                }
                Qs = Qs.coerceIn(0.0, 1.0)
            }
            else -> {
                // For angles, channels, T-sections — conservative Qs = 1.0 if not specifically calculated
                if (classification.flangeStatus == ElementClassification.SLENDER) {
                    Qs = 0.9
                }
            }
        }

        return Pair(Qs, Qa)
    }

    // ===================== FLEXURAL MEMBER DESIGN (AISC Chapter F) =====================

    /**
     * تصميم كمرة شامل حسب AISC Chapter F
     * @param Mux Factored moment about strong axis (kN.m)
     * @param Muy Factored moment about weak axis (kN.m)
     * @param Vux Factored shear about strong axis (kN)
     * @param Vuy Factored shear about weak axis (kN)
     * @param Lb Unbraced length for LTB (mm)
     * @param Cb Moment gradient factor (default 1.0 for uniform moment)
     */
    fun designFlexuralMember(
        section: SteelSectionType,
        grade: SteelGrade,
        Mux: Double,
        Muy: Double,
        Vux: Double,
        Vuy: Double,
        Lb: Double,
        Cb: Double = 1.0,
        isLaterallyBraced: Boolean = false
    ): SteelFlexuralResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val Fy = grade.fy

        val classification = classifySection(section, grade)
        codeNotes.add("AISC 360-16 Chapter F: تصميم الكمرة")
        codeNotes.add("Fy = ${Fy.toInt()} MPa, تصنيف المقطع: ${classification.overall}")
        codeNotes.add("Ag = ${"%.0f".format(section.area)} mm², Zx = ${"%.0f".format(section.zx)} mm³")

        // ---- Strong axis flexure (F2, F3, F4, F6, F7) ----
        val Mnx = calculateNominalMomentX(section, grade, Lb, Cb, isLaterallyBraced, classification, warnings, codeNotes)
        val phiMnx = PHI_FLEXURE * Mnx

        // ---- Weak axis flexure (F3, F6, F7) ----
        val Mny = calculateNominalMomentY(section, grade, classification, warnings, codeNotes)
        val phiMny = PHI_FLEXURE * Mny

        // ---- Shear (Chapter G) ----
        val phiVnx = calculateShearCapacityX(section, grade, warnings, codeNotes)
        val phiVny = calculateShearCapacityY(section, grade, warnings, codeNotes)

        // ---- LTB capacity (reported separately) ----
        val ltbCapacity = if (Lb > 0 && !isLaterallyBraced) {
            calculateLTBCapacity(section, grade, Lb, Cb, classification, codeNotes)
        } else {
            Mnx  // No LTB concern
        }

        // ---- Deflection check (serviceability) ----
        // Use Lb as span if provided, otherwise use a reference span
        val span = if (Lb > 0) Lb else 6000.0  // mm
        val Ix = section.ix  // mm⁴
        val deltaMax = span / 360.0  // L/360 for floors
        // Assume a reference distributed load to give Mux: w*L²/8 = Mux → w = 8*Mux/(L²)
        val wRef = 8.0 * Mux / ((span / 1000.0).pow(2))  // kN/m
        val Lm = span / 1000.0
        val Im4 = Ix / 1e12  // m⁴
        val delta = if (Im4 > 0) {
            5.0 * wRef * Lm.pow(4) / (384.0 * (E_STEEL / 1e6) * Im4) * 1000.0  // mm
        } else 0.0
        val deflectionRatio = if (deltaMax > 0) delta / deltaMax else 0.0

        // ---- Determine governing check ----
        val ratioMx = if (phiMnx > 0) Mux / phiMnx else if (Mux > 0) Double.MAX_VALUE else 0.0
        val ratioMy = if (phiMny > 0) Muy / phiMny else if (Muy > 0) Double.MAX_VALUE else 0.0
        val ratioVx = if (phiVnx > 0) Vux / phiVnx else if (Vux > 0) Double.MAX_VALUE else 0.0
        val ratioVy = if (phiVny > 0) Vuy / phiVny else if (Vuy > 0) Double.MAX_VALUE else 0.0

        val governingCheck = when {
            ratioMx >= ratioMy && ratioMx >= ratioVx && ratioMx >= ratioVy -> "انحناء حول المحور القوي (Strong Axis Bending)"
            ratioMy >= ratioVx && ratioMy >= ratioVy -> "انحناء حول المحور الضعيف (Weak Axis Bending)"
            ratioVx >= ratioVy -> "قص حول المحور القوي (Strong Axis Shear)"
            else -> "قص حول المحور الضعيف (Weak Axis Shear)"
        }

        val maxRatio = maxOf(ratioMx, ratioMy, ratioVx, ratioVy)
        val isSafe = maxRatio <= 1.0

        codeNotes.add("φMnx = ${"%.1f".format(phiMnx)} kN.m (η = ${"%.3f".format(ratioMx)})")
        codeNotes.add("φMny = ${"%.1f".format(phiMny)} kN.m (η = ${"%.3f".format(ratioMy)})")
        codeNotes.add("φVnx = ${"%.1f".format(phiVnx)} kN (η = ${"%.3f".format(ratioVx)})")
        codeNotes.add("φVny = ${"%.1f".format(phiVny)} kN (η = ${"%.3f".format(ratioVy)})")
        if (deflectionRatio > 0) {
            codeNotes.add("الانحراف = ${"%.1f".format(delta)} mm (الحد = ${"%.1f".format(deltaMax)} mm, η = ${"%.3f".format(deflectionRatio)})")
        }

        if (maxRatio > 1.0) {
            warnings.add("المقطع غير كافٍ — نسبة الإجهاد القصوى = ${"%.3f".format(maxRatio)} > 1.0")
        }
        if (deflectionRatio > 1.0) {
            warnings.add("الانحراف يتجاوز الحد المسموح (L/${(360.0 / deltaMax * span).toInt()})")
        }

        return SteelFlexuralResult(
            momentCapacityX = phiMnx,
            momentCapacityY = phiMny,
            shearCapacityX = phiVnx,
            shearCapacityY = phiVny,
            ltbCapacity = PHI_FLEXURE * ltbCapacity,
            deflectionRatio = deflectionRatio,
            isSafe = isSafe,
            governingCheck = governingCheck,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    /** Calculate nominal moment about strong axis (AISC F2, F3, F4, F6, F7) */
    private fun calculateNominalMomentX(
        section: SteelSectionType,
        grade: SteelGrade,
        Lb: Double,
        Cb: Double,
        isLaterallyBraced: Boolean,
        classification: SectionClassification,
        warnings: MutableList<String>,
        codeNotes: MutableList<String>
    ): Double {
        val Fy = grade.fy

        return when (section) {
            is SteelSectionType.ISection -> {
                calculateMomentISection(section, Fy, Lb, Cb, isLaterallyBraced, classification, codeNotes)
            }
            is SteelSectionType.CSection -> {
                calculateMomentCSection(section, Fy, Lb, Cb, isLaterallyBraced, classification, codeNotes)
            }
            is SteelSectionType.RHS -> {
                // AISC F6: Rectangular HSS
                calculateMomentRHS(section, Fy, classification)
            }
            is SteelSectionType.CHS -> {
                // AISC F7: Round HSS
                calculateMomentCHS(section, Fy, classification)
            }
            is SteelSectionType.LSection -> {
                // AISC F8: Single angles
                val Zx = section.zx
                val Sx = section.sx
                val Mp = Fy * Zx / 1e6  // kN.m
                if (classification.flangeStatus == ElementClassification.SLENDER) {
                    Mp * 0.85  // Reduced for slender legs
                } else {
                    Mp
                }
            }
            is SteelSectionType.TSection -> {
                // AISC F4: Singly symmetric I-shapes (T-sections)
                calculateMomentTSection(section, Fy, Lb, Cb, isLaterallyBraced, classification, codeNotes)
            }
            is SteelSectionType.BuiltUp -> {
                section.zx * Fy / 1e6  // Simplified
            }
            else -> 0.0
        }
    }

    /** AISC F2: Doubly symmetric I-shapes, strong axis */
    private fun calculateMomentISection(
        section: SteelSectionType.ISection,
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

        // Plastic moment
        val Mp = Fy * Zx / 1e6  // kN.m

        // LTB parameters (AISC F2)
        val Lp = 1.76 * ry * sqrt(E_STEEL / Fy)  // mm, F2-5

        // rt per AISC F2-6
        val rt = sqrt(sqrt(Iy * Cw) / Sx.coerceAtLeast(1.0))  // mm
        val Lr = 1.95 * rt * sqrt(E_STEEL / (0.7 * Fy))  // mm, F2-6

        codeNotes.add("Lp = ${"%.0f".format(Lp)} mm, Lr = ${"%.0f".format(Lr)} mm, rt = ${"%.1f".format(rt)} mm")
        codeNotes.add("Lb = ${"%.0f".format(Lb)} mm, Cb = ${"%.2f".format(Cb)}")

        val Mn = if (isLaterallyBraced || Lb <= Lp) {
            // Fully braced or within plastic range: Mn = Mp
            codeNotes.add("Lb ≤ Lp → Mn = Mp (قدرة بلاستيكية كاملة)")
            Mp
        } else if (Lb <= Lr) {
            // Inelastic LTB — AISC F2-2
            val MnLr = 0.7 * Fy * Sx / 1e6  // kN.m
            val MnLtb = Cb * Mp - (Cb * Mp - MnLr) * ((Lb - Lp) / (Lr - Lp).coerceAtLeast(1.0))
            codeNotes.add("Lp < Lb ≤ Lr → انبعاج لدن (Inelastic LTB) — F2-2")
            MnLtb.coerceAtMost(Mp)  // Cb * Mn ≤ Mp
        } else {
            // Elastic LTB — AISC F2-3, F2-4
            val FcrLtb = Cb * PI * PI * E_STEEL / ((Lb / ry.coerceAtLeast(1.0)).pow(2))  // MPa
            val MnLtb = FcrLtb * Sx / 1e6  // kN.m
            codeNotes.add("Lb > Lr → انبعاج مرن (Elastic LTB) — F2-4, Fcr = ${"%.1f".format(FcrLtb)} MPa")
            MnLtb.coerceAtMost(Mp)
        }

        // Local buckling reduction for noncompact/slender flanges
        return when (classification.flangeStatus) {
            ElementClassification.COMPACT -> Mn
            ElementClassification.NONCOMPACT -> {
                // AISC F3-2: Mn reduced linearly
                val lambdaF = classification.flangeSlenderness
                val lambdaP = classification.flangeCompactLimit
                val lambdaR = classification.flangeSlenderLimit
                val MnR = minOf(Mn, 0.7 * Fy * Sx / 1e6)
                val MnReduced = Mn - (Mn - MnR) * ((lambdaF - lambdaP) / (lambdaR - lambdaP).coerceAtLeast(0.001))
                MnReduced.coerceAtLeast(0.0)
            }
            ElementClassification.SLENDER -> {
                // AISC F3-2 for slender flanges
                val lambdaF = classification.flangeSlenderness
                val lambdaR = classification.flangeSlenderLimit
                val MnR = 0.7 * Fy * Sx / 1e6
                val FcrFlange = 0.9 * E_STEEL / lambdaF.pow(2)  // AISC F3-3
                val MnSlender = FcrFlange * Sx / 1e6
                minOf(MnSlender, Mn)
            }
        }
    }

    /** AISC F2 (modified): Channels, strong axis */
    private fun calculateMomentCSection(
        section: SteelSectionType.CSection,
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

        val Mp = Fy * Zx / 1e6
        val Lp = 1.76 * ry * sqrt(E_STEEL / Fy)

        codeNotes.add("Lp = ${"%.0f".format(Lp)} mm, Lb = ${"%.0f".format(Lb)} mm")

        val Mn = if (isLaterallyBraced || Lb <= Lp) {
            Mp
        } else {
            // Elastic LTB for channels (conservative)
            val FcrLtb = Cb * PI * PI * E_STEEL / ((Lb / ry.coerceAtLeast(1.0)).pow(2))
            val MnLtb = minOf(FcrLtb * Sx / 1e6, Mp)
            codeNotes.add("انبعاج مرن للقناة (Elastic LTB) — Fcr = ${"%.1f".format(FcrLtb)} MPa")
            MnLtb
        }

        return when (classification.flangeStatus) {
            ElementClassification.COMPACT -> Mn
            ElementClassification.NONCOMPACT -> {
                val lambdaP = classification.flangeCompactLimit
                val lambdaR = classification.flangeSlenderLimit
                val MnR = minOf(Mn, 0.7 * Fy * Sx / 1e6)
                val reduction = (classification.flangeSlenderness - lambdaP) / (lambdaR - lambdaP).coerceAtLeast(0.001)
                Mn - (Mn - MnR) * reduction
            }
            ElementClassification.SLENDER -> {
                val FcrFlange = 0.9 * E_STEEL / classification.flangeSlenderness.pow(2)
                minOf(FcrFlange * Sx / 1e6, Mn)
            }
        }
    }

    /** AISC F6: Rectangular HSS */
    private fun calculateMomentRHS(
        section: SteelSectionType.RHS,
        Fy: Double,
        classification: SectionClassification
    ): Double {
        val Zx = section.zx
        val Sx = section.sx
        val Mp = Fy * Zx / 1e6

        // No LTB for closed sections
        return when (classification.flangeStatus) {
            ElementClassification.COMPACT -> Mp
            ElementClassification.NONCOMPACT -> {
                val MnR = 0.7 * Fy * Sx / 1e6
                val lambdaP = classification.flangeCompactLimit
                val lambdaR = classification.flangeSlenderLimit
                val reduction = (classification.flangeSlenderness - lambdaP) / (lambdaR - lambdaP).coerceAtLeast(0.001)
                MnR + (Mp - MnR) * (1.0 - reduction)
            }
            ElementClassification.SLENDER -> {
                val Fcr = 0.9 * E_STEEL / classification.flangeSlenderness.pow(2)
                Fcr * Sx / 1e6
            }
        }
    }

    /** AISC F7: Round HSS */
    private fun calculateMomentCHS(
        section: SteelSectionType.CHS,
        Fy: Double,
        classification: SectionClassification
    ): Double {
        val Zx = section.zx
        val Sx = section.sx
        val Mp = Fy * Zx / 1e6

        return when (classification.flangeStatus) {
            ElementClassification.COMPACT -> Mp
            ElementClassification.NONCOMPACT -> {
                val MnR = 0.7 * Fy * Sx / 1e6
                val lambdaP = classification.flangeCompactLimit
                val lambdaR = classification.flangeSlenderLimit
                val reduction = (classification.flangeSlenderness - lambdaP) / (lambdaR - lambdaP).coerceAtLeast(0.001)
                MnR + (Mp - MnR) * (1.0 - reduction)
            }
            ElementClassification.SLENDER -> {
                val D = section.outerDiameter
                val t = section.thickness
                val Fcr = 0.38 * E_STEEL / (D / t)
                Fcr * Sx / 1e6
            }
        }
    }

    /** AISC F4: T-sections (singly symmetric) */
    private fun calculateMomentTSection(
        section: SteelSectionType.TSection,
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

        val Mp = Fy * Zx / 1e6

        if (isLaterallyBraced) return Mp

        // Simplified LTB for T-sections
        val Lp = 1.76 * ry * sqrt(E_STEEL / Fy)
        if (Lb <= Lp) {
            codeNotes.add("T-section: Lb ≤ Lp → Mn = Mp")
            return Mp
        }

        // Elastic LTB (conservative for T-sections)
        val FcrLtb = Cb * PI * PI * E_STEEL / ((Lb / ry.coerceAtLeast(1.0)).pow(2))
        val MnLtb = minOf(FcrLtb * Sx / 1e6, Mp)
        codeNotes.add("T-section: انبعاج مرن — Fcr = ${"%.1f".format(FcrLtb)} MPa")
        return MnLtb
    }

    /** Calculate LTB capacity (separate, for reporting) */
    private fun calculateLTBCapacity(
        section: SteelSectionType,
        grade: SteelGrade,
        Lb: Double,
        Cb: Double,
        classification: SectionClassification,
        codeNotes: MutableList<String>
    ): Double {
        return calculateNominalMomentX(
            section, grade, Lb, Cb, false, classification,
            mutableListOf(), codeNotes
        )
    }

    /** Calculate nominal moment about weak axis (AISC F3, F6, F7) */
    private fun calculateNominalMomentY(
        section: SteelSectionType,
        grade: SteelGrade,
        classification: SectionClassification,
        warnings: MutableList<String>,
        codeNotes: MutableList<String>
    ): Double {
        val Fy = grade.fy
        val Zy = getZy(section)
        val Sy = getSy(section)
        val Mp = Fy * Zy / 1e6  // kN.m

        // No LTB about weak axis for I-shapes
        return when (classification.webStatus) {
            ElementClassification.COMPACT -> Mp
            ElementClassification.NONCOMPACT -> {
                val MnR = 0.7 * Fy * Sy / 1e6
                val lambdaW = classification.webSlenderness
                // For weak axis, use web slenderness as a proxy
                val Mn = Mp - (Mp - MnR) * 0.3  // Simplified reduction
                Mn
            }
            ElementClassification.SLENDER -> {
                val Fcr = 0.9 * E_STEEL / classification.webSlenderness.pow(2).coerceAtLeast(1.0)
                Fcr * Sy / 1e6
            }
        }
    }

    /** Shear capacity (AISC Chapter G) — strong axis (web shear) */
    private fun calculateShearCapacityX(
        section: SteelSectionType,
        grade: SteelGrade,
        warnings: MutableList<String>,
        codeNotes: MutableList<String>
    ): Double {
        val Fy = grade.fy

        return when (section) {
            is SteelSectionType.ISection -> {
                calculateWebShear(section.h, section.tw, section.tf, Fy, codeNotes)
            }
            is SteelSectionType.CSection -> {
                calculateWebShear(section.h, section.tw, section.tf, Fy, codeNotes)
            }
            is SteelSectionType.TSection -> {
                // Shear through the stem
                val Aw = section.webDepth * section.webThickness
                0.6 * Fy * Aw / 1000.0 * PHI_SHEAR
            }
            is SteelSectionType.RHS -> {
                // AISC G5: HSS shear — Vn = 0.6*Fy*2*D*t (two webs)
                val h = section.height
                val w = section.width
                val t = section.thickness
                val Aw = 2.0 * (h - 2.0 * t) * t  // Two side walls
                val Cv = 1.0  // HSS typically compact in shear
                PHI_SHEAR * 0.6 * Fy * Cv * Aw / 1000.0
            }
            is SteelSectionType.CHS -> {
                // AISC G5: Round HSS
                val D = section.outerDiameter
                val t = section.thickness
                val Aw = PI * (D - t) * t / 2.0
                val Fcr = when {
                    D / t <= 1.15 * sqrt(E_STEEL / Fy) -> {
                        0.6 * Fy  // Full capacity
                    }
                    else -> {
                        0.6 * Fy * (1.15 * sqrt(E_STEEL / Fy) / (D / t))
                    }
                }
                PHI_SHEAR * Fcr * Aw / 1000.0
            }
            is SteelSectionType.LSection -> {
                // Single angle — shear through the leg
                val Aw = section.legA * section.thickness
                PHI_SHEAR * 0.6 * Fy * Aw / 1000.0
            }
            is SteelSectionType.BuiltUp -> 0.0
            else -> 0.0
        }
    }

    /** AISC G2: Shear capacity for I-shapes with unstiffened webs */
    private fun calculateWebShear(
        h: Double,
        tw: Double,
        tf: Double,
        Fy: Double,
        codeNotes: MutableList<String>
    ): Double {
        val hw = (h - 2.0 * tf).coerceAtLeast(0.001)
        val Aw = hw * tw  // mm²
        val hOverTw = hw / tw

        // AISC G2-1: Vn = 0.6 * Fy * Aw * Cv
        // Cv per AISC G2-1(a,b,c)
        val k = 5.0  // unstiffened web (AISC G2.1b)
        val Cv = when {
            hOverTw <= 1.10 * sqrt(k * E_STEEL / Fy) -> {
                // Case (a): Full web yield
                1.0
            }
            hOverTw <= 1.37 * sqrt(k * E_STEEL / Fy) -> {
                // Case (b): Inelastic web buckling
                1.10 * sqrt(k * E_STEEL / Fy) / hOverTw
            }
            else -> {
                // Case (c): Elastic web buckling
                1.51 * k * E_STEEL / (hOverTw * hOverTw * Fy)
            }
        }

        val Vn = 0.6 * Fy * Aw * Cv / 1000.0  // kN
        val phiVn = PHI_SHEAR * Vn

        codeNotes.add("القص: h/tw = ${"%.1f".format(hOverTw)}, Cv = ${"%.3f".format(Cv)}, Aw = ${"%.0f".format(Aw)} mm²")
        codeNotes.add("φVn = ${"%.1f".format(phiVn)} kN")

        return phiVn
    }

    /** Shear capacity — weak axis (flange shear for I-shapes) */
    private fun calculateShearCapacityY(
        section: SteelSectionType,
        grade: SteelGrade,
        warnings: MutableList<String>,
        codeNotes: MutableList<String>
    ): Double {
        val Fy = grade.fy
        val t = section.flangeThickness

        return when (section) {
            is SteelSectionType.ISection -> {
                // Shear through flanges: 2 * bf * tf
                val Aw = 2.0 * section.bf * t
                PHI_SHEAR * 0.6 * Fy * Aw / 1000.0
            }
            is SteelSectionType.CSection -> {
                val Aw = 2.0 * section.bf * t
                PHI_SHEAR * 0.6 * Fy * Aw / 1000.0
            }
            is SteelSectionType.RHS -> {
                val Aw = 2.0 * (section.width - 2.0 * t) * t
                PHI_SHEAR * 0.6 * Fy * Aw / 1000.0
            }
            is SteelSectionType.CHS -> {
                // Same as strong axis for round HSS
                val D = section.outerDiameter
                val Aw = PI * (D - t) * t / 2.0
                PHI_SHEAR * 0.6 * Fy * Aw / 1000.0
            }
            is SteelSectionType.LSection -> {
                val Aw = section.legB * t
                PHI_SHEAR * 0.6 * Fy * Aw / 1000.0
            }
            is SteelSectionType.TSection -> {
                val Aw = section.flangeWidth * section.flangeThickness
                PHI_SHEAR * 0.6 * Fy * Aw / 1000.0
            }
            is SteelSectionType.BuiltUp -> 0.0
            else -> 0.0
        }
    }

    // ===================== COMBINED LOADING (AISC Chapter H) =====================

    /**
     * فحص الحمل المركب (محوري + انحناء) حسب AISC Chapter H
     * @param Pu Factored axial load (kN, positive = compression, negative = tension)
     * @param Mux Factored moment about strong axis (kN.m)
     * @param Muy Factored moment about weak axis (kN.m)
     * @param isSwayFrame If true, use amplified moments (second-order effects)
     */
    fun checkCombinedLoading(
        Pu: Double,
        Mux: Double,
        Muy: Double,
        section: SteelSectionType,
        grade: SteelGrade,
        Kx: Double,
        Ky: Double,
        Lx: Double,
        Ly: Double,
        Cb: Double = 1.0,
        isSwayFrame: Boolean = false
    ): CombinedLoadingResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // --- Calculate individual capacities ---

        // Axial capacity (compression or tension)
        val phiPn: Double
        if (Pu >= 0) {
            // Compression
            val compressionResult = designCompressionMember(section, grade, Pu, Kx, Ky, Lx, Ly)
            phiPn = compressionResult.phiPn
            codeNotes.add("ضغط: φPn = ${"%.1f".format(phiPn)} kN, Fcr = ${"%.1f".format(compressionResult.Fcr)} MPa")
        } else {
            // Tension (use a default welded connection if none provided)
            val tensionResult = designTensionMember(
                section, grade, abs(Pu),
                ConnectionType.Welded(WeldType.FILLET, 6.0, 100.0, ElectrodeType.E70XX)
            )
            phiPn = tensionResult.designCapacity
            codeNotes.add("شد: φPn = ${"%.1f".format(phiPn)} kN")
        }

        // Flexural capacities
        val flexResult = designFlexuralMember(section, grade, Mux, Muy, 0.0, 0.0, 0.0, Cb, true)
        val phiMnx = flexResult.momentCapacityX
        val phiMny = flexResult.momentCapacityY

        // --- AISC H1: Doubly and singly symmetric members ---

        // Amplified moments for sway frames (simplified second-order analysis)
        var MuxAmplified = Mux
        var MuyAmplified = Muy
        if (isSwayFrame && Pu > 0) {
            // Simplified B1 factor (notional amplification for non-sway within sway frame)
            val Cm = 1.0  // Conservative for gravity-only columns
            val Pe1x = PI * PI * E_STEEL * section.ix / ((Kx * Lx).pow(2).coerceAtLeast(1.0)) / 1000.0  // kN
            val Pe1y = PI * PI * E_STEEL * getIy(section) / ((Ky * Ly).pow(2).coerceAtLeast(1.0)) / 1000.0
            val B1x = Cm / (1.0 - (abs(Pu) / Pe1x.coerceAtLeast(1.0))).coerceAtLeast(1.0)
            val B1y = Cm / (1.0 - (abs(Pu) / Pe1y.coerceAtLeast(1.0))).coerceAtLeast(1.0)
            MuxAmplified = B1x * Mux
            MuyAmplified = B1y * Muy
            codeNotes.add("B1x = ${"%.2f".format(B1x)}, B1y = ${"%.2f".format(B1y)} (تضخيم العزوم — إطار جانبي)")
        }

        // --- Interaction equations ---
        val absPu = abs(Pu)
        val pr = if (phiPn > 0) absPu / phiPn else if (absPu > 0) Double.MAX_VALUE else 0.0
        val mrX = if (phiMnx > 0) MuxAmplified / phiMnx else if (MuxAmplified > 0) Double.MAX_VALUE else 0.0
        val mrY = if (phiMny > 0) MuyAmplified / phiMny else if (MuyAmplified > 0) Double.MAX_VALUE else 0.0

        val (interactionRatio, equation) = if (pr >= 0.2) {
            // AISC H1-1a: Pu/(φPn) + (8/9)*(Mux/(φMnx) + Muy/(φMny)) ≤ 1.0
            val ratio = pr + (8.0 / 9.0) * (mrX + mrY)
            Pair(ratio, "H1-1a")
        } else {
            // AISC H1-1b: Pu/(2φPn) + (Mux/(φMnx) + Muy/(φMny)) ≤ 1.0
            val ratio = pr / 2.0 + (mrX + mrY)
            Pair(ratio, "H1-1b")
        }

        codeNotes.add("AISC 360-16 Chapter H: الحمل المركب")
        codeNotes.add("المعادلة: ${equation}")
        codeNotes.add("Pu/(φPn) = ${"%.3f".format(pr)}")
        codeNotes.add("Mux/(φMnx) = ${"%.3f".format(mrX)}, Muy/(φMny) = ${"%.3f".format(mrY)}")
        codeNotes.add("نسبة التفاعل = ${"%.3f".format(interactionRatio)}")

        if (interactionRatio > 1.0) {
            warnings.add("معادلة التفاعل تتجاوز 1.0 (η = ${"%.3f".format(interactionRatio)}) — زِد المقطع أو قلل الأحمال")
        }
        if (isSwayFrame) {
            warnings.add("إطار جانبي — فحص الاستقرار الكلي مطلوب (AISC C2)")
        }

        return CombinedLoadingResult(
            interactionRatio = interactionRatio,
            axialRatio = pr,
            flexuralRatioX = mrX,
            flexuralRatioY = mrY,
            equation = equation,
            isSafe = interactionRatio <= 1.0,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ===================== BRACING DESIGN (AISC Chapter C & Appendix 6) =====================

    /**
     * تصميم الكوبري (عضو الدعم الجانبي) حسب AISC
     * @param axialLoad Factored axial load in kN (compression in brace)
     * @param unbracedLength Unbraced length in mm
     */
    fun designBracing(
        section: SteelSectionType,
        grade: SteelGrade,
        axialLoad: Double,
        unbracedLength: Double,
        connectionType: ConnectionType
    ): SteelBracingResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // Bracing members typically have K = 1.0 (pinned-pinned)
        // Check both axes — use the larger slenderness
        val rx = section.rx
        val ry = getRy(section)
        val lambdaX = unbracedLength / rx.coerceAtLeast(0.1)
        val lambdaY = unbracedLength / ry.coerceAtLeast(0.1)
        val lambdaMax = maxOf(lambdaX, lambdaY)

        // Compression capacity (same as compression member with K=1.0)
        val compressionResult = designCompressionMember(
            section, grade, axialLoad, 1.0, 1.0, unbracedLength, unbracedLength
        )

        // Connection adequacy check
        var connectionAdequate = true
        when (connectionType) {
            is ConnectionType.Bolted -> {
                // Check if bolt capacity exceeds applied load
                val fu = connectionType.boltGrade.fu
                val db = connectionType.boltDiameter
                val Ab = PI * db * db / 4.0  // mm²
                // Single bolt shear capacity: φRn = φ × 0.6 × Fu × Ab (AISC J3-6)
                val phiRnBolt = 0.75 * 0.6 * fu * Ab / 1000.0  // kN per bolt (double shear possible)
                val totalBoltCapacity = phiRnBolt * connectionType.numberOfBolts
                connectionAdequate = totalBoltCapacity >= axialLoad
                codeNotes.add("قدرة المسامير = ${"%.1f".format(totalBoltCapacity)} kN (${connectionType.numberOfBolts} × ${"%.1f".format(phiRnBolt)} kN)")
            }
            is ConnectionType.Welded -> {
                // Weld capacity check
                val Fexx = connectionType.electrodeType.tensileStrength
                val a = connectionType.weldSize
                val L = connectionType.weldLength
                // Fillet weld capacity per AISC J2.4
                val Rnw = 0.60 * Fexx * (a / sqrt(2.0)) * L / 1000.0  // kN
                val phiRnw = 0.75 * Rnw
                connectionAdequate = phiRnw >= axialLoad
                codeNotes.add("قدرة اللحام = ${"%.1f".format(phiRnw)} kN")
            }
            else -> {
                connectionAdequate = true
            }
        }

        // Slenderness check
        if (lambdaMax > MAX_SLENDERNESS_BRACING) {
            warnings.add("نسبة النحافة تتجاوز الحد (λ=${"%.1f".format(lambdaMax)} > ${MAX_SLENDERNESS_BRACING.toInt()})")
        }

        codeNotes.add("AISC 360-16: تصميم الكوبري (Bracing)")
        codeNotes.add("λx = ${"%.1f".format(lambdaX)}, λy = ${"%.1f".format(lambdaY)}")
        codeNotes.add("قدرة الضغط = ${"%.1f".format(compressionResult.phiPn)} kN")
        codeNotes.add("الحمل المطلوب = ${"%.1f".format(axialLoad)} kN")
        codeNotes.add("نسبة الإجهاد = ${"%.3f".format(compressionResult.utilizationRatio)}")

        val isSafe = compressionResult.isSafe && connectionAdequate

        return SteelBracingResult(
            compressionCapacity = compressionResult.phiPn,
            slendernessRatio = lambdaMax,
            isSafe = isSafe,
            connectionAdequate = connectionAdequate,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ===================== COMPOSITE BEAM DESIGN (AISC Chapter I) =====================

    /**
     * تصميم كمرة مركبة (صلب + خرسانة) حسب AISC Chapter I
     * @param fcu Concrete compressive strength (fc') in MPa
     * @param slabThickness Total slab thickness in mm
     * @param effectiveWidth Effective width of concrete slab in mm
     * @param studDiameter Shear stud diameter in mm
     * @param studSpacing Stud center-to-center spacing in mm
     * @param Mu Factored moment in kN.m
     * @param Span Beam span in mm
     */
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

        // Concrete properties
        val Ec = 4700.0 * sqrt(fcu)  // MPa (ACI 318)
        val concreteModularRatio = E_STEEL / Ec

        codeNotes.add("AISC 360-16 Chapter I: تصميم الكمرة المركبة")
        codeNotes.add("Fy = ${Fy.toInt()} MPa, fc' = ${fcu.toInt()} MPa")
        codeNotes.add("Ec = ${"%.0f".format(Ec)} MPa, n = ${"%.1f".format(concreteModularRatio)}")

        // Steel section properties
        val As = steelSection.area  // mm²
        val d = steelSection.depth  // mm
        val Zx = steelSection.zx    // mm³
        val Ix = steelSection.ix    // mm⁴

        // Transformed concrete area
        val Ac = effectiveWidth * slabThickness  // mm² (concrete area)
        val AcTransformed = Ac / concreteModularRatio  // mm² (transformed to steel)

        // Shear stud capacity (AISC I8.1a)
        val AsStud = PI * studDiameter * studDiameter / 4.0  // mm²
        val FuStud = 450.0  // MPa (typical for welded studs)
        val EsStud = 200000.0  // MPa

        // Qn = 0.5 × As × √(fc' × Ec) × (Es/Fu) but not exceeding Fu × As (AISC I8-1)
        val QnValue1 = 0.5 * AsStud * sqrt(fcu * Ec) * (EsStud / FuStud)
        val QnValue2 = FuStud * AsStud
        val Qn = minOf(QnValue1, QnValue2)  // N per stud

        codeNotes.add("قطر الوتد = ${studDiameter.toInt()} mm, مساحة الوتد = ${"%.0f".format(AsStud)} mm²")
        codeNotes.add("قدرة الوتد Qn = ${"%.1f".format(Qn / 1000.0)} kN")

        // Horizontal shear (AISC I3)
        // Vh = min(0.85 × fc' × Ac, Fy × As) (full composite)
        val Vh1 = 0.85 * fcu * Ac / 1000.0  // kN (concrete crushing)
        val Vh2 = Fy * As / 1000.0  // kN (steel yielding)
        val Vh = minOf(Vh1, Vh2)

        codeNotes.add("Vh (قص أفقي) = ${"%.1f".format(Vh)} kN")
        codeNotes.add("  0.85×fc'×Ac = ${"%.1f".format(Vh1)} kN")
        codeNotes.add("  Fy×As = ${"%.1f".format(Vh2)} kN")

        // Number of studs required (half-span studs on each side)
        val studsRequired = if (Qn > 0) {
            val totalStuds = ceil(Vh * 1000.0 / Qn).toInt()
            totalStuds
        } else {
            0
        }

        // Number of studs provided based on spacing and span
        val studsProvided = if (studSpacing > 0) {
            maxOf(0, (span / studSpacing).toInt() + 1)
        } else {
            0
        }

        codeNotes.add("عدد الأوتاد المطلوبة = $studsRequired")
        codeNotes.add("عدد الأوتاد المتوفرة = $studsProvided (مسافة = ${studDiameter.toInt()} mm)")
        val shearStudsAdequate = studsProvided >= studsRequired

        if (!shearStudsAdequate) {
            warnings.add("عدد الأوتاد غير كافٍ — المطلوب $studsRequired والمتوفر $studsProvided")
        }

        // Determine composite section properties
        // Locate plastic neutral axis (PNA)
        // Cc = 0.85 × fc' × a × beff (compression in concrete)
        // T = Fy × As (tension in steel)
        val beff = effectiveWidth
        val aFull = (Fy * As) / (0.85 * fcu * beff)  // mm (full composite)

        // Check if PNA is in slab
        val pnainSlab = aFull <= slabThickness

        val compositeMomentCapacity: Double
        if (pnainSlab) {
            // PNA in concrete slab — full composite action
            // Mn = Fy × As × (d + a/2 - a/2) simplified:
            // Mn = Fy × As × (d/2 + slabThickness/2) approximately
            val yt = d / 2.0 + slabThickness / 2.0 - aFull / 2.0
            compositeMomentCapacity = Fy * As * yt / 1e6  // kN.m
            codeNotes.add("PNA داخل البلاطة الخرسانية (a = ${"%.1f".format(aFull)} mm)")
        } else {
            // PNA in steel section — partial composite
            // Use steel moment capacity as lower bound (conservative)
            compositeMomentCapacity = Fy * Zx / 1e6  // kN.m
            warnings.add("المحور المحايد البلاستيكي خارج البلاطة — استخدم كمرة مركبة جزئية")
            codeNotes.add("PNA خارج البلاطة — تصميم جزئي")
        }

        codeNotes.add("القدرة اللدنة المركبة = ${"%.1f".format(compositeMomentCapacity)} kN.m")

        // Compare with non-composite capacity
        val nonCompositeMp = Fy * Zx / 1e6
        codeNotes.add("القدرة اللدنة غير المركبة = ${"%.1f".format(nonCompositeMp)} kN.m")
        if (compositeMomentCapacity > nonCompositeMp) {
            codeNotes.add("الزيادة بسبب التركيب = ${"%.1f".format(compositeMomentCapacity - nonCompositeMp)} kN.m (${"%.0f".format((compositeMomentCapacity / nonCompositeMp - 1) * 100)}%)")
        }

        val utilizationRatio = if (compositeMomentCapacity > 0) Mu / compositeMomentCapacity else Double.MAX_VALUE
        val phiMn = PHI_FLEXURE * compositeMomentCapacity
        val phiUtilization = Mu / phiMn

        codeNotes.add("φMn = ${"%.1f".format(phiMn)} kN.m")
        codeNotes.add("Mu = ${"%.1f".format(Mu)} kN.m, نسبة الإجهاد = ${"%.3f".format(phiUtilization)}")

        if (phiUtilization > 1.0) {
            warnings.add("الكمرة المركبة غير كافية — نسبة الإجهاد = ${"%.3f".format(phiUtilization)} > 1.0")
        }

        // Check minimum slab thickness (AISC I3.1)
        if (slabThickness < 64.0) {
            warnings.add("سمك البلاطة أقل من 64 mm — تأكد من تغطية الأوتاد بالكامل")
        }

        // Check maximum stud spacing (AISC I8.2d)
        val maxStudSpacing = minOf(8.0 * (slabThickness / 1.0), 915.0)  // mm
        if (studSpacing > maxStudSpacing) {
            warnings.add("مسافة الأوتاد تتجاوز الحد الأقصى ${"%.0f".format(maxStudSpacing)} mm")
        }

        return CompositeBeamResult(
            compositeMomentCapacity = compositeMomentCapacity,
            studsRequired = studsRequired,
            studsProvided = studsProvided,
            shearStudsAdequate = shearStudsAdequate,
            isSafe = phiUtilization <= 1.0,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }
}