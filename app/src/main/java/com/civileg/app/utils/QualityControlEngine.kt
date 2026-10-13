package com.civileg.app.utils

import com.civileg.app.domain.entities.GenericSafetyCheck
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

object QualityControlEngine {

    private const val CUBE_TO_CYLINDER = 0.8
    private const val STEEL_E = 200_000.0

    data class QcOutcome(
        val title: String,
        val checks: List<GenericSafetyCheck>,
        val details: List<Pair<String, String>>,
        val passed: Boolean,
        val utilizationRatio: Double,
        val summary: String
    )

    object Category {
        const val CONCRETE = "CONCRETE"
        const val SOIL = "SOIL"
        const val REBAR = "REBAR"
        const val STEEL = "STEEL"
        const val ROADS = "ROADS"
        const val BRIDGES = "BRIDGES"
        const val TUNNELS = "TUNNELS"
        const val BUILDINGS = "BUILDINGS"
        const val EARTHWORKS = "EARTHWORKS"
        const val PILING = "PILING"
        const val RETAINING_WALLS = "RETAINING_WALLS"
        const val WATERPROOFING = "WATERPROOFING"
        const val MASONRY = "MASONRY"
        const val FLOORING = "FLOORING"
        const val PRECAST = "PRECAST"
        const val POST_TENSION = "POST_TENSION"
        const val GROUTING = "GROUTING"
        const val SOIL_IMPROVEMENT = "SOIL_IMPROVEMENT"
        const val WATER_TANKS = "WATER_TANKS"
        const val DRAINAGE = "DRAINAGE"
        const val PAINTING = "PAINTING"
        const val SITE_WORKS = "SITE_WORKS"
        const val FIRE_PROTECTION = "FIRE_PROTECTION"
    }

    fun codeName(code: CalculatorEngine.DesignCode): String = when (code) {
        CalculatorEngine.DesignCode.ACI -> "ACI 318-19"
        CalculatorEngine.DesignCode.SAUDI -> "SBC 304-2018"
        else -> "ECP 203-2020"
    }

    // ==================================================================
    // CONCRETE
    // ==================================================================
    fun concreteQc(
        code: CalculatorEngine.DesignCode,
        elementId: String,
        volumeM3: Double,
        slumpMm: Double,
        fcuDesignMpa: Double,
        strength7DaysMpa: Double,
        strength28DaysMpa: Double,
        airContent: Double = 4.0,
        concreteTemp: Double = 25.0
    ): QcOutcome {
        val fcu = fcuDesignMpa.coerceAtLeast(1.0)
        val lowerAccept = fcu - 3.5
        val ratio = if (strength28DaysMpa > 0) strength7DaysMpa / strength28DaysMpa else 0.0
        val slumpRange = QcLimits.slumpRange(code)

        val checks = listOf(
            GenericSafetyCheck("Slump within code range", slumpMm, slumpRange.endInclusive, "mm",
                slumpMm in slumpRange, "ECP 203 §6.3 / ACI 318-19 §26.12.1"),
            GenericSafetyCheck("28-day strength ≥ f'cu", strength28DaysMpa, fcu, "MPa",
                strength28DaysMpa >= fcu, "ACI 318-19 Table 26.12.3.1"),
            GenericSafetyCheck("Individual strength ≥ f'cu − 3.5 MPa", strength28DaysMpa, lowerAccept, "MPa",
                strength28DaysMpa >= lowerAccept, "ACI 318-19 Table 26.12.3.1"),
            GenericSafetyCheck("7/28-day strength ratio ≥ 65%", ratio * 100.0, 65.0, "%",
                ratio >= 0.65, "ECP 203 §6.3"),
            GenericSafetyCheck("Air content 3–8%", airContent, 8.0, "%",
                airContent in 3.0..8.0, "ASTM C231"),
            GenericSafetyCheck("Concrete temperature 5–40 °C", concreteTemp, 40.0, "°C",
                concreteTemp in 5.0..40.0, "ACI 305.1 / ACI 306.1")
        )

        val passed = checks.all { it.passed }
        val utilization = if (fcu > 0) strength28DaysMpa / fcu else 0.0
        val details = listOf(
            "Element" to elementId.ifBlank { "-" },
            "Design f'cu" to "%.1f MPa".format(fcu),
            "Concrete volume" to "%.3f m³".format(volumeM3),
            "Slump" to "%.0f mm".format(slumpMm),
            "Air content" to "%.1f %%".format(airContent),
            "Concrete temperature" to "%.1f °C".format(concreteTemp),
            "7-day strength" to "%.1f MPa".format(strength7DaysMpa),
            "28-day strength" to "%.1f MPa".format(strength28DaysMpa),
            "7/28 ratio" to "%.1f %%".format(ratio * 100.0)
        )
        return QcOutcome(
            title = "Concrete Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = utilization,
            summary = "Concrete pour QC for ${elementId.ifBlank { "element" }} — " +
                (if (passed) "all acceptance criteria met" else "one or more criteria failed")
        )
    }

    // ==================================================================
    // REBAR
    // ==================================================================
    fun rebarQc(
        code: CalculatorEngine.DesignCode,
        barDiameterMm: Double,
        numBars: Int,
        fyMpa: Double,
        fcuMpa: Double,
        coverMm: Double,
        beamWidthMm: Double,
        effectiveDepthMm: Double,
        elementType: String = "BEAM"
    ): QcOutcome {
        val n = numBars.coerceAtLeast(1)
        val barArea = PI * barDiameterMm * barDiameterMm / 4.0
        val asTotal = n * barArea
        val b = beamWidthMm.coerceAtLeast(1.0)
        val d = effectiveDepthMm.coerceAtLeast(1.0)
        val rho = asTotal / (b * d)

        val fc = if (code == CalculatorEngine.DesignCode.EGYPTIAN) fcuMpa else fcuMpa * CUBE_TO_CYLINDER
        val (k1, k2) = if (code == CalculatorEngine.DesignCode.EGYPTIAN) 0.225 to 1.3 else 0.25 to 1.4
        val rhoMin = maxOf(k1 * sqrt(fc) / fyMpa, k2 / fyMpa)
        val beta1 = if (fc <= 28.0) 0.85 else (0.85 - 0.05 * (fc - 28.0) / 7.0).coerceAtLeast(0.65)
        val rhoBal = 0.85 * beta1 * (fc / fyMpa) * (600.0 / (600.0 + fyMpa))
        val rhoMax = 0.75 * rhoBal

        val element = when (elementType.uppercase()) {
            "SLAB" -> ElementType.SLAB
            "COLUMN" -> ElementType.COLUMN
            "FOUNDATION" -> ElementType.FOUNDATION
            else -> ElementType.BEAM
        }
        val coverLimit = QcLimits.minCover(element, code)
        val minClearSpacing = maxOf(barDiameterMm, 25.0)
        val clearSpacing = if (n > 1) (b - 2 * coverMm - n * barDiameterMm) / (n - 1) else Double.POSITIVE_INFINITY

        val checks = listOf(
            GenericSafetyCheck("Reinforcement ratio ρ ≥ ρ_min", rho * 100.0, rhoMin * 100.0, "%",
                rho >= rhoMin, "ACI 318-19 §25.4.2.2 / ECP 203 §4.2"),
            GenericSafetyCheck("Reinforcement ratio ρ ≤ 0.75·ρ_bal", rho * 100.0, rhoMax * 100.0, "%",
                rho <= rhoMax, "ACI 318-19 §25.4.2.2"),
            GenericSafetyCheck("Cover ≥ required", coverMm, coverLimit.value, "mm",
                coverMm >= coverLimit.value, coverLimit.reference),
            GenericSafetyCheck("Clear spacing ≥ max(d_b, 25 mm)", if (n > 1) clearSpacing else barDiameterMm,
                minClearSpacing, "mm", if (n > 1) clearSpacing >= minClearSpacing else true, "ACI 318-19 §25.2.1")
        )

        val passed = checks.all { it.passed }
        val utilization = if (rhoMax > 0) rho / rhoMax else 0.0
        val details = listOf(
            "Bar diameter" to "%.0f mm".format(barDiameterMm),
            "Number of bars" to n.toString(),
            "Provided area A_s" to "%.0f mm²".format(asTotal),
            "fy" to "%.0f MPa".format(fyMpa),
            "f'cu" to "%.1f MPa".format(fcuMpa),
            "Element type" to elementType,
            "ρ provided" to "%.3f %%".format(rho * 100.0),
            "ρ_min" to "%.3f %%".format(rhoMin * 100.0),
            "ρ_max" to "%.3f %%".format(rhoMax * 100.0),
            "Required cover" to "%.0f mm".format(coverLimit.value)
        )
        return QcOutcome(
            title = "Reinforcement Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = utilization,
            summary = "Rebar detail check — " + (if (passed) "compliant" else "review required")
        )
    }

    // ==================================================================
    // STEEL — uses SteelTables (111 sections) instead of SteelDictionary
    // ==================================================================
    fun steelQc(
        code: CalculatorEngine.DesignCode,
        sectionKey: String,
        fyMpa: Double,
        spanM: Double,
        muKnM: Double,
        vuKn: Double
    ): QcOutcome {
        val sec = SteelTables.getSectionByName(sectionKey)
        if (sec == null) {
            val check = GenericSafetyCheck("Section geometry available", 0.0, 1.0, "-",
                false, "AISC 360-22 §B4")
            return QcOutcome(
                title = "Steel Structure Quality Control",
                checks = listOf(check),
                details = listOf("Section" to sectionKey, "Note" to "Section not found in SteelTables"),
                passed = false,
                utilizationRatio = 0.0,
                summary = "Steel section check unavailable for $sectionKey"
            )
        }

        val h = sec.depth
        val bf = sec.width
        val tw = sec.tw
        val tf = sec.tf
        val fy = fyMpa.coerceAtLeast(1.0)
        val Aw = h * tw
        val Ix = (bf * h.pow(3) - (bf - tw) * (h - 2 * tf).pow(3)) / 12.0
        val Wx = Ix / (h / 2.0)
        val Zx = bf * tf * (h - tf) + tw * (h - 2 * tf).pow(2) / 4.0
        val phiB = 0.90
        val phiV = 1.00
        val mn = phiB * fy * Zx / 1e6
        val vn = phiV * 0.60 * fy * Aw / 1e3

        val lambdaF = bf / (2 * tf)
        val lambdaW = (h - 2 * tf) / tw
        val lambdaPf = 0.38 * sqrt(STEEL_E / fy)
        val lambdaPw = 3.76 * sqrt(STEEL_E / fy)

        val checks = listOf(
            GenericSafetyCheck("Flexural capacity φb·M_n ≥ M_u", muKnM, mn, "kN·m",
                muKnM <= mn, "AISC 360-22 §F2"),
            GenericSafetyCheck("Shear capacity φv·V_n ≥ V_u", vuKn, vn, "kN",
                vuKn <= vn, "AISC 360-22 §G2"),
            GenericSafetyCheck("Flange compact (λf ≤ λpf)", lambdaF, lambdaPf, "-",
                lambdaF <= lambdaPf, "AISC 360-22 Table B4.1"),
            GenericSafetyCheck("Web compact (λw ≤ λpw)", lambdaW, lambdaPw, "-",
                lambdaW <= lambdaPw, "AISC 360-22 Table B4.1")
        )

        val passed = checks.all { it.passed }
        val utilization = maxOf(
            if (mn > 0) muKnM / mn else 0.0,
            if (vn > 0) vuKn / vn else 0.0
        )
        val details = listOf(
            "Section" to sectionKey,
            "Section modulus W_x" to "%.0f cm³".format(Wx / 1e3),
            "Plastic modulus Z_x" to "%.0f cm³".format(Zx / 1e3),
            "fy" to "%.0f MPa".format(fy),
            "Span" to "%.2f m".format(spanM),
            "Design moment M_u" to "%.1f kN·m".format(muKnM),
            "Design shear V_u" to "%.1f kN".format(vuKn),
            "φb·M_n" to "%.1f kN·m".format(mn),
            "φv·V_n" to "%.1f kN".format(vn)
        )
        return QcOutcome(
            title = "Steel Structure Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = utilization,
            summary = "Steel member $sectionKey — " + (if (passed) "adequate" else "inadequate")
        )
    }

    // ==================================================================
    // ROADS — surface/base/subbase, density, compaction, air voids
    // ==================================================================
    fun roadsQc(
        surfaceThicknessMm: Double,
        baseThicknessMm: Double,
        subbaseThicknessMm: Double,
        compactionPercent: Double,
        asphaltDensityKnM3: Double,
        airVoidsPercent: Double,
        gmb: Double = 2.35,
        gmm: Double = 2.45
    ): QcOutcome {
        val va = if (airVoidsPercent > 0) airVoidsPercent
            else if (gmm > 0) (1.0 - gmb / gmm) * 100.0 else 0.0

        val checks = listOf(
            GenericSafetyCheck("Surface course 40–60 mm", surfaceThicknessMm, 60.0, "mm",
                surfaceThicknessMm in 40.0..60.0, "Egyptian Roads Specs"),
            GenericSafetyCheck("Base course ≥ 150 mm", baseThicknessMm, 150.0, "mm",
                baseThicknessMm >= 150.0, "Egyptian Roads Specs"),
            GenericSafetyCheck("Sub-base course ≥ 150 mm", subbaseThicknessMm, 150.0, "mm",
                subbaseThicknessMm >= 150.0, "Egyptian Roads Specs"),
            GenericSafetyCheck("Field compaction ≥ 95%", compactionPercent, 95.0, "%",
                compactionPercent >= 95.0, "AASHTO T99 / ASTM D698"),
            GenericSafetyCheck("Asphalt bulk density ≥ 22.0 kN/m³", asphaltDensityKnM3, 22.0, "kN/m³",
                asphaltDensityKnM3 >= 22.0, "ASTM D2726"),
            GenericSafetyCheck("Air voids 3–5% (Marshall)", va, 5.0, "%",
                va in 3.0..5.0, "ASTM D3203")
        )

        val passed = checks.all { it.passed }
        val passedCount = checks.count { it.passed }
        val details = listOf(
            "Surface course" to "%.0f mm".format(surfaceThicknessMm),
            "Base course" to "%.0f mm".format(baseThicknessMm),
            "Sub-base course" to "%.0f mm".format(subbaseThicknessMm),
            "Compaction" to "%.1f %%".format(compactionPercent),
            "Asphalt density" to "%.2f kN/m³".format(asphaltDensityKnM3),
            "Air voids (Va)" to "%.1f %%".format(va)
        )
        return QcOutcome(
            title = "Roads Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = passedCount.toDouble() / checks.size,
            summary = "Pavement QC — $passedCount/${checks.size} acceptance criteria met"
        )
    }

    // ==================================================================
    // BRIDGES
    // ==================================================================
    fun bridgeQc(
        code: CalculatorEngine.DesignCode,
        concreteStrengthMpa: Double,
        spanM: Double,
        naturalFrequencyHz: Double
    ): QcOutcome {
        val minConcrete = 30.0
        val deflection = if (naturalFrequencyHz > 0) (15.76 / naturalFrequencyHz).pow(2) else Double.POSITIVE_INFINITY
        val allowableDeflection = spanM * 1000.0 / 250.0

        val checks = listOf(
            GenericSafetyCheck("Concrete strength ≥ 30 MPa", concreteStrengthMpa, minConcrete, "MPa",
                concreteStrengthMpa >= minConcrete, "ECP 203 / AASHTO LRFD"),
            GenericSafetyCheck("Natural frequency ≥ 2.0 Hz", naturalFrequencyHz, 2.0, "Hz",
                naturalFrequencyHz >= 2.0, "AASHTO LRFD §10"),
            GenericSafetyCheck("Total deflection ≤ L/250", deflection, allowableDeflection, "mm",
                deflection <= allowableDeflection, "AASHTO LRFD Table 2.5.2.6.3-1")
        )

        val passed = checks.all { it.passed }
        val utilization = if (allowableDeflection > 0) deflection / allowableDeflection else 0.0
        val details = listOf(
            "Concrete class" to "%.1f MPa".format(concreteStrengthMpa),
            "Span" to "%.2f m".format(spanM),
            "Natural frequency" to "%.2f Hz".format(naturalFrequencyHz),
            "Est. deflection δ" to if (deflection.isFinite()) "%.2f mm".format(deflection) else "-",
            "Allowable L/250" to "%.2f mm".format(allowableDeflection)
        )
        return QcOutcome(
            title = "Bridges Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = utilization,
            summary = "Bridge QC — " + (if (passed) "acceptable" else "review required")
        )
    }

    // ==================================================================
    // TUNNELS
    // ==================================================================
    fun tunnelQc(
        rockClass: String,
        convergenceMm: Double,
        diameterM: Double,
        supportThicknessMm: Double
    ): QcOutcome {
        val minSupport = when (rockClass.trim().uppercase()) {
            "I" -> 50.0
            "II" -> 75.0
            "III" -> 100.0
            "IV" -> 150.0
            else -> 200.0
        }
        val diameterMm = diameterM * 1000.0
        val convRatio = if (diameterMm > 0) convergenceMm / diameterMm * 100.0 else Double.POSITIVE_INFINITY

        val checks = listOf(
            GenericSafetyCheck("Convergence ratio ≤ 1.0% of diameter", convRatio, 1.0, "%",
                convRatio <= 1.0, "Convergence-Confinement Method"),
            GenericSafetyCheck("Support thickness ≥ class minimum", supportThicknessMm, minSupport, "mm",
                supportThicknessMm >= minSupport, "NFPA 502 / ISRM")
        )

        val passed = checks.all { it.passed }
        val utilization = if (1.0 > 0) convRatio / 1.0 else 0.0
        val details = listOf(
            "Rock class" to rockClass,
            "Tunnel diameter" to "%.2f m".format(diameterM),
            "Measured convergence" to "%.1f mm".format(convergenceMm),
            "Convergence ratio" to if (convRatio.isFinite()) "%.2f %%".format(convRatio) else "-",
            "Support thickness" to "%.0f mm".format(supportThicknessMm),
            "Required minimum" to "%.0f mm".format(minSupport)
        )
        return QcOutcome(
            title = "Tunnels Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = utilization,
            summary = "Tunnel support QC (rock class $rockClass) — " + (if (passed) "stable" else "attention required")
        )
    }

    // ==================================================================
    // BUILDINGS
    // ==================================================================
    fun buildingQc(
        code: CalculatorEngine.DesignCode,
        numFloors: Int,
        floorHeightM: Double,
        totalWeightKn: Double,
        importanceFactor: Double,
        sds: Double,
        responseR: Double,
        storyDrift: Double
    ): QcOutcome {
        val hn = numFloors * floorHeightM
        val ta = 0.0466 * hn.pow(0.9)
        val ie = importanceFactor.coerceAtLeast(1.0)
        val r = responseR.coerceAtLeast(1.0)
        val csRaw = sds * ie / r
        val csMin = maxOf(0.044 * sds * ie, 0.01)
        val csMax = if (ta > 0) sds * ie / (r * ta) else csRaw
        val cs = csRaw.coerceIn(csMin, csMax)
        val baseShear = cs * totalWeightKn
        val allowableDrift = 0.020

        val checks = listOf(
            GenericSafetyCheck("Story drift ≤ 2.0% (allowable)", storyDrift * 100.0, allowableDrift * 100.0, "%",
                storyDrift <= allowableDrift, "ASCE 7-16 Table 12.12-1"),
            GenericSafetyCheck("Base shear coefficient C_s ≥ C_s,min", cs, csMin, "-",
                cs >= csMin, "ASCE 7-16 §12.8.1.1"),
            GenericSafetyCheck("Fundamental period T_a ≥ 0.1 s", ta, 0.1, "s",
                ta >= 0.1, "ASCE 7-16 §12.8.2"),
            GenericSafetyCheck("Structure height ≤ 100 m (ELF applicability)", hn, 100.0, "m",
                hn <= 100.0, "ASCE 7-16 §12.8.2")
        )

        val passed = checks.all { it.passed }
        val utilization = storyDrift / allowableDrift
        val details = listOf(
            "Building type floors" to numFloors.toString(),
            "Typical floor height" to "%.2f m".format(floorHeightM),
            "Total height h_n" to "%.2f m".format(hn),
            "Seismic weight W" to "%.0f kN".format(totalWeightKn),
            "Importance factor I_e" to "%.2f".format(ie),
            "Design spectral accel. S_DS" to "%.3f g".format(sds),
            "Response factor R" to "%.1f".format(r),
            "Fundamental period T_a" to "%.3f s".format(ta),
            "Base shear coefficient C_s" to "%.4f".format(cs),
            "Base shear V" to "%.0f kN".format(baseShear)
        )
        return QcOutcome(
            title = "Buildings / High-Rise / Hospitals Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = utilization,
            summary = "Building seismic/serviceability QC — " + (if (passed) "compliant" else "review required")
        )
    }
}