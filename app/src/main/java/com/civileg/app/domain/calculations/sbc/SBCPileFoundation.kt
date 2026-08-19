package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.*
import com.civileg.app.domain.SoilType
import com.civileg.app.domain.calculations.base.*
import kotlin.math.*

/**
 * SBC 304 Pile Foundation Design Implementation
 *
 * References:
 *  - SBC 304 §7: Deep foundations (piles)
 *  - SBC 304 §3-2: Load factors (1.4DL + 1.6LL)
 *  - SBC 304 §4: Material safety factors (γc = 1.5, γs = 1.15)
 *
 * Key SBC differences from ACI 318:
 *  - Uses fcu directly (cube strength), not f'c = 0.8×fcu
 *  - Material partial safety factors: γc = 1.5, γs = 1.15
 *  - Strength reduction factors: φ_flexure = 0.9, φ_shear = 0.75
 *  - Load factors: 1.4DL + 1.6LL
 *
 * Geotechnical capacity:
 *  - Clay: Alpha method (α varies with cu and pile type)
 *  - Sand: Beta method (K × tan φ)
 *  - Rock: Socket capacity (5 × cu × tip area)
 *
 * Structural design:
 *  - Pile as short column under axial + lateral loads
 *  - φ = 0.9 for compression, φ = 0.75 for shear
 *  - Minimum reinforcement: 1% of gross area
 */
class SBCPileFoundation : PileFoundationDesign {

    companion object {
        // SBC 304 material safety factors
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15

        // SBC 304 strength reduction factors
        private const val PHI_FLEXURE = 0.9
        private const val PHI_SHEAR = 0.75
        private const val PHI_COMPRESSION = 0.65

        // Load factors
        private const val LF_DL = 1.4
        private const val LF_LL = 1.6

        // Concrete properties
        private const val CONCRETE_UNIT_WEIGHT = 25.0  // kN/m³
        private const val STEEL_UNIT_WEIGHT = 7850.0   // kg/m³
        private const val MIN_REIN_RATIO = 0.01        // 1% min for piles (column-like)
        private const val MIN_TIE_SPACING = 200.0      // mm
        private const val MAX_TIE_SPACING = 400.0      // mm

        // Available bar diameters (mm)
        private val BAR_DIAMETERS = listOf(12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0, 28.0, 32.0)
    }

    // ══════════════════════════════════════════════════════════════
    // 1. MAIN ENTRY POINT
    // ══════════════════════════════════════════════════════════════

    override fun designPile(input: PileInput): PileDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        codeNotes.add("SBC 304 §7: Pile Foundation Design")
        codeNotes.add("Pile Type: ${input.pileType.displayName}, Soil: ${input.soilType.displayName}")
        codeNotes.add("γc = $GAMMA_C, γs = $GAMMA_S, φ_flex = $PHI_FLEXURE, φ_shear = $PHI_SHEAR")

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
            "Group efficiency η = %.2f, group capacity = %.0f kN (%s)",
            groupResult.efficiencyFactor, groupResult.groupCapacity, groupResult.pattern
        ))

        // ── 4. Lateral capacity ───────────────────────────────────
        val lateralResult = calculateLateralCapacity(input) as com.civileg.app.domain.calculations.base.LateralLoadResult
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
        codeNotes.add(String.format(
            "Pile reinforcement: %s", pileReinf.barString
        ))

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
        } else 0.0
        val maxUtil = maxOf(
            if (netCapacity > 0) input.axialLoad / netCapacity else 2.0,
            lateralUtilRatio,
            capacity.utilizationRatio
        )
        val overallSafe = axialOk && lateralOk && settlement.isOk && pileReinf.isSafe

        if (!axialOk) warnings.add(String.format(
            "Pile capacity %.0f kN < axial load %.0f kN", netCapacity, input.axialLoad
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
    // 2. GEOTECHNICAL CAPACITY
    // ══════════════════════════════════════════════════════════════

    override fun calculatePileCapacity(input: PileInput): PileCapacityResult {
        val D = input.pileDiameter / 1000.0  // m
        val L = input.pileLength             // m
        val fs = input.safetyFactor

        var Qs = 0.0  // shaft resistance (kN)
        var Qb = 0.0  // end bearing (kN)

        when (input.soilType) {
            com.civileg.app.domain.SoilType.CLAY -> {
                // Alpha method — SBC 304 §7-3-2
                val alpha = calculateAlphaFactor(input.cu, input.pileType)
                Qs = alpha * input.cu * PI * D * L
                Qb = 9.0 * input.cu * PI * D * D / 4.0  // Nc = 9
            }
            com.civileg.app.domain.SoilType.SAND -> {
                // Beta method — SBC 304 §7-3-3
                val K = calculateKFactor(input.pileType)
                val beta = K * tan(input.phi * PI / 180.0)
                val avgEffStress = input.gammaSoil * L / 2.0
                Qs = beta * avgEffStress * PI * D * L
                // End bearing: Nq from Berezantzev
                val Nq = exp(PI * tan(input.phi * PI / 180.0)) *
                         tan(PI / 4 + input.phi * PI / 360.0).pow(2)
                Qb = Nq * input.gammaSoil * L * PI * D * D / 4.0
            }
            com.civileg.app.domain.SoilType.ROCK -> {
                // Socket capacity — SBC 304 §7-3-5
                Qs = 0.2 * input.cu * PI * D * input.embedmentDepth  // side friction in socket
                Qb = input.cu * 5.0 * PI * D * D / 4.0  // end bearing on rock
            }
            com.civileg.app.domain.SoilType.MIXED -> {
                // Split calculation: 40% clay, 60% sand
                val clayDepth = L * 0.4
                val sandDepth = L * 0.6
                // Clay portion
                val alpha = calculateAlphaFactor(input.cu, input.pileType)
                Qs = alpha * input.cu * PI * D * clayDepth
                // Sand portion
                val K = calculateKFactor(input.pileType)
                val beta = K * tan(input.phi * PI / 180.0)
                Qs += beta * input.gammaSoil * sandDepth / 2.0 * PI * D * sandDepth
                // End bearing on sand layer
                val Nq = exp(PI * tan(input.phi * PI / 180.0))
                Qb = Nq * input.gammaSoil * L * PI * D * D / 4.0
            }
            else -> {}
        }

        // Water table correction — SBC 304 §7-4
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
     * Calculate alpha adhesion factor for clay.
     * SBC 304 Table 7.1: alpha depends on cu and pile installation method.
     */
    private fun calculateAlphaFactor(cu: Double, pileType: PileType): Double {
        val baseAlpha = when {
            cu <= 25.0 -> 1.0
            cu <= 50.0 -> 1.0 - (cu - 25.0) / 100.0
            cu <= 100.0 -> 0.5
            else -> 0.5 * sqrt(100.0 / cu)
        }
        // Pile type reduction factors per SBC 304
        return when (pileType) {
            PileType.DRIVEN -> baseAlpha
            PileType.BORED -> baseAlpha * 0.7
            PileType.CFA -> baseAlpha * 0.8
            PileType.MICROPILE -> baseAlpha * 0.6
        }
    }

    /**
     * Calculate earth pressure coefficient K for sand.
     * SBC 304 Table 7.2.
     */
    private fun calculateKFactor(pileType: PileType): Double {
        return when (pileType) {
            PileType.DRIVEN -> 1.0
            PileType.BORED -> 0.5
            PileType.CFA -> 0.7
            PileType.MICROPILE -> 0.4
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 3. PILE CAP DESIGN
    // ══════════════════════════════════════════════════════════════

    override fun designPileCap(input: PileCapInput): PileCapResult {
        val n = input.numberOfPiles
        val dPile = input.pileDiameter
        val s = input.pileSpacing

        // ── Cap dimensions ────────────────────────────────────────
        val colW = input.columnWidth
        val colL = input.columnLength
        val minOverhang = max(dPile * 0.3, 150.0)  // SBC 304 min overhang

        val capWidth = colW + 2 * minOverhang + (if (n > 2) s else 0.0)
        val capLength = colL + 2 * minOverhang + (if (n > 2) s else 0.0)
        val capThickness = max(300.0, dPile * 0.5 + 150.0)  // SBC min 300mm

        val dCap = capThickness - input.cover - 20.0  // effective depth (assume 20mm bar)
        val fcDesign = 0.67 * input.fcu / GAMMA_C
        val fsDesign = input.fy / GAMMA_S

        // ── Load per pile (with eccentricity) ─────────────────────
        val PperPile = input.axialLoad / n
        val MxPerPile = input.momentX / n
        val MyPerPile = input.momentY / n

        // ── Punching shear check (at column face + d/2) ───────────
        val b1 = colW + dCap
        val b2 = colL + dCap
        val bo = 2.0 * (b1 + b2)
        val VuPunch = input.axialLoad  // factored
        val vcPunch = PHI_SHEAR * 0.24 * sqrt(input.fcu) * bo * dCap / 1000.0  // kN
        val punchOk = VuPunch <= vcPunch
        val punchStress = VuPunch / (bo * dCap) * 1000.0  // MPa
        val punchCapacity = PHI_SHEAR * 0.24 * sqrt(input.fcu)  // MPa

        // ── Beam shear check (at pile face) ───────────────────────
        val shearSpan = (capWidth - colW) / 2.0 - dPile / 2.0
        val VuBeam = input.axialLoad / 2.0  // one-way shear
        val vcBeam = PHI_SHEAR * 0.17 * sqrt(input.fcu) * capLength * dCap / 1000.0  // kN
        val beamOk = VuBeam <= vcBeam
        val beamShearStress = VuBeam / (capLength * dCap) * 1000.0
        val beamShearCapacity = PHI_SHEAR * 0.17 * sqrt(input.fcu)

        // ── Flexural reinforcement ────────────────────────────────
        val Mu = PperPile * shearSpan  // kN.m per meter strip
        val MuNmm = Mu * 1e6
        val b = capLength  // mm

        // K-method with fcu directly (SBC approach)
        val K = MuNmm / (input.fcu * b * dCap * dCap)
        val leverArm = dCap * (0.5 + sqrt(max(0.0, 0.25 - K / 1.25)))
        val AsRequired = MuNmm / (fsDesign * leverArm)
        val AsMin = 0.0018 * b * dCap  // SBC min for foundations
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

        // ── Punching reinforcement (if needed) ────────────────────
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
    // 4. SETTLEMENT (Meyerhof simplified)
    // ══════════════════════════════════════════════════════════════

    override fun calculateSettlement(input: PileInput): PileSettlementResult {
        val Q = input.axialLoad          // kN
        val D = input.pileDiameter / 1000.0  // m
        val L = input.pileLength

        // Modulus of compressibility based on soil type
        val Es = when (input.soilType) {
            SoilType.CLAY -> 15000.0 * input.cu / 100.0  // kPa (correlated with cu)
            SoilType.SAND -> 20000.0 * max(1.0, (input.phi - 25.0) / 15.0)  // kPa
            SoilType.ROCK -> 300000.0  // kPa
            SoilType.MIXED -> 20000.0  // kPa (conservative)
        }

        // Meyerhof simplified: δ = Q / (Es × D) × correction factor
        val immediateSettlement = if (Es > 0 && D > 0) {
            Q / (Es * D) * 0.5 * 1000  // mm
        } else 0.0

        // Consolidation settlement for clay
        val consolidationSettlement = if (input.soilType == SoilType.CLAY) {
            immediateSettlement * 0.3  // empirical multiplier
        } else 0.0

        val totalSettlement = immediateSettlement + consolidationSettlement
        val allowableSettlement = 25.0  // SBC 304 general limit

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
        val d = input.pileDiameter / 1000.0  // m
        val s = input.spacing * d             // spacing in m (spacing = xD)

        // Converse-Labarre formula
        val m = ceil(sqrt(n.toDouble())).toInt()
        val angle = atan(s / d)
        val efficiency = 1.0 -
            (m - 1) * (s - d) / (m * s) * angle * 2.0 / PI

        // Individual pile capacity estimate (uses average soil parameters)
        val singleCapacity = 500.0  // kN (conservative default)
        val groupCapacity = efficiency * n * singleCapacity

        // Pattern string
        val rows = ceil(sqrt(n.toDouble())).toInt()
        val cols = ceil(n.toDouble() / rows).toInt()
        val pattern = if (rows == cols) "${rows}×${cols}" else "${rows}×${cols} (${n} piles)"

        return PileGroupResult(
            efficiencyFactor = efficiency.coerceIn(0.5, 1.0),
            groupCapacity = groupCapacity,
            individualCapacity = singleCapacity,
            spacing = s * 1000.0,  // mm
            numberOfPiles = n,
            pattern = pattern
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 6. LATERAL CAPACITY (Broms method)
    // ══════════════════════════════════════════════════════════════

    override fun calculateLateralCapacity(input: PileInput): LateralLoadResult {
        val D = input.pileDiameter / 1000.0  // m
        val L = input.pileLength             // m
        val cu = input.cu                    // kPa
        val gamma = input.gammaSoil          // kN/m³

        // Broms method for cohesive soil (clay)
        val HuClay = if (input.soilType == com.civileg.app.domain.SoilType.CLAY || input.soilType == com.civileg.app.domain.SoilType.MIXED) {
            val momentCapacity = 2.0 * cu * D * D  // kN.m/m
            val depthToFixity = 1.5 * D + sqrt(maxOf(0.0, (1.5 * D).pow(2) + 2.0 * momentCapacity / (9.0 * cu * D)))
            9.0 * cu * D * (depthToFixity + 1.5 * D) / (depthToFixity + 1.5 * D + L).coerceAtLeast(1.0)
        } else 0.0

        // Broms method for cohesionless soil (sand)
        val HuSand = if (input.soilType == com.civileg.app.domain.SoilType.SAND || input.soilType == com.civileg.app.domain.SoilType.MIXED) {
            val phi = input.phi * PI / 180.0
            val Kp = tan(PI / 4 + phi / 2).pow(2)
            val depthToFixitySand = (1.5 * D * Kp).pow(0.5) * (L / D).pow(0.33)
            1.5 * gamma * D * D * Kp * minOf(depthToFixitySand, L * 0.3)
        } else 0.0

        // Rock: very high lateral capacity
        val HuRock = if (input.soilType == com.civileg.app.domain.SoilType.ROCK) {
            3.0 * cu * D  // socket provides fixity
        } else 0.0

        val Hu = maxOf(HuClay, HuSand, HuRock)
        val Ha = Hu / 2.0  // SBC: FS = 2.0 for lateral
        val Mmax = Hu * D * 0.67  // approximate max moment at ~1.3D depth
        val depthToFixity = min(1.5 * D, L * 0.3)
        val deflectionHead = if (Hu > 0) Hu * D * D * D / (8.0 * 25000.0 * PI * (D / 2).pow(4)) * 1000 else 0.0

        return LateralLoadResult(
            ultimateLateralCapacity = Hu,
            allowableLateralCapacity = Ha,
            maxBendingMoment = Mmax,
            depthToFixity = depthToFixity,
            deflectionAtHead = min(deflectionHead, 25.0)  // cap at 25mm for display
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 7. NEGATIVE SKIN FRICTION
    // ══════════════════════════════════════════════════════════════

    override fun calculateNegativeSkinFriction(input: PileInput): Double {
        if (input.waterTableDepth >= input.pileLength) return 0.0

        val D = input.pileDiameter / 1000.0  // m
        val compressibleDepth = min(input.waterTableDepth, input.pileLength)

        if (compressibleDepth <= 0) return 0.0

        // Negative skin friction in compressible layer above water table
        val gammaEffective = input.gammaSoil - 9.81  // submerged unit weight
        val avgStress = gammaEffective.coerceAtLeast(5.0) * compressibleDepth / 2.0

        // K × tan δ for shaft friction in consolidating layer
        val K = 0.5  // at-rest coefficient
        val delta = input.phi * 2.0 / 3.0 * PI / 180.0  // δ ≈ 0.67φ
        val qf = K * tan(delta) * avgStress

        return qf * PI * D * compressibleDepth  // kN
    }

    // ══════════════════════════════════════════════════════════════
    // 8. PILE STRUCTURAL REINFORCEMENT
    // ══════════════════════════════════════════════════════════════

    override fun designPileReinforcement(
        input: PileInput,
        axialLoad: Double,
        moment: Double
    ): PileReinforcementResult {
        val D = input.pileDiameter       // mm
        val cover = input.capConcreteCover  // mm (rebar cover in pile)
        val tieDia = 10.0                // mm
        val d = D - cover - tieDia       // effective depth mm
        val Ag = PI * (D / 2.0).pow(2)   // gross area mm²

        // Factored loads
        val Pu = axialLoad * LF_DL       // kN (use DL factor as conservative)
        val Mu = moment * LF_DL          // kN.m

        // Design stresses with SBC material factors
        val fcDesign = 0.67 * input.fcu / GAMMA_C  // MPa
        val fsDesign = input.fy / GAMMA_S          // MPa

        // ── Axial capacity check ──────────────────────────────────
        // φPn = φ[0.67×fcu/γc × (Ag - As) + fy/γs × As]
        val minAs = MIN_REIN_RATIO * Ag
        val maxAs = 0.08 * Ag

        // Required As from combined axial + moment
        val MuNmm = Mu * 1e6
        val PuN = Pu * 1000.0
        val leverArm = d * 0.7  // conservative

        var AsRequired = 0.0
        if (MuNmm > 0 && leverArm > 0) {
            AsRequired = max(0.0, (MuNmm - PuN * (d - D / 2.0)) / (PHI_FLEXURE * fsDesign * leverArm))
        }

        // Minimum from axial: As_min from φPn ≥ Pu
        val AsMinAxial = if (fcDesign > 0) {
            max(0.0, (PuN / PHI_COMPRESSION - fcDesign * Ag) / (fsDesign - fcDesign))
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

        // ── Ties design ───────────────────────────────────────────
        val tieSpacing = kotlin.math.min(
            MAX_TIE_SPACING,
            kotlin.math.max(MIN_TIE_SPACING, (16.0 * selDia).toInt().toDouble())
        ).toInt()

        // ── Capacity check ────────────────────────────────────────
        val AsTotal = AsProvided
        val Pn = PHI_COMPRESSION * (
            fcDesign * (Ag - AsTotal) + fsDesign * AsTotal
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