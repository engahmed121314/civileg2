package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.app.domain.entities.CodeReference
import com.civileg.app.domain.entities.ColumnShearDesignResult
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult
import kotlin.math.*

class ACIColumn : ColumnDesign {
    
    companion object {
        private const val BETA_1 = 0.85  // Concrete stress block factor
        private const val PHI_TIED = 0.65     // معامل الاختزال للأعمدة المربوطة (ACI 318-21.2.1)
        private const val PHI_SPIRAL = 0.75   // معامل الاختزال للأعمدة الحلزونية (ACI 318-21.2.1)
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
        val Ag = width * depth
        val Ast = reinforcementArea.coerceAtMost(Ag * 0.08)
        
        // ACI 318-22.4.2: Pn = 0.85*fc'*(Ag-Ast) + fy*Ast
        // fc' = 0.8 × fcu (cube to cylinder conversion)
        val fc_prime = 0.8 * fcu
        val concreteCapacity = 0.85 * fc_prime * (Ag - Ast)
        val steelCapacity = fy * Ast
        val nominalCapacity = concreteCapacity + steelCapacity
        
        // ACI 318-25 §22.4.2.2: Pn,max = 0.80*Po for tied, 0.85*Po for spiral (حد اللامركزية الدنيا)
        // ثم φPn,max = φ * Pn,max
        val alpha = 0.80 // tied column limit
        val phi = PHI_TIED
        return phi * alpha * nominalCapacity / 1000.0 // kN
    }
    
    /**
     * حساب القدرة المحورية مع اختيار نوع العمود (مربوط/حلزوني)
     * ACI 318-21.2.1: φ=0.65 (tied) أو φ=0.75 (spiral)
     */
    fun calculateAxialCapacityWithPhi(
        fcu: Double, fy: Double, width: Double, depth: Double,
        reinforcementArea: Double, isSpiral: Boolean
    ): Double {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("width", width)
        InputGuard.positive("depth", depth)
        val Ag = width * depth
        val Ast = reinforcementArea.coerceAtMost(Ag * 0.08)
        val fc_prime = 0.8 * fcu
        val concreteCapacity = 0.85 * fc_prime * (Ag - Ast)
        val steelCapacity = fy * Ast
        val nominalCapacity = concreteCapacity + steelCapacity
        val phi = if (isSpiral) PHI_SPIRAL else PHI_TIED
        val alpha = if (isSpiral) 0.85 else 0.80 // ACI 22.4.2.2
        return phi * alpha * nominalCapacity / 1000.0
    }
    
    /**
     * معامل تكبير العزم بالطريقة الصحيحة من الكود — ACI 318M-14 §6.2.5 + §6.6.4.4/§6.6.4.5
     * (F-09 fix; owner-approved 2026-11-21).
     *
     * Verified printed pages: 6.2.5 + 6.2.5.1 (printed pp.103-104),
     * 6.6.4.4.2/4.4.3/4.4.4 (printed pp.122-123),
     * 6.6.4.5.1/5.2/5.3 (printed pp.124-125) — CLAUSE_LOG A-17.
     *
     * Sign convention (6.2.5): M1/M2 is NEGATIVE in single curvature,
     * POSITIVE in double curvature.
     *
     * NONSWAY frames only (6.6.4.5). Sway frames: calculateSwayMagnification (6.6.4.6).
     *
     * @return δ moment magnifier (>= 1.0); Mc = δ × M2 (6.6.4.5.1).
     */
    fun calculateMomentMagnification(
        Pu: Double,                      // kN, factored axial load
        M1: Double,                      // kNm, smaller end moment, signed
        M2: Double,                      // kNm, larger end moment, positive
        kLu: Double,                     // mm, effective length k×ℓu
        width: Double,                   // mm, perpendicular to bending
        depth: Double,                   // mm, in bending direction
        fcu: Double,
        betaDns: Double = 0.0            // 6.6.4.4.4 sustained-axial ratio (0.6 → simplified 0.25EcIg)
    ): Double {
        InputGuard.positive("Pu", Pu)
        InputGuard.positive("M2", M2)
        InputGuard.positive("kLu", kLu)
        InputGuard.positive("width", width)
        InputGuard.positive("depth", depth)
        InputGuard.positive("fcu", fcu)
        require(M2 >= abs(M1)) { "M2 must be the larger end moment in absolute value" }
        require(betaDns in 0.0..1.0) { "betaDns must be in 0..1" }

        // 6.2.5.1(b): r = 0.30 × dimension in the direction stability is considered
        val r = 0.30 * depth
        val kluOverR = kLu / r
        val mRatio = M1 / M2
        // 6.2.5(b): nonsway (braced) neglect limit — 6.2.5b AND 6.2.5c (cap 40).
        // Sway frames are NOT this method: use calculateSwayMagnification (6.6.4.6).
        val mayNeglect = kluOverR <= (34.0 + 12.0 * mRatio).coerceAtMost(40.0)
        if (mayNeglect) return 1.0

        // 6.6.4.4.4(a): EI = 0.4EcIg / (1+βdns)
        val fcPrime = 0.8 * fcu
        val ec = 4700.0 * sqrt(fcPrime)
        val ig = width * depth.pow(3) / 12.0
        val ei = 0.4 * ec * ig / (1.0 + betaDns)
        // 6.6.4.4.2: Pc = π²EI / (kℓu)²
        val pc = PI * PI * ei / (kLu * kLu) / 1000.0  // N → kN
        // 6.6.4.5.3(a): Cm = 0.6 − 0.4(M1/M2)
        val cm = 0.6 - 0.4 * mRatio
        // 6.6.4.5.2: δ = Cm / (1 − Pu/(0.75Pc)) ≥ 1.0
        val delta = cm / (1.0 - Pu / (0.75 * pc))
        return max(1.0, delta)
    }

    /**
     * تكبير العزوم للإطارات غير المقيدة — ACI 318M-14 §6.6.4.6 (sway frames).
     * Verified printed pages 125-126 (CLAUSE_LOG A-17).
     *
     * 6.6.4.6.1: M = Mns + δs·Ms (sway moments amplified, nonsway are not).
     * 6.6.4.6.2: δs = 1/(1−Q) ≥ 1 (a) when [stabilityIndexQ] is given, else
     * δs = 1/(1 − ΣPu/(0.75ΣPc)) ≥ 1 (b) from the story sums. Method (a) is
     * only permitted while δs ≤ 1.5; method (b) has no such limit.
     *
     * @return Pair(δs, magnified moment M = Mns + δs·Ms)
     */
    fun calculateSwayMagnification(
        Mns: Double,                     // kNm, first-order NONSWAY moment
        Ms: Double,                      // kNm, first-order SWAY moment
        sumPu: Double,                   // kN, story ΣPu (needed for 6.6.4.6.2b)
        sumPc: Double,                   // kN, story ΣPc (needed for 6.6.4.6.2b)
        stabilityIndexQ: Double? = null  // 6.6.4.6.2a; when null, method (b) is used
    ): Pair<Double, Double> {
        InputGuard.positive("sumPu", sumPu)
        InputGuard.positive("sumPc", sumPc)
        val deltaS = if (stabilityIndexQ != null) {
            require(stabilityIndexQ < 1.0) { "Q must be < 1.0 (unstable otherwise)" }
            val d = 1.0 / (1.0 - stabilityIndexQ)
            require(d <= 1.5) {
                "6.6.4.6.2: Q-method only permitted while δs ≤ 1.5 (δs=$d); use the ΣPc method (stabilityIndexQ = null) or second-order analysis"
            }
            d
        } else {
            1.0 / (1.0 - sumPu / (0.75 * sumPc))
        }
        val ds = max(1.0, deltaS)
        return Pair(ds, Mns + ds * Ms)
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
        val Ag = width * depth
        val Pu = axialLoad * 1000.0  // N - الحمل المحوري التصميمي (مضروب في معامل التحميل بالفعل)
        
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // ACI 318-25: Simplified reinforcement calculation
        // fc' = 0.8 × fcu (cube to cylinder)
        val fc_prime = 0.8 * fcu
        val phi = 0.65
        val alpha = 0.80
        var requiredSteelArea = (Pu / (phi * alpha) - 0.85 * fc_prime * Ag) / (fy - 0.85 * fc_prime)
        
        // ── Moment consideration: increase As when eccentricity is significant ──
        val Mu = sqrt(momentX.pow(2) + momentY.pow(2)) * 1e6 // N.mm
        val eccentricity = if (Pu > 0) Mu / Pu else 0.0
        val h = max(width, depth)
        if (eccentricity > 0.05 * h) {
            val momentFactor = max(1.0, 1.0 + 2.0 * eccentricity / h)
            requiredSteelArea *= momentFactor
            codeNotes.add("ACI 318-25: Significant moment (e=${String.format("%.1f", eccentricity)}mm > 0.05h), As increased by factor ${String.format("%.2f", momentFactor)}")
        }
        
        // Reinforcement limits per ACI
        val minSteel = getMinReinforcementRatio() * Ag
        val maxSteel = getMaxReinforcementRatio() * Ag
        
        var astRequired = requiredSteelArea.coerceIn(minSteel, maxSteel)
        
        if (requiredSteelArea < minSteel) {
            warnings.add("Minimum reinforcement (1%) applied per ACI 318-10.6.1")
        }
        
        // Selection of bar diameters مع بدائل اقتصادية وآمنة
        val availableBars = listOf(14.0, 16.0, 19.0, 22.0, 25.0, 29.0, 32.0) // No.4 to No.10
        var selectedBarDia = availableBars.firstOrNull { 
            val area = PI * it * it / 4
            ceil(astRequired / area) <= 12
        } ?: 19.0
        
        // حساب البدائل
        val alternatives = mutableListOf<String>()
        for (dia in availableBars) {
            val area = PI * dia * dia / 4
            val numBars = ceil(astRequired / area).toInt().coerceIn(4, 16)
            val asProv = numBars * area
            val cap = calculateAxialCapacity(fcu, fy, width, depth, asProv, loadCombination)
            val util = if (cap > 0) axialLoad / cap else 2.0
            if (util in 0.5..1.0 && dia != selectedBarDia) {
                alternatives.add("${numBars}Ø${dia.toInt()} (${(util*100).toInt()}%)")
            }
        }
        if (alternatives.size >= 2) {
            codeNotes.add("Economical: ${alternatives.first()}")
            codeNotes.add("Safest: ${alternatives.last()}")
        }
        
        val barArea = PI * selectedBarDia * selectedBarDia / 4
        val numberOfBars = ceil(astRequired / barArea).toInt().coerceIn(4, 16)
        val astProvided = numberOfBars * barArea
        val barDiameter = selectedBarDia
        
        // Ties per ACI 318-25.7.2
        val tiesDiameter = if (barDiameter <= 32.0) 10.0 else 12.0
        val tiesSpacing = minOf(
            16 * barDiameter,           // 16 x longitudinal bar diameter
            48 * tiesDiameter,          // 48 x tie bar diameter
            width, depth,               // smallest dimension of member
            300.0                        // 300 mm max
        ).coerceIn(getMinSpacing(), getMaxSpacing())
        
        // Safety check
        val capacity = calculateAxialCapacity(fcu, fy, width, depth, astProvided, loadCombination)
        val utilizationRatio = axialLoad / capacity
        
        codeNotes.add(CodeReference.ACI.COLUMN_AXIAL)
        codeNotes.add(CodeReference.ACI.COLUMN_REINFORCEMENT_MIN)
        codeNotes.add(CodeReference.ACI.COLUMN_REINFORCEMENT_MAX)
        codeNotes.add(CodeReference.ACI.COLUMN_COVER)
        codeNotes.add("phi = 0.65 for tied columns")
        codeNotes.add("Min cover: ${getMinCover()}mm for cast-in-place")
        
        return ReinforcementResult(
            astRequired = astRequired,
            astProvided = astProvided,
            barDiameter = barDiameter,
            numberOfBars = numberOfBars,
            tiesDiameter = tiesDiameter,
            tiesSpacing = tiesSpacing,
            isSafe = utilizationRatio <= 1.0,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    override fun getMinReinforcementRatio(): Double = 0.01   // 1% ACI 10.6.1
    override fun getMaxReinforcementRatio(): Double = 0.08   // 8% ACI 10.6.1
    override fun getMinSpacing(): Double = 40.0
    override fun getMaxSpacing(): Double = 300.0
    override fun getMinCover(): Double = 40.0

    // ── Shear Design per ACI 318-25 §22.5 ─────────────────────────────────────

    /**
     * Column shear reinforcement design — ACI 318-25 §22.5
     * @param Vu     factored shear force (kN)
     * @param width  column width b (mm)
     * @param depth  column depth h (mm)
     * @param fcu    concrete cube strength (MPa);  f'c = 0.8 × fcu
     * @param fy     steel yield strength (MPa)
     * @param cover  concrete cover (mm), default 40
     */
    fun calculateShearDesign(
        Vu: Double,
        width: Double,
        depth: Double,
        fcu: Double,
        fy: Double,
        cover: Double = 40.0,
        longitudinalBarDia: Double = 20.0
    ): ColumnShearDesignResult {
        InputGuard.positive("width", width)
        InputGuard.positive("depth", depth)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("cover", cover)
        val b = width
        val d = depth - cover
        val fc = 0.8 * fcu  // cylinder strength
        val phi = 0.75
        val codeNotes = mutableListOf<String>()

        // Vc = 0.17 × √(f'c) × b × d   (ACI 318-22.5.5.1)
        val Vc = 0.17 * sqrt(fc) * b * d / 1000.0  // kN
        val phiVc = phi * Vc

        val needsStirrups = Vu > phiVc

        // Asv/s = (Vu - φVc) / (φ × fy × d)
        val requiredAsvPerS = if (needsStirrups) {
            (Vu - phiVc) * 1000.0 / (phi * fy * d)  // mm²/mm
        } else 0.0

        // Max spacing per ACI 318-25 §25.7.2.2: min(b/4, 6×db, 150mm)
        val maxSpacing = minOf(min(width, depth) / 4.0, 6.0 * longitudinalBarDia, 150.0)

        // Min Asv per ACI 22.5.10.1: Av,min = 0.062√f'c × b×s / fy  (but not less than 0.35×b×s/fy)
        // As Av/s:  minAvPerS = max(0.062√f'c × b / fy,  0.35 × b / fy)
        val minAsvPerS = max(0.062 * sqrt(fc) * b / fy, 0.35 * b / fy)

        val designAsvPerS = max(requiredAsvPerS, if (needsStirrups) minAsvPerS else 0.0)

        // Select stirrup diameter and spacing
        val availableTies = listOf(9.5, 12.7, 15.9, 19.1)  // #3, #4, #5, #6
        var selectedDia = 9.5
        var selectedSpacing = maxSpacing

        if (designAsvPerS > 0) {
            for (dia in availableTies) {
                val asv = 2.0 * PI * dia * dia / 4.0
                val spacing = asv / designAsvPerS
                if (spacing <= maxSpacing && spacing >= getMinSpacing()) {
                    selectedDia = dia
                    selectedSpacing = min(spacing, maxSpacing)
                    break
                }
                if (spacing < getMinSpacing()) {
                    selectedDia = dia
                    selectedSpacing = getMinSpacing()
                }
            }
        }

        val providedAsvPerS = if (designAsvPerS > 0) {
            2.0 * PI * selectedDia * selectedDia / 4.0 / selectedSpacing
        } else 0.0

        val totalCapacity = phiVc + if (needsStirrups) phi * fy * providedAsvPerS * d / 1000.0 else 0.0
        val utilizationRatio = if (totalCapacity > 0) Vu / totalCapacity else 2.0

        codeNotes.add("ACI 318-25 §22.5: Column Shear Design")
        codeNotes.add("f'c = 0.8×fcu = ${String.format("%.0f", fc)} MPa")
        codeNotes.add("Vc = 0.17√f'c·b·d = ${String.format("%.1f", Vc)} kN")
        codeNotes.add(CodeReference.ACI.COLUMN_TIES)
        codeNotes.add("φVc = ${String.format("%.1f", phiVc)} kN  (φ=$phi)")
        if (needsStirrups) {
            codeNotes.add("Vu (${String.format("%.1f", Vu)} kN) > φVc → Stirrups required")
            codeNotes.add("Asv/s = ${String.format("%.3f", designAsvPerS)} mm²/mm")
            codeNotes.add(String.format("%.1fmm ties @ %.0fmm c/c", selectedDia, selectedSpacing))
        } else {
            codeNotes.add("Vu (${String.format("%.1f", Vu)} kN) ≤ φVc → Concrete alone sufficient")
        }

        return ColumnShearDesignResult(
            Vu = Vu,
            Vc = Vc,
            phiVc = phiVc,
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
