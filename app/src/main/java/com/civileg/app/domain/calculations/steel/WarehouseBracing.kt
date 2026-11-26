package com.civileg.app.domain.calculations.steel

import com.civileg.app.domain.entities.BracingPreliminary
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
        lateralLoadPerPanelKn: Double?
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
        if (slender != null) lines += "Slenderness L/r(min) = ${f(lengthM * 1000.0)} / ${f(rMin)} = ${f(slender)} (r from the section model's dimensions; no limit compared)"

        var force: Double? = null
        var stress: Double? = null
        if (lateralLoadPerPanelKn != null && lateralLoadPerPanelKn > 0.0) {
            val cosT = cos(Math.toRadians(angleDeg))
            force = lateralLoadPerPanelKn / cosT
            stress = force * 1000.0 / area
            lines += "Tension-only, one diagonal active per braced panel: N = H / cos(angle) = ${f(lateralLoadPerPanelKn)} / ${f(cosT, 4)} = ${f(force)} kN (H entered per braced panel)"
            lines += "Axial stress N / A = ${f(force * 1000.0)} / ${f(area)} = ${f(stress)} MPa (no capacity check: NOT ASSESSED)"
        } else {
            lines += "Brace force: NOT ASSESSED (enter the lateral load H per braced panel, kN)"
        }
        lines += "NOT ASSESSED: wind derivation, base fixity, connections, frame stability, sway, brace capacity/utilisation"
        return BracingPreliminary(
            issued = true, numBays = numBays, bracedBays = braced, usedDefaultBays = usedDefault,
            bayM = bayM, eaveM = eaveM, lengthM = lengthM, angleDeg = angleDeg, sectionName = section.sectionName,
            areaMm2 = area, weightKgM = weightKgM, weightPerBraceKg = perBraceKg, braceCount = count,
            totalWeightKg = perBraceKg * count, slenderness = slender,
            lateralLoadPerPanelKn = lateralLoadPerPanelKn, braceForceKn = force, stressMpa = stress, lines = lines
        )
    }

    private fun f(v: Double, d: Int = 2) = String.format(java.util.Locale.US, "%.${d}f", v)
}
