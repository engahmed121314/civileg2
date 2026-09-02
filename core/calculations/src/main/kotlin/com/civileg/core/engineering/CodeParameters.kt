package com.civileg.core.engineering

import kotlin.math.sqrt

/**
 * Code-family parameters (PHASE 09 CodeRuleEngine seed, spec §13–14).
 *
 * ONE place per code family for every coefficient. Engines receive these as
 * constructor dependencies — no engine may hardcode a factor (spec §3).
 */
interface ConcreteCodeParams {
    val family: CodeFamily
    val reference: String

    /** Partial safety factors — null for φ-based families. */
    val gammas: PartialSafetyFactors?

    /** Flexural strength-reduction φ — null for γ-based families. */
    val phiFlexure: Double?

    /** Design concrete compressive stress for the equivalent block (MPa). */
    fun designBlockStressMpa(concrete: ConcreteMaterial): Double

    /** Equivalent-block depth factor β₁. */
    fun beta1(concrete: ConcreteMaterial): Double

    /** Modulus of elasticity (MPa). */
    fun elasticModulusMpa(concrete: ConcreteMaterial): Double

    /** Minimum flexural tension-steel ratio. */
    fun minFlexuralSteelRatio(concrete: ConcreteMaterial, steel: SteelMaterial): Double

    /**
     * Maximum flexural steel ratio (tension-controlled / balanced-derived cap).
     * Used only as a warning gate; exceeding it routes to the doubly path.
     */
    fun maxFlexuralSteelRatio(concrete: ConcreteMaterial, steel: SteelMaterial): Double

    /** Available rebar diameters for this market/family (mm), ascending. */
    val barMenuMm: List<Double>

    // ── Shear rules (spec §20) ──

    /** Concrete shear capacity Vc (kN) on width b × depth d (mm). */
    fun concreteShearCapacityKn(b: Double, d: Double, concrete: ConcreteMaterial): Double

    /** Absolute maximum shear capacity (kN) beyond which the section must grow. */
    fun maxShearCapacityKn(b: Double, d: Double, concrete: ConcreteMaterial): Double

    /** Minimum shear reinforcement As/s (mm²/m) over a 1 m strip. */
    fun minShearReinforcementMm2PerM(b: Double, concrete: ConcreteMaterial, steel: SteelMaterial): Double

    /**
     * Maximum stirrup spacing (mm).
     * @param vsAboveHalfVc true when required Vs exceeds the half-capacity threshold
     * @param vsAboveLimit true when Vs exceeds the high-shear limit (tighter caps)
     */
    fun maxStirrupSpacingMm(d: Double, vsAboveHalfVc: Boolean, vsAboveLimit: Boolean): Double

    /** Stirrup diameter (mm) by section width / demand severity policy. */
    fun stirrupDiameterMm(sectionWidthMm: Double, vuExceedsHalfConcrete: Boolean): Double

    // ── Deflection screening rules (spec §20; detailed computation = separate engine) ──

    /** Basic span/depth ratio for the support condition (Table basis of each code). */
    fun basicSpanDepthRatio(support: com.civileg.core.calculations.entities.SupportCondition): Double

    /**
     * Modification factor applied to the basic ratio.
     * @param fyMpa reinforcement yield (MPa)
     * @param rhoPercent tension reinforcement ratio at midspan, PERCENT
     */
    fun spanDepthModificationFactor(fyMpa: Double, rhoPercent: Double): Double

    /** Total-load deflection limit (mm) for a span given in metres. */
    fun deflectionLimitMm(spanM: Double): Double

    /**
     * Deemed-to-satisfy crack control: maximum tension-bar spacing (mm).
     * Null when the family has no authored rule here yet — callers must then
     * report NOT_CHECKED, never silently pass (spec §62).
     */
    fun maxTensionBarSpacingMm(clearCoverMm: Double, fyMpa: Double): Double?

    // ── Column rules (spec §20, §82) ──

    /** Axial strength-reduction φ for tied columns. */
    val axialPhiTied: Double

    /** Axial strength-reduction φ for spiral columns. */
    val axialPhiSpiral: Double

    /** Small-eccentricity factor α (ECP §4-2-2-2). Null for φ-based families. */
    val smallEccentricityFactor: Double?

    /** Maximum allowable design axial factor applied to P0 (tied/spiral). */
    fun maxAxialFactor(isSpiral: Boolean): Double

    /** Minimum longitudinal column steel ratio (fraction of Ag). */
    fun minColumnSteelRatio(): Double

    /** Maximum longitudinal column steel ratio (fraction of Ag). */
    fun maxColumnSteelRatio(): Double

    /** Maximum tie spacing (mm): min(16·db, 48·dtie, least dim, 300). */
    fun tiesMaxSpacingMm(longitudinalBarDiaMm: Double, tieDiaMm: Double, leastSectionDimMm: Double): Double

    /** Maximum column stirrup spacing for shear (mm). */
    fun columnShearMaxSpacingMm(sectionDimMm: Double, d: Double): Double

    // ── Slab rules (spec §20, §83) ──

    /** Two-way slab moment coefficients (per-metre strip): M = α·w·L². */
    fun twoWaySlabCoefficients(aspectRatio: Double, allEdgesFixed: Boolean): SlabMomentCoeffs

    /** Minimum slab thickness ratio (span/depth) for a support condition. */
    fun minSlabThicknessRatio(support: com.civileg.core.calculations.entities.SupportCondition): Double

    // ── Torsion rules (spec §20, §22.7 / ECP §4-3-4) ──

    /** √(design concrete strength) for torsion (MPa). ECP √(fcu/γc); ACI √f'c. */
    fun torsionConcreteRootMpa(concrete: ConcreteMaterial): Double

    /** Strength-reduction φ for torsion (null for γ-based families). */
    val torsionPhi: Double?

    /** Threshold torsion — neglect torsion if demand < this (N·mm). ACI §22.7.4.1(a); ECP §4-3-4. */
    fun torsionThresholdTorqueNmm(b: Double, h: Double, concrete: ConcreteMaterial): Double

    /** Cracking torsion — redistribution limit (N·mm). ACI §22.7.5.1; ECP §4-3-4. */
    fun torsionCrackingTorqueNmm(b: Double, h: Double, concrete: ConcreteMaterial): Double

    /** Maximum allowable torsion — section adequacy / crushing limit (N·mm). */
    fun torsionMaxTorqueNmm(b: Double, h: Double, concrete: ConcreteMaterial): Double

    /** Design transverse (stirrup) yield for torsion — ECP fy/γs, ACI fy. */
    fun torsionTransverseDesignMpa(steel: SteelMaterial): Double

    /** Minimum transverse torsion steel At/s (mm²/mm) for closed stirrups. */
    fun torsionMinTransverseMm2PerMm(b: Double, concrete: ConcreteMaterial, steel: SteelMaterial): Double

    // ── Isolated footing rules (spec §20.7; isolated path, golden-matched to the app) ──

    /** Minimum flexural reinforcement ratio for footings (fraction of b·d). */
    fun footingMinReinRatio(): Double

    /** Coefficient c in ρmin = max(c·√fc/fy, footingMinReinRatio) — ECP 0.26, ACI 1.33. */
    fun footingMinReinStressCoef(): Double

    /** Least footing thickness (mm) before a warning fires. */
    fun footingMinThicknessMm(): Double

    /** Clear cover for footings (mm). */
    fun footingCoverMm(): Double

    /** One-way beam shear stress capacity (MPa): ECP 0.24√(fcu/γc); ACI φ·0.17√f'c. */
    fun footingOneWayShearStressMpa(concrete: ConcreteMaterial): Double

    /** Punching shear base stress (MPa), PRE-φ: ECP 0.316√(fcu/γc); ACI 0.33√f'c. */
    fun footingPunchingBaseStressMpa(concrete: ConcreteMaterial): Double

    /** Punching strength factor φ — null for γ-based families (ECP). */
    val footingPunchingPhi: Double?

    // ── Stair rules (waist-slab stair, modelled as a 1 m simply-supported strip;
    //    golden-matched to the app ECP/Aci/SBC staircase implementations) ──

    /** Minimum practical waist thickness (mm) — ECP 120, ACI 125, SBC 130. */
    val stairMinWaistMm: Double

    /** Clear cover for the waist slab (mm) — the app's interior-cover value. */
    val stairCoverMm: Double

    /** Stirrup-diameter estimate used in the effective-depth back-calc (mm). */
    val stairStirrupEstimateMm: Double

    /** d = h − cover − stirrup/2 − 6, the app's own effective-depth back-calc (mm). */
    fun stairEffectiveDepthMm(waistThicknessMm: Double): Double

    /** Maximum riser / minimum going (mm) — ECP 180/250, ACI+SBC 178/279. */
    val stairMaxRiserMm: Double
    val stairMinGoingMm: Double

    /** Comfort range for 2R+G (mm). */
    val stairComfortMinMm: Double
    val stairComfortMaxMm: Double

    /** Geometry auto-split constants — app-faithful and per-family. */
    val stairRiserCountIdealGoingBaseMm: Double   // going = base − 2R when only risers known
    val stairAutoComfortTargetMm: Double          // best-fit 2R+G target, fully auto
    val stairAutoBestRiserStartMm: Double         // scan seed
    val stairAutoBestRiserMinMm: Double           // scan clamp

    /** Minimum flexural steel ratio reported by the app (fraction of the strip). */
    val stairMinSteelRatio: Double

    /** Full minimum-steel area (mm² over a 1 m strip): ECP max(0.26√fcu/fy, ρmin)·b·d; ACI ρmin·b·h; SBC ρmin·b·d. */
    fun stairMinSteelAreaMm2(b: Double, h: Double, d: Double, concrete: ConcreteMaterial, steel: SteelMaterial): Double

    /** Distribution steel floor ratio × b·h — ECP 0.0012, ACI 0.0018, SBC 0.002. */
    val stairDistributionMinRatio: Double

    /** Main / distribution bar menus (mm) + fallback diameter when none fits. */
    fun stairMainBarMenu(): List<Double>
    fun stairMainBarFallback(): Double
    fun stairDistBarMenu(): List<Double>
    fun stairDistBarFallback(): Double

    /** Stirrup diameter for the waist slab (mm) — ECP 8, ACI/SBC 10. */
    val stairShearStirrupDiameterMm: Double

    /** Upper clamp on waist stirrup spacing (mm) — ECP 200, ACI/SBC 300. */
    fun stairStirrupSpacingMaxMm(): Double

    /** Deflection modification factor × basic L/d — ECP 0.55+0.45/ρ%; ACI/SBC 1.0. */
    fun stairDeflectionModificationFactor(rhoPercent: Double): Double

    /** Total-load deflection limit (mm) — ECP L/250, ACI/SBC L/240. */
    fun stairDeflectionLimitMm(spanM: Double): Double

    // ── Water-retaining (tank) rules (liquid-retaining structures; golden-matched
    //    to the app ECPTank / ACITank / SBCTank implementations) ──

    /** Minimum reinforcement ratio for water-retaining members (fraction of b·d). */
    fun tankMinRho(concrete: ConcreteMaterial, steel: SteelMaterial): Double

    /** Flat min-ρ displayed in the wall reinforcement-ratio check row and used in
     *  wall isSafe (legacy golden: ECP 0.0025 fixed; ACI/SBC 0.0020 — the design
     *  minimum [tankMinRho] may be higher via the 1.33√f'c/fy term). */
    fun tankMinRhoFlat(): Double

    /** Maximum allowed crack width (mm) for water-retaining structures. */
    fun tankCrackWidthLimitMm(): Double

    /** Liquid (water) load factor — water-storage structures use a dedicated F. */
    fun tankFluidLoadFactor(): Double

    /** Flexural φ for tank members (environmental structures); null for γ-based ECP. */
    val tankPhiFlexure: Double?

    /** Clear cover for water-retaining faces (mm). */
    fun tankCoverMm(): Double

    /** d = thickness − cover − stirrup-leg (the app's own back-calc). */
    fun tankEffectiveDepthMm(thicknessMm: Double): Double

    /** Bar spacing schedule (mm) from bars-per-metre. */
    fun tankSpacingMm(barsPerMeter: Int): Double

    /** Market bar menu (mm) + fallback diameter for tank walls/base. */
    fun tankBarMenuMm(): List<Double>
    fun tankBarFallback(): Double

    /** Hoop-tension (circular wall) crack limit (MPa). */
    fun tankHoopTensionLimitMpa(concrete: ConcreteMaterial): Double

    /** Flexure ρ cap for tank members (compression-failure / doubly branch). */
    fun tankRhoMax(): Double

    /** Least wall / base thickness before the proportional rule governs (mm). */
    fun tankMinWallThicknessMm(): Double
    fun tankMinBaseThicknessMm(): Double

    // ── Cantilever retaining-wall rules (earth-retaining; golden-matched to
    //    the app ECPRetainingWall / ACIRetainingWall / SBCRetainingWall) ──

    /** Bar-menu diameters used by the wall's selectBars — the SHARED legacy
     *  market list, identical for every family (app RetainingWallDesign). */
    fun retainingWallBarMenuMm(): List<Double>

    /** Clear cover for earth-contact faces (mm) — ECP 50, ACI/SBC 75. */
    fun retainingWallCoverMm(): Double

    /** Stirrup-diameter estimate in the effective-depth back-calc (mm) — ECP 8, ACI/SBC 10. */
    fun retainingWallStirrupEstimateMm(): Double

    /** Lateral (soil + surcharge) load factor — ECP 1.6 imposed §2-3-1, ACI/SBC 1.6 lateral. */
    fun retainingWallLateralLoadFactor(): Double

    /** Dead load factor — ECP 1.4, ACI/SBC 1.2. */
    fun retainingWallDeadLoadFactor(): Double

    /** Minimum flexural steel ratio for wall members (fraction of b·d). */
    fun retainingWallMinSteelRatio(): Double

    /** Maximum flexural steel ratio cap (ACI ρ-coerced ceiling 0.025; not
     *  exercised by the ECP K-method, kept uniform across families). */
    fun retainingWallMaxFlexuralSteelRatio(): Double

    /** Minimum-steel coefficient of the √fcu/fy form (ECP 0.26); π-based
     *  families use the flat [retainingWallMinSteelRatio] only. */
    fun retainingWallMinSteelStressCoef(): Double

    /** Minimum ratio shown in the note text — SBC declares 0.002 while its
     *  design math is ACI's 0.0018 (legacy byte-exact behaviour). */
    fun retainingWallMinSteelRatioNote(): Double

    /** Overturning factor-of-safety limit — ECP 1.5, ACI 2.0, SBC 1.5. */
    fun retainingWallOtFsLimit(): Double

    /** Sliding factor-of-safety limit — every family 1.5. */
    fun retainingWallSlidingFsLimit(): Double

    /** Shear-reduction φ (note text) — null for γ-based ECP. */
    val retainingWallShearPhi: Double?

    /** Stem distribution-steel area (mm² over a 1 m strip): ECP 0.0025·b·d
     *  (wall minimum); ACI/SBC max(ρmin·b·d/4, 100). */
    fun retainingWallDistributionAreaMm2(b: Double, d: Double): Double
}

/**
 * Torsion torque constants (N·mm, f in MPa, geometry in mm).
 *
 * Derived by unit conversion from the psi-based code constants:
 *   T = K · √(f') · (Acp²/Pcp)
 * where Acp²/Pcp = (b·h)² / (2·(b+h)) in mm³.
 * The published code constants are 0.083 (threshold, ACI §22.7.4.1(a)),
 * 0.33 (cracking, §22.7.5.1 / ECP §4-3-4) and 1.7 (ACI §22.7.7.1a
 * torsion-alone crushing bound, derived as 1.7/0.083 × threshold).
 * Conversion factor = 0.083 · √145.038 / 16387.064 · 112.9848 = 0.006894
 * (psi→MPa→N·mm). Cracking = 4 × threshold; ECP max = cracking form.
 */
private const val TORSION_THRESHOLD_K = 0.006894
private const val TORSION_CRACKING_K = 0.027576   // = 4 × threshold
private const val TORSION_ACI_MAX_K = 0.1412      // = (1.7 / 0.083) × threshold
private const val TORSION_ECP_MAX_K = TORSION_CRACKING_K  // ECP §4-3-4 max = 0.333 form

/** Shared torsion torque helper — Acp²/Pcp in mm³. */
private fun torsionTorqueNmm(k: Double, rootMpa: Double, b: Double, h: Double): Double {
    val acp = b * h
    val pcp = 2.0 * (b + h)
    return k * rootMpa * (acp * acp) / pcp
}

/** Two-way slab moment coefficients (α values for M = α·w·L², kN·m/m). */
data class SlabMomentCoeffs(
    val posShort: Double,
    val negShort: Double,
    val posLong: Double,
    val negLong: Double
)

/** ECP 203 — cube strength, γc/γs partial factors, K-method. */
object Ecp203Params : ConcreteCodeParams {
    override val family = CodeFamily.ECP_203
    override val reference = "ECP 203-2020"
    override val gammas: PartialSafetyFactors = PartialSafetyFactors.ECP
    override val phiFlexure: Double? = null

    override fun designBlockStressMpa(concrete: ConcreteMaterial) =
        0.67 * concrete.fcuMpa / gammas!!.gammaC                       // §4-2-1-2(b)

    override fun beta1(concrete: ConcreteMaterial) = concrete.betaOne(CodeFamily.ECP_203)

    override fun elasticModulusMpa(concrete: ConcreteMaterial) =
        concrete.elasticModulusMpa(CodeFamily.ECP_203)                 // 4400√fcu

    override fun minFlexuralSteelRatio(concrete: ConcreteMaterial, steel: SteelMaterial) =
        maxOf(0.25 * sqrt(concrete.fcuMpa) / steel.yieldMpa, 0.0013)   // post-A10 pair

    override fun maxFlexuralSteelRatio(concrete: ConcreteMaterial, steel: SteelMaterial): Double {
        // ρ_bal from the K-method balance point, capped at 75% and 4%.
        val kBal = kBalanced(steel)
        val rhoBal = kBal * 1.25 * concrete.fcuMpa / (steel.yieldMpa / gammas!!.gammaS)
        return minOf(0.75 * rhoBal, 0.04)
    }

    /** K_bal per §4-2-2-1 (εcu = 0.003, β = 0.9). */
    fun kBalanced(steel: SteelMaterial): Double {
        val epsY = steel.yieldMpa / (200_000.0 * gammas!!.gammaS)
        val aOverD = 0.9 * 0.003 / (0.003 + epsY)
        return (0.67 / gammas.gammaC) * aOverD * (1.0 - aOverD / 2.0)
    }

    override val barMenuMm: List<Double> = RebarTable.DIAMETERS_MM

    // ── Shear: ECP 203 §4-3-1-2 / §4-3-2 ──

    override fun concreteShearCapacityKn(b: Double, d: Double, concrete: ConcreteMaterial) =
        0.24 * kotlin.math.sqrt(concrete.fcuMpa / gammas!!.gammaC) * b * d / 1000.0

    override fun maxShearCapacityKn(b: Double, d: Double, concrete: ConcreteMaterial) =
        0.7 * kotlin.math.sqrt(concrete.fcuMpa / gammas!!.gammaC) * b * d / 1000.0

    override fun minShearReinforcementMm2PerM(b: Double, concrete: ConcreteMaterial, steel: SteelMaterial) =
        0.0015 * b * 1000.0

    override fun maxStirrupSpacingMm(d: Double, vsAboveHalfVc: Boolean, vsAboveLimit: Boolean) = 200.0

    override fun stirrupDiameterMm(sectionWidthMm: Double, vuExceedsHalfConcrete: Boolean) =
        if (sectionWidthMm < 250.0) 8.0 else 10.0

    // ── Deflection: ECP 203 §6-3 span/depth method ──

    /** MF = 0.55 + 477/(fy·ρ%) with ρ% floored at 0.15 — uses ACTUAL fy (legacy hardcoded 360). */
    override fun spanDepthModificationFactor(fyMpa: Double, rhoPercent: Double) =
        0.55 + 477.0 / (fyMpa * maxOf(rhoPercent, 0.15))

    override fun deflectionLimitMm(spanM: Double) = spanM * 1000.0 / 250.0  // §6-3 total load

    override fun basicSpanDepthRatio(support: com.civileg.core.calculations.entities.SupportCondition) =
        when (support) {
            com.civileg.core.calculations.entities.SupportCondition.SIMPLY_SUPPORTED -> 20.0
            com.civileg.core.calculations.entities.SupportCondition.CONTINUOUS -> 26.0
            com.civileg.core.calculations.entities.SupportCondition.CANTILEVER -> 7.0
        }

    /** ECP 203-2020 §4-2-3-5-2 crack control — 300 mm deemed-to-satisfy limit. */
    override fun maxTensionBarSpacingMm(clearCoverMm: Double, fyMpa: Double): Double? = 300.0

    // ── Column rules (ECP 203 §4-2-2-2, §4-2-3, §4-2-6) ──

    override val axialPhiTied = 0.65
    override val axialPhiSpiral = 0.75
    override val smallEccentricityFactor = 0.8   // α — small-eccentricity factor for tied columns

    /** ECP: no separate 0.80 cap; design uses φ·α·Pn directly (γ already in stresses). */
    override fun maxAxialFactor(isSpiral: Boolean) = 1.0

    override fun minColumnSteelRatio() = 0.008    // §4-2-3: 0.8%
    override fun maxColumnSteelRatio() = 0.06     // §4-2-3: 6% (4% at laps)

    override fun tiesMaxSpacingMm(longitudinalBarDiaMm: Double, tieDiaMm: Double, leastSectionDimMm: Double) =
        minOf(16.0 * longitudinalBarDiaMm, 48.0 * tieDiaMm, leastSectionDimMm, 300.0)

    override fun columnShearMaxSpacingMm(sectionDimMm: Double, d: Double) =
        minOf(15.0 * 10.0, sectionDimMm, 300.0)   // §4-2-5: min(15db_tie, b, 300); db_tie=10 default

    // ── Slab rules (ECP 203 §5 simplified Marcus coefficients) ──

    override fun twoWaySlabCoefficients(aspectRatio: Double, allEdgesFixed: Boolean) = when {
        allEdgesFixed -> when {
            aspectRatio <= 1.0 -> SlabMomentCoeffs(0.031, 0.024, 0.031, 0.024)
            aspectRatio <= 1.2 -> SlabMomentCoeffs(0.036, 0.027, 0.028, 0.021)
            aspectRatio <= 1.4 -> SlabMomentCoeffs(0.040, 0.030, 0.025, 0.019)
            else -> SlabMomentCoeffs(0.044, 0.033, 0.022, 0.017)
        }
        else -> when {
            aspectRatio <= 1.0 -> SlabMomentCoeffs(0.048, 0.036, 0.048, 0.036)
            aspectRatio <= 1.2 -> SlabMomentCoeffs(0.055, 0.041, 0.043, 0.032)
            aspectRatio <= 1.4 -> SlabMomentCoeffs(0.061, 0.046, 0.038, 0.029)
            else -> SlabMomentCoeffs(0.067, 0.050, 0.034, 0.026)
        }
    }

    override fun minSlabThicknessRatio(support: com.civileg.core.calculations.entities.SupportCondition) =
        when (support) {
            com.civileg.core.calculations.entities.SupportCondition.SIMPLY_SUPPORTED -> 25.0
            com.civileg.core.calculations.entities.SupportCondition.CONTINUOUS -> 30.0
            com.civileg.core.calculations.entities.SupportCondition.CANTILEVER -> 10.0
        }

    // ── Torsion: ECP 203 §4-3-4 (space-truss, γc/γs) ──

    override fun torsionConcreteRootMpa(concrete: ConcreteMaterial) =
        kotlin.math.sqrt(concrete.fcuMpa / gammas!!.gammaC)

    override val torsionPhi: Double? = null

    override fun torsionThresholdTorqueNmm(b: Double, h: Double, concrete: ConcreteMaterial) =
        torsionTorqueNmm(TORSION_THRESHOLD_K, torsionConcreteRootMpa(concrete), b, h)

    override fun torsionCrackingTorqueNmm(b: Double, h: Double, concrete: ConcreteMaterial) =
        torsionTorqueNmm(TORSION_CRACKING_K, torsionConcreteRootMpa(concrete), b, h)

    override fun torsionMaxTorqueNmm(b: Double, h: Double, concrete: ConcreteMaterial) =
        torsionTorqueNmm(TORSION_ECP_MAX_K, torsionConcreteRootMpa(concrete), b, h)

    override fun torsionTransverseDesignMpa(steel: SteelMaterial) = steel.yieldMpa / gammas!!.gammaS

    override fun torsionMinTransverseMm2PerMm(b: Double, concrete: ConcreteMaterial, steel: SteelMaterial) =
        0.00125 * b   // 0.25 % ρv,min for a 2-leg closed stirrup (ECP §4-3-4)

    // ── Isolated footing rules (ECP 203 §7 / Table 4-8 / §4-3-2) ──

    override fun footingMinReinRatio() = 0.0015                // Table 4-8: 0.15%
    override fun footingMinReinStressCoef() = 0.26             // §4-2-1-2 minimum pair
    override fun footingMinThicknessMm() = 300.0               // §7-1
    override fun footingCoverMm() = 50.0                       // §7-1-3 / §4-1-4
    override fun footingOneWayShearStressMpa(concrete: ConcreteMaterial) =
        0.24 * kotlin.math.sqrt(concrete.fcuMpa / gammas!!.gammaC)   // §4-3-1-2 (stress incl. γc)
    override fun footingPunchingBaseStressMpa(concrete: ConcreteMaterial) =
        0.316 * kotlin.math.sqrt(concrete.fcuMpa / gammas!!.gammaC)  // §4-3-2 (stress incl. γc)
    override val footingPunchingPhi: Double? = null

    // ── Stair rules (ECP 203 §4-2 waist slab; app ECPStaircase golden match) ──

    override val stairMinWaistMm = 120.0                  // app constant
    override val stairCoverMm = 25.0                      // §4-1-4 interior
    override val stairStirrupEstimateMm = 8.0             // app back-calc seed
    override fun stairEffectiveDepthMm(waistThicknessMm: Double) =
        waistThicknessMm - stairCoverMm - stairStirrupEstimateMm / 2.0 - 6.0
    override val stairMaxRiserMm = 180.0                  // ECP 201 geometry
    override val stairMinGoingMm = 250.0
    override val stairComfortMinMm = 550.0
    override val stairComfortMaxMm = 700.0
    override val stairRiserCountIdealGoingBaseMm = 620.0
    override val stairAutoComfortTargetMm = 625.0
    override val stairAutoBestRiserStartMm = 170.0
    override val stairAutoBestRiserMinMm = 140.0
    override val stairMinSteelRatio = 0.0013              // app-flagged "0.15%" floor
    override fun stairMinSteelAreaMm2(b: Double, h: Double, d: Double, concrete: ConcreteMaterial, steel: SteelMaterial) =
        maxOf(0.26 * kotlin.math.sqrt(concrete.fcuMpa) / steel.yieldMpa, stairMinSteelRatio) * b * d
    override val stairDistributionMinRatio = 0.0012
    override fun stairMainBarMenu(): List<Double> = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0)
    override fun stairMainBarFallback() = 12.0
    override fun stairDistBarMenu(): List<Double> = listOf(8.0, 10.0, 12.0)
    override fun stairDistBarFallback() = 8.0
    override val stairShearStirrupDiameterMm = 8.0
    override fun stairStirrupSpacingMaxMm() = 200.0
    override fun stairDeflectionModificationFactor(rhoPercent: Double) =
        0.55 + 0.45 / rhoPercent
    override fun stairDeflectionLimitMm(spanM: Double) = spanM * 1000.0 / 250.0  // §6-3

    // ── Tank rules (ECP 203 §8-1 water-retaining; app ECPTank golden match) ──

    override fun tankMinRho(concrete: ConcreteMaterial, steel: SteelMaterial) = 0.0025   // §8-1 min ρ
    override fun tankMinRhoFlat() = 0.0025
    override fun tankCrackWidthLimitMm() = 0.20                                            // §8-1: w ≤ 0.20 mm
    override fun tankFluidLoadFactor() = 1.6                                               // §2-3-1: γQ = 1.6 imposed/liquid
    override val tankPhiFlexure: Double? = null                                            // K-method (§4-2-2-1)
    override fun tankCoverMm() = 50.0                                                      // §8-1 water face
    override fun tankEffectiveDepthMm(thicknessMm: Double) = thicknessMm - tankCoverMm() - 8.0
    /** ECP schedules bars by rounding the spacing UP to the next 10 mm. */
    override fun tankSpacingMm(barsPerMeter: Int) =
        kotlin.math.ceil((1000.0 / barsPerMeter) / 10.0) * 10.0
    override fun tankBarMenuMm(): List<Double> = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0)
    override fun tankBarFallback() = 16.0
    override fun tankHoopTensionLimitMpa(concrete: ConcreteMaterial) = concrete.tensileStrengthMpa // fct = 0.6√fcu
    override fun tankRhoMax() = 0.025
    override fun tankMinWallThicknessMm() = 200.0
    override fun tankMinBaseThicknessMm() = 250.0

    // ── Retaining-wall rules (ECP 203 earth-retaining; app ECPRetainingWall golden match) ──

    /** Shared legacy market list — identical for every family. */
    override fun retainingWallBarMenuMm(): List<Double> =
        listOf(10.0, 12.0, 13.0, 16.0, 18.0, 20.0, 22.0, 25.0, 28.0, 32.0)
    override fun retainingWallCoverMm() = 50.0                      // §4-1-4 earth contact
    override fun retainingWallStirrupEstimateMm() = 8.0             // app back-calc seed
    override fun retainingWallLateralLoadFactor() = 1.6             // §2-3-1 imposed/lateral
    override fun retainingWallDeadLoadFactor() = 1.4                // §2-3-1 dead
    override fun retainingWallMinSteelRatio() = 0.0013              // wall minimum floor
    override fun retainingWallMaxFlexuralSteelRatio() = 0.025       // unused by K-method (uniform cap)
    override fun retainingWallMinSteelStressCoef() = 0.26           // §4-2-1-2 0.26√fcu/fy pair
    override fun retainingWallMinSteelRatioNote() = 0.0013
    override fun retainingWallOtFsLimit() = 1.5
    override fun retainingWallSlidingFsLimit() = 1.5
    override val retainingWallShearPhi: Double? = null              // γs-based (§4-3-1-2)
    override fun retainingWallDistributionAreaMm2(b: Double, d: Double) = 0.0025 * b * d
}

/** ACI 318-19 — cylinder strength f'c = 0.8×fcu, φ factors, Rn–ρ method. */
object Aci318Params : ConcreteCodeParams {
    override val family = CodeFamily.ACI_318
    override val reference = "ACI 318-19"
    override val gammas: PartialSafetyFactors? = null
    override val phiFlexure: Double? = 0.90                            // §21.2.2 tension-controlled

    override fun designBlockStressMpa(concrete: ConcreteMaterial) =
        0.85 * concrete.cylinderStrengthMpa                            // §22.2.2.4.1

    override fun beta1(concrete: ConcreteMaterial) = concrete.betaOne(CodeFamily.ACI_318)

    override fun elasticModulusMpa(concrete: ConcreteMaterial) =
        concrete.elasticModulusMpa(CodeFamily.ACI_318)                 // 4700√f'c

    override fun minFlexuralSteelRatio(concrete: ConcreteMaterial, steel: SteelMaterial) =
        maxOf(0.25 * sqrt(concrete.cylinderStrengthMpa) / steel.yieldMpa, 1.4 / steel.yieldMpa)  // §9.6.1.2

    override fun maxFlexuralSteelRatio(concrete: ConcreteMaterial, steel: SteelMaterial) =
        0.85 * beta1(concrete) * (concrete.cylinderStrengthMpa / steel.yieldMpa) * 0.375         // εt ≥ 0.005

    /** US soft-metric bar sizes (#4–#10): 12.7,15.9,19.1,22.2,25.4,28.7,32.3 → rounded menu. */
    override val barMenuMm: List<Double> = listOf(12.0, 16.0, 19.0, 22.0, 25.0, 29.0, 32.0)

    // ── Shear: ACI 318-19 §22.5 / §9.6.3 / §9.7.6.2 (φshear = 0.75) ──

    private val phiShear = 0.75

    override fun concreteShearCapacityKn(b: Double, d: Double, concrete: ConcreteMaterial) =
        phiShear * 0.17 * kotlin.math.sqrt(concrete.cylinderStrengthMpa) * b * d / 1000.0

    override fun maxShearCapacityKn(b: Double, d: Double, concrete: ConcreteMaterial): Double {
        val vc = 0.17 * kotlin.math.sqrt(concrete.cylinderStrengthMpa) * b * d / 1000.0
        val vsMax = 0.66 * kotlin.math.sqrt(concrete.cylinderStrengthMpa) * b * d / 1000.0
        return phiShear * (vc + vsMax)
    }

    override fun minShearReinforcementMm2PerM(b: Double, concrete: ConcreteMaterial, steel: SteelMaterial) =
        maxOf(
            0.062 * kotlin.math.sqrt(concrete.cylinderStrengthMpa) * b / steel.yieldMpa,
            0.35 * b / steel.yieldMpa
        ) * 1000.0

    override fun maxStirrupSpacingMm(d: Double, vsAboveHalfVc: Boolean, vsAboveLimit: Boolean) =
        if (vsAboveLimit) minOf(d / 4.0, 300.0) else minOf(d / 2.0, 600.0)

    override fun stirrupDiameterMm(sectionWidthMm: Double, vuExceedsHalfConcrete: Boolean) =
        if (vuExceedsHalfConcrete) 10.0 else 8.0

    // ── Deflection screening: ACI 318-19 Table 24.2.2 ──
    // NOTE: basic ratios are ACI's own table (16/21/8) — NOT the ECP 20/26/7 set.

    /** Footnote multiplier for fy ≠ 420 MPa: (0.4 + fy/700). */
    override fun spanDepthModificationFactor(fyMpa: Double, rhoPercent: Double) =
        0.4 + fyMpa / 700.0

    override fun deflectionLimitMm(spanM: Double) = spanM * 1000.0 / 250.0  // Table 24.2.2 immediate + long-term framing

    override fun basicSpanDepthRatio(support: com.civileg.core.calculations.entities.SupportCondition) =
        when (support) {
            com.civileg.core.calculations.entities.SupportCondition.SIMPLY_SUPPORTED -> 16.0
            com.civileg.core.calculations.entities.SupportCondition.CONTINUOUS -> 21.0   // both ends continuous
            com.civileg.core.calculations.entities.SupportCondition.CANTILEVER -> 8.0
        }

    /** ACI 318-19 §24.3.2 crack control — deemed-to-satisfy using spacing formula. */
    override fun maxTensionBarSpacingMm(clearCoverMm: Double, fyMpa: Double): Double? {
        val barSpacing = 380.0 * (280.0 / fyMpa) - 2.5 * clearCoverMm
        return barSpacing.coerceIn(150.0, 300.0)
    }

    // ── Column rules (ACI 318-19 §22.4.2, §22.4.2.1, §25.7.2) ──

    override val axialPhiTied = 0.65
    override val axialPhiSpiral = 0.75
    override val smallEccentricityFactor: Double? = null   // ACI uses φ only

    /** ACI 318-19 Table 22.4.2.1: 0.80·P0 tied / 0.85·P0 spiral. */
    override fun maxAxialFactor(isSpiral: Boolean) = if (isSpiral) 0.85 else 0.80

    override fun minColumnSteelRatio() = 0.01     // §10.6.1: 1%
    override fun maxColumnSteelRatio() = 0.08     // §10.6.1: 8%

    override fun tiesMaxSpacingMm(longitudinalBarDiaMm: Double, tieDiaMm: Double, leastSectionDimMm: Double) =
        minOf(16.0 * longitudinalBarDiaMm, 48.0 * tieDiaMm, leastSectionDimMm, 300.0)

    override fun columnShearMaxSpacingMm(sectionDimMm: Double, d: Double) =
        minOf(d / 2.0, 48.0 * 10.0, 300.0)        // §22.5: min(d/2, 48db_tie, 300); db_tie=10 default

    // ── Slab rules (ACI 318-19 §8 DDM-inspired coefficients) ──

    override fun twoWaySlabCoefficients(aspectRatio: Double, allEdgesFixed: Boolean): SlabMomentCoeffs {
        val cs = if (allEdgesFixed) 0.033 else 0.076   // edge moment coeff (short)
        val cm = if (allEdgesFixed) 0.063 else 0.071   // edge moment coeff (long)
        val shortCoeff = when {
            aspectRatio <= 1.0 -> 0.08
            aspectRatio <= 1.5 -> 0.070
            aspectRatio <= 2.0 -> 0.050
            else -> 0.045
        }
        // longCoeff = shortCoeff / ratio²  (ACI distribution between directions)
        val longCoeff = shortCoeff * (1.0 / (aspectRatio * aspectRatio)).coerceAtMost(1.0)
        return SlabMomentCoeffs(posShort = shortCoeff, negShort = cs, posLong = longCoeff, negLong = cm)
    }

    override fun minSlabThicknessRatio(support: com.civileg.core.calculations.entities.SupportCondition) =
        when (support) {
            com.civileg.core.calculations.entities.SupportCondition.SIMPLY_SUPPORTED -> 20.0   // Table 7.3.1.1
            com.civileg.core.calculations.entities.SupportCondition.CONTINUOUS -> 28.0
            com.civileg.core.calculations.entities.SupportCondition.CANTILEVER -> 8.0
        }

    // ── Torsion: ACI 318-19 §22.7 (space-truss, φ = 0.75) ──

    override fun torsionConcreteRootMpa(concrete: ConcreteMaterial) =
        kotlin.math.sqrt(concrete.cylinderStrengthMpa)

    override val torsionPhi: Double? = 0.75

    override fun torsionThresholdTorqueNmm(b: Double, h: Double, concrete: ConcreteMaterial) =
        torsionTorqueNmm(TORSION_THRESHOLD_K, torsionConcreteRootMpa(concrete), b, h)

    override fun torsionCrackingTorqueNmm(b: Double, h: Double, concrete: ConcreteMaterial) =
        torsionTorqueNmm(TORSION_CRACKING_K, torsionConcreteRootMpa(concrete), b, h)

    override fun torsionMaxTorqueNmm(b: Double, h: Double, concrete: ConcreteMaterial) =
        torsionTorqueNmm(TORSION_ACI_MAX_K, torsionConcreteRootMpa(concrete), b, h)

    override fun torsionTransverseDesignMpa(steel: SteelMaterial) = steel.yieldMpa

    override fun torsionMinTransverseMm2PerMm(b: Double, concrete: ConcreteMaterial, steel: SteelMaterial) =
        0.5 * maxOf(
            0.062 * kotlin.math.sqrt(concrete.cylinderStrengthMpa) * b / steel.yieldMpa,
            0.35 * b / steel.yieldMpa
        )   // §9.6.4.2(a): (Av + 2At)/s min, torsion-only (Av = 0) → At/s = ½·max(...)

    // ── Isolated footing rules (ACI 318-19 §13, §22.6.5, §7.7.1) ──

    override fun footingMinReinRatio() = 0.0018                // §13.3.1
    override fun footingMinReinStressCoef() = 1.33             // §13.3.1 minimum pair (√f'c/fy)
    override fun footingMinThicknessMm() = 300.0               // §13.3.1.2 (soil-bearing)
    override fun footingCoverMm() = 75.0                       // §20.5.1.3 (cast against soil)
    override fun footingOneWayShearStressMpa(concrete: ConcreteMaterial) =
        0.75 * 0.17 * kotlin.math.sqrt(concrete.cylinderStrengthMpa)  // §22.5.5.1 (incl. φshear)
    override fun footingPunchingBaseStressMpa(concrete: ConcreteMaterial) =
        0.33 * kotlin.math.sqrt(concrete.cylinderStrengthMpa)         // §22.6.5.2(b) base term
    override val footingPunchingPhi: Double? = 0.75

    // ── Stair rules (ACI 318-19 Ch.9/22 waist slab; app ACIStaircase golden match) ──

    override val stairMinWaistMm = 125.0                  // 5" minimum practical
    override val stairCoverMm = 38.0                      // interior cover
    override val stairStirrupEstimateMm = 10.0            // app back-calc seed
    override fun stairEffectiveDepthMm(waistThicknessMm: Double) =
        waistThicknessMm - stairCoverMm - stairStirrupEstimateMm / 2.0 - 6.0
    override val stairMaxRiserMm = 178.0                  // IBC 1011.5.2 7"
    override val stairMinGoingMm = 279.0                  // IBC 1011.5.2 11"
    override val stairComfortMinMm = 580.0
    override val stairComfortMaxMm = 640.0
    override val stairRiserCountIdealGoingBaseMm = 610.0
    override val stairAutoComfortTargetMm = 610.0
    override val stairAutoBestRiserStartMm = 178.0
    override val stairAutoBestRiserMinMm = 150.0
    override val stairMinSteelRatio = 0.0018              // §7.6.1.1 (Grade 60 slabs)
    override fun stairMinSteelAreaMm2(b: Double, h: Double, d: Double, concrete: ConcreteMaterial, steel: SteelMaterial) =
        stairMinSteelRatio * b * h                        // app form: ρmin × b × h
    override val stairDistributionMinRatio = 0.0018
    override fun stairMainBarMenu(): List<Double> = listOf(12.0, 16.0, 19.0, 22.0, 25.0, 29.0, 32.0)
    override fun stairMainBarFallback() = 16.0
    override fun stairDistBarMenu(): List<Double> = listOf(10.0, 12.0, 16.0)
    override fun stairDistBarFallback() = 10.0
    override val stairShearStirrupDiameterMm = 10.0
    override fun stairStirrupSpacingMaxMm() = 300.0
    override fun stairDeflectionModificationFactor(rhoPercent: Double) =
        1.0   // app fyFactor = min(1.0, 0.4 + 420/420) = 1.0 at Grade 420
    override fun stairDeflectionLimitMm(spanM: Double) = spanM * 1000.0 / 240.0  // Table 24.2.2 floors L/240

    // ── Tank rules (ACI 350-06 environmental structures; app ACITank golden match) ──

    override fun tankMinRho(concrete: ConcreteMaterial, steel: SteelMaterial) =
        maxOf(0.0020, 1.33 * kotlin.math.sqrt(concrete.cylinderStrengthMpa) / steel.yieldMpa)  // ACI 350-06 §7.12.2
    override fun tankMinRhoFlat() = 0.0020
    override fun tankCrackWidthLimitMm() = 0.25   // ACI 350-06 (0.25 mm vs ECP 0.2)
    override fun tankFluidLoadFactor() = 1.4      // ACI 350-06 Sec. 9.2.1 (liquid F)
    /** ACI 350-06 §9.2.7 environmental-structure φ for flexure under fluid loads. */
    override val tankPhiFlexure: Double? = 0.65
    override fun tankCoverMm() = 50.0             // ACI 350 water face
    override fun tankEffectiveDepthMm(thicknessMm: Double) = thicknessMm - tankCoverMm() - 10.0
    /** ACI/SBC schedule bars by FLOORING the spacing, clamped to 100..300 mm. */
    override fun tankSpacingMm(barsPerMeter: Int) =
        kotlin.math.floor(1000.0 / barsPerMeter).coerceIn(100.0, 300.0)
    override fun tankBarMenuMm(): List<Double> = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0)
    override fun tankBarFallback() = 16.0
    /** ACI 350 hoop fct = 0.62√f'c (cylinder). */
    override fun tankHoopTensionLimitMpa(concrete: ConcreteMaterial) =
        0.62 * kotlin.math.sqrt(concrete.cylinderStrengthMpa)
    override fun tankRhoMax() = 0.025
    override fun tankMinWallThicknessMm() = 200.0
    override fun tankMinBaseThicknessMm() = 250.0

    // ── Retaining-wall rules (ACI 318 earth-retaining; app ACIRetainingWall golden match) ──

    /** Shared legacy market list — identical for every family. */
    override fun retainingWallBarMenuMm(): List<Double> =
        listOf(10.0, 12.0, 13.0, 16.0, 18.0, 20.0, 22.0, 25.0, 28.0, 32.0)
    override fun retainingWallCoverMm() = 75.0                      // §20.5.1.3 earth contact
    override fun retainingWallStirrupEstimateMm() = 10.0            // app back-calc seed
    override fun retainingWallLateralLoadFactor() = 1.6             // §5.3 lateral (H)
    override fun retainingWallDeadLoadFactor() = 1.2                // §5.3 dead (D); soil = D
    override fun retainingWallMinSteelRatio() = 0.0018              // §11.6.1 Grade 60 wall min
    override fun retainingWallMaxFlexuralSteelRatio() = 0.025        // app ρ-coerced ceiling
    override fun retainingWallMinSteelStressCoef() = 0.0            // flat ρmin only
    override fun retainingWallMinSteelRatioNote() = 0.0018
    override fun retainingWallOtFsLimit() = 2.0                     // app OT limit for ACI
    override fun retainingWallSlidingFsLimit() = 1.5
    override val retainingWallShearPhi: Double? = 0.75              // §21.2.1
    /** ACI stem distribution = max(ρmin·b·d/4, 100) (app golden). */
    override fun retainingWallDistributionAreaMm2(b: Double, d: Double) =
        maxOf(retainingWallMinSteelRatio() * b * d * 0.25, 100.0)
}

/** SBC 304-2018 — φ-based family issued from ACI 318 formulations with Saudi-specific
 *  stair deltas (higher cover, thicker waist, hotter-climate ρmin). Mirrors Aci318Params
 *  for the shared member rules (SBC 304 is published as an ACI-based code). */
object Sbc304Params : ConcreteCodeParams {
    override val family = CodeFamily.SBC_304
    override val reference = "SBC 304-2018"
    override val gammas: PartialSafetyFactors? = null
    override val phiFlexure: Double? = 0.90

    override fun designBlockStressMpa(concrete: ConcreteMaterial) =
        0.85 * concrete.cylinderStrengthMpa

    override fun beta1(concrete: ConcreteMaterial) = concrete.betaOne(CodeFamily.SBC_304)

    override fun elasticModulusMpa(concrete: ConcreteMaterial) =
        concrete.elasticModulusMpa(CodeFamily.SBC_304)                 // ACI 4700√f'c

    override fun minFlexuralSteelRatio(concrete: ConcreteMaterial, steel: SteelMaterial) =
        maxOf(0.25 * sqrt(concrete.cylinderStrengthMpa) / steel.yieldMpa, 1.4 / steel.yieldMpa)

    override fun maxFlexuralSteelRatio(concrete: ConcreteMaterial, steel: SteelMaterial) =
        0.85 * beta1(concrete) * (concrete.cylinderStrengthMpa / steel.yieldMpa) * 0.375

    override val barMenuMm: List<Double> = listOf(12.0, 16.0, 19.0, 22.0, 25.0, 29.0, 32.0)

    // ── Shear: SBC 304 (ACI-based) §11 / §9.6.3 / §9.7.6.2 ──

    private val phiShear = 0.75

    override fun concreteShearCapacityKn(b: Double, d: Double, concrete: ConcreteMaterial) =
        phiShear * 0.17 * kotlin.math.sqrt(concrete.cylinderStrengthMpa) * b * d / 1000.0

    override fun maxShearCapacityKn(b: Double, d: Double, concrete: ConcreteMaterial): Double {
        val vc = 0.17 * kotlin.math.sqrt(concrete.cylinderStrengthMpa) * b * d / 1000.0
        val vsMax = 0.66 * kotlin.math.sqrt(concrete.cylinderStrengthMpa) * b * d / 1000.0
        return phiShear * (vc + vsMax)
    }

    override fun minShearReinforcementMm2PerM(b: Double, concrete: ConcreteMaterial, steel: SteelMaterial) =
        maxOf(
            0.062 * kotlin.math.sqrt(concrete.cylinderStrengthMpa) * b / steel.yieldMpa,
            0.35 * b / steel.yieldMpa
        ) * 1000.0

    override fun maxStirrupSpacingMm(d: Double, vsAboveHalfVc: Boolean, vsAboveLimit: Boolean) =
        if (vsAboveLimit) minOf(d / 4.0, 300.0) else minOf(d / 2.0, 600.0)

    override fun stirrupDiameterMm(sectionWidthMm: Double, vuExceedsHalfConcrete: Boolean) =
        if (vuExceedsHalfConcrete) 10.0 else 8.0

    // ── Deflection: SBC 304 (ACI Table 24.2.2 set) ──

    override fun spanDepthModificationFactor(fyMpa: Double, rhoPercent: Double) =
        0.4 + fyMpa / 700.0

    override fun deflectionLimitMm(spanM: Double) = spanM * 1000.0 / 250.0

    override fun basicSpanDepthRatio(support: com.civileg.core.calculations.entities.SupportCondition) =
        when (support) {
            com.civileg.core.calculations.entities.SupportCondition.SIMPLY_SUPPORTED -> 16.0
            com.civileg.core.calculations.entities.SupportCondition.CONTINUOUS -> 21.0
            com.civileg.core.calculations.entities.SupportCondition.CANTILEVER -> 8.0
        }

    override fun maxTensionBarSpacingMm(clearCoverMm: Double, fyMpa: Double): Double? {
        val barSpacing = 380.0 * (280.0 / fyMpa) - 2.5 * clearCoverMm
        return barSpacing.coerceIn(150.0, 300.0)
    }

    // ── Column rules (SBC 304, ACI-based) ──

    override val axialPhiTied = 0.65
    override val axialPhiSpiral = 0.75
    override val smallEccentricityFactor: Double? = null

    override fun maxAxialFactor(isSpiral: Boolean) = if (isSpiral) 0.85 else 0.80

    override fun minColumnSteelRatio() = 0.01
    override fun maxColumnSteelRatio() = 0.08

    override fun tiesMaxSpacingMm(longitudinalBarDiaMm: Double, tieDiaMm: Double, leastSectionDimMm: Double) =
        minOf(16.0 * longitudinalBarDiaMm, 48.0 * tieDiaMm, leastSectionDimMm, 300.0)

    override fun columnShearMaxSpacingMm(sectionDimMm: Double, d: Double) =
        minOf(d / 2.0, 48.0 * 10.0, 300.0)

    // ── Slab rules (SBC 304, ACI §8 set) ──

    override fun twoWaySlabCoefficients(aspectRatio: Double, allEdgesFixed: Boolean): SlabMomentCoeffs =
        Aci318Params.twoWaySlabCoefficients(aspectRatio, allEdgesFixed)

    override fun minSlabThicknessRatio(support: com.civileg.core.calculations.entities.SupportCondition) =
        when (support) {
            com.civileg.core.calculations.entities.SupportCondition.SIMPLY_SUPPORTED -> 20.0
            com.civileg.core.calculations.entities.SupportCondition.CONTINUOUS -> 28.0
            com.civileg.core.calculations.entities.SupportCondition.CANTILEVER -> 8.0
        }

    // ── Torsion: SBC 304 (ACI §22.7 set) ──

    override fun torsionConcreteRootMpa(concrete: ConcreteMaterial) =
        kotlin.math.sqrt(concrete.cylinderStrengthMpa)

    override val torsionPhi: Double? = 0.75

    override fun torsionThresholdTorqueNmm(b: Double, h: Double, concrete: ConcreteMaterial) =
        torsionTorqueNmm(TORSION_THRESHOLD_K, torsionConcreteRootMpa(concrete), b, h)

    override fun torsionCrackingTorqueNmm(b: Double, h: Double, concrete: ConcreteMaterial) =
        torsionTorqueNmm(TORSION_CRACKING_K, torsionConcreteRootMpa(concrete), b, h)

    override fun torsionMaxTorqueNmm(b: Double, h: Double, concrete: ConcreteMaterial) =
        torsionTorqueNmm(TORSION_ACI_MAX_K, torsionConcreteRootMpa(concrete), b, h)

    override fun torsionTransverseDesignMpa(steel: SteelMaterial) = steel.yieldMpa

    override fun torsionMinTransverseMm2PerMm(b: Double, concrete: ConcreteMaterial, steel: SteelMaterial) =
        0.5 * maxOf(
            0.062 * kotlin.math.sqrt(concrete.cylinderStrengthMpa) * b / steel.yieldMpa,
            0.35 * b / steel.yieldMpa
        )

    // ── Isolated footing rules (SBC 304 §13 = ACI set) ──

    override fun footingMinReinRatio() = 0.0018
    override fun footingMinReinStressCoef() = 1.33
    override fun footingMinThicknessMm() = 300.0
    override fun footingCoverMm() = 75.0
    override fun footingOneWayShearStressMpa(concrete: ConcreteMaterial) =
        0.75 * 0.17 * kotlin.math.sqrt(concrete.cylinderStrengthMpa)
    override fun footingPunchingBaseStressMpa(concrete: ConcreteMaterial) =
        0.33 * kotlin.math.sqrt(concrete.cylinderStrengthMpa)
    override val footingPunchingPhi: Double? = 0.75

    // ── Stair rules (SBC 304; app SBCStaircase golden match) ──

    override val stairMinWaistMm = 130.0                   // SBC: thicker than ACI for hot climate
    override val stairCoverMm = 40.0                       // §7.7 interior (corrosion allowance)
    override val stairStirrupEstimateMm = 10.0             // app back-calc seed
    override fun stairEffectiveDepthMm(waistThicknessMm: Double) =
        waistThicknessMm - stairCoverMm - stairStirrupEstimateMm / 2.0 - 6.0
    override val stairMaxRiserMm = 178.0
    override val stairMinGoingMm = 279.0
    override val stairComfortMinMm = 580.0
    override val stairComfortMaxMm = 640.0
    override val stairRiserCountIdealGoingBaseMm = 610.0
    override val stairAutoComfortTargetMm = 610.0
    override val stairAutoBestRiserStartMm = 175.0         // app-specific scan seed
    override val stairAutoBestRiserMinMm = 150.0
    override val stairMinSteelRatio = 0.002                // hot/arid climate durability
    override fun stairMinSteelAreaMm2(b: Double, h: Double, d: Double, concrete: ConcreteMaterial, steel: SteelMaterial) =
        stairMinSteelRatio * b * d                         // app form: ρmin × b × d
    override val stairDistributionMinRatio = 0.002
    override fun stairMainBarMenu(): List<Double> = listOf(12.0, 16.0, 19.0, 22.0, 25.0, 29.0, 32.0)
    override fun stairMainBarFallback() = 16.0
    override fun stairDistBarMenu(): List<Double> = listOf(10.0, 12.0, 16.0)
    override fun stairDistBarFallback() = 10.0
    override val stairShearStirrupDiameterMm = 10.0
    override fun stairStirrupSpacingMaxMm() = 300.0
    override fun stairDeflectionModificationFactor(rhoPercent: Double) = 1.0
    override fun stairDeflectionLimitMm(spanM: Double) = spanM * 1000.0 / 240.0

    // ── Tank rules (SBC 304-2018 water-retaining = ACI 350 set, Saudi market; app SBCTank golden match) ──

    override fun tankMinRho(concrete: ConcreteMaterial, steel: SteelMaterial) =
        maxOf(0.0020, 1.33 * kotlin.math.sqrt(concrete.cylinderStrengthMpa) / steel.yieldMpa)
    override fun tankMinRhoFlat() = 0.0020
    override fun tankCrackWidthLimitMm() = 0.25
    override fun tankFluidLoadFactor() = 1.4
    override val tankPhiFlexure: Double? = 0.90      // SBC 304 keeps §9.3 flexure φ
    override fun tankCoverMm() = 50.0
    override fun tankEffectiveDepthMm(thicknessMm: Double) = thicknessMm - tankCoverMm() - 10.0
    override fun tankSpacingMm(barsPerMeter: Int) =
        kotlin.math.floor(1000.0 / barsPerMeter).coerceIn(100.0, 300.0)
    /** Saudi market bars: 12,14,16,20,25,32 mm (14/16/20/25/32 common; 12 rare). */
    override fun tankBarMenuMm(): List<Double> = listOf(12.0, 14.0, 16.0, 20.0, 25.0, 32.0)
    override fun tankBarFallback() = 16.0
    override fun tankHoopTensionLimitMpa(concrete: ConcreteMaterial) =
        0.62 * kotlin.math.sqrt(concrete.cylinderStrengthMpa)
    override fun tankRhoMax() = 0.025
    override fun tankMinWallThicknessMm() = 200.0
    override fun tankMinBaseThicknessMm() = 250.0

    // ── Retaining-wall rules (SBC 304 earth-retaining = ACI 318 maths with Saudi
    //    FS/notes; app SBCRetainingWall wraps ACIRetainingWall — golden match) ──

    /** Shared legacy market list — identical for every family. */
    override fun retainingWallBarMenuMm(): List<Double> =
        listOf(10.0, 12.0, 13.0, 16.0, 18.0, 20.0, 22.0, 25.0, 28.0, 32.0)
    override fun retainingWallCoverMm() = 75.0          // app wraps ACI (earth contact)
    override fun retainingWallStirrupEstimateMm() = 10.0
    override fun retainingWallLateralLoadFactor() = 1.6
    override fun retainingWallDeadLoadFactor() = 1.2
    /** SBC design maths are ACI's (wrap) — 0.0018, NOT the declared 0.002. */
    override fun retainingWallMinSteelRatio() = 0.0018
    override fun retainingWallMaxFlexuralSteelRatio() = 0.025
    override fun retainingWallMinSteelStressCoef() = 0.0
    /** SBC's declared min-ρ (0.002) only appears in the note text (legacy). */
    override fun retainingWallMinSteelRatioNote() = 0.002
    override fun retainingWallOtFsLimit() = 1.5         // SBC §4 OT FS = 1.5
    override fun retainingWallSlidingFsLimit() = 1.5
    override val retainingWallShearPhi: Double? = 0.75
    override fun retainingWallDistributionAreaMm2(b: Double, d: Double) =
        maxOf(retainingWallMinSteelRatio() * b * d * 0.25, 100.0)
}
