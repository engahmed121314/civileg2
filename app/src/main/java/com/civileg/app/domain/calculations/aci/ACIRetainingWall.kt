package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.DesignCode
import kotlin.math.*

class ACIRetainingWall : RetainingWallDesign {

    companion object {
        private const val PHI_FLEXURE = 0.9
        private const val PHI_SHEAR = 0.75
        private const val LF_DEAD = 1.2
        private const val LF_LIVE = 1.6
        private const val LF_LATERAL = 1.6
        private const val COVER_EARTH = 75.0
        private const val MIN_STEEL_RATIO = 0.0018
        private const val OT_FS_LIMIT = 2.0
        private const val SLIDE_FS_LIMIT = 1.5
    }

    override fun designRetainingWall(input: RetainingWallInput): RetainingWallResult {
        val H = input.wallHeight
        val tBase = input.stemBaseThickness
        val tTop = input.stemTopThickness
        val B = input.baseWidth
        val tFooting = input.baseThickness
        val toe = input.toeLength
        val heel = input.heelLength
        val gamma = input.soilDensity
        val phi = input.frictionAngle
        val q = input.surchargeLoad
        val zwt = input.waterTableDepth
        val fcu = input.fcu
        val fy = input.fy
        val mu = input.baseFrictionCoeff
        val fc = 0.8 * fcu // ACI conversion

        val phiRad = Math.toRadians(phi)
        val Ka = tan(Math.PI / 4 - phiRad / 2).pow(2)

        val hSoil = if (zwt >= H) H else zwt
        val hWater = max(0.0, H - zwt)
        val gammaSub = gamma - 9.81

        val paSoil = 0.5 * Ka * gamma * hSoil.pow(2)
        val paWater = 0.5 * 9.81 * hWater.pow(2)
        val paSurcharge = Ka * q * H
        val totalPa = paSoil + paWater + paSurcharge
        val paArm = H / 3.0

        val momentOT = paSoil * paArm + paWater * (hWater / 3.0 + hSoil) + paSurcharge * (H / 2.0)

        val stemW = 0.5 * (tBase + tTop) * H * 25.0
        val baseW = B * tFooting * 25.0
        val soilW = heel * (H - tFooting) * gamma
        val totalW = stemW + baseW + soilW

        val stemArm = toe + (tBase * (H + 2 * tTop)) / (3 * (tBase + tTop))
        val baseArm = B / 2.0
        val heelArm = toe + tBase / 2.0 + heel / 2.0

        val momentR = stemW * stemArm + baseW * baseArm + soilW * heelArm
        val otFS = momentR / momentOT

        val Kp = tan(Math.PI / 4 + phiRad / 2).pow(2)
        val pp = 0.5 * gamma * tFooting.pow(2) * Kp
        val slideFS = (mu * totalW + pp * 0.5) / totalPa

        val ecc = abs(B / 2.0 - (momentR - momentOT) / totalW)
        val maxBP = totalW / B * (1 + 6 * ecc / B)
        val minBP = max(0.0, totalW / B * (1 - 6 * ecc / B))
        val bearingFS = if (maxBP > 0) input.soilBearingCapacity / maxBP else 0.0

        // Stem - cantilever
        val stemH = H - tFooting
        val MuStem = LF_LATERAL * (Ka * gamma * stemH.pow(3) / 6 + Ka * q * stemH.pow(2) / 2) * 1e3 // kN.m -> N.mm
        val VuStem = LF_LATERAL * (Ka * gamma * stemH.pow(2) / 2 + Ka * q * stemH) // kN
        val b = 1000.0
        val d = tBase * 1000 - COVER_EARTH - 10.0

        // ACI Rn-ρ method
        val Rn = MuStem / (PHI_FLEXURE * b * d * d)
        val rho = 0.85 * fc / fy * (1 - sqrt(1 - 2 * Rn / (0.85 * fc)))
        val rhoMin = MIN_STEEL_RATIO
        val rhoMax = 0.025
        val rhoFinal = rho.coerceIn(rhoMin, rhoMax)
        val As = rhoFinal * b * d
        val (nBars, barDia) = RetainingWallDesign.selectBars(As)
        val AsProv = nBars * PI * (barDia / 2.0).pow(2)
        val distAs = max(rhoMin * b * d * 0.25, 100.0)
        val distBars = RetainingWallDesign.selectBars(distAs)

        // Shear
        val qu = VuStem * 1000 / (b * d)
        val phiVc = PHI_SHEAR * 0.17 * sqrt(fc) * b * d // N
        val shearOk = VuStem * 1000 <= phiVc

        // Toe
        val toeMu = max(0.0, (maxBP * toe * toe / 2 - minBP * toe * toe / 6)) * 1e3 * PHI_FLEXURE
        val toeD = tFooting * 1000 - COVER_EARTH - 10.0
        val toeRn = toeMu / (PHI_FLEXURE * b * toeD * toeD)
        val toeRho = 0.85 * fc / fy * (1 - sqrt(max(0.0, 1 - 2 * toeRn / (0.85 * fc))))
        val toeAs = max(toeRho, rhoMin) * b * toeD
        val tb = RetainingWallDesign.selectBars(toeAs)

        // Heel
        val heelLoad = (H - tFooting) * gamma + q
        val heelMu = heelLoad * heel * heel / 2 * LF_LIVE * 1e3 * PHI_FLEXURE
        val heelD = tFooting * 1000 - COVER_EARTH - 10.0
        val heelRn = heelMu / (PHI_FLEXURE * b * heelD * heelD)
        val heelRho = 0.85 * fc / fy * (1 - sqrt(max(0.0, 1 - 2 * heelRn / (0.85 * fc))))
        val heelAs = max(heelRho, rhoMin) * b * heelD
        val hb = RetainingWallDesign.selectBars(heelAs)

        val checks = listOf(
            WallSafetyCheck("OT FS", otFS >= OT_FS_LIMIT, otFS, OT_FS_LIMIT,
                "ACI: FS=${"%.2f".format(otFS)} >= ${OT_FS_LIMIT}"),
            WallSafetyCheck("Sliding FS", slideFS >= SLIDE_FS_LIMIT, slideFS, SLIDE_FS_LIMIT,
                "FS=${"%.2f".format(slideFS)} >= ${SLIDE_FS_LIMIT}"),
            WallSafetyCheck("Bearing", maxBP <= input.soilBearingCapacity, maxBP, input.soilBearingCapacity,
                "${"%.1f".format(maxBP)} <= ${"%.1f".format(input.soilBearingCapacity)} kN/m\u00B2"),
            WallSafetyCheck("Stem Flexure", AsProv >= As * 0.95, AsProv, As,
                "\u03C1=${"%.4f".format(rhoFinal)} (min=${rhoMin})"),
            WallSafetyCheck("Shear", shearOk, qu, phiVc / (b * d),
                "Vu=${"%.0f".format(qu)} <= \u03C6Vc=${"%.0f".format(phiVc / (b * d))} N/mm\u00B2")
        )

        val isSafe = checks.all { it.isSafe }
        val notes = mutableListOf(
            "ACI 318: \u03C6f=${PHI_FLEXURE}, \u03C6v=${PHI_SHEAR}",
            "f'c = 0.8\u00D7fcu = ${"%.0f".format(fc)} MPa",
            "Ka = ${"%.3f".format(Ka)} (Rankine)",
            "Cover = ${COVER_EARTH}mm (earth contact)",
            "Min \u03C1 = ${MIN_STEEL_RATIO}"
        )
        if (hWater > 0) notes.add("Water table at ${"%.1f".format(zwt)}m - hydrostatic pressure included")

        return RetainingWallResult(
            isSafe = isSafe, designCode = DesignCode.ACI,
            overturningFS = otFS, slidingFS = slideFS, bearingFS = bearingFS,
            maxBearingPressure = maxBP, minBearingPressure = minBP,
            stemMoment = MuStem / 1e6, stemShear = VuStem,
            stemMainRebar = RetainingWallDesign.formatRebar(nBars, barDia), stemMainRebarArea = AsProv,
            stemDistributionRebar = "${distBars.first}\u03A6${distBars.second}",
            toeMoment = toeMu / 1e6, toeShear = maxBP * toe, toeRebar = "${tb.first}\u03A6${tb.second}",
            heelMoment = heelMu / 1e6, heelShear = heelLoad * heel, heelRebar = "${hb.first}\u03A6${hb.second}",
            safetyChecks = checks, codeNotes = notes
        )
    }
}