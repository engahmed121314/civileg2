package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.*
import com.civileg.app.domain.SoilType
import com.civileg.app.domain.calculations.base.*
import kotlin.math.*

/**
 * ACI 318-19 Pile Foundation Design Implementation
 *
 * ── Scope split (documented per governance ADR) ──────────────────────────
 * Geotechnical capacity (shaft/end-bearing/settlement/lateral/group):
 *   Code-agnostic soil mechanics — α-method (clay), β-method (sand),
 *   rock socket, Broms lateral, Converse-Labarre group, Meyerhof settlement.
 *   SHARED VERBATIM with the SBC implementation (verified by PileCodeParityTest).
 *   NOTE: ECP intentionally uses its own more rigorous geotech (numerical
 *   effective-stress integration, tabulated Berezantzev Nq, embedment factor,
 *   scour-aware length) — legitimate code independence per spec §3.
 *
 * Structural design (pile section + pile cap): ACI 318-19:
 *  - §5.3.1 / ASCE 7-16 load combinations: U = 1.2D + 1.6L
 *  - §21.2.1 strength reduction factors:
 *      φ_flexure = 0.90 (tension-controlled), φ_shear = 0.75,
 *      φ_compression (tied) = 0.65
 *  - §22.4.2 nominal axial: Pn = 0.85·f'c·(Ag − Ast) + fy·Ast
 *  - §22.5.5.1 one-way shear: Vc = 0.17·λ·√f'c·bw·d  (MPa units)
 *  - §22.6.5.2 two-way shear: vc = 0.33·λ·√f'c (square interior column)
 *  - §7.6.1.1/13.3.6.1 minimum flexural reinforcement 0.0018·b·d for caps
 *  - Cube→cylinder conversion used across this app: f'c = 0.80 × fcu
 */
class ACIPileFoundation : PileFoundationDesign {

    companion object {
        // Concrete strength model (ACI 318-19)
        private const val CUBE_TO_CYLINDER = 0.80   // f'c = 0.80 × fcu

        // Strength reduction factors — ACI 318-19 §21.2.1
        private const val PHI_FLEXURE = 0.90
        private const val PHI_SHEAR = 0.75
        private const val PHI_COMPRESSION = 0.65    // tied; spiral piles → 0.75

        // Load factors — ACI 318-19 §5.3.1 (ASCE 7-16 basic)
        private const val LF_DL = 1.2
        private const val LF_LL = 1.6

        private const val STEEL_UNIT_WEIGHT = 7850.0   // kg/m³
        private const val MIN_REIN_RATIO = 0.01        // 1% min for column-like piles
        private const val MIN_TIE_SPACING = 200.0      // mm
        private const val MAX_TIE_SPACING = 400.0      // mm

        private val BAR_DIAMETERS = listOf(12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0, 28.0, 32.0)
    }

    // ══════════════════════════════════════════════════════════════
    // 1. MAIN ENTRY POINT
    // ══════════════════════════════════════════════════════════════

    override fun designPile(input: PileInput): PileDesignResult {
        com.civileg.app.domain.calculations.InputGuard.positive(
            "pileDiameter" to input.pileDiameter, "pileLength" to input.pileLength,
            "safetyFactor" to input.safetyFactor, "fcu" to input.fcu, "fy" to input.fy
        )
        com.civileg.app.domain.calculations.InputGuard.atLeastOne("numberOfPiles", input.numberOfPiles)
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        codeNotes.add("ACI 318-19 §13: Pile Foundation Design")
        codeNotes.add("Pile Type: ${input.pileType.displayName}, Soil: ${input.soilType.displayName}")
        codeNotes.add(
            "f'c = $CUBE_TO_CYLINDER×fcu = ${"%.1f".format(input.fcu * CUBE_TO_CYLINDER)} MPa, " +
            "φ_flex = $PHI_FLEXURE, φ_shear = $PHI_SHEAR, φ_comp = $PHI_COMPRESSION (§21.2.1)"
        )
        codeNotes.add("Load combo U = ${LF_DL}D + ${LF_LL}L (§5.3.1)")
        codeNotes.add("Geotechnical side: code-agnostic soil mechanics (α/β methods)")

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
            pileType = input.pileType,
            singleCapacityKn = capacity.allowableCapacity
        )
        val groupResult = checkGroupEfficiency(groupInput)
        codeNotes.add(String.format(
            "Group efficiency η = %.2f, group capacity = %.0f kN (%s)",
            groupResult.efficiencyFactor, groupResult.groupCapacity, groupResult.pattern
        ))

        // ── 4. Lateral capacity ───────────────────────────────────
        val lateralResult = calculateLateralCapacity(input)
        val lateralOk = input.lateralLoad <= lateralResult.allowableLateralCapacity
        codeNotes.add(String.format(
            "Lateral: Hu = %.0f kN, Ha = %.0f kN, M_max = %.1f kN.m (Vu = %.0f kN) → %s",
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
        codeNotes.add(String.format("Pile reinforcement: %s", pileReinf.barString))

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
            "Pile cap: %.0f × %.0f × %.0f mm, concrete = %.2f m³, steel = %.0f kg",
            capResult.capWidth, capResult.capLength, capResult.capThickness,
            capResult.concreteVolume, capResult.steelWeight
        ))

        // ── 8. Overall safety ─────────────────────────────────────
        val netCapacity = capacity.allowableCapacity - negSkinFriction
        val axialOk = netCapacity >= input.axialLoad
        val lateralUtilRatio = if (lateralResult.allowableLateralCapacity > 0) {
            input.lateralLoad / lateralResult.allowableLateralCapacity
        } else 2.0 // rule 1.4: unknown → UNSAFE
        val maxUtil = maxOf(
            if (netCapacity > 0) input.axialLoad / netCapacity else 2.0,
            lateralUtilRatio,
            capacity.utilizationRatio
        )
        val overallSafe = axialOk && lateralOk && settlement.isOk &&
            pileReinf.isSafe && capResult.punchingShearOk && capResult.beamShearOk

        if (!axialOk) warnings.add(String.format(
            "Pile capacity %.0f kN < axial load %.0f kN", netCapacity, input.axialLoad
        ))
        if (!capResult.punchingShearOk) warnings.add(String.format(
            "Pile cap punching shear %.2f MPa > capacity %.2f MPa (punching reinforcement required)",
            capResult.punchingShearStress, capResult.punchingShearCapacity
        ))
        if (!capResult.beamShearOk) warnings.add(String.format(
            "Pile cap one-way beam shear %.2f MPa > capacity %.2f MPa",
            capResult.beamShearStress, capResult.beamShearCapacity
        ))

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
    // 2. GEOTECHNICAL CAPACITY (code-agnostic soil mechanics)
    // ══════════════════════════════════════════════════════════════

    override fun calculatePileCapacity(input: PileInput): PileCapacityResult {
        val D = input.pileDiameter / 1000.0  // m
        val L = input.pileLength             // m
        val fs = input.safetyFactor

        var Qs = 0.0  // shaft resistance (kN)
        var Qb = 0.0  // end bearing (kN)

        when (input.soilType) {
            SoilType.CLAY -> {
                // Alpha method (Tomlinson values)
                val alpha = calculateAlphaFactor(input.cu, input.pileType)
                Qs = alpha * input.cu * PI * D * L
                Qb = 9.0 * input.cu * PI * D * D / 4.0  // Nc = 9
            }
            SoilType.SAND -> {
                // Beta method
                val K = calculateKFactor(input.pileType)
                val beta = K * tan(input.phi * PI / 180.0)
                val avgEffStress = input.gammaSoil * L / 2.0
                Qs = beta * avgEffStress * PI * D * L
                // End bearing: Nq from Berezantzev
                val Nq = exp(PI * tan(input.phi * PI / 180.0)) *
                         tan(PI / 4 + input.phi * PI / 360.0).pow(2)
                Qb = Nq * input.gammaSoil * L * PI * D * D / 4.0
            }
            SoilType.ROCK -> {
                // Rock socket
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

        // Water table correction (submerged shaft/bearing reduction)
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

    /**
     * α adhesion factor for clay vs cu and installation method
     * (universal Tomlinson-type correlation).
     */
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

    /** Earth pressure coefficient K for sand β-method (installation dependent). */
    private fun calculateKFactor(pileType: PileType): Double {
        return when (pileType) {
            PileType.DRIVEN -> 1.0
            PileType.BORED -> 0.5
            PileType.CFA -> 0.7
            PileType.MICROPILE -> 0.4
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 3. PILE CAP DESIGN — ACI 318-19 structural
    // ══════════════════════════════════════════════════════════════

    override fun designPileCap(input: PileCapInput): PileCapResult {
        val n = input.numberOfPiles
        val dPile = input.pileDiameter
        val s = input.pileSpacing

        // ── Cap dimensions ────────────────────────────────────────
        val colW = input.columnWidth
        val colL = input.columnLength
        val minOverhang = max(dPile * 0.3, 150.0)

        val capWidth = colW + 2 * minOverhang + (if (n > 2) s else 0.0)
        val capLength = colL + 2 * minOverhang + (if (n > 2) s else 0.0)
        val capThickness = max(300.0, dPile * 0.5 + 150.0)

        val dCap = capThickness - input.cover - 20.0  // effective depth (assume 20mm bar)
        val fcPrime = CUBE_TO_CYLINDER * input.fcu     // MPa — cylinder strength

        // ── Load per pile (with eccentricity) ─────────────────────
        val PperPile = input.axialLoad / n
        val MxPerPile = input.momentX / n
        val MyPerPile = input.momentY / n

        // ── Two-way (punching) shear at d/2 from column face ──────
        // ACI 318-19 §22.6.5.2: vc = 0.33·λ·√f'c (interior square column)
        val b1 = colW + dCap
        val b2 = colL + dCap
        val bo = 2.0 * (b1 + b2)
        val VuPunch = input.axialLoad
        val punchStress = VuPunch / (bo * dCap) * 1000.0                       // MPa
        val punchCapacity = PHI_SHEAR * 0.33 * sqrt(fcPrime)                   // MPa
        val punchOk = punchStress <= punchCapacity

        // ── One-way beam shear at pile face ───────────────────────
        // ACI 318-19 §22.5.5.1: Vc = 0.17·λ·√f'c·bw·d
        val shearSpan = (capWidth - colW) / 2.0 - dPile / 2.0
        val VuBeam = input.axialLoad / 2.0
        val beamShearCapacity = PHI_SHEAR * 0.17 * sqrt(fcPrime)               // MPa
        val beamShearStress = VuBeam / (capLength * dCap) * 1000.0             // MPa
        val beamOk = beamShearStress <= beamShearCapacity

        // ── Flexural reinforcement (critical strip) ───────────────
        // Units (verified §63 classification — was WRONG: kN·mm summed with kN·m):
        //   PperPile [kN] × shearSpan [mm] = kN·mm → /1000 → kN·m.
        //   MxPerPile already includes the 1/n distribution — do NOT divide again.
        val MuKnM = PperPile * shearSpan / 1000.0 + abs(MxPerPile)   // kN·m
        val MuNmm = MuKnM * 1e6
        val b = capLength  // mm

        // Rectangular stress block, solved in closed form (§22.2):
        // Mu = φ·As·fy·(d − a/2), a = As·fy/(0.85·f'c·b)
        // → As² coefficient form: k·As² − fy·d·As + Mu/φ = 0
        val fy = input.fy
        val kCoef = fy * fy / (1.7 * fcPrime * b)
        val disc = fy * fy * dCap * dCap - 4.0 * kCoef * MuNmm / PHI_FLEXURE
        val AsRequired = if (disc >= 0 && kCoef > 0) {
            (fy * dCap - sqrt(disc)) / (2.0 * kCoef)
        } else {
            // Deep-block fallback: j ≈ 0.9
            MuNmm / (PHI_FLEXURE * fy * 0.9 * dCap)
        }.coerceAtLeast(0.0)

        // ACI 318-19 §7.6.1.1 / 13.3.6.1 minimum flexural reinforcement
        val AsMin = 0.0018 * b * dCap
        val AsDesign = max(AsRequired, AsMin)

        // Select bars
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

        // ── Punching reinforcement (studs) if needed ──────────────
        val punchRebar = if (!punchOk) {
            val vsReq = max(0.0, VuPunch - punchCapacity * bo * dCap / 1000.0)
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

        // ── Quantities ────────────────────────────────────────────
        val concreteVolume = capWidth * capLength * capThickness / 1e9  // m³
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
    // 4. SETTLEMENT (Meyerhof simplified — code-agnostic)
    // ══════════════════════════════════════════════════════════════

    override fun calculateSettlement(input: PileInput): PileSettlementResult {
        val Q = input.axialLoad          // kN
        val D = input.pileDiameter / 1000.0  // m
        val L = input.pileLength

        val Es = when (input.soilType) {
            SoilType.CLAY -> 15000.0 * input.cu / 100.0
            SoilType.SAND -> 20000.0 * max(1.0, (input.phi - 25.0) / 15.0)
            SoilType.ROCK -> 300000.0
            SoilType.MIXED -> 20000.0
        }

        val immediateSettlement = if (Es > 0 && D > 0) {
            Q / (Es * D) * 0.5 * 1000  // mm
        } else 0.0

        val consolidationSettlement = if (input.soilType == SoilType.CLAY) {
            immediateSettlement * 0.3
        } else 0.0

        val totalSettlement = immediateSettlement + consolidationSettlement
        val allowableSettlement = 25.0

        return PileSettlementResult(
            immediateSettlement = immediateSettlement,
            consolidationSettlement = consolidationSettlement,
            totalSettlement = totalSettlement,
            allowableSettlement = allowableSettlement,
            isOk = totalSettlement <= allowableSettlement
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 5. GROUP EFFICIENCY (Converse-Labarre — code-agnostic)
    // ══════════════════════════════════════════════════════════════

    override fun checkGroupEfficiency(input: PileGroupInput): PileGroupResult {
        val n = input.numberOfPiles
        val d = input.pileDiameter / 1000.0
        val s = input.spacing * d

        val m = ceil(sqrt(n.toDouble())).toInt()
        val angle = atan(s / d)
        val efficiency = 1.0 -
            (m - 1) * (s - d) / (m * s) * angle * 2.0 / PI

        val singleCapacity = input.singleCapacityKn ?: 500.0  // kN — computed Qu when provided
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
    // 6. LATERAL CAPACITY (Broms — code-agnostic)
    // ══════════════════════════════════════════════════════════════

    override fun calculateLateralCapacity(input: PileInput): LateralLoadResult {
        val D = input.pileDiameter / 1000.0
        val L = input.pileLength
        val cu = input.cu
        val gamma = input.gammaSoil

        val HuClay = if (input.soilType == SoilType.CLAY || input.soilType == SoilType.MIXED) {
            val momentCapacity = 2.0 * cu * D * D
            val depthToFixity = 1.5 * D + sqrt(maxOf(0.0, (1.5 * D).pow(2) + 2.0 * momentCapacity / (9.0 * cu * D)))
            9.0 * cu * D * (depthToFixity + 1.5 * D) / (depthToFixity + 1.5 * D + L).coerceAtLeast(1.0)
        } else 0.0

        val HuSand = if (input.soilType == SoilType.SAND || input.soilType == SoilType.MIXED) {
            val phi = input.phi * PI / 180.0
            val Kp = tan(PI / 4 + phi / 2).pow(2)
            val depthToFixitySand = (1.5 * D * Kp).pow(0.5) * (L / D).pow(0.33)
            1.5 * gamma * D * D * Kp * minOf(depthToFixitySand, L * 0.3)
        } else 0.0

        val HuRock = if (input.soilType == SoilType.ROCK) {
            3.0 * cu * D
        } else 0.0

        val Hu = maxOf(HuClay, HuSand, HuRock)
        val Ha = Hu / 2.0
        val Mmax = Hu * D * 0.67
        val depthToFixity = min(1.5 * D, L * 0.3)
        val deflectionHead = if (Hu > 0) Hu * D * D * D / (8.0 * 25000.0 * PI * (D / 2).pow(4)) * 1000 else 0.0

        return LateralLoadResult(
            ultimateLateralCapacity = Hu,
            allowableLateralCapacity = Ha,
            maxBendingMoment = Mmax,
            depthToFixity = depthToFixity,
            deflectionAtHead = deflectionHead  // A7-FIX: report true deflection (was silently capped)
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 7. NEGATIVE SKIN FRICTION (code-agnostic)
    // ══════════════════════════════════════════════════════════════

    override fun calculateNegativeSkinFriction(input: PileInput): Double {
        if (input.waterTableDepth >= input.pileLength) return 0.0

        val D = input.pileDiameter / 1000.0
        val compressibleDepth = min(input.waterTableDepth, input.pileLength)

        if (compressibleDepth <= 0) return 0.0

        val gammaEffective = input.gammaSoil - 9.81
        val avgStress = gammaEffective.coerceAtLeast(5.0) * compressibleDepth / 2.0

        val K = 0.5
        val delta = input.phi * 2.0 / 3.0 * PI / 180.0
        val qf = K * tan(delta) * avgStress

        return qf * PI * D * compressibleDepth
    }

    // ══════════════════════════════════════════════════════════════
    // 8. PILE STRUCTURAL REINFORCEMENT — ACI 318-19
    // ══════════════════════════════════════════════════════════════

    override fun designPileReinforcement(
        input: PileInput,
        axialLoad: Double,
        moment: Double
    ): PileReinforcementResult {
        val D = input.pileDiameter       // mm
        val cover = input.capConcreteCover
        val tieDia = 10.0
        val d = D - cover - tieDia       // effective depth mm
        val Ag = PI * (D / 2.0).pow(2)
        val fcPrime = CUBE_TO_CYLINDER * input.fcu
        val fy = input.fy

        // Factored loads — U = 1.2D + 1.6L (§5.3.1). The input carries ONE axial
        // value with unknown D/L split → apply the ENVELOPE upper bound taken
        // from the Code Engine (§17: no invented factors); 1.2 alone was
        // UNCONSERVATIVE for live-load-dominated piles.
        val lfEnv = maxOf(
            com.civileg.core.calculations.entities.DesignCode.ACI.getDeadLoadFactor(),
            com.civileg.core.calculations.entities.DesignCode.ACI.getLiveLoadFactor()
        )
        val Pu = axialLoad * lfEnv   // kN
        val Mu = moment * lfEnv      // kN.m

        val minAs = MIN_REIN_RATIO * Ag
        val maxAs = 0.08 * Ag

        // Required As from flexure: Mu = φ·As·fy·j·d, j = 0.9 conservative
        val MuNmm = Mu * 1e6
        val PuN = Pu * 1000.0
        var AsRequired = 0.0
        if (MuNmm > 0) {
            AsRequired = max(0.0, MuNmm / (PHI_FLEXURE * fy * 0.9 * d))
        }

        // Minimum from axial: φ[0.85·f'c(Ag−As) + fy·As] ≥ Pu  (§22.4.2)
        // → As ≥ (Pu/φ − 0.85·f'c·Ag) / (fy − 0.85·f'c)
        val denom = fy - 0.85 * fcPrime
        val AsMinAxial = if (denom > 0) {
            max(0.0, (PuN / PHI_COMPRESSION - 0.85 * fcPrime * Ag) / denom)
        } else minAs

        AsRequired = maxOf(AsRequired, AsMinAxial, minAs).coerceAtMost(maxAs)

        // ── Select longitudinal bars ──────────────────────────────
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
        val ratio = AsProvided / Ag

        // ── Ties ──────────────────────────────────────────────────
        val tieSpacing = kotlin.math.min(
            MAX_TIE_SPACING,
            kotlin.math.max(MIN_TIE_SPACING, (16.0 * selDia).toInt().toDouble())
        ).toInt()

        // ── Capacity check: φPn = φ[0.85f'c(Ag−As) + fy·As] ───────
        val Pn = PHI_COMPRESSION * (
            0.85 * fcPrime * (Ag - AsProvided) + fy * AsProvided
        ) / 1000.0  // kN
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

