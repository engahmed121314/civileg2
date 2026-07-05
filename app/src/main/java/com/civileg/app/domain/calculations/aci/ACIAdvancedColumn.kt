package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.usecases.AnalyzeRebarInventory
import kotlin.math.*

/**
 * تصميم أعمدة متقدم حسب الكود الأمريكي ACI 318-19
 * يغطي جميع حالات التصميم: النحافة، الانحناء الثنائي، الأعمدة الحلزونية،
 * قص الاختراق، منحنى التفاعل، وتكبير العزوم
 */
class ACIAdvancedColumn : ColumnDesign {

    companion object {
        private const val PHI_TIED = 0.65          // ACI 21.2.1(b) - tied columns
        private const val PHI_SPIRAL = 0.75        // ACI 21.2.1(b) - spiral columns
        private const val PHI_FLEXURE = 0.9        // ACI 21.2.1(a) - tension-controlled
        private const val PHI_SHEAR = 0.75         // ACI 21.2.1(c) - shear
        private const val BETA_1_DEFAULT = 0.85    // for fc' <= 28 MPa (ACI 22.2.2.4.1)
        private const val EPSILON_CU = 0.003       // ACI 22.2.2.1 - max concrete strain
        private const val EPSILON_T_MIN = 0.005    // tension-controlled limit (ACI 21.2.2)
        private const val EPSILON_TY = 0.002       // compression-controlled limit (ACI Table 21.2.2)
        private const val NS_LIMIT = 22.0          // tied columns (ACI 22.4.2.1)
        private const val NS_LIMIT_SPIRAL = 28.0   // spiral columns (ACI 22.4.2.1)
        private const val ES = 200_000.0           // Steel modulus of elasticity (MPa)
        private const val LAMBDA = 1.0             // Normal-weight concrete (ACI 19.2.4.1)
        private const val CLEAR_COVER = 40.0       // mm - ACI 20.6.1
        private const val TIE_BAR_EXTRA = 10.0     // mm - tie bar diameter estimate
        private const val BETA_DNS = 0.6           // Sustained load ratio for creep

        /** ACI standard bar diameters (No.3 through No.11 in metric) */
        private val ACI_BAR_DIAMETERS = listOf(
            10.0, 12.0, 14.0, 16.0, 19.0, 22.0, 25.0, 29.0, 32.0, 36.0
        )
    }

    private val baseDesign = ACIColumn()
    private val inventoryAnalyzer = AnalyzeRebarInventory()

    // ═══════════════════════════════════════════════════════════════════
    // MAIN DESIGN FUNCTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * تصميم عمود متقدم حسب ACI 318-19
     * Advanced column design covering slenderness, biaxial bending, interaction diagrams,
     * spiral/tied reinforcement, and punching shear checks.
     *
     * @param columnType نوع العمود وأبعاده
     * @param fcu مقاومة الخرسانة للمكعب (MPa)
     * @param fy إجهاد خضوع الحديد (MPa)
     * @param axialLoad الحمل المحوري التصميمي (kN)
     * @param momentX العزم حول المحور X (kN.m)
     * @param momentY العزم حول المحور Y (kN.m)
     * @param unsupportedLength الطول غير المدعوم (m)
     * @param endConditions ظروف التثبيت
     * @param connectedSlab نوع البلاطة المتصلة
     * @param hasCap هل يوجد رأس عمود (column cap)
     * @param inventory مخزون الحديد المتاح
     * @param loadCombination مجموعة التحميل
     * @param isSpiral هل العمود حلزوني
     * @param clearCover الغطاء الخرساني (mm)
     * @param slabDepth سمك البلاطة للقص الاختراقي (mm)
     */
    fun designAdvancedColumn(
        columnType: ColumnType,
        fcu: Double,
        fy: Double,
        axialLoad: Double,
        momentX: Double,
        momentY: Double,
        unsupportedLength: Double,
        endConditions: ColumnEndConditions,
        connectedSlab: ConnectedSlabType,
        hasCap: Boolean,
        inventory: RebarInventory?,
        loadCombination: LoadCombination,
        isSpiral: Boolean = false,
        clearCover: Double = CLEAR_COVER,
        slabDepth: Double = 200.0
    ): AdvancedColumnResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val fcPrime = 0.8 * fcu // Cube to cylinder conversion for ACI

        // ── Section geometry ──
        val (b, h) = getColumnDimensions(columnType)
        val Ag = columnType.getGrossArea()

        // ── Step 1: Slenderness analysis ──
        val (kFactor, isSway) = calculateEffectiveLengthFactor(endConditions)
        val r = getRadiusOfGyration(columnType)
        val slendernessRatio = calculateSlendernessRatio(kFactor, unsupportedLength, r)
        val short = isShortColumn(slendernessRatio, isSpiral)

        if (!short) {
            warnings.add("⚠️ عمود نحيف! يجب مراعاة تأثيرات الدرجة الثانية (P-Delta)")
            codeNotes.add("ACI 318-22.4: Slenderness ratio λ=%.1f > %.0f".format(slendernessRatio, if (isSpiral) NS_LIMIT_SPIRAL else NS_LIMIT))
        }

        // ── Step 2: Moment magnification for slender columns ──
        val Ig = getGrossMomentOfInertia(columnType)
        val Ec = 4700.0 * sqrt(fcPrime) // ACI 19.2.2.1
        val Is = calculateSteelMomentOfInertia(columnType, Ag, clearCover)
        val EI = calculateEffectiveEI(Ec, Ig, Is, Ag)

        val KLu = kFactor * unsupportedLength // m
        val Pc = calculateCriticalLoad(EI, KLu)
        val Pu = axialLoad

        val magnifierX = if (!short) calculateMomentMagnification(Pu, momentX, Pc) else 1.0
        val magnifierY = if (!short) calculateMomentMagnification(Pu, momentY, Pc) else 1.0

        val MxDesign = momentX * magnifierX
        val MyDesign = momentY * magnifierY

        if (magnifierX > 1.05 || magnifierY > 1.05) {
            codeNotes.add("ACI 22.4.2.1: Moment magnification δx=%.2f, δy=%.2f".format(magnifierX, magnifierY))
        }

        // ── Step 3: Reinforcement design ──
        val reinforcementResult = if (short) {
            designShortColumn(
                fcPrime, fy, b, h, Ag, Pu, MxDesign, MyDesign,
                isSpiral, clearCover, warnings, codeNotes
            )
        } else {
            designShortColumn(
                fcPrime, fy, b, h, Ag, Pu, MxDesign, MyDesign,
                isSpiral, clearCover, warnings, codeNotes
            )
        }

        // ── Step 4: Capacity calculations ──
        val As = reinforcementResult.astProvided
        val axialCapacity = calculateAxialCapacityWithPhi(
            fcPrime, fy, b, h, As, Ag, isSpiral
        )

        val dEff = h - clearCover - TIE_BAR_EXTRA - reinforcementResult.barDiameter / 2
        val momentCapacityX = calculateMomentCapacity(fcPrime, fy, b, h, As, dEff, Ag, isSpiral, clearCover)
        val momentCapacityY = if (abs(b - h) > 1.0) {
            val dEffY = b - clearCover - TIE_BAR_EXTRA - reinforcementResult.barDiameter / 2
            calculateMomentCapacity(fcPrime, fy, h, b, As, dEffY, Ag, isSpiral, clearCover)
        } else {
            momentCapacityX
        }

        // ── Step 5: Biaxial bending check ──
        val biaxialCheck = if (abs(momentX) > 0.01 && abs(momentY) > 0.01) {
            val breslerResult = checkBiaxialBresler(
                fcPrime, fy, b, h, Ag, As, Pu, MxDesign, MyDesign, isSpiral, clearCover
            )
            if (breslerResult.isSafe) {
                breslerResult
            } else {
                // Verify with load contour method
                checkBiaxialLoadContour(
                    fcPrime, fy, b, h, Ag, As, Pu, MxDesign, MyDesign,
                    momentCapacityX, momentCapacityY, isSpiral, clearCover
                )
            }
        } else null

        if (biaxialCheck != null && !biaxialCheck.isSafe) {
            warnings.add("❌ فشل فحص الانحناء الثنائي! يجب زيادة مقطع العمود أو التسليح")
        }

        // ── Step 6: Spiral reinforcement design ──
        if (isSpiral) {
            val spiralResult = designSpiralReinforcement(fcPrime, fy, columnType, Ag, clearCover)
            codeNotes.add("ACI 25.7.6: Spiral ρs=%.4f, pitch=%.0fmm, dia=%.0fmm".format(
                spiralResult.third, spiralResult.first, spiralResult.second
            ))
        }

        // ── Step 7: Punching shear check (flat slab only) ──
        val punchingCheck = if (connectedSlab == ConnectedSlabType.FLAT) {
            checkPunchingShear(columnType, Pu, fcPrime, slabDepth, clearCover, hasCap, warnings, codeNotes)
        } else null

        // ── Step 8: Inventory analysis ──
        val inventoryAnalysis = inventory?.let { inv ->
            inventoryAnalyzer.analyze(
                requiredArea = reinforcementResult.astRequired,
                requiredLength = reinforcementResult.numberOfBars * unsupportedLength,
                inventory = inv,
                designCode = DesignCode.ACI,
                elementLength = unsupportedLength,
                cover = clearCover
            )
        }

        // ── Code notes ──
        codeNotes.add("ACI 318-19: Chapter 10 - Design of Compression Members")
        codeNotes.add("ACI 21.2.1: φ = %.2f (%s column)".format(
            if (isSpiral) PHI_SPIRAL else PHI_TIED,
            if (isSpiral) "spiral" else "tied"
        ))
        codeNotes.add("ACI 10.6.1.1: Reinforcement ratio = %.2f%%".format(As / Ag * 100))
        if (fcu > 0) {
            codeNotes.add("fc' = 0.8 × fcu = %.0f MPa (cylinder strength)".format(fcPrime))
        }
        codeNotes.add("ACI 20.6.1: Clear cover = %.0fmm".format(clearCover))

        // ── Steel weight and concrete volume per meter ──
        val steelWeightPerMeter = As / 1e6 * 7850.0 // kg/m (longitudinal bars only)
        val concreteVolumePerMeter = Ag / 1e6 // m³/m

        return AdvancedColumnResult(
            columnType = columnType,
            axialCapacity = axialCapacity,
            momentCapacityX = momentCapacityX,
            momentCapacityY = momentCapacityY,
            slendernessRatio = slendernessRatio,
            isSlender = !short,
            effectiveLength = KLu * 1000.0, // mm
            reinforcementResult = reinforcementResult,
            inventoryAnalysis = inventoryAnalysis,
            biaxialCheck = biaxialCheck,
            punchingCheck = punchingCheck,
            warnings = warnings,
            codeNotes = codeNotes,
            steelWeightPerMeter = steelWeightPerMeter,
            concreteVolumePerMeter = concreteVolumePerMeter
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // SLENDERNESS ANALYSIS  (ACI 318-22.4)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculate effective length factor K from end conditions.
     * Uses simplified alignment chart values per ACI 22.4.2.
     * Returns K factor and whether the frame is considered sway.
     */
    fun calculateEffectiveLengthFactor(endConditions: ColumnEndConditions): Pair<Double, Boolean> {
        val kTop = endConditions.topCondition.effectiveLengthFactor
        val kBottom = endConditions.bottomCondition.effectiveLengthFactor

        // Use the average of top and bottom, or the more critical value
        // For braced (non-sway) frames: 0.5 ≤ K ≤ 1.0
        // For unbraced (sway) frames: 1.0 ≤ K ≤ ∞
        val kAvg = (kTop + kBottom) / 2.0

        // Sway determination: if both ends are fixed or partially fixed → non-sway
        val isSway = when {
            endConditions.topCondition == EndCondition.PINNED ||
            endConditions.topCondition == EndCondition.ROLLER ||
            endConditions.topCondition == EndCondition.FREE -> true
            endConditions.bottomCondition == EndCondition.PINNED ||
            endConditions.bottomCondition == EndCondition.ROLLER -> true
            else -> false
        }

        // For non-sway frames, limit K to [0.5, 1.0]
        // For sway frames, K ≥ 1.0
        val kEffective = if (isSway) {
            max(1.0, kAvg)
        } else {
            kAvg.coerceIn(0.5, 1.0)
        }

        return Pair(kEffective, isSway)
    }

    /**
     * Calculate slenderness ratio λ = K × Lu / r
     * ACI 318-22.4.1
     *
     * @param K effective length factor
     * @param unsupportedLength unsupported length (m)
     * @param r radius of gyration (mm)
     * @return slenderness ratio (dimensionless)
     */
    fun calculateSlendernessRatio(K: Double, unsupportedLength: Double, r: Double): Double {
        val Lu_mm = unsupportedLength * 1000.0
        return K * Lu_mm / r
    }

    /**
     * Check if the column is short per ACI 22.4.2.1
     * λ ≤ 22 for tied columns, λ ≤ 28 for spiral columns
     */
    fun isShortColumn(slendernessRatio: Double, isSpiral: Boolean): Boolean {
        val limit = if (isSpiral) NS_LIMIT_SPIRAL else NS_LIMIT
        return slendernessRatio <= limit
    }

    /**
     * Calculate non-sway moment magnification factor δns
     * ACI 22.4.2.1: δns = Cm / (1 - Pu / (0.75 × Pc))
     *
     * Cm is taken as 1.0 (conservative for transverse loads or unknown M1/M2 ratio).
     * βdns defaults to 0.6 (typical sustained load ratio).
     */
    fun calculateMomentMagnification(Pu: Double, M: Double, Pc: Double, Cm: Double = 1.0): Double {
        if (M < 0.01 || Pc <= 0.0) return 1.0
        val ratio = Pu * 1000.0 / (0.75 * Pc) // Pu in N, Pc in N
        if (ratio >= 1.0) return 10.0 // Cap to indicate failure
        return (Cm / (1.0 - ratio)).coerceIn(1.0, 10.0)
    }

    /**
     * Calculate sway moment magnification factor δs
     * ACI 22.4.4: δs = 1 / (1 - ΣPu / (0.75 × ΣPc))
     *
     * For a single column: δs = 1 / (1 - Pu / (0.75 × Pc))
     */
    fun calculateSwayMomentMagnification(Pu: Double, Pc: Double): Double {
        val ratio = Pu * 1000.0 / (0.75 * Pc)
        if (ratio >= 1.0) return 10.0
        return (1.0 / (1.0 - ratio)).coerceIn(1.0, 10.0)
    }

    /**
     * Calculate effective flexural stiffness EI per ACI 22.4.2.4
     * EI = 0.4 × Ec × Ig / (1 + βdns)
     *
     * @param Ec concrete elastic modulus (MPa)
     * @param Ig gross moment of inertia (mm⁴)
     * @param Is steel moment of inertia (mm⁴)
     * @param Ag gross area (mm²)
     * @param betaDns ratio of max sustained axial load to max factored axial load
     * @return EI in N·mm²
     */
    fun calculateEffectiveEI(
        Ec: Double, Ig: Double, Is: Double, Ag: Double,
        betaDns: Double = BETA_DNS
    ): Double {
        // Simplified: EI = 0.4 × Ec × Ig / (1 + βdns)  [ACI 22.4.2.4(a)]
        val eiSimplified = 0.4 * Ec * Ig / (1.0 + betaDns)

        // Detailed: EI = (0.2 × Ec × Ig + Es × Is) / (1 + βdns)  [ACI 22.4.2.4(b)]
        val eiDetailed = (0.2 * Ec * Ig + ES * Is) / (1.0 + betaDns)

        // Use the larger of the two for conservative design
        return max(eiSimplified, eiDetailed)
    }

    /**
     * Calculate critical buckling load Pc
     * Pc = π² × EI / (K × Lu)²
     *
     * @param EI effective stiffness (N·mm²)
     * @param KLu K × unsupported length (m)
     * @return Pc in N
     */
    fun calculateCriticalLoad(EI: Double, KLu: Double): Double {
        val KLu_mm = KLu * 1000.0
        return PI * PI * EI / (KLu_mm * KLu_mm)
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERACTION DIAGRAM  (ACI 318 Chapter 10)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate a P-M interaction diagram with at least 20 points.
     * Returns list of (φPn in kN, φMn in kN·m) pairs from pure compression to pure tension.
     *
     * For rectangular columns: uses Whitney stress block with strain compatibility.
     * For circular columns: uses equivalent rectangular approximation.
     */
    fun generateInteractionDiagram(
        columnType: ColumnType,
        fcu: Double,
        fy: Double,
        reinforcementArea: Double,
        isSpiral: Boolean = false,
        clearCover: Double = CLEAR_COVER
    ): List<Pair<Double, Double>> {
        val fcPrime = 0.8 * fcu
        val (b, h) = getColumnDimensions(columnType)
        val Ag = columnType.getGrossArea()
        val As = reinforcementArea.coerceAtMost(Ag * 0.08)

        return when (columnType) {
            is ColumnType.Rectangular, is ColumnType.LShaped, is ColumnType.TShaped, is ColumnType.Composite, is ColumnType.Tubular -> {
                generateRectangularInteractionDiagram(b, h, fcPrime, fy, As, isSpiral, clearCover)
            }
            is ColumnType.Circular -> {
                generateCircularInteractionDiagram(columnType.diameter, fcPrime, fy, As, isSpiral, clearCover)
            }
        }
    }

    /**
     * Generate interaction diagram for rectangular columns using strain compatibility
     * and Whitney stress block per ACI 318 Chapter 10.
     */
    private fun generateRectangularInteractionDiagram(
        b: Double, h: Double,
        fcPrime: Double, fy: Double,
        As: Double, isSpiral: Boolean,
        clearCover: Double
    ): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        val beta1 = getBeta1(fcPrime)
        val d = h - clearCover - TIE_BAR_EXTRA
        val dPrime = clearCover + TIE_BAR_EXTRA
        val epsilonY = fy / ES

        // Bar positions: distribute around perimeter
        // 4 corners + side bars
        val numBars = max(4, (As / (PI * 16.0 * 16.0 / 4)).toInt().coerceIn(4, 16))
        val barArea = As / numBars
        val barPositions = getRectangularBarPositions(b, h, numBars, clearCover, TIE_BAR_EXTRA)

        // Balanced neutral axis depth
        val cBalanced = d * EPSILON_CU / (EPSILON_CU + epsilonY)

        // Generate 24 points from pure compression to pure tension
        val cValues = mutableListOf<Double>()

        // Pure compression region (c > h): 4 points
        cValues.add(3.0 * h)
        cValues.add(2.0 * h)
        cValues.add(1.5 * h)
        cValues.add(1.2 * h)

        // Near balanced: 3 points
        cValues.add(cBalanced * 1.5)
        cValues.add(cBalanced * 1.1)
        cValues.add(cBalanced)

        // Transition zone (cBalanced to tension-controlled): 8 points
        val cTc = d * EPSILON_CU / (EPSILON_CU + EPSILON_T_MIN) // tension-controlled NA depth
        for (i in 8 downTo 1) {
            cValues.add(cBalanced - (cBalanced - cTc) * i / 8.0)
        }

        // Tension-controlled region: 6 points
        for (i in 1..6) {
            val cVal = cTc * (1.0 - i / 7.0)
            if (cVal > 5.0) cValues.add(cVal)
        }

        // Near pure tension: 3 points
        cValues.add(15.0)
        cValues.add(10.0)
        cValues.add(5.0)

        for (c in cValues) {
            val (phiPn, phiMn) = calculateRectangularInteractionPoint(
                b, h, fcPrime, fy, beta1, barPositions, barArea, c, d, isSpiral
            )
            points.add(Pair(phiPn, phiMn))
        }

        // Pure tension point
        val phiT = if (isSpiral) PHI_SPIRAL else PHI_TIED
        val pureTensionPn = -As * fy / 1000.0 * PHI_FLEXURE
        points.add(Pair(pureTensionPn, 0.0))

        return points
    }

    /**
     * Generate interaction diagram for circular columns using equivalent rectangular approximation.
     */
    private fun generateCircularInteractionDiagram(
        diameter: Double,
        fcPrime: Double, fy: Double,
        As: Double, isSpiral: Boolean,
        clearCover: Double
    ): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        val R = diameter / 2.0
        val beta1 = getBeta1(fcPrime)
        val d = diameter - clearCover - TIE_BAR_EXTRA
        val epsilonY = fy / ES

        // Bar positions: equally spaced around the circumference
        val numBars = max(6, (As / (PI * 16.0 * 16.0 / 4)).toInt().coerceIn(6, 16))
        val barArea = As / numBars
        val barPositions = getCircularBarPositions(diameter, numBars, clearCover, TIE_BAR_EXTRA)

        val cBalanced = d * EPSILON_CU / (EPSILON_CU + epsilonY)
        val cTc = d * EPSILON_CU / (EPSILON_CU + EPSILON_T_MIN)

        val cValues = mutableListOf<Double>()
        cValues.add(3.0 * diameter)
        cValues.add(2.0 * diameter)
        cValues.add(1.5 * diameter)
        cValues.add(1.2 * diameter)
        cValues.add(cBalanced * 1.5)
        cValues.add(cBalanced * 1.1)
        cValues.add(cBalanced)

        for (i in 8 downTo 1) {
            cValues.add(cBalanced - (cBalanced - cTc) * i / 8.0)
        }
        for (i in 1..6) {
            val cVal = cTc * (1.0 - i / 7.0)
            if (cVal > 5.0) cValues.add(cVal)
        }
        cValues.add(15.0)
        cValues.add(10.0)
        cValues.add(5.0)

        for (c in cValues) {
            val (phiPn, phiMn) = calculateCircularInteractionPoint(
                diameter, R, fcPrime, fy, beta1, barPositions, barArea, c, d, isSpiral
            )
            points.add(Pair(phiPn, phiMn))
        }

        val pureTensionPn = -As * fy / 1000.0 * PHI_FLEXURE
        points.add(Pair(pureTensionPn, 0.0))

        return points
    }

    /**
     * Calculate a single point on the rectangular interaction diagram.
     * Uses Whitney stress block and strain compatibility per ACI 318.
     *
     * @param c neutral axis depth from extreme compression fiber (mm)
     * @return Pair(φPn in kN, φMn in kN·m)
     */
    private fun calculateRectangularInteractionPoint(
        b: Double, h: Double,
        fcPrime: Double, fy: Double,
        beta1: Double,
        barPositions: List<Pair<Double, Double>>,
        barArea: Double,
        c: Double, d: Double,
        isSpiral: Boolean
    ): Pair<Double, Double> {
        val a = min(beta1 * c, h) // Depth of stress block, capped at h

        // Concrete compression force: Cc = 0.85 × fc' × a × b
        val Cc = 0.85 * fcPrime * a * b
        // Cc acts at (h/2 - a/2) from centroid
        val yCc = h / 2.0 - a / 2.0

        var totalSteelForce = 0.0 // N (positive = compression)
        var totalMoment = 0.0     // N·mm (positive)

        // Find extreme tension bar strain for φ calculation
        var minTensionStrain = Double.MAX_VALUE
        val compressionFaceY = h / 2.0 // y-coordinate of compression face from centroid

        for ((yi, _) in barPositions) {
            // Distance from compression face (positive y is up, compression at top)
            val distFromCompression = compressionFaceY - yi

            // Strain at this bar: εs = εcu × (c - dist) / c
            val epsilonS = EPSILON_CU * (c - distFromCompression) / max(c, 1.0)

            // Track most extreme tension strain (most negative εs)
            if (epsilonS < minTensionStrain) {
                minTensionStrain = epsilonS
            }

            // Steel stress (capped at ±fy)
            val fs = (ES * epsilonS).coerceIn(-fy, fy)

            val barForce = barArea * fs // N, positive = compression
            totalSteelForce += barForce
            totalMoment += barForce * yi // Moment about centroid
        }

        // Pn = Cc + ΣFs (compression positive)
        val Pn = Cc + totalSteelForce

        // Mn = Cc × (h/2 - a/2) + Σ(Fs × yi) about centroid
        val Mn = Cc * yCc + totalMoment

        // φ factor based on net tensile strain
        val phi = getPhiFactor(minTensionStrain, isSpiral)

        // For high compression (c > h), limit concrete force to 0.85 × fc' × Ag
        val PnCapped = if (a >= h) {
            val steelContribution = totalSteelForce
            val concreteMax = 0.85 * fcPrime * b * h
            concreteMax + steelContribution
        } else {
            Pn
        }

        return Pair(phi * PnCapped / 1000.0, phi * abs(Mn) / 1e6)
    }

    /**
     * Calculate a single point on the circular interaction diagram.
     * Uses circular segment area for compression zone.
     */
    private fun calculateCircularInteractionPoint(
        diameter: Double, R: Double,
        fcPrime: Double, fy: Double,
        beta1: Double,
        barPositions: List<Pair<Double, Double>>,
        barArea: Double,
        c: Double, d: Double,
        isSpiral: Boolean
    ): Pair<Double, Double> {
        val a = min(beta1 * c, diameter)

        // Compression zone is a circular segment of depth a from the compression face
        // Use the equivalent rectangular approximation: Cc = 0.85 × fc' × Ac_eff
        // where Ac_eff ≈ a × 0.8 × diameter (approximate width of compression zone)
        val effectiveWidth = if (a <= R) {
            // Partial compression zone - use circular segment area
            val cosTheta = (R - a) / R
            val theta = 2.0 * acos(cosTheta.coerceIn(-1.0, 1.0))
            val segmentArea = R * R * (theta - sin(theta)) / 2.0
            // Centroid of segment from compression face
            val segmentCentroid = if (theta > 0.01) {
                R * R * R * sin(theta).pow(3) / (3.0 * segmentArea) - (R - a)
            } else {
                a / 2.0
            }
            // Effective force
            val Cc = 0.85 * fcPrime * segmentArea
            val yCc = R - a + segmentCentroid - R // from centroid (centroid at center)

            var totalSteelForce = 0.0
            var totalMoment = 0.0
            var minTensionStrain = Double.MAX_VALUE

            for ((yi, _) in barPositions) {
                val distFromCompression = R - yi // R is compression face y from centroid
                val epsilonS = EPSILON_CU * (c - distFromCompression) / max(c, 1.0)
                if (epsilonS < minTensionStrain) minTensionStrain = epsilonS

                val fs = (ES * epsilonS).coerceIn(-fy, fy)
                totalSteelForce += barArea * fs
                totalMoment += barArea * fs * yi
            }

            val Pn = Cc + totalSteelForce
            val Mn = Cc * yCc + totalMoment
            val phi = getPhiFactor(minTensionStrain, isSpiral)
            return Pair(phi * Pn / 1000.0, phi * abs(Mn) / 1e6)
        } else {
            // Full section in compression
            val Ag = PI * R * R
            val Cc = 0.85 * fcPrime * Ag

            var totalSteelForce = 0.0
            for ((yi, _) in barPositions) {
                val epsilonS = EPSILON_CU * (c - (R - yi)) / max(c, 1.0)
                val fs = (ES * epsilonS).coerceIn(-fy, fy)
                totalSteelForce += barArea * fs
            }

            val Pn = Cc + totalSteelForce
            val phi = if (isSpiral) PHI_SPIRAL else PHI_TIED
            return Pair(phi * Pn / 1000.0, 0.0)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // BIAXIAL BENDING  (ACI 318-22.10)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Bresler reciprocal load method per ACI 22.10.3.2 (Eq. 22.10.3.2):
     *   1/Pn = 1/Pnx + 1/Pny - 1/Po
     *
     * Pnx: nominal axial strength at given Mux (bending about X only)
     * Pny: nominal axial strength at given Muy (bending about Y only)
     * Po:  pure axial nominal strength
     */
    fun checkBiaxialBresler(
        fcPrime: Double, fy: Double,
        b: Double, h: Double,
        Ag: Double, As: Double,
        Pu: Double, Mx: Double, My: Double,
        isSpiral: Boolean = false,
        clearCover: Double = CLEAR_COVER
    ): BiaxialCheckResult {
        val phiComp = if (isSpiral) PHI_SPIRAL else PHI_TIED

        // Pure axial capacity Po (ACI 22.10.2.2)
        val Po = 0.85 * fcPrime * (Ag - As) + As * fy

        // Generate interaction diagrams for each axis
        val diagramX = generateInteractionDiagram(
            ColumnType.Rectangular(b, h), fcPrime / 0.8, fy, As, isSpiral, clearCover
        )
        val diagramY = generateInteractionDiagram(
            ColumnType.Rectangular(h, b), fcPrime / 0.8, fy, As, isSpiral, clearCover
        )

        // Find Pnx at Mx: interpolate on the X-axis interaction diagram
        val Pnx = interpolatePnFromDiagram(diagramX, Mx)

        // Find Pny at My: interpolate on the Y-axis interaction diagram
        val Pny = interpolatePnFromDiagram(diagramY, My)

        // Bresler equation: 1/Pn = 1/Pnx + 1/Pny - 1/Po
        val invPnx = if (abs(Pnx) > 0.01) 1.0 / Pnx else 0.0
        val invPny = if (abs(Pny) > 0.01) 1.0 / Pny else 0.0
        val invPo = 1.0 / Po

        var invPn = invPnx + invPny - invPo
        val PnBresler = if (abs(invPn) > 1e-10) 1.0 / invPn else Double.MAX_VALUE

        // For tension: PnBresler is negative, check with absolute values
        val isSafe = if (Pu >= 0) {
            // Compression case: φPn ≥ Pu
            phiComp * PnBresler / 1000.0 >= Pu * 0.95 // 5% tolerance
        } else {
            // Tension case
            PHI_FLEXURE * abs(PnBresler) / 1000.0 >= abs(Pu) * 0.95
        }

        val mxRatio = if (abs(Pnx) > 0.01) abs(Pu * 1000.0) / abs(Pnx) else 0.0
        val myRatio = if (abs(Pny) > 0.01) abs(Pu * 1000.0) / abs(Pny) else 0.0
        val interactionFactor = if (isSafe) mxRatio.coerceAtMost(myRatio) else max(mxRatio, myRatio)

        return BiaxialCheckResult(
            mxRatio = mxRatio,
            myRatio = myRatio,
            interactionFactor = if (PnBresler > 0) Pu * 1000.0 / PnBresler else abs(Pu * 1000.0 / PnBresler),
            isSafe = isSafe,
            formula = "ACI 22.10.3.2: 1/Pn = 1/Pnx + 1/Pny - 1/Po, Pn=%.0fkN".format(PnBresler / 1000.0)
        )
    }

    /**
     * Load contour method (approximate interaction surface) per ACI 22.10.3.
     * Uses the Hsu equation: (Mux/Mnx)^α + (Muy/Mny)^α ≤ 1.0
     * where α ≈ 1.15 to 2.0 depending on Pn/Po ratio.
     */
    fun checkBiaxialLoadContour(
        fcPrime: Double, fy: Double,
        b: Double, h: Double,
        Ag: Double, As: Double,
        Pu: Double, Mx: Double, My: Double,
        Mnx: Double, Mny: Double,
        isSpiral: Boolean = false,
        clearCover: Double = CLEAR_COVER
    ): BiaxialCheckResult {
        val phiComp = if (isSpiral) PHI_SPIRAL else PHI_TIED

        // Pure axial capacity
        val Po = 0.85 * fcPrime * (Ag - As) + As * fy

        // Load level ratio
        val r = (Pu * 1000.0 / (phiComp * Po)).coerceIn(0.1, 0.95)

        // Exponent α per Hsu (1973): α = 1.15 + 0.84 × r
        // Higher α when axial load is high (more circular interaction surface)
        val alpha = 1.15 + 0.84 * r

        // Ratios
        val mxRatio = if (Mnx > 0.01) abs(Mx) / Mnx else 0.0
        val myRatio = if (Mny > 0.01) abs(My) / Mny else 0.0

        // Interaction factor: (Mux/Mnx)^α + (Muy/Mny)^α
        val interactionFactor = mxRatio.pow(alpha) + myRatio.pow(alpha)

        val isSafe = interactionFactor <= 1.0

        return BiaxialCheckResult(
            mxRatio = mxRatio,
            myRatio = myRatio,
            interactionFactor = interactionFactor,
            isSafe = isSafe,
            formula = "ACI 22.10.3 (Hsu): (Mx/Mnx)^%.2f + (My/Mny)^%.2f = %.3f ≤ 1.0".format(alpha, alpha, interactionFactor)
        )
    }

    /**
     * Interpolate Pn from the interaction diagram at a given moment value.
     * The diagram points are (φPn in kN, φMn in kN·m).
     * For a given M, finds the maximum Pn that can be sustained.
     */
    private fun interpolatePnFromDiagram(diagram: List<Pair<Double, Double>>, M: Double): Double {
        if (diagram.isEmpty()) return 0.0
        if (diagram.size == 1) return diagram[0].first * 1000.0

        val absM = abs(M)

        // The interaction diagram goes from high P, low M to low P, high M (compression side)
        // Find two points that bracket the target moment
        for (i in 0 until diagram.size - 1) {
            val (p1, m1) = diagram[i]
            val (p2, m2) = diagram[i + 1]

            if ((m1 <= absM && m2 >= absM) || (m1 >= absM && m2 <= absM)) {
                // Interpolate
                val dm = m2 - m1
                val dp = p2 - p1
                if (abs(dm) < 1e-6) return (p1 + p2) / 2.0 * 1000.0
                val t = (absM - m1) / dm
                return (p1 + t * dp) * 1000.0 // Convert kN to N
            }
        }

        // If moment is beyond the diagram range, use the last point
        return diagram.last().first * 1000.0
    }

    // ═══════════════════════════════════════════════════════════════════
    // SPIRAL COLUMN DESIGN  (ACI 318-25.7.6)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Design spiral reinforcement per ACI 25.7.6.
     *
     * Required volumetric ratio:
     *   ρs ≥ 0.45 × (Ag/Ac - 1) × (fc'/fyh)
     *
     * Pitch limits: 25mm ≤ s ≤ 75mm
     * Clear spacing between spirals ≥ 25mm (ACI 25.7.6.1)
     *
     * @return Triple(pitch_mm, spiralDiameter_mm, rhoS)
     */
    fun designSpiralReinforcement(
        fcPrime: Double, fy: Double,
        columnType: ColumnType,
        Ag: Double,
        clearCover: Double = CLEAR_COVER
    ): Triple<Double, Double, Double> {
        // Core dimensions
        val (coreWidth, coreDepth) = when (columnType) {
            is ColumnType.Rectangular -> {
                // For rectangular columns with spiral: use inscribed circle
                val Ds = min(columnType.width, columnType.depth) - 2.0 * clearCover
                Pair(Ds, Ds)
            }
            is ColumnType.Circular -> {
                val Ds = columnType.diameter - 2.0 * clearCover
                Pair(Ds, Ds)
            }
            else -> {
                // For other types, use equivalent circle
                val Ds = sqrt(4.0 * Ag / PI) - 2.0 * clearCover
                Pair(Ds, Ds)
            }
        }

        val Ds = coreWidth // spiral diameter (mm)
        val Ac = PI * Ds * Ds / 4.0 // Core area

        // Required volumetric spiral reinforcement ratio (ACI 25.7.6.1)
        val rhoSRequired = 0.45 * (Ag / Ac - 1.0) * (fcPrime / fy)

        // Try standard spiral diameters (8mm to 12mm typical)
        val spiralDiameters = listOf(8.0, 10.0, 12.0)
        var bestPitch = 75.0
        var bestDia = 10.0
        var bestRhoS = 0.0

        for (spiralDia in spiralDiameters) {
            val asSpiral = PI * spiralDia * spiralDia / 4.0

            // ρs = 4 × Aspiral / (Ds × s)  →  s = 4 × Aspiral / (Ds × ρs)
            val pitchForRequired = 4.0 * asSpiral / (Ds * rhoSRequired)

            // Check pitch limits
            val pitch = pitchForRequired.coerceIn(25.0, 75.0)

            // Check clear spacing: s - dspiral ≥ 25mm
            val clearSpacing = pitch - spiralDia
            if (clearSpacing >= 25.0 || pitch == 25.0) {
                // Calculate actual ρs with this pitch
                val rhoSActual = 4.0 * asSpiral / (Ds * pitch)
                if (rhoSActual >= rhoSRequired) {
                    bestPitch = pitch
                    bestDia = spiralDia
                    bestRhoS = rhoSActual
                    break // Accept first valid solution
                }
            }
        }

        // If no solution found with clear spacing requirement, relax and use minimum pitch
        if (bestRhoS < rhoSRequired) {
            val spiralDia = 12.0
            val asSpiral = PI * spiralDia * spiralDia / 4.0
            bestPitch = (4.0 * asSpiral / (Ds * rhoSRequired)).coerceIn(25.0, 75.0)
            bestDia = spiralDia
            bestRhoS = 4.0 * asSpiral / (Ds * bestPitch)
        }

        return Triple(bestPitch, bestDia, bestRhoS)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SHORT COLUMN DESIGN
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Design a short column without P-Delta effects.
     * Uses simplified interaction approach per ACI 318 Chapter 10.
     *
     * For Pu/(φAg) > 0.1×fc': compression controls, use axial formula.
     * For Pu/(φAg) ≤ 0.1×fc': use interaction diagram or approximate formula.
     */
    private fun designShortColumn(
        fcPrime: Double, fy: Double,
        b: Double, h: Double, Ag: Double,
        Pu: Double, Mx: Double, My: Double,
        isSpiral: Boolean,
        clearCover: Double,
        warnings: MutableList<String>,
        codeNotes: MutableList<String>
    ): ReinforcementResult {
        val phi = if (isSpiral) PHI_SPIRAL else PHI_TIED
        val Pu_N = Pu * 1000.0 // Convert to N

        // Check if compression controls
        val stressRatio = Pu_N / (phi * Ag)
        val compressionThreshold = 0.1 * fcPrime

        // Minimum and maximum reinforcement ratios per ACI 10.6.1.1
        val rhoMin = 0.01
        val rhoMax = 0.08
        val AsMin = rhoMin * Ag
        val AsMax = rhoMax * Ag

        // Estimate required steel area
        // ACI simplified: Pu = φ × [0.85×fc'×(Ag - Ast) + fy×Ast]
        // Solving for Ast: Ast = (Pu/φ - 0.85×fc'×Ag) / (fy - 0.85×fc')
        val numerator = Pu_N / phi - 0.85 * fcPrime * Ag
        val denominator = fy - 0.85 * fcPrime
        val AsRequired = if (abs(denominator) > 1.0) numerator / denominator else 0.0

        // If there is significant moment, increase reinforcement
        val Mu = sqrt(Mx * Mx + My * My) // Resultant moment (kN.m)
        val Mu_Nmm = Mu * 1e6 // N·mm

        // Approximate moment contribution to required steel
        val d = h - clearCover - TIE_BAR_EXTRA
        val leverArm = 0.8 * d // Approximate lever arm for column
        val AsMoment = if (leverArm > 0 && fy > 0) Mu_Nmm / (leverArm * fy) else 0.0

        // Total required steel: axial + moment contribution
        var astRequired = max(AsRequired, AsMoment)
        astRequired = astRequired.coerceIn(AsMin, AsMax)

        if (AsRequired < AsMin) {
            warnings.add("تم تطبيق نسبة التسليح الدنيا (1%) حسب ACI 10.6.1.1")
            codeNotes.add("ACI 10.6.1.1: ρmin = 1% applied (governs)")
        }

        // Compression load ratio check
        if (stressRatio > 0.1 * fcPrime) {
            codeNotes.add("ACI 10.6.2: Compression controls (Pu/φAg = %.1f > 0.1fc' = %.1f)".format(
                stressRatio, 0.1 * fcPrime
            ))
        }

        // Select bar diameter and number
        var selectedBarDia = 16.0
        var numberOfBars = 4
        var astProvided = 0.0

        // Find optimal bar combination
        for (dia in ACI_BAR_DIAMETERS) {
            val barArea = PI * dia * dia / 4.0
            val n = ceil(astRequired / barArea).toInt().coerceIn(4, 16)

            // Check if bars fit in the section (ACI 25.2)
            val minSpacing = max(dia, 25.0)
            val perimeter = 2.0 * (b + h) - 8.0 * clearCover // usable perimeter
            val maxBarsBySpacing = (perimeter / (dia + minSpacing)).toInt()

            if (n <= maxBarsBySpacing || n <= 12) {
                val asProv = n * barArea
                if (asProv >= astRequired && asProv <= AsMax) {
                    selectedBarDia = dia
                    numberOfBars = n
                    astProvided = asProv
                    break
                }
            }
        }

        if (astProvided < astRequired) {
            // Fallback: use the smallest bars that work
            for (dia in ACI_BAR_DIAMETERS) {
                val barArea = PI * dia * dia / 4.0
                val n = ceil(astRequired / barArea).toInt().coerceIn(4, 20)
                val asProv = n * barArea
                if (asProv >= astRequired && asProv <= AsMax) {
                    selectedBarDia = dia
                    numberOfBars = n
                    astProvided = asProv
                    break
                }
            }
        }

        astProvided = numberOfBars * PI * selectedBarDia * selectedBarDia / 4.0

        // Design ties or spirals
        val tiesDiameter = if (isSpiral) {
            // Spiral tie: typically smaller than longitudinal bars
            if (selectedBarDia <= 25.0) 10.0 else 12.0
        } else {
            // Tied column: ACI 25.7.2.2
            if (selectedBarDia <= 32.0) 10.0 else 12.0
        }

        val tiesSpacing = if (isSpiral) {
            // Spiral spacing is handled separately, put nominal value
            val spiralResult = designSpiralReinforcement(fcPrime, fy, ColumnType.Rectangular(b, h), Ag, clearCover)
            spiralResult.first
        } else {
            // ACI 25.7.2.1: min of 16db, 48dtie, min(b,h), 300mm
            minOf(
                16.0 * selectedBarDia,
                48.0 * tiesDiameter,
                min(b, h),
                300.0
            ).coerceIn(40.0, 300.0)
        }

        // Safety check: verify capacity
        val capacity = calculateAxialCapacityWithPhi(fcPrime, fy, b, h, astProvided, Ag, isSpiral)
        val utilizationRatio = if (capacity > 0) Pu / capacity else 2.0
        val isSafe = utilizationRatio <= 1.0

        // Check maximum spacing
        if (tiesSpacing > 150.0) {
            codeNotes.add("ACI 25.7.2.3: Tighter spacing may be needed near joints (so/2 ≤ %.0fmm)".format(tiesSpacing / 2))
        }

        val description = if (isSpiral) {
            "Spiral: %dØ%d + Spiral Ø%d @ %.0fmm".format(
                numberOfBars, selectedBarDia.toInt(), tiesDiameter.toInt(), tiesSpacing
            )
        } else {
            "%dØ%d, Ties Ø%d @ %.0fmm".format(
                numberOfBars, selectedBarDia.toInt(), tiesDiameter.toInt(), tiesSpacing
            )
        }

        return ReinforcementResult(
            astRequired = astRequired,
            astProvided = astProvided,
            barDiameter = selectedBarDia,
            numberOfBars = numberOfBars,
            tiesDiameter = tiesDiameter,
            tiesSpacing = tiesSpacing,
            isSafe = isSafe,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes,
            spacing = tiesSpacing,
            description = description
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUNCHING SHEAR  (ACI 318-22.6)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Check punching shear for flat slab columns per ACI 22.6.
     *
     * Interior: vc = 0.33 × λ × √fc'   (ACI 22.6.5.2)
     * Edge:     vc = 0.33 × λ × √fc' × (1 + 2/β)
     * Corner:   vc = 0.33 × λ × √fc' × (2 + αs×d/bo) / 12
     *
     * φ = 0.75 for shear (ACI 21.2.1)
     * Shear head design when vc is exceeded.
     *
     * @param slabDepth effective slab depth d (mm)
     */
    fun checkPunchingShear(
        columnType: ColumnType,
        Pu: Double,        // kN - column axial load (transfer from slab)
        fcPrime: Double,   // MPa
        slabDepth: Double, // mm - slab effective depth d
        clearCover: Double,
        hasCap: Boolean,
        warnings: MutableList<String>,
        codeNotes: MutableList<String>
    ): PunchingCheckResult {
        val d = slabDepth - clearCover - 8.0 // Effective depth of slab (mm)
        val Pu_N = Pu * 1000.0 // N

        // Critical section perimeter at d/2 from column face
        val (b, h) = getColumnDimensions(columnType)

        val bo = when (columnType) {
            is ColumnType.Rectangular -> {
                2.0 * (b + h) + 4.0 * d
            }
            is ColumnType.Circular -> {
                PI * (columnType.diameter + d)
            }
            else -> {
                // Approximate with equivalent square
                val side = sqrt(columnType.getGrossArea())
                4.0 * (side + d)
            }
        }

        // Applied shear stress
        val vu = Pu_N / (bo * d)

        // β = ratio of long side to short side of critical section (for rectangular columns)
        val beta = when (columnType) {
            is ColumnType.Rectangular -> max(b, h) / min(b, h)
            is ColumnType.Circular -> 1.0
            else -> 1.0
        }

        // αs factor: 30 for interior, 20 for edge, 15 for corner
        // Default to interior (most common and conservative for checking)
        val alphaS = 30.0

        // Calculate vc for all cases and take the minimum (most conservative)
        val vcInterior = 0.33 * LAMBDA * sqrt(fcPrime)
        val vcEdge = 0.33 * LAMBDA * sqrt(fcPrime) * (1.0 + 2.0 / beta)
        val vcCorner = 0.33 * LAMBDA * sqrt(fcPrime) * (2.0 + alphaS * d / bo) / 12.0

        // Use the appropriate vc based on column position
        // For interior columns: min of the three limits
        val vc = minOf(vcInterior, vcEdge, vcCorner)

        // Column cap factor (increases capacity)
        val capFactor = if (hasCap) 1.25 else 1.0

        // Design capacity: φ × vc × bo × d
        val phiVc = PHI_SHEAR * vc * capFactor * bo * d

        val isSafe = Pu_N <= phiVc

        if (!isSafe) {
            warnings.add("❌ فشل قص الاختراق! vu=%.2f > φvc=%.2f MPa".format(vu, PHI_SHEAR * vc * capFactor))
            warnings.add("يجب زيادة سمك البلاطة أو إضافة رأس عمود أو حديد قص إضافي (shear studs)")
            codeNotes.add("ACI 22.6.5: Shear head or shear studs required (vc exceeded)")
            codeNotes.add("ACI 22.6.8: Consider shear reinforcement (studs or stirrups)")
        }

        codeNotes.add("ACI 22.6: Critical perimeter bo=%.0fmm at d/2=%.0fmm".format(bo, d / 2))
        codeNotes.add("ACI 22.6.5.2: vc=%.2f MPa (interior), φ=%.2f".format(vc, PHI_SHEAR))

        return PunchingCheckResult(
            appliedShear = Pu,
            capacity = phiVc / 1000.0, // kN
            isSafe = isSafe,
            hasCap = hasCap,
            criticalPerimeter = bo
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // REINFORCEMENT DETAILS  (ACI 25.7.2, 25.2)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Design ties per ACI 25.7.2.
     * Returns Pair(tiesDiameter, tiesSpacing).
     *
     * Tie diameter: ACI 25.7.2.2 - No.3 (10mm) for bars ≤ No.10 (32mm), No.4 (12mm) otherwise
     * Tie spacing: ACI 25.7.2.1 - min(16db, 48dtie, min column dimension, 300mm)
     */
    fun designTies(barDiameter: Double, numBars: Int, b: Double, h: Double): Pair<Double, Double> {
        // Tie diameter per ACI 25.7.2.2
        val tiesDiameter = if (barDiameter <= 32.0) 10.0 else 12.0

        // Tie spacing per ACI 25.7.2.1
        val tiesSpacing = minOf(
            16.0 * barDiameter,     // 16 × longitudinal bar diameter
            48.0 * tiesDiameter,    // 48 × tie bar diameter
            min(b, h),              // least column dimension
            300.0                   // 300 mm maximum
        ).coerceIn(40.0, 300.0)

        // ACI 25.7.2.3: Every corner and alternate bar must be laterally supported
        // Max clear spacing between laterally supported bars: 150mm
        // Additional ties may be needed at splice regions (so/2)

        return Pair(tiesDiameter, tiesSpacing)
    }

    /**
     * Calculate bar positions for rectangular columns.
     * Distributes bars around the perimeter with bars at corners and along faces.
     *
     * @return List of (y, x) positions from centroid in mm
     */
    private fun getRectangularBarPositions(
        b: Double, h: Double,
        numBars: Int,
        cover: Double, tieBar: Double
    ): List<Pair<Double, Double>> {
        val positions = mutableListOf<Pair<Double, Double>>()
        val xMax = b / 2.0 - cover - tieBar
        val yMax = h / 2.0 - cover - tieBar

        when {
            numBars == 4 -> {
                // 4 corner bars
                positions.add(Pair(yMax, xMax))
                positions.add(Pair(yMax, -xMax))
                positions.add(Pair(-yMax, xMax))
                positions.add(Pair(-yMax, -xMax))
            }
            numBars <= 8 -> {
                // 4 corners + distribute remaining along the longer face
                val sideBars = numBars - 4
                val barsPerLongFace = if (h >= b) ceil(sideBars / 2.0).toInt() else 0
                val barsPerShortFace = sideBars - barsPerLongFace

                // Corners
                positions.add(Pair(yMax, xMax))
                positions.add(Pair(yMax, -xMax))
                positions.add(Pair(-yMax, xMax))
                positions.add(Pair(-yMax, -xMax))

                // Long face bars (top and bottom)
                if (h >= b) {
                    for (i in 1..barsPerLongFace) {
                        val y = yMax - 2.0 * yMax * i / (barsPerLongFace + 1)
                        positions.add(Pair(y, 0.0))
                        positions.add(Pair(-y, 0.0))
                    }
                }

                // Short face bars (left and right)
                for (i in 1..barsPerShortFace) {
                    val x = xMax - 2.0 * xMax * i / (barsPerShortFace + 1)
                    positions.add(Pair(0.0, x))
                    positions.add(Pair(0.0, -x))
                }
            }
            else -> {
                // More than 8 bars: distribute evenly around perimeter
                // Assign bars proportionally to each face
                val perimeter = 2.0 * (b + h)
                val usablePerimeter = 2.0 * (2.0 * xMax + 2.0 * yMax)

                // Distribute: corners first, then sides proportionally
                positions.add(Pair(yMax, xMax))
                positions.add(Pair(yMax, -xMax))
                positions.add(Pair(-yMax, xMax))
                positions.add(Pair(-yMax, -xMax))

                val remaining = numBars - 4
                val longFaceLength = 2.0 * yMax
                val shortFaceLength = 2.0 * xMax
                val totalFaceLength = 2.0 * longFaceLength + 2.0 * shortFaceLength

                // Distribute remaining bars proportionally to face lengths
                var barsAllocated = 0
                val longFaceBars = if (h >= b) {
                    round(remaining * 2.0 * longFaceLength / totalFaceLength).toInt().coerceAtLeast(2)
                } else {
                    round(remaining * 2.0 * shortFaceLength / totalFaceLength).toInt().coerceAtLeast(2)
                }
                barsAllocated += longFaceBars
                val shortFaceBars = remaining - barsAllocated

                // Long face bars (y direction, x = 0)
                val barsPerSide = longFaceBars / 2
                for (i in 1..barsPerSide) {
                    val y = yMax - 2.0 * yMax * i / (barsPerSide + 1)
                    positions.add(Pair(y, 0.0))
                    if (positions.size < numBars) positions.add(Pair(-y, 0.0))
                }

                // Short face bars (x direction, y = 0)
                val barsPerSideShort = max(0, (numBars - positions.size) / 2)
                for (i in 1..barsPerSideShort) {
                    if (positions.size >= numBars) break
                    val x = xMax - 2.0 * xMax * i / (barsPerSideShort + 1)
                    positions.add(Pair(0.0, x))
                    if (positions.size < numBars) positions.add(Pair(0.0, -x))
                }

                // Fill remaining to match numBars
                var fillCount = 0
                while (positions.size < numBars && fillCount < numBars) {
                    fillCount++
                    // Add at varying positions along the faces
                    val idx = (positions.size - 4).coerceAtLeast(0)
                    val frac = (idx + 1).toDouble() / (numBars - 3).coerceAtLeast(1)
                    val y = yMax - 2.0 * yMax * frac
                    val xPos = if (positions.size % 2 == 0) xMax else -xMax
                    positions.add(Pair(y, xPos))
                }
            }
        }

        return positions.take(numBars)
    }

    /**
     * Calculate bar positions for circular columns.
     * Bars equally spaced around a circle of diameter (D - 2×cover - 2×tie).
     *
     * @return List of (y, x) positions from center in mm
     */
    private fun getCircularBarPositions(
        diameter: Double,
        numBars: Int,
        cover: Double,
        tieBar: Double
    ): List<Pair<Double, Double>> {
        val positions = mutableListOf<Pair<Double, Double>>()
        val rBar = diameter / 2.0 - cover - tieBar // radius of bar circle

        for (i in 0 until numBars) {
            val angle = 2.0 * PI * i / numBars
            val x = rBar * cos(angle)
            val y = rBar * sin(angle)
            positions.add(Pair(y, x))
        }

        return positions
    }

    // ═══════════════════════════════════════════════════════════════════
    // CAPACITY CALCULATIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculate axial capacity with phi factor.
     * ACI 22.10.2.1: φPn = φ × [0.85×fc'×(Ag - Ast) + fy×Ast]
     */
    fun calculateAxialCapacityWithPhi(
        fcPrime: Double, fy: Double,
        b: Double, h: Double,
        As: Double, Ag: Double,
        isSpiral: Boolean
    ): Double {
        val AsCapped = As.coerceAtMost(Ag * 0.08)
        val concreteCapacity = 0.85 * fcPrime * (Ag - AsCapped)
        val steelCapacity = fy * AsCapped
        val nominalCapacity = concreteCapacity + steelCapacity
        val phi = if (isSpiral) PHI_SPIRAL else PHI_TIED
        return phi * nominalCapacity / 1000.0 // kN
    }

    /**
     * Calculate approximate moment capacity of the column section.
     * Uses the interaction diagram approach at zero axial load (pure flexure).
     */
    private fun calculateMomentCapacity(
        fcPrime: Double, fy: Double,
        b: Double, h: Double,
        As: Double, d: Double,
        Ag: Double, isSpiral: Boolean,
        clearCover: Double
    ): Double {
        // Generate interaction diagram and find max moment point
        val diagram = generateInteractionDiagram(
            ColumnType.Rectangular(b, h), fcPrime / 0.8, fy, As, isSpiral, clearCover
        )

        // Find maximum moment on the compression side
        var maxMoment = 0.0
        for ((p, m) in diagram) {
            if (m > maxMoment && p >= 0) {
                maxMoment = m
            }
        }

        // Also check tension side
        for ((p, m) in diagram) {
            if (m > maxMoment) {
                maxMoment = m
            }
        }

        return maxMoment
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPER FUNCTIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get β1 factor per ACI 22.2.2.4.1.
     * β1 = 0.85 for fc' ≤ 28 MPa
     * β1 decreases by 0.05 for each 7 MPa above 28 MPa
     * Minimum β1 = 0.65
     */
    fun getBeta1(fcPrime: Double): Double {
        return if (fcPrime <= 28.0) {
            BETA_1_DEFAULT
        } else {
            val reduction = 0.05 * floor((fcPrime - 28.0) / 7.0)
            (BETA_1_DEFAULT - reduction).coerceAtLeast(0.65)
        }
    }

    /**
     * Get phi (φ) reduction factor based on net tensile strain.
     * ACI 21.2.2:
     *   εt ≥ 0.005 → φ = 0.9 (tension-controlled)
     *   εt ≤ 0.002 → φ = 0.65 (tied) or 0.75 (spiral) (compression-controlled)
     *   0.002 < εt < 0.005 → linear interpolation
     */
    fun getPhiFactor(epsilonT: Double, isSpiral: Boolean): Double {
        val phiComp = if (isSpiral) PHI_SPIRAL else PHI_TIED

        return when {
            epsilonT >= EPSILON_T_MIN -> PHI_FLEXURE
            epsilonT <= EPSILON_TY -> phiComp
            else -> {
                // Linear interpolation between compression and tension-controlled
                phiComp + (PHI_FLEXURE - phiComp) * (epsilonT - EPSILON_TY) / (EPSILON_T_MIN - EPSILON_TY)
            }
        }
    }

    /**
     * Extract column dimensions (b, h) from ColumnType.
     * Returns width and depth in mm.
     */
    private fun getColumnDimensions(columnType: ColumnType): Pair<Double, Double> {
        return when (columnType) {
            is ColumnType.Rectangular -> Pair(columnType.width, columnType.depth)
            is ColumnType.Circular -> {
                // Equivalent square: b = 0.8D for interaction calculations
                Pair(0.8 * columnType.diameter, columnType.diameter)
            }
            is ColumnType.LShaped -> {
                // Equivalent rectangular: gross area based
                val Ag = columnType.getGrossArea()
                val eqSide = sqrt(Ag)
                Pair(eqSide, eqSide)
            }
            is ColumnType.TShaped -> {
                val Ag = columnType.getGrossArea()
                val eqSide = sqrt(Ag)
                Pair(eqSide, eqSide)
            }
            is ColumnType.Composite -> {
                Pair(columnType.concreteWidth, columnType.concreteDepth)
            }
            is ColumnType.Tubular -> {
                Pair(columnType.outerDiameter, columnType.outerDiameter)
            }
        }
    }

    /**
     * Get radius of gyration r for the column section.
     * ACI 22.4.1.2: r = 0.3 × min dimension (rectangular), 0.25 × D (circular)
     */
    private fun getRadiusOfGyration(columnType: ColumnType): Double {
        return when (columnType) {
            is ColumnType.Rectangular -> {
                0.3 * min(columnType.width, columnType.depth)
            }
            is ColumnType.Circular -> {
                0.25 * columnType.diameter
            }
            is ColumnType.LShaped -> {
                // Use smallest leg dimension
                0.3 * min(columnType.legWidth, columnType.legDepth, columnType.thickness)
            }
            is ColumnType.TShaped -> {
                0.3 * min(columnType.webWidth, columnType.flangeThickness)
            }
            is ColumnType.Composite -> {
                0.3 * min(columnType.concreteWidth, columnType.concreteDepth)
            }
            is ColumnType.Tubular -> {
                0.25 * columnType.outerDiameter
            }
        }
    }

    /**
     * Get gross moment of inertia Ig for the column section (mm⁴).
     */
    private fun getGrossMomentOfInertia(columnType: ColumnType): Double {
        return when (columnType) {
            is ColumnType.Rectangular -> {
                columnType.width * columnType.depth.pow(3) / 12.0
            }
            is ColumnType.Circular -> {
                PI * columnType.diameter.pow(4) / 64.0
            }
            is ColumnType.LShaped -> {
                // Approximate with equivalent square
                val side = sqrt(columnType.getGrossArea())
                side.pow(4) / 12.0
            }
            is ColumnType.TShaped -> {
                val side = sqrt(columnType.getGrossArea())
                side.pow(4) / 12.0
            }
            is ColumnType.Composite -> {
                columnType.concreteWidth * columnType.concreteDepth.pow(3) / 12.0
            }
            is ColumnType.Tubular -> {
                PI * (columnType.outerDiameter.pow(4) - columnType.innerDiameter.pow(4)) / 64.0
            }
        }
    }

    /**
     * Estimate steel moment of inertia Is (mm⁴).
     * Assumes 1% reinforcement ratio, bars distributed at 0.8 × h/2 from centroid.
     */
    private fun calculateSteelMomentOfInertia(columnType: ColumnType, Ag: Double, clearCover: Double): Double {
        val rho = 0.01 // assumed reinforcement ratio
        val As = rho * Ag
        val (b, h) = getColumnDimensions(columnType)

        // Approximate: assume bars at distance 0.4h from centroid on each side
        val dBar = 0.4 * h
        // Is ≈ 2 × (As/2) × dBar² (two groups of bars)
        return As * dBar * dBar
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERFACE IMPLEMENTATIONS (delegate to base ACIColumn)
    // ═══════════════════════════════════════════════════════════════════

    override fun calculateAxialCapacity(
        fcu: Double, fy: Double,
        width: Double, depth: Double,
        reinforcementArea: Double,
        loadCombination: LoadCombination
    ): Double {
        return baseDesign.calculateAxialCapacity(fcu, fy, width, depth, reinforcementArea, loadCombination)
    }

    override fun calculateReinforcement(
        fcu: Double, fy: Double,
        width: Double, depth: Double,
        axialLoad: Double, momentX: Double, momentY: Double,
        loadCombination: LoadCombination
    ): ReinforcementResult {
        return baseDesign.calculateReinforcement(fcu, fy, width, depth, axialLoad, momentX, momentY, loadCombination)
    }

    override fun getMinReinforcementRatio(): Double = 0.01   // ACI 10.6.1.1: 1%
    override fun getMaxReinforcementRatio(): Double = 0.08   // ACI 10.6.1.1: 8%
    override fun getMinSpacing(): Double = 40.0               // ACI 25.2
    override fun getMaxSpacing(): Double = 300.0              // ACI 25.7.2.1
    override fun getMinCover(): Double = CLEAR_COVER           // ACI 20.6.1
}