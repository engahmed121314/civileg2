package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.app.domain.entities.ColumnShearDesignResult
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult
import com.civileg.app.domain.entities.SlendernessCheckResult
import kotlin.math.*

class ECPColumn : ColumnDesign {
    
    companion object {
        private const val ALPHA = 0.8          // عامل اختزال الخرسانة
        private const val GAMMA_C = 1.5        // معامل أمان الخرسانة (ECP 203 §2-3-1)
        private const val GAMMA_S = 1.15       // معامل أمان الحديد (ECP 203 §2-3-1)
        // ECP 203 لا يستخدم معامل φ منفصل للقص - γc و γs كافيان
        // (تم حذف PHI_SHEAR = 0.75 الذي كان ACI-style ويسبب double-counting للأمان)
    }

    override fun calculateAxialCapacity(
        fcu: Double,
        fy: Double,
        width: Double,
        depth: Double,
        reinforcementArea: Double,
        loadCombination: LoadCombination
    ): Double {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("width", width)
        InputGuard.positive("depth", depth)

        val Ag = width * depth                          // مساحة المقطع الكلية (mm²)
        val Ast = reinforcementArea.coerceAtMost(Ag * 0.08) // ECP 203 §4-2-3: حد أقصى 8%
        
        // مقاومة الخرسانة: 0.67 * fcu / γc
        val concreteStress = 0.67 * fcu / GAMMA_C
        // مقاومة الحديد: fy / γs
        val steelStress = fy / GAMMA_S
        
        // القدرة المحورية: Pu = α × [0.67×fcu/γc × (Ag-Ast) + fy/γs × Ast]
        // ECP 203-2020 §4-2-3: α = 0.8 for tied columns
        val concreteCapacity = concreteStress * (Ag - Ast)
        val steelCapacity = steelStress * Ast
        val designCapacity = ALPHA * (concreteCapacity + steelCapacity)
        InputGuard.finite("axialCapacity", designCapacity / 1000.0)
        
        // التحويل من نيوتن إلى كيلو نيوتن
        return designCapacity / 1000.0
    }

    override fun calculateReinforcement(
        fcu: Double,
        fy: Double,
        width: Double,
        depth: Double,
        axialLoad: Double,
        momentX: Double,
        momentY: Double,
        loadCombination: LoadCombination
    ): ReinforcementResult {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("width", width)
        InputGuard.positive("depth", depth)
        InputGuard.nonNegative("axialLoad", axialLoad)

        val Ag = width * depth
        // Pu: الحمل المحوري التصميمي (N) - نستخدمه مباشرة بدون قسمة
        val Pu = axialLoad * 1000.0  // N
        val Mu = sqrt(momentX.pow(2) + momentY.pow(2)) * 1e6 // N.mm
        InputGuard.finite("Mu", Mu)
        
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // حساب العزم اللامركزي
        val eccentricity = if (Pu > 0) Mu / Pu else 0.0
        // ECP 203-2020 §4-2-3: e_min = max(20mm, b/20, h/20)
        val minEccentricity = maxOf(20.0, width / 20.0, depth / 20.0)
        
        // ECP 203-2020 §4-2-3: Enforce minimum eccentricity for safety
        // If e < e_min, design must use e_min (account for accidental eccentricity)
        if (eccentricity < minEccentricity && eccentricity > 0) {
            codeNotes.add("ECP 203 §4-2-3: Actual e=${"%.1f".format(eccentricity)}mm < e_min=${"%.1f".format(minEccentricity)}mm → using e_min")
        }
        
        // طريقة مبسطة لحساب التسليح (لأعمدة قصيرة)
        // ECP 203-2020 §4-2-3: Pu = α × [0.67×fcu/γc×(Ag-Ast) + fy/γs×Ast]
        // بدون معامل φ إضافي (ECP يستخدم γ فقط)
        val concreteStress = 0.67 * fcu / GAMMA_C
        val steelStress = fy / GAMMA_S
        
        // نحل المعادلة لإيجاد Ast المطلوبة
        // Pu = α(concreteStress × (Ag-Ast) + steelStress × Ast)
        // Pu/α = concreteStress×Ag - concreteStress×Ast + steelStress×Ast
        // Pu/α - concreteStress×Ag = Ast×(steelStress - concreteStress)
        val numerator = Pu / ALPHA - concreteStress * Ag
        val denominator = steelStress - concreteStress
        var requiredSteelArea = if (abs(denominator) > 0.1) numerator / denominator else 0.0
        InputGuard.finite("requiredSteelArea", requiredSteelArea)
        // Negative steel area means section is oversized — apply minimum only
        if (requiredSteelArea < 0) {
            requiredSteelArea = 0.0
            codeNotes.add("Section capacity exceeds demand — minimum reinforcement applied")
        }
        
        // ── لحظة: تحقق من العزوم الكبيرة وزيادة التسليح حسب اللامركزية ──
        val h = max(width, depth)
        if (eccentricity > 0.05 * h) {
            // Simplified interaction approach: if e > 0.05h, increase As by factor
            val momentFactor = max(1.0, 1.0 + 2.0 * eccentricity / h)
            requiredSteelArea *= momentFactor
            codeNotes.add("ECP 203: Significant moment (e=${"%.1f".format(eccentricity)}mm > 0.05h), As increased by factor ${"%.2f".format(momentFactor)}")
        }
        
        // تطبيق حدود التسليح
        val minSteel = getMinReinforcementRatio() * Ag
        val maxSteel = getMaxReinforcementRatio() * Ag
        
        if (requiredSteelArea < minSteel) {
            requiredSteelArea = minSteel
            warnings.add("Minimum reinforcement applied")
        }
        
        if (requiredSteelArea > maxSteel) {
            warnings.add("WARNING: Reinforcement exceeds maximum limit! Consider increasing section size.")
        }
        
        // اختيار قطر حديد مناسب (12, 16, 20, 22, 25 مم)
        val availableBars = listOf(12.0, 16.0, 20.0, 22.0, 25.0)
        val barDiameter = availableBars.firstOrNull { 
            val area = PI * it * it / 4
            ceil(requiredSteelArea / area) <= 12 // أقصى 12 سيخ في الوجه
        } ?: 16.0
        
        val barArea = PI * barDiameter * barDiameter / 4
        val numberOfBars = ceil(requiredSteelArea / barArea).toInt().coerceIn(4, 32)
        val astProvided = numberOfBars * barArea
        
        // حساب الكانات
        val tiesDiameter = max(10.0, barDiameter / 4).coerceAtLeast(8.0)
        val tiesSpacing = calculateTiesSpacing(barDiameter, tiesDiameter, width, depth)
        
        // حساب نسبة الاستغلال
        val capacity = calculateAxialCapacity(fcu, fy, width, depth, astProvided, loadCombination)
        val utilizationRatio = if (capacity > 0) axialLoad / capacity else 2.0
        InputGuard.finite("utilizationRatio", utilizationRatio)
        
        // ملاحظات الكود
        codeNotes.add("ECP 203-2020: Section 4-2-3 (Column Design)")
        codeNotes.add("Cover: ${getMinCover()}mm minimum")
        if (eccentricity > minEccentricity) {
            codeNotes.add("Eccentricity check: e=${"%.1f".format(eccentricity)}mm > e_min=${"%.1f".format(minEccentricity)}mm")
        }
        
        return ReinforcementResult(
            astRequired = requiredSteelArea,
            astProvided = astProvided,
            barDiameter = barDiameter,
            numberOfBars = numberOfBars,
            tiesDiameter = tiesDiameter,
            tiesSpacing = tiesSpacing,
            isSafe = utilizationRatio <= 1.0 && requiredSteelArea <= maxSteel,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    private fun calculateTiesSpacing(barDiameter: Double, tiesDiameter: Double, width: Double, depth: Double): Double {
        // حسب الكود المصري ECP 203 البند 4-2-6: أقل من (16×قطر السيخ، 48×قطر الكانة، أقل بعد في المقطع، 300 مم)
        return minOf(16 * barDiameter, 48 * tiesDiameter, width, depth, 300.0).coerceIn(getMinSpacing(), getMaxSpacing())
    }

    /**
     * Slenderness ratio check per ECP 203-2020 §4-2-3
     * λ = K × L / r, where r = sqrt(I/Ag) ≈ 0.3h for rectangular columns
     * Short column: λ ≤ 15 (braced) or λ ≤ 10 (unbraced)
     * Long column: λ > threshold → moment magnification required
     *
     * @param effectiveLength effective column length K×L (mm), where K = effective length factor
     * @param width column width b (mm)
     * @param depth column depth h (mm)
     * @param isBraced whether column is braced against sidesway
     * @return SlendernessCheckResult with ratio and classification
     */
    fun checkSlenderness(
        effectiveLength: Double,
        width: Double,
        depth: Double,
        isBraced: Boolean = true
    ): SlendernessCheckResult {
        InputGuard.positive("effectiveLength", effectiveLength)
        InputGuard.positive("width", width)
        InputGuard.positive("depth", depth)

        // ECP 203-2020 §4-2-3: Radius of gyration for rectangular section
        // r = h / √12 ≈ 0.289h (for buckling about depth axis)
        val r = depth / sqrt(12.0)
        val slendernessRatio = effectiveLength / r.coerceAtLeast(1.0)
        InputGuard.finite("slendernessRatio", slendernessRatio)

        // ECP 203-2020 §4-2-3: Threshold slenderness
        val threshold = if (isBraced) 15.0 else 10.0
        val isShort = slendernessRatio <= threshold

        return SlendernessCheckResult(
            slendernessRatio = slendernessRatio,
            threshold = threshold,
            isShortColumn = isShort,
            isBraced = isBraced,
            recommendation = if (isShort) {
                "Short column (λ=${"%.1f".format(slendernessRatio)} ≤ ${"%.0f".format(threshold)}) — no moment magnification needed"
            } else {
                "Long column (λ=${"%.1f".format(slendernessRatio)} > ${"%.0f".format(threshold)}) — moment magnification required per ECP 203 §4-2-3"
            }
        )
    }

    /**
     * Moment magnification factor for slender columns per ECP 203-2020 §4-2-3
     * δ = 1 / (1 - Pu/Pcr) where Pcr = π²EI/(KL)²
     *
     * @param Pu factored axial load (kN)
     * @param effectiveLength K×L in mm
     * @param fcu concrete cube strength (MPa)
     * @param width column width (mm)
     * @param depth column depth (mm)
     * @param reinforcementArea total steel area (mm²)
     * @return magnification factor δ (≥ 1.0)
     */
    fun calculateMomentMagnification(
        Pu: Double,
        effectiveLength: Double,
        fcu: Double,
        width: Double,
        depth: Double,
        reinforcementArea: Double = 0.0
    ): Double {
        InputGuard.positive("effectiveLength", effectiveLength)
        InputGuard.positive("fcu", fcu)

        // ECP 203-2020 §4-2-3: EI ≈ 0.4 × Ec × Ig + Es × Is
        // Simplified: EI ≈ 0.4 × (4400√fcu) × (b×h³/12) + Es × [Ast/2 × (d-d')²/2]
        val Ec = 4400.0 * sqrt(fcu)  // MPa per ECP 203
        val Ig = width * depth.pow(3) / 12.0  // mm⁴
        val Es = 200000.0  // MPa
        // Approximate Is for symmetric reinforcement
        val dPrime = 40.0 + 10.0  // cover + half bar ~ 50mm
        val d = depth - dPrime
        val AsHalf = reinforcementArea / 2.0  // steel in one face
        val Is = 2.0 * AsHalf * ((d - dPrime) / 2.0).pow(2)  // mm⁴

        val EI = 0.4 * Ec * Ig + Es * Is  // N·mm²
        val Lmm = effectiveLength  // mm
        val Pcr = (PI.pow(2) * EI) / (Lmm * Lmm) / 1000.0  // kN

        if (Pcr <= 0) return 2.0  // Safety fallback

        val ratio = Pu / Pcr
        // ECP 203 §4-2-3: If Pu/Pcr ≥ 1.0, column is unstable
        val delta = if (ratio >= 1.0) {
            2.0  // Cap at 2.0 — flag as critical
        } else {
            1.0 / (1.0 - ratio)
        }
        return delta.coerceIn(1.0, 2.5)  // Cap at 2.5 for safety
    }

    /**
     * Generate interaction diagram points per ECP 203-2020 §4-2-3
     * Returns list of (Mu, Pu) points for the interaction curve
     *
     * @param fcu concrete cube strength (MPa)
     * @param fy steel yield strength (MPa)
     * @param width column width b (mm)
     * @param depth column depth h (mm)
     * @param Ast total reinforcement area (mm²)
     * @param dPrime distance from face to reinforcement centroid (mm)
     * @param numPoints number of points to generate (default 20)
     * @return List of pairs (Mu_kNm, Pu_kN)
     */
    fun generateInteractionDiagram(
        fcu: Double,
        fy: Double,
        width: Double,
        depth: Double,
        Ast: Double,
        dPrime: Double = 50.0,
        numPoints: Int = 20
    ): List<Pair<Double, Double>> {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("width", width)
        InputGuard.positive("depth", depth)

        val d = depth - dPrime
        val AsHalf = Ast / 2.0  // Assume symmetric: half in each face
        val fs = fy / GAMMA_S
        val fc = 0.67 * fcu / GAMMA_C
        val points = mutableListOf<Pair<Double, Double>>()

        // ECP 203-2020 §4-2-3: Vary neutral axis depth from 0 to h
        for (i in 0..numPoints) {
            val c = (i.toDouble() / numPoints) * depth * 1.2  // Go slightly beyond h for pure tension
            val a = min(0.8 * c, depth)  // Whitney stress block, β₁=0.8 for ECP

            // Concrete contribution
            val Cc = if (a > 0) fc * width * a else 0.0  // N
            val Mc = Cc * (d - a / 2.0) / 1e6  // kN·m

            // Steel strains and stresses
            val epsilonCu = 0.003
            val epsilonS1 = if (c > 0) epsilonCu * (d - c) / c else 0.0  // Tension steel
            val epsilonS2 = if (c > 0) epsilonCu * (c - dPrime) / c else 0.0  // Compression steel

            // ECP 203: Steel stress = min(ε × Es, fy/γs) for tension, max(-fy/γs, ε×Es) for compression
            val fs1 = if (epsilonS1 >= 0) min(epsilonS1 * 200000.0, fs) else max(epsilonS1 * 200000.0, -fs)
            val fs2 = if (epsilonS2 >= 0) min(epsilonS2 * 200000.0, fs) else max(epsilonS2 * 200000.0, -fs)

            val Fs1 = AsHalf * fs1  // N (tension positive)
            val Fs2 = AsHalf * fs2  // N (compression positive when ε>0)

            // Pu = Cc + Fs2 - Fs1 (compression positive)
            val Pu = (Cc + Fs2 - Fs1) / 1000.0  // kN
            // Mu about plastic centroid
            val Mu = (Cc * (depth / 2.0 - a / 2.0) + Fs2 * (depth / 2.0 - dPrime) - Fs1 * (d - depth / 2.0)) / 1e6  // kN·m

            if (Pu.isFinite() && Mu.isFinite()) {
                points.add(Pair(abs(Mu), max(Pu, 0.0)))  // Only compression side
            }
        }
        return points
    }

    override fun getMinReinforcementRatio(): Double = 0.008  // 0.8%
    override fun getMaxReinforcementRatio(): Double = 0.08   // 8% per ECP 203-2020 Section 4-2-3 (4% typical, 6% at splices, 8% max)
    override fun getMinSpacing(): Double = 100.0
    override fun getMaxSpacing(): Double = 300.0
    override fun getMinCover(): Double = 40.0

    // ── Shear Design per ECP 203 §4-2-5 ────────────────────────────────────────

    /**
     * تصميم كانات القص للأعمدة — ECP 203 البند 4-2-5
     * @param Vu   factored shear force (kN)
     * @param width   column width b (mm)
     * @param depth   column depth h (mm)
     * @param fcu      concrete cube strength (MPa)
     * @param fy       steel yield strength (MPa)
     * @param cover    concrete cover (mm), default 40
     * @return ColumnShearDesignResult
     */
    fun calculateShearDesign(
        Vu: Double,
        width: Double,
        depth: Double,
        fcu: Double,
        fy: Double,
        cover: Double = 40.0
    ): ColumnShearDesignResult {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("width", width)
        InputGuard.positive("depth", depth)
        InputGuard.positive("cover", cover)

        val b = width
        val d = depth - cover  // effective depth (mm)
        val codeNotes = mutableListOf<String>()

        // Vc = 0.24 × √(fcu/γc) × b × d   (ECP 203 §4-3-1-2)
        val Vc = 0.24 * sqrt(fcu / GAMMA_C) * b * d / 1000.0  // kN

        val needsStirrups = Vu > Vc

        // Asv/s = (Vu - Vc) / ((fy/γs) × d)  — بدون φ إضافي
        val fyDesign = fy / GAMMA_S  // MPa
        val requiredAsvPerS = if (needsStirrups) {
            (Vu - Vc) * 1000.0 / (fyDesign * d)  // mm²/mm
        } else 0.0

        // Maximum spacing = min(15×db_tie, b, 300mm)
        val dbTie = 10.0  // assume 10mm tie as starting point
        val maxSpacing = minOf(15.0 * dbTie, b, 300.0)

        // Minimum Asv/s = 0.0025 × b × s  → Asv/s_min = 0.0025 × b (per mm)
        val minAsvPerS = 0.0025 * b

        val designAsvPerS = max(requiredAsvPerS, if (needsStirrups) minAsvPerS else 0.0)

        // Select stirrup diameter and spacing
        val availableTies = listOf(8.0, 10.0, 12.0, 16.0)
        var selectedDia = 8.0
        var selectedSpacing = maxSpacing

        if (designAsvPerS > 0) {
            for (dia in availableTies) {
                val asv = 2.0 * PI * dia * dia / 4.0  // 2 legs
                val spacing = asv / designAsvPerS  // mm
                if (spacing <= maxSpacing && spacing >= getMinSpacing()) {
                    selectedDia = dia
                    selectedSpacing = min(spacing, maxSpacing)
                    break
                }
                // If spacing < minSpacing, try larger dia
                if (spacing < getMinSpacing()) {
                    selectedDia = dia
                    selectedSpacing = getMinSpacing()
                }
            }
        }

        val providedAsvPerS = if (designAsvPerS > 0) {
            2.0 * PI * selectedDia * selectedDia / 4.0 / selectedSpacing
        } else 0.0

        val totalCapacity = Vc + if (needsStirrups) fyDesign * providedAsvPerS * d / 1000.0 else 0.0
        val utilizationRatio = if (totalCapacity > 0) Vu / totalCapacity else 2.0

        codeNotes.add("ECP 203 §4-2-5: Column Shear Design")
        codeNotes.add("Vc = 0.24√fcu·b·d / γc = ${"%.1f".format(Vc)} kN  (design capacity, γc=${GAMMA_C})")
        if (needsStirrups) {
            codeNotes.add("Vu (${"%.1f".format(Vu)} kN) > Vc → Stirrups required")
            codeNotes.add("Asv/s = ${"%.3f".format(designAsvPerS)} mm²/mm")
            codeNotes.add("${selectedDia.toInt()}mm ties @ ${selectedSpacing.toInt()}mm c/c")
        } else {
            codeNotes.add("Vu (${"%.1f".format(Vu)} kN) ≤ Vc → Concrete alone sufficient")
        }

        return ColumnShearDesignResult(
            Vu = Vu,
            Vc = Vc,
            phiVc = Vc,  // ECP 203: no φ factor — phiVc equals Vc (kept for data class compat)
            asvPerS = requiredAsvPerS,
            minAsvPerS = minAsvPerS,
            designAsvPerS = designAsvPerS,
            stirrupDiameter = selectedDia,
            stirrupSpacing = selectedSpacing,
            providedAsvPerS = providedAsvPerS,
            maxSpacing = maxSpacing,
            needsStirrups = needsStirrups,
            isSafe = Vu <= totalCapacity,
            utilizationRatio = utilizationRatio,
            codeNotes = codeNotes
        )
    }
}
