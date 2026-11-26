package com.civileg.app.domain.calculations.steel

import com.civileg.app.domain.entities.BracingPreliminary
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.SteelSectionType
import com.civileg.app.domain.entities.area
import com.civileg.app.domain.entities.rx
import com.civileg.app.domain.entities.ry
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sqrt

/**
 * X-bracing of the side walls: geometry, quantities and the tension-only brace force from an
 * ENTERED lateral load. No capacity check: steel code text is not in the supplied references.
 */
object WarehouseBracing {

    fun compute(
        bayM: Double,
        eaveM: Double,
        numBays: Int,
        section: SteelSectionType,
        bracedBaysInput: List<Int>?,
        lateralLoadPerPanelKn: Double?,
        code: DesignCode = DesignCode.ECP,
        fyMpa: Double? = null,
        fuMpa: Double? = null,
        netAreaMm2: Double? = null
    ): BracingPreliminary {
        val usedDefault = bracedBaysInput == null
        val braced = (bracedBaysInput ?: if (numBays <= 1) listOf(1) else listOf(1, numBays)).distinct().sorted()
        if (braced.isEmpty() || braced.any { it < 1 || it > numBays }) {
            return BracingPreliminary(issued = false, reason = "NOT ISSUED: braced bays must be between 1 and $numBays", numBays = numBays)
        }
        val lengthM = sqrt(bayM * bayM + eaveM * eaveM)
        val angleDeg = Math.toDegrees(atan2(eaveM, bayM))
        val area = section.area
        val weightKgM = area * 7.85e-3          // mm2 x 7.85e-3 = kg/m (7850 kg/m3)
        val perBraceKg = weightKgM * lengthM
        val count = braced.size * 2 * 2         // 2 side walls x 2 diagonals (X) per braced bay
        val rMin = min(section.rx, section.ry)
        val slender = if (rMin > 0.0) lengthM * 1000.0 / rMin else null

        val lines = mutableListOf<String>()
        lines += "Brace length L = sqrt(bay^2 + eave^2) = sqrt(${f(bayM)}^2 + ${f(eaveM)}^2) = ${f(lengthM)} m"
        lines += "Angle to horizontal = atan(eave / bay) = atan(${f(eaveM)} / ${f(bayM)}) = ${f(angleDeg)} deg"
        lines += "Section ${section.sectionName}: A = ${f(area)} mm2, w = A x 7.85e-3 = ${f(weightKgM)} kg/m"
        lines += "Weight per diagonal = ${f(weightKgM)} x ${f(lengthM)} = ${f(perBraceKg)} kg; ${count} diagonals = ${f(perBraceKg * count)} kg"
        var slenderCheck: String? = null
        if (slender != null) {
            if (code == DesignCode.ECP) {
                // ECP 205 LRFD 2008 Table 2.3 (p.2-8): tension members 300 (K = 1.0 for tension members, 2.2.1.1). The table also lists
                // "bracing systems and secondary compression members" at 200; these braces are tension-only, so the tension row is used.
                slenderCheck = if (slender <= 300.0) "PASS" else "FAIL"
                lines += "Slenderness L/r(min) = ${f(lengthM * 1000.0)} / ${f(rMin)} = ${f(slender)} vs 300 (ECP 205 LRFD 2008 Table 2.3, p.2-8, tension members) -> $slenderCheck (r from the section model's dimensions, unverified)"
            } else lines += "Slenderness L/r(min) = ${f(lengthM * 1000.0)} / ${f(rMin)} = ${f(slender)} (r from the section model's dimensions; no limit compared)"
        }

        var force: Double? = null
        var stress: Double? = null
        var tension: String? = null
        var util: Double? = null
        if (lateralLoadPerPanelKn != null && lateralLoadPerPanelKn > 0.0) {
            val cosT = cos(Math.toRadians(angleDeg))
            force = lateralLoadPerPanelKn / cosT
            stress = force * 1000.0 / area
            lines += "Tension-only, one diagonal active per braced panel: N = H / cos(angle) = ${f(lateralLoadPerPanelKn)} / ${f(cosT, 4)} = ${f(force)} kN (H entered per braced panel)"
            lines += "Axial stress N / A = ${f(force * 1000.0)} / ${f(area)} = ${f(stress)} MPa"
            if (code == DesignCode.ACI || code == DesignCode.ECP) {
                // ACI selection: AISC 360-10 Section D2, Specification p.16.1-26 (14th ed. Manual): phi 0.90 yield, 0.75 rupture.
                // ECP selection: ECP 205 LRFD 2008 Section 3.1.1, p.3-1, Eq. 3.1 and 3.2: phi 0.85 yield, 0.70 fracture.
                // LRFD only; N is taken as the entered factored load.
                val american = code == DesignCode.ACI
                val phiY = if (american) 0.90 else 0.85
                val phiU = if (american) 0.75 else 0.70
                val ref = if (american) "AISC 360-10 D2 (p.16.1-26)" else "ECP 205 LRFD 2008 3.1.1 (p.3-1, Eq. 3.1, 3.2)"
                val fy = fyMpa; val fu = fuMpa; val ae = netAreaMm2
                val missing = listOfNotNull(if (fy == null || fy <= 0.0) "Fy" else null, if (fu == null || fu <= 0.0) "Fu" else null, if (ae == null || ae <= 0.0) "net area Ae" else null)
                if (missing.isNotEmpty()) {
                    lines += "$ref tension check: NOT ASSESSED (enter ${missing.joinToString(", ")})"
                } else if (ae!! > area + 1e-9) {
                    lines += "$ref tension check: NOT ASSESSED (net area Ae ${f(ae)} mm2 exceeds the brace gross area Ag ${f(area)} mm2; Ae must be Ag or less)"
                } else {
                    val yieldKn = phiY * fy!! * area / 1000.0
                    val ruptureKn = phiU * fu!! * ae / 1000.0
                    val cap = min(yieldKn, ruptureKn)
                    util = force / cap
                    tension = if (util <= 1.0 + 1e-9) "PASS" else "FAIL"
                    lines += "$ref, LRFD: yielding phi Pn = ${f(phiY)} x Fy x Ag = ${f(phiY)} x ${f(fy)} x ${f(area)} / 1000 = ${f(yieldKn)} kN (D2-1)"
                    lines += "${if (american) "Rupture" else "Fracture"} phi Pn = ${f(phiU)} x Fu x Ae = ${f(phiU)} x ${f(fu)} x ${f(ae)} / 1000 = ${f(ruptureKn)} kN (D2-2); governing phi Pn = ${f(cap)} kN"
                    lines += "N / phi Pn = ${f(force)} / ${f(cap)} = ${f(util, 3)} -> $tension (tension member only; Ag from the section model, Ae and Fy, Fu as entered)"
                }
            } else {
                lines += "Brace capacity check: NOT ASSESSED for ${code.displayName} (no sourced steel code text)"
            }
        } else {
            lines += "Brace force: NOT ASSESSED (enter the lateral load H per braced panel, kN)"
        }
        lines += "NOT ASSESSED: wind derivation, base fixity, connections (bolts and block shear need bolt layout), frame stability, sway${if (code == DesignCode.ACI || code == DesignCode.ECP) "" else ", brace capacity/utilisation"}"
        return BracingPreliminary(
            issued = true, numBays = numBays, bracedBays = braced, usedDefaultBays = usedDefault,
            bayM = bayM, eaveM = eaveM, lengthM = lengthM, angleDeg = angleDeg, sectionName = section.sectionName,
            areaMm2 = area, weightKgM = weightKgM, weightPerBraceKg = perBraceKg, braceCount = count,
            totalWeightKg = perBraceKg * count, slenderness = slender,
            lateralLoadPerPanelKn = lateralLoadPerPanelKn, braceForceKn = force, stressMpa = stress, tensionCheck = tension, tensionUtilisation = util, slendernessCheck = slenderCheck, lines = lines
        )
    }

    private fun f(v: Double, d: Int = 2) = String.format(java.util.Locale.US, "%.${d}f", v)
}
