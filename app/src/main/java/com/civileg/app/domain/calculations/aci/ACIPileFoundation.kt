package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.*
import com.civileg.app.domain.SoilType
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.CodeReference
import kotlin.math.*

/**
 * ACI 318-19 Pile Foundation Design Implementation.
 *
 * References:
 *  - ACI 318-19 Chapter 13: Foundations (deep foundations §13.4)
 *  - ACI 318-19 §21.2.1: Strength reduction factors (φ flexure 0.9, shear 0.75, tied compression 0.65)
 *  - ACI 318-19 §22.5: One-way shear (Vc = 0.17·λ·√f'c·b·d)
 *  - ACI 318-19 §22.6.5: Two-way (punching) shear
 *  - ACI 318-19 §5.3.1: Load combinations (1.4D, 1.2D+1.6L governing)
 *  - FHWA-NHI-16-009: Driven pile geotechnical methods (alpha/beta)
 *  - Meyerhof (1951, 1956): bearing capacity & settlement
 *  - Broms (1964): lateral load analysis
 *  - Converse-Labarre (1937): group efficiency
 *
 * Key ACI differences from ECP/SBC:
 *  - Uses f'c (cylinder) = 0.8 × fcu (cube) — conversion applied once at entry
 *  - Strength reduction: φ_flexure = 0.9, φ_shear = 0.75, φ_tied = 0.65
 *  - Load factors: 1.2DL + 1.6L governing (also checks 1.4DL)
 *  - Geotechnical alpha/beta methods are soil-mechanics (code-independent) — same as SBC/ECP
 *  - Minimum pile reinforcement per ACI 10.6 (≈1%)
 *
 * ADR-010: كل معامل موثق ببنده. لا حذف — ملف جديد توسعي.
 */
class ACIPileFoundation : PileFoundationDesign {

    companion object {
        // ACI 318-19 strength reduction factors §21.2.1
        private const val PHI_FLEXURE = 0.9
        private const val PHI_SHEAR = 0.75
        private const val PHI_TIED = 0.65
        private const val PHI_SPIRAL = 0.75

        // Cube → cylinder conversion (Egyptian/Saudi cube tests → ACI cylinder)
        private const val CUBE_TO_CYLINDER = 0.8

        // ACI 318-19 §5.3.1 load combinations
        private const val LF_DL_ALONE = 1.4
        private const val LF_DL = 1.2
        private const val LF_LL = 1.6

        // Concrete properties
        private const val CONCRETE_UNIT_WEIGHT = 24.0  // kN/m³ (ACI normal-weight)
        private const val STEEL_UNIT_WEIGHT = 7850.0   // kg/m³
        private const val MIN_REIN_RATIO = 0.01        // 1% min for piles (ACI 10.6)
        private const val MAX_REIN_RATIO = 0.08        // 8% max (ACI 10.6.1)
        private const val MIN_TIE_SPACING = 150.0      // mm
        private const val MAX_TIE_SPACING = 300.0      // mm

        // Available bar diameters (mm)
        private val BAR_DIAMETERS = listOf(12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0, 28.0, 32.0)
    }

    // ══════════════════════════════════════════════════════════════
    // 1. MAIN ENTRY POINT
    // ══════════════════════════════════════════════════════════════

    override fun designPile(input: PileInput): PileDesignResult {
        // Rule 1.4: loud failures — validate before any maths
        InputGuard.positive("pileDiameter", input.pileDiameter)
        InputGuard.positive("pileLength", input.pileLength)
        InputGuard.positive("fcu", input.fcu)
        InputGuard.positive("fy", input.fy)
        InputGuard.positive("safetyFactor", input.safetyFactor)
        InputGuard.positive("numberOfPiles", input.numberOfPiles)
        InputGuard.nonNegative("axialLoad", input.axialLoad)
        InputGuard.nonNegative("lateralLoad", input.lateralLoad)
        InputGuard.nonNegative("momentLoad", input.momentLoad)

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val fc = input.fcu * CUBE_TO_CYLINDER
        codeNotes.add("ACI 318-19 Chapter 13: Deep Foundation Design")
        codeNotes.add("f'c = 0.8 × fcu = %.1f MPa (cylinder) | ${CodeReference.ACI.BEAM_FLEXURE}".format(fc))
        codeNotes.add("φ_flex = $PHI_FLEXURE, φ_shear = $PHI_SHEAR, φ_tied = $PHI_TIED (ACI 318-19 §21.2.1)")
        codeNotes.add("Load combos: 1.2D+1.6L governing, 1.4D checked (ACI 318-19 §5.3.1)")
        codeNotes.add("Pile Type: ${input.pileType.displayName}, Soil: ${input.soilType.displayName}")

        // ── 1. Geotechnical capacity ──────────────────────────────
        val capacity = calculatePileCapacity(input)
        codeNotes.add(String.format(
            "Qu = %.0f kN (Qs = %.0f, Qb = %.0f), Qa = %.0f kN (FS = %.1f)",
            capacity.ultimateCapacity, capacity.shaftResistance,
            capacity.endBearingResistance, capacity.allowableCapacity, capacity.fs
        ))

        // ── 2. Settlement ─────────────────────────────────────────
        val settlement = calculateSettlement(input)
        codeNotes.add(String.format(
            "Settlement: immediate = %.1f mm, total = %.1f mm (allowable = %.0f mm) → %s",
            settlement.immediateSettlement, settlement.totalSettlement,
            settlement.allowableSettlement, if (settlement.isOk) "OK" else "FAIL"
        ))

        // ── 3. Group efficiency ───────────────────────────────────
        val groupInput = PileGroupInput(
            numberOfPiles = input.numberOfPiles,
            pileDiameter = input.pileDiameter,
            spacing = input.spacing,
            pattern = input.pileGroupPattern,
            soilType = input.soilType,
            pileLength = input.pileLength,
            pileType = input.pileType
        )
        val groupResult = checkGroupEfficiency(groupInput)
        codeNotes.add(String.format(
            "Group efficiency η = %.2f (Converse-Labarre), group capacity = %.0f kN (%s)",
            groupResult.efficiencyFactor, groupResult.groupCapacity, groupResult.pattern
        ))

        // ── 4. Lateral capacity ───────────────────────────────────
        val lateralResult = calculateLateralCapacity(input)
        val lateralOk = input.lateralLoad <= lateralResult.allowableLateralCapacity
        codeNotes.add(String.format(
            "Lateral (Broms 1964): Hu = %.0f kN, Ha = %.0f kN, M_max = %.1f kN.m (Vu = %.0f kN) → %s",
            lateralResult.ultimateLateralCapacity, lateralResult.allowableLateralCapacity,
            lateralResult.maxBendingMoment, input.lateralLoad,
            if (lateralOk) "OK" else "FAIL"
        ))

        // ── 5. Negative skin friction ─────────────────────────────
        val negSkinFriction = calculateNegativeSkinFriction(input)
        if (negSkinFriction > 0) {
            codeNotes.add(String.format(
                "Negative skin friction = %.0f kN (embedment above water table)", negSkinFriction
            ))
            warnings.add(String.format(
                "Negative skin friction %.0f kN reduces net capacity", negSkinFriction
            ))
        }

        // ── 6. Pile structural reinforcement ──────────────────────
        val pileReinf = designPileReinforcement(input, input.axialLoad, input.momentLoad)
        codeNotes.add(String.format("Pile reinforcement (ACI 10.6): %s", pileReinf.barString))
        if (!pileReinf.isSafe) {
            warnings.add("Pile structural capacity insufficient — increase diameter or reinforcement")
        }

        // ── 7. Pile cap design ────────────────────────────────────
        val capInput = PileCapInput(
            axialLoad = input.axialLoad,
            momentX = input.momentLoad,
            momentY = 0.0,
            lateralLoad = input.lateralLoad,
            numberOfPiles = input.numberOfPiles,
            pileDiameter = input.pileDiameter,
            pileSpacing = input.spacing * input.pileDiameter,
            columnWidth = input.columnWidth,
            columnLength = input.columnLength,
            fcu = input.fcu,
            fy = input.fy,
            cover = input.capConcreteCover,
            pileGroupPattern = input.pileGroupPattern
        )
        val capResult = designPileCap(capInput)
        codeNotes.add(String.format(
            "Pile cap (ACI 13.2/22.6): %.0f × %.0f × %.0f mm, concrete = %.2f m³, steel = %.0f kg",
            capResult.capWidth, capResult.capLength, capResult.capThickness,
            capResult.concreteVolume, capResult.steelWeight
        ))
        if (!capResult.punchingShearOk) warnings.add("Pile cap punching shear FAIL (ACI 22.6.5)")
        if (!capResult.beamShearOk) warnings.add("Pile cap beam shear FAIL (ACI 22.5.5)")

        // ── 8. Overall safety ─────────────────────────────────────
        val netCapacity = capacity.allowableCapacity - negSkinFriction
        val axialOk = netCapacity >= input.axialLoad
        val lateralUtilRatio = if (lateralResult.allowableLateralCapacity > 0) {
            input.lateralLoad / lateralResult.allowableLateralCapacity
        } else 0.0
        val maxUtil = maxOf(
            if (netCapacity > 0) input.axialLoad / netCapacity else 2.0,
            lateralUtilRatio,
            capacity.utilizationRatio
        )
        // ACI: cap shear + pile structural must also pass (louder than legacy)
        val overallSafe = axialOk && lateralOk && settlement.isOk && pileReinf.isSafe &&
            capResult.punchingShearOk && capResult.beamShearOk

        if (!axialOk) warnings.add(String.format(
            "Pile capacity %.0f kN < axial load %.0f kN", netCapacity, input.axialLoad
        ))
        if (!settlement.isOk) warnings.add("Settlement exceeds allowable 25 mm")

        return PileDesignResult(
            pileType = input.pileType.displayName,
            soilType = input.soilType.displayName,
            pileDiameterMm = input.pileDiameter,
            pileLengthM = input.pileLength,
            numberOfPiles = input.numberOfPiles,
            fcu = input.fcu,
            fy = input.fy,
            axialLoad = input.axialLoad,
            lateralLoad = input.lateralLoad,
            columnWidth = input.columnWidth,
            columnLength = input.columnLength,
            capacityResult = capacity,
            groupResult = groupResult,
            settlementResult = settlement,
            capResult = capResult,
            lateralCapacity = lateralResult.allowableLateralCapacity,
            lateralUtilizationRatio = lateralUtilRatio,
            negativeSkinFriction = negSkinFriction,
            pileReinforcement = pileReinf,
            isSafe = overallSafe,
            utilizationRatio = min(maxUtil, 2.0),
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 2. GEOTECHNICAL CAPACITY (soil mechanics — code-independent)
    // ══════════════════════════════════════════════════════════════

    override fun calculatePileCapacity(input: PileInput): PileCapacityResult {
        InputGuard.positive("pileDiameter", input.pileDiameter)
        InputGuard.positive("pileLength", input.pileLength)
        InputGuard.positive("safetyFactor", input.safetyFactor)

        val D = input.pileDiameter / 1000.0  // m
        val L = input.pileLength             // m
        val fs = input.safetyFactor

        var Qs = 0.0
        var Qb = 0.0

        when (input.soilType) {
            SoilType.CLAY -> {
                // Alpha method — FHWA-NHI-16-009 §8.3, ACI 318-19 Ch.13 (geotechnical)
                val alpha = calculateAlphaFactor(input.cu, input.pileType)
                Qs = alpha * input.cu * PI * D * L
                Qb = 9.0 * input.cu * PI * D * D / 4.0  // Nc = 9 (Skempton)
            }
            SoilType.SAND -> {
                // Beta method — FHWA-NHI-16-009 §8.4
                val K = calculateKFactor(input.pileType)
                val beta = K * tan(input.phi * PI / 180.0)
                val avgEffStress = input.gammaSoil * L / 2.0
                Qs = beta * avgEffStress * PI * D * L
                val Nq = exp(PI * tan(input.phi * PI / 180.0)) *
                    tan(PI / 4 + input.phi * PI / 360.0).pow(2)  // Berezantzev
                Qb = Nq * input.gammaSoil * L * PI * D * D / 4.0
            }
            SoilType.ROCK -> {
                // Rock socket — FHWA §10
                Qs = 0.2 * input.cu * PI * D * input.embedmentDepth
                Qb = input.cu * 5.0 * PI * D * D / 4.0
            }
            SoilType.MIXED -> {
                val clayDepth = L * 0.4
                val sandDepth = L * 0.6
                val alpha = calculateAlphaFactor(input.cu, input.pileType)
                Qs = alpha * input.cu * PI * D * clayDepth
                val K = calculateKFactor(input.pileType)
                val beta = K * tan(input.phi * PI / 180.0)
                Qs += beta * input.gammaSoil * sandDepth / 2.0 * PI * D * sandDepth
                val Nq = exp(PI * tan(input.phi * PI / 180.0))
                Qb = Nq * input.gammaSoil * L * PI * D * D / 4.0
            }
        }

        // Water table correction
        if (input.waterTableDepth < L) {
            val submergedLength = maxOf(0.0, L - input.waterTableDepth)
            val correction = submergedLength / L
            Qs *= (1.0 - 0.5 * correction)
            Qb *= (1.0 - correction)
        }

        val Qu = Qs + Qb
        val Qa = Qu / fs

        return PileCapacityResult(
            ultimateCapacity = Qu,
            allowableCapacity = Qa,
            shaftResistance = Qs,
            endBearingResistance = Qb,
            fs = fs,
            utilizationRatio = if (Qa > 0) input.axialLoad / Qa else 2.0
        )
    }

    private fun calculateAlphaFactor(cu: Double, pileType: PileType): Double {
        val baseAlpha = when {
            cu <= 25.0 -> 1.0
            cu <= 50.0 -> 1.0 - (cu - 25.0) / 100.0
            cu <= 100.0 -> 0.5
            else -> 0.5 * sqrt(100.0 / cu)
        }
        return when (pileType) {
            PileType.DRIVEN -> baseAlpha
            PileType.BORED -> baseAlpha * 0.7
            PileType.CFA -> baseAlpha * 0.8
            PileType.MICROPILE -> baseAlpha * 0.6
        }
    }

    private fun calculateKFactor(pileType: PileType): Double {
        return when (pileType) {
            PileType.DRIVEN -> 1.0
            PileType.BORED -> 0.5
            PileType.CFA -> 0.7
            PileType.MICROPILE -> 0.4
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 3. PILE CAP DESIGN (ACI 318-19 §13.2 / §22.5 / §22.6)
    // ══════════════════════════════════════════════════════════════

    override fun designPileCap(input: PileCapInput): PileCapResult {
        InputGuard.positive("axialLoad", input.axialLoad.coerceAtLeast(1.0))
        InputGuard.positive("numberOfPiles", input.numberOfPiles)
        InputGuard.positive("pileDiameter", input.pileDiameter)
        InputGuard.positive("fcu", input.fcu)
        InputGuard.positive("fy", input.fy)

        val n = input.numberOfPiles
        val dPile = input.pileDiameter
        val s = input.pileSpacing

        val colW = input.columnWidth
        val colL = input.columnLength
        val minOverhang = max(dPile * 0.3, 150.0)  // ACI 13.2.7

        val capWidth = colW + 2 * minOverhang + (if (n > 2) s else 0.0)
        val capLength = colL + 2 * minOverhang + (if (n > 2) s else 0.0)
        val capThickness = max(300.0, dPile * 0.5 + 150.0)

        val dCap = capThickness - input.cover - 20.0
        val fc = input.fcu * CUBE_TO_CYLINDER  // MPa cylinder
        val fsDesign = input.fy  // ACI uses fy directly with φ

        // Load per pile (with eccentricity)
        val PperPile = input.axialLoad / n

        // ── Punching shear (ACI 22.6.5) ───────────────────────────
        val b1 = colW + dCap
        val b2 = colL + dCap
        val bo = 2.0 * (b1 + b2)
        val VuPunch = input.axialLoad
        // vc = min(0.33, 0.17(1+2/β), 0.083(2+αs·d/bo))·√f'c — interior αs=40
        val beta = max(colW, colL) / min(colW, colL).coerceAtLeast(1.0)
        val vc1 = 0.33 * sqrt(fc)
        val vc2 = 0.17 * (1.0 + 2.0 / beta) * sqrt(fc)
        val vc3 = 0.083 * (2.0 + 40.0 * dCap / bo.coerceAtLeast(1.0)) * sqrt(fc)
        val vcPunchStress = minOf(vc1, vc2, vc3)
        val vcPunch = PHI_SHEAR * vcPunchStress * bo * dCap / 1000.0  // kN
        val punchOk = VuPunch <= vcPunch
        val punchStress = VuPunch / (bo * dCap).coerceAtLeast(1.0) * 1000.0
        val punchCapacity = PHI_SHEAR * vcPunchStress

        // ── Beam shear (ACI 22.5.5: Vc = 0.17·λ·√f'c·b·d) ──────────
        val shearSpan = (capWidth - colW) / 2.0 - dPile / 2.0
        val VuBeam = input.axialLoad / 2.0
        val vcBeam = PHI_SHEAR * 0.17 * sqrt(fc) * capLength * dCap / 1000.0
        val beamOk = VuBeam <= vcBeam
        val beamShearStress = VuBeam / (capLength * dCap).coerceAtLeast(1.0) * 1000.0
        val beamShearCapacity = PHI_SHEAR * 0.17 * sqrt(fc)

        // ── Flexure (ACI Rn-ρ, φ=0.9) ──────────────────────────────
        val Mu = PperPile * shearSpan  // kN.m per meter strip
        val MuNmm = Mu * 1e6
        val b = capLength
        val Rn = MuNmm / (PHI_FLEXURE * b * dCap * dCap)
        val m = input.fy / (0.85 * fc)
        val rhoReq = (1.0 - sqrt(max(0.0, 1.0 - 2.0 * m * Rn / input.fy))) / m
        val rhoMin = max(0.25 * sqrt(fc) / input.fy, 1.4 / input.fy)
        val rho = max(rhoReq, rhoMin).coerceAtMost(0.025)
        val AsRequired = rho * b * dCap
        val AsMin = rhoMin * b * dCap
        val AsDesign = max(AsRequired, AsMin)

        var selDia = 16.0
        var selSpacing = 200
        for (dia in BAR_DIAMETERS) {
            val area = PI * dia * dia / 4
            val nBars = ceil(AsDesign / area).toInt().coerceAtLeast(4)
            val sp = floor(b / nBars).toInt().coerceAtLeast(100)
            if (sp <= 300) {
                selDia = dia; selSpacing = sp; break
            }
        }
        val AsProvided = PI * selDia * selDia / 4.0 * (capLength / selSpacing).toInt()

        val flexRebar = RebarDetail(
            bars = (capLength / selSpacing).toInt(),
            diameter = selDia.toInt(),
            spacing = selSpacing,
            area = AsProvided,
            requiredArea = AsRequired,
            ratio = if (AsRequired > 0) AsProvided / AsRequired else 1.0
        )

        val punchRebar = if (!punchOk) {
            val vsReq = max(0.0, VuPunch - vcPunch)
            val studsPerRow = max(4, (bo / 150.0).toInt())
            RebarDetail(
                bars = studsPerRow * 2,
                diameter = 12,
                spacing = 150,
                area = studsPerRow * 2 * PI * 12 * 12 / 4.0,
                requiredArea = vsReq,
                ratio = 1.2
            )
        } else null

        val concreteVolume = capWidth * capLength * capThickness / 1e9
        val steelWeight = (AsProvided * capWidth / 1e6 + AsProvided * capLength / 1e6) * STEEL_UNIT_WEIGHT

        return PileCapResult(
            capWidth = capWidth,
            capLength = capLength,
            capThickness = capThickness,
            punchingShearOk = punchOk,
            punchingShearStress = punchStress,
            punchingShearCapacity = punchCapacity,
            beamShearOk = beamOk,
            beamShearStress = beamShearStress,
            beamShearCapacity = beamShearCapacity,
            flexuralReinforcement = flexRebar,
            punchingReinforcement = punchRebar,
            concreteVolume = concreteVolume,
            steelWeight = steelWeight
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 4. SETTLEMENT (Meyerhof simplified)
    // ══════════════════════════════════════════════════════════════

    override fun calculateSettlement(input: PileInput): PileSettlementResult {
        val Q = input.axialLoad
        val D = input.pileDiameter / 1000.0
        val L = input.pileLength

        val Es = when (input.soilType) {
            SoilType.CLAY -> 15000.0 * input.cu / 100.0
            SoilType.SAND -> 20000.0 * max(1.0, (input.phi - 25.0) / 15.0)
            SoilType.ROCK -> 300000.0
            SoilType.MIXED -> 20000.0
        }

        val immediateSettlement = if (Es > 0 && D > 0) {
            Q / (Es * D) * 0.5 * 1000
        } else 0.0

        val consolidationSettlement = if (input.soilType == SoilType.CLAY) {
            immediateSettlement * 0.3
        } else 0.0

        val totalSettlement = immediateSettlement + consolidationSettlement
        val allowableSettlement = 25.0  // ACI 318-19 performance limit

        return PileSettlementResult(
            immediateSettlement = immediateSettlement,
            consolidationSettlement = consolidationSettlement,
            totalSettlement = totalSettlement,
            allowableSettlement = allowableSettlement,
            isOk = totalSettlement <= allowableSettlement
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 5. GROUP EFFICIENCY (Converse-Labarre)
    // ══════════════════════════════════════════════════════════════

    override fun checkGroupEfficiency(input: PileGroupInput): PileGroupResult {
        val n = input.numberOfPiles
        val d = input.pileDiameter / 1000.0
        val s = input.spacing * d

        val m = ceil(sqrt(n.toDouble())).toInt()
        val angle = atan(s / d.coerceAtLeast(0.01))
        val efficiency = 1.0 - (m - 1) * (s - d).coerceAtLeast(0.0) / (m * s.coerceAtLeast(0.01)) * angle * 2.0 / PI

        val singleCapacity = 500.0  // kN conservative default
        val groupCapacity = efficiency * n * singleCapacity

        val rows = ceil(sqrt(n.toDouble())).toInt()
        val cols = ceil(n.toDouble() / rows).toInt()
        val pattern = if (rows == cols) "${rows}×${cols}" else "${rows}×${cols} (${n} piles)"

        return PileGroupResult(
            efficiencyFactor = efficiency.coerceIn(0.5, 1.0),
            groupCapacity = groupCapacity,
            individualCapacity = singleCapacity,
            spacing = s * 1000.0,
            numberOfPiles = n,
            pattern = pattern
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 6. LATERAL CAPACITY (Broms 1964)
    // ══════════════════════════════════════════════════════════════

    override fun calculateLateralCapacity(input: PileInput): LateralLoadResult {
        val D = input.pileDiameter / 1000.0
        val L = input.pileLength
        val cu = input.cu
        val gamma = input.gammaSoil

        val HuClay = if (input.soilType == SoilType.CLAY || input.soilType == SoilType.MIXED) {
            val momentCapacity = 2.0 * cu * D * D
            val depthToFixity = 1.5 * D + sqrt(maxOf(0.0, (1.5 * D).pow(2) + 2.0 * momentCapacity / (9.0 * cu * D).coerceAtLeast(0.01)))
            9.0 * cu * D * (depthToFixity + 1.5 * D) / (depthToFixity + 1.5 * D + L).coerceAtLeast(1.0)
        } else 0.0

        val HuSand = if (input.soilType == SoilType.SAND || input.soilType == SoilType.MIXED) {
            val phi = input.phi * PI / 180.0
            val Kp = tan(PI / 4 + phi / 2).pow(2)
            val depthToFixitySand = (1.5 * D * Kp).pow(0.5) * (L / D.coerceAtLeast(0.01)).pow(0.33)
            1.5 * gamma * D * D * Kp * minOf(depthToFixitySand, L * 0.3)
        } else 0.0

        val HuRock = if (input.soilType == SoilType.ROCK) {
            3.0 * cu * D
        } else 0.0

        val Hu = maxOf(HuClay, HuSand, HuRock)
        val Ha = Hu / 2.0  // ACI: FS = 2.0 for lateral (service)
        val Mmax = Hu * D * 0.67
        val depthToFixity = min(1.5 * D, L * 0.3)
        val deflectionHead = if (Hu > 0) Hu * D * D * D / (8.0 * 25000.0 * PI * (D / 2).pow(4).coerceAtLeast(1e-9)) * 1000 else 0.0

        return LateralLoadResult(
            ultimateLateralCapacity = Hu,
            allowableLateralCapacity = Ha,
            maxBendingMoment = Mmax,
            depthToFixity = depthToFixity,
            deflectionAtHead = min(deflectionHead, 25.0)
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 7. NEGATIVE SKIN FRICTION
    // ══════════════════════════════════════════════════════════════

    override fun calculateNegativeSkinFriction(input: PileInput): Double {
        if (input.waterTableDepth >= input.pileLength) return 0.0

        val D = input.pileDiameter / 1000.0
        val compressibleDepth = min(input.waterTableDepth, input.pileLength)

        if (compressibleDepth <= 0) return 0.0

        val gammaEffective = (input.gammaSoil - 9.81).coerceAtLeast(5.0)
        val avgStress = gammaEffective * compressibleDepth / 2.0

        val K = 0.5
        val delta = input.phi * 2.0 / 3.0 * PI / 180.0
        val qf = K * tan(delta) * avgStress

        return qf * PI * D * compressibleDepth
    }

    // ══════════════════════════════════════════════════════════════
    // 8. PILE STRUCTURAL REINFORCEMENT (ACI 10.6 / Ch.13)
    // ══════════════════════════════════════════════════════════════

    override fun designPileReinforcement(
        input: PileInput,
        axialLoad: Double,
        moment: Double
    ): PileReinforcementResult {
        val D = input.pileDiameter
        val cover = input.capConcreteCover
        val tieDia = 10.0
        val Ag = PI * (D / 2.0).pow(2)

        val fc = input.fcu * CUBE_TO_CYLINDER
        // Factored loads — governing of 1.2D+1.6L and 1.4D (use max envelope)
        val Pu = max(axialLoad * LF_DL_ALONE, axialLoad * LF_DL)
        val Mu = max(moment * LF_DL_ALONE, moment * LF_DL)

        val minAs = MIN_REIN_RATIO * Ag
        val maxAs = MAX_REIN_RATIO * Ag

        val MuNmm = Mu * 1e6
        val PuN = Pu * 1000.0
        val d = (D - cover - tieDia).coerceAtLeast(D * 0.5)
        val leverArm = d * 0.7

        var AsRequired = 0.0
        if (MuNmm > 0 && leverArm > 0) {
            AsRequired = max(0.0, (MuNmm - PuN * (d - D / 2.0)) / (PHI_FLEXURE * input.fy * leverArm))
        }

        // Axial: φPn = φ[0.85·f'c·(Ag−As) + fy·As] (ACI 22.4.2, tied φ=0.65)
        val AsMinAxial = if (fc > 0) {
            max(0.0, (PuN / PHI_TIED - 0.85 * fc * Ag) / (input.fy - 0.85 * fc).coerceAtLeast(1.0))
        } else minAs

        AsRequired = maxOf(AsRequired, AsMinAxial, minAs).coerceAtMost(maxAs)

        var selDia = 20.0
        var selCount = 6
        for (dia in BAR_DIAMETERS) {
            val area = PI * dia * dia / 4
            val count = ceil(AsRequired / area).toInt().coerceAtLeast(6)
            if (count <= 20 && count >= 4) {
                selDia = dia; selCount = count; break
            }
        }
        val AsProvided = selCount * PI * selDia * selDia / 4
        val ratio = AsProvided / Ag.coerceAtLeast(1.0)

        val tieSpacing = min(
            MAX_TIE_SPACING,
            max(MIN_TIE_SPACING, (16.0 * selDia))
        ).toInt()

        val Pn = PHI_TIED * (0.85 * fc * (Ag - AsProvided) + input.fy * AsProvided) / 1000.0
        val isSafe = Pn >= Pu

        return PileReinforcementResult(
            longitudinalBars = selCount,
            longitudinalDiameter = selDia.toInt(),
            longitudinalArea = AsProvided,
            requiredLongitudinalArea = AsRequired,
            tiesDiameter = tieDia.toInt(),
            tiesSpacing = tieSpacing,
            isSafe = isSafe,
            ratio = ratio
        )
    }
}
