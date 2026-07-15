package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.DesignCode
import kotlin.math.*

class ECPRetainingWall : RetainingWallDesign {

    companion object {
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15
        private const val LOAD_FACTOR_DEAD = 1.4
        private const val LOAD_FACTOR_LIVE = 1.6
        private const val COVER_EARTH = 50.0
        private const val MIN_STEEL_RATIO = 0.0013
        private const val OT_FS_LIMIT = 1.5
        private const val SLIDE_FS_LIMIT = 1.5
        private const val CRACK_WIDTH_LIMIT = 0.3
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

        val phiRad = Math.toRadians(phi)
        val Ka = tan(Math.PI / 4 - phiRad / 2).pow(2)
        val KaSurcharge = Ka

        // Earth pressures at base of wall
        val hSoil = if (zwt >= H) H else zwt
        val hWater = max(0.0, H - zwt)
        val gammaSub = gamma - 9.81

        val paSoil = 0.5 * Ka * gamma * hSoil.pow(2)
        val paWater = 0.5 * 9.81 * hWater.pow(2)
        val paSurcharge = KaSurcharge * q * H
        val totalPa = paSoil + paWater + paSurcharge
        val paHeight = H / 3.0
        val surchargeHeight = H / 2.0

        val momentOverturning = paSoil * paHeight + paWater * (hWater / 3.0 + hSoil) + paSurcharge * surchargeHeight

        // Self-weight components (per meter run)
        val stemWeight = 0.5 * (tBase + tTop) * H * 25.0
        val baseWeight = B * tFooting * 25.0
        val soilOnHeel = heel * (H - tFooting) * gamma
        val totalWeight = stemWeight + baseWeight + soilOnHeel

        // Lever arms from toe
        val stemArm = toe + (tBase * (H + 2 * tTop)) / (3 * (tBase + tTop))
        val baseArm = B / 2.0
        val heelSoilArm = toe + tBase / 2.0 + heel / 2.0

        val momentResisting = stemWeight * stemArm + baseWeight * baseArm + soilOnHeel * heelSoilArm
        val overturningFS = momentResisting / momentOverturning

        // Sliding check
        val passivePressure = 0.5 * gamma * tFooting.pow(2) * tan(Math.PI / 4 + phiRad / 2).pow(2)
        val slidingFS = (mu * totalWeight + passivePressure * 0.5) / totalPa

        // Bearing pressure
        val eccentricity = (momentResisting - momentOverturning) / totalWeight
        val e = abs(B / 2.0 - eccentricity)
        val maxBearing = totalWeight / B * (1 + 6 * e / B)
        val minBearing = max(0.0, totalWeight / B * (1 - 6 * e / B))
        val bearingFS = if (maxBearing > 0) input.soilBearingCapacity / maxBearing else 0.0

        // Stem design (cantilever from base)
        val stemH = H - tFooting
        val stemMomentUnfactored = (Ka * gamma * stemH.pow(3) / 6) + (Ka * q * stemH.pow(2) / 2)
        val stemShearUnfactored = Ka * gamma * stemH.pow(2) / 2 + Ka * q * stemH

        val Mu = LOAD_FACTOR_DEAD * stemMomentUnfactored
        val Vu = LOAD_FACTOR_DEAD * stemShearUnfactored
        val b = 1000.0
        val d = (tBase * 1000) - COVER_EARTH - 8.0

        val R = Mu * 1e6 / (fcu / GAMMA_C * b * d * d)
        val z = 0.8 + sqrt(0.36 - 2 * R) * 0.45
        val As = Mu * 1e6 / (fy / GAMMA_S * z * d)
        val AsMin = MIN_STEEL_RATIO * b * d
        val AsReq = max(As, AsMin)

        val (numBars, barDia) = selectBars(AsReq)
        val AsProvided = numBars * PI * (barDia / 2.0).pow(2)
        val distBars = selectBars(AsMin * 0.25).let { "${it.first}\u03A6${it.second}" }

        // Shear check
        val qu = Vu * 1000 / (b * d)
        val qcu = 0.45 * sqrt(fcu / GAMMA_C)
        val qcuLimit = 0.45 * sqrt(fcu)
        val needStirrups = qu > 0.67 * qcu

        // Toe design
        val toeMoment = max(0.0, maxBearing * toe * toe / 2 - minBearing * toe * toe / 6)
        val toeShear = max(0.0, (maxBearing + minBearing) / 2 * toe)
        val toeD = (tFooting * 1000) - COVER_EARTH - 8.0
        val toeR = toeMoment * 1e6 / (fcu / GAMMA_C * b * toeD * toeD)
        val toeZ = 0.8 + sqrt(max(0.0, 0.36 - 2 * toeR)) * 0.45
        val toeAs = toeMoment * 1e6 / (fy / GAMMA_S * toeZ * toeD)
        val toeAsFinal = max(toeAs, MIN_STEEL_RATIO * b * toeD)
        val toeBars = selectBars(toeAsFinal)
        val toeRebarStr = "${toeBars.first}\u03A6${toeBars.second}"

        // Heel design
        val heelLoad = (H - tFooting) * gamma + q
        val heelMoment = heelLoad * heel * heel / 2 * LOAD_FACTOR_DEAD
        val heelShear = heelLoad * heel * LOAD_FACTOR_DEAD
        val heelD = (tFooting * 1000) - COVER_EARTH - 8.0
        val heelR = heelMoment * 1e6 / (fcu / GAMMA_C * b * heelD * heelD)
        val heelZ = 0.8 + sqrt(max(0.0, 0.36 - 2 * heelR)) * 0.45
        val heelAs = heelMoment * 1e6 / (fy / GAMMA_S * heelZ * heelD)
        val heelAsFinal = max(heelAs, MIN_STEEL_RATIO * b * heelD)
        val heelBars = selectBars(heelAsFinal)
        val heelRebarStr = "${heelBars.first}\u03A6${heelBars.second}"

        val checks = listOf(
            WallSafetyCheck("OT FS", overturningFS >= OT_FS_LIMIT, overturningFS, OT_FS_LIMIT,
                "Overturning: ${"%.2f".format(overturningFS)} >= ${OT_FS_LIMIT}"),
            WallSafetyCheck("Sliding FS", slidingFS >= SLIDE_FS_LIMIT, slidingFS, SLIDE_FS_LIMIT,
                "Sliding: ${"%.2f".format(slidingFS)} >= ${SLIDE_FS_LIMIT}"),
            WallSafetyCheck("Bearing", bearingFS >= 1.0, bearingFS, input.soilBearingCapacity,
                "Max: ${"%.1f".format(maxBearing)} kN/m\u00B2 <= ${"%.1f".format(input.soilBearingCapacity)}"),
            WallSafetyCheck("Stem Flexure", AsProvided >= AsReq, AsProvided, AsReq,
                "As=${"%.0f".format(AsProvided)} >= Req=${"%.0f".format(AsReq)} mm\u00B2/m"),
            WallSafetyCheck("Shear", !needStirrups, qu, 0.67 * qcu,
                "qu=${"%.2f".format(qu)} <= ${"%.2f".format(0.67 * qcu)}")
        )

        val isSafe = checks.all { it.isSafe }
        val notes = mutableListOf(
            "ECP 203: \u03B3c=${GAMMA_C}, \u03B3s=${GAMMA_S}",
            "Ka = ${"%.3f".format(Ka)} (Rankine)",
            "Cover = ${COVER_EARTH}mm (earth contact)",
            "Min steel = ${"%.4f".format(MIN_STEEL_RATIO)} (${MIN_STEEL_RATIO * 100}%)"
        )
        if (hWater > 0) notes.add("Water table at ${"%.1f".format(zwt)}m - hydrostatic pressure included")

        return RetainingWallResult(
            isSafe = isSafe, designCode = DesignCode.ECP,
            overturningFS = overturningFS, slidingFS = slidingFS, bearingFS = bearingFS,
            maxBearingPressure = maxBearing, minBearingPressure = minBearing,
            stemMoment = Mu, stemShear = Vu,
            stemMainRebar = formatRebar(numBars, barDia), stemMainRebarArea = AsProvided,
            stemDistributionRebar = distBars,
            toeMoment = toeMoment * LOAD_FACTOR_DEAD, toeShear = toeShear, toeRebar = toeRebarStr,
            heelMoment = heelMoment, heelShear = heelShear, heelRebar = heelRebarStr,
            safetyChecks = checks, codeNotes = notes
        )
    }
}