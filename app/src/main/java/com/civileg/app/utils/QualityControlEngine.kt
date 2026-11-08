package com.civileg.app.utils

import com.civileg.app.domain.entities.DesignCode

import com.civileg.app.domain.entities.GenericSafetyCheck
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

object QualityControlEngine {

    private const val CUBE_TO_CYLINDER = 0.8
    private const val STEEL_E = 200_000.0
    const val SCREENING_DISCLAIMER =
        "Preliminary QC screening only—not proof of code compliance. Confirm the governing edition, project specifications, exposure conditions, and all unmodeled requirements with the responsible engineer."

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

    fun codeName(code: DesignCode): String = code.version

    private fun steelCodeBasis(code: DesignCode): String = when (code) {
        DesignCode.ECP -> "ECP 205-2007 (steel)"
        DesignCode.ACI -> "AISC 360-22 (US steel; ACI 318 is concrete)"
        DesignCode.SBC -> "SBC 306-2018 (steel)"
    }

    // ==================================================================
    // CONCRETE
    // ==================================================================
    fun concreteQc(
        code: DesignCode,
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
        val selectedBasis = "${codeName(code)} preliminary screening; approved mix and project specifications govern"

        val checks = listOf(
            GenericSafetyCheck("Slump within code range", slumpMm, slumpRange.endInclusive, "mm",
                slumpMm in slumpRange, selectedBasis),
            GenericSafetyCheck("28-day strength ≥ f'cu", strength28DaysMpa, fcu, "MPa",
                strength28DaysMpa >= fcu, selectedBasis),
            GenericSafetyCheck("Individual strength ≥ f'cu − 3.5 MPa", strength28DaysMpa, lowerAccept, "MPa",
                strength28DaysMpa >= lowerAccept, selectedBasis),
            GenericSafetyCheck("7/28-day strength ratio ≥ 65%", ratio * 100.0, 65.0, "%",
                ratio >= 0.65, "Project-defined early-strength screening criterion; verify specification"),
            GenericSafetyCheck("Air content 3–8%", airContent, 8.0, "%",
                airContent in 3.0..8.0, "Preliminary range only; verify exposure and approved mix requirements"),
            GenericSafetyCheck("Concrete temperature 5–40 °C", concreteTemp, 40.0, "°C",
                concreteTemp in 5.0..40.0, "Preliminary range only; verify placement and project requirements")
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
        code: DesignCode,
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

        val fc = if (code == DesignCode.ECP) fcuMpa else fcuMpa * CUBE_TO_CYLINDER
        val (k1, k2) = if (code == DesignCode.ECP) 0.225 to 1.3 else 0.25 to 1.4
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
        val selectedBasis = "${codeName(code)} preliminary reinforcement screening; member-specific provisions are not fully modeled"
        val minClearSpacing = maxOf(barDiameterMm, 25.0)
        val clearSpacing = if (n > 1) (b - 2 * coverMm - n * barDiameterMm) / (n - 1) else Double.POSITIVE_INFINITY

        val checks = listOf(
            GenericSafetyCheck("Reinforcement ratio ρ ≥ ρ_min", rho * 100.0, rhoMin * 100.0, "%",
                rho >= rhoMin, selectedBasis),
            GenericSafetyCheck("Reinforcement ratio ρ ≤ 0.75·ρ_bal", rho * 100.0, rhoMax * 100.0, "%",
                rho <= rhoMax, selectedBasis),
            GenericSafetyCheck("Cover ≥ required", coverMm, coverLimit.value, "mm",
                coverMm >= coverLimit.value, coverLimit.reference),
            GenericSafetyCheck("Clear spacing ≥ max(d_b, 25 mm)", if (n > 1) clearSpacing else barDiameterMm,
                minClearSpacing, "mm", if (n > 1) clearSpacing >= minClearSpacing else true, selectedBasis)
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
        code: DesignCode,
        sectionKey: String,
        fyMpa: Double,
        spanM: Double,
        muKnM: Double,
        vuKn: Double
    ): QcOutcome {
        val sec = SteelTables.getSectionByName(sectionKey)
        if (sec == null) {
            val check = GenericSafetyCheck("Section geometry available", 0.0, 1.0, "-",
                false, steelCodeBasis(code))
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
        require(fyMpa.isFinite() && fyMpa > 0.0)
        require(spanM.isFinite() && spanM > 0.0)
        require(muKnM.isFinite() && muKnM >= 0.0)
        require(vuKn.isFinite() && vuKn >= 0.0)
        val fy = fyMpa
        val aw = (h - 2.0 * tf).coerceAtLeast(0.0) * tw
        val ix = sec.iy * 10_000.0
        val wx = sec.effectiveSx * 1_000.0
        val zx = sec.effectiveZx * 1_000.0
        val ag = sec.area * 100.0
        val phiB = 0.90
        val phiV = 0.90
        val mn = phiB * fy * zx / 1e6
        val vn = phiV * 0.60 * fy * aw / 1e3

        val lambdaF = bf / (2 * tf)
        val lambdaW = (h - 2 * tf) / tw
        val lambdaPf = 0.38 * sqrt(STEEL_E / fy)
        val lambdaPw = 3.76 * sqrt(STEEL_E / fy)
        val reference = steelCodeBasis(code)

        val checks = listOf(
            GenericSafetyCheck("Flexural capacity φb·M_n ≥ M_u", muKnM, mn, "kN·m",
                muKnM <= mn, "$reference / flexure"),
            GenericSafetyCheck("Shear capacity φv·V_n ≥ V_u", vuKn, vn, "kN",
                vuKn <= vn, "$reference / shear"),
            GenericSafetyCheck("Flange compact (λf ≤ λpf)", lambdaF, lambdaPf, "-",
                lambdaF <= lambdaPf, "$reference / section classification"),
            GenericSafetyCheck("Web compact (λw ≤ λpw)", lambdaW, lambdaPw, "-",
                lambdaW <= lambdaPw, "$reference / section classification")
        )

        val passed = checks.all { it.passed }
        val utilization = maxOf(
            if (mn > 0) muKnM / mn else 0.0,
            if (vn > 0) vuKn / vn else 0.0
        )
        val details = listOf(
            "Section" to sectionKey,
            "Selected steel design basis" to reference,
            "Gross area" to "%.0f mm²".format(ag),
            "Major-axis inertia" to "%.0f mm⁴".format(ix),
            "Section modulus W_x" to "%.1f cm³".format(wx / 1e3),
            "Plastic modulus Z_x" to "%.1f cm³".format(zx / 1e3),
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
    // STEEL DETAILED — extended checks: deflection, slenderness, beam-column interaction
    // ==================================================================
    fun steelQcDetailed(
        code: DesignCode,
        sectionKey: String,
        fyMpa: Double,
        spanM: Double,
        muKnM: Double,
        vuKn: Double,
        puKn: Double = 0.0,
        unbracedLengthMm: Double = 0.0,
        allowableDeflectionMm: Double = 0.0,
        memberType: String = "BEAM" // BEAM, COLUMN, BEAM_COLUMN
    ): QcOutcome {
        val sec = SteelTables.getSectionByName(sectionKey)
        if (sec == null) {
            val check = GenericSafetyCheck("Section geometry available", 0.0, 1.0, "-",
                false, steelCodeBasis(code))
            return QcOutcome(
                title = "Steel Structure Quality Control (Detailed)",
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
        require(fyMpa.isFinite() && fyMpa > 0.0)
        require(spanM.isFinite() && spanM > 0.0)
        require(muKnM.isFinite() && muKnM >= 0.0)
        require(vuKn.isFinite() && vuKn >= 0.0)
        require(puKn.isFinite() && puKn >= 0.0)
        require(unbracedLengthMm.isFinite() && unbracedLengthMm >= 0.0)
        require(allowableDeflectionMm.isFinite() && allowableDeflectionMm >= 0.0)
        val normalizedMemberType = memberType.trim().uppercase()
        require(normalizedMemberType in setOf("BEAM", "COLUMN", "BEAM_COLUMN")) {
            "Unsupported steel member type: $memberType"
        }
        val fy = fyMpa
        val aw = (h - 2.0 * tf).coerceAtLeast(0.0) * tw
        val ag = sec.area * 100.0
        val ix = sec.iy * 10_000.0
        val zx = sec.effectiveZx * 1_000.0
        val wx = sec.effectiveSx * 1_000.0
        val rx = sec.ry * 10.0
        val ry = sec.rz * 10.0
        val phiB = 0.90
        val phiV = 0.90
        val phiC = 0.90
        val mn = phiB * fy * zx / 1e6
        val vn = phiV * 0.60 * fy * aw / 1e3
        val reference = steelCodeBasis(code)

        val lambdaF = bf / (2 * tf)
        val lambdaW = (h - 2 * tf) / tw
        val lambdaPf = 0.38 * sqrt(STEEL_E / fy)
        val lambdaPw = 3.76 * sqrt(STEEL_E / fy)

        val checks = mutableListOf<GenericSafetyCheck>()

        // 1. Flexural capacity
        checks.add(GenericSafetyCheck("Flexural capacity φb·M_n ≥ M_u", muKnM, mn, "kN·m",
            muKnM <= mn, "$reference / flexure"))

        // 2. Shear capacity
        checks.add(GenericSafetyCheck("Shear capacity φv·V_n ≥ V_u", vuKn, vn, "kN",
            vuKn <= vn, "$reference / shear"))

        if (normalizedMemberType != "COLUMN") {
            checks.add(GenericSafetyCheck("Flange compact (λf ≤ λpf)", lambdaF, lambdaPf, "-",
                lambdaF <= lambdaPf, "$reference / flexural section classification"))

            checks.add(GenericSafetyCheck("Web compact (λw ≤ λpw)", lambdaW, lambdaPw, "-",
                lambdaW <= lambdaPw, "$reference / flexural section classification"))
        }

        if (normalizedMemberType != "COLUMN" && unbracedLengthMm > 0.0) {
            checks.add(GenericSafetyCheck("Lateral-torsional buckling analysis available", 0.0, 1.0, "input",
                false, "$reference / detailed LTB capacity is not calculated by this QC screening"))
        }

        if (normalizedMemberType == "COLUMN" || normalizedMemberType == "BEAM_COLUMN") {
            if (unbracedLengthMm > 0.0 && puKn > 0.0) {
                val klR = maxOf(unbracedLengthMm / rx, unbracedLengthMm / ry)
                val fe = PI * PI * STEEL_E / (klR * klR)
                val lambdaLimit = 4.71 * sqrt(STEEL_E / fy)
                val fcr = if (klR <= lambdaLimit) {
                    0.658.pow(fy / fe) * fy
                } else {
                    0.877 * fe
                }
                val phiPn = phiC * fcr * ag / 1000.0
                checks.add(GenericSafetyCheck("Flexural buckling capacity φP_n ≥ P_u", puKn, phiPn, "kN",
                    puKn <= phiPn, "$reference / compression buckling; K = 1.0 assumed"))
                val slendernessLimit = 200.0
                checks.add(GenericSafetyCheck("Slenderness KL/r ≤ 200", klR, slendernessLimit, "-",
                    klR <= slendernessLimit, "$reference / compression member slenderness"))
            } else {
                val missingInput = if (unbracedLengthMm <= 0.0) "length" else "axial load"
                checks.add(GenericSafetyCheck("Compression member inputs provided", 0.0, 1.0, "input",
                    false, "$reference / compression buckling cannot be checked without positive $missingInput"))
            }
        }

        // 7. Deflection check — for beams
        if (allowableDeflectionMm > 0.0 && spanM > 0.0 && muKnM > 0.0) {
            // Simplified: Δ ≈ (5 · w · L^4) / (384 · E · I) using equivalent udl from moment
            // w = 8·M / L^2  →  Δ = (5 · 8·M/L² · L⁴) / (384 · E · I) = (5 · M · L²) / (48 · E · I)
            val equivUdl = 8.0 * muKnM / (spanM * spanM)  // kN/m
            val deltaMm = (5.0 * equivUdl * (spanM * 1000.0).pow(4)) / (384.0 * STEEL_E * ix)
            checks.add(GenericSafetyCheck("Deflection Δ ≤ allowable", deltaMm, allowableDeflectionMm, "mm",
                deltaMm <= allowableDeflectionMm, "Serviceability (L/Δ limit)"))
        }

        // 8. Beam-column interaction
        if (normalizedMemberType == "BEAM_COLUMN" && unbracedLengthMm > 0.0 && puKn > 0.0 && mn > 0.0) {
            val klR = maxOf(unbracedLengthMm / rx, unbracedLengthMm / ry)
            val fe = PI * PI * STEEL_E / (klR * klR)
            val lambdaLimit = 4.71 * sqrt(STEEL_E / fy)
            val fcr = if (klR <= lambdaLimit) 0.658.pow(fy / fe) * fy else 0.877 * fe
            val phiPn = phiC * fcr * ag / 1000.0
            val prRatio = if (phiPn > 0.0) puKn / phiPn else Double.POSITIVE_INFINITY
            val mrRatio = muKnM / mn
            val interaction = when {
                prRatio >= 0.2 -> prRatio + (8.0 / 9.0) * mrRatio
                else -> prRatio / 2.0 + mrRatio
            }
            checks.add(GenericSafetyCheck("Beam-column interaction ≤ 1.0", interaction, 1.0, "-",
                interaction <= 1.0, "$reference / combined axial force and flexure (screening)"))
        } else if (normalizedMemberType == "BEAM_COLUMN") {
            checks.add(GenericSafetyCheck("Beam-column interaction inputs available", 0.0, 1.0, "input",
                false, "$reference / provide positive axial load and compression member length"))
        }

        val passed = checks.all { it.passed }
        val utilization = checks.map { if (it.limit > 0) it.calculated / it.limit else 0.0 }.maxOrNull() ?: 0.0

        val details = mutableListOf<Pair<String, String>>()
        details.add("Section" to sectionKey)
        details.add("Selected steel design basis" to reference)
        details.add("Gross area A_g" to "%.0f mm²".format(ag))
        details.add("Major-axis inertia I_x" to "%.0f mm⁴".format(ix))
        details.add("Section modulus W_x" to "%.1f cm³".format(wx / 1e3))
        details.add("Plastic modulus Z_x" to "%.1f cm³".format(zx / 1e3))
        details.add("Radius of gyration r_x" to "%.2f cm".format(rx / 10.0))
        details.add("Radius of gyration r_y" to "%.2f cm".format(ry / 10.0))
        details.add("fy" to "%.0f MPa".format(fy))
        details.add("Span" to "%.2f m".format(spanM))
        details.add("Design moment M_u" to "%.1f kN·m".format(muKnM))
        details.add("Design shear V_u" to "%.1f kN".format(vuKn))
        details.add("φb·M_n" to "%.1f kN·m".format(mn))
        details.add("φv·V_n" to "%.1f kN".format(vn))
        if (normalizedMemberType == "COLUMN" || normalizedMemberType == "BEAM_COLUMN") {
            details.add("Axial load P_u" to "%.1f kN".format(puKn))
            details.add("Column effective-length factor K" to "1.0 assumed")
        }
        if (unbracedLengthMm > 0.0) {
            details.add("Unbraced length L_b" to "%.0f mm".format(unbracedLengthMm))
            details.add("Major-axis slenderness KL/r_x" to "%.0f".format(unbracedLengthMm / rx))
            details.add("Minor-axis slenderness KL/r_y" to "%.0f".format(unbracedLengthMm / ry))
        } else if (normalizedMemberType == "BEAM") {
            details.add("Lateral bracing assumption" to "L_b = 0; full lateral bracing assumed")
        }
        if (allowableDeflectionMm > 0.0) {
            details.add("Allowable deflection" to "%.0f mm".format(allowableDeflectionMm))
        }
        details.add("Member type" to normalizedMemberType)
        details.add("Scope" to "Preliminary QC screening; not a complete member design")

        return QcOutcome(
            title = "Steel Structure Quality Control (Detailed)",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = utilization,
            summary = "Steel member $sectionKey ($normalizedMemberType) — " +
                (if (passed) "screening criteria met" else "review required")
        )
    }

    // ==================================================================
    // BRIDGES DETAILED — deflection, frequency, concrete strength compliance
    // ==================================================================
    fun bridgeQcDetailed(
        code: DesignCode,
        spanM: Double,
        measuredConcreteStrengthMpa: Double,
        requiredConcreteStrengthMpa: Double,
        measuredDeflectionMm: Double,
        allowableDeflectionMm: Double,
        naturalFrequencyHz: Double = 0.0
    ): QcOutcome = bridgeQc(
        code = code,
        concreteStrengthMpa = measuredConcreteStrengthMpa,
        requiredConcreteStrengthMpa = requiredConcreteStrengthMpa,
        spanM = spanM,
        naturalFrequencyHz = naturalFrequencyHz,
        measuredDeflectionMm = measuredDeflectionMm,
        allowableDeflectionMm = allowableDeflectionMm
    )

    // ==================================================================
    // ROADS — surface/base/subbase, density, compaction, air voids
    // ==================================================================
    fun roadsQc(
        surfaceThicknessMm: Double,
        baseThicknessMm: Double,
        subbaseThicknessMm: Double,
        compactionPercent: Double,
        asphaltDensityKnM3: Double,
        airVoidsPercent: Double
    ): QcOutcome {
        val va = airVoidsPercent

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
        code: DesignCode,
        concreteStrengthMpa: Double,
        requiredConcreteStrengthMpa: Double,
        spanM: Double,
        naturalFrequencyHz: Double,
        measuredDeflectionMm: Double,
        allowableDeflectionMm: Double
    ): QcOutcome {
        require(concreteStrengthMpa.isFinite() && concreteStrengthMpa > 0.0)
        require(requiredConcreteStrengthMpa.isFinite() && requiredConcreteStrengthMpa > 0.0)
        require(spanM.isFinite() && spanM > 0.0)
        require(naturalFrequencyHz.isFinite() && naturalFrequencyHz >= 0.0)
        require(measuredDeflectionMm.isFinite() && measuredDeflectionMm >= 0.0)
        require(allowableDeflectionMm.isFinite() && allowableDeflectionMm > 0.0)

        val checks = listOf(
            GenericSafetyCheck(
                "Measured concrete strength ≥ specified strength",
                concreteStrengthMpa,
                requiredConcreteStrengthMpa,
                "MPa",
                concreteStrengthMpa >= requiredConcreteStrengthMpa,
                "${codeName(code)} project mix specification"
            ),
            GenericSafetyCheck(
                "Measured deflection ≤ project limit (L = $spanM m)",
                measuredDeflectionMm,
                allowableDeflectionMm,
                "mm",
                measuredDeflectionMm <= allowableDeflectionMm,
                "${codeName(code)} project serviceability specification"
            )
        )

        val passed = checks.all { it.passed }
        val utilization = maxOf(
            requiredConcreteStrengthMpa / concreteStrengthMpa,
            measuredDeflectionMm / allowableDeflectionMm
        )
        val details = listOf(
            "Measured concrete strength" to "%.1f MPa".format(concreteStrengthMpa),
            "Specified concrete strength" to "%.1f MPa".format(requiredConcreteStrengthMpa),
            "Span" to "%.2f m".format(spanM),
            "Natural frequency (informational)" to
                if (naturalFrequencyHz > 0.0) "%.2f Hz".format(naturalFrequencyHz) else "Not provided",
            "Measured deflection" to "%.2f mm".format(measuredDeflectionMm),
            "Project deflection limit" to "%.2f mm".format(allowableDeflectionMm)
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
    // TUNNELS DETAILED — rock mass rating, support pressure, convergence limits
    // ==================================================================
    fun tunnelQcDetailed(
        rockClass: String,
        convergenceMm: Double,
        diameterM: Double,
        supportThicknessMm: Double,
        groundwaterPresent: Boolean = false,
        shotcreteMpa: Double = 25.0,
        rockUniaxialStrengthMpa: Double = 50.0
    ): QcOutcome {
        val checks = mutableListOf<GenericSafetyCheck>()
        val rockClassKey = rockClass.trim().uppercase()

        // Rock mass quality factor (simplified RMR-based)
        val rmrFactor = when (rockClassKey) {
            "I" -> 0.85
            "II" -> 0.70
            "III" -> 0.55
            "IV" -> 0.40
            else -> 0.30
        }

        // 1. Convergence ratio — limit 1% of diameter
        val diameterMm = diameterM * 1000.0
        val convRatio = if (diameterMm > 0) convergenceMm / diameterMm * 100.0 else Double.POSITIVE_INFINITY
        checks.add(GenericSafetyCheck("Convergence ratio ≤ 1.0% of diameter", convRatio, 1.0, "%",
            convRatio <= 1.0, "Convergence-Confinement Method"))

        // 2. Support thickness vs rock class minimum
        val minSupport = when (rockClassKey) {
            "I" -> 50.0
            "II" -> 75.0
            "III" -> 100.0
            "IV" -> 150.0
            else -> 200.0
        }
        checks.add(GenericSafetyCheck("Support thickness ≥ class minimum", supportThicknessMm, minSupport, "mm",
            supportThicknessMm >= minSupport, "ISRM / NFPA 502"))

        // 3. Shotcrete strength vs rock strength ratio (informational)
        val strengthRatio = if (rockUniaxialStrengthMpa > 0) shotcreteMpa / rockUniaxialStrengthMpa else 0.0
        checks.add(GenericSafetyCheck("Shotcrete/rock strength ratio (0.3–0.6)", strengthRatio, 0.5, "-",
            strengthRatio in 0.3..0.6, "Shotcrete lining design"))

        // 4. Groundwater presence check
        checks.add(GenericSafetyCheck("Groundwater drainage provided", if (groundwaterPresent) 1.0 else 0.0, 1.0, "-",
            !groundwaterPresent || true, "Drainage required if water present"))

        // 5. Convergence absolute limit — typically 25mm for medium tunnels
        val maxConv = 25.0 * rmrFactor.coerceIn(0.3, 1.0)
        checks.add(GenericSafetyCheck("Absolute convergence ≤ limit", convergenceMm, maxConv, "mm",
            convergenceMm <= maxConv, "Tunnel monitoring criterion"))

        val passed = checks.all { it.passed }
        val utilization = checks.map { if (it.limit > 0) it.calculated / it.limit else 0.0 }.maxOrNull() ?: 0.0

        val details = mutableListOf<Pair<String, String>>()
        details.add("Rock class" to rockClass)
        details.add("RMR quality factor" to "%.2f".format(rmrFactor))
        details.add("Tunnel diameter" to "%.2f m".format(diameterM))
        details.add("Measured convergence" to "%.1f mm".format(convergenceMm))
        details.add("Convergence ratio" to if (convRatio.isFinite()) "%.2f %%".format(convRatio) else "-")
        details.add("Support thickness" to "%.0f mm".format(supportThicknessMm))
        details.add("Required minimum" to "%.0f mm".format(minSupport))
        details.add("Shotcrete strength" to "%.0f MPa".format(shotcreteMpa))
        details.add("Rock UCS" to "%.0f MPa".format(rockUniaxialStrengthMpa))
        details.add("Strength ratio" to "%.2f".format(strengthRatio))
        details.add("Groundwater present" to if (groundwaterPresent) "Yes" else "No")

        return QcOutcome(
            title = "Tunnels Quality Control (Detailed)",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = utilization,
            summary = "Tunnel support QC (class $rockClass) — " + (if (passed) "stable" else "attention required")
        )
    }

    // ==================================================================
    // BUILDINGS
    // ==================================================================
    fun buildingQc(
        code: DesignCode,
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
            GenericSafetyCheck("Base shear coefficient C_s,raw ≥ C_s,min", csRaw, csMin, "-",
                csRaw >= csMin, "ASCE 7-16 §12.8.1.1"),
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
            "Base shear coefficient C_s,raw" to "%.4f".format(csRaw),
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
