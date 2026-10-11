package com.civileg.app.utils

import com.civileg.app.domain.entities.GenericSafetyCheck
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * QualityControlEngine
 *
 * Code-referenced site quality-control checks for the Quality Engineer module.
 *
 * Every check returns a [GenericSafetyCheck] whose `calculated` / `limit` values
 * are in the stated unit, plus a human readable [QcOutcome] used by the UI and
 * the English PDF report.
 *
 * References
 *  • Concrete   : ACI 318-19 §26.12 (Table 26.12.1.3 slump, §26.12.3.1 acceptance) / ECP 203-2020 §6-3
 *  • Rebar      : ACI 318-19 §9.6.1.2 (ρmin), §9.3.2 (ρmax), §20.5.1.3 (cover), §25.2.1 (clear spacing) / ECP 203-2020 §4-2
 *  • Steel      : AISC 360-16 §B4 (classification), §F2 (flexure), §G2 (shear) / ECP 205-2007
 *  • Roads      : AASHTO / Egyptian Road Specs — Marshall air voids, layer compaction
 *  • Bridges    : ECP 203-2020 / AASHTO LRFD — minimum concrete class, deflection L/250, frequency comfort
 *  • Tunnels    : Convergence-confinement practice — convergence ratio, shotcrete thickness by rock class
 *  • Buildings  : ASCE 7-16 §12.8 (period & base shear), Table 12.12-1 (drift) / ECP 201-2012
 */
object QualityControlEngine {

    /** Emission (MPa) used for the cube→cylinder conversion f'c ≈ 0.8·fcu (ECP/ACI). */
    private const val CUBE_TO_CYLINDER = 0.8
    private const val STEEL_E = 200_000.0 // MPa

    data class QcOutcome(
        val title: String,
        val checks: List<GenericSafetyCheck>,
        val details: List<Pair<String, String>>,
        val passed: Boolean,
        val utilizationRatio: Double,
        val summary: String
    )

    // ==================================================================
    // Discipline keys
    // ==================================================================
    object Category {
        const val CONCRETE = "CONCRETE"
        const val SOIL = "SOIL"
        const val REBAR = "REBAR"
        const val STEEL = "STEEL"
        const val ROADS = "ROADS"
        const val BRIDGES = "BRIDGES"
        const val TUNNELS = "TUNNELS"
        const val BUILDINGS = "BUILDINGS"
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
        strength28DaysMpa: Double
    ): QcOutcome {
        val fcu = fcuDesignMpa.coerceAtLeast(1.0)
        val lowerAccept = fcu - 3.5          // ACI 318-19 Table 26.12.3.1 (f'c ≤ 35 MPa)
        val ratio = if (strength28DaysMpa > 0) strength7DaysMpa / strength28DaysMpa else 0.0

        val checks = listOf(
            GenericSafetyCheck(
                name = "Slump within code range (25–100 mm)",
                calculated = slumpMm,
                limit = 100.0,
                unit = "mm",
                passed = slumpMm in 25.0..100.0
            ),
            GenericSafetyCheck(
                name = "28-day strength ≥ f'cu",
                calculated = strength28DaysMpa,
                limit = fcu,
                unit = "MPa",
                passed = strength28DaysMpa >= fcu
            ),
            GenericSafetyCheck(
                name = "Individual strength ≥ f'cu − 3.5 MPa",
                calculated = strength28DaysMpa,
                limit = lowerAccept,
                unit = "MPa",
                passed = strength28DaysMpa >= lowerAccept
            ),
            GenericSafetyCheck(
                name = "7/28-day strength ratio ≥ 65%",
                calculated = ratio * 100.0,
                limit = 65.0,
                unit = "%",
                passed = ratio >= 0.65
            )
        )

        val passed = checks.all { it.passed }
        val utilization = if (fcu > 0) strength28DaysMpa / fcu else 0.0
        val details = listOf(
            "Element" to elementId.ifBlank { "-" },
            "Design f'cu" to "%.1f MPa".format(fcu),
            "Concrete volume" to "%.3f m³".format(volumeM3),
            "Slump" to "%.0f mm".format(slumpMm),
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
        effectiveDepthMm: Double
    ): QcOutcome {
        val n = numBars.coerceAtLeast(1)
        val barArea = PI * barDiameterMm * barDiameterMm / 4.0
        val asTotal = n * barArea
        val b = beamWidthMm.coerceAtLeast(1.0)
        val d = effectiveDepthMm.coerceAtLeast(1.0)
        val rho = asTotal / (b * d)

        // ρmin / ρmax — ECP 203-2020 vs ACI 318-19 / SBC 304
        val fc = if (code == CalculatorEngine.DesignCode.EGYPTIAN) fcuMpa else fcuMpa * CUBE_TO_CYLINDER
        val (k1, k2) = if (code == CalculatorEngine.DesignCode.EGYPTIAN) 0.225 to 1.3 else 0.25 to 1.4
        val rhoMin = maxOf(k1 * sqrt(fc) / fyMpa, k2 / fyMpa)
        val beta1 = if (fc <= 28.0) 0.85 else (0.85 - 0.05 * (fc - 28.0) / 7.0).coerceAtLeast(0.65)
        val rhoBal = 0.85 * beta1 * (fc / fyMpa) * (600.0 / (600.0 + fyMpa))
        val rhoMax = 0.75 * rhoBal

        val requiredCover = 40.0 // ECP 203-2020 / ACI 318-19 — beams, mild exposure
        val minClearSpacing = maxOf(barDiameterMm, 25.0) // ACI 318-19 §25.2.1
        val clearSpacing = if (n > 1) (b - 2 * coverMm - n * barDiameterMm) / (n - 1) else Double.POSITIVE_INFINITY

        val checks = listOf(
            GenericSafetyCheck(
                name = "Reinforcement ratio ρ ≥ ρ_min",
                calculated = rho * 100.0,
                limit = rhoMin * 100.0,
                unit = "%",
                passed = rho >= rhoMin
            ),
            GenericSafetyCheck(
                name = "Reinforcement ratio ρ ≤ 0.75·ρ_bal",
                calculated = rho * 100.0,
                limit = rhoMax * 100.0,
                unit = "%",
                passed = rho <= rhoMax
            ),
            GenericSafetyCheck(
                name = "Cover ≥ required (40 mm)",
                calculated = coverMm,
                limit = requiredCover,
                unit = "mm",
                passed = coverMm >= requiredCover
            ),
            GenericSafetyCheck(
                name = "Clear spacing ≥ max(d_b, 25 mm)",
                calculated = if (n > 1) clearSpacing else barDiameterMm,
                limit = minClearSpacing,
                unit = "mm",
                passed = if (n > 1) clearSpacing >= minClearSpacing else true
            )
        )

        val passed = checks.all { it.passed }
        val utilization = if (rhoMax > 0) rho / rhoMax else 0.0
        val details = listOf(
            "Bar diameter" to "%.0f mm".format(barDiameterMm),
            "Number of bars" to n.toString(),
            "Provided area A_s" to "%.0f mm²".format(asTotal),
            "fy" to "%.0f MPa".format(fyMpa),
            "f'cu" to "%.1f MPa".format(fcuMpa),
            "ρ provided" to "%.3f %%".format(rho * 100.0),
            "ρ_min" to "%.3f %%".format(rhoMin * 100.0),
            "ρ_max" to "%.3f %%".format(rhoMax * 100.0)
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
    // STEEL
    // ==================================================================
    fun steelQc(
        code: CalculatorEngine.DesignCode,
        sectionKey: String,
        fyMpa: Double,
        spanM: Double,
        muKnM: Double,
        vuKn: Double
    ): QcOutcome {
        val sec = SteelDictionary.egyptianSections[sectionKey]
            ?: SteelDictionary.americanSections[sectionKey]
            ?: SteelDictionary.saudiSections[sectionKey]

        val dims = sec?.let { parseIhDims(it.dimensions) }
        if (sec == null || dims == null) {
            val check = GenericSafetyCheck(
                name = "Section geometry available",
                calculated = 0.0,
                limit = 1.0,
                unit = "-",
                passed = false
            )
            return QcOutcome(
                title = "Steel Structure Quality Control",
                checks = listOf(check),
                details = listOf("Section" to sectionKey, "Note" to "Geometry not resolvable for this profile"),
                passed = false,
                utilizationRatio = 0.0,
                summary = "Steel section check unavailable for $sectionKey"
            )
        }

        val (h, bf, tw, tf) = dims // mm
        val fy = fyMpa.coerceAtLeast(1.0)
        val Aw = h * tw                    // mm²  shear area (web)
        val Ix = (bf * h.pow(3) - (bf - tw) * (h - 2 * tf).pow(3)) / 12.0 // mm⁴
        val Wx = Ix / (h / 2.0)            // mm³ elastic
        val Zx = bf * tf * (h - tf) + tw * (h - 2 * tf).pow(2) / 4.0      // mm³ plastic
        val phiB = 0.90
        val phiV = 1.00
        val mn = phiB * fy * Zx / 1e6      // kN·m
        val vn = phiV * 0.60 * fy * Aw / 1e3 // kN

        // Compactness (AISC 360-16 Table B4.1)
        val lambdaF = bf / (2 * tf)
        val lambdaW = (h - 2 * tf) / tw
        val lambdaPf = 0.38 * sqrt(STEEL_E / fy)
        val lambdaPw = 3.76 * sqrt(STEEL_E / fy)

        val checks = listOf(
            GenericSafetyCheck(
                name = "Flexural capacity φb·M_n ≥ M_u",
                calculated = muKnM,
                limit = mn,
                unit = "kN·m",
                passed = muKnM <= mn
            ),
            GenericSafetyCheck(
                name = "Shear capacity φv·V_n ≥ V_u",
                calculated = vuKn,
                limit = vn,
                unit = "kN",
                passed = vuKn <= vn
            ),
            GenericSafetyCheck(
                name = "Flange compact (λf ≤ λpf)",
                calculated = lambdaF,
                limit = lambdaPf,
                unit = "-",
                passed = lambdaF <= lambdaPf
            ),
            GenericSafetyCheck(
                name = "Web compact (λw ≤ λpw)",
                calculated = lambdaW,
                limit = lambdaPw,
                unit = "-",
                passed = lambdaW <= lambdaPw
            )
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

    /** Parse "h=200mm, b=100mm, tw=5.6mm, tf=8.5mm" (also accepts bf=). */
    private fun parseIhDims(desc: String): List<Double>? {
        val regex = Regex("""h\s*=\s*([\d.]+)mm.*?b(?:f)?\s*=\s*([\d.]+)mm.*?tw\s*=\s*([\d.]+)mm.*?tf\s*=\s*([\d.]+)mm""")
        val m = regex.find(desc) ?: return null
        return m.groupValues.drop(1).mapNotNull { it.toDoubleOrNull() }.takeIf { it.size == 4 }
    }

    // ==================================================================
    // ROADS
    // ==================================================================
    fun roadsQc(
        surfaceThicknessMm: Double,
        baseThicknessMm: Double,
        subbaseThicknessMm: Double,
        compactionPercent: Double,
        asphaltDensityKnM3: Double,
        airVoidsPercent: Double
    ): QcOutcome {
        val checks = listOf(
            GenericSafetyCheck(
                name = "Surface course 40–60 mm",
                calculated = surfaceThicknessMm,
                limit = 60.0,
                unit = "mm",
                passed = surfaceThicknessMm in 40.0..60.0
            ),
            GenericSafetyCheck(
                name = "Base course ≥ 150 mm",
                calculated = baseThicknessMm,
                limit = 150.0,
                unit = "mm",
                passed = baseThicknessMm >= 150.0
            ),
            GenericSafetyCheck(
                name = "Sub-base course ≥ 150 mm",
                calculated = subbaseThicknessMm,
                limit = 150.0,
                unit = "mm",
                passed = subbaseThicknessMm >= 150.0
            ),
            GenericSafetyCheck(
                name = "Field compaction ≥ 95%",
                calculated = compactionPercent,
                limit = 95.0,
                unit = "%",
                passed = compactionPercent >= 95.0
            ),
            GenericSafetyCheck(
                name = "Asphalt bulk density ≥ 22.0 kN/m³",
                calculated = asphaltDensityKnM3,
                limit = 22.0,
                unit = "kN/m³",
                passed = asphaltDensityKnM3 >= 22.0
            ),
            GenericSafetyCheck(
                name = "Air voids 3–5% (Marshall)",
                calculated = airVoidsPercent,
                limit = 5.0,
                unit = "%",
                passed = airVoidsPercent in 3.0..5.0
            )
        )
        val passed = checks.all { it.passed }
        val passedCount = checks.count { it.passed }
        val details = listOf(
            "Surface course" to "%.0f mm".format(surfaceThicknessMm),
            "Base course" to "%.0f mm".format(baseThicknessMm),
            "Sub-base course" to "%.0f mm".format(subbaseThicknessMm),
            "Compaction" to "%.1f %%".format(compactionPercent),
            "Asphalt density" to "%.2f kN/m³".format(asphaltDensityKnM3),
            "Air voids" to "%.1f %%".format(airVoidsPercent)
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
        val minConcrete = 30.0 // ECP 203-2020 / AASHTO — minimum bridge deck concrete class
        val deflection = if (naturalFrequencyHz > 0) (15.76 / naturalFrequencyHz).pow(2) else Double.POSITIVE_INFINITY
        val allowableDeflection = spanM * 1000.0 / 250.0 // L/250 vehicular (mm)

        val checks = listOf(
            GenericSafetyCheck(
                name = "Concrete strength ≥ 30 MPa",
                calculated = concreteStrengthMpa,
                limit = minConcrete,
                unit = "MPa",
                passed = concreteStrengthMpa >= minConcrete
            ),
            GenericSafetyCheck(
                name = "Natural frequency ≥ 2.0 Hz",
                calculated = naturalFrequencyHz,
                limit = 2.0,
                unit = "Hz",
                passed = naturalFrequencyHz >= 2.0
            ),
            GenericSafetyCheck(
                name = "Total deflection ≤ L/250",
                calculated = deflection,
                limit = allowableDeflection,
                unit = "mm",
                passed = deflection <= allowableDeflection
            )
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
            else -> 200.0 // V
        }
        val diameterMm = diameterM * 1000.0
        val convRatio = if (diameterMm > 0) convergenceMm / diameterMm * 100.0 else Double.POSITIVE_INFINITY

        val checks = listOf(
            GenericSafetyCheck(
                name = "Convergence ratio ≤ 1.0% of diameter",
                calculated = convRatio,
                limit = 1.0,
                unit = "%",
                passed = convRatio <= 1.0
            ),
            GenericSafetyCheck(
                name = "Support thickness ≥ class minimum",
                calculated = supportThicknessMm,
                limit = minSupport,
                unit = "mm",
                passed = supportThicknessMm >= minSupport
            )
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
    // BUILDINGS / HIGH-RISE / HOSPITALS
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
        // ASCE 7-16 §12.8.2.1 — approximate fundamental period (RC moment frame)
        val ta = 0.0466 * hn.pow(0.9)
        // ASCE 7-16 §12.8.1.1 — seismic response coefficient
        val ie = importanceFactor.coerceAtLeast(1.0)
        val r = responseR.coerceAtLeast(1.0)
        val csRaw = sds * ie / r
        val csMin = maxOf(0.044 * sds * ie, 0.01)
        val csMax = if (ta > 0) sds * ie / (r * ta) else csRaw
        val cs = csRaw.coerceIn(csMin, csMax)
        val baseShear = cs * totalWeightKn
        val allowableDrift = 0.020 // ASCE 7-16 Table 12.12-1 — Risk Category I/II

        val checks = listOf(
            GenericSafetyCheck(
                name = "Story drift ≤ 2.0% (allowable)",
                calculated = storyDrift * 100.0,
                limit = allowableDrift * 100.0,
                unit = "%",
                passed = storyDrift <= allowableDrift
            ),
            GenericSafetyCheck(
                name = "Base shear coefficient C_s ≥ C_s,min",
                calculated = cs,
                limit = csMin,
                unit = "-",
                passed = cs >= csMin
            ),
            GenericSafetyCheck(
                name = "Fundamental period T_a ≥ 0.1 s",
                calculated = ta,
                limit = 0.1,
                unit = "s",
                passed = ta >= 0.1
            ),
            GenericSafetyCheck(
                name = "Structure height ≤ 100 m (ELF applicability)",
                calculated = hn,
                limit = 100.0,
                unit = "m",
                passed = hn <= 100.0
            )
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
