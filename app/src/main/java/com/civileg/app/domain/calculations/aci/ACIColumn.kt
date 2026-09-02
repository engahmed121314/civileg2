package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.core.calculations.entities.ColumnShearDesignResult
import com.civileg.core.calculations.entities.LoadCombination
import com.civileg.core.calculations.entities.ReinforcementResult
import kotlin.math.*

class ACIColumn : ColumnDesign {
    
    companion object {
        private const val BETA_1 = 0.85  // Concrete stress block factor
        private const val PHI_TIED = 0.65     // معامل الاختزال للأعمدة المربوطة (ACI 318-21.2.1)
        private const val PHI_SPIRAL = 0.75   // معامل الاختزال للأعمدة الحلزونية (ACI 318-21.2.1)

        // ACI 318-19 Table 22.4.2.1: maximum nominal axial strength
        // Pn,max = 0.80·P0 (tied) / 0.85·P0 (spiral) — replaces the pre-2002
        // blanket 0.80 factor with an explicit cap on design capacity.
        private const val CAP_TIED = 0.80
        private const val CAP_SPIRAL = 0.85

        /** φ·Pn,max per ACI 318-19 §22.4.2, with fc' = 0.8×fcu cube conversion. */
        private fun cappedDesignCapacity(
            fcu: Double, fy: Double, Ag: Double, Ast: Double,
            phi: Double, capFactor: Double
        ): Double {
            val fcPrime = 0.8 * fcu
            val p0 = 0.85 * fcPrime * (Ag - Ast) + fy * Ast
            return phi * capFactor * p0 / 1000.0 // kN
        }
    }

    override fun calculateAxialCapacity(
        fcu: Double,
        fy: Double,
        width: Double,
        depth: Double,
        reinforcementArea: Double,
        loadCombination: LoadCombination
    ): Double {
        val Ag = width * depth
        val Ast = reinforcementArea.coerceAtMost(Ag * 0.08)
        // W5: ACI 318-19 Table 22.4.2.1(a) — tied columns capped at 0.80·P0
        return cappedDesignCapacity(fcu, fy, Ag, Ast, PHI_TIED, CAP_TIED)
    }
    
    /**
     * حساب القدرة المحورية مع اختيار نوع العمود (مربوط/حلزوني)
     * ACI 318-21.2.1: φ=0.65 (tied) أو φ=0.75 (spiral)
     */
    fun calculateAxialCapacityWithPhi(
        fcu: Double, fy: Double, width: Double, depth: Double,
        reinforcementArea: Double, isSpiral: Boolean
    ): Double {
        val Ag = width * depth
        val Ast = reinforcementArea.coerceAtMost(Ag * 0.08)
        // W5: ACI 318-19 Table 22.4.2.1(a)/(b) — 0.80·P0 tied / 0.85·P0 spiral
        return if (isSpiral) cappedDesignCapacity(fcu, fy, Ag, Ast, PHI_SPIRAL, CAP_SPIRAL)
        else cappedDesignCapacity(fcu, fy, Ag, Ast, PHI_TIED, CAP_TIED)
    }
    
    /**
     * حساب نسبة النحافة ونسبة التحميل المسموحة
     * ACI 318-22.4: λ = KL/r, Pn = Fcr × Ag
     */
    fun calculateSlendernessEffect(
        unsupportedLength: Double, // m
        effectiveLengthFactor: Double, // K
        width: Double, depth: Double, // mm
        fcu: Double, reinforcementRatio: Double = 0.01
    ): Triple<Double, Boolean, Double> {
        val Ag = width * depth
        // نصف القطر الدوراني (المحور الأضع)
        val minDimension = min(width, depth)
        val r = minDimension / sqrt(12.0)
        val L_mm = unsupportedLength * 1000.0
        val slendernessRatio = effectiveLengthFactor * L_mm / r
        
        // W5 note: ACI 318-19 Table 6.2.3 — short-column limits depend on
        // bracing (kLu/r ≤ 34+12·M1/M2 braced, ≤ 22 non-braced). This engine
        // conservatively applies the non-braced bound of 22.
        val isShort = slendernessRatio <= 22.0
        
        // حساب إجهاد الانبعاج الحرج Fcr
        // ACI 22.4.2.1: EI = 0.4 × Ec × Ig + Es × As × (h/2 - d')^2
        // Ig = b × h^3 / 12 (moment of inertia of gross concrete section)
        val fc_prime = 0.8 * fcu
        val Ig = width * depth.pow(3) / 12.0  // mm^4
        val Ec = 4700.0 * sqrt(fc_prime)      // MPa
        val Es = 200000.0                       // MPa
        val As = reinforcementRatio * Ag
        val d_prime = 40.0  // cover + tie dia (approximate, mm)
        val EI = 0.4 * Ec * Ig + Es * As * ((minDimension / 2.0) - d_prime).pow(2)  // N.mm^2
        // W5 fix: Pc[kN] = pi^2*EI[N.mm^2] / (1000*(K*L_mm)^2)
        // The old code divided by 1e6 and then by L in metres, mixing unit bases
        // (~1000x overstatement) and ignored K entirely.
        val KL_mm = effectiveLengthFactor * L_mm
        val Pc = PI * PI * EI / (1000.0 * KL_mm.pow(2))  // kN
        
        return Triple(slendernessRatio, !isShort, Pc)
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
        com.civileg.app.domain.calculations.InputGuard.positive(
            "fcu" to fcu, "fy" to fy, "width" to width, "depth" to depth
        )
        val Ag = width * depth
        val Pu = axialLoad * 1000.0  // N - الحمل المحوري التصميمي (مضروب في معامل التحميل بالفعل)
        
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // ACI 318-19: Simplified reinforcement calculation
        // fc' = 0.8 × fcu (cube to cylinder)
        val fc_prime = 0.8 * fcu
        val phi = 0.65
        var requiredSteelArea = (Pu / phi - 0.85 * fc_prime * Ag) / (fy - 0.85 * fc_prime)
        
        // ── Moment consideration: increase As when eccentricity is significant ──
        val Mu = sqrt(momentX.pow(2) + momentY.pow(2)) * 1e6 // N.mm
        val eccentricity = if (Pu > 0) Mu / Pu else 0.0
        val h = max(width, depth)
        if (eccentricity > 0.05 * h) {
            val momentFactor = max(1.0, 1.0 + 2.0 * eccentricity / h)
            requiredSteelArea *= momentFactor
            codeNotes.add("ACI 318: Significant moment (e=${String.format("%.1f", eccentricity)}mm > 0.05h), As increased by factor ${String.format("%.2f", momentFactor)}")
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
        val tiesDiameter = if (barDiameter <= 25.0) 10.0 else 12.0
        val tiesSpacing = minOf(
            16 * barDiameter,           // 16 x longitudinal bar diameter
            48 * tiesDiameter,          // 48 x tie bar diameter
            width, depth,               // smallest dimension of member
            300.0                        // 300 mm max
        ).coerceIn(getMinSpacing(), getMaxSpacing())
        
        // Safety check
        val capacity = calculateAxialCapacity(fcu, fy, width, depth, astProvided, loadCombination)
        // rule 1.4 — non-positive capacity means the design FAILS, never Infinity/0
        val utilizationRatio = if (capacity > 0) axialLoad / capacity else 2.0
        if (capacity <= 0) warnings.add("Axial capacity non-positive — section/materials invalid")
        
        // [Phase 3] Capture Calculation Trace for Transparency
        val traceSteps = mutableListOf<com.civileg.core.calculations.entities.CalculationStep>()
        traceSteps.add(com.civileg.core.calculations.entities.CalculationStep(
            "Cylinder Strength (f'c)", "f'c = 0.8 * fcu", "0.8 * $fcu", fc_prime, "MPa"
        ))
        traceSteps.add(com.civileg.core.calculations.entities.CalculationStep(
            "Gross Area (Ag)", "Ag = b * h", "$width * $depth", Ag, "mm2"
        ))
        traceSteps.add(com.civileg.core.calculations.entities.CalculationStep(
            "Design Strength (\u03C6Pn,max)", "\u03C6Pn,max = 0.80 * \u03C6 * [0.85*f'c*(Ag-As) + fy*As]", "0.80*0.65*[0.85*$fc_prime*($Ag-$astProvided) + $fy*$astProvided]", capacity, "kN"
        ))
        traceSteps.add(com.civileg.core.calculations.entities.CalculationStep(
            "Utilization Ratio", "U = Pu / \u03C6Pn", "$axialLoad / $capacity", utilizationRatio, "", limit = 1.0, limitType = com.civileg.core.calculations.entities.LimitType.MAX, isSafe = utilizationRatio <= 1.0
        ))

        codeNotes.add("ACI 318-19: Chapter 10 - Columns")
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
            codeNotes = codeNotes,
            trace = com.civileg.core.calculations.entities.DesignTrace(traceSteps)
        )
    }

    override fun getMinReinforcementRatio(): Double = 0.01   // 1% ACI 10.6.1
    override fun getMaxReinforcementRatio(): Double = 0.08   // 8% ACI 10.6.1
    override fun getMinSpacing(): Double = 40.0
    override fun getMaxSpacing(): Double = 300.0
    override fun getMinCover(): Double = 40.0

    // ── Shear Design per ACI 318-19 §22.5 ─────────────────────────────────────

    /**
     * Column shear reinforcement design — ACI 318-19 §22.5
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
        cover: Double = 40.0
    ): ColumnShearDesignResult {
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

        // Max spacing = min(d/2, 48×db_tie, 300mm) for columns
        val dbTie = 10.0
        val maxSpacing = minOf(d / 2.0, 48.0 * dbTie, 300.0)

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

        codeNotes.add("ACI 318-19 §22.5: Column Shear Design")
        codeNotes.add("f'c = 0.8×fcu = ${String.format("%.0f", fc)} MPa")
        codeNotes.add("Vc = 0.17√f'c·b·d = ${String.format("%.1f", Vc)} kN")
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